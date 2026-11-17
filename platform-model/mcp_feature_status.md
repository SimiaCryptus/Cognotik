# MCP Tool Support — Feature Status

Plan: "MCP Tool Support — Feature Plan" (Phase 1 = sequential, non-cancellable loop).
This document tracks what has been implemented, deviations from the plan, and follow-up items.

Status legend: ✅ done · 🟡 partial · ⏳ not started / follow-up · ❌ dropped (by decision)

## 1. Decisions applied (answers to the plan's open questions)

| # | Question | Decision | Applied as |
|---|---|---|---|
| Q1 | Module layout | Put MCP protocol code in `core` | `core/src/main/kotlin/com/simiacryptus/cognotik/mcp/*` |
| Q2 | Hand-roll vs SDK | SDK is acceptable | 🟡 **Phase 1 uses a minimal hand-rolled JSON-RPC layer** (Jackson only). Reason: no build files were in scope, so no new dependency could be added. **No new dependencies were introduced.** See F-3. |
| Q3 | Sides required | Local provider + MCP client + MCP server host | ✅ All three; transports: stdio + in-memory. Streamable HTTP ⏳ (F-3) |
| Q4 | Anthropic targets | Latest models | Adaptive thinking (implies interleaved thinking); no beta header sent |
| Q5 | Usage semantics | `usageHandler` is authoritative | Metered per round inside the clients; `ChatResponse.usage` is a display-only sum across rounds |
| Q6 | Gemini catalog | Set `supportsTools` in `ChatModel` | `ChatModel.supportsInteractiveTools` added; catalog edits ⏳ (F-1) |
| Q7 | Metrics source | Via `ServiceRouter` | 🟡 `McpToolLoop.metricsProvider` hook, defaults to `NoOpMetrics`; wiring ⏳ (F-2) |
| Q8 | Server-host auth | API key / random key, later | Not needed for stdio/in-memory; applies to HTTP (F-3) |
| Q9 | JSON cloning of `ChatRequest` | Not a concern | `toolProviders`/`toolOptions` are `@get:JsonIgnore`; the loop uses data-class `copy` |
| Q10 | Approval policy | Tool implementer's job | ❌ `approval` hook removed from `ToolLoopOptions`; only `allowedTools` remains |
| Q11 | Thinking display | No streaming; simple | Thinking kept in `blocks` only (logs/usage); not concatenated into `content`. Gemini `includeThoughts` is not enabled |
| Q12 | Tool-result media | Later | Non-text MCP content becomes text placeholders (`[image: mime, N bytes]`) |

## 2. Implementation status (plan §10 order)

### 2.1 platform-model

| Item | Status | Location / notes |
|---|---|---|
| `MCPToolDefinition`, `MCPToolResult`, `MCPContent`, `MCPCallContext`, `MCPTool`, `MCPToolProvider`, `MCPException`, `ToolLoopOptions` | ✅ | `platform/model/mcp/MCPTypes.kt`. `MCPContent` is polymorphic JSON (`type` = text/image/audio/resource_link/resource). |
| `ChatRequest.tools`, `tool_choice`, `toolProviders` (`@JsonIgnore`), `toolOptions` (`@JsonIgnore`) | ✅ | `ModelSchema.kt`, appended with defaults (source compatible for named-arg callers) |
| `ContentPart.thinking` / `tool_use` / `tool_result` + `ThinkingBlock`, `ToolUseBlock`, `ToolResultBlock` | ✅ | `ModelSchema.kt`. Deviation: a Gemini text part that carries a thought signature is a `ContentPart(text, thinking=ThinkingBlock(signature))`, so the signature is replayed on the same part. |
| `ChatMessageResponse.blocks` + `toolCalls` helper | ✅ | `content` is still the concatenated visible text |
| `toRequestData()` renders tool/thinking blocks | ✅ | `ContentPart.usageText()` |
| `ChatModel.supportsInteractiveTools` (both constructors) + `ReasoningVariants.variant` | ✅ | Default `false` (opt-in) |
| `ToolRegistry` (sanitising, namespacing `<provider>__<tool>`, reverse mapping, `allowedTools`) | ✅ | `mcp/ToolRegistry.kt`. A provider whose `listTools()` fails is skipped with a warning. |
| `McpToolLoop` | ✅ | `mcp/McpToolLoop.kt` (details in §3) |
| `ChatInterface.chat` wiring (client obtained once; loop only when `toolProviders` is non-empty) | ✅ | |
| Metrics vocabulary: `TOOL`, `MCP_PROVIDER`, `TOOL_CALLS`, `TOOL_CALL_DURATION`, `TOOL_LOOP_ROUNDS`, `TOOL_CALLED`, `TOOL_LOOP_LIMIT_REACHED`, `Outcomes.TIMEOUT/DENIED` | ✅ | `Metrics.kt` |
| Unit tests (scripted fake client + fake provider) | ⏳ | F-5 |

