package com.edgeore.app

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.scan.AddressQr
import com.edgeore.app.scan.QrDecoder
import com.edgeore.app.solana.PlainLanguage
import com.edgeore.app.solana.SolanaMessage
import com.edgeore.app.solana.TransferReview
import com.edgeore.app.ui.ReviewInput
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QR address input, inline review checks and the plain-language layer (ported/adapted from the earlier Clearance app). */
class UxFromClearanceTest {
    private val payer = Ed25519.KeyPair(ByteArray(32) { 7 })
    private val dest = "73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n"
    private val blockhash = ByteArray(32) { 9 }

    private fun refused(raw: String): String = (AddressQr.parse(raw) as AddressQr.Result.Refused).reason

    @Test fun plainAddressFillsDestinationOnly() {
        val r = AddressQr.parse("  $dest \n") as AddressQr.Result.Filled
        assertEquals(dest, r.destination); assertNull(r.amountSol); assertEquals(AddressQr.Source.PLAIN_ADDRESS, r.source)
    }

    @Test fun solanaPayTransferFillsDestinationAndAmount() {
        val r = AddressQr.parse("solana:$dest?amount=0.01&label=Coffee%20stand&message=Thanks") as AddressQr.Result.Filled
        assertEquals(dest, r.destination); assertEquals("0.01", r.amountSol); assertEquals("Coffee stand", r.label)
        assertEquals(AddressQr.Source.SOLANA_PAY, r.source)
    }

    @Test fun anythingEdgeOreCannotBuildIsRefusedNotDropped() {
        assertTrue(refused("solana:$dest?amount=1&spl-token=EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v").contains("SPL token"))
        assertTrue(refused("solana:$dest?reference=$dest").contains("reference"))
        assertTrue(refused("solana:$dest?memo=hi").contains("memo"))
        assertTrue(refused("solana:https%3A%2F%2Fexample.com%2Fpay").contains("transaction request"))
        assertTrue(refused("solana:$dest?amount=1&amount=2").contains("ambiguous"))
        assertTrue(refused("solana:$dest?foo=1").contains("foo"))
        assertTrue(refused("solana:$dest?amount=-1").contains("amount"))
        assertTrue(refused("solana:$dest?amount=0.0000000001").contains("amount"))
        assertTrue(refused("solana:notanaddress").contains("recipient"))
        assertTrue(refused("https://evil.example/$dest").contains("not a Solana address"))
        assertTrue(refused("").contains("empty"))
        assertTrue(refused("1".repeat(AddressQr.MAX_LEN + 1)).contains("too long"))
    }

    @Test fun qrRoundTripThroughZxingDecoder() {
        val text = "solana:$dest?amount=0.01"
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val decoded = QrDecoder.decodeArgb(px, m.width, m.height)
        assertEquals(text, decoded)
        val lum = ByteArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0 else 0xFF.toByte() }
        assertEquals(text, QrDecoder.decodeLuminance(lum, m.width, m.width, m.height))
        assertEquals(dest, (AddressQr.parse(decoded) as AddressQr.Result.Filled).destination)
    }

    @Test fun noQrInBlankImage() {
        assertNull(QrDecoder.decodeArgb(IntArray(200 * 200) { 0xFFFFFFFF.toInt() }, 200, 200))
    }

    @Test fun inlineChecksGatePrepareButNeverReplaceTheByteCheck() {
        assertFalse(ReviewInput.destination("").ok); assertFalse(ReviewInput.destination("").error)
        assertTrue(ReviewInput.destination("0OIl").error)
        assertTrue(ReviewInput.destination(dest).ok)
        assertFalse(ReviewInput.amount("").ok); assertFalse(ReviewInput.amount("").error)
        assertTrue(ReviewInput.amount("abc").error); assertTrue(ReviewInput.amount("0").error); assertTrue(ReviewInput.amount("1.0000000001").error)
        assertEquals("10000000 lamports.", ReviewInput.amount("0.01").text)
    }

    @Test fun reviewDefaultsAreEmpty() {
        val s = ReviewState()
        assertEquals("", s.destination); assertEquals("", s.amount)
    }

    @Test fun plainLanguageComesFromDecodedBytesNotInputs() {
        val d = TransferReview.prepare(payer.publicKey, dest, "0.01", blockhash, 0, 50_000_000)
        val e = PlainLanguage.explain(d, feeKnown = true, feeLamports = 5000)
        assertTrue(e.supported)
        assertEquals("Send 0.01 SOL to 73Cc…R93n", e.headline)
        assertTrue(e.points.any { it.contains("5000 lamports") })
        assertTrue(e.points.any { it.contains("devnet") })
        // Tamper the draft's decoded transfer: the explanation follows the bytes, not what was typed.
        val dec = d.decoded as SolanaMessage.Decoded.SupportedTransfer
        val other = ByteArray(32) { 3 }
        val forged = d.copy(decoded = SolanaMessage.Decoded.SupportedTransfer(dec.transfer.copy(to = other, lamports = 42)))
        val e2 = PlainLanguage.explain(forged, feeKnown = false, feeLamports = null)
        assertTrue(e2.headline.contains("0.000000042 SOL")); assertTrue(e2.headline.contains(Base58.encode(other).take(4)))
        assertTrue(e2.points.any { it.contains("fee is unknown") })
    }

    @Test fun plainLanguageRefusesToExplainUnsupportedBytes() {
        val d = TransferReview.prepare(payer.publicKey, "not-an-address", "0.01", blockhash, 0, 50_000_000)
        val e = PlainLanguage.explain(d, false, null)
        assertFalse(e.supported); assertTrue(e.headline.contains("approval stays off"))
        val selfD = TransferReview.prepare(payer.publicKey, Base58.encode(payer.publicKey), "0.5", blockhash, 0, 1_000_000_000)
        assertTrue(PlainLanguage.explain(selfD, false, null).headline.startsWith("Self-transfer"))
    }
}
