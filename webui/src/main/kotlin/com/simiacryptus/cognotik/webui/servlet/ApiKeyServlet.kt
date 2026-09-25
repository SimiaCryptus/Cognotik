package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl.Companion.fileApplicationServices
import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.platform.AuthenticationInterface.TokenMetadata
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * UI + JSON API for management of the access tokens held by [AuthenticationInterface].
 *
 * Endpoints (all require an already-authenticated session):
 *
 *  - `GET  /api/keys`                          -> HTML page, or JSON when `Accept: application/json`/`?format=json`
 *  - `POST /api/keys?action=create[&ttl=<s>]`  -> issue a new token (returned in full exactly once)
 *  - `POST /api/keys?action=revoke&token=<t>`  -> revoke a single token owned by the caller
 *  - `POST /api/keys?action=revoke-all`        -> revoke every session for the caller
 *  - `DELETE /api/keys?token=<t>`              -> JSON alias for `action=revoke`
 *
 * Mutating requests must carry the caller's CSRF token (`csrf` parameter or `X-CSRF-Token`
 * header); the value is rendered into the HTML forms and returned by the JSON `GET`.
 */
class ApiKeyServlet : HttpServlet() {

  private val services by lazy { fileApplicationServices() }
  private val authenticationManager: AuthenticationInterface by lazy { services.authenticationManager }
  private val userProvider by lazy { UserProviderImpl() }
  private val mapper = ObjectMapper()
  private val random = SecureRandom()

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: return
    if (wantsJson(request)) {
      writeJson(
        response, HttpServletResponse.SC_OK, mapOf(
          "user" to userJson(user),
          "csrf" to user.signature,
          "tokens" to tokens(user).map(::tokenJson)
        )
      )
    } else {
      renderPage(response, user, newToken = null, notice = null, error = null)
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: return
    if (!csrfOk(request, user)) {
      fail(request, response, user, HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token")
      return
    }
    when (val action = request.getParameter("action")?.trim()?.lowercase() ?: "create") {
      "create", "new", "issue" -> create(request, response, user)
      "revoke", "delete" -> revoke(request, response, user)
      "revoke-all", "revokeall" -> revokeAll(request, response, user)
      else -> fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: $action")
    }
  }

  /** JSON-only alias for `action=revoke`. */
  override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: return
    if (!csrfOk(request, user)) {
      writeJson(response, HttpServletResponse.SC_FORBIDDEN, mapOf("error" to "Invalid or missing CSRF token"))
      return
    }
    val token = request.getParameter("token")
    if (token.isNullOrBlank()) {
      writeJson(response, HttpServletResponse.SC_BAD_REQUEST, mapOf("error" to "Missing 'token' parameter"))
      return
    }
    val revoked = tryLogout(token, user)
    if (revoked == null) {
      writeJson(
        response, HttpServletResponse.SC_NOT_IMPLEMENTED,
        mapOf("error" to "Revocation is not supported by ${authenticationManager.javaClass.simpleName}")
      )
    } else {
      writeJson(response, if (revoked) HttpServletResponse.SC_OK else HttpServletResponse.SC_NOT_FOUND,
        mapOf("revoked" to revoked))
    }
  }

  private fun create(request: HttpServletRequest, response: HttpServletResponse, user: User) {
    val ttl = parseTtl(request.getParameter("ttl"))
    val token = generateToken()
    try {
      authenticationManager.putUser(token, user, ttl)
    } catch (e: Exception) {
      log.warn("Failed to issue access token for {}", user, e)
      fail(request, response, user, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to issue token")
      return
    }
    log.info("Issued access token {} for user {} (ttl={})", mask(token), user, ttl ?: "default")
    if (wantsJson(request)) {
      writeJson(
        response, HttpServletResponse.SC_CREATED, mapOf(
          "token" to token,
          "expiresInSeconds" to ttl?.seconds,
          "warning" to "This value is shown only once; store it securely."
        )
      )
    } else {
      renderPage(
        response, user, newToken = token,
        notice = "New access token created - copy it now, it will not be shown again.", error = null
      )
    }
  }

  private fun revoke(request: HttpServletRequest, response: HttpServletResponse, user: User) {
    val token = request.getParameter("token")
    if (token.isNullOrBlank()) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Missing 'token' parameter")
      return
    }
    when (tryLogout(token, user)) {
      null -> fail(
        request, response, user, HttpServletResponse.SC_NOT_IMPLEMENTED,
        "Revocation is not supported by ${authenticationManager.javaClass.simpleName}"
      )

      true -> {
        log.info("Revoked access token {} for user {}", mask(token), user)
        if (wantsJson(request)) writeJson(response, HttpServletResponse.SC_OK, mapOf("revoked" to true))
        else renderPage(response, user, null, notice = "Token ${mask(token)} revoked.", error = null)
      }

      false -> fail(request, response, user, HttpServletResponse.SC_NOT_FOUND, "Unknown or already expired token")
    }
  }

