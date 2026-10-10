package com.edgeore.app.storage

import com.edgeore.app.io.BoundedInput
import com.edgeore.app.io.InputTooLargeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/** Local state of one vault object. */
enum class LocalState(val label: String) {
    ENCRYPTED_LOCALLY("Encrypted locally"),
    UNAVAILABLE("Unavailable"),
}

/** Remote-backup state of one vault object, kept separate from [LocalState]. */
enum class RemoteState(val label: String) {
    NOT_CONFIGURED("Not configured"),
    NO_BACKUP("No backup"),
    UPLOADING("Uploading"),
    UPLOAD_ACKNOWLEDGED("Upload acknowledged"),
    PINNED("Pinned on configured node"),
    RESTORE_VERIFIED("Restore verified"),
    UNAVAILABLE("Unavailable"),
    OUTCOME_UNKNOWN("Outcome unknown"),
    FAILED("Failed"),
    CANCELED("Canceled"),
}

data class VaultFileView(val entry: VaultEntry, val local: LocalState, val remote: RemoteState, val job: BackupJob?)

data class VaultView(
    val files: List<VaultFileView> = emptyList(),
    val usedBytes: Long = 0,
    val backup: ProviderAvailability = ProviderAvailability.NotConfigured,
    val busy: Boolean = false,
    val message: String? = null,
)

sealed interface ImportOutcome {
    data class Published(val entry: VaultEntry, val encryptedSha256: String) : ImportOutcome
    data class Refused(val reason: String) : ImportOutcome
    data object Canceled : ImportOutcome
}

object StorageLabels {
    /**
     * Remote state is derived only from what the job records. An acknowledged or pinned upload never
     * becomes "Restore verified"; that needs a completed restore check of the same job.
     */
    fun remoteState(job: BackupJob?, availability: ProviderAvailability): RemoteState {
        if (job == null) return when (availability) {
            is ProviderAvailability.Configured -> RemoteState.NO_BACKUP
            is ProviderAvailability.NotConfigured -> RemoteState.NOT_CONFIGURED
            is ProviderAvailability.Unavailable -> RemoteState.UNAVAILABLE
        }
        if (job.restoreStage == RestoreStage.RESTORE_VERIFIED && job.restoredObjectId != null &&
            (job.stage == BackupStage.PINNED || job.stage == BackupStage.UPLOAD_ACKNOWLEDGED)) return RemoteState.RESTORE_VERIFIED
        return when (job.stage) {
            BackupStage.QUEUED, BackupStage.UPLOADING -> RemoteState.UPLOADING
            BackupStage.UPLOAD_ACKNOWLEDGED -> RemoteState.UPLOAD_ACKNOWLEDGED
            BackupStage.PINNED -> RemoteState.PINNED
            BackupStage.OUTCOME_UNKNOWN, BackupStage.CANCEL_REQUESTED -> RemoteState.OUTCOME_UNKNOWN
            BackupStage.FAILED -> RemoteState.FAILED
            BackupStage.CANCELED -> RemoteState.CANCELED
        }
    }

    fun localState(e: VaultEntry): LocalState = if (!e.legacy && e.plainBytes < 0) LocalState.UNAVAILABLE else LocalState.ENCRYPTED_LOCALLY

    fun describe(e: Throwable): String = when (e) {
        is VaultException -> e.message ?: e.javaClass.simpleName
        is BackupUnavailableException -> e.message ?: "Remote backup unavailable"
        else -> e.message ?: e.javaClass.simpleName
    }
}

/**
 * Vault operations for the Storage screen, independent of Android so they can be tested on the JVM.
 * All file I/O and encryption run on [io]. The visible list ([view]) is refreshed from disk only
 * after an import was published (temp file fsynced and renamed), so the UI never shows a file that
 * is not durably stored.
 */
