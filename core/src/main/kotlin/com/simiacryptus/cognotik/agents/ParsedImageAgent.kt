package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.toContentList
import org.slf4j.LoggerFactory

open class ParsedImageAgent<T : Any>(
  var resultClass: Class<T>? = null,
  val exampleInstance: T? = resultClass?.getConstructor()?.newInstance(),
  prompt: String = "",
  name: String? = resultClass?.simpleName,
  model: ChatInterface,
  temperature: Double = 0.3,
  val deserializerRetries: Int = 2,
  val validation: Boolean = true,
  open val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "cognotik.actions"
  ) {
    override val includeMethods: Boolean get() = false
  },
) : BaseAgent<List<ImageAndText>, ParsedResponse<T>>(
  prompt = prompt,
  name = name,
  model = model,
  temperature = temperature,
) {
  init {
    requireNotNull(resultClass) {
      "Result class is required"
    }
  }

  override fun chatMessages(questions: List<ImageAndText>) = arrayOf(
    ModelSchema.ChatMessage(
      role = ModelSchema.Role.system,
      content = """
$prompt

Response should be in JSON format:
${describer.describe(resultClass!!)}
            """.toContentList()
    ),
    ModelSchema.ChatMessage(
      role = ModelSchema.Role.user,
      content = questions.flatMap { question ->
        listOf(
          ModelSchema.ContentPart(
            text = question.text,
          ).apply {
              image = question.image
          }
        )
      }
    )
  )

  private inner class ParsedResponseImpl(vararg messages: ModelSchema.ChatMessage) :
    ParsedResponse<T>(resultClass!!) {
    private val t: Pair<String, T> by lazy {
      var lastException: Exception? = null
      var resultText: String? = null
      var resultObj: T? = null
      for (i in 0..deserializerRetries) {
        try {
          val responseContent = model.chat(
            ChatRequest(
              model = model.model.modelId,
              messages = messages.toList(),
              temperature = temperature,
              audio = model.audio,
            )
          ).choices.firstOrNull()?.message?.content
            ?: throw RuntimeException("No response")
          resultText = responseContent
          resultObj = JsonUtil.fromJson<T>(unwrap(responseContent), resultClass!!)
          break
        } catch (e: Exception) {
          lastException = e
          log.info("Failed to parse response", e)
        }
      }
      if (resultObj == null) throw lastException!!
      Pair(resultText!!, resultObj)
    }
    override val text: String get() = t.first
    override val obj: T get() = t.second

    private fun unwrap(text: String): String {
      val trimmed = text.trim()
      return if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
        trimmed.removePrefix("```json").removeSuffix("```").trim()
      } else if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
        trimmed.removePrefix("```").removeSuffix("```").trim()
      } else {
        trimmed
      }
    }

  }

  override fun respond(input: List<ImageAndText>, vararg messages: ModelSchema.ChatMessage): ParsedResponse<T> =
    try {
      ParsedResponseImpl(*messages)
    } catch (e: Exception) {
      log.info("Failed to parse response", e)
      throw e
    }


  companion object {
    private val log = LoggerFactory.getLogger(ParsedImageAgent::class.java)
  }
}