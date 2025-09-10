package com.simiacryptus.cognotik

import com.intellij.openapi.actionSystem.AnActionEvent
import com.simiacryptus.cognotik.chat.ProvidersChatClient
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import java.io.BufferedOutputStream

open class IdeaChatClient(
    key: Map<APIProvider, String> = AppSettingsState.Companion.instance.apiKeys?.mapKeys { APIProvider.Companion.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    apiBase: Map<APIProvider, String> = AppSettingsState.Companion.instance.apiBase?.mapKeys { APIProvider.Companion.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
) : ProvidersChatClient(
    apiKeyMap = key,
    apiBaseMap = apiBase,
    workPool = workPool,
) {

    override fun apiBase(provider: APIProvider): String {
        return apiBaseMap[provider] ?: provider.base ?: throw IllegalArgumentException("No API Base configured for provider $provider")
    }

    override fun key(provider: APIProvider): String {
        return apiKeyMap[provider] ?: throw IllegalArgumentException("No API Key configured for provider $provider")
    }

    init {
        require(key.size == apiBase.size) {
            "API Key not configured for all providers: ${key.keys} != ${APIProvider.Companion.values().toList()}"
        }
    }

    override fun onUsage(
        model: LLMModel, tokens: ApiModel.Usage,
        logStreams: MutableList<BufferedOutputStream>
    ) {
        ApplicationServices.usageManager.incrementUsage(currentSession, localUser, model, tokens)
        super.onUsage(model, tokens, logStreams)
    }

    companion object {
        val instance by lazy { IdeaChatClient() }
        var lastEvent: AnActionEvent? = null
        val currentSession = Session.Companion.newGlobalID()
        val localUser = User(id = "1", email = "user@localhost")
        val workPool = ApplicationServices.clientManager.getPool(currentSession, localUser)
    }

}