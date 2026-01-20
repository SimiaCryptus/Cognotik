# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/AIServiceException.kt

```
package com.simiacryptus.cognotik.exceptions

import java.io.IOException

open class AIServiceException(message: String?, val isFatal: Boolean = false) : IOException(message)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/ErrorUtil.kt

```
package com.simiacryptus.cognotik.exceptions

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.simiacryptus.cognotik.models.LLMModel
import java.io.IOException
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
        } catch (e: JsonParseException) {
            throw IOException(
                "Invalid JSON response: $result" + (if (null == model) "" else "\nChat Model: ${model}"),
                e
            )
        }
    }

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/InvalidModelException.kt

```
package com.simiacryptus.cognotik.exceptions

import com.simiacryptus.cognotik.util.LoggerFactory

class InvalidModelException(model: String?) : AIServiceException("Invalid model: $model", isFatal = true) {
    companion object {
        private val log = LoggerFactory.getLogger("InvalidModelLogger")
    }

    init {
        if (model.isNullOrEmpty()) {
            log.warn("InvalidModelException thrown with no model specified")
        } else {
            log.error("InvalidModelException thrown for model: $model")
        }
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/InvalidValueException.kt

```
package com.simiacryptus.cognotik.exceptions

class InvalidValueException(field: String?, value: String?) :
    AIServiceException("Invalid value: $field = $value", isFatal = true)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/ModelMaxException.kt

```
package com.simiacryptus.cognotik.exceptions

class ModelMaxException(
    modelMax: Int,
    val request: Int,
    val messages: Int,
    completion: Int
) : AIServiceException(
    message = "Model max exceeded: $modelMax, request: $request, messages: $messages, completion: $completion",
    isFatal = true
)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/ModerationException.kt

```
package com.simiacryptus.cognotik.exceptions

class ModerationException(message: String?) : Exception(message)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/QuotaException.kt

```
package com.simiacryptus.cognotik.exceptions

import com.simiacryptus.cognotik.util.LoggerFactory

class QuotaException : AIServiceException("Quota exceeded", isFatal = true) {
    companion object {
        private val log = LoggerFactory.getLogger("QuotaLogger")
    }

    init {
        log.warn("QuotaException initialized: Quota exceeded")
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/RateLimitException.kt

```
package com.simiacryptus.cognotik.exceptions

class RateLimitException(
    org: String?,
    limit: Int,
    val delay: Long
) : AIServiceException("Rate limit exceeded: $org, limit: $limit, delay: $delay")
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/RequestOverloadException.kt

```
package com.simiacryptus.cognotik.exceptions

import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.IOException

class RequestOverloadException(
    message: String = "That model is currently overloaded with other requests."
) : IOException(message) {
    companion object {
        private val log = LoggerFactory.getLogger(RequestOverloadException::class.java)
    }

    init {
        log.debug("RequestOverloadException initialized with message: $message")
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/SafetyException.kt

```
/**
 * Exception thrown when a safety violation is detected
 */
package com.simiacryptus.cognotik.exceptions

/**
 * Exception thrown when a safety violation is detected
 */
class SafetyException : AIServiceException {
    constructor() : super("Safety violation")
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

Here is the updated `README.md` for the exceptions package, summarizing the error handling logic and the specific exception types used within the Cognotik core.

### core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/README.md
```markdown
# Exceptions

This package provides a structured hierarchy of exceptions and utilities for handling errors encountered when interacting with AI services. It includes specialized exceptions for common failure modes like rate limiting, quota exhaustion, and context length violations.

## Error Handling Utility

### [ErrorUtil](ErrorUtil.kt)
The `ErrorUtil` object is the central point for interpreting error responses from AI providers. It parses JSON error bodies and uses regular expression patterns to map provider-specific error messages to the appropriate internal exception classes.

## Exception Hierarchy

### [AIServiceException](AIServiceException.kt)
The base class for all AI service-related errors. It extends `java.io.IOException` and includes an `isFatal` flag to help calling code determine if a retry is appropriate.

### Fatal Exceptions
These exceptions indicate configuration or input issues that typically cannot be resolved by simple retries:
*   **[InvalidModelException](InvalidModelException.kt)**: Thrown when the requested model identifier is not recognized by the service.
*   **[InvalidValueException](InvalidValueException.kt)**: Thrown when a request parameter (e.g., temperature, top_p) contains an invalid value.
*   **[ModelMaxException](ModelMaxException.kt)**: Thrown when the combined prompt and completion token count exceeds the model's hard context limit.
*   **[QuotaException](QuotaException.kt)**: Thrown when the account's usage quota or credit balance has been exhausted.

### Transient & Behavioral Exceptions
These exceptions represent temporary conditions or policy-driven rejections:
*   **[RateLimitException](RateLimitException.kt)**: Thrown when the provider's rate limits (RPM/TPM) are exceeded. Includes a `delay` field suggesting how long to wait before retrying.
*   **[RequestOverloadException](RequestOverloadException.kt)**: Thrown when the remote server is currently too busy to process the request.
*   **[SafetyException](SafetyException.kt)**: Thrown when a request is rejected by the provider's safety filters.
*   **[ModerationException](ModerationException.kt)**: A general exception for content moderation failures.

## Usage
Most exceptions in this package are automatically thrown by `ErrorUtil.checkError(responseBody)` when processing API responses. Developers should catch `AIServiceException` to handle AI-specific errors or `IOException` for general networking issues.
```

### Summary of Changes
- Created a new `README.md` in `core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/`.
- Documented the `AIServiceException` base class and its `isFatal` property.
- Categorized exceptions into "Fatal" and "Transient/Behavioral" to guide developers on retry logic.
- Documented the role of `ErrorUtil` in parsing provider responses.
- Linked to the individual source files for detailed implementation reference.

This documentation aligns with the provided source code and the transformation rules specified in `index_docs.md`.
</details>

                - <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/exceptions/README.md</a> Updated
