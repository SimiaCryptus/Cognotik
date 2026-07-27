package com.simiacryptus.cognotik.webui.servlet.util

import java.io.File

/**
 * Weak ETags derived from (size, mtime) — see nodejs.md §5.5.
 * Cheap, stable within a single filesystem, and sufficient for
 * optimistic-concurrency (If-Match) and cache validation (If-None-Match).
 */
object EtagUtil {
  fun weakEtag(file: File): String =
    "W/\"${file.length().toString(16)}-${file.lastModified().toString(16)}\""

  /**
   * Evaluates an If-Match / If-None-Match header value against [etag].
   * Handles `*`, comma-separated lists and weak/strong prefixes.
   */
  fun matches(header: String?, etag: String): Boolean {
    if (header.isNullOrBlank()) return false
    if (header.trim() == "*") return true
    val normalizedTarget = normalize(etag)
    return header.split(",").any { normalize(it.trim()) == normalizedTarget }
  }

  private fun normalize(value: String): String =
    value.removePrefix("W/").removePrefix("w/").trim().trim('"')
}