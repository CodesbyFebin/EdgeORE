package com.edgeore.app.ore

import com.edgeore.app.crypto.Base58
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * ORE v3 on-chain layouts, read-only. Every constant and offset here is taken from regolith-labs/ore at commit
 * [SOURCE_COMMIT], which verify.osec.io reported (2026-10-12) as the verified build of the deployed mainnet program:
 *   api/src/lib.rs        declare_id!("oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv")
 *   api/src/consts.rs     BOARD_ADDRESS, TREASURY_ADDRESS, seeds "board"/"round"/"treasury", TOKEN_DECIMALS = 11
 *   api/src/state/mod.rs  OreAccount discriminators: Treasury = 104, Board = 105, Round = 109
 *   api/src/state/board.rs, round.rs, treasury.rs  #[repr(C)] Pod structs (all u64 / [u64; 25] / [u8; 32] / Pubkey)
 * Steel accounts start with an 8-byte discriminator (first byte = the OreAccount value, the rest zero), then the struct.
 * Decoders refuse wrong owners, discriminators and sizes instead of guessing.
 */
object OreV3 {
    const val SOURCE_REPO = "https://github.com/regolith-labs/ore"
    const val SOURCE_COMMIT = "48c203bd75db3cc45105ec29d8f8db719e5a2263"
    const val PROGRAM_ID = "oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv"
    const val BOARD_ADDRESS = "BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi"
    const val TREASURY_ADDRESS = "45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG"
    const val SQUARES = 25
    const val LAMPORTS_PER_SOL = 1_000_000_000L
    /** TOKEN_DECIMALS = 11: 10^11 indivisible units ("grams") per ORE. */
    const val GRAMS_PER_ORE = 100_000_000_000L
    /** Board.end_slot while a round waits for its first deploy (reset.rs, u64::MAX). */
    const val END_SLOT_WAITING = -1L // u64::MAX read as a signed Long

    const val DISC_TREASURY = 104
    const val DISC_BOARD = 105
    const val DISC_ROUND = 109
    const val BOARD_SIZE = 8 + 4 * 8
    const val TREASURY_SIZE = 8 + 8 + 16 + 8 + 8
    const val ROUND_SIZE = 8 + 8 + 3 * 25 * 8 + 32 + 8 + 8 + 32 + 25 * 8 + 8 + 8 + 8 + 32

    data class Board(val roundId: Long, val startSlot: Long, val endSlot: Long, val productionCostEma: Long)
    data class Treasury(val motherlodeGrams: Long)
    data class Round(
        val id: Long, val deployedLamports: List<Long>, val count: List<Long>,
        val expiresAt: Long, val motherlodeGrams: Long, val totalMiners: Long,
    ) {
        val totalDeployedLamports: Long get() = deployedLamports.sum()
    }

    class DecodeException(message: String) : Exception(message)

    private fun body(owner: String, data: ByteArray, disc: Int, size: Int, what: String): ByteBuffer {
        if (owner != PROGRAM_ID) throw DecodeException("$what is not owned by the ORE v3 program")
        if (data.size != size) throw DecodeException("$what has ${data.size} bytes; the verified layout has $size")
        if ((data[0].toInt() and 0xff) != disc || (1 until 8).any { data[it].toInt() != 0 }) throw DecodeException("$what discriminator is not $disc")
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).also { it.position(8) }
    }

    fun decodeBoard(owner: String, data: ByteArray): Board = body(owner, data, DISC_BOARD, BOARD_SIZE, "Board").let { b ->
        Board(b.long, b.long, b.long, b.long)
    }

    fun decodeTreasury(owner: String, data: ByteArray): Treasury = body(owner, data, DISC_TREASURY, TREASURY_SIZE, "Treasury").let { b ->
        Treasury(b.long) // motherlode; the remaining fields are not read
    }

    fun decodeRound(owner: String, data: ByteArray): Round = body(owner, data, DISC_ROUND, ROUND_SIZE, "Round").let { b ->
        val id = b.long
        val deployed = List(SQUARES) { b.long }
        repeat(SQUARES) { b.long } // mass
        val count = List(SQUARES) { b.long }
        b.position(b.position() + 32) // slot_hash
        val expiresAt = b.long
        val motherlode = b.long
        b.position(b.position() + 32 + SQUARES * 8 + 8 + 8) // rent_payer, rewards, total_vaulted, total_returned_sol
        val totalMiners = b.long
        if (deployed.any { it < 0 } || count.any { it < 0 }) throw DecodeException("Round has an out-of-range value")
        Round(id, deployed, count, expiresAt, motherlode, totalMiners)
    }

    /** round_pda(id): seeds ["round", id.to_le_bytes()] under the program id. */
    fun roundAddress(id: Long): String =
        Pda.find(listOf("round".toByteArray(), ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(id).array()), PROGRAM_ID).first
}

/** Solana find_program_address: the first bump from 255 down whose SHA-256 hash is not a point on the edwards25519 curve. */
object Pda {
    fun find(seeds: List<ByteArray>, programId: String): Pair<String, Int> {
        val pid = Base58.decodePublicKey(programId)
        require(seeds.size <= 15 && seeds.all { it.size <= 32 })
        for (bump in 255 downTo 0) {
            val md = MessageDigest.getInstance("SHA-256")
            seeds.forEach(md::update)
            md.update(byteArrayOf(bump.toByte())); md.update(pid); md.update("ProgramDerivedAddress".toByteArray())
            val h = md.digest()
            if (!onCurve(h)) return Base58.encode(h) to bump
        }
        error("No program address found")
    }

    /** Whether 32 bytes decode to a point on edwards25519 (canonical y; x recoverable). */
    fun onCurve(b: ByteArray): Boolean {
        val p = java.math.BigInteger.valueOf(2).pow(255) - java.math.BigInteger.valueOf(19)
        val le = b.reversedArray().also { it[0] = (it[0].toInt() and 0x7f).toByte() }
        val y = java.math.BigInteger(1, le)
        if (y >= p) return false
        val d = java.math.BigInteger.valueOf(-121665).mod(p) * java.math.BigInteger.valueOf(121666).modInverse(p) % p
        val u = (y * y - java.math.BigInteger.ONE).mod(p)
        val v = (d * y * y + java.math.BigInteger.ONE).mod(p)
        val x2 = u * v.modInverse(p) % p
        if (x2.signum() == 0) return true
        return x2.modPow((p - java.math.BigInteger.ONE).shiftRight(1), p) == java.math.BigInteger.ONE
    }
}
