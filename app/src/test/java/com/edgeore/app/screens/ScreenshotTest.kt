package com.edgeore.app.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.MainActivity
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real MainActivity from this source tree in its fresh-install state (Robolectric, API 28,
 * so Android thermal status is genuinely "not observed"). No device data is faked: values the JVM
 * cannot observe render as not observed. Runs only with -Pscreens.
 */
private typealias Rule_ = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

private val out: String = System.getProperty("screens.out") ?: "build/screenshots"

// The Storage telemetry line shows a CPU percentage read from the JVM host's /proc/stat (not a phone),
// which varies between runs. A 0.1% pixel threshold tolerates that one line and still catches layout changes.
private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))
// The tall Storage render also shows the host's Disk I/O rate next to CPU. Two varying host lines measured 0.109%
// of that image (run on ea2ec1b), just over 0.1%. 0.3% (~36k px of 1078x11025) still fails any layout shift, which moves
// every pixel below it, but a one-word copy change inside that page could pass: review Storage copy changes by eye.
private val storageFullOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.003f))

private fun Rule_.tab(name: String) { onNode(hasText(name) and hasClickAction()).performClick(); waitForIdle() }
private fun Rule_.button(text: String) { onNode(hasText(text) and hasClickAction()).performScrollTo().performClick(); waitForIdle() }
private fun Rule_.shot(file: String, roborazziOptions: RoborazziOptions = options) {
    onAllNodes(hasScrollAction()).onFirst().performTouchInput { repeat(10) { swipeDown() } }
    waitForIdle()
    onRoot().captureRoboImage("$out/$file.png", roborazziOptions = roborazziOptions)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun mine() = rule.shot("01-mine")
    @Test fun ai() { rule.tab("AI"); rule.shot("02-ai") }
    @Test fun storage() { rule.tab("Storage"); rule.shot("03-storage") }
    @Test fun nodes() { rule.tab("Nodes"); rule.shot("04-nodes") }
    @Test fun receipts() { rule.tab("Receipts"); rule.shot("05-receipts") }
    @Test fun review() { rule.button("Review a supported action"); rule.shot("06-review") }
}

/** Same screens on a very tall viewport so every control and its effect label is visible in one image. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h4200dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class FullScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun mine() = rule.shot("full/01-mine-full")
    @Test fun ai() { rule.tab("AI"); rule.shot("full/02-ai-full") }
    @Test fun storage() { rule.tab("Storage"); rule.shot("full/03-storage-full", storageFullOptions) }
    @Test fun nodes() { rule.tab("Nodes"); rule.shot("full/04-nodes-full") }
    @Test fun receipts() { rule.tab("Receipts"); rule.shot("full/05-receipts-full") }
    @Test fun review() { rule.button("Review a supported action"); rule.shot("full/06-review-full") }
}
