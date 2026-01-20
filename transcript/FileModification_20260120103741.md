# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/BaseAgent.kt

```
package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema

abstract class BaseAgent<I, R>(
    open val prompt: String,
    val name: String? = null,
    val model: ChatInterface,
    val temperature: Double = 0.3,
) {
    abstract fun respond(
        input: I,
        vararg messages: ModelSchema.ChatMessage = this.chatMessages(input),
    ): R

    protected open fun response(
        vararg input: ModelSchema.ChatMessage,
        model: AIModel = this.model.modelType
    ): ModelSchema.ChatResponse =
        this.model.chat(input.toList())

    open fun answer(input: I): R = respond(input = input, *chatMessages(input))

    abstract fun chatMessages(questions: I): Array<ModelSchema.ChatMessage>
    abstract fun withModel(model: ChatInterface): BaseAgent<I, R>
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ChatAgent.kt

```
package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.toContentList

open class ChatAgent(
    prompt: String,
    name: String? = null,
    model: ChatInterface,
    temperature: Double = 0.3,
) : BaseAgent<List<String>, String>(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
) {

    override fun respond(input: List<String>, vararg messages: ModelSchema.ChatMessage): String =
        response(*messages).choices.first().message?.content ?: throw RuntimeException("No response")

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

    override fun withModel(model: ChatInterface): ChatAgent = ChatAgent(
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
    )
}

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/CodeAgent.kt

```
package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.OutputInterceptor
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.util.FailedToImplementException
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.toContentList
import javax.script.ScriptException
import kotlin.reflect.KClass

private const val TT = "`" + "`" + "`"
typealias CodeInterceptor = (String) -> String

