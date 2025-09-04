package cognotik.actions.knowledge

import cognotik.actions.agent.toFile
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.apps.parse.DocumentRecord
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.embedding.EmbeddingClientBase
import com.simiacryptus.jopenai.embedding.OllamaEmbeddingClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.EmbeddingModel
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class EmbeddingSearchServer(
    val settings: EmbeddingSearchAction.SearchSettings,
    val api: ChatClientInterface,
    val model: EmbeddingModel,
    val files: List<VirtualFile?>,
    root: File
) : ApplicationServer(
    applicationName = "Embedding Search",
    path = "/embeddingSearch",
    showMenubar = false,
    root = root
), AutoCloseable {

    companion object {
        private val log = LoggerFactory.getLogger(EmbeddingSearchServer::class.java)
        private const val MAX_CONTEXT_LENGTH = 5000
        private const val SEARCH_TIMEOUT_SECONDS = 30L
        private const val MAX_CONCURRENT_SEARCHES = 4
        private const val EMBEDDING_CACHE_SIZE = 100
    }

    override val inputCnt = 0
    override val stickyInput = false

    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtMost(8)
    )
    private val embeddingCache = mutableMapOf<String, DoubleArray>()

    override fun close() {
        threadPool.shutdown()
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow()
            }
        } catch (e: InterruptedException) {
            threadPool.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    override fun newSession(user: User?, session: Session): SocketManager {
        val socketManager = super.newSession(user, session)
        val ui = (socketManager as ApplicationSocketManager).applicationInterface
        socketManager.pool.submit {
            val task = ui.newTask(true)
            try {
                executeSearch(task, ui)
            } catch (e: Exception) {
                log.error("Error during search", e)
                task.error(e)
            }
        }
        return socketManager
    }

    private fun executeSearch(task: SessionTask, ui: ApplicationInterface) {
        task.add(MarkdownUtil.renderMarkdown("# Embedding Search", ui = ui))
        task.add(MarkdownUtil.renderMarkdown("## Search Parameters", ui = ui))
        task.add(MarkdownUtil.renderMarkdown("""
            - **Positive queries:** ${settings.positiveQueries.joinToString(", ")}
            - **Negative queries:** ${settings.negativeQueries.joinToString(", ")}
            - **Distance type:** ${settings.distanceType}
            - **Results count:** ${settings.count}
            - **Min length:** ${settings.minLength}
            - **Required patterns:** ${settings.requiredRegexes.joinToString(", ")}
        """.trimIndent(), ui = ui))
        
        val indexFiles = files.filter { it?.name?.endsWith(".index.data") == true }
        if (indexFiles.isEmpty()) {
            task.add(MarkdownUtil.renderMarkdown("""
                ## ⚠️ No Index Files Found
                
                No `.index.data` files were found in the selected location.
                Please run Knowledge Indexing first to create searchable embeddings.
            """.trimIndent(), ui = ui))
            task.complete("No index files found")
            return
        }
        
        task.add(MarkdownUtil.renderMarkdown("""
            ## Searching ${indexFiles.size} index files...
            
            Creating query embeddings and searching for similar content...
        """.trimIndent(), ui = ui))

        try {
            val embeddingClient = OllamaEmbeddingClient("", workPool = threadPool)
            val searchResults = performEmbeddingSearch(embeddingClient, indexFiles)
            val formattedResults = formatSearchResults(searchResults)

            task.add(MarkdownUtil.renderMarkdown(formattedResults, ui = ui))
            task.complete("Search completed successfully")
        } catch (e: Exception) {
            log.error("Error during search process", e)
            task.add(MarkdownUtil.renderMarkdown("""
                ## ❌ Search Failed
                An error occurred during the search:
                ```
                ${e.message}
                ```
            """.trimIndent(), ui = ui))
            task.error(e)
        }
    }

    private fun performEmbeddingSearch(
        api: EmbeddingClientBase,
        indexFiles: List<VirtualFile?>
    ): List<EmbeddingSearchResult> {
        if (settings.positiveQueries.isEmpty()) {
            throw IllegalArgumentException("At least one positive query is required")
        }
        log.info("Creating embeddings for ${settings.positiveQueries.size} positive queries")
        // Create embeddings with progress tracking
        val totalQueries = settings.positiveQueries.size + settings.negativeQueries.size
        var processedQueries = 0
        
        
        fun getOrCreateEmbedding(query: String): DoubleArray? {
            return embeddingCache.getOrPut(query) {
                try {
                    processedQueries++
                    log.debug("Creating embedding ${processedQueries}/$totalQueries: $query")
                    api.createEmbedding(
                        ApiModel.EmbeddingRequest(
                            input = query,
                            model = model.modelName
                        ),
                        model
                    ).data.firstOrNull()?.embedding ?: return null
                } catch (e: Exception) {
                    log.error("Failed to create embedding for query: $query", e)
                    return null
                }
            }
        }
        val positiveEmbeddings = settings.positiveQueries.mapNotNull { getOrCreateEmbedding(it) }
        val negativeEmbeddings = settings.negativeQueries.mapNotNull { getOrCreateEmbedding(it) }


        if (positiveEmbeddings.isEmpty()) {
            throw IllegalStateException("Failed to create any positive embeddings")
        }

        val distanceType = settings.distanceType
        val minLength = settings.minLength
        val requiredRegexes = settings.requiredRegexes.map { Pattern.compile(it) }
        fun String.matchesAllRegexes() = requiredRegexes.all { regex -> regex.matcher(this).find() }
        
        log.info("Searching through ${indexFiles.size} index files")
        val semaphore = java.util.concurrent.Semaphore(MAX_CONCURRENT_SEARCHES)
        val searchResults = indexFiles
            .parallelStream()
            .flatMap { path ->
                val results = mutableListOf<EmbeddingSearchResult>()
                try {
                    semaphore.acquire()
                    val inputPath = path?.toFile?.toString() ?: throw IllegalArgumentException("Invalid file path")
                    log.debug("Reading index file: $inputPath")
                    var recordCount = 0
                    
                    DocumentRecord.readBinaryStream(inputPath) { record ->
                        recordCount++
                        if (recordCount % 100 == 0) {
                            log.debug("Processing record $recordCount from $inputPath")
                        }
                        record.vector?.let { vector ->
                            val positiveDistances = positiveEmbeddings.map { embedding ->
                                distanceType.distance(vector, embedding)
                            }
                            val negativeDistances = negativeEmbeddings.map { embedding ->
                                distanceType.distance(vector, embedding)
                            }
                            val overallDistance = if (negativeDistances.isEmpty()) {
                                positiveDistances.minOrNull() ?: Double.MAX_VALUE
                            } else {
                                val minPositive = positiveDistances.minOrNull() ?: Double.MAX_VALUE
                                val minNegative = negativeDistances.minOrNull() ?: Double.MIN_VALUE
                                if (minNegative == 0.0) Double.MAX_VALUE else minPositive / minNegative
                            }
                            val content = record.text ?: ""
                            if (content.length >= minLength && content.matchesAllRegexes()) {
                                results.add(EmbeddingSearchResult(
                                    file = path?.toNioPath()?.let { root.toPath().relativize(it).toString() } ?: "Unknown",
                                    record = record,
                                    distance = overallDistance
                                ))
                            }
                        }
                    }
                    log.debug("Processed $recordCount records from $inputPath, found ${results.size} matches")
                    results.stream()
                } catch (e: Exception) {
                    log.error("Failed to read index file: $path", e)
                    java.util.stream.Stream.empty<EmbeddingSearchResult>()
                } finally {
                    semaphore.release()
                }
            }
            .toList()

        return searchResults
            .sortedBy { it.distance }
            .take(settings.count)
    }

    private fun formatSearchResults(results: List<EmbeddingSearchResult>): String {
        return buildString {
            appendLine("# Embedding Search Results")
            appendLine()
            if (results.isEmpty()) {
                appendLine("No results found matching the search criteria.")
                return@buildString
            }

            results.forEachIndexed { index, result ->
                appendLine("## Result ${index + 1}")
                appendLine("* Distance: %.3f".format(result.distance))
                appendLine("* File: ${result.record.sourcePath}")
                appendLine(getContextSummary(result.record))
                appendLine("Metadata:\n```json\n${result.record.metadata}\n```")
                appendLine()
            }
        }
    }

    private fun getContextSummary(record: DocumentRecord): String {
        return try {
            val sourceFile = File(record.sourcePath)
            if (!sourceFile.exists()) {
                return "Source file not found: ${record.sourcePath}"
            }
            try {
                val objectMapper = ObjectMapper()
                val jsonNode = objectMapper.readTree(sourceFile)
                val contextNode = getNodeAtPath(jsonNode, record.jsonPath)
                buildString {
                    appendLine("```json")
                    appendLine(summarizeContext(contextNode, record.jsonPath, jsonNode))
                    appendLine("```")
                }
            } catch (e: JsonParseException) {
                buildString {
                    appendLine()
                    appendLine("**Source Path:** ${record.sourcePath}")
                    appendLine()
                    appendLine("**JSON Path:** ${record.jsonPath}")
                    appendLine()
                    appendLine("```text")
                    appendLine(record.text)
                    appendLine("```")
                    appendLine()
//                    appendLine("```text")
//                    appendLine(summarizeTextContext(sourceFile, record.jsonPath))
//                    appendLine("```")
                    appendLine()
                }
            }
        } catch (e: Exception) {
            log.warn("Error getting context summary for ${record.sourcePath}:${record.jsonPath}", e)
            "Context summary unavailable: ${e.message}"
        }
    }

    private fun summarizeTextContext(sourceFile: File, jsonPath: String): String {
        val linePattern = Regex("""content_list\[(\d+)]""")
        val match = linePattern.matchEntire(jsonPath)
        if (match != null) {
            val chunkIndex = match.groupValues[1].toIntOrNull() ?: return "Invalid line index in path: $jsonPath"
            // TODO: Parse document using parsing model to extract position and surrounding context
            TODO()
        } else {
            return "Unrecognized text path format: $jsonPath"
        }
    }

    private fun getNodeAtPath(jsonNode: JsonNode, path: String): JsonNode {
        if (path.isEmpty()) return jsonNode
        
        var currentNode = jsonNode

        val segments = path.split(".")
        
        for (segment in segments) {
            if (segment.isEmpty()) continue
            
            currentNode = when {
                segment.contains("[") -> {
                    val parts = segment.split("[", limit = 2)
                    if (parts.size != 2) {
                        log.warn("Invalid array notation in segment: $segment")
                        return currentNode
                    }
                    
                    val (arrayName, indexPart) = parts
                    val index = indexPart.substringBefore("]").toIntOrNull()
                    if (index == null) {
                        log.warn("Invalid index in path segment: $segment")
                        return currentNode
                    }
                    
                    val field = if (arrayName.isEmpty()) currentNode else currentNode.get(arrayName)
                    field?.get(index) ?: run {
                        log.warn("Array element not found for segment: $segment in path: $path")
                        return currentNode
                    }
                }

                else -> {
                    currentNode.get(segment) ?: run {
                        log.warn("Child not found for segment: $segment in path: $path")
                        return currentNode
                    }
                }
            }
        }
        return currentNode
    }

    private fun summarizeContext(node: JsonNode, path: String, jsonNode: JsonNode): String {
        var summary = mutableMapOf<String, Any>()

        node.fields().forEach { (key, value) ->
            if (value.isPrimitive()) {
                summary[key] = value.asText()
            }
        }

        val pathSegments = path.split(".")
        for (i in pathSegments.size - 1 downTo 1) {
            val parentPath = pathSegments.subList(0, i).joinToString(".")
            val parentNode = getNodeAtPath(jsonNode, parentPath)
            summary = mutableMapOf(
                pathSegments[i] to summary
            )
            parentNode.properties().forEach { (key, value) ->
                when {
                    value.isPrimitive() -> summary[key] = value.asText()
                    key == "entities" || key == "tags" || key == "metadata" -> summary[key] = value
                }
            }
        }
        return JsonUtil.toJson(summary)
    }

    data class EmbeddingSearchResult(
        val file: String,
        val record: DocumentRecord,
        val distance: Double
    )
}

private fun JsonNode.isPrimitive(): Boolean {
    return this.isNumber || this.isTextual || this.isBoolean
}