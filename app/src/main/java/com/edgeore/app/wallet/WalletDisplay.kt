package com.edgeore.app.wallet

import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.WalletState

/** Text shown on the Mine wallet card. Pure, so the shown state can be tested without a device. */
object WalletDisplay {
    /** The connection line. A failure is never rendered as the neutral "Not connected". */
    fun statusLine(wallet: WalletState): String = when {
        wallet.busy -> wallet.status
        wallet.address != null -> wallet.status
        wallet.error != null -> "Connection failed"
        else -> wallet.status
    }

    /** User-facing failure text: what happened, what to do, and a sanitized reference code. */
    fun errorLines(error: ConnectFailure, showDiagnostics: Boolean): List<String> = buildList {
        add(error.code.message)
        add(error.code.action)
        add("Code ${error.code.name} · stage ${error.stage.name} · ${error.atUtc}")
        if (showDiagnostics) {
            add(buildString {
                append("Debug: ").append(error.exceptionType ?: "no exception")
                error.causeType?.let { append(" ← ").append(it) }
                error.rpcCode?.let { append(" · rpc ").append(it) }
            })
        }
    }

    /** Balance line for an authorized account. A failed request reads "Balance unavailable", never zero. */
    fun balanceLine(balance: RpcObservation?, fresh: (RpcObservation.Fresh) -> String): String = when (balance) {
        is RpcObservation.Fresh -> fresh(balance)
        is RpcObservation.Unavailable -> "Balance unavailable"
        null -> "Balance not requested"
    }
}
