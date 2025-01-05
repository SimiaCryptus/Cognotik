package com.simiacryptus.skyenet.webui.application

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.platform.ApplicationServices.authenticationManager
import com.simiacryptus.skyenet.core.platform.ApplicationServices.authorizationManager
import com.simiacryptus.skyenet.core.platform.ApplicationServices.dataStorageFactory
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.skyenet.core.platform.model.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.chat.ChatServer
import com.simiacryptus.skyenet.webui.servlet.*
import com.simiacryptus.skyenet.webui.session.SocketManager
import com.simiacryptus.util.JsonUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import java.io.File

abstract class ApplicationServer(
  final override val applicationName: String,
  val path: String,
  resourceBase: String = "application",
  open val root: File = dataStorageRoot,
  val showMenubar: Boolean = true,
) : ChatServer(resourceBase) {

  open val description: String = ""
  open val singleInput = true
  open val stickyInput = false
  open fun appInfo(session: Session) = appInfoMap.getOrPut(session) {
    AppInfoData(
      applicationName = applicationName,
      singleInput = singleInput,
      stickyInput = stickyInput,
      loadImages = false,
      showMenubar = showMenubar
    )
  }.toMap()

  final override val dataStorage: StorageInterface by lazy { dataStorageFactory(dataStorageRoot) }

  protected open val appInfoServlet by lazy {
    ServletHolder("appInfo", AppInfoServlet { session ->
      appInfo(Session(session!!))
    })
  }
  protected open val userInfo by lazy { ServletHolder("userInfo", UserInfoServlet()) }
  protected open val usageServlet by lazy { ServletHolder("usage", UsageServlet()) }
  protected open val fileZip by lazy { ServletHolder("fileZip", ZipServlet(dataStorage)) }
  protected open val fileIndex by lazy { ServletHolder("fileIndex", SessionFileServlet(dataStorage)) }
  protected open val sessionSettingsServlet by lazy { ServletHolder("settings", SessionSettingsServlet(this)) }
  protected open val sessionShareServlet by lazy { ServletHolder("share", SessionShareServlet(this)) }
  protected open val sessionThreadsServlet by lazy { ServletHolder("threads", SessionThreadsServlet()) }
  protected open val deleteSessionServlet by lazy { ServletHolder("delete", DeleteSessionServlet(this)) }
  protected open val cancelSessionServlet by lazy { ServletHolder("cancel", CancelThreadsServlet()) }

  override fun newSession(user: User?, session: Session): SocketManager {
    dataStorage.setJson(
      user, session, "info.json", mapOf(
        "session" to session.toString(),
        "application" to applicationName,
        "path" to path,
        "startTime" to System.currentTimeMillis(),
      )
    )
    return object : ApplicationSocketManager(
      session = session,
      owner = user,
      dataStorage = dataStorage,
      applicationClass = this@ApplicationServer::class.java,
    ) {
      override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        socketManager: ApplicationSocketManager,
        api: API
      ) = this@ApplicationServer.userMessage(
        session = session,
        user = user,
        userMessage = userMessage,
        ui = socketManager.applicationInterface,
        api = api
      )
    }
  }

  open fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ): Unit = throw UnsupportedOperationException()

  open val settingsClass: Class<*> get() = Map::class.java

  open fun <T : Any> initSettings(session: Session): T? = null

  fun <T : Any> getSettings(
    session: Session,
    userId: User?,
    @Suppress("UNCHECKED_CAST") clazz: Class<T> = settingsClass as Class<T>
  ): T? {
    val settingsFile = getSettingsFile(session, userId)
    var settings: T? = if (settingsFile.exists()) JsonUtil.fromJson(settingsFile.readText(), clazz) else null
    if (null == settings) {
      val initSettings = initSettings<T>(session)
      if (null != initSettings) {
        settingsFile.writeText(JsonUtil.toJson(initSettings))
      }
      if (settingsFile.exists()) {
        settings = JsonUtil.fromJson(settingsFile.readText(), clazz)
      }
    }
    return settings
  }

  fun getSettingsFile(
    session: Session,
    userId: User?
  ): File {
    val settingsFile =
      dataStorage.getDataDir(userId, session).resolve("settings.json")
        .apply { parentFile.mkdirs() }
    return settingsFile
  }

  protected open fun sessionsServlet(path: String) =
    ServletHolder("sessionList", SessionListServlet(this.dataStorage, path, this))

  override fun configure(webAppContext: WebAppContext) {
    super.configure(webAppContext)

    webAppContext.addFilter(
      FilterHolder { request, response, chain ->
        val user = authenticationManager.getUser((request as HttpServletRequest).getCookie())
        val canRead = authorizationManager.isAuthorized(
          applicationClass = this@ApplicationServer.javaClass,
          user = user,
          operationType = OperationType.Read
        )
        if (canRead) {
          chain?.doFilter(request, response)
        } else {
          response?.writer?.write("Access Denied")
          (response as HttpServletResponse?)?.status = HttpServletResponse.SC_FORBIDDEN
        }
      }, "/*", null
    )

    webAppContext.addServlet(appInfoServlet, "/appInfo")
    webAppContext.addServlet(userInfo, "/userInfo")
    webAppContext.addServlet(usageServlet, "/usage")
    webAppContext.addServlet(fileIndex, "/fileIndex/*")
    webAppContext.addServlet(fileZip, "/fileZip")
    webAppContext.addServlet(sessionsServlet(path), "/sessions")
    webAppContext.addServlet(sessionSettingsServlet, "/settings")
    webAppContext.addServlet(sessionThreadsServlet, "/threads")
    webAppContext.addServlet(sessionShareServlet, "/share")
    webAppContext.addServlet(deleteSessionServlet, "/delete")
    webAppContext.addServlet(cancelSessionServlet, "/cancel")
  }

  companion object {

    fun HttpServletRequest.getCookie(name: String = AuthenticationInterface.AUTH_COOKIE) =
      cookies?.find { it.name == name }?.value


    val appInfoMap = mutableMapOf<Session, AppInfoData>()
  }

}