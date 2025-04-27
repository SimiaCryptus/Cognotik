# Kotlin Diff Utilities Package Documentation

## Overview

The `com.simiacryptus.diff` package provides a comprehensive set of utilities for generating, displaying, and applying
differences (diffs) between text files. This package is particularly useful for code editing applications, version
control systems, and collaborative development environments where tracking and applying changes is essential.

The package includes several key components:

- Diff generation and formatting utilities
- Patch application tools
- Interactive UI components for applying diffs
- Support for both line-by-line and character-level diff operations

## Core Components

### DiffUtil

`DiffUtil` is the central class for generating and formatting differences between text files.

```kotlin
object DiffUtil {
    fun generateDiff(original: List<String>, modified: List<String>): List<PatchLine>
    fun formatDiff(patchLines: List<PatchLine>, contextLines: Int = 3): String
}
```

#### Key Features:

- Generates a list of `PatchLine` objects representing differences between original and modified texts
- Categorizes changes as additions, deletions, or unchanged lines
- Formats diffs with proper context for readability
- Provides detailed logging for debugging and analysis

#### Example Usage:

```kotlin
val original = File("original.txt").readLines()
val modified = File("modified.txt").readLines()
val diff = DiffUtil.generateDiff(original, modified)
val formattedDiff = DiffUtil.formatDiff(diff)
println(formattedDiff)
```

### PatchLine and PatchLineType

These data structures represent individual lines in a diff:

```kotlin
enum class PatchLineType {
    Added, Deleted, Unchanged
}

data class PatchLine(
    val type: PatchLineType,
    val lineNumber: Int,
    val line: String,
    val compareText: String = line.trim()
)
```

### PatchResult

`PatchResult` encapsulates the outcome of applying a patch:

```kotlin
data class PatchResult(
    val newCode: String,
    val isValid: Boolean,
    val error: String? = null
)
```

### ApxPatchUtil

`ApxPatchUtil` provides approximate patching capabilities, useful when exact matches aren't possible:

```kotlin
object ApxPatchUtil {
    fun patch(source: String, patch: String): String
}
```

#### Key Features:

- Handles fuzzy matching for more flexible patch application
- Uses Levenshtein distance to find similar lines when exact matches aren't available
- Provides context-aware patching for better results

### DiffMatchPatch

`DiffMatchPatch` is a port of Google's diff-match-patch library, offering character-level diff operations:

```kotlin
class DiffMatchPatch {
    fun diff_main(text1: String?, text2: String?, checklines: Boolean = true): LinkedList<Diff>
    fun patch_make(text1: String?, text2: String?): LinkedList<Patch>
    fun patch_apply(patches: LinkedList<Patch>, text: String): Array<Any>
}
```

#### Key Features:

- Character-level diff generation
- Semantic cleanup to produce more meaningful diffs
- Patch creation and application
- Support for fuzzy matching

## UI Integration Components

### AddApplyDiffLinks

`AddApplyDiffLinks` enhances markdown content by adding interactive UI elements for applying diffs:

```kotlin
class AddApplyDiffLinks {
    fun apply(
        socketManagerBase: SocketManagerBase,
        code: () -> String,
        response: String,
        handle: (String) -> Unit,
        task: SessionTask,
        ui: ApplicationInterface,
        shouldAutoApply: Boolean = false
    ): String
}
```

#### Key Features:

- Parses markdown content to identify diff blocks
- Adds interactive buttons to apply diffs
- Provides preview functionality to see the result before applying
- Supports automatic application of diffs when configured

### AddApplyFileDiffLinks

`AddApplyFileDiffLinks` extends the functionality to work with file systems:

```kotlin
class AddApplyFileDiffLinks {
    fun instrument(
        self: SocketManagerBase,
        root: Path,
        response: String,
        handle: (Map<Path, String>) -> Unit = {},
        ui: ApplicationInterface,
        api: API,
        shouldAutoApply: (Path) -> Boolean = { false },
        model: ChatModel? = null,
        defaultFile: String? = null
    ): String
}
```

#### Key Features:

- Works with file paths to apply diffs to actual files
- Provides file-specific UI elements for diff application
- Supports automatic application of diffs to files
- Includes verification and preview capabilities
- Offers "bottom-to-top" application for special cases
- Provides revert functionality to undo changes

## Advanced Features

### Iterative Patching

The package includes `IterativePatchUtil` (documented separately) which provides a sophisticated algorithm for
generating and applying patches between two versions of textual content. This utility is particularly useful for
handling complex code changes while maintaining structural integrity.

Key capabilities include:

- Bracket nesting awareness
- Context-sensitive patching
- Intelligent line linking between versions
- Handling of moved code blocks

### Diff Validation

The package includes validation capabilities to ensure that applied diffs result in valid code:

- Syntax validation for various programming languages
- Error reporting with line numbers and descriptions
- Preview functionality to check results before applying changes

### Fuzzy Matching

For situations where exact matches aren't possible, the package provides fuzzy matching capabilities:

- Levenshtein distance calculations for finding similar lines
- Configurable threshold for match acceptance
- Special handling for whitespace and formatting differences

## Integration Examples

### Basic Diff Generation and Application

```kotlin

val original = "function add(a, b) {\n  return a + b;\n}"
val modified = "function add(a, b) {\n  const sum = a + b;\n  return sum;\n}"
val patchLines = DiffUtil.generateDiff(original.lines(), modified.lines())
val formattedDiff = DiffUtil.formatDiff(patchLines)

val result = ApxPatchUtil.patch(original, formattedDiff)
```

### Interactive UI Integration

```kotlin

val enhancedMarkdown = AddApplyDiffLinks.addApplyDiffLinks(
    socketManager,
    { currentCode },
    markdownWithDiffs,
    { newCode -> updateCodeFile(newCode) },
    currentTask,
    uiInterface
)
```

### File-Based Diff Application

```kotlin

val enhancedMarkdown = AddApplyFileDiffLinks.instrumentFileDiffs(
    socketManager,
    projectRoot,
    markdownWithFileDiffs,
    { fileChanges -> applyFileChanges(fileChanges) },
    uiInterface,
    apiClient,
    { path -> shouldAutoApplyToFile(path) }
)
```

## Best Practices

1. **Validation Before Application**: Always validate patches before applying them to important code.
2. **Context Preservation**: Use appropriate context line settings to ensure diffs have sufficient context.
3. **Error Handling**: Implement proper error handling for cases where patches cannot be applied cleanly.
4. **Backup Original**: Keep a backup of the original text before applying patches.
5. **UI Feedback**: Provide clear feedback to users about the success or failure of patch applications.

## Conclusion

The `com.simiacryptus.diff` package provides a robust set of tools for working with text differences and patches. From
low-level diff generation to high-level UI integration, this package offers comprehensive support for implementing
diff-related functionality in Kotlin applications.
