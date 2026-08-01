package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.action.FsActionContext
import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import com.simiacryptus.cognotik.webui.servlet.util.FsPath
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Interactive shell / command sessions for FS API v1.
 *
 * `POST   /.fsapi/v1/terminal`         start a session (shell, or a single command)
 * `GET    /.fsapi/v1/terminal`         list sessions
 * `GET    /.fsapi/v1/terminal/stream`  SSE output stream (resumable via ?from=<seq>)
 * `POST   /.fsapi/v1/terminal/input`   write to stdin
 * `POST   /.fsapi/v1/terminal/resize`  record cols/rows
 * `POST   /.fsapi/v1/terminal/signal`  terminate the process
 * `DELETE /.fsapi/v1/terminal`         close a session
 *
 * Limitations (documented, not bugs): the JVM has no portable PTY, so the child
 * is attached to *pipes*. There is therefore no terminal echo, no job control and
 * no SIGINT delivery to a foreground job — the client performs local line editing
 * and CR is translated to LF here. Everything else (prompts, colour, streaming)
 * works because the shell is started interactively.
 *
 * This is arbitrary code execution by design: it is gated behind
 * `FsApiConfig.terminalEnabled`, refused in read-only mode, and bounded by
 * `maxTerminals` plus an idle reaper.
 */
object FsTerminalHandler {
  private val log = LoggerFactory.getLogger(FsTerminalHandler::class.java)
  private const val MAX_BUFFER_CHARS = 256 * 1024
  private const val REAP_AFTER_EXIT_MS = 5 * 60_000L
  private val sessions = ConcurrentHashMap<String, Session>()
  private val counter = AtomicLong()

  class Chunk(val seq: Long, val stream: String, val data: String)

  class Session(
    val id: String,
    val label: String,
    val cwd: String,
    val command: List<String>,
    val process: Process,
  ) {
    val lock = Object()
    private val chunks = ArrayList<Chunk>()
    private var buffered = 0
    var nextSeq = 0L
      private set

    @Volatile
    var exitCode: Int? = null

    @Volatile
    var cols = 80

    @Volatile
    var rows = 24

    @Volatile
    var lastActivityMs = System.currentTimeMillis()

    @Volatile
    var finishedAtMs = 0L
    val startedAtMs = System.currentTimeMillis()

    fun append(stream: String, text: String) {
      if (text.isEmpty()) return
      synchronized(lock) {
        chunks.add(Chunk(nextSeq++, stream, text))
        buffered += text.length
        /* Ring buffer: a late subscriber gets the tail, never the whole history. */
        while (buffered > MAX_BUFFER_CHARS && chunks.size > 1) buffered -= chunks.removeAt(0).data.length
        lastActivityMs = System.currentTimeMillis()
        lock.notifyAll()
      }
    }

    fun since(from: Long): List<Chunk> = synchronized(lock) { chunks.filter { it.seq >= from } }

    fun finish(code: Int) {
      exitCode = code
      finishedAtMs = System.currentTimeMillis()
      synchronized(lock) { lock.notifyAll() }
    }

    fun describe(): Map<String, Any?> = linkedMapOf(
      "id" to id,
      "label" to label,
      "cwd" to cwd,
      "command" to command,
      "cols" to cols,
      "rows" to rows,
      "pid" to pidOrNull(),
      "running" to (exitCode == null),
      "exitCode" to exitCode,
      "startedAtMs" to startedAtMs,
      "nextSeq" to nextSeq,
      "tty" to false
    )

    private fun pidOrNull(): Long? = try {
      process.pid()
    } catch (e: Throwable) {
      null
    }
  }

  // ------------------------------------------------------------- endpoints

  fun list(ctx: FsActionContext) {
    reap()
    writeJson(
      ctx.resp, HttpServletResponse.SC_OK,
      linkedMapOf(
        "sessions" to sessions.values.sortedBy { it.startedAtMs }.map { it.describe() },
        "max" to ctx.config.maxTerminals,
        "enabled" to ctx.config.terminalEnabled
      )
    )
  }

  fun create(ctx: FsActionContext) {
    requireEnabled(ctx, "open")
    reap()
    if (sessions.size >= ctx.config.maxTerminals) {
      throw FsException(
        FsErrorCode.EMFILE, "terminal", null,
        "too many terminal sessions (max ${ctx.config.maxTerminals})"
      )
    }
    val body = FsJson.parseObject(ctx.req.reader.readText())
    val target = FsPath.resolve(ctx.root, FsJson.string(body, "cwd") ?: "/", "terminal")
    if (FileAccessControl.isHidden(ctx.root, target.file)) {
      throw FsException(FsErrorCode.ENOENT, "terminal", target.virtual)
    }
    if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, "terminal", target.virtual)
    if (!target.file.isDirectory) throw FsException(FsErrorCode.ENOTDIR, "terminal", target.virtual)

