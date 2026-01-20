# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/CognotikApps.kt

```
package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.UpdateManager.checkUpdate
import com.simiacryptus.cognotik.apps.UnifiedPlanApp
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.PlanHarness.Companion.initDynamicEnums
import com.simiacryptus.cognotik.util.encrypt
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.chat.BasicChatApp
import com.simiacryptus.cognotik.webui.servlet.OAuthBase
import org.eclipse.jetty.webapp.WebAppContext
import java.awt.Desktop
import java.awt.SystemTray
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

val globalID = Session.newGlobalID()
val model = AnthropicModels.Claude35Haiku

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
                initDynamicEnums()
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
                exitProcess(1)
            }
        }

        private var server: CognotikApps? = null

        init {
            require(null != CodeRuntimes.GroovyRuntime) { "Groovy runtime not initialized" } // Force DynamicEnum initialization
        }

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
            scheduledExecutorService.scheduleAtFixedRate(
                { checkUpdate() },
                0, 7 * 24, TimeUnit.HOURS
            )
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
            val port: Int = 12891,
            val host: String = "localhost",
            val publicName: String = "apps.simiacrypt.us"
        )

        private fun parseServerOptions(vararg args: String): ServerOptions {
            var port = 12891
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
                                exitProcess(1)
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
                    exitProcess(0)
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

    override fun authenticatedWebsite() = object : OAuthBase("") {
        override fun configure(context: WebAppContext, addFilter: Boolean) = context
    }

    override fun setupPlatform() {
        super.setupPlatform()
        ApplicationServices.authenticationManager = object : AuthenticationInterface {
            override fun getUser(accessToken: String?) = defaultUser
            override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
            override fun logout(accessToken: String, user: User) {}
        }
        ApplicationServices.authorizationManager = object : AuthorizationManager() {
            override fun isAuthorized(
                applicationClass: Class<*>?,
                user: User?,
                operationType: AuthorizationInterface.OperationType
            ): Boolean = true
        }
    }

    override val childWebApps by lazy {
        OrchestrationConfig.instanceFn =
            { m -> m.instance() ?: throw IllegalStateException("Model or provider not set") }
        listOf(
            ChildWebApp("/chat", BasicChatApp(File("."), model, model)),
            ChildWebApp(
                "/taskChat", object : UnifiedPlanApp(
                    path = "/taskChat",
                    applicationName = "Task-Runner"
                ) {
                    override fun instance(model: ApiChatModel) = model.instance()
                        ?: throw IllegalStateException("Model or provider not set")
                }
            ),
        )
    }

    protected open fun onMessage(line: String?): String {
        log.info("Received command from DaemonClient: $line")
        if (line != null && line.trim().equals("shutdown", ignoreCase = true)) {
            log.info("Shutdown command received via socket. Stopping server...")

            Thread {
                Thread.sleep(100)

                stopServer()
                exitProcess(0)
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

fun ApiChatModel.instance(
    user: User = defaultUser,
    session: Session = globalID,
    service: ExecutorService = ApplicationServices.threadPoolManager.getPool(session, user),
    temperature: Double = 0.1
) = model?.instance(
    key = when (provider?.key) {
        null -> null
        "NONE".encrypt -> null
        else -> provider?.key
    } ?: ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user).apis.let {
        it.firstOrNull { it.provider == this.provider }?.key
            ?: it.firstOrNull { (it.provider?.name ?: "b") == (this.model?.provider?.name ?: "a") }?.key
            ?: throw IllegalStateException("No API key configured for model $model")
    },
    base = provider?.provider?.base ?: model?.provider?.base
    ?: throw IllegalStateException("No API base configured for model $model"),
    workPool = service,
    temperature = temperature,
    scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(session, user),
    onUsage = { model, usage ->
        ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
            session,
            user,
            model,
            usage
        )
    }
)

```

# /home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/DaemonClient.kt

