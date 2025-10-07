package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.nio.file.Path

abstract class AbstractTask<T : TaskExecutionConfig, U : TaskTypeConfig>(
    val orchestrationConfig: OrchestrationConfig,
    val executionConfig: T?
) {
    var state: TaskState? = TaskState.Pending
    protected val codeFiles = mutableMapOf<Path, String>()

    protected open val root: Path
        get() = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
            ?: throw IllegalStateException("Working directory not set")

    open val typeConfig: U
        get() = executionConfig?.task_type
            ?.let { task_type -> orchestrationConfig.taskSettings.values.firstOrNull { it.task_type == task_type } as? U }
            ?: throw IllegalStateException("No task type config for ${executionConfig?.task_type}")

    enum class TaskState {
        Pending,
        InProgress,
        Completed,
    }

    open fun getPriorCode(executionState: ExecutionState?) =
        executionConfig?.task_dependencies?.joinToString("\n\n\n") { dependency ->
            "# $dependency\n\n${executionState?.taskResult[dependency] ?: ""}"
        } ?: ""

    protected fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
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

    companion object {
        val log = LoggerFactory.getLogger(AbstractTask::class.java)
    }
}