# DocOps Refactoring Plan — Dismantling the `DocProcessorBase` God Object

Status: proposed Owner: docops Scope: `core/src/main/kotlin/com/simiacryptus/cognotik/docops/**` plus host adapters
(IntelliJ plugin / web app) that subclass `DocProcessorBase`.

---

## 1. Current State

### 1.1 What `DocProcessorBase` does today

`DocProcessorBase<K, S, P>` is ~1000 lines and owns *at least* nine unrelated responsibilities:

| # | Responsibility                          | Representative members                                                                                                                                                                                                |
|---|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Markdown/YAML frontmatter parsing       | `parseMarkdownWithFrontmatter`, `parseFrontmatter`, `parseSpecifies`, `parseDocuments`, `parseTransforms`, `parseGenerates`, `parseRelated`, `parseTaskType`, `parseTaskConfigJson`, `parseUpdateMode`, `parseFolder` |
| 2 | Template variable handling              | `parseTemplateVars`, `applyTemplateSubstitutions`, `renderFrontmatterToYaml`, `listTemplateVarKeys`, `TEMPLATE_VAR_KEYS`                                                                                              |
| 3 | Path/glob/regex expansion               | `isGlobPattern`, `expandPatternOrLiteral`, `expandSimpleGlob`, `expandRecursiveGlob`, `expandTransformPattern`, `applyBackreferences`, `normalizePath`                                                                |
| 4 | Network I/O + caching                   | `isUrl`, `fetchAndCacheUrl`, `resolveRelatedResource(s)`, `urlCacheDir`                                                                                                                                               |
| 5 | Target discovery / matching             | `fileToSpecs`, `transformMatches`, `documentMatches`, `generateMatches`, `discoverTransitiveTargets`, `modificationTasksRecursive`                                                                                    |
| 6 | Task construction & policy resolution   | `buildModificationTask`, `resolveTaskType`, `resolveUpdateMode`, `resolveEffectiveRoot`, `resolveTaskConfigJson`, `primarySource`, `allRelatedFiles`, `buildCombinedTaskDescription`, the inline `message` lambda     |
| 7 | Work partitioning & dependency ordering | `separateQueues`, `sortByDependencies`                                                                                                                                                                                |
| 8 | Execution orchestration                 | `run()`, `runAll()`, `executionConfig()`, timeouts, cancellation                                                                                                                                                      |
| 9 | Status persistence                      | inherited from `DocStatus` — `initializeStatus`, `updateTaskStatus`, `markRunningTasksAs`, `targetKeyOf`                                                                                                              |

It also carries the entire domain model (`DocSpec`, `TransformSpec`,
`GenerateSpec`, `TransformMatch`, `GenerateMatch`, `DocumentMatch`,
`ModificationTask`, `ModificationTaskConfig`, `DocumentMatch`) as nested types, which forces every consumer to depend on
the god object just to name a data class.

### 1.2 Concrete symptoms

* **Untestable planning.** You cannot test target discovery without subclassing an abstract class that demands a
  scheduler, an execution context, and a task-kind resolver. `fetchAndCacheUrl` does real HTTP with no seam.
* **Inheritance used for reuse.** `DocProcessorBase : DocStatus(root)` leaks
  `protected` status internals into the planner and prevents an in-memory status store for tests.
* **Three generic parameters** (`K`, `S`, `P`) leak into every signature. `P` is erased anyway —
  `ModificationTask.patchProcessor: Any?` plus an unchecked
  `patchProcessorAs()` cast. Meanwhile `UpdateModes` already returns a concrete
  `com.simiacryptus.cognotik.text.patch.PatchProcessor`, so `P` buys nothing.
* **Impure planning.** `buildModificationTask` *deletes files on disk*
  (`targetFileObj.delete()`) while it is supposed to be building a plan. A dry-run / preview mode is impossible.
