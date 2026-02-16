package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor

class ResponseParser(private val processor: PatchProcessor) {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ResponseParser::class.java)
    private val MARKDOWN_HEADER_PATTERN = """(?<![^\n])#+\s*([^\n]+)""".toRegex()
    private val FILE_HEADER_PATTERN = """(?m)^(?:─+|-+)\s*\nFile:\s*(.+?)\s*\n(?:─+|-+)\s*""".toRegex()
  }

  fun parse(
    response: String,
    defaultFile: String? = null
  ): List<ResponseSegment> {
    val initiator = processor.getInitiatorPattern()
    // Auto-close unclosed code blocks
    val normalizedResponse = if (response.contains(initiator) &&
      !response.split(initiator, 2)[1].contains("\n```(?![^\n])".toRegex())
    ) {
      response + "\n```\n"
    } else {
      response
    }

    val codeBlocks = processor.extractCodeBlocks(normalizedResponse)
    if (codeBlocks.isEmpty()) {
      return listOf(ResponseSegment.Markdown(normalizedResponse))
    }

    val headers = collectHeaders(normalizedResponse)
    val segments = mutableListOf<ResponseSegment>()
    var lastEnd = 0

    for ((lang, code) in codeBlocks) {
      val codeBlockPattern = buildCodeBlockPattern(lang, code, normalizedResponse.substring(lastEnd))
      val match = codeBlockPattern.find(normalizedResponse, lastEnd)
      if (match == null) {
        log.warn("Code block not found in response: lang='{}', code='{}'", lang, code)
        continue
      }

      // Add preceding markdown
      if (match.range.first > lastEnd) {
        val markdownContent = normalizedResponse.substring(lastEnd, match.range.first)
        if (markdownContent.isNotBlank()) {
          segments.add(ResponseSegment.Markdown(markdownContent))
        }
      }

      val headerFilename = findHeaderBefore(headers, match.range.first)
      val filename = resolveFilename(headerFilename, defaultFile)

      if (filename != null) {
        val normalizedName = normalizeFilename(filename)
        if (normalizedName.isNotBlank()) {
          val isDiff = isDiffContent(lang, code)
          if (isDiff) {
            segments.add(
              ResponseSegment.DiffBlock(
                filename = normalizedName,
                diff = code,
                originalRange = match.range
              )
            )
          } else {
            segments.add(
              ResponseSegment.NewFileBlock(
                filename = normalizedName,
                language = lang,
                code = code.trimIndent(),
                originalRange = match.range
              )
            )
          }
        } else {
          segments.add(ResponseSegment.Markdown(match.value))
        }
      } else {
        segments.add(ResponseSegment.Markdown(match.value))
      }

      lastEnd = match.range.last + 1
    }

    // Add trailing markdown
    if (lastEnd < normalizedResponse.length) {
      val trailing = normalizedResponse.substring(lastEnd)
      if (trailing.isNotBlank()) {
        segments.add(ResponseSegment.Markdown(trailing))
      }
    }

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

  private fun buildCodeBlockPattern(lang: String, code: String, shouldAppearIn: String?): Regex {
    val regex = Regex("""```${Regex.escape(lang.trim())}\s*\n${Regex.escape(code.trim())}\s*\n```""")
    if (shouldAppearIn != null) {
      val occurrences = regex.findAll(shouldAppearIn).toList()
      if (occurrences.isEmpty()) {
        log.warn("Code block not found in response: lang='{}', code='{}'", lang, code)
      } else if (occurrences.size > 1) {
        log.warn("Multiple code blocks found in response for lang='{}', code='{}'. Using first occurrence.", lang, code)
      } else {
        log.debug("Code block found in response at position {}: lang='{}'", occurrences.first().range, lang)
      }
    }
    return regex
  }
}