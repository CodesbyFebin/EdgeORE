package com.edgeore.app

import com.edgeore.app.ui.screens.VaultLossWarning
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultLossWarningTest {
    @Test fun shownOnlyBeforeFirstImportUntilAcknowledged() {
        assertTrue(VaultLossWarning.needed(vaultEmpty = true, acknowledged = false))
        assertFalse(VaultLossWarning.needed(vaultEmpty = true, acknowledged = true))
        assertFalse(VaultLossWarning.needed(vaultEmpty = false, acknowledged = false))
        assertTrue(VaultLossWarning.BODY.contains("no recovery phrase"))
    }
}
