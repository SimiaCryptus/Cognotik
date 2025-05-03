# Cognotik Utility Package Documentation

## Overview

The `com.simiacryptus.cognotik.util` package provides a collection of utility classes that support various
functionalities within the Cognotik application framework. These utilities handle tasks such as rendering markdown,
managing tabbed displays, simplifying HTML, encrypting files, working with OpenAPI specifications, and providing
interactive UI components.

## Core Components

### TabbedDisplay

`TabbedDisplay` is a base class for creating tabbed interfaces in the UI.

```kotlin

```

**Key Features:**

- Manages a collection of tabs with content
- Renders tab buttons and content areas
- Supports tab selection, addition, and removal
- Updates the display when tab content changes

**Example Usage:**

```kotlin
val display = TabbedDisplay(sessionTask)
display.set("Tab 1", "Content for tab 1")
display.set("Tab 2", "Content for tab 2")
display.update()
```

### Retryable

`Retryable` extends `TabbedDisplay` to provide retry functionality for operations that might fail.

```kotlin

```

**Key Features:**

- Executes a process and displays its result
- Provides a retry button to re-execute the process
- Maintains history of attempts in separate tabs

### Discussable

`Discussable` implements an interactive discussion interface where users can provide feedback on generated content.

```kotlin

```

**Key Features:**

- Generates an initial response based on user input
- Allows users to provide feedback
- Revises responses based on feedback
- Maintains conversation history
- Provides accept/retry functionality

### MarkdownUtil

`MarkdownUtil` provides utilities for rendering Markdown content to HTML.

```kotlin

```

**Key Features:**

- Converts Markdown to HTML
- Renders Mermaid diagrams
- Supports tabbed view of rendered content and source
- Handles code blocks and syntax highlighting

### HtmlSimplifier

`HtmlSimplifier` provides methods to clean and simplify HTML content.

```kotlin

```

**Key Features:**

- Removes unsafe elements and attributes
- Simplifies HTML structure
- Converts relative URLs to absolute URLs
- Cleans up text nodes and whitespace
- Configurable preservation of different element types

### Selenium2S3

`Selenium2S3` extends Selenium functionality to save web content to S3 storage.

```kotlin

```

**Key Features:**

- Navigates to URLs using Selenium
- Captures page content and resources
- Saves HTML, JavaScript, and media files to S3
- Processes and transforms links for archived content

### TensorflowProjector

`TensorflowProjector` generates visualizations of embeddings using TensorFlow Projector.

```kotlin

```

**Key Features:**

- Generates embeddings for text using OpenAI API
- Creates TSV files for vectors and metadata
- Configures TensorFlow Projector visualization
- Embeds the visualization in an iframe

### OpenAPI

The `OpenAPI` data classes provide a structured representation of OpenAPI specifications.

```kotlin

```

**Key Features:**

- Comprehensive data model for OpenAPI 3.0.0
- Supports paths, operations, schemas, and other OpenAPI components
- JSON serialization support with Jackson annotations

### EncryptFiles

`EncryptFiles` provides utilities for encrypting files.

```kotlin

```

**Key Features:**

- Encrypts file content using AWS KMS
- Writes encrypted content to files

## Extension Functions

The package includes several useful extension functions:

- `StringBuilder.set(newValue: String)`: Replaces the content of a StringBuilder
- `String.write(outpath: String)`: Writes a string to a file
- `String.encrypt(keyId: String)`: Encrypts a string using AWS KMS

## Usage Patterns

### Creating a Tabbed Interface

```kotlin
val display = TabbedDisplay(sessionTask)
display.set("Tab 1", "Content for tab 1")
display.set("Tab 2", "Content for tab 2")
display.update()
```

### Rendering Markdown

```kotlin
val html = MarkdownUtil.renderMarkdown(markdownText, ui = applicationInterface)
```

### Simplifying HTML

```kotlin
val cleanHtml = HtmlSimplifier.scrubHtml(
  htmlContent,
  baseUrl = "https://example.com",
  simplifyStructure = true,
  keepScriptElements = false
)
```

### Creating an Interactive Discussion

```kotlin
val discussable = Discussable(
  task = sessionTask,
  userMessage = { "Initial user message" },
  initialResponse = { msg -> generateResponse(msg) },
  outputFn = { response -> formatResponse(response) },
  ui = applicationInterface,
  reviseResponse = { history -> generateRevisedResponse(history) },
  heading = "Discussion Title"
)
val result = discussable.call()
```

### Visualizing Embeddings

```kotlin
val projector = TensorflowProjector(
  api = openAIClient,
  dataStorage = storageInterface,
  sessionID = session,
  session = applicationInterface,
  userId = user
)
val html = projector.writeTensorflowEmbeddingProjectorHtml("word1", "word2", "word3")
```

### Saving Web Content

```kotlin
val selenium = Selenium2S3(cookies = cookies)
selenium.save(
  url = URL("https://example.com"),
  currentFilename = "example.html",
  saveRoot = "archived-content"
)
```

## Integration Points

These utilities are designed to work with other components of the Cognotik framework:

- `SessionTask`: For managing UI updates and task state
- `ApplicationInterface`: For interacting with the application
- `StorageInterface`: For storing and retrieving data
- `OpenAIClient`: For generating embeddings and AI responses
- `Cloud Storage`: For storing and serving content

## Best Practices

1. Use `TabbedDisplay` for organizing complex UI content
2. Leverage `Discussable` for interactive AI-assisted workflows
3. Apply `HtmlSimplifier` before displaying user-generated HTML
4. Use `MarkdownUtil` for rendering markdown with proper formatting
5. Implement `Retryable` for operations that might fail
6. Use `TensorflowProjector` for visualizing vector embeddings

This package provides a robust set of utilities that enhance the functionality and user experience of Cognotik
applications.
