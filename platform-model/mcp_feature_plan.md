---
specifies: mcp_feature_status.md
---

Implement the plan below (phase 1), and record status and follow up items in the mcp_feature_status.md document

Related Files:
* [ChatClientInterface.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ChatClientInterface.kt)
* [ChatModel.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ChatModel.kt)
* [ModelSchema.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ModelSchema.kt)
* [ChatClientBase.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/chat/ChatClientBase.kt)
* [AnthropicChatClient.kt](../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/AnthropicChatClient.kt)
* [ChatInterface.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/ChatInterface.kt)
* [MetricsInterface.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/service/MetricsInterface.kt)
* [Metrics.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/Metrics.kt)
* [HttpClientManager.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/HttpClientManager.kt)
* [JsonUtil.kt](../lwcore/src/main/kotlin/com/simiacryptus/cognotik/util/JsonUtil.kt)
* [JsonDescriber.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/describe/JsonDescriber.kt)
* [ReasoningVariants.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ReasoningVariants.kt)
* [GeminiSdkChatClient.kt](../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/GeminiSdkChatClient.kt)
* [TypeDescriber.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/describe/TypeDescriber.kt)

# MCP Tool Support — Feature Plan

Status: **Draft for review** · Scope: **plumbing only** (no concrete tools) · Phase 1 = sequential, non-cancellable loop; Phase 2 = concurrency + cancellation.

## 1. Goals / Non-goals

### Goals
1. The application can supply implementations of an `MCPTool` interface; the LLM can **invoke them interactively** during a chat: model emits tool call → we execute → result goes back to the model → repeat until a final answer.
2. The tool plumbing **speaks the real Model Context Protocol** (JSON-RPC 2.0; stdio and Streamable HTTP transports), as **client** (consume MCP servers) and **server** (expose our `MCPTool`s), not a private tool-call format.
3. **Reasoning + MCP**: thinking blocks (Anthropic `thinking` / `redacted_thinking` with signatures; Gemini thought signatures) are carried through every round of the loop, unmodified, as the providers require.
4. `MCPToolProvider` instances are passed as an optional collection on `ModelSchema.ChatRequest`.
5. Fits the existing structure: `ChatClientInterface` / `ChatInterface` / `ChatClientBase`, `ReasoningVariants`, the `Metrics.kt` vocabulary, and markdown-in-`logStreams` logging.

### Non-goals (Phase 1)
- Designing real tools.
- Parallel tool execution, cancellation, progress notifications, streaming responses (Phase 2).
- MCP resources/prompts/sampling/elicitation (only `tools/*`; the capability negotiation must not advertise the others).
- OpenAI-style providers (not in the files reviewed; see §10).
- Anthropic's server-side "MCP connector" (`mcp_servers` request param). It is Anthropic-only, requires publicly reachable servers, and does not cover Gemini or app-local tools. Rejected.

## 2. Current state (what the code does today)

