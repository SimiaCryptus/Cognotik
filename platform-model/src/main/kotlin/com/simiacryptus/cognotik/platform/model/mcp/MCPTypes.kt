package com.simiacryptus.cognotik.platform.model.mcp

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import java.time.Duration

/** MCP `Tool` definition (as returned by tools/list). [inputSchema] is a JSON Schema object. */
data class MCPToolDefinition(
  val name: String,
  val description: String = "",
  val title: String? = null,
  val inputSchema: JsonNode,
  val outputSchema: JsonNode? = null,
  /** MCP tool annotations (readOnlyHint, destructiveHint, ...). */
  val annotations: Map<String, Any?>? = null,
)

/**
 * MCP CallToolResult. Tool *execution* failures are reported with [isError] = true
 * (and fed back to the model); JSON-RPC protocol failures are thrown as [MCPException].
 */
data class MCPToolResult(
  val content: List<MCPContent> = emptyList(),
  val structuredContent: JsonNode? = null,
  val isError: Boolean = false,
) {
  companion object {
    fun text(text: String) = MCPToolResult(content = listOf(MCPContent.Text(text)))
    fun error(message: String) = MCPToolResult(content = listOf(MCPContent.Text(message)), isError = true)
  }
}

/** MCP content block. JSON form matches the MCP wire `type` discriminator. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(value = MCPContent.Text::class, name = "text"),
  JsonSubTypes.Type(value = MCPContent.Image::class, name = "image"),
  JsonSubTypes.Type(value = MCPContent.Audio::class, name = "audio"),
  JsonSubTypes.Type(value = MCPContent.ResourceLink::class, name = "resource_link"),
  JsonSubTypes.Type(value = MCPContent.Resource::class, name = "resource"),
)
sealed interface MCPContent {
  data class Text(val text: String) : MCPContent
  data class Image(val data: String, val mimeType: String) : MCPContent
  data class Audio(val data: String, val mimeType: String) : MCPContent
  data class ResourceLink(val uri: String, val name: String? = null, val mimeType: String? = null) : MCPContent
  data class Resource(
    val uri: String,
    val text: String? = null,
    val blob: String? = null,
    val mimeType: String? = null,
  ) : MCPContent
}

/** Context handed to every tool call. Phase 2 adds a cancellation token and progress sink. */
data class MCPCallContext(
  val session: Session,
  val user: User?,
  val callId: String,
)

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

  /** Blocking in Phase 1. Execution failures should be returned as `isError` results. */
  fun callTool(name: String, arguments: JsonNode, context: MCPCallContext): MCPToolResult

  override fun close() {}
}

/** JSON-RPC level failure (protocol error), distinct from a tool's `isError` result. */
class MCPException(
  val code: Int,
  message: String,
  val data: JsonNode? = null,
  cause: Throwable? = null,
) : RuntimeException(message, cause) {
  companion object {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    /** Implementation-defined: request timed out or the connection was closed. */
    const val REQUEST_TIMEOUT = -32001
  }
}

/**
 * Options for [McpToolLoop].
 *
 * @param toolTimeout reserved for Phase 2 (not enforced by the sequential Phase 1 loop)
 * @param allowedTools optional filter; tools rejected here are never exposed to the model
 */
data class ToolLoopOptions(
  val maxRounds: Int = 16,
  val toolTimeout: Duration = Duration.ofSeconds(120),
  val maxResultChars: Int = 100_000,
  val allowedTools: ((MCPToolDefinition) -> Boolean)? = null,
)