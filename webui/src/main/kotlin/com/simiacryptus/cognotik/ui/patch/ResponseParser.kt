package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.getMarkdownCodeBlockMatches
import org.slf4j.LoggerFactory

class ResponseParser(private val processor: PatchProcessor) {
  companion object {
    private val log = LoggerFactory.getLogger(ResponseParser::class.java)

    /**
     * Regular expression pattern used to match Markdown headers.
     *
     * This pattern ensures that the header starts at the beginning of a line (or after a newline)
     * and captures the text following the hash `#` characters.
     *
     * **Example Matches:**
     * - `# Header 1` -> Captures "Header 1"
     * - `### Subtitle` -> Captures "Subtitle"
     */
    private val MARKDOWN_HEADER_PATTERN = """(?<![^\n])#+\s*([^\n]+)""".toRegex()

    /**
     * Regular expression pattern used to match custom file headers in text blocks.
     *
     * This pattern looks for a specific block format enclosed by dashes or lines,
     * extracting the file name or path specified after "File: ".
     *
     * **Example Matches:**
     * ```
     * --------------------
     * File: src/main/Main.kt
     * --------------------
     * ```
     * -> Captures "src/main/Main.kt"
     */
    private val FILE_HEADER_PATTERN = """(?m)^(?:─+|-+)\s*\nFile:\s*(.+?)\s*\n(?:─+|-+)\s*""".toRegex()
  }

  fun parse(
    response: String,
    defaultFile: String? = null
  ): List<ResponseSegment> {
    log.debug("Parsing response: {} chars, defaultFile={}", response.length, defaultFile)
    if (response.isBlank()) {
      log.debug("Response is blank, returning empty list")
      return emptyList()
    }
    val initiator = processor.getInitiatorPattern()
    // Auto-close unclosed code blocks
    val normalizedResponse = (if (response.contains(initiator) &&
      !response.split(initiator, 2)[1].contains("\n```(?![^\n])".toRegex())
    ) {
      log.debug("Auto-closing unclosed code block in response")
      response + "\n```\n"
    } else {
      response
    })
    val normalizedResponseLines = normalizedResponse.lines()

    val codeBlockMatches = normalizedResponse.getMarkdownCodeBlockMatches()
    if (codeBlockMatches.isEmpty()) {
      log.debug("No code blocks found in response")
      return listOf(ResponseSegment.Markdown(normalizedResponse))
    }
    log.debug("Found {} code blocks in response", codeBlockMatches.size)

    val headers = collectHeaders(normalizedResponse)
    log.debug("Found {} headers in response", headers.size)
    val segments = mutableListOf<ResponseSegment>()
    var lastEnd = 0

    for (codeBlockMatch in codeBlockMatches) {
      val lang = codeBlockMatch.language
      val code = codeBlockMatch.code
      val matchRange = codeBlockMatch.range
      log.debug("Processing code block: lang='{}', code length={}, range={}", lang, code.length, matchRange)

      // Add preceding markdown
      val lineStart = matchRange.first
      val lineStartPos = normalizedResponseLines.take(lineStart).sumOf { it.length + 1 } // +1 for newline
      val lineEnd = matchRange.last
      if (lineStart > lastEnd) {
        val markdownContent = normalizedResponseLines.subList(lastEnd, lineStart).joinToString("\n")
        if (markdownContent.isNotBlank()) {
          segments.add(ResponseSegment.Markdown(markdownContent))
        }
      } else {
        log.debug("No preceding markdown for code block at range {}", matchRange)
      }

      val headerFilename = findHeaderBefore(headers, lineStartPos)
      val filename = resolveFilename(headerFilename, defaultFile)
      log.debug("Resolved filename for code block: headerFilename='{}', resolved='{}'", headerFilename, filename)

      if (filename != null) {
        val normalizedName = normalizeFilename(filename)
        log.debug("Normalized filename: '{}' -> '{}'", filename, normalizedName)
        if (normalizedName.isNotBlank()) {
          val isDiff = isDiffContent(lang, code)
          log.debug("Code block isDiff={} for file '{}'", isDiff, normalizedName)
          if (isDiff) {
            segments.add(
              ResponseSegment.DiffBlock(
                filename = normalizedName,
                diff = code,
                originalRange = matchRange
              )
            )
          } else {
            segments.add(
              ResponseSegment.NewFileBlock(
                filename = normalizedName,
                language = lang,
                code = code.trimIndent(),
                originalRange = matchRange
              )
            )
          }
        } else {
          log.debug("Normalized filename is blank, treating code block as markdown")
          segments.add(ResponseSegment.Markdown(normalizedResponseLines.subList(lineStart, lineEnd + 1).joinToString("\n")))
        }
      } else {
        log.debug("No filename resolved, treating code block as markdown")
        segments.add(ResponseSegment.Markdown(normalizedResponseLines.subList(lineStart, lineEnd + 1).joinToString("\n")))
      }

      lastEnd = lineEnd + 1
    }

    // Add trailing markdown
    if (lastEnd < normalizedResponseLines.size) {
      val trailing = normalizedResponseLines.subList(lastEnd, normalizedResponseLines.size).joinToString("\n")
      if (trailing.isNotBlank()) {
        segments.add(ResponseSegment.Markdown(trailing))
      }
    }

    log.debug("Parsed {} total segments from response", segments.size)
    return segments
  }


  private fun collectHeaders(response: String): List<Pair<IntRange, String>> {
    val headers = mutableListOf<Pair<IntRange, String>>()
    MARKDOWN_HEADER_PATTERN.findAll(response).forEach { match ->
      headers.add(match.range to normalizeFilename(match.groupValues[1]))
    }
    FILE_HEADER_PATTERN.findAll(response).forEach { match ->
      headers.add(match.range to normalizeFilename(match.groupValues[1]))
    }
    return headers
  }

  private fun findHeaderBefore(headers: List<Pair<IntRange, String>>, position: Int): String? {
    return headers
      .filter { it.first.last <= position }
      .maxByOrNull { it.first.last }
      ?.second
  }

  private fun resolveFilename(headerFilename: String?, defaultFile: String?): String? {
    return when {
      headerFilename != null && headerFilename.isNotBlank() && headerFilename.contains('.') -> headerFilename
      headerFilename != null && headerFilename.isNotBlank() -> headerFilename
      defaultFile != null -> defaultFile
      else -> null
    }
  }

  private fun isDiffContent(lang: String, code: String): Boolean {
    if (lang.equals("diff", ignoreCase = true)) return true
    val lines = code.lines()
    val diffLineCount = lines.count { it.startsWith('+') || it.startsWith('-') || it.startsWith('@') }
    return diffLineCount > lines.size / 2
  }

}