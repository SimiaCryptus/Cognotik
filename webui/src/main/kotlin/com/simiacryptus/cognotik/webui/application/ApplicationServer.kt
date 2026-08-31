package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.StorageInterface
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.fileserver.FileServlet
import com.simiacryptus.cognotik.fileserver.WebUiServlet
import com.simiacryptus.cognotik.platform.model.Session.Companion.validateSessionId
import com.simiacryptus.cognotik.platform.web.AbstractHttpServletResponse
import com.simiacryptus.cognotik.webui.session.ChatServer
import com.simiacryptus.cognotik.webui.servlet.*
import com.simiacryptus.cognotik.webui.session.SocketManager
import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder

abstract class ApplicationServer(
  final override val applicationName: String,
  val path: String,
  resourceBase: String = "application",
  open val root: File = dataStorageRoot,
  showMenubar: Boolean = true,
) : ChatServer(resourceBase, showMenubar) {
  init {
    FileServlet.userResolver = UserProviderImpl()
    FileServlet.isWriteAllowed = fun(user: User?, request: HttpServletRequest): Boolean {
      val sessionOwner = request.session()?.let { metadataDB.getSessionOwner(it) }
      return sessionOwner == null || sessionOwner == user?.id
    }
  }
  private val metadataDB by lazy { ApplicationServices.fileApplicationServices().metadataDB }


  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  open fun appInfo(session: Session, user: User) = appInfoMap.getOrPut(session) {
    AppInfoData(
      applicationName = applicationName,
      inputCnt = inputCnt,
      stickyInput = stickyInput,
      loadImages = false,
      showMenubar = showMenubar
    )
  }.toMap()

  final override val dataStorage: StorageInterface by lazy {
    ApplicationServices.fileApplicationServices().dataStorageFactory
  }
  protected open val appInfoServlet by lazy {
    ServletHolder("appInfo", AppInfoServlet { session, user ->
      appInfo(Session(session!!), user)
    })
  }
  protected open val userInfo by lazy { ServletHolder("userInfo", UserInfoServlet()) }
  protected open val usageServlet by lazy { ServletHolder("usage", UsageServlet()) }
  protected open val fileZip by lazy { ServletHolder("fileZip", ZipServlet(dataStorage)) }
  protected open val fileIndex by lazy {
    ServletHolder("fileIndex", object : SessionFileServlet(dataStorage) {
      val sessions = mutableSetOf<Session>()
      override fun onSession(session: Session, user: User?) {
        super.onSession(session, user)
        if (user != null && sessions.add(session)) {
          this@ApplicationServer.newSession(user = user, session = session)
        }
      }

      override fun getZipLink(req: HttpServletRequest, filePath: String): String {
        val session = req.pathInfo?.split("/")?.filter { it.isNotBlank() }?.firstOrNull() ?: return ""
        val zipPath = if (filePath.isNotBlank()) filePath else "/"
        return "${req.contextPath}/fileZip?session=${session}&path=${
          java.net.URLEncoder.encode(zipPath, "UTF-8")
        }"
      }
    }).apply {
      registration.setMultipartConfig(
        MultipartConfigElement(
          System.getProperty("java.io.tmpdir"),
          1024L * 1024L * 50L,  // maxFileSize: 50MB
          1024L * 1024L * 100L, // maxRequestSize: 100MB
          1024 * 1024 * 2       // fileSizeThreshold: 2MB
        )
      )
    }
  }
  protected open val sessionSettingsServlet by lazy { ServletHolder("settings", SessionSettingsServlet(this)) }
  protected open val sessionNameServlet by lazy { ServletHolder("sessionName", SessionNameServlet(this)) }
  protected open val sessionThreadsServlet by lazy { ServletHolder("threads", SessionThreadsServlet()) }
  protected open val deleteSessionServlet by lazy { ServletHolder("delete", DeleteSessionServlet(this)) }
  protected open val cancelSessionServlet by lazy { ServletHolder("cancel", CancelThreadsServlet()) }

  protected open val webUiServlet by lazy { ServletHolder("ui", WebUiServlet()) }

  override fun newSession(user: User, session: Session): SocketManager? {
    (SessionProxyServer.chats[session]?.takeIf { it != this }?.newSession(user, session)
      ?: SessionProxyServer.agents[session])?.apply { return this; }
    logger.info(
      "Creating new session: {} for user: {} in application: {}",
      session,
      user.email,
      applicationName
    )
    dataStorage.setJson(
      user, session, "info.json", mapOf(
        "session" to session.toString(),
        "application" to applicationName,
        "path" to path,
        "startTime" to System.currentTimeMillis(),
      )
    )
    logger.debug("Session info saved for session: {}", session)
    return object : ApplicationSocketManager(
      session = session,
      owner = user,
      dataStorage = dataStorage,
      applicationClass = this@ApplicationServer::class.java,
    ) {
      override fun userMessage(
        session: Session,
        user: User,
        userMessage: String,
        socketManager: ApplicationSocketManager
      ) = this@ApplicationServer.userMessage(
        session = session,
        user = user,
        userMessage = userMessage,
        ui = socketManager
      )
    }
  }

  open fun userMessage(
    session: Session,
    user: User,
    userMessage: String,
    ui: SocketManager
  ) {
    logger.warn(
      "userMessage not implemented for application: {} - session: {} user: {}",
      applicationName, session, user.email
    )
    throw UnsupportedOperationException("userMessage not implemented for $applicationName")
  }

  open val settingsClass: Class<*> get() = Map::class.java

  open fun <T : Any> initSettings(session: Session, user: User): T? = null

  open fun <T : Any> getSettings(
    session: Session,
    user: User,
    @Suppress("UNCHECKED_CAST") clazz: Class<T> = settingsClass as Class<T>
  ): T? {
    logger.debug(
      "Getting settings for session: {} user: {} class: {}",
      session,
      user.email,
      clazz.simpleName
    )
    val settingsFile = getSettingsFile(session, user)
    logger.debug("Settings file path: {}", settingsFile.absolutePath)
    return if (settingsFile.exists()) try {
      val text = settingsFile.readText()
      var settings: T? = if (settingsFile.exists()) JsonUtil.fromJson(text, clazz) else null
      logger.debug(
        "Settings file content (class {}):\nRAW:{}\nPARSED:{}",
        clazz,
        text.indent("    "),
        toJson(settings).indent("    ")
      )
      if (null == settings) {
        logger.debug("No existing settings found, initializing default settings")
        val initSettings = initSettings<T>(session, user)
        if (null != initSettings) {
          logger.debug("Writing initial settings to file")
          settingsFile.writeText(toJson(initSettings))
        }
        if (settingsFile.exists()) {
          settings = JsonUtil.fromJson(text, clazz)
          logger.debug("Loaded initial settings from file")
        }
      } else {
        logger.debug("Loaded existing settings from file")
      }
      settings
    } catch (e: Exception) {
      logger.error("Error reading settings file: ${settingsFile.absolutePath}", e)
      null
    } else {
      logger.debug("Settings file does not exist, returning null")
      null
    } ?: initSettings(session, user)
  }

  fun getSettingsFile(
    session: Session,
    userId: User
  ): File {
    logger.debug("Getting settings file for session: {} user: {}", session, userId.email)
    val settingsFile =
      dataStorage.getSystemDir(userId, session).resolve("settings.json")
        .apply { parentFile.mkdirs() }
    logger.debug("Settings file resolved to: {}", settingsFile.absolutePath)
    return settingsFile
  }

  override fun configure(webAppContext: ServletContextHandler) {
    logger.info("Configuring web application context for: {}", applicationName)
    super.configure(webAppContext)
    logger.debug("Adding servlets for application: {}", applicationName)
    webAppContext.addServlet(appInfoServlet, "/appInfo")
    logger.debug("Added appInfo servlet")
    webAppContext.addServlet(userInfo, "/userInfo")
    logger.debug("Added userInfo servlet")
    webAppContext.addServlet(usageServlet, "/usage")
    logger.debug("Added usage servlet")
    webAppContext.addServlet(fileIndex, "/fileIndex/*")
    logger.debug("Added fileIndex servlet")
    webAppContext.addServlet(fileZip, "/fileZip")
    logger.debug("Added fileZip servlet")
    webAppContext.addServlet(webUiServlet, "/ui/*")
    logger.debug("Added web ui (IDE view) servlet")
    webAppContext.addServlet(sessionSettingsServlet, "/settings")
    logger.debug("Added sessionSettings servlet")
    webAppContext.addServlet(sessionNameServlet, "/sessionName")
    logger.debug("Added sessionName servlet")
    webAppContext.addServlet(sessionThreadsServlet, "/threads")
    logger.debug("Added sessionThreads servlet")
    webAppContext.addServlet(deleteSessionServlet, "/delete")
    logger.debug("Added deleteSession servlet")
    webAppContext.addServlet(cancelSessionServlet, "/cancel")
    logger.debug("Added cancelSession servlet")
  }

  companion object {

    @Suppress("unused")
    @JvmStatic
    private val log: Logger = LoggerFactory.getLogger(ApplicationServer::class.java)

    val appInfoMap = mutableMapOf<Session, AppInfoData>()
    fun HttpServletRequest.session(): Session? {
      val sessionId = getParameter("sessionId") ?: getParameter("session") ?: pathInfo.let {
        it.split("/").firstOrNull { it.isNotBlank() && it.validateSessionId() }
      }
      return sessionId?.let { Session(it) }
    }
  }

}

