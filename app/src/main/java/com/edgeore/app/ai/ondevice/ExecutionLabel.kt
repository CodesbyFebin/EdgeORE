package com.edgeore.app.ai.ondevice

/**
 * Title of the AI page's execution card (hardening backlog H5). It names what can actually run right now:
 * "On-device" appears only once a model file is on this phone, and "loaded" only once the runtime opened it.
 * Neither title claims that a generation has run.
 */
object ExecutionLabel {
    fun title(downloaded: Set<String>, loadedId: String?): String = when {
        loadedId != null -> "On-device model loaded"
        downloaded.isNotEmpty() -> "On-device model downloaded, not loaded"
        else -> "Owned-host AI · on-device not set up"
    }
}
