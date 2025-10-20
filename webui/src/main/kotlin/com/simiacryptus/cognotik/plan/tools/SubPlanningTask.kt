package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
 import com.simiacryptus.cognotik.plan.AbstractTask
 import com.simiacryptus.cognotik.plan.OrchestrationConfig
 import com.simiacryptus.cognotik.plan.TaskExecutionConfig
 import com.simiacryptus.cognotik.plan.TaskOrchestrator
 import com.simiacryptus.cognotik.plan.TaskType
 import com.simiacryptus.cognotik.plan.TaskTypeConfig
 import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeStrategies
 import com.simiacryptus.cognotik.platform.model.ApiChatModel
 import com.simiacryptus.cognotik.util.LoggerFactory
 import com.simiacryptus.cognotik.util.TabbedDisplay
 import com.simiacryptus.cognotik.util.jsonCast
 import com.simiacryptus.cognotik.webui.session.SessionTask
 import com.simiacryptus.cognotik.webui.session.getChildClient

 class SubPlanningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SubPlanningTaskExecutionConfigData?
 ) : AbstractTask<SubPlanningTask.SubPlanningTaskExecutionConfigData, SubPlanningTask.SubPlanningTaskTypeConfig>(
   orchestrationConfig,
   planTask
) {

   class SubPlanningTaskTypeConfig(
       @Description("Cognitive strategy to use for sub-planning (overrides default)")
       var cognitiveMode: CognitiveModeStrategies? = null,
       @Description("Task-specific configurations available within sub-plans")
       val taskSettings: MutableMap<String, TaskTypeConfig> = mutableMapOf(),
       task_type: String = "RecursiveToolDefinition",
       model: ApiChatModel? = null,
       name: String? = task_type,
   ) : TaskTypeConfig(task_type = task_type, name = name, model = model)

   override val typeConfig: SubPlanningTaskTypeConfig
       get() = super.typeConfig.jsonCast<SubPlanningTaskTypeConfig>()

   class SubPlanningTaskExecutionConfigData(
       @Description("The goal or objective for the sub-planning task")
       val planning_goal: String? = null,
       @Description("Context information to provide to the sub-planner")
       val context: List<String>? = null,
       task_description: String? = null,
       task_dependencies: List<String>? = null,
       state: TaskState? = null,
   ) : TaskExecutionConfig(
       task_type = SubPlanning.name,
       task_description = task_description,
       task_dependencies = task_dependencies?.toMutableList(),
       state = state
   )

   override fun promptSegment() = """
       SubPlanningTask - Create and execute sub-plans using recursive planning with configurable cognitive modes.
       ** Specify a planning goal or objective
       ** Optionally provide context information
       ** Can override the cognitive mode for the sub-plan
       ** Supports multiple levels of recursion up to configured depth
       ** Results are aggregated and optionally summarized
   """.trimIndent()

   override fun run(
       agent: TaskOrchestrator,
       messages: List<String>,
       task: SessionTask,
       resultFn: (String) -> Unit,
       orchestrationConfig: OrchestrationConfig
   ) {
       log.info("Starting SubPlanningTask with goal: ${executionConfig?.planning_goal}")

       try {
           // Get the planning goal
           val planningGoal = executionConfig?.planning_goal
               ?: executionConfig?.task_description
               ?: throw IllegalArgumentException("No planning goal specified for SubPlanningTask")

           log.debug("Planning goal: $planningGoal")

           // Create a sub-orchestration config with potentially different cognitive mode
           val subOrchestrationConfig = createSubOrchestrationConfig(orchestrationConfig)

           // Get the cognitive mode for sub-planning
           val cognitiveMode = (typeConfig.cognitiveMode
               ?: orchestrationConfig.cognitiveMode
               ?: CognitiveModeStrategies.Adaptive)

           log.info("Using cognitive mode: ${cognitiveMode.name} for sub-planning")

           // Create tabs for displaying sub-plan execution
           val tabs = TabbedDisplay(task)

           // Create planning context
           val planningTask = task.ui.newTask(false)
           tabs["Planning"] = planningTask.placeholder

           // Build context for the sub-planner
           val contextMessages = buildContextMessages(messages)

           // Initialize the cognitive mode
           val cognitiveInstance = cognitiveMode.getCognitiveMode(
               task = planningTask,
               orchestrationConfig = subOrchestrationConfig,
               session = agent.session,
               user = agent.user
           ).apply { initialize() }

           // Display planning information
           val planningInfo = buildString {
               appendLine("# Sub-Planning Task")
               appendLine()
               appendLine("**Goal:** $planningGoal")
               appendLine()
               appendLine("**Cognitive Mode:** ${cognitiveMode.name}")
               appendLine()
               if (!executionConfig?.context.isNullOrEmpty()) {
                   appendLine("**Context:**")
                   executionConfig.context.forEach { ctx ->
                       appendLine("- $ctx")
                   }
                   appendLine()
               }
               appendLine("---")
               appendLine()
           }
           planningTask.add(planningInfo.renderMarkdown)

           // Execute the sub-plan using the cognitive mode
           val executionTask = task.ui.newTask(false)
           tabs["Execution"] = executionTask.placeholder

           log.debug("Executing sub-plan with ${contextMessages.size} context messages")

           // Handle the user message through the cognitive mode
           cognitiveInstance.handleUserMessage(planningGoal, executionTask)

           // Collect results from the cognitive mode's context
           val results = cognitiveInstance.contextData()

           log.info("Sub-plan execution completed with ${results.size} results")

           // Create summary if configured
           val summaryTask = task.ui.newTask(false)
           tabs["Summary"] = summaryTask.placeholder

           val summary = createSummary(results, planningGoal, summaryTask, orchestrationConfig)
           summaryTask.add(summary.renderMarkdown)
           tabs.update()
           resultFn(summary)

       } catch (e: Exception) {
           log.error("Error executing SubPlanningTask", e)
           task.error(e)
           resultFn("Error in sub-planning: ${e.message}")
       }
   }

   private fun createSubOrchestrationConfig(parentConfig: OrchestrationConfig): OrchestrationConfig {
       // Create a copy of the parent config with sub-planning specific settings
       val subConfig = parentConfig.copy(
           cognitiveMode = typeConfig.cognitiveMode ?: parentConfig.cognitiveMode,
           taskSettings = (parentConfig.taskSettings + typeConfig.taskSettings).toMutableMap(),
           maxIterations = (parentConfig.maxIterations * 0.7).toInt().coerceAtLeast(3),
           maxTasksPerIteration = (parentConfig.maxTasksPerIteration * 0.8).toInt().coerceAtLeast(2)
       )

       log.debug("Created sub-orchestration config with maxIterations=${subConfig.maxIterations}, maxTasksPerIteration=${subConfig.maxTasksPerIteration}")
       return subConfig
   }

   private fun buildContextMessages(messages: List<String>): List<String> {
       val contextMessages = mutableListOf<String>()

       // Add explicit context from execution config
       executionConfig?.context?.let { contextMessages.addAll(it) }

       // Add prior task results
       val priorCode = getPriorCode(null)
       if (priorCode.isNotBlank()) {
           contextMessages.add("## Prior Task Results\n\n$priorCode")
       }

       // Add incoming messages
       contextMessages.addAll(messages)

       log.debug("Built ${contextMessages.size} context messages for sub-planning")
       return contextMessages
   }

   private fun createSummary(
       results: List<String>,
       goal: String,
       task: SessionTask,
       orchestrationConfig: OrchestrationConfig
   ): String {
       log.info("Creating summary of ${results.size} sub-plan results")

       val combinedResults = results.joinToString("\n\n---\n\n")

       if (combinedResults.length < 5000) {
           log.debug("Results are short enough, returning without summarization")
           return buildString {
               appendLine("# Sub-Planning Results")
               appendLine()
               appendLine("**Goal:** $goal")
               appendLine()
               appendLine("---")
               appendLine()
               appendLine(combinedResults)
           }
       }

       // Use an agent to create a summary
       val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
           ?: orchestrationConfig.defaultChatter).getChildClient(task)

       val summaryAgent = ChatAgent(
           prompt = """
               Create a comprehensive summary of the sub-planning results below.
               
               Original Goal: $goal
               
               The summary should:
               - Highlight key findings and accomplishments
               - Identify any issues or blockers encountered
               - Provide actionable next steps if applicable
               - Be concise but complete
               
               Use markdown formatting with headers and bullet points.
           """.trimIndent(),
           model = model
       )

       val summary = summaryAgent.answer(listOf(combinedResults))

       log.debug("Generated summary of length: ${summary.length}")

       return buildString {
           appendLine("# Sub-Planning Summary")
           appendLine()
           appendLine("**Goal:** $goal")
           appendLine()
           appendLine("---")
           appendLine()
           appendLine(summary)
           appendLine()
           appendLine("---")
           appendLine()
           appendLine("<details>")
           appendLine("<summary>Full Results (${results.size} items)</summary>")
           appendLine()
           appendLine(combinedResults)
           appendLine()
           appendLine("</details>")
       }
   }

   companion object {
       private val log = LoggerFactory.getLogger(SubPlanningTask::class.java)

       val SubPlanning = TaskType(
           "SubPlanning",
           SubPlanningTaskExecutionConfigData::class.java,
           SubPlanningTaskTypeConfig::class.java,
           "Create and execute sub-plans using recursive planning",
           """
             Enables recursive planning and execution with configurable cognitive modes.
             <ul>
               <li>Create sub-plans with different cognitive strategies</li>
               <li>Support for multiple recursion levels</li>
               <li>Context propagation to sub-plans</li>
               <li>Configurable recursion depth limits</li>
               <li>Automatic result aggregation and summarization</li>
               <li>Flexible cognitive mode selection per sub-plan</li>
               <li>Useful for complex multi-stage problems</li>
             </ul>
           """
       )
   }
}