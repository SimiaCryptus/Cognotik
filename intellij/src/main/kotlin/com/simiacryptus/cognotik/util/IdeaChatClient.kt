package com.simiacryptus.cognotik.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel.*
import com.simiacryptus.jopenai.models.OpenAIModel
import com.simiacryptus.jopenai.models.TextModel
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.JsonUtil.toJson
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel
import javax.swing.JTextArea

open class IdeaChatClient(
    key: Map<APIProvider, String> = AppSettingsState.instance.apiKeys?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    apiBase: Map<APIProvider, String> = AppSettingsState.instance.apiBase?.mapKeys { APIProvider.valueOf(it.key) }?.entries?.toTypedArray()
        ?.associate { it.key to it.value } ?: mapOf(),
    reasoningEffort: ReasoningEffort = ReasoningEffort.valueOf(AppSettingsState.instance.reasoningEffort)
) : ChatClient(
    key = key,
    apiBase = apiBase,
    reasoningEffort = reasoningEffort,
    workPool = Executors.newCachedThreadPool(),
) {

    init {

        require(key.size == apiBase.size) {
            "API Key not configured for all providers: ${key.keys} != ${APIProvider.values().toList()}"
        }
    }

    private class IdeaChildClient(
        val inner: IdeaChatClient,
        key: Map<APIProvider, String>,
        apiBase: Map<APIProvider, String>
    ) : IdeaChatClient(
        key = key,
        apiBase = apiBase,
        reasoningEffort = inner.reasoningEffort
    ) {
        override fun log(level: Level, msg: String) {
            super.log(level, msg)
            inner.log(level, msg)
        }
    }

    override fun getChildClient(): ChatClient = IdeaChildClient(inner = this, key = key, apiBase = apiBase).apply {
        session = inner.session
        user = inner.user
        textCompressor = inner.textCompressor
    }

    private val isInRequest = AtomicBoolean(false)

    override fun onUsage(model: OpenAIModel?, tokens: Usage) {
        ApplicationServices.usageManager.incrementUsage(currentSession, localUser, model!!, tokens)
        super.onUsage(model, tokens)
    }

    override fun authorize(request: HttpRequest, apiProvider: APIProvider) {
        val checkApiKey = key.get(apiProvider) ?: throw IllegalArgumentException("No API Key for $apiProvider")
        key = key.toMutableMap().let {
            it[apiProvider] = checkApiKey
            it
        }.entries.toTypedArray().associate { it.key to it.value }
        super.authorize(request, apiProvider)
    }

    @Suppress("NAME_SHADOWING")
    override fun chat(
        chatRequest: ChatRequest,
        model: TextModel
    ): ChatResponse {
        val storeMetadata = AppSettingsState.instance.storeMetadata
        var chatRequest = chatRequest.copy(
            store = storeMetadata?.let { it.isNotBlank() },
            metadata = storeMetadata?.let { JsonUtil.fromJson(it, Map::class.java) }
        )
        val lastEvent = lastEvent
        lastEvent ?: return super.chat(chatRequest, model)
        chatRequest = chatRequest.copy(
            store = chatRequest.store,
            metadata = chatRequest.metadata?.let {
                it + mapOf(
                    "project" to lastEvent.project?.name,
                    "action" to lastEvent.presentation.text,
                    "language" to lastEvent.getData(CommonDataKeys.PSI_FILE)?.language?.displayName,
                )
            }
        )
        if (isInRequest.getAndSet(true)) {
            val response = super.chat(chatRequest, model)
            if (null != response.usage) {
                UITools.logAction(
                    "Chat Response: ${toJson(response.usage!!)}"
                )
            }
            return response
        } else {
            try {
                val response = super.chat(chatRequest, model)
                if (null != response.usage) {
                    UITools.logAction(
                        "Chat Response: ${toJson(response.usage!!)}"
                    )
                }
                return response
            } finally {
                isInRequest.set(false)
            }
        }
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