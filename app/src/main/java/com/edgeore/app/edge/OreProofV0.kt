package com.edgeore.app.edge

import com.edgeore.app.crypto.Base58
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.solana.OreDeployMessage
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * `.oreproof` file, format "edgeore.roundproof/v0". Canonical JSON: keys sorted, no whitespace, amounts are integers
 * (SOL in lamports; ORE in base units, 10^11 per ORE: api/src/consts.rs TOKEN_DECIMALS = 11). The receipt-checker
 * reports each claim P1..P5 as PASS, FAIL or UNVERIFIED. An "attestation" section, if present, is ignored.
 */
object OreProofV0 {
    const val FORMAT = "edgeore.roundproof/v0"
    const val PROTOCOL = "ore-v3@" + RoundRules.SOURCE_COMMIT
    const val CONFIG_ADDRESS = "9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy"

    /** The rates calculate_fees() applies (round.rs, hardcoded): admin 1% of each square, protocol 10% of the rest on losing squares. */
    const val PROGRAM_ADMIN_BPS = 100L
    const val PROGRAM_PROTOCOL_BPS = 1000L

    // ---------- canonical JSON (RFC 8785-style subset: objects, arrays, strings, integers, booleans, null) ----------
    fun canonical(v: Any?): String =
        when (v) {
            null, JSONObject.NULL -> "null"
            is JSONObject ->
                v.keys().asSequence().sorted().joinToString(
                    ",",
                    "{",
                    "}",
                ) { JSONObject.quote(it) + ":" + canonical(v.opt(it)) }
            is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canonical(v.opt(it)) }
            is Map<*, *> ->
                v.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") {
                    JSONObject.quote(it.key.toString()) + ":" +
                        canonical(it.value)
                }
            is List<*> -> v.joinToString(",", "[", "]") { canonical(it) }
            is String -> JSONObject.quote(v)
            is Boolean -> v.toString()
            is Int, is Long -> v.toString()
            is Number -> throw IllegalArgumentException("Non-integer number $v is not allowed")
            else -> throw IllegalArgumentException("Unsupported ${v.javaClass.simpleName}")
        }

    data class Config(
        val adminBps: Long,
        val protocolBps: Long,
    )

    /** api/src/state/config.rs: disc(8), admin {authority, fee_collector, fee_rate}, protocol {authority, fee_collector, fee_rate}. */
    fun decodeConfig(data: ByteArray): Config {
        if (data.size < 152 || (data[0].toInt() and 0xff) != 101) throw RoundRules.RuleException("Not an ORE v3 Config account")
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return Config(b.getLong(8 + 64), b.getLong(8 + 72 + 64))
    }

    /** Builds the file from the deploy receipt body, the receipt export and the stored Round Proof JSON. */
    fun build(
        deployReceiptId: String,
        deployReceiptBody: String,
        receiptExportJson: String,
        roundProofJson: String,
        attestation: Map<String, Any?>? = null,
    ): String {
        val body = JSONObject(deployReceiptBody)
        val proof = JSONObject(roundProofJson)
        val wallet = body.optJSONObject("wallet")
        val roundId = proof.getLong("roundId")
        val message = wallet?.optString("messageBase64")?.let { Base64.getDecoder().decode(it) }
        val deploy = message?.let { OreDeployMessage.decode(it, roundId) }
        val round = RoundRules.decodeRound(Base64.getDecoder().decode(proof.getString("roundAccountBase64")))
        val miner =
            if (proof.isNull(
                    "minerAccountBase64",
                )
            ) {
                null
            } else {
                RoundRules.decodeMiner(Base64.getDecoder().decode(proof.getString("minerAccountBase64")))
            }
        val cfg =
            if (proof.isNull(
                    "configAccountBase64",
                )
            ) {
                null
            } else {
                decodeConfig(Base64.getDecoder().decode(proof.getString("configAccountBase64")))
            }
        val r = RoundRules.rng(round.slotHash)
        val w = r?.let { RoundRules.winningSquare(it) }
        val out = miner?.let { RoundRules.minerOutcome(round, it, Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS)) }
        val (adm, prot) = if (w != null) RoundRules.fees(round.deployed, w) else 0L to 0L
        val doc =
            linkedMapOf<String, Any?>(
                "format" to FORMAT,
                "protocol_version" to PROTOCOL,
                "units" to mapOf("sol" to "lamports", "ore" to "base units (10^11 per ORE, TOKEN_DECIMALS 11)"),
                "receipt" to
                    mapOf(
                        "receipt_id" to deployReceiptId,
                        "intent_digest" to body.optJSONObject("digests")?.optString("messageSha256"),
                        "wallet" to wallet?.optString("signer"),
                        "round_id" to roundId,
                        "deployments" to
                            (
                                deploy?.let { d ->
                                    (0 until 25).filter { d.mask and (1 shl it) != 0 }.map {
                                        mapOf(
                                            "square" to it + 1,
                                            "lamports" to d.amountPerSquareLamports,
                                        )
                                    }
                                } ?: emptyList<Any>()
                            ),
                        "message_base64" to wallet?.optString("messageBase64"),
                        "tx_signature" to body.optString("solanaSignature").ifEmpty { null },
                        "wallet_signature" to wallet?.optString("signature"),
                    ),
                "chain" to
                    mapOf(
                        "rpc" to proof.optString("rpc"),
                        "read_slot" to proof.optLong("readSlot"),
                        "round_expires_at_slot" to round.expiresAt,
                        "winner_square" to w?.plus(1),
                        "winner_source" to
                            if (r != null) "recomputed from Round.slot_hash (round.rs rng % 25)" else "unavailable: slot_hash empty",
                        "tile_mode" to w?.let { if (RoundRules.isSolo(round.id, it)) "solo" else "split" },
                        "fee_params" to
                            mapOf(
                                "config_admin_bps" to cfg?.adminBps,
                                "config_protocol_bps" to cfg?.protocolBps,
                                "program_admin_bps" to PROGRAM_ADMIN_BPS,
                                "program_protocol_bps" to PROGRAM_PROTOCOL_BPS,
                                "source" to "Config account $CONFIG_ADDRESS (read); round.rs calculate_fees applies the program rates",
                            ),
                        "round_admin_fee" to adm,
                        "round_total_vaulted" to round.totalVaulted,
                        "round_total_returned_sol" to round.totalReturnedSol,
                        "user_sol_deployed" to miner?.deployed?.sum(),
                        "user_sol_returned" to out?.rewardsSol,
                        "user_ore_credited" to out?.let { it.rewardsOre + it.motherloadOre },
                        "round_proof_json" to roundProofJson,
                    ),
                "receipt_export" to receiptExportJson,
            )
        if (attestation != null) doc["attestation"] = attestation
        return canonical(doc)
    }

    enum class Verdict { PASS, FAIL, UNVERIFIED }

    data class Claim(
        val id: String,
        val name: String,
        val verdict: Verdict,
        val why: String,
    )

    /**
     * [exportAccepted]/[walletSigsVerified]/[verifiedBodies] come from ReceiptVerifier on receipt_export (it verifies
     * the integrity of signed receipt contents). [roundAddressFor] is OreV3.roundAddress.
     */
    fun check(
        text: String,
        exportAccepted: Boolean,
        walletSigsVerified: Int,
        verifiedBodies: Map<String, JSONObject>,
        roundAddressFor: (Long) -> String,
    ): List<Claim> {
        val d = JSONObject(text)
        val claims = mutableListOf<Claim>()
        val canonicalOk = canonical(d) == text.trim()
        val rc = d.getJSONObject("receipt")
        val ch = d.getJSONObject("chain")
        val roundId = rc.getLong("round_id")

        fun c(
            id: String,
            n: String,
            v: Verdict,
            w: String,
        ) = claims.add(Claim(id, n, v, w))

        if (d.optString("format") != FORMAT || !canonicalOk) {
            c("P0", "file", Verdict.FAIL, if (canonicalOk) "unknown format" else "not canonical JSON (keys sorted, no whitespace)")
            return claims
        }
        // P1 intent
        val body = verifiedBodies[rc.optString("receipt_id")]
        val msg = rc.optString("message_base64").takeIf { it.isNotEmpty() && it != "null" }?.let { Base64.getDecoder().decode(it) }
        when {
            !exportAccepted -> c("P1", "intent", Verdict.FAIL, "receipt export rejected")
            body == null -> c("P1", "intent", Verdict.FAIL, "deploy receipt not in the verified export")
            msg == null -> c("P1", "intent", Verdict.UNVERIFIED, "no reviewed message in the file")
            else -> {
                val dig = Sha256.hex(msg)
                val deploy = OreDeployMessage.decode(msg, roundId)
                val listed = rc.getJSONArray("deployments")
                val want =
                    deploy?.let { x ->
                        (0 until 25).filter { x.mask and (1 shl it) != 0 }.map { (it + 1) to x.amountPerSquareLamports }
                    }
                val got =
                    (0 until listed.length()).map {
                        listed.getJSONObject(it).let { o ->
                            o.getInt("square") to o.getLong("lamports")
                        }
                    }
                val okSig = walletSigsVerified > 0 && body.optJSONObject("wallet")?.optString("signer") == rc.optString("wallet")
                when {
                    body.optJSONObject("digests")?.optString("messageSha256") != dig || rc.optString("intent_digest") != dig ->
                        c("P1", "intent", Verdict.FAIL, "intent digest does not match the signed receipt / message")
                    deploy == null -> c("P1", "intent", Verdict.FAIL, "message is not the exact ORE v3 Deploy for round $roundId")
                    want != got -> c("P1", "intent", Verdict.FAIL, "listed deployments differ from the signed message")
                    !okSig -> c("P1", "intent", Verdict.UNVERIFIED, "wallet Ed25519 signature not verified")
                    else -> c("P1", "intent", Verdict.PASS, "receipt signed; digest, Deploy message and wallet signature agree")
                }
            }
        }
        // Round Proof must be inside a signed receipt before any chain claim counts.
        val pj = ch.getString("round_proof_json")
        val signedDigests = verifiedBodies.values.mapNotNull { it.optJSONObject("digests")?.optString(RoundProof.DIGEST_KEY) }
        val rp = RoundProof.check(pj, signedDigests, roundAddressFor)
        val proof = JSONObject(pj)
        if (!exportAccepted || !rp.verified.any { it.startsWith("Proof digest") } || proof.getLong("roundId") != roundId) {
            listOf("P2" to "winner", "P3" to "split/solo", "P4" to "SOL accounting", "P5" to "ORE share").forEach { (i, n) ->
                c(i, n, Verdict.FAIL, "Round Proof not covered by a verified receipt, or for another round")
            }
            return claims
        }
        val round = RoundRules.decodeRound(Base64.getDecoder().decode(proof.getString("roundAccountBase64")))
        val r = RoundRules.rng(round.slotHash)
        // P2
        if (r == null) {
            c("P2", "winner", Verdict.UNVERIFIED, "slot_hash empty; winner cannot be recomputed")
        } else {
            val w = RoundRules.winningSquare(r) + 1
            c(
                "P2",
                "winner",
                if (ch.optInt("winner_square") ==
                    w
                ) {
                    Verdict.PASS
                } else {
                    Verdict.FAIL
                },
                "recomputed square $w from slot_hash (entropy source itself not verified)",
            )
        }
        val w0 = r?.let { RoundRules.winningSquare(it) }
        // P3
        if (w0 == null) {
            c("P3", "split/solo", Verdict.UNVERIFIED, "no winner")
        } else {
            val solo = RoundRules.isSolo(round.id, w0)
            val marker = !round.topMiner.contentEquals(Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS))
            val claimed = ch.optString("tile_mode") == if (solo) "solo" else "split"
            c(
                "P3",
                "split/solo",
                if (solo == marker &&
                    claimed
                ) {
                    Verdict.PASS
                } else {
                    Verdict.FAIL
                },
                "distribution_mask says ${if (solo) "solo" else "split"}; Round.top_miner agrees: ${solo == marker}",
            )
        }
        val miner =
            if (proof.isNull(
                    "minerAccountBase64",
                )
            ) {
                null
            } else {
                RoundRules.decodeMiner(Base64.getDecoder().decode(proof.getString("minerAccountBase64")))
            }
        val out =
            if (miner != null &&
                w0 != null
            ) {
                RoundRules.minerOutcome(round, miner, Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS))
            } else {
                null
            }
        // P4
        if (w0 == null) {
            c("P4", "SOL accounting", Verdict.UNVERIFIED, "no winner")
        } else {
            val (adm, prot) = RoundRules.fees(round.deployed, w0)
            val totals = round.totalVaulted == prot && round.totalReturnedSol == round.totalDeployed - adm - prot
            val cfg =
                if (proof.isNull(
                        "configAccountBase64",
                    )
                ) {
                    null
                } else {
                    decodeConfig(Base64.getDecoder().decode(proof.getString("configAccountBase64")))
                }
            val cfgOk = cfg == null || (cfg.adminBps == PROGRAM_ADMIN_BPS && cfg.protocolBps == PROGRAM_PROTOCOL_BPS)
            when {
                !totals -> c("P4", "SOL accounting", Verdict.FAIL, "round totals do not match calculate_fees()")
                !cfgOk -> c("P4", "SOL accounting", Verdict.FAIL, "Config fee rates differ from the rates the program applies")
                out == null ->
                    c(
                        "P4",
                        "SOL accounting",
                        Verdict.UNVERIFIED,
                        "round totals match; your Miner account for this round was not captured",
                    )
                out.rewardsSol !=
                    ch.optLong(
                        "user_sol_returned",
                        -1,
                    )
                -> c("P4", "SOL accounting", Verdict.FAIL, "your SOL return differs from checkpoint.rs")
                else ->
                    c(
                        "P4",
                        "SOL accounting",
                        if (cfg == null) Verdict.UNVERIFIED else Verdict.PASS,
                        "round totals and your return of ${out.rewardsSol} lamports match" +
                            if (cfg == null) "; Config account not captured" else "; Config rates ${cfg.adminBps}/${cfg.protocolBps} bps",
                    )
            }
        }
        // P5
        if (out == null) {
            c("P5", "ORE share", Verdict.UNVERIFIED, "Miner account not captured or round not settled")
        } else {
            val ore = out.rewardsOre + out.motherloadOre
            c(
                "P5",
                "ORE share",
                if (ch.optLong("user_ore_credited", -1) ==
                    ore
                ) {
                    Verdict.PASS
                } else {
                    Verdict.FAIL
                },
                "$ore base units per checkpoint.rs (not a claim or balance check)",
            )
        }
        return claims
    }
}
