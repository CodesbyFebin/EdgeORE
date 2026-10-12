package com.edgeore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeore.app.OreDeployUi
import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.contribution.SessionView
import com.edgeore.app.ore.OreDeployAvailability
import com.edgeore.app.solana.OreDeployLimits
import com.edgeore.app.solana.OreDeployNetwork
import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.theme.EdgeColors
import java.math.BigDecimal

const val ORE_DEPLOY_NFA = "Not financial advice. SOL deployed into an ORE round may be lost entirely; EdgeORE claims no income or return."
const val WALLET_REQUIRED = "Wallet required. Connect a Solana wallet to deploy."

private val cardShape = RoundedCornerShape(16.dp)
private fun Modifier.mineCard() = this.fillMaxWidth().background(EdgeColors.surface, cardShape).border(1.dp, EdgeColors.border, cardShape).padding(14.dp)
private fun sol(l: Long): String = BigDecimal(l).movePointLeft(9).stripTrailingZeros().toPlainString()

/** "Connect Solana wallets" row. */
@Composable
fun MineWalletRow(address: String?, busy: Boolean, onConnect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Connect Solana wallets", fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary)
            Text(address?.let { "Connected · ${it.take(4)}…${it.takeLast(4)}" } ?: "Not connected", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            if (address == null) Text("Mobile Wallet Adapter · no wallet session", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (address == null) Box(Modifier.heightIn(min = 48.dp).border(1.dp, EdgeColors.border, RoundedCornerShape(50)).background(EdgeColors.surface, RoundedCornerShape(50))
            .clickable(enabled = !busy, role = Role.Button, onClick = onConnect).testTag("mine-connect").padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text(if (busy) "Waiting…" else "Connect", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** SOL balance and ORE cards. Nothing read is shown as unread, never as zero. */
@Composable
fun MineBalanceCards(connected: Boolean, balance: RpcObservation?) {
    val fresh = balance as? RpcObservation.Fresh
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallCard("SOL", fresh?.let { sol(it.lamports) + " SOL" } ?: "Balance unavailable",
            when { fresh != null -> "Devnet · slot ${fresh.slot}"; connected -> "Devnet · not read yet"; else -> "Devnet · not read" }, Modifier.weight(1f), big = true, tag = "mine-sol")
        SmallCard("ORE", "Not observed", "ORE token balance is not read in this build", Modifier.weight(1f), tag = "mine-ore")
    }
}

@Composable
private fun SmallCard(label: String, value: String, note: String, modifier: Modifier, big: Boolean = false, tag: String) {
    Column(modifier.background(EdgeColors.surface, cardShape).border(1.dp, EdgeColors.border, cardShape).padding(12.dp).testTag(tag).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = EdgeColors.textPrimary, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, fontSize = if (big) 18.sp else 15.sp)
        Text(note, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/** "Edge node": the rock-and-pickaxe art (animated only while the session is ACTIVE) with the session controls. */
@Composable
fun EdgeNodeCard(view: SessionView, edgeModeResumed: Boolean, headline: String, onStart: () -> Unit, onStop: () -> Unit, onToggleEdgeMode: () -> Unit) {
    Column(Modifier.mineCard().testTag("edge-node"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Edge node", style = MaterialTheme.typography.titleLarge, color = EdgeColors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
            val active = view.phase == SessionPhase.ACTIVE
            Text(if (active) "Active" else "Paused", color = EdgeColors.background, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.background(if (active) EdgeColors.mint else EdgeColors.copper, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp).testTag("edge-node-badge"))
        }
        MiningSessionPanel(view, edgeModeResumed, headline, onStart, onStop, onToggleEdgeMode)
    }
}

/** CPU and Memory: what Android reports about this phone. Usage is not sampled, so no live bar is drawn. */
@Composable
fun DeviceCards(cores: Int?, memoryGbClass: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallCard("CPU", cores?.let { "$it cores" } ?: "Not read", "Usage not sampled", Modifier.weight(1f), big = true, tag = "mine-cpu")
        SmallCard("Memory", memoryGbClass?.let { "$it GB class" } ?: "Not read", "Usage not sampled", Modifier.weight(1f), big = true, tag = "mine-mem")
    }
}

/** Why Deploy is off, or null when it may be pressed. Order: cluster, wallet, then the amount limits. */
fun deployBlocker(ui: OreDeployUi, walletConnected: Boolean, balanceLamports: Long?): String? = when (val a = ui.availability) {
    OreDeployAvailability.NotChecked -> "Devnet not checked for ORE v3 yet."
    OreDeployAvailability.Checking -> "Checking devnet for ORE v3…"
    is OreDeployAvailability.Unavailable -> "Deploy off: ${a.reason} (devnet). EdgeORE does not fall back to mainnet."
    is OreDeployAvailability.Available -> when {
        !walletConnected -> WALLET_REQUIRED
        else -> OreDeployLimits.refusal(ui.totalLamports ?: 0, Integer.bitCount(ui.mask), balanceLamports)
    }
}

/** Adds a chip amount, clamped to MAX (balance minus the fee reserve, at most the per-deploy cap) when MAX is known. */
fun addChip(ui: OreDeployUi, addLamports: Long, balanceLamports: Long?): OreDeployUi {
    val next = (ui.totalLamports ?: 0) + addLamports
    val max = OreDeployLimits.max(balanceLamports) ?: OreDeployLimits.PER_DEPLOY_CAP_LAMPORTS
    return ui.copy(totalLamports = next.coerceAtMost(max))
}

/** The ORE Mining card: Lite / Pro, amount chips, ROUNDS, PER ROUND and Deploy. Deploy only opens the exact-message review. */
@Composable
fun OreMiningCard(
    ui: OreDeployUi, walletConnected: Boolean, balanceLamports: Long?,
    onEdit: ((OreDeployUi) -> OreDeployUi) -> Unit, onCheck: () -> Unit, onDeploy: () -> Unit,
) {
    val total = ui.totalLamports ?: 0
    val blocker = deployBlocker(ui, walletConnected, balanceLamports)
    Column(Modifier.mineCard().testTag("ore-mining"), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ORE Mining", style = MaterialTheme.typography.titleLarge, color = EdgeColors.textPrimary, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Box(Modifier.size(112.dp).border(3.dp, Brush.sweepGradient(listOf(EdgeColors.copper, Color(0xFFF58A4E), EdgeColors.mint, EdgeColors.copper)), CircleShape)
            .padding(10.dp).background(EdgeColors.surfaceInset, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EdgeIcons.Ore, contentDescription = "ORE emblem", tint = EdgeColors.copper, modifier = Modifier.size(52.dp))
        }
        Row(Modifier.border(1.dp, EdgeColors.border, RoundedCornerShape(50)).padding(3.dp)) {
            ToggleTab("Lite", !ui.pro) { onEdit { it.copy(pro = false) } }
            ToggleTab("Pro", ui.pro) { onEdit { it.copy(pro = true) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◎", color = EdgeColors.mint, fontSize = 26.sp)
            Text(sol(total), color = if (total == 0L) EdgeColors.textMuted else EdgeColors.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("ore-amount"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("+0.01") { onEdit { addChip(it, 10_000_000, balanceLamports) } }
            Chip("+0.1") { onEdit { addChip(it, 100_000_000, balanceLamports) } }
            Chip("+1") { onEdit { addChip(it, 1_000_000_000, balanceLamports) } }
            Chip("MAX", enabled = balanceLamports != null) { onEdit { it.copy(totalLamports = OreDeployLimits.max(balanceLamports)) } }
        }
        if (ui.pro) SquarePicker(ui.proMask) { m -> onEdit { it.copy(proMask = m) } }
        val squares = Integer.bitCount(ui.mask)
        Line("ROUNDS", "1", "One round per deploy. Multi-round automation is not supported.")
        Line("PER ROUND", "◎ " + sol(total), OreDeployLimits.perSquare(total, squares)?.let { "${sol(it)} SOL on each of $squares square${if (squares == 1) "" else "s"}" }
            ?: if (ui.pro && squares == 0) "Pick squares on the grid" else "Add an amount")
        PrimaryAction("Deploy", Modifier.fillMaxWidth().testTag("ore-deploy"), enabled = blocker == null, onClick = onDeploy)
        Text(blocker ?: "Opens the exact-message review. You approve, your wallet signs, then you submit separately.",
            color = if (blocker != null) EdgeColors.copper else EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            modifier = Modifier.testTag("ore-deploy-hint"))
        ui.message?.let { Text(it, color = EdgeColors.copper, style = MaterialTheme.typography.labelSmall) }
        val net = when (val a = ui.availability) {
            is OreDeployAvailability.Available -> "Devnet: ORE v3 found · round ${a.roundId} at slot ${a.slot}"
            is OreDeployAvailability.Unavailable -> "Devnet: ORE v3 not available"
            OreDeployAvailability.Checking -> "Devnet: checking…"
            OreDeployAvailability.NotChecked -> "Devnet: not checked"
        } + if (OreDeployNetwork.MAINNET_ENABLED) "" else " · mainnet deploy is disabled in this build"
        Text(net, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("ore-deploy-network"))
        if (ui.availability !is OreDeployAvailability.Checking) Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onCheck).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Check devnet again", color = EdgeColors.mint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Text("Limits: up to 0.1 SOL per deploy, and MAX keeps 0.01 SOL back for fees and account rent. Your daily budget also applies.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(ORE_DEPLOY_NFA, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.testTag("ore-nfa"))
    }
}

@Composable
private fun ToggleTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 40.dp).background(if (selected) EdgeColors.background else Color.Transparent, RoundedCornerShape(50))
        .then(if (selected) Modifier.border(1.dp, EdgeColors.textPrimary, RoundedCornerShape(50)) else Modifier)
        .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.testTag("ore-mode-$text").padding(horizontal = 26.dp, vertical = 10.dp)) {
        Text(text, color = if (selected) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowScope.Chip(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.weight(1f).heightIn(min = 44.dp).background(EdgeColors.surfaceInset, RoundedCornerShape(50)).border(1.dp, EdgeColors.border, RoundedCornerShape(50))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag("chip-$text"), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun Line(label: String, value: String, note: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(value, color = EdgeColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text(note, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SquarePicker(mask: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("ore-square-picker"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in 0 until 5) {
                val i = r * 5 + c; val on = mask and (1 shl i) != 0
                Box(Modifier.weight(1f).aspectRatio(1.6f).background(if (on) EdgeColors.mint.copy(alpha = 0.25f) else EdgeColors.surfaceInset, RoundedCornerShape(6.dp))
                    .border(1.dp, if (on) EdgeColors.mint else EdgeColors.border, RoundedCornerShape(6.dp))
                    .clickable(role = Role.Checkbox) { onChange(mask xor (1 shl i)) }.semantics { contentDescription = "Square ${i + 1}" + if (on) ", selected" else "" }.testTag("pick-$i"),
                    contentAlignment = Alignment.Center) { Text("${i + 1}", color = if (on) EdgeColors.textPrimary else EdgeColors.textMuted, fontSize = 11.sp) }
            }
        }
    }
}

/** CONTRIBUTION toggles. Each states its real effect (from settings/ControlEffects.kt), not a guess. */
@Composable
fun ContributionToggles(chargeOnly: Boolean, thermalGuard: Boolean, batteryReservePercent: Int, effect: (com.edgeore.app.settings.Control) -> String,
                        onChargeOnly: (Boolean) -> Unit, onThermal: (Boolean) -> Unit) {
    Column(Modifier.mineCard().testTag("contribution-toggles"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("CONTRIBUTION", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text("These gate Edge Mode and the Start Mining session on this phone. They do not cap CPU, memory, storage or network.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        ToggleRow("Charge-only", effect(com.edgeore.app.settings.Control.CHARGE_ONLY), chargeOnly, onChargeOnly)
        ToggleRow("Thermal guard", effect(com.edgeore.app.settings.Control.THERMAL_GUARD), thermalGuard, onThermal)
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Battery reserve", color = EdgeColors.textPrimary, modifier = Modifier.weight(1f))
            Text(effect(com.edgeore.app.settings.Control.BATTERY_RESERVE), color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("$batteryReservePercent%", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ToggleRow(label: String, effect: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = EdgeColors.textPrimary, modifier = Modifier.weight(1f))
        Text(effect, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked, onChange, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.background),
            modifier = Modifier.semantics { contentDescription = label })
    }
}
