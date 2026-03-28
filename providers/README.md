# Cognotik Core Module Documentation

## Overview

The Cognotik Core module provides the foundational components for building AI-powered applications. It includes
utilities for interacting with language models, managing application state, handling user sessions, and implementing
various platform services.

## Key Components

### Actors

The `actors` package contains classes that encapsulate interactions with AI models:

- **BaseActor**: Abstract base class for all actors that interact with AI models
- **CodingActor**: Specialized actor for code generation and execution
- **ImageActor**: Actor for generating images from text descriptions
- **ParsedActor**: Actor that parses structured data from AI responses
- **SimpleActor**: Basic actor for text-based interactions
- **LargeOutputActor**: Actor designed for generating and refining long-form content
- **TextToSpeechActor**: Actor for converting text to speech

### Platform Services

The `platform` package provides infrastructure for application state management:

- **ApplicationServices**: Central registry for application-wide services
- **Session**: Represents a user interaction session
- **ClientManager**: Manages API clients for different services

#### Storage Implementations

- **DataStorage**: File-based storage for session data
- **MetadataStorage**: Storage for session metadata
- **HSQLMetadataStorage**: HSQL-based implementation of metadata storage
- **HSQLUsageManager**: HSQL-based implementation for tracking API usage

#### Authentication and Authorization

- **AuthenticationManager**: Manages user authentication
- **AuthorizationManager**: Controls access to application features
- **UserSettingsManager**: Manages user-specific settings

### Utilities

The `util` package contains various utility classes:

- **IterativePatchUtil**: Utilities for generating and applying code patches
- **PythonPatchUtil**: Python-specific patch utilities
- **SimpleDiffApplier**: Simplified diff application
- **FileSelectionUtils**: Utilities for selecting files based on criteria
- **GrammarValidator**: Interface for validating code grammar
- **FunctionWrapper**: Utilities for function interception and recording
- **FixedConcurrencyProcessor**: Controls concurrent task execution

## Interpreters

The `interpreter` package provides an abstraction for code execution:

- **Interpreter**: Interface for executing code in different languages

## Usage Examples

### Creating a Simple Actor

```kotlin
val simpleActor = SimpleActor(
    prompt = "You are a helpful assistant.",
    model = ChatModel.GPT4o,
    temperature = 0.7
)

val response = simpleActor.answer(listOf("What is the capital of France?"), api)
println(response)

```

### Using the CodingActor

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

### Managing Sessions

```kotlin

val session = Session.newGlobalID()

val dataStorage = ApplicationServices.dataStorageFactory(dataStorageRoot)
val sessionDir = dataStorage.getSessionDir(user, session)

dataStorage.updateMessage(user, session, "msg001", "Hello, world!")

val messages = dataStorage.getMessages(user, session)
```

### Applying Patches

```kotlin
val oldCode = """
    function greet() {
        console.log("Hello");
    }
"""

val patch = """
  function greet() {
-     console.log("Hello");
+     console.log("Hello, World!");
  }
"""

val newCode = IterativePatchUtil.applyPatch(oldCode, patch)
println(newCode)
```

## Configuration

The core module can be configured through the `ApplicationServicesConfig` object:

```kotlin

ApplicationServicesConfig.dataStorageRoot = File("/path/to/data")

ApplicationServicesConfig.isLocked = true
```

## Extension Points

The module provides several interfaces that can be implemented to customize behavior:

- **StorageInterface**: For custom storage implementations
- **AuthenticationInterface**: For custom authentication mechanisms
- **AuthorizationInterface**: For custom authorization rules
- **UsageInterface**: For tracking API usage
- **MetadataStorageInterface**: For storing session metadata
- **CloudPlatformInterface**: For cloud storage integration
- **GrammarValidator**: For validating code grammar

## AWS Integration

The module includes AWS integration through the `AwsPlatform` class, which provides:

- S3 file storage
- KMS encryption/decryption

## Thread Safety

The module includes utilities for managing concurrent operations:

- **FixedConcurrencyProcessor**: Limits concurrent task execution
- **ImmediateExecutorService**: Custom executor service for immediate task execution
- **RecordingThreadFactory**: Thread factory that records created threads

## Testing

The module includes comprehensive test classes for all major components, which also serve as usage examples.