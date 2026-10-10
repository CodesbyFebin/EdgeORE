package com.edgeore.app.storage

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.io.AtomicFiles
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

/*
 * Optional device-bound encrypted backup: Android -> authenticated EdgeORE backend -> owned IPFS node.
 *
 * No EdgeORE backend exists in this repository, so the app ships only [NotConfiguredBackupProvider].
 * Everything below is the contract and the client-side state machine a real backend client must
 * plug into. It never simulates an upload: with the shipped provider every remote action is refused.
 *
 * What is uploaded is ciphertext only: the object re-sealed by [LocalVault.sealForBackup] with the
 * same device-bound Android Keystore key and an empty name field. That key never leaves the phone,
 * so a backup can only be decrypted by this installation. This is NOT cross-device recovery, which
 * is not implemented.
 */

/** Whether a remote backup route exists. A label is an owner-chosen name, never a credential. */
sealed interface ProviderAvailability {
    data object NotConfigured : ProviderAvailability
    data class Unavailable(val reason: String) : ProviderAvailability
    data class Configured(val label: String) : ProviderAvailability
}

/**
 * Exactly what leaves the phone. There is deliberately no filename, MIME type or plaintext digest
 * field, so private metadata cannot reach provider metadata through this interface.
 */
class UploadRequest(val clientJobId: String, val ciphertext: ByteArray, val ciphertextSha256: String, val ciphertextBytes: Long)

/**
 * What the backend reported. Only [Pinned] means the configured node acknowledged pinning; an
 * [Acknowledged] upload is not a pin, and neither is ever a restore verification. A remote reference
 * (for example a CID) alone never implies availability.
 */
sealed interface ProviderObservation {
    /** The backend has no record of this job. */
    data object NotFound : ProviderObservation
    data class Acknowledged(val remoteRef: String, val ciphertextSha256: String, val at: String) : ProviderObservation
    data class Pinned(val remoteRef: String, val ciphertextSha256: String, val nodeAckAt: String, val node: String) : ProviderObservation
    data class Failed(val reason: String) : ProviderObservation
    data class Canceled(val at: String) : ProviderObservation
}

class BackupUnavailableException(message: String) : Exception(message)

/**
 * Client side of the authenticated EdgeORE backend. Implementations keep credentials out of logs and
 * receipts, enforce request/response size limits and timeouts, and never talk to an IPFS
 * administrative API directly (that stays on the backend).
 */
interface BackupProvider {
    fun availability(): ProviderAvailability
    /** Idempotent per [UploadRequest.clientJobId]. */
    suspend fun upload(request: UploadRequest): ProviderObservation
    suspend fun status(clientJobId: String): ProviderObservation
    /** Streams at most [maxBytes] into [sink]; must fail rather than write more. Returns bytes written. */
    suspend fun download(remoteRef: String, maxBytes: Long, sink: OutputStream): Long
    /** True only if the backend acknowledged the cancellation. */
    suspend fun cancel(clientJobId: String): Boolean
}

/** The only provider in this build. Every remote action is refused; nothing is simulated. */
object NotConfiguredBackupProvider : BackupProvider {
    const val REASON = "No EdgeORE backup backend is configured in this build. Remote backup is unavailable."
    override fun availability(): ProviderAvailability = ProviderAvailability.NotConfigured
    override suspend fun upload(request: UploadRequest): ProviderObservation = throw BackupUnavailableException(REASON)
    override suspend fun status(clientJobId: String): ProviderObservation = throw BackupUnavailableException(REASON)
    override suspend fun download(remoteRef: String, maxBytes: Long, sink: OutputStream): Long = throw BackupUnavailableException(REASON)
    override suspend fun cancel(clientJobId: String): Boolean = throw BackupUnavailableException(REASON)
}

enum class BackupStage { QUEUED, UPLOADING, UPLOAD_ACKNOWLEDGED, PINNED, OUTCOME_UNKNOWN, FAILED, CANCEL_REQUESTED, CANCELED }

/** Restore checkpoints, each recorded only after it actually happened. */
enum class RestoreStage { NONE, DOWNLOADED, DECRYPTED, RESTORE_VERIFIED, FAILED }

/**
 * One backup job, persisted before any network work. [displayName] and [plainSha256] stay in
 * app-private storage on this phone; they are never uploaded or written to receipts.
 */
