package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.actors.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.servlet.*
import com.simiacryptus.cognotik.webui.session.SocketManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.Logger
import java.io.File

abstract class ApplicationServer(
    final override val applicationName: String,
    val path: String,
    resourceBase: String = "application",
    open val root: File = dataStorageRoot,
    showMenubar: Boolean = true,
) : ChatServer(resourceBase, showMenubar) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    open fun appInfo(session: Session) = appInfoMap.getOrPut(session) {
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
        logger.info(
            "Creating new session: {} for user: {} in application: {}",
            session,
            user?.email ?: "anonymous",
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
                user: User?,
                userMessage: String,
                socketManager: ApplicationSocketManager
            ) = this@ApplicationServer.userMessage(
                session = session,
                user = user,
                userMessage = userMessage,
                ui = socketManager
            )
        }
        logger.info("New session created successfully: {}", session)
    }

    open fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: SocketManager
    ) {
        logger.warn(
            "userMessage not implemented for application: {} - session: {} user: {}",
            applicationName, session, user?.email ?: "anonymous"
        )
        throw UnsupportedOperationException("userMessage not implemented for $applicationName")
    }

    open val settingsClass: Class<*> get() = Map::class.java

    open fun <T : Any> initSettings(session: Session): T? = null

    fun <T : Any> getSettings(
        session: Session,
        userId: User?,
        @Suppress("UNCHECKED_CAST") clazz: Class<T> = settingsClass as Class<T>
    ): T? {
        logger.debug(
            "Getting settings for session: {} user: {} class: {}",
            session,
            userId?.email ?: "anonymous",
            clazz.simpleName
        )
        val settingsFile = getSettingsFile(session, userId)
        logger.debug("Settings file path: {}", settingsFile.absolutePath)
        if(settingsFile.exists()) try {
            val text = settingsFile.readText()
            var settings: T? = if (settingsFile.exists()) JsonUtil.fromJson(text, clazz) else null
            logger.debug("Settings file content (class {}):\nRAW:{}\nPARSED:{}", clazz, text.indent("    "), toJson(settings).indent("    "))
            if (null == settings) {
                logger.debug("No existing settings found, initializing default settings")
                val initSettings = initSettings<T>(session)
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
            return settings
        } catch (e: Exception) {
            logger.error("Error reading settings file: ${settingsFile.absolutePath}", e)
            return null
        } else {
            logger.debug("Settings file does not exist, returning null")
            return null
        }
    }

    fun getSettingsFile(
        session: Session,
        userId: User?
    ): File {
        logger.debug("Getting settings file for session: {} user: {}", session, userId?.email ?: "anonymous")
        val settingsFile =
            dataStorage.getDataDir(userId, session).resolve("settings.json")
                .apply { parentFile.mkdirs() }
        logger.debug("Settings file resolved to: {}", settingsFile.absolutePath)
        return settingsFile
    }

    protected open fun sessionsServlet(path: String) =
        ServletHolder("sessionList", SessionListServlet(this.dataStorage, path, this))

    override fun configure(webAppContext: WebAppContext) {
        logger.info("Configuring web application context for: {}", applicationName)
        super.configure(webAppContext)

        webAppContext.addFilter(
            FilterHolder { request, response, chain ->
                val requestPath = (request as HttpServletRequest).requestURI
                logger.debug("Processing request: {} for application: {}", requestPath, applicationName)
                val user = authenticationManager.getUser((request as HttpServletRequest).getCookie())
                logger.debug("Authenticated user: {} for request: {}", user?.email ?: "anonymous", requestPath)
                val canRead = authorizationManager.isAuthorized(
                    applicationClass = this@ApplicationServer.javaClass,
                    user = user,
                    operationType = OperationType.Read
                )
                logger.debug(
                    "Authorization check result: {} for user: {} on path: {}",
                    canRead,
                    user?.email ?: "anonymous",
                    requestPath
                )
                if (canRead) {
                    logger.debug("Access granted for request: {}", requestPath)
                    chain?.doFilter(request, response)
                } else {
                    logger.warn(
                        "Access denied for user: {} on path: {} in application: {}",
                        user?.email ?: "anonymous",
                        requestPath,
                        applicationName
                    )
                    response?.writer?.write("Access Denied")
                    (response as HttpServletResponse?)?.status = HttpServletResponse.SC_FORBIDDEN
                }
            }, "/*", null
        )
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
        webAppContext.addServlet(sessionsServlet(path), "/sessions")
        logger.debug("Added sessions servlet")
        webAppContext.addServlet(sessionSettingsServlet, "/settings")
        logger.debug("Added sessionSettings servlet")
        webAppContext.addServlet(sessionThreadsServlet, "/threads")
        logger.debug("Added sessionThreads servlet")
        webAppContext.addServlet(sessionShareServlet, "/share")
        logger.debug("Added sessionShare servlet")
        webAppContext.addServlet(deleteSessionServlet, "/delete")
        logger.debug("Added deleteSession servlet")
        webAppContext.addServlet(cancelSessionServlet, "/cancel")
        logger.debug("Added cancelSession servlet")
        logger.info("Web application context configuration completed for: {}", applicationName)
    }

    companion object {

        @JvmStatic
        val log: Logger = LoggerFactory.getLogger(ApplicationServer::class.java)

        fun HttpServletRequest.getCookie(name: String = AuthenticationInterface.AUTH_COOKIE) =
            cookies?.find { it.name == name }?.value.also { cookie ->
                log.debug("Retrieved cookie '{}': {}", name, if (cookie != null) "[PRESENT]" else "[NOT_FOUND]")
            }

        val appInfoMap = mutableMapOf<Session, AppInfoData>()
    }

}