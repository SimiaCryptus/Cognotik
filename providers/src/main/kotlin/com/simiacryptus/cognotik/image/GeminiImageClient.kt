package com.simiacryptus.cognotik.image

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.genai.Client
import com.google.genai.types.GenerateImagesConfig
import com.google.genai.types.GenerateImagesResponse
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.*
import java.util.concurrent.ExecutorService

class GeminiImageClient(
  apiKey: SecureString,
  workPool: ExecutorService,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream>,
  scheduledPool: ListeningScheduledExecutorService,
  useVertexAI: Boolean = false,
  project: String? = null,
  location: String? = null,
) : HttpClientManager(
  logLevel = logLevel,
  logStreams = logStreams,
  workPool = workPool,
  scheduledPool = scheduledPool
), ImageClientInterface {

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

  override fun createImage(request: ImageGenerationRequest) = withPerformanceLogging {
    try {
      val config = buildGenerateImagesConfig(request)

      log(
        Level.DEBUG,
        "Sending image generation request to Gemini SDK for model: ${request.model}",
        logStreams
      )

      val response: GenerateImagesResponse =
        client.models.generateImages(request.model, request.prompt, config)

      val imageData = response.generatedImages().orElse(emptyList()).mapNotNull { generatedImage ->
        generatedImage.image().orElse(null)?.let { image ->
          // Convert the image to base64 or URL format
          val imageBytes = image.imageBytes().orElse(null)
          val imageUrl = image.gcsUri().orElse(null)

          ImageObject(
            url = imageUrl,
            b64_json = imageBytes?.let { Base64.getEncoder().encodeToString(it) }
          )
        }
      }

      val model = GeminiImageModels.values.values.find { it.modelId.equals(request.model, true) }
      val dims = request.size?.split("x")
      if (model != null) {
        onUsage(
          model, Usage(
            completion_tokens = imageData.size.toLong(),
          )
        )
      }

      ImageGenerationResponse(
        created = System.currentTimeMillis() / 1000,
        data = imageData
      )
    } catch (e: Exception) {
      log.error("Error during Gemini image generation", e)
      throw e
    }
  }

  private fun buildGenerateImagesConfig(request: ImageGenerationRequest): GenerateImagesConfig? {
    val builder = GenerateImagesConfig.builder()

    request.n?.let { builder.numberOfImages(it) }

    // Set output format based on response_format
    when (request.response_format) {
      "b64_json" -> builder.outputMimeType("image/jpeg")
      "url" -> builder.outputMimeType("image/jpeg")
      else -> builder.outputMimeType("image/jpeg")
    }

    // Include safety attributes
    builder.includeSafetyAttributes(true)

    // Parse size if provided
    request.size?.let { size ->
      val dims = size.split("x")
      if (dims.size == 2) {
        try {
          val width = dims[0].toInt()
          val height = dims[1].toInt()
          // Note: Gemini SDK may have specific size constraints
          // You may need to validate or adjust these values
        } catch (e: NumberFormatException) {
          log.warn("Invalid size format: $size")
        }
      }
    }

    return builder.build()
  }

  override fun getModels(): List<ImageModel>? {
    return try {
      GeminiImageModels.values.values.toList()
    } catch (e: Exception) {
      log.warn("Failed to fetch Gemini image models: ${e.message}")
      listOf()
    }
  }

  fun onUsage(model: ImageModel, usage: Usage) {
    // Override this method to track usage
  }


  companion object {
    private val log = LoggerFactory.getLogger(GeminiImageClient::class.java)
  }
}