### 2.2 Anthropic (`AnthropicChatClient`)

| Item | Status | Notes |
|---|---|---|
| `tools` / `tool_choice` in the request | ✅ | When thinking is on, `any` or a named tool is downgraded to `auto` (with a warning). |
| New input blocks: `tool_use`, `tool_result`, `thinking`, `redacted_thinking` | ✅ | Thinking is replayed only if `provider` is null or `anthropic`; unsigned thinking is dropped. |
| Consolidation preserves order and puts `tool_result` first in a message | ✅ | Also fixes a latent bug: `removeAll(toConsolidate)` used data-class equality and could remove identical later messages. |
| Tolerant response block parsing; `blocks` built in order; `stop_reason tool_use → finish_reason tool_calls` | ✅ | Unknown block types are logged and skipped. |
| Response no longer round-trips through JSON | ✅ | Removes the need to deserialize polymorphic blocks |
| Fail fast when `toolProviders` is set without `tools` | ✅ | `IllegalStateException` |
| Unknown-model fallback in `getModels()` sets `supportsTools = true` | ✅ | |
| Cache token mapping (`cache_read_input_tokens` / `cache_creation_input_tokens`) | ⏳ | F-7 (would change billing; needs review) |

### 2.3 Gemini (`GeminiSdkChatClient`)

| Item | Status | Notes |
|---|---|---|
| `FunctionDeclaration` with `parametersJsonSchema`, `toolConfig` from `tool_choice`, automatic function calling disabled | ✅ | Requires a google-genai SDK version that has `FunctionDeclaration.parametersJsonSchema` and `FunctionCallingConfigMode.Known` (F-4) |
| `part()`: `tool_use` → `functionCall` (+ `thoughtSignature`), `tool_result` → `functionResponse` (`output` / `error` / `structured`), thought/signature parts | ✅ | The new branches run before the `fromText("")` fallback. Foreign-provider thinking is dropped. |
| `convertFromGeminiResponse`: `functionCall` → `ToolUseBlock` (synthetic `synth_<name>#<i>` id when missing; synthetic ids are never sent back), signatures captured on any part, thought parts excluded from `content` | ✅ | `finish_reason = "tool_calls"` when calls are present |
| `mergeTextParts` does not merge tool or thinking parts | ✅ | |
| Fail fast when `toolProviders` is set without `tools` | ✅ | |
| Markdown request log shows tool calls and results | ✅ | |
| Model inline media in assistant turns is not part of `blocks` (lost on replay) | 🟡 | Acceptable for Phase 1 (media later) |

### 2.4 core — MCP protocol (`com.simiacryptus.cognotik.mcp`)

| Item | Status | Notes |
|---|---|---|
| `LocalMCPToolProvider` | ✅ | Unknown tool → `MCPException(-32602)`; execution failure → `isError` result |
| `McpJson` codec (definitions, results, content, JSON-RPC envelopes) | ✅ | Protocol `2025-06-18`; also accepts `2025-03-26` and `2024-11-05` |
| `McpTransport`, `StdioTransport` (newline-delimited, subprocess `spawn`, stderr → logs), `InMemoryTransport.pair()` | ✅ | |
| `McpServerHost`: `initialize`, `notifications/initialized`, `ping`, `tools/list` (cursor pagination), `tools/call`; advertises `capabilities.tools` only | ✅ | Requests are handled sequentially on the transport thread |
| `McpClientToolProvider`: handshake with version check, paginated `tools/list` with cache, `list_changed` invalidation, `tools/call`, server `ping`, timeout → `notifications/cancelled` | ✅ | Protocol errors are returned as `MCPToolResult(isError=true, "MCP error <code>: …")` |
| `McpClientToolProvider.loopback(...)` (client ⇄ in-memory ⇄ host) | ✅ | Basis for the protocol proof test (F-5) |
| Streamable HTTP transport (client and host, `Mcp-Session-Id`, `MCP-Protocol-Version`, Origin validation, localhost binding, random API key) | ⏳ | F-3 |

## 3. Loop behaviour (as implemented)

