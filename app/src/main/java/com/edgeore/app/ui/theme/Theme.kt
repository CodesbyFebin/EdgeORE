package com.edgeore.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mint holographic tokens (EdgeORE Web3 design spec v2, "Colour tokens"). Older names are kept as aliases so every
 * screen picks up the new palette from this one source.
 */
object EdgeColors {
    val background = Color(0xFF060D0C)
    val surface = Color(0xFF0D1917)
    val surfaceRaised = Color(0xFF142421)
    val surfacePressed = Color(0xFF1C3530)
    val surfaceInset = Color(0xFF09130F)
    val mint = Color(0xFF19E6BC)
    val mintSoft = Color(0xFF8BF4DF)
    val copper = Color(0xFFFF963F)          // spec "orange"
    val orange = copper
    val copperDeep = Color(0xFFE8792A)
    /** Mainnet warning copper (#FFA365): used only where real SOL is at stake (ORE mainnet deploy). */
    val mainnetWarning = Color(0xFFFFA365)
    val textPrimary = Color(0xFFF2F7F5)
    /** Supporting text. The spec's text-secondary; used where the code says textMuted (keeps ≥4.5:1 on surface). */
    val textMuted = Color(0xFFABBDB7)
    val textSecondary = textMuted
    val textTertiary = Color(0xFF81968F)
    val border = Color(0xFF2C4740)
    val borderActive = mint
    val success = Color(0xFF43D99B)
    val warning = Color(0xFFFFB65C)
    val danger = Color(0xFFFF6B79)          // spec "error"
    val disabledSurface = Color(0xFF182421)
    val disabledText = Color(0xFF89968F)
    val onAction = Color(0xFF07110F)
    /** Decorative mint: borders at ~30 %, glow at ~15 % (spec "Use mint at approximately 25–40% opacity"). */
    val holoBorder = mint.copy(alpha = 0.32f)
    val holoGlow = mint.copy(alpha = 0.15f)
    val copperGradient = Brush.horizontalGradient(listOf(copper, Color(0xFFF07A2E)))
    val markGradient = Brush.linearGradient(listOf(mint, Color(0xFF0FB897)))
    // From PR #20 (mint holographic design).
    val ambient = Brush.verticalGradient(listOf(Color(0xFF0A211B), background, background))
    val actionGradient = Brush.horizontalGradient(listOf(mintSoft, mint))
    val cardGradient = Brush.linearGradient(listOf(surfaceInset, surface, background))
}

/** Shape and spacing tokens (spec "Layout and typography"). */
object EdgeShapes {
    val card = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val control = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val pill = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val gap = 16.dp
    val minTouch = 48.dp
}

private val scheme = darkColorScheme(
    primary = EdgeColors.mint,
    onPrimary = EdgeColors.onAction,
    primaryContainer = EdgeColors.surfacePressed,
    onPrimaryContainer = EdgeColors.mintSoft,
    secondary = EdgeColors.mint,
    onSecondary = EdgeColors.onAction,
    secondaryContainer = EdgeColors.surfacePressed,
    onSecondaryContainer = EdgeColors.mintSoft,
    tertiary = EdgeColors.copper,
    onTertiary = EdgeColors.onAction,
    tertiaryContainer = Color(0xFF3E2515),
    onTertiaryContainer = EdgeColors.warning,
    surfaceTint = EdgeColors.mint,
    outlineVariant = EdgeColors.border,
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
    errorContainer = Color(0xFF3F1622),
    onErrorContainer = EdgeColors.danger,
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
