package com.edgeore.app.ui

import com.edgeore.app.EdgeOreCore
import com.edgeore.app.scan.AddressQr

/** Inline form checks for the review route. They only gate "Prepare"; TransferReview still decides on the real bytes. */
object ReviewInput {
    data class Check(val ok: Boolean, val error: Boolean, val text: String)

    fun destination(v: String): Check = when {
        v.isBlank() -> Check(false, false, "Type, paste or scan the recipient's Solana address.")
        AddressQr.isAddress(v) -> Check(true, false, "Valid Solana address format. Format alone does not prove who owns it.")
        else -> Check(false, true, "Not a valid Solana address (base58, 32 bytes).")
    }

    fun amount(v: String): Check {
        if (v.isBlank()) return Check(false, false, "Enter an amount in devnet SOL, for example 0.01.")
        val lamports = runCatching { EdgeOreCore.lamports(v.trim()) }.getOrNull()
            ?: return Check(false, true, "Use digits with at most 9 decimals, for example 0.01.")
        if (lamports <= 0) return Check(false, true, "The amount must be greater than zero.")
        return Check(true, false, "$lamports lamports.")
    }
}
