---
specifies: ./**/*Client.kt
---

 # LLM Exceptions in Cognotik

This guide covers the exception types Cognotik uses for LLM and AI-service failures. It explains how provider error responses become typed exceptions. It also covers how errors are classified for retry logic and metrics, and how to extend the system.

All types live in the `com.simiacryptus.cognotik.exceptions` package. The helpers are in `ErrorUtil`.

---

## 1. Overview

An LLM call can fail in several ways:

| Category                         | Examples                                    | Typical handling                  |
|----------------------------------|---------------------------------------------|-----------------------------------|
| Transient / capacity             | Rate limits, model overloaded, timeouts     | Back off and retry                |
| Request is invalid               | Context too long, bad model, bad parameter  | Fix the request; do not retry     |
| Account / policy                 | Quota exhausted, budget exceeded            | Stop; surface to the user         |
| Content policy                   | Safety system rejection, moderation         | Surface to the user               |
| Unparseable response             | Malformed / unexpected JSON                 | Usually retry, or report a bug    |
| Higher-level task failures       | Code generation failed, multiple failures   | Application-specific              |

Cognotik handles these failures in four steps:

1. **Typed exceptions** describe each failure (`RateLimitException`, `ModelMaxException`, and others).
2. **`ErrorUtil.checkError`** turns a provider's JSON error body into one of these exceptions.
3. **`ErrorUtil.isFatal` / `errorType` / `isUnparseableResponse`** classify any `Throwable`. These are used for retry decisions and metrics.
4. **`ChatClientBase`** reports every failure as an `AI_ERROR` metric event.

---

## 2. Exception Hierarchy

```text
Throwable
└── Exception
    ├── IOException
    │   ├── AIServiceException(message, isFatal = false)
    │   │   ├── RateLimitException        (retryable, has `delay`)
    │   │   ├── SafetyException           (isFatal = false, see notes)
    │   │   ├── ModelMaxException         (fatal)
    │   │   ├── QuotaException            (fatal)
    │   │   ├── InvalidModelException     (fatal)
    │   │   └── InvalidValueException     (fatal)
    │   └── RequestOverloadException      (retryable)
    ├── RuntimeException
    │   ├── NonRetryableException         (always fatal)
    │   │   └── BudgetException           (always fatal)
    │   ├── FailedToImplementException
    │   └── MultiExeption
    └── ModerationException               (always fatal)
```

### 2.1 `AIServiceException`

```kotlin
open class AIServiceException(message: String?, val isFatal: Boolean = false) : IOException(message)
```

This is the base class for errors reported by an AI provider. It extends `IOException`, so existing I/O error handling will catch it.

The `isFatal` flag is the main signal for retry logic:
- `false` means the request might succeed if retried later.
- `true` means retrying the same request will not help.

Subclass it for new provider-level errors, and set `isFatal` accordingly.

### 2.2 `RateLimitException`

```kotlin
class RateLimitException(org: String?, limit: Int, val delay: Long) : AIServiceException(...)
```

- **Fatal:** no.
- **Meaning:** the organization has hit a tokens-per-minute or requests-per-minute limit.
- **`delay`:** a suggested wait before retrying, parsed from the provider message.

> ⚠️ **The unit of `delay` depends on which message matched.**
> - `"... Please try again in (\d+)ms"`: **milliseconds**.
> - `"... Please try again in (\d+)s."`: **seconds**.
> - `"... Limit: X/Nmin ..."`: `N * 60`, i.e. the window length in **seconds**.
>
> Treat `delay` as a hint. Clamp it to a sensible minimum and maximum, and add your own backoff.

### 2.3 `RequestOverloadException`

```kotlin
class RequestOverloadException(message: String = "That model is currently overloaded with other requests.") : IOException(message)
```

- **Fatal:** no.
- **Meaning:** the provider is temporarily out of capacity for this model.
- **Handling:** retry with exponential backoff.
- **Note:** this class does **not** extend `AIServiceException`.

### 2.4 `ModelMaxException`

```kotlin
class ModelMaxException(modelMax: Int, val request: Int, val messages: Int, completion: Int)
```

