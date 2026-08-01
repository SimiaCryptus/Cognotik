# Cognotik Core

`com.cognotik:core` is the foundation library of the **Cognotik** AI framework. It provides the primitives required to
take free-form LLM output and turn it into deterministic, validated file mutations:

1. **Response parsing** — extract file-scoped code / diff segments from markdown-ish LLM output.
2. **Patch processing** — a family of interchangeable diff engines that tolerate the sloppiness of model-generated
   patches (bad line counts, missing hunk headers, drifted context, re-indentation).
3. **Grammar validation** — cheap post-application sanity checks so a bad patch can be rejected.
4. **Serialization utilities** — an intentionally permissive Jackson configuration for parsing model-emitted JSON/YAML.

The library has no dependency on any particular LLM client; it operates purely on strings.

---

## Table of contents

- [Installation](#installation)
- [Module layout](#module-layout)
- [Architecture overview](#architecture-overview)
- [PatchParser](#patchparser)
- [PatchProcessor](#patchprocessor)
- [Patch engines](#patch-engines)
- [FuzzyPatchMatcher](#fuzzypatchmatcher)
- [ThermodynamicPatchMatcher](#thermodynamicpatchmatcher)
- [PythonPatcher](#pythonpatcher)
- [FullReplacementProcessor](#fullreplacementprocessor)
- [DataMergeProcessor](#datamergeprocessor)
- [PatchProcessors presets](#patchprocessors-presets)
- [Validation](#validation)
- [Utilities](#utilities)
- [End-to-end example](#end-to-end-example)
- [Testing](#testing)
- [Extending the library](#extending-the-library)
- [Known caveats](#known-caveats)
- [Build & publishing](#build--publishing)

---

## Installation

Gradle (Kotlin DSL):

```kotlin
dependencies {
  implementation("com.cognotik:core:<version>")
}
```

Maven:

```xml

<dependency>
    <groupId>com.cognotik</groupId>
    <artifactId>core</artifactId>
    <version>VERSION</version>
</dependency>
```

**Runtime requirements**

| Dependency                                           | Purpose                                 | Optional     |
|------------------------------------------------------|-----------------------------------------|--------------|
| `org.jetbrains.kotlin:kotlin-stdlib`                 | language runtime                        | no           |
| `org.apache.commons:commons-text`                    | `LevenshteinDistance`                   | no           |
| `org.slf4j:slf4j-api`                                | logging facade                          | no           |
| `com.fasterxml.jackson.*` (databind, kotlin, jsr310) | `JsonUtil`                              | no           |
| `jackson-dataformat-{yaml,xml,toml,properties}`      | `DataMergeProcessor`                    | only if used |
| `Cognotik:antlr` + `antlr4-runtime`                  | `KotlinGrammarValidator`                | only if used |
| `groovy.lang.GString`                                | extra serializer, resolved reflectively | yes          |
| `com.google.guava:guava`                             | collection helpers                      | no           |
| `commons-io:commons-io`                              | stream helpers                          | no           |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core`      | available for async callers             | no           |
| `org.hsqldb:hsqldb`                                  | inherited from the sibling modules      | yes          |

Logback is `compileOnly` — bring your own SLF4J binding. **Toolchain**

* Kotlin and JUnit versions are inherited from the root version catalog (`libs.versions.toml`); the module declares no
  independent pins other than `resolutionStrategy.force(libs.antlr.runtime)`.
* `compileJava` is forced to run after `compileKotlin`, so Kotlin code may reference the ANTLR-generated Java types.
  **Trimming the graph.** `PatchParser`, `FuzzyPatchMatcher`, `ThermodynamicPatchMatcher`, `PythonPatcher`,
  `FullReplacementProcessor` and `ParenMatchingValidator` touch neither Jackson nor ANTLR. If that is all you need:

```kotlin
implementation("com.cognotik:core:<version>") {
  exclude(group = "com.fasterxml.jackson.dataformat") // disables DataMergeProcessor
  exclude(group = "org.antlr")                        // disables KotlinGrammarValidator
}
```

If you exclude ANTLR, also replace the default validator chain (see [Validation](#validation)) so `*.kt`
lookups never construct `KotlinGrammarValidator`.

## Quick start

The common "one file, one response" case needs three lines of glue:

```kotlin
import com.simiacryptus.cognotik.diff.PatchProcessors
val processor = PatchProcessors.Fuzzy                  // 1. pick a dialect + engine
val prompt = processor.patchFormatPrompt               // 2. describe it to the model
val result = processor.apply(original, modelResponse, filename = "src/Main.kt")
if (result.isValid) file.writeText(result.newCode)
else result.errors.forEach { logger.warn(it.message) }
```

Integration checklist:

1. **Use one instance for both prompt and parse.** `patchFormatPrompt` documents the dialect *that*
   implementation can read; mixing presets is the single most common source of "nothing applied".
2. **Always pass `filename`** to `apply()`, otherwise validator selection degrades to
   `ParenMatchingValidator`.
3. **Detect no-ops.** `apply()` never throws; compare `result.newCode` with the input.
4. **Use `parse()` for multi-file replies.** `apply()` only sees ```` ```diff ```` fences.
5. **Never write on `isValid == false`** unless a human reviews the result.

---

---

## Module layout

```
src/main/kotlin/com/simiacryptus/cognotik/
├── diff/
│   ├── PatchParser.kt                 # LLM response → typed segments
│   ├── PatchProcessor.kt              # generate/apply/validate contract
│   ├── PatchProcessors.kt             # named preset enum
│   ├── FuzzyPatchMatcher.kt           # default engine (line linking + Levenshtein)
│   ├── ThermodynamicPatchMatcher.kt   # DP alignment with an energy model
│   ├── PythonPatcher.kt               # indentation-preserving engine
│   ├── FullReplacementProcessor.kt    # whole-file replacement
│   ├── DataMergeProcessor.kt          # structured-data deep merge
│   ├── FileValidators.kt              # validator registry + DIFF_PATTERN
│   └── DiffApplicationResult.kt
└── util/
  ├── GrammarValidator.kt
  ├── KotlinGrammarValidator.kt      # ANTLR-based
  ├── ParenMatchingValidator.kt      # bracket/quote balance
  ├── JsonUtil.kt                    # lenient Jackson mapper + helpers
  ├── StringSplitter.kt              # entropy-weighted string split
  └── isBinary.kt                    # heuristic binary detection
```

---

## Architecture overview

```
LLM response (String)
    │
    ▼
PatchParser.parse()  ──►  List<ResponseSegment>
    │                     ├─ Markdown       (prose, ignored)
    │                     ├─ DiffBlock      (filename + unified-ish diff)
    │                     └─ NewFileBlock   (filename + language + full content)
    ▼
PatchProcessor.applyPatch(source, diff)   ──►  new file content
    │
    ▼
GrammarValidator.validateGrammar(newCode) ──►  List<ValidationError>
    │
    ▼
DiffApplicationResult(newCode, errors, isValid, validator)
```

`PatchProcessor` extends `PatchParser`, so any engine instance can both *parse* an LLM response and *apply* the
extracted patches, and can emit the prompt fragment that instructs the model how to format its answer
(`patchFormatPrompt`).

---

## PatchParser

`interface PatchParser` converts a raw model response into a list of `ResponseSegment`s.

### Segment model

```kotlin
sealed class ResponseSegment(val filename: String?, val content: String) {
  class Markdown(content: String)
  class NewFileBlock(filename: String, val language: String, content: String, val originalRange: IntRange)
  class DiffBlock(filename: String, content: String, val originalRange: IntRange)
}
```

`originalRange` is a **line** range into the (normalized) response, useful for UI highlighting.

### Entry points

```kotlin
fun parse(response: String, defaultFile: String? = null): List<ResponseSegment>
fun parse(response: String, defaultFile: String? = null, root: Path? = null): List<ResponseSegment>
```

* `defaultFile` — filename used when a code block has no discoverable header.
* `root` — when supplied, filenames are resolved/normalized against this project root via
  `ResponseSegment.calcFilename`.

### Recognised input dialects

The parser accepts three overlapping conventions and picks the strongest signal available.

**1. Explicit markers (highest precedence).** If `<<<DIFF …>>> … <<<END>>>` or
`<<<FILE …>>> … <<<END>>>` appears anywhere, the explicit parser is used exclusively.

```
<<<DIFF src/main/Example.kt>>>
...patch body (may itself contain fenced blocks)...
<<<END>>>
```

* Markers are **case-insensitive** (`<<<diff … >>>` / `<<<end>>>`).
* `DIFF` forces `DiffBlock`; `FILE` yields `NewFileBlock` unless the body looks like a diff.
* Text between blocks is preserved as `Markdown`, with marker lines stripped.
* *Chained blocks*: bare fenced blocks each terminated by `<<<END>>>` that follow a marker block inherit the previous
  filename and block type (`parseChainedCodeBlocks`). This tolerates models that emit one header followed by several
  hunks.

**2. Markdown headers.** `#`-headers and a dashed `File:` banner are indexed by position; the nearest preceding header
before a fence supplies the filename.

**3. Bare fences + `defaultFile`.** With no header and no marker, `defaultFile` is used; if that is also absent, the
block degrades to `Markdown`.

### Fence matching

`getMarkdownCodeBlockMatches` does **not** use a regex over the whole document. It:

1. Scans line-by-line for `^(\s*)```(.*)$`, recording indentation and the info-string.
2. Classifies a fence as *opening* when the info-string is non-empty (a language tag).
3. Pairs fences with a depth counter, **only considering fences at the same indentation** as the opening fence;
   deeper-indented fences are treated as block content.

This is what allows a `md` block to legally contain an indented ```` ```js ```` block (see the
`EdgeCases` tests). Unclosed blocks are auto-closed by appending a fence before parsing.

### Filename normalization

`normalizeFilename` is applied iteratively (fixed point, max 10 passes) and removes:

* prefixes: `File:`, `file:`, `Code:`, `Path:`, `Filename:`, `Modified:`, `Updated:`, `Changed:`,
  `Edit:`, `Patch:` (each in both capitalisations)
* trailing `:` / `.`
* wrapping quotes, single quotes, backticks
* ordered-list prefixes (`1. `)
* markdown emphasis (`**`, `*`)

Finally, a header consisting solely of a **language keyword** (`kotlin`, `py`, `dockerfile`, …) is normalized to the
empty string so `### kotlin` is never mistaken for a path.

### Diff detection

```kotlin
isDiffContent(lang, code) =
  lang == "diff"(case - insensitive)
      || every line starts with one of : '', ' ', '\t', '@', '-', '+'
```

A block satisfying either condition becomes a `DiffBlock`; otherwise `NewFileBlock`.

### Path resolution (`calcFilename`)

When a `root` is provided, the parser defends against two common model errors:

* **Duplicated path prefixes** — `src/utils/src/utils/x.js` collapses to `src/utils/x.js`.
* **Root/file overlap** — if the tail of `root` equals the head of the emitted path, the overlap is removed
  (`root=/proj/src`, file=`src/main/X.kt` → `/proj/src/main/X.kt`).

Leading `.`/`..` segments are stripped (`removeAllUpperDirectories`) before resolution.

### `patchFormatPrompt`

Every parser/processor exposes a `patchFormatPrompt` string to be embedded in the system prompt. It documents the exact
dialect that implementation can parse. Always feed the prompt from the *same*
instance you will use to parse the reply.

---

## PatchProcessor

```kotlin
interface PatchProcessor : PatchParser {
  val label: String
  fun generatePatch(oldCode: String, newCode: String): String
  fun applyPatch(source: String, patch: String): String

  fun apply(originalCode: String, response: String, filename: String? = null): DiffApplicationResult
}
```

`apply()` is the convenience pipeline:

1. Extract every ```` ```diff ```` block via
   `FileValidators.DIFF_PATTERN = (?s)(?<![^\n])```diff\n(.*?)\n``` `, de-duplicated.
2. Reject any hunk larger than `FileValidators.MAX_DIFF_SIZE_CHARS` (100 000 chars).
3. Apply hunks sequentially, threading the result of each into the next.
4. Validate after every hunk with the validator selected for `filename`.
5. Subtract errors that were already present in the *original* code, so pre-existing breakage is not attributed to the
   patch.
6. Return `DiffApplicationResult(newCode, errors, isValid = errors.isEmpty(), validator)`.

Exceptions thrown while applying an individual hunk are swallowed (that hunk contributes no errors and no changes);
check `newCode` against `originalCode` if you need to detect a total no-op.

> Note: `apply()` only recognises ```` ```diff ```` fences. For the full segment model (new files,
> explicit markers, multi-file responses) call `parse()` and drive `applyPatch()` yourself.

## DiffApplicationResult

```kotlin
data class DiffApplicationResult(
  val newCode: String,
  val errors: List<GrammarValidator.ValidationError> = emptyList(),
  val isValid: Boolean = errors.isEmpty(),
  val validator: GrammarValidator? = null
)
```

The result is a *report*, not a guarantee: it says what the engine produced and what the selected validator thought of
it. Read it as follows.

| Observation                                   | Interpretation                                                  |
|-----------------------------------------------|-----------------------------------------------------------------|
| `newCode == originalCode`                     | nothing matched, or every hunk threw and was swallowed          |
| `newCode != originalCode && errors.isEmpty()` | applied and validated — the success case                        |
| `isValid == false`                            | applied, but introduced errors **not** present in the original  |
| `errors` non-empty yet identical to originals | cannot happen: pre-existing errors are subtracted               |
| `validator is ParenMatchingValidator`         | only bracket/quote balance was checked; assume shallow coverage |

Recommended write path:

```kotlin
val result = processor.apply(original, response, filename)
when {
  result.newCode == original -> logger.warn("Patch was a no-op for $filename")
  !result.isValid -> logger.warn("Rejecting patch for $filename: ${result.errors}")
  else -> file.writeText(result.newCode)
}
```

Because errors are *differenced* against the original file, a file that was already broken will not block a patch that
leaves it equally broken — but a patch that adds a new breakage is always visible.
---

---

## Patch engines

| Engine                      | Strategy                                                 | Best for                             | Handles new files |
|-----------------------------|----------------------------------------------------------|--------------------------------------|-------------------|
| `FuzzyPatchMatcher`         | line linking + Levenshtein + snippet fallback            | general purpose, default             | yes               |
| `ThermodynamicPatchMatcher` | Needleman–Wunsch DP over a binding-energy matrix         | small, noisy hunks                   | partial           |
| `PythonPatcher`             | same linking model, indentation-preserving normalization | Python / YAML                        | yes               |
| `FullReplacementProcessor`  | no diff at all                                           | large rewrites, small files          | yes               |
| `DataMergeProcessor`        | structured deep merge                                    | JSON/YAML/XML/TOML/properties config | yes               |

### Choosing an engine

A decision order that holds up in practice:

1. **Structured data** (`json`, `yaml`, `yml`, `xml`, `toml`, `properties`) → `DataMerge`. Diffing a serialized tree by
   lines is fragile; merging it is not.
2. **Indentation is semantic** (Python, YAML, Nim, Haskell) → `Python`.
3. **Small file (< ~200 lines) or edit touching more than half the lines** → `FullReplacement`. The extra output tokens
   are usually cheaper than one failed patch plus a retry.
4. **Auto-commit without human review** → `Strict`. Failing loudly beats mis-applying quietly.
5. **Otherwise** → `Fuzzy`. With a weak model *and* a review step, `Lenient`.
6. **Small hunks that keep drifting even under `Lenient`** → `Thermodynamic`: it aligns globally instead of growing
   outward from anchors.

```kotlin
private val DATA_EXTS = setOf("json", "yaml", "yml", "xml", "toml", "properties")
fun pick(filename: String, changedLines: Int, totalLines: Int): PatchProcessor = when {
  filename.substringAfterLast('.', "") in DATA_EXTS -> PatchProcessors.DataMerge
  filename.endsWith(".py") -> PatchProcessors.Python
  totalLines < 200 || changedLines > totalLines / 2 -> PatchProcessors.FullReplacement
  else -> PatchProcessors.Fuzzy
}
```

Cost/risk summary (risk = probability the engine produces *something*; corruption = probability that something is
wrong):

| Preset            | Output tokens | Failure-to-apply risk | Silent-corruption risk             |
|-------------------|---------------|-----------------------|------------------------------------|
| `FullReplacement` | high          | none                  | none (all-or-nothing)              |
| `DataMerge`       | low           | low                   | medium (arrays replaced wholesale) |
| `Strict`          | medium        | high                  | low                                |
| `Fuzzy`           | medium        | low                   | medium                             |
| `Lenient`         | medium        | very low              | high                               |
| `Thermodynamic`   | medium        | low                   | medium                             |
| `Python`          | medium        | low                   | medium                             |

Whatever you pick, keep the original content until validation passes — every engine can return a file that parses but is
semantically wrong.

### FuzzyPatchMatcher

The default engine. Both directions operate on a doubly-linked list of `LineRecord`s (`index`, `line`, `previousLine`,
`nextLine`, `matchingLine`, `type ∈ {CONTEXT, ADD, DELETE}`).

**Linking pipeline** (shared by generate and apply):

1. `linkUniqueMatchingLines` — group unmatched lines on both sides by *normalized* content; link a group only when the
   multiplicities are equal. This yields unambiguous, high-confidence anchors and deliberately refuses to guess for
   repeated lines (e.g. blank lines, `}`).
2. `linkAdjacentMatchingLines` — iteratively grow matched regions outward from anchors, skipping
   `ADD` and empty lines, using `isMatch` for the comparison. Repeats until a fixed point; bounded by
   `sourceLines.size * 10` iterations and a visited-pair set.
3. `subsequenceLinking` — recurse into the still-unmatched residue (max depth
   `maxRecursionDepth`, default 100), re-running steps 1–2 on each shrinking segment.

**`isMatch` heuristics** — beyond exact normalized equality, fuzzy acceptance requires that the two lines agree on
*structural class* before Levenshtein is even consulted:

* both, or neither, a list item (`^[-*+\d]+\.?\s+`)
* both, or neither, an ATX header (`#…`)
* both, or neither, a block quote (`>`)
* both, or neither, a fence (```` ``` ````)

Then, if `max(len) > minLineLengthForFuzzyMatch`, accept when
`levenshtein(a, b) <= floor(max(len) / levenshteinThresholdDivisor)`.

**`generatePatch` post-processing:**

* `markMovedLines` — detects order inversions among matched pairs and rewrites the move as a
  `DELETE` + `ADD`.
* `newToPatch` — walks the new file, emitting `ADD` for unmatched lines and back-filling `DELETE`s from unmatched source
  predecessors.
* `truncateContext` — collapses runs longer than `2 * contextSize` into head/`...`/tail.
* `fixPatchLineOrder` — bubble-sorts adjacent `ADD, DELETE` pairs into `DELETE, ADD` (both in the list *and* in the
  linked-list pointers), then re-links.
* `annihilateNoopLinePairs` — removes `-X` / `+X` pairs with identical normalized content.

**`applyPatch` dispatch:**

| Condition                       | Behaviour                                                    |
|---------------------------------|--------------------------------------------------------------|
| `patch.isBlank()`               | return `source`                                              |
| `source.isBlank()`              | strip `+`/`-` markers, drop deletions, return as new content |
| patch contains no `+`/`-` lines | **snippet mode** (below)                                     |
| otherwise                       | link source ↔ patch, then `generatePatchedText`              |

**Snippet mode** (`applySnippetPatch`, enabled by `enableSnippetPatching`) applies a context-only block using a
three-stage fallback:

1. exact contiguous normalized block match → replace in place;
2. **anchor** match — first and last normalized lines land at the expected offsets;
3. fuzzy sliding window — best score, requiring `score >= patchSize * snippetMatchThreshold`, and (when
   `requireAnchorMatch`) either an anchor hit or `score >= patchSize - 1`.

If none succeed the source is returned unchanged and a warning is logged. Single-line snippets are handled specially
(exact lookup only).

**Safety valve:** `generatePatchedText` will only append leftover `ADD` lines when at least one context line matched (or
the source is empty). This prevents a patch aimed at a different file from simply appending its additions.

**Constructor parameters**

| Parameter                     | Default | Meaning                                                 |
|-------------------------------|---------|---------------------------------------------------------|
| `contextSize`                 | 3       | context lines kept around a hunk in `generatePatch`     |
| `maxRecursionDepth`           | 100     | recursion limit for subsequence linking                 |
| `levenshteinThresholdDivisor` | 4       | larger ⇒ stricter fuzzy matching                        |
| `minLineLengthForFuzzyMatch`  | 5       | shorter lines must match exactly                        |
| `enableFuzzyMatching`         | `true`  | disable for byte-exact context requirements             |
| `enableSnippetPatching`       | `true`  | allow marker-less snippet application                   |
| `snippetMatchThreshold`       | 0.8     | fraction of lines that must match in fuzzy snippet mode |
| `requireAnchorMatch`          | `true`  | require first/last line agreement in fuzzy snippet mode |
| `debug`                       | `false` | verbose per-line trace logging                          |

`FuzzyPatchMatcher.default` is a shared instance with these defaults.

### ThermodynamicPatchMatcher

Models alignment as molecular hybridisation. Each line pair gets a **binding energy** (negative = favourable):

```
ΔG(a, b) = -10 * len                              if normalize(a) == normalize(b)
       = 0                                       if either is empty
       = -10 * similarity * maxLen + P_S * |Δlen| otherwise
similarity = 1 - levenshtein(a, b) / maxLen
```

`generatePatch` runs a Needleman–Wunsch dynamic program minimising total free energy, with a gap cost of
`entropyPenalty` and a **cooperativity bonus** subtracted when the previous cell was also a match (an analogue of
base-stacking, biasing toward contiguous alignments). Backtracking produces
`Match/Delete/Insert` operations that are rendered as `  `/`- `/`+ ` lines and context-truncated.

`applyPatch` enumerates candidate **binding sites** (every offset in the source), scores each with
`calculateBindingSiteEnergy`, discards sites with `ΔG >= minBindingEnergy`, and applies the patch at the minimum-energy
site. Patches with no context lines fall back to a sliding-window snippet scan.

| Parameter            | Default |
|----------------------|---------|
| `temperature`        | 1.0     |
| `cooperativityBonus` | 2.0     |
| `entropyPenalty`     | 1.0     |
| `contextSize`        | 3       |
| `minBindingEnergy`   | 0.0     |

Complexity is `O(n·m)` in lines for generation — prefer it for focused hunks, not whole-file rewrites.

### PythonPatcher

Structurally similar to `FuzzyPatchMatcher`, but with one critical difference:

```kotlin
// FuzzyPatchMatcher
fun normalizeLine(line: String) = line.trim().replace("\\s{2,}".toRegex(), " ")

// PythonPatcher
fun normalizeLine(line: String) = line.replace(Regex("\\s+$"), "")   // trailing only
```

Leading whitespace is therefore **significant** during matching, which is required for Python and YAML where indentation
carries semantics. Bracket-density heuristics are omitted. Fuzzy matching uses a fixed threshold of
`floor(maxLength / 4)` for lines longer than 5 characters.

### FullReplacementProcessor

`generatePatch` returns `newCode`; `applyPatch` returns `patch.trim()`. Useful when the change ratio is high enough that
diff application risk outweighs token cost. Its `patchFormatPrompt` instructs the model to emit whole files in fenced
blocks preceded by a `###` path header.

### DataMergeProcessor

Treats the "patch" as a **partial document** to be deep-merged into the source.

Supported formats (`DataMergeProcessor.DataFormat`):

| Format       | Read/Write mapper                           | Extensions    |
|--------------|---------------------------------------------|---------------|
| `JSON`       | `ObjectMapper`                              | `json`        |
| `YAML`       | `YAMLFactory` (no `---` doc start on write) | `yaml`, `yml` |
| `XML`        | `XmlMapper`                                 | `xml`         |
| `TOML`       | `TomlMapper`                                | `toml`        |
| `PROPERTIES` | `JavaPropsMapper`                           | `properties`  |

**Format detection** (`detectFormat`, heuristic, in order): `{`/`[` → JSON; `<?xml`/`<` → XML; contains `=` but no `:`/
`{` → PROPERTIES; contains `[`, `]`, `=` and parses as TOML → TOML; parses as YAML → YAML; else JSON. The patch itself
is parsed with `tryParseWithFallback`, so a JSON patch may be merged into a YAML source.

**Merge semantics** (`deepMerge`):

* object ∩ object → recursive merge
* `null` value in the patch → **removes** the key
* arrays → replaced wholesale (no element merging)
* scalars → patch wins

**`generatePatch`** computes a *minimal* diff (`computeDiff`): only changed/added subtrees are emitted, and keys removed
in the new version are emitted as explicit `null`s. Returns `null`-equivalent (falls back to the full new content) if
the trees are identical or an error occurs.

Both `applyPatch` and `generatePatch` degrade gracefully: on any parse failure they log a warning and return the
patch/new content verbatim.

---

## PatchProcessors presets

`enum class PatchProcessors : PatchProcessor` delegates to a preconfigured `matcher`, giving named, serializable
strategies suitable for configuration files and UI dropdowns.

| Constant          | Backing engine                                                                                   | Notes                   |
|-------------------|--------------------------------------------------------------------------------------------------|-------------------------|
| `FullReplacement` | `FullReplacementProcessor()`                                                                     | no patching             |
| `DataMerge`       | `DataMergeProcessor()`                                                                           | structured config files |
| `Thermodynamic`   | `ThermodynamicPatchMatcher(1.0, 2.0, 1.0)`                                                       | energy-based            |
| `Strict`          | `FuzzyPatchMatcher(enableFuzzyMatching=false, enableSnippetPatching=false, contextSize=5)`       | exact context only      |
| `Lenient`         | `FuzzyPatchMatcher(divisor=2, minLen=3, threshold=0.6, requireAnchorMatch=false, contextSize=2)` | maximum tolerance       |
| `Fuzzy`           | `FuzzyPatchMatcher()`                                                                            | balanced default        |
| `Python`          | `PythonPatcher()`                                                                                | indentation-preserving  |

```kotlin
val processor: PatchProcessor = PatchProcessors.Fuzzy
val prompt = processor.patchFormatPrompt      // feed to the model
val result = processor.apply(original, llmResponse, "src/Main.kt")
```

---

## Validation

```kotlin
interface GrammarValidator {
  fun validateGrammar(code: String): List<ValidationError>
  data class ValidationError(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val severity: Severity = Severity.ERROR
  )
  enum class Severity { ERROR }
}
```

### Implementations

* **`KotlinGrammarValidator`** — runs the generated ANTLR `KotlinLexer`/`KotlinParser` over the source and reports a
  single aggregate error if `parser.numberOfSyntaxErrors != 0`. Requires the sibling `Cognotik:antlr` project on the
  classpath. Throwables are caught and reported as errors rather than propagated.
* **`ParenMatchingValidator`** — language-agnostic balance checks for `{}`, `[]`, `()`, `"` and `'`. Quote counting is
  escape-aware (`\\` toggles the escape state). Cheap and always available.

### Registry

```kotlin
object FileValidators {
  const val MAX_DIFF_SIZE_CHARS = 100_000
  val DIFF_PATTERN = """(?s)(?<![^\n])```diff\n(.*?)\n```""".toRegex()

  val validatorProviders: MutableList<(String?) -> GrammarValidator?>
  fun getValidator(filename: String?): GrammarValidator
}
```

`getValidator` returns the first non-null provider result. The default chain maps `*.kt` to
`KotlinGrammarValidator` and everything else to `ParenMatchingValidator`. Register your own by prepending to
`validatorProviders`:

```kotlin
FileValidators.validatorProviders.add(0) { filename ->
  if (filename?.endsWith(".json") == true) JsonGrammarValidator() else null
}
```

---

## Utilities

### `JsonUtil`

A single, intentionally permissive Jackson `ObjectMapper` plus thin helpers, tuned for text that came out of a language
model rather than out of another program.

```kotlin
val text: String = JsonUtil.toJson(myObject)
val obj: MyType = JsonUtil.fromJson(text, MyType::class.java)
val merged: MyType = JsonUtil.merge(base, overrides)   // shallow: top-level fields only
```

What you can rely on:

* unknown properties are ignored rather than fatal — models invent fields;
* Kotlin classes, JSR-310 date/time and `Optional` are registered;
* output is pretty-printed and stable enough to diff between runs;
* `groovy.lang.GString` gains an extra serializer when Groovy is on the classpath; the lookup is reflective, so its
  absence is not an error.
  `JsonUtil` is a *parser*, not a *validator*: use it to accept model output, and a `GrammarValidator` (or your own
  schema check) to decide whether to keep it. For nested merging prefer `DataMergeProcessor` — see the
  [caveat](#known-caveats) about `JsonUtil.merge` being shallow.

### `StringSplitter`

Splits text at the separator occurrence that maximises an entropy-like objective
`b·ln(a) + a·ln(b)` (where `a` is the normalised offset), weighted per separator. Useful for chunking prompts near
sentence boundaries rather than at a fixed midpoint.

```kotlin
val (head, tail) = StringSplitter.split(text, mapOf("." to 2.0, ", " to 2.0, " " to 1.0))
```

### `isBinary`

Extension properties on `String` and `InputStream`: content is considered binary when more than 10 % of bytes fall
outside the printable ASCII range `0x20..0x7E`.

---

## End-to-end example

```kotlin
import com.simiacryptus.cognotik.diff.PatchParser
import com.simiacryptus.cognotik.diff.PatchProcessors
import java.nio.file.Path

val processor = PatchProcessors.Fuzzy
val root = Path.of("/workspace/project")

// 1. Ask the model, embedding the dialect description.
val systemPrompt = """
  You are a code editor.
  ${processor.patchFormatPrompt}
""".trimIndent()
val response: String = llm.chat(systemPrompt, userRequest)

// 2. Parse the reply into typed, root-resolved segments.
val segments = processor.parse(response, defaultFile = null, root = root)

// 3. Apply.
for (segment in segments) {
  when (segment) {
    is PatchParser.ResponseSegment.Markdown -> Unit

    is PatchParser.ResponseSegment.NewFileBlock -> {
      root.resolve(segment.filename!!).toFile().apply {
        parentFile.mkdirs()
        writeText(segment.removeCodeFences())
      }
    }

    is PatchParser.ResponseSegment.DiffBlock -> {
      val file = root.resolve(segment.filename!!).toFile()
      val original = if (file.exists()) file.readText() else ""
      val patched = processor.applyPatch(original, segment.content)

      val validator = com.simiacryptus.cognotik.diff.FileValidators
        .getValidator(segment.filename)
      val newErrors = validator.validateGrammar(patched) -
          validator.validateGrammar(original).toSet()

      if (newErrors.isEmpty()) file.writeText(patched)
      else logger.warn("Rejected patch for ${segment.filename}: $newErrors")
    }
  }
}
```

Single-file shortcut using the built-in pipeline:

```kotlin
val result = processor.apply(originalCode, response, filename = "src/Main.kt")
if (result.isValid) file.writeText(result.newCode)
else result.errors.forEach { logger.error(it.message) }
```

---

## Testing

### Structure

```
src/test/kotlin/com/simiacryptus/cognotik/diff/
├── PatchTestCase.kt                 # fixture model + shared assertion
├── PatchParserTest.kt               # ~40 nested JUnit 5 tests for the parser
├── FuzzyPatchMatcherTest.kt         # parameterized over JSON fixtures
├── ThermodynamicPatchMatcherTest.kt
└── PythonPatchUtilTest.kt
src/test/resources/*.json            # fixtures
```

### Fixture format

```json
{
  "filename": "test_add_line.txt",
  "originalCode": "line1\nline2\nline3",
  "diff": "line1\nline2\n+newLine\nline3",
  "newCode": "line1\nline2\nnewLine\nline3",
  "isValid": true,
  "errors": ""
}
```

`PatchTestCase.test` loads the resource relative to the *patcher's* class loader, skips the case when
`isValid == false`, applies the diff, and compares after normalizing (`trim()` + CRLF → LF).

```kotlin
@ParameterizedTest
@MethodSource("testCases")
fun testPatchApplication(resourceName: String) = test(resourceName, FuzzyPatchMatcher.default)
```

### Fixture inventory

The fixtures in `src/test/resources` are small on purpose: each one isolates a single behaviour of the
`applyPatch` dispatch table.

| Fixture                            | Exercises                                                            |
|------------------------------------|----------------------------------------------------------------------|
| `patch_add_line.json`              | one insertion between two context lines                              |
| `patch_add_2_lines_variant_2.json` | two insertions straddling a blank line, `+ ` marker with a space     |
| `patch_add_2_lines_variant_3.json` | two adjacent insertions after a blank line                           |
| `patch_append_line.json`           | insertion at end of file                                             |
| `patch_prepend_line.json`          | insertion at start of file                                           |
| `patch_append_to_empty_file.json`  | empty source — the `source.isBlank()` branch                         |
| `patch_blank_file.json`            | marker-less full document written into an empty file                 |
| `patch_exact_match.json`           | context-only patch that must be a no-op (snippet mode)               |
| `patch_inner_block.json`           | hunk with no leading context, taken from the middle of a JS function |
| `patch_modify_line.json`           | `-`/`+` replacement pair                                             |
| `patch_remove_line.json`           | deletion written as `- line` (space after the marker)                |
| `patch_wrap_panel.json`            | whole-member rewrite: large delete block + re-indented add block     |
| `yaml_min_repro.json`              | YAML whose context lines carry a stray leading space                 |

The last two are the interesting ones. `patch_wrap_panel.json` is the canonical
`fixPatchLineOrder` + `annihilateNoopLinePairs` stress case, and `yaml_min_repro.json` is precisely the case where
`FuzzyPatchMatcher.normalizeLine` (which trims) succeeds and `PythonPatcher.normalizeLine` (which does not) fails.

### Debugging a failing fixture

```kotlin
val matcher = FuzzyPatchMatcher(debug = true)   // per-line link/apply trace
println(matcher.applyPatch(fixture.originalCode, fixture.diff))
```

Useful narrowing steps, in order:

1. Does `generatePatch(original, expectedNew)` round-trip through `applyPatch`? If not, the linking phase is at fault,
   not the application phase.
2. Does the failure disappear with `enableFuzzyMatching = false`? Then a fuzzy match is firing where it should not —
   raise `levenshteinThresholdDivisor` or `minLineLengthForFuzzyMatch`.
3. Does it disappear with `enableSnippetPatching = false`? Then the patch was misclassified as a context-only snippet —
   it contains no `+`/`-` lines.

### Coverage matrix

Each engine declares the fixtures it is expected to pass. Entries commented out in a given test class document that
engine's current limitations, e.g. `ThermodynamicPatchMatcher` does not yet pass
`patch_inner_block.json`, `patch_wrap_panel.json`, or the two-line-insertion variants, and
`PythonPatcher` does not pass `yaml_min_repro.json`. Treat those lists as the authoritative capability matrix.

### Adding a case

1. Drop a JSON fixture into `src/test/resources/`.
2. Add `"/your_fixture.json"` to the `testCases()`/`patchTestCases()` list of every engine expected to handle it.
3. `./gradlew test`

Test logging is configured by `src/test/resources/logback.xml` at `debug`; the patch engines emit extremely verbose
per-line traces, so raise the level when running large suites.

---

## Extending the library

### A custom engine

```kotlin
class MyPatcher : PatchProcessor {
  override val label = "MyPatcher"

  override val patchFormatPrompt = """
      Describe here exactly the format this parser accepts.
  """.trimIndent()

  override fun generatePatch(oldCode: String, newCode: String): String = TODO()
  override fun applyPatch(source: String, patch: String): String = TODO()
  // parse(), apply() and patchFormatPrompt defaults are inherited from PatchParser/PatchProcessor
}
```

Override `PatchParser` members only if your dialect differs from the default marker/header grammar.

### Reusing `FuzzyPatchMatcher` with different normalization

`FuzzyPatchMatcher` is `open` and `normalizeLine` is `open`:

```kotlin
class CaseInsensitivePatcher : FuzzyPatchMatcher() {
  override fun normalizeLine(line: String) =
    super.normalizeLine(line).lowercase()
}
```

## Performance & thread safety

### Complexity

| Operation                                 | Cost                                                                                                                                |
|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `PatchParser.parse`                       | `O(lines)` — line-wise fence scan, no global regex                                                                                  |
| `FuzzyPatchMatcher` linking               | bounded by `sourceLines.size * 10` iterations plus a visited-pair set; each `isMatch` costs `O(len²)` when Levenshtein is consulted |
| `subsequenceLinking`                      | recursion capped by `maxRecursionDepth` (100)                                                                                       |
| `ThermodynamicPatchMatcher.generatePatch` | `O(n·m)` cells, each with a Levenshtein call ⇒ effectively `O(n·m·len²)`                                                            |
| `ThermodynamicPatchMatcher.applyPatch`    | `O(n)` binding sites × `O(patch)` scoring                                                                                           |
| `FullReplacementProcessor`                | `O(1)` beyond the string copy                                                                                                       |
| `DataMergeProcessor`                      | dominated by parse + serialize of both documents                                                                                    |

Practical consequences:

* Long lines are more expensive than many lines — minified or generated files are the worst case for every
  Levenshtein-based engine. Consider routing them to `FullReplacement`.
* `Thermodynamic` is for focused hunks. Feeding it a whole-file rewrite is quadratic in the *file*, not in the change.
* `FileValidators.MAX_DIFF_SIZE_CHARS` (100 000) exists to stop a runaway hunk from becoming a runaway alignment.
  Oversized hunks are dropped, not truncated.

### Thread safety

* Engines hold only their constructor configuration; all mutable state (`LineRecord` graphs, DP tables) is created per
  call. Sharing a single instance — including `FuzzyPatchMatcher.default` and the
  `PatchProcessors` enum constants — across threads is safe.
* `ParenMatchingValidator` is stateless. `KotlinGrammarValidator` builds a fresh lexer/parser per call.
* `FileValidators.validatorProviders` is a plain `MutableList`. Mutate it once during application startup, before any
  concurrent `getValidator` calls; it is not synchronized.

### Logging

All engines log through SLF4J under their own class names. The per-line traces are `debug`-level and very verbose
(`src/test/resources/logback.xml` enables them for the test suite). In production, keep
`com.simiacryptus.cognotik.diff` at `info` and enable `debug` only for the file you are investigating;
`FuzzyPatchMatcher(debug = true)` adds a further, even noisier trace independent of the logger level.
---

## Troubleshooting

| Symptom                                                 | Likely cause                                                      | Remedy                                                                  |
|---------------------------------------------------------|-------------------------------------------------------------------|-------------------------------------------------------------------------|
| `result.newCode == originalCode`                        | no ```` ```diff ```` fence in the reply, or every hunk threw      | log `parse(response)`; check the fence's info-string; try `Lenient`     |
| Additions land at the end of the file                   | zero context lines matched; the safety valve appended the residue | require context in the prompt; use `Strict` to fail loudly instead      |
| Python/YAML indentation collapses                       | `FuzzyPatchMatcher.normalizeLine` trims leading whitespace        | use `PatchProcessors.Python`                                            |
| Filenames like `src/utils/src/utils/x.js`               | model repeated the path prefix                                    | pass `root` so `calcFilename` collapses the duplication                 |
| `filename` comes back empty                             | the header was a bare language keyword (`### kotlin`)             | supply `defaultFile`                                                    |
| A fenced block swallows the rest of the document        | an inner fence sits at the same indentation as the outer one      | indent the inner fence, per the `md`-containing-`js` example            |
| A hunk is ignored with no error                         | larger than `MAX_DIFF_SIZE_CHARS`                                 | split the hunk, or switch to `FullReplacement`                          |
| A JSON/YAML array is clobbered                          | `deepMerge` replaces arrays wholesale                             | emit the complete array in the patch                                    |
| A config key refuses to disappear                       | removal requires an explicit `null`                               | emit `"key": null`                                                      |
| A YAML patch was parsed as TOML (or vice versa)         | `detectFormat` is heuristic                                       | keep the patch in the same syntax as the source, or add a marker line   |
| `KotlinGrammarValidator` reports errors on valid Kotlin | ANTLR runtime/grammar mismatch                                    | check `resolutionStrategy.force(libs.antlr.runtime)` reaches your build |
| Everything validates but nothing is really checked      | `ParenMatchingValidator` was selected                             | register a real validator for that extension                            |

When in doubt, bisect the pipeline: `parse()` → inspect segments → `applyPatch()` on one segment →
`validateGrammar()`. `apply()` collapses all four steps and hides per-hunk exceptions, which is convenient in production
and unhelpful while debugging.
---

---

## Known caveats

* **`apply()` is diff-fence only.** `FileValidators.DIFF_PATTERN` matches ```` ```diff ```` blocks exclusively; new-file
  blocks and explicit markers require the `parse()` route.
* **Silent hunk failures.** `PatchProcessor.apply()` swallows exceptions per hunk. Compare
  `result.newCode` with the input to detect a no-op.
* **Double relativization.** `PatchParser.maybeResolveFilenames` relativizes the result of
  `calcFilename`, which already returns a root-relative path. If you depend on `root`-based resolution, verify the
  produced `filename` values for your layout, or pass `root = null` and resolve paths yourself.
* **Snippet mode is lossy.** Context-only patches are matched heuristically; with
  `requireAnchorMatch = false` and a low `snippetMatchThreshold`, mis-application is possible. Use
  `PatchProcessors.Strict` when correctness dominates.
* **`ThermodynamicPatchMatcher.temperature`** is accepted but not currently used in the scoring functions; stringency is
  governed by `minBindingEnergy`, `entropyPenalty` and `cooperativityBonus`.
* **`JsonUtil.merge` is shallow.** It replaces top-level fields; use `DataMergeProcessor.deepMerge`
  semantics (via `applyPatch`) for nested structures.
* **`isBinary` on `InputStream`** consumes the stream and compares against `available()`, which is only a lower bound
  for some stream types.
* **Oversized hunks vanish.** Hunks above `MAX_DIFF_SIZE_CHARS` are skipped by `apply()` without an entry in
  `errors`; the only evidence is that `newCode` is unchanged for that hunk.
* **`DataMergeProcessor.detectFormat` is heuristic.** A YAML document containing `=` and brackets can be read as TOML,
  and an ambiguous document falls back to JSON. Keep patch and source in the same syntax when it matters.
* **Array semantics are replace-only.** There is no element-wise merge, so a patch must repeat every element it wants to
  keep.
* **`validatorProviders` is unsynchronized.** Register providers during startup only.

## Glossary

| Term               | Meaning in this codebase                                                                                  |
|--------------------|-----------------------------------------------------------------------------------------------------------|
| **Segment**        | One typed piece of a model response: `Markdown`, `NewFileBlock` or `DiffBlock`.                           |
| **Hunk**           | One contiguous ```` ```diff ```` block, applied atomically by `applyPatch`.                               |
| **Snippet mode**   | Applying a context-only block (no `+`/`-` markers) by locating and replacing the matching region.         |
| **Anchor**         | An unambiguous line pair produced by `linkUniqueMatchingLines`; growth proceeds outward from anchors.     |
| **Linking**        | Establishing `matchingLine` pointers between source and patch `LineRecord`s.                              |
| **Normalization**  | `normalizeLine` — what "equal" means for an engine. The one real difference between `Fuzzy` and `Python`. |
| **Binding energy** | `ThermodynamicPatchMatcher`'s per-line-pair score; negative is favourable.                                |
| **Binding site**   | A candidate source offset at which a thermodynamic patch could be applied.                                |
| **Cooperativity**  | Bonus for extending an existing match, biasing alignments toward contiguity.                              |
| **Safety valve**   | The rule that leftover `ADD` lines are appended only if some context matched.                             |
| **Preset**         | A `PatchProcessors` enum constant: a named, serializable engine configuration.                            |

---

---

## Build & publishing

```bash
./gradlew build          # compile + test
./gradlew test           # tests only
./gradlew publishToMavenLocal
```

Notes:

* Kotlin compilation is forced ahead of Java (`compileJava dependsOn compileKotlin`) because the ANTLR validators
  reference generated Java types from Kotlin.
* `resolutionStrategy.force(libs.antlr.runtime)` pins a single ANTLR runtime across the graph.
* `java { withJavadocJar(); withSourcesJar() }` — the `maven` publication ships binaries, sources and javadoc under
  `com.cognotik:core`.
* Signing is enabled when `signingInMemoryKey` / `signingInMemoryKeyPassword` Gradle properties (or
  `SIGNING_KEY` / `SIGNING_PASSWORD` environment variables) are present; otherwise it is skipped so local builds work
  unsigned.
* `group` and `version` come from the `libraryGroup` / `libraryVersion` Gradle properties.

## Contributing

The library's contract is "never make a file worse", so changes are judged mainly on their effect on the fixture suite.

1. **Reproduce first.** Add a minimal JSON fixture to `src/test/resources` that fails before your change. The existing
   fixtures show the preferred granularity — one behaviour per file.
2. **Declare the capability matrix.** Add the fixture to `testCases()`/`patchTestCases()` for every engine that should
   pass it, and leave it commented out (not deleted) for engines that should not. Those commented lines are the
   documentation of each engine's limits.
3. **Do not regress the matrix.** A change that makes one engine pass a new fixture while silently dropping another is a
   net loss; call it out explicitly in the PR description.
4. **Keep `normalizeLine` the seam.** Prefer overriding normalization over adding conditionals inside the linking
   algorithms; `FuzzyPatchMatcher` is `open` for exactly this reason.
5. **New engines implement `PatchProcessor`** and ship their own `patchFormatPrompt`. A preset in
   `PatchProcessors` is only warranted once the engine passes a meaningful slice of the fixtures.
6. **New validators go through `FileValidators.validatorProviders`.** Never hard-code a validator inside an engine.
7. Run `./gradlew test` before opening a PR, and raise the log level in `src/test/resources/logback.xml` if the per-line
   traces drown the output.

### Compatibility

`PatchParser`, `PatchProcessor`, `GrammarValidator` and `DiffApplicationResult` are the public surface; treat their
signatures as semver-relevant. Engine *behaviour* is explicitly heuristic and may change between minor versions — pin a
version if byte-identical output matters to you, and prefer `Strict` or
`FullReplacement` when reproducibility outweighs tolerance.

## License

Apache License 2.0 — see the POM metadata in `build.gradle.kts`.