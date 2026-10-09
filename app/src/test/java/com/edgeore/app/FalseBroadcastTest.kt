package com.edgeore.app

import com.edgeore.app.receipts.chainClaimContradictsSignature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FalseBroadcastTest {
    @Test fun confirmedWithoutSignatureIsAContradiction() {
        assertTrue(chainClaimContradictsSignature("CONFIRMED", false, ""))
    }

    @Test fun broadcastFlagWithoutSignatureIsAContradiction() {
        assertTrue(chainClaimContradictsSignature("NOT_OBSERVED", true, ""))
    }

    @Test fun missingObservationIsNotAContradiction() {
        assertFalse(chainClaimContradictsSignature("NOT_OBSERVED", false, ""))
    }

    @Test fun presentSignatureIsNotJudgedHere() {
        assertFalse(chainClaimContradictsSignature("CONFIRMED", true, "5Kb"))
    }
}
