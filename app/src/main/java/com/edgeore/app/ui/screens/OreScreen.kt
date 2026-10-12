package com.edgeore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.ore.AdvisorState
import com.edgeore.app.ore.OreAdvisor
import com.edgeore.app.ore.OreClock
import com.edgeore.app.ore.OreFormat
import com.edgeore.app.ore.OreState
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.PillTone
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.StatusPill
import com.edgeore.app.ui.theme.EdgeColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val ORE_READ_ONLY_LABEL = "Read-only · mainnet observation"
const val ORE_DEPLOY_UNAVAILABLE = "Deploy unavailable in this release (read-only)"

/** Opened from Mine. Reads the live ORE v3 board every 15 s while shown; there is no transaction path on this screen. */
@Composable
fun OreScreen(vm: EdgeOreViewModel, compact: Boolean = false) {
    val ore by vm.ore.collectAsStateWithLifecycle()
    val advisor by vm.advisor.collectAsStateWithLifecycle()
    val ai by vm.ai.collectAsStateWithLifecycle()
    val onDevice by vm.onDevice.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ore.rpc) { while (true) { vm.refreshOre(); delay(15_000) } }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val backend = remember(ai.selectedModel, ai.endpoint, onDevice.selectedId, onDevice.downloaded) { vm.advisorBackend() }
    OreContent(ore, advisor, backend, now, onRefresh = vm::refreshOre, onSetRpc = vm::setOreRpc, onAsk = vm::askAdvisor, onClear = vm::clearAdvisor, compact = compact)
}

private fun localTime(ms: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))

