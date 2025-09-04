# Developer Guide to Actor Types in Cognotik

## Overview

The Cognotik framework provides a comprehensive set of actor types for building AI-powered applications. Each actor type
is designed for specific use cases and extends the base `BaseActor` class to provide specialized functionality for
different types of AI interactions.

## Base Actor Architecture

All actors inherit from `BaseActor<I, R>`, which defines the core interface:

```kotlin
abstract class BaseActor<I, R>(
    open val prompt: String,
    val name: String? = null,
    val model: TextModel,
    val temperature: Double = 0.3,
)
```

- **I**: Input type
- **R**: Response type
- **prompt**: System prompt that defines the actor's behavior
- **model**: The AI model to use
- **temperature**: Controls randomness in responses (0.0-1.0)

## Actor Types

### 1. SimpleActor

**Purpose**: Basic text-to-text interactions with straightforward prompting.

**Input**: `List<String>` - List of user messages
**Output**: `String` - AI response text

**Use Cases**:

- General Q&A
- Text summarization
- Content generation
- Simple conversational interfaces

**Example**:

```kotlin
val summarizer = SimpleActor(
    prompt = "You are a helpful assistant that summarizes text concisely.",
    model = GPT35Turbo,
    temperature = 0.3
)

val result = summarizer.answer(listOf("Summarize this article: ..."), api)
```

**Key Features**:

- Minimal overhead
- Direct text responses
- Suitable for most basic AI interactions

### 2. ParsedActor<T>

**Purpose**: Structured data extraction and parsing from AI responses.

**Input**: `List<String>` - User messages
**Output**: `ParsedResponse<T>` - Contains both raw text and parsed object

**Use Cases**:

- Data extraction from unstructured text
- Form filling
- Structured content generation
- API response parsing

**Example**:

```kotlin
data class PersonInfo(
    val name: String,
    val age: Int,
    val occupation: String
)

val extractor = ParsedActor<PersonInfo>(
    resultClass = PersonInfo::class.java,
    prompt = "Extract person information from the given text.",
    model = GPT4,
    parsingModel = GPT35Turbo
)

val result = extractor.answer(listOf("John is a 30-year-old engineer"), api)
val person = result.obj // PersonInfo object
```

**Key Features**:

- Automatic JSON parsing with retry logic
- Type-safe object extraction
- Configurable parsing model for cost optimization
- YAML schema generation for better parsing accuracy

**Configuration Options**:

- `deserializerRetries`: Number of parsing attempts (default: 2)
- `parsingModel`: Separate model for parsing (can be cheaper than main model)
- `describer`: Custom type description for better parsing

### 3. CodingActor

**Purpose**: Code generation, execution, and debugging with multiple programming languages.

**Input**: `CodeRequest` - Contains messages, code prefix, and execution settings
**Output**: `CodeResult` - Contains code, execution results, and status

**Use Cases**:

- Code generation from natural language
- Script automation
- Data analysis and visualization
- API integration tasks

**Example**:

```kotlin
val coder = CodingActor(
    interpreterClass = KotlinInterpreter::class,
    symbols = mapOf("api" to myApiClient),
    model = GPT4,
    fallbackModel = GPT35Turbo
)

val request = CodingActor.CodeRequest(
    messages = listOf("Create a function to calculate fibonacci numbers" to Role.user),
    autoEvaluate = true,
    fixIterations = 3
)

val result = coder.answer(request, api)
println(result.code) // Generated code
println(result.result.resultValue) // Execution result
```

**Key Features**:

- Multi-language support (Kotlin, Python, JavaScript, etc.)
- Automatic code execution and validation
- Error correction with iterative fixing
- Symbol injection for API access
- Code formatting and import management

**Configuration Options**:

- `interpreterClass`: Programming language interpreter
- `symbols`: Pre-defined variables and APIs
- `fixIterations`: Number of error correction attempts
- `autoEvaluate`: Whether to execute code automatically
- `codeInterceptor`: Custom code transformation

**Code Execution Flow**:

1. Generate initial code
2. Validate syntax
3. Execute if `autoEvaluate` is true
4. Fix errors iteratively
5. Return final result with status

### 4. ImageActor

**Purpose**: AI-powered image generation from text descriptions.

**Input**: `List<String>` - Text descriptions for image generation
**Output**: `ImageResponse` - Contains both prompt text and generated image

**Use Cases**:

- Content illustration
- Creative image generation
- Prototype mockups
- Visual storytelling

**Example**:

```kotlin
val imageGen = ImageActor(
    prompt = "Create detailed image prompts that will generate high-quality images",
    textModel = GPT4,
    imageModel = ImageModels.DallE3,
    width = 1024,
    height = 1024
)

val result = imageGen.answer(listOf("A serene mountain landscape at sunset"), api)
val image = result.image // BufferedImage
val description = result.text // Enhanced prompt used for generation
```

**Key Features**:

- Two-stage generation (text enhancement + image creation)
- Automatic prompt optimization
- Multiple image model support
- Configurable dimensions
- Prompt length management for model limits

**Configuration Options**:

- `imageModel`: DALL-E 2, DALL-E 3, etc.
- `width`/`height`: Image dimensions
- `textModel`: Model for prompt enhancement
- `openAI`: Separate OpenAI client for image generation
