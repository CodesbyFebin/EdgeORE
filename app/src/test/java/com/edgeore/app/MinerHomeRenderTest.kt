package com.edgeore.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.edgeore.app.edge.FeeMath
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ui.screens.MinerClaimCard
import com.edgeore.app.ui.screens.MinerRoundBoard
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.Base64

/** Miner home from recorded mainnet accounts only (round 437127 and the Config read of 20 Oct 2026). */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28], qualifiers = "w400dp-h1400dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class MinerHomeRenderTest {
    @get:Rule val rule = createComposeRule()

    private val f = OreV3Test.Fixture("mainnet-round-437127.json")
    private val cfg =
        JSONObject(javaClass.classLoader!!.getResource("ore/mainnet-config.json").readText()).let {
            OreV3.decodeConfig(it.getString("owner"), Base64.getDecoder().decode(it.getString("dataBase64")))
        }

    private fun snap(config: OreV3.Config?) =
        OreSnapshot(
            "https://api.mainnet-beta.solana.com",
            OreV3.decodeBoard(f.board.owner, f.board.data),
            f.slot("board"),
            OreV3.decodeTreasury(f.treasury.owner, f.treasury.data),
            OreV3.decodeRound(f.round.owner, f.round.data),
            f.slot("round"),
            0L,
            null,
            config,
        )

    private fun host(s: OreSnapshot?) =
        rule.setContent {
            EdgeOreTheme {
                Surface(Modifier.fillMaxSize(), color = EdgeColors.background, contentColor = EdgeColors.textPrimary) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MinerRoundBoard(s, 0L)
                        MinerClaimCard(onReceipts = {}, receiptCount = 0)
                    }
                }
            }
        }

    @Test fun boardShowsSquares0To24CountdownMotherlodeConfigTimingAndFees() {
        host(snap(cfg))
        (0 until OreV3.SQUARES).forEach { rule.onNodeWithTag("miner-square-$it").assertExists() }
        rule.onNodeWithTag("miner-square-25").assertDoesNotExist()
        rule.onNodeWithTag("miner-countdown").assertExists()
        rule.onNodeWithText("Motherlode pool", substring = true).assertExists()
        rule.onNodeWithText("Round 300 slots · break 60 slots", substring = true).assertExists()
        rule.onNodeWithText(FeeMath.LOSING).assertExists()
        rule.onNodeWithText("not affiliated", substring = true).assertExists()
        rule.onNodeWithText("Claim review is not in this build", substring = true).assertExists()
        System.getenv("HOME_FINAL_DIR")?.let {
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("$it/miner-home.png")
        }
    }

    @Test fun noConfigMeansNoTimingInvented() {
        host(snap(null))
        rule.onNodeWithText("Config not read", substring = true).assertExists()
    }

    @Test fun noBoardShowsNoSquares() {
        host(null)
        rule.onNodeWithTag("miner-square-0").assertDoesNotExist()
        rule.onNodeWithText("No live board read yet", substring = true).assertExists()
    }
}
