package com.simiacryptus.cognotik.plan.tools.knowledge

import com.simiacryptus.cognotik.apps.parse.DocumentRecord.Companion.indexJsonFile
import com.simiacryptus.cognotik.apps.parse.ProgressState
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingModels
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KnowledgeIndexingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: KnowledgeIndexingTaskExecutionConfigData?
) : AbstractTask<KnowledgeIndexingTask.KnowledgeIndexingTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class KnowledgeIndexingTaskExecutionConfigData(
        @Description("The file paths to process and index")
        val file_paths: List<String>,
        @Description("The type of parsing to use: 'document' or 'code'")
        val parsing_type: String? = "document",
        @Description("The chunk size for splitting documents (0.0 to 1.0)")
        val chunk_size: Double? = 0.1,
        @Description("The embedding model to use for indexing")
        val embedding_model: String? = OllamaEmbeddingModels.NomicEmbedText.modelName,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = TaskType.KnowledgeIndexing.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
      KnowledgeIndexing - Process and index files for semantic search
        ** Specify the file paths to process
        ** Specify the parsing type (document or code)
        ** Optionally specify the chunk size (default 0.1)
        ** Optionally specify the embedding model (default OllamaNomadic)
    """.trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val filePaths = executionConfig?.file_paths ?: return
        val files = filePaths.map { path ->
            File(path).also { file ->
                if (!file.exists()) {
                    log.warn("File does not exist: $path")
                }
            }
        }.filter { it.exists() }

        if (files.isEmpty()) {
            val result = buildString {
                appendLine("# No Valid Files Found")
                appendLine()
                appendLine("The following paths were specified but could not be found:")
                filePaths.forEach { path ->
                    appendLine("* $path")
                }
            }
            task.add(MarkdownUtil.renderMarkdown(result, ui = task.manager))
            resultFn(result)
            return
        }

        val threadPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceAtMost(16)
        )
        try {
            val progressState = ProgressState.progressBar(task)
            // Determine embedding model from configuration
            val embeddingModel = EmbeddingModel.values().toList().firstOrNull {
                it.second.modelName.equals(executionConfig.embedding_model, ignoreCase = true)
            }!!.second
            indexJsonFile(
                pool = threadPool,
                progressState = progressState,
                inputPaths = files.map { it.absolutePath }.toTypedArray(),
                model = embeddingModel
            )

            val result = buildString {
                appendLine("# Knowledge Indexing Complete")
                appendLine()
                appendLine("## Configuration")
                appendLine("* Embedding Model: ${executionConfig?.embedding_model ?: "OllamaNomadic"}")
                appendLine("* Parsing Type: ${executionConfig?.parsing_type ?: "document"}")
                appendLine("* Chunk Size: ${executionConfig?.chunk_size ?: 0.1}")
                appendLine()
                appendLine("Processed ${files.size} files:")
                files.forEach { file ->
                    appendLine("* ${file.name}")
                }
            }
            task.add(MarkdownUtil.renderMarkdown(result, ui = task.manager))
            resultFn(result)
        } finally {
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
    }

    companion object {
        private val log = LoggerFactory.getLogger(KnowledgeIndexingTask::class.java)
    }
}