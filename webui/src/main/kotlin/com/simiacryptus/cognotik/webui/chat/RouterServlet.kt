package com.simiacryptus.cognotik.webui.chat
    
    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.platform.model.StorageInterface
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.webui.application.authenticate
   import com.simiacryptus.cognotik.webui.servlet.handler.FileAccessControl
    import jakarta.servlet.http.HttpServlet
    import jakarta.servlet.http.HttpServletRequest
    import jakarta.servlet.http.HttpServletResponse
    import org.slf4j.LoggerFactory
    import java.io.File
    
    class RouterServlet(
        val dataStorage: StorageInterface
    ) : HttpServlet() {
        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            when {
                request.pathInfo == "/" -> response.sendRedirect(
                    "${request.contextPath}/fileIndex/${
                        request.getParameter(
                            "sessionId"
                        ) ?: Session.Companion.newUserID()
                    }/app.html"
                )
    
                request.pathInfo == "/global" -> response.sendRedirect(
                    "${request.contextPath}/fileIndex/${
                        request.getParameter(
                            "sessionId"
                        ) ?: Session.Companion.newGlobalID()
                    }/app.html"
                )
    
                request.pathInfo.startsWith("/share/") ->
                    share(
                        request,
                        response,
                        Session(request.pathInfo.removePrefix("/share/").split('/').firstOrNull() ?: ""),
                        authenticate(request, response) ?: run {
                            response.sendError(
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Authentication required to share session"
                            )
                            return
                        })
    
                else -> response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown path: ${request.pathInfo}")
            }
        }
    
        override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
            when {
                request.pathInfo?.startsWith("/share/") == true -> {
                    val session = Session(request.pathInfo.removePrefix("/share/").split('/').firstOrNull() ?: "")
                    val user = authenticate(request, response) ?: run {
                        response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Authentication required to share session"
                        )
                        return
                    }
                    confirmShare(request, response, session, user)
                }
    
                else -> response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown path: ${request.pathInfo}")
            }
        }
    
        fun share(
            request: HttpServletRequest,
            response: HttpServletResponse,
            session: Session,
            user: User
        ) {
            require(!session.isGlobal()) { "Cannot share a global session" }
            val sessionRoot = dataStorage.getUserDir(user, session)
            if (!sessionRoot.exists() || sessionRoot.list()?.isEmpty() == true) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Session is empty: ${session.sessionId}")
                return
            }
            val globalSession = session.toGlobal()
            val globalRoot = dataStorage.getUserDir(user, globalSession)
            val isUpdate = globalRoot.exists() && (globalRoot.list()?.isNotEmpty() == true)
            val filesToCopy = collectFilesToCopy(sessionRoot, globalRoot)
            renderConfirmationPage(request, response, session, globalSession, filesToCopy, isUpdate)
        }
    
        fun confirmShare(
            request: HttpServletRequest,
            response: HttpServletResponse,
            session: Session,
            user: User
        ) {
            require(!session.isGlobal()) { "Cannot share a global session" }
            val confirmation = request.getParameter("confirm")
            if (confirmation != "yes") {
                response.sendRedirect("${request.contextPath}/fileIndex/${session.sessionId}/app.html")
                return
            }
            val sessionRoot = dataStorage.getUserDir(user, session)
            if (!sessionRoot.exists() || sessionRoot.list()?.isEmpty() == true) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Session is empty: ${session.sessionId}")
                return
            }
            val globalSession = session.toGlobal()
            val globalRoot = dataStorage.getUserDir(user, globalSession)
            try {
                val filesToCopy = collectFilesToCopy(sessionRoot, globalRoot)
                filesToCopy.forEach { relativePath ->
                    val source = File(sessionRoot, relativePath)
                    val destination = File(globalRoot, relativePath)
                    destination.parentFile?.mkdirs()
                    DocOpsApp.Companion.copyFileWithLineEndingNormalization(source, destination)
                }
                response.sendRedirect("${request.contextPath}/fileIndex/${globalSession.sessionId}/app.html")
            } catch (e: Exception) {
                LoggerFactory.getLogger(DocOpsApp::class.java)
                    .error(
                        "Failed to share session ${session.sessionId} to global session ${globalSession.sessionId}: ${e.message}",
                        e
                    )
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to share session: ${e.message}")
            }
        }
    
        /**
         * Collects relative paths of files that need to be copied from source to destination.
         * Only files that don't exist in destination, or whose contents differ, are included.
         */
        fun collectFilesToCopy(source: File, destination: File): List<String> {
            val result = mutableListOf<String>()
           collectFilesToCopyRecursive(source, source, destination, "", result)
            return result
        }
    
        private fun collectFilesToCopyRecursive(
           baseDir: File,
            source: File,
            destination: File,
            relativePath: String,
            result: MutableList<String>
        ) {
           // Skip files/directories that are hidden according to FileAccessControl.
           // Hidden files should never be shared publicly.
           if (FileAccessControl.isHidden(baseDir, source)) {
               return
           }
            if (source.isDirectory) {
                source.listFiles()?.forEach { child ->
                    val childRelative = if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"
                   collectFilesToCopyRecursive(baseDir, child, File(destination, child.name), childRelative, result)
                }
            } else {
                if (!destination.exists() || !filesContentEqual(source, destination)) {
                    result.add(relativePath)
                }
            }
        }
    
        private fun filesContentEqual(a: File, b: File): Boolean {
            if (a.length() != b.length()) return false
            return try {
                a.readBytes().contentEquals(b.readBytes())
            } catch (e: Exception) {
                false
            }
        }
    
        private fun renderConfirmationPage(
            request: HttpServletRequest,
            response: HttpServletResponse,
            session: Session,
            globalSession: Session,
            filesToCopy: List<String>,
            isUpdate: Boolean
        ) {
            response.contentType = "text/html; charset=UTF-8"
            response.status = HttpServletResponse.SC_OK
            val actionUrl = "${request.contextPath}/share/${session.sessionId}"
            val cancelUrl = "${request.contextPath}/fileIndex/${session.sessionId}/app.html"
            val title = if (isUpdate) "Update Public Share" else "Share Session Publicly"
            val warning = if (isUpdate) {
                "You are about to <strong>update an existing public share</strong>. The files listed below will be copied to the public global session and will be <strong>visible to anyone</strong>."
            } else {
                "You are about to <strong>share this session publicly</strong>. The files listed below will be copied to a public global session and will be <strong>visible to anyone</strong>."
            }
            val fileListHtml = if (filesToCopy.isEmpty()) {
                "<p><em>No files need to be copied. The public share is already up to date.</em></p>"
            } else {
                buildString {
                    append("<p><strong>${filesToCopy.size}</strong> file(s) will be copied:</p>")
                    append("<ul class=\"file-list\">")
                    filesToCopy.forEach { path ->
                        append("<li>").append(escapeHtml(path)).append("</li>")
                    }
                    append("</ul>")
                }
            }
            val confirmButton = if (filesToCopy.isEmpty()) {
                "<button type=\"submit\" name=\"confirm\" value=\"yes\">Continue to Public Share</button>"
            } else {
                "<button type=\"submit\" name=\"confirm\" value=\"yes\" class=\"danger\">Yes, Share Publicly</button>"
            }
            val html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <title>${escapeHtml(title)}</title>
                  <style>
                    body { font-family: sans-serif; max-width: 800px; margin: 2em auto; padding: 0 1em; }
                    .warning { background: #fff3cd; border: 1px solid #ffeeba; padding: 1em; border-radius: 4px; margin-bottom: 1em; }
                    .file-list { max-height: 400px; overflow-y: auto; border: 1px solid #ddd; padding: 0.5em 1.5em; background: #f9f9f9; }
                    .file-list li { font-family: monospace; font-size: 0.9em; }
                    button { padding: 0.6em 1.2em; margin-right: 0.5em; font-size: 1em; cursor: pointer; border-radius: 4px; border: 1px solid #ccc; }
                    button.danger { background: #d9534f; color: #fff; border-color: #d43f3a; }
                    button.cancel { background: #f0f0f0; }
                    .meta { color: #555; font-size: 0.9em; }
                  </style>
                </head>
                <body>
                  <h1>${escapeHtml(title)}</h1>
                  <div class="warning">$warning</div>
                  <p class="meta">
                    Source session: <code>${escapeHtml(session.sessionId)}</code><br>
                    Target (public) session: <code>${escapeHtml(globalSession.sessionId)}</code>
                  </p>
                  $fileListHtml
                  <form method="POST" action="${escapeHtml(actionUrl)}">
                    $confirmButton
                    <a href="${escapeHtml(cancelUrl)}"><button type="button" class="cancel">Cancel</button></a>
                  </form>
                </body>
                </html>
            """.trimIndent()
            response.writer.use { it.write(html) }
        }
    
        private fun escapeHtml(s: String): String =
            s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    
        fun copyRecursively(source: File, destination: File) {
            try {
                if (source.isDirectory) {
                    destination.mkdirs()
                    source.listFiles()?.forEach { child ->
                        copyRecursively(child, File(destination, child.name))
                    }
                } else {
                    DocOpsApp.Companion.copyFileWithLineEndingNormalization(source, destination)
                }
            } catch (e: Exception) {
                DocOpsApp.Companion.log.error(
                    "Failed to copy file from ${source.absolutePath} to ${destination.absolutePath}: ${e.message}",
                    e
                )
            }
        }
    
    }