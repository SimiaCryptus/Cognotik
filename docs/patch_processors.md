# PatchProcessor: A Comprehensive Guide

## What is PatchProcessor?

PatchProcessor is an intelligent code patching system that applies modifications to source code using a fuzzy matching
algorithm. Unlike traditional diff/patch tools that require exact line matches, PatchProcessor can intelligently match
and apply patches even when the source code has minor variations, making it ideal for AI-assisted code modification
workflows.

### Core Components

1. **PatchProcessor Interface**: Defines the contract for patch generation and application
2. **FuzzyPatchMatcher**: The core implementation with sophisticated matching algorithms
3. **PatchProcessors Enum**: Pre-configured processors optimized for different languages and use cases

## Which PatchProcessor Should You Use?

### Language-Specific Processors

#### **CStyle** - For C-family Languages

```kotlin
PatchProcessors.CStyle
```

**Use for**: Java, JavaScript, TypeScript, C++, C#, Go, Rust, Swift, Kotlin

- Optimized for curly-brace syntax
- Tracks bracket nesting depth with weighted scoring
- Best for languages with explicit block delimiters

#### **Indentation** - For Whitespace-Sensitive Languages

```kotlin
PatchProcessors.Indentation
```

**Use for**: Python, YAML, Haskell, F#, CoffeeScript

- Reduced bracket matching (only parentheses and square brackets)
- Larger context window (4 lines vs 3)
- Respects indentation-based structure

#### **Markdown** - For Documentation and Prose

```kotlin
PatchProcessors.Markdown
```

**Use for**: Markdown, reStructuredText, plain text, documentation

- Disables bracket matching
- More lenient fuzzy matching (threshold divisor: 3)
- Longer minimum line length for fuzzy matching (10 chars)
- Ideal for natural language content

#### **Markup** - For XML/HTML

```kotlin
PatchProcessors.Markup
```

**Use for**: HTML, XML, SVG, XAML

- Specialized angle bracket matching (`<` and `>`)
- Higher weight for tags (2x)
- Smaller context window (2 lines)

#### **Lisp** - For S-expression Languages

```kotlin
PatchProcessors.Lisp
```

**Use for**: Lisp, Scheme, Clojure, Racket

- Heavy parenthesis tracking
- Triple weight for parentheses
- Optimized for deeply nested expressions

#### **EndBased** - For Block-End Languages

```kotlin
PatchProcessors.EndBased
```

**Use for**: Ruby, Lua, VB.NET, Pascal

- Supports optional braces
- Equal weight for all bracket types
- Larger context window (4 lines)

#### **SQL** - For Database Languages

```kotlin
PatchProcessors.SQL
```

**Use for**: SQL, PL/SQL, T-SQL, PostgreSQL

- Parenthesis-only bracket matching
- Stricter fuzzy matching (threshold divisor: 5)
- Balanced context window (3 lines)

#### **DataFormat** - For Structured Data

```kotlin
PatchProcessors.DataFormat
```

**Use for**: JSON, TOML, HOCON

- Curly and square bracket tracking
- Higher weights for structure (3x for `{}`, 2x for `[]`)
- **Disables fuzzy matching** for data integrity
- Smaller context window (2 lines)

#### **Shell** - For Shell Scripts

```kotlin
PatchProcessors.Shell
```

**Use for**: Bash, Zsh, Fish, PowerShell

- Full bracket support
- Fuzzy matching enabled
- Standard context window (3 lines)

#### **Config** - For Configuration Files

```kotlin
PatchProcessors.Config
```

**Use for**: INI, properties, .env, .conf files

- No bracket matching
- Fuzzy matching enabled
- Minimal context (2 lines)

### Behavior-Specific Processors

#### **Strict** - Maximum Precision

```kotlin
PatchProcessors.Strict
```

**When to use**:

- Critical production code
- When exact matching is required
- Security-sensitive files
- When you want to avoid false positives

**Characteristics**:

- No fuzzy matching
- No snippet patching
- Larger context window (5 lines)
- Only exact line matches accepted

#### **Lenient** - Maximum Flexibility

```kotlin
PatchProcessors.Lenient
```

**When to use**:

- Heavily modified codebases
- When source has significant drift
- Experimental or rapid prototyping
- When you need patches to apply despite variations

**Characteristics**:

- Very lenient Levenshtein threshold (divisor: 2)
- Low minimum line length for fuzzy matching (3 chars)
- Snippet matching with 60% threshold
- No anchor match requirement
- Minimal context (2 lines)

