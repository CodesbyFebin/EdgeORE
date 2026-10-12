package com.edgeore.app

import com.edgeore.app.contribution.ContributionSession
import com.edgeore.app.contribution.GatewayRoundTripPart
import com.edgeore.app.contribution.GatewaySetup
import com.edgeore.app.contribution.Measurement
import com.edgeore.app.contribution.NodeProofPart
import com.edgeore.app.contribution.NodeSigned
import com.edgeore.app.contribution.PairedNode
import com.edgeore.app.contribution.PartKind
import com.edgeore.app.contribution.PartNotConfigured
import com.edgeore.app.contribution.PartStatus
import com.edgeore.app.contribution.SessionPart
import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Hex
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class ContributionSessionTest {
    /** A part whose every round waits for one permit on [release]; it reports a running total as it starts each round. */
    private class FakePart(
        override val kind: PartKind,
        private val setup: String? = null,
        private val throwOnRun: Exception? = null,
        private val onRound: () -> Unit = {},
    ) : SessionPart {
        val release = Channel<Unit>(Channel.UNLIMITED)
        var runs = 0; var cancels = 0; var completed = 0
        override fun notConfigured() = setup
        override suspend fun runRound(sessionId: String, round: Int, report: (Measurement) -> Unit): String {
            runs++
            throwOnRun?.let { throw it }
            report(Measurement("Sent", round * 10L, "bytes", "test counter"))
            release.receive()
            onRound()
            completed++
            report(Measurement("Sent", round * 10L + 5, "bytes", "test counter"))
            return "round $round"
        }
        override fun receipt(sessionId: String) = if (completed == 0) null else ReceiptDraft(ReceiptKind.NODE, "PART_DONE", "t", "$completed rounds", "test")
        override suspend fun cancel(sessionId: String) { cancels++ }
    }

    private class Harness(scope: TestScope, val parts: List<SessionPart>, var blocked: String? = null, maxRounds: Int = 100, maxMs: Long = 600_000, clock: () -> Long = { 0L }) {
        val receipts = mutableListOf<ReceiptDraft>()
        val phases = mutableListOf<SessionPhase>()
        val session = ContributionSession(scope.backgroundScope, { parts }, { blocked }, { receipts += it }, watchMs = 1_000,
            maxRounds = maxRounds, maxDurationMs = maxMs, nowMs = clock, newId = { "s1" })
        fun phase() = session.view.value.phase.also { if (phases.lastOrNull() != it) phases += it }
        fun part(k: PartKind) = session.view.value.parts.first { it.kind == k }
    }

    @Test fun idleStartingActiveStoppingIdle() = runTest {
        val node = FakePart(PartKind.NODE_PROOF); val gw = FakePart(PartKind.GATEWAY_ROUND_TRIP)
        val h = Harness(this, listOf(node, gw), maxRounds = 2)
        assertEquals(SessionPhase.IDLE, h.phase())
        assertTrue(h.part(PartKind.NODE_PROOF).measurements.isEmpty())
        assertTrue(h.session.start())
        assertEquals(SessionPhase.STARTING, h.phase())
        assertFalse("a second start is refused while running", h.session.start())
        runCurrent()
        assertEquals(SessionPhase.ACTIVE, h.phase())
        assertEquals(PartStatus.Running, h.part(PartKind.NODE_PROOF).status)
        assertEquals(10L, h.part(PartKind.NODE_PROOF).measurements.single().value)
        node.release.send(Unit); runCurrent()
        assertEquals("the same label replaces the last value", 15L, h.part(PartKind.NODE_PROOF).measurements.single().value)
        assertEquals(PartStatus.Waiting, h.part(PartKind.NODE_PROOF).status)
        assertEquals(1, h.part(PartKind.NODE_PROOF).rounds)
        assertEquals(PartStatus.Running, h.part(PartKind.GATEWAY_ROUND_TRIP).status)
        gw.release.send(Unit); runCurrent()
        assertEquals("round 2 starts back to back", PartStatus.Running, h.part(PartKind.NODE_PROOF).status)
        assertEquals(SessionPhase.ACTIVE, h.phase())
        node.release.send(Unit); gw.release.send(Unit); runCurrent()
        assertEquals(SessionPhase.IDLE, h.phase())
        assertEquals(listOf(SessionPhase.IDLE, SessionPhase.STARTING, SessionPhase.ACTIVE, SessionPhase.IDLE), h.phases)
        assertEquals(PartStatus.Done("2 rounds completed"), h.part(PartKind.NODE_PROOF).status)
        assertEquals(listOf("PART_DONE", "PART_DONE", "CONTRIBUTION_SESSION_COMPLETED"), h.receipts.map { it.outcome })
        assertTrue(h.receipts.all { it.digests["sessionId"] == "s1" })
        assertTrue(h.receipts.last().detail, h.receipts.last().detail.contains("2 rounds · Sent 25 bytes [test counter]"))
        assertEquals("Session finished. Session limit reached: 2 rounds.", h.session.view.value.note)
        assertEquals(2, node.runs); assertEquals(0, node.cancels)
    }

    @Test fun stopCancelsTheRunningPartAndReturnsToIdle() = runTest {
        val node = FakePart(PartKind.NODE_PROOF); val gw = FakePart(PartKind.GATEWAY_ROUND_TRIP)
        val h = Harness(this, listOf(node, gw))
        h.session.start(); runCurrent()
        assertEquals(SessionPhase.ACTIVE, h.phase())
        h.session.stop()
        assertEquals("the pickaxe stops immediately", SessionPhase.STOPPING, h.phase())
        runCurrent()
        assertEquals(SessionPhase.IDLE, h.phase())
        assertEquals(PartStatus.Cancelled, h.part(PartKind.NODE_PROOF).status)
        assertEquals(PartStatus.Ready, h.part(PartKind.GATEWAY_ROUND_TRIP).status)
        assertEquals(1, node.cancels); assertEquals(0, gw.runs); assertEquals(0, gw.cancels)
        assertEquals("no round completed, so only the summary", listOf("CONTRIBUTION_SESSION_STOPPED"), h.receipts.map { it.outcome })
        assertEquals("Stopped by you.", h.session.view.value.note)
        assertTrue("restartable after a stop", h.session.start())
    }

    @Test fun stopAfterCompletedRoundsStillRecordsThem() = runTest {
        val node = FakePart(PartKind.NODE_PROOF); val gw = FakePart(PartKind.GATEWAY_ROUND_TRIP)
        val h = Harness(this, listOf(node, gw))
        h.session.start(); runCurrent()
        node.release.send(Unit); runCurrent()
        assertEquals(PartStatus.Running, h.part(PartKind.GATEWAY_ROUND_TRIP).status)
        h.session.stop(); runCurrent()
        assertEquals(SessionPhase.IDLE, h.phase())
        assertEquals(PartStatus.Cancelled, h.part(PartKind.NODE_PROOF).status)
        assertEquals(PartStatus.Cancelled, h.part(PartKind.GATEWAY_ROUND_TRIP).status)
        assertEquals("only the part that was mid-round is cancelled remotely", 0, node.cancels); assertEquals(1, gw.cancels)
        assertEquals(listOf("PART_DONE", "CONTRIBUTION_SESSION_STOPPED"), h.receipts.map { it.outcome })
        assertEquals("1 rounds", h.receipts.first().detail)
    }

    @Test fun durationLimitEndsTheSession() = runTest {
        var now = 0L
        val node = FakePart(PartKind.NODE_PROOF, onRound = { now += 3_000 })
        val h = Harness(this, listOf(node), maxMs = 5_000, clock = { now })
        h.session.start(); runCurrent()
        node.release.send(Unit); runCurrent()
        assertEquals(SessionPhase.ACTIVE, h.phase())
        node.release.send(Unit); runCurrent()
        assertEquals(SessionPhase.IDLE, h.phase())
        assertEquals("Session finished. Session limit reached: 5 s.", h.session.view.value.note)
        assertEquals(2, node.completed)
    }

    @Test fun aGateFailingMidSessionStopsItAsFailed() = runTest {
        val node = FakePart(PartKind.NODE_PROOF)
        val h = Harness(this, listOf(node))
        h.session.start(); runCurrent()
        assertEquals(SessionPhase.ACTIVE, h.phase())
        h.blocked = "Charge-only: Not charging"
        advanceTimeBy(1_001); runCurrent()
        assertEquals(SessionPhase.FAILED, h.phase())
        assertEquals("Stopped by policy: Charge-only: Not charging", h.session.view.value.failure)
        assertEquals(1, node.cancels)
        assertEquals("CONTRIBUTION_SESSION_STOPPED", h.receipts.single().outcome)
    }

    @Test fun blockedAtStartFailsWithTheReasonAndRunsNothing() = runTest {
        val node = FakePart(PartKind.NODE_PROOF)
        val h = Harness(this, listOf(node), blocked = "an unmetered network (Wi-Fi) is not observed")
        h.session.start(); runCurrent()
        assertEquals(SessionPhase.FAILED, h.phase())
        assertEquals("Not started: an unmetered network (Wi-Fi) is not observed", h.session.view.value.failure)
        assertEquals(0, node.runs); assertTrue(h.receipts.isEmpty())
        assertFalse("ACTIVE was never reached", SessionPhase.ACTIVE in h.phases)
    }

    @Test fun nothingConfiguredFailsAndShowsWhatToSetUp() = runTest {
        val real = listOf(NodeProofPart(null, { null }), GatewayRoundTripPart(null, { null }))
        val h = Harness(this, real)
        assertFalse(h.session.view.value.anyConfigured)
        val setups = h.session.view.value.parts.map { (it.status as PartStatus.NotConfigured).setup }
        assertEquals(listOf("Set up: pair a node (Nodes tab).", "Set up your storage gateway (Storage tab)."), setups)
        assertTrue(h.session.view.value.parts.all { it.measurements.isEmpty() })
        h.session.start(); runCurrent()
        assertEquals(SessionPhase.FAILED, h.phase())
        val why = h.session.view.value.failure!!
        assertTrue(why, why.startsWith("Nothing to run.") && why.contains("pair a node") && why.contains("Set up your storage gateway"))
        assertTrue("nothing counted, nothing recorded", h.receipts.isEmpty())
        assertTrue(h.session.view.value.parts.all { it.measurements.isEmpty() && it.rounds == 0 })
        assertFalse(SessionPhase.ACTIVE in h.phases)
    }

    @Test fun aPartNotConfiguredOnTheRemoteSideIsNotCounted() = runTest {
        val node = FakePart(PartKind.NODE_PROOF, throwOnRun = PartNotConfigured("Not configured on the node: no proof job"))
        val gw = FakePart(PartKind.GATEWAY_ROUND_TRIP).also { it.release.trySend(Unit) }
        val h = Harness(this, listOf(node, gw), maxRounds = 1)
        h.session.start(); runCurrent()
        assertEquals(SessionPhase.IDLE, h.phase())
        assertEquals(PartStatus.NotConfigured("Not configured on the node: no proof job"), h.part(PartKind.NODE_PROOF).status)
        assertTrue(h.part(PartKind.NODE_PROOF).measurements.isEmpty())
        assertEquals(0, h.part(PartKind.NODE_PROOF).rounds)
        assertEquals(listOf("PART_DONE", "CONTRIBUTION_SESSION_COMPLETED"), h.receipts.map { it.outcome })
        assertEquals("the not-configured part is tried once, not every round", 1, node.runs)
    }

    @Test fun allPartsFailingIsFailedWithTheReason() = runTest {
        val node = FakePart(PartKind.NODE_PROOF, throwOnRun = IllegalStateException("Node unreachable during proof: Timed out"))
        val h = Harness(this, listOf(node, FakePart(PartKind.GATEWAY_ROUND_TRIP, setup = "Set up your storage gateway (Storage tab).")))
        h.session.start(); runCurrent()
        assertEquals(SessionPhase.FAILED, h.phase())
        assertEquals("Node unreachable during proof: Timed out", h.session.view.value.failure)
        assertEquals(listOf("CONTRIBUTION_SESSION_FAILED"), h.receipts.map { it.outcome })
    }

    @Test fun noMeasurementWithoutARealSource() {
        assertThrows(IllegalArgumentException::class.java) { Measurement("Sent", 1, "bytes", "") }
        assertThrows(IllegalArgumentException::class.java) { Measurement("Sent", 1, "bytes", "  ") }
        assertThrows(IllegalArgumentException::class.java) { Measurement("Sent", -1, "bytes", "counter") }
    }

    @Test fun realPartsExplainWhatIsMissing() {
        val key = Ed25519.generate()
        val readOnly = PairedNode("https://127.0.0.1:9843", "00".repeat(32), "11".repeat(32), "s", listOf("READ_NODE"))
        assertEquals("Set up: pair the node again with the RUN_PROOF_JOB scope.", NodeProofPart(readOnly, { key }).notConfigured())
        val withScope = readOnly.copy(scopes = listOf("READ_NODE", "RUN_PROOF_JOB"))
        assertEquals("Set up: the node key is not on this phone; pair again.", NodeProofPart(withScope, { null }).notConfigured())
        assertNull(NodeProofPart(withScope, { key }).notConfigured())
        val noPin = GatewaySetup("https://127.0.0.1:18443", emptyList()) { "t".repeat(40) }
        assertEquals("Set up: add the gateway's SPKI pin (Storage tab).", GatewayRoundTripPart(noPin, { null }).notConfigured())
        val noToken = GatewaySetup("https://127.0.0.1:18443", listOf("sha256/" + Base64.getEncoder().encodeToString(ByteArray(32)))) { null }
        assertEquals("Set up: the gateway token is not on this phone; set up storage again.", GatewayRoundTripPart(noToken, { null }).notConfigured())
    }

    @Test fun nodeSignedResultsAreCheckedAgainstThePairedKey() {
        val node = Ed25519.generate()
        val fp = Hex.encode(Sha256.digest(node.publicKey))
        val payload = """{"domain":"deproof-contribution-v1","jobId":"j"}""".toByteArray()
        fun env(sig: ByteArray, pub: ByteArray = node.publicKey) = JSONObject().put("schema", "deproof-node-signed-v1")
            .put("payloadBase64", Base64.getEncoder().encodeToString(payload)).put("signatureBase64", Base64.getEncoder().encodeToString(sig))
            .put("publicKeyBase64", Base64.getEncoder().encodeToString(pub))
        assertEquals("j", NodeSigned.verify(env(node.sign(payload)), fp).payload.getString("jobId"))
        assertThrows(IllegalArgumentException::class.java) { NodeSigned.verify(env(node.sign(payload)), "22".repeat(32)) }
        val other = Ed25519.generate()
        assertThrows(IllegalArgumentException::class.java) { NodeSigned.verify(env(other.sign(payload), other.publicKey), fp) }
        val bad = node.sign(payload).also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { NodeSigned.verify(env(bad), fp) }
        assertNotNull(NodeSigned.verify(env(node.sign(payload)), fp.uppercase()))
    }
}
