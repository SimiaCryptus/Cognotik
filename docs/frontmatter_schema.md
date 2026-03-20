---
documents:
  - ../intellij/src/main/kotlin/cognotik/actions/task/DocProcessorAction.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/embed/DocProcessor.kt
  - ../webui/src/test/kotlin/com/simiacryptus/cognotik/util/DocProcessorTest.kt
specifies: ../site/cognotik.com/frontmatter.html
---

# Frontmatter Schema for DocProcessor

This document describes the YAML frontmatter schema used by `DocProcessor` (located in `com.simiacryptus.cognotik.util`,
source file at `com.simiacryptus.cognotik.embed.DocProcessor`)
to process markdown documentation files and manage relationships between documentation and source code.

## Overview

`DocProcessor` processes markdown files that contain YAML frontmatter blocks. The frontmatter specifies how the
documentation relates to source files - either as specifications that drive code generation, as documentation that
should be updated based on source files, or as transformation rules between files.
The processor also supports fetching and caching URL-based related resources, allowing documentation to reference
external web content as context.

## Frontmatter Format

Frontmatter must be enclosed between `---` delimiters at the start of the markdown file:

```yaml
---
key: value
list_key:
  - item1
  - item2
---

# Document content starts here
```

## Supported Keys

### `specifies`

Defines glob patterns for files that this documentation specifies. The matched files will be created or updated based on
the documentation content.

**Type:** `String` or `List<String>`

**Examples:**

```yaml
# Single file
specifies: ../src/utils/helper.kt
```

```yaml
# Single glob pattern
specifies: ../src/**/*.kt
```

```yaml
# Multiple patterns
specifies:
  - ../src/models/*.kt
  - ../src/utils/*.kt
```

**Glob Pattern Support:**

- Simple patterns: `*.kt`, `helper.kt`
- Recursive patterns: `**/*.kt` (matches files in all subdirectories)
- Paths are resolved relative to the markdown file's directory
- Bracket patterns: `file[0-9].txt` (matches character ranges)
- Question mark patterns: `file?.txt` (matches single character)
- Literal paths (without wildcards) are returned even if the file doesn't exist yet, enabling creation of new files

---

### `documents`

Defines glob patterns for source files that this documentation describes. This is the inverse of `specifies` - the
documentation file itself becomes the target to be updated based on the matched source files.

**Type:** `String` or `List<String>`

**Examples:**

```yaml
# Single file
documents: ../src/main/kotlin/MyClass.kt
```

```yaml
# Multiple source files
documents:
  - ../src/**/*.kt
  - ../src/**/*.java
```

**Use Case:** Keep documentation in sync with source code changes. When source files change, the documentation can be
automatically updated to reflect the current implementation.

---

### `transforms`

Defines regex-based transformation rules that map source files to destination files. Uses regex capture groups and
backreferences for flexible file mapping.

**Type:** `String` or `List<String>`

**Format:** `sourcePattern -> destinationPattern`

