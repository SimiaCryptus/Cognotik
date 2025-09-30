package com.simiacryptus.cognotik.diff

 import com.simiacryptus.cognotik.diff.FuzzyPatchMatcher.Companion.LineType.ADD
 import com.simiacryptus.cognotik.diff.FuzzyPatchMatcher.Companion.LineType.CONTEXT
 import com.simiacryptus.cognotik.diff.FuzzyPatchMatcher.Companion.LineType.DELETE
 import com.simiacryptus.cognotik.util.LoggerFactory
 import org.apache.commons.text.similarity.LevenshteinDistance
 import kotlin.math.floor
 import kotlin.math.max

open class FuzzyPatchMatcher(
    private val contextSize: Int = DEFAULT_CONTEXT_SIZE,
    private val maxRecursionDepth: Int = MAX_RECURSION_DEPTH,
    private val levenshteinThresholdDivisor: Int = LEVENSHTEIN_THRESHOLD_DIVISOR,
    private val minLineLengthForFuzzyMatch: Int = MIN_LINE_LENGTH_FOR_FUZZY_MATCH,
    private val enableFuzzyMatching: Boolean = true,
    private val enableSnippetPatching: Boolean = true,
    private val snippetMatchThreshold: Double = 0.8,
    private val requireAnchorMatch: Boolean = true
) : PatchProcessor {

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

        val sourceLines = parseLines(oldCode)
        val newLines = parseLines(newCode)
        link(sourceLines, newLines, null)
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

        val result = strings(source, patch)
        return result.joinToString("\n").trim()
    }

    private fun strings(source: String, patch: String): List<String> {
        val sourceLines = parseLines(source)
        val patchLines = parsePatchLines(patch, sourceLines)
        log.debug("Parsed source lines: ${sourceLines.size}, initial patch lines: ${patchLines.size}")
        link(sourceLines, patchLines, LevenshteinDistance())

        val filteredPatchLines = patchLines.filter { it.line != null && normalizeLine(it.line!!).isNotEmpty() }
        log.debug("Filtered patch lines: ${patchLines.size}")
        val result = generatePatchedText(sourceLines, filteredPatchLines)
        return result
    }

    private fun annihilateNoopLinePairs(diff: MutableList<LineRecord>) {
        log.debug("Starting annihilation of no-op line pairs")
        val toRemove = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < diff.size - 1) {
            if (diff[i].type == DELETE) {
                var j = i + 1
                while (j < diff.size && diff[j].type != CONTEXT) {
                    if (diff[j].type == ADD &&
                        diff[i].index != -1 && diff[j].index != -1 &&
                        normalizeLine(diff[i].line ?: "") == normalizeLine(diff[j].line ?: "")
                    ) {
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

    private fun normalizeLine(line: String): String {
        // Preserve more structure - only trim ends and normalize consecutive spaces
        // but preserve single spaces and indentation patterns
        return line.trimEnd().replace("\\s{2,}".toRegex(), " ")
    }


    private fun link(
        sourceLines: List<LineRecord>,
        patchLines: List<LineRecord>,
        levenshteinDistance: LevenshteinDistance?
    ) {
        linkUniqueMatchingLines(sourceLines, patchLines)
        linkAdjacentMatchingLines(sourceLines, levenshteinDistance)
        subsequenceLinking(sourceLines, patchLines, levenshteinDistance = levenshteinDistance)
    }

    private fun subsequenceLinking(
        sourceLines: List<LineRecord>,
        patchLines: List<LineRecord>,
        depth: Int = 0,
        levenshteinDistance: LevenshteinDistance?
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
            patchLines.filter { it.type == ADD && !usedPatchLines.contains(it) }
                .forEach { line ->
                    log.debug("Added patch line: {}", line)
                    patchedText.add(line.line ?: "")
                }
        } else {
            log.warn("No context lines matched - patch may not apply to this source")
        }
        log.debug("Generated patched text with ${patchedText.size} lines")
        return patchedText
    }

    private fun checkBeforeForInserts(
        patchLine: LineRecord,
        usedPatchLines: MutableSet<LineRecord>,
        patchedText: MutableList<String>
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

    private fun checkAfterForInserts(
        patchLine: LineRecord,
        usedPatchLines: MutableSet<LineRecord>,
        patchedText: MutableList<String>
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

            while (nextPatchLine != null && (
                        normalizeLine(nextPatchLine.line ?: "").isEmpty() ||
                                (nextPatchLine.matchingLine == null && nextPatchLine.type == CONTEXT)
                        )
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
     * Links lines between the source and the patch that are unique and match exactly.
     * @param sourceLines The source lines.
     * @param patchLines The patch lines.
     */
    private fun linkUniqueMatchingLines(sourceLines: List<LineRecord>, patchLines: List<LineRecord>): Int {
        log.debug("Starting to link unique matching lines. Source lines: ${sourceLines.size}, Patch lines: ${patchLines.size}")
        if (sourceLines.isEmpty() || patchLines.isEmpty()) {
            return 0
        }

        val sourceLineMap = sourceLines
            .filter { it.line != null && it.matchingLine == null }
            .groupBy { normalizeLine(it.line!!) }

        val patchLineMap = patchLines.filter {
            it.line != null && it.matchingLine == null &&
                    when (it.type) {
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
     * Links lines that are adjacent to already linked lines and match exactly.
     * @param sourceLines The source lines with some established links.
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

                if (sourcePrev != null && sourcePrev.matchingLine == null &&
                    patchPrev != null && patchPrev.matchingLine == null
                ) {

                    if (isMatch(sourcePrev, patchPrev, levenshtein)) {

                        sourcePrev.matchingLine = patchPrev
                        patchPrev.matchingLine = sourcePrev
                        foundMatch = true
                        matchedLines++
                        log.debug("Linked adjacent previous lines: Source[${sourcePrev.index}]: ${sourcePrev.line} <-> Patch[${patchPrev.index}]: ${patchPrev.line}")
                    }
                }

                var patchNext = patchLine.nextLine
                var skipCount = 0
                val maxSkips = sourceLines.size // More reasonable limit
                while (patchNext?.nextLine != null &&
                    (patchNext.type == ADD || normalizeLine(patchNext.line ?: "").isEmpty())
                ) {
                    if (++skipCount > maxSkips) {
                        log.warn("Maximum skip count exceeded when finding next patch line")
                        break
                    }
                    if (patchNext === patchNext.nextLine) {
                        log.error("Circular reference detected in patch lines")
                        break
                    }
                    patchNext = patchNext.nextLine!!
                }

                var sourceNext = sourceLine.nextLine
                skipCount = 0
                while (sourceNext?.nextLine != null && (normalizeLine(sourceNext.line ?: "").isEmpty())) {
                    if (++skipCount > maxSkips) {
                        log.warn("Maximum skip count exceeded when finding next source line")
                        break
                    }
                    if (sourceNext === sourceNext.nextLine) {
                        log.error("Circular reference detected in source lines")
                        break
                    }
                    sourceNext = sourceNext.nextLine!!
                }

                if (sourceNext != null && sourceNext.matchingLine == null &&
                    patchNext != null && patchNext.matchingLine == null
                ) {
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

    private fun isMatch(
        sourcePrev: LineRecord,
        patchPrev: LineRecord,
        levenshteinDistance: LevenshteinDistance?
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

        // Check if bracket/paren depths match - important for code structure
        if (sourcePrev.metrics.parenthesesDepth != patchPrev.metrics.parenthesesDepth ||
            sourcePrev.metrics.squareBracketsDepth != patchPrev.metrics.squareBracketsDepth ||
            sourcePrev.metrics.curlyBracesDepth != patchPrev.metrics.curlyBracesDepth
        ) {
            return false
        }

        // For markdown, be more strict about matching to avoid false positives
        // Check if lines have similar structure (e.g., both are list items, headers, etc.)
        val sourceIsListItem = sourcePrev.line?.trimStart()?.matches(Regex("^[-*+\\d]+\\.?\\s+.*")) ?: false
        val patchIsListItem = patchPrev.line?.trimStart()?.matches(Regex("^[-*+\\d]+\\.?\\s+.*")) ?: false
        if (sourceIsListItem != patchIsListItem) return false

        val sourceIsHeader = sourcePrev.line?.trimStart()?.startsWith("#") ?: false
        val patchIsHeader = patchPrev.line?.trimStart()?.startsWith("#") ?: false
        if (sourceIsHeader != patchIsHeader) return false
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
     * @param text The text to parse.
     * @return The list of line records.
     */
    private fun parseLines(text: String): List<LineRecord> {
        log.debug("Starting to parse lines")

        val lines = setLinks(text.lines().mapIndexed { index, line -> LineRecord(index, line) })

        calculateLineMetrics(lines)
        log.debug("Finished parsing ${lines.size} lines")
        return lines
    }

    /**
     * Sets the previous and next line links for a list of line records.
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
     * Parses the patch text into a list of line records, identifying the type of each line (ADD, DELETE, CONTEXT).
     * @param text The patch text to parse.
     * @return The list of line records with types set.
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
                index = index,
                line = run {
                    when {
                        content.startsWith("+++") || content.startsWith("---") || content.startsWith("@@") -> null
                        content.startsWith("+") -> content.substring(1)
                        content.startsWith("-") -> content.substring(1)
                        else -> content
                    }
                },
                type = when {
                    content.startsWith("+") && !content.startsWith("+++") -> ADD
                    content.startsWith("-") && !content.startsWith("---") -> DELETE
                    else -> CONTEXT
                }
            )
        }.filter { it.line != null }).toMutableList()

        fixPatchLineOrder(patchLines)

        calculateLineMetrics(patchLines)
        log.debug("Finished parsing ${patchLines.size} patch lines")
        return patchLines
    }

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
                    val addLine = patchLines[i].copy()
                    val deleteLine = patchLines[i + 1].copy()

                    val nextLine = deleteLine.nextLine
                    val previousLine = addLine.previousLine

                    if (addLine === deleteLine || previousLine === deleteLine ||
                        nextLine === addLine || nextLine === deleteLine
                    ) {
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
     * Calculates the metrics for each line, including bracket nesting depth.
     * @param lines The list of line records to process.
     */
    private fun calculateLineMetrics(lines: List<LineRecord>) {
        log.debug("Starting to calculate line metrics for ${lines.size} lines")
        if (lines.isEmpty()) return

        var currentMetrics = LineMetrics(0, 0, 0)

        for (lineRecord in lines) {
            // Start from previous line's ending depth
            var parenDepth = currentMetrics.parenthesesDepth
            var squareDepth = currentMetrics.squareBracketsDepth
            var curlyDepth = currentMetrics.curlyBracesDepth

            (lineRecord.line ?: "").forEach { char ->
                when (char) {
                    '(' -> parenDepth++
                    ')' -> parenDepth = max(0, parenDepth - 1)
                    '[' -> squareDepth++
                    ']' -> squareDepth = max(0, squareDepth - 1)
                    '{' -> curlyDepth++
                    '}' -> curlyDepth = max(0, curlyDepth - 1)
                }
            }

            currentMetrics = LineMetrics(parenDepth, squareDepth, curlyDepth)
            lineRecord.metrics = currentMetrics
        }
        log.debug("Finished calculating line metrics")
    }

    private fun findPreviousValidLine(
        start: LineRecord?,
        skipAdd: Boolean = false,
        skipEmpty: Boolean = false
    ): LineRecord? {
        var current = start
        val visited = mutableSetOf<LineRecord>()
        while (current != null) {
            if (!visited.add(current)) {
                log.error("Circular reference detected in findPreviousValidLine")
                return null
            }
            if ((skipAdd && current.type == ADD) ||
                (skipEmpty && normalizeLine(current.line ?: "").isEmpty())
            ) {
                current = current.previousLine
            } else {
                return current
            }
        }
        return null
    }

    private val log = LoggerFactory.getLogger(PatchProcessor::class.java)

    /**
     * Applies a snippet patch that consists solely of context lines.
     * It searches for the first and last (normalized) lines of the patch in the source.
     * If found, the block within (and including) those anchors is replaced by the patch snippet.
     * If not found, the original source is returned unchanged.
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
                if (expectedLastIndex < normalizedSource.size &&
                    normalizedSource[expectedLastIndex] == lastPatchLine
                ) {
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
            val hasAnchorMatch = normalizedSource[i] == normalizedPatch[0] ||
                    normalizedSource[i + patchSize - 1] == normalizedPatch[patchSize - 1]
            if (matchScore > bestScore && matchScore >= (patchSize * snippetMatchThreshold).toInt() &&
                (!requireAnchorMatch || hasAnchorMatch || matchScore >= patchSize - 1)
            ) {
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
    companion object : FuzzyPatchMatcher() {
        enum class LineType { CONTEXT, ADD, DELETE }


        const val DEFAULT_CONTEXT_SIZE = 3
        const val MAX_RECURSION_DEPTH = 100
        const val LEVENSHTEIN_THRESHOLD_DIVISOR = 4
        const val MIN_LINE_LENGTH_FOR_FUZZY_MATCH = 5
        const val MAX_ITERATION_MULTIPLIER = 10

        data class LineMetrics(
            var parenthesesDepth: Int = 0,
            var squareBracketsDepth: Int = 0,
            var curlyBracesDepth: Int = 0
        )

        data class LineRecord(
            val index: Int,
            val line: String?,
            var previousLine: LineRecord? = null,
            var nextLine: LineRecord? = null,
            var matchingLine: LineRecord? = null,
            var type: LineType = CONTEXT,
            var metrics: LineMetrics = LineMetrics()
        ) {
            override fun toString(): String {
                val sb = StringBuilder()
                sb.append("${index.toString().padStart(5, ' ')}: ")
                when (type) {
                    CONTEXT -> sb.append(" ")
                    ADD -> sb.append("+")
                    DELETE -> sb.append("-")
                }
                sb.append(" ")
                sb.append(line)
                sb.append(" (${metrics.parenthesesDepth})[${metrics.squareBracketsDepth}]{${metrics.curlyBracesDepth}}")
                return sb.toString()
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as LineRecord
                if (index != other.index) return false
                if (line != other.line) return false
                return true
            }

            override fun hashCode(): Int {
                var result = index
                result = 31 * result + (line?.hashCode() ?: 0)
                return result
            }

        }

    }
}