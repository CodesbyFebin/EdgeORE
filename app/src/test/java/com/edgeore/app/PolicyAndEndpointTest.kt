package com.edgeore.app

import com.edgeore.app.ai.EndpointPolicy
import com.edgeore.app.ai.OwnedHostModelClient
import com.edgeore.app.device.DeviceSnapshot
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.EdgeState
import com.edgeore.app.device.ResourceSettings
import com.edgeore.app.device.ThermalLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PolicyAndEndpointTest {
    private fun ip(s: String): (String) -> InetAddress = { InetAddress.getByAddress(it, s.split('.').map { p -> p.toInt().toByte() }.toByteArray()) }

    @Test fun loopbackAllowedPublicRefused() {
        assertTrue(EndpointPolicy.check("http://127.0.0.1:11434", ip("127.0.0.1")) is EndpointPolicy.Decision.Allowed)
        assertTrue(EndpointPolicy.check("https://api.example.com", ip("93.184.216.34")) is EndpointPolicy.Decision.Refused)
        assertTrue(EndpointPolicy.check("http://192.168.1.5:11434", ip("192.168.1.5")) is EndpointPolicy.Decision.Refused)
        assertTrue(EndpointPolicy.check("https://192.168.1.5:443", ip("192.168.1.5")) is EndpointPolicy.Decision.Allowed)
        assertTrue(EndpointPolicy.check("https://100.80.1.2", ip("100.80.1.2")) is EndpointPolicy.Decision.Allowed)
        assertTrue(EndpointPolicy.check("ftp://127.0.0.1", ip("127.0.0.1")) is EndpointPolicy.Decision.Refused)
        assertTrue(EndpointPolicy.check("http://127.0.0.1:11434/api", ip("127.0.0.1")) is EndpointPolicy.Decision.Refused)
    }

    @Test fun chatRequestIsNonStreamingWithSystemPrompt() {
        val o = JSONObject(OwnedHostModelClient.buildChatRequest("m", "sys", "hi"))
        assertFalse(o.getBoolean("stream"))
        assertEquals("system", o.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    private val good = DeviceSnapshot(80, true, ThermalLevel.NONE, 0)

    @Test fun pausedByUserByDefault() = assertEquals(EdgeState.PAUSED, EdgePolicy.evaluate(ResourceSettings(), good).state)

    @Test fun resumedWithoutWorkloadIsIdleNeverActive() {
        val e = EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true), good)
        assertEquals(EdgeState.IDLE, e.state)
        assertTrue(e.gatesPass)
    }

    @Test fun missingReadingsFailClosed() {
        val e = EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true), DeviceSnapshot(null, null, null, 0))
        assertEquals(EdgeState.PAUSED, e.state)
        assertTrue(e.gates.none { it.allowed })
    }

    @Test fun batteryReserveAndThermalEnforced() {
        val low = EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true, batteryReservePercent = 90), good)
        assertEquals(EdgeState.PAUSED, low.state)
        val hot = EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true), good.copy(thermal = ThermalLevel.SEVERE))
        assertEquals(EdgeState.PAUSED, hot.state)
        val notCharging = EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true), good.copy(charging = false))
        assertEquals(EdgeState.PAUSED, notCharging.state)
        val chargeOnlyOff = EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true, chargeOnly = false), good.copy(charging = false))
        assertEquals(EdgeState.IDLE, chargeOnlyOff.state)
    }
}