* **Redundant, expensive work.** `expandTransformPattern` walks the whole tree and is called from `transformMatches`
  (twice, once for the "re-key" pass), from `fileToSpecs`, and again from `discoverTransitiveTargets`. Recursive
  planning re-runs *all* discovery at every depth and then filters by relative path, which is both O (depth × tree) and
  duplicate-prone.
* **Case-normalization by string smearing.** `normalizePath` + `groupBy` +
  "re-key with original path" appears four times, each slightly different.
* **Unsafe casts.** `separateQueues` does `queues.last() as MutableList<...>`.
* **Silent failure.** `buildModificationTask` catches `Exception`, logs, and returns `null` — the target silently
  disappears from the plan instead of being recorded as `FAILED`.
* **Misc bugs.** stray `return` inside `markRunningTasksAs`'s `synchronized`
  block; unused `targetFile1`/`source` duplication; `executionConfig` resolves
  `doc_files` (absolute) against `root`; URL cache TTL hard-coded to 1 hour with no ETag/`If-Modified-Since`.

---

## 2. Target Architecture

### 2.1 Principles

1. **Pipeline, not god object.** `load → resolve targets → build tasks →
schedule → execute`, with an explicit, immutable value type between stages.
2. **Pure planning.** Everything up to and including `WorkPlan` is free of mutation of the workspace. Side effects
   (deleting a target, writing status)
   happen only in the execution stage, driven by data (`TargetPreparation`).
3. **Composition over inheritance.** No abstract base for hosts; a `DocOpsHost`
   interface supplies the platform bindings.
4. **Seams for I/O.** `HttpFetcher`, `Clock`, `FileSystem`-ish resolvers and
   `DocStatusStore` are interfaces so tests never touch the network and rarely touch disk.
5. **Two generics, not three.** Keep `K : DocTaskKind` and `S` (session handle). Delete `P` and use `PatchProcessor`
   directly — `core` already depends on it.

### 2.2 Package layout

```
 docops/
   DocOps.kt                      facade: plan() / run()
   DocOpsConfig.kt                root, docsFolder, updateMode, timeouts, overrides
   DocOpsHost.kt                  taskKinds + newScheduler + newExecutionContext
   model/
     DocSpec.kt                   DocSpec, TransformSpec, GenerateSpec
     TargetPath.kt                canonical path + case-normalized key
     TargetContribution.kt        contribution kinds + payload
     ModificationTask.kt          ModificationTask, ModificationTaskConfig
     PlannedTask.kt               ModificationTask + TargetPreparation
     WorkPlan.kt                  List<TaskQueue>
   spec/
     Frontmatter.kt               typed accessors over Map<String, Any>
     FrontmatterParser.kt         text -> Frontmatter (pure)
     TemplateEngine.kt            {{var}} substitution + var extraction
     DocSpecLoader.kt             interface + MarkdownDocSpecLoader
   resolve/
     GlobExpander.kt              literal/simple/recursive glob expansion
     TransformExpander.kt         regex source pattern + $1/$1+1 backrefs
     ResourceResolver.kt          interface
     FileResourceResolver.kt
     UrlResourceResolver.kt       uses UrlCache
     UrlCache.kt                  ttl, hashing, metadata
     HttpFetcher.kt               interface + JdkHttpFetcher
   plan/
     TargetResolver.kt            interface
     SpecifiesTargetResolver.kt
     TransformTargetResolver.kt
     DocumentTargetResolver.kt
     GenerateTargetResolver.kt
     FolderTargetResolver.kt
     TargetIndex.kt               target -> contributions
     DocPlanner.kt                fixpoint expansion, produces WorkPlan
     TaskBuilder.kt               contributions -> PlannedTask
     policy/
       UpdateModePolicy.kt
       TaskKindPolicy.kt
       RootPolicy.kt
       TaskConfigPolicy.kt
       TaskDescriptionComposer.kt
       ContextMessageComposer.kt
   schedule/
     QueuePartitioner.kt
     DependencySorter.kt
   exec/
     DocExecution.kt              (existing interfaces, minus P)
     ExecutionConfigFactory.kt    executionConfig() extracted
     DocTaskRunner.kt             runAll/run, cancellation, timeouts
   status/
     DocStatusStore.kt            interface
     JsonFileDocStatusStore.kt    current docops.status.json behaviour
     InMemoryDocStatusStore.kt    tests
   UpdateModes.kt                 (unchanged)
```

