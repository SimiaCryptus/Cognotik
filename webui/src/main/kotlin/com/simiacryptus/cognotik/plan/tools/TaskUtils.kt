package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

/**
 * Truncates text for display with an ellipsis indicator.
 */
fun String.truncateForDisplay(maxLength: Int = 10000): String {
    return if (this.length > maxLength) {
        "${this.take(maxLength)}\n... (truncated for display, ${this.length - maxLength} characters omitted)"
    } else {
        this
    }
}

/**
 * Safely completes a task with error handling.
 */
fun SessionTask.safeComplete(message: String, log: Logger) {
    try {
        this.complete(message)
    } catch (e: Exception) {
        log.warn("Error completing task: ${e.message}")
    }
}