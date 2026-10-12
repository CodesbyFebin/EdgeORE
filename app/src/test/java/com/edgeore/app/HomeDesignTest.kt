package com.edgeore.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.edgeore.app.ore.OreDeployAvailability
import com.edgeore.app.solana.Cluster
import com.edgeore.app.ui.screens.BalanceRow
import com.edgeore.app.ui.screens.ContributionToggles
import com.edgeore.app.ui.screens.EdgeNodeCard
import com.edgeore.app.ui.screens.NetworkSelector
import com.edgeore.app.ui.screens.OreMiningCard
import com.edgeore.app.ui.screens.ResourceGrid
import com.edgeore.app.ui.screens.WalletConnectRow
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** The Home layout from the design concept: one network selector, wallet, balances, Edge node, metrics, ORE card with round blocks, preferences. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [28], qualifiers = "w400dp-h2600dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class HomeDesignTest {
    @get:Rule val rule = createComposeRule()

    private fun home(cluster: Cluster, deploy: OreDeployUi) = rule.setContent {
        EdgeOreTheme {
            Surface(Modifier.fillMaxSize(), color = EdgeColors.background, contentColor = EdgeColors.textPrimary) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NetworkSelector(cluster, {})
                    WalletConnectRow(null, false, cluster, {}, {}, {}, {})
                    BalanceRow(com.edgeore.app.solana.Balances(), 0L, false, "ORE")
                    EdgeNodeCard(com.edgeore.app.contribution.SessionView(), false, "", {}, {}, {})
                    ResourceGrid(null, null, null, com.edgeore.app.device.NetActivity.UNAVAILABLE, true)
                    OreMiningCard(deploy, false, null, {}, {}, {})
                    ContributionToggles(true, true, 20, { it.effect.label }, {}, {})
                }
            }
        }
    }

    private val proDeploy = OreDeployUi(pro = true, proMask = (1 shl 2) or (1 shl 7) or (1 shl 12) or (1 shl 17) or (1 shl 22),
        availability = OreDeployAvailability.Available("https://api.mainnet-beta.solana.com", 1, 1, 0), mainnet = true)

    @Test fun homeFollowsTheDesignAndOneNetworkSwitch() {
        home(Cluster.MAINNET, proDeploy)
        rule.onNodeWithText("Wallet not connected").assertExists()
        rule.onNodeWithText("Connect wallet").assertExists()
        rule.onNodeWithText("Round blocks").assertExists()
        rule.onNodeWithText("01", useUnmergedTree = true).assertExists(); rule.onNodeWithText("25", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("check-2", useUnmergedTree = true).assertExists(); rule.onNodeWithTag("check-0", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Amount per round").assertExists()
        rule.onNodeWithText("Device preferences").assertExists()
        rule.onNodeWithText("Network: Mainnet (from the selector at the top)").assertExists()
        rule.onNodeWithTag("network-dropdown").assertExists()
        rule.onNodeWithTag("ore-mainnet-ack").assertExists() // the risk tick stays
        rule.onNodeWithText("Review deployment").assertExists()
        rule.onNodeWithTag("ore-deploy").assertIsNotEnabled()
        rule.onNodeWithText("Design concept", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Preview only", substring = true).assertDoesNotExist()
        val dir = System.getenv("HOME_FINAL_DIR")
        if (!dir.isNullOrBlank()) { rule.waitForIdle(); rule.onRoot().captureRoboImage("$dir/home.png") }
    }
}
