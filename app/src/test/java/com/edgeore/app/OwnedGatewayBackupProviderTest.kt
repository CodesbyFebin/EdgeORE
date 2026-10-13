package com.edgeore.app

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.storage.OwnedGatewayBackupProvider
import com.edgeore.app.storage.UploadRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class OwnedGatewayBackupProviderTest {
    @Test(expected = IllegalArgumentException::class)
    fun refusesCleartext() {
        OwnedGatewayBackupProvider("http://example.com", token = { "x".repeat(40) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesUrlCredentials() {
        OwnedGatewayBackupProvider("https://secret@example.com", token = { "x".repeat(40) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesQuery() {
        OwnedGatewayBackupProvider("https://example.com?token=secret", token = { "x".repeat(40) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesProviderPath() {
        OwnedGatewayBackupProvider("https://example.com/admin", token = { "x".repeat(40) })
    }

    // Added in review. Each of these must be refused before any connection is opened: the endpoint is a closed local
    // port, so reaching the network would surface as an IOException instead of IllegalArgumentException.
    private val closed = "https://127.0.0.1:9"
    private val body = "ciphertext".toByteArray()
    private val good = UploadRequest("job-1", body, Sha256.hex(body), body.size.toLong())

    private fun refusedOffline(block: suspend () -> Unit) {
        val e = runCatching { runBlocking { block() } }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $e", e is IllegalArgumentException)
    }

    @Test fun shortCredentialRefusedBeforeConnecting() =
        refusedOffline { OwnedGatewayBackupProvider(closed, token = { "short" }).status("job-1") }

    @Test fun credentialWithHeaderBreakRefusedBeforeConnecting() =
        refusedOffline { OwnedGatewayBackupProvider(closed, token = { "x".repeat(40) + "\r\nX-Injected: 1" }).status("job-1") }

    @Test fun pathLikeJobIdRefusedBeforeConnecting() =
        refusedOffline { OwnedGatewayBackupProvider(closed, token = { "x".repeat(40) }).status("../objects") }

    @Test fun nonDigestRemoteRefRefusedBeforeConnecting() =
        refusedOffline {
            OwnedGatewayBackupProvider(
                closed,
                token = { "x".repeat(40) },
            ).download("https://elsewhere.example/x", 10, ByteArrayOutputStream())
        }

    @Test fun mismatchedCiphertextDigestRefusedBeforeConnecting() =
        refusedOffline {
            OwnedGatewayBackupProvider(
                closed,
                token = { "x".repeat(40) },
            ).upload(UploadRequest("job-1", body, "0".repeat(64), body.size.toLong()))
        }

    @Test fun oversizedUploadRefusedBeforeConnecting() {
        val big = ByteArray(8_001_025)
        refusedOffline {
            OwnedGatewayBackupProvider(
                closed,
                token = { "x".repeat(40) },
            ).upload(UploadRequest("job-1", big, Sha256.hex(big), big.size.toLong()))
        }
    }

    @Test fun wellFormedRequestDoesReachTheNetworkLayer() {
        // Control for the tests above: a valid request gets past validation and fails only at the closed port.
        val e =
            runCatching {
                runBlocking {
                    OwnedGatewayBackupProvider(
                        closed,
                        token = { "x".repeat(40) },
                    ).upload(good)
                }
            }.exceptionOrNull()
        assertTrue("expected an I/O failure, got $e", e is java.io.IOException)
    }
}
