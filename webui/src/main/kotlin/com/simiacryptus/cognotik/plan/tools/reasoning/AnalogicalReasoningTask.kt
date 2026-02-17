package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        @Description("Input files to provide context for analogical reasoning (supports glob patterns)")
        val input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = AnalogicalReasoning.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (source_domain.isNullOrBlank()) {
                return "source_domain must not be blank"
            }
            if (target_problem.isNullOrBlank()) {
                return "target_problem must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class AnalogyMapping(
        @Description("The source concept from the source domain")
        val source_concept: String = "",
        @Description("The target concept in the problem domain")
        val target_concept: String = "",
        @Description("Explanation of how the concepts map to each other")
        val mapping_rationale: String = "",
        @Description("Structural similarities between source and target")
        val structural_similarities: List<String> = emptyList(),
        @Description("Key differences or limitations of the analogy")
        val limitations: List<String> = emptyList()
    ) : ValidatedObject

    data class Analogy(
        @Description("Title of the analogy")
        val title: String = "",
        @Description("Description of the source domain concept")
        val source_description: String = "",
        @Description("How this applies to the target problem")
        val application: String = "",
        @Description("Detailed mappings between source and target concepts")
        val mappings: List<AnalogyMapping> = emptyList(),
        @Description("Insights gained from this analogy")
        val insights: List<String> = emptyList(),
        @Description("Potential solutions suggested by this analogy")
        val suggested_solutions: List<String> = emptyList(),
        @Description("Confidence score (0-1) in the validity of this analogy")
        val confidence: Double = 0.0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) {
                return "Analogy title must not be blank"
            }
            if (confidence < 0.0 || confidence > 1.0) {
                return "Confidence must be between 0.0 and 1.0, got $confidence"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class AnalogicalReasoningResult(
        @Description("List of generated analogies")
        val analogies: List<Analogy> = emptyList(),
        @Description("Synthesis of insights across all analogies")
        val synthesized_insights: List<String> = emptyList(),
        @Description("Recommended approach based on analogical reasoning")
        val recommended_approach: String = "",
        @Description("Validation results if validation was requested")
        val validation_notes: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (analogies.isEmpty()) {
                return "At least one analogy must be generated"
            }
            if (recommended_approach.isBlank()) {
                return "recommended_approach must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

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
        var transcriptStream: FileOutputStream? = null
        try {
            val startTime = System.currentTimeMillis()
            log.info("Starting AnalogicalReasoningTask with source_domain='${executionConfig?.source_domain}', target_problem='${executionConfig?.target_problem}', num_analogies=${executionConfig?.num_analogies ?: 3}")
            // Validate configuration
            executionConfig?.validate()?.let { validationError ->
                log.error("Configuration validation failed: $validationError")
                task.safeComplete("CONFIGURATION ERROR: $validationError", log)
                task.error(ValidatedObject.ValidationError(validationError, executionConfig))
                resultFn("CONFIGURATION ERROR: $validationError")
                return
            }


            val sourceDomain = executionConfig?.source_domain
            val targetProblem = executionConfig?.target_problem
            val numAnalogies = executionConfig?.num_analogies ?: 3
            val validateMappings = executionConfig?.validate_mappings ?: true

            if (sourceDomain.isNullOrBlank() || targetProblem.isNullOrBlank()) {
                log.error("Configuration error: source_domain or target_problem is blank")
                task.safeComplete("CONFIGURATION ERROR: Both source_domain and target_problem must be specified", log)
                task.error(RuntimeException("Configuration error: source_domain or target_problem is blank"))
                resultFn("CONFIGURATION ERROR: Both source_domain and target_problem must be specified")
                return
            }

            log.info("Configuration validated successfully")

            val tabs = TabbedDisplay(task)
            val api = defaultSmart ?: return
            // Initialize transcript
          transcriptStream = task.newFileOutputStream(transcriptFile())
            transcriptStream?.let { stream ->
                writeTranscriptHeader(stream, sourceDomain, targetProblem, numAnalogies, validateMappings)
            }


            // Create overview tab
            val overviewTask = task.newTask()
            tabs["Overview"] = overviewTask.placeholder
            val overviewContent = buildString {
                appendLine("# Analogical Reasoning Task")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Configuration")
                appendLine()
                appendLine("| Parameter | Value |")
                appendLine("|-----------|-------|")
                appendLine("| Source Domain | $sourceDomain |")
                appendLine("| Target Problem | $targetProblem |")
                appendLine("| Number of Analogies | $numAnalogies |")
                appendLine("| Validation | ${if (validateMappings) "✓ Enabled" else "✗ Disabled"} |")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Gathering context...")
            }
            overviewTask.add(overviewContent.renderMarkdown(true))

            log.debug("Gathering prior context and related files")
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getContextFiles()
            val inputFileContent =
                super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)
            transcriptStream?.let { stream ->
                writeToTranscript(stream, "## Input Files Context\n\n$inputFileContent\n\n")
            }
            log.debug("Context gathered: priorContext length=${priorContext.length}, contextFiles length=${contextFiles.length}")
            // Update overview with context info
            overviewTask.add(buildString {
                            appendLine()
                            appendLine("- ✓ Context gathered")
                            appendLine("- ⏳ Generating analogies...")
                        }.renderMarkdown(true))
            if (inputFileContent.isNotBlank()) {
                overviewTask.expandable("Input Files Context", "<pre>${inputFileContent}</pre>")
            }

            // Step 1: Generate analogies
            log.info("Starting analogy generation phase")
            val analogiesTask = task.newTask()
            tabs["Analogy Generation"] = analogiesTask.placeholder
            analogiesTask.add(buildString {
                            appendLine("# Generating Analogies")
                            appendLine()
                            appendLine("**Status:** In Progress")
                            appendLine()
                            appendLine("Generating $numAnalogies analogies from the source domain...")
                        }.renderMarkdown(true))

            val analogiesPrompt = """
 You are an expert in analogical reasoning and creative problem-solving.

 ## Input Files
 $inputFileContent
 
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
                model = api,
                temperature = 0.7,
                name = "AnalogicalReasoning",
                parsingChatter = defaultFast,
            )

            var result: AnalogicalReasoningResult? = analogyParser.answer(listOf(analogiesPrompt)).obj
            // Validate the result
            result?.validate()?.let { validationError ->
                log.error("Generated result validation failed: $validationError")
                analogiesTask.error(ValidatedObject.ValidationError(validationError, result))
                result = null
            }


            if (result == null) {
                log.error("Failed to generate analogies after retries")
                analogiesTask.error(RuntimeException("Failed to generate analogies"))
                overviewTask.add(buildString {
                                    appendLine()
                                    appendLine("- ✗ Analogy generation failed")
                                }.renderMarkdown(true))
                transcriptStream?.let { stream ->
                    writeToTranscript(stream, "## Error\n\nFailed to generate analogies\n\n")
                }
                task.safeComplete("Failed to generate analogies", log)
                resultFn("ERROR: Failed to generate analogies")
                return
            }
            // Display generated analogies
            analogiesTask.add(buildString {
                            appendLine()
                            appendLine("---")
                            appendLine()
                            appendLine("## Generated Analogies")
                            appendLine()
                            appendLine("**Status:** ✓ Complete")
                            appendLine()
                            appendLine("**Total Analogies:** ${result.analogies.size}")
                            appendLine()
                            result.analogies.forEachIndexed { index, analogy ->
                                appendLine("### ${index + 1}. ${analogy.title}")
                                appendLine()
                                appendLine("**Confidence:** ${String.format("%.1f%%", analogy.confidence * 100)}")
                                appendLine()
                                appendLine("#### Source Concept")
                                appendLine(analogy.source_description)
                                appendLine()
                                appendLine("#### Application")
                                appendLine(analogy.application)
                                appendLine()
                                appendLine("#### Key Mappings (${analogy.mappings.size})")
                                analogy.mappings.take(3).forEach { mapping ->
                                    appendLine("- **${mapping.source_concept}** → **${mapping.target_concept}**")
                                    appendLine("  - ${mapping.mapping_rationale}")
                                }
                                if (analogy.mappings.size > 3) {
                                    appendLine("- *...and ${analogy.mappings.size - 3} more mappings*")
                                }
                                appendLine()
                                appendLine("#### Insights (${analogy.insights.size})")
                                analogy.insights.take(3).forEach { appendLine("- $it") }
                                if (analogy.insights.size > 3) {
                                    appendLine("- *...and ${analogy.insights.size - 3} more insights*")
                                }
                                appendLine()
                                appendLine("---")
                                appendLine()
                            }
                        }.renderMarkdown(true))
            analogiesTask.complete()
            transcriptStream?.let { stream ->
                writeToTranscript(stream, "## Generated Analogies\n\n${result.analogies.size} analogies generated\n\n")
            }

            // Update overview
            overviewTask.add(buildString {
                            appendLine()
                            appendLine("- ✓ Generated ${result.analogies.size} analogies")
                            if (validateMappings) {
                                appendLine("- ⏳ Validating mappings...")
                            }
                        }.renderMarkdown(true))

            // Step 2: Validate mappings if requested
            if (validateMappings) {
                log.info("Starting mapping validation phase")
                val validationTask = task.newTask()
                tabs["Validation"] = validationTask.placeholder
                validationTask.add(buildString {
                                    appendLine("# Mapping Validation")
                                    appendLine()
                                    appendLine("**Status:** In Progress")
                                    appendLine()
                                    appendLine("Validating structural coherence of ${result.analogies.size} analogies...")
                                    appendLine()
                                    appendLine("## Validation Criteria")
                                    appendLine()
                                    appendLine("1. ✓ Structural relationship parallelism")
                                    appendLine("2. ✓ Mapping consistency and coherence")
                                    appendLine("3. ✓ Logical derivation of insights")
                                    appendLine("4. ✓ Absence of logical fallacies")
                                }.renderMarkdown(true))

                val validationPrompt = """
Review the following analogies and validate their structural coherence.

## Analogies
 ${
                    result.analogies.joinToString("\n\n") { analogy ->
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

                var validationResult = validationAgent.answer(listOf(validationPrompt))

                result = result!!.copy(validation_notes = validationResult)
                // Display validation results
                validationTask.add(buildString {
                                    appendLine()
                                    appendLine("---")
                                    appendLine()
                                    appendLine("## Validation Results")
                                    appendLine()
                                    appendLine("**Status:** ✓ Complete")
                                    appendLine()
                                    appendLine(validationResult.truncateForDisplay())
                                }.renderMarkdown(true))
                validationTask.complete()
                transcriptStream?.let { stream ->
                    writeToTranscript(stream, "## Validation Results\n\n$validationResult\n\n")
                }

                // Update overview
                overviewTask.add(buildString {
                                    appendLine()
                                    appendLine("- ✓ Validation completed")
                                    appendLine("- ⏳ Synthesizing results...")
                                }.renderMarkdown(true))
            }

            // Step 3: Format and display results
            log.info("Formatting and displaying final results")

            log.info("Formatting and displaying final results")
            val synthesisTask = task.newTask()
            tabs["Synthesis & Recommendations"] = synthesisTask.placeholder

            val formattedResult = formatAnalogicalReasoningResult(result)
            synthesisTask.add(formattedResult.renderMarkdown(true))
            synthesisTask.complete()

            val resultText = buildString {
                appendLine("# Analogical Reasoning Results")
                appendLine()
                appendLine("**Source Domain:** $sourceDomain")
                appendLine("**Target Problem:** $targetProblem")
                appendLine()
                appendLine("## Analogies")

                result.analogies.forEachIndexed { index, analogy ->
                    appendLine()
                    appendLine("### ${index + 1}. ${analogy.title}")
                    appendLine(analogy.source_description)
                    appendLine()
                    appendLine(analogy.application)
                    appendLine()

                    appendLine()
                    analogy.insights.forEach { appendLine("- $it") }
                    appendLine()
                    analogy.suggested_solutions.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine()
                }


                appendLine("## Key Insights")
                result.synthesized_insights.forEach { appendLine("- $it") }
                appendLine()

                appendLine("## Recommended Approach")
                appendLine(result.recommended_approach)

                if (result.validation_notes != null) {
                    appendLine()
                    appendLine(result.validation_notes)
                    appendLine()
                }
                appendLine("## Summary")
                appendLine(
                    "Generated ${result.analogies.size} analogies with average confidence of ${
                        String.format(
                            "%.1f%%",
                            result.analogies.map { it.confidence }.average() * 100
                        )
                    }"
                )
                appendLine()
                appendLine("*See the Synthesis & Recommendations tab for detailed analysis*")
            }

            // Final overview update
            val totalTime = System.currentTimeMillis() - startTime
            overviewTask.add(buildString {
                            appendLine()
                            appendLine("- ✓ Synthesis completed")
                            appendLine()
                            appendLine("## Summary")
                            appendLine()
                            appendLine("| Metric | Value |")
                            appendLine("|--------|-------|")
                            appendLine("| Total Analogies | ${result.analogies.size} |")
                            appendLine(
                                "| Average Confidence | ${
                                    String.format(
                                        "%.1f%%",
                                        result.analogies.map { it.confidence }.average() * 100
                                    )
                                } |"
                            )
                            appendLine("| Synthesized Insights | ${result.synthesized_insights.size} |")
                            appendLine("| Validation | ${if (validateMappings) "✓ Performed" else "✗ Skipped"} |")
                            appendLine("| Total Time | ${totalTime / 1000}s |")
                            appendLine()
                            appendLine("**Status:** ✓ Complete")
                        }.renderMarkdown(true))
            overviewTask.complete()
            transcriptStream?.let { stream ->
                writeTranscriptFooter(stream, totalTime, result.analogies.size)
            }


            log.info(
                "AnalogicalReasoningTask completed successfully: total_time=${totalTime}ms, analogies=${result.analogies.size}, avg_confidence=${
                    result.analogies.map { it.confidence }.average()
                }"
            )
            task.safeComplete(
                "Completed in ${totalTime / 1000} seconds with ${result.analogies.size} analogies generated.",
                log
            )
            resultFn(resultText)

        } catch (e: Exception) {
            log.error("Error during AnalogicalReasoningTask execution", e)
            transcriptStream?.let { stream ->
                writeToTranscript(stream, "## Error\n\n${e.message}\n\n")
            }
            task.error(e)
            task.add(buildString {
                            appendLine("# ❌ Error")
                            appendLine()
                            appendLine("An error occurred during analogical reasoning:")
                            appendLine("```")
                            appendLine(e.message ?: "Unknown error")
                            appendLine("```")
                        }.renderMarkdown(true))
            task.safeComplete("Failed with error: ${e.message}", log)
            resultFn("ERROR: ${e.message}")
        } finally {
            transcriptStream?.close()
        }
    }

    private fun formatAnalogicalReasoningResult(result: AnalogicalReasoningResult): String {
        return buildString {
            appendLine("# Synthesis & Recommendations")
            appendLine()
            appendLine("## Cross-Analogy Synthesis")
            appendLine()
            appendLine("### Key Insights")
            appendLine()
            result.synthesized_insights.forEach { appendLine("- $it") }
            appendLine()
            appendLine("### Recommended Approach")
            appendLine()
            appendLine(result.recommended_approach.truncateForDisplay())
            appendLine()
            if (result.validation_notes != null) {
                appendLine("### Validation Assessment")
                appendLine()
                appendLine(result.validation_notes.truncateForDisplay())
                appendLine()
            }
            appendLine("---")
            appendLine()
            appendLine("## Detailed Analogy Breakdown")
            appendLine()

            result.analogies.forEachIndexed { index, analogy ->
                appendLine("### ${index + 1}. ${analogy.title}")
                appendLine()
                appendLine("**Confidence:** ${String.format("%.1f%%", analogy.confidence * 100)}")
                appendLine()
                appendLine("#### Source Domain Concept")
                appendLine(analogy.source_description.truncateForDisplay())
                appendLine()
                appendLine("#### Application to Target Problem")
                appendLine(analogy.application.truncateForDisplay())
                appendLine()

                if (analogy.mappings.isNotEmpty()) {
                    appendLine("#### Conceptual Mappings")
                    appendLine()
                    appendLine("| Source Concept | Target Concept | Rationale |")
                    appendLine("|----------------|----------------|-----------|")
                    analogy.mappings.forEach { mapping ->
                        appendLine("| ${mapping.source_concept} | ${mapping.target_concept} | ${mapping.mapping_rationale.truncateForDisplay()} |")
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

            appendLine()
            appendLine()

            appendLine("## Recommended Approach")
            appendLine()

        }
    }

    private fun writeTranscriptHeader(
        stream: FileOutputStream,
        sourceDomain: String,
        targetProblem: String,
        numAnalogies: Int,
        validateMappings: Boolean
    ) {
        try {
            val header = buildString {
                appendLine("# Analogical Reasoning Transcript")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Configuration")
                appendLine()
                appendLine("- **Source Domain:** $sourceDomain")
                appendLine("- **Target Problem:** $targetProblem")
                appendLine("- **Number of Analogies:** $numAnalogies")
                appendLine("- **Validation Enabled:** $validateMappings")
                appendLine()
                appendLine("---")
                appendLine()
            }
            stream.write(header.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        } catch (e: Exception) {
            log.error("Failed to write transcript header", e)
        }
    }

    private fun writeTranscriptFooter(stream: FileOutputStream, totalTime: Long, analogyCount: Int) {
        try {
            val footer = buildString {
                appendLine("---")
                appendLine()
                appendLine(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine("**Total Time:** ${totalTime / 1000} seconds")
                appendLine("**Analogies Generated:** $analogyCount")
            }
            stream.write(footer.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        } catch (e: Exception) {
            log.error("Failed to write transcript footer", e)
        }
    }


    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""
        log.debug("Loading ${relatedFiles.size} related context files")

        return buildString {
            appendLine("## Related Files Context")
            appendLine()
            relatedFiles.forEach { file ->
                try {
                    val filePath = root.resolve(file)
                    if (filePath.toFile().exists()) {
                        log.debug("Successfully loaded context file: $file")
                        appendLine("### $file")
                        appendLine("```")
                        appendLine(filePath.toFile().readText().truncateForDisplay())
                        appendLine("```")
                        appendLine()
                    } else {
                        log.warn("Context file not found: $file")
                    }
                } catch (e: Exception) {
                    log.warn("Error reading file: $file", e)
                }
            }
        }
    }


    private fun String.truncateForDisplay(maxLength: Int = 1000): String {
        return if (this.length > maxLength) this.substring(0, maxLength) + "\n...(truncated)" else this
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(AnalogicalReasoningTask::class.java)
        @JvmStatic val AnalogicalReasoning = TaskType(
          name = "AnalogicalReasoning",
          category = "Reasoning",
          taskClass = AnalogicalReasoningTask::class.java,
          executionConfigClass = AnalogicalReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Solve problems by finding and applying analogies from different domains",
          tooltipHtml = """
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
                      """,
        )
    }
}