- `require(model.supportsTools)`; tool definitions are listed once per `run` and fixed for the whole turn.
- Per round: `client.chat(request.copy(messages = history, tools = defs, toolProviders = null, toolOptions = null))`.
  - No tool calls → return the response with the summed usage.
  - Otherwise the assistant message is replayed from `blocks` verbatim (thinking, signatures, order). Then **one** user message carries a `tool_result` for **every** `tool_use`.
- A forced `tool_choice` (`any` or a tool name) applies to round 1 only; later rounds use `auto`, so a forced choice cannot loop until `maxRounds`. `none` is kept on every round.
- Tool failures never escape the loop. Unknown tool, `MCPException` and other exceptions all become `is_error` results. `InterruptedException` is re-thrown.
- Results are normalised to a single text item (media → placeholders) and capped at `maxResultChars` with a truncation marker. `structuredContent` is passed through if it fits the cap.
- When `maxRounds` is reached: emit `TOOL_LOOP_LIMIT_REACHED`, make one wrap-up call with `tool_choice = "none"`, and return it.
- Each tool call is logged as a `<details>` markdown block in `logStreams` (name, id, outcome, duration, arguments, result).
- Metrics: `TOOL_CALLED` event (→ `TOOL_CALLS` counter), `TOOL_CALL_DURATION`, `TOOL_LOOP_ROUNDS`. Tool failures are **not** `AI_ERROR`s. Metrics failures are swallowed.
- `ToolLoopOptions.toolTimeout` is **not enforced** in Phase 1 (non-cancellable). The MCP client's request timeout is enforced.

### Usage example

```kotlin
val echo = object : MCPTool {
  override val definition = MCPToolDefinition(
    name = "echo", description = "Echo the input",
    inputSchema = ObjectMapper().readTree("""{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""")
  )
  override fun call(arguments: JsonNode, context: MCPCallContext) = MCPToolResult.text(arguments.path("text").asText())
}
val response = chatInterface.chat(ModelSchema.ChatRequest(
  messages = listOf(/* ... */), model = chatInterface.model.modelId,
  toolProviders = listOf(LocalMCPToolProvider(listOf(echo))),
))
```

## 4. Follow-up items

| ID | Item | Priority |
|---|---|---|
| F-1 | **Set `supportsTools = true` in the `AnthropicModels` and `GeminiModels` catalogs** for current tool-capable models (not TTS/image/live). Until then the loop rejects catalog models. Gemini's dynamic fallback in `getModels()` stays `false`. | **Blocker for use** |
| F-2 | Wire `McpToolLoop.metricsProvider` to `ServiceRouter` / `ServiceKey.METRICS` (and `ChatClientBase.metricsProvider` likewise) | High |
| F-3 | Streamable HTTP transport (client + host), via `io.modelcontextprotocol.sdk:mcp`. Also consider moving stdio onto the SDK. Requires adding the SDK dependency to `core`. Host: localhost bind, `Origin` check, random bearer key. | High |
| F-4 | Pin/verify the google-genai SDK version (needs `FunctionDeclaration.parametersJsonSchema`, `Part.thoughtSignature`, `FunctionCall.id`, `FunctionCallingConfigMode.Known`). If unavailable, convert MCP JSON Schema to Gemini's OpenAPI subset. | High |
| F-5 | Tests: loop with a scripted fake client (multi-round, unknown tool, limit reached, forced choice); Anthropic and Gemini request/response golden files (thinking + tool_use round trip; no silently dropped blocks); MCP loopback; stdio subprocess; opt-in live tests per provider | High |
| F-6 | Phase 2: parallel tool execution through a `ToolExecutor` seam, cancellation token in `MCPCallContext`, `callToolAsync`, progress notifications, enforce `toolTimeout`, refresh on `list_changed` between rounds | Next phase |
| F-7 | Anthropic cache-token usage mapping (`Cached`, `CacheWrite5m`); review pricing impact first | Medium |
| F-8 | Generate `MCPToolDefinition.inputSchema` from Kotlin types (could reuse `describe/JsonDescriber`) to simplify writing local tools | Low |
| F-9 | Tool-result media (images to Anthropic image blocks / Gemini inline data) | Later (Q12) |
| F-10 | README section and a sample `echo` tool for tests | Low |
| F-11 | Server host: handle requests concurrently and reject non-`ping` requests before `initialize` (currently lenient) | Phase 2 |

## 5. Dependencies

No new dependencies. Everything uses Jackson (already present in `platform-model` and `core`), slf4j, and the existing google-genai SDK. See F-3 and F-4 for planned or verified dependency changes.