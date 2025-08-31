package cognotik.actions.knowledge

import com.simiacryptus.cognotik.apps.parse.DocumentRecord.Companion.indexTextFiles
import com.simiacryptus.cognotik.apps.parse.ParsingModelType
import com.simiacryptus.cognotik.apps.parse.ProgressState
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.chat.model.GoogleModels
import com.simiacryptus.jopenai.embedding.OllamaEmbeddingClient
import com.simiacryptus.jopenai.models.EmbeddingModel
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KnowledgeIndexingServer(
    val settings: KnowledgeIndexingAction.IndexingSettings,
    val api: ChatClientInterface,
    val model: EmbeddingModel
) : ApplicationServer(
    applicationName = "Knowledge Indexing",
    path = "/knowledgeIndexing",
    showMenubar = false,
), AutoCloseable {

    companion object {
        private val log = LoggerFactory.getLogger(KnowledgeIndexingServer::class.java)
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
        private const val MAX_DISPLAY_FILES = 20
        private const val MAX_FILE_SIZE_MB = 100
        private const val CHUNK_SIZE_MB = 10
        private const val BATCH_SIZE = 10
    }

    override val inputCnt = 0
    override val stickyInput = false

    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtMost(16)
    )
    @Volatile
    private var isCancelled = false

    override fun close() {
        isCancelled = true
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
                executeIndexing(task, ui)
            } catch (e: Exception) {
                log.error("Error during indexing", e)
                task.error(e)
            }
        }
        return socketManager
    }

    private fun executeIndexing(task: SessionTask, ui: ApplicationInterface) {
        val files = settings.filePaths.map { path ->
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
                settings.filePaths.forEach { path ->
                    appendLine("* $path")
                }
            }
            task.add(MarkdownUtil.renderMarkdown(result, ui = ui))
            task.complete(result)
            return
        }
        // Group files by size for better batch processing
        val (smallFiles, largeFiles) = files.partition { it.length() < CHUNK_SIZE_MB * 1024 * 1024 }
        val veryLargeFiles = files.filter { it.length() / (1024 * 1024) > MAX_FILE_SIZE_MB }
        if (veryLargeFiles.isNotEmpty()) {
            task.add(MarkdownUtil.renderMarkdown(buildString {
                appendLine("⚠️ **Warning: Large files detected**")
                appendLine()
                veryLargeFiles.forEach { file ->
                    val sizeMB = file.length() / (1024 * 1024)
                    appendLine("- ${file.name}: ${sizeMB}MB")
                }
                appendLine()
                appendLine("Large files will be processed in chunks to avoid memory issues.")
            }, ui = ui))
        }


        val totalSizeKB = files.sumOf { it.length() } / 1024
        val totalSizeMB = totalSizeKB / 1024
        val sizeDisplay = if (totalSizeMB > 1) "${totalSizeMB} MB" else "${totalSizeKB} KB"
        
        task.add(MarkdownUtil.renderMarkdown("# Knowledge Indexing", ui = ui))

        task.add(MarkdownUtil.renderMarkdown(buildString {
            this.appendLine("## Indexing Overview")
            this.appendLine("- **Files to process:** ${files.size}")
            this.appendLine("- **Total size:** $sizeDisplay")
            this.appendLine("- **Embedding model:** ${model.modelName}")
            this.appendLine()

            if (files.size <= MAX_DISPLAY_FILES) {
                this.appendLine("### Files:")
                files.forEach { file ->
                    val fileSizeKB = file.length() / 1024
                    val fileSizeMB = fileSizeKB / 1024
                    val fileSize = if (fileSizeMB > 1) "${fileSizeMB} MB" else "${fileSizeKB} KB"
                    this.appendLine("- `${file.name}` ($fileSize)")
                }
            } else {
                this.appendLine("### Sample Files:")
                files.take(MAX_DISPLAY_FILES).forEach { file ->
                    val fileSizeKB = file.length() / 1024
                    val fileSizeMB = fileSizeKB / 1024
                    val fileSize = if (fileSizeMB > 1) "${fileSizeMB} MB" else "${fileSizeKB} KB"
                    this.appendLine("- `${file.name}` ($fileSize)")
                }
                this.appendLine("- ... and ${files.size - MAX_DISPLAY_FILES} more files")
            }
            this.appendLine()
            this.appendLine("---")
        }, ui = ui))

        try {
            val progressState = ProgressState.progressBar(task)
            val startTime = System.currentTimeMillis()
            val errors = mutableListOf<String>()
            val successfulFiles = mutableListOf<String>()

            // Process small files in batches to avoid overwhelming the system
            val smallResults = mutableListOf<String?>()
            smallFiles.chunked(BATCH_SIZE).forEach { batch ->
                if (isCancelled) {
                    task.add(MarkdownUtil.renderMarkdown("⚠️ Indexing cancelled by user", ui = ui))
                    return
                }
                
                try {
                    val batchResults = indexTextFiles(
                        embeddingClient = OllamaEmbeddingClient(
                            "",
                            workPool = ui.socketManager!!.pool,
                        ),
                        pool = threadPool,
                        progressState = progressState,
                        inputPaths = batch.map { it.absolutePath }.toTypedArray(),
                        model = model,
                        parsingModel = ParsingModelType.getImpl(
                            chatModel = GoogleModels.GeminiFlash_25_Lite,
                            temperature = 0.0,
                            modelType = ParsingModelType.RawText,
                            api = api
                        ),
                    )
                    smallResults.addAll(batchResults)
                    batch.forEach { successfulFiles.add(it.name) }
                } catch (e: Exception) {
                    log.error("Failed to index batch of ${batch.size} files", e)
                    batch.forEach { errors.add("${it.name}: ${e.message}") }
                }
            }
            
            // Process large files one by one with chunking
            val largeResults = largeFiles.mapNotNull { file ->
                try {
                    task.add(MarkdownUtil.renderMarkdown("Processing large file: ${file.name}...", ui = ui))
                    val result = indexTextFiles(
                        embeddingClient = OllamaEmbeddingClient(
                            "",
                            workPool = ui.socketManager!!.pool,
                        ),
                        pool = threadPool,
                        progressState = progressState,
                        inputPaths = arrayOf(file.absolutePath),
                        model = model,
                        parsingModel = ParsingModelType.getImpl(
                            chatModel = GoogleModels.GeminiFlash_25_Lite,
                            temperature = 0.0,
                            modelType = ParsingModelType.RawText,
                            api = api
                        ),
                    ).firstOrNull()
                    if (result != null) {
                        successfulFiles.add(file.name)
                    }
                    result
                } catch (e: Exception) {
                    log.error("Failed to index large file: ${file.name}", e)
                    errors.add("${file.name}: ${e.message}")
                    null
                }
            }

            val results = smallResults + largeResults
            val successCount = results.filterNotNull().size + successfulFiles.size
            val failureCount = files.size - successCount

            val endTime = System.currentTimeMillis()
            val totalDuration = (endTime - startTime) / 1000
            
            val completionResult = buildString {
                if (failureCount == 0) {
                    appendLine("# 🎉 Knowledge Indexing Complete")
                } else if (successCount > 0) {
                    appendLine("# ⚠️ Knowledge Indexing Partially Complete")
                } else {
                    appendLine("# ❌ Knowledge Indexing Failed")
                }
                appendLine()
                appendLine("## Summary")
                appendLine("- **Successfully indexed:** $successCount files")
                if (failureCount > 0) {
                    appendLine("- **Failed:** $failureCount files")
                }
                appendLine("- **Total processing time:** ${formatDuration(totalDuration.toInt())}")
                appendLine()
                if (errors.isNotEmpty()) {
                    appendLine("## Errors")
                    errors.forEach { error ->
                        appendLine("- $error")
                    }
                    appendLine()
                }

                appendLine("## Next Steps")
                appendLine("Your files have been indexed and are now ready for:")
                appendLine("- **Semantic search** - Find relevant content using natural language queries")
                appendLine("- **Knowledge retrieval** - Access contextual information for AI conversations")
                appendLine("- **Document analysis** - Explore relationships between indexed content")
                appendLine()
                appendLine("The indexed knowledge base is now available for use in other Cognotik features.")
            }

            task.add(MarkdownUtil.renderMarkdown(completionResult, ui = ui))

        } catch (e: Exception) {
            log.error("Error during indexing process", e)
            val errorResult = buildString {
                appendLine("# ❌ Knowledge Indexing Failed")
                appendLine()
                appendLine("An error occurred during the indexing process:")
                appendLine("```")
                appendLine(e.message ?: "Unknown error")
                appendLine("```")
                appendLine()
                appendLine("Please check the logs for more details and try again.")
            }
            task.add(MarkdownUtil.renderMarkdown(errorResult, ui = ui))
            task.error(e)
        }
    }
    private fun formatDuration(seconds: Int): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ${seconds % 60}s"
        }
    }
}