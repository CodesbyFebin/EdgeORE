package com.edgeore.app.edge

/**
 * The on-device AI explainer. It only rewords facts EdgeORE already computed (round, tile insight, Round Proof
 * results, the decoded review message). It gets text in and gives text out: it has no reference to the wallet,
 * Mobile Wallet Adapter, receipt signer or keys, and nothing it returns is used to fill amounts, squares or presets.
 */
object AdvisorExplainer {
    const val LABEL = "AI explanation, may be wrong; check the review."
    const val RULES_LABEL = "Rule-based explanation (no AI model answered)."

    enum class Subject(
        val title: String,
    ) {
        ROUND("this round"),
        TILE("this square"),
        ROUND_PROOF("this Round Proof result"),
        REVIEW_MESSAGE("this exact review message"),
    }

    enum class Source { ON_DEVICE_AI, RULES }

    data class Result(
        val text: String,
        val source: Source,
        val note: String?,
    )

    const val SYSTEM =
        "You explain Solana ORE v3 facts to a phone user in at most 5 short plain sentences. " +
            "Use only the FACTS given. Do not invent numbers. Do not suggest amounts, squares or strategies. " +
            "Never promise or estimate earnings, income, profit, yield, APR or APY. " +
            "You cannot sign, send or change anything; tell the user to check the review screen before signing."

    fun prompt(
        subject: Subject,
        facts: String,
    ): String = "Explain ${subject.title} in plain words.\nFACTS:\n" + facts.take(MAX_FACT_CHARS)

    private val BANNED =
        Regex(
            "(?i)\\b(apr|apy|yield|guarantee[ds]?|guaranteed|profit(s|able)?|passive income|earnings?|" +
                "you will (win|earn)|risk[- ]free|financial advice)\\b",
        )

    /** Null when the model said nothing usable or made a claim EdgeORE does not allow. */
    fun accept(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || BANNED.containsMatchIn(t)) return null
        return t.take(MAX_ANSWER_CHARS)
    }

    /**
     * [model] is the selected downloaded on-device model, or null when none is ready (device unsupported, not
     * downloaded, busy). Any failure, refusal or disallowed claim falls back to [ruleText].
     */
    suspend fun explain(
        subject: Subject,
        facts: String,
        ruleText: String,
        model: (suspend (system: String, prompt: String) -> String?)?,
    ): Result {
        val run = model?.let { m -> runModel { m(SYSTEM, prompt(subject, facts)) } }
        val ok = run?.getOrNull()?.let(::accept)
        return when {
            model == null -> Result(ruleText, Source.RULES, RoundExplainer.NOT_CONFIGURED)
            ok != null -> Result("$LABEL\n$ok", Source.ON_DEVICE_AI, null)
            run?.isFailure == true -> Result(ruleText, Source.RULES, unavailable(run.exceptionOrNull()))
            else -> Result(ruleText, Source.RULES, "The model's answer was empty or not allowed. $RULES_LABEL")
        }
    }

    private fun unavailable(t: Throwable?): String {
        val why = t?.javaClass?.simpleName
        return "On-device model unavailable on this phone ($why). $RULES_LABEL"
    }

    /** Like runCatching, but rethrows cancellation. Covers native-library errors on unsupported phones. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runModel(block: suspend () -> String?): kotlin.Result<String?> =
        try {
            kotlin.Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            kotlin.Result.failure(t)
        }

    /** Facts for the decoded review message, taken only from the decoded bytes. */
    fun reviewFacts(
        roundId: Long,
        mask: Int,
        perSquareLamports: Long,
        totalLamports: Long,
        mainnet: Boolean,
    ): String {
        val net = if (mainnet) "Solana mainnet (real SOL, capped at 0.01 SOL)" else "devnet"
        val squares = (0 until SQUARES).filter { mask and (1 shl it) != 0 }.joinToString(",") { "${it + 1}" }
        return "ORE v3 Deploy on $net; round $roundId; squares $squares; " +
            "$perSquareLamports lamports per square; $totalLamports lamports total. SOL deployed may be lost."
    }

    fun proofFacts(c: RoundProof.Check): String {
        val state = if (c.accepted) "re-derived" else "did not re-derive"
        val failed = c.failures.joinToString("; ").ifEmpty { "none" }
        val unverified = c.unverified.joinToString("; ").ifEmpty { "none" }
        return "Round Proof $state; winning square ${c.winningSquare ?: "unknown"}. " +
            "Verified: ${c.verified.joinToString("; ")}. Failed: $failed. Unverified: $unverified. " +
            "This verifies the integrity of signed receipt contents; it is not a fairness guarantee."
    }

    private const val SQUARES = 25
    private const val MAX_FACT_CHARS = 2400
    private const val MAX_ANSWER_CHARS = 1200
}