#### **Fuzzy** - Balanced Default

```kotlin
PatchProcessors.Fuzzy
```

**When to use**:

- General-purpose patching
- When you're unsure which to choose
- Mixed or unknown file types
- Default recommendation

**Characteristics**:

- Balanced fuzzy matching (divisor: 4)
- Standard context (3 lines)
- Snippet patching enabled (80% threshold)
- Anchor match required
- Bracket matching enabled

## The Novel Approach: How FuzzyPatchMatcher Works

### 1. **Bidirectional Line Linking**

Traditional diff tools work linearly. FuzzyPatchMatcher creates a **doubly-linked list** of lines with forward and
backward references:

```kotlin
data class LineRecord(
    val index: Int,
    val line: String?,
    var previousLine: LineRecord? = null,
    var nextLine: LineRecord? = null,
    var matchingLine: LineRecord? = null,
    var type: LineType = CONTEXT,
    var metrics: LineMetrics = LineMetrics()
)
```

This enables:

- **Context-aware matching**: Lines know their neighbors
- **Bidirectional traversal**: Can search forward or backward
- **Relationship preservation**: Maintains structural integrity

### 2. **Multi-Phase Matching Strategy**

The algorithm uses a sophisticated three-phase approach:

#### Phase 1: Unique Line Matching

```kotlin
private fun linkUniqueMatchingLines(
    sourceLines: List<LineRecord>,
    patchLines: List<LineRecord>
): Int
```

- Groups lines by normalized content
- Matches lines that appear exactly once in both source and patch
- Establishes "anchor points" for further matching
- **Why it's novel**: Most diff tools don't prioritize unique lines first

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
- **Why it's novel**: Leverages local context to grow match regions organically

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
- Depth-limited to prevent infinite recursion
- **Why it's novel**: Hierarchical matching that adapts to code structure

### 3. **Structural Awareness via Bracket Tracking**

```kotlin
data class LineMetrics(
    var parenthesesDepth: Int = 0,
    var squareBracketsDepth: Int = 0,
    var curlyBracesDepth: Int = 0,
    var weightedBracketDepth: Int = 0
)
```

Each line tracks its position in nested structures:

```kotlin
private fun calculateLineMetrics(lines: List<LineRecord>) {
    var currentMetrics = LineMetrics(0, 0, 0, 0)

    for (lineRecord in lines) {
        (lineRecord.line ?: "").forEach { char ->
            if (bracketChars.contains(char)) {
                val weight = bracketWeights[char] ?: 1
                // Update depths based on opening/closing brackets
            }
        }
        lineRecord.metrics = currentMetrics
    }
}
```

**Why it's novel**:

- Traditional diff tools ignore syntactic structure
- Bracket depth provides semantic context
- Weighted scoring prioritizes important delimiters (e.g., `{}` over `()`)
- Helps distinguish between similar lines in different scopes

### 4. **Intelligent Fuzzy Matching**

```kotlin
private fun isMatch(
    sourcePrev: LineRecord,
    patchPrev: LineRecord,
    levenshteinDistance: LevenshteinDistance?
): Boolean {
    val normalizedSource = normalizeLine(sourcePrev.line ?: "")
    val normalizedPatch = normalizeLine(patchPrev.line ?: "")

    // Exact match first
    if (normalizedSource == normalizedPatch) return true

    // Structural similarity checks
    val sourceIsListItem = sourcePrev.line?.trimStart()
        ?.matches(Regex("^[-*+\\d]+\\.?\\s+.*")) ?: false
    val patchIsListItem = patchPrev.line?.trimStart()
        ?.matches(Regex("^[-*+\\d]+\\.?\\s+.*")) ?: false
    if (sourceIsListItem != patchIsListItem) return false

    // Levenshtein distance for longer lines
    val maxLength = max(normalizedSource.length, normalizedPatch.length)
    if (maxLength > minLineLengthForFuzzyMatch) {
        val distance = levenshteinDistance.apply(normalizedSource, normalizedPatch)
        return distance <= floor(maxLength / levenshteinThresholdDivisor.toDouble()).toInt()
    }

    return false
}
```

**Novel aspects**:

1. **Structural type checking**: Ensures list items match list items, headers match headers
2. **Adaptive thresholds**: Levenshtein tolerance scales with line length
3. **Minimum length requirement**: Prevents false positives on short lines
4. **Normalization**: Handles whitespace variations intelligently

### 5. **Snippet Patching**

When a patch contains only context lines (no `+` or `-`), the system attempts **snippet patching**:

