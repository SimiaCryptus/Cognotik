package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.webui.servlet.action.ActionParam
import com.simiacryptus.cognotik.webui.servlet.action.ActionMenu
import com.simiacryptus.cognotik.webui.servlet.action.ActionOption
import com.simiacryptus.cognotik.webui.servlet.action.ActionSelection
import com.simiacryptus.cognotik.webui.servlet.action.ActionUi
import com.simiacryptus.cognotik.webui.servlet.action.FsAction
import com.simiacryptus.cognotik.webui.servlet.action.FsActionContext
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrors
import com.simiacryptus.cognotik.webui.servlet.handler.FsException
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Bridges the **DocOps** and **AutoFix** command line tools onto the file server's
 * FS API, so a browser sitting on a directory listing (or any HTTP client) can run
 * them against the served tree:
 *
 * ```
 * POST {mount}/.fsapi/v1/docops?command=plan
 * POST {mount}/.fsapi/v1/docops?command=run&path=docs/api.md
 * POST {mount}/.fsapi/v1/autofix?cmd=./gradlew%20build&dir=.
 * GET  {mount}/.fsapi/v1/tasks
 * GET  {mount}/.fsapi/v1/tasks?id=t3
 * ```
 *
 * Design notes:
 *
 *  1. **No new process.** Both tools are invoked in-process through their
 *     programmatic entry points ([DocOpsCli.run] / [AutoFixCli.run]); the platform
 *     bootstrap those functions perform is idempotent, so nothing happens at server
 *     start-up and a misconfigured model never breaks `FileServerCli`.
 *  2. **One task at a time.** The tools write to `System.out`, so execution is
 *     serialised behind a lock and stdout is *tee'd* into the task record while it
 *     runs. Pure/read-only commands answer synchronously; anything that mutates the
 *     workspace answers `202`-style with a task id you poll.
 *  3. **Read-only mounts stay read-only.** `docops run` and `autofix` are refused
 *     with `EROFS` when either the server or the FS API config is read-only.
 *  4. **Parameters are query parameters** (also accepted as a form-encoded body),
 *     so no JSON parser is needed and the shape is visible in
 *     `GET /.fsapi/v1/actions`.
 */
object ServerTaskActions {

  const val DOCOPS_OP = "docops"
  const val AUTOFIX_OP = "autofix"
  const val TASKS_OP = "tasks"

  /** Commands accepted by `?command=`; only `run` mutates the workspace. */
  private val DOCOPS_COMMANDS = listOf("plan", "run", "status", "vars", "models")

  private const val MAX_TASKS = 50
  private const val MAX_OUTPUT_CHARS = 400_000

  data class Config(
    /** Project root handed to the tools (defaults to the served directory). */
    val root: File,
    val readOnly: Boolean = false,
    val smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL"),
    val fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL"),
    val timeoutMinutes: Long = 30,
    /** true = let the tool start its own ephemeral monitor server. */
    val monitor: Boolean = false,
  )

