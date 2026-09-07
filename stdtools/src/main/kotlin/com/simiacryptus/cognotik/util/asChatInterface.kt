package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.ChatInterface
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.asApiChatModel

fun ChatModel.asChatInterface(
    user: User
): ChatInterface {
    val userSettings = ApplicationServicesImpl.fileApplicationServices().userSettingsManager.getUserSettings(user)
    val name = provider?.name ?: throw IllegalStateException("Provider not specified for model $modelId")
    val secureString = (userSettings.apis.find { it.provider?.name == name }?.key
        ?: throw IllegalStateException("API key for model provider $name not found in user settings"))
    return asApiChatModel((secureString).decrypt!!).instance(user)
}