class StorageController(
    private val vault: LocalVault,
    private val jobs: BackupJobStore,
    private val provider: BackupProvider,
    private val audit: StorageAudit? = null,
    private val onEvent: suspend (StorageEvent) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val _view = MutableStateFlow(VaultView(backup = provider.availability()))
    val view: StateFlow<VaultView> = _view.asStateFlow()

    private fun snapshot(message: String?): VaultView {
        val availability = provider.availability()
        val files = vault.entries().map { e ->
            val job = jobs.latestFor(e.id)
            VaultFileView(e, StorageLabels.localState(e), StorageLabels.remoteState(job, availability), job)
        }
        return VaultView(files, vault.usedBytes(), availability, busy = false, message = message)
    }

    suspend fun refresh(message: String? = _view.value.message) {
        val v = withContext(io) { snapshot(message) }
        _view.value = v
    }

    private fun setBusy(message: String) { _view.value = _view.value.copy(busy = true, message = message) }

    /**
     * Imports one document. [open] is called on [io]. The byte limit (per-file cap, or what is left of
     * the allowance if that is smaller) is enforced while reading, so a provider that reports no
     * length or a wrong length cannot make the app buffer more than the limit + 1 byte.
     */
    suspend fun import(displayName: String?, allowanceBytes: Long, open: () -> InputStream?): ImportOutcome {
        setBusy("Encrypting…")
        val outcome = try {
            withContext(io) {
                val name = displayName?.takeIf { it.isNotBlank() } ?: "file"
                val limit = vault.maxImportBytes(allowanceBytes, name.toByteArray(Charsets.UTF_8).size.coerceAtMost(480))
                val bytes = try {
                    (open() ?: throw VaultException.SourceUnavailable("the provider returned no data")).use { BoundedInput.readAtMost(it, limit) }
                } catch (_: InputTooLargeException) {
                    throw if (limit < vault.perFileLimit) VaultException.OverAllowance(limit + 1, vault.allowanceRemaining(allowanceBytes) ?: 0) else VaultException.TooLarge(vault.perFileLimit)
                } catch (_: SecurityException) {
                    throw VaultException.SourceUnavailable("permission was revoked")
                } catch (_: FileNotFoundException) {
                    throw VaultException.SourceUnavailable("the document is no longer available")
                } catch (e: IOException) {
                    throw VaultException.SourceUnavailable(e.message ?: "read failed")
                }
                val ctx = coroutineContext
                ctx.ensureActive()
                val entry = vault.put(name, bytes, allowanceBytes, beforePublish = { ctx.ensureActive() })
                val sha = vault.objectSha256(entry.id)
                audit?.append("encrypted object ${entry.id}")
                ImportOutcome.Published(entry, sha)
            }
        } catch (e: CancellationException) {
            _view.value = _view.value.copy(busy = false, message = "Import canceled. Nothing was saved.")
            refresh()
            throw e
        } catch (e: VaultException) {
            ImportOutcome.Refused(StorageLabels.describe(e))
        } catch (e: IOException) {
            ImportOutcome.Refused("could not write the encrypted file (${e.message ?: e.javaClass.simpleName})")
        }
        when (outcome) {
            is ImportOutcome.Published -> {
                refresh("Encrypted ${outcome.entry.displayName} on this device. The plaintext was not kept.")
                onEvent(StorageEvent("IMPORTED_ENCRYPTED", newId(), outcome.entry.id, outcome.encryptedSha256, "Published to the vault after fsync and atomic rename"))
            }
            is ImportOutcome.Refused -> refresh("Import failed, nothing was saved: ${outcome.reason}")
            ImportOutcome.Canceled -> refresh("Import canceled. Nothing was saved.")
        }
        return outcome
    }

    /** Writes a decrypted copy to [open]'s stream. Only called after the user confirmed the warning. */
    suspend fun exportDecrypted(id: String, open: () -> OutputStream?): Result<Int> {
        setBusy("Decrypting…")
        val r = withContext(io) {
            runCatching {
                val sha = vault.objectSha256(id)
                val bytes = vault.read(id)
                (open() ?: throw VaultException.SourceUnavailable("destination unavailable")).use { it.write(bytes) }
                audit?.append("exported decrypted copy of $id")
                sha to bytes.size
            }
        }
        r.onSuccess { (sha, _) -> onEvent(StorageEvent("EXPORTED_DECRYPTED_COPY", newId(), id, sha, "Plaintext copy written to a user-chosen document; it is no longer protected by the vault")) }
        refresh(r.fold({ "Exported a decrypted copy (${it.second} bytes). That copy is no longer protected by the vault." }, { "Export failed: ${StorageLabels.describe(it)}" }))
        return r.map { it.second }
    }

    /** Deletes the local encrypted object only. Remote copies are not touched by this action. */
    suspend fun deleteLocal(id: String): Boolean {
        val (ok, sha) = withContext(io) {
            val sha = runCatching { vault.objectSha256(id) }.getOrNull()
            val ok = vault.delete(id)
            audit?.append(if (ok) "deleted object $id" else "delete missed $id")
            ok to sha
        }
        if (ok) onEvent(StorageEvent("DELETED_LOCAL", newId(), id, sha, "Local file removed by the app; not proof of physical erasure", null))
        val remote = jobs.latestFor(id)?.takeIf { it.stage == BackupStage.PINNED || it.stage == BackupStage.UPLOAD_ACKNOWLEDGED }
        refresh(when {
            !ok -> "That file was not in the vault."
            remote != null -> "Deleted the local encrypted copy. The remote backup was not deleted."
            else -> "Deleted the local encrypted copy from this phone."
        })
        return ok
    }
}
