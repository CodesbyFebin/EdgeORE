package com.edgeore.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeore.app.OreDeployUi
import com.edgeore.app.device.CheckInState
import com.edgeore.app.device.DailyCheckIn
import com.edgeore.app.device.NetActivity
import com.edgeore.app.device.StorageReading
import com.edgeore.app.device.Telemetry
import com.edgeore.app.ore.OreFormat
import com.edgeore.app.ore.OreState
import com.edgeore.app.solana.BalanceRead
import com.edgeore.app.solana.Balances
import com.edgeore.app.solana.Cluster
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeShapes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun Modifier.holoCard() = this.fillMaxWidth().background(EdgeColors.surface, EdgeShapes.card).border(1.dp, EdgeColors.holoBorder, EdgeShapes.card).padding(14.dp)
private fun hm(ms: Long) = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))

/** NetworkSelector: devnet / testnet / mainnet-beta, persisted by the view model. */
@Composable
fun NetworkSelector(selected: Cluster, onSelect: (Cluster) -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.dp, EdgeColors.holoBorder, EdgeShapes.pill).padding(3.dp).testTag("network-selector"), verticalAlignment = Alignment.CenterVertically) {
        Icon(EdgeIcons.Globe, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.padding(horizontal = 8.dp).size(18.dp))
        Cluster.entries.forEach { c ->
            val on = c == selected
            Box(Modifier.weight(1f).heightIn(min = 40.dp).background(if (on) EdgeColors.surfacePressed else Color.Transparent, EdgeShapes.pill)
                .then(if (on) Modifier.border(1.dp, EdgeColors.mint, EdgeShapes.pill) else Modifier)
                .clickable(role = Role.Tab) { onSelect(c) }.semantics { this.selected = on }.testTag("cluster-${c.id}"), contentAlignment = Alignment.Center) {
                Text(c.label, color = if (on) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
            }
        }
    }
}

