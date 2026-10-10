package com.edgeore.app

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.storage.BackupCoordinator
import com.edgeore.app.storage.BackupJob
import com.edgeore.app.storage.BackupJobStore
import com.edgeore.app.storage.BackupProvider
import com.edgeore.app.storage.BackupStage
import com.edgeore.app.storage.BackupUnavailableException
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.NotConfiguredBackupProvider
import com.edgeore.app.storage.ProviderAvailability
import com.edgeore.app.storage.ProviderObservation
import com.edgeore.app.storage.RemoteState
import com.edgeore.app.storage.RestoreOutcome
import com.edgeore.app.storage.RestoreStage
import com.edgeore.app.storage.StorageLabels
import com.edgeore.app.storage.UploadRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Remote-backup state machine against a TEST DOUBLE backend (this file only). The shipped app has
 * no backend and uses NotConfiguredBackupProvider; nothing here is evidence that an upload, pin or
 * restore ever ran against a real EdgeORE backend or IPFS node. Those stay NOT_RUN.
 */
class BackupCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val vaultDir get() = File(tmp.root, "vault")
    private val jobsFile get() = File(tmp.root, "jobs.json")
    private val restoreDir get() = File(tmp.root, "restore")
    private fun vault(k: () -> SecretKey = { key }) = LocalVault(vaultDir, k, freeSpace = { Long.MAX_VALUE })
    private fun objects() = vaultDir.listFiles().orEmpty().filter { it.name.endsWith(LocalVault.SUFFIX) }

    /** Test double: an in-memory backend whose answers each test scripts. */
    private class FakeBackend : BackupProvider {
        val stored = HashMap<String, ByteArray>()
        val uploads = mutableListOf<UploadRequest>()
        var pinOnUpload = false
        var uploadBehavior: (suspend (UploadRequest) -> ProviderObservation)? = null
        var statusBehavior: (String) -> ProviderObservation = { id ->
            stored[id]?.let { ProviderObservation.Acknowledged("ref-$id", Sha256.hex(it), "t1") } ?: ProviderObservation.NotFound
        }
        var downloadTransform: (ByteArray) -> ByteArray = { it }
        var cancelAck = true
        override fun availability() = ProviderAvailability.Configured("test double")
        override suspend fun upload(request: UploadRequest): ProviderObservation {
            uploads += request
            uploadBehavior?.let { return it(request) }
            stored[request.clientJobId] = request.ciphertext
            return if (pinOnUpload) ProviderObservation.Pinned("ref-${request.clientJobId}", request.ciphertextSha256, "t2", "owned-node")
            else ProviderObservation.Acknowledged("ref-${request.clientJobId}", request.ciphertextSha256, "t1")
        }
        override suspend fun status(clientJobId: String) = statusBehavior(clientJobId)
        override suspend fun download(remoteRef: String, maxBytes: Long, sink: OutputStream): Long {
            val b = downloadTransform(stored[remoteRef.removePrefix("ref-")]!!)
            sink.write(b); return b.size.toLong()
        }
        override suspend fun cancel(clientJobId: String) = cancelAck
    }

    private fun coordinator(p: BackupProvider, v: LocalVault = vault(), store: BackupJobStore = BackupJobStore(jobsFile)) =
        BackupCoordinator(v, store, p, restoreDir)

    @Test fun unavailableProviderRefusesEveryRemoteActionAndPersistsNothing() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val c = coordinator(NotConfiguredBackupProvider, v)
        assertTrue(runCatching { c.backup(e.id, e.displayName) }.exceptionOrNull() is BackupUnavailableException)
        assertTrue(runCatching { c.reconcile("x") }.exceptionOrNull() is BackupUnavailableException)
        assertTrue(runCatching { c.restore("x") }.exceptionOrNull() is BackupUnavailableException)
        assertFalse("no job written", jobsFile.exists())
        assertEquals(RemoteState.NOT_CONFIGURED, StorageLabels.remoteState(null, NotConfiguredBackupProvider.availability()))
        assertEquals(RemoteState.UNAVAILABLE, StorageLabels.remoteState(null, ProviderAvailability.Unavailable("backend down")))
    }

    @Test fun uploadCarriesCiphertextOnlyNeverTheNameOrPlaintext() = runBlocking {
        val v = vault()
        val name = "tax-return-2026.pdf"
        val plain = "very private plaintext".toByteArray()
        val e = v.put(name, plain)
        val p = FakeBackend()
        coordinator(p, v).backup(e.id, name)
        val sent = p.uploads.single().ciphertext
        val s = String(sent, Charsets.ISO_8859_1)
        assertFalse(s.contains(name))
        assertFalse(s.contains("very private"))
        assertEquals(Sha256.hex(sent), p.uploads.single().ciphertextSha256)
        // The receipt-facing event carries no plaintext digest.
        val job = BackupJobStore(jobsFile).all().single()
        assertNotEquals(job.plainSha256, job.ciphertextSha256)
    }

    @Test fun jobIsPersistedAsUploadingBeforeTheNetworkCallAndRestartMakesItOutcomeUnknown() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val inFlight = CompletableDeferred<Unit>()
        val never = CompletableDeferred<ProviderObservation>()
        val p = FakeBackend().apply { uploadBehavior = { r -> stored[r.clientJobId] = r.ciphertext; inFlight.complete(Unit); never.await() } }
        val job = async(Dispatchers.Default) { coordinator(p, v).backup(e.id, e.displayName) }
        inFlight.await()
        // "Process death" here: a fresh store reads what is on disk.
        val onDisk = BackupJobStore(jobsFile).all().single()
        assertEquals(BackupStage.UPLOADING, onDisk.stage)
        job.cancel()

        val restarted = coordinator(p, v, BackupJobStore(jobsFile))
        restarted.recoverAfterRestart()
        val afterRecovery = BackupJobStore(jobsFile).get(onDisk.jobId)!!
        assertEquals(BackupStage.OUTCOME_UNKNOWN, afterRecovery.stage)
        assertEquals(RemoteState.OUTCOME_UNKNOWN, StorageLabels.remoteState(afterRecovery, p.availability()))

        // Reconciliation records exactly what the backend reports: acknowledged is not pinned.
        val ack = restarted.reconcile(onDisk.jobId)
        assertEquals(BackupStage.UPLOAD_ACKNOWLEDGED, ack.stage)
        assertEquals(RemoteState.UPLOAD_ACKNOWLEDGED, StorageLabels.remoteState(ack, p.availability()))
        p.statusBehavior = { id -> ProviderObservation.Pinned("ref-$id", Sha256.hex(p.stored[id]!!), "t3", "owned-node") }
        val pinned = restarted.reconcile(onDisk.jobId)
        assertEquals(BackupStage.PINNED, pinned.stage)
    }

    @Test fun failedUploadIsOutcomeUnknownAndBackendWithoutRecordStaysUnknown() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val p = FakeBackend().apply { uploadBehavior = { throw java.net.SocketTimeoutException("timeout") } }
        val c = coordinator(p, v)
        val j = c.backup(e.id, e.displayName)
        assertEquals(BackupStage.OUTCOME_UNKNOWN, j.stage)
        assertEquals(BackupStage.OUTCOME_UNKNOWN, c.reconcile(j.jobId).stage)  // NotFound is not success
        p.statusBehavior = { throw java.io.IOException("unreachable") }
        assertEquals(BackupStage.OUTCOME_UNKNOWN, c.reconcile(j.jobId).stage)
    }

    @Test fun pinWithAForeignDigestIsFailedNotPinned() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val p = FakeBackend().apply { uploadBehavior = { r -> ProviderObservation.Pinned("ref-${r.clientJobId}", "0".repeat(64), "t", "node") } }
        assertEquals(BackupStage.FAILED, coordinator(p, v).backup(e.id, e.displayName).stage)
    }

    @Test fun cancellationIsCanceledOnlyWhenTheBackendAcknowledges() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val p = FakeBackend()
        val c = coordinator(p, v)
        val j = c.backup(e.id, e.displayName)
        p.cancelAck = false
        assertEquals(BackupStage.OUTCOME_UNKNOWN, c.cancel(j.jobId).stage)
        p.cancelAck = true
        assertEquals(BackupStage.CANCELED, c.cancel(j.jobId).stage)
    }

    @Test fun acknowledgementsAndPinsNeverBecomeRestoreVerified() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val p = FakeBackend().apply { pinOnUpload = true }
        val j = coordinator(p, v).backup(e.id, e.displayName)
        assertEquals(RemoteState.PINNED, StorageLabels.remoteState(j, p.availability()))
        assertEquals(RemoteState.UPLOAD_ACKNOWLEDGED, StorageLabels.remoteState(j.copy(stage = BackupStage.UPLOAD_ACKNOWLEDGED), p.availability()))
        for (stage in listOf(RestoreStage.NONE, RestoreStage.DOWNLOADED, RestoreStage.DECRYPTED, RestoreStage.FAILED))
            assertNotEquals(RemoteState.RESTORE_VERIFIED, StorageLabels.remoteState(j.copy(restoreStage = stage), p.availability()))
        // A verified restore flag without a restored object, or on a non-acknowledged job, is not trusted either.
        assertNotEquals(RemoteState.RESTORE_VERIFIED, StorageLabels.remoteState(j.copy(restoreStage = RestoreStage.RESTORE_VERIFIED), p.availability()))
        assertNotEquals(RemoteState.RESTORE_VERIFIED, StorageLabels.remoteState(j.copy(stage = BackupStage.OUTCOME_UNKNOWN, restoreStage = RestoreStage.RESTORE_VERIFIED, restoredObjectId = "x"), p.availability()))
    }

    @Test fun verifiedRestorePublishesANewObjectAndKeepsTheOriginal() = runBlocking {
        val v = vault()
        val plain = "restore me".toByteArray()
        val e = v.put("a.txt", plain)
        val original = File(vaultDir, e.id + LocalVault.SUFFIX).readBytes()
        val p = FakeBackend().apply { pinOnUpload = true }
        val c = coordinator(p, v)
        val j = c.backup(e.id, e.displayName)
        val r = c.restore(j.jobId) as RestoreOutcome.Verified
        assertNotEquals(e.id, r.restored.id)
        assertArrayEquals(plain, v.read(r.restored.id))
        assertArrayEquals(original, File(vaultDir, e.id + LocalVault.SUFFIX).readBytes())
        assertEquals("a.txt (restored)", r.restored.displayName)
        assertEquals(RestoreStage.RESTORE_VERIFIED, r.job.restoreStage)
        assertEquals(RemoteState.RESTORE_VERIFIED, StorageLabels.remoteState(r.job, p.availability()))
        assertTrue(restoreDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun corruptedTruncatedOrOversizedDownloadIsRejectedBeforeDecryption() = runBlocking {
        for ((label, transform) in listOf<Pair<String, (ByteArray) -> ByteArray>>(
            "flipped" to { b -> b.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() } },
            "truncated" to { b -> b.copyOf(b.size - 5) },
            "oversized" to { b -> b + ByteArray(64) },
        )) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            val v = vault()
            val e = v.put("a.txt", "abc".toByteArray())
            val p = FakeBackend().apply { pinOnUpload = true; downloadTransform = transform }
            val c = coordinator(p, v)
            val j = c.backup(e.id, e.displayName)
            val r = c.restore(j.jobId)
            assertTrue(label, r is RestoreOutcome.Failed)
            val after = BackupJobStore(jobsFile).get(j.jobId)!!
            assertEquals(label, RestoreStage.FAILED, after.restoreStage)
            assertEquals(label, 1, objects().size)
            assertTrue(label, restoreDir.listFiles().orEmpty().isEmpty())
            assertEquals(RemoteState.PINNED, StorageLabels.remoteState(after, p.availability()))
        }
    }

    @Test fun wrongOrUnavailableDeviceKeyFailsRestoreWithoutPublishing() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val p = FakeBackend().apply { pinOnUpload = true }
        val j = coordinator(p, v).backup(e.id, e.displayName)
        val other: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val wrong = coordinator(p, vault { other }).restore(j.jobId)
        assertTrue(wrong is RestoreOutcome.Failed && wrong.reason.contains("Authentication failed"))
        val gone = coordinator(p, vault { throw java.security.UnrecoverableKeyException("key permanently invalidated") }).restore(j.jobId)
        assertTrue(gone is RestoreOutcome.Failed && gone.reason.contains("not available"))
        assertEquals(1, objects().size)
        assertEquals(RestoreStage.FAILED, BackupJobStore(jobsFile).get(j.jobId)!!.restoreStage)
    }

    @Test fun plaintextDigestMismatchIsNotRestoreVerified() = runBlocking {
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        val p = FakeBackend().apply { pinOnUpload = true }
        val store = BackupJobStore(jobsFile)
        val j = coordinator(p, v, store).backup(e.id, e.displayName)
        // The recorded expected plaintext digest differs (for example the job belongs to other content).
        store.put(j.copy(plainSha256 = "f".repeat(64)))
        val r = coordinator(p, v, BackupJobStore(jobsFile)).restore(j.jobId)
        assertTrue(r is RestoreOutcome.Failed && r.reason.contains("does not match"))
        assertEquals(1, objects().size)
    }

    @Test fun damagedJobStoreFailsClosed() = runBlocking {
        jobsFile.writeText("{not json")
        val store = BackupJobStore(jobsFile)
        assertTrue(store.unavailableReason != null)
        val v = vault()
        val e = v.put("a.txt", "abc".toByteArray())
        assertTrue(runCatching { coordinator(FakeBackend(), v, store).backup(e.id, e.displayName) }.exceptionOrNull() is BackupUnavailableException)
        assertEquals("{not json", jobsFile.readText())
    }

    @Test fun jobJsonRoundTrips() {
        val j = BackupJob("j", "o", "n", BackupStage.PINNED, "c", 1, "p", 2, "t0", "t1", "ref", "obs", RestoreStage.DECRYPTED, "d", null)
        assertEquals(j, BackupJob.fromJson(j.toJson()))
    }
}
