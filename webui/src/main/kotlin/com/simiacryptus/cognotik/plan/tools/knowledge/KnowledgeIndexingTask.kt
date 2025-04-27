package com.simiacryptus.cognotik.plan.tools.knowledge

import com.simiacryptus.cognotik.apps.parse.CodeParsingModel
import com.simiacryptus.cognotik.apps.parse.DocumentParsingModel
import com.simiacryptus.cognotik.apps.parse.DocumentRecord.Companion.saveAsBinary
import com.simiacryptus.cognotik.apps.parse.ProgressState
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors

class KnowledgeIndexingTask(
    planSettings: PlanSettings,
    planTask: KnowledgeIndexingTaskConfigData?
) : AbstractTask<KnowledgeIndexingTask.KnowledgeIndexingTaskConfigData>(planSettings, planTask) {

    class KnowledgeIndexingTaskConfigData(
        @Description("The file paths to process and index")
        val file_paths: List<String>,
        @Description("The type of parsing to use (document, code)")
        val parsing_type: String = "document",
        @Description("The chunk size for parsing (default 0.1)")
        val chunk_size: Double = 0.1,
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
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        val filePaths = taskConfig?.file_paths ?: return
        val files = filePaths.map { File(it) }.filter { it.exists() }

        if (files.isEmpty()) {
            val result = "No valid files found to process"
            task.add(MarkdownUtil.renderMarkdown(result, ui = agent.ui))
            resultFn(result)
            return
        }

        val threadPool = Executors.newFixedThreadPool(8)
        try {
            val parsingModel = when (taskConfig.parsing_type.lowercase()) {
                "code" -> CodeParsingModel(taskSettings.model ?: planSettings.defaultModel, taskConfig.chunk_size)
                else -> DocumentParsingModel(taskSettings.model ?: planSettings.defaultModel, taskConfig.chunk_size)
            }

            val progressState = ProgressState()
            var currentProgress = 0.0
            progressState.onUpdate += {
                val newProgress = it.progress / it.max
                if (newProgress != currentProgress) {
                    currentProgress = newProgress
                    task.add(
                        MarkdownUtil.renderMarkdown(
                            "Processing: ${(currentProgress * 100).toInt()}%",
                            ui = agent.ui
                        )
                    )
                }
            }

            saveAsBinary(
                openAIClient = api2,
                pool = threadPool,
                progressState = progressState,
                inputPaths = files.map { it.absolutePath }.toTypedArray()
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
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KnowledgeIndexingTask::class.java)
    }
}