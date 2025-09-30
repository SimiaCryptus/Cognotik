package com.simiacryptus.cognotik.android

import android.content.Context
import com.simiacryptus.cognotik.apps.general.UnifiedPlanApp
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.cognitive.AutoPlanMode
import com.simiacryptus.cognotik.plan.cognitive.GoalOrientedMode
import com.simiacryptus.cognotik.plan.cognitive.PlanAheadMode
import com.simiacryptus.cognotik.plan.cognitive.TaskChatMode
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.chat.BasicChatApp
import com.simiacryptus.cognotik.webui.servlet.OAuthBase
import com.simiacryptus.cognotik.webui.servlet.WelcomeServlet
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.chat.model.Chatter
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.instance
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.util.resource.PathResource
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android-adapted version of CognotikApps that removes desktop-specific features
 * like system tray, daemon client socket server, and JavaFX dependencies.
 */
class AndroidCognotikApps private constructor(
    private val androidContext: Context,
    localName: String = "localhost",
    publicName: String = "localhost",
    port: Int = 12891
) : ApplicationDirectory(
    localName = localName, 
    publicName = publicName, 
    port = port
) {

    companion object {
        private val log = LoggerFactory.getLogger(AndroidCognotikApps::class.java.name)
        private const val MAX_PORT_ATTEMPTS = 10
        private val isInitialized = AtomicBoolean(false)
        
        /**
         * Create a new instance with the given context and port
         */
        fun create(context: Context, port: Int = 12891): AndroidCognotikApps {
            log.info("Creating AndroidCognotikApps instance with port: $port")
            log.debug("Context: ${context.javaClass.simpleName}, Files dir: ${context.filesDir.absolutePath}")
            return AndroidCognotikApps(
                androidContext = context,
                localName = "localhost",
                publicName = "localhost",
                port = port
            )
        }
    }

    override fun authenticatedWebsite() = object : OAuthBase("") {
        override fun configure(context: WebAppContext, addFilter: Boolean) = context
    }
    private fun createAndroidWelcomeResources(): Resource {
        try {
            // Create a temporary directory for welcome resources
            val welcomeDir = File(androidContext.filesDir, "welcome")
            if (!welcomeDir.exists()) {
                welcomeDir.mkdirs()
                log.debug("Created welcome directory: ${welcomeDir.absolutePath}")
            }
            // Create a simple index.html file if it doesn't exist
            val indexFile = File(welcomeDir, "index.html")
            if (!indexFile.exists()) {
                val htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Cognotik Apps</title>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { font-family: Arial, sans-serif; margin: 20px; }
                            h1 { color: #333; }
                            .app-list { list-style: none; padding: 0; }
                            .app-item { margin: 10px 0; }
                            .app-link { 
                                display: inline-block; 
                                padding: 10px 20px; 
                                background: #007bff; 
                                color: white; 
                                text-decoration: none; 
                                border-radius: 5px; 
                            }
                            .app-link:hover { background: #0056b3; }
                        </style>
                    </head>
                    <body>
                        <h1>Welcome to Cognotik Apps</h1>
                        <p>Select an application:</p>
                        <ul class="app-list">
                            <li class="app-item"><a href="/chat" class="app-link">Chat</a></li>
                            <li class="app-item"><a href="/taskChat" class="app-link">Task Runner</a></li>
                            <li class="app-item"><a href="/autoPlan" class="app-link">Auto Plan</a></li>
                            <li class="app-item"><a href="/planAhead" class="app-link">Plan Ahead</a></li>
                            <li class="app-item"><a href="/goalOriented" class="app-link">Goal Oriented</a></li>
                        </ul>
                    </body>
                    </html>
                """.trimIndent()
                indexFile.writeText(htmlContent)
                log.debug("Created index.html file")
            }
            return PathResource(welcomeDir.toPath())
        } catch (e: Exception) {
            log.error("Failed to create Android welcome resources", e)
            // Return an empty PathResource as fallback
            val emptyDir = File(androidContext.filesDir, "empty")
            emptyDir.mkdirs()
            return PathResource(emptyDir.toPath())
        }
    }
    
    override val welcomeResources: Resource by lazy {
        try {
            log.debug("Initializing welcome resources for Android")
            createAndroidWelcomeResources()
        } catch (e: Exception) {
            log.error("Failed to load welcome resources", e)
            // Create minimal fallback
            val fallbackDir = File(androidContext.filesDir, "fallback")
            fallbackDir.mkdirs()
            PathResource(fallbackDir.toPath())
        }
    }
    override val welcomeServlet: WelcomeServlet by lazy {
        log.debug("Creating WelcomeServlet for Android")
        WelcomeServlet(this)
    }

    override fun setupPlatform() {
        if (isInitialized.compareAndSet(false, true)) {
            log.info("Setting up platform for Android Cognotik")
        } else {
            log.debug("Platform already initialized, skipping setup")
            return
        }
        
        super.setupPlatform()
        log.debug("Creating mock authentication and authorization managers")
        log.debug("Created mock user: ${UserSettingsManager.Companion.defaultUser.email}")
        
        ApplicationServices.authenticationManager = object : AuthenticationInterface {
            override fun getUser(accessToken: String?) = UserSettingsManager.Companion.defaultUser
            override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
            override fun logout(accessToken: String, user: User) {}
        }
        ApplicationServices.authorizationManager = object : AuthorizationManager() {
            @Suppress("UNUSED_PARAMETER")
            override fun isAuthorized(
                applicationClass: Class<*>?,
                user: User?,
                operationType: AuthorizationInterface.OperationType
            ): Boolean = true
        }
        log.info("Platform setup completed successfully")
    }

    private val describer = AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "com.simiacryptus"
    )
    private val model : ApiChatModel = AnthropicModels.Claude35Haiku.let {
        ApiChatModel(
            model = it,
            provider = ApiData(
                provider = it.provider
            ).validate()
        )
    }

    override val childWebApps: List<ChildWebApp> by lazy {
        try {
            log.info("Initializing child web apps...")
            createChildWebApps()
        } catch (e: Exception) {
            log.error("Failed to initialize child web apps", e)
            log.error("Exception details: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun createChildWebApps(): List<ChildWebApp> {
        val filesDir = androidContext.filesDir.absolutePath
        log.info("Using files directory: $filesDir")
        log.debug("Parsing model: ${model.javaClass.simpleName}")
        log.debug("Default model: ${model.javaClass.simpleName}")
        
        val planSettings = object : PlanSettings(
            defaultModel = model.instance()!!,
            parsingModel = model.instance()!!,
            workingDir = filesDir
        ) {
            override fun instance(model: ApiChatModel): Chatter {
                TODO()
            }

        }
        log.debug("Created plan settings with working directory: ${planSettings.workingDir}")
        
        val webApps = listOf(
            ChildWebApp("/chat", BasicChatApp(File(filesDir), model.model!!, model.model!!)),
            ChildWebApp(
                "/taskChat", object : UnifiedPlanApp(
                    path = "/taskChat",
                    applicationName = "Task-Runner",
                    planSettings = planSettings,
                    cognitiveStrategy = TaskChatMode,
                    describer = describer
                ) {
                    override fun instance(model: ApiChatModel): Chatter {
                        TODO("Not yet implemented")
                    }
                }
            ),
            ChildWebApp(
                "/autoPlan", object : UnifiedPlanApp(
                    path = "/autoPlan",
                    applicationName = "Auto-Plan",
                    planSettings = planSettings,
                    cognitiveStrategy = AutoPlanMode,
                    describer = describer
                ) {
                    override fun instance(model: ApiChatModel): Chatter {
                        TODO("Not yet implemented")
                    }
                }
            ),
            ChildWebApp(
                "/planAhead", object : UnifiedPlanApp(
                    path = "/planAhead",
                    applicationName = "Plan-Ahead",
                    planSettings = planSettings,
                    cognitiveStrategy = PlanAheadMode,
                    describer = describer
                ) {
                    override fun instance(model: ApiChatModel): Chatter {
                        TODO("Not yet implemented")
                    }
                }
            ),
            ChildWebApp(
                "/goalOriented", object : UnifiedPlanApp(
                    path = "/goalOriented",
                    applicationName = "Goal-Oriented",
                    planSettings = planSettings,
                    cognitiveStrategy = GoalOrientedMode,
                    describer = describer
                ) {
                    override fun instance(model: ApiChatModel): Chatter {
                        TODO("Not yet implemented")
                    }
                }
            )
        )
        log.info("Created ${webApps.size} child web apps:")
        webApps.forEach { app ->
            log.debug("  - ${app.path}: ${app.server.javaClass.simpleName}")
        }
        return webApps
    }

    /**
     * Find an available port for the Android app, avoiding system ports
     */
    fun findAvailablePort(startPort: Int = 12891): Int {
        log.debug("Searching for available port starting from: $startPort")
        var port = startPort
        var attempts = 0
        while (attempts < MAX_PORT_ATTEMPTS) {
            try {
                ServerSocket(port).use {
                    log.info("Found available port: $port (attempt ${attempts + 1})")
                    return port
                }
            } catch (e: IOException) {
                log.debug("Port $port is not available (${e.message}), trying next port")
                port++
                attempts++
            }
        }
        log.warn("Could not find available port after $MAX_PORT_ATTEMPTS attempts, using random port")
        val randomPort = ServerSocket(0).use { it.localPort }
        log.info("Using random port: $randomPort")
        return randomPort
    }

    /**
     * Start the server on Android - simplified version without daemon functionality
     */
    fun startServer(): Int {
        log.info("Starting Android Cognotik server...")
        log.debug("Current thread: ${Thread.currentThread().name}")
        
        
        
        
        try {
            // Ensure platform is set up before accessing childWebApps
            log.debug("Setting up platform...")
            setupPlatform()
            log.debug("Finding available port...")
            val actualPort = findAvailablePort(port)
            log.info("Server will use port: $actualPort")
            
            // Create a new instance with the correct port
            log.debug("Creating server instance with port: $actualPort")
            val serverInstance = create(androidContext, actualPort)
            log.debug("Setting up platform for server instance...")
            serverInstance.setupPlatform() // Ensure platform is set up
            // Pre-initialize lazy properties to catch any initialization errors early
            log.debug("Pre-initializing welcome resources...")
            val resources = serverInstance.welcomeResources
            log.debug("Welcome resources initialized: ${resources.javaClass.simpleName}")
            log.debug("Pre-initializing child web apps...")
            val apps = serverInstance.childWebApps
            log.debug("Child web apps initialized: ${apps.size} apps")
            
            log.debug("Starting server main process...")
            serverInstance._main() // Start the server
            log.info("Android Cognotik server started successfully on port $actualPort")
            log.info("Server accessible at: http://localhost:$actualPort")
            return actualPort
        } catch (e: Exception) {
            log.error("Failed to start Android Cognotik server", e)
            log.error("Exception type: ${e.javaClass.simpleName}")
            log.error("Exception message: ${e.message}")
            log.error("Stack trace:", e)
            throw e
        }
    }

    // Remove browse() functionality since Android handles this differently
    override fun browse() {
        log.debug("Browse method called - Android will handle browsing through WebView")
        // Android will handle browsing through the WebView in MainActivity
    }
}

