package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.genai.Client
import com.google.genai.types.*
import com.google.genai.types.Content
import com.google.genai.types.Content.builder
import com.google.genai.types.Part
import com.google.genai.types.Part.fromText
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.LoggerFactory
import okio.ByteString.Companion.decodeBase64
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.jvm.optionals.getOrNull

/**
 * Gemini Chat Client using the official Google Gen AI Java SDK
 */
class GeminiSdkChatClient(
    apiKey: String,
    val apiBase: String = APIProvider.Gemini.base,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService,
    private val useVertexAI: Boolean = false,
    private val project: String? = null,
    private val location: String? = null,
) : ChatClientBase(
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
), ChatClientInterface {

    private val client: Client = buildClient(apiKey, useVertexAI, project, location)

    private fun buildClient(
        apiKey: String,
        useVertexAI: Boolean,
        project: String?,
        location: String?
    ): Client {
        val builder = Client.builder()

        if (useVertexAI) {
            builder.vertexAI(true)
            if (project != null && location != null) {
                builder.project(project).location(location)
            } else {
                builder.apiKey(apiKey)
            }
        } else {
            builder.apiKey(apiKey)
        }

        return builder.build()
    }

    override fun getModels(): List<ChatModel>? {
        // Check cache first
        modelsCache[apiBase]?.let { return it }
        val models = try {
            client.models.list(
                ListModelsConfig.builder().build()
            ).mapNotNull {
                val model = it.name().get()
                val baseModelId = model.removePrefix("models/")
                GeminiModels.values.values.find {
                    it.modelName == baseModelId || it.modelName == model
                } ?: run {
                    // If not found in predefined models, create a dynamic one
                    log.debug("Creating basic ChatModel for unknown Gemini model: ${baseModelId}")
                    ChatModel(
                        name = model,
                        modelName = baseModelId,
                        maxTotalTokens = it.inputTokenLimit().get() + it.outputTokenLimit().get(),
                        maxOutTokens = it.outputTokenLimit().get(),
                        provider = APIProvider.Gemini,
                        inputTokenPricePerK = 0.0, // Default pricing - would need to be configured
                        outputTokenPricePerK = 0.0
                    )
                }
            }.toList()
        } catch (e: Exception) {
            log.warn("Failed to fetch models: ${e.message}")
            null
        }
        // Cache the result
        models?.let { modelsCache[apiBase] = it }
        return models
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        val requestID = UUID.randomUUID().toString()
        try {
            val config = buildGenerateContentConfig(chatRequest)
            val contents: List<Content> = convertToGeminiContents(chatRequest.messages)
            log(
                "<details>\n<summary>Sending request to Gemini SDK for model: ${model.modelName} (${requestID})</summary>\n${
                    contents.joinToString("\n\n") {
                        it.toMarkdown()
                    }.indent("  ")
                }\n</details>",
                logStreams
            )
            val response = client.models.generateContent(model.modelName, contents, config)
            // Log response
            log(
                "<details>\n<summary>Gemini SDK Response (${requestID})</summary>\n${
                    response.candidates().orElse(emptyList()).joinToString("\n\n") { candidate ->
                        candidate.content().orElse(null)?.toMarkdown() ?: "\n\n**No content**\n\n"
                    }.indent("  ")
                }\n</details>",
                logStreams
            )
            val chatResponse = convertFromGeminiResponse(response)
            if (chatResponse.usage != null) {
                onUsage(
                    model,
                    chatResponse.usage.copy(cost = model.pricing(chatResponse.usage)),
                    logStreams = logStreams
                )
            }
            return chatResponse
        } catch (e: Exception) {
            log.error("Error during Gemini SDK chat request", e)
            throw e
        }
    }

    private fun Content.toMarkdown(): CharSequence {
        val sb = StringBuilder()
        this.parts().orElse(emptyList()).forEach { part ->
            part.text().getOrNull()?.let { text ->
                sb.append(text).append("\n")
            }
            part.inlineData().getOrNull()?.let { inlineData ->
                when (inlineData.mimeType().getOrNull()) {
                    "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
                        val imageBytes = inlineData.data().getOrNull()
                        if (imageBytes != null) {
                            /*Resize to no more than 256 px wide*/
                            val maxWidth = 256
                            var sourceImage = javax.imageio.ImageIO.read(imageBytes.inputStream())
                            if (sourceImage.width > maxWidth) {
                                val aspectRatio = sourceImage.height.toDouble() / sourceImage.width.toDouble()
                                val newHeight = (maxWidth * aspectRatio).toInt()
                                val resizedImage = java.awt.image.BufferedImage(maxWidth, newHeight, sourceImage.type)
                                val g2d = resizedImage.createGraphics()
                                g2d.drawImage(sourceImage, 0, 0, maxWidth, newHeight, null)
                                g2d.dispose()
                                sourceImage = resizedImage
                            }
                            val logBytes = java.io.ByteArrayOutputStream()
                            javax.imageio.ImageIO.write(sourceImage, inlineData.mimeType().getOrNull()!!.substringAfter("image/"), logBytes)
                            val imageBytes = logBytes.toByteArray()
                            sb.append("<img src=\"data:${inlineData.mimeType().getOrNull()};base64,${imageBytes.base64()}\" alt=\"image\" width=\"${sourceImage.width}\" height=\"${sourceImage.height}\" />\n")
                        }
                    }

                    else -> {
                        sb.append("`[Unsupported inline data of type ${inlineData.mimeType().getOrNull()}]`\n")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun buildGenerateContentConfig(chatRequest: ModelSchema.ChatRequest): GenerateContentConfig? {
        val builder = GenerateContentConfig.builder()
        chatRequest.temperature.let { builder.temperature(it.toFloat()) }
        chatRequest.max_tokens?.let { builder.maxOutputTokens(it) }
        val systemMessages = chatRequest.messages.filter { it.role == ModelSchema.Role.system }
        if (systemMessages.isNotEmpty()) {
            builder.systemInstruction(systemMessages.reduceOrNull { acc, message ->
                ModelSchema.ChatMessage(
                    role = ModelSchema.Role.system,
                    content = (acc.content ?: emptyList()) + (message.content ?: emptyList())
                )
            }?.let { reduceOrNull ->
                builder()
                    .role("system")
                    .parts(reduceOrNull.content?.map { it.part() } ?: listOf(fromText("")))
                    .build()
            })
        }
        return builder.build()
    }

    private fun convertToGeminiContents(messages: List<ModelSchema.ChatMessage>) = messages
        .filter { it.role != ModelSchema.Role.system }
        .mapNotNull { it.toContent() }

    private fun ModelSchema.ChatMessage.toContent() = builder()
        .role(
            when (this.role) {
                ModelSchema.Role.system -> "user" // Gemini does not have a system role, treat as user
                ModelSchema.Role.user -> "user"
                ModelSchema.Role.assistant -> "model"
                else -> "user"
            }
        )
        .parts(content?.flatMap { it.parts() } ?: listOf(fromText("")))
        .build()

    fun ModelSchema.ContentPart.part(): Part? = when {
        image_url != null -> {
            // Handle image URLs
            val imageUrl = image_url
            if (imageUrl?.startsWith("data:") == true) {
                // Base64 encoded image
                val parts = imageUrl.split(",")
                val mimeType = parts[0].substringAfter("data:").substringBefore(";")
                val data = parts[1]
                Part.fromBytes(data.decodeBase64()?.toByteArray(), mimeType)
            } else if (imageUrl?.startsWith("gs://") == true) {
                // GCS URI
                Part.fromUri(imageUrl, "image/jpeg")
            } else {
                // Regular URL - convert to text description
                Part.fromUri(imageUrl, "image/jpeg")
            }
        }

        text != null -> fromText(text)

        else -> fromText("")
    }
    fun ModelSchema.ContentPart.parts(): List<Part> = when {
        image_url != null && text != null -> listOfNotNull(
            copy(text = null).part(),
            copy(image_url = null).part()
        )
        else -> listOfNotNull(
            this.part()
        )
    }

    private fun convertFromGeminiResponse(response: GenerateContentResponse): ModelSchema.ChatResponse {
        val choices = response.candidates().orElse(emptyList()).mapIndexed { index, candidate ->
            val content = candidate.content().orElse(null)
            val text = content?.parts()?.orElse(emptyList())
                ?.mapNotNull { it.text().getOrNull() }?.joinToString("\n")?.let {
                    when (it) {
                        "" -> null
                        else -> it
                    }
                }

            val chatMessageResponse = ModelSchema.ChatMessageResponse(
                content = text,
            )
            content?.parts()?.orElse(emptyList())?.forEach { part ->
                part.inlineData()?.getOrNull()?.apply {
                    when (mimeType().getOrNull()) {
                        "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
                            chatMessageResponse.image_data = this.data().getOrNull()
                            chatMessageResponse.image_mime_type = this.mimeType().getOrNull()
                        }
                    }
                }
            }
            ModelSchema.ChatChoice(
                message = chatMessageResponse,
                index = index,
                finish_reason = candidate.finishReason().orElse(null)?.toString()
            )
        }

        val usage = response.usageMetadata().orElse(null)?.let { metadata ->
            ModelSchema.Usage(
                prompt_tokens = metadata.promptTokenCount().orElse(0).toLong(),
                completion_tokens = metadata.candidatesTokenCount().orElse(0).toLong(),
                total_tokens = metadata.totalTokenCount().orElse(0).toLong()
            )
        }

        return ModelSchema.ChatResponse(
            choices = choices,
            usage = usage
        )
    }

    override fun authorize(request: HttpRequest, apiProvider: APIProvider) {
        TODO("Not yet implemented")
    }

    companion object {
        private val log = LoggerFactory.getLogger(GeminiSdkChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()
    }
}


private fun ByteArray.base64() = java.util.Base64.getEncoder().encodeToString(this)
private fun String.base64() = java.util.Base64.getDecoder().decode(this)