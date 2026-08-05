# SymbolIndexer

`SymbolIndexer` crawls a source tree, runs the language-specific
[`GrammarValidator`](GrammarValidator.kt) resolved by
[`FileValidators`](FileValidators.kt) against every supported file, and persists the extracted **public symbols**,
**grammar-level symbol references** and (optionally)
**validation diagnostics** as JSON sidecar files plus one whole-project manifest.

Referenced names are additionally matched **lexically** (never semantically) against the qualified names of every
indexed file by [`SymbolResolver`](SymbolResolver.kt), and the outcome is stored as `resolutions` / `unresolvedNames`.
Ambiguous names are ranked by **file-path distance** and, by default, only the nearest candidate is kept — see
[Reference resolution](#12-reference-resolution-lexical).
Two kinds of noise are suppressed by default: references that only resolve **inside their own file** are hidden
everywhere, and **ambiguous** resolutions are hidden from the reports (`project.json` and the per-folder
`package.json` rollups) while the sidecars keep them — see
[Same-file resolutions](#125-same-file-resolutions-are-hidden) and
[Folder rollups](#13-folder-rollups-packagejson).

It is intentionally dumb about *semantics*: no imports, scoping or overload resolution are considered. The output is a
fast, greppable, machine-readable index that higher layers (search, RAG, refactoring tools, real symbol resolution) can
build on.

---

## 1. Output layout

Given the default `Config.dataDirName` of `.data`:

```
<root>/
  package/
    foo.java
    .data/
      foo.java.json          <- FileRecord for package/foo.java
      package.json           <- rollup for package/ (includes package/sub/)
    sub/
      bar.kt
      .data/
        bar.kt.json          <- FileRecord for package/sub/bar.kt
        package.json         <- rollup for package/sub/
  .data/
    project.json             <- Manifest (all FileRecords + failures)
```

Rules:

* Sidecar name is **`<original file name>.json`** — the extension is *kept*, so
  `Foo.kt` and `Foo.java` in the same directory do not collide.
* Sidecars live in a `.data` folder **next to the source file**, never in a mirrored tree.
* The manifest lives in `<root>/.data/project.json` and contains a **slim copy** of every
  `FileRecord` (the `symbols` and `references` detail lists are dropped unless
  `Config.includeDetailsInManifest` is set — see
  [Size considerations](#8-size-and-performance-considerations)).
* Every folder containing indexed files also gets `<folder>/.data/package.json`: a `Manifest`
  summarizing that folder **and everything below it**, built with exactly the same rules as
  `project.json` (slimming, ambiguity hiding, counts). The crawl root has no `package.json` —
  `project.json` *is* its rollup. See [Folder rollups](#13-folder-rollups-packagejson).
* All `.data` folders are skipped during crawling, so the index never indexes itself.

---

## 2. Usage

### Kotlin / Java API

```kotlin
// One-liner with default settings
val manifest = SymbolIndexer.index(File("/path/to/project"))

// Explicit configuration
val indexer = SymbolIndexer(
  root = Paths.get("/path/to/project"),
  config = SymbolIndexer.Config(
    incremental = true,
    parallel = true,
    includeValidationErrors = false,
    includeReferenceDetails = false,   // keep counts + distinct names only
    resolveReferences = true,          // lexical, project-wide
    maxResolutionTargets = 1,          // nearest candidate only (default)
    excludeSelfFileResolutions = true, // hide names that only resolve in their own file
    hideAmbiguousInReports = true,     // no ambiguous entries in project/package.json
    writePackageManifests = true,      // per-folder rollups
    maxFileSizeBytes = 8L * 1024 * 1024,
  )
)
val manifest = indexer.index()

// Read back later without re-parsing
val cached: SymbolIndexer.Manifest? = indexer.loadManifest()
val folderRollup: SymbolIndexer.Manifest? =
  indexer.loadPackageManifest(Paths.get("/path/to/project/src/main/kotlin"))

// Index a single file (writes only that file's sidecar, not the manifest)
val record = indexer.indexFile(Paths.get("/path/to/project/src/Foo.kt"))

// Remove every generated .data folder under root
indexer.clean()
```

### Command line

```
SymbolIndexer <root> [--clean] [--no-incremental] [--sequential]
                     [--no-errors] [--no-references] [--no-reference-details]
                     [--no-resolve] [--manifest-details]
                     [--self-refs] [--keep-ambiguous] [--no-package-manifests]
```

| Flag                     | Effect                                                                                 |
|--------------------------|----------------------------------------------------------------------------------------|
| *(positional)* `<root>`  | Directory (or single file) to index. First non-`--` argument. Default `.`              |
| `--clean`                | Delete all existing `.data` folders **before** indexing                                |
| `--no-incremental`       | `Config.incremental = false` — always re-parse                                         |
| `--sequential`           | `Config.parallel = false` — single-threaded                                            |
| `--no-errors`            | `Config.includeValidationErrors = false`                                               |
| `--no-references`        | `Config.includeReferences = false`                                                     |
| `--no-reference-details` | `Config.includeReferenceDetails = false` (counts + distinct names only)                |
| `--no-resolve`           | `Config.resolveReferences = false` — skip lexical resolution                           |
| `--self-refs`            | `Config.excludeSelfFileResolutions = false` — keep same-file resolutions               |
| `--keep-ambiguous`       | `Config.hideAmbiguousInReports = false` — keep ambiguous entries in the reports        |
| `--no-package-manifests` | `Config.writePackageManifests = false` — skip the per-folder rollups                   |
| `--manifest-details`     | `Config.includeDetailsInManifest = true` — keep `symbols`/`references` in the manifest |

Unknown `--flags` are ignored. Only the *first* non-flag argument is used as the root.

Stdout:

```
123 files, 4567 symbols, 89012 references, 7890 resolved, 1234 unresolved -> /path/to/project/.data/project.json
FAILED src/Broken.kt: Unexpected token at 12:4
```

---

## 3. Configuration reference (`SymbolIndexer.Config`)

| Field                      | Type          | Default                                                                                   | Meaning                                                                                                          |
|----------------------------|---------------|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `dataDirName`              | `String`      | `".data"`                                                                                 | Hidden folder written beside each source file; also skipped while crawling                                       |
| `manifestName`             | `String`      | `"project.json"`                                                                          | Manifest file name inside `<root>/<dataDirName>`                                                                 |
| `packageManifestName`      | `String`      | `"package.json"`                                                                          | Per-folder rollup file name inside `<folder>/<dataDirName>`                                                      |
| `writePackageManifests`    | `Boolean`     | `true`                                                                                    | Write a rollup summary for every folder containing indexed files                                                  |
| `excludedDirNames`         | `Set<String>` | `.git .hg .svn .gradle .idea .vscode build out target dist node_modules venv __pycache__` | Directories never descended into                                                                                 |
| `skipHiddenDirs`           | `Boolean`     | `true`                                                                                    | Skip any directory whose name starts with `.` (also covers `.data`)                                              |
| `maxFileSizeBytes`         | `Long`        | `4 MiB` (`4 * 1024 * 1024`)                                                               | Larger files are silently skipped (logged at DEBUG)                                                              |
| `includeValidationErrors`  | `Boolean`     | `true`                                                                                    | Also run `validateGrammar` and store `errors`                                                                    |
| `includeReferences`        | `Boolean`     | `true`                                                                                    | Also run `extractSymbolReferences`                                                                               |
| `includeReferenceDetails`  | `Boolean`     | `true`                                                                                    | Store the full `references` array; when `false`, only `referenceCount` + `referencedNames` are kept              |
| `maxReferencesPerFile`     | `Int`         | `50_000`                                                                                  | Truncates the stored `references` array; `referenceCount` and `referencedNames` still reflect *everything* found |
| `resolveReferences`        | `Boolean`     | `true`                                                                                    | Match `referencedNames` against the `qualifiedNames` of every indexed file (`SymbolResolver`)                    |
| `maxResolutionTargets`     | `Int`         | `SymbolResolver.DEFAULT_MAX_TARGETS` (`1`)                                                | Candidate declarations stored per resolved name, nearest first; values `< 1` are treated as `1`                  |
| `excludeSelfFileResolutions` | `Boolean`   | `true`                                                                                    | Drop candidates declared in the referencing file; names with *only* such candidates are hidden entirely           |
| `hideAmbiguousInReports`   | `Boolean`     | `true`                                                                                    | Omit `ambiguous = true` resolutions from `project.json` / `package.json` (sidecars keep them)                     |
| `includeDetailsInManifest` | `Boolean`     | `false`                                                                                   | Keep the verbose `symbols` / `references` lists in `project.json`                                                |
| `incremental`              | `Boolean`     | `true`                                                                                    | Reuse the existing sidecar when size **and** `lastModified` match                                                |
| `parallel`                 | `Boolean`     | `true`                                                                                    | Parse on the common ForkJoinPool via `parallelStream()`                                                          |
| `followSymlinks`           | `Boolean`     | `false`                                                                                   | Passes `FileVisitOption.FOLLOW_LINKS` to `walkFileTree`                                                          |

> `Config` is a Kotlin `data class` with defaults for every field; it is not itself
> persisted in the JSON output.

---

## 4. JSON schema

### 4.1 Serialization conventions

All JSON is produced by `SymbolIndexer.mapper`:

* `SerializationFeature.INDENT_OUTPUT` — pretty printed, 2-space indent.
* `JsonInclude.Include.NON_EMPTY` — **`null`s, empty strings and empty arrays are omitted**. Consumers must treat a
  missing field as "empty/absent". Numeric `0` values *are*
  written (Jackson ≥ 2.6 no longer folds primitive defaults into `NON_EMPTY`).
* `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` disabled, plus
  `@JsonIgnoreProperties(ignoreUnknown = true)` on every record → forward/backward compatible reads; new fields can be
  added without breaking old readers.
* Every record type has default values for all fields, so partially populated JSON deserializes cleanly.
* Timestamps are **ISO-8601 UTC instants** (`Instant.toString()`, e.g.
  `2024-05-17T10:32:11.482Z`).
* Paths inside records are **relative to `root`, always `/`-separated**, regardless of platform. If a path cannot be
  relativized against `root`, the absolute path is stored instead (still `/`-normalized).

### 4.2 `Manifest` — `<root>/.data/project.json` and `<folder>/.data/package.json`

| Field                 | JSON type           | Notes                                                                             |
|-----------------------|---------------------|-----------------------------------------------------------------------------------|
| `root`                | `string`            | Absolute, normalized crawl root (platform separators, **not** normalized to `/`)  |
| `folder`              | `string`            | Root-relative folder this report covers; omitted (`""`) for `project.json`         |
| `generatedAt`         | `string` (ISO-8601) | When `index()` finished building the manifest                                     |
| `fileCount`           | `integer`           | `files.size` — successfully indexed files only                                    |
| `symbolCount`         | `integer`           | Sum of `files[].symbolCount` (includes nested symbols)                            |
| `referenceCount`      | `integer`           | Sum of `files[].referenceCount` (pre-truncation totals)                           |
| `resolvedNameCount`   | `integer`           | Sum of `files[].resolutions.size` **as stored in this report** — i.e. after ambiguous entries were hidden |
| `unresolvedNameCount` | `integer`           | Sum of `files[].unresolvedNames.size` — referenced names that matched nothing     |
| `files`               | `array<FileRecord>` | Sorted ascending by `path`; **slim** unless `includeDetailsInManifest`            |
| `failures`            | `array<Failure>`    | Sorted ascending by `path`; omitted when empty                                    |
`package.json` rollups use the very same shape; `files[].path` stays relative to `root` (never to
`folder`) so records can be compared across reports.

### 4.3 `FileRecord` — `<dir>/.data/<file>.json` and `Manifest.files[]`

| Field             | JSON type                | Notes                                                                                                        |
|-------------------|--------------------------|--------------------------------------------------------------------------------------------------------------|
| `path`            | `string`                 | Relative, `/`-separated, e.g. `src/main/kotlin/Foo.kt`                                                       |
| `name`            | `string`                 | File name with extension, e.g. `Foo.kt`                                                                      |
| `extension`       | `string`                 | Text after the last `.`; `""` (omitted) when there is none                                                   |
| `size`            | `integer` (int64)        | Source file size in bytes at index time                                                                      |
| `lastModified`    | `string` (ISO-8601)      | Source file's last modified time; part of the incremental key                                                |
| `contentHash`     | `string`                 | Lowercase hex SHA-256 of the raw file bytes (64 chars)                                                       |
| `validator`       | `string`                 | Simple class name of the `GrammarValidator` used, e.g. `KotlinValidator`                                     |
| `symbolCount`     | `integer`                | `symbols.sumOf { it.flatten().size }` — **includes nested** symbols                                          |
| `symbols`         | `array<SymbolInfo>`      | Top-level public symbols; nested members live in `children`                                                  |
| `qualifiedNames`  | `array<string>`          | Flattened dotted names for fast lookup/grep, e.g. `Foo.bar`                                                  |
| `referenceCount`  | `integer`                | Total references found **before** truncation                                                                 |
| `references`      | `array<SymbolReference>` | Empty/omitted when `includeReferenceDetails = false`; otherwise capped at `maxReferencesPerFile`             |
| `referencedNames` | `array<string>`          | Distinct `references[].name`, sorted ascending — always populated when `includeReferences = true`            |
| `resolutions`     | `array<Resolution>`      | One entry per *resolved* referenced name, sorted ascending by `name`; empty when `resolveReferences = false` |
| `unresolvedNames` | `array<string>`          | Distinct referenced names that matched no `qualifiedNames` anywhere, sorted ascending                        |
| `errors`          | `array<ValidationError>` | Empty/omitted when `includeValidationErrors = false` or the file is clean                                    |

Invariants worth relying on:

* `referenceCount >= references.size` (equality unless truncated or details disabled).
* `referencedNames` is derived from the **full** reference list, never from the truncated one.
* `resolutions.size + unresolvedNames.size <= referencedNames.size` when
  `resolveReferences = true`; the difference is the set of names whose *only* declarations live in
  this very file (see [Same-file resolutions](#125-same-file-resolutions-are-hidden)). Equality
  holds when `excludeSelfFileResolutions = false`.
* No `resolutions[].targets[].path` equals the record's own `path` (unless
  `excludeSelfFileResolutions = false`).
* In `project.json` / `package.json` no resolution has `ambiguous = true` (unless
  `hideAmbiguousInReports = false`); the sidecars always keep the full set, so
  `sidecar.resolutions.size >= manifest.files[i].resolutions.size`.
* `resolutions[].targets.size <= min(candidateCount, maxResolutionTargets)`;
  `candidateCount` is the number of matches **after** same-file candidates were dropped and
  **before** truncation.
* `ambiguous == (candidateCount > 1)`, independent of how many targets were kept.
* `symbolCount` counts nested symbols; `symbols.size` counts only top-level ones.
* `contentHash` is informational — it is **not** used by the incremental check.

### 4.4 `Failure` — `Manifest.failures[]`

| Field   | JSON type | Notes                                                                  |
|---------|-----------|------------------------------------------------------------------------|
| `path`  | `string`  | Relative, `/`-separated path of the file that could not be indexed     |
| `error` | `string`  | `Throwable.message`, falling back to the exception's simple class name |

A `Failure` is recorded when `indexFile` throws — e.g. no validator resolved, I/O error, or a JSON write failure. Note
that *symbol extraction*, *validation* and *reference extraction* failures are caught individually and degrade to empty
lists (logged at WARN)
rather than failing the whole file.

### 4.5 `GrammarValidator.SymbolInfo` (nested, recursive)

Defined by `GrammarValidator`, serialized verbatim by the indexer. The contract the indexer relies on is
`flatten(): List<SymbolInfo>` and `qualifiedNames(): List<String>`; the on-disk shape typically looks like:

```json
{
  "name": "MyClass",
  "kind": "class",
  "signature": "class MyClass : Base",
  "modifiers": [
    "public"
  ],
  "startLine": 12,
  "endLine": 84,
  "children": [
    {
      "name": "doWork",
      "kind": "function",
      "signature": "fun doWork(x: Int): String",
      "startLine": 20,
      "endLine": 26
    }
  ]
}
```

* `children` is the recursion point; nesting depth is unbounded.
* `qualifiedNames()` produces the dotted concatenation of `name` down each branch (`MyClass`, `MyClass.doWork`, …) which
  is what lands in `FileRecord.qualifiedNames`.
* Because of `NON_EMPTY`, absent/empty fields (e.g. `children`, `modifiers`) are omitted.
* Treat the exact field set as validator-defined and **read it tolerantly**; consult
  `GrammarValidator.SymbolInfo` for the authoritative definition.

### 4.6 `GrammarValidator.SymbolReference`

Grammar-level, **unresolved** reference to a name occurring in the file. The only field the indexer itself depends on is
`name` (used for `referencedNames`). Typical shape:

```json
{
  "name": "doWork",
  "line": 42,
  "column": 17,
  "context": "result = target.doWork(3)"
}
```

No cross-file resolution is attempted: a reference is a syntactic occurrence, and the same name may appear many times.
`extractSymbolReferences(code, symbols)` receives the file's own symbol list so validators can exclude declarations if
they choose to.

### 4.7 `GrammarValidator.ValidationError`

Diagnostics from `validateGrammar(code)`. Typical shape:

```json
{
  "message": "Unexpected token '}'",
  "line": 118,
  "column": 3,
  "severity": "ERROR"
}
```

### 4.7b `SymbolResolver.Resolution` / `SymbolResolver.Target`

One `Resolution` per referenced name that matched at least one declaration anywhere in the index. Targets are ordered
**nearest first** (see
[Reference resolution](#12-reference-resolution-lexical)).

```json
{
  "name": "Greeter",
  "targets": [
    {
      "qualifiedName": "demo.Greeter",
      "path": "src/main/kotlin/Greeter.kt"
    }
  ],
  "ambiguous": true,
  "candidateCount": 3,
  "distance": 0
}
```

| Field            | JSON type       | Notes                                                                                        |
|------------------|-----------------|----------------------------------------------------------------------------------------------|
| `name`           | `string`        | The referenced name, exactly as it appeared in `referencedNames`                             |
| `targets`        | `array<Target>` | Nearest candidates first, capped at `Config.maxResolutionTargets` (default `1`)              |
| `ambiguous`      | `boolean`       | `true` when more than one declaration matched — even if only one target was kept; such entries are omitted from the reports |
| `candidateCount` | `integer`       | Matching declarations after same-file candidates were dropped, **before** truncation          |
| `distance`       | `integer`       | Directory-traversal distance from this file to `targets[0].path` (`0` = same directory)       |

`Target` fields:
| Field | JSON type | Notes | |-----------------|-----------|-------| | `qualifiedName` | `string`  | Dotted declaration
name as produced by `SymbolInfo.qualifiedNames()` | | `path`          | `string`  | Root-relative, `/`-separated path of
the declaring file |

### 4.8 JSON Schema (draft 2020-12, illustrative)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://simiacryptus.com/cognotik/symbol-index/manifest.json",
  "title": "SymbolIndexer Manifest",
  "type": "object",
  "additionalProperties": true,
  "properties": {
    "root": {
      "type": "string"
    },
    "folder": {
      "type": "string"
    },
    "generatedAt": {
      "type": "string",
      "format": "date-time"
    },
    "fileCount": {
      "type": "integer",
      "minimum": 0
    },
    "symbolCount": {
      "type": "integer",
      "minimum": 0
    },
    "referenceCount": {
      "type": "integer",
      "minimum": 0
    },
    "resolvedNameCount": {
      "type": "integer",
      "minimum": 0
    },
    "unresolvedNameCount": {
      "type": "integer",
      "minimum": 0
    },
    "files": {
      "type": "array",
      "items": {
        "$ref": "#/$defs/FileRecord"
      }
    },
    "failures": {
      "type": "array",
      "items": {
        "$ref": "#/$defs/Failure"
      }
    }
  },
  "$defs": {
    "FileRecord": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "path": {
          "type": "string"
        },
        "name": {
          "type": "string"
        },
        "extension": {
          "type": "string"
        },
        "size": {
          "type": "integer",
          "minimum": 0
        },
        "lastModified": {
          "type": "string",
          "format": "date-time"
        },
        "contentHash": {
          "type": "string",
          "pattern": "^[0-9a-f]{64}$"
        },
        "validator": {
          "type": [
            "string",
            "null"
          ]
        },
        "symbolCount": {
          "type": "integer",
          "minimum": 0
        },
        "symbols": {
          "type": "array",
          "items": {
            "$ref": "#/$defs/SymbolInfo"
          }
        },
        "qualifiedNames": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "referenceCount": {
          "type": "integer",
          "minimum": 0
        },
        "references": {
          "type": "array",
          "items": {
            "$ref": "#/$defs/SymbolReference"
          }
        },
        "referencedNames": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "resolutions": {
          "type": "array",
          "items": {
            "$ref": "#/$defs/Resolution"
          }
        },
        "unresolvedNames": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "errors": {
          "type": "array",
          "items": {
            "$ref": "#/$defs/ValidationError"
          }
        }
      }
    },
    "SymbolInfo": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "name": {
          "type": "string"
        },
        "kind": {
          "type": "string"
        },
        "signature": {
          "type": "string"
        },
        "modifiers": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "startLine": {
          "type": "integer"
        },
        "endLine": {
          "type": "integer"
        },
        "children": {
          "type": "array",
          "items": {
            "$ref": "#/$defs/SymbolInfo"
          }
        }
      }
    },
    "SymbolReference": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "name": {
          "type": "string"
        },
        "line": {
          "type": "integer"
        },
        "column": {
          "type": "integer"
        },
        "context": {
          "type": "string"
        }
      },
      "required": [
        "name"
      ]
    },
    "Resolution": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "name": {
          "type": "string"
        },
        "targets": {
          "type": "array",
          "items": {
            "$ref": "#/$defs/Target"
          }
        },
        "ambiguous": {
          "type": "boolean"
        },
        "candidateCount": {
          "type": "integer",
          "minimum": 0
        },
        "distance": {
          "type": "integer",
          "minimum": 0
        }
      },
      "required": [
        "name"
      ]
    },
    "Target": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "qualifiedName": {
          "type": "string"
        },
        "path": {
          "type": "string"
        }
      }
    },
    "ValidationError": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "message": {
          "type": "string"
        },
        "line": {
          "type": "integer"
        },
        "column": {
          "type": "integer"
        },
        "severity": {
          "type": "string"
        }
      }
    },
    "Failure": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "path": {
          "type": "string"
        },
        "error": {
          "type": "string"
        }
      }
    }
  }
}
```

A per-file sidecar validates against `#/$defs/FileRecord`.

