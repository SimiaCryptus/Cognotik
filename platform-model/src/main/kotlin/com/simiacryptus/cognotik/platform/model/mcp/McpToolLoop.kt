package com.simiacryptus.cognotik.platform.model.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.platform.model.Attributes
import com.simiacryptus.cognotik.platform.model.ChatClientInterface
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.EventType
import com.simiacryptus.cognotik.platform.model.MetricAttribute
import com.simiacryptus.cognotik.platform.model.MetricType
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Outcomes
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageListener
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.service.MetricsInterface
import com.simiacryptus.cognotik.platform.service.NoOpMetrics
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream

/**
 * Provider-neutral tool loop (Phase 1: sequential, non-cancellable).
 *
 * Calls the single-round provider client, executes any requested tools via the
 * [MCPToolProvider]s, appends the assistant blocks (verbatim, including thinking
 * and signatures) plus one user message holding every tool result, and repeats
 * until the model answers without tool calls or [ToolLoopOptions.maxRounds] is hit.
 *
 * Tools run outside the client, so a retried round never re-executes tools.
 * Usage is metered per round by the client via [UsageListener] (authoritative);
 * the returned response's `usage` is a display-only sum across rounds.
 */
class McpToolLoop(
  private val metrics: () -> MetricsInterface = { metricsProvider() },
) {

  @Suppress("DEPRECATION")
  fun run(
    client: ChatClientInterface,
    request: ModelSchema.ChatRequest,
    model: ChatModel,
    session: Session,
    user: User?,
    logStreams: MutableList<BufferedOutputStream>,
    usageHandler: UsageListener,
  ): ModelSchema.ChatResponse {
    require(model.supportsTools) {
      "Model ${model.modelId} does not support tools (ChatModel.supportsTools=false); cannot run MCP tool loop"
    }
    val options = request.toolOptions ?: ToolLoopOptions()
    require(options.maxRounds >= 1) { "ToolLoopOptions.maxRounds must be >= 1" }
    val registry = ToolRegistry(request.toolProviders.orEmpty(), options.allowedTools)
    if (registry.isEmpty()) {
      log.warn("Tool providers supplied but no tools available; running a plain chat")
      return client.chat(
        request.copy(tools = null, tool_choice = null, toolProviders = null, toolOptions = null),
        model, logStreams, usageHandler
      )
    }
    val definitions = registry.definitions
    val history = request.messages.toMutableList()
    var totalUsage: ModelSchema.Usage? = null

    for (round in 1..options.maxRounds) {
      val choice = effectiveToolChoice(request.tool_choice, round)
      val response = client.chat(roundRequest(request, history, definitions, choice), model, logStreams, usageHandler)
      totalUsage = addUsage(totalUsage, response.usage)
      val message = response.choices.firstOrNull()?.message
      val calls = message?.toolCalls.orEmpty()
      if (message == null || calls.isEmpty()) {
        recordLoop(round, Outcomes.SUCCESS, model)
        return response.copy(usage = totalUsage)
      }
      log.debug("Tool loop round {}: {} tool call(s) requested", round, calls.size)
      history += ModelSchema.ChatMessage(role = ModelSchema.Role.assistant, content = message.blocks.orEmpty())
      history += ModelSchema.ChatMessage(
        role = ModelSchema.Role.user,
        content = calls.map { call ->
          ModelSchema.ContentPart(tool_result = execute(registry, call, options, session, user, logStreams))
        }
      )
    }

    log.warn("Tool loop reached maxRounds=${options.maxRounds} for model ${model.modelId}; requesting wrap-up")
    safely {
      it.event(
        EventType.TOOL_LOOP_LIMIT_REACHED,
        Attributes.of(
          MetricAttribute.MODEL(model.modelId),
          user?.toString()?.let { u -> MetricAttribute.USER(u) },
          MetricAttribute.SESSION(session.toString()),
        )
      )
    }
    val final = client.chat(roundRequest(request, history, definitions, "none"), model, logStreams, usageHandler)
    totalUsage = addUsage(totalUsage, final.usage)
    recordLoop(options.maxRounds + 1, Outcomes.FAILURE, model)
    if (final.choices.firstOrNull()?.message?.toolCalls?.isNotEmpty() == true) {
      log.warn("Model requested tools despite tool_choice=none after loop limit; returning without executing them")
    }
    return final.copy(usage = totalUsage)
  }

  private fun roundRequest(
    request: ModelSchema.ChatRequest,
    history: List<ModelSchema.ChatMessage>,
    definitions: List<MCPToolDefinition>,
    toolChoice: String?,
  ) = request.copy(
    messages = history.toList(),
    tools = definitions,
    tool_choice = toolChoice,
    toolProviders = null,
    toolOptions = null,
  )

  /** Forced choices ("any" / a tool name) apply to the first round only, otherwise the loop could never end. */
  private fun effectiveToolChoice(choice: String?, round: Int): String? = when {
    choice == null -> null
    choice == "none" -> "none"
    round == 1 -> choice
    else -> null
  }

  private fun execute(
    registry: ToolRegistry,
    call: ModelSchema.ToolUseBlock,
    options: ToolLoopOptions,
    session: Session,
    user: User?,
    logStreams: List<BufferedOutputStream>,
  ): ModelSchema.ToolResultBlock {
    val start = System.nanoTime()
    val entry = registry.resolve(call.name)
    var outcome = Outcomes.SUCCESS
    val result: MCPToolResult = if (entry == null) {
      outcome = Outcomes.FAILURE
      MCPToolResult.error("Unknown tool: ${call.name}")
    } else try {
      entry.provider.callTool(entry.definition.name, call.input, MCPCallContext(session, user, call.id))
    } catch (e: MCPException) {
      MCPToolResult.error("MCP protocol error ${e.code}: ${e.message}")
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw e
    } catch (e: Exception) {
      log.warn("Tool ${call.name} failed", e)
      MCPToolResult.error("Tool execution failed: ${e.javaClass.simpleName}: ${e.message}")
    }
    if (result.isError) outcome = Outcomes.FAILURE
    val durationMs = (System.nanoTime() - start) / 1_000_000.0
    val (content, structured) = normalize(result, options.maxResultChars)

    val attrs = Attributes.of(
      MetricAttribute.TOOL(if (entry == null) "unknown" else entry.exposedName),
      MetricAttribute.MCP_PROVIDER(entry?.provider?.name ?: "unknown"),
      MetricAttribute.OUTCOME(outcome),
      user?.toString()?.let { MetricAttribute.USER(it) },
      MetricAttribute.SESSION(session.toString()),
    )
    safely {
      it.event(EventType.TOOL_CALLED, attrs)
      it.record(MetricType.TOOL_CALL_DURATION, durationMs, attrs)
    }
    writeLog(
      logStreams,
      "\n<details><summary>Tool call <code>${call.name}</code> (${call.id}) — $outcome in ${"%.0f".format(durationMs)}ms</summary>\n\n" +
          "Arguments:\n\n${FENCE}json\n${call.input.toPrettyString().take(LOG_LIMIT)}\n$FENCE\n\n" +
          "Result:\n\n$FENCE\n${content.joinToString("\n") { (it as? MCPContent.Text)?.text.orEmpty() }.take(LOG_LIMIT)}\n$FENCE\n</details>\n"
    )
    return ModelSchema.ToolResultBlock(
      tool_use_id = call.id,
      name = call.name,
      content = content,
      structured = structured,
      is_error = result.isError,
    )
  }

  private fun recordLoop(rounds: Int, outcome: String, model: ChatModel) = safely {
    it.record(
      MetricType.TOOL_LOOP_ROUNDS, rounds.toDouble(),
      Attributes.of(MetricAttribute.OUTCOME(outcome), MetricAttribute.MODEL(model.modelId))
    )
  }

  private inline fun safely(block: (MetricsInterface) -> Unit) {
    try {
      block(metrics())
    } catch (e: Exception) {
      log.debug("Failed to record tool metrics", e)
    }
  }

  private fun writeLog(logStreams: List<BufferedOutputStream>, markdown: String) {
    val bytes = markdown.toByteArray()
    logStreams.forEach { stream ->
      try {
        stream.write(bytes)
        stream.flush()
      } catch (e: Exception) {
        log.debug("Failed to write tool log", e)
      }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(McpToolLoop::class.java)
    private const val FENCE = "\u0060\u0060\u0060"
    private const val LOG_LIMIT = 4000

    /** Metrics source; TODO wire to ServiceRouter (ServiceKey.METRICS) at startup. */
    @JvmStatic
    @Volatile
    var metricsProvider: () -> MetricsInterface = { NoOpMetrics }

    /**
     * Flattens a tool result into a single text item (media → placeholders, Phase 1) and
     * applies the size cap. Structured content is kept only if it fits the cap.
     */
    fun normalize(result: MCPToolResult, maxChars: Int): Pair<List<MCPContent>, JsonNode?> {
      val texts = result.content.map { c ->
        when (c) {
          is MCPContent.Text -> c.text
          is MCPContent.Image -> "[image: ${c.mimeType}, ${c.data.length * 3 / 4} bytes]"
          is MCPContent.Audio -> "[audio: ${c.mimeType}, ${c.data.length * 3 / 4} bytes]"
          is MCPContent.ResourceLink -> "[resource link: ${c.uri}${c.name?.let { " ($it)" } ?: ""}]"
          is MCPContent.Resource -> c.text ?: "[resource: ${c.uri}, ${c.mimeType ?: "binary"}]"
        }
      }.toMutableList()
      val structuredJson = result.structuredContent?.toString()
      if (texts.isEmpty() && structuredJson != null) texts += structuredJson
      if (texts.isEmpty()) texts += if (result.isError) "Tool failed without a message" else "(no output)"
      var joined = texts.joinToString("\n")
      if (joined.length > maxChars) {
        joined = joined.take(maxChars) + "\n…[truncated ${joined.length - maxChars} characters]"
      }
      val structured = result.structuredContent?.takeIf { (structuredJson?.length ?: 0) <= maxChars }
      return listOf(MCPContent.Text(joined)) to structured
    }

    fun addUsage(a: ModelSchema.Usage?, b: ModelSchema.Usage?): ModelSchema.Usage? {
      if (b == null) return a
      if (a == null) return b.copy(counts = b.counts.toMutableMap())
      val counts = a.counts.toMutableMap()
      b.counts.forEach { (k, v) -> counts[k] = (counts[k] ?: 0L) + v }
      return ModelSchema.Usage(counts = counts, total_tokens = a.total_tokens + b.total_tokens, cost = a.cost + b.cost)
    }
  }
}