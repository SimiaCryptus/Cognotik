---
documents: ../core/src/main/kotlin/com/simiacryptus/cognotik/diff/*.kt
specifies: ../site/cognotik.com/patch-processors.html
---

# PatchProcessor: A Comprehensive Guide

## What is PatchProcessor?

PatchProcessor is an intelligent code patching system that applies modifications to source code using sophisticated matching
algorithms. Unlike traditional diff/patch tools that require exact line matches, PatchProcessor can intelligently match
and apply patches even when the source code has minor variations, making it ideal for AI-assisted code modification
workflows.

### Core Components

1. **PatchProcessor Interface**: Defines the contract for patch generation and application
2. **FuzzyPatchMatcher**: The primary implementation with sophisticated fuzzy matching algorithms
3. **ThermodynamicPatchMatcher**: An alternative implementation based on DNA-binding thermodynamic principles
4. **FullReplacementProcessor**: Simple full-file replacement without patching
5. **PythonPatcher**: Specialized processor for Python and YAML with indentation preservation
6. **PatchProcessors Enum**: Pre-configured processors optimized for different use cases
7. **DiffUtil**: Utility for generating and formatting diffs
8. **SimpleDiffApplier**: High-level API for applying patches with validation

## Which PatchProcessor Should You Use?

### Available Processors

#### **Fuzzy** - Balanced Default (Recommended)

```kotlin
PatchProcessors.Fuzzy
```

**When to use**:

- General-purpose patching
- When you're unsure which to choose
- Mixed or unknown file types
- Default recommendation for most use cases

**Characteristics**:

- Balanced fuzzy matching (Levenshtein threshold divisor: 4)
- Standard context (3 lines before/after changes)
- Snippet patching enabled (80% match threshold)
- Anchor match required for snippet patches
- Supports both standard diff format and code snippet format
- Handles moved line detection

**Best for**: JavaScript, TypeScript, Java, Kotlin, C++, C#, Go, Rust, and most general-purpose languages

#### **Strict** - Maximum Precision

```kotlin
PatchProcessors.Strict
```

**When to use**:

- Critical production code
- When exact matching is required
- Security-sensitive files
- When you want to avoid false positives
- Highly structured code with minimal variations

**Characteristics**:

- No fuzzy matching (exact matches only)
- No snippet patching
- Larger context window (5 lines)
- Only exact line matches accepted
- Stricter validation

**Best for**: Configuration files, security-critical code, highly standardized codebases

#### **Lenient** - Maximum Flexibility

```kotlin
PatchProcessors.Lenient
```

**When to use**:

- Heavily modified codebases
- When source has significant drift from patch context
- Experimental or rapid prototyping
- When you need patches to apply despite variations
- Codebases with inconsistent formatting

**Characteristics**:

- Very lenient Levenshtein threshold (divisor: 2)
- Low minimum line length for fuzzy matching (3 chars)
- Snippet matching with 60% threshold
- No anchor match requirement for snippets
- Minimal context (2 lines)
- Aggressive fuzzy matching

**Best for**: Legacy code, rapidly evolving projects, code with formatting variations

#### **Python** - Python/YAML Specialized

```kotlin
PatchProcessors.Python
```

**When to use**:

- Python source files
- YAML configuration files
- Any indentation-sensitive language
- When whitespace structure is critical

**Characteristics**:

- Preserves leading whitespace (indentation) exactly
- Only removes trailing whitespace during normalization
- Specialized for indentation-based syntax
- Standard fuzzy matching with indentation awareness
- Optimized for Python and YAML semantics

**Best for**: Python, YAML, Haskell, F#, CoffeeScript, and other whitespace-sensitive languages

#### **Thermodynamic** - Physics-Based Matching

```kotlin
PatchProcessors.Thermodynamic
```

**When to use**:

- When you want an alternative matching strategy
- Experimental applications
- Research or novel matching scenarios
- When traditional matching fails

**Characteristics**:

- Based on DNA-binding thermodynamic principles
- Uses binding energy calculations for line matching
- Incorporates cooperativity bonuses for adjacent matches
- Entropy penalties for gaps
- Temperature parameter controls matching stringency
- Finds thermodynamically optimal alignment

**Parameters**:

- `temperature`: Controls matching stringency (default: 1.0)
- `cooperativityBonus`: Energy bonus for adjacent matches (default: 2.0)
- `entropyPenalty`: Energy cost per gap (default: 1.0)

**Best for**: Experimental use, novel matching scenarios, research applications

#### **FullReplacement** - Complete File Replacement

```kotlin
PatchProcessors.FullReplacement
```

**When to use**:

- When changes are extensive
- Creating entirely new files
- When patching would be more complex than replacement
- Simple file updates without context preservation

**Characteristics**:

- No patching logic
- Replaces entire file content
- Expects complete updated file in code blocks
- Simplest approach, no matching required

**Best for**: New files, complete rewrites, simple replacements

## Core Concepts

### The PatchProcessor Interface

```kotlin
interface PatchProcessor {
    val label: String
    val patchFormatPrompt: String
    fun generatePatch(oldCode: String, newCode: String): String
    fun applyPatch(source: String, patch: String): String
    fun extractCodeBlocks(response: String): List<Pair<String, String>>
    fun getInitiatorPattern(): Regex
}
```

All processors implement this interface, providing:

- **generatePatch**: Creates a diff between two code versions
- **applyPatch**: Applies a patch to source code
- **extractCodeBlocks**: Parses code blocks from markdown responses
- **getInitiatorPattern**: Regex to detect code block starts
- **patchFormatPrompt**: Instructions for LLMs on expected format

### Patch Format

Patches use standard unified diff format:

```diff
 context line (unchanged)
-deleted line
+added line
 more context
```

Alternatively, patches can be provided as code snippets without `+`/`-` markers:

```
 context line
 updated code block
 more context
```

## How FuzzyPatchMatcher Works

The FuzzyPatchMatcher is the primary implementation, using a sophisticated multi-phase algorithm:

### 1. **Bidirectional Line Linking**

Lines are organized as a doubly-linked list:

```kotlin
data class LineRecord(
    val index: Int,
    val line: String?,
    var previousLine: LineRecord? = null,
    var nextLine: LineRecord? = null,
    var matchingLine: LineRecord? = null,
    var type: LineType = CONTEXT
)
```

**Benefits**:

- Lines know their neighbors
- Enables context-aware matching
- Supports bidirectional traversal
- Maintains structural relationships

### 2. **Multi-Phase Matching Strategy**

#### Phase 1: Unique Line Matching

```kotlin
private fun linkUniqueMatchingLines(
    sourceLines: List<LineRecord>,
    patchLines: List<LineRecord>
): Int
```

- Groups lines by normalized content
- Matches lines appearing exactly once in both source and patch
- Establishes high-confidence "anchor points"
- Prevents ambiguous matches

**Why it works**: Unique lines are unambiguous matches that serve as reliable starting points.

#### Phase 2: Adjacent Line Propagation

```kotlin
private fun linkAdjacentMatchingLines(
    sourceLines: List<LineRecord>,
    levenshtein: LevenshteinDistance?
): Int
```

- Expands matches from established anchor points
- Checks previous and next lines of matched pairs
- Uses fuzzy matching for near-matches
- Iterates until no new matches found

**Why it works**: Lines near confirmed matches are likely to be related.

#### Phase 3: Recursive Subsequence Linking

```kotlin
private fun subsequenceLinking(
    sourceLines: List<LineRecord>,
    patchLines: List<LineRecord>,
    depth: Int = 0,
    levenshteinDistance: LevenshteinDistance?
)
```

- Recursively processes unmatched segments
- Applies phases 1 and 2 to remaining gaps
- Depth-limited (default: 100) to prevent infinite recursion
- Hierarchical matching that adapts to code structure

**Why it works**: Remaining unmatched blocks may contain islands of similarity that benefit from the same matching strategy.

### 3. **Intelligent Fuzzy Matching**

```kotlin
private fun isMatch(
    sourcePrev: LineRecord,
    patchPrev: LineRecord,
    levenshteinDistance: LevenshteinDistance?
): Boolean
```

Matching logic:

1. **Exact Match**: Normalized lines are identical → match
2. **Empty Lines**: Both empty → match
3. **Structural Type Checking**:
  - List items only match list items
  - Headers only match headers
  - Block quotes only match block quotes
  - Code blocks only match code blocks
4. **Levenshtein Distance**: For longer lines, calculate edit distance
  - Threshold: `line_length / levenshteinThresholdDivisor`
  - Default divisor: 4 (stricter = higher divisor)
  - Only applied to lines longer than `minLineLengthForFuzzyMatch` (default: 5)

**Why it's novel**:

- Structural type checking prevents false positives
- Adaptive thresholds scale with line length
- Minimum length requirement prevents noise
- Normalization handles whitespace intelligently

### 4. **Snippet Patching**

When a patch contains only context lines (no `+` or `-`), the system attempts **snippet patching**:

```kotlin
private fun applySnippetPatch(source: String, patch: String): String
```

Three-tier matching strategy:

1. **Exact Block Match**: Find identical contiguous block in source
2. **Anchor Match**: Match first and last lines, replace content between
3. **Fuzzy Scoring**: Slide window across source, score matches
  - Requires `snippetMatchThreshold` match (default: 80%)
  - Optionally requires anchor match (first or last line exact)

**Why it's novel**: Handles AI-generated patches that provide updated code blocks without explicit diff markers.

### 5. **Move Detection**

```kotlin
private fun markMovedLines(newLines: List<LineRecord>)
```

Identifies when lines have been moved (not just added/deleted):

- Examines sequence of matched lines sorted by original position
- Detects "order inversions" where line A appeared before B in source but after B in patch
- Marks original as DELETE and new as ADD
- Represents moves as delete + add operations

**Why it matters**: Distinguishes between modifications and relocations.

### 6. **Context Truncation**

```kotlin
private fun truncateContext(diff: MutableList<LineRecord>): MutableList<LineRecord>
```

Intelligently collapses large context blocks:

- Keeps `contextSize` lines before and after changes
- Inserts `...` marker for collapsed sections
- Preserves critical context without bloat
- Reduces patch size while maintaining readability

### 7. **No-op Annihilation**

```kotlin
private fun annihilateNoopLinePairs(diff: MutableList<LineRecord>)
```

Removes redundant DELETE/ADD pairs:

- Identifies consecutive DELETE followed by ADD with same content
- Removes both lines (no actual change)
- Cleans up diff output
- Occurs when moved lines end up adjacent to originals

## How ThermodynamicPatchMatcher Works

An alternative implementation based on DNA-binding thermodynamic principles:

### Core Concept

Treats patch matching as a molecular binding problem:

- **Binding Energy (ΔG)**: Negative for favorable matches, positive for mismatches
- **Temperature (T)**: Controls stringency (lower T requires better matches)
- **Cooperativity**: Adjacent matches stabilize each other
- **Entropy**: Gaps have entropic cost

### Algorithm

1. **Calculate Binding Energy Matrix**: Energy for each line pair
  - Perfect match: -10.0 × line_length
  - Similarity-based: -10.0 × similarity × line_length
  - Length penalty: entropyPenalty × |length_diff|

2. **Find Optimal Alignment**: Dynamic programming with thermodynamic scoring
  - Similar to Needleman-Wunsch algorithm
  - Incorporates cooperativity bonuses
  - Finds lowest free energy configuration

3. **Generate Diff**: Convert alignment to standard diff format

### When to Use

- Experimental applications
- Novel matching scenarios
- When traditional matching fails
- Research or specialized use cases

## Utility Classes

### DiffUtil

Provides basic diff generation and formatting:

```kotlin
object DiffUtil {
    fun generateDiff(original: List<String>, modified: List<String>): List<PatchLine>
    fun formatDiff(patchLines: List<PatchLine>, contextLines: Int = 3): String
}
```

**Use for**: Simple diff generation without advanced matching.

### SimpleDiffApplier

High-level API for applying patches with validation:

```kotlin
class SimpleDiffApplier {
    fun apply(
        originalCode: String,
        response: String,
        filename: String? = null,
        processor: PatchProcessor
    ): DiffApplicationResult
}
```

**Features**:

- Extracts diffs from markdown responses
- Applies patches sequentially
- Validates grammar (Kotlin, parentheses matching)
- Filters out pre-existing errors
- Returns validation results

**Use for**: Production code with validation requirements.

### PythonPatcher

Specialized processor for Python and YAML:

```kotlin
class PythonPatcher : PatchProcessor
```

**Key difference**: Preserves leading whitespace (indentation) exactly, only normalizes trailing whitespace.

**Use for**: Python, YAML, and other indentation-sensitive languages.

## Configuration Parameters

### FuzzyPatchMatcher Parameters

```kotlin
FuzzyPatchMatcher(
    contextSize: Int = 3,                              // Context lines before/after changes
    maxRecursionDepth: Int = 100,                      // Max recursion in subsequence linking
    levenshteinThresholdDivisor: Int = 4,              // Stricter = higher value
    minLineLengthForFuzzyMatch: Int = 5,               // Minimum line length for fuzzy matching
    enableFuzzyMatching: Boolean = true,               // Enable Levenshtein distance matching
    enableSnippetPatching: Boolean = true,             // Enable snippet patch application
    snippetMatchThreshold: Double = 0.8,               // Minimum match % for snippets (0.0-1.0)
    requireAnchorMatch: Boolean = true,                // Require first/last line match for snippets
    debug: Boolean = false                             // Enable debug logging
)
```

### ThermodynamicPatchMatcher Parameters

```kotlin
ThermodynamicPatchMatcher(
    temperature: Double = 1.0,                         // Matching stringency
    cooperativityBonus: Double = 2.0,                  // Energy bonus for adjacent matches
    entropyPenalty: Double = 1.0,                      // Energy cost per gap
    contextSize: Int = 3,                              // Context lines
    minBindingEnergy: Double = 0.0                     // Maximum energy for valid binding
)
```

## Pre-configured Processor Modes

### Strict Mode

```kotlin
FuzzyPatchMatcher(
    enableFuzzyMatching = false,
    enableSnippetPatching = false,
    contextSize = 5
)
```

### Lenient Mode

```kotlin
FuzzyPatchMatcher(
    enableFuzzyMatching = true,
    levenshteinThresholdDivisor = 2,
    minLineLengthForFuzzyMatch = 3,
    enableSnippetPatching = true,
    snippetMatchThreshold = 0.6,
    requireAnchorMatch = false,
    contextSize = 2
)
```

## Usage Examples

### Basic Patch Generation

```kotlin
val processor = PatchProcessors.Fuzzy
val oldCode = "fun hello() { return 1 }"
val newCode = "fun hello() { return 2 }"
val patch = processor.generatePatch(oldCode, newCode)
println(patch)
// Output:
//   fun hello() { return
// - return 1
// + return 2
//   }
```

### Applying a Patch

```kotlin
val processor = PatchProcessors.Fuzzy
val source = "fun hello() { return 1 }"
val patch = """
  fun hello() { return
- return 1
+ return 2
  }
""".trimIndent()
val result = processor.applyPatch(source, patch)
println(result)
// Output: fun hello() { return 2 }
```

### Snippet Patching

```kotlin
val processor = PatchProcessors.Fuzzy
val source = """
  fun greet(name: String) {
      println("Hello, ${'$'}name")
      return
  }
""".trimIndent()

val patch = """
  fun greet(name: String) {
      println("Hello, ${'$'}name!")
      return
  }
""".trimIndent()

val result = processor.applyPatch(source, patch)
// Automatically detects snippet format and applies it
```

### Using SimpleDiffApplier

```kotlin
val applier = SimpleDiffApplier()
val result = applier.apply(
    originalCode = "fun test() { return 1 }",
    response = """
        Here's the fix:

        ```diff
         fun test() {
        -    return 1
        +    return 2
         }
        ```
    """.trimIndent(),
    filename = "Test.kt",
    processor = PatchProcessors.Fuzzy
)

if (result.isValid) {
    println("Patched successfully: ${result.newCode}")
} else {
    println("Errors: ${result.errors.joinToString("\n") { it.message }}")
}
```

## Key Innovations Summary

1. **Bidirectional Linked Structure**: Lines know their neighbors, enabling context-aware matching
2. **Multi-Phase Matching**: Unique → Adjacent → Recursive strategy adapts to code structure
3. **Adaptive Fuzzy Matching**: Levenshtein distance with structural type checking and adaptive thresholds
4. **Snippet Patching**: Handles AI-generated code blocks without explicit diff markers
5. **Move Detection**: Identifies relocated code, not just additions/deletions
6. **Intelligent Context Management**: Truncates with ellipsis while preserving critical context
7. **No-op Elimination**: Cleans up redundant change pairs
8. **Thermodynamic Alternative**: Physics-based matching for specialized scenarios
9. **Language-Specific Support**: Python/YAML processor preserves indentation
10. **Validation Integration**: SimpleDiffApplier validates grammar and filters pre-existing errors

## Troubleshooting

### Patch Not Applying

1. **Try Lenient mode**: More forgiving matching
2. **Check context lines**: Ensure patch context matches source
3. **Verify format**: Use standard diff format with `+`/`-` markers
4. **Enable debug**: Set `debug = true` for detailed logging

### False Positives

1. **Try Strict mode**: Exact matching only
2. **Increase threshold divisor**: Make fuzzy matching stricter
3. **Disable snippet patching**: Require explicit diff markers
4. **Increase context**: More context lines reduce ambiguity

### Performance Issues

1. **Reduce recursion depth**: Lower `maxRecursionDepth`
2. **Disable fuzzy matching**: Faster but less flexible
3. **Use Strict mode**: Simpler algorithm
4. **Check file size**: Very large files may be slow

## Best Practices

1. **Start with Fuzzy**: Default recommendation for most cases
2. **Use language-specific processors**: Python for Python, etc.
3. **Provide context**: Include surrounding lines in patches
4. **Test on samples**: Verify patches work before production
5. **Enable validation**: Use SimpleDiffApplier for critical code
6. **Monitor logs**: Enable debug mode to understand matching behavior
7. **Adjust parameters**: Fine-tune for your specific use case
