package com.edgeore.app

import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.node.NodeAgentProtocol
import com.edgeore.app.node.NodeResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs against a real DeProof node agent started by scripts/node-agent-it.sh.
 * Skipped (not failed) when EDGEORE_NODE_IT is unset.
 */
class NodeAgentIntegrationTest {
    @Test fun pairObserveRevokeAgainstRealAgent() {
        val path = System.getenv("EDGEORE_NODE_IT").orEmpty()
        assumeTrue("EDGEORE_NODE_IT not set; real node-agent test skipped", path.isNotEmpty() && File(path).exists())
        val cfg = JSONObject(File(path).readText())
        val endpoint = cfg.getString("endpoint")
        val pin = cfg.getString("certSha256")
        val challenge = NodeAgentProtocol.parseChallenge(cfg.getString("challenge"))
        val code = cfg.getString("code")

        // Wrong certificate pin must fail closed before any request is sent.
        val wrongPin = NodeAgentClient(endpoint, "00".repeat(32))
        assertTrue(wrongPin.pair(challenge, code, listOf("READ_NODE"), Ed25519.generate()) is NodeResult.Unreachable)

        val client = NodeAgentClient(endpoint, pin)
        // Wrong code is refused by the agent.
        val bad = client.pair(challenge, "wrong-code", listOf("READ_NODE"), Ed25519.generate())
        assertTrue("$bad", bad is NodeResult.Refused)

        val key = Ed25519.generate()
        val paired = client.pair(challenge, code, listOf("READ_NODE"), key)
        assertTrue("$paired", paired is NodeResult.Ok)
        val session = (paired as NodeResult.Ok).value

        // Challenge is single-use.
        val replay = client.pair(challenge, code, listOf("READ_NODE"), Ed25519.generate())
        assertTrue("$replay", replay is NodeResult.Refused && replay.code == "EXPIRED_OR_REPLAYED_CHALLENGE")

        val health = client.command(session, key, "observe")
        assertTrue("$health", health is NodeResult.Ok)
        assertEquals(challenge.fingerprint, (health as NodeResult.Ok).value.getString("fingerprint"))
        println("NODE_IT observe: " + health.value.toString())

        // A different key cannot drive this session.
        val forged = client.command(session, Ed25519.generate(), "observe")
        assertTrue("$forged", forged is NodeResult.Refused && forged.code == "BAD_COMMAND_SIGNATURE")

        // Out-of-scope action refused.
        val scope = client.command(session, key, "transfer")
        assertTrue("$scope", scope is NodeResult.Refused && scope.code == "SCOPE_DENIED")

        val revoked = client.command(session, key, "revoke")
        assertTrue("$revoked", revoked is NodeResult.Ok)

        val after = client.command(session, key, "observe")
        assertTrue("$after", after is NodeResult.Refused && after.code == "SESSION_REVOKED_OR_UNKNOWN")
        println("NODE_IT: pair → observe → revoke → refused-after-revoke PASSED")
    }
}
