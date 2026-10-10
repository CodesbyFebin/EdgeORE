package com.edgeore.app.scan

import com.edgeore.app.EdgeOreCore
import com.edgeore.app.crypto.Base58
import java.net.URLDecoder

/**
 * Turns scanned QR text into review-form input. It only ever fills the destination (and, from a Solana Pay
 * transfer link, the amount) of the one supported action. It never signs, never prepares a message and never
 * accepts anything EdgeORE cannot build exactly: SPL tokens, memos, references and transaction-request links are
 * refused with a reason instead of being silently dropped, because dropping them would change what the payee asked for.
 * The user still has to prepare and read the exact-message review before anything reaches the wallet.
 */
object AddressQr {
    sealed interface Result {
        data class Filled(val destination: String, val amountSol: String?, val label: String?, val source: Source) : Result
        data class Refused(val reason: String) : Result
    }
    enum class Source { PLAIN_ADDRESS, SOLANA_PAY }

    /** Length cap: a Solana Pay transfer link with every optional field is far shorter than this. */
    const val MAX_LEN = 2048

    fun isAddress(text: String): Boolean = runCatching { Base58.decodePublicKey(text.trim()) }.isSuccess

    fun parse(raw: String?): Result {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Result.Refused("The QR code is empty.")
        if (text.length > MAX_LEN) return Result.Refused("The QR code is too long to be a Solana address or transfer link.")
        if (isAddress(text)) return Result.Filled(text, null, null, Source.PLAIN_ADDRESS)
        if (!text.startsWith("solana:", ignoreCase = true)) {
            return Result.Refused("This QR code is not a Solana address or a solana: transfer link.")
        }
        val body = text.substring("solana:".length)
        val pathPart = body.substringBefore('?')
        val query = if ('?' in body) body.substringAfter('?') else ""
        val recipient = decode(pathPart)
        if (recipient.startsWith("http", ignoreCase = true)) {
            return Result.Refused("This is a Solana Pay transaction request (a server builds the transaction). EdgeORE only builds and reviews its own devnet SOL transfer.")
        }
        if (!isAddress(recipient)) return Result.Refused("The link's recipient is not a valid Solana address.")
        val params = LinkedHashMap<String, String>()
        if (query.isNotEmpty()) for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val k = decode(pair.substringBefore('=')).lowercase()
            val v = decode(pair.substringAfter('=', ""))
            if (params.containsKey(k)) return Result.Refused("The link repeats \"$k\", so it is ambiguous.")
            params[k] = v
        }
        params["spl-token"]?.let { return Result.Refused("The link asks for an SPL token transfer. EdgeORE only supports a devnet SOL transfer.") }
        params["reference"]?.let { return Result.Refused("The link needs a reference account added to the transaction. EdgeORE cannot build that, so it will not pretend to.") }
        params["memo"]?.let { return Result.Refused("The link needs a memo instruction. EdgeORE cannot build that, so it will not pretend to.") }
        val unknown = params.keys - setOf("amount", "label", "message")
        if (unknown.isNotEmpty()) return Result.Refused("The link has fields EdgeORE does not understand: ${unknown.sorted().joinToString()}.")
        val amount = params["amount"]?.takeIf { it.isNotEmpty() }
        if (amount != null) {
            val ok = runCatching { EdgeOreCore.lamports(amount) > 0 }.getOrDefault(false)
            if (!ok) return Result.Refused("The link's amount \"$amount\" is not a positive SOL value with at most 9 decimals.")
        }
        val label = params["label"]?.take(64)?.takeIf { it.isNotBlank() }
        return Result.Filled(recipient, amount, label, Source.SOLANA_PAY)
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
}
