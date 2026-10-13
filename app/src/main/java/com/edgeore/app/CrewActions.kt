package com.edgeore.app

import com.edgeore.app.EdgeOreViewModel.CrewState
import com.edgeore.app.edge.Crew
import com.edgeore.app.receipts.ReceiptSigner

/**
 * Creator-only suggested plan update after dropouts. Re-signs the plan locally; never deploys or signs a transaction.
 */
internal fun reassignedCrewState(
    current: CrewState,
    active: Set<String>,
    myWallet: String?,
    signer: ReceiptSigner,
): CrewState {
    val signed = current.signed ?: return current
    val next = Crew.reassign(signed.plan, active)
    return when {
        next == null -> current
        signed.plan.creator != myWallet -> current.copy(message = "Only the crew creator can sign a plan update")
        else -> {
            val s = Crew.signWithKeystore(next, signer)
            val note = "Plan updated. Share the new link; each member signs their own deploy."
            CrewState(s, Crew.verify(s), Crew.encode(s), note)
        }
    }
}
