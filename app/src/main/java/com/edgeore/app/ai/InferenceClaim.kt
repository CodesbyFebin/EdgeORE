package com.edgeore.app.ai

enum class ExecutionLocation { NOT_RUN, OWNED_HOST, ON_DEVICE, REFUSED }

/** Labels only. A private address or a network-status check is not an inference result. */
object InferenceClaim {
    fun cancellationLabel(clientClosed: Boolean, hostAcknowledgedStop: Boolean): String = when {
        hostAcknowledgedStop -> "Host acknowledged stop."
        clientClosed -> "Stopped receiving."
        else -> "Cancellation was not observed."
    }

    fun classify(
        modelAnswered: Boolean,
        networkDisabled: Boolean,
        runtimeInsideApk: Boolean,
        refused: Boolean = false,
    ): ExecutionLocation = when {
        refused -> ExecutionLocation.REFUSED
        runtimeInsideApk && modelAnswered && networkDisabled -> ExecutionLocation.ON_DEVICE
        modelAnswered && !runtimeInsideApk && !networkDisabled -> ExecutionLocation.OWNED_HOST
        else -> ExecutionLocation.NOT_RUN
    }
}
