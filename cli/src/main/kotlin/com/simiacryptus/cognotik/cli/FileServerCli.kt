package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.cli.CliSupport.availableModels
import com.simiacryptus.cognotik.cli.CliSupport.bootstrapPlatform
import com.simiacryptus.cognotik.cli.CliSupport.email
import com.simiacryptus.cognotik.cli.CliSupport.fail
import com.simiacryptus.cognotik.cli.SimpleFileServlet.Companion.FILES_PREFIX
import com.simiacryptus.cognotik.cli.SimpleFileServlet.Companion.ROOT_SEGMENT
import com.simiacryptus.cognotik.cli.SimpleFileServlet.Companion.UI_PREFIX
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.servlet.StaticZipServlet
import com.simiacryptus.cognotik.webui.servlet.WebUiServlet
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.servlet.ApiKeyServlet
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet
import com.simiacryptus.cognotik.webui.servlet.UserSettingsServlet

import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Minimal foreground file server, and the reference example of a **permissive
 * local mount**: interactive terminals and unrestricted `child_process` are on
 * by default, because the process already runs with the invoking user's rights
 * and binds to loopback. Pass the lockdown flags to harden it.
 *
 * On top of the plain mount it also wires in the two agentic CLIs of this module
 * as FS API operations (see [ServerTaskActions]):
 *
 * ```
 * POST {mount}/.fsapi/v1/docops?command=plan
 * POST {mount}/.fsapi/v1/docops?command=run&path=docs/api.md
 * POST {mount}/.fsapi/v1/autofix?cmd=./gradlew%20build
 * POST {mount}/.fsapi/v1/modify?path=src/Foo.kt
 * GET  {mount}/.fsapi/v1/tasks[?id=t1]
 * ```
 *
 * The classic directory listing grows matching affordances: per-document
 * *Plan* / *Run* links for markdown files, a per-file *Modify* link (the port of
 * the IDE's `ModifyFilesAction`), an *AutoFix…* toolbar button, and a live output
 * panel that polls the task endpoint.
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
 *       --secure       --read-only --no-terminal --no-exec --no-tasks
 *       --shell <cmd>  Shell used for new terminals (default: auto-detect)
 *       --help         Print this help
 *
 * Runs until interrupted (Ctrl-C).
 */
object FileServerCli {

  var user: User = CliSupport.defaultUser()
  var available: Map<String, ChatModel> = emptyMap()
  var models: CliSupport.Models? = null

  /**
   * The DocOps servlet installed by [ServerTaskActions.install]. It is mounted at
   * [DOCOPS_PREFIX] and is the same instance the `.fsapi/v1/docops` action drives,
   * so there is a single DocOps implementation in the server.
   */
  @Volatile
  var docProcessorServlet: CliDocProcessorServlet? = null

  /** Mount point of [docProcessorServlet]. */
  const val DOCOPS_PREFIX = "/docops"

  /** Mount point of the resource-based homepage ([StaticResourceServlet]). */
  const val HOME_PREFIX = "/home"

  const val LIB_PREFIX = "/lib"

  /** @see LIB_PREFIX */
  const val APP_PREFIX = "/app"

  data class ServerInfo(
    val servedDir: String = "",
    val host: String = "",
    val port: Int = 0,
    val gitEnabled: Boolean = false,
    val readOnly: Boolean = false,
    val uiEnabled: Boolean = false,
    val homeEnabled: Boolean = false,
    val terminalEnabled: Boolean = false,
    val execPermissive: Boolean = false,
    val tasksEnabled: Boolean = false,
    val modifyEnabled: Boolean = false,
    /** Where the gateway page ('/') sends the browser, e.g. `/home/`. */
    val landingPath: String = "",
  )

  @Volatile
  var serverInfo: ServerInfo = ServerInfo()

  const val PROXY_PREFIX = "/proxy"


  /** Contexts that already carry the session-proxy servlets (weakly held). */
  private val proxiedContexts: MutableSet<ServletContextHandler> =
    java.util.Collections.synchronizedSet(
      java.util.Collections.newSetFromMap(java.util.WeakHashMap<ServletContextHandler, Boolean>())
    )

  /** Contexts that already had websocket support installed (weakly held). */
  private val webSocketContexts: MutableSet<ServletContextHandler> =
    java.util.Collections.synchronizedSet(
      java.util.Collections.newSetFromMap(java.util.WeakHashMap<ServletContextHandler, Boolean>())
    )

  /**
   * Embedded Jetty does not run `ServletContainerInitializer`s, so a plain
   * [ServletContextHandler] has no `WebSocketComponents`. Any `JettyWebSocketServlet`
   * (the session proxy installs one) then fails its `init()` with
   * `IllegalStateException: WebSocketComponents has not been created` while the server
   * is starting. Running the initializer here is the embedded equivalent of the
   * container's automatic discovery; it must happen *before* the context starts and is
   * idempotent per context.
   *
   * @return true when the context can host websocket servlets.
   */
  fun ensureWebSocketSupport(context: ServletContextHandler): Boolean {
    if (!webSocketContexts.add(context)) return true
    return try {
      JettyWebSocketServletContainerInitializer.configure(context, null)
      true
    } catch (e: Throwable) {
      webSocketContexts.remove(context)
      log.warn("Could not enable websocket support on ${context.contextPath}: ${e.message}")
      System.err.println("warning: could not enable websocket support: ${e.message}")
      false
    }
  }

  /** Servlets registered after start-up must be started/initialised explicitly. */
  private fun startNewHolders(context: ServletContextHandler) {
    if (!context.isStarted) return
    context.servletHandler.servlets.forEach { holder ->
      if (!holder.isStarted) runCatching { holder.start() }
    }
    runCatching { context.servletHandler.initialize() }
  }

  /**
   * Gateway for the context root. `/` is not a mount of its own: it is an alias for the
   * landing page ([ServerInfo.landingPath], the homepage by default), because the
   * homepage is the only page that explains the mount and lets the user pick models.
   *
   * `/?session=ID` is sent to [PROXY_PREFIX] instead, so the two documented spellings
   * of a session URL ('/' and '/proxy/') keep behaving identically.
   *
   * It is mapped both on the exact root spec (`""`) and on the default spec (`"/"`), so
   * it replaces Jetty's `Default404Servlet`; every other mount is a more specific spec
   * and therefore still wins. Paths other than the root are answered with 404 - the
   * gateway is an alias for '/', not a wildcard redirect.
   */
  private class RootGatewayServlet : HttpServlet() {
    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
      val path = req.requestURI.removePrefix(req.contextPath).ifEmpty { "/" }
      val query = req.queryString?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
      if (!req.getParameter("session").isNullOrBlank()) {
        resp.sendRedirect("$PROXY_PREFIX/$query")
        return
      }
      if (path != "/") {
        /* Reached via the default mapping: nothing else claimed this path. */
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No such resource: $path")
        return
      }
      /* Never redirect to '/' itself: that would loop through this very servlet. */
      val target = serverInfo.landingPath.takeIf { it.isNotBlank() && it != "/" } ?: "$HOME_PREFIX/"
      resp.setHeader("Cache-Control", "no-store")
      resp.sendRedirect(target + query)
    }
  }


  private fun usage(): String = """
                Usage: FileServerCli [options] [directory]

                  -p, --port <n>     Port to listen on (default 8081, 0 = random free port)
                  -h, --host <addr>  Interface to bind (default 127.0.0.1, 0.0.0.0 for all)
                     --email <addr> Login email for the local CLI user (default: anonymous)
                      --no-git       Disable Git UI/API features
                      --read-only    Disable uploads, edits and deletes
                      --no-terminal  Disable interactive terminal sessions
                       --secure       Shorthand for --read-only --no-terminal --no-exec --no-tasks --no-modify
                      --shell <cmd>  Shell for new terminals (default: auto-detect)
                      --no-ui        Do not serve the SPA at all
                      --files        Make the classic listing the landing page
                     ('/' is only a gateway: it never serves the workspace, it redirects
                      to the landing page - /home/ by default - and '/?session=ID' is
                      forwarded to /proxy/.)
                      --help         Show this message

                Task actions (DocOps / AutoFix), enabled by default:
                      --no-tasks         Do not expose the docops/autofix/tasks operations
                      --task-root <dir>  Project root handed to the tools (default: served dir)
                      --smart-model <id> Primary model (or COGNOTIK_SMART_MODEL)
                      --fast-model <id>  Secondary model (or COGNOTIK_FAST_MODEL)
                      --task-timeout <m> AutoFix timeout in minutes (default 30)
                      --task-monitor     Let the tools start their own ephemeral monitor server
                      --fix-cmd <cmd>    Command pre-filled in the AutoFix prompt

                  POST {mount}/.fsapi/v1/docops?command=plan|run|status|vars|models[&path=...]
                  POST {mount}/.fsapi/v1/autofix?cmd=<command>[&dir=<subdir>]
                  GET  {mount}/.fsapi/v1/tasks[?id=<taskId>]
                   The same DocOps engine is also mounted directly as a servlet:
                   POST /docops?doc=<file>[&target=<file>][&mode=<mode>][&var.NAME=VALUE]
                   GET  /docops?doc=<file>&listTemplateVars=true

                  'docops run' and 'autofix' mutate the workspace, run in the background and
                  return a task id; everything else answers inline. Both are refused with
                  EROFS on a read-only mount.
                Model selection (start-up flags are only the initial value):
                  GET  {mount}/.fsapi/v1/models[?refresh=true]
                  POST {mount}/.fsapi/v1/models?smart=<id>&fast=<id>
                   The IDE view's Tools menu gains "🧠 Select Models…" (two dropdowns filled
                   live from the models your API keys expose) and the classic listing gains a
                   "🧠 Models…" button. An omitted parameter is left unchanged; the choice
                   applies immediately to DocOps, AutoFix and the patch chat.

                 Patch chat (port of the IDE's ModifyFilesAction), enabled by default:
                       --no-modify        Do not expose the modify operation
                       --line-numbers     Number the code summary given to the model
                       --chat-port <n>    Port for the chat UI (default 0 = random, started on demand)
                   POST {mount}/.fsapi/v1/modify?path=src/Foo.kt[&path=...][&lineNumbers=true]
                     -> { "session": "...", "url": "http://host:port/#<session>", "files": [...] }
                   Omit 'path' to select the whole served tree. Folders are expanded; the
                   selection is embedded in the chat's system prompt and the model's patches
                   are applied to the workspace, so it is refused with EROFS when read-only.
                   Session URLs are served both from the context root and from /proxy/,
                   i.e. http://host:port/?session=ID and http://host:port/proxy/?session=ID
                   are equivalent (on this server and on the chat server).


                By default this is a PERMISSIVE LOCAL server: interactive terminals and
                unrestricted child processes are enabled and it binds to 127.0.0.1 only.
                Use --secure (and/or the individual flags) before exposing it.

                The server runs in the foreground; press Ctrl-C to stop it.
            """.trimIndent()

  @JvmStatic
  fun main(args: Array<String>) {
    CliSupport.installFileServices()

    var port = 8081
    var host = "127.0.0.1"
    var gitEnabled = true
    var readOnly = false
    var uiEnabled = true
    var homeEnabled = true
    var terminalEnabled = true
    var execPermissive = true
    var shell: List<String> = emptyList()
    var dirArg: String? = null
    var tasksEnabled = true
    var taskRootArg: String? = null
    var smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL")
    var fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL")
    val imageModel: String? = System.getenv("COGNOTIK_IMAGE_MODEL")
    val audioModel: String? = System.getenv("COGNOTIK_AUDIO_MODEL")
    var taskTimeout = 30L
    var taskMonitor = false
    var fixCommand = ""
    var modifyEnabled = true
    var lineNumbers = false
    var chatPort = 8061
    /* null = "whatever is enabled", see landingPathFor(). */
    var landing: String? = null

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
          tasksEnabled = false
          modifyEnabled = false
        }

        "--no-tasks" -> tasksEnabled = false
        "--tasks" -> tasksEnabled = true
        "--no-modify" -> modifyEnabled = false
        "--modify" -> modifyEnabled = true
        "--line-numbers" -> lineNumbers = true
        "--chat-port" -> chatPort = args.getOrNull(++i)?.toIntOrNull()
          ?: fail("Missing or invalid value for $arg")

        "--email" -> email = args.getOrNull(++i) ?: fail("Missing value for $arg")
        "--task-root" -> taskRootArg = args.getOrNull(++i) ?: fail("Missing value for $arg")
        "--smart-model" -> smartModel = args.getOrNull(++i) ?: smartModel ?: fail("Missing value for $arg")
        "--fast-model" -> fastModel = args.getOrNull(++i) ?: fastModel ?: fail("Missing value for $arg")
        "--task-timeout" -> taskTimeout = args.getOrNull(++i)?.toLongOrNull()
          ?: fail("Missing or invalid value for $arg")

        "--task-monitor" -> taskMonitor = true
        "--fix-cmd" -> fixCommand = args.getOrNull(++i) ?: fail("Missing value for $arg")

        "--no-ui" -> uiEnabled = false
        "--ui" -> {
          uiEnabled = true
          landing = "ui"
        }

        "--no-home" -> homeEnabled = false
        "--home" -> {
          homeEnabled = true
          landing = "home"
        }

        "--files" -> landing = "files"
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


    /* Built after parsing so --email is honoured, then published for the FS actions. */
    val cliUser = CliSupport.defaultUser()
    bootstrapPlatform(cliUser)
    user = cliUser


    available = availableModels(cliUser)
    /* The pair is runtime state now: the web UI may replace it at any time. */
    ModelSelection.install(user = { user }, smart = smartModel, fast = fastModel)
    ModelSelectionActions.install()
    models = try {
      CliSupport.resolveModels(
        user = cliUser,
        smartModel = ModelSelection.smart,
        fastModel = ModelSelection.fast,
        imageModel = imageModel,
        audioModel = audioModel,
      )
    } catch (e: Exception) {
      /* Starting without a model is no longer fatal - pick one from the web UI. */
      System.err.println("warning: ${e.message}")
      null
    }

    val baseDir = File(dirArg ?: ".").canonicalFile
    if (!baseDir.exists() || !baseDir.isDirectory) {
      fail("Not a directory: ${baseDir.absolutePath}")
    }
    val taskRoot = (taskRootArg?.let { File(it) } ?: baseDir).canonicalFile
    if (readOnly) modifyEnabled = false
    if ((tasksEnabled || modifyEnabled) && !taskRoot.isDirectory) {
      fail("Task root is not a directory: ${taskRoot.absolutePath}")
    }

    /*
     * Registering the actions is cheap and side-effect free; the platform bootstrap
     * (providers, models, auth) happens lazily inside the first invocation, so a
     * missing API key never prevents the file server from starting.
     */
    if (tasksEnabled) {
      ServerTaskActions.install(
        ServerTaskActions.Config(
          root = taskRoot,
          readOnly = readOnly,
          smartModel = smartModel,
          fastModel = fastModel,
          timeoutMinutes = taskTimeout,
          monitor = taskMonitor,
        )
      )
    }
    /*
     * Same contract for the patch chat: registering is free, and the chat UI server
     * (CognotikAppServer) is only started by the first successful modify request.
     */
    if (modifyEnabled) {
      val appServer = CognotikAppServer.getServer(host, chatPort)
      ModifyFilesActions.install(
        ModifyFilesActions.Config(
          root = taskRoot,
          chatUri = {
            appServer.server.uri
          },
          readOnly = readOnly,
          smartModel = smartModel,
          fastModel = fastModel,
          showLineNumbers = lineNumbers,
        )
      )
    }
    /* One selection, every toolchain: re-bind whatever is installed when it changes. */
    ModelSelection.onChange {
      if (tasksEnabled) ServerTaskActions.refreshModels()
      if (modifyEnabled) ModifyFilesActions.refreshModels()
      models = try {
        CliSupport.resolveModels(
          user = user,
          smartModel = ModelSelection.smart,
          fastModel = ModelSelection.fast,
          imageModel = imageModel,
          audioModel = audioModel,
          quiet = true,
        )
      } catch (_: Exception) {
        models
      }
      println("Models -> ${ModelSelection.summary()}")
    }


    val server = start(
      baseDir, host, port, gitEnabled, readOnly, uiEnabled,
      terminalEnabled, execPermissive, shell, tasksEnabled, fixCommand,
      modifyEnabled, lineNumbers, homeEnabled, landing
    )
    val boundPort = (server.connectors.first() as ServerConnector).localPort
    val displayHost = if (host == "0.0.0.0" || host == "::") "localhost" else host

    println("Serving ${baseDir.absolutePath}")
    println("  ->  http://$displayHost:$boundPort/ (redirects to ${serverInfo.landingPath})")
    if (homeEnabled) {
      println("  Home      -> http://$displayHost:$boundPort$HOME_PREFIX/ (overview: links, config, endpoints)")
      println("  Settings  -> http://$displayHost:$boundPort$HOME_PREFIX/settings.html (models & API keys)")
      println("               GET      http://$displayHost:$boundPort/serverInfo")
      println("               GET/POST http://$displayHost:$boundPort/apiKeys")
    }
    if (uiEnabled) println("  IDE view  -> http://$displayHost:$boundPort$UI_PREFIX/")
    println("  Classic   -> http://$displayHost:$boundPort$FILES_PREFIX/$ROOT_SEGMENT/")
    println("  Assets    -> http://$displayHost:$boundPort$LIB_PREFIX/ (classpath web/lib), $APP_PREFIX/ (classpath web/app)")
    println("  Alias     -> http://$displayHost:$boundPort$PROXY_PREFIX/ (same as /, for ?session=... URLs)")
    println("  FS API v1 -> http://$displayHost:$boundPort$FILES_PREFIX/$ROOT_SEGMENT/.fsapi/v1/meta")
    println(
      "  Mode      -> ${if (readOnly) "read-only" else "read-write"}" +
          ", terminal ${if (terminalEnabled && !readOnly) "enabled" else "disabled"}" +
          ", exec ${if (execPermissive) "unrestricted" else "allowlisted"}"
    )
    val apiBase = "http://$displayHost:$boundPort$FILES_PREFIX/$ROOT_SEGMENT/.fsapi/v1"
    println("  Models    -> ${ModelSelection.summary()} (${ModelSelection.modelIds().size} available)")
    println("               GET/POST $apiBase/models — or the IDE view's Tools ▸ Select Models…")
    if (tasksEnabled) {
      println("  Tasks     -> docops/autofix enabled (root ${taskRoot.absolutePath})")
      println("               POST $apiBase/docops?command=plan")
      println("               POST http://$displayHost:$boundPort$DOCOPS_PREFIX?doc=<file>  (DocProcessorServlet)")
      println("               POST $apiBase/autofix?cmd=<command>")
      println("               GET  $apiBase/tasks")
      if (readOnly) println("               (read-only mount: 'docops run' and 'autofix' answer EROFS)")
      if (smartModel == null) {
        println("               NOTE: no smart model selected; set --smart-model or COGNOTIK_SMART_MODEL")
      }
    } else {
      println("  Tasks     -> disabled")
    }
    if (modifyEnabled) {
      println("  Modify    -> patch chat enabled (root ${taskRoot.absolutePath}, line numbers $lineNumbers)")
      println("               POST $apiBase/modify?path=<file>")
      println("               (the chat UI server starts on the first request)")
      if (smartModel == null) {
        println("               NOTE: no smart model selected; set --smart-model or COGNOTIK_SMART_MODEL")
      }
    } else {
      println("  Modify    -> disabled${if (readOnly) " (read-only mount)" else ""}")
    }
    if (!readOnly && (terminalEnabled || execPermissive || tasksEnabled) &&
      host != "127.0.0.1" && host != "localhost"
    ) {
      println("  WARNING: arbitrary code execution is enabled on a non-loopback interface (see --secure)")
    }
    println("Press Ctrl-C to stop.")

    Runtime.getRuntime().addShutdownHook(Thread {
      println("\nShutting down...")
      try {
        server.stop()
      } catch (_: Exception) {
        // best effort
      }
    })

    /* Blocks until the server is stopped (i.e. by the shutdown hook on Ctrl-C). */
    server.join()
  }

  fun start(
    baseDir: File,
    host: String = "127.0.0.1",
    port: Int = 8081,
    gitEnabled: Boolean = true,
    readOnly: Boolean = false,
    uiEnabled: Boolean = true,
    terminalEnabled: Boolean = true,
    execPermissive: Boolean = true,
    shell: List<String> = emptyList(),
    tasksEnabled: Boolean = false,
    defaultFixCommand: String = "",
    modifyEnabled: Boolean = false,
    lineNumbers: Boolean = false,
    homeEnabled: Boolean = true,
    landing: String? = null,
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
    ensureWebSocketSupport(context)


    val showTasks = tasksEnabled && ServerTaskActions.isEnabled
    val showModify = modifyEnabled && !readOnly && ModifyFilesActions.isEnabled

    serverInfo = ServerInfo(
      servedDir = baseDir.absolutePath,
      host = host,
      port = port,
      gitEnabled = gitEnabled,
      readOnly = readOnly,
      uiEnabled = uiEnabled,
      homeEnabled = homeEnabled,
      terminalEnabled = terminalEnabled && !readOnly,
      execPermissive = execPermissive,
      tasksEnabled = showTasks,
      modifyEnabled = showModify,
      landingPath = landingPathFor(landing, homeEnabled, uiEnabled),
    )
    val fileServlet = if (readOnly) ReadOnlyFileServlet(baseDir, gitEnabled, uiEnabled, execPermissive, showTasks)
    else SimpleFileServlet(
      baseDir, gitEnabled, readOnly = false, uiEnabled = uiEnabled,
      terminalEnabled = terminalEnabled, execPermissive = execPermissive, shell = shell,
      tasksEnabled = showTasks, defaultFixCommand = defaultFixCommand,
      modifyEnabled = showModify, lineNumbers = lineNumbers
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
    addServletSafely(context, fileHolder, "$FILES_PREFIX/*")

    /* ZIP downloads: session = directory name, resolved against the parent dir. */
    addServletSafely(
      context,
      ServletHolder("zip", StaticZipServlet(baseDir.parentFile?.absolutePath ?: baseDir.absolutePath)),
      "/zip"
    )
    /*
     * The DocOps engine, exposed as itself. This is the same instance the FS API
     * 'docops' action invokes (see ServerTaskActions.install), so the HTTP endpoint
     * and the action can never drift apart.
     */
    docProcessorServlet?.let { servlet ->
      val docopsHolder = ServletHolder("docops", servlet)
      docopsHolder.registration.setMultipartConfig(
        MultipartConfigElement(System.getProperty("java.io.tmpdir"))
      )
      addServletSafely(context, docopsHolder, "$DOCOPS_PREFIX/*")
      addServletSafely(context, docopsHolder, DOCOPS_PREFIX)
    }


    if (uiEnabled) {
      addServletSafely(context, ServletHolder("webui", WebUiServlet()), "$UI_PREFIX/*")
    }
    /*
     * Shared classpath assets, always mounted: /lib -> web/lib, /app -> web/app.
     * They are read from the classpath (never from the workspace), so they are safe on
     * read-only, --no-ui and --secure mounts alike.
     */
    register(context, ServletHolder("web-lib", WebUiServlet("web/lib")), LIB_PREFIX)
    register(context, ServletHolder("web-app", WebUiServlet("web/app")), APP_PREFIX)


    /*
     * The homepage is classpath-served (never from the workspace) and is the default
     * landing page: it is the only place that explains the mount and lets the user pick
     * models. --ui and --files move the landing page without unmounting anything.
     */
    if (homeEnabled) {
      register(context, ServletHolder("home", StaticResourceServlet()), this@FileServerCli.HOME_PREFIX)
      /* The overview page is a pure client of this: never let the two drift apart. */
      register(context, ServletHolder("server-info", ServerInfoServlet()), "/serverInfo")
      register(context, ServletHolder("settings-api", UserSettingsServlet()), "/userSettings")
      register(context, ServletHolder("provider-api", ApiProviderServlet()), "/apiProviders")
      register(context, ServletHolder("keys-api", ApiKeyServlet()), "/apiKeys")
    }
    /*
     * Registered last and on the default mapping: every mount above uses a more specific
     * path spec and keeps winning, while '/' (and anything unmatched) is answered by the
     * gateway instead of Jetty's Default404Servlet. Both specs share one holder so the
     * exact root ("") and the default ("/") resolve to the same instance.
     */
    val gatewayHolder = ServletHolder("root-gateway", RootGatewayServlet())
    addServletSafely(context, gatewayHolder, "")
    addServletSafely(context, gatewayHolder, "/")



    server.handler = context
    server.stopAtShutdown = true
    server.start()
    /*
     * Identify this process as the owner of the sessions it creates: SessionProxyServer
     * records a worker id per session, and it is only meaningful once the port is bound
     * (port 0 means "pick a free one").
     */
    val boundPort = (server.connectors.first() as ServerConnector).localPort
    val ownerHost = if (host == "0.0.0.0" || host == "::" || host.isBlank()) "localhost" else host
    SessionProxyServer.OWNER_ID = "$ownerHost:$boundPort"
    return server
  }

  /**
   * Resolves the landing page. An explicit `--files` / `--ui` / `--home` wins; otherwise
   * the most informative page that is actually mounted is used, so the gateway can never
   * redirect to a disabled mount.
   */
  private fun landingPathFor(landing: String?, homeEnabled: Boolean, uiEnabled: Boolean): String = when {
    landing == "files" -> "$FILES_PREFIX/$ROOT_SEGMENT/"
    landing == "ui" && uiEnabled -> "$UI_PREFIX/"
    landing == "home" && homeEnabled -> "$HOME_PREFIX/"
    homeEnabled -> "$HOME_PREFIX/"
    uiEnabled -> "$UI_PREFIX/"
    else -> "$FILES_PREFIX/$ROOT_SEGMENT/"
  }

  private fun register(
    context: ServletContextHandler,
    homeHolder: ServletHolder,
    prefix: String
  ) {
    addServletSafely(context, homeHolder, "$prefix/*")
    addServletSafely(context, homeHolder, prefix)
  }

  /** True when [pathSpec] is already claimed by a servlet mapping in [context]. */
  private fun isMapped(context: ServletContextHandler, pathSpec: String): Boolean =
    context.servletHandler.servletMappings?.any { mapping ->
      mapping.pathSpecs?.any { it == pathSpec } == true
    } == true

  /**
   * Adds [holder] at [pathSpec] unless something (e.g. the session proxy) already
   * mapped that spec. Jetty throws `IllegalStateException: Multiple servlets map to
   * path ...` at start-up for duplicates, which is a fatal error at a point where the
   * offending registration is long gone; first-registration-wins is both deterministic
   * and debuggable.
   *
   * @return true when the servlet was actually registered.
   */
  private fun addServletSafely(
    context: ServletContextHandler,
    holder: ServletHolder,
    pathSpec: String
  ): Boolean {
    if (isMapped(context, pathSpec)) {
      log.info("Skipping servlet '{}' at {}: path already mapped", holder.name, pathSpec)
      return false
    }
    context.addServlet(holder, pathSpec)
    return true
  }


  val log = LoggerFactory.getLogger(FileServerCli::class.java)
}