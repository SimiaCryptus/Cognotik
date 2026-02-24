package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor

class ResponseParser(private val processor: PatchProcessor) {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ResponseParser::class.java)
    private val MARKDOWN_HEADER_PATTERN = """(?<![^\n])#+\s*([^\n]+)""".toRegex()
    private val FILE_HEADER_PATTERN = """(?m)^(?:─+|-+)\s*\nFile:\s*(.+?)\s*\n(?:─+|-+)\s*""".toRegex()
    private val CODE_BLOCK_REGEX = """(?m)^```(\w*)\s*\n([\s\S]*?)^```\s*$""".toRegex(RegexOption.MULTILINE)
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
    val normalizedResponse = if (response.contains(initiator) &&
      !response.split(initiator, 2)[1].contains("\n```(?![^\n])".toRegex())
    ) {
      log.debug("Auto-closing unclosed code block in response")
      response + "\n```\n"
    } else {
      response
    }

    val codeBlockMatches = findCodeBlocks(normalizedResponse)
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
      val lang = codeBlockMatch.lang
      val code = codeBlockMatch.code
      val matchRange = codeBlockMatch.range
      log.debug("Processing code block: lang='{}', code length={}, range={}", lang, code.length, matchRange)

      // Add preceding markdown
      if (matchRange.first > lastEnd) {
        val markdownContent = normalizedResponse.substring(lastEnd, matchRange.first)
        if (markdownContent.isNotBlank()) {
          segments.add(ResponseSegment.Markdown(markdownContent))
        }
      }

      val headerFilename = findHeaderBefore(headers, matchRange.first)
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
          segments.add(ResponseSegment.Markdown(normalizedResponse.substring(matchRange.first, matchRange.last + 1)))
        }
      } else {
        log.debug("No filename resolved, treating code block as markdown")
        segments.add(ResponseSegment.Markdown(normalizedResponse.substring(matchRange.first, matchRange.last + 1)))
      }

      lastEnd = matchRange.last + 1
    }

    // Add trailing markdown
    if (lastEnd < normalizedResponse.length) {
      val trailing = normalizedResponse.substring(lastEnd)
      if (trailing.isNotBlank()) {
        segments.add(ResponseSegment.Markdown(trailing))
      }
    }
    log.debug("Parsed {} total segments from response", segments.size)

    return segments
  }
  private data class CodeBlockMatch(
    val lang: String,
    val code: String,
    val range: IntRange
  )
  /**
   * Finds individual code blocks in the response by matching non-indented code fences.
   * This correctly splits multiple consecutive code blocks that might otherwise be
   * merged into a single block by pattern matchers that match from first opening
   * fence to last closing fence.
   */
  private fun findCodeBlocks(response: String): List<CodeBlockMatch> {
    val results = mutableListOf<CodeBlockMatch>()
    val lines = response.lines()
    var i = 0
    var charOffset = 0
    val lineOffsets = mutableListOf<Int>()
    // Pre-compute character offset for each line
    for (line in lines) {
      lineOffsets.add(charOffset)
      charOffset += line.length + 1 // +1 for newline
    }
    while (i < lines.size) {
      val line = lines[i]
      // Match opening code fence: must start at beginning of line (non-indented)
      val openMatch = Regex("^```(\\w*)\\s*$").matchEntire(line)
      if (openMatch != null) {
        val lang = openMatch.groupValues[1]
        val startLineIndex = i
        val codeLines = mutableListOf<String>()
        i++
        var closed = false
        while (i < lines.size) {
          val closeLine = lines[i]
          // Match closing code fence: must be exactly ``` at start of line (non-indented)
          if (closeLine.matches(Regex("^```\\s*$"))) {
            closed = true
            val endLineIndex = i
            val startOffset = lineOffsets[startLineIndex]
            val endOffset = if (endLineIndex < lineOffsets.size - 1) {
              lineOffsets[endLineIndex] + lines[endLineIndex].length - 1
            } else {
              // Last line
              lineOffsets[endLineIndex] + lines[endLineIndex].length - 1
            }
            val code = codeLines.joinToString("\n")
            if (code.isNotBlank()) {
              results.add(CodeBlockMatch(lang, code, startOffset..endOffset))
            }
            i++
            break
          }
          codeLines.add(closeLine)
          i++
        }
        if (!closed) {
          // Unclosed code block - treat remaining lines as code
          val code = codeLines.joinToString("\n")
          if (code.isNotBlank()) {
            val startOffset = lineOffsets[startLineIndex]
            val endOffset = response.length - 1
            results.add(CodeBlockMatch(lang, code, startOffset..endOffset))
          }
        }
      } else {
        i++
      }
    }
    log.debug("findCodeBlocks: found {} individual code blocks", results.size)
    return results
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