Here is a breakdown of the antipatterns found
in [github.com/SimiaCryptus/Cognotik](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/):

---

## Antipatterns Found

### 1. Kotlin Non-Null Assertions (`!!`) — 153 files

The most pervasive issue. The `!!` operator crashes at runtime with `NullPointerException` if null occurs. It is spread
across virtually every module.

Heaviest affected areas:

- [
  `intellij/src/main/kotlin/cognotik/actions/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/intellij/src/main/kotlin/cognotik/actions) —
  nearly every action class
- [
  `core/src/main/kotlin/com/simiacryptus/cognotik/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/core/src/main/kotlin/com/simiacryptus/cognotik) —
  agents, utils, diff, models

**Fix:** Replace `!!` with `?: error(...)`, `requireNotNull(...)`, safe-calls with appropriate fallbacks, or restructure
to avoid nullable paths.

---

### 2. `Thread.sleep` in Production Code — 20+ files

Blocking calls in coroutine/async contexts. Found across the entire `intellij/` actions layer and several `webui/`,
`desktop/`, and `experiment/` modules.

Notable examples:

- [
  `intellij/.../FileContextAction.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/intellij/src/main/kotlin/cognotik/actions/FileContextAction.kt)
- [
  `webui/.../TaskOrchestrator.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/TaskOrchestrator.kt)
- [
  `webui/.../PluginManager.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/platform/PluginManager.kt)

**Fix:** Replace with `delay()` (Kotlin coroutines) or restructure with proper async waiting.

---

### 3. `printStackTrace` instead of Logger — 7 locations (3 serious)

Three cases silently swallow or misreport exceptions using `e.printStackTrace()` instead of routing through the
structured logger:

| File                                                                                                                                                                                                           | Line | Issue                                                                                   |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------|-----------------------------------------------------------------------------------------|
| [`ApiKeyServlet.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/ApiKeyServlet.kt?L292)                      | 292  | `catch (e: Throwable) { e.printStackTrace() }` — exception silently printed, not logged |
| [`ApplicationDirectory.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/stdtools/src/main/kotlin/com/simiacryptus/cognotik/webui/application/ApplicationDirectory.kt?L113) | 113  | Both `printStackTrace` and `log.error` called — double logging                          |
| [`ScalaLocalInterpreter.scala`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/scala/src/main/scala/com/simiacryptus/cognotik/scala/ScalaLocalInterpreter.scala?L109)         | 109  | `e.printStackTrace()` inside injected Scala string                                      |

---

### 4. TODO Comments in Source Code — 10+ in Kotlin production source

Notable ones that indicate incomplete features:

| File                                                                                                                                                                                                 | Issue                                                           |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| [`GeminiSdkChatClient.kt:788`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/providers/src/main/kotlin/com/simiacryptus/cognotik/chat/GeminiSdkChatClient.kt?L788) | `TODO("Not yet implemented")` — `authorize()` throws at runtime |
| [`DeepSeekChatClient.kt:64`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/providers/src/main/kotlin/com/simiacryptus/cognotik/chat/DeepSeekChatClient.kt?L64)     | Pricing set to `0.0` with TODO                                  |
| [`AnthropicChatClient.kt:68`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/providers/src/main/kotlin/com/simiacryptus/cognotik/chat/AnthropicChatClient.kt?L68)   | Same — pricing `0.0` with TODO                                  |
| [`UITools.kt:481`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/UITools.kt?L481)                          | API key saving is commented out with TODO                       |
| [`PluginStartupActivity.kt:48`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/intellij/src/main/kotlin/com/simiacryptus/cognotik/PluginStartupActivity.kt?L48)     | `ENABLE_LOGS = true` hardcoded, TODO to make configurable       |

---

### 5. Catch Generic `Exception`/`Throwable` — minor

Only 1 occurrence in production Java ([
`CodeImplementer.java:101`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tool/src/main/java/com/simiacryptus/CodeImplementer.java?L101))
which does log the error properly — low severity.

---

## Priority Summary

| Priority | Antipattern                                                             | Count       |
|----------|-------------------------------------------------------------------------|-------------|
| High     | Kotlin `!!` non-null assertions                                         | 153 files   |
| High     | `Thread.sleep` in coroutine/async production code                       | 20+ files   |
| Medium   | `TODO("Not yet implemented")` throws at runtime (`GeminiSdkChatClient`) | 1           |
| Medium   | `e.printStackTrace()` swallowing exceptions without logger              | 3 locations |
| Low      | Pricing set to `0.0` (TODO) in provider clients                         | 2 files     |
| Low      | `ENABLE_LOGS` hardcoded to `true`                                       | 1           |