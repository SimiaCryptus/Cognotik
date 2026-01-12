package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class AbductiveReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: AbductiveReasoningTaskExecutionConfigData?
) : AbstractTask<AbductiveReasoningTask.AbductiveReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxOutputSize = 5000

    data class Hypothesis(
        val id: Int = 0,
        val description: String = "",
        val explanation: String = "",
        val explanatory_power: Double = 0.0,
        val simplicity: Double = 0.0,
        val testability: Double = 0.0,
        val prior_probability: Double = 0.0,
        val overall_score: Double = 0.0,
        val supporting_evidence: List<String> = emptyList(),
        val contradicting_evidence: List<String> = emptyList(),
        val testable_predictions: List<String> = emptyList()
    )

    protected val codeFiles = mutableMapOf<Path, String>()


    data class HypothesesResponse(
        val hypotheses: List<Hypothesis> = emptyList(),
        val reasoning: String = ""
    )

    class AbductiveReasoningTaskExecutionConfigData(
        @Description("List of observations that need explanation")
        val observations: List<String>? = null,
        @Description("Whether to generate new hypotheses (vs only evaluate provided ones)")
        val generate_hypotheses: Boolean = true,
        @Description("Maximum number of hypotheses to generate")
        val max_hypotheses: Int = 5,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Criteria for evaluating hypotheses: explanatory_power, simplicity, testability, prior_probability")
        val evaluate_criteria: List<String>? = listOf(
            "explanatory_power",
            "simplicity",
            "testability",
            "prior_probability"
        ),
        @Description("Whether to suggest tests to validate hypotheses")
        val suggest_tests: Boolean = true,
        @Description("Pre-existing hypotheses to evaluate (optional)")
        val existing_hypotheses: List<String>? = null,
        @Description("Domain context or constraints")
        val domain_context: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = AbductiveReasoning.name,
        task_description = task_description
            ?: "Generate and evaluate explanatory hypotheses for ${observations?.size ?: 0} observations",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (observations.isNullOrEmpty()) return "Observations must be specified"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
AbductiveReasoning - Generate and evaluate explanatory hypotheses
  ** Specify observations that need explanation
  ** Configure hypothesis generation (max_hypotheses: ${executionConfig?.max_hypotheses ?: 5})
  ** Select evaluation criteria: ${executionConfig?.evaluate_criteria?.joinToString(", ") ?: "all"}
  ** Optionally provide existing hypotheses to evaluate
  ** Optionally suggest tests to validate hypotheses
  ** Useful for:
     - Root cause analysis
     - Bug investigation
     - Understanding anomalies
     - Scientific reasoning
     - Inference to best explanation
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
        var stepStartTime = System.currentTimeMillis()
        log.info("Starting AbductiveReasoningTask with ${executionConfig?.observations?.size ?: 0} observations")
        val transcript = task.transcript()
        // Combine messages with file input
        val inputContext = (messages + listOf(getInputFileCode())).filter { it.isNotBlank() }


        
        val config = executionConfig ?: return resultFn("No configuration provided")
        val validationError = config.validate()
        if (validationError != null) {
            val errorMsg = "CONFIGURATION ERROR: $validationError"
            log.error(errorMsg)
            task.error(ValidatedObject.ValidationError(validationError, config))
            resultFn(errorMsg)
            transcript?.close()
            return
        }

        val api = defaultSmart ?: return
        val observations = config.observations!!

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        task.header("Abductive Reasoning Analysis", level = 2)

        val maxHypotheses = config.max_hypotheses.coerceIn(1, 10)
        val evaluateCriteria = config.evaluate_criteria ?: listOf(
            "explanatory_power",
            "simplicity",
            "testability",
            "prior_probability"
        )
        val suggestTests = executionConfig.suggest_tests
        val domainContext = executionConfig.domain_context ?: "general software system"

        
        log.info("Configuration: maxHypotheses=$maxHypotheses, criteria=$evaluateCriteria, suggestTests=$suggestTests")

        overviewTask.add(
            buildString {
                writeToTranscript(transcript, this)
                appendLine("# Abductive Reasoning Analysis")
                appendLine()
                appendLine("**Purpose:** Generate and evaluate explanatory hypotheses")
                appendLine()
                appendLine("**Observations to Explain:** ${observations.size}")
                appendLine()
                appendLine("**Max Hypotheses:** $maxHypotheses")
                appendLine()
                appendLine("**Evaluation Criteria:** ${evaluateCriteria.joinToString(", ")}")
                appendLine()
                appendLine("**Domain Context:** $domainContext")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("**Input Context:** ${inputContext.size} sections provided")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("*Analyzing observations...*")
            }.renderMarkdown
        )

        try {
            // Observations tab
            val observationsTask = tabs.newTask("Observations")
            task.header("Step 1: Documenting Observations", level = 3)
            observationsTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine("# Observations")
                    appendLine()
                    appendLine("The following observations need explanation:")
                    appendLine()
                    observations.forEachIndexed { index, obs ->
                        appendLine("${index + 1}. $obs")
                    }
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Observations documented")
                }.renderMarkdown
            )
            observationsTask.complete()

            // Gather context
            val priorContext = getPriorCode(agent?.executionState)
            val combinedContext = (priorContext + "\n\n" + inputContext.joinToString("\n\n")).trim()
            if (priorContext.isNotBlank()) {
                log.debug("Found prior context: ${priorContext.length} characters")
                val contextTask = tabs.newTask("Context")
                contextTask.add(
                    buildString {
                        writeToTranscript(transcript, this)
                        appendLine("# Context from Previous Tasks")
                        appendLine()
                        appendLine(priorContext.truncateForDisplay())
                    }.renderMarkdown
                )
                contextTask.complete()
            }

            // Update overview
            overviewTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("✅ Observations documented")
                    appendLine()
                    appendLine("*Generating hypotheses...*")
                }.renderMarkdown
            )

            // Generate or use existing hypotheses
            val hypothesesTask = tabs.newTask("Hypotheses")
            task.header("Step 2: Generating Hypotheses", level = 3)
            hypothesesTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine("# Hypothesis Generation")
                    appendLine()
                    appendLine("**Status:** 🔄 Generating hypotheses...")
                }.renderMarkdown
            )

            val hypotheses = if (executionConfig.generate_hypotheses) {
                log.debug("Generating hypotheses using LLM")
                generateHypotheses(
                    observations,
                    maxHypotheses,
                    evaluateCriteria,
                    domainContext,
                    combinedContext,
                    api
                )
            } else {
                log.debug("Using existing hypotheses")
                val existing = executionConfig.existing_hypotheses ?: emptyList()
                evaluateExistingHypotheses(
                    observations,
                    existing,
                    evaluateCriteria,
                    domainContext,
                    combinedContext,
                    api
                )
            }

            val hypothesesTime = (System.currentTimeMillis() - stepStartTime) / 1000.0
            stepStartTime = System.currentTimeMillis()
            log.info("Generated/evaluated ${hypotheses.size} hypotheses in ${hypothesesTime}s")

            // Display hypotheses
            hypothesesTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("## Generated Hypotheses")
                    appendLine()
                    appendLine("**Count:** ${hypotheses.size}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    hypotheses.sortedByDescending { it.overall_score }.forEachIndexed { index, hyp ->
                        appendLine("### Hypothesis ${index + 1}: ${hyp.description}")
                        appendLine()
                        appendLine("**Overall Score:** ${String.format("%.2f", hyp.overall_score)}")
                        appendLine()
                        appendLine("**Explanation:**")
                        appendLine()
                        appendLine(hyp.explanation)
                        appendLine()
                        appendLine("**Evaluation Metrics:**")
                        appendLine("- Explanatory Power: ${String.format("%.2f", hyp.explanatory_power)}")
                        appendLine("- Simplicity: ${String.format("%.2f", hyp.simplicity)}")
                        appendLine("- Testability: ${String.format("%.2f", hyp.testability)}")
                        appendLine("- Prior Probability: ${String.format("%.2f", hyp.prior_probability)}")
                        appendLine()
                        if (hyp.supporting_evidence.isNotEmpty()) {
                            appendLine("**Supporting Evidence:**")
                            hyp.supporting_evidence.forEach { appendLine("- $it") }
                            appendLine()
                        }
                        if (hyp.contradicting_evidence.isNotEmpty()) {
                            appendLine("**Contradicting Evidence:**")
                            hyp.contradicting_evidence.forEach { appendLine("- $it") }
                            appendLine()
                        }
                        if (hyp.testable_predictions.isNotEmpty()) {
                            appendLine("**Testable Predictions:**")
                            hyp.testable_predictions.forEach { appendLine("- $it") }
                            appendLine()
                        }
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("**Status:** ✅ Hypotheses generated and evaluated")
                }.renderMarkdown
            )
            hypothesesTask.complete()

            // Update overview
            overviewTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("✅ Hypotheses generated: ${hypotheses.size} (${hypothesesTime}s)")
                    appendLine()
                    appendLine("*Performing comparative analysis...*")
                }.renderMarkdown
            )

            // Comparative analysis
            val analysisTask = tabs.newTask("Analysis")
            task.header("Step 3: Comparative Analysis", level = 3)
            analysisTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine("# Comparative Analysis")
                    appendLine()
                    appendLine("**Status:** 🔄 Analyzing hypotheses...")
                }.renderMarkdown
            )

            val analysis = performComparativeAnalysis(
                observations,
                hypotheses,
                evaluateCriteria,
                api
            )

            val analysisTime = (System.currentTimeMillis() - stepStartTime) / 1000.0
            stepStartTime = System.currentTimeMillis()
            log.info("Comparative analysis completed in ${analysisTime}s (${analysis.length} chars)")

            analysisTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("## Comparative Analysis Results")
                    appendLine()
                    appendLine(analysis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Analysis complete")
                }.renderMarkdown
            )
            analysisTask.complete()

            // Update overview
            overviewTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("✅ Comparative analysis complete (${analysisTime}s)")
                    if (suggestTests) {
                        appendLine()
                        appendLine("*Generating validation tests...*")
                    }
                }.renderMarkdown
            )

            // Generate validation tests if requested
            var testSuggestions: String
            if (suggestTests) {
                val testsTask = tabs.newTask("Validation Tests")
                task.header("Step 4: Validation Tests", level = 3)
                testsTask.add(
                    buildString {
                        writeToTranscript(transcript, this)
                        appendLine("# Validation Tests")
                        appendLine()
                        appendLine("**Status:** 🔄 Generating test suggestions...")
                    }.renderMarkdown
                )

                testSuggestions = generateValidationTests(
                    observations,
                    hypotheses.take(3), // Top 3 hypotheses
                    domainContext,
                    api
                )

                val testsTime = (System.currentTimeMillis() - stepStartTime) / 1000.0
                stepStartTime = System.currentTimeMillis()
                log.info("Validation tests generated in ${testsTime}s (${testSuggestions.length} chars)")

                testsTask.add(
                    buildString {
                        writeToTranscript(transcript, this)
                        appendLine()
                        appendLine("## Suggested Validation Tests")
                        appendLine()
                        appendLine(testSuggestions)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Test suggestions complete")
                    }.renderMarkdown
                )
                testsTask.complete()

                overviewTask.add(
                    buildString {
                        writeToTranscript(transcript, this)
                        appendLine()
                        appendLine("✅ Validation tests generated (${testsTime}s)")
                    }.renderMarkdown
                )
            }

            // Best explanation summary
            val bestHypothesis = hypotheses.maxByOrNull { it.overall_score }
            val summaryTask = tabs.newTask("Best Explanation")
            task.header("Final Inference", level = 3)
            summaryTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine("# Best Explanation (Inference to Best Explanation)")
                    appendLine()
                    if (bestHypothesis != null) {
                        appendLine("## ${bestHypothesis.description}")
                        appendLine()
                        appendLine("**Overall Score:** ${String.format("%.2f", bestHypothesis.overall_score)}")
                        appendLine()
                        appendLine("### Why This is the Best Explanation")
                        appendLine()
                        appendLine(bestHypothesis.explanation)
                        appendLine()
                        appendLine("### Key Strengths")
                        appendLine()
                        appendLine(
                            "- **Explanatory Power:** ${
                                String.format(
                                    "%.2f",
                                    bestHypothesis.explanatory_power
                                )
                            } - ${getStrengthDescription(bestHypothesis.explanatory_power)}"
                        )
                        appendLine(
                            "- **Simplicity:** ${
                                String.format(
                                    "%.2f",
                                    bestHypothesis.simplicity
                                )
                            } - ${getSimplicityDescription(bestHypothesis.simplicity)}"
                        )
                        appendLine(
                            "- **Testability:** ${
                                String.format(
                                    "%.2f",
                                    bestHypothesis.testability
                                )
                            } - ${getTestabilityDescription(bestHypothesis.testability)}"
                        )
                        appendLine(
                            "- **Prior Probability:** ${
                                String.format(
                                    "%.2f",
                                    bestHypothesis.prior_probability
                                )
                            } - ${getProbabilityDescription(bestHypothesis.prior_probability)}"
                        )
                        appendLine()
                        if (bestHypothesis.testable_predictions.isNotEmpty()) {
                            appendLine("### Next Steps: Validate This Hypothesis")
                            appendLine()
                            bestHypothesis.testable_predictions.forEach { pred ->
                                appendLine("- [ ] $pred")
                            }
                            appendLine()
                        }
                    } else {
                        appendLine("No hypotheses were generated.")
                    }
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Best explanation identified")
                }.renderMarkdown
            )
            summaryTask.complete()
            // Final summary
            val totalTime = System.currentTimeMillis() - startTime

            val transcriptFile =
                "abductive_reasoning_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"

            val (summaryLink, summaryFile) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
            val finalSummary = buildString {
                appendLine("# Abductive Reasoning Summary")
                appendLine()
                appendLine("**Observations Analyzed:** ${observations.size}")
                appendLine("**Hypotheses Generated:** ${hypotheses.size}")
                appendLine("**Best Explanation:** ${bestHypothesis?.description ?: "None"}")
                appendLine(
                    "**Best Score:** ${
                        bestHypothesis?.let {
                            String.format(
                                "%.2f",
                                it.overall_score
                            )
                        } ?: "N/A"
                    }")
                appendLine()
                appendLine("## Key Findings")
                appendLine()
                appendLine(analysis.truncateForDisplay(maxOutputSize))
            }
            // Write detailed summary to file
            summaryFile?.outputStream()?.use { stream ->
                stream.write(finalSummary.toByteArray())
                stream.flush()
            }
            val summaryMessage = buildString {
                appendLine("Analysis complete. View detailed results:")
                appendLine(
                    "<a href='$summaryLink' target='_blank'>Summary</a> | <a href='${summaryLink.removeSuffix(".md")}.html' target='_blank'>HTML</a> | <a href='${
                        summaryLink.removeSuffix(
                            ".md"
                        )
                    }.pdf' target='_blank'>PDF</a>"
                )
            }


            log.info("AbductiveReasoningTask completed: total_time=${totalTime}ms, observations=${observations.size}, hypotheses=${hypotheses.size}, best_score=${bestHypothesis?.overall_score}")

            overviewTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Analysis Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Hypotheses Evaluated:** ${hypotheses.size}")
                    appendLine()
                    appendLine("**Best Explanation:** ${bestHypothesis?.description ?: "None"}")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )
            overviewTask.complete()
            transcript?.close()

            task.safeComplete(
                "Completed abductive reasoning analysis: ${hypotheses.size} hypotheses evaluated in ${totalTime / 1000}s",
                log
            )
            resultFn(summaryMessage.toString())

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("AbductiveReasoningTask failed after ${duration}ms", e)
            task.error(e)

            overviewTask.add(
                buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
                }.renderMarkdown
            )
            overviewTask.complete()

            val errorOutput = buildString {
                appendLine("# Error in Abductive Reasoning")
                appendLine()
                appendLine("**Observations:** ${observations?.size ?: 0}")
                appendLine()
                appendLine("**Error:** ${e.message}")
            }
            resultFn(errorOutput)
            transcript?.close()
        }
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
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
        .filterNotNull()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = if (!isTextFile(file)) {
                    extractDocumentContent(file)
                } else {
                    codeFiles[file.toPath()] ?: file.readText()
                }
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    private fun isTextFile(file: File): Boolean {
        val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: File) = try {
        file.getDocumentReader().use { reader ->
            reader.getText()
        }
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}", e)
        file.readText()
    }


    private fun generateHypotheses(
        observations: List<String>,
        maxHypotheses: Int,
        evaluateCriteria: List<String>,
        domainContext: String,
        priorContext: String,
        api: ChatInterface
    ): List<Hypothesis> {
        val prompt = buildString {
            appendLine("You are an expert in abductive reasoning and scientific inference.")
            appendLine()
            appendLine("## Task")
            appendLine("Generate explanatory hypotheses for the following observations.")
            appendLine()
            appendLine("## Observations")
            observations.forEachIndexed { index, obs ->
                appendLine("${index + 1}. $obs")
            }
            appendLine()
            appendLine("## Domain Context")
            appendLine(domainContext)
            appendLine()
            if (priorContext.isNotBlank()) {
                appendLine("## Additional Context")
                appendLine(priorContext.truncateForDisplay(2000))
                appendLine()
            }
            appendLine("## Instructions")
            appendLine("Generate $maxHypotheses distinct explanatory hypotheses that could explain ALL the observations.")
            appendLine()
            appendLine("For each hypothesis, provide:")
            appendLine("1. A clear, concise description (one sentence)")
            appendLine("2. A detailed explanation of HOW it explains the observations")
            appendLine("3. Evaluation scores (0.0 to 1.0) for:")
            evaluateCriteria.forEach { criterion ->
                appendLine("   - $criterion")
            }
            appendLine("4. Supporting evidence from the observations")
            appendLine("5. Any contradicting evidence")
            appendLine("6. Testable predictions that would validate/falsify the hypothesis")
            appendLine()
            appendLine("Apply these principles:")
            appendLine("- **Explanatory Power**: How well does it explain ALL observations?")
            appendLine("- **Simplicity (Occam's Razor)**: Prefer simpler explanations with fewer assumptions")
            appendLine("- **Testability**: Can we design experiments to test this?")
            appendLine("- **Prior Probability**: How likely is this given our background knowledge?")
            appendLine()
            appendLine("Generate diverse hypotheses ranging from simple to complex, common to rare.")
        }

        val parsedAgent = ParsedAgent(
            resultClass = HypothesesResponse::class.java,
            prompt = prompt.toString(),
            model = api,
            temperature = 0.7,
            parsingChatter = defaultFast
        )

        val response = parsedAgent.answer(listOf(prompt.toString())).obj
        return response.hypotheses
    }

    private fun evaluateExistingHypotheses(
        observations: List<String>,
        existingHypotheses: List<String>,
        evaluateCriteria: List<String>,
        domainContext: String,
        priorContext: String,
        api: ChatInterface
    ): List<Hypothesis> {
        val prompt = buildString {
            appendLine("You are an expert in abductive reasoning and hypothesis evaluation.")
            appendLine()
            appendLine("## Task")
            appendLine("Evaluate the following pre-existing hypotheses against the observations.")
            appendLine()
            appendLine("## Observations")
            observations.forEachIndexed { index, obs ->
                appendLine("${index + 1}. $obs")
            }
            appendLine()
            appendLine("## Hypotheses to Evaluate")
            existingHypotheses.forEachIndexed { index, hyp ->
                appendLine("${index + 1}. $hyp")
            }
            appendLine()
            appendLine("## Domain Context")
            appendLine(domainContext)
            appendLine()
            if (priorContext.isNotBlank()) {
                appendLine("## Additional Context")
                appendLine(priorContext.truncateForDisplay(2000))
                appendLine()
            }
            appendLine("## Instructions")
            appendLine("For each hypothesis, provide:")
            appendLine("1. The original hypothesis description")
            appendLine("2. A detailed explanation of HOW it explains the observations")
            appendLine("3. Evaluation scores (0.0 to 1.0) for:")
            evaluateCriteria.forEach { criterion ->
                appendLine("   - $criterion")
            }
            appendLine("4. Supporting evidence from the observations")
            appendLine("5. Any contradicting evidence")
            appendLine("6. Testable predictions")
        }

        val parsedAgent = ParsedAgent(
            resultClass = HypothesesResponse::class.java,
            prompt = prompt.toString(),
            model = api,
            temperature = 0.5,
            parsingChatter = defaultFast
        )

        val response = parsedAgent.answer(listOf(prompt.toString())).obj
        return response.hypotheses
    }

    private fun performComparativeAnalysis(
        observations: List<String>,
        hypotheses: List<Hypothesis>,
        evaluateCriteria: List<String>,
        api: ChatInterface
    ): String {
        val prompt = buildString {
            appendLine("Perform a comparative analysis of the following hypotheses.")
            appendLine()
            appendLine("## Observations")
            observations.forEachIndexed { index, obs ->
                appendLine("${index + 1}. $obs")
            }
            appendLine()
            appendLine("## Hypotheses (ranked by overall score)")
            hypotheses.sortedByDescending { it.overall_score }.forEachIndexed { index, hyp ->
                appendLine()
                appendLine("### Hypothesis ${index + 1}: ${hyp.description}")
                appendLine("**Score:** ${String.format("%.2f", hyp.overall_score)}")
                appendLine("- Explanatory Power: ${String.format("%.2f", hyp.explanatory_power)}")
                appendLine("- Simplicity: ${String.format("%.2f", hyp.simplicity)}")
                appendLine("- Testability: ${String.format("%.2f", hyp.testability)}")
                appendLine("- Prior Probability: ${String.format("%.2f", hyp.prior_probability)}")
            }
            appendLine()
            appendLine("## Analysis Instructions")
            appendLine("Provide a comparative analysis that:")
            appendLine("1. Identifies the best explanation and why (inference to best explanation)")
            appendLine("2. Compares trade-offs between hypotheses")
            appendLine("3. Discusses which observations are best explained by which hypotheses")
            appendLine("4. Identifies any observations that remain poorly explained")
            appendLine("5. Suggests which hypothesis should be tested first and why")
            appendLine("6. Discusses the role of Occam's Razor in this case")
            appendLine()
            appendLine("Be specific and reference the evaluation criteria: ${evaluateCriteria.joinToString(", ")}")
        }

        val chatAgent = ChatAgent(
            prompt = prompt.toString(),
            model = api,
            temperature = 0.6
        )

        return chatAgent.answer(listOf(prompt.toString()))
    }

    private fun generateValidationTests(
        observations: List<String>,
        topHypotheses: List<Hypothesis>,
        domainContext: String,
        api: ChatInterface
    ): String {
        val prompt = buildString {
            appendLine("Generate concrete validation tests for the top hypotheses.")
            appendLine()
            appendLine("## Observations")
            observations.forEachIndexed { index, obs ->
                appendLine("${index + 1}. $obs")
            }
            appendLine()
            appendLine("## Top Hypotheses to Test")
            topHypotheses.forEachIndexed { index, hyp ->
                appendLine()
                appendLine("### Hypothesis ${index + 1}: ${hyp.description}")
                appendLine(hyp.explanation)
                if (hyp.testable_predictions.isNotEmpty()) {
                    appendLine()
                    appendLine("**Predictions:**")
                    hyp.testable_predictions.forEach { pred ->
                        appendLine("- $pred")
                    }
                }
            }
            appendLine()
            appendLine("## Domain Context")
            appendLine(domainContext)
            appendLine()
            appendLine("## Instructions")
            appendLine("For each hypothesis, provide:")
            appendLine("1. **Confirmatory Tests**: Experiments/observations that would support the hypothesis")
            appendLine("2. **Falsification Tests**: Experiments/observations that would disprove the hypothesis")
            appendLine("3. **Discriminating Tests**: Tests that would distinguish between competing hypotheses")
            appendLine("4. **Implementation Details**: Specific steps to conduct each test")
            appendLine("5. **Expected Results**: What results would confirm/falsify each hypothesis")
            appendLine()
            appendLine("Make tests concrete, actionable, and specific to the domain context.")
            appendLine("Prioritize tests that are:")
            appendLine("- Easy to implement")
            appendLine("- Likely to provide clear results")
            appendLine("- Able to discriminate between multiple hypotheses")
        }

        val chatAgent = ChatAgent(
            prompt = prompt.toString(),
            model = api,
            temperature = 0.5
        )

        return chatAgent.answer(listOf(prompt.toString()))
    }

    private fun getStrengthDescription(score: Double): String = when {
        score >= 0.8 -> "Excellent - explains all observations comprehensively"
        score >= 0.6 -> "Good - explains most observations well"
        score >= 0.4 -> "Moderate - explains some observations"
        else -> "Weak - limited explanatory power"
    }

    private fun getSimplicityDescription(score: Double): String = when {
        score >= 0.8 -> "Very simple - few assumptions, elegant explanation"
        score >= 0.6 -> "Reasonably simple - moderate complexity"
        score >= 0.4 -> "Complex - multiple assumptions required"
        else -> "Very complex - many assumptions and moving parts"
    }

    private fun getTestabilityDescription(score: Double): String = when {
        score >= 0.8 -> "Highly testable - clear predictions, easy to validate"
        score >= 0.6 -> "Testable - can design experiments to validate"
        score >= 0.4 -> "Somewhat testable - difficult but possible to test"
        else -> "Hard to test - unclear how to validate"
    }

    private fun getProbabilityDescription(score: Double): String = when {
        score >= 0.8 -> "Very likely - consistent with known patterns"
        score >= 0.6 -> "Plausible - reasonable given background knowledge"
        score >= 0.4 -> "Possible - not impossible but less common"
        else -> "Unlikely - requires unusual circumstances"
    }

    private fun writeToTranscript(transcript: FileOutputStream?, content: StringBuilder) {
        transcript?.write(content.toString().toByteArray())
        transcript?.write("\n\n".toByteArray())
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(AbductiveReasoningTask::class.java)
        val AbductiveReasoning = TaskType(
          name = "AbductiveReasoning",
          category = "Reasoning",
          taskClass = AbductiveReasoningTask::class.java,
          executionConfigClass = AbductiveReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate and evaluate explanatory hypotheses",
          tooltipHtml = """
                        Performs abductive reasoning (inference to best explanation) to generate and evaluate hypotheses.
                        <ul>
                          <li>Generates multiple explanatory hypotheses for observations</li>
                          <li>Evaluates explanatory power, simplicity, testability, and prior probability</li>
                          <li>Applies Occam's Razor to prefer simpler explanations</li>
                          <li>Ranks hypotheses by overall quality</li>
                          <li>Suggests validation tests for top hypotheses</li>
                          <li>Useful for root cause analysis, bug investigation, and scientific reasoning</li>
                        </ul>
                      """,
        )
    }
}