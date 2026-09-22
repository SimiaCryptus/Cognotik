package com.simiacryptus.cognotik.webui.servlet

import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.io.File

/**
 * Minimal foreground file server, and the reference example of a **permissive
 * local mount**: interactive terminals and unrestricted `child_process` are on
 * by default, because the process already runs with the invoking user's rights
 * and binds to loopback. Pass the lockdown flags to harden it.
 *
 * Usage:
 *   FileServerCli [options] [directory]
 *
 * Options:
 *   -p, --port <n>     Port to listen on (default 8081, 0 = random free port)
 *   -h, --host <addr>  Interface to bind (default 127.0.0.1, use 0.0.0.0 for all)
 *       --no-git       Disable the Git UI/API features
 *       --read-only    Disable POST/PUT/DELETE (uploads, edits, deletes)
 *       --no-terminal  Disable /.fsapi/v1/terminal sessions
 *       --no-exec      Restrict /.fsapi/v1/exec to read-mostly git sub-commands
 *       --secure       --read-only --no-terminal --no-exec
 *       --shell <cmd>  Shell used for new terminals (default: auto-detect)
 *       --help         Print this help
 *
 * Runs until interrupted (Ctrl-C).
 */
object FileServerCli {

  /** First path segment consumed by [FileServlet] (normally a session id). */
  private const val ROOT_SEGMENT = "root"
  private const val FILES_PREFIX = "/files"
  const val UI_PREFIX = "/ui"

  /**
   * Shared web assets served straight from the classpath, independent of the served
   * directory and of every feature flag: `web/lib` is published at [LIB_PREFIX] and
   * `web/app` at [APP_PREFIX].
   */
  const val LIB_PREFIX = "/lib"

  /** @see LIB_PREFIX */
  const val APP_PREFIX = "/app"

