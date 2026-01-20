# Diff and Patch Utilities

The `com.simiacryptus.cognotik.diff` package provides a suite of robust tools for generating and applying text patches. It is specifically designed to handle the challenges of working with Large Language Models (LLMs), which may produce patches with minor inaccuracies, missing context, or unconventional formatting.

## Key Features

*   **Fuzzy Matching:** Uses Levenshtein distance to match lines even when context or formatting has slightly changed.
*   **Snippet Patching:** Can apply code blocks directly without standard diff markers by finding the best fit in the source.
*   **Thermodynamic Alignment:** An experimental matcher inspired by DNA binding principles for high-precision alignment.
*   **Language-Specific Logic:** Specialized handling for Python and YAML where indentation is significant.
*   **Validation Integration:** Built-in support for validating patched code using grammar and syntax checkers.
*   **Moved Line Detection:** Identifies blocks of code that have been relocated within a file.

## Core Components

### `PatchProcessor`
The central interface for all patching logic. It defines methods for:
*   `generatePatch(oldCode, newCode)`: Creates a diff string.
*   `applyPatch(source, patch)`: Applies a diff string to source text.
*   `extractCodeBlocks(response)`: Parses markdown responses to find patches.

### `FuzzyPatchMatcher`
The most versatile processor. It employs a multi-pass linking strategy:
1.  **Exact Unique Anchors:** Links lines that appear exactly once in both versions.
2.  **Adjacent Expansion:** Grows matches outward from anchors.
3.  **Subsequence Linking:** Recursively finds similarities in remaining blocks.
4.  **Fuzzy Logic:** Uses Levenshtein distance thresholds to bridge minor differences.

### `ThermodynamicPatchMatcher`
Treats patching as a molecular binding problem. It calculates "binding energy" between lines and seeks the lowest free energy configuration. This is particularly effective for complex alignments where traditional algorithms might struggle.

### `PythonPatcher`
A specialized version of the fuzzy matcher that preserves leading whitespace, ensuring that Python and YAML indentation remains intact during the patching process.

### `PatchProcessors` (Enum)
Provides pre-configured instances for common use cases:
*   **`Fuzzy`**: Balanced configuration for general use.
*   **`Strict`**: Exact matching only, no fuzzy logic.
*   **`Lenient`**: High tolerance for differences and lower thresholds for snippet matching.
*   **`Python`**: Optimized for indentation-sensitive code.
*   **`Thermodynamic`**: Physics-based alignment.
*   **`FullReplacement`**: Simply replaces the entire file content.

### `SimpleDiffApplier`
A high-level utility that:
1.  Extracts diff blocks from an LLM response.
2.  Applies them sequentially using a `PatchProcessor`.
3.  Validates the result using `GrammarValidator` (e.g., checking Kotlin syntax or parenthesis matching).

## Usage Examples

### Basic Patching with Fuzzy Matcher

```kotlin
val matcher = PatchProcessors.Fuzzy
val oldCode = "..."
val newCode = "..."

// Generate a patch
val patch = matcher.generatePatch(oldCode, newCode)

// Apply a patch
val result = matcher.applyPatch(oldCode, patch)
```

### Applying Patches from LLM Responses

```kotlin
val applier = SimpleDiffApplier()
val response = """
    Here is the fix:
    ```diff
    - old line
    + new line
    ```
"""

val result = applier.apply(
    originalCode = sourceCode,
    response = response,
    filename = "MyFile.kt",
    processor = PatchProcessors.Fuzzy
)

if (result.isValid) {
    println("Successfully patched: ${result.newCode}")
} else {
    result.errors.forEach { println("Validation error: ${it.message}") }
}
```

## Configuration

The `FuzzyPatchMatcher` can be tuned with several parameters:
*   `contextSize`: Number of lines around changes (default: 3).
*   `levenshteinThresholdDivisor`: Controls fuzzy matching stringency (default: 4).
*   `snippetMatchThreshold`: Minimum similarity for snippet application (default: 0.8).
*   `enableFuzzyMatching`: Toggle for fuzzy logic.

The `ThermodynamicPatchMatcher` parameters include:
*   `temperature`: Controls tolerance for mismatches.
*   `cooperativityBonus`: Rewards contiguous matches.
*   `entropyPenalty`: Penalizes gaps and insertions.