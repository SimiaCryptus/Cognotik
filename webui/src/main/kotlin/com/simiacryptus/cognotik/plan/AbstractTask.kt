package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.input.isDocumentFile
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.exists

abstract class AbstractTask<T : TaskExecutionConfig, U : TaskTypeConfig>(
    val orchestrationConfig: OrchestrationConfig,
    val executionConfig: T?
) {
    var state: TaskState? = TaskState.Pending

    protected open val root: Path
        get() = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
            ?: throw IllegalStateException("Working directory not set")

    open val taskType: String = executionConfig?.task_type ?: this::class.simpleName ?: "UnknownTask"

    open val typeConfig: U?
        get() = taskType.let { task_type -> orchestrationConfig.taskSettings.values.firstOrNull { it.task_type == task_type } as? U }

    open val defaultSmart: ChatInterface
        get() = typeConfig?.model?.let { orchestrationConfig.instance(it) } ?: orchestrationConfig.defaultSmart

    open val defaultFast: ChatInterface
        get() = orchestrationConfig.defaultFast

    enum class TaskState {
        Pending,
        InProgress,
        Completed,
    }

    open fun getPriorCode(executionState: ExecutionState?) =
        executionConfig?.task_dependencies?.joinToString("\n\n\n") { dependency ->
            "# $dependency\n\n${executionState?.taskResult[dependency] ?: ""}"
        } ?: ""

    protected open fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val footerTask = ui.newTask(false)
        lateinit var textHandle: StringBuilder
        @Suppress("AssignedValueIsNeverRead")
        textHandle = footerTask.complete(ui.hrefLink("Accept", classname = "href-link cmd-button") {
            try {
                textHandle.set("""<div class="cmd-button">Accepted</div>""")
                footerTask.complete()
            } catch (e: Throwable) {
                log.warn("Error", e)
            }
            fn()
        })!!
        return footerTask.placeholder
    }

    abstract fun promptSegment(): String

    abstract fun run(
        agent: TaskOrchestrator,
        messages: List<String> = listOf(),
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig,
    )

    fun getInputFileContent(
        files: List<String>?,
        root: Path,
        treatDocumentsAsText: Boolean = true,
    ): String = (files ?: listOf())
        .flatMap { pattern: String ->
            if (root.resolve(pattern).exists()) {
                return@flatMap listOf(root.resolve(pattern).toFile())
            }
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile(), treatDocumentsAsText = treatDocumentsAsText) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    it.isDirectory -> true
                    !matcher.matches(root.relativize(it.toPath())) -> false
                    else -> true
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                if (treatDocumentsAsText && file.isDocumentFile()) {
                    file.getDocumentReader().getText()
                } else {
                    "# $relativePath\n\n```\n${file.readText()}\n```"
                }
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    fun transcript(
        task: SessionTask,
        transcriptFile: String = this.taskType + "_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
    ): FileOutputStream? {
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.add("[Transcript](${link.removeSuffix(".md")}.html)".renderMarkdown())
        return markdownTranscript
    }

    companion object {
        val log = LoggerFactory.getLogger(AbstractTask::class.java)
    }
}