---

## 5. Example output

### `src/main/kotlin/Greeter.kt`

```kotlin
package demo

class Greeter(private val name: String) {
  fun greet(): String = "Hello, " + name
}
```

### `src/main/kotlin/.data/Greeter.kt.json`

```json
{
  "path": "src/main/kotlin/Greeter.kt",
  "name": "Greeter.kt",
  "extension": "kt",
  "size": 118,
  "lastModified": "2024-05-17T10:31:02.117Z",
  "contentHash": "9f2c1a0e5b7d4f83a61c0d2e8b5f7a41c3d9e6b2a8f10c4d7e5b3a9f1c8d6e2b",
  "validator": "KotlinValidator",
  "symbolCount": 2,
  "symbols": [
    {
      "name": "Greeter",
      "kind": "class",
      "startLine": 3,
      "endLine": 5,
      "children": [
        {
          "name": "greet",
          "kind": "function",
          "signature": "fun greet(): String",
          "startLine": 4,
          "endLine": 4
        }
      ]
    }
  ],
  "qualifiedNames": [
    "Greeter",
    "Greeter.greet"
  ],
  "referenceCount": 3,
  "references": [
    {
      "name": "String",
      "line": 3,
      "column": 38
    },
    {
      "name": "String",
      "line": 4,
      "column": 18
    },
    {
      "name": "name",
      "line": 4,
      "column": 45
    }
  ],
  "referencedNames": [
    "String",
    "name"
  ],
  "unresolvedNames": [
    "String"
  ]
}
```

