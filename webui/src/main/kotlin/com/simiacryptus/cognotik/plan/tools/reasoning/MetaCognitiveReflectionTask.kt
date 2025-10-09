package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

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
    )

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
        val subjectTaskId = executionConfig?.subject_task_id
        if (subjectTaskId.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No subject_task_id specified for reflection")
            return
        }

        val executionState = agent.executionState
        if (executionState == null) {
            resultFn("ERROR: Execution state not available")
            return
        }

        val subjectTaskResult = executionState.taskResult[subjectTaskId]
        if (subjectTaskResult.isNullOrBlank()) {
            resultFn("ERROR: No result found for task '$subjectTaskId'")
            return
        }

        val newTask = task.ui.newTask(false)
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(
            MarkdownUtil.renderMarkdown(
                "## Meta-Cognitive Reflection on Task: `$subjectTaskId`",
                ui = ui
            )
        )

        val reflectionAspects =
            executionConfig?.reflection_aspects ?: listOf("assumptions", "biases", "alternatives", "confidence")
        val aspectsText = reflectionAspects.joinToString(", ")

        val prompt = buildReflectionPrompt(
            subjectTaskId = subjectTaskId,
            subjectTaskResult = subjectTaskResult,
            reflectionAspects = reflectionAspects,
            suggestImprovements = executionConfig?.suggest_improvements ?: true,
            identifyGaps = executionConfig?.identify_gaps ?: true,
            evaluateConfidence = executionConfig?.evaluate_confidence ?: true
        )

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |### Reflection Parameters
                |
                |**Subject Task**: `$subjectTaskId`
                |
                |**Reflection Aspects**: $aspectsText
                |
                |**Suggest Improvements**: ${executionConfig?.suggest_improvements ?: true}
                |
                |**Identify Gaps**: ${executionConfig?.identify_gaps ?: true}
                |
                |**Evaluate Confidence**: ${executionConfig?.evaluate_confidence ?: true}
                """.trimMargin(),
                ui = ui
            )
        )

        val chatAgent = ChatAgent(
            prompt = promptSegment(),
            model = api,
        )

        var reflectionResult: String? = null
        try {
            Retryable(newTask, newTask.ui) { sb ->
                reflectionResult = chatAgent.answer(listOf(prompt))
                sb.append(reflectionResult ?: "")
                sb.toString()
            }

            newTask.add(
                MarkdownUtil.renderMarkdown(
                    """
                    |### Reflection Analysis
                    |
                    |$reflectionResult
                    """.trimMargin(),
                    ui = ui
                )
            )

            val summary = generateReflectionSummary(reflectionResult!!)

            newTask.complete(
                MarkdownUtil.renderMarkdown(
                    """
                    |### Summary
                    |
                    |$summary
                    |
                    |---
                    |
                    |**Meta-cognitive reflection completed successfully.**
                    """.trimMargin(),
                    ui = ui
                )
            )

            resultFn(reflectionResult)

        } catch (e: Exception) {
            log.error("Error during meta-cognitive reflection", e)
            newTask.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun buildReflectionPrompt(
        subjectTaskId: String,
        subjectTaskResult: String,
        reflectionAspects: List<String>,
        suggestImprovements: Boolean,
        identifyGaps: Boolean,
        evaluateConfidence: Boolean
    ): String {
        return """
You are a meta-cognitive analyst tasked with reflecting on and critiquing a reasoning process.

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
            """
        )
    }
}