package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

/**
 * Validates and retrieves the default chatter API, handling errors consistently.
 * Returns null if validation fails, after logging and updating the task.
 */
fun validateAndGetApi(
    orchestrationConfig: OrchestrationConfig,
    task: SessionTask,
    log: Logger,
    resultFn: (String) -> Unit
): ChatInterface? {
    val api = orchestrationConfig.defaultChatter
    if (api == null) {
        log.error("No default chatter available")
        task.complete("ERROR: No API available")
        resultFn("ERROR: No API available")
        return null
    }
    return api
}

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