- **Fatal:** yes.
- **Meaning:** prompt tokens plus requested completion tokens exceed the model's context window.
- **Properties:** only `request` (total tokens requested) and `messages` (prompt tokens) are exposed. `modelMax` and `completion` appear only in the message text.
- **Handling:** shrink the request and send a new one. You can trim history, summarize, chunk the input, lower `max_tokens`, or switch to a model with a larger context window. Resending the same request will fail again.

### 2.5 `QuotaException`

- **Fatal:** yes.
- **Meaning:** the account's billing or usage quota is exhausted.
- **Logging:** logs a warning (`QuotaLogger`) when constructed.
- **Handling:** stop and notify the user or operator.

### 2.6 `InvalidModelException`

```kotlin
class InvalidModelException(model: String?)
```

- **Fatal:** yes.
- **Meaning:** the model ID does not exist or is not available to this key.
- **Logging:** logs an error (or a warning if `model` is empty) to `InvalidModelLogger` when constructed.
- **Handling:** fix the configuration. Do not retry.

### 2.7 `InvalidValueException`

```kotlin
class InvalidValueException(field: String?, value: String?)
```

- **Fatal:** yes.
- **Meaning:** a request parameter has an invalid value, for example an out-of-range `temperature`.
- **Handling:** fix the request. Do not retry.

### 2.8 `SafetyException`

- **Fatal:** **no**. It uses the default `isFatal = false`.
- **Meaning:** the provider's safety system rejected the request.
- **Handling:** generic retry loops will treat this as retryable. Retrying an identical prompt usually fails again, so handle `SafetyException` explicitly if you want different behavior.

### 2.9 `ModerationException`

```kotlin
class ModerationException(message: String?) : Exception(message)
```

- **Fatal:** yes. `ErrorUtil.isFatal` hard-codes this.
- **Meaning:** a moderation check failed. This is typically raised by Cognotik's own moderation step, not parsed from a provider body.
- **Note:** this is a checked-style `Exception`, not an `IOException`.

### 2.10 `NonRetryableException` and `BudgetException`

```kotlin
open class NonRetryableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
open class BudgetException(message: String, cause: Throwable? = null) : NonRetryableException(message, cause)
```

- **Fatal:** always.
- **Usage:** wrap any error in `NonRetryableException` to force retry logic to stop. This works because `isFatal` inspects the whole cause chain.
- **`BudgetException`:** signals that a Cognotik-level spending or token budget was exceeded. This is distinct from a provider `QuotaException`.

### 2.11 `FailedToImplementException`

```kotlin
class FailedToImplementException(
  cause: Throwable? = null,
  message: String = "Failed to implement",
  val language: String? = null,
  val code: String? = null,
  val prefix: String? = null,
) : RuntimeException(message, cause)
```

This is a higher-level failure raised by code-generating agents when the model could not produce working code. It carries the target `language`, the last attempted `code`, and the `prefix` context so callers can display or repair the result.

### 2.12 `MultiExeption`

```kotlin
class MultiExeption(exceptions: Collection<Throwable>) : RuntimeException(...)
```

This aggregates several failures, for example from parallel sub-tasks. The message joins each stack trace in a fenced `text` block, so it renders well in Markdown UIs.

- **Spelling:** the class name is `MultiExeption`, not `MultiException`.
- **Fatal:** no. The wrapped exceptions are not attached as `cause`, so `isFatal` cannot see them.

---

## 3. Turning Provider Responses into Exceptions: `ErrorUtil.checkError`

```kotlin
fun checkError(result: String, model: LLMModel? = null)
```

Call this on every raw response body from a provider. It returns normally if no error is found.

**Algorithm:**

1. Parse `result` with Gson.
   - If parsing fails with `JsonParseException`, it throws `IOException("Invalid JSON response: <body>\nChat Model: <model>")`.
2. If the body is an object with an object-valued `"error"` field, read `error.message`.
3. If the body is an array, check each element the same way and stop at the first error.
4. Test the message against the known error patterns below, in order. Throw the first exception produced.
5. If no pattern matches, throw a plain `IOException(errorMessage)`.

**Patterns, in evaluation order:**

