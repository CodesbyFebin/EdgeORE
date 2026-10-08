package com.edgeore.app

import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.node.NodeAgentProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NodeAgentProtocolTest {
    private fun challengeJson(expires: Instant = Instant.now().plusSeconds(120)) =
        """{"id":"d763f4bc81fb81bfe91472f9c496eb9cf9d0ba7a31e61a8d","nonce":"8ba801af8fdcad1238199c8c3acf1e1de8262067f367fb49","fingerprint":"06b10e6ceb875eb348e9a339b1c20af6ab50e0ebb5add9f8234ce02dfb8f5561","expiresAt":"$expires"}"""

    @Test fun parsesAgentChallenge() {
        val c = NodeAgentProtocol.parseChallenge(challengeJson())
        assertEquals("06b10e6ceb875eb348e9a339b1c20af6ab50e0ebb5add9f8234ce02dfb8f5561", c.fingerprint)
    }

    @Test fun refusesExpiredOrExtraKeys() {
        assertTrue(runCatching { NodeAgentProtocol.parseChallenge(challengeJson(Instant.now().minusSeconds(1))) }.isFailure)
        assertTrue(runCatching { NodeAgentProtocol.parseChallenge(challengeJson().replace("}", ",\"x\":1}")) }.isFailure)
        assertTrue(runCatching { NodeAgentProtocol.parseChallenge("not json") }.isFailure)
    }

    @Test fun pairPayloadMatchesGoFieldOrder() {
        val c = NodeAgentProtocol.Challenge("aa", "bb", "cc", "2026-10-08T04:43:06.466237869Z")
        assertEquals(
            """{"domain":"deproof-pair-v1","challenge":{"id":"aa","nonce":"bb","fingerprint":"cc","expiresAt":"2026-10-08T04:43:06.466237869Z"},"publicKey":"PK+/=","scopes":["READ_NODE"]}""",
            NodeAgentProtocol.pairPayload(c, "PK+/=", listOf("READ_NODE")),
        )
    }

    @Test fun commandPayloadIsValidJsonWithPolicy() {
        val p = NodeAgentProtocol.commandPayload("sess", "0123456789abcdef0123", Instant.parse("2026-10-08T00:00:30Z"), "observe")
        val o = JSONObject(p)
        assertEquals("node-policy-v1", o.getString("policy"))
        assertEquals(listOf("sessionId", "operationId", "deadline", "policy", "action", "params"), Regex("\"(\\w+)\":").findAll(p).map { it.groupValues[1] }.toList())
    }

    @Test fun goStyleEscaping() = assertEquals("\"\\u003ca\\u0026b\\u003e\"", NodeAgentProtocol.q("<a&b>"))

    @Test fun operationIdsAreUniqueHex() {
        val ids = (1..50).map { NodeAgentProtocol.newOperationId() }.toSet()
        assertEquals(50, ids.size)
        assertTrue(ids.all { it.length == 32 && it.all { ch -> ch in "0123456789abcdef" } })
    }

    @Test fun clientRefusesInsecureEndpoints() {
        val pin = "b8ff19120ddd74f4e535fc5304eaf43b8900b3ebe4329476d78e9a9c372c6de2"
        assertTrue(runCatching { NodeAgentClient("http://127.0.0.1:9843", pin) }.isFailure)
        assertTrue(runCatching { NodeAgentClient("https://127.0.0.1:9843/x", pin) }.isFailure)
        assertTrue(runCatching { NodeAgentClient("https://127.0.0.1:9843", "abc") }.isFailure)
        assertEquals("https://127.0.0.1:9843", NodeAgentClient("https://127.0.0.1:9843/", pin.uppercase()).baseUrl)
    }
}