data class BackupJob(
    val jobId: String,
    val objectId: String,
    val displayName: String,
    val stage: BackupStage,
    val ciphertextSha256: String,
    val ciphertextBytes: Long,
    val plainSha256: String,
    val plainBytes: Long,
    val createdAt: String,
    val updatedAt: String,
    val remoteRef: String? = null,
    val lastObservation: String? = null,
    val restoreStage: RestoreStage = RestoreStage.NONE,
    val restoreDetail: String? = null,
    val restoredObjectId: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("jobId", jobId).put("objectId", objectId).put("displayName", displayName)
        .put("stage", stage.name).put("ciphertextSha256", ciphertextSha256).put("ciphertextBytes", ciphertextBytes)
        .put("plainSha256", plainSha256).put("plainBytes", plainBytes).put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("remoteRef", remoteRef ?: JSONObject.NULL).put("lastObservation", lastObservation ?: JSONObject.NULL)
        .put("restoreStage", restoreStage.name).put("restoreDetail", restoreDetail ?: JSONObject.NULL)
        .put("restoredObjectId", restoredObjectId ?: JSONObject.NULL)

    companion object {
        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)
        fun fromJson(o: JSONObject) = BackupJob(
            o.getString("jobId"), o.getString("objectId"), o.getString("displayName"), BackupStage.valueOf(o.getString("stage")),
            o.getString("ciphertextSha256"), o.getLong("ciphertextBytes"), o.getString("plainSha256"), o.getLong("plainBytes"),
            o.getString("createdAt"), o.getString("updatedAt"), o.str("remoteRef"), o.str("lastObservation"),
            RestoreStage.valueOf(o.getString("restoreStage")), o.str("restoreDetail"), o.str("restoredObjectId"),
        )
    }
}

/** Crash-safe job store (atomic replace). A damaged file fails closed: no new jobs, never "no backups". */
class BackupJobStore(private val file: File) {
    private val lock = Any()
    private val jobs = LinkedHashMap<String, BackupJob>()
    /** Non-null when the store could not be read; every write is refused. */
    val unavailableReason: String?

    init {
        var reason: String? = null
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) BackupJob.fromJson(arr.getJSONObject(i)).let { jobs[it.jobId] = it }
            } catch (e: Exception) {
                jobs.clear()
                reason = "The backup job store could not be read (${e.javaClass.simpleName}). Remote backup actions are disabled."
            }
        }
        unavailableReason = reason
    }

    fun all(): List<BackupJob> = synchronized(lock) { jobs.values.toList() }
    fun get(jobId: String): BackupJob? = synchronized(lock) { jobs[jobId] }
    /** Latest job for a vault object, by creation order. */
    fun latestFor(objectId: String): BackupJob? = synchronized(lock) { jobs.values.lastOrNull { it.objectId == objectId } }

    /** Durable before return; memory changes only after the file write succeeded. */
    fun put(job: BackupJob) = synchronized(lock) {
        unavailableReason?.let { throw IOException(it) }
        val next = LinkedHashMap(jobs).apply { put(job.jobId, job) }
        AtomicFiles.write(file, JSONArray(next.values.map { it.toJson() }).toString().toByteArray(Charsets.UTF_8))
        jobs.clear(); jobs.putAll(next)
    }
}

/** A storage event for receipts. Carries identifiers, digests of encrypted bytes and provider observations only. */
data class StorageEvent(
    val action: String,
    val operationId: String,
    val objectId: String?,
    val encryptedSha256: String?,
    val observation: String,
    val providerObservation: String? = null,
)

/** Thrown by a size-capped sink when the provider tries to write more than allowed. */
class DownloadTooLargeException(limit: Long) : IOException("Download exceeded $limit bytes")

/** Bounded temporary storage for downloads: refuses byte limit + 1. */
class BoundedFileSink(private val file: File, private val limit: Long) : OutputStream() {
    private val out = FileOutputStream(file)
    var written = 0L; private set
    override fun write(b: Int) { if (written + 1 > limit) throw DownloadTooLargeException(limit); out.write(b); written++ }
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (written + len > limit) throw DownloadTooLargeException(limit)
        out.write(b, off, len); written += len
    }
    override fun flush() = out.flush()
    override fun close() { out.flush(); out.fd.sync(); out.close() }
}

sealed interface RestoreOutcome {
    data class Verified(val job: BackupJob, val restored: VaultEntry) : RestoreOutcome
    data class Failed(val job: BackupJob?, val reason: String) : RestoreOutcome
}

/**
 * Client-side backup state machine. Every stage change is written to [store] before the next
 * network call, so a process death leaves a state that [recoverAfterRestart] can turn into
 * OUTCOME_UNKNOWN and [reconcile] can resolve by asking the backend.
 */