```
package com.simiacryptus.cognotik

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.system.exitProcess

/**
 * Entry point for the daemon client.
 * This will launch the AppServer as a separate process (daemon) if needed, reconnect if possible, and dispatch commands to the server.
 * This should not use the logging system, we want to prevent creating log files in the current directory.
 */
object DaemonClient {
    private const val DEFAULT_PORT = 12891
    private const val DEFAULT_HOST = "localhost"
    private const val PID_FILE = "cognotik_server.pid"
    private const val SOCKET_PORT_OFFSET = 1
    private const val SESSION_DIR_BASE = ".cognotik"

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isNotEmpty() && args[0].equals("--stop", ignoreCase = true)) {
            stopServer()
            exitProcess(0)
        }
        if (args.isNotEmpty() && args[0].equals("server", ignoreCase = true)) {
            CognotikApps.main(args)
        } else {
            val port = DEFAULT_PORT
            val host = DEFAULT_HOST
            if (!isServerRunning(host, port)) {
                launchDaemon(port)
                waitForServer(host, port)
            }
            val commandArgs = if (args.isNotEmpty()) {
                args
            } else {
                arrayOf(createRandomSessionDir())
            }
            dispatchCommand(host, port + SOCKET_PORT_OFFSET, (commandArgs.take(1).map { it.trim('\'', '"') }.map {
                when (it) {
                    "." -> File(".").absolutePath
                    ".." -> File("..").absolutePath
                    else -> it
                }
            } + commandArgs.drop(1)).toTypedArray())
        }
    }

    fun createRandomSessionDir(): String {
        val baseDir = getHome()
        val sessionId = UUID.randomUUID().toString().substring(0, 8)
        val sessionDir = File(baseDir, sessionId)
        if (!sessionDir.exists()) {
            sessionDir.mkdirs()
        }
        return sessionDir.absolutePath
    }

    fun getHome(): File {
        val userHome = System.getProperty("user.home")
        val baseDir = File(userHome, SESSION_DIR_BASE)
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    private fun stopServer() {
        val host = DEFAULT_HOST
        val port = DEFAULT_PORT
        if (isServerRunning(host, port)) {
            try {
                Socket(host, port + SOCKET_PORT_OFFSET).use { socket ->
                    val out = PrintWriter(socket.getOutputStream(), true)
                    val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                    out.println("shutdown")
                    val response = input.readLine()
                }
                var attempts = 0
                while (isServerRunning(host, port) && attempts < 10) {
                    Thread.sleep(500)
                    attempts++
                }
                if (!isServerRunning(host, port)) {
                    return
                }
            } catch (e: Exception) {
                println("Failed to stop server via socket: ${e.message}")
            }
        }
        try {
            val pidFile = getHome().resolve(PID_FILE)
            if (pidFile.exists()) {
                val pid = pidFile.readText().trim().toLong()
                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                val processBuilder = if (isWindows) {
                    ProcessBuilder("taskkill", "/F", "/PID", pid.toString())
                } else {
                    ProcessBuilder("kill", "-9", pid.toString())
                }
                val process = processBuilder.start()
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    pidFile.delete()
                }
            }
        } catch (e: Exception) {
            println("Error stopping server: ${e.message}")
        }
    }

    private fun isServerRunning(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { true }
            true
        } catch (e: ConnectException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun waitForServer(host: String, port: Int, timeoutMs: Long = 10000L) {
        val start = System.currentTimeMillis()
        while (!isServerRunning(host, port)) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw RuntimeException("Timed out waiting for server to start")
            }
            Thread.sleep(200)
        }
    }

    private fun launchDaemon(port: Int) {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val scriptExt = if (isWindows) "bat" else "sh"
        val scriptFile = File.createTempFile("cognotik_daemon_", ".$scriptExt")
        val appPath = System.getProperty("jpackage.app-path", "")
        if (isWindows) {
            scriptFile.writeText(
                """
                @echo 
                start /b /min "" "${appPath.ifEmpty { "C:/Program Files/Cognotik/Cognotik.exe" }}" server --port $port
                exit
            """.trimIndent()
            )
        } else if (isMac) {
            scriptFile.writeText(
                """
               #!/bin/sh
               # Use caffeinate to prevent sleep and run in background without UI
               nohup /usr/bin/caffeinate -i "${appPath.ifEmpty { "/Applications/Cognotik.app/Contents/MacOS/Cognotik" }}" server --port $port >/dev/null 2>&1 &
               # Ensure the process doesn't show in dock
               defaults write "${if (appPath.isNotEmpty()) "${File(appPath).resolve("../../Info")}" else "/Applications/Cognotik.app/Contents/Info"}" LSUIElement -bool true
               exit 0
           """.trimIndent()
            )
            scriptFile.setExecutable(true)
        } else {
            scriptFile.writeText(
                """
                #!/bin/sh
                nohup ${appPath.ifEmpty { "/opt/cognotik/bin/Cognotik" }} server --port $port &
                exit 0
            """.trimIndent()
            )
            scriptFile.setExecutable(true)
        }
        val processBuilder = if (isWindows) {
            ProcessBuilder("cmd", "/c", scriptFile.absolutePath)
        } else {
            ProcessBuilder("sh", scriptFile.absolutePath)
        }
        processBuilder.directory(getHome())
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
        val process = try {
            processBuilder.start()
        } catch (e: Exception) {
            throw e
        }
        try {
            writePidFile(process)
        } catch (e: Exception) {
            println("Failed to write PID file: ${e.message}")
        }
        Thread.sleep(5000)
    }

    private fun writePidFile(process: Process) {
        try {
            val pid = process.pid()
            Files.write(Paths.get(PID_FILE), pid.toString().toByteArray())
        } catch (e: Exception) {
            println("Warning: Could not write PID file: ${e.message}")
        }
    }

    private fun dispatchCommand(host: String, port: Int, args: Array<String>) {
        try {
            Socket(host, port).use { socket ->
                val out = PrintWriter(socket.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                out.println(args.joinToString(" "))
                val response = input.readLine()
                if (response != null) {
                    println("Server response: $response")
                } else {
                    println("No response received from server.")
                }
            }
        } catch (e: Exception) {
            println("Failed to dispatch command: ${e.message}")
        }
    }

}
```

