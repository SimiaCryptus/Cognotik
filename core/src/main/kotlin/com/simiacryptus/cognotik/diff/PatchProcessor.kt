package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.diff.FileValidators.DIFF_PATTERN
import com.simiacryptus.cognotik.util.LoggerFactory

interface PatchProcessor {
    val label: String
    val patchFormatPrompt: String
        get() = """
  Response format:
  * Response should use one or more code patches in diff format within ```diff code blocks.
  * Each diff should be preceded by a header that identifies the file being modified.
  * The diff format should use + for line additions, - for line deletions.
  * The diff should include 2 lines of context before and after every change.
  * Separate code blocks with a single blank line.
  * The content inside the code blocks should be indented - this is CRITICAL for correct parsing.
  
  Example:
  
  Here are the patches:
  
  ### src/utils/exampleUtils.js
  ```diff
    const b = 2;
    function exampleFunction() {
  -   return b + 1;
  +   return b + 2;
    }
  ```
  
  ### tests/exampleUtils.test.js
  ${TRIPLE_TILDE}diff
    const assert = require('assert');
    const { exampleFunction } = require('../src/utils/exampleUtils'); 
    describe('exampleFunction', () => {
  -   it('should return 3', () => {
  +   it('should return 4', () => {
        assert.equal(exampleFunction(), 4);
      });
    });
  ```
  
  ### README.md
  ${TRIPLE_TILDE}md
    This file contains utility functions for the project.
    Example usage:
    ${TRIPLE_TILDE}js
      print("Something")
    ${TRIPLE_TILDE}
  ${TRIPLE_TILDE}
  
  Alternately, the patch can be provided as a snippet of updated code with context.
  This is useful when the patch is small and can be applied directly, when creating the delete lines is cumbersome, or when creating a new file.
  
        """

    fun generatePatch(oldCode: String, newCode: String): String
    fun applyPatch(source: String, patch: String): String

    /**
     * Gets the regex pattern that initiates a code block
     */
    fun getInitiatorPattern(): Regex {
        return "(?s)$TRIPLE_TILDE\\w*\n".toRegex()
    }

    fun apply(
        originalCode: String, response: String, filename: String? = null
    ): DiffApplicationResult {
        val matches = DIFF_PATTERN.findAll(response).distinct()
        var currentCode = originalCode
        val validator = FileValidators.getValidator(filename)
        val originalCodeErrors = validator.validateGrammar(originalCode)
        val newErrors = matches.flatMap { diffBlock ->
            val response: String = diffBlock.groupValues[1]
            try {
                if (response.length > FileValidators.MAX_DIFF_SIZE_CHARS) {
                    throw IllegalArgumentException("Diff size exceeds maximum limit")
                }
                val newCode = applyPatch(currentCode, response).replace("\r", "")
                val validationErrors = validator.validateGrammar(newCode)
                currentCode = newCode
                return@flatMap validationErrors
            } catch (e: Throwable) {
                return@flatMap emptyList()
            }
        }.toList().filter {
            originalCodeErrors.none { originalError ->
                it.message == originalError.message
            }
        }
        if (newErrors.isNotEmpty()) {
            log.error("Error applying diff: ${newErrors.joinToString("\n") { it.message }}")
        }
        return DiffApplicationResult(currentCode, newErrors, validator = validator)
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
            val (openInfo,closeInfo) = t
            results.add(CodeBlockMatch(
                language = openInfo.language,
                code = code,
                range = openInfo.lineIndex until closeInfo.lineIndex + 1
            ))
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
            if (trailing.isNotBlank()) {
                segments.add(ResponseSegment.Markdown(trailing))
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
        val log = LoggerFactory.getLogger(PatchProcessor::class.java)

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

