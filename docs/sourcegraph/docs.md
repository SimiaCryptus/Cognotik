# Cognotik — High-Level Architecture

> This document is derived entirely from the source code
> in [github.com/SimiaCryptus/Cognotik](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik),
> treating existing documentation as unreliable.

---

## Module Map

The repository is a multi-module Gradle build ([
`settings.gradle.kts`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/settings.gradle.kts)):

| Module                               | Role                                                                           |
|--------------------------------------|--------------------------------------------------------------------------------|
| `core`                               | Foundational agent model, platform services, diff/patch, interpreters          |
| `providers`                          | One concrete `ChatInterface` implementation per AI provider                    |
| `webui`                              | Jetty HTTP/WebSocket server, session management, planning orchestrator         |
| `tasklib`                            | Concrete task implementations (file, run, reasoning, online) + cognitive modes |
| `stdtools`                           | Shared utilities referenced by multiple modules                                |
| `intellij`                           | IntelliJ Platform plugin entry points                                          |
| `desktop`                            | Standalone desktop launcher (system tray, daemon)                              |
| `webapp`                             | React front-end (TypeScript/JS)                                                |
| `antlr`, `kotlin`, `groovy`, `scala` | Language-specific interpreter plugins                                          |
| `tool`, `experiment`                 | Scratch/tool-specific modules                                                  |

---

## Layer 1 — Core (`core/`)

The foundation everything else depends on.

**Agent hierarchy** ([
`agents/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/core/src/main/kotlin/com/simiacryptus/cognotik/agents))

[
`BaseAgent<I, R>`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/agents/BaseAgent.kt)
is a generic, typed abstract class parameterised by input and response type. It holds a `ChatInterface`, a prompt, and a
temperature, and requires subclasses to implement `chatMessages()` and `respond()`. Concrete subclasses in `core`
include `ChatAgent`, `CodeAgent`, `ParsedAgent`, `ImageGenerationAgent`, `AudioProcessingAgent`, and `ProxyAgent`.

**Platform services** ([
`platform/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/core/src/main/kotlin/com/simiacryptus/cognotik/platform))

`ApplicationServices` is a service-locator singleton that exposes `authenticationManager`, `authorizationManager`,
`dataStorageFactory`, and `threadPoolManager`. Auth and storage are accessed through `AuthenticationInterface`,
`AuthorizationInterface`, and `StorageInterface` — all interfaces, so implementations are swappable. The two platform
model classes are [
`Session`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/platform/model/Session.kt)
and [
`User`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/platform/model/User.kt).

**Other core capabilities**

- `diff/` — code patch generation and application utilities
- `interpreter/` — extensible interpreter framework (concrete impls in `antlr/`, `kotlin/`, `groovy/`, `scala/` modules)
- `embedding/`, `image/`, `audio/` — cross-modal utilities
- `HttpClientManager`, `TranscriptionClient` — outbound HTTP and transcription

---

## Layer 2 — Providers (`providers/`)

Each file in [
`providers/src/main/kotlin/.../providers/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/providers/src/main/kotlin/com/simiacryptus/cognotik/providers)
implements the `ChatInterface` (and related interfaces) for a single AI provider:

`OpenAIProvider`, `AnthropicProvider`, `GeminiProvider`, `BedrockProvider`, `GroqProvider`, `MistralProvider`,
`DeepSeekProvider`, `OllamaProvider`, `PerplexityProvider`, `ModelsLabProvider`, `ElevenLabsProvider`.

This is the BYOK surface: callers supply keys; Cognotik routes to whichever provider is configured.

---

## Layer 3 — Web UI Framework (`webui/`)

This module hosts the runtime server and defines the core execution model.

**HTTP server** — [
`ApplicationServer`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/application/ApplicationServer.kt)
extends `ChatServer` (Jetty-backed). It wires servlet endpoints (`AppInfoServlet`, `UserInfoServlet`, `UsageServlet`,
etc.), configures multipart uploads, and manages WebSocket upgrade. `CognotikAppServer` is the concrete top-level
deployment.

**Session / WebSocket** — [
`SocketManager`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SocketManager.kt)
is the abstract base for a connected user session. It loads persisted message state from `StorageInterface` on
construction. Concrete subclasses (`ChatSocketManager`, `SmartChatSocketManager`) handle message dispatch. `SessionTask`
models an in-flight async task within a session.

**UI primitives** ([
`ui/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/webui/src/main/kotlin/com/simiacryptus/cognotik/ui)) —
`Discussable`, `Retryable`, `TabbedDisplay`, and the patch UI are server-rendered interactive components injected into
the WebSocket message stream.

**Planning orchestrator** — [
`TaskOrchestrator`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/TaskOrchestrator.kt)
drives agentic plan execution. It resolves task dependency order (`PlanUtil.getAllDependencies`), builds Mermaid
visualizations of the plan graph, and submits tasks to a per-session thread pool (from
`ApplicationServices.threadPoolManager`). It holds the working-directory file tree and exposes it as `codeFiles` (a
`Map<Path, String>`).

