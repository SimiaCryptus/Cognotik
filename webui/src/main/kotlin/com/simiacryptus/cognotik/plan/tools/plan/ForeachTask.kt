package com.simiacryptus.cognotik.plan.tools.plan

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask

class ForeachTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ForeachTaskConfigData?
) : AbstractTask<ForeachTask.ForeachTaskConfigData>(orchestrationConfig, planTask) {

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
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val userMessage = messages.joinToString("\n")
        val items =
            taskConfig?.foreach_items ?: throw RuntimeException("No items specified for ForeachTask")
        val subTasks = taskConfig.foreach_subplan ?: throw RuntimeException("No subTasks specified for ForeachTask")
        val subPlanTask = task.manager.newTask(false)
        task.add(subPlanTask.placeholder)

        items.forEachIndexed { index, item ->
            val itemSubTasks = subTasks.mapValues { (_, subTaskPlan) ->
                subTaskPlan.task_description = "${subTaskPlan.task_description} - Item $index: $item"
                subTaskPlan
            }
            val itemExecutionState = ExecutionState(itemSubTasks)
            val tabs = TabbedDisplay(task)
            agent.executePlan(
                diagramBuffer = subPlanTask.add(
                    PlanUtil.diagram(itemExecutionState.subTasks)
                ),
                subTasks = itemSubTasks,
                task = subPlanTask,
                executionState = itemExecutionState,
                taskIdProcessingQueue = PlanUtil.executionOrder(itemSubTasks)
                    .toMutableList(),
                pool = agent.pool,
                userMessage = "$userMessage\nProcessing item $index: $item",
                plan = itemSubTasks,
                tabs = tabs
            )
        }
        subPlanTask.complete("Completed ForeachTask for ${items.size} items")
    }

}