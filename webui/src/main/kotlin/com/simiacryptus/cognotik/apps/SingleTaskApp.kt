package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * An application that executes a single pre-configured task without planning.
 * The task configuration is provided directly by the user through a dialog.
 */
abstract class SingleTaskApp(
    path: String,
    applicationName: String = "Single Task App",
    showMenubar: Boolean = false,
    private val taskType: TaskType<*, *>,
    private val taskConfig: TaskExecutionConfig,
    val instanceFn: ((ApiChatModel) -> ChatInterface)?
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
    showMenubar = showMenubar,
    root = dataStorageRoot,
) {
    private val log = LoggerFactory.getLogger(SingleTaskApp::class.java)

    override val stickyInput = false
    override val inputCnt: Int = 1

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T =
        OrchestrationConfig(sessionId = session.sessionId, null) as T

    abstract fun instance(model: ApiChatModel): ChatInterface

    override fun newSession(
        user: User, session: Session
    ): SocketManager {
        val socketManager = super.newSession(user, session)
        startSession(session, user, socketManager)
        return socketManager
    }

    protected fun startSession(
        session: Session,
        user: User,
        socketManager: SocketManager
    ) {
        val settings = getSettings(session, user, OrchestrationConfig::class.java)
        if (null != instanceFn) OrchestrationConfig.instanceFn = instanceFn
        socketManager.newTask(cancelable = false, root = true).expandable(
            "Session Info", """
    Session ID: `${session}`
    
    Start Time: `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}`
    
    Root: `${settings?.absoluteWorkingDir}`
    
    Session Location: `${dataStorage.getSessionDir(user, session).absolutePath}`
    
    Data Location: `${dataStorage.getDataDir(user, session).absolutePath}`
    
    Task Type: `${taskType.name}`
    
              """.renderMarkdown()
        )
        socketManager.pool.submit { executeTask(session, user, socketManager, settings) }
    }

    protected open fun onTaskComplete(result: String, task: SessionTask) {}
    protected open fun onTaskError(e: Throwable) {}

    protected open fun executeTask(
        session: Session, user: User = defaultUser, ui: SocketManager, settings: OrchestrationConfig?
    ) {
        try {
            val orchestrationConfig = settings?.apply {
                if(null == DataStorage.sessionPaths[session]) absoluteWorkingDir?.let { DataStorage.sessionPaths[session] = File(it) }
            } ?: throw IllegalStateException("OrchestrationConfig not found in session settings")

            val task = ui.newTask(true)

            // Get the task implementation
            val taskImpl = orchestrationConfig.getImpl(
                taskType = taskType, cfg = taskConfig
            )

            // Execute the task
            taskImpl.run(
                agent = TaskOrchestrator(
                    user = user,
                    session = session,
                    dataStorage = ui.dataStorage!!,
                    root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                        ?: ui.dataStorage.getSessionDir(user, session).toPath() ?: File(".").toPath()
                ),
                messages = listOf(taskConfig.task_description ?: "Execute task"),
                task = task,
                resultFn = { result ->
                    task.complete(result.renderMarkdown)
                    onTaskComplete(result, task)
                },
                orchestrationConfig = orchestrationConfig
            )

        } catch (e: Throwable) {
            log.error("Error executing task", e)
            ui.newTask().error(e)
            onTaskError(e)
        }
    }

    override fun userMessage(
        session: Session, user: User, userMessage: String, ui: SocketManager
    ) {
        // Single task apps don't accept user messages after initialization
        ui.newTask().error(
            IllegalStateException("This is a single-task application. User messages are not supported.")
        )
    }
}