package com.edgeore.app

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.edgeore.app.contribution.ContributionScheduling
import com.edgeore.app.contribution.ContributionWorker
import com.edgeore.app.contribution.JobState
import com.edgeore.app.contribution.LastRunStore
import com.edgeore.app.contribution.RunOutcome
import com.edgeore.app.device.ResourceSettings
import com.edgeore.app.device.ResourceSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * WorkManager wiring under Robolectric with WorkManager's own test driver. This proves the request's
 * constraints and the job's decisions on the JVM; it is not a phone run and not Android's real JobScheduler.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class ContributionWorkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = ResourceSettingsStore.prefs(context)

    @Before fun setUp() {
        prefs.edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    private fun charging(percent: Int) {
        @Suppress("DEPRECATION")
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, percent).putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            .putExtra(BatteryManager.EXTRA_BATTERY_LOW, false))
    }

    private fun runWorker(): RunOutcome {
        val result = runBlocking { TestListenableWorkerBuilder<ContributionWorker>(context).build().doWork() }
        assertTrue(result is ListenableWorker.Result.Success)
        return RunOutcome.fromCode(result.outputData.getString(ContributionScheduling.OUTPUT_OUTCOME))!!
    }

    private fun infos() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ContributionScheduling.UNIQUE_NAME).get()

    @Test fun requestRequiresUnmeteredChargingAndBatteryNotLow() {
        val c = ContributionScheduling.constraints()
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
        assertTrue(c.requiresCharging())
        assertTrue(c.requiresBatteryNotLow())
        assertEquals(c, ContributionScheduling.request().workSpec.constraints)
    }

    @Test fun workerSkipsWhenNotOptedIn() {
        assertNull(LastRunStore.read(prefs))
        assertEquals(RunOutcome.SKIPPED_NOT_OPTED_IN, runWorker())
        assertEquals(RunOutcome.SKIPPED_NOT_OPTED_IN, LastRunStore.read(prefs)!!.outcome)
    }

    @Test fun workerRespectsUserPause() {
        ResourceSettingsStore.save(prefs, ResourceSettings(contributionOptIn = true, edgeModeResumed = false))
        assertEquals(RunOutcome.SKIPPED_PAUSED_BY_YOU, runWorker())
    }

    @Test fun workerRespectsSafetyGates() {
        // API 28 Robolectric: thermal status is not observable, so the thermal guard fails closed.
        charging(80)
        ResourceSettingsStore.save(prefs, ResourceSettings(contributionOptIn = true, edgeModeResumed = true))
        assertEquals(RunOutcome.SKIPPED_BY_POLICY, runWorker())
    }

    @Test fun workerRunsNothingWithoutQualifiedWorkload() {
        charging(80)
        ResourceSettingsStore.save(prefs, ResourceSettings(contributionOptIn = true, edgeModeResumed = true, thermalGuard = false))
        assertEquals(RunOutcome.NO_QUALIFIED_WORKLOAD, runWorker())
        assertEquals(RunOutcome.NO_QUALIFIED_WORKLOAD, LastRunStore.read(prefs)!!.outcome)
    }

    @Test fun jobDoesNotRunUntilConstraintsAreMetThenOnlyReports() {
        charging(80)
        ResourceSettingsStore.save(prefs, ResourceSettings(contributionOptIn = true, edgeModeResumed = true, thermalGuard = false))
        assertTrue(ContributionScheduling.apply(context, schedule = true))
        val id = infos().single().id
        assertEquals(JobState.ENQUEUED, ContributionScheduling.jobState(infos()))
        assertNull("constraints unmet: the job must not have run", LastRunStore.read(prefs))

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(id)
        assertEquals(RunOutcome.NO_QUALIFIED_WORKLOAD, LastRunStore.read(prefs)!!.outcome)
        // Periodic job: back to ENQUEUED for the next window, not "succeeded with work".
        assertEquals(WorkInfo.State.ENQUEUED, infos().single().state)
    }

    @Test fun optOutOrPauseCancelsTheJob() {
        assertTrue(ContributionScheduling.apply(context, schedule = true))
        assertEquals(JobState.ENQUEUED, ContributionScheduling.jobState(infos()))
        assertTrue(ContributionScheduling.apply(context, schedule = false))
        assertEquals(JobState.CANCELLED, ContributionScheduling.jobState(infos()))
        assertEquals(JobState.NONE, ContributionScheduling.jobState(emptyList()))
        assertEquals(JobState.UNAVAILABLE, ContributionScheduling.jobState(null))
    }

    @Test fun workerCancelsItsOwnJobIfUserPausedMeanwhile() {
        assertTrue(ContributionScheduling.apply(context, schedule = true))
        ResourceSettingsStore.save(prefs, ResourceSettings(contributionOptIn = true, edgeModeResumed = false))
        val id = infos().single().id
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(id)
        assertEquals(RunOutcome.SKIPPED_PAUSED_BY_YOU, LastRunStore.read(prefs)!!.outcome)
        assertEquals(JobState.CANCELLED, ContributionScheduling.jobState(infos()))
    }

    @Test fun settingsRoundTripIncludesOptIn() {
        assertEquals(ResourceSettings(), ResourceSettingsStore.load(prefs))
        val s = ResourceSettings(edgeModeResumed = true, contributionOptIn = true, batteryReservePercent = 30)
        ResourceSettingsStore.save(prefs, s)
        assertEquals(s, ResourceSettingsStore.load(prefs))
    }
}
