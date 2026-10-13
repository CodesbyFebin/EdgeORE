package com.edgeore.app.edge

import com.edgeore.app.crypto.Base58
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Startup guardrail: compares the live ORE v3 program and Config with what this build was written for. On any
 * difference the app shows [BANNER] instead of silently rendering rules that may have changed. Read-only.
 * Expected values were read from mainnet on 13 Oct 2026 (app/src/test/resources/ore/mainnet-protocol-capture.json).
 */
object ProtocolGuard {
    const val BANNER = "ORE protocol changed — some screens may be out of date"
    const val PROGRAM_DATA = "GXa6JV9AwsccP3hxvKFcZGp4w3MMtf7PJ6HYuTSyokfJ"
    const val EXPECTED_DEPLOY_SLOT = 452_682_055L
    const val EXPECTED_AUTHORITY = "J5K5tWj3nKfxuSkAJ25WTMf4u5EsxJRfUoRKKxgrfFGV"
    const val PROGRAMDATA_HEAD = 45

    sealed interface Result {
        data object Match : Result

        data class Changed(
            val reasons: List<String>,
        ) : Result

        data class Unknown(
            val why: String,
        ) : Result
    }

    /** [program]: the 36-byte program account; [programDataHead]: first 45 bytes of ProgramData; [config]: Config account. */
    fun check(
        program: ByteArray?,
        programDataHead: ByteArray?,
        config: ByteArray?,
    ): Result {
        if (program == null || programDataHead == null || config == null) return Result.Unknown("Protocol accounts not readable")
        val why = mutableListOf<String>()
        val p = ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN)
        if (program.size != 36 || p.getInt(0) != 2 || Base58.encode(program.copyOfRange(4, 36)) != PROGRAM_DATA) {
            why += "program data account moved"
        }
        val h = ByteBuffer.wrap(programDataHead).order(ByteOrder.LITTLE_ENDIAN)
        if (programDataHead.size < PROGRAMDATA_HEAD || h.getInt(0) != 3) {
            why += "program data header unreadable"
        } else {
            val slot = h.getLong(4)
            if (slot != EXPECTED_DEPLOY_SLOT) why += "program redeployed at slot $slot (built for $EXPECTED_DEPLOY_SLOT)"
            val auth = if (programDataHead[12].toInt() == 1) Base58.encode(programDataHead.copyOfRange(13, 45)) else "none"
            if (auth != EXPECTED_AUTHORITY) why += "upgrade authority changed"
        }
        runCatching { OreProofV0.decodeConfig(config) }.fold({ c ->
            if (c.adminBps != OreProofV0.PROGRAM_ADMIN_BPS || c.protocolBps != OreProofV0.PROGRAM_PROTOCOL_BPS) {
                why += "Config fee rates ${c.adminBps}/${c.protocolBps} bps differ from the rules in this build"
            }
        }, { why += "Config account layout changed" })
        return if (why.isEmpty()) Result.Match else Result.Changed(why)
    }
}
