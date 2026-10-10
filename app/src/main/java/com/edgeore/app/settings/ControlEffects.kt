package com.edgeore.app.settings

import com.edgeore.app.device.CpuBudget

/** What a user-facing control actually does in this build. Every Switch and Slider on a screen shows one of these. */
enum class Effect(val label: String) {
    /** Code changes behaviour when the control changes. */
    ENFORCED("Enforced"),
    /** The value is stored and shown, but nothing reads it to change behaviour. */
    SAVED_ONLY("Saved only"),
    /** The control cannot take effect because the mechanism does not exist in this build. */
    UNAVAILABLE("Unavailable"),
}

/**
 * The single source of truth for control effects. The text names the code path that applies the
 * setting (or says that none does). ControlEffectsTest pins each entry to that code path.
 */
enum class Control(val screen: String, val title: String, val effect: Effect, val detail: String) {
    CHARGE_ONLY("Mine", "Charge-only mode", Effect.ENFORCED,
        "EdgePolicy blocks Edge Mode while unplugged. No mining workload exists yet, so this gates the Edge Mode state only."),
    THERMAL_GUARD("Mine", "Thermal guard", Effect.ENFORCED,
        "EdgePolicy blocks Edge Mode at Moderate or hotter Android thermal status, and when thermal status cannot be read. It does not stop chat or downloads."),
    BATTERY_RESERVE("Mine", "Battery reserve", Effect.ENFORCED,
        "EdgePolicy blocks Edge Mode below this battery level. It gates the Edge Mode state only."),
    CONTRIBUTION_SCHEDULER("Mine", "Contribution scheduler", Effect.ENFORCED,
        "Off by default. When on and Edge Mode is resumed, a WorkManager job is scheduled that Android starts only on an unmetered network, while charging, with battery not low. The job re-checks pause and the safety controls. No qualified workload exists in this build, so it records that and runs nothing."),
    CPU_LIMIT("Mine", "CPU limit", Effect.SAVED_ONLY,
        "Stored workload budget. Not a device-wide CPU cap. No workload reads it in this build."),
    AI_MEMORY_LIMIT("AI", "Model memory limit", Effect.SAVED_ONLY,
        "Stored only. Model memory use is not measured, so the number is not enforced."),
    AI_THERMAL_GUARD("AI", "Thermal auto-pause", Effect.ENFORCED,
        "Same setting as Thermal guard on Mine. It gates Edge Mode. Chat requests are not blocked by it."),
    PAUSE_COMPUTE_DURING_CHAT("AI", "Pause compute during chat", Effect.ENFORCED,
        "Sending a prompt sets Edge Mode back to paused."),
    AI_ALLOCATION_CHARS("AI", "Memory allocation", Effect.ENFORCED,
        "The attached document is cut to this many characters before it is sent with a prompt."),
    VAULT_ALLOWANCE("Storage", "Vault allowance", Effect.ENFORCED,
        "Imports that would exceed allowance − used − in-progress are refused. 0 means no allowance; per-file and free-space limits still apply."),
    SHARING_CONSENT("Storage", "Bandwidth sharing", Effect.SAVED_ONLY,
        "Consent is recorded in the audit trail. No sharing protocol exists, so no bytes are shared."),
    SHARING_QUOTA("Storage", "Daily sharing quota", Effect.SAVED_ONLY,
        "Stored only. Shared traffic is not metered because nothing is shared."),
    KILL_SWITCH("Storage", "Block traffic on disconnect", Effect.UNAVAILABLE,
        "No VPN tunnel exists in this build, so no traffic can be blocked."),
    PAUSE_ON_METERED("Storage", "Pause sharing consent on metered", Effect.ENFORCED,
        "Turning sharing consent on is refused while the network is metered."),
    HIDE_DEVICE_IDS("Receipts", "Hide device identifiers", Effect.ENFORCED,
        "The export omits the device public key."),
    INCLUDE_LOCATION("Receipts", "Include location", Effect.UNAVAILABLE,
        "No location is collected, so exports never contain one."),
    ;

    companion object {
        fun cpuDetail(percent: Int): String = CpuBudget.storedBudgetLabel(percent)
    }
}
