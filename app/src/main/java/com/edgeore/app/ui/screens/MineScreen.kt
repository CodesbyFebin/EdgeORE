package com.edgeore.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.EdgeState
import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.ui.Format
import com.edgeore.app.ui.components.Capability
import com.edgeore.app.ui.components.CapabilityBadge
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.ObservationCard
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.ResourceControl
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.theme.EdgeColors

@Composable
fun MineScreen(vm: EdgeOreViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit, onReview: () -> Unit, onPreview: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val wallet by vm.walletState.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val eval = EdgePolicy.evaluate(settings, device)

    EdgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Edge Mode", style = MaterialTheme.typography.titleMedium)
                Text("Review ORE. Control your edge.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            CapabilityBadge(if (eval.state == EdgeState.PAUSED) Capability.PAUSED else Capability.NOT_OBSERVED, eval.state.label)
        }
    }

    // Pause / resume ring. Static: an animated ring would imply real activity.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(200.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = if (settings.edgeModeResumed) "Pause Edge Mode" else "Resume Edge Mode") {
                    vm.updateSettings { it.copy(edgeModeResumed = !it.edgeModeResumed) }
                }
                .semantics { contentDescription = "Edge Mode ${eval.state.label}. ${eval.headline}" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(200.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(EdgeColors.border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(EdgeColors.copperDeep, 120f, 300f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (eval.state == EdgeState.IDLE) drawArc(EdgeColors.mint, -90f, 40f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (eval.state == EdgeState.PAUSED) EdgeIcons.Pause else EdgeIcons.Play, contentDescription = null, tint = EdgeColors.copper, modifier = Modifier.size(40.dp))
                Text(eval.state.label.uppercase(), fontWeight = FontWeight.Bold, color = EdgeColors.textPrimary)
                Text(if (settings.edgeModeResumed) "Tap to pause" else "Tap to resume", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
            }
        }
        Text(eval.headline, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Notice("ORE participation: protocol qualification required. Local hash benchmarks are not ORE board participation and earn nothing.")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val walletValue = wallet.address?.let { addr ->
            Format.short(addr) + when (val b = balance) { is RpcObservation.Fresh -> "\n" + Format.sol(b.lamports); is RpcObservation.Unavailable -> "\nBalance unavailable"; null -> "" }
        }
        ObservationCard(Modifier.weight(1f), EdgeIcons.Wallet, "Wallet", walletValue, "Not connected",
            (balance as? RpcObservation.Fresh)?.let { "devnet · slot ${it.slot} · ${Format.ago(it.observedAt)}" })
        ObservationCard(Modifier.weight(1f), EdgeIcons.Coins, "ORE rewards", null, "Not observed", "No qualified ORE adapter")
        val d = device
        val load = if (d == null) null else listOfNotNull(
            d.batteryPercent?.let { "Battery $it%" },
            d.charging?.let { if (it) "Charging" else "On battery" },
            d.thermal?.let { "Thermal ${it.label}" },
        ).joinToString("\n").ifEmpty { null }
        ObservationCard(Modifier.weight(1f), EdgeIcons.Pulse, "Device load", load, "Not observed", d?.let { "Android sensors · ${Format.ago(it.observedAt)}" })
    }

    EdgeCard(inset = true) {
        Text("Workload", style = MaterialTheme.typography.titleMedium)
        Notice("No measured samples. No workload has run on this device, so there is nothing to chart.")
    }

    PrimaryAction("Review a supported action", icon = EdgeIcons.Shield, onClick = onReview)
    SecondaryAction("Preview session (concept)", Modifier.fillMaxWidth(), onClick = onPreview)

    EdgeCard {
        Text("Wallet", style = MaterialTheme.typography.titleMedium)
        Text(wallet.status, color = EdgeColors.textMuted)
        wallet.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Mobile Wallet Adapter · devnet. Keys stay in your wallet app.", style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wallet.address == null) SecondaryAction(if (wallet.busy) "Waiting…" else "Connect wallet", enabled = !wallet.busy, onClick = onConnect)
            else {
                SecondaryAction("Refresh balance", onClick = { vm.refreshBalance() })
                SecondaryAction("Disconnect", danger = true, onClick = onDisconnect)
            }
        }
    }

    EdgeCard {
        Text("Safety & resource controls", style = MaterialTheme.typography.titleMedium)
        Text("Policy gates are evaluated against live Android readings and fail closed when a reading is missing.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        ResourceControl(EdgeIcons.Bolt, "Charge-only mode", "Run only while charging", settings.chargeOnly, gateText(eval, "Charge-only")) { v -> vm.updateSettings { it.copy(chargeOnly = v) } }
        ResourceControl(EdgeIcons.Thermo, "Thermal guard", "Pause at moderate thermal status or above", settings.thermalGuard, gateText(eval, "Thermal guard")) { v -> vm.updateSettings { it.copy(thermalGuard = v) } }
        LabeledSlider(EdgeIcons.Battery, "Battery reserve", "Keep ${settings.batteryReservePercent}% minimum", gateText(eval, "Battery reserve"), settings.batteryReservePercent, 5f..90f) { v -> vm.updateSettings { it.copy(batteryReservePercent = v) } }
        LabeledSlider(EdgeIcons.Cpu, "CPU limit", "Limit to ${settings.cpuLimitPercent}% of device CPU", "Stored · no workload in this build to apply it to", settings.cpuLimitPercent, 10f..100f) { v -> vm.updateSettings { it.copy(cpuLimitPercent = v) } }
        Text("Daily devnet review budget: ${Format.sol(settings.dailyLimitLamports)} · spent today ${Format.sol(vm.spentTodayLamports())}", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10_000_000L, 50_000_000L, 100_000_000L).forEach { l ->
                FilterChip(selected = settings.dailyLimitLamports == l, onClick = { vm.updateSettings { it.copy(dailyLimitLamports = l) } }, label = { Text(Format.sol(l)) })
            }
        }
    }
}

private fun gateText(eval: com.edgeore.app.device.EdgeEvaluation, name: String): String =
    eval.gates.firstOrNull { it.name == name }?.let { (if (it.allowed) "Enforced · pass: " else "Enforced · blocking: ") + it.explanation } ?: "Off"

@Composable
private fun LabeledSlider(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, explanation: String, enforcement: String, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = EdgeColors.mint)
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
                Text(enforcement, style = MaterialTheme.typography.labelSmall, color = EdgeColors.copper)
            }
        }
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = EdgeColors.mint, activeTrackColor = EdgeColors.mint),
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "$label $value percent" },
        )
    }
}
