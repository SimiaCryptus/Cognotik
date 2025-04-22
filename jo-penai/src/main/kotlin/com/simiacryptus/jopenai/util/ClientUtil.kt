package com.simiacryptus.jopenai.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.exceptions.*
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.TextModel
import com.simiacryptus.util.JsonUtil.fromJson
import java.io.File
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

    fun checkError(result: String, model: TextModel? = null) {
        try {
            val jsonObject = Gson().fromJson(result, JsonObject::class.java) ?: return
            if (jsonObject.has("error")) {
                val errorObject = jsonObject.getAsJsonObject("error")
                val errorMessage = errorObject["message"].asString
                errorPatterns.forEach { errorPattern ->
                    errorPattern.match(errorMessage)?.let { throw it }
                }
                throw IOException(errorMessage)
            }
        } catch (e: com.google.gson.JsonSyntaxException) {
            throw IOException("Invalid JSON response: $result" + (if(null == model) "" else "\nChat Model: ${model}"), e)
        }
    }

    private var _keyTxt: String? = null
    val defaultApiProvider: APIProvider = APIProvider.OpenAI
    var keyTxt: String
        get() {
            if (null != _keyTxt) return _keyTxt!!
            val resourceAsStream = OpenAIClient::class.java.getResourceAsStream("/openai.key.json")
            if (null != resourceAsStream) return resourceAsStream.readAllBytes().toString(Charsets.UTF_8).trim()
            val keyFile = File(File(System.getProperty("user.home")), "openai.key.json")
            if (keyFile.exists()) return keyFile.readText().trim()
            return ""
        }
        set(value) {
            _keyTxt = value
        }

    val keyMap: Map<String, String>
        get() = try {
            fromJson(keyTxt, Map::class.java)
        } catch (e: Exception) {
            emptyMap()
        }

    fun String.toContentList() = listOf(this).map { ApiModel.ContentPart(text = it, type = "text") }
    fun String.toChatMessage(role: ApiModel.Role = ApiModel.Role.user) =
        ApiModel.ChatMessage(role = role, content = toContentList())

    val allowedCharset: Charset = Charset.forName("ASCII")

}