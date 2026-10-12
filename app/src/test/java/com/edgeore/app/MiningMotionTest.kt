package com.edgeore.app

import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.EdgeState
import com.edgeore.app.device.ResourceSettings
import com.edgeore.app.ui.components.MiningMotion
import com.edgeore.app.ui.components.MiningPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Mine pickaxe animation follows the evaluated Edge Mode state and never runs on its own. */
class MiningMotionTest {
    @Test fun posesFollowTheRealEdgeState() {
        for (s in EdgeState.entries) {
            val on = s == EdgeState.IDLE || s == EdgeState.STARTING || s == EdgeState.ACTIVE
            assertEquals(s.name, if (on) MiningPose.SWINGING else MiningPose.RESTING, MiningMotion.pose(s, reducedMotion = false))
            assertEquals(s.name, if (on) MiningPose.STILL_ON else MiningPose.RESTING, MiningMotion.pose(s, reducedMotion = true))
        }
    }

    @Test fun freshInstallAndPausedRest() {
        // Fresh install: Edge Mode is paused by default, so the art rests.
        assertEquals(MiningPose.RESTING, MiningMotion.pose(EdgePolicy.evaluate(ResourceSettings(), null).state, false))
        // Resumed but gates cannot be observed: paused by policy, still resting.
        assertEquals(MiningPose.RESTING, MiningMotion.pose(EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true), null).state, false))
    }

    @Test fun swingIsContinuousAndStrikesAtImpact() {
        assertEquals(MiningMotion.swingDegrees(0f), MiningMotion.swingDegrees(0.99999f), 0.01f)
        assertEquals(0f, MiningMotion.swingDegrees(MiningMotion.IMPACT), 0.001f)
        assertEquals(MiningMotion.WIND_UP_DEG, MiningMotion.swingDegrees(0.45f), 0.001f)
        var prev = MiningMotion.swingDegrees(0f)
        var i = 1
        while (i <= 1000) {
            val a = MiningMotion.swingDegrees(i / 1000f)
            assertTrue("jump at $i", kotlin.math.abs(a - prev) < 6f)
            assertTrue(a >= 0f && a <= MiningMotion.WIND_UP_DEG)
            prev = a; i++
        }
        // The strike (wind-up peak to contact) is faster than the wind-up.
        assertTrue((MiningMotion.IMPACT - 0.45f) < 0.45f / 2)
    }

    @Test fun impactEffectsOnlyAfterTheStrike() {
        assertEquals(0f, MiningMotion.glow(0.3f), 0f)
        assertEquals(0f, MiningMotion.shake(0.5f), 0f)
        assertNull(MiningMotion.sparks(0.59f))
        assertEquals(1f, MiningMotion.glow(MiningMotion.IMPACT), 0.001f)
        assertTrue(MiningMotion.glow(0.9f) < 0.1f)
        assertEquals(0f, MiningMotion.sparks(MiningMotion.IMPACT)!!, 0.001f)
        assertNull(MiningMotion.sparks(0.9f))
        assertEquals(0f, MiningMotion.shake(0.9f), 0f)
    }

    @Test fun descriptionsMakeNoEarningsClaim() {
        for (p in MiningPose.entries) {
            val d = MiningMotion.description(p).lowercase()
            for (word in listOf("ore ", "skr", "reward", "income", "earning ")) assertFalse("$p: $d", d.contains(word))
        }
        assertTrue(MiningMotion.description(MiningPose.SWINGING).contains("nothing is earned"))
        assertTrue(MiningMotion.description(MiningPose.RESTING).contains("paused"))
    }
}
