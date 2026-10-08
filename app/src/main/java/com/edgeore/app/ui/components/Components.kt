package com.edgeore.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeore.app.ui.theme.EdgeColors

val CardShape = RoundedCornerShape(16.dp)

/** Three-bar E mark drawn natively (no raster artwork). */
@Composable
fun EMark(size: Dp = 32.dp) {
    Canvas(Modifier.size(size).semantics { contentDescription = "EdgeORE mark" }) {
        val w = this.size.width
        val h = this.size.height
        val barH = h * 0.2f
        val gap = (h - 3 * barH) / 2f
        val skew = w * 0.12f
        listOf(1f, 0.72f, 1f).forEachIndexed { i, len ->
            val top = i * (barH + gap)
            val p = Path().apply {
                moveTo(skew, top); lineTo(w * len, top); lineTo(w * len - skew, top + barH); lineTo(0f, top + barH); close()
            }
            drawPath(p, EdgeColors.markGradient)
        }
    }
}

@Composable
fun EdgeOreHeader(onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        EMark(30.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = EdgeColors.textPrimary)) { append("Edge") }
                withStyle(SpanStyle(color = EdgeColors.mint)) { append("ORE") }
            },
            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
        )
        if (onSettings != null) {
            Box(Modifier.size(48.dp).clip(CardShape).clickable(role = Role.Button, onClickLabel = "Settings", onClick = onSettings), contentAlignment = Alignment.Center) {
                Icon(EdgeIcons.Info, contentDescription = "About this build", tint = EdgeColors.textMuted)
            }
        }
    }
}

/** "CONCEPT · SAMPLE DATA" label for any illustrative content. */
@Composable
fun ConceptBadge(text: String = "CONCEPT · SAMPLE DATA") {
    Text(
        text, color = EdgeColors.copper, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.border(1.dp, EdgeColors.copper, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

enum class Capability(val label: String, val color: Color, val icon: ImageVector) {
    IMPLEMENTED("Implemented", EdgeColors.mint, EdgeIcons.Check),
    AVAILABLE("Available", EdgeColors.mint, EdgeIcons.Check),
    UNAVAILABLE("Unavailable", EdgeColors.danger, EdgeIcons.Cross),
    PAUSED("Paused", EdgeColors.copper, EdgeIcons.Pause),
    STALE("Stale", EdgeColors.copper, EdgeIcons.Clock),
    DEMO("Demo", EdgeColors.copper, EdgeIcons.Info),
    NOT_OBSERVED("Not observed", EdgeColors.textMuted, EdgeIcons.Info),
}

/** Status always uses text + icon, never color alone. */
@Composable
fun CapabilityBadge(cap: Capability, text: String = cap.label) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(cap.icon, contentDescription = null, tint = cap.color, modifier = Modifier.size(16.dp))
        Text(text, color = cap.color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EdgeCard(modifier: Modifier = Modifier, inset: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var m = modifier.fillMaxWidth().clip(CardShape).background(if (inset) EdgeColors.surfaceInset else EdgeColors.surface)
        .border(BorderStroke(1.dp, EdgeColors.border), CardShape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = EdgeColors.textPrimary)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
    }
}

/** Value, source and freshness. A missing value is shown as text, never as zero. */
@Composable
fun ObservationCard(modifier: Modifier, icon: ImageVector, title: String, value: String?, missing: String, source: String? = null) {
    EdgeCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = EdgeColors.mint, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textPrimary)
        }
        Text(value ?: missing, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (value == null) EdgeColors.copper else EdgeColors.textPrimary)
        if (source != null) Text(source, style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
    }
}

@Composable
fun PrimaryAction(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val active = enabled && !loading
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CardShape)
            .background(if (active) EdgeColors.copperGradient else androidx.compose.ui.graphics.SolidColor(EdgeColors.border))
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = EdgeColors.onAction, strokeWidth = 2.dp)
        else if (icon != null) Icon(icon, contentDescription = null, tint = if (active) EdgeColors.onAction else EdgeColors.textMuted)
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (active) EdgeColors.onAction else EdgeColors.textMuted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (!enabled) EdgeColors.border else if (danger) EdgeColors.danger else EdgeColors.mint
    Box(
        modifier.heightIn(min = 48.dp).clip(CardShape).border(1.dp, c, CardShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) c else EdgeColors.textMuted, fontWeight = FontWeight.SemiBold) }
}

/** A row that opens a scoped operation. */
@Composable
fun OperationRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CardShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) EdgeColors.copper else EdgeColors.border)
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) EdgeColors.textPrimary else EdgeColors.textMuted, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(EdgeIcons.Chevron, contentDescription = null, tint = EdgeColors.textMuted)
    }
}

@Composable
fun ResourceControl(icon: ImageVector, label: String, explanation: String, checked: Boolean, enforcement: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = EdgeColors.mint)
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary)
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
            Text(enforcement, style = MaterialTheme.typography.labelSmall, color = EdgeColors.copper)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = EdgeColors.mint, checkedThumbColor = EdgeColors.onAction),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
fun KeyValue(key: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(key, style = MaterialTheme.typography.labelSmall, color = EdgeColors.textMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textPrimary, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
    }
}

@Composable
fun Notice(text: String, error: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (error) EdgeIcons.Cross else EdgeIcons.Info, contentDescription = if (error) "Error" else "Note", tint = if (error) EdgeColors.danger else EdgeColors.textMuted, modifier = Modifier.size(18.dp))
        Text(text, color = if (error) EdgeColors.danger else EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Simple line chart; used only for clearly labelled concept previews or measured series. */
@Composable
fun LineChart(series: List<Pair<List<Float>, Color>>, modifier: Modifier) {
    Canvas(modifier) {
        val maxV = series.flatMap { it.first }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        series.forEach { (pts, color) ->
            if (pts.size < 2) return@forEach
            val stepX = size.width / (pts.size - 1)
            for (i in 1 until pts.size) {
                drawLine(color, Offset((i - 1) * stepX, size.height * (1 - pts[i - 1] / maxV)), Offset(i * stepX, size.height * (1 - pts[i] / maxV)), strokeWidth = 4f)
            }
        }
    }
}

@Composable
fun RowScope.Tile(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.weight(1f), content = content) }