Note the omitted `errors` array (`NON_EMPTY`) and that `referenceCount` (3) exceeds
`referencedNames.size` (2). `String` is unresolved because no indexed file *declares* it, and
`name` produces **no** `resolutions` entry at all: its only declaration (`Greeter.name`) lives in
this very file, so it is hidden (`excludeSelfFileResolutions`).

### `.data/project.json`

```json
{
  "root": "/home/dev/demo",
  "generatedAt": "2024-05-17T10:31:02.640Z",
  "fileCount": 1,
  "symbolCount": 2,
  "referenceCount": 3,
  "resolvedNameCount": 0,
  "unresolvedNameCount": 1,
  "files": [
    {
      "...": "the FileRecord above, verbatim"
    }
  ]
}
```
### `src/main/kotlin/.data/package.json`
```json
{
  "root": "/home/dev/demo",
  "folder": "src/main/kotlin",
  "generatedAt": "2024-05-17T10:31:02.640Z",
  "fileCount": 1,
  "symbolCount": 2,
  "referenceCount": 3,
  "unresolvedNameCount": 1,
  "files": [
    {
      "...": "the same slim FileRecord, paths still relative to root"
    }
  ]
}
```
`src/.data/package.json` and `src/main/.data/package.json` contain the same rollup (they are
ancestors of the only indexed file).

