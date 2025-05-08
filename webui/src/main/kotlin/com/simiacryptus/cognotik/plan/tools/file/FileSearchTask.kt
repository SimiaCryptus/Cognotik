package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.math.max

class FileSearchTask(
    planSettings: PlanSettings,
    planTask: SearchTaskConfigData?
) : AbstractTask<FileSearchTask.SearchTaskConfigData>(planSettings, planTask) {
    // SearchTaskConfigData remains the same
    class SearchTaskConfigData(
        @Description("The search pattern (substring or regex) to look for in the files")
        val search_pattern: String = "",
        @Description("Whether the search pattern is a regex (true) or a substring (false)")
        val is_regex: Boolean = false,
        @Description("The number of context lines to include before and after each match")
        val context_lines: Int = 2,
        @Description("The specific files (or file patterns) to be searched")
        val input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskConfigBase(
        task_type = TaskType.FileSearchTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )
    // promptSegment remains the same

    override fun promptSegment() = """
FileSearchTask - Search for patterns in files and provide results with context
* Specify the search pattern (substring or regex)
* Specify whether the pattern is a regex or a substring
* Specify the number of context lines to include
* List files (incl glob patterns) to be searched
Available files:
${getAvailableFiles(root).joinToString("\n") { "  - $it" }}
""".trimIndent()
    // run remains the same

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        val searchResults = performSearch()
        val formattedResults = formatSearchResults(searchResults)
        task.add(MarkdownUtil.renderMarkdown(formattedResults, ui = agent.ui))
        resultFn(formattedResults)
    }

    // Temporary holder for a raw match within a file
    private data class RawMatch(val lineNumber: Int, val lineContent: String) // lineNumber is 1-based

    // Represents a block of context that might contain multiple matches
    private data class DisplayBlock(
        val file: String,
        val contextLines: List<String>, // The actual lines of the combined context
        val firstLineNumberInFile: Int, // 1-based line number in the original file for contextLines[0]
        val matches: List<MatchInBlock> // Original matches that fall into this block
    )

    // Info about each original match within a DisplayBlock
    private data class MatchInBlock(
        val originalLineNumber: Int, // 1-based line number in the file
        val indexInDisplayBlockContext: Int // 0-based index within DisplayBlock.contextLines
    )

    private fun performSearch(): List<DisplayBlock> {
        val currentConfig = taskConfig
        if (currentConfig == null) {
            log.warn("FileSearchTask taskConfig is null. Cannot perform search.")
            return emptyList()
        }

        val pattern = if (currentConfig.is_regex) {
            Pattern.compile(currentConfig.search_pattern)
        } else {
            Pattern.compile(Pattern.quote(currentConfig.search_pattern))
        }

        return (currentConfig.input_files ?: emptyList())
            .flatMap { filePattern ->
                val matcher = FileSystems.getDefault().getPathMatcher("glob:$filePattern")
                FileSelectionUtils.filteredWalk(root.toFile()) {
                    path -> matcher.matches(root.relativize(path.toPath())) && !FileSelectionUtils.isLLMIgnored(path.toPath())
                }.map { it.toPath() }.flatMap { path ->
                    try {
                        val fileContentLines = Files.readAllLines(path)
                        val relativePath = root.relativize(path).toString()

                        // 1. Find all individual raw matches (line number and content)
                        val rawMatches = fileContentLines.mapIndexedNotNull { index, line ->
                            if (pattern.matcher(line).find()) {
                                RawMatch(lineNumber = index + 1, lineContent = line) // 1-based line number
                            } else null
                        }
                        if (rawMatches.isEmpty()) return@flatMap emptyList<DisplayBlock>()
                        // 2. Group raw matches into DisplayBlocks
                        val combinedBlocks = mutableListOf<DisplayBlock>()
                        var currentBlockAggregatedMatches = mutableListOf<RawMatch>()
                        var currentBlockContextStartLineInFile = 0 // 1-based
                        var currentBlockContextEndLineInFile = 0   // 1-based
                        val contextLinesCount = currentConfig.context_lines
                        for (match in rawMatches) { // rawMatches are already sorted by line number
                            val matchIdealContextStart = (match.lineNumber - contextLinesCount).coerceAtLeast(1)
                            val matchIdealContextEnd = (match.lineNumber + contextLinesCount).coerceAtMost(fileContentLines.size)
                            if (currentBlockAggregatedMatches.isEmpty() || matchIdealContextStart > currentBlockContextEndLineInFile + 1) {
                                // Finalize previous block if it exists
                                if (currentBlockAggregatedMatches.isNotEmpty()) {
                                    val actualContext = fileContentLines.subList(
                                        (currentBlockContextStartLineInFile - 1).coerceAtLeast(0), // to 0-based index
                                        currentBlockContextEndLineInFile.coerceAtMost(fileContentLines.size) // exclusive end
                                    )
                                    combinedBlocks.add(DisplayBlock(
                                        file = relativePath,
                                        contextLines = actualContext,
                                        firstLineNumberInFile = currentBlockContextStartLineInFile,
                                        matches = currentBlockAggregatedMatches.map { aggMatch ->
                                            MatchInBlock(
                                                originalLineNumber = aggMatch.lineNumber,
                                                indexInDisplayBlockContext = aggMatch.lineNumber - currentBlockContextStartLineInFile
                                            )
                                        }
                                    ))
                                }
                                // Start a new block
                                currentBlockAggregatedMatches = mutableListOf(match)
                                currentBlockContextStartLineInFile = matchIdealContextStart
                                currentBlockContextEndLineInFile = matchIdealContextEnd
                            } else {
                                // Merge with current block
                                currentBlockAggregatedMatches.add(match)
                                // currentBlockContextStartLineInFile remains the earliest start (already set)
                                currentBlockContextEndLineInFile = max(currentBlockContextEndLineInFile, matchIdealContextEnd)
                            }
                        }
                        // Add the last processed block
                        if (currentBlockAggregatedMatches.isNotEmpty()) {
                            val actualContext = fileContentLines.subList(
                                (currentBlockContextStartLineInFile - 1).coerceAtLeast(0),
                                currentBlockContextEndLineInFile.coerceAtMost(fileContentLines.size)
                            )
                            combinedBlocks.add(DisplayBlock(
                                file = relativePath,
                                contextLines = actualContext,
                                firstLineNumberInFile = currentBlockContextStartLineInFile,
                                matches = currentBlockAggregatedMatches.map { aggMatch ->
                                    MatchInBlock(
                                        originalLineNumber = aggMatch.lineNumber,
                                        indexInDisplayBlockContext = aggMatch.lineNumber - currentBlockContextStartLineInFile
                                    )
                                }
                            ))
                        }
                        combinedBlocks // Return list of blocks for this file
                    } catch (e: Exception) {
                        log.warn("Error processing file ${root.relativize(path)} for search: ${e.message}", e)
                        emptyList()
                    }
                }
            }
    }


    private fun formatSearchResults(results: List<DisplayBlock>, maxLength: Int = 500000): String {
        if (results.isEmpty()) {
            return "# Search Results\n\nNo matches found."
        }

        val sb = StringBuilder()
        val truncationMessage = "\n\n... (results truncated due to length limit)"
        // Effective max length for content, allowing space for truncation message if needed.
        // If maxLength is too small to even hold the truncation message, effectiveMaxLength might be 0 or negative.
        val effectiveMaxLength = if (maxLength > truncationMessage.length) maxLength - truncationMessage.length else 0

        var outputTruncated = false

        // Helper to append string segments, checking against effectiveMaxLength
        fun StringBuilder.appendCheckingLength(str: String): Boolean {
            if (this.length + str.length > effectiveMaxLength && effectiveMaxLength > 0) { // Check effectiveMaxLength > 0 to avoid issues if it's 0
                val remainingSpace = effectiveMaxLength - this.length
                if (remainingSpace > 0) {
                    this.append(str.take(remainingSpace))
                }
                outputTruncated = true
                return false // Signal to stop further appends to main content
            } else if (effectiveMaxLength <= 0 && maxLength > 0) { // Not enough space for content + truncation message
                // This case means we can only fit a small part of the content or just the truncation message
                outputTruncated = true
                return false
            }
            this.append(str)
            return true // Signal to continue
        }

        // Handle extremely small maxLength
        if (maxLength <= 0) return ""
        if (maxLength < 20 && results.isNotEmpty()) { // Arbitrary small number, too small for meaningful output
            return truncationMessage.trimStart().take(maxLength) // Show a part of truncation message if possible
        }

        if (!sb.appendCheckingLength("# Search Results\n\n")) {
            if (outputTruncated) { // Append truncation message if space allows, within original maxLength
                val finalMsg = truncationMessage.trimStart()
                sb.clear() // Clear partially added header
                sb.append(finalMsg.take(maxLength))
            }
            return sb.toString()
        }

        val totalMatches = results.sumOf { it.matches.size }
        val filesWithMatches = results.distinctBy { it.file }.size // Correctly counts files based on DisplayBlock.file
        val summary = "Found $totalMatches match(es) in $filesWithMatches file(s).\n\n"
        if (!sb.appendCheckingLength(summary)) {
            if (outputTruncated) { // Append truncation message, ensuring total length <= maxLength
                val spaceForMessage = maxLength - sb.length
                if (spaceForMessage > 0) sb.append(truncationMessage.take(spaceForMessage))
            }
            return sb.toString().take(maxLength) // Ensure final length constraint
        }

        results.groupBy { it.file }.forEach { (file, fileBlocks) -> // fileBlocks is List<DisplayBlock>
            if (outputTruncated) return@forEach

            val fileHeader = "## $file\n\n"
            if (!sb.appendCheckingLength(fileHeader)) return@forEach

            fileBlocks.forEach { block -> // Iterate over each DisplayBlock
                if (outputTruncated) return@forEach

                val blockEndLine = block.firstLineNumberInFile + block.contextLines.size - 1
                val resultHeader = "### Lines ${block.firstLineNumberInFile} - $blockEndLine\n\n"
                
                val contextBlockString = buildString {
                    appendLine("```")
                    block.contextLines.forEachIndexed { indexInBlock, lineContent ->
                        val actualLineNumber = block.firstLineNumberInFile + indexInBlock
                        // Check if this line is one of the actual matches
                        val isMatchedLine = block.matches.any { it.indexInDisplayBlockContext == indexInBlock }
                        val prefix = if (isMatchedLine) ">" else " "
                        appendLine("$prefix ${actualLineNumber.toString().padStart(5)}: $lineContent")
                    }
                    appendLine("```")
                    appendLine() // Extra newline after the block
                }
                val fullResultBlock = resultHeader + contextBlockString
                if (!sb.appendCheckingLength(fullResultBlock)) return@forEach
            }
        }

        if (outputTruncated) {
            val spaceForMessage = maxLength - sb.length
            if (spaceForMessage > 0) {
                sb.append(truncationMessage.take(spaceForMessage))
            }
        }

        return sb.toString().take(maxLength) // Final safeguard
    }

    // SearchResult data class is removed, replaced by DisplayBlock and MatchInBlock defined earlier
    // getContext method is removed as it's no longer used

    companion object {
        private val log = LoggerFactory.getLogger(FileSearchTask::class.java)
        fun getAvailableFiles(path: Path): List<String> {
            return try {
                listOf(FileSelectionUtils.filteredWalkAsciiTree(path.toFile(), 20))
            } catch (e: Exception) {
                log.error("Error listing available files", e)
                listOf("Error listing files: ${e.message}")
            }
        }
    }
}