open class CodeAgent(
    val codeRuntime: CodeRuntime,
    val codeRuntimeClass: KClass<out CodeRuntime> = codeRuntime::class,
    val symbols: Map<String, Any> = mapOf(),
    val describer: TypeDescriber = AbbrevWhitelistYamlDescriber(
        "com.simiacryptus"
    ),
    name: String? = codeRuntimeClass.simpleName,
    val details: String? = null,
    model: ChatInterface,
    val fallbackModel: ChatInterface? = null,
    temperature: Double = 0.1,
    val runtimeSymbols: Map<String, Any> = mapOf(),
    var codeInterceptor: CodeInterceptor = { it }
) : BaseAgent<CodeAgent.CodeRequest, CodeAgent.CodeResult>(
    prompt = "",
    name = name,
    model = model,
    temperature = temperature,
) {
//    val codeRuntime: CodeRuntime
//        get() = codeRuntimeClass.java.getConstructor(Map::class.java).newInstance(symbols + runtimeSymbols)

    data class CodeRequest(
        val messages: List<Pair<String, Role>>,
        val codePrefix: String = "",
        val autoEvaluate: Boolean = false,
        val fixIterations: Int = 1,
        val fixRetries: Int = 1,
    )

    interface CodeResult {
        enum class Status {
            Coding, Correcting, Success, Failure
        }

        val code: String
        val status: Status
        val result: ExecutionResult
        val renderedResponse: String?
    }

    data class ExecutionResult(
        val resultValue: String,
        val resultOutput: String
    )

    var evalFormat = true
    override val prompt: String
        get() {
            val formatInstructions =
                if (evalFormat) """Code should be structured as appropriately parameterized function(s)

 with the final line invoking the function with the appropriate request parameters.""" else ""
            val symbols = this.codeRuntime.symbols
            return if (symbols.isNotEmpty()) {
                """
You are a coding assistant allows users actions to be enacted using $language and the script context.
Your role is to translate natural language instructions into code as well as interpret the results and converse with the user.
Use $TT code blocks labeled with $language where appropriate. (i.e. ${TT}$language)
Each response should have EXACTLY ONE code block. Do not use inline blocks.
$formatInstructions

Defined symbols include ${symbols.keys.joinToString(", ")} described below:

$TT${this.describer.markupLanguage}
${this.apiDescription}
${TT}

THESE VARIABLES ARE READ-ONLY: ${symbols.keys.joinToString(", ")}
They are already defined for you.

${details ?: ""}
""".trim()
            } else """
You are a coding assistant allowing users actions to be enacted using $language and the script context.
Your role is to translate natural language instructions into code as well as interpret the results and converse with the user.
Use $TT code blocks labeled with $language where appropriate. (i.e. ${TT}$language)
Each response should have EXACTLY ONE code block. Do not use inline blocks.
$formatInstructions

${details ?: ""}
""".trim()
        }

    open val apiDescription: String
        get() = this.symbols.map { (name, utilityObj) ->
            val describe = this.describer.describe(utilityObj.javaClass, utilityObj)
            log.info("Describing $name (${utilityObj.javaClass}) in ${describe.length} characters")
            "$name:\n    ${describe.indent("    ")}"
        }.joinToString("\n")

    val language: String by lazy { codeRuntime.language }

    override fun chatMessages(questions: CodeRequest): Array<ChatMessage> {
        var chatMessages = arrayOf(
            ChatMessage(
                role = Role.system,
                content = prompt.toContentList()
            ),
        ) + questions.messages.map {
            ChatMessage(
                role = it.second,
                content = it.first.toContentList()
            )
        }
        if (questions.codePrefix.isNotBlank()) {
            chatMessages = (chatMessages.dropLast(1) + listOf(
                ChatMessage(Role.assistant, "Code Prefix:\n$TT\n${questions.codePrefix}\n${TT}".toContentList())
            ) + chatMessages.last()).toTypedArray<ChatMessage>()
        }
        return chatMessages

    }

    override fun respond(
        input: CodeRequest,
        vararg messages: ChatMessage,
    ): CodeResult {
        var result = CodeResultImpl(
            *messages,
            input = input,
        )
        if (!input.autoEvaluate) return result
        for (i in 0..input.fixIterations) try {
            require(result.result.resultValue.length > -1)
            return result
        } catch (ex: Throwable) {
            if (i == input.fixIterations) {
                log.info(
                    "Failed to implement ${
                        messages.map { it.content?.joinToString("\n") { it.text ?: "" } }.joinToString("\n")
                    }"
                )
                throw ex
            }
            val respondWithCode = fixCommand(result.code, ex, model = model, *messages)
            val blocks = extractTextBlocks(respondWithCode)
            val renderedResponse = getRenderedResponse(blocks)
            val codedInstruction = codeInterceptor(getCode(language, blocks))
            log.debug(
                "Response: \n\t${
                    renderedResponse.lineSequence()
                        .map {
                            when {
                                it.isBlank() -> {
                                    when {
                                        it.length < "\t".length -> "\t"
                                        else -> it
                                    }
                                }

                                else -> "\t" + it
                            }
                        }
                        .joinToString("\n")
                }")
            log.debug(
                "New Code: \n\t${
                    codedInstruction.lineSequence()
                        .map {
                            when {
                                it.isBlank() -> {
                                    when {
                                        it.length < "\t".length -> "\t"
                                        else -> it
                                    }
                                }

                                else -> "\t" + it
                            }
                        }
                        .joinToString("\n")
                }")
            result = CodeResultImpl(
                *messages,
                input = input,
                givenCode = codedInstruction,
                givenResponse = renderedResponse
            )
        }
        throw IllegalStateException()
    }

    open fun execute(prefix: String, code: String): ExecutionResult {

        log.debug("Running $code")
        OutputInterceptor.clearGlobalOutput()
        val result = try {
            codeRuntime.run((prefix + "\n" + codeInterceptor(code)).sortCode())
        } catch (e: Exception) {
            when {
                e is FailedToImplementException -> throw e
                e is ScriptException -> throw FailedToImplementException(
                    cause = e,
                    message = errorMessage(e, code),
                    language = language,
                    code = code,
                    prefix = prefix,
                )

                e.cause is ScriptException -> throw FailedToImplementException(
                    cause = e,
                    message = errorMessage(e.cause!! as ScriptException, code),
                    language = language,
                    code = code,
                    prefix = prefix,
                )

                else -> throw e
            }
        }
        log.debug("Result: {}", result)

        val executionResult = ExecutionResult(result.toString(), OutputInterceptor.getThreadOutput())
        OutputInterceptor.clearThreadOutput()
        return executionResult
    }

    inner class CodeResultImpl(
        vararg val messages: ChatMessage,
        private val input: CodeRequest,
        private val givenCode: String? = null,
        private val givenResponse: String? = null,
    ) : CodeResult {
        private val implementation by lazy {
            if (!givenCode.isNullOrBlank() && !givenResponse.isNullOrBlank()) (givenCode to givenResponse) else try {
                implement(model)
            } catch (ex: FailedToImplementException) {
                val fallbackModel = fallbackModel
                if (fallbackModel != model && null != fallbackModel) {
                    try {
                        implement(fallbackModel)
                    } catch (ex: FailedToImplementException) {
                        log.debug("Failed to implement ${messages.map { it.content }.joinToString("\n")}")
                        _status = CodeResult.Status.Failure
                        throw ex
                    }
                } else {
                    log.debug("Failed to implement ${messages.map { it.content }.joinToString("\n")}")
                    _status = CodeResult.Status.Failure
                    throw ex
                }
            }
        }

        private var _status = CodeResult.Status.Coding

        override val status get() = _status

        override val renderedResponse: String = givenResponse ?: implementation.second
        override val code: String = givenCode ?: implementation.first

        private fun implement(
            model: ChatInterface,
        ): Pair<String, String> {
            val request = ChatRequest(messages = ArrayList(this.messages.toList()))
            for (codingAttempt in 0..input.fixRetries) {
                try {
                    val codeBlocks = extractTextBlocks(chat(request, model))
                    val renderedResponse = getRenderedResponse(codeBlocks)
                    val codedInstruction = codeInterceptor(getCode(language, codeBlocks))
                    log.debug(
                        "Response: \n\t${
                            renderedResponse.lineSequence()
                                .map {
                                    when {
                                        it.isBlank() -> {
                                            when {
                                                it.length < "\t".length -> "\t"
                                                else -> it
                                            }
                                        }

                                        else -> "\t" + it
                                    }
                                }
                                .joinToString("\n")
                        }")
                    log.debug(
                        "New Code: \n\t${
                            codedInstruction.lineSequence()
                                .map {
                                    when {
                                        it.isBlank() -> {
                                            when {
                                                it.length < "\t".length -> "\t"
                                                else -> it
                                            }
                                        }

                                        else -> "\t" + it
                                    }
                                }
                                .joinToString("\n")
                        }")
                    var workingCode = codedInstruction
                    var workingRenderedResponse = renderedResponse
                    for (fixAttempt in 0..input.fixIterations) {
                        try {
                            val validate =
                                codeRuntime.validate((input.codePrefix + "\n" + codeInterceptor(workingCode)).sortCode())
                            if (validate != null) throw validate
                            log.debug("Validation succeeded")
                            _status = CodeResult.Status.Success
                            return workingCode to workingRenderedResponse
                        } catch (ex: Throwable) {
                            if (fixAttempt == input.fixIterations)
                                throw ex as? FailedToImplementException
                                    ?: FailedToImplementException(
                                        cause = ex,
                                        message = """
                            **ERROR**
                                          |
                            ${TT}text
                            ${ex.stackTraceToString()}
                            ${TT}
                            """.trim(),
                                        language = language,
                                        code = workingCode,
                                        prefix = input.codePrefix
                                    )
                            log.debug("Validation failed - ${ex.message}")
                            _status = CodeResult.Status.Correcting
                            val respondWithCode = fixCommand(workingCode, ex, model = model, *messages)
                            val codeBlocks = extractTextBlocks(respondWithCode)
                            workingRenderedResponse = getRenderedResponse(codeBlocks)
                            workingCode = codeInterceptor(getCode(language, codeBlocks))
                            log.debug(
                                "Response: \n\t" + workingRenderedResponse.lineSequence()
                                    .map {
                                        when {
                                            it.isBlank() -> {
                                                when {
                                                    it.length < "\t".length -> "\t"
                                                    else -> it
                                                }
                                            }

                                            else -> "\t" + it
                                        }
                                    }
                                    .joinToString("\n"))
                            log.debug(
                                "New Code: \n\t${
                                    workingCode.lineSequence()
                                        .map {
                                            when {
                                                it.isBlank() -> {
                                                    when {
                                                        it.length < "\t".length -> "\t"
                                                        else -> it
                                                    }
                                                }

                                                else -> "\t" + it
                                            }
                                        }
                                        .joinToString("\n")
                                }")
                        }
                    }
                } catch (ex: FailedToImplementException) {
                    if (codingAttempt == input.fixRetries) {
                        log.debug("Failed to implement ${messages.map { it.content }.joinToString("\n")}")
                        throw ex
                    }
                    log.debug("Retry failed to implement ${messages.map { it.content }.joinToString("\n")}")
                    _status = CodeResult.Status.Correcting
                }
            }
            throw IllegalStateException()
        }

        private val executionResult by lazy { execute(input.codePrefix, code) }

        override val result get() = executionResult
    }

    private fun fixCommand(
        previousCode: String,
        error: Throwable,
        model: ChatInterface,
        vararg promptMessages: ChatMessage
    ): String = chat(
        request = ChatRequest(
            messages = ArrayList(
                promptMessages.toList() + listOf(
                    ChatMessage(
                        Role.assistant,
                        """
$TT${language.lowercase()}
${previousCode}
${TT}
""".trim().toContentList()
                    ),
                    ChatMessage(
                        Role.system,
                        """
The previous code failed with the following error:

$TT
${error.message?.trim() ?: ""}
${TT}

Correct the code and try again.
""".trim().toContentList()
                    )
                )
            ) as ArrayList<ChatMessage>
        ),
        model = model
    )

    private fun chat(request: ChatRequest, model: ChatInterface): String {
        return model.chat(request.messages)
            .choices.first().message?.content.orEmpty().trim()
    }

    override fun withModel(model: ChatInterface): CodeAgent = CodeAgent(
        codeRuntime = codeRuntime,
//        codeRuntimeClass = codeRuntimeClass,
        symbols = symbols,
        describer = describer,
        name = name,
        details = details,
        model = model,
        fallbackModel = fallbackModel,
        temperature = temperature,
        runtimeSymbols = runtimeSymbols,
        codeInterceptor = codeInterceptor,
    )

    companion object {
        private val log = LoggerFactory.getLogger(CodeAgent::class.java)

        fun String.indent(indent: String = "  ") = this.lineSequence()
            .map {
                when {
                    it.isBlank() -> {
                        when {
                            it.length < indent.length -> indent
                            else -> it
                        }
                    }

                    // HACK: Indented end-markdown blocks are still considered code blocks
                    it.trim().startsWith("```") -> indent + "|```"

                    else -> indent + it
                }
            }
            .joinToString("\n")

        fun extractTextBlocks(response: String): List<Pair<String, String>> {
            val codeBlockRegex = Regex("(?s)$TT(.*?)\\n(.*?)${TT}")
            val languageRegex = Regex("([a-zA-Z0-9-_]+)")

            val result = mutableListOf<Pair<String, String>>()
            var startIndex = 0

            val matches = codeBlockRegex.findAll(response)
            if (matches.count() == 0) return listOf(Pair("text", response))
            for (match in matches) {

                if (startIndex < match.range.first) {
                    result.add(Pair("text", response.substring(startIndex, match.range.first)))
                }

                val languageMatch = languageRegex.find(match.groupValues[1])
                val language = languageMatch?.groupValues?.get(0) ?: "code"
                val code = match.groupValues[2]

                result.add(Pair(language, code))

                startIndex = match.range.last + 1
            }

            if (startIndex < response.length) {
                result.add(Pair("text", response.substring(startIndex)))
            }

            return result
        }

        fun getRenderedResponse(respondWithCode: List<Pair<String, String>>, defaultLanguage: String = "") =
            respondWithCode.joinToString("\n") {
                when (it.first) {
                    "code" -> "$TT$defaultLanguage\n${it.second}\n${TT}"
                    "text" -> it.second.toString()
                    else -> "$TT${it.first}\n${it.second}\n${TT}"
                }
            }

        fun getCode(language: String, textSegments: List<Pair<String, String>>): String {
            if (textSegments.size == 1) return textSegments.joinToString("\n") { it.second }
            return textSegments.joinToString("\n") {
                if (it.first.lowercase() == "code" || it.first.lowercase() == language.lowercase()) {
                    it.second
                } else {
                    ""
                }
            }
        }

        fun String.sortCode(bodyWrapper: (String) -> String = { it }): String {
            val (imports, otherCode) = this.split("\n").partition { it.trim().startsWith("import ") }
            return imports.map { it.trim() }.distinct().sorted()
                .joinToString("\n") + "\n\n" + bodyWrapper(otherCode.joinToString("\n"))
        }

        fun errorMessage(ex: ScriptException, code: String) = try {
            "${TT}text\n${ex.message ?: ""} at line ${ex.lineNumber} column ${ex.columnNumber}\n  ${
                if (ex.lineNumber > 0) code.split(
                    "\n"
                )[ex.lineNumber - 1] else ""
            }\n  ${
                if (ex.columnNumber > 0) " ".repeat(
                    ex.columnNumber - 1
                ) + "^" else ""
            }\n${TT}".trim()
        } catch (_: Exception) {
            ex.message ?: ""
        }
    }

}

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ImageAndText.kt

