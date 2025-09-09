package com.simiacryptus.cognotik.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.simiacryptus.cognotik.chat.ProvidersChatClient
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel.Usage
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.Executors

open class IdeaChatClient(
    key: Map<APIProvider, String> = AppSettingsState.instance.apiKeys?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    apiBase: Map<APIProvider, String> = AppSettingsState.instance.apiBase?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
) : ProvidersChatClient(
    apiKeyMap = key,
    apiBaseMap = apiBase,
    workPool = Executors.newCachedThreadPool(),
) {

    override fun apiBase(provider: APIProvider): String {
        return apiBaseMap[provider] ?: provider.base ?: throw IllegalArgumentException("No API Base configured for provider $provider")
    }

    override fun key(provider: APIProvider): String {
        return apiKeyMap[provider] ?: throw IllegalArgumentException("No API Key configured for provider $provider")
    }

    init {
        require(key.size == apiBase.size) {
            "API Key not configured for all providers: ${key.keys} != ${APIProvider.values().toList()}"
        }
    }

    override fun onUsage(
        model: LLMModel, tokens: Usage,
        logStreams: MutableList<BufferedOutputStream>
    ) {
        ApplicationServices.usageManager.incrementUsage(currentSession, localUser, model, tokens)
        super.onUsage(model, tokens, logStreams)
    }

    companion object {

        val instance by lazy {
            val client = IdeaChatClient()
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

        var lastEvent: AnActionEvent? = null

        private val log = LoggerFactory.getLogger(IdeaChatClient::class.java)
        val currentSession = Session.newGlobalID()
        val localUser = User(id = "1", email = "user@localhost")
    }

}