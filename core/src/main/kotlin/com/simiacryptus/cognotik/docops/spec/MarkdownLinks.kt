package com.simiacryptus.cognotik.docops.spec

/** Unwraps `[label](path)` so docs stay clickable in an IDE / on GitHub. */
object MarkdownLinks {

  private val MARKDOWN_LINK_REGEX = Regex("""^\s*\[[^\]]*]\(\s*([^)\s]+)\s*\)\s*$""")

  fun extractPath(value: String): String =
    MARKDOWN_LINK_REGEX.matchEntire(value)?.groupValues?.get(1)?.trim() ?: value.trim()

  fun extractPaths(values: List<String>): List<String> = values.map { extractPath(it) }
}