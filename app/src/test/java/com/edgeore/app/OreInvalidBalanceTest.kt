package com.edgeore.app

import com.edgeore.app.solana.OreDeployLimits
import com.edgeore.app.solana.OreDeployNetwork
import com.edgeore.app.solana.OreDeployReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OreInvalidBalanceTest {
    @Test
    fun negativeBalanceCannotOverflowIntoSpendableFunds() {
        val signer = ByteArray(32) { (it + 1).toByte() }
        val blockhash = ByteArray(32) { 7 }
        val cluster = OreDeployNetwork.MAINNET_CLUSTER
        for (balance in listOf(Long.MIN_VALUE, -1L, 0L, 9_999_999L)) {
            assertEquals(0L, OreDeployLimits.max(balance, cluster))
            val draft =
                OreDeployReview.prepare(
                    signer,
                    1,
                    1,
                    1,
                    blockhash,
                    balance,
                    0,
                    10_000_000_000L,
                    cluster,
                )
            assertFalse(draft.approvable)
            assertEquals(0, draft.message.size)
        }
    }
}
