package com.edgeore.app.ui.screens

import com.edgeore.app.ai.ondevice.ExecutionLabel
import com.edgeore.app.ui.components.EffectNote
import com.edgeore.app.settings.Control
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.AiStatus
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.ai.ondevice.OnDevicePhase
import androidx.compose.runtime.LaunchedEffect
import com.edgeore.app.ui.Format
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeDivider
import com.edgeore.app.ui.components.EmptyChat
import com.edgeore.app.ui.components.FactCard
import com.edgeore.app.ui.components.ModelBubble
import com.edgeore.app.ui.components.PillTone
import com.edgeore.app.ui.components.PlainError
import com.edgeore.app.ui.components.PlainNotice
import com.edgeore.app.ui.components.StatCell
import com.edgeore.app.ui.components.StatusPill
import com.edgeore.app.ui.components.UserBubble
import com.edgeore.app.ui.components.WorkingRow
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
    val od by vm.onDevice.collectAsStateWithLifecycle()
    var odPrompt by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshOnDevice() }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var endpoint by rememberSaveable { mutableStateOf(ai.endpoint.ifEmpty { "http://127.0.0.1:11434" }) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.attachDocument(uri) }
    val pickSum = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.verifyChecksum(uri, expected) }

    SectionTitle("Personal Edge AI", "Your models. Your memory. Your control.")

    FactCard(EdgeIcons.Ai, ExecutionLabel.title(od.downloaded, od.loadedId), null) {
        Text("No weights ship in this APK. Download an allowlisted model below to run on this phone, or connect a host you own.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        StatusPill("Cloud fallback OFF", tone = PillTone.MINT, icon = EdgeIcons.Check)
    }

    od.consentFor?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.declineOnDeviceDownload() },
            title = { Text("Download ${m.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyValue("Size", "${Format.bytes(m.sizeBytes)} (${m.sizeBytes} bytes)")
                    KeyValue("From", "huggingface.co/${m.repo}")
                    KeyValue("Commit", m.commit.take(12), mono = true)
                    KeyValue("License", m.license)
                    KeyValue("Free space", Format.bytes(od.freeBytes))
                    Text("Saved in this app's private storage. Size and SHA-256 are checked before it counts as downloaded. Only the weights are fetched; prompts are never sent.", style = MaterialTheme.typography.bodyMedium)
                    if (od.consentMetered) Text("You are on a metered network. This is a large download.", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)
                    m.minDeviceMemoryGb?.let { gb -> if (od.deviceRamBytes in 1 until gb * 1_000_000_000L) Text("This phone reports ${Format.bytes(od.deviceRamBytes)} RAM; Google AI Edge Gallery lists $gb GB for this model. It may fail to load.", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmOnDeviceDownload() }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { vm.declineOnDeviceDownload() }) { Text("Not now") } },
        )
    }

    val odBusy = od.phase == OnDevicePhase.LOADING_MODEL || od.phase == OnDevicePhase.GENERATING
    FactCard(
        EdgeIcons.Ai, "On-device model", "LiteRT-LM · in this app · CPU",
        status = {
            when {
                od.phase == OnDevicePhase.GENERATING -> StatusPill("Generating", tone = PillTone.COPPER)
                od.loadedId != null -> StatusPill("Loaded", tone = PillTone.MINT, icon = EdgeIcons.Check)
                od.downloaded.isNotEmpty() -> StatusPill("Downloaded", tone = PillTone.MINT)
                else -> StatusPill("Not set up", tone = PillTone.NEUTRAL)
            }
        },
        footer = "Prompts on this path never leave the phone. Model output cannot approve a transaction.",
    ) {
        Text("Allowlist and runtime from Google AI Edge Gallery (Apache-2.0). Runs in this app on the CPU. Not device-qualified yet.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        od.catalogError?.let { PlainNotice(it, PlainError.Area.ON_DEVICE_AI) }
        od.models.forEach { m ->
            val downloaded = m.id in od.downloaded
            FilterChip(selected = od.selectedId == m.id, onClick = { vm.selectOnDeviceModel(m.id) }, label = {
                Text("${m.name} · ${Format.bytes(m.sizeBytes)} · " + when { downloaded -> "downloaded"; m.gated -> "gated"; else -> "not downloaded" })
            })
        }
        val sel = od.selected
        if (sel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCell("Size", Format.bytes(sel.sizeBytes), Modifier.weight(1f))
                StatCell("License", sel.license, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCell("Commit", sel.commit.take(12), Modifier.weight(1f), mono = true)
                StatCell("SHA-256", sel.sha256?.let { Format.short(it, 6) }, Modifier.weight(1f), missing = "Gated · not pinned", mono = true)
            }
            when {
                od.downloadingId == sel.id -> {
                    LinearProgressIndicator(progress = { if (sel.sizeBytes > 0) (od.downloadedBytes.toFloat() / sel.sizeBytes).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                    Text("${Format.bytes(od.downloadedBytes)} of ${Format.bytes(sel.sizeBytes)}", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
                    SecondaryAction("Cancel download") { vm.cancelOnDeviceDownload() }
                }
                sel.id in od.downloaded -> SecondaryAction("Delete from phone", danger = true, enabled = od.phase == OnDevicePhase.IDLE) { vm.deleteOnDeviceModel(sel.id) }
                sel.downloadable -> SecondaryAction("Download ${Format.bytes(sel.sizeBytes)}…", enabled = od.phase == OnDevicePhase.IDLE) { vm.requestOnDeviceDownload(sel.id) }
                else -> Notice("Gated: needs a Hugging Face sign-in this build does not have.")
            }
        }
        if (od.error != null) PlainNotice(od.error!!, PlainError.Area.ON_DEVICE_AI)
        else Text(od.status, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        EdgeDivider()
        if (od.messages.isEmpty() && !odBusy) EmptyChat("No messages yet. A reply appears only after you run a prompt on a downloaded model.")
        od.messages.forEach { m -> if (m.fromUser) UserBubble(m.text) else ModelBubble(m.text, "On-device model") }
        if (od.phase == OnDevicePhase.LOADING_MODEL) WorkingRow("Loading the model on this phone…")
        if (od.phase == OnDevicePhase.GENERATING) WorkingRow("Generating on this phone…")
        OutlinedTextField(odPrompt, { odPrompt = it }, label = { Text(if (od.selectedDownloaded) "Prompt for the on-device model…" else "Download a model first") }, enabled = od.selectedDownloaded, modifier = Modifier.fillMaxWidth())
        PrimaryAction(if (od.selectedDownloaded) "Run on this phone" else "Model not downloaded", icon = EdgeIcons.Send, enabled = od.canSend && odPrompt.isNotBlank(), loading = odBusy) { vm.sendOnDevicePrompt(odPrompt); odPrompt = "" }
        SecondaryAction("Stop generation", enabled = od.phase == OnDevicePhase.GENERATING) { vm.cancelOnDeviceGeneration() }
    }

    EdgeCard {
        Text("1. Local model gallery", style = MaterialTheme.typography.titleMedium)
        Text("Check license, size and device fit.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        if (ai.models.isEmpty()) Text("No model names reported.", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)
        else ai.models.take(6).forEach { m -> FilterChip(selected = ai.selectedModel == m, onClick = { vm.selectModel(m) }, label = { Text(m) }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Small chat model", "Coding model", "Embedding model").forEach { kind ->
                SecondaryAction(kind, Modifier.weight(1f)) { vm.inspectGallery(kind) }
            }
        }
        ai.galleryNote?.let { Text(it, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }

    EdgeCard {
        Text("2. Model integrity", style = MaterialTheme.typography.titleMedium)
        Text("Verify before loading.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(expected, { expected = it }, label = { Text("Expected SHA-256, optional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SecondaryAction("Verify checksum") { pickSum.launch(arrayOf("*/*")) }
        ai.checksumResult?.let { Text(it, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium) }
    }

    EdgeCard(inset = true) {
        Text("3. Private chat", style = MaterialTheme.typography.titleMedium)
        Text(if (ai.selectedModel == null) "Illustrative until a model is chosen." else "Messages go to ${ai.selectedModel} on ${ai.location}.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        if (ai.messages.isEmpty()) Notice("Preview only — no inference executed.")
        ai.messages.forEach { m -> if (m.fromUser) UserBubble(m.text) else ModelBubble(m.text, "Owned host · ${ai.selectedModel ?: "model"}") }
        if (ai.status == AiStatus.LOADING) WorkingRow("Waiting for ${ai.selectedModel ?: "the host"}…")
        OutlinedTextField(prompt, { prompt = it }, label = { Text("Message your local AI…") }, modifier = Modifier.fillMaxWidth())
        val canSend = ai.selectedModel != null && ai.status != AiStatus.LOADING && prompt.isNotBlank()
        PrimaryAction(if (ai.selectedModel == null) "Send stays off" else "Send", icon = EdgeIcons.Send, enabled = canSend) { vm.sendPrompt(prompt); prompt = "" }
        Text("Model output cannot approve a transaction.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }

    EdgeCard {
        Text("4. Runtime and cancellation", style = MaterialTheme.typography.titleMedium)
        if (ai.status == AiStatus.FAILED || ai.statusDetail.startsWith("Host unreachable")) PlainNotice(ai.statusDetail, PlainError.Area.OWNED_HOST)
        else Text(ai.statusDetail, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        PrimaryAction(if (ai.status == AiStatus.CHECKING) "Checking…" else "Load model", icon = EdgeIcons.Play, enabled = ai.status != AiStatus.CHECKING && ai.status != AiStatus.LOADING) {
            if (ai.selectedModel == null) vm.setAiEndpoint(endpoint) else vm.selectModel(ai.selectedModel!!)
        }
        SecondaryAction("Cancel generation", enabled = ai.status == AiStatus.LOADING) { vm.cancelPrompt() }
        Text("Device qualification pending. Cancel stops waiting; the host may still finish.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }

    EdgeCard {
        Text("5. Memory and thermal budget", style = MaterialTheme.typography.titleMedium)
        Text("Model memory limit ${ai.memoryLimitMb} MB.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Slider(ai.memoryLimitMb.toFloat(), { vm.setMemoryLimit(it.toInt()) }, valueRange = 256f..8192f, modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Model memory limit" }, colors = SliderDefaults.colors(thumbColor = EdgeColors.mint, activeTrackColor = EdgeColors.mint))
        EffectNote(Control.AI_MEMORY_LIMIT)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Thermal auto-pause")
            Switch(settings.thermalGuard, { v -> vm.updateSettings { it.copy(thermalGuard = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.onAction))
        }
        EffectNote(Control.AI_THERMAL_GUARD)
        Text("Thermal guard uses the Android thermal status already read on Mine. It does not invent a chip temperature.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }

    EdgeCard {
        Text("6. Workload coordination", style = MaterialTheme.typography.titleMedium)
        Text("Queue stays local. No second agent is running.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Pause compute during chat", modifier = Modifier.weight(1f))
            Switch(ai.pauseComputeDuringChat, vm::setPauseComputeDuringChat, colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.onAction))
        }
        EffectNote(Control.PAUSE_COMPUTE_DURING_CHAT)
        Text(if (ai.pauseComputeDuringChat) "On." else "Off. Chat does not change Edge Mode.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }

    EdgeCard {
        Text("7. Agent memory", style = MaterialTheme.typography.titleMedium)
        Text("On-device text for the next prompt. Allocation ${ai.allocationChars} characters.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        if (ai.documentName == null) {
            SecondaryAction("Choose allocation file") { pickDoc.launch(arrayOf("text/*", "application/json")) }
            Text("No memory file is attached.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        } else {
            KeyValue("File", ai.documentName!!)
            KeyValue("SHA-256", Format.short(ai.documentSha256 ?: "", 10), mono = true)
            SecondaryAction("Delete memory", danger = true) { vm.detachDocument() }
        }
        Slider(ai.allocationChars.toFloat(), { vm.setAllocationChars(it.toInt()) }, valueRange = 1000f..32000f, modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Memory allocation characters" }, colors = SliderDefaults.colors(thumbColor = EdgeColors.mint, activeTrackColor = EdgeColors.mint))
        EffectNote(Control.AI_ALLOCATION_CHARS)
    }

    EdgeCard {
        Text("8. Local AI gateway", style = MaterialTheme.typography.titleMedium)
        Text("Local routing only. Public hosts are refused.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Owned-host address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        SecondaryAction("Configure allowlist") { vm.setAiEndpoint(endpoint) }
        Text("Remote routes stay off unless the address is loopback or a private network.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }

    EdgeCard {
        Text("9. Offline verification", style = MaterialTheme.typography.titleMedium)
        Text("Confirm whether a network is up. This does not run a model.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        SecondaryAction("Run airplane-mode test") { vm.runAirplaneCheck() }
        ai.airplaneResult?.let { Text(it, color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }

    EdgeCard {
        Text("10. Compute and ORE", style = MaterialTheme.typography.titleMedium)
        Text("Native compute: qualification required", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Text("ORE rewards: Not observed", color = EdgeColors.copper, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text("AI use does not create an ORE multiplier. There is no monthly fee in this build.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
