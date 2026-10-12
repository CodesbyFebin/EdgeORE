package com.edgeore.app.solana

import com.edgeore.app.BuildConfig
import com.edgeore.app.EdgeOreCore
import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.ore.OreV3

/** Which cluster an ORE deploy may target. Devnet only unless the build flag (off by default) enables mainnet. */
object OreDeployNetwork {
    /** Build flag ORE_MAINNET_DEPLOY, false in every build type in app/build.gradle.kts. Nothing reads mainnet for deploy while it is off. */
    val MAINNET_ENABLED: Boolean get() = BuildConfig.ORE_MAINNET_DEPLOY
    const val CLUSTER = SolanaRpc.CLUSTER
    const val RPC = SolanaRpc.DEVNET
}

/** Input limits for one deploy. */
object OreDeployLimits {
    /** Kept back from MAX for the network fee and the rent of a first-time miner account. */
    const val FEE_RESERVE_LAMPORTS = 10_000_000L      // 0.01 SOL
    /** Hard cap on the total of one deploy. */
    const val PER_DEPLOY_CAP_LAMPORTS = 100_000_000L  // 0.1 SOL

    /** MAX: the balance minus the fee reserve, never above the per-deploy cap, never negative. Null when the balance is not read. */
    fun max(balanceLamports: Long?): Long? = balanceLamports?.let { (it - FEE_RESERVE_LAMPORTS).coerceIn(0, PER_DEPLOY_CAP_LAMPORTS) }

    /** Splits a total across the selected squares (rounded down per square). Null when it cannot be split. */
    fun perSquare(totalLamports: Long, squares: Int): Long? = if (squares in 1..OreV3.SQUARES && totalLamports >= squares) totalLamports / squares else null

    /** A reason the total cannot be deployed, or null. */
    fun refusal(totalLamports: Long, squares: Int, balanceLamports: Long?): String? = when {
        balanceLamports == null -> "SOL balance not read, so the deploy limit is unknown"
        squares !in 1..OreV3.SQUARES -> "Select at least one square"
        totalLamports <= 0 -> "Amount must be greater than zero"
        totalLamports > PER_DEPLOY_CAP_LAMPORTS -> "Above the per-deploy cap of 0.1 SOL"
        totalLamports > max(balanceLamports)!! -> "Above your balance minus the 0.01 SOL fee reserve"
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
        if (cluster != SolanaRpc.CLUSTER && !OreDeployNetwork.MAINNET_ENABLED) return refused("ORE deploy is devnet-only in this build")
        OreDeployLimits.refusal(totalLamports, squares, balanceLamports)?.let { return refused(it) }
        val per = OreDeployLimits.perSquare(totalLamports, squares)!!
        val deploy = OreDeployMessage.Deploy(signer, roundId, per, mask, recentBlockhash)
        val message = OreDeployMessage.build(deploy)
        val decoded = OreDeployMessage.decode(message, roundId)
        var refusal: String? = null
        if (decoded == null || !decoded.signer.contentEquals(signer) || decoded.amountPerSquareLamports != per || decoded.mask != mask) refusal = "Decoded message does not match the requested deploy"
        if (refusal == null && !EdgeOreCore.eligible(deploy.totalLamports, spentTodayLamports, dailyLimitLamports)) refusal = "Daily budget would be exceeded"
        return TransferReview.Draft(message, Sha256.hex(message), decoded?.let { SolanaMessage.Decoded.OreDeploy(it) } ?: SolanaMessage.Decoded.Unsupported("Not an ORE deploy"),
            deploy.totalLamports, Base58.encode(signer), OreV3.PROGRAM_ID, Base58.encode(recentBlockhash), refusal)
    }
}
