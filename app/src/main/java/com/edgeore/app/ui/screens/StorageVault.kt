package com.edgeore.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edgeore.app.StorageState
import com.edgeore.app.settings.Control
import com.edgeore.app.storage.BackupStage
import com.edgeore.app.storage.LocalState
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.ProviderAvailability
import com.edgeore.app.storage.RemoteState
import com.edgeore.app.storage.RestoreStage
import com.edgeore.app.storage.VaultFileView
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.EffectNote
import com.edgeore.app.ui.components.KeyValue
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors

/**
 * The Storage page's vault and remote-backup sections. Stateless apart from dialog visibility, so it
 * can be rendered on the JVM. Every figure shown is read from the vault or the device; nothing here
 * invents a capacity, progress value, CID or success.
 */
@Composable
fun StorageVaultSection(
    st: StorageState,
    onImport: () -> Unit,
    onExport: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onAllowance: (Int) -> Unit,
) {
    var confirmExport by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var inspect by rememberSaveable { mutableStateOf<String?>(null) }
    var configure by rememberSaveable { mutableStateOf(false) }
    val free = st.device?.storageFree
    val allowanceBytes = st.allocationMb * 1024L * 1024L

    SectionTitle("Storage", "Encrypted on this device. Backup stays under your control.")

    EdgeCard {
        Text("Encrypted vault", style = MaterialTheme.typography.titleMedium)
        Text("AES-256-GCM. The key stays in this phone's Android Keystore.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        KeyValue("Vault usage", "${formatBytes(st.usedBytes)} encrypted · ${st.files.size} file(s)")
        KeyValue("Configured allowance", if (st.allocationMb == 0) "Not set" else "${st.allocationMb} MB · ${formatBytes((allowanceBytes - st.usedBytes).coerceAtLeast(0))} left")
        KeyValue("Available on device", if (free == null) "Not observed" else formatBytes(free))
        Text("Limits enforced while reading: ${LocalVault.MAX_PLAIN_BYTES / 1_000_000} MB per file, the allowance if set, and phone free space.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Slider(st.allocationMb.toFloat(), { onAllowance(it.toInt()) }, valueRange = 0f..2048f, modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = SliderDefaults.colors(thumbColor = EdgeColors.mint, activeTrackColor = EdgeColors.mint))
        EffectNote(Control.VAULT_ALLOWANCE)
        PrimaryAction(if (st.busy) "Working…" else "Import file", icon = EdgeIcons.Storage, enabled = !st.busy, loading = st.busy) { onImport() }
        Text("Only this installation's Keystore key can decrypt these files. Uninstalling the app, resetting the phone or losing the device-bound key makes them unrecoverable.",
            color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)
        if (st.note.isNotBlank()) Text(st.note, color = EdgeColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
    }

    EdgeCard {
        Text("Files", style = MaterialTheme.typography.titleMedium)
        if (st.views.isEmpty()) {
            Text("No files in the vault yet.", style = MaterialTheme.typography.bodyMedium)
            Text("Tap Import file and choose a document. EdgeORE encrypts it on this phone and keeps only the encrypted copy.",
                color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
        st.views.forEach { f -> FileRow(f, onExport = { confirmExport = f.entry.id }, onInspect = { inspect = f.entry.id }, onDelete = { confirmDelete = f.entry.id }) }
    }

    EdgeCard {
        Text("Remote backup", style = MaterialTheme.typography.titleMedium)
        val (label, color) = when (val b = st.backup) {
            is ProviderAvailability.NotConfigured -> "Not configured" to EdgeColors.copper
            is ProviderAvailability.Unavailable -> "Unavailable: ${b.reason}" to EdgeColors.copper
            is ProviderAvailability.Configured -> "Configured: ${b.label}" to EdgeColors.mint
        }
        Text(label, color = color)
        Text("Optional device-bound encrypted backup: this phone → an authenticated EdgeORE backend → your own IPFS node. Only ciphertext would be sent; file names stay on this phone.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Text("Nothing is uploaded until a backend is configured. Cross-device recovery is not implemented.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        SecondaryAction("Configure remote backup") { configure = true }
    }

    fun find(id: String?) = st.views.firstOrNull { it.entry.id == id }

    find(confirmExport)?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmExport = null },
            title = { Text("Export a decrypted copy?") },
            text = { Text("EdgeORE will decrypt ${f.entry.displayName} and write it to a location you choose. That copy is no longer protected by the vault.") },
            confirmButton = { TextButton(onClick = { confirmExport = null; onExport(f.entry.id, f.entry.displayName) }) { Text("Export decrypted copy") } },
            dismissButton = { TextButton(onClick = { confirmExport = null }) { Text("Cancel") } },
        )
    }

    find(confirmDelete)?.let { f ->
        val remote = f.job?.takeIf { it.stage == BackupStage.PINNED || it.stage == BackupStage.UPLOAD_ACKNOWLEDGED }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${f.entry.displayName} from this phone?") },
            text = {
                Text("This deletes the local encrypted copy. It cannot be undone. " +
                    if (remote != null) "The remote backup is not deleted by this action; remove it on your node." else "There is no remote backup of this file.")
            },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDelete(f.entry.id) }) { Text("Delete local copy", color = EdgeColors.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    find(inspect)?.let { f ->
        AlertDialog(
            onDismissRequest = { inspect = null },
            title = { Text("Backup status") },
            text = { BackupStatusDetail(f) },
            confirmButton = { TextButton(onClick = { inspect = null }) { Text("Close") } },
        )
    }

    if (configure) {
        AlertDialog(
            onDismissRequest = { configure = false },
            title = { Text("Remote backup: Not configured") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This build has no EdgeORE backup backend to connect to, so there is nothing to configure yet and no upload can run.")
                    Text("A backend must authenticate this phone, keep IPFS credentials and administrative endpoints on the server, and report a pin only after your node acknowledges it.")
                    Text("Backups would be device-bound: only this installation's Keystore key can decrypt them. Cross-device recovery is not implemented.", color = EdgeColors.copper)
                }
            },
            confirmButton = { TextButton(onClick = { configure = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun FileRow(f: VaultFileView, onExport: () -> Unit, onInspect: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, EdgeColors.border, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(f.entry.displayName, style = MaterialTheme.typography.titleSmall)
        Text(if (f.entry.plainBytes >= 0) formatBytes(f.entry.plainBytes) else "Size unknown" + if (f.entry.legacy) " · legacy format" else "",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        StateLine("Local", f.local.label, if (f.local == LocalState.ENCRYPTED_LOCALLY) EdgeColors.mint else EdgeColors.danger)
        StateLine("Remote backup", f.remote.label, remoteColor(f.remote))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExport, enabled = f.local == LocalState.ENCRYPTED_LOCALLY) { Text("Export") }
            TextButton(onClick = onInspect) { Text("Backup status") }
            TextButton(onClick = onDelete) { Text("Delete", color = EdgeColors.danger) }
        }
    }
}

@Composable
private fun StateLine(key: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun remoteColor(r: RemoteState): Color = when (r) {
    RemoteState.PINNED, RemoteState.RESTORE_VERIFIED -> EdgeColors.mint
    RemoteState.FAILED -> EdgeColors.danger
    RemoteState.NO_BACKUP -> EdgeColors.textMuted
    else -> EdgeColors.copper
}

/** Each checkpoint is shown separately; an earlier one never implies a later one. */
@Composable
private fun BackupStatusDetail(f: VaultFileView) {
    val j = f.job
    val restore = j?.restoreStage ?: RestoreStage.NONE
    val reached = { s: RestoreStage -> j != null && restore != RestoreStage.FAILED && restore.ordinal >= s.ordinal }
    fun mark(b: Boolean) = if (b) "Observed" else "Not observed"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyValue("Local", f.local.label)
        KeyValue("Remote backup", f.remote.label)
        KeyValue("Upload acknowledged", mark(j != null && (j.stage == BackupStage.UPLOAD_ACKNOWLEDGED || j.stage == BackupStage.PINNED)))
        KeyValue("Pinned on configured node", mark(j?.stage == BackupStage.PINNED))
        KeyValue("Downloaded", mark(reached(RestoreStage.DOWNLOADED)))
        KeyValue("Successfully decrypted", mark(reached(RestoreStage.DECRYPTED)))
        KeyValue("Restore verified", mark(reached(RestoreStage.RESTORE_VERIFIED)))
        j?.lastObservation?.let { Text(it, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium) }
        j?.restoreDetail?.let { Text(it, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium) }
        if (j == null) Text("No backup job exists for this file.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Text("Device-bound encrypted backup only: cross-device recovery is not implemented.", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)
    }
}
