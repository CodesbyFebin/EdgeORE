package com.edgeore.app

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.edgeore.app.ore.AdvisorState
import com.edgeore.app.ore.OreAdvisor
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreState
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ui.screens.ORE_DEPLOY_UNAVAILABLE
import com.edgeore.app.ui.screens.OreContent
import com.edgeore.app.ui.theme.EdgeOreTheme
import org.junit.Assert.assertEquals
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.ConscryptMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28], qualifiers = "w360dp-h2400dp-xhdpi")
class OreScreenTest {
    @get:Rule val rule = createComposeRule()
    private val f = OreV3Test.Fixture("mainnet-round-437127.json")
    private val snap = OreSnapshot("https://api.mainnet-beta.solana.com", OreV3.decodeBoard(f.board.owner, f.board.data), f.slot("board"),
        OreV3.decodeTreasury(f.treasury.owner, f.treasury.data), OreV3.decodeRound(f.round.owner, f.round.data), f.slot("round"), 1_000L, f.rate)

    private fun show(state: OreState, backend: String?, advisor: AdvisorState = AdvisorState()) = rule.setContent {
        EdgeOreTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.androidxScroll()) { OreContent(state, advisor, backend, 1_000L, {}, {}, {}, {}) } }
    }

    @Test fun proGridShowsTheRealPerSquareSolAndNoActingControls() {
        show(OreState(snapshot = snap), backend = null)
        rule.onNodeWithTag("ore-read-only").assertExists()
        rule.onNodeWithText("6.5 SOL").assertExists()
        rule.onNodeWithText("4.6 ORE").assertExists()
        rule.onNodeWithText("00:08").assertExists() // 38 slots at 60 s / 277 slots
        for (i in 0 until 25) rule.onNodeWithTag("ore-square-$i").assertExists()
        rule.onNodeWithTag("ore-square-0").assertExists()
        rule.onNodeWithContentDescription("Square 1: 0.261 SOL, 122 miners").assertExists()
        rule.onNodeWithTag("ore-deploy-unavailable").assertHasNoClickAction()
        rule.onNodeWithText(ORE_DEPLOY_UNAVAILABLE).assertExists()
        rule.onNodeWithText("Read-only · mainnet observation").assertExists()
        rule.onNodeWithTag("ore-source").assertTextContains("Last read", substring = true)
        rule.onNodeWithTag("ore-source").assertTextContains(snap.rpc, substring = true)
        for (chip in listOf("+0.01", "+0.1", "+1", "MAX")) assertEquals(chip, 0, rule.onAllNodesWithText(chip).fetchSemanticsNodes().size)
        rule.onNodeWithText("Prices: not shown. No price source is configured in EdgeORE.").assertExists()
    }

    @Test fun liteShowsTotals() {
        show(OreState(snapshot = snap), backend = null)
        rule.onNodeWithText("Lite").performClick()
        rule.onNodeWithTag("ore-lite").assertExists()
        assertEquals(0, rule.onAllNodesWithContentDescription("Square 1: 0.261 SOL, 122 miners").fetchSemanticsNodes().size)
    }

    @Test fun nothingReadShowsNotObservedNeverZero() {
        show(OreState(error = "Read failed: timeout"), backend = null)
        assertEquals(3, rule.onAllNodesWithText("Not observed").fetchSemanticsNodes().size)
        rule.onNodeWithText("Read failed: timeout").assertExists()
        assertEquals(0, rule.onAllNodesWithText("0 SOL").fetchSemanticsNodes().size)
    }

    @Test fun advisorNotConfiguredAndConfigured() {
        show(OreState(snapshot = snap), backend = null)
        rule.onNodeWithTag("ore-advisor-not-configured").assertExists()
        rule.onNodeWithText(OreAdvisor.NOTICE).assertExists()
        assertEquals(0, rule.onAllNodesWithText("Ask").fetchSemanticsNodes().size)
    }

    @Test fun advisorConfiguredShowsBackendAndAsk() {
        show(OreState(snapshot = snap), backend = "llama3 on your owned host (LAN)")
        assertEquals(0, rule.onAllNodesWithText(OreAdvisor.NOT_CONFIGURED).fetchSemanticsNodes().size)
        rule.onNodeWithText("Ask").assertExists()
    }
}

/** Preview PNGs of the real OreContent with a captured mainnet fixture; written only when ORE_FRAMES_OUT is set. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h1600dp-xhdpi")
class OrePreviewFrames {
    @get:Rule val rule = createComposeRule()
    @Test fun frames() {
        val out = System.getenv("ORE_FRAMES_OUT")
        org.junit.Assume.assumeTrue(!out.isNullOrBlank())
        val dir = java.io.File(out!!).apply { mkdirs() }
        val f = OreV3Test.Fixture(System.getenv("ORE_FRAMES_FIXTURE") ?: "mainnet-round-437134.json")
        val snap = OreSnapshot(f.j.getString("rpc"), OreV3.decodeBoard(f.board.owner, f.board.data), f.slot("board"),
            OreV3.decodeTreasury(f.treasury.owner, f.treasury.data), OreV3.decodeRound(f.round.owner, f.round.data), f.slot("round"),
            java.time.Instant.parse(f.j.getString("capturedAtUtc")).toEpochMilli(), f.rate)
        rule.setContent {
            EdgeOreTheme {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSizeCompat(), color = com.edgeore.app.ui.theme.EdgeColors.background, contentColor = com.edgeore.app.ui.theme.EdgeColors.textPrimary) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding16()) {
                        OreContent(OreState(snapshot = snap), AdvisorState(), null, snap.observedAtMs, {}, {}, {}, {})
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(java.io.File(dir, "ore-pro.png").path)
        rule.onNodeWithText("Lite").performClick(); rule.waitForIdle()
        rule.onRoot().captureRoboImage(java.io.File(dir, "ore-lite.png").path)
    }
}

private fun androidx.compose.ui.Modifier.fillMaxSizeCompat() = this.fillMaxSize()
private fun androidx.compose.ui.Modifier.padding16() = this.then(androidx.compose.ui.Modifier.padding(16.dp))
private fun androidx.compose.ui.Modifier.androidxScroll(): androidx.compose.ui.Modifier = this