  private fun revokeAll(request: HttpServletRequest, response: HttpServletResponse, user: User) {
    val count = try {
      authenticationManager.revokeAll(user)
    } catch (e: UnsupportedOperationException) {
      // Fall back to revoking the enumerable sessions one at a time.
      tokens(user).count { tryLogout(it.token, user) == true }
    }
    log.info("Revoked {} session(s) for user {}", count, user)
    if (wantsJson(request)) writeJson(response, HttpServletResponse.SC_OK, mapOf("revoked" to count))
    else renderPage(
      response, user, null,
      notice = "Revoked $count session(s). You may need to sign in again.", error = null
    )
  }

  private fun tokens(user: User): List<TokenMetadata> = try {
    authenticationManager.listTokens(user)
  } catch (e: Exception) {
    log.warn("Failed to list tokens for {}", user, e)
    emptyList()
  }

  /** @return true/false when revocation is supported, null when the backend cannot revoke. */
  private fun tryLogout(token: String, user: User): Boolean? = try {
    authenticationManager.logoutIfMatching(token, user)
  } catch (e: UnsupportedOperationException) {
    null
  } catch (e: StackOverflowError) {
    // Defensive: some implementations inherit the (previously recursive) default.
    log.error("AuthenticationInterface.logoutIfMatching is not implemented by {}", authenticationManager.javaClass)
    null
  }

  private fun authenticate(request: HttpServletRequest, response: HttpServletResponse): User? {
    val user = userProvider.authenticate(request)
    if (user == null) {
      if (!response.isCommitted) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
      }
      return null
    }
    return user
  }

  private fun csrfOk(request: HttpServletRequest, user: User): Boolean {
    val presented = request.getHeader(CSRF_HEADER) ?: request.getParameter("csrf")
    return user.isSignatureValid(presented)
  }

  private fun generateToken(): String {
    val bytes = ByteArray(TOKEN_BYTES)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  private fun parseTtl(raw: String?): Duration? {
    val value = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "never" && it != "default" } ?: return null
    val seconds = when {
      value.endsWith("d") -> value.dropLast(1).toLongOrNull()?.times(86_400)
      value.endsWith("h") -> value.dropLast(1).toLongOrNull()?.times(3_600)
      value.endsWith("m") -> value.dropLast(1).toLongOrNull()?.times(60)
      value.endsWith("s") -> value.dropLast(1).toLongOrNull()
      else -> value.toLongOrNull()
    } ?: return null
    return if (seconds <= 0) null else Duration.ofSeconds(seconds)
  }

  private fun wantsJson(request: HttpServletRequest): Boolean {
    if (request.getParameter("format").equals("json", ignoreCase = true)) return true
    val accept = request.getHeader("Accept").orEmpty()
    return accept.contains("application/json", ignoreCase = true) && !accept.contains("text/html", ignoreCase = true)
  }

  private fun writeJson(response: HttpServletResponse, status: Int, body: Any) {
    response.status = status
    response.contentType = "application/json"
    response.characterEncoding = "UTF-8"
    response.setHeader("Cache-Control", "no-store")
    mapper.writeValue(response.writer, body)
  }

  private fun fail(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    status: Int,
    message: String
  ) {
    if (wantsJson(request)) {
      writeJson(response, status, mapOf("error" to message))
    } else {
      response.status = status
      renderPage(response, user, newToken = null, notice = null, error = message)
    }
  }

  private fun userJson(user: User) = mapOf(
    "id" to user.id,
    "name" to user.name,
    "email" to user.redactedEmail
  )

  private fun tokenJson(meta: TokenMetadata) = mapOf(
    "preview" to mask(meta.token),
    "userId" to meta.userId,
    "issuedAt" to meta.issuedAt?.toString(),
    "expiresAt" to meta.expiresAt?.toString(),
    "lastUsedAt" to meta.lastUsedAt?.toString(),
    "expired" to (meta.expiresAt?.isBefore(Instant.now()) ?: false)
  )

  private fun renderPage(
    response: HttpServletResponse,
    user: User,
    newToken: String?,
    notice: String?,
    error: String?
  ) {
    val tokens = tokens(user)
    val csrf = user.signature
    response.contentType = "text/html"
    response.characterEncoding = "UTF-8"
    response.setHeader("Cache-Control", "no-store")
    val rows = if (tokens.isEmpty()) {
      """<tr><td colspan="5" class="empty">No active sessions are tracked for this account.</td></tr>"""
    } else tokens.joinToString("\n") { meta ->
      val expired = meta.expiresAt?.isBefore(Instant.now()) ?: false
      """
      <tr${if (expired) " class=\"expired\"" else ""}>
        <td><code>${esc(mask(meta.token))}</code></td>
        <td>${esc(fmt(meta.issuedAt))}</td>
        <td>${esc(fmt(meta.expiresAt))}</td>
        <td>${esc(fmt(meta.lastUsedAt))}</td>
        <td>
          <form method="post" onsubmit="return confirm('Revoke this token?')">
            <input type="hidden" name="action" value="revoke"/>
            <input type="hidden" name="csrf" value="${esc(csrf)}"/>
            <input type="hidden" name="token" value="${esc(meta.token)}"/>
            <button type="submit" class="danger">Revoke</button>
          </form>
        </td>
      </tr>
      """.trimIndent()
    }
    val banners = buildString {
      if (error != null) append("""<div class="banner error">${esc(error)}</div>""")
      if (notice != null) append("""<div class="banner notice">${esc(notice)}</div>""")
      if (newToken != null) append(
        """
        <div class="banner token">
          <div>Your new access token (shown once):</div>
          <code id="new-token">${esc(newToken)}</code>
          <button type="button" onclick="navigator.clipboard.writeText(document.getElementById('new-token').textContent)">Copy</button>
        </div>
        """.trimIndent()
      )
    }
    response.writer.write(
      """
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <title>Access Tokens</title>
        <style>
          body { font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 60rem; }
          table { border-collapse: collapse; width: 100%; margin-top: 1rem; }
          th, td { border-bottom: 1px solid #ddd; padding: .5rem; text-align: left; font-size: .9rem; }
          tr.expired { opacity: .5; }
          td.empty { text-align: center; color: #666; }
          code { background: #f4f4f4; padding: .15rem .35rem; border-radius: 3px; }
          .banner { padding: .75rem 1rem; border-radius: 4px; margin: .5rem 0; }
          .banner.error { background: #fdecea; color: #8a1c12; }
          .banner.notice { background: #eaf4fd; color: #11527d; }
          .banner.token { background: #edf7ed; color: #17501b; display: flex; gap: .75rem; align-items: center; flex-wrap: wrap; }
          .toolbar { display: flex; gap: 1rem; align-items: flex-end; margin-top: 1rem; flex-wrap: wrap; }
          button { cursor: pointer; padding: .4rem .8rem; }
          button.danger { color: #8a1c12; }
          form.inline { display: inline; }
        </style>
      </head>
      <body>
        <h1>Access Tokens</h1>
        <p>Signed in as <strong>${esc(user.name)}</strong> (<code>${esc(user.redactedEmail)}</code>)</p>
        $banners
        <div class="toolbar">
          <form method="post" class="inline">
            <input type="hidden" name="action" value="create"/>
            <input type="hidden" name="csrf" value="${esc(csrf)}"/>
            <label>Expires in
              <select name="ttl">
                <option value="1d">1 day</option>
                <option value="7d">7 days</option>
                <option value="30d" selected>30 days</option>
                <option value="90d">90 days</option>
                <option value="never">Never</option>
              </select>
            </label>
            <button type="submit">Create token</button>
          </form>
          <form method="post" class="inline" onsubmit="return confirm('Revoke ALL sessions, including this one?')">
            <input type="hidden" name="action" value="revoke-all"/>
            <input type="hidden" name="csrf" value="${esc(csrf)}"/>
            <button type="submit" class="danger">Revoke all</button>
          </form>
        </div>
        <table>
          <thead><tr><th>Token</th><th>Issued</th><th>Expires</th><th>Last used</th><th></th></tr></thead>
          <tbody>
          $rows
          </tbody>
        </table>
      </body>
      </html>
      """.trimIndent()
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(ApiKeyServlet::class.java)
    private const val TOKEN_BYTES = 32
    private const val CSRF_HEADER = "X-CSRF-Token"
    private val TIMESTAMP_FORMAT: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    internal fun mask(token: String): String = when {
      token.length <= 8 -> "*".repeat(token.length.coerceAtLeast(4))
      else -> token.take(4) + "\u2026" + token.takeLast(4)
    }

    private fun fmt(instant: Instant?): String = instant?.let { TIMESTAMP_FORMAT.format(it) } ?: "-"

    private fun esc(value: String): String = value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
  }
}