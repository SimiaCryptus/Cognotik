package com.simiacryptus.cognotik.embedding

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.ApiModel.Usage
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService

interface EmbeddingClientInterface {

    fun createEmbedding(
        request: ApiModel.EmbeddingRequest,
        model: EmbeddingModel
    ): ApiModel.EmbeddingResponse

}

abstract class SingleProviderEmbeddingClient(
    protected val provider: APIProvider,
    val apiKey: String,
    val apiBase: String = provider.base!!,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : EmbeddingClientBase(
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
)

abstract class EmbeddingClientBase(
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : HttpClientManager(
    logLevel = logLevel, logStreams = logStreams, workPool = workPool, scheduledPool = scheduledPool
), EmbeddingClientInterface {

    var session: Any? = null
    var user: Any? = null
    var budget: Number? = null

    @Throws(IOException::class, InterruptedException::class)
    fun post(
        url: String,
        json: String,
        apiProvider: APIProvider,
        requestID: String = UUID.randomUUID().toString()
    ): String {
        validatePostRequest(url, json)
        val request = HttpPost(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        authorize(request, apiProvider)
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        return post(request, requestID = requestID)
    }

    private fun validatePostRequest(url: String, json: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(json.isNotBlank()) { "JSON payload cannot be blank" }
        require(url.startsWith("http")) { "URL must be a valid HTTP/HTTPS URL: $url" }
    }

    abstract fun authorize(request: HttpRequest, apiProvider: APIProvider)

    fun post(request: HttpPost, requestID: String = UUID.randomUUID().toString()): String = try {
        withClient<String> {
            log(
                level = Level.DEBUG,
                msg = String.format(
                    "POST %s\nID:%s\nPrefix:\n\t%s\n%s\n",
                    request.uri,
                    requestID,
                    formatEntityForLogging(request.entity),
                    captureCallerStack().lineSequence().map { str ->
                        when {
                            str.isBlank() -> {
                                when {
                                    str.length < "\t".length -> "\t"
                                    else -> str
                                }
                            }

                            else -> "\t" + str
                        }
                    }.joinToString("\n")
                )
            )
            val response = it.execute(request)
            val entity = response.entity
            if (entity != null) {
                val responseBody = EntityUtils.toString(entity)
                if (responseBody.isBlank()) {
                    throw IOException("Empty response body")
                }
                responseBody
            } else {
                throw IOException("Empty response entity")
            }
        }
    } catch (e: Exception) {
        log.error("Failed to execute POST request to ${request.uri}", e)
        throw e
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

    fun onUsage(model: EmbeddingModel, tokens: Usage) {
        log.debug("Usage recorded for session: {}, user: {}, model: {}, tokens: {}", session, user, model, tokens)
    }

    inner class ChildClient() : EmbeddingClientBase(
        logLevel = Level.INFO,
        workPool = workPool,
        logStreams = logStreams,
        scheduledPool = scheduledPool
    ) {
        init {
            session = this@EmbeddingClientBase.session
            user = this@EmbeddingClientBase.user
        }

        override fun log(level: Level, msg: String, logStreams: MutableList<BufferedOutputStream>) {
            super.log(level, msg, logStreams)
            this@EmbeddingClientBase.log(level, msg)
        }

        override fun authorize(
            request: HttpRequest,
            apiProvider: APIProvider
        ) {
            this@EmbeddingClientBase.authorize(request, apiProvider)
        }

        override fun createEmbedding(
            request: ApiModel.EmbeddingRequest,
            model: EmbeddingModel
        ): ApiModel.EmbeddingResponse {
            return this@EmbeddingClientBase.createEmbedding(request, model)
        }

    }

    fun getChildClient(): ChildClient = ChildClient()

    companion object {
        val log = LoggerFactory.getLogger(EmbeddingClientBase::class.java)
    }
}