| Area | Observation | Impact |
|---|---|---|
| `ModelSchema.ChatRequest` | Data class; `functions: List<RequestFunction>?` exists but is a legacy OpenAI shape (`parameters: Map<String,String>`), unused by Anthropic/Gemini clients. | Do not reuse. Add new fields. |
| `ModelSchema.ContentPart` | `text`, `image_url`, `input_audio` only. | Cannot represent tool_use / tool_result / thinking. |
| `ModelSchema.ChatMessageResponse` | `content: String?` plus media fields. | Thinking blocks and tool calls are lost. Need ordered structured blocks. |
| `ModelSchema.Role` | `assistant, user, system`. | Keep as is. Tool results travel in a `user` message (Anthropic and Gemini both do this). |
| `ChatClientInterface.chat` | Single round trip, `@Deprecated` "use via preauthenticated chat models" (= `ChatInterface`). | `ChatInterface.chat` is the natural choke point for the loop. |
| `ChatInterface.chat` | Builds a client via `provider.getChatClient(...)` on every call and delegates once. | Refactor to obtain the client once per loop. |
| `ChatModel` | Has `supportsTemperature`, `supportsReasoning`, `reasoningLevel`; no tool capability. | Add `supportsTools`. `ReasoningVariants.variant` and the secondary constructor copy every field by hand and must be updated too. |
| `AnthropicChatClient.chat` | Builds `AnthropicChatRequest` (no `tools`/`tool_choice`); `AnthropicContentInput` sealed class only has text/image subtypes; `AnthropicContentBlock` is flat `(type, text)`; response flattened to joined text; `stop_reason` ignored; thinking blocks dropped; usage only `input_tokens`/`output_tokens`. | Largest provider change. |
| `GeminiSdkChatClient.chat` | `ContentPart.part()` falls through to `fromText("")` for unknown shapes; `convertFromGeminiResponse` reads only `text()` and `inlineData()`; no `functionCall`, `thought`, or `thoughtSignature` handling; `ThinkingConfig` has a budget but not `includeThoughts`. | Must extend `part()`, `toContent()`, `convertFromGeminiResponse`, `buildGenerateContentConfig`. |
| `ChatClientBase` | Template for HTTP clients; `reportException` → `EventType.AI_ERROR`; logging via `log(...)` into `logStreams`. | Tool-loop logging and errors follow the same style. |
| `Metrics.kt` | Closed-by-convention vocabulary; metrics declared in `MetricType` companion with `by lazy`; attributes must be declared or they are sanitized away. | Add tool metrics here (§8). |
| `JsonUtil` | Lenient mapper (`FAIL_ON_UNKNOWN_PROPERTIES` off, NON_NULL). `jsonCopy`/`copy` round-trip through JSON. | `@JsonIgnore` fields (the providers) are **lost** by `jsonCopy()`; see Risks. |

## 3. Architecture

```
 caller ── ChatRequest(toolProviders=[…]) ──▶ ChatInterface.chat
                                                 │  (toolProviders non-empty?)
                                                 ▼
                                          McpToolLoop  ◀── MCPToolProvider(s) ◀─(JSON-RPC)─▶ MCP server
                                                 │  per round:
                                                 │   client.chat(request + tools, history)   ← single-round provider call
                                                 │   parse tool_use blocks
                                                 │   execute via provider(s)
                                                 │   append assistant blocks (thinking verbatim) + tool_result user msg
                                                 ▼
                                          final ChatResponse (text answer, aggregated usage)
```

Key decisions:

**D1 — Provider clients stay single-round and stateless.** They only translate: tool *definitions* in the request → provider format, and tool *calls*/thinking blocks in the response → neutral blocks (and neutral blocks back to provider format on later rounds). The loop is provider-neutral and lives outside the clients. This keeps retry semantics clean: a retried round never re-executes tools, because tools run outside the client.

**D2 — Two request fields.**
- `toolProviders: List<MCPToolProvider>?` — what the caller sets (live objects, `@JsonIgnore`).
- `tools: List<MCPToolDefinition>?` + `tool_choice` — serializable, populated **by the loop** per round; this is what provider clients read. A provider client that receives `toolProviders` but no `tools` (called directly, bypassing `ChatInterface`) must fail fast with a clear message rather than silently ignoring tools.

**D3 — Structured blocks, not strings.** Add typed block fields to `ContentPart` and an ordered `blocks` list to `ChatMessageResponse` (keeping `content` as the concatenated text for backwards compatibility).

**D4 — Real MCP lives in a separate module** (suggested name `mcp`, depends on `platform-model`), so the SDK dependency does not leak into `platform-model`. `platform-model` only holds the interfaces/DTOs and the loop.

**D5 — Use the official MCP SDK** (Java SDK `io.modelcontextprotocol.sdk:mcp`, or the Kotlin SDK) rather than hand-rolling JSON-RPC. Hand-rolling Streamable HTTP (SSE, session ids, protocol-version headers, resumption) is real work, and the existing `HttpClientManager` is a blocking Apache client with no SSE support. Needs sign-off (Q2).

## 4. Types (platform-model)

Package suggestion: `com.simiacryptus.cognotik.platform.model.mcp` (new files below).

### 4.1 `MCPTool.kt`, `MCPToolProvider.kt`

