package com.edgeore.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.node.NodeAgentProtocol
import com.edgeore.app.ui.Format
import com.edgeore.app.ui.components.Capability
import com.edgeore.app.ui.components.CapabilityBadge
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.KeyValue
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.OperationRow
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors

private const val STALE_MS = 5 * 60 * 1000L

@Composable
fun NodesScreen(vm: EdgeOreViewModel) {
    val node by vm.node.collectAsStateWithLifecycle()
    val rec = node.record
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var confirmRevoke by rememberSaveable { mutableStateOf(false) }

    SectionTitle("Owned Nodes", "Use your phone or Ubuntu host.")

    EdgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ubuntu host", style = MaterialTheme.typography.titleMedium)
                Text(if (rec == null) "No node paired" else Format.short(rec.fingerprint, 8), color = if (rec == null) EdgeColors.copper else EdgeColors.textPrimary, fontFamily = FontFamily.Monospace)
            }
            when {
                rec == null -> CapabilityBadge(Capability.UNAVAILABLE, "Not paired")
                rec.revocationPending -> CapabilityBadge(Capability.STALE, "Revocation pending")
                node.healthObservedAt == null -> CapabilityBadge(Capability.NOT_OBSERVED, "Not read yet")
                System.currentTimeMillis() - node.healthObservedAt!! > STALE_MS -> CapabilityBadge(Capability.STALE)
                else -> CapabilityBadge(Capability.AVAILABLE, "Observed")
            }
        }
        if (rec != null) {
            KeyValue("Route", rec.endpoint, mono = true)
            KeyValue("Scopes", rec.scopes.joinToString())
            KeyValue("Paired", Format.time(rec.pairedAt) + " · session ≤24 h")
            KeyValue("Observation freshness", node.healthObservedAt?.let { Format.ago(it) } ?: "Never read")
            Text("Pairing key: Ed25519 in software, seed wrapped by Android Keystore AES-GCM.", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        }
    }

    node.status?.let { Notice(it) }
    node.error?.let { Notice(it, error = true) }

    if (rec == null) PairingForm(vm, node.busy)

    EdgeCard {
        Text("Scoped operations", style = MaterialTheme.typography.titleMedium)
        Text("Each row sends one Ed25519-signed command with a 30-second deadline. No arbitrary shell commands exist.", style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
        OperationRow(EdgeIcons.Pulse, "Read health", "observe · scope READ_NODE", enabled = rec != null && !node.busy) { vm.readHealth() }
        HorizontalDivider(color = EdgeColors.border)
        OperationRow(EdgeIcons.Doc, "Review logs", "Operations this phone sent (${node.ops.size})", enabled = node.ops.isNotEmpty()) { showLogs = !showLogs }
        HorizontalDivider(color = EdgeColors.border)
        OperationRow(EdgeIcons.Shield, "Revoke access", "revoke · ends this session on the node", enabled = rec != null && !node.busy) { confirmRevoke = true }
        if (rec?.revocationPending == true) SecondaryAction("Forget locally (node not acknowledged)", Modifier.fillMaxWidth(), danger = true) { vm.forgetNode() }
    }

    if (showLogs) EdgeCard(inset = true) {
        Text("Operation log (this phone)", style = MaterialTheme.typography.titleMedium)
        Notice("The node agent exposes no remote log endpoint; this is the phone's record of signed operations and outcomes.")
        node.ops.take(30).forEach { op ->
            Column {
                Text("${Format.time(op.time)} · ${op.action} · ${op.outcome}", style = MaterialTheme.typography.bodyMedium)
                op.operationId?.let { Text("op $it", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted, fontFamily = FontFamily.Monospace) }
                op.payloadSha256?.let { Text("payload sha256 ${Format.short(it, 8)}", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted, fontFamily = FontFamily.Monospace) }
            }
        }
    }

    node.health?.let { h ->
        EdgeCard {
            Text("Node health", style = MaterialTheme.typography.titleMedium)
            KeyValue("Host", "${h.optString("hostOS")} / ${h.optString("architecture")} · ${h.optInt("logicalCPUs")} logical CPUs")
            KeyValue("Observed by node", Format.time(h.optString("observedAt")))
            KeyValue("Node key protection", h.optString("keyProtection"))
            KeyValue("Hosting", h.opt("hosting")?.toString() ?: "Unknown")
            KeyValue("Proof backend", "${h.opt("proofBackend") ?: "Unknown"} · ${h.optString("proofState")}")
            KeyValue("Active operations", (h.optJSONArray("activeOperations")?.length() ?: 0).toString())
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EdgeCard(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { androidx.compose.material3.Icon(EdgeIcons.Storage, null, tint = EdgeColors.mint); Text("Storage", style = MaterialTheme.typography.titleMedium) }
            Text("Capacity unavailable", color = EdgeColors.copper)
            Text("The node agent does not report provisioned capacity.", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        }
        EdgeCard(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { androidx.compose.material3.Icon(EdgeIcons.Wifi, null, tint = EdgeColors.mint); Text("Bandwidth", style = MaterialTheme.typography.titleMedium) }
            Text("OFF", color = EdgeColors.copper)
            Text("Cannot enable: destination policy, quotas and consent withdrawal are not configured in this app.", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        }
    }
    Notice("Connect only infrastructure you own.")

    if (confirmRevoke && rec != null) AlertDialog(
        onDismissRequest = { confirmRevoke = false },
        title = { Text("Revoke node access?") },
        text = { Text("Sends a signed revoke for this session to ${rec.endpoint}. Active flows owned by this session are cancelled on the node. The local pairing key is destroyed once the node acknowledges.") },
        confirmButton = { TextButton(onClick = { confirmRevoke = false; vm.revokeNode() }) { Text("Revoke", color = EdgeColors.danger) } },
        dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PairingForm(vm: EdgeOreViewModel, busy: Boolean) {
    var endpoint by rememberSaveable { mutableStateOf("https://127.0.0.1:9843") }
    var cert by rememberSaveable { mutableStateOf("") }
    var challenge by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var exportScope by rememberSaveable { mutableStateOf(false) }
    val parsed = runCatching { NodeAgentProtocol.parseChallenge(challenge) }

    EdgeCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(EdgeIcons.Link, null, tint = EdgeColors.copper)
            Text("Scoped pairing", style = MaterialTheme.typography.titleMedium)
        }
        Text("On your Ubuntu host run the DeProof node agent: deproof-node -pair-scopes READ_NODE. It prints a 2-minute challenge, a single-use code and its TLS certificate SHA-256. The agent listens on loopback only; reach it with `adb reverse tcp:9843 tcp:9843` or an SSH tunnel.",
            style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
        val shape = RoundedCornerShape(24.dp)
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Node endpoint (https)") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cert, { cert = it }, label = { Text("TLS certificate SHA-256") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(challenge, { challenge = it }, label = { Text("Pairing challenge JSON") }, minLines = 2, shape = shape, modifier = Modifier.fillMaxWidth())
        parsed.onSuccess { c ->
            Text("Confirm this fingerprint matches the host output:", style = MaterialTheme.typography.bodyMedium)
            Text(c.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.mint)
            Text("Challenge expires ${Format.time(c.expiresAt)}", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        }.onFailure { if (challenge.isNotBlank()) Notice(it.message ?: "Invalid challenge", error = true) }
        OutlinedTextField(code, { code = it }, label = { Text("Single-use pairing code") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = true, onCheckedChange = null, enabled = false); Text("READ_NODE (required: health)") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = exportScope, onCheckedChange = { exportScope = it }); Text("EXPORT_PUBLIC_RECORDS (optional; host must allow)") }
        PrimaryAction("Pair your node", icon = EdgeIcons.Link, enabled = parsed.isSuccess && cert.isNotBlank() && code.isNotBlank(), loading = busy) {
            vm.pairNode(endpoint, cert, challenge, code, listOfNotNull("READ_NODE", if (exportScope) "EXPORT_PUBLIC_RECORDS" else null))
        }
    }
}
