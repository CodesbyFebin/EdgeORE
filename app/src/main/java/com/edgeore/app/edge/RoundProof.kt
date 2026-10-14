package com.edgeore.app.edge

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Sha256
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Round Proof: after a round settles, EdgeORE keeps the raw Round (and the user's Miner) account bytes it read, and
 * what it derived from them with [RoundRules]. The proof JSON's SHA-256 goes into a Keystore-signed receipt
 * (digests.roundProofSha256), so the receipt checker verifies the integrity of signed receipt contents and then
 * re-derives every result from the same bytes, offline.
 *
 * What this does NOT show: that the bytes are what the chain held (an RPC could lie; compare with another RPC or an
 * explorer), and how the program produced slot_hash (the entropy source in reset.rs is outside this check).
 */
object RoundProof {
    const val SCHEMA = "edgeore.roundproof.v1"
    const val BUNDLE_SCHEMA = "edgeore.roundproof-bundle.v1"
    const val DIGEST_KEY = "roundProofSha256"

    val UNVERIFIED =
        listOf(
            "That these account bytes are what the chain held at the read slot (single RPC read; compare with another RPC or an explorer).",
            "How the program produced slot_hash (entropy source used by reset.rs); only the steps after slot_hash are re-derived.",
            "That the round's total distribution is fair beyond the rules re-derived here.",
            "Whether the miner has checkpointed or claimed; on-chain balances are not checked.",
            "What the buyback later does with the vaulted SOL (burn/staker split happens in other instructions, not read here).",
        )

    val VERIFIED_STEPS =
        listOf(
            "Round account layout, discriminator 109 and size (api/src/state/round.rs)",
            "Round PDA from seeds [\"round\", id] under the ORE v3 program id",
            "rng = XOR of slot_hash's four u64 words; winning square = rng % 25",
            "Motherlode hit = reverse_bits(rng) % 500 == 0, compared with Round.motherlode",
            "Split vs top-miner from Round.top_miner == SPLIT_ADDRESS",
            "Miner SOL return and ORE share per program/src/checkpoint.rs (when a Miner account was read)",
            "Winning tile split/solo from distribution_mask(round id) agrees with Round.top_miner",
            "Losing-square fees: Round.total_vaulted and total_returned_sol equal calculate_fees() (buyback vault accounting)",
        )

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    private fun unb64(s: String) = Base64.getDecoder().decode(s)

    fun derive(
        round: RoundRules.FullRound,
        miner: RoundRules.MinerRound?,
    ): JSONObject {
        val split = Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS)
        val r = RoundRules.rng(round.slotHash)
        val o = JSONObject()
        o.put("settled", r != null)
        if (r == null) return o
        val w = RoundRules.winningSquare(r)
        o
            .put("rngHex", "%016x".format(r))
            .put("winningSquare", w + 1)
            .put("motherlodeHitByRng", RoundRules.didHitMotherlode(r))
            .put("motherlodeGrams", round.motherlode)
            .put("split", round.topMiner.contentEquals(split))
            .put("topMiner", Base58.encode(round.topMiner))
            .put("rewardGrams", round.topMinerReward)
            .put("deployedOnWinnerLamports", round.deployed[w])
            .put("winnerTileSoloByMask", RoundRules.isSolo(round.id, w))
            .put("feesAdminProtocol", RoundRules.fees(round.deployed, w).let { "${it.first}/${it.second}" })
            .put("totalVaulted", round.totalVaulted)
            .put("totalReturnedSol", round.totalReturnedSol)
        if (miner != null) {
            val m = RoundRules.minerOutcome(round, miner, split)
            o.put(
                "miner",
                JSONObject()
                    .put("authority", Base58.encode(miner.authority))
                    .put("deployedLamports", miner.deployed.sum())
                    .put("solBackLamports", m.rewardsSol)
                    .put("oreGrams", m.rewardsOre)
                    .put("motherlodeGrams", m.motherloadOre)
                    .put("wonTopMiner", m.wonTopMiner),
            )
        }
        return o
    }

    /** Builds the proof JSON text. Its exact bytes are what the receipt digest covers. */
    fun build(
        roundAddress: String,
        roundBytes: ByteArray,
        readSlot: Long,
        rpc: String,
        minerAddress: String?,
        minerBytes: ByteArray?,
        configBytes: ByteArray? = null,
    ): String {
        val round = RoundRules.decodeRound(roundBytes)
        val miner = minerBytes?.let { RoundRules.decodeMiner(it) }
        if (miner != null &&
            miner.roundId != round.id
        ) {
            throw RoundRules.RuleException("Miner round ${miner.roundId} is not round ${round.id}")
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("sourceCommit", RoundRules.SOURCE_COMMIT)
            .put("programId", RoundRules.PROGRAM_ID)
            .put("roundId", round.id)
            .put("roundAddress", roundAddress)
            .put("roundAccountBase64", b64(roundBytes))
            .put("readSlot", readSlot)
            .put("rpc", rpc)
            .put("minerAddress", minerAddress ?: JSONObject.NULL)
            .put("minerAccountBase64", minerBytes?.let(::b64) ?: JSONObject.NULL)
            .put("configAccountBase64", configBytes?.let(::b64) ?: JSONObject.NULL)
            .put("derived", derive(round, miner))
            .put("unverified", JSONArray(UNVERIFIED))
            .toString()
    }

    fun digest(proofJson: String): String = Sha256.hex(proofJson)

    fun bundle(
        receiptExportJson: String,
        proofJson: String,
    ): String =
        JSONObject()
            .put("schema", BUNDLE_SCHEMA)
            .put("receiptExport", receiptExportJson)
            .put("proofJson", proofJson)
            .toString(2)

    data class Check(
        val accepted: Boolean,
        val verified: List<String>,
        val failures: List<String>,
        val unverified: List<String>,
        val winningSquare: Int?,
    )

    /**
     * Re-derives the proof from its own bytes. [roundAddressFor] is the PDA function (OreV3.roundAddress); the receipt
     * signature check is done by the caller (ReceiptVerifier) and its digest passed as [signedDigests].
     */
    fun check(
        proofJson: String,
        signedDigests: Collection<String>,
        roundAddressFor: (Long) -> String,
    ): Check {
        val ok = mutableListOf<String>()
        val bad = mutableListOf<String>()
        val p =
            try {
                JSONObject(proofJson)
            } catch (_: Exception) {
                return Check(false, ok, listOf("Proof is not JSON"), UNVERIFIED, null)
            }
        if (p.optString("schema") != SCHEMA) bad += "Unknown proof schema"
        if (digest(proofJson) in signedDigests) {
            ok += "Proof digest is inside a signed receipt (digests.$DIGEST_KEY)"
        } else {
            bad += "Proof digest is not in any verified receipt"
        }
        if (p.optString("sourceCommit") != RoundRules.SOURCE_COMMIT) bad += "Proof was derived against a different ORE source commit"
        val round =
            try {
                RoundRules.decodeRound(unb64(p.getString("roundAccountBase64")))
            } catch (e: Exception) {
                bad += "Round bytes: ${e.message}"
                return Check(false, ok, bad, UNVERIFIED, null)
            }
        ok += VERIFIED_STEPS[0]
        if (round.id != p.optLong("roundId", -1)) bad += "roundId does not match the Round bytes"
        if (roundAddressFor(round.id) ==
            p.optString("roundAddress")
        ) {
            ok += VERIFIED_STEPS[1]
        } else {
            bad += "roundAddress is not the PDA for round ${round.id}"
        }
        val miner =
            if (p.isNull("minerAccountBase64")) {
                null
            } else {
                try {
                    RoundRules.decodeMiner(unb64(p.getString("minerAccountBase64")))
                } catch (e: Exception) {
                    bad += "Miner bytes: ${e.message}"
                    null
                }
            }
        if (miner != null && miner.roundId != round.id) bad += "Miner bytes are for round ${miner.roundId}"
        val again = derive(round, miner)
        val claimed = p.optJSONObject("derived")
        if (claimed == null || !same(claimed, again)) {
            bad += "Derived results do not match a re-derivation from the bytes"
        } else if (again.optBoolean("settled")) {
            ok += VERIFIED_STEPS[2]
            val hit = again.getBoolean("motherlodeHitByRng")
            if (hit || round.motherlode == 0L) {
                ok += VERIFIED_STEPS[3]
            } else {
                bad += "Motherlode field disagrees with the rng rule"
            }
            ok += VERIFIED_STEPS[4]
            if (miner != null) ok += VERIFIED_STEPS[5]
            val w = again.getInt("winningSquare") - 1
            val solo = again.getBoolean("winnerTileSoloByMask")
            if (solo == !again.getBoolean("split")) ok += VERIFIED_STEPS[6] else bad += "Split marker disagrees with distribution_mask"
            val (adm, prot) = RoundRules.fees(round.deployed, w)
            if (round.totalVaulted == prot && round.totalReturnedSol == round.totalDeployed - adm - prot) {
                ok += VERIFIED_STEPS[7]
            } else {
                bad += "total_vaulted / total_returned_sol do not match calculate_fees()"
            }
        } else {
            ok += "Round not settled (slot_hash empty): nothing to derive"
        }
        val win = if (again.optBoolean("settled")) again.getInt("winningSquare") else null
        return Check(bad.isEmpty(), ok, bad, UNVERIFIED, win)
    }

    private fun same(
        a: JSONObject,
        b: JSONObject,
    ): Boolean = canon(a) == canon(b)

    /** Key-sorted rendering; Android's org.json has no similar(). */
    private fun canon(v: Any?): String =
        when (v) {
            is JSONObject ->
                v
                    .keys()
                    .asSequence()
                    .sorted()
                    .joinToString(",", "{", "}") { k -> JSONObject.quote(k) + ":" + canon(v.opt(k)) }
            is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
            is String -> JSONObject.quote(v)
            is Number ->
                java.math
                    .BigDecimal(v.toString())
                    .stripTrailingZeros()
                    .toPlainString()
            null -> "null"
            else -> v.toString()
        }
}
