package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.exceptions.ErrorUtil
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.Attributes
import com.simiacryptus.cognotik.platform.model.ChatClientInterface
import com.simiacryptus.cognotik.platform.model.EventType
import com.simiacryptus.cognotik.platform.model.MetricAttribute
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.service.MetricsInterface
import com.simiacryptus.cognotik.platform.service.NoOpMetrics
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.toJson
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.HttpEntity
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService

abstract class ChatClientBase(
    protected val provider: APIProvider,
    val apiKey: SecureString = SecureString(""), // Default to empty, but should be provided by subclasses
    val apiBase: String = provider.base,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
    override val session: Session
) : HttpClientManager(
  logLevel = logLevel, logStreams = logStreams, workPool = workPool, scheduledPool = scheduledPool
), ChatClientInterface {

  protected fun get(url: String) = client.execute(HttpGet(url).let {
    authorize(it)
    it
  }).use { response ->
    val responseBody = response.entity?.content?.bufferedReader()?.readText() ?: ""
    log(Level.DEBUG, "GET $url -> ${response.code}: $responseBody", logStreams)
    responseBody
  }


  var user: Any? = null
   /** Metrics sink for exception events. Override, or set [metricsProvider] globally. */
   protected open val metrics: MetricsInterface get() = metricsProvider()
   /**
    * Reports [e] as an [EventType.AI_ERROR] event (which also increments the AI_ERRORS counter).
    * Never throws: metrics failures must not break requests.
    */
   protected open fun reportException(e: Throwable, requestID: String? = null, model: String? = null) {
     try {
       metrics.event(
         EventType.AI_ERROR,
         Attributes.of(
           MetricAttribute.PROVIDER(provider.toString()),
           model?.let { MetricAttribute.MODEL(it) },
           MetricAttribute.ERROR_TYPE(ErrorUtil.errorType(e)),
           MetricAttribute.FATAL(ErrorUtil.isFatal(e)),
           user?.toString()?.let { MetricAttribute.USER(it) },
           MetricAttribute.SESSION(session.toString()),
         )
       )
     } catch (t: Throwable) {
       log.debug("Failed to report exception metric for request $requestID", t)
     }
   }


  @Throws(IOException::class, InterruptedException::class)
  fun post(
    url: String,
    json: String,
    requestID: String = UUID.randomUUID().toString(),
     logStreams: MutableList<BufferedOutputStream> = this.logStreams,
     model: String? = null
  ): String {
    validatePostRequest(url, json)
    val request = HttpPost(url)
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    authorize(request)
    request.entity = StringEntity(json, Charsets.UTF_8, false)
     return post(request, requestID = requestID, logStreams = logStreams, model = model)
  }

  private fun validatePostRequest(url: String, json: String) {
    require(url.isNotBlank()) { "URL cannot be blank" }
    require(json.isNotBlank()) { "JSON payload cannot be blank" }
    require(url.startsWith("http")) { "URL must be a valid HTTP/HTTPS URL: $url" }
  }
  protected open fun authorize(request: HttpRequest) {
    // Default implementation does nothing, subclasses can override to add headers or other auth
  }

  fun post(
    request: HttpPost,
    requestID: String = UUID.randomUUID().toString(),
     logStreams: MutableList<BufferedOutputStream> = this.logStreams,
     model: String? = null
  ): String = try {
    log(
      level = Level.DEBUG,
      msg = String.format(
        "<details><summary>POST %s\nID:%s</summary>\nPrefix:\n\n```json\n%s\n```\n\n```\n%s\n```\n</details>",
        request.uri,
        requestID,
        request.entity.formatEntityForLogging(),
        captureCallerStack().indent("  ")
      ),
      logStreams
    )
    val response =
      innerPost(client, request) ?: throw IOException("Empty response from POST request to ${request.uri}")
    log(
      level = Level.DEBUG,
      msg = String.format(
        "<details><summary>POST %s\nID:%s</summary>\nResponse:\n\n```\n%s\n```\n</details>",
        request.uri,
        requestID,
        response.let {
          try {
            fromJson<Map<String, Any>>(it, Map::class.java).toJson()
          } catch (e: Exception) {
            it
          }
        }.indent("  ")
      ),
      logStreams
    )
     // Report API-level errors embedded in the response body without changing behaviour:
     // callers still run ErrorUtil.checkError themselves and receive the exception there.
     try {
       ErrorUtil.checkError(response)
     } catch (e: Exception) {
       if (!ErrorUtil.isUnparseableResponse(e)) reportException(e, requestID, model)
     }
    response
  } catch (e: Exception) {
     reportException(e, requestID, model)
    log(
      Level.ERROR,
      "Error during POST request to ${request.uri}\nID:$requestID\nRequest Entity:\n${request.entity.formatEntityForLogging()}",
      logStreams
    )
    log.error("Failed to execute POST request to ${request.uri}", e)
    throw e
  }

  protected open fun innerPost(
    client: CloseableHttpClient,
    request: HttpPost
  ): String? {
    val response = client.execute(request)
    val entity = response.entity
    return if (entity != null) {
      val responseBody = EntityUtils.toString(entity)
      if (responseBody.isBlank()) {
        throw IOException("Empty response body")
      }
      responseBody
    } else {
      throw IOException("Empty response entity")
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(ChatClientBase::class.java)
     /** Global metrics resolver used by [metrics]; wire to the configured backend at startup. */
     @JvmStatic
     @Volatile
     var metricsProvider: () -> MetricsInterface = { NoOpMetrics }
  }
}

fun HttpEntity?.formatEntityForLogging() = try {
  EntityUtils.toString(this)?.indent("  ").orEmpty()
} catch (e: Exception) {
  "[Unable to format entity for logging]: $e"
}