```kotlin
private fun applySnippetPatch(source: String, patch: String): String {
    // Try exact block match first
    for (i in 0..normalizedSource.size - normalizedPatch.size) {
        var exactMatch = true
        for (j in normalizedPatch.indices) {
            if (normalizedSource[i + j] != normalizedPatch[j]) {
                exactMatch = false
                break
            }
        }
        if (exactMatch) {
            // Replace the matched block
        }
    }

    // Fall back to anchor-based matching
    // Find first and last lines, replace content between

    // Last resort: fuzzy scoring
    var bestMatch = -1
    var bestScore = 0
    for (i in 0..normalizedSource.size - patchSize) {
        var matchScore = 0
        for (j in normalizedPatch.indices) {
            if (normalizedSource[i + j] == normalizedPatch[j]) {
                matchScore++
            }
        }
        if (matchScore >= (patchSize * snippetMatchThreshold).toInt()) {
            bestScore = matchScore
            bestMatch = i
        }
    }
}
```

**Why it's novel**:

- Handles AI-generated patches that provide updated code blocks without explicit diff markers
- Three-tier matching strategy (exact → anchor → fuzzy)
- Configurable match threshold
- Graceful degradation when exact matches fail

### 6. **Move Detection**

```kotlin
private fun markMovedLines(newLines: List<LineRecord>) {
    val matchedSourceLines = newLines.mapNotNull { it.matchingLine }
        .distinct()
        .sortedBy { it.index }

    for (i in matchedSourceLines.indices) {
        val current = matchedSourceLines[i]
        for (j in i + 1 until matchedSourceLines.size) {
            val later = matchedSourceLines[j]
            if (later.matchingLine!!.index < current.matchingLine!!.index) {
                current.type = DELETE
                current.matchingLine!!.type = ADD
                break
            }
        }
    }
}
```

**Novel aspect**: Detects when lines have been moved (not just added/deleted) by comparing the ordering of matched lines
between source and patch.

### 7. **Context Truncation with Ellipsis**

```kotlin
private fun truncateContext(diff: MutableList<LineRecord>): MutableList<LineRecord> {
    val contextBuffer = mutableListOf<LineRecord>()
    for (line in diff) {
        when {
            line.type != CONTEXT -> {
                if (contextSize * 2 < contextBuffer.size) {
                    truncatedDiff.addAll(contextBuffer.take(contextSize))
                    truncatedDiff.add(LineRecord(-1, "...", type = CONTEXT))
                    truncatedDiff.addAll(contextBuffer.takeLast(contextSize))
                } else {
                    truncatedDiff.addAll(contextBuffer)
                }
                contextBuffer.clear()
                truncatedDiff.add(line)
            }
            else -> contextBuffer.add(line)
        }
    }
}
```

**Why it's novel**:

- Intelligently collapses large context blocks
- Preserves context around changes
- Adds visual `...` markers for clarity
- Reduces patch size without losing critical information

### 8. **No-op Annihilation**

```kotlin
private fun annihilateNoopLinePairs(diff: MutableList<LineRecord>) {
    val toRemove = mutableListOf<Pair<Int, Int>>()
    var i = 0
    while (i < diff.size - 1) {
        if (diff[i].type == DELETE) {
            var j = i + 1
            while (j < diff.size && diff[j].type != CONTEXT) {
                if (diff[j].type == ADD &&
                    normalizeLine(diff[i].line ?: "") == normalizeLine(diff[j].line ?: "")
                ) {
                    toRemove.add(Pair(i, j))
                    break
                }
                j++
            }
        }
        i++
    }
    toRemove.flatMap { listOf(it.first, it.second) }
        .distinct()
        .sortedDescending()
        .forEach { diff.removeAt(it) }
}
```

**Novel aspect**: Removes DELETE/ADD pairs that represent the same line (no actual change), cleaning up the diff output.

## Key Innovations Summary

1. **Bidirectional Linked Structure**: Lines know their neighbors, enabling context-aware matching
2. **Multi-Phase Matching**: Unique → Adjacent → Recursive strategy adapts to code structure
3. **Structural Awareness**: Bracket tracking provides semantic context beyond text matching
4. **Adaptive Fuzzy Matching**: Levenshtein distance with structural type checking
5. **Snippet Patching**: Handles AI-generated code blocks without explicit diff markers
6. **Move Detection**: Identifies relocated code, not just additions/deletions
7. **Intelligent Context Management**: Truncates with ellipsis while preserving critical context
8. **No-op Elimination**: Cleans up redundant change pairs

