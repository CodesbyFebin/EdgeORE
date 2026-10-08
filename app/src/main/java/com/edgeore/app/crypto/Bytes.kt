package com.edgeore.app.crypto

import java.math.BigInteger
import java.security.MessageDigest

object Hex {
    fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "INVALID_HEX" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

object Sha256 {
    fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun hex(bytes: ByteArray): String = Hex.encode(digest(bytes))
    fun hex(text: String): String = hex(text.toByteArray(Charsets.UTF_8))
}

/** Bitcoin-alphabet Base58 as used by Solana addresses and signatures. */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var value = BigInteger(1, input)
        val sb = StringBuilder()
        while (value > BigInteger.ZERO) {
            val (q, r) = value.divideAndRemainder(BASE)
            sb.append(ALPHABET[r.toInt()])
            value = q
        }
        for (b in input) { if (b.toInt() == 0) sb.append('1') else break }
        return sb.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        require(input.isNotEmpty()) { "INVALID_BASE58" }
        var value = BigInteger.ZERO
        for (c in input) {
            val digit = ALPHABET.indexOf(c)
            require(digit >= 0) { "INVALID_BASE58" }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        val raw = value.toByteArray().let { if (it.size > 1 && it[0].toInt() == 0) it.copyOfRange(1, it.size) else it }
        val leading = input.takeWhile { it == '1' }.length
        val body = if (value == BigInteger.ZERO) ByteArray(0) else raw
        return ByteArray(leading) + body
    }

    /** Decodes a Solana public key; refuses anything that is not exactly 32 bytes. */
    fun decodePublicKey(input: String): ByteArray {
        require(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$").matches(input)) { "INVALID_ADDRESS" }
        val bytes = decode(input)
        require(bytes.size == 32) { "INVALID_ADDRESS" }
        return bytes
    }
}
