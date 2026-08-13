# Fuzzy Patch Matcher

*Matches and applies edits using Levenshtein-distance fuzzy line matching, so patches still apply even when context lines don't align perfectly with the source.*

**Best for:** LLM-generated diffs against real-world source files where whitespace drift, minor reformatting, or slightly inaccurate context lines are likely — situations where a strict unified-diff applier would reject the patch outright.

## How It Works

1. **Parse into linked lines.** Both the source and the patch (or new version) are split into lines and converted into doubly-linked lists (`LineRecord`s), so the algorithm can walk forward and backward from any anchor point.
2. **Link unique exact matches first.** Lines whose normalized text appears the same number of times in both the source and the patch are linked as high-confidence anchors.
3. **Expand to adjacent lines.** Starting from each anchor, the matcher tries to link the immediately preceding and following lines, optionally using fuzzy (Levenshtein) comparison instead of exact equality.
4. **Recursively match remaining blocks.** Any still-unmatched segments between anchors are recursively re-run through steps 2–3, up to a maximum recursion depth, to find "islands" of similarity within larger changed blocks.
5. **Detect moved lines.** If a matched line appears out of order relative to its neighbors, it's reclassified as a `DELETE` at its old location and an `ADD` at its new one.
6. **Generate or apply the diff.** For patch *generation*, matched/unmatched lines are converted into `+`, `-`, and context lines, then context is truncated and no-op delete/add pairs are annihilated. For patch *application*, the same linking process is used to align the incoming patch's `+`/`-`/context lines against the real source, then the source is reconstructed by walking source lines and splicing in adjacent adds/deletes.
7. **Fall back to snippet patching if needed.** If the given patch has no `+`/`-` markers at all, it's treated as a bare code snippet and located in the source via exact block match, first/last-line anchor match, or fuzzy sliding-window match.

## Key Features

- **Tunable fuzzy strictness** — `levenshteinThresholdDivisor` controls how much a line can differ and still be considered a match (threshold = line length ÷ divisor); lower values make matching stricter, higher values more tolerant.
- **Fuzzy matching can be disabled entirely** (`enableFuzzyMatching = false`) to fall back to exact normalized-text comparison only.
- **Minimum line length gate** (`minLineLengthForFuzzyMatch`) prevents short lines (where edit distance is noisy) from being fuzzily matched.
- **Structure-aware heuristics** avoid matching across incompatible line kinds (e.g. a Markdown list item vs. a plain line, a header vs. non-header, a blockquote vs. non-blockquote, a code-fence marker vs. non-marker), reducing false positives in structured text.
- **Configurable context size** (`contextSize`) trims how many unchanged lines surround each change block in a generated diff, inserting a `...` marker when context is truncated.
- **Recursion depth cap** (`maxRecursionDepth`) bounds the recursive subsequence-matching pass to avoid runaway recursion on adversarial input.
- **Snippet patching fallback** (`enableSnippetPatching`) lets the matcher apply raw code blocks without diff markers, using exact match, then anchor (first/last line) match, then fuzzy sliding-window match with a required score of `snippetMatchThreshold`.
- **Optional anchor requirement** (`requireAnchorMatch`) demands that a fuzzy snippet match have its first or last line match exactly (or be at most one line off), reducing risky low-confidence snippet placements.
- **Graceful edge-case handling** — identical inputs return an empty patch, a blank source treated as "whole file is new," and a blank patch is a no-op.
- **Moved-block detection** represents relocated code as a delete/add pair rather than leaving it silently unmatched.

## Example

Original source:
```text
def greet(name):
    print("Hello " + name)
    return None
```

Patch (unified-diff-style, as generated/consumed by this matcher):
```diff
  def greet(name):
-     print("Hello " + name)
+     print(f"Hello, {name}!")
    return None
```

Result after `applyPatch`:
```text
def greet(name):
    print(f"Hello, {name}!")
    return None
```

Even if the patch's context line (`def greet(name):`) had trailing whitespace or slightly different spacing than the source, the fuzzy adjacency matching would still align it correctly.

## Quick Reference

Fuzzy Patch Matcher sits between the extremes of the patcher family: unlike **Full Replacement**, which discards structural diffing entirely and swaps in a whole new file, Fuzzy Patch Matcher tries hard to preserve unrelated lines by aligning changes at the line level — even under imperfect context. Unlike a strict unified-diff applier, it tolerates whitespace drift and near-miss context via Levenshtein matching, and it can fall back to snippet-style placement when the LLM omits diff markers altogether. Compared to **Data Merge** (structural/JSON-aware merging) or the **Python Patcher** (language-specific patching), this matcher is language-agnostic and line-oriented, making it a general-purpose default rather than a format-specialized one. It is more conservative than **Thermodynamic Patch**'s probabilistic exploration, favoring deterministic, explainable line linking over sampling-based reconciliation.