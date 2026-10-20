package com.edgeore.app.ore

/**
 * Text-only advisor for the ORE screen. It has no tools and no functions: the model gets a system prompt, the current
 * snapshot as text and the question, and returns text. EdgeORE then (1) replaces any number in the reply that is not in
 * the context it was given and (2) appends [NOTICE]. Nothing in this file, or anywhere on the ORE screen, can sign, send,
 * deploy, trade or stake.
 */
object OreAdvisor {
    const val NOTICE = "Not financial advice. ORE rounds can lose SOL. EdgeORE cannot deploy."
    const val NOT_CONFIGURED = "Set up: connect your owned host and choose a model (AI → Private AI), or download an on-device model (AI → On this phone)."
    const val REDACTED = "[number not in snapshot]"

    /** How ORE v3 works, stated only from the source at [OreV3.SOURCE_COMMIT] (file paths given so the model can cite them). */
    val FACTS =
        """
        How ORE v3 works (regolith-labs/ore at ${OreV3.SOURCE_COMMIT.take(12)}):
        - The board has 25 squares. Players deploy SOL to squares during a round (program/src/deploy.rs).
        - A round runs from Board.start_slot to Board.end_slot. After a reset, end_slot is unset until the first deploy starts the round.
        - At reset one winning square is picked from the round's entropy: rng % 25 (api/src/state/round.rs, winning_square).
        - Each miner's SOL comes back minus fees (program/src/checkpoint.rs): on the winning square about 1% is kept as an admin fee; on every losing square the SOL is refunded minus a 10% fee, plus a 1% admin fee (about 89.1% comes back).
        - Each round mints up to 1 ORE to the winning square's miners; on 15 of the 25 squares it is split among them in proportion to SOL, on the other 10 it goes to one miner chosen in proportion to SOL (round.rs, distribution_mask, is_split_reward).
        - Each round adds up to 0.2 ORE to the motherlode pool (Treasury.motherlode). In a round with a 1 in 500 chance the whole pool is paid to the winning square's miners in proportion to their SOL (round.rs, did_hit_motherlode; checkpoint.rs).
        - Outcomes are random. Past rounds do not predict the next winning square.
        """.trimIndent()

    const val SYSTEM_PROMPT =
        "You explain the ORE v3 board to the owner of this phone. You are read-only: you have no tools and cannot sign, send, " +
            "deploy, trade or stake, and EdgeORE cannot deploy. Use only the snapshot and the facts given. Quote numbers exactly as they appear there and " +
            "never compute, estimate or invent other numbers, odds, returns or prices. You may describe options in words, but never tell the owner to deploy " +
            "and never promise any outcome. Keep answers short."

    fun context(
        s: OreSnapshot,
        clockLabel: String,
    ): String =
        buildString {
            appendLine(
                "Snapshot (read-only mainnet observation via ${s.rpc}; Board read at slot ${s.boardSlot}, Round read at slot ${s.roundSlot}):",
            )
            appendLine("- Round id: ${s.round.id}")
            appendLine(
                "- Round start slot: ${s.board.startSlot}; end slot: ${if (s.board.endSlot == OreV3.END_SLOT_WAITING) {
                    "not set (waiting for first deploy)"
                } else {
                    s.board.endSlot
                        .toString()
                }}",
            )
            appendLine("- Time left: $clockLabel")
            appendLine("- Total SOL deployed this round: ${OreFormat.sol(s.round.totalDeployedLamports)}")
            appendLine("- Motherlode pool: ${OreFormat.ore(s.treasury.motherlodeGrams)} ORE")
            appendLine("- Unique miners this round: ${s.round.totalMiners}")
            appendLine("- SOL deployed per square (square number: SOL, miners):")
            s.round.deployedLamports.forEachIndexed {
                i,
                l,
                ->
                appendLine("  square ${i + 1}: ${OreFormat.sol(l)} SOL, ${s.round.count[i]} miners")
            }
            appendLine()
            append(FACTS)
        }

    fun prompt(
        s: OreSnapshot,
        clockLabel: String,
        question: String,
    ): String = context(s, clockLabel) + "\n\nQuestion: " + question.trim().take(2_000)

    private val NUMBER = Regex("""\d[\d,]*(?:\.\d+)?""")

    private fun norm(n: String): String? =
        runCatching {
            java.math
                .BigDecimal(n.replace(",", ""))
                .stripTrailingZeros()
                .toPlainString()
        }.getOrNull()

    /** Every number the model was given (snapshot, facts, question); anything else in a reply is replaced. */
    fun allowedNumbers(givenText: String): Set<String> = NUMBER.findAll(givenText).mapNotNull { norm(it.value) }.toSet()

    data class Checked(
        val text: String,
        val redacted: Int,
    )

    /** Replaces numbers not present in [given], then appends the notice. Applied to every reply before it is shown. */
    fun check(
        reply: String,
        given: String,
    ): Checked {
        val allowed = allowedNumbers(given)
        var n = 0
        val body =
            NUMBER.replace(reply.trim()) { m ->
                if (norm(m.value) in allowed) {
                    m.value
                } else {
                    n++
                    REDACTED
                }
            }
        val note = if (n > 0) "\n\n($n number${if (n == 1) "" else "s"} not found in the snapshot replaced.)" else ""
        return Checked("$body$note\n\n$NOTICE", n)
    }
}