  @Volatile
  private var config: Config? = null
  private val installed = AtomicBoolean(false)
  private val tasks = ConcurrentHashMap<String, TaskRecord>()
  private val order = mutableListOf<String>()
  private val ids = AtomicLong()
  private val ioLock = ReentrantLock()
  private val executor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "cognotik-task").apply { isDaemon = true }
  }

  val isEnabled: Boolean get() = config != null

  /** Idempotent: registers the actions once, and (re)installs the configuration. */
  @Synchronized
  fun install(cfg: Config) {
    config = cfg
    if (!installed.compareAndSet(false, true)) return
    FsAction.register(
      FsAction(
        op = DOCOPS_OP,
        method = "POST",
        description = "Run the DocOps CLI against the served tree",
        parameters = listOf(
          ActionParam(
            "command", required = false, default = "plan", label = "Command",
            description = "only \"run\" mutates the workspace", options = DOCOPS_COMMANDS
          ),
          ActionParam("path", required = false, description = "document or folder, relative to the root (repeatable)"),
          ActionParam("mode", required = false, label = "Update mode", description = "e.g. PatchToUpdate"),
          ActionParam(
           "target", required = false, label = "Targets",
           description = "only run the tasks producing these output files",
           multi = true, dynamic = true,
          ),
          ActionParam("var", required = false, description = "template variable override NAME=VALUE (repeatable)"),
        ),
        mutating = true,
        ui = ActionUi(
          title = "DocOps…", icon = "📘", category = "Cognotik",
          menus = listOf(
            ActionMenu("main/tools", "7_run", 40),
            ActionMenu("explorer/context", "7_run", 40),
          ),
          selection = ActionSelection(min = 0, kinds = listOf("file", "dir")),
          hiddenParams = setOf("path", "var"),
          sendSelection = "paths", selectionParam = "path",
        ),
       /* Live options for "target": scrapes `docops plan` for the selected files. */
       paramResolvers = mapOf("target" to ::resolveDocOpsTargets),
      ) { ctx -> handleDocOps(ctx) },
      replace = true,
    )
    FsAction.register(
      FsAction(
        op = AUTOFIX_OP,
        method = "POST",
        description = "Run a command and iteratively fix whatever it reports",
        parameters = listOf(
          ActionParam("cmd", required = true, label = "Command", description = "command line to run (repeatable)"),
          ActionParam("dir", required = false, description = "working directory, relative to the root"),
          ActionParam("autoFix", required = false, default = "true", description = "apply generated patches"),
          ActionParam("timeout", "int", required = false, label = "Timeout (minutes)"),
        ),
        mutating = true,
        ui = ActionUi(
          title = "AutoFix…", icon = "🩺", category = "Cognotik",
          menus = listOf(
            ActionMenu("main/tools", "7_run", 50),
            ActionMenu("explorer/context", "7_run", 50),
          ),
          selection = ActionSelection(min = 0, max = 1, kinds = listOf("file", "dir")),
          hiddenParams = setOf("dir", "autoFix"),
          sendSelection = "folder", selectionParam = "dir",
        ),
      ) { ctx -> handleAutoFix(ctx) },
      replace = true,
    )
    FsAction.register(
      FsAction(
        op = TASKS_OP,
        method = "GET",
        description = "List task invocations, or fetch one with its captured output",
        parameters = listOf(
          ActionParam("id", required = false, description = "task id; omit to list"),
        ),
        mutating = false,
        ui = ActionUi(
          title = "Cognotik Tasks", icon = "🗒", category = "Cognotik",
          menus = listOf(ActionMenu("main/tools", "7_run", 60)),
          selection = ActionSelection(min = 0, kinds = listOf("file", "dir")),
          hiddenParams = setOf("id"),
          sendSelection = "none",
        ),
      ) { ctx -> handleTasks(ctx) },
      replace = true,
    )
  }

  /*
   * ------------------------------------------------------------------
   * Handlers
   * ------------------------------------------------------------------
   */

  private fun handleDocOps(ctx: FsActionContext) {
   if (FsAction.serveParamResolution(ctx)) return
    val cfg = config ?: return fail(ctx.resp, FsErrorCode.ENOSYS, "docops", "task actions are not enabled")
    val command = (ctx.req.getParameter("command") ?: "plan").trim().lowercase()
    if (command !in DOCOPS_COMMANDS) {
      return fail(
        ctx.resp, FsErrorCode.EINVAL, "docops",
        "unknown command '$command' (known: ${DOCOPS_COMMANDS.joinToString(", ")})"
      )
    }
    val mutates = command == "run"
    if (mutates && (cfg.readOnly || ctx.config.readOnly)) {
      return fail(ctx.resp, FsErrorCode.EROFS, "docops", "this mount is read-only; 'run' is refused")
    }

    val argv = mutableListOf(command)
    ctx.req.getParameterValues("path")?.forEach { if (it.isNotBlank()) argv.add(it) }
    argv += listOf("--root", cfg.root.absolutePath)
    ctx.req.getParameter("mode")?.takeIf { it.isNotBlank() }?.let { argv += listOf("--mode", it) }
   ctx.req.getParameterValues("target")?.forEach { if (it.isNotBlank()) argv += listOf("--target", it) }
    ctx.req.getParameterValues("var")?.forEach { if (it.contains('=')) argv += listOf("--var", it) }
    cfg.smartModel?.takeIf { it.isNotBlank() }?.let { argv += listOf("--smart-model", it) }
    cfg.fastModel?.takeIf { it.isNotBlank() }?.let { argv += listOf("--fast-model", it) }
    // Embedded execution: no browser, no second web server unless explicitly asked for.
    if (!cfg.monitor) argv += "--serverless"

    val label = "docops $command" + (ctx.req.getParameterValues("path")?.joinToString(" ", " ") ?: "")
    dispatch(ctx.resp, kind = "docops", label = label, async = mutates) {
      DocOpsCli.run(argv.toTypedArray())
    }
  }
/**
  * Live options for the "target" checkbox list (§ActionParam.dynamic): asks
  * DocOps, in read-only "plan" mode, what it would produce for the selected
  * documents, then scrapes "-> target" / "- target" style lines out of the
  * (human-oriented) plan output. Best-effort: an empty result just means an
  * empty checkbox list, never a failed dialog.
  */
