package com.edgeore.app.solana

import com.edgeore.app.crypto.Base58
import java.math.BigDecimal

/**
 * Plain-language reading of a review draft, derived only from the decoded message bytes ([TransferReview.Draft.decoded]),
 * never from the form inputs. It sits on top of the exact-message fields and never replaces them: if the bytes do not
 * decode as the one supported transfer, it says so and explains nothing else.
 */
object PlainLanguage {
    data class Explanation(val headline: String, val points: List<String>, val supported: Boolean)

    fun explain(draft: TransferReview.Draft, feeKnown: Boolean, feeLamports: Long?, cluster: String = SolanaRpc.CLUSTER): Explanation {
        val dec = draft.decoded
        if (dec !is SolanaMessage.Decoded.SupportedTransfer) {
            val why = (dec as SolanaMessage.Decoded.Unsupported).reason
            return Explanation(
                "EdgeORE cannot explain this one, so approval stays off",
                listOf(
                    "These bytes are not the one action EdgeORE understands (a single devnet SOL transfer): $why.",
                    "Nothing is guessed or approximated. Start a new review with a valid address and amount.",
                ),
                supported = false,
            )
        }
        val t = dec.transfer
        val from = Base58.encode(t.from)
        val to = Base58.encode(t.to)
        val self = t.from.contentEquals(t.to)
        val amount = sol(t.lamports)
        val points = buildList {
            add(if (self) "Moves $amount from your account back to itself (a self-transfer). Only the network fee leaves it."
                else "Sends $amount from your account ${short(from)} to ${short(to)}.")
            add("Runs on Solana $cluster. Devnet SOL is test currency with no market value.")
            add(if (feeKnown && feeLamports != null) "Your wallet also pays a network fee of ${sol(feeLamports)} ($feeLamports lamports), as priced by the RPC for these exact bytes."
                else "The network fee is unknown because the RPC could not price these bytes. Your wallet shows the fee it will charge.")
            add("It is one System Program transfer and nothing else. It grants no token approval, no delegate and no authority change.")
            add("It is valid only until its recent blockhash expires (usually one to two minutes). After that EdgeORE asks for a new review.")
            add("Once devnet accepts it, it cannot be reversed.")
        }
        return Explanation(if (self) "Self-transfer of $amount" else "Send $amount to ${short(to)}", points, supported = true)
    }

    private fun sol(lamports: Long): String = BigDecimal(lamports).movePointLeft(9).stripTrailingZeros().toPlainString() + " SOL"
    private fun short(s: String): String = if (s.length <= 9) s else s.take(4) + "…" + s.takeLast(4)
}
