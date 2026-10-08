package com.edgeore.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real Activity and walks the four destinations, checking honest default states. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(text: String, substring: Boolean = true) =
        assertTrue("missing: $text", rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty())

    @Test fun walksAllDestinations() {
        exists("Edge Mode")
        exists("Not connected")
        exists("Not observed")
        exists("No measured samples")

        rule.onNodeWithText("AI", substring = false).performClick()
        exists("Private AI")
        exists("No local model installed")
        rule.onNodeWithText("Choose local model first").performScrollTo().assertIsNotEnabled()

        rule.onNodeWithText("Nodes", substring = false).performClick()
        exists("Owned Nodes")
        exists("No node paired")
        exists("Capacity unavailable")

        rule.onNodeWithText("Receipts", substring = false).performClick()
        exists("No receipts yet.")
        rule.onNodeWithText("Run tamper test").performScrollTo().performClick()
        rule.waitForIdle()
        exists("No receipts yet: create one first")

        rule.onNodeWithText("Mine", substring = false).performClick()
        rule.onNodeWithText("Preview session (concept)").performScrollTo().performClick()
        exists("CONCEPT · SAMPLE DATA")
    }
}
