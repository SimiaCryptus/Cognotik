package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.file.FileSystems

class MetaCognitiveReflectionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: MetaCognitiveReflectionTaskExecutionConfigData?
) : AbstractTask<MetaCognitiveReflectionTask.MetaCognitiveReflectionTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class MetaCognitiveReflectionTaskExecutionConfigData(
        @Description("The ID of the task whose reasoning process should be reflected upon")
        val subject_task_id: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as context for reflection")
        val input_files: List<String>? = null,
        @Description("Additional context or questions to guide the reflection")
        val reflection_questions: List<String>? = null,
        @Description("Whether to include file context in the reflection analysis")
        val include_file_context: Boolean = true,
        @Description("Aspects to evaluate: 'assumptions', 'biases', 'alternatives', 'confidence', 'completeness', 'logic'")
        val reflection_aspects: List<String>? = listOf("assumptions", "biases", "alternatives", "confidence"),
        @Description("Whether to suggest improvements to the reasoning process")
        val suggest_improvements: Boolean = true,
        @Description("Whether to identify knowledge gaps and uncertainties")
        val identify_gaps: Boolean = true,
        @Description("Whether to evaluate the confidence level of conclusions")
        val evaluate_confidence: Boolean = true,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = MetaCognitiveReflection.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (subject_task_id.isNullOrBlank()) {
                return "subject_task_id must not be null or blank"
            }
            if (reflection_aspects.isNullOrEmpty()) {
                return "reflection_aspects must not be null or empty"
            }
            if (reflection_aspects.isNullOrEmpty()) {
                return "reflection_aspects must not be null or empty"
            }
            val validAspects = setOf("assumptions", "biases", "alternatives", "confidence", "completeness", "logic")
            val invalidAspects = reflection_aspects.filterNot { it in validAspects }
            if (invalidAspects.isNotEmpty()) {
                return "Invalid reflection_aspects: ${invalidAspects.joinToString(", ")}. Valid aspects are: ${
                    validAspects.joinToString(
                        ", "
                    )
                }"
            }
            return ValidatedObject.validateFields(this)
        }
    }
    data class ReflectionAnalysis(
        @Description("Identified underlying assumptions")
        val assumptions: List<String> = emptyList(),
        @Description("Potential cognitive biases detected")
        val biases: List<String> = emptyList(),
        @Description("Alternative approaches or perspectives")
        val alternatives: List<String> = emptyList(),
        @Description("Confidence level and rationale")
        val confidence_assessment: String = "",
        @Description("Logical consistency and completeness check")
        val logical_check: String = "",
        @Description("Actionable suggestions for improvement")
        val improvement_suggestions: List<String> = emptyList()
    ) : ValidatedObject


    override fun promptSegment(): String {
        return """
MetaCognitiveReflection - Reflect on and critique reasoning processes
  ** Specify the subject_task_id to identify which task's reasoning to reflect upon
  ** Choose reflection_aspects from:
     - 'assumptions': Identify underlying assumptions
     - 'biases': Detect potential cognitive biases
     - 'alternatives': Consider alternative approaches
     - 'confidence': Evaluate certainty levels
     - 'completeness': Check for missing considerations
     - 'logic': Verify logical consistency
  ** Optionally, list input files (supports glob patterns) to provide context
  ** Optionally, specify reflection_questions to guide the analysis
  ** Enable include_file_context to incorporate file content in reflection
  ** Enable suggest_improvements to get actionable recommendations
  ** Enable identify_gaps to surface knowledge uncertainties
  ** Enable evaluate_confidence to assess conclusion reliability
  ** This task implements "thinking about thinking" for quality improvement
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
        log.info("Starting MetaCognitiveReflection task for subject_task_id: ${executionConfig?.subject_task_id}")
        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }


        val subjectTaskId = executionConfig?.subject_task_id
        if (subjectTaskId.isNullOrBlank()) {
            log.error("Configuration error: No subject_task_id specified")
            task.safeComplete("CONFIGURATION ERROR: No subject_task_id specified", log)
            resultFn("CONFIGURATION ERROR: No subject_task_id specified for reflection")
            return
        }

        val executionState = agent.executionState
        if (executionState == null) {
            log.error("Execution state not available")
            task.safeComplete("ERROR: Execution state not available", log)
            resultFn("ERROR: Execution state not available")
            return
        }

        val subjectTaskResult = executionState.taskResult[subjectTaskId]
        if (subjectTaskResult.isNullOrBlank()) {
            log.error("No result found for task: $subjectTaskId")
            task.safeComplete("ERROR: No result found for task '$subjectTaskId'", log)
            resultFn("ERROR: No result found for task '$subjectTaskId'")
            return
        }

        val api = defaultSmart ?: return

        val (transcriptLink, transcript) = initializeTranscript(task, "MetaReflection")
        transcript?.let { stream ->


            writeToTranscript(stream, "# Meta-Cognitive Reflection Transcript\n\n")
            writeToTranscript(stream, "## Subject Task: `$subjectTaskId`\n\n")
            writeToTranscript(stream, "**Timestamp**: ${java.time.Instant.now()}\n\n")
        }


        val tabbedDisplay = createTabbedDisplay(task)
        val overviewTask = tabbedDisplay.newTask("Overview")

        overviewTask.header("Meta-Cognitive Reflection on Task: $subjectTaskId", level = 2)
        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            val contextTask = tabbedDisplay.newTask("Context")
            contextTask.header("Prior Context", level = 3)
            contextTask.safeComplete(
                MarkdownUtil.renderMarkdown(
                    """
          |```
          |${priorContext.truncateForDisplay()}
          |```
          """.trimMargin(), ui = contextTask.ui
                ), log
            )
        }
        // Gather file context if enabled
        val fileContext = if (executionConfig?.include_file_context == true) {
            getInputFileContext(executionConfig?.input_files ?: listOf())
        } else {
            ""
        }
        // Gather messages context
        val messagesContext = messages.filter { it.isNotBlank() }.joinToString("\n\n")
        // Gather reflection questions
        val questionsContext = if (!executionConfig?.reflection_questions.isNullOrEmpty()) {
            "## Reflection Questions:\n\n" + executionConfig?.reflection_questions?.mapIndexed { idx, q ->
                "${idx + 1}. $q"
            }?.joinToString("\n")
        } else {
            ""
        }
        transcript?.let { stream ->
            writeToTranscript(stream, "## Input Context\n\n$fileContext\n\n$messagesContext\n\n$questionsContext\n\n")
        }


        val reflectionAspects =
            executionConfig?.reflection_aspects ?: listOf("assumptions", "biases", "alternatives", "confidence")
        val aspectsText = reflectionAspects.joinToString(", ")
        // Step 3: Build reflection prompt

        val prompt = buildReflectionPrompt(
            subjectTaskId = subjectTaskId,
            subjectTaskResult = subjectTaskResult,
            priorContext = priorContext,
            fileContext = fileContext,
            messagesContext = messagesContext,
            questionsContext = questionsContext,
            reflectionAspects = reflectionAspects,
            suggestImprovements = executionConfig?.suggest_improvements ?: true,
            identifyGaps = executionConfig?.identify_gaps ?: true,
            evaluateConfidence = executionConfig?.evaluate_confidence ?: true
        )

        overviewTask.header("Reflection Parameters", level = 3)
        overviewTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |**Subject Task**: `$subjectTaskId`
                |
                |**Reflection Aspects**: $aspectsText
         |
         |**Include File Context**: ${executionConfig?.include_file_context ?: true}
         |
         |**Input Files**: ${executionConfig?.input_files?.joinToString(", ") ?: "None"}
         |
         |**Reflection Questions**: ${executionConfig?.reflection_questions?.size ?: 0} questions
                |
                |**Suggest Improvements**: ${executionConfig?.suggest_improvements ?: true}
                |
                |**Identify Gaps**: ${executionConfig?.identify_gaps ?: true}
                |
                |**Evaluate Confidence**: ${executionConfig?.evaluate_confidence ?: true}
                """.trimMargin(),
                ui = overviewTask.ui
            )
        )
        transcript?.let { stream ->
            stream.write("\n## Reflection Parameters\n\n".toByteArray())
            stream.write("- **Subject Task**: `$subjectTaskId`\n".toByteArray())
            stream.write("- **Reflection Aspects**: $aspectsText\n".toByteArray())
            stream.write("- **Include File Context**: ${executionConfig?.include_file_context ?: true}\n".toByteArray())
            stream.write("- **Input Files**: ${executionConfig?.input_files?.joinToString(", ") ?: "None"}\n".toByteArray())
            stream.write("- **Reflection Questions**: ${executionConfig?.reflection_questions?.size ?: 0}\n".toByteArray())
            stream.write("- **Suggest Improvements**: ${executionConfig?.suggest_improvements ?: true}\n".toByteArray())
            stream.write("- **Identify Gaps**: ${executionConfig?.identify_gaps ?: true}\n".toByteArray())
            stream.write("- **Evaluate Confidence**: ${executionConfig?.evaluate_confidence ?: true}\n\n".toByteArray())
        }

        overviewTask.safeComplete("", log)
        // Step 4: Create agent and perform reflection
        val reflectionTask = tabbedDisplay.newTask("Reflection Analysis")
        reflectionTask.header("Analyzing reasoning process...", level = 3)


        val reflectionAgent = ParsedAgent(
            resultClass = ReflectionAnalysis::class.java,
            prompt = buildSystemPrompt() + "\n\n" + prompt,
            model = api,
            parsingChatter = defaultFast
        )

        try {
            val analysis = reflectionAgent.answer(listOf("Perform reflection analysis")).obj
            transcript?.let { stream ->
                writeToTranscript(stream, "\n## Reflection Analysis\n\n")
                writeToTranscript(stream, JsonUtil.toJson(analysis))
                writeToTranscript(stream, "\n\n")
            }


            reflectionTask.header("Reflection Analysis", level = 3)
            val analysisMarkdown = buildString {
                appendLine("### Assumptions\n${analysis.assumptions.joinToString("\n") { "- $it" }}")
                appendLine("\n### Biases\n${analysis.biases.joinToString("\n") { "- $it" }}")
                appendLine("\n### Alternatives\n${analysis.alternatives.joinToString("\n") { "- $it" }}")
                appendLine("\n### Confidence\n${analysis.confidence_assessment}")
                appendLine("\n### Logic & Completeness\n${analysis.logical_check}")
                appendLine("\n### Improvements\n${analysis.improvement_suggestions.joinToString("\n") { "- $it" }}")
            }
            reflectionTask.add(MarkdownUtil.renderMarkdown(analysisMarkdown, ui = reflectionTask.ui))
            reflectionTask.safeComplete("✅ Reflection analysis complete", log)

            val summaryTask = tabbedDisplay.newTask("Summary")


            val summary = buildString {
                appendLine("**Key Insights:**")
                analysis.improvement_suggestions.take(3).forEach { appendLine("- $it") }
                appendLine("\n**Confidence:** ${analysis.confidence_assessment.take(200)}...")
            }

            transcript?.let { stream ->
                writeToTranscript(stream, "\n## Summary\n\n")
                writeToTranscript(stream, summary)
                writeToTranscript(stream, "\n\n---\n\n")
                writeToTranscript(stream, "**Duration**: ${System.currentTimeMillis() - startTime}ms\n")
                writeToTranscript(stream, "**Status**: Completed successfully\n")
            }


            summaryTask.header("Summary", level = 3)
            summaryTask.add(MarkdownUtil.renderMarkdown(summary, ui = summaryTask.ui))
            summaryTask.safeComplete(
                MarkdownUtil.renderMarkdown(
                    """
                    |---
                    |
                    |**Meta-cognitive reflection completed successfully.**
                    """.trimMargin(),
                    ui = summaryTask.ui
                ), log
            )

            // Step 6: Complete main task
            task.safeComplete("Meta-cognitive reflection completed for task: $subjectTaskId", log)

            val duration = System.currentTimeMillis() - startTime
            log.info("MetaCognitiveReflection task completed successfully for subject_task_id: $subjectTaskId in ${duration}ms. Summary length: ${summary.length}")
            val finalOutput =
                "Meta-cognitive reflection completed. View detailed analysis: <a href='$transcriptLink' target='_blank'>transcript.md</a> <a href='${
                    transcriptLink.removeSuffix(".md")
                }.html' target='_blank'>html</a>\n\n$summary"
            resultFn(finalOutput)
            transcript?.close()


        } catch (e: Exception) {
            log.error("Error during meta-cognitive reflection", e)
            transcript?.let { stream ->
                writeToTranscript(stream, "\n## ❌ Error\n\n")
                writeToTranscript(stream, "```\n${e.message}\n```\n")
            }
            transcript?.close()

            task.error(e)
            reflectionTask.error(e)
            task.add(
                MarkdownUtil.renderMarkdown(
                    """
          |### ❌ Error During Reflection
          |
          |An error occurred while performing meta-cognitive reflection:
          |
          |```
          |${e.message}
          |```
          |
          |Please check the logs for more details.
          """.trimMargin(),
                    ui = task.ui
                )
            )
            resultFn("ERROR: ${e.message}")
        }
    }



    private fun getInputFileContext(inputFiles: List<String>): String {
        if (inputFiles.isEmpty()) return ""
        return inputFiles.flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            }
        }.filter { it.isFile && it.exists() }
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
    }


    private fun buildSystemPrompt(): String {
        return """
 You are a meta-cognitive analyst specializing in critical thinking and reasoning evaluation.
Your role is to provide thoughtful, constructive reflection on reasoning processes.
You identify strengths, weaknesses, assumptions, biases, and opportunities for improvement.
You are thorough, objective, and focused on enhancing the quality of thinking.
    """.trimIndent()
    }

    private fun buildReflectionPrompt(
        subjectTaskId: String,
        subjectTaskResult: String,
        priorContext: String,
        fileContext: String,
        messagesContext: String,
        questionsContext: String,
        reflectionAspects: List<String>,
        suggestImprovements: Boolean,
        identifyGaps: Boolean,
        evaluateConfidence: Boolean
    ): String {

        if (fileContext.isNotBlank()) """
## File Context:
The following files provide additional context for the reflection:
$fileContext
""" else ""
        if (messagesContext.isNotBlank()) """
## Messages Context:
The following messages were provided as input:
$messagesContext
""" else ""
        if (questionsContext.isNotBlank()) """
$questionsContext
""" else ""

        val contextBlock = if (priorContext.isNotBlank()) """
## Overall Context from Prior Steps:
The following context was available to the task being analyzed. Consider this when evaluating its reasoning.
```
$priorContext
```""" else ""

        return """
You are a meta-cognitive analyst tasked with reflecting on and critiquing a reasoning process.${contextBlock}

## Subject Task: $subjectTaskId

## Task Result to Reflect Upon:
$subjectTaskResult

## Your Reflection Should Address:

${buildAspectInstructions(reflectionAspects)}

${
            if (suggestImprovements) """
## Improvement Suggestions:
Provide specific, actionable recommendations to enhance the reasoning quality:
- What could be done differently?
- What additional considerations would strengthen the solution?
- Are there better approaches or methodologies?
""" else ""
        }

${
            if (identifyGaps) """
## Knowledge Gaps:
Identify areas where information is missing or uncertain:
- What assumptions lack verification?
- What data or evidence would be valuable?
- What questions remain unanswered?
""" else ""
        }

${
            if (evaluateConfidence) """
## Confidence Assessment:
Evaluate the reliability and certainty of the conclusions:
- Rate confidence levels (high/medium/low) for key conclusions
- Identify factors that increase or decrease confidence
- Highlight areas of uncertainty
""" else ""
        }

## Output Format:
Provide a structured reflection with clear sections for each aspect analyzed.
Use markdown formatting with headers, bullet points, and emphasis where appropriate.
Be specific, constructive, and actionable in your critique.

Begin your meta-cognitive reflection now:
        """.trimIndent()
    }

    private fun buildAspectInstructions(aspects: List<String>): String {
        val instructions = mutableListOf<String>()

        if ("assumptions" in aspects) {
            instructions.add(
                """
### 1. Underlying Assumptions
- What assumptions were made (explicit or implicit)?
- Are these assumptions valid and well-founded?
- What happens if these assumptions are incorrect?
            """.trimIndent()
            )
        }

        if ("biases" in aspects) {
            instructions.add(
                """
### 2. Cognitive Biases
- Are there signs of confirmation bias, anchoring, or availability bias?
- Does the reasoning favor certain perspectives unfairly?
- Are alternative viewpoints adequately considered?
            """.trimIndent()
            )
        }

        if ("alternatives" in aspects) {
            instructions.add(
                """
### 3. Alternative Approaches
- What other methods or solutions were not explored?
- Could different frameworks yield better results?
- Are there unconsidered trade-offs?
            """.trimIndent()
            )
        }

        if ("confidence" in aspects) {
            instructions.add(
                """
### 4. Confidence and Certainty
- How certain can we be about the conclusions?
- What evidence supports or undermines confidence?
- Where is uncertainty highest?
            """.trimIndent()
            )
        }

        if ("completeness" in aspects) {
            instructions.add(
                """
### 5. Completeness
- Are all relevant factors considered?
- What might be missing from the analysis?
- Are edge cases addressed?
            """.trimIndent()
            )
        }

        if ("logic" in aspects) {
            instructions.add(
                """
### 6. Logical Consistency
- Is the reasoning logically sound?
- Are there any logical fallacies or contradictions?
- Do conclusions follow from premises?
            """.trimIndent()
            )
        }

        return instructions.joinToString("\n\n")
    }

    private fun generateReflectionSummary(reflectionResult: String): String {
        // Extract key points for a concise summary
        val lines = reflectionResult.lines()
        val keyPoints = mutableListOf<String>()

        // Look for bullet points or numbered items
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.matches(Regex("^\\d+\\."))) {
                if (trimmed.length > 10) { // Avoid very short items
                    keyPoints.add(trimmed.removePrefix("-").removePrefix("*").trim())
                }
            }
        }

        return if (keyPoints.isNotEmpty()) {
            "**Key Insights:**\n" + keyPoints.take(5).joinToString("\n") { "- $it" }
        } else {
            "Reflection analysis completed. See detailed results above."
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MetaCognitiveReflectionTask::class.java)
        val MetaCognitiveReflection = TaskType(
            "MetaCognitiveReflection",
            "Reasoning",
            MetaCognitiveReflectionTask::class.java,
            MetaCognitiveReflectionTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Reflect on and critique reasoning processes",
            """
              Performs meta-cognitive reflection on task reasoning and solutions.
              <ul>
                <li>Analyzes assumptions and identifies biases</li>
                <li>Evaluates alternative approaches</li>
                <li>Assesses confidence and certainty levels</li>
                <li>Identifies knowledge gaps and uncertainties</li>
                <li>Suggests improvements to reasoning quality</li>
                <li>Checks logical consistency and completeness</li>
              </ul>
            """,
        )
    }
}