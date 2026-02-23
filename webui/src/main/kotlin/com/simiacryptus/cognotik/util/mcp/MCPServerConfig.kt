package com.simiacryptus.cognotik.util.mcp

/**
 * Configuration for an MCP server
 */
data class MCPServerConfig(
    val name: String,
    val command: List<String>,
    val env: Map<String, String>? = null,
    val description: String? = null
)