```
package com.simiacryptus.cognotik.agents

import java.awt.image.BufferedImage

data class ImageAndText(
    val text: String,
    val image: BufferedImage? = null,
)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ImageGenerationAgent.kt

```
package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.image.ImageClientInterface
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ImageGenerationRequest
import com.simiacryptus.cognotik.util.toChatMessage
import com.simiacryptus.cognotik.util.toContentList
import java.awt.image.BufferedImage
import java.io.Serializable
import java.net.URL
import javax.imageio.ImageIO

open class ImageGenerationAgent(
    prompt: String = "Transform the user request into an image generation prompt that the user will like",
    name: String? = null,
    textModel: ChatInterface,
    var imageModel: ImageModel?,
    val imageClient: ImageClientInterface?,
    temperature: Double = 0.3,
    val width: Int = 1024,
    val height: Int = 1024,
) : BaseAgent<List<String>, ImageAndText>(
    prompt = prompt,
    name = name,
    model = textModel,
    temperature = temperature,
) {
    override fun chatMessages(questions: List<String>) = arrayOf(
        ChatMessage(
            role = ModelSchema.Role.system,
            content = prompt.toContentList()
        ),
    ) + questions.map {
        ChatMessage(
            role = ModelSchema.Role.user,
            content = it.toContentList()
        )
    }

    open fun render(
        text: String,
        api: ImageClientInterface,
    ): BufferedImage {
        val data = api.createImage(
            ImageGenerationRequest(
                prompt = text,
                model = imageModel?.modelName ?: throw RuntimeException("No image model configured"),
                size = "${width}x$height"
            )
        ).data
        val first = data.first()
        return when {
            first.url != null -> ImageIO.read(URL(first.url))
            first.b64_json != null -> ImageIO.read(first.b64_json?.decodeBase64()?.inputStream())
            else -> throw RuntimeException("No image data returned")
        }
    }

    override fun respond(input: List<String>, vararg messages: ChatMessage): ImageAndText {
        var text = response(*messages).choices.first().message?.content
            ?: throw RuntimeException("No response")
        val maxPrompt = imageModel?.maxPrompt ?: Int.MAX_VALUE
        while (maxPrompt <= text.length && null != imageClient) {
            text = response(
                *listOf(
                    messages.toList(),
                    listOf(
                        text.toChatMessage(),
                        "Please shorten the description".toChatMessage(),
                    ),
                ).flatten().toTypedArray(),
                model = imageModel!!
            ).choices.first().message?.content ?: throw RuntimeException("No response")
        }
        return ImageAndText(
            text = text,
            image = render(
                text,
                api = this.imageClient ?: throw RuntimeException("No image client configured")
            )
        )
    }

    override fun withModel(model: ChatInterface): ImageGenerationAgent = ImageGenerationAgent(
        prompt = prompt,
        name = name,
        textModel = model,
        imageModel = imageModel,
        imageClient = imageClient,
        temperature = temperature,
        width = width,
        height = height,
    )

}

