package com.simiacryptus.cognotik.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.chat.ProvidersChatClient
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel.*
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

open class IdeaChatClient(
    key: Map<APIProvider, String> = AppSettingsState.instance.apiKeys?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    apiBase: Map<APIProvider, String> = AppSettingsState.instance.apiBase?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    reasoningEffort: ReasoningEffort = ReasoningEffort.valueOf(AppSettingsState.instance.reasoningEffort)
) : ProvidersChatClient(
    apiKeyMap = key,
    apiBaseMap = apiBase,
    reasoningEffort = reasoningEffort,
    workPool = Executors.newCachedThreadPool(),
) {

    init {

        require(key.size == apiBase.size) {
            "API Key not configured for all providers: ${key.keys} != ${APIProvider.values().toList()}"
        }
    }

    override fun onUsage(
        model: LLMModel, tokens: Usage,
        logStreams: MutableList<BufferedOutputStream>
    ) {
        ApplicationServices.usageManager.incrementUsage(currentSession, localUser, model!!, tokens)
        super.onUsage(model, tokens, logStreams)
    }

    @Suppress("NAME_SHADOWING")
    override fun chat(
        chatRequest: ChatRequest,
        model: LLMModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ChatResponse {
        val storeMetadata = AppSettingsState.instance.storeMetadata
        var chatRequest = chatRequest.copy(
            store = storeMetadata?.let { it.isNotBlank() },
            metadata = storeMetadata?.let { JsonUtil.fromJson(it, Map::class.java) }
        )
        val lastEvent = lastEvent
        if(lastEvent != null) chatRequest = chatRequest.copy(
            store = chatRequest.store,
            metadata = chatRequest.metadata?.let {
                it + mapOf(
                    "project" to lastEvent.project?.name,
                    "action" to lastEvent.presentation.text,
                    "language" to lastEvent.getData(CommonDataKeys.PSI_FILE)?.language?.displayName,
                )
            }
        )
        val response = super.chat(chatRequest, model, logStreams)
        if (null != response.usage) {
            UITools.logAction(
                "Chat Response: ${toJson(response.usage!!)}"
            )
        }
        return response
    }

    companion object {

        val instance
            get() = _instance.apply {
                reasoningEffort = AppSettingsState.instance.reasoningEffort.let(ReasoningEffort::valueOf)
            }

        private val _instance by lazy {
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

        private fun <T : Any> execute(
            fn: () -> T
        ): T? {
            val application = ApplicationManager.getApplication()
            val ref: AtomicReference<T> = AtomicReference()
            if (null != application) {
                application.invokeAndWait { ref.set(fn()) }
            } else {
                ref.set(fn())
            }
            return ref.get()
        }

        private val log = LoggerFactory.getLogger(IdeaChatClient::class.java)
        val currentSession = Session.newGlobalID()
        val localUser = User(id = "1", email = "user@localhost")
    }

}