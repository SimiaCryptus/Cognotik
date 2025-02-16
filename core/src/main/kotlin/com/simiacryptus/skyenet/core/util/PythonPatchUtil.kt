package com.simiacryptus.skyenet.core.util
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.LoggerFactory
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
/**
 * PythonPatchUtil is an alternate diffing utility optimized for Python and YAML.
 * In Python/YAML code the leading spaces (indentation) are significant, so our normalizer
 * only removes trailing whitespace. The bracket‐metrics from IterativePatchUtil are omitted.
 */
object PythonPatchUtil {
    private enum class LineType { CONTEXT, ADD, DELETE }
    // Represents a single line in the source or patch text.
    // Note: Removed bracket metrics as they are not relevant for Python/YAML.
    private data class LineRecord(
        val index: Int,
        val line: String?,
        var previousLine: LineRecord? = null,
        var nextLine: LineRecord? = null,
        var matchingLine: LineRecord? = null,
        var type: LineType = LineType.CONTEXT
    ) {
        override fun toString(): String {
            return "${index.toString().padStart(5, ' ')}: ${when(type) {
                LineType.CONTEXT -> " "
                LineType.ADD -> "+"
                LineType.DELETE -> "-"
            }} $line"
        }
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LineRecord) return false
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
    /**
     * Generate a patch from oldCode to newCode.
     */
    fun generatePatch(oldCode: String, newCode: String): String {
        log.info("Starting python/yaml patch generation process")
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
        shortDiff.forEach { line ->
            when (line.type) {
                LineType.CONTEXT -> patch.append("  ${line.line}\n")
                LineType.ADD -> patch.append("+ ${line.line}\n")
                LineType.DELETE -> patch.append("- ${line.line}\n")
            }
        }
        log.info("Patch generation completed")
        return patch.toString().trimEnd()
    }
    /**
     * Applies a patch to the given source text.
     */
    fun applyPatch(source: String, patch: String): String {
        log.info("Starting python/yaml patch application process")
        val sourceLines = parseLines(source)
        var patchLines = parsePatchLines(patch, sourceLines)
        log.debug("Parsed source lines: ${sourceLines.size}, initial patch lines: ${patchLines.size}")
        link(sourceLines, patchLines, LevenshteinDistance())
        patchLines = patchLines.filter { it.line != null && normalizeLine(it.line!!).isNotEmpty() }
        log.debug("Filtered patch lines: ${patchLines.size}")
        val result = generatePatchedText(sourceLines, patchLines)
        val patchedText = result.joinToString("\n").trim()
        log.info("Patch application completed")
        return patchedText
    }
    private fun annihilateNoopLinePairs(diff: MutableList<LineRecord>) {
        log.debug("Starting annihilation of no-op line pairs")
        val toRemove = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < diff.size - 1) {
            if (diff[i].type == LineType.DELETE) {
                var j = i + 1
                while (j < diff.size && diff[j].type != LineType.CONTEXT) {
                    if (diff[j].type == LineType.ADD &&
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
        toRemove.flatMap { listOf(it.first, it.second) }
            .distinct()
            .sortedDescending()
            .forEach { diff.removeAt(it) }
        log.debug("Removed ${toRemove.size} no-op line pairs")
    }
    private fun markMovedLines(newLines: List<LineRecord>) {
        log.debug("Starting to mark moved lines (python/yaml)")
        var newLine = newLines.firstOrNull()
        var iterationCount = 0
        val maxIterations = newLines.size * 2
        while (newLine != null) {
            try {
                if (newLine.matchingLine != null) {
                    var nextNewLine = newLine.nextLine ?: break
                    try {
                        while (nextNewLine.matchingLine == null || nextNewLine.type == LineType.ADD) {
                            nextNewLine = nextNewLine.nextLine ?: break
                        }
                        if (nextNewLine.matchingLine == null || nextNewLine.type == LineType.ADD) break
                        val sourceLine = newLine.matchingLine!!
                        log.debug("Processing patch line ${newLine.index} with matching source line ${sourceLine.index}")
                        var nextSourceLine = sourceLine.nextLine ?: continue
                        while (nextSourceLine.matchingLine == null || nextSourceLine.type == LineType.DELETE) {
                            if (++iterationCount > maxIterations) {
                                log.error("Exceeded maximum iterations in markMovedLines")
                                break
                            }
                            nextSourceLine = nextSourceLine.nextLine ?: break
                        }
                        if (nextSourceLine.matchingLine == null || nextSourceLine.type == LineType.DELETE) break
                        while (nextNewLine.matchingLine != nextSourceLine) {
                            if (nextSourceLine.matchingLine != null) {
                                nextSourceLine.type = LineType.DELETE
                                nextSourceLine.matchingLine!!.type = LineType.ADD
                                log.debug("Marked moved line: Patch[${nextSourceLine.index}] as ADD, Source[${nextSourceLine.matchingLine!!.index}] as DELETE")
                            }
                            nextSourceLine = nextSourceLine.nextLine ?: break
                            while (nextSourceLine.matchingLine == null || nextSourceLine.type == LineType.DELETE) {
                                nextSourceLine = nextSourceLine.nextLine ?: continue
                            }
                        }
                    } finally {
                        if (++iterationCount > maxIterations) {
                            log.error("Exceeded maximum iterations in markMovedLines")
                            newLine = newLine.nextLine
                        }
                    }
                } else {
                    newLine = newLine.nextLine
                }
            } catch (e: Exception) {
                log.error("Error marking moved lines", e)
            }
        }
        log.debug("Finished marking moved lines")
    }
    private fun newToPatch(newLines: List<LineRecord>): MutableList<LineRecord> {
        val diff = mutableListOf<LineRecord>()
        log.debug("Starting diff generation for python/yaml")
        var newLine = newLines.firstOrNull()
        while (newLine != null) {
            val sourceLine = newLine.matchingLine
            when {
                sourceLine == null || newLine.type == LineType.ADD -> {
                    diff.add(LineRecord(newLine.index, newLine.line, type = LineType.ADD))
                    log.debug("Added ADD line: ${newLine.line}")
                }
                else -> {
                    var priorSourceLine = sourceLine.previousLine
                    val lineBuffer = mutableListOf<LineRecord>()
                    while (priorSourceLine != null &&
                        (priorSourceLine.matchingLine == null || priorSourceLine.type == LineType.DELETE)
                    ) {
                        lineBuffer.add(LineRecord(-1, priorSourceLine.line, type = LineType.DELETE))
                        priorSourceLine = priorSourceLine.previousLine
                    }
                    diff.addAll(lineBuffer.reversed())
                    diff.add(LineRecord(newLine.index, newLine.line, type = LineType.CONTEXT))
                    log.debug("Added CONTEXT line: ${sourceLine.line}")
                }
            }
            newLine = newLine.nextLine
        }
        log.debug("Generated diff with ${diff.size} lines")
        return diff
    }
    private fun truncateContext(diff: MutableList<LineRecord>): MutableList<LineRecord> {
        val contextSize = 3 // Number of context lines before and after changes.
        log.debug("Truncating context with size $contextSize")
        val truncatedDiff = mutableListOf<LineRecord>()
        val contextBuffer = mutableListOf<LineRecord>()
        for (i in diff.indices) {
            val line = diff[i]
            when {
                line.type != LineType.CONTEXT -> {
                    if (contextSize * 2 < contextBuffer.size) {
                        if (truncatedDiff.isNotEmpty()) {
                            truncatedDiff.addAll(contextBuffer.take(contextSize))
                            truncatedDiff.add(LineRecord(-1, "...", type = LineType.CONTEXT))
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
     * Normalizes a line for Python/YAML by preserving leading whitespace (indentation)
     * and removing any trailing whitespace.
     */
    private fun normalizeLine(line: String): String {
        return line.replace(Regex("\\s+$"), "")
    }
    private fun link(sourceLines: List<LineRecord>, patchLines: List<LineRecord>, levenshteinDistance: LevenshteinDistance?) {
        log.info("Step 1: Linking unique matching lines")
        linkUniqueMatchingLines(sourceLines, patchLines)
        log.info("Step 2: Linking adjacent matching lines")
        linkAdjacentMatchingLines(sourceLines, levenshteinDistance)
        log.info("Step 3: Performing subsequence linking")
        subsequenceLinking(sourceLines, patchLines, levenshteinDistance = levenshteinDistance)
    }
    private fun subsequenceLinking(sourceLines: List<LineRecord>, patchLines: List<LineRecord>, depth: Int = 0, levenshteinDistance: LevenshteinDistance?) {
        log.debug("Subsequence linking at depth $depth")
        if (depth > 10 || sourceLines.isEmpty() || patchLines.isEmpty()) {
            return
        }
        val sourceSegment = sourceLines.filter { it.matchingLine == null }
        val patchSegment = patchLines.filter { it.matchingLine == null }
        if (sourceSegment.isNotEmpty() && patchSegment.isNotEmpty()) {
            var matchedLines = linkUniqueMatchingLines(sourceSegment, patchSegment)
            matchedLines += linkAdjacentMatchingLines(sourceSegment, levenshteinDistance)
            // For Python/YAML we skip advanced bracket matching; simply continue.
            if (matchedLines > 0) {
                subsequenceLinking(sourceSegment, patchSegment, depth + 1, levenshteinDistance)
            }
            log.debug("Matched $matchedLines lines in subsequence linking at depth $depth")
        }
    }
    private fun generatePatchedText(sourceLines: List<LineRecord>, patchLines: List<LineRecord>): List<String> {
        log.debug("Starting to generate patched text for python/yaml")
        val patchedText = mutableListOf<String>()
        val usedPatchLines = mutableSetOf<LineRecord>()
        var sourceIndex = -1
        var lastMatchedPatchIndex = -1
        while (sourceIndex < sourceLines.size - 1) {
            val codeLine = sourceLines[++sourceIndex]
            when {
                codeLine.matchingLine?.type == LineType.DELETE -> {
                    val patchLine = codeLine.matchingLine!!
                    log.debug("Deleting line: $codeLine")
                    usedPatchLines.add(patchLine)
                    checkAfterForInserts(patchLine, usedPatchLines, patchedText)
                    lastMatchedPatchIndex = patchLine.index
                }
                codeLine.matchingLine != null -> {
                    val patchLine = codeLine.matchingLine!!
                    log.debug("Patching line: $codeLine <-> $patchLine")
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
                    log.debug("Added unmatched source line: $codeLine")
                    patchedText.add(codeLine.line ?: "")
                }
            }
        }
        if (lastMatchedPatchIndex == -1) {
            patchLines.filter { it.type == LineType.ADD && !usedPatchLines.contains(it) }
                .forEach { line ->
                    log.debug("Added patch line: $line")
                    patchedText.add(line.line ?: "")
                }
        }
        log.debug("Generated patched text with ${patchedText.size} lines")
        return patchedText
    }
    private fun checkBeforeForInserts(patchLine: LineRecord, usedPatchLines: MutableSet<LineRecord>, patchedText: MutableList<String>): LineRecord? {
        val buffer = mutableListOf<String>()
        var prevPatchLine = patchLine.previousLine
        while (prevPatchLine != null) {
            if (prevPatchLine.type != LineType.ADD || usedPatchLines.contains(prevPatchLine)) {
                break
            }
            log.debug("Added unmatched patch line: $prevPatchLine")
            buffer.add(prevPatchLine.line ?: "")
            usedPatchLines.add(prevPatchLine)
            prevPatchLine = prevPatchLine.previousLine
        }
        patchedText.addAll(buffer.reversed())
        return prevPatchLine
    }
    private fun checkAfterForInserts(patchLine: LineRecord, usedPatchLines: MutableSet<LineRecord>, patchedText: MutableList<String>): LineRecord {
        var nextPatchLine = patchLine.nextLine
        while (nextPatchLine != null) {
            while (nextPatchLine != null && (normalizeLine(nextPatchLine.line ?: "").isEmpty() ||
                        (nextPatchLine.matchingLine == null && nextPatchLine.type == LineType.CONTEXT))
            ) {
                nextPatchLine = nextPatchLine.nextLine
            }
            if (nextPatchLine == null) break
            if (nextPatchLine.type != LineType.ADD) break
            if (usedPatchLines.contains(nextPatchLine)) break
            log.debug("Added unmatched patch line: $nextPatchLine")
            patchedText.add(nextPatchLine.line ?: "")
            usedPatchLines.add(nextPatchLine)
            nextPatchLine = nextPatchLine.nextLine
        }
        return nextPatchLine ?: patchLine
    }
    private fun linkUniqueMatchingLines(sourceLines: List<LineRecord>, patchLines: List<LineRecord>): Int {
        log.debug("Starting to link unique matching lines. Source lines: ${sourceLines.size}, Patch lines: ${patchLines.size}")
        val sourceLineMap = sourceLines.groupBy { normalizeLine(it.line!!) }
        val patchLineMap = patchLines.filter { it.type != LineType.ADD }.groupBy { normalizeLine(it.line!!) }
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
    private fun linkAdjacentMatchingLines(sourceLines: List<LineRecord>, levenshtein: LevenshteinDistance?): Int {
        log.debug("Starting to link adjacent matching lines. Source lines: ${sourceLines.size}")
        var foundMatch = true
        var matchedLines = 0
        while (foundMatch) {
            log.debug("Starting new iteration to find adjacent matches")
            foundMatch = false
            for (sourceLine in sourceLines) {
                val patchLine = sourceLine.matchingLine ?: continue
                val patchPrev = findPreviousValidLine(patchLine.previousLine, skipAdd = true, skipEmpty = true)
                val sourcePrev = findPreviousValidLine(sourceLine.previousLine, skipAdd = true, skipEmpty = true)
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
                while (patchNext?.nextLine != null && (patchNext.type == LineType.ADD || normalizeLine(patchNext.line ?: "").isEmpty())) {
                    require(patchNext !== patchNext.nextLine)
                    patchNext = patchNext.nextLine!!
                }
                var sourceNext = sourceLine.nextLine
                while (sourceNext?.nextLine != null && normalizeLine(sourceNext.line ?: "").isEmpty()) {
                    require(sourceNext !== sourceNext.nextLine)
                    sourceNext = sourceNext.nextLine!!
                }
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

    private fun findPreviousValidLine(
        previousLine: LineRecord?,
        skipAdd: Boolean,
        skipEmpty: Boolean,
    ): LineRecord? {
        var prev = previousLine
        while (prev != null && (skipAdd && prev.type == LineType.ADD || skipEmpty && normalizeLine(prev.line ?: "").isEmpty())) {
            prev = prev.previousLine
        }
        return prev
    }

    private fun isMatch(sourcePrev: LineRecord, patchPrev: LineRecord, levenshteinDistance: LevenshteinDistance?): Boolean {
        val normalizedSource = normalizeLine(sourcePrev.line!!)
        val normalizedPatch = normalizeLine(patchPrev.line!!)
        if (normalizedSource == normalizedPatch) return true
        val maxLength = max(normalizedSource.length, normalizedPatch.length)
        if (maxLength > 5 && levenshteinDistance != null) {
            val distance = levenshteinDistance.apply(normalizedSource, normalizedPatch)
            log.debug("Levenshtein distance: $distance")
            return distance <= floor(maxLength / 4.0).toInt()
        }
        return false
    }
    private fun parseLines(text: String): List<LineRecord> {
        log.debug("Starting to parse lines for python/yaml")
        val lines = setLinks(text.lines().mapIndexed { index, line -> LineRecord(index, line) })
        log.debug("Finished parsing ${lines.size} lines")
        return lines
    }
    private fun setLinks(list: List<LineRecord>): List<LineRecord> {
        log.debug("Starting to set links for ${list.size} lines")
        for (i in list.indices) {
            list[i].previousLine = if (i <= 0) null else list[i - 1]
            list[i].nextLine = if (i >= list.size - 1) null else list[i + 1]
        }
        log.debug("Finished setting links for ${list.size} lines")
        return list
    }
    private fun parsePatchLines(text: String, sourceLines: List<LineRecord>): List<LineRecord> {
        log.debug("Starting to parse patch lines for python/yaml")
        val patchLines = setLinks(text.lines().mapIndexed { index, line ->
            LineRecord(
                index = index,
                line = run {
                    val trimmed = line.trimStart()
                    when {
                        trimmed.startsWith("+++") || trimmed.startsWith("---") || trimmed.startsWith("@@") -> null
                        sourceLines.any { patchLine -> normalizeLine(patchLine.line ?: "") == normalizeLine(line) } -> line
                        trimmed.startsWith("+") -> trimmed.substring(1)
                        trimmed.startsWith("-") -> trimmed.substring(1)
                        else -> line
                    }
                },
                type = when {
                    line.trimStart().startsWith("+") -> LineType.ADD
                    line.trimStart().startsWith("-") -> LineType.DELETE
                    else -> LineType.CONTEXT
                }
            )
        }.filter { it.line != null }).toMutableList()
        fixPatchLineOrder(patchLines)
        log.debug("Finished parsing ${patchLines.size} patch lines")
        return patchLines
    }
    private fun fixPatchLineOrder(patchLines: MutableList<LineRecord>) {
        log.debug("Starting to fix patch line order for python/yaml")
        var swapped: Boolean
        do {
            swapped = false
            for (i in 0 until patchLines.size - 1) {
                if (patchLines[i].type == LineType.ADD && patchLines[i + 1].type == LineType.DELETE) {
                    swapped = true
                    val addLine = patchLines[i]
                    val deleteLine = patchLines[i + 1]
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
        log.debug("Finished fixing patch line order for python/yaml")
    }
    private val log = LoggerFactory.getLogger(PythonPatchUtil::class.java)
    val patchFormatPrompt = """
      Response should use one or more code patches in diff format within ```diff code blocks.
      Each diff should be preceded by a header that identifies the file being modified.
      The diff format should use + for line additions, - for line deletions.
      The diff should include 2 lines of context before and after every change.
      Example:
      ### src/app/example.py
      ```diff
       # Utility functions for example feature
       a = 2
       def example_function():
      -    return a + 1
      +    return a + 2
      ```
      ### config/example.yaml
      ```diff
       # Configuration for example feature
       key: value
       items:
      -  - item1
      +  - item1
      +  - item2
      ```
      If needed, new files can be created by using code blocks labeled with the filename in the same manner.
      """.trimIndent()
}