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