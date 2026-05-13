package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.genai.Client
import com.google.genai.types.*
import com.google.genai.types.Content.builder
import com.google.genai.types.Part.fromText
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import okio.ByteString.Companion.decodeBase64
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory
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
  apiBase: String = CoreProviders.Gemini.base,
  workPool: ExecutorService,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream>,
  scheduledPool: ListeningScheduledExecutorService,
  private val useVertexAI: Boolean = false,
  private val project: String? = null,
  private val location: String? = null,
  session: Session,
) : ChatClientBase(
  provider = CoreProviders.Gemini,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  logLevel = logLevel,
  logStreams = logStreams,
  scheduledPool = scheduledPool,
  session = session,
) {

  private val client: Client = buildClient(apiKey, useVertexAI, project, location)

  private fun buildClient(
    apiKey: SecureString,
    useVertexAI: Boolean,
    project: String?,
    location: String?
  ): Client {


    log.debug("Building Gemini client (useVertexAI={}, project={}, location={})", useVertexAI, project, location)
    try {
      val builder = Client.builder()

      if (useVertexAI) {
        builder.vertexAI(true)
        if (project != null && location != null) {
          log.info("Configuring Gemini client with Vertex AI: project={}, location={}", project, location)
          builder.project(project).location(location)
        } else {
          log.info("Configuring Gemini client with Vertex AI using API key (project/location not provided)")
          builder.apiKey(apiKey.decrypt)
        }
      } else {
        log.info("Configuring Gemini client with API key (Generative Language API)")
        builder.apiKey(apiKey.decrypt)
      }

      val client = builder.build()
      log.debug("Gemini client built successfully")
      return client
    } catch (e: IllegalArgumentException) {
      log.error("Invalid configuration for Gemini client: {}", e.message, e)
      throw IllegalStateException("Failed to configure Gemini client due to invalid arguments", e)
    } catch (e: Exception) {
      log.error("Unexpected error building Gemini client: {}", e.message, e)
      throw IllegalStateException("Failed to build Gemini client", e)
    }
  }

  override fun getModels(): List<ChatModel> {
    // Check cache first
    modelsCache[apiBase]?.let {
      log.debug("Returning cached models list for apiBase={} ({} models)", apiBase, it.size)
      return it
    }
    log.debug("Fetching models from Gemini API for apiBase={}", apiBase)
    val models = try {
      client.models.list(
        ListModelsConfig.builder().build()
      ).mapNotNull {
        try {
          val model = it.name().get()
          val baseModelId = model.removePrefix("models/")
          GeminiModels.values.values.find { gm ->
            gm.modelId == baseModelId || gm.modelId == model
          } ?: run {
            // If not found in predefined models, create a dynamic one
            log.debug("Creating basic ChatModel for unknown Gemini model: {}", baseModelId)
            try {
              ChatModel(
                name = model,
                modelId = baseModelId,
                maxTotalTokens = it.inputTokenLimit().get() + it.outputTokenLimit().get(),
                maxOutTokens = it.outputTokenLimit().get(),
                provider = CoreProviders.Gemini,
                inputTokenPricePerK = 0.0, // Default pricing - would need to be configured
                outputTokenPricePerK = 0.0
              )
            } catch (e: NoSuchElementException) {
              log.warn("Skipping model {} due to missing token limits: {}", baseModelId, e.message)
              null
            }
          }
        } catch (e: Exception) {
          log.warn("Failed to process model entry, skipping: {}", e.message, e)
          null
        }
      }.toList()
    } catch (e: java.net.SocketTimeoutException) {
      log.warn("Timeout while fetching Gemini models from {}: {}", apiBase, e.message)
      null
    } catch (e: java.io.IOException) {
      log.warn("I/O error while fetching Gemini models from {}: {}", apiBase, e.message, e)
      null
    } catch (e: Exception) {
      log.warn("Failed to fetch models from {}: {}", apiBase, e.message, e)
      null
    }
    // Cache the result
    models?.let {
      log.info("Cached {} Gemini models for apiBase={}", it.size, apiBase)
      modelsCache[apiBase] = it
    }
    return models ?: emptyList()
  }

  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    val requestID = UUID.randomUUID().toString()
    val startTime = System.currentTimeMillis()
    log.debug("Starting Gemini chat request {} for model {}", requestID, model.modelId)
    try {
      val supportsSystemInstruction = modelSupportsSystemInstruction(model.modelId)
      log.debug(
        "Request {}: model {} supportsSystemInstruction={}",
        requestID,
        model.modelId,
        supportsSystemInstruction
      )
      val config = try {
        buildGenerateContentConfig(chatRequest, supportsSystemInstruction)
      } catch (e: Exception) {
        log.error("Request {}: Failed to build GenerateContentConfig: {}", requestID, e.message, e)
        throw IllegalStateException("Failed to build Gemini generation config for request $requestID", e)
      }
      val contents: List<Content> = try {
        convertToGeminiContents(chatRequest.messages, supportsSystemInstruction)
      } catch (e: Exception) {
        log.error("Request {}: Failed to convert messages to Gemini contents: {}", requestID, e.message, e)
        throw IllegalStateException("Failed to convert messages for request $requestID", e)
      }
      log.debug("Request {}: built config and {} content items", requestID, contents.size)
      val sysInstruct = config?.systemInstruction()?.getOrNull()?.text()?.indent("  ")
      val contentStr = contents.joinToString("\n\n") { it.toMarkdown() }
      val toJson = config?.toJson()?.indent("  ") ?: "No config"
      log(
        "\n<details>\n<summary>Sending request to Gemini SDK for model: ${model.modelId} (${requestID})</summary>\n\n```json\n$toJson\n```\n\nSystem Prompt:\n```\n${sysInstruct}\n```\n\n$contentStr\n</details>",
        logStreams
      )
      val response = try {
        client.models.generateContent(model.modelId, contents, config)
      } catch (e: java.net.SocketTimeoutException) {
        log.error("Request {}: Timeout calling Gemini API for model {}: {}", requestID, model.modelId, e.message)
        throw e
      } catch (e: java.io.IOException) {
        log.error("Request {}: I/O error calling Gemini API for model {}: {}", requestID, model.modelId, e.message, e)
        throw e
      } catch (e: IllegalArgumentException) {
        log.error(
          "Request {}: Invalid arguments to Gemini API for model {}: {}",
          requestID,
          model.modelId,
          e.message,
          e
        )
        throw e
      } catch (e: Exception) {
        log.error(
          "Request {}: Unexpected error calling Gemini API for model {}: {}",
          requestID,
          model.modelId,
          e.message,
          e
        )
        throw e
      }
      val elapsed = System.currentTimeMillis() - startTime
      log.debug("Request {}: Gemini API responded in {} ms", requestID, elapsed)
      // Log response
      log(
        "\n<details>\n<summary>Gemini SDK Response (${requestID})</summary>\n\n${
          response.candidates().orElse(emptyList()).joinToString("\n\n") { candidate ->
            candidate.content().orElse(null)?.toMarkdown() ?: "\n\n**No content**\n\n"
          }
        }\n</details>",
        logStreams
      )
      val chatResponse = try {
        convertFromGeminiResponse(response)
      } catch (e: Exception) {
        log.error("Request {}: Failed to convert Gemini response: {}", requestID, e.message, e)
        throw IllegalStateException("Failed to convert Gemini response for request $requestID", e)
      }
      if (chatResponse.usage != null) {
        try {
          usageHandler.onUsage(model, chatResponse.usage?.copy(cost = model.pricing(chatResponse.usage!!))!!)
        } catch (e: Exception) {
          log.warn("Request {}: Failed to record usage: {}", requestID, e.message, e)
        }
      } else {
        log.debug("Request {}: No usage data returned from Gemini SDK", requestID)
        log("No usage data returned from Gemini SDK for request ${requestID}", logStreams)
      }
      log.info(
        "Request {}: completed successfully in {} ms ({} choices)",
        requestID, System.currentTimeMillis() - startTime, chatResponse.choices?.size ?: 0
      )
      return chatResponse
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startTime
      log.error(
        "Error during Gemini SDK chat request {} for model {} after {} ms: {}",
        requestID, model.modelId, elapsed, e.message, e
      )
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



    try {
      this.role().getOrNull()?.let { role ->
        sb.append("**Role:** ").append(role).append("\n\n")
      }
      this.parts().orElse(emptyList()).forEach { part ->
        try {
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
                try {
                  val imageBytes = inlineData.data().getOrNull()
                  if (imageBytes != null) {
                    /*Resize to no more than 256 px wide*/
                    val maxWidth = 256
                    var sourceImage = javax.imageio.ImageIO.read(imageBytes.inputStream())
                    if (sourceImage == null) {
                      log.warn(
                        "Failed to decode image of type {} ({} bytes) for markdown rendering",
                        rawMime, imageBytes.size
                      )
                      sb.append("`[Unable to decode image of type ${rawMime}]`\n")
                      return@let
                    }
                    if (sourceImage.width > maxWidth) {
                      val aspectRatio = sourceImage.height.toDouble() / sourceImage.width.toDouble()
                      val newHeight = (maxWidth * aspectRatio).toInt()
                      val resizedImage = java.awt.image.BufferedImage(maxWidth, newHeight, sourceImage.type)
                      val g2d = resizedImage.createGraphics()
                      try {
                        g2d.drawImage(sourceImage, 0, 0, maxWidth, newHeight, null)
                      } finally {
                        g2d.dispose()
                      }
                      sourceImage = resizedImage
                    }
                    val logBytes = java.io.ByteArrayOutputStream()
                    javax.imageio.ImageIO.write(
                      sourceImage,
                      baseMime!!.substringAfter("image/"),
                      logBytes
                    )
                    val outBytes = logBytes.toByteArray()
                    sb.append(
                      "<img src=\"data:${
                        baseMime
                      };base64,${outBytes.base64()}\" alt=\"image\" width=\"${sourceImage.width}\" height=\"${sourceImage.height}\" />\n"
                    )
                  }
                } catch (e: java.io.IOException) {
                  log.warn("I/O error while processing image inline data ({}): {}", rawMime, e.message, e)
                  sb.append("`[Error rendering image of type ${rawMime}: ${e.message}]`\n")
                } catch (e: OutOfMemoryError) {
                  log.error("Out of memory while processing image inline data ({}): {}", rawMime, e.message)
                  sb.append("`[Out of memory rendering image of type ${rawMime}]`\n")
                } catch (e: Exception) {
                  log.warn("Unexpected error while processing image inline data ({}): {}", rawMime, e.message, e)
                  sb.append("`[Error rendering image of type ${rawMime}]`\n")
                }
              }

              "audio/wav", "audio/mp3", "audio/mpeg", "audio/aiff", "audio/aac",
              "audio/ogg", "audio/flac", "audio/pcm", "audio/l16" -> {
                try {
                  val audioBytes = inlineData.data().getOrNull()
                  if (audioBytes != null) {
                    val mime = rawMime ?: "audio/wav"
                    sb.append(
                      "<audio controls src=\"data:${mime};base64,${audioBytes.base64()}\">[audio: ${mime}, ${audioBytes.size} bytes]</audio>\n"
                    )
                  }
                } catch (e: Exception) {
                  log.warn("Error while processing audio inline data ({}): {}", rawMime, e.message, e)
                  sb.append("`[Error rendering audio of type ${rawMime}]`\n")
                }
              }


              else -> {
                log.debug("Unsupported inline data type encountered in markdown rendering: {}", rawMime)
                sb.append("`[Unsupported inline data of type ${rawMime}]`\n")
              }
            }
          }
        } catch (e: Exception) {
          log.warn("Error processing content part for markdown rendering: {}", e.message, e)
          sb.append("`[Error rendering content part: ${e.message}]`\n")
        }
      }
    } catch (e: Exception) {
      log.warn("Error converting Content to markdown: {}", e.message, e)
      sb.append("`[Error converting content: ${e.message}]`\n")
    }
    return sb.toString()
  }

  private fun buildGenerateContentConfig(
    chatRequest: ModelSchema.ChatRequest,
    supportsSystemInstruction: Boolean = true
  ): GenerateContentConfig? {
    val builder = GenerateContentConfig.builder()
    try {
      chatRequest.temperature.let { builder.temperature(it.toFloat()) }
    } catch (e: Exception) {
      log.warn("Failed to set temperature ({}): {}", chatRequest.temperature, e.message)
    }
    chatRequest.max_tokens?.let {
      try {
        builder.maxOutputTokens(it)
      } catch (e: Exception) {
        log.warn("Failed to set max output tokens ({}): {}", it, e.message)
      }
    }
    // Configure response modalities (e.g., for TTS audio output)
    chatRequest.modalities?.let { modalities ->
      val mapped = modalities.mapNotNull { modality ->
        when (modality.lowercase()) {
          "text" -> "TEXT"
          "audio" -> "AUDIO"
          "image" -> "IMAGE"
          else -> {
            log.debug("Ignoring unsupported modality '{}'", modality)
            null
          }
        }
      }
      if (mapped.isNotEmpty()) {
        try {
          log.debug("Setting Gemini response modalities: {}", mapped)
          builder.responseModalities(mapped)
        } catch (e: Exception) {
          log.warn("Failed to set response modalities {}: {}", mapped, e.message, e)
        }
      }
    }
    // Configure speech (voice) settings for TTS output
    chatRequest.audio?.let { audioConfig ->
      try {
        val voiceName = audioConfig["voice"]
        if (voiceName != null) {
          log.debug("Configuring Gemini speech config with voice '{}'", voiceName)
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
        } else {
          log.debug("Audio config provided but no 'voice' key found; skipping speech config")
        }
      } catch (e: Exception) {
        log.warn("Failed to configure speech config for TTS: {}", e.message, e)
      }
    }
    val systemMessages = chatRequest.messages.filter { it.role == ModelSchema.Role.system }
    if (systemMessages.isNotEmpty() && supportsSystemInstruction) {
      try {
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
      } catch (e: Exception) {
        log.warn("Failed to set system instruction (continuing without): {}", e.message, e)
      }
    } else if (systemMessages.isNotEmpty() && !supportsSystemInstruction) {
      log.debug(
        "Model does not support system instruction; {} system message(s) will be merged into user content",
        systemMessages.size
      )
    }
    return try {
      builder.build()
    } catch (e: Exception) {
      log.error("Failed to build GenerateContentConfig: {}", e.message, e)
      throw e
    }
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
      try {
        // Handle image URLs
        val imageUrl = image_url
        when {
          imageUrl?.startsWith("data:") == true -> {
            // Base64 encoded image
            val parts = imageUrl.split(",")
            if (parts.size < 2) {
              log.warn("Malformed data URL for image; falling back to empty text part")
              fromText("")
            } else {
              val mimeType = parts[0].substringAfter("data:").substringBefore(";")
              val data = parts[1]
              val decoded = data.decodeBase64()?.toByteArray()
              if (decoded == null) {
                log.warn("Failed to base64-decode image data URL (mime={})", mimeType)
                fromText("")
              } else {
                Part.fromBytes(decoded, mimeType)
              }
            }
          }

          imageUrl?.startsWith("gs://") == true -> {
            // GCS URI
            Part.fromUri(imageUrl, "image/jpeg")
          }

          imageUrl != null -> {
            // Regular URL - convert to text description
            Part.fromUri(imageUrl, "image/jpeg")
          }

          else -> {
            log.debug("Image URL is null; emitting empty text part")
            fromText("")
          }
        }
      } catch (e: Exception) {
        log.warn("Failed to convert image content part to Gemini Part: {}", e.message, e)
        fromText("")
      }
    }

    input_audio != null -> {
      try {
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
          else -> {
            log.debug("Using non-standard audio MIME type for format '{}'", format)
            "audio/$format"
          }
        }
        val audioBytes = audio.audioBytes
        if (audioBytes.isNotEmpty()) {
          Part.fromBytes(audioBytes, mimeType)
        } else {
          log.warn("Audio input has empty audioBytes (format={}); emitting empty text part", format)
          fromText("")
        }
      } catch (e: Exception) {
        log.warn("Failed to convert audio content part to Gemini Part: {}", e.message, e)
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



      try {
        val content = candidate.content().orElse(null)
        val text = content?.parts()?.orElse(emptyList())
          ?.mapNotNull {
            try {
              it.text().getOrNull()
            } catch (e: Exception) {
              log.warn("Failed to extract text from response part at choice {}: {}", index, e.message, e)
              null
            }
          }?.joinToString("\n")?.let {
            when (it) {
              "" -> null
              else -> it
            }
          }

        val chatMessageResponse = ModelSchema.ChatMessageResponse(
          content = text,
        )
        content?.parts()?.orElse(emptyList())?.forEach { part ->
          try {
            part.inlineData()?.getOrNull()?.apply {
              val rawMime = mimeType().getOrNull()
              val baseMime = rawMime?.substringBefore(";")?.trim()?.lowercase()
              when (baseMime) {
                "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
                  chatMessageResponse.image_data = this.data().getOrNull()
                  chatMessageResponse.image_mime_type = rawMime
                  log.debug(
                    "Choice {}: extracted image data ({} bytes, mime={})",
                    index, chatMessageResponse.image_data?.size ?: 0, rawMime
                  )
                }

                "audio/wav", "audio/mp3", "audio/mpeg", "audio/aiff", "audio/aac",
                "audio/ogg", "audio/flac", "audio/pcm", "audio/l16" -> {
                  val audioBytes = this.data().getOrNull()
                  if (audioBytes != null) {
                    val format = baseMime.substringAfter("audio/")
                    chatMessageResponse.audio_data = audioBytes
                    chatMessageResponse.audio_mime_type = rawMime
                    chatMessageResponse.audio_format = format
                    log.debug(
                      "Choice {}: extracted audio data ({} bytes, mime={}, format={})",
                      index, audioBytes.size, rawMime, format
                    )
                    // Extract additional parameters from MIME type (e.g., rate, channels)
                    if (rawMime.contains(";")) {
                      try {
                        val params = rawMime.substringAfter(";")
                          .split(";")
                          .mapNotNull { param ->
                            val kv = param.trim().split("=", limit = 2)
                            if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim() else null
                          }
                          .toMap()
                        params["rate"]?.toIntOrNull()?.let { chatMessageResponse.audio_sample_rate = it }
                        params["channels"]?.toIntOrNull()?.let { chatMessageResponse.audio_channels = it }
                      } catch (e: Exception) {
                        log.warn("Failed to parse audio MIME parameters from '{}': {}", rawMime, e.message)
                      }
                    }
                  } else {
                    log.warn("Choice {}: audio inline data is null (mime={})", index, rawMime)
                  }
                }

                else -> {
                  // Unsupported inline data type - ignore or log as needed
                  log.warn(
                    "Choice {}: received unsupported inline data type in Gemini response: {}",
                    index, rawMime
                  )
                }
              }
            }
          } catch (e: Exception) {
            log.warn("Choice {}: error processing response part: {}", index, e.message, e)
          }
        }
        ModelSchema.ChatChoice(
          message = chatMessageResponse,
          index = index,
          finish_reason = candidate.finishReason().orElse(null)?.toString()
        )
      } catch (e: Exception) {
        log.error("Failed to convert candidate at index {}: {}", index, e.message, e)
        ModelSchema.ChatChoice(
          message = ModelSchema.ChatMessageResponse(content = null),
          index = index,
          finish_reason = "error"
        )
      }
    }

    val usage = try {
      response.usageMetadata().orElse(null)?.let { metadata ->
        ModelSchema.Usage(
          prompt_tokens = metadata.promptTokenCount().orElse(0).toLong(),
          completion_tokens = metadata.candidatesTokenCount().orElse(0).toLong(),
          total_tokens = metadata.totalTokenCount().orElse(0).toLong()
        )
      }
    } catch (e: Exception) {
      log.warn("Failed to extract usage metadata from Gemini response: {}", e.message, e)
      null
    }

    return ModelSchema.ChatResponse(
      choices = choices,
      usage = usage
    )
  }

  override fun authorize(request: HttpRequest) {
    log.error("authorize() called on GeminiSdkChatClient but is not implemented (provider={})", provider)
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