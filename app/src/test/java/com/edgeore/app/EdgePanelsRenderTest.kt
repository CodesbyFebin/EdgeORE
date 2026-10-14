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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.edgeore.app.edge.History
import com.edgeore.app.edge.RoundExplainer
import com.edgeore.app.edge.RoundProof
import com.edgeore.app.edge.RoundRules
import com.edgeore.app.edge.StoredProof
import com.edgeore.app.edge.StrategyPreset
import com.edgeore.app.edge.TileInsight
import com.edgeore.app.ui.screens.ExplainerCard
import com.edgeore.app.ui.screens.HistoryView
import com.edgeore.app.ui.screens.PlanningCalculatorCard
import com.edgeore.app.ui.screens.RoundAlertsCard
import com.edgeore.app.ui.screens.RoundProofView
import com.edgeore.app.ui.screens.TileInsightPanel
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28], qualifiers = "w400dp-h2400dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class EdgePanelsRenderTest {
    @get:Rule val rule = createComposeRule()

    private val real =
        JSONObject(javaClass.classLoader!!.getResource("ore/mainnet-settled-rounds.json").readText())
            .getJSONArray("rounds")
            .getJSONObject(0)
    private val roundBytes = Base64.getDecoder().decode(real.getString("dataBase64"))
    private val round = RoundRules.decodeRound(roundBytes)

    private fun shot(name: String) {
        val dir = System.getenv("HOME_FINAL_DIR") ?: return
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("$dir/$name.png")
    }

    private fun host(content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent {
            EdgeOreTheme {
                Surface(Modifier.fillMaxSize(), color = EdgeColors.background, contentColor = EdgeColors.textPrimary) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
                }
            }
        }

    @Test fun tileInsightIsInformationOnlyAndTapOpensReview() {
        val tiles = TileInsight.tiles(round.deployed, round.count, 10_000_000, com.edgeore.app.ore.OreV3.GRAMS_PER_ORE, 0)
        var reviewed: Int? = null
        var preset: StrategyPreset? = null
        host {
            TileInsightPanel(tiles, RoundRules.distributionMask(round.id), 4, {}, { reviewed = it }, { preset = it })
            ExplainerCard(RoundExplainer.tile(tiles[4]), null, {}, {})
            RoundAlertsCard(false, null) {}
        }
        rule.onNodeWithTag("ev-label").assertExists()
        rule.onNodeWithText(RoundExplainer.NOT_CONFIGURED).assertExists()
        assertEquals(10, (0 until 25).count { runCatching { rule.onNodeWithTag("solo-$it", true).assertExists() }.isSuccess })
        shot("edge-tile-insight")
        rule.onNodeWithTag("insight-review").performClick()
        assertEquals(4, reviewed)
        rule.onNodeWithTag("preset-SOLO").performClick()
        assertEquals(StrategyPreset.SOLO, preset)
    }

    @Test fun planningCalculatorShowsAssumptions() {
        host { PlanningCalculatorCard() }
        rule.onNodeWithText("assumptions, not a promise", substring = true).assertExists()
        rule.onNodeWithText("Break-even ORE price", substring = true).assertExists()
        shot("edge-planning")
    }

    @Test fun historyAndRoundProofRender() {
        val proof =
            RoundProof.build(
                real.getString("address"),
                roundBytes,
                real.getLong("contextSlot"),
                "https://api.mainnet-beta.solana.com",
                null,
                null,
            )
        val check = RoundProof.check(proof, listOf(RoundProof.digest(proof)), com.edgeore.app.ore.OreV3::roundAddress)
        assertTrue(check.failures.toString(), check.accepted)
        host {
            RoundProofView(listOf(StoredProof(RoundProof.digest(proof), round.id, "r1", proof, check)), {})
            HistoryView(
                listOf(
                    History.Row(
                        round.id,
                        "5xSyntheticSignatureForRender",
                        10_000_000,
                        null,
                        null,
                        "Settled; your Miner account was not readable for this round",
                    ),
                ),
            )
        }
        rule.onNodeWithText("Not verified").assertExists()
        shot("edge-round-proof-history")
    }

    @Test fun crewScreenRenders() {
        val a =
            com.edgeore.app.crypto.Base58
                .encode(ByteArray(32) { 1 })
        val b =
            com.edgeore.app.crypto.Base58
                .encode(ByteArray(32) { 2 })
        val plan =
            com.edgeore.app.edge.Crew.Plan(
                "c1",
                "Night crew",
                round.id + 1,
                a,
                listOf(
                    com.edgeore.app.edge.Crew
                        .Member(a, 0b111, 1_000_000),
                    com.edgeore.app.edge.Crew
                        .Member(b, 0b111000, 1_500_000),
                ),
            )
        val s =
            com.edgeore.app.edge.Crew
                .signWithKeystore(
                    plan,
                    com.edgeore.app.receipts
                        .SoftwareReceiptSigner(),
                )
        var reviewed = false
        host {
            com.edgeore.app.ui.screens.CrewView(
                s,
                com.edgeore.app.edge.Crew
                    .verify(s),
                com.edgeore.app.edge.Crew
                    .encode(s),
                null,
                a,
                com.edgeore.app.edge.Crew
                    .stats(listOf(plan), emptyMap()),
                listOf(
                    com.edgeore.app.edge.Crew
                        .Transfer(b, a, 500_000),
                ),
                {},
                { _, _ -> },
                { reviewed = true },
            )
        }
        rule.onNodeWithTag("crew-custody").assertExists()
        rule.onNodeWithText("Not tampered", substring = true).assertExists()
        rule.onNodeWithTag("crew-review").performClick()
        assertTrue(reviewed)
        rule.onNodeWithTag("crew-ack-1").performClick()
        rule.onNodeWithText("spread evenly over 1 member", substring = true).assertExists()
        rule.onNodeWithText("· 25 squares · 0.003 SOL", substring = true).assertExists()
        shot("crew")
    }

    @Test fun onDeviceAdvisorAnswerIsLabelledAndReadOnly() {
        var asked = 0
        host {
            com.edgeore.app.ui.screens.ExplainerCard(
                com.edgeore.app.edge.AdvisorExplainer.LABEL +
                    "\nRound #439116 has SOL on all 25 squares. One square wins when the round ends; " +
                    "SOL on the other squares is mostly returned minus fees. " +
                    "Check the review screen before signing anything.",
                "Qwen2.5-1.5B-Instruct (q8) on this phone (offline)",
                {},
                null,
            )
            com.edgeore.app.ui.screens.ExplainerCard(
                "Rule-based: round #439116, 25 squares.\n" + com.edgeore.app.edge.RoundExplainer.NOT_CONFIGURED,
                null,
                {},
                null,
            )
            com.edgeore.app.ui.screens
                .AdvisorExplainButton { asked++ }
        }
        rule.onNodeWithText("AI explanation, may be wrong; check the review.", substring = true).assertExists()
        rule.onNodeWithText("on this phone (offline)", substring = true).assertExists()
        rule.onNodeWithText("Explain (read-only)").performClick()
        assertTrue(asked == 1)
        shot("ondevice-ai")
    }
}
