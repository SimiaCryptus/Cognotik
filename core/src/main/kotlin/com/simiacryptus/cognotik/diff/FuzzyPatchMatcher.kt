package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.diff.FuzzyPatchMatcher.Companion.LineType.*
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.floor
import kotlin.math.max

/**
 * A robust patch processor designed to generate and apply diffs between two text versions with high tolerance for inaccuracies.
 * This class employs a sophisticated fuzzy matching algorithm, making it resilient to patches where context lines don't perfectly
 * align with the source code. This is common when dealing with minor formatting changes, whitespace differences, or other
 * slight modifications that might occur.
 *
 * Key features include:
 * - **Fuzzy Matching:** Uses Levenshtein distance to match lines that are similar but not identical.
 * - **Snippet Patching:** Can apply patches that are just blocks of code without the standard `+` or `-` diff markers,
 *   by intelligently finding the best location to apply the snippet.
 * - **Robust Linking Strategy:** Employs a multi-pass approach to link lines between the source and patch:
 *   1. Links unique, exact matches first as reliable anchors.
 *   2. Expands matches to adjacent lines, using fuzzy logic if enabled.
 *   3. Recursively finds common subsequences in remaining unmatched blocks.
 * - **Moved Line Detection:** Identifies blocks of code that have been moved from one location to another.
 *
 * @param contextSize The number of context lines to include before and after a change in a generated patch.
 * @param maxRecursionDepth The maximum depth for recursive subsequence linking to prevent stack overflows.
 * @param levenshteinThresholdDivisor A divisor used to calculate the Levenshtein distance threshold for fuzzy matching.
 *                                    A smaller value makes matching stricter. The threshold is `lineLength / divisor`.
 * @param minLineLengthForFuzzyMatch The minimum length a line must have to be considered for fuzzy matching.
 * @param enableFuzzyMatching A flag to enable or disable fuzzy matching based on Levenshtein distance.
 * @param enableSnippetPatching A flag to enable or disable the ability to apply patches that are just code snippets.
 * @param snippetMatchThreshold The minimum percentage of lines that must match for a snippet patch to be applied.
 * @param requireAnchorMatch A flag that, when applying snippet patches, requires either the first or last line to match exactly.
 */
