package com.edgeore.app

import com.edgeore.app.device.CpuBudget
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.EdgeState
import com.edgeore.app.device.ResourceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WorkloadGateTest {
    @Test fun resumedWithoutWorkloadIsNotActive() {
        assertFalse(EdgePolicy.QUALIFIED_WORKLOAD_AVAILABLE)
        val evaluation = EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true), null)
        assertEquals(EdgeState.PAUSED, evaluation.state)
    }

    @Test fun missingSampleIsNotZero() {
        assertNull(CpuBudget.sampleRate(null, 10, 1000))
        assertNull(CpuBudget.sampleRate(50, 10, 1000))
        assertEquals(10L, CpuBudget.sampleRate(10, 20, 1000))
        assertFalse(CpuBudget.DEVICE_WIDE_CAP_ENFORCED)
    }
}