---

## 6. Crawling semantics

`collectFiles()`:

1. If `root` is **not** a directory: return `[root]` when it is a regular file and
   `FileValidators.isSupported(name)` is true, otherwise an empty list.
2. Otherwise walk the tree with unlimited depth, skipping subtrees whose directory name

* equals `config.dataDirName`, or
* is in `config.excludedDirNames`, or
* starts with `.` when `config.skipHiddenDirs` is set. The root directory itself is always descended, even if its own
  name is hidden.

3. Skip non-regular files and files larger than `maxFileSizeBytes`.
4. Keep files for which `FileValidators.isSupported(fileName)` is true.
5. Result is sorted by natural `Path` order.

Unreadable entries are reported to `visitFileFailed` and logged at DEBUG; they do **not**
appear in `failures`.

---

## 7. Incremental behaviour

When `Config.incremental = true`, `indexFile` reads the existing sidecar and returns it unchanged if **both**:

* `existing.size == attrs.size()`, and
* `existing.lastModified == attrs.lastModifiedTime().toInstant().toString()`

Consequences / caveats:

* `contentHash` is *not* consulted; a same-size, same-timestamp edit is missed. Use
  `--no-incremental` when timestamps are untrustworthy (e.g. some archive extractions).
* A cached record is reused **even if the configuration changed**. After toggling
  `includeReferences`, `includeReferenceDetails`, `includeValidationErrors` or
  `maxReferencesPerFile`, run with `--no-incremental` (or `--clean`) so records are regenerated consistently.