/** WalletConnectRow: one Connect (Mobile Wallet Adapter), then copy / switch account / disconnect. */
@Composable
fun WalletConnectRow(address: String?, busy: Boolean, cluster: Cluster, onConnect: () -> Unit, onCopy: () -> Unit, onSwitch: () -> Unit, onDisconnect: () -> Unit) {
    com.edgeore.app.ui.components.SecureWindow()
    Column(Modifier.holoCard().testTag("wallet-connect"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EdgeIcons.Wallet, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (address == null) "Connect a Solana wallet" else "Wallet connected", fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary)
                Text(address?.let { "${it.take(4)}…${it.takeLast(4)} · ${cluster.label}" } ?: "Mobile Wallet Adapter: Phantom, Solflare, Backpack, Seed Vault and other MWA wallets",
                    color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, fontFamily = if (address != null) FontFamily.Monospace else null)
            }
        }
        if (address == null) Pill(if (busy) "Waiting for wallet…" else "Connect", Modifier.fillMaxWidth().testTag("connect"), primary = true, enabled = !busy, onClick = onConnect)
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Copy address", Modifier.weight(1f).testTag("copy-address"), icon = EdgeIcons.Copy, onClick = onCopy)
            Pill("Switch", Modifier.weight(1f).testTag("switch-account"), icon = EdgeIcons.Refresh, onClick = onSwitch)
            Pill("Disconnect", Modifier.weight(1f).testTag("disconnect"), danger = true, onClick = onDisconnect)
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, primary: Boolean = false, danger: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 48.dp).background(if (primary) EdgeColors.mint else EdgeColors.surfaceRaised, EdgeShapes.control)
        .border(1.dp, if (danger) EdgeColors.danger.copy(alpha = 0.6f) else if (primary) EdgeColors.mint else EdgeColors.border, EdgeShapes.control)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, contentDescription = null, tint = EdgeColors.textMuted, modifier = Modifier.size(18.dp).padding(end = 4.dp)) }
        Text(text, color = when { primary -> EdgeColors.onAction; danger -> EdgeColors.danger; else -> EdgeColors.textPrimary }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/** What one balance card says. Zero only after a successful read; stale and failed reads keep their last value labelled. */
fun balanceText(r: BalanceRead, nowMs: Long, connected: Boolean): Pair<String, String> = when (r) {
    BalanceRead.NotRead -> "—" to if (connected) "Not read yet" else "Connect a wallet"
    BalanceRead.Loading -> "…" to "Loading"
    is BalanceRead.NotOnCluster -> "Not on ${r.cluster.label}" to "No verified mint on this network"
    is BalanceRead.Value -> r.formatted() to (if (nowMs - r.atMs > Balances.STALE_AFTER_MS) "Stale · read ${hm(r.atMs)}" else "Read ${hm(r.atMs)} · slot ${r.slot}")
    is BalanceRead.Failed -> (r.last?.formatted() ?: "Unavailable") to (r.reason + (r.last?.let { " · last read ${hm(it.atMs)}" } ?: ""))
}

/** TokenBalanceCard ×3: SOL, ORE, SKR for the selected cluster. */
@Composable
fun BalanceRow(b: Balances, nowMs: Long, connected: Boolean, oreLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("SOL", b.sol, EdgeColors.mint), Triple(oreLabel, b.ore, EdgeColors.copper), Triple("SKR", b.skr, EdgeColors.mintSoft)).forEach { (sym, r, col) ->
            val (v, note) = balanceText(r, nowMs, connected)
            Column(Modifier.weight(1f).background(EdgeColors.surface, EdgeShapes.card).border(1.dp, EdgeColors.holoBorder, EdgeShapes.card).padding(10.dp)
                .testTag("balance-${sym.substringAfterLast(' ')}").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sym, color = col, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(v, color = if (r is BalanceRead.Value) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = FontWeight.Bold, fontSize = if (v.length > 9) 13.sp else 17.sp, maxLines = 2)
                Text(note, color = if (r is BalanceRead.Failed) EdgeColors.warning else EdgeColors.textTertiary, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
    }
}

/** DailySessionCard: 5 one-hour check-in slots per UTC day, streak and points. No token reward. */
@Composable
fun DailyCheckInCard(state: CheckInState, nowMs: Long, reminders: Boolean, message: String?, tips: List<String>,
                     onCheckIn: () -> Unit, onReminders: (Boolean) -> Unit) {
    val used = DailyCheckIn.used(state, nowMs)
    val until = DailyCheckIn.activeUntil(state, nowMs)
    Column(Modifier.holoCard().testTag("daily-check-in"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EdgeIcons.Calendar, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.size(22.dp))
            Text("Daily check-in", style = MaterialTheme.typography.titleMedium, color = EdgeColors.textPrimary, modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() })
            Text("Slots $used/${DailyCheckIn.SLOTS_PER_DAY}", color = EdgeColors.textMuted, fontSize = 13.sp, modifier = Modifier.testTag("slots-used"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until DailyCheckIn.SLOTS_PER_DAY) Box(Modifier.weight(1f).heightIn(min = 8.dp).background(
                when { i < used - 1 || (i == used - 1 && until == null) -> EdgeColors.mint.copy(alpha = 0.55f); i == used - 1 -> EdgeColors.mint; else -> EdgeColors.surfaceRaised }, EdgeShapes.pill))
        }
        Text(until?.let { "Slot active until ${hm(it)} (${(it - nowMs) / 60_000} min left)" }
            ?: if (used >= DailyCheckIn.SLOTS_PER_DAY) "All slots used. Resets at ${hm(DailyCheckIn.nextResetMs(nowMs))} (00:00 UTC)." else "A slot is available.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Streak ${state.streakDays} day${if (state.streakDays == 1) "" else "s"}", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("streak"))
            Text("Points ${state.points}", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("points"))
        }
        Pill("Check in", Modifier.fillMaxWidth().testTag("check-in"), primary = DailyCheckIn.available(state, nowMs), enabled = DailyCheckIn.available(state, nowMs), icon = null, onClick = onCheckIn)
        Text(DailyCheckIn.REWARD_LABEL, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("no-token-reward"))
        message?.let { Text(it, color = EdgeColors.warning, style = MaterialTheme.typography.labelSmall) }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EdgeIcons.Bell, contentDescription = null, tint = EdgeColors.textMuted, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("ORE Bot reminders", color = EdgeColors.textPrimary)
                Text("Off by default. One local notification when a slot opens.", color = EdgeColors.textTertiary, fontSize = 11.sp)
            }
            Switch(reminders, onReminders, colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = Color.White, uncheckedTrackColor = EdgeColors.surfaceRaised),
                modifier = Modifier.semantics { contentDescription = "ORE Bot reminders" }.testTag("reminders"))
        }
        Column(Modifier.fillMaxWidth().background(EdgeColors.surfaceInset, EdgeShapes.control).border(1.dp, EdgeColors.border, EdgeShapes.control).padding(10.dp).testTag("ore-bot"),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EdgeIcons.Bot, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.size(18.dp))
                Text(" ORE Bot · basic help, text only, cannot sign", color = EdgeColors.mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            tips.take(3).forEach { Text("• $it", color = EdgeColors.textMuted, fontSize = 12.sp) }
        }
    }
}

