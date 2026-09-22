package com.simiacryptus.cognotik.webui.servlet.util

import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
import com.simiacryptus.cognotik.webui.servlet.handler.FsException
import java.io.File
import java.io.IOException

/** A validated FS API path: the client-visible virtual path plus the real File. */
data class FsTarget(val virtual: String, val file: File)

/**
 * Path normalization/containment for FS API v1 (nodejs.md §5.1, §9.8, §9.9).
 *
 * Rules:
 *  - always `/`-separated, always relative to the servlet root ("/" == getDir())
 *  - `.` collapsed, `..` resolved lexically; escaping the root is EINVAL
 *  - NUL / control characters, `\`, `:` and the reserved `.fsapi` segment are EINVAL
 *  - after lexical normalization the canonical path is re-checked for containment,
 *    which also blocks symlink escape
 */
object FsPath {
  const val RESERVED_SEGMENT = ".fsapi"

  fun normalize(path: String?, syscall: String): String {
    val raw = path ?: throw FsException(FsErrorCode.EINVAL, syscall, null, "missing path")
    if (raw.indexOf('\u0000') >= 0) throw FsException(FsErrorCode.EINVAL, syscall, null, "path contains NUL")
    val segments = ArrayList<String>()
    for (segment in raw.split('/')) {
      when {
        segment.isEmpty() || segment == "." -> {}
        segment == ".." -> {
          if (segments.isEmpty()) throw FsException(FsErrorCode.EINVAL, syscall, raw, "path escapes the served root")
          segments.removeAt(segments.size - 1)
        }

        segment == RESERVED_SEGMENT ->
          throw FsException(FsErrorCode.EINVAL, syscall, raw, "reserved path segment '$RESERVED_SEGMENT'")

        segment.any { it == '\\' || it == ':' || it == '~' || it.code < 32 || it.code == 127 } ->
          throw FsException(FsErrorCode.EINVAL, syscall, raw, "invalid character in path segment '$segment'")

        segment == "." || segment.endsWith(" ") || segment.endsWith(".") && segment != "." ->
          // trailing dots/spaces are ambiguous on Windows hosts
          throw FsException(FsErrorCode.EINVAL, syscall, raw, "invalid path segment '$segment'")

        else -> segments.add(segment)
      }
    }
    return segments.joinToString("/")
  }

  fun resolve(root: File, path: String?, syscall: String): FsTarget {
    val relative = normalize(path, syscall)
    val virtual = "/" + relative
    val canonicalRoot = try {
      root.canonicalFile
    } catch (e: IOException) {
      root.absoluteFile
    }
    val target = if (relative.isEmpty()) canonicalRoot else File(canonicalRoot, relative)
    val canonicalTarget = try {
      target.canonicalFile
    } catch (e: IOException) {
      target.absoluteFile
    }
    val rootPath = canonicalRoot.path
    val targetPath = canonicalTarget.path
    val contained = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    if (!contained) throw FsException(FsErrorCode.EACCES, syscall, virtual, "path escapes the served root")
    return FsTarget(virtual, target)
  }

  /** Inverse of [resolve] — the virtual path a real file is exposed as. */
  fun virtualPath(root: File, file: File): String {
    val rootPath = try {
      root.canonicalFile.path
    } catch (e: IOException) {
      root.absoluteFile.path
    }
    val filePath = try {
      file.canonicalFile.path
    } catch (e: IOException) {
      file.absoluteFile.path
    }
    if (filePath == rootPath) return "/"
    if (!filePath.startsWith(rootPath + File.separator)) return "/" + file.name
    return "/" + filePath.substring(rootPath.length + 1).replace(File.separatorChar, '/')
  }

  fun join(base: String, relative: String): String {
    val prefix = base.trimEnd('/')
    return if (relative.isEmpty()) prefix.ifEmpty { "/" } else "$prefix/$relative"
  }
}