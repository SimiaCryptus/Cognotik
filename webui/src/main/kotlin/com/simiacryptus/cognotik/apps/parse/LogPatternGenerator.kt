package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.Chatter
import com.simiacryptus.cognotik.describe.Description

class LogPatternGenerator(
    private val parsingModel: Chatter,
    private val temperature: Double
) {
    data class PatternResponse(
        @Description("List of identified regex patterns")
        val patterns: List<LogDataParsingModel.PatternData>? = null
    )

    private val promptSuffix = """
        Analyze the log text and identify regular expressions that can parse individual log messages.
        For each pattern:
        1. Create a regex that captures important fields as named groups
        2. Capture names should use only letters in camelCase

        3. Ensure the pattern is specific enough to avoid false matches
        4. Describe what type of log message the pattern identifies

        Return only the regex patterns with descriptions, no matches or analysis.
    """.trimIndent()

    fun generatePatterns(api: ChatClientInterface, text: String): List<LogDataParsingModel.PatternData> {
        val parser = ParsedActor(
            resultClass = PatternResponse::class.java,
            exampleInstance = PatternResponse(),
            prompt = "",
            parsingModel = parsingModel,
            temperature = temperature,
            model = parsingModel,
        ).getParser(api, promptSuffix = promptSuffix)

        return parser.apply(text).patterns ?: emptyList()
    }
}