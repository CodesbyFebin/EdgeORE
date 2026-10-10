package com.edgeore.app.contribution

import com.edgeore.app.device.DeviceSnapshot
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.ResourceSettings

/**
 * Pure decision logic for the opt-in contribution scheduler. No Android or WorkManager types, so it is
 * unit-tested directly. Android enforces the same three constraints for the scheduled job
 * (see [ContributionScheduling.constraints]); this logic only explains them truthfully in the UI and
 * re-checks EdgeORE's own gates when the job runs.
 */

/** What EdgeORE observed about the scheduler's constraints. Null means "not observed", never "met". */
data class ConstraintObservation(
    /** NET_CAPABILITY_NOT_METERED on the active network (Wi-Fi or other unmetered network). */
    val unmetered: Boolean?,
    val charging: Boolean?,
    /** BatteryManager.EXTRA_BATTERY_LOW (API 28+). */
    val batteryLow: Boolean?,
) {
    companion object {
        val NOT_OBSERVED = ConstraintObservation(null, null, null)
        fun of(snapshot: DeviceSnapshot?, unmetered: Boolean?) =
            ConstraintObservation(unmetered, snapshot?.charging, snapshot?.batteryLow)
    }
}

/** WorkManager's state for the unique job, as far as EdgeORE could read it. */
enum class JobState { NONE, ENQUEUED, RUNNING, BLOCKED, CANCELLED, FINISHED, UNAVAILABLE }

enum class SchedulerStatus(val label: String) {
    NOT_SCHEDULED("Not scheduled"),
    PAUSED_BY_YOU("Paused by you"),
    SCHEDULER_UNAVAILABLE("Scheduler unavailable"),
    WAITING_FOR_WIFI("Waiting for Wi-Fi"),
    WAITING_FOR_CHARGER("Waiting for charger"),
    WAITING_FOR_BATTERY("Waiting for battery"),
    PAUSED_BY_POLICY("Paused by policy"),
    NO_QUALIFIED_WORKLOAD("No qualified workload"),
    CHECKING("Checking"),
}

data class SchedulerView(val status: SchedulerStatus, val detail: String)

/** Outcome recorded by one run of the scheduled job. None of these means work was done. */
enum class RunOutcome(val code: String, val label: String) {
    SKIPPED_NOT_OPTED_IN("not_opted_in", "Skipped: scheduler is off"),
    SKIPPED_PAUSED_BY_YOU("paused_by_you", "Skipped: paused by you"),
    SKIPPED_BY_POLICY("policy", "Skipped: blocked by a safety control"),
    NO_QUALIFIED_WORKLOAD("no_qualified_workload", "No qualified workload. Nothing ran."),
    ;
    companion object { fun fromCode(c: String?): RunOutcome? = entries.firstOrNull { it.code == c } }
}

object ContributionPolicy {
    /** Workloads EdgeORE really has for this job. None in this build: no AI training, no mining, no compute job. */
    const val REGISTERED_WORKLOADS = 0

    /** Schedule the job only when the user opted in AND has Edge Mode resumed. Otherwise cancel it. */
    fun shouldSchedule(s: ResourceSettings): Boolean = s.contributionOptIn && s.edgeModeResumed

    /** Status line for the Mine screen. Order: user choices, then scheduler health, then Android constraints, then EdgeORE gates. */
    fun view(
        s: ResourceSettings,
        snapshot: DeviceSnapshot?,
        observed: ConstraintObservation,
        job: JobState,
        qualifiedWorkload: Boolean = EdgePolicy.QUALIFIED_WORKLOAD_AVAILABLE && REGISTERED_WORKLOADS > 0,
    ): SchedulerView {
        if (!s.contributionOptIn) return SchedulerView(SchedulerStatus.NOT_SCHEDULED, "Off by default. Opt in to let Android schedule a contribution check.")
        if (!s.edgeModeResumed) return SchedulerView(SchedulerStatus.PAUSED_BY_YOU, "Edge Mode is paused, so no job is scheduled.")
        when (job) {
            JobState.UNAVAILABLE -> return SchedulerView(SchedulerStatus.SCHEDULER_UNAVAILABLE, "Android's job scheduler could not be read. Nothing is scheduled as far as EdgeORE can tell.")
            JobState.NONE, JobState.CANCELLED, JobState.FINISHED -> return SchedulerView(SchedulerStatus.NOT_SCHEDULED, "Opted in, but no scheduled job was found.")
            else -> Unit
        }
        when (observed.unmetered) {
            false -> return SchedulerView(SchedulerStatus.WAITING_FOR_WIFI, "Android runs the job only on an unmetered network (Wi-Fi).")
            null -> return SchedulerView(SchedulerStatus.WAITING_FOR_WIFI, "Unmetered network not observed.")
            true -> Unit
        }
        when (observed.charging) {
            false -> return SchedulerView(SchedulerStatus.WAITING_FOR_CHARGER, "Android runs the job only while charging.")
            null -> return SchedulerView(SchedulerStatus.WAITING_FOR_CHARGER, "Charging state not observed.")
            true -> Unit
        }
        if (observed.batteryLow == true) return SchedulerView(SchedulerStatus.WAITING_FOR_BATTERY, "Android reports battery low.")
        val eval = EdgePolicy.evaluate(s, snapshot)
        eval.gates.firstOrNull { !it.allowed }?.let { return SchedulerView(SchedulerStatus.PAUSED_BY_POLICY, "${it.name}: ${it.explanation}") }
        if (!qualifiedWorkload) return SchedulerView(SchedulerStatus.NO_QUALIFIED_WORKLOAD, "Protocol qualification required. A scheduled check runs nothing in this build.")
        return SchedulerView(SchedulerStatus.CHECKING, if (job == JobState.RUNNING) "Job running." else "Waiting for Android to start the job.")
    }

    /** What the job does when Android starts it. It re-checks the user's choices and EdgeORE's gates; it never runs a workload that does not exist. */
    fun decide(
        s: ResourceSettings,
        snapshot: DeviceSnapshot?,
    ): RunOutcome = when {
        !s.contributionOptIn -> RunOutcome.SKIPPED_NOT_OPTED_IN
        !s.edgeModeResumed -> RunOutcome.SKIPPED_PAUSED_BY_YOU
        !EdgePolicy.evaluate(s, snapshot).gatesPass -> RunOutcome.SKIPPED_BY_POLICY
        // This build has no workload adapter (REGISTERED_WORKLOADS == 0, QUALIFIED_WORKLOAD_AVAILABLE == false).
        // Adding one needs a new outcome with its own tests; until then the job reports this and exits.
        else -> RunOutcome.NO_QUALIFIED_WORKLOAD
    }

    fun constraintLine(name: String, met: Boolean?): String = name + ": " + when (met) { true -> "met"; false -> "not met"; null -> "not observed" }
}
