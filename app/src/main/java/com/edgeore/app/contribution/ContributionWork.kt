package com.edgeore.app.contribution

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.edgeore.app.device.DeviceObservations
import com.edgeore.app.device.ResourceSettingsStore
import java.util.concurrent.TimeUnit

/** WorkManager wiring for the opt-in contribution scheduler. */
object ContributionScheduling {
    const val UNIQUE_NAME = "edgeore.contribution"
    const val OUTPUT_OUTCOME = "outcome"

    /** Android starts the job only on an unmetered network, while charging, with battery not low. */
    fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresCharging(true)
        .setRequiresBatteryNotLow(true)
        .build()

    fun request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<ContributionWorker>(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(constraints())
            .addTag(UNIQUE_NAME)
            .build()

    /** Enqueue when opted in and resumed; cancel otherwise. Returns false if WorkManager could not be reached. */
    fun apply(context: Context, schedule: Boolean): Boolean = runCatching {
        val wm = WorkManager.getInstance(context)
        if (schedule) wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request())
        else wm.cancelUniqueWork(UNIQUE_NAME)
        true
    }.getOrElse { Log.w(TAG, "scheduler_apply_failed schedule=$schedule exception=${it.javaClass.name}"); false }

    fun jobState(infos: List<WorkInfo>?): JobState {
        if (infos == null) return JobState.UNAVAILABLE
        val live = infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull() ?: return JobState.NONE
        return when (live.state) {
            WorkInfo.State.ENQUEUED -> JobState.ENQUEUED
            WorkInfo.State.RUNNING -> JobState.RUNNING
            WorkInfo.State.BLOCKED -> JobState.BLOCKED
            WorkInfo.State.CANCELLED -> JobState.CANCELLED
            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> JobState.FINISHED
        }
    }

    internal const val TAG = "EdgeORE.Contribution"
}

/** Last outcome the job recorded, kept in the same settings file. Not a receipt; it says nothing ran. */
data class LastRun(val outcome: RunOutcome, val atMillis: Long)

object LastRunStore {
    private const val OUTCOME = "sched.lastOutcome"
    private const val AT = "sched.lastAt"
    fun read(prefs: SharedPreferences): LastRun? {
        val o = RunOutcome.fromCode(prefs.getString(OUTCOME, null)) ?: return null
        val at = prefs.getLong(AT, -1).takeIf { it > 0 } ?: return null
        return LastRun(o, at)
    }
    fun write(prefs: SharedPreferences, run: LastRun) {
        prefs.edit().putString(OUTCOME, run.outcome.code).putLong(AT, run.atMillis).apply()
    }
}

/**
 * The scheduled job. It re-reads the user's settings and the device gates, records what it decided,
 * and exits. It never runs a workload EdgeORE does not have and never reports progress.
 */
class ContributionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = ResourceSettingsStore.prefs(applicationContext)
        val settings = ResourceSettingsStore.load(prefs)
        val snapshot = runCatching { DeviceObservations.read(applicationContext) }.getOrNull()
        val outcome = ContributionPolicy.decide(settings, snapshot)
        LastRunStore.write(prefs, LastRun(outcome, System.currentTimeMillis()))
        if (!ContributionPolicy.shouldSchedule(settings)) ContributionScheduling.apply(applicationContext, schedule = false)
        Log.i(ContributionScheduling.TAG, "contribution_check outcome=${outcome.code}")
        // Success means "the check finished", not "work was done". Retrying would only repeat the same check.
        return Result.success(workDataOf(ContributionScheduling.OUTPUT_OUTCOME to outcome.code))
    }
}
