package com.edgeore.app

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.ui.components.MiningAnimation
import com.edgeore.app.ui.components.MiningMotion
import com.edgeore.app.ui.components.MiningPose
import com.edgeore.app.ui.components.MiningSwingDegreesKey
import com.edgeore.app.ui.components.rememberReducedMotion
import com.edgeore.app.ui.theme.EdgeOreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Compose behaviour of the Mine art: swinging, paused and reduced-motion states (Robolectric, JVM only). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiningAnimationComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun node() = rule.onNodeWithTag("mining-animation").fetchSemanticsNode().config
    private fun swing() = node()[MiningSwingDegreesKey]
    private fun pose() = node().getOrNull(SemanticsProperties.StateDescription)

    private fun show(state: SessionPhase, reduced: Boolean?) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EdgeOreTheme {
                if (reduced == null) MiningAnimation(state, Modifier.size(280.dp, 210.dp))
                else MiningAnimation(state, Modifier.size(280.dp, 210.dp), reducedMotion = reduced)
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test fun runningSwings() {
        show(SessionPhase.ACTIVE, reduced = false)
        assertEquals(MiningPose.SWINGING.name, pose())
        val seen = mutableSetOf<Int>()
        repeat(12) { rule.mainClock.advanceTimeBy(83); seen += swing().toInt() }
        assertNotEquals("the pickaxe moves while a session is active", 1, seen.size)
        val d = node()[SemanticsProperties.ContentDescription].single()
        assertEquals(MiningMotion.description(MiningPose.SWINGING), d)
    }

    @Test fun pausedRestsWithoutMotion() {
        show(SessionPhase.IDLE, reduced = false)
        assertEquals(MiningPose.RESTING.name, pose())
        repeat(6) { rule.mainClock.advanceTimeBy(170); assertEquals(MiningMotion.REST_DEG, swing(), 0f) }
    }

    @Test fun reducedMotionHoldsAStaticFrame() {
        show(SessionPhase.ACTIVE, reduced = true)
        assertEquals(MiningPose.STILL_ON.name, pose())
        repeat(6) { rule.mainClock.advanceTimeBy(170); assertEquals(0f, swing(), 0f) }
    }

    @Test fun removeAnimationsSettingIsRead() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            var reduced = false
            rule.setContent { reduced = rememberReducedMotion(); MiningAnimation(SessionPhase.ACTIVE, Modifier.size(280.dp, 210.dp)) }
            rule.waitForIdle()
            assertEquals(true, reduced)
            assertEquals(MiningPose.STILL_ON.name, pose())
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    /**
     * Preview frames of the real Mine session panel, written only when MINE_FRAMES_OUT is set (not part of the gate):
     * idle with Start Mining, then ACTIVE with the measurements captured from the real end-to-end session
     * (MINE_FRAMES_SESSION = evidence/contribution-session/e2e/active-snapshot.json). Nothing in the frames is invented.
     *   MINE_FRAMES_OUT=… MINE_FRAMES_SESSION=… ./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true --tests '*MiningAnimationComposeTest.frames'
     */
    @Config(sdk = [28], qualifiers = "w360dp-h1000dp-hdpi")
    @Test fun frames() {
        val out = System.getenv("MINE_FRAMES_OUT")
        val snap = System.getenv("MINE_FRAMES_SESSION")
        assumeTrue(!out.isNullOrBlank() && !snap.isNullOrBlank())
        val dir = File(out!!).apply { mkdirs() }
        val j = org.json.JSONObject(File(snap!!).readText())
        require(j.getString("phase") == "ACTIVE")
        val active = com.edgeore.app.contribution.SessionView(SessionPhase.ACTIVE, (0 until j.getJSONArray("parts").length()).map { i ->
            val p = j.getJSONArray("parts").getJSONObject(i)
            val ms = p.getJSONArray("measurements")
            com.edgeore.app.contribution.PartView(com.edgeore.app.contribution.PartKind.valueOf(p.getString("kind")),
                when (p.getString("status")) { "Running" -> com.edgeore.app.contribution.PartStatus.Running; "Waiting" -> com.edgeore.app.contribution.PartStatus.Waiting; else -> error(p.getString("status")) },
                (0 until ms.length()).map { k -> ms.getJSONObject(k).let { m -> com.edgeore.app.contribution.Measurement(m.getString("label"), m.getLong("value"), m.getString("unit"), m.getString("source")) } },
                rounds = p.getInt("rounds"))
        })
        val idle = com.edgeore.app.contribution.SessionView(SessionPhase.IDLE, active.parts.map { com.edgeore.app.contribution.PartView(it.kind, com.edgeore.app.contribution.PartStatus.Ready) })
        fun save(name: String) = rule.onRoot().captureRoboImage(File(dir, "$name.png").path)
        var view by androidx.compose.runtime.mutableStateOf(idle)
        var phase by androidx.compose.runtime.mutableStateOf(0f)
        rule.setContent {
            EdgeOreTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.edgeore.app.ui.components.LocalMiningPhaseOverride provides phase) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = com.edgeore.app.ui.theme.EdgeColors.background, contentColor = com.edgeore.app.ui.theme.EdgeColors.textPrimary) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                            com.edgeore.app.ui.screens.MiningSessionPanel(view, edgeModeResumed = true, headline = "", onStart = {}, onStop = {}, onToggleEdgeMode = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Start Mining").assertExists()
        save("idle")
        view = active
        rule.waitForIdle()
        rule.onNodeWithText("Stop").assertExists()
        for (i in 0 until 25) {
            phase = i / 25f
            rule.waitForIdle()
            save("active-%02d".format(i))
        }
    }
}
