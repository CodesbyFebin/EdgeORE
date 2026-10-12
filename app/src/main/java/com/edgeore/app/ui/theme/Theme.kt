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
import androidx.compose.ui.unit.dp

/** Color tokens from the EdgeORE native design specification v1.0. */
object EdgeColors {
    val background = Color(0xFF060D0C)
    val surface = Color(0xFF0D1917)
    val surfaceInset = Color(0xFF142421)
    val border = Color(0xFF2C4740)
    val textPrimary = Color(0xFFF2F7F5)
    val textMuted = Color(0xFFABBDB7)
    val mint = Color(0xFF19E6BC)
    val copper = Color(0xFFFF963F)
    val copperDeep = Color(0xFFE86D35)
    val danger = Color(0xFFFF6B79)
    val onAction = Color(0xFF111514)
    val mintSoft = Color(0xFF8BF4DF)
    val warning = Color(0xFFFFB65C)
    val success = Color(0xFF43D99B)
    val disabledSurface = Color(0xFF182421)
    val disabledText = Color(0xFFABBDB7)
    val surfacePressed = Color(0xFF1C3530)
    val ambient = Brush.verticalGradient(listOf(Color(0xFF0A211B), background, background))
    val actionGradient = Brush.horizontalGradient(listOf(mintSoft, mint))
    val cardGradient = Brush.linearGradient(listOf(surfaceInset, surface, background))
    val copperGradient = Brush.horizontalGradient(listOf(copper, Color(0xFFF58A4E)))
    val markGradient = Brush.linearGradient(listOf(mint, copper))
}

private val scheme = darkColorScheme(
    primary = EdgeColors.mint,
    onPrimary = EdgeColors.onAction,
    secondary = EdgeColors.mint,
    onSecondary = EdgeColors.onAction,
    background = EdgeColors.background,
    onBackground = EdgeColors.textPrimary,
    surface = EdgeColors.surface,
    onSurface = EdgeColors.textPrimary,
    surfaceVariant = EdgeColors.surfaceInset,
    surfaceContainer = EdgeColors.surface,
    surfaceContainerHigh = EdgeColors.surfaceInset,
    surfaceContainerHighest = EdgeColors.surfacePressed,
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
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = androidx.compose.material3.Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)), content = content)
}
