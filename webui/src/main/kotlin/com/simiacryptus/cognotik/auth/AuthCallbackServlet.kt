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

            // Determine session id: prefer path info, fall back to "state" query param
            val sessionId = extractSessionId(pathInfo) ?: params["state"]

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
            if (pending == null) {
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
                pending.complete(token)
                writeResponse(
                    resp,
                    HttpServletResponse.SC_OK,
                    successHtml()
                )
            } else {
                val error = params["error"] ?: "Unknown error"
                val errorDescription = params["error_description"] ?: ""
                log.warn("Auth callback received error for session={}: {} - {}", sessionId, error, errorDescription)
                pending.complete(null)
                writeResponse(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    errorHtml(error, errorDescription)
                )
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

    companion object {
        private val log = LoggerFactory.getLogger(AuthCallbackServlet::class.java)

        /** Registry of pending authentication requests keyed by session id. */
        private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<String?>>()

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
         * Cancels and removes a pending request (e.g. on timeout).
         */
        fun cancelPending(sessionId: String) {
            val future = pendingRequests.remove(sessionId)
            future?.cancel(true)
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