```kotlin
/** MCP `Tool` definition (tools/list). inputSchema is JSON Schema, kept as a JsonNode. */
data class MCPToolDefinition(
  val name: String,
  val description: String = "",
  val title: String? = null,
  val inputSchema: JsonNode,                // must be {"type":"object", ...}
  val outputSchema: JsonNode? = null,
  val annotations: Map<String, Any?>? = null,   // readOnlyHint, destructiveHint, ...
)

/** MCP CallToolResult. Tool *execution* failures are isError=true (fed back to the model),
 *  distinct from JSON-RPC protocol errors (thrown as MCPException). */
data class MCPToolResult(
  val content: List<MCPContent> = emptyList(),   // text | image | audio | resource_link | resource
  val structuredContent: JsonNode? = null,
  val isError: Boolean = false,
)

sealed interface MCPContent {
  data class Text(val text: String) : MCPContent
  data class Image(val data: String, val mimeType: String) : MCPContent
  data class Audio(val data: String, val mimeType: String) : MCPContent
  data class ResourceLink(val uri: String, val name: String?, val mimeType: String?) : MCPContent
  data class Resource(val uri: String, val text: String?, val blob: String?, val mimeType: String?) : MCPContent
}

/** Context handed to every call. Phase 2 adds a cancellation token and progress sink here. */
data class MCPCallContext(val session: Session, val user: User?, val callId: String)

/** A tool implemented by this application. */
interface MCPTool {
  val definition: MCPToolDefinition
  fun call(arguments: JsonNode, context: MCPCallContext): MCPToolResult
}

/** A source of tools: in-process, or a connection to an MCP server. */
interface MCPToolProvider : AutoCloseable {
  /** Stable, bounded identifier (used for namespacing and metrics), e.g. "local" or a server name. */
  val name: String
  fun listTools(): List<MCPToolDefinition>
  fun callTool(name: String, arguments: JsonNode, context: MCPCallContext): MCPToolResult
  override fun close() {}
}
```

Notes:
- `callTool` is blocking for Phase 1 (consistent with the blocking `chat` API). Phase 2 widens it (§9).
- `MCPException(code, message, data)` is used for JSON-RPC-level failures.

### 4.2 Provider implementations (module `mcp`)

| Class | Role |
|---|---|
| `LocalMCPToolProvider(tools: List<MCPTool>)` | In-process, direct dispatch; no JSON-RPC. Fast path and the default for app-supplied tools. |
| `McpServerHost(tools: List<MCPTool>, …)` | **MCP server**: exposes `MCPTool`s via `initialize`, `notifications/initialized`, `tools/list` (with cursor pagination), `tools/call`, `ping`; transports: stdio, Streamable HTTP. Advertises `capabilities.tools` only. |
| `McpClientToolProvider(transport)` | **MCP client**: `initialize` handshake (protocolVersion, `clientInfo`, no client capabilities), then `tools/list` (follow `nextCursor`), `tools/call`; handles `notifications/tools/list_changed` by invalidating a cached list. Transports: stdio subprocess, Streamable HTTP (+ in-memory pair for tests). |

A loopback test (`McpClientToolProvider` ⇄ in-memory transport ⇄ `McpServerHost`) is the proof that the *protocol* is really exercised, and not only a private interface.

### 4.3 `ModelSchema` additions

```kotlin
// ChatRequest (all optional, appended to the end; defaults keep source/binary compat for named-arg callers)
@JsonIgnore var toolProviders: List<MCPToolProvider>? = null
var tools: List<MCPToolDefinition>? = null          // set by the loop, read by provider clients
var tool_choice: String? = null                      // "auto" (default) | "none" | "any" | tool name
@JsonIgnore var toolOptions: ToolLoopOptions? = null // maxRounds, per-call timeout, result size cap

// ContentPart: new, mutually exclusive with text/image/audio (one block per part)
var thinking: ThinkingBlock? = null
var tool_use: ToolUseBlock? = null
var tool_result: ToolResultBlock? = null

data class ThinkingBlock(
  val text: String? = null,              // Anthropic `thinking`; Gemini thought summary
  val signature: String? = null,         // Anthropic signature (opaque; must round-trip)
  val redacted: String? = null,          // Anthropic redacted_thinking.data (opaque)
  val provider: String? = null,          // which provider produced it; never send to another one
)
data class ToolUseBlock(
  val id: String, val name: String, val input: JsonNode,
  val signature: String? = null,         // Gemini thoughtSignature carried on the functionCall part
)
data class ToolResultBlock(val tool_use_id: String, val name: String, val content: List<MCPContent>,
                           val structured: JsonNode? = null, val is_error: Boolean = false)

// ChatMessageResponse
var blocks: List<ContentPart>? = null    // ordered raw blocks; `content` stays = concatenated text
// ChatChoice.finish_reason: normalize "tool_use" (Anthropic) / function-call parts (Gemini) → "tool_calls"
val ChatMessageResponse.toolCalls: List<ToolUseBlock>  // helper
```