* Resolution is *not* cached: `index()` recomputes `resolutions` / `unresolvedNames` for every record (cached or freshly
  parsed) after the whole tree has been crawled, so a changed `maxResolutionTargets` takes effect without a full
  re-parse.
* Upgrading a `GrammarValidator` also requires a non-incremental run to take effect.
* `indexFile` always (re)writes the sidecar only when it actually re-parses; a cache hit performs no write.

---

## 8. Size and performance considerations

* The manifest stores a **slim** copy of every `FileRecord` (no `symbols`, no
  `references`), but keeps `qualifiedNames`, `referencedNames` and `resolutions`. With `includeDetailsInManifest = true`
  its size is roughly the sum of all sidecars and can reach hundreds of MB on large repos. Mitigations: leave it`false`,
  set
  `includeReferenceDetails = false` (keeps `referenceCount` + `referencedNames`), lower
  `maxReferencesPerFile` / `maxResolutionTargets`, or read sidecars individually instead of
  `loadManifest()`.
* Resolution is `O(references)` hash lookups against an in-memory suffix index built from every `qualifiedNames` entry
  (one entry per dot-boundary suffix); keeping
  `maxResolutionTargets` at `1` keeps the stored output small even for very common names.
* Folder rollups are a *rollup*: a file at depth `d` is written into `d` `package.json` reports, so the
  total rollup bytes are roughly `depth ×` the manifest size. Set
  `writePackageManifests = false` (`--no-package-manifests`) on very deep trees, and leave
  `hideAmbiguousInReports = true` / `includeDetailsInManifest = false` to keep each report small.
