package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.chat.ProvidersChatClient
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.defaultUser
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import java.io.BufferedOutputStream

open class IdeaChatClient(
    key: Map<APIProvider, String> = AppSettingsState.instance.getUserSettings().apis
        .mapNotNull { api -> api.provider?.let { it to (api.key ?: "") } }
        .toMap(),
    apiBase: Map<APIProvider, String> = AppSettingsState.instance.getUserSettings().apis
        .mapNotNull { api -> api.provider?.let { it to (api.baseUrl ?: it.base ?: "") } }
        .toMap(),
) : ProvidersChatClient(
    apiKeyMap = key,
    apiBaseMap = apiBase,
    workPool = AppSettingsState.workPool,
) {

    override fun apiBase(provider: APIProvider): String {
        return apiBaseMap[provider] ?: provider.base ?: throw IllegalArgumentException("No API Base configured for provider $provider")
    }

    override fun key(provider: APIProvider): String {
        return apiKeyMap[provider] ?: throw IllegalArgumentException("No API Key configured for provider $provider")
    }

    init {
        require(key.isNotEmpty()) {
            "No API Keys configured. Please configure API keys in settings."
        }
    }

    override fun onUsage(
        model: LLMModel, tokens: ApiModel.Usage,
        logStreams: MutableList<BufferedOutputStream>
    ) {
        ApplicationServices.usageManager.incrementUsage(AppSettingsState.currentSession, defaultUser, model, tokens)
        super.onUsage(model, tokens, logStreams)
    }

    companion object {
        val instance by lazy { IdeaChatClient() }
    }

}