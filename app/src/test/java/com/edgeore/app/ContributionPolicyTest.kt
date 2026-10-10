package com.edgeore.app

import com.edgeore.app.contribution.ConstraintObservation
import com.edgeore.app.contribution.ContributionPolicy
import com.edgeore.app.contribution.JobState
import com.edgeore.app.contribution.RunOutcome
import com.edgeore.app.contribution.SchedulerStatus
import com.edgeore.app.device.DeviceSnapshot
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.ResourceSettings
import com.edgeore.app.device.ThermalLevel
import com.edgeore.app.settings.Control
import com.edgeore.app.settings.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Constraint and state logic of the opt-in contribution scheduler. JVM only; no device or WorkManager run. */
class ContributionPolicyTest {
    private val on = ResourceSettings(edgeModeResumed = true, contributionOptIn = true)
    private val healthy = DeviceSnapshot(80, true, ThermalLevel.NONE, 0, batteryLow = false)
    private val allMet = ConstraintObservation(unmetered = true, charging = true, batteryLow = false)
    private fun view(s: ResourceSettings = on, d: DeviceSnapshot? = healthy, o: ConstraintObservation = allMet, j: JobState = JobState.ENQUEUED) =
        ContributionPolicy.view(s, d, o, j)

    @Test fun offByDefaultAndNotScheduled() {
        assertFalse(ResourceSettings().contributionOptIn)
        assertFalse(ContributionPolicy.shouldSchedule(ResourceSettings()))
        assertFalse(ContributionPolicy.shouldSchedule(ResourceSettings(edgeModeResumed = true)))
        assertEquals(SchedulerStatus.NOT_SCHEDULED, view(s = ResourceSettings(), j = JobState.NONE).status)
        // Even if a stale job were somehow present, opt-out wins.
        assertEquals(SchedulerStatus.NOT_SCHEDULED, view(s = ResourceSettings(edgeModeResumed = true)).status)
    }

    @Test fun userPauseWinsOverEverything() {
        val paused = on.copy(edgeModeResumed = false)
        assertFalse(ContributionPolicy.shouldSchedule(paused))
        assertEquals(SchedulerStatus.PAUSED_BY_YOU, view(s = paused).status)
        assertEquals(RunOutcome.SKIPPED_PAUSED_BY_YOU, ContributionPolicy.decide(paused, healthy))
        assertTrue(ContributionPolicy.shouldSchedule(on))
    }

    @Test fun waitsForWifi() {
        assertEquals(SchedulerStatus.WAITING_FOR_WIFI, view(o = allMet.copy(unmetered = false)).status)
        // Not observed is not "met".
        assertEquals(SchedulerStatus.WAITING_FOR_WIFI, view(o = allMet.copy(unmetered = null)).status)
    }

    @Test fun waitsForCharger() {
        assertEquals(SchedulerStatus.WAITING_FOR_CHARGER, view(o = allMet.copy(charging = false)).status)
        assertEquals(SchedulerStatus.WAITING_FOR_CHARGER, view(o = allMet.copy(charging = null)).status)
        // Wi-Fi is reported first when both are missing.
        assertEquals(SchedulerStatus.WAITING_FOR_WIFI, view(o = ConstraintObservation(false, false, false)).status)
    }

    @Test fun waitsForBatteryNotLow() {
        assertEquals(SchedulerStatus.WAITING_FOR_BATTERY, view(o = allMet.copy(batteryLow = true)).status)
    }

    @Test fun existingSafetyGatesStillApply() {
        val hot = healthy.copy(thermal = ThermalLevel.SEVERE)
        assertEquals(SchedulerStatus.PAUSED_BY_POLICY, view(d = hot).status)
        assertEquals(RunOutcome.SKIPPED_BY_POLICY, ContributionPolicy.decide(on, hot))
        assertEquals(SchedulerStatus.PAUSED_BY_POLICY, view(d = null).status)
        assertEquals(RunOutcome.SKIPPED_BY_POLICY, ContributionPolicy.decide(on, null))
        val lowReserve = on.copy(batteryReservePercent = 90)
        assertEquals(SchedulerStatus.PAUSED_BY_POLICY, view(s = lowReserve).status)
    }

    @Test fun qualificationGateMeansNothingRuns() {
        assertFalse(EdgePolicy.QUALIFIED_WORKLOAD_AVAILABLE)
        assertEquals(0, ContributionPolicy.REGISTERED_WORKLOADS)
        val v = view()
        assertEquals(SchedulerStatus.NO_QUALIFIED_WORKLOAD, v.status)
        assertTrue(v.detail.contains("Protocol qualification required"))
        assertEquals(RunOutcome.NO_QUALIFIED_WORKLOAD, ContributionPolicy.decide(on, healthy))
        // Every state reachable in this build: none of them is "checking"/running work.
        for (j in JobState.entries) for (u in listOf(true, false, null)) for (c in listOf(true, false, null)) for (b in listOf(true, false, null))
            for (d in listOf(healthy, null)) for (s in listOf(on, on.copy(edgeModeResumed = false), ResourceSettings()))
                assertNotEquals(SchedulerStatus.CHECKING, ContributionPolicy.view(s, d, ConstraintObservation(u, c, b), j).status)
    }

    @Test fun schedulerHealthIsReportedNotAssumed() {
        assertEquals(SchedulerStatus.SCHEDULER_UNAVAILABLE, view(j = JobState.UNAVAILABLE).status)
        assertEquals(SchedulerStatus.NOT_SCHEDULED, view(j = JobState.NONE).status)
        assertEquals(SchedulerStatus.NOT_SCHEDULED, view(j = JobState.CANCELLED).status)
    }

    @Test fun jobOutcomesNeverClaimWork() {
        assertEquals(RunOutcome.SKIPPED_NOT_OPTED_IN, ContributionPolicy.decide(ResourceSettings(edgeModeResumed = true), healthy))
        for (o in RunOutcome.entries) assertTrue(o.label, o.label.startsWith("Skipped") || o.label.contains("Nothing ran"))
        assertEquals(RunOutcome.NO_QUALIFIED_WORKLOAD, RunOutcome.fromCode("no_qualified_workload"))
        assertEquals(null, RunOutcome.fromCode("done"))
    }

    @Test fun constraintLinesSayNotObserved() {
        assertEquals("Charging: not observed", ContributionPolicy.constraintLine("Charging", null))
        assertEquals("Charging: met", ContributionPolicy.constraintLine("Charging", true))
        assertEquals(ConstraintObservation.NOT_OBSERVED, ConstraintObservation.of(null, null))
    }

    @Test fun controlIsLabelledAndCopyMakesNoRewardClaims() {
        assertEquals(Effect.ENFORCED, Control.CONTRIBUTION_SCHEDULER.effect)
        val banned = Regex("""(?i)\b(reward|earn|earning|earnings|apr|apy|boost|yield|zero[- ]knowledge|income|profit)\b""")
        val texts = SchedulerStatus.entries.map { it.label } + RunOutcome.entries.map { it.label } + Control.CONTRIBUTION_SCHEDULER.detail +
            File("src/main/java/com/edgeore/app/contribution").listFiles()!!.map { it.readText() }
        for (t in texts) assertFalse("banned wording in: ${t.take(80)}", banned.containsMatchIn(t))
    }
}
