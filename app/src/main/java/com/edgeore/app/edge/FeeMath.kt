package com.edgeore.app.edge

/**
 * One wording and one calculator for fees, from calculate_fees() in api/src/state/round.rs at
 * regolith-labs/ore 48c203bd: every square with SOL pays a 1% admin fee (deployed / 100, at least 1 lamport);
 * a losing square also pays 10% of what is left (at least 1 lamport); the rest is refunded.
 */
object FeeMath {
    const val SOURCE = "calculate_fees, regolith-labs/ore 48c203bd"
    const val LOSING =
        "Losing squares: your SOL is refunded minus a 10% fee, plus a 1% admin fee (about 89.1% comes back)."
    const val WINNING = "Winning square: your SOL is refunded minus the 1% admin fee."

    private const val ADMIN_DIVISOR = 100L
    private const val PROTOCOL_DIVISOR = 10L

    fun adminFee(squareTotal: Long): Long = if (squareTotal <= 0) 0 else maxOf(squareTotal / ADMIN_DIVISOR, 1)

    fun protocolFee(squareTotal: Long): Long =
        if (squareTotal <= 0) 0 else maxOf((squareTotal - adminFee(squareTotal)) / PROTOCOL_DIVISOR, 1)

    /** SOL refunded on a losing square for [mine] of the square's [squareTotal] (checkpoint.rs, pro rata). */
    fun losingRefund(
        mine: Long,
        squareTotal: Long,
    ): Long {
        if (mine <= 0 || squareTotal <= 0) return 0
        require(mine <= squareTotal)
        val keep = (squareTotal - adminFee(squareTotal) - protocolFee(squareTotal)).coerceAtLeast(0)
        return java.math.BigInteger
            .valueOf(mine)
            .multiply(java.math.BigInteger.valueOf(keep))
            .divide(java.math.BigInteger.valueOf(squareTotal))
            .toLong()
    }
}
