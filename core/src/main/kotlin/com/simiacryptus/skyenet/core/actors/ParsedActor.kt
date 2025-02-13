package com.simiacryptus.skyenet.core.actors

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.models.TextModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.skyenet.core.util.MultiExeption
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.util.function.Function

open class ParsedActor<T : Any>(
  var resultClass: Class<T>? = null,
  val exampleInstance: T? = resultClass?.getConstructor()?.newInstance(),
  prompt: String = "",
  name: String? = resultClass?.simpleName,
  model: TextModel = OpenAIModels.GPT4o,
  temperature: Double = 0.3,
  val parsingModel: TextModel = OpenAIModels.GPT4oMini,
  val deserializerRetries: Int = 2,
  open val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "aicoder.actions"
  ) {
    override val includeMethods: Boolean get() = false
  },
  var parserPrompt: String? = null,
) : BaseActor<List<String>, ParsedResponse<T>>(
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

  override fun chatMessages(questions: List<String>) = arrayOf(
    ApiModel.ChatMessage(
      role = ApiModel.Role.system,
      content = prompt.toContentList()
    ),
  ) + questions.map {
    ApiModel.ChatMessage(
      role = ApiModel.Role.user,
      content = it.toContentList()
    )
  }

  private inner class ParsedResponseImpl(api: API, vararg messages: ApiModel.ChatMessage) :
    ParsedResponse<T>(resultClass!!) {
    override val text =
      response(*messages, api = api).choices.firstOrNull()?.message?.content ?: throw RuntimeException("No response")
    private val _obj: T by lazy { getParser(api, parserPrompt).apply(text) }
    override val obj get() = _obj
  }

  fun getParser(api: API, promptSuffix: String? = null) = Function<String, T> { input ->
    describer.coverMethods = false
    val describe = if (null == resultClass) "" else {
      describer.describe(resultClass!!)
    }
    val exceptions = mutableListOf<Exception>()
    val prompt = "Parse the user's message into a json object described by:\n\n```yaml\n${
      describe.replace(
        "\n",
        "\n  "
      )
    }\n```\n\nThis is an example output:\n```json\n${JsonUtil.toJson(exampleInstance!!)}\n```${promptSuffix?.let { "\n$it" } ?: ""}"
    for (i in 0 until deserializerRetries) {
      try {
        val content = (api as ChatClient).chat(
          ApiModel.ChatRequest(
            messages = listOf(
              ApiModel.ChatMessage(role = ApiModel.Role.system, content = prompt.toContentList()),
              ApiModel.ChatMessage(
                role = ApiModel.Role.user,
                content = "The user message to parse:\n\n$input".toContentList()
              ),
            ),
            temperature = temperature,
            model = parsingModel.modelName,
          ),
          model = parsingModel,
        ).choices.first().message?.content
        var contentUnwrapped = content?.trim() ?: throw RuntimeException("No response")

        // If Plaintext is found before the { or ```, strip it
        if (!contentUnwrapped.startsWith("{") && !contentUnwrapped.startsWith("```")) {
          val start = contentUnwrapped.indexOf("{").coerceAtMost(contentUnwrapped.indexOf("```"))
          val end =
            contentUnwrapped.lastIndexOf("}").coerceAtLeast(contentUnwrapped.lastIndexOf("```") + 2) + 1
          if (start < end && start >= 0) contentUnwrapped = contentUnwrapped.substring(start, end)
        }

        // if input is wrapped in a ```json block, remove the block
        if (contentUnwrapped.startsWith("```json")) {
          val endIndex = contentUnwrapped.lastIndexOf("```")
          if (endIndex > 7) {
            contentUnwrapped = contentUnwrapped.substring(7, endIndex)
          } else {
            throw RuntimeException(
              "Failed to parse response: ${
                contentUnwrapped.replace(
                  "\n",
                  "\n  "
                )
              }"
            )
          }
        }

        contentUnwrapped.let {
          try {
            return@Function JsonUtil.fromJson<T>(
              it, resultClass
                ?: throw RuntimeException("Result class undefined")
            )
          } catch (e: Exception) {
            throw RuntimeException("Failed to parse response: ${it.replace("\n", "\n  ")}", e)
          }
        }
      } catch (e: Exception) {
        log.info("Failed to parse response", e)
        exceptions.add(e)
      }
    }
    throw MultiExeption(exceptions)
  }

  override fun respond(input: List<String>, api: API, vararg messages: ApiModel.ChatMessage): ParsedResponse<T> = try {
    ParsedResponseImpl(api, *messages)
  } catch (e: Exception) {
    log.info("Failed to parse response", e)
    throw e
  }

  override fun withModel(model: ChatModel): ParsedActor<T> = ParsedActor(
    resultClass = resultClass,
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
    parsingModel = parsingModel,
  )

  companion object {
    private val log = LoggerFactory.getLogger(ParsedActor::class.java)
  }


}