package com.edgeore.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.edgeore.app.ui.ReceiptActivity
import com.edgeore.app.ui.components.Domain
import com.edgeore.app.ui.components.monotoneTangents
import com.edgeore.app.ui.components.plotPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Chart geometry and the receipts-per-day series behind the Receipts activity chart (agentic redesign). */
class ChartsAndActivityTest {
    @Test fun flatSeriesGetsAUnitDomainInsteadOfDividingByZero() {
        val d = Domain.padded(listOf(5.0, 5.0, 5.0), 0.1)
        assertEquals(4.0, d.lo, 0.0); assertEquals(6.0, d.hi, 0.0)
        assertEquals(Domain(0.0, 1.0), Domain.counts(listOf(0.0, 0.0)))
        assertEquals(Domain(0.0, 7.0), Domain.counts(listOf(0.0, 7.0, 3.0)))
    }

    @Test fun plotPointsSpanTheRectAndPutZeroOnTheFloor() {
        val pts = plotPoints(listOf(0.0, 2.0, 4.0), Rect(0f, 0f, 100f, 50f), Domain.counts(listOf(0.0, 2.0, 4.0)))
        assertEquals(Offset(0f, 50f), pts.first())
        assertEquals(Offset(100f, 0f), pts.last())
        assertEquals(50f, pts[1].x, 0.001f)
        assertTrue(plotPoints(emptyList(), Rect(0f, 0f, 1f, 1f), Domain(0.0, 1.0)).isEmpty())
    }

    @Test fun monotoneCurveIsFlatAtLocalExtremesAndDoesNotOvershoot() {
        val pts = listOf(Offset(0f, 10f), Offset(10f, 0f), Offset(20f, 10f), Offset(30f, 10f), Offset(40f, 2f))
        val t = monotoneTangents(pts)
        assertEquals(0f, t[1], 0f) // peak
        assertEquals(0f, t[2], 0f) // flat run
        assertEquals(0f, t[3], 0f)
        for (i in 0 until pts.size - 1) {
            val h = (pts[i + 1].x - pts[i].x) / 3f
            val c1 = pts[i].y + t[i] * h
            val c2 = pts[i + 1].y - t[i + 1] * h
            val lo = minOf(pts[i].y, pts[i + 1].y); val hi = maxOf(pts[i].y, pts[i + 1].y)
            assertTrue("segment $i control 1 overshoots", c1 in lo..hi)
            assertTrue("segment $i control 2 overshoots", c2 in lo..hi)
        }
    }


    @Test fun receiptActivityCountsOnlyRealTimestampsPerDay() {
        val today = LocalDate.of(2026, 10, 11)
        val s = ReceiptActivity.lastDays(
            listOf("2026-10-11T01:00:00Z", "2026-10-11T23:00:00Z", "2026-10-10T12:00:00Z", "2026-09-01T00:00:00Z", "not a time"),
            days = 14, today = today, zone = ZoneOffset.UTC,
        )
        assertEquals(14, s.days.size)
        assertEquals(today, s.days.last())
        assertEquals(2, s.counts.last())
        assertEquals(1, s.counts[12])
        assertEquals(3, s.total) // the September receipt is outside the window
        assertEquals(1, s.unreadable)
        assertTrue(s.summary().startsWith("3 receipts in the last 14 days"))
    }

    @Test fun noReceiptsMeansAnEmptySeriesNotASampleTrend() {
        val s = ReceiptActivity.lastDays(emptyList(), today = LocalDate.of(2026, 10, 11), zone = ZoneOffset.UTC)
        assertTrue(s.isEmpty)
        assertTrue(s.counts.all { it == 0 })
        assertEquals("No receipts in the last 14 days.", s.summary())
    }
}
