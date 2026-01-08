package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.*
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
    val validation: Boolean = true,
    open val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "aicoder.actions"
    ) {
        override val includeMethods: Boolean get() = false
    },
    var parserPrompt: String? = null,
    val singleStage: Boolean = false,
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

    override fun chatMessages(questions: List<String>): Array<ModelSchema.ChatMessage> {
        val systemPrompt = if (singleStage) {
            describer.coverMethods = false
            val describe = if (null == resultClass) "" else {
                describer.describe(resultClass!!)
            }
            val jsonInstructions = """
                |
                |The response must be a JSON object conforming to the following YAML schema:
                |
                |```yaml
                |${describe.replace("\n", "\n  ")}
                |```
                |
                |Example of a valid response:
                |```json
                |${JsonUtil.toJson(exampleInstance!!)}
                |```
                |${parserPrompt?.let { "\n$it" } ?: ""}
                |
                |Return ONLY the JSON object.
            """.trimMargin()
            if (prompt.isBlank()) jsonInstructions else "$prompt\n$jsonInstructions"
        } else {
            prompt
        }
        return arrayOf(
            ModelSchema.ChatMessage(
                role = ModelSchema.Role.system,
                content = systemPrompt.toContentList()
            ),
        ) + questions.map {
            ModelSchema.ChatMessage(
                role = ModelSchema.Role.user,
                content = it.toContentList()
            )
        }
    }

    private inner class ParsedResponseImpl(vararg messages: ModelSchema.ChatMessage) :
        ParsedResponse<T>(resultClass!!) {
        override val text =
            response(*messages).choices.firstOrNull()?.message?.content
                ?: throw RuntimeException("No response")
        private val _obj: T by lazy {
            if (singleStage) {
                try {
                    parse(text)
                } catch (e: Exception) {
                    log.info("Failed to parse single-stage response, falling back to parser agent", e)
                    getParser(parserPrompt).apply(text)
                }
            } else {
                getParser(parserPrompt).apply(text)
            }
        }
        override val obj get() = _obj
    }

    fun getParser(promptSuffix: String? = null) = Function<String, T> { input ->
        describer.coverMethods = false
        val describe = if (null == resultClass) "" else {
            describer.describe(resultClass!!)
        }
        val exceptions = mutableListOf<Exception>()
        val prompt = """
            |Extract information from the user's message and format it as a JSON object.
            |
            |Schema (YAML):
            |```yaml
            |${
                describe.replace(
                    "\n",
                    "\n  "
                )
            }
            |```
            |
            |Example Output:
            |```json
            |${JsonUtil.toJson(exampleInstance!!)}
            |```${promptSuffix?.let { "\n$it" } ?: ""}
            |
            |Respond with the JSON object wrapped in a ```json code block.
        """.trimMargin()
        for (i in 0 until deserializerRetries) {
            try {
                val content = parsingChatter.chat(
                    listOf(
                        ModelSchema.ChatMessage(role = ModelSchema.Role.system, content = prompt.toContentList()),
                        ModelSchema.ChatMessage(
                            role = ModelSchema.Role.user,
                            content = "The user message to parse:\n\n$input".toContentList()
                        ),
                    )
                ).choices.first().message?.content


                val contentUnwrapped = content?.trim() ?: throw RuntimeException("No response")
                return@Function parse(contentUnwrapped)
            } catch (e: Exception) {
                log.info("Failed to parse response", e)
                exceptions.add(e)
            }
        }
        throw MultiExeption(exceptions)
    }

    private fun parse(content: String): T {
        var contentUnwrapped = content.trim()
        if (contentUnwrapped.isEmpty()) throw RuntimeException("Empty response from model")
        if (!contentUnwrapped.startsWith("{") && !contentUnwrapped.startsWith("[")) {
            val codeStart = contentUnwrapped.indexOf("```json")
            val codeEnd = contentUnwrapped.lastIndexOf("```")
            
            if (codeStart != -1 && codeEnd > codeStart + 7) {
                contentUnwrapped = contentUnwrapped.substring(codeStart + 7, codeEnd).trim()
            } else {
                val jsonStart = contentUnwrapped.indexOf("{").let { if (it == -1) contentUnwrapped.indexOf("[") else it }
                val jsonEnd = contentUnwrapped.lastIndexOf("}").let { if (it == -1) contentUnwrapped.lastIndexOf("]") else it }
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    contentUnwrapped = contentUnwrapped.substring(jsonStart, jsonEnd + 1).trim()
                }
            }
        }
        return contentUnwrapped.let {
            try {
                val fromJson = JsonUtil.fromJson<T>(
                    it, resultClass
                        ?: throw RuntimeException("Result class undefined")
                )
                if (validation) {
                    if (fromJson is ValidatedObject) {
                        val validate = fromJson.validate()
                        if (null != validate) {
                            throw RuntimeException("Validation failed: $validate")
                        }
                    }
                }
                fromJson
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
                    }", e
                )
            }
        }
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
        deserializerRetries = deserializerRetries,
        validation = validation,
        describer = describer,
        parserPrompt = parserPrompt,
        singleStage = singleStage,
    )

    companion object {
        private val log = LoggerFactory.getLogger(ParsedAgent::class.java)
    }

}


inline fun <reified T : Any> Any.parserCast(
    model: ChatInterface, describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "aicoder.actions"
    ) {
        override val includeMethods: Boolean get() = false
    }
): T = ParsedAgent(
    prompt = "",
    resultClass = T::class.java,
    model = model,
    parsingChatter = model,
    describer = describer
).getParser().apply(this.toJson())