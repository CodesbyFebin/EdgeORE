package com.edgeore.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
class EdgePalette(
    val light: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfacePressed: Color,
    val surfaceInset: Color,
    val mint: Color,
    val mintSoft: Color,
    val copper: Color,
    val copperDeep: Color,
    val mainnetWarning: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textTertiary: Color,
    val border: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val disabledSurface: Color,
    val disabledText: Color,
    val onAction: Color,
    val ambientTop: Color,
    val markEnd: Color,
    val copperEnd: Color,
)

/** The existing EdgeORE dark palette, unchanged. */
val DarkPalette =
    EdgePalette(
        light = false,
        background = Color(0xFF060D0C),
        surface = Color(0xFF0D1917),
        surfaceRaised = Color(0xFF142421),
        surfacePressed = Color(0xFF1C3530),
        surfaceInset = Color(0xFF09130F),
        mint = Color(0xFF19E6BC),
        mintSoft = Color(0xFF8BF4DF),
        copper = Color(0xFFFF963F),
        copperDeep = Color(0xFFE8792A),
        mainnetWarning = Color(0xFFFFA365),
        textPrimary = Color(0xFFF2F7F5),
        textMuted = Color(0xFFABBDB7),
        textTertiary = Color(0xFF81968F),
        border = Color(0xFF2C4740),
        success = Color(0xFF43D99B),
        warning = Color(0xFFFFB65C),
        danger = Color(0xFFFF6B79),
        disabledSurface = Color(0xFF182421),
        disabledText = Color(0xFF89968F),
        onAction = Color(0xFF07110F),
        ambientTop = Color(0xFF0A211B),
        markEnd = Color(0xFF0FB897),
        copperEnd = Color(0xFFF07A2E),
    )

/** Accessible light counterpart: text and accents chosen for >= 4.5:1 on the light surfaces. */
val LightPalette =
    EdgePalette(
        light = true,
        background = Color(0xFFF6F8F7),
        surface = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFEEF2F0),
        surfacePressed = Color(0xFFDDEFE9),
        surfaceInset = Color(0xFFF0F3F2),
        mint = Color(0xFF0A7560),
        mintSoft = Color(0xFF0E9F80),
        copper = Color(0xFFA0521A),
        copperDeep = Color(0xFF8A4614),
        mainnetWarning = Color(0xFF9C4C14),
        textPrimary = Color(0xFF101414),
        textMuted = Color(0xFF56625C),
        textTertiary = Color(0xFF5F6B66),
        border = Color(0xFFD5DCD9),
        success = Color(0xFF1B7F55),
        warning = Color(0xFF8A5A00),
        danger = Color(0xFFB3261E),
        disabledSurface = Color(0xFFE6EAE8),
        disabledText = Color(0xFF6E7A75),
        onAction = Color(0xFFFFFFFF),
        ambientTop = Color(0xFFE3F2EC),
        markEnd = Color(0xFF0E9F80),
        copperEnd = Color(0xFF9A4E17),
    )

/** Dark | Light | System, chosen in Settings and persisted (ThemePrefs). */
enum class ThemeMode(
    val label: String,
) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System"),
}

object ThemePrefs {
    private const val FILE = "edgeore_ui"
    private const val KEY = "theme"

    fun read(ctx: android.content.Context): ThemeMode =
        ctx.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).getString(KEY, null)?.let { v ->
            ThemeMode.entries.firstOrNull {
                it.name ==
                    v
            }
        }
            ?: ThemeMode.DARK

    fun write(
        ctx: android.content.Context,
        mode: ThemeMode,
    ) = ctx
        .getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(KEY, mode.name)
        .apply()
}

/** The selected mode, observed by EdgeOreTheme. */
object ThemeState {
    var mode by androidx.compose.runtime.mutableStateOf(ThemeMode.DARK)
}

/**
 * Colour tokens. Every token reads the active palette, so all screens follow the Dark/Light setting from this one source.
 */
object EdgeColors {
    var palette: EdgePalette by androidx.compose.runtime.mutableStateOf(DarkPalette)
    val background get() = palette.background
    val surface get() = palette.surface
    val surfaceRaised get() = palette.surfaceRaised
    val surfacePressed get() = palette.surfacePressed
    val surfaceInset get() = palette.surfaceInset
    val mint get() = palette.mint
    val mintSoft get() = palette.mintSoft
    val copper get() = palette.copper
    val orange get() = copper
    val copperDeep get() = palette.copperDeep

    /** Mainnet warning copper: used only where real SOL is at stake (ORE mainnet deploy). */
    val mainnetWarning get() = palette.mainnetWarning
    val textPrimary get() = palette.textPrimary
    val textMuted get() = palette.textMuted
    val textSecondary get() = textMuted
    val textTertiary get() = palette.textTertiary
    val border get() = palette.border
    val borderActive get() = mint
    val success get() = palette.success
    val warning get() = palette.warning
    val danger get() = palette.danger
    val disabledSurface get() = palette.disabledSurface
    val disabledText get() = palette.disabledText
    val onAction get() = palette.onAction
    val holoBorder get() = mint.copy(alpha = if (palette.light) 0.45f else 0.32f)
    val holoGlow get() = mint.copy(alpha = 0.15f)
    val copperGradient get() = Brush.horizontalGradient(listOf(copper, palette.copperEnd))
    val markGradient get() = Brush.linearGradient(listOf(mint, palette.markEnd))
    val ambient get() = Brush.verticalGradient(listOf(palette.ambientTop, background, background))
    val actionGradient get() =
        if (palette.light) {
            Brush.horizontalGradient(
                listOf(mint, mint),
            )
        } else {
            Brush.horizontalGradient(listOf(mintSoft, mint))
        }
    val cardGradient get() = Brush.linearGradient(listOf(surfaceInset, surface, background))
}

/** Shape and spacing tokens (spec "Layout and typography"). */
object EdgeShapes {
    val card =
        androidx.compose.foundation.shape
            .RoundedCornerShape(20.dp)
    val control =
        androidx.compose.foundation.shape
            .RoundedCornerShape(14.dp)
    val pill =
        androidx.compose.foundation.shape
            .RoundedCornerShape(50)
    val gap = 16.dp
    val minTouch = 48.dp
}

private fun schemeFor(p: EdgePalette) =
    if (p.light) {
        androidx.compose.material3.lightColorScheme(
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
            tertiaryContainer = if (p.light) Color(0xFFFBE3D2) else Color(0xFF3E2515),
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
            errorContainer = if (p.light) Color(0xFFF9DEDC) else Color(0xFF3F1622),
            onErrorContainer = EdgeColors.danger,
        )
    } else {
        darkColorScheme(
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
            tertiaryContainer = if (p.light) Color(0xFFFBE3D2) else Color(0xFF3E2515),
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
            errorContainer = if (p.light) Color(0xFFF9DEDC) else Color(0xFF3F1622),
            onErrorContainer = EdgeColors.danger,
        )
    }

private val typography =
    Typography(
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )

@Composable
fun EdgeOreTheme(
    mode: ThemeMode = ThemeState.mode,
    content: @Composable () -> Unit,
) {
    val dark =
        when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        }
    val p = if (dark) DarkPalette else LightPalette
    if (EdgeColors.palette !== p) EdgeColors.palette = p
    MaterialTheme(
        colorScheme = schemeFor(p),
        typography = typography,
        shapes =
            androidx.compose.material3.Shapes(
                extraSmall =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(14.dp),
                extraLarge =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(24.dp),
                small =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(14.dp),
                medium =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(20.dp),
                large =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(24.dp),
            ),
        content = content,
    )
}
