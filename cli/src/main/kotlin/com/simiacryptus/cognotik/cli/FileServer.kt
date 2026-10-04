package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.cli.CliSupport.defaultUser
import com.simiacryptus.cognotik.fileserver.StaticZipServlet
import com.simiacryptus.cognotik.fileserver.WebUiServlet
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.ServiceKey
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.LOCAL_WORKER_ID
import com.simiacryptus.cognotik.platform.service.UserProvider
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.servlet.ApiKeyServlet
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet
import com.simiacryptus.cognotik.webui.servlet.UserSettingsServlet
import com.simiacryptus.cognotik.webui.servlet.action.ExtractUtilsFsAction
import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.*

open class FileServer {
  private val log = LoggerFactory.getLogger(FileServer::class.java)

  /**
   * Everything the command line can configure. Immutable: parsing produces one,
   * [validate] normalises it and [installActions] may return a copy with features
   * disabled that could not be initialised.
   */
  data class Config(
    val port: Int = 8081,
    val host: String = "127.0.0.1",
    val gitEnabled: Boolean = true,
    val readOnly: Boolean = false,
    val uiEnabled: Boolean = true,
    val homeEnabled: Boolean = true,
    val terminalEnabled: Boolean = true,
    val execPermissive: Boolean = true,
    val shell: List<String> = emptyList(),
    val dir: String? = null,
    val email: String? = null,
    val tasksEnabled: Boolean = true,
    val taskRoot: String? = null,
    val smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL"),
    val fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL"),
    val imageModel: String? = System.getenv("COGNOTIK_IMAGE_MODEL"),
    val audioModel: String? = System.getenv("COGNOTIK_AUDIO_MODEL"),
    val taskTimeout: Long = 30L,
    val taskMonitor: Boolean = false,
    val fixCommand: String = "",
    val modifyEnabled: Boolean = true,
    val lineNumbers: Boolean = false,
    val chatPort: Int = 8061,
    val extractUtilsEnabled: Boolean = true,
    val utilDir: String = ExtractUtilsFsAction.DEFAULT_DIR,
    /** null = "whatever is enabled", see landingPathFor(). */
    val landing: String? = null,
  ) {
    val baseDir: File get() = File(dir ?: ".").canonicalFile
    val taskRootDir: File get() = (taskRoot?.let { File(it) } ?: baseDir).canonicalFile

    /** A read-only mount can never modify the workspace. */
    fun normalized(): Config = if (readOnly) copy(modifyEnabled = false, extractUtilsEnabled = false) else this
  }

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
    /** True when POST /.fsapi/v1/extract-utils is available. */
    val extractUtilsEnabled: Boolean = false,
    /** Where the gateway page ('/') sends the browser, e.g. `/home/`. */
    val landingPath: String = "",
  )

  /** The single Jetty server (file server + session proxy), set once started. */
  @Volatile
  var runningServer: Server? = null

  /** URL of the chat UI, served by this same server under [FileServerCli.PROXY_PREFIX]. */
  fun sessionUri(): URI {
    val server = runningServer ?: throw IllegalStateException("Server not started yet")
    val port = (server.connectors.first() as ServerConnector).localPort
    val host = CognotikAppServer.displayHostFor(serverInfo.host)
    return URI("http", null, host, port, "${FileServerCli.PROXY_PREFIX}/", null, null)
  }


  /** Contexts that already had websocket support installed (weakly held). */
  private val webSocketContexts: MutableSet<ServletContextHandler> =
    Collections.synchronizedSet(
      Collections.newSetFromMap(WeakHashMap<ServletContextHandler, Boolean>())
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
      log.debug("Websocket support enabled on context '{}'", context.contextPath)
      true
    } catch (e: Exception) {
      webSocketContexts.remove(context)
      log.warn("Could not enable websocket support on context '{}'", context.contextPath, e)
      System.err.println("warning: could not enable websocket support: ${e.message ?: e.javaClass.simpleName}")
      false
    }
  }


  /**
   * Gateway for the context root. `/` is not a mount of its own: it is an alias for the
   * landing page ([ServerInfo.landingPath], the homepage by default), because the
   * homepage is the only page that explains the mount and lets the user pick models.
   *
   * `/?session=ID` is sent to [FileServerCli.PROXY_PREFIX] instead, so the two documented spellings
   * of a session URL ('/' and '/proxy/') keep behaving identically.
   *
   * It is mapped both on the exact root spec (`""`) and on the default spec (`"/"`), so
   * it replaces Jetty's `Default404Servlet`; every other mount is a more specific spec
   * and therefore still wins. Paths other than the root are answered with 404 - the
   * gateway is an alias for '/', not a wildcard redirect.
   */
  private inner class RootGatewayServlet : HttpServlet() {
    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
      if (resp.isCommitted) {
        log.debug("Gateway: response already committed for {}", req.requestURI)
        return
      }
      val path = req.requestURI.removePrefix(req.contextPath).ifEmpty { "/" }
      val query = req.queryString?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
      try {
        if (!req.getParameter("session").isNullOrBlank()) {
          resp.sendRedirect("${FileServerCli.PROXY_PREFIX}/$query")
          return
        }
        if (path != "/") {
          /* Reached via the default mapping: nothing else claimed this path. */
          log.debug("Gateway: 404 for unmatched path {}", path)
          resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No such resource: $path")
          return
        }
        /* Never redirect to '/' itself: that would loop through this very servlet. */
        val target = serverInfo.landingPath.takeIf { it.isNotBlank() && it != "/" } ?: "${FileServerCli.HOME_PREFIX}/"
        resp.setHeader("Cache-Control", "no-store")
        resp.sendRedirect(target + query)
      } catch (e: IllegalStateException) {
        log.debug("Gateway: could not respond to {} (response committed)", path, e)
      }
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
                       --chat-port <n>    (deprecated, ignored: the chat UI shares the main port under /proxy/)
                   POST {mount}/.fsapi/v1/modify?path=src/Foo.kt[&path=...][&lineNumbers=true]
                     -> { "session": "...", "url": "http://host:port/proxy/#<session>", "files": [...] }
                   Omit 'path' to select the whole served tree. Folders are expanded; the
                   selection is embedded in the chat's system prompt and the model's patches
                   are applied to the workspace, so it is refused with EROFS when read-only.
                   Session URLs are served both from the context root and from /proxy/,
                   i.e. http://host:port/?session=ID and http://host:port/proxy/?session=ID
                   are equivalent (on this server and on the chat server).
                 Bundled tooling (web/util), enabled by default:
                       --no-extract-utils Do not expose the extract-utils operation
                       --util-dir <dir>   Default target directory (default cognotik-tools)
                   POST {mount}/.fsapi/v1/extract-utils[?dir=<dir>][&overwrite=false]
                     -> { "dir": "...", "count": N, "files": [...] }
                   Copies the classpath toolkit into the workspace; '?dir=' is resolved
                   against the task root and may not escape it. Refused with EROFS on a
                   read-only mount.


                By default this is a PERMISSIVE LOCAL server: interactive terminals and
                unrestricted child processes are enabled and it binds to 127.0.0.1 only.
                Use --secure (and/or the individual flags) before exposing it.

                The server runs in the foreground; press Ctrl-C to stop it.
            """.trimIndent()

  // ---------------------------------------------------------------------------------
  // Entry point
  // ---------------------------------------------------------------------------------

  fun run(args: Array<String>) {
    CognotikPlatform.init()
    ServiceKey.USER_RESOLVER.factory = {
      object : UserProvider {
        override fun authenticate(
          request: HttpServletRequest
        ) = defaultUser
      }
    }
    DatabaseFacet.root = File(".").absolutePath

    val parsed = parseArgs(args) ?: run {
      println(usage())
      return
    }
    initializeUser(parsed)
    val validated = validate(parsed)
    val config = installActions(validated)
    installModelSelectionListener(config)

    val server = start(config)
    printBanner(config, server)
    installShutdownHook(server)

    /* Blocks until the server is stopped (i.e. by the shutdown hook on Ctrl-C). */
    server.join()
  }

  // ---------------------------------------------------------------------------------
  // Parsing & validation
  // ---------------------------------------------------------------------------------

  /**
   * Parses the command line into a [Config]. Pure: no globals are touched.
   * @return null when `--help` was requested.
   */
  fun parseArgs(args: Array<String>): Config? {
    var c = Config()
    var i = 0
    fun value(arg: String): String = args.getOrNull(++i) ?: CliSupport.fail("Missing value for $arg")
    fun intValue(arg: String): Int =
      args.getOrNull(++i)?.toIntOrNull() ?: CliSupport.fail("Missing or invalid value for $arg")

    while (i < args.size) {
      when (val arg = args[i]) {
        "-p", "--port" -> c = c.copy(port = intValue(arg))
        "-h", "--host" -> c = c.copy(host = value(arg))
        "--shell" -> c = c.copy(shell = value(arg).trim().split(" ").filter { it.isNotBlank() })
        "--no-git" -> c = c.copy(gitEnabled = false)
        "--read-only" -> c = c.copy(readOnly = true)
        "--no-terminal" -> c = c.copy(terminalEnabled = false)
        "--no-exec" -> c = c.copy(execPermissive = false)
        "--secure" -> c = c.copy(
          readOnly = true,
          terminalEnabled = false,
          execPermissive = false,
          tasksEnabled = false,
          modifyEnabled = false,
          extractUtilsEnabled = false,
        )

        "--no-tasks" -> c = c.copy(tasksEnabled = false)
        "--tasks" -> c = c.copy(tasksEnabled = true)
        "--no-modify" -> c = c.copy(modifyEnabled = false)
        "--modify" -> c = c.copy(modifyEnabled = true)
        "--no-extract-utils" -> c = c.copy(extractUtilsEnabled = false)
        "--extract-utils" -> c = c.copy(extractUtilsEnabled = true)
        "--util-dir" -> c = c.copy(utilDir = value(arg))
        "--line-numbers" -> c = c.copy(lineNumbers = true)
        "--chat-port" -> {
          c = c.copy(chatPort = intValue(arg))
          System.err.println("warning: --chat-port is ignored; the chat UI is served on the main port under ${FileServerCli.PROXY_PREFIX}/")
        }

        "--email" -> c = c.copy(email = value(arg))
        "--task-root" -> c = c.copy(taskRoot = value(arg))
        "--smart-model" -> c = c.copy(
          smartModel = args.getOrNull(++i) ?: c.smartModel ?: CliSupport.fail("Missing value for $arg")
        )

        "--fast-model" -> c = c.copy(
          fastModel = args.getOrNull(++i) ?: c.fastModel ?: CliSupport.fail("Missing value for $arg")
        )

        "--task-timeout" -> c = c.copy(
          taskTimeout = args.getOrNull(++i)?.toLongOrNull()
            ?: CliSupport.fail("Missing or invalid value for $arg")
        )

        "--task-monitor" -> c = c.copy(taskMonitor = true)
        "--fix-cmd" -> c = c.copy(fixCommand = value(arg))
        "--no-ui" -> c = c.copy(uiEnabled = false)
        "--ui" -> c = c.copy(uiEnabled = true, landing = "ui")
        "--no-home" -> c = c.copy(homeEnabled = false)
        "--home" -> c = c.copy(homeEnabled = true, landing = "home")
        "--files" -> c = c.copy(landing = "files")
        "--help" -> return null
        else -> {
          if (arg.startsWith("-")) CliSupport.fail("Unknown option: $arg")
          if (c.dir != null) CliSupport.fail("Only one directory may be specified")
          c = c.copy(dir = arg)
        }
      }
      i++
    }
    return c
  }

  /** Normalises the config and checks the directories it refers to. */
  fun validate(config: Config): Config {
    val c = config.normalized()
    val baseDir = c.baseDir
    if (!baseDir.exists() || !baseDir.isDirectory) {
      CliSupport.fail("Not a directory: ${baseDir.absolutePath}")
    }
    val taskRoot = c.taskRootDir
    if ((c.tasksEnabled || c.modifyEnabled || c.extractUtilsEnabled) && !taskRoot.isDirectory) {
      CliSupport.fail("Task root is not a directory: ${taskRoot.absolutePath}")
    }
    return c
  }

  // ---------------------------------------------------------------------------------
  // Initialisation
  // ---------------------------------------------------------------------------------

  /** Builds the CLI user (honouring --email), bootstraps the platform and resolves models. */
  fun initializeUser(config: Config) {
    config.email?.let { CliSupport.email = it }
    val cliUser = CliSupport.defaultUser
    CliSupport.bootstrapPlatform(cliUser)
    if (cliUser == null) {
      log.info("No user could be created; the server will start without a user or models.")
      return
    }
    available = CliSupport.availableModels(cliUser)
    /* The pair is runtime state now: the web UI may replace it at any time. */
    ModelSelection.install(user = { cliUser }, smart = config.smartModel, fast = config.fastModel)
    ModelSelectionActions.install()
    models = try {
      when (ModelSelection.smart) {
        null -> null
        else -> CliSupport.resolveModels(
          user = cliUser,
          smartModel = ModelSelection.smart,
          fastModel = ModelSelection.fast,
          imageModel = config.imageModel,
          audioModel = config.audioModel,
        )
      }
    } catch (e: Exception) {
      /* Starting without a model is no longer fatal - pick one from the web UI. */
      log.warn("Could not resolve models at start-up; continuing without them", e)
      System.err.println("warning: ${e.message}")
      null
    }
  }

  /**
   * Registers the FS API actions. Registering is cheap and side-effect free; the
   * platform bootstrap happens lazily on first use, so a missing API key never prevents
   * the file server from starting.
   *
   * @return the effective config (features that failed to initialise are disabled).
   */
  fun installActions(config: Config): Config {
    installTaskActions(config)
    val modifyOk = installModifyActions(config)
    installExtractUtils(config)
    return config.copy(modifyEnabled = modifyOk)
  }

  private fun installTaskActions(config: Config) {
    if (!config.tasksEnabled) return
    ServerTaskActions.install(
      ServerTaskActions.Config(
        root = config.taskRootDir,
        readOnly = config.readOnly,
        smartModel = config.smartModel,
        fastModel = config.fastModel,
        timeoutMinutes = config.taskTimeout,
        monitor = config.taskMonitor,
      )
    )
  }

  /**
   * The chat UI is mounted on this server under [FileServerCli.PROXY_PREFIX]; the URI
   * is resolved lazily because the port is only known once the server is bound.
   *
   * @return true when the patch chat is enabled.
   */
  private fun installModifyActions(config: Config): Boolean {
    if (!config.modifyEnabled) return false
    ModifyFilesActions.install(
      ModifyFilesActions.Config(
        root = config.taskRootDir,
        chatUri = { sessionUri() },
        readOnly = config.readOnly,
        smartModel = config.smartModel,
        fastModel = config.fastModel,
        showLineNumbers = config.lineNumbers,
      )
    )
    return true
  }

  /** Extraction only reads the classpath and writes under the task root: nothing to bootstrap. */
  private fun installExtractUtils(config: Config) {
    if (!config.extractUtilsEnabled) return
    val taskRoot = config.taskRootDir
    ExtractUtilsFsAction.install(
      ExtractUtilsFsAction.Config(
        readOnly = config.readOnly,
        defaultDir = config.utilDir,
      ) { taskRoot }
    )
  }

  /** One selection, every toolchain: re-bind whatever is installed when it changes. */
  fun installModelSelectionListener(config: Config) {
    ModelSelection.onChange {
      if (config.tasksEnabled) runCatching { ServerTaskActions.refreshModels() }
        .onFailure { log.warn("Failed to refresh task models", it) }
      if (config.modifyEnabled) runCatching { ModifyFilesActions.refreshModels() }
        .onFailure { log.warn("Failed to refresh modify models", it) }
      models = try {
        CliSupport.resolveModels(
          user = CliSupport.defaultUser ?: throw IllegalStateException("No user available"),
          smartModel = ModelSelection.smart,
          fastModel = ModelSelection.fast,
          imageModel = config.imageModel,
          audioModel = config.audioModel,
          quiet = true,
        )
      } catch (e: Exception) {
        log.warn("Model re-resolution failed after selection change; keeping previous models", e)
        models
      }
      log.info("Model selection changed: {}", ModelSelection.summary())
      println("Models -> ${ModelSelection.summary()}")
    }
  }

  private fun installShutdownHook(server: Server) {
    Runtime.getRuntime().addShutdownHook(Thread {
      println("\nShutting down...")
      try {
        server.stop()
      } catch (e: Exception) {
        log.debug("Error while stopping server (ignored)", e)
      }
    })
  }

  // ---------------------------------------------------------------------------------
  // Banner
  // ---------------------------------------------------------------------------------

  fun printBanner(config: Config, server: Server) {
    val boundPort = (server.connectors.first() as ServerConnector).localPort
    val host = config.host
    val displayHost = if (host == "0.0.0.0" || host == "::") "localhost" else host
    val origin = "http://$displayHost:$boundPort"
    val taskRoot = config.taskRootDir
    val readOnly = config.readOnly

    println("Serving ${config.baseDir.absolutePath}")
    println("  ->  $origin/ (redirects to ${serverInfo.landingPath})")
    if (config.homeEnabled) {
      println("  Home      -> $origin${FileServerCli.HOME_PREFIX}/ (overview: links, config, endpoints)")
      println("  Settings  -> $origin${FileServerCli.HOME_PREFIX}/settings.html (models & API keys)")
      println("               GET      $origin/serverInfo")
      println("               GET/POST $origin/apiKeys")
    }
    if (config.uiEnabled) println("  IDE view  -> $origin${SimpleFileServlet.UI_PREFIX}/")
    println("  Classic   -> $origin${SimpleFileServlet.FILES_PREFIX}/${SimpleFileServlet.ROOT_SEGMENT}/")
    println("  Assets    -> $origin${FileServerCli.LIB_PREFIX}/ (classpath web/lib), ${FileServerCli.APP_PREFIX}/ (classpath web/app)")
    println("  Alias     -> $origin${FileServerCli.PROXY_PREFIX}/ (same as /, for ?session=... URLs)")
    println("  FS API v1 -> $origin${SimpleFileServlet.FILES_PREFIX}/${SimpleFileServlet.ROOT_SEGMENT}/.fsapi/v1/meta")
    println(
      "  Mode      -> ${if (readOnly) "read-only" else "read-write"}" +
          ", terminal ${if (config.terminalEnabled && !readOnly) "enabled" else "disabled"}" +
          ", exec ${if (config.execPermissive) "unrestricted" else "allowlisted"}"
    )
    val apiBase = "$origin${SimpleFileServlet.FILES_PREFIX}/${SimpleFileServlet.ROOT_SEGMENT}/.fsapi/v1"
    println("  Models    -> ${ModelSelection.summary()} (${ModelSelection.modelIds().size} available)")
    println("               GET/POST $apiBase/models — or the IDE view's Tools ▸ Select Models…")
    if (config.tasksEnabled) {
      println("  Tasks     -> docops/autofix enabled (root ${taskRoot.absolutePath})")
      println("               POST $apiBase/docops?command=plan")
      println("               POST $origin${FileServerCli.DOCOPS_PREFIX}?doc=<file>  (DocProcessorServlet)")
      println("               POST $apiBase/autofix?cmd=<command>")
      println("               GET  $apiBase/tasks")
      if (readOnly) println("               (read-only mount: 'docops run' and 'autofix' answer EROFS)")
      if (config.smartModel == null) {
        println("               NOTE: no smart model selected; set --smart-model or COGNOTIK_SMART_MODEL")
      }
    } else {
      println("  Tasks     -> disabled")
    }
    if (config.modifyEnabled) {
      println("  Modify    -> patch chat enabled (root ${taskRoot.absolutePath}, line numbers ${config.lineNumbers})")
      println("               POST $apiBase/modify?path=<file>")
      println("               (chat UI served at $origin${FileServerCli.PROXY_PREFIX}/)")
      if (config.smartModel == null) {
        println("               NOTE: no smart model selected; set --smart-model or COGNOTIK_SMART_MODEL")
      }
    } else {
      println("  Modify    -> disabled${if (readOnly) " (read-only mount)" else ""}")
    }
    if (config.extractUtilsEnabled) {
      println("  Tools     -> extract-utils enabled (default dir ${File(taskRoot, config.utilDir).absolutePath})")
      println("               POST $apiBase/${ExtractUtilsFsAction.EXTRACT_OP}?dir=${config.utilDir}")
    } else {
      println("  Tools     -> extract-utils disabled${if (readOnly) " (read-only mount)" else ""}")
    }
    if (!readOnly && (config.terminalEnabled || config.execPermissive || config.tasksEnabled) &&
      host != "127.0.0.1" && host != "localhost"
    ) {
      println("  WARNING: arbitrary code execution is enabled on a non-loopback interface (see --secure)")
    }
    println("Press Ctrl-C to stop.")
  }

  // ---------------------------------------------------------------------------------
  // Server construction
  // ---------------------------------------------------------------------------------

  fun start(config: Config): Server = start(
    baseDir = config.baseDir,
    host = config.host,
    port = config.port,
    gitEnabled = config.gitEnabled,
    readOnly = config.readOnly,
    uiEnabled = config.uiEnabled,
    terminalEnabled = config.terminalEnabled,
    execPermissive = config.execPermissive,
    shell = config.shell,
    tasksEnabled = config.tasksEnabled,
    defaultFixCommand = config.fixCommand,
    modifyEnabled = config.modifyEnabled,
    lineNumbers = config.lineNumbers,
    homeEnabled = config.homeEnabled,
    landing = config.landing,
  )

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
    tasksEnabled: Boolean = true,
    defaultFixCommand: String = "",
    modifyEnabled: Boolean = false,
    lineNumbers: Boolean = false,
    homeEnabled: Boolean = true,
    landing: String? = null,
  ): Server {

    val server = server(host, port)
    /* ONE root context: every servlet below must be registered on the instance Jetty serves. */
    val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS).apply {
      this.contextPath = "/"
      this.resourceBase = baseDir.absolutePath
    }
    ensureWebSocketSupport(context)

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
      tasksEnabled = tasksEnabled && ServerTaskActions.isEnabled,
      modifyEnabled = modifyEnabled && !readOnly && ModifyFilesActions.isEnabled,
      extractUtilsEnabled = ExtractUtilsFsAction.isEnabled && !readOnly,
      landingPath = landingPathFor(landing, homeEnabled, uiEnabled),
    )
    registerFileServlets(
      context, baseDir, gitEnabled, readOnly, uiEnabled, terminalEnabled,
      execPermissive, shell, tasksEnabled && ServerTaskActions.isEnabled, defaultFixCommand,
      modifyEnabled && !readOnly && ModifyFilesActions.isEnabled, lineNumbers
    )
    registerDocOps(context)
    registerAssets(context, uiEnabled)
    if (homeEnabled) registerHome(context)
    registerGateway(context)
    try {
      configureContext(context)
    } catch (e: Exception) {
      log.error("configureContext hook failed for '{}'; continuing without customisations", context.contextPath, e)
    }


    /* The session proxy (chat UI) shares this server and port under /proxy. */
    val sessionContext = try {
      CognotikAppServer.newSessionContext(paths = arrayOf(FileServerCli.PROXY_PREFIX))
    } catch (e: Exception) {
      log.error("Could not create session proxy context; chat UI disabled", e)
      System.err.println("warning: chat UI disabled: ${e.message ?: e.javaClass.simpleName}")
      null
    }
    server.handler = if (sessionContext != null) ContextHandlerCollection(sessionContext, context) else context
    server.stopAtShutdown = true
    log.debug("Starting server: dir={}, host={}, port={}, info={}", baseDir, host, port, serverInfo)
    val startMs = System.currentTimeMillis()
    try {
      server.start()
    } catch (e: Exception) {
      log.error("Failed to start server on {}:{} (port in use?)", host, port, e)
      runCatching { server.stop() }.onFailure { log.debug("Cleanup stop failed", it) }
      throw e
    }
    log.info(
      "Server started on {}:{} in {} ms (dir={}, landing={})",
      host, (server.connectors.first() as ServerConnector).localPort,
      System.currentTimeMillis() - startMs, baseDir.absolutePath, serverInfo.landingPath
    )
    /*
     * Identify this process as the owner of the sessions it creates: SessionProxyServer
     * records a worker id per session, and it is only meaningful once the port is bound
     * (port 0 means "pick a free one").
     */
    LOCAL_WORKER_ID = workerId(server, host)
    runningServer = server
    return server
  }

  private fun server(host: String, port: Int): Server {
    val server = Server()
    val connector = ServerConnector(server).apply {
      this.host = host
      this.port = port
    }
    server.addConnector(connector)
    return server
  }

  private fun registerFileServlets(
    context: ServletContextHandler,
    baseDir: File,
    gitEnabled: Boolean,
    readOnly: Boolean,
    uiEnabled: Boolean,
    terminalEnabled: Boolean,
    execPermissive: Boolean,
    shell: List<String>,
    showTasks: Boolean,
    defaultFixCommand: String,
    showModify: Boolean,
    lineNumbers: Boolean,
  ) {
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
    addServletSafely(context, fileHolder, "${SimpleFileServlet.FILES_PREFIX}/*")

    /* ZIP downloads: session = directory name, resolved against the parent dir. */
    addServletSafely(
      context,
      ServletHolder("zip", StaticZipServlet(baseDir.parentFile?.absolutePath ?: baseDir.absolutePath)),
      "/zip"
    )
  }

  /**
   * The DocOps engine, exposed as itself. This is the same instance the FS API
   * 'docops' action invokes (see ServerTaskActions.install), so the HTTP endpoint
   * and the action can never drift apart.
   */
  private fun registerDocOps(context: ServletContextHandler) {
    val servlet = docProcessorServlet ?: return
    val docopsHolder = ServletHolder("docops", servlet)
    docopsHolder.registration.setMultipartConfig(
      MultipartConfigElement(System.getProperty("java.io.tmpdir"))
    )
    addServletSafely(context, docopsHolder, "${FileServerCli.DOCOPS_PREFIX}/*")
    addServletSafely(context, docopsHolder, FileServerCli.DOCOPS_PREFIX)
  }

  /**
   * Shared classpath assets, always mounted: /lib -> web/lib, /app -> web/app.
   * They are read from the classpath (never from the workspace), so they are safe on
   * read-only, --no-ui and --secure mounts alike.
   */
  private fun registerAssets(context: ServletContextHandler, uiEnabled: Boolean) {
    if (uiEnabled) {
      addServletSafely(context, ServletHolder("webui", WebUiServlet()), "${SimpleFileServlet.UI_PREFIX}/*")
    }
    register(context, ServletHolder("web-lib", WebUiServlet("web/lib")), FileServerCli.LIB_PREFIX)
    register(context, ServletHolder("web-app", WebUiServlet("web/app")), FileServerCli.APP_PREFIX)
  }

  /**
   * The homepage is classpath-served (never from the workspace) and is the default
   * landing page: it is the only place that explains the mount and lets the user pick
   * models. --ui and --files move the landing page without unmounting anything.
   */
  private fun registerHome(context: ServletContextHandler) {
    register(context, ServletHolder("home", StaticResourceServlet()), FileServerCli.HOME_PREFIX)
    /* The overview page is a pure client of this: never let the two drift apart. */
    register(context, ServletHolder("server-info", ServerInfoServlet()), "/serverInfo")
    register(context, ServletHolder("settings-api", UserSettingsServlet()), "/userSettings")
    register(context, ServletHolder("provider-api", ApiProviderServlet()), "/apiProviders")
    register(context, ServletHolder("keys-api", ApiKeyServlet()), "/apiKeys")
  }

  /**
   * Registered last and on the default mapping: every mount above uses a more specific
   * path spec and keeps winning, while '/' (and anything unmatched) is answered by the
   * gateway instead of Jetty's Default404Servlet. Both specs share one holder so the
   * exact root ("") and the default ("/") resolve to the same instance.
   */
  private fun registerGateway(context: ServletContextHandler) {
    val gatewayHolder = ServletHolder("root-gateway", RootGatewayServlet())
    addServletSafely(context, gatewayHolder, "")
    addServletSafely(context, gatewayHolder, "/")
  }

  /**
   * Extension hook invoked after all servlets are registered and before the server
   * starts, e.g. to add filters. Default: no-op.
   */
  open fun configureContext(context: ServletContextHandler) {}


  open fun workerId(server: Server, host: String): String {
    val boundPort = (server.connectors.first() as ServerConnector).localPort
    val ownerHost = if (host == "0.0.0.0" || host == "::" || host.isBlank()) "localhost" else host
    return "$ownerHost:$boundPort"
  }

  /**
   * Resolves the landing page. An explicit `--files` / `--ui` / `--home` wins; otherwise
   * the most informative page that is actually mounted is used, so the gateway can never
   * redirect to a disabled mount.
   */
  private fun landingPathFor(landing: String?, homeEnabled: Boolean, uiEnabled: Boolean): String = when {
    landing == "files" -> "${SimpleFileServlet.FILES_PREFIX}/${SimpleFileServlet.ROOT_SEGMENT}/"
    landing == "ui" && uiEnabled -> "${SimpleFileServlet.UI_PREFIX}/"
    landing == "home" && homeEnabled -> "${FileServerCli.HOME_PREFIX}/"
    homeEnabled -> "${FileServerCli.HOME_PREFIX}/"
    uiEnabled -> "${SimpleFileServlet.UI_PREFIX}/"
    else -> "${SimpleFileServlet.FILES_PREFIX}/${SimpleFileServlet.ROOT_SEGMENT}/"
  }

  private fun register(
    context: ServletContextHandler,
    holder: ServletHolder,
    prefix: String
  ) {
    addServletSafely(context, holder, "$prefix/*")
    addServletSafely(context, holder, prefix)
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
      log.warn("Skipping servlet '{}' at {}: path already mapped", holder.name, pathSpec)
      return false
    }
    context.addServlet(holder, pathSpec)
    log.debug("Mapped servlet '{}' at {}", holder.name, pathSpec)
    return true
  }

  fun stop() {
    runningServer?.stop()
  }

  companion object {
    @Volatile
    var docProcessorServlet: CliDocProcessorServlet? = null
    var available: Map<String, ChatModel> = emptyMap()
    var models: CliSupport.Models? = null
    @Volatile
    var serverInfo: ServerInfo = ServerInfo()

  }
}