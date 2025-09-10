package com.simiacryptus.cognotik.plan.tools.knowledge

import com.simiacryptus.cognotik.apps.parse.DocumentRecord.Companion.indexJsonFile
import com.simiacryptus.cognotik.apps.parse.ProgressState
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingClient
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KnowledgeIndexingTask(
    planSettings: PlanSettings,
    planTask: KnowledgeIndexingTaskConfigData?
) : AbstractTask<KnowledgeIndexingTask.KnowledgeIndexingTaskConfigData>(planSettings, planTask) {

    class KnowledgeIndexingTaskConfigData(
        @Description("The file paths to process and index")
        val file_paths: List<String>,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskConfigBase(
        task_type = TaskType.KnowledgeIndexingTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
      KnowledgeIndexingTask - Process and index files for semantic search
        ** Specify the file paths to process
        ** Specify the parsing type (document or code)
        ** Optionally specify the chunk size (default 0.1)
    """.trimIndent()

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        planSettings: PlanSettings
    ) {
        val filePaths = taskConfig?.file_paths ?: return
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
            task.add(MarkdownUtil.renderMarkdown(result, ui = agent.ui))
            resultFn(result)
            return
        }

        val threadPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceAtMost(16)
        )
        try {
            val progressState = ProgressState.progressBar(task)
            val embeddingClient = OllamaEmbeddingClient(workPool = threadPool)
            indexJsonFile(
                embeddingClient = embeddingClient,
                pool = threadPool,
                progressState = progressState,
                inputPaths = files.map { it.absolutePath }.toTypedArray(),
                model = EmbeddingModel.Large
            )

            val result = buildString {
                appendLine("# Knowledge Indexing Complete")
                appendLine()
                appendLine("Processed ${files.size} files:")
                files.forEach { file ->
                    appendLine("* ${file.name}")
                }
            }
            task.add(MarkdownUtil.renderMarkdown(result, ui = agent.ui))
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