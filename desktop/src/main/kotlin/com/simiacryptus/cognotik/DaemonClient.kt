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
    private const val DEFAULT_PORT = 7683
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
            AppServer.main(args)
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
                start /b /min "" "${if (appPath.isNotEmpty()) appPath else "C:/Program Files/Cognotik/Cognotik.exe"}" server --port $port
                exit
            """.trimIndent()
            )
        } else if (isMac) {
            scriptFile.writeText(
                """
               #!/bin/sh
               # Use caffeinate to prevent sleep and run in background without UI
               nohup /usr/bin/caffeinate -i "${if (appPath.isNotEmpty()) appPath else "/Applications/Cognotik.app/Contents/MacOS/Cognotik"}" server --port $port >/dev/null 2>&1 &
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
                nohup ${if (appPath.isNotEmpty()) appPath else "/opt/cognotik/bin/Cognotik"} server --port $port &
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