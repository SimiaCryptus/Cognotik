# Cognotik Core

*The foundation layer for building AI-powered JVM applications — model actors, session state, storage, and patching, all in one library.*

## Overview

Cognotik Core (`lwcore`) is the base library that every Cognotik application builds on. It gives you a
consistent, type-safe way to talk to language models, manage multi-user sessions and their persisted state, apply
AI-generated code patches safely, and plug in your own storage, authentication, and cloud backends.

If you're building a chat app, coding assistant, or any tool that orchestrates LLM calls with real application
state, Core handles the plumbing so you can focus on prompts and product logic.

## Key Features

- **Actor abstractions for LLM interaction** — `SimpleActor` for text, `CodingActor` for code generation with
  execution/evaluation, `ImageActor` for image generation, `ParsedActor` for structured output, `LargeOutputActor`
  for long-form content, and `TextToSpeechActor` for audio.
- **Session and state management** — `Session`, `ApplicationServicesImpl`, and `ClientManager` provide a central
  registry for per-user, per-session application state and API clients.
- **Pluggable storage** — file-based `DataStorage` and `MetadataStorage` out of the box, with HSQL-backed
  implementations (`HSQLMetadataStorage`, `HSQLUsageManager`) for tracking usage and metadata.
- **Authentication & authorization hooks** — `AuthenticationManager`, `AuthorizationManager`, and
  `UserSettingsManager` give you extension points instead of a hardcoded security model.
- **Patch generation and application** — `IterativePatchUtil`, `PythonPatchUtil`, and `SimpleDiffApplier` let you
  generate and safely apply unified-diff style patches from AI output.
- **Multi-language code execution** — the `Interpreter` interface abstracts running generated code across
  languages (e.g., Kotlin).
- **Concurrency utilities** — `FixedConcurrencyProcessor`, `ImmediateExecutorService`, and
  `RecordingThreadFactory` for controlled, observable concurrent execution.
- **Cloud-ready** — built-in AWS integration (`AwsPlatform`) for S3 storage and KMS encryption/decryption, plus a
  `CloudPlatformInterface` for adding others.

## Example

Ask an LLM to write and evaluate code with `CodingActor`:

```kotlin
val codingActor = CodingActor(
    interpreterClass = KotlinInterpreter::class,
    model = ChatModel.GPT4o,
    fallbackModel = ChatModel.GPT35Turbo,
    temperature = 0.1
)

val codeRequest = CodingActor.CodeRequest(
    messages = listOf("Write a function to calculate the factorial of a number" to ApiModel.Role.user),
    autoEvaluate = true
)

val result = codingActor.answer(codeRequest, api)
println(result.code)
println(result.result.resultValue)
```

Apply an AI-generated patch to existing source:

```kotlin
val newCode = IterativePatchUtil.applyPatch(oldCode, patch)
println(newCode)
```

## Integration & Dependencies

Core is JVM-native (Kotlin + Java interop) and pulls in a curated, production-grade dependency set rather than
reinventing infrastructure:

- **Persistence & data**: HSQLDB, Jackson (JSON/YAML/XML/TOML/Properties), Apache POI, PDFBox, Commons CSV
- **Networking**: Apache HttpClient5, Jsoup
- **Cloud**: AWS SDK (Bedrock, Bedrock Runtime, STS/SSO, S3, KMS) via the AWS BOM
- **Language tooling**: ANTLR runtime, Kotlin coroutines, Groovy
- **Google GenAI SDK** for additional model access

Core has no dependency on other Cognotik modules — it's the layer everything else (UI, plugins, agents) is built
on top of, making it the natural starting point when integrating Cognotik into an existing JVM project.