package com.simiacryptus.skyenet.apps.plan.tools.plan

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.skyenet.Discussable
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.ParsedResponse
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory

class PlanningTask(
  planSettings: PlanSettings,
  planTask: PlanningTaskConfigData?
) : AbstractTask<PlanningTask.PlanningTaskConfigData>(planSettings, planTask) {

  class PlanningTaskConfigData(
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskConfigBase(
    task_type = TaskType.TaskPlanningTask.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  data class TaskBreakdownResult(
    @Description("A map where each task ID is associated with its corresponding PlanTask object. Crucial for defining task relationships and information flow.")
    val tasksByID: Map<String, TaskConfigBase>? = null,
  )

  override fun promptSegment() = """
    PlanningTask:
      * Perform high-level planning and organization of tasks.
      * Decompose the overall goal into smaller, actionable tasks based on current information, ensuring proper information flow between tasks.
      * Specify prior tasks and the overall goal of the task, emphasizing dependencies to ensure each task is connected with its upstream and downstream tasks.
      * Dynamically break down tasks as new information becomes available.
      * Carefully consider task dependencies to ensure efficient information transfer and coordination between tasks.
      * Design the task structure to maximize parallel execution where possible, while respecting necessary dependencies.
      * **Note**: A planning task should refine the plan based on new information, optimizing task relationships and dependencies, and should not initiate execution.
      * Ensure that each task utilizes the outputs or side effects of its upstream tasks, and provides outputs or side effects for its downstream tasks.
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
    val userMessage = messages.joinToString("\n")
    val newTask = agent.ui.newTask(false).apply { add(placeholder) }
    fun toInput(s: String) = (messages + listOf(s)).filter { it.isNotBlank() }

    val subPlan = if (!planSettings.autoFix) {
      createSubPlanDiscussable(newTask, userMessage, ::toInput, api, agent.ui, planSettings, agent.describer).call().obj
    } else {
      val design = planSettings.planningActor(agent.describer).answer(
        toInput("Expand ${taskConfig?.task_description ?: ""}"),
        api = api
      )
      PlanUtil.render(
        withPrompt = TaskBreakdownWithPrompt(
          plan = PlanUtil.filterPlan { design.obj.tasksByID } ?: emptyMap(),
          planText = design.text,
          prompt = userMessage
        ),
        ui = agent.ui
      )
      design.obj
    }
    executeSubTasks(agent, userMessage, PlanUtil.filterPlan { subPlan.tasksByID } ?: emptyMap(), task, api, api2)
  }

  private fun createSubPlanDiscussable(
    task: SessionTask,
    userMessage: String,
    toInput: (String) -> List<String>,
    api: API,
    ui: ApplicationInterface,
    planSettings: PlanSettings,
    describer: TypeDescriber
  ) = Discussable(
    task = task,
    userMessage = { "Expand ${taskConfig?.task_description ?: ""}" },
    heading = "",
    initialResponse = { it: String -> planSettings.planningActor(describer).answer(toInput(it), api = api) },
    outputFn = { design: ParsedResponse<TaskBreakdownResult> ->
      PlanUtil.render(
        withPrompt = TaskBreakdownWithPrompt(
          plan = PlanUtil.filterPlan { design.obj.tasksByID } ?: emptyMap(),
          planText = design.text,
          prompt = userMessage
        ),
        ui = ui
      )
    },
    ui = ui,
    reviseResponse = { usermessages: List<Pair<String, ApiModel.Role>> ->
      planSettings.planningActor(describer).respond(
        messages = usermessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
          .toTypedArray<ApiModel.ChatMessage>(),
        input = toInput("Expand ${taskConfig?.task_description ?: ""}\n${JsonUtil.toJson(this)}"),
        api = api
      )
    },
  )

  private fun executeSubTasks(
    coordinator: PlanCoordinator,
    userMessage: String,
    subPlan: Map<String, TaskConfigBase>,
    parentTask: SessionTask,
    api: API,
    api2: OpenAIClient,
  ) {
    val subPlanTask = coordinator.ui.newTask(false)
    parentTask.add(subPlanTask.placeholder)
    val planProcessingState = PlanProcessingState(subPlan.toMutableMap())
    coordinator.copy(
      planSettings = coordinator.planSettings.copy(
        taskSettings = coordinator.planSettings.taskSettings.toList().toTypedArray().toMap().toMutableMap().apply {
          this["TaskPlanning"] = TaskSettingsBase(enabled = false, model = null, task_type = TaskType.TaskPlanningTask.name)
        }
      )
    ).executePlan(
      task = subPlanTask,
      diagramBuffer = subPlanTask.add(PlanUtil.diagram(coordinator.ui, planProcessingState.subTasks)),
      subTasks = subPlan,
      diagramTask = subPlanTask,
      planProcessingState = planProcessingState,
      taskIdProcessingQueue = PlanUtil.executionOrder(subPlan).toMutableList(),
      pool = coordinator.pool,
      userMessage = userMessage,
      plan = subPlan,
      api = api,
      api2 = api2,
    )
    subPlanTask.complete()
  }

  companion object {
    private val log = LoggerFactory.getLogger(PlanningTask::class.java)
  }
}