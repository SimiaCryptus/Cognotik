package com.simiacryptus.cognotik.actors

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MultiExeption
import com.simiacryptus.cognotik.util.toContentList
import java.util.function.Function

open class ParsedAgent<T : Any>(
  var resultClass: Class<T>? = null,
  val exampleInstance: T? = resultClass?.getConstructor()?.newInstance(),
  prompt: String = "",
  name: String? = resultClass?.simpleName,
  model: ChatInterface,
  temperature: Double = 0.3,
  val parsingChatter: ChatInterface,
  val deserializerRetries: Int = 2,
  open val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "aicoder.actions"
    ) {
        override val includeMethods: Boolean get() = false
    },
  var parserPrompt: String? = null,
) : BaseAgent<List<String>, ParsedResponse<T>>(
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
        ModelSchema.ChatMessage(
            role = ModelSchema.Role.system,
            content = prompt.toContentList()
        ),
    ) + questions.map {
        ModelSchema.ChatMessage(
            role = ModelSchema.Role.user,
            content = it.toContentList()
        )
    }

    private inner class ParsedResponseImpl(vararg messages: ModelSchema.ChatMessage) :
        ParsedResponse<T>(resultClass!!) {
        override val text =
            response(*messages).choices.firstOrNull()?.message?.content
                ?: throw RuntimeException("No response")
        private val _obj: T by lazy { getParser(parserPrompt).apply(text) }
        override val obj get() = _obj
    }

    fun getParser(promptSuffix: String? = null) = Function<String, T> { input ->
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
                val content = parsingChatter.copy(temperature = 0.0).chat(
                    listOf(
                        ModelSchema.ChatMessage(role = ModelSchema.Role.system, content = prompt.toContentList()),
                        ModelSchema.ChatMessage(
                            role = ModelSchema.Role.user,
                            content = "The user message to parse:\n\n$input".toContentList()
                        ),
                    )
                ).choices.first().message?.content
                var contentUnwrapped = content?.trim() ?: throw RuntimeException("No response")

                if (!contentUnwrapped.startsWith("{") && !contentUnwrapped.startsWith("```")) {
                    val start = contentUnwrapped.indexOf("{").coerceAtMost(contentUnwrapped.indexOf("```"))
                    val end =
                        contentUnwrapped.lastIndexOf("}").coerceAtLeast(contentUnwrapped.lastIndexOf("```") + 2) + 1
                    if (start < end && start >= 0) contentUnwrapped = contentUnwrapped.substring(start, end)
                }

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
                        throw RuntimeException(
                            "Failed to parse response: ${
                                it.lineSequence()
                                    .map {
                                        when {
                                            it.isBlank() -> {
                                                when {
                                                    it.length < "  ".length -> "  "
                                                    else -> it
                                                }
                                            }

                                            else -> "  " + it
                                        }
                                    }
                                    .joinToString("\n")
                            }", e)
                    }
                }
            } catch (e: Exception) {
                log.info("Failed to parse response", e)
                exceptions.add(e)
            }
        }
        throw MultiExeption(exceptions)
    }

    override fun respond(input: List<String>, vararg messages: ModelSchema.ChatMessage): ParsedResponse<T> =
        try {
            ParsedResponseImpl(*messages)
        } catch (e: Exception) {
            log.info("Failed to parse response", e)
            throw e
        }

    override fun withModel(model: ChatInterface): ParsedAgent<T> = ParsedAgent(
        resultClass = resultClass,
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
        parsingChatter = parsingChatter,
    )

    companion object {
        private val log = LoggerFactory.getLogger(ParsedAgent::class.java)
    }

}