- `sourcePattern`: A regex pattern to match source file paths (relative to the doc file's directory)
- `destinationPattern`: The destination path with backreferences (`$0`, `$1`, `$2`, etc.)

**Examples:**

```yaml
# Single transform
transforms: src/(.+)\.java -> generated/$1.kt
```

```yaml
# Multiple transforms
transforms:
  - src/models/(.+)\.java -> kotlin/models/$1.kt
  - src/utils/(.+)\.java -> kotlin/utils/$1.kt
```

**Backreference Support:**

- `$0` - The entire matched string
- `$1`, `$2`, etc. - Captured groups from the regex pattern
- `$1+1` - Arithmetic addition: if group 1 is numeric, replaced with (group1 + 1); otherwise the literal `+1` is
  appended
- `$2-5` - Arithmetic subtraction: if group 2 is numeric, replaced with (group2 - 5); otherwise the literal `-5` is
  appended

**Note:** Transform source patterns use Java regex syntax (not glob patterns). The regex is matched against file paths
relative to the documentation file's parent directory. When rebasing, transform patterns are preserved as-is since they
are resolved relative to the doc file at usage time.
**Data File Detection:** If a transform matches a JSON source file, it can be automatically used as a data source for
template processing (see `data_file` under implicit frontmatter keys).
---

### `generates`

Defines explicit output files to generate from specified input files. Unlike `transforms`, this doesn't use pattern
matching - it explicitly lists the output file and its input sources.

**Type:** `Map` or `List<Map>`

**Structure:**

```yaml
generates:
  output: path/to/output/file
  inputs:
    - input/pattern/*.kt
    - another/input.kt
```

**Examples:**

```yaml
# Single generate spec
generates:
  output: ../generated/combined.kt
  inputs:
    - ../src/models/*.kt
    - ../src/utils/*.kt
```

```yaml
# Multiple generate specs
generates:
  - output: ../generated/models.kt
    inputs:
      - ../src/models/**/*.kt
  - output: ../generated/utils.kt
    inputs:
      - ../src/utils/**/*.kt
```

**Input Pattern Support:**

- Simple globs: `*.kt`, `models/*.kt`
- Recursive globs: `**/*.kt` (matches files in all subdirectories)
- Paths are resolved relative to the markdown file's directory
- A single string input is also accepted (converted to a single-element list)

**Validation:** A generate spec requires both `output` and `inputs` fields. Specs missing either field are skipped
with a warning.

**Use Case:** Generate aggregate files, combined outputs, or files that depend on multiple input sources.

---

### `related`

Specifies additional files or URLs to include as context when processing modification tasks. These resources are not
targets but provide supplementary information.

**Type:** `String` or `List<String>`

**Examples:**

```yaml
# Single related file
related: ../shared/constants.kt
```

```yaml
# Multiple related files, glob patterns, and URLs
related:
  - ../shared/constants.kt
  - ../config/settings.yaml
  - ./helper-docs.md
  - ../src/models/*.kt
  - https://example.com/api-spec
```

**Glob Pattern Support:** Related resources can use glob patterns (containing `*`, `?`, or `[`). Glob patterns are
expanded against the filesystem relative to the markdown file's directory. If a glob pattern matches no files, a debug
message is logged and the pattern is skipped.

**URL Support:** Related resources can be URLs (http:// or https://). URLs are automatically fetched, cached locally
(with a 1-hour cache TTL), and their HTML content is simplified before being included as context. The URL cache is
stored in `.doc-processor-cache/url-cache` within the root directory.

**Use Case:** Include configuration files, shared constants, related documentation, or external web resources that
provide context for the AI when processing the target files.

---

### `task_type`

Specifies which task type to use for processing the target files. This allows customization of how the AI processes the
modification task.

**Type:** `String`

**Default:** `FileModification`

**Examples:**

```yaml
# Use default file modification task
task_type: FileModification
```

```yaml
# Use a different task type
task_type: CodeReview
```

**Resolution Priority:** When multiple specifications apply to a single target file, the task type is resolved in this
order:

1. `specifies` frontmatter (first non-null)
2. `transforms` frontmatter (first non-null)
3. `documents` frontmatter (first non-null)
4. `generates` frontmatter (first non-null)
5. Default: `FileModification`

**Task Type Resolution:** The task type name is resolved using `TaskType.valueOf()` with spaces removed. Unknown task
type names log a warning and fall back to `FileModification`.

**Use Case:** Customize the AI's behavior when processing files. Different task types may have different prompts,
validation rules, or processing strategies.

---

### `task_config_json`

Specifies a relative file path to a JSON file containing additional task type configuration. This allows for more
complex configuration that would be unwieldy in YAML frontmatter.

**Type:** `String`

**Examples:**

```yaml
# Reference a JSON config file
task_config_json: ./config/my-task-config.json
```

```yaml
# Config file in parent directory
task_config_json: ../shared/task-settings.json
```

**Use Case:** Provide detailed task configuration without cluttering the frontmatter. Useful for complex task types that
require many parameters or when sharing configuration across multiple documentation files.

---

### `update_mode`

Specifies the update mode for this documentation file's targets. This controls how existing files are handled during
processing. This per-doc setting overrides the global update mode configured at the `DocProcessor` level.

**Type:** `String`

**Valid Values:**

- `SkipExisting` - Skip files that already exist (no processing)
- `OverwriteExisting` - Always overwrite existing files with full replacement
- `OverwriteToUpdate` - Overwrite only if source/related files are newer than target
- `PatchExisting` - Always apply fuzzy patch to existing files
- `PatchToUpdate` - Apply fuzzy patch only if source/related files are newer than target (default)
- `ForceOverwrite` - Delete all target files before generation (use with caution)
- `ForceUpdate` - Delete target files older than their source documentation before generation (use with caution)

**Examples:**

```yaml
# Always apply patches to existing files
update_mode: PatchExisting
```

```yaml
# Skip processing if target exists
update_mode: SkipExisting
```

```yaml
# Always fully overwrite
update_mode: OverwriteExisting
```

```yaml
# Force delete and regenerate
update_mode: ForceOverwrite
```

**Resolution Priority:** When multiple specifications apply to a single target file, the update mode is resolved in
this order:

1. `specifies` frontmatter (first non-null `update_mode`)
2. `transforms` frontmatter (first non-null `update_mode`)
3. `documents` frontmatter (first non-null `update_mode`)
4. `generates` frontmatter (first non-null `update_mode`)
5. Global `updateMode` configured on the `DocProcessor` instance

**Use Case:** Control how the processor handles existing target files. Use `PatchExisting` or
`PatchToUpdate` for incremental updates that preserve manual changes. Use `OverwriteExisting` or
`OverwriteToUpdate` for complete regeneration. Use `SkipExisting` to prevent accidental overwrites.
Use `ForceOverwrite` or `ForceUpdate` to delete targets before regeneration (dangerous).

---

### `prompt`

Specifies a custom prompt string to use as the task description instead of the auto-generated one. Only used when
there is exactly one spec for the target file.

**Type:** `String`

**Examples:**

```yaml
# Custom prompt for the AI
specifies: ../src/Main.kt
prompt: Refactor this file to use coroutines instead of callbacks
```

**Use Case:** Override the default task description with a specific instruction for the AI.

---

### `template_file`

Specifies a template file to use when processing the target. The path is resolved relative to the markdown file's
directory.

**Type:** `String`

**Examples:**

```yaml
specifies: ../src/Generated.kt
template_file: ./templates/class-template.kt
```

**Use Case:** Provide a template that guides the structure of generated files.

---

### `data_file`

Specifies a JSON data file to use as structured data input for template processing. The path is resolved relative to
the markdown file's directory.

**Type:** `String`

**Examples:**

```yaml
specifies: ../src/Generated.kt
template_file: ./templates/class-template.kt
data_file: ./data/model-config.json
```

**Implicit Detection:** If no explicit `data_file` is specified and a transform matches a JSON source file, that JSON
file is automatically used as the data source.

**Use Case:** Provide structured data that can be used in conjunction with templates for code generation.

---

---

## Complete Example

```yaml
---
specifies:
  - ../src/api/*.kt
  - ../src/models/*.kt
documents:
  - ../src/core/Engine.kt
transforms:
  - src/legacy/(.+)\.java -> src/modern/$1.kt
generates:
  output: ../generated/api-index.md
  inputs:
    - ../src/api/**/*.kt
related:
  - ../config/api-config.yaml
  - ./api-conventions.md
  - https://example.com/api-spec
update_mode: PatchExisting
task_type: FileModification
task_config_json: ./config/api-task-config.json
prompt: Update the API layer to conform to the latest specification
---

# API Documentation

This document specifies the API layer implementation...
```

## Processing Behavior

1. **Dependency Resolution:** Tasks are sorted topologically so dependencies are processed before dependents. Cycles are
   detected and broken automatically by selecting the task with the minimum remaining dependencies.

2. **File Resolution:** All paths in frontmatter are resolved relative to the markdown file's parent directory.

3. **Glob Expansion:**

- Simple globs (`*.kt`) match files in the specified directory
- Recursive globs (`**/*.kt`) match files in all subdirectories
- For `transforms`, the source pattern is a regex (not a glob) that matches against file paths relative to the doc
- Bracket patterns (`file[0-9].txt`) match character ranges
- Question mark patterns (`file?.txt`) match single characters
  file's directory
- Literal paths (without wildcards) are returned even if the target file doesn't exist, enabling file creation

4. **Multiple Specifications:** A single target file can be specified by multiple documentation files. All
   specifications are combined when processing.

5. **Overwrite Modes:** The processor supports different overwrite strategies for handling existing files:

- `SkipExisting` - Skip files that already exist (no processing)
- `OverwriteExisting` - Always overwrite existing files with full replacement
- `OverwriteToUpdate` - Overwrite only if source/related files are newer than target
- `PatchExisting` - Always apply fuzzy patch to existing files
- `PatchToUpdate` - Apply fuzzy patch only if source/related files are newer than target (default)
- `ForceOverwrite` - Delete all target files before generation (use with caution)
- `ForceUpdate` - Delete target files older than their source documentation before generation (use with caution)

6. **Task Description Generation:** The processor automatically generates appropriate task descriptions based on the
   frontmatter type:

- For `specifies`/`transforms`: Updates target files based on documentation and specifications
- For `documents`: Updates documentation to reflect current source code state
- For `generates`: Generates output files based on documentation and input files
- If a single spec has a `prompt` frontmatter key, that prompt is used directly as the task description
- For non-file task types: Processes the file according to the task type with documentation as context

7. **File Modification Time Checking:** For `OverwriteIfOlder` and `PatchIfOlder` modes, the processor compares the
   target file's last modified time against:

- The documentation file itself
- All related files specified in the frontmatter
- All source/input files that contribute to the target
  If any of these are newer than the target, the target will be processed.

8. **URL Fetching and Caching:** Related resources specified as URLs (http:// or https://) are automatically fetched
   and cached locally:

- Cache location: `.doc-processor-cache/url-cache` within the root directory
- Cache TTL: 1 hour (cached content older than 1 hour is re-fetched)
- HTML content is automatically simplified (scripts, styles, interactive elements removed)
- Non-HTML content is stored as-is
- Failed fetches log a warning and return null (the resource is skipped)
- Cache files use a SHA-256 hash prefix for uniqueness


9. **Rebasing:** Both `DocSpec` and `ModificationTask` support rebasing from one root directory to another. This is
   used when the IntelliJ action needs to adjust paths for a different working directory. URL-based related resources
   are preserved as-is during rebasing.
10. **Transitive Target Discovery:** The processor recursively discovers transitive targets in multi-stage build
    pipelines. After computing the initial set of targets, it checks if any newly-generated target files would match
    additional doc spec patterns (via transforms). If so, those hypothetical files are treated as existing and the
    expansion continues until a fixed-point is reached (no new targets are discovered) or a maximum recursion depth
    of 10 is reached. This enables proper dependency ordering for pipelines where intermediate artifacts are inputs
    to subsequent transformations.
11. **Status Tracking:** The processor maintains a `docops.status.json` file in the root directory that tracks the
    status of each target generation task. Status values include `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, and
    `CANCELLED`. The status file is updated atomically (write to temp file then rename) and is thread-safe. Status
    entries include timestamps for start/completion and optional session IDs and error messages.
12. **Path Normalization:** Target file paths are normalized to lowercase for case-insensitive comparison when
    aggregating specifications. This ensures that targets are grouped consistently regardless of case differences
    in paths across different doc specs.

## Data Structures

The frontmatter is parsed into a `DocSpec` containing:

| Field            | Type                  | Description                                                                |
|------------------|-----------------------|----------------------------------------------------------------------------|
| `docFile`        | `File`                | The markdown file itself                                                   |
| `specifies`      | `List<String>`        | Glob patterns for files this doc specifies                                 |
| `documents`      | `List<String>`        | Glob patterns for files this doc describes                                 |
| `transforms`     | `List<TransformSpec>` | Source-to-destination transformation rules                                 |
| `generates`      | `List<GenerateSpec>`  | Explicit generation specifications                                         |
| `related`        | `List<String>`        | Additional context files, glob patterns, or URLs                           |
| `taskType`       | `String?`             | Task type to use for processing (nullable, defaults to `FileModification`) |
| `taskConfigJson` | `String?`             | Path to JSON file with additional task configuration (nullable)            |
| `updateMode`     | `String?`             | Per-doc update mode override (nullable, falls back to global `updateMode`) |
| `content`        | `String`              | The markdown body (after frontmatter)                                      |
| `frontmatter`    | `Map<String, Any>`    | Raw parsed frontmatter                                                     |

**Note:** The `updateMode` field in `DocSpec` stores the per-doc override from the `update_mode` frontmatter key.
If null, the global `updateMode` configured at the `DocProcessor` level applies.

### TransformSpec

| Field                | Type     | Description                             |
|----------------------|----------|-----------------------------------------|
| `sourcePattern`      | `String` | Regex pattern to match source files     |
| `destinationPattern` | `String` | Destination pattern with backreferences |

### GenerateSpec

| Field    | Type           | Description                                 |
|----------|----------------|---------------------------------------------|
| `output` | `String`       | The output file path (relative to doc file) |
| `inputs` | `List<String>` | Glob patterns for input files               |

### ModificationTaskConfig

Represents the configuration for a single modification task:

| Field                 | Type                | Description                                              |
|-----------------------|---------------------|----------------------------------------------------------|
| `root`                | `File`              | The root directory for path resolution                   |
| `files`               | `List<String>?`     | Target file paths (absolute)                             |
| `related_files`       | `List<String>?`     | Related/context file paths (absolute)                    |
| `task_description`    | `String`            | Generated or custom task description                     |
| `data`                | `Map<String, Any>?` | Structured data from data_file or JSON source (nullable) |
| `taskConfigOverrides` | `Map<String, Any>?` | Overrides from task_config_json file (nullable)          |

**Computed Properties:**

- `relative_files` - Target file paths relative to root (computed from `files`)
- `relative_related_files` - Related file paths relative to root (computed from `related_files`)

### ModificationTask

Represents a complete modification task ready for execution:

| Field                | Type                     | Description                                        |
|----------------------|--------------------------|----------------------------------------------------|
| `data`               | `ModificationTaskConfig` | Task configuration                                 |
| `message`            | `(File) -> String`       | Function generating message content given root dir |
| `patchProcessor`     | `PatchProcessors`        | Patch processing strategy (default: Fuzzy)         |
| `shouldDeleteTarget` | `Boolean`                | Whether to delete the target file (default: false) |
| `taskType`           | `TaskType<*, *>`         | The resolved task type (default: FileModification) |

**Computed Properties:**

- `typeConfig` - Resolved `TaskTypeConfig` from `taskConfigOverrides` or task type defaults

## Additional Processing Classes

### TransformMatch

Represents a matched transformation from source to destination:

| Field             | Type      | Description                       |
|-------------------|-----------|-----------------------------------|
| `sourceFile`      | `File`    | The matched source file           |
| `destinationFile` | `File`    | The computed destination file     |
| `spec`            | `DocSpec` | The originating doc specification |

### GenerateMatch

Represents a matched generation specification:

| Field        | Type         | Description                       |
|--------------|--------------|-----------------------------------|
| `outputFile` | `File`       | The output file to generate       |
| `inputFiles` | `List<File>` | The resolved input files          |
| `spec`       | `DocSpec`    | The originating doc specification |

### DocumentMatch

Represents a documentation update specification:

| Field             | Type         | Description                                           |
|-------------------|--------------|-------------------------------------------------------|
| `docSpec`         | `DocSpec`    | The doc specification (target is the doc file itself) |
| `supportingFiles` | `List<File>` | Source files that provide context                     |

### TaskStatusEntry

Represents the status of a single target generation task in `docops.status.json`:
| Field | Type | Description |
|---------------|---------------|------------------------------------------------|
| `target`      | `String`      | The target file path |
| `status`      | `TaskStatus`  | Current status (PENDING/RUNNING/COMPLETED/FAILED/CANCELLED) |
| `sessionId`   | `String?`     | Associated session ID (nullable)               |
| `startedAt`   | `String?`     | ISO timestamp when task started (nullable)     |
| `completedAt` | `String?`     | ISO timestamp when task completed (nullable)   |
| `error`       | `String?`     | Error message if failed (nullable)             |

### DocOpsStatus

Root structure for `docops.status.json`:
| Field | Type | Description |
|---------------|-------------------------------|------------------------------------|
| `lastUpdated` | `String`                      | ISO timestamp of last update |
| `tasks`       | `Map<String, TaskStatusEntry>`| Map of target path to status entry |

## Implementation Notes

### Frontmatter Parsing

The frontmatter is parsed using a custom simple YAML parser (not SnakeYAML). The parser handles the following value
types:

- **String values**: Converted directly
- **List values**: Each element is converted to a string
- **Map values** (for `generates`): Parsed into `GenerateSpec` objects with `output` and `inputs` fields (note: the
  simple parser may have limitations with deeply nested YAML structures like maps within lists)

**Parser Behavior:**

- Lines are split on the first colon to extract key-value pairs
- Lines without colons are ignored
- If the value after the colon is empty, the parser looks for subsequent list items (lines starting with `- `)
- Empty keys (colon with no value and no subsequent list items) are not added to the result map
- Values are trimmed of whitespace

### Transform Pattern Matching

Transform patterns use Java regex syntax. The source pattern is matched against file paths relative to the documentation
file's directory. When a match is found:

1. The regex is applied to the relative file path
2. Capture groups are extracted from the match
3. Backreferences (`$0`, `$1`, etc.) in the destination pattern are replaced with the captured values. Backreferences
   support optional arithmetic modifiers (e.g., `$1+1`, `$2-5`): if the captured group is numeric, the arithmetic is
   applied; otherwise the modifier is appended as a literal string.
4. The destination path is resolved relative to the documentation file's directory

### Primary Source Resolution

When determining the primary source file for overwrite mode checks, the priority is:

1. First transform's source file
2. First spec's doc file
3. First document match's first supporting file (or doc file if no supporting files)
4. First generate match's first input file (or doc file if no input files)

### Error Handling

- Invalid frontmatter YAML will cause parsing to fail
- Missing required fields in `generates` entries (like `output`) will result in incomplete specifications
- Invalid regex patterns in `transforms` will cause matching to fail for those rules
- Unknown `task_type` values will log a warning and fall back to `FileModification`
- Invalid `task_config_json` paths will be stored but may cause errors during task execution
- Files without frontmatter (not starting with `---`) return null (silently skipped)
- Files with unclosed frontmatter (no closing `---`) return null
- Files with frontmatter but no `specifies`, `transforms`, `documents`, or `generates` keys return null
- Non-existent files referenced in `related` are still returned (downstream code handles them)
- URL fetch failures log a warning and return null (the resource is skipped)
- Errors processing individual target files are caught and logged; other targets continue processing
- Unknown `update_mode` values in frontmatter log a warning and fall back to the global update mode
- Recursive transitive target discovery is bounded to a maximum depth of 10 to prevent infinite loops

### IntelliJ Integration

The `DocProcessorAction` provides an IntelliJ IDE action that:

1. Filters selected files to markdown files (`.md` or `.markdown` extensions)
2. Creates a `DocProcessor` instance with the configured fast and smart models
3. Calls `getAll()` to collect all modification tasks from the selected files
4. Shows a `DocProcessorTaskDialog` with a checklist of tasks for user selection
5. Executes selected tasks via `UnifiedHarness` with progress tracking and cancellation support
6. Opens a browser session with a master task view linking to individual task sessions

The action is available through the `DocProcessorActionGroup` which provides a submenu with all update mode options:

- 🚫 Skip Existing Files (`SkipExisting`)
- 🔄 Overwrite All Files (`OverwriteExisting`)
- 📅 Overwrite Outdated Files (`OverwriteToUpdate`)
- 🩹 Patch Existing Files (`PatchExisting`)
- 📝 Patch Outdated Files (`PatchToUpdate`)
- 🔥 Force Overwrite (Dangerous) (`ForceOverwrite`)
- ⚡ Force Update (Dangerous) (`ForceUpdate`)

The dialog includes:

- An "Auto-fix issues" checkbox
- A search/filter field for tasks
- Select All / Deselect All buttons
- A selection count indicator
- Hover tooltips with task details (target files, related files, task description, config overrides)
- Right-click context menu with a "Details..." option showing a resizable detail dialog
- Task items sorted alphabetically by display name