private val log: Logger = LoggerFactory.getLogger(ApplicationServer::class.java)

fun authFilter(applicationClass: Class<ApplicationServer>): FilterHolder = FilterHolder { request, response, chain ->
  val requestPath = (request as HttpServletRequest).requestURI
  val servletPath = request.servletPath
  log.debug("Processing request: {}", requestPath)
  val user = UserProviderImpl().authenticate(request, response as HttpServletResponse)
  /*
   * /fileIndex issues its own (session-aware) redirects, and /ui is the static SPA shell:
   * redirecting its module/CSS requests to the login page would break the page load, while
   * every byte of data it shows still goes through the authenticated FS API.
   */
  val anonymousOk = servletPath == "/fileIndex" || servletPath == "/ui" || servletPath.startsWith("/ui/")
  val email = if (user == null && !anonymousOk) {
    log.warn("Authentication failed for request: {} ({})- redirecting to login", servletPath, requestPath)
    response.status = HttpServletResponse.SC_TEMPORARY_REDIRECT
    val originalRequest = request.requestURL.toString()
    val queryString = request.queryString
    val targetUrl = if (queryString != null) "$originalRequest?$queryString" else originalRequest
    val encodedTarget = URLEncoder.encode(targetUrl, "UTF-8")
    response.setHeader("Location", "/login/?target=$encodedTarget")
    return@FilterHolder
  } else {
    val email = user?.email ?: "anonymous"
    log.debug("Authenticated user: {} for request: {}", email, requestPath)
    email
  }
  val canRead = authorizationManager.isAuthorized(
    ResourceRef.of(applicationClass = applicationClass),
    Principal.of(user = user),
    operationType = OperationType.Read
  )
  log.debug(
    "Authorization check result: {} for user: {} on path: {}",
    canRead,
    email,
    requestPath
  )
  if (canRead) {
    log.debug("Access granted for request: {}", requestPath)
    chain?.doFilter(request, response)
  } else {
    log.warn(
      "Access denied for user: {} on path: {}",
      user?.email,
      requestPath
    )
    response.writer?.write("Access Denied")
    (response as HttpServletResponse?)?.status = HttpServletResponse.SC_FORBIDDEN
  }
}

