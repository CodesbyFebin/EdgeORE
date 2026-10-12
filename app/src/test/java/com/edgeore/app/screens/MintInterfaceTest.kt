package com.edgeore.app.screens

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.MainActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** JVM renders and interactions; not wallet/device qualification. No fabricated balances. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-normal-notround-any-420dpi-keyshidden-nonav")
class MintInterfaceTest {
    @get:Rule(order = 0) val readings = FixedDeviceReadings()
    @get:Rule(order = 1) val onboarded = OnboardingSeen()
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()
    private val out = System.getProperty("screens.out") ?: "build/screenshots"
    @Test fun connectShowsSolanaConsentWithoutSigning() {
        rule.onNodeWithText("Connect", substring = false).performScrollTo().performClick()
        // Merged Mine (#22) also titles its card "Connect a Solana wallet", so the sheet adds a second node.
        rule.onAllNodesWithText("Connect a Solana wallet", substring = false).onLast().assertExists()
        rule.onNodeWithText("Continue to wallet", substring = false).assertExists()
        rule.onAllNodes(isRoot()).onLast().captureRoboImage("$out/mint/wallet-chooser.png")
    }
    @Test fun settingsUseOptInRemindersAndScrollableBuildDisclosure() {
        rule.onNodeWithContentDescription("Settings and build").performClick()
        rule.onNodeWithText("Optional daily reminder", substring = false).assertExists()
        rule.onAllNodes(isRoot()).onLast().captureRoboImage("$out/mint/settings.png")
    }
    @Test fun helpShowsCurrentStateWithoutClaimingEarnings() {
        rule.onNodeWithContentDescription("ORE Bot help").performClick()
        rule.onNodeWithText("ORE Bot · Basic help", substring = false).assertExists()
        rule.onNodeWithText("Wallet: not connected.", substring = true).assertExists()
        rule.onAllNodes(isRoot()).onLast().captureRoboImage("$out/mint/basic-help.png")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-normal-notround-any-420dpi-keyshidden-nonav", fontScale = 2f)
class MintLargeTextTest {
    @get:Rule(order = 0) val readings = FixedDeviceReadings()
    @get:Rule(order = 1) val onboarded = OnboardingSeen()
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()
    @Test fun walletAndReviewRemainReachableAtTwoHundredPercent() {
        rule.onNodeWithText("Connect", substring = false).performScrollTo().assertHasClickAction()
        rule.onNodeWithText("Review a supported action", substring = false).performScrollTo().performClick()
        rule.onAllNodesWithText("Review", substring = true).onFirst().assertExists()
        rule.onAllNodes(isRoot()).onLast().captureRoboImage("${System.getProperty("screens.out")}/mint/review-200percent.png")
        rule.onNodeWithTag("nav-Receipts").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("No receipts yet.", substring = false).assertExists()
    }
}
