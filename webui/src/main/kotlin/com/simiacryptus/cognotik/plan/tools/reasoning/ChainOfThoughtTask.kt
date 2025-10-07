package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

class ChainOfThoughtTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ChainOfThoughtTaskExecutionConfigData?
) : AbstractTask<ChainOfThoughtTask.ChainOfThoughtTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class ChainOfThoughtTaskExecutionConfigData(
        @Description("The complex problem requiring step-by-step reasoning")
        val problem_statement: String? = null,
        @Description("Number of reasoning steps to generate (default: auto)")
        val reasoning_depth: Int? = null,
        @Description("Whether to validate each step before proceeding")
        val validate_steps: Boolean = true,
        @Description("Additional files for context")
        val related_files: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ChainOfThought.name,
        task_description = problem_statement,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class ReasoningStep(
        val step_number: Int,
        val reasoning: String,
        val conclusion: String,
        val confidence: Double,
        val next_question: String?
    )

    data class StepValidation(
        val is_valid: Boolean,
        val issues: List<String>?,
        val suggestions: String?
    )

    override fun promptSegment(): String {
        return """
ChainOfThought - Break down complex problems into explicit reasoning steps
  ** Specify the problem statement that requires step-by-step reasoning
  ** Optionally set reasoning_depth to control the number of steps (default: auto)
  ** Enable validate_steps to validate each step before proceeding (default: true)
  ** Related files can provide additional context for reasoning
  ** Each step will be:
     - Generated with explicit reasoning
     - Validated for logical consistency
     - Used as context for the next step
  ** The task will backtrack if validation fails
  ** Final output includes the complete reasoning chain and conclusion
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val problemStatement = executionConfig?.problem_statement
        if (problemStatement.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No problem statement specified")
            return
        }

        val newTask = task.ui.newTask(false)
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(
            MarkdownUtil.renderMarkdown(
                "## Chain of Thought Reasoning\n\n**Problem**: $problemStatement",
                ui = ui
            )
        )

        val priorContext = getPriorCode(agent.executionState!!)
        val contextFiles = getContextFiles()

        val tabs = TabbedDisplay(newTask, closable = false)
        val reasoningChain = mutableListOf<ReasoningStep>()
        var currentQuestion = problemStatement
        var stepNumber = 1
        val maxSteps = executionConfig?.reasoning_depth ?: 10

        try {
            while (stepNumber <= maxSteps) {
                val stepTask = ui.newTask(cancelable = false, root = false)
                tabs["Step $stepNumber"] = stepTask.placeholder

                val step = generateReasoningStep(
                    stepTask,
                    currentQuestion!!,
                    reasoningChain,
                    priorContext,
                    contextFiles,
                    stepNumber,
                    api
                )

                if (executionConfig?.validate_steps == true) {
                    val validation = validateStep(stepTask, step, reasoningChain, api)
                    if (!validation.is_valid) {
                        stepTask.add(
                            MarkdownUtil.renderMarkdown(
                                """
                                |### ⚠️ Validation Failed
                                |
                                |**Issues**:
                                |${validation.issues?.joinToString("\n") { "- $it" } ?: "Unknown issues"}
                                |
                                |**Suggestions**: ${validation.suggestions ?: "None"}
                                """.trimMargin(),
                                ui = ui
                            )
                        )

                        // Attempt to regenerate with validation feedback
                        val revisedStep = generateReasoningStep(
                            stepTask,
                            currentQuestion,
                            reasoningChain,
                            priorContext,
                            contextFiles,
                            stepNumber,
                            api,
                            validationFeedback = validation
                        )
                        reasoningChain.add(revisedStep)
                        stepTask.complete()
                    } else {
                        reasoningChain.add(step)
                        stepTask.complete()
                    }
                } else {
                    reasoningChain.add(step)
                    stepTask.complete()
                }

                val lastStep = reasoningChain.last()

                // Check if we should continue
                if (lastStep.next_question.isNullOrBlank() || lastStep.confidence >= 0.9) {
                    break
                }

                currentQuestion = lastStep.next_question
                stepNumber++
            }

            tabs.update()

            // Generate final summary
            val summary = generateSummary(newTask, reasoningChain, problemStatement, api)

            newTask.complete(
                MarkdownUtil.renderMarkdown(
                    """
                    |## Final Reasoning Chain
                    |
                    |$summary
                    """.trimMargin(),
                    ui = ui
                )
            )

            resultFn(formatReasoningChain(reasoningChain, summary))

        } catch (e: Exception) {
            log.error("Error in chain of thought reasoning", e)
            newTask.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun generateReasoningStep(
        task: SessionTask,
        question: String,
        priorSteps: List<ReasoningStep>,
        priorContext: String,
        contextFiles: String,
        stepNumber: Int,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface,
        validationFeedback: StepValidation? = null
    ): ReasoningStep {
        val prompt = buildString {
            append("You are performing step-by-step reasoning to solve a complex problem.\n\n")
            append("## Current Question\n$question\n\n")

            if (priorSteps.isNotEmpty()) {
                append("## Previous Reasoning Steps\n")
                priorSteps.forEach { step ->
                    append("**Step ${step.step_number}**: ${step.reasoning}\n")
                    append("**Conclusion**: ${step.conclusion}\n\n")
                }
            }

            if (priorContext.isNotBlank()) {
                append("## Context from Previous Tasks\n$priorContext\n\n")
            }

            if (contextFiles.isNotBlank()) {
                append("## Related Files\n$contextFiles\n\n")
            }

            if (validationFeedback != null) {
                append("## Validation Feedback\n")
                append("Previous attempt had issues:\n")
                validationFeedback.issues?.forEach { append("- $it\n") }
                append("\nSuggestions: ${validationFeedback.suggestions}\n\n")
                append("Please revise your reasoning based on this feedback.\n\n")
            }

            append(
                """
                |## Instructions
                |Provide your reasoning for this step, including:
                |1. Your thought process and analysis
                |2. A clear conclusion for this step
                |3. Your confidence level (0.0 to 1.0)
                |4. The next question to explore (or null if reasoning is complete)
                |
                |Be thorough but concise. Show your work.
            """.trimMargin()
            )
        }

        val reasoningAgent = ParsedAgent(
            resultClass = ReasoningStep::class.java,
            prompt = prompt,
            model = api,
            temperature = 0.3,
            name = "ReasoningStep$stepNumber",
            parsingModel = orchestrationConfig.parsingChatter,
        )

        var step: ReasoningStep? = null
        Retryable(task, task.ui) { sb ->
            step = reasoningAgent.answer(listOf(question)).obj.copy(step_number = stepNumber)
            sb.append("Step $stepNumber completed")
            sb.toString()
        }
        val finalStep = step!!

        task.add(
            MarkdownUtil.renderMarkdown(
                """
                |### Step $stepNumber
                |
                |**Reasoning**: ${finalStep.reasoning}
                |
                |**Conclusion**: ${finalStep.conclusion}
                |
                |**Confidence**: ${String.format("%.1f%%", finalStep.confidence * 100)}
                |
                |${if (finalStep.next_question != null) "**Next Question**: ${finalStep.next_question}" else "**Status**: Reasoning complete"}
                """.trimMargin(),
                ui = task.ui
            )
        )

        return finalStep
    }

    private fun validateStep(
        task: SessionTask,
        step: ReasoningStep,
        priorSteps: List<ReasoningStep>,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface
    ): StepValidation {
        val prompt = buildString {
            append("You are validating a reasoning step for logical consistency and correctness.\n\n")

            if (priorSteps.isNotEmpty()) {
                append("## Previous Steps\n")
                priorSteps.forEach { s ->
                    append("**Step ${s.step_number}**: ${s.conclusion}\n")
                }
                append("\n")
            }

            append("## Current Step to Validate\n")
            append("**Reasoning**: ${step.reasoning}\n")
            append("**Conclusion**: ${step.conclusion}\n\n")

            append(
                """
                |## Validation Criteria
                |Check for:
                |1. Logical consistency with previous steps
                |2. Sound reasoning without logical fallacies
                |3. Appropriate confidence level
                |4. Clear and justified conclusion
                |
                |Provide validation results.
            """.trimMargin()
            )
        }

        val validationAgent = ParsedAgent(
            resultClass = StepValidation::class.java,
            prompt = prompt,
            model = api,
            temperature = 0.1,
            name = "StepValidation",
            parsingModel = orchestrationConfig.parsingChatter,
        )

        var validation: StepValidation? = null
        Retryable(task, task.ui) { sb ->
            validation = validationAgent.answer(listOf("Validate step ${step.step_number}")).obj
            sb.append("Validation completed")
            sb.toString()
        }
        return validation!!
    }

    private fun generateSummary(
        task: SessionTask,
        reasoningChain: List<ReasoningStep>,
        originalProblem: String,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface
    ): String {
        val prompt = buildString {
            append("Summarize the complete reasoning chain and provide a final answer.\n\n")
            append("## Original Problem\n$originalProblem\n\n")
            append("## Reasoning Chain\n")
            reasoningChain.forEach { step ->
                append("**Step ${step.step_number}**: ${step.conclusion}\n")
            }
            append("\n## Instructions\n")
            append("Provide a comprehensive summary that:\n")
            append("1. Synthesizes all reasoning steps\n")
            append("2. Provides a clear final answer\n")
            append("3. Highlights key insights\n")
            append("4. Notes any limitations or assumptions\n")
        }

        val summaryAgent = ChatAgent(
            prompt = prompt,
            model = api,
            temperature = 0.3,
            name = "ReasoningSummary"
        )

        var summary = ""
        Retryable(task, task.ui) { sb ->
            summary = summaryAgent.answer(listOf("Generate summary"))
            sb.append(summary)
            sb.toString()
        }
        return summary
    }

    private fun formatReasoningChain(steps: List<ReasoningStep>, summary: String): String {
        return buildString {
            append("# Chain of Thought Reasoning\n\n")
            append("## Reasoning Steps\n\n")
            steps.forEach { step ->
                append("### Step ${step.step_number}\n")
                append("**Reasoning**: ${step.reasoning}\n\n")
                append("**Conclusion**: ${step.conclusion}\n\n")
                append("**Confidence**: ${String.format("%.1f%%", step.confidence * 100)}\n\n")
                if (step.next_question != null) {
                    append("**Next Question**: ${step.next_question}\n\n")
                }
                append("---\n\n")
            }
            append("## Final Summary\n\n")
            append(summary)
        }
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""

        return relatedFiles.joinToString("\n\n") { filePath ->
            try {
                val file = root.resolve(filePath).toFile()
                if (file.exists()) {
                    "### $filePath\n```\n${file.readText()}\n```"
                } else {
                    "### $filePath\n(File not found)"
                }
            } catch (e: Exception) {
                log.warn("Error reading file: $filePath", e)
                "### $filePath\n(Error reading file: ${e.message})"
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ChainOfThoughtTask::class.java)
        val ChainOfThought = TaskType(
            "ChainOfThought",
            ChainOfThoughtTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Break down complex problems into explicit reasoning steps",
            """
              Performs step-by-step reasoning with validation:
              <ul>
                <li>Breaks complex problems into logical steps</li>
                <li>Validates each step before proceeding</li>
                <li>Provides reasoning transparency</li>
                <li>Can backtrack if validation fails</li>
                <li>Generates comprehensive reasoning chains</li>
              </ul>
            """
        )
    }
}