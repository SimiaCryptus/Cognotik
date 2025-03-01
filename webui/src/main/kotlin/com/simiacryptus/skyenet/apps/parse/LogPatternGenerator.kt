package com.simiacryptus.skyenet.apps.parse

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.core.actors.ParsedActor

class LogPatternGenerator(
  private val parsingModel: ChatModel,
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

  fun generatePatterns(api: API, text: String): List<LogDataParsingModel.PatternData> {
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