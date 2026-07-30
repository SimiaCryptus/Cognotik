package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.action.ActionParam
import com.simiacryptus.cognotik.webui.servlet.action.FsAction
import com.simiacryptus.cognotik.webui.servlet.action.FsActionContext

import com.simiacryptus.cognotik.webui.servlet.util.EtagUtil
import com.simiacryptus.cognotik.webui.servlet.util.FileChannelCache
import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import com.simiacryptus.cognotik.webui.servlet.util.FsPath
import com.simiacryptus.cognotik.webui.servlet.util.FsTarget
import com.simiacryptus.cognotik.webui.servlet.util.MimeTypeResolver
import com.simiacryptus.cognotik.webui.servlet.util.RangeUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.UncheckedIOException
import java.nio.channels.ClosedChannelException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.util.Base64

/**
 * Routing + dispatch for FS API v1 (nodejs.md §5, Appendix A).
 *
 * Security invariants (nodejs.md §9):
 *  - every op resolves paths through FsPath (lexical + canonical containment)
 *  - every op consults FileAccessControl.isHidden (=> ENOENT) and, when
 *    mutating, FileAccessControl.isReadOnly (=> EACCES)
 *  - two-path ops (rename/copy) validate *both* endpoints
 *  - all mutations invalidate FileChannelCache (fixes D2 for this API)
 */
object FsApiHandler {
  const val API_VERSION = 1
  private val log = LoggerFactory.getLogger(FsApiHandler::class.java)
  private val WRITE_FLAGS = setOf("w", "wx", "a", "ax", "r+", "w+", "a+")
  private const val OCTET_STREAM = "application/octet-stream"
  /** Types that can execute script/markup in the serving origin when rendered inline. */
  private val SCRIPTABLE_TYPES = setOf(
    "text/html", "application/xhtml+xml", "image/svg+xml",
    "application/xml", "text/xml", "application/mathml+xml"
  )
  private val MIME_TOKEN = Regex("^[A-Za-z0-9!#\$%&'*+.^_`|~-]+/[A-Za-z0-9!#\$%&'*+.^_`|~-]+$")
  /** Substrings that identify a disconnected client rather than a server fault. */
  private val CLIENT_ABORT_HINTS = listOf(
    "broken pipe", "connection reset", "connection closed", "closed channel",
    "early eof", "aborted", "cancelled"
  )

