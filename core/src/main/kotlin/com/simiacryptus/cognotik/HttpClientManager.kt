package com.simiacryptus.cognotik

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.exceptions.*
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.io.SocketConfig
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.util.Timeout
import org.slf4j.Logger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.function.Function
import kotlin.math.pow

abstract class HttpClientManager(
  val logLevel: Level = Level.DEBUG,
  val logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  val workPool: ExecutorService,
  val scheduledPool: ListeningScheduledExecutorService,
  val onUsageListeners: MutableList<(model: LLMModel, tokens: ModelSchema.Usage) -> Unit> = mutableListOf(),
) {
  @Suppress("unused")
  val createdBy = Thread.currentThread().stackTrace

  /**
   * Called when API usage occurs to track tokens and costs
   * @param model The model that was used
   * @param tokens Usage information including token counts and cost
   */
  open fun onUsage(
    model: LLMModel,
    tokens: ModelSchema.Usage,
    logStreams: MutableList<BufferedOutputStream> = this.logStreams.toTypedArray().toMutableList(),
  ) {
    onUsageListeners.forEach { it(model, tokens) }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(HttpClientManager::class.java)

    private const val DEFAULT_USER_AGENT = "Cognotik/1.0"
    val client by lazy { createHttpClient(DEFAULT_USER_AGENT) }
    fun createHttpClient(userAgent: String = DEFAULT_USER_AGENT): CloseableHttpClient = HttpClientBuilder.create()
      .setRetryStrategy(
        DefaultHttpRequestRetryStrategy(
          /* maxRetries = */ 0,
          /* defaultRetryInterval = */ Timeout.ofSeconds(15)
        )
      )
      .setConnectionManager(with(PoolingHttpClientConnectionManager()) {
        defaultSocketConfig = with(SocketConfig.custom()) {
          setSoTimeout(Timeout.ofSeconds(3000))
          setSoReuseAddress(false)
          setSoKeepAlive(true)
          setDefaultConnectionConfig(ConnectionConfig.custom().apply {
            setConnectTimeout(Timeout.ofSeconds(30))
            setSocketTimeout(Timeout.ofSeconds(3000))
            setSoKeepAlive(true)
            setTimeToLive(Timeout.ofSeconds(6000))
          }.build())
          build()
        }
        defaultMaxPerRoute = 64
        maxTotal = 64
        this
      })
      .setUserAgent(userAgent)
      .setDefaultHeaders(listOf(BasicHeader(HttpHeaders.USER_AGENT, userAgent)))
      .build()

    val startTime by lazy { System.currentTimeMillis() }


    fun toString(exception: Throwable): String {
      val writer = StringWriter()
      exception.printStackTrace(PrintWriter(writer))
      return writer.toString()
    }
  }

  protected fun captureCallerStack(): String {
    var stack = Throwable().stackTrace
      .dropWhile { it.methodName == "withPool" || it.className.contains("HttpClientManager") }
      .joinToString("\n") { "\tat $it" }
    if (stackCalls.containsKey(Thread.currentThread())) {
      stack += "\n\tPrevious stack:\n${stackCalls[Thread.currentThread()]}"
    }
    return stack
  }

  val stackCalls: MutableMap<Thread, String> = ConcurrentHashMap()

  fun <T> withPerformanceLogging(logStreams: MutableList<BufferedOutputStream> = this.logStreams, fn: () -> T): T {
    val start = Date()
    try {
      return fn()
    } finally {
      log(Level.DEBUG, "Request completed in ${Date().time - start.time}ms", logStreams)
    }
  }

  protected open fun log(
    level: Level,
    msg: String,
    logStreams: MutableList<BufferedOutputStream> = this.logStreams,
    format: Boolean = true
  ) {
    val message = if (format) formatMessage(msg, level) else msg
    logFmt(message, logStreams)
    logSys(level, msg)
  }

  protected open fun log(
    msg: String,
    logStreams: MutableList<BufferedOutputStream> = this.logStreams,
    format: Boolean = true
  ) = log(logLevel, msg, logStreams, format)

  protected open fun formatMessage(msg: String, level: Level) =
    "\n* [$level] [${"%.3f".format((System.currentTimeMillis() - startTime) / 1000.0)}] ${
      (msg.takeIf { it.isNotBlank() } ?: "")
    }\n"

  protected open fun logSys(level: Level, message: String) {
    when (level) {
      Level.ERROR -> log.error(message)
      Level.WARN -> log.warn(message)
      Level.INFO -> log.info(message)
      Level.DEBUG -> log.debug(message)
      Level.TRACE -> log.trace(message)
    }
  }

  protected open fun logFmt(
    message: String,
    logStreams: MutableList<BufferedOutputStream>
  ) {
    logStreams.forEach { stream ->
      try {
        stream.write(message.toByteArray())
        stream.flush()
      } catch (e: Exception) {
        // Avoid logging errors in the logging mechanism itself
        System.err.println("Failed to write to log stream: ${e.message}")
      }
    }

  }

}