### 2.3 Dataflow

```
 docsFolder ──► DocSpecLoader ──► List<DocSpec>
                                     │
                                     ▼
                  ┌───────── TargetResolver × 5 ─────────┐
                  │ specifies / transforms / documents / │
                  │ generates / folder                   │
                  └──────────────┬───────────────────────┘
                                 ▼
                         List<TargetContribution>
                                 ▼
                            TargetIndex  ◄── fixpoint loop (DocPlanner)
                                 ▼
                    TaskBuilder + policies + composers
                                 ▼
                         List<PlannedTask<K>>
                                 ▼
             QueuePartitioner ─► DependencySorter ─► WorkPlan
                                 ▼
   DocTaskRunner ─► DocExecutionContext ─► host agent   (+ DocStatusStore)
```

### 2.4 Key contracts (sketches)

```kotlin
 // model/TargetPath.kt — kills every normalizePath()/re-key hack.
@JvmInline
value class TargetPath private constructor(val file: File) {
  val key: String get() = file.path.lowercase()

  companion object {
    fun of(f: File): TargetPath = TargetPath(runCatching { f.canonicalFile }.getOrDefault(f.absoluteFile))
  }
}

// model/TargetContribution.kt
enum class ContributionKind { SPECIFIES, TRANSFORM, DOCUMENT, GENERATE, FOLDER }

data class TargetContribution(
  val target: TargetPath,
  val spec: DocSpec,
  val kind: ContributionKind,
  /** transform source file / generate inputs / documented sources. */
  val sourceFiles: List<File> = emptyList(),
)

// plan/TargetResolver.kt
fun interface TargetResolver {
  fun contributions(specs: List<DocSpec>, ctx: ResolveContext): List<TargetContribution>
}

// plan/TaskBuilder.kt
data class TargetPreparation(val deleteTargetBeforeRun: Boolean = false)

data class PlannedTask<K : DocTaskKind>(
  val task: ModificationTask<K>,
  val preparation: TargetPreparation,
)

class TaskBuilder<K : DocTaskKind>(
  private val updateModePolicy: UpdateModePolicy,
  private val taskKindPolicy: TaskKindPolicy<K>,
  private val rootPolicy: RootPolicy,
  private val taskConfigPolicy: TaskConfigPolicy,
  private val description: TaskDescriptionComposer<K>,
  private val message: ContextMessageComposer<K>,
  private val related: RelatedFileCollector,
) {
  fun build(target: TargetPath, contributions: List<TargetContribution>): BuildOutcome<K>
}

sealed interface BuildOutcome<out K : DocTaskKind> {
  data class Planned<K : DocTaskKind>(val task: PlannedTask<K>) : BuildOutcome<K>
  data class Skipped(val target: TargetPath, val reason: String) : BuildOutcome<Nothing>
  data class Failed(val target: TargetPath, val error: Throwable) : BuildOutcome<Nothing>
}

// status/DocStatusStore.kt — composition, not inheritance.
interface DocStatusStore {
  fun initialize(targetKeys: Collection<String>)
  fun set(targetKey: String, status: TaskStatus, sessionId: String? = null, error: String? = null)
  fun markAllRunningAs(status: TaskStatus, error: String? = null)
  fun read(): DocOpsStatus
}

// DocOpsHost.kt — replaces the abstract methods on DocProcessorBase.
interface DocOpsHost<K : DocTaskKind, S : Any> {
  val taskKinds: DocTaskKindResolver<K>
  fun newScheduler(): DocTaskScheduler
  fun newExecutionContext(): DocExecutionContext<K, S>
}

// DocOps.kt — the only public entry point.
class DocOps<K : DocTaskKind, S : Any>(
  private val config: DocOpsConfig,
  private val host: DocOpsHost<K, S>,
  private val loader: DocSpecLoader = MarkdownDocSpecLoader(config),
  private val planner: DocPlanner<K> = DocPlanner.default(config, host.taskKinds),
  private val runner: DocTaskRunner<K, S> = DocTaskRunner(config, host, statusStore),
  private val statusStore: DocStatusStore = JsonFileDocStatusStore(config.root),
) {
  fun plan(files: Iterable<File> = config.markdownFiles()): WorkPlan<K>  // pure
  fun run(
    plan: WorkPlan<K> = plan(), cancel: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (S) -> Unit = {}
  ): List<S>
}
```

