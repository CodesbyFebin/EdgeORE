package com.edgeore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeRadius
import com.edgeore.app.ui.theme.EdgeSpacing
import com.edgeore.app.ui.theme.EdgeType

/*
 * Small shared pieces: status pill, count badge, icon tile, stat cell, divider and the "fact card" layout
 * (icon tile + title/subtitle + status pill header, a content block, a muted footer line).
 *
 * Adapted from OptimAI Agentic for Android (MIT License, Copyright (c) 2026 OptimAI Agentic contributors; see NOTICE):
 * DSBadge, CountBadge, DSIconTile and DSDivider from ui/components/Components.kt, StatCell from ui/agent/AgentChatScreen.kt
 * and the header/content/footer layout of TechnicalCard from ui/agent/TechnicalCards.kt. Changes for EdgeORE: EdgeORE
 * colours, text is never below 12 sp, a pill always carries text (and optionally an icon) so status never relies on
 * colour alone, and the content is whatever real state the caller passes; nothing here has sample values.
 */

enum class PillTone(internal val fg: Color, internal val bg: Color) {
    NEUTRAL(EdgeColors.textMuted, EdgeColors.surfaceInset),
    MINT(EdgeColors.mint, EdgeColors.mint.copy(alpha = 0.12f)),
    COPPER(EdgeColors.copper, EdgeColors.copper.copy(alpha = 0.12f)),
    DANGER(EdgeColors.danger, EdgeColors.danger.copy(alpha = 0.12f)),
}

/** A rounded status label. Always text; the tone only reinforces it. */
@Composable
fun StatusPill(text: String, tone: PillTone = PillTone.NEUTRAL, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.background(tone.bg, RoundedCornerShape(EdgeRadius.pill)).border(1.dp, tone.fg.copy(alpha = 0.35f), RoundedCornerShape(EdgeRadius.pill))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(14.dp))
        Text(text, color = tone.fg, style = EdgeType.caption, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small count for a tab or filter. Hidden by the caller when the count is zero. */
@Composable
fun CountBadge(count: Int, tone: PillTone = PillTone.COPPER, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).background(tone.fg, RoundedCornerShape(EdgeRadius.pill)).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (count > 99) "99+" else "$count", color = EdgeColors.onAction, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold) }
}

/** Icon inside an inset tile, used as the leading element of card headers and rows. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = EdgeColors.mint, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).background(EdgeColors.surfaceInset, RoundedCornerShape(EdgeRadius.tile)).border(1.dp, EdgeColors.border, RoundedCornerShape(EdgeRadius.tile)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
fun EdgeDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(EdgeColors.border))
}

/**
 * Label over value. A missing value is passed as null and shown as [missing] in copper, never as zero.
 */
@Composable
fun StatCell(label: String, value: String?, modifier: Modifier = Modifier, missing: String = "Not observed", mono: Boolean = false) {
    Column(
        modifier.background(EdgeColors.surfaceInset, RoundedCornerShape(EdgeRadius.tile)).padding(horizontal = EdgeSpacing.md, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: ${value ?: missing}" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = EdgeType.caption, color = EdgeColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value ?: missing,
            style = if (mono && value != null) EdgeType.mono(14, FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, color = if (value == null) EdgeColors.copper else EdgeColors.textPrimary,
        )
    }
}

/**
 * Card with a fixed reading order: header (icon tile, title, subtitle, optional status pill), content, footer.
 * The footer states the source or the limit of what the card shows.
 */
@Composable
fun FactCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    iconTint: Color = EdgeColors.mint,
    status: (@Composable () -> Unit)? = null,
    footer: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    EdgeCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EdgeSpacing.md)) {
            IconTile(icon, iconTint)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = EdgeColors.textPrimary)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
            }
            status?.invoke()
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(EdgeSpacing.sm), content = content)
        if (footer != null) Text(footer, style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
    }
}

/** The compact environment line under the header (design.md §3): no global "secure" seal. */
@Composable
fun EnvironmentStrip(text: String = "Devnet · Review candidate") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EdgeSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        StatusPill(text, PillTone.COPPER, EdgeIcons.Info)
        StatusPill("Test SOL only", PillTone.NEUTRAL)
    }
}
