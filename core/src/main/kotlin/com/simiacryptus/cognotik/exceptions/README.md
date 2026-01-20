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