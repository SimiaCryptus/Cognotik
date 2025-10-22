package com.simiacryptus.cognotik.plan.tools.knowledge

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.apps.parse.DocumentRecord
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.embedding.DistanceType
import com.simiacryptus.cognotik.embedding.EmbedderClient
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingModels
import com.simiacryptus.cognotik.plan.AbstractTask
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.streams.asSequence

class VectorSearchTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: VectorSearchTaskExecutionConfigData?
) : AbstractTask<VectorSearchTask.VectorSearchTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
    class VectorSearchTaskExecutionConfigData(
        @Description("The positive search queries to look for in the embeddings")
        val positive_queries: List<String>,
        @Description("The negative search queries to avoid in the embeddings")
        val negative_queries: List<String> = emptyList(),
        @Description("The distance type to use for comparing embeddings (Euclidean, Manhattan, or Cosine)")
        val distance_type: DistanceType = DistanceType.Cosine,
        @Description("The number of top results to return")
        val count: Int = 5,
        @Description("The minimum length of the content to be considered")
        val min_length: Int = 0,
        @Description("List of regex patterns that must be present in the content")
        val required_regexes: List<String> = emptyList(),
        val model: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = VectorSearch.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (positive_queries.isEmpty()) {
                return "At least one positive query is required"
            }
            if (count <= 0) {
                return "Count must be greater than 0"
            }
            if (min_length < 0) {
                return "Minimum length cannot be negative"
            }
            required_regexes.forEach { regex ->
                try {
                    Pattern.compile(regex)
                } catch (e: Exception) {
                    return "Invalid regex pattern: $regex - ${e.message}"
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment() = """
VectorSearch - Search for similar embeddings in index files and provide top results
    ** Specify the positive search queries
    ** Optionally specify negative search queries
    ** Specify the distance type (Euclidean, Manhattan, or Cosine)
    ** Specify the number of top results to return
    """.trim()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val threadPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceAtMost(8)
        )
        try {
            val searchResults = performEmbeddingSearch(
            )
            val formattedResults = formatSearchResults(searchResults)
            task.add(MarkdownUtil.renderMarkdown(formattedResults, ui = task.ui))
            resultFn(formattedResults)
        } finally {
            threadPool.shutdown()
            try {
                if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow()
                }
            } catch (_: InterruptedException) {
                threadPool.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun performEmbeddingSearch(): List<EmbeddingSearchResult> {
        // Validate queries first
        executionConfig?.validate()?.let { errorMessage ->
            throw ValidatedObject.ValidationError(errorMessage, executionConfig)
        }
        if (executionConfig?.positive_queries?.isEmpty() != false) {
            throw ValidatedObject.ValidationError("At least one positive query is required", executionConfig!!)
        }
        
        // Create embeddings with retry logic
        fun createEmbeddingWithRetry(query: String, maxRetries: Int = 3): DoubleArray? {
            repeat(maxRetries) { attempt ->
                try {
                    return embedderClient(executionConfig.model).embed(query)
                } catch (e: Exception) {
                    if (attempt == maxRetries - 1) {
                        log.error("Failed to create embedding for query after $maxRetries attempts: $query", e)
                        return null
                    }
                    Thread.sleep(1000L * (attempt + 1))
                }
            }
            return null
        }

        val positiveEmbeddings = executionConfig.positive_queries.map { query ->
            createEmbeddingWithRetry(query)
        }

        val negativeEmbeddings = executionConfig.negative_queries.map { query ->
            createEmbeddingWithRetry(query)
        }

        if (positiveEmbeddings.filterNotNull().isEmpty()) {
            throw IllegalStateException("Failed to create any positive embeddings")
        }
        val filtered = Files.walk(root).asSequence()
            .filter { path ->
                path.toString().endsWith(".index.data")
            }.toList().toTypedArray()
        val minLength = executionConfig.min_length
        val requiredRegexes = executionConfig.required_regexes.map { Pattern.compile(it) }
        fun String.matchesAllRegexes(): Boolean {
            return requiredRegexes.all { regex -> regex.matcher(this).find() }
        }

        val searchResults = filtered
            .flatMap { path ->
                val results = mutableListOf<EmbeddingSearchResult>()
                try {
                    DocumentRecord.readBinaryStream(path.toString()) { record ->
                        record.vector?.let { vector ->
                            val positiveDistances = positiveEmbeddings.filterNotNull().map { embedding ->
                                executionConfig.distance_type.distance(vector, embedding)
                            }
                            val negativeDistances = negativeEmbeddings.filterNotNull().map { embedding ->
                                executionConfig.distance_type.distance(vector, embedding)
                            }
                            val overallDistance = if (negativeDistances.isEmpty()) {
                                positiveDistances.minOrNull() ?: Double.MAX_VALUE
                            } else {
                                (positiveDistances.minOrNull() ?: Double.MAX_VALUE) / (negativeDistances.minOrNull()
                                    ?: Double.MIN_VALUE)
                            }
                            val content = record.text ?: ""
                            if (content.length >= minLength && content.matchesAllRegexes()) {
                                results.add(
                                    EmbeddingSearchResult(
                                        file = root.relativize(path).toString(),
                                        record = record,
                                        distance = overallDistance
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Failed to search in file: $path", e)
                }
                results
            }
            .toList()
        return searchResults
            .sortedBy { it.distance }
            .take(executionConfig.count)
    }

    private fun formatSearchResults(results: List<EmbeddingSearchResult>): String {
        return buildString {
            appendLine("# Embedding Search Results")
            appendLine()
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

    private fun getNodeAtPath(jsonNode: JsonNode, path: String): JsonNode {
        var currentNode = jsonNode

        path.split(".").forEach { segment ->
            currentNode = when {
                segment.contains("[") -> {
                    val (arrayName, indexPart) = segment.split("[", limit = 2)
                    val index = indexPart.substringBefore("]").toIntOrNull() ?: run {
                        log.warn("Invalid index in path segment: $segment")
                        return currentNode
                    }
                    val field = currentNode.get(arrayName)
                    val child = field?.get(index)
                    if (child == null) {
                        log.warn("Child not found for segment: $segment in path: $path")
                        return currentNode
                    }
                    child
                }

                else -> {
                    val child = currentNode.get(segment)
                    if (child == null) {
                        log.warn("Child not found for segment: $segment in path: $path")
                        return currentNode
                    }
                    child
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
            parentNode.fields().forEach { (key, value) ->
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

    companion object {
        private val log = LoggerFactory.getLogger(VectorSearchTask::class.java)

        val VectorSearch = TaskType(
            "VectorSearch",
            VectorSearchTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Perform semantic search using AI embeddings",
            """
                      Performs semantic search using AI embeddings across indexed content.
                      <ul>
                        <li>Uses OpenAI embeddings for semantic matching</li>
                        <li>Supports positive and negative search queries</li>
                        <li>Configurable similarity metrics and thresholds</li>
                        <li>Regular expression filtering capabilities</li>
                        <li>Returns ranked results with context</li>
                      </ul>
                    """
        )

        var embedderClient : (String?) -> EmbedderClient = {
            val modelName = it ?: OllamaEmbeddingModels.NomicEmbedText.modelName!!
            val embeddingModel = EmbeddingModel.values()[modelName]
                ?: throw IllegalArgumentException("Unknown embedding model: $modelName")
            embeddingModel.instance()
        }
    }
}

private fun JsonNode.isPrimitive(): Boolean {
    return this.isNumber || this.isTextual || this.isBoolean
}