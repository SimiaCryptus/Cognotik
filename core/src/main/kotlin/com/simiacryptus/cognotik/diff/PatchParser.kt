package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.util.LoggerFactory

interface PatchParser {

  sealed class ResponseSegment {
    data class Markdown(val content: String) : ResponseSegment()
    data class NewFileBlock(
      val filename: String,
      val language: String,
      val code: String,
      val originalRange: IntRange
    ) : ResponseSegment()

    data class DiffBlock(
      val filename: String,
      val diff: String,
      val originalRange: IntRange
    ) : ResponseSegment()
  }

  val patchFormatPrompt: String
    get() = """
Response format:
* Response should use one or more code patches in diff format within ```diff code blocks
* Each diff should be preceded by a header that identifies the file being modified.
* For redundant clarity, each section should be preceded by `<<<DIFF filename>>` and followed by `<<<END>>>` marker lines 
* The diff format should use + for line additions, - for line deletions.
* The diff should include 2 lines of context before and after every change.
* Separate code blocks with a single blank line.
* The content inside the code blocks should be indented.

Examples:

### src/utils/exampleUtils.js
<<<DIFF src/utils/exampleUtils.js>>>
${TRIPLE_TILDE}diff
  const b = 2;
  function exampleFunction() {
-   return b + 1;
+   return b + 2;
  }
${TRIPLE_TILDE}
<<<END>>>

### tests/exampleUtils.test.js
<<<DIFF src/utils/exampleUtils.js>>>
${TRIPLE_TILDE}diff
  const assert = require('assert');
  const { exampleFunction } = require('../src/utils/exampleUtils'); 
  describe('exampleFunction', () => {
  -   it('should return 3', () => {
  +   it('should return 4', () => {
    assert.equal(exampleFunction(), 4);
  });
${TRIPLE_TILDE}
<<<END>>>

### README.md

<<<DIFF src/utils/exampleUtils.js>>>
${TRIPLE_TILDE}md
  This file contains utility functions for the project.
  Example usage:
  ${TRIPLE_TILDE}js
    print("Something")
  ${TRIPLE_TILDE}
${TRIPLE_TILDE}
<<<END>>>

"""

  fun parse(
    response: String,
    defaultFile: String? = null
  ): List<ResponseSegment> {
    log.debug("Parsing response: {} chars, defaultFile={}", response.length, defaultFile)
    if (response.isBlank()) {
      log.debug("Response is blank, returning empty list")
      return emptyList()
    }
    // Check for explicit marker syntax first
    if (hasExplicitMarkers(response)) {
      log.debug("Detected explicit <<<PATCH/DIFF>>> markers, using explicit parser")
      return parseExplicitMarkers(response, defaultFile)
    }
    val initiator = this.getInitiatorPattern()
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
    val codeBlockMatches = this.getMarkdownCodeBlockMatches(normalizedResponse)
    val segments = markdowns(codeBlockMatches, normalizedResponse, normalizedResponseLines, defaultFile)
    log.debug("Parsed {} total segments from response", segments.size)
    return segments
  }

  /**
   * Gets the regex pattern that initiates a code block
   */
  private fun getInitiatorPattern(): Regex {
    return "(?s)${TRIPLE_TILDE}\\w*\n".toRegex()
  }

  private fun hasExplicitMarkers(response: String): Boolean {
    return EXPLICIT_BLOCK_PATTERN.containsMatchIn(response)
  }