* `INDENT_OUTPUT` makes files human-readable at ~20–30 % size cost.
* `parallel = true` uses the **common ForkJoinPool**; avoid running several indexers concurrently inside a
  latency-sensitive service, or set `parallel = false`.
* Failures are collected in a `Collections.synchronizedList`, so parallel indexing is safe.
* `Files.readAllBytes` loads whole files into memory — `maxFileSizeBytes` is the guard.

---

## 9. Cleaning

```kotlin
indexer.clean()
```

Walks `root`, collects every directory named `config.dataDirName` (not descending into them), and deletes each one
depth-first (`Comparator.reverseOrder()`). Individual delete failures are logged at WARN and do not abort the operation.
Nothing happens when `root`
is not a directory.

---

## 10. Key API surface

| Member                                        | Description                                                          |
|-----------------------------------------------|----------------------------------------------------------------------|
| `SymbolIndexer(Path, Config)`                 | Primary constructor                                                  |
| `SymbolIndexer(File, Config)`                 | Convenience constructor                                              |
| `index(): Manifest`                           | Full crawl → sidecars + manifest → manifest                          |
| `indexFile(Path): FileRecord`                 | Parse one file, write its sidecar; throws if no validator matches    |
| `collectFiles(): List<Path>`                  | The candidate file list, sorted                                      |
| `dataFileFor(Path): Path`                     | `dir/foo.kt` → `dir/.data/foo.kt.json`                               |
| `manifestFile(): Path`                        | `<root>/<dataDirName>/<manifestName>`                                |
| `packageManifestFile(Path \| String): Path`    | `dir/.data/package.json` (folder rollup location)                    |
| `loadManifest(): Manifest?`                   | Read the manifest, `null` if missing/unparseable                     |
| `loadPackageManifest(Path): Manifest?`        | Read one folder's rollup, `null` if missing/unparseable              |
| `clean()`                                     | Delete all `.data` folders under `root`                              |
| `SymbolIndexer.mapper`                        | Shared, pre-configured Jackson `ObjectMapper` (reuse it for reads)   |
| `SymbolIndexer.index(File)` *(static)*        | One-liner with default config                                        |
| `SymbolIndexer.main(Array<String>)`           | CLI entry point                                                      |
| `SymbolResolver.resolve(Manifest)`            | Recompute `resolutions` of an existing manifest (sidecars untouched) |
| `SymbolResolver.buildIndex(List<FileRecord>)` | Suffix → declarations table, reusable across `resolve` calls         |
| `SymbolResolver.pathDistance(from, to)`       | Directory-traversal distance between two root-relative paths         |
| `FileRecord.withoutAmbiguousResolutions()`    | Report-friendly copy with `ambiguous` resolutions removed            |

