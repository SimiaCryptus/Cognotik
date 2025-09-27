package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.OpenAIClient
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import org.apache.hc.core5.http.HttpRequest
import java.util.concurrent.Executors

class IdeaOpenAIClient : OpenAIClient(
    key = emptyMap(),
    apiBase = emptyMap(),
    workPool = Executors.newCachedThreadPool(),
    scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
        AppSettingsState.currentSession,
        UserSettingsManager.defaultUser
    ),
) {

    init {

        require(key.size == apiBase.size) {
            "API Key not configured for all providers: ${key.keys} != ${APIProvider.values().toList()}"
        }
    }

    override fun onUsage(model: AIModel?, tokens: ApiModel.Usage) {
        ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).usageManager.incrementUsage(
            AppSettingsState.currentSession,
            UserSettingsManager.defaultUser, model!!, tokens
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