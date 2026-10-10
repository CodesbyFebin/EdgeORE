package com.edgeore.app

import com.edgeore.app.device.BatteryDrain
import com.edgeore.app.device.CpuSample
import com.edgeore.app.device.ReadingFreshness
import com.edgeore.app.device.ReadingFreshness.State
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

    // Audit section 11: charging current is not a drain rate, and a missing property is not 0 %/h.
    @Test
    fun batteryDrainOnlyWhileKnownDischarging() {
        // 4000 mAh full (2000 mAh at 50 %), 400 mA draw -> 10 % per hour, whichever sign the device reports.
        assertEquals(10, BatteryDrain.percentPerHour(-400_000, 2_000_000, 50, charging = false))
        assertEquals(10, BatteryDrain.percentPerHour(400_000, 2_000_000, 50, charging = false))
        assertNull(BatteryDrain.percentPerHour(-400_000, 2_000_000, 50, charging = true))
        assertNull(BatteryDrain.percentPerHour(-400_000, 2_000_000, 50, charging = null))
    }

    @Test
    fun batteryDrainUnavailableInputsStayNull() {
        assertNull(BatteryDrain.percentPerHour(Int.MIN_VALUE, 2_000_000, 50, charging = false))
        assertNull(BatteryDrain.percentPerHour(-400_000, Int.MIN_VALUE, 50, charging = false))
        assertNull(BatteryDrain.percentPerHour(-400_000, 2_000_000, 0, charging = false))
        assertNull(BatteryDrain.percentPerHour(0, 2_000_000, 50, charging = false))
        assertNull(BatteryDrain.percentPerHour(-400_000, 0, 50, charging = false))
    }

    // Audit section 11: a reading shown after a failed re-read or long after it was taken must say so.
    @Test
    fun freshnessDistinguishesNotReadFreshStaleAndFailed() {
        assertEquals(State.NOT_READ, ReadingFreshness.state(null, 10_000, lastReadFailed = false))
        assertEquals(State.FRESH, ReadingFreshness.state(10_000, 10_000 + ReadingFreshness.STALE_AFTER_MS, lastReadFailed = false))
        assertEquals(State.STALE, ReadingFreshness.state(10_000, 10_001 + ReadingFreshness.STALE_AFTER_MS, lastReadFailed = false))
        assertEquals(State.LAST_READ_FAILED, ReadingFreshness.state(10_000, 10_500, lastReadFailed = true))
        // Monotonic time going backwards (e.g. a reading from another boot) is never presented as fresh.
        assertEquals(State.STALE, ReadingFreshness.state(10_000, 5_000, lastReadFailed = false))
    }

    @Test
    fun freshnessLabelsNeverPresentOldNumbersAsCurrent() {
        assertEquals("Read under a minute ago", ReadingFreshness.label(1_000, 2_000, false))
        assertEquals("Stale: read 3 min ago", ReadingFreshness.label(0, 3 * 60_000 + 5_000, false))
        assertEquals("Stale: the last read failed, so these are the previous readings", ReadingFreshness.label(1_000, 2_000, true))
        assertEquals("Device readings unavailable: the read failed", ReadingFreshness.label(null, 2_000, true))
        assertEquals("Device readings not taken yet", ReadingFreshness.label(null, 2_000, false))
        assertEquals("Stale: reading age unknown", ReadingFreshness.label(10_000, 5_000, false))
    }
}
