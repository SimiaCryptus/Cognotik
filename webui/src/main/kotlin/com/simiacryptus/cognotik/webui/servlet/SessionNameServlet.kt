package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory

/**
 * Read/write access to the human-readable display name of a session.
 *
 * The name itself lives in [MetadataStorageInterface] (see
 * [MetadataStorageInterface.getSessionName] / [MetadataStorageInterface.setSessionName]),
 * so this servlet is a thin, authenticated HTTP facade over that store.
 *
 * Endpoints (mounted at `/sessionName`):
 *  - `GET  /sessionName?sessionId=<id>`            -> HTML rename form
 *  - `GET  /sessionName?sessionId=<id>&raw=true`   -> `{"sessionId":"..","name":".."}`
 *  - `POST /sessionName` with `sessionId` + `name` -> rename (form post: redirect, api post: JSON)
 *  - `POST /sessionName?sessionId=<id>` with JSON body `{"name":".."}` -> rename
 *
 * @param server owning application server (used for the app name shown in the form)
 * @param metadataStorageProvider indirection so tests can inject an in-memory store
 */
class SessionNameServlet(
  private val server: ApplicationServer,
  private val metadataStorageProvider: () -> MetadataStorageInterface = {
    ApplicationServices.fileApplicationServices().metadataDB
  },
) : HttpServlet() {
  private val logger = LoggerFactory.getLogger(SessionNameServlet::class.java)

  private val metadataStorage: MetadataStorageInterface get() = metadataStorageProvider()

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      logger.info("Handling GET request from ${request.remoteAddr} with parameters: ${request.parameterMap}")
      val sessionId = request.getParameter("sessionId")
      if (sessionId.isNullOrBlank()) {
        logger.warn("Request missing required sessionId parameter")
        respondText(response, HttpServletResponse.SC_BAD_REQUEST, "Session ID is required")
        return
      }
      val session = Session(sessionId)
      val user = UserProviderImpl().authenticate(request, response)
      if (user == null) {
        logger.warn("Authentication failed / redirect issued for sessionName GET on session {}", sessionId)
        return
      }
      logger.debug("User identified: {}", user.id ?: "anonymous")

      val name = try {
        metadataStorage.getSessionName(user, session)
      } catch (e: Exception) {
        logger.error("Error retrieving session name for session $sessionId", e)
        respondText(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error retrieving session name: ${e.message}"
        )
        return
      }

      if (wantsJson(request)) {
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_OK
        response.writer.write(JsonUtil.toJson(mapOf("sessionId" to session.toString(), "name" to name)))
        return
      }

      response.contentType = "text/html"
      response.status = HttpServletResponse.SC_OK
      response.writer.write(renderForm(request, session, name))
    } catch (e: Exception) {
      logger.error("Unhandled exception in doGet", e)
      respondText(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: ${e.message}")
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      logger.info("Handling POST request from ${request.remoteAddr}")
      val sessionId = request.getParameter("sessionId")
      if (sessionId.isNullOrBlank()) {
        logger.warn("POST request missing required sessionId parameter")
        respondText(response, HttpServletResponse.SC_BAD_REQUEST, "Session ID is required")
        return
      }
      val session = Session(sessionId)
      val user = UserProviderImpl().authenticate(request, response)
      if (user == null) {
        logger.warn("Authentication failed / redirect issued for sessionName POST on session {}", sessionId)
        return
      }
      logger.debug("User identified for session rename: {}", user.id ?: "anonymous")

      val submittedName = readName(request)
      if (submittedName == null) {
        logger.warn("POST request for session {} did not contain a usable name", sessionId)
        respondText(response, HttpServletResponse.SC_BAD_REQUEST, "A non-blank 'name' is required")
        return
      }
      val name = sanitize(submittedName)
      if (name.isBlank()) {
        respondText(response, HttpServletResponse.SC_BAD_REQUEST, "A non-blank 'name' is required")
        return
      }

      try {
        metadataStorage.setSessionName(user, session, name)
        logger.info("Renamed session {} to '{}'", sessionId, name)
      } catch (e: Exception) {
        logger.error("Failed to store session name for session $sessionId", e)
        respondText(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to save session name: ${e.message}"
        )
        return
      }

      if (wantsJson(request)) {
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_OK
        response.writer.write(JsonUtil.toJson(mapOf("sessionId" to session.toString(), "name" to name)))
      } else {
        response.sendRedirect("${request.contextPath}/#$session")
      }
    } catch (e: Exception) {
      logger.error("Unhandled exception in doPost", e)
      respondText(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: ${e.message}")
    }
  }

  /** Accepts the name from a form parameter, falling back to a JSON (or plain text) request body. */
  private fun readName(request: HttpServletRequest): String? {
    request.getParameter("name")?.takeIf { it.isNotBlank() }?.let { return it }
    val body = try {
      request.reader.readText()
    } catch (e: Exception) {
      logger.debug("Unable to read request body: {}", e.message)
      return null
    }
    if (body.isBlank()) return null
    return try {
      @Suppress("UNCHECKED_CAST")
      val parsed = JsonUtil.fromJson<Map<String, Any?>>(body, Map::class.java) as Map<String, Any?>
      (parsed["name"] ?: parsed["sessionName"])?.toString()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      logger.debug("Body was not JSON, treating as raw name: {}", e.message)
      body.takeIf { it.isNotBlank() }
    }
  }

  private fun wantsJson(request: HttpServletRequest): Boolean {
    if (request.getParameter("raw") == "true") return true
    val contentType = request.contentType ?: ""
    if (contentType.contains("application/json", ignoreCase = true)) return true
    val accept = request.getHeader("Accept") ?: return false
    return accept.contains("application/json", ignoreCase = true) && !accept.contains("text/html", ignoreCase = true)
  }

  private fun respondText(response: HttpServletResponse, status: Int, message: String) {
    response.contentType = "text/plain"
    response.status = status
    response.writer.write(message)
  }

  private fun sanitize(name: String) = name.replace(Regex("[\\r\\n\\t]"), " ").trim().take(MAX_NAME_LENGTH)

  private fun renderForm(request: HttpServletRequest, session: Session, name: String) = """
        <html>
        <head>
            <title>Rename Session</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
        </head>
        <body>
        <h3>${escape(server.applicationName)}</h3>
        <form action="${escape(request.contextPath)}/sessionName" method="post">
            <input type="hidden" name="sessionId" value="${escape(session.toString())}"/>
            <input type="text" name="name" maxlength="$MAX_NAME_LENGTH" style="width: 100%;" value="${escape(name)}"/>
            <input type="submit" value="Rename"/>
        </form>
        </body>
        </html>
      """.trimIndent()

  private fun escape(text: String) = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

  companion object {
    const val MAX_NAME_LENGTH = 256
  }
}