  /**
   * Parses a response that uses explicit <<<PATCH filename>>> / <<<DIFF filename>>> ... <<<END>>> markers.
   * This avoids all ambiguity with embedded markdown code fences.
   */
  private fun parseExplicitMarkers(response: String, defaultFile: String?): List<ResponseSegment> {
    val segments = mutableListOf<ResponseSegment>()
    var lastEnd = 0
    for (match in EXPLICIT_BLOCK_PATTERN.findAll(response)) {
      // Add any preceding text as markdown
      if (match.range.first > lastEnd) {
        val preceding = response.substring(lastEnd, match.range.first).trim()
        if (preceding.isNotBlank()) {
          segments.add(ResponseSegment.Markdown(preceding))
        }
      }
      val blockType = match.groupValues[1].uppercase() // PATCH or DIFF
      val rawFilename = match.groupValues[2].trim()
      val code = match.groupValues[3]
      val filename = normalizeFilename(rawFilename).ifBlank {
        defaultFile ?: rawFilename
      }
      log.debug("Explicit marker block: type={}, filename='{}', code length={}", blockType, filename, code.length)
      if (filename.isNotBlank()) {
        val normalizedName = normalizeFilename(filename)
        if (normalizedName.isNotBlank()) {
          if (blockType == "DIFF" || isDiffContent("", code)) {
            segments.add(
              ResponseSegment.DiffBlock(
                filename = normalizedName,
                diff = code.trimEnd(),
                originalRange = IntRange.EMPTY
              )
            )
          } else {
            segments.add(
              ResponseSegment.NewFileBlock(
                filename = normalizedName,
                language = "",
                code = code.trimIndent().trimEnd(),
                originalRange = IntRange.EMPTY
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
    // Add trailing text as markdown
    if (lastEnd < response.length) {
      val trailing = response.substring(lastEnd).trim()
      if (trailing.isNotBlank()) {
        segments.add(ResponseSegment.Markdown(trailing))
      }
    }
    return segments
  }

  private data class CodeBlockMatch(
    val language: String, val code: String, val range: IntRange
  )

  private data class FenceInfo(
    val lineIndex: Int,
    val indentation: Int,
    val language: String,
    val isOpening: Boolean, // true = definitely opening (has language), false = closing/ambiguous
    val raw: String
  )

  private fun getMarkdownCodeBlockMatches(text: String): List<CodeBlockMatch> {
    val lines = text.lines()
    val fencePattern = """^(\s*)```(.*)$""".toRegex()
    val fences = mutableListOf<FenceInfo>()
    for ((lineIndex, line) in lines.withIndex()) {
      val match = fencePattern.matchEntire(line) ?: continue
      val suffix = match.groupValues[2].trim()
      // A fence is "opening" if it has a language keyword (non-empty suffix that isn't just whitespace)
      // A bare ``` is ambiguous - could be opening or closing
      fences.add(
        FenceInfo(
          lineIndex = lineIndex,
          indentation = match.groupValues[1].length,
          language = suffix,
          isOpening = suffix.isNotEmpty() && !suffix.startsWith("`"),
          raw = line
        )
      )
    }

    // Now find top-level code blocks by pairing open/close fences
    // Fences indented deeper than the opening fence are treated as nested content
    val result = mutableListOf<Pair<Pair<FenceInfo, FenceInfo>, String>>()
    var i = 0
    while (i < fences.size) {
      val openFence = fences[i]
      var closeIndex = -1
      var j = i
      var depth = 1
      while (++j < fences.size) {
        val candidate = fences[j]
        if (candidate.indentation < openFence.indentation) {
          break
        } else if (candidate.indentation == openFence.indentation) {
          if (candidate.isOpening) {
            depth++
          } else {
            depth--
            if (depth == 0) {
              closeIndex = j
              break
            }
          }
        }
      }
      if (closeIndex == -1) {
        log.warn("No closing fence found for opening fence at line ${openFence.lineIndex}: '${openFence.raw}'")
        i++
        continue // No matching close found, skip this fence
      }
      val closeFence = fences[closeIndex]
      val contentLines = lines.subList(openFence.lineIndex + 1, closeFence.lineIndex)
      val code = contentLines.joinToString("\n")
      result.add((openFence to closeFence) to code)
      i = closeIndex + 1
    }

    // Convert to CodeBlockMatch format
    val results = mutableListOf<CodeBlockMatch>()
    for ((t, code) in result) {
      val (openInfo, closeInfo) = t
      results.add(
        CodeBlockMatch(
          language = openInfo.language,
          code = code,
          range = openInfo.lineIndex until closeInfo.lineIndex + 1
        )
      )
    }
    return results
  }

  private fun markdowns(
    codeBlockMatches: List<CodeBlockMatch>,
    normalizedResponse: String,
    normalizedResponseLines: List<String>,
    defaultFile: String?
  ): List<ResponseSegment> {
    if (codeBlockMatches.isEmpty()) {
      log.debug("No code blocks found in response")
      return listOf(ResponseSegment.Markdown(stripDiffMarkerLines(normalizedResponse)))
    }
    log.debug("Found {} code blocks in response", codeBlockMatches.size)

    val headers = collectHeaders(normalizedResponse)
    log.debug("Found {} headers in response", headers.size)
    val diffMarkers = collectDiffMarkers(normalizedResponse)
    log.debug("Found {} <<<DIFF>>> marker lines in response", diffMarkers.size)
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
        val cleanedMarkdown = stripDiffMarkerLines(markdownContent)
        if (cleanedMarkdown.isNotBlank()) {
          segments.add(ResponseSegment.Markdown(cleanedMarkdown))
        }
      } else {
        log.debug("No preceding markdown for code block at range {}", matchRange)
      }

      // Prefer <<<DIFF filename>>> marker if present before this code block, then fall back to headers
      val diffMarkerFilename = findDiffMarkerBefore(diffMarkers, lineStartPos)
      val headerFilename = diffMarkerFilename ?: findHeaderBefore(headers, lineStartPos)
      val filename = resolveFilename(headerFilename, defaultFile)
      log.debug("Resolved filename for code block: headerFilename='{}', resolved='{}'", headerFilename, filename)

      if (filename != null) {
        val normalizedName = normalizeFilename(filename)
        log.debug("Normalized filename: '{}' -> '{}'", filename, normalizedName)
        if (normalizedName.isNotBlank()) {
          val isDiff = (diffMarkerFilename != null) || isDiffContent(lang, code)
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
          segments.add(
            ResponseSegment.Markdown(
              normalizedResponseLines.subList(lineStart, lineEnd + 1).joinToString("\n")
            )
          )
        }
      } else {
        log.debug("No filename resolved, treating code block as markdown")
        segments.add(
          ResponseSegment.Markdown(
            normalizedResponseLines.subList(lineStart, lineEnd + 1).joinToString("\n")
          )
        )
      }

      lastEnd = lineEnd + 1
    }

    // Add trailing markdown
    if (lastEnd < normalizedResponseLines.size) {
      val trailing = normalizedResponseLines.subList(lastEnd, normalizedResponseLines.size).joinToString("\n")
      val cleanedTrailing = stripDiffMarkerLines(trailing)
      if (cleanedTrailing.isNotBlank()) {
        segments.add(ResponseSegment.Markdown(cleanedTrailing))
      }
    }
    return segments
  }

  private fun normalizeFilename(filename: String, maxIterations: Int = 10): String {
    if (filename.isBlank()) {
      log.debug("normalizeFilename called with blank input")
      return ""
    }
    val result = repeat(filename, maxIterations) {
      it.trim()
        // Remove common prefixes
        .removePrefix("Code:")
        .removePrefix("code:")
        .removePrefix("File:")
        .removePrefix("file:")
        .removePrefix("Path:")
        .removePrefix("path:")
        .removePrefix("Filename:")
        .removePrefix("filename:")
        .removePrefix("Modified:")
        .removePrefix("modified:")
        .removePrefix("Updated:")
        .removePrefix("updated:")
        .removePrefix("Changed:")
        .removePrefix("changed:")
        .removePrefix("Edit:")
        .removePrefix("edit:")
        .removePrefix("Patch:")
        .removePrefix("patch:")
        // Remove common suffixes
        .removeSuffix(":")
        .removeSuffix(".")
        // Remove quotes and backticks
        .removePrefix("\"").removeSuffix("\"")
        .removePrefix("'").removeSuffix("'")
        .removePrefix("`").removeSuffix("`")
        // Remove any leading number with a period (e.g. "1. foo.kt")
        .replace(Regex("^\\d+\\.\\s*"), "")
        // Remove markdown formatting
        .replace("**", "")
        .replace("*", "")
        // Clean up whitespace
        .trim()
        // Remove code block language indicators mistaken for filenames
        .let { name ->
          if (name.matches(
              Regex(
                "^(java|kotlin|kt|js|javascript|python|py|cpp|c|cs|go|rust|rs|php|rb|ruby|swift|scala|clj|clojure|sh|bash|sql|html|css|xml|json|yaml|yml|toml|ini|cfg|conf|config|properties|gradle|maven|pom|dockerfile|docker|makefile|make|cmake|bazel|build)$",
                RegexOption.IGNORE_CASE
              )
            )
          ) "" else name
        }
        .trim()
    }
    if (result != filename) {
      log.debug("normalizeFilename: '{}' -> '{}'", filename, result)
    }
    return result
  }

  private fun <T> repeat(
    original: T,
    maxIterations: Int,
    function: (T) -> T
  ): T {
    var current = original
    repeat(maxIterations) {
      val next = (function)(current)
      if (next == current) return current
      current = next
    }
    log.debug("repeat() reached max iterations ({})", maxIterations)
    return current
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

  /**
   * Collects all <<<DIFF filename>>> marker lines (without requiring <<<END>>>).
   * Returns a list of (charRange, filename) pairs.
   */
  private fun collectDiffMarkers(response: String): List<Pair<IntRange, String>> {
    val markers = mutableListOf<Pair<IntRange, String>>()
    DIFF_MARKER_LINE_PATTERN.findAll(response).forEach { match ->
      val filename = normalizeFilename(match.groupValues[1].trim())
      if (filename.isNotBlank()) {
        markers.add(match.range to filename)
      }
    }
    return markers
  }

  /**
   * Finds the closest <<<DIFF filename>>> marker that appears before the given position.
   */
  private fun findDiffMarkerBefore(markers: List<Pair<IntRange, String>>, position: Int): String? {
    return markers
      .filter { it.first.last <= position }
      .maxByOrNull { it.first.last }
      ?.second
  }

  /**
   * Strips <<<DIFF filename>>>, <<<PATCH filename>>>, and <<<END>>> marker lines from text
   * so they don't appear as artifacts in markdown output.
   */
  private fun stripDiffMarkerLines(text: String): String {
    return text
      .replace(DIFF_MARKER_LINE_PATTERN, "")
      .replace(END_MARKER_LINE_PATTERN, "")
      .trim()
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

  companion object {
    private val log = LoggerFactory.getLogger(PatchParser::class.java)

    /**
     * Pattern matching explicit <<<PATCH filename>>> or <<<DIFF filename>>> ... <<<END>>> blocks.
     * This syntax is unambiguous even when the content contains markdown code fences.
     * Group 1: block type (PATCH or DIFF)
     * Group 2: filename
     * Group 3: content between markers
     */
    private val EXPLICIT_BLOCK_PATTERN =
      """<<<(PATCH|DIFF)\s+(.+?)>>>\n(.*?)<<<END>>>""".toRegex(
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
      )

    /**
     * Pattern matching a single <<<DIFF filename>>> or <<<PATCH filename>>> marker line.
     * Used to extract filenames and strip these lines from markdown output.
     */
    private val DIFF_MARKER_LINE_PATTERN =
      """<<<(?:PATCH|DIFF)\s+(.+?)>>>""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Pattern matching <<<END>>> marker lines for stripping from output.
     */
    private val END_MARKER_LINE_PATTERN =
      """<<<END>>>""".toRegex(RegexOption.IGNORE_CASE)


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

    const val TRIPLE_TILDE = """```"""
  }
}