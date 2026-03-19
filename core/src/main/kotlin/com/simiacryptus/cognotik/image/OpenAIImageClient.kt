package com.simiacryptus.cognotik.image

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.gson.Gson
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.Logger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService

open class OpenAIImageClient(
  protected var key: String,
  protected val apiBase: String,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  workPool: ExecutorService,
  scheduledPool: ListeningScheduledExecutorService,
) : HttpClientManager(
  logLevel = logLevel,
  logStreams = logStreams,
  workPool = workPool,
  scheduledPool = scheduledPool
), ImageClientInterface {

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

  override fun createImage(request: ImageGenerationRequest): ImageGenerationResponse = withReliability {
    withPerformanceLogging {
      val url = "${apiBase}/images/generations"
      val httpRequest = HttpPost(url)
      httpRequest.addHeader("Accept", "application/json")
      httpRequest.addHeader("Content-Type", "application/json")
      provider.authorize(httpRequest, key, apiBase)
      val requestBody = Gson().toJson(request)
      httpRequest.entity = StringEntity(requestBody, Charsets.UTF_8, false)
      val response = post(httpRequest)
      checkError(response)
      log.info("Image creation response received")
      val model = OpenAIImageModels.values.values.find { it.modelId.equals(request.model, true) }
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

  override fun getModels(): List<ImageModel>? {
    return try {
      OpenAIImageModels.values.values.toList()
    } catch (e: Exception) {
      log.error("Failed to fetch image models", e)
      null
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(OpenAIImageClient::class.java)
  }
}