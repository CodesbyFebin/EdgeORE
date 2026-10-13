package com.edgeore.app

import com.edgeore.app.contribution.ContributionSession
import com.edgeore.app.contribution.GatewayRoundTripPart
import com.edgeore.app.contribution.GatewaySetup
import com.edgeore.app.contribution.NodeProofPart
import com.edgeore.app.contribution.PairedNode
import com.edgeore.app.contribution.PartKind
import com.edgeore.app.contribution.PartStatus
import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.contribution.SessionView
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.node.NodeAgentProtocol
import com.edgeore.app.node.NodeResult
import com.edgeore.app.receipts.ReceiptLog
import com.edgeore.app.receipts.ReceiptVerifier
import com.edgeore.app.receipts.SoftwareReceiptSigner
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.OwnedGatewayBackupProvider
import com.edgeore.app.storage.ProviderObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator

/**
 * A real contribution session on the build machine: the DeProof node agent (pinned build, proof backend configured with
 * an owner-pinned job) and services/storage-gateway behind Caddy with a throwaway self-signed certificate, both on
 * loopback, started by scripts/session-e2e.sh. Drives the same ContributionSession, NodeProofPart and
 * GatewayRoundTripPart the app uses; phone gates are not part of this run (no phone), so blockedBy is fixed to null.
 * Skipped (not failed) when EDGEORE_SESSION_IT is unset.
 */
class ContributionSessionE2ETest {
    private companion object {
        const val ROUNDS = 5
    }

    private fun viewJson(
        v: SessionView,
        atMs: Long,
    ) = JSONObject()
        .put("tMs", atMs)
        .put("phase", v.phase.name)
        .put("failure", v.failure)
        .put("note", v.note)
        .put(
            "parts",
            JSONArray(
                v.parts.map { p ->
                    JSONObject()
                        .put("kind", p.kind.name)
                        .put("status", p.status.toString())
                        .put("rounds", p.rounds)
                        .put(
                            "measurements",
                            JSONArray(
                                p.measurements.map { m ->
                                    JSONObject()
                                        .put("label", m.label)
                                        .put("value", m.value)
                                        .put("unit", m.unit)
                                        .put("source", m.source)
                                },
                            ),
                        )
                },
            ),
        )

    private fun waitFor(
        timeoutMs: Long,
        what: String,
        cond: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            require(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            Thread.sleep(20)
        }
    }

