package com.edgeore.app.contribution

import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Hex
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.node.NodeAgentProtocol
import com.edgeore.app.node.NodeResult
import com.edgeore.app.receipts.Evidence
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.OwnedGatewayBackupProvider
import com.edgeore.app.storage.ProviderObservation
import com.edgeore.app.storage.UploadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64

/** What a paired node record offers a session (endpoint, pinned certificate, signed session and scopes). */
data class PairedNode(val endpoint: String, val certSha256: String, val fingerprint: String, val sessionId: String, val scopes: List<String>)

/** The owner's storage gateway as configured in Storage. [token] is read only when a request is made. */
data class GatewaySetup(val endpoint: String, val spkiPins: List<String>, val token: () -> String?)

private const val PHONE_CLOCK = "this phone's monotonic clock"

/**
 * Runs the owner-pinned proof job on the paired DeProof node agent (scope RUN_PROOF_JOB): discoverProofJobs once,
 * then one proof command with explicit consent per round. The agent bounds it to 60 s and runs the separate gnark verifier; the phone checks the node's
 * Ed25519 signature over the result against the paired node key. Stop sends cancelProof.
 */
class NodeProofPart(
    private val node: PairedNode?,
    private val key: () -> Ed25519.KeyPair?,
    private val client: (endpoint: String, cert: String, timeoutMs: Int) -> NodeAgentClient = { e, c, t -> NodeAgentClient(e, c, t) },
    private val nanos: () -> Long = System::nanoTime,
    private val tickMs: Long = 250,
) : SessionPart {
    override val kind = PartKind.NODE_PROOF

    override fun notConfigured(): String? = when {
        node == null -> "Set up: pair a node (Nodes tab)."
        "RUN_PROOF_JOB" !in node.scopes -> "Set up: pair the node again with the RUN_PROOF_JOB scope."
        key() == null -> "Set up: the node key is not on this phone; pair again."
        else -> null
    }

    private fun refusal(r: NodeResult<*>, what: String): Exception = when (r) {
        is NodeResult.Refused -> if (r.code == "PROOF_BACKEND_UNAVAILABLE") PartNotConfigured("Not configured on the node: ${NodeAgentProtocol.explain(r.code)}")
            else IllegalStateException("$what refused: ${r.message}")
        is NodeResult.Unreachable -> IllegalStateException("Node unreachable during $what: ${r.message}")
        is NodeResult.Ok -> IllegalStateException("unexpected")
    }

    // Totals for this session (a new part instance is made per session).
    private var jobs = 0
    private var totalMs = 0L
    private var lastOperation: String? = null
    private var lastCommandSha: String? = null
    private val resultCommitment = java.security.MessageDigest.getInstance("SHA-256")
    private var jobId: String? = null

    override suspend fun runRound(sessionId: String, round: Int, report: (Measurement) -> Unit): String = coroutineScope {
        val n = node ?: throw PartNotConfigured(notConfigured()!!)
        val k = key() ?: throw PartNotConfigured(notConfigured()!!)
        val job = jobId ?: run {
            val discover = withContext(Dispatchers.IO) { client(n.endpoint, n.certSha256, 10_000).command(n.sessionId, k, "discoverProofJobs") }
            if (discover !is NodeResult.Ok) throw refusal(discover, "discoverProofJobs")
            discover.value.optString("jobId").ifEmpty { throw IllegalStateException("Node listed no proof job id") }.also { jobId = it }
        }
        val params = "{\"jobId\":${NodeAgentProtocol.q(job)},\"explicitConsent\":true}"
        val t0 = nanos()
        val call = async(Dispatchers.IO) { client(n.endpoint, n.certSha256, 75_000).command(n.sessionId, k, "proof", paramsJson = params) }
        val ticker = launch { while (true) { report(Measurement("Current proof job", (nanos() - t0) / 1_000_000, "ms", "$PHONE_CLOCK, waiting for the node")); delay(tickMs) } }
        val r = try { call.await() } finally { ticker.cancel() }
        val roundTripMs = (nanos() - t0) / 1_000_000
        if (r !is NodeResult.Ok) throw refusal(r, "proof")
        val signed = NodeSigned.verify(r.value, n.fingerprint)
        val p = signed.payload
        require(p.optString("domain") == "deproof-contribution-v1") { "Unexpected result domain" }
        require(p.optString("jobId") == job && p.optString("operationId") == r.operationId) { "Result does not match the job or operation sent" }
        require(p.optJSONObject("verificationObservation")?.optString("status") == "VALID") { "Node verifier did not report VALID" }
        require(p.optString("resultDigest").matches(Regex("(sha256:)?[0-9a-f]{64}"))) { "Malformed result digest" }
        jobs++; totalMs += roundTripMs; lastOperation = r.operationId; lastCommandSha = r.payloadSha256
        resultCommitment.update(Sha256.digest(signed.payloadRaw))
        report(Measurement("Current proof job", roundTripMs, "ms", "$PHONE_CLOCK, signed command sent to node-signed result received"))
        report(Measurement("Proof jobs verified", jobs.toLong(), "jobs", "node-signed results, Ed25519 checked against the paired node key; node verifier VALID"))
        report(Measurement("Proof job time, total", totalMs, "ms", PHONE_CLOCK))
        "proof job $jobs verified in $roundTripMs ms"
    }

    override fun receipt(sessionId: String): ReceiptDraft? {
        val n = node ?: return null
        if (jobs == 0) return null
        val commitment = Hex.encode((resultCommitment.clone() as java.security.MessageDigest).digest())
        val detail = "$jobs Groth16/BN254 proof job${if (jobs == 1) "" else "s"} of the node's owner-pinned sample circuit, each checked VALID by the node's separate verifier; $totalMs ms total round trip"
        return ReceiptDraft(
            ReceiptKind.NODE, "PROOF_JOBS_VERIFIED", "Proof jobs on your node",
            "$detail. The node agent does not report CPU time, so none is shown. The jobs prove a fixed sample relation and have no value to anyone; nothing is paid: no ORE, SKR, points or payment.",
            n.endpoint, operationId = lastOperation,
            digests = mapOf("lastCommandPayloadSha256" to (lastCommandSha ?: ""), "signedResultsCommitmentSha256" to commitment,
                "jobId" to (jobId ?: ""), "jobsVerified" to jobs.toString(), "totalRoundTripMs" to totalMs.toString(), "nodeFingerprint" to n.fingerprint),
            localObservation = Evidence("CHECKED", "Round trips measured on the phone's monotonic clock; commitment is SHA-256 over each signed result payload's SHA-256 in order"),
            nodeSignature = Evidence("VERIFIED", "Ed25519 signature over every result payload checked against the paired node key ${n.fingerprint.take(16)}…"),
            independentVerification = Evidence("NOT_AVAILABLE", "The proofs were verified by a separate process on the same node host, not by this phone"),
        )
    }

    override suspend fun cancel(sessionId: String) {
        val n = node ?: return
        val k = key() ?: return
        withContext(Dispatchers.IO) { runCatching { client(n.endpoint, n.certSha256, 10_000).command(n.sessionId, k, "cancelProof") } }
    }
}

