# Full Replacement

*Replaces the entire file's contents rather than computing or applying a diff.*

**Best for:** Small files, extensive rewrites, or any situation where the changes are broad enough that
line-level patching would be more complex — and more error-prone — than just regenerating the whole file.

## How It Works

1. The LLM is prompted to return the complete, updated contents of each modified file inside a fenced code
   block, preceded by a header identifying the file.
2. When generating a patch, the "patch" produced is simply the new file content in full — no diff computation
   is performed.
3. When applying a patch, the processor discards the original source entirely and returns the patch content
   (trimmed of leading/trailing whitespace) as the new file content.

## Key Features

- **No diffing logic:** Since the full file is always provided, there's no risk of a fuzzy or exact match
  failing to locate the right lines to change.
- **Trims incidental whitespace:** The applied result is trimmed, so stray blank lines or whitespace at the
  start/end of the LLM's response don't leak into the file.
- **Simple, predictable format:** The expected response format is just a labeled code block containing the
  entire file — no special diff syntax to parse or get wrong.

## Example

Prompted format:

    ### src/utils/exampleUtils.js
    ```javascript
    const a = 1;
    const b = 2;

    function exampleFunction() {
      return a + b;
    }

    module.exports = { exampleFunction };
    ```

Applying this patch simply replaces the entire contents of `src/utils/exampleUtils.js` with the code block
shown above, regardless of what the file previously contained.

## Quick Reference

Unlike Fuzzy Patch or the Python Patcher, Full Replacement never attempts partial matching — it's the
fallback when structural diffing isn't worth the risk, at the cost of requiring the LLM to reproduce the
entire file (including unchanged portions) on every edit.