package com.simiacryptus.cognotik.plan.tools.plan

import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.toContentList
import com.simiacryptus.cognotik.webui.session.SessionTask

class PlanningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: PlanningTaskConfigData?
) : AbstractTask<PlanningTask.PlanningTaskConfigData>(orchestrationConfig, planTask) {

    class PlanningTaskConfigData(
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskConfigBase(
        task_type = "TaskPlanningTask",
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
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val userMessage = messages.joinToString("\n")
        val newTask = task.manager.newTask(false).apply { add(placeholder) }
        fun toInput(s: String) = (messages + listOf(s)).filter { it.isNotBlank() }

        val subPlan = if (!orchestrationConfig.autoFix) {
            createSubPlanDiscussable(
                newTask,
                userMessage,
                ::toInput,
                orchestrationConfig,
                agent.describer
            ).call()?.obj
        } else {
            val design = orchestrationConfig.planningActor(agent.describer).answer(
                toInput("Expand ${taskConfig?.task_description ?: ""}"),
            )
            PlanUtil.render(
                withPrompt = TaskBreakdownWithPrompt(
                    plan = PlanUtil.filterPlan { design.obj.tasksByID } ?: emptyMap(),
                    planText = design.text,
                    prompt = userMessage
                )
            )
            design.obj
        }
        executeSubTasks(
            agent,
            userMessage,
            PlanUtil.filterPlan { subPlan?.tasksByID } ?: emptyMap(),
            task,
        )
    }

    private fun createSubPlanDiscussable(
        task: SessionTask,
        userMessage: String,
        toInput: (String) -> List<String>,
        orchestrationConfig: OrchestrationConfig,
        describer: TypeDescriber
    ) = Discussable(
        task = task,
        userMessage = { "Expand ${taskConfig?.task_description ?: ""}" },
        heading = "",
        initialResponse = { it: String -> orchestrationConfig.planningActor(describer).answer(toInput(it)) },
        outputFn = { design: ParsedResponse<TaskBreakdownResult> ->
            PlanUtil.render(
                withPrompt = TaskBreakdownWithPrompt(
                    plan = PlanUtil.filterPlan { design.obj.tasksByID } ?: emptyMap(),
                    planText = design.text,
                    prompt = userMessage
                )
            )
        },
        reviseResponse = { usermessages: List<Pair<String, ModelSchema.Role>> ->
            orchestrationConfig.planningActor(describer).respond(
                messages = usermessages.map { ModelSchema.ChatMessage(it.second, it.first.toContentList()) }
                    .toTypedArray<ModelSchema.ChatMessage>(),
                input = toInput("Expand ${taskConfig?.task_description ?: ""}\n${JsonUtil.toJson(this)}"),
            )
        },
    )

    private fun executeSubTasks(
        coordinator: TaskOrchestrator,
        userMessage: String,
        subPlan: Map<String, TaskConfigBase>,
        parentTask: SessionTask,
    ) {
        val subPlanTask = parentTask.manager.newTask(false)
        parentTask.add(subPlanTask.placeholder)
        val executionState = ExecutionState(subPlan.toMutableMap())
        coordinator.copy(
            orchestrationConfig = coordinator.orchestrationConfig.copy(
                taskSettings = coordinator.orchestrationConfig.taskSettings.toList().toTypedArray().toMap().toMutableMap()
            )
        ).executePlan(
            diagramBuffer = subPlanTask.add(
                PlanUtil.diagram(
                    executionState.subTasks
                )
            ),
            subTasks = subPlan,
            task = subPlanTask,
            executionState = executionState,
            taskIdProcessingQueue = PlanUtil.executionOrder(subPlan).toMutableList(),
            pool = coordinator.pool,
            userMessage = userMessage,
            plan = subPlan,
            tabs = TabbedDisplay(subPlanTask),
        )
        subPlanTask.complete()
    }

    companion object {
    }
}