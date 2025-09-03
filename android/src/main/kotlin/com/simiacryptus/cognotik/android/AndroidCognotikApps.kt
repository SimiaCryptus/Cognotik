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
import com.simiacryptus.jopenai.chat.model.AnthropicModels
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android-adapted version of CognotikApps that removes desktop-specific features
 * like system tray, daemon client socket server, and JavaFX dependencies.
 */
class AndroidCognotikApps private constructor(
    private val context: Context,
    localName: String = "localhost", 
    publicName: String = "localhost",
    port: Int = 8080
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
        fun create(context: Context, port: Int = 8080): AndroidCognotikApps {
            log.info("Creating AndroidCognotikApps instance with port: $port")
            log.debug("Context: ${context.javaClass.simpleName}, Files dir: ${context.filesDir.absolutePath}")
            return AndroidCognotikApps(context, "localhost", "localhost", port)
        }
    }

    override fun authenticatedWebsite() = object : OAuthBase("") {
        override fun configure(context: WebAppContext, addFilter: Boolean) = context
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
        val mockUser = User(
            "1",
            "user@android.local",
            "Android User",
            ""
        )
        log.debug("Created mock user: ${mockUser.email}")
        
        ApplicationServices.authenticationManager = object : AuthenticationInterface {
            override fun getUser(accessToken: String?) = mockUser
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
    private val model = AnthropicModels.Claude35Haiku

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
        val parsingModel = model
        val filesDir = context.filesDir.absolutePath
        log.info("Using files directory: $filesDir")
        log.debug("Parsing model: ${parsingModel.javaClass.simpleName}")
        log.debug("Default model: ${model.javaClass.simpleName}")
        
        val planSettings = PlanSettings(
            defaultModel = model,
            parsingModel = parsingModel,
            workingDir = filesDir
        )
        log.debug("Created plan settings with working directory: ${planSettings.workingDir}")
        
        val webApps = listOf(
            ChildWebApp("/chat", BasicChatApp(File(filesDir), model, parsingModel)),
            ChildWebApp(
                "/taskChat", UnifiedPlanApp(
                    path = "/taskChat",
                    applicationName = "Task-Runner",
                    planSettings = planSettings,
                    model = model,
                    parsingModel = parsingModel,
                    cognitiveStrategy = TaskChatMode,
                    describer = describer
                )
            ),
            ChildWebApp(
                "/autoPlan", UnifiedPlanApp(
                    path = "/autoPlan",
                    applicationName = "Auto-Plan",
                    planSettings = planSettings,
                    model = model,
                    parsingModel = parsingModel,
                    cognitiveStrategy = AutoPlanMode,
                    describer = describer
                )
            ),
            ChildWebApp(
                "/planAhead", UnifiedPlanApp(
                    path = "/planAhead",
                    applicationName = "Plan-Ahead",
                    planSettings = planSettings,
                    model = model,
                    parsingModel = parsingModel,
                    cognitiveStrategy = PlanAheadMode,
                    describer = describer
                )
            ),
            ChildWebApp(
                "/goalOriented", UnifiedPlanApp(
                    path = "/goalOriented",
                    applicationName = "Goal-Oriented",
                    planSettings = planSettings,
                    model = model,
                    parsingModel = parsingModel,
                    cognitiveStrategy = GoalOrientedMode,
                    describer = describer
                )
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
    fun findAvailablePort(startPort: Int = 8080): Int {
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
        
        // Ensure platform is set up before accessing childWebApps
        log.debug("Setting up platform...")
        setupPlatform()
        log.debug("Finding available port...")
        
        
        val actualPort = findAvailablePort(port)
        log.info("Server will use port: $actualPort")
        
        try {
            // Create a new instance with the correct port
            log.debug("Creating server instance with port: $actualPort")
            val serverInstance = create(context, actualPort)
            log.debug("Setting up platform for server instance...")
            serverInstance.setupPlatform() // Ensure platform is set up
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