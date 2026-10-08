package com.edgeore.app

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.solana.SolanaMessage
import com.edgeore.app.solana.TransferReview
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaMessageTest {
    private val payer = Ed25519.KeyPair(ByteArray(32) { 7 })
    private val other = ByteArray(32) { (it + 1).toByte() }
    private val blockhash = ByteArray(32) { 9 }

    @Test fun base58RoundTripAndKnownValues() {
        assertEquals("11111111111111111111111111111111", Base58.encode(ByteArray(32)))
        assertArrayEquals(ByteArray(32), Base58.decode("11111111111111111111111111111111"))
        val k = payer.publicKey
        assertArrayEquals(k, Base58.decodePublicKey(Base58.encode(k)))
        assertEquals("2NEpo7TZRRrLZSi2U", Base58.encode("Hello World!".toByteArray()))
    }

    @Test fun invalidAddressRefused() {
        assertTrue(runCatching { Base58.decodePublicKey("0OIl") }.isFailure)
        assertTrue(runCatching { Base58.decodePublicKey("1111") }.isFailure)
    }

    @Test fun transferEncodesAndDecodes() {
        val msg = SolanaMessage.buildTransfer(payer.publicKey, other, 1_000_000, blockhash)
        // header(3) + keys len(1) + 3*32 + blockhash 32 + ix count(1) + program(1) + acc len(1) + 2 + data len(1) + 12
        assertEquals(3 + 1 + 96 + 32 + 1 + 1 + 1 + 2 + 1 + 12, msg.size)
        val d = SolanaMessage.decode(msg) as SolanaMessage.Decoded.SupportedTransfer
        assertArrayEquals(payer.publicKey, d.transfer.from)
        assertArrayEquals(other, d.transfer.to)
        assertEquals(1_000_000L, d.transfer.lamports)
    }

    @Test fun selfTransferDeduplicatesKeys() {
        val msg = SolanaMessage.buildTransfer(payer.publicKey, payer.publicKey, 5, blockhash)
        val d = SolanaMessage.decode(msg) as SolanaMessage.Decoded.SupportedTransfer
        assertArrayEquals(d.transfer.from, d.transfer.to)
    }

    @Test fun unknownProgramIsUnsupported() {
        val msg = SolanaMessage.buildTransfer(payer.publicKey, other, 5, blockhash)
        val programOffset = 4 + 64 // header + key count + 2 keys
        msg[programOffset] = 1 // program id is no longer the System Program
        assertTrue(SolanaMessage.decode(msg) is SolanaMessage.Decoded.Unsupported)
    }

    @Test fun trailingBytesUnsupported() {
        val msg = SolanaMessage.buildTransfer(payer.publicKey, other, 5, blockhash) + byteArrayOf(0)
        assertTrue(SolanaMessage.decode(msg) is SolanaMessage.Decoded.Unsupported)
    }

    @Test fun versionedMessageUnsupported() {
        val msg = byteArrayOf(0x80.toByte()) + SolanaMessage.buildTransfer(payer.publicKey, other, 5, blockhash)
        assertTrue(SolanaMessage.decode(msg) is SolanaMessage.Decoded.Unsupported)
    }

    @Test fun reviewRefusesOverBudget() {
        val d = TransferReview.prepare(payer.publicKey, Base58.encode(other), "0.06", blockhash, 0, 50_000_000)
        assertFalse(d.approvable)
        assertEquals("Daily budget would be exceeded", d.refusal)
    }

    @Test fun reviewRefusesBadAmountAndAddress() {
        assertFalse(TransferReview.prepare(payer.publicKey, Base58.encode(other), "0.0000000001", blockhash, 0, 50_000_000).approvable)
        assertFalse(TransferReview.prepare(payer.publicKey, "not-an-address", "0.001", blockhash, 0, 50_000_000).approvable)
        assertFalse(TransferReview.prepare(payer.publicKey, Base58.encode(other), "0", blockhash, 0, 50_000_000).approvable)
    }

    @Test fun walletReturnMustMatchReviewedBytesAndSignature() {
        val d = TransferReview.prepare(payer.publicKey, Base58.encode(other), "0.001", blockhash, 0, 50_000_000)
        assertTrue(d.approvable)
        val signed = signedTx(payer.sign(d.message), d.message)
        val ok = TransferReview.verifyWalletReturn(d.message, payer.publicKey, signed)
        assertTrue(ok is TransferReview.WalletReturn.Verified)

        // Wallet changed the amount (different message) even with a valid signature over it.
        val changed = SolanaMessage.buildTransfer(payer.publicKey, other, 2_000_000, blockhash)
        val r1 = TransferReview.verifyWalletReturn(d.message, payer.publicKey, signedTx(payer.sign(changed), changed))
        assertTrue(r1 is TransferReview.WalletReturn.Refused)

        // Same message, forged signature.
        val r2 = TransferReview.verifyWalletReturn(d.message, payer.publicKey, signedTx(ByteArray(64) { 1 }, d.message))
        assertTrue(r2 is TransferReview.WalletReturn.Refused)

        // Malformed bytes.
        assertTrue(TransferReview.verifyWalletReturn(d.message, payer.publicKey, byteArrayOf(5)) is TransferReview.WalletReturn.Refused)
    }

    @Test fun unsignedTransactionLayout() {
        val m = SolanaMessage.buildTransfer(payer.publicKey, other, 5, blockhash)
        val tx = SolanaMessage.unsignedTransaction(m)
        assertEquals(1, tx[0].toInt())
        assertArrayEquals(m, SolanaMessage.splitTransaction(tx).message)
    }

    private fun signedTx(sig: ByteArray, message: ByteArray) = byteArrayOf(1) + sig + message
}
