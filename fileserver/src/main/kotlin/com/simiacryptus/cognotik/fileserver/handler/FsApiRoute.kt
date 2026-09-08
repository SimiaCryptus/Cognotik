package com.simiacryptus.cognotik.fileserver.handler

import com.simiacryptus.cognotik.fileserver.util.FsPath

/**
 * Recognises `{mount}/[session/].fsapi/v<N>/<op>` before normal file-path
 * parsing (nodejs.md A2, D11). Everything preceding the `.fsapi` segment is
 * the opaque servlet prefix (typically a session id) and is never part of a
 * Node-visible path.
 */
data class FsApiRoute(val prefix: String, val version: Int, val op: String) {
  companion object {
    fun parse(pathInfo: String?): FsApiRoute? {
      if (pathInfo == null) return null
      val segments = pathInfo.split('/')
      val index = segments.indexOf(FsPath.RESERVED_SEGMENT)
      if (index < 0) return null
      val prefix = segments.take(index).filter { it.isNotBlank() }.joinToString("/")
      val versionSegment = segments.getOrNull(index + 1)
      val version = versionSegment?.removePrefix("v")?.toIntOrNull() ?: -1
      val op = segments.drop(index + 2).filter { it.isNotBlank() }.joinToString("/")
      return FsApiRoute(prefix, version, op)
    }
  }
}