# docops

A small, host-agnostic engine that turns **markdown documents with YAML frontmatter** into **planned, scheduled,
executed file-modification tasks**.

A doc says *"this document specifies `src/**/*.kt`"*, or *"every `*.proto` transforms into a
`*.kt`"*, and `docops` figures out which files need to be created/updated, in what order, with which context files, and
hands each unit of work to the host platform to actually execute (typically via an LLM agent harness).

---

## Table of contents

* [Design principles](#design-principles)
* [Architecture](#architecture)
* [Quick start](#quick-start)
* [Document format](#document-format)
* [Target declarations](#target-declarations)
* [Context declarations](#context-declarations)
* [Behaviour declarations](#behaviour-declarations)
* [Template variables](#template-variables)
* [Transforms](#transforms)
* [Update modes](#update-modes)
* [Planning pipeline](#planning-pipeline)
* [Execution](#execution)
* [Status tracking](#status-tracking)
* [URL context and caching](#url-context-and-caching)
* [Extension points](#extension-points)
* [Testing](#testing)
* [Package layout](#package-layout)
* [Legacy files](#legacy-files)

---

## Design principles

1. **Planning is pure.** `DocOps.plan()` never mutates the workspace. It reads files and returns an immutable
   `WorkPlan`. Every destructive side effect (deleting a target, writing `docops.status.json`) happens inside
   `DocOps.run()`.
2. **Composition, not inheritance.** Hosts do *not* subclass anything. They provide
   `DocOpsConfig` (data) plus a `DocOpsHost` (three platform bindings).
3. **Everything is injectable.** The status store, spec loader, resource resolvers, HTTP fetcher, directory lister,
   planner, partitioner and sorter are all constructor parameters with sensible defaults — so tests never need the
   network or a real agent.
4. **Deterministic output.** Target iteration, queue partitioning and dependency sorting are all stably ordered, so the
   same inputs always produce the same plan.
5. **Strong typing at the seams.** Task kinds (`K`), host sessions (`S`) and patch processors are generic/typed; there
   are no erased `Any?` fields or unchecked casts in the task model.

---

## Architecture

```
 markdown files
       │
       ▼
 DocSpecLoader ────────────► DocSpec (frontmatter + body, template vars applied)
       │
       ▼
 TargetResolver[]  ────────► TargetContribution  ──► TargetIndex   (+ fixpoint expansion)
       │
       ▼
 TaskBuilder (policies + composers)                ──► BuildOutcome
       │                                                   │
       ▼                                                   ├─ Planned
 QueuePartitioner ──► DependencySorter ──► WorkPlan  ──────┼─ Skipped
       │                                                   └─ Failed
       ▼
 DocTaskRunner ──► DocTaskScheduler ──► DocExecutionContext ──► DocTaskRequest
       │                                                             │
       ▼                                                             ▼
 DocStatusStore                                           host agent / session
```

The whole engine is reached through one class:

```kt
 class DocOps<K : DocTaskKind, S : Any>(
  val config: DocOpsConfig,
  val host: DocOpsHost<K, S>,
  val statusStore: DocStatusStore = JsonFileDocStatusStore(config.root),
  val loader: DocSpecLoader = MarkdownDocSpecLoader(config.templateVarOverrides),
  val resources: ResourceResolver = defaultResources(config),
  val planner: DocPlanner<K> = defaultPlanner(config, host.taskKinds),
  val runner: DocTaskRunner<K, S> = DocTaskRunner(config, host, statusStore),
)
```

---

## Quick start

### 1. Describe your task kinds

```kt
 enum class MyTaskKind(override val name: String) : DocTaskKind {
  Edit("Edit"),
  FileModification("FileModification") {
    override val isFileTask get() = true
  },
  Template("Template") {
    override val isTemplateTask get() = true
  },
  SubPlan("SubPlan") {
    override val isSubPlanTask get() = true
  };

  override fun defaultConfig(): Map<String, Any>? = mapOf("task_type" to name)
}

object MyTaskKinds : DocTaskKindResolver<MyTaskKind> {
  override val default = MyTaskKind.Edit
  override fun byName(name: String) =
    MyTaskKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
```

`DocTaskKind` flags change how a task is prepared:

| Flag             | Meaning                                                                              |
|------------------|--------------------------------------------------------------------------------------|
| `isFileTask`     | The engine supplies context files itself — they are **not** inlined into the prompt. |
| `isSubPlanTask`  | The kind expands into a sub-plan rather than a single edit.                          |
| `isTemplateTask` | The kind renders a template; a related `*.erb` file becomes `template_file`.         |

### 2. Bind the platform

```kt
 class MyHost(private val pool: ExecutorService) : DocOpsHost<MyTaskKind, MySession> {
  override val taskKinds = MyTaskKinds

  override fun newScheduler() = object : DocTaskScheduler {
    override fun submit(block: () -> Unit) = CompletableFuture.runAsync(block, pool)
  }

  override fun newExecutionContext() = MyExecutionContext()
}

class MyExecutionContext : DocExecutionContext<MyTaskKind, MySession> {
  override fun reset() { /* drop per-task session state */
  }

  override fun inferTaskConfig(request: DocTaskInferenceRequest<MyTaskKind>): Map<String, Any> =
    askTheModelForAConfig(request)

  override fun execute(request: DocTaskRequest<MyTaskKind>, callbacks: DocTaskCallbacks<MySession>) {
    val session = startSession(request)
    callbacks.onSessionStarted(session, session.id)   // may throw CancellationException
    try {
      session.await(request.timeoutMinutes)
      callbacks.onCompleted(session.id)
    } catch (e: Throwable) {
      callbacks.onFailed(e)
    }
  }

  override fun close() { /* release resources */
  }
}
```

### 3. Plan and run

```kt
 val docOps = DocOps(
  config = DocOpsConfig(
    root = projectRoot,
    docsFolder = File(projectRoot, "docs"),
    updateMode = UpdateModes.PatchToUpdate,
    templateVarOverrides = mapOf("MODULE" to "billing"),
    taskTimeoutMinutes = 30,
    overallTimeoutMinutes = 90,
  ),
  host = MyHost(pool),
)

val plan = docOps.plan()                 // pure: no writes, no deletes

println("${plan.tasks.size} task(s) in ${plan.queues.size} queue(s)")
plan.skipped.forEach { println("skipped ${it.target}: ${it.reason}") }
plan.failed.forEach { println("failed  ${it.target}: ${it.error}") }

val cancel = AtomicBoolean(false)
val sessions = docOps.run(plan, cancelFlag = cancel) { session ->
  ui.attach(session)                     // called as each session starts
}
```

Useful variations:

```kt
 docOps.plan(File("docs/api.md"))                        // a single document
docOps.planSpecs(docOps.load(listOf(someFile)))          // pre-loaded specs
docOps.plan().filter { it.target.name == "Api.kt" }      // narrow to one target
docOps.initializeStatus(plan)                            // seed status, execute nothing
docOps.templateVarKeys()                                 // declared {{ VARS }} + defaults
```

---

## Document format

A doc-ops document is a markdown file whose frontmatter declares at least one target. Files without frontmatter, or
without any target declaration, are silently ignored.

```md
---
specifies:
    - src/main/kotlin/billing/[Invoice.kt](src/main/kotlin/billing/Invoice.kt)
    - src/main/kotlin/billing/Ledger.kt related:
    - schema/billing.sql
    - https://example.com/spec/invoicing
update_mode: PatchToUpdate 
task_type: Edit prompt: Keep the public API stable; only change internals.
---

# Invoicing

Invoices are immutable once issued...
```

The body (frontmatter stripped, template variables applied) becomes part of the task description passed to the host.

### Target declarations

At least one of these must be present, otherwise the file is not a doc-ops document (`DocSpec.hasTargets`).

| Key          | Type                      | Target                            | Notes                                                                                           |
|--------------|---------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------|
| `specifies`  | string \| list of strings | each matched path                 | Globs are expanded; **literal paths are kept even if missing** (they are meant to be created).  |
| `transforms` | string \| list of strings | `destinationPattern` per match    | `"regex -> destination"`; see [Transforms](#transforms).                                        |
| `documents`  | string \| list of strings | **the doc file itself**           | Glob-only; matched files become source/context. Used for "keep this doc in sync with the code". |
| `generates`  | map \| list of maps       | the declared `output`             | `{ output: ..., inputs: [...] }`; glob-only inputs; entries without inputs are dropped.         |
| `folder`     | string                    | the folder (only if nothing else) | Also overrides the effective task root — see below.                                             |

```md
---
transforms:
  - "proto/ (.*)\\.proto -> generated/$1.kt"
documents:
  - src/main/kotlin/billing/**/*.kt
---
```

All paths are resolved **relative to the document's own directory** (`DocSpec.baseDir`). Any value may be written as a
markdown link — `[label](real/path.kt)` is unwrapped to
`real/path.kt` by `MarkdownLinks`, so docs stay clickable in an IDE and on GitHub.

### Context declarations

| Key       | Type                      | Meaning                                                                                                                                                                     |
|-----------|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `related` | string \| list of strings | Extra context files. Supports literal paths, globs and `http(s)://` URLs. Non-existent literal paths are still passed through (a sibling task may be about to create them). |

For non-`isFileTask` kinds each related file is inlined into the prompt inside a fenced block
(`ContextMessageComposer`); leading frontmatter of related `*.md` files is stripped. For `isFileTask` kinds the message
is just `"Execute task."` and the host is expected to supply the files from `executionConfig["related_files"]`.

### Behaviour declarations

| Key                | Type   | Meaning                                                                                                                            |
|--------------------|--------|------------------------------------------------------------------------------------------------------------------------------------|
| `update_mode`      | string | Per-doc [update mode](#update-modes) (case-insensitive). Falls back to `DocOpsConfig.updateMode` if unknown.                       |
| `task_type`        | string | Resolved via `DocTaskKindResolver.byName`; falls back to `.default`.                                                               |
| `task_config_json` | string | Path (relative to the doc) to a JSON file merged over the derived execution config.                                                |
| `folder`           | string | Overrides the effective task root. **Must resolve to a path at or under `config.root`**, otherwise planning fails for that target. |
| `prompt`           | string | When exactly one doc contributes to a target, this replaces the generated task description preamble.                               |

When several documents contribute to the same target, policies pick a winner in this order:

```
 specifies  >  transforms  >  documents  >  generates  >  folder
```

The *primary source file* is picked by a slightly different order (`ContributionKind.sourcePriority`): `transforms` >
`specifies` > `documents` > `generates` > `folder`.

### Template variables

Declare defaults under any of `template_vars`, `template_variables`, `vars`, `variables`
(map form, `k: v` list form, or bare-name list form). `{{ NAME }}` placeholders are then substituted in **both the
remaining frontmatter and the body**; unknown placeholders are left untouched.

```md
---
template_vars:
  - MODULE: billing 
  - OWNER: platform-team 
specifies:
  - src/main/kotlin/{{ MODULE }}/Service.kt
---

Owned by {{ OWNER }}.
```

`DocOpsConfig.templateVarOverrides` wins over declared defaults, so a host UI can prompt for values discovered with
`DocOps.templateVarKeys()`:

```kt
val declared = docOps.templateVarKeys()          // { MODULE=billing, OWNER=platform-team }
val answers = ui.ask(declared)
val configured = DocOps(config.copyWith(templateVarOverrides = answers), host)
```

---

## Transforms

```
 transforms:
   - "<java-regex-over-relative-path> -> <destination-with-backreferences>"
```

* The regex must **fully match** the candidate path, relativized against the doc's directory and normalized to `/`.
* The destination supports `$n` backreferences plus integer arithmetic: `$1+1`, `$2-3`. Non-numeric groups with
  arithmetic are concatenated verbatim; out-of-range groups are left alone.
* Invalid regexes and malformed rules (missing `->`) are logged and skipped.

```md
---
transforms:
  - "src/(.*)\\.proto -> src/$1.kt"          # a.proto -> a.kt
  - "src/(.*)\\.kt -> docs/$1.md"            # a.kt    -> a.md   (chained!)
  - "chapters/ch(\\d+)\\.md -> chapters/ch$1+1.summary.md"
---
```

Chained pipelines work because the planner runs a **fixpoint pass**: after resolving real files it repeatedly asks every
transform *"would you consume this (possibly not-yet-existing)
target?"* and adds the resulting hypothetical targets, up to `DocOpsConfig.maxPlanningDepth`
(default 10). Hitting the limit logs a warning — it almost always means a circular rule.

---

## Update modes

`UpdateModes.prepare(source, target, relatedFiles)` decides *whether* a target is processed, *how* the result is applied
(`PatchProcessors.FullReplacement` vs `PatchProcessors.Fuzzy`), and whether the target is deleted first. Returning
`null` skips the target (`BuildOutcome.Skipped("update mode declined")`).

| Mode                | Existing target                            | Missing target   | Deletes first |
|---------------------|--------------------------------------------|------------------|---------------|
| `SkipExisting`      | skip                                       | full replacement | no            |
| `OverwriteExisting` | full replacement                           | full replacement | yes           |
| `OverwriteToUpdate` | full replacement **iff sources are newer** | full replacement | yes           |
| `PatchExisting`     | fuzzy patch                                | fuzzy patch      | no            |
| `PatchToUpdate`     | fuzzy patch **iff sources are newer**      | fuzzy patch      | no            |
| `ForceUpdate`       | fuzzy patch (always)                       | full replacement | no            |
| `ForceOverwrite`    | full replacement (always)                  | full replacement | yes           |

"Sources are newer" compares `target.lastModified()` against the **maximum** timestamp of the primary source *and* all
related files (`UpdateModes.lastModifiedOfSources`).

Deletion is recorded on the plan as `TargetPreparation(deleteTargetBeforeRun = true)` and only performed by
`DocTaskRunner` immediately before the task executes — planning stays pure.

---

## Planning pipeline

1. **Discover** — `markdownFiles()` walks `docsFolder` for `config.markdownExtensions`
   (`md`, `markdown` by default).
2. **Load** — `MarkdownDocSpecLoader` splits frontmatter, applies template variables, builds
   `DocSpec`; documents without targets are dropped.
3. **Resolve** — the five `TargetResolver`s (`specifies`, `transforms`, `documents`,
   `generates`, `folder`) emit `TargetContribution`s. Recursive directory listings are memoized per plan by
   `ResolveContext`, so a whole-tree walk happens at most once.
4. **Index + fixpoint** — contributions are grouped into a `TargetIndex`, then extended with hypothetical transform
   destinations until nothing new appears.
5. **Build** — for each target (sorted, deterministic) `TaskBuilder` runs the policies (update mode, task kind, root,
   task config), the composers (description, context message)
   and `RelatedFileCollector`, producing `Planned` / `Skipped` / `Failed`. Targets outside
   `config.root` are skipped. Exceptions are captured, never thrown.
6. **Partition** — `QueuePartitioner` starts a new queue whenever a task touches a target or related file already
   claimed by the current queue. Queues run **concurrently**; tasks inside a queue run **sequentially**.
7. **Sort** — `DependencySorter` runs Kahn's algorithm over *"my `related_files` contain your
   `main_file`"*. It is index-based (value-equal tasks cannot collapse) and deterministic; cycles are broken by
   fewest-unsatisfied-deps then target order, with a warning.

The result is an immutable `WorkPlan<K>`:

```kt
 data class WorkPlan<K : DocTaskKind>(
  val queues: List<TaskQueue<K>>,
  val skipped: List<BuildOutcome.Skipped<K>>,
  val failed: List<BuildOutcome.Failed<K>>,
)
```

---

## Execution

`DocTaskRunner` owns scheduling, cancellation, timeouts, preparation side effects and every status transition. For each
queue it opens **one** `DocExecutionContext` (`use`d, so `close()`
always runs) and for each task:

1. Rebase the task if `folder:` moved the effective root.
2. `ctx.reset()`.
3. Check the cancel flag → `CANCELLED` + `CancellationException`.
4. Apply `TargetPreparation` (delete the target if requested).
5. Mark `RUNNING`, build the execution config via `ExecutionConfigFactory`, call `ctx.execute`.

`ExecutionConfigFactory` produces the JSON-compatible `executionConfig`:

* for `isFileTask` / `isTemplateTask` kinds, **declaratively** — `task_type`, the serialized
  `ModificationTaskConfig`, an `.erb` `template_file` for template kinds, plus
  `task_config_json` overrides;
* otherwise by delegating to `ctx.inferTaskConfig(...)` with a synthesized history (task type, description, output
  files, fenced related-file contents, the task message), then merging overrides;
* finally normalizing `related_files` to `List<String>` and setting `task_description` to the description plus the first
  doc's body.

Callbacks (`DocTaskCallbacks<S>`) are how the host reports progress. `onSessionStarted` may throw
`CancellationException` to abort — the runner honours it and marks the rest of the queue
`CANCELLED`. Any other throwable marks the remaining queue tasks `FAILED` with
`"Queue aborted: ..."`.

Timeouts: per task `config.taskTimeoutMinutes` (passed to the host in `DocTaskRequest`), overall
`config.overallTimeoutMinutes` (enforced by the runner, which then marks all `RUNNING`
tasks `FAILED` and rethrows).

---

## Status tracking

```kt
interface DocStatusStore {
  fun initialize(targetKeys: Collection<String>)
  fun set(targetKey: String, status: TaskStatus, sessionId: String? = null, error: String? = null): TaskStatusEntry
  fun markAllRunningAs(status: TaskStatus, error: String? = null)
  fun read(): DocOpsStatus
}
```

`TaskStatus` is `PENDING → RUNNING → COMPLETED | FAILED | CANCELLED`
(`isTerminal` covers the last three). Target keys are paths relative to `config.root`.

* `JsonFileDocStatusStore` (default) writes `<root>/docops.status.json` under a **process-wide** lock, so concurrent
  queues cannot corrupt it. `initialize` preserves entries from previous runs.
* `InMemoryDocStatusStore` has identical semantics without touching disk — use it in tests, optionally with a fixed
  `Clock`.

```json
{
  "lastUpdated": "2024-05-01T12:00:00Z",
  "tasks": {
    "src/main/kotlin/billing/Invoice.kt": {
      "target": "src/main/kotlin/billing/Invoice.kt",
      "status": "COMPLETED",
      "sessionId": "s-123",
      "startedAt": "2024-05-01T11:59:10Z",
      "completedAt": "2024-05-01T12:00:00Z"
    }
  }
}
```

---

## URL context and caching

`related:` entries beginning with `http://` / `https://` are fetched and cached on disk by
`UrlCache` (default `<root>/.doc-processor-cache/url-cache`, TTL 1 hour):

* entries are `<sha256[0..16]>_<sanitized-name>` plus a `.meta` sidecar (`url`, `fetched`, `content-type`, `etag`,
  `last-modified`);
* conditional requests use `If-None-Match` / `If-Modified-Since`; `304` just refreshes the timestamp;
* HTML is reduced with `HtmlSimplifier` (falling back to crude tag stripping);
* on fetch failure a **stale** cache entry is served if one exists, otherwise `null`.

All network access goes through one seam:

```kt
fun interface HttpFetcher {
  fun fetch(request: HttpFetchRequest): HttpFetchResponse
}
```

---

## Extension points

| Seam                | Interface                                                                                                                   | Replace it to…                                               |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Platform bindings   | `DocOpsHost`                                                                                                                | run on a different agent/thread-pool stack                   |
| Task semantics      | `DocTaskKind`, `DocTaskKindResolver`                                                                                        | add new kinds of work                                        |
| Document syntax     | `DocSpecLoader`                                                                                                             | support a different metadata format                          |
| Path/URL resolution | `ResourceResolver`, `CompositeResourceResolver`                                                                             | add e.g. a `git://` or classpath resolver                    |
| Networking          | `HttpFetcher`                                                                                                               | proxying, offline mode, fakes                                |
| Target discovery    | `TargetResolver`                                                                                                            | add a new frontmatter key (pass `resolvers` to `DocPlanner`) |
| Policies/composers  | `UpdateModePolicy`, `TaskKindPolicy`, `RootPolicy`, `TaskConfigPolicy`, `TaskDescriptionComposer`, `ContextMessageComposer` | change precedence or prompt wording                          |
| Scheduling          | `QueuePartitioner`, `DependencySorter`, `DocTaskScheduler`                                                                  | different concurrency strategy                               |
| Update semantics    | `UpdateMode`                                                                                                                | custom skip/patch/overwrite rules                            |
| Status              | `DocStatusStore`                                                                                                            | report to a DB or UI instead of JSON                         |

Adding a resolver:

```kt
object TodoTargetResolver : TargetResolver {
  override fun contributions(specs: List<DocSpec>, ctx: ResolveContext) =
    specs.mapNotNull { spec ->
      (spec.frontmatter["todo"] as? String)?.let {
        TargetContribution(TargetPath.of(spec.baseDir.resolve(it)), spec, ContributionKind.GENERATE)
      }
    }
}

val planner = DocPlanner(
  taskBuilder = DocOps.defaultTaskBuilder(config, host.taskKinds),
  resolvers = defaultTargetResolvers + TodoTargetResolver,
  maxDepth = config.maxPlanningDepth,
)
val docOps = DocOps(config, host, planner = planner)
```

---

## Testing

Nothing in the planning path needs a network, an agent, or a real status file.

```kt
val status = InMemoryDocStatusStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

val docOps = DocOps(
  config = DocOpsConfig(root = tmp, docsFolder = tmp),
  host = FakeHost(),
  statusStore = status,
  resources = CompositeResourceResolver(
    listOf(
      UrlResourceResolver(UrlCache(tmp.resolve("cache"), fetcher = { req ->
        HttpFetchResponse(200, "<html>hi</html>", "text/html")
      })),
      FileResourceResolver(lister = { listOf(/* fixed listing */) }),
    )
  ),
)

val plan = docOps.plan()
assertEquals(listOf("a.kt", "b.kt"), plan.tasks.map { it.target.name })
// plan() touched nothing on disk
```

Handy pure units to test directly: `FrontmatterParser`, `TemplateEngine`, `MarkdownLinks`,
`TransformExpander.applyBackreferences`, `GlobExpander`, `QueuePartitioner`,
`DependencySorter`, `TargetIndex`, `TargetPath`.

---

## Package layout

| Package              | Contents                                                                                                                                                           |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `docops`             | `DocOps` (entry point), `DocOpsConfig`, `DocOpsHost`, `UpdateModes`                                                                                                |
| `docops.spec`        | `FrontmatterParser`, `TemplateEngine`, `MarkdownLinks`, `Frontmatter`, `DocSpecLoader`                                                                             |
| `docops.model`       | `DocSpec`, `TransformSpec`, `GenerateSpec`, `TargetPath`, `TargetContribution`, `ModificationTask(Config)`, `PlannedTask`, `BuildOutcome`, `TaskQueue`, `WorkPlan` |
| `docops.resolve`     | `GlobExpander`, `TransformExpander`, `ResourceResolver`s, `UrlCache`, `HttpFetcher`                                                                                |
| `docops.plan`        | `DocPlanner`, `TargetResolver`s, `TargetIndex`, `ResolveContext`, `TaskBuilder`, `RelatedFileCollector`                                                            |
| `docops.plan.policy` | `UpdateModePolicy`, `TaskKindPolicy`, `RootPolicy`, `TaskConfigPolicy`, composers                                                                                  |
| `docops.schedule`    | `QueuePartitioner`, `DependencySorter`                                                                                                                             |
| `docops.exec`        | `DocTaskKind`, `DocTaskScheduler`, `DocTaskRequest`, `DocExecutionContext`, `DocTaskRunner`, `ExecutionConfigFactory`                                              |
| `docops.status`      | `TaskStatus`, `DocStatusStore`, `JsonFileDocStatusStore`, `InMemoryDocStatusStore`                                                                                 |

---
