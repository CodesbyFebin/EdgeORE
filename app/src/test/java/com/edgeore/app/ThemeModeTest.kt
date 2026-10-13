package com.edgeore.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.edgeore.app.ui.components.ThemeSwitch
import com.edgeore.app.ui.theme.DarkPalette
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.edgeore.app.ui.theme.LightPalette
import com.edgeore.app.ui.theme.ThemeMode
import com.edgeore.app.ui.theme.ThemePrefs
import com.edgeore.app.ui.theme.ThemeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Dark | Light | System: persisted, applied to every token, and the light palette keeps readable contrast. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28])
class ThemeModeTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() {
        ThemeState.mode = ThemeMode.DARK
        EdgeColors.palette = DarkPalette
    }

    @Test fun choiceIsPersistedAndDefaultsToDark() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(ThemeMode.DARK, ThemePrefs.read(ctx))
        ThemePrefs.write(ctx, ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, ThemePrefs.read(ctx))
        ThemePrefs.write(ctx, ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, ThemePrefs.read(ctx))
    }

    @Test fun switchAppliesTheLightPaletteToTheTokens() {
        rule.setContent { EdgeOreTheme { ThemeSwitch(ThemeState.mode) { ThemeState.mode = it } } }
        rule.onNodeWithTag("theme-dark").assertIsSelected()
        assertSame(DarkPalette, EdgeColors.palette)
        rule.onNodeWithTag("theme-light").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("theme-light").assertIsSelected()
        assertSame(LightPalette, EdgeColors.palette)
        assertEquals(LightPalette.background, EdgeColors.background)
    }

    private fun lum(c: androidx.compose.ui.graphics.Color): Double {
        fun ch(v: Float) = if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(
        a: androidx.compose.ui.graphics.Color,
        b: androidx.compose.ui.graphics.Color,
    ): Double {
        val (x, y) = lum(a) to lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test fun lightPaletteTextAndAccentsMeetAa() {
        val p = LightPalette
        for (bg in listOf(p.background, p.surface, p.surfaceInset)) {
            for (fg in listOf(p.textPrimary, p.textMuted, p.mint, p.copper, p.mainnetWarning, p.danger)) {
                assertTrue("contrast ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5)
            }
        }
    }
}