| Provider message (regex, abbreviated)                                                                    | Exception                    |
|----------------------------------------------------------------------------------------------------------|------------------------------|
| `That model is currently overloaded with other requests.`                                                | `RequestOverloadException`   |
| `Your request was rejected as a result of our safety system.`                                            | `SafetyException`            |
| `This model's maximum context length is N tokens. However, you requested N tokens (N in the messages, N in the completion)` | `ModelMaxException` |
| `This model's maximum context length is N tokens, however you requested N tokens (N in your prompt; N for the completion)`  | `ModelMaxException` |
| `Rate limit reached for NKTPM-NRPM in organization ORG on tokens per min. Limit: N / min. Please try again in Nms` | `RateLimitException` (ms) |
| `Rate limit reached for X in organization ORG on requests per min (RPM): Limit N, Used N, Requested N. Please try again in Ns.` | `RateLimitException` (s) |
| `Rate limit exceeded for X per minute in organization ORG. Limit: N/Nmin. Current: N/Nmin.`              | `RateLimitException`         |
| `exceeded .*quota`                                                                                        | `QuotaException`             |
| ``model `X` does not exist`` / `Invalid model: X`                                                         | `InvalidModelException`      |
| `Invalid value for 'FIELD': VALUE`                                                                        | `InvalidValueException`      |

**Example:**

```kotlin
val body = client.post(url, requestJson, model = model.modelName)
ErrorUtil.checkError(body, model)   // throws a typed exception if `body` is an error payload
val parsed = parseResponse(body)
```

**Caveats:**
- The patterns target OpenAI-style error bodies (`{"error": {"message": ...}}`). Other providers may produce only the generic `IOException`, or need new patterns (see §7).
- If `error.message` is missing, `errorObject["message"]` is `null` and `.asString` throws a `NullPointerException`.

---

## 4. Classifying Exceptions

All classifiers walk the **cause chain** (`e`, `e.cause`, `e.cause.cause`, …). They are protected against cyclic causes. This means wrapping an exception in `ExecutionException`, `RuntimeException`, and so on does not hide it.

### 4.1 `ErrorUtil.isFatal(e): Boolean`

Returns `true` if **any** throwable in the chain is fatal:

| Type                         | Fatal?          |
|------------------------------|-----------------|
| `AIServiceException`         | `e.isFatal`     |
| `NonRetryableException` (incl. `BudgetException`) | yes |
| `ModerationException`        | yes             |
| `InterruptedException`       | yes             |
| `CancellationException`      | yes             |
| `VirtualMachineError` (OOM, StackOverflow) | yes |
| `RequestOverloadException`   | no              |
| anything else                | no              |

Because of the "any" rule, `NonRetryableException(cause = RateLimitException(...))` is fatal.

### 4.2 `ErrorUtil.errorType(e): String`

Returns a short, stable label for metrics and logs. The cause chain is scanned from the outermost exception inward, and the **first recognized** type wins. Within a single throwable, more specific types are checked before their parents.

| Label                | Matches                                                    |
|----------------------|------------------------------------------------------------|
| `RateLimit`          | `RateLimitException`                                       |
| `Quota`              | `QuotaException`                                           |
| `ModelMax`           | `ModelMaxException`                                        |
| `InvalidModel`       | `InvalidModelException`                                    |
| `InvalidValue`       | `InvalidValueException`                                    |
| `Safety`             | `SafetyException`                                          |
| `AIService`          | other `AIServiceException`                                 |
| `Overload`           | `RequestOverloadException`                                 |
| `Moderation`         | `ModerationException`                                      |
| `Budget`             | `BudgetException`                                          |
| `NonRetryable`       | other `NonRetryableException`                              |
| `FailedToImplement`  | `FailedToImplementException`                               |
| `Multiple`           | `MultiExeption`                                            |
| `Interrupted`        | `InterruptedException`                                     |
| `Cancelled`          | `CancellationException`                                    |
| `Timeout`            | `SocketTimeoutException`, `TimeoutException`               |
| `Network`            | `UnknownHostException`, `ConnectException`                 |
| `InvalidResponse`    | Gson `JsonParseException`                                  |
| `VirtualMachineError`| `VirtualMachineError`                                      |