/** A `deproof-node-signed-v1` envelope whose Ed25519 signature and key fingerprint have been checked. */
class NodeSigned private constructor(val payloadRaw: ByteArray, val payload: JSONObject) {
    val payloadBytes: Int get() = payloadRaw.size
    companion object {
        fun verify(envelope: JSONObject, expectedFingerprint: String): NodeSigned {
            require(envelope.optString("schema") == "deproof-node-signed-v1") { "Not a node-signed envelope" }
            val dec = Base64.getDecoder()
            val pub = dec.decode(envelope.getString("publicKeyBase64"))
            require(pub.size == 32 && Hex.encode(Sha256.digest(pub)) == expectedFingerprint.lowercase()) { "Result was signed by a different node key" }
            val raw = dec.decode(envelope.getString("payloadBase64"))
            require(Ed25519.verify(pub, raw, dec.decode(envelope.getString("signatureBase64")))) { "Node signature does not verify" }
            return NodeSigned(raw, JSONObject(String(raw, Charsets.UTF_8)))
        }
    }
}

/**
 * Seals one chunk of random bytes per session with the vault key (EOV2, empty name). Each round uploads it to the
 * owner's storage gateway over SPKI-pinned TLS (the gateway re-checks the digest and stores it once), requires the
 * acknowledgement of its SHA-256, downloads it again and decrypts it. Byte counts are what this phone wrote to and read
 * from the TLS stream; the stored size is what the gateway acknowledged. The chunk stays on the gateway.
 */
