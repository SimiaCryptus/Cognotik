package com.simiacryptus.cognotik.webui.servlet.action

    import com.simiacryptus.cognotik.fileserver.action.ActionMenu
    import com.simiacryptus.cognotik.fileserver.action.ActionParam
    import com.simiacryptus.cognotik.fileserver.action.ActionSelection
    import com.simiacryptus.cognotik.fileserver.action.ActionUi
    import com.simiacryptus.cognotik.fileserver.action.FsAction
    import com.simiacryptus.cognotik.fileserver.action.FsActionContext
    import com.simiacryptus.cognotik.fileserver.handler.FsErrorCode
    import jakarta.servlet.http.HttpServletResponse
    import java.io.ByteArrayOutputStream
    import java.io.OutputStream
    import java.io.PrintStream
    import java.util.concurrent.ConcurrentHashMap
    import java.util.concurrent.Executors
    import java.util.concurrent.TimeUnit
    import java.util.concurrent.atomic.AtomicBoolean
    import java.util.concurrent.atomic.AtomicLong
    import java.util.concurrent.locks.ReentrantLock

    /**
     * Task book-keeping for the long-running FS API actions (DocOps `run`, AutoFix), plus the
     * `tasks` operation used to poll them:
     *
     * ```
     * GET {mount}/.fsapi/v1/tasks
     * GET {mount}/.fsapi/v1/tasks?id=t3
     * ```
     *
     * Ported from the CLI file server. The invariants are unchanged:
     *
     *  1. **One task at a time.** The tools write to `System.out`, so execution is serialised
     *     behind a lock and stdout is *tee'd* into the task record while it runs.
     *  2. Pure/read-only commands answer synchronously; anything that mutates the workspace
     *     answers with a task id you poll.
     */
    object FsTasks {

      const val TASKS_OP = "tasks"

      private const val MAX_TASKS = 50
      private const val MAX_OUTPUT_CHARS = 400_000

      private val tasks = ConcurrentHashMap<String, TaskRecord>()
      private val order = mutableListOf<String>()
      private val ids = AtomicLong()
      private val ioLock = ReentrantLock()
      private val installed = AtomicBoolean(false)
      private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "cognotik-task").apply { isDaemon = true }
      }

      /** Idempotent: registers the `tasks` operation once. */
      @Synchronized
      fun install() {
        if (!installed.compareAndSet(false, true)) return
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
          ) { ctx -> handle(ctx) },
          replace = true,
        )
      }

      fun handle(ctx: FsActionContext) {
        val id = ctx.req.getParameter("id")?.trim()
        if (id.isNullOrEmpty()) {
          val snapshot = synchronized(order) { order.toList() }.mapNotNull { tasks[it] }
          FsJson.write(ctx.resp, 200, "{\"tasks\":[" + snapshot.joinToString(",") { taskJson(it, false) } + "]}")
          return
        }
        val record = tasks[id]
          ?: return FsJson.fail(ctx.resp, FsErrorCode.ENOENT, TASKS_OP, "no such task '$id'")
        FsJson.write(ctx.resp, 200, "{\"task\":" + taskJson(record, true) + "}")
      }

      /**
       * Runs [body] as a task and writes the task record as the response.
       *
       * @param async true = queue it and answer immediately with a pollable id.
       */
      fun dispatch(
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
          FsJson.write(resp, 200, "{\"task\":" + taskJson(record, true) + "}")
          return
        }
        // Pure commands answer inline; if a background run owns stdout, say so honestly.
        if (!runCaptured(record, 1_000, body)) {
          record.markFailed("a task is already running")
          return FsJson.fail(resp, FsErrorCode.EBUSY, kind, "another task is already running")
        }
        FsJson.write(resp, 200, "{\"task\":" + taskJson(record, true) + "}")
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
        sb.append("\"id\":\"").append(FsJson.esc(record.id)).append("\",")
        sb.append("\"kind\":\"").append(FsJson.esc(record.kind)).append("\",")
        sb.append("\"label\":\"").append(FsJson.esc(record.label)).append("\",")
        sb.append("\"state\":\"").append(FsJson.esc(record.state)).append("\",")
        sb.append("\"exitCode\":").append(record.exitCode?.toString() ?: "null").append(",")
        sb.append("\"startedMs\":").append(record.startedMs).append(",")
        sb.append("\"finishedMs\":").append(record.finishedMs?.toString() ?: "null")
        if (includeOutput) sb.append(",\"output\":\"").append(FsJson.esc(record.output())).append("\"")
        return sb.append("}").toString()
      }
    }