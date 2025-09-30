# VectorSearchTask

## Overview

The `VectorSearchTask` is a specialized task implementation that performs semantic similarity searches using vector embeddings. It searches through indexed document embeddings to find content that is semantically similar to provided query strings, supporting both positive queries (what to find) and negative queries (what to avoid).

## Purpose

This task enables semantic search capabilities within the Cognotik framework by:
- Converting search queries into vector embeddings
- Comparing query embeddings against pre-indexed document embeddings
- Ranking results based on semantic similarity
- Supporting both inclusion and exclusion criteria
- Filtering results based on content requirements

## Configuration

### VectorSearchTaskConfigData

The task is configured using the `VectorSearchTaskConfigData` class with the following parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `positive_queries` | `List<String>` | Required | Search queries to find similar content for |
| `negative_queries` | `List<String>` | `emptyList()` | Search queries to avoid in results |
| `distance_type` | `DistanceType` | `Cosine` | Distance metric for embedding comparison (Euclidean, Manhattan, or Cosine) |
| `count` | `Int` | `5` | Number of top results to return |
| `min_length` | `Int` | `0` | Minimum content length requirement |
| `required_regexes` | `List<String>` | `emptyList()` | Regex patterns that must match in content |
| `model` | `EmbeddingModel` | `OllamaNomadic` | Embedding model to use for query vectorization |

## How It Works

### 1. Query Processing
- Validates that at least one positive query is provided
- Converts all positive and negative queries into vector embeddings using the specified model
- Implements retry logic (up to 3 attempts) for embedding creation to handle transient failures

### 2. Search Process
- Scans the configured root directory for `.index.data` files containing pre-computed embeddings
- For each indexed document:
  - Calculates distances between document embedding and all query embeddings
  - Computes minimum distance to positive queries
  - If negative queries exist, adjusts score by dividing by minimum negative distance
  - Applies content filters (minimum length, required regex patterns)

### 3. Result Ranking
- Sorts results by computed distance (lower is better for similarity)
- Returns the top N results as specified by the `count` parameter

### 4. Output Formatting
- Generates markdown-formatted results
- Includes distance scores, file paths, and metadata
- Provides context summaries showing the JSON structure around matched content

## Distance Metrics

The task supports three distance metrics for comparing embeddings:

- **Cosine Distance**: Measures angle between vectors (default, best for semantic similarity)
- **Euclidean Distance**: Measures straight-line distance between points
- **Manhattan Distance**: Measures sum of absolute differences

## Filtering Capabilities

### Content Length Filter
- Excludes results with content shorter than `min_length` characters
- Useful for filtering out trivial matches

### Regex Pattern Matching
- Supports multiple required regex patterns
- All patterns must match for a result to be included
- Useful for domain-specific filtering

## Output Format

Results are formatted as markdown with:
- Numbered result sections
- Distance scores (lower indicates better match)
- Source file paths
- JSON context summaries showing document structure
- Metadata in JSON format

Example output structure:
```markdown
# Embedding Search Results

## Result 1
* Distance: 0.234
* File: path/to/document.json
```json
{
  "context": "...",
  "metadata": {...}
}
```
```

## Error Handling

The task implements robust error handling:
- Retry logic for embedding creation failures
- Graceful handling of missing or corrupted index files
- Validation of required configuration parameters
- Thread pool management with proper shutdown procedures
- Detailed error logging for debugging

## Performance Considerations

- Uses parallel processing with a thread pool (up to 8 threads)
- Streams document records to minimize memory usage
- Implements efficient distance calculations
- Sorts only after all distances are computed

## Use Cases

1. **Semantic Document Search**: Find documents discussing similar concepts
2. **Content Discovery**: Locate related information across large document sets
3. **Duplicate Detection**: Identify semantically similar content
4. **Filtered Search**: Combine semantic search with pattern-based filtering
5. **Negative Filtering**: Exclude certain topics while searching for others

## Integration

The task integrates with:
- `DocumentRecord`: For reading indexed embeddings
- `EmbeddingModel`: For creating query embeddings
- `TaskOrchestrator`: For task execution coordination
- `SessionTask`: For UI interaction and result display

## Limitations

- Requires pre-indexed embeddings in `.index.data` files
- Performance depends on the number and size of index files
- Embedding model must be compatible with indexed embeddings
- Memory usage scales with the number of search results

## Example Configuration

```json
{
  "task_type": "EmbeddingSearch",
  "positive_queries": ["machine learning algorithms", "neural networks"],
  "negative_queries": ["basic statistics"],
  "distance_type": "Cosine",
  "count": 10,
  "min_length": 100,
  "required_regexes": ["\\b(AI|ML|deep learning)\\b"],
  "model": "OllamaNomadic"
}
```

## Best Practices

1. **Query Design**: Use specific, descriptive queries for better results
2. **Distance Metric**: Use Cosine distance for semantic similarity
3. **Negative Queries**: Use sparingly to avoid over-filtering
4. **Result Count**: Balance between coverage and relevance
5. **Regex Patterns**: Test patterns separately before using in searches
6. **Model Selection**: Ensure query and index embeddings use compatible models