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
    
    fun registerServer(config: MCPServerConfig) {
        servers[config.name] = config
        log.info("Registered MCP server: ${config.name}")
    }
    
    fun unregisterServer(name: String) {
        servers.remove(name)
        clients.remove(name)?.disconnect()
        log.info("Unregistered MCP server: $name")
    }
    
    fun getClient(name: String): MCPClient? {
        val config = servers[name] ?: return null
        
        return clients.computeIfAbsent(name) { MCPClient(config) }
    }
    
    fun listServers(): List<MCPServerConfig> = servers.values.toList()
    
    fun disconnectAll() {
        clients.values.forEach { it.disconnect() }
        clients.clear()
    }
}