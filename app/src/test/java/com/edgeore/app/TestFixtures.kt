package com.edgeore.app

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.solana.ChainGateway
import com.edgeore.app.solana.ChainStatus
import com.edgeore.app.solana.SolanaMessage
import kotlinx.coroutines.CompletableDeferred
import java.util.Collections

/** A real devnet-shaped transfer signed by a real Ed25519 key (test key, never funded). */
class SignedFixture(seed: Byte = 7, lamports: Long = 1_000_000, toSeed: Byte = 3) {
    val payer = Ed25519.KeyPair(ByteArray(32) { seed })
    val to = Ed25519.KeyPair(ByteArray(32) { toSeed }).publicKey
    val blockhash = ByteArray(32) { 9 }
    val lamports = lamports
    val message: ByteArray = SolanaMessage.buildTransfer(payer.publicKey, to, lamports, blockhash)
    val messageSha256: String = Sha256.hex(message)
    val signatureBytes: ByteArray = payer.sign(message)
    val signature: String = Base58.encode(signatureBytes)
    val signedTx: ByteArray = SolanaMessage.unsignedTransaction(message).also { System.arraycopy(signatureBytes, 0, it, 1, 64) }
    val signer: String = Base58.encode(payer.publicKey)
    val recipient: String = Base58.encode(to)
}

/** Scriptable RPC. Records every call so tests can assert "never resent" and call order. */
class FakeGateway : ChainGateway {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile var sendBehavior: suspend (ByteArray) -> String = { tx -> Base58.encode(tx.copyOfRange(1, 65)) }
    @Volatile var statusBehavior: suspend (String) -> ChainStatus = { ChainStatus.NotFound }
    @Volatile var height: Long = 100
    @Volatile var heightFails = false
    val sendCount: Int get() = calls.count { it.startsWith("send") }

    override suspend fun send(signedTx: ByteArray): String { calls += "send"; return sendBehavior(signedTx) }
    override suspend fun status(signature: String): ChainStatus { calls += "status:$signature"; return statusBehavior(signature) }
    override suspend fun blockHeight(): Long { calls += "height"; if (heightFails) throw java.io.IOException("height unavailable"); return height }

    /** Makes send block until released, to test concurrent taps. */
    fun blockSends(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        val prev = sendBehavior
        sendBehavior = { tx -> gate.await(); prev(tx) }
        return gate
    }
}
