package com.simiacryptus.jopenai.chat

import com.simiacryptus.jopenai.HttpClientManager
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ApiModel.Usage
import com.simiacryptus.jopenai.models.chat.LLMModel
import com.simiacryptus.util.copy
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService


abstract class SingleProviderChatClient(
    protected val provider: APIProvider,
    val apiKey: String,
    val apiBase: String = provider.base!!,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
) : ChatClientBase(
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams
)

abstract class ChatClientBase(
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
) : HttpClientManager(
    logLevel = logLevel, logStreams = logStreams, workPool = workPool
), ChatClientInterface {

    var session: Any? = null
    var user: Any? = null
    override var budget: Number? = null

    @Throws(IOException::class, InterruptedException::class)
    fun post(
        url: String,
        json: String,
        apiProvider: APIProvider,
        requestID: String = UUID.randomUUID().toString(),
        logStreams: MutableList<java.io.BufferedOutputStream> = this.logStreams
    ): String {
        validatePostRequest(url, json)
        val request = HttpPost(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        authorize(request, apiProvider)
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        return post(request, requestID = requestID, logStreams = logStreams)
    }

    private fun validatePostRequest(url: String, json: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(json.isNotBlank()) { "JSON payload cannot be blank" }
        require(url.startsWith("http")) { "URL must be a valid HTTP/HTTPS URL: $url" }
    }

    abstract fun authorize(request: HttpRequest, apiProvider: APIProvider)

    fun post(
        request: HttpPost,
        requestID: String = UUID.randomUUID().toString(),
        logStreams: MutableList<java.io.BufferedOutputStream> = this.logStreams
    ): String = try {
        withClient<String> {
            log(
                level = Level.DEBUG,
                msg = String.format(
                    "POST %s\nID:%s\nPrefix:\n\t%s\n%s\n",
                    request.uri,
                    requestID,

                    formatEntityForLogging(request.entity),
                    captureCallerStack().lineSequence().map {
                        when {
                            it.isBlank() -> {
                                when {
                                    it.length < "\t".length -> "\t"
                                    else -> it
                                }
                            }

                            else -> "\t" + it
                        }
                    }.joinToString("\n")
                ),
                logStreams
            )
            innerPost(it, request) ?: throw IOException("Empty response from POST request to ${request.uri}")
        }
    } catch (e: Exception) {
        log.error("Failed to execute POST request to ${request.uri}", e)
        throw e
    }

    protected open fun innerPost(
        client: CloseableHttpClient,
        request: HttpPost
    ): String? {
        val response = client.execute(request)
        val entity = response.entity
        return if (entity != null) {
            val responseBody = EntityUtils.toString(entity)
            if (responseBody.isBlank()) {
                throw IOException("Empty response body")
            }
            responseBody
        } else {
            throw IOException("Empty response entity")
        }
    }

    private fun formatEntityForLogging(entity: org.apache.hc.core5.http.HttpEntity?): String {
        return try {
            EntityUtils.toString(entity)?.lineSequence()?.map {
                when {
                    it.isBlank() -> if (it.length < "\t".length) "\t" else it
                    else -> "\t$it"
                }
            }?.joinToString("\n").orEmpty()
        } catch (e: Exception) {
            log.warn("Failed to format entity for logging", e)
            "[Unable to format entity for logging]"
        }
    }


    override fun onUsage(model: LLMModel, tokens: Usage) {
        log.debug("Usage recorded for session: {}, user: {}, model: {}, tokens: {}", session, user, model, tokens)
        budget?.let { currentBudget ->
            val cost = tokens.cost ?: 0.0
            budget = (currentBudget.toDouble() - cost).coerceAtLeast(0.0)
            if (budget!!.toDouble() <= 0.0) {
                log.warn("Budget exhausted for session: $session, user: $user")
            }
        }
    }

    inner class ChildClient() : ChatClientBase(
        logLevel = Level.INFO,
        workPool = workPool,
        logStreams = this@ChatClientBase.logStreams.toTypedArray().toMutableList(),
    ) {
        init {
            session = this@ChatClientBase.session
            user = this@ChatClientBase.user
        }

        override fun authorize(
            request: HttpRequest,
            apiProvider: APIProvider
        ) {
            this@ChatClientBase.authorize(request, apiProvider)
        }

        override fun chat(
            chatRequest: ApiModel.ChatRequest,
            model: LLMModel,
            logStreams: MutableList<BufferedOutputStream>
        ): ApiModel.ChatResponse {
            return this@ChatClientBase.chat(chatRequest, model, logStreams)
        }

        override fun onUsage(model: LLMModel, tokens: Usage) {
            this@ChatClientBase.onUsage(model, tokens)
            super.onUsage(model, tokens)
        }
    }

    override fun getChildClient(): ChildClient = ChildClient()

    companion object {
        val log = LoggerFactory.getLogger(ChatClientBase::class.java)
    }
}