package com.edgeore.app.solana

import com.edgeore.app.BuildConfig
import com.edgeore.app.EdgeOreCore
import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.ore.OreV3

/**
 * Which cluster an ORE deploy may target. Devnet is the default. Mainnet is used only when the build flag
 * ORE_MAINNET_DEPLOY is on AND the owner explicitly switches the Mine deploy panel to mainnet (never automatic,
 * never a fallback). Every mainnet deploy is capped at [OreDeployLimits.MAINNET_CAP_LAMPORTS] and signed by the owner's wallet.
 */
object OreDeployNetwork {
    /** Build flag ORE_MAINNET_DEPLOY (app/build.gradle.kts). Owner-approved 2026-10-12 with the 0.01 SOL cap. */
    val MAINNET_ENABLED: Boolean get() = BuildConfig.ORE_MAINNET_DEPLOY
    const val CLUSTER = SolanaRpc.CLUSTER
    const val RPC = SolanaRpc.DEVNET
    const val MAINNET_CLUSTER = SolanaRpc.MAINNET_CLUSTER
    const val MAINNET_RPC = SolanaRpc.MAINNET
    fun cluster(mainnet: Boolean) = if (mainnet) MAINNET_CLUSTER else CLUSTER
    fun rpc(mainnet: Boolean) = if (mainnet) MAINNET_RPC else RPC
}

/** Input limits for one deploy. */
object OreDeployLimits {
    /** Kept back from MAX for the network fee and the rent of a first-time miner account. */
    const val FEE_RESERVE_LAMPORTS = 10_000_000L      // 0.01 SOL
    /** Hard cap on the total of one devnet deploy. */
    const val PER_DEPLOY_CAP_LAMPORTS = 100_000_000L  // 0.1 SOL
    /** Hard cap on the total of one MAINNET deploy, across all selected squares (owner decision 2026-10-12). */
    const val MAINNET_CAP_LAMPORTS = 10_000_000L      // 0.01 SOL

    fun cap(cluster: String): Long = if (cluster == OreDeployNetwork.MAINNET_CLUSTER) MAINNET_CAP_LAMPORTS else PER_DEPLOY_CAP_LAMPORTS

    /** MAX: the balance minus the fee reserve, never above the per-deploy cap, never negative. Null when the balance is not read. */
    fun max(balanceLamports: Long?, cluster: String = OreDeployNetwork.CLUSTER): Long? =
        balanceLamports?.let { (it - FEE_RESERVE_LAMPORTS).coerceIn(0, cap(cluster)) }

    /** Splits a total across the selected squares (rounded down per square). Null when it cannot be split. */
    fun perSquare(totalLamports: Long, squares: Int): Long? = if (squares in 1..OreV3.SQUARES && totalLamports >= squares) totalLamports / squares else null

    /** A reason the total cannot be deployed, or null. */
    fun refusal(totalLamports: Long, squares: Int, balanceLamports: Long?, cluster: String = OreDeployNetwork.CLUSTER): String? = when {
        // The mainnet cap is checked first, before anything else, so an over-cap amount is refused even with no balance read.
        cluster == OreDeployNetwork.MAINNET_CLUSTER && totalLamports > MAINNET_CAP_LAMPORTS -> "Above the mainnet per-deploy cap of 0.01 SOL"
        balanceLamports == null -> "SOL balance not read, so the deploy limit is unknown"
        squares !in 1..OreV3.SQUARES -> "Select at least one square"
        totalLamports <= 0 -> "Amount must be greater than zero"
        totalLamports > PER_DEPLOY_CAP_LAMPORTS -> "Above the per-deploy cap of 0.1 SOL"
        totalLamports > max(balanceLamports, cluster)!! -> "Above your balance minus the 0.01 SOL fee reserve"
        perSquare(totalLamports, squares) == null -> "Amount is too small to split across $squares squares"
        else -> null
    }
}

/** Prepares an ORE v3 Deploy as a [TransferReview.Draft], so it uses the same review, signing, wallet-bytes check, submit and receipt. */
object OreDeployReview {
    fun prepare(
        signer: ByteArray, roundId: Long, totalLamports: Long, mask: Int, recentBlockhash: ByteArray,
        balanceLamports: Long?, spentTodayLamports: Long, dailyLimitLamports: Long, cluster: String = OreDeployNetwork.CLUSTER,
    ): TransferReview.Draft {
        val squares = Integer.bitCount(mask)
        fun refused(why: String) = TransferReview.Draft(ByteArray(0), "", SolanaMessage.Decoded.Unsupported(why), 0, Base58.encode(signer), OreV3.PROGRAM_ID, "", why)
        val mainnet = cluster == OreDeployNetwork.MAINNET_CLUSTER
        if (cluster != SolanaRpc.CLUSTER && !mainnet) return refused("ORE deploy supports devnet or mainnet-beta only")
        if (mainnet && !OreDeployNetwork.MAINNET_ENABLED) return refused("ORE deploy is devnet-only in this build")
        // Every limit, including the 0.01 SOL mainnet cap, is checked BEFORE a message (and so any wallet request) exists.
        OreDeployLimits.refusal(totalLamports, squares, balanceLamports, cluster)?.let { return refused(it) }
        val per = OreDeployLimits.perSquare(totalLamports, squares)!!
        val deploy = OreDeployMessage.Deploy(signer, roundId, per, mask, recentBlockhash)
        // Defence in depth: the rounded-down total that is actually encoded must also be within the cap.
        if (deploy.totalLamports > OreDeployLimits.cap(cluster)) return refused("Encoded total exceeds the per-deploy cap")
        val message = OreDeployMessage.build(deploy)
        val decoded = OreDeployMessage.decode(message, roundId)
        var refusal: String? = null
        if (decoded == null || !decoded.signer.contentEquals(signer) || decoded.amountPerSquareLamports != per || decoded.mask != mask) refusal = "Decoded message does not match the requested deploy"
        if (refusal == null && !EdgeOreCore.eligible(deploy.totalLamports, spentTodayLamports, dailyLimitLamports)) refusal = "Daily budget would be exceeded"
        return TransferReview.Draft(message, Sha256.hex(message), decoded?.let { SolanaMessage.Decoded.OreDeploy(it) } ?: SolanaMessage.Decoded.Unsupported("Not an ORE deploy"),
            deploy.totalLamports, Base58.encode(signer), OreV3.PROGRAM_ID, Base58.encode(recentBlockhash), refusal)
    }
}
