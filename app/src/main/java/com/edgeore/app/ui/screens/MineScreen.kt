package com.edgeore.app.ui.screens

import com.edgeore.app.ui.components.EffectNote
import com.edgeore.app.settings.Control
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.edgeore.app.BuildConfig
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.contribution.ConstraintObservation
import com.edgeore.app.contribution.ContributionPolicy
import com.edgeore.app.contribution.SchedulerStatus
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.EdgeState
import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.ui.Format
import com.edgeore.app.wallet.WalletDisplay
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
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
    val unmetered by vm.unmetered.collectAsStateWithLifecycle()
    val job by vm.contributionJob.collectAsStateWithLifecycle()
    val lastRun by vm.lastContributionRun.collectAsStateWithLifecycle()
    val eval = EdgePolicy.evaluate(settings, device)
    val fresh = balance as? RpcObservation.Fresh

    EdgeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(EdgeIcons.Wallet, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Connect Solana Wallets", style = MaterialTheme.typography.titleMedium)
                Text(
                    wallet.address?.let { Format.short(it) } ?: "Authorize securely through your wallet",
                    color = EdgeColors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (wallet.address == null) {
                SecondaryAction(if (wallet.busy) "Waiting…" else "Connect wallet", enabled = !wallet.busy, onClick = onConnect)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(WalletDisplay.statusLine(wallet), color = if (wallet.error != null && wallet.address == null) EdgeColors.copper else EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            Text("Compatible MWA wallet required", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        val failure = wallet.error
        if (failure != null && wallet.address == null && !wallet.busy) {
            // Persistent until the next Connect attempt; sanitized (no token, key or wallet payload).
            Column(Modifier.semantics { contentDescription = "Wallet connection error ${failure.code.name}" }) {
                WalletDisplay.errorLines(failure, showDiagnostics = BuildConfig.DEBUG).forEachIndexed { i, line ->
                    Text(line, color = if (i == 0) EdgeColors.copper else EdgeColors.textMuted,
                        style = if (i < 2) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall)
                }
            }
        }
        wallet.address?.let {
            Text(
                WalletDisplay.balanceLine(balance) { b -> "Devnet ${Format.sol(b.lamports)} · slot ${b.slot} · ${Format.ago(b.observedAt)}" },
                color = if (fresh == null) EdgeColors.copper else EdgeColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction("Refresh", onClick = { vm.refreshBalance() })
                SecondaryAction("Review", onClick = onReview)
                SecondaryAction("Disconnect", danger = true, onClick = onDisconnect)
            }
        }
    }

    EdgeCard(onClick = onPreview) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(EdgeIcons.Nodes, contentDescription = null, tint = EdgeColors.copper, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Edge Mode", style = MaterialTheme.typography.titleMedium)
                Text("Your device. Your resources. Your control.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(EdgeIcons.Chevron, contentDescription = null, tint = EdgeColors.textMuted)
        }
    }

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
            }
        }
        Text("Protocol qualification required", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Text(eval.headline, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ObservationCard(Modifier.weight(1f), EdgeIcons.Coins, "ORE rewards", null, "Not observed", "No qualified ORE adapter")
        ObservationCard(Modifier.weight(1f), EdgeIcons.Cpu, "Compute", if (eval.state == EdgeState.PAUSED) "Paused" else eval.state.label, "Not measured", "No workload is running")
    }

    EdgeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Workload & resources", style = MaterialTheme.typography.titleMedium)
            Text("No live telemetry", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text("CPU, memory, storage and network are not sampled in this build. Missing readings are not shown as zero.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(EdgeIcons.Cpu, "CPU")
            MetricTile(EdgeIcons.Pulse, "Memory")
            MetricTile(EdgeIcons.Storage, "Storage")
            MetricTile(EdgeIcons.Wifi, "Network")
        }
    }

    PrimaryAction("Preview session", icon = EdgeIcons.Play, onClick = onPreview)
    SecondaryAction("Review a supported action", Modifier.fillMaxWidth(), onClick = onReview)

    EdgeCard {
        Text("Safety & resource controls", style = MaterialTheme.typography.titleMedium)
        Text("Each control states its effect: Enforced, Saved only or Unavailable. The gates use battery and thermal readings; nothing here caps CPU, memory, storage or network.", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        ResourceControl(EdgeIcons.Bolt, "Charge-only mode", "Run only when charging", settings.chargeOnly, gateText(eval, "Charge-only")) { v -> vm.updateSettings { it.copy(chargeOnly = v) } }
        EffectNote(Control.CHARGE_ONLY)
        ResourceControl(EdgeIcons.Thermo, "Thermal guard", "Pause at high temperature", settings.thermalGuard, gateText(eval, "Thermal guard")) { v -> vm.updateSettings { it.copy(thermalGuard = v) } }
        EffectNote(Control.THERMAL_GUARD)
        LabeledSlider(EdgeIcons.Battery, "Battery reserve", "Keep ${settings.batteryReservePercent}% minimum", gateText(eval, "Battery reserve"), settings.batteryReservePercent, 5f..90f) { v -> vm.updateSettings { it.copy(batteryReservePercent = v) } }
        EffectNote(Control.BATTERY_RESERVE)
        LabeledSlider(EdgeIcons.Cpu, "CPU limit", "Stored limit ${settings.cpuLimitPercent}%", "Now: not applied", settings.cpuLimitPercent, 10f..100f) { v -> vm.updateSettings { it.copy(cpuLimitPercent = v) } }
        EffectNote(Control.CPU_LIMIT, Control.cpuDetail(settings.cpuLimitPercent))
    }

    val observed = ConstraintObservation.of(device, unmetered)
    val sched = ContributionPolicy.view(settings, device, observed, job)
    EdgeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Contribution scheduler", style = MaterialTheme.typography.titleMedium)
            Text(sched.status.label, color = if (sched.status == SchedulerStatus.CHECKING) EdgeColors.mint else EdgeColors.copper, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(sched.detail, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        ResourceControl(EdgeIcons.Wifi, "Allow scheduled checks", "Check only on Wi-Fi, while charging, battery not low", settings.contributionOptIn, if (settings.contributionOptIn) "Now: opted in" else "Now: off") { v -> vm.updateSettings { it.copy(contributionOptIn = v) } }
        EffectNote(Control.CONTRIBUTION_SCHEDULER)
        Text(ContributionPolicy.constraintLine("Unmetered network (Wi-Fi)", observed.unmetered), color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(ContributionPolicy.constraintLine("Charging", observed.charging), color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(ContributionPolicy.constraintLine("Battery not low", observed.batteryLow?.not()), color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            lastRun?.let { "Last check ${Format.time(it.atMillis)}: ${it.outcome.label}" } ?: "No check has run yet",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall,
        )
        Text("The scheduler runs no workload in this build and measures nothing. It does not produce progress or any token.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RowScope.MetricTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    EdgeCard(Modifier.weight(1f)) {
        Icon(icon, contentDescription = null, tint = EdgeColors.copper, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text("Not measured", color = EdgeColors.copper, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun gateText(eval: com.edgeore.app.device.EdgeEvaluation, name: String): String =
    eval.gates.firstOrNull { it.name == name }?.let { (if (it.allowed) "Now: pass · " else "Now: blocking · ") + it.explanation } ?: "Now: off"

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
