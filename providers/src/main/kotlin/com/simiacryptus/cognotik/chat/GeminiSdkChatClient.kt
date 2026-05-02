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
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.AudioSegment
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
  workPool = workPool, logLevel = logLevel, logStreams = logStreams, scheduledPool = scheduledPool
), ChatClientInterface {

  private val client: Client = buildClient(apiKey, useVertexAI, project, location)

  private fun buildClient(
    apiKey: SecureString, useVertexAI: Boolean, project: String?, location: String?
  ): Client {

    return try {
      val builder = Client.builder()
      if (useVertexAI) {
        log.info("Building Gemini client in Vertex AI mode (project=$project, location=$location)")
        builder.vertexAI(true)
        if (project != null && location != null) {
          builder.project(project).location(location)
        } else {
          log.warn("Vertex AI mode requested but project/location not provided; falling back to API key auth")
          builder.apiKey(apiKey.decrypt)
        }
      } else {
        log.debug("Building Gemini client in API key mode")
        builder.apiKey(apiKey.decrypt)
      }
      builder.build()
    } catch (e: Exception) {
      log.error("Failed to build Gemini SDK client (useVertexAI=$useVertexAI, project=$project, location=$location)", e)
      throw IllegalStateException("Failed to initialize Gemini SDK client: ${e.message}", e)
    }

  }

  /**
   * Convert a GenerateContentConfig (or any object containing many Optional<T>
   * fields) into a clean map suitable for JSON logging. The Gemini SDK's
   * config classes serialize each Optional as `{empty: true, present: false}`
   * which is extremely noisy. This walks the object reflectively, unwraps
   * Optionals, and omits any empty fields.
   */
  private fun configToLoggable(config: Any?): Any? {
    if (config == null) return null
    return unwrapForLogging(config, depth = 0)
  }

  private fun unwrapForLogging(value: Any?, depth: Int): Any? {
    if (value == null) return null
    if (depth > 8) return value.toString()
    return when (value) {
      is Optional<*> -> value.getOrNull()?.let { unwrapForLogging(it, depth + 1) }
      is CharSequence, is Number, is Boolean, is Enum<*> -> value
      is Map<*, *> -> value.entries.mapNotNull { (k, v) ->
        val unwrapped = unwrapForLogging(v, depth + 1)
        if (unwrapped == null) null else k.toString() to unwrapped
      }.toMap()

      is Iterable<*> -> value.mapNotNull { unwrapForLogging(it, depth + 1) }
      is Array<*> -> value.mapNotNull { unwrapForLogging(it, depth + 1) }
      else -> {
        // Try to reflectively read getters / accessor methods.
        val cls = value.javaClass
        val pkg = cls.`package`?.name ?: ""
        // Only deeply traverse Gemini SDK types; otherwise fall back to toString.
        if (!pkg.startsWith("com.google.genai")) {
          return try {
            value.toString()
          } catch (e: Throwable) {
            log.trace("toString() failed for ${cls.name}: ${e.message}")
            "<${cls.simpleName}>"
          }
        }
        val result = linkedMapOf<String, Any?>()
        for (method in cls.methods) {
          if (method.parameterCount != 0) continue
          if (method.declaringClass == Object::class.java) continue
          val name = method.name
          // Skip common non-property accessors
          if (name in setOf("toString", "hashCode", "toBuilder", "getClass", "isEmpty")) continue
          if (name.startsWith("__") || name.contains("$")) continue
          try {
            method.isAccessible = true
            val raw = method.invoke(value)
            val unwrapped = unwrapForLogging(raw, depth + 1) ?: continue
            // Skip empty maps/lists
            if (unwrapped is Map<*, *> && unwrapped.isEmpty()) continue
            if (unwrapped is List<*> && unwrapped.isEmpty()) continue
            result[name] = unwrapped
          } catch (t: Throwable) {
            // Ignore reflection failures but log at trace level for diagnostics
            log.trace("Reflection failed for ${cls.simpleName}.${method.name}: ${t.message}")
          }
        }
        if (result.isEmpty()) null else result
      }
    }
  }


  override fun getModels(): List<ChatModel>? {
    // Check cache first
    modelsCache[apiBase]?.let { return it }
    val models = try {
      log.debug("Fetching available Gemini models from $apiBase")
      client.models.list(
        ListModelsConfig.builder().build()
      ).mapNotNull {
        try {
          val model = it.name().getOrNull() ?: run {
            log.warn("Gemini model entry has no name; skipping")
            return@mapNotNull null
          }
          val baseModelId = model.removePrefix("models/")
          GeminiModels.values.values.find { m ->
            m.modelId == baseModelId || m.modelId == model
          } ?: run {
            // If not found in predefined models, create a dynamic one
            log.debug("Creating basic ChatModel for unknown Gemini model: $baseModelId")
            val inputLimit = it.inputTokenLimit().getOrNull() ?: 0
            val outputLimit = it.outputTokenLimit().getOrNull() ?: 0
            if (inputLimit == 0 && outputLimit == 0) {
              log.warn("Model $baseModelId has no token limits reported; using defaults")
            }
            ChatModel(
              name = model,
              modelId = baseModelId,
              maxTotalTokens = inputLimit + outputLimit,
              maxOutTokens = outputLimit,
              provider = CoreProviders.Gemini,
              inputTokenPricePerK = 0.0, // Default pricing - would need to be configured
              outputTokenPricePerK = 0.0
            )
          }
        } catch (e: Exception) {
          log.warn("Failed to process Gemini model entry: ${e.message}", e)
          null
        }
      }.toList()
    } catch (e: IllegalStateException) {
      log.error("Gemini SDK client is not properly configured: ${e.message}", e)
      null
    } catch (e: Exception) {
      log.warn("Failed to fetch models from Gemini API ($apiBase): ${e.message}", e)
      null
    }
    // Cache the result
    models?.let {
      modelsCache[apiBase] = it
      log.debug("Cached ${it.size} Gemini models for $apiBase")
    }
    return models
  }

  override fun chat(
    chatRequest: ModelSchema.ChatRequest, model: ChatModel, logStreams: MutableList<BufferedOutputStream>
  ): ModelSchema.ChatResponse {
    val requestID = UUID.randomUUID().toString()
    val startTime = System.currentTimeMillis()
    try {
      val supportsSystemInstruction = modelSupportsSystemInstruction(model.modelId)
      val config = buildGenerateContentConfig(chatRequest, supportsSystemInstruction)
      val contents: List<Content> = convertToGeminiContents(chatRequest.messages, supportsSystemInstruction)
      if (contents.isEmpty()) {
        log.warn("Chat request $requestID has no content after conversion (model=${model.modelId})")
      }
      val sysInstruct = config?.systemInstruction()?.getOrNull()?.text()?.indent("  ")
      val contentStr = contents.joinToString("\n\n") { it.toMarkdown() }
      val toJson = try {
        config?.toJson()?.indent("  ")
      } catch (e: Exception) {
        log.warn("Failed to serialize config for logging (request $requestID): ${e.message}")
        "<config serialization failed: ${e.message}>"
      }
      log(
        "\n<details>\n<summary>Sending request to Gemini SDK for model: ${model.modelId} (${requestID})</summary>\n\n```json\n$toJson\n```\n\nSystem Prompt:\n```\n${sysInstruct}\n```\n\n$contentStr\n</details>",
        logStreams
      )
      // Use streaming for audio modality (TTS) to avoid huge single-payload
      // responses and to allow chunks to be concatenated as they arrive.
      // Streaming is also generally faster for any long generation.
      val wantsAudio = chatRequest.modalities?.any { it.equals("audio", ignoreCase = true) } == true
      val chatResponse = if (wantsAudio) {
        log.debug("Using streaming path for request $requestID (audio modality requested)")
        streamAndAggregate(model, contents, config, requestID, logStreams)
      } else {
        val response = try {
          client.models.generateContent(model.modelId, contents, config)
        } catch (e: Exception) {
          log.error("Gemini generateContent call failed (request $requestID, model=${model.modelId}): ${e.message}", e)
          throw e
        }
        // Log response
        log(
          "\n<details>\n<summary>Gemini SDK Response (${requestID})</summary>\n\n${
            response.candidates().orElse(emptyList()).joinToString("\n\n") { candidate ->
              try {
                candidate.content().orElse(null)?.toMarkdown() ?: "\n\n**No content**\n\n"
              } catch (e: Exception) {
                log.warn("Failed to render candidate content for logging (request $requestID): ${e.message}")
                "\n\n**[render error: ${e.message}]**\n\n"
              }
            }
          }\n</details>", logStreams
        )
        convertFromGeminiResponse(response)
      }
      if (chatResponse.usage != null) {
        try {
          onUsage(
            model, chatResponse.usage?.copy(cost = model.pricing(chatResponse.usage!!))!!, logStreams = logStreams
          )
        } catch (e: Exception) {
          log.warn("Failed to record usage for request $requestID: ${e.message}", e)
        }
      } else {
        log("No usage data returned from Gemini SDK for request ${requestID}", logStreams)
        log.debug("Request $requestID completed without usage metadata")
      }
      val elapsed = System.currentTimeMillis() - startTime
      log.debug("Gemini chat request $requestID completed in ${elapsed}ms (model=${model.modelId})")
      return chatResponse
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startTime
      log.error(
        "Error during Gemini SDK chat request $requestID after ${elapsed}ms (model=${model.modelId}): ${e.message}",
        e
      )
      throw e
    }
  }

  /**
   * Stream a generateContent request, aggregating text and (most importantly)
   * audio bytes across all chunks/parts. The non-streaming path concatenates
   * the entire response as a single huge base64 JSON blob which is both slow
   * and prone to truncation; worse, the prior aggregation logic only kept
   * the last audio Part's bytes, producing audio that goes silent after the
   * first chunk (~10s).
   *
   * For raw PCM/L16 chunks (Gemini's typical TTS output), simple byte
   * concatenation is correct since every chunk shares the same sample rate,
   * channel count, and bit depth. For WAV chunks (rare from the API), each
   * chunk's RIFF header is stripped and a single WAV header is generated
   * over the merged PCM data.
   */
  private fun streamAndAggregate(
    model: ChatModel,
    contents: List<Content>,
    config: GenerateContentConfig?,
    requestID: String,
    logStreams: MutableList<BufferedOutputStream>
  ): ModelSchema.ChatResponse {
    val textBuilder = StringBuilder()
    val audioChunks = mutableListOf<ByteArray>()
    var audioMime: String? = null
    var audioFormat: String? = null
    var audioSampleRate: Int? = null
    var audioChannels: Int? = null
    var imageBytes: ByteArray? = null
    var imageMime: String? = null
    var finishReason: String? = null
    var promptTokens = 0L
    var completionTokens = 0L
    var totalTokens = 0L
    var chunkCount = 0
    var partErrorCount = 0
    val streamStart = System.currentTimeMillis()
    val stream = try {
      client.models.generateContentStream(model.modelId, contents, config)
    } catch (e: Exception) {
      log.error("Failed to initiate Gemini streaming request $requestID (model=${model.modelId}): ${e.message}", e)
      throw e
    }
    try {
      val iterator = stream.iterator()
      while (iterator.hasNext()) {
        val chunk = try {
          iterator.next()
        } catch (e: Exception) {
          log.warn("Failed to fetch next chunk #${chunkCount + 1} from stream (request $requestID): ${e.message}", e)
          break
        }
        chunkCount++
        try {
          chunk.usageMetadata().getOrNull()?.let { meta ->
            promptTokens = meta.promptTokenCount().orElse(0)!!.toLong().coerceAtLeast(promptTokens)
            completionTokens = meta.candidatesTokenCount().orElse(0)!!.toLong().coerceAtLeast(completionTokens)
            totalTokens = meta.totalTokenCount().orElse(0)!!.toLong().coerceAtLeast(totalTokens)
          }
          for (candidate in chunk.candidates().orElse(emptyList())!!) {
            candidate.finishReason().getOrNull()?.toString()?.let { finishReason = it }
            val content = candidate.content().getOrNull() ?: continue
            for (part in content.parts().orElse(emptyList())!!) {
              try {
                part.text().getOrNull()?.let { textBuilder.append(it) }
                part.inlineData().getOrNull()?.let { inline ->
                  val rawMime = inline.mimeType().getOrNull()
                  val baseMime = rawMime?.substringBefore(";")?.trim()?.lowercase()
                  val data = inline.data().getOrNull() ?: run {
                    log.warn("Streaming request $requestID: inline data part has null bytes (mime=$rawMime)")
                    return@let
                  }
                  when (baseMime) {
                    "audio/wav", "audio/mp3", "audio/mpeg", "audio/aiff", "audio/aac",
                    "audio/ogg", "audio/flac", "audio/pcm", "audio/l16" -> {
                      audioMime = audioMime ?: rawMime
                      audioFormat = audioFormat ?: baseMime.substringAfter("audio/")
                      if (rawMime?.contains(";") == true) {
                        try {
                          val params = rawMime.substringAfter(";").split(";").mapNotNull { p ->
                            val kv = p.trim().split("=", limit = 2)
                            if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim() else null
                          }.toMap()
                          params["rate"]?.toIntOrNull()?.let { audioSampleRate = audioSampleRate ?: it }
                          params["channels"]?.toIntOrNull()?.let { audioChannels = audioChannels ?: it }
                        } catch (e: Exception) {
                          log.warn("Failed to parse audio MIME parameters '$rawMime' (request $requestID): ${e.message}")
                        }
                      }
                      audioChunks.add(data)
                    }

                    "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
                      imageBytes = data
                      imageMime = rawMime
                    }

                    else -> log.warn("Streaming request $requestID: unsupported inline data type: $rawMime")
                  }
                }
              } catch (e: Exception) {
                partErrorCount++
                log.warn("Failed to process part in chunk #$chunkCount (request $requestID): ${e.message}", e)
              }
            }
          }
        } catch (e: Exception) {
          log.warn("Failed to process chunk #$chunkCount in stream (request $requestID): ${e.message}", e)
        }
      }
    } catch (e: Exception) {
      log.error("Stream iteration failed for request $requestID after $chunkCount chunks: ${e.message}", e)
      // Re-throw only if we got nothing useful; otherwise return partial results
      if (chunkCount == 0 || (textBuilder.isEmpty() && audioChunks.isEmpty() && imageBytes == null)) {
        throw e
      }
      log.warn("Returning partial results for request $requestID despite stream error")
    } finally {
      closeStreamQuietly(stream, requestID)
    }
    val streamElapsed = System.currentTimeMillis() - streamStart
    log(
      "\n<details>\n<summary>Gemini SDK Streaming Response (${requestID}) - $chunkCount chunks, " +
          "${audioChunks.size} audio parts, ${audioChunks.sumOf { it.size }} audio bytes, " +
          "${streamElapsed}ms${if (partErrorCount > 0) ", $partErrorCount part errors" else ""}</summary>\n</details>",
      logStreams
    )
    log.debug(
      "Stream $requestID complete: $chunkCount chunks, ${audioChunks.size} audio parts, " +
        "${audioChunks.sumOf { it.size }} audio bytes in ${streamElapsed}ms"
    )
    val mergedAudio: ByteArray? = if (audioChunks.isNotEmpty()) {
      try {
        mergeAudioChunks(audioChunks, audioFormat, audioSampleRate, audioChannels)
      } catch (e: Exception) {
        log.error(
          "Failed to merge ${audioChunks.size} audio chunks for request $requestID " +
              "(format=$audioFormat, rate=$audioSampleRate, channels=$audioChannels): ${e.message}", e
        )
        null
      }
    } else null
    val message = ModelSchema.ChatMessageResponse(
      content = textBuilder.toString().ifEmpty { null }
    )
    mergedAudio?.let {
      message.audio_data = it
      message.audio_mime_type = audioMime
      message.audio_format = audioFormat
      audioSampleRate?.let { sr -> message.audio_sample_rate = sr }
      audioChannels?.let { ch -> message.audio_channels = ch }
    }
    imageBytes?.let {
      message.image_data = it
      message.image_mime_type = imageMime
    }
    val usage = if (promptTokens > 0 || completionTokens > 0 || totalTokens > 0) {
      ModelSchema.Usage(
        prompt_tokens = promptTokens,
        completion_tokens = completionTokens,
        total_tokens = if (totalTokens > 0) totalTokens else promptTokens + completionTokens
      )
    } else null
    return ModelSchema.ChatResponse(
      choices = listOf(
        ModelSchema.ChatChoice(message = message, index = 0, finish_reason = finishReason)
      ),
      usage = usage
    )
  }

  /**
   * Concatenate audio chunks correctly:
   *  - Raw PCM/L16 chunks are simply byte-concatenated (all chunks share the
   *    same sample rate / channels / bit-depth).
   *  - WAV chunks have their RIFF headers stripped, the PCM payloads are
   *    concatenated, and a fresh WAV header is generated.
   */
  /**
   * Close a Gemini SDK stream using whatever close mechanism is available.
   * The SDK's ResponseStream is iterable and holds an underlying HTTP
   * connection that MUST be released; otherwise the connection pool will
   * be exhausted under concurrent load (e.g. parallel TTS rendering),
   * causing the application to hang indefinitely.
   *
   * We try multiple strategies because the concrete return type of
   * generateContentStream() varies across SDK versions:
   *   1. AutoCloseable / Closeable
   *   2. A reflective close() / cancel() method
   */
  private fun closeStreamQuietly(stream: Any?, requestID: String) {
    if (stream == null) return
    // Strategy 1: AutoCloseable (covers Closeable too)
    if (stream is AutoCloseable) {
      try {
        stream.close()
        return
      } catch (t: Throwable) {
        log.debug("AutoCloseable.close() failed for Gemini stream (request $requestID): ${t.message}")
      }
    }
    // Strategy 2: Reflective close() or cancel()
    val cls = stream.javaClass
    for (methodName in listOf("close", "cancel", "shutdown")) {
      try {
        val method = cls.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
        if (method != null) {
          method.isAccessible = true
          method.invoke(stream)
          log.debug("Closed Gemini stream via reflective $methodName() (request $requestID)")
          return
        }
      } catch (t: Throwable) {
        log.debug("Reflective $methodName() failed for Gemini stream (request $requestID): ${t.message}")
      }
    }
    log.warn(
      "Could not close Gemini stream (request $requestID, type=${cls.name}); " +
        "underlying HTTP connection may leak"
    )
  }

  private fun mergeAudioChunks(
    chunks: List<ByteArray>,
    format: String?,
    sampleRate: Int?,
    channels: Int?
  ): ByteArray {
    require(chunks.isNotEmpty()) { "Cannot merge empty audio chunks list" }
    if (chunks.size == 1) return chunks[0]
    val fmt = format?.lowercase()
    log.debug("Merging ${chunks.size} audio chunks (format=$fmt, rate=$sampleRate, channels=$channels)")
    return when (fmt) {
      "wav" -> {
        val pcmTotal = chunks.foldIndexed(ByteArray(0)) { idx, acc, c ->
          try {
            acc + AudioSegment.stripWavHeader(c)
          } catch (e: Exception) {
            log.warn("Failed to strip WAV header from chunk $idx (size=${c.size}): ${e.message}")
            acc + c
          }
        }
        AudioSegment.pcmToWav(
          pcm = pcmTotal,
          sampleRate = sampleRate ?: 24000,
          channels = channels ?: 1,
          bitsPerSample = 16
        )
      }

      else -> {
        // Raw PCM/L16/etc.: direct concatenation is correct.
        val total = chunks.sumOf { it.size }
        val out = ByteArray(total)
        var off = 0
        for (c in chunks) {
          System.arraycopy(c, 0, out, off, c.size)
          off += c.size
        }
        out
      }
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
        sb.append("\n```text\n").append(text.indent("    ")).append("\n```\n")
      }
      part.inlineData().getOrNull()?.let { inlineData ->
        val rawMime = inlineData.mimeType().getOrNull()
        val baseMime = rawMime?.substringBefore(";")?.trim()?.lowercase()
        when (baseMime) {
          "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
            val imageBytes = inlineData.data().getOrNull()
            if (imageBytes != null) {
              try {
                /*Resize to no more than 256 px wide*/
                val maxWidth = 256
                var sourceImage = javax.imageio.ImageIO.read(imageBytes.inputStream())
                if (sourceImage == null) {
                  log.warn("Could not decode image bytes (mime=$rawMime, size=${imageBytes.size})")
                  sb.append("`[Image decode failed: ${rawMime}, ${imageBytes.size} bytes]`\n")
                  return@let
                }
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
                  sourceImage, baseMime!!.substringAfter("image/"), logBytes
                )
                val resizedBytes = logBytes.toByteArray()
                sb.append(
                  "<img src=\"data:${
                    baseMime
                  };base64,${resizedBytes.base64()}\" alt=\"image\" width=\"${sourceImage.width}\" height=\"${sourceImage.height}\" />\n"
                )
              } catch (e: Exception) {
                log.warn("Failed to render image for logging (mime=$rawMime): ${e.message}", e)
                sb.append("`[Image render error: ${e.message}]`\n")
              }
            }
          }

          "audio/wav", "audio/mp3", "audio/mpeg", "audio/aiff", "audio/aac", "audio/ogg", "audio/flac", "audio/pcm", "audio/l16" -> {
            val audioBytes = inlineData.data().getOrNull()
            if (audioBytes != null) {
              val mime = rawMime ?: "audio/wav"
              try {
                sb.append(
                  "<audio controls src=\"data:${mime};base64,${audioBytes.base64()}\">[audio: ${mime}, ${audioBytes.size} bytes]</audio>\n"
                )
              } catch (e: Exception) {
                log.warn("Failed to encode audio for logging (mime=$mime, size=${audioBytes.size}): ${e.message}")
                sb.append("`[Audio: $mime, ${audioBytes.size} bytes - encode failed]`\n")
              }
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
    chatRequest: ModelSchema.ChatRequest, supportsSystemInstruction: Boolean = true
  ): GenerateContentConfig? {
    return try {
      val builder = GenerateContentConfig.builder()
      try {
        builder.temperature(chatRequest.temperature.toFloat())
      } catch (e: Exception) {
        log.warn("Failed to set temperature ${chatRequest.temperature}: ${e.message}")
      }
      chatRequest.max_tokens?.let {
        try {
          builder.maxOutputTokens(it)
        } catch (e: Exception) {
          log.warn("Failed to set maxOutputTokens $it: ${e.message}")
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
              log.warn("Unknown response modality '$modality'; ignoring")
              null
            }
          }
        }
        if (mapped.isNotEmpty()) {
          try {
            builder.responseModalities(mapped)
          } catch (e: Exception) {
            log.warn("Failed to set response modalities $mapped: ${e.message}", e)
          }
        }
      }
      // Configure speech (voice) settings for TTS output
      chatRequest.audio?.let { audioConfig ->
        try {
          val voiceName = audioConfig["voice"]
          if (voiceName != null) {
            val speechConfig = SpeechConfig.builder().voiceConfig(
              VoiceConfig.builder().prebuiltVoiceConfig(
                PrebuiltVoiceConfig.builder().voiceName(voiceName).build()
              ).build()
            ).build()
            builder.speechConfig(speechConfig)
          } else {
            log.debug("Audio config provided without 'voice' key; skipping speech config")
          }
        } catch (e: Exception) {
          log.warn("Failed to configure speech config for TTS (audioConfig=$audioConfig): ${e.message}", e)
        }
      }
      val systemMessages = chatRequest.messages.filter { it.role == ModelSchema.Role.system }
      if (systemMessages.isNotEmpty() && supportsSystemInstruction) {
        try {
          builder.systemInstruction(systemMessages.reduceOrNull { acc, message ->
            ModelSchema.ChatMessage(
              role = ModelSchema.Role.system, content = (acc.content ?: emptyList()) + (message.content ?: emptyList())
            )
          }?.let { reduceOrNull ->
            builder().role("system").parts(reduceOrNull.content?.map { it.part() } ?: listOf(fromText(""))).build()
          })
        } catch (e: Exception) {
          log.warn("Failed to set system instruction (${systemMessages.size} system messages): ${e.message}", e)
        }
      } else if (systemMessages.isNotEmpty()) {
        log.debug("Model does not support system instructions; ${systemMessages.size} system messages will be merged into user content")
      }
      builder.build()
    } catch (e: Exception) {
      log.error("Failed to build GenerateContentConfig: ${e.message}", e)
      null
    }
  }

  private fun convertToGeminiContents(
    messages: List<ModelSchema.ChatMessage>, supportsSystemInstruction: Boolean = true
  ): List<Content> {
    if (supportsSystemInstruction) {
      return messages.filter { it.role != ModelSchema.Role.system }.mapNotNull { it.toContent() }
    }
    // Model does not support system instructions: merge system messages
    // into the first user message (or prepend as a user message).
    val systemContent = messages.filter { it.role == ModelSchema.Role.system }.flatMap { it.content ?: emptyList() }
    val nonSystem = messages.filter { it.role != ModelSchema.Role.system }
    if (systemContent.isEmpty()) {
      return nonSystem.mapNotNull { it.toContent() }
    }
    val firstUserIdx = nonSystem.indexOfFirst { it.role == ModelSchema.Role.user }
    return if (firstUserIdx >= 0) {
      nonSystem.mapIndexedNotNull { idx, msg ->
        if (idx == firstUserIdx) {
          ModelSchema.ChatMessage(
            role = ModelSchema.Role.user, content = mergeTextParts(systemContent + (msg.content ?: emptyList()))
          ).toContent()
        } else {
          msg.toContent()
        }
      }
    } else {
      // No user message exists; prepend system content as a user message
      listOfNotNull(
        ModelSchema.ChatMessage(
          role = ModelSchema.Role.user, content = mergeTextParts(systemContent)
        ).toContent()
      ) + nonSystem.mapNotNull { it.toContent() }
    }
  }

  private fun ModelSchema.ChatMessage.toContent() = builder().role(
    when (this.role) {
      ModelSchema.Role.system -> "user" // Gemini does not have a system role, treat as user
      ModelSchema.Role.user -> "user"
      ModelSchema.Role.assistant -> "model"
      else -> "user"
    }
  ).parts(content?.flatMap { it.parts() } ?: listOf(fromText(""))).build()

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
      try {
        if (imageUrl?.startsWith("data:") == true) {
          // Base64 encoded image
          val parts = imageUrl.split(",")
          if (parts.size < 2) {
            log.warn("Malformed data URL (missing comma): ${imageUrl.take(80)}...")
            fromText("[malformed image data URL]")
          } else {
            val mimeType = parts[0].substringAfter("data:").substringBefore(";")
            val data = parts[1]
            val decoded = data.decodeBase64()?.toByteArray()
            if (decoded == null) {
              log.warn("Failed to base64-decode image data URL (mime=$mimeType, len=${data.length})")
              fromText("[invalid base64 image]")
            } else {
              Part.fromBytes(decoded, mimeType)
            }
          }
        } else if (imageUrl?.startsWith("gs://") == true) {
          // GCS URI
          Part.fromUri(imageUrl, "image/jpeg")
        } else if (imageUrl != null) {
          // Regular URL - convert to text description
          Part.fromUri(imageUrl, "image/jpeg")
        } else {
          log.warn("ContentPart.image_url is null in non-null branch")
          fromText("")
        }
      } catch (e: Exception) {
        log.warn("Failed to convert image_url to Part (url prefix='${imageUrl?.take(40)}'): ${e.message}", e)
        fromText("[image conversion failed: ${e.message}]")
      }
    }

    input_audio != null -> {
      // Handle audio input - Gemini supports audio via inline data
      try {
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
            log.debug("Using fallback MIME type for audio format '$format'")
            "audio/$format"
          }
        }
        val audioBytes = audio.audioBytes
        if (audioBytes.isNotEmpty()) {
          Part.fromBytes(audioBytes, mimeType)
        } else {
          log.warn("input_audio has empty audioBytes (format=$format)")
          fromText("")
        }
      } catch (e: Exception) {
        log.warn("Failed to convert input_audio to Part: ${e.message}", e)
        fromText("[audio conversion failed: ${e.message}]")
      }
    }


    text != null -> fromText(text)

    else -> fromText("")
  }

  fun ModelSchema.ContentPart.parts(): List<Part> = when {
    image_url != null && text != null -> listOfNotNull(
      copy(text = null).part(), copy(image_url = null).part()
    )

    input_audio != null && text != null -> listOfNotNull(
      copy(text = null).part(), copy(input_audio = null).part()
    )


    else -> listOfNotNull(
      this.part()
    )
  }

  private fun convertFromGeminiResponse(response: GenerateContentResponse): ModelSchema.ChatResponse {
    val choices = response.candidates().orElse(emptyList()).mapIndexed { index, candidate ->


      try {
        val content = candidate.content().orElse(null)
        val text = content?.parts()?.orElse(emptyList())?.mapNotNull {
          try {
            it.text().getOrNull()
          } catch (e: Exception) {
            log.warn("Failed to extract text from part in candidate $index: ${e.message}")
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
                }

                "audio/wav", "audio/mp3", "audio/mpeg", "audio/aiff", "audio/aac", "audio/ogg", "audio/flac", "audio/pcm", "audio/l16" -> {
                  val audioBytes = this.data().getOrNull()
                  if (audioBytes != null) {
                    val format = baseMime.substringAfter("audio/")
                    chatMessageResponse.audio_data = audioBytes
                    chatMessageResponse.audio_mime_type = rawMime
                    chatMessageResponse.audio_format = format
                    // Extract additional parameters from MIME type (e.g., rate, channels)
                    if (rawMime?.contains(";") == true) {
                      try {
                        val params = rawMime.substringAfter(";").split(";").mapNotNull { param ->
                          val kv = param.trim().split("=", limit = 2)
                          if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim() else null
                        }.toMap()
                        params["rate"]?.toIntOrNull()?.let { chatMessageResponse.audio_sample_rate = it }
                        params["channels"]?.toIntOrNull()?.let { chatMessageResponse.audio_channels = it }
                      } catch (e: Exception) {
                        log.warn("Failed to parse audio MIME parameters '$rawMime': ${e.message}")
                      }
                    }
                  } else {
                    log.warn("Audio inline data has null bytes (mime=$rawMime)")
                  }
                }

                else -> {
                  // Unsupported inline data type - ignore or log as needed
                  log.warn("Received unsupported inline data type in Gemini response: ${rawMime}")
                }
              }
            }
          } catch (e: Exception) {
            log.warn("Failed to process inline data part in candidate $index: ${e.message}", e)
          }
        }
        ModelSchema.ChatChoice(
          message = chatMessageResponse,
          index = index,
          finish_reason = candidate.finishReason().orElse(null)?.toString()
        )
      } catch (e: Exception) {
        log.error("Failed to convert candidate $index from Gemini response: ${e.message}", e)
        ModelSchema.ChatChoice(
          message = ModelSchema.ChatMessageResponse(content = "[response conversion error: ${e.message}]"),
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
      log.warn("Failed to extract usage metadata from Gemini response: ${e.message}", e)
      null
    }

    return ModelSchema.ChatResponse(
      choices = choices, usage = usage
    )
  }

  override fun authorize(request: HttpRequest, apiProvider: APIProvider) {
    log.error("authorize() called on GeminiSdkChatClient but is not implemented (apiProvider=${apiProvider})")
    throw UnsupportedOperationException(
      "authorize() is not implemented for GeminiSdkChatClient; authentication is handled via SDK client construction"
    )
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
        val constraints =
          com.fasterxml.jackson.core.StreamReadConstraints.builder().maxStringLength(256 * 1024 * 1024).build()
        com.fasterxml.jackson.core.StreamReadConstraints.overrideDefaultStreamReadConstraints(constraints)
        log.debug("Overrode Jackson StreamReadConstraints maxStringLength to 256MB for Gemini SDK")
      } catch (e: Throwable) {
        log.warn(
          "Failed to override Jackson StreamReadConstraints (large responses may fail to parse): ${e.message}",
          e
        )
      }
    }
  }
}


private fun ByteArray.base64() = java.util.Base64.getEncoder().encodeToString(this)