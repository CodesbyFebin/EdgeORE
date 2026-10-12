package com.edgeore.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.contribution.Measurement
import com.edgeore.app.contribution.PartKind
import com.edgeore.app.contribution.PartStatus
import com.edgeore.app.contribution.PartView
import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.contribution.SessionView
import com.edgeore.app.ui.components.MiningPose
import com.edgeore.app.ui.screens.MiningSessionPanel
import com.edgeore.app.ui.screens.SESSION_EXPLAINER
import com.edgeore.app.ui.theme.EdgeOreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** The Mine session panel: Start Mining while idle, Stop while active, the pickaxe swinging only in ACTIVE. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "w360dp-h2000dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiningSessionPanelTest {
    @get:Rule val rule = createComposeRule()

    private val notConfigured = listOf(
        PartView(PartKind.NODE_PROOF, PartStatus.NotConfigured("Not configured: pair a node (Nodes tab).")),
        PartView(PartKind.GATEWAY_ROUND_TRIP, PartStatus.NotConfigured("Not configured: set up your storage gateway (Storage tab).")),
    )
    private val sent = Measurement("Sent to your gateway", 262_205, "bytes", "counted by this phone as written to the TLS stream")

    private var starts = 0
    private var stops = 0
    private var view by mutableStateOf(SessionView(parts = notConfigured))

    private fun show() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EdgeOreTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MiningSessionPanel(view, edgeModeResumed = true, headline = "", onStart = { starts++ }, onStop = { stops++ }, onToggleEdgeMode = {})
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun pose() = rule.onNodeWithTag("mining-animation").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    private fun texts() = rule.onAllNodesWithText("", substring = true).fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }

    @Test fun idleShowsAClickableStartMiningAndARestingPickaxe() {
        show()
        rule.onNodeWithText("Mine").assertHasClickAction().assertIsEnabled().performClick()
        assertEquals(1, starts)
        assertEquals(MiningPose.RESTING.name, pose())
        rule.onNodeWithText(SESSION_EXPLAINER).assertExists()
        assertTrue(SESSION_EXPLAINER.contains("Nothing is earned: no ORE, SKR, points or payment."))
        assertEquals(0, rule.onAllNodesWithText("Stop").fetchSemanticsNodes().size)
    }

    @Test fun notConfiguredPartsSayWhatToSetUpAndShowNoNumbers() {
        show()
        rule.onNodeWithText("Not configured: pair a node (Nodes tab).").assertExists()
        rule.onNodeWithText("Not configured: set up your storage gateway (Storage tab).").assertExists()
        val all = texts()
        assertFalse("no measurement line without a source: $all", all.any { it.contains(" bytes") || it.contains(" ms ·") || it.contains(" · this phone") })
    }

    @Test fun activeSwingsShowsStopAndOnlyReportedMeasurements() {
        view = SessionView(SessionPhase.ACTIVE, listOf(PartView(PartKind.NODE_PROOF, PartStatus.Running), PartView(PartKind.GATEWAY_ROUND_TRIP, PartStatus.Waiting, listOf(sent), rounds = 1)))
        show()
        assertEquals(MiningPose.SWINGING.name, pose())
        rule.onNodeWithText("Stop").assertHasClickAction().performClick()
        assertEquals(1, stops)
        assertEquals(0, rule.onAllNodesWithText("Mine").fetchSemanticsNodes().size)
        rule.onNodeWithText("Sent to your gateway: 262,205 bytes · counted by this phone as written to the TLS stream").assertExists()
        val lines = texts().filter { it.contains(" · ") && (it.contains("bytes") || it.endsWith("ms")) }
        assertEquals("exactly the one reported measurement is shown: $lines", 1, lines.size)
    }

    @Test fun startingStoppingAndFailedDoNotSwing() {
        view = SessionView(SessionPhase.STARTING, notConfigured)
        show()
        assertEquals(MiningPose.RESTING.name, pose())
        rule.onNodeWithText("Stop").assertExists()
        view = SessionView(SessionPhase.STOPPING, notConfigured); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeByFrame()
        assertEquals(MiningPose.RESTING.name, pose())
        rule.onNodeWithTag("stopping-mining").assertIsNotEnabled()
        view = SessionView(SessionPhase.FAILED, notConfigured, failure = "Not started: an unmetered network (Wi-Fi) is not observed"); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeByFrame()
        assertEquals(MiningPose.RESTING.name, pose())
        rule.onNodeWithText("Not started: an unmetered network (Wi-Fi) is not observed").assertExists()
        rule.onNodeWithText("Mine").assertHasClickAction()
    }
    @Test fun activeWithoutWorkerResultKeepsPickaxeStill() {
        view = SessionView(SessionPhase.ACTIVE, listOf(PartView(PartKind.NODE_PROOF, PartStatus.Running)))
        show()
        assertEquals(MiningPose.RESTING.name, pose())
        rule.onNodeWithText("Waiting for first worker result…").assertExists()
    }
}
