# Thermodynamic Patch Matcher

*Aligns and applies patches by treating line matching as a molecular binding problem, seeking the lowest-energy (most stable) configuration.*

**Best for:** Situations where source and patch content have drifted apart in small ways (reordered whitespace, minor rewording, partial context) and you want a principled, tunable way to trade off match strictness against tolerance for near-matches — especially when generating diffs between two full versions of a file or applying context-based patches to a file that may not match the patch context exactly.

## How It Works

**Generating a patch (`generatePatch`):**

1. Short-circuit trivial cases: identical inputs produce no patch; a blank old file produces an all-additions patch; a blank new file produces an all-deletions patch.
2. Build a **binding energy matrix** between every line of the old file and every line of the new file. Identical (normalized) lines get strongly negative energy; similar lines get energy scaled by Levenshtein similarity; differing lengths add an entropic penalty.
3. Run a Needleman-Wunsch-style dynamic program over this matrix to find the **minimum total free energy alignment** across the whole file, choosing between match/mismatch, delete, and insert moves at each step. Consecutive matches receive a **cooperativity bonus** (lower energy), similar to base-stacking stabilization in DNA.
4. Backtrack through the DP table to recover the sequence of match/delete/insert operations.
5. Convert the alignment into diff lines (context/add/delete), then **truncate long runs of context** down to a configurable number of lines around each change, inserting a `...` marker for large gaps.

**Applying a patch (`applyPatch`):**

1. Parse the patch text into context/add/delete lines.
2. If the patch has context lines, scan every possible position in the source file and compute the **binding site energy** of anchoring the patch there (context lines must energetically match nearby source lines; deletions/additions incur an entropic cost).
3. If the patch has *no* context lines (pure snippet), instead slide a window across the source and score each window as a direct multi-line binding energy sum, with cooperativity bonuses for consecutive favorable matches.
4. Keep only binding sites whose free energy is below a configurable threshold, then select the **single lowest-energy site** as the true binding location.
5. Apply the patch's context/delete/add operations starting at that site, preserving all source lines outside the patched region.

## Key Features

- **Similarity-based line matching** using Levenshtein distance rather than exact string equality, so minor textual drift doesn't break alignment.
- **Configurable stringency via `temperature`** — conceptually controls how much mismatch tolerance the matcher allows (higher values are more permissive).
- **Cooperativity bonus (`cooperativityBonus`)** rewards runs of consecutive matches, favoring contiguous alignments over scattered, fragmented ones — mirroring how adjacent base pairs stabilize each other in DNA binding.
- **Entropy penalty (`entropyPenalty`)** charges an energetic cost for every gap (insertion/deletion), discouraging unnecessary line churn.
- **Minimum binding energy threshold (`minBindingEnergy`)** filters out weak/unreliable patch application sites — if no site is stable enough, the patch is rejected and the source is returned unchanged.
- **Context truncation (`contextSize`)** keeps generated diffs readable by collapsing long unchanged runs into a bounded window with a `...` marker.
- **Dual apply strategies**: full context-anchored scanning for well-formed diffs, and snippet-window scanning for patches without any context lines.
- **Safe fallbacks** for edge cases: identical content yields an empty patch; fully blank old/new inputs degrade to pure addition/deletion listings; unmatched patches are safely rejected rather than corrupting the file.

## Example

Original file:
```text
function greet(name) {
  console.log("Hello " + name);
}
```

Generated diff (unified-diff-style, 2 lines of context):
```diff
  function greet(name) {
-   console.log("Hello " + name);
+   console.log(`Hello, ${name}!`);
  }
```

When this diff is applied back to a source file whose surrounding context lines match closely enough (even if not byte-identical), the matcher finds the lowest-energy binding site for the `function greet(name) {` / `}` context pair and swaps in the new `console.log` line, leaving everything else untouched.

## Quick Reference

Compared to **Fuzzy Patch Matcher**, which is optimized for approximate but structurally similar context matching, the Thermodynamic Patch Matcher adds an explicit energy-minimization framework with tunable cooperativity and entropy terms, making trade-offs between strictness and tolerance more explicit and physically motivated. Unlike **Full Replacement**, it never discards the original file — it always seeks a partial, localized alignment. Unlike **Data Merge**, it operates purely on line text and diff/context semantics rather than structured data shapes. And unlike **Python Patcher**, its algorithm is language-agnostic, relying only on generic line similarity rather than syntax-aware parsing.