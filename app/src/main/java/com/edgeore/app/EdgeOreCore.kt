package com.edgeore.app

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Domain core verified in Kotlin Playground (playground/Main.kt, 10/10 PASS) and ported to
 * unit tests in EdgeOreCoreTest. Android note: BigInteger.longValueExact() requires API 31,
 * so the overflow check is done with bitLength() to keep minSdk 26 safe (same semantics).
 */
object EdgeOreCore {
    private val LAMPORTS_PER_SOL = BigInteger("1000000000")

    fun lamports(input: String): Long {
        require(Regex("""^[0-9]+(\.[0-9]{1,9})?$""").matches(input)) { "INVALID_AMOUNT" }
        val parts = input.split('.')
        val units = BigInteger(parts[0]) * LAMPORTS_PER_SOL +
            BigInteger((parts.getOrNull(1) ?: "").padEnd(9, '0'))
        if (units.bitLength() > 63) throw ArithmeticException("BigInteger out of long range")
        return units.toLong()
    }

    fun exposure(perSquare: Long, mask: Int): Long {
        require(perSquare > 0 && mask > 0 && (mask ushr 25) == 0) { "INVALID_DEPLOY" }
        return Math.multiplyExact(perSquare, Integer.bitCount(mask).toLong())
    }

    fun sameMessage(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun eligible(total: Long, spent: Long, limit: Long): Boolean =
        total > 0 && spent >= 0 && limit >= spent && total <= limit - spent
}
