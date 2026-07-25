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

Logback is `compileOnly` — bring your own SLF4J binding.

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

```
### src/main/Example.kt
```diff
...
```

```

```

--------------------
File: src/main/Example.kt
--------------------

```

**3. Bare fences + `defaultFile`.** With no header and no marker, `defaultFile` is used; if that is
also absent, the block degrades to `Markdown`.

### Fence matching

`getMarkdownCodeBlockMatches` does **not** use a regex over the whole document. It:

1. Scans line-by-line for `^(\s*)```(.*)$`, recording indentation and the info-string.
2. Classifies a fence as *opening* when the info-string is non-empty (a language tag).
3. Pairs fences with a depth counter, **only considering fences at the same indentation** as the
 opening fence; deeper-indented fences are treated as block content.

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

Finally, a header consisting solely of a **language keyword** (`kotlin`, `py`, `dockerfile`, …) is
normalized to the empty string so `### kotlin` is never mistaken for a path.

### Diff detection

```kotlin
isDiffContent(lang, code) =
  lang == "diff" (case-insensitive)
  || every line starts with one of: '', ' ', '\t', '@', '-', '+'
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

---

## Patch engines

| Engine                      | Strategy                                                 | Best for                             | Handles new files |
|-----------------------------|----------------------------------------------------------|--------------------------------------|-------------------|
| `FuzzyPatchMatcher`         | line linking + Levenshtein + snippet fallback            | general purpose, default             | yes               |
| `ThermodynamicPatchMatcher` | Needleman–Wunsch DP over a binding-energy matrix         | small, noisy hunks                   | partial           |
| `PythonPatcher`             | same linking model, indentation-preserving normalization | Python / YAML                        | yes               |
| `FullReplacementProcessor`  | no diff at all                                           | large rewrites, small files          | yes               |
| `DataMergeProcessor`        | structured deep merge                                    | JSON/YAML/XML/TOML/properties config | yes               |

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

## License

Apache License 2.0 — see the POM metadata in `build.gradle.kts`.
