package com.edgeore.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.edgeore.app.ui.theme.EdgeColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular mint holographic frame (spec "Edge Node": 160–200dp ring). Decorative only: it draws the same whether or
 * not anything runs, and hides nothing from accessibility (the content inside carries the semantics).
 */
@Composable
fun HoloRing(
    size: Dp,
    modifier: Modifier = Modifier,
    bright: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            drawCircle(
                Brush.radialGradient(listOf(EdgeColors.holoGlow.copy(alpha = if (bright) 0.28f else 0.16f), Color.Transparent), c, r),
                r,
                c,
            )
            drawCircle(
                Brush.sweepGradient(
                    listOf(EdgeColors.mint, EdgeColors.mintSoft.copy(alpha = 0.4f), EdgeColors.mint.copy(alpha = 0.25f), EdgeColors.mint),
                    c,
                ),
                r * 0.94f,
                c,
                style = Stroke(2.dp.toPx()),
            )
            drawCircle(EdgeColors.holoBorder, r * 0.84f, c, style = Stroke(1.dp.toPx()))
            for (deg in listOf(-90f, 25f, 160f, 205f)) {
                val a = Math.toRadians(deg.toDouble())
                drawCircle(EdgeColors.mintSoft, 3.dp.toPx(), Offset(c.x + r * 0.94f * cos(a).toFloat(), c.y + r * 0.94f * sin(a).toFloat()))
            }
        }
        Box(Modifier.padding(size * 0.1f).clip(CircleShape), contentAlignment = Alignment.Center, content = content)
    }
}
