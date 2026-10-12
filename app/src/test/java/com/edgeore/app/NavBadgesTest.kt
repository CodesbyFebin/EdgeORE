package com.edgeore.app

import com.edgeore.app.solana.OpState
import com.edgeore.app.solana.PendingOperation
import com.edgeore.app.ui.Destination
import com.edgeore.app.ui.NavBadges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bottom-navigation counts come only from local operation and receipt state. */
class NavBadgesTest {
    private fun op(id: String, state: OpState) = PendingOperation(
        id = id, cluster = "devnet", signer = "S", recipient = "R", lamports = 1, messageSha256 = "00", messageBase64 = "",
        blockhash = "B", lastValidBlockHeight = 1, budgetDay = "2026-10-11", reviewedAt = "2026-10-11T00:00:00Z", state = state,
    )

    @Test fun badgesComeOnlyFromLocalStateAndHideAtZero() {
        assertTrue(NavBadges.of(emptyList(), 0).isEmpty())
        val ops = listOf(op("a", OpState.REVIEWED), op("b", OpState.OUTCOME_UNKNOWN), op("c", OpState.SIGNED))
        val badges = NavBadges.of(ops, 2)
        assertEquals(2, badges[Destination.Mine]?.count)
        assertEquals("2 operations awaiting an outcome", badges[Destination.Mine]?.spoken)
        assertEquals(2, badges[Destination.Receipts]?.count)
        assertFalse(badges.containsKey(Destination.AI))
        assertNotNull(badges[Destination.Receipts]?.spoken)
    }
}
