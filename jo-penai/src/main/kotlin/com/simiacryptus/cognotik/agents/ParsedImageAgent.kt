package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.toContentList

open class ParsedImageAgent<T : Any>(
    var resultClass: Class<T>? = null,
    val exampleInstance: T? = resultClass?.getConstructor()?.newInstance(),
    prompt: String = "",
    name: String? = resultClass?.simpleName,
    model: ChatInterface,
    temperature: Double = 0.3,
    val validation: Boolean = true,
    open val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "aicoder.actions"
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
            """.trimIndent().toContentList()
        ),
        ModelSchema.ChatMessage(
            role = ModelSchema.Role.user,
            content = questions.flatMap { question ->
                listOf(
                    ModelSchema.ContentPart(
                        text = question.text,
                        image_url = question.image?.let { "data:image/png;base64,${it.encodeImageToBase64()}" },
                    )
                )
            }
        )
    )

    private inner class ParsedResponseImpl(vararg messages: ModelSchema.ChatMessage) :
        ParsedResponse<T>(resultClass!!) {
        override val text =
            response(*messages).choices.firstOrNull()?.message?.content
                ?: throw RuntimeException("No response")
        private val _obj: T by lazy { JsonUtil.fromJson(unwrap(text), resultClass!!) }

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

        override val obj get() = _obj
    }

    override fun respond(input: List<ImageAndText>, vararg messages: ModelSchema.ChatMessage): ParsedResponse<T> =
        try {
            ParsedResponseImpl(*messages)
        } catch (e: Exception) {
            log.info("Failed to parse response", e)
            throw e
        }

    override fun withModel(model: ChatInterface): ParsedImageAgent<T> = ParsedImageAgent(
        resultClass = resultClass,
        exampleInstance = exampleInstance,
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
        validation = validation,
        describer = describer,
    )

    companion object {
        private val log = LoggerFactory.getLogger(ParsedImageAgent::class.java)
    }
}