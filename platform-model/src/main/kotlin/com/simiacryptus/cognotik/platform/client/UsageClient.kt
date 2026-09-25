package com.simiacryptus.cognotik.platform.client

import com.simiacryptus.cognotik.platform.UsageInterface
import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
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
class UsageClient(
  private val baseUrl: String = "https://hosted.cognotik.com/usageApi",
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
    auth.forEach { (k, v) -> builder.header(k, v) }
    val startTime = System.currentTimeMillis()
    val response = try {
      httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    } catch (e: Exception) {
      log.error("GET {} threw exception after {}ms", uri, System.currentTimeMillis() - startTime, e)
      throw e
    }
    val elapsed = System.currentTimeMillis() - startTime
    if (response.statusCode() in 200..299) {
      log.debug("GET {} succeeded in {}ms - status={} bodyLength={}", uri, elapsed, response.statusCode(), response.body().length)
    } else {
      log.warn("GET {} failed in {}ms - status={} body={}", uri, elapsed, response.statusCode(), response.body())
    }
    check(response.statusCode() in 200..299) { "GET $uri failed: ${response.statusCode()} ${response.body()}" }
    val body = response.body()
    if (looksLikeHtml(body)) {
      throw IllegalStateException(
        "GET $uri returned an HTML page instead of JSON (likely an authentication redirect/expired session): " +
          body.take(200).replace("\n", " ")
      )
    }
    return body
  }

  private fun post(action: String, body: Any?, auth: Map<String, String?> = emptyMap()
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
    auth.forEach { (k, v) -> builder.header(k, v) }
    val startTime = System.currentTimeMillis()
    val response = try {
      httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    } catch (e: Exception) {
      log.error("POST {} threw exception after {}ms", uri, System.currentTimeMillis() - startTime, e)
      throw e
    }
    val elapsed = System.currentTimeMillis() - startTime
    if (response.statusCode() in 200..299) {
      log.debug("POST {} succeeded in {}ms - status={} bodyLength={}", uri, elapsed, response.statusCode(), response.body().length)
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

  override fun getUserUsageSummary(user: User, from: LocalDate, to: LocalDate): Map<String, ModelSchema.Usage> =
    JsonUtil.fromJson<UsageSummaryResponse>(
      get("userSummary", mapOf("from" to from.toString(), "to" to to.toString()), user.getAuthCookies()),
      UsageSummaryResponse::class.java
    ).summary

  override fun getSessionUsageSummary(session: Session): Map<String, ModelSchema.Usage> =
    JsonUtil.fromJson<UsageSummaryResponse>(
      get("sessionSummary", mapOf("sessionId" to session.sessionId)),
      UsageSummaryResponse::class.java
    ).summary

  override fun getSessionUsageSummaryBulk(sessionIds: Collection<Session>): Map<Session, Map<String, ModelSchema.Usage>> {
    val resp = JsonUtil.fromJson<SessionSummaryBulkResponse>(
      post("sessionSummaryBulk", SessionSummaryBulkRequest(sessionIds.map { it.sessionId })),
      SessionSummaryBulkResponse::class.java
    )
    return resp.summary.mapKeys { Session(it.key) }
  }

  override fun incrementUsage(
    session: Session,
    user: User,
    model: AIModel,
    usage: ModelSchema.Usage,
    data: ModelSchema.UsageData?
  ) {
    post("increment", IncrementUsageRequest(session.sessionId, model, usage, data), user.getAuthCookies())
  }

  override fun clear() {
    post("clear", null)
  }

  override fun setParentSession(child: Session, parent: Session) {
    post("parentSession", ParentSessionRequest(child.sessionId, parent.sessionId))
  }

  override fun getParentSession(child: Session): Session? =
    JsonUtil.fromJson<ParentSessionResponse>(get("parentSession", mapOf("child" to child.sessionId)), ParentSessionResponse::class.java)
      .parent?.let { Session(it) }

  override fun getAvailableBudget(user: User): Double =
    JsonUtil.fromJson<BudgetResponse>(get("budget", emptyMap(), user.getAuthCookies()), BudgetResponse::class.java).budget

  override fun creditUser(user: User, amount: Double, comment: String?, metadata: Map<String, String>?): Double =
    JsonUtil.fromJson<CreditResponse>(post("credit", CreditRequest(amount, comment, metadata), user.getAuthCookies()), CreditResponse::class.java).balance

  override fun getUserDailyUsage(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.DailyUsage> =
    JsonUtil.fromJson<DailyUsageResponse>(
      get("dailyUsage", mapOf("from" to from.toString(), "to" to to.toString()), user.getAuthCookies()),
      DailyUsageResponse::class.java
    ).entries

  override fun getUserCredits(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.CreditEntry> =
    JsonUtil.fromJson<CreditsResponse>(
      get("credits", mapOf("from" to from.toString(), "to" to to.toString()), user.getAuthCookies()),
      CreditsResponse::class.java
    ).entries

  override fun getUserBalance(user: User): Double =
    JsonUtil.fromJson<BalanceResponse>(get("balance", emptyMap(), user.getAuthCookies()), BalanceResponse::class.java).balance

  override fun getSessionUsageRows(session: Session): List<UsageInterface.UsageRow> =
    JsonUtil.fromJson<SessionRowsResponse>(get("sessionRows", mapOf("sessionId" to session.sessionId)), SessionRowsResponse::class.java).rows

  companion object {
    private val log = LoggerFactory.getLogger(UsageClient::class.java)
  }
}