    @Test fun realSessionOnOwnedNodeAndGatewayThenCancelledSession() {
        val path = System.getenv("EDGEORE_SESSION_IT").orEmpty()
        assumeTrue("EDGEORE_SESSION_IT not set; end-to-end session test skipped", path.isNotEmpty() && File(path).exists())
        val cfg = JSONObject(File(path).readText())
        val out = File(cfg.getString("evidenceDir")).apply { mkdirs() }

        // Pair with the scope the session needs, exactly as the Nodes screen does.
        val challenge = NodeAgentProtocol.parseChallenge(cfg.getString("challenge"))
        val key = Ed25519.generate()
        val paired =
            NodeAgentClient(
                cfg.getString("nodeEndpoint"),
                cfg.getString("nodeCertSha256"),
            ).pair(challenge, cfg.getString("code"), listOf("READ_NODE", "RUN_PROOF_JOB"), key)
        assertTrue("$paired", paired is NodeResult.Ok)
        val node =
            PairedNode(
                cfg.getString("nodeEndpoint"),
                cfg.getString("nodeCertSha256"),
                challenge.fingerprint,
                (paired as NodeResult.Ok).value,
                listOf("READ_NODE", "RUN_PROOF_JOB"),
            )
        val token = cfg.getString("gatewayToken")
        val gateway = GatewaySetup(cfg.getString("gatewayEndpoint"), listOf(cfg.getString("gatewaySpkiPin"))) { token }
        val tmp = Files.createTempDirectory("session-e2e").toFile()
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = LocalVault(File(tmp, "vault"), { aes }, freeSpace = { Long.MAX_VALUE })
        val signer = SoftwareReceiptSigner()
        val log = ReceiptLog(File(tmp, "receipts.jsonl"), signer)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session =
            ContributionSession(scope, {
                listOf(NodeProofPart(node, { key }), GatewayRoundTripPart(gateway, { vault }))
            }, { null }, { d -> log.append(d) }, maxRounds = ROUNDS)
        val trace = JSONArray()
        val t0 = System.currentTimeMillis()

        fun sample() {
            trace.put(viewJson(session.view.value, System.currentTimeMillis() - t0))
        }
        try {
            // 1) A full session.
            assertEquals(SessionPhase.IDLE, session.view.value.phase)
            assertTrue(
                session.view.value.parts
                    .all { it.status == PartStatus.Ready && it.measurements.isEmpty() },
            )
            assertTrue(session.start())
            val phases = mutableListOf<SessionPhase>()
            var activeSnapshot: SessionView? = null
            waitFor(120_000, "session to finish") {
                val v = session.view.value
                if (phases.lastOrNull() != v.phase) {
                    phases += v.phase
                    sample()
                }
                if (v.phase == SessionPhase.ACTIVE &&
                    v.parts.any { it.measurements.isNotEmpty() } &&
                    (activeSnapshot?.parts?.sumOf { it.measurements.size } ?: 0) < v.parts.sumOf { it.measurements.size }
                ) {
                    activeSnapshot =
                        v
                    sample()
                }
                v.phase == SessionPhase.IDLE || v.phase == SessionPhase.FAILED
            }
            sample()
            val done = session.view.value
            assertEquals("${done.failure} ${done.parts}", SessionPhase.IDLE, done.phase)
            assertTrue(
                "$phases",
                phases.containsAll(listOf(SessionPhase.STARTING, SessionPhase.ACTIVE)) || phases.contains(SessionPhase.ACTIVE),
            )
            val nodeView = done.parts.first { it.kind == PartKind.NODE_PROOF }
            val gwView = done.parts.first { it.kind == PartKind.GATEWAY_ROUND_TRIP }
            assertTrue("${nodeView.status}", nodeView.status is PartStatus.Done)
            assertTrue("${gwView.status}", gwView.status is PartStatus.Done)
            assertEquals(ROUNDS, nodeView.rounds)
            assertEquals(ROUNDS, gwView.rounds)
            val m = gwView.measurements.associateBy { it.label }
            val stored = m.getValue("Stored on your gateway").value
            assertTrue(stored > GatewayRoundTripPart.DEFAULT_CHUNK_BYTES)
            assertEquals(stored * ROUNDS, m.getValue("Sent to your gateway").value)
            assertEquals(stored * ROUNDS, m.getValue("Read back from your gateway").value)
            assertEquals(ROUNDS.toLong(), m.getValue("Round trips verified").value)
            val nm = nodeView.measurements.associateBy { it.label }
            assertEquals(ROUNDS.toLong(), nm.getValue("Proof jobs verified").value)
            assertTrue(nm.getValue("Proof job time, total").value > 0)

            // The chunk really is on the gateway.
            val receipts = log.read().receipts
            val gwReceipt = receipts.first { it.outcome == "GATEWAY_ROUND_TRIPS_VERIFIED" }
            val obs =
                runBlocking { OwnedGatewayBackupProvider(gateway.endpoint, { token }, gateway.spkiPins).status(gwReceipt.operationId!!) }
            assertTrue("$obs", obs is ProviderObservation.Acknowledged)
            assertEquals(
                listOf("PROOF_JOBS_VERIFIED", "GATEWAY_ROUND_TRIPS_VERIFIED", "CONTRIBUTION_SESSION_COMPLETED"),
                receipts.map { it.outcome },
            )

            // 2) A session stopped while ACTIVE: the running part is cancelled on the node or gateway, then IDLE.
            assertTrue(session.start())
            // Stop in the middle of the second round's proof job.
            waitFor(60_000, "second session in round 2") {
                session.view.value.parts.first { it.kind == PartKind.NODE_PROOF }.let {
                    it.rounds ==
                        1 &&
                        it.status == PartStatus.Running
                }
            }
            sample()
            session.stop()
            assertEquals(SessionPhase.STOPPING, session.view.value.phase)
            sample()
            waitFor(90_000, "stop to complete") {
                session.view.value.phase == SessionPhase.IDLE ||
                    session.view.value.phase == SessionPhase.FAILED
            }
            sample()
            val stopped = session.view.value
            assertEquals(SessionPhase.IDLE, stopped.phase)
            assertEquals("Stopped by you.", stopped.note)
            assertTrue("${stopped.parts}", stopped.parts.any { it.status == PartStatus.Cancelled })
            assertEquals(PartStatus.Cancelled, stopped.parts.first { it.kind == PartKind.NODE_PROOF }.status)
            assertEquals(
                listOf(
                    "PROOF_JOBS_VERIFIED",
                    "GATEWAY_ROUND_TRIPS_VERIFIED",
                    "CONTRIBUTION_SESSION_STOPPED",
                ),
                log.read().receipts.drop(3).map {
                    it.outcome
                },
            )
            // The node is no longer running a proof after the stop.
            val observe = NodeAgentClient(node.endpoint, node.certSha256).command(node.sessionId, key, "observe")
            assertTrue("$observe", observe is NodeResult.Ok && observe.value.optString("proofState") == "IDLE")

            // Every receipt verifies with the session's signer.
            val report = ReceiptVerifier.verify(log.export(), signer.publicKeySpki())
            assertTrue("${report.findings}", report.accepted)

            File(out, "session-trace.json").writeText(trace.toString(2))
            activeSnapshot?.let { File(out, "active-snapshot.json").writeText(viewJson(it, -1).toString(2)) }
            File(out, "final-session.json").writeText(viewJson(done, -1).toString(2))
            File(out, "receipts.jsonl").writeText(File(tmp, "receipts.jsonl").readText())
            File(out, "receipt-export.json").writeText(log.export())
            File(out, "summary.txt").writeText(
                buildString {
                    appendLine("phases (session 1): ${phases.joinToString(" -> ")}")
                    done.parts.forEach { p ->
                        appendLine("${p.kind}: ${p.status}, rounds=${p.rounds}")
                        p.measurements.forEach { appendLine("  ${it.label}: ${it.value} ${it.unit} [${it.source}]") }
                    }
                    appendLine(
                        "session 2 (stopped during round 2): ${stopped.parts.joinToString {
                            "${it.kind}=${it.status} rounds=${it.rounds}"
                        }}; note=${stopped.note}",
                    )
                    appendLine("receipts: ${log.read().receipts.joinToString { it.outcome }}")
                    appendLine("receipt verifier: accepted=${report.accepted} ok=${report.verifiedReceipts}")
                    appendLine("node observe after stop: proofState=${(observe as NodeResult.Ok).value.optString("proofState")}")
                },
            )
        } finally {
            scope.cancel()
            tmp.deleteRecursively()
        }
    }
}
