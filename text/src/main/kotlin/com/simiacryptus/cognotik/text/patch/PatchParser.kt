package com.simiacryptus.cognotik.text.patch

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString

interface PatchParser {

  sealed class ResponseSegment(val filename: String?, val content: String) {
    class Markdown(content: String) : ResponseSegment(null, content)
    class NewFileBlock(
      filename: String,
      val language: String,
      content: String,
      val originalRange: IntRange
    ) : ResponseSegment(filename, content)

    class DiffBlock(
      filename: String,
      content: String,
      val originalRange: IntRange
    ) : ResponseSegment(filename, content)

    companion object {
      private val log = LoggerFactory.getLogger(ResponseSegment::class.java)
    }


    fun calcFilename(root: Path): Path? {
      try {
        val root = root.normalize().removeAllUpperDirectories()
        var file = filename?.let { root.resolve(it).normalize() }
          ?: throw IllegalStateException("Cannot calculate filename for segment without filename: $this")
        // look for repeated segments like "src/utils/src/utils/exampleUtils.js" and reduce to single path if not found
        val parts = Path.of(filename).normalize().toList().map { it.toString() }
        // Try to find a repeated prefix subsequence in the path parts
        for (len in 1..parts.size / 2) {
          val prefix = parts.subList(0, len)
          val nextChunk = parts.subList(len, minOf(len * 2, parts.size))
          val subList = nextChunk.subList(0, minOf(prefix.size, nextChunk.size))
          if (prefix == subList && prefix.size <= nextChunk.size) {
            // Found a repeated prefix - reconstruct without the duplication
            val deduplicated = parts.subList(len, parts.size).joinToString("/")
            val candidate = root.resolve(deduplicated).normalize()
            file = candidate
            break
          }
        }
        // Also check if the filename itself starts with a prefix that matches part of the root path
        val rootParts = root.normalize().toList().map { it.toString() }
        val fileParts = Path.of(filename).normalize().toList().map { it.toString() }
        // Check if the file path starts with segments that overlap with the end of the root path
        for (overlap in minOf(rootParts.size, fileParts.size) downTo 1) {
          val rootSuffix = rootParts.subList(rootParts.size - overlap, rootParts.size)
          val filePrefix = fileParts.subList(0, overlap)
          if (rootSuffix == filePrefix) {
            val trimmedPath = fileParts.subList(overlap, fileParts.size).joinToString("/")
            if (trimmedPath.isNotEmpty()) {
              val candidate = root.resolve(trimmedPath).normalize()
              file = candidate
              break
            }
          }
        }
        return root.relativize(file)
      } catch (e: Exception) {
        log.debug("Error calculating filename for segment '{}': {}", filename, e.message)
        return null
      }
    }
    fun Path.removeAllUpperDirectories(): Path =
      this.pathString.split("/").dropWhile { it == "." || it == ".." }.joinToString("/").let { Path.of(it) }

    fun removeCodeFences(): String {
      return when {
        content.trim().startsWith(TRIPLE_TILDE) && content.endsWith(TRIPLE_TILDE) ->
          content.trim().lines()
            .drop(1)
            .dropLast(1)
            .joinToString("\n")

        else -> content
      }
    }

  }

