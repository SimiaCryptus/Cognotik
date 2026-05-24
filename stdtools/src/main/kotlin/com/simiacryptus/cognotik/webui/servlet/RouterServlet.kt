package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.AppEntry
import com.simiacryptus.cognotik.webui.application.authenticate
import com.simiacryptus.cognotik.webui.servlet.handler.FileAccessControl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File

class RouterServlet(
    val dataStorage: StorageInterface,
    val appTitle: String
) : HttpServlet() {
    val appEntry: AppEntry = AppEntry.values().find { it.appId == appTitle } ?: throw IllegalStateException("AppEntry with name '$appTitle' not found")
    val readme : String by lazy { appEntry.readme ?: throw IllegalStateException("AppEntry with name '$appTitle' not found or has no readme") }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        when {
            request.pathInfo == "/" -> renderGatewayPage(request, response)

            request.pathInfo == "/new" -> response.sendRedirect(
                "${request.contextPath}/fileIndex/${
                    request.getParameter(
                        "sessionId"
                    ) ?: Session.newUserID()
                }/app.html"
            )

            request.pathInfo == "/global" -> response.sendRedirect(
                "${request.contextPath}/fileIndex/${
                    request.getParameter(
                        "sessionId"
                    ) ?: Session.newGlobalID()
                }/app.html"
            )

            request.pathInfo.startsWith("/share/") -> share(
                request,
                response,
                Session(request.pathInfo.removePrefix("/share/").split('/').firstOrNull() ?: ""),
                authenticate(request, response) ?: run {
                    response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to share session"
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
                        HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to share session"
                    )
                    return
                }
                confirmShare(request, response, session, user)
            }

            else -> response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown path: ${request.pathInfo}")
        }
    }

    fun share(
        request: HttpServletRequest, response: HttpServletResponse, session: Session, user: User
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
        request: HttpServletRequest, response: HttpServletResponse, session: Session, user: User
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
                DocOpsApp.copyFileWithLineEndingNormalization(source, destination)
            }
            response.sendRedirect("${request.contextPath}/fileIndex/${globalSession.sessionId}/app.html")
        } catch (e: Exception) {
            LoggerFactory.getLogger(DocOpsApp::class.java).error(
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
        baseDir: File, source: File, destination: File, relativePath: String, result: MutableList<String>
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
                    :root {
                      --bg: #ffffff;
                      --fg: #222222;
                      --muted: #555555;
                      --panel-bg: #f9f9f9;
                      --panel-border: #dddddd;
                      --warning-bg: #fff3cd;
                      --warning-border: #ffeeba;
                      --code-bg: #f4f4f4;
                      --btn-bg: #f0f0f0;
                      --btn-border: #cccccc;
                      --btn-fg: #222222;
                    }
                    [data-theme="dark"] {
                      --bg: #1e1e1e;
                      --fg: #e6e6e6;
                      --muted: #b0b0b0;
                      --panel-bg: #2a2a2a;
                      --panel-border: #444444;
                      --warning-bg: #4a3f1a;
                      --warning-border: #6b5a24;
                      --code-bg: #2a2a2a;
                      --btn-bg: #333333;
                      --btn-border: #555555;
                      --btn-fg: #e6e6e6;
                    }
                    @media (prefers-color-scheme: dark) {
                      :root:not([data-theme="light"]) {
                        --bg: #1e1e1e;
                        --fg: #e6e6e6;
                        --muted: #b0b0b0;
                        --panel-bg: #2a2a2a;
                        --panel-border: #444444;
                        --warning-bg: #4a3f1a;
                        --warning-border: #6b5a24;
                        --code-bg: #2a2a2a;
                        --btn-bg: #333333;
                        --btn-border: #555555;
                        --btn-fg: #e6e6e6;
                      }
                    }
                    body { font-family: sans-serif; max-width: 800px; margin: 2em auto; padding: 0 1em;
                           background: var(--bg); color: var(--fg); }
                    h1, h2, h3 { color: var(--fg); }
                    code { background: var(--code-bg); padding: 0.1em 0.3em; border-radius: 3px; }
                    .warning { background: var(--warning-bg); border: 1px solid var(--warning-border);
                               padding: 1em; border-radius: 4px; margin-bottom: 1em; }
                    .file-list { max-height: 400px; overflow-y: auto; border: 1px solid var(--panel-border);
                                 padding: 0.5em 1.5em; background: var(--panel-bg); }
                    .file-list li { font-family: monospace; font-size: 0.9em; }
                    button { padding: 0.6em 1.2em; margin-right: 0.5em; font-size: 1em; cursor: pointer;
                             border-radius: 4px; border: 1px solid var(--btn-border);
                             background: var(--btn-bg); color: var(--btn-fg); }
                    button.danger { background: #d9534f; color: #fff; border-color: #d43f3a; }
                    button.cancel { background: var(--btn-bg); }
                    .meta { color: var(--muted); font-size: 0.9em; }
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
                 <script src="/modules/theme.js"></script>
                 <script>
                   if (window.ThemeManager) { window.ThemeManager.init(); }
                 </script>
                </body>
                </html>
            """.trimIndent()
        response.writer.use { it.write(html) }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun renderGatewayPage(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = "text/html; charset=UTF-8"
        response.status = HttpServletResponse.SC_OK
        val newSessionUrl = "${request.contextPath}/new"
        // Embed the readme content as a JSON-escaped JS string for safe inclusion in the page
        val readmeJs = jsStringEscape(readme)
        // Resolve display fields from AppEntry
        val displayName = appEntry.displayName.ifBlank { appTitle }
        val description = appEntry.description
        val icon = appEntry.icon
        val badge = appEntry.badge
        val badgeClass = appEntry.badgeClass
        val category = appEntry.category
        val tags = appEntry.tags
        val videoUrl = appEntry.videoUrl
        val exampleSessions = appEntry.exampleSessions ?: emptyMap()
        // Background and icon image URLs (served by a static resource handler based on appId)
        val backgroundUrl =
            if (appEntry.hasBackground) "/appDirectory/${appEntry.appId ?: appEntry.id}/background.png" else null

        val backgroundCss = if (backgroundUrl != null) {
            """
            .background-layer {
              position: fixed;
              top: 0; left: 0; right: 0; bottom: 0;
              background-image: url('${escapeHtml(backgroundUrl)}');
              background-size: cover;
              background-position: center;
              background-repeat: no-repeat;
              background-attachment: fixed;
              filter: saturate(0.35) brightness(0.85) blur(1px);
              opacity: 0.45;
              z-index: 0;
              pointer-events: none;
            }
            [data-theme="dark"] .background-layer {
              filter: saturate(0.3) brightness(0.5) blur(1px);
              opacity: 0.35;
            }
            @media (prefers-color-scheme: dark) {
              :root:not([data-theme="light"]) .background-layer {
                filter: saturate(0.3) brightness(0.5) blur(1px);
                opacity: 0.35;
              }
            }
            /* Ensure content sits above the background layer */
            body > *:not(.background-layer) {
              position: relative;
              z-index: 1;
            }
            /* Make body transparent so background-layer is visible behind content */
            body { background: transparent !important; }
            """.trimIndent()
        } else ""

        val backgroundHtml = if (backgroundUrl != null) {
            """<div class="background-layer" aria-hidden="true"></div>"""
        } else ""

        val iconHtml = """<span class="app-icon-emoji" aria-hidden="true">${escapeHtml(icon)}</span>"""

        val badgeHtml = if (!badge.isNullOrBlank()) {
            val cls = badgeClass?.let { " ${escapeHtml(it)}" } ?: ""
            """<span class="app-badge$cls">${escapeHtml(badge)}</span>"""
        } else ""

        val categoryHtml = if (!category.isNullOrBlank()) {
            """<span class="app-category">${escapeHtml(category)}</span>"""
        } else ""

        val tagsHtml = if (tags.isNotEmpty()) {
            buildString {
                append("""<div class="app-tags">""")
                tags.forEach {
                    append("""<span class="app-tag">""").append(escapeHtml(it)).append("</span>")
                }
                append("</div>")
            }
        } else ""

        val descriptionHtml = if (description.isNotBlank()) {
            """<p class="app-description">${escapeHtml(description)}</p>"""
        } else ""

        val videoHtml = if (!videoUrl.isNullOrBlank()) {
            // Support direct video files and YouTube/Vimeo embeds
            if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be") || videoUrl.contains("vimeo.com")) {
                """
                <div class="app-video">
                  <iframe src="${escapeHtml(videoUrl)}" frameborder="0" allowfullscreen
                          title="${escapeHtml(displayName)} demo"></iframe>
                </div>
                """.trimIndent()
            } else {
                """
                <div class="app-video">
                  <video controls preload="metadata">
                    <source src="${escapeHtml(videoUrl)}">
                    Your browser does not support the video tag.
                  </video>
                </div>
                """.trimIndent()
            }
        } else ""

        val examplesHtml = if (exampleSessions.isNotEmpty()) {
            buildString {
                append("""<div class="app-examples"><h2>Example Sessions</h2><ul class="examples-list">""")
                exampleSessions.forEach { (name, url) ->
                    append("""<li><a href="""").append(escapeHtml(url)).append("""">""")
                        .append(escapeHtml(name)).append("</a></li>")
                }
                append("</ul></div>")
            }
        } else ""

        val html = """
             <!DOCTYPE html>
             <html lang="en">
             <head>
               <meta charset="UTF-8">
               <title>${escapeHtml(displayName)}</title>
               <script src="/modules/theme.js"></script>
               <script src="/modules/marked.min.js"></script>
               <link rel="stylesheet" href="/menubar.css">
               <style>
                 :root {
                   --bg: #ffffff;
                   --fg: #222222;
                   --heading-fg: #333333;
                   --muted: #555555;
                   --panel-bg: #f9f9f9;
                   --panel-border: #dddddd;
                   --code-bg: #f4f4f4;
                   --link-fg: #ffffff;
                   --btn-primary-bg: #337ab7;
                   --btn-secondary-bg: #5bc0de;
                   --btn-border: #cccccc;
                   --top-bar-bg: #f4f4f4;
                   --top-bar-border: #dddddd;
                   --top-bar-fg: #222222;
                 }
                 [data-theme="dark"] {
                   --bg: #1e1e1e;
                   --fg: #e6e6e6;
                   --heading-fg: #f0f0f0;
                   --muted: #b0b0b0;
                   --panel-bg: #2a2a2a;
                   --panel-border: #444444;
                   --code-bg: #2a2a2a;
                   --link-fg: #ffffff;
                   --btn-primary-bg: #4a90c2;
                   --btn-secondary-bg: #5bc0de;
                   --btn-border: #555555;
                   --top-bar-bg: #2a2a2a;
                   --top-bar-border: #444444;
                   --top-bar-fg: #e6e6e6;
                 }
                 @media (prefers-color-scheme: dark) {
                   :root:not([data-theme="light"]) {
                     --bg: #1e1e1e;
                     --fg: #e6e6e6;
                     --heading-fg: #f0f0f0;
                     --muted: #b0b0b0;
                     --panel-bg: #2a2a2a;
                     --panel-border: #444444;
                     --code-bg: #2a2a2a;
                     --link-fg: #ffffff;
                     --btn-primary-bg: #4a90c2;
                     --btn-secondary-bg: #5bc0de;
                     --btn-border: #555555;
                     --top-bar-bg: #2a2a2a;
                     --top-bar-border: #444444;
                     --top-bar-fg: #e6e6e6;
                   }
                 }
                 html, body { background: var(--bg); color: var(--fg); }
                 body { font-family: sans-serif; margin: 0; padding: 0; line-height: 1.6; position: relative; min-height: 100vh; }
                 $backgroundCss
                 .page-container { max-width: 900px; margin: 2em auto; padding: 0 1em; }
                 h1, h2, h3 { color: var(--heading-fg); }
                 .app-header { display: flex; align-items: center; gap: 1em; margin-bottom: 0.5em; }
                 .app-icon { width: 64px; height: 64px; border-radius: 12px; object-fit: cover;
                             box-shadow: 0 2px 8px rgba(0,0,0,0.15); background: var(--panel-bg); }
                 .app-icon-emoji { font-size: 3rem; line-height: 1; }
                 .app-title-block { display: flex; flex-direction: column; gap: 0.25em; }
                 .app-title-block h1 { margin: 0; }
                 .app-meta { display: flex; flex-wrap: wrap; gap: 0.5em; align-items: center; }
                 .app-badge { display: inline-block; padding: 0.15em 0.6em; font-size: 0.8em;
                              background: var(--btn-primary-bg); color: var(--link-fg);
                              border-radius: 12px; font-weight: 600; }
                 .app-badge.new { background: #28a745; }
                 .app-badge.beta { background: #f0ad4e; color: #222; }
                 .app-badge.experimental { background: #d9534f; }
                 .app-category { display: inline-block; padding: 0.15em 0.6em; font-size: 0.8em;
                                 background: var(--panel-bg); color: var(--muted);
                                 border: 1px solid var(--panel-border); border-radius: 12px; }
                 .app-description { font-size: 1.05em; color: var(--muted); margin: 0.5em 0 1em 0; }
                 .app-tags { display: flex; flex-wrap: wrap; gap: 0.35em; margin: 0.5em 0 1em 0; }
                 .app-tag { display: inline-block; padding: 0.1em 0.5em; font-size: 0.75em;
                            background: var(--code-bg); color: var(--muted);
                            border: 1px solid var(--panel-border); border-radius: 4px; }
                 .app-video { margin: 1.5em 0; border-radius: 6px; overflow: hidden;
                              background: var(--panel-bg); border: 1px solid var(--panel-border); }
                 .app-video iframe, .app-video video { width: 100%; aspect-ratio: 16 / 9; display: block; border: 0; }
                 .app-examples { margin: 2em 0; padding: 1em; background: var(--panel-bg);
                                 border: 1px solid var(--panel-border); border-radius: 4px; }
                 .app-examples h2 { margin-top: 0; }
                 .examples-list { list-style: none; padding: 0; margin: 0; }
                 .examples-list li { margin: 0.35em 0; }
                 .examples-list a { color: var(--btn-primary-bg); text-decoration: none; }
                 .examples-list a:hover { text-decoration: underline; }
                 pre { background: var(--code-bg); padding: 1em; border-radius: 4px; overflow-x: auto; }
                 code { font-family: monospace; background: var(--code-bg); padding: 0.1em 0.3em; border-radius: 3px; }
                 pre code { background: none; padding: 0; }
                 .actions { margin: 2em 0; padding: 1em; background: var(--panel-bg);
                            border: 1px solid var(--panel-border); border-radius: 4px; }
                 .actions a { display: inline-block; padding: 0.6em 1.2em; margin-right: 0.5em; font-size: 1em;
                              cursor: pointer; border-radius: 4px; border: 1px solid var(--btn-border); text-decoration: none;
                              color: var(--link-fg); background: var(--btn-primary-bg); }
                 .actions a.secondary { background: var(--btn-secondary-bg); }
                 .readme { margin-top: 2em; }
                 /* Menubar styles */
                 .top-bar { display: flex; align-items: center; gap: 0.5em; padding: 0.5em 1em;
                            background: var(--top-bar-bg); border-bottom: 1px solid var(--top-bar-border);
                            color: var(--top-bar-fg); }
                 .top-bar .logo-container { display: flex; align-items: center; gap: 0.5em; }
                 .top-bar .logo { height: 28px; width: auto; }
                 .top-bar .logo-text { font-weight: bold; font-size: 1.1em; }
                 .top-bar-spacer { flex: 1; }
                 .top-bar .theme-selector { padding: 0.3em 0.5em; border-radius: 4px;
                                            border: 1px solid var(--btn-border);
                                            background: var(--panel-bg); color: var(--top-bar-fg); }
                 .top-bar .top-bar-btn { padding: 0.4em 0.8em; border-radius: 4px;
                                         border: 1px solid var(--btn-border);
                                         background: var(--panel-bg); color: var(--top-bar-fg); cursor: pointer; }
                 .top-bar .top-bar-btn:hover { filter: brightness(1.1); }
                 .top-bar .btn-icon { margin-right: 0.25em; }
               </style>
             </head>
             <body>
                 $backgroundHtml
                <div id="menubar-container"></div>
                <div class="page-container">
                  <div class="app-header">
                    $iconHtml
                    <div class="app-title-block">
                      <h1>${escapeHtml(displayName)}</h1>
                      <div class="app-meta">
                        $badgeHtml
                        $categoryHtml
                      </div>
                    </div>
                  </div>
                  $descriptionHtml
                  $tagsHtml
                  $videoHtml
                  <div class="actions">
                    <a href="${escapeHtml(newSessionUrl)}">Start New Session</a>
                  </div>
                  $examplesHtml
                  <div class="readme" id="readme">
                    <p><em>Loading documentation...</em></p>
                  </div>
                </div>
                <!-- Sessions Modal -->
                <div class="modal" id="sessions-modal" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.5); z-index:1000;">
                    <div class="modal-content" style="position:relative; background:var(--bg); margin:5vh auto; padding:1em; width:90%; max-width:1200px; height:85vh; display:flex; flex-direction:column; border-radius:6px;">
                        <span class="close" id="close-sessions-modal" style="position:absolute; top:10px; right:15px; font-size:24px; cursor:pointer; color:var(--fg);">&times;</span>
                        <h3 style="color:var(--heading-fg);">📁 Sessions</h3>
                        <iframe id="sessions-iframe" src="about:blank"
                                style="flex:1; width:100%; border:1px solid var(--panel-border); border-radius:4px; background:#fff;"
                                title="Sessions"></iframe>
                    </div>
                </div>
                <!-- Usage / Credits Modal -->
                <div class="modal" id="usage-modal" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.5); z-index:1000;">
                    <div class="modal-content" style="position:relative; background:var(--bg); margin:5vh auto; padding:1em; width:90%; max-width:1200px; height:85vh; display:flex; flex-direction:column; border-radius:6px;">
                        <span class="close" id="close-usage-modal" style="position:absolute; top:10px; right:15px; font-size:24px; cursor:pointer; color:var(--fg);">&times;</span>
                        <h3 style="color:var(--heading-fg);">📊 Usage &amp; Credits</h3>
                        <iframe id="usage-iframe" src="about:blank"
                                style="flex:1; width:100%; border:1px solid var(--panel-border); border-radius:4px; background:#fff;"
                                title="Usage and Credits"></iframe>
                    </div>
                </div>
                <!-- About Modal -->
                <div class="modal" id="about-modal" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.5); z-index:1000;">
                    <div class="modal-content" style="position:relative; background:var(--bg); margin:5vh auto; width:90%; max-width:1000px; max-height:90vh; overflow-y:auto; border-radius:6px;">
                        <div style="position:sticky; top:0; z-index:10; display:flex; justify-content:flex-end; padding:10px 14px; background:var(--panel-bg); border-bottom:1px solid var(--panel-border);">
                            <span class="close" id="close-about-modal" style="font-size:24px; cursor:pointer; color:var(--fg); line-height:1;">&times;</span>
                        </div>
                        <iframe id="about-iframe" src="about:blank"
                                style="width:100%; border:none; min-height:600px; display:block;"
                                title="About Cognotik"></iframe>
                    </div>
                </div>
                <script src="/modules/menubar.js"></script>
                <script>
                  (function() {
                   // Initialize theme manager as early as possible
                   if (window.ThemeManager) {
                     window.ThemeManager.init();
                   }
                    if (typeof Menubar === 'undefined') {
                      console.warn('Menubar module not loaded');
                      return;
                    }
                    var newSessionUrl = "${jsStringEscape(newSessionUrl)}";
                    // Generic helper to wire up an iframe modal
                    function setupIframeModal(opts) {
                      var modal = document.getElementById(opts.modalId);
                      var iframe = document.getElementById(opts.iframeId);
                      var closeBtn = opts.closeBtnId ? document.getElementById(opts.closeBtnId) : null;
                      if (!modal || !iframe) return null;
                      function open() {
                        iframe.src = opts.url;
                        modal.style.display = 'block';
                      }
                      function close() {
                        modal.style.display = 'none';
                        iframe.src = 'about:blank';
                      }
                      if (closeBtn) closeBtn.addEventListener('click', close);
                      modal.addEventListener('click', function(e) {
                        if (e.target === modal) close();
                      });
                      document.addEventListener('keydown', function(e) {
                        if (e.key === 'Escape' && modal.style.display === 'block') close();
                      });
                      return { open: open, close: close };
                    }
                    var sessionsModal = setupIframeModal({
                      modalId: 'sessions-modal',
                      iframeId: 'sessions-iframe',
                      closeBtnId: 'close-sessions-modal',
                      url: '/sessions/'
                    });
                    var usageModal = setupIframeModal({
                      modalId: 'usage-modal',
                      iframeId: 'usage-iframe',
                      closeBtnId: 'close-usage-modal',
                      url: '/usage/'
                    });
                    var aboutModal = setupIframeModal({
                      modalId: 'about-modal',
                      iframeId: 'about-iframe',
                      closeBtnId: 'close-about-modal',
                      url: '/about.html'
                    });
                      Menubar.render('#menubar-container', {
                      title: "${jsStringEscape(displayName)}",
                        logoSrc: '/logo.svg',
                        showLayoutSelector: false,
                        showThemeSelector: true,
                        titleClickable: true,
                        titleAriaLabel: 'About Cognotik',
                        onTitleClick: function() { if (aboutModal) aboutModal.open(); },
                        buttons: [
                          { id: 'gw-new-session', icon: '➕', label: 'New Session',
                          onClick: function() { window.location.href = newSessionUrl; } },
                          { id: 'gw-sessions-btn', icon: '📁', label: 'Sessions',
                            ariaLabel: 'Open Sessions',
                            onClick: function() { if (sessionsModal) sessionsModal.open(); } },
                          { id: 'gw-budget-btn', icon: '📊', label: 'Budget',
                            ariaLabel: 'Open Usage & Credits',
                            title: 'Usage and available credit balance',
                            onClick: function() { if (usageModal) usageModal.open(); } },
                          { id: 'gw-auth-btn', icon: '🔑', label: 'Login',
                            ariaLabel: 'Login',
                            onClick: function() { window.location.href = '/login/'; } }
                        ]
                      });
                   // Wire up the theme selector rendered by Menubar to ThemeManager
                   (function bindThemeSelector() {
                     if (!window.ThemeManager) return;
                     // Try a few times in case Menubar renders asynchronously
                     var attempts = 0;
                     var iv = setInterval(function() {
                       attempts++;
                       var sel = document.querySelector('#menubar-container .theme-selector')
                              || document.querySelector('.theme-selector');
                       if (sel) {
                         window.ThemeManager.bindSelector(sel);
                         clearInterval(iv);
                       } else if (attempts > 20) {
                         clearInterval(iv);
                       }
                     }, 50);
                   })();
                    // Update login/logout button based on current user
                    (function updateAuthButton() {
                      fetch('/userInfo', { headers: { 'Accept': 'application/json' } })
                        .then(function(r) { return r.ok ? r.json() : null; })
                        .then(function(user) {
                          var btn = document.getElementById('gw-auth-btn');
                          if (!btn) return;
                          if (user && (user.name || user.email || user.id)) {
                            var label = user.name || user.email || user.id;
                            btn.setAttribute('aria-label', 'Logout ' + label);
                            btn.setAttribute('title', 'Logout (' + label + ')');
                            btn.innerHTML = '<span class="btn-icon" aria-hidden="true">🚪</span> ' +
                              String(label).replace(/[&<>"']/g, function(c) {
                                return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
                              });
                            btn.onclick = function() {
                              var msg = 'Are you sure you want to log out as ' + label + '?';
                              if (window.confirm(msg)) {
                                fetch('/login/?action=logout', { method: 'POST' })
                                  .then(function() { location.reload(); });
                              }
                            };
                          } else {
                            btn.onclick = function() { window.location.href = '/login/'; };
                          }
                        })
                        .catch(function() { /* ignore */ });
                    })();
                  })();
                </script>
               <script>
                 (function() {
                   var readmeContent = "$readmeJs";
                   var container = document.getElementById('readme');
                   if (!readmeContent || readmeContent.length === 0) {
                     container.innerHTML = '<p><em>No documentation available.</em></p>';
                     return;
                   }
                   try {
                     if (typeof marked !== 'undefined') {
                       container.innerHTML = marked.parse(readmeContent);
                     } else {
                       container.innerText = readmeContent;
                     }
                   } catch (e) {
                     container.innerText = readmeContent;
                   }
                 })();
               </script>
             </body>
             </html>
         """.trimIndent()
        response.writer.use { it.write(html) }
    }

    private fun jsStringEscape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '<' -> sb.append("\\u003C")
                '>' -> sb.append("\\u003E")
                '&' -> sb.append("\\u0026")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }


    fun copyRecursively(source: File, destination: File) {
        try {
            if (source.isDirectory) {
                destination.mkdirs()
                source.listFiles()?.forEach { child ->
                    copyRecursively(child, File(destination, child.name))
                }
            } else {
                DocOpsApp.copyFileWithLineEndingNormalization(source, destination)
            }
        } catch (e: Exception) {
            DocOpsApp.log.error(
                "Failed to copy file from ${source.absolutePath} to ${destination.absolutePath}: ${e.message}", e
            )
        }
    }

}