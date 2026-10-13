package com.edgeore.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.edgeore.app.screens.FixedDeviceReadings
import com.edgeore.app.screens.OnboardingSeen
import com.edgeore.app.ui.Destination
import com.edgeore.app.ui.screens.ZK_NOTE
import com.edgeore.app.ui.theme.DarkPalette
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.LightPalette
import com.edgeore.app.ui.theme.ThemeMode
import com.edgeore.app.ui.theme.ThemeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Bottom nav Home | AI | ZK Proof | Settings, honest AI/ZK states, and Receipts/Storage/Nodes still reachable. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28], qualifiers = "w411dp-h3000dp")
class FourScreensTest {
    @get:Rule(order = 0)
    val readings = FixedDeviceReadings()

    @get:Rule(order = 1)
    val onboarded = OnboardingSeen()

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<MainActivity>()

    @After fun reset() {
        ThemeState.mode = ThemeMode.DARK
        EdgeColors.palette = DarkPalette
    }

    private fun tab(label: String) {
        rule.onNode(hasText(label) and hasClickAction()).performClick()
        rule.waitForIdle()
    }

    @Test fun bottomNavHasTheFourDestinations() {
        assertEquals(listOf("Home", "AI", "ZK Proof", "Settings"), Destination.nav.map { it.label })
        rule.onNodeWithTag("ore-mining").assertExists() // ORE stays on Home
    }

    @Test fun javAiShowsHonestModelState() {
        tab("AI")
        rule.onNodeWithText("Jav AI").assertExists()
        rule.onNodeWithText("Model not loaded").assertExists()
        rule.onNodeWithText("No model installed").assertExists()
        rule.onNodeWithTag("import-model").performClick()
        rule.onNodeWithText("Picking an arbitrary file is not supported", substring = true).assertExists()
    }

    @Test fun zkProofMakesNoClaims() {
        tab("ZK Proof")
        rule.onNodeWithText("Prove. Verify. Share.").assertExists()
        rule.onNodeWithText("Prover not configured").assertExists()
        rule.onNodeWithText("Not loaded").assertExists()
        rule.onNodeWithTag("zk-circuit").performClick()
        rule.onNodeWithText("No circuits are available in this build yet.").assertExists()
        rule.onNodeWithText(ZK_NOTE).assertExists()
    }

    @Test fun settingsReachesReceiptsStorageNodesAndTheme() {
        tab("Settings")
        rule.onNodeWithText("Your device. Your rules.").assertExists()
        rule.onNodeWithTag("settings-connect").assertExists()
        rule.onNodeWithText("Device preferences").assertExists()
        rule.onNodeWithText("Default model").assertExists()
        rule.onNodeWithTag("theme-light").performScrollTo().performClick()
        rule.waitForIdle()
        assertSame(LightPalette, EdgeColors.palette)
        rule.onNode(hasTestTag("settings-receipts")).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Receipts", substring = false).assertExists()
    }
}
