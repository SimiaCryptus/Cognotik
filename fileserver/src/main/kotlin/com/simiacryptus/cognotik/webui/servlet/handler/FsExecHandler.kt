package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.util.FsPath
import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * POST /.fsapi/v1/exec — the `child_process` backing store (nodejs.md §6.8).
 *
 * This is remote code execution by design, so it is:
 *  - disabled unless an allowlist is configured,
 *  - restricted to an allowlisted binary *and* sub-command,
 *  - never routed through a shell,
 *  - hardened against git argument-injection (`-c`, `--upload-pack`, ...),
 *  - bounded by a wall-clock timeout and an output cap.
 */
object FsExecHandler {
  private val log = LoggerFactory.getLogger(FsExecHandler::class.java)
  private const val MAX_OUTPUT = 1024 * 1024
  private val FORBIDDEN_ARG_PREFIXES = listOf(
    "-c", "--exec", "--upload-pack", "--receive-pack", "--upload-archive",
    "--config", "core.sshcommand", "core.pager", "core.editor", "--output"
  )

  fun handle(req: HttpServletRequest, resp: HttpServletResponse, root: File, config: FsApiConfig) {
    if (config.execAllowlist.isEmpty() && !config.execAllowAny) {
      throw FsException(FsErrorCode.ENOSYS, "exec", null, "exec capability disabled")
    }
    val body = FsJson.parseObject(req.reader.readText())
    val cmd = FsJson.string(body, "cmd")
      ?: throw FsException(FsErrorCode.EINVAL, "exec", null, "missing 'cmd'")
    val allowedSubcommands = config.execAllowlist[cmd]
      ?: if (config.execAllowAny) emptySet()
      else throw FsException(FsErrorCode.EACCES, "exec", cmd, "command '$cmd' is not allowlisted")
    val args = FsJson.list(body, "args").map { it?.toString() ?: "" }
    validate(cmd, args, allowedSubcommands, config.execRestrictArguments)

    val cwdTarget = FsPath.resolve(root, FsJson.string(body, "cwd") ?: "/", "exec")
    if (FileAccessControl.isHidden(root, cwdTarget.file)) {
      throw FsException(FsErrorCode.ENOENT, "exec", cwdTarget.virtual)
    }
    if (!cwdTarget.file.isDirectory) throw FsException(FsErrorCode.ENOTDIR, "exec", cwdTarget.virtual)

    log.info("exec ${listOf(cmd).plus(args).joinToString(" ")} in ${cwdTarget.file.absolutePath}")
    val builder = ProcessBuilder(listOf(cmd) + args)
      .directory(cwdTarget.file)
      .redirectErrorStream(false)
    builder.environment().remove("GIT_ASKPASS")
    builder.environment()["GIT_TERMINAL_PROMPT"] = "0"

    val process = try {
      builder.start()
    } catch (e: Exception) {
      throw FsException(FsErrorCode.ENOENT, "spawn", cmd, e.message)
    }
    val stderrBuffer = StringBuilder()
    val stderrThread = Thread {
      try {
        process.errorStream.bufferedReader().forEachLine { line ->
          if (stderrBuffer.length < MAX_OUTPUT) stderrBuffer.append(line).append('\n')
        }
      } catch (e: Exception) {
        // stream closed
      }
    }
    stderrThread.isDaemon = true
    stderrThread.start()
    val stdout = StringBuilder()
    try {
      process.inputStream.bufferedReader().forEachLine { line ->
        if (stdout.length < MAX_OUTPUT) stdout.append(line).append('\n')
      }
    } catch (e: Exception) {
      // stream closed
    }
    val finished = process.waitFor(config.execTimeoutMs, TimeUnit.MILLISECONDS)
    if (!finished) {
      process.destroyForcibly()
      throw FsException(FsErrorCode.EBUSY, "exec", cmd, "timed out after ${config.execTimeoutMs}ms")
    }
    stderrThread.join(1000)
    resp.status = HttpServletResponse.SC_OK
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(
      FsJson.stringify(
        linkedMapOf(
          "cmd" to cmd,
          "args" to args,
          "cwd" to cwdTarget.virtual,
          "code" to process.exitValue(),
          "signal" to null,
          "stdout" to stdout.toString(),
          "stderr" to stderrBuffer.toString(),
          "truncated" to (stdout.length >= MAX_OUTPUT || stderrBuffer.length >= MAX_OUTPUT)
        )
      )
    )
  }

  private fun validate(
    cmd: String,
    args: List<String>,
    allowedSubcommands: Set<String>,
    restrictArguments: Boolean,
  ) {
    if (cmd.any { it.code < 32 } || (restrictArguments && cmd.any { it == '/' || it == '\\' })) {
      throw FsException(FsErrorCode.EACCES, "exec", cmd, "command must be a bare allowlisted name")
    }
    if (allowedSubcommands.isNotEmpty()) {
      val sub = args.firstOrNull()
        ?: throw FsException(FsErrorCode.EINVAL, "exec", cmd, "missing sub-command")
      if (sub !in allowedSubcommands) {
        throw FsException(FsErrorCode.EACCES, "exec", cmd, "sub-command '$sub' is not allowlisted")
      }
    }
    for (arg in args) {
      if (arg.any { it == '\u0000' || it.code < 32 }) {
        throw FsException(FsErrorCode.EINVAL, "exec", cmd, "argument contains control characters")
      }
      if (!restrictArguments) continue
      val lowered = arg.lowercase()
      if (FORBIDDEN_ARG_PREFIXES.any { lowered == it || lowered.startsWith("$it=") }) {
        throw FsException(FsErrorCode.EACCES, "exec", cmd, "argument '$arg' is not permitted")
      }
    }
  }
}