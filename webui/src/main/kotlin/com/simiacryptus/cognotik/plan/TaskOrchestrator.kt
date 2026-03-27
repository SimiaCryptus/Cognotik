package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph
import com.simiacryptus.cognotik.plan.PlanUtil.filterPlan
import com.simiacryptus.cognotik.plan.PlanUtil.getAllDependencies
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils.isBinaryFile
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class TaskOrchestrator(
  val user: User,
  val session: Session,
  val dataStorage: StorageInterface,
  val root: Path,
  val transcriptStream: OutputStream? = null,
  val timeoutMinutes: Long = 15
) {
  val pool: ExecutorService by lazy { ApplicationServices.threadPoolManager.getPool(session, user) }

  val files: Array<File> by lazy {
    FileSelectionUtils.expandFileList(root.toFile())
  }

  val codeFiles: Map<Path, String>
    get() = files
      .filter { it.exists() && it.isFile && !isBinaryFile(it) }
      .filter { !it.name.startsWith(".") }
      .associate { file ->
        root.relativize(file.toPath()) to try {
          file.inputStream().bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
          log.warn("Error reading $file", e)
          ""
        }
      }

  var executionState: ExecutionState? = null

  fun executePlan(
    plan: Map<String, TaskExecutionConfig>,
    task: SessionTask,
    userMessage: String,
    orchestrationConfig: OrchestrationConfig,
  ): ExecutionState {
    val tabs = TabbedDisplay(task)
    val planProcessingState = newState(plan)
    this.executionState = planProcessingState
    try {
      val diagramTask = tabs.newTask("Plan")
      executePlan(
        diagramBuffer = diagramTask.add(
          "## Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${buildMermaidGraph(planProcessingState.subTasks)}\n$TRIPLE_TILDE".renderMarkdown(
            true
          ),
          additionalClasses = "flow-chart"
        ),
        subTasks = planProcessingState.subTasks,
        task = diagramTask,
        executionState = planProcessingState,
        taskIdProcessingQueue = planProcessingState.taskIdProcessingQueue,
        pool = pool,
        userMessage = userMessage,
        plan = plan,
        tabs = tabs,
        orchestrationConfig = orchestrationConfig
      )
    } catch (e: Throwable) {
      log.warn("Error during incremental code generation process", e)
      task.error(e)
    }
    return planProcessingState
  }

  private fun newState(plan: Map<String, TaskExecutionConfig>) =
    ExecutionState(
      subTasks = (filterPlan { plan }?.entries?.toTypedArray<Map.Entry<String, TaskExecutionConfig>>()
        ?.associate { it.key to it.value } ?: mapOf()).toMutableMap()
    )

  fun executePlan(
    diagramBuffer: StringBuilder?,
    subTasks: Map<String, TaskExecutionConfig>,
    task: SessionTask,
    executionState: ExecutionState,
    taskIdProcessingQueue: MutableList<String>,
    pool: ExecutorService,
    userMessage: String,
    plan: Map<String, TaskExecutionConfig>,
    tabs: TabbedDisplay,
    orchestrationConfig: OrchestrationConfig,
  ) {
    val taskTabs = object : TabbedDisplay(
      tabs.newTask("Session"),
      additionalClasses = "task-tabs"
    ) {
      override fun renderTabButtons(): String {
        diagramBuffer?.set(
          "\n## Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${buildMermaidGraph(subTasks)}\n$TRIPLE_TILDE".renderMarkdown(
            true
          )
        )
        task.complete()
        return buildString {
          append("<div class='tabs'>\n")
          super.tabs.withIndex().forEach { (idx, t) ->
            val (taskId, _) = t
            val subTask = executionState.tasksByDescription[taskId]
            if (null == subTask) {
              log.warn("Task tab not found: $taskId")
            }
            val isChecked = if (taskId in taskIdProcessingQueue) "checked" else ""
            val style = when (subTask?.state) {
              AbstractTask.TaskState.Completed -> " style='text-decoration: line-through;'"
              null -> " style='opacity: 20%;'"
              AbstractTask.TaskState.Pending -> " style='opacity: 30%;'"
              else -> ""
            }
            append("<label class='tab-button' data-for-tab='${idx}'$style><input type='checkbox' $isChecked disabled />$taskId</label>\n")
          }
          append("</div>")
        }
      }
    }
    taskIdProcessingQueue.forEach { taskId ->
      val subtask: TaskExecutionConfig? = executionState.subTasks[taskId]
      val description = subtask?.task_description
      val newTask = taskTabs.newTask(description ?: taskId)
      executionState.uitaskMap[taskId] = newTask
      log.debug("Creating task tab: $taskId ${System.identityHashCode(subtask)} $description")
    }
    Thread.sleep(100)
    while (taskIdProcessingQueue.isNotEmpty()) {
      val taskId = taskIdProcessingQueue.removeAt(0)
      val subTask = executionState.subTasks[taskId] ?: throw RuntimeException("Task not found: $taskId")
      subTask.state = AbstractTask.TaskState.Pending
      log.debug("Awaiting dependencies: ${subTask.task_dependencies?.joinToString(", ") ?: ""}")
      subTask.task_dependencies
        ?.associate { it to executionState.taskFutures[it] }
        ?.forEach { (id, future) ->
          try {
            future?.get() ?: log.warn("Dependency not found: $id")
          } catch (e: Throwable) {
            log.warn("Error", e)
          }
        }
      executionState.taskFutures[taskId] = pool.submit {
        subTask.state = AbstractTask.TaskState.InProgress
        taskTabs.update()
        log.debug("Running task: ${System.identityHashCode(subTask)} ${subTask.task_description}")
        transcriptStream?.let { stream ->
          synchronized(stream) {
            stream.write("\n## Task `$taskId` Started\n\n${subTask.task_description}\n\n".toByteArray())
            stream.flush()
          }
        }
        val task = executionState.uitaskMap[taskId] ?: taskTabs.newTask(taskId)
        task.add(
          ("\n## Task `" + taskId + "`" + (subTask.task_description ?: "") + "\n" +
              TRIPLE_TILDE + "json" + JsonUtil.toJson(data = subTask) + "\n" + TRIPLE_TILDE + "\n").renderMarkdown(
            true
          )
        )
        try {
          val dependencies = subTask.task_dependencies?.toMutableSet() ?: mutableSetOf()
          dependencies += getAllDependencies(
            subPlanTask = subTask,
            subTasks = executionState.subTasks,
            visited = mutableSetOf()
          )
          task.add(
            ("\n### Dependencies:\n\n" + dependencies.joinToString("\n") { "* ${it.indent("    ")}" }).renderMarkdown(
              true
            )
          )
          val impl = orchestrationConfig.getImpl(subTask)
          val messages = listOf(
            userMessage,
            //JsonUtil.toJson(plan), // Giving the task the whole plan can distract it from its specific task, but may be useful for some implementations. Consider making this configurable.
            impl.getPriorCode(executionState),
            "Goals:\n" + (subTask.let {
              when {
                it.files.isNotEmpty() -> it.files.joinToString("\n") { "* Generate `${it}`" }
                else -> it.task_description
              }
            })
          )
          val onComplete = Semaphore(0)
          impl.run(
            agent = this,
            messages = messages,
            task = task,
            resultFn = {
              try {
                executionState.taskResult[taskId] = it
                transcriptStream?.let { stream ->
                  synchronized(stream) {
                    stream.write("\n### Task `$taskId` Result\n\n$it\n\n".toByteArray())
                    stream.flush()
                  }
                }
              } catch (e: Throwable) {
                log.warn("Error during result handling", e)
              } finally {
                onComplete.release()
              }
            },
            orchestrationConfig = orchestrationConfig
          )
          if (!onComplete.tryAcquire(timeoutMinutes, TimeUnit.MINUTES)) {
            throw RuntimeException("Task $taskId timed out")
          }
        } catch (e: Throwable) {
          log.warn("Error during task execution", e)
          task.error(e)
          transcriptStream?.let { stream ->
            synchronized(stream) {
              stream.write("\n### Task `$taskId` Error\n\n```\n${e.message}\n${e.stackTraceToString()}\n```\n\n".toByteArray())
              stream.flush()
            }
          }
        } finally {
          executionState.completedTasks.add(element = taskId)
          subTask.state = AbstractTask.TaskState.Completed
          log.debug("Completed task: $taskId ${System.identityHashCode(subTask)}")
          taskTabs.update()
          transcriptStream?.let { stream ->
            synchronized(stream) {
              stream.write("\n### Task `$taskId` Completed\n\n".toByteArray())
              stream.flush()
            }
          }
        }
      }
    }
    await(executionState.taskFutures)
  }

  fun await(futures: MutableMap<String, Future<*>>) {
    val start = System.currentTimeMillis()
    fun cont(): Boolean {
      val elapsed = System.currentTimeMillis() - start
      val done = futures.values.count { it.isDone }
      return done < futures.size && elapsed < TimeUnit.MINUTES.toMillis(20)
    }
    while (cont()) Thread.sleep(1000)
  }

  fun copy(
    user: User = this.user,
    session: Session = this.session,
    dataStorage: StorageInterface = this.dataStorage,
    root: Path = this.root,
    transcriptStream: OutputStream? = this.transcriptStream
  ) = TaskOrchestrator(
    user = user,
    session = session,
    dataStorage = dataStorage,
    root = root,
    transcriptStream = transcriptStream
  )

  companion object {
    private val log = LoggerFactory.getLogger(TaskOrchestrator::class.java)
  }
}

const val TRIPLE_TILDE = "```"