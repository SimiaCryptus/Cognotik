package com.simiacryptus.cognotik.mcp

import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for managing MCP server configurations and clients
 */
object MCPServerRegistry {
    private val log: Logger = LoggerFactory.getLogger(MCPServerRegistry::class.java)
    private val servers = ConcurrentHashMap<String, MCPServerConfig>()
    private val clients = ConcurrentHashMap<String, MCPClient>()

    fun getClient(name: String): MCPClient? {
        val config = servers[name] ?: return null

        return clients.computeIfAbsent(name) { MCPClient(config) }
    }

}