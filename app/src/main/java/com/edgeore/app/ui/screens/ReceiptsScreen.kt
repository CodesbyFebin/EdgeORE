package com.edgeore.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

private val NODE_KINDS = setOf("NODE")
private val REVIEW_KINDS = setOf("REVIEW")

@Composable
fun ReceiptsScreen(vm: EdgeOreViewModel, onOpen: (StoredReceipt) -> Unit, onExport: (StoredReceipt?) -> Unit) {
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val verify by vm.verify.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("All") }
    var preview by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.verifyImported(uri) }

    SectionTitle("Receipts", "Review on-device activity and events.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Reviews", "Node").forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
    }
    val shown = receipts.filter {
        when (filter) { "Reviews" -> it.kind in REVIEW_KINDS; "Node" -> it.kind in NODE_KINDS; else -> true }
    }
    if (shown.isEmpty()) EdgeCard { Notice("No receipts yet.") }
    shown.forEach { r -> ReceiptCard(r) { onOpen(r) } }

    verify?.let { v ->
        EdgeCard(inset = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (v.accepted) EdgeIcons.Check else EdgeIcons.Cross, contentDescription = null, tint = if (v.accepted) EdgeColors.mint else EdgeColors.danger)
                Text(v.summary, fontWeight = FontWeight.SemiBold, color = if (v.accepted) EdgeColors.mint else EdgeColors.danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }
            v.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = { vm.clearVerify() }) { Text("Dismiss") }
        }
    }

    EdgeCard {
        Text("Evidence dimensions", style = MaterialTheme.typography.titleMedium)
        Text("Each receipt records four independent claims: local observation, node signature, independent verification and provider acknowledgement. Payment status is separate from receipts.",
            style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
    }

    PrimaryAction("Preview export", icon = EdgeIcons.Export, enabled = receipts.isNotEmpty()) { preview = true }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryAction("Verify a receipt file", Modifier.weight(1f)) { pick.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
        SecondaryAction("Run tamper test", Modifier.weight(1f)) { vm.runTamperTest() }
    }

    if (preview) AlertDialog(
        onDismissRequest = { preview = false },
        title = { Text("Export preview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Includes ${receipts.size} receipt(s): exact body bytes, SHA-256 of each body, device-key signatures, chain links and a bundle digest.")
                Text("Excludes:", fontWeight = FontWeight.SemiBold)
                ReceiptLog.EXCLUSIONS.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Exporting is not independent verification.", color = EdgeColors.copper)
            }
        },
        confirmButton = { TextButton(onClick = { preview = false; onExport(null) }) { Text("Export JSON") } },
        dismissButton = { TextButton(onClick = { preview = false }) { Text("Cancel") } },
    )
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
                Text(r.solanaSignature?.let { "Signature ${Format.short(it, 6)}" } ?: "No signature", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.time(r.createdAt), style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
                if (!r.integrityOk) Text("Integrity failed", color = EdgeColors.danger, style = MaterialTheme.typography.labelSmall)
            }
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
