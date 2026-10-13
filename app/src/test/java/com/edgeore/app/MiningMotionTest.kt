package com.edgeore.app

import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.ui.components.MiningMotion
import com.edgeore.app.ui.components.MiningPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Mine pickaxe swings only while a real contribution session is ACTIVE and never runs on its own. */
class MiningMotionTest {
    @Test fun swingsOnlyWhileASessionIsActive() {
        for (p in SessionPhase.entries) {
            val on = p == SessionPhase.ACTIVE
            assertEquals(p.name, if (on) MiningPose.SWINGING else MiningPose.RESTING, MiningMotion.pose(p, reducedMotion = false))
            assertEquals(p.name, if (on) MiningPose.STILL_ON else MiningPose.RESTING, MiningMotion.pose(p, reducedMotion = true))
        }
    }

    @Test fun idleStartingStoppingAndFailedRest() {
        // The owner exception (swinging while Edge Mode was merely on) is gone: no session, no swing.
        for (p in listOf(
            SessionPhase.IDLE,
            SessionPhase.STARTING,
            SessionPhase.STOPPING,
            SessionPhase.FAILED,
        )) {
            assertEquals(MiningPose.RESTING, MiningMotion.pose(p, false))
        }
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
            prev = a
            i++
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
        assertTrue(MiningMotion.description(MiningPose.SWINGING).lowercase().contains("nothing is earned"))
        assertTrue(MiningMotion.description(MiningPose.RESTING).contains("no contribution session is running"))
    }
}
