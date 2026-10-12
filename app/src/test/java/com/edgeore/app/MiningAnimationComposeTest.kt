package com.edgeore.app

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.device.EdgeState
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

    private fun show(state: EdgeState, reduced: Boolean?) {
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
        show(EdgeState.IDLE, reduced = false)
        assertEquals(MiningPose.SWINGING.name, pose())
        val seen = mutableSetOf<Int>()
        repeat(12) { rule.mainClock.advanceTimeBy(83); seen += swing().toInt() }
        assertNotEquals("the pickaxe moves while Edge Mode is on", 1, seen.size)
        val d = node()[SemanticsProperties.ContentDescription].single()
        assertEquals(MiningMotion.description(MiningPose.SWINGING), d)
    }

    @Test fun pausedRestsWithoutMotion() {
        show(EdgeState.PAUSED, reduced = false)
        assertEquals(MiningPose.RESTING.name, pose())
        repeat(6) { rule.mainClock.advanceTimeBy(170); assertEquals(MiningMotion.REST_DEG, swing(), 0f) }
    }

    @Test fun reducedMotionHoldsAStaticFrame() {
        show(EdgeState.IDLE, reduced = true)
        assertEquals(MiningPose.STILL_ON.name, pose())
        repeat(6) { rule.mainClock.advanceTimeBy(170); assertEquals(0f, swing(), 0f) }
    }

    @Test fun removeAnimationsSettingIsRead() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            var reduced = false
            rule.setContent { reduced = rememberReducedMotion(); MiningAnimation(EdgeState.IDLE, Modifier.size(280.dp, 210.dp)) }
            rule.waitForIdle()
            assertEquals(true, reduced)
            assertEquals(MiningPose.STILL_ON.name, pose())
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    /**
     * Preview frames of the real composable, written only when MINE_FRAMES_OUT is set (not part of the gate):
     *   MINE_FRAMES_OUT=/workspace/mine-animation/frames ./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true \
 *     --tests '*MiningAnimationComposeTest.frames'
     */
    @Config(sdk = [28], qualifiers = "xxhdpi")
    @Test fun frames() {
        val out = System.getenv("MINE_FRAMES_OUT")
        assumeTrue(!out.isNullOrBlank())
        val dir = File(out!!).apply { mkdirs() }
        // Roborazzi capture (Compose's captureToImage times out under Robolectric). Needs record mode on.
        fun save(name: String) = rule.onRoot().captureRoboImage(File(dir, "$name.png").path)
        var state by androidx.compose.runtime.mutableStateOf(EdgeState.PAUSED)
        var phase by androidx.compose.runtime.mutableStateOf(0f)
        rule.setContent {
            EdgeOreTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.edgeore.app.ui.components.LocalMiningPhaseOverride provides phase) {
                    androidx.compose.foundation.layout.Box(Modifier.size(280.dp, 210.dp).background(com.edgeore.app.ui.theme.EdgeColors.surface)) {
                        MiningAnimation(state, Modifier.size(280.dp, 210.dp), reducedMotion = false)
                    }
                }
            }
        }
        rule.waitForIdle()
        save("idle")
        state = EdgeState.IDLE
        // One cycle at 25 fps; the PNGs are the phase the running clock would show at each 40 ms step.
        for (i in 0 until 25) {
            phase = i / 25f
            rule.waitForIdle()
            save("cycle-%02d".format(i))
        }
    }
}