fun HttpServletRequest.getCookie(name: String = AuthenticationInterface.AUTH_COOKIE) =
  cookies?.find { it.name == name }?.value.also { cookie ->
    log.debug(
      "Retrieved cookie '{}': {}",
      name,
      if (cookie != null) "[PRESENT]" else "[NOT_FOUND]"
    )
  }

class UserProviderImpl : com.simiacryptus.cognotik.platform.web.UserProvider {
  override fun authenticate(
    request: HttpServletRequest,
    response: AbstractHttpServletResponse?
  ): User? {
    val claimedUser = request.getCookie("USER")?.let { username ->
      val email = request.getCookie("EMAIL") ?: ""
      User(
        name = username,
        email = email,
      )
    }
    if (null != claimedUser) {
      val userSettings =
        ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(claimedUser)
      val token = request.getCookie() ?: ""
      val passwordHash = userSettings.passwordHash
      val internalToken = userSettings.internalToken
      val verified = try {
        (if (internalToken != null) {
          claimedUser.isMatch(LoginServlet.verifySessionToken(token, internalToken))
        } else null) ?: (if (passwordHash != null) {
          claimedUser.isMatch(LoginServlet.verifySessionToken(token, passwordHash))
        } else null) ?: apply {
          log.warn("No password hash found for user: {}, cannot verify session token", claimedUser.email)
          null
        }
      } catch (e: Exception) {
        log.warn("Session token verification failed for user: {} - {}", claimedUser.email, e.message)
        null
      }
      if (verified != null) {
        if (authenticationManager.listTokens(claimedUser).firstOrNull()?.label.isNullOrBlank()) {
          authenticationManager.putUser(token, claimedUser)
          log.warn("Session token stored for user: {}", claimedUser.email)
        } else {
          log.warn("Session token valid for user: {}", claimedUser.email)
        }
        return claimedUser
      } else {
        log.warn("No valid session token found for user: {}", claimedUser.email)
      }
    }
    try {
      val user = authenticationManager.getUser(request.getCookie())
      return user
    } catch (e: RuntimeException) {
      log.debug(e.message)
      if (null != response) {
        response.status = HttpServletResponse.SC_TEMPORARY_REDIRECT
        val originalRequest = request.requestURL.toString()
        val queryString = request.queryString
        val targetUrl = if (queryString != null) "$originalRequest?$queryString" else originalRequest
        val encodedTarget = URLEncoder.encode(targetUrl, "UTF-8")
        response.setHeader("Location", "/login/?target=$encodedTarget")
      }
      return null
    }
  }

