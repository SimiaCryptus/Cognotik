package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient

class SubPlanTask(
    orchestrationConfig: OrchestrationConfig, planTask: SubPlanTaskExecutionConfigData?
) : AbstractTask<SubPlanTask.SubPlanTaskExecutionConfigData, SubPlanTask.SubPlanTaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class SubPlanTaskTypeConfig(
        @Description("Cognitive strategy to use for sub-planning (overrides default)") var cognitiveSettings: CognitiveModeConfig? = null,
        @Description("Task-specific configurations available within sub-plans") val taskSettings: MutableMap<String, TaskTypeConfig> = mutableMapOf(),
        @Description("Supplemental description of the purpose of this configuration") val purpose: String = "",
        task_type: String = "RecursiveToolDefinition",
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(task_type = task_type, name = name, model = model), ValidatedObject {
        val cognitiveMode: CognitiveModeType<*>? get() = cognitiveSettings?.type
        override fun validate(): String? {
            // Validate that taskSettings don't contain invalid configurations
            taskSettings.forEach { (key, config) ->
                if (config is ValidatedObject) {
                    config.validate()?.let { return "Invalid task setting '$key': $it" }
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    class SubPlanTaskExecutionConfigData(
        @Description("The goal or objective for the sub-planning task") val planning_goal: String? = null,
        @Description("Context information to provide to the sub-planner") val context: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = SubPlan.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            // Validate that either planning_goal or task_description is provided
            if (planning_goal.isNullOrBlank() && task_description.isNullOrBlank()) {
                return "Either planning_goal or task_description must be specified for SubPlanningTask"
            }

            // Validate context items if present
            context?.forEachIndexed { index, ctx ->
                if (ctx.isBlank()) {
                    return "Context item at index $index is blank"
                }
            }

            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        val typeConfig = typeConfig
        return buildString {
            appendLine("SubPlanningTask - Create and execute sub-plans using recursive planning with configurable cognitive modes.")
            typeConfig?.purpose?.takeIf { it.isNotEmpty() }?.let {
                appendLine("** Purpose: $it")
            }
            typeConfig?.taskSettings?.values?.joinToString(", ") { it.task_type ?: "?" }?.let {
                appendLine("** This SubPlanningTask can run the following tasks types: $it")
            }
            appendLine("** Specify a planning goal or objective")
            appendLine("** Optionally provide context information")
            appendLine("** Can override the cognitive mode for the sub-plan")
            appendLine("** Supports multiple levels of recursion up to configured depth")
            append("** Results are aggregated and optionally summarized")
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        log.info("Starting SubPlanningTask with goal: ${executionConfig?.planning_goal}")
        val transcript = transcript(task)

        try {
            val typeConfig = this.typeConfig ?: throw RuntimeException()
            // Get the cognitive mode for sub-planning
            val cognitiveMode =
                (typeConfig.cognitiveMode?.newSettings() ?: orchestrationConfig.cognitiveSettings
                ?: CognitiveModeType.Adaptive.newSettings())

            val subConfig = orchestrationConfig.copy(
                taskSettings = typeConfig.taskSettings,
                cognitiveSettings = typeConfig.cognitiveSettings ?: orchestrationConfig.cognitiveSettings,
            )
            log.debug("Created sub-orchestration config with maxIterations=${subConfig.maxIterations}, maxTasksPerIteration=${subConfig.maxTasksPerIteration}")

            log.info("Using cognitive mode: ${cognitiveMode.type?.name} for sub-planning")

            // Create tabs for displaying sub-plan execution
            val tabs = TabbedDisplay(task)

            // Create planning context
            val planningTask = task.ui.newTask()
            tabs["Planning"] = planningTask.placeholder

            // Get the planning goal
            var planningGoal =
                executionConfig?.planning_goal ?: executionConfig?.task_description
                ?: throw IllegalArgumentException("No planning goal specified for SubPlanningTask")

            // Append purpose if available
            if (typeConfig.purpose.isNotEmpty()) planningGoal = planningGoal + """
                Purpose: ${typeConfig.purpose}
            """.trimIndent()

            // Build context for the sub-planner
            val contextMessages = buildContextMessages(messages)

            // Append context to the planning goal
            if (contextMessages.isNotEmpty()) {
                planningGoal = planningGoal + "\n\nContext:\n" + contextMessages.joinToString("\n")
            }

            log.debug("Planning goal: $planningGoal")

            // Initialize the cognitive mode
            val cognitiveInstance = cognitiveMode.type!!.getImpl(
                task = planningTask, orchestrationConfig = subConfig, session = agent.session, user = agent.user
            ).apply { initialize() }

            // Display planning information
            val planningInfo = buildString {
                appendLine("# Sub-Planning Task")
                appendLine()
                appendLine("**Goal:** $planningGoal")
                appendLine()
                appendLine("**Cognitive Mode:** ${cognitiveMode.type?.name}")
                appendLine()
                if (typeConfig.purpose.isNotEmpty()) {
                    appendLine("**Purpose:** ${typeConfig.purpose}")
                    appendLine()
                }
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
            transcript?.write(planningInfo.toByteArray())
            planningTask.add(planningInfo.renderMarkdown)
            planningTask.complete()








            fun runExecution(): String {
                // Execute the sub-plan using the cognitive mode
                val executionTask = task.ui.newTask()
                tabs["Execution"] = executionTask.placeholder

                log.debug("Executing sub-plan with ${contextMessages.size} context messages")

                // Handle the user message through the cognitive mode
                transcript?.write("\n\n## Execution\n\n".toByteArray())
                transcript?.write("**Planning Goal:**\n\n".toByteArray())
                transcript?.write(planningGoal.toByteArray())
                transcript?.write("\n\n".toByteArray())

                cognitiveInstance.handleUserMessage(planningGoal, executionTask)

                // Collect results from the cognitive mode's context
                val results = cognitiveInstance.contextData()

                log.info("Sub-plan execution completed with ${results.size} results")

                // Create summary if configured
                val summaryTask = task.ui.newTask()
                tabs["Summary"] = summaryTask.placeholder

                val summary = createSummary(results, planningGoal, summaryTask, orchestrationConfig)
                transcript?.write("\n\n## Summary\n\n".toByteArray())
                transcript?.write(summary.toByteArray())
                transcript?.write("\n\n".toByteArray())
                summaryTask.add(summary.renderMarkdown)
                summaryTask.complete()
                tabs.update()
                return summary
            }

            if (orchestrationConfig.autoFix) {
                val summary = runExecution()
                resultFn(summary)
                task.complete()
            } else {
                val semaphore = java.util.concurrent.Semaphore(0)
                task.add(task.ui.hrefLink("▶ Run Sub-Plan", "btn btn-primary") {
                    task.ui.pool.submit {
                        try {
                            val summary = runExecution()
                            val footer = acceptButtonFooter(task.ui) {
                                resultFn(summary)
                                semaphore.release()
                            }
                            task.append(footer)
                        } catch (e: Exception) {
                            log.error("Error in manual sub-plan execution", e)
                            task.error(e)
                        }
                    }
                })
                task.complete()
                semaphore.acquire()
            }

        } catch (e: Exception) {
            log.error("Error executing SubPlanningTask", e)
            transcript?.write("\n\n## Error\n\n".toByteArray())
            transcript?.write("```\n${e.message}\n${e.stackTraceToString()}\n```\n".toByteArray())
            task.error(e)
            resultFn("Error in sub-planning: ${e.message}")
        } finally {
            transcript?.close()
        }
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
        results: List<String>, goal: String, task: SessionTask, orchestrationConfig: OrchestrationConfig
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
        val typeConfig = typeConfig ?: throw RuntimeException()
        val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
            ?: defaultSmart).getChildClient(task)

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
           """.trimIndent(), model = model
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
        private val log = LoggerFactory.getLogger(SubPlanTask::class.java)

        val SubPlan = TaskType(
            "SubPlan",
            "Execution & Automation",
            SubPlanTaskExecutionConfigData::class.java,
            SubPlanTaskTypeConfig::class.java,
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