Also:
- `toRequestData()` must render tool/thinking blocks into `UsageData.input_text` (today it only reads `part.text`).
- `ChatModel`: add `val supportsTools: Boolean = false` (opt-in); add to both constructors and to `ReasoningVariants.variant`. Set `true` in the Anthropic/Gemini catalogs and in the "unknown model" fallbacks in each `getModels()` for Anthropic (Gemini: see Q6).
- `ToolLoopOptions(maxRounds = 16, toolTimeout = 120s, maxResultChars = 100_000, allowedTools: ((MCPToolDefinition) -> Boolean)? , approval: ((ToolUseBlock) -> Boolean)?)`.

## 5. The loop — `McpToolLoop` (platform-model)

```
fun run(client: ChatClientInterface, request: ChatRequest, model: ChatModel, logStreams, usageHandler): ChatResponse
  registry = ToolRegistry(request.toolProviders)        // listTools() once per loop; namespacing
  require(model.supportsTools)
  history = request.messages
  repeat(maxRounds):
    resp = client.chat(request.copy(messages = history, tools = registry.definitions, toolProviders = null), …)
    msg  = resp.choices[0].message;  calls = msg.toolCalls
    accumulate usage
    if calls.isEmpty(): return resp.copy(usage = totalUsage)
    history += ChatMessage(assistant, msg.blocks)        // thinking + tool_use verbatim, original order
    history += ChatMessage(user, calls.map { execute(it) as tool_result part })   // ALL results in ONE message
  // limit reached
  final = client.chat(request.copy(messages = history, tool_choice = "none", …))   // ask for a wrap-up; never loop forever
```

Rules:
- **Every `tool_use` must get a `tool_result`** (both providers 400 otherwise). Unknown tool, approval denied, timeout, protocol error, and exception all become `tool_result(is_error=true)` with a short message, and the model decides what to do. Never throw out of the loop mid-turn because of a tool.
- **Order**: Anthropic requires `tool_result` blocks first in the user message, before any text. Results are matched by id, not by position.
- **Thinking**: the assistant message is replayed from `msg.blocks` (not rebuilt from `content`), so thinking/signature/redacted blocks and Gemini part signatures return exactly as received. Reasoning level and model are fixed for the whole loop (providers disallow toggling thinking mid-turn). Never mutate or trim thinking text.
- **Tool names**: provider limits are ~64 chars of `[A-Za-z0-9_-]` (Gemini also allows `.`). The registry sanitizes, namespaces on collision (`<provider>__<tool>`), and maps back on dispatch. MCP names can contain characters the providers reject.
- **Results to text**: Phase 1 sends text content and `structuredContent` (serialized JSON text for Anthropic, `Map` for Gemini). Image/audio/resource blocks go as Anthropic image blocks where supported, otherwise a placeholder (`[image: mime, N bytes]`). Cap at `maxResultChars` with an explicit truncation marker.
- **Usage/billing**: each round calls `usageHandler` inside the client (existing behavior), so metering is per round. The returned `ChatResponse.usage` is the sum across rounds for display. Callers that also meter from `response.usage` would double count (Q5).
- **Logging**: per round, a `<details>` markdown block into `logStreams` (tool name, arguments, result summary, duration), same style as the `POST` logging in `ChatClientBase`.
- **`ChatInterface.chat`** obtains the client **once**, then: no providers → today's path unchanged; providers → `McpToolLoop.run`.

## 6. Provider mapping

