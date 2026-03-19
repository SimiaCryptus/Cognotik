# Text Processing Utilities

This package provides high-performance text processing utilities designed for efficient indexing, searching, and
compression of large text bodies. The core of the package is built around Suffix Array data structures and
memory-efficient character sequences.

## Key Components

### [FlyweightCharSequence](FlyweightCharSequence.kt)

A memory-efficient implementation of `CharSequence` that references a sub-sequence of a backing `String` without
performing any data copying. This is used extensively during suffix array construction and comparison to minimize memory
overhead.

### [SuffixArray](SuffixArray.kt)

The foundation for advanced text operations. It constructs a sorted array of all suffixes of a given text.

- **Construction**: Uses `FlyweightCharSequence` for efficient lexicographical sorting.
- **LCP Array**: Implements **Kasai's algorithm** to build the Longest Common Prefix (LCP) array in $O(n)$ time, which
  is essential for identifying repeating patterns.

### [FullTextSearcher](FullTextSearcher.kt)

A high-speed search utility that leverages the `SuffixArray` to perform substring queries.

- **Operations**: Supports `contains`, `findAll`, and `countOccurrences`.
- **Performance**: Uses binary search over the suffix array, providing $O(m \log n)$ search time (where $m$ is pattern
  length and $n$ is text length).

### [TextCompressor](TextCompressor.kt)

An intelligent text abbreviation tool that identifies repeated subsequences and replaces redundant occurrences with a
compact representation (e.g., `prefix...suffix`).

- **Logic**: Uses the LCP array to find candidate repeating strings of a minimum length.
- **Preservation**: Ensures that the first occurrence is preserved while subsequent occurrences are abbreviated,
  provided the abbreviation actually reduces the total character count.
- **Overlap Protection**: Includes logic to prevent overlapping replacements.

## Usage Examples

### Searching Text

```kotlin
val text = "The quick brown fox jumps over the lazy dog. The dog was not impressed."
val searcher = FullTextSearcher(text)

val occurrences = searcher.findAll("dog")
println("Found 'dog' at indices: $occurrences")

val count = searcher.countOccurrences("The")
println("Found 'The' $count times")
```

### Compressing Text

```kotlin
val longText = "This is a very long repetitive string. " + 
               "This is a very long repetitive string. " +
               "This is a very long repetitive string."

val compressor = TextCompressor(minLength = 10, minOccurrences = 2)
val compressed = compressor.compress(longText)

println("Original length: ${longText.length}")
println("Compressed length: ${compressed.length}")
println("Result: $compressed")
// Output will look like: "This is a very long repetitive string. This i...ring. This i...ring."
```

## Implementation Details

- **Memory Efficiency**: By using `FlyweightCharSequence`, the system avoids creating millions of `String` objects
  during suffix sorting.
- **Search Complexity**: Searching is performed via binary search on the `IntArray` representing the suffix positions.
- **Compression Strategy**: The `TextCompressor` prioritizes longer patterns and higher frequency occurrences to
  maximize the compression ratio while maintaining a readable "skeleton" of the original text.