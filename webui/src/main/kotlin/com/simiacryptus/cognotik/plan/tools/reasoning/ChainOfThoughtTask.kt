package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
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
        val problem_statement: String = "",
        @Description("Number of reasoning steps to generate (default: auto)")
        val reasoning_depth: Int = 10,
        @Description("Whether to validate each step before proceeding")
        val validate_steps: Boolean = true,
        @Description("Additional files for context")
        val related_files: List<String> = emptyList(),
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ChainOfThought.name,
        task_description = problem_statement,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class ReasoningStep(
        val step_number: Int = 0,
        val reasoning: String = "",
        val conclusion: String = "",
        val confidence: Double = 0.0,
        val next_question: String? = null
    )

    data class StepValidation(
        val is_valid: Boolean = true,
        val issues: List<String>? = null,
        val suggestions: String? = null
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
        val startTime = System.currentTimeMillis()
        log.info("Starting ChainOfThoughtTask with problem: '${executionConfig?.problem_statement}'")

        val problemStatement = executionConfig?.problem_statement
        if (problemStatement?.isBlank() != false) {
            log.error("No problem statement specified")
            resultFn("CONFIGURATION ERROR: No problem statement specified")
            return
        }

        val maxSteps = executionConfig.reasoning_depth.coerceIn(1, 20)
        val validateSteps = executionConfig.validate_steps
        log.info("Configuration: maxSteps=$maxSteps, validateSteps=$validateSteps")

        val ui = task.ui
        val api = orchestrationConfig.defaultChatter
        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)
        // Overview tab
        val overviewTask = task.ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Chain of Thought Reasoning")
            appendLine()
            appendLine("**Problem Statement:** $problemStatement")
            appendLine()
            appendLine("**Max Steps:** $maxSteps")
            appendLine()
            appendLine("**Validate Steps:** ${if (validateSteps) "Yes" else "No"}")
            appendLine()
            appendLine(
                "**Started:** ${
                    java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }"
            )
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing reasoning process...*")
        }
        overviewTask.add(
            MarkdownUtil.renderMarkdown(
                overviewContent,
                ui = ui
            )
        )
        task.update()

        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            log.debug("Found prior context from previous tasks: ${priorContext.length} characters")
        }

        val contextFiles = getContextFiles()
        if (contextFiles.isNotBlank()) {
            log.debug("Found context files: ${executionConfig.related_files.size} files")
        }

        if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
            val contextTask = task.ui.newTask(false)
            tabs["Context"] = contextTask.placeholder
            contextTask.add(
                buildString {
                    appendLine("# Context")
                    appendLine()
                    if (priorContext.isNotBlank()) {
                        appendLine("## Previous Tasks")
                        appendLine()
                        appendLine(priorContext)
                        appendLine()
                    }
                    if (contextFiles.isNotBlank()) {
                        appendLine("## Related Files")
                        appendLine()
                        appendLine(contextFiles)
                    }
                }.let { MarkdownUtil.renderMarkdown(it, ui = ui) }
            )
            task.update()
        }

        // Update overview with initialization complete
        overviewTask.add(
            MarkdownUtil.renderMarkdown(
                buildString {
                    appendLine()
                    appendLine("✅ Initialization complete")
                    appendLine()
                    appendLine("*Starting reasoning steps...*")
                },
                ui = ui
            )
        )
        task.update()

        val reasoningChain = mutableListOf<ReasoningStep>()
        var currentQuestion = problemStatement
        var stepNumber = 1
        val stepTimes = mutableListOf<Long>()

        try {
            while (stepNumber <= maxSteps) {
                val stepStartTime = System.currentTimeMillis()
                log.info("Starting reasoning step $stepNumber of $maxSteps")

              val stepTask = ui.newTask(false)
                tabs["Step $stepNumber"] = stepTask.placeholder
                stepTask.add(
                    MarkdownUtil.renderMarkdown(
                        buildString {
                            appendLine("# Step $stepNumber of $maxSteps")
                            appendLine()
                            appendLine("**Status:** Processing...")
                            appendLine()
                            appendLine("**Question:** $currentQuestion")
                            appendLine()
                            appendLine("---")
                            appendLine()
                        },
                        ui = ui
                    )
                )
                task.update()

                val step = generateReasoningStep(
                    stepTask,
                    currentQuestion!!,
                    reasoningChain,
                    priorContext,
                    contextFiles,
                    stepNumber,
                    api
                )
                log.debug("Generated reasoning step $stepNumber: confidence=${step.confidence}")

                if (validateSteps) {
                    stepTask.add(
                        MarkdownUtil.renderMarkdown(
                            buildString {
                                appendLine("*Validating step...*")
                                appendLine()
                            },
                            ui = ui
                        )
                    )
                    task.update()

                    val validation = validateStep(stepTask, step, reasoningChain, api)
                    log.debug("Validation result for step $stepNumber: valid=${validation.is_valid}")

                    if (!validation.is_valid) {
                        log.warn("Step $stepNumber failed validation, attempting to regenerate")
                        stepTask.add(
                            MarkdownUtil.renderMarkdown(
                                """
                                |### ⚠️ Validation Failed
                                |
                                |**Issues**:
                                |${validation.issues?.joinToString("\n") { "- $it" } ?: "Unknown issues"}
                                |
                                |**Suggestions**: ${validation.suggestions ?: "None"}
                                |
                                |*Regenerating step with feedback...*
                                """.trimMargin(),
                                ui = ui
                            )
                        )
                        task.update()

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
                        log.info("Regenerated step $stepNumber with validation feedback")
                        reasoningChain.add(revisedStep)
                    } else {
                        reasoningChain.add(step)
                    }
                } else {
                    reasoningChain.add(step)
                }

                val lastStep = reasoningChain.last()
                val stepTime = System.currentTimeMillis() - stepStartTime
                stepTimes.add(stepTime)
                // Mark step as complete
                stepTask.add(
                    MarkdownUtil.renderMarkdown(
                        buildString {
                            appendLine("---")
                            appendLine()
                            appendLine("**Status:** ✅ Complete")
                            appendLine()
                            appendLine("**Processing Time:** ${stepTime / 1000.0}s")
                        },
                        ui = ui
                    )
                )
                stepTask.complete()
                task.update()
                // Update overview with progress
                overviewTask.add(
                    MarkdownUtil.renderMarkdown(
                        buildString {
                            appendLine()
                            appendLine("✅ Step $stepNumber complete (${stepTime / 1000.0}s)")
                            if (lastStep.next_question.isNullOrBlank() || lastStep.confidence >= 0.9) {
                                appendLine()
                                appendLine("*Reasoning complete, generating summary...*")
                            } else if (stepNumber < maxSteps) {
                                appendLine()
                                appendLine("*Processing step ${stepNumber + 1}...*")
                            }
                        },
                        ui = ui
                    )
                )
                task.update()
                log.info("Step $stepNumber completed in ${stepTime}ms")

                if (lastStep.next_question.isNullOrBlank() || lastStep.confidence >= 0.9) {
                    // Check if we should continue - only stop if we have high confidence AND no next question
                    // OR if next_question explicitly indicates completion
                    val shouldStop = (lastStep.next_question.isNullOrBlank() && lastStep.confidence >= 0.9) ||
                            lastStep.next_question?.lowercase()?.contains("complete") == true ||
                            lastStep.next_question?.lowercase()?.contains("no further") == true

                    if (shouldStop) {
                        log.info("Reasoning complete: next_question=${lastStep.next_question}, confidence=${lastStep.confidence}")
                        break
                    }
                }

                if (lastStep.next_question.isNullOrBlank()) {
                    log.warn("Step $stepNumber has no next question but confidence is low (${lastStep.confidence}), continuing anyway")
                    currentQuestion = "Continue reasoning about: ${executionConfig?.problem_statement}"
                } else {
                    currentQuestion = lastStep.next_question
                }
                stepNumber++
            }


            // Generate final summary
            log.info("Generating summary of reasoning chain")
            val summaryTask = task.ui.newTask(false)
            tabs["Summary"] = summaryTask.placeholder

            summaryTask.add(
                MarkdownUtil.renderMarkdown(
                    buildString {
                        appendLine("# Summary")
                        appendLine()
                        appendLine("**Status:** Generating comprehensive summary...")
                        appendLine()
                    },
                    ui = ui
                )
            )
            task.update()

            val summary = generateSummary(summaryTask, reasoningChain, problemStatement, api)
            log.debug("Summary generated: ${summary.length} characters")

            summaryTask.add(
                MarkdownUtil.renderMarkdown(
                    """
                    |## Final Summary
                    |
                    |$summary
                    |
                    |---
                    |
                    |**Status:** ✅ Complete
                    """.trimMargin(),
                    ui = ui
                )
            )
            summaryTask.complete()
            task.update()

            val finalResult = formatReasoningChain(reasoningChain, summary)
            val totalTime = System.currentTimeMillis() - startTime
            val avgStepTime = if (stepTimes.isNotEmpty()) stepTimes.average() else 0.0
            log.info("ChainOfThoughtTask completed: total_time=${totalTime}ms, steps=${reasoningChain.size}, avg_step_time=${avgStepTime}ms, output_size=${finalResult.length} chars")

            // Update overview with completion stats
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    buildString {
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("## ✅ Reasoning Complete")
                        appendLine()
                        appendLine("**Total Time:** ${totalTime / 1000.0}s")
                        appendLine()
                        appendLine("**Steps Completed:** ${reasoningChain.size}")
                        appendLine()
                        appendLine("**Average Step Time:** ${avgStepTime / 1000.0}s")
                        appendLine()
                        appendLine(
                            "**Final Confidence:** ${
                                String.format(
                                    "%.1f%%",
                                    reasoningChain.lastOrNull()?.confidence?.times(100) ?: 0.0
                                )
                            }"
                        )
                        appendLine()
                        appendLine(
                            "**Completed:** ${
                                java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            }"
                        )
                    },
                    ui = ui
                )
            )
            task.update()

            task.complete("Completed ${reasoningChain.size} reasoning steps in ${totalTime / 1000}s.")

            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error in chain of thought reasoning", e)
            task.error(e)

            // Update overview with error
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    buildString {
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("## ❌ Error Occurred")
                        appendLine()
                        appendLine("**Error:** ${e.message}")
                        appendLine()
                        appendLine("**Type:** ${e.javaClass.simpleName}")
                        appendLine()
                        appendLine("**Steps Completed:** ${reasoningChain.size} of $maxSteps")
                    },
                    ui = ui
                )
            )
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Chain of Thought Reasoning")
                appendLine()
                appendLine("**Problem:** $problemStatement")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Steps Completed:** ${reasoningChain.size} of $maxSteps")
                if (reasoningChain.isNotEmpty()) {
                    appendLine()
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(formatReasoningChain(reasoningChain, "Reasoning incomplete due to error"))
                }
            }
            resultFn(errorOutput)
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
            parsingChatter = orchestrationConfig.parsingChatter,
        )

      var step: ReasoningStep? = reasoningAgent.answer(listOf(question)).obj.copy(step_number = stepNumber)
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
            parsingChatter = orchestrationConfig.parsingChatter,
        )

      var validation: StepValidation? = validationAgent.answer(listOf("Validate step ${step.step_number}")).obj
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

      var summary = summaryAgent.answer(listOf("Generate summary"))
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
        val relatedFiles = executionConfig?.related_files ?: emptyList()
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