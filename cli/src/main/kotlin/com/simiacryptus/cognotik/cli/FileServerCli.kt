package com.simiacryptus.cognotik.cli

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

import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.io.File
import kotlin.system.exitProcess

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

  /** First path segment consumed by [FileServlet] (normally a session id). */

  /** Sends browsers landing on "/" (or "/files") to the served directory listing. */
  class RootRedirectServlet(private val target: String = "$FILES_PREFIX/$ROOT_SEGMENT/") : HttpServlet() {
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
      response.sendRedirect("${request.contextPath}$target")
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
                      --ui           Make the IDE-style SPA (/ui/) the landing page
                      --no-ui        Do not serve the SPA at all
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
    var uiDefault = false
    var terminalEnabled = true
    var execPermissive = true
    var shell: List<String> = emptyList()
    var dirArg: String? = null
    var tasksEnabled = true
    var taskRootArg: String? = null
    var smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL")
    var fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL")
    var imageModel: String? = System.getenv("COGNOTIK_IMAGE_MODEL")
    var audioModel: String? = System.getenv("COGNOTIK_AUDIO_MODEL")
    var taskTimeout = 30L
    var taskMonitor = false
    var fixCommand = ""
    var modifyEnabled = true
    var lineNumbers = false
    var chatPort = 8061

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


    /* Built after parsing so --email is honoured, then published for the FS actions. */
    val cliUser = CliSupport.defaultUser()
    bootstrapPlatform(cliUser)
    user = cliUser

    available = availableModels(cliUser)
    /* The pair is runtime state now: the web UI may replace it at any time. */
    ModelSelection.install(user = { FileServerCli.user }, smart = smartModel, fast = fastModel)
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
      ModifyFilesActions.install(
        ModifyFilesActions.Config(
          root = taskRoot,
          chatUri = { CognotikAppServer.getServer(host, chatPort).server.uri },
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
          user = FileServerCli.user,
          smartModel = ModelSelection.smart,
          fastModel = ModelSelection.fast,
          imageModel = imageModel,
          audioModel = audioModel,
          quiet = true,
        )
      } catch (e: Exception) {
        models
      }
      println("Models -> ${ModelSelection.summary()}")
    }


    val server = start(
      baseDir, host, port, gitEnabled, readOnly, uiEnabled, uiDefault,
      terminalEnabled, execPermissive, shell, tasksEnabled, fixCommand,
      modifyEnabled, lineNumbers
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
   *
   * Note: [tasksEnabled] only controls the *UI affordances*; the FS API operations
   * themselves are registered by [ServerTaskActions.install].
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
    tasksEnabled: Boolean = false,
    defaultFixCommand: String = "",
    modifyEnabled: Boolean = false,
    lineNumbers: Boolean = false,
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

    val showTasks = tasksEnabled && ServerTaskActions.isEnabled
    val showModify = modifyEnabled && !readOnly && ModifyFilesActions.isEnabled
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
    context.addServlet(fileHolder, "$FILES_PREFIX/*")

    /* ZIP downloads: session = directory name, resolved against the parent dir. */
    context.addServlet(
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
       context.addServlet(docopsHolder, "$DOCOPS_PREFIX/*")
       context.addServlet(docopsHolder, DOCOPS_PREFIX)
     }


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

}