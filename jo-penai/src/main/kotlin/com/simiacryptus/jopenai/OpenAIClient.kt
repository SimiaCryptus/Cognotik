package com.simiacryptus.jopenai

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.jopenai.exceptions.ModerationException
import com.simiacryptus.jopenai.models.*
import com.simiacryptus.jopenai.models.ApiModel.*
import com.simiacryptus.jopenai.util.ClientUtil.allowedCharset
import com.simiacryptus.jopenai.util.ClientUtil.checkError
import com.simiacryptus.jopenai.util.ClientUtil.defaultApiProvider
import com.simiacryptus.jopenai.util.ClientUtil.keyMap
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.StringUtil
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.mime.FileBody
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.concurrent.ExecutorService
import javax.imageio.ImageIO

open class OpenAIClient(
    protected var key: Map<APIProvider, String> = keyMap.mapKeys { APIProvider.valueOf(it.key) },
    protected val apiBase: Map<APIProvider, String> = APIProvider.values().associate { it to (it.base ?: "") },
    logLevel: Level = Level.TRACE,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    workPool: ExecutorService
) : HttpClientManager(
    logLevel = logLevel,
    logStreams = logStreams,
    workPool = workPool
) {
    private val log: Logger = LoggerFactory.getLogger(OpenAIClient::class.java).apply {
        info("OpenAIClient initialized with log level: $logLevel")
    }

    var user: Any? = null
    var session: Any? = null

    open fun onUsage(model: OpenAIModel?, tokens: Usage) {
    }

    @Throws(IOException::class, InterruptedException::class)
    protected fun post(url: String, json: String, apiProvider: APIProvider): String {
        val request = HttpPost(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        log.info("Sending POST request to URL: $url with payload: $json")
        authorize(request, apiProvider)
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        return post(request)
        log.info("Executed POST request: ${request.uri}")
    }

    protected fun post(request: HttpPost): String = withClient { EntityUtils.toString(it.execute(request).entity) }

    @Throws(IOException::class)
    protected open fun authorize(request: HttpRequest, apiProvider: APIProvider) {
        when (apiProvider) {
            APIProvider.Google -> {
//        request.addHeader("X-goog-api-key", "${key.get(apiProvider)}")
            }

            APIProvider.Anthropic -> {
                request.addHeader("x-api-key", "${key.get(apiProvider)}")
                request.addHeader("anthropic-version", "2023-06-01")
            }

            else -> request.addHeader("Authorization", "Bearer ${key.get(apiProvider)}")
        }
    }

    @Throws(IOException::class)
    protected operator fun get(url: String?, apiProvider: APIProvider): String = withClient {
        val request = HttpGet(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        log.debug("Sending GET request to URL: $url")
        authorize(request, apiProvider)
        EntityUtils.toString(it.execute(request).entity)
    }

    open fun complete(
        request: CompletionRequest, model: TextModel
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
                "${apiBase[defaultApiProvider]}/engines/${model.modelName}/completions",
                StringUtil.restrictCharacterSet(
                    JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(request),
                    allowedCharset
                ),
                defaultApiProvider
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

    open fun transcription(wavAudio: ByteArray, prompt: String = "", audioModel: AudioModels): String =
        withReliability {
            withPerformanceLogging {
                val url = "${apiBase[defaultApiProvider]}/audio/transcriptions"
                val request = HttpPost(url)
                request.addHeader("Accept", "application/json")
                authorize(request, defaultApiProvider)
                val entity = MultipartEntityBuilder.create()
                entity.setMode(HttpMultipartMode.EXTENDED)
                entity.addBinaryBody("file", wavAudio, ContentType.create("audio/x-wav"), "audio.wav")
                entity.addTextBody("model", audioModel.modelName)
                entity.addTextBody("response_format", "json")
                if (prompt.isNotEmpty()) entity.addTextBody("prompt", prompt)
                request.entity = entity.build()
                val response = post(request)
                log.info("Transcription response received")
                val jsonObject = Gson().fromJson(response, JsonObject::class.java)
                if (jsonObject.has("error")) {
                    val errorObject = jsonObject.getAsJsonObject("error")
                    throw RuntimeException(IOException(errorObject["message"].asString))
                }
                try {
                    val result = JsonUtil.objectMapper().readValue(response, TranscriptionResult::class.java)
                    result.text ?: ""
                } catch (e: Exception) {
                    jsonObject.get("text").asString ?: ""
                }
            }
        }

    open fun createSpeech(request: ApiModel.SpeechRequest): ByteArray? = withReliability {
        withPerformanceLogging {
            val httpRequest = HttpPost("${apiBase[defaultApiProvider]}/audio/speech")
            authorize(httpRequest, defaultApiProvider)
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
                val model = AudioModels.values().find { it.modelName.equals(request.model, true) }
                onUsage(
                    model, Usage(
                        prompt_tokens = request.input.length.toLong(),
                        cost = model?.pricing(request.input.length)
                    )
                )
                bytes
            }
        }
    }

    open fun render(prompt: String = "", resolution: Int = 1024, count: Int = 1): List<BufferedImage> =
        withReliability {
            withPerformanceLogging {
                val url = "${apiBase[defaultApiProvider]}/images/generations"
                val request = HttpPost(url)
                request.addHeader("Accept", "application/json")
                request.addHeader("Content-Type", "application/json")
                authorize(request, defaultApiProvider)
                val jsonObject = JsonObject()
                jsonObject.addProperty("prompt", prompt)
                jsonObject.addProperty("n", count)
                jsonObject.addProperty("size", "${resolution}x$resolution")
                request.entity = StringEntity(jsonObject.toString(), Charsets.UTF_8, false)
                val response = post(request)
                log.info("Image generation response received")
                val jsonObject2 = Gson().fromJson(response, JsonObject::class.java)
                if (jsonObject2.has("error")) {
                    val errorObject = jsonObject2.getAsJsonObject("error")
                    throw RuntimeException(IOException(errorObject["message"].asString))
                }
                val dataArray = jsonObject2.getAsJsonArray("data")
                val images = ArrayList<BufferedImage>()
                for (i in 0 until dataArray.size()) {
                    images.add(ImageIO.read(URL(dataArray[i].asJsonObject.get("url").asString)))
                }
                images
            }
        }

    data class Content(
        val role: String? = null,
        val parts: List<Part>? = null
    )

    data class Part(
        val inlineData: Blob? = null,
        val text: String? = null
    )

    data class Blob(
        val mimeType: String? = null,
        val data: String? = null
    )

    open fun moderate(text: String) = withReliability {
        when {
            defaultApiProvider == APIProvider.Groq -> return@withReliability
            defaultApiProvider == APIProvider.ModelsLab -> return@withReliability
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
                this.post("${apiBase[defaultApiProvider]}/moderations", body, defaultApiProvider)
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

    open fun edit(
        editRequest: EditRequest
    ): CompletionResponse = withReliability {
        withPerformanceLogging {
            if (editRequest.input == null) {
                log(
                    msg = String.format(
                        "Text Edit Request\nInstruction:\n\t%s\n", editRequest.instruction.lineSequence()
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
            } else {
                log(
                    msg = String.format(
                        "Text Edit Request\nInstruction:\n\t%s\nInput:\n\t%s\n",
                        editRequest.instruction.lineSequence()
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
                        editRequest.input.lineSequence()
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
            }
            val request: String = StringUtil.restrictCharacterSet(
                JsonUtil.objectMapper().writeValueAsString(editRequest), allowedCharset
            )
            val result = post("${apiBase[defaultApiProvider]}/edits", request, defaultApiProvider)
            log.info("Edit response received")
            checkError(result)
            val response = JsonUtil.objectMapper().readValue(
                result, CompletionResponse::class.java
            )
            if (response.usage != null) {
                val model = EditModels.values().values.find { it.modelName.equals(editRequest.model, true) }
                onUsage(
                    model, response.usage.copy(cost = model?.pricing(response.usage))
                )
            }
            log(
                msg = String.format(
                    "Edit Completion:\n\t%s",
                    response.firstChoice.orElse("").toString().trim { it <= ' ' }.toString().lineSequence()
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
            response
        }
    }

    open fun listModels(): ApiModel.ModelListResponse {
        val result = get("${apiBase[defaultApiProvider]}/models", defaultApiProvider)
        checkError(result)
        return JsonUtil.objectMapper().readValue(result, ModelListResponse::class.java)
    }

    open fun createEmbedding(
        request: EmbeddingRequest
    ): EmbeddingResponse {
        return withReliability {
            withPerformanceLogging {
                if (request.input is String) {
                    log(
                        msg = String.format(
                            "Embedding Creation Request\nModel:\n\t%s\nInput:\n\t%s\n",
                            request.model,
                            request.input.lineSequence()
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
                }
                val result = post(
                    "${apiBase[defaultApiProvider]}/embeddings", StringUtil.restrictCharacterSet(
                        JsonUtil.objectMapper().writeValueAsString(request), allowedCharset
                    ), defaultApiProvider
                )
                log.info("Embedding creation response received")
                checkError(result)
                val response = JsonUtil.objectMapper().readValue(
                    result, EmbeddingResponse::class.java
                )
                if (response.usage != null) {
                    val model = EmbeddingModels.values().values.find { it.modelName.equals(request.model, true) }
                    onUsage(
                        model,
                        response.usage.copy(cost = model?.pricing(response.usage))
                    )
                }
                response
            }
        }
    }

    open fun createImage(request: ImageGenerationRequest): ImageGenerationResponse = withReliability {
        withPerformanceLogging {
            val url = "${apiBase[defaultApiProvider]}/images/generations"
            val httpRequest = HttpPost(url)
            httpRequest.addHeader("Accept", "application/json")
            httpRequest.addHeader("Content-Type", "application/json")
            authorize(httpRequest, defaultApiProvider)

            val requestBody = Gson().toJson(request)
            httpRequest.entity = StringEntity(requestBody, Charsets.UTF_8, false)

            val response = post(httpRequest)
            checkError(response)
            log.info("Image creation response received")
            val model = ImageModels.values().find { it.modelName.equals(request.model, true) }
            val dims = request.size?.split("x")
            onUsage(
                model, Usage(
                    completion_tokens = 1, cost = model?.pricing(
                        width = dims?.get(0)?.toInt() ?: 0,
                        height = dims?.get(1)?.toInt() ?: 0
                    )
                )
            )

            JsonUtil.objectMapper().readValue(response, ImageGenerationResponse::class.java)
        }
    }

    open fun createImageEdit(request: ImageEditRequest): ImageEditResponse = withReliability {
        withPerformanceLogging {
            val url = "${apiBase[defaultApiProvider]}/images/edits"
            val httpRequest = HttpPost(url)
            httpRequest.addHeader("Accept", "application/json")
            authorize(httpRequest, defaultApiProvider)

            val entityBuilder = MultipartEntityBuilder.create()
            entityBuilder.addPart("image", FileBody(request.image))
            entityBuilder.addTextBody("prompt", request.prompt)
            request.mask?.let { entityBuilder.addPart("mask", FileBody(it)) }
            request.model?.let { entityBuilder.addTextBody("model", it) }
            request.n?.let { entityBuilder.addTextBody("n", it.toString()) }
            request.size?.let { entityBuilder.addTextBody("size", it) }
            request.responseFormat?.let { entityBuilder.addTextBody("response_format", it) }
            request.user?.let { entityBuilder.addTextBody("user", it) }

            httpRequest.entity = entityBuilder.build()
            val response = post(httpRequest)
            checkError(response)
            log.info("Image edit response received")

            JsonUtil.objectMapper().readValue(response, ImageEditResponse::class.java)
        }
    }

    open fun createImageVariation(request: ImageVariationRequest): ImageVariationResponse = withReliability {
        withPerformanceLogging {
            val url = "${apiBase[defaultApiProvider]}/images/variations"
            val httpRequest = HttpPost(url)
            httpRequest.addHeader("Accept", "application/json")
            authorize(httpRequest, defaultApiProvider)

            val entityBuilder = MultipartEntityBuilder.create()
            entityBuilder.addPart("image", FileBody(request.image))
            //request.model?.let { entityBuilder.addTextBody("model", it) }
            request.n?.let { entityBuilder.addTextBody("n", it.toString()) }
            request.responseFormat?.let { entityBuilder.addTextBody("response_format", it) }
            request.size?.let { entityBuilder.addTextBody("size", it) }
            request.user?.let { entityBuilder.addTextBody("user", it) }

            httpRequest.entity = entityBuilder.build()
            val response = post(httpRequest)
            checkError(response)
            log.info("Image variation response received")

            JsonUtil.objectMapper().readValue(response, ImageVariationResponse::class.java)
        }
    }

}