# /home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/SystemTrayManager.kt

```
package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.DaemonClient.createRandomSessionDir
import com.simiacryptus.cognotik.UpdateManager.currentVersion
import com.simiacryptus.cognotik.UpdateManager.latestVersion
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.net.URI
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class SystemTrayManager(
    private val port: Int,
    private val host: String,
    private val onExit: () -> Unit
) {
    private fun loadSvgImage(): Image? {
        return try {
            val svgStream = javaClass.getResourceAsStream("/toolbarIcon.svg")
            if (svgStream == null) {
                log.warn("Could not find toolbarIcon.svg")
                null
            } else {
                val transcoder = object : ImageTranscoder() {
                    var image: BufferedImage? = null
                    override fun createImage(w: Int, h: Int): BufferedImage {
                        return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                    }

                    override fun writeImage(img: BufferedImage, output: TranscoderOutput?) {
                        this.image = img
                    }
                }
                transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, 32f)
                transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, 32f)
                transcoder.transcode(TranscoderInput(svgStream), TranscoderOutput())
                transcoder.image
            }
        } catch (e: Exception) {
            log.error("Failed to load SVG image: ${e.message}", e)
            null
        }
    }

    fun initialize() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray is not supported")
            return
        }

        SwingUtilities.invokeLater {
            try {
                val tray = SystemTray.getSystemTray()
                val image = loadSvgImage()
                val popup = PopupMenu()

                popup.add(MenuItem("Open in Browser").apply {
                    addActionListener {
                        openInBrowser(host, port)
                    }
                })

                if (latestVersion.greaterThan(currentVersion)) {
                    popup.add(MenuItem("Update to $latestVersion").apply {
                        addActionListener {
                            confirm("Update to ${latestVersion.version}?") {
                                Thread {
                                    try {
                                        UpdateManager.doUpdate()
                                    } catch (e: Exception) {
                                        log.error("Failed to update: ${e.message}", e)
                                        showError("Failed to update")
                                    }
                                }.start()
                            }
                        }
                    })
                }

                popup.add(MenuItem("Exit").apply {
                    addActionListener {
                        confirm("Exit?") {
                            onExit()
                        }
                    }
                })

                trayIcon = TrayIcon(image, "Cognotik ${currentVersion}", popup).apply {
                    isImageAutoSize = true
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) {
                                openInBrowser(host, port)
                            }
                        }
                    })
                }

                tray.add(trayIcon)
                log.info("System tray icon initialized")
            } catch (e: Exception) {
                log.error("Failed to initialize system tray: ${e.message}", e)
                showError("Failed to initialize system tray")
            }
        }
    }

    fun remove() {
        SwingUtilities.invokeLater {
            try {
                trayIcon?.let { SystemTray.getSystemTray().remove(it) }
                log.info("System tray icon removed")
            } catch (e: Exception) {
                log.error("Failed to remove system tray icon: ${e.message}", e)
                showError("Failed to remove system tray icon")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SystemTrayManager::class.java)
        private var trayIcon: TrayIcon? = null
        private var lastErrorTime: Long = 0
        private val ERROR_COOLDOWN = 5000
        private var lastErrorMessage: String? = null

        fun confirm(message: String, onConfirm: () -> Unit) {
            val result = JOptionPane.showConfirmDialog(
                null,
                message,
                "Cognotik",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )
            if (result == JOptionPane.YES_OPTION) {
                onConfirm()
            }
        }

        fun openInBrowser(host: String, port: Int) {
            try {
                val sessionDir = createRandomSessionDir()
                val domainName =
                    "http://${if (host == "0.0.0.0") "localhost" else host}:${port}"
                val url = "$domainName/#${sessionDir.urlEncode()}"
                Desktop.getDesktop().browse(URI(url))
                log.info("Opened browser to $url")
            } catch (e: Exception) {
                log.error("Failed to open browser: ${e.message}", e)
                showError("Failed to open browser")
            }
        }

        fun showError(message: String) {
            val now = System.currentTimeMillis()
            if (now - lastErrorTime > ERROR_COOLDOWN && message != lastErrorMessage) {
                trayIcon?.displayMessage(
                    "Error",
                    message,
                    TrayIcon.MessageType.ERROR
                )
                lastErrorTime = now
                lastErrorMessage = message
            } else {
                log.debug("Suppressing error notification due to cooldown: $message")
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/UpdateManager.kt

```
package com.simiacryptus.cognotik

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.simiacryptus.cognotik.SystemTrayManager.Companion.confirm
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.util.LoggerFactory
import java.awt.BorderLayout
import java.awt.BorderLayout.*
import java.awt.Desktop
import java.awt.Dimension
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.BorderFactory.createEmptyBorder
import javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import kotlin.system.exitProcess


