package com.simiacryptus.cognotik.exceptions

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.simiacryptus.cognotik.platform.model.LLMModel
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.regex.Pattern

object ErrorUtil {
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
      val jsonElement = Gson().fromJson(result, JsonElement::class.java) ?: return
      if (jsonElement.isJsonObject) {
        val jsonObject = jsonElement.asJsonObject
        if (jsonObject.has("error") && jsonObject.get("error").isJsonObject) {
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
            if (jsonObject.has("error") && jsonObject.get("error").isJsonObject) {
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
    } catch (e: JsonParseException) {
      throw IOException(
        "Invalid JSON response: $result" + (if (null == model) "" else "\nChat Model: ${model}"),
        e
      )
    }
  }


  /**
   * Returns the chain of causes starting with [e], guarding against cycles.
   */
  private fun causeChain(e: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = e
    while (current != null && seen.add(current)) {
      chain.add(current)
      current = current.cause
    }
    return chain
  }

  /**
   * Classifies a single throwable, or returns null if it is not a recognized type.
   */
  private fun classify(e: Throwable): String? = when (e) {
    is RateLimitException -> "RateLimit"
    is QuotaException -> "Quota"
    is ModelMaxException -> "ModelMax"
    is InvalidModelException -> "InvalidModel"
    is InvalidValueException -> "InvalidValue"
    is SafetyException -> "Safety"
    is AIServiceException -> "AIService"
    is RequestOverloadException -> "Overload"
    is ModerationException -> "Moderation"
    is BudgetException -> "Budget"
    is NonRetryableException -> "NonRetryable"
    is FailedToImplementException -> "FailedToImplement"
    is MultiExeption -> "Multiple"
    is InterruptedException -> "Interrupted"
    is CancellationException -> "Cancelled"
    is java.net.SocketTimeoutException -> "Timeout"
    is java.util.concurrent.TimeoutException -> "Timeout"
    is java.net.UnknownHostException -> "Network"
    is java.net.ConnectException -> "Network"
    is JsonParseException -> "InvalidResponse"
    is VirtualMachineError -> "VirtualMachineError"
    else -> null
  }

  /**
   * Determines a descriptive error type for the given throwable. Wrapper exceptions
   * (e.g. [ExecutionException] or generic RuntimeExceptions) are unwrapped so that the
   * most specific recognized cause is reported.
   */
  fun errorType(e: Throwable): String {
    causeChain(e).forEach { t -> classify(t)?.let { return it } }
    val root = causeChain(e).firstOrNull { it !is ExecutionException } ?: e
    return root::class.simpleName ?: "UnknownError"
  }

  /**
   * Determines whether the given throwable represents a fatal (non-retryable) error.
   * The cause chain is inspected; if any exception in the chain is fatal, the error is fatal.
   */
  fun isFatal(e: Throwable): Boolean = causeChain(e).any { t ->
    when (t) {
      is AIServiceException -> t.isFatal
      is NonRetryableException -> true
      is ModerationException -> true
      is InterruptedException -> true
      is CancellationException -> true
      is VirtualMachineError -> true
      is RequestOverloadException -> false
      else -> false
    }
  }

  /**
   * Fully-qualified class names (from optional libraries) whose instances indicate a
   * response that could not be parsed. Matched by name to avoid hard dependencies.
   */
  private val unparseableClassNames = setOf(
    "com.fasterxml.jackson.core.JsonProcessingException",
    "com.fasterxml.jackson.core.JacksonException",
    "com.fasterxml.jackson.databind.exc.MismatchedInputException",
    "kotlinx.serialization.SerializationException",
    "com.google.gson.stream.MalformedJsonException",
  )

  /**
   * Message patterns commonly produced by JSON parsers (or by [checkError]) when a
   * response body cannot be interpreted.
   */
  private val unparseableMessagePatterns = listOf(
    Pattern.compile("""^Invalid JSON response"""),
    Pattern.compile("""Expected \S+(?: \S+)* but was \S+"""),
    Pattern.compile("""Unterminated (object|array|string|escape sequence)""", Pattern.CASE_INSENSITIVE),
    Pattern.compile("""Unexpected (character|token|end[- ]of[- ](input|stream|file))""", Pattern.CASE_INSENSITIVE),
    Pattern.compile("""malformed\s*json""", Pattern.CASE_INSENSITIVE),
    Pattern.compile("""Unrecognized token""", Pattern.CASE_INSENSITIVE),
    Pattern.compile("""Use JsonReader\.setLenient\(true\)"""),
    Pattern.compile("""Not a JSON (Object|Array|Primitive)"""),
  )

  private fun isUnparseableClass(t: Throwable): Boolean {
    var clazz: Class<*>? = t.javaClass
    while (clazz != null && clazz != Throwable::class.java) {
      if (clazz.name in unparseableClassNames) return true
      clazz = clazz.superclass
    }
    return false
  }

  private fun hasUnparseableMessage(t: Throwable): Boolean {
    val message = t.message ?: return false
    return unparseableMessagePatterns.any { it.matcher(message).find() }
  }

  /**
   * Determines whether the given throwable indicates that a model/service response could
   * not be parsed (e.g. malformed or unexpected JSON). The full cause chain is inspected.
   */
  fun isUnparseableResponse(e: Throwable): Boolean = causeChain(e).any { t ->
    when {
      t is JsonParseException -> true
      isUnparseableClass(t) -> true
      t is IllegalStateException || t is IOException || t is IllegalArgumentException ->
        hasUnparseableMessage(t)

      else -> hasUnparseableMessage(t)
    }
  }

}