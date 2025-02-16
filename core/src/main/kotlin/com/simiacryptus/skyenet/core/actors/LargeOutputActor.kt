package com.simiacryptus.skyenet.core.actors

import com.google.common.base.Strings
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.models.TextModel
import com.simiacryptus.jopenai.util.ClientUtil.toChatMessage
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.skyenet.core.util.IterativePatchUtil.patchFormatPrompt
import com.simiacryptus.skyenet.core.util.SimpleDiffApplier
import org.slf4j.LoggerFactory

/**
 * Data class representing a refinement step with a name and prompt
 */
data class RefinementStep(
  val name: String,
  val prompt: String,
  val perSection: Boolean = false,
  val iterations: Int = 1
)

/**
 * An actor that handles large outputs by using recursive replacement.
 * It instructs the initial LLM call to use ellipsis expressions to manage result size,
 * then recursively expands the result by searching for the pattern and making additional LLM calls.
 */
class LargeOutputActor(
  prompt: String = """
        You are a long-form content writer. You have been tasked with writing a comprehensive guide on a topic.
        1. Break down the content into logical sections using markdown formatting and headers.
        2. To support large content generation, use markers to indicate where content needs expansion.
        3. Expansion markers should use a line formatted like '...sectionName...' to indicate where detailed content should be inserted.
        4. Use descriptive and unique section names that reflect the content expected in that section.
        5. For the initial iteration, provide a high level document structure with a few expansion markers. Each '...sectionName...' will be expanded in subsequent iterations.
    """.trimIndent(),
  name: String? = null,
  model: TextModel = OpenAIModels.GPT4o,
  temperature: Double = 0.3,
  private val maxIterations: Int = 3,
  private val namedEllipsisPattern: Regex = Regex("""\.\.\.(?<sectionName>[\w\s\-_]+?)\.\.\."""),
  private val refinementSteps: List<RefinementStep> = listOf(
    RefinementStep(
      "RedundancyReviewer",
      "Review the text to identify and remove instances of redundant text or unnecessary framing language. Provide changes as a diff.",
      perSection = false,
      iterations = 2
    ),
    RefinementStep(
      "NarrativeEnhancer",
      "Review and enhance the text's narrative quality and flow. Provide changes as a diff.",
      perSection = true,
      iterations = 1
    ),
    RefinementStep(
      "StyleConsistencyReviewer",
      "Review and improve consistency in style, tone and terminology. Provide changes as a diff.",
      perSection = false,
      iterations = 1
    ),
  )
) : BaseActor<List<String>, String>(
  prompt = prompt, name = name, model = model, temperature = temperature
) {

  override fun chatMessages(questions: List<String>): Array<ApiModel.ChatMessage> {
    val systemMessage = ApiModel.ChatMessage(
      role = ApiModel.Role.system, content = prompt.toContentList()
    )
    val userMessages = questions.map {
      ApiModel.ChatMessage(
        role = ApiModel.Role.user, content = it.toContentList()
      )
    }
    return arrayOf(systemMessage) + userMessages
  }

  override fun respond(input: List<String>, api: API, vararg messages: ApiModel.ChatMessage): String {
    var accumulatedResponse = ""
    var iterations = 0
    if (input.isEmpty()) return ""
    val expandedSections = mutableSetOf<String>()
    
    while (iterations < maxIterations) {
      if (accumulatedResponse.isEmpty()) {
        accumulatedResponse = response(input = messages, api = api).choices.first().message?.content?.trim() ?: throw RuntimeException("No response from LLM")
      }
      val matches = namedEllipsisPattern.findAll(accumulatedResponse).toMutableList()
      if (matches.isEmpty()) break
      val pairs = matches.mapNotNull { matchResult ->
        val nextSection = matchResult.groups["sectionName"]?.value ?: return@mapNotNull null
        if (expandedSections.contains(nextSection)) return@mapNotNull null
        expandedSections.add(nextSection)
        val contextLines = 100
        val contextChars = 10000
        Pair(
          matchResult, response(
            *(listOf(
              """
              You are a long-form content writer. You have been tasked with writing a comprehensive guide on a topic by filling in a detail section.
              1. Break down the content into logical sections using markdown formatting and headers.
              2. To support large content generation, use markers to indicate where content needs expansion.
              3. Expansion markers should use a line formatted like '...sectionName...' to indicate where detailed content should be inserted.
              4. Use descriptive and unique section names that reflect the content expected in that section.
              """.trimIndent().toChatMessage(ApiModel.Role.system)
            ) + messages.toList().drop(1) + listOf(
              ApiModel.ChatMessage(
                role = ApiModel.Role.user,
                content = (buildString {
                  appendLine("Previous context:\n\n```")
                  appendLine(accumulatedResponse.substring(0, matchResult.range.first)
                      .lines().takeLast(contextLines).joinToString { "  $it" }.takeLast(contextChars))
                  append("```\n\nContinue the section '")
                  append(nextSection)
                  appendLine("'\nMake sure the response flows naturally with the existing content.\nIt should end so that it matches the next section, provided below:\n\n```")
                  appendLine(accumulatedResponse.substring(matchResult.range.last)
                    .lines().take(contextLines).joinToString { "  $it" }.take(contextChars))
                  appendLine("```")
                }).toContentList()
              )
            )).toTypedArray(), api = api
          )
        )
      }
      accumulatedResponse = pairs.reversed().fold(accumulatedResponse) { acc, (match, response) ->
        val original = response.choices.first().message?.content?.trim() ?: ""
        var replacement = original
        if (replacement.isEmpty()) return acc
        if (replacement.startsWith("```")) {
          replacement = replacement.lines().drop(1).reversed().dropWhile { !it.startsWith("```") }.drop(1).reversed().joinToString("\n")
        }
        val prefix = acc.substring(0, match.range.first)
        val suffix = acc.substring(match.range.last)
        val commonPrefix = Strings.commonPrefix(prefix, replacement)
        if (commonPrefix.isNotBlank() && commonPrefix.contains('\n')) replacement = replacement.substring(commonPrefix.length)
        val largestCommonSubstring = largestCommonSubstring(replacement, suffix)
        if (largestCommonSubstring.isNotBlank()) replacement = replacement.substring(0, replacement.indexOf(largestCommonSubstring))
        val replaceRange = acc.replaceRange(match.range, replacement)
        replaceRange
      }
      iterations++
    }
    // Apply refinement steps after content expansion
    val diffApplier = SimpleDiffApplier()
    refinementSteps.forEach { step ->
      try {
        repeat(step.iterations) { iteration ->
          if (step.perSection) {
            // Split content into H1 sections
            val sections = splitIntoH1Sections(accumulatedResponse)
            var modifiedContent = ""
            sections.forEach { (header, content) ->
              val sectionResponse = response(
                ApiModel.ChatMessage(
                  role = ApiModel.Role.system,
                  content = (step.prompt + "\n\n" + patchFormatPrompt).toContentList()
                ),
                ApiModel.ChatMessage(
                  role = ApiModel.Role.user,
                  content = content.toContentList()
                ),
                api = api
              )
              val modifiedSection = diffApplier.apply(content, sectionResponse.choices.first().message?.content ?: "")
              modifiedContent += if (header.isNotEmpty()) "$header\n$modifiedSection\n\n" else modifiedSection
            }
            accumulatedResponse = modifiedContent.trim()
          } else {
            // Apply refinement to entire content
            val refinementResponse = response(
              ApiModel.ChatMessage(
                role = ApiModel.Role.system,
                content = (step.prompt + "\n\n" + patchFormatPrompt).toContentList()
              ),
              ApiModel.ChatMessage(
                role = ApiModel.Role.user,
                content = accumulatedResponse.toContentList()
              ),
              api = api
            )
            accumulatedResponse = diffApplier.apply(
              accumulatedResponse,
              refinementResponse.choices.first().message?.content ?: ""
            )
          }
        }
      } catch (e: Exception) {
        log.warn("Error applying ${step.name} refinement", e)
      }
    }
    return accumulatedResponse
  }

  override fun withModel(model: ChatModel): LargeOutputActor {
    return LargeOutputActor(
      prompt = this.prompt,
      name = this.name,
      model = model,
      temperature = this.temperature,
      maxIterations = this.maxIterations,
      namedEllipsisPattern = this.namedEllipsisPattern,
      refinementSteps = this.refinementSteps
    )
  }
  companion object {
    private val log = LoggerFactory.getLogger(LargeOutputActor::class.java)

    /**
     * Splits content into sections based on H1 headers (# Header)
     * Returns a list of pairs containing the header and its content
     */
    private fun splitIntoH1Sections(content: String): List<Pair<String, String>> {
      val h1Pattern = Regex("^#\\s+.*$", RegexOption.MULTILINE)
      val matches = h1Pattern.findAll(content)
      val positions = matches.map { it.range.first }.toList()

      // Handle case where content has no headers
      if (positions.isEmpty()) {
        return listOf(Pair("", content.trim()))
      }

      return positions.mapIndexed { index, start ->
        val headerEnd = content.indexOf('\n', start).takeIf { it != -1 } ?: content.length
        val header = content.substring(start, headerEnd)
        val contentStart = headerEnd + 1
        val contentEnd = if (index < positions.size - 1) positions[index + 1] else content.length
        Pair(header, content.substring(contentStart, contentEnd).trim())
      }
    }
  }
}


fun largestCommonSubstring(a: String, b: String): String {
  if (a.isEmpty() || b.isEmpty()) return ""
  if (a == b) return a
  val lengths = Array(a.length + 1) { IntArray(b.length + 1) }
  var z = 0
  var ret = ""
  val maxWindow = 10000
  val aLen = minOf(a.length, maxWindow)
  val bLen = minOf(b.length, maxWindow)
  for (i in 0 until aLen) {
    for (j in 0 until bLen) {
      if (a[i] == b[j]) {
        lengths[i + 1][j + 1] = lengths[i][j] + 1
        val len = lengths[i + 1][j + 1]
        if (len > z) {
          z = len
          ret = a.substring(i - z + 1, i + 1)
        }
      }
    }
  }
  return ret
}