package com.edgeore.app.solana

import com.edgeore.app.crypto.Base58
import java.io.ByteArrayOutputStream

/** Minimal legacy Solana message support for the single supported action: System Program transfer. */
object SolanaMessage {
    val SYSTEM_PROGRAM: ByteArray = ByteArray(32)
    private const val TRANSFER_INSTRUCTION = 2

    data class Transfer(
        val feePayer: ByteArray,
        val from: ByteArray,
        val to: ByteArray,
        val lamports: Long,
        val recentBlockhash: ByteArray,
    )

    /** Result of decoding bytes for review. Unknown instructions are never "approximately" shown as transfers. */
    sealed interface Decoded {
        data class SupportedTransfer(val transfer: Transfer) : Decoded
        data class Unsupported(val reason: String) : Decoded
    }

    fun buildTransfer(from: ByteArray, to: ByteArray, lamports: Long, recentBlockhash: ByteArray): ByteArray {
        require(from.size == 32 && to.size == 32 && recentBlockhash.size == 32) { "INVALID_KEY_LENGTH" }
        require(lamports > 0) { "INVALID_AMOUNT" }
        val self = from.contentEquals(to)
        val keys = if (self) listOf(from, SYSTEM_PROGRAM) else listOf(from, to, SYSTEM_PROGRAM)
        val out = ByteArrayOutputStream()
        out.write(1) // required signatures
        out.write(0) // readonly signed
        out.write(1) // readonly unsigned (system program)
        writeCompactU16(out, keys.size)
        keys.forEach { out.write(it) }
        out.write(recentBlockhash)
        writeCompactU16(out, 1)
        out.write(keys.size - 1) // program id index
        val accountIdx = if (self) intArrayOf(0, 0) else intArrayOf(0, 1)
        writeCompactU16(out, accountIdx.size)
        accountIdx.forEach { out.write(it) }
        val data = ByteArray(12)
        putU32LE(data, 0, TRANSFER_INSTRUCTION.toLong())
        putU64LE(data, 4, lamports)
        writeCompactU16(out, data.size)
        out.write(data)
        return out.toByteArray()
    }

    /** Unsigned wire transaction with one zeroed signature slot, as handed to the wallet for signing. */
    fun unsignedTransaction(message: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeCompactU16(out, 1)
        out.write(ByteArray(64))
        out.write(message)
        return out.toByteArray()
    }

    data class SignedTransaction(val signatures: List<ByteArray>, val message: ByteArray)

    fun splitTransaction(tx: ByteArray): SignedTransaction {
        val r = Reader(tx)
        val n = r.compactU16()
        require(n in 1..8) { "BAD_SIGNATURE_COUNT" }
        val sigs = List(n) { r.bytes(64) }
        val message = r.rest()
        require(message.isNotEmpty()) { "EMPTY_MESSAGE" }
        return SignedTransaction(sigs, message)
    }

    fun decode(message: ByteArray): Decoded = try {
        decodeUnchecked(message)
    } catch (e: Exception) {
        Decoded.Unsupported("Malformed message")
    }

    private fun decodeUnchecked(message: ByteArray): Decoded {
        val r = Reader(message)
        val first = r.u8()
        if (first and 0x80 != 0) return Decoded.Unsupported("Versioned message (v0) is not supported for review")
        val numSigners = first
        val roSigned = r.u8()
        val roUnsigned = r.u8()
        val keyCount = r.compactU16()
        require(keyCount in 1..64)
        val keys = List(keyCount) { r.bytes(32) }
        val blockhash = r.bytes(32)
        val ixCount = r.compactU16()
        if (numSigners != 1 || roSigned != 0) return Decoded.Unsupported("Only single-signer messages are supported")
        if (ixCount != 1) return Decoded.Unsupported("Message contains $ixCount instructions; only one transfer is supported")
        val programIdx = r.u8()
        val accCount = r.compactU16()
        val accs = List(accCount) { r.u8() }
        val dataLen = r.compactU16()
        val data = r.bytes(dataLen)
        if (r.remaining() != 0) return Decoded.Unsupported("Trailing bytes after instructions")
        if (programIdx >= keys.size || !keys[programIdx].contentEquals(SYSTEM_PROGRAM)) return Decoded.Unsupported("Unknown program")
        if (roUnsigned != 1 || programIdx != keys.size - 1) return Decoded.Unsupported("Unexpected account layout")
        if (accCount != 2 || accs.any { it >= keys.size - 1 }) return Decoded.Unsupported("Unexpected transfer accounts")
        if (dataLen != 12 || getU32LE(data, 0) != TRANSFER_INSTRUCTION.toLong()) return Decoded.Unsupported("Unknown System Program instruction")
        if (accs[0] != 0) return Decoded.Unsupported("Transfer source is not the fee payer")
        val lamports = getU64LE(data, 4)
        if (lamports <= 0) return Decoded.Unsupported("Non-positive amount")
        return Decoded.SupportedTransfer(Transfer(keys[0], keys[accs[0]], keys[accs[1]], lamports, blockhash))
    }

    fun signatureBase58(signature: ByteArray): String = Base58.encode(signature)

    private fun writeCompactU16(out: ByteArrayOutputStream, value: Int) {
        require(value in 0..0xffff)
        var v = value
        while (true) {
            val b = v and 0x7f
            v = v ushr 7
            if (v == 0) { out.write(b); return }
            out.write(b or 0x80)
        }
    }
    private fun putU32LE(a: ByteArray, off: Int, v: Long) { for (i in 0 until 4) a[off + i] = ((v ushr (8 * i)) and 0xff).toByte() }
    private fun putU64LE(a: ByteArray, off: Int, v: Long) { for (i in 0 until 8) a[off + i] = ((v ushr (8 * i)) and 0xff).toByte() }
    private fun getU32LE(a: ByteArray, off: Int): Long { var v = 0L; for (i in 0 until 4) v = v or ((a[off + i].toLong() and 0xff) shl (8 * i)); return v }
    private fun getU64LE(a: ByteArray, off: Int): Long { var v = 0L; for (i in 0 until 8) v = v or ((a[off + i].toLong() and 0xff) shl (8 * i)); return v }

    private class Reader(private val b: ByteArray) {
        private var p = 0
        fun u8(): Int { require(p < b.size) { "EOF" }; return b[p++].toInt() and 0xff }
        fun bytes(n: Int): ByteArray { require(n >= 0 && p + n <= b.size) { "EOF" }; return b.copyOfRange(p, p + n).also { p += n } }
        fun rest(): ByteArray = b.copyOfRange(p, b.size).also { p = b.size }
        fun remaining() = b.size - p
        fun compactU16(): Int {
            var result = 0
            var shift = 0
            for (i in 0 until 3) {
                val x = u8()
                result = result or ((x and 0x7f) shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
            throw IllegalArgumentException("BAD_COMPACT_U16")
        }
    }
}
