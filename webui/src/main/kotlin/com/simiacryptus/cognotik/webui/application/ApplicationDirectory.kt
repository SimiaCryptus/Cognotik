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
  open val simpleLoginServlet: HttpServlet = SimpleLoginServlet()
    .also { log.debug("Initialized SimpleLoginServlet") }


  protected open val docopsServlet by lazy { DocProcessorServlet() }

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

  open fun _main(vararg args: String) {
    try {
      log.info("Starting application with args: ${args.joinToString(", ")}")
      init(args.contains("--server"))
      setupPlatform()
      isLocked = true
      val server = start(port, "127.0.0.1", *(webAppContexts()))
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

  open fun webAppContexts() = listOfNotNull(
    run { log.debug("Creating web app contexts"); null },
    newWebAppContext("/logout", logoutServlet),
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
   newWebAppContext("/login", simpleLoginServlet).also {
     log.debug("Configuring login context")
   },
    newWebAppContext("/", welcomeResources, "welcome", welcomeServlet).let {
      log.debug("Configuring root context with welcome resources")
      authenticatedWebsite()?.configure(it, false) ?: it
    },
    newWebAppContext("/api", welcomeServlet).let {
      log.debug("Configuring API context")
      authenticatedWebsite()?.configure(it, false) ?: it
    },
    newWebAppContext("/docops", docopsServlet).let {
      log.debug("Configuring docops context with servlet")
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