package com.simiacryptus.cognotik.webui.servlet.util

object PathUtils {
  /**
   * Path segment reserved for the FS API v1 surface
   * (`{mount}/.fsapi/v1/...`). Never treated as a real file/directory.
   */
  const val RESERVED_SEGMENT = ".fsapi"

  fun parsePath(path: String): List<String> {
    val pathSegments = path.split("/").filter { it.isNotBlank() }
    pathSegments.forEach {
      when {
        it == ".." -> throw IllegalArgumentException("Invalid path")
        it == RESERVED_SEGMENT -> throw IllegalArgumentException("Reserved path segment: $it")
        it.any { ch ->
          when {
            ch == ':' -> true
            ch == '/' -> true
            ch == '~' -> true
            ch == '\\' -> true
            ch.code < 32 -> true
            else -> false
          }
        } -> throw IllegalArgumentException("Invalid path")
      }
    }
    return pathSegments
  }

  fun isValidFileName(fileName: String): Boolean {
    return !fileName.contains("..") &&
        !fileName.contains("/") &&
        !fileName.contains("\\") &&
        !fileName.contains(":") &&
        !fileName.contains("~") &&
        fileName.isNotBlank() &&
        fileName.all { it.code >= 32 }
  }

  fun jsonEscape(value: String): String {
    val escaped = value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    return "\"$escaped\""
  }
}