package com.simiacryptus.skyenet

import org.slf4j.LoggerFactory
import java.io.*
import java.lang.management.ManagementFactory
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Entry point for the daemon client.
 * This will launch the AppServer as a separate process (daemon) if needed,
 * reconnect if possible, and dispatch commands to the server.
 */
object DaemonClient {
    private const val DEFAULT_PORT = 7683
    private const val DEFAULT_HOST = "localhost"
    private const val PID_FILE = "skyenet_server.pid"
    private const val MAX_PORT_ATTEMPTS = 10
    private const val SOCKET_PORT_OFFSET = 1 // Socket port is main port + this offset

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("DaemonClient starting. PID: ${ManagementFactory.getRuntimeMXBean().name}. Args: ${args.joinToString(" ")}")
        // Check if the first argument is "stop"
        if (args.isNotEmpty() && args[0].equals("--stop", ignoreCase = true)) {
            log.info("Stop command received, attempting to stop the server")
            stopServer()
            exitProcess(0)
        }
        
        
        // Check if the first argument is "server"
        if (args.isNotEmpty() && args[0].equals("server", ignoreCase = true)) {
            log.info("First argument is 'server', delegating to AppServer.main")
            AppServer.main(args) // Delegate to AppServer
        } else {
            // Original DaemonClient logic
            var port = DEFAULT_PORT
            val host = DEFAULT_HOST
            log.debug("Default host: $host, Default port: $port")
            if (!isServerRunning(host, port)) {
                log.info("Server not running. Launching daemon...")
                println("Server not running on $host:$port. Launching daemon...")
                try {
                    // Just test if the port is available - don't keep it open
                    ServerSocket(port).use {
                        log.debug("Port $port is available")
                    }
                } catch (e: IOException) {
                    log.info("Port $port is in use, finding alternative port")
                    println("Port $port is in use, finding alternative port...")
                    port = findAvailablePort(port + 1)
                    log.info("Found available alternative port: $port")
                    println("Using alternative port: $port")
                }
                launchDaemon(port)
                waitForServer(host, port)
            } else {
                log.info("Server already running on $host:$port.")
                println("Server already running on $host:$port.")
            }
            if (args.isNotEmpty()) {
                log.info("Dispatching command: ${args.joinToString(" ")}")
                dispatchCommand(host, port + SOCKET_PORT_OFFSET, args)
            } else {
                log.warn("No command specified. Use: daemonclient <command> [args]")
                println("No command specified. Use: daemonclient <command> [args]")
            }
        }
    }
    /**
     * Stops the running server by sending a shutdown command or killing the process
     */
    private fun stopServer() {
        val host = DEFAULT_HOST
        val port = DEFAULT_PORT
        // First try to send a shutdown command via socket
        if (isServerRunning(host, port)) {
            try {
                log.info("Sending shutdown command to server at $host:${port + SOCKET_PORT_OFFSET}")
                println("Sending shutdown command to server...")
                Socket(host, port + SOCKET_PORT_OFFSET).use { socket ->
                    val out = PrintWriter(socket.getOutputStream(), true)
                    val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                    out.println("shutdown")
                    val response = input.readLine()
                    if (response != null) {
                        log.info("Server response to shutdown: $response")
                        println("Server response: $response")
                    }
                }
                // Wait for server to shut down
                var attempts = 0
                while (isServerRunning(host, port) && attempts < 10) {
                    log.info("Waiting for server to shut down...")
                    println("Waiting for server to shut down...")
                    Thread.sleep(500)
                    attempts++
                }
                if (!isServerRunning(host, port)) {
                    log.info("Server successfully stopped")
                    println("Server successfully stopped")
                    return
                }
            } catch (e: Exception) {
                log.warn("Failed to stop server via socket: ${e.message}", e)
                println("Failed to stop server via socket: ${e.message}")
            }
        }
        // If socket method failed or server still running, try to kill the process using PID file
        try {
            val pidFile = File(PID_FILE)
            if (pidFile.exists()) {
                val pid = pidFile.readText().trim().toLong()
                log.info("Attempting to kill server process with PID: $pid")
                println("Attempting to kill server process with PID: $pid")
                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                val processBuilder = if (isWindows) {
                    ProcessBuilder("taskkill", "/F", "/PID", pid.toString())
                } else {
                    ProcessBuilder("kill", "-9", pid.toString())
                }
                val process = processBuilder.start()
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    log.info("Server process killed successfully")
                    println("Server process killed successfully")
                    pidFile.delete()
                } else {
                    log.warn("Failed to kill server process, exit code: $exitCode")
                    println("Failed to kill server process, exit code: $exitCode")
                }
            } else {
                log.warn("PID file not found: $PID_FILE")
                println("PID file not found. Server may not be running or was started without creating a PID file.")
            }
        } catch (e: Exception) {
            log.error("Error stopping server: ${e.message}", e)
            println("Error stopping server: ${e.message}")
        }
    }
    
    
    private fun findAvailablePort(startPort: Int): Int {
        var port = startPort
        log.debug("Searching for available port starting from $startPort")
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
        val randomPort = ServerSocket(0).use { it.localPort }
        log.info("Using random port: $randomPort")
        return randomPort
    }


    private fun isServerRunning(host: String, port: Int): Boolean {
        return try {
            log.debug("Checking if server is running at $host:$port")
            Socket(host, port).use { true }
            log.debug("Connection successful to $host:$port. Server is running.")
            true
        } catch (e: ConnectException) {
            // This is the expected case when the server is not running
            log.debug("Server is not running at $host:$port: ${e.message}")
            false
        } catch (e: Exception) {
            log.warn("Unexpected error while checking server status: ${e.message}", e)
            false
        }
    }

    private fun waitForServer(host: String, port: Int, timeoutMs: Long = 10000L) {
        val start = System.currentTimeMillis()
        log.info("Waiting for server to start at $host:$port (timeout=${timeoutMs}ms)")
        while (!isServerRunning(host, port)) {
            log.debug("Server not yet available at $host:$port. Waiting...")
            if (System.currentTimeMillis() - start > timeoutMs) {
                log.error("Timed out waiting for server to start at $host:$port")
                throw RuntimeException("Timed out waiting for server to start")
            }
            Thread.sleep(200)
        }
        // isServerRunning logs success internally now
        log.info("Server is now running at $host:$port")
        println("Server is now running.")
    }

    private fun launchDaemon(port: Int) {
        // Get the current JVM executable path
        val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        val classpath = System.getProperty("java.class.path")
        val className = "com.simiacryptus.skyenet.AppServer"
        log.debug("Java executable: $javaBin")
        log.debug("Classpath: $classpath")
        log.debug("Server class: $className")
        
        // Create a temporary script file to launch the daemon
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val scriptExt = if (isWindows) "bat" else "sh"
        val scriptFile = File.createTempFile("skyenet_daemon_", ".$scriptExt")
        //scriptFile.deleteOnExit()
        
        if (isWindows) {
            log.debug("Detected Windows OS.")
            // Windows batch file
            scriptFile.writeText(
                """
                @echo log.info("Daemon process launched. Waiting for it to start...")
                start /b /min "" "$javaBin" -cp "$classpath" $className server --port $port
                exit
            """.trimIndent()
            )
        } else {
            log.debug("Detected non-Windows OS (assuming Unix-like).")
            // Unix shell script
            scriptFile.writeText(
                """
                #!/bin/sh
                nohup /opt/skyenetapps/bin/SkyenetApps server --port $port &
                exit 0
            """.trimIndent()
            )
            scriptFile.setExecutable(true)
        }
        
        log.info("Created daemon launcher script: ${scriptFile.absolutePath}")
        log.debug("Script content:\n${scriptFile.readText()}")
        
        // Build the process to run the script
        val processBuilder = if (isWindows) {
            log.debug("Using ProcessBuilder: cmd /c ${scriptFile.absolutePath}")
            ProcessBuilder("cmd", "/c", scriptFile.absolutePath)
        } else {
            log.debug("Using ProcessBuilder: sh ${scriptFile.absolutePath}")
            ProcessBuilder("sh", scriptFile.absolutePath)
        }
        // Ensure the process doesn't inherit IO streams from parent
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
        
        val process = try {
            log.info("Launching daemon process using script: ${processBuilder.command().joinToString(" ")}")
            processBuilder.start()
        } catch (e: Exception) {
            log.error("Failed to launch daemon process: ${e.message}", e)
            throw e
        }
        
        // Check if the daemon is running by writing the PID file
        try {
            writePidFile(process)
        } catch (e: Exception) {
            log.error("Failed to write PID file: ${e.message}", e)
            println("Failed to write PID file: ${e.message}")
        }
        
        // Wait for 5 seconds while relaying output, then exit
        Thread.sleep(5000)
        log.info("Daemon launched successfully, waiting for server to be ready...")
    }

    private fun writePidFile(process: Process) {
        try {
            val pid = process.pid()
            Files.write(Paths.get(PID_FILE), pid.toString().toByteArray())
            log.info("Wrote PID file: $PID_FILE with PID $pid")
        } catch (e: Exception) {
            log.warn("Could not write PID file: ${e.message}", e)
            println("Warning: Could not write PID file: ${e.message}")
        }
    }
    
    private fun dispatchCommand(host: String, port: Int, args: Array<String>) {
        try {
            log.debug("Attempting to connect to server at $host:$port to dispatch command: \"${args.joinToString(" ")}\"")
            Socket(host, port).use { socket ->
                log.info("Connected to server at $host:$port")
                val out = PrintWriter(socket.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                out.println(args.joinToString(" "))
                log.info("Sent command: ${args.joinToString(" ") { "`$it`" }}")
                // Read response (if any)
                log.debug("Waiting for server response...")
                val response = input.readLine()
                if (response != null) {
                    log.info("Received server response: $response")
                    println("Server response: $response")
                    log.debug("Closing connection to $host:$port")
                } else {
                    log.warn("No response received from server.")
                    println("No response received from server.")
                }
            }
        } catch (e: Exception) {
            log.error("Failed to dispatch command: ${e.message}", e)
            println("Failed to dispatch command: ${e.message}")
        }
    }

    val log = LoggerFactory.getLogger(DaemonClient::class.java)
}