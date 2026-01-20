# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/MCPClient.kt

```
package com.simiacryptus.cognotik.mcp

import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Client for communicating with MCP (Model Context Protocol) servers
 */
class MCPClient(
    private val serverConfig: MCPServerConfig
) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var messageId = 0

    data class MCPTool(
        val name: String,
        val description: String,
        val inputSchema: Map<String, Any>
    )

    fun isConnected(): Boolean = process?.isAlive == true

    fun connect() {
        if (isConnected()) {
            log.warn("Already connected to MCP server")
            return
        }

        log.info("Starting MCP server: ${serverConfig.command}")

        val processBuilder = ProcessBuilder(serverConfig.command)
        serverConfig.env?.let { processBuilder.environment().putAll(it) }

        process = processBuilder.start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        reader = BufferedReader(InputStreamReader(process!!.inputStream))

        // Initialize the connection
        sendInitialize()

        log.info("Connected to MCP server: ${serverConfig.name}")
    }

    fun disconnect() {
        try {
            writer?.close()
            reader?.close()
            process?.destroy()
            process?.waitFor(5, TimeUnit.SECONDS)
            if (process?.isAlive == true) {
                process?.destroyForcibly()
            }
        } catch (e: Exception) {
            log.error("Error disconnecting from MCP server", e)
        } finally {
            process = null
            writer = null
            reader = null
        }
    }

    fun listTools(): List<MCPTool> {
        val response = sendRequest("tools/list", emptyMap())
        val tools = response["tools"] as? List<*> ?: emptyList<Any>()

        return tools.mapNotNull { tool ->
            val toolMap = tool as? Map<*, *> ?: return@mapNotNull null
            MCPTool(
                name = toolMap["name"] as? String ?: return@mapNotNull null,
                description = toolMap["description"] as? String ?: "",
                inputSchema = toolMap["inputSchema"] as? Map<String, Any> ?: emptyMap()
            )
        }
    }

    fun executeTool(
        toolName: String,
        arguments: Map<String, Any>,
        timeout: Long,
        unit: TimeUnit
    ): Any? {
        val params = mapOf(
            "name" to toolName,
            "arguments" to arguments
        )

        val future = executor.submit(Callable {
            sendRequest("tools/call", params)
        })

        return try {
            val response = future.get(timeout, unit)
            response["content"]
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw TimeoutException("Tool execution timed out after $timeout ${unit.name.lowercase()}")
        }
    }

    private fun sendInitialize() {
        val params = mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf(
                "tools" to emptyMap<String, Any>()
            ),
            "clientInfo" to mapOf(
                "name" to "cognotik",
                "version" to "1.0.0"
            )
        )

        sendRequest("initialize", params)
    }

    private fun sendRequest(method: String, params: Map<String, Any>): Map<String, Any> {
        if (!isConnected()) {
            throw IllegalStateException("Not connected to MCP server")
        }

        val id = ++messageId
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to method,
            "params" to params
        )

        val requestJson = JsonUtil.toJson(request)
        log.debug("Sending MCP request: $requestJson")

        writer!!.write(requestJson)
        writer!!.newLine()
        writer!!.flush()

        // Read response
        val responseLine = reader!!.readLine()
            ?: throw IllegalStateException("No response from MCP server")

        log.debug("Received MCP response: $responseLine")

        val response = JsonUtil.fromJson<Map<String, Any>>(responseLine, Map::class.java)

        if (response.containsKey("error")) {
            val error = response["error"] as Map<*, *>
            throw RuntimeException("MCP error: ${error["message"]}")
        }

        return response["result"] as? Map<String, Any> ?: emptyMap()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MCPClient::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/MCPServerConfig.kt

```
package com.simiacryptus.cognotik.mcp

/**
 * Configuration for an MCP server
 */
data class MCPServerConfig(
    val name: String,
    val command: List<String>,
    val env: Map<String, String>? = null,
    val description: String? = null
)
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/MCPServerRegistry.kt

```
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
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new `README.md` file for the MCP integration package, based on the provided source code and documentation requirements.

### webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/README.md
```markdown
# MCP (Model Context Protocol) Integration

This package provides the core infrastructure for integrating with Model Context Protocol (MCP) servers. MCP is a standardized protocol that allows AI applications to discover and interact with tools and data sources provided by external processes.

## Components

### [MCPClient](MCPClient.kt)
The `MCPClient` is the primary interface for communicating with an MCP server. It manages a child process and communicates via JSON-RPC over standard input/output.

- **Lifecycle Management**: Handles starting the server process, performing the `initialize` handshake, and graceful shutdown.
- **Tool Discovery**: Implements `tools/list` to retrieve available tools and their JSON Schema definitions.
- **Tool Execution**: Implements `tools/call` for executing specific tools with arguments, including support for execution timeouts.
- **Thread Safety**: Uses a single-thread executor for managing request/response cycles.

### [MCPServerConfig](MCPServerConfig.kt)
A data class used to define the launch configuration for an MCP server.
- `name`: A unique identifier for the server instance.
- `command`: The executable and arguments used to start the server process.
- `env`: Optional environment variables required by the server.
- `description`: An optional human-readable description of the server's capabilities.

### [MCPServerRegistry](MCPServerRegistry.kt)
A singleton registry that manages the mapping between server configurations and active `MCPClient` instances. It ensures that clients are instantiated lazily and reused based on their server name.

## Protocol Specifications
- **JSON-RPC Version**: 2.0
- **MCP Protocol Version**: `2024-11-05`
- **Transport**: Standard I/O (Process Pipes)

## Usage Example

```kotlin
val config = MCPServerConfig(
    name = "filesystem",
    command = listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", "/path/to/workspace")
)

val client = MCPClient(config)
try {
    client.connect()
    
    // List available tools
    val tools = client.listTools()
    
    // Execute a tool
    val result = client.executeTool(
        toolName = "read_file",
        arguments = mapOf("path" to "config.json"),
        timeout = 5,
        unit = TimeUnit.SECONDS
    )
    
    println("Tool Result: $result")
} finally {
    client.disconnect()
}
```

## Implementation Details
- **Serialization**: Uses `JsonUtil` for converting between Kotlin objects and JSON-RPC messages.
- **Error Handling**: Maps MCP protocol errors to standard `RuntimeException` with descriptive messages.
- **Process Management**: Ensures server processes are terminated forcibly if they do not shut down gracefully within a timeout period.
```

### Summary of Changes
- Created a new `README.md` in `webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/`.
- Documented the three main classes: `MCPClient`, `MCPServerConfig`, and `MCPServerRegistry`.
- Included protocol details (version `2024-11-05`, JSON-RPC 2.0, Stdio transport).
- Provided a code example demonstrating how to configure, connect, and use the client.
- Added implementation notes regarding process management and serialization.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/mcp/README.md</a> Updated
