package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.genai.Client
import com.google.genai.types.*
import com.google.genai.types.Content.builder
import com.google.genai.types.Part.fromText
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SecureString
import okio.ByteString.Companion.decodeBase64
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.jvm.optionals.getOrNull

/**
 * Gemini Chat Client using the official Google Gen AI Java SDK
 */
class GeminiSdkChatClient(
  apiKey: SecureString,
  val apiBase: String = CoreProviders.Gemini.base,
  workPool: ExecutorService,
  logLevel: Level = Level.DEBUG,
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
    apiKey: SecureString,
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
        builder.apiKey(apiKey.decrypt)
      }
    } else {
      builder.apiKey(apiKey.decrypt)
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
          it.modelId == baseModelId || it.modelId == model
        } ?: run {
          // If not found in predefined models, create a dynamic one
          log.debug("Creating basic ChatModel for unknown Gemini model: ${baseModelId}")
          ChatModel(
            name = model,
            modelId = baseModelId,
            maxTotalTokens = it.inputTokenLimit().get() + it.outputTokenLimit().get(),
            maxOutTokens = it.outputTokenLimit().get(),
            provider = CoreProviders.Gemini,
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
      val supportsSystemInstruction = modelSupportsSystemInstruction(model.modelId)
      val config = buildGenerateContentConfig(chatRequest, supportsSystemInstruction)
      val contents: List<Content> = convertToGeminiContents(chatRequest.messages, supportsSystemInstruction)
      val sysInstruct = config?.systemInstruction()?.getOrNull()?.text()?.indent("  ")
      val contentStr = contents.joinToString("\n\n") { it.toMarkdown() }
      val toJson = toJson(config).indent("  ")
      log(
        "\n<details>\n<summary>Sending request to Gemini SDK for model: ${model.modelId} (${requestID})</summary>\n\n```json\n$toJson\n```\n\nSystem Prompt:\n```\n${sysInstruct}\n```\n\n$contentStr\n</details>",
        logStreams
      )
      val response = client.models.generateContent(model.modelId, contents, config)
      // Log response
      log(
        "\n<details>\n<summary>Gemini SDK Response (${requestID})</summary>\n\n${
          response.candidates().orElse(emptyList()).joinToString("\n\n") { candidate ->
            candidate.content().orElse(null)?.toMarkdown() ?: "\n\n**No content**\n\n"
          }
        }\n</details>",
        logStreams
      )
      val chatResponse = convertFromGeminiResponse(response)
      if (chatResponse.usage != null) {
        onUsage(
          model,
          chatResponse.usage?.copy(cost = model.pricing(chatResponse.usage!!))!!,
          logStreams = logStreams
        )
      } else {
        log("No usage data returned from Gemini SDK for request ${requestID}", logStreams)
      }
      return chatResponse
    } catch (e: Exception) {
      log.error("Error during Gemini SDK chat request", e)
      throw e
    }
  }
  /**
   * Some Gemini models (notably TTS and certain preview models) do not support
   * system instructions ("Developer instruction is not enabled for this model").
   * For these models, we must merge any system messages into the user content
   * rather than passing them via systemInstruction.
   */
  private fun modelSupportsSystemInstruction(modelId: String): Boolean {
    val lower = modelId.lowercase()
    // TTS models do not support system instructions
    if (lower.contains("-tts")) return false
    // Native audio / live preview models also reject system instructions
    if (lower.contains("native-audio")) return false
    if (lower.contains("-live-")) return false
    if (lower.endsWith("-live")) return false
    // Image generation preview models may also reject system instructions
    if (lower.contains("image-generation")) return false
    return true
  }

  private fun Content.toMarkdown(): CharSequence {
    val sb = StringBuilder()
    this.role().getOrNull()?.let { role ->
      sb.append("**Role:** ").append(role).append("\n\n")
    }
    this.parts().orElse(emptyList()).forEach { part ->
      part.text().getOrNull()?.let { text ->
        sb
          .append("\n```text\n")
          .append(text.indent("    "))
          .append("\n```\n")
      }
      part.inlineData().getOrNull()?.let { inlineData ->
        val rawMime = inlineData.mimeType().getOrNull()
        val baseMime = rawMime?.substringBefore(";")?.trim()?.lowercase()
        when (baseMime) {
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
              javax.imageio.ImageIO.write(
                sourceImage,
                baseMime!!.substringAfter("image/"),
                logBytes
              )
              val imageBytes = logBytes.toByteArray()
              sb.append(
                "<img src=\"data:${
                  baseMime
                };base64,${imageBytes.base64()}\" alt=\"image\" width=\"${sourceImage.width}\" height=\"${sourceImage.height}\" />\n"
              )
            }
          }
          "audio/wav", "audio/mp3", "audio/mpeg", "audio/aiff", "audio/aac",
          "audio/ogg", "audio/flac", "audio/pcm", "audio/l16" -> {
            val audioBytes = inlineData.data().getOrNull()
            if (audioBytes != null) {
              val mime = rawMime ?: "audio/wav"
              sb.append(
                "<audio controls src=\"data:${mime};base64,${audioBytes.base64()}\">[audio: ${mime}, ${audioBytes.size} bytes]</audio>\n"
              )
            }
          }


          else -> {
            sb.append("`[Unsupported inline data of type ${rawMime}]`\n")
          }
        }
      }
    }
    return sb.toString()
  }

  private fun buildGenerateContentConfig(
    chatRequest: ModelSchema.ChatRequest,
    supportsSystemInstruction: Boolean = true
  ): GenerateContentConfig? {
    val builder = GenerateContentConfig.builder()
    chatRequest.temperature.let { builder.temperature(it.toFloat()) }
    chatRequest.max_tokens?.let { builder.maxOutputTokens(it) }
    // Configure response modalities (e.g., for TTS audio output)
    chatRequest.modalities?.let { modalities ->
      val mapped = modalities.mapNotNull { modality ->
        when (modality.lowercase()) {
          "text" -> "TEXT"
          "audio" -> "AUDIO"
          "image" -> "IMAGE"
          else -> null
        }
      }
      if (mapped.isNotEmpty()) {
        try {
          builder.responseModalities(mapped)
        } catch (e: Exception) {
          log.warn("Failed to set response modalities: ${e.message}")
        }
      }
    }
    // Configure speech (voice) settings for TTS output
    chatRequest.audio?.let { audioConfig ->
      try {
        val voiceName = audioConfig["voice"]
        if (voiceName != null) {
          val speechConfig = SpeechConfig.builder()
            .voiceConfig(
              VoiceConfig.builder()
                .prebuiltVoiceConfig(
                  PrebuiltVoiceConfig.builder()
                    .voiceName(voiceName)
                    .build()
                )
                .build()
            )
            .build()
          builder.speechConfig(speechConfig)
        }
      } catch (e: Exception) {
        log.warn("Failed to configure speech config for TTS: ${e.message}")
      }
    }
    val systemMessages = chatRequest.messages.filter { it.role == ModelSchema.Role.system }
    if (systemMessages.isNotEmpty() && supportsSystemInstruction) {
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

  private fun convertToGeminiContents(
    messages: List<ModelSchema.ChatMessage>,
    supportsSystemInstruction: Boolean = true
  ): List<Content> {
    if (supportsSystemInstruction) {
      return messages
        .filter { it.role != ModelSchema.Role.system }
        .mapNotNull { it.toContent() }
    }
    // Model does not support system instructions: merge system messages
    // into the first user message (or prepend as a user message).
    val systemContent = messages
      .filter { it.role == ModelSchema.Role.system }
      .flatMap { it.content ?: emptyList() }
    val nonSystem = messages.filter { it.role != ModelSchema.Role.system }
    if (systemContent.isEmpty()) {
      return nonSystem.mapNotNull { it.toContent() }
    }
    val firstUserIdx = nonSystem.indexOfFirst { it.role == ModelSchema.Role.user }
    return if (firstUserIdx >= 0) {
      nonSystem.mapIndexedNotNull { idx, msg ->
        if (idx == firstUserIdx) {
          ModelSchema.ChatMessage(
            role = ModelSchema.Role.user,
           content = mergeTextParts(systemContent + (msg.content ?: emptyList()))
          ).toContent()
        } else {
          msg.toContent()
        }
      }
    } else {
      // No user message exists; prepend system content as a user message
      listOfNotNull(
        ModelSchema.ChatMessage(
          role = ModelSchema.Role.user,
         content = mergeTextParts(systemContent)
        ).toContent()
      ) + nonSystem.mapNotNull { it.toContent() }
    }
  }

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
/**
  * Merge consecutive text-only ContentParts into a single ContentPart with
  * newline-concatenated text. Non-text parts (images, audio) are preserved
  * as-is. Gemini rejects content with multiple separate text parts in some
  * cases, so we coalesce them here.
  */
private fun mergeTextParts(parts: List<ModelSchema.ContentPart>): List<ModelSchema.ContentPart> {
   if (parts.isEmpty()) return parts
   val result = mutableListOf<ModelSchema.ContentPart>()
   val textBuffer = StringBuilder()
   fun flushText() {
     if (textBuffer.isNotEmpty()) {
       result.add(ModelSchema.ContentPart(text = textBuffer.toString()))
       textBuffer.clear()
     }
   }
   for (part in parts) {
     val isTextOnly = part.text != null && part.image_url == null && part.input_audio == null
     if (isTextOnly) {
       if (textBuffer.isNotEmpty()) textBuffer.append("\n")
       textBuffer.append(part.text)
     } else {
       flushText()
       result.add(part)
     }
   }
   flushText()
   return result
}


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
    input_audio != null -> {
      // Handle audio input - Gemini supports audio via inline data
      val audio = input_audio!!
      val format = audio.format.lowercase()
      val mimeType = when (format) {
        "wav" -> "audio/wav"
        "mp3" -> "audio/mp3"
        "aiff" -> "audio/aiff"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "audio/$format"
      }
      val audioBytes = audio.audioBytes
      if (audioBytes.isNotEmpty()) {
        Part.fromBytes(audioBytes, mimeType)
      } else {
        fromText("")
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
    input_audio != null && text != null -> listOfNotNull(
      copy(text = null).part(),
      copy(input_audio = null).part()
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
          val rawMime = mimeType().getOrNull()
          val baseMime = rawMime?.substringBefore(";")?.trim()?.lowercase()
          when (baseMime) {
            "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
              chatMessageResponse.image_data = this.data().getOrNull()
              chatMessageResponse.image_mime_type = rawMime
            }
            "audio/wav", "audio/mp3", "audio/mpeg", "audio/aiff", "audio/aac",
            "audio/ogg", "audio/flac", "audio/pcm", "audio/l16" -> {
              val audioBytes = this.data().getOrNull()
              if (audioBytes != null) {
                val format = baseMime.substringAfter("audio/")
                chatMessageResponse.audio_data = audioBytes
                chatMessageResponse.audio_mime_type = rawMime
                chatMessageResponse.audio_format = format
                // Extract additional parameters from MIME type (e.g., rate, channels)
                if (rawMime.contains(";")) {
                  val params = rawMime.substringAfter(";")
                    .split(";")
                    .mapNotNull { param ->
                      val kv = param.trim().split("=", limit = 2)
                      if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim() else null
                    }
                    .toMap()
                  params["rate"]?.toIntOrNull()?.let { chatMessageResponse.audio_sample_rate = it }
                  params["channels"]?.toIntOrNull()?.let { chatMessageResponse.audio_channels = it }
                }
              }
            }
            else -> {
              // Unsupported inline data type - ignore or log as needed
              log.warn("Received unsupported inline data type in Gemini response: ${rawMime}")
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
    init {
      // The Gemini SDK uses Jackson internally to parse responses. Audio/image
      // generation responses can contain very large base64-encoded payloads
      // that exceed Jackson's default 20MB string length limit. Raise the
      // global default to 256MB to accommodate these responses.
      try {
        val constraints = com.fasterxml.jackson.core.StreamReadConstraints.builder()
          .maxStringLength(256 * 1024 * 1024)
          .build()
        com.fasterxml.jackson.core.StreamReadConstraints.overrideDefaultStreamReadConstraints(constraints)
      } catch (e: Throwable) {
        log.warn("Failed to override Jackson StreamReadConstraints: ${e.message}")
      }
    }
  }
}


private fun ByteArray.base64() = java.util.Base64.getEncoder().encodeToString(this)