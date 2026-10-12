package com.edgeore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeore.app.ore.OreClock
import com.edgeore.app.ore.OreFormat
import com.edgeore.app.ore.OreState
import com.edgeore.app.ui.theme.EdgeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A price with where and when it came from. EdgeORE configures no price source, so the app passes none and shows "—". */
data class PriceQuote(val symbol: String, val usd: java.math.BigDecimal, val source: String, val observedAtMs: Long)

/** How fresh the home strip's ORE read is. Older than [STALE_AFTER_MS] is labelled stale, never shown as live. */
enum class OreFreshness { NOT_READ, LOADING, LIVE, STALE, ERROR }

object OreHome {
    const val STALE_AFTER_MS = 60_000L
    const val NO_PRICE = "—"

    fun freshness(ore: OreState, nowMs: Long): OreFreshness {
        val s = ore.snapshot
        return when {
            s == null && ore.loading -> OreFreshness.LOADING
            s == null && ore.error != null -> OreFreshness.ERROR
            s == null -> OreFreshness.NOT_READ
            ore.error != null -> OreFreshness.ERROR
            nowMs - s.observedAtMs > STALE_AFTER_MS -> OreFreshness.STALE
            else -> OreFreshness.LIVE
        }
    }

    fun price(symbol: String, prices: List<PriceQuote>): String =
        prices.firstOrNull { it.symbol == symbol }?.let { "$" + it.usd.setScale(2, java.math.RoundingMode.DOWN).toPlainString() } ?: NO_PRICE
}

private fun hms(ms: Long): String = DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))

/**
 * Mine home ORE strip: prices (only with a source and time, otherwise "—"), the round's deployed SOL, the motherlode
 * and the round clock from the read-only ORE observer, and Lite / Pro shortcuts into the ORE board. No acting controls.
 */
@Composable
fun OreHomeStrip(ore: OreState, nowMs: Long, onOpen: (pro: Boolean) -> Unit, prices: List<PriceQuote> = emptyList()) {
    val s = ore.snapshot
    val fresh = OreHome.freshness(ore, nowMs)
    Column(Modifier.fillMaxWidth().background(EdgeColors.surface, RoundedCornerShape(16.dp)).border(1.dp, EdgeColors.border, RoundedCornerShape(16.dp))
        .padding(14.dp).testTag("ore-home"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PriceChip("ORE", OreHome.price("ORE", prices))
                PriceChip("SOL", OreHome.price("SOL", prices))
            }
            Text("Read-only", color = EdgeColors.mint, style = MaterialTheme.typography.labelSmall)
        }
        val clock = s?.let { OreClock.at(it.board, it.roundSlot, nowMs - it.observedAtMs, it.slotRate) }
        Row(Modifier.fillMaxWidth().background(EdgeColors.surfaceInset, RoundedCornerShape(12.dp)).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            HomeStat("DEPLOYED SOL", s?.let { OreFormat.sol(it.round.totalDeployedLamports) }, EdgeColors.textPrimary)
            HomeStat("MOTHERLODE", s?.let { OreFormat.ore(it.treasury.motherlodeGrams) + " ORE" }, EdgeColors.copper)
            HomeStat("TIME", clock?.let(OreClock::label), EdgeColors.textPrimary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ModeButton("Lite") { onOpen(false) }
            ModeButton("Pro") { onOpen(true) }
        }
        val status = when (fresh) {
            OreFreshness.NOT_READ -> "Not read yet"
            OreFreshness.LOADING -> "Reading the ORE v3 board…"
            OreFreshness.LIVE -> "Round ${s!!.round.id} · read ${hms(s.observedAtMs)} at slot ${s.roundSlot}"
            OreFreshness.STALE -> "Stale · last read ${hms(s!!.observedAtMs)}, ${(nowMs - s.observedAtMs) / 1000} s ago"
            OreFreshness.ERROR -> "Read failed: ${ore.error}" + (s?.let { " · showing the read from ${hms(it.observedAtMs)}" } ?: "")
        }
        Text(status, color = if (fresh == OreFreshness.LIVE) EdgeColors.textMuted else EdgeColors.copper, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("ore-home-status"))
        Text("Mainnet observation of the ORE v3 program. Prices show \"—\" because no price source is configured. Nothing here is earned or deployable.",
            color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PriceChip(symbol: String, value: String) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$symbol price: " + if (value == OreHome.NO_PRICE) "not available" else value },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = if (symbol == "ORE") EdgeColors.copper else EdgeColors.mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(value, color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.testTag("price-$symbol"))
    }
}

@Composable
private fun RowScope.HomeStat(label: String, value: String?, color: Color) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: "—", color = if (value == null) EdgeColors.textMuted else color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ModeButton(text: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp).border(1.dp, EdgeColors.border, RoundedCornerShape(50))
        .clickable(role = Role.Button, onClick = onClick).testTag("ore-home-$text")
        .semantics { contentDescription = "Open the ORE board in $text view, read-only" }.padding(horizontal = 22.dp, vertical = 12.dp)) {
        Text(text, color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}
