package cognotik.actions.knowledge

import com.simiacryptus.cognotik.apps.parse.DocumentRecord.Companion.indexTextFile
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
    }

    override val inputCnt = 0
    override val stickyInput = false

    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtMost(16)
    )

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

        val task = ui.newTask(true)

        try {
            executeIndexing(task, ui)
        } catch (e: Exception) {
            log.error("Error during indexing", e)
            task.error(e)
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

        // Display initial status with file statistics
        val totalSizeKB = files.sumOf { it.length() } / 1024
        val totalSizeMB = totalSizeKB / 1024
        val sizeDisplay = if (totalSizeMB > 1) "${totalSizeMB} MB" else "${totalSizeKB} KB"
        
        task.add(MarkdownUtil.renderMarkdown("# Knowledge Indexing", ui = ui))
        
        val initialStatus = buildString {
            appendLine("## Indexing Overview")
            appendLine("- **Files to process:** ${files.size}")
            appendLine("- **Total size:** $sizeDisplay")
            appendLine("- **Embedding model:** ${model.modelName}")
            appendLine()
            
            if (files.size <= MAX_DISPLAY_FILES) {
                appendLine("### Files:")
                files.forEach { file ->
                    val fileSizeKB = file.length() / 1024
                    val fileSizeMB = fileSizeKB / 1024
                    val fileSize = if (fileSizeMB > 1) "${fileSizeMB} MB" else "${fileSizeKB} KB"
                    appendLine("- `${file.name}` ($fileSize)")
                }
            } else {
                appendLine("### Sample Files:")
                files.take(MAX_DISPLAY_FILES).forEach { file ->
                    val fileSizeKB = file.length() / 1024
                    val fileSizeMB = fileSizeKB / 1024
                    val fileSize = if (fileSizeMB > 1) "${fileSizeMB} MB" else "${fileSizeKB} KB"
                    appendLine("- `${file.name}` ($fileSize)")
                }
                appendLine("- ... and ${files.size - MAX_DISPLAY_FILES} more files")
            }
            appendLine()
            appendLine("---")
        }
        
        task.add(MarkdownUtil.renderMarkdown(initialStatus, ui = ui))
        
        // Create progress status placeholder
        val progressStatus = task.add(MarkdownUtil.renderMarkdown("## Progress\n\nInitializing...", ui = ui))!!
        task.add(MarkdownUtil.renderMarkdown("### Processing Details\n\n", ui = ui))!!

        try {
            val progressState = ProgressState()
            
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime
            var currentFile = ""
            val processedFiles = mutableSetOf<String>()
            val failedFiles = mutableListOf<String>()
            
            progressState.onUpdate += { state ->
                val currentTime = System.currentTimeMillis()
                // Update at most once per second to avoid UI flooding
                if (currentTime - lastUpdateTime > PROGRESS_UPDATE_INTERVAL_MS) {
                    lastUpdateTime = currentTime
                    
                    val progress = if (state.max > 0) state.progress.toDouble() / state.max else 0.0
                    val elapsedSeconds = (currentTime - startTime) / 1000
                    val rate = if (elapsedSeconds > 0) state.progress.toDouble() / elapsedSeconds else 0.0
                    val estimatedTotal = if (rate > 0) (state.max / rate).toInt() else 0
                    val remainingSeconds = maxOf(0, estimatedTotal - elapsedSeconds.toInt())
                    
                    val progressBar = "█".repeat((progress * 20).toInt()) + "░".repeat(20 - (progress * 20).toInt())
                    
                    val progressMarkdown = buildString {
                        appendLine("## Progress")
                        appendLine()
                        appendLine("**Overall Progress:** ${(progress * 100).toInt()}% `[$progressBar]`")
                        appendLine()
                        appendLine("- **Processed:** ${state.progress} / ${state.max} chunks")
                        appendLine("- **Files completed:** ${processedFiles.size} / ${files.size}")
                        if (failedFiles.isNotEmpty()) {
                            appendLine("- **Failed files:** ${failedFiles.size}")
                        }
                        appendLine("- **Processing rate:** ${String.format("%.1f", rate)} chunks/sec")
                        appendLine("- **Elapsed time:** ${formatDuration(elapsedSeconds.toInt())}")
                        if (remainingSeconds > 0) {
                            appendLine("- **Estimated remaining:** ${formatDuration(remainingSeconds)}")
                        }
                        if (currentFile.isNotEmpty()) {
                            appendLine("- **Current file:** `$currentFile`")
                        }
                    }
                    
                    progressStatus.set(MarkdownUtil.renderMarkdown(progressMarkdown, ui = ui))
                    task.update()
                }
            }

            indexTextFile(
                embeddingClient = OllamaEmbeddingClient(
                    "",
                    workPool = threadPool,
                ),
                pool = threadPool,
                progressState = progressState,
                inputPaths = files.map { it.absolutePath }.toTypedArray(),
                model = model,
                parsingModel = ParsingModelType.getImpl(
                    chatModel = GoogleModels.GeminiFlash_25_Lite,
                    temperature = 0.0,
                    modelType = ParsingModelType.RawText,
                    api = api
                ),
            )


            val endTime = System.currentTimeMillis()
            val totalDuration = (endTime - startTime) / 1000
            val successCount = processedFiles.size
            val failureCount = failedFiles.size
            
            val completionResult = buildString {
                appendLine("# 🎉 Knowledge Indexing Complete")
                appendLine()
                appendLine("## Summary")
                appendLine("- **Total files:** ${files.size}")
                appendLine("- **Successfully indexed:** $successCount")
                if (failureCount > 0) {
                    appendLine("- **Failed:** $failureCount")
                }
                appendLine("- **Total processing time:** ${formatDuration(totalDuration.toInt())}")
                appendLine("- **Average time per file:** ${String.format("%.1f", totalDuration.toDouble() / successCount)} seconds")
                appendLine()
                
                if (successCount > 0) {
                    appendLine("## ✅ Successfully Indexed Files")
                    if (successCount <= MAX_DISPLAY_FILES) {
                        processedFiles.forEach { file ->
                            appendLine("- `$file`")
                        }
                    } else {
                        processedFiles.take(MAX_DISPLAY_FILES).forEach { file ->
                            appendLine("- `$file`")
                        }
                        appendLine("- ... and ${successCount - MAX_DISPLAY_FILES} more files")
                    }
                    appendLine()
                }
                
                if (failureCount > 0) {
                    appendLine("## ❌ Failed Files")
                    failedFiles.forEach { error ->
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
            
            val statusMessage = if (failureCount == 0) {
                "✅ Indexing completed successfully - $successCount files indexed"
            } else {
                "⚠️ Indexing completed with issues - $successCount succeeded, $failureCount failed"
            }
            task.complete(statusMessage)
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