package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.webui.servlet.handler.FsApiConfig

import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
  private const val UI_PREFIX = "/ui"

  open class SimpleFileServlet(
    private val baseDir: File,
    private val gitEnabled: Boolean,
    private val readOnly: Boolean = false,
    private val uiEnabled: Boolean = true,
    private val terminalEnabled: Boolean = true,
    /** true = any bare command may be spawned; false = allowlisted git only. */
    private val execPermissive: Boolean = true,
    private val maxTerminals: Int = 8,
    private val shell: List<String> = emptyList(),
  ) : FilesystemServlet() {
    override fun getDir(request: HttpServletRequest, response: HttpServletResponse): File = baseDir
    override fun isGitEnabled(req: HttpServletRequest): Boolean = gitEnabled

    /**
     * The FS API is dispatched from service() and therefore bypasses the
     * doPost/doPut/doDelete overrides below; every capability (read-only mode,
     * exec, terminal) must be declared here.
     *
     * This is the *permissive* profile: it is what makes `POST /.fsapi/v1/terminal`
     * and the IDE view's terminal panel work. Tighten it for anything reachable
     * from a network.
     */
    override fun getFsApiConfig(req: HttpServletRequest) = FsApiConfig(
      readOnly = readOnly,
      /* Sub-command allowlist only matters in the hardened profile. */
      execAllowlist = if (!gitEnabled) emptyMap()
      else mapOf("git" to if (execPermissive) emptySet() else GIT_SUBCOMMANDS),
      execAllowAny = execPermissive,
      execRestrictArguments = !execPermissive,
      /* A terminal is a write operation: read-only mounts never get one. */
      terminalEnabled = terminalEnabled && !readOnly,
      maxTerminals = maxTerminals,
      terminalShell = shell,
    )

    override fun getZipLink(req: HttpServletRequest, filePath: String): String {
      val session = URLEncoder.encode(baseDir.name, StandardCharsets.UTF_8)
      val path = URLEncoder.encode(if (filePath.isBlank()) "/" else filePath, StandardCharsets.UTF_8)
      return "${req.contextPath}/zip?session=$session&path=$path"
    }

    /** docs/ui.md §21.3 — the classic listing links to the equivalent SPA path. */
    override fun getToolbarActions(req: HttpServletRequest, currentPath: String): String {
      if (!uiEnabled) return ""
      val hash = if (currentPath.isBlank()) "/" else "/$currentPath/"
      return """<a class="zip-link" style="background-color:#6f42c1;" href="${req.contextPath}$UI_PREFIX/#$hash">🧭 Open in IDE view</a>"""
    }
  }

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
}