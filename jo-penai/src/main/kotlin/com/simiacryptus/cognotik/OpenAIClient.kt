package com.simiacryptus.cognotik

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.cognotik.exceptions.ErrorUtil.allowedCharset
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.exceptions.ModerationException
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.StringUtil
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.Logger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService

open class OpenAIClient(
    protected var key: String,
    protected val apiBase: String,
    logLevel: Level = Level.TRACE,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    workPool: ExecutorService,
    scheduledPool: ListeningScheduledExecutorService,
) : HttpClientManager(
    logLevel = logLevel,
    logStreams = logStreams,
    workPool = workPool,
    scheduledPool = scheduledPool
) {

    var user: Any? = null
    var session: Any? = null
    open val provider = APIProvider.OpenAI

    open fun onUsage(model: AIModel?, tokens: Usage) {
    }

    @Throws(IOException::class, InterruptedException::class)
    protected fun post(url: String, json: String, apiProvider: APIProvider): String {
        val request = HttpPost(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        log.info("Sending POST request to URL: $url with payload: $json")
        apiProvider.authorize(request, key, apiBase)
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        return post(request)
    }

    protected fun post(request: HttpPost): String = withClient { EntityUtils.toString(it.execute(request).entity) }

    @Throws(IOException::class)
    protected operator fun get(url: String?, apiProvider: APIProvider): String = withClient {
        val request = HttpGet(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        log.debug("Sending GET request to URL: $url")
        apiProvider.authorize(request, key, apiBase)
        EntityUtils.toString(it.execute(request).entity)
    }

    open fun complete(
        request: CompletionRequest, model: LLMModel
    ): CompletionResponse = withReliability {
        withPerformanceLogging {
            if (request.suffix == null) {
                log(
                    msg = String.format(
                        "Text Completion Request\nPrefix:\n\t%s\n", request.prompt.lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    )
                )
                log.debug("Text Completion Request with Prefix: ${request.prompt}")
            } else {
                log(
                    msg = String.format(
                        "Text Completion Request\nPrefix:\n\t%s\nSuffix:\n\t%s\n",
                        request.prompt.lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n"),
                        request.suffix.lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    )
                )
                log.debug("Text Completion Request with Prefix: ${request.prompt} and Suffix: ${request.suffix}")
            }
            val result = post(
                "$provider/engines/${model.modelName}/completions",
                StringUtil.restrictCharacterSet(
                    JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(request),
                    allowedCharset
                ),
                provider
            )
            checkError(result)
            val response = JsonUtil.objectMapper().readValue(
                result, CompletionResponse::class.java
            )
            if (response.usage != null) {
                onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
            }
            val completionResult =
                StringUtil.stripPrefix(
                    response.firstChoice.orElse("").toString().trim { it <= ' ' },
                    request.prompt.trim { it <= ' ' })
            log(
                msg = String.format(
                    "Text Completion:\n\t%s", completionResult.toString().lineSequence()
                        .map {
                            when {
                                it.isBlank() -> {
                                    when {
                                        it.length < "  ".length -> "  "
                                        else -> it
                                    }
                                }

                                else -> "  " + it
                            }
                        }
                        .joinToString("\n")
                )
            )
            log.debug("Text Completion Result: $completionResult")
            response
        }
    }

    open fun createSpeech(request: SpeechRequest): ByteArray? = withReliability {
        withPerformanceLogging {
            val httpRequest = HttpPost("${apiBase}/audio/speech")
            provider.authorize(httpRequest, key, apiBase)
            httpRequest.addHeader("Accept", "application/json")
            httpRequest.addHeader("Content-Type", "application/json")
            httpRequest.entity =
                StringEntity(JsonUtil.objectMapper().writeValueAsString(request), Charsets.UTF_8, false)
            val response = withClient { it.execute(httpRequest).entity }
            val contentType = response.contentType
            val bytes = response.content.readAllBytes()
            log.info("Speech creation response received with content type: $contentType")
            if (contentType != null && contentType.startsWith("text") || contentType.startsWith("application/json")) {
                checkError(bytes.toString(Charsets.UTF_8))
                null
            } else {
//                val model = AudioModels.entries.find { it.modelName.equals(request.model, true) }
//                onUsage(
//                    model, Usage(
//                        prompt_tokens = request.input.length.toLong(),
//                        cost = model?.pricing(request.input.length)
//                    )
//                )
                bytes
            }
        }
    }

    open fun moderate(text: String) = withReliability {
        when {
            provider == APIProvider.Groq -> return@withReliability
            provider == APIProvider.ModelsLab -> return@withReliability
        }
        withPerformanceLogging {
            val body: String = try {
                JsonUtil.objectMapper().writeValueAsString(
                    mapOf(
                        "input" to StringUtil.restrictCharacterSet(text, allowedCharset)
                    )
                )
            } catch (e: JsonProcessingException) {
                throw RuntimeException(e)
            }
            val result: String = try {
                this.post("${apiBase}/moderations", body, provider)
            } catch (e: IOException) {
                log.warn("IOException during moderation request", e)
                throw RuntimeException(e)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            val jsonObject = Gson().fromJson(
                result, JsonObject::class.java
            ) ?: return@withPerformanceLogging
            if (jsonObject.has("error")) {
                val errorObject = jsonObject.getAsJsonObject("error")
                throw RuntimeException(IOException(errorObject["message"].asString))
            }
            val moderationResult = jsonObject.getAsJsonArray("results")[0].asJsonObject
            if (moderationResult["flagged"].asBoolean) {
                val categoriesObj = moderationResult["categories"].asJsonObject
                throw RuntimeException(
                    ModerationException(
                        "Moderation flagged this request due to " + categoriesObj.keySet()
                            .stream().filter { c: String? ->
                                categoriesObj[c].asBoolean
                            }.reduce { a: String, b: String -> "$a, $b" }.orElse("???")
                    )
                )
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OpenAIClient::class.java)
    }
}
