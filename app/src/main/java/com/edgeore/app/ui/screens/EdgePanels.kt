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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.edge.History
import com.edgeore.app.edge.RoundExplainer
import com.edgeore.app.edge.StoredProof
import com.edgeore.app.edge.StrategyPreset
import com.edgeore.app.edge.TileInsight
import com.edgeore.app.ore.OreFormat
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors

private fun crowdColor(c: TileInsight.Crowding) =
    when (c) {
        TileInsight.Crowding.EMPTY -> EdgeColors.surfaceInset
        TileInsight.Crowding.LIGHT -> EdgeColors.mint.copy(alpha = 0.35f)
        TileInsight.Crowding.AVERAGE -> EdgeColors.copper.copy(alpha = 0.30f)
        TileInsight.Crowding.CROWDED -> EdgeColors.copper.copy(alpha = 0.75f)
    }

private fun crowdWord(c: TileInsight.Crowding) =
    when (c) {
        TileInsight.Crowding.EMPTY -> "empty"
        TileInsight.Crowding.LIGHT -> "light"
        TileInsight.Crowding.AVERAGE -> "average"
        TileInsight.Crowding.CROWDED -> "crowded"
    }

/** Information only. Selecting a tile shows its numbers; the only action is "Review", which opens the normal review. */
@Composable
fun TileInsightPanel(
    tiles: List<TileInsight.Tile>?,
    soloMask: Int? = null,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onReview: (Int) -> Unit,
    onPreset: (StrategyPreset) -> Unit,
) {
    EdgeCard(Modifier.testTag("tile-insight")) {
        Text("Tile insight", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(
            "Crowding and expected value per square from the live round. ${TileInsight.LABEL.replaceFirstChar { it.uppercase() }}.",
            color = EdgeColors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.testTag("ev-label"),
        )
        if (tiles == null) {
            Notice("Not synced: no live board read yet, so there is nothing to estimate.")
        } else {
            for (r in 0 until 5) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until 5) {
                        val t = tiles[r * 5 + c]
                        val on = selected == t.square
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(crowdColor(t.crowding), RoundedCornerShape(10.dp))
                                .border(if (on) 2.dp else 1.dp, if (on) EdgeColors.mint else EdgeColors.border, RoundedCornerShape(10.dp))
                                .clickable { onSelect(t.square) }
                                .semantics { contentDescription = "Square ${t.square + 1}, ${crowdWord(t.crowding)}" }
                                .testTag("insight-${t.square}"),
                        ) {
                            Text(
                                "${t.square + 1}",
                                color = EdgeColors.textPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.Center),
                            )
                            if (soloMask != null && soloMask and (1 shl t.square) != 0) {
                                Text(
                                    "★",
                                    color = EdgeColors.copper,
                                    fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp).testTag("solo-${t.square}"),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "Light = less SOL than average · Crowded = more than 1.5× · ★ solo tile: if it wins, one miner on it takes the round's ORE " +
                    "(chosen by SOL weight); other 15 tiles split it by SOL. Set by distribution_mask(round id) in ORE v3. " +
                    "Losing tiles keep about 89.1% of their SOL (1% admin + 10% protocol fee).",
                color = EdgeColors.textMuted,
                fontSize = 11.sp,
            )
            val t = selected?.let { tiles[it] }
            if (t != null) {
                Column(Modifier.testTag("insight-detail"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Square ${t.square + 1}: ${OreFormat.sol(t.deployedLamports)} SOL · ${t.miners} miners · ${crowdWord(t.crowding)}",
                        color = EdgeColors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Text(
                        "For ${OreFormat.sol(
                            t.stakeLamports,
                        )} SOL here: about ${OreFormat.sol(t.expectedSolKeptLamports)} SOL kept on average, " +
                            "about ${java.math.BigDecimal.valueOf(
                                t.expectedOreGrams,
                                11,
                            ).setScale(
                                5,
                                java.math.RoundingMode.DOWN,
                            ).stripTrailingZeros().toPlainString()} ORE on average (${TileInsight.LABEL}).",
                        color = EdgeColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
                SecondaryAction("Review a deploy on square ${t.square + 1}", Modifier.fillMaxWidth().testTag("insight-review")) {
                    onReview(t.square)
                }
            }
        }
        Text("Strategy presets · pre-fill the review only", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StrategyPreset.entries.forEach { p ->
                SecondaryAction(p.title, Modifier.weight(1f).testTag("preset-${p.name}")) { onPreset(p) }
            }
        }
        Text(
            "Presets only pick squares. You still review the exact message and sign each deploy in your wallet. Mainnet cap 0.01 SOL, Rounds 1.",
            color = EdgeColors.textMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun ExplainerCard(
    text: String?,
    backend: String?,
    onRound: () -> Unit,
    onTile: (() -> Unit)?,
) {
    EdgeCard(Modifier.testTag("explainer")) {
        Text("Explain this round", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(
            if (backend ==
                null
            ) {
                RoundExplainer.NOT_CONFIGURED
            } else {
                "AI model: $backend. The explanation below is the built-in rule-based one."
            },
            color = EdgeColors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.testTag("explainer-backend"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Round", Modifier.weight(1f), onClick = onRound)
            if (onTile != null) SecondaryAction("Selected square", Modifier.weight(1f), onClick = onTile)
        }
        if (text != null) Text(text, color = EdgeColors.textPrimary, fontSize = 13.sp, modifier = Modifier.testTag("explainer-text"))
        Text("The explainer reads; it cannot sign or send.", color = EdgeColors.textMuted, fontSize = 11.sp)
    }
}

@Composable
fun RoundAlertsCard(
    on: Boolean,
    message: String?,
    onToggle: (Boolean) -> Unit,
) {
    EdgeCard(Modifier.testTag("round-alerts")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Round alerts", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Round ending, motherlode hit, your squares won. Local notifications from read-only polls while EdgeORE is open. No server.",
                    color = EdgeColors.textMuted,
                    fontSize = 12.sp,
                )
            }
            Switch(on, onToggle, Modifier.testTag("alerts-switch"))
        }
        if (message != null) Notice(message, error = true)
    }
}

@Composable
fun HistoryView(rows: List<History.Row>) {
    val t = History.totals(rows)
    Column(Modifier.testTag("history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("My history", "From your own signed deploy receipts and Round Proofs. No leaderboard server.")
        EdgeCard {
            Text("Settled rounds: ${t.rounds} · waiting: ${t.unsettled}", color = EdgeColors.textPrimary)
            Text("SOL deployed (settled): ${OreFormat.sol(t.deployedLamports)}", color = EdgeColors.textMuted, fontSize = 13.sp)
            Text("SOL returned by the rules: ${OreFormat.sol(t.solBackLamports)}", color = EdgeColors.textMuted, fontSize = 13.sp)
            Text(
                "Net SOL: ${if (t.netSolLamports < 0) "−" else ""}${OreFormat.sol(kotlin.math.abs(t.netSolLamports))}",
                color = EdgeColors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.testTag("history-net"),
            )
            Text("ORE credited by the rules: ${OreFormat.ore(t.oreGrams)}", color = EdgeColors.textMuted, fontSize = 13.sp)
            Text(
                "Computed from account bytes; not a balance check, not claimed, past results only.",
                color = EdgeColors.textMuted,
                fontSize = 11.sp,
            )
        }
        if (rows.isEmpty()) Notice("No signed ORE deploys yet.")
        rows.forEach { r ->
            EdgeCard(inset = true) {
                Text(
                    "Round #${r.roundId ?: "?"} · ${OreFormat.sol(r.deployedLamports)} SOL",
                    color = EdgeColors.textPrimary,
                    fontSize = 13.sp,
                )
                Text(r.status, color = EdgeColors.textMuted, fontSize = 12.sp)
                if (r.solBackLamports != null) {
                    Text(
                        "Back: ${OreFormat.sol(r.solBackLamports)} SOL · ${OreFormat.ore(r.oreGrams ?: 0)} ORE",
                        color = EdgeColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
                r.signature?.let {
                    Text(
                        it.take(16) + "…",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = EdgeColors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun RoundProofView(
    proofs: List<StoredProof>,
    onExport: (StoredProof) -> Unit,
) {
    Column(Modifier.testTag("round-proofs"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Round Proof", "Settled rounds re-derived from the Round account, attached to your signed receipts.")
        if (proofs.isEmpty()) Notice("No Round Proofs yet. One is made after a round you deployed in settles, while EdgeORE is open.")
        proofs.forEach { p ->
            EdgeCard(Modifier.testTag("proof-${p.roundId}")) {
                Text(
                    "Round #${p.roundId} · " + if (p.check.accepted) "re-derived" else "did not re-derive",
                    color = if (p.check.accepted) EdgeColors.mint else EdgeColors.danger,
                    fontWeight = FontWeight.SemiBold,
                )
                p.check.winningSquare?.let { Text("Winning square: $it", color = EdgeColors.textPrimary) }
                Text("Verified", color = EdgeColors.textPrimary, fontSize = 12.sp)
                p.check.verified.forEach { Text("✓ $it", color = EdgeColors.textMuted, fontSize = 11.sp) }
                p.check.failures.forEach { Text("✗ $it", color = EdgeColors.danger, fontSize = 11.sp) }
                Text("Not verified", color = EdgeColors.textPrimary, fontSize = 12.sp)
                p.check.unverified.forEach { Text("· $it", color = EdgeColors.textMuted, fontSize = 11.sp) }
                Text(
                    "The checker verifies the integrity of signed receipt contents, then re-derives these results offline.",
                    color = EdgeColors.textMuted,
                    fontSize = 11.sp,
                )
                PrimaryAction("Export .oreproof", Modifier.fillMaxWidth()) { onExport(p) }
            }
        }
    }
}

/** Stateful section placed under the ORE card on Home. */
@Composable
fun EdgeInsightsSection(
    vm: EdgeOreViewModel,
    onOpenReview: () -> Unit,
    onHistory: () -> Unit,
    onProofs: () -> Unit,
    onAlerts: (Boolean) -> Unit,
) {
    val ore by vm.ore.collectAsStateWithLifecycle()
    val deploy by vm.oreDeploy.collectAsStateWithLifecycle()
    val edge by vm.edge.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { vm.refreshProofs() }
    ProtocolBanner(edge.protocol)
    val snap = ore.snapshot.takeIf { deploy.mainnet }
    val tiles =
        snap?.let {
            TileInsight.tiles(
                it.round.deployedLamports,
                it.round.count,
                deploy.totalLamports ?: com.edgeore.app.solana.OreDeployLimits.MAINNET_CAP_LAMPORTS,
                com.edgeore.app.ore.OreV3.GRAMS_PER_ORE,
                it.treasury.motherlodeGrams,
            )
        }
    TileInsightPanel(
        tiles,
        snap?.let {
            com.edgeore.app.edge.RoundRules
                .distributionMask(it.round.id)
        },
        selected,
        { selected = it },
        { i ->
            vm.reviewSquare(i)
            onOpenReview()
        },
        vm::applyPreset,
    )
    ExplainerCard(edge.explanation, edge.explainerBackend, { vm.explain(null) }, selected?.let { s -> { vm.explain(s) } })
    PlanningCalculatorCard()
    RoundAlertsCard(edge.alertsOn, edge.alertsMessage, onAlerts)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryAction("My history", Modifier.weight(1f).testTag("open-history"), onClick = onHistory)
        SecondaryAction("Round Proof", Modifier.weight(1f).testTag("open-proofs"), onClick = onProofs)
    }
    if (snap == null) {
        Text(
            "Tile insight uses the mainnet board. Switch the network selector to Mainnet to read it.",
            color = EdgeColors.textMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun PlanningCalculatorCard(initial: com.edgeore.app.edge.PlanningCalculator.Inputs? = null) {
    var sol by remember { mutableStateOf(initial?.solPerRound?.toString() ?: "0.01") }
    var rounds by remember { mutableStateOf(initial?.rounds?.toString() ?: "1") }
    var tiles by remember { mutableStateOf(initial?.tiles?.toString() ?: "25") }
    var others by remember { mutableStateOf(initial?.othersSolPerTile?.toString() ?: "0.5") }
    var price by remember { mutableStateOf(initial?.orePriceSol?.toString() ?: "") }
    var fee by remember { mutableStateOf(initial?.txFeeSol?.toString() ?: "0.000005") }
    EdgeCard(Modifier.testTag("planning")) {
        Text("Planning calculator", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(
            "Your inputs, ORE v3 rules, averages only: ${com.edgeore.app.edge.PlanningCalculator.LABEL}.",
            color = EdgeColors.textMuted,
            fontSize = 12.sp,
        )

        @Composable
        fun field(
            label: String,
            v: String,
            set: (String) -> Unit,
        ) = androidx.compose.material3.OutlinedTextField(
            v,
            set,
            label = { Text(label, fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { field("SOL per round", sol) { sol = it } }
            Column(Modifier.weight(1f)) { field("Rounds", rounds) { rounds = it } }
            Column(Modifier.weight(1f)) { field("Tiles", tiles) { tiles = it } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { field("Others' SOL per tile", others) { others = it } }
            Column(Modifier.weight(1f)) { field("Assumed ORE price (SOL)", price) { price = it } }
            Column(Modifier.weight(1f)) { field("Tx fee (SOL)", fee) { fee = it } }
        }
        val r =
            runCatching {
                com.edgeore.app.edge.PlanningCalculator.compute(
                    com.edgeore.app.edge.PlanningCalculator.Inputs(
                        sol.toDouble(),
                        rounds.toInt(),
                        tiles.toInt(),
                        others.toDouble(),
                        price.toDoubleOrNull() ?: 0.0,
                        fee.toDouble(),
                    ),
                )
            }.getOrNull()
        if (r == null) {
            Notice("Enter numbers: tiles 1–25, rounds at least 1.")
        } else {
            fun f(x: Double) =
                java.math
                    .BigDecimal(x)
                    .setScale(6, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()
            Column(Modifier.testTag("planning-result"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Chance one of your tiles wins a round: ${TileInsight.pct(r.chanceAnyTileWinsPerRound)}",
                    color = EdgeColors.textPrimary,
                    fontSize = 13.sp,
                )
                Text(
                    "Average SOL cost per round (fees + tx): ${f(r.expectedSolCostPerRound)} SOL",
                    color = EdgeColors.textPrimary,
                    fontSize = 13.sp,
                )
                Text("Average ORE share per round: ${f(r.expectedOrePerRound)} ORE", color = EdgeColors.textPrimary, fontSize = 13.sp)
                Text(
                    "Break-even ORE price: " + (r.breakEvenOrePriceSol?.let { "${f(it)} SOL per ORE" } ?: "none (no ORE share)"),
                    color = EdgeColors.textPrimary,
                    fontSize = 13.sp,
                )
                if (price.toDoubleOrNull() != null) {
                    Text(
                        "Average net per round at your assumed price: ${f(r.expectedNetSolPerRoundAtAssumedPrice)} SOL",
                        color = EdgeColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "SOL you would commit over ${rounds.trim()} round(s): ${f(r.totalSolCommitted)} SOL",
                    color = EdgeColors.textMuted,
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            "Ignores the motherlode, later deploys that dilute your share, and price changes. Single rounds vary widely. Not advice.",
            color = EdgeColors.textMuted,
            fontSize = 11.sp,
        )
    }
}

/** Shown when the live ORE program or Config differs from what this build was written for. */
@Composable
fun ProtocolBanner(r: com.edgeore.app.edge.ProtocolGuard.Result?) {
    if (r is com.edgeore.app.edge.ProtocolGuard.Result.Changed) {
        EdgeCard(Modifier.testTag("protocol-banner")) {
            Text(com.edgeore.app.edge.ProtocolGuard.BANNER, color = EdgeColors.mainnetWarning, fontWeight = FontWeight.SemiBold)
            r.reasons.forEach { Text("· $it", color = EdgeColors.textMuted, fontSize = 12.sp) }
            Text(
                "Tile insight, the planner and Round Proof use the rules this build was written for.",
                color = EdgeColors.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}
