package com.edgeore.app.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.MainActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** dApp Store screenshots: the real MainActivity, fresh install (no wallet, so no balances), 1080x1920 portrait. Set -Dstore.out. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-normal-long-notround-port-xxhdpi-keyshidden-nonav")
class StoreScreenshotTest {
    @get:Rule(order = 0) val readings = FixedDeviceReadings()
    @get:Rule(order = 1) val onboarded = OnboardingSeen()
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()
    private val out: String? = System.getProperty("store.out")

    private fun top() { rule.onAllNodes(hasScrollAction()).onFirst().performTouchInput { repeat(10) { swipeDown() } }; rule.waitForIdle() }
    private fun shot(name: String) { assumeTrue(out != null); rule.waitForIdle(); rule.onRoot().captureRoboImage("$out/$name.png") }

    @Test fun home() { top(); shot("screenshot-1-home") }
    @Test fun oreCard() { rule.onNode(hasTestTag("ore-mining")).performScrollTo(); rule.onNode(hasText("Round blocks")).performScrollTo(); shot("screenshot-2-ore-card") }
    @Test fun review() { rule.onNode(hasText("Send SOL (devnet transfer review)") and hasClickAction()).performScrollTo().performClick(); rule.waitForIdle(); top(); shot("screenshot-3-review") }
    @Test fun receipts() { rule.onNode(hasText("Receipts") and hasClickAction()).performClick(); rule.waitForIdle(); top(); shot("screenshot-4-receipts") }
}
