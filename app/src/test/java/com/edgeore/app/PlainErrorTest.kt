package com.edgeore.app

import com.edgeore.app.ui.components.PlainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain-language headlines for technical failures, with the original text kept and capped. */
class PlainErrorTest {
    @Test fun plainSentencesPassThroughUnchanged() {
        val raw = "Pairing refused by the node."
        assertEquals(raw, PlainError.headline(raw, PlainError.Area.NODE))
        assertNull(PlainError.detail(raw))
    }

    @Test fun technicalErrorsGetAPlainHeadlineAndKeepTheOriginal() {
        val tls = "TLS failed: Certificate pin mismatch (check the certificate SHA-256)"
        assertEquals("The secure connection to your node was refused. Check the certificate SHA-256.", PlainError.headline(tls, PlainError.Area.NODE))
        val net = "Network error: ConnectException: failed to connect to /127.0.0.1"
        assertEquals("Couldn't reach your node. Check the network and the address.", PlainError.headline(net, PlainError.Area.NODE))
        assertEquals(net, PlainError.detail(net))
        val host = "Host unreachable: SocketTimeoutException"
        assertEquals("Your AI host took too long to answer. Try again.", PlainError.headline(host, PlainError.Area.OWNED_HOST))
        val odd = "On-device run failed: IllegalStateException"
        assertEquals(PlainError.Area.ON_DEVICE_AI.fallback, PlainError.headline(odd, PlainError.Area.ON_DEVICE_AI))
    }

    @Test fun longDetailsAreCapped() {
        val raw = "x_".repeat(400)
        val d = PlainError.detail(raw)!!
        assertTrue(d.startsWith("x_".repeat(150)))
        assertTrue(d.endsWith("(500 more characters not shown)"))
    }
}
