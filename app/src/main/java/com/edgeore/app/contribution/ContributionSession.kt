package com.edgeore.app.contribution

import com.edgeore.app.receipts.Evidence
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** IDLE → STARTING → ACTIVE → STOPPING → IDLE, or FAILED with a reason. Only ACTIVE means a workload is running. */
enum class SessionPhase { IDLE, STARTING, ACTIVE, STOPPING, FAILED }

enum class PartKind(
    val title: String,
) {
    NODE_PROOF("Proof job on your paired node"),
    GATEWAY_ROUND_TRIP("Encrypted chunk to, on and back from your storage gateway"),
}

/**
 * One number from a real observation. It cannot exist without a named [source] (the clock, counter or signed
 * record it came from); nothing in the session invents a value or shows zero for "not measured".
 */
data class Measurement(
    val label: String,
    val value: Long,
    val unit: String,
    val source: String,
) {
    init {
        require(label.isNotBlank() && unit.isNotBlank()) { "label and unit required" }
        require(source.isNotBlank()) { "a measurement needs a real source" }
        require(value >= 0) { "measurements are counts or durations" }
    }
}

sealed class PartStatus {
    /** The part cannot run: [setup] says what the owner has to configure. It is not counted. */
    data class NotConfigured(
        val setup: String,
    ) : PartStatus()

    data object Ready : PartStatus()

    data object Running : PartStatus()

    /** Between its rounds while the session's other part runs. */
    data object Waiting : PartStatus()

    data class Done(
        val detail: String,
    ) : PartStatus()

    data object Cancelled : PartStatus()

    data class Failed(
        val reason: String,
    ) : PartStatus()
}

data class PartView(
    val kind: PartKind,
    val status: PartStatus,
    val measurements: List<Measurement> = emptyList(),
    val rounds: Int = 0,
)

data class SessionView(
    val phase: SessionPhase = SessionPhase.IDLE,
    val parts: List<PartView> = emptyList(),
    val failure: String? = null,
    val note: String? = null,
    val sessionId: String? = null,
) {
    val anyConfigured: Boolean get() = parts.any { it.status !is PartStatus.NotConfigured }
}

/** Thrown by a part that turns out not to be configured on the remote side (for example a node without a proof job). */
class PartNotConfigured(
    message: String,
) : Exception(message)

/** One bounded, cancellable piece of a session on the owner's own equipment. A new instance is made per session. */
interface SessionPart {
    val kind: PartKind

    /** Null when the part can run, otherwise the setup step that is missing ("Not configured: …"). */
    fun notConfigured(): String?

    /**
     * Runs one round and returns a short description of it. [report] is called only with values observed in this
     * session (running totals); the same label replaces the last value.
     */
    suspend fun runRound(
        sessionId: String,
        round: Int,
        report: (Measurement) -> Unit,
    ): String

    /** Signed-receipt draft covering every round this part completed in the session, or null if none completed. */
    fun receipt(sessionId: String): ReceiptDraft?

    /** Best-effort remote cleanup after a stop. Runs non-cancellably. */
    suspend fun cancel(sessionId: String) {}
}

/**
 * The user-started contribution session behind the Mine screen's Start Mining button. It runs rounds of the configured
 * parts back to back (a round is each part once) while [blockedBy] stays null (Edge Mode on, gates pass, unmetered
 * network), checking that every [watchMs]. It ends on [stop], on a gate failure, when every part has failed, or at the
 * session limits ([maxRounds] rounds or [maxDurationMs]). Each part that completed a round records one signed receipt
 * with its measured totals, and every finished or stopped session records a summary receipt.
 */
