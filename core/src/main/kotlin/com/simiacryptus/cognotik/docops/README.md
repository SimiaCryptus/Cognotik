# Cognotik Doc-Ops (`com.simiacryptus.cognotik.docops`)

A **platform‑neutral, documentation‑driven code generation engine**.

You write ordinary Markdown files with YAML frontmatter that declare *which files they own*
(`specifies:`, `transforms:`, `documents:`, `generates:`, `folder:`), and this module turns those declarations into a
dependency‑ordered plan of concrete "modification tasks". The actual execution of a task (calling an LLM, running an
agent, applying a patch) is delegated to the host application through a small set of interfaces, so the planning logic
has **no dependency on any particular agent framework, thread pool, or session model**.

---

## Table of contents

1. [Why this module exists](#why-this-module-exists)
2. [Architecture at a glance](#architecture-at-a-glance)
3. [File map](#file-map)
4. [Quick start: implementing the host bindings](#quick-start-implementing-the-host-bindings)
5. [Doc spec reference (frontmatter)](#doc-spec-reference-frontmatter)
6. [Pattern expansion](#pattern-expansion)
7. [Related resources and URL caching](#related-resources-and-url-caching)
8. [Template variables](#template-variables)
9. [Update modes](#update-modes)
10. [The planning pipeline in detail](#the-planning-pipeline-in-detail)
11. [Execution model](#execution-model)
12. [Status tracking (`docops.status.json`)](#status-tracking-docopsstatusjson)
13. [API summary](#api-summary)
14. [Gotchas and operational notes](#gotchas-and-operational-notes)

---

## Why this module exists

Documentation rots. The idea here is to invert the relationship: **the document is the source of truth**, and the files
it describes are derived artifacts that can be regenerated (or incrementally patched) whenever the document — or any of
its declared inputs — changes.

A single Markdown file can:

* **specify** one or more target files (`specifies:`), i.e. "this doc describes `src/Foo.kt`";
* **transform** a set of files into another set via regex rename rules (`transforms:`);
* **document** source files (`documents:`), i.e. the doc *itself* is the generated target;
* **generate** an output file from a set of inputs (`generates:`);
* own an entire **folder** (`folder:`), which also becomes the effective root for the task;
* attach arbitrary **related** context, including remote URLs (`related:`).

The engine then decides, per target, *whether* work is needed at all (see
[Update modes](#update-modes)), builds a prompt/config, orders tasks so that dependencies are produced before consumers,
runs them concurrently where it is safe, and records progress in a resumable status file.

---

## Architecture at a glance

```text
docsFolder/**.md
    │
    │  parseMarkdownWithFrontmatter()      ← YAML frontmatter + template substitution
    ▼
List<DocSpec>
    │
    │  fileToSpecs / transformMatches / documentMatches / generateMatches
    │  (glob + regex expansion, recursive transitive discovery)
    ▼
target file  ──►  buildModificationTask()
                      │  primarySource(), allRelatedFiles(),
                      │  resolveUpdateMode() → UpdateMode.prepare()  ← may SKIP or DELETE
                      │  resolveTaskType(), resolveTaskConfigJson()
                      ▼
                 ModificationTask<K>
    │
    │  separateQueues()  → sortByDependencies()  (Kahn topological sort, cycle-breaking)
    ▼
DocTaskScheduler.submit { ... }                       ← host concurrency
    │
    │  per queue: newExecutionContext() (AutoCloseable)
    │      ctx.reset()
    │      executionConfig(mod, ctx)  → maybe ctx.inferTaskConfig(...)
    ▼
DocExecutionContext.execute(DocTaskRequest, DocTaskCallbacks)   ← host execution
    │
    └─► callbacks → setTaskStatus(...) → docops.status.json
```

Three type parameters keep the core generic:

| Param             | Meaning                                                            | Supplied by             |
|-------------------|--------------------------------------------------------------------|-------------------------|
| `K : DocTaskKind` | The *kind* of work (e.g. `CodeEdit`, `ImageVariation`, `Template`) | Host, usually an `enum` |
| `S : Any`         | Opaque session handle returned by the host when a task starts      | Host                    |
| `P : Any`         | Opaque patch‑processor type (in practice `PatchProcessor`)         | Host                    |

---

## File map

| File                  | Contents                                                                                                                                                               |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DocExecution.kt`     | The host‑facing SPI: `DocTaskKind`, `DocTaskKindResolver`, `DocTaskScheduler`, `DocTaskRequest`, `DocTaskInferenceRequest`, `DocTaskCallbacks`, `DocExecutionContext`. |
| `DocProcessorBase.kt` | The engine: frontmatter parsing, pattern expansion, URL caching, planning, dependency sorting, execution config assembly, orchestration.                               |
| `DocStatus.kt`        | Thread‑safe persistence of `docops.status.json` (`TaskStatus`, `TaskStatusEntry`, `DocOpsStatus`).                                                                     |
| `UpdateModes.kt`      | The `UpdateMode` strategy interface, the built‑in `UpdateModes` enum, and `UpdatePrepareResult`.                                                                       |

---

## Quick start: implementing the host bindings

### 1. Describe your task kinds

```kotlin
enum class MyTaskKind(
  override val isFileTask: Boolean = false,
  override val isSubPlanTask: Boolean = false,
  override val isTemplateTask: Boolean = false,
) : DocTaskKind {

  /** Normal single-file edit: context files are inlined into the prompt. */
  CodeEdit,

  /** Engine supplies files itself — do NOT inline context into the message. */
  FileModification(isFileTask = true),

  /** Renders an `.erb` template found among the related files. */
  ErbTemplate(isFileTask = true, isTemplateTask = true),

  /** Expands into a sub-plan instead of one edit. */
  PlanExpansion(isSubPlanTask = true),

  ImageVariation {
    override fun defaultConfig() = mapOf(
      "task_type" to name,
      "resolution" to 1024,
    )
  };

  override val name: String get() = super.name
}
```

`name` is the discriminator matched against `task_type:` in frontmatter, and is written into the
`task_type` key of the generated execution config.

Flag semantics:

| Flag                  | Effect in the engine                                                                                                                                   |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `isFileTask`          | Prompt becomes just `"Execute task."` — context files are **not** inlined (the engine/host already has them). Execution config is built declaratively. |
| `isTemplateTask`      | Execution config additionally gets `template_file` = first related file whose path ends with `.erb`.                                                   |
| `isSubPlanTask`       | Changes the auto‑generated task description to "Perform &lt;name&gt; generation."                                                                      |
| *(none of the above)* | The host is asked to **infer** the execution config via `inferTaskConfig`.                                                                             |
| `defaultConfig()`     | Fallback for `ModificationTask.typeConfig` when no `task_config_json` override exists.                                                                 |

### 2. Resolve names

```kotlin
object MyKinds : DocTaskKindResolver<MyTaskKind> {
  override val default = MyTaskKind.CodeEdit
  override fun byName(name: String) =
    MyTaskKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
```

Unknown `task_type:` values log a warning and fall back to `default`.

### 3. Provide concurrency

```kotlin
class PoolScheduler(private val pool: ExecutorService) : DocTaskScheduler {
  override fun submit(block: () -> Unit): CompletableFuture<*> =
    CompletableFuture.runAsync(block, pool)
}
```

### 4. Provide execution

One `DocExecutionContext` is created **per queue** and closed when the queue finishes, so it is a natural place to hold
a long‑lived agent harness / chat session.

```kotlin
class MyExecutionContext : DocExecutionContext<MyTaskKind, MySession, PatchProcessor> {

  override fun reset() { /* drop per-task conversation state */
  }

  override fun inferTaskConfig(
    request: DocTaskInferenceRequest<MyTaskKind, PatchProcessor>
  ): Map<String, Any> = myPlanner.infer(
    kind = request.taskKind,
    description = request.taskDescription,
    prompt = request.prompt,
    history = request.history,
    cwd = request.workingDir,
  )

  override fun execute(
    request: DocTaskRequest<MyTaskKind, PatchProcessor>,
    callbacks: DocTaskCallbacks<MySession>
  ) {
    val session = agent.newSession(request.workingDir)
    callbacks.onSessionStarted(session, session.id)   // may throw CancellationException
    try {
      agent.run(
        config = request.executionConfig,
        typeConfig = request.typeConfig,
        message = request.message,
        patcher = request.patchProcessor,
        timeout = Duration.ofMinutes(request.timeoutMinutes.toLong()),
      )
      callbacks.onCompleted(session.id)
    } catch (e: Throwable) {
      callbacks.onFailed(e)
      throw e
    }
  }

  override fun close() {
    agent.shutdown()
  }
}
```

### 5. Wire up the processor

```kotlin
class MyDocProcessor(root: File, docs: File) :
  DocProcessorBase<MyTaskKind, MySession, PatchProcessor>(
    root = root,
    docsFolder = docs,
    updateMode = UpdateModes.PatchToUpdate,
    additionalContext = { spec, target -> listOf("$root/build/schema.json") },
    templateVarOverrides = mapOf("PROJECT" to "cognotik"),
  ) {
  override val taskKinds = MyKinds
  override fun newScheduler() = PoolScheduler(Executors.newFixedThreadPool(4))
  override fun newExecutionContext() = MyExecutionContext()
  override val taskTimeoutMinutes = 20
  override val overallTimeoutMinutes = 120L
}

// Plan + run everything under docsFolder:
MyDocProcessor(root, docsFolder).run()

// Or drive it manually:
val processor = MyDocProcessor(root, docsFolder)
val tasks = processor.getAll(File(docsFolder, "api.md"))
val cancel = AtomicBoolean(false)
val sessions = processor.runAll(tasks, cancelFlag = cancel) { session -> ui.attach(session) }
```

---

## Doc spec reference (frontmatter)

A Markdown file participates in doc‑ops if it starts with `---`, contains a closing `---`, and declares **at least one**
of `specifies`, `transforms`, `documents`, `generates`, or `folder`. Anything else is ignored (returns `null` from
`parseMarkdownWithFrontmatter`).

```md
---
specifies:
  - ../src/main/kotlin/com/example/Widget.kt
  - "*.props"
related:
  - ./style-guide.md
  - https://example.com/spec.html
task_type: CodeEdit
task_config_json: ./widget-task.json
update_mode: PatchToUpdate
prompt: |
  Keep the public API stable.
---

# Widget

Prose here becomes part of the task description.
```

### Supported keys

| Key                                                           | Type                                                 | Meaning                                                                                                                                                              |
|---------------------------------------------------------------|------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `specifies`                                                   | string \| list                                       | Target files this doc owns. Globs and literals allowed, resolved **relative to the doc file's directory**.                                                           |
| `transforms`                                                  | string \| list of `"<sourceRegex> -> <destPattern>"` | Regex‑driven source→destination mapping. See [Pattern expansion](#pattern-expansion).                                                                                |
| `documents`                                                   | string \| list                                       | Source files that this doc *describes*; here the **doc file itself is the target** and the matched files are context.                                                |
| `generates`                                                   | map \| list of maps `{output, inputs}`               | Produce `output` from `inputs`.                                                                                                                                      |
| `folder`                                                      | string                                               | Target folder. Also **overrides the effective root** for the task (must stay under `root`). If no other target key is present, the folder itself becomes the target. |
| `related`                                                     | string \| list                                       | Extra context: files, globs, or `http(s)://` URLs.                                                                                                                   |
| `prompt`                                                      | string                                               | If exactly **one** spec matches a target, this string becomes the task description verbatim.                                                                         |
| `task_type`                                                   | string                                               | Name of a `DocTaskKind`. Unknown → warning + default kind.                                                                                                           |
| `task_config_json`                                            | string                                               | Path (relative to the doc) of a JSON file whose contents become `taskConfigOverrides` / `typeConfig`.                                                                |
| `update_mode`                                                 | string                                               | Per‑doc `UpdateModes` name (case‑insensitive), overriding the global mode.                                                                                           |
| `template_vars` / `template_variables` / `vars` / `variables` | map \| list \| string                                | Template variables, see [Template variables](#template-variables).                                                                                                   |

Any other keys are preserved in `DocSpec.frontmatter` and available to subclasses.

### Markdown links are unwrapped

Every path‑bearing value accepts a Markdown link, so docs stay clickable in an IDE or on GitHub:

```md
specifies:

- [Widget](../src/main/kotlin/com/example/Widget.kt)
  transforms:
- "[src](src/ (.*)\\.proto) -> [gen](gen/$1.kt)"
```

`extractPathFromMarkdownLink("[x](y)") == "y"`; plain values are just trimmed.

### YAML subset caveat

`parseFrontmatter` is a deliberately tiny hand‑rolled parser, **not** a YAML engine. It understands:

* `key: value` → `String`
* `key:` followed by `  - item` lines → `List<String>`

It does **not** understand nested maps, block scalars (`|`, `>`), quoting rules, comments, or anchors. Consequences:

* `prompt: |` multi‑line blocks will only capture the first line (`"|"` → the value is `"|"`), so prefer single‑line
  prompts or put prose in the body.
* `generates:` requires map‑shaped values, which this parser cannot produce; use it only if your host feeds a richer
  frontmatter map into `DocSpec`, or drive generation through `specifies`
  plus `related`.
* Template variables are most reliably declared as a list of `key: value` strings:

```md
vars:

- PROJECT: cognotik
- LANG=kotlin
```

---

## Pattern expansion

### Globs (`specifies`, `documents`, `generates.inputs`, `related`)

* A value is treated as a glob if it contains `*`, `?`, or `[` (`isGlobPattern`).
* Patterns containing `**` use `expandRecursiveGlob`: everything before `**` becomes the base directory, and the
  remainder is matched with a `glob:` `PathMatcher` **against the file name only**, recursively.
* `src/**/*.kt` → walks `src/` and keeps files whose *name* matches `*.kt`.
* `src/**` → every file under `src/`.
* Other globs use `expandSimpleGlob`: the parent segment is resolved as a literal directory and the last segment is
  matched against file names in that directory (non‑recursive).
* Non‑glob values are resolved as literal (canonical) paths, **even if they don't exist yet** — this is what lets a doc
  specify a file that has not been created.

### Transforms (regex, not glob)

```md
transforms:

- "protos/ (.*)\\.proto -> generated/$1.kt"
- "v (\\d+)/api\\.md -> v$1+1/api.md"
```

* Every file under `root` is walked (`root.listFilesRecursively()`).
* The candidate path is made **relative to the doc file's parent directory**, with `\` normalized to
  `/`, then matched with `Pattern.matches()` (full match, Java regex).
* The destination is produced by `applyBackreferences`, which supports:
* `$n` — capture group *n*;
* `$n+k` / `$n-k` — numeric arithmetic on the captured group (`v3` → `$1+1` → `4`). If the group is not numeric the
  suffix is appended literally.
* Destination paths are resolved relative to the doc file's directory and canonicalized.
* Invalid regexes are logged and skipped.

### Transitive (recursive) discovery

After building tasks for the currently known targets, the planner asks: *would any of the newly discovered targets
themselves match a transform's source pattern?* If so it recurses (`modificationTasksRecursive`), up to **depth 10**,
deduplicating against already planned targets. This makes chained pipelines work (`a.proto → a.kt → a.docs.md`) without
you having to enumerate the intermediate steps. Hitting the depth limit logs a warning and usually means you have a
circular transform rule.

---

## Related resources and URL caching

`related:` entries are resolved by `resolveRelatedResources`:

* `http://` / `https://` → fetched via `java.net.http.HttpClient`
  (30 s connect, 60 s request, follows redirects, browser‑ish `User-Agent`).
* `text/html` (or missing content type) responses are run through `HtmlSimplifier.scrubHtml`
  (scripts, media, event handlers, interactive elements and object ids stripped). If that fails, a crude tag‑stripping
  fallback is used.
* Results are cached in `urlCacheDir` (default `<root>/.doc-processor-cache/url-cache`) as
  `<sha256[0..16]>_<sanitized-name>` plus a `.meta` sidecar recording url, fetch time and content type. **Cache TTL is 1
  hour**; non‑2xx responses return `null` (the entry is dropped).
* Globs → expanded as described above.
* Plain paths → returned even when missing (logged at debug), so a doc can reference a file that a sibling task is about
  to create.

In addition to `related:`, the `additionalContext: (DocSpec, File) -> List<String>` constructor hook lets the host
inject computed context paths per (spec, target) pair.

---

## Template variables

Docs can be parameterized. Declared variables are merged with the processor‑wide
`templateVarOverrides` (overrides win), then `{{ NAME }}` placeholders are substituted in:

1. the **frontmatter** (the template‑var keys themselves are stripped first, the rest is re‑rendered to YAML by
   `renderFrontmatterToYaml` and re‑parsed), and
2. the **body** of the document, and
3. the doc body appended to `task_description` inside `executionConfig`.

Placeholder syntax: `{{ NAME }}` where `NAME` matches `[A-Za-z_][A-Za-z0-9_]*`. Unknown names are left untouched (so
unrelated Mustache/ERB content survives). Replacements are escaped, so `$` in a value is safe.

Because frontmatter is substituted *before* pattern expansion, variables can parameterize target paths:

```md
---
vars:
  - MODULE: core
specifies:
  - ../{{ MODULE }}/src/Main.kt
---
```

Discovery helpers (useful for building a UI that prompts for values):

```kotlin
DocProcessorBase.listTemplateVarKeys(file)            // Map<String, String> defaults
DocProcessorBase.listTemplateVarKeys(listOf(a, b))    // merged, first declaration wins
DocProcessorBase.TEMPLATE_VAR_KEYS                    // recognized frontmatter keys
```

---

## Update modes

Before a task is emitted, the resolved `UpdateMode.prepare(source, target, relatedFiles)` decides whether work is needed
and how the result should be applied. Returning `null` **skips the target entirely** (no task, no status entry).

| Mode                        | Target missing    | Target exists, stale       | Target exists, fresh       |
|-----------------------------|-------------------|----------------------------|----------------------------|
| `SkipExisting`              | `FullReplacement` | *skip*                     | *skip*                     |
| `OverwriteExisting`         | `FullReplacement` | `FullReplacement` + delete | `FullReplacement` + delete |
| `OverwriteToUpdate`         | `FullReplacement` | `FullReplacement` + delete | *skip*                     |
| `PatchExisting`             | `Fuzzy`           | `Fuzzy`                    | `Fuzzy`                    |
| `PatchToUpdate` *(default)* | `Fuzzy`           | `Fuzzy`                    | *skip*                     |
| `ForceUpdate`               | `FullReplacement` | `Fuzzy`                    | `Fuzzy`                    |
| `ForceOverwrite`            | `FullReplacement` | `FullReplacement` + delete | `FullReplacement` + delete |

* **Staleness** = `max(lastModified)` over the primary source **and** all related files (see
  `UpdateModes.lastModifiedOfSources`) is greater than the target's `lastModified`. Deduplicated, and missing files are
  ignored.
* `shouldDeleteTarget` causes the planner to delete the target file immediately during planning, so the executor starts
  from a clean slate.
* Precedence: `update_mode:` on the matching spec / transform / document / generate wins; otherwise the processor‑wide
  `updateMode`. Unknown names log a warning and fall back to the global mode.
* `UpdateMode` is an interface — hosts can plug in arbitrary policies (e.g. git‑diff‑aware, hash based) without touching
  the engine.

---

## The planning pipeline in detail

### 1. Matching

Four independent match maps are built, all keyed by **absolute target path** (case‑insensitively grouped, then re‑keyed
to the original casing):

| Map                | Target is                                                                                     | Context is                     |
|--------------------|-----------------------------------------------------------------------------------------------|--------------------------------|
| `fileToSpecs`      | each file matched by `specifies` (plus every transform destination, plus `folder`‑only specs) | the doc                        |
| `transformMatches` | `destinationFile`                                                                             | `sourceFile` + doc's `related` |
| `documentMatches`  | the **doc file itself**                                                                       | the matched `documents` files  |
| `generateMatches`  | `outputFile`                                                                                  | `inputFiles`                   |

### 2. Combining

For each distinct target, all matches across all four maps are merged into a single
`ModificationTask`:

* **`primarySource`** priority: transform source → first spec doc → first document supporting file → first generate
  input. `folder`‑only specs fall back to the doc file itself.
* **`allRelatedFiles`** = union (distinct) of spec `related`, transform sources, document supporting files, generate
  inputs, and `additionalContext(...)`.
* **`resolveEffectiveRoot`** — if any contributing spec declares `folder:`, that directory becomes the task root. It is
  validated to be at or under `root`, otherwise an `IllegalArgumentException`
  is thrown.
* **`resolveTaskType`** — first non‑null `task_type` across specs → transforms → documents → generates, resolved through
  `taskKinds`.
* **`resolveTaskConfigJson`** — first `task_config_json` found; parsed with `JsonUtil` into
  `taskConfigOverrides`. Missing or unparsable files log a warning and are ignored.
* **`buildCombinedTaskDescription`** — a single `prompt:` wins; otherwise a sensible default is synthesized based on the
  task kind and which match types are present ("Update the file X based on the included documentation…", "Update the
  documentation file X based on the supporting source files…", "Generate or update the file X…", "Perform &lt;kind&gt;
  generation.").
* Targets outside `root` are logged and dropped. Any exception while building a single task is logged and that task is
  skipped — one bad doc never aborts the whole run.

### 3. The prompt (`ModificationTask.message()`)

* `isFileTask` kinds get the literal message `"Execute task."` — the host is expected to feed files to the model itself.
* Everything else gets each related file inlined as:

```text
  # Context file: <path relative to task root>
  ```

<file contents, YAML frontmatter stripped for .md files>

  ```
```

Missing files are represented as `<!-- File not found: … -->` so the model can see the gap.

### 4. The execution config (`executionConfig(mod, ctx)`)

A JSON‑compatible `MutableMap<String, Any>`:

* **File tasks**: `{"task_type": <kind>} + <ModificationTaskConfig serialized> + taskConfigOverrides`.
* **Template tasks**: as above, plus `template_file` = first related file whose path ends with `.erb`.
* **Everything else**: `ctx.inferTaskConfig(DocTaskInferenceRequest(...))`, merged with
  `taskConfigOverrides` via `JsonUtil.merge`. The inference request carries the task description, a generic prompt, the
  working dir, the patch processor, `typeConfig`, and a `history` list containing the task type, description, output
  file (s), the full text of every existing related file, and the rendered message.

Post‑processing applied to every config:

* `related_files` is normalized to a `List<String>` (never `null`).
* `task_description` is rebuilt as the combined description **plus the body of the first doc file**
  (frontmatter stripped, template variables applied).

Note that for non‑file tasks the working directory is narrowed to the **target file's parent directory**
(`main_file.parentFile`), and the task data is rebased accordingly.

---

## Execution model

`runAll(fileMods, scheduler, cancelFlag, onNewSession)`:

1. `initializeStatus(fileMods)` — writes/merges `PENDING` entries (existing `COMPLETED` entries from earlier runs are
   preserved and reported in the log).
2. **`separateQueues`** — tasks are partitioned so that tasks touching an already‑seen target or related file start a
   **new** queue. Everything else is appended to the current queue. Queues run concurrently; tasks *within* a queue run
   sequentially. This is a conservative, cheap way to avoid two agents editing overlapping files at the same time.
3. **`sortByDependencies`** — within each queue, Kahn's algorithm orders tasks so that a task whose
   `related_files` include another task's `main_file` runs *after* it. Cycles are detected and broken by picking the
   task with the fewest unresolved dependencies (logged as a warning), so the sort always terminates and never drops
   tasks.
4. Each queue is submitted to the `DocTaskScheduler`. A fresh `DocExecutionContext` is created for the queue and closed
   with `use { }` afterwards.
5. Per task: `ctx.reset()`, cancellation check, status → `RUNNING`, then
   `ctx.execute(DocTaskRequest, DocTaskCallbacks)`.
6. `allOf(futures).get(overallTimeoutMinutes, MINUTES)` waits for completion.

### Cancellation

Pass an `AtomicBoolean` as `cancelFlag`. It is polled:

* before a queue starts (remaining tasks → `CANCELLED`),
* before each task,
* inside `onSessionStarted` — the callback throws `CancellationException`, which the host's
  `execute` should let propagate to abort mid‑flight work.

A `CancellationException` escaping a task marks the current task and all remaining tasks in that queue as `CANCELLED`;
any other `Throwable` marks them `FAILED` with `"Queue aborted: …"`.

### Timeouts

| Setting                 | Default | Effect                                                                                                           |
|-------------------------|---------|------------------------------------------------------------------------------------------------------------------|
| `taskTimeoutMinutes`    | 30      | Passed through to the host in `DocTaskRequest.timeoutMinutes`; the host enforces it.                             |
| `overallTimeoutMinutes` | 90      | Enforced by `runAll`. On timeout all `RUNNING` tasks are marked `FAILED` and the `TimeoutException` is rethrown. |

`ExecutionException` → remaining `RUNNING` tasks marked `FAILED`; `InterruptedException` → marked
`CANCELLED`. In all three cases the exception is rethrown after the status file is updated.

### Sessions

Every `onSessionStarted` handle is appended to the list returned by `runAll` and forwarded to the
`onNewSession` callback, which is how a host UI can attach to running agents live.

---

## Status tracking (`docops.status.json`)

Written at `<root>/docops.status.json`, guarded by a process‑wide lock (`DocStatus.statusLock`) so concurrent queues
cannot corrupt it.

```json
{
  "lastUpdated": "2024-05-01T12:34:56.789Z",
  "tasks": {
    "src/main/kotlin/com/example/Widget.kt": {
      "target": "src/main/kotlin/com/example/Widget.kt",
      "status": "COMPLETED",
      "sessionId": "abc123",
      "startedAt": "2024-05-01T12:30:00Z",
      "completedAt": "2024-05-01T12:34:56Z",
      "error": null
    }
  }
}
```

* **Keys** are the target path relative to `root` (absolute if the target is outside `root`,
  `"unknown"` if there is no main file).
* **Statuses**: `PENDING → RUNNING → COMPLETED | FAILED | CANCELLED`.
* `startedAt` is stamped on transition to `RUNNING`; `completedAt` on any terminal status; otherwise previous values are
  preserved. A `sessionId` of `null` preserves the existing one.
* A corrupt or unreadable file is logged and treated as empty, so a bad status file never blocks a run.
* Because `initializeStatus` merges rather than truncates, the file doubles as a **resume log** — a host can inspect it
  to decide what to re‑run.

---

## API summary

### Must implement

```kotlin
protected abstract val taskKinds: DocTaskKindResolver<K>
protected abstract fun newScheduler(): DocTaskScheduler
protected abstract fun newExecutionContext(): DocExecutionContext<K, S, P>
```

### May override

```kotlin
protected open val taskTimeoutMinutes: Int = 30
protected open val overallTimeoutMinutes: Long = 90
```

### Useful public entry points

| Member                                                                                                               | Purpose                                                                    |
|----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `run()`                                                                                                              | Scan `docsFolder` for `*.md` / `*.markdown`, plan, and execute everything. |
| `getAll(vararg files)`                                                                                               | Plan tasks for specific doc files (no execution).                          |
| `modificationTasks(docSpecs)`                                                                                        | Plan from already‑parsed specs.                                            |
| `runAll(tasks, scheduler, cancelFlag, onNewSession)`                                                                 | Execute a plan; returns the session handles.                               |
| `run(mod, ctx, cancelFlag, onNewSession, sessions)`                                                                  | Execute a single task in a supplied context.                               |
| `parseMarkdownWithFrontmatter(file)`                                                                                 | `DocSpec?` for one file.                                                   |
| `sortByDependencies(tasks)` / `separateQueues(tasks)`                                                                | Expose the ordering/partitioning logic.                                    |
| `executionConfig(mod, ctx)`                                                                                          | Build the JSON config for one task (handy for dry runs).                   |
| `initializeStatus(tasks)`                                                                                            | Seed the status file without running anything.                             |
| `isUrl`, `resolveRelatedResource(s)`                                                                                 | URL/path resolution with caching.                                          |
| `expandPatternOrLiteral`, `expandSimpleGlob`, `expandRecursiveGlob`, `expandTransformPattern`, `applyBackreferences` | Static pattern utilities (also unit‑testable in isolation).                |
| `listTemplateVarKeys`, `parseTemplateVars`, `applyTemplateSubstitutions`                                             | Template variable tooling.                                                 |
| `UpdateModes.fromName(name)`                                                                                         | Case‑insensitive update‑mode lookup.                                       |

### Model types

* `DocSpec` — one parsed document (targets, related, body, frontmatter, task type/config, update mode, target folder).
* `TransformSpec` / `TransformMatch`, `GenerateSpec` / `GenerateMatch`, `DocumentMatch`.
* `ModificationTaskConfig` — root, `main_file`, `related_files`, `doc_files`, `task_description`,
  `taskConfigOverrides`, plus `relative_*` projections used when serializing to JSON.
* `ModificationTask<K>` — the planned unit of work: data, message builder, patch processor, task kind, and the derived
  `typeConfig` (`taskConfigOverrides` → `taskKind.defaultConfig()` →
  `{"task_type": name}`).

---

## Gotchas and operational notes

* **All doc paths are relative to the doc file's own directory**, not to `root`. `root` is only used as the walk base
  for `transforms`, as the relativization base for status keys, and as the containment boundary for `folder:`.
* **Targets outside `root` are dropped** during planning (with a warning). `folder:` values outside
  `root` throw.
* **Case handling**: matching is grouped case‑insensitively (via `normalizePath`, which lowercases)
  to behave sanely on Windows/macOS, then re‑keyed to the original path. On case‑sensitive file systems two targets
  differing only by case will collide.
* **Deletion happens at plan time**, not execute time: `shouldDeleteTarget` modes delete the target as soon as the task
  is built. If you plan but never run, the file is already gone.
* **`ModificationTask.shouldDeleteTarget` is vestigial** — the engine acts on
  `UpdatePrepareResult.shouldDeleteTarget` during planning.
* **`patchProcessor` is type‑erased** on `ModificationTask` (`Any?`); use the protected
  `patchProcessorAs()` helper to narrow it back to `P`.
* **Timestamp‑based staleness** means a `touch` on any related file re‑triggers work, and a checkout that rewrites
  mtimes can trigger a full regeneration. Use `SkipExisting` or a custom `UpdateMode`
  if you need content‑hash semantics.
* **URL cache TTL is 1 hour** and keyed on the URL; delete `<root>/.doc-processor-cache/url-cache`
  to force a refetch.
* **Queue partitioning is conservative, not exact** — it prevents overlap between *adjacent* tasks in plan order, but
  two independent queues could still touch the same file if the overlap is only discovered later. Prefer explicit
  `related:` links to make dependencies visible to the sorter.
* **Logging**: everything is logged through SLF4J under
  `com.simiacryptus.cognotik.docops.DocProcessorBase` / `DocStatus` / `UpdateModes`. Enable `DEBUG`
  to see per‑file glob resolution, skip decisions, transitive target discovery, and cache hits.