object UpdateManager {
    private val log = LoggerFactory.getLogger(UpdateManager::class.java)
    private const val REPO_OWNER = "SimiaCryptus"
    private const val REPO_NAME = "Cognotik"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
    private val httpClient = HttpClient.newBuilder().build()
    private val gson = Gson()

    // Cache the latest release to avoid repeated API calls
    private var cachedLatestRelease: Release? = null
    private var lastCheckTime: Long = 0
    private var cachedLatestVersion: Version? = null
    private var lastVersionCheckTime: Long = 0
    private const val CACHE_DURATION_MS = 3600000 // 1 hour

    data class Release(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("name") val name: String,
        @SerializedName("html_url") val htmlUrl: String,
        @SerializedName("assets") val assets: List<Asset>
    )

    data class Asset(
        @SerializedName("name") val name: String,
        @SerializedName("browser_download_url") val downloadUrl: String,
        @SerializedName("content_type") val contentType: String
    )

    fun doUpdate() {
        log.info("Starting update process")
        var progressDialog: JDialog? = null // Keep track of the dialog
        try {
            log.debug("Fetching latest release information")
            val latestRelease = fetchLatestRelease() ?: throw IOException("No releases found")
            log.info("Latest release found: ${latestRelease.name} (${latestRelease.tagName})")

            val currentOs = detectOperatingSystem()
            log.info("Detected operating system: $currentOs")

            val assetToDownload = findAssetForCurrentOs(latestRelease, currentOs)
                ?: throw IOException("No compatible download found for $currentOs")
            log.info("Found compatible asset for download: ${assetToDownload.name}")

            // Create and show progress dialog
            val canceled = AtomicBoolean(false)
            log.debug("Creating progress dialog for download")
            val message = "Downloading ${assetToDownload.name}..."
            val dialogHolder = arrayOfNulls<JDialog>(1)


            val progressBarHolder = arrayOfNulls<JProgressBar>(1)

            // Ensure dialog creation and showing happens on the EDT
            val semaphore = java.util.concurrent.Semaphore(0)
            SwingUtilities.invokeLater {
                val panel = JPanel(BorderLayout(10, 10)).apply {
                    this.border = createEmptyBorder(10, 10, 10, 10)
                }

                val progressBar = JProgressBar(0, 100).apply {
                    this.isIndeterminate = false
                    this.value = 0
                    this.preferredSize = Dimension(300, 20)
                    this.name = "downloadProgressBar" // Keep name for potential testing/lookup
                }
                progressBarHolder[0] = progressBar // Store progressBar reference

                dialogHolder[0] = JDialog().apply {
                    this.title = "Cognotik Update"
                    this.isModal = false // Keep non-modal for cancel button interaction
                    this.defaultCloseOperation = DISPOSE_ON_CLOSE

                    panel.add(JLabel(message), NORTH)
                    panel.add(progressBar, CENTER)
                    panel.add(JPanel().apply {
                        add(JButton("Cancel").apply {
                            addActionListener {
                                log.info("User canceled the download")
                                canceled.set(true)
                                // Optionally disable button or close dialog immediately on cancel
                                // dialogHolder[0]?.dispose() // Example: close dialog on cancel
                            }
                        })
                    }, SOUTH)

                    this.contentPane = panel
                    pack()
                    setLocationRelativeTo(null) // Center on screen
                    log.debug("Progress dialog created and configured, making visible")
                    this.isVisible = true // Show the dialog
                }
                semaphore.release()
            }
            semaphore.acquire()
            progressDialog = dialogHolder[0] // Assign to outer variable
            val progressBar = progressBarHolder[0]
                ?: throw IllegalStateException("Progress bar was not initialized") // Should not happen

            // Download the asset
            log.info("Starting download of asset: ${assetToDownload.name}")
            val tempFile = downloadAsset(assetToDownload, progressBar, canceled)


            log.info("Download completed successfully to: ${tempFile.absolutePath}")
            // Close the progress dialog as download is done
            progressDialog?.dispose()
            progressDialog = null // Nullify to avoid disposing again in finally

            // Launch the installer and exit the current application
            log.info("Launching installer and preparing to exit application")
            launchInstallerAndExit(tempFile, currentOs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt() // Re-interrupt the thread
            log.warn("Update process interrupted", e)
        } catch (e: Exception) {
            log.error("Update failed", e)
            log.debug("Stack trace for update failure", e)
            JOptionPane.showMessageDialog(
                null, "Update failed: ${e.message}", "Update Error", JOptionPane.ERROR_MESSAGE
            )
            throw e
        } finally {
            // Ensure dialog is disposed if an error occurred after it was created
            progressDialog?.dispose()
        }
    }

    data class Version(
        val version: String
    ) {
        override fun toString() = version
        fun greaterThan(other: Version) = greaterThan(other.version)
        fun greaterThan(other: String): Boolean {
            log.debug("Comparing versions: $version > $other")
            val thisParts = version.split('.').map { it.toIntOrNull() ?: 0 }
            val otherParts = other.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(thisParts.size, otherParts.size)) {
                val thisPart = if (i < thisParts.size) thisParts[i] else 0
                val otherPart = if (i < otherParts.size) otherParts[i] else 0
                if (thisPart > otherPart) return true
                if (thisPart < otherPart) return false
            }
            return false
        }
    }

