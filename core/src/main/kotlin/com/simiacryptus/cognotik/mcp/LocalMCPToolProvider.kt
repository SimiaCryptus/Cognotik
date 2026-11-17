package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.platform.model.mcp.MCPCallContext
import com.simiacryptus.cognotik.platform.model.mcp.MCPException
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolProvider
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolResult
import org.slf4j.LoggerFactory

/**
 * In-process tool provider: direct dispatch, no JSON-RPC. The default for app-supplied tools.
 * Unknown tools raise [MCPException] (INVALID_PARAMS); tool exceptions become `isError` results.
 */
class LocalMCPToolProvider(
  tools: List<MCPTool>,
  override val name: String = "local",
) : MCPToolProvider {

  private val tools: List<MCPTool> = tools.toList()
  private val byName: Map<String, MCPTool> = this.tools.associateBy { it.definition.name }

  init {
    require(byName.size == this.tools.size) {
      "Duplicate tool names in provider '$name': " +
          this.tools.groupBy { it.definition.name }.filterValues { it.size > 1 }.keys
    }
  }

  override fun listTools(): List<MCPToolDefinition> = tools.map { it.definition }

  override fun callTool(name: String, arguments: JsonNode, context: MCPCallContext): MCPToolResult {
    val tool = byName[name] ?: throw MCPException(MCPException.INVALID_PARAMS, "Unknown tool: $name")
    return try {
      tool.call(arguments, context)
    } catch (e: MCPException) {
      throw e
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw e
    } catch (e: Exception) {
      log.warn("Local tool '$name' failed", e)
      MCPToolResult.error("Tool '$name' failed: ${e.javaClass.simpleName}: ${e.message}")
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(LocalMCPToolProvider::class.java)
  }
}