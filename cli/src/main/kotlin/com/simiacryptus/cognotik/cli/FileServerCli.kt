package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.webui.servlet.FilesystemServlet
import com.simiacryptus.cognotik.webui.servlet.StaticZipServlet
import com.simiacryptus.cognotik.webui.servlet.WebUiServlet
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
 * GET  {mount}/.fsapi/v1/tasks[?id=t1]
 * ```
 *
 * The classic directory listing grows matching affordances: per-document
 * *Plan* / *Run* links for markdown files, an *AutoFix…* toolbar button, and a
 * live output panel that polls the task endpoint.
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
    /** true = render the DocOps/AutoFix affordances (the actions must be installed). */
    private val tasksEnabled: Boolean = false,
    /** Pre-filled command in the AutoFix prompt. */
    private val defaultFixCommand: String = "",
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
      val ide = if (!uiEnabled) "" else {
        val hash = if (currentPath.isBlank()) "/" else "/$currentPath/"
        """<a class="zip-link" style="background-color:#6f42c1;" href="${req.contextPath}$UI_PREFIX/#$hash">🧭 Open in IDE view</a>"""
      }
      if (!tasksEnabled) return ide
      val fix = if (readOnly) "" else
        """<a class="zip-link" style="background-color:#d63384;" href="#" onclick="return cognotikAutoFix(event)">🩺 AutoFix…</a>"""
      return ide +
          """<a class="zip-link" style="background-color:#0d6efd;" href="#" onclick="return cognotikDocOps(event,'plan','')">📘 DocOps plan</a>""" +
          fix +
          """<a class="zip-link" style="background-color:#495057;" href="#" onclick="return cognotikTasks(event)">🗒 Tasks</a>"""
    }

    /** Markdown documents get direct DocOps entry points. */
    override fun getFileActions(file: File, req: HttpServletRequest): String {
      if (!tasksEnabled) return ""
      if (file.extension.lowercase() !in setOf("md", "markdown")) return ""
      val rel = escapeJs(relativeToBase(file))
      val plan =
        """<a class="action-link" href="#" title="Plan doc-ops for this document" onclick="return cognotikDocOps(event,'plan','$rel')">📘 Plan</a>"""
      if (readOnly) return plan
      return plan +
          """<a class="action-link" href="#" title="Run doc-ops for this document" onclick="return cognotikDocOps(event,'run','$rel')">🚀 Run</a>"""
    }

    override fun getAdditionalSections(dir: File?, req: HttpServletRequest, currentPath: String): String {
      if (!tasksEnabled) return ""
      val base = "${req.contextPath}$FILES_PREFIX/$ROOT_SEGMENT/.fsapi/v1"
      return """
              <script>
                window.COGNOTIK_FSAPI = "${escapeJs(base)}";
                window.COGNOTIK_PATH = "${escapeJs(currentPath)}";
                window.COGNOTIK_FIX_CMD = "${escapeJs(defaultFixCommand)}";
              </script>
              <section id="cognotik-tasks" class="cognotik-tasks" style="display:none;">
                <h3 style="margin-top:0;">Cognotik tasks</h3>
                <div id="cognotik-task-status" class="cognotik-task-status">idle</div>
                <pre id="cognotik-task-output" class="cognotik-task-output"></pre>
              </section>
          """.trimIndent()
    }

    override fun getAdditionalStyles(): String {
      if (!tasksEnabled) return ""
      return """
              .cognotik-tasks { margin: 1rem 0; padding: 0.75rem 1rem; border: 1px solid #6f42c1; border-radius: 6px; }
              .cognotik-task-status { font-weight: 600; margin-bottom: 0.5rem; }
              .cognotik-task-output { max-height: 24rem; overflow: auto; white-space: pre-wrap;
                  font-family: monospace; font-size: 0.85rem; margin: 0; }
          """.trimIndent()
    }

    override fun getAdditionalScripts(): String {
      if (!tasksEnabled) return ""
      /* Plain ES5, no template literals: this string is also a Kotlin raw string. */
      return """
              function cognotikPanel() {
                var el = document.getElementById('cognotik-tasks');
                if (el) { el.style.display = 'block'; }
                return el;
              }
              function cognotikSetOutput(text) {
                cognotikPanel();
                var pre = document.getElementById('cognotik-task-output');
                if (pre) { pre.textContent = text; pre.scrollTop = pre.scrollHeight; }
              }
              function cognotikStatus(text) {
                cognotikPanel();
                var el = document.getElementById('cognotik-task-status');
                if (el) { el.textContent = text; }
              }
              function cognotikUrl(op, params) {
                var url = (window.COGNOTIK_FSAPI || '') + '/' + op;
                var qs = [];
                for (var k in params) {
                  if (!Object.prototype.hasOwnProperty.call(params, k)) continue;
                  var v = params[k];
                  if (v === null || v === undefined || v === '') continue;
                  if (Object.prototype.toString.call(v) === '[object Array]') {
                    for (var i = 0; i < v.length; i++) {
                      qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v[i]));
                    }
                  } else {
                    qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
                  }
                }
                if (qs.length) url += '?' + qs.join('&');
                return url;
              }
              function cognotikCall(method, op, params) {
                return fetch(cognotikUrl(op, params), { method: method, headers: { 'X-Fs-Api': '1' } })
                  .then(function (r) {
                    return r.json().catch(function () { return {}; })
                      .then(function (j) { return { status: r.status, body: j }; });
                  });
              }
              function cognotikRender(res) {
                var body = (res && res.body) || {};
                if (body.error) {
                  cognotikStatus('error: ' + (body.error.code || '') + ' ' + (body.error.message || ''));
                  cognotikSetOutput(JSON.stringify(body.error, null, 2));
                  return null;
                }
                var task = body.task || body;
                var code = (task.exitCode === null || task.exitCode === undefined) ? '' : ' (exit ' + task.exitCode + ')';
                cognotikStatus('[' + task.id + '] ' + task.kind + ' ' + task.label + ' - ' + task.state + code);
                cognotikSetOutput(task.output || '(no output yet)');
                return task;
              }
              function cognotikPoll(id) {
                cognotikCall('GET', 'tasks', { id: id }).then(function (res) {
                  var task = cognotikRender(res);
                  if (task && task.state === 'running') {
                    setTimeout(function () { cognotikPoll(id); }, 1500);
                  }
                }).catch(function (e) { cognotikStatus('poll failed: ' + e); });
              }
              function cognotikDocOps(ev, command, path) {
                if (ev) ev.preventDefault();
                cognotikStatus('docops ' + command + (path ? ' ' + path : '') + ' ...');
                cognotikSetOutput('');
                cognotikCall('POST', 'docops', { command: command, path: path }).then(function (res) {
                  var task = cognotikRender(res);
                  if (task && task.state === 'running') cognotikPoll(task.id);
                }).catch(function (e) { cognotikStatus('request failed: ' + e); });
                return false;
              }
              function cognotikAutoFix(ev) {
                if (ev) ev.preventDefault();
                var cmd = window.prompt('Command to run and fix:', window.COGNOTIK_FIX_CMD || '');
                if (!cmd) return false;
                window.COGNOTIK_FIX_CMD = cmd;
                cognotikStatus('autofix: ' + cmd + ' ...');
                cognotikSetOutput('');
                cognotikCall('POST', 'autofix', { cmd: cmd, dir: window.COGNOTIK_PATH || '' }).then(function (res) {
                  var task = cognotikRender(res);
                  if (task && task.state === 'running') cognotikPoll(task.id);
                }).catch(function (e) { cognotikStatus('request failed: ' + e); });
                return false;
              }
              function cognotikTasks(ev) {
                if (ev) ev.preventDefault();
                cognotikCall('GET', 'tasks', {}).then(function (res) {
                  var list = ((res && res.body) || {}).tasks || [];
                  cognotikStatus(list.length + ' task(s) - click an id below and poll with ?id=');
                  cognotikSetOutput(list.map(function (t) {
                    return t.id + '  ' + t.state + '  ' + t.kind + '  ' + t.label;
                  }).join('\n') || '(no tasks yet)');
                }).catch(function (e) { cognotikStatus('request failed: ' + e); });
                return false;
              }
          """.trimIndent()
    }

    private fun relativeToBase(file: File): String = try {
      file.canonicalFile.relativeTo(baseDir.canonicalFile).path.replace(File.separatorChar, '/')
    } catch (e: Exception) {
      file.name
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
    tasksEnabled: Boolean = false,
  ) : SimpleFileServlet(
    baseDir, gitEnabled, readOnly = true, uiEnabled = uiEnabled,
    terminalEnabled = false, execPermissive = execPermissive, tasksEnabled = tasksEnabled,
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
                      --secure       Shorthand for --read-only --no-terminal --no-exec --no-tasks
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

                  'docops run' and 'autofix' mutate the workspace, run in the background and
                  return a task id; everything else answers inline. Both are refused with
                  EROFS on a read-only mount.

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
    var tasksEnabled = true
    var taskRootArg: String? = null
    var smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL")
    var fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL")
    var taskTimeout = 30L
    var taskMonitor = false
    var fixCommand = ""

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
        }

        "--no-tasks" -> tasksEnabled = false
        "--tasks" -> tasksEnabled = true
        "--task-root" -> taskRootArg = args.getOrNull(++i) ?: fail("Missing value for $arg")
        "--smart-model" -> smartModel = args.getOrNull(++i) ?: fail("Missing value for $arg")
        "--fast-model" -> fastModel = args.getOrNull(++i) ?: fail("Missing value for $arg")
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

    val baseDir = File(dirArg ?: ".").canonicalFile
    if (!baseDir.exists() || !baseDir.isDirectory) {
      fail("Not a directory: ${baseDir.absolutePath}")
    }
    val taskRoot = (taskRootArg?.let { File(it) } ?: baseDir).canonicalFile
    if (tasksEnabled && !taskRoot.isDirectory) {
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

    val server = start(
      baseDir, host, port, gitEnabled, readOnly, uiEnabled, uiDefault,
      terminalEnabled, execPermissive, shell, tasksEnabled, fixCommand
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
    if (tasksEnabled) {
      val apiBase = "http://$displayHost:$boundPort$FILES_PREFIX/$ROOT_SEGMENT/.fsapi/v1"
      println("  Tasks     -> docops/autofix enabled (root ${taskRoot.absolutePath})")
      println("               POST $apiBase/docops?command=plan")
      println("               POST $apiBase/autofix?cmd=<command>")
      println("               GET  $apiBase/tasks")
      if (readOnly) println("               (read-only mount: 'docops run' and 'autofix' answer EROFS)")
      if (smartModel == null) {
        println("               NOTE: no smart model selected; set --smart-model or COGNOTIK_SMART_MODEL")
      }
    } else {
      println("  Tasks     -> disabled")
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
    val fileServlet = if (readOnly) ReadOnlyFileServlet(baseDir, gitEnabled, uiEnabled, execPermissive, showTasks)
    else SimpleFileServlet(
      baseDir, gitEnabled, readOnly = false, uiEnabled = uiEnabled,
      terminalEnabled = terminalEnabled, execPermissive = execPermissive, shell = shell,
      tasksEnabled = showTasks, defaultFixCommand = defaultFixCommand
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

  private fun escapeJs(s: String): String = s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("'", "\\'")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

  private fun fail(message: String): Nothing {
    System.err.println("error: $message")
    System.err.println()
    System.err.println(usage())
    exitProcess(2)
  }
}