@file:Suppress("LoggingSimilarMessage")

package com.simiacryptus.skyenet.core.util

import com.simiacryptus.skyenet.core.util.IterativePatchUtil.LineType.*
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.LoggerFactory
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object IterativePatchUtil {
    private enum class LineType { CONTEXT, ADD, DELETE }

    // Tracks the nesting depth of different bracket types
    private data class LineMetrics(
        var parenthesesDepth: Int = 0,
        var squareBracketsDepth: Int = 0,
        var curlyBracesDepth: Int = 0
    )

    // Represents a single line in the source or patch text
    private data class LineRecord(
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

    fun generatePatch(oldCode: String, newCode: String): String {
        log.info("Starting patch generation process")
        val sourceLines = parseLines(oldCode)
        val newLines = parseLines(newCode)
        link(sourceLines, newLines, null)
        log.debug("Parsed and linked source lines: ${sourceLines.size}, new lines: ${newLines.size}")
        markMovedLines(newLines)
        val longDiff = newToPatch(newLines)
        val shortDiff = truncateContext(longDiff).toMutableList()
        fixPatchLineOrder(shortDiff)
        annihilateNoopLinePairs(shortDiff)
        log.debug("Generated diff with ${shortDiff.size} lines after processing")
        val patch = StringBuilder()
        // Generate the patch text
        shortDiff.forEach { line ->
            when (line.type) {
                CONTEXT -> patch.append("  ${line.line}\n")
                ADD -> patch.append("+ ${line.line}\n")
                DELETE -> patch.append("- ${line.line}\n")
            }
        }
        log.info("Patch generation completed")
        return patch.toString().trimEnd()
    }

    /**
     * Applies a patch to the given source text.
     * @param source The original text.
     * @param patch The patch to apply.
     * @return The text after the patch has been applied.
     */
    fun applyPatch(source: String, patch: String): String {
        log.info("Starting patch application process")
        // Parse the source and patch texts into lists of line records
        val sourceLines = parseLines(source)
        var patchLines = parsePatchLines(patch, sourceLines)
        log.debug("Parsed source lines: ${sourceLines.size}, initial patch lines: ${patchLines.size}")
        link(sourceLines, patchLines, LevenshteinDistance())

        // Filter out patch lines that become empty after normalization
        patchLines = patchLines.filter { it.line != null && normalizeLine(it.line!!).isNotEmpty() }
        log.debug("Filtered patch lines: ${patchLines.size}")
        log.info("Generating patched text")

        val result = generatePatchedText(sourceLines, patchLines)
        val generatePatchedTextUsingLinks = result.joinToString("\n").trim()
        log.info("Patch application completed")

        return generatePatchedTextUsingLinks
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
        // Remove the pairs in reverse order to maintain correct indices
        toRemove.flatMap { listOf(it.first, it.second) }.distinct().sortedDescending().forEach { diff.removeAt(it) }
        log.debug("Removed ${toRemove.size} no-op line pairs")
    }

    private fun markMovedLines(newLines: List<LineRecord>) {
        log.debug("Starting to mark moved lines")
        // We start with the first line of the new (patched) code
        var newLine = newLines.firstOrNull()
        var iterationCount = 0
        val maxIterations = newLines.size * 2 // Arbitrary limit to prevent infinite loops
        // We'll iterate through all lines of the new code
        while (null != newLine) {
            try {
                // We only process lines that have a matching line in the source code
                if (newLine.matchingLine != null) {
                    // Get the next line in the new code
                    var nextNewLine = newLine.nextLine ?: break
                    try {
                        // Skip any lines that don't have a match or are additions
                        // This helps us find the next "anchor" point in the new code
                        while (nextNewLine.matchingLine == null || nextNewLine.type == ADD) {
                            nextNewLine = nextNewLine.nextLine ?: break
                        }
                        if (nextNewLine.matchingLine == null || nextNewLine.type == ADD) break
                        // Get the corresponding line in the source code
                        val sourceLine = newLine.matchingLine!!
                        log.debug("Processing patch line ${newLine.index} with matching source line ${sourceLine.index}")
                        // Find the next line in the source code
                        var nextSourceLine = sourceLine.nextLine ?: continue
                        // Skip any lines in the source that don't have a match or are deletions
                        // This helps us find the next "anchor" point in the source code
                        while (nextSourceLine.matchingLine == null || nextSourceLine.type == DELETE) {
                            // Safeguard to prevent infinite loop
                            if (++iterationCount > maxIterations) {
                                log.error("Exceeded maximum iterations in markMovedLines")
                                break
                            }
                            nextSourceLine = nextSourceLine.nextLine ?: break
                        }
                        if (nextSourceLine.matchingLine == null || nextSourceLine.type == DELETE) break
                        // If the next matching lines in source and new don't correspond,
                        // it means there's a moved block of code
                        while (nextNewLine.matchingLine != nextSourceLine) {
                            if (nextSourceLine.matchingLine != null) {
                                // Mark the line in the new code as an addition
                                nextSourceLine.type = DELETE
                                // Mark the corresponding line in the source code as a deletion
                                nextSourceLine.matchingLine!!.type = ADD
                                log.debug("Marked moved line: Patch[${nextSourceLine.index}] as ADD, Source[${nextSourceLine.matchingLine!!.index}] as DELETE")
                            }
                            // Move to the next line in the new code
                            nextSourceLine = nextSourceLine.nextLine ?: break
                            // Skip any lines that don't have a match or are additions
                            while (nextSourceLine.matchingLine == null || nextSourceLine.type == DELETE) {
                                nextSourceLine = nextSourceLine.nextLine ?: continue
                            }
                        }
                    } finally {
                        // Safeguard to prevent infinite loop
                        if (++iterationCount > maxIterations) {
                            log.error("Exceeded maximum iterations in markMovedLines")
                            newLine = nextNewLine
                        }
                    }
                } else {
                    // If the current line doesn't have a match, move to the next one
                    newLine = newLine.nextLine
                }
            } catch (e: Exception) {
                log.error("Error marking moved lines", e)
            }
        }
        // At this point, we've marked all moved lines in both the source and new code
        log.debug("Finished marking moved lines")
    }

    private fun newToPatch(
        newLines: List<LineRecord>
    ): MutableList<LineRecord> {
        val diff = mutableListOf<LineRecord>()
        log.debug("Starting diff generation")
        // Generate raw patch without limited context windows
        var newLine = newLines.firstOrNull()
        while (newLine != null) {
            val sourceLine = newLine.matchingLine
            when {
                sourceLine == null || newLine.type == ADD -> {
                    diff.add(LineRecord(newLine.index, newLine.line, type = ADD))
                    log.debug("Added ADD line: ${newLine.line}")
                }

                else -> {
                    // search for prior, unlinked source lines
                    var priorSourceLine = sourceLine.previousLine
                    val lineBuffer = mutableListOf<LineRecord>()
                    while (priorSourceLine != null && (priorSourceLine.matchingLine == null || priorSourceLine.type == DELETE)) {
                        // Note the deletion of the prior source line
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
        val contextSize = 3 // Number of context lines before and after changes
        log.debug("Truncating context with size $contextSize")
        val truncatedDiff = mutableListOf<LineRecord>()
        val contextBuffer = mutableListOf<LineRecord>()
        for (i in diff.indices) {
            val line = diff[i]
            when {
                line.type != CONTEXT -> {
                    // Start of a change, add buffered context
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
        // Add trailing context after the last change
        log.debug("Truncated diff size: ${truncatedDiff.size}")
        return truncatedDiff
    }

    /**
     * Normalizes a line by removing all whitespace.
     * @param line The line to normalize.
     * @return The normalized line.
     */
    private fun normalizeLine(line: String): String {
        return line.replace(whitespaceRegex, "")
    }

    private val whitespaceRegex = "\\s".toRegex()

    private fun link(
        sourceLines: List<LineRecord>,
        patchLines: List<LineRecord>,
        levenshteinDistance: LevenshteinDistance?
    ) {
        // Step 1: Link all unique lines in the source and patch that match exactly
        log.info("Step 1: Linking unique matching lines")
        linkUniqueMatchingLines(sourceLines, patchLines)

        // Step 2: Link all exact matches in the source and patch which are adjacent to established links
        log.info("Step 2: Linking adjacent matching lines")
        linkAdjacentMatchingLines(sourceLines, levenshteinDistance)
        log.info("Step 3: Performing subsequence linking")

        subsequenceLinking(sourceLines, patchLines, levenshteinDistance = levenshteinDistance)
    }

    private fun subsequenceLinking(
        sourceLines: List<LineRecord>,
        patchLines: List<LineRecord>,
        depth: Int = 0,
        levenshteinDistance: LevenshteinDistance?
    ) {
        log.debug("Subsequence linking at depth $depth")
        if (depth > 10 || sourceLines.isEmpty() || patchLines.isEmpty()) {
            return // Base case: prevent excessive recursion
        }
        val sourceSegment = sourceLines.filter { it.matchingLine == null }
        val patchSegment = patchLines.filter { it.matchingLine == null }
        if (sourceSegment.isNotEmpty() && patchSegment.isNotEmpty()) {
            var matchedLines = linkUniqueMatchingLines(sourceSegment, patchSegment)
            matchedLines += linkAdjacentMatchingLines(sourceSegment, levenshteinDistance)
            if (matchedLines == 0) {
                matchedLines += matchFirstBrackets(sourceSegment, patchSegment)
            }
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
                    // Delete the line -- do not add to patched text
                    usedPatchLines.add(patchLine)
                    checkAfterForInserts(patchLine, usedPatchLines, patchedText)
                    lastMatchedPatchIndex = patchLine.index
                }

                codeLine.matchingLine != null -> {
                    val patchLine: LineRecord = codeLine.matchingLine!!
                    log.debug("Patching line: {} <-> {}", codeLine, patchLine)
                    checkBeforeForInserts(patchLine, usedPatchLines, patchedText)
                    usedPatchLines.add(patchLine)
                    // Use the source line if it matches the patch line (ignoring whitespace)
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
        if (lastMatchedPatchIndex == -1) patchLines.filter { it.type == ADD && !usedPatchLines.contains(it) }
            .forEach { line ->
                log.debug("Added patch line: {}", line)
                patchedText.add(line.line ?: "")
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
        while (null != nextPatchLine) {
            while (nextPatchLine != null && (
                        normalizeLine(nextPatchLine.line ?: "").isEmpty() ||
                                (nextPatchLine.matchingLine == null && nextPatchLine.type == CONTEXT)
                        )
            ) {
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

    private fun matchFirstBrackets(sourceLines: List<LineRecord>, patchLines: List<LineRecord>): Int {
        log.debug("Starting to match first brackets")
        log.debug("Starting to link unique matching lines")
        // Group source lines by their normalized content
        val sourceLineMap = sourceLines.filter {
            it.line?.lineMetrics() != LineMetrics()
        }.groupBy { normalizeLine(it.line!!) }
        // Group patch lines by their normalized content, excluding ADD lines
        val patchLineMap = patchLines.filter {
            it.line?.lineMetrics() != LineMetrics()
        }.filter {
            when (it.type) {
                ADD -> false // ADD lines are not matched to source lines
                else -> true
            }
        }.groupBy { normalizeLine(it.line!!) }
        log.debug("Created source and patch line maps")

        // Find intersecting keys (matching lines) and link them
        val matched = sourceLineMap.keys.intersect(patchLineMap.keys)
        matched.forEach { key ->
            val sourceGroup = sourceLineMap[key]!!
            val patchGroup = patchLineMap[key]!!
            for (i in 0 until min(sourceGroup.size, patchGroup.size)) {
                sourceGroup[i].matchingLine = patchGroup[i]
                patchGroup[i].matchingLine = sourceGroup[i]
                log.debug("Linked matching lines: Source[${sourceGroup[i].index}]: ${sourceGroup[i].line} <-> Patch[${patchGroup[i].index}]: ${patchGroup[i].line}")
            }
        }
        val matchedCount = matched.sumOf { sourceLineMap[it]!!.size }
        log.debug("Finished matching first brackets. Matched $matchedCount lines")
        return matched.sumOf { sourceLineMap[it]!!.size }
    }

    /**
     * Links lines between the source and the patch that are unique and match exactly.
     * @param sourceLines The source lines.
     * @param patchLines The patch lines.
     */
    private fun linkUniqueMatchingLines(sourceLines: List<LineRecord>, patchLines: List<LineRecord>): Int {
        log.debug("Starting to link unique matching lines. Source lines: ${sourceLines.size}, Patch lines: ${patchLines.size}")
        // Group source lines by their normalized content
        val sourceLineMap = sourceLines.groupBy { normalizeLine(it.line!!) }
        // Group patch lines by their normalized content, excluding ADD lines
        val patchLineMap = patchLines.filter {
            when (it.type) {
                ADD -> false // ADD lines are not matched to source lines
                else -> true
            }
        }.groupBy { normalizeLine(it.line!!) }
        log.debug("Created source and patch line maps")

        // Find intersecting keys (matching lines) and link them
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
        // Continue linking until no more matches are found
        while (foundMatch) {
            log.debug("Starting new iteration to find adjacent matches")
            foundMatch = false
            for (sourceLine in sourceLines) {
                val patchLine = sourceLine.matchingLine ?: continue // Skip if there's no matching line
                val patchPrev = findPreviousValidLine(patchLine.previousLine, skipAdd = true, skipEmpty = true)
                val sourcePrev = findPreviousValidLine(sourceLine.previousLine, skipEmpty = true)

                if (sourcePrev != null && sourcePrev.matchingLine == null &&
                    patchPrev != null && patchPrev.matchingLine == null
                ) { // Skip if there's already a match
                    if (isMatch(sourcePrev, patchPrev, levenshtein)) { // Check if the lines match exactly
                        sourcePrev.matchingLine = patchPrev
                        patchPrev.matchingLine = sourcePrev
                        foundMatch = true
                        matchedLines++
                        log.debug("Linked adjacent previous lines: Source[${sourcePrev.index}]: ${sourcePrev.line} <-> Patch[${patchPrev.index}]: ${patchPrev.line}")
                    }
                }

                var patchNext = patchLine.nextLine
                while (patchNext?.nextLine != null &&
                    (patchNext.type == ADD || normalizeLine(patchNext.line ?: "").isEmpty())
                ) {
                    require(patchNext !== patchNext.nextLine)
                    patchNext = patchNext.nextLine!!
                }

                var sourceNext = sourceLine.nextLine
                while (sourceNext?.nextLine != null && (normalizeLine(sourceNext.line ?: "").isEmpty())) {
                    require(sourceNext !== sourceNext.nextLine)
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
        val normalizedSource = normalizeLine(sourcePrev.line!!)
        val normalizedPatch = normalizeLine(patchPrev.line!!)
        if (normalizedSource == normalizedPatch) return true
        val maxLength = max(normalizedSource.length, normalizedPatch.length)
        // Use Levenshtein distance if available and the strings are long enough
        if (maxLength > 5 && levenshteinDistance != null) {
            val distance = levenshteinDistance.apply(normalizedSource, normalizedPatch)
            log.debug("Levenshtein distance: $distance")
            return distance <= floor(maxLength / 4.0).toInt()
        }
        return false
    }

    /**
     * @param text The text to parse.
     * @return The list of line records.
     */
    private fun parseLines(text: String): List<LineRecord> {
        log.debug("Starting to parse lines")
        // Create LineRecords for each line and set links between them
        val lines = setLinks(text.lines().mapIndexed { index, line -> LineRecord(index, line) })
        // Calculate bracket metrics for each line
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
            LineRecord(
                index = index,
                line = run {
                    val trimmed = line.trimStart()
                    when {
                        trimmed.startsWith("+++") || trimmed.startsWith("---") || trimmed.startsWith("@@") -> null
                        // If one of the source lines already equals this (normalized), keep as-is
                        sourceLines.any { patchLine ->
                            normalizeLine(
                                patchLine.line ?: ""
                            ) == normalizeLine(line)
                        } -> line

                        trimmed.startsWith("+") -> trimmed.substring(1)
                        trimmed.startsWith("-") -> trimmed.substring(1)
                        else -> line
                    }
                },
                type = when {
                    line.trimStart().startsWith("+") -> ADD
                    line.trimStart().startsWith("-") -> DELETE
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
        // Fixup: Iterate over the patch lines and look for adjacent ADD and DELETE lines; the DELETE should come first... if needed, swap them
        var swapped: Boolean
        do {
            swapped = false
            for (i in 0 until patchLines.size - 1) {
                if (patchLines[i].type == ADD && patchLines[i + 1].type == DELETE) {
                    swapped = true
                    val addLine = patchLines[i]
                    val deleteLine = patchLines[i + 1]
                    // Swap records and update pointers
                    val nextLine = deleteLine.nextLine
                    val previousLine = addLine.previousLine

                    require(addLine !== deleteLine)
                    if (previousLine === deleteLine) {
                        throw RuntimeException("previousLine === deleteLine")
                    }
                    require(previousLine !== deleteLine)
                    require(nextLine !== addLine)
                    require(nextLine !== deleteLine)
                    deleteLine.nextLine = addLine
                    addLine.previousLine = deleteLine
                    deleteLine.previousLine = previousLine
                    addLine.nextLine = nextLine
                    patchLines[i] = deleteLine
                    patchLines[i + 1] = addLine
                }
            }
        } while (swapped)
        log.debug("Finished fixing patch line order")
    }

    /**
     * Calculates the metrics for each line, including bracket nesting depth.
     * @param lines The list of line records to process.
     */
    private fun calculateLineMetrics(lines: List<LineRecord>) {
        log.debug("Starting to calculate line metrics for ${lines.size} lines")
        lines.fold(
            Triple(0, 0, 0)
        ) { (parenDepth, squareDepth, curlyDepth), lineRecord ->
            val updatedDepth = lineRecord.line?.fold(Triple(parenDepth, squareDepth, curlyDepth)) { acc, char ->
                when (char) {
                    '(' -> Triple(acc.first + 1, acc.second, acc.third)
                    ')' -> Triple(max(0, acc.first - 1), acc.second, acc.third)
                    '[' -> Triple(acc.first, acc.second + 1, acc.third)
                    ']' -> Triple(acc.first, max(0, acc.second - 1), acc.third)
                    '{' -> Triple(acc.first, acc.second, acc.third + 1)
                    '}' -> Triple(acc.first, acc.second, max(0, acc.third - 1))
                    else -> acc
                }
            } ?: Triple(parenDepth, squareDepth, curlyDepth)
            lineRecord.metrics = LineMetrics(
                parenthesesDepth = updatedDepth.first,
                squareBracketsDepth = updatedDepth.second,
                curlyBracesDepth = updatedDepth.third
            )
            updatedDepth
        }
        log.debug("Finished calculating line metrics")
    }

    private fun String.lineMetrics(): LineMetrics {
        var parenthesesDepth = 0
        var squareBracketsDepth = 0
        var curlyBracesDepth = 0

        this.forEach { char ->
            when (char) {
                '(' -> parenthesesDepth++
                ')' -> parenthesesDepth = maxOf(0, parenthesesDepth - 1)
                '[' -> squareBracketsDepth++
                ']' -> squareBracketsDepth = maxOf(0, squareBracketsDepth - 1)
                '{' -> curlyBracesDepth++
                '}' -> curlyBracesDepth = maxOf(0, curlyBracesDepth - 1)
            }
        }
        return LineMetrics(
            parenthesesDepth = parenthesesDepth,
            squareBracketsDepth = squareBracketsDepth,
            curlyBracesDepth = curlyBracesDepth
        )
    }

    val patchFormatPrompt = """
      Response should use one or more code patches in diff format within ```diff code blocks.
      Each diff should be preceded by a header that identifies the file being modified.
      The diff format should use + for line additions, - for line deletions.
      The diff should include 2 lines of context before and after every change.
      
      Example:
      
      Here are the patches:
      
      ### src/utils/exampleUtils.js
      ```diff
       // Utility functions for example feature
       const b = 2;
       function exampleFunction() {
      -   return b + 1;
      +   return b + 2;
       }
      ```
      
      ### tests/exampleUtils.test.js
      ```diff
       // Unit tests for exampleUtils
       const assert = require('assert');
       const { exampleFunction } = require('../src/utils/exampleUtils');
       
       describe('exampleFunction', () => {
      -   it('should return 3', () => {
      +   it('should return 4', () => {
           assert.equal(exampleFunction(), 3);
         });
       });
      ```
      
      If needed, new files can be created by using code blocks labeled with the filename in the same manner.
      """.trimIndent()

    private fun findNextMatchingLine(
        start: LineRecord?,
        skipAdd: Boolean = false,
        skipDelete: Boolean = false
    ): LineRecord? {
        var current = start
        while (current != null) {
            if ((skipAdd && current.type == ADD) ||
                (skipDelete && current.type == DELETE) ||
                current.matchingLine == null
            ) {
                current = current.nextLine
            } else {
                return current
            }
        }
        return null
    }

    private fun findPreviousValidLine(
        start: LineRecord?,
        skipAdd: Boolean = false,
        skipEmpty: Boolean = false
    ): LineRecord? {
        var current = start
        while (current != null) {
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

    private val log = LoggerFactory.getLogger(IterativePatchUtil::class.java)

}