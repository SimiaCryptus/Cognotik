# Cognotik Core Module

The Cognotik Core Module provides foundational components and utilities for building AI-powered applications, focusing on seamless integration with OpenAI-compatible APIs and advanced processing capabilities.

## Features

- **API Client Management**: Robust clients for interacting with AI services, including support for retries, timeouts, and error handling.
- **Model Support**: Comprehensive support for various AI models from providers such as OpenAI, Anthropic, AWS, Google, Groq, Mistral, Perplexity, ModelsLab, and DeepSeek.
- **Actors Framework**: Abstract and concrete actor classes to handle different AI interaction scenarios, including coding assistants, image generation, text-to-speech, large output handling, and parsed responses.
- **Audio Processing**: Utilities for audio recording, transcription, and silence detection.
- **Text Processing**: Efficient tokenization and compression utilities compatible with GPT-4 tokenizers.
- **Dynamic Enum and Reflection**: Support for dynamic enumerations with JSON serialization and proxy-based API interfaces.
- **Description Utilities**: Tools for generating API documentation and introspection in JSON, YAML, and TypeScript formats.
- **Exception Handling**: Custom exceptions tailored for AI service errors such as rate limits, quota issues, moderation flags, and invalid models.
- **Optimization Tools**: Genetic algorithms for prompt optimization and embedding distance metrics.
- **Platform Services**: File-based and HSQL-backed implementations for authentication, authorization, data storage, metadata management, usage tracking, and user settings.
- **Concurrency Utilities**: Fixed concurrency processors and immediate executor services with thread recording.
- **Grammar Validation**: Validators for Kotlin and general parentheses/braces matching.
- **Patch Utilities**: Advanced diff and patch utilities optimized for code and Python/YAML formats.
- **Logging and Output Interception**: Facilities to intercept and capture system output and logs for debugging and analysis.

## Key Components

- **Actors**: `BaseActor`, `CodingActor`, `ImageActor`, `LargeOutputActor`, `ParsedActor`, `SimpleActor`, `TextToSpeechActor`.
- **Platform**: `ApplicationServices`, `ClientManager`, `AuthenticationManager`, `AuthorizationManager`, `DataStorage`, `MetadataStorage`, `UsageManager`, `UserSettingsManager`.
- **Utilities**: `IterativePatchUtil`, `PythonPatchUtil`, `RuleTreeBuilder`, `OutputInterceptor`, `LoggingInterceptor`, `FunctionWrapper`, `FixedConcurrencyProcessor`.
- **Interpreters**: Interface and test base for code interpreters.
- **Tests**: Comprehensive test suites for platform services and utilities.

## Getting Started

To use the Cognotik Core Module, include it as a dependency in your project. Configure the `ApplicationServices` as needed for your environment, especially for storage and cloud platform integration.

Example usage of a coding actor:

```kotlin
val codingActor = CodingActor(
    interpreterClass = YourInterpreter::class,
    symbols = mapOf("helper" to HelperClass()),
    model = yourTextModel,
    fallbackModel = yourFallbackChatModel
)

val codeRequest = CodingActor.CodeRequest(
    messages = listOf("Write a function to add two numbers" to ApiModel.Role.user),
    autoEvaluate = true
)

val result = codingActor.answer(codeRequest, yourApiClient)
println("Generated code: ${result.code}")
println("Execution result: ${result.result.resultValue}")
```
