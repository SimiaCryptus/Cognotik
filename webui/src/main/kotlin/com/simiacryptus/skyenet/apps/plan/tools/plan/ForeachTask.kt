package com.simiacryptus.skyenet.apps.plan.tools.plan

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory

class ForeachTask(
  planSettings: PlanSettings,
  planTask: ForeachTaskConfigData?
) : AbstractTask<ForeachTask.ForeachTaskConfigData>(planSettings, planTask) {

  class ForeachTaskConfigData(
    @Description("A list of items over which the ForEach task will iterate. (Only applicable for ForeachTask tasks) Can be used to process outputs from previous tasks.")
    val foreach_items: List<String>? = null,
    @Description("A map of sub-task IDs to PlanTask objects to be executed for each item. (Only applicable for ForeachTask tasks) Allows for complex task dependencies and information flow within iterations.")
    val foreach_subplan: Map<String, TaskConfigBase>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = TaskType.ForeachTask.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment(): String {
    return """
ForeachTask - Execute a task for each item in a list
  ** Specify the list of items to iterate over
  ** Define the task to be executed for each item
        """.trimIndent()
  }

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
    val items =
      taskConfig?.foreach_items ?: throw RuntimeException("No items specified for ForeachTask")
    val subTasks = taskConfig.foreach_subplan ?: throw RuntimeException("No subTasks specified for ForeachTask")
    val subPlanTask = agent.ui.newTask(false)
    task.add(subPlanTask.placeholder)

    items.forEachIndexed { index, item ->
      val itemSubTasks = subTasks.mapValues { (_, subTaskPlan) ->
        subTaskPlan.task_description = "${subTaskPlan.task_description} - Item $index: $item"
        subTaskPlan
      }
      val itemPlanProcessingState = PlanProcessingState(itemSubTasks)
      agent.executePlan(
        task = subPlanTask,
        diagramBuffer = subPlanTask.add(PlanUtil.diagram(agent.ui, itemPlanProcessingState.subTasks)),
        subTasks = itemSubTasks,
        diagramTask = subPlanTask,
        planProcessingState = itemPlanProcessingState,
        taskIdProcessingQueue = PlanUtil.executionOrder(itemSubTasks).toMutableList(),
        pool = agent.pool,
        userMessage = "$userMessage\nProcessing item $index: $item",
        plan = itemSubTasks,
        api = api,
        api2 = api2,
      )
    }
    subPlanTask.complete("Completed ForeachTask for ${items.size} items")
  }

  companion object {
    private val log = LoggerFactory.getLogger(ForeachTask::class.java)
  }
}