/** ResourceCard ×4 (2×2): capacity facts and app traffic, from the platform. No utilisation bars. */
@Composable
fun ResourceGrid(cores: Int?, memoryGb: Int?, storage: StorageReading?, net: NetActivity, reducedMotion: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Res(EdgeIcons.Cpu, "CPU", cores?.let { "$it cores" } ?: "Not read", "Logical cores (Runtime)", "res-cpu")
            Res(EdgeIcons.Memory, "Memory", memoryGb?.let { "$it GB class" } ?: "Not read", "Total RAM (ActivityManager)", "res-mem")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Res(EdgeIcons.Storage, "Storage", storage?.let { "${Telemetry.gb(it.freeBytes)} free" } ?: "Not read", storage?.let { "of ${Telemetry.gb(it.totalBytes)} (StatFs)" } ?: "StatFs unavailable", "res-storage")
            Res(EdgeIcons.Network, "Network", when (net) { NetActivity.IDLE -> "Idle"; NetActivity.ACTIVE -> "Active"; NetActivity.UNAVAILABLE -> "Not available" },
                "This app's traffic (TrafficStats)", "res-net", dot = net, reducedMotion = reducedMotion)
        }
    }
}

@Composable
private fun RowScope.Res(icon: ImageVector, label: String, value: String, source: String, tag: String, dot: NetActivity? = null, reducedMotion: Boolean = true) {
    Column(Modifier.weight(1f).background(EdgeColors.surface, EdgeShapes.card).border(1.dp, EdgeColors.holoBorder, EdgeShapes.card).padding(12.dp).testTag(tag)
        .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.size(20.dp))
            Text(" $label", color = EdgeColors.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (dot != null && dot != NetActivity.UNAVAILABLE) {
                // Idle: slow decorative pulse labelled Idle (not an activity signal); Active: steady mint. Off under reduced motion.
                val a = if (dot == NetActivity.IDLE && !reducedMotion) rememberInfiniteTransition(label = "idle-dot").animateFloat(0.25f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a").value else 1f
                Box(Modifier.size(8.dp).alpha(a).background(if (dot == NetActivity.ACTIVE) EdgeColors.mint else EdgeColors.textMuted, CircleShape).testTag("net-dot"))
            }
        }
        Text(value, color = EdgeColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(source, color = EdgeColors.textTertiary, fontSize = 10.sp)
    }
}

/** OreGridCell ×25 for the selected cluster. Read-only display; in Pro, a tap toggles the square in the deploy draft only. */
@Composable
fun OreGridCard(cluster: Cluster, ore: OreState, deploy: OreDeployUi, onToggle: (Int) -> Unit) {
    val s = ore.snapshot.takeIf { cluster == Cluster.MAINNET }
    Column(Modifier.holoCard().testTag("ore-live-grid"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EdgeIcons.Grid, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.size(20.dp))
            Text(" ORE v3 grid · ${cluster.label}", style = MaterialTheme.typography.titleMedium, color = EdgeColors.textPrimary, modifier = Modifier.semantics { heading() })
        }
        when {
            cluster != Cluster.MAINNET -> Text("ORE not on ${cluster.label}: the ORE v3 program, Board and Treasury accounts were not found there (docs/ore/devnet-check.txt). No grid is shown.",
                color = EdgeColors.warning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ore-grid-absent"))
            s == null -> Text(if (ore.loading) "Reading the mainnet board…" else ore.error ?: "Not observed yet.", color = EdgeColors.warning, style = MaterialTheme.typography.bodyMedium)
        }
        val show = s != null || (deploy.pro && cluster != Cluster.MAINNET)
        if (show) for (r in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (c in 0 until 5) {
                val i = r * 5 + c
                val picked = deploy.pro && deploy.proMask and (1 shl i) != 0
                Box(Modifier.weight(1f).aspectRatio(1.15f).background(if (picked) EdgeColors.mint.copy(alpha = 0.18f) else EdgeColors.surfaceInset, RoundedCornerShape(10.dp))
                    .border(1.dp, if (picked) EdgeColors.mint else EdgeColors.border, RoundedCornerShape(10.dp))
                    .then(if (deploy.pro) Modifier.clickable(role = Role.Checkbox) { onToggle(i) } else Modifier)
                    .semantics { contentDescription = "Square ${i + 1}" + (s?.let { ", ${OreFormat.sol(it.round.deployedLamports[i])} SOL" } ?: "") + if (picked) ", selected for the deploy draft" else "" }
                    .padding(4.dp).testTag("grid-$i")) {
                    Text("${i + 1}", color = EdgeColors.textTertiary, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart))
                    s?.let { Text("◎" + OreFormat.sol(it.round.deployedLamports[i]), color = if (picked) EdgeColors.mint else EdgeColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomEnd)) }
                }
            }
        }
        s?.let { Text("Round ${it.round.id} · slot ${it.roundSlot} · read ${hm(it.observedAtMs)} from ${it.rpc}", color = EdgeColors.textTertiary, fontSize = 11.sp) }
        if (deploy.pro) Text("Pro: tap squares to add them to the deploy draft. Nothing is sent from the grid.", color = EdgeColors.textMuted, fontSize = 11.sp)
    }
}
