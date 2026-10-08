package com.edgeore.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports every PASS check from playground/Main.kt (Kotlin Playground, 10/10). */
class EdgeOreCoreTest {
    private fun refuses(body: () -> Unit) = assertTrue("expected refusal", runCatching(body).isFailure)

    @Test fun exactDecimalUnits() = assertEquals(1L, EdgeOreCore.lamports("0.000000001"))
    @Test fun wholeSol() = assertEquals(2_000_000_000L, EdgeOreCore.lamports("2"))
    @Test fun excessPrecisionRefused() = refuses { EdgeOreCore.lamports("0.0000000001") }
    @Test fun negativeInputRefused() = refuses { EdgeOreCore.lamports("-1") }
    @Test fun overflowRefused() = refuses { EdgeOreCore.lamports("999999999999999999999999") }
    @Test fun allSelectedSquaresCharged() = assertEquals(30L, EdgeOreCore.exposure(10, 7))
    @Test fun highMaskBitRefused() = refuses { EdgeOreCore.exposure(10, 1 shl 25) }
    @Test fun exposureOverflowRefused() = refuses { EdgeOreCore.exposure(Long.MAX_VALUE, 3) }
    @Test fun mutationRefused() = assertFalse(EdgeOreCore.sameMessage(byteArrayOf(1, 2), byteArrayOf(1, 3)))
    @Test fun dailyBudgetEnforced() {
        assertFalse(EdgeOreCore.eligible(10, 95, 100))
        assertTrue(EdgeOreCore.eligible(5, 95, 100))
    }

    // Additional edge cases beyond the playground set.
    @Test fun malformedAmountsRefused() {
        listOf("", ".5", "1.", "1,5", "1e9", " 1", "0x10", "١").forEach { s -> refuses { EdgeOreCore.lamports(s) } }
    }
    @Test fun nineDecimalsExact() = assertEquals(1_234_567_891L, EdgeOreCore.lamports("1.234567891"))
    @Test fun zeroMaskRefused() = refuses { EdgeOreCore.exposure(10, 0) }
    @Test fun allTwentyFiveSquares() = assertEquals(250L, EdgeOreCore.exposure(10, (1 shl 25) - 1))
    @Test fun sameBytesAccepted() = assertTrue(EdgeOreCore.sameMessage(byteArrayOf(1, 2), byteArrayOf(1, 2)))
    @Test fun budgetRefusesNonPositiveAndOverspent() {
        assertFalse(EdgeOreCore.eligible(0, 0, 100))
        assertFalse(EdgeOreCore.eligible(1, 101, 100))
        assertFalse(EdgeOreCore.eligible(1, -1, 100))
        assertTrue(EdgeOreCore.eligible(100, 0, 100))
    }

    @Test fun longBoundary() {
        // Long.MAX_VALUE lamports = 9223372036.854775807 SOL is accepted; one more lamport is refused.
        assertEquals(Long.MAX_VALUE, EdgeOreCore.lamports("9223372036.854775807"))
        refuses { EdgeOreCore.lamports("9223372036.854775808") }
    }
}
