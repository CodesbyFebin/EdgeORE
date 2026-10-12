package com.edgeore.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreState
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ui.screens.OreFreshness
import com.edgeore.app.ui.screens.OreHome
import com.edgeore.app.ui.screens.OreHomeStrip
import com.edgeore.app.ui.screens.PriceQuote
import com.edgeore.app.ui.theme.EdgeOreTheme
import org.junit.Assert.assertEquals
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28])
class OreHomeStripTest {
    @get:Rule val rule = createComposeRule()
    private val f = OreV3Test.Fixture("mainnet-round-437127.json")
    private val snap = OreSnapshot("https://api.mainnet-beta.solana.com", OreV3.decodeBoard(f.board.owner, f.board.data), f.slot("board"),
        OreV3.decodeTreasury(f.treasury.owner, f.treasury.data), OreV3.decodeRound(f.round.owner, f.round.data), f.slot("round"), 1_000L, f.rate)

    @Test fun liveReadShowsRealStatsAndNoPrices() {
        var opened: Boolean? = null
        rule.setContent { EdgeOreTheme { OreHomeStrip(OreState(snapshot = snap), 1_000L, onOpen = { opened = it }) } }
        rule.onNodeWithText("6.5").assertExists()
        rule.onNodeWithText("4.6 ORE").assertExists()
        rule.onNodeWithText("00:08").assertExists()
        rule.onNodeWithTag("price-ORE", useUnmergedTree = true).assertTextEquals("—")
        rule.onNodeWithTag("price-SOL", useUnmergedTree = true).assertTextEquals("—")
        for (chip in listOf("+0.01", "+0.1", "+1", "MAX", "Deploy")) assertEquals(chip, 0, rule.onAllNodesWithText(chip).fetchSemanticsNodes().size)
        rule.onNodeWithTag("ore-home-Lite").performClick(); assertEquals(false, opened)
        rule.onNodeWithTag("ore-home-Pro").performClick(); assertEquals(true, opened)
    }

    @Test fun freshnessIsLabelledHonestly() {
        assertEquals(OreFreshness.NOT_READ, OreHome.freshness(OreState(), 0))
        assertEquals(OreFreshness.LOADING, OreHome.freshness(OreState(loading = true), 0))
        assertEquals(OreFreshness.ERROR, OreHome.freshness(OreState(error = "timeout"), 0))
        assertEquals(OreFreshness.LIVE, OreHome.freshness(OreState(snapshot = snap), 1_000L + OreHome.STALE_AFTER_MS))
        assertEquals(OreFreshness.STALE, OreHome.freshness(OreState(snapshot = snap), 1_001L + OreHome.STALE_AFTER_MS))
        assertEquals(OreFreshness.ERROR, OreHome.freshness(OreState(snapshot = snap, error = "timeout"), 1_000L))
    }

    @Test fun staleAndLoadingStatesAreShown() {
        rule.setContent { EdgeOreTheme { OreHomeStrip(OreState(snapshot = snap), 91_000L, onOpen = {}) } }
        rule.onNodeWithTag("ore-home-status").assertExists()
        assertEquals(1, rule.onAllNodesWithText("Stale", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun nothingReadShowsDashesNotZero() {
        rule.setContent { EdgeOreTheme { OreHomeStrip(OreState(loading = true), 0L, onOpen = {}) } }
        rule.onNodeWithText("Reading the ORE v3 board…").assertExists()
        assertEquals(0, rule.onAllNodesWithText("0").fetchSemanticsNodes().size)
    }

    @Test fun aPriceIsShownOnlyWithItsSource() {
        assertEquals("—", OreHome.price("SOL", emptyList()))
        assertEquals("$1.23", OreHome.price("SOL", listOf(PriceQuote("SOL", java.math.BigDecimal("1.239"), "test", 1L))))
    }

    /**
     * Previews of the Mine home (ORE strip + session panel), written only when HOME_PREVIEW_OUT and MINE_FRAMES_SESSION are set.
     * The ORE numbers are the saved mainnet fixture (mainnet-round-437127.json, read at its capture time), and the ACTIVE
     * session numbers are the real e2e capture (evidence/contribution-session/e2e/active-snapshot.json). Not part of the gate.
     */
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(sdk = [28], qualifiers = "w360dp-h1200dp-hdpi")
    @Test fun homePreview() {
        val out = System.getenv("HOME_PREVIEW_OUT"); val snapPath = System.getenv("MINE_FRAMES_SESSION")
        org.junit.Assume.assumeTrue(!out.isNullOrBlank() && !snapPath.isNullOrBlank())
        val dir = java.io.File(out!!).apply { mkdirs() }
        val read = java.time.Instant.parse(f.j.getString("capturedAtUtc").replace("+00:00", "Z")).toEpochMilli()
        val ore = OreState(snapshot = snap.copy(observedAtMs = read))
        val j = org.json.JSONObject(java.io.File(snapPath!!).readText())
        val active = com.edgeore.app.contribution.SessionView(com.edgeore.app.contribution.SessionPhase.ACTIVE, (0 until j.getJSONArray("parts").length()).map { i ->
            val p = j.getJSONArray("parts").getJSONObject(i); val ms = p.getJSONArray("measurements")
            com.edgeore.app.contribution.PartView(com.edgeore.app.contribution.PartKind.valueOf(p.getString("kind")),
                if (p.getString("status") == "Running") com.edgeore.app.contribution.PartStatus.Running else com.edgeore.app.contribution.PartStatus.Waiting,
                (0 until ms.length()).map { k -> ms.getJSONObject(k).let { m -> com.edgeore.app.contribution.Measurement(m.getString("label"), m.getLong("value"), m.getString("unit"), m.getString("source")) } },
                rounds = p.getInt("rounds"))
        })
        val idle = com.edgeore.app.contribution.SessionView(com.edgeore.app.contribution.SessionPhase.IDLE, active.parts.map { com.edgeore.app.contribution.PartView(it.kind, com.edgeore.app.contribution.PartStatus.Ready) })
        var view by androidx.compose.runtime.mutableStateOf(idle)
        var phase by androidx.compose.runtime.mutableStateOf(0f)
        rule.setContent {
            EdgeOreTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.edgeore.app.ui.components.LocalMiningPhaseOverride provides phase) {
                    androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize(), color = com.edgeore.app.ui.theme.EdgeColors.background, contentColor = com.edgeore.app.ui.theme.EdgeColors.textPrimary) {
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                            OreHomeStrip(ore, read, onOpen = {})
                            com.edgeore.app.ui.screens.MiningSessionPanel(view, edgeModeResumed = true, headline = "", onStart = {}, onStop = {}, onToggleEdgeMode = {})
                        }
                    }
                }
            }
        }
        fun save(name: String) = rule.onRoot().captureRoboImage(java.io.File(dir, "$name.png").path)
        rule.waitForIdle(); rule.onNodeWithText("Mine").assertExists(); save("idle")
        view = active; rule.waitForIdle(); rule.onNodeWithText("Stop").assertExists()
        for (i in 0 until 25) { phase = i / 25f; rule.waitForIdle(); save("active-%02d".format(i)) }
    }
}