### 6.1 Anthropic (`AnthropicChatClient`)
- Request: add `tools: [{name, description, input_schema}]` and `tool_choice` (`{"type":"auto"}` | `none` | `any` | `{"type":"tool","name":…}`). With thinking enabled only `auto`/`none` are accepted, so reject or downgrade `any`/named choices when `reasoningLevel != null`.
- Add `AnthropicContentInput` subtypes: `tool_use(id,name,input)`, `tool_result(tool_use_id, content, is_error)`, `thinking(thinking, signature)`, `redacted_thinking(data)` (register in `@JsonSubTypes`).
- Message consolidation (the `takeWhile { role == thisRole }` block) must preserve block order, put `tool_result` first in a user message, and handle the new `ContentPart` fields before the text/image branches. Update the plain-text fallback so it cannot swallow tool blocks.
- Response: replace the flat `AnthropicContentBlock(type, text)` with a tolerant block (type, text, id, name, input: JsonNode, thinking, signature, data). Build `ChatMessageResponse.blocks` in order; set `content` = joined text blocks only. Map `stop_reason == "tool_use"` → `finish_reason = "tool_calls"`; handle `max_tokens` and `pause_turn`. Unknown block types must not break parsing (the mapper already ignores unknown properties; keep unknown blocks out of `blocks` but log them).
- Thinking details to verify against the API version in use (Q4): interleaved thinking (`adaptive` thinking enables it implicitly; budgeted thinking on Claude 4 may need the `interleaved-thinking` beta header); `budget_tokens < max_tokens`; summarized thinking is billed at full thinking tokens, so `usage.output_tokens` exceeds visible text; prior-turn thinking blocks may be stripped server-side, but the current tool-use turn's must be sent back.
- Usage: map `cache_read_input_tokens` / `cache_creation_input_tokens` to `TokenTypes.Cached` / `CacheWrite5m` while here, since multi-round loops make caching matter. This is optional; flag in review.
- Keep `NonRetryableException` wrapping for request-mapping failures; add a clear `IllegalStateException` for `toolProviders` without `tools`.

### 6.2 Gemini (`GeminiSdkChatClient`)
- Request: `Tool.builder().functionDeclarations(FunctionDeclaration…name/description/parametersJsonSchema)` in `GenerateContentConfig.tools`; `toolConfig` for `tool_choice`; **disable the SDK's automatic function calling** (we run the loop). Verify the SDK version supports `parametersJsonSchema`; otherwise schemas must be reduced to the OpenAPI subset Gemini accepts (the MCP schema → Gemini schema converter is a known risk).
- `ContentPart.part()` / `parts()`: new branches for `tool_use` → `Part` with `functionCall` (and `thoughtSignature` if present); `tool_result` → `Part.fromFunctionResponse(name, Map)`. The current `else -> fromText("")` would silently drop them, so the new branches must come before it.
- `convertFromGeminiResponse`: collect `functionCall` parts into `ToolUseBlock` (Gemini may omit ids; synthesize deterministic ones, `name#index`, and send `name` back in the response). Capture `thoughtSignature` on **whichever part** carries it (text, functionCall); replay parts in the same order with the same signatures. Gemini 3 rejects function-call turns missing their signatures; for parallel calls only the first part has one.
- Thinking summaries: set `includeThoughts(true)` only if we want to surface summaries; if so, filter `thought()==true` parts out of `content` (they currently would be concatenated into the answer). Round-trip needs signatures only.
- Finish reasons: a response with function calls reports STOP; the loop keys off `toolCalls`, not `finish_reason`. Keep the existing SAFETY/MALFORMED_FUNCTION_CALL handling.
- `authorize()` is a `TODO()` that throws; unrelated, but do not route any tool traffic through `ChatClientBase.post`.

## 7. MCP protocol details (module `mcp`)

