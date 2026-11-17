package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.platform.model.mcp.MCPContent
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolResult
import com.simiacryptus.cognotik.util.JsonUtil
import org.slf4j.LoggerFactory

/**
 * Minimal MCP / JSON-RPC 2.0 wire codec (tools subset only).
 * Phase 1 hand-rolled layer; see mcp_feature_status.md F-3 for the SDK migration.
 */
object McpJson {
  private val log = LoggerFactory.getLogger(McpJson::class.java)

  const val JSONRPC_VERSION = "2.0"
  const val LATEST_PROTOCOL_VERSION = "2025-06-18"
  val SUPPORTED_PROTOCOL_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")

  val mapper: ObjectMapper by lazy { JsonUtil.objectMapper() }

  fun obj(): ObjectNode = mapper.createObjectNode()

  /** Compact single-line JSON (JSON string escaping guarantees no raw newlines). */
  fun encode(node: JsonNode): String = mapper.writeValueAsString(node)

  fun decode(line: String): JsonNode = mapper.readTree(line)

  // ---------------- JSON-RPC envelopes ----------------

  fun request(id: Long, method: String, params: JsonNode? = null): ObjectNode = obj().apply {
    put("jsonrpc", JSONRPC_VERSION)
    put("id", id)
    put("method", method)
    params?.let { set<JsonNode>("params", it) }
  }

  fun notification(method: String, params: JsonNode? = null): ObjectNode = obj().apply {
    put("jsonrpc", JSONRPC_VERSION)
    put("method", method)
    params?.let { set<JsonNode>("params", it) }
  }

  fun result(id: JsonNode, result: JsonNode): ObjectNode = obj().apply {
    put("jsonrpc", JSONRPC_VERSION)
    set<JsonNode>("id", id)
    set<JsonNode>("result", result)
  }

  fun error(id: JsonNode?, code: Int, message: String, data: JsonNode? = null): ObjectNode = obj().apply {
    put("jsonrpc", JSONRPC_VERSION)
    set<JsonNode>("id", id ?: NullNode.instance)
    set<JsonNode>("error", obj().apply {
      put("code", code)
      put("message", message)
      data?.let { set<JsonNode>("data", it) }
    })
  }

  // ---------------- Tools ----------------

  fun definitionToJson(d: MCPToolDefinition): ObjectNode = obj().apply {
    put("name", d.name)
    d.title?.let { put("title", it) }
    put("description", d.description)
    set<JsonNode>("inputSchema", d.inputSchema)
    d.outputSchema?.let { set<JsonNode>("outputSchema", it) }
    d.annotations?.let { set<JsonNode>("annotations", mapper.valueToTree<JsonNode>(it)) }
  }

  @Suppress("UNCHECKED_CAST")
  fun definitionFromJson(n: JsonNode): MCPToolDefinition = MCPToolDefinition(
    name = n.path("name").asText(),
    description = n.path("description").asText(""),
    title = n.get("title")?.takeIf { it.isTextual }?.asText(),
    inputSchema = n.get("inputSchema")?.takeIf { it.isObject } ?: obj().put("type", "object"),
    outputSchema = n.get("outputSchema")?.takeIf { it.isObject },
    annotations = n.get("annotations")?.takeIf { it.isObject }
      ?.let { mapper.convertValue(it, Map::class.java) as Map<String, Any?> },
  )

  fun resultToJson(r: MCPToolResult): ObjectNode = obj().apply {
    set<JsonNode>("content", mapper.createArrayNode().apply { r.content.forEach { add(contentToJson(it)) } })
    r.structuredContent?.let { set<JsonNode>("structuredContent", it) }
    put("isError", r.isError)
  }

  fun resultFromJson(n: JsonNode): MCPToolResult = MCPToolResult(
    content = n.path("content").mapNotNull { contentFromJson(it) },
    structuredContent = n.get("structuredContent")?.takeUnless { it.isNull },
    isError = n.path("isError").asBoolean(false),
  )

  fun contentToJson(c: MCPContent): ObjectNode = when (c) {
    is MCPContent.Text -> obj().put("type", "text").put("text", c.text)
    is MCPContent.Image -> obj().put("type", "image").put("data", c.data).put("mimeType", c.mimeType)
    is MCPContent.Audio -> obj().put("type", "audio").put("data", c.data).put("mimeType", c.mimeType)
    is MCPContent.ResourceLink -> obj().put("type", "resource_link").put("uri", c.uri).apply {
      c.name?.let { put("name", it) }
      c.mimeType?.let { put("mimeType", it) }
    }

    is MCPContent.Resource -> obj().put("type", "resource").apply {
      set<JsonNode>("resource", obj().put("uri", c.uri).apply {
        c.text?.let { put("text", it) }
        c.blob?.let { put("blob", it) }
        c.mimeType?.let { put("mimeType", it) }
      })
    }
  }

  fun contentFromJson(n: JsonNode): MCPContent? = when (n.path("type").asText()) {
    "text" -> MCPContent.Text(n.path("text").asText(""))
    "image" -> MCPContent.Image(n.path("data").asText(""), n.path("mimeType").asText("application/octet-stream"))
    "audio" -> MCPContent.Audio(n.path("data").asText(""), n.path("mimeType").asText("application/octet-stream"))
    "resource_link" -> MCPContent.ResourceLink(
      uri = n.path("uri").asText(),
      name = n.get("name")?.takeIf { it.isTextual }?.asText(),
      mimeType = n.get("mimeType")?.takeIf { it.isTextual }?.asText(),
    )

    "resource" -> n.path("resource").let { r ->
      MCPContent.Resource(
        uri = r.path("uri").asText(),
        text = r.get("text")?.takeIf { it.isTextual }?.asText(),
        blob = r.get("blob")?.takeIf { it.isTextual }?.asText(),
        mimeType = r.get("mimeType")?.takeIf { it.isTextual }?.asText(),
      )
    }

    else -> {
      log.debug("Ignoring unsupported MCP content type: {}", n.path("type").asText())
      null
    }
  }
}