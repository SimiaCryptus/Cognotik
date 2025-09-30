# FileSearchTask

## Overview

The `FileSearchTask` is a specialized task implementation that performs pattern-based searches across project files with contextual results. It supports both substring and regex search patterns, provides configurable context lines around matches, and presents results in an organized, readable format.

## Purpose

This task enables users to:
- Search for specific patterns or text across multiple files in a project
- Use either simple substring matching or complex regex patterns
- View search results with surrounding context for better understanding
- Extract and search content from non-text files (PDF, HTML, etc.) when needed
- Filter searches to specific files or file patterns using glob syntax

## Configuration

### SearchTaskConfigData

The task is configured using the `SearchTaskConfigData` class with the following parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `search_pattern` | String | "" | The search pattern (substring or regex) to look for in the files |
| `is_regex` | Boolean | false | Whether the search pattern is a regex (true) or a substring (false) |
| `context_lines` | Int | 2 | The number of context lines to include before and after each match |
| `input_files` | List<String>? | null | The specific files (or file patterns) to be searched |
| `extractContent` | Boolean | false | Whether to extract and search text content from non-text files (PDF, HTML, etc.) |
| `task_description` | String? | null | Optional description of the task |
| `task_dependencies` | List<String>? | null | Optional list of task dependencies |
| `state` | TaskState? | null | Current state of the task |

## Features

### Search Capabilities

1. **Pattern Types**
   - **Substring Search**: Simple text matching (default)
   - **Regex Search**: Complex pattern matching using Java regex syntax

2. **File Selection**
   - Supports glob patterns for file selection (e.g., `*.kt`, `src/**/*.java`)
   - Automatically filters out LLM-ignored files
   - Respects project file selection utilities

3. **Context Display**
   - Shows configurable number of lines before and after each match
   - Groups nearby matches into combined context blocks to avoid redundancy
   - Preserves line numbers from original files

4. **Content Extraction**
   - Can extract searchable text from non-text files (PDF, HTML, etc.)
   - Automatically detects text file types based on extension

### Output Format

The search results are formatted as markdown with:
- Summary of total matches and files
- Results grouped by file
- Line numbers for easy reference
- Visual indicators (>) for matched lines
- Code blocks for context display

## Implementation Details

### Key Components

1. **Data Structures**
   - `RawMatch`: Represents a single match with line number and content
   - `DisplayBlock`: Groups related matches with their context
   - `MatchInBlock`: Maps matches to their position within a display block

2. **Search Process**
   ```
   1. Parse search pattern (substring or regex)
   2. Iterate through specified files/patterns
   3. Read file content (or extract if needed)
   4. Find all matches in each file
   5. Group matches into context blocks
   6. Format results as markdown
   ```

3. **Context Grouping Algorithm**
   - Merges overlapping or adjacent context windows
   - Minimizes redundant display of lines
   - Preserves all match locations

### Text File Detection

The following extensions are recognized as text files:
- Programming: `kt`, `java`, `js`, `ts`, `py`, `rb`, `go`, `rs`, `c`, `cpp`, `h`, `hpp`
- Web: `css`, `html`, `xml`, `json`
- Configuration: `yaml`, `yml`, `properties`, `gradle`, `maven`
- Documentation: `txt`, `md`

## Usage Examples

### Basic Substring Search
```kotlin
SearchTaskConfigData(
    search_pattern = "TODO",
    is_regex = false,
    context_lines = 2,
    input_files = listOf("src/**/*.kt")
)
```

### Regex Pattern Search
```kotlin
SearchTaskConfigData(
    search_pattern = "\\bclass\\s+\\w+Task\\b",
    is_regex = true,
    context_lines = 3,
    input_files = listOf("**/*.kt", "**/*.java")
)
```

### Search with Content Extraction
```kotlin
SearchTaskConfigData(
    search_pattern = "configuration",
    is_regex = false,
    context_lines = 1,
    input_files = listOf("docs/**/*"),
    extractContent = true
)
```

## Output Example

```markdown
# Search Results

Found 3 match(es) in 2 file(s).

## src/main/kotlin/Example.kt

### Lines 10 - 14

```
    10: class ExampleClass {
>   11:     // TODO: Implement this method
    12:     fun doSomething() {
    13:         println("Not implemented")
    14:     }
```

### Lines 25 - 29

```
    25:     fun anotherMethod() {
    26:         val result = calculate()
>   27:         // TODO: Add error handling
    28:         return result
    29:     }
```

## src/test/kotlin/ExampleTest.kt

### Lines 5 - 9

```
    5: class ExampleTest {
    6:     @Test
>   7:     fun testSomething() {
>   8:         // TODO: Write actual test
    9:         assertTrue(true)
```
```

## Error Handling

The task handles various error scenarios:
- Invalid regex patterns are caught and reported
- File reading errors are logged with warnings
- Missing or inaccessible files are skipped
- Results are truncated if they exceed the maximum length limit

## Performance Considerations

1. **File Walking**: Uses filtered walk to avoid processing ignored files
2. **Memory Usage**: Processes files line-by-line when possible
3. **Result Truncation**: Limits output size to prevent memory issues (default 500KB)
4. **Context Grouping**: Reduces redundant display by merging overlapping contexts

## Integration

The `FileSearchTask` integrates with:
- `TaskOrchestrator`: For task execution and coordination
- `FileSelectionUtils`: For file filtering and selection
- `AbstractFileTask`: For content extraction capabilities
- `MarkdownUtil`: For rendering formatted results

## Limitations

- Binary files are not searchable unless content extraction is enabled
- Large files may impact performance
- Complex regex patterns may be slow on large codebases
- Context display is limited to prevent excessive output size