    val currentVersion: Version
        get() {
            val version = System.getProperty("jpackage.app-version", "99.99.99").removePrefix("v")
            log.info("Current application version: $version")
            return Version(version)
        }

    val latestVersion: Version
        get() {
            log.debug("Retrieving latest version information")
            val now = System.currentTimeMillis()
            if (cachedLatestVersion != null && now - lastVersionCheckTime < CACHE_DURATION_MS) {
                log.debug("Using cached version information (age: ${(now - lastVersionCheckTime) / 1000} seconds)")
                return cachedLatestVersion!!
            }

            try {
                val release = fetchLatestRelease()
                return if (release != null) {
                    val version = release.tagName.removePrefix("v")
                    log.info("Latest available version: $version")
                    val versionObj = Version(version)
                    cachedLatestVersion = versionObj
                    lastVersionCheckTime = now
                    versionObj
                } else {
                    log.warn("Could not determine latest version, using current version as fallback")
                    val currentVer = currentVersion
                    cachedLatestVersion = currentVer
                    lastVersionCheckTime = now
                    currentVer
                }
            } catch (e: Exception) {
                log.error("Failed to fetch latest version", e)
                log.debug("Stack trace for version fetch failure", e)
                val currentVer = currentVersion
                cachedLatestVersion = currentVer
                lastVersionCheckTime = now
                return currentVer
            }
        }

