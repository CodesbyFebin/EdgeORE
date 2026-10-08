package com.edgeore.app

import java.security.MessageDigest

/** Exact-byte binding: the bytes a person reviewed must be the bytes that get signed. */
object MessageBinding {
    fun unchanged(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}

/**
 * Spend guard for ORE-style per-square deployments (from the original starter).
 * Returns null when allowed, or a refusal code.
 */
object SpendGuard {
    fun check(amount: Long, squares: Int, roundLimit: Long, spentToday: Long, dailyLimit: Long): String? {
        if (amount <= 0 || squares !in 1..25 || roundLimit < 0 || spentToday < 0 || dailyLimit < 0) return "INVALID_INPUT"
        val total = try {
            Math.multiplyExact(amount, squares.toLong())
        } catch (_: ArithmeticException) {
            return "OVERFLOW"
        }
        if (total > roundLimit) return "ROUND_LIMIT"
        if (spentToday > dailyLimit || total > dailyLimit - spentToday) return "DAILY_LIMIT"
        return null
    }
}
