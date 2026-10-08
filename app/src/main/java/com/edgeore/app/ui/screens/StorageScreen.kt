package com.edgeore.app.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors
import kotlinx.coroutines.delay

@Composable
fun StorageScreen(vm: EdgeOreViewModel, onBrowser: () -> Unit) {
    val st by vm.storage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var provider by rememberSaveable { mutableStateOf(st.provider) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var exportName by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importVault(uri) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val name = exportName
        if (uri != null && name != null) {
            val bytes = vm.readVault(name)
            if (bytes == null) Toast.makeText(context, "Could not decrypt $name", Toast.LENGTH_LONG).show()
            else context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
    LaunchedEffect(Unit) {
        vm.refreshStorage()
        delay(900)
        vm.refreshStorage()
    }
    val device = st.device
    val total = device?.storageTotal
    val free = device?.storageFree
    val usedDisk = if (total != null && free != null) (total - free).coerceAtLeast(0) else null
    val ring = if (total != null && total > 0 && usedDisk != null) usedDisk.toFloat() / total else 0f

    SectionTitle("Storage & Bandwidth", "Your files. Your connection. Your control.")

    EdgeCard {
        Text("1. Encrypted local storage", style = MaterialTheme.typography.titleMedium)
        Text("AES-256-GCM. The key stays in Android Keystore.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UsageRing(ring)
            Column {
                Text(if (usedDisk == null) "Storage not observed" else "${formatBytes(usedDisk)} used", color = EdgeColors.mint, style = MaterialTheme.typography.titleMedium)
                Text(if (total == null) "Total not observed" else "${formatBytes(total)} on this device", color = EdgeColors.textMuted)
            }
        }
        Text("Used ${shown(usedDisk)}  ·  Available ${shown(free)}  ·  Reserved ${if (st.allocationMb == 0) "none" else "${st.allocationMb} MB"}", style = MaterialTheme.typography.bodyMedium)
        Text("Vault ciphertext ${formatBytes(st.usedBytes)}. That is only files this app encrypted.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Slider(st.allocationMb.toFloat(), { vm.setAllocationMb(it.toInt()) }, valueRange = 0f..2048f, modifier = Modifier.fillMaxWidth().height(48.dp), colors = SliderDefaults.colors(thumbColor = EdgeColors.mint, activeTrackColor = EdgeColors.mint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Restore", Modifier.weight(1f)) { pick.launch(arrayOf("*/*")) }
            SecondaryAction("Manage files", Modifier.weight(1f)) { pick.launch(arrayOf("*/*")) }
        }
        if (st.files.isEmpty()) Text("No vault files yet.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        st.files.forEach { name ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { exportName = name; save.launch(name.removeSuffix(".vault")) }) { Text("Export") }
                TextButton(onClick = { pendingDelete = name }) { Text("Delete", color = EdgeColors.danger) }
            }
        }
    }

    EdgeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("2. Bandwidth sharing", style = MaterialTheme.typography.titleMedium)
                Text(if (st.sharingConsent) "Consent on. This app is not sending shared traffic." else "Sharing OFF", color = if (st.sharingConsent) EdgeColors.mint else EdgeColors.textMuted)
            }
            Switch(st.sharingConsent, vm::setSharingConsent, colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.onAction))
        }
        Text("Received since boot ${shown(device?.rxSinceBoot)}", style = MaterialTheme.typography.bodyMedium)
        Text("Sent since boot ${shown(device?.txSinceBoot)}", style = MaterialTheme.typography.bodyMedium)
        Text("Shared by EdgeORE: 0 B. Consent does not start a sharing protocol.", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)
        Text("Daily quota you set: ${st.quotaMb} MB. It is not a measured total.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Slider(st.quotaMb.toFloat(), { vm.setQuotaMb(it.toInt()) }, valueRange = 50f..2000f, modifier = Modifier.fillMaxWidth().height(48.dp), colors = SliderDefaults.colors(thumbColor = EdgeColors.copper, activeTrackColor = EdgeColors.copper))
        PrimaryAction("Stop all sharing", icon = EdgeIcons.Pause, enabled = st.sharingConsent) { vm.setSharingConsent(false) }
    }

    EdgeCard {
        Text("3. Personal cloud", style = MaterialTheme.typography.titleMedium)
        Text(if (st.provider.isBlank()) "No provider connected. Synced: 0 B." else "Label “${st.provider}” saved. Synced: 0 B. No upload ran.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(provider, { provider = it }, label = { Text("Provider label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SecondaryAction("Configure provider") { vm.setProvider(provider) }
    }

    EdgeCard {
        Text("4. Bandwidth earning", style = MaterialTheme.typography.titleMedium)
        Text("Protocol not qualified", color = EdgeColors.copper)
        Text("ORE rewards: Not observed", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)
        Text("No bandwidth-to-ORE contract is verified. Claim stays off.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        SecondaryAction("Claim (Not available)", enabled = false) {}
    }

    EdgeCard {
        Text("5. In-app browser", style = MaterialTheme.typography.titleMedium)
        Text("Open tabs in this app: 0 until you load one https page. Cookies start off.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        SecondaryAction("Open browser") { onBrowser() }
    }

    EdgeCard {
        Text("6. VPN", style = MaterialTheme.typography.titleMedium)
        Text(
            when (device?.vpnActive) {
                true -> "A VPN transport is active on this device. EdgeORE did not start it."
                false -> "No VPN transport is active."
                null -> "VPN state was not observed."
            },
            color = EdgeColors.copper,
        )
        SecondaryAction("Connect") { vm.setBlockOnDisconnect(st.blockOnDisconnect) }
        Text("Connect does not start a tunnel. No endpoint is configured.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Block traffic on disconnect", modifier = Modifier.weight(1f))
            Switch(st.blockOnDisconnect, vm::setBlockOnDisconnect, colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.onAction))
        }
    }

    EdgeCard {
        Text("7. Resource telemetry", style = MaterialTheme.typography.titleMedium)
        Text("CPU ${device?.cpuPercent?.let { "$it% between readings" } ?: "waiting for a second reading"}", style = MaterialTheme.typography.bodyMedium)
        Text("Battery ${device?.batteryPercent?.let { "$it%" } ?: "not observed"} · drain ${device?.batteryPercentPerHour?.let { "$it% per hour at the current draw" } ?: "not reported"}", style = MaterialTheme.typography.bodyMedium)
        Text("Disk I/O ${device?.ioBytesPerSec?.let { "${formatBytes(it)}/s since the last reading" } ?: "waiting for a second reading"}", style = MaterialTheme.typography.bodyMedium)
        SecondaryAction("Read again") { vm.refreshStorage() }
    }

    EdgeCard {
        Text("8. Scheduling", style = MaterialTheme.typography.titleMedium)
        Text(
            when (device?.metered) {
                true -> "This network is metered."
                false -> "This network is not metered."
                null -> "Metered state was not observed."
            },
            color = EdgeColors.textMuted,
        )
        Text("No idle sync job is registered.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Pause sharing consent on metered")
            Switch(st.pauseOnMetered, vm::setPauseOnMetered, colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.onAction))
        }
    }

    EdgeCard {
        Text("9. Audit trail", style = MaterialTheme.typography.titleMedium)
        Text("${st.auditCount} event(s) written on this phone.", style = MaterialTheme.typography.bodyMedium)
        Text(st.auditOk?.let { if (it) "Chain matches." else "Chain does not match." } ?: "Integrity not checked yet.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Verify integrity", Modifier.weight(1f)) { vm.verifyStorageAudit() }
            SecondaryAction("Export log", Modifier.weight(1f)) {
                val text = vm.storageAuditText()
                val file = java.io.File(context.cacheDir, "edgeore-storage-audit.jsonl")
                file.writeText(text)
                Toast.makeText(context, if (text.isEmpty()) "Audit log is empty." else "Wrote ${file.name} in cache.", Toast.LENGTH_LONG).show()
            }
        }
    }

    EdgeCard {
        Text("10. Permissions", style = MaterialTheme.typography.titleMedium)
        Text("Revoke clears sharing consent and the provider label. Encrypted files stay until you delete them.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Revoke access", Modifier.weight(1f)) { vm.restoreStorageDefaults() }
            SecondaryAction("Restore secure defaults", Modifier.weight(1f)) { vm.restoreStorageDefaults() }
        }
    }
    Text(st.note, color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete $name?") },
            text = { Text("This removes the encrypted file from this phone.") },
            confirmButton = { TextButton(onClick = { vm.deleteVault(name); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun BrowserScreen() {
    var url by rememberSaveable { mutableStateOf("") }
    var go by rememberSaveable { mutableStateOf("") }
    var script by rememberSaveable { mutableStateOf(false) }
    SectionTitle("Private browser", "One WebView. Cookies start off.")
    OutlinedTextField(url, { url = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    SecondaryAction(if (script) "JavaScript on" else "JavaScript off") { script = !script }
    SecondaryAction("Open") { go = url.trim() }
    if (go.startsWith("https://")) {
        AndroidView(
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(false)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = script
                    settings.domStorageEnabled = false
                    loadUrl(go)
                }
            },
            update = { it.settings.javaScriptEnabled = script; if (it.url != go) it.loadUrl(go) },
            modifier = Modifier.fillMaxWidth().height(420.dp),
        )
    } else if (go.isNotEmpty()) {
        Notice("Only https addresses open. Nothing was loaded.", error = true)
    }
}

@Composable
private fun UsageRing(fraction: Float) {
    Canvas(Modifier.size(88.dp)) {
        drawArc(EdgeColors.border, 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx()))
        drawArc(EdgeColors.mint, -90f, 360f * fraction, false, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

private fun shown(n: Long?): String = if (n == null) "not observed" else formatBytes(n)

private fun formatBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${n / 1024} KB"
    n < 1024L * 1024 * 1024 -> "%.1f MB".format(n / (1024.0 * 1024.0))
    else -> "%.2f GB".format(n / (1024.0 * 1024.0 * 1024.0))
}
