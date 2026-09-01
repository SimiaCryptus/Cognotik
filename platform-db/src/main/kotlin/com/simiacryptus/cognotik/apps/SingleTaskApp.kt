package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.LoggerFactory
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
  val taskType: TaskType<*, *>,
  val taskConfig: TaskExecutionConfig = TaskExecutionConfig(task_type = taskType.name),
  val instanceFn: ((ApiChatModel, User) -> ChatInterface)?,
  var message: String,
  val user: User,
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
  override fun <T : Any> initSettings(session: Session, user: User) =
    OrchestrationConfig(sessionId = session.sessionId, user = user) as T


  abstract fun instance(model: ApiChatModel): ChatInterface

  override fun newSession(
    user: User, session: Session
  ): SocketManager {
    val socketManager = super.newSession(user, session)!!
    startSession(
      session,
      user,
      socketManager,
    )
    return socketManager
  }


  protected fun startSession(
    session: Session,
    user: User,
    socketManager: SocketManager,
  ) {
    if (null != instanceFn) OrchestrationConfig.instanceFn = instanceFn
    val orchestrationConfig = getOrchestrationConfig(session, user)
    socketManager.newTask(cancelable = false, root = true).expandable(
      "Session Info", """
Session ID: `${session}`

Start Time: `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}`

Root: `${orchestrationConfig?.absoluteWorkingDir}`

Session Location: `${dataStorage.getUserDir(user, session).absolutePath}`

Data Location: `${dataStorage.getSystemDir(user, session).absolutePath}`

Task Type: `${taskType.name}`

Task Config: 
```json
${taskConfig.toJson()}
```

              """.renderMarkdown()
    )
    socketManager.pool.submit {
      executeTask(
        session = session,
        user = user,
        ui = socketManager,
        settings = orchestrationConfig,
        message = message,
      )
    }
  }

  open fun getOrchestrationConfig(
    session: Session,
    user: User
  ): OrchestrationConfig = initSettings(session, user)

  protected open fun onTaskComplete(result: String, task: ISessionTask) {}
  protected open fun onTaskError(e: Throwable) {}

  protected open fun executeTask(
    session: Session,
    user: User,
    ui: SocketManager,
    settings: OrchestrationConfig?,
    message: String,
  ) {
    try {
      val orchestrationConfig = settings?.apply {
        if (null == DataStorage.userPaths[session]) absoluteWorkingDir?.let {
          DataStorage.userPaths[session] = File(it)
        }
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
          dataStorage = ui.dataStorage,
          root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
            ?: ui.dataStorage.getUserDir(user, session).toPath() ?: File(".").toPath()
        ),
        messages = listOf(message),
        task = task,
        resultFn = { result ->
          task.complete(result.renderMarkdown(true))
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