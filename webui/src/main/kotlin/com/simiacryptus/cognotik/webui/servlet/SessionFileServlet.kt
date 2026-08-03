package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import com.simiacryptus.cognotik.webui.application.getCookie
import com.simiacryptus.cognotik.webui.servlet.handler.FsApiConfig
import com.simiacryptus.cognotik.webui.servlet.handler.FsApiRoute
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrors
import com.simiacryptus.cognotik.webui.servlet.handler.FsException
import com.simiacryptus.cognotik.webui.servlet.util.PathUtils.parsePath
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URLEncoder

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 50,      // 50MB
    maxRequestSize = 1024 * 1024 * 100   // 100MB
)
open class SessionFileServlet(val dataStorage: StorageInterface) : FilesystemServlet() {
    companion object {
        private val log = LoggerFactory.getLogger(SessionFileServlet::class.java)
    }
     /**
      * Path (relative to the servlet context) where [WebUiServlet] is mounted.
      * See `ApplicationServer.configure`.
      */
     open val webUiPath: String = "/ui"
     /**
      * FS API capability switches. Unlike `FileServerCli` this server is multi-user and
      * usually reachable from more than loopback, so the *hardened* profile is the default:
      * no interactive terminals, and `/exec` limited to the read-mostly git allowlist.
      */
     open val fsApiReadOnly: Boolean = false
     open val fsApiTerminalEnabled: Boolean = false
     open val fsApiExecEnabled: Boolean = true
     /**
      * The FS API is dispatched from [FilesystemServlet.service] and therefore bypasses the
      * doGet/doPost pre-flight below; authenticate here or the session sandbox is wide open.
      */
     override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
         val route = FsApiRoute.parse(req.pathInfo ?: req.servletPath)
         if (route != null) {
             val sessionId = sessionIdOf(req)
             if (sessionId.isNullOrBlank() || sessionId == ".fsapi") {
                 log.warn("FS API request without a session prefix: ${req.pathInfo}")
                 FsErrors.write(
                     resp, FsException(
                         FsErrorCode.EINVAL, "fsapi", null,
                         "FS API requests must be scoped to a session: {mount}/<session>/.fsapi/v1/<op>"
                     )
                 )
                 return
             }
             // The FS API is consumed by fetch()-style clients (the IDE view) that expect a
             // JSON body on *every* response. isAuthenticatedForSession() is built for the
             // classic HTML browser and reacts to a missing/invalid session by issuing a 307
             // redirect to /login/...; to a fetch() client that redirect is indistinguishable
             // from a dead endpoint, which is exactly why the IDE view falls back to a generic
             // "No FS API endpoint answered /meta" message instead of an actionable one. Report
             // a proper FS API error here instead, consistent with the "missing session" case
             // above.
             val session = Session(sessionId)
             val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
             if (user == null && !session.isGlobal()) {
                 log.debug("FS API request rejected (unauthenticated): ${req.pathInfo}")
                 FsErrors.write(
                     resp, FsException(
                         FsErrorCode.EACCES, "fsapi", null,
                         "Not authenticated for session '$sessionId'; log in and retry"
                     )
                 )
                 return
             }
         }
         super.service(req, resp)
     }
     /** First path segment of the request (the session id), FS API routes included. */
     protected fun sessionIdOf(req: HttpServletRequest): String? {
         val raw = FsApiRoute.parse(req.pathInfo ?: req.servletPath)?.prefix
             ?: (req.pathInfo ?: req.servletPath ?: "/")
         return raw.split("/").firstOrNull { it.isNotBlank() }
     }
     /**
      * Node-space "/" for this mount. Deliberately *not* [getDir]: the FS API must not
      * perform the `.md -> .html/.pdf` substitution that the HTML browser does (nodejs.md A4),
      * and it must always resolve against the writable user directory.
      */
     override fun getFsApiRoot(req: HttpServletRequest, resp: HttpServletResponse): File? {
         val sessionId = sessionIdOf(req) ?: return null
         val session = Session(sessionId)
         val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
         if (user == null && !session.isGlobal()) {
             log.warn("FS API: no user for session ${session.sessionId}")
             return null
         }
         onSession(session, user)
         return dataStorage.getUserDir(user, session).apply { if (!exists()) mkdirs() }
     }
     override fun getFsApiConfig(req: HttpServletRequest): FsApiConfig = FsApiConfig(
         readOnly = fsApiReadOnly,
         execAllowlist = if (fsApiExecEnabled && isGitEnabled(req)) mapOf("git" to GIT_SUBCOMMANDS) else emptyMap(),
         execAllowAny = false,
         execRestrictArguments = true,
         terminalEnabled = fsApiTerminalEnabled && !fsApiReadOnly,
     )
     /**
      * docs/ui.md §21.3 — the classic listing links to the equivalent SPA path.
      * The SPA is a shared, session-agnostic mount; it derives the FS API base itself
      * from `?session=<id>` (siblings `/ui/` and `/fileIndex/<id>/.fsapi/v1`), with the
      * directory carried in the location hash.
      */
     override fun getToolbarActions(req: HttpServletRequest, currentPath: String): String {
         val sessionId = sessionIdOf(req) ?: return ""
         val hash = if (currentPath.isBlank()) "/" else "/$currentPath/"
         val encodedSession = URLEncoder.encode(sessionId, "UTF-8")
        return """<a class="zip-link" style="background-color:#6f42c1;" href="${req.contextPath}$webUiPath/?session=$encodedSession#$hash">🧭 Open in IDE view</a>"""
     }
     /**
      * Directory-listing GET requests (e.g. `/fileIndex/<session>/`) are redirected to the
      * new IDE-style UI by default, so the legacy HTML browser is only shown when explicitly
      * requested via `?legacy=1`. The underlying FS API and file-serving endpoints (used by
      * both UIs, and by any legacy integrations) are unaffected.
      */
     override fun newUiRedirectUrl(req: HttpServletRequest, currentPath: String): String? {
         val sessionId = sessionIdOf(req) ?: return null
         val hash = if (currentPath.isBlank()) "/" else "/$currentPath/"
         val encodedSession = URLEncoder.encode(sessionId, "UTF-8")
        return "${req.contextPath}$webUiPath/?session=$encodedSession#$hash"
     }


    override fun getDir(request: HttpServletRequest, response: HttpServletResponse): File? {
        return try {
            val pathInfo = request.pathInfo ?: request.servletPath
            log.debug("getDir called with pathInfo: $pathInfo")
            val pathSegments = parsePath(pathInfo ?: "/")
            if (pathSegments.isEmpty()) {
                log.warn("Empty path segments for pathInfo: $pathInfo")
                throw RuntimeException("Invalid path: $pathInfo")
            }
            val session = Session(pathSegments.first())
            log.debug("Resolved session: ${session.sessionId}")
            val cookie = request.getCookie()
            val user = ApplicationServices.authenticationManager.getUser(cookie)
            if (user == null && !session.isGlobal()) {
                log.warn("No user found for token (cookie present: ${cookie != null}) for session ${session.sessionId}; redirecting to login")
                response.status = HttpServletResponse.SC_TEMPORARY_REDIRECT
                val originalRequest = request.requestURL.toString()
                val queryString = request.queryString
                val targetUrl = if (queryString != null) "$originalRequest?$queryString" else originalRequest
                val encodedTarget = URLEncoder.encode(targetUrl, "UTF-8")
                response.setHeader("Location", "/login/?target=$encodedTarget")
                return null
            }
            log.debug("Authenticated user: ${user?.email} for session ${session.sessionId}")
            try {
                onSession(session, user)
            } catch (e: Exception) {
                log.error("Error in onSession callback for session ${session.sessionId}, user ${user?.email}", e)
                throw e
            }
            val sessionDir = dataStorage.getUserDir(user, session)
            val dataDir = dataStorage.getSystemDir(user, session)
            log.debug("sessionDir=${sessionDir.absolutePath}, dataDir=${dataDir.absolutePath}")
            val dirs = if (sessionDir.absolutePath != dataDir.absolutePath) {
                listOf(sessionDir, dataDir)
            } else {
                listOf(sessionDir)
            }

            // First, try to find the exact file
            val path = pathSegments.drop(1).joinToString("/")
            log.debug("Looking for path '$path' in dirs: ${dirs.map { it.absolutePath }}")
            val exactMatch = dirs.firstOrNull { File(it, path).exists() }
            if (exactMatch != null) {
                log.debug("Exact match found in: ${exactMatch.absolutePath}")
                return exactMatch
            }

            // If not found, check if this is a request for HTML/PDF with an equivalent .md file
            val requestedFile = File(dirs.first(), path)
            val fileName = requestedFile.name
            if (fileName.endsWith(".html") || fileName.endsWith(".pdf") || fileName.endsWith(".txt")) {
                val mdFileName = fileName.substringBeforeLast(".") + ".md"
                log.debug("Checking for markdown equivalent: $mdFileName")
                val mdMatch = dirs.firstOrNull {
                    val mdFile = File(File(it, path).parentFile, mdFileName)
                    mdFile.exists() && mdFile.isFile
                }
                if (mdMatch != null) {
                    log.debug("Markdown equivalent found in: ${mdMatch.absolutePath}")
                    return mdMatch
                }
            }
            log.debug("No match found, returning first dir: ${dirs.firstOrNull()?.absolutePath}")
            dirs.firstOrNull()
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            log.error("Unexpected error in getDir for path: ${request.pathInfo ?: request.servletPath}", e)
            throw RuntimeException("Failed to resolve directory: ${e.message}", e)
        }
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        val pathInfo = request.pathInfo ?: request.servletPath ?: "/"
        log.debug("doGet: pathInfo=$pathInfo, remoteAddr=${request.remoteAddr}")
        try {
            // Handle git API endpoints
            if (pathInfo.contains("/.git/api/")) {
                log.debug("Routing to git API GET handler for path: $pathInfo")
                handleGitApiGet(request, response, pathInfo)
                return
            }
            // Pre-flight auth check: if getDir would redirect to login, honor that and stop
            // before super.doGet attempts to call listContents (which would throw).
            if (!isAuthenticatedForSession(request, response)) {
                log.debug("doGet: not authenticated, redirect already issued by isAuthenticatedForSession")
                return
            }
            super.doGet(request, response)
        } catch (e: Exception) {
            log.error("Error in doGet for path: $pathInfo", e)
            if (!response.isCommitted) {
                response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                response.contentType = "application/json"
                response.writer.write("""{"error": "Request failed: ${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        val pathInfo = request.pathInfo ?: request.servletPath ?: "/"
        log.debug("doPost: pathInfo=$pathInfo, remoteAddr=${request.remoteAddr}")
        try {
            // Handle git API endpoints
            if (pathInfo.contains("/.git/api/")) {
                log.debug("Routing to git API POST handler for path: $pathInfo")
                handleGitApiPost(request, response, pathInfo)
                return
            }
            if (!isAuthenticatedForSession(request, response)) {
                log.debug("doPost: not authenticated, redirect already issued by isAuthenticatedForSession")
                return
            }
            super.doPost(request, response)
        } catch (e: Exception) {
            log.error("Error in doPost for path: $pathInfo", e)
            if (!response.isCommitted) {
                response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                response.contentType = "application/json"
                response.writer.write("""{"error": "Request failed: ${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    private fun handleGitApiGet(request: HttpServletRequest, response: HttpServletResponse, pathInfo: String) {
        log.info("handleGitApiGet: pathInfo=$pathInfo")
        try {
            val pathSegments = parsePath(pathInfo)
            if (pathSegments.isEmpty()) {
                log.warn("Empty path segments for git API GET: $pathInfo")
                response.status = HttpServletResponse.SC_BAD_REQUEST
                response.contentType = "application/json"
                response.writer.write("""{"error": "Invalid path"}""")
                return
            }
            val session = Session(pathSegments.first())
            log.debug("Git API GET session: ${session.sessionId}")
            val user = UserProviderImpl().authenticate(request, response) ?: run {
                log.warn("Authentication failed for git API GET on session ${session.sessionId}")
                throw IllegalStateException("Authentication failed")
            }
            log.debug("Git API GET authenticated user: ${user.email}")
            onSession(session, user)
            val sessionDir = dataStorage.getUserDir(user, session)
            // Extract the git API action from the path
            val gitApiIndex = pathSegments.indexOf(".git")
            if (gitApiIndex == -1 || gitApiIndex + 2 >= pathSegments.size) {
                log.warn("Invalid git API path structure: $pathInfo (gitApiIndex=$gitApiIndex, segments=${pathSegments.size})")
                response.status = HttpServletResponse.SC_BAD_REQUEST
                response.contentType = "application/json"
                response.writer.write("""{"error": "Invalid git API path"}""")
                return
            }
            val action = pathSegments[gitApiIndex + 2] // .git/api/<action>
            log.info("Git API GET action: $action for session ${session.sessionId}, user ${user.email}")
            when (action) {
                "status" -> gitStatus(sessionDir, response)
                "branches" -> gitListBranches(sessionDir, response)
                "log" -> gitLog(sessionDir, request, response)
                else -> {
                    log.warn("Unknown git GET action: $action")
                    response.status = HttpServletResponse.SC_BAD_REQUEST
                    response.contentType = "application/json"
                    response.writer.write("""{"error": "Unknown git GET action: $action"}""")
                }
            }
        } catch (e: IllegalStateException) {
            log.warn("Git API GET authentication/state error: ${e.message}")
            if (!response.isCommitted) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = "application/json"
                response.writer.write("""{"error": "${escapeJson(e.message ?: "Authentication failed")}"}""")
            }
        } catch (e: Exception) {
            log.error("Error handling git API GET request", e)
            if (!response.isCommitted) {
                response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                response.contentType = "application/json"
                response.writer.write("""{"error": "Git operation failed: ${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    private fun handleGitApiPost(request: HttpServletRequest, response: HttpServletResponse, pathInfo: String) {
        log.info("handleGitApiPost: pathInfo=$pathInfo")
        try {
            val pathSegments = parsePath(pathInfo)
            if (pathSegments.isEmpty()) {
                log.warn("Empty path segments for git API POST: $pathInfo")
                response.status = HttpServletResponse.SC_BAD_REQUEST
                response.contentType = "application/json"
                response.writer.write("""{"error": "Invalid path"}""")
                return
            }
            val session = Session(pathSegments.first())
            log.debug("Git API POST session: ${session.sessionId}")
            val user = UserProviderImpl().authenticate(request, response) ?: run {
                log.warn("Authentication failed for git API POST on session ${session.sessionId}")
                throw IllegalStateException("Authentication failed")
            }
            log.debug("Git API POST authenticated user: ${user.email}")
            onSession(session, user)
            val sessionDir = dataStorage.getUserDir(user, session)
            val gitApiIndex = pathSegments.indexOf(".git")
            if (gitApiIndex == -1 || gitApiIndex + 2 >= pathSegments.size) {
                log.warn("Invalid git API path structure: $pathInfo (gitApiIndex=$gitApiIndex, segments=${pathSegments.size})")
                response.status = HttpServletResponse.SC_BAD_REQUEST
                response.contentType = "application/json"
                response.writer.write("""{"error": "Invalid git API path"}""")
                return
            }
            val action = pathSegments[gitApiIndex + 2]
            log.info("Git API POST action: $action for session ${session.sessionId}, user ${user.email}")
            when (action) {
                "init" -> gitInit(sessionDir, response)
                "commit" -> {
                    val body = try {
                        request.reader.readText()
                    } catch (e: Exception) {
                        log.error("Failed to read request body for commit action", e)
                        ""
                    }
                    log.debug("Commit request body length: ${body.length}")
                    val message = parseJsonField(body, "message") ?: "Auto-commit"
                    gitCommit(sessionDir, message, response)
                }

                "checkout" -> {
                    val body = try {
                        request.reader.readText()
                    } catch (e: Exception) {
                        log.error("Failed to read request body for checkout action", e)
                        ""
                    }
                    log.debug("Checkout request body length: ${body.length}")
                    val branch = parseJsonField(body, "branch")
                    val create = parseJsonField(body, "create")?.toBoolean() ?: false
                    if (branch.isNullOrBlank()) {
                        log.warn("Checkout request missing branch name")
                        response.status = HttpServletResponse.SC_BAD_REQUEST
                        response.contentType = "application/json"
                        response.writer.write("""{"error": "Branch name is required"}""")
                        return
                    }
                    gitCheckout(sessionDir, branch, create, response)
                }

                else -> {
                    log.warn("Unknown git POST action: $action")
                    response.status = HttpServletResponse.SC_BAD_REQUEST
                    response.contentType = "application/json"
                    response.writer.write("""{"error": "Unknown git POST action: $action"}""")
                }
            }
        } catch (e: IllegalStateException) {
            log.warn("Git API POST authentication/state error: ${e.message}")
            if (!response.isCommitted) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = "application/json"
                response.writer.write("""{"error": "${escapeJson(e.message ?: "Authentication failed")}"}""")
            }
        } catch (e: Exception) {
            log.error("Error handling git API POST request", e)
            if (!response.isCommitted) {
                response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                response.contentType = "application/json"
                response.writer.write("""{"error": "Git operation failed: ${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    /**
     * Initialize a git repository in the session directory.
     */
    private fun gitInit(sessionDir: File, resp: HttpServletResponse) {
        log.info("Initializing git repository in: ${sessionDir.absolutePath}")
        try {
            if (!sessionDir.exists()) {
                log.warn("Session directory does not exist, creating: ${sessionDir.absolutePath}")
                if (!sessionDir.mkdirs()) {
                    log.error("Failed to create session directory: ${sessionDir.absolutePath}")
                    resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    resp.contentType = "application/json"
                    resp.writer.write("""{"success": false, "error": "Failed to create session directory"}""")
                    return
                }
            }
            val gitDir = File(sessionDir, ".git")
            if (gitDir.exists()) {
                log.info("Git repository already exists in: ${sessionDir.absolutePath}")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": true, "message": "Git repository already initialized", "path": "${
                        escapeJson(sessionDir.absolutePath)
                    }"}"""
                )
                return
            }
            val result = executeGitCommand(sessionDir, "git", "init")
            if (result.exitCode == 0) {
                log.info("Git init succeeded, creating initial commit")
                // Perform an initial commit so the repo has a valid HEAD
                executeGitCommand(sessionDir, "git", "add", "-A")
                executeGitCommand(sessionDir, "git", "commit", "-m", "Initial commit", "--allow-empty")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": true, "message": "Git repository initialized", "output": "${escapeJson(result.output)}", "path": "${
                        escapeJson(sessionDir.absolutePath)
                    }"}"""
                )
            } else {
                log.error("Git init failed with exit code ${result.exitCode}: ${result.error}")
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": false, "error": "${escapeJson(result.error)}", "output": "${
                        escapeJson(
                            result.output
                        )
                    }"}"""
                )
            }
        } catch (e: Exception) {
            log.error("Exception during gitInit for ${sessionDir.absolutePath}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    /**
     * List all branches in the session's git repository.
     */
    private fun gitListBranches(sessionDir: File, resp: HttpServletResponse) {
        log.info("Listing git branches in: ${sessionDir.absolutePath}")
        try {
            ensureGitRepo(sessionDir)
            val result = executeGitCommand(sessionDir, "git", "branch", "-a", "--no-color")
            if (result.exitCode == 0) {
                val branches = result.output.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val isCurrent = line.trimStart().startsWith("*")
                        val name = line.trimStart().removePrefix("* ").removePrefix("  ").trim()
                        """{"name": "${escapeJson(name)}", "current": $isCurrent}"""
                    }
                log.debug("Found ${branches.size} branches in ${sessionDir.absolutePath}")
                // Also get the current branch name
                val currentBranchResult = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD")
                val currentBranch = currentBranchResult.output.trim()
                log.debug("Current branch: $currentBranch")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": true, "currentBranch": "${escapeJson(currentBranch)}", "branches": [${
                        branches.joinToString(", ")
                    }]}"""
                )
            } else {
                log.error("git branch failed with exit code ${result.exitCode}: ${result.error}")
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
            }
        } catch (e: Exception) {
            log.error("Exception during gitListBranches for ${sessionDir.absolutePath}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    /**
     * Checkout a branch in the session's git repository.
     */
    private fun gitCheckout(sessionDir: File, branch: String, create: Boolean, resp: HttpServletResponse) {
        log.info("Checking out branch '$branch' (create=$create) in: ${sessionDir.absolutePath}")
        try {
            ensureGitRepo(sessionDir)
            // Validate branch name
            if (!isValidBranchName(branch)) {
                log.warn("Invalid branch name rejected: '$branch'")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "Invalid branch name: ${escapeJson(branch)}"}""")
                return
            }
            val args = mutableListOf("git", "checkout")
            if (create) {
                args.add("-b")
            }
            args.add(branch)
            val result = executeGitCommand(sessionDir, *args.toTypedArray())
            if (result.exitCode == 0) {
                log.info("Successfully checked out branch '$branch' in ${sessionDir.absolutePath}")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": true, "message": "Checked out branch '${escapeJson(branch)}'", "output": "${
                        escapeJson(result.output)
                    }"}"""
                )
            } else {
                log.error("git checkout failed for branch '$branch' with exit code ${result.exitCode}: ${result.error}")
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": false, "error": "${escapeJson(result.error)}", "output": "${
                        escapeJson(
                            result.output
                        )
                    }"}"""
                )
            }
        } catch (e: Exception) {
            log.error("Exception during gitCheckout for branch '$branch' in ${sessionDir.absolutePath}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    /**
     * Commit all changes in the session's git repository.
     */
    private fun gitCommit(sessionDir: File, message: String, resp: HttpServletResponse) {
        log.info("Committing changes in: ${sessionDir.absolutePath} with message: $message")
        try {
            ensureGitRepo(sessionDir)
            // Stage all changes
            val addResult = executeGitCommand(sessionDir, "git", "add", "-A")
            if (addResult.exitCode != 0) {
                log.error("git add failed with exit code ${addResult.exitCode}: ${addResult.error}")
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "Failed to stage changes: ${escapeJson(addResult.error)}"}""")
                return
            }
            // Check if there are changes to commit
            val statusResult = executeGitCommand(sessionDir, "git", "status", "--porcelain")
            if (statusResult.exitCode == 0 && statusResult.output.isBlank()) {
                log.info("Nothing to commit in ${sessionDir.absolutePath}")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write("""{"success": true, "message": "Nothing to commit, working tree clean"}""")
                return
            }
            log.debug("Staged changes detected, proceeding with commit")
            val commitResult = executeGitCommand(
                sessionDir, "git", "commit", "-m", message,
                "--author=SessionFileServlet <noreply@localhost>"
            )
            if (commitResult.exitCode == 0) {
                // Get the commit hash
                val hashResult = executeGitCommand(sessionDir, "git", "rev-parse", "HEAD")
                val commitHash = hashResult.output.trim()
                log.info("Commit successful: $commitHash in ${sessionDir.absolutePath}")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": true, "message": "Changes committed", "commitHash": "${escapeJson(commitHash)}", "output": "${
                        escapeJson(commitResult.output)
                    }"}"""
                )
            } else {
                log.error("git commit failed with exit code ${commitResult.exitCode}: ${commitResult.error}")
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write(
                    """{"success": false, "error": "${escapeJson(commitResult.error)}", "output": "${
                        escapeJson(commitResult.output)
                    }"}"""
                )
            }
        } catch (e: Exception) {
            log.error("Exception during gitCommit for ${sessionDir.absolutePath}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    /**
     * Get the git status of the session directory.
     */
    private fun gitStatus(sessionDir: File, resp: HttpServletResponse) {
        log.info("Getting git status for: ${sessionDir.absolutePath}")
        try {
            val gitDir = File(sessionDir, ".git")
            if (!gitDir.exists()) {
                log.debug("No git repository at ${sessionDir.absolutePath}")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write("""{"success": true, "initialized": false, "message": "Not a git repository"}""")
                return
            }
            val result = executeGitCommand(sessionDir, "git", "status", "--porcelain")
            val branchResult = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD")
            val currentBranch = branchResult.output.trim()
            val changes = result.output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                try {
                    if (line.length < 3) {
                        log.warn("Unexpectedly short status line: '$line'")
                        return@mapNotNull null
                    }
                    val status = line.substring(0, 2).trim()
                    val file = line.substring(3).trim()
                    """{"status": "${escapeJson(status)}", "file": "${escapeJson(file)}"}"""
                } catch (e: Exception) {
                    log.warn("Failed to parse status line: '$line'", e)
                    null
                }
            }
            log.debug("Git status: branch=$currentBranch, ${changes.size} changes")
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write(
                """{"success": true, "initialized": true, "currentBranch": "${escapeJson(currentBranch)}", "clean": ${changes.isEmpty()}, "changes": [${
                    changes.joinToString(", ")
                }]}"""
            )
        } catch (e: Exception) {
            log.error("Exception during gitStatus for ${sessionDir.absolutePath}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    /**
     * Get the git log for the session directory.
     */
    private fun gitLog(sessionDir: File, req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Getting git log for: ${sessionDir.absolutePath}")
        try {
            ensureGitRepo(sessionDir)
            val maxCount = req.getParameter("maxCount")?.toIntOrNull() ?: 20
            log.debug("git log maxCount=$maxCount")
            val result = executeGitCommand(
                sessionDir, "git", "log",
                "--format=%H%n%an%n%ae%n%aI%n%s",
                "-n", maxCount.coerceIn(1, 100).toString()
            )
            if (result.exitCode == 0) {
                val lines = result.output.lines().filter { it.isNotBlank() }
                val commits = mutableListOf<String>()
                var i = 0
                while (i + 4 < lines.size) {
                    try {
                        val hash = lines[i]
                        val authorName = lines[i + 1]
                        val authorEmail = lines[i + 2]
                        val date = lines[i + 3]
                        val subject = lines[i + 4]
                        commits.add(
                            """{"hash": "${escapeJson(hash)}", "author": "${escapeJson(authorName)}", "email": "${
                                escapeJson(authorEmail)
                            }", "date": "${escapeJson(date)}", "message": "${escapeJson(subject)}"}"""
                        )
                    } catch (e: Exception) {
                        log.warn("Failed to parse commit at line $i", e)
                    }
                    i += 5
                }
                log.debug("Parsed ${commits.size} commits from git log")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write("""{"success": true, "commits": [${commits.joinToString(", ")}]}""")
            } else {
                log.error("git log failed with exit code ${result.exitCode}: ${result.error}")
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
            }
        } catch (e: Exception) {
            log.error("Exception during gitLog for ${sessionDir.absolutePath}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    /**
     * Ensure the session directory has a git repository initialized.
     * If not, initialize one.
     */
    private fun ensureGitRepo(sessionDir: File) {
        val gitDir = File(sessionDir, ".git")
        if (!gitDir.exists()) {
            log.info("Auto-initializing git repository in: ${sessionDir.absolutePath}")
            try {
                if (!sessionDir.exists()) {
                    log.warn("Session directory does not exist, creating: ${sessionDir.absolutePath}")
                    if (!sessionDir.mkdirs()) {
                        log.error("Failed to create session directory: ${sessionDir.absolutePath}")
                        throw RuntimeException("Failed to create session directory: ${sessionDir.absolutePath}")
                    }
                }
                val initResult = executeGitCommand(sessionDir, "git", "init")
                if (initResult.exitCode != 0) {
                    log.error("Auto-init git failed: ${initResult.error}")
                    throw RuntimeException("git init failed: ${initResult.error}")
                }
                executeGitCommand(sessionDir, "git", "add", "-A")
                executeGitCommand(sessionDir, "git", "commit", "-m", "Initial commit", "--allow-empty")
                log.info("Auto-initialized git repository in ${sessionDir.absolutePath}")
            } catch (e: Exception) {
                log.error("Failed to auto-initialize git repository in ${sessionDir.absolutePath}", e)
                throw e
            }
        } else {
            log.debug("Git repository already exists at ${gitDir.absolutePath}")
        }
    }

    private fun isValidBranchName(name: String): Boolean {
        // Basic validation for git branch names
        return name.isNotBlank() &&
                !name.contains("..") &&
                !name.contains("~") &&
                !name.contains("^") &&
                !name.contains(":") &&
                !name.contains("\\") &&
                !name.contains(" ") &&
                !name.startsWith("-") &&
                !name.endsWith(".lock") &&
                !name.endsWith(".") &&
                !name.contains("@{") &&
                name.all { it.code >= 33 && it.code <= 126 }
    }

    private data class GitResult(val exitCode: Int, val output: String, val error: String)

    private fun executeGitCommand(workingDir: File, vararg command: String): GitResult {
        log.info("Executing git command: ${command.joinToString(" ")} in ${workingDir.absolutePath}")
        val startTime = System.currentTimeMillis()
        return try {
            if (!workingDir.exists()) {
                log.error("Working directory does not exist: ${workingDir.absolutePath}")
                return GitResult(-1, "", "Working directory does not exist: ${workingDir.absolutePath}")
            }
            if (!workingDir.isDirectory) {
                log.error("Working directory is not a directory: ${workingDir.absolutePath}")
                return GitResult(-1, "", "Working directory is not a directory: ${workingDir.absolutePath}")
            }
            val processBuilder = ProcessBuilder(*command)
                .directory(workingDir)
                .redirectErrorStream(false)
            // Set minimal git config for commits if not configured
            processBuilder.environment()["GIT_AUTHOR_NAME"] = "SessionFileServlet"
            processBuilder.environment()["GIT_AUTHOR_EMAIL"] = "noreply@localhost"
            processBuilder.environment()["GIT_COMMITTER_NAME"] = "SessionFileServlet"
            processBuilder.environment()["GIT_COMMITTER_EMAIL"] = "noreply@localhost"
            val process = processBuilder.start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            val elapsed = System.currentTimeMillis() - startTime
            if (exitCode != 0) {
                log.warn("Git command exited with code $exitCode in ${elapsed}ms: ${command.joinToString(" ")} - stderr: $error")
            } else {
                log.debug("Git command completed successfully in ${elapsed}ms: ${command.joinToString(" ")}")
            }
            GitResult(exitCode, output, error)
        } catch (e: InterruptedException) {
            log.error("Git command interrupted: ${command.joinToString(" ")}", e)
            Thread.currentThread().interrupt()
            GitResult(-1, "", "Command interrupted: ${e.message}")
        } catch (e: java.io.IOException) {
            log.error("IO error executing git command (is git installed?): ${command.joinToString(" ")}", e)
            GitResult(-1, "", "IO error: ${e.message}")
        } catch (e: Exception) {
            log.error("Failed to execute git command: ${command.joinToString(" ")}", e)
            GitResult(-1, "", e.message ?: "Unknown error")
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Simple JSON field parser for request bodies.
     * Extracts the value of a given field from a JSON string.
     */
    private fun parseJsonField(json: String, field: String): String? {
        return try {
            if (json.isBlank()) {
                log.debug("parseJsonField: empty JSON body for field '$field'")
                return null
            }
            val pattern = """"$field"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
            val match = pattern.find(json)
            if (match == null) {
                log.debug("parseJsonField: field '$field' not found in JSON")
                return null
            }
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        } catch (e: Exception) {
            log.warn("Failed to parse JSON field '$field' from body", e)
            null
        }
    }


    override fun listContents(file: File?, request: HttpServletRequest, response: HttpServletResponse): Pair<String, String> {
        return try {
            file?.let {
                log.debug("listContents: delegating to super for ${it.absolutePath}")
                return super.listContents(it, request, response)
            }
            val pathInfo = request.pathInfo ?: request.servletPath
            log.debug("listContents: pathInfo=$pathInfo")
            val pathSegments = parsePath(pathInfo ?: "/")
            if (pathSegments.isEmpty()) {
                log.warn("listContents: empty path segments for pathInfo: $pathInfo")
                throw RuntimeException("Invalid path: $pathInfo")
            }
            val session = Session(pathSegments.first())
            val cookie = request.getCookie(AuthenticationInterface.AUTH_COOKIE)
            val user = ApplicationServices.authenticationManager.getUser(cookie)
            if (user == null && !session.isGlobal()) {
                log.warn("listContents: could not find user for token (cookie present: ${cookie != null}) for session ${session.sessionId}; redirecting to login")
                if (!response.isCommitted) {
                    response.status = HttpServletResponse.SC_TEMPORARY_REDIRECT
                    val originalRequest = request.requestURL.toString()
                    val queryString = request.queryString
                    val targetUrl = if (queryString != null) "$originalRequest?$queryString" else originalRequest
                    val encodedTarget = URLEncoder.encode(targetUrl, "UTF-8")
                    response.setHeader("Location", "/login/?target=$encodedTarget")
                }
                return Pair("", "")
            }
            log.debug("listContents: user=${user?.email}, session=${session.sessionId}")
            try {
                onSession(session, user)
            } catch (e: Exception) {
                log.error("Error in onSession callback during listContents", e)
                throw e
            }
            val sessionPair = listContents(dataStorage.getUserDir(user, session), request, response)
            val dataPair = listContents(dataStorage.getSystemDir(user, session), request, response)
            Pair(sessionPair.first + dataPair.first, sessionPair.second + dataPair.second)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            log.error("Unexpected error in listContents for path: ${request.pathInfo ?: request.servletPath}", e)
            throw RuntimeException("Failed to list contents: ${e.message}", e)
        }
    }

    open fun onSession(session: Session, user: User?) {

    }
    /**
     * Returns true if the request is authenticated (or the session is global so no auth required).
     * If not authenticated, writes a 307 redirect to the login page and returns false.
     * The caller should return immediately after a false result.
     */
     protected fun isAuthenticatedForSession(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        return try {
            val pathInfo = request.pathInfo ?: request.servletPath ?: "/"
            val pathSegments = parsePath(pathInfo)
            if (pathSegments.isEmpty()) {
                // Let downstream handle invalid paths
                return true
            }
            val session = Session(pathSegments.first())
            val cookie = request.getCookie()
            val user = ApplicationServices.authenticationManager.getUser(cookie)
            if (user == null && !session.isGlobal()) {
                log.debug("isAuthenticatedForSession: no user for token (cookie present: ${cookie != null}) for session ${session.sessionId}; redirecting to login")
                if (!response.isCommitted) {
                    response.status = HttpServletResponse.SC_TEMPORARY_REDIRECT
                    val originalRequest = request.requestURL.toString()
                    val queryString = request.queryString
                    val targetUrl = if (queryString != null) "$originalRequest?$queryString" else originalRequest
                    val encodedTarget = URLEncoder.encode(targetUrl, "UTF-8")
                    response.setHeader("Location", "/login/?target=$encodedTarget")
                }
                false
            } else {
                true
            }
        } catch (e: Exception) {
            log.error("Error during pre-flight auth check", e)
            // Fall through to normal processing; downstream will handle errors
            true
        }
    }
}