package com.simiacryptus.cognotik.platform.client

import com.simiacryptus.cognotik.platform.CognotikConfig.controllerEndpoint
import com.simiacryptus.cognotik.platform.service.UsageInterface
import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.toJson
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate

/**
 * HTTP client implementation of [UsageInterface], talking to
 * `com.simiacryptus.cognotik.webui.servlet.UsageStorageApiServlet` on a remote
 * ApplicationDirectory instance (mounted at `/usageStorageApi`).
 *
 * Intended for worker/CLI processes that need to record or query usage against a
 * central owner process rather than a local, file-backed [UsageInterface].
 *
 * @param baseUrl e.g. `http://host:port/usageStorageApi` (no trailing slash)
 */
open class UsageClient(
  private val baseUrl: String = controllerEndpoint + "/usageApi",
  private val httpClient: HttpClient = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build(),
) : UsageInterface {

  private fun get(
    action: String, params: Map<String, String?> = emptyMap(), auth: Map<String, String?> = emptyMap()
  ): String {
    val query = params.filterValues { it != null }
      .entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
    val uri = URI.create("$baseUrl/$action" + if (query.isNotEmpty()) "?$query" else "")
    log.info("GET {} - params={} authHeaderKeys={}", uri, params, auth.keys)
    val builder = HttpRequest.newBuilder(uri).GET()
    cookieHeader(auth)?.let { builder.header("Cookie", it) }
    val startTime = System.currentTimeMillis()
    val response = try {
      httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    } catch (e: Exception) {
      log.error("GET {} threw exception after {}ms", uri, System.currentTimeMillis() - startTime, e)
      throw e
    }
    val elapsed = System.currentTimeMillis() - startTime
    if (response.statusCode() in 200..299) {
      log.debug(
        "GET {} succeeded in {}ms - status={} bodyLength={}",
        uri,
        elapsed,
        response.statusCode(),
        response.body().length
      )
    } else {
      log.warn("GET {} failed in {}ms - status={} body={}", uri, elapsed, response.statusCode(), response.body())
    }
    check(response.statusCode() in 200..299) { "GET $uri failed: ${response.statusCode()} ${response.body()}" }
    val body = response.body()
    if (looksLikeHtml(body)) {
      throw IllegalStateException(
        "GET $uri returned an HTML page instead of JSON (likely an authentication redirect/expired session): " +
            mapOf(
              "auth" to auth.mapValues { it?.value?.truncate(5) },
            ).toJson()
      )
    }
    return body
  }

  private fun post(
    action: String, body: Any?, auth: Map<String, String?> = emptyMap()
  ): String {
    val uri = URI.create("$baseUrl/$action")
    val jsonBody = JsonUtil.toJson(body ?: emptyMap<String, Any>())
    log.info("POST {} - authHeaderKeys={} bodyLength={}", uri, auth.keys, jsonBody.length)
    if (log.isTraceEnabled) {
      log.trace("POST {} body={}", uri, jsonBody)
    }
    val builder = HttpRequest.newBuilder(uri)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
    cookieHeader(auth)?.let { builder.header("Cookie", it) }
    val startTime = System.currentTimeMillis()
    val response = try {
      httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    } catch (e: Exception) {
      log.error("POST {} threw exception after {}ms", uri, System.currentTimeMillis() - startTime, e)
      throw e
    }
    val elapsed = System.currentTimeMillis() - startTime
    if (response.statusCode() in 200..299) {
      log.debug(
        "POST {} succeeded in {}ms - status={} bodyLength={}",
        uri,
        elapsed,
        response.statusCode(),
        response.body().length
      )
    } else {
      log.warn("POST {} failed in {}ms - status={} body={}", uri, elapsed, response.statusCode(), response.body())
    }
    check(response.statusCode() in 200..299) { "POST $uri failed: ${response.statusCode()} ${response.body()}" }
    val responseBody = response.body()
    if (looksLikeHtml(responseBody)) {
      throw IllegalStateException(
        "POST $uri returned an HTML page instead of JSON (likely an authentication redirect/expired session): " +
            responseBody.take(200).replace("\n", " ")
      )
    }
    return responseBody
  }

  private fun looksLikeHtml(body: String): Boolean {
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)
  }
   /**
    * Executes [block], logging and swallowing any exception and returning [default] instead.
    * Preserves the thread interrupt flag if the failure was caused by an interruption.
    */
   private inline fun <T> guarded(operation: String, default: T, block: () -> T): T =
     try {
       block()
     } catch (e: InterruptedException) {
       Thread.currentThread().interrupt()
       log.error("UsageClient.{} interrupted; returning default value", operation, e)
       default
     } catch (e: Exception) {
       log.error("UsageClient.{} failed; returning default value", operation, e)
       default
     }


  open fun cookieHeader(auth: Map<String, String?>): String?  {
    val entries = auth.filterValues { !it.isNullOrEmpty() }
    if (entries.isEmpty()) return null
    return entries.entries.joinToString("; ") { (k, v) -> "$k=$v" }
  }

  override fun getUserUsageSummary(user: User, from: LocalDate, to: LocalDate): Map<String, ModelSchema.Usage> =
     guarded("getUserUsageSummary", emptyMap()) {
       JsonUtil.fromJson<UsageSummaryResponse>(
         get("userSummary", mapOf("from" to from.toString(), "to" to to.toString()), user.getAuthCookies()),
         UsageSummaryResponse::class.java
       ).summary
     }

   override fun getSessionUsageSummary(user: User, session: Session): Map<String, ModelSchema.Usage> =
     guarded("getSessionUsageSummary", emptyMap()) {
       requireNotNull(user) { "user is required" }
       log.debug("Fetching usage summary for session={} user={}", session.sessionId, user.toJson())
       JsonUtil.fromJson<UsageSummaryResponse>(
         get("sessionSummary", mapOf("sessionId" to session.sessionId), user.getAuthCookies()),
         UsageSummaryResponse::class.java
       ).summary
     }

  override fun getSessionUsageSummaryBulk(
    user: User,
    sessionIds: Collection<Session>
   ): Map<Session, Map<String, ModelSchema.Usage>> =
     guarded("getSessionUsageSummaryBulk", emptyMap()) {
       requireNotNull(user) { "user is required" }
       val resp = JsonUtil.fromJson<SessionSummaryBulkResponse>(
         post("sessionSummaryBulk", SessionSummaryBulkRequest(sessionIds.map { it.sessionId }), user.getAuthCookies()),
         SessionSummaryBulkResponse::class.java
       )
       resp.summary.mapKeys { Session(it.key) }
     }

  override fun incrementUsage(
    session: Session,
    user: User,
    model: AIModel,
    usage: ModelSchema.Usage,
    data: ModelSchema.UsageData?
  ) {
     guarded("incrementUsage", Unit) {
       post("increment", IncrementUsageRequest(session.sessionId, model, usage, data), user.getAuthCookies())
     }
  }

  override fun clear() {
     guarded("clear", Unit) {
       post("clear", null)
     }
  }

  override fun setParentSession(user: User, child: Session, parent: Session) {
     guarded("setParentSession", Unit) {
       log.info("Setting parent session: child={}, parent={}, user={}", child.sessionId, parent.sessionId, user)
       requireNotNull(user) { "user is required" }
       post("parentSession", ParentSessionRequest(child.sessionId, parent.sessionId), user.getAuthCookies())
     }
  }

   override fun getParentSession(user: User, child: Session): Session? =
     guarded<Session?>("getParentSession", null) {
       requireNotNull(user) { "user is required" }
       JsonUtil.fromJson<ParentSessionResponse>(
         get("parentSession", mapOf("child" to child.sessionId), user.getAuthCookies()),
         ParentSessionResponse::class.java
       ).parent?.let { Session(it) }
     }

  override fun getAvailableBudget(user: User): Double =
     guarded("getAvailableBudget", 0.0) {
       JsonUtil.fromJson<BudgetResponse>(
         get("budget", emptyMap(), user.getAuthCookies()),
         BudgetResponse::class.java
       ).budget
     }

  override fun creditUser(user: User, amount: Double, comment: String?, metadata: Map<String, String>?): Double =
     guarded("creditUser", 0.0) {
       JsonUtil.fromJson<CreditResponse>(
         post("credit", CreditRequest(amount, comment, metadata), user.getAuthCookies()),
         CreditResponse::class.java
       ).balance
     }

  override fun getUserDailyUsage(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.DailyUsage> =
     guarded("getUserDailyUsage", emptyList()) {
       JsonUtil.fromJson<DailyUsageResponse>(
         get("dailyUsage", mapOf("from" to from.toString(), "to" to to.toString()), user.getAuthCookies()),
         DailyUsageResponse::class.java
       ).entries
     }

  override fun getUserCredits(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.CreditEntry> =
     guarded("getUserCredits", emptyList()) {
       JsonUtil.fromJson<CreditsResponse>(
         get("credits", mapOf("from" to from.toString(), "to" to to.toString()), user.getAuthCookies()),
         CreditsResponse::class.java
       ).entries
     }

  override fun getUserBalance(user: User): Double =
     guarded("getUserBalance", 0.0) {
       JsonUtil.fromJson<BalanceResponse>(
         get("balance", emptyMap(), user.getAuthCookies()),
         BalanceResponse::class.java
       ).balance
     }

   override fun getSessionUsageRows(session: Session, user: User): List<UsageInterface.UsageRow> =
     guarded("getSessionUsageRows", emptyList()) {
       requireNotNull(user) { "user is required" }
       JsonUtil.fromJson<SessionRowsResponse>(
         get("sessionRows", mapOf("sessionId" to session.sessionId), user.getAuthCookies()),
         SessionRowsResponse::class.java
       ).rows
     }

  companion object {
    private val log = LoggerFactory.getLogger(UsageClient::class.java)
  }
}

fun String.truncate(i: Int) = if (this.length <= i) this else this.substring(0, i) + "..."