package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import jakarta.servlet.http.HttpServletResponse

/**
 * errno taxonomy for FS API v1 (nodejs.md §5.4 / Appendix B).
 * Each entry maps 1:1 onto a Node `err.code` / `err.errno` pair.
 */
enum class FsErrorCode(val errno: Int, val status: Int, val defaultMessage: String) {
  ENOENT(-2, HttpServletResponse.SC_NOT_FOUND, "no such file or directory"),
  EACCES(-13, HttpServletResponse.SC_FORBIDDEN, "permission denied"),
  EROFS(-30, HttpServletResponse.SC_FORBIDDEN, "read-only file system"),
  EEXIST(-17, HttpServletResponse.SC_CONFLICT, "file already exists"),
  EISDIR(-21, HttpServletResponse.SC_BAD_REQUEST, "illegal operation on a directory"),
  ENOTDIR(-20, HttpServletResponse.SC_BAD_REQUEST, "not a directory"),
  ENOTEMPTY(-39, HttpServletResponse.SC_CONFLICT, "directory not empty"),
  EINVAL(-22, HttpServletResponse.SC_BAD_REQUEST, "invalid argument"),
  EFBIG(-27, 413, "file too large"),
  EBUSY(-16, HttpServletResponse.SC_PRECONDITION_FAILED, "resource busy or locked"),
  EMFILE(-24, 429, "too many open files"),
  ERANGE(-34, 416, "result out of range"),
  ENOSYS(-38, HttpServletResponse.SC_NOT_IMPLEMENTED, "function not implemented"),
  EIO(-5, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "i/o error"),
}

class FsException(
  val code: FsErrorCode,
  val syscall: String,
  val path: String? = null,
  val detail: String? = null,
) : RuntimeException(
  buildString {
    append(code.name).append(": ").append(detail ?: code.defaultMessage)
    if (path != null) append(", ").append(syscall).append(" '").append(path).append('\'')
  }
)

object FsErrors {
  fun payload(e: FsException): Map<String, Any?> = linkedMapOf(
    "code" to e.code.name,
    "errno" to e.code.errno,
    "syscall" to e.syscall,
    "path" to e.path,
    "message" to (e.detail ?: e.code.defaultMessage)
  )

  fun write(resp: HttpServletResponse, e: FsException) {
    if (resp.isCommitted) return
    resp.reset()
    resp.status = e.code.status
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.setHeader("X-Fs-Error", e.code.name)
    resp.writer.write(FsJson.stringify(mapOf("error" to payload(e))))
  }
}