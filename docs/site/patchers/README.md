# Patchers

Patchers are the strategies Cognotik uses to turn LLM-generated changes into updated files — pick the page below for the one relevant to your use case.

- [Data Merge](./DataMerge.md) — Deep-merges structured data (JSON, YAML, XML, TOML, Properties) instead of diffing raw text.
- [Full Replacement](./FullReplacement.md) — Replaces the entire file's contents rather than computing or applying a diff.
- [Fuzzy Patch Matcher](./FuzzyPatch.md) — Matches and applies edits using Levenshtein-distance fuzzy line matching, so patches still apply even when context lines don't align perfectly with the source.
- [Python Patcher](./PythonPatcher.md) — Whitespace-aware diffing and patching tuned for indentation-sensitive languages like Python and YAML.
- [Thermodynamic Patch Matcher](./ThermodynamicPatch.md) — Aligns and applies patches by treating line matching as a molecular binding problem, seeking the lowest-energy (most stable) configuration.