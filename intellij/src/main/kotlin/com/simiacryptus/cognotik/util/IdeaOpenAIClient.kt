package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.OpenAIModel
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory
import java.io.File
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

    override fun onUsage(model: OpenAIModel?, tokens: ApiModel.Usage) {

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

            val client = IdeaOpenAIClient()
            if (AppSettingsState.instance.apiLog) {
                try {
                    val file = File(AppSettingsState.instance.pluginHome, "openai.log")
                    file.parentFile.mkdirs()
                    AppSettingsState.auxiliaryLog = file
                    client.logStreams.add(java.io.FileOutputStream(file, file.exists()).buffered())
                } catch (e: Exception) {
                    log.warn("Error initializing log file", e)
                }
            }
            client
        }
        val log = LoggerFactory.getLogger(IdeaOpenAIClient::class.java)
    }
}