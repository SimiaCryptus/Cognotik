---
documents: ../webui/src/main/kotlin/com/simiacryptus/cognotik/embed/DocProcessor.kt
specifies: ../site/cognotik.com/frontmatter.html
related: 
  - product_pages.md
  - ../site/cognotik.com/assets/styles/main.css
---

# Frontmatter Schema for DocProcessor

This document describes the YAML frontmatter schema used by `DocProcessor` to process markdown documentation files and manage relationships between documentation and source code.

## Overview

`DocProcessor` processes markdown files that contain YAML frontmatter blocks. The frontmatter specifies how the documentation relates to source files - either as specifications that drive code generation, as documentation that should be updated based on source files, or as transformation rules between files.

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

Defines glob patterns for files that this documentation specifies. The matched files will be created or updated based on the documentation content.

**Type:** `String` or `List<String>`

**Examples:**

```yaml
# Single file
specifies: ../src/utils/helper.kt

# Single glob pattern
specifies: ../src/**/*.kt

# Multiple patterns
specifies:
  - ../src/models/*.kt
  - ../src/utils/*.kt
```

**Glob Pattern Support:**
- Simple patterns: `*.kt`, `helper.kt`
- Recursive patterns: `**/*.kt` (matches files in all subdirectories)
- Paths are resolved relative to the markdown file's directory

---

### `documents`

Defines glob patterns for source files that this documentation describes. This is the inverse of `specifies` - the documentation file itself becomes the target to be updated based on the matched source files.

**Type:** `String` or `List<String>`

**Examples:**

```yaml
# Single file
documents: ../src/main/kotlin/MyClass.kt

# Multiple source files
documents:
  - ../src/**/*.kt
  - ../src/**/*.java
```

**Use Case:** Keep documentation in sync with source code changes. When source files change, the documentation can be automatically updated to reflect the current implementation.

---

### `transforms`

Defines regex-based transformation rules that map source files to destination files. Uses regex capture groups and backreferences for flexible file mapping.

**Type:** `String` or `List<String>`

**Format:** `sourcePattern -> destinationPattern`

- `sourcePattern`: A regex pattern to match source file paths (relative to the doc file's directory)
- `destinationPattern`: The destination path with backreferences (`$0`, `$1`, `$2`, etc.)

**Examples:**

```yaml
# Single transform
transforms: src/(.+)\.java -> generated/$1.kt

# Multiple transforms
transforms:
  - src/models/(.+)\.java -> kotlin/models/$1.kt
  - src/utils/(.+)\.java -> kotlin/utils/$1.kt
```

**Backreference Support:**
- `$0` - The entire matched string
- `$1`, `$2`, etc. - Captured groups from the regex pattern

---

### `generates`

Defines explicit output files to generate from specified input files. Unlike `transforms`, this doesn't use pattern matching - it explicitly lists the output file and its input sources.

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

# Multiple generate specs
generates:
  - output: ../generated/models.kt
    inputs:
      - ../src/models/**/*.kt
  - output: ../generated/utils.kt
    inputs:
      - ../src/utils/**/*.kt
```

**Use Case:** Generate aggregate files, combined outputs, or files that depend on multiple input sources.

---

### `related`

Specifies additional files to include as context when processing modification tasks. These files are not targets but provide supplementary information.

**Type:** `String` or `List<String>`

**Examples:**

```yaml
# Single related file
related: ../shared/constants.kt

# Multiple related files
related:
  - ../shared/constants.kt
  - ../config/settings.yaml
  - ./helper-docs.md
```

**Use Case:** Include configuration files, shared constants, or related documentation that provides context for the AI when processing the target files.

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
---

# API Documentation

This document specifies the API layer implementation...
```

## Processing Behavior

1. **Dependency Resolution:** Tasks are sorted topologically so dependencies are processed before dependents. Cycles are detected and broken automatically.

2. **File Resolution:** All paths in frontmatter are resolved relative to the markdown file's parent directory.

3. **Glob Expansion:**
  - Simple globs (`*.kt`) match files in the specified directory
  - Recursive globs (`**/*.kt`) match files in all subdirectories

4. **Multiple Specifications:** A single target file can be specified by multiple documentation files. All specifications are combined when processing.

5. **Overwrite Modes:** The processor supports different overwrite strategies (configured externally) for handling existing files.

## Data Structures

The frontmatter is parsed into a `DocSpec` containing:

| Field | Type | Description |
|-------|------|-------------|
| `docFile` | `File` | The markdown file itself |
| `specifies` | `List<String>` | Glob patterns for files this doc specifies |
| `documents` | `List<String>` | Glob patterns for files this doc describes |
| `transforms` | `List<TransformSpec>` | Source-to-destination transformation rules |
| `generates` | `List<GenerateSpec>` | Explicit generation specifications |
| `related` | `List<String>` | Additional context files |
| `content` | `String` | The markdown body (after frontmatter) |
| `frontmatter` | `Map<String, Any>` | Raw parsed frontmatter |
```
