package com.edgeore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeore.app.edge.FeeMath
import com.edgeore.app.edge.RoundRules
import com.edgeore.app.ore.OreClock
import com.edgeore.app.ore.OreFormat
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.theme.EdgeColors

/**
 * Miner-first home: the live ORE v3 round board. Read-only. Every number comes from [snap] (a real chain read);
 * without one nothing is shown in its place. Deploys still go through the exact-message review and the wallet.
 */
object MinerHome {
    const val TITLE = "Mine · ORE round"
    const val SIGN_EVERY_ROUND = "You review and sign every round in your wallet. Nothing deploys on its own."
    const val NO_BOARD = "No live board read yet. Switch to Mainnet to read the ORE v3 board (read-only)."
    const val NOT_AFFILIATED = "ORE is by Regolith Labs. EdgeORE is not affiliated with Regolith Labs or ORE."

    fun timing(c: OreV3.Config?): String =
        c?.let {
            "Round ${it.roundSlots} slots · break ${it.intermissionSlots} slots (ORE Config, read from chain)"
        } ?: "Round length: Config not read, so it is not shown"

    fun motherlode(grams: Long): String = "Motherlode pool ${OreFormat.ore(grams)} ORE · hits 1 in 500 rounds (program rule)"
}

@Composable
fun MinerRoundBoard(
    snap: OreSnapshot?,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    EdgeCard(modifier.testTag("miner-board")) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                MinerHome.TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = EdgeColors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (snap != null) {
                Text(
                    OreClock.label(OreClock.at(snap.board, snap.boardSlot, nowMs - snap.observedAtMs, snap.slotRate)),
                    color = EdgeColors.mint,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("miner-countdown"),
                )
            }
        }
        if (snap == null) {
            Text(MinerHome.NO_BOARD, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Round ${snap.round.id} · ${OreFormat.sol(snap.round.totalDeployedLamports)} SOL deployed · " +
                    "${snap.round.totalMiners} miners",
                color = EdgeColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            BoardGrid(snap)
            Text("SOL per square · squares 0–24 · ★ solo square", color = EdgeColors.textMuted, fontSize = 11.sp)
            Text(
                MinerHome.motherlode(snap.treasury.motherlodeGrams),
                color = EdgeColors.copper,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("miner-motherlode"),
            )
            Text(
                MinerHome.timing(snap.config),
                color = EdgeColors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.testTag("miner-timing"),
            )
        }
        Text(FeeMath.LOSING, color = EdgeColors.textMuted, fontSize = 11.sp, modifier = Modifier.testTag("miner-fees"))
        Text(MinerHome.SIGN_EVERY_ROUND, color = EdgeColors.textMuted, fontSize = 11.sp)
    }
}

private const val SIDE = 5
private const val SQUARE_RATIO = 1.1f

@Composable
private fun BoardGrid(snap: OreSnapshot) {
    val solo = RoundRules.distributionMask(snap.round.id)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until SIDE) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until SIDE) {
                    val i = row * SIDE + col
                    val isSolo = solo and (1 shl i) != 0
                    val sol = OreFormat.sol(snap.round.deployedLamports[i])
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(SQUARE_RATIO)
                            .background(EdgeColors.surfaceInset, RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (isSolo) EdgeColors.copper else EdgeColors.border,
                                RoundedCornerShape(8.dp),
                            ).testTag("miner-square-$i")
                            .semantics { contentDescription = "Square $i, $sol SOL${if (isSolo) ", solo" else ""}" },
                    ) {
                        Text(
                            "$i${if (isSolo) " ★" else ""}",
                            color = EdgeColors.textMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        )
                        Text(
                            sol,
                            color = EdgeColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Claim: shown honestly as not available in this build. ClaimSOL/ClaimORE are not in the reviewed signing path or the
 * decoder yet, so the app shows no claimable amount and builds no claim message (a placeholder never supplies values).
 */
@Composable
fun MinerClaimCard(
    onReceipts: () -> Unit,
    receiptCount: Int,
) {
    EdgeCard(Modifier.testTag("miner-claim")) {
        Text("Claim & receipts", style = MaterialTheme.typography.titleMedium, color = EdgeColors.textPrimary)
        Text(
            "Claim review is not in this build: EdgeORE does not decode ClaimSOL or ClaimORE yet, " +
                "so it shows no claimable amount and builds no claim message. Claim from a wallet or app you trust.",
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        SecondaryAction(
            if (receiptCount == 0) "Receipts · none yet" else "Receipts · $receiptCount on this phone",
            Modifier.fillMaxWidth().testTag("miner-receipts"),
            onClick = onReceipts,
        )
        Text(MinerHome.NOT_AFFILIATED, color = EdgeColors.textMuted, fontSize = 11.sp)
    }
}
