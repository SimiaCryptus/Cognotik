# Providers

*The connective tissue between Cognotik and the AI models, storage backends, and platform services that power it.*

## Overview

The Providers module is Cognotik's foundation layer for building AI-powered applications. It supplies ready-to-use
**actors** that wrap language model interactions (text, code, images, speech), a **platform services** layer for
session and state management, and a set of battle-tested **utilities** for tasks like patch generation, file
selection, and concurrency control.

If Cognotik's other modules are the "application," Providers is the runtime they build on: authentication, storage,
usage tracking, and model orchestration all live here, ready to be composed into your own tools and workflows.

## Key Features

- **Actor abstractions for AI models** — `SimpleActor`, `CodingActor`, `ImageActor`, `ParsedActor`,
  `LargeOutputActor`, and `TextToSpeechActor` give you consistent, typed interfaces for text generation, code
  execution, structured data extraction, image generation, and text-to-speech.
- **Session & state management** — `Session`, `ApplicationServices`, and `ClientManager` handle user sessions and API
  clients so you don't have to reinvent session plumbing.
- **Pluggable storage** — file-based `DataStorage`, HSQL-backed metadata and usage tracking, plus interfaces for
  swapping in your own storage backend.
- **Authentication & authorization** — `AuthenticationManager`, `AuthorizationManager`, and `UserSettingsManager`
  provide out-of-the-box access control that you can extend or replace.
- **Code patch utilities** — `IterativePatchUtil`, `PythonPatchUtil`, and `SimpleDiffApplier` apply diff-style patches
  to generated code reliably, across languages.
- **Multi-language code execution** — the `Interpreter` interface abstracts code execution so actors can run and
  evaluate generated code in different runtimes.
- **AWS-ready** — built-in `AwsPlatform` support for S3 storage and KMS encryption, plus Bedrock model integration.
- **Concurrency controls** — `FixedConcurrencyProcessor` and related utilities keep concurrent AI calls and tasks
  bounded and predictable.

## Example

Create an actor and get a model response in a few lines:

```kotlin
val simpleActor = SimpleActor(
    prompt = "You are a helpful assistant.",
    model = ChatModel.GPT4o,
    temperature = 0.7
)

val response = simpleActor.answer(listOf("What is the capital of France?"), api)
println(response)
```

Or generate, execute, and evaluate code directly:

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

## Integration

Providers is designed to sit underneath the rest of the Cognotik stack:

- Depends on **core** and **lwcore** for shared framework primitives.
- Uses **text** and **docops** for document and text processing support.
- Integrates with **AWS Bedrock** for hosted model access, and **HSQLDB** for lightweight embedded metadata/usage
  storage — no external database required to get started.
- Exposes extension points (`StorageInterface`, `AuthenticationInterface`, `AuthorizationInterface`,
  `UsageInterface`, `CloudPlatformInterface`) so teams can swap in enterprise-grade storage, auth, or cloud
  integrations without touching actor code.