class BackupCoordinator(
    private val vault: LocalVault,
    private val store: BackupJobStore,
    private val provider: BackupProvider,
    private val restoreDir: File,
    private val onEvent: suspend (StorageEvent) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private fun now() = clock().toString()

    private fun requireConfigured() {
        when (val a = provider.availability()) {
            is ProviderAvailability.Configured -> Unit
            is ProviderAvailability.NotConfigured -> throw BackupUnavailableException(NotConfiguredBackupProvider.REASON)
            is ProviderAvailability.Unavailable -> throw BackupUnavailableException(a.reason)
        }
        store.unavailableReason?.let { throw BackupUnavailableException(it) }
    }

    private fun save(job: BackupJob): BackupJob = job.copy(updatedAt = now()).also { store.put(it) }

    /** Upload ciphertext for [objectId]. Refused (nothing persisted) when no provider is configured. */
    suspend fun backup(objectId: String, displayName: String): BackupJob {
        requireConfigured()
        val blob = vault.sealForBackup(objectId)
        val t = now()
        var job = save(BackupJob(newId(), objectId, displayName, BackupStage.QUEUED, blob.ciphertextSha256, blob.ciphertextBytes, blob.plainSha256, blob.plainBytes, t, t))
        job = save(job.copy(stage = BackupStage.UPLOADING))   // persisted before the network call
        val obs = try {
            provider.upload(UploadRequest(job.jobId, blob.ciphertext, blob.ciphertextSha256, blob.ciphertextBytes))
        } catch (e: CancellationException) {
            save(job.copy(stage = BackupStage.OUTCOME_UNKNOWN, lastObservation = "Canceled while the upload was in flight; the backend may have received it"))
            throw e
        } catch (e: Exception) {
            return save(job.copy(stage = BackupStage.OUTCOME_UNKNOWN, lastObservation = "Upload did not complete (${e.javaClass.simpleName}); the backend may or may not have received it")).also { emit("BACKUP_OUTCOME_UNKNOWN", it, null) }
        }
        return apply(job, obs)
    }

    /** No network: interrupted transitions become OUTCOME_UNKNOWN, never success. */
    fun recoverAfterRestart(): Int {
        if (store.unavailableReason != null) return 0
        var n = 0
        for (j in store.all()) {
            when (j.stage) {
                BackupStage.QUEUED, BackupStage.UPLOADING, BackupStage.CANCEL_REQUESTED -> {
                    save(j.copy(stage = BackupStage.OUTCOME_UNKNOWN, lastObservation = "App stopped during ${j.stage.name.lowercase()}; outcome not observed")); n++
                }
                else -> Unit
            }
        }
        return n
    }

    /** Ask the backend about one job and record exactly what it reported. */
    suspend fun reconcile(jobId: String): BackupJob {
        requireConfigured()
        val job = store.get(jobId) ?: throw BackupUnavailableException("Unknown backup job")
        val obs = try { provider.status(jobId) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            return save(job.copy(lastObservation = "Status not observed (${e.javaClass.simpleName})"))
        }
        return apply(job, obs)
    }

    suspend fun reconcileAll(): List<BackupJob> = store.all()
        .filter { it.stage == BackupStage.OUTCOME_UNKNOWN || it.stage == BackupStage.UPLOAD_ACKNOWLEDGED }
        .map { reconcile(it.jobId) }

    suspend fun cancel(jobId: String): BackupJob {
        requireConfigured()
        var job = store.get(jobId) ?: throw BackupUnavailableException("Unknown backup job")
        job = save(job.copy(stage = BackupStage.CANCEL_REQUESTED))
        val acked = try { provider.cancel(jobId) } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        return if (acked) save(job.copy(stage = BackupStage.CANCELED, lastObservation = "Backend acknowledged cancellation"))
        else save(job.copy(stage = BackupStage.OUTCOME_UNKNOWN, lastObservation = "Cancellation not acknowledged by the backend"))
    }

    private suspend fun apply(job: BackupJob, obs: ProviderObservation): BackupJob {
        val next = when (obs) {
            is ProviderObservation.Pinned ->
                if (obs.ciphertextSha256 != job.ciphertextSha256) job.copy(stage = BackupStage.FAILED, lastObservation = "Backend reported a different ciphertext digest")
                else job.copy(stage = BackupStage.PINNED, remoteRef = obs.remoteRef, lastObservation = "Node ${obs.node} acknowledged pin at ${obs.nodeAckAt}")
            is ProviderObservation.Acknowledged ->
                if (obs.ciphertextSha256 != job.ciphertextSha256) job.copy(stage = BackupStage.FAILED, lastObservation = "Backend reported a different ciphertext digest")
                // Acknowledged is not pinned. A remote reference alone never implies availability.
                else job.copy(stage = BackupStage.UPLOAD_ACKNOWLEDGED, remoteRef = obs.remoteRef, lastObservation = "Backend acknowledged upload at ${obs.at}; pin not confirmed")
            is ProviderObservation.NotFound -> job.copy(stage = BackupStage.OUTCOME_UNKNOWN, lastObservation = "Backend has no record of this job")
            is ProviderObservation.Failed -> job.copy(stage = BackupStage.FAILED, lastObservation = "Backend reported failure: ${obs.reason}")
            is ProviderObservation.Canceled -> job.copy(stage = BackupStage.CANCELED, lastObservation = "Backend reported canceled at ${obs.at}")
        }
        return save(next).also { emit("BACKUP_${it.stage.name}", it, it.lastObservation) }
    }

    /**
     * Restore verification. Downloads into bounded temporary storage, checks ciphertext length and
     * digest, decrypts through [LocalVault.openEnvelope], checks the plaintext digest recorded at
     * backup time, then publishes a NEW vault object atomically. The original is never overwritten.
     */
    suspend fun restore(jobId: String, allowanceBytes: Long = 0): RestoreOutcome {
        requireConfigured()
        var job = store.get(jobId) ?: return RestoreOutcome.Failed(null, "Unknown backup job")
        val ref = job.remoteRef
        if (ref == null || (job.stage != BackupStage.PINNED && job.stage != BackupStage.UPLOAD_ACKNOWLEDGED))
            return RestoreOutcome.Failed(job, "No acknowledged remote copy to restore from")
        restoreDir.mkdirs()
        val tmp = File(restoreDir, "restore-${job.jobId}${AtomicFiles.TEMP_SUFFIX}")
        fun fail(why: String): RestoreOutcome.Failed {
            job = save(job.copy(restoreStage = RestoreStage.FAILED, restoreDetail = why))
            return RestoreOutcome.Failed(job, why)
        }
        try {
            try {
                BoundedFileSink(tmp, job.ciphertextBytes).use { sink -> provider.download(ref, job.ciphertextBytes, sink) }
            } catch (e: CancellationException) { throw e } catch (e: DownloadTooLargeException) {
                return fail("Downloaded data is longer than the recorded ciphertext").also { emit("RESTORE_FAILED", job, it.reason) }
            } catch (e: Exception) {
                return fail("Download did not complete (${e.javaClass.simpleName})").also { emit("RESTORE_FAILED", job, it.reason) }
            }
            if (tmp.length() != job.ciphertextBytes) return fail("Downloaded length ${tmp.length()} does not match recorded ${job.ciphertextBytes}").also { emit("RESTORE_FAILED", job, it.reason) }
            val bytes = tmp.readBytes()
            if (Sha256.hex(bytes) != job.ciphertextSha256) return fail("Downloaded ciphertext digest does not match the recorded digest").also { emit("RESTORE_FAILED", job, it.reason) }
            job = save(job.copy(restoreStage = RestoreStage.DOWNLOADED, restoreDetail = "Ciphertext length and digest match"))
            val plain = try { vault.openEnvelope(bytes, job.objectId) } catch (e: VaultException) {
                return fail("Decryption refused: ${e.message}").also { emit("RESTORE_FAILED", job, it.reason) }
            }
            job = save(job.copy(restoreStage = RestoreStage.DECRYPTED, restoreDetail = "Authenticated and decrypted with this phone's vault key"))
            if (plain.size.toLong() != job.plainBytes || Sha256.hex(plain) != job.plainSha256)
                return fail("Decrypted content does not match the digest recorded at backup time").also { emit("RESTORE_FAILED", job, it.reason) }
            val entry = try { vault.put("${job.displayName} (restored)", plain, allowanceBytes) } catch (e: VaultException) {
                return fail("Verified, but could not be saved: ${e.message}").also { emit("RESTORE_FAILED", job, it.reason) }
            }
            job = save(job.copy(restoreStage = RestoreStage.RESTORE_VERIFIED, restoredObjectId = entry.id, restoreDetail = "Verified and saved as a new vault object"))
            emit("RESTORE_VERIFIED", job, job.restoreDetail)
            return RestoreOutcome.Verified(job, entry)
        } finally {
            tmp.delete()
        }
    }

    private suspend fun emit(action: String, job: BackupJob, providerObservation: String?) {
        runCatching { onEvent(StorageEvent(action, job.jobId, job.objectId, job.ciphertextSha256, "backup job ${job.stage.name}, restore ${job.restoreStage.name}", providerObservation)) }
    }
}
