package com.simiacryptus.cognotik.platform.client

import com.simiacryptus.cognotik.platform.SessionMetadataInterface
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionListEntry
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * HTTP client implementation of [SessionMetadataInterface], talking to
 * `com.simiacryptus.cognotik.webui.servlet.MetadataStorageApiServlet` on a remote
 * ApplicationDirectory instance (mounted at `/metadataStorageApi`).
 *
 * All operations act on the identity established by [authHeaders]; there is no
 * support for querying another user's metadata through this client, matching the
 * server-side restriction.
 */
class SessionMetadataClient(
  private val baseUrl: String = "https://hosted.cognotik.com/sessionMetadata",
  private val httpClient: HttpClient = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build(),
) : SessionMetadataInterface {

  private fun get(
    action: String,
    params: Map<String, String?> = emptyMap(),
    auth: Map<String, String?> = emptyMap()
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

  private fun post(action: String, body: Any?, auth: Map<String, String?> = emptyMap()): String {
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

  override fun getSessionName(user: User?, session: Session): String =
    JsonUtil.fromJson<SessionNameResponse>(
      get(
        "sessionName",
        mapOf("sessionId" to session.sessionId),
        user?.getAuthCookies() ?: emptyMap()
      ), SessionNameResponse::class.java
    ).name

  override fun setSessionName(user: User?, session: Session, name: String) {
    post("setSessionName", SetSessionNameRequest(session.sessionId, name), user?.getAuthCookies() ?: emptyMap())
  }

  override fun getMessageIds(user: User?, session: Session): List<String> =
    JsonUtil.fromJson<MessageIdsResponse>(
      get(
        "messageIds",
        mapOf("sessionId" to session.sessionId),
        user?.getAuthCookies() ?: emptyMap()
      ), MessageIdsResponse::class.java
    ).ids

  override fun setMessageIds(user: User?, session: Session, ids: List<String>) {
    post("setMessageIds", SetMessageIdsRequest(session.sessionId, ids), user?.getAuthCookies() ?: emptyMap())
  }

  override fun getSessionTimestamp(user: User?, session: Session): Instant? =
    JsonUtil.fromJson<SessionTimestampResponse>(
      get("sessionTimestamp", mapOf("sessionId" to session.sessionId), user?.getAuthCookies() ?: emptyMap()),
      SessionTimestampResponse::class.java
    ).timestamp?.let { Instant.parse(it) }

  override fun setSessionTimestamp(user: User?, session: Session, time: Instant) {
    post(
      "setSessionTimestamp",
      SetSessionTimestampRequest(session.sessionId, time.toString()),
      user?.getAuthCookies() ?: emptyMap()
    )
  }

  override fun listSessionsByPath(path: String): List<String> =
    JsonUtil.fromJson<SessionIdsResponse>(
      get("sessionsByPath", mapOf("path" to path)),
      SessionIdsResponse::class.java
    ).sessionIds

  override fun listSessionsForUser(user: User): List<String> =
    JsonUtil.fromJson<SessionIdsResponse>(
      get("sessionsForUser", emptyMap(), user.getAuthCookies()),
      SessionIdsResponse::class.java
    ).sessionIds

  override fun getSessionOwner(session: Session): String? =
    JsonUtil.fromJson<SessionOwnerResponse>(
      get("sessionOwner", mapOf("sessionId" to session.sessionId)),
      SessionOwnerResponse::class.java
    ).ownerId

  override fun setSessionOwner(session: Session, ownerId: String?) {
    post("setSessionOwner", SetSessionOwnerRequest(session.sessionId, ownerId))
  }

  override fun getSessionWorker(session: Session): String? =
    JsonUtil.fromJson<SessionWorkerResponse>(
      get("sessionWorker", mapOf("sessionId" to session.sessionId)),
      SessionWorkerResponse::class.java
    ).workerId

  override fun setSessionWorker(session: Session, ownerId: String?) {
    post("setSessionWorker", SetSessionWorkerRequest(session.sessionId, ownerId))
  }

  override fun getSessionPath(user: User?, session: Session): String? =
    JsonUtil.fromJson<SessionPathResponse>(
      get(
        "sessionPath",
        mapOf("sessionId" to session.sessionId),
        user?.getAuthCookies() ?: emptyMap()
      ), SessionPathResponse::class.java
    ).path

  override fun setSessionPath(user: User?, session: Session, path: String?) {
    post("setSessionPath", SetSessionPathRequest(session.sessionId, path), user?.getAuthCookies() ?: emptyMap())
  }

  override fun exists(user: User?, session: Session): Boolean =
    JsonUtil.fromJson<ExistsResponse>(
      get(
        "exists",
        mapOf("sessionId" to session.sessionId),
        user?.getAuthCookies() ?: emptyMap()
      ), ExistsResponse::class.java
    ).exists

  override fun deleteSession(user: User?, session: Session) {
    post("deleteSession", DeleteSessionRequest(session.sessionId), user?.getAuthCookies() ?: emptyMap())
  }

  override fun deleteAllForUser(user: User): Int =
    JsonUtil.fromJson<DeleteCountResponse>(
      post("deleteAllForUser", null, user.getAuthCookies()),
      DeleteCountResponse::class.java
    ).deleted

  override fun getSessionMetadata(user: User?, session: Session): SessionMetadata =
    JsonUtil.fromJson(
      get(
        "sessionMetadata",
        mapOf("sessionId" to session.sessionId),
        user?.getAuthCookies() ?: emptyMap()
      ), SessionMetadata::class.java
    )

  override fun listSessionMetadata(user: User): List<SessionMetadata> =
    JsonUtil.fromJson<SessionMetadataListResponse>(
      get("listSessionMetadata", emptyMap(), user.getAuthCookies()),
      SessionMetadataListResponse::class.java
    ).items

  override fun listSessionMetadata(path: String): List<SessionMetadata> =
    JsonUtil.fromJson<SessionMetadataListResponse>(
      get("listSessionMetadataByPath", mapOf("path" to path)),
      SessionMetadataListResponse::class.java
    ).items

  override fun getSessionMetadataMap(user: User?, sessionIds: Collection<String>): Map<String, SessionMetadata> =
    JsonUtil.fromJson<SessionMetadataMapResponse>(
      post("sessionMetadataMap", SessionMetadataMapRequest(sessionIds.toList()), user?.getAuthCookies() ?: emptyMap()),
      SessionMetadataMapResponse::class.java
    ).items

  override fun listSessionEntries(user: User): List<SessionListEntry> =
    JsonUtil.fromJson<SessionListEntryListResponse>(
      get("listSessionEntries", emptyMap(), user.getAuthCookies()),
      SessionListEntryListResponse::class.java
    ).items

  override fun listSessionEntries(path: String): List<SessionListEntry> =
    JsonUtil.fromJson<SessionListEntryListResponse>(
      get("listSessionEntriesByPath", mapOf("path" to path)),
      SessionListEntryListResponse::class.java
    ).items

  companion object {
    private val log = LoggerFactory.getLogger(SessionMetadataClient::class.java)
  }
}