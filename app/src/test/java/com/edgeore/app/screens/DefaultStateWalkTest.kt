package com.edgeore.app.screens

import com.edgeore.app.MainActivity
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

/** JVM twin of androidTest AppSmokeTest (Robolectric API 28), so the walk is executed even without a phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class DefaultStateWalkTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(text: String, substring: Boolean = true) =
        assertTrue("missing: $text", rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty())

    @Test fun walksAllDestinations() {
        exists("Edge Mode")
        exists("Not connected")
        exists("Not observed")
        exists("Saved only")          // CPU limit states it is not enforced

        rule.onNodeWithText("AI", substring = false).performClick()
        exists("Personal Edge AI")
        exists("Cloud fallback OFF")
        exists("No model names reported.")
        rule.onNodeWithText("Send stays off").performScrollTo().assertIsNotEnabled()

        rule.onNodeWithText("Storage", substring = false).performClick()
        exists("Encrypted on this device. Backup stays under your control.")
        exists("Not configured")      // remote backup: no EdgeORE backend in this build
        exists("No files in the vault yet.")
        exists("Unavailable")         // kill switch: no VPN tunnel exists

        rule.onNodeWithText("Nodes", substring = false).performClick()
        exists("Owned Nodes")
        exists("No node paired")
        exists("Capacity unavailable")

        rule.onNodeWithText("Receipts", substring = false).performClick()
        exists("No receipts yet.")

        rule.onNodeWithText("Mine", substring = false).performClick()
        rule.onNodeWithText("Preview session", substring = false).performScrollTo().performClick()
        exists("CONCEPT · SAMPLE DATA")
    }
}
