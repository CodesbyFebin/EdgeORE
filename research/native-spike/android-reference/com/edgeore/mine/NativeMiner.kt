package com.edgeore.mine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Computational spike only. Not an ORE protocol client or foreground service. */
object NativeMiner {
    private val loaded: Boolean by lazy {
        runCatching { System.loadLibrary("edgeore_drillx_spike") }.isSuccess
    }
    @JvmStatic private external fun mineChunk(challenge: ByteArray, startNonce: Long, budgetMs: Long): ByteArray
    data class Result(val hasSolution: Boolean, val exhausted: Boolean, val attempts: Long,
        val nextNonce: Long, val bestNonce: Long, val elapsedNanos: Long, val difficulty: Int,
        val digest: ByteArray, val hash: ByteArray) {
        val attemptsPerSecond: Double get() = if (elapsedNanos > 0) attempts.toDouble() * 1e9 / elapsedNanos else 0.0
    }
    suspend fun compute(challenge: ByteArray, startNonce: Long, budgetMs: Long = 100): Result = withContext(Dispatchers.Default) {
        check(loaded) { "NATIVE_LIBRARY_UNAVAILABLE" }
        require(challenge.size == 32) { "CHALLENGE_LENGTH" }
        require(budgetMs in 1..250) { "BUDGET_RANGE" }
        val out = mineChunk(challenge.copyOf(), startNonce, budgetMs)
        require(out.size == 92) { "BRIDGE_LENGTH" }
        val b = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        require(b.int == 1) { "BRIDGE_VERSION" }
        val flags = b.int
        require(flags and 3.inv() == 0) { "UNKNOWN_FLAGS" }
        val attempts = b.long; val next = b.long; val best = b.long; val elapsed = b.long; val difficulty = b.int
        require(attempts >= 0 && elapsed >= 0 && difficulty in 0..256) { "INVALID_METRICS" }
        val digest = ByteArray(16).also { b.get(it) }; val hash = ByteArray(32).also { b.get(it) }
        Result(flags and 1 != 0, flags and 2 != 0, attempts, next, best, elapsed, difficulty, digest, hash)
    }
}
