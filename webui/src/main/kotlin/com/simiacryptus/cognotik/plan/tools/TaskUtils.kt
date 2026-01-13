package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.OutputStream

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

/**
 * Implements the "Triple Log Rule" from Cognotik IO Best Practices.
 * Logs to UI, SLF4J, and the Task Transcript.
 */
fun SessionTask.tripleLog(
    e: Throwable,
    log: Logger,
    transcript: OutputStream? = null,
    contextMessage: String = "An error occurred"
) {
    // 1. UI: Visual feedback for the user
    this.error(e)

    // 2. SLF4J: System operational layer (Single line preferred)
    log.error("$contextMessage: ${e.message}")

    // 3. Transcript: Audit trail with stack trace in <details> tag
    if (transcript != null) {
        try {
            val errorEntry = """
                ## Error: $contextMessage
                
                **Message:** `${e.message}`
                
                <details>
                <summary>Stack Trace</summary>
                
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent()
            transcript.write(errorEntry.toByteArray())
        } catch (transcriptEx: Exception) {
            log.warn("Failed to write to transcript: ${transcriptEx.message}")
        }
    }
}


fun ChatModel.toApiChatModel(): ApiChatModel {
    val apis = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis
    return ApiChatModel(
        model = this, provider = ApiData(
            key = apis.find { it.provider == this.provider }?.key
                ?: throw IllegalArgumentException("No API Key for ${this.provider?.name}"),
            baseUrl = apis.find { it.provider == this.provider }?.baseUrl ?: this.provider?.base ?: "",
            provider = this.provider,
        ).validate()
    )
}