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
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.models.chat.AnthropicModels
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.ServerSocket

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
        
        /**
         * Create a new instance with the given context and port
         */
        fun create(context: Context, port: Int = 8080): AndroidCognotikApps {
            return AndroidCognotikApps(context, "localhost", "localhost", port)
        }
    }

    override fun authenticatedWebsite() = object : OAuthBase("") {
        override fun configure(context: WebAppContext, addFilter: Boolean) = context
    }

    override fun setupPlatform() {
        super.setupPlatform()
        val mockUser = User(
            "1",
            "user@android.local",
            "Android User",
            ""
        )
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
    }

    private val describer = AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "com.simiacryptus"
    )
    private val model = AnthropicModels.Claude35Haiku

    override val childWebApps by lazy {
        val parsingModel = model
        val filesDir = context.filesDir.absolutePath
        val planSettings = PlanSettings(
            defaultModel = model,
            parsingModel = parsingModel,
            workingDir = filesDir
        )
        listOf(
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
    }

    /**
     * Find an available port for the Android app, avoiding system ports
     */
    fun findAvailablePort(startPort: Int = 8080): Int {
        var port = startPort
        var attempts = 0
        while (attempts < MAX_PORT_ATTEMPTS) {
            try {
                ServerSocket(port).use {
                    log.debug("Port $port is available")
                    return port
                }
            } catch (e: IOException) {
                log.debug("Port $port is not available, trying next port")
                port++
                attempts++
            }
        }
        log.warn("Could not find available port after $MAX_PORT_ATTEMPTS attempts, using random port")
        return ServerSocket(0).use { it.localPort }
    }

    /**
     * Start the server on Android - simplified version without daemon functionality
     */
    fun startServer(): Int {
        log.info("Starting Android Cognotik server...")
        
        val actualPort = findAvailablePort(port)
        log.info("Using port $actualPort")
        
        try {
            // Create a new instance with the correct port
            val serverInstance = create(context, actualPort)
            serverInstance._main() // Start the server
            log.info("Android Cognotik server started successfully on port $actualPort")
            return actualPort
        } catch (e: Exception) {
            log.error("Failed to start Android Cognotik server", e)
            throw e
        }
    }

    // Remove browse() functionality since Android handles this differently
    override fun browse() {
        // Android will handle browsing through the WebView in MainActivity
    }
}