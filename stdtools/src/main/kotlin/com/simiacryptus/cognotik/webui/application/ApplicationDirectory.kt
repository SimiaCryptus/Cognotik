package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.OutputInterceptor
import com.simiacryptus.cognotik.auth.AuthCallbackServlet
import com.simiacryptus.cognotik.webui.servlet.GiftedCreditsServlet
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.servlet.*
import com.simiacryptus.cognotik.webui.servlet.action.DocOpsFsActions
import com.simiacryptus.cognotik.webui.servlet.action.DocOpsServlets
import com.simiacryptus.cognotik.webui.servlet.action.ModifyFilesFsAction
import com.simiacryptus.cognotik.webui.servlet.action.SessionFsRoots
import com.simiacryptus.cognotik.webui.servlet.payment.NoOpPaymentProvider
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
import org.eclipse.jetty.util.resource.ResourceCollection
import org.eclipse.jetty.webapp.WebAppClassLoader
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.util.*
import kotlin.system.exitProcess

abstract class ApplicationDirectory(
  val localName: String = "localhost",
  val publicName: String? = null,
  val port: Int = 8081,
) {
  init {
    log.info("Creating ApplicationDirectory instance with localName='$localName', publicName='${publicName ?: "null"}', port=$port")
    require(publicName != "localhost")
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
    if (isServer && publicName != null) "https://$publicName" else "http://$localName:$port".also {
      log.debug("Generated domain name: $it (isServer: $isServer)")
    }

  open val welcomeResources: Resource = ResourceCollection(
    *allResources("welcome").map(Resource::newResource).toTypedArray()
  )
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
  open val welcomeServlet: HttpServlet = WelcomeServlet(this)
    .also { log.debug("Initialized WelcomeServlet") }
  open val apiKeyServlet: HttpServlet = ApiKeyServlet()
    .also { log.debug("Initialized ApiKeyServlet") }
  open val taskConfigServlet: HttpServlet = TaskConfigServlet()
    .also { log.debug("Initialized TaskConfigServlet") }
  open val loginServlet: HttpServlet = LoginServlet()
    .also { log.debug("Initialized SimpleLoginServlet") }
  open val appDirectoryServlet: HttpServlet = AppDirectoryServlet()
    .also { log.debug("Initialized AppDirectoryServlet") }
  open val sitemapServlet: HttpServlet = SitemapServlet()
    .also { log.debug("Initialized SitemapServlet") }
  open val pluginManagerServlet: HttpServlet? by lazy { PluginManagerServlet().also { log.debug("Initialized PluginManagerServlet") } }
  open val videoLandingServlet by lazy { VideoLandingServlet() }

  open val authCallbackServlet by lazy { AuthCallbackServlet() }


  protected open val docopsServlet: HttpServlet by lazy { DocProcessorServlet() }

  open val cognitiveConfigServlet: HttpServlet = CognitiveConfigServlet()
    .also { log.debug("Initialized CognitiveConfigServlet") }

  open val sessionsServlet: HttpServlet = SessionsServlet()
    .also { log.debug("Initialized SessionsServlet") }
  open val sessionUsageDetailsServlet by lazy { SessionUsageDetailsServlet() }
    .also { log.debug("Initialized SessionUsageDetailsServlet") }

  open val creditsServlet: CreditsServlet =
    CreditsServlet(NoOpPaymentProvider(ApplicationServices.fileApplicationServices().usageDB))
      .also { log.debug("Initialized CreditsServlet") }

  open fun setupPlatform() {
    log.info("Setting up platform (default implementation - no action taken)")
  }

  open fun _main(vararg args: String) {
    try {
      log.info("Starting application with args: ${args.joinToString(", ")}")
      init(args.contains("--server"))
      setupPlatform()
      installFsApiActions()
      ApplicationServicesConfig.isLocked = true
      val server = start(port, "0.0.0.0", *(webAppContexts()))
      log.info("Server started successfully on port $port")
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

  /**
   * Publishes the DocOps endpoint and installs the extended FS API actions (DocOps,
   * Tasks, Modify Files) that were previously CLI-only.
   *
   * [docopsServlet] is resolved *lazily* through [DocOpsServlets]: in some deployments it is
   * overridden with a proxy implementation, and every doc-ops invocation (HTTP mount, the
   * `.fsapi/v1/docops` action, `?resolveParam=target`) must go through that instance rather
   * than build its own `DocProcessor`.
   *
   * AutoFix is intentionally *not* registered here: it needs a fix-loop implementation
   * ([com.simiacryptus.cognotik.webui.servlet.action.AutoFixRunner]) which this server does
   * not ship, and arbitrary command execution is not appropriate for a shared mount.
   */
  protected open fun installFsApiActions() {
    domainName = domainName(true)
    log.info("Publishing DocOps endpoint and installing extended FS API actions")
    DocOpsServlets.install { docopsServlet as? DocProcessorServlet }
    DocOpsFsActions.install(
      DocOpsFsActions.Config(
        root = SessionFsRoots::rootOf,
        user = SessionFsRoots::userOf,
      )
    )
    ModifyFilesFsAction.install(
      ModifyFilesFsAction.Config(
        root = SessionFsRoots::rootOf,
        user = SessionFsRoots::userOf,
        chatUri = {
          URI(
            domainName.ifBlank {
              if (publicName != null) {
                "http://$publicName"
              } else {
                "http://$localName:$port"
              }
            }
          )
        },
      )
    )
  }

  open fun webAppContexts(): Array<WebAppContext> {
    return listOfNotNull(
      run { log.debug("Creating web app contexts"); null },
      newWebAppContext("/", welcomeResources, "welcome", welcomeServlet).also { ctx ->
        val sitemapHolder = ServletHolder(sitemapServlet)
        sitemapHolder.registration.setMultipartConfig(MultipartConfigElement("./tmp"))
        ctx.addServlet(sitemapHolder, "/robots.txt")
        ctx.addServlet(sitemapHolder, "/sitemap.xml")
        ctx.addServlet(sitemapHolder, "/sitemap")
      },
      newWebAppContext("/auth/*", authCallbackServlet),
      newWebAppContext("/logout", logoutServlet),
      newWebAppContext("/login", loginServlet),
      newWebAppContext("/appDirectory", appDirectoryServlet),
      newWebAppContext("/video", videoLandingServlet),
      newWebAppContext("/api", welcomeServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/sessions", sessionsServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/sessionUsage", sessionUsageDetailsServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/credits", creditsServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/apiProviders", apiProviderServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/apiKeys", apiKeyServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/cognitiveConfig", cognitiveConfigServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/docops", docopsServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/userInfo", userInfoServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/userSettings", userSettingsServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/usage", usageServlet).configureAuth(ApplicationServer::class.java),
      newWebAppContext("/taskConfig", taskConfigServlet).configureAuth(ApplicationServer::class.java),
      pluginManagerServlet?.let { pluginManagerServlet ->
        newWebAppContext("/pluginManager", pluginManagerServlet).configureAuth(ApplicationServer::class.java)
      },
      newWebAppContext("/gifts/*", GiftedCreditsServlet()),
    ).toTypedArray() + childWebApps.map {
      log.debug("Adding child web app context for path: ${it.path}")
      newWebAppContext(it.path, it.server)
    }.also { contexts ->
      log.info("Created ${contexts.size} web app contexts total")
    }
  }

  protected open fun WebAppContext.configureAuth(applicationClass: Class<ApplicationServer>): WebAppContext {
    log.debug("Adding authentication filter to context: ${this.contextPath}")
    addFilter(authFilter(applicationClass), "/*", EnumSet.of(DispatcherType.REQUEST))
    return this
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
    fun allResources(resourceName: String): List<URL> {
      log.debug("Loading all resources for name: $resourceName")
      val resources = Thread.currentThread().contextClassLoader.getResources(resourceName).toList()
      log.debug("Found ${resources.size} resource(s) for name: $resourceName")
      return resources
    }
  }

}