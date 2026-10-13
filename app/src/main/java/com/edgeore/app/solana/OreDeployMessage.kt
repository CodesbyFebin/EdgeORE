package com.edgeore.app.solana

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ore.Pda
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The ONLY place in EdgeORE that builds an ORE v3 instruction. It lives in the reviewed signing path (solana/), not in
 * ore/, and its bytes go through the same exact-message review, wallet signing, returned-bytes check, separate submit
 * and receipt as a transfer (OreReadOnlyArchitectureTest enforces the allowlist).
 *
 * Layout from regolith-labs/ore @ [OreV3.SOURCE_COMMIT]:
 *   api/src/instruction.rs  OreInstruction::Deploy = 6; struct Deploy { amount: [u8; 8], squares: [u8; 4] } (steel prefixes the 1-byte discriminator)
 *   api/src/sdk.rs deploy() accounts: signer (w,s), authority (w), automation_pda(authority), board, config, miner_pda(authority),
 *     round_pda(round_id), treasury (all w), system program (ro), ORE program (ro), entropy var_pda(board, 0) (w), entropy program (ro)
 *   program/src/deploy.rs   amount is lamports PER selected square; squares is a 25-bit mask
 *   entropy-api 0.1.4 (Cargo.lock checksum bfdd094b…): program id below; var_pda seeds ["var", authority, id LE]
 * EdgeORE signs with authority = signer (the connected wallet), as the ORE CLI does for a manual deploy.
 */
object OreDeployMessage {
    const val DEPLOY_DISCRIMINATOR = 6
    const val ENTROPY_PROGRAM_ID = "3jSkUuYBoJzQPMEzTvkDFXCZUBksPamrVhrnHR9igu2X"
    const val CONFIG_ADDRESS = "9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy"
    const val ALL_SQUARES_MASK = (1 shl 25) - 1

    data class Deploy(
        val signer: ByteArray,
        val roundId: Long,
        val amountPerSquareLamports: Long,
        val mask: Int,
        val blockhash: ByteArray,
    ) {
        val squareCount: Int get() = Integer.bitCount(mask)
        val totalLamports: Long get() = Math.multiplyExact(amountPerSquareLamports, squareCount.toLong())
    }

    private fun le64(v: Long) =
        ByteBuffer
            .allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(v)
            .array()

    private fun key(b58: String) = Base58.decodePublicKey(b58)

    private fun pda(
        vararg seeds: ByteArray,
        program: String = OreV3.PROGRAM_ID,
    ) = key(Pda.find(seeds.toList(), program).first)

    /** Message keys in order: writable signer, 7 writable non-signers, then 3 read-only programs. */
    fun keys(
        signer: ByteArray,
        roundId: Long,
    ): List<ByteArray> {
        val board = key(OreV3.BOARD_ADDRESS)
        return listOf(
            signer,
            pda("automation".toByteArray(), signer),
            board,
            key(CONFIG_ADDRESS),
            pda("miner".toByteArray(), signer),
            pda("round".toByteArray(), le64(roundId)),
            key(OreV3.TREASURY_ADDRESS),
            pda("var".toByteArray(), board, le64(0), program = ENTROPY_PROGRAM_ID),
            SolanaMessage.SYSTEM_PROGRAM,
            key(OreV3.PROGRAM_ID),
            key(ENTROPY_PROGRAM_ID),
        )
    }

    /** sdk.rs account order, as indexes into [keys]: signer, authority(=signer), automation, board, config, miner, round, treasury, system, ore, var, entropy. */
    private val ACCOUNT_INDEXES = intArrayOf(0, 0, 1, 2, 3, 4, 5, 6, 8, 9, 7, 10)
    private const val PROGRAM_INDEX = 9

    fun data(
        amountPerSquareLamports: Long,
        mask: Int,
    ): ByteArray =
        byteArrayOf(DEPLOY_DISCRIMINATOR.toByte()) + le64(amountPerSquareLamports) +
            ByteBuffer
                .allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(mask)
                .array()

    fun build(d: Deploy): ByteArray {
        require(d.signer.size == 32 && d.blockhash.size == 32) { "INVALID_KEY_LENGTH" }
        require(d.amountPerSquareLamports > 0) { "INVALID_AMOUNT" }
        require(d.mask != 0 && d.mask and ALL_SQUARES_MASK.inv() == 0) { "INVALID_SQUARES" }
        require(d.roundId >= 0) { "INVALID_ROUND" }
        val keys = keys(d.signer, d.roundId)
        val out = ByteArrayOutputStream()
        out.write(1)
        out.write(0)
        out.write(3)
        out.write(keys.size)
        keys.forEach { out.write(it) }
        out.write(d.blockhash)
        out.write(1)
        out.write(PROGRAM_INDEX)
        out.write(ACCOUNT_INDEXES.size)
        ACCOUNT_INDEXES.forEach { out.write(it) }
        val data = data(d.amountPerSquareLamports, d.mask)
        out.write(data.size)
        out.write(data)
        return out.toByteArray()
    }

    /**
     * Decodes only the exact shape [build] produces for [roundId], re-deriving every account and rebuilding the whole
     * message; returns null for anything else (other program, accounts, data, round, trailing bytes).
     */
    fun decode(
        message: ByteArray,
        roundId: Long,
    ): Deploy? =
        try {
            val n = 3 + 1 + 11 * 32
            val signer = message.copyOfRange(4, 36)
            val blockhash = message.copyOfRange(n, n + 32)
            val dataStart = n + 32 + 1 + 1 + 1 + ACCOUNT_INDEXES.size + 1
            val data = message.copyOfRange(dataStart, message.size)
            require(data.size == 13 && data[0].toInt() == DEPLOY_DISCRIMINATOR)
            val bb = ByteBuffer.wrap(data, 1, 12).order(ByteOrder.LITTLE_ENDIAN)
            Deploy(signer, roundId, bb.long, bb.int, blockhash).takeIf { build(it).contentEquals(message) }
        } catch (_: Exception) {
            null
        }

    fun summary(d: Deploy): String =
        "ORE v3 Deploy · round ${d.roundId} · ${d.squareCount} square${if (d.squareCount == 1) "" else "s"} × " +
            "${d.amountPerSquareLamports} lamports = ${d.totalLamports} lamports · signer ${Base58.encode(d.signer)}"

    fun sha256(message: ByteArray) = Sha256.hex(message)
}
