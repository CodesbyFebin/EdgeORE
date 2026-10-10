package com.edgeore.app.ui

import com.edgeore.app.solana.OpState
import com.edgeore.app.solana.PendingOperation
import com.edgeore.app.ui.components.PillTone

/**
 * Counts shown on the bottom navigation, computed only from local state: operations that still need an outcome
 * observed (Mine) and receipt-log lines that could not be read (Receipts). A zero count shows no badge.
 * The tab-badge idea is adapted from OptimAI Agentic for Android, ui/AppRoot.kt (MIT; see NOTICE).
 */
object NavBadges {
    data class Badge(val count: Int, val tone: PillTone, val spoken: String)

    fun waiting(ops: List<PendingOperation>): Int = ops.count { it.state.needsObservation || it.state == OpState.SIGNED }

    fun of(ops: List<PendingOperation>, damagedLines: Int): Map<Destination, Badge> = buildMap {
        val w = waiting(ops)
        if (w > 0) put(Destination.Mine, Badge(w, PillTone.COPPER, "$w operation${if (w == 1) "" else "s"} awaiting an outcome"))
        if (damagedLines > 0) put(Destination.Receipts, Badge(damagedLines, PillTone.DANGER, "$damagedLines receipt line${if (damagedLines == 1) "" else "s"} could not be read"))
    }
}
