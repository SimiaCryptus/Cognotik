package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

class AnalogicalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: AnalogicalReasoningTaskExecutionConfigData?
) : AbstractTask<AnalogicalReasoningTask.AnalogicalReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class AnalogicalReasoningTaskExecutionConfigData(
        @Description("The source domain to draw analogies from (e.g., 'biological systems', 'urban planning', 'musical composition')")
        val source_domain: String? = null,
        @Description("The target problem to solve using analogies")
        val target_problem: String? = null,
        @Description("Number of analogies to generate and explore")
        val num_analogies: Int = 3,
        @Description("Whether to validate analogy mappings for structural consistency")
        val validate_mappings: Boolean = true,
        @Description("Additional context files to inform the reasoning process")
        val related_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = AnalogicalReasoning.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class AnalogyMapping(
        @Description("The source concept from the source domain")
        val source_concept: String,
        @Description("The target concept in the problem domain")
        val target_concept: String,
        @Description("Explanation of how the concepts map to each other")
        val mapping_rationale: String,
        @Description("Structural similarities between source and target")
        val structural_similarities: List<String>,
        @Description("Key differences or limitations of the analogy")
        val limitations: List<String>
    )

    data class Analogy(
        @Description("Title of the analogy")
        val title: String,
        @Description("Description of the source domain concept")
        val source_description: String,
        @Description("How this applies to the target problem")
        val application: String,
        @Description("Detailed mappings between source and target concepts")
        val mappings: List<AnalogyMapping>,
        @Description("Insights gained from this analogy")
        val insights: List<String>,
        @Description("Potential solutions suggested by this analogy")
        val suggested_solutions: List<String>,
        @Description("Confidence score (0-1) in the validity of this analogy")
        val confidence: Double
    )

    data class AnalogicalReasoningResult(
        @Description("List of generated analogies")
        val analogies: List<Analogy>,
        @Description("Synthesis of insights across all analogies")
        val synthesized_insights: List<String>,
        @Description("Recommended approach based on analogical reasoning")
        val recommended_approach: String,
        @Description("Validation results if validation was requested")
        val validation_notes: String? = null
    )

    override fun promptSegment(): String {
        return """
AnalogicalReasoning - Solve problems by finding and applying analogies from different domains
  ** Specify a source domain to draw analogies from (e.g., biological systems, architecture, music)
  ** Provide the target problem you want to solve
  ** Configure the number of analogies to generate (default: 3)
  ** Optionally enable mapping validation for structural consistency
  ** The task will:
     - Identify relevant concepts in the source domain
     - Map structural relationships to the target problem
     - Generate insights and potential solutions
     - Validate the coherence of the analogical mappings
     - Synthesize findings across multiple analogies
  ** Useful for creative problem-solving, design thinking, and novel approaches
  ** Can reference related files for additional context
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val sourceDomain = executionConfig?.source_domain
        val targetProblem = executionConfig?.target_problem
        val numAnalogies = executionConfig?.num_analogies ?: 3
        val validateMappings = executionConfig?.validate_mappings ?: true

        if (sourceDomain.isNullOrBlank() || targetProblem.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: Both source_domain and target_problem must be specified")
            return
        }

        val newTask = task.ui.newTask(false)
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Analogical Reasoning Task
                |
                |**Source Domain:** $sourceDomain
                |
                |**Target Problem:** $targetProblem
                |
                |**Number of Analogies:** $numAnalogies
                |
                |**Validation:** ${if (validateMappings) "Enabled" else "Disabled"}
                """.trimMargin(),
                ui = ui
            )
        )

        val priorContext = getPriorCode(agent.executionState!!)
        val contextFiles = getContextFiles()

        // Step 1: Generate analogies
        val analogiesPrompt = """
You are an expert in analogical reasoning and creative problem-solving.

## Task
Generate $numAnalogies high-quality analogies from the source domain to help solve the target problem.

## Source Domain
$sourceDomain

## Target Problem
$targetProblem

## Additional Context
$priorContext

$contextFiles

## Instructions
For each analogy:
1. Identify a relevant concept, pattern, or system from the source domain
2. Explain the concept clearly with its key characteristics
3. Map the structural relationships to the target problem
4. Identify specific insights this analogy provides
5. Suggest concrete solutions or approaches based on the analogy
6. Assess your confidence in the analogy's validity (0-1)

Focus on:
- Deep structural similarities, not superficial resemblances
- Actionable insights and solutions
- Novel perspectives that challenge conventional thinking
- Clear mapping between source and target concepts

Generate the analogies now.
        """.trimIndent()

        val analogyParser = ParsedAgent(
            resultClass = AnalogicalReasoningResult::class.java,
            prompt = analogiesPrompt,
            model = api!!,
            temperature = 0.7,
            name = "AnalogicalReasoning",
            parsingModel = orchestrationConfig.parsingChatter,
        )

        var result: AnalogicalReasoningResult? = null
        Retryable(newTask, newTask.ui) { sb ->
            result = analogyParser.answer(listOf(analogiesPrompt)).obj
            sb.append("Analogies generated")
            sb.toString()
        }

        if (result == null) {
            resultFn("ERROR: Failed to generate analogies")
            return
        }

        // Step 2: Validate mappings if requested
        if (validateMappings) {
            val validationPrompt = """
 Review the following analogies and validate their structural coherence.

 ## Analogies
 ${
                result!!.analogies.joinToString("\n\n") { analogy ->
                    """
 ### ${analogy.title}
 **Source:** ${analogy.source_description}
 **Application:** ${analogy.application}
 **Mappings:**
 ${analogy.mappings.joinToString("\n") { "- ${it.source_concept} → ${it.target_concept}: ${it.mapping_rationale}" }}
 **Confidence:** ${analogy.confidence}
    """.trim()
                }
            }

 ## Validation Criteria
 1. Are the structural relationships truly parallel?
 2. Are the mappings consistent and coherent?
 3. Do the insights follow logically from the mappings?
 4. Are there any logical fallacies or weak connections?

 Provide a brief validation assessment.
            """.trimIndent()

            val validationAgent = ChatAgent(
                prompt = "You are an expert in logical reasoning and analogy validation.",
                model = api,
                temperature = 0.3
            )

            var validationResult = ""
            Retryable(newTask, newTask.ui) { sb ->
                validationResult = validationAgent.answer(listOf(validationPrompt))
                sb.append(validationResult)
                sb.toString()
            }

            result = result!!.copy(validation_notes = validationResult)
        }

        // Step 3: Format and display results
        val formattedResult = formatAnalogicalReasoningResult(result!!)

        newTask.add(MarkdownUtil.renderMarkdown(formattedResult, ui = ui))

        val resultText = buildString {
            appendLine("# Analogical Reasoning Results")
            appendLine()
            appendLine("## Source Domain: $sourceDomain")
            appendLine("## Target Problem: $targetProblem")
            appendLine()

            result!!.analogies.forEachIndexed { index, analogy ->
                appendLine("### Analogy ${index + 1}: ${analogy.title}")
                appendLine()
                appendLine("**Source Description:**")
                appendLine(analogy.source_description)
                appendLine()
                appendLine("**Application to Target:**")
                appendLine(analogy.application)
                appendLine()
                appendLine("**Key Mappings:**")
                analogy.mappings.forEach { mapping ->
                    appendLine("- ${mapping.source_concept} → ${mapping.target_concept}")
                    appendLine("  - Rationale: ${mapping.mapping_rationale}")
                }
                appendLine()
                appendLine("**Insights:**")
                analogy.insights.forEach { appendLine("- $it") }
                appendLine()
                appendLine("**Suggested Solutions:**")
                analogy.suggested_solutions.forEach { appendLine("- $it") }
                appendLine()
                appendLine("**Confidence:** ${analogy.confidence}")
                appendLine()
            }

            appendLine("## Synthesized Insights")
            result!!.synthesized_insights.forEach { appendLine("- $it") }
            appendLine()

            appendLine("## Recommended Approach")
            appendLine(result!!.recommended_approach)

            if (result!!.validation_notes != null) {
                appendLine()
                appendLine("## Validation Notes")
                appendLine(result!!.validation_notes)
            }
        }

        newTask.complete(MarkdownUtil.renderMarkdown("✓ Analogical reasoning completed successfully", ui = ui))
        resultFn(resultText)
    }

    private fun formatAnalogicalReasoningResult(result: AnalogicalReasoningResult): String {
        return buildString {
            appendLine("## Generated Analogies")
            appendLine()

            result.analogies.forEachIndexed { index, analogy ->
                appendLine("### ${index + 1}. ${analogy.title}")
                appendLine()
                appendLine("**Confidence:** ${String.format("%.1f%%", analogy.confidence * 100)}")
                appendLine()
                appendLine("#### Source Domain Concept")
                appendLine(analogy.source_description)
                appendLine()
                appendLine("#### Application to Target Problem")
                appendLine(analogy.application)
                appendLine()

                if (analogy.mappings.isNotEmpty()) {
                    appendLine("#### Conceptual Mappings")
                    appendLine()
                    appendLine("| Source Concept | Target Concept | Rationale |")
                    appendLine("|----------------|----------------|-----------|")
                    analogy.mappings.forEach { mapping ->
                        appendLine("| ${mapping.source_concept} | ${mapping.target_concept} | ${mapping.mapping_rationale} |")
                    }
                    appendLine()

                    appendLine("**Structural Similarities:**")
                    analogy.mappings.flatMap { it.structural_similarities }.distinct().forEach {
                        appendLine("- $it")
                    }
                    appendLine()

                    appendLine("**Limitations:**")
                    analogy.mappings.flatMap { it.limitations }.distinct().forEach {
                        appendLine("- $it")
                    }
                    appendLine()
                }

                appendLine("#### Insights")
                analogy.insights.forEach { appendLine("- $it") }
                appendLine()

                appendLine("#### Suggested Solutions")
                analogy.suggested_solutions.forEach { appendLine("- $it") }
                appendLine()
                appendLine("---")
                appendLine()
            }

            appendLine("## Cross-Analogy Synthesis")
            appendLine()
            result.synthesized_insights.forEach { appendLine("- $it") }
            appendLine()

            appendLine("## Recommended Approach")
            appendLine()
            appendLine(result.recommended_approach)

            if (result.validation_notes != null) {
                appendLine()
                appendLine("## Validation Assessment")
                appendLine()
                appendLine(result.validation_notes)
            }
        }
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""

        return buildString {
            appendLine("## Related Files Context")
            appendLine()
            relatedFiles.forEach { file ->
                try {
                    val filePath = root.resolve(file)
                    if (filePath.toFile().exists()) {
                        appendLine("### $file")
                        appendLine("```")
                        appendLine(filePath.toFile().readText())
                        appendLine("```")
                        appendLine()
                    }
                } catch (e: Exception) {
                    log.warn("Error reading file: $file", e)
                }
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(AnalogicalReasoningTask::class.java)
        val AnalogicalReasoning = TaskType(
            "AnalogicalReasoning",
            AnalogicalReasoningTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Solve problems by finding and applying analogies from different domains",
            """
              Performs creative problem-solving through analogical reasoning.
              <ul>
                <li>Draws analogies from specified source domains</li>
                <li>Maps structural relationships to target problems</li>
                <li>Generates multiple perspectives and insights</li>
                <li>Validates mapping coherence and consistency</li>
                <li>Synthesizes findings across analogies</li>
                <li>Suggests concrete solutions based on analogies</li>
                <li>Useful for design thinking and novel approaches</li>
              </ul>
            """
        )
    }
}