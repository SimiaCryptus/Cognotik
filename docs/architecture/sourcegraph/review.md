Now I have enough to give a thorough critical review.

---

## Critical Review — Branch `2.1.19`, Today's Commits

---

### `DocProcessorBase.kt` — The Main Event

This is a 1,776-line God class and deserves the most scrutiny.

**Structural problems:**

**1. God class — it does everything.**
Planning, task resolution, URL fetching, glob expansion, frontmatter parsing, YAML rendering, template substitution, dependency sorting, queue partitioning, execution orchestration, and status tracking all live in one class. The abstract generics `<K, S, P>` give the appearance of separation but the actual logic is monolithic. This will be painful to test, maintain, and extend.

**2. `separateQueues` logic is broken.**
[Lines 846–890](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L846-L890) — the algorithm is wrong. It puts a task into a new singleton queue if its target or any related file appears in `processedFiles`. But it then *also* adds all those files to `processedFiles`, which means subsequent independent tasks can be incorrectly forced into their own singleton queues. The "conflict detection" and the "tracking" are collapsed into the same set, making the partitioning overly conservative and potentially serializing everything.

**3. `transformMatches` and `generateMatches` compute the same expansion twice.**
[Lines 710–736](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L710-L736) — `expandTransformPattern` is called once to group by normalized key, then called *again* just to build the re-keying map. This is O(2n) file-system traversals on every planning call.

**4. `parseFrontmatter` is a hand-rolled YAML parser.**
[Lines 1676–1706](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L1676-L1706) — it only handles scalar values and flat lists. It silently drops nested maps, multi-line scalars, quoted strings, YAML anchors, and anything else standard YAML can contain. There is already a `JsonUtil` in the project. A proper YAML library (`snakeyaml` is a 1-line dependency) should replace this. Any doc with a non-trivial frontmatter will be silently misread.

**5. URL cache TTL is hardcoded to 1 hour.**
[Line 164](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L164) — `3600_000` ms is not configurable. The `HttpClient` is also created fresh per URL fetch ([line 171](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L171)), which defeats connection pooling.

**6. `ModificationTask.patchProcessor` is typed as `Any?`.**
[Line 342](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L342) — the erasure is recovered via an unchecked cast at [line 367](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L367). Since `ModificationTask` is already generic on `K`, it could carry `P` too and eliminate the cast entirely. This is a type-safety hole that the generics were supposed to prevent.

**7. `run()` throws exceptions that escape `runAll()`'s queue.**
[Lines 1026–1033](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L1026-L1033) — a `Throwable` from a single task is re-thrown, which bubbles into the `CompletableFuture` for the entire queue and causes `allOf(...).get()` to throw `ExecutionException`, aborting *all remaining tasks in that queue*. The catch block in `runAll` at [line 927](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L927) marks remaining tasks as FAILED, but this means a single flaky task kills the whole queue. This should be `continue` with error reporting, not rethrow.

**8. `executionConfig` reads related files into memory inline.**
[Lines 1075–1079](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L1075-L1079) — large related files are read into a `String` and embedded in a `List<String>` (the `history` list) with no size limit. This will blow up context windows or memory for large codebases.

**9. `buildModificationTask` has a dead variable.**
[Line 540](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocProcessorBase.kt?L540) — `val targetFile1 = File(targetFile)` is declared but `targetFile` (the `String` parameter) is already used as a `File` at line 504. `targetFile1` is then passed to `buildCombinedTaskDescription` at line 553 — this is a redundant allocation of the same object.

---

### `DocExecution.kt` — Clean

[This file](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/docops/DocExecution.kt) is well-designed. The interfaces are minimal, the separation of concerns is correct (`DocTaskKind`, `DocTaskKindResolver`, `DocTaskScheduler`, `DocExecutionContext`, `DocTaskCallbacks`), and the `AutoCloseable` on `DocExecutionContext` is appropriate. No issues here.

---

### Kotlin Grammar Updates (`KotlinLexer.g4` / `KotlinParser.g4`)

Two real issues beyond cosmetic reformatting:

**1. `FIELD` is now both a keyword token and a soft keyword in `simpleIdentifier`.**
`FIELD` is added as a new lexer token (the bare `field` keyword for explicit backing fields), but it is also listed in `simpleIdentifier` as a soft keyword. This creates an ambiguity: wherever the parser sees `field`, it must decide between the `FIELD` token in `explicitBackingField` and the `simpleIdentifier` path. ANTLR resolves this by precedence (first alternative wins), which may silently break any property named `field`.

**2. `functionModifierList` vs `modifierList` split is incomplete.**
`functionDeclaration` now uses `functionModifierList` ([KotlinParser.g4 line 148](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@2364d48/-/blob/antlr/src/main/antlr/KotlinParser.g4)) which adds `contextModifier` support, but `propertyDeclaration` still uses the original `modifierList` — so `context` parameters on properties are not supported, which is inconsistent with the Kotlin spec.

---

### Module Extraction (`text`, `diffpatch`)

The Gradle and IntelliJ files are updated but there are no actual source files yet in the new modules. This means the `2.1.19` branch is in a broken intermediate state — the IDE project references modules that have no content. This is the "wip" commit at HEAD in its purest form, and is fine for a feature branch, but should not merge to `main` as-is.

---

### Warning Suppressions

The `@Suppress("unused")` sweep is correct in intent but slightly sloppy: it's applied at the object/class level, suppressing all unused warnings for the entire class, rather than targeted at the specific JUnit lifecycle methods (`@BeforeAll`, etc.) that trigger the warning. The broader suppression will hide any genuinely unused members added in future.

---

### Summary Verdict

| Area | Verdict |
|------|---------|
| `DocExecution.kt` interfaces | Good |
| `DocProcessorBase` structure | Needs decomposition |
| `separateQueues` logic | Bug — overly serializes tasks |
| `transformMatches` double traversal | Performance issue |
| Hand-rolled YAML parser | Should be replaced |
| Single-task failure kills whole queue | Bug |
| `patchProcessor: Any?` erasure | Type-safety gap |
| URL `HttpClient` per-request | Resource inefficiency |
| Kotlin grammar `FIELD` ambiguity | Correctness risk |
| `context` modifier on properties | Incomplete implementation |
| New modules have no source files | WIP — not merge-ready |