    private fun fetchLatestRelease(): Release? {
        log.debug("Fetching latest release from GitHub API")
        val now = System.currentTimeMillis()
        if (cachedLatestRelease != null && now - lastCheckTime < CACHE_DURATION_MS) {
            log.debug("Using cached release information (age: ${(now - lastCheckTime) / 1000} seconds)")
            return cachedLatestRelease
        }

        try {
            log.debug("Building HTTP request to GitHub API")
            val request = HttpRequest.newBuilder().uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/vnd.github.v3+json").GET().build()
            log.debug("Sending request to GitHub API")

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.error("GitHub API returned error status code: ${response.statusCode()}")
                log.debug("Response body: ${response.body()}")
                return null
            }
            log.debug("Parsing GitHub API response")

            val releases = gson.fromJson(response.body(), Array<Release>::class.java)
            if (releases.isEmpty()) {
                log.warn("No releases found in GitHub API response")
                return null
            }
            log.info("Found ${releases.size} releases, using latest: ${releases[0].name} (${releases[0].tagName})")

            cachedLatestRelease = releases[0]
            lastCheckTime = now
            return cachedLatestRelease
        } catch (e: Exception) {
            log.error("Error fetching releases from GitHub", e)
            log.debug("Stack trace for GitHub API error", e)
            return null
        }
    }

    private fun detectOperatingSystem(): String {
        log.debug("Detecting operating system")
        val os = System.getProperty("os.name").lowercase()
        val detectedOs = when {
            os.contains("win") -> "windows"
            os.contains("mac") -> "mac"
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> "linux"
            else -> "unknown"
        }
        log.debug("Detected OS: $detectedOs (from: ${System.getProperty("os.name")})")
        return detectedOs
    }

    private fun findAssetForCurrentOs(release: Release, os: String): Asset? {
        log.debug("Finding compatible asset for OS: $os")
        log.debug("Available assets: ${release.assets.joinToString { it.name }}")

        val asset = release.assets.find { asset ->
            when (os) {
                "windows" -> asset.name.endsWith(".msi") || asset.name.endsWith(".exe")
                "mac" -> asset.name.endsWith(".dmg") || asset.name.endsWith(".pkg")
                "linux" -> asset.name.endsWith(".deb") || asset.name.endsWith(".rpm") || asset.name.endsWith(".AppImage")
                else -> false
            }
        }
        if (asset != null) {
            log.debug("Found compatible asset: ${asset.name}")
        } else {
            log.warn("No compatible asset found for OS: $os")
        }
        return asset
    }

    private fun downloadAsset(asset: Asset, progressBar: JProgressBar, canceled: AtomicBoolean): File {
        log.debug("Creating temporary file for download")
        val tempFile = File("cognotik-update-${UUID.randomUUID().toString().split("-").first()}-${asset.name}")
        log.info("Downloading update from ${asset.downloadUrl} to ${tempFile.absolutePath}")

        // Create a connection to get file size
        log.debug("Opening connection to download URL")
        val connection = URL(asset.downloadUrl).openConnection()
        val fileSize = connection.contentLengthLong
        log.debug("File size to download: $fileSize bytes")

        connection.getInputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            var lastLoggedProgress = 0

            // Use output stream to write to file
            log.debug("Starting file download loop")
            tempFile.outputStream().use { output ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Check for cancellation before writing
                    if (canceled.get()) {
                        log.warn("Download canceled by user.")
                        throw IOException("Download canceled by user") // Throw exception to stop
                    }

                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Update progress in UI thread
                    if (fileSize > 0) {
                        val progress = (totalBytesRead * 100 / fileSize).toInt()
                        // Log progress at 10% intervals
                        if (progress / 10 > lastLoggedProgress / 10) {
                            log.debug("Download progress: $progress% ($totalBytesRead/$fileSize bytes)")
                            lastLoggedProgress = progress
                        }

                        SwingUtilities.invokeLater {
                            progressBar.let { bar ->
                                if (bar.isDisplayable) {
                                    bar.value = progress
                                }
                            }
                        }
                    }
                }
            }
        } // Input stream is closed automatically by 'use'
        log.info("Download completed successfully: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
        return tempFile
    }

    private fun loadScriptTemplate(resourcePath: String): String {
        log.debug("Loading script template from resource: {}", resourcePath)
        val inputStream = UpdateManager::class.java.getResourceAsStream(resourcePath)
            ?: throw IOException("Cannot find resource: $resourcePath")
        return inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }


    private fun launchInstallerAndExit(installerFile: File, os: String) {
        log.info("Preparing to launch installer for OS: $os")
        try {
            when (os) {
                "windows" -> {
                    log.debug("Windows update process starting")
                    // For Windows, first uninstall the current version, then install the new one
                    val appName = "Cognotik" // Ensure this matches the installer's application name
                    // Get the product code using wmic
                    log.debug("Getting product code for $appName")
                    // Show confirmation dialog
                    log.debug("Showing confirmation dialog to user")
                    val confirm = JOptionPane.showConfirmDialog(
                        null,
                        "The application will now close and update to the latest version.\n" + "1. The current version will be uninstalled\n" + "2. The new version will be installed\n\n" + "Do you want to continue?",
                        "Update Confirmation",
                        JOptionPane.YES_NO_OPTION
                    )
                    if (confirm != JOptionPane.YES_OPTION) {
                        log.info("User canceled the update process")
                        return
                    }
                    // Create a PowerShell script file to execute the uninstall and install commands
                    log.debug("Creating PowerShell script for update process")
                    val scriptFile = File.createTempFile("cognotik-update-", ".ps1")
                    val installerPath = installerFile.absolutePath
                    // Construct the PowerShell script content carefully
                    val template = loadScriptTemplate("/scripts/update/windows_update.ps1.template")
                    // Substitute the Kotlin variables into the PowerShell script template
                    val finalSrc = template
                        .replace("@@APP_NAME@@", appName)
                        .replace("@@INSTALLER_PATH@@", installerPath)


                    log.debug("Writing to PowerShell script file: ${scriptFile.absolutePath}: \n${finalSrc.indent("  ")}")
                    scriptFile.writeText(finalSrc)

                    // Execute the PowerShell script and exit
                    // Using -NoProfile for faster startup and -ExecutionPolicy Bypass to avoid issues
                    // Start-Process can be used within PowerShell itself for elevation, but we'll launch directly first.
                    // We need to ensure PowerShell can find the script path correctly, especially if it contains spaces.
                    val process = ProcessBuilder(
                        "cmd",
                        "/c", // Run command and terminate
                        "start", // Start a separate window/process
                        "\"Cognotik Update\"", // Title for the new window (optional but good practice)
                        "powershell.exe",
                        "-WindowStyle", "Normal",
                        "-NoExit",
                        "-File", scriptFile.absolutePath
                    ).redirectErrorStream(true).start()
                    log.info("Update PowerShell process started with PID: ${process.pid()}")

                    // Schedule application exit after a short delay
                    CompletableFuture.runAsync {
                        log.info("Scheduling application exit in 1 second")
                        TimeUnit.SECONDS.sleep(1)
                        log.info("Exiting application for update")
                        exitProcess(0)
                    }
                }

                "mac" -> {
                    log.debug("macOS update process starting")

                    // For Mac, we need to uninstall the current version first
                    val appName = "Cognotik"

                    // Create a script to handle the update
                    log.debug("Creating update script for macOS")
                    val scriptFile = File.createTempFile("cognotik-update-", ".sh")
                    scriptFile.setExecutable(true)

                    // Write the update script
                    val installerPath = installerFile.absolutePath
                    val scriptPath = scriptFile.absolutePath

                    val template = loadScriptTemplate("/scripts/update/mac_update.sh.template")
                    val finalSrc = template
                        .replace("@@INSTALLER_PATH@@", installerPath)
                        .replace("@@SCRIPT_PATH@@", scriptPath)
                        .replace("@@APP_NAME@@", appName)

                    log.debug("Writing to macOS update script file: ${scriptFile.absolutePath}: \n${finalSrc.indent("  ")}")
                    scriptFile.writeText(finalSrc)

                    // Show confirmation dialog
                    log.debug("Showing update confirmation to user")
                    JOptionPane.showMessageDialog(
                        null,
                        "The application will now close and update to the latest version.\n" +
                                "You may need to enter your password for the installation process.",
                        "Update Confirmation",
                        JOptionPane.INFORMATION_MESSAGE
                    )

                    log.info("Executing update script: ${scriptFile.absolutePath}")
                    // Execute the update script in a new terminal
                    ProcessBuilder("open", "-a", "Terminal", scriptFile.absolutePath).start()

                    // Schedule application exit
                    CompletableFuture.runAsync {
                        log.info("Scheduling application exit in 2 seconds")
                        TimeUnit.SECONDS.sleep(2)
                        log.info("Exiting application for update")
                        exitProcess(0)
                    }
                }

                "linux" -> {
                    log.debug("Linux update process starting")

                    // For Linux, create a script to handle the update
                    val scriptFile = File.createTempFile("cognotik-update-", ".sh")

                    // Make the script executable
                    log.debug("Setting execute permissions on update script")
                    scriptFile.setExecutable(true)

                    // Write the update script
                    log.debug("Creating update script for installer: ${installerFile.name}")
                    val scriptTemplatePath: String
                    val installerPath = installerFile.absolutePath
                    val scriptPath = scriptFile.absolutePath

                    if (installerFile.name.endsWith(".deb")) {
                        scriptTemplatePath = "/scripts/update/linux_update_deb.sh.template"
                    } else if (installerFile.name.endsWith(".AppImage")) {
                        scriptTemplatePath = "/scripts/update/linux_update_appimage.sh.template"
                    } else {
                        log.error("Unsupported Linux installer type: ${installerFile.name}")
                        throw IOException("Unsupported Linux installer type: ${installerFile.name}")
                    }
                    val template = loadScriptTemplate(scriptTemplatePath)
                    val finalSrc = template
                        .replace("@@INSTALLER_PATH@@", installerPath)
                        .replace("@@SCRIPT_PATH@@", scriptPath)
                    scriptFile.writeText(finalSrc)

                    // Show confirmation dialog
                    log.debug("Showing update confirmation to user")
                    JOptionPane.showMessageDialog(
                        null,
                        "The application will now close and update to the latest version.\n" + "You may need to enter your password for the uninstallation and installation process.",
                        "Update Confirmation",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    log.info("Executing update script: ${scriptFile.absolutePath}")
                    // Execute the update script in a new terminal
                    ProcessBuilder("x-terminal-emulator", "-e", scriptFile.absolutePath).start()

                    // Schedule application exit
                    CompletableFuture.runAsync {
                        log.info("Scheduling application exit in 2 seconds")
                        TimeUnit.SECONDS.sleep(2)
                        log.info("Exiting application for update")
                        exitProcess(0)
                    }
                }

                else -> {
                    log.warn("Unknown OS: $os - attempting to open installer directly")
                    // For unknown OS, just open the file and hope for the best
                    Desktop.getDesktop().open(installerFile)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to launch installer", e)
            log.debug("Stack trace for installer launch failure", e)
            JOptionPane.showMessageDialog(
                null,
                "Failed to launch installer: ${e.message}\n" + "The installer has been downloaded to: ${installerFile.absolutePath}",
                "Installation Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    fun checkUpdate() {
        if (latestVersion.greaterThan(currentVersion)) {
            confirm("Update to ${latestVersion.version}?") {
                Thread {
                    try {
                        doUpdate()
                    } catch (e: Exception) {
                        log.error("Failed to update: ${e.message}", e)
                        JOptionPane.showMessageDialog(
                            null,
                            "Update failed: ${e.message}",
                            "Update Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }.start()
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik` package, reflecting the functionality and architecture described in the provided source files.

### /home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/README.md
```markdown
# Cognotik Desktop Core

This package contains the core logic for the Cognotik desktop application, including the main server entry point, daemon management, system tray integration, and the automated update system.

## Key Components

### [CognotikApps.kt](./CognotikApps.kt)
The primary application class that extends `ApplicationDirectory`. It serves as the central hub for:
- **Web Server**: Initializes a Jetty-based server hosting the Cognotik web interface.
- **Application Registry**: Configures child applications like `/chat` (Basic Chat) and `/taskChat` (Unified Plan/Task Runner).
- **Socket Server**: Listens for commands from the `DaemonClient` (typically on `port + 1`).
- **Platform Setup**: Configures default authentication and authorization providers for local desktop use.
- **System Tray**: Initializes the `SystemTrayManager` for desktop integration.

### [DaemonClient.kt](./DaemonClient.kt)
A lightweight client used to manage the Cognotik background process. It handles:
- **Process Management**: Launches the server as a background daemon if it isn't already running.
- **Command Dispatch**: Sends commands (like opening specific session directories) to the running server via a socket.
- **Lifecycle Control**: Supports stopping the server via the `--stop` flag.
- **Session Management**: Generates unique session directories in `~/.cognotik`.

### [SystemTrayManager.kt](./SystemTrayManager.kt)
Provides OS-level integration via the system tray icon. Features include:
- **Quick Access**: "Open in Browser" menu item and double-click support.
- **Update Notifications**: Displays a menu item when a new version is available via the `UpdateManager`.
- **SVG Icon Support**: Uses Apache Batik to render the application icon from SVG.
- **Notifications**: Displays error and status messages using native tray notifications.

### [UpdateManager.kt](./UpdateManager.kt)
A comprehensive update system that interacts with GitHub Releases. It includes:
- **Version Checking**: Compares the current `jpackage` version against the latest tag on GitHub.
- **OS Detection**: Identifies Windows, macOS, or Linux to select the appropriate installer (`.msi`, `.dmg`, `.deb`, etc.).
- **Download Management**: Downloads updates with a Swing-based progress bar and support for cancellation.
- **Automated Installation**: Generates and executes OS-specific scripts (PowerShell for Windows, Shell for Unix) to uninstall the old version and launch the new installer.

## Usage

### Command Line Arguments

The application can be invoked with several commands:

- **Server Mode**: `cognotik server [--port <port>] [--host <host>] [--public-name <name>]`
  - Starts the web server directly in the foreground.
- **Daemon Mode**: (Default behavior when no command is provided)
  - Checks if a server is running; if not, launches it in the background.
  - Dispatches the current directory or a new session to the running server.
- **Stop Server**: `cognotik --stop`
  - Sends a shutdown command to the running daemon.
- **Help**: `cognotik help`
  - Displays usage information.

### Configuration

- **Default Port**: `12891`
- **Socket Port**: `12892` (Default Port + 1)
- **Home Directory**: `~/.cognotik` (Stores PID files and session data)

## Architecture

1. **Communication**: The `DaemonClient` and `CognotikApps` communicate via a simple socket protocol. This allows multiple CLI invocations to interact with a single persistent server instance.
2. **Update Flow**:
   - `UpdateManager` fetches JSON from the GitHub API.
   - If a newer version is found, the user is prompted via the System Tray.
   - Upon confirmation, the installer is downloaded to a temporary file.
   - A platform-specific script is generated to handle the transition (closing the app and running the installer).
3. **Model Integration**: Uses `AnthropicModels.Claude35Haiku` as the default model for orchestration tasks.

## Dependencies

- **Jetty**: Web server engine.
- **Apache Batik**: SVG transcoding for the system tray icon.
- **Gson**: JSON parsing for GitHub API responses.
- **Kotlin Coroutines/Executors**: Background task management.
```

### Summary of Changes
- **Created README.md**: Documented the core desktop package.
- **Component Breakdown**: Detailed the roles of `CognotikApps`, `DaemonClient`, `SystemTrayManager`, and `UpdateManager`.
- **Usage Guide**: Explained CLI arguments and default port configurations.
- **Architecture Overview**: Described the socket-based client-server model and the update workflow.
- **Technical Details**: Included information on OS-specific update handling and default AI models used.
</details>

                - <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/README.md'>/home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/README.md'>/home/andrew/code/Cognotik/desktop/src/main/kotlin/com/simiacryptus/cognotik/README.md</a> Updated
