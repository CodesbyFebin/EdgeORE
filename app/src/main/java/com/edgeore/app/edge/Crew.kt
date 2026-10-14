package com.edgeore.app.edge

import com.edgeore.app.crypto.Base58
import org.json.JSONObject
import java.util.Base64

/**
 * Crew Pool, non-custodial. A crew is only a signed plan: which squares and lamports each member intends to deploy in
 * one round. There is no deposit, no shared wallet, no server and no server key. Each member deploys only their own
 * share from their own wallet through the normal exact-message review and MWA signature; ORE pays each deployer
 * directly on-chain. EdgeORE never holds or moves funds. The even-split view only computes who-owes-whom for members
 * to settle manually, if they choose.
 */
object Crew {
    const val SCHEMA = "edgeore.crewplan/v0"
    const val LINK_PREFIX = "edgeore://crew?plan="
    const val CUSTODY_NOTE =
        "EdgeORE never holds funds. There is no deposit and no shared wallet: each member deploys their own share from " +
            "their own wallet, and ORE pays each deployer directly on-chain."

    data class Member(
        val wallet: String,
        val mask: Int,
        val lamportsPerSquare: Long,
    ) {
        val totalLamports: Long get() = lamportsPerSquare * Integer.bitCount(mask)
    }

    data class Plan(
        val crewId: String,
        val name: String,
        val roundId: Long,
        val creator: String,
        val members: List<Member>,
    ) {
        val coverageMask: Int get() = members.fold(0) { m, x -> m or x.mask }
    }

    /** Canonical bytes that are signed (keys sorted, no whitespace, integers only). */
    fun canonical(p: Plan): String =
        OreProofV0.canonical(
            mapOf(
                "schema" to SCHEMA,
                "crew_id" to p.crewId,
                "name" to p.name,
                "round_id" to p.roundId,
                "creator" to p.creator,
                "members" to
                    p.members.sortedBy { it.wallet }.map {
                        mapOf(
                            "wallet" to it.wallet,
                            "mask" to it.mask.toLong(),
                            "lamports_per_square" to it.lamportsPerSquare,
                        )
                    },
            ),
        )

    /** Scheme "ed25519-wallet": creator's wallet signed the canonical bytes (signMessage). "keystore-p256": local Keystore. */
    data class Signed(
        val plan: Plan,
        val scheme: String,
        val signatureB64: String,
        /** For keystore-p256: the SPKI of the signing key (base64). For the wallet scheme the creator address is the key. */
        val keySpkiB64: String?,
    )

    fun problems(p: Plan): List<String> {
        val out = mutableListOf<String>()
        if (p.members.isEmpty()) out += "no members"
        if (p.members
                .map { it.wallet }
                .toSet()
                .size != p.members.size
        ) {
            out += "a wallet is listed twice"
        }
        p.members.forEach { m ->
            if (runCatching { Base58.decodePublicKey(m.wallet) }.isFailure) out += "invalid wallet ${m.wallet.take(8)}"
            if (m.mask <= 0 || m.mask >= (1 shl 25)) out += "member ${m.wallet.take(6)} has no valid squares"
            if (m.lamportsPerSquare <= 0) out += "member ${m.wallet.take(6)} has no amount"
        }
        return out
    }

    fun signWithKeystore(
        p: Plan,
        signer: com.edgeore.app.receipts.ReceiptSigner,
    ): Signed {
        val sig = signer.sign(canonical(p).toByteArray(Charsets.UTF_8))
        return Signed(
            p,
            "keystore-p256",
            Base64.getEncoder().encodeToString(sig),
            Base64.getEncoder().encodeToString(signer.publicKeySpki()),
        )
    }

    enum class Check { VALID_WALLET, VALID_KEYSTORE, TAMPERED, INVALID }