If nothing in the chain is recognized, the label is the simple class name of the outermost exception that is not an `ExecutionException`, for example `IOException` or `IllegalStateException`.

**Wrapping matters.** Recognized wrappers are reported instead of their causes. For example, `NonRetryableException(cause = RateLimitException(...))` is labelled `NonRetryable`, not `RateLimit`.

### 4.3 `ErrorUtil.isUnparseableResponse(e): Boolean`

Returns `true` when the failure means a response body could not be parsed. Any throwable in the chain counts if:

- it is a Gson `JsonParseException`; or
- its class (or a superclass) is one of these, matched by name so no dependency is required:
  - Jackson `JsonProcessingException`, `JacksonException`, `MismatchedInputException`
  - kotlinx `SerializationException`
  - Gson `MalformedJsonException`
- or its message matches a typical parser error, such as:
  - `Invalid JSON response…` (from `checkError`)
  - `Expected X but was Y`
  - `Unterminated object/array/string`
  - `Unexpected character/token/end of input`
  - `malformed json`
  - `Unrecognized token`
  - `Use JsonReader.setLenient(true)`
  - `Not a JSON Object/Array/Primitive`

Use it to tell "the model or provider returned junk" apart from "the provider returned a well-formed error". Junk is often worth a retry; a well-formed error should be handled by its type.

---

## 5. Integration with `ChatClientBase` and Metrics

`ChatClientBase.post(...)` is the shared HTTP path for chat clients.

1. **Logging.** It logs the request, the response, and the caller stack at `DEBUG` to the configured log streams.
2. **Embedded API errors.** After a successful HTTP exchange, it runs `ErrorUtil.checkError(response)` for metrics only.
   - If an error is detected and it is *not* an unparseable-response error, it is reported via `reportException`.
   - It is **not thrown** at this point. The raw body is still returned, and callers must call `checkError` themselves to get the exception.
3. **Transport failures.** Any exception from the request itself is reported, logged at `ERROR` along with the request entity, and rethrown. This covers timeouts, connection errors, and empty bodies.

`reportException` emits an `EventType.AI_ERROR` event, which also increments the AI-errors counter. The event has these attributes:

| Attribute     | Value                                 |
|---------------|---------------------------------------|
| `PROVIDER`    | `provider.toString()`                 |
| `MODEL`       | the `model` argument passed to `post` |
| `ERROR_TYPE`  | `ErrorUtil.errorType(e)`              |
| `FATAL`       | `ErrorUtil.isFatal(e)`                |
| `USER`        | `user?.toString()` if set             |
| `SESSION`     | `session.toString()`                  |

Metric failures are swallowed and logged at `DEBUG`, so they never break a request.

**Wiring metrics at startup:**

```kotlin
ChatClientBase.metricsProvider = { myMetricsBackend }   // default: NoOpMetrics
```

Subclasses can override `metrics` or `reportException` for per-client behavior.

Pass `model = ...` to `post(...)` so error metrics can be broken down by model.

---

## 6. Recommended Handling Patterns

### 6.1 A generic retry loop

```kotlin
fun <T> withRetries(maxAttempts: Int = 5, block: () -> T): T {
  var attempt = 0
  var backoffMs = 1_000L
  while (true) {
    try {
      return block()
    } catch (e: Throwable) {
      attempt++
      if (ErrorUtil.isFatal(e) || attempt >= maxAttempts) throw e
      val waitMs = when (val rl = findCause<RateLimitException>(e)) {
        null -> backoffMs
        // `delay` may be ms or seconds; normalise defensively.
        else -> rl.delay.let { if (it < 1_000) it * 1_000 else it }.coerceIn(500, 120_000)
      }
      Thread.sleep(waitMs)
      backoffMs = (backoffMs * 2).coerceAtMost(60_000)
    }
  }
}

inline fun <reified T : Throwable> findCause(e: Throwable): T? =
  generateSequence(e) { it.cause }.take(32).filterIsInstance<T>().firstOrNull()
```

### 6.2 Handling specific failures

