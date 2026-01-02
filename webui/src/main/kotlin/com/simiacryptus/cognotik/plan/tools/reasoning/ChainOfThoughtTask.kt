package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems

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
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val related_files: List<String> = emptyList(),
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ChainOfThought.name,
        task_description = problem_statement,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (problem_statement.isBlank()) {
                return "problem_statement cannot be blank"
            }
            if (reasoning_depth < 1 || reasoning_depth > 20) {
                return "reasoning_depth must be between 1 and 20, got: $reasoning_depth"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class ReasoningStep(
        val step_number: Int = 0,
        val reasoning: String = "",
        val conclusion: String = "",
        val confidence: Double = 0.0,
        val next_question: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (step_number < 1) {
                return "step_number must be positive, got: $step_number"
            }
            if (reasoning.isBlank()) {
                return "reasoning cannot be blank"
            }
            if (conclusion.isBlank()) {
                return "conclusion cannot be blank"
            }
            if (confidence < 0.0 || confidence > 1.0) {
                return "confidence must be between 0.0 and 1.0, got: $confidence"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class StepValidation(
        val is_valid: Boolean = true,
        val issues: List<String>? = null,
        val suggestions: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
 ChainOfThought - Break down complex problems into explicit reasoning steps
  ** Optionally, list input files (supports glob patterns) to be examined for context
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
        val transcript = task.transcript()
        val inputFileContent = getInputFileCode()

        val problemStatement = executionConfig?.problem_statement
        if (problemStatement?.isBlank() != false) {
            log.error("No problem statement specified")
            task.complete("CONFIGURATION ERROR: No problem statement specified")
            resultFn("CONFIGURATION ERROR: No problem statement specified")
            return
        }

        val maxSteps = executionConfig.reasoning_depth.coerceIn(1, 20)
        val validateSteps = executionConfig.validate_steps
        log.info("Configuration: maxSteps=$maxSteps, validateSteps=$validateSteps")

        val ui = task.ui
        val api = defaultSmart
        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)
        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Chain of Thought Reasoning")
            appendLine()
            appendLine("**Problem Statement:** $problemStatement")
            appendLine()
            appendLine("**Max Steps:** $maxSteps")
            appendLine()
            appendLine("**Validate Steps:** ${if (validateSteps) "Yes" else "No"}")
            appendLine()
            if (inputFileContent.isNotBlank()) {
                appendLine("## Input Files")
                appendLine()
                appendLine(inputFileContent)
                appendLine()
            }
        }

        transcript?.write(overviewContent.toByteArray())
        transcript?.flush()

        overviewTask.header("Chain of Thought Reasoning")
        overviewTask.add("<b>Problem Statement:</b> $problemStatement")
        overviewTask.add("<b>Max Steps:</b> $maxSteps")
        overviewTask.add("<b>Validate Steps:</b> ${if (validateSteps) "Yes" else "No"}")

        if (inputFileContent.isNotBlank()) {
            overviewTask.expandable("Input Files", MarkdownUtil.renderMarkdown(inputFileContent, ui = ui))
        }

        overviewTask.add(
            "<b>Started:</b> ${
                java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }"
        )
        overviewTask.append("<hr/>")
        overviewTask.header("Progress", level = 2)
        overviewTask.add("<i>Initializing reasoning process...</i>")
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
            val contextTask = tabs.newTask("Context")
            contextTask.header("Context")
            if (priorContext.isNotBlank()) {
                contextTask.expandable("Previous Tasks", MarkdownUtil.renderMarkdown(priorContext, ui = ui))
            }
            if (contextFiles.isNotBlank()) {
                contextTask.expandable("Related Files", MarkdownUtil.renderMarkdown(contextFiles, ui = ui))
            }

            // Write context to transcript
            transcript?.write("\n\n# Context\n\n".toByteArray())
            if (priorContext.isNotBlank()) transcript?.write("## Previous Tasks\n\n$priorContext\n\n".toByteArray())
            if (contextFiles.isNotBlank()) transcript?.write("## Related Files\n\n$contextFiles\n\n".toByteArray())
            transcript?.flush()
            task.update()
        }

        // Update overview with initialization complete
        overviewTask.add("✅ Initialization complete<br/><i>Starting reasoning steps...</i>")
        task.update()

        val reasoningChain = mutableListOf<ReasoningStep>()
        var currentQuestion = problemStatement
        var stepNumber = 1
        val stepTimes = mutableListOf<Long>()

        try {
            while (stepNumber <= maxSteps) {
                val stepStartTime = System.currentTimeMillis()
                log.info("Starting reasoning step $stepNumber of $maxSteps")

                val stepTask = tabs.newTask("Step $stepNumber")
                stepTask.header("Step $stepNumber of $maxSteps")
                stepTask.add("<b>Status:</b> Processing...")
                stepTask.add("<b>Question:</b> $currentQuestion")
                stepTask.append("<hr/>")
                task.update()
                // Write step header to transcript
                transcript?.write("\n\n# Step $stepNumber of $maxSteps\n\n".toByteArray())
                transcript?.write("**Question:** $currentQuestion\n\n".toByteArray())
                transcript?.flush()


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
                    stepTask.add("<i>Validating step...</i>")
                    task.update()

                    val validation = validateStep(stepTask, step, reasoningChain, api)
                    log.debug("Validation result for step $stepNumber: valid=${validation.is_valid}")

                    if (!validation.is_valid) {
                        log.warn("Step $stepNumber failed validation, attempting to regenerate")
                        stepTask.add(
                            """
                            <b>⚠️ Validation Failed</b><br/>
                            <b>Issues:</b>
                            <ul>${validation.issues?.joinToString("") { "<li>$it</li>" } ?: "<li>Unknown issues</li>"}</ul>
                            <b>Suggestions:</b> ${validation.suggestions ?: "None"}<br/>
                            <i>Regenerating step with feedback...</i>
                            """.trimIndent(),
                            additionalClasses = "alert alert-warning"
                        )
                        // Write validation failure to transcript
                        transcript?.write("### ⚠️ Validation Failed\n\n".toByteArray())
                        transcript?.write("**Issues**: ${validation.issues?.joinToString(", ")}\n\n".toByteArray())
                        transcript?.write("**Suggestions**: ${validation.suggestions}\n\n".toByteArray())
                        transcript?.flush()
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
                // Write completed step to transcript
                transcript?.write("**Reasoning**: ${lastStep.reasoning}\n\n".toByteArray())
                transcript?.write("**Conclusion**: ${lastStep.conclusion}\n\n".toByteArray())
                transcript?.write(
                    "**Confidence**: ${
                        String.format(
                            "%.1f%%",
                            lastStep.confidence * 100
                        )
                    }\n\n".toByteArray()
                )
                if (lastStep.next_question != null) {
                    transcript?.write("**Next Question**: ${lastStep.next_question}\n\n".toByteArray())
                }
                transcript?.flush()
                // Mark step as complete
                stepTask.append("<hr/>")
                stepTask.add(
                    "<b>Status:</b> ✅ Complete<br/><b>Processing Time:</b> ${stepTime / 1000.0}s",
                    additionalClasses = "alert alert-success"
                )
                stepTask.complete()
                task.update()
                // Update overview with progress
                val nextAction = if (lastStep.next_question.isNullOrBlank() || lastStep.confidence >= 0.9) {
                    "<i>Reasoning complete, generating summary...</i>"
                } else if (stepNumber < maxSteps) {
                    "<i>Processing step ${stepNumber + 1}...</i>"
                } else {
                    ""
                }
                overviewTask.add("✅ Step $stepNumber complete (${stepTime / 1000.0}s)<br/>$nextAction")
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
            val summaryTask = tabs.newTask("Summary")

            summaryTask.header("Summary")
            summaryTask.add("<b>Status:</b> Generating comprehensive summary...")
            task.update()

            val summary = generateSummary(summaryTask, reasoningChain, problemStatement, api)
            log.debug("Summary generated: ${summary.length} characters")

            summaryTask.header("Final Summary", level = 2)
            summaryTask.add(MarkdownUtil.renderMarkdown(summary, ui = ui))
            summaryTask.append("<hr/>")
            summaryTask.add("<b>Status:</b> ✅ Complete", additionalClasses = "alert alert-success")

            // Write summary to transcript
            transcript?.write("\n\n# Final Summary\n\n$summary\n\n".toByteArray())
            transcript?.flush()
            summaryTask.complete()
            task.update()

            val finalResult = formatReasoningChain(reasoningChain, summary)
            val totalTime = System.currentTimeMillis() - startTime
            val avgStepTime = if (stepTimes.isNotEmpty()) stepTimes.average() else 0.0
            log.info("ChainOfThoughtTask completed: total_time=${totalTime}ms, steps=${reasoningChain.size}, avg_step_time=${avgStepTime}ms, output_size=${finalResult.length} chars")

            // Update overview with completion stats
            overviewTask.append("<hr/>")
            overviewTask.header("✅ Reasoning Complete", level = 2)
            overviewTask.add(
                """
                <b>Total Time:</b> ${totalTime / 1000.0}s<br/>
                <b>Steps Completed:</b> ${reasoningChain.size}<br/>
                <b>Average Step Time:</b> ${avgStepTime / 1000.0}s<br/>
                <b>Final Confidence:</b> ${
                    String.format(
                        "%.1f%%",
                        reasoningChain.lastOrNull()?.confidence?.times(100) ?: 0.0
                    )
                }<br/>
                <b>Completed:</b> ${
                    java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }
            """.trimIndent()
            )
            task.update()
            transcript?.close()

            task.complete("Completed ${reasoningChain.size} reasoning steps in ${totalTime / 1000}s.")

            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error in chain of thought reasoning", e)
            task.error(e)

            // Update overview with error
            overviewTask.append("<hr/>")
            overviewTask.header("❌ Error Occurred", level = 2)
            overviewTask.add(
                """
                <b>Error:</b> ${e.message}<br/>
                <b>Type:</b> ${e.javaClass.simpleName}<br/>
                <b>Steps Completed:</b> ${reasoningChain.size} of $maxSteps
            """.trimIndent(), additionalClasses = "alert alert-danger"
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
            // Write error to transcript and close
            transcript?.write("\n\n# Error\n\n${e.message}\n\n".toByteArray())
            transcript?.write("**Steps Completed:** ${reasoningChain.size} of $maxSteps\n".toByteArray())
            transcript?.close()
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
            parsingChatter = defaultFast,
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
            parsingChatter = defaultFast,
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

    private fun getInputFileCode() = (executionConfig?.related_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = file.readText()
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ChainOfThoughtTask::class.java)
        val ChainOfThought = TaskType(
            "ChainOfThought",
            "Reasoning",
            ChainOfThoughtTask::class.java,
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
            """,
        )
    }
}