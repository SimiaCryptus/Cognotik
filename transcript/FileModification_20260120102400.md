# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/AppInfoData.kt

```
package com.simiacryptus.cognotik.webui.application

data class AppInfoData(
    val applicationName: String,
    val inputCnt: Int,
    val stickyInput: Boolean,
    val loadImages: Boolean = true,
    val showMenubar: Boolean
) {

    fun toMap(): Map<String, Any> {
        return this::class.java.declaredFields.associate { it.name to it.get(this) }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/ApplicationDirectory.kt

```
package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.OutputInterceptor
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.servlet.*
import jakarta.servlet.DispatcherType
import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.Servlet
import jakarta.servlet.http.HttpServlet
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlet.StatisticsServlet
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.util.resource.Resource.newResource
import org.eclipse.jetty.util.resource.ResourceCollection
import org.eclipse.jetty.webapp.WebAppClassLoader
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import java.awt.Desktop
import java.net.URI
import java.util.*
import kotlin.system.exitProcess

abstract class ApplicationDirectory(
    val localName: String = "localhost",
    val publicName: String = "localhost",
    val port: Int = 8081,
) {
    init {
        log.info("Creating ApplicationDirectory instance with localName='$localName', publicName='$publicName', port=$port")
    }

    var domainName: String = ""
        private set
    abstract val childWebApps: List<ChildWebApp>

    data class ChildWebApp(
        val path: String,
        val server: ChatServer,
        val thumbnail: String? = null,
    )

    private fun domainName(isServer: Boolean) =
        if (isServer) "https://$publicName" else "http://$localName:$port".also {
            log.debug("Generated domain name: $it (isServer: $isServer)")
        }

    open val welcomeResources: Resource = ResourceCollection(*allResources("welcome").map(::newResource).toTypedArray())
        .also { log.debug("Initialized welcome resources with ${allResources("welcome").size} resource(s)") }
    open val userInfoServlet: HttpServlet = UserInfoServlet()
        .also { log.debug("Initialized UserInfoServlet") }
    open val userSettingsServlet: HttpServlet = UserSettingsServlet()
        .also { log.debug("Initialized UserSettingsServlet") }

    open val apiProviderServlet: HttpServlet = ApiProviderServlet()
        .also { log.debug("Initialized ApiProviderServlet") }
    open val logoutServlet: HttpServlet = LogoutServlet()
        .also { log.debug("Initialized LogoutServlet") }
    open val usageServlet: HttpServlet = UsageServlet()
        .also { log.debug("Initialized UsageServlet") }
    open val proxyHttpServlet: HttpServlet = ProxyHttpServlet()
        .also { log.debug("Initialized ProxyHttpServlet") }
    open val welcomeServlet: HttpServlet = WelcomeServlet(this)
        .also { log.debug("Initialized WelcomeServlet") }
    open val apiKeyServlet: HttpServlet = ApiKeyServlet()
        .also { log.debug("Initialized ApiKeyServlet") }
    open val taskConfigServlet: HttpServlet = TaskConfigServlet()
        .also { log.debug("Initialized TaskConfigServlet") }

    open val cognitiveConfigServlet: HttpServlet = CognitiveConfigServlet()
        .also { log.debug("Initialized CognitiveConfigServlet") }

    open fun authenticatedWebsite(): OAuthBase? = OAuthGoogle(
        redirectUri = "$domainName/oauth2callback",
        applicationName = "Demo",
        key = {
            log.debug("Loading OAuth configuration from encrypted resource")
            val encryptedData =
                javaClass.classLoader!!.getResourceAsStream("client_secret_google_oauth.json.kms")?.readBytes()
                    ?: throw RuntimeException("Unable to load resource: ${"client_secret_google_oauth.json.kms"}").also {
                        log.error("Failed to load OAuth configuration resource")
                    }
            log.debug("Successfully loaded encrypted OAuth data (${encryptedData.size} bytes)")
            val decrypt = ApplicationServices.cloud?.decrypt(encryptedData)
            log.debug("OAuth configuration decrypted successfully")
            decrypt?.byteInputStream()
        }
    ).also { log.info("OAuth authentication configured with Google provider") }

    open fun setupPlatform() {
        log.info("Setting up platform (default implementation - no action taken)")
    }

    protected open fun _main(vararg args: String) {
        try {
            log.info("Starting application with args: ${args.joinToString(", ")}")
            init(args.contains("--server"))
            setupPlatform()
            isLocked = true
            val server = start(port, "127.0.0.1", *(webAppContexts()))
            log.info("Server started successfully on port $port")
            browse()
            server.join()
        } catch (e: Throwable) {
            e.printStackTrace()
            log.error("Application encountered an error: ${e.message}", e)
            Thread.sleep(1000)
            exitProcess(1)
        } finally {
            Thread.sleep(1000)
            exitProcess(0)
        }
    }

    protected open fun browse() {
        try {
            log.info("Attempting to open browser to: $domainName/")
            Desktop.getDesktop().browse(URI("$domainName/"))
            log.info("Browser opened successfully")
        } catch (e: Throwable) {
            log.warn("Failed to open browser automatically: ${e.message}")

        }
    }

    open fun webAppContexts() = listOfNotNull(
        run { log.debug("Creating web app contexts"); null },
        newWebAppContext("/logout", logoutServlet),
        newWebAppContext("/proxy", proxyHttpServlet),
        newWebAppContext("/userInfo", userInfoServlet).let {
            log.debug("Configuring userInfo context with authentication")
            authenticatedWebsite()?.configure(it, true) ?: it
        },
        newWebAppContext("/userSettings", userSettingsServlet).let {
            log.debug("Configuring userSettings context with authentication")
            authenticatedWebsite()?.configure(it, true) ?: it
        },
        newWebAppContext("/apiProviders", apiProviderServlet).let {
            log.debug("Configuring apiProviders context with authentication")
            authenticatedWebsite()?.configure(it, false) ?: it
        },
        newWebAppContext("/usage", usageServlet).let {
            log.debug("Configuring usage context with authentication")
            authenticatedWebsite()?.configure(it, true) ?: it
        },
        newWebAppContext("/apiKeys", apiKeyServlet).let {
            log.debug("Configuring apiKeys context with authentication")
            authenticatedWebsite()?.configure(it, true) ?: it
        },
        newWebAppContext("/taskConfig", taskConfigServlet).let {
            log.debug("Configuring taskConfig context with authentication")
            authenticatedWebsite()?.configure(it, true) ?: it
        },
        newWebAppContext("/cognitiveConfig", cognitiveConfigServlet).let {
            log.debug("Configuring cognitiveConfig context with authentication")
            authenticatedWebsite()?.configure(it, true) ?: it
        },
        newWebAppContext("/", welcomeResources, "welcome", welcomeServlet).let {
            log.debug("Configuring root context with welcome resources")
            authenticatedWebsite()?.configure(it, false) ?: it
        },
        newWebAppContext("/api", welcomeServlet).let {
            log.debug("Configuring API context")
            authenticatedWebsite()?.configure(it, false) ?: it
        },
    ).toTypedArray() + childWebApps.map {
        log.debug("Adding child web app context for path: ${it.path}")
        newWebAppContext(it.path, it.server)
    }.also { contexts ->
        log.info("Created ${contexts.size} web app contexts total")
    }

    open fun init(isServer: Boolean): ApplicationDirectory {
        OutputInterceptor.setupInterceptor()
        log.info("Initializing application, isServer: $isServer")
        domainName = domainName(isServer)
        return this
    }

    protected open fun start(
        port: Int,
        host: String,
        vararg webAppContexts: WebAppContext
    ): Server {
        log.info("Starting Jetty server on $host:$port with ${webAppContexts.size} web app contexts")
        val contexts = ContextHandlerCollection()

        log.info("Starting server on port: $port")
        contexts.handlers = (
                listOf(
                    run { log.debug("Adding statistics servlet context"); null },
                    newWebAppContext("/stats", StatisticsServlet())
                ) +
                        webAppContexts.map {
                            log.debug("Adding CORS filter to context: ${it.contextPath}")
                            it.addFilter(FilterHolder(CorsFilter()), "/*", EnumSet.of(DispatcherType.REQUEST))
                            it
                        }
                ).filterNotNull().toTypedArray()

        log.debug("Created context handler collection with ${contexts.handlers.size} handlers")
        val server = Server(port)

        val serverConnector = ServerConnector(server, 4, 8, httpConnectionFactory())
        serverConnector.port = port
        serverConnector.host = host
        serverConnector.acceptQueueSize = 1000
        serverConnector.idleTimeout = 30000
        log.debug("Server connector configured: host=$host, port=$port, acceptQueueSize=1000, idleTimeout=30000ms")

        server.connectors = arrayOf(serverConnector)
        server.handler = contexts
        log.info("Starting Jetty server...")
        server.start()
        if (!server.isStarted) throw IllegalStateException("Server failed to start")
        log.info("Jetty server started successfully and is ready to accept connections")
        log.info("Server initialization completed successfully.")
        return server
    }

    protected open fun httpConnectionFactory(): HttpConnectionFactory {
        log.debug("Creating HTTP connection factory with forwarded request customizer")
        val httpConfig = HttpConfiguration()
        httpConfig.addCustomizer(ForwardedRequestCustomizer())
        log.debug("HTTP connection factory created with custom configuration.")
        return HttpConnectionFactory(httpConfig)
    }

    protected open fun newWebAppContext(path: String, server: ChatServer): WebAppContext {
        log.debug("Creating WebAppContext for ChatServer at path: $path")
        var baseResource: Resource? = server.baseResource
        if (baseResource == null) {
            log.warn("No baseResource specified for ChatServer at path: $path, defaulting to root resource")
            baseResource = Resource.newClassPathResource("/")
        }
        if (baseResource == null) {
            log.error("Failed to determine baseResource for ChatServer at path: $path, using empty resource collection")
            // Create an empty resource collection as fallback for Android
            baseResource = ResourceCollection()
        }
        log.debug("Base resource determined for path $path: ${baseResource?.javaClass?.simpleName}")
        val webAppContext = newWebAppContext(path, baseResource, resourceBase = "application")
        log.debug("Configuring ChatServer for WebAppContext at path: $path")
        server.configure(webAppContext)
        log.info("WebAppContext configured for path: $path with ChatServer")
        return webAppContext
    }

    protected open fun newWebAppContext(
        path: String,
        baseResource: Resource?,
        resourceBase: String,
        indexServlet: Servlet? = null
    ): WebAppContext {
        log.debug("Creating WebAppContext: path=$path, resourceBase=$resourceBase, hasIndexServlet=${indexServlet != null}")
        val context = WebAppContext()
        log.debug("Configuring WebSocket support for context: $path")
        JettyWebSocketServletContainerInitializer.configure(context, null)
        // Use standard class loader on Android to avoid WebAppClassLoader compatibility issues
        if (!isAndroid()) {
            log.debug("Using WebAppClassLoader for context: $path")
            context.classLoader = WebAppClassLoader(ApplicationServices::class.java.classLoader, context)
            context.isParentLoaderPriority = true
        } else {
            log.debug("Using standard class loader for Android compatibility in context: $path")
            context.classLoader = ApplicationServices::class.java.classLoader
        }
        if (baseResource != null) {
            log.debug("Setting base resource for context $path: ${baseResource.javaClass.simpleName}")
            context.baseResource = baseResource
        } else {
            log.warn("No base resource provided for context at path: $path")
        }
        log.debug("New WebAppContext created for path: $path")
        context.contextPath = path
        context.welcomeFiles = arrayOf("index.html")
        if (indexServlet != null) {
            log.debug("Adding index servlet to context: $path")
            context.addServlet(ServletHolder("$path/index", indexServlet), "/")
            context.addServlet(ServletHolder("$path/index", indexServlet), "/index.html")
        }
        log.debug("WebAppContext configuration completed for path: $path")
        return context
    }

    protected open fun newWebAppContext(path: String, servlet: Servlet): WebAppContext {
        log.debug("Creating WebAppContext for servlet at path: $path (servlet type: ${servlet.javaClass.simpleName})")
        val context = WebAppContext()
        log.debug("Configuring WebSocket support for servlet context: $path")
        JettyWebSocketServletContainerInitializer.configure(context, null)
        // Use standard class loader on Android to avoid WebAppClassLoader compatibility issues
        if (!isAndroid()) {
            log.debug("Using WebAppClassLoader for servlet context: $path")
            context.classLoader = WebAppClassLoader(ApplicationServices::class.java.classLoader, context)
            context.isParentLoaderPriority = true
        } else {
            log.debug("Using standard class loader for Android compatibility in servlet context: $path")
            context.classLoader = ApplicationServices::class.java.classLoader
        }
        context.contextPath = path
        log.debug("New WebAppContext created for servlet at path: $path")
        context.resourceBase = "application"
        context.welcomeFiles = arrayOf("index.html")
        val servletHolder = ServletHolder(servlet)
        log.debug("Configuring multipart support for servlet at path: $path")
        servletHolder.getRegistration().setMultipartConfig(MultipartConfigElement("./tmp"))
        context.addServlet(servletHolder, "/")
        log.debug("Servlet WebAppContext configuration completed for path: $path")
        return context
    }

    private fun isAndroid(): Boolean {
        val result = try {
            Class.forName("android.os.Build")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        log.debug("Android platform detection result: $result")
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApplicationDirectory::class.java)
        fun allResources(resourceName: String): List<java.net.URL> {
            log.debug("Loading all resources for name: $resourceName")
            val resources = Thread.currentThread().contextClassLoader.getResources(resourceName).toList()
            log.debug("Found ${resources.size} resource(s) for name: $resourceName")
            return resources
        }
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/ApplicationServer.kt

```
package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.servlet.*
import com.simiacryptus.cognotik.webui.session.SocketManager
import jakarta.servlet.MultipartConfigElement
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
    protected open val fileIndex by lazy {
        ServletHolder("fileIndex", SessionFileServlet(dataStorage)).apply {
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
    protected open val sessionThreadsServlet by lazy { ServletHolder("threads", SessionThreadsServlet()) }
    protected open val deleteSessionServlet by lazy { ServletHolder("delete", DeleteSessionServlet(this)) }
    protected open val cancelSessionServlet by lazy { ServletHolder("cancel", CancelThreadsServlet()) }

    override fun newSession(user: User, session: Session): SocketManager {
        (SessionProxyServer.chats[session]?.takeIf { it != this }?.newSession(user, session)
            ?: SessionProxyServer.agents[session])?.apply { return this; }
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
        logger.info("New session created successfully: {}", session)
    }

    open fun userMessage(
        session: Session,
        user: User = defaultUser,
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

    open fun <T : Any> getSettings(
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
        val settingsFile = getSettingsFile(session, userId ?: defaultUser)
        logger.debug("Settings file path: {}", settingsFile.absolutePath)
        if (settingsFile.exists()) try {
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
        userId: User = UserSettingsManager.defaultUser
    ): File {
        logger.debug("Getting settings file for session: {} user: {}", session, userId.email)
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
        webAppContext.addServlet(deleteSessionServlet, "/delete")
        logger.debug("Added deleteSession servlet")
        webAppContext.addServlet(cancelSessionServlet, "/cancel")
        logger.debug("Added cancelSession servlet")
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/ApplicationSocketManager.kt

```
package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.chat.ChatSocket
import com.simiacryptus.cognotik.webui.session.SocketManager

abstract class ApplicationSocketManager(
    session: Session,
    owner: User = defaultUser,
    dataStorage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
    applicationClass: Class<*>,
) : SocketManager(
    sessionId = session,
    dataStorage = dataStorage,
    owner = owner,
    applicationClass = applicationClass,
) {
    override fun onRun(userMessage: String, socket: ChatSocket) {
        userMessage(
            session = sessionId,
            user = socket.user,
            userMessage = userMessage,
            socketManager = this
        )
    }

    abstract fun userMessage(
        session: Session,
        user: User = defaultUser,
        userMessage: String,
        socketManager: ApplicationSocketManager
    )

    companion object {


    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/CognotikAppServer.kt

```
package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.servlet.CorsFilter
import jakarta.servlet.DispatcherType
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*

class CognotikAppServer(
    val localName: String,
    val port: Int = 8080,
    val endpoint : String = "127.0.0.1",
) {
    val server by lazy {
        try {
            log.info("Initializing server on $localName:$port")
            val server = Server(InetSocketAddress(localName, port))
            server.handler = ContextHandlerCollection().apply {
                this.handlers = arrayOf(
                    newWebAppContext(SessionProxyServer(), "/")
                ).map {
                    try {
                        it.addFilter(FilterHolder(CorsFilter()), "/*", EnumSet.of(DispatcherType.REQUEST))
                        log.debug("Added CORS filter to context: ${it.contextPath}")
                        it
                    } catch (e: Exception) {
                        log.error("Failed to add CORS filter to context", e)
                        throw e
                    }
                }.toMutableList().toTypedArray<WebAppContext>()
            }
            server
        } catch (e: Exception) {
            log.error("Failed to initialize server on $localName:$port", e)
            throw e
        }
    }

    var context: WebAppContext? = null
        private set

    private fun newWebAppContext(server: ChatServer, path: String): WebAppContext {
        return try {
            log.debug("Creating new WebAppContext for path: $path")
            require(this.context == null) { "WebAppContext has already been initialized" }
            val context = WebAppContext()
            this.context = context
            JettyWebSocketServletContainerInitializer.configure(context, null)
            context.baseResource = server.baseResource
            context.classLoader = CognotikAppServer::class.java.classLoader
            context.contextPath = path
            context.welcomeFiles = arrayOf("index.html")
            server.configure(context)
            log.info("Successfully created WebAppContext for path: $path")
            context
        } catch (e: Exception) {
            log.error("Failed to create WebAppContext for path: $path", e)
            throw e
        }
    }

    fun start(): Server {
        try {
            log.info("Starting CognotikAppServer on $localName:$port")
            server.start()
            if (server.isStarted) {
                log.info("CognotikAppServer successfully started on $localName:$port")
            } else {
                log.warn("Server start() completed but server is not in started state")
            }
            return server
        } catch (e: Exception) {
            log.error("Failed to start CognotikAppServer", e)
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CognotikAppServer::class.java)


        @Transient
        private var server: CognotikAppServer? = null

        fun isRunning(): Boolean {
            val running = server?.server?.isRunning ?: false
            log.debug("Server running status: $running")
            return running
        }

        fun getServer(
            endpoint: String = "localhost",
            port: Int = 8181
        ): CognotikAppServer {
            try {
                if (null == server || !server!!.server.isRunning) {
                    if (endpoint.isBlank()) throw IllegalArgumentException("Endpoint cannot be blank when starting a new server")
                    val endpoint = endpoint
                    val port = port

                    if (endpoint.isBlank()) {
                        log.error("Listening endpoint is blank")
                        throw IllegalStateException("Listening endpoint cannot be blank")
                    }

                    if (port <= 0 || port > 65535) {
                        log.error("Invalid port number: $port")
                        throw IllegalArgumentException("Port must be between 1 and 65535, got: $port")
                    }

                    log.info("Creating new CognotikAppServer instance for endpoint: $endpoint:$port")
                    server = CognotikAppServer(endpoint, port)
                    server!!.start()
                } else {
                    log.debug("Returning existing running server instance")
                }
                return server!!
            } catch (e: Exception) {
                log.error("Failed to get or create server instance", e)
                throw e
            }
        }
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/SymbolGraphApp.kt

```
package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.webui.servlet.SymbolGraphServlet
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import java.io.File


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new `README.md` file for the `com.simiacryptus.cognotik.webui.application` package, based on the provided source code and specifications.

### webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/README.md
```markdown
# Web UI Application Framework

This package provides the core infrastructure for hosting and managing AI-powered web applications within the Cognotik platform. It leverages the Jetty web server to provide a robust, session-aware environment for chat-based and interactive AI tools.

## Key Components

### Server Infrastructure

*   **`ApplicationDirectory`**: An abstract base class designed to manage multiple child web applications. It handles:
    *   Jetty server initialization and lifecycle.
    *   OAuth2 authentication configuration (specifically Google OAuth).
    *   Registration of common servlets (User Info, Settings, Usage, API Keys, etc.).
    *   Automatic browser launching for local development.
    *   Android compatibility checks for class loading.
*   **`CognotikAppServer`**: A concrete server implementation used to run the Cognotik application environment. It includes built-in support for CORS filters and session proxying.
*   **`ApplicationServer`**: The primary base class for individual AI applications. It extends `ChatServer` and provides:
    *   Session-specific data storage and settings management.
    *   Security filters for authorization (Read/Write/etc.).
    *   Standardized API endpoints for application metadata (`/appInfo`), file management (`/fileIndex`, `/fileZip`), and session control.

### Session & Communication

*   **`ApplicationSocketManager`**: An abstract handler for WebSocket-based communication. It bridges the gap between the real-time web interface and the application's backend logic, routing user messages to the appropriate processing methods.
*   **`AppInfoData`**: A data transfer object (DTO) that carries application configuration to the frontend, including UI preferences like menu bar visibility and image loading settings.

### Specialized Applications

*   **`SymbolGraphApp`**: (In development) Integrates with `SymbolGraphService` to provide specialized graph-based analysis or visualization.

## Features

*   **Multi-Tenancy & Sessions**: Built-in support for user-isolated sessions with persistent storage for settings and chat history.
*   **Security**: Integrated OAuth2 flow and granular operation-based authorization checks.
*   **Extensibility**: Developers can create new AI applications by extending `ApplicationServer` and implementing the `userMessage` logic.
*   **Resource Management**: Efficient handling of static resources, multipart file uploads, and dynamic servlet registration.

## Usage Pattern

To create a new application in this framework:

1.  Extend `ApplicationServer` to define your application's metadata and session logic.
2.  Implement `userMessage` to handle incoming AI prompts.
3.  Register the application within an `ApplicationDirectory` instance to expose it via a specific URL path.
4.  Use `ApplicationSocketManager` to customize real-time interaction behaviors if necessary.
```

### Summary of Changes
- Created a new `README.md` in the `webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/` directory.
- Documented the primary classes (`ApplicationDirectory`, `ApplicationServer`, `CognotikAppServer`, etc.) and their roles in the system.
- Outlined the core features such as session management, security, and the general usage pattern for developers.
- Ensured the documentation reflects the Jetty-based architecture and the integration with the broader Cognotik platform services.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/README.md</a> Updated