### 2.5 Behavioural changes introduced deliberately

* `plan()` performs **no** destructive filesystem writes. Target deletion moves to
  `DocTaskRunner` immediately before the task is dispatched, driven by
  `TargetPreparation.deleteTargetBeforeRun`.
* Transitive/recursive discovery becomes a **fixpoint loop over `TargetIndex`**
  (add hypothetical targets, re-run only the resolvers that can match them, stop when no new targets appear or
  `maxDepth` is hit) instead of re-running the whole pipeline and de-duplicating afterwards.
* `expandTransformPattern` runs **once** per (spec, transform) per plan, behind a memoized directory listing in
  `ResolveContext`.
* Build failures surface as `BuildOutcome.Failed` and are recorded in the status file as `FAILED` instead of vanishing.

---

## 3. Member Migration Map

| Current member (`DocProcessorBase`)                                                                 | New home                                                                                 |
|-----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `parseMarkdownWithFrontmatter`, `parseFrontmatter`                                                  | `spec.FrontmatterParser`, `spec.MarkdownDocSpecLoader`                                   |
| `parseSpecifies/Documents/Transforms/Generates/Related/TaskType/TaskConfigJson/UpdateMode/Folder`   | `spec.Frontmatter` typed accessors                                                       |
| `parseTemplateVars`, `applyTemplateSubstitutions`, `renderFrontmatterToYaml`, `listTemplateVarKeys` | `spec.TemplateEngine`                                                                    |
| `extractPathFromMarkdownLink(s)`, `MARKDOWN_LINK_REGEX`                                             | `spec.MarkdownLinks`                                                                     |
| `isGlobPattern`, `expandPatternOrLiteral`, `expandSimpleGlob`, `expandRecursiveGlob`                | `resolve.GlobExpander`                                                                   |
| `expandTransformPattern`, `applyBackreferences`                                                     | `resolve.TransformExpander`                                                              |
| `isUrl`, `fetchAndCacheUrl`, `urlCacheDir`                                                          | `resolve.UrlResourceResolver` + `resolve.UrlCache` + `resolve.HttpFetcher`               |
| `resolveRelatedResource(s)`                                                                         | `resolve.CompositeResourceResolver`                                                      |
| `fileToSpecs`                                                                                       | `plan.SpecifiesTargetResolver` + `plan.FolderTargetResolver` (+ transform contributions) |
| `transformMatches`                                                                                  | `plan.TransformTargetResolver`                                                           |
| `documentMatches`                                                                                   | `plan.DocumentTargetResolver`                                                            |
| `generateMatches`                                                                                   | `plan.GenerateTargetResolver`                                                            |
| `normalizePath`, all "re-key with original path" blocks                                             | `model.TargetPath`                                                                       |
| `modificationTasks`, `modificationTasksRecursive`, `discoverTransitiveTargets`                      | `plan.DocPlanner`                                                                        |
| `buildModificationTask`                                                                             | `plan.TaskBuilder`                                                                       |
| `resolveUpdateMode` / `resolveTaskType` / `resolveEffectiveRoot` / `resolveTaskConfigJson`          | `plan.policy.*`                                                                          |
| `primarySource`, `allRelatedFiles`                                                                  | `plan.RelatedFileCollector`                                                              |
| `buildCombinedTaskDescription`                                                                      | `plan.policy.TaskDescriptionComposer`                                                    |
| inline `message = { root -> ... }` lambda                                                           | `plan.policy.ContextMessageComposer`                                                     |
| `separateQueues`                                                                                    | `schedule.QueuePartitioner`                                                              |
| `sortByDependencies`                                                                                | `schedule.DependencySorter`                                                              |
| `executionConfig`                                                                                   | `exec.ExecutionConfigFactory`                                                            |
| `run`, `runAll`                                                                                     | `exec.DocTaskRunner`                                                                     |
| `initializeStatus`, `updateTaskStatus`, `markRunningTasksAs`, `targetKeyOf`, `DocStatus`            | `status.DocStatusStore` / `JsonFileDocStatusStore`                                       |
| `getAll`, `run()`                                                                                   | `DocOps.plan()` / `DocOps.run()`                                                         |
| `taskKinds`, `newScheduler`, `newExecutionContext`, timeouts                                        | `DocOpsHost` / `DocOpsConfig`                                                            |

