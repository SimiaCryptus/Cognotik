# Cognotik Package Documentation

## Overview

The Cognotik package is a comprehensive framework for building AI-powered applications. It provides a collection of
tools, utilities, and pre-built components that enable developers to create sophisticated AI agents, document parsers,
code generators, and more. The package leverages large language models (LLMs) like GPT-4 to provide intelligent
functionality across various domains.

## Core Components

### 1. Code Generation and Analysis

#### CodingAgent (code/CodingAgent.kt)

A versatile agent for generating, executing, and refining code. It can:

- Generate code based on user prompts
- Execute the generated code and display results
- Allow for iterative refinement through user feedback
- Support various programming languages through different interpreters

```kotlin
val codingAgent = CodingAgent(
    api = api,
    dataStorage = dataStorage,
    session = session,
    user = user,
    ui = ui,
    interpreter = KotlinInterpreter::class,
    symbols = mapOf("key" to value),
    model = model
)

codingAgent.start("Generate a function to calculate Fibonacci numbers")
```

#### ShellToolAgent (code/ShellToolAgent.kt)

Extends the CodingAgent to create shell-based tools. It can:

- Generate shell scripts based on user prompts
- Execute shell commands and capture their output
- Create OpenAPI specifications for the generated tools
- Build test pages for the tools

### 2. Document Parsing and Analysis

#### DocumentParserApp (parse/DocumentParserApp.kt)

An application for extracting and analyzing content from various document types:

- Supports PDF, HTML, and text files
- Extracts text and renders images from documents
- Processes documents in batches for efficient handling
- Saves extracted content in various formats

```kotlin
val parserApp = DocumentParserApp(
    applicationName = "Document Extractor",
    parsingModel = DocumentParsingModel(model, temperature),
    reader = { file -> PDFReader(file) }
)
```

#### ParsingModel Implementations

- **DocumentParsingModel**: Parses general documents into structured content
- **CodeParsingModel**: Specializes in parsing code files with syntax awareness
- **LogDataParsingModel**: Extracts patterns and structured data from log files

### 3. Meta-Agent Framework

#### MetaAgentApp (meta/MetaAgentApp.kt)

A powerful tool for creating custom AI agents:

- Guides users through designing their own AI agents
- Generates code for the designed agents
- Supports various actor types (Simple, Parsed, Coding, Image)
- Handles the complete lifecycle from design to implementation

```kotlin
val metaAgent = MetaAgentAgent(
    user = user,
    session = session,
    api = api,
    ui = ui,
    model = model,
    parsingModel = parsingModel,
    temperature = 0.3
)

metaAgent.buildAgent(userMessage = "Create an agent that summarizes news articles")
```

### 4. Graph and Visualization Tools

#### CloudNodeType (graph/CloudNodeType.kt)

Defines a type system for cloud infrastructure resources:

- Represents various cloud resources (Storage, DNS, Load Balancer, etc.)
- Supports serialization and deserialization
- Provides operations for merging and diffing resource configurations

#### SoftwareNodeType (graph/SoftwareNodeType.kt)

Defines a type system for software development artifacts:

- Represents code files, packages, projects, and specifications
- Supports relationships between software components
- Provides operations for merging and diffing software structures

### 5. General Purpose Applications

#### OutlineApp (general/OutlineApp.kt)

Creates and expands outlines for concepts:

- Generates initial outlines from user prompts
- Iteratively expands sections of the outline
- Visualizes relationships between concepts using embeddings
- Produces final essays based on the expanded outline

```kotlin
val outlineAgent = OutlineAgent(
    api = api,
    api2 = api2,
    dataStorage = dataStorage,
    session = session,
    user = user,
    temperature = 0.3,
    models = listOf(model1, model2),
    firstLevelModel = model,
    parsingModel = parsingModel,
    userMessage = userMessage,
    ui = ui
)

outlineAgent.buildMap()
```

#### PatchApp (general/PatchApp.kt)

Fixes code issues based on error messages:

- Analyzes command output for errors
- Identifies files that need to be modified
- Generates patches to fix the identified issues
- Applies patches automatically or with user approval

#### UnifiedPlanApp (general/UnifiedPlanApp.kt)

A flexible planning application that supports different cognitive modes:

- Adapts to different planning and execution strategies
- Processes messages with expansion expressions
- Creates multiple variants of plans based on user input

## Utilities and Support Classes

### Document Readers

- **PDFReader**: Extracts text and images from PDF files
- **HTMLReader**: Parses HTML documents into text
- **TextReader**: Handles plain text files with line number support

### Actor Designers

Various classes for designing different types of actors:

- **SimpleActorDesigner**: Creates basic conversational actors
- **ParsedActorDesigner**: Creates actors that produce structured data
- **CodingActorDesigner**: Creates actors that generate and execute code
- **ImageActorDesigner**: Creates actors that generate images

## Integration and Extension

### Creating Custom Applications

To create a custom application using the Cognotik framework:

1. Extend one of the base application classes (e.g., `ApplicationServer`)
2. Configure the required components (models, settings, etc.)
3. Implement the `userMessage` method to handle user input
4. Use the provided UI components to display results

Example:

```kotlin
class MyCustomApp(
    applicationName: String = "My Custom App",
    path: String = "/myCustomApp",
    val model: ChatModel
) : ApplicationServer(
    applicationName = applicationName,
    path = path
) {
    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        // Process the user message and display results
        val task = ui.newTask()
        task.add("Processing your request...")

        // Use Cognotik components
        // ...

        task.complete("Done!")
    }
}
```

### Extending Existing Components

Most components in the Cognotik package are designed to be extensible:

- Override methods to customize behavior
- Implement new parsing models for specific document types
- Create custom actor types for specialized tasks
- Develop new cognitive modes for the unified planning app

## Best Practices

1. **Resource Management**: Close resources properly, especially when working with document readers
2. **Error Handling**: Implement proper error handling to provide meaningful feedback to users
3. **Concurrency**: Be mindful of concurrent operations, especially when processing large documents
4. **Model Selection**: Choose appropriate models for different tasks (e.g., smaller models for parsing, larger models
   for generation)
5. **UI Feedback**: Provide clear progress indicators and feedback during long-running operations

## Conclusion

The Cognotik package provides a powerful and flexible framework for building AI-powered applications. By leveraging its
components and utilities, developers can create sophisticated agents, parsers, and tools that harness the capabilities
of large language models to solve complex problems across various domains.
