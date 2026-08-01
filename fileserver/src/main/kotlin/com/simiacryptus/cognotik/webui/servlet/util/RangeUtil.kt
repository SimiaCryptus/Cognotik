package com.simiacryptus.cognotik.webui.servlet.util

import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
import com.simiacryptus.cognotik.webui.servlet.handler.FsException

/**
 * Single-range `Range:`/`Content-Range:` parsing — required so that
 * fs.read(fd, buf, off, len, pos) and createReadStream({start,end})
 * can be implemented without transferring whole files (nodejs.md D6).
 */
object RangeUtil {
  data class Range(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
  }

  fun parse(header: String?, size: Long, syscall: String, path: String): Range? {
    if (header.isNullOrBlank()) return null
    if (!header.startsWith("bytes=")) {
      throw FsException(FsErrorCode.ERANGE, syscall, path, "unsupported range unit: $header")
    }
    val spec = header.removePrefix("bytes=").split(',').first().trim()
    val dash = spec.indexOf('-')
    if (dash < 0) throw FsException(FsErrorCode.ERANGE, syscall, path, "malformed range: $header")
    val startText = spec.substring(0, dash).trim()
    val endText = spec.substring(dash + 1).trim()
    if (size == 0L) throw FsException(FsErrorCode.ERANGE, syscall, path, "range not satisfiable for empty file")
    if (startText.isEmpty()) {
      val suffix = endText.toLongOrNull()
        ?: throw FsException(FsErrorCode.ERANGE, syscall, path, "malformed range: $header")
      if (suffix <= 0) throw FsException(FsErrorCode.ERANGE, syscall, path, "malformed range: $header")
      return Range((size - suffix).coerceAtLeast(0), size - 1)
    }
    val start = startText.toLongOrNull()
      ?: throw FsException(FsErrorCode.ERANGE, syscall, path, "malformed range: $header")
    if (start < 0 || start >= size) {
      throw FsException(FsErrorCode.ERANGE, syscall, path, "range start $start outside 0..${size - 1}")
    }
    val end = if (endText.isEmpty()) size - 1
    else (endText.toLongOrNull()
      ?: throw FsException(FsErrorCode.ERANGE, syscall, path, "malformed range: $header")).coerceAtMost(size - 1)
    if (end < start) throw FsException(FsErrorCode.ERANGE, syscall, path, "inverted range: $header")
    return Range(start, end)
  }

  fun contentRangeStart(header: String?, syscall: String, path: String): Long? {
    if (header.isNullOrBlank()) return null
    val spec = header.trim().removePrefix("bytes").trim()
    val dash = spec.indexOf('-')
    if (dash <= 0) throw FsException(FsErrorCode.EINVAL, syscall, path, "malformed Content-Range: $header")
    return spec.substring(0, dash).trim().toLongOrNull()
      ?: throw FsException(FsErrorCode.EINVAL, syscall, path, "malformed Content-Range: $header")
  }
}