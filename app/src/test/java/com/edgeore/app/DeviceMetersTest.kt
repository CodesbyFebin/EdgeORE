package com.edgeore.app

import com.edgeore.app.device.CpuSample
import com.edgeore.app.device.DiskSample
import com.edgeore.app.device.ioBytesPerSecond
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMetersTest {
    @Test
    fun cpuBusyPercentIgnoresIdle() {
        val prev = CpuSample.parse("cpu  10 0 10 80 0 0 0")
        val next = CpuSample.parse("cpu  20 0 20 160 0 0 0")
        assertEquals(20, CpuSample.percent(prev!!, next!!))
    }

    @Test
    fun cpuStandsStill() {
        val sample = CpuSample.parse("cpu  1 0 1 1")!!
        assertNull(CpuSample.percent(sample, sample))
    }

    @Test
    fun diskSkipsPartitions() {
        val text = """
            8 0 sda 1 0 100 0 1 0 50 0 0 0 0
            8 1 sda1 9 0 900 0 9 0 900 0 0 0 0
            259 0 nvme0n1 0 0 10 0 0 0 0 0 0 0 0
            259 1 nvme0n1p1 0 0 99 0 0 0 0 0 0 0 0
        """.trimIndent()
        assertEquals((100 + 50 + 10) * 512L, DiskSample.parse(text)!!.bytes)
    }

    @Test
    fun ioRateUsesElapsedTime() {
        assertEquals(1024L, ioBytesPerSecond(DiskSample(0), DiskSample(1024), 1000))
        assertNull(ioBytesPerSecond(DiskSample(0), DiskSample(10), 0))
    }
}