---

## 4. Refactoring Plan

Each phase must leave the repository compiling and green. Phases 1–4 are pure extraction with delegating shims, so they
can land independently and be reverted cheaply. Phase 9 is the only breaking change for hosts.

### Phase 0 — Safety net (prerequisite)

* Add `core/src/test/.../docops/` fixtures: a temp-dir workspace builder that writes markdown docs + source files.
* Add **characterization tests** that snapshot the current plan:
  serialize `modificationTasks(...)` to canonical JSON (sorted by target, paths relativized to root) and assert against
  golden files. Cover:
  `specifies` (literal, simple glob, `**` glob), `transforms` (incl. `$1+1`
  backrefs), `documents`, `generates`, `folder`, `related` with URLs,
  `update_mode` per-doc override, `task_type`, `task_config_json`, template vars.
* Add tests for `sortByDependencies` (chains, cycles) and `separateQueues`.
* **Freeze**: no behaviour change until goldens exist.

### Phase 1 — Extract the pure statics out of the companion

* Create `spec.FrontmatterParser`, `spec.TemplateEngine`, `spec.MarkdownLinks`,
  `resolve.GlobExpander`, `resolve.TransformExpander` as `object`s with the code moved verbatim.
* Keep the `DocProcessorBase.Companion` functions as `@Deprecated`
  one-line delegates so nothing else breaks.
* Done when: companion contains only delegates; new objects have direct unit tests (backreference arithmetic, glob edge
  cases, YAML list parsing,
  `{{var}}` substitution incl. `$` escaping).

### Phase 2 — Introduce `TargetPath`

* Add `model.TargetPath`; change the four `Map<String, List<...>>` return types to
  `Map<TargetPath, List<...>>`.
* Delete `normalizePath` and every "re-key with original path" block (≈60 lines).
* Done when: goldens unchanged; no `lowercase()` on paths outside `TargetPath`.

### Phase 3 — Extract resource resolution and the URL cache

* `HttpFetcher` interface + `JdkHttpFetcher` (current `HttpClient` code);
  `UrlCache(dir, ttl, clock)`; `UrlResourceResolver`; `FileResourceResolver`;
  `CompositeResourceResolver`.
* Inject via `DocOpsConfig`/constructor with defaults; `DocProcessorBase`
  delegates.
* Add TTL/ETag support and make TTL configurable (`DocOpsConfig.urlCacheTtl`).
* Done when: a `FakeHttpFetcher` test proves related-URL resolution without network, and cache hit/miss/expiry are
  unit-tested with a fake clock.

### Phase 4 — Extract `DocSpecLoader`

* Move `parseMarkdownWithFrontmatter` and all `parseX(frontmatter)` helpers into
  `MarkdownDocSpecLoader` + `Frontmatter`.
