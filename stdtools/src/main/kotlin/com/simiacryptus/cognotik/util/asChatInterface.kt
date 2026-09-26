package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.ChatInterface
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.ServiceKey
import com.simiacryptus.cognotik.platform.ServiceMap
import com.simiacryptus.cognotik.platform.service.asApiChatModel

fun ChatModel.asChatInterface(
    user: User
): ChatInterface {
    val userSettings =
      (ServiceMap.services
        ?: throw IllegalStateException("ApplicationServices not initialized"))[ServiceKey.USER_SETTINGS].getUserSettings(user)
    val name = provider?.name ?: throw IllegalStateException("Provider not specified for model $modelId")
    val secureString = (userSettings.apis.find { it.provider?.name == name }?.key
        ?: throw IllegalStateException("API key for model provider $name not found in user settings"))
    return asApiChatModel((secureString).decrypt!!).instance(user)
}