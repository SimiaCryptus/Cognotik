package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.platform.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import com.simiacryptus.cognotik.webui.application.getCookie
import com.simiacryptus.cognotik.webui.servlet.handler.FsApiConfig
import com.simiacryptus.cognotik.webui.servlet.handler.FsApiRoute
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrors
import com.simiacryptus.cognotik.webui.servlet.handler.FsException
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder
import java.nio.file.Path.of

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
  private val metadataDB by lazy { ApplicationServices.fileApplicationServices().metadataDB }

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
      if (!session.isGlobal()) {
        if (user == null) {
          log.debug("FS API request rejected (unauthenticated): ${req.pathInfo}")
          FsErrors.write(
            resp, FsException(
              FsErrorCode.EACCES, "fsapi", null,
              "Not authenticated for session '$sessionId'; log in and retry"
            )
          )
          return
        } else if (req.method.uppercase() == "POST") {
          val sessionOwner = metadataDB.getSessionOwner(session) ?: user.id
          if (sessionOwner != user.id) {
            log.debug("FS API request rejected (user ${user.email} is not owner of session $sessionId; ${sessionOwner} is.)")
            FsErrors.write(
              resp, FsException(
                FsErrorCode.EACCES, "fsapi", null,
                "User '${user.email}' is not the owner of session '$sessionId'; only the session owner may modify its files"
              )
            )
            return
          }
        }
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
    return """<a class="zip-link" style="background-color:#6f42c1;" href="${req.contextPath}$webUiPath/?session=$encodedSession#$hash">🧭 Open in IDE view</a>""" +
        super.getToolbarActions(req, currentPath)
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
      val pathSegments = of(pathInfo ?: "/").normalize()
      if (pathSegments.toList().isEmpty()) {
        log.warn("Empty path segments for pathInfo: $pathInfo")
        throw RuntimeException("Invalid path: $pathInfo")
      }
      val session = Session(pathSegments.first().toString())
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
      val dataDir = dataStorage.getSystemDir(user, session)
      if (session.isGlobal() && dataDir.exists()) return dataDir
      try {
        onSession(session, user)
      } catch (e: Exception) {
        log.error("Error in onSession callback for session ${session.sessionId}, user ${user?.email}", e)
        throw e
      }
      val sessionDir = dataStorage.getUserDir(user, session)
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
  override val git: GitProvider = object : GitProvider(dataStorage) {
    override fun authenticate(request: HttpServletRequest, response: HttpServletResponse) =
      UserProviderImpl().authenticate(request, response)

    override fun onSession(session: Session, user: User?) {
      this@SessionFileServlet.onSession(session, user)
    }
  }

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val pathInfo = request.pathInfo ?: request.servletPath ?: "/"
    log.debug("doGet: pathInfo=$pathInfo, remoteAddr=${request.remoteAddr}")
    try {
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
        response.writer.write("""{"error": "Request failed: ${git.escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val pathInfo = request.pathInfo ?: request.servletPath ?: "/"
    log.debug("doPost: pathInfo=$pathInfo, remoteAddr=${request.remoteAddr}")
    try {
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
        response.writer.write("""{"error": "Request failed: ${git.escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  override fun listContents(
    file: File?,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Pair<String, String> {
    return try {
      file?.let {
        log.debug("listContents: delegating to super for ${it.absolutePath}")
        return super.listContents(it, request, response)
      }
      val pathInfo = request.pathInfo ?: request.servletPath
      log.debug("listContents: pathInfo=$pathInfo")
      val pathSegments = of(pathInfo ?: "/").normalize()
      if (pathSegments.toList().isEmpty()) {
        log.warn("listContents: empty path segments for pathInfo: $pathInfo")
        throw RuntimeException("Invalid path: $pathInfo")
      }
      val session = Session(pathSegments.toList().first().toString())
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
      val pathSegments = of(pathInfo).normalize()
      if (pathSegments.toList().isEmpty()) {
        // Let downstream handle invalid paths
        return true
      }
      val session = Session(pathSegments.toList().first().toString())
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