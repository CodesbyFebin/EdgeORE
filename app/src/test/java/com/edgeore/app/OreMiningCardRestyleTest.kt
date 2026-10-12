package com.edgeore.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.edgeore.app.ore.OreDeployAvailability
import com.edgeore.app.ore.OreFormat
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreState
import com.edgeore.app.ore.OreV3
import com.edgeore.app.solana.Cluster
import com.edgeore.app.solana.OreDeployMessage
import com.edgeore.app.ui.screens.OreGridCard
import com.edgeore.app.ui.screens.OreMiningCard
import com.edgeore.app.ui.screens.oreHeaderStats
import com.edgeore.app.ui.screens.stepTiles
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** The ORE-web-app style card: live stats or "—", Lite/Pro, capped chips, Tiles/Rounds steppers. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28], qualifiers = "w400dp-h2000dp")
class OreMiningCardRestyleTest {
    @get:Rule val rule = createComposeRule()
    private val f = OreV3Test.Fixture("mainnet-round-437127.json")
    private val snap = OreSnapshot("https://api.mainnet-beta.solana.com", OreV3.decodeBoard(f.board.owner, f.board.data), f.slot("board"),
        OreV3.decodeTreasury(f.treasury.owner, f.treasury.data), OreV3.decodeRound(f.round.owner, f.round.data), f.slot("round"), 1_000L, f.rate)
    private val avail = OreDeployAvailability.Available("https://api.mainnet-beta.solana.com", 437127, 9, 0)

    @Test fun statsComeFromTheLiveReadOrShowADash() {
        val none = oreHeaderStats(null, 0)
        assertEquals(listOf("—", "—", "—"), listOf(none.deployed, none.motherlode, none.time))
        val s = oreHeaderStats(snap, 1_000L)
        assertEquals(OreFormat.sol(snap.round.totalDeployedLamports), s.deployed)
        assertEquals(OreFormat.ore(snap.treasury.motherlodeGrams), s.motherlode)
    }

    @Test fun tilesStepper() {
        assertEquals(1, stepTiles(0, 1)); assertEquals(0b11, stepTiles(1, 1)); assertEquals(1, stepTiles(0b11, -1))
        assertEquals(0, stepTiles(0, -1)); assertEquals(OreDeployMessage.ALL_SQUARES_MASK, stepTiles(OreDeployMessage.ALL_SQUARES_MASK, 1))
    }

    @Test fun mainnetChipsStopAtTheCapAndRoundsIsFixed() {
        var ui by mutableStateOf(OreDeployUi(availability = avail, mainnet = true, riskAcknowledged = true, mainnetBalanceLamports = 5_000_000_000))
        rule.setContent { EdgeOreTheme { OreMiningCard(ui, true, null, onEdit = { ui = it(ui) }, onCheck = {}, onDeploy = {}, snapshot = snap, nowMs = 1_000L) } }
        rule.onNodeWithTag("chip-+1").performClick(); assertEquals(10_000_000L, ui.totalLamports)
        rule.onNodeWithTag("chip-+0.1").performClick(); assertEquals(10_000_000L, ui.totalLamports)
        rule.onNodeWithTag("rounds-plus").assertIsNotEnabled()
        rule.onNodeWithText("1 round per approval").assertExists()
        rule.onNodeWithTag("ore-mode-Pro").performClick()
        rule.onNodeWithTag("tiles-all").performClick(); rule.onNodeWithTag("tiles-value").assertTextEquals("25")
        rule.onNodeWithTag("tiles-minus").performClick(); rule.onNodeWithTag("tiles-value").assertTextEquals("24")
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    /** Renders Lite and Pro to PNG (run recordRoborazziDebug with ORE_RESTYLE_DIR set; skipped otherwise). */
    @Test fun renderLiteAndPro() {
        val dir = System.getenv("ORE_RESTYLE_DIR")
        org.junit.Assume.assumeTrue("set ORE_RESTYLE_DIR to render", !dir.isNullOrBlank())
        var ui by mutableStateOf(OreDeployUi(availability = avail, mainnet = true, riskAcknowledged = true, mainnetBalanceLamports = 50_000_000, totalLamports = 10_000_000))
        rule.setContent {
            EdgeOreTheme {
                Surface(Modifier.fillMaxSize(), color = EdgeColors.background, contentColor = EdgeColors.textPrimary) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OreMiningCard(ui, true, null, onEdit = { ui = it(ui) }, onCheck = {}, onDeploy = {}, snapshot = snap, nowMs = 1_000L)
                        OreGridCard(Cluster.MAINNET, OreState(snapshot = snap), ui) { i -> ui = ui.copy(proMask = ui.proMask xor (1 shl i)) }
                    }
                }
            }
        }
        rule.waitForIdle(); rule.onRoot().captureRoboImage("$dir/ore-card-lite.png")
        ui = ui.copy(pro = true, proMask = 0b1000110000100); rule.waitForIdle()
        rule.onRoot().captureRoboImage("$dir/ore-card-pro.png")
    }
}
