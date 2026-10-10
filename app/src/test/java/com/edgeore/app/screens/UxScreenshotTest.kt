package com.edgeore.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.MainActivity
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.solana.PlainLanguage
import com.edgeore.app.solana.TransferReview
import com.edgeore.app.ui.Onboarding
import com.edgeore.app.ui.screens.PlainLanguageCard
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val out: String = System.getProperty("screens.out") ?: "build/screenshots"
private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))
private const val DEST = "73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n"

/** First run: the introduction is shown before the tabs, and continuing lands on Mine and is remembered. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OnboardingScreenshotTest {
    @get:Rule(order = 0) val readings = FixedDeviceReadings()
    @get:Rule(order = 1) val fresh = OnboardingSeen(false)
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun onboardingThenTabs() {
        rule.onRoot().captureRoboImage("$out/07-onboarding.png", roborazziOptions = options)
        assertTrue(rule.onAllNodes(hasText("What it does not do")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(hasText("Devnet only")).fetchSemanticsNodes().isNotEmpty())
        rule.onNode(hasText("I understand · continue") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText("Edge Mode")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(Onboarding.done(ApplicationProvider.getApplicationContext()))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h2600dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class OnboardingFullScreenshotTest {
    @get:Rule(order = 0) val readings = FixedDeviceReadings()
    @get:Rule(order = 1) val fresh = OnboardingSeen(false)
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()
    @Test fun onboardingFull() = rule.onRoot().captureRoboImage("$out/full/07-onboarding-full.png", roborazziOptions = options)
}

/** Review form with a typed destination and amount: inline checks shown; Prepare stays off until a wallet connects. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h2600dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class ReviewFilledScreenshotTest {
    @get:Rule(order = 0) val readings = FixedDeviceReadings()
    @get:Rule(order = 1) val onboarded = OnboardingSeen()
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun reviewFilled() {
        rule.onNode(hasText("Review a supported action") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(DEST)
        fields[1].performTextInput("0.01")
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText("Valid Solana address format", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(hasText("10000000 lamports.")).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodes(hasScrollAction()).onFirst().performTouchInput { repeat(10) { swipeDown() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("$out/full/08-review-filled-full.png", roborazziOptions = options)
    }
}

/**
 * The plain-language card over a real draft: TransferReview.prepare builds and decodes actual message bytes from a
 * fixed test key, destination and blockhash (no wallet, no RPC). The fee is shown as unknown because no RPC priced it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlainLanguageRenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun plainLanguageCard() {
        val payer = Ed25519.KeyPair(ByteArray(32) { 7 })
        val draft = TransferReview.prepare(payer.publicKey, DEST, "0.01", ByteArray(32) { 9 }, 0, 50_000_000)
        val unsupported = TransferReview.prepare(payer.publicKey, "not-an-address", "0.01", ByteArray(32) { 9 }, 0, 50_000_000)
        rule.setContent {
            EdgeOreTheme {
                Column(Modifier.fillMaxWidth().background(EdgeColors.background).padding(16.dp)) {
                    PlainLanguageCard(PlainLanguage.explain(draft, feeKnown = false, feeLamports = null))
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                    PlainLanguageCard(PlainLanguage.explain(unsupported, feeKnown = false, feeLamports = null))
                }
            }
        }
        rule.onRoot().captureRoboImage("$out/09-plain-language.png", roborazziOptions = options)
    }
}

/** Font scaling: the introduction at 1.5x text size still wraps rather than clipping. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h3400dp-normal-long-notround-any-420dpi-keyshidden-nonav", fontScale = 1.5f)
class OnboardingLargeFontScreenshotTest {
    @get:Rule(order = 0) val readings = FixedDeviceReadings()
    @get:Rule(order = 1) val fresh = OnboardingSeen(false)
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()
    @Test fun onboardingLargeFont() = rule.onRoot().captureRoboImage("$out/full/07-onboarding-fontscale-1.5.png", roborazziOptions = options)
}