**Cognitive modes** ([
`plan/cognitive/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive)) —
`CognitiveModeType` is a typed `DynamicEnum` where each value carries a `configClass` and a factory lambda. Modes
register themselves via `registerCognitiveMode(...)`. Concrete modes (`AdaptivePlanningMode`, `WaterfallMode`,
`ConversationalMode`) live in `tasklib`.

---

## Layer 4 — Task Library (`tasklib/`)

The library of concrete task types that `TaskOrchestrator` can execute. They are organised by category:

| Category                                                                                                                                                               | Key tasks                                                                                                                                        |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| [`file/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file)           | `FileModificationTask`, `IterativeFileModificationTask`, `ImageGenerationTask`, `AudioGenerationTask`, `IllustrateDocumentTask`, `WriteHtmlTask` |
| [`run/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run)             | `CodingTask`, `RunCodeTask`, `AutoFixTask`, `SingleFixTask`, `SubPlanTask`, `SymbolsDbCodeTask`                                                  |
| [`reasoning/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning) | `BrainstormingTask`, `SocraticDialogueTask`, `FiniteStateMachineTask`, `HistoricalFigureDebateTask`                                              |
| [`online/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/online)       | `SeleniumFetchTask`, `MCPToolTask`                                                                                                               |
| [`cognitive/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive)       | `AdaptivePlanningMode`, `WaterfallMode`, `ConversationalMode`                                                                                    |

All concrete tasks extend [
`AbstractTask`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/AbstractTask.kt)
(defined in `webui`) and are registered against a `TaskType` enum-like key.

---

## Layer 5 — Client Applications

**IntelliJ Plugin** ([
`intellij/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/intellij)) — a standard
JetBrains Platform plugin. It embeds an `ApplicationServer` (or delegates to a running desktop daemon) to provide
IDE-integrated AI actions. Action entry points live under [
`intellij/src/main/kotlin/cognotik/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/intellij/src/main/kotlin/cognotik).

**Desktop App** ([
`desktop/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/desktop)) — a standalone JVM
application that hosts the full `ApplicationServer` locally. Provides system-tray access and a socket-based control
channel for the IDE plugin to reuse the running instance.

**Web App** ([`webapp/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/webapp)) — a
React/TypeScript single-page application that communicates with `ApplicationServer` over HTTP and WebSocket. Handles
real-time message rendering, tab state, and theming on the client side.

---

## Key Cross-Cutting Patterns

- **File-based persistence** — `StorageInterface` (backed by the local filesystem by default) stores every session's
  messages and generated artifacts as readable files. This is the "AI that shows its work" property.
- **Thread-pool-per-session** — `ApplicationServices.threadPoolManager` allocates and tracks pools per `(session, user)`
  pair, so parallel tasks within a plan run concurrently without interfering across sessions.
- **Dynamic task/mode registries** — both `TaskType` and `CognitiveModeType` use a `DynamicEnum` pattern, meaning new
  task types and cognitive modes can be added at runtime (e.g. by the IntelliJ plugin) by calling `register(...)`.
- **No Python bridge** — all AI calls go through JVM-native HTTP clients (`HttpClientManager`) directly to provider REST
  APIs. The interpreter modules (`kotlin/`, `groovy/`, `scala/`, `antlr/`) execute code on the JVM or via the local
  runtime.

Here is a systematic comparison between my code-derived documentation and the existing docs.

---

## Comparison: Code-Derived vs. Existing Documentation

### Overall Quality of Existing Docs

The existing documentation — especially [
`docs/architecture/architecture_overview.md`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/docs/architecture/architecture_overview.md) —
is **substantially accurate and well-structured**. It is far from totally wrong. The module-level READMEs are a
different story.

---

### Where the Existing Docs Are Correct

| Claim                                                                            | Verdict                                                                                                                                                                                                                                                       |
|----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BaseAgent<I, R>` is the typed LLM abstraction                                   | Confirmed by [`BaseAgent.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/agents/BaseAgent.kt)                                                                             |
| `DynamicEnum` underpins all extension points (providers, tasks, cognitive modes) | Confirmed by [`CognitiveModeType.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveModeType.kt)                                                    |
| `ApplicationServices` is the central service-locator                             | Confirmed by imports throughout `ApplicationServer`, `SocketManager`, `TaskOrchestrator`                                                                                                                                                                      |
| `SocketManager` maintains message state history for reconnect replay             | Confirmed by [`SocketManager.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SocketManager.kt) — it loads from `dataStorage.getMessages()` at construction |
| `SessionProxyServer` acts as the session router                                  | Confirmed by [`SessionProxyServer.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/SessionProxyServer.kt)                                                            |
| `TaskOrchestrator` resolves dependency order and uses per-session thread pools   | Confirmed by [`TaskOrchestrator.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/TaskOrchestrator.kt)                                                                |
| Three cognitive modes: Waterfall, Conversational, Adaptive                       | Confirmed in [`tasklib/plan/cognitive/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive)                                                                    |
| Server-driven UI pushed over WebSocket                                           | Confirmed by `SessionTask` + `SocketManager` architecture                                                                                                                                                                                                     |
| The "four audiences" IO discipline (user, auditor, developer, LLM)               | Present in [`docs/tasks/agentic_io_best_practices.md`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/docs/tasks/agentic_io_best_practices.md) — nothing in the code directly contradicts this                               |

---

### Where My Documentation Added Accuracy the Existing Docs Lack

**1. Module boundaries are wrong in per-module READMEs**

[`core/README.md`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/README.md) and [
`providers/README.md`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/providers/README.md)
are **identical files** — the providers README is a verbatim copy of the core README. It describes actors, patch
utilities, and HSQL storage, none of which are in the `providers` module. The `providers` module actually contains only
the 11 concrete `*Provider.kt` files.

Similarly, [
`tasklib/README.md`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/README.md)
is **identical to `webui/README.md`** — both are boilerplate that doesn't describe either module.

**2. `PlanCoordinator` is mentioned but doesn't exist**

The `webui/README.md` and `tasklib/README.md` both refer to a `PlanCoordinator` as the class that "orchestrates task
execution with dependency management." The actual class is [
`TaskOrchestrator`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/TaskOrchestrator.kt).
There is no `PlanCoordinator` in the source tree.

**3. Actor names in `core/README.md` are partially stale**

The README lists `SimpleActor`, `LargeOutputActor`, `TextToSpeechActor` as actor types. The actual classes in [
`core/agents/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/core/src/main/kotlin/com/simiacryptus/cognotik/agents)
are `ChatAgent`, `CodeAgent`, `ParsedAgent`, `ParsedImageAgent`, `ImageGenerationAgent`, `ImageProcessingAgent`,
`AudioProcessingAgent`, and `ProxyAgent`. The naming convention shifted from `*Actor` to `*Agent` and the README was not
updated.

**4. `ClientManager` is described but the actual class is `HttpClientManager`**

The `core/README.md` refers to `ClientManager`. The real top-level class is [
`HttpClientManager.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/HttpClientManager.kt).

**5. Cognitive mode names in the top-level `README.md` are wrong**

The top-level [
`README.md`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/README.md?L141) lists
`AutoPlanMode`, `PlanAheadMode`, `GoalOrientedMode`, `TaskChatMode`, `GraphOrderedPlanMode` as the cognitive modes. None
of these names match the actual implementations. The real modes are `AdaptivePlanningMode`, `WaterfallMode`,
`ConversationalMode` (in `tasklib`), plus the abstract `CognitiveModeType` registry. The `intellij/README.md` correctly
names Waterfall, Conversational, and Adaptive — more up-to-date than the project root README.

**6. `stdtools` is completely undocumented**

Neither the top-level README nor any module README mentions `stdtools`. It is a real Gradle subproject in [
`settings.gradle.kts`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/settings.gradle.kts?L29)
but has no README.

**7. `tasklib` task-category organisation is not reflected anywhere**

The docs reference task categories generically. The actual source organises tasks into five subdirectories (`file/`,
`run/`, `reasoning/`, `online/`, `session/`, `social/`, `writing/`) under [
`tasklib/plan/tools/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/tools).
The docs enumerate individual task names but don't reflect this categorical structure.

---

### Where the `docs/architecture/` Doc Is Notably Better Than Mine

- It explicitly describes the **"Four Audiences" / Triple Log Rule** IO discipline — a real architectural convention I
  noted only briefly.
- It describes `HierarchicalPlanningMode` (a fourth cognitive mode). I only found three concrete files in
  `tasklib/cognitive/`; the hierarchical mode may be registered externally or still in progress — this is worth
  verifying.
- It documents `DocProcessor` / frontmatter-driven doc pipelines, `PatchProcessor` strategies (Fuzzy, Thermodynamic,
  FullReplacement), and the plugin platform via `ServiceLoader` — none of which I covered.
- It distinguishes `SingleTaskApp` from `ApplicationServer` as a second top-level application pattern, which I missed.

---

### Summary Table

| Area                                                         | Existing Docs                                                             | My Docs                                                       |
|--------------------------------------------------------------|---------------------------------------------------------------------------|---------------------------------------------------------------|
| `docs/architecture/architecture_overview.md`                 | Accurate and detailed                                                     | Consistent, less detail on IO discipline and patch strategies |
| Per-module READMEs (`core`, `providers`, `tasklib`, `webui`) | Significantly stale — copy-pasted, wrong class names, nonexistent classes | Accurate to source                                            |
| Top-level `README.md` cognitive mode names                   | Wrong (stale names)                                                       | Correct                                                       |
| `providers/README.md`                                        | Verbatim copy of `core/README.md`, entirely wrong for the module          | Correct (11 provider files)                                   |
| `stdtools`                                                   | Not documented anywhere                                                   | Noted as undocumented                                         |
| Task category structure                                      | Flat list, misses directory grouping                                      | Reflects actual directory layout                              |

 