# Text Block Utilities

This package provides a robust framework for parsing, manipulating, and formatting structured text blocks, specifically
focusing on indentation management and code comment styles (line and block comments).

## Core Components

### [TextBlock.kt](./TextBlock.kt)

The base interface for all text block types. It defines the fundamental contract for text that can be represented as a
series of lines and re-indented.

- **Constants**:
    - `TAB_REPLACEMENT`: Defaults to two spaces ("  ").
    - `DELIMITER`: The standard newline character (`\n`).
- **Methods**:
    - `rawString()`: Returns the lines of text without the block-level indentation.
    - `withIndent(indent)`: Returns a new instance with the specified indentation.

### [IndentedText.kt](./IndentedText.kt)

A foundational implementation of `TextBlock` that manages a collection of lines with a shared whitespace prefix.

- **Functionality**: Automatically detects the common indentation prefix when created via `fromString`.
- **Indentation Management**: Provides tools to strip and re-apply indentation consistently across multiple lines.

### [BlockComment.kt](./BlockComment.kt)

Specialized class for handling multi-line block comments (e.g., `/* ... */`).

- **Structure**: Manages a `blockPrefix`, a `linePrefix` (applied to each line within the block), and a `blockSuffix`.
- **Factory**: Includes a `Factory` that can identify if a string "looks like" a block comment and parse it into its
  constituent parts, stripping away the comment syntax to retrieve the raw text.

### [LineComment.kt](./LineComment.kt)

Specialized class for handling sequences of line comments (e.g., `// ...`).

- **Structure**: Manages a `commentPrefix` that is expected at the start of every line.
- **Factory**: Includes a `Factory` that validates if all lines in a block start with the specified prefix and parses
  them into a single logical text block.

### [TextBlockFactory.kt](./TextBlockFactory.kt)

A generic interface for creating `TextBlock` instances from raw strings.

- **Methods**:
    - `fromString(text)`: Parses a string into a specific `TextBlock` implementation.
  - `looksLike(text)`: A heuristic method used to determine if a given string matches the format handled by the
    factory (e.g., checking for comment delimiters).

## Usage Patterns

### Parsing and Re-indenting

The utilities are designed to handle the common task of taking a block of code or text, identifying its current
indentation level, and shifting it to a new level while preserving internal formatting.

```kotlin
val rawText = """
    /**
     * This is a comment
     * with multiple lines.
     */
""".trimIndent()

val factory = BlockComment.Factory("/**", "*", "*/")
if (factory.looksLike(rawText)) {
    val comment = factory.fromString(rawText)
    val reIndented = comment.withIndent("  ")
    println(reIndented.toString())
}
```

### Implementation Details

- **Tab Handling**: All implementations consistently replace tab characters with `TextBlock.TAB_REPLACEMENT` to ensure
  predictable indentation calculations.
- **Whitespace Preservation**: The logic distinguishes between the "indentation" (the common prefix shared by all lines)
  and the "content" of the lines.