class ContributionSession(
    private val scope: CoroutineScope,
    private val parts: () -> List<SessionPart>,
    private val blockedBy: () -> String?,
    private val record: suspend (ReceiptDraft) -> Unit,
    private val watchMs: Long = 2_000,
    val maxRounds: Int = MAX_ROUNDS,
    val maxDurationMs: Long = MAX_DURATION_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val newId: () -> String = { UUID.randomUUID().toString().replace("-", "") },
) {
    private val _view = MutableStateFlow(SessionView(parts = plan(parts())))
    val view: StateFlow<SessionView> = _view.asStateFlow()
    private var job: Job? = null

    @Volatile private var stopReason: String? = null

    @Volatile private var current: List<SessionPart> = emptyList()

    private fun plan(list: List<SessionPart>): List<PartView> =
        list.map { p ->
            PartView(p.kind, p.notConfigured()?.let { PartStatus.NotConfigured(it) } ?: PartStatus.Ready)
        }

    private val busy get() =
        _view.value.phase.let {
            it == SessionPhase.STARTING || it == SessionPhase.ACTIVE || it == SessionPhase.STOPPING
        }

    /** Re-reads which parts are configured. Ignored while a session runs. */
    fun refresh() {
        if (!busy) _view.update { it.copy(parts = plan(parts())) }
    }

    /** Starts a session. False when one is already running. */
    fun start(): Boolean {
        if (busy) return false
        val list = parts()
        current = list
        stopReason = null
        val id = newId()
        _view.value = SessionView(SessionPhase.STARTING, plan(list), sessionId = id)
        job =
            scope.launch {
                try {
                    run(id, list)
                } catch (c: CancellationException) {
                    withContext(NonCancellable) { finishStopped(id) }
                    throw c
                }
            }
        return true
    }

    /** Stops a starting or active session. The pickaxe stops as soon as the phase leaves ACTIVE. */
    fun stop(reason: String? = null) {
        if (_view.value.phase != SessionPhase.STARTING && _view.value.phase != SessionPhase.ACTIVE) return
        stopReason = reason
        _view.update { it.copy(phase = SessionPhase.STOPPING) }
        job?.cancel()
    }

    private fun setPart(
        kind: PartKind,
        f: (PartView) -> PartView,
    ) = _view.update { v -> v.copy(parts = v.parts.map { if (it.kind == kind) f(it) else it }) }

    private fun status(kind: PartKind) =
        _view.value.parts
            .first { it.kind == kind }
            .status

    private suspend fun run(
        id: String,
        list: List<SessionPart>,
    ) {
        blockedBy()?.let { why ->
            fail(id, "Not started: $why", recordIt = false)
            return
        }
        val runnable = list.filter { status(it.kind) == PartStatus.Ready }
        if (runnable.isEmpty()) {
            fail(id, "Nothing to run. " + list.mapNotNull { it.notConfigured() }.joinToString(" "), recordIt = false)
            return
        }
        _view.update { it.copy(phase = SessionPhase.ACTIVE) }
        val started = nowMs()
        var limit = "No part could continue"
        coroutineScope {
            val watchdog =
                launch {
                    while (true) {
                        delay(watchMs)
                        blockedBy()?.let { why -> stop("Stopped by policy: $why") }
                    }
                }
            var round = 1
            rounds@ while (true) {
                val live = runnable.filter { status(it.kind).let { s -> s == PartStatus.Ready || s == PartStatus.Waiting } }
                if (live.isEmpty()) break
                for (p in live) {
                    setPart(p.kind) { it.copy(status = PartStatus.Running) }
                    try {
                        p.runRound(id, round) { m ->
                            setPart(p.kind) { v ->
                                v.copy(
                                    measurements =
                                        v.measurements.filter { it.label != m.label } + m,
                                )
                            }
                        }
                        setPart(p.kind) { it.copy(status = PartStatus.Waiting, rounds = it.rounds + 1) }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (e: PartNotConfigured) {
                        setPart(p.kind) { it.copy(status = PartStatus.NotConfigured(e.message ?: "Not configured")) }
                    } catch (e: Exception) {
                        setPart(p.kind) { it.copy(status = PartStatus.Failed(e.message ?: e.javaClass.simpleName)) }
                    }
                }
                if (round >= maxRounds) {
                    limit = "Session limit reached: $maxRounds rounds"
                    break@rounds
                }
                if (nowMs() - started >=
                    maxDurationMs
                ) {
                    limit =
                        "Session limit reached: " +
                        if (maxDurationMs % 60_000 ==
                            0L
                        ) {
                            "${maxDurationMs / 60_000} minutes"
                        } else {
                            "${maxDurationMs / 1000} s"
                        }
                    break@rounds
                }
                round++
            }
            watchdog.cancel()
        }
        _view.update { v ->
            v.copy(
                phase = SessionPhase.STOPPING,
                parts =
                    v.parts.map { p ->
                        if (p.status ==
                            PartStatus.Waiting
                        ) {
                            p.copy(status = PartStatus.Done("${p.rounds} round${if (p.rounds == 1) "" else "s"} completed"))
                        } else {
                            p
                        }
                    },
            )
        }
        recordParts(id, list)
        val v = _view.value
        val done = v.parts.count { it.status is PartStatus.Done }
        if (done > 0) {
            summary(id, "CONTRIBUTION_SESSION_COMPLETED", "Session finished")
            _view.update { it.copy(phase = SessionPhase.IDLE, note = "Session finished. $limit.") }
        } else {
            val why =
                v.parts.firstNotNullOfOrNull { (it.status as? PartStatus.Failed)?.reason }
                    ?: v.parts.firstNotNullOfOrNull { (it.status as? PartStatus.NotConfigured)?.setup } ?: "No part completed"
            fail(id, why, recordIt = true)
        }
    }

    /** One receipt per part that completed at least one round, with its totals. */
    private suspend fun recordParts(
        id: String,
        list: List<SessionPart>,
    ) {
        for (p in list) {
            val d = runCatching { p.receipt(id) }.getOrNull() ?: continue
            runCatching { record(d.copy(digests = d.digests + ("sessionId" to id))) }
        }
    }

    private suspend fun fail(
        id: String,
        why: String,
        recordIt: Boolean,
    ) {
        if (recordIt) summary(id, "CONTRIBUTION_SESSION_FAILED", "Session failed")
        _view.update { it.copy(phase = SessionPhase.FAILED, failure = why) }
    }

    private suspend fun finishStopped(id: String) {
        val wasRunning =
            _view.value.parts
                .filter { it.status == PartStatus.Running }
                .map { it.kind }
                .toSet()
        _view.update { v ->
            v.copy(
                phase = SessionPhase.STOPPING,
                parts =
                    v.parts.map {
                        if (it.status == PartStatus.Running ||
                            it.status == PartStatus.Waiting
                        ) {
                            it.copy(status = PartStatus.Cancelled)
                        } else {
                            it
                        }
                    },
            )
        }
        for (p in current) if (p.kind in wasRunning) runCatching { p.cancel(id) }
        recordParts(id, current)
        val reason = stopReason
        summary(id, "CONTRIBUTION_SESSION_STOPPED", if (reason == null) "Session stopped by you" else "Session stopped by policy")
        _view.update {
            if (reason ==
                null
            ) {
                it.copy(phase = SessionPhase.IDLE, note = "Stopped by you.")
            } else {
                it.copy(phase = SessionPhase.FAILED, failure = reason)
            }
        }
    }

    private suspend fun summary(
        id: String,
        outcome: String,
        title: String,
    ) {
        val v = _view.value
        val lines =
            v.parts.joinToString("; ") { p ->
                val s =
                    when (val st = p.status) {
                        is PartStatus.NotConfigured -> "not configured (${st.setup})"
                        PartStatus.Ready -> "not run"
                        PartStatus.Running -> "running"
                        PartStatus.Waiting -> "waiting"
                        is PartStatus.Done -> "done"
                        PartStatus.Cancelled -> "cancelled"
                        is PartStatus.Failed -> "failed (${st.reason})"
                    }
                val m = p.measurements.joinToString(", ") { "${it.label} ${it.value} ${it.unit} [${it.source}]" }
                "${p.kind.title}: $s, ${p.rounds} round${if (p.rounds == 1) "" else "s"}" + if (m.isEmpty()) "" else " · $m"
            }
        runCatching {
            record(
                ReceiptDraft(
                    ReceiptKind.POLICY,
                    outcome,
                    title,
                    "$lines. Nothing was earned; no ORE, SKR or payment is involved.",
                    "this phone",
                    operationId = id,
                    digests = mapOf("sessionId" to id),
                    localObservation =
                        Evidence(
                            "CHECKED",
                            "Edge Mode, charge/thermal/battery gates and unmetered network checked at start and every ${watchMs / 1000} s",
                        ),
                ),
            )
        }
    }
}

/** Session limits: a session never runs longer or more rounds than this, whatever the gates say. */
const val MAX_ROUNDS = 100
const val MAX_DURATION_MS = 10 * 60_000L