  /**
   * Every operation is a registered [FsAction] (a `DynamicEnum` constant).
   * That makes the operation set extensible (`FsAction.register(...)`) and
   * self-describing (`GET /.fsapi/v1/actions`).
   */
  init {
    val path = ActionParam("path", required = true, description = "virtual path, '/'-relative to the served root")
    read("meta", "API version, platform, limits and capabilities") { ctx ->
      writeJson(ctx.resp, HttpServletResponse.SC_OK, meta(ctx.config))
    }
    read("actions", "Self-description of every registered file and git action") { ctx ->
      writeJson(ctx.resp, HttpServletResponse.SC_OK, describeActions(ctx.config))
    }
    read(
      "stat", "fs.stat / fs.lstat",
      listOf(path, ActionParam("lstat", "boolean"), ActionParam("throwIfNoEntry", "boolean", default = true))
    ) { ctx -> httpStat(ctx.req, ctx.resp, ctx.root) }
    read(
      "dir", "fs.readdir",
      listOf(path, ActionParam("recursive", "boolean"), ActionParam("depth", "int"), ActionParam("stat", "boolean"))
    ) { ctx -> httpReaddir(ctx.req, ctx.resp, ctx.root, ctx.config) }
    read(
      "file", "fs.readFile / createReadStream (supports Range and ETag)",
      listOf(
        path,
        ActionParam(
          "mime",
          description = "'binary' (default for XHR), 'auto' for the detected type, or an explicit type"
        ),
        ActionParam("download", "boolean", description = "force Content-Disposition: attachment")
      )
    ) { ctx ->
      httpReadFile(ctx.req, ctx.resp, ctx.root, ctx.method == "HEAD")
    }
    read("realpath", "fs.realpath", listOf(path)) { ctx ->
      writeJson(ctx.resp, HttpServletResponse.SC_OK, opRealpath(ctx.root, ctx.req.getParameter("path")))
    }
    read(
      "resolve", "CommonJS/ESM module resolution",
      listOf(ActionParam("request", required = true), ActionParam("from")), capability = "resolve"
    ) { ctx -> FsResolveHandler.handle(ctx.req, ctx.resp, ctx.root, ctx.config) }
    read(
      "snapshot", "Zip snapshot of a subtree",
      listOf(path, ActionParam("maxBytes", "long")), capability = "snapshot"
    ) { ctx -> FsSnapshotHandler.handle(ctx.req, ctx.resp, ctx.root, ctx.config) }
    read(
      "watch", "SSE change stream (fs.watch)",
      listOf(path, ActionParam("recursive", "boolean")), capability = "watch"
    ) { ctx -> FsWatchHandler.handle(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "stat", "Batch fs.stat",
      listOf(
        ActionParam("paths", "array", required = true, location = "body"),
        ActionParam("lstat", "boolean", location = "body")
      )
    ) { ctx -> httpStatBatch(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "dir", "fs.mkdir",
      listOf(
        ActionParam("path", required = true, location = "body"),
        ActionParam("recursive", "boolean", location = "body")
      )
    ) { ctx -> httpMkdir(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "rename", "fs.rename",
      listOf(
        ActionParam("from", required = true, location = "body"),
        ActionParam("to", required = true, location = "body"),
        ActionParam("overwrite", "boolean", location = "body", default = true)
      )
    ) { ctx -> httpRename(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "copy", "fs.copyFile / fs.cp",
      listOf(
        ActionParam("from", required = true, location = "body"),
        ActionParam("to", required = true, location = "body"),
        ActionParam("recursive", "boolean", location = "body"),
        ActionParam("force", "boolean", location = "body", default = true),
        ActionParam("preserveTimestamps", "boolean", location = "body")
      )
    ) { ctx -> httpCopy(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "truncate", "fs.truncate",
      listOf(
        ActionParam("path", required = true, location = "body"),
        ActionParam("len", "long", location = "body", default = 0)
      )
    ) { ctx -> httpTruncate(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "utimes", "fs.utimes",
      listOf(
        ActionParam("path", required = true, location = "body"),
        ActionParam("atimeMs", "long", location = "body"),
        ActionParam("mtimeMs", "long", location = "body")
      ),
      capability = "utimes"
    ) { ctx -> httpUtimes(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "batch", "Pipeline several operations in one round trip",
      listOf(
        ActionParam("ops", "array", required = true, location = "body"),
        ActionParam("stopOnError", "boolean", location = "body")
      )
    ) { ctx -> httpBatch(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "exec", "Run an allowlisted child_process command",
      listOf(
        ActionParam("cmd", required = true, location = "body"),
        ActionParam("args", "array", location = "body"),
        ActionParam("cwd", location = "body")
      ),
      capability = "exec"
    ) { ctx -> FsExecHandler.handle(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "POST", "git", "Run a registered git action (see the 'git' section of /actions)",
      listOf(
        ActionParam("action", required = true, location = "body"),
        ActionParam("params", "object", location = "body")
      ),
      capability = "git"
    ) { ctx -> httpGit(ctx) }
    read("terminal", "List interactive terminal sessions", capability = "terminal") { ctx ->
      FsTerminalHandler.list(ctx)
    }
    FsAction.register(
      FsAction(
        "terminal/stream", "GET",
        "SSE output stream for a terminal session (resumable via ?from=<seq>)",
        listOf(ActionParam("id", required = true), ActionParam("from", "long")),
        requiresCapability = "terminal"
      ) { ctx -> FsTerminalHandler.stream(ctx) }
    )
    write(
      "POST", "terminal", "Start an interactive shell (or a single command) session",
      listOf(
        ActionParam("cwd", location = "body", description = "virtual working directory", default = "/"),
        ActionParam("cmd", location = "body", description = "omit for the default shell"),
        ActionParam("args", "array", location = "body"),
        ActionParam("label", location = "body"),
        ActionParam("cols", "int", location = "body", default = 80),
        ActionParam("rows", "int", location = "body", default = 24)
      ),
      capability = "terminal"
    ) { ctx -> FsTerminalHandler.create(ctx) }
    write(
      "POST", "terminal/input", "Write to a session's stdin",
      listOf(
        ActionParam("id", required = true, location = "body"),
        ActionParam("data", required = true, location = "body")
      ),
      capability = "terminal"
    ) { ctx -> FsTerminalHandler.input(ctx) }
    write(
      "POST", "terminal/resize", "Record the client's cols/rows",
      listOf(
        ActionParam("id", required = true, location = "body"),
        ActionParam("cols", "int", location = "body"),
        ActionParam("rows", "int", location = "body")
      ),
      capability = "terminal"
    ) { ctx -> FsTerminalHandler.resize(ctx) }
    write(
      "POST", "terminal/signal", "Terminate the child process",
      listOf(
        ActionParam("id", required = true, location = "body"),
        ActionParam("signal", location = "body", default = "SIGTERM")
      ),
      capability = "terminal"
    ) { ctx -> FsTerminalHandler.signal(ctx) }
    write(
      "DELETE", "terminal", "Close a terminal session",
      listOf(ActionParam("id", required = true)),
      capability = "terminal"
    ) { ctx -> FsTerminalHandler.close(ctx) }
    write(
      "PUT", "file", "fs.writeFile / createWriteStream (supports Content-Range and If-Match)",
      listOf(path, ActionParam("flag", default = "w"), ActionParam("position", "long"))
    ) { ctx -> httpWriteFile(ctx.req, ctx.resp, ctx.root, ctx.config) }
    write(
      "DELETE", "file", "fs.unlink / fs.rm / fs.rmdir",
      listOf(path, ActionParam("recursive", "boolean"), ActionParam("force", "boolean"))
    ) { ctx -> httpRemove(ctx.req, ctx.resp, ctx.root, ctx.config) }
    FsAction.register(FsAction("", "OPTIONS", "Advertise the permitted methods") { ctx ->
      ctx.resp.setHeader("Allow", "GET,HEAD,POST,PUT,DELETE,OPTIONS")
      ctx.resp.status = HttpServletResponse.SC_NO_CONTENT
    })
  }

  private fun read(
    op: String,
    description: String,
    parameters: List<ActionParam> = emptyList(),
    capability: String? = null,
    handler: (FsActionContext) -> Unit,
  ) {
    FsAction.register(FsAction(op, "GET", description, parameters, requiresCapability = capability, handler = handler))
    FsAction.register(FsAction(op, "HEAD", description, parameters, requiresCapability = capability, handler = handler))
  }

  private fun write(
    method: String,
    op: String,
    description: String,
    parameters: List<ActionParam> = emptyList(),
    capability: String? = null,
    handler: (FsActionContext) -> Unit,
  ) = FsAction.register(
    FsAction(op, method, description, parameters, requiresCapability = capability, handler = handler)
  )


  fun handle(
    method: String,
    op: String,
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File?,
    config: FsApiConfig,
  ) {
    val syscall = op.ifBlank { "fsapi" }
    val normalizedMethod = method.ifBlank { "GET" }.uppercase()
    val startedNanos = System.nanoTime()
    val tag = requestTag(normalizedMethod, op, req)
    try {
      if (log.isDebugEnabled) log.debug("FS API -> {}", tag)
      resp.setHeader("X-Fs-Api", API_VERSION.toString())
      if (root == null || !root.exists() || !root.isDirectory) {
        log.warn("FS API {} rejected: served root is unavailable (root={})", tag, root?.absolutePath)
        throw FsException(FsErrorCode.ENOENT, syscall, "/", "served root is unavailable")
      }
      val action = FsAction.find(normalizedMethod, op)
        ?: (if (normalizedMethod == "OPTIONS") FsAction.find("OPTIONS", "") else null)
      if (action == null) {
        log.info("FS API {} -> unsupported operation", tag)
        throw unknown(normalizedMethod, op)
      }
      if (action.mutating && config.requireApiHeader && req.getHeader("X-Fs-Api").isNullOrBlank()) {
        log.warn("FS API {} rejected: missing X-Fs-Api request header", tag)
        throw FsException(FsErrorCode.EACCES, syscall, null, "missing X-Fs-Api request header")
      }





      action.handler(FsActionContext(normalizedMethod, op, req, resp, root, config))
      if (log.isDebugEnabled) {
        log.debug("FS API <- {} status={} in {}ms", tag, resp.status, elapsedMs(startedNanos))
      }
    } catch (e: Exception) {
      if (isClientAbort(e)) {
        /* The peer went away mid-transfer: nothing to report, and nothing to write to. */
        log.debug("FS API {} aborted by client after {}ms: {}", tag, elapsedMs(startedNanos), e.toString())
        return
      }
      when (e) {
        is FsException ->
          log.debug("FS API {} -> {} ({}ms)", tag, e.message ?: e.javaClass.simpleName, elapsedMs(startedNanos))
        is IllegalArgumentException ->
          log.info("FS API {} rejected after {}ms: {}", tag, elapsedMs(startedNanos), e.message)
        else ->
          log.error("FS API {} failed after {}ms", tag, elapsedMs(startedNanos), e)
      }
      writeError(resp, tag, toFsException(syscall, null, e))
    } catch (e: StackOverflowError) {
      /* Guards the recursive walkers (collect/copyTree/invalidateTree) against pathological trees. */
      log.error("FS API {} exhausted the stack (deep or cyclic directory tree?)", tag, e)
      writeError(resp, tag, FsException(FsErrorCode.EIO, syscall, null, "directory tree too deep"))
    } catch (e: Throwable) {
      log.error("FS API {} failed fatally", tag, e)
      throw e
    }
  }

  private fun unknown(method: String, op: String) =
    FsException(FsErrorCode.ENOSYS, op.ifBlank { "fsapi" }, null, "unsupported operation: $method /$op")
  // ------------------------------------------------- diagnostics & error mapping
  private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000
  /**
   * Compact, log-safe request identity. Only reads query parameters for methods whose
   * bodies the container will never consume as a form, so that `req.reader` stays intact.
   */
  private fun requestTag(method: String, op: String, req: HttpServletRequest): String = try {
    buildString {
      append(method).append(" /").append(op.ifBlank { "" })
      if (method == "GET" || method == "HEAD" || method == "DELETE") {
        req.getParameter("path")?.takeIf { it.isNotBlank() }?.let { append(" path=").append(it) }
      }
      req.remoteAddr?.let { append(" from=").append(it) }
    }
  } catch (e: Exception) {
    log.debug("unable to build request tag", e)
    "$method /$op"
  }
  /**
   * Single translation point from JVM failures to the errno-shaped API contract.
   * Deliberately reports the *virtual* path (or none) so that no host path escapes.
   */
  private fun toFsException(syscall: String, path: String?, e: Throwable): FsException = when (e) {
    is FsException -> e
    is java.nio.file.NoSuchFileException -> FsException(FsErrorCode.ENOENT, syscall, path)
    is FileNotFoundException -> FsException(FsErrorCode.ENOENT, syscall, path)
    is FileAlreadyExistsException -> FsException(FsErrorCode.EEXIST, syscall, path)
    is DirectoryNotEmptyException -> FsException(FsErrorCode.ENOTEMPTY, syscall, path)
    is AccessDeniedException -> FsException(FsErrorCode.EACCES, syscall, path, "denied by the host filesystem")
    is SecurityException -> FsException(FsErrorCode.EACCES, syscall, path, "denied by the security manager")
    is NumberFormatException -> FsException(FsErrorCode.EINVAL, syscall, path, "malformed numeric parameter")
    is IllegalArgumentException -> FsException(FsErrorCode.EINVAL, syscall, path, e.message)
    is UncheckedIOException -> toFsException(syscall, path, e.cause ?: e as Throwable)
    is ClosedChannelException -> FsException(FsErrorCode.EIO, syscall, path, "stream closed")
    is IOException -> FsException(FsErrorCode.EIO, syscall, path, e.message ?: e.javaClass.simpleName)
    is InterruptedException -> {
      Thread.currentThread().interrupt()
      FsException(FsErrorCode.EIO, syscall, path, "interrupted")
    }
    else -> FsException(FsErrorCode.EIO, syscall, path, e.message ?: e.javaClass.simpleName)
  }
  /** True when the failure is the peer disconnecting, not a server-side fault. */
  private fun isClientAbort(error: Throwable): Boolean {
    var cursor: Throwable? = error
    var hops = 0
    while (cursor != null && hops++ < 8) {
      if (cursor is ClosedChannelException || cursor is InterruptedIOException) return true
      if (cursor.javaClass.simpleName.contains("Eof", ignoreCase = true)) return true
      val message = cursor.message?.lowercase()
      if (message != null && CLIENT_ABORT_HINTS.any { message.contains(it) }) return true
      cursor = cursor.cause.takeIf { it !== cursor }
    }
    return false
  }
  /** Never blow up while reporting a failure, and never corrupt an already-started body. */
  private fun writeError(resp: HttpServletResponse, tag: String, e: FsException) {
    if (resp.isCommitted) {
      log.warn("FS API {}: response already committed, dropping error payload ({})", tag, e.message)
      return
    }
    try {
      FsErrors.write(resp, e)
    } catch (io: Exception) {
      log.warn("FS API {}: failed to write error response", tag, io)
    }
  }
  /** Attaches the syscall + virtual path to any raw IO failure inside [block]. */
  private inline fun <T> fsIo(syscall: String, path: String?, block: () -> T): T = try {
    block()
  } catch (e: FsException) {
    throw e
  } catch (e: Exception) {
    if (isClientAbort(e)) log.debug("FS API {} on {} aborted by client", syscall, path)
    else log.warn("FS API {} failed on {}", syscall, path, e)
    throw toFsException(syscall, path, e)
  }
  /** Tolerant, well-reported JSON body reader (empty body == `{}`). */
  private fun readBody(req: HttpServletRequest, syscall: String): Map<String, Any?> {
    val raw = try {
      req.reader.readText()
    } catch (e: Exception) {
      log.debug("FS API {}: unable to read request body", syscall, e)
      throw toFsException(syscall, null, e)
    }
    return try {
      FsJson.parseObject(raw.ifBlank { "{}" })
    } catch (e: Exception) {
      log.debug("FS API {}: malformed JSON body ({} bytes)", syscall, raw.length, e)
      throw FsException(FsErrorCode.EINVAL, syscall, null, "malformed JSON body: ${e.message}")
    }
  }

  // ---------------------------------------------------------------- meta

  private fun meta(config: FsApiConfig): Map<String, Any?> = linkedMapOf(
    "apiVersion" to API_VERSION,
    "platform" to FsApiConfig.platform(),
    "sep" to "/",
    "caseSensitive" to config.caseSensitive,
    "cwd" to config.cwd,
    "tmpdir" to config.tmpdir,
    "homedir" to "/",
    "root" to "/",
    "readOnly" to config.readOnly,
    "limits" to linkedMapOf(
      "maxFileSize" to config.maxFileSize,
      "maxRequestSize" to config.maxRequestSize,
      "maxBatchOps" to config.maxBatchOps,
      "maxDirEntries" to config.maxDirEntries,
      "maxDepth" to config.maxDepth,
      "maxSnapshotBytes" to config.maxSnapshotBytes,
      "maxTerminals" to config.maxTerminals
    ),
    "capabilities" to linkedMapOf(
      "range" to true,
      "conditional" to true,
      "batch" to true,
      "statBatch" to true,
      "actions" to true,
      "git" to config.execAllowlist.containsKey("git"),
      "resolve" to config.resolveEnabled,
      "snapshot" to config.snapshotEnabled,
      "watch" to config.watchMode,
      "utimes" to config.utimesEnabled,
      "symlink" to false,
      "chmod" to false,
      "exec" to if (config.execAllowAny) listOf("*") else config.execAllowlist.keys.sorted(),
      "terminal" to config.terminalEnabled,
      "sync" to config.syncStrategy,
      "crossOriginIsolated" to config.crossOriginIsolated
    )
  )
  // ------------------------------------------------------- self-description
  /** Payload for `GET /.fsapi/v1/actions` — the registry, not a hard-coded list. */
  private fun describeActions(config: FsApiConfig): Map<String, Any?> = linkedMapOf(
    "apiVersion" to API_VERSION,
    "capabilities" to meta(config)["capabilities"],
    /* Presentation-bearing actions, in the shape the web UI registers directly. */
    "actions" to FsAction.values().sortedBy { it.name }.mapNotNull { it.uiDescriptor() },
    "fs" to FsAction.values().sortedBy { it.name }.map { it.describe() },
    "git" to GitActions.describe()
  )


  // -------------------------------------------------------------- helpers

  private fun writeJson(resp: HttpServletResponse, status: Int, payload: Any?) {
    if (resp.isCommitted) {
      log.warn("skipping JSON payload (status {}): response already committed", status)
      return
    }
    resp.status = status
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(FsJson.stringify(payload))
  }

  private fun target(root: File, path: String?, syscall: String, default: String? = null): FsTarget {
    val raw = if (path.isNullOrEmpty()) default else path
    if (raw == null) throw FsException(FsErrorCode.EINVAL, syscall, null, "missing 'path'")
    val resolved = FsPath.resolve(root, raw, syscall)
    if (FileAccessControl.isHidden(root, resolved.file)) {
      throw FsException(FsErrorCode.ENOENT, syscall, resolved.virtual)
    }
    return resolved
  }

  private fun requireExisting(target: FsTarget, syscall: String): FsTarget {
    if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, syscall, target.virtual)
    return target
  }

  private fun requireWritable(root: File, target: FsTarget, syscall: String, config: FsApiConfig) {
    if (config.readOnly) throw FsException(FsErrorCode.EROFS, syscall, target.virtual)
    if (FileAccessControl.isReadOnly(root, target.file)) {
      throw FsException(FsErrorCode.EACCES, syscall, target.virtual)
    }
  }

  private fun requireWritableParent(root: File, target: FsTarget, syscall: String, create: Boolean) {
    val parent = target.file.parentFile ?: return
    if (parent.exists() && !parent.isDirectory) {
      throw FsException(FsErrorCode.ENOTDIR, syscall, target.virtual, "parent is not a directory")
    }
    if (!parent.exists()) {
      if (!create) throw FsException(FsErrorCode.ENOENT, syscall, target.virtual, "parent directory does not exist")
      if (!parent.mkdirs() && !parent.exists()) {
        throw FsException(FsErrorCode.EIO, syscall, target.virtual, "failed to create parent directories")
      }
    }
    if (FileAccessControl.isReadOnly(root, parent)) {
      throw FsException(FsErrorCode.EACCES, syscall, target.virtual, "parent directory is read-only")
    }
  }

  private fun boolParam(req: HttpServletRequest, name: String, default: Boolean = false): Boolean {
    val value = req.getParameter(name) ?: return default
    return value.isEmpty() || value.equals("true", true) || value == "1"
  }
  /**
   * True when the browser is *navigating* to this URL (top-level, iframe, embed…).
   * Such responses must carry a real content type, otherwise Chrome turns them into a
   * download and the frame stays blank ("No resource with the given identifier found").
   */
  private fun isNavigation(req: HttpServletRequest): Boolean {
    val dest = req.getHeader("Sec-Fetch-Dest")?.trim()?.lowercase()
    if (!dest.isNullOrEmpty()) {
      return dest == "document" || dest == "iframe" || dest == "frame" ||
          dest == "embed" || dest == "object"
    }
    /* Clients without Sec-Fetch-* metadata: fall back to Accept negotiation. */
    val accept = req.getHeader("Accept")?.lowercase() ?: return false
    return accept.startsWith("text/html") || accept.contains("application/xhtml+xml")
  }
  /**
   * `?mime=` / `?download=` win; otherwise navigations get the detected type and
   * programmatic readers keep the historical `application/octet-stream`.
   */
  private fun negotiateContentType(req: HttpServletRequest, resolved: String, virtual: String): String {
    if (boolParam(req, "download")) return OCTET_STREAM
    val requested = req.getParameter("mime")?.trim()
    return when {
      requested.isNullOrEmpty() -> if (isNavigation(req)) resolved else OCTET_STREAM
      requested.equals("binary", true) || requested.equals("raw", true) ||
          requested.equals("false", true) -> OCTET_STREAM
      requested.equals("auto", true) || requested.equals("true", true) -> resolved
      MIME_TOKEN.matches(requested) -> requested
      else -> throw FsException(FsErrorCode.EINVAL, "read", virtual, "malformed 'mime' parameter")
    }
  }
  private fun baseType(contentType: String) = contentType.substringBefore(';').trim().lowercase()
  /** RFC 6266-safe `filename` (plus `filename*` for non-ASCII names). */
  private fun contentDisposition(inline: Boolean, name: String): String {
    val ascii = name.map { if (it.code in 32..126 && it != '"' && it != '\\') it else '_' }.joinToString("")
    val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    return buildString {
      append(if (inline) "inline" else "attachment")
      append("; filename=\"").append(ascii).append('"')
      if (ascii != name) append("; filename*=UTF-8''").append(encoded)
    }
  }


  private fun copyStream(
    input: InputStream,
    sink: (ByteArray, Int) -> Unit,
    limit: Long,
    syscall: String,
    path: String
  ): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      total += read
      if (total > limit) throw FsException(FsErrorCode.EFBIG, syscall, path, "exceeds maxFileSize ($limit)")
      sink(buffer, read)
    }
    return total
  }

  private fun assertSubtreeWritable(root: File, dir: File, syscall: String) {
    val children = dir.listFiles() ?: return
    for (child in children) {
      if (FileAccessControl.isReadOnly(root, child)) {
        throw FsException(FsErrorCode.EACCES, syscall, FsPath.virtualPath(root, child), "read-only descendant")
      }
      if (child.isDirectory) assertSubtreeWritable(root, child, syscall)
    }
  }

  private fun invalidateTree(file: File) {
    if (file.isDirectory) {
      file.listFiles()?.forEach { invalidateTree(it) }
    } else {
      FileChannelCache.invalidate(file)
    }
  }

  // ------------------------------------------------------------ core ops
  // Each op* is HTTP-agnostic so /batch can reuse it verbatim.

  private fun opStat(root: File, path: String?, lstat: Boolean, throwIfNoEntry: Boolean): Map<String, Any?> {
    val syscall = if (lstat) "lstat" else "stat"
    val t = target(root, path, syscall, "/")
    if (!t.file.exists()) {
      if (throwIfNoEntry) throw FsException(FsErrorCode.ENOENT, syscall, t.virtual)
      return linkedMapOf("path" to t.virtual, "exists" to false)
    }
    val payload = FsStat.payload(root, t.file, t.virtual, followLinks = !lstat)
    payload["exists"] = true
    return payload
  }

  private fun opReaddir(
    root: File,
    config: FsApiConfig,
    path: String?,
    recursive: Boolean,
    depth: Int,
    includeStat: Boolean,
  ): Map<String, Any?> {
    val t = requireExisting(target(root, path, "scandir", "/"), "scandir")
    if (!t.file.isDirectory) throw FsException(FsErrorCode.ENOTDIR, "scandir", t.virtual)
    val entries = ArrayList<Any?>()
    val effectiveDepth = if (recursive) depth.coerceIn(1, config.maxDepth) else 1
    collect(root, config, t.file, "", effectiveDepth, includeStat, entries)
    return linkedMapOf(
      "path" to t.virtual,
      "entries" to entries,
      "truncated" to (entries.size >= config.maxDirEntries)
    )
  }

  private fun collect(
    root: File,
    config: FsApiConfig,
    dir: File,
    prefix: String,
    depth: Int,
    includeStat: Boolean,
    out: ArrayList<Any?>,
  ) {
    if (depth <= 0 || out.size >= config.maxDirEntries) return
    val children = dir.listFiles()?.sortedBy { it.name } ?: return
    for (child in children) {
      if (out.size >= config.maxDirEntries) return
      if (FileAccessControl.isHidden(root, child)) continue
      val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
      out.add(FsStat.dirent(root, child, child.name, relative, includeStat))
      if (child.isDirectory) collect(root, config, child, relative, depth - 1, includeStat, out)
    }
  }

  private fun opMkdir(root: File, config: FsApiConfig, path: String?, recursive: Boolean): Map<String, Any?> {
    val t = target(root, path, "mkdir")
    requireWritable(root, t, "mkdir", config)
    if (t.file.exists()) {
      if (!t.file.isDirectory) throw FsException(FsErrorCode.EEXIST, "mkdir", t.virtual)
      if (!recursive) throw FsException(FsErrorCode.EEXIST, "mkdir", t.virtual)
      return linkedMapOf("path" to t.virtual, "created" to false)
    }
    requireWritableParent(root, t, "mkdir", create = recursive)
    val created = if (recursive) t.file.mkdirs() else t.file.mkdir()
    if (!created && !t.file.isDirectory) throw FsException(FsErrorCode.EIO, "mkdir", t.virtual)
    return linkedMapOf("path" to t.virtual, "created" to true)
  }

  private fun opRemove(
    root: File,
    config: FsApiConfig,
    path: String?,
    recursive: Boolean,
    force: Boolean,
  ): Map<String, Any?> {
    val resolved = FsPath.resolve(root, path, "unlink")
    if (FileAccessControl.isHidden(root, resolved.file) || !resolved.file.exists()) {
      if (force) return linkedMapOf("path" to resolved.virtual, "removed" to false)
      throw FsException(FsErrorCode.ENOENT, "unlink", resolved.virtual)
    }
    requireWritable(root, resolved, "unlink", config)
    if (resolved.file.isDirectory) {
      val children = resolved.file.listFiles() ?: emptyArray()
      if (children.isNotEmpty() && !recursive) {
        throw FsException(FsErrorCode.ENOTEMPTY, "rmdir", resolved.virtual)
      }
      if (recursive) assertSubtreeWritable(root, resolved.file, "rm")
      invalidateTree(resolved.file)
      val ok = fsIo("rmdir", resolved.virtual) {
        if (recursive) resolved.file.deleteRecursively() else resolved.file.delete()
      }
      if (!ok && resolved.file.exists()) throw FsException(FsErrorCode.EIO, "rmdir", resolved.virtual)
    } else {
      FileChannelCache.invalidate(resolved.file)
      if (!resolved.file.delete() && resolved.file.exists()) {
        throw FsException(FsErrorCode.EIO, "unlink", resolved.virtual)
      }
    }
    return linkedMapOf("path" to resolved.virtual, "removed" to true)
  }

  private fun opRename(
    root: File,
    config: FsApiConfig,
    from: String?,
    to: String?,
    overwrite: Boolean,
  ): Map<String, Any?> {
    val source = requireExisting(target(root, from, "rename"), "rename")
    val dest = target(root, to, "rename")
    requireWritable(root, source, "rename", config)
    requireWritable(root, dest, "rename", config)
    requireWritableParent(root, dest, "rename", create = false)
    if (source.file.isDirectory) assertSubtreeWritable(root, source.file, "rename")
    if (dest.file.exists() && !overwrite) throw FsException(FsErrorCode.EEXIST, "rename", dest.virtual)
    invalidateTree(source.file)
    if (dest.file.exists()) invalidateTree(dest.file)
    try {
      try {
        Files.move(
          source.file.toPath(), dest.file.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          *(if (overwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray())
        )
      } catch (e: AtomicMoveNotSupportedException) {
        log.debug("atomic move unsupported for {} -> {}, retrying non-atomically", source.virtual, dest.virtual, e)
        Files.move(
          source.file.toPath(), dest.file.toPath(),
          *(if (overwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray())
        )
      }
    } catch (e: FileAlreadyExistsException) {
      log.debug("rename {} -> {} rejected: destination exists", source.virtual, dest.virtual)
      throw FsException(FsErrorCode.EEXIST, "rename", dest.virtual)
    } catch (e: IOException) {
      if (isClientAbort(e)) log.debug("rename {} -> {} aborted", source.virtual, dest.virtual)
      else log.warn("rename {} -> {} failed", source.virtual, dest.virtual, e)
      throw FsException(FsErrorCode.EIO, "rename", dest.virtual, e.message)
    }
    return linkedMapOf("from" to source.virtual, "to" to dest.virtual)
  }

  private fun opCopy(
    root: File,
    config: FsApiConfig,
    from: String?,
    to: String?,
    recursive: Boolean,
    force: Boolean,
    preserveTimestamps: Boolean,
  ): Map<String, Any?> {
    val source = requireExisting(target(root, from, "copyfile"), "copyfile")
    val dest = target(root, to, "copyfile")
    requireWritable(root, dest, "copyfile", config)
    requireWritableParent(root, dest, "copyfile", create = true)
    if (source.file.isDirectory && !recursive) throw FsException(FsErrorCode.EISDIR, "copyfile", source.virtual)
    if (dest.file.exists() && !force) throw FsException(FsErrorCode.EEXIST, "copyfile", dest.virtual)
    val copied = copyTree(root, config, source.file, dest.file, force, preserveTimestamps)
    return linkedMapOf("from" to source.virtual, "to" to dest.virtual, "files" to copied)
  }

  private fun copyTree(
    root: File,
    config: FsApiConfig,
    source: File,
    dest: File,
    force: Boolean,
    preserveTimestamps: Boolean,
  ): Int {
    // Hidden sources are never copied: that would leak content past .hidden.
    if (FileAccessControl.isHidden(root, source)) return 0
    if (source.isDirectory) {
      if (!dest.exists() && !dest.mkdirs() && !dest.isDirectory) {
        throw FsException(FsErrorCode.EIO, "mkdir", FsPath.virtualPath(root, dest))
      }
      var count = 0
      for (child in source.listFiles() ?: emptyArray()) {
        count += copyTree(root, config, child, File(dest, child.name), force, preserveTimestamps)
      }
      return count
    }
    if (source.length() > config.maxFileSize) {
      throw FsException(FsErrorCode.EFBIG, "copyfile", FsPath.virtualPath(root, source))
    }
    if (dest.exists() && !force) throw FsException(FsErrorCode.EEXIST, "copyfile", FsPath.virtualPath(root, dest))
    FileChannelCache.invalidate(dest)
    val options = ArrayList<StandardCopyOption>()
    if (force) options.add(StandardCopyOption.REPLACE_EXISTING)
    if (preserveTimestamps) options.add(StandardCopyOption.COPY_ATTRIBUTES)
    fsIo("copyfile", FsPath.virtualPath(root, dest)) {
      Files.copy(source.toPath(), dest.toPath(), *options.toTypedArray())
    }
    return 1
  }

  private fun opTruncate(root: File, config: FsApiConfig, path: String?, len: Long): Map<String, Any?> {
    if (len < 0) throw FsException(FsErrorCode.EINVAL, "truncate", path, "negative length")
    if (len > config.maxFileSize) throw FsException(FsErrorCode.EFBIG, "truncate", path)
    val t = requireExisting(target(root, path, "truncate"), "truncate")
    if (t.file.isDirectory) throw FsException(FsErrorCode.EISDIR, "truncate", t.virtual)
    requireWritable(root, t, "truncate", config)
    FileChannelCache.invalidate(t.file)
    fsIo("truncate", t.virtual) { RandomAccessFile(t.file, "rw").use { it.setLength(len) } }
    return linkedMapOf(
      "path" to t.virtual,
      "size" to t.file.length(),
      "etag" to EtagUtil.weakEtag(t.file),
      "mtimeMs" to t.file.lastModified()
    )
  }

  private fun opUtimes(
    root: File,
    config: FsApiConfig,
    path: String?,
    atimeMs: Long?,
    mtimeMs: Long?,
  ): Map<String, Any?> {
    if (!config.utimesEnabled) throw FsException(FsErrorCode.ENOSYS, "utimes", path, "utimes capability disabled")
    val t = requireExisting(target(root, path, "utimes"), "utimes")
    requireWritable(root, t, "utimes", config)
    fsIo("utimes", t.virtual) {
      val view = Files.getFileAttributeView(t.file.toPath(), BasicFileAttributeView::class.java)
        ?: throw FsException(FsErrorCode.ENOSYS, "utimes", t.virtual, "attribute view unsupported")
      view.setTimes(
        mtimeMs?.let { FileTime.fromMillis(it) },
        atimeMs?.let { FileTime.fromMillis(it) },
        null
      )
    }
    return linkedMapOf("path" to t.virtual, "mtimeMs" to t.file.lastModified())
  }

  private fun opRealpath(root: File, path: String?): Map<String, Any?> {
    val t = requireExisting(target(root, path, "realpath", "/"), "realpath")
    return linkedMapOf("path" to FsPath.virtualPath(root, t.file), "type" to if (t.file.isDirectory) "dir" else "file")
  }

  private fun opReadEncoded(
    root: File,
    config: FsApiConfig,
    path: String?,
    offset: Long,
    length: Long?,
  ): Map<String, Any?> {
    val t = requireExisting(target(root, path, "read"), "read")
    if (t.file.isDirectory) throw FsException(FsErrorCode.EISDIR, "read", t.virtual)
    val size = t.file.length()
    val start = offset.coerceIn(0, size)
    val count = (length ?: (size - start)).coerceAtMost(size - start)
    if (count > config.maxFileSize) throw FsException(FsErrorCode.EFBIG, "read", t.virtual)
    val bytes = ByteArray(count.toInt())
    fsIo("read", t.virtual) {
      RandomAccessFile(t.file, "r").use { raf ->
        raf.seek(start)
        raf.readFully(bytes)
      }
    }
    return linkedMapOf(
      "path" to t.virtual,
      "encoding" to "base64",
      "offset" to start,
      "size" to size,
      "etag" to EtagUtil.weakEtag(t.file),
      "data" to Base64.getEncoder().encodeToString(bytes)
    )
  }

  private fun opWriteEncoded(
    root: File,
    config: FsApiConfig,
    path: String?,
    flag: String,
    data: String?,
    encoding: String,
  ): Map<String, Any?> {
    val t = target(root, path, "open")
    val normalizedFlag = flag.lowercase()
    if (normalizedFlag !in WRITE_FLAGS) {
      throw FsException(FsErrorCode.EINVAL, "open", t.virtual, "unsupported flag '$flag'")
    }
    requireWritable(root, t, "open", config)
    val exists = t.file.exists()
    if (exists && t.file.isDirectory) throw FsException(FsErrorCode.EISDIR, "open", t.virtual)
    if (exists && (normalizedFlag == "wx" || normalizedFlag == "ax")) {
      throw FsException(FsErrorCode.EEXIST, "open", t.virtual)
    }
    if (!exists && normalizedFlag.startsWith("r")) throw FsException(FsErrorCode.ENOENT, "open", t.virtual)
    requireWritableParent(root, t, "open", create = true)
    val bytes = try {
      when (encoding.lowercase()) {
        "base64" -> Base64.getDecoder().decode(data ?: "")
        "base64url" -> Base64.getUrlDecoder().decode(data ?: "")
        "utf8", "utf-8" -> (data ?: "").toByteArray(Charsets.UTF_8)
        "hex" -> (data ?: "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        else -> throw FsException(FsErrorCode.EINVAL, "write", t.virtual, "unsupported encoding '$encoding'")
      }
    } catch (e: IllegalArgumentException) {
      log.debug("rejecting malformed '{}' payload for {}", encoding, t.virtual, e)
      throw FsException(FsErrorCode.EINVAL, "write", t.virtual, "malformed '$encoding' payload")
    }
    if (bytes.size > config.maxFileSize) throw FsException(FsErrorCode.EFBIG, "write", t.virtual)
    FileChannelCache.invalidate(t.file)
    fsIo("write", t.virtual) {
      RandomAccessFile(t.file, "rw").use { raf ->
        if (normalizedFlag.startsWith("a")) raf.seek(raf.length())
        else if (!normalizedFlag.startsWith("r")) {
          raf.setLength(0)
          raf.seek(0)
        }
        raf.write(bytes)
      }
    }
    return linkedMapOf(
      "path" to t.virtual,
      "bytesWritten" to bytes.size,
      "size" to t.file.length(),
      "etag" to EtagUtil.weakEtag(t.file),
      "mtimeMs" to t.file.lastModified(),
      "created" to !exists
    )
  }

  // ------------------------------------------------------- HTTP adapters

  private fun httpStat(req: HttpServletRequest, resp: HttpServletResponse, root: File) {
    val payload = opStat(
      root,
      req.getParameter("path"),
      boolParam(req, "lstat"),
      req.getParameter("throwIfNoEntry")?.equals("false", true) != true
    )
    (payload["etag"] as? String)?.let { resp.setHeader("ETag", it) }
    writeJson(resp, HttpServletResponse.SC_OK, payload)
  }

  private fun httpStatBatch(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val body = readBody(req, "stat")
    val paths = FsJson.list(body, "paths")
    if (paths.size > config.maxBatchOps) {
      log.info("rejecting batch stat with {} paths (max {})", paths.size, config.maxBatchOps)
      throw FsException(FsErrorCode.EINVAL, "stat", null, "too many paths (max ${config.maxBatchOps})")
    }
    val lstat = FsJson.boolean(body, "lstat", false)
    val results = paths.map { raw ->
      try {
        linkedMapOf<String, Any?>("ok" to true, "stat" to opStat(root, raw?.toString(), lstat, true))
      } catch (e: FsException) {
        linkedMapOf<String, Any?>("ok" to false, "error" to FsErrors.payload(e))
      } catch (e: Exception) {
        log.warn("batch stat failed for '{}'", raw, e)
        linkedMapOf<String, Any?>("ok" to false, "error" to FsErrors.payload(toFsException("stat", null, e)))
      }
    }
    writeJson(resp, HttpServletResponse.SC_OK, results)
  }

  private fun httpReaddir(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val recursive = boolParam(req, "recursive")
    val depth = req.getParameter("depth")?.toIntOrNull() ?: if (recursive) config.maxDepth else 1
    val includeStat = req.getParameter("stat")?.let { it.isEmpty() || it.equals("true", true) }
      ?: boolParam(req, "withFileTypes", true)
    writeJson(
      resp, HttpServletResponse.SC_OK,
      opReaddir(root, config, req.getParameter("path"), recursive, depth, includeStat)
    )
  }

  private fun httpReadFile(req: HttpServletRequest, resp: HttpServletResponse, root: File, headOnly: Boolean) {
    val t = requireExisting(target(root, req.getParameter("path"), "read"), "read")
    if (t.file.isDirectory) throw FsException(FsErrorCode.EISDIR, "read", t.virtual)
    val etag = EtagUtil.weakEtag(t.file)
    val size = t.file.length()
    val resolvedMime = MimeTypeResolver.getMimeType(t.file.name)
    resp.setHeader("ETag", etag)
    resp.setDateHeader("Last-Modified", t.file.lastModified())
    resp.setHeader("Accept-Ranges", "bytes")
    resp.setHeader("Cache-Control", "no-cache")
    resp.setHeader("X-Fs-Mime-Type", resolvedMime)
    resp.setHeader("X-Fs-Size", size.toString())
    resp.setHeader("X-Fs-Mtime-Ms", t.file.lastModified().toString())
    val ifNoneMatch = req.getHeader("If-None-Match")
    if (ifNoneMatch != null && EtagUtil.matches(ifNoneMatch, etag)) {
      resp.status = HttpServletResponse.SC_NOT_MODIFIED
      return
    }
    val ifMatch = req.getHeader("If-Match")
    if (ifMatch != null && !EtagUtil.matches(ifMatch, etag)) {
      throw FsException(FsErrorCode.EBUSY, "read", t.virtual, "ETag mismatch")
    }
    val range = RangeUtil.parse(req.getHeader("Range"), size, "read", t.virtual)
    /*
     * A navigation (iframe / new tab) served as application/octet-stream is turned into a
     * download by the browser, so the frame never renders. Serve the real type there, and
     * neutralise scriptable payloads with an opaque-origin sandbox instead.
     */
    val contentType = negotiateContentType(req, resolvedMime, t.virtual)
    val inline = contentType != OCTET_STREAM
    resp.contentType = contentType
    resp.setHeader("X-Content-Type-Options", "nosniff")
    resp.setHeader("Content-Disposition", contentDisposition(inline, t.file.name))
    if (inline && baseType(contentType) in SCRIPTABLE_TYPES) {
      resp.setHeader(
        "Content-Security-Policy",
        "sandbox allow-scripts allow-forms allow-popups allow-modals allow-downloads"
      )
    }
    if (range == null) {
      resp.status = HttpServletResponse.SC_OK
      resp.setContentLengthLong(size)
    } else {
      resp.status = HttpServletResponse.SC_PARTIAL_CONTENT
      resp.setContentLengthLong(range.length)
      resp.setHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/$size")
    }
    if (headOnly) return
    val start = range?.start ?: 0L
    var remaining = range?.length ?: size
    val out: OutputStream = resp.outputStream
    try {
      RandomAccessFile(t.file, "r").use { raf ->
        raf.seek(start)
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
          val chunk = minOf(remaining, buffer.size.toLong()).toInt()
          val read = raf.read(buffer, 0, chunk)
          if (read <= 0) {
            if (remaining > 0) {
              log.warn("short read on {}: {} byte(s) still expected (file changed under us?)", t.virtual, remaining)
            }
            break
          }
          out.write(buffer, 0, read)
          remaining -= read
        }
      }
      out.flush()
    } catch (e: IOException) {
      /* Headers are already on the wire: an error body would corrupt the response. */
      if (isClientAbort(e)) {
        log.debug("client aborted download of {} ({} byte(s) unsent)", t.virtual, remaining)
        return
      }
      log.error("failed streaming {} ({} byte(s) unsent)", t.virtual, remaining, e)
      throw FsException(FsErrorCode.EIO, "read", t.virtual, e.message)
    }
  }

  private fun httpWriteFile(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val t = target(root, req.getParameter("path"), "open")
    val flag = (req.getParameter("flag") ?: "w").lowercase()
    if (flag !in WRITE_FLAGS) throw FsException(FsErrorCode.EINVAL, "open", t.virtual, "unsupported flag '$flag'")
    requireWritable(root, t, "open", config)
    val exists = t.file.exists()
    if (exists && t.file.isDirectory) throw FsException(FsErrorCode.EISDIR, "open", t.virtual)
    val ifNoneMatch = req.getHeader("If-None-Match")
    val ifMatch = req.getHeader("If-Match")
    if (exists && (flag == "wx" || flag == "ax" || ifNoneMatch?.trim() == "*")) {
      log.debug("write to {} rejected: exclusive create on existing file", t.virtual)
      throw FsException(FsErrorCode.EEXIST, "open", t.virtual)
    }
    if (!exists && flag.startsWith("r")) throw FsException(FsErrorCode.ENOENT, "open", t.virtual)
    if (ifMatch != null) {
      if (!exists) throw FsException(FsErrorCode.ENOENT, "open", t.virtual)
      if (!EtagUtil.matches(ifMatch, EtagUtil.weakEtag(t.file))) {
        log.info("write to {} rejected: If-Match mismatch (client sent '{}')", t.virtual, ifMatch)
        throw FsException(FsErrorCode.EBUSY, "write", t.virtual, "ETag mismatch (concurrent modification)")
      }
    }
    val declared = req.contentLengthLong
    if (declared > config.maxFileSize) {
      log.info("write to {} rejected: declared {} bytes exceeds maxFileSize {}", t.virtual, declared, config.maxFileSize)
      throw FsException(FsErrorCode.EFBIG, "write", t.virtual)
    }
    requireWritableParent(root, t, "open", create = true)
    val position = req.getParameter("position")?.toLongOrNull()
      ?: RangeUtil.contentRangeStart(req.getHeader("Content-Range"), "write", t.virtual)
    FileChannelCache.invalidate(t.file)
    val bytesWritten = fsIo("write", t.virtual) {
      RandomAccessFile(t.file, "rw").use { raf ->
        when {
          flag.startsWith("a") -> raf.seek(raf.length())
          position != null -> raf.seek(position)
          flag.startsWith("r") -> raf.seek(0)
          else -> {
            raf.setLength(0)
            raf.seek(0)
          }
        }
        copyStream(
          req.inputStream,
          { buf, len -> raf.write(buf, 0, len) },
          config.maxFileSize,
          "write",
          t.virtual
        )
      }
    }
    if (log.isDebugEnabled) log.debug("wrote {} byte(s) to {} (flag={})", bytesWritten, t.virtual, flag)
    val etag = EtagUtil.weakEtag(t.file)
    resp.setHeader("ETag", etag)
    resp.setDateHeader("Last-Modified", t.file.lastModified())
    writeJson(
      resp,
      if (exists) HttpServletResponse.SC_OK else HttpServletResponse.SC_CREATED,
      linkedMapOf(
        "path" to t.virtual,
        "bytesWritten" to bytesWritten,
        "size" to t.file.length(),
        "etag" to etag,
        "mtimeMs" to t.file.lastModified(),
        "created" to !exists
      )
    )
  }

  private fun httpRemove(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    opRemove(root, config, req.getParameter("path"), boolParam(req, "recursive"), boolParam(req, "force"))
    resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  private fun httpMkdir(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val body = readBody(req, "mkdir")
    val payload = opMkdir(
      root, config,
      FsJson.string(body, "path") ?: req.getParameter("path"),
      FsJson.boolean(body, "recursive", boolParam(req, "recursive"))
    )
    writeJson(
      resp,
      if (payload["created"] == true) HttpServletResponse.SC_CREATED else HttpServletResponse.SC_OK,
      payload
    )
  }

  private fun httpRename(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val body = readBody(req, "rename")
    opRename(
      root, config,
      FsJson.string(body, "from") ?: req.getParameter("from"),
      FsJson.string(body, "to") ?: req.getParameter("to"),
      FsJson.boolean(body, "overwrite", true)
    )
    resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  private fun httpCopy(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val body = readBody(req, "copyfile")
    opCopy(
      root, config,
      FsJson.string(body, "from") ?: req.getParameter("from"),
      FsJson.string(body, "to") ?: req.getParameter("to"),
      FsJson.boolean(body, "recursive", false),
      FsJson.boolean(body, "force", true),
      FsJson.boolean(body, "preserveTimestamps", false)
    )
    resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  private fun httpTruncate(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val body = readBody(req, "truncate")
    opTruncate(
      root, config,
      FsJson.string(body, "path") ?: req.getParameter("path"),
      FsJson.long(body, "len") ?: req.getParameter("len")?.toLongOrNull() ?: 0L
    )
    resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  private fun httpUtimes(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val body = readBody(req, "utimes")
    opUtimes(
      root, config,
      FsJson.string(body, "path") ?: req.getParameter("path"),
      FsJson.long(body, "atime") ?: FsJson.long(body, "atimeMs"),
      FsJson.long(body, "mtime") ?: FsJson.long(body, "mtimeMs")
    )
    resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  /** Bridges the FS API onto the extensible [GitActions] registry. */
  private fun httpGit(ctx: FsActionContext) {
    if (!ctx.config.execAllowlist.containsKey("git")) {
      log.debug("git action rejected: capability disabled for this mount")
      throw FsException(FsErrorCode.ENOSYS, "git", null, "git capability disabled for this mount")
    }
    val body = readBody(ctx.req, "git")
    val name = FsJson.string(body, "action")
      ?: throw FsException(FsErrorCode.EINVAL, "git", null, "missing 'action'")

    @Suppress("UNCHECKED_CAST")
    val params = (body["params"] as? Map<String, Any?>) ?: body
    val startedNanos = System.nanoTime()
    val payload = try {
      GitActions.execute(name, params, ctx.root)
    } catch (e: FsException) {
      throw e
    } catch (e: IllegalArgumentException) {
      log.info("git action '{}' rejected: {}", name, e.message)
      throw FsException(FsErrorCode.EINVAL, "git", null, e.message)
    } catch (e: Exception) {
      log.error("git action '{}' failed after {}ms", name, elapsedMs(startedNanos), e)
      throw toFsException("git", null, e)
    }
    if (log.isDebugEnabled) log.debug("git action '{}' completed in {}ms", name, elapsedMs(startedNanos))
    writeJson(ctx.resp, HttpServletResponse.SC_OK, payload)
  }

  private fun httpBatch(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    root: File,
    config: FsApiConfig,
  ) {
    val body = readBody(req, "batch")
    val ops = FsJson.list(body, "ops")
    val stopOnError = FsJson.boolean(body, "stopOnError", false)
    if (ops.size > config.maxBatchOps) {
      log.info("rejecting batch with {} ops (max {})", ops.size, config.maxBatchOps)
      throw FsException(FsErrorCode.EINVAL, "batch", null, "too many ops (max ${config.maxBatchOps})")
    }
    val results = ArrayList<Any?>(ops.size)
    for ((index, raw) in ops.withIndex()) {
      @Suppress("UNCHECKED_CAST")
      val op = raw as? Map<String, Any?>
      try {
        if (op == null) throw FsException(FsErrorCode.EINVAL, "batch", null, "each op must be an object")
        results.add(linkedMapOf("ok" to true, "value" to runBatchOp(root, config, op)))
      } catch (e: FsException) {
        if (log.isDebugEnabled) {
          log.debug("batch op #{} ({}) -> {}", index, op?.get("op"), e.message ?: e.javaClass.simpleName)
        }
        results.add(linkedMapOf("ok" to false, "error" to FsErrors.payload(e)))
        if (stopOnError) break
      } catch (e: Exception) {
        log.error("batch op #{} ({}) failed", index, op?.get("op"), e)
        results.add(
          linkedMapOf(
            "ok" to false,
            "error" to FsErrors.payload(toFsException("batch", FsJson.string(op ?: emptyMap(), "path"), e))
          )
        )
        if (stopOnError) break
      }
    }
    if (log.isDebugEnabled) log.debug("batch completed: {}/{} op(s) executed", results.size, ops.size)
    writeJson(resp, HttpServletResponse.SC_OK, results)
  }

  private fun runBatchOp(root: File, config: FsApiConfig, op: Map<String, Any?>): Any {
    val name = (FsJson.string(op, "op") ?: "").lowercase()
    val path = FsJson.string(op, "path")
    return when (name) {
      "stat" -> opStat(root, path, false, FsJson.boolean(op, "throwIfNoEntry", true))
      "lstat" -> opStat(root, path, true, FsJson.boolean(op, "throwIfNoEntry", true))
      "exists" -> {
        val stat = opStat(root, path, false, false)
        linkedMapOf("path" to stat["path"], "exists" to (stat["exists"] == true))
      }

      "readdir" -> opReaddir(
        root, config, path,
        FsJson.boolean(op, "recursive", false),
        FsJson.int(op, "depth") ?: config.maxDepth,
        FsJson.boolean(op, "withFileTypes", true)
      )

      "mkdir" -> opMkdir(root, config, path, FsJson.boolean(op, "recursive", false))
      "rm", "unlink", "rmdir" -> opRemove(
        root, config, path,
        FsJson.boolean(op, "recursive", name == "rm"),
        FsJson.boolean(op, "force", false)
      )

      "rename" -> opRename(
        root, config,
        FsJson.string(op, "from"), FsJson.string(op, "to"),
        FsJson.boolean(op, "overwrite", true)
      )

      "copy" -> opCopy(
        root, config,
        FsJson.string(op, "from"), FsJson.string(op, "to"),
        FsJson.boolean(op, "recursive", false),
        FsJson.boolean(op, "force", true),
        FsJson.boolean(op, "preserveTimestamps", false)
      )

      "truncate" -> opTruncate(root, config, path, FsJson.long(op, "len") ?: 0L)
      "utimes" -> opUtimes(
        root, config, path,
        FsJson.long(op, "atime") ?: FsJson.long(op, "atimeMs"),
        FsJson.long(op, "mtime") ?: FsJson.long(op, "mtimeMs")
      )

      "realpath" -> opRealpath(root, path)
      "read" -> opReadEncoded(root, config, path, FsJson.long(op, "offset") ?: 0L, FsJson.long(op, "length"))
      "write" -> opWriteEncoded(
        root, config, path,
        FsJson.string(op, "flag") ?: "w",
        FsJson.string(op, "data"),
        FsJson.string(op, "encoding") ?: "base64"
      )

      "resolve" -> FsResolveHandler.resolve(
        root, config,
        FsJson.string(op, "from") ?: "/",
        FsJson.string(op, "request") ?: throw FsException(FsErrorCode.EINVAL, "resolve", null, "missing 'request'")
      )

      "git" -> GitActions.execute(
        FsJson.string(op, "action") ?: throw FsException(FsErrorCode.EINVAL, "git", null, "missing 'action'"),
        @Suppress("UNCHECKED_CAST") ((op["params"] as? Map<String, Any?>) ?: op),
        root
      )

      else -> throw FsException(FsErrorCode.ENOSYS, "batch", path, "unknown batch op '$name'")
    }
  }
}