package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.models.LLMModel
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.HttpEntity
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService

abstract class SingleProviderChatClient(
    protected val provider: APIProvider,
    val apiKey: String,
    val apiBase: String = provider.base,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : ChatClientBase(
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
) {
    protected fun get(url: String) = withClient { client ->
        client.execute(HttpGet(url).let {
            provider.authorize(
                request = it,
                key = apiKey,
                apiBase = apiBase
            )
            it
        }).use { response ->
            val responseBody = response.entity?.content?.bufferedReader()?.readText() ?: ""
            log(Level.DEBUG, "GET $url -> ${response.code}: $responseBody", logStreams)
            responseBody
        }
    }
}

abstract class ChatClientBase(
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : HttpClientManager(
    logLevel = logLevel, logStreams = logStreams, workPool = workPool, scheduledPool = scheduledPool
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
        logStreams: MutableList<BufferedOutputStream> = this.logStreams
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
        logStreams: MutableList<BufferedOutputStream> = this.logStreams
    ): String = try {
        withClient { client ->
            log(
                level = Level.DEBUG,
                msg = String.format(
                    "POST %s\nID:%s\nPrefix:\n\t%s\n%s\n",
                    request.uri,
                    requestID,
                    request.entity.formatEntityForLogging(),
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
            val response = innerPost(client, request) ?: throw IOException("Empty response from POST request to ${request.uri}")
            log(
                level = Level.DEBUG,
                msg = String.format(
                    "POST %s\nID:%s\nResponse:\n\t%s",
                    request.uri,
                    requestID,
                    response.lineSequence().map {
                        when {
                            it.isBlank() -> if (it.length < "\t".length) "\t" else it
                            else -> "\t$it"
                        }
                    }.joinToString("\n")
                ),
                logStreams
            )
            response
        }
    } catch (e: Exception) {
        log(
            Level.ERROR,
            "Error during POST request to ${request.uri}\nID:$requestID\nRequest Entity:\n${request.entity.formatEntityForLogging()}",
            logStreams
        )
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

    override fun onUsage(
        model: LLMModel,
        tokens: Usage,
        logStreams: MutableList<BufferedOutputStream>
    ) {
        log(
            Level.INFO,
            "Usage recorded for session: %s, user: %s, model: %s, tokens: %s".format(session, user, model, tokens),
            logStreams
        )
        budget?.let { currentBudget ->
            val cost = tokens.cost ?: 0.0
            budget = (currentBudget.toDouble() - cost).coerceAtLeast(0.0)
            if (budget!!.toDouble() <= 0.0) {
                log(Level.WARN, "Budget exhausted for session: $session, user: $user", logStreams)
            } else {
                log(Level.INFO, "Remaining budget for session: $session, user: $user is $budget", logStreams)
            }
        }
        super.onUsage(model, tokens, logStreams)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatClientBase::class.java)
    }
}

fun HttpEntity?.formatEntityForLogging() = try {
    EntityUtils.toString(this)?.lineSequence()?.map {
        when {
            it.isBlank() -> if (it.length < "\t".length) "\t" else it
            else -> "\t$it"
        }
    }?.joinToString("\n").orEmpty()
} catch (e: Exception) {
    "[Unable to format entity for logging]: $e"
}