- Protocol revision: target the latest stable (confirm, e.g. 2025-06-18 or later) and negotiate down per the `initialize` response. Send `MCP-Protocol-Version` on every HTTP request after init.
- Lifecycle: `initialize` → `notifications/initialized` → operate → close (stdio: close stdin, then terminate; HTTP: `DELETE` the session).
- stdio: newline-delimited JSON on stdin/stdout, **stderr is logs only**; never write non-protocol bytes to stdout in the server host.
- Streamable HTTP: single endpoint; `POST` returns JSON or an SSE stream; session via `Mcp-Session-Id`; **validate `Origin` and bind to localhost by default** for the server host; auth is pluggable (Q8).
- Errors: unknown tool / invalid args → JSON-RPC error (`-32602`); tool execution failure → `isError:true` result. The client provider surfaces both to the loop as `MCPToolResult(isError=true)` (protocol errors prefixed so the model can tell).
- `tools/list` pagination and `list_changed`: loop caches definitions for the duration of one `run`, so the tool set cannot change mid-turn.

## 8. Metrics (`Metrics.kt`) and errors

Follow the existing patterns: attributes declared in `MetricAttribute`, metrics in the `MetricType` companion with `by lazy`, events in `EventType`.

- `MetricAttribute.TOOL = of<String>("tool")` (bounded by registered tools), `MetricAttribute.MCP_PROVIDER = of<String>("mcp_provider")`.
- `MetricType.TOOL_CALLS` (counter; `tool`, `mcp_provider`, `outcome`), `MetricType.TOOL_CALL_DURATION` (distribution, ms), `MetricType.TOOL_LOOP_ROUNDS` (distribution, `outcome`).
- `EventType.TOOL_CALLED` (counter = `TOOL_CALLS`; includes `user`, `session`), `EventType.TOOL_LOOP_LIMIT_REACHED`.
- Outcomes reuse `Outcomes.SUCCESS/FAILURE/CANCELLED`; add `TIMEOUT`, `DENIED` constants.
- Tool failures are **not** `AI_ERROR` events (those mean provider failures). Provider failures inside the loop keep flowing through the existing `reportException` in the clients.
- Open question: how `platform-model` obtains a `MetricsInterface` (`ChatClientBase.metricsProvider` is in `core`). Loop takes it as a constructor parameter; `ChatInterface` needs a source (Q7).

## 9. Phase 2 (immediate follow-up): concurrency and cancellation

Design hooks to leave in Phase 1 so Phase 2 is additive:
- `MCPCallContext` is a data class passed everywhere (add `cancellation: CancellationToken`, `progress: (Double, Double?, String?) -> Unit`).
- Loop executes calls through a single `ToolExecutor` seam (Phase 1: sequential inline).
- `MCPToolProvider.callTool` stays blocking, with an additional `callToolAsync(...): CompletableFuture<MCPToolResult>` defaulting to `supplyAsync` over the workPool.

Phase 2 work:
1. **Parallel calls**: models can emit several `tool_use` blocks in a turn. Run them concurrently on `workPool`, preserve result association by id, honor `disable_parallel_tool_use` (Anthropic), and a per-provider concurrency limit. Tools may declare `readOnlyHint` to opt in to parallelism; default to sequential for non-annotated tools.
2. **Cancellation**: a token tied to the `Session` / UI stop; propagate as `notifications/cancelled` (with `requestId`) from `McpClientToolProvider`, `Future.cancel(true)` for local tools, and handle `notifications/cancelled` in `McpServerHost`. In-flight chat HTTP calls need an abort path (Apache `HttpUriRequest.abort`). On cancel the loop must still produce **valid history** (synthetic `is_error` tool_results for every outstanding `tool_use`) if the conversation will continue, or discard the incomplete turn.
3. **Progress** notifications (`progressToken`) surfaced into `logStreams`/UI.
4. Timeouts per tool and per loop; `tools/list_changed` refresh between rounds; optional streaming responses.
5. Reasoning + parallel + cancellation tests (signature replay with multiple calls, partial results).

## 10. Implementation order (Phase 1)

1. **Spikes/decisions** (Q1–Q4): SDK choice and module layout; confirm both providers' tool + thinking wire shapes against the SDK/API versions in use with one hand-written request each.
2. `platform-model`: DTOs (§4), `ModelSchema` additions, `ChatModel.supportsInteractiveTools` (+ `ReasoningVariants.variant`, secondary constructor), `ToolRegistry`, `McpToolLoop`, `ChatInterface` wiring. Unit-test the loop with a scripted fake `ChatClientInterface` and fake provider.
3. `AnthropicChatClient` mapping + tests (serialization golden files for request and response, including thinking+tool_use round trip).
4. `GeminiSdkChatClient` mapping + tests.
5. Module `mcp`: `LocalMCPToolProvider`, `McpServerHost`, `McpClientToolProvider`, in-memory + stdio + HTTP transports; loopback and conformance tests.
6. Metrics (§8), logging, docs (`README` section, a sample `echo` tool used in tests only).
7. Live integration test (opt-in, behind env keys) per provider: a trivial tool, reasoning on, 2+ rounds.