```kotlin
try {
  val body = client.post(url, json, model = model.modelName)
  ErrorUtil.checkError(body, model)
  return parse(body)
} catch (e: ModelMaxException) {
  // Retrying won't help: shrink the prompt, then re-issue.
  return retryWithTrimmedContext(e.messages)
} catch (e: SafetyException) {
  // Not marked fatal, but identical retries rarely succeed.
  throw NonRetryableException("Request rejected by provider safety system", e)
} catch (e: IOException) {
  if (ErrorUtil.isUnparseableResponse(e)) {
    // Malformed model output: a retry (possibly with a stricter prompt) is reasonable.
  }
  throw e
}
```

### 6.3 Forcing a stop

To stop retries no matter what the underlying error is, wrap it:

```kotlin
throw NonRetryableException("User cancelled the task", cause)
throw BudgetException("Session token budget of $limit exceeded")
```

### 6.4 Aggregating parallel failures

```kotlin
val failures = futures.mapNotNull { runCatching { it.get() }.exceptionOrNull() }
if (failures.isNotEmpty()) throw MultiExeption(failures)
```

Remember that `MultiExeption` hides the individual causes from `isFatal` and `errorType`. If any of them is fatal, check before aggregating:

```kotlin
failures.firstOrNull(ErrorUtil::isFatal)?.let { throw it }
```

---

## 7. Extending the System

### Adding a new provider error

1. Create the exception. Prefer subclassing `AIServiceException` and choose `isFatal` deliberately:

   ```kotlin
   class ContentTooLargeException(bytes: Long) :
     AIServiceException("Content too large: $bytes bytes", isFatal = true)
   ```

2. Register a pattern in `ErrorUtil.errorPatterns`. The list is private, so edit it directly. Order matters, and the first match wins:

   ```kotlin
   ErrorPattern(
     Pattern.compile("""Request payload too large: (\d+) bytes"""),
   ) { msg, pattern ->
     val m = pattern.matcher(msg)
     if (m.find()) ContentTooLargeException(m.group(1).toLong()) else null
   },
   ```

   An `ErrorPattern` can take several regexes. The factory receives whichever regex matched.

3. Add a label in `ErrorUtil.classify`. Put it **before** `is AIServiceException`, or it will be labelled `AIService`.

4. If the type is not an `AIServiceException` but should be fatal, add it to `ErrorUtil.isFatal`.

### Adding unparseable-response detection

- Add the class's fully qualified name to `unparseableClassNames`.
- Or add a regex to `unparseableMessagePatterns`.

---

## 8. Quick Reference

| Exception                   | Base                  | Fatal | `errorType`        | Typical action                     |
|-----------------------------|-----------------------|-------|--------------------|------------------------------------|
| `RateLimitException`        | `AIServiceException`  | no    | `RateLimit`        | Wait `delay` (check units), retry  |
| `RequestOverloadException`  | `IOException`         | no    | `Overload`         | Exponential backoff, retry         |
| `SafetyException`           | `AIServiceException`  | no*   | `Safety`           | Surface to user / rephrase         |
| `ModelMaxException`         | `AIServiceException`  | yes   | `ModelMax`         | Reduce context / completion size   |
| `QuotaException`            | `AIServiceException`  | yes   | `Quota`            | Stop; billing issue                |
| `InvalidModelException`     | `AIServiceException`  | yes   | `InvalidModel`     | Fix configuration                  |
| `InvalidValueException`     | `AIServiceException`  | yes   | `InvalidValue`     | Fix request parameters             |
| `ModerationException`       | `Exception`           | yes   | `Moderation`       | Surface to user                    |
| `NonRetryableException`     | `RuntimeException`    | yes   | `NonRetryable`     | Stop                               |
| `BudgetException`           | `NonRetryableException` | yes | `Budget`           | Stop; raise budget                 |
| `FailedToImplementException`| `RuntimeException`    | no    | `FailedToImplement`| Inspect `code`, repair or report   |
| `MultiExeption`             | `RuntimeException`    | no    | `Multiple`         | Inspect individual failures        |

\* Not marked fatal, but identical retries are unlikely to succeed.