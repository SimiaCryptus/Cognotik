package com.simiacryptus.cognotik.plan.tools.code

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Semaphore

class LanguageServerTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: LanguageServerTaskExecutionConfigData?
) : AbstractTask<LanguageServerTask.LanguageServerTaskExecutionConfigData, LanguageServerTask.LanguageServerTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class LanguageServerTaskTypeConfig(
        task_type: String = LanguageServer.name,
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name,
        model = model,
    )

    class LanguageServerTaskExecutionConfigData(
        @Description("The LSP operation to perform. Supported: 'diagnostics', 'definition', 'references', 'hover'")
        val action: String? = null,
        @Description("The relative path of the file to analyze")
        val file: String? = null,
        @Description("The line number (0-indexed) for position-based requests (definition, hover, references)")
        val line: Int? = null,
        @Description("The character offset (0-indexed) for position-based requests")
        val character: Int? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = LanguageServer.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        val supportedLangs = serverCommands.keys.joinToString(", ")
        return """
            LanguageServer - Query code intelligence (LSP)
              * Use to find definitions, references, or check for syntax errors (diagnostics).
              * Supported extensions: $supportedLangs
              * Actions: 'diagnostics' (file-wide), 'definition' (specific pos), 'references' (specific pos), 'hover' (specific pos).
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        val mapper = ObjectMapper()

        try {
            val filePath = executionConfig?.file ?: throw IllegalArgumentException("File path is required")
            val action = executionConfig.action ?: throw IllegalArgumentException("Action is required")
            val file = root.resolve(filePath).toFile()

            if (!file.exists()) throw IllegalArgumentException("File does not exist: $filePath")

            val extension = file.extension
            val command = serverCommands[extension]
                ?: throw IllegalArgumentException("No Language Server configured for extension: .$extension")


            val executeLsp = {
                task.header("LSP Execution: $action", level = 3)
                val statusBuffer = task.add("Starting LSP for .$extension...")
                transcript?.write("# LSP Session\nCommand: ${command.joinToString(" ")}\nTarget: $filePath\nAction: $action\n\n".toByteArray())
                val process = ProcessBuilder(command)
                    .directory(root.toFile())
                    .start()
                val lsp = LspClient(process.inputStream, process.outputStream, mapper, transcript)
                try {
                    // 1. Initialize
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Initializing Server...")
                    task.update()
                    lsp.sendRequest("initialize", mapper.createObjectNode().apply {
                        put("processId", ProcessHandle.current().pid())
                        put("rootUri", root.toUri().toString())
                        putObject("capabilities").putObject("textDocument")
                    })
                    // 2. Open Document
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Opening Document...")
                    task.update()
                    val fileUri = file.toURI().toString()
                    lsp.sendNotification("textDocument/didOpen", mapper.createObjectNode().apply {
                        putObject("textDocument").apply {
                            put("uri", fileUri)
                            put("languageId", extension)
                            put("version", 1)
                            put("text", file.readText())
                        }
                    })
                    // 3. Perform Action
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Executing $action...")
                    task.update()
                    val result = when (action.lowercase()) {
                        "diagnostics" -> {
                            // Diagnostics are usually pushed as notifications after opening.
                            // We wait a brief moment for them.
                            Thread.sleep(2000)
                            // In a real persistent client, we'd listen.
                            // For a one-shot task, we might miss them if not immediate,
                            // but many LSPs send them right after didOpen.
                            "Diagnostics are pushed asynchronously. Check transcript for 'textDocument/publishDiagnostics'."
                        }

                        "definition" -> {
                            validatePosition()
                            val params = positionParams(mapper, fileUri)
                            val response = lsp.sendRequest("textDocument/definition", params)
                            formatLocationResponse(response, "Definition")
                        }

                        "references" -> {
                            validatePosition()
                            val params = positionParams(mapper, fileUri).apply {
                                putObject("context").put("includeDeclaration", true)
                            }
                            val response = lsp.sendRequest("textDocument/references", params)
                            formatLocationResponse(response, "References")
                        }

                        "hover" -> {
                            validatePosition()
                            val params = positionParams(mapper, fileUri)
                            val response = lsp.sendRequest("textDocument/hover", params)
                            response?.get("contents")?.toString() ?: "No hover info"
                        }

                        else -> throw IllegalArgumentException("Unknown action: $action")
                    }
                    // 4. Shutdown
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Shutting down...")
                    task.update()
                    lsp.sendRequest("shutdown", null)
                    lsp.sendNotification("exit", null)
                    val finalOutput = "LSP Action '$action' completed.\nResult:\n$result"
                    transcript?.write("\n## Final Result\n$finalOutput\n".toByteArray())
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("<b>LSP Action Completed</b>")
                    task.update()
                    finalOutput
                } catch (e: Exception) {
                    log.error("LSP Error", e)
                    transcript?.write("\n## Error\n${e.message}\n".toByteArray())
                    task.error(e)
                    throw e
                } finally {
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }
            if (orchestrationConfig.autoFix) {
                resultFn(executeLsp())
            } else {
                val semaphore = Semaphore(0)
                task.add("Ready to run LSP action '$action' on '$filePath'.")
                task.add(task.ui.hrefLink("Run LSP Action", "btn btn-primary") {
                    try {
                        resultFn(executeLsp())
                    } catch (e: Exception) {
                        task.error(e)
                        resultFn("Error: ${e.message}")
                    } finally {
                        semaphore.release()
                    }
                })
                semaphore.acquire()
            }

        } catch (e: Exception) {
            log.warn("Task Failed", e)
            task.error(e)
            resultFn("Error: ${e.message}")
        } finally {
            transcript?.close()
        }
    }

    private fun validatePosition() {
        if (executionConfig?.line == null || executionConfig.character == null) {
            throw IllegalArgumentException("Line and Character are required for this action")
        }
    }

    private fun positionParams(mapper: ObjectMapper, uri: String): ObjectNode {
        return mapper.createObjectNode().apply {
            putObject("textDocument").put("uri", uri)
            putObject("position").apply {
                put("line", executionConfig!!.line!!)
                put("character", executionConfig.character!!)
            }
        }
    }

    private fun formatLocationResponse(json: JsonNode?, title: String): String {
        if (json == null || json.isNull) return "No $title found."

        val locations = if (json.isArray) json else listOf(json)
        return locations.joinToString("\n") { loc ->
            val uri = loc.get("uri").asText()
            val range = loc.get("range")
            val start = range.get("start")
            val line = start.get("line").asInt()
            val char = start.get("character").asInt()
            // Convert URI back to relative path if possible
            val path = if (uri.startsWith(root.toUri().toString())) {
                uri.removePrefix(root.toUri().toString())
            } else uri
            "- $path:$line:$char"
        }
    }

    /**
     * Minimal JSON-RPC Client for one-shot tasks
     */
    private class LspClient(
        val input: InputStream,
        val output: OutputStream,
        val mapper: ObjectMapper,
        val transcript: OutputStream?
    ) {
        private var idCounter = 0

        fun sendRequest(method: String, params: JsonNode?): JsonNode? {
            val id = idCounter++
            val message = mapper.createObjectNode().apply {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                if (params != null) set<JsonNode>("params", params)
            }
            write(message)

            // Read until we get the response with matching ID
            while (true) {
                val response = read() ?: throw RuntimeException("Stream ended before response")
                if (response.has("id") && response.get("id").asInt() == id) {
                    if (response.has("error")) {
                        throw RuntimeException("LSP Error: ${response.get("error")}")
                    }
                    return response.get("result")
                }
                // Log notifications while waiting
                if (!response.has("id")) {
                    transcript?.write("Received Notification: ${response.get("method")}\n".toByteArray())
                }
            }
        }

        fun sendNotification(method: String, params: JsonNode?) {
            val message = mapper.createObjectNode().apply {
                put("jsonrpc", "2.0")
                put("method", method)
                if (params != null) set<JsonNode>("params", params)
            }
            write(message)
        }

        private fun write(json: JsonNode) {
            val str = mapper.writeValueAsString(json)
            val content = "Content-Length: ${str.toByteArray().size}\r\n\r\n$str"
            output.write(content.toByteArray())
            output.flush()
            transcript?.write("--> $str\n".toByteArray())
        }

        private fun read(): JsonNode? {
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine() ?: return null
                if (line.isEmpty()) break
                val parts = line.split(": ", limit = 2)
                if (parts.size == 2) headers[parts[0]] = parts[1]
            }

            val length = headers["Content-Length"]?.toIntOrNull()
                ?: throw RuntimeException("Missing Content-Length header")

            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val c = input.read(bytes, read, length - read)
                if (c == -1) throw RuntimeException("Unexpected EOF")
                read += c
            }

            val str = String(bytes)
            transcript?.write("<-- $str\n".toByteArray())
            return mapper.readTree(str)
        }

        private fun readLine(): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (bytes.size() > 0) bytes.toString() else null
                if (b == '\n'.code) {
                    val str = bytes.toString()
                    return if (str.endsWith('\r')) str.dropLast(1) else str
                }
                bytes.write(b)
            }
        }
    }

    val serverCommands: Map<String, List<String>>
        get() {
            val tools = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
            val executables: List<String>? = tools.flatMap { it.absoluteExecutablePaths() }.distinct().sorted()
            return mapOf(
                "py" to listOf("pylsp"),
                "js" to listOf("typescript-language-server", "--stdio"),
                "ts" to listOf("typescript-language-server", "--stdio"),
                "kt" to listOf("kotlin-language-server"),
                "java" to listOf("jdtls"),
                "c" to listOf("clangd"),
                "cpp" to listOf("clangd"),
                "go" to listOf("gopls"),
                "rs" to listOf("rust-analyzer"),
                "sh" to listOf("bash-language-server", "start"),
                "tex" to listOf("texlab"),
                "yaml" to listOf("yaml-language-server", "--stdio"),
                "dockerfile" to listOf("docker-langserver", "--stdio")
            ).filter { (_, cmd) -> executables?.contains(cmd[0]) ?: false }
        }

    companion object {
        private val log = LoggerFactory.getLogger(LanguageServerTask::class.java)
        val LanguageServer = TaskType(
            "LanguageServer",
            "File",
            LanguageServerTaskExecutionConfigData::class.java,
            LanguageServerTaskTypeConfig::class.java,
            "Interact with Language Servers (LSP)",
            """
                Provides code intelligence capabilities via the Language Server Protocol.
                <ul>
                    <li><b>Definition:</b> Locate where a symbol is defined.</li>
                    <li><b>References:</b> Find all usages of a symbol.</li>
                    <li><b>Diagnostics:</b> Check files for syntax errors and warnings.</li>
                    <li><b>Hover:</b> Get documentation or type information at a specific position.</li>
                </ul>
                Requires language servers (e.g., pylsp, typescript-language-server) to be installed in the environment.
            """.trimIndent()
        )
    }
}