## 11. Risks

- **Silent drop of blocks**: `ContentPart.part()` (Gemini) and the Anthropic consolidation fall through quietly for unknown shapes. Add tests that fail if a tool/thinking block is dropped.
- **`@JsonIgnore` providers + `jsonCopy()`/`copy{}`**: any caller that clones a `ChatRequest` through JSON loses `toolProviders`. Search for such callers (Q9). The loop itself uses data-class `copy`.
- **Signature fidelity**: thinking signatures/redacted data must never be edited, re-encoded, or sent to a different provider (`ThinkingBlock.provider` guard; strip on cross-provider replays).
- **Prompt injection via tool results**: results are untrusted input. Mitigations: result size cap, `approval` hook for destructive tools (`destructiveHint`), allow-list, no automatic credential forwarding to servers, and user consent before attaching external MCP servers (MCP's host-responsibility guidance).
- **Runaway cost**: `maxRounds` default 16 plus a final `tool_choice=none` wrap-up; per-round usage metering.
- **Schema incompatibilities**: MCP JSON Schema vs. Gemini's accepted subset; tool-name constraints.
- **Model default `supportsTools=false`** hides tools until catalogs are updated; deliberate, but needs the catalog edits.
- **Retries**: retry wrappers (location unknown) must wrap the single-round client call, not the loop.

## 12. Open questions

1. **Module/dependency**: new `mcp` Gradle module vs. placing in `core`? OK to add the MCP SDK dependency (Java vs. Kotlin SDK; JVM target)?
put it in core, i can move later if i want
2. **Hand-roll vs. SDK** for JSON-RPC/transports (recommended: SDK).
SDK is fine. note any dependencies
3. **Which sides are required in Phase 1?** Recommendation: local provider + MCP client + MCP server host (stdio + Streamable HTTP). If only app-local tools matter initially, the server host can slip to Phase 1.5, but the loopback proof of protocol would go with it.
local provider + MCP client + MCP server hostz
4. **Anthropic model targets**: which models/API versions must work (adaptive vs. budgeted thinking, interleaved-thinking header, `xhigh`/`max` effort)? Existing code always sends `ThinkingConfig()` adaptive with no budget.
latest models (5.5) 
5. **Usage semantics**: do callers read `ChatResponse.usage` to bill, or rely on `usageHandler`? Aggregating in the response could double count.
   `usageHandler` is authoritative
6. **Gemini catalog**: which Gemini models get `supportsTools=true`? (TTS/image/live models must be false; the dynamic fallback in `getModels()` cannot know.)
all the latest ones we care about support it. we should be able to set it in ChatModel
7. **Metrics source in `platform-model`**: how should `ChatInterface`/`McpToolLoop` get a `MetricsInterface` (via `ServiceRouter`/`ServiceKey.METRICS`)?
   ServiceRouter just like everything else
8. **Server host auth**: what authentication is expected on the HTTP transport (session/user token reuse)?
we have the api key if that is needed. if we are hosting an ephemeral server, just make up a random key. I'll sort that part out later.
9. **Who clones `ChatRequest`** via JSON, or serializes/persists it (session logs, UI)?
   fuggetaboutit. when the interface gets the request, it has valid callbacks
10. **Approval policy**: should destructive tools require interactive approval by default, and is there an existing UI hook?
    approvals are an implementation detail for the function implementer to return something like "sent for approval" or to actually block for user approval.
11. **Thinking display**: should thinking blocks be surfaced to the UI/logs, or kept internal (logs only)?
    this platform doesn't do streaming replies, so this is pretty simple.
12. **Tool-result media**: required in Phase 1, or text + structured JSON only?
    we will do media later
