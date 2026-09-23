package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.platform.ApiData
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.UserSettings
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.iterator

/**
 * Server-side servlet that handles proxied chat requests from ProxyProvider clients.
 *
 * Endpoints:
 *   POST /proxy/chat/{providerNames}                  - Start an async chat completion; returns a token
 *   GET  /proxy/chat/{providerNames}/result/{token}   - Poll for the result of a previously-started request
 *   GET  /proxy/models/{providerNames}                - List chat models
 *
 * `{providerNames}` may be a single provider or a comma separated list
 * (e.g. "anthropic,gemini"). For model listing the results of all providers are
 * aggregated into one response; for chat the request is routed to whichever of
 * the listed providers owns the requested model. Model lists are cached briefly
 * so that routing requires no additional upstream API calls.
 *
 * Authentication is handled via the "X-API-Key" header which contains the actual
 * upstream provider API key. The server uses this key to call the real provider.
 */
class ChatApiProxyServlet(
  private val metrics: ProxyMetrics = NoopProxyMetrics()
) : HttpServlet() {

  private val log = LoggerFactory.getLogger(ChatApiProxyServlet::class.java)
  private val mapper = ObjectMapper().registerKotlinModule()
  private val workPool = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
  private val scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(2))
  private val fileApplicationServices = ApplicationServicesImpl.fileApplicationServices()
  private val usageManager = fileApplicationServices.usageDB

  /**
   * Holds the state of an asynchronous chat request.
   */
  private data class AsyncJob(
    val token: String,
    val createdAt: Long = System.currentTimeMillis(),
    @Volatile var status: Status = Status.PENDING,
    @Volatile var response: ModelSchema.ChatResponse? = null,
    @Volatile var error: Throwable? = null,
    @Volatile var completedAt: Long? = null,
    @Volatile var retrievedAt: Long? = null
  ) {
    enum class Status { PENDING, COMPLETED, FAILED }
  }

  /**
   * In-memory store of pending/completed async jobs keyed by token.
   * Entries are evicted by [janitor] after expiration.
   */
  private val jobs = ConcurrentHashMap<String, AsyncJob>()

  /**
   * Maximum age, in ms, that a completed/failed job is retained for retrieval
   * after completion (whether retrieved or not).
   */
  private val resultRetentionMs = TimeUnit.MINUTES.toMillis(10)

  /**
   * Maximum age, in ms, that any job entry (including still-pending ones) is
   * allowed to live in the store before being forcibly evicted. Acts as a
   * safety net against leaked jobs.
   */
  private val jobMaxAgeMs = TimeUnit.MINUTES.toMillis(60)

  /**
   * Once a result has been retrieved by the client, retain it for only this
   * short window to allow retries on transient client failures, then evict.
   */
  private val postRetrievalGraceMs = TimeUnit.SECONDS.toMillis(30)

  /**
   * Cached per-provider/per-user model lists. Used both for the /models
   * endpoint and for routing a chat request to the owning provider without
   * issuing extra upstream API calls.
   */
  private data class CachedModels(val fetchedAt: Long, val models: List<ChatModel>)

  private val modelCache = ConcurrentHashMap<String, CachedModels>()
  private val modelCacheTtlMs = TimeUnit.MINUTES.toMillis(5)

  /** Result of locating the provider/model pair for a chat request. */
  private data class ResolvedModel(
    val provider: APIProvider,
    val client: ChatClientInterface,
    val model: ChatModel
  )


  init {
    scheduledPool.scheduleAtFixedRate({
      try {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        for ((token, job) in jobs) {
          val age = now - job.createdAt
          val completedAge = job.completedAt?.let { now - it } ?: 0L
          val retrievedAge = job.retrievedAt?.let { now - it } ?: 0L
          when {
            age > jobMaxAgeMs -> toRemove.add(token)
            job.status != AsyncJob.Status.PENDING && completedAge > resultRetentionMs -> toRemove.add(
              token
            )

            job.retrievedAt != null && retrievedAge > postRetrievalGraceMs -> toRemove.add(token)
          }
        }
        for (token in toRemove) {
          jobs.remove(token)
          log.debug("Evicted async job token={}", token)
          metrics.incrementCounter(ProxyMetrics.Companion.Names.CHAT_JOBS_EVICTED)
        }
        modelCache.entries.removeIf { (_, cached) -> now - cached.fetchedAt > modelCacheTtlMs }
        metrics.recordGauge(
          ProxyMetrics.Companion.Names.CHAT_ACTIVE_JOBS,
          jobs.size.toDouble()
        )
      } catch (e: Exception) {
        log.warn("Janitor task failed: {}", e.message, e)
      }
    }, 1, 1, TimeUnit.MINUTES)
  }

  /**
   * Custom exception types for better error categorization.
   */
  private class AuthenticationException(message: String) : RuntimeException(message)
  private class ProviderNotFoundException(message: String) : RuntimeException(message)
  private class ApiKeyNotConfiguredException(message: String) : RuntimeException(message)
  private class ModelNotFoundException(message: String) : RuntimeException(message)
  private class InvalidRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
  private class JobNotFoundException(message: String) : RuntimeException(message)
  private class InsufficientBudgetException(message: String) : RuntimeException(message)


  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val requestId = UUID.randomUUID().toString()
    MDC.put("requestId", requestId)
    MDC.put("method", "POST")
    MDC.put("remoteAddr", request.remoteAddr ?: "unknown")
    val startTime = System.currentTimeMillis()
    metrics.incrementCounter(
      ProxyMetrics.Companion.Names.REQUESTS,
      tags = mapOf(ProxyMetrics.Companion.Tags.HTTP_METHOD to "POST")
    )
    try {
      if (post(request, response, startTime)) return
    } catch (e: Exception) {
      handle(e, response, startTime)
    } finally {
      metrics.recordTiming(
        name = ProxyMetrics.Companion.Names.REQUEST_LATENCY,
        duration = System.currentTimeMillis() - startTime,
        tags = mapOf(
          ProxyMetrics.Companion.Tags.HTTP_METHOD to "POST",
          ProxyMetrics.Companion.Tags.HTTP_STATUS to response.status.toString()
        )
      )
      MDC.clear()
    }
  }


  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val requestId = UUID.randomUUID().toString()
    MDC.put("requestId", requestId)
    MDC.put("method", "GET")
    MDC.put("remoteAddr", request.remoteAddr ?: "unknown")
    val startTime = System.currentTimeMillis()
    metrics.incrementCounter(
      ProxyMetrics.Companion.Names.REQUESTS,
      tags = mapOf(ProxyMetrics.Companion.Tags.HTTP_METHOD to "GET")
    )
    try {
      if (get(request, response, startTime)) return
    } catch (e: Exception) {
      handle(e, response, startTime)
    } finally {
      metrics.recordTiming(
        name = ProxyMetrics.Companion.Names.REQUEST_LATENCY,
        duration = System.currentTimeMillis() - startTime,
        tags = mapOf(
          ProxyMetrics.Companion.Tags.HTTP_METHOD to "GET",
          ProxyMetrics.Companion.Tags.HTTP_STATUS to response.status.toString()
        )
      )
      MDC.clear()
    }
  }

  private fun handle(e: Exception, response: HttpServletResponse, startTime: Long) {
    metrics.incrementCounter(
      ProxyMetrics.Companion.Names.REQUEST_ERRORS,
      tags = mapOf(ProxyMetrics.Companion.Tags.ERROR_TYPE to e.javaClass.simpleName)
    )
    when (e) {
      is AuthenticationException -> {
        log.warn("Authentication failure: {}", e.message)
        writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, e)
      }

      is ProviderNotFoundException -> {
        log.warn("Provider not found: {}", e.message)
        writeErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, e)
      }

      is ApiKeyNotConfiguredException -> {
        log.warn("API key not configured: {}", e.message)
        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, e)
      }

      is ModelNotFoundException -> {
        log.warn("Model not found: {}", e.message)
        writeErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, e)
      }

      is InvalidRequestException -> {
        log.warn("Invalid request: {}", e.message)
        writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e)
      }

      is JobNotFoundException -> {
        log.warn("Job not found: {}", e.message)
        writeErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, e)
      }

      is InsufficientBudgetException -> {
        log.warn("Insufficient budget: {}", e.message)
        writeErrorResponse(response, 402 /* Payment Required */, e)
      }


      else -> {
        val elapsed = System.currentTimeMillis() - startTime
        log.error("Unhandled error processing proxy chat request after {}ms", elapsed, e)
        writeErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e)
      }
    }
  }

  /**
   * Handles POST /chat/{providerNames} - validates the request, selects the
   * provider owning the requested model and starts an asynchronous job.
   * Returns 202 Accepted with a token the client uses to poll.
   */
  private fun post(
    request: HttpServletRequest, response: HttpServletResponse, startTime: Long
  ): Boolean {
    val pathInfo = request.pathInfo ?: ""
    log.debug("Handling proxy chat POST request: pathInfo='{}'", pathInfo)
    val parts = pathInfo.trim('/').split('/')
    if (parts.size < 2 || parts[0] != "chat") {
      log.warn("Invalid path for chat endpoint: '{}'", pathInfo)
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("Not Found")
      return true
    }
    val providerNames = parseProviderNames(parts[1])
    if (providerNames.isEmpty()) throw InvalidRequestException("No provider specified in path: '$pathInfo'")
    val providerLabel = providerNames.joinToString(",")
    MDC.put("provider", providerLabel)
    val sessionId = request.getParameter("session")?.let { Session(it) }
      ?: throw InvalidRequestException("Missing required 'session' query parameter")
    MDC.put("sessionId", sessionId.toString())
    val providers = resolveProviders(providerNames)
    val chatRequest = try {
      mapper.readValue(request.inputStream, ModelSchema.ChatRequest::class.java)
    } catch (e: IOException) {
      throw InvalidRequestException("Failed to parse chat request body: ${e.message}", e)
    } catch (e: Exception) {
      throw InvalidRequestException("Invalid chat request format: ${e.message}", e)
    }
    val user = UserProviderImpl().authenticate(request, response)
      ?: throw AuthenticationException("Authentication failed for proxy chat request")
    MDC.put("user", user.email)
    val userSettings = getUserSettings(user, requiredBudget = 0.0)
    val model = chatRequest.model
    MDC.put("model", model ?: "null")
    if (model.isNullOrBlank()) throw InvalidRequestException("Missing required 'model' field in chat request")
    val resolveFailures = mutableListOf<Exception>()
    val resolved = resolveChatModel(providers, userSettings, sessionId, model, user, resolveFailures)
      ?: run {
        log.warn(
          "Model '{}' not found for any of the providers '{}' ({} provider failure(s))",
          model, providerLabel, resolveFailures.size
        )
        if (resolveFailures.isNotEmpty() && resolveFailures.all { it is ApiKeyNotConfiguredException }) {
          throw ApiKeyNotConfiguredException(
            "No usable API key configured for provider(s) $providerLabel: " +
                resolveFailures.joinToString("; ") { it.message ?: it.javaClass.simpleName })
        }
        throw ModelNotFoundException(
          "Model $model not found for provider(s) $providerLabel" +
              if (resolveFailures.isEmpty()) "" else " (provider errors: " +
                  resolveFailures.joinToString("; ") { "${it.javaClass.simpleName}: ${it.message}" } + ")"
        )
      }
    val provider = resolved.provider
    val providerName = provider.name
    val client = resolved.client
    val chatModel = resolved.model
    MDC.put("provider", providerName)

    val token = UUID.randomUUID().toString()
    val job = AsyncJob(token = token)
    jobs[token] = job
    log.info(
      "Queueing async chat job token={} for provider='{}', model='{}'",
      token, providerName, model
    )
    val jobTags = mapOf(
      ProxyMetrics.Companion.Tags.PROVIDER to providerName,
      ProxyMetrics.Companion.Tags.MODEL to (model ?: "unknown"),
      ProxyMetrics.Companion.Tags.USER to user.email
    )
    metrics.incrementCounter(ProxyMetrics.Companion.Names.CHAT_REQUESTS, tags = jobTags)
    metrics.incrementCounter(ProxyMetrics.Companion.Names.CHAT_QUEUED, tags = jobTags)
    metrics.recordGauge(
      ProxyMetrics.Companion.Names.CHAT_ACTIVE_JOBS,
      jobs.size.toDouble()
    )

    val requestIdSnapshot = MDC.get("requestId")
    workPool.submit {
      val previousMdc = try {
        MDC.getCopyOfContextMap()
      } catch (ignored: Exception) {
        null
      }
      try {
        MDC.put("requestId", requestIdSnapshot ?: token)
        MDC.put("provider", providerName)
        MDC.put("sessionId", sessionId.toString())
        MDC.put("model", model ?: "null")
        MDC.put("user", user.email)
        MDC.put("jobToken", token)
        val chatResponse = client.chat(
          chatRequest = chatRequest,
          model = chatModel,
          usageHandler = UsageListener.fn(sessionId) { m, usage, data ->
            usageManager.incrementUsage(sessionId, user, m, usage, data)
            try {
              val usageTags = mapOf(
                ProxyMetrics.Companion.Tags.PROVIDER to providerName,
                ProxyMetrics.Companion.Tags.MODEL to m.modelId,
                ProxyMetrics.Companion.Tags.USER to user.email
              )
              val prompt = usage.counts.getOrDefault(TokenTypes.Prompt, 0)
              val completion = usage.counts.getOrDefault(TokenTypes.Completion, 0)
              val total = (prompt + completion)
              if (prompt > 0) metrics.incrementCounter(
                ProxyMetrics.Companion.Names.TOKENS_PROMPT, prompt, usageTags
              )
              if (completion > 0) metrics.incrementCounter(
                ProxyMetrics.Companion.Names.TOKENS_COMPLETION, completion, usageTags
              )
              if (total > 0) metrics.incrementCounter(
                ProxyMetrics.Companion.Names.TOKENS_TOTAL, total, usageTags
              )
              val cost = usage.cost
              if (cost != null && cost > 0.0) {
                metrics.recordCost(
                  ProxyMetrics.Companion.Names.COST_USD, cost, usageTags
                )
              }
            } catch (metricsError: Exception) {
              log.debug("Failed to record usage metrics: {}", metricsError.message)
            }
            log.debug("Chat usage for model '{}': {}", m.modelId, usage)
          })
        job.response = chatResponse
        job.status = AsyncJob.Status.COMPLETED
        job.completedAt = System.currentTimeMillis()
        val duration = job.completedAt!! - job.createdAt
        metrics.incrementCounter(ProxyMetrics.Companion.Names.CHAT_COMPLETED, tags = jobTags)
        metrics.recordTiming(
          name = ProxyMetrics.Companion.Names.CHAT_JOB_DURATION,
          duration = duration,
          tags = jobTags + (ProxyMetrics.Companion.Tags.OUTCOME to "success")
        )
        log.info(
          "Async chat job token={} completed in {}ms",
          token, duration
        )
      } catch (e: Throwable) {
        job.error = e
        job.status = AsyncJob.Status.FAILED
        job.completedAt = System.currentTimeMillis()
        val duration = job.completedAt!! - job.createdAt
        metrics.incrementCounter(
          ProxyMetrics.Companion.Names.CHAT_FAILED,
          tags = jobTags + (ProxyMetrics.Companion.Tags.ERROR_TYPE to e.javaClass.simpleName)
        )
        metrics.recordTiming(
          name = ProxyMetrics.Companion.Names.CHAT_JOB_DURATION,
          duration = duration,
          tags = jobTags + mapOf(
            ProxyMetrics.Companion.Tags.OUTCOME to "error",
            ProxyMetrics.Companion.Tags.ERROR_TYPE to e.javaClass.simpleName
          )
        )
        log.error(
          "Async chat job token={} failed for provider='{}', model='{}'",
          token, providerName, model, e
        )
      } finally {
        try {
          if (previousMdc == null) MDC.clear() else MDC.setContextMap(previousMdc)
        } catch (ignored: Exception) {
          // MDC restoration must never mask the job outcome
        }
      }
    }

    response.status = HttpServletResponse.SC_ACCEPTED
    response.contentType = "application/json"
    mapper.writeValue(
      response.outputStream, mapOf(
        "token" to token,
        "status" to "pending"
      )
    )
    val elapsed = System.currentTimeMillis() - startTime
    metrics.recordTiming(
      name = ProxyMetrics.Companion.Names.CHAT_LATENCY,
      duration = elapsed,
      tags = jobTags
    )
    log.debug("Async chat request queued (token={}) in {}ms", token, elapsed)
    return false
  }

  private fun getAvailableModels(
    client: ChatClientInterface, providerName: String, user: User
  ): List<ChatModel> {
    cachedModels(providerName, user)?.let { return it }
    val models = try {
      client.getModels().filter { ALLOWED_MODELS.isEmpty() || ALLOWED_MODELS.contains(it) }
    } catch (e: Exception) {
      log.warn("Failed to retrieve models from provider '{}': {}", providerName, e.message, e)
      throw RuntimeException("Failed to retrieve models from $providerName: ${e.message}", e)
    }
    modelCache[modelCacheKey(providerName, user)] = CachedModels(System.currentTimeMillis(), models)
    return models
  }

  private fun modelCacheKey(providerName: String, user: User) = "${providerName.lowercase()}:${user.email}"

  private fun cachedModels(providerName: String, user: User): List<ChatModel>? =
    modelCache[modelCacheKey(providerName, user)]
      ?.takeIf { System.currentTimeMillis() - it.fetchedAt < modelCacheTtlMs }
      ?.models

  /**
   * Splits a (possibly comma separated) provider path segment into distinct,
   * URL-decoded provider names.
   */
  private fun parseProviderNames(segment: String): List<String> = segment
    .split(',')
    .map { URLDecoder.decode(it, StandardCharsets.UTF_8).trim() }
    .filter { it.isNotBlank() }
    .distinctBy { it.lowercase() }

  /**
   * Resolves the requested provider names, skipping (with a warning) any that
   * cannot be resolved. Only fails if *none* of them is known.
   */
  private fun resolveProviders(names: List<String>): List<APIProvider> {
    val resolved = names.mapNotNull { name ->
      resolveProvider(name) ?: run {
        log.warn("Ignoring unknown provider '{}'", name)
        null
      }
    }
    if (resolved.isEmpty()) throw ProviderNotFoundException("Unknown provider(s): ${names.joinToString(",")}")
    return resolved
  }

  /**
   * Picks the most informative failure to surface to the client: a real
   * upstream/runtime error is preferred over a "not configured" error.
   */
  private fun representativeFailure(failures: List<Exception>): Exception =
    failures.firstOrNull { it !is ApiKeyNotConfiguredException && it !is ProviderNotFoundException }
      ?: failures.first()

  private fun chatClientFor(
    provider: APIProvider, userSettings: UserSettings, session: Session
  ): ChatClientInterface {
    val apiKey = userSettings.apis.firstOrNull { it.provider?.name.equals(provider.name, ignoreCase = true) }
      ?: throw ApiKeyNotConfiguredException("No API key configured for provider ${provider.name}")
    val key = apiKey.key ?: throw ApiKeyNotConfiguredException("API key is null for provider ${provider.name}")
    return try {
      provider.getChatClient(
        key = key, workPool = workPool,
        scheduledPool = scheduledPool,
        session = session,
      )
    } catch (e: ApiKeyNotConfiguredException) {
      throw e
    } catch (e: Exception) {
      log.error("Failed to create chat client for provider '{}'", provider.name, e)
      throw RuntimeException("Failed to create chat client: ${e.message}", e)
    }
  }

  /**
   * Finds which of the requested [providers] serves [modelId]. Cached model
   * lists are consulted first so that in the common case (client already
   * listed models) no additional upstream API call is performed.
   */
  private fun resolveChatModel(
    providers: List<APIProvider>,
    userSettings: UserSettings,
    session: Session,
    modelId: String?,
    user: User,
    failures: MutableList<Exception> = mutableListOf()
  ): ResolvedModel? {
    if (modelId.isNullOrBlank()) return null

    for (provider in providers) {
      val match = cachedModels(provider.name, user)?.find { it.modelId == modelId } ?: continue
      val client = try {
        chatClientFor(provider, userSettings, session)
      } catch (e: Exception) {
        log.warn(
          "Cached model '{}' belongs to provider '{}' but no client could be created: {}",
          modelId, provider.name, e.message
        )
        failures.add(e)
        continue
      }
      log.debug("Resolved model '{}' to provider '{}' from cache", modelId, provider.name)
      return ResolvedModel(provider, client, match)
    }

    for (provider in providers) {
      val client = try {
        chatClientFor(provider, userSettings, session)
      } catch (e: Exception) {
        log.warn("Skipping provider '{}' while resolving model '{}': {}", provider.name, modelId, e.message)
        failures.add(e)
        continue
      }
      val models = try {
        getAvailableModels(client, provider.name, user)
      } catch (e: Exception) {
        log.warn("Failed listing models of provider '{}': {}", provider.name, e.message)
        failures.add(e)
        continue
      }
      val match = models.find { it.modelId == modelId } ?: continue
      log.debug("Resolved model '{}' to provider '{}'", modelId, provider.name)
      return ResolvedModel(provider, client, match)
    }
    return null
  }

  /**
   * Handles GET requests for both:
   *   - /models/{providerNames}                  -> list models (aggregated)
   *   - /chat/{providerNames}/result/{token}     -> poll async chat result
   */
  private fun get(
    request: HttpServletRequest, response: HttpServletResponse, startTime: Long
  ): Boolean {
    val pathInfo = request.pathInfo ?: ""
    log.debug("Handling proxy GET request: pathInfo='{}'", pathInfo)
    val parts = pathInfo.trim('/').split('/')
    if (parts.isEmpty()) {
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("Not Found")
      return true
    }
    return when (parts[0]) {
      "models" -> handleModels(parts, request, response, startTime)
      "chat" -> handleChatResult(parts, request, response, startTime)
      else -> {
        log.warn("Unknown GET endpoint: '{}'", pathInfo)
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.writer.write("Not Found")
        true
      }
    }
  }

  private fun handleModels(
    parts: List<String>,
    request: HttpServletRequest,
    response: HttpServletResponse,
    startTime: Long
  ): Boolean {
    if (parts.size < 2) {
      log.warn("Invalid path for models endpoint: '{}'", parts.joinToString("/"))
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("Not Found")
      return true
    }
    val providerNames = parseProviderNames(parts[1])
    if (providerNames.isEmpty()) {
      log.warn("No provider specified for models endpoint: '{}'", parts.joinToString("/"))
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("Not Found")
      return true
    }
    val providerLabel = providerNames.joinToString(",")
    MDC.put("provider", providerLabel)
    val sessionId = request.getParameter("session")?.let { Session(it) } ?: Session.newUserID()
    val providers = resolveProviders(providerNames)
    val user = UserProviderImpl().authenticate(request, response)
      ?: throw AuthenticationException("Authentication failed for proxy models request")
    MDC.put("user", user.email)
    val userSettings = getUserSettings(user, false, null)
    log.info("Retrieving chat models for provider(s) '{}'", providerLabel)
    val models = mutableListOf<ChatModel>()
    val seenModelIds = mutableSetOf<String>()
    val failures = mutableListOf<Exception>()
    for (provider in providers) {
      try {
        val chatClient = chatClientFor(provider, userSettings, sessionId)
        for (model in getAvailableModels(chatClient, provider.name, user)) {
          if (seenModelIds.add(model.modelId)) models.add(model)
        }
      } catch (e: Exception) {
        log.warn("Failed to list models for provider '{}': {}", provider.name, e.message)
        failures.add(e)
      }
    }
    if (models.isEmpty() && failures.isNotEmpty()) {
      log.warn(
        "No models available for provider(s) '{}'; {} failure(s): {}",
        providerLabel, failures.size, failures.joinToString("; ") { "${it.javaClass.simpleName}: ${it.message}" }
      )
      throw representativeFailure(failures)
    }
    if (failures.isNotEmpty()) {
      log.info(
        "Returning partial model list for '{}' ({} provider(s) failed)", providerLabel, failures.size
      )
    }
    response.status = HttpServletResponse.SC_OK
    response.contentType = "application/json"
    mapper.writeValue(response.outputStream, models)
    val elapsed = System.currentTimeMillis() - startTime
    val modelsTags = mapOf(
      ProxyMetrics.Companion.Tags.PROVIDER to providerLabel,
      ProxyMetrics.Companion.Tags.USER to user.email
    )
    metrics.incrementCounter(ProxyMetrics.Companion.Names.MODELS_REQUESTS, tags = modelsTags)
    metrics.recordTiming(
      name = ProxyMetrics.Companion.Names.MODELS_LATENCY,
      duration = elapsed,
      tags = modelsTags
    )
    metrics.recordHistogram(
      ProxyMetrics.Companion.Names.MODELS_RETURNED,
      models.size.toDouble(),
      modelsTags
    )
    log.info(
      "Proxy models request completed successfully in {}ms (returned {} models)", elapsed, models.size
    )
    return false
  }

  /**
   * Handles GET /chat/{providerNames}/result/{token}.
   * Returns 202 if still pending, 200 with the response if completed, 500 if
   * failed, or 404 if the token is unknown/expired.
   */
  private fun handleChatResult(
    parts: List<String>,
    request: HttpServletRequest,
    response: HttpServletResponse,
    startTime: Long
  ): Boolean {
    if (parts.size < 4 || parts[2] != "result") {
      log.warn("Invalid path for chat result endpoint: '{}'", parts.joinToString("/"))
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("Not Found")
      return true
    }
    val providerName = parseProviderNames(parts[1]).joinToString(",").ifBlank { parts[1] }
    val token = parts[3]
    MDC.put("provider", providerName)
    MDC.put("jobToken", token)

    val user = UserProviderImpl().authenticate(request, response)
      ?: throw AuthenticationException("Authentication failed for proxy chat result request")
    MDC.put("user", user.email)

    val job = jobs[token] ?: throw JobNotFoundException("Unknown or expired token: $token")

    response.contentType = "application/json"
    val pollTags = mapOf(
      ProxyMetrics.Companion.Tags.PROVIDER to providerName,
      ProxyMetrics.Companion.Tags.STATUS to job.status.name.lowercase()
    )
    metrics.incrementCounter(ProxyMetrics.Companion.Names.CHAT_POLL_REQUESTS, tags = pollTags)
    when (job.status) {
      AsyncJob.Status.PENDING -> {
        response.status = HttpServletResponse.SC_ACCEPTED
        mapper.writeValue(
          response.outputStream, mapOf(
            "token" to token,
            "status" to "pending"
          )
        )
        val elapsed = System.currentTimeMillis() - startTime
        log.debug("Poll for token={} still pending ({}ms)", token, elapsed)
      }

      AsyncJob.Status.COMPLETED -> {
        val resp = job.response
        if (resp == null) {
          log.error("Job token={} marked COMPLETED but response is null", token)
          response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          mapper.writeValue(
            response.outputStream, mapOf(
              "status" to "failed",
              "error" to "Internal error: completed job has no response"
            )
          )
        } else {
          response.status = HttpServletResponse.SC_OK
          mapper.writeValue(response.outputStream, resp)
        }
        if (job.retrievedAt == null) {
          job.retrievedAt = System.currentTimeMillis()
        }
        val elapsed = System.currentTimeMillis() - startTime
        log.info(
          "Poll for token={} returned COMPLETED in {}ms (job duration {}ms)",
          token, elapsed, (job.completedAt ?: 0L) - job.createdAt
        )
      }

      AsyncJob.Status.FAILED -> {
        val err = job.error
        response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        mapper.writeValue(
          response.outputStream, mapOf(
            "status" to "failed",
            "error" to (err?.message ?: "Unknown error"),
            "type" to (err?.javaClass?.simpleName ?: "Exception")
          )
        )
        if (job.retrievedAt == null) {
          job.retrievedAt = System.currentTimeMillis()
        }
        val elapsed = System.currentTimeMillis() - startTime
        log.info("Poll for token={} returned FAILED in {}ms", token, elapsed)
      }
    }
    return false
  }

  private fun getUserSettings(
    user: User, requiresBudget: Boolean = true, requiredBudget: Double?

  ): UserSettings {
    val availableBudget = try {
      usageManager.getAvailableBudget(user)
    } catch (e: Exception) {
      log.warn("Failed to read available budget for user '{}': {}", user.email, e.message, e)
      null
    }
    if (requiresBudget && (availableBudget == null || availableBudget <= 0.0)) {
      throw InsufficientBudgetException("No available budget for user ${user.email}")
    }
    val baseSettings = try {
      fileApplicationServices.userSettingsManager.getUserSettings(user)
    } catch (e: Exception) {
      log.error("Failed to load user settings for user '{}'", user.email, e)
      throw RuntimeException("Failed to load user settings: ${e.message}", e)
    }
    val apis = getApiSettings(user, requiredBudget, availableBudget)
    if (apis.isEmpty()) {
      log.warn("No provider API keys could be resolved for user '{}'", user.email)
    }
    return try {
      baseSettings.copy(apis = apis.toMutableList())
    } catch (e: Exception) {
      log.error("Failed to apply API settings for user '{}'", user.email, e)
      throw RuntimeException("Failed to load user settings: ${e.message}", e)
    }
  }

  private fun getApiSettings(
    user: User,
    requiredBudget: Double?,
    availableBudget: Double? = null
  ): List<ApiData> {
    val providers = try {
      APIProvider.values().toList()
    } catch (e: Exception) {
      log.error("Failed to enumerate API providers", e)
      emptyList()
    }
    return providers.mapNotNull { provider ->
      try {
        getKeyForProvider(user, provider, requiredBudget, availableBudget)?.let { apiKey ->
          ApiData(
            provider = provider, key = apiKey
          )
        }
      } catch (e: Exception) {

        /* A single mis-configured provider must never break the whole request. */
        log.warn(
          "Skipping provider '{}' for user '{}': {}",
          provider.name, user.email, e.message
        )
        null
      }
    }
  }

  private fun getKeyForProvider(
    user: User, provider: APIProvider, requiredBudget: Double?, availableBudget: Double? = null
  ): SecureString? {

    if (requiredBudget != null) {
      val budget = availableBudget ?: try {
        usageManager.getAvailableBudget(user)
      } catch (e: Exception) {
        log.warn("Failed to read available budget for user '{}': {}", user.email, e.message)
        null
      }
      if (budget == null || budget <= requiredBudget) {
        log.debug(
          "User '{}' has insufficient budget ({}), not providing API key for provider '{}'",
          user.email, budget, provider.name
        )
        return null
      }
    }
    val rawKey = envKeyFor(provider)
    if (rawKey == null) {
      log.debug("No API key present in the environment for provider '{}'", provider.name)
      return null
    }
    return try {
      SecureString(rawKey)
    } catch (e: Exception) {
      log.warn("Failed to wrap API key for provider '{}': {}", provider.name, e.message)
      null
    }
  }

  /**
   * Looks up the API key for [provider] in the environment. Returns null (never
   * throws / never NPEs) when the variable is missing or blank. In addition to
   * the well-known names a generic `<PROVIDER>_API_KEY` fallback is tried so new
   * providers work without a code change.
   */
  private fun envKeyFor(provider: APIProvider): String? {
    val candidates = LinkedHashSet<String>()
    when (provider.name.lowercase()) {
      "anthropic" -> candidates.add("ANTHROPIC_API_KEY")
      "gemini" -> candidates.add("GEMINI_API_KEY")
      "elevenlabs" -> candidates.add("ELEVENLABS_API_KEY")
      "groq" -> candidates.add("GROQ_API_KEY")
      "mistral" -> candidates.add("MISTRAL_API_KEY")
      "xai" -> candidates.add("XAI_API_KEY")
      "deepseek" -> candidates.add("DEEPSEEK_API_KEY")
    }
    candidates.add(provider.name.uppercase().replace(Regex("[^A-Z0-9]"), "_") + "_API_KEY")
    return candidates.firstNotNullOfOrNull { name ->
      try {
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
      } catch (e: SecurityException) {
        log.warn("Not permitted to read environment variable '{}': {}", name, e.message)
        null
      }
    }
  }

  /**
   * Writes a structured JSON error response.
   * Falls back gracefully if writing the response itself fails.
   */
  private fun writeErrorResponse(response: HttpServletResponse, statusCode: Int, e: Exception) {
    try {
      if (response.isCommitted) {
        log.warn("Response already committed; cannot write error response for: {}", e.message)
        return
      }
      response.reset()
      response.status = statusCode
      response.contentType = "application/json"
      mapper.writeValue(
        response.outputStream, mapOf(
          "error" to (e.message ?: e.javaClass.simpleName),
          "type" to e.javaClass.simpleName,
          "status" to statusCode
        )
      )
    } catch (writeError: Exception) {
      log.error("Failed to write error response (original error: {})", e.message, writeError)
    }
  }

  private fun resolveProvider(name: String): APIProvider? = try {
    val provider = APIProvider.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
    if (provider == null) {
      log.debug("No provider found matching name '{}'", name)
    }
    provider
  } catch (e: Exception) {
    log.warn("Failed to resolve provider '{}': {}", name, e.message, e)
    null
  }


  /**
   * Backend-agnostic metrics interface for the proxy.
   *
   * Designed to map cleanly onto common metrics backends such as:
   *   - Datadog (StatsD/DogStatsD): counters, gauges, histograms, distributions with tags
   *   - CloudWatch: metrics with dimensions and units
   *   - Prometheus / Micrometer: counters, gauges, timers with labels
   *   - OpenTelemetry metrics
   *
   * Tags are expressed as a map of key/value pairs. Implementations are expected
   * to translate these into their backend's native concept (Datadog tags,
   * CloudWatch dimensions, Prometheus labels, etc).
   *
   * All methods MUST be safe to call from multiple threads and MUST NOT throw;
   * implementations should swallow/log internal errors so that metric emission
   * never impacts request processing.
   */
  interface ProxyMetrics {

    /**
     * Increment a monotonically-increasing counter.
     *
     * @param name   The metric name, e.g. "proxy.chat.requests".
     * @param value  Amount to increment by (default 1). Must be >= 0.
     * @param tags   Dimensional tags / labels.
     */
    fun incrementCounter(name: String, value: Long = 1, tags: Map<String, String> = emptyMap())

    /**
     * Record an instantaneous gauge value (e.g. queue depth, active jobs).
     */
    fun recordGauge(name: String, value: Double, tags: Map<String, String> = emptyMap())

    /**
     * Record a value in a histogram / distribution (e.g. tokens used, request size).
     * Use [recordTiming] for elapsed durations.
     */
    fun recordHistogram(name: String, value: Double, tags: Map<String, String> = emptyMap())

    /**
     * Record a timing / latency measurement.
     *
     * @param name      Metric name, e.g. "proxy.chat.latency".
     * @param duration  Numeric duration value in [unit].
     * @param unit      Time unit of [duration]. Implementations should normalize
     *                  internally (commonly to milliseconds).
     * @param tags      Dimensional tags / labels.
     */
    fun recordTiming(
      name: String,
      duration: Long,
      unit: TimeUnit = TimeUnit.MILLISECONDS,
      tags: Map<String, String> = emptyMap()
    )

    /**
     * Record a monetary cost in USD (or implementation-defined currency).
     * Separate from generic histograms so backends can attach currency units / aggregations.
     */
    fun recordCost(name: String, amountUsd: Double, tags: Map<String, String> = emptyMap()) {
      recordHistogram(name, amountUsd, tags + ("unit" to "usd"))
    }

    /**
     * Convenience: time a block of code, recording its duration on success/failure
     * and propagating exceptions.
     */
    fun <T> time(name: String, tags: Map<String, String> = emptyMap(), block: () -> T): T {
      val start = System.nanoTime()
      var success = false
      return try {
        val result = block()
        success = true
        result
      } finally {
        val elapsedNanos = System.nanoTime() - start
        recordTiming(
          name = name,
          duration = TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
          unit = TimeUnit.MILLISECONDS,
          tags = tags + ("outcome" to if (success) "success" else "error")
        )
      }
    }

    companion object {
      /**
       * Standard tag keys used throughout the proxy. Centralizing these avoids
       * tag-name drift between call sites and makes dashboards/alerts portable
       * across metrics backends.
       */
      object Tags {
        const val PROVIDER = "provider"
        const val MODEL = "model"
        const val USER = "user"
        const val STATUS = "status"
        const val OUTCOME = "outcome"
        const val ERROR_TYPE = "error_type"
        const val HTTP_METHOD = "http_method"
        const val HTTP_STATUS = "http_status"
      }

      /**
       * Standard metric names. Keeping these in one place ensures consistent
       * naming conventions (dot-delimited, lowercase) suitable for Datadog,
       * Prometheus (after translation), and CloudWatch.
       */
      object Names {
        const val REQUESTS = "proxy.requests"
        const val REQUEST_LATENCY = "proxy.request.latency"
        const val REQUEST_ERRORS = "proxy.request.errors"

        const val CHAT_REQUESTS = "proxy.chat.requests"
        const val CHAT_LATENCY = "proxy.chat.latency"
        const val CHAT_QUEUED = "proxy.chat.queued"
        const val CHAT_COMPLETED = "proxy.chat.completed"
        const val CHAT_FAILED = "proxy.chat.failed"
        const val CHAT_JOB_DURATION = "proxy.chat.job.duration"
        const val CHAT_POLL_REQUESTS = "proxy.chat.poll.requests"
        const val CHAT_ACTIVE_JOBS = "proxy.chat.jobs.active"
        const val CHAT_JOBS_EVICTED = "proxy.chat.jobs.evicted"

        const val MODELS_REQUESTS = "proxy.models.requests"
        const val MODELS_LATENCY = "proxy.models.latency"
        const val MODELS_RETURNED = "proxy.models.returned"

        const val TOKENS_PROMPT = "proxy.tokens.prompt"
        const val TOKENS_COMPLETION = "proxy.tokens.completion"
        const val TOKENS_TOTAL = "proxy.tokens.total"

        const val COST_USD = "proxy.cost.usd"
      }
    }
  }

  /**
   * No-op standin implementation. Safe default that performs no I/O.
   *
   * Emits TRACE-level log lines so developers can verify wiring during local
   * debugging without configuring a real backend.
   */
  class NoopProxyMetrics : ProxyMetrics {
    private val log = LoggerFactory.getLogger(NoopProxyMetrics::class.java)

    override fun incrementCounter(name: String, value: Long, tags: Map<String, String>) {
      if (log.isTraceEnabled) log.trace("counter {} += {} tags={}", name, value, tags)
    }

    override fun recordGauge(name: String, value: Double, tags: Map<String, String>) {
      if (log.isTraceEnabled) log.trace("gauge {} = {} tags={}", name, value, tags)
    }

    override fun recordHistogram(name: String, value: Double, tags: Map<String, String>) {
      if (log.isTraceEnabled) log.trace("histogram {} = {} tags={}", name, value, tags)
    }

    override fun recordTiming(name: String, duration: Long, unit: TimeUnit, tags: Map<String, String>) {
      if (log.isTraceEnabled) log.trace("timing {} = {} {} tags={}", name, duration, unit, tags)
    }
  }

  companion object {
    var ALLOWED_MODELS: MutableSet<ChatModel> = mutableSetOf()
  }

}