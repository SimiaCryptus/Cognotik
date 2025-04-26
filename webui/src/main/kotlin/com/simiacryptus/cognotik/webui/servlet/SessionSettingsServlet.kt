package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import com.simiacryptus.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.IOException

class SessionSettingsServlet(
    private val server: ApplicationServer,
) : HttpServlet() {
    private val logger = LoggerFactory.getLogger(SessionSettingsServlet::class.java)
    val settingsClass = Map::class.java // server.settingsClass

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            logger.info("Handling GET request from ${req.remoteAddr} with parameters: ${req.parameterMap}")
            resp.contentType = "text/html"
            resp.status = HttpServletResponse.SC_OK

            if (req.parameterMap.containsKey("sessionId")) {
                val sessionId = req.getParameter("sessionId")
                logger.debug("Processing request for session: $sessionId")
                val session = Session(sessionId)
                val cookie = req.getCookie()
                val user = authenticationManager.getUser(cookie)
                logger.debug("User identified: ${user?.id ?: "anonymous"}")

                try {
                    val settings = server.getSettings(session, user, settingsClass)
                    val json = if (settings != null) JsonUtil.toJson(settings) else ""
                    logger.debug("Retrieved settings for session $sessionId: ${json.take(100)}${if (json.length > 100) "..." else ""}")

                    if (req.parameterMap.containsKey("raw") && req.getParameter("raw") == "true") {
                        logger.debug("Returning raw JSON response")
                        resp.contentType = "application/json"
                        resp.writer.write(json)
                        return
                    }

                    //language=HTML
                    resp.writer.write(
                        """
            <html>
            <head>
                <title>Settings</title>
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            </head>
            <body>
            <form action="""".trimIndent() + req.contextPath + """/settings" method="post">
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
                    resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    resp.writer.write("Error retrieving settings: ${e.message}")
                }
            } else {
                logger.warn("Request missing required sessionId parameter")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Session ID is required")
            }
        } catch (e: Exception) {
            logger.error("Unhandled exception in doGet", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Internal server error: ${e.message}")
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            logger.info("Handling POST request from ${req.remoteAddr}")
            resp.contentType = "text/html"
            resp.status = HttpServletResponse.SC_OK

            if (!req.parameterMap.containsKey("sessionId")) {
                logger.warn("POST request missing required sessionId parameter")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Session ID is required")
            } else {
                val sessionId = req.getParameter("sessionId")
                logger.debug("Processing POST request for session: $sessionId")
                val session = Session(sessionId)

                try {
                    val settingsJson = if (req.parameterNames.toList().contains("settings")) {
                        val paramSettings = req.getParameter("settings")
                        logger.debug("Using settings from parameter: ${paramSettings.take(100)}${if (paramSettings.length > 100) "..." else ""}")
                        paramSettings
                    } else {
                        val bodySettings = req.reader.readText()
                        logger.debug("Using settings from request body: ${bodySettings.take(100)}${if (bodySettings.length > 100) "..." else ""}")
                        bodySettings
                    }

                    val settings = JsonUtil.fromJson<Any>(settingsJson, settingsClass)
                    val cookie = req.getCookie()
                    val user = authenticationManager.getUser(cookie)
                    logger.debug("User identified for settings update: ${user?.id ?: "anonymous"}")

                    val settingsFile = server.getSettingsFile(session, user)
                    settingsFile.parentFile.mkdirs()
                    logger.debug("Saving settings to file: ${settingsFile.absolutePath}")

                    try {
                        settingsFile.writeText(JsonUtil.toJson(settings))
                        logger.info("Successfully saved settings for session $sessionId")
                        resp.sendRedirect("${req.contextPath}/#$session")
                    } catch (e: IOException) {
                        logger.error("Failed to write settings to file: ${settingsFile.absolutePath}", e)
                        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                        resp.writer.write("Failed to save settings: ${e.message}")
                    }
                } catch (e: Exception) {
                    logger.error("Error processing settings for session $sessionId", e)
                    resp.status = HttpServletResponse.SC_BAD_REQUEST
                    resp.writer.write("Invalid settings format: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("Unhandled exception in doPost", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Internal server error: ${e.message}")
        }
    }
}