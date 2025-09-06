package com.simiacryptus.cognotik.util

import com.google.gson.Gson
import com.simiacryptus.cognotik.exceptions.*
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern

object ClientUtil {
    open class ErrorPattern(
        vararg val pattern: Pattern,
        val exceptionFactory: (String, Pattern) -> Exception?
    ) {
        open fun match(str: String): Exception? {
            pattern.forEach {
                val matcher = it.matcher(str)
                if (matcher.find()) return exceptionFactory(str, it)
            }
            return null
        }
    }

    private val errorPatterns = listOf(
        ErrorPattern(
            Pattern.compile("""That model is currently overloaded with other requests."""),
        ) { errorMessage, pattern -> RequestOverloadException(errorMessage) },
        ErrorPattern(
            Pattern.compile("""Your request was rejected as a result of our safety system."""),
        ) { errorMessage, pattern -> SafetyException() },
        ErrorPattern(
            Pattern.compile("""This model's maximum context length is (\d+) tokens. However, you requested (\d+) tokens \((\d+) in the messages, (\d+) in the completion\).*"""),
        ) { errorMessage, pattern ->
            val matcher = pattern.matcher(errorMessage)
            if (matcher.find()) {
                ModelMaxException(
                    matcher.group(1).toInt(),
                    matcher.group(2).toInt(),
                    matcher.group(3).toInt(),
                    matcher.group(4).toInt()
                )
            } else null
        },
        ErrorPattern(
            Pattern.compile("""This model's maximum context length is (\d+) tokens, however you requested (\d+) tokens \((\d+) in your prompt; (\d+) for the completion\).*"""),
        ) { errorMessage, pattern ->
            val matcher = pattern.matcher(errorMessage)
            if (matcher.find()) {
                ModelMaxException(
                    matcher.group(1).toInt(),
                    matcher.group(2).toInt(),
                    matcher.group(3).toInt(),
                    matcher.group(4).toInt()
                )
            } else null
        },
        ErrorPattern(
            Pattern.compile("""Rate limit reached for (\d+)KTPM-(\d+)RPM in organization (\S+) on tokens per min. Limit: (\d+) / min. Please try again in (\d+)ms"""),
        ) { errorMessage, pattern ->
            val matcher =
                pattern.matcher(errorMessage)
            if (matcher.find()) {
                RateLimitException(matcher.group(3), matcher.group(4).toInt(), matcher.group(5).toLong())
            } else null
        },
        ErrorPattern(
            Pattern.compile("""Rate limit reached for (\S+) in organization (\S+) on requests per min \(RPM\): Limit (\d+), Used (\d+), Requested (\d+). Please try again in (\d+)s."""),
        ) { errorMessage, pattern ->
            val matcher = pattern.matcher(errorMessage)
            if (matcher.find()) {
                RateLimitException(matcher.group(2), matcher.group(3).toInt(), matcher.group(6).toLong())
            } else null
        },
        ErrorPattern(
            Pattern.compile("""Rate limit exceeded for (\S+) per minute in organization (\S+). Limit: (\d+)/(\d+)min. Current: (\d+)/(\d+)min."""),
        ) { errorMessage, pattern ->
            val matcher = pattern.matcher(errorMessage)
            if (matcher.find()) {
                RateLimitException(matcher.group(2), matcher.group(3).toInt(), matcher.group(4).toLong() * 60)
            } else null
        },
        ErrorPattern(
            Pattern.compile("""exceeded .*quota"""),
        ) { errorMessage, pattern ->
            if (pattern.matcher(errorMessage).find()) QuotaException() else null
        },
        ErrorPattern(
            Pattern.compile("""model `(\S+)` does not exist"""),
            Pattern.compile("""Invalid model: (\S+)"""),
        ) { errorMessage, pattern ->
            val matcher = pattern.matcher(errorMessage)
            if (matcher.find()) InvalidModelException(matcher.group(1)) else null
        },
        ErrorPattern(
            Pattern.compile("""Invalid value for '(\S+)': (\S+)"""),
        ) { errorMessage, pattern ->
            val matcher = pattern.matcher(errorMessage)
            if (matcher.find()) InvalidValueException(matcher.group(1), matcher.group(2)) else null
        }
    )

    fun checkError(result: String, model: LLMModel? = null) {
        try {
            val jsonElement = Gson().fromJson(result, com.google.gson.JsonElement::class.java) ?: return
            if (jsonElement.isJsonObject) {
                val jsonObject = jsonElement.asJsonObject
                if (jsonObject.has("error")) {
                    val errorObject = jsonObject.getAsJsonObject("error")
                    val errorMessage = errorObject["message"].asString
                    errorPatterns.forEach { errorPattern ->
                        errorPattern.match(errorMessage)?.let { throw it }
                    }
                    throw IOException(errorMessage)
                }
            } else if (jsonElement.isJsonArray) {
                val jsonArray = jsonElement.asJsonArray
                for (element in jsonArray) {
                    if (element.isJsonObject) {
                        val jsonObject = element.asJsonObject
                        if (jsonObject.has("error")) {
                            val errorObject = jsonObject.getAsJsonObject("error")
                            val errorMessage = errorObject["message"].asString
                            errorPatterns.forEach { errorPattern ->
                                errorPattern.match(errorMessage)?.let { throw it }
                            }
                            throw IOException(errorMessage)
                        }
                    }
                }
            }
        } catch (e: com.google.gson.JsonSyntaxException) {
            throw IOException(
                "Invalid JSON response: $result" + (if (null == model) "" else "\nChat Model: ${model}"),
                e
            )
        }
    }

    fun String.toContentList() = listOf(this).map { ApiModel.ContentPart(text = it, type = "text") }
    fun String.toChatMessage(role: ApiModel.Role = ApiModel.Role.user) =
        ApiModel.ChatMessage(role = role, content = toContentList())

    val allowedCharset: Charset = Charset.forName("ASCII")

}