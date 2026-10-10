package com.edgeore.app

import com.edgeore.app.storage.OwnedGatewayBackupProvider
import org.junit.Test

class OwnedGatewayBackupProviderTest {
    @Test(expected = IllegalArgumentException::class)
    fun refusesCleartext() { OwnedGatewayBackupProvider("http://example.com", token = { "x".repeat(40) }) }
    @Test(expected = IllegalArgumentException::class)
    fun refusesUrlCredentials() { OwnedGatewayBackupProvider("https://secret@example.com", token = { "x".repeat(40) }) }
    @Test(expected = IllegalArgumentException::class)
    fun refusesQuery() { OwnedGatewayBackupProvider("https://example.com?token=secret", token = { "x".repeat(40) }) }
    @Test(expected = IllegalArgumentException::class)
    fun refusesProviderPath() { OwnedGatewayBackupProvider("https://example.com/admin", token = { "x".repeat(40) }) }
}
