package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.AppEntry
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AppDirectoryServlet : HttpServlet() {

    companion object {
        private val log = LoggerFactory.getLogger(AppDirectoryServlet::class.java)
        private val gson = Gson()
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        try {
            val entries = AppEntry.values().map { entry ->
                mapOf(
                    "id" to entry.id,
                    "name" to entry.displayName,
                    "icon" to entry.icon,
                    "description" to entry.description,
                    "badge" to entry.badge,
                    "badgeClass" to (entry.badgeClass ?: ""),
                    "type" to entry.type,
                    "path" to entry.path,
                    "appId" to entry.appId,
                    "cardClass" to entry.cardClass
                )
            }
            resp.writer.write(gson.toJson(entries))
            log.debug("Served ${entries.size} app directory entries")
        } catch (e: Exception) {
            log.error("Error serving app directory: ${e.message}", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write(gson.toJson(mapOf("error" to (e.message ?: "Unknown error"))))
        }
    }
}