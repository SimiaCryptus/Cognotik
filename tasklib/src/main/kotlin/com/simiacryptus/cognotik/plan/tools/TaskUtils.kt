package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

/**
 * Truncates text for display with an ellipsis indicator.
 * Follows Cognotik IO Best Practices for concise UI output.
 */
fun String.truncateForDisplay(maxLength: Int = 10000): String {
  return if (this.length > maxLength) {
    "${this.take(maxLength)}\n\n> _... (truncated for display, ${this.length - maxLength} characters omitted)_"
  } else {
    this
  }
}

/**
 * Safely completes a task with error handling and Markdown rendering.
 * Ensures the UI spinner is removed even if rendering fails.
 * Follows Cognotik IO Best Practices for UI output.
 */
fun SessionTask.safeComplete(message: String, log: Logger) {
  try {
    // Use renderMarkdown extension as per Cognotik IO Best Practices
    this.complete(message.renderMarkdown())
  } catch (e: Exception) {
    log.error("Error completing task with message: $message", e)
    // Fallback to raw message if markdown rendering fails, ensuring task.complete() is called to clear UI state
    try {
      this.complete(message)
    } catch (e2: Exception) {
      log.error("Critical failure in SessionTask.complete", e2)
    }
  }
}
