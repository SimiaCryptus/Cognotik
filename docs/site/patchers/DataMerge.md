# Data Merge

*Deep-merges structured data (JSON, YAML, XML, TOML, Properties) instead of diffing raw text.*

**Best for:** configuration files and other structured-data formats where the LLM should only specify the fields
it wants to add, change, or remove — not regenerate the entire file. Ideal for JSON/YAML/XML/TOML/properties configs
where preserving unrelated existing fields matters more than tracking line-level text changes.

## How It Works

1. **Detect the data format** of the source content by heuristic and trial parsing (JSON, YAML, XML, TOML, or
   Properties), falling back to JSON if nothing else matches.
2. **Generating a patch:** parse both the old and new content into trees, then compute a minimal diff object
   containing only fields that were added or changed between them, and fields removed in the new version (marked as
   `null`). If the trees are identical, no diff is produced.
3. **Applying a patch:** parse the source with the detected format's mapper, then parse the incoming patch — trying
   the source's format first and falling back to trying every other supported format if that fails.
4. **Deep merge:** recursively merge the patch into the source. Nested objects are merged field-by-field; a `null`
   value in the patch removes the corresponding key from the source; arrays and other scalar values in the patch
   fully replace the corresponding source value.
5. **Serialize** the merged result back out using the source format's writer with pretty-printing.
6. If parsing or merging fails at any point, the processor falls back to returning the raw patch/new content
   unmodified rather than throwing.

## Key Features

- **Format-agnostic:** supports JSON, YAML, XML, TOML, and Java Properties, auto-detecting the format from content
  shape (leading `{`/`[`, `<?xml`/`<`, `key=value` syntax, or `[section]` + `=` patterns for TOML) rather than
  requiring an explicit declaration.
- **Cross-format patch tolerance:** if the LLM emits a patch in a different structured format than the source file
  (e.g. JSON patch against a YAML source), the processor tries every supported format's parser before giving up.
- **Null-to-delete semantics:** setting a field to `null` in the patch removes that key from the merged result,
  giving the LLM an explicit way to delete configuration fields.
- **Nested object merging, scalar/array override:** only nested objects are merged recursively; arrays are treated
  as atomic values and replaced wholesale by the patch's array, avoiding ambiguous partial-array merges.
- **Safe fallback:** any parse or merge failure degrades gracefully — patch generation falls back to emitting the
  full new content, and patch application falls back to returning the raw patch text — rather than corrupting the
  file or throwing.

## Example

Original `config/settings.json`:
```json
{
  "database": {
    "port": 5432,
    "maxConnections": 10
  },
  "legacyFeature": {
    "enabled": true
  }
}
```

LLM-provided patch:
```json
{
  "database": {
    "port": 5433,
    "maxConnections": 20
  },
  "newFeature": {
    "enabled": true
  },
  "legacyFeature": null
}
```

Resulting merged file:
```json
{
  "database": {
    "port": 5433,
    "maxConnections": 20
  },
  "newFeature": {
    "enabled": true
  }
}
```

## Quick Reference

Unlike **Fuzzy Patch** or the **Python Patcher**, Data Merge never operates on raw text lines or diff hunks at all —
it works entirely at the structured-tree level, making it the right choice specifically for config-style files.
Compared to **Full Replacement**, it preserves unrelated fields instead of requiring the LLM to regenerate the whole
file, and compared to **Thermodynamic Patch**, it trades general-purpose text robustness for format-aware precision
on structured data.