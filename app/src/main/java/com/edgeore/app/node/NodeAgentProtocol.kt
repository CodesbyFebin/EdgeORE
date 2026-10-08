package com.edgeore.app.node

import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * Wire format of the DeProof node agent (CodesbyFebin/DeProof--EdgeORE, node-agent/internal/agent).
 * Payload strings reproduce Go's json.Marshal field order exactly, because the agent verifies
 * Ed25519 signatures over those bytes.
 */
object NodeAgentProtocol {
    const val POLICY = "node-policy-v1"
    val KNOWN_SCOPES = listOf("READ_NODE", "EXPORT_PUBLIC_RECORDS")

    data class Challenge(val id: String, val nonce: String, val fingerprint: String, val expiresAt: String)

    private val HEX = Regex("^[0-9a-f]+$")

    /** Parses the challenge JSON printed by `deproof-node`. Unknown or missing keys are refused. */
    fun parseChallenge(text: String): Challenge {
        val o = try { JSONObject(text.trim()) } catch (_: Exception) { throw IllegalArgumentException("Challenge is not valid JSON") }
        val keys = o.keys().asSequence().toSet()
        require(keys == setOf("id", "nonce", "fingerprint", "expiresAt")) { "Challenge must contain exactly id, nonce, fingerprint, expiresAt" }
        val c = Challenge(o.getString("id"), o.getString("nonce"), o.getString("fingerprint"), o.getString("expiresAt"))
        require(HEX.matches(c.id) && HEX.matches(c.nonce)) { "Challenge id/nonce must be hex" }
        require(c.fingerprint.length == 64 && HEX.matches(c.fingerprint)) { "Node fingerprint must be 64 hex characters" }
        val expiry = try { Instant.parse(c.expiresAt) } catch (_: Exception) { throw IllegalArgumentException("Challenge expiry is not RFC 3339") }
        require(expiry.isAfter(Instant.now())) { "Challenge expired; restart the node agent for a new one" }
        return c
    }

    fun pairPayload(c: Challenge, publicKeyB64: String, scopes: List<String>): String =
        "{\"domain\":\"deproof-pair-v1\",\"challenge\":{\"id\":${q(c.id)},\"nonce\":${q(c.nonce)},\"fingerprint\":${q(c.fingerprint)},\"expiresAt\":${q(c.expiresAt)}}," +
            "\"publicKey\":${q(publicKeyB64)},\"scopes\":[${scopes.joinToString(",") { q(it) }}]}"

    fun pairRequest(c: Challenge, code: String, publicKeyB64: String, signatureB64: String, scopes: List<String>): String =
        "{\"challengeId\":${q(c.id)},\"code\":${q(code)},\"publicKey\":${q(publicKeyB64)},\"signatureBase64\":${q(signatureB64)}," +
            "\"approvedFingerprint\":${q(c.fingerprint)},\"scopes\":[${scopes.joinToString(",") { q(it) }}]}"

    fun commandPayload(sessionId: String, operationId: String, deadline: Instant, action: String, paramsJson: String = "{}"): String =
        "{\"sessionId\":${q(sessionId)},\"operationId\":${q(operationId)},\"deadline\":${q(deadline.toString())}," +
            "\"policy\":${q(POLICY)},\"action\":${q(action)},\"params\":$paramsJson}"

    fun signedEnvelope(payload: ByteArray, signature: ByteArray): String =
        "{\"payloadBase64\":${q(b64(payload))},\"signatureBase64\":${q(b64(signature))}}"

    fun newOperationId(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** JSON string literal matching Go's encoder for the ASCII values used here (HTML chars escaped like Go). */
    fun q(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch == '<' || ch == '>' || ch == '&' || ch.code < 0x20 || ch == '\u2028' || ch == '\u2029' -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }

    /** Human-readable explanation for agent error codes. */
    fun explain(code: String): String = when (code) {
        "EXPIRED_OR_REPLAYED_CHALLENGE" -> "Challenge expired or already used. Restart the node agent to get a fresh 2-minute challenge."
        "PAIRING_DENIED" -> "Pairing denied: wrong code or fingerprint."
        "SCOPE_DENIED" -> "Scope denied by the node owner's allow-list."
        "BAD_PAIRING_SIGNATURE" -> "Node rejected the pairing signature."
        "SESSION_REVOKED_OR_UNKNOWN" -> "Session revoked or unknown on the node. Pair again."
        "SESSION_EXPIRED" -> "Session expired (24 h). Pair again."
        "BAD_COMMAND_SIGNATURE" -> "Node rejected the command signature."
        "INVALID_DEADLINE" -> "Node rejected the command deadline. Check the phone and host clocks."
        "REPLAYED_OPERATION" -> "Node refused a replayed operation."
        else -> "Node refused: $code"
    }
}
