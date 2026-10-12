package com.edgeore.app.solana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeTypesTest {
    @Test fun blockhashCheckCoversAllCases() {
        assertEquals(BlockhashCheckResult.Unavailable, BlockhashCheckResult.of(null, 100, 20))
        assertTrue(BlockhashCheckResult.of(81, 100, 20) is BlockhashCheckResult.Expired)
        assertEquals(BlockhashCheckResult.Valid(80, 100, 20), BlockhashCheckResult.of(80, 100, 20))
    }
    @Test fun everyStateMapsAndUncertainStatesAreUnknown() {
        OpState.entries.forEach { OperationOutcome.of(it) }
        assertEquals(OperationOutcome.Unknown, OperationOutcome.of(OpState.OUTCOME_UNKNOWN))
        assertEquals(OperationOutcome.Unknown, OperationOutcome.of(OpState.SUBMIT_ATTEMPTED))
    }
    @Test fun unknownNeverAutoResolves() {
        assertEquals(OperationOutcome.Unknown, OperationOutcome.observed(OperationOutcome.Unknown, landed = null, failed = false, finalized = false))
        assertEquals(OperationOutcome.Unknown, OperationOutcome.observed(OperationOutcome.Unknown, landed = false, failed = false, finalized = false))
        assertEquals(OperationOutcome.Landed(true), OperationOutcome.observed(OperationOutcome.Unknown, landed = true, failed = false, finalized = true))
        assertEquals(OperationOutcome.Failed, OperationOutcome.observed(OperationOutcome.Unknown, landed = null, failed = true, finalized = false))
    }
}