* Move `DocSpec`/`TransformSpec`/`GenerateSpec` to `model/` as top-level types; add `typealias`es inside
  `DocProcessorBase` for source compatibility.
* Done when: loader is constructible standalone and tested against markdown fixtures.

### Phase 5 — Extract target resolvers and make planning pure

* Introduce `TargetContribution`, `ResolveContext` (memoized recursive file listing + resolvers), `TargetIndex`, and the
  five `TargetResolver`s.
* Rewrite `modificationTasksRecursive` as `DocPlanner`'s fixpoint loop.
* **Move the `targetFileObj.delete()` side effect** into `TargetPreparation`, consumed later by the runner.
* Done when: goldens unchanged *except* that no files are deleted during planning (add an explicit test asserting a
  pre-existing target survives
  `plan()` under `OverwriteExisting` and is deleted by `run()`).

### Phase 6 — Extract policies, composers and `TaskBuilder`

* `UpdateModePolicy`, `TaskKindPolicy`, `RootPolicy`, `TaskConfigPolicy`,
  `TaskDescriptionComposer`, `ContextMessageComposer`, `RelatedFileCollector`.
* `TaskBuilder.build` returns `BuildOutcome` (`Planned` / `Skipped` / `Failed`)
  instead of nullable + swallowed exception.
* Done when: `TaskBuilder` is unit-tested with hand-built contribution lists and no filesystem beyond a temp dir; each
  policy has precedence tests (spec > transform > document > generate; per-doc > global).

### Phase 7 — Extract scheduling

* `QueuePartitioner` (rewritten without the `as MutableList` cast, using
  `TargetPath` sets) and `DependencySorter` (Kahn + deterministic cycle break by stable target ordering rather than
  iteration order).
* Produce `WorkPlan(queues: List<TaskQueue<K>>)`.
* Done when: partitioning/ordering tests are deterministic across runs (add a shuffled-input test asserting a stable
  output order).

### Phase 8 — Extract status and execution; drop generic `P`

* `DocStatusStore` interface; `JsonFileDocStatusStore` absorbs `DocStatus`
  (which becomes internal/deleted); `InMemoryDocStatusStore` for tests. Fix the stray `return` in `markAllRunningAs`.
* `ExecutionConfigFactory` takes `PlannedTask` + `DocExecutionContext` and returns the config map. Fix `doc_files`
  resolution (they are absolute; don't
  `root.resolve`).
* `DocTaskRunner` owns scheduler submission, cancellation, timeouts, preparation side effects, and status transitions.
* Replace `P` with `com.simiacryptus.cognotik.text.patch.PatchProcessor` in
  `DocTaskRequest`, `DocTaskInferenceRequest`, `DocExecutionContext`,
  `ModificationTask`; delete `patchProcessorAs()` and the unchecked cast.
* Done when: a fake `DocExecutionContext` + `InMemoryDocStatusStore` drives an end-to-end `run()` test asserting
  PENDING→RUNNING→COMPLETED, cancellation → CANCELLED, and timeout → FAILED.

### Phase 9 — Facade and host migration (breaking)

* Add `DocOps` + `DocOpsHost` + `DocOpsConfig`.
* Convert each existing subclass of `DocProcessorBase` into a `DocOpsHost`
  implementation (they only ever implemented `taskKinds`, `newScheduler`,
  `newExecutionContext`, and the timeout overrides).
* Mark `DocProcessorBase` `@Deprecated(level = WARNING)` as a thin adapter that constructs a `DocOps` internally; keep
  for one release.
* Done when: no production code references `DocProcessorBase` except the adapter.

### Phase 10 — Cleanup

* Delete `DocProcessorBase`, the companion delegates and the `typealias` shims.
* Delete `DocStatus` inheritance points.
* Update module docs; add an architecture note pointing at this file.

---

## 5. Bug Fix Checklist (tracked with the phases)

