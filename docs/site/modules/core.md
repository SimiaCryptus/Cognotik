# Core

*The foundation layer for building AI-powered applications on the JVM.*

## Overview

Core is the engine room of Cognotik. It provides the building blocks every AI application needs: a consistent way to
talk to language models, manage user sessions and application state, persist data, authenticate users, and safely
apply AI-generated code changes. If you're building an agent, chatbot, or coding assistant on Cognotik, this is the
module you'll depend on directly.

Core is designed to be provider-agnostic and storage-agnostic — swap in your own authentication, storage, or cloud
backend without rewriting business logic.

## Key Features

- **Actor abstractions** for common AI interaction patterns:
    - `SimpleActor` — straightforward text-in, text-out prompting
    - `CodingActor` — code generation with built-in execution and evaluation
    - `ImageActor` — text-to-image generation
    - `ParsedActor` — structured data extraction from model responses
    - `LargeOutputActor` — iterative generation and refinement of long-form content
    - `TextToSpeechActor` — text-to-speech conversion
- **Session and state management** via `ApplicationServicesImpl`, `Session`, and `ClientManager`, so multi-user,
  multi-session applications work out of the box.
- **Pluggable storage** — file-based `DataStorage` and metadata storage, with an HSQL-backed implementation included
  and a `StorageInterface` for bringing your own.
- **Authentication & authorization hooks** (`AuthenticationManager`, `AuthorizationManager`, `UserSettingsManager`)
  that you can back with your own identity provider.
- **Code patch utilities** (`IterativePatchUtil`, `PythonPatchUtil`, `SimpleDiffApplier`) for reliably applying
  AI-generated diffs to real source files.
- **Concurrency controls** (`FixedConcurrencyProcessor`, custom executors) for safely running multiple model calls or
  tool executions in parallel.
- **AWS integration** out of the box — S3-backed file storage and KMS encryption via `AwsPlatform`.
- **Pluggable code execution** through the `Interpreter` interface, supporting multiple languages.

## Example

Spin up a simple AI actor and get a response in a few lines:

```kotlin
val simpleActor = SimpleActor(
    prompt = "You are a helpful assistant.",
    model = ChatModel.GPT4o,
    temperature = 0.7
)

val response = simpleActor.answer(listOf("What is the capital of France?"), api)
println(response)
```

Generate code, execute it, and inspect the result:

```kotlin
val codingActor = CodingActor(
    interpreterClass = KotlinInterpreter::class,
    model = ChatModel.GPT4o,
    fallbackModel = ChatModel.GPT35Turbo,
    temperature = 0.1
)

val result = codingActor.answer(
    CodingActor.CodeRequest(
        messages = listOf("Write a function to calculate the factorial of a number" to ApiModel.Role.user),
        autoEvaluate = true
    ),
    api
)
println(result.code)
println(result.result.resultValue)
```

Apply an AI-generated patch directly to source code:

```kotlin
val newCode = IterativePatchUtil.applyPatch(oldCode, patch)
```

## Integration

Core builds directly on top of Cognotik's foundational libraries:

- **`text`** — shared text-processing utilities used across actors and patch tools.
- **`lwcore`** — lightweight core primitives shared across the platform.

It's implemented in pure Kotlin/JVM with optional integrations for AWS (S3, KMS, Bedrock), HSQLDB for metadata
storage, and standard document formats (PDF, Office, ODF) for content processing — no Python runtime required.