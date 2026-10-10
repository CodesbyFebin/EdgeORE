package com.edgeore.app.ui.screens

import com.edgeore.app.ui.components.EffectNote
import com.edgeore.app.settings.Control
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.receipts.ReceiptLog
import com.edgeore.app.receipts.StoredReceipt
import com.edgeore.app.ui.Format
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.KeyValue
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val NODE_KINDS = setOf("NODE")
private val REVIEW_KINDS = setOf("REVIEW")
private val dayLabel = DateTimeFormatter.ofPattern("d MMM")
private val clock = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun ReceiptsScreen(vm: EdgeOreViewModel, onOpen: (StoredReceipt) -> Unit, onExport: (StoredReceipt?, Boolean) -> Unit) {
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val verify by vm.verify.collectAsStateWithLifecycle()
    val damage by vm.receiptDamage.collectAsStateWithLifecycle()
    val observation by vm.observation.collectAsStateWithLifecycle()
    val wallet by vm.walletState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    var menu by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var why by rememberSaveable { mutableStateOf(false) }
    var hideDevice by rememberSaveable { mutableStateOf(true) }
    var includeLocation by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var retention by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.verifyImported(uri) }

    SectionTitle("Receipts", "Understand every action. Keep your proof.")
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search receipts, jobs or signatures") },
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Reviews", "Node").forEach { f ->
            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
        }
        Box {
            FilterChip(selected = menu || sort != "Newest", onClick = { menu = true }, label = { Text(sort) })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf("Newest", "Oldest", "Refused first", "Devnet only").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; menu = false })
                }
            }
        }
    }
    val needle = query.trim().lowercase()
    val shown = receipts.filter { r ->
        val kindOk = when (filter) { "Reviews" -> r.kind in REVIEW_KINDS; "Node" -> r.kind in NODE_KINDS; else -> true }
        val netOk = sort != "Devnet only" || r.cluster == "devnet"
        val text = listOf(r.title, r.detail, r.outcome, r.id, r.solanaSignature ?: "", r.source).joinToString(" ").lowercase()
        kindOk && netOk && (needle.isEmpty() || text.contains(needle))
    }.let { list ->
        when (sort) {
            "Oldest" -> list.sortedBy { it.createdAt }
            "Refused first" -> list.sortedByDescending { it.outcome.contains("REFUS") || it.outcome.contains("REJECT") }
            else -> list
        }
    }
    if (damage.isNotEmpty()) EdgeCard {
        Notice("Evidence damage: ${damage.size} receipt line(s) could not be read. Valid records are still shown below; the damaged lines were kept, not deleted.", error = true)
        damage.take(5).forEach { Text("Line ${it.lineNumber}: ${it.reason}", style = MaterialTheme.typography.bodyMedium) }
    }
    val activity = remember(receipts) { com.edgeore.app.ui.ReceiptActivity.lastDays(receipts.map { it.createdAt }) }
    com.edgeore.app.ui.components.FactCard(
        EdgeIcons.Receipts, "Receipt activity", "Receipts recorded on this phone per day · last ${activity.days.size} days",
        status = { com.edgeore.app.ui.components.StatusPill("${activity.total} stored", tone = if (activity.isEmpty) com.edgeore.app.ui.components.PillTone.NEUTRAL else com.edgeore.app.ui.components.PillTone.MINT) },
        footer = "Local records only. A receipt is not a payment and is not checked on chain here.",
    ) {
        if (activity.isEmpty) {
            Notice("Nothing to plot yet. The chart appears once receipts exist; no sample data is shown.")
        } else {
            com.edgeore.app.ui.components.CountAreaChart(activity.counts.map { it.toDouble() }, activity.summary(), Modifier.fillMaxWidth().height(96.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayLabel.format(activity.days.first()), style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
                Text("Today", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
            }
            Text(activity.summary(), style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
        }
        if (activity.unreadable > 0) Notice("${activity.unreadable} receipt time(s) could not be read and are not plotted.", error = true)
    }
    if (shown.isEmpty()) EdgeCard { Notice(if (receipts.isEmpty() && damage.isEmpty()) "No receipts yet." else if (receipts.isEmpty()) "No readable receipts. See the damage report above." else "No receipts match this search or filter.") }
    shown.forEach { r ->
        val open = openId == r.id
        ReceiptCard(r) { openId = if (open) null else r.id }
        if (open) {
            EdgeCard(inset = true) {
                Text(whyStopped(r), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryAction(if (why) "Hide reason" else "Why it stopped", Modifier.weight(1f)) { why = !why }
                    SecondaryAction("Review details", Modifier.weight(1f)) { onOpen(r) }
                }
                if (why) Text(r.detail, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    verify?.let { v ->
        EdgeCard(inset = true) {
            Text(v.summary, fontWeight = FontWeight.SemiBold, color = if (v.accepted) EdgeColors.mint else EdgeColors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            v.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = { vm.clearVerify() }) { Text("Dismiss") }
        }
    }

    EdgeCard {
        Text("Evidence check", style = MaterialTheme.typography.titleMedium)
        val sample = shown.firstOrNull()
        if (sample == null) Text("Artifact missing", color = EdgeColors.copper, fontWeight = FontWeight.SemiBold)
        else Text(if (sample.integrityOk) "Stored body matches its digest" else "Stored body does not match its digest", color = if (sample.integrityOk) EdgeColors.mint else EdgeColors.danger)
        listOf(
            "Independent check" to (sample?.evidence("independentVerification")?.state ?: "Not run"),
            "Node signature" to (sample?.evidence("nodeSignature")?.state ?: "Not available"),
            "Provider acknowledgement" to (sample?.evidence("providerAcknowledgement")?.state ?: "Not received"),
        ).forEach { (label, state) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(state.replace('_', ' '), color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        SecondaryAction("Locate file") { pick.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
    }

    EdgeCard {
        Text("Export privacy", style = MaterialTheme.typography.titleMedium)
        Text("Control what gets included in exports.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        PrivacySwitch("Hide device identifiers", hideDevice) { hideDevice = it }
        EffectNote(Control.HIDE_DEVICE_IDS)
        PrivacySwitch("Include location", includeLocation) { includeLocation = it }
        EffectNote(Control.INCLUDE_LOCATION)
        Text(
            if (includeLocation) "No location has been collected, so the export still omits it." else "Location stays out of the file.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium,
        )
    }

    PrimaryAction("Preview export", icon = EdgeIcons.Export, enabled = receipts.isNotEmpty()) { preview = true }
    SecondaryAction("Verify offline") { pick.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }

    EdgeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Pending observations", style = MaterialTheme.typography.titleMedium)
                Text(if (observation?.ok == true) "Observed" else "RPC unavailable", color = if (observation?.ok == true) EdgeColors.mint else EdgeColors.danger, fontWeight = FontWeight.SemiBold)
                Text(observation?.let { clock.format(Instant.ofEpochMilli(it.at)) + " · " + it.detail } ?: "No attempt yet.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            SecondaryAction("Retry observation") { vm.retryObservation(wallet.address) }
        }
        Text("No automatic broadcast will be made.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
    }

    EdgeCard {
        Text("Payment status", style = MaterialTheme.typography.titleMedium)
        Text("Not observed", color = EdgeColors.copper, fontWeight = FontWeight.SemiBold)
        Text("Acknowledgement does not mean payment.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
    }

    EdgeCard {
        Text("Recovery and retention", style = MaterialTheme.typography.titleMedium)
        Text("Local records. No automatic deletion.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Back up records", Modifier.weight(1f), enabled = receipts.isNotEmpty()) { onExport(null, hideDevice) }
            SecondaryAction("Manage retention", Modifier.weight(1f)) { retention = true }
        }
    }

    if (preview) AlertDialog(
        onDismissRequest = { preview = false },
        title = { Text("Export preview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Includes ${receipts.size} receipt(s): exact body bytes, SHA-256 of each body, chain links, the signing key of each record, wallet signatures over reviewed messages where present, and a checkpoint signed by this install's current key. A selected-receipt export does not claim the history is complete.")
                Text(if (hideDevice) "Device public key is omitted. Offline signature checks of this file will fail until you export again with the key included." else "Device public key is included so a later check can verify these signatures.")
                Text(if (includeLocation) "Location was requested, but none was collected." else "Location is excluded.")
                Text("Payment status in the file: Not observed.", color = EdgeColors.copper)
                Text("Excludes:", fontWeight = FontWeight.SemiBold)
                ReceiptLog.EXCLUSIONS.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Exporting is not independent verification.", color = EdgeColors.copper)
            }
        },
        confirmButton = { TextButton(onClick = { preview = false; onExport(null, hideDevice) }) { Text("Export JSON") } },
        dismissButton = { TextButton(onClick = { preview = false }) { Text("Cancel") } },
    )
    if (retention) AlertDialog(
        onDismissRequest = { retention = false },
        title = { Text("Retention") },
        text = { Text("Receipts stay on this device until you uninstall EdgeORE. Automatic deletion is off and is not available in this build.") },
        confirmButton = { TextButton(onClick = { retention = false }) { Text("Keep all") } },
    )
}

@Composable
private fun PrivacySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.onAction),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

private fun whyStopped(r: StoredReceipt): String = when {
    r.outcome.contains("REFUS") || r.outcome.contains("REJECT") -> "The review was not approved. ${r.outcome.replace('_', ' ')}. No signature was stored and nothing was broadcast."
    r.solanaSignature == null -> "This record has no wallet signature."
    else -> "A signature is recorded. Payment is still not observed."
}

@Composable
fun ReceiptCard(r: StoredReceipt, onClick: () -> Unit) {
    val refused = r.outcome.contains("REFUSED") || r.outcome.contains("REJECTED")
    EdgeCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(when (r.kind) { "NODE" -> EdgeIcons.Nodes; "REVIEW" -> EdgeIcons.Shield; "LOCAL_AI" -> EdgeIcons.Ai; else -> EdgeIcons.Doc }, contentDescription = null, tint = EdgeColors.copper)
            Column(Modifier.weight(1f)) {
                Text(r.title, fontWeight = FontWeight.SemiBold)
                Text(r.outcome.replace('_', ' '), color = if (refused) EdgeColors.danger else EdgeColors.mint, style = MaterialTheme.typography.bodyMedium)
                Text(r.solanaSignature?.let { "Signature ${Format.short(it, 6)}" } ?: "No signature · Not broadcast", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
            }
            Text(Format.time(r.createdAt), style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        }
    }
}

@Composable
fun ReceiptDetailScreen(r: StoredReceipt, onExport: () -> Unit) {
    SectionTitle(r.title, r.outcome.replace('_', ' '))
    EdgeCard {
        KeyValue("Receipt ID (immutable)", r.id, mono = true)
        KeyValue("Sequence", r.sequence.toString())
        KeyValue("Time", Format.time(r.createdAt) + " (" + r.createdAt + ")")
        KeyValue("Source", r.source, mono = true)
        KeyValue("Cluster", r.cluster ?: "Not applicable")
        KeyValue("Operation ID", r.operationId ?: "None")
        KeyValue("Solana signature", r.solanaSignature ?: "No signature", mono = true)
        r.lamports?.let { KeyValue("Amount", Format.sol(it) + " ($it lamports)") }
        r.relatesTo?.let { KeyValue("Observation appended to", it, mono = true) }
        KeyValue("Detail", r.detail)
    }
    EdgeCard {
        Text("Integrity", style = MaterialTheme.typography.titleMedium)
        KeyValue("Body SHA-256", r.sha256, mono = true)
        Text(if (r.integrityOk) "Stored body matches its digest" else "Stored body does NOT match its digest", color = if (r.integrityOk) EdgeColors.mint else EdgeColors.danger)
        r.json.optJSONObject("digests")?.let { d -> d.keys().forEach { k -> KeyValue(k, d.optString(k), mono = true) } }
        KeyValue("Previous receipt SHA-256", r.json.optString("previousSha256"), mono = true)
    }
    EdgeCard {
        Text("Evidence dimensions", style = MaterialTheme.typography.titleMedium)
        listOf("localObservation" to "Local observation", "nodeSignature" to "Node signature", "independentVerification" to "Independent check", "providerAcknowledgement" to "Provider acknowledgement").forEachIndexed { i, (k, label) ->
            if (i > 0) HorizontalDivider(color = EdgeColors.border)
            val e = r.evidence(k)
            val ok = e.state == "CHECKED" || e.state == "OBSERVED"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (ok) EdgeIcons.Check else EdgeIcons.Cross, contentDescription = null, tint = if (ok) EdgeColors.mint else EdgeColors.textMuted)
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Text(e.checked, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
                }
                Text(if (ok) e.state.lowercase().replaceFirstChar { it.uppercase() } else "Not available", style = MaterialTheme.typography.labelSmall)
            }
        }
        HorizontalDivider(color = EdgeColors.border)
        KeyValue("Payment status (separate)", r.json.optString("payment").replace('_', ' '))
    }
    EdgeCard(inset = true) {
        Text("Exact stored body", style = MaterialTheme.typography.titleMedium)
        Text(r.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
    }
    PrimaryAction("Export this receipt", icon = EdgeIcons.Export, onClick = onExport)
}
