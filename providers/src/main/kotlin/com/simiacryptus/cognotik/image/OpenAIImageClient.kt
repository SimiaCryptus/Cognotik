package com.simiacryptus.cognotik.image

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.gson.Gson
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.ImageClientInterface
import com.simiacryptus.cognotik.platform.model.ImageModel
import com.simiacryptus.cognotik.platform.model.ModelSchema.*
import com.simiacryptus.cognotik.util.JsonUtil
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
  open val provider = CoreProviders.OpenAI

  open fun onUsage(model: AIModel?, tokens: Usage) {
  }

  @Throws(IOException::class, InterruptedException::class)
  protected fun post(url: String, json: String, apiProvider: APIProvider): String {
    val request = HttpPost(url)
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    log.info("Sending POST request to URL: $url with payload: $json")
    request.addHeader("Authorization", "Bearer ${key}")
    request.entity = StringEntity(json, Charsets.UTF_8, false)
    return post(request)
  }

  protected fun post(request: HttpPost): String =
    EntityUtils.toString(client.execute(request).entity)

  @Throws(IOException::class)
  protected operator fun get(url: String?, apiProvider: APIProvider): String {
    val request = HttpGet(url)
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    log.debug("Sending GET request to URL: $url")
    request.addHeader("Authorization", "Bearer ${key}")
    return EntityUtils.toString(client.execute(request).entity)
  }
  override fun createImage(request: ImageGenerationRequest): ImageGenerationResponse {
    return withPerformanceLogging {
      val url = "${apiBase}/images/generations"
      val httpRequest = HttpPost(url)
      httpRequest.addHeader("Accept", "application/json")
      httpRequest.addHeader("Content-Type", "application/json")
      httpRequest.addHeader("Authorization", "Bearer ${key}")
      val requestBody = Gson().toJson(request)
      httpRequest.entity = StringEntity(requestBody, Charsets.UTF_8, false)
      val response = post(httpRequest)
      checkError(response)
      log.info("Image creation response received")
      val model = OpenAIImageModels.values.values.find {
        it.modelId.equals(
          request.model,
          true
        )
      }
      onUsage(
        model, Usage(
          completion_tokens = 1,
        )
      )
      JsonUtil.objectMapper().readValue(
        response,
        ImageGenerationResponse::class.java
      )
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