package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.UpdateManager.checkUpdate
import com.simiacryptus.cognotik.apps.general.UnifiedPlanApp
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.cognitive.AutoPlanMode
import com.simiacryptus.cognotik.plan.cognitive.PlanAheadMode
import com.simiacryptus.cognotik.plan.cognitive.TaskChatMode
import com.simiacryptus.cognotik.plan.cognitive.GoalOrientedMode
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.chat.BasicChatApp
import com.simiacryptus.cognotik.webui.servlet.OAuthBase
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.models.OpenAIModels
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.SystemTray
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

open class CognotikApps(
    localName: String, publicName: String, port: Int
) : ApplicationDirectory(
    localName = localName, publicName = publicName, port = port
) {
    private var systemTrayManager: SystemTrayManager? = null
    private var socketServer: ServerSocket? = null
    private var socketThread: Thread? = null

    companion object {
        private val log = LoggerFactory.getLogger(CognotikApps::class.java.name)
        private const val MAX_PORT_ATTEMPTS = 10
        val scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                if (args.isEmpty()) {
                    log.info("No arguments provided - defaulting to server mode with default options")
                    handleServer()
                    return
                }
                when (args[0].lowercase()) {
                    "server" -> handleServer(*args.sliceArray(1 until args.size))
                    "help", "-h", "--help" -> printUsage()
                    "daemon" -> {
                        handleServer(*args.sliceArray(1 until args.size))
                    }

                    else -> {
                        handleServer()
                    }
                }
            } catch (e: Exception) {
                log.error("Fatal error: ${e.message}", e)

                Runtime.getRuntime().addShutdownHook(Thread {
                    log.info("Shutting down server...")
                    server?.stopServer()
                })
                System.exit(1)
            }
        }

        private var server: CognotikApps? = null




        private fun handleServer(vararg args: String) {
            log.info("Parsing server options...")
            val options = parseServerOptions(*args)
            log.info("Configuring server with options: port=${options.port}, host=${options.host}, publicName=${options.publicName}")

            var actualPort = options.port
            try {
                ServerSocket(actualPort).use {
                    log.debug("Port $actualPort is available")
                }
            } catch (e: IOException) {
                log.info("Port ${options.port} is in use, finding alternative port")
                println("Port ${options.port} is in use, finding alternative port")
                actualPort = findAvailablePort(options.port + 1)
                log.info("Using alternative port $actualPort")
                println("Using alternative port $actualPort")
            }
            scheduledExecutorService.scheduleAtFixedRate({ checkUpdate() },
                0, 7 * 24, TimeUnit.HOURS)
            server = CognotikApps(
                localName = options.host,
                publicName = options.publicName,
                port = actualPort
            )
            server?.initSystemTray()
            server?.startSocketServer(actualPort + 1)

            Runtime.getRuntime().addShutdownHook(Thread {
                log.info("Shutdown hook triggered, stopping server...")
                server?.stopServer()
            })
            // Call _main with NO server options (strip out --port/--host/--public-name and their values)
            val filteredArgs = args.filterIndexed { i, arg ->
                arg in listOf("--port", "--host", "--public-name") ||
                        (i > 0 && args[i - 1] in listOf("--port", "--host", "--public-name"))
            }.toTypedArray()
            server?._main(*filteredArgs)
        }

        private fun findAvailablePort(startPort: Int): Int {
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

        private fun printUsage() {
            println(
                """
                Cognotik Server
                Usage:
                  cognotik <command> [options]
                Commands:
                  server     Start the server
                  help      Show this help message
                For server options:
                  cognotik server --help
            """.trimIndent()
            )
        }

        private data class ServerOptions(
            val port: Int = 7682,
            val host: String = "localhost",
            val publicName: String = "apps.simiacrypt.us"
        )

        private fun parseServerOptions(vararg args: String): ServerOptions {
            var port = 7682
            var host = "localhost"
            var publicName = "apps.simiacrypt.us"
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--port" -> {
                        if (i + 1 < args.size) {
                            log.debug("Setting port to: ${args[i + 1]}")
                            port = args[++i].toIntOrNull() ?: run {
                                log.error("Invalid port number: ${args[i]}")
                                System.exit(1)
                                throw IllegalArgumentException("Invalid port number: ${args[i]}")
                            }
                        }
                    }

                    "--host" -> if (i + 1 < args.size) host = args[++i]
                    "--public-name" -> if (i + 1 < args.size) publicName = args[++i]
                    else -> {
                        log.error("Unknown server option: ${args[i]}")
                        throw IllegalArgumentException("Unknown server option: ${args[i]}")
                    }
                }
                i++
            }
            log.debug("Server options parsed successfully")
            return ServerOptions(port, host, publicName)
        }
    }

    private fun initSystemTray() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray is not supported")
            return
        }
        try {
            systemTrayManager = SystemTrayManager(
                port = port,
                host = localName,
                onExit = {
                    log.info("Exit requested from system tray")
                    stopServer()
                    System.exit(0)
                })
            systemTrayManager?.initialize()
        } catch (e: Exception) {
            log.warn("Failed to initialize system tray: ${e.message}")
        }
    }

    fun stopServer() {
        systemTrayManager?.remove()
        stopSocketServer()
    }

    open val api2 = OpenAIClient(workPool = Executors.newCachedThreadPool())

    override fun authenticatedWebsite() = object : OAuthBase("") {
        override fun configure(context: WebAppContext, addFilter: Boolean) = context
    }

    override fun setupPlatform() {
        super.setupPlatform()
        val mockUser = User(
            "1",
            "user@mock.test",
            "Test User",
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

    val describer = AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "com.simiacryptus"
    )
    override val childWebApps by lazy {
        val model = OpenAIModels.GPT4o
        val parsingModel = OpenAIModels.GPT4oMini
        val planSettings = PlanSettings(
            defaultModel = model,
            parsingModel = parsingModel,
            workingDir = "."
        )
        listOf(
            ChildWebApp("/chat", BasicChatApp(File("."), model, parsingModel)),
            ChildWebApp(
                "/taskChat", UnifiedPlanApp(
                    path = "/taskChat",
                    applicationName = "Task-Runner",
                    planSettings = planSettings,
                    model = model,
                    parsingModel = parsingModel,
                    api2 = api2,
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
                    api2 = api2,
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
                    api2 = api2,
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
                    api2 = api2,
                    cognitiveStrategy = GoalOrientedMode,
                    describer = describer
                )
            )
        )
    }

    protected open fun onMessage(line: String?): String {
        log.info("Received command from DaemonClient: $line")
        if (line != null && line.trim().equals("shutdown", ignoreCase = true)) {
            log.info("Shutdown command received via socket. Stopping server...")

            Thread {
                Thread.sleep(100)

                stopServer()
                System.exit(0)
            }.start()
            return "Server shutting down"
        } else {
            try {
                Desktop.getDesktop().browse(URI("$domainName/#${line.urlEncode()}"))
            } catch (e: Throwable) {

            }
            return "OK: $line"
        }
    }

    /**
     * Start a simple socket server to listen for commands from DaemonClient.
     * Responds with a simple acknowledgment or shutdown message.
     */
    private fun startSocketServer(port: Int) {
        if (socketServer != null) {
            log.warn("Socket server already started on port $port")
            return
        }
        socketThread = Thread {
            try {
                try {
                    socketServer = ServerSocket(port)
                    log.info("Socket server started on port $port")
                } catch (e: IOException) {
                    log.error("Failed to start socket server on port $port: ${e.message}")

                    for (attemptPort in (port + 1)..(port + 10)) {
                        try {
                            socketServer = ServerSocket(attemptPort)
                            log.info("Socket server started on alternative port $attemptPort")
                            break
                        } catch (e2: IOException) {
                            log.debug("Failed to start socket server on alternative port $attemptPort: ${e2.message}")
                        }
                    }

                    if (socketServer == null) {
                        log.error("Could not find any available port for socket server")
                        return@Thread
                    }
                }

                while (!socketServer!!.isClosed) {
                    val client = try {
                        socketServer!!.accept()
                    } catch (e: IOException) {
                        log.info("Socket server stopped accepting connections: ${e.message}")
                        break
                    }
                    Thread {
                        var output: BufferedWriter? = null
                        try {
                            val input = client.getInputStream().bufferedReader()
                            output = client.getOutputStream().bufferedWriter()
                            val line = input.readLine()
                            val response = if (line != null) onMessage(line) else "ERROR: No command received"
                            output.write("$response\n")
                        } catch (e: Exception) {
                            output?.write("ERROR: ${(e.message ?: e.toString()).replace('\n', ' ')}\n")
                            log.error("Socket handler error: ${e.message}", e)
                        } finally {
                            output?.flush()
                            try {
                                client.close()
                            } catch (_: Exception) {
                            }
                        }
                    }.start()
                }
            } catch (e: Exception) {
                log.error("Socket server error: ${e.message}", e)
            } finally {
                try {
                    socketServer?.close()
                } catch (_: Exception) {
                }
                socketServer = null
                log.info("Socket server thread exiting")
            }
        }
        socketThread?.isDaemon = true
        socketThread?.name = "AppServer-SocketThread"
        socketThread?.start()
    }

    private fun stopSocketServer() {
        try {
            socketServer?.close()
        } catch (_: Exception) {
        }
        socketThread?.interrupt()
        socketServer = null
        socketThread = null
    }

    override fun browse() {}

}




fun String?.urlEncode(): String {
    return this?.let {
        URLEncoder.encode(it, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
    } ?: ""
}