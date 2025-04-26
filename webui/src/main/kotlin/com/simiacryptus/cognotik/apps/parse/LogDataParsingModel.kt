package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel

open class LogDataParsingModel(
    private val parsingModel: ChatModel,
    private val temperature: Double
) : ParsingModel<LogDataParsingModel.LogData> {
    private val maxIterations = 10 // Make constant value explicit for clarity


    override fun merge(
        runningDocument: LogData,
        newData: LogData
    ): LogData {
        return LogData(
            id = newData.id ?: runningDocument.id,
            patterns = (runningDocument.patterns ?: emptyList()) + (newData.patterns ?: emptyList()),
            matches = (runningDocument.matches ?: emptyList()) + (newData.matches ?: emptyList()),
        )
    }

    open val exampleInstance = LogData()

    override fun getFastParser(api: API): (String) -> LogData {
        val patternGenerator = LogPatternGenerator(parsingModel, temperature)
        return { originalText ->
            parseText(originalText, patternGenerator, api, emptyList())
        }
    }

    override fun getSmartParser(api: API): (LogData, String) -> LogData {
        val patternGenerator = LogPatternGenerator(parsingModel, temperature)
        return { runningDocument, prompt ->
            parseText(prompt, patternGenerator, api, runningDocument.patterns ?: emptyList())
        }
    }

    private fun parseText(
        originalText: String,
        patternGenerator: LogPatternGenerator,
        api: API,
        existingPatterns: List<PatternData>
    ): LogData {
        var remainingText = originalText
        var result: LogData? = null
        var iterationCount = 0
        var currentPatterns = existingPatterns
        try {
            // First try with existing patterns
            if (currentPatterns.isNotEmpty()) {
                val applyPatterns = applyPatterns(remainingText, currentPatterns)
                result = applyPatterns.first
                remainingText = applyPatterns.second
            }
            // Then generate new patterns for remaining text
            while (remainingText.isNotBlank() && iterationCount++ < maxIterations) {
                val newPatterns = patternGenerator.generatePatterns(api, remainingText)
                if (newPatterns.isEmpty()) break
                currentPatterns = (currentPatterns + newPatterns).distinctBy { it.regex }
                val applyPatterns = applyPatterns(remainingText, currentPatterns)
                result = if (result != null) {
                    merge(result, applyPatterns.first)
                } else {
                    applyPatterns.first
                }
                remainingText = applyPatterns.second
            }
        } catch (e: Exception) {
            log.error("Error parsing log data", e)
        }
        return result ?: LogData()
    }


    private fun applyPatterns(text: String, patterns: List<PatternData>): Pair<LogData, String> {
        val patterns = patterns.filter { it.regex != null }.groupBy { it.id }.map { it.value.first() }
        val matches = patterns.flatMap { pattern ->
            try {
                val regexOptions = mutableSetOf<RegexOption>().apply {
                    if (pattern.multiline) add(RegexOption.MULTILINE)
                    if (pattern.dotMatchesAll) add(RegexOption.DOT_MATCHES_ALL)
                    if (pattern.ignoreCase) add(RegexOption.IGNORE_CASE)
                    if (pattern.comments) add(RegexOption.COMMENTS)
                }
                val regex = pattern.regex?.toRegex(regexOptions)
                val matches = regex?.findAll(text)?.toList() ?: emptyList()
                matches.map { pattern to it }
            } catch (e: Exception) {
                log.error("Error applying pattern ${pattern.id}", e)
                emptyList()
            }
        }.sortedBy {
            it.second.range.first
        }.toTypedArray()
        val matchesWithRanges = mutableListOf<Pair<MatchData, Pair<PatternData, MatchResult>>>()
        val occupiedIndices = mutableListOf<IntRange>()
        matches.forEach { matchResult: Pair<PatternData, MatchResult> ->
            val range = matchResult.second.range
            val filter = occupiedIndices.filter { it.overlaps(range) }
            if (filter.isEmpty()) {
                val captures = extractCaptures((matchResult.first.regex ?: "").toRegex(), matchResult.second)
                if (captures.isNotEmpty()) {
                    matchesWithRanges.add(
                        MatchData(
                            patternId = matchResult.first.id ?: "pattern_${patterns.indexOf(matchResult.first)}",
                            captures = captures
                        ) to matchResult
                    )
                } else {
                    matchesWithRanges.add(
                        MatchData(
                            patternId = matchResult.first.id ?: "pattern_${patterns.indexOf(matchResult.first)}"
                        ) to matchResult
                    )
                }
                occupiedIndices.add(range)
            }
        }
        val remainingText = matchesWithRanges.reversed().fold(text) { acc, match ->
            acc.replaceRange(match.second.second.range, "")
        }
        // Sort matches by their start index to ensure correct ordering
        return LogData(
            matches = matchesWithRanges.sortedBy { it.second.second.range.start }.map { it.first },
            patterns = matchesWithRanges.map { it.second.first }.distinct()
        ) to remainingText
    }

    private fun extractCaptures(regex: Regex, matchResult: MatchResult): Map<String, String> {
        // Improved named group detection
        val namedGroups = regex.pattern
            .split("(?<")
            .drop(1)
            .map { it.substringBefore(">") }

        return matchResult.groups
            .filterNotNull()
            .mapIndexed { index, group ->
                when {
                    index == 0 -> null  // Skip the full match
                    index <= namedGroups.size -> namedGroups[index - 1] to group.value
                    else -> "group${index}" to group.value
                }
            }
            .filterNotNull()
            .toMap()
    }


    override fun newDocument() = LogData()

    data class LogData(
        @Description("Log file identifier")
        override val id: String? = null,
        @Description("List of identified regex patterns")
        val patterns: List<PatternData>? = null,
        @Description("List of pattern matches found")
        val matches: List<MatchData>? = null,
    ) : ParsingModel.DocumentData {
        override val content_list: List<ParsingModel.ContentData>? = null
    }

    data class PatternData(
        @Description("Unique identifier for the pattern")
        val id: String? = null,
        @Description("Regular expression with named capture groups")
        val regex: String? = null,
        @Description("Description of what this pattern matches")
        val description: String? = null,
        @Description("Enable multiline mode - ^ and $ match line start/end")
        val multiline: Boolean = true,
        @Description("Enable dot matches all - dot matches newlines")
        val dotMatchesAll: Boolean = true,
        @Description("Enable case insensitive matching")
        val ignoreCase: Boolean = false,
        @Description("Enable comments and whitespace in pattern")
        val comments: Boolean = false
    )

    data class MatchData(
        @Description("The ID of the pattern that matched")
        val patternId: String? = null,
        @Description("Map of captured group names to values")
        val captures: Map<String, String>? = null
    )

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(LogDataParsingModel::class.java)
    }
}

private fun IntRange.overlaps(range: IntRange): Boolean {
    return this.first <= range.last && this.last >= range.first || range.first <= this.last && range.last >= this.first
}