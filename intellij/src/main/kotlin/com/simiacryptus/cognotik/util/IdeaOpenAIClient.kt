package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.OpenAIClient
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import org.apache.hc.core5.http.HttpRequest
import java.util.concurrent.Executors

class IdeaOpenAIClient : OpenAIClient(
    key = AppSettingsState.instance.apiKeys?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    apiBase = AppSettingsState.instance.apiBase?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    workPool = Executors.newCachedThreadPool(),
) {

    init {

        require(key.size == apiBase.size) {
            "API Key not configured for all providers: ${key.keys} != ${APIProvider.values().toList()}"
        }
    }

    override fun onUsage(model: AIModel?, tokens: ApiModel.Usage) {

        ApplicationServices.usageManager.incrementUsage(
            IdeaChatClient.currentSession,
            IdeaChatClient.localUser, model!!, tokens
        )
    }

    override fun authorize(request: HttpRequest, apiProvider: APIProvider) {
        val checkApiKey =
            key.get(apiProvider) ?: throw IllegalArgumentException("No API Key for $apiProvider")
        key = key.toMutableMap().let {
            it[apiProvider] = checkApiKey
            it
        }.entries.toTypedArray().associate { it.key to it.value }
        super.authorize(request, apiProvider)
    }

    companion object {

        val instance by lazy {
            IdeaOpenAIClient()
        }
        val log = LoggerFactory.getLogger(IdeaOpenAIClient::class.java)
    }
}