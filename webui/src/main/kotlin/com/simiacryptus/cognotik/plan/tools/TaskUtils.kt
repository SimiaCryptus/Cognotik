package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
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