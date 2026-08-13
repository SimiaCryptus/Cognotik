# Python Patcher

*Whitespace-aware diffing and patching tuned for indentation-sensitive languages like Python and YAML.*

**Best for:** Editing Python, YAML, or other indentation-significant files where leading whitespace must be
preserved exactly, but trailing whitespace and minor line drift from LLM output should be tolerated.

## How It Works

1. **Parse into linked lines** — both the source and new/patch text are split into line records, each aware of
   its previous and next neighbor, forming a doubly-linked structure for traversal.
2. **Link unique matches** — lines are grouped by a normalized form (leading whitespace preserved, trailing
   whitespace stripped); lines whose normalized text appears the same number of times in both source and patch
   are linked directly.
3. **Expand to adjacent lines** — starting from confirmed matches, the algorithm looks at neighboring lines on
   both sides and links them too, falling back to Levenshtein-distance fuzzy matching (threshold scaled to line
   length) when exact text differs slightly.
4. **Recurse on remaining unmatched lines** — the unique-match and adjacent-match passes repeat (up to a depth
   limit) on the still-unmatched subset, progressively resolving ambiguous regions.
5. **Detect moved lines** — once matching stabilizes, sequences of matched lines are walked in parallel; when the
   expected next matched pair diverges, the misaligned lines are reclassified as delete/add pairs, indicating a
   move.
6. **Emit or apply the diff** — for generation, the linked structure is converted into context/add/delete line
   records, trimmed to a small context window (with `...` separators for large gaps), and no-op add/delete pairs
   with identical normalized text are annihilated. For application, matched patch lines are spliced against the
   source, and unmatched `+` lines are inserted around their nearest linked anchor.

## Key Features

- **Indentation-preserving normalization** — only trailing whitespace is stripped before comparison, so leading
  spaces (Python/YAML indentation) are never treated as noise and are never silently altered by the matching
  process.
- **Fuzzy adjacency matching** — uses Levenshtein distance (via Apache Commons Text) on lines longer than 5
  characters, accepting a match if the edit distance is within roughly a quarter of the longer line's length,
  which absorbs small LLM rewording without breaking alignment.
- **Recursive subsequence resolution** — repeatedly re-applies unique and adjacent matching to shrinking sets of
  unmatched lines (depth-capped at 10), improving alignment in files with repeated or similar-looking blocks.
- **Move detection** — explicitly identifies lines that were relocated rather than edited, marking the old
  position as delete and the new position as add instead of misreporting them as unrelated changes.
- **Context truncation with ellipsis** — generated diffs keep only a few lines of context (3) around each change,
  collapsing longer unchanged runs with a `...` marker for readability.
- **No-op pair annihilation** — adjacent delete/add pairs that normalize to the same text (e.g. only trailing
  whitespace differed) are removed from the final diff, avoiding noisy pseudo-changes.

## Example

Original (`config.yaml`):
```text
database:
  host: localhost
  port: 5432
```

Patch:
```diff
 database:
-  host: localhost
+  host: 127.0.0.1
   port: 5432
```

Result:
```text
database:
  host: 127.0.0.1
  port: 5432
```

## Quick Reference

Compared to **Fuzzy Patch**, the Python Patcher shares the same context-linking/adjacency approach but restricts
whitespace normalization to trailing characters only, making it the safer choice whenever indentation is
semantically meaningful. Unlike **Full Replacement**, it still performs structural, line-level diffing rather than
swapping the whole file. It is distinct from the **Data Merge** patcher, which targets structured formats like JSON
by key rather than by line, and from **Thermodynamic Patch**, which optimizes globally for minimal edit energy
rather than local line linkage.