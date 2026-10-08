package com.edgeore.app

import com.edgeore.app.solana.SpendLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendLedgerTest {
    private fun ledger() = SpendLedger()

    @Test fun overflowRefuses() {
        val result = ledger().reserve("op", Long.MAX_VALUE, "devnet", "a", "m", alreadySpent = 1, dailyLimit = Long.MAX_VALUE)
        assertTrue(result is SpendLedger.ReserveResult.Refused)
    }

    @Test fun secondReserveOverLimitRefuses() {
        val ledger = ledger()
        assertTrue(ledger.reserve("a", 60, "devnet", "s", "m1", 0, 100) is SpendLedger.ReserveResult.Reserved)
        val second = ledger.reserve("b", 50, "devnet", "s", "m2", 0, 100)
        assertTrue(second is SpendLedger.ReserveResult.Refused)
        assertEquals(60L, ledger.pending())
    }

    @Test fun duplicateIdDoesNotDoubleCount() {
        val ledger = ledger()
        ledger.reserve("a", 10, "devnet", "s", "m", 0, 100)
        val again = ledger.reserve("a", 10, "devnet", "s", "m", 0, 100)
        assertTrue(again is SpendLedger.ReserveResult.Reserved)
        assertEquals(10L, ledger.pending())
    }

    @Test fun timeoutDoesNotFreeBudget() {
        val ledger = ledger()
        ledger.reserve("a", 40, "devnet", "s", "m", 0, 100)
        assertTrue(ledger.markUnknown("a"))
        assertFalse(ledger.release("a"))
        assertEquals(40L, ledger.pending())
        val second = ledger.reserve("b", 70, "devnet", "s", "m2", 0, 100)
        assertTrue(second is SpendLedger.ReserveResult.Refused)
    }

    @Test fun releaseBeforeBroadcastFreesBudget() {
        val ledger = ledger()
        ledger.reserve("a", 40, "devnet", "s", "m", 0, 100)
        assertTrue(ledger.release("a"))
        assertEquals(0L, ledger.pending())
        assertTrue(ledger.reserve("b", 40, "devnet", "s", "m2", 0, 100) is SpendLedger.ReserveResult.Reserved)
    }

    @Test fun settleStopsCountingAsPending() {
        val ledger = ledger()
        ledger.reserve("a", 40, "devnet", "s", "m", 0, 100)
        assertTrue(ledger.settle("a"))
        assertEquals(0L, ledger.pending())
        assertFalse(ledger.settle("a"))
    }
}
