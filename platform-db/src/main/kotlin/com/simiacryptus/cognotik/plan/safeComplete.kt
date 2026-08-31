package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.platform.ApiData
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
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
fun ISessionTask.safeComplete(message: String, log: Logger) {
  try {
    // Use renderMarkdown extension as per Cognotik IO Best Practices
    this.complete(message.renderMarkdown())
  } catch (e: Exception) {
    log.error("Error completing task with message: $message", e)
    // Fallback to raw message if markdown rendering fails, ensuring task.complete() is called to clear UI state
    try {
      this.complete(message)
    } catch (e2: Exception) {
      log.error("Critical failure in ISessionTask.complete", e2)
    }
  }
}

fun ChatModel.toApiChatModel(user: User): ApiChatModel {
  val apis =
    ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user).apis
  return ApiChatModel(
    model = this, provider = ApiData(
      key = apis.find { it.provider == this.provider }?.key
        ?: throw IllegalArgumentException("No API Key for ${this.provider?.name}"),
      baseUrl = apis.find { it.provider == this.provider }?.baseUrl ?: this.provider?.base ?: "",
      provider = this.provider,
    ).validate()
  )
}