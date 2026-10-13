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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.theme.EdgeColors
import java.math.BigDecimal

const val ORE_DEPLOY_NFA = "Not financial advice. SOL deployed into an ORE round may be lost entirely; EdgeORE claims no income or return."
const val WALLET_REQUIRED = "Wallet required. Connect a Solana wallet to deploy."

private val cardShape = RoundedCornerShape(16.dp)

private fun Modifier.mineCard() =
    this
        .fillMaxWidth()
        .background(EdgeColors.surface, cardShape)
        .border(1.dp, EdgeColors.border, cardShape)
        .padding(14.dp)

private fun sol(l: Long): String = BigDecimal(l).movePointLeft(9).stripTrailingZeros().toPlainString()

/** "Connect Solana wallets" row. */
@Composable
fun MineWalletRow(
    address: String?,
    busy: Boolean,
    onConnect: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Connect Solana wallets", fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary)
            Text(
                address?.let {
                    "Connected · ${it.take(4)}…${it.takeLast(4)}"
                } ?: "Not connected",
                color = EdgeColors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (address ==
                null
            ) {
                Text(
                    "Mobile Wallet Adapter · no wallet session",
                    color = EdgeColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (address ==
            null
        ) {
            Box(
                Modifier
                    .heightIn(
                        min = 48.dp,
                    ).border(1.dp, EdgeColors.border, RoundedCornerShape(50))
                    .background(EdgeColors.surface, RoundedCornerShape(50))
                    .clickable(
                        enabled = !busy,
                        role = Role.Button,
                        onClick = onConnect,
                    ).testTag("mine-connect")
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(if (busy) "Waiting…" else "Connect", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** SOL balance and ORE cards. Nothing read is shown as unread, never as zero. */
@Composable
fun MineBalanceCards(
    connected: Boolean,
    balance: RpcObservation?,
) {
    val fresh = balance as? RpcObservation.Fresh
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallCard(
            "SOL",
            fresh?.let { sol(it.lamports) + " SOL" } ?: "Balance unavailable",
            when {
                fresh != null -> "Devnet · slot ${fresh.slot}"
                connected -> "Devnet · not read yet"
                else -> "Devnet · not read"
            },
            Modifier.weight(1f),
            big = true,
            tag = "mine-sol",
        )
        SmallCard("ORE", "Not observed", "ORE token balance is not read in this build", Modifier.weight(1f), tag = "mine-ore")
    }
}

@Composable
private fun SmallCard(
    label: String,
    value: String,
    note: String,
    modifier: Modifier,
    big: Boolean = false,
    tag: String,
) {
    Column(
        modifier
            .background(
                EdgeColors.surface,
                cardShape,
            ).border(1.dp, EdgeColors.border, cardShape)
            .padding(12.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = EdgeColors.textPrimary,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (big) 18.sp else 15.sp,
        )
        Text(note, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/** "Edge node": the rock-and-pickaxe art (animated only while the session is ACTIVE) with the session controls. */
@Composable
fun EdgeNodeCard(
    view: SessionView,
    edgeModeResumed: Boolean,
    headline: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleEdgeMode: () -> Unit,
) {
    Column(Modifier.mineCard().testTag("edge-node"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Edge node",
                style = MaterialTheme.typography.titleLarge,
                color = EdgeColors.textPrimary,
                modifier =
                    Modifier
                        .weight(
                            1f,
                        ).semantics {
                            heading()
                        },
            )
            val active = view.phase == SessionPhase.ACTIVE
            Text(
                if (active) "Running" else "Paused",
                color = EdgeColors.background,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier =
                    Modifier
                        .background(
                            if (active) EdgeColors.mint else EdgeColors.copper,
                            RoundedCornerShape(50),
                        ).padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("edge-node-badge"),
            )
        }
        MiningSessionPanel(view, edgeModeResumed, headline, onStart, onStop, onToggleEdgeMode)
    }
}

/** CPU and Memory: what Android reports about this phone. Usage is not sampled, so no live bar is drawn. */
@Composable
fun DeviceCards(
    cores: Int?,
    memoryGbClass: Int?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallCard("CPU", cores?.let { "$it cores" } ?: "Not read", "Usage not sampled", Modifier.weight(1f), big = true, tag = "mine-cpu")
        SmallCard(
            "Memory",
            memoryGbClass?.let {
                "$it GB class"
            } ?: "Not read",
            "Usage not sampled",
            Modifier.weight(1f),
            big = true,
            tag = "mine-mem",
        )
    }
}

/** Why Deploy is off, or null when it may be pressed. Order: cluster, wallet, then the amount limits. */
fun deployBlocker(
    ui: OreDeployUi,
    walletConnected: Boolean,
    balanceLamports: Long?,
): String? {
    val net = if (ui.mainnet) "Mainnet" else "Devnet"
    val cluster = OreDeployNetwork.cluster(ui.mainnet)
    // On mainnet the deploy limit uses the mainnet balance, never the devnet one.
    val bal = if (ui.mainnet) ui.mainnetBalanceLamports else balanceLamports
    return when (val a = ui.availability) {
        OreDeployAvailability.NotChecked -> "$net not checked for ORE v3 yet."
        OreDeployAvailability.Checking -> "Checking ${net.lowercase()} for ORE v3…"
        is OreDeployAvailability.Unavailable -> "Deploy off: ${a.reason} (${net.lowercase()}). EdgeORE does not fall back to another network."
        is OreDeployAvailability.Available ->
            when {
                !walletConnected -> WALLET_REQUIRED
                ui.mainnet && !ui.riskAcknowledged -> MAINNET_ACK_REQUIRED
                else -> OreDeployLimits.refusal(ui.totalLamports ?: 0, Integer.bitCount(ui.mask), bal, cluster)
            }
    }
}

const val MAINNET_ACK_REQUIRED = "Tick the mainnet risk confirmation to enable Deploy."
const val MAINNET_DISCLAIMER = "Mainnet: deploying risks real SOL, which may be lost entirely. Capped at 0.01 SOL per deploy. EdgeORE claims no income, return or production readiness."

/** Adds a chip amount, clamped to MAX (balance minus the fee reserve, at most the per-deploy cap) when MAX is known. */
fun addChip(
    ui: OreDeployUi,
    addLamports: Long,
    balanceLamports: Long?,
): OreDeployUi {
    val next = (ui.totalLamports ?: 0) + addLamports
    val cluster = OreDeployNetwork.cluster(ui.mainnet)
    val bal = if (ui.mainnet) ui.mainnetBalanceLamports else balanceLamports
    val max = OreDeployLimits.max(bal, cluster) ?: OreDeployLimits.cap(cluster)
    return ui.copy(totalLamports = next.coerceAtMost(max))
}

/** Tiles stepper: ALL selects 25, + adds the lowest unselected square, − removes the highest selected one. */
fun stepTiles(
    mask: Int,
    delta: Int,
): Int =
    when {
        delta > 0 -> (0 until 25).firstOrNull { mask and (1 shl it) == 0 }?.let { mask or (1 shl it) } ?: mask
        delta < 0 -> (24 downTo 0).firstOrNull { mask and (1 shl it) != 0 }?.let { mask and (1 shl it).inv() } ?: mask
        else -> mask
    }

/** Header stats from the live mainnet read only; "—" when not read. Nothing here is a reward figure. */
data class OreHeaderStats(
    val deployed: String,
    val motherlode: String,
    val time: String,
)

fun oreHeaderStats(
    s: com.edgeore.app.ore.OreSnapshot?,
    nowMs: Long,
): OreHeaderStats {
    if (s == null) return OreHeaderStats("—", "—", "—")
    val clock =
        com.edgeore.app.ore.OreClock
            .at(s.board, s.boardSlot, nowMs - s.observedAtMs, s.slotRate)
    val time =
        when (clock) {
            is com.edgeore.app.ore.RoundClock.Live ->
                clock.secondsLeft?.let {
                    com.edgeore.app.ore.OreClock
                        .mmss(it)
                }
                    ?: "${clock.slotsLeft} slots"
            com.edgeore.app.ore.RoundClock.WaitingForFirstDeploy -> "Waiting"
            is com.edgeore.app.ore.RoundClock.Ended -> "Ended"
        }
    return OreHeaderStats(
        com.edgeore.app.ore.OreFormat
            .sol(s.round.totalDeployedLamports),
        com.edgeore.app.ore.OreFormat
            .ore(s.treasury.motherlodeGrams),
        time,
    )
}

/** The ORE Mining card, laid out like the ORE web app: stats, Lite/Pro, amount, chips, steppers, Deploy. Deploy only opens the review. */
@Composable
fun OreMiningCard(
    ui: OreDeployUi,
    walletConnected: Boolean,
    balanceLamports: Long?,
    onEdit: ((OreDeployUi) -> OreDeployUi) -> Unit,
    onCheck: () -> Unit,
    onDeploy: () -> Unit,
    onNetwork: (mainnet: Boolean) -> Unit = {},
    snapshot: com.edgeore.app.ore.OreSnapshot? = null,
    nowMs: Long = System.currentTimeMillis(),
    syncError: String? = null,
) {
    val total = ui.totalLamports ?: 0

    // The live board is the mainnet ORE v3 board; on devnet nothing from it is shown.
    @Suppress("NAME_SHADOWING")
    val snapshot = snapshot.takeIf { ui.mainnet }
    val blocker = deployBlocker(ui, walletConnected, balanceLamports)
    val cluster = OreDeployNetwork.cluster(ui.mainnet)
    val cap = OreDeployLimits.cap(cluster)
    val stats = oreHeaderStats(snapshot, nowMs)
    val sync = boardSyncLabel(ui.mainnet, snapshot, syncError, nowMs)
    Column(
        Modifier.mineCard().testTag("ore-mining"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ORE Mining",
            style = MaterialTheme.typography.titleLarge,
            color = EdgeColors.textPrimary,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        heading()
                    },
        )
        Row(Modifier.fillMaxWidth().testTag("ore-stats"), verticalAlignment = Alignment.CenterVertically) {
            Stat("◎ " + stats.deployed, "DEPLOYED", EdgeColors.textPrimary, Modifier.weight(1f), "stat-deployed")
            Box(Modifier.width(1.dp).height(34.dp).background(EdgeColors.border))
            Stat("⬡ " + stats.motherlode, "MOTHERLODE", EdgeColors.mainnetWarning, Modifier.weight(1f), "stat-motherlode")
            Box(Modifier.width(1.dp).height(34.dp).background(EdgeColors.border))
            Stat(stats.time, "TIME", EdgeColors.textPrimary, Modifier.weight(1f), "stat-time")
        }
        Text(
            sync,
            color = EdgeColors.textMuted,
            fontSize = 11.sp,
        )
        if (OreDeployNetwork.MAINNET_ENABLED) {
            // The network follows the selector at the top of Home: one switch, no second toggle here.
            Text(
                if (ui.mainnet) "Network: Mainnet (from the selector at the top)" else "Network: Devnet (from the selector at the top)",
                color = if (ui.mainnet) EdgeColors.mainnetWarning else EdgeColors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("ore-network-switch"),
            )
            if (ui.mainnet) {
                Text(
                    MAINNET_DISCLAIMER,
                    color = EdgeColors.mainnetWarning,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("ore-mainnet-disclaimer"),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Checkbox) {
                            onEdit { it.copy(riskAcknowledged = !it.riskAcknowledged) }
                        }.testTag("ore-mainnet-ack"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(ui.riskAcknowledged, null)
                    Text(
                        "I understand SOL on squares that don't win can be lost.",
                        color = EdgeColors.textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Row(Modifier.border(1.dp, EdgeColors.border, RoundedCornerShape(50)).padding(3.dp)) {
            ToggleTab("Lite", !ui.pro) { onEdit { it.copy(pro = false) } }
            ToggleTab("Pro", ui.pro) { onEdit { it.copy(pro = true) } }
        }
        RoundBlocks(
            ui,
            snapshot,
            synced = snapshot != null && syncError == null,
        ) { i -> onEdit { it.copy(proMask = it.proMask xor (1 shl i)) } }
        Text(
            "Amount per round",
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            sol(total),
            color =
                if (total ==
                    0L
                ) {
                    EdgeColors.textMuted
                } else {
                    EdgeColors.textPrimary
                },
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("ore-amount"),
        )
        Text("◎ SOL", color = EdgeColors.mint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("+0.01") { onEdit { addChip(it, 10_000_000, balanceLamports) } }
            Chip("+0.1") { onEdit { addChip(it, 100_000_000, balanceLamports) } }
            Chip("+1") { onEdit { addChip(it, 1_000_000_000, balanceLamports) } }
            val maxBal = if (ui.mainnet) ui.mainnetBalanceLamports else balanceLamports
            Chip("MAX", enabled = maxBal != null) {
                onEdit { it.copy(totalLamports = OreDeployLimits.max(maxBal, OreDeployNetwork.cluster(it.mainnet))) }
            }
        }
        Text(
            "Capped at ${sol(cap)} SOL per deploy" + (if (ui.mainnet) "" else " (devnet)") +
                ". Buttons stop at the cap; MAX also keeps 0.01 SOL back for fees.",
            color = if (ui.mainnet) EdgeColors.mainnetWarning else EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("ore-cap-note"),
        )
        val squares = Integer.bitCount(ui.mask)
        if (ui.pro) {
            StepRow(
                "TILES",
                squares.toString(),
                enabled = true,
                tag = "tiles",
                onAll = { onEdit { it.copy(proMask = (1 shl com.edgeore.app.ore.OreV3.SQUARES) - 1) } },
                onMinus = { onEdit { it.copy(proMask = stepTiles(it.proMask, -1)) } },
                onPlus = { onEdit { it.copy(proMask = stepTiles(it.proMask, 1)) } },
            )
        }
        StepRow("ROUNDS", "1", enabled = false, tag = "rounds", note = "1 round per approval")
        Line(
            "Total deployment",
            "◎ " + sol(total),
            OreDeployLimits.perSquare(total, squares)?.let { "${sol(it)} SOL on each of $squares square${if (squares == 1) "" else "s"}" }
                ?: if (ui.pro && squares == 0) "Pick tiles on the grid or with the stepper" else "Add an amount",
        )
        PrimaryAction("Review deployment", Modifier.fillMaxWidth().testTag("ore-deploy"), enabled = blocker == null, onClick = onDeploy)
        Text(
            blocker ?: "Opens the exact-message review. You approve, your wallet signs, then you submit separately.",
            color =
                if (blocker !=
                    null
                ) {
                    EdgeColors.copper
                } else {
                    EdgeColors.textMuted
                },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("ore-deploy-hint"),
        )
        ui.message?.let { Text(it, color = EdgeColors.copper, style = MaterialTheme.typography.labelSmall) }
        val netLabel = if (ui.mainnet) "Mainnet" else "Devnet"
        val net =
            when (val a = ui.availability) {
                is OreDeployAvailability.Available -> "$netLabel: ORE v3 found · round ${a.roundId} at slot ${a.slot}"
                is OreDeployAvailability.Unavailable -> "$netLabel: ORE v3 not available"
                OreDeployAvailability.Checking -> "$netLabel: checking…"
                OreDeployAvailability.NotChecked -> "$netLabel: not checked"
            } + if (OreDeployNetwork.MAINNET_ENABLED) "" else " · mainnet deploy is disabled in this build"
        Text(
            net,
            color = if (ui.mainnet) EdgeColors.mainnetWarning else EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("ore-deploy-network"),
        )
        if (ui.availability !is OreDeployAvailability.Checking) {
            Box(
                Modifier
                    .heightIn(
                        min = 44.dp,
                    ).clickable(role = Role.Button, onClick = onCheck)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Check ${netLabel.lowercase()} again", color = EdgeColors.mint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Text(
            ORE_DEPLOY_NFA,
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("ore-nfa"),
        )
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier,
    tag: String,
) {
    Column(modifier.testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = EdgeColors.textMuted, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun StepRow(
    label: String,
    value: String,
    enabled: Boolean,
    tag: String,
    note: String? = null,
    onAll: (() -> Unit)? = null,
    onMinus: () -> Unit = {},
    onPlus: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().testTag("step-$tag")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            onAll?.let { StepButton("ALL", enabled, "$tag-all", it) }
            StepButton("−", enabled, "$tag-minus", onMinus)
            Text(
                value,
                color = if (enabled) EdgeColors.textPrimary else EdgeColors.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp).testTag("$tag-value"),
            )
            StepButton("+", enabled, "$tag-plus", onPlus)
        }
        note?.let { Text(it, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun StepButton(
    text: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .width(
                if (text ==
                    "ALL"
                ) {
                    52.dp
                } else {
                    40.dp
                },
            ).background(EdgeColors.surfaceInset, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                EdgeColors.border,
                RoundedCornerShape(8.dp),
            ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) EdgeColors.textPrimary else EdgeColors.textMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ToggleTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) EdgeColors.background else Color.Transparent, RoundedCornerShape(50))
            .then(if (selected) Modifier.border(1.dp, EdgeColors.textPrimary, RoundedCornerShape(50)) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
            }.testTag("ore-mode-$text")
            .padding(horizontal = 26.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (selected) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowScope.Chip(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(
                1f,
            ).heightIn(
                min = 44.dp,
            ).background(EdgeColors.surfaceInset, RoundedCornerShape(50))
            .border(1.dp, EdgeColors.border, RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("chip-$text"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) EdgeColors.textPrimary else EdgeColors.textMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun Line(
    label: String,
    value: String,
    note: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(value, color = EdgeColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text(note, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SquarePicker(
    mask: Int,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("ore-square-picker"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 5) {
                    val i = r * 5 + c
                    val on = mask and (1 shl i) != 0
                    Box(
                        Modifier
                            .weight(
                                1f,
                            ).aspectRatio(
                                1.6f,
                            ).background(if (on) EdgeColors.mint.copy(alpha = 0.25f) else EdgeColors.surfaceInset, RoundedCornerShape(6.dp))
                            .border(1.dp, if (on) EdgeColors.mint else EdgeColors.border, RoundedCornerShape(6.dp))
                            .clickable(role = Role.Checkbox) { onChange(mask xor (1 shl i)) }
                            .semantics {
                                contentDescription =
                                    "Square ${i + 1}" + if (on) ", selected" else ""
                            }.testTag("pick-$i"),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = if (on) EdgeColors.textPrimary else EdgeColors.textMuted, fontSize = 11.sp) }
                }
            }
        }
    }
}

/** CONTRIBUTION toggles. Each states its real effect (from settings/ControlEffects.kt), not a guess. */
@Composable
fun ContributionToggles(
    chargeOnly: Boolean,
    thermalGuard: Boolean,
    batteryReservePercent: Int,
    effect: (com.edgeore.app.settings.Control) -> String,
    onChargeOnly: (Boolean) -> Unit,
    onThermal: (Boolean) -> Unit,
) {
    Column(Modifier.mineCard().testTag("contribution-toggles"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Device preferences", style = MaterialTheme.typography.titleMedium, color = EdgeColors.textPrimary)
        Text(
            "These gate Edge Mode and the Mine session on this phone. They do not cap CPU, memory, storage or network.",
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        ToggleRow("Charge-only", effect(com.edgeore.app.settings.Control.CHARGE_ONLY), chargeOnly, onChargeOnly)
        ToggleRow("Thermal guard", effect(com.edgeore.app.settings.Control.THERMAL_GUARD), thermalGuard, onThermal)
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Battery reserve", color = EdgeColors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                effect(com.edgeore.app.settings.Control.BATTERY_RESERVE),
                color = EdgeColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text("$batteryReservePercent%", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    effect: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = EdgeColors.textPrimary, modifier = Modifier.weight(1f))
        Text(effect, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked,
            onChange,
            colors =
                androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = EdgeColors.mint,
                    checkedThumbColor = EdgeColors.background,
                ),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** "Round blocks": 01-25, mint check badge on selected squares, live per-square SOL only when the mainnet round was read. Pro taps toggle the draft. */
@Composable
fun RoundBlocks(
    ui: OreDeployUi,
    snapshot: com.edgeore.app.ore.OreSnapshot?,
    synced: Boolean = snapshot != null,
    onToggle: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("round-blocks"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Round blocks", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (ui.pro) "Select blocks" else "Lite: all 25", color = EdgeColors.textMuted, fontSize = 12.sp)
        }
        if (snapshot != null) {
            Text(
                "Each block: miners (top left), SOL deployed (bottom)",
                color = EdgeColors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().testTag("grid-legend"),
            )
        }
        for (r in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until 5) {
                    val i = r * 5 + c
                    val on = ui.mask and (1 shl i) != 0
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.25f)
                            .background(
                                if (on &&
                                    ui.pro
                                ) {
                                    EdgeColors.mint.copy(alpha = 0.16f)
                                } else {
                                    EdgeColors.surfaceInset
                                },
                                shape,
                            ).border(1.dp, if (on && ui.pro) EdgeColors.mint else EdgeColors.border, shape)
                            .then(if (ui.pro) Modifier.clickable(role = Role.Checkbox) { onToggle(i) } else Modifier)
                            .semantics {
                                contentDescription =
                                    "Block ${i + 1}" +
                                    (
                                        snapshot?.let { s ->
                                            ", ${com.edgeore.app.ore.OreFormat.sol(
                                                s.round.deployedLamports[i],
                                            )} SOL, ${s.round.count[i]} miners"
                                        } ?: ""
                                    ) +
                                    if (on) ", selected" else ""
                            }.testTag("grid-$i"),
                    ) {
                        Text(
                            "%02d".format(i + 1),
                            color =
                                if (on &&
                                    ui.pro
                                ) {
                                    EdgeColors.textPrimary
                                } else {
                                    EdgeColors.textMuted
                                },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        snapshot?.let { s ->
                            Text(
                                "◎" +
                                    com.edgeore.app.ore.OreFormat
                                        .sol(s.round.deployedLamports[i]),
                                color = EdgeColors.textMuted,
                                fontSize = 9.sp,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp)
                                        .testTag("sol-$i"),
                            )
                            Text(
                                "${s.round.count[i]}",
                                color = EdgeColors.textMuted,
                                fontSize = 9.sp,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 4.dp, top = 2.dp)
                                        .testTag("miners-$i"),
                            )
                        }
                        if (on &&
                            ui.pro
                        ) {
                            Box(
                                Modifier
                                    .align(
                                        Alignment.TopEnd,
                                    ).padding(
                                        3.dp,
                                    ).size(
                                        14.dp,
                                    ).background(EdgeColors.mint, androidx.compose.foundation.shape.CircleShape)
                                    .testTag("check-$i"),
                                contentAlignment = Alignment.Center,
                            ) { Text("✓", color = EdgeColors.background, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        val n = Integer.bitCount(ui.mask)
        Text(
            "$n selected" +
                if (snapshot ==
                    null
                ) {
                    " · live per-block SOL not read"
                } else {
                    " · round #${snapshot.round.id}" + if (synced) "" else " · Not synced (last good read)"
                },
            color = EdgeColors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One line under the ORE stats: which board this is and whether the last read worked. Devnet never shows the mainnet
 * board. A failed read says "Not synced"; an older good read stays visible, labelled with its age.
 */
fun boardSyncLabel(
    mainnet: Boolean,
    snapshot: com.edgeore.app.ore.OreSnapshot?,
    syncError: String?,
    nowMs: Long,
): String {
    val head =
        when {
            !mainnet -> "ORE v3 is not deployed on devnet. The live board is mainnet only."
            snapshot == null && syncError != null -> "Not synced. $syncError"
            snapshot == null -> "Live round not read yet (mainnet board)."
            else -> null
        }
    if (head != null || snapshot == null) return head.orEmpty()
    val clock =
        com.edgeore.app.ore.OreClock
            .at(snapshot.board, snapshot.boardSlot, nowMs - snapshot.observedAtMs, snapshot.slotRate)
    val left =
        when (clock) {
            com.edgeore.app.ore.RoundClock.WaitingForFirstDeploy -> "Waiting for first deploy"
            is com.edgeore.app.ore.RoundClock.Live ->
                "${clock.slotsLeft} slots left" +
                    clock.secondsLeft?.let { " (~${com.edgeore.app.ore.OreClock.mmss(it)})" }.orEmpty()
            is com.edgeore.app.ore.RoundClock.Ended -> "Ended · awaiting reset"
        }
    val base = "Mainnet round #${snapshot.round.id} · $left. Pool values, not your earnings."
    val ageS = ((nowMs - snapshot.observedAtMs) / MS_PER_S).coerceAtLeast(0)
    return if (syncError == null) base else "Not synced. Showing the read from ${ageS}s ago. $base"
}

private const val MS_PER_S = 1000L
