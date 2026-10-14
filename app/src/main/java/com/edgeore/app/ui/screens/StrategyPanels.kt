package com.edgeore.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.edge.Strategy
import com.edgeore.app.edge.StrategyPlan
import com.edgeore.app.edge.StrategyPlaybook
import com.edgeore.app.edge.StrategyRules
import com.edgeore.app.ore.OreFormat
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.solana.OreDeployLimits
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.components.SectionTitle
import com.edgeore.app.ui.theme.EdgeColors

/** Collapsed by default under the Crew Pool button. Strategies pre-fill the review only. */
@Composable
fun StrategiesSection(
    vm: EdgeOreViewModel,
    snap: OreSnapshot?,
    onOpenReview: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var lateLook by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(open, lateLook) {
        while (open || lateLook) {
            kotlinx.coroutines.delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    // Late Look: foreground-only, in-app reminder. It never deploys or signs.
    if (lateLook) LateLookBanner(snap, now) { lateLook = false }
    val toggle = if (open) "Hide strategies" else "Strategies (6)"
    SecondaryAction(toggle, Modifier.fillMaxWidth().testTag("open-strategies")) {
        open = !open
    }
    if (!open) return
    var thresholdOre by remember { mutableStateOf("") }
    val context = StrategyPlaybook.Inputs(OreDeployLimits.MAINNET_CAP_LAMPORTS, 0, vm.crewMasks(), now)
    StrategyCards(snap, thresholdOre, { thresholdOre = it }, context) { s ->
        if (s == Strategy.LATE_LOOK) lateLook = true
        if (vm.applyStrategy(s, StrategyInputs.grams(thresholdOre))) onOpenReview()
    }
}

private const val TICK_MS = 1000L

@Composable
private fun LateLookBanner(
    snap: OreSnapshot?,
    now: Long,
    onStop: () -> Unit,
) {
    val plan =
        snap?.let {
            StrategyPlaybook.lateLook(
                StrategyPlaybook.clock(it, now),
            )
        } as? StrategyPlan.Remind
    EdgeCard(Modifier.testTag("late-look")) {
        Text(
            plan?.reason ?: "Late Look needs a live board read.",
            color =
                if (plan?.now ==
                    true
                ) {
                    EdgeColors.mainnetWarning
                } else {
                    EdgeColors.textMuted
                },
        )
        SecondaryAction("Stop Late Look", Modifier.fillMaxWidth(), onClick = onStop)
    }
}

/** Parses the Motherlode Watch threshold in ORE (up to 11 decimals) into grams; blank or invalid is 0 (gate closed). */
object StrategyInputs {
    fun grams(ore: String): Long =
        ore
            .trim()
            .toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 }
            ?.movePointRight(GRAMS_DECIMALS)
            ?.toBigInteger()
            ?.toLong() ?: 0L

    private const val GRAMS_DECIMALS = 11
}

/** Stateless strategy cards, so they render in JVM tests. Each shows its trade-off and the shared disclaimer. */
@Composable
fun StrategyCards(
    snap: OreSnapshot?,
    thresholdOre: String,
    onThreshold: (String) -> Unit,
    context: StrategyPlaybook.Inputs,
    onApply: (Strategy) -> Unit,
) {
    val inputs = context.copy(motherlodeThresholdGrams = StrategyInputs.grams(thresholdOre))
    Column(Modifier.testTag("strategies"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Strategies", "Each only pre-fills the review. You review and sign every deploy in your wallet.")
        Notice(Strategy.DISCLAIMER)
        Strategy.entries.forEach { s ->
            val plan = StrategyPlaybook.plan(s, snap, inputs)
            StrategyCard(s, plan, thresholdOre, onThreshold) { onApply(s) }
        }
        Text(
            "Mainnet cap 0.01 SOL per deploy, Rounds 1, risk tick required. " +
                "Nothing deploys automatically or in the background.",
            color = EdgeColors.textMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun StrategyCard(
    s: Strategy,
    plan: StrategyPlan,
    thresholdOre: String,
    onThreshold: (String) -> Unit,
    onApply: () -> Unit,
) {
    EdgeCard(Modifier.testTag("strategy-${s.name}")) {
        Text(s.title, color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        if (!s.verified) Text(StrategyRules.UNVERIFIED, color = EdgeColors.danger, fontSize = 12.sp)
        Text(s.tradeOff, color = EdgeColors.textMuted, fontSize = 12.sp)
        Text(Strategy.DISCLAIMER, color = EdgeColors.textMuted, fontSize = 11.sp)
        if (s == Strategy.MOTHERLODE_WATCH) {
            OutlinedTextField(
                thresholdOre,
                onThreshold,
                Modifier.fillMaxWidth().testTag("motherlode-threshold"),
                label = { Text("Motherlode threshold (ORE)") },
                singleLine = true,
            )
        }
        val status = planStatus(plan)
        Text(status.first, Modifier.testTag("strategy-status-${s.name}"), color = status.second, fontSize = 12.sp)
        val enabled = s.verified && planEnabled(plan)
        val tag = Modifier.fillMaxWidth().testTag("strategy-apply-${s.name}")
        SecondaryAction(applyLabel(s), tag, enabled = enabled, onClick = onApply)
    }
}

private fun applyLabel(s: Strategy) =
    when (s) {
        Strategy.CREW_BLANKET -> "Review my crew share"
        Strategy.MOTHERLODE_WATCH -> "Watch the motherlode"
        Strategy.LATE_LOOK -> "Show reminder"
        else -> "Pre-fill the review"
    }

private fun planEnabled(p: StrategyPlan) =
    when (p) {
        is StrategyPlan.Fill -> true
        is StrategyPlan.Gate -> true
        is StrategyPlan.Remind -> true
        else -> false
    }

private fun planStatus(p: StrategyPlan): Pair<String, Color> =
    when (p) {
        is StrategyPlan.Fill -> {
            val n = Integer.bitCount(p.mask)
            val amt = p.totalLamports?.let { " · ${OreFormat.sol(it)} SOL total" } ?: ""
            "Would pre-fill $n square(s)$amt." to EdgeColors.textPrimary
        }
        is StrategyPlan.Gate -> p.reason to if (p.open) EdgeColors.mint else EdgeColors.mainnetWarning
        is StrategyPlan.Remind -> p.reason to if (p.now) EdgeColors.mainnetWarning else EdgeColors.textMuted
        is StrategyPlan.SitOut -> p.reason to EdgeColors.mainnetWarning
        is StrategyPlan.Disabled -> p.reason to EdgeColors.textMuted
    }