private fun String?.decodeBase64(): ByteArray? {
    return if (this == null) {
        null
    } else {
        java.util.Base64.getDecoder().decode(this)
    }
}


```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ImageProcessingAgent.kt

```
package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ContentPart
import com.simiacryptus.cognotik.util.toContentList
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

/**
 * Agent that processes text/images input and generates text/images output based on the prompt.
 * Can be used for image generation, image captioning, and image editing tasks.
 */
open class ImageProcessingAgent(
    prompt: String = "Analyze and describe the image based on the user's request",
    name: String? = null,
    model: ChatInterface,
    temperature: Double = 0.3,
) : BaseAgent<List<ImageAndText>, ImageAndText>(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
) {

    override fun chatMessages(questions: List<ImageAndText>) = arrayOf(
        ChatMessage(
            role = ModelSchema.Role.system,
            content = prompt.toContentList()
        ),
        ChatMessage(
            role = ModelSchema.Role.user,
            content = questions.flatMap { question ->
                listOf(
                    ContentPart(
                        text = question.text,
                        image_url = question.image?.let { "data:image/png;base64,${it.encodeImageToBase64()}" },
                    )
                )
            }
        )
    )

    override fun respond(
        input: List<ImageAndText>,
        vararg messages: ChatMessage
    ): ImageAndText {
        val choices = response(*messages).choices
        val image = choices.firstOrNull { it.message?.image_url != null }?.let { it.message?.image }
        if (image == null) {
            log.info("No image returned in response, falling back to input image.")
        }
        val text = choices.firstOrNull()?.message?.content ?: ""
        return ImageAndText(text = text, image = image ?: input.map { it.image }.firstOrNull())
    }

    override fun withModel(model: ChatInterface): ImageProcessingAgent = ImageProcessingAgent(
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
    )

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ImageProcessingAgent::class.java)
    }
}

