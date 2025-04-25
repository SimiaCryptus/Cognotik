package com.simiacryptus.cognotik.plan


import com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph
import com.simiacryptus.cognotik.plan.PlanUtil.filterPlan
import com.simiacryptus.cognotik.plan.PlanUtil.getAllDependencies
import com.simiacryptus.cognotik.plan.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class PlanCoordinator(
  val user: User?,
  val session: Session,
  val dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val planSettings: PlanSettings,
  val root: Path
) {
  
  var describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "aicoder.actions"
  ) {
    override val includeMethods: Boolean get() = false
    
    override fun getEnumValues(clazz: Class<*>): List<String> {
      return if (clazz == TaskType::class.java) {
        planSettings.taskSettings.filter { it.value.enabled }.map { it.key }
      } else {
        super.getEnumValues(clazz)
      }
    }
  }
  
  val pool: ExecutorService by lazy { ApplicationServices.clientManager.getPool(session, user) }

  val files: Array<File> by lazy {
    FileSelectionUtils.expandFileList(root.toFile())
  }

  val codeFiles: Map<Path, String>
    get() = files
      .filter { it.exists() && it.isFile }
      .filter { !it.name.startsWith(".") }
      .associate { file ->
        root.relativize(file.toPath()) to try {
          file.inputStream().bufferedReader().use { it.readText() }
        } catch (e: Exception) {
          log.warn("Error reading file", e)
          ""
        }
      }
  
  var planProcessingState: PlanProcessingState? = null
  
  fun executePlan(
    plan: Map<String, TaskConfigBase>,
    task: SessionTask,
    userMessage: String,
    api: API,
    api2: OpenAIClient,
  ): PlanProcessingState {
    val api = (api as ChatClient).getChildClient(task)
    val planProcessingState = newState(plan)
    this.planProcessingState = planProcessingState
    try {
      val diagramTask = ui.newTask(false).apply { task.add(placeholder) }
      executePlan(
        task = task,
        diagramBuffer = diagramTask.add(
          renderMarkdown(
            "## Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${buildMermaidGraph(planProcessingState.subTasks)}\n$TRIPLE_TILDE",
            ui = ui
          ), additionalClasses = "flow-chart"
        ),
        subTasks = planProcessingState.subTasks,
        diagramTask = diagramTask,
        planProcessingState = planProcessingState,
        taskIdProcessingQueue = planProcessingState.taskIdProcessingQueue,
        pool = pool,
        userMessage = userMessage,
        plan = plan,
        api = api,
        api2 = api2,
      )
    } catch (e: Throwable) {
      log.warn("Error during incremental code generation process", e)
      task.error(ui, e)
    }
    return planProcessingState
  }

  private fun newState(plan: Map<String, TaskConfigBase>) =
    PlanProcessingState(
      subTasks = (filterPlan { plan }?.entries?.toTypedArray<Map.Entry<String, TaskConfigBase>>()
        ?.associate { it.key to it.value } ?: mapOf()).toMutableMap()
    )

  fun executePlan(
    task: SessionTask,
    diagramBuffer: StringBuilder?,
    subTasks: Map<String, TaskConfigBase>,
    diagramTask: SessionTask,
    planProcessingState: PlanProcessingState,
    taskIdProcessingQueue: MutableList<String>,
    pool: ExecutorService,
    userMessage: String,
    plan: Map<String, TaskConfigBase>,
    api: API,
    api2: OpenAIClient,
  ) {
    val sessionTask = ui.newTask(false).apply { task.add(placeholder) }
    val api = (api as ChatClient).getChildClient(task)
    val taskTabs = object : TabbedDisplay(sessionTask, additionalClasses = "task-tabs") {
      override fun renderTabButtons(): String {
        diagramBuffer?.set(
          renderMarkdown(
            "## Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${buildMermaidGraph(subTasks)}\n$TRIPLE_TILDE", ui = ui
          )
        )
        diagramTask.complete()
        return buildString {
          append("<div class='tabs'>\n")
          super.tabs.withIndex().forEach { (idx, t) ->
            val (taskId, taskV) = t
            val subTask = planProcessingState.tasksByDescription[taskId]
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
      val newTask = ui.newTask(false)
      planProcessingState.uitaskMap[taskId] = newTask
      val subtask: TaskConfigBase? = planProcessingState.subTasks[taskId]
      val description = subtask?.task_description
      log.debug("Creating task tab: $taskId ${System.identityHashCode(subtask)} $description")
      taskTabs[description ?: taskId] = newTask.placeholder
    }
    Thread.sleep(100)
    while (taskIdProcessingQueue.isNotEmpty()) {
      val taskId = taskIdProcessingQueue.removeAt(0)
      val subTask = planProcessingState.subTasks[taskId] ?: throw RuntimeException("Task not found: $taskId")
      planProcessingState.taskFutures[taskId] = pool.submit {
        subTask.state = AbstractTask.TaskState.Pending
        log.debug("Awaiting dependencies: ${subTask.task_dependencies?.joinToString(", ") ?: ""}")
        subTask.task_dependencies
          ?.associate { it to planProcessingState.taskFutures[it] }
          ?.forEach { (id, future) ->
            try {
              future?.get() ?: log.warn("Dependency not found: $id")
            } catch (e: Throwable) {
              log.warn("Error", e)
            }
          }
        subTask.state = AbstractTask.TaskState.InProgress
        taskTabs.update()
        log.debug("Running task: ${System.identityHashCode(subTask)} ${subTask.task_description}")
        val task1 = planProcessingState.uitaskMap.get(taskId) ?: ui.newTask(false).apply {
          taskTabs[taskId] = placeholder
        }
        try {
          val dependencies = subTask.task_dependencies?.toMutableSet() ?: mutableSetOf()
          dependencies += getAllDependencies(
            subPlanTask = subTask,
            subTasks = planProcessingState.subTasks,
            visited = mutableSetOf()
          )

          task1.add(
            renderMarkdown(
              """
              ## Task `""".trimIndent() + taskId + "`" + (subTask.task_description ?: "") + "\n" +
                  TRIPLE_TILDE + "json" + JsonUtil.toJson(data = subTask) + "\n" + TRIPLE_TILDE +
                  "\n### Dependencies:" + dependencies.joinToString("\n") { "* $it" }, ui = ui
            )
          )
          val api = api.getChildClient(task)
          val impl = getImpl(planSettings, subTask)
          val messages = listOf(
            userMessage,
            JsonUtil.toJson(plan),
            impl.getPriorCode(planProcessingState)
          )
          impl.run(
            agent = this,
            messages = messages,
            task = task1,
            api = api,
            api2 = api2,
            resultFn = { planProcessingState.taskResult[taskId] = it },
            planSettings = planSettings
          )
        } catch (e: Throwable) {
          log.warn("Error during task execution", e)
          task1.error(ui, e)
        } finally {
          planProcessingState.completedTasks.add(element = taskId)
          subTask.state = AbstractTask.TaskState.Completed
          log.debug("Completed task: $taskId ${System.identityHashCode(subTask)}")
          taskTabs.update()
        }
      }
    }
    await(planProcessingState.taskFutures)
  }

  fun await(futures: MutableMap<String, Future<*>>) {
    val start = System.currentTimeMillis()
    while (futures.values.count { it.isDone } < futures.size && (System.currentTimeMillis() - start) < TimeUnit.MINUTES.toMillis(2)) {
      Thread.sleep(1000)
    }
  }

  fun copy(
    user: User? = this.user,
    session: Session = this.session,
    dataStorage: StorageInterface = this.dataStorage,
    ui: ApplicationInterface = this.ui,
    planSettings: PlanSettings = this.planSettings,
    root: Path = this.root
  ) = PlanCoordinator(
    user = user,
    session = session,
    dataStorage = dataStorage,
    ui = ui,
    planSettings = planSettings,
    root = root
  )

  fun executeTask(
    task: TaskConfigBase,
    messages: List<String>,
    sessionTask: SessionTask,
    api: API,
    api2: OpenAIClient
  ) {
    try {
      val api = (api as ChatClient).getChildClient(sessionTask)
      val impl = getImpl(planSettings, task, strict = false)
      sessionTask.add(
        renderMarkdown(
          """
          ## Executing Task
          ${TRIPLE_TILDE}json
          ${JsonUtil.toJson(task)}
          ${TRIPLE_TILDE}
          """.trimIndent(),
          ui = ui, tabs = false
        )
      )
      impl.run(
        agent = this,
        messages = messages,
        task = sessionTask,
        api = api,
        api2 = api2,
        resultFn = { /* Individual task execution doesn't need result storage */ },
        planSettings = planSettings
      )
    } catch (e: Throwable) {
      log.warn("Error during task execution", e)
      sessionTask.error(ui, e)
    }
  }

  companion object : Planner() {
    private val log = LoggerFactory.getLogger(PlanCoordinator::class.java)
  }
}

const val TRIPLE_TILDE = "```"