  val patchFormatPrompt: String
    get() = """
Response format:
* Response should use one or more code patches in diff format within ```diff code blocks
* Each diff should be preceded by a header that identifies the file being modified.
* Files can also be given in raw (non-diff) form
* For redundant clarity, each section should be preceded by `<<<FILE filename>>` or `<<<DIFF filename>>` and followed by `<<<END>>>` marker lines 
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
<<<FILE src/utils/exampleUtils.js>>>
${TRIPLE_TILDE}diff
  const assert = require('assert');
  const { exampleFunction } = require('../src/utils/exampleUtils'); 
  describe('exampleFunction', () => {
  it('should return 4', () => {
    assert.equal(exampleFunction(), 4);
  });
${TRIPLE_TILDE}
<<<END>>>

### README.md
(This file shows proper markdown)
<<<FILE src/utils/exampleUtils.js>>>
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
    return parse(response, defaultFile, null)
  }

  fun parse(
    response: String,
    defaultFile: String? = null,
    root: Path? = null
  ): List<ResponseSegment> {
    log.debug("Parsing response: {} chars, defaultFile={}", response.length, defaultFile)
    if (response.isBlank()) {
      log.debug("Response is blank, returning empty list")
      return emptyList()
    }
    // Check for explicit marker syntax first
    return if (hasExplicitMarkers(response)) {
      log.debug("Detected explicit <<<FILE>>> markers, using explicit parser")
      parseExplicitMarkers(response, defaultFile).maybeResolveFilenames(root)
    } else {
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
      segments.maybeResolveFilenames(root)
    }
  }

  private fun getInitiatorPattern() = "(?s)${TRIPLE_TILDE}\\w*\n".toRegex()

  private fun hasExplicitMarkers(response: String): Boolean {
    return EXPLICIT_BLOCK_PATTERN.containsMatchIn(response)
  }

  private fun parseExplicitMarkers(response: String, defaultFile: String?): List<ResponseSegment> {
    val segments = mutableListOf<ResponseSegment>()
    var lastEnd = 0
     var lastExplicitFilename: String? = null
     var lastExplicitBlockType: String? = null
    for (match in EXPLICIT_BLOCK_PATTERN.findAll(response)) {
      // Add any preceding text as markdown
      if (match.range.first > lastEnd) {
        val preceding = response.substring(lastEnd, match.range.first).trim()
        if (preceding.isNotBlank()) {
           // Check for chained code blocks between <<<END>>> and next <<<DIFF/FILE>>>
           // These are continuation diffs for the previous file
           val chainedSegments = parseChainedCodeBlocks(preceding, lastExplicitFilename, lastExplicitBlockType)
           if (chainedSegments != null) {
             segments.addAll(chainedSegments)
           } else {
             segments.add(ResponseSegment.Markdown(preceding))
           }
        }
      }
      val blockType = match.groupValues[1].uppercase()
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
                content = code.trimEnd(),
                originalRange = IntRange.EMPTY
              )
            )
          } else {
            segments.add(
              ResponseSegment.NewFileBlock(
                filename = normalizedName,
                language = "",
                content = code.trimIndent().trimEnd(),
                originalRange = IntRange.EMPTY
              )
            )
          }
           lastExplicitFilename = normalizedName
           lastExplicitBlockType = blockType
        } else {
          segments.add(ResponseSegment.Markdown(match.value))
           lastExplicitFilename = null
           lastExplicitBlockType = null
        }
      } else {
        segments.add(ResponseSegment.Markdown(match.value))
         lastExplicitFilename = null
         lastExplicitBlockType = null
      }
      lastEnd = match.range.last + 1
    }
    // Add trailing text as markdown
    if (lastEnd < response.length) {
      val trailing = response.substring(lastEnd).trim()
      if (trailing.isNotBlank()) {
         // Check for chained code blocks after the last <<<END>>>
         val chainedSegments = parseChainedCodeBlocks(trailing, lastExplicitFilename, lastExplicitBlockType)
         if (chainedSegments != null) {
           segments.addAll(chainedSegments)
         } else {
           segments.add(ResponseSegment.Markdown(trailing))
         }
      }
    }
    return segments
  }

   /**
    * Parses text that may contain chained code blocks (code fences followed by <<<END>>> markers)
    * that should inherit the filename from a preceding explicit marker block.
    * Returns null if the text doesn't look like chained code blocks.
    */
   private fun parseChainedCodeBlocks(
     text: String,
     inheritedFilename: String?,
     inheritedBlockType: String?
   ): List<ResponseSegment>? {
     if (inheritedFilename == null) return null
     // Pattern: optional whitespace/markdown, then one or more (```...``` <<<END>>>) sequences
     val chainedBlockPattern = """```(\w*)\n(.*?)```\s*\n\s*<<<END>>>""".toRegex(RegexOption.DOT_MATCHES_ALL)
     val matches = chainedBlockPattern.findAll(text).toList()
     if (matches.isEmpty()) return null
     // Verify that the non-matched content is only whitespace/blank lines
     var remaining = text
     for (match in matches) {
       remaining = remaining.replace(match.value, "")
     }
     remaining = remaining.replace(END_MARKER_LINE_PATTERN, "").trim()
     if (remaining.isNotBlank()) {
       // There's significant non-code-block content; not a pure chain
       return null
     }
     val segments = mutableListOf<ResponseSegment>()
     for (match in matches) {
       val lang = match.groupValues[1]
       val code = match.groupValues[2]
       val isDiff = (inheritedBlockType == "DIFF") || isDiffContent(lang, code)
       if (isDiff) {
         segments.add(
           ResponseSegment.DiffBlock(
             filename = inheritedFilename,
             content = code.trimEnd(),
             originalRange = IntRange.EMPTY
           )
         )
       } else {
         segments.add(
           ResponseSegment.NewFileBlock(
             filename = inheritedFilename,
             language = lang,
             content = code.trimIndent().trimEnd(),
             originalRange = IntRange.EMPTY
           )
         )
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
    log.debug("Found {} <<<FILE>>> marker lines in response", diffMarkers.size)
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
                content = code,
                originalRange = matchRange
              )
            )
          } else {
            segments.add(
              ResponseSegment.NewFileBlock(
                filename = normalizedName,
                language = lang,
                content = code.trimIndent(),
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

  private fun findDiffMarkerBefore(markers: List<Pair<IntRange, String>>, position: Int): String? {
    return markers
      .filter { it.first.last <= position }
      .maxByOrNull { it.first.last }
      ?.second
  }

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
    val firstChars = lines.map { it.firstOrNull() }.groupBy { it }.mapValues { it.value.size }
    return firstChars.filterKeys {
      when (it) {
        null -> false
        ' ' -> false
        '\t' -> false
        '@' -> false
        '-' -> false
        '+' -> false
        else -> true
      }
    }.isEmpty()
  }
  private fun List<ResponseSegment>.maybeResolveFilenames(root: Path?): List<ResponseSegment> {
    if (root == null) return this
    return map { segment ->
      if (segment.filename == null) return@map segment
      try {
        val resolved = segment.calcFilename(root) ?: return@map segment
        val relativePath = root.relativize(resolved).toString().replace('\\', '/')
        when (segment) {
          is ResponseSegment.DiffBlock -> ResponseSegment.DiffBlock(
            filename = relativePath,
            content = segment.content,
            originalRange = segment.originalRange
          )

          is ResponseSegment.NewFileBlock -> ResponseSegment.NewFileBlock(
            filename = relativePath,
            language = segment.language,
            content = segment.content,
            originalRange = segment.originalRange
          )

          is ResponseSegment.Markdown -> segment
        }
      } catch (e: Exception) {
        log.debug("Failed to resolve filename '{}' against root '{}': {}", segment.filename, root, e.message)
        segment
      }
    }
  }


  companion object {
    private val log = LoggerFactory.getLogger(PatchParser::class.java)

    private val EXPLICIT_BLOCK_PATTERN = """<<<(DIFF|FILE)\s+(.+?)>>>\n(.*?)<<<END>>>"""
      .toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private val DIFF_MARKER_LINE_PATTERN = """<<<(?:DIFF|FILE)\s+(.+?)>>>""".toRegex(RegexOption.IGNORE_CASE)

    private val END_MARKER_LINE_PATTERN = """<<<END>>>""".toRegex(RegexOption.IGNORE_CASE)


    private val MARKDOWN_HEADER_PATTERN = """(?<![^\n])#+\s*([^\n]+)""".toRegex()

    private val FILE_HEADER_PATTERN = """(?m)^(?:─+|-+)\s*\nFile:\s*(.+?)\s*\n(?:─+|-+)\s*""".toRegex()

    const val TRIPLE_TILDE = """```"""
  }
}