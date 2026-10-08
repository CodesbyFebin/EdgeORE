package com.edgeore.app.device

/** Persisted safety and resource controls. */
data class ResourceSettings(
    val edgeModeResumed: Boolean = false,
    val chargeOnly: Boolean = true,
    val thermalGuard: Boolean = true,
    val batteryReservePercent: Int = 20,
    val cpuLimitPercent: Int = 50,
    val dailyLimitLamports: Long = 50_000_000, // 0.05 SOL on devnet
)

/** Ring states from the design spec. ACTIVE is never reached without a real qualified workload. */
enum class EdgeState(val label: String) { UNAVAILABLE("Unavailable"), IDLE("Idle"), STARTING("Starting"), ACTIVE("Active"), PAUSING("Pausing"), PAUSED("Paused"), FAILED("Failed") }

data class Gate(val name: String, val allowed: Boolean, val explanation: String)

data class EdgeEvaluation(val state: EdgeState, val headline: String, val gates: List<Gate>) {
    val gatesPass: Boolean get() = gates.all { it.allowed }
}

/**
 * Evaluates the safety controls against real device observations. Controls that cannot be
 * observed fail closed. This build ships no qualified workload, so a resumed Edge Mode is IDLE.
 */
object EdgePolicy {
    const val QUALIFIED_WORKLOAD_AVAILABLE = false

    fun evaluate(s: ResourceSettings, d: DeviceSnapshot?): EdgeEvaluation {
        val gates = mutableListOf<Gate>()
        if (s.chargeOnly) {
            gates += when (d?.charging) {
                true -> Gate("Charge-only", true, "Charging observed")
                false -> Gate("Charge-only", false, "Not charging")
                null -> Gate("Charge-only", false, "Charging state not observed")
            }
        }
        if (s.thermalGuard) {
            val t = d?.thermal
            gates += when {
                t == null -> Gate("Thermal guard", false, "Thermal status unavailable on this device")
                t.ordinal >= ThermalLevel.MODERATE.ordinal -> Gate("Thermal guard", false, "Thermal status ${t.label}")
                else -> Gate("Thermal guard", true, "Thermal status ${t.label}")
            }
        }
        val pct = d?.batteryPercent
        gates += when {
            pct == null -> Gate("Battery reserve", false, "Battery level not observed")
            pct < s.batteryReservePercent -> Gate("Battery reserve", false, "Battery $pct% is below reserve ${s.batteryReservePercent}%")
            else -> Gate("Battery reserve", true, "Battery $pct% ≥ reserve ${s.batteryReservePercent}%")
        }
        val state: EdgeState
        val headline: String
        when {
            !s.edgeModeResumed -> { state = EdgeState.PAUSED; headline = "Paused by you" }
            gates.any { !it.allowed } -> { state = EdgeState.PAUSED; headline = "Paused by policy: " + gates.first { !it.allowed }.explanation }
            !QUALIFIED_WORKLOAD_AVAILABLE -> { state = EdgeState.IDLE; headline = "Idle · no qualified workload in this build" }
            else -> { state = EdgeState.IDLE; headline = "Ready" }
        }
        return EdgeEvaluation(state, headline, gates)
    }
}
