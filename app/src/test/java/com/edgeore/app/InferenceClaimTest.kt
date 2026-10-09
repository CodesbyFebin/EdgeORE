package com.edgeore.app

import com.edgeore.app.ai.ExecutionLocation
import com.edgeore.app.ai.InferenceClaim
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceClaimTest {
    @Test fun privateAddressWithoutAnswerIsNotASession() {
        assertEquals(ExecutionLocation.NOT_RUN, InferenceClaim.classify(false, false, false))
    }

    @Test fun remoteAnswerIsOwnedHost() {
        assertEquals(ExecutionLocation.OWNED_HOST, InferenceClaim.classify(true, false, false))
    }

    @Test fun airplaneModeWithoutRuntimeIsNotOnDevice() {
        assertEquals(ExecutionLocation.NOT_RUN, InferenceClaim.classify(true, true, false))
    }

    @Test fun clientCloseIsNotHostStop() {
        assertEquals("Stopped receiving.", InferenceClaim.cancellationLabel(true, false))
    }

    @Test fun refusalWins() {
        assertEquals(ExecutionLocation.REFUSED, InferenceClaim.classify(true, false, true, refused = true))
    }
}
