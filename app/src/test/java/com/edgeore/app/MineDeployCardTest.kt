package com.edgeore.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edgeore.app.ore.OreDeployAvailability
import com.edgeore.app.ui.screens.ORE_DEPLOY_NFA
import com.edgeore.app.ui.screens.OreMiningCard
import com.edgeore.app.ui.screens.WALLET_REQUIRED
import com.edgeore.app.ui.screens.deployBlocker
import com.edgeore.app.ui.theme.EdgeOreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
@Config(sdk = [28], qualifiers = "w360dp-h1600dp")
class MineDeployCardTest {
    @get:Rule val rule = createComposeRule()
    private val avail = OreDeployAvailability.Available("https://api.devnet.solana.com", 5, 9, 0)

    private fun show(initial: OreDeployUi, wallet: Boolean, balance: Long?): () -> OreDeployUi {
        var ui by mutableStateOf(initial)
        rule.setContent { EdgeOreTheme { OreMiningCard(ui, wallet, balance, onEdit = { f -> ui = f(ui) }, onCheck = {}, onDeploy = {}) } }
        return { ui }
    }

    @Test fun unavailableOnDevnetKeepsDeployOffWithTheReason() {
        show(OreDeployUi(availability = OreDeployAvailability.Unavailable("https://api.devnet.solana.com", "ORE v3 program oreV3EG1… is not deployed on this cluster", 0)), true, 1_000_000_000)
        rule.onNodeWithTag("ore-deploy").assertIsNotEnabled()
        rule.onNodeWithText("Deploy off: ORE v3 program oreV3EG1… is not deployed on this cluster (devnet). EdgeORE does not fall back to mainnet.").assertExists()
        rule.onNodeWithText(ORE_DEPLOY_NFA).assertExists()
    }

    @Test fun walletRequiredHint() {
        show(OreDeployUi(availability = avail), false, null)
        rule.onNodeWithTag("ore-deploy").assertIsNotEnabled()
        rule.onNodeWithText(WALLET_REQUIRED).assertExists()
    }

    @Test fun chipsAndMaxAreCapped() {
        val ui = show(OreDeployUi(availability = avail), true, 50_000_000)
        rule.onNodeWithTag("chip-+0.01").performClick(); assertEquals(10_000_000L, ui().totalLamports)
        rule.onNodeWithTag("chip-+1").performClick(); assertEquals(40_000_000L, ui().totalLamports) // balance − 0.01 reserve
        rule.onNodeWithTag("chip-MAX").performClick(); assertEquals(40_000_000L, ui().totalLamports)
        rule.onNodeWithTag("ore-deploy").assertIsEnabled()
        rule.onNodeWithTag("ore-amount").assertTextEquals("0.04")
    }

    @Test fun proNeedsSquares() {
        val ui = show(OreDeployUi(availability = avail, totalLamports = 10_000_000), true, 1_000_000_000)
        rule.onNodeWithTag("ore-mode-Pro").performClick()
        rule.onNodeWithTag("ore-deploy").assertIsNotEnabled()
        rule.onNodeWithTag("pick-12").performClick(); assertEquals(1 shl 12, ui().mask)
        rule.onNodeWithTag("ore-deploy").assertIsEnabled()
    }

    @Test fun blockerOrder() {
        assertTrue(deployBlocker(OreDeployUi(), true, 1)!!.contains("not checked"))
        assertEquals(WALLET_REQUIRED, deployBlocker(OreDeployUi(availability = avail), false, 1))
    }

    /**
     * Mine page previews, written only when MINE_DEPLOY_OUT and MINE_FRAMES_SESSION are set (not part of the gate).
     * Wallet not connected, balance not read, CPU/Memory "Not read" (the JVM is not a phone), and the devnet result
     * recorded in docs/ore/devnet-check.txt (program, Board, Treasury and Config all null on devnet).
     */
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(sdk = [28], qualifiers = "w360dp-h2200dp-hdpi")
    @Test fun minePreview() {
        val out = System.getenv("MINE_DEPLOY_OUT"); val snapPath = System.getenv("MINE_FRAMES_SESSION")
        org.junit.Assume.assumeTrue(!out.isNullOrBlank() && !snapPath.isNullOrBlank())
        val dir = java.io.File(out!!).apply { mkdirs() }
        val j = org.json.JSONObject(java.io.File(snapPath!!).readText())
        val c = com.edgeore.app.contribution.SessionPhase.ACTIVE
        val active = com.edgeore.app.contribution.SessionView(c, (0 until j.getJSONArray("parts").length()).map { i ->
            val p = j.getJSONArray("parts").getJSONObject(i); val ms = p.getJSONArray("measurements")
            com.edgeore.app.contribution.PartView(com.edgeore.app.contribution.PartKind.valueOf(p.getString("kind")),
                if (p.getString("status") == "Running") com.edgeore.app.contribution.PartStatus.Running else com.edgeore.app.contribution.PartStatus.Waiting,
                (0 until ms.length()).map { k -> ms.getJSONObject(k).let { m -> com.edgeore.app.contribution.Measurement(m.getString("label"), m.getLong("value"), m.getString("unit"), m.getString("source")) } },
                rounds = p.getInt("rounds"))
        })
        val idle = com.edgeore.app.contribution.SessionView(com.edgeore.app.contribution.SessionPhase.IDLE, active.parts.map { com.edgeore.app.contribution.PartView(it.kind, com.edgeore.app.contribution.PartStatus.Ready) })
        var view by mutableStateOf(idle)
        var phase by mutableStateOf(0f)
        val deploy = OreDeployUi(availability = OreDeployAvailability.Unavailable("https://api.devnet.solana.com", "ORE v3 program oreV3EG1… is not deployed on this cluster", 0))
        rule.setContent {
            EdgeOreTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.edgeore.app.ui.components.LocalMiningPhaseOverride provides phase) {
                    androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize(), color = com.edgeore.app.ui.theme.EdgeColors.background, contentColor = com.edgeore.app.ui.theme.EdgeColors.textPrimary) {
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                            com.edgeore.app.ui.components.SectionTitle("Mine", "Your device. Your resources. Your control.")
                            com.edgeore.app.ui.screens.MineWalletRow(null, false) {}
                            com.edgeore.app.ui.screens.MineBalanceCards(false, null)
                            com.edgeore.app.ui.screens.EdgeNodeCard(view, true, "", {}, {}, {})
                            com.edgeore.app.ui.screens.DeviceCards(null, null)
                            OreMiningCard(deploy, false, null, {}, {}, {})
                            com.edgeore.app.ui.screens.ContributionToggles(true, true, 20, { it.effect.label }, {}, {})
                        }
                    }
                }
            }
        }
        fun save(name: String) = rule.onRoot().captureRoboImage(java.io.File(dir, "$name.png").path)
        rule.waitForIdle(); save("idle")
        view = active; rule.waitForIdle()
        for (i in 0 until 25) { phase = i / 25f; rule.waitForIdle(); save("active-%02d".format(i)) }
    }
}