| Bug                                                                             | Phase |
|---------------------------------------------------------------------------------|-------|
| Planning deletes target files                                                   | 5     |
| Transform expansion recomputed 3–4× (full tree walk each time)                  | 5     |
| Recursive planner re-plans everything per depth and filters by string paths     | 5     |
| `queues.last() as MutableList` unchecked cast                                   | 7     |
| `markRunningTasksAs` stray `return` inside `synchronized`                       | 8     |
| Swallowed exception in `buildModificationTask` hides failed targets             | 6     |
| `executionConfig` resolves absolute `doc_files` against `root`                  | 8     |
| Unchecked `patchProcessor as? P`                                                | 8     |
| URL cache: hard-coded 1h TTL, no ETag, no configurability, non-injectable clock | 3     |
| Dead locals (`targetFile1`, duplicated `source`/`effectiveSource`)              | 6     |
| `DocStatus` inheritance leaking `protected` API into the planner                | 8     |

---

## 6. Testing Strategy

* **Golden plan tests** (Phase 0) guard every extraction phase; they are the contract. Any intentional change updates
  goldens in the same commit with a rationale in the message.
* **Unit tests per component**, with no shared base class:
  `FrontmatterParserTest`, `TemplateEngineTest`, `GlobExpanderTest`,
  `TransformExpanderTest` (property test for `$n±k` arithmetic),
  `UrlCacheTest` (fake clock + fake fetcher), `TargetIndexTest`,
  `DocPlannerFixpointTest` (incl. circular transform → depth cap),
  `TaskBuilderTest`, policy precedence tests, `QueuePartitionerTest`,
  `DependencySorterTest` (chain, diamond, cycle, shuffle-stability),
  `DocTaskRunnerTest` (fake context: success/failure/cancel/timeout),
  `JsonFileDocStatusStoreTest` (concurrency: N threads × M updates, no lost writes).
* **No network, no wall clock** in `core` tests; inject `HttpFetcher` and `Clock`.

## 7. Acceptance Criteria

1. No class in `docops` exceeds ~250 lines or has more than one of the nine responsibilities listed in §1.1.
2. `DocOps.plan()` is side-effect free apart from URL cache reads/writes, and there is a test proving it.
3. Zero `@Suppress("UNCHECKED_CAST")` in `docops`.
4. Generic parameters reduced from three (`K, S, P`) to two (`K, S`).
5. Planning package line coverage ≥ 80%; every public type in `plan/`,
   `spec/`, `resolve/`, `schedule/` has at least one direct unit test.
6. Whole-tree file listing occurs at most once per `plan()` invocation.
7. Host adapters implement `DocOpsHost` (≤ 40 lines each) and subclass nothing.

## 8. Risks & Mitigations

| Risk                                                      | Mitigation                                                                                     |
|-----------------------------------------------------------|------------------------------------------------------------------------------------------------|
| Silent behaviour drift during extraction                  | Golden plan tests land first (Phase 0) and run in CI on every phase                            |
| Downstream hosts break on the `P` removal                 | Phase 8 is mechanical; `PatchProcessor` is already the only value ever passed                  |
| Long-lived refactor branch                                | Phases 1–8 are additive with delegating shims and merge independently                          |
| Deletion-timing change alters user-visible behaviour      | Only the *moment* of deletion moves (plan → just-before-run); documented and covered by a test |
| Case-insensitive-filesystem regressions from `TargetPath` | Dedicated tests for mixed-case targets on both case-sensitive and case-insensitive semantics   |

## 9. Open Questions

* Should `DocSpec` expose the raw `frontmatter` map at all, or only typed accessors? (Currently
  `buildCombinedTaskDescription` reads `frontmatter["prompt"]`
  directly — candidate for a `DocSpec.prompt: String?` field.)
* Should `WorkPlan` be serializable so a plan can be reviewed/approved before execution (a natural follow-up once
  planning is pure)?
* Does any host actually need `S` to be distinct from a session id string? If not,
  `S` can be dropped too, leaving a single generic parameter.