package com.simiacryptus.cognotik.webui.servlet
    
    import com.google.gson.Gson
    import com.simiacryptus.cognotik.webui.application.AppEntry
    import jakarta.servlet.http.HttpServlet
    import jakarta.servlet.http.HttpServletRequest
    import jakarta.servlet.http.HttpServletResponse
    import org.slf4j.LoggerFactory
    
    class AppDirectoryServlet : HttpServlet() {
    
        companion object {
            private val log = LoggerFactory.getLogger(AppDirectoryServlet::class.java)
            private val gson = Gson()
        }
    
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            // Support asset requests: /appDirectory/{appId}/background.png or /appDirectory/{appId}/icon.png
            // Or via query params: ?appId=xxx&asset=background|icon
            val pathInfo = req.servletPath
            val assetParam = req.getParameter("asset")
            val appIdParam = req.getParameter("appId")
    
            if (!pathInfo.isNullOrBlank() && pathInfo != "/") {
                val parts = pathInfo.trim('/').split('/')
                if (parts.size >= 2) {
                    val appId = parts[0]
                    val assetFile = parts[1].lowercase()
                    if (serveAsset(appId, assetFile, resp)) return
                }
            } else if (assetParam != null && appIdParam != null) {
                val assetFile = when (assetParam.lowercase()) {
                    "background" -> "background.png"
                    "icon" -> "icon.png"
                    else -> assetParam
                }
                if (serveAsset(appIdParam, assetFile, resp)) return
            }
    
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
                        "cardClass" to entry.cardClass,
                        "readme" to entry.readme,
                        "category" to entry.category,
                        "tags" to entry.tags,
                        "hasBackground" to entry.hasBackground,
                        "hasIcon" to entry.hasIcon,
                        "backgroundUrl" to if (entry.hasBackground) "${entry.appId}/background.png" else null,
                        "iconUrl" to if (entry.hasIcon) "${entry.appId}/icon.png" else null,
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
    
        /**
         * Attempts to serve a per-app static asset (background.png / icon.png) from the
         * resource_path of the matching AppEntry, using its classLoader.
         * Returns true if the response was written (success or 404 image not found).
         */
        private fun serveAsset(appIdRaw: String, assetFile: String, resp: HttpServletResponse): Boolean {
            val normalizedAsset = assetFile.lowercase()
            if (normalizedAsset != "background.png" && normalizedAsset != "icon.png") {
                return false
            }
            val appId = appIdRaw.removePrefix("app-")
            val entry = AppEntry.values().firstOrNull {
                it.appId == appId || it.appId == appIdRaw || it.id == appIdRaw || it.id == "app-$appId"
            }
            if (entry == null) {
                log.debug("No AppEntry found for appId='{}'", appIdRaw)
                resp.status = HttpServletResponse.SC_NOT_FOUND
                return true
            }
            val resourcePath = entry.resource_path?.trimEnd('/')
            if (resourcePath.isNullOrBlank()) {
                resp.status = HttpServletResponse.SC_NOT_FOUND
                return true
            }
            val fullPath = "$resourcePath/$normalizedAsset"
            val stream = entry.classLoader.getResourceAsStream(fullPath)
            if (stream == null) {
                log.debug("Asset not found at '{}' for app '{}'", fullPath, appIdRaw)
                resp.status = HttpServletResponse.SC_NOT_FOUND
                return true
            }
            resp.contentType = "image/png"
            resp.setHeader("Cache-Control", "public, max-age=3600")
            stream.use { input ->
                resp.outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            log.debug("Served asset '{}' for app '{}'", normalizedAsset, appIdRaw)
            return true
        }
    }