class GatewayRoundTripPart(
    private val gateway: GatewaySetup?,
    private val vault: () -> LocalVault?,
    private val chunkBytes: Int = DEFAULT_CHUNK_BYTES,
    private val provider: (GatewaySetup) -> OwnedGatewayBackupProvider = { g -> OwnedGatewayBackupProvider(g.endpoint, { g.token() ?: "" }, g.spkiPins) },
    private val nanos: () -> Long = System::nanoTime,
    private val random: SecureRandom = SecureRandom(),
) : SessionPart {
    override val kind = PartKind.GATEWAY_ROUND_TRIP

    override fun notConfigured(): String? = when {
        gateway == null -> "Set up your storage gateway (Storage tab)."
        gateway.spkiPins.isEmpty() -> "Set up: add the gateway's SPKI pin (Storage tab)."
        gateway.token().isNullOrEmpty() -> "Set up: the gateway token is not on this phone; set up storage again."
        vault() == null -> "Set up: the vault key is unavailable on this phone."
        else -> null
    }

    private fun jobId(sessionId: String) = "session-" + sessionId.filter { it.isLetterOrDigit() }.take(40)

    // Session state: one chunk is sealed and stored per session; later rounds send and read the same chunk again.
    private var blob: com.edgeore.app.storage.BackupBlob? = null
    private var ack: ProviderObservation.Acknowledged? = null
    private var sent = 0L
    private var received = 0L
    private var upMs = 0L
    private var downMs = 0L
    private var trips = 0

    override suspend fun runRound(sessionId: String, round: Int, report: (Measurement) -> Unit): String {
        val g = gateway ?: throw PartNotConfigured(notConfigured()!!)
        val v = vault() ?: throw PartNotConfigured(notConfigured()!!)
        val p = provider(g)
        val b = blob ?: withContext(Dispatchers.Default) { v.sealDetached(ByteArray(chunkBytes).also(random::nextBytes)) }.also { blob = it }
        val id = jobId(sessionId)
        val sentBefore = sent
        val up0 = nanos()
        val a = p.upload(UploadRequest(id, b.ciphertext, b.ciphertextSha256, b.ciphertextBytes)) { n ->
            report(Measurement("Sent to your gateway", sentBefore + n, "bytes", "counted by this phone as written to the TLS stream"))
        }
        upMs += (nanos() - up0) / 1_000_000
        if (a !is ProviderObservation.Acknowledged || a.ciphertextSha256 != b.ciphertextSha256) throw IllegalStateException("Gateway did not acknowledge the chunk's SHA-256")
        sent += b.ciphertextBytes
        if (ack == null) ack = a
        report(Measurement("Stored on your gateway", b.ciphertextBytes, "bytes", "one chunk; gateway acknowledged SHA-256 ${b.ciphertextSha256.take(12)}… at ${ack!!.at}"))
        val receivedBefore = received
        val sink = CountingSink { n -> report(Measurement("Read back from your gateway", receivedBefore + n, "bytes", "counted by this phone as read from the TLS stream")) }
        val down0 = nanos()
        val got = p.download(a.remoteRef, b.ciphertextBytes, sink)
        downMs += (nanos() - down0) / 1_000_000
        val back = sink.bytes()
        require(got == b.ciphertextBytes && Sha256.hex(back) == b.ciphertextSha256) { "Downloaded bytes do not match what was stored" }
        received += got
        val opened = withContext(Dispatchers.Default) { v.openEnvelope(back, b.objectId) }
        require(Sha256.hex(opened) == b.plainSha256) { "Decrypted chunk does not match" }
        trips++
        report(Measurement("Round trips verified", trips.toLong(), "round trips", "downloaded bytes matched the SHA-256 and decrypted with the vault key"))
        report(Measurement("Transfer time, total", upMs + downMs, "ms", PHONE_CLOCK))
        return "round trip $trips verified"
    }

    override fun receipt(sessionId: String): ReceiptDraft? {
        val g = gateway ?: return null
        val b = blob ?: return null
        val a = ack ?: return null
        val detail = "One ${b.ciphertextBytes}-byte encrypted chunk stored; $trips verified round trip${if (trips == 1) "" else "s"}: $sent bytes sent in $upMs ms and $received bytes read back in $downMs ms"
        return ReceiptDraft(
            ReceiptKind.STORAGE, "GATEWAY_ROUND_TRIPS_VERIFIED", "Encrypted chunk on your gateway",
            "$detail. The chunk is random test data sealed with this phone's vault key and stays on your gateway. Your own equipment only; nothing is earned.",
            g.endpoint, operationId = jobId(sessionId),
            digests = mapOf("ciphertextSha256" to b.ciphertextSha256, "remoteRef" to a.remoteRef, "storedBytes" to b.ciphertextBytes.toString(),
                "bytesSent" to sent.toString(), "bytesReceived" to received.toString(), "roundTrips" to trips.toString(), "uploadMs" to upMs.toString(), "downloadMs" to downMs.toString()),
            localObservation = Evidence("CHECKED", "Byte counts and times measured on this phone; every download matched the SHA-256 and decrypted with the vault key"),
            providerAcknowledgement = Evidence("ACKNOWLEDGED", "Gateway acknowledged ciphertext SHA-256 ${b.ciphertextSha256} at ${a.at}, over SPKI-pinned TLS"),
        )
    }

    /** Cancels the gateway job only if the chunk was never acknowledged, so an interrupted first upload leaves nothing. */
    override suspend fun cancel(sessionId: String) {
        val g = gateway ?: return
        if (ack != null) return
        withContext(Dispatchers.IO) { runCatching { provider(g).cancel(jobId(sessionId)) } }
    }

    companion object { const val DEFAULT_CHUNK_BYTES = 256 * 1024 }
}

/** In-memory sink that reports its running byte count after every write. */
private class CountingSink(private val onCount: (Long) -> Unit) : OutputStream() {
    private val buf = ByteArrayOutputStream()
    override fun write(b: Int) { buf.write(b); onCount(buf.size().toLong()) }
    override fun write(b: ByteArray, off: Int, len: Int) { buf.write(b, off, len); onCount(buf.size().toLong()) }
    fun bytes(): ByteArray = buf.toByteArray()
}