private fun resolveDocOpsTargets(ctx: FsActionContext): List<ActionOption> {
   val cfg = config ?: return emptyList()
   val paths = ctx.req.getParameterValues("path").orEmpty().filter { it.isNotBlank() }
   if (paths.isEmpty()) return emptyList()
   val argv = mutableListOf("plan")
   argv += paths
   argv += listOf("--root", cfg.root.absolutePath, "--serverless")
   if (!ioLock.tryLock(2, TimeUnit.SECONDS)) return emptyList()
   val buffer = ByteArrayOutputStream()
   val previousOut = System.out
   val previousErr = System.err
   try {
     val stream = PrintStream(buffer, true, "UTF-8")
     System.setOut(stream)
     System.setErr(stream)
     try {
       DocOpsCli.run(argv.toTypedArray())
     } catch (e: Throwable) {
       return emptyList()
     } finally {
       stream.flush()
     }
   } finally {
     System.setOut(previousOut)
     System.setErr(previousErr)
     ioLock.unlock()
   }
   val text = buffer.toString("UTF-8")
   val targets = LinkedHashSet<String>()
   val arrow = Regex("""->\s*(\S.*\.\w+)\s*$""")
   val bullet = Regex("""^\s*[-*]\s+(\S.*\.\w+)\s*$""")
   text.lineSequence().forEach { line ->
     arrow.find(line)?.groupValues?.get(1)?.trim()?.let { targets.add(it) }
     bullet.find(line)?.groupValues?.get(1)?.trim()?.let { targets.add(it) }
   }
   return targets.map { ActionOption(it) }
}


  private fun handleAutoFix(ctx: FsActionContext) {
    val cfg = config ?: return fail(ctx.resp, FsErrorCode.ENOSYS, "autofix", "task actions are not enabled")
    if (cfg.readOnly || ctx.config.readOnly) {
      return fail(ctx.resp, FsErrorCode.EROFS, "autofix", "this mount is read-only")
    }
    val commands = ctx.req.getParameterValues("cmd").orEmpty().filter { it.isNotBlank() }
    if (commands.isEmpty()) {
      return fail(ctx.resp, FsErrorCode.EINVAL, "autofix", "missing 'cmd' parameter")
    }
    val dir = ctx.req.getParameter("dir")?.trim().orEmpty()
    val timeout = ctx.req.getParameter("timeout")?.toLongOrNull() ?: cfg.timeoutMinutes

    val argv = mutableListOf("--root", cfg.root.absolutePath)
    if (dir.isNotBlank() && dir != "/" && dir != ".") argv += listOf("--dir", dir)
    commands.forEach { argv += listOf("--cmd", it) }
    cfg.smartModel?.takeIf { it.isNotBlank() }?.let { argv += listOf("--smart-model", it) }
    cfg.fastModel?.takeIf { it.isNotBlank() }?.let { argv += listOf("--fast-model", it) }
    argv += listOf("-t", timeout.coerceAtLeast(1).toString())
    // There is nobody to click an approval link, so a headless run must auto-apply.
    if (!cfg.monitor) argv += "--serverless"

    dispatch(ctx.resp, kind = "autofix", label = commands.joinToString(" && "), async = true) {
      AutoFixCli.run(argv.toTypedArray())
    }
  }

  private fun handleTasks(ctx: FsActionContext) {
    val id = ctx.req.getParameter("id")?.trim()
    if (id.isNullOrEmpty()) {
      val snapshot = synchronized(order) { order.toList() }.mapNotNull { tasks[it] }
      writeJson(ctx.resp, 200, "{\"tasks\":[" + snapshot.joinToString(",") { taskJson(it, false) } + "]}")
      return
    }
    val record = tasks[id]
      ?: return fail(ctx.resp, FsErrorCode.ENOENT, "tasks", "no such task '$id'")
    writeJson(ctx.resp, 200, "{\"task\":" + taskJson(record, true) + "}")
  }

  /*
   * ------------------------------------------------------------------
   * Execution
   * ------------------------------------------------------------------
   */

  private fun dispatch(
    resp: HttpServletResponse,
    kind: String,
    label: String,
    async: Boolean,
    body: () -> Int,
  ) {
    val record = newTask(kind, label)
    if (async) {
      executor.submit {
        if (!runCaptured(record, TimeUnit.HOURS.toMillis(1), body)) {
          record.markFailed("another task held the console for too long")
        }
      }
      writeJson(resp, 200, "{\"task\":" + taskJson(record, true) + "}")
      return
    }
    // Pure commands answer inline; if a background run owns stdout, say so honestly.
    if (!runCaptured(record, 1_000, body)) {
      record.markFailed("a task is already running")
      return fail(resp, FsErrorCode.EBUSY, kind, "another task is already running")
    }
    writeJson(resp, 200, "{\"task\":" + taskJson(record, true) + "}")
  }

  /** Runs [body] with stdout/stderr tee'd into [record]; false = could not get the lock. */
  private fun runCaptured(record: TaskRecord, waitMillis: Long, body: () -> Int): Boolean {
    if (!ioLock.tryLock(waitMillis, TimeUnit.MILLISECONDS)) return false
    val previousOut = System.out
    val previousErr = System.err
    val stream = PrintStream(TeeStream(previousOut, record.sink()), true, "UTF-8")
    try {
      System.setOut(stream)
      System.setErr(stream)
      val code = body()
      record.exitCode = code
      record.state = if (code == 0) "done" else "failed"
    } catch (e: Throwable) {
      stream.println("task failed: ${e.javaClass.simpleName}: ${e.message ?: e.toString()}")
      record.exitCode = 1
      record.state = "failed"
    } finally {
      stream.flush()
      System.setOut(previousOut)
      System.setErr(previousErr)
      record.finishedMs = System.currentTimeMillis()
      ioLock.unlock()
    }
    return true
  }

  private fun newTask(kind: String, label: String): TaskRecord {
    val record = TaskRecord("t" + ids.incrementAndGet(), kind, label.trim())
    tasks[record.id] = record
    synchronized(order) {
      order.add(record.id)
      while (order.size > MAX_TASKS) {
        val victim = order.firstOrNull { tasks[it]?.state != "running" } ?: break
        order.remove(victim)
        tasks.remove(victim)
      }
    }
    return record
  }

  /*
   * ------------------------------------------------------------------
   * Records and rendering
   * ------------------------------------------------------------------
   */

  private class TaskRecord(val id: String, val kind: String, val label: String) {
    val startedMs: Long = System.currentTimeMillis()

    @Volatile
    var finishedMs: Long? = null

    @Volatile
    var state: String = "running"

    @Volatile
    var exitCode: Int? = null
    private val buffer = ByteArrayOutputStream()

    fun sink(): OutputStream = object : OutputStream() {
      override fun write(b: Int) = synchronized(buffer) { buffer.write(b) }
      override fun write(b: ByteArray, off: Int, len: Int) = synchronized(buffer) { buffer.write(b, off, len) }
    }

    fun output(): String {
      val text = synchronized(buffer) { buffer.toString("UTF-8") }
      return if (text.length <= MAX_OUTPUT_CHARS) text
      else "... (${text.length - MAX_OUTPUT_CHARS} characters trimmed)\n" + text.takeLast(MAX_OUTPUT_CHARS)
    }

    fun markFailed(message: String) {
      synchronized(buffer) { buffer.write(message.toByteArray(Charsets.UTF_8)) }
      exitCode = 1
      state = "failed"
      finishedMs = System.currentTimeMillis()
    }
  }

  private class TeeStream(val primary: OutputStream, val secondary: OutputStream) : OutputStream() {
    override fun write(b: Int) {
      primary.write(b); secondary.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      primary.write(b, off, len); secondary.write(b, off, len)
    }

    override fun flush() {
      primary.flush(); secondary.flush()
    }
  }

  private fun taskJson(record: TaskRecord, includeOutput: Boolean): String {
    val sb = StringBuilder("{")
    sb.append("\"id\":\"").append(esc(record.id)).append("\",")
    sb.append("\"kind\":\"").append(esc(record.kind)).append("\",")
    sb.append("\"label\":\"").append(esc(record.label)).append("\",")
    sb.append("\"state\":\"").append(esc(record.state)).append("\",")
    sb.append("\"exitCode\":").append(record.exitCode?.toString() ?: "null").append(",")
    sb.append("\"startedMs\":").append(record.startedMs).append(",")
    sb.append("\"finishedMs\":").append(record.finishedMs?.toString() ?: "null")
    if (includeOutput) sb.append(",\"output\":\"").append(esc(record.output())).append("\"")
    return sb.append("}").toString()
  }

  private fun writeJson(resp: HttpServletResponse, status: Int, body: String) {
    resp.status = status
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(body)
  }

  private fun fail(resp: HttpServletResponse, code: FsErrorCode, syscall: String, message: String) {
    FsErrors.write(resp, FsException(code, syscall, null, message))
  }

  private fun esc(s: String): String {
    val sb = StringBuilder(s.length + 16)
    for (c in s) when {
      c == '"' -> sb.append("\\\"")
      c == '\\' -> sb.append("\\\\")
      c == '\n' -> sb.append("\\n")
      c == '\r' -> sb.append("\\r")
      c == '\t' -> sb.append("\\t")
      c < ' ' -> sb.append(String.format("\\u%04x", c.code))
      else -> sb.append(c)
    }
    return sb.toString()
  }
}