---

## 11. Known limitations

* **Resolution is purely lexical.** Suffix matching ignores imports, scoping, visibility and overloads; `targets` is a
  best-effort guess, `ambiguous`/`candidateCount` tell you how much to trust it.
* **Single-file root edge case.** When `root` is a *file*, `manifestFile()` resolves to
  `<file>/.data/project.json`, which is not a valid path on most filesystems and will make `index()` fail on write.
  Always pass a directory to `index()`; use `indexFile()`
  for one-off files.
* **Root-relative paths only.** `FileRecord.path` is meaningless without `Manifest.root`; keep them together.
* **`Manifest.root` is not `/`-normalized** (unlike `FileRecord.path`), so it contains platform separators.
* **No deletion tracking.** Sidecars of removed source files are not pruned; they simply stop appearing in the manifest.
  Use `clean()` for a fresh start.
* **Validator coverage** is whatever `FileValidators` supports; unsupported files are silently skipped (not reported as
  failures).
* **Path distance is textual.** It is computed from root-relative `/`-separated paths, so symlinks, generated-source
  mirrors and multi-module layouts can make "nearest" surprising.
* **`indexFile()` resolves against one file only** — the file's own symbols. Because same-file
  candidates are hidden by default, its `resolutions` list is normally *empty* and every referenced
  name that is not declared elsewhere lands in `unresolvedNames`. Use `index()` for project-wide
  resolution (or `excludeSelfFileResolutions = false` if you really want self references).
* **Reports lose information on purpose.** `project.json` / `package.json` drop ambiguous
  resolutions, so their `resolvedNameCount` is *lower* than the sum over the sidecars. Read the
  sidecars (or pass `--keep-ambiguous`) when you need every candidate.
* **Rollups are not pruned.** A `package.json` for a folder that no longer contains indexed files is
  left behind; use `clean()`.

---

## 12. Reference resolution (lexical)

When `Config.resolveReferences` is set (default), `index()` resolves *after* the whole tree has been parsed, so every
file sees every other file's declarations.

### 12.1 Suffix index

Each `qualifiedNames` entry is registered under **all of its dot-boundary suffixes**:

```
com.foo.Bar.baz  ->  baz | Bar.baz | foo.Bar.baz | com.foo.Bar.baz
```

A referenced name therefore matches every declaration whose qualified name *ends with*
that name at a dot boundary. Before lookup the name is normalized: argument lists (`f(...)`), generics (`<...>`),
trailing `!!` / `?`, `[]` decorations and stray dots are stripped.

### 12.2 Ranking by file-path distance

Ambiguous matches are ordered by:

1. **Same file** as the reference (always first).
2. **File-path distance** — the number of directory traversals needed to walk from the referencing file to the declaring
   file: the `..` hops up to the common ancestor plus the descents back down.
