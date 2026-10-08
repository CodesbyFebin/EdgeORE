package com.edgeore.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Color tokens from the EdgeORE native design specification v1.0. */
object EdgeColors {
    val background = Color(0xFF101414)
    val surface = Color(0xFF1C2323)
    val surfaceInset = Color(0xFF121919)
    val border = Color(0xFF3D4745)
    val textPrimary = Color(0xFFEEF2EF)
    val textMuted = Color(0xFFA6B2AC)
    val mint = Color(0xFF35E7C0)
    val copper = Color(0xFFFFA365)
    val copperDeep = Color(0xFFE86D35)
    val danger = Color(0xFFFF8275)
    val onAction = Color(0xFF111514)
    val copperGradient = Brush.horizontalGradient(listOf(copper, Color(0xFFF58A4E)))
    val markGradient = Brush.linearGradient(listOf(mint, Color(0xFF1FB59A)))
}

private val scheme = darkColorScheme(
    primary = EdgeColors.copper,
    onPrimary = EdgeColors.onAction,
    secondary = EdgeColors.mint,
    onSecondary = EdgeColors.onAction,
    background = EdgeColors.background,
    onBackground = EdgeColors.textPrimary,
    surface = EdgeColors.surface,
    onSurface = EdgeColors.textPrimary,
    surfaceVariant = EdgeColors.surfaceInset,
    onSurfaceVariant = EdgeColors.textMuted,
    outline = EdgeColors.border,
    error = EdgeColors.danger,
    onError = EdgeColors.onAction,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun EdgeOreTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