    fun verify(s: Signed): Check {
        if (problems(s.plan).isNotEmpty()) return Check.INVALID
        val msg = canonical(s.plan).toByteArray(Charsets.UTF_8)
        val sig = runCatching { Base64.getDecoder().decode(s.signatureB64) }.getOrNull() ?: return Check.INVALID
        return when (s.scheme) {
            "ed25519-wallet" -> {
                val pub = runCatching { Base58.decodePublicKey(s.plan.creator) }.getOrNull() ?: return Check.INVALID
                val ok =
                    runCatching {
                        org.bouncycastle.crypto.signers.Ed25519Signer().run {
                            init(
                                false,
                                org.bouncycastle.crypto.params
                                    .Ed25519PublicKeyParameters(pub, 0),
                            )
                            update(msg, 0, msg.size)
                            verifySignature(sig)
                        }
                    }.getOrDefault(false)
                if (ok) Check.VALID_WALLET else Check.TAMPERED
            }
            "keystore-p256" -> {
                val spki = s.keySpkiB64?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() } ?: return Check.INVALID
                val ok =
                    runCatching {
                        val key =
                            java.security.KeyFactory
                                .getInstance("EC")
                                .generatePublic(java.security.spec.X509EncodedKeySpec(spki))
                        java.security.Signature.getInstance("SHA256withECDSA").run {
                            initVerify(key)
                            update(msg)
                            verify(sig)
                        }
                    }.getOrDefault(false)
                if (ok) Check.VALID_KEYSTORE else Check.TAMPERED
            }
            else -> Check.INVALID
        }
    }

    /** Share payload for QR / deep link / file: the signed plan as JSON, base64url. */
    fun encode(s: Signed): String {
        val j =
            JSONObject()
                .put("plan", JSONObject(canonical(s.plan)))
                .put("scheme", s.scheme)
                .put("signature", s.signatureB64)
                .put("key", s.keySpkiB64 ?: JSONObject.NULL)
        return LINK_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(j.toString().toByteArray(Charsets.UTF_8))
    }

    fun decode(link: String): Signed? =
        runCatching {
            require(link.startsWith(LINK_PREFIX) && link.length < 16_000)
            val j = JSONObject(String(Base64.getUrlDecoder().decode(link.removePrefix(LINK_PREFIX)), Charsets.UTF_8))
            val p = j.getJSONObject("plan")
            require(p.getString("schema") == SCHEMA)
            val ms = p.getJSONArray("members")
            val plan =
                Plan(
                    p.getString("crew_id"),
                    p.getString("name"),
                    p.getLong("round_id"),
                    p.getString("creator"),
                    (0 until ms.length()).map { i ->
                        ms
                            .getJSONObject(
                                i,
                            ).let { Member(it.getString("wallet"), it.getLong("mask").toInt(), it.getLong("lamports_per_square")) }
                    },
                )
            Signed(plan, j.getString("scheme"), j.getString("signature"), if (j.isNull("key")) null else j.getString("key"))
        }.getOrNull()

    /** One member's result for a round, from that member's own signed receipt / Round Proof (shared by them). */
    data class MemberResult(
        val wallet: String,
        val deployedLamports: Long,
        val solBackLamports: Long,
        val oreGrams: Long,
        val receiptVerified: Boolean,
    )

    data class Transfer(
        val from: String,
        val to: String,
        val lamports: Long,
    )

    /**
     * Optional even split of the round's combined SOL result: each member's fair net = total net × (their deploy / total).
     * Returns the transfers that would equalise it. Only verified receipts count. Members settle manually; EdgeORE never moves funds.
     * ORE is listed per member and not converted (no price is assumed).
     */
    fun evenSplit(results: List<MemberResult>): List<Transfer> {
        val ok = results.filter { it.receiptVerified && it.deployedLamports > 0 }
        val total = ok.sumOf { it.deployedLamports }
        if (total == 0L) return emptyList()
        val net = ok.sumOf { it.solBackLamports - it.deployedLamports }
        val diff =
            ok
                .associate { r ->
                    val fair =
                        java.math.BigInteger
                            .valueOf(
                                net,
                            ).multiply(java.math.BigInteger.valueOf(r.deployedLamports))
                            .divide(java.math.BigInteger.valueOf(total))
                            .toLong()
                    r.wallet to (r.solBackLamports - r.deployedLamports) - fair
                }.toMutableMap()
        val out = mutableListOf<Transfer>()
        while (true) {
            val payer = diff.filter { it.value > 0 }.maxByOrNull { it.value } ?: break
            val payee = diff.filter { it.value < 0 }.minByOrNull { it.value } ?: break
            val amt = minOf(payer.value, -payee.value)
            if (amt <= 0) break
            out += Transfer(payer.key, payee.key, amt)
            diff[payer.key] = payer.value - amt
            diff[payee.key] = payee.value + amt
        }
        return out
    }

    /**
     * Suggested plan update when members drop out (for example, they did not acknowledge this round's plan). Deterministic:
     * active wallets sorted, square i goes to active[i % n], so all 25 squares are covered with no overlap and counts
     * differ by at most one. Each member keeps their own original total, spread over their new squares (floored), so no
     * one's total grows and the per-deploy cap is unchanged. This is only a suggestion: it must be re-signed by the
     * creator, and each member still reviews and signs their own deploy.
     */
    fun reassign(
        plan: Plan,
        active: Set<String>,
    ): Plan? {
        val keep = plan.members.filter { it.wallet in active }.sortedBy { it.wallet }
        if (keep.isEmpty()) return null
        val masks = IntArray(keep.size)
        for (sq in 0 until 25) masks[sq % keep.size] = masks[sq % keep.size] or (1 shl sq)
        val members =
            keep
                .mapIndexed { i, m ->
                    val per = m.totalLamports / Integer.bitCount(masks[i])
                    Member(m.wallet, masks[i], per)
                }.filter { it.lamportsPerSquare > 0 }
        return plan.copy(members = members)
    }

    data class Stats(
        val coveredSquares: Int,
        val rounds: Int,
        val wins: Int,
        val members: Int,
    )

    /** [winners]: roundId -> winning square (0-based) from Round Proofs; [plans]: signed plans the user holds. */
    fun stats(
        plans: List<Plan>,
        winners: Map<Long, Int>,
    ): Stats {
        val last = plans.maxByOrNull { it.roundId }
        val wins = plans.count { p -> winners[p.roundId]?.let { p.coverageMask and (1 shl it) != 0 } == true }
        return Stats(last?.coverageMask?.let(Integer::bitCount) ?: 0, plans.map { it.roundId }.toSet().size, wins, last?.members?.size ?: 0)
    }
}