@Composable
fun OreContent(
    ore: OreState, advisor: AdvisorState, advisorBackend: String?, nowMs: Long,
    onRefresh: () -> Unit, onSetRpc: (String) -> Unit, onAsk: (String) -> Unit, onClear: () -> Unit, compact: Boolean = false,
) {
    var pro by rememberSaveable { mutableStateOf(true) }
    val s = ore.snapshot
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ORE v3 board", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            StatusPill(ORE_READ_ONLY_LABEL, Modifier.testTag("ore-read-only"), tone = PillTone.MINT)
        }
        if (!compact) Text("Live state of the ORE v3 program on Solana mainnet, read with getMultipleAccounts. EdgeORE cannot deploy, sign or send anything here, and nothing on this screen is earned.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)

        val clock = s?.let { OreClock.at(it.board, it.roundSlot, nowMs - it.observedAtMs, it.slotRate) }
        Row(Modifier.fillMaxWidth().background(EdgeColors.surfaceInset, RoundedCornerShape(12.dp)).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            OreStat("DEPLOYED", s?.let { OreFormat.sol(it.round.totalDeployedLamports) + " SOL" }, EdgeColors.textPrimary)
            OreStat("MOTHERLODE", s?.let { OreFormat.ore(it.treasury.motherlodeGrams) + " ORE" }, EdgeColors.copper)
            OreStat("TIME", clock?.let(OreClock::label), EdgeColors.textPrimary)
        }
        if (s != null) {
            Text("Round ${s.round.id} · ${s.round.totalMiners} miners · Last read ${localTime(s.observedAtMs)} at slot ${s.roundSlot} from ${s.rpc}" +
                (s.slotRate?.let { " · time estimated at %.0f ms per slot (RPC performance sample)".format(it.msPerSlot) } ?: " · no slot rate measured, so time is in slots"),
                color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("ore-source"))
        } else {
            Text(if (ore.loading) "Reading the board…" else "Not observed yet.", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium)
        }
        ore.error?.let { Text(it + if (s != null) " Showing the read from ${localTime(s.observedAtMs)}." else "", color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium) }
        Text("Prices: not shown. No price source is configured in EdgeORE.", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            OreTab("Lite", !pro) { pro = false }
            OreTab("Pro", pro) { pro = true }
        }
        if (s != null && !pro) {
            Column(Modifier.fillMaxWidth().testTag("ore-lite"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(OreFormat.sol(s.round.totalDeployedLamports), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text("SOL deployed this round across 25 squares", color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (pro) {
            Column(Modifier.fillMaxWidth().testTag("ore-grid"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until 5) {
                        val i = r * 5 + c
                        val lamports = s?.round?.deployedLamports?.get(i)
                        Box(Modifier.weight(1f).aspectRatio(1f).border(1.dp, EdgeColors.border, RoundedCornerShape(8.dp)).padding(5.dp)
                            .testTag("ore-square-$i").semantics(mergeDescendants = true) { contentDescription = if (s == null) "Square ${i + 1}: not observed" else "Square ${i + 1}: ${OreFormat.sol(lamports!!)} SOL, ${s.round.count[i]} miners" }) {
                            Text("${i + 1}", color = EdgeColors.textMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart))
                            Text(lamports?.let(OreFormat::sol) ?: "—", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }
                Text(if (s == null) "All 25 squares are unobserved. A dash is not a zero bid." else "SOL deployed per square, from Round.deployed (lamports ÷ 10⁹, rounded down to 3 decimals).", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(ORE_DEPLOY_UNAVAILABLE, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("ore-deploy-unavailable"))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction(if (ore.loading) "Reading…" else "Refresh", enabled = !ore.loading, onClick = onRefresh)
        }
        if (!compact) RpcSetting(ore, onSetRpc)
        if (!compact) AdvisorCard(advisor, advisorBackend, s != null, onAsk, onClear)
        if (!compact) Text("Layout and program id from ${OreV3.SOURCE_REPO} at ${OreV3.SOURCE_COMMIT.take(12)} (verified build of program ${OreV3.PROGRAM_ID.take(8)}…). " +
            "The ORE source documents no devnet deployment (its localnet.sh clones mainnet accounts), so this screen reads mainnet, read-only.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.OreStat(label: String, value: String?, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: "Not observed", color = if (value == null) EdgeColors.copper else color, fontWeight = FontWeight.Bold, fontSize = if (value == null) 14.sp else 18.sp, textAlign = TextAlign.Center)
        Text(label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OreTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp)
        .border(1.dp, if (selected) EdgeColors.textPrimary else EdgeColors.surface, RoundedCornerShape(50))
        .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(text, color = if (selected) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RpcSetting(ore: OreState, onSetRpc: (String) -> Unit) {
    var url by rememberSaveable(ore.rpc) { mutableStateOf(ore.rpc) }
    EdgeCard(inset = true) {
        Text("Mainnet RPC for observation", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(url, { url = it }, label = { Text("https:// RPC URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ore.rpcError?.let { Text(it, color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium) }
        SecondaryAction("Use this RPC", enabled = url.isNotBlank() && url.trim() != ore.rpc) { onSetRpc(url) }
    }
}

@Composable
private fun AdvisorCard(st: AdvisorState, backend: String?, haveSnapshot: Boolean, onAsk: (String) -> Unit, onClear: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    EdgeCard(Modifier.testTag("ore-advisor")) {
        Text("Advisor", style = MaterialTheme.typography.titleMedium)
        if (backend == null) {
            Text(OreAdvisor.NOT_CONFIGURED, color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ore-advisor-not-configured"))
        } else {
            Text("Answers from $backend, given only this board snapshot and how ORE v3 works from its source. Text only: it has no tools and cannot sign, send, deploy, trade or stake. Numbers not in the snapshot are replaced.",
                color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Text(OreAdvisor.NOTICE, color = EdgeColors.copper, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        st.messages.forEach { m ->
            Text((if (m.fromUser) "You: " else "Advisor: ") + m.text, color = if (m.fromUser) EdgeColors.textMuted else EdgeColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
        }
        st.error?.let { Text(it, color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium) }
        if (backend != null) {
            OutlinedTextField(q, { q = it }, label = { Text("Ask about this round") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(if (st.busy) "Thinking…" else "Ask", enabled = !st.busy && haveSnapshot && q.isNotBlank()) { onAsk(q); q = "" }
                if (st.messages.isNotEmpty()) SecondaryAction("Clear", onClick = onClear)
            }
        }
    }
}