open class FuzzyPatchMatcher(
  private val contextSize: Int = DEFAULT_CONTEXT_SIZE,
  private val maxRecursionDepth: Int = MAX_RECURSION_DEPTH,
  private val levenshteinThresholdDivisor: Int = LEVENSHTEIN_THRESHOLD_DIVISOR,
  private val minLineLengthForFuzzyMatch: Int = MIN_LINE_LENGTH_FOR_FUZZY_MATCH,
  private val enableFuzzyMatching: Boolean = true,
  private val enableSnippetPatching: Boolean = true,
  private val snippetMatchThreshold: Double = 0.8,
  private val requireAnchorMatch: Boolean = true,
) : PatchProcessor {
  /** A descriptive label for this patch processor. */
  override val label: String = "Fuzzy Patch Matcher"

  /**
   * A detailed instructional prompt intended for a language model (LLM).
   * This prompt explains the expected patch format, including the use of diff blocks, file headers,
   * context lines, and the alternative snippet format. Providing this to an LLM helps ensure that
   * the patches it generates are compatible with this processor's `applyPatch` method.
   */
  override val patchFormatPrompt = """
      Response should use one or more code patches in diff format within ```diff code blocks.
      Each diff should be preceded by a header that identifies the file being modified.
      The diff format should use + for line additions, - for line deletions.
      The diff should include 2 lines of context before and after every change.

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
      ```diff

       const assert = require('assert');
       const { exampleFunction } = require('../src/utils/exampleUtils');

       describe('exampleFunction', () => {
      -   it('should return 3', () => {
      +   it('should return 4', () => {
           assert.equal(exampleFunction(), 4);
         });
       });
      ```

      Alternately, the patch can be provided as a snippet of updated code with context.
      This is useful when the patch is small and can be applied directly, when creating the delete lines is cumbersome, or when creating a new file.
      """.trimIndent()

  /**
   * Generates a diff patch that transforms the `oldCode` into the `newCode`.
   * The process involves:
   * 1. Parsing both old and new code into lists of `LineRecord` objects, creating linked lists.
   * 2. Linking lines that match exactly and are unique across both versions to establish reliable anchors.
   * 3. Expanding these matches by linking adjacent lines.
   * 4. Recursively linking common subsequences within the remaining unmatched blocks to find more matches.
   * 5. Identifying and marking lines that have been moved from one location to another.
   * 6. Generating a raw, preliminary diff from the linked `newCode` records.
   * 7. Cleaning up the raw diff by truncating excessive context, ensuring correct line order (delete before add), and removing redundant change pairs.
   * 8. Formatting the cleaned-up list of `LineRecord`s into the final diff string.
   *
   * @param oldCode The original source code.
   * @param newCode The modified source code.
   * @return A string representing the diff in the specified format, or an empty string if there are no changes.
   */

  override fun generatePatch(oldCode: String, newCode: String): String {
    log.info("Starting patch generation process")
    if (oldCode == newCode) {
      log.debug("No changes detected, returning empty patch")
      return ""
    }
    // Handle edge cases
    if (oldCode.isBlank() && newCode.isNotBlank()) {
      return newCode.lines().joinToString("\n") { "+ $it" }
    }
    if (newCode.isBlank() && oldCode.isNotBlank()) {
      return oldCode.lines().joinToString("\n") { "- $it" }
    }

    val sourceLines = setLinks(oldCode.lines().mapIndexed { index, line -> LineRecord(index, line) })
    val newLines = setLinks(newCode.lines().mapIndexed { index, line -> LineRecord(index, line) })
    linkUniqueMatchingLines(sourceLines, newLines)
    linkAdjacentMatchingLines(sourceLines, null)
    subsequenceLinking(sourceLines, newLines, levenshteinDistance = null)
    log.debug("Parsed and linked source lines: ${sourceLines.size}, new lines: ${newLines.size}")
    markMovedLines(newLines)
    val longDiff = newToPatch(newLines)
    val shortDiff = truncateContext(longDiff)
    fixPatchLineOrder(shortDiff)
    annihilateNoopLinePairs(shortDiff)
    log.debug("Generated diff with ${shortDiff.size} lines after processing")
    val patch = StringBuilder()

    shortDiff.forEach { line ->
      when (line.type) {
        CONTEXT -> patch.append("  ${line.line}\n")
        ADD -> patch.append("+ ${line.line}\n")
        DELETE -> patch.append("- ${line.line}\n")
      }
    }
    return patch.toString().trimEnd()
  }

  /**
   * Applies a given patch to the source code.
   * This method is designed to be flexible and can handle two types of patches:
   *
   * 1.  **Standard Diff Patch:** A patch containing lines prefixed with `+`, `-`, or `  ` (for context).
   *     For these, it parses both the source and the patch, links them using the fuzzy matching algorithm,
   *     and then reconstructs the source code based on these established links.
   * 2.  **Snippet Patch:** A block of code without any `+` or `-` markers. If detected, it delegates
   *     to `applySnippetPatch` to find the best location in the source to replace with the snippet.
   * @param source The original source code to be patched.
   * @param patch The diff patch to apply.
   * @return The patched source code. If the patch cannot be applied, it may return the original source.
   */
  override fun applyPatch(source: String, patch: String): String {
    if (patch.isBlank()) {
      log.debug("Empty patch provided, returning original source")
      return source
    }

    val hasAddOrDeleteLines = patch.lines().any { line ->
      val trimmed = line.trimStart()
      trimmed.startsWith("+") || trimmed.startsWith("-")
    }
    if (!hasAddOrDeleteLines) {
      log.info("Patch with context lines only detected. Attempting to apply as snippet patch.")
      return applySnippetPatch(source, patch)
    }

    val sourceLines = setLinks(source.lines().mapIndexed { index, line -> LineRecord(index, line) })
    val patchLines = parsePatchLines(patch, sourceLines)
    log.debug("Parsed source lines: ${sourceLines.size}, initial patch lines: ${patchLines.size}")
    val levenshteinDistance1 = LevenshteinDistance()
    linkUniqueMatchingLines(sourceLines, patchLines)
    linkAdjacentMatchingLines(sourceLines, levenshteinDistance1)
    subsequenceLinking(sourceLines, patchLines, levenshteinDistance = levenshteinDistance1)
    log.debug("Filtered patch lines: ${patchLines.size}")
    val result = generatePatchedText(sourceLines, patchLines.filter { it.line != null && normalizeLine(it.line).isNotEmpty() })
    return result.joinToString("\n").trim()
  }

  /**
   * Removes redundant pairs of DELETE and ADD lines that have the same content (a "no-op" change).
   * This situation can arise during diff generation, especially when a line is marked as "moved" but
   * ultimately ends up in a position adjacent to its original location within the same diff block.
   * Such pairs (`- some line` immediately followed by `+ some line`) are unnecessary and can be
   * safely removed to create a cleaner, more minimal diff.
   * @param diff The list of diff lines to process.
   */
  private fun annihilateNoopLinePairs(diff: MutableList<LineRecord>) {
    log.debug("Starting annihilation of no-op line pairs")
    val toRemove = mutableListOf<Pair<Int, Int>>()
    var i = 0
    while (i < diff.size - 1) {
      if (diff[i].type == DELETE) {
        var j = i + 1
        while (j < diff.size && diff[j].type != CONTEXT) {
          if (diff[j].type == ADD && diff[i].index != -1 && diff[j].index != -1 && normalizeLine(diff[i].line ?: "") == normalizeLine(diff[j].line ?: "")) {
            toRemove.add(Pair(i, j))
            break
          }
          j++
        }
      }
      i++
    }

    toRemove.flatMap { listOf(it.first, it.second) }.distinct().sortedDescending().forEach { diff.removeAt(it) }
    log.debug("Removed ${toRemove.size} no-op line pairs")
  }

  /**
   * Identifies lines that have been moved from one location to another.
   * The method works by examining the sequence of matched lines. It iterates through the source lines that have a match
   * in the new code, sorted by their original position. If it finds an "order inversion" — where a line `A` that
   * appeared before line `B` in the source now appears *after* line `B` in the new code — it concludes that
   * line `A` has been moved. It then marks the original source line for deletion and the new line for addition,
   * effectively representing the move as a `DELETE` and `ADD` operation.
   * @param newLines The list of `LineRecord`s for the new code, with links to the source code.
   */
  private fun markMovedLines(newLines: List<LineRecord>) {
    log.debug("Starting to mark moved lines")
    if (newLines.isEmpty()) return

    val matchedSourceLines = newLines.mapNotNull { it.matchingLine }.distinct().sortedBy { it.index }
    if (matchedSourceLines.isEmpty()) return

    for (i in matchedSourceLines.indices) {
      val current = matchedSourceLines[i]
      for (j in i + 1 until matchedSourceLines.size) {
        val later = matchedSourceLines[j]
        if (later.matchingLine!!.index < current.matchingLine!!.index) {
          current.type = DELETE
          current.matchingLine!!.type = ADD
          log.debug("Marked moved line: Source[${current.index}] as DELETE, Patch[${current.matchingLine!!.index}] as ADD")
          break
        }
      }
    }
    log.debug("Finished marking moved lines")
  }

  /**
   * Converts the linked list of new lines into a preliminary diff format.
   * This function iterates through the `newLines` list, which has already been linked to the `sourceLines`.
   * For each line in `newLines`:
   * - If it has no matching source line, it's a new addition and is marked as `ADD`.
   * - If it *does* have a matching source line, it's treated as `CONTEXT`. Crucially, before adding this context line,
   *   the function looks at the corresponding source line's predecessors. Any preceding source lines that were *not*
   *   matched in the new code are considered deletions and are added to the diff as `DELETE`.
   * @param newLines The list of `LineRecord`s for the new code.
   * @return A mutable list of `LineRecord`s representing the generated diff.
   */
  private fun newToPatch(
    newLines: List<LineRecord>
  ): MutableList<LineRecord> {
    val diff = mutableListOf<LineRecord>()
    log.debug("Starting diff generation")

    var newLine = newLines.firstOrNull()
    while (newLine != null) {
      val sourceLine = newLine.matchingLine
      when {
        sourceLine == null || newLine.type == ADD -> {
          diff.add(LineRecord(newLine.index, newLine.line, type = ADD))
          log.debug("Added ADD line: ${newLine.line}")
        }

        else -> {
          var priorSourceLine = sourceLine.previousLine
          val lineBuffer = mutableListOf<LineRecord>()
          while (priorSourceLine != null && (priorSourceLine.matchingLine == null || priorSourceLine.type == DELETE)) {

            lineBuffer.add(LineRecord(-1, priorSourceLine.line, type = DELETE))
            priorSourceLine = priorSourceLine.previousLine
          }
          diff.addAll(lineBuffer.reversed())
          diff.add(LineRecord(newLine.index, newLine.line, type = CONTEXT))
          log.debug("Added CONTEXT line: ${sourceLine.line}")
        }
      }
      newLine = newLine.nextLine
    }
    log.debug("Generated diff with ${diff.size} lines")
    return diff
  }

  /**
   * Reduces the number of context lines in the diff to improve readability.
   * This function processes a full diff and ensures that no more than `contextSize` context lines are
   * shown before or after a block of changes (`ADD`/`DELETE` lines). If a large block of consecutive
   * context lines is found, it is truncated, keeping only the specified number of lines at the
   * beginning and end of the block.
   * @param diff The full diff list.
   * @return A new list of `LineRecord`s with truncated context.
   */
  private fun truncateContext(diff: MutableList<LineRecord>): MutableList<LineRecord> {
    log.debug("Truncating context with size $contextSize")
    if (diff.isEmpty()) return mutableListOf()

    val truncatedDiff = mutableListOf<LineRecord>()
    val contextBuffer = mutableListOf<LineRecord>()
    for (i in diff.indices) {
      val line = diff[i]
      when {
        line.type != CONTEXT -> {
          if (contextSize * 2 < contextBuffer.size) {
            if (truncatedDiff.isNotEmpty()) {
              truncatedDiff.addAll(contextBuffer.take(contextSize))
              truncatedDiff.add(LineRecord(-1, "...", type = CONTEXT))
            }
            truncatedDiff.addAll(contextBuffer.takeLast(contextSize))
          } else {
            truncatedDiff.addAll(contextBuffer)
          }
          contextBuffer.clear()
          truncatedDiff.add(line)
        }

        else -> {
          contextBuffer.add(line)
        }
      }
    }
    if (truncatedDiff.isEmpty()) {
      return truncatedDiff
    }
    if (contextSize < contextBuffer.size) {
      truncatedDiff.addAll(contextBuffer.take(contextSize))
    } else {
      truncatedDiff.addAll(contextBuffer)
    }

    log.debug("Truncated diff size: ${truncatedDiff.size}")
    return truncatedDiff
  }

  /**
   * Normalizes a line of text for comparison purposes.
   * By default, this trims trailing whitespace. This ensures that comparisons between lines
   * are not affected by minor, often invisible, formatting differences. This method is `open`
   * so that subclasses can implement more aggressive normalization if needed (e.g., trimming all whitespace).
   * @param line The string to normalize.
   * @return The normalized string.
   */
  open fun normalizeLine(line: String): String {
    return line.trimEnd().replace("\\s{2,}".toRegex(), " ")
  }

  /**
   * Recursively finds and links common subsequences within unmatched blocks of lines.
   * After initial matching (unique and adjacent lines), there may be large blocks of unmatched lines in both
   * the source and the patch. This method isolates these unmatched segments and attempts to find further
   * matches within them. It re-runs the unique and adjacent linking logic on these smaller segments.
   * If any new matches are found, it calls itself recursively on the now even smaller remaining segments.
   * This helps to identify "islands" of similarity within large sections of changed code.
   * @param sourceLines The list of source lines to search within.
   * @param patchLines The list of patch lines to search within.
   * @param depth The current recursion depth, to prevent infinite loops.
   * @param levenshteinDistance An optional Levenshtein distance calculator for fuzzy matching.
   */
  private fun subsequenceLinking(
    sourceLines: List<LineRecord>, patchLines: List<LineRecord>, depth: Int = 0, levenshteinDistance: LevenshteinDistance?
  ) {
    log.debug("Subsequence linking at depth $depth")
    if (depth > maxRecursionDepth || sourceLines.isEmpty() || patchLines.isEmpty()) {
      if (depth > maxRecursionDepth) {
        log.warn("Maximum recursion depth reached in subsequence linking")
      }
      return

    }

    val sourceSegment = sourceLines.filter { it.matchingLine == null }
    val patchSegment = patchLines.filter { it.matchingLine == null }
    if (sourceSegment.isNotEmpty() && patchSegment.isNotEmpty()) {
      var matchedLines = linkUniqueMatchingLines(sourceSegment, patchSegment)
      matchedLines += linkAdjacentMatchingLines(sourceSegment, levenshteinDistance)
      if (matchedLines > 0) {
        subsequenceLinking(sourceSegment, patchSegment, depth + 1, levenshteinDistance)
      }
      log.debug("Matched $matchedLines lines in subsequence linking at depth $depth")
    }
  }

  /**
   * Constructs the final patched text by iterating through the source lines and applying changes
   * based on the previously established links to the patch lines.
   *
   * The logic iterates through each source line. If a source line is matched to a `DELETE` patch line, it's skipped.
   * If it's matched to a `CONTEXT` line, it's added to the output (potentially using the patch version if they differ slightly).
   * Unmatched source lines are kept as-is. Crucially, before and after processing a matched line, it checks for any
   * adjacent `ADD` lines in the patch that need to be inserted at that location.
   * @param sourceLines The original source lines, with links to patch lines.
   * @param patchLines The parsed patch lines.
   * @return A list of strings representing the new, patched file content.
   */
  private fun generatePatchedText(
    sourceLines: List<LineRecord>,
    patchLines: List<LineRecord>,
  ): List<String> {
    log.debug("Starting to generate patched text")
    val patchedText: MutableList<String> = mutableListOf()
    val usedPatchLines = mutableSetOf<LineRecord>()
    var sourceIndex = -1
    var lastMatchedPatchIndex = -1

    while (sourceIndex < sourceLines.size - 1) {
      val codeLine = sourceLines[++sourceIndex]
      when {
        codeLine.matchingLine?.type == DELETE -> {
          val patchLine = codeLine.matchingLine!!
          log.debug("Deleting line: {}", codeLine)

          usedPatchLines.add(patchLine)

          var nextPatchLine = patchLine.nextLine
          while (nextPatchLine != null && nextPatchLine.type == ADD && !usedPatchLines.contains(
              nextPatchLine
            )
          ) {
            log.debug("Inserting added line after delete: {}", nextPatchLine)
            patchedText.add(nextPatchLine.line ?: "")
            usedPatchLines.add(nextPatchLine)
            nextPatchLine = nextPatchLine.nextLine
          }
          checkAfterForInserts(patchLine, usedPatchLines, patchedText)
          lastMatchedPatchIndex = patchLine.index
        }

        codeLine.matchingLine != null -> {
          val patchLine: LineRecord = codeLine.matchingLine!!
          log.debug("Patching line: {} <-> {}", codeLine, patchLine)
          checkBeforeForInserts(patchLine, usedPatchLines, patchedText)
          usedPatchLines.add(patchLine)

          if (normalizeLine(codeLine.line ?: "") == normalizeLine(patchLine.line ?: "")) {
            patchedText.add(codeLine.line ?: "")
          } else {
            patchedText.add(patchLine.line ?: "")
          }
          checkAfterForInserts(patchLine, usedPatchLines, patchedText)
          lastMatchedPatchIndex = patchLine.index
        }

        else -> {
          log.debug("Added unmatched source line: {}", codeLine)
          patchedText.add(codeLine.line ?: "")
        }

      }
    }
    // Only add unmatched ADD lines if we had at least some context match
    // Otherwise, the patch likely doesn't apply to this file
    if (lastMatchedPatchIndex >= 0) {
      patchLines.filter { it.type == ADD && !usedPatchLines.contains(it) }.forEach { line ->
        log.debug("Added patch line: {}", line)
        patchedText.add(line.line ?: "")
      }
    } else {
      log.warn("No context lines matched - patch may not apply to this source")
    }
    log.debug("Generated patched text with ${patchedText.size} lines")
    return patchedText
  }

  /**
   * Scans backward from a given patch line to find and insert any preceding ADD lines
   * that haven't been processed yet. This is a helper for `generatePatchedText`.
   *
   * When a context line is matched, this function is called to ensure that any `ADD` lines that should
   * appear immediately *before* this context line are inserted into the output text.
   * @param patchLine The anchor patch line.
   * @param usedPatchLines A set of patch lines that have already been applied.
   * @param patchedText The list of strings being built for the final output.
   * @return The last line processed before hitting a non-ADD or used line.
   */
  private fun checkBeforeForInserts(
    patchLine: LineRecord, usedPatchLines: MutableSet<LineRecord>, patchedText: MutableList<String>
  ): LineRecord? {
    val buffer = mutableListOf<String>()
    var prevPatchLine = patchLine.previousLine
    while (null != prevPatchLine) {
      if (prevPatchLine.type != ADD || usedPatchLines.contains(prevPatchLine)) {
        break
      }

      log.debug("Added unmatched patch line: {}", prevPatchLine)
      buffer.add(prevPatchLine.line ?: "")
      usedPatchLines.add(prevPatchLine)
      prevPatchLine = prevPatchLine.previousLine
    }
    patchedText.addAll(buffer.reversed())
    return prevPatchLine
  }

  /**
   * Scans forward from a given patch line to find and insert any subsequent ADD lines
   * that haven't been processed yet. This is a helper for `generatePatchedText`.
   *
   * When a context or delete line is processed, this function is called to ensure that any `ADD` lines that should
   * appear immediately *after* it are inserted into the output text.
   * @param patchLine The anchor patch line.
   * @param usedPatchLines A set of patch lines that have already been applied.
   * @param patchedText The list of strings being built for the final output.
   * @return The last line processed.
   */
  private fun checkAfterForInserts(
    patchLine: LineRecord, usedPatchLines: MutableSet<LineRecord>, patchedText: MutableList<String>
  ): LineRecord {
    var nextPatchLine = patchLine.nextLine
    var iterationCount = 0
    val maxIterations = patchedText.size * MAX_ITERATION_MULTIPLIER

    while (null != nextPatchLine) {
      if (++iterationCount > maxIterations) {
        log.error("Maximum iteration count exceeded in checkAfterForInserts")
        break
      }
      var innerIterationCount = 0

      while (nextPatchLine != null && (normalizeLine(
          nextPatchLine.line ?: ""
        ).isEmpty() || (nextPatchLine.matchingLine == null && nextPatchLine.type == CONTEXT))
      ) {
        if (++innerIterationCount > maxIterations) {
          log.error("Maximum iteration count exceeded in inner loop")
          break
        }
        nextPatchLine = nextPatchLine.nextLine
      }
      if (nextPatchLine == null) break
      if (nextPatchLine.type != ADD) break
      if (usedPatchLines.contains(nextPatchLine)) break
      log.debug("Added unmatched patch line: {}", nextPatchLine)
      patchedText.add(nextPatchLine.line ?: "")
      usedPatchLines.add(nextPatchLine)
      nextPatchLine = nextPatchLine.nextLine
    }
    return nextPatchLine ?: patchLine
  }

  /**
   * Links lines between the source and the patch that are unique and match exactly (after normalization).
   * This is the crucial first pass in the matching algorithm. It identifies lines that are perfect, unambiguous
   * matches between the source and the patch.
   *
   * A line is considered "unique" if its normalized text content appears the exact same number of times in the
   * (currently unmatched) source lines as it does in the (currently unmatched) patch lines. This avoids ambiguity.
   * For example, if a blank line appears 10 times in the source and 5 times in the patch, it won't be matched here.
   * @param sourceLines The source lines.
   * @param patchLines The patch lines.
   * @return The number of lines that were matched.
   */
  private fun linkUniqueMatchingLines(sourceLines: List<LineRecord>, patchLines: List<LineRecord>): Int {
    log.debug("Starting to link unique matching lines. Source lines: ${sourceLines.size}, Patch lines: ${patchLines.size}")
    if (sourceLines.isEmpty() || patchLines.isEmpty()) {
      return 0
    }

    val sourceLineMap = sourceLines.filter { it.line != null && it.matchingLine == null }.groupBy { normalizeLine(it.line!!) }

    val patchLineMap = patchLines.filter {
      it.line != null && it.matchingLine == null && when (it.type) {
        ADD -> false
        else -> true
      }
    }.groupBy { normalizeLine(it.line!!) }
    log.debug("Created source and patch line maps")

    val matched = sourceLineMap.keys.intersect(patchLineMap.keys).filter {
      sourceLineMap[it]?.size == patchLineMap[it]?.size
    }
    matched.forEach { key ->
      val sourceGroup = sourceLineMap[key]!!
      val patchGroup = patchLineMap[key]!!
      for (i in sourceGroup.indices) {
        sourceGroup[i].matchingLine = patchGroup[i]
        patchGroup[i].matchingLine = sourceGroup[i]
        log.debug("Linked unique matching lines: Source[${sourceGroup[i].index}]: ${sourceGroup[i].line} <-> Patch[${patchGroup[i].index}]: ${patchGroup[i].line}")
      }
    }
    val matchedCount = matched.sumOf { sourceLineMap[it]!!.size }
    log.debug("Finished linking unique matching lines. Matched $matchedCount lines")
    return matched.sumOf { sourceLineMap[it]!!.size }
  }

  /**
   * Expands matches from existing anchor points. It iterates through already linked lines and
   * attempts to match their immediate neighbors (the lines directly before and after). This process is repeated
   * until no more adjacent matches can be found.
   *
   * This method effectively "grows" the regions of matched code outward from the high-confidence anchors
   * established by `linkUniqueMatchingLines`. It can use fuzzy matching (`isMatch`) if enabled.
   * @param sourceLines The source lines with some established links.
   * @param levenshtein An optional Levenshtein distance calculator for fuzzy matching.
   * @return The number of new matches found.
   */
  private fun linkAdjacentMatchingLines(sourceLines: List<LineRecord>, levenshtein: LevenshteinDistance?): Int {
    log.debug("Starting to link adjacent matching lines. Source lines: ${sourceLines.size}")
    var foundMatch = true
    var matchedLines = 0
    var iterationCount = 0
    val maxIterations = sourceLines.size * 10 // More reasonable limit
    val processedPairs = mutableSetOf<Pair<Int, Int>>()

    while (foundMatch) {
      if (++iterationCount > maxIterations) {
        log.warn("Maximum iterations reached in linkAdjacentMatchingLines")
        break
      }
      foundMatch = false
      for (sourceLine in sourceLines) {
        val patchLine = sourceLine.matchingLine ?: continue
        // Skip if we've already processed this line
        if (patchLine.type == DELETE || patchLine.type == ADD) continue
        val pairKey = Pair(sourceLine.index, patchLine.index)
        if (!processedPairs.add(pairKey)) continue

        val patchPrev = findPreviousValidLine(patchLine.previousLine, skipAdd = true, skipEmpty = true)
        val sourcePrev = findPreviousValidLine(sourceLine.previousLine, skipEmpty = true)

        if (sourcePrev != null && sourcePrev.matchingLine == null && patchPrev != null && patchPrev.matchingLine == null) {

          if (isMatch(sourcePrev, patchPrev, levenshtein)) {

            sourcePrev.matchingLine = patchPrev
            patchPrev.matchingLine = sourcePrev
            foundMatch = true
            matchedLines++
            log.debug("Linked adjacent previous lines: Source[${sourcePrev.index}]: ${sourcePrev.line} <-> Patch[${patchPrev.index}]: ${patchPrev.line}")
          }
        }

        val patchNext = findNextValidLine(patchLine.nextLine, skipAdd = true, skipEmpty = true)
        val sourceNext = findNextValidLine(sourceLine.nextLine, skipEmpty = true)

        if (sourceNext != null && sourceNext.matchingLine == null && patchNext != null && patchNext.matchingLine == null) {
          if (isMatch(sourceNext, patchNext, levenshtein)) {
            sourceNext.matchingLine = patchNext
            patchNext.matchingLine = sourceNext
            foundMatch = true
            matchedLines++
            log.debug("Linked adjacent next lines: Source[${sourceNext.index}]: ${sourceNext.line} <-> Patch[${patchNext.index}]: ${patchNext.line}")
          }
        }
      }
    }
    log.debug("Finished linking adjacent matching lines. Matched $matchedLines lines")
    return matchedLines
  }

  /**
   * Determines if two lines match, either exactly or fuzzily.
   * The matching logic is as follows:
   * 1. Normalizes both lines.
   * 2. Returns `true` if the normalized lines are identical.
   * 3. If fuzzy matching is enabled, it calculates the Levenshtein distance between the lines.
   * 4. The match is considered valid if the distance is below a threshold, which is calculated as `line_length / levenshteinThresholdDivisor`.
   * 5. Includes heuristics to prevent false positives, especially in structured text like Markdown. For example, it avoids matching a list item with a non-list item, or a header with a regular line, even if their text is similar.
   * @param sourcePrev The source line record.
   * @param patchPrev The patch line record.
   * @param levenshteinDistance An optional Levenshtein distance calculator.
   * @return `true` if the lines are considered a match, `false` otherwise.
   */
  private fun isMatch(
    sourcePrev: LineRecord, patchPrev: LineRecord, levenshteinDistance: LevenshteinDistance?
  ): Boolean {
    if (!enableFuzzyMatching) {
      return normalizeLine(sourcePrev.line ?: "") == normalizeLine(patchPrev.line ?: "")
    }

    val normalizedSource = normalizeLine(sourcePrev.line ?: "")
    val normalizedPatch = normalizeLine(patchPrev.line ?: "")

    if (normalizedSource == normalizedPatch) return true
    // Don't skip empty lines - they can be important context
    if (normalizedSource.isEmpty() && normalizedPatch.isEmpty()) return true
    if (normalizedSource.isEmpty() || normalizedPatch.isEmpty()) return false

    // For markdown, be more strict about matching to avoid false positives
    // Check if lines have similar structure (e.g., both are list items, headers, etc.)
    val sourceIsListItem = sourcePrev.line?.trimStart()?.matches(Regex("^[-*+\\d]+\\.?\\s+.*")) ?: false
    val patchIsListItem = patchPrev.line?.trimStart()?.matches(Regex("^[-*+\\d]+\\.?\\s+.*")) ?: false
    if (sourceIsListItem != patchIsListItem) return false

    val sourceIsHeader = sourcePrev.line?.trimStart()?.startsWith("#") ?: false
    val patchIsHeader = patchPrev.line?.trimStart()?.startsWith("#") ?: false
    if (sourceIsHeader != patchIsHeader) return false
    // Check for code block markers
    val sourceIsBlockQuote = sourcePrev.line?.trimStart()?.startsWith(">") ?: false
    val patchIsBlockQuote = patchPrev.line?.trimStart()?.startsWith(">") ?: false
    if (sourceIsBlockQuote != patchIsBlockQuote) return false
    // Check for code block markers
    val sourceIsCodeBlock = sourcePrev.line?.trimStart()?.startsWith("```") ?: false
    val patchIsCodeBlock = patchPrev.line?.trimStart()?.startsWith("```") ?: false
    if (sourceIsCodeBlock != patchIsCodeBlock) return false

    val maxLength = max(normalizedSource.length, normalizedPatch.length)

    if (maxLength > minLineLengthForFuzzyMatch && levenshteinDistance != null) {
      val distance = levenshteinDistance.apply(normalizedSource, normalizedPatch)
      log.debug("Levenshtein distance: $distance")
      return distance <= floor(maxLength / levenshteinThresholdDivisor.toDouble()).toInt()
    }
    return false
  }

  /**
   * Sets the `previousLine` and `nextLine` properties for each `LineRecord` in a list,
   * effectively converting a standard `List` into a doubly-linked list. This is essential for
   * the algorithm, as it frequently needs to navigate forwards and backwards from a given line
   * to find adjacent matches or context.
   * @param list The list of `LineRecord`s to link.
   * @return The list with links set.
   */
  private fun setLinks(list: List<LineRecord>): List<LineRecord> {
    log.debug("Starting to set links for ${list.size} lines")
    for (i in list.indices) {
      list[i].previousLine = if (i <= 0) null else {
        require(list[i - 1] !== list[i])
        list[i - 1]
      }
      list[i].nextLine = if (i >= list.size - 1) null else {
        require(list[i + 1] !== list[i])
        list[i + 1]
      }
    }
    log.debug("Finished setting links for ${list.size} lines")
    return list
  }

  /**
   * Parses the raw patch text into a list of `LineRecord` objects.
   * It iterates through each line of the input `text` and determines its type (`ADD`, `DELETE`, `CONTEXT`)
   * based on its prefix (`+`, `-`, or `  ` respectively). It strips the prefix to get the line's content.
   * It also specifically filters out diff header lines (like `--- a/file`, `+++ b/file`, `@@ -1,5 +1,5 @@`)
   * by setting their content to `null`, which causes them to be removed in a subsequent filter step.
   * @param text The patch text to parse.
   * @param sourceLines The source lines (used for context, though not directly in this function).
   * @return The list of parsed `LineRecord`s.
   */
  private fun parsePatchLines(text: String, sourceLines: List<LineRecord>): List<LineRecord> {
    log.debug("Starting to parse patch lines")
    val patchLines = setLinks(text.lines().mapIndexed { index, line ->


      // More robust detection of line types
      val trimmedLine = line.trimStart()
      val content = when {
        line.startsWith("  ") -> line.substring(2)
        trimmedLine.startsWith("+") || trimmedLine.startsWith("-") -> trimmedLine
        else -> line
      }

      LineRecord(
        index = index, line = run {
          when {
            content.startsWith("+++") || content.startsWith("---") || content.startsWith("@@") -> null
            content.startsWith("+") -> content.substring(1)
            content.startsWith("-") -> content.substring(1)
            else -> content
          }
        }, type = when {
          content.startsWith("+") && !content.startsWith("+++") -> ADD
          content.startsWith("-") && !content.startsWith("---") -> DELETE
          else -> CONTEXT
        }
      )
    }.filter { it.line != null }).toMutableList()

    fixPatchLineOrder(patchLines)

    log.debug("Finished parsing ${patchLines.size} patch lines")
    return patchLines
  }

  /**
   * Reorders adjacent ADD and DELETE lines in the patch.
   * In a standard diff format, a line modification is typically represented by a `DELETE` line followed
   * immediately by an `ADD` line. The parsing process might occasionally result in an `ADD` appearing
   * before its corresponding `DELETE`. This function iterates through the patch and repeatedly swaps
   * any adjacent `ADD, DELETE` pairs until the entire list is in the correct `DELETE, ADD` order.
   * @param patchLines The list of patch lines to reorder.
   */
  private fun fixPatchLineOrder(patchLines: MutableList<LineRecord>) {
    log.debug("Starting to fix patch line order")
    if (patchLines.size < 2) return
    var swapped: Boolean
    var iterationCount = 0
    val maxIterations = patchLines.size * patchLines.size / 2 // O(n²/2) average case

    do {
      if (++iterationCount > maxIterations) {
        log.error("Maximum iterations exceeded in fixPatchLineOrder - possible circular reference")
        break
      }
      swapped = false
      for (i in 0 until patchLines.size - 1) {
        if (patchLines[i].type == ADD && patchLines[i + 1].type == DELETE) {
          swapped = true
          val addLine: LineRecord = patchLines[i]
          val deleteLine: LineRecord = patchLines[i + 1]

          val nextLine = deleteLine.nextLine
          val previousLine = addLine.previousLine

          if (addLine === deleteLine || previousLine === deleteLine || nextLine === addLine || nextLine === deleteLine) {
            log.error("Invalid line references detected, skipping swap")
            continue
          }

          deleteLine.nextLine = addLine
          addLine.previousLine = deleteLine
          deleteLine.previousLine = previousLine
          addLine.nextLine = nextLine
          patchLines[i] = deleteLine
          patchLines[i + 1] = addLine
        }
      }
    } while (swapped)
    // Re-establish the links after reordering
    setLinks(patchLines)
    log.debug("Finished fixing patch line order")
  }

  /**
   * Traverses backwards from a starting `LineRecord` to find the next "valid" line.
   * This is a utility function for navigating the linked list of lines. A line's "validity" is
   * determined by the `skip...` parameters. For example, it can be used to find the previous
   * non-empty, non-ADD line, which is useful when trying to find the true preceding context for a match.
   * It includes protection against circular references.
   * @param start The `LineRecord` to start searching from.
   * @param skipAdd If true, skips lines of type ADD.
   * @param skipEmpty If true, skips lines that are empty after normalization.
   * @return The first valid `LineRecord` found, or `null` if the beginning of the list is reached.
   */
  private fun findPreviousValidLine(
    start: LineRecord?, skipAdd: Boolean = false, skipEmpty: Boolean = false
  ): LineRecord? {
    var current = start
    val visited = mutableSetOf<LineRecord>()
    while (current != null) {
      if (!visited.add(current)) {
        log.error("Circular reference detected in findPreviousValidLine")
        return null
      }
      if ((skipAdd && current.type == ADD) || (skipEmpty && normalizeLine(current.line ?: "").isEmpty())) {
        current = current.previousLine
      } else {
        return current
      }
    }
    return null
  }

  /**
   * Traverses forwards from a starting `LineRecord` to find the next "valid" line.
   * This is a utility function for navigating the linked list of lines. A line's "validity" is
   * determined by the `skip...` parameters. It includes protection against circular references.
   * @param start The `LineRecord` to start searching from.
   * @param skipAdd If true, skips lines of type ADD.
   * @param skipEmpty If true, skips lines that are empty after normalization.
   * @return The first valid `LineRecord` found, or `null` if the end of the list is reached.
   */
  private fun findNextValidLine(
    start: LineRecord?, skipAdd: Boolean = false, skipEmpty: Boolean = false
  ): LineRecord? {
    var current = start
    val visited = mutableSetOf<LineRecord>()
    while (current != null) {
      if (!visited.add(current)) {
        log.error("Circular reference detected in findNextValidLine")
        return null
      }
      if ((skipAdd && current.type == ADD) || (skipEmpty && normalizeLine(current.line ?: "").isEmpty())) {
        current = current.nextLine
      } else {
        return current
      }
    }
    return null
  }


  /**
   * Applies a "snippet" patch, which is a block of code without `+` or `-` markers.
   * This is useful for applying small, self-contained changes provided by a language model.
   * The method uses a multi-stage fallback strategy to find the correct location to apply the patch:
   *
   * 1.  **Exact Block Match:** First, it tries to find the entire snippet as a contiguous, identical block in the source.
   *     If found, it replaces that block with the patch.
   * 2.  **Anchor Match:** If an exact block match fails, it uses the first and last lines of the snippet as "anchors".
   *     It looks for a block in the source that starts with the same first line and ends with the same last line.
   * 3.  **Fuzzy Block Match:** As a last resort, it slides the snippet window across the source code, calculating a match score
   *     for each position based on the number of identical lines. If a position is found with a score exceeding `snippetMatchThreshold`
   *     (and optionally satisfying `requireAnchorMatch`), it replaces that block in the source.
   *
   * @param source The original source code.
   * @param patch The snippet of code to apply.
   * @return The patched source code, or the original source if no suitable match is found.
   */
  private fun applySnippetPatch(source: String, patch: String): String {
    if (!enableSnippetPatching) {
      log.debug("Snippet patching disabled, returning original source")
      return source
    }

    val patchLines = patch.lines().filter { it.isNotBlank() }
    if (patchLines.isEmpty()) {
      log.debug("Empty patch lines, returning original source")
      return source
    }

    val sourceLines = source.lines().toMutableList()
    if (sourceLines.isEmpty()) {
      log.debug("Empty source, returning patch as new content")
      return patch
    }


    // Use normalized lines for matching to handle whitespace consistently
    val normalizedSource = sourceLines.map { normalizeLine(it) }
    val normalizedPatch = patchLines.map { normalizeLine(it) }

    // Handle single-line patches
    if (normalizedPatch.size == 1) {
      val lineIndex = normalizedSource.indexOf(normalizedPatch[0])
      if (lineIndex != -1) {
        sourceLines[lineIndex] = patchLines[0]
        return sourceLines.joinToString("\n")
      }
      // If not found, don't apply
      log.warn("Single line patch not found in source, patch not applied")
      return source
    }


    // First try exact block match
    for (i in 0..normalizedSource.size - normalizedPatch.size) {
      var exactMatch = true
      for (j in normalizedPatch.indices) {
        if (normalizedSource[i + j] != normalizedPatch[j]) {
          exactMatch = false
          break
        }
      }
      if (exactMatch) {
        log.info("Found exact match for snippet patch at line $i")
        val newSource = mutableListOf<String>()
        newSource.addAll(sourceLines.subList(0, i))
        newSource.addAll(patchLines)
        newSource.addAll(sourceLines.subList(i + normalizedPatch.size, sourceLines.size))
        return newSource.joinToString("\n")
      }
    }

    // Try to find match using first and last lines as anchors
    val firstPatchLine = normalizedPatch.first()
    val lastPatchLine = normalizedPatch.last()

    for (i in 0..normalizedSource.size - normalizedPatch.size) {
      if (normalizedSource[i] == firstPatchLine) {
        // Check if we can find the last line at the expected position
        val expectedLastIndex = i + normalizedPatch.size - 1
        if (expectedLastIndex < normalizedSource.size && normalizedSource[expectedLastIndex] == lastPatchLine) {
          log.info("Found anchor match for snippet patch from line $i to $expectedLastIndex")
          val newSource = mutableListOf<String>()
          newSource.addAll(sourceLines.subList(0, i))
          newSource.addAll(patchLines)
          newSource.addAll(sourceLines.subList(expectedLastIndex + 1, sourceLines.size))
          return newSource.joinToString("\n")
        }
      }
    }

    // If still no match, try fuzzy matching as last resort
    val patchSize = normalizedPatch.size
    var bestMatch = -1
    var bestScore = 0

    // Slide through source looking for best match
    for (i in 0..normalizedSource.size - patchSize) {
      var matchScore = 0
      for (j in normalizedPatch.indices) {
        if (normalizedSource[i + j] == normalizedPatch[j]) {
          matchScore++
        }
      }
      // Require at least snippetMatchThreshold match to consider it valid
      // And at least 2 lines must match exactly (first and last ideally)
      val hasAnchorMatch = normalizedSource[i] == normalizedPatch[0] || normalizedSource[i + patchSize - 1] == normalizedPatch[patchSize - 1]
      if (matchScore > bestScore && matchScore >= (patchSize * snippetMatchThreshold).toInt() && (!requireAnchorMatch || hasAnchorMatch || matchScore >= patchSize - 1)) {
        bestScore = matchScore
        bestMatch = i
      }
    }

    if (bestMatch == -1) {
      log.warn("Could not find suitable match for snippet patch in source. Patch not applied.")
      return source
    }

    val startIndex = bestMatch
    val endIndex = bestMatch + patchSize - 1
    log.info("Applying snippet patch from source line $startIndex to $endIndex (match score: $bestScore/$patchSize)")

    val newSource = mutableListOf<String>()
    newSource.addAll(sourceLines.subList(0, startIndex))
    newSource.addAll(patchLines)
    newSource.addAll(sourceLines.subList(endIndex + 1, sourceLines.size))
    return newSource.joinToString("\n")
  }

  companion object {
    /** A default instance of the matcher with standard configuration. */
    val default = FuzzyPatchMatcher()

    /**
     * Defines the type of a line within a diff.
     */
    enum class LineType { CONTEXT, ADD, DELETE }

    /** The default number of context lines to include in a generated patch. */
    const val DEFAULT_CONTEXT_SIZE = 3

    /** The maximum recursion depth for subsequence linking to prevent stack overflows. */
    const val MAX_RECURSION_DEPTH = 100

    /** The divisor for calculating the Levenshtein distance threshold. A lower number is stricter. */
    const val LEVENSHTEIN_THRESHOLD_DIVISOR = 4

    /** The minimum line length required for fuzzy matching to be considered. */
    const val MIN_LINE_LENGTH_FOR_FUZZY_MATCH = 5

    /** A multiplier to set a safe upper bound on loop iterations to prevent infinite loops. */
    const val MAX_ITERATION_MULTIPLIER = 10

    /**
     * A data class representing a single line of code within either the source text or the patch.
     * It acts as a node in a doubly-linked list and stores metadata about the line's state and its
     * relationship to a corresponding line in the other text.
     *
     * @param index The original 0-based index of the line in its file.
     * @param line The actual text content of the line.
     * @param previousLine A link to the previous `LineRecord` in the list.
     * @param nextLine A link to the next `LineRecord` in the list.
     * @param matchingLine A link to the corresponding `LineRecord` in the other file (source or patch).
     * @param type The type of the line (CONTEXT, ADD, or DELETE).
     */

    class LineRecord(
      val index: Int,
      val line: String?,
      var previousLine: LineRecord? = null,
      var nextLine: LineRecord? = null,
      var matchingLine: LineRecord? = null,
      var type: LineType = CONTEXT
    )

    /** Logger instance for the patch processor. */
    private val log = LoggerFactory.getLogger(FuzzyPatchMatcher::class.java)
  }
}