  /** Sends browsers landing on "/" (or "/files") to the served directory listing. */
  class RootRedirectServlet(private val target: String = "$FILES_PREFIX/$ROOT_SEGMENT/") : HttpServlet() {
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
      response.sendRedirect("${request.contextPath}$target")
    }
  }

  /** Rejects mutating requests when --read-only is used. */
  class ReadOnlyFileServlet(
    baseDir: File,
    gitEnabled: Boolean,
    uiEnabled: Boolean = true,
    execPermissive: Boolean = false,
  ) : SimpleFileServlet(
    baseDir, gitEnabled, readOnly = true, uiEnabled = uiEnabled,
    terminalEnabled = false, execPermissive = execPermissive,
  ) {
    private fun deny(response: HttpServletResponse) {
      response.status = HttpServletResponse.SC_FORBIDDEN
      response.contentType = "text/plain"
      response.writer.write("Server is running in read-only mode")
    }

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) = deny(response)
    override fun doPut(request: HttpServletRequest, response: HttpServletResponse) = deny(response)
    override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) = deny(response)
  }

  private fun usage(): String = """
              Usage: FileServerCli [options] [directory]

                -p, --port <n>     Port to listen on (default 8081, 0 = random free port)
                -h, --host <addr>  Interface to bind (default 127.0.0.1, 0.0.0.0 for all)
                    --no-git       Disable Git UI/API features
                    --read-only    Disable uploads, edits and deletes
                    --no-terminal  Disable interactive terminal sessions
                    --no-exec      Restrict /exec to read-mostly git sub-commands
                    --secure       Shorthand for --read-only --no-terminal --no-exec
                    --shell <cmd>  Shell for new terminals (default: auto-detect)
                    --ui           Make the IDE-style SPA (/ui/) the landing page
                    --no-ui        Do not serve the SPA at all
                    --help         Show this message

              By default this is a PERMISSIVE LOCAL server: interactive terminals and
              unrestricted child processes are enabled and it binds to 127.0.0.1 only.
              Use --secure (and/or the individual flags) before exposing it.

              The server runs in the foreground; press Ctrl-C to stop it.
          """.trimIndent()

  @JvmStatic
  fun main(args: Array<String>) {
    var port = 8081
    var host = "127.0.0.1"
    var gitEnabled = true
    var readOnly = false
    var uiEnabled = true
    var uiDefault = false
    var terminalEnabled = true
    var execPermissive = true
    var shell: List<String> = emptyList()
    var dirArg: String? = null

    var i = 0
    while (i < args.size) {
      when (val arg = args[i]) {
        "-p", "--port" -> {
          port = args.getOrNull(++i)?.toIntOrNull()
            ?: fail("Missing or invalid value for $arg")
        }

        "-h", "--host" -> {
          host = args.getOrNull(++i) ?: fail("Missing value for $arg")
        }

        "--shell" -> {
          val value = args.getOrNull(++i) ?: fail("Missing value for $arg")
          shell = value.trim().split(" ").filter { it.isNotBlank() }
        }

        "--no-git" -> gitEnabled = false
        "--read-only" -> readOnly = true
        "--no-terminal" -> terminalEnabled = false
        "--no-exec" -> execPermissive = false
        "--secure" -> {
          readOnly = true
          terminalEnabled = false
          execPermissive = false
        }

        "--no-ui" -> uiEnabled = false
        "--ui" -> uiDefault = true
        "--help" -> {
          println(usage())
          return
        }

        else -> {
          if (arg.startsWith("-")) fail("Unknown option: $arg")
          if (dirArg != null) fail("Only one directory may be specified")
          dirArg = arg
        }
      }
      i++
    }

    val baseDir = File(dirArg ?: ".").canonicalFile
    if (!baseDir.exists() || !baseDir.isDirectory) {
      fail("Not a directory: ${baseDir.absolutePath}")
    }

    val server = start(
      baseDir, host, port, gitEnabled, readOnly, uiEnabled, uiDefault,
      terminalEnabled, execPermissive, shell
    )
    val boundPort = (server.connectors.first() as ServerConnector).localPort
    val displayHost = if (host == "0.0.0.0" || host == "::") "localhost" else host

    println("Serving ${baseDir.absolutePath}")
    println("  ->  http://$displayHost:$boundPort/")
    if (uiEnabled) println("  IDE view  -> http://$displayHost:$boundPort$UI_PREFIX/")
    println("  Classic   -> http://$displayHost:$boundPort$FILES_PREFIX/$ROOT_SEGMENT/")
    println("  Assets    -> http://$displayHost:$boundPort$LIB_PREFIX/ (classpath web/lib), $APP_PREFIX/ (classpath web/app)")
    println("  FS API v1 -> http://$displayHost:$boundPort$FILES_PREFIX/$ROOT_SEGMENT/.fsapi/v1/meta")
    println(
      "  Mode      -> ${if (readOnly) "read-only" else "read-write"}" +
          ", terminal ${if (terminalEnabled && !readOnly) "enabled" else "disabled"}" +
          ", exec ${if (execPermissive) "unrestricted" else "allowlisted"}"
    )
    if (!readOnly && (terminalEnabled || execPermissive) && host != "127.0.0.1" && host != "localhost") {
      println("  WARNING: arbitrary code execution is enabled on a non-loopback interface (see --secure)")
    }
    println("Press Ctrl-C to stop.")

    Runtime.getRuntime().addShutdownHook(Thread {
      println("\nShutting down...")
      try {
        server.stop()
      } catch (e: Exception) {
        // best effort
      }
    })

    /* Blocks until the server is stopped (i.e. by the shutdown hook on Ctrl-C). */
    server.join()
  }

  /**
   * Starts an embedded server for [baseDir]. Exposed for tests/embedding;
   * the caller owns stopping the returned [Server].
   */
  fun start(
    baseDir: File,
    host: String = "127.0.0.1",
    port: Int = 8081,
    gitEnabled: Boolean = true,
    readOnly: Boolean = false,
    uiEnabled: Boolean = true,
    uiDefault: Boolean = false,
    terminalEnabled: Boolean = true,
    execPermissive: Boolean = true,
    shell: List<String> = emptyList(),
  ): Server {
    val server = Server()
    val connector = ServerConnector(server).apply {
      this.host = host
      this.port = port
    }
    server.addConnector(connector)

    val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS).apply {
      contextPath = "/"
      resourceBase = baseDir.absolutePath
    }

    val fileServlet = if (readOnly) ReadOnlyFileServlet(baseDir, gitEnabled, uiEnabled, execPermissive)
    else SimpleFileServlet(
      baseDir, gitEnabled, readOnly = false, uiEnabled = uiEnabled,
      terminalEnabled = terminalEnabled, execPermissive = execPermissive, shell = shell
    )
    val fileHolder = ServletHolder("files", fileServlet)
    /* @MultipartConfig is not honoured for programmatically registered instances. */
    fileHolder.registration.setMultipartConfig(
      MultipartConfigElement(
        System.getProperty("java.io.tmpdir"),
        1024L * 1024 * 50,
        1024L * 1024 * 100,
        1024 * 1024 * 2
      )
    )
    context.addServlet(fileHolder, "$FILES_PREFIX/*")

    /* ZIP downloads: session = directory name, resolved against the parent dir. */
    context.addServlet(
      ServletHolder("zip", StaticZipServlet(baseDir.parentFile?.absolutePath ?: baseDir.absolutePath)),
      "/zip"
    )

    if (uiEnabled) {
      context.addServlet(ServletHolder("webui", WebUiServlet()), "$UI_PREFIX/*")
    }
    /*
     * Shared classpath assets, always mounted: /lib -> web/lib, /app -> web/app.
     * Read from the classpath (never from the workspace), so they stay available on
     * read-only, --no-ui and --secure mounts alike.
     */
    register(context, ServletHolder("web-lib", WebUiServlet("web/lib")), LIB_PREFIX)
    register(context, ServletHolder("web-app", WebUiServlet("web/app")), APP_PREFIX)


    val landing = if (uiEnabled && uiDefault) "$UI_PREFIX/" else "$FILES_PREFIX/$ROOT_SEGMENT/"
    val redirect = ServletHolder("redirect", RootRedirectServlet(landing))
    context.addServlet(redirect, "")
    context.addServlet(redirect, FILES_PREFIX)

    server.handler = context
    server.stopAtShutdown = true
    server.start()
    return server
  }

  private fun fail(message: String): Nothing {
    System.err.println("error: $message")
    System.err.println()
    System.err.println(usage())
    kotlin.system.exitProcess(2)
  }

  private fun register(
    context: ServletContextHandler,
    homeHolder: ServletHolder,
    prefix: String
  ) {
    context.addServlet(homeHolder, "$prefix/*")
    context.addServlet(homeHolder, prefix)
  }
}