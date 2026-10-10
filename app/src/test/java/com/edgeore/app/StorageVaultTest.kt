package com.edgeore.app

import com.edgeore.app.io.AtomicFiles
import com.edgeore.app.storage.BackupJobStore
import com.edgeore.app.storage.ImportOutcome
import com.edgeore.app.storage.LocalState
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.NotConfiguredBackupProvider
import com.edgeore.app.storage.RemoteState
import com.edgeore.app.storage.StorageController
import com.edgeore.app.storage.StorageEvent
import com.edgeore.app.storage.VaultException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Storage page import/export/delete on the JVM with a software AES key standing in for Android
 * Keystore. These results say nothing about Keystore or device behaviour, which stay NOT_RUN.
 */
class StorageVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val dir get() = File(tmp.root, "vault")
    private fun vault(k: () -> SecretKey = { key }, free: Long = Long.MAX_VALUE, max: Long = LocalVault.MAX_PLAIN_BYTES) =
        LocalVault(dir, k, maxPlainBytes = max, freeSpace = { free })
    private val events = mutableListOf<StorageEvent>()
    private fun controller(v: LocalVault = vault()) =
        StorageController(v, BackupJobStore(File(tmp.root, "jobs.json")), NotConfiguredBackupProvider, onEvent = { events += it }, io = Dispatchers.IO)
    private fun objects() = dir.listFiles().orEmpty().filter { it.name.endsWith(LocalVault.SUFFIX) }

    /** A provider stream with no declared length that never ends; counts what was pulled. */
    private class Endless : InputStream() {
        var pulled = 0L
        override fun read(): Int { pulled++; return 1 }
        override fun read(b: ByteArray, off: Int, len: Int): Int { java.util.Arrays.fill(b, off, off + len, 1); pulled += len; return len }
    }

    @Test fun unknownLengthImportIsCutOffAtTheVaultLimitWhileReading() = runBlocking {
        val s = Endless()
        val c = controller(vault(max = 100_000))
        val r = c.import("stream.bin", 0) { s }
        assertTrue(r is ImportOutcome.Refused && r.reason.contains("larger than"))
        assertTrue("pulled ${s.pulled}", s.pulled <= 100_000 + 8192)
        assertTrue(objects().isEmpty())
        assertTrue(c.view.value.files.isEmpty())
    }

    @Test fun providerLengthIsNeverTrustedAndRemainingAllowanceIsTheReadLimit() = runBlocking {
        // A provider may report 10 bytes and deliver 5000: the read limit comes from the vault, not the provider.
        val c = controller()
        val r = c.import("lies.bin", 2048) { ByteArrayInputStream(ByteArray(5000)) }
        assertTrue("$r", r is ImportOutcome.Refused && r.reason.contains("allowance"))
        assertTrue(objects().isEmpty())
        val ok = c.import("fits.bin", 2048) { ByteArrayInputStream(ByteArray(1000)) }
        assertTrue(ok is ImportOutcome.Published)
        assertEquals(1, objects().size)
    }

    @Test fun quotaExceededAndDiskFullAreSpecificAndLeaveNothing() = runBlocking {
        val v = vault()
        v.put("a", ByteArray(500), allowanceBytes = 2000)
        assertTrue(runCatching { v.put("b", ByteArray(1500), allowanceBytes = 2000) }.exceptionOrNull() is VaultException.OverAllowance)
        val full = controller(vault(free = 10))
        val r = full.import("x", 0) { ByteArrayInputStream(ByteArray(100)) }
        assertTrue("$r", r is ImportOutcome.Refused && r.reason.contains("Not enough free space"))
        // Disk filling up during the write itself: the temp file is removed, nothing is published.
        val e = runCatching { v.put("c", ByteArray(10), beforePublish = { throw IOException("write failed: ENOSPC (No space left on device)") }) }.exceptionOrNull()
        assertTrue("$e", e is VaultException.NoSpace)
        assertEquals(1, objects().size)
        assertTrue(AtomicFiles.abandonedTemps(dir).isEmpty())
    }

    @Test fun permissionLossAndMissingDocumentAreReportedNotSaved() = runBlocking {
        val c = controller()
        val a = c.import("x", 0) { throw SecurityException("Permission Denial") }
        assertTrue("$a", a is ImportOutcome.Refused && a.reason.contains("permission was revoked"))
        val b = c.import("x", 0) { throw java.io.FileNotFoundException("gone") }
        assertTrue(b is ImportOutcome.Refused && b.reason.contains("no longer available"))
        val n = c.import("x", 0) { null }
        assertTrue(n is ImportOutcome.Refused)
        val mid = c.import("x", 0) { object : InputStream() { override fun read(): Int = throw IOException("provider crashed") } }
        assertTrue(mid is ImportOutcome.Refused)
        assertTrue(objects().isEmpty())
    }

    @Test fun cancellationBeforePublicationLeavesNoObjectAndNoTemp() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = object : InputStream() {
            var sent = false
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent) return -1
                entered.countDown(); release.await(5, TimeUnit.SECONDS); sent = true
                b[off] = 42; return 1
            }
        }
        val c = controller()
        val job = async(Dispatchers.Default) { c.import("late.txt", 0) { blocking } }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        val e = runCatching { job.await() }.exceptionOrNull()
        assertTrue("$e", e is CancellationException)
        withTimeout(5000) { while (c.view.value.busy) kotlinx.coroutines.delay(10) }
        assertTrue(objects().isEmpty())
        assertTrue(AtomicFiles.abandonedTemps(dir).isEmpty())
        assertTrue(c.view.value.files.isEmpty())
        assertTrue(c.view.value.message!!.contains("canceled"))
        assertTrue(events.isEmpty())
    }

    @Test fun listShowsAFileOnlyAfterDurablePublication() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CountDownLatch(1)
        val c = controller()
        val stream = object : InputStream() {
            var done = false
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (done) return -1
                entered.countDown(); runBlocking { gate.await() }; done = true
                "hello".toByteArray().copyInto(b, off); return 5
            }
        }
        val job = async(Dispatchers.Default) { c.import("note.txt", 0) { stream } }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        // Mid-import: busy, no file listed, no success message.
        assertTrue(c.view.value.busy)
        assertTrue(c.view.value.files.isEmpty())
        assertFalse(c.view.value.message!!.contains("Encrypted note.txt"))
        gate.complete(Unit)
        val r = job.await() as ImportOutcome.Published
        val f = File(dir, r.entry.id + LocalVault.SUFFIX)
        assertTrue("published object exists on disk before the list shows it", f.isFile)
        val view = c.view.value.files.single()
        assertEquals(r.entry.id, view.entry.id)
        assertEquals(LocalState.ENCRYPTED_LOCALLY, view.local)
        assertEquals(RemoteState.NOT_CONFIGURED, view.remote)
        assertTrue(c.view.value.message!!.contains("Encrypted note.txt"))
        // Receipt event: identifiers and the encrypted-object digest only; no name, no plaintext.
        val ev = events.single()
        assertEquals("IMPORTED_ENCRYPTED", ev.action)
        assertEquals(com.edgeore.app.crypto.Sha256.hex(f.readBytes()), ev.encryptedSha256)
        assertFalse(ev.toString().contains("note.txt"))
        assertFalse(ev.toString().contains("hello"))
    }

    @Test fun unavailableKeyRefusesImportWithoutShowingSuccess() = runBlocking {
        val c = controller(vault(k = { throw java.security.KeyStoreException("key invalidated") }))
        val r = c.import("x", 0) { ByteArrayInputStream(ByteArray(10)) }
        assertTrue("$r", r is ImportOutcome.Refused && r.reason.contains("vault key is not available"))
        assertTrue(objects().isEmpty())
        assertTrue(c.view.value.files.isEmpty())
    }

    @Test fun exportOfTamperedTruncatedOrWrongKeyObjectWritesNothing() = runBlocking {
        val v = vault()
        val e = v.put("t.txt", "secret".toByteArray())
        val f = File(dir, e.id + LocalVault.SUFFIX)
        val good = f.readBytes()
        val c = controller(v)
        fun flip(pos: Long) = RandomAccessFile(f, "rw").use { it.seek(pos); val b = it.read(); it.seek(pos); it.write(b xor 1) }

        flip(f.length() - 1)
        var out = ByteArrayOutputStream()
        assertTrue(c.exportDecrypted(e.id) { out }.exceptionOrNull() is VaultException.Tampered)
        assertEquals(0, out.size())

        f.writeBytes(good.copyOf(good.size - 4))
        out = ByteArrayOutputStream()
        assertTrue(c.exportDecrypted(e.id) { out }.exceptionOrNull() is VaultException.Corrupt)
        assertEquals(0, out.size())

        f.writeBytes(good)
        val other: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        out = ByteArrayOutputStream()
        assertTrue(controller(vault(k = { other })).exportDecrypted(e.id) { out }.exceptionOrNull() is VaultException.Tampered)
        assertTrue(controller(vault(k = { throw IllegalStateException("no key") })).exportDecrypted(e.id) { out }.exceptionOrNull() is VaultException.KeyUnavailable)
        assertEquals(0, out.size())

        out = ByteArrayOutputStream()
        assertTrue(c.exportDecrypted(e.id) { out }.isSuccess)
        assertArrayEquals("secret".toByteArray(), out.toByteArray())
        assertTrue(c.view.value.message!!.contains("no longer protected by the vault"))
    }

    @Test fun unreadableHeaderIsListedAsUnavailableNotHidden() = runBlocking {
        val v = vault()
        val e = v.put("t.txt", "x".toByteArray())
        File(dir, e.id + LocalVault.SUFFIX).writeBytes(ByteArray(8))
        val c = controller(v)
        c.refresh()
        assertEquals(LocalState.UNAVAILABLE, c.view.value.files.single().local)
    }

    @Test fun deleteIsLocalOnlyAndSaysSo() = runBlocking {
        val v = vault()
        val e = v.put("t.txt", "x".toByteArray())
        val c = controller(v)
        assertTrue(c.deleteLocal(e.id))
        assertTrue(objects().isEmpty())
        assertTrue(c.view.value.message!!.contains("local encrypted copy"))
        assertEquals("DELETED_LOCAL", events.single().action)
        assertTrue(events.single().observation.contains("not proof of physical erasure"))
        assertFalse(c.deleteLocal(e.id))
        assertNull(c.view.value.files.firstOrNull())
    }
}
