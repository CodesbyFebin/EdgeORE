package com.edgeore.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.edgeore.app.ui.theme.EdgeColors

/*
 * Chart geometry and an area line chart.
 *
 * Domain, plotPoints, monotonePath (Fritsch-Carlson monotone cubic) and areaPath are adapted from OptimAI Agentic for
 * Android, app/src/main/java/com/test/agenttrade/ui/components/Charts.kt (MIT License, Copyright (c) 2026 OptimAI
 * Agentic contributors; see NOTICE). Changes for EdgeORE: a count domain that starts at zero, no draw-in animation
 * (design.md §12 limits motion), a required accessible summary, and callers must pass measured local data only
 * (design.md §4: "Never create attractive sample trends"). With fewer than two points nothing is drawn.
 */

/** A value range with lo < hi guaranteed. */
data class Domain(val lo: Double, val hi: Double) {
    val span: Double get() = hi - lo

    companion object {
        /** min/max padded by pad × range, or ±1 around a flat series. */
        fun padded(values: List<Double>, pad: Double): Domain {
            val lo = values.minOrNull()
            val hi = values.maxOrNull()
            if (lo == null || hi == null || lo >= hi) {
                val v = values.firstOrNull() ?: 0.0
                return Domain(v - 1, v + 1)
            }
            val p = (hi - lo) * pad
            return Domain(lo - p, hi + p)
        }

        /** For counts: 0 to max (at least 1), so a flat zero series sits on the floor instead of mid-height. */
        fun counts(values: List<Double>): Domain = Domain(0.0, (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0))
    }
}

/** Evenly spaced x, value → y inside rect against domain. */
fun plotPoints(values: List<Double>, rect: Rect, domain: Domain): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val step = if (values.size > 1) rect.width / (values.size - 1) else 0f
    return values.mapIndexed { i, v ->
        val norm = ((v - domain.lo) / domain.span).toFloat()
        Offset(rect.left + i * step, rect.bottom - norm * rect.height)
    }
}

/** Tangents of the monotone cubic through points; exposed for tests (no overshoot past local extremes). */
fun monotoneTangents(points: List<Offset>): FloatArray {
    val n = points.size
    if (n < 2) return FloatArray(n)
    val dx = FloatArray(n - 1) { points[it + 1].x - points[it].x }
    val slope = FloatArray(n - 1) { if (dx[it] == 0f) 0f else (points[it + 1].y - points[it].y) / dx[it] }
    val tangent = FloatArray(n)
    tangent[0] = slope[0]
    tangent[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) tangent[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
    for (i in 0 until n - 1) {
        if (slope[i] == 0f) { tangent[i] = 0f; tangent[i + 1] = 0f; continue }
        val a = tangent[i] / slope[i]
        val b = tangent[i + 1] / slope[i]
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            tangent[i] = t * a * slope[i]
            tangent[i + 1] = t * b * slope[i]
        }
    }
    return tangent
}

/** A monotone cubic through points: smooth, and never overshoots past a local min/max. */
fun monotonePath(points: List<Offset>, path: Path = Path()): Path {
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) { path.lineTo(points[1].x, points[1].y); return path }
    val tangent = monotoneTangents(points)
    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val h = (p1.x - p0.x) / 3f
        path.cubicTo(p0.x + h, p0.y + tangent[i] * h, p1.x - h, p1.y - tangent[i + 1] * h, p1.x, p1.y)
    }
    return path
}

/** Closes a line path down to floorY, for an area fill under it. */
fun areaPath(line: Path, points: List<Offset>, floorY: Float): Path = Path().apply {
    addPath(line)
    lineTo(points.last().x, floorY)
    lineTo(points.first().x, floorY)
    close()
}

/**
 * Area + monotone line for a series of counts. [summary] is read by screen readers instead of the drawing.
 */
@Composable
fun CountAreaChart(values: List<Double>, summary: String, modifier: Modifier = Modifier, color: Color = EdgeColors.mint) {
    Canvas(modifier.semantics { contentDescription = summary }) {
        if (values.size < 2) return@Canvas
        val inset = 2.dp.toPx()
        val rect = Rect(0f, inset, size.width, size.height - inset)
        drawLine(EdgeColors.border, Offset(0f, rect.bottom), Offset(size.width, rect.bottom), 1.dp.toPx())
        val pts = plotPoints(values, rect, Domain.counts(values))
        val line = monotonePath(pts)
        drawPath(areaPath(line, pts, rect.bottom), Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.5.dp.toPx(), pts.last())
    }
}
