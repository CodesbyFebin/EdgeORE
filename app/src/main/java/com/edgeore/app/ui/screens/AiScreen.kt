package com.edgeore.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.AiStatus
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.ui.Format
import com.edgeore.app.ui.components.Capability
import com.edgeore.app.ui.components.CapabilityBadge
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.KeyValue
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors

@Composable
fun AiScreen(vm: EdgeOreViewModel) {
    val ai by vm.ai.collectAsStateWithLifecycle()
    var endpoint by rememberSaveable { mutableStateOf(ai.endpoint.ifEmpty { "http://127.0.0.1:11434" }) }
    var prompt by rememberSaveable { mutableStateOf("") }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.attachDocument(uri) }

    SectionTitle("Private AI", "Chat with models on hardware you own.")

    EdgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ai.selectedModel ?: "No local model installed", style = MaterialTheme.typography.titleMedium)
                Text(ai.location ?: "On-device runtime: not bundled in this build", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            CapabilityBadge(Capability.IMPLEMENTED, "Cloud fallback off")
        }
        Text("Enforced: public endpoints are refused. Allowed: loopback (adb reverse / SSH tunnel to your host) or https on a private network.", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Owned-host model server (Ollama-compatible)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth())
        SecondaryAction(if (ai.status == AiStatus.CHECKING) "Checking…" else "Connect to host", enabled = ai.status != AiStatus.CHECKING) { vm.setAiEndpoint(endpoint) }
        if (ai.models.isNotEmpty()) {
            Text("Choose a model explicitly:", style = MaterialTheme.typography.bodyMedium)
            ai.models.take(6).forEach { m -> FilterChip(selected = ai.selectedModel == m, onClick = { vm.selectModel(m) }, label = { Text(m) }) }
        }
        Text(ai.statusDetail, color = if (ai.status == AiStatus.FAILED) EdgeColors.danger else EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }

    EdgeCard {
        Text("Private document", style = MaterialTheme.typography.titleMedium)
        if (ai.documentName == null) {
            Notice("Optional. The text is read on this phone and included only in prompts to your selected owned host. Receipts keep its SHA-256, not its contents.")
            SecondaryAction("Attach text document") { pickDoc.launchTextPicker() }
        } else {
            KeyValue("Document", ai.documentName!!)
            KeyValue("SHA-256", Format.short(ai.documentSha256 ?: "", 10), mono = true)
            KeyValue("Included", "${ai.documentChars} characters" + if (ai.documentTruncated) " (truncated)" else "")
            Notice("No retrieval index: the document text is passed directly in the prompt.")
            SecondaryAction("Remove document", danger = true) { vm.detachDocument() }
        }
    }

    EdgeCard(inset = true) {
        if (ai.messages.isEmpty()) Notice(if (ai.selectedModel == null) "Choose a model to start. Send stays disabled without one." else "No messages yet.")
        ai.messages.forEach { m ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                Column(
                    Modifier.widthIn(max = 300.dp)
                        .background(if (m.fromUser) EdgeColors.mint.copy(alpha = 0.16f) else EdgeColors.surface, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Text(m.text, style = MaterialTheme.typography.bodyLarge)
                    Text(Format.time(m.time), style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EdgeCard(Modifier.weight(1f)) {
            Text("Execution", style = MaterialTheme.typography.titleMedium)
            Text(if (ai.location != null) "Owned host receives the prompt" else "Not configured", style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
        }
        EdgeCard(Modifier.weight(1f)) {
            Text("Retention", style = MaterialTheme.typography.titleMedium)
            Text("Session only · not written to disk", style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
        }
    }
    if (ai.messages.isNotEmpty()) SecondaryAction("Clear conversation", Modifier.fillMaxWidth(), danger = true) { vm.clearConversation() }

    OutlinedTextField(prompt, { prompt = it }, label = { Text("Message your private AI…") }, minLines = 2, maxLines = 6,
        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth())
    val canSend = ai.selectedModel != null && ai.status in setOf(AiStatus.READY, AiStatus.COMPLETED, AiStatus.CANCELED, AiStatus.FAILED) && prompt.isNotBlank()
    if (ai.status == AiStatus.LOADING) {
        SecondaryAction("Cancel", Modifier.fillMaxWidth(), danger = true) { vm.cancelPrompt() }
    } else {
        PrimaryAction(if (ai.selectedModel == null) "Choose local model first" else "Send", icon = EdgeIcons.Send, enabled = canSend) { vm.sendPrompt(prompt); prompt = "" }
    }
    Notice("Model output is informative only. It cannot grant permissions, change settings or approve transactions.")
}

private fun androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>.launchTextPicker() =
    launch(arrayOf("text/*", "application/json", "application/xml"))
