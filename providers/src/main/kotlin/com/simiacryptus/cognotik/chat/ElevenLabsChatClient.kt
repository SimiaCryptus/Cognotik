package com.simiacryptus.cognotik.chat

    import com.fasterxml.jackson.databind.ObjectMapper
    import com.fasterxml.jackson.module.kotlin.registerKotlinModule
    import com.google.common.util.concurrent.ListeningScheduledExecutorService
    import com.simiacryptus.cognotik.CoreProviders
    import com.simiacryptus.cognotik.chat.model.ChatModel
    import com.simiacryptus.cognotik.models.APIProvider
    import com.simiacryptus.cognotik.models.LLMModel
    import com.simiacryptus.cognotik.models.ModelSchema
    import com.simiacryptus.cognotik.util.LoggerFactory
    import com.simiacryptus.cognotik.util.SecureString
    import org.apache.hc.client5.http.classic.methods.HttpGet
    import org.apache.hc.client5.http.classic.methods.HttpPost
    import org.apache.hc.client5.http.impl.classic.HttpClients
    import org.apache.hc.core5.http.ContentType
    import org.apache.hc.core5.http.HttpRequest
    import org.apache.hc.core5.http.io.entity.EntityUtils
    import org.apache.hc.core5.http.io.entity.StringEntity
    import org.slf4j.event.Level
    import java.io.BufferedOutputStream
    import java.net.URLEncoder
    import java.nio.charset.StandardCharsets
    import java.util.UUID
    import java.util.concurrent.ExecutorService

    /**
     * ElevenLabs chat client. ElevenLabs is primarily a text-to-speech
     * provider, so this client implements TTS by treating "chat" requests
     * as TTS requests: the user's text content is converted to speech using
     * a configured voice, and the resulting audio is returned as the
     * assistant's response.
     */
    class ElevenLabsChatClient(
        apiKey: SecureString,
        apiBase: String,
        workPool: ExecutorService,
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream>,
        scheduledPool: ListeningScheduledExecutorService
    ) : ChatClientBase(
        provider = CoreProviders.ElevenLabs,
        apiKey = apiKey,
        apiBase = apiBase,
        workPool = workPool,
        logLevel = logLevel,
        logStreams = logStreams,
        scheduledPool = scheduledPool
    ) {

        private val mapper = ObjectMapper().registerKotlinModule()

        /**
         * Default voice ID. ElevenLabs has many voices available; this is
         * "Rachel", a common default. Users should override via the
         * chatRequest.audio map with key "voice".
         */
        private val defaultVoiceId = "21m00Tcm4TlvDq8ikWAM"

        override fun getModels(): List<ChatModel>? {
            return com.simiacryptus.cognotik.chat.model.ElevenLabsModels.values.values.toList()
        }

        override fun chat(
            chatRequest: ModelSchema.ChatRequest,
            model: ChatModel,
            logStreams: MutableList<BufferedOutputStream>,
            usageHandler: ((model: LLMModel, usage: ModelSchema.Usage) -> Unit)?
        ): ModelSchema.ChatResponse {
            val requestId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()
            log.debug("Starting ElevenLabs TTS request {} for model {}", requestId, model.modelId)

            // Extract text from messages (concatenate all user/assistant text content)
            val textToSpeak = chatRequest.messages
                .filter { it.role == ModelSchema.Role.user || it.role == ModelSchema.Role.assistant }
                .flatMap { it.content ?: emptyList() }
                .mapNotNull { it.text }
                .joinToString("\n")
                .trim()

            require(textToSpeak.isNotEmpty()) {
                "ElevenLabs TTS requires non-empty text content in messages"
            }

            // Get voice from audio config or use default
            val voiceId = chatRequest.audio?.get("voice") ?: defaultVoiceId
            log.debug("Request {}: using voice='{}', text length={} chars", requestId, voiceId, textToSpeak.length)

            // Build request body
            val requestBody = mapOf(
                "text" to textToSpeak,
                "model_id" to model.modelId,
                "voice_settings" to mapOf(
                    "stability" to 0.5,
                    "similarity_boost" to 0.75
                )
            )
            val jsonBody = mapper.writeValueAsString(requestBody)

            log(
                "\n<details>\n<summary>Sending request to ElevenLabs TTS for model: ${model.modelId} (${requestId})</summary>\n\n```json\n$jsonBody\n```\n</details>",
                logStreams
            )

            val url = "$apiBase/v1/text-to-speech/${URLEncoder.encode(voiceId, StandardCharsets.UTF_8)}"

            val audioBytes: ByteArray = HttpClients.createDefault().use { httpClient ->
                val httpPost = HttpPost(url)
                httpPost.addHeader("xi-api-key", apiKey.decrypt)
                httpPost.addHeader("Accept", "audio/mpeg")
                httpPost.entity = StringEntity(jsonBody, ContentType.APPLICATION_JSON)

                httpClient.execute(httpPost) { response ->
                    val status = response.code
                    if (status !in 200..299) {
                        val errorBody = response.entity?.let { EntityUtils.toString(it) } ?: ""
                        log.error("ElevenLabs API error: status={}, body={}", status, errorBody)
                        throw RuntimeException("ElevenLabs API returned status $status: $errorBody")
                    }
                    response.entity?.content?.use { it.readAllBytes() }
                        ?: throw RuntimeException("ElevenLabs API returned empty response body")
                }
            }

            log.info(
                "Request {}: received {} bytes of audio in {} ms",
                requestId, audioBytes.size, System.currentTimeMillis() - startTime
            )

            val response = ModelSchema.ChatMessageResponse(
                content = null,
            ).apply {
                audio_data = audioBytes
                audio_mime_type = "audio/mpeg"
                audio_format = "mp3"
            }

            log(
                "\n<details>\n<summary>ElevenLabs TTS Response (${requestId})</summary>\n\nGenerated ${audioBytes.size} bytes of audio (mp3)\n</details>",
                logStreams
            )

            // Estimate token usage based on character count (ElevenLabs bills by character)
            val charCount = textToSpeak.length.toLong()
            val usage = ModelSchema.Usage(
                prompt_tokens = charCount,
                completion_tokens = 0L,
                total_tokens = charCount
            )

            try {
                usageHandler?.invoke(model, usage.copy(cost = model.pricing(usage)),)
            } catch (e: Exception) {
                log.warn("Request {}: failed to record usage: {}", requestId, e.message)
            }

            return ModelSchema.ChatResponse(
                choices = listOf(
                    ModelSchema.ChatChoice(
                        message = response,
                        index = 0,
                        finish_reason = "stop"
                    )
                ),
                usage = usage
            )
        }

        override fun authorize(request: HttpRequest) {
            request.addHeader("xi-api-key", apiKey.decrypt)
        }

        companion object {
            private val log = LoggerFactory.getLogger(ElevenLabsChatClient::class.java)
        }
    }