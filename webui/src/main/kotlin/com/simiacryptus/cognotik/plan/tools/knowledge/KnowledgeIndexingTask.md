# KnowledgeIndexingTask

## Overview

The `KnowledgeIndexingTask` is a specialized task implementation that processes and indexes files for semantic search capabilities. It leverages embedding models to create searchable vector representations of document content, enabling efficient knowledge retrieval in AI-powered applications.

## Purpose

This task is designed to:
- Process multiple file paths for indexing
- Create embeddings using the OllamaNomadic embedding model
- Support concurrent processing for improved performance
- Provide progress tracking during indexing operations

## Configuration

### KnowledgeIndexingTaskConfigData

The task configuration accepts the following parameters:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file_paths` | `List<String>` | Yes | List of file paths to process and index |
| `task_description` | `String?` | No | Optional description of the task |
| `task_dependencies` | `List<String>?` | No | Optional list of dependent task identifiers |
| `state` | `TaskState?` | No | Current state of the task |

## Usage

### Example Configuration

```kotlin
val config = KnowledgeIndexingTaskConfigData(
    file_paths = listOf(
        "/path/to/document1.pdf",
        "/path/to/document2.txt",
        "/path/to/codebase/src"
    ),
    task_description = "Index project documentation for semantic search"
)
```

### Prompt Segment

When using this task in an orchestration context, the following prompt segment is provided:

```
KnowledgeIndexingTask - Process and index files for semantic search
  ** Specify the file paths to process
  ** Specify the parsing type (document or code)
  ** Optionally specify the chunk size (default 0.1)
```

## Implementation Details

### Processing Flow

1. **File Validation**: The task first validates that all specified file paths exist
2. **Thread Pool Creation**: Creates a thread pool with up to 16 threads (based on available processors)
3. **Indexing**: Uses the `indexJsonFile` method to process files with:
   - Progress tracking via `ProgressState`
   - OllamaNomadic embedding model for vector generation
4. **Result Reporting**: Generates a markdown report of processed files
5. **Cleanup**: Ensures proper thread pool shutdown

### Error Handling

- **Missing Files**: Files that don't exist are filtered out with warnings logged
- **Empty File List**: If no valid files are found, returns a detailed error report
- **Thread Interruption**: Properly handles thread interruption and ensures cleanup

### Performance Considerations

- **Concurrent Processing**: Utilizes multi-threading for parallel file processing
- **Thread Pool Size**: Limited to 16 threads maximum to prevent resource exhaustion
- **Graceful Shutdown**: Implements a 60-second timeout for thread pool termination

## Output Format

The task generates markdown-formatted output with two possible outcomes:

### Success Output
```markdown
# Knowledge Indexing Complete

Processed N files:
* file1.txt
* file2.pdf
* ...
```

### Error Output (No Valid Files)
```markdown
# No Valid Files Found

The following paths were specified but could not be found:
* /invalid/path1
* /invalid/path2
```

## Dependencies

- `com.simiacryptus.cognotik.apps.parse.DocumentRecord`: For file indexing functionality
- `com.simiacryptus.cognotik.embedding.EmbeddingModel`: For vector embeddings
- `com.simiacryptus.cognotik.util.MarkdownUtil`: For rendering markdown output

## Limitations and Notes

1. **Fixed Embedding Model**: Currently hardcoded to use `EmbeddingModel.OllamaNomadic`
2. **Chunk Size**: The prompt mentions chunk size configuration, but it's not exposed in the current implementation
3. **Parsing Type**: The prompt mentions document vs code parsing types, but this is not configurable in the current version

## Future Enhancements

Consider implementing:
- Configurable embedding models
- Adjustable chunk size parameter
- Document vs code parsing type selection
- Support for recursive directory processing
- File type filtering options
- Custom metadata extraction