3. Fewest dots in the qualified name (shallowest declaration).
4. Qualified name, then path (alphabetical, for a stable result). Distance examples (`from = a/b/Foo.kt`):

| Declaring file | Distance | Why                      |
|----------------|----------|--------------------------|
| `a/b/Foo.kt`   | `0`      | same file                |
| `a/b/Bar.kt`   | `0`      | same directory           |
| `a/Bar.kt`     | `1`      | one `..`                 |
| `a/c/Bar.kt`   | `2`      | one `..`, one descent    |
| `x/y/z/Bar.kt` | `5`      | two `..`, three descents |

### 12.3 Truncation

`Config.maxResolutionTargets` (default `SymbolResolver.DEFAULT_MAX_TARGETS = 1`) caps
`targets`, so by default only the **closest** declaration is stored. Truncation never hides the ambiguity:

* `ambiguous` stays `true` whenever more than one declaration matched, and
* `candidateCount` keeps the **raw** match count. Raise `maxResolutionTargets` when you want the full candidate list;
  values below `1` are clamped to `1`.
### 12.5 Same-file resolutions are hidden
A reference that resolves to a declaration of **its own file** tells you nothing about the project graph, so
`Config.excludeSelfFileResolutions` (default `true`, i.e.
`SymbolResolver.DEFAULT_EXCLUDE_SELF_FILE`) removes such candidates *before* ranking and truncation:
* candidates with `path == record.path` are dropped, so `targets` never points back at the referencing file;
* `candidateCount` / `ambiguous` describe the **remaining** (cross-file) candidates only;
* when *every* candidate was same-file the name is **hidden entirely** — it appears neither in
  `resolutions` nor in `unresolvedNames` (that is why
  `resolutions.size + unresolvedNames.size` can be smaller than `referencedNames.size`);
* `distance` is therefore `0` only for a *sibling* file in the same directory, never for the file itself.
Pass `--self-refs` / `excludeSelfFileResolutions = false` to restore the old behaviour.
### 12.6 Ambiguous resolutions are hidden from the reports
`Config.hideAmbiguousInReports` (default `true`) filters `ambiguous = true` entries out of
`project.json` and every `package.json`, so a report only contains resolutions that matched exactly one declaration.
Nothing is lost: the per-file sidecars still hold the ambiguous entries with their
`candidateCount`. Because the reports' `resolvedNameCount` is computed **after** filtering, it is self-consistent with
the `files[]` they contain. Pass `--keep-ambiguous` to disable the filter.
---
## 13. Folder rollups (`package.json`)
When `Config.writePackageManifests` is set (default), `index()` writes
`<folder>/<dataDirName>/<packageManifestName>` for **every folder that contains at least one indexed file or failure**.
Each rollup is an ordinary [`Manifest`](#42-manifest--rootdataprojectjson-and-folderdatapackagejson) built with exactly
the same rules as `project.json`:
* slim records unless `includeDetailsInManifest`,
* ambiguous resolutions hidden unless `hideAmbiguousInReports = false`,
* counts (`fileCount`, `symbolCount`, `referenceCount`, `resolvedNameCount`,
  `unresolvedNameCount`) summed over exactly the records it contains,
* `failures` restricted to the same subtree, sorted by `path`.
Semantics:
| Aspect        | Behaviour                                                                                     |
|---------------|-----------------------------------------------------------------------------------------------|
| Scope         | **Recursive rollup** — the folder *and every subfolder*, so `a/.data/package.json` covers `a/b/c/Foo.kt` |
| `folder`      | Root-relative, `/`-separated folder path (`""`/omitted only for `project.json`)                |
| `root`        | Same absolute crawl root as `project.json`                                                     |
| `files[].path`| Still relative to `root`, **not** to `folder`                                                  |
| Root folder   | Not written — `project.json` already is the root rollup                                        |
| Resolution    | Computed project-wide *before* the split, so targets may point outside the folder              |
| Timestamps    | All rollups share the `generatedAt` of the manifest of that run                                |
| Failures      | Write failures of a rollup are logged at WARN and never abort `index()`                         |
Reading them back:
```kotlin
val rollup = indexer.loadPackageManifest(Paths.get("/repo/src/main/kotlin"))
println("${rollup?.fileCount} files, ${rollup?.unresolvedNameCount} unresolved names")
```
`clean()` removes the rollups together with the sidecars (they live in the same `.data` folders);
`--no-package-manifests` skips writing them altogether.

### 12.4 Re-resolving without re-parsing

```kotlin
val manifest = indexer.loadManifest()!!
val rescored = SymbolResolver.resolve(manifest, maxTargets = 5)
// or against a pre-built index, e.g. for a single new file
val index = SymbolResolver.buildIndex(manifest.files)
val record = SymbolResolver.resolve(indexer.indexFile(path), index, maxTargets = 5)
```

`SymbolResolver.resolve(Manifest)` returns an updated manifest (with recomputed
`resolvedNameCount` / `unresolvedNameCount`); it does **not** rewrite the sidecars.