    val cmd = FsJson.string(body, "cmd")
    val command = if (cmd.isNullOrBlank()) defaultShell(ctx.config)
    else listOf(cmd) + FsJson.list(body, "args").map { it?.toString() ?: "" }
    validate(command)

    val cols = (FsJson.int(body, "cols") ?: 80).coerceIn(20, 500)
    val rows = (FsJson.int(body, "rows") ?: 24).coerceIn(5, 200)
    val builder = ProcessBuilder(command).directory(target.file).redirectErrorStream(true)
    builder.environment().apply {
      put("TERM", "dumb")
      put("PS1", "\\w\$ ")
      put("PAGER", "cat")
      put("GIT_PAGER", "cat")
      put("GIT_TERMINAL_PROMPT", "0")
      put("COLUMNS", cols.toString())
      put("LINES", rows.toString())
      remove("GIT_ASKPASS")
    }
    val process = try {
      builder.start()
    } catch (e: Exception) {
      throw FsException(FsErrorCode.ENOENT, "spawn", command.firstOrNull(), e.message)
    }
    val id = "t${counter.incrementAndGet()}${System.nanoTime().toString(16)}"
    val label = FsJson.string(body, "label") ?: target.file.name.ifBlank { "/" }
    val session = Session(id, label, target.virtual, command, process)
    session.cols = cols
    session.rows = rows
    sessions[id] = session
    pump(session)
    session.append("system", "${command.joinToString(" ")}   [${target.virtual}]\n")
    log.info("terminal $id started: ${command.joinToString(" ")} in ${target.file.absolutePath}")
    writeJson(ctx.resp, HttpServletResponse.SC_CREATED, session.describe())
  }

  fun stream(ctx: FsActionContext) {
    requireEnabled(ctx, "read")
    val session = lookup(ctx.req.getParameter("id"))
    var cursor = ctx.req.getParameter("from")?.toLongOrNull() ?: 0L
    val resp = ctx.resp
    resp.status = HttpServletResponse.SC_OK
    resp.contentType = "text/event-stream"
    resp.characterEncoding = "UTF-8"
    resp.setHeader("Cache-Control", "no-cache, no-transform")
    resp.setHeader("Connection", "keep-alive")
    resp.setHeader("X-Accel-Buffering", "no")
    resp.flushBuffer()

    val async = ctx.req.startAsync()
    async.timeout = 0
    val thread = Thread({
      try {
        send(resp, "ready", session.describe())
        while (!Thread.currentThread().isInterrupted) {
          val pending = synchronized(session.lock) {
            if (session.since(cursor).isEmpty() && session.exitCode == null) session.lock.wait(10_000)
            session.since(cursor)
          }
          for (chunk in pending) {
            send(resp, "data", linkedMapOf("seq" to chunk.seq, "stream" to chunk.stream, "data" to chunk.data))
            cursor = chunk.seq + 1
          }
          if (pending.isEmpty()) {
            resp.writer.write(": heartbeat\n\n")
            resp.writer.flush()
          }
          if (resp.writer.checkError()) break
          if (session.exitCode != null && session.since(cursor).isEmpty()) {
            send(resp, "exit", linkedMapOf("code" to session.exitCode))
            break
          }
        }
      } catch (e: InterruptedException) {
        // shutdown
      } catch (e: Exception) {
        log.debug("terminal stream ${session.id} terminated", e)
      } finally {
        try {
          async.complete()
        } catch (e: Exception) {
          // best effort
        }
      }
    }, "fs-terminal-sse-${session.id}")
    thread.isDaemon = true
    thread.start()
  }

  fun input(ctx: FsActionContext) {
    requireEnabled(ctx, "write")
    val body = FsJson.parseObject(ctx.req.reader.readText())
    val session = lookup(FsJson.string(body, "id"))
    if (session.exitCode != null) {
      throw FsException(FsErrorCode.EINVAL, "terminal", session.id, "session has exited")
    }
    /* No PTY: a bare CR would never terminate a line for the child shell. */
    val data = (FsJson.string(body, "data") ?: "").replace("\r\n", "\n").replace('\r', '\n')
    try {
      val out = session.process.outputStream
      out.write(data.toByteArray(StandardCharsets.UTF_8))
      out.flush()
    } catch (e: IOException) {
      throw FsException(FsErrorCode.EIO, "terminal", session.id, e.message)
    }
    session.lastActivityMs = System.currentTimeMillis()
    ctx.resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  fun resize(ctx: FsActionContext) {
    requireEnabled(ctx, "write")
    val body = FsJson.parseObject(ctx.req.reader.readText())
    val session = lookup(FsJson.string(body, "id"))
    session.cols = (FsJson.int(body, "cols") ?: session.cols).coerceIn(20, 500)
    session.rows = (FsJson.int(body, "rows") ?: session.rows).coerceIn(5, 200)
    ctx.resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  fun signal(ctx: FsActionContext) {
    requireEnabled(ctx, "write")
    val body = FsJson.parseObject(ctx.req.reader.readText())
    val session = lookup(FsJson.string(body, "id"))
    val name = (FsJson.string(body, "signal") ?: "SIGTERM").uppercase()
    if (name == "SIGKILL") session.process.destroyForcibly() else session.process.destroy()
    writeJson(ctx.resp, HttpServletResponse.SC_OK, linkedMapOf("id" to session.id, "signal" to name))
  }

  fun close(ctx: FsActionContext) {
    val id = ctx.req.getParameter("id")
      ?: throw FsException(FsErrorCode.EINVAL, "terminal", null, "missing 'id'")
    val session = sessions.remove(id)
    session?.process?.destroyForcibly()
    ctx.resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  // --------------------------------------------------------------- helpers

  private fun requireEnabled(ctx: FsActionContext, syscall: String) {
    if (!ctx.config.terminalEnabled) {
      throw FsException(FsErrorCode.ENOSYS, syscall, null, "terminal capability disabled for this mount")
    }
    if (ctx.config.readOnly) {
      throw FsException(FsErrorCode.EROFS, syscall, null, "the server is read-only")
    }
  }

  private fun lookup(id: String?): Session {
    if (id.isNullOrBlank()) throw FsException(FsErrorCode.EINVAL, "terminal", null, "missing 'id'")
    return sessions[id] ?: throw FsException(FsErrorCode.ENOENT, "terminal", id, "no such terminal session")
  }

  private fun pump(session: Session) {
    val reader = Thread({
      try {
        val stream = session.process.inputStream.reader(StandardCharsets.UTF_8)
        val buffer = CharArray(4096)
        while (true) {
          val read = stream.read(buffer)
          if (read < 0) break
          session.append("stdout", String(buffer, 0, read))
        }
      } catch (e: Exception) {
        log.debug("terminal ${session.id} reader ended", e)
      } finally {
        val code = try {
          session.process.waitFor()
        } catch (e: InterruptedException) {
          -1
        }
        session.append("system", "\n[process exited with code $code]\n")
        session.finish(code)
      }
    }, "fs-terminal-${session.id}")
    reader.isDaemon = true
    reader.start()
  }

  private fun defaultShell(config: FsApiConfig): List<String> {
    if (config.terminalShell.isNotEmpty()) return config.terminalShell
    if (FsApiConfig.platform() == "win32") {
      return listOf(System.getenv("COMSPEC") ?: "cmd.exe")
    }
    val shell = sequenceOf(System.getenv("SHELL"), "/bin/bash", "/bin/sh")
      .filterNotNull()
      .firstOrNull { it.isNotBlank() && File(it).canExecute() } ?: "/bin/sh"
    return if (shell.endsWith("bash")) listOf(shell, "--noprofile", "--norc", "-i") else listOf(shell, "-i")
  }

  private fun validate(command: List<String>) {
    if (command.isEmpty() || command.first().isBlank()) {
      throw FsException(FsErrorCode.EINVAL, "terminal", null, "empty command")
    }
    for (arg in command) {
      if (arg.any { it == '\u0000' }) {
        throw FsException(FsErrorCode.EINVAL, "terminal", null, "argument contains NUL")
      }
    }
  }

  private fun reap() {
    val now = System.currentTimeMillis()
    val expired = sessions.values.filter { session ->
      (session.exitCode != null && now - session.finishedAtMs > REAP_AFTER_EXIT_MS)
    }
    for (session in expired) sessions.remove(session.id)
  }

  private fun send(resp: HttpServletResponse, event: String, payload: Map<String, Any?>) {
    val writer = resp.writer
    writer.write("event: $event\n")
    writer.write("data: ${FsJson.stringify(payload)}\n\n")
    writer.flush()
  }

  private fun writeJson(resp: HttpServletResponse, status: Int, payload: Any?) {
    if (resp.isCommitted) return
    resp.status = status
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(FsJson.stringify(payload))
  }
}