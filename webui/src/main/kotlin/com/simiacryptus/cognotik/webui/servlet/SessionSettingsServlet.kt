package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.authenticate
import com.simiacryptus.cognotik.webui.application.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.IOException

class SessionSettingsServlet(
  private val server: ApplicationServer,
) : HttpServlet() {
  private val logger = LoggerFactory.getLogger(SessionSettingsServlet::class.java)
  val settingsClass = Map::class.java


  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      logger.info("Handling GET request from ${request.remoteAddr} with parameters: ${request.parameterMap}")
      response.contentType = "text/html"
      response.status = HttpServletResponse.SC_OK

      if (request.parameterMap.containsKey("sessionId")) {
        val sessionId = request.getParameter("sessionId")
        logger.debug("Processing request for session: $sessionId")
        val session = Session(sessionId)
        val cookie = request.getCookie()
        val user = authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
        logger.debug("User identified: ${user?.id ?: "anonymous"}")

        try {
          val settings = server.getSettings(session, user, settingsClass)
          val json = if (settings != null) JsonUtil.toJson(settings) else ""
          logger.debug("Retrieved settings for session $sessionId: ${json.take(100)}${if (json.length > 100) "..." else ""}")

          if (request.parameterMap.containsKey("raw") && request.getParameter("raw") == "true") {
            logger.debug("Returning raw JSON response")
            response.contentType = "application/json"
            response.writer.write(json)
            return
          }

          response.writer.write(
            """
            <html>
            <head>
                <title>Settings</title>
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            </head>
            <body>
            <form action="""".trimIndent() + request.contextPath + """/settings" method="post">
                <input type="hidden" name="sessionId" value="""" + session + """"/>
                <input type="hidden" name="action" value="save"/>
                <textarea name="settings" style="width: 100%; height: 100px;">""" + json + """</textarea>
                <input type="submit" value="Save"/>
            </form>
            </body>
            </html>
          """.trimIndent()
          )
        } catch (e: Exception) {
          logger.error("Error retrieving settings for session $sessionId", e)
          response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          response.writer.write("Error retrieving settings: ${e.message}")
        }
      } else {
        logger.warn("Request missing required sessionId parameter")
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.writer.write("Session ID is required")
      }
    } catch (e: Exception) {
      logger.error("Unhandled exception in doGet", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("Internal server error: ${e.message}")
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      logger.info("Handling POST request from ${request.remoteAddr}")
      response.contentType = "text/html"
      response.status = HttpServletResponse.SC_OK

      if (!request.parameterMap.containsKey("sessionId")) {
        logger.warn("POST request missing required sessionId parameter")
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.writer.write("Session ID is required")
      } else {
        val sessionId = request.getParameter("sessionId")
        logger.debug("Processing POST request for session: $sessionId")
        val session = Session(sessionId)

        try {
          val settingsJson = if (request.parameterNames.toList().contains("settings")) {
            val paramSettings = request.getParameter("settings")
            logger.debug("Using settings from parameter: ${paramSettings.take(100)}${if (paramSettings.length > 100) "..." else ""}")
            paramSettings
          } else {
            val bodySettings = request.reader.readText()
            logger.debug("Using settings from request body: ${bodySettings.take(100)}${if (bodySettings.length > 100) "..." else ""}")
            bodySettings
          }

          val settings = JsonUtil.fromJson<Any>(settingsJson, settingsClass)
          val cookie = request.getCookie()
          val user = authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
          logger.debug("User identified for settings update: ${user?.id ?: "anonymous"}")

          val settingsFile = server.getSettingsFile(session, user)
          settingsFile.parentFile.mkdirs()
          logger.debug("Saving settings to file: ${settingsFile.absolutePath}")

          try {
            settingsFile.writeText(JsonUtil.toJson(settings))
            logger.info("Successfully saved settings for session $sessionId")
            response.sendRedirect("${request.contextPath}/#$session")
          } catch (e: IOException) {
            logger.error("Failed to write settings to file: ${settingsFile.absolutePath}", e)
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            response.writer.write("Failed to save settings: ${e.message}")
          }
        } catch (e: Exception) {
          logger.error("Error processing settings for session $sessionId", e)
          response.status = HttpServletResponse.SC_BAD_REQUEST
          response.writer.write("Invalid settings format: ${e.message}")
        }
      }
    } catch (e: Exception) {
      logger.error("Unhandled exception in doPost", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("Internal server error: ${e.message}")
    }
  }
}