  private fun User.isMatch(
    result: LoginServlet.Companion.SessionVerificationResult
  ): LoginServlet.Companion.SessionEnvelope? = when (result) {
    is LoginServlet.Companion.SessionVerificationResult.Success -> result.envelope
    is LoginServlet.Companion.SessionVerificationResult.Failure -> {
      log.warn(
        "Session token verification failed for user: {} - {} ({})",
        email, result.error, result.reason
      )
      null
    }
  }
}


fun HttpURLConnection.setCookies(cookies: Map<String, String?>) {
  setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
}

fun HttpURLConnection.getCookies(): Map<String, String?> = getRequestProperty("Cookie")?.let { cookieHeader ->
  return cookieHeader.split(";").mapNotNull { cookie ->
    val parts = cookie.trim().split("=", limit = 2)
    if (parts.size == 2) {
      val name = parts[0].trim()
      val value = parts[1].trim()
      name to value
    } else {
      null
    }
  }.toMap()
} ?: emptyMap()

fun HttpURLConnection.appendCookies(cookies: Map<String, String?>) {
  val prevCookies = getCookies()
  val newCookies = prevCookies + cookies
  setCookies(newCookies)
}

fun User.getAuthCookies(): Map<String, String?> = mapOf(
  AuthenticationInterface.AUTH_COOKIE to authenticationManager.listTokens(this).firstOrNull()?.label,
  "USER" to name,
  "EMAIL" to email
)