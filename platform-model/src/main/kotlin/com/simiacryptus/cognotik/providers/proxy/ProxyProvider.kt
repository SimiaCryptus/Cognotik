package com.simiacryptus.cognotik.providers.proxy

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.ChatClientInterface
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService


/**
 * Base class for client-side proxy providers. Forwards chat requests over HTTP
 * to a Cognotik server's ProxyChatServlet which will perform the upstream call.
 *
 * The proxy server URL is taken from the [base] field. Conventionally this
 * should point at the server's /api-proxy mount, e.g. "http://server:12891/api-proxy".
 *
 * One or more upstream provider names (e.g. "Anthropic", "Gemini") may be
 * declared; they are sent as a single comma-separated path segment so that
 * model listing and chat dispatch require no additional API calls per provider.
 * The upstream API key is sent in the X-API-Key header.
 */
abstract class ProxyProvider(
  name: String,
  proxyBase: String,
  /** The names of the upstream providers that the server should dispatch to. */
  vararg upstreamProviderNames: String
) : APIProvider(name, proxyBase) {
  /** Ordered, de-duplicated set of upstream providers handled by this proxy. */
  val upstreamProviderNames: Set<String> = upstreamProviderNames.toCollection(LinkedHashSet())

  /** Human readable label of all upstream providers, e.g. "Anthropic,Gemini". */
  val upstreamProviderName: String
    get() = this.upstreamProviderNames.joinToString(",")

  /** Single path segment addressing all upstream providers in one request. */
  val upstreamProviderPath: String
    get() = this.upstreamProviderNames.joinToString(",") { URLEncoder.encode(it, StandardCharsets.UTF_8) }

  private val log = LoggerFactory.getLogger(javaClass)
  private val mapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  init {
    if (proxyBase.isBlank()) {
      log.error("ProxyProvider '$name' initialized with blank proxy base URL")
      throw IllegalArgumentException("Proxy base URL cannot be blank for provider '$name'")
    }
    if (this.upstreamProviderNames.isEmpty()) {
      log.error("ProxyProvider '$name' initialized without any upstream provider names")
      throw IllegalArgumentException("At least one upstream provider name is required for provider '$name'")
    }
    if (this.upstreamProviderNames.any { it.isBlank() }) {
      log.error("ProxyProvider '$name' initialized with a blank upstream provider name")
      throw IllegalArgumentException("Upstream provider names cannot be blank for provider '$name'")
    }
    log.info(
      "Initialized ProxyProvider name='$name' base='$proxyBase' upstream={}",
      this.upstreamProviderNames
    )
  }

  override fun getChatClient(
    key: SecureString,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService,
    session: Session
  ): ChatClientInterface {
    log.debug("Creating ProxyChatClient for upstream='${upstreamProviderNames}' base='$base'")
    return try {
      ProxyChatClient(
        proxyBase = this.base,
        upstreamKey = key,
        upstreamProviderNames = this.upstreamProviderNames,
        mapper = mapper,
        session = session
      )
    } catch (e: IllegalArgumentException) {
      log.error("Failed to create ProxyChatClient for upstream='$upstreamProviderName': ${e.message}", e)
      throw e
    } catch (e: Exception) {
      log.error("Unexpected error creating ProxyChatClient for upstream='$upstreamProviderName'", e)
      throw ProxyProviderException(
        "Failed to create chat client for provider '$upstreamProviderName': ${e.message}",
        provider = upstreamProviderName,
        cause = e
      )
    }
  }

  override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> {
    /* All declared upstream providers are requested in a single call. */
    val urlString = "${base.trimEnd('/')}/models/$upstreamProviderPath"
    log.debug("Fetching chat models from proxy: $urlString")
    val url = try {
      URL(urlString)
    } catch (e: Exception) {
      log.error("Invalid proxy URL '$urlString': ${e.message}", e)
      throw ProxyProviderException(
        "Invalid proxy URL: $urlString",
        provider = upstreamProviderName,
        cause = e
      )
    }

    var conn: HttpURLConnection? = null
    try {
      conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "GET"
      conn.setRequestProperty("X-API-Key", key.toString())
      conn.setRequestProperty("Accept", "application/json")
      /* A cookie-resolution failure must not abort model discovery: the proxy can
         still authenticate via X-API-Key, so degrade gracefully instead of throwing. */
      val authCookies: Map<String, String?> = try {
        getAuthCookies(key)
      } catch (e: Exception) {
        log.warn(
          "Unable to resolve auth cookies for provider='$upstreamProviderName' (url=$urlString): ${e.message}",
          e
        )
        emptyMap()
      }
      conn.setCookies(authCookies)
      conn.connectTimeout = ProxyConfig.connectTimeoutMs
      conn.readTimeout = ProxyConfig.modelsReadTimeoutMs

      val code = conn.responseCode
      log.debug("Proxy models response code=$code for provider='$upstreamProviderName'")
      if (code !in 200..299) {
        val err = runCatching {
          conn.errorStream?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: ""
        log.warn("Proxy models request failed (provider='$upstreamProviderName', code=$code, url=$urlString): $err")
        return emptyList()
      }
      return conn.inputStream.use { input ->
        mapper.readValue(
          input,
          mapper.typeFactory.constructCollectionType(List::class.java, ChatModel::class.java)
        )
      }
    } catch (e: SocketTimeoutException) {
      log.warn("Timeout fetching models from proxy (provider='$upstreamProviderName', url=$urlString): ${e.message}")
      return emptyList()
    } catch (e: UnknownHostException) {
      log.error("Unknown host fetching models from proxy (provider='$upstreamProviderName', url=$urlString): ${e.message}")
      return emptyList()
    } catch (e: IOException) {
      log.error("I/O error fetching models from proxy (provider='$upstreamProviderName', url=$urlString)", e)
      return emptyList()
    } catch (e: Exception) {
      log.error("Unexpected error fetching models from proxy (provider='$upstreamProviderName', url=$urlString)", e)
      return emptyList()
    } finally {
      try {
        conn?.disconnect()
      } catch (e: Exception) {
        log.debug("Error disconnecting connection: ${e.message}")
      }
    }
  }

  abstract fun getAuthCookies(key: SecureString): Map<String, String?>

}