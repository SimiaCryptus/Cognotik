package com.simiacryptus.cognotik.auth

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * A persistent servlet that handles OAuth callbacks for authentication flows
 * (e.g. Patreon, GitHub). Unlike the previous design that spun up a temporary
 * HTTP server per auth attempt, this servlet is mounted once at application
 * startup and routes callbacks to pending auth requests by session id.
 *
 * The servlet expects callbacks at a path that includes a session id, e.g.:
 *   {mountPath}/{sessionId}?token=...
 * Or alternatively the session id can be passed as a query parameter:
 *   {mountPath}?state={sessionId}&token=...
 */
class AuthCallbackServlet : HttpServlet() {

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val pathInfo = req.pathInfo ?: ""
            val query = req.queryString ?: ""
            log.debug(
                "Received auth callback: pathInfo={}, queryLength={}, remoteAddr={}",
                pathInfo, query.length, req.remoteAddr
            )

            val params = parseQuery(query)

            // Determine session id: prefer path info, fall back to "state" query param.
            // We also separately track the "state" parameter, which (if present and
            // corresponds to a registered web-flow session) indicates this callback
            // belongs to a browser-initiated login that needs to be redirected to
            // the /login/ finalization endpoint.
            val stateParam = params["state"]
            val sessionId = extractSessionId(pathInfo) ?: stateParam

            if (sessionId.isNullOrBlank()) {
                log.warn("Auth callback received without session id")
                writeResponse(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    errorHtml(
                        "Missing session identifier",
                        "Authorization callback was missing the session identifier."
                    )
                )
                return
            }

            val pending = pendingRequests.remove(sessionId)
            val webFlow = webFlowSessions.remove(sessionId)
            val webMethodName = webFlow?.loginMethodName
            if (pending == null && webFlow == null) {
                log.warn("Auth callback received for unknown or expired session: {}", sessionId)
                writeResponse(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    errorHtml(
                        "Unknown session",
                        "No pending authorization was found for this session. It may have expired."
                    )
                )
                return
            }

            val token = params["token"]
            if (token != null) {
                log.debug("Auth callback received valid token for session={} (length={})", sessionId, token.length)
                pending?.complete(token)
                if (webMethodName != null) {
                    val finalizationUrl = try {
                        buildLoginFinalizationUrl(webMethodName, token, sessionId)
                    } catch (e: Exception) {
                        log.error("Failed to build login finalization URL for session={}", sessionId, e)
                        null
                    }
                    if (finalizationUrl != null) {
                        log.debug("Redirecting browser to login finalization URL for session={}", sessionId)
                        resp.setHeader("Cache-Control", "no-store, no-cache")
                        resp.sendRedirect(finalizationUrl)
                        return
                    } else {
                        // Fall back to static success page if we can't build the URL.
                        writeResponse(resp, HttpServletResponse.SC_OK, successHtml())
                    }
                } else {
                    writeResponse(
                        resp,
                        HttpServletResponse.SC_OK,
                        successHtml()
                    )
                }
            } else {
                val error = params["error"] ?: "Unknown error"
                val errorDescription = params["error_description"] ?: ""
                log.warn("Auth callback received error for session={}: {} - {}", sessionId, error, errorDescription)
                pending?.complete(null)
                if (webMethodName != null) {
                    // For web flows, redirect back to /login/ with the error so that
                    // the existing handleLogin() code path can render an error page.
                    val finalizationUrl = try {
                        buildLoginFinalizationErrorUrl(webMethodName, error, errorDescription, sessionId)
                    } catch (e: Exception) {
                        log.error("Failed to build login finalization error URL for session={}", sessionId, e)
                        null
                    }
                    if (finalizationUrl != null) {
                        resp.setHeader("Cache-Control", "no-store, no-cache")
                        resp.sendRedirect(finalizationUrl)
                        return
                    } else {
                        writeResponse(
                            resp,
                            HttpServletResponse.SC_BAD_REQUEST,
                            errorHtml(error, errorDescription)
                        )
                    }
                } else {
                    writeResponse(
                        resp,
                        HttpServletResponse.SC_BAD_REQUEST,
                        errorHtml(error, errorDescription)
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Unexpected error handling auth callback", e)
            try {
                writeResponse(
                    resp,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    errorHtml("Internal error", e.message ?: "Unexpected error processing callback")
                )
            } catch (ignored: Exception) {
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) {
                try {
                    parts[0] to URLDecoder.decode(parts[1], "UTF-8")
                } catch (e: IllegalArgumentException) {
                    log.warn("Failed to URL-decode query parameter '{}': {}", parts[0], e.message)
                    parts[0] to parts[1]
                }
            } else parts[0] to ""
        }
    }

    private fun extractSessionId(pathInfo: String): String? {
        // pathInfo is like "/{sessionId}" or empty/null
        val trimmed = pathInfo.trim('/')
        return if (trimmed.isBlank()) null else trimmed
    }

    private fun writeResponse(resp: HttpServletResponse, statusCode: Int, body: String) {
        resp.status = statusCode
        resp.contentType = "text/html; charset=UTF-8"
        resp.setHeader("Cache-Control", "no-store, no-cache")
        val bytes = body.toByteArray(Charsets.UTF_8)
        resp.setContentLength(bytes.size)
        resp.outputStream.use { it.write(bytes) }
    }

    private fun successHtml(): String = """
    <html><body style="font-family: sans-serif; text-align: center; padding-top: 50px;">
    <h2>✅ Authorization Successful</h2>
    <p>You can close this window and return to the application.</p>
    <p style="color: #888; font-size: 0.9em;">Token received and being verified...</p>
    <script>window.close();</script>
    </body></html>
  """.trimIndent()

    private fun errorHtml(error: String, description: String): String = """
    <html><body style="font-family: sans-serif; text-align: center; padding-top: 50px;">
    <h2>❌ Authorization Failed</h2>
    <p>${escapeHtml(error)}</p>
    ${if (description.isNotEmpty()) "<p style=\"color: #666;\">${escapeHtml(description)}</p>" else ""}
    <p>You can close this window.</p>
    </body></html>
  """.trimIndent()

    private fun escapeHtml(input: String) = input
        .replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#x27;")

    /**
     * Builds the login finalization URL by reflectively invoking
     * OAuthLoginMethod.buildLoginFinalizationUrl(loginMethodName, token, sessionId).
     * Reflection is used to avoid a hard compile-time dependency cycle with
     * the login package; if the method is unavailable we fall back to a
     * sensible default path.
     */
    private fun buildLoginFinalizationUrl(loginMethodName: String, token: String, sessionId: String): String {
        try {
            val cls = Class.forName("com.simiacryptus.cognotik.auth.OAuthLoginMethod")
            // Try companion-style static method first
            val method = cls.declaredMethods.firstOrNull {
                it.name == "buildLoginFinalizationUrl" && it.parameterCount == 3
            }
            if (method != null) {
                method.isAccessible = true
                val result = method.invoke(null, loginMethodName, token, sessionId)
                if (result is String) return result
            }
            // Try Companion object
            val companionField = cls.getDeclaredField("Companion")
            companionField.isAccessible = true
            val companion = companionField.get(null)
            val companionMethod = companion.javaClass.declaredMethods.firstOrNull {
                it.name == "buildLoginFinalizationUrl" && it.parameterCount == 3
            }
            if (companionMethod != null) {
                companionMethod.isAccessible = true
                val result = companionMethod.invoke(companion, loginMethodName, token, sessionId)
                if (result is String) return result
            }
        } catch (e: Throwable) {
            log.debug("Reflective lookup of OAuthLoginMethod.buildLoginFinalizationUrl failed: {}", e.message)
        }
        // Fallback: construct URL matching the documented contract.
        val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
        val encodedMethod = java.net.URLEncoder.encode(loginMethodName, "UTF-8")
        val encodedSession = java.net.URLEncoder.encode(sessionId, "UTF-8")
        return "/login/?formAction=login&loginMethod=$encodedMethod&token=$encodedToken&state=$encodedSession"
    }

    private fun buildLoginFinalizationErrorUrl(
        loginMethodName: String,
        error: String,
        errorDescription: String,
        sessionId: String
    ): String {
        val encodedMethod = java.net.URLEncoder.encode(loginMethodName, "UTF-8")
        val encodedError = java.net.URLEncoder.encode(error, "UTF-8")
        val encodedDesc = java.net.URLEncoder.encode(errorDescription, "UTF-8")
        val encodedSession = java.net.URLEncoder.encode(sessionId, "UTF-8")
        return "/login/?formAction=login&loginMethod=$encodedMethod&error=$encodedError" +
                "&error_description=$encodedDesc&state=$encodedSession"
    }


    companion object {
        private val log = LoggerFactory.getLogger(AuthCallbackServlet::class.java)

        /** Registry of pending authentication requests keyed by session id. */
        private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<String?>>()

        /**
         * Registry of web-flow sessions keyed by session id. Presence of an entry
         * indicates that the OAuth callback for this session id should redirect
         * the user's browser to the /login/ finalization endpoint instead of
         * (or in addition to) rendering the static success page.
         */
        private val webFlowSessions = ConcurrentHashMap<String, WebFlowEntry>()

        private data class WebFlowEntry(val loginMethodName: String)


        /**
         * The base URL where this servlet is mounted (e.g. "http://127.0.0.1:8080/auth/callback").
         * Must be configured at application startup before any auth flow is initiated.
         */
        @Volatile
        var publicBaseUrl: String? = null

        /**
         * Registers a new pending auth request and returns a session id that can be
         * embedded in the callback URL.
         */
        fun registerPending(future: CompletableFuture<String?>): String {
            val sessionId = UUID.randomUUID().toString()
            pendingRequests[sessionId] = future
            return sessionId
        }

        /**
         * Registers a new web-flow auth request. The callback handler will redirect
         * the user's browser to the login finalization URL on success rather than
         * rendering the static success page. An optional [future] may also be
         * supplied for callers that still want to observe completion locally.
         */
        fun registerWebFlow(
            loginMethodName: String,
            future: CompletableFuture<String?>? = null
        ): String {
            val sessionId = UUID.randomUUID().toString()
            if (future != null) pendingRequests[sessionId] = future
            webFlowSessions[sessionId] = WebFlowEntry(loginMethodName)
            return sessionId
        }

        /**
         * Marks an already-registered session as a web flow (so it will redirect
         * to the /login/ finalization URL on success).
         */
        fun markAsWebFlow(sessionId: String, loginMethodName: String) {
            webFlowSessions[sessionId] = WebFlowEntry(loginMethodName)
        }


        /**
         * Cancels and removes a pending request (e.g. on timeout).
         */
        fun cancelPending(sessionId: String) {
            val future = pendingRequests.remove(sessionId)
            future?.cancel(true)
            webFlowSessions.remove(sessionId)
        }

        /**
         * Builds the full callback URL for a given session id.
         * Returns null if [publicBaseUrl] has not been configured.
         */
        fun buildCallbackUrl(sessionId: String): String? {
            val base = publicBaseUrl ?: return null
            val trimmed = base.trimEnd('/')
            return "$trimmed/$sessionId"
        }

        /**
         * Number of currently pending auth requests (for diagnostics).
         */
        fun pendingCount(): Int = pendingRequests.size
    }
}