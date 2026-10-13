package com.edgeore.app.ore

import com.edgeore.app.solana.AccountReader

/** Whether ORE v3 is present on the deploy cluster (devnet). Read-only; this file builds nothing. */
sealed interface OreDeployAvailability {
    data object NotChecked : OreDeployAvailability

    data object Checking : OreDeployAvailability

    /** Program, Board, Treasury and Config all present and owned as expected; [roundId] from the devnet Board. */
    data class Available(
        val rpc: String,
        val roundId: Long,
        val slot: Long,
        val checkedAtMs: Long,
    ) : OreDeployAvailability

    data class Unavailable(
        val rpc: String,
        val reason: String,
        val checkedAtMs: Long,
    ) : OreDeployAvailability
}

object OreDeployCheck {
    @androidx.annotation.VisibleForTesting
    @Volatile internal var testSource: ((AccountReader) -> OreDeployAvailability)? = null
    const val UPGRADEABLE_LOADER = "BPFLoaderUpgradeab1e11111111111111111111111"
    const val CONFIG_ADDRESS = "9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy"

    /** Never falls back to another cluster: a failed or empty read is reported as unavailable. */
    suspend fun check(
        reader: AccountReader,
        nowMs: () -> Long = System::currentTimeMillis,
    ): OreDeployAvailability {
        testSource?.let { return it(reader) }
        return try {
            val r = reader.accounts(listOf(OreV3.PROGRAM_ID, OreV3.BOARD_ADDRESS, OreV3.TREASURY_ADDRESS, CONFIG_ADDRESS))
            val (program, board, treasury, config) = r.accounts
            when {
                program == null ->
                    OreDeployAvailability.Unavailable(
                        reader.endpoint,
                        "ORE v3 program ${OreV3.PROGRAM_ID.take(8)}… is not deployed on this cluster",
                        nowMs(),
                    )
                program.owner != UPGRADEABLE_LOADER ->
                    OreDeployAvailability.Unavailable(
                        reader.endpoint,
                        "Account ${OreV3.PROGRAM_ID.take(8)}… is not an upgradeable program here",
                        nowMs(),
                    )
                board == null || treasury == null || config == null ->
                    OreDeployAvailability.Unavailable(
                        reader.endpoint,
                        "ORE program found, but its Board, Treasury or Config account is missing",
                        nowMs(),
                    )
                config.owner != OreV3.PROGRAM_ID ->
                    OreDeployAvailability.Unavailable(
                        reader.endpoint,
                        "Config account is not owned by the ORE program",
                        nowMs(),
                    )
                else -> {
                    OreV3.decodeTreasury(treasury.owner, treasury.data)
                    OreDeployAvailability.Available(reader.endpoint, OreV3.decodeBoard(board.owner, board.data).roundId, r.slot, nowMs())
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            OreDeployAvailability.Unavailable(
                reader.endpoint,
                "Could not read the cluster: ${e.message ?: e.javaClass.simpleName}",
                nowMs(),
            )
        }
    }
}