/**
 * Encodes a BufferedImage to a Base64 string in PNG format
 */
fun BufferedImage.encodeImageToBase64(): String {
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(this, "png", outputStream)
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ParsedAgent.kt

```
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ParsedImageAgent.kt

```
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
    val deserializerRetries: Int = 2,
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
            """.toContentList()
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
        private val t : Pair<String, T> by lazy {
            var lastException: Exception? = null
            var resultText: String? = null
            var resultObj: T? = null
            for (i in 0..deserializerRetries) {
                try {
                    val responseContent = response(*messages).choices.firstOrNull()?.message?.content
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

    override fun withModel(model: ChatInterface): ParsedImageAgent<T> = ParsedImageAgent(
        resultClass = resultClass,
        exampleInstance = exampleInstance,
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
        deserializerRetries = deserializerRetries,
        validation = validation,
        describer = describer,
    )

    companion object {
        private val log = LoggerFactory.getLogger(ParsedImageAgent::class.java)
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ParsedResponse.kt

```
package com.simiacryptus.cognotik.agents

abstract class ParsedResponse<T>(val clazz: Class<T>) {
    abstract val text: String
    abstract val obj: T
    override fun toString() = text
    open fun <V> map(cls: Class<V>, fn: (T) -> V): ParsedResponse<V> = MappedResponse(cls, fn, this)
}

class MappedResponse<F, T>(
    clazz: Class<T>,
    private val fn: (F) -> T,
    private val inner: ParsedResponse<F>
) : ParsedResponse<T>(clazz) {
    override val text: String
        get() = inner.text
    override val obj: T
        get() = fn(inner.obj)

    override fun <V> map(cls: Class<V>, fn: (T) -> V): ParsedResponse<V> {
        return MappedResponse(cls, fn, this)
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ProxyAgent.kt

```
package com.simiacryptus.cognotik.agents

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.gson.reflect.TypeToken
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.DescriptorUtil
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.toContentList
import java.lang.reflect.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaType

open class ProxyAgent<T : Any>(
    val clazz: Class<out T>,
    private var model: ChatInterface,
    private var temperature: Double = 0.5,
    val validation: Boolean = true,
    private var maxRetries: Int = 5,
    val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "com.simiacryptus"
    ) {
        override val includeMethods: Boolean get() = false
    }
) {

    init {
        log.info("Created proxy for class: ${clazz.simpleName}")
    }

    open val metrics: Map<String, Any>
        get() = hashMapOf(
            "requests" to requestCounter.get(),
            "attempts" to attemptCounter.get(),
        ) + requestCounters.mapValues { it.value.get() }.mapKeys { "requests.${it.key}" }
    private val requestCounter = AtomicInteger(0)
    private val attemptCounter = AtomicInteger(0)
    private val requestCounters = HashMap<String, AtomicInteger>()

    fun create() = Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args ->
        if (method.name == "toString") return@newProxyInstance clazz.simpleName
        log.debug("Invoking method: ${method.name} with arguments: ${args?.joinToString()}")
        requestCounters.computeIfAbsent(method.name) { AtomicInteger(0) }.incrementAndGet()
        val type: Type = if (clazz.isKotlinClass()) {
            val returnType = DescriptorUtil.resolveMethodReturnType(clazz.kotlin, method.name)
            returnType.javaType
        } else {
            method.genericReturnType
        }
        val argList = if (clazz.isKotlinClass()) {
            val declaredMethod = clazz.kotlin.functions.find { it.name == method.name }
            if (null != declaredMethod) {
                (args ?: arrayOf()).zip(declaredMethod.parameters.filter { it.kind == KParameter.Kind.VALUE })
                    .filter { (arg: Any?, _) -> arg != null }
                    .withIndex()
                    .associate { (idx, p) ->
                        val (arg, param) = p
                        val toJson = JsonUtil.toJson(arg!!)
                        (param.name ?: "arg$idx") to toJson
                    }
            } else {
                (args ?: arrayOf()).zip(method.parameters)
                    .filter { (arg: Any?, _) -> arg != null }
                    .associate { (arg, param) -> param.name to JsonUtil.toJson(arg!!) }
            }
        } else {
            (args ?: arrayOf()).zip(method.parameters)
                .filter { (arg: Any?, _) -> arg != null }
                .associate { (arg, param) -> param.name to JsonUtil.toJson(arg!!) }
        }
        val prompt = ProxyRequest(
            method.name,
            describer.describe(method, clazz).trimIndent(),
            argList
        )

        var lastException: Exception? = null
        val originalTemp = temperature
        try {
            requestCounter.incrementAndGet()
            for (retry in 0 until maxRetries) {
                attemptCounter.incrementAndGet()
                log.debug("Attempt $retry for method: ${method.name}")
                if (retry > 0) {

                    temperature =
                        if (temperature <= 0.0) 0.0 else temperature.coerceAtLeast(0.1).pow(1.0 / (retry + 1))
                }
                val jsonResult0 = complete(prompt, *examples[method.name]?.toTypedArray() ?: arrayOf())
                val jsonResult = fixup(jsonResult0, type)
                try {
                    val obj = JsonUtil.fromJson<Any>(jsonResult, type)
                    if (validation) {
                        if (obj is ValidatedObject) {
                            val validate = obj.validate()
                            if (null != validate) {
                                log.error("Validation failed for method: ${method.name}, reason: $validate")
                                lastException = ValidatedObject.ValidationError(validate, obj)
                                continue
                            }
                        }
                    }
                    log.info("Successfully parsed response for method: ${method.name}")
                    return@newProxyInstance obj
                } catch (e: Exception) {
                    log.error("Failed to parse response for method: ${method.name}, response: $jsonResult", e)
                    lastException = e
                    log.debug("Retry $retry of $maxRetries for method: ${method.name}")
                }
            }
            log.error("Exhausted retries for method: ${method.name}, throwing exception")
            throw lastException ?: RuntimeException("Failed to parse response for method: ${method.name}")
        } finally {
            temperature = originalTemp
        }
    } as T

    val examples = HashMap<String, MutableList<RequestResponse>>()

    fun <R : Any> addExample(returnValue: R, functionCall: (T) -> Unit) {
        functionCall(
            Proxy.newProxyInstance(
                clazz.classLoader,
                arrayOf(clazz)
            ) { _: Any, method: Method, args: Array<Any> ->
                if (method.name == "toString") return@newProxyInstance clazz.simpleName
                val argList = args.zip(method.parameters)
                    .filter<Pair<Any?, Parameter>> { (arg: Any?, _) -> arg != null }
                    .associate { (arg, param) ->
                        param.name to JsonUtil.toJson(arg!!)
                    }
                val result = JsonUtil.toJson(returnValue)
                examples.getOrPut(method.name) { ArrayList() }.add(RequestResponse(argList, result))
                return@newProxyInstance returnValue
            } as T)
    }

    data class ProxyRequest(
        val methodName: String = "",
        val apiYaml: String = "",
        val argList: Map<String, String> = mapOf(),
    )

    data class RequestResponse(
        val argList: Map<String, String> = mapOf(),
        val response: String,
    )

    fun complete(prompt: ProxyRequest, vararg examples: RequestResponse): String {
        log.info("Starting completion with prompt: {}", prompt.toString())
        var request = ModelSchema.ChatRequest()
        val exampleMessages = examples.flatMap {
            listOf(
                ModelSchema.ChatMessage(
                    ModelSchema.Role.user,
                    argsToString(it.argList).toContentList()
                ),
                ModelSchema.ChatMessage(
                    ModelSchema.Role.assistant,
                    it.response.toContentList()
                )
            )
        }
        request = request.copy(
            messages = ArrayList(
                listOf(
                    ModelSchema.ChatMessage(
                        ModelSchema.Role.system, ("""
                          You are a JSON-RPC Service
                          Responses are in JSON format
                          Do not include explaining text outside the JSON
                          All input arguments are optional
                          Outputs are based on inputs, with any missing information filled randomly
                          You will respond to the following method
                          """.trimIndent() + prompt.apiYaml
                                ).trim().toContentList()
                    )
                ) + exampleMessages +
                        listOf(
                            ModelSchema.ChatMessage(
                                ModelSchema.Role.user,
                                argsToString(prompt.argList).toContentList()
                            )
                        )
            )
        )
        request = request.copy(model = model.modelType.modelName)
        request = request.copy(temperature = temperature)
        val json = JsonUtil.toJson(request)
        log.info("Request JSON: {}", json)
        val completion = model.chat(request.messages).choices.first().message?.content.orEmpty()
        log.debug("Received completion: {}", completion)
        val trimPrefix = trimPrefix(completion)
        val trimSuffix = trimSuffix(trimPrefix)
        log.info("Trimmed completion: {}", trimSuffix)
        return trimSuffix
    }

    companion object {
        fun fixup(jsonResult: String, type: Type): String {
            var jsonResult1 = jsonResult
            // Remove JSON-RPC wrapper if present
            jsonResult1 = unwrapJsonRpc(jsonResult1)

            if (type is ParameterizedType && List::class.java.isAssignableFrom(type.rawType as Class<*>) && !jsonResult1.startsWith(
                    "["
                )
            ) {
                val obj =
                    JsonUtil.fromJson<Map<String, Any>>(jsonResult1, object : TypeToken<Map<String, Any>>() {}.type)
                if (obj.size == 1) {
                    val key = obj.keys.firstOrNull()
                    if (key is String && obj[key] is List<*>) {
                        jsonResult1 = obj[key]?.let { JsonUtil.toJson(it) } ?: "[]"
                    }
                }
            }
            return jsonResult1
        }

        private fun unwrapJsonRpc(jsonResult: String): String {
            return try {
                val obj =
                    JsonUtil.fromJson<Map<String, Any>>(jsonResult, object : TypeToken<Map<String, Any>>() {}.type)
                // Check if this looks like a JSON-RPC response
                if (obj.containsKey("jsonrpc") && obj.containsKey("result")) {
                    log.debug("Detected JSON-RPC wrapper, extracting result field")
                    val result = obj["result"]
                    JsonUtil.toJson(result)
                } else {
                    jsonResult
                }
            } catch (e: Exception) {
                log.debug("Failed to parse as JSON-RPC wrapper, returning original: ${e.message}")
                jsonResult
            }
        }


        @JvmStatic
        fun main(args: Array<String>) {
            println(
                fixup(
                    """
                    {
                      "topics": [
                        "Stand-up comedy",
                        "Slapstick humor",
                        "Satire",
                        "Parody",
                        "Impressions",
                        "Observational comedy",
                        "Sketch comedy",
                        "Dark humor",
                        "Physical comedy",
                        "Improvisational comedy"
                      ]
                    }
                """.trimIndent(), object : TypeToken<List<String>>() {}.type
                )
            )

        }

        private val log = LoggerFactory.getLogger(ProxyAgent::class.java)
        private fun trimPrefix(completion: String): String {
            val braceIndex = completion.indexOf('{')
            val bracketIndex = completion.indexOf('[')
            val start = when {
                braceIndex == -1 && bracketIndex == -1 -> -1
                braceIndex == -1 -> bracketIndex
                bracketIndex == -1 -> braceIndex
                else -> minOf(braceIndex, bracketIndex)
            }
            return if (start < 0) {
                completion
            } else {
                completion.substring(start)
            }
        }

        private fun trimSuffix(completion: String): String {
            val braceIndex = completion.lastIndexOf('}')
            val bracketIndex = completion.lastIndexOf(']')
            val end = when {
                braceIndex == -1 && bracketIndex == -1 -> -1
                braceIndex == -1 -> bracketIndex
                bracketIndex == -1 -> braceIndex
                else -> maxOf(braceIndex, bracketIndex)
            }
            return if (end < 0) {
                completion
            } else {
                completion.substring(0, end + 1)
            }
        }

        private fun argsToString(argList: Map<String, String>) =
            "{" + argList.entries.joinToString(",\n", transform = { (argName, argValue) ->
                """"$argName": $argValue"""
            }) + "}"
    }
}
```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated README.md for the agents package.

### core/src/main/kotlin/com/simiacryptus/cognotik/agents/README.md
```markdown
# Cognotik Agents

The `com.simiacryptus.cognotik.agents` package provides a robust framework for building and interacting with AI agents. These agents range from simple text-based conversationalists to complex systems capable of code execution, image processing, and structured data extraction.

## Core Architecture

### BaseAgent
The `BaseAgent<I, R>` is the abstract foundation for all agents in the system. It defines a consistent interface for:
- **Input/Output**: Generic types `I` (Input) and `R` (Response).
- **Prompting**: Managing system prompts and chat message construction.
- **Model Integration**: Interfacing with `ChatInterface` for LLM calls.
- **Configuration**: Setting parameters like temperature and agent names.

## Agent Implementations

### ChatAgent
A simple text-to-text agent. It takes a list of strings (conversation history) and returns a string response. It is the most direct implementation of a conversational LLM.

### CodeAgent
A powerful agent designed for generating and executing code.
- **Execution Environment**: Uses a `CodeRuntime` to run generated code.
- **Self-Correction**: If code execution fails, the agent can automatically analyze the error and attempt to fix the code.
- **API Description**: Automatically generates documentation for provided symbols/objects using a `TypeDescriber`, allowing the LLM to use local APIs effectively.
- **Validation**: Can validate code syntax before execution.

### ImageGenerationAgent
Specializes in creating images from text descriptions.
- **Prompt Refinement**: Uses a text model to expand or refine user requests into detailed image prompts.
- **Multi-Model**: Coordinates between a text LLM and an image generation model (e.g., DALL-E).

### ImageProcessingAgent
A multi-modal agent that accepts both text and images as input.
- **Analysis**: Can describe images, answer questions about visual content, or perform image-to-image tasks.
- **Input Format**: Uses the `ImageAndText` data class for handling mixed media.

### ParsedAgent & ParsedImageAgent
These agents are designed to return structured data instead of raw text.
- **Schema-Driven**: Uses Kotlin/Java classes to define the expected output structure.
- **JSON Extraction**: Automatically handles the extraction and parsing of JSON from LLM responses.
- **Validation**: Integrates with `ValidatedObject` to ensure the parsed data meets specific business rules.
- **ParsedImageAgent**: Extends this functionality to multi-modal inputs (images + text).

### ProxyAgent
A high-level abstraction that allows interacting with an LLM through a standard Java/Kotlin interface.
- **Dynamic Proxy**: Creates an implementation of an interface at runtime.
- **Type-Safe**: Method calls are translated into LLM prompts, and the JSON responses are mapped back to the method's return type.
- **Few-Shot Learning**: Supports adding examples to guide the LLM's behavior for specific methods.

## Supporting Components

- **ImageAndText**: A data structure for passing images (`BufferedImage`) and associated text together.
- **ParsedResponse**: A wrapper that provides access to both the raw text response and the deserialized object.
- **CodeInterceptor**: A functional interface in `CodeAgent` for modifying or logging code before it is executed.

## Usage Patterns

Agents are typically instantiated with a `ChatInterface` (representing the LLM) and a specific prompt or configuration. They are designed to be composable and can be wrapped or extended to create complex multi-agent workflows.
```

### Summary of Changes
- Created a new `README.md` in the `com.simiacryptus.cognotik.agents` package directory.
- Provided a high-level overview of the agent framework.
- Documented each agent class (`BaseAgent`, `ChatAgent`, `CodeAgent`, `ImageGenerationAgent`, `ImageProcessingAgent`, `ParsedAgent`, `ParsedImageAgent`, and `ProxyAgent`) based on their implementation details in the provided source code.
- Highlighted key features such as `CodeAgent`'s self-correction and `ProxyAgent`'s type-safe interface mapping.
- Described supporting data structures like `ImageAndText` and `ParsedResponse`.
- Followed the directory structure and naming conventions implied by the project documentation.
</details>

                - <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/agents/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/agents/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/agents/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/agents/README.md</a> Updated
