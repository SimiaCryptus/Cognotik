# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/AbductiveReasoningTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
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
          }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
                  }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
                  }.renderMarkdown(true)
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
                  }.renderMarkdown(true)
                )
                testsTask.complete()

                overviewTask.add(
                  buildString {
                    writeToTranscript(transcript, this)
                    appendLine()
                    appendLine("✅ Validation tests generated (${testsTime}s)")
                  }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
              }.renderMarkdown(true)
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
        @JvmStatic val AbductiveReasoning = TaskType(
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/AbstractionLadderTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AbstractionLadderTask(
    orchestrationConfig: OrchestrationConfig, planTask: AbstractionLadderTaskExecutionConfigData?
) : AbstractTask<AbstractionLadderTask.AbstractionLadderTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class AbstractionLadderTaskExecutionConfigData(
        @Description("The concrete concept, problem, or code pattern to analyze") val concrete_concept: String? = null,
        @Description("Direction to traverse: 'up' for abstraction (generalizations), 'down' for concretization (specific implementations), 'both' for bidirectional analysis") val direction: String = "both",
        @Description("Number of abstraction levels to traverse in each direction (1-5 recommended)") val levels: Int = 3,
        @Description("Whether to identify design patterns, anti-patterns, and refactoring opportunities at each level") val identify_patterns: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task") val input_files: List<String>? = null,
        @Description("Additional files for context (e.g., existing code, related implementations)") val related_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = AbstractionLadder.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (concrete_concept.isNullOrBlank()) {
                return "concrete_concept must not be null or blank"
            }
            if (direction.lowercase() !in listOf("up", "down", "both")) {
                return "direction must be 'up', 'down', or 'both', got: '$direction'"
            }
            if (levels !in 1..5) {
                return "levels must be between 1 and 5, got: $levels"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
AbstractionLadder - Traverse abstraction levels to find patterns and design insights
  ** Specify the concrete concept or problem to analyze
  ** Choose direction: 'up' (generalize), 'down' (concretize), or 'both'
  ** Set number of levels to traverse (1-5 recommended)
  ** Enable pattern identification to discover:
     - Design patterns and anti-patterns
     - Refactoring opportunities
     - Architectural insights
     - Code smells and improvements
  ** Related files provide context for analysis
  ** Output includes:
     - Abstraction hierarchy visualization
     - Pattern analysis at each level
     - Concrete examples and generalizations
     - Refactoring recommendations
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        var detailedOutputFile: FileOutputStream? = null
        val startTime = System.currentTimeMillis()
        log.info(
            "Starting Abstraction Ladder Analysis - Concept: ${
                executionConfig?.concrete_concept?.truncateForDisplay(
                    100
                )
            }, Direction: ${executionConfig?.direction}, Levels: ${executionConfig?.levels}"
        )
        // Validate configuration
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }


        val concept = executionConfig?.concrete_concept
        if (concept.isNullOrBlank()) {
            log.error("Configuration error: No concrete concept specified")
            task.safeComplete("CONFIGURATION ERROR: No concrete concept specified", log)
            resultFn("CONFIGURATION ERROR: No concrete concept specified")
            return
        }

        val direction = executionConfig.direction.lowercase()
        if (direction !in listOf("up", "down", "both")) {
            log.error("Configuration error: Invalid direction '$direction'")
            task.safeComplete("CONFIGURATION ERROR: Invalid direction", log)
            resultFn("CONFIGURATION ERROR: Direction must be 'up', 'down', or 'both'")
            return
        }

        val levels = executionConfig.levels.coerceIn(1, 5)
        val identifyPatterns = executionConfig.identify_patterns

        val api = defaultSmart ?: return

        // Initialize detailed output file
        detailedOutputFile = initializeDetailedOutput(task)
        detailedOutputFile?.write(
            """
      # Abstraction Ladder Analysis Transcript
      **Concept:** $concept  
      **Direction:** $direction  
      **Levels:** $levels  
      **Pattern Analysis:** ${if (identifyPatterns) "Enabled" else "Disabled"}
      ---
      
      **Concept:** $concept  
      **Direction:** $direction  
      **Levels:** $levels  
      **Pattern Analysis:** ${if (identifyPatterns) "Enabled" else "Disabled"}
    """.trimIndent().toByteArray()
        )

        val tabbedDisplay = TabbedDisplay(task)

        // Overview tab with input context
        val overviewTask = task.newTask().apply {
            tabbedDisplay["Overview"] = placeholder
            header("Abstraction Ladder Analysis: $concept", 2)
            add(
                MarkdownUtil.renderMarkdown(
                    """
          
          
          **Direction:** $direction | **Levels:** $levels | **Pattern Analysis:** ${if (identifyPatterns) "Enabled" else "Disabled"}
          
          Starting analysis...
          """.trimIndent(), ui = task.ui
                )
            )
        }
        val inputFileContent =
            super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)


        val contextFiles = getContextFiles()
        val priorCode = getPriorCode(agent.executionState)

        val result = StringBuilder()

        try {
            if (direction == "up" || direction == "both") {
                log.info("Performing upward abstraction analysis")
                val upwardTab = task.newTask()
                tabbedDisplay["Upward Analysis"] = upwardTab.placeholder
                val upwardAnalysis = analyzeUpward(
                    concept = concept,
                    levels = levels,
                    identifyPatterns = identifyPatterns,
                    contextFiles = contextFiles,
                    inputFileContent = inputFileContent,
                    priorCode = priorCode,
                    api = api,
                    task = upwardTab
                )
                result.append("## Upward Abstraction (Generalizations)\n\n")
                result.append(upwardAnalysis)
                detailedOutputFile?.write("\n## Upward Abstraction (Generalizations)\n\n".toByteArray())
                detailedOutputFile?.write(upwardAnalysis.toByteArray())
                result.append("\n\n")
                upwardTab.add("✅ Upward analysis complete", additionalClasses = "text-success")
                upwardTab.complete()
            }

            if (direction == "down" || direction == "both") {
                log.info("Performing downward concretization analysis")
                val downwardTab = task.newTask()
                tabbedDisplay["Downward Analysis"] = downwardTab.placeholder
                val downwardAnalysis = analyzeDownward(
                    concept = concept,
                    levels = levels,
                    identifyPatterns = identifyPatterns,
                    contextFiles = contextFiles,
                    inputFileContent = inputFileContent,
                    priorCode = priorCode,
                    api = api,
                    task = downwardTab
                )
                result.append("## Downward Concretization (Specific Implementations)\n\n")
                result.append(downwardAnalysis)
                detailedOutputFile?.write("\n\n## Downward Concretization (Specific Implementations)\n\n".toByteArray())
                detailedOutputFile?.write(downwardAnalysis.toByteArray())
                result.append("\n\n")
                downwardTab.add("✅ Downward analysis complete", additionalClasses = "text-success")
                downwardTab.complete()
            }

            if (identifyPatterns) {
                log.info("Generating pattern summary and recommendations")
                val patternTab = task.newTask()
                tabbedDisplay["Pattern Analysis"] = patternTab.placeholder
                val patternSummary = generatePatternSummary(
                    concept = concept,
                    upwardAnalysis = if (direction == "up" || direction == "both") result.toString() else "",
                    downwardAnalysis = if (direction == "down" || direction == "both") result.toString() else "",
                    api = api,
                    task = patternTab
                )
                result.append("## Pattern Analysis & Recommendations\n\n")
                result.append(patternSummary)
                detailedOutputFile?.write("\n\n## Pattern Analysis & Recommendations\n\n".toByteArray())
                detailedOutputFile?.write(patternSummary.toByteArray())
                patternTab.add("✅ Pattern analysis complete", additionalClasses = "text-success")
                patternTab.complete()
            }

            // Update overview with completion status
            overviewTask.append("<hr/>")
            overviewTask.header("✅ Analysis Complete", 2)
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
          
          
          
          **Total Levels Analyzed:** $levels  
          **Directions Covered:** $direction  
          **Pattern Analysis:** ${if (identifyPatterns) "Included" else "Skipped"}
          
          See individual tabs for detailed results.
          """.trimIndent(), ui = task.ui
                )
            )
            overviewTask.complete()

            val duration = System.currentTimeMillis() - startTime
            log.info("Abstraction Ladder Analysis completed successfully - Concept: ${concept.truncateForDisplay(100)}, Levels: $levels")
            detailedOutputFile?.close()
            task.safeComplete(
                "Abstraction ladder analysis complete for '${concept.truncateForDisplay(100)}' with $levels levels in $direction direction(s) (${duration}ms)",
                log
            )
            val summaryMessage = generateSummaryMessage(
                task,
                duration,
                concept,
                levels,
                direction,
                "abstraction_ladder_analysis_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            )
            resultFn(summaryMessage)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("Error in abstraction ladder analysis after ${duration}ms", e)
            detailedOutputFile?.close()
            task.error(e)
            task.add(
                MarkdownUtil.renderMarkdown(
                    """
          ## ❌ Error During Analysis
          An error occurred while performing the abstraction ladder analysis:
          ```
          ${e.message}
          ```
          Please check the logs for more details.
          """.trimIndent(), ui = task.ui
                )
            )
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun analyzeUpward(
        concept: String,
        levels: Int,
        identifyPatterns: Boolean,
        contextFiles: String,
        inputFileContent: String,
        priorCode: String,
        api: ChatInterface,
        task: SessionTask
    ): String {
        val prompt = """
 Analyze the following concept by moving UP the abstraction ladder.
 Start with the concrete concept and identify increasingly general abstractions.

 ## Input Files:
 $inputFileContent
 
 ## Concrete Concept:
 $concept

 ## Context from Related Files:
 $contextFiles

 ## Previous Task Results:
 $priorCode

 ## Instructions:
 For each of $levels abstraction levels above the concrete concept:
 1. Identify the more general/abstract concept
 2. Explain what aspects are being generalized
 3. Provide examples of other concrete instances at the lower level
 ${if (identifyPatterns) "4. Identify relevant design patterns or architectural patterns\n5. Note refactoring opportunities" else ""}

 Format your response as:

 ### Level 0 (Concrete): [Original Concept]
- Description: [Brief description]
- Characteristics: [Key characteristics]

### Level 1: [First Abstraction]
- Generalization: [What is being abstracted]
- Examples: [Other concrete instances]
 ${if (identifyPatterns) "- Patterns: [Relevant patterns]\n- Refactoring: [Opportunities]" else ""}

 [Continue for all levels...]

 Generate the upward abstraction analysis now:
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = """
        You are an expert software architect analyzing code abstractions and design patterns.
        Your role is to identify generalizations and patterns as you move up the abstraction ladder.
      """.trimIndent(),
            model = api,
        )
        task.add("Analyzing upward abstractions...", additionalClasses = "text-info")

        return chatAgent.answer(listOf(prompt)).apply {
            task.add(renderMarkdown(true))
        }
    }

    private fun analyzeDownward(
        concept: String,
        levels: Int,
        identifyPatterns: Boolean,
        contextFiles: String,
        inputFileContent: String,
        priorCode: String,
        api: ChatInterface,
        task: SessionTask
    ): String {
        val prompt = """
 Analyze the following concept by moving DOWN the abstraction ladder.
 Start with the concept and identify increasingly specific/concrete implementations.

 ## Input Files:
 $inputFileContent
 
 ## Starting Concept:
 $concept

 ## Context from Related Files:
 $contextFiles

 ## Previous Task Results:
 $priorCode

 ## Instructions:
 For each of $levels concretization levels below the starting concept:
 1. Identify more specific/concrete implementations
 2. Explain what aspects are being specialized
 3. Provide concrete code examples or use cases
 ${if (identifyPatterns) "4. Identify implementation patterns or idioms\n5. Note code smells or anti-patterns to avoid" else ""}

 Format your response as:

 ### Level 0 (Starting): [Original Concept]
- Description: [Brief description]
- Characteristics: [Key characteristics]

### Level -1: [First Concretization]
- Specialization: [What is being made more specific]
- Examples: [Concrete implementations]
- Code: [Code snippets if applicable]
 ${if (identifyPatterns) "- Patterns: [Implementation patterns]\n- Anti-patterns: [Things to avoid]" else ""}

 [Continue for all levels...]

 Generate the downward concretization analysis now:
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = """
        You are an expert software architect analyzing code implementations and concrete patterns.
        Your role is to identify specific implementations as you move down the abstraction ladder.
      """.trimIndent(),
            model = api,
        )
        task.add("Analyzing downward concretizations...", additionalClasses = "text-info")

        return chatAgent.answer(listOf(prompt)).apply {
            task.add(renderMarkdown(true))
        }
    }

    private fun generatePatternSummary(
        concept: String, upwardAnalysis: String, downwardAnalysis: String, api: ChatInterface, task: SessionTask
    ): String {
        val prompt = """
 Based on the abstraction ladder analysis, provide a comprehensive pattern summary and recommendations.

 ## Original Concept:
 $concept

 ## Upward Analysis:
 $upwardAnalysis

 ## Downward Analysis:
 $downwardAnalysis

 ## Instructions:
 Synthesize the analysis and provide:

 1. **Design Patterns Identified**: List all relevant design patterns found at various abstraction levels
 2. **Architectural Insights**: High-level architectural patterns or principles
 3. **Refactoring Opportunities**: Specific recommendations for improving the code
 4. **Anti-patterns & Code Smells**: Issues to address
 5. **Best Practices**: Recommendations based on the abstraction analysis
 6. **Implementation Guidance**: Concrete steps for applying insights

 Format as a structured markdown report with clear sections and actionable recommendations.

 Generate the pattern summary now:
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = """
        You are an expert software architect specializing in design patterns and code quality.
        Your role is to synthesize abstraction analysis into actionable recommendations.
      """.trimIndent(),
            model = api,
        )
        task.add("Generating pattern summary and recommendations...", additionalClasses = "text-info")
        return chatAgent.answer(listOf(prompt)).apply {
            task.add(renderMarkdown(true))
        }
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: emptyList()
        if (relatedFiles.isEmpty()) return "No related files provided."

        return relatedFiles.joinToString("\n\n") { pattern ->
            try {
                val file = root.resolve(pattern).toFile()
                if (file.exists() && file.isFile) {
                    "# $pattern\n\n```\n${file.readText()}\n```"
                } else {
                    "# $pattern\n\nFile not found or is a directory"
                }
            } catch (e: Exception) {
                log.warn("Error reading file: $pattern", e)
                "# $pattern\n\nError reading file: ${e.message}"
            }
        }
    }


    private fun initializeDetailedOutput(task: SessionTask): FileOutputStream? {
        return try {
            val (link, file) = Pair(
                task.linkTo("abstraction_ladder_analysis.md"),
                task.resolveUserFile("abstraction_ladder_analysis.md")
            )
            val outputStream = file?.outputStream()
            task.complete(
                "Writing detailed analysis to <a href='$link' target='_blank'>$link</a> " +
                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                        "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
            )
            log.info("Initialized detailed output file: $link")
            outputStream
        } catch (e: Exception) {
            log.error("Failed to initialize detailed output file", e)
            null
        }
    }

    private fun generateSummaryMessage(
        task: SessionTask,
        duration: Long,
        concept: String,
        levels: Int,
        direction: String,
        transcriptName: String
    ) = """
    Abstraction Ladder analysis complete for '$concept' with $levels levels in $direction direction(s).
    **Duration:** ${duration / 1000}s
    Detailed analysis: <a href='${task.linkTo(transcriptName)}' target='_blank'>View Full Report</a>
  """.trimIndent()


    companion object {
        private val log: Logger = LoggerFactory.getLogger(AbstractionLadderTask::class.java)
        @JvmStatic val AbstractionLadder = TaskType(
          name = "AbstractionLadder",
          category = "Reasoning",
          taskClass = AbstractionLadderTask::class.java,
          executionConfigClass = AbstractionLadderTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Traverse abstraction levels to identify patterns and design insights",
          tooltipHtml = """
                        Analyzes concepts by moving up and down abstraction levels.
                        <ul>
                          <li>Move up to find generalizations and patterns</li>
                          <li>Move down to find specific implementations</li>
                          <li>Identify design patterns at each level</li>
                          <li>Discover refactoring opportunities</li>
                          <li>Analyze architectural patterns</li>
                          <li>Find code smells and anti-patterns</li>
                          <li>Generate actionable recommendations</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/AdversarialReasoningTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class AdversarialReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: AdversarialReasoningTaskExecutionConfigData?
) : AbstractTask<AdversarialReasoningTask.AdversarialReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxDescriptionLength = 1500

    data class VulnerabilityReport(
        val category: String = "",
        val severity: String = "",
        val description: String = "",
        val attack_scenario: String = "",
        val potential_impact: String = "",
        val exploit_steps: List<String> = emptyList(),
        val mitigation_strategies: List<String> = emptyList()
    ) : ValidatedObject {
        override fun toString(): String = """
            ### ${severity.uppercase()}: $category
            $description
        """.trimIndent()
        override fun validate(): String? {
            if (category.isBlank()) return "VulnerabilityReport: category cannot be blank"
            if (severity.isBlank()) return "VulnerabilityReport: severity cannot be blank"
            if (severity.lowercase() !in listOf("critical", "high", "medium", "low")) {
                return "VulnerabilityReport: severity must be one of: critical, high, medium, low"
            }
            if (description.isBlank()) return "VulnerabilityReport: description cannot be blank"
            return ValidatedObject.validateFields(this)
        }
    }
    data class VulnerabilityList(
        val vulnerabilities: List<VulnerabilityReport> = emptyList()
    ) : ValidatedObject { override fun validate() = vulnerabilities.mapNotNull { it.validate() }.firstOrNull() }

    class AdversarialReasoningTaskExecutionConfigData(
        @Description("The target system, design, or argument to analyze for weaknesses")
        val target_system: String? = null,
        @Description("Attack vectors to explore: 'security', 'performance', 'logic', 'business', 'privacy', 'compliance'")
        val attack_vectors: List<String>? = listOf("security", "logic"),
        @Description("Adversary capability level: 'basic', 'intermediate', 'advanced', 'nation-state'")
        val adversary_capability: String = "intermediate",
        @Description("Whether to generate detailed exploit scenarios")
        val generate_exploits: Boolean = false,
        @Description("Whether to suggest mitigation strategies")
        val suggest_mitigations: Boolean = true,
        @Description("Related files or code to analyze (glob patterns)")
        val related_files: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Specific assumptions to challenge")
        val challenge_assumptions: List<String>? = null,
        @Description("Maximum number of vulnerabilities to identify per vector")
        val max_vulnerabilities_per_vector: Int = 5,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = AdversarialReasoning.name,
        task_description = task_description
            ?: "Red team analysis of '$target_system' with ${attack_vectors?.size ?: 0} attack vectors",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (target_system.isNullOrBlank()) {
                return "AdversarialReasoningTaskExecutionConfigData: target_system is required"
            }

            attack_vectors?.forEach { vector ->
                if (vector.isBlank()) {
                    return "AdversarialReasoningTaskExecutionConfigData: invalid attack_vector '$vector'.}"
                }
            }

            if (adversary_capability.isBlank()) {
                return "AdversarialReasoningTaskExecutionConfigData: adversary_capability cannot be blank"
            }

            if (max_vulnerabilities_per_vector !in 1..20) {
                return "AdversarialReasoningTaskExecutionConfigData: max_vulnerabilities_per_vector must be between 1 and 20"
            }

            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
AdversarialReasoning - Red team analysis to identify vulnerabilities and weaknesses
  ** Specify target_system: the system, design, or argument to analyze
  ** Choose attack_vectors from: 'security', 'performance', 'logic', 'business', 'privacy', 'compliance'
  ** Set adversary_capability: 'basic', 'intermediate', 'advanced', 'nation-state'
  ** Enable generate_exploits for detailed attack scenarios (use with caution)
  ** Enable suggest_mitigations to get defensive recommendations
  ** Optionally specify related_files (glob patterns) to analyze code
  ** Optionally list challenge_assumptions to target specific beliefs
  ** Identifies vulnerabilities, edge cases, and failure modes
  ** Simulates adversarial thinking to stress test systems
  ** Produces structured vulnerability reports with severity ratings
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
        log.info("Starting AdversarialReasoningTask for target: '${executionConfig?.target_system}'")
        var transcriptStream: FileOutputStream? = null

        val targetSystem = executionConfig?.target_system
        if (targetSystem.isNullOrBlank()) {
            log.error("Configuration error: No target_system specified")
            task.safeComplete("CONFIGURATION ERROR: No target_system specified", log)
            resultFn("CONFIGURATION ERROR: No target_system specified for adversarial analysis")
            return
        }

        val api = defaultSmart ?: return

        val attackVectors = executionConfig.attack_vectors ?: listOf("security", "logic")
        val adversaryCapability = executionConfig.adversary_capability
        val generateExploits = executionConfig.generate_exploits
        val suggestMitigations = executionConfig.suggest_mitigations
        val relatedFiles = executionConfig.related_files
        val challengeAssumptions = executionConfig.challenge_assumptions
        val maxVulnerabilitiesPerVector = executionConfig.max_vulnerabilities_per_vector.coerceIn(1, 20)

        // Initialize transcript
        transcriptStream = initializeTranscript(task)
        transcriptStream?.let { stream ->
            writeTranscriptHeader(
                stream,
                targetSystem,
                attackVectors,
                adversaryCapability,
                generateExploits,
                suggestMitigations
            )
        }
        log.info(
            "Configuration: vectors=${attackVectors.size}, capability=$adversaryCapability, " +
                    "exploits=$generateExploits, mitigations=$suggestMitigations"
        )

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")
        transcriptStream?.let {
            it.write("# 🔴 Adversarial Reasoning / Red Team Analysis\n\n".toByteArray())
            it.write("**Target System:** $targetSystem\n\n".toByteArray())
            it.write("**Attack Vectors:** ${attackVectors.joinToString(", ")}\n\n".toByteArray())
            it.write("**Adversary Capability:** $adversaryCapability\n\n".toByteArray())
            it.write("**Generate Exploits:** ${if (generateExploits) "⚠️ Yes" else "No"}\n\n".toByteArray())
            it.write("**Suggest Mitigations:** ${if (suggestMitigations) "Yes" else "No"}\n\n".toByteArray())
            it.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )
            it.write("---\n\n".toByteArray())
            it.flush()
        }


        overviewTask.header("🔴 Adversarial Reasoning / Red Team Analysis")
        overviewTask.add(
          """
                      **Target System:** $targetSystem
                      **Attack Vectors:** ${attackVectors.joinToString(", ")}
                      **Adversary Capability:** $adversaryCapability
                      **Generate Exploits:** ${if (generateExploits) "⚠️ Yes" else "No"}
                      **Suggest Mitigations:** ${if (suggestMitigations) "Yes" else "No"}
                      ${if (!relatedFiles.isNullOrEmpty()) "**Related Files:** ${relatedFiles.size} patterns" else ""}
                      ${if (!challengeAssumptions.isNullOrEmpty()) "**Assumptions to Challenge:** ${challengeAssumptions.size}" else ""}
                      **Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                      """.trimIndent().renderMarkdown(true)
        )
        overviewTask.header("Progress", 2)
        overviewTask.add("*Initializing adversarial analysis...*".renderMarkdown(true))
        task.update()

        // Gather context
        val priorContext = getPriorCode(agent.executionState)
        val fileContext = if (!relatedFiles.isNullOrEmpty()) {
            buildString {
                appendLine("## Related Files Context")
                relatedFiles.forEach { pattern ->
                    appendLine("- Pattern: `$pattern`")
                }
            }
        } else ""

        val inputFileContent = getInputFileCode()
        if (priorContext.isNotBlank() || fileContext.isNotBlank() || inputFileContent.isNotBlank()) {
            val contextTask = tabs.newTask("Context")
            transcriptStream?.let {
                it.write("## Context for Analysis\n\n".toByteArray())
                if (priorContext.isNotBlank()) {
                    it.write("### Prior Task Results\n\n".toByteArray())
                    it.write("${priorContext.truncateForDisplay()}\n\n".toByteArray())
                }
            }
            contextTask.header("Context for Analysis")
            if (priorContext.isNotBlank()) {
                contextTask.expandable("Prior Task Results", priorContext.truncateForDisplay().renderMarkdown(true))
            }
            if (inputFileContent.isNotBlank()) {
                contextTask.expandable("Input Files", inputFileContent.renderMarkdown(true))
            }
            if (fileContext.isNotBlank()) {
                contextTask.add(fileContext.renderMarkdown(true))
            }
            contextTask.complete()
            task.update()
        }

        overviewTask.add(
          buildString {
            appendLine()
            appendLine("✅ Context gathered")
            appendLine()
            appendLine("*Beginning adversarial analysis...*")
          }.renderMarkdown(true)
        )
        task.update()

        val allVulnerabilities = mutableListOf<VulnerabilityReport>()
        val allEdgeCases = mutableListOf<String>()
        val allFailureModes = mutableListOf<String>()
        val vectorAnalysisTimes = mutableMapOf<String, Long>()

        try {
            // Analyze each attack vector
            attackVectors.forEachIndexed { index, vector ->
                val vectorStartTime = System.currentTimeMillis()
                log.info("Analyzing attack vector ${index + 1}/${attackVectors.size}: $vector")

                val vectorTask = tabs.newTask("Vector: ${vector.capitalize()}")
                transcriptStream?.let {
                    it.write("## Attack Vector: ${vector.capitalize()}\n\n".toByteArray())
                    it.write("**Adversary Capability:** $adversaryCapability\n\n".toByteArray())
                    it.write("---\n\n".toByteArray())
                    it.flush()
                }


                vectorTask.header("Attack Vector: ${vector.capitalize()}")
                vectorTask.add(
                  """
                                      **Status:** Analyzing...
                                      **Adversary Capability:** $adversaryCapability
                                      """.trimIndent().renderMarkdown(true)
                )
                task.update()

                // Create adversarial agent for this vector
                val adversarialAgent = createAdversarialAgent(
                    vector = vector,
                    adversaryCapability = adversaryCapability,
                   generateExploits = generateExploits,
                    api = api,
                )

                val analysisPrompt = buildAnalysisPrompt(
                    targetSystem = targetSystem,
                    vector = vector,
                    adversaryCapability = adversaryCapability,
                    priorContext = priorContext,
                    fileContext = fileContext,
                    challengeAssumptions = challengeAssumptions,
                    maxVulnerabilities = maxVulnerabilitiesPerVector,
                    generateExploits = generateExploits
                )

                vectorTask.header("Analysis in Progress", 2)
                vectorTask.add("*Identifying vulnerabilities and weaknesses...*".renderMarkdown(true))
                task.update()

                // Perform analysis
                val analysisResult = adversarialAgent.answer(listOf(analysisPrompt)).obj
                val parsedVulnerabilities = analysisResult.vulnerabilities
                
                transcriptStream?.let {
                    it.write("### Analysis Results\n\n".toByteArray())
                    it.write(parsedVulnerabilities.joinToString("\n\n").toByteArray())
                    it.flush()
                }


                vectorTask.header("Analysis Results", 2)
                vectorTask.add(parsedVulnerabilities.joinToString("\n\n").renderMarkdown(true))
                task.update()

                allVulnerabilities.addAll(parsedVulnerabilities)



                val vectorTime = System.currentTimeMillis() - vectorStartTime
                vectorAnalysisTimes[vector] = vectorTime
                transcriptStream?.let {
                    it.write("**Vulnerabilities Found:** ${parsedVulnerabilities.size}\n\n".toByteArray())
                    it.write("**Analysis Time:** ${vectorTime / 1000.0}s\n\n".toByteArray())
                    it.write("---\n\n".toByteArray())
                    it.flush()
                }


                vectorTask.add(
                  buildString {
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                    appendLine()
                    appendLine("**Vulnerabilities Found:** ${parsedVulnerabilities.size}")
                    appendLine()
                    appendLine("**Analysis Time:** ${vectorTime / 1000.0}s")
                  }.renderMarkdown(true)
                )
                vectorTask.complete()
                task.update()

                overviewTask.add(
                  buildString {
                    appendLine()
                    appendLine("✅ Vector '${vector}' analyzed (${parsedVulnerabilities.size} vulnerabilities, ${vectorTime / 1000.0}s)")
                    if (index < attackVectors.size - 1) {
                      appendLine()
                      appendLine("*Analyzing next vector...*")
                    }
                  }.renderMarkdown(true)
                )
                task.update()

                log.info("Completed analysis of vector '$vector': ${parsedVulnerabilities.size} vulnerabilities in ${vectorTime}ms")
            }

            // Generate mitigations if requested
            if (suggestMitigations && allVulnerabilities.isNotEmpty()) {
                log.info("Generating mitigation strategies")
                val mitigationTask = tabs.newTask("Mitigations")

                mitigationTask.header("🛡️ Mitigation Strategies")
                mitigationTask.add("**Status:** Generating recommendations...".renderMarkdown(true))
                task.update()
                transcriptStream?.let {
                    it.write("## 🛡️ Mitigation Strategies\n\n".toByteArray())
                }


                val mitigationAgent = createMitigationAgent(api)
                val mitigationPrompt = buildMitigationPrompt(
                    targetSystem = targetSystem,
                    vulnerabilities = allVulnerabilities,
                    adversaryCapability = adversaryCapability
                )

                val mitigations = mitigationAgent.answer(listOf(mitigationPrompt))
                transcriptStream?.let {
                    it.write("$mitigations\n\n".toByteArray())
                    it.flush()
                }


                mitigationTask.header("Recommended Mitigations", 2)
                mitigationTask.add(mitigations.renderMarkdown(true))
                mitigationTask.add("**Status:** ✅ Complete".renderMarkdown(true))
                mitigationTask.complete()
                task.update()

                overviewTask.add(
                  buildString {
                    appendLine()
                    appendLine("✅ Mitigation strategies generated")
                  }.renderMarkdown(true)
                )
                task.update()
            }

            // Generate executive summary
            log.info("Generating executive summary")
            val summaryTask = tabs.newTask("Executive Summary")

            summaryTask.header("📊 Executive Summary")
            summaryTask.add("**Status:** Generating summary...".renderMarkdown(true))
            task.update()

            val summary = generateExecutiveSummary(
                targetSystem = targetSystem,
                attackVectors = attackVectors,
                adversaryCapability = adversaryCapability,
                vulnerabilities = allVulnerabilities,
                edgeCases = allEdgeCases,
                failureModes = allFailureModes,
                totalTime = System.currentTimeMillis() - startTime
            )
            transcriptStream?.let {
                it.write("## 📊 Executive Summary\n\n".toByteArray())
                it.write("$summary\n\n".toByteArray())
                it.flush()
            }


            summaryTask.header("Summary", 2)
            summaryTask.add(summary.renderMarkdown(true))
            summaryTask.add("**Status:** ✅ Complete".renderMarkdown(true))
            summaryTask.complete()
            task.update()

            // Final overview update
            val totalTime = System.currentTimeMillis() - startTime
            transcriptStream?.let {
                it.write("---\n\n".toByteArray())
                it.write("## ✅ Analysis Complete\n\n".toByteArray())
                it.write("**Total Time:** ${totalTime / 1000.0}s\n\n".toByteArray())
                it.write("**Total Vulnerabilities:** ${allVulnerabilities.size}\n\n".toByteArray())
                it.write("**Edge Cases Identified:** ${allEdgeCases.size}\n\n".toByteArray())
                it.write("**Failure Modes:** ${allFailureModes.size}\n\n".toByteArray())
                it.flush()
                it.close()
            }

            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Analysis Complete")
                appendLine()
                appendLine("**Total Time:** ${totalTime / 1000.0}s")
                appendLine()
                appendLine("**Attack Vectors Analyzed:** ${attackVectors.size}")
                appendLine()
                appendLine("**Total Vulnerabilities:** ${allVulnerabilities.size}")
                appendLine()
                appendLine("**Edge Cases Identified:** ${allEdgeCases.size}")
                appendLine()
                appendLine("**Failure Modes:** ${allFailureModes.size}")
                appendLine()
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )
            overviewTask.complete()
            task.update()

            // Build concise result for orchestrator
            val conciseResult = buildString {
                appendLine("# Adversarial Analysis: $targetSystem")
                appendLine()
                appendLine("**Adversary Capability:** $adversaryCapability")
                appendLine()
                appendLine("## Key Findings")
                appendLine()
                appendLine("- **Total Vulnerabilities:** ${allVulnerabilities.size}")
                appendLine(
                    "- **Critical/High Severity:** ${
                        allVulnerabilities.count {
                            it.severity in listOf(
                                "critical",
                                "high"
                            )
                        }
                    }"
                )
                appendLine("- **Attack Vectors:** ${attackVectors.joinToString(", ")}")
                appendLine()

                if (allVulnerabilities.isNotEmpty()) {
                    appendLine("## Top Vulnerabilities")
                    appendLine()
                    allVulnerabilities
                        .sortedByDescending { severityToInt(it.severity) }
                        .take(5)
                        .forEach { vuln ->
                            appendLine("### ${vuln.severity.uppercase()}: ${vuln.category}")
                            appendLine(vuln.description.truncateForDisplay(maxDescriptionLength))
                            appendLine()
                        }
                }

                appendLine("## Statistics")
                appendLine("- Analysis Time: ${totalTime / 1000.0}s")
                appendLine("- Vectors Analyzed: ${attackVectors.size}")
                appendLine("- Edge Cases: ${allEdgeCases.size}")
                appendLine("- Failure Modes: ${allFailureModes.size}")
            }

            task.safeComplete(
                "Adversarial analysis completed: ${allVulnerabilities.size} vulnerabilities found across ${attackVectors.size} vectors in ${totalTime / 1000}s",
                log
            )

            log.info(
                "AdversarialReasoningTask completed: total_time=${totalTime}ms, " +
                        "vectors=${attackVectors.size}, vulnerabilities=${allVulnerabilities.size}, " +
                        "edge_cases=${allEdgeCases.size}, failure_modes=${allFailureModes.size}"
            )

            resultFn(conciseResult)

        } catch (e: Exception) {
            transcriptStream?.let {
                it.write("\n\n---\n\n".toByteArray())
                it.write("## ❌ Error Occurred\n\n".toByteArray())
                it.write("**Error:** ${e.message}\n\n".toByteArray())
                it.write("**Type:** ${e.javaClass.simpleName}\n\n".toByteArray())
                it.flush()
                it.close()
            }
            log.error("Error during adversarial reasoning", e)
            task.error(e)

            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ❌ Error Occurred")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Type:** ${e.javaClass.simpleName}")
              }.renderMarkdown(true)
            )
            overviewTask.complete()
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Adversarial Analysis")
                appendLine()
                appendLine("**Target:** $targetSystem")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Vectors Completed:** ${vectorAnalysisTimes.size} of ${attackVectors.size}")
                appendLine()
                if (allVulnerabilities.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine("**Vulnerabilities Found:** ${allVulnerabilities.size}")
                }
            }
            resultFn(errorOutput)
        } finally {
            transcriptStream?.close()
            log.debug("Transcript stream closed")
        }
    }

    private fun initializeTranscript(task: SessionTask): FileOutputStream? {
        return try {
            val transcriptFile = "adversarial_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
            val transcriptStream = file?.outputStream()
            task.add(
                "Writing transcript to <a href='$link' target='_blank'>$link</a> " +
                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                        "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
            )
            log.info("Initialized transcript file: $link")
            transcriptStream
        } catch (e: Exception) {
            log.error("Failed to initialize transcript", e)
            null
        }
    }

    private fun writeTranscriptHeader(
        stream: FileOutputStream,
        targetSystem: String,
        attackVectors: List<String>,
        adversaryCapability: String,
        generateExploits: Boolean,
        suggestMitigations: Boolean
    ) {
        try {
            val header = buildString {
                appendLine("# 🔴 Adversarial Reasoning / Red Team Analysis Transcript")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("**Target System:** $targetSystem")
                appendLine("**Attack Vectors:** ${attackVectors.joinToString(", ")}")
                appendLine("**Adversary Capability:** $adversaryCapability")
                appendLine("**Generate Exploits:** ${if (generateExploits) "⚠️ Yes" else "No"}")
                appendLine("**Suggest Mitigations:** ${if (suggestMitigations) "Yes" else "No"}")
                appendLine()
                appendLine("---")
                appendLine()
            }
            stream.write(header.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            stream.flush()
        } catch (e: Exception) {
            log.error("Failed to write transcript header", e)
        }
    }

    private fun getInputFileCode(): String = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
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
                    file.readText()
                }
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    private fun isTextFile(file: java.io.File): Boolean {
        val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: java.io.File): String = try {
        file.getDocumentReader().use { reader ->
            when (reader) {
                is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                else -> reader.getText()
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
        try {
            file.readText()
        } catch (e2: Exception) {
            "Error reading file: ${e2.message}"
        }
    }


    private fun createAdversarialAgent(
        vector: String,
        adversaryCapability: String,
        generateExploits: Boolean,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface,
    ): ParsedAgent<VulnerabilityList> {
        val capabilityDescription = when (adversaryCapability.lowercase()) {
            "basic" -> "You have basic technical skills and use common tools and techniques."
            "intermediate" -> "You have solid technical skills, understand common vulnerabilities, and can chain exploits."
            "advanced" -> "You are a skilled security researcher with deep technical knowledge and creative attack strategies."
            "nation-state" -> "You have unlimited resources, advanced persistent threat capabilities, and can develop zero-day exploits."
            else -> "You have intermediate technical skills."
        }

        val exploitWarning = if (generateExploits) {
            "\n\nProvide detailed exploit scenarios and proof-of-concept steps. Be specific and technical."
        } else {
            "\n\nDescribe vulnerabilities conceptually without providing detailed exploit code or step-by-step instructions."
        }

        return ParsedAgent(
            resultClass = VulnerabilityList::class.java,
            prompt = """
You are a red team security analyst performing adversarial reasoning on a system.
Your focus is on the '$vector' attack vector.

$capabilityDescription

Your goal is to:
1. Think like an attacker trying to exploit the system
2. Identify vulnerabilities, weaknesses, and edge cases
3. Challenge assumptions aggressively
4. Find failure modes and breaking points
5. Consider both technical and non-technical attack surfaces

Be thorough, creative, and adversarial in your thinking.

Consider unconventional attack paths and second-order effects.$exploitWarning
            """.trimIndent(),
            model = api,
            parsingChatter = api,
            temperature = 0.8 // Higher temperature for creative adversarial thinking
        )
    }

    private fun createMitigationAgent(api: com.simiacryptus.cognotik.chat.model.ChatInterface): ChatAgent {
        return ChatAgent(
            prompt = """
You are a security architect specializing in defensive strategies and risk mitigation.

Your role is to:
1. Analyze identified vulnerabilities
2. Propose practical, implementable mitigations
3. Prioritize defenses based on risk and impact
4. Consider defense-in-depth strategies
5. Balance security with usability and performance

Provide actionable recommendations with clear implementation guidance.
Consider both immediate fixes and long-term architectural improvements.
            """.trimIndent(),
            model = api,
            temperature = 0.5
        )
    }

    private fun buildAnalysisPrompt(
        targetSystem: String,
        vector: String,
        adversaryCapability: String,
        priorContext: String,
        fileContext: String,
        challengeAssumptions: List<String>?,
        maxVulnerabilities: Int,
        generateExploits: Boolean
    ): String {
        return buildString {
            appendLine("# Red Team Analysis Task")
            appendLine()
            appendLine("## Target System")
            appendLine(targetSystem)
            appendLine()

            if (priorContext.isNotBlank()) {
                appendLine("## System Context")
                appendLine(priorContext.truncateForDisplay(5000))
                appendLine()
            }

            if (fileContext.isNotBlank()) {
                appendLine(fileContext)
                appendLine()
            }

            appendLine("## Attack Vector Focus")
            appendLine("**Vector:** $vector")
            appendLine()
            appendLine("**Your Capability Level:** $adversaryCapability")
            appendLine()

            if (!challengeAssumptions.isNullOrEmpty()) {
                appendLine("## Assumptions to Challenge")
                challengeAssumptions.forEach { assumption ->
                    appendLine("- $assumption")
                }
                appendLine()
            }

            appendLine("## Analysis Requirements")
            appendLine()
            appendLine("Identify up to $maxVulnerabilities vulnerabilities in the '$vector' category and return them as a structured list.")

            if (generateExploits) {
                appendLine("* **Exploit Steps**: [Detailed technical steps to exploit]")
            }

            appendLine()
            appendLine("Think creatively and adversarially. Consider:")
            appendLine("- What assumptions does the system make?")
            appendLine("- What happens at boundaries and limits?")
            appendLine("- How could components interact in unexpected ways?")
            appendLine("- What would a motivated attacker try?")
            appendLine()
            appendLine("Provide your analysis in a structured format with clear sections.")
        }
    }

    private fun buildMitigationPrompt(
        targetSystem: String,
        vulnerabilities: List<VulnerabilityReport>,
        adversaryCapability: String
    ): String {
        return buildString {
            appendLine("# Mitigation Strategy Development")
            appendLine()
            appendLine("## Target System")
            appendLine(targetSystem)
            appendLine()
            appendLine("## Threat Model")
            appendLine("**Adversary Capability:** $adversaryCapability")
            appendLine()
            appendLine("## Identified Vulnerabilities")
            appendLine()

            vulnerabilities
                .sortedByDescending { severityToInt(it.severity) }
                .forEach { vuln ->
                    appendLine("### ${vuln.severity.uppercase()}: ${vuln.category}")
                    appendLine(vuln.description)
                    appendLine()
                }

            appendLine("## Required Mitigations")
            appendLine()
            appendLine("For each vulnerability category, provide:")
            appendLine("1. **Immediate Actions**: Quick fixes or workarounds")
            appendLine("2. **Short-term Solutions**: Tactical improvements (weeks)")
            appendLine("3. **Long-term Strategy**: Architectural changes (months)")
            appendLine("4. **Detection & Monitoring**: How to detect exploitation attempts")
            appendLine("5. **Priority**: Based on severity and exploitability")
            appendLine()
            appendLine("Consider defense-in-depth principles:")
            appendLine("- Multiple layers of security")
            appendLine("- Fail-safe defaults")
            appendLine("- Principle of least privilege")
            appendLine("- Input validation and sanitization")
            appendLine("- Security monitoring and logging")
            appendLine()
            appendLine("Provide practical, implementable recommendations.")
        }
    }


        
        


        






















    private fun generateExecutiveSummary(
        targetSystem: String,
        attackVectors: List<String>,
        adversaryCapability: String,
        vulnerabilities: List<VulnerabilityReport>,
        edgeCases: List<String>,
        failureModes: List<String>,
        totalTime: Long
    ): String {
        val criticalCount = vulnerabilities.count { it.severity == "critical" }
        val highCount = vulnerabilities.count { it.severity == "high" }
        val mediumCount = vulnerabilities.count { it.severity == "medium" }
        val lowCount = vulnerabilities.count { it.severity == "low" }

        return buildString {
            appendLine("## Overview")
            appendLine()
            appendLine("Red team analysis of **$targetSystem** completed against a **$adversaryCapability** adversary model.")
            appendLine()
            appendLine("## Risk Assessment")
            appendLine()
            appendLine("| Severity | Count |")
            appendLine("|----------|-------|")
            appendLine("| 🔴 Critical | $criticalCount |")
            appendLine("| 🟠 High | $highCount |")
            appendLine("| 🟡 Medium | $mediumCount |")
            appendLine("| 🟢 Low | $lowCount |")
            appendLine()

            val overallRisk = when {
                criticalCount > 0 -> "🔴 **CRITICAL** - Immediate action required"
                highCount > 2 -> "🟠 **HIGH** - Urgent attention needed"
                highCount > 0 || mediumCount > 3 -> "🟡 **MEDIUM** - Should be addressed soon"
                else -> "🟢 **LOW** - Monitor and improve over time"
            }

            appendLine("**Overall Risk Level:** $overallRisk")
            appendLine()

            appendLine("## Attack Surface Analysis")
            appendLine()
            appendLine("**Vectors Analyzed:** ${attackVectors.joinToString(", ")}")
            appendLine()
            appendLine("**Edge Cases Identified:** ${edgeCases.size}")
            appendLine()
            appendLine("**Failure Modes:** ${failureModes.size}")
            appendLine()

            if (vulnerabilities.isNotEmpty()) {
                appendLine("## Top Concerns")
                appendLine()
                vulnerabilities
                    .sortedByDescending { severityToInt(it.severity) }
                    .take(3)
                    .forEachIndexed { index, vuln ->
                        appendLine("${index + 1}. **${vuln.category}** (${vuln.severity})")
                        appendLine("   - ${vuln.description.truncateForDisplay(maxDescriptionLength)}")
                        appendLine()
                    }
            }

            appendLine("## Recommendations")
            appendLine()
            when {
                criticalCount > 0 -> {
                    appendLine("1. **Immediate:** Address all critical vulnerabilities within 24-48 hours")
                    appendLine("2. **Urgent:** Implement temporary mitigations for high-severity issues")
                    appendLine("3. **Short-term:** Develop comprehensive remediation plan")
                }

                highCount > 0 -> {
                    appendLine("1. **Priority:** Address high-severity vulnerabilities within 1-2 weeks")
                    appendLine("2. **Planning:** Schedule remediation for medium-severity issues")
                    appendLine("3. **Monitoring:** Implement detection for identified attack patterns")
                }

                else -> {
                    appendLine("1. **Continuous Improvement:** Address identified issues in regular sprint cycles")
                    appendLine("2. **Monitoring:** Implement logging and alerting for edge cases")
                    appendLine("3. **Testing:** Add test coverage for identified failure modes")
                }
            }

            appendLine()
            appendLine("---")
            appendLine()
            appendLine("*Analysis completed in ${totalTime / 1000.0} seconds*")
        }
    }

    private fun severityToInt(severity: String): Int {
        return when (severity.trim().lowercase()) {
            "critical" -> 4
            "high" -> 3
            "medium" -> 2
            "low" -> 1
            else -> 0
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(AdversarialReasoningTask::class.java)
        @JvmStatic val AdversarialReasoning = TaskType(
          name = "AdversarialReasoning",
          category = "Reasoning",
          taskClass = AdversarialReasoningTask::class.java,
          executionConfigClass = AdversarialReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Red team analysis to identify vulnerabilities and weaknesses",
          tooltipHtml = """
                        Performs adversarial reasoning and red team analysis on systems, designs, or arguments.
                        <ul>
                          <li>Identifies security vulnerabilities and attack vectors</li>
                          <li>Challenges assumptions aggressively</li>
                          <li>Finds edge cases and failure modes</li>
                          <li>Simulates adversarial scenarios at different capability levels</li>
                          <li>Stress tests logical arguments and system designs</li>
                          <li>Generates detailed vulnerability reports with severity ratings</li>
                          <li>Optionally provides exploit scenarios and mitigation strategies</li>
                          <li>Supports multiple attack vectors: security, performance, logic, business, privacy, compliance</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/AnalogicalReasoningTask.kt

```
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
            transcriptStream = task.transcript()
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/BrainstormingTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class BrainstormingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: BrainstormingTaskExecutionConfigData?
) : AbstractTask<BrainstormingTask.BrainstormingTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    private var transcriptStream: FileOutputStream? = null
    protected val codeFiles = mutableMapOf<Path, String>()

    data class BrainstormedOption(
        val title: String = "",
        val description: String = "",
        val category: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "BrainstormedOption title cannot be blank"
            if (description.isBlank()) return "BrainstormedOption description cannot be blank"
            return null
        }
    }

    data class BrainstormResult(
        val options: List<BrainstormedOption> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (options.isEmpty()) return "BrainstormResult must contain at least one option"
            options.forEach { option ->
                option.validate()?.let { return it }
            }
            return null
        }
    }

    data class OptionAnalysis(
        val pros: List<String> = emptyList(),
        val cons: List<String> = emptyList(),
        val feasibility: String = "",
        val impact: String = "",
        val risks: List<String> = emptyList(),
        val requirements: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (feasibility.isBlank()) return "OptionAnalysis feasibility cannot be blank"
            if (impact.isBlank()) return "OptionAnalysis impact cannot be blank"
            return null
        }
    }
    data class BrainstormingSummary(
        val overview: String = "",
        val top_option_index: Int = 1,
        val selection_reasoning: String = "",
        val next_steps: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (overview.isBlank()) return "Summary overview cannot be blank"
            if (selection_reasoning.isBlank()) return "Selection reasoning cannot be blank"
            return null
        }
    }



    class BrainstormingTaskExecutionConfigData(
        @Description("The problem or question to brainstorm solutions for")
        val problem_statement: String? = null,
        @Description("A list of specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Number of options to generate (default: 5-10)")
        val target_option_count: Int = 7,
        @Description("A list of categories or domains to consider (optional)")
        val categories: List<String>? = null,
        @Description("A list of constraints or requirements to consider")
        val constraints: List<String>? = null,
        @Description("Whether to include creative/unconventional options")
        val include_creative_options: Boolean = true,
        @Description("Depth of analysis for each option (brief/moderate/detailed)")
        val analysis_depth: String = "moderate",
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,

        ) : TaskExecutionConfig(
        task_type = Brainstorming.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (problem_statement.isNullOrBlank()) {
                return "BrainstormingTaskExecutionConfigData problem_statement cannot be null or blank"
            }
            if (target_option_count < 3 || target_option_count > 20) {
                return "BrainstormingTaskExecutionConfigData target_option_count must be between 3 and 20"
            }
            return super.validate()
        }
    }

    override fun promptSegment(): String {
        return """
Brainstorming - Generate and analyze multiple solution options
  ** Specify the problem or question to brainstorm solutions for
  ** Configure target number of options (default: 7)
  ** Optionally specify categories or domains to explore
  ** Define constraints or requirements
  ** Enable/disable creative/unconventional options
  ** Set analysis depth (brief/moderate/detailed)
  ** Generates diverse options, analyzes each independently
  ** Provides comparative summary with recommendations
  ** Useful for:
     - Solution exploration
     - Decision making
     - Strategic planning
     - Problem solving
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
        log.info("Starting BrainstormingTask for problem: '${executionConfig?.problem_statement}'")

        val problemStatement = executionConfig?.problem_statement
        if (problemStatement.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No problem statement specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val targetCount = executionConfig.target_option_count.coerceIn(3, 20)
        val categories = executionConfig.categories?.joinToString(", ") ?: "any relevant domain"
        val constraints = executionConfig.constraints ?: emptyList()
        val includeCreative = executionConfig.include_creative_options
        val analysisDepth = executionConfig.analysis_depth

        log.info("Configuration: targetCount=$targetCount, categories=$categories, includeCreative=$includeCreative, analysisDepth=$analysisDepth")
        log.info("Input files: ${executionConfig?.input_files?.joinToString(", ") ?: "none"}")

        val ui = task.ui

        try {
            // Initialize transcript
            transcriptStream = task.transcript()
            transcriptStream?.write("# Brainstorming Session Transcript\n\n".toByteArray())
            transcriptStream?.write("**Input Files:** ${executionConfig.input_files?.joinToString(", ") ?: "none"}\n\n".toByteArray())
            transcriptStream?.write("**Problem Statement:** $problemStatement\n\n".toByteArray())
            transcriptStream?.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )
            transcriptStream?.write("---\n\n".toByteArray())

            // Create tabbed display for organized output
            val tabs = TabbedDisplay(task)

            // Overview tab
            val overviewTask = tabs.newTask("Overview")
            overviewTask.header("Brainstorming: $problemStatement")

            val overviewContent = buildString {
                appendLine("**Problem Statement:** $problemStatement")
                appendLine()
                appendLine("**Target Options:** $targetCount")
                appendLine()
                appendLine("**Categories:** $categories")
                appendLine()
                if (constraints.isNotEmpty()) {
                    appendLine("**Constraints:**")
                    constraints.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("**Include Creative Options:** ${if (includeCreative) "Yes" else "No"}")
                appendLine()
                appendLine("**Analysis Depth:** $analysisDepth")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                if (!executionConfig?.input_files.isNullOrEmpty()) {
                    appendLine()
                    appendLine("**Input Files:**")
                    executionConfig?.input_files?.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
            overviewTask.add(MarkdownUtil.renderMarkdown(overviewContent, ui = ui))
            val progressStatus = overviewTask.add(MarkdownUtil.renderMarkdown("🔄 *Generating options...*", ui = ui))
            task.update()

            // Get input file content
            val inputFileContent = getInputFileCode()
            if (inputFileContent.isNotBlank()) {
                log.debug("Found input file content: ${inputFileContent.length} characters")
                val inputFilesTask = tabs.newTask("Input Files")
                inputFilesTask.add(MarkdownUtil.renderMarkdown(inputFileContent, ui = ui))
                inputFilesTask.complete()
                task.update()
                transcriptStream?.write("\n## Input Files\n\n$inputFileContent\n\n".toByteArray())
            }


            // Gather context from previous tasks
            val priorContext = getPriorCode(agent.executionState)
            if (priorContext.isNotBlank()) {
                log.debug("Found prior context: ${priorContext.length} characters")
                val contextTask = tabs.newTask("Context")
                contextTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |# Context from Previous Tasks
            |
            |${priorContext.truncateForDisplay()}
            """.trimMargin(), ui = ui
                    )
                )
                contextTask.complete()
                task.update()
            }

            // Step 1: Generate options using ParsedActor for structured output
            log.info("Generating $targetCount options")
            val optionsTask = tabs.newTask("Generated Options")
            val optionsStatus = optionsTask.add(
                MarkdownUtil.renderMarkdown(
                    "## Generated Options\n\n🔄 Brainstorming options...",
                    ui = ui
                )
            )
            task.update()

            val brainstormPrompt = buildBrainstormPrompt(
                problemStatement,
                targetCount,
                categories,
                constraints,
                includeCreative,
                priorContext,
                inputFileContent
            )

            val api = defaultSmart ?: return
            val parsingChatter = defaultFast.getChildClient(task)
            val defaultChatter = api.getChildClient(task)
            val brainstormAgent = ParsedAgent(
                resultClass = BrainstormResult::class.java,
                prompt = brainstormPrompt,
                model = defaultChatter,
                temperature = if (includeCreative) 0.8 else 0.6,
                parsingChatter = parsingChatter
            )

            val brainstormResult = brainstormAgent.answer(listOf(brainstormPrompt))
            val options = brainstormResult.obj.options

            log.info("Generated ${options.size} options")
            // Write to transcript
            transcriptStream?.write("\n## Generated Options\n\n".toByteArray())
            options.forEachIndexed { index, option ->
                transcriptStream?.write("### ${index + 1}. ${option.title}\n".toByteArray())
                if (option.category != null) {
                    transcriptStream?.write("**Category:** ${option.category}\n\n".toByteArray())
                }
                transcriptStream?.write("${option.description}\n\n".toByteArray())
            }

            // Display generated options
            optionsStatus?.setLength(0)
            optionsTask.add(
                MarkdownUtil.renderMarkdown(
                    buildString {
                        appendLine("## Generated Options")
                        appendLine()
                        appendLine("✅ Generated ${options.size} options")
                        appendLine()
                        options.forEachIndexed { index, option ->
                            appendLine("### ${index + 1}. ${option.title}")
                            if (option.category != null) {
                                appendLine("**Category:** ${option.category}")
                            }
                            appendLine()
                            appendLine(option.description)
                            appendLine()
                        }
                    }, ui = ui
                )
            )
            optionsTask.complete()
            task.update()

            // Update overview
            progressStatus?.setLength(0)
            progressStatus?.append(MarkdownUtil.renderMarkdown("✅ Generated ${options.size} options\n\n🔄 *Analyzing each option...*", ui = ui))
            task.update()

            // Step 2: Analyze each option independently
            log.info("Analyzing ${options.size} options")
            val analyses = mutableMapOf<Int, OptionAnalysis>()
            val analysisAgent = ParsedAgent(
                resultClass = OptionAnalysis::class.java,
                prompt = "", // Will be set per option
                model = defaultChatter,
                temperature = 0.3,
                parsingChatter = parsingChatter
            )

            options.forEachIndexed { index, option ->
                val optionNumber = index + 1
                log.debug("Analyzing option $optionNumber: ${option.title}")

                val analysisTask = tabs.newTask("Option $optionNumber Analysis")
                val analysisStatus = analysisTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |# Option $optionNumber: ${option.title}
            |
            |🔄 Analyzing...
            """.trimMargin(), ui = ui
                    )
                )
                task.update()

                val analysisPrompt = buildAnalysisPrompt(
                    option,
                    problemStatement,
                    constraints,
                    analysisDepth
                )

                val analysis = analysisAgent.answer(listOf(analysisPrompt))
                analyses[optionNumber] = analysis.obj
                // Write analysis to transcript
                transcriptStream?.write("\n## Option $optionNumber Analysis: ${option.title}\n\n".toByteArray())
                transcriptStream?.write("### ✅ Pros\n".toByteArray())
                analysis.obj.pros.forEach { transcriptStream?.write("- $it\n".toByteArray()) }
                transcriptStream?.write("\n### ❌ Cons\n".toByteArray())
                analysis.obj.cons.forEach { transcriptStream?.write("- $it\n".toByteArray()) }
                transcriptStream?.write("\n### 📊 Feasibility\n${analysis.obj.feasibility}\n\n".toByteArray())
                transcriptStream?.write("### 💥 Impact\n${analysis.obj.impact}\n\n".toByteArray())
                transcriptStream?.write("### ⚠️ Risks\n".toByteArray())
                analysis.obj.risks.forEach { transcriptStream?.write("- $it\n".toByteArray()) }
                transcriptStream?.write("\n### 📋 Requirements\n".toByteArray())
                analysis.obj.requirements.forEach { transcriptStream?.write("- $it\n".toByteArray()) }
                transcriptStream?.write("\n---\n\n".toByteArray())


                // Display analysis
                analysisStatus?.setLength(0)
                analysisTask.add(
                    MarkdownUtil.renderMarkdown(
                        buildString {
                            appendLine("# Option $optionNumber: ${option.title}")
                            appendLine()
                            if (option.category != null) {
                                appendLine("**Category:** ${option.category}")
                            }
                            appendLine()
                            appendLine("## Description")
                            appendLine(option.description)
                            appendLine()
                            appendLine("## Analysis")
                            appendLine()
                            appendLine("### ✅ Pros")
                            analysis.obj.pros.forEach { appendLine("- $it") }
                            appendLine()
                            appendLine("### ❌ Cons")
                            analysis.obj.cons.forEach { appendLine("- $it") }
                            appendLine()
                            appendLine("### 📊 Feasibility")
                            appendLine(analysis.obj.feasibility)
                            appendLine()
                            appendLine("### 💥 Impact")
                            appendLine(analysis.obj.impact)
                            appendLine()
                            appendLine("### ⚠️ Risks")
                            analysis.obj.risks.forEach { appendLine("- $it") }
                            appendLine()
                            appendLine("### 📋 Requirements")
                            analysis.obj.requirements.forEach { appendLine("- $it") }
                            appendLine()
                            appendLine("---")
                            appendLine()
                            appendLine("**Status:** ✅ Analysis complete")
                        }, ui = ui
                    )
                )
                analysisTask.complete()
                task.update()

                // Update overview
                progressStatus?.setLength(0)
                progressStatus?.append(MarkdownUtil.renderMarkdown("✅ Generated ${options.size} options\n✅ Analyzed $optionNumber/${options.size} options\n\n🔄 *Analyzing next option...*", ui = ui))
                task.update()
            }

            // Step 3: Generate comparative summary
            log.info("Generating comparative summary")
            val summaryTask = tabs.newTask("Summary & Recommendations")
            val summaryStatus = summaryTask.add(
                MarkdownUtil.renderMarkdown(
                    "## Summary & Recommendations\n\n🔄 Synthesizing findings...",
                    ui = ui
                )
            )
            progressStatus?.setLength(0)
            progressStatus?.append(MarkdownUtil.renderMarkdown("✅ Generated ${options.size} options\n✅ Analyzed all ${options.size} options\n\n🔄 *Synthesizing findings...*", ui = ui))
            task.update()

            val summaryPrompt = buildSummaryPrompt(
                problemStatement,
                options,
                analyses
            )

            val summaryAgent = ParsedAgent(
                resultClass = BrainstormingSummary::class.java,
                prompt = summaryPrompt,
                model = defaultChatter,
                temperature = 0.4,
                parsingChatter = parsingChatter
            )

            val summaryResult = summaryAgent.answer(listOf(summaryPrompt)).obj
            
            // Resolve top option safely
            val topOptionIndex = (summaryResult.top_option_index - 1).coerceIn(0, options.indices.last)
            val topOption = options[topOptionIndex]

            // Construct display string
            val summaryDisplay = buildString {
                appendLine("## 🏆 Top Recommendation: ${topOption.title}")
                appendLine()
                appendLine(summaryResult.selection_reasoning)
                appendLine()
                appendLine("### Overview")
                appendLine(summaryResult.overview)
                appendLine()
                appendLine("### Next Steps")
                appendLine(summaryResult.next_steps)
            }

            summaryStatus?.setLength(0)

            summaryTask.add(
                MarkdownUtil.renderMarkdown(
                    buildString {
                        appendLine("## Summary & Recommendations")
                        appendLine()
                        appendLine("✅ Analysis complete")
                        appendLine()
                        appendLine(summaryDisplay)
                    }, ui = ui
                )
            )
            summaryTask.complete()
            task.update()

            val totalTime = System.currentTimeMillis() - startTime
            // Write detailed results to file
            val detailedResults = buildDetailedResults(
                problemStatement,
                options,
                analyses,
                summaryDisplay,
                totalTime
            )
            val (resultsLink, resultsFile) = task.createFile("brainstorming_results.md")
            resultsFile?.outputStream()?.use { stream ->
                stream.write(detailedResults.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }
            log.info("Saved detailed results to: $resultsLink")

            // Finalize transcript
            transcriptStream?.write("\n## Session Complete\n\n".toByteArray())
            transcriptStream?.write("**Total Time:** ${totalTime / 1000.0}s\n".toByteArray())
            transcriptStream?.write("**Options Generated:** ${options.size}\n".toByteArray())
            transcriptStream?.write("**Options Analyzed:** ${analyses.size}\n".toByteArray())
            transcriptStream?.write(
                "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )
            transcriptStream?.flush()
            transcriptStream?.close()
            val transcriptLink = task.createFile("brainstorming_transcript.md").first
            // Build final concise output with file links
            val finalOutput = buildString {
                appendLine("# Brainstorming Results: $problemStatement")
                appendLine()
                appendLine("✅ Generated and analyzed ${options.size} options in ${totalTime / 1000}s")
                appendLine()
                appendLine("## 🏆 Top Recommendation: ${topOption.title}")
                appendLine()
                appendLine(topOption.description)
                appendLine()
                appendLine("> ${summaryResult.selection_reasoning}")
                appendLine()
                appendLine("## Summary")
                appendLine(summaryResult.overview.truncateForDisplay())
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Detailed Results")
                appendLine()
                appendLine(
                    "📄 [Full Results]($resultsLink) | [HTML](${resultsLink.removeSuffix(".md")}.html) | [PDF](${
                        resultsLink.removeSuffix(
                            ".md"
                        )
                    }.pdf)"
                )
                appendLine()
                appendLine(
                    "📋 [Transcript]($transcriptLink) | [HTML](${transcriptLink.removeSuffix(".md")}.html) | [PDF](${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.pdf)"
                )
                appendLine()
                appendLine("**Options:** ${options.size} | **Analysis Depth:** $analysisDepth | **Time:** ${totalTime / 1000}s")

                appendLine()
                appendLine()
                options.forEachIndexed { index, option ->
                    val prefix = if (index == topOptionIndex) "🏆 " else ""
                    appendLine("### $prefix${index + 1}. ${option.title}")
                    appendLine()
                }
                appendLine()
                appendLine()
                appendLine("---")

            }

            log.info("BrainstormingTask completed: total_time=${totalTime}ms, options=${options.size}, output_size=${finalOutput.length} chars")

            // Update overview with completion
            progressStatus?.setLength(0)
            progressStatus?.append(MarkdownUtil.renderMarkdown("""
                |---
                |## ✅ Brainstorming Complete
                |
                |### 🏆 Winner: ${topOption.title}
                |${summaryResult.selection_reasoning.truncateForDisplay(300)}
                |
                |**Total Time:** ${totalTime / 1000.0}s | **Options:** ${options.size}
            """.trimMargin(), ui = ui))
            overviewTask.complete()
            task.update()

            task.safeComplete("Generated and analyzed ${options.size} options in ${totalTime / 1000}s", log)
            resultFn(finalOutput)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("BrainstormingTask failed after ${duration}ms for problem: $problemStatement", e)
            // Write error to transcript
            transcriptStream?.write("\n## ❌ Error Occurred\n\n".toByteArray())
            transcriptStream?.write("**Error:** ${e.message}\n".toByteArray())
            transcriptStream?.write("**Type:** ${e.javaClass.simpleName}\n".toByteArray())
            transcriptStream?.flush()
            transcriptStream?.close()

            task.error(e)

            val errorOutput = buildString {
                appendLine("# Error in Brainstorming Task")
                appendLine()
                appendLine("**Problem:** $problemStatement")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Type:** ${e.javaClass.simpleName}")
            }

            task.safeComplete("Brainstorming failed: ${e.message}", log)
            resultFn(errorOutput)
        }
    }

    private fun buildBrainstormPrompt(
        problemStatement: String,
        targetCount: Int,
        categories: String,
        constraints: List<String>,
        includeCreative: Boolean,
        priorContext: String,
        inputFileContent: String = ""
    ): String {
        val constraintsSection = if (constraints.isNotEmpty()) {
            """
      |
      |## Constraints to Consider:
      |${constraints.joinToString("\n") { "- $it" }}
      """.trimMargin()
        } else {
            ""
        }

        val contextSection = if (priorContext.isNotBlank()) {
            """
      |
      |## Context from Previous Tasks:
      |$priorContext
      """.trimMargin()
        } else {
            ""
        }

        return """
You are a creative problem solver and brainstorming expert. Your task is to generate diverse, well-thought-out options for addressing a problem.

## Problem Statement:
$problemStatement

## Target:
Generate exactly $targetCount distinct options.

## Categories/Domains to Consider:
$categories
$constraintsSection
$contextSection

## Brainstorming Guidelines:
1. **Diversity**: Ensure options span different approaches and perspectives
2. **Clarity**: Each option should be clearly described and actionable
3. **Relevance**: All options must address the core problem
${if (includeCreative) "4. **Creativity**: Include unconventional and innovative approaches" else "4. **Practicality**: Focus on realistic, proven approaches"}
5. **Categorization**: Assign each option to a relevant category

## Output Format:
Generate a JSON object with an "options" array. Each option should have:
- title: A concise, descriptive name (5-10 words)
- description: A clear explanation of the option (2-4 sentences)
- category: The domain or approach category

Generate $targetCount diverse options now.
        """.trimIndent()
    }

    private fun buildAnalysisPrompt(
        option: BrainstormedOption,
        problemStatement: String,
        constraints: List<String>,
        analysisDepth: String
    ): String {
        val depthGuidance = when (analysisDepth.lowercase()) {
            "brief" -> "Provide concise analysis with 2-3 items per category."
            "detailed" -> "Provide comprehensive analysis with 5-7 items per category and detailed explanations."
            else -> "Provide moderate analysis with 3-5 items per category."
        }

        val constraintsSection = if (constraints.isNotEmpty()) {
            """
      |
      |## Constraints:
      |${constraints.joinToString("\n") { "- $it" }}
      """.trimMargin()
        } else {
            ""
        }

        return """
You are an analytical expert evaluating solution options. Analyze this option independently and objectively.

## Problem Statement:
$problemStatement

## Option to Analyze:
**Title:** ${option.title}
**Description:** ${option.description}
${if (option.category != null) "**Category:** ${option.category}" else ""}
$constraintsSection

## Analysis Instructions:
$depthGuidance

Evaluate the following aspects:

1. **Pros**: Advantages and benefits of this option
2. **Cons**: Disadvantages and limitations
3. **Feasibility**: How realistic is implementation? (Consider technical, resource, and organizational factors)
4. **Impact**: What outcomes and effects can be expected?
5. **Risks**: What could go wrong? What are the potential negative consequences?
6. **Requirements**: What resources, skills, or conditions are needed?

## Output Format:
Provide a JSON object with these fields:
- pros: Array of strings (advantages)
- cons: Array of strings (disadvantages)
- feasibility: String (assessment of how realistic this is)
- impact: String (expected outcomes and effects)
- risks: Array of strings (potential problems)
- requirements: Array of strings (what's needed to implement)

Analyze this option now.
        """.trimIndent()
    }

    private fun buildSummaryPrompt(
        problemStatement: String,
        options: List<BrainstormedOption>,
        analyses: Map<Int, OptionAnalysis>
    ): String {
        val optionsWithAnalysis = buildString {
            options.forEachIndexed { index, option ->
                val optionNumber = index + 1
                val analysis = analyses[optionNumber]
                appendLine("### Option $optionNumber: ${option.title}")
                appendLine("**Description:** ${option.description}")
                if (analysis != null) {
                    appendLine("**Pros:** ${analysis.pros.size} identified")
                    appendLine("**Cons:** ${analysis.cons.size} identified")
                    appendLine("**Feasibility:** ${analysis.feasibility.take(100)}...")
                    appendLine("**Key Risks:** ${analysis.risks.take(2).joinToString("; ")}")
                }
                appendLine()
            }
        }

        return """
You are a strategic advisor synthesizing brainstorming results. Review all options and their analyses to provide actionable recommendations.

## Problem Statement:
$problemStatement

## Options and Analyses:
$optionsWithAnalysis

## Your Task:
Evaluate all options and select the single best recommendation.

## Output Format:
Provide a JSON object with the following fields:
- `top_option_index`: The integer index (1-based) of the single best option.
- `selection_reasoning`: Why was this option chosen as the winner? (Compare against runners-up).
- `overview`: A brief executive summary of the session findings and general trends.
- `next_steps`: Concrete actions to take to implement the top recommendation.

Select the best option and summarize the findings now.
        """.trimIndent()
    }

    private fun buildDetailedResults(
        problemStatement: String,
        options: List<BrainstormedOption>,
        analyses: Map<Int, OptionAnalysis>,
        summary: String,
        totalTime: Long
    ): String {
        return buildString {
            appendLine("# Brainstorming Session - Detailed Results")
            appendLine()
            appendLine("**Problem Statement:** $problemStatement")
            appendLine()
            appendLine("**Session Duration:** ${totalTime / 1000}s")
            appendLine()
            appendLine("**Options Generated:** ${options.size}")
            appendLine()
            appendLine(
                "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }"
            )
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## All Options")
            appendLine()
            options.forEachIndexed { index, option ->
                val optionNumber = index + 1
                appendLine("### ${optionNumber}. ${option.title}")
                if (option.category != null) {
                    appendLine("**Category:** ${option.category}")
                }
                appendLine()
                appendLine(option.description)
                appendLine()
                val analysis = analyses[optionNumber]
                if (analysis != null) {
                    appendLine("#### Analysis")
                    appendLine()
                    appendLine("**Pros:**")
                    analysis.pros.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("**Cons:**")
                    analysis.cons.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("**Feasibility:** ${analysis.feasibility}")
                    appendLine()
                    appendLine("**Impact:** ${analysis.impact}")
                    appendLine()
                    appendLine("**Risks:**")
                    analysis.risks.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("**Requirements:**")
                    analysis.requirements.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("---")
                appendLine()
            }
            appendLine("## Summary & Recommendations")
            appendLine()
            appendLine(summary)
            appendLine()
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
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp",
            "h", "hpp", "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: File) = try {
        file.getDocumentReader().use { reader ->
            when (reader) {
                is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                else -> reader.getText()
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
        try {
            file.readText()
        } catch (e2: Exception) {
            "Error reading file: ${e2.message}"
        }
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(BrainstormingTask::class.java)
      @JvmStatic val Brainstorming = TaskType(
          name = "Brainstorming",
          category = "Reasoning",
          taskClass = BrainstormingTask::class.java,
          executionConfigClass = BrainstormingTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate and analyze multiple solution options",
          tooltipHtml = """
                        Systematically generates diverse options and analyzes each independently.
                        <ul>
                          <li>Generates multiple solution options for a given problem</li>
                          <li>Analyzes each option independently (pros, cons, feasibility, impact, risks)</li>
                          <li>Provides comparative summary with recommendations</li>
                          <li>Supports creative and conventional approaches</li>
                          <li>Configurable analysis depth and option count</li>
                          <li>Identifies hybrid approaches and synergies</li>
                          <li>Useful for decision making, strategic planning, and problem solving</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/CausalInferenceTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.nio.file.Path

class CausalInferenceTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: CausalInferenceTaskExecutionConfigData?
) : AbstractTask<CausalInferenceTask.CausalInferenceTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    protected val codeFiles = mutableMapOf<Path, String>()

    val maxOutputLength: Int = 20000

    class CausalInferenceTaskExecutionConfigData(
        @Description("The observed effect or outcome to explain")
        var observed_effect: String? = null,
        @Description("Potential causes to investigate")
        var potential_causes: List<String>? = null,
        @Description("Whether to build a causal graph")
        var build_causal_graph: Boolean = true,
        @Description("Whether to identify confounding factors")
        var identify_confounders: Boolean = true,
        @Description("Data sources for evidence (file patterns or paths)")
        var evidence_sources: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("Additional files for context")
        var related_files: List<String>? = null,
        @Description("A description of the task's purpose")
        task_description: String? = null,
        @Description("List of task IDs this task depends on")
        task_dependencies: List<String>? = null,
        @Description("The current state of the task")
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = CausalInference.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            // Validate observed_effect is not null or blank
            if (observed_effect.isNullOrBlank()) {
                return "observed_effect must not be null or blank"
            }
            // Validate potential_causes list if provided
            potential_causes?.let { causes ->
                if (causes.any { it.isBlank() }) {
                    return "potential_causes must not contain blank entries"
                }
            }
            // Call parent validation
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
CausalInference - Identify causal relationships and root causes
  ** Specify the observed effect or outcome to explain
  ** List potential causes to investigate
  ** Optionally build a causal graph showing relationships
  ** Optionally identify confounding factors
  ** Provide evidence sources (logs, metrics, code files)
  ** Optionally, list input files (supports glob patterns) to be examined
  ** Useful for:
     - Root cause analysis
     - Debugging complex issues
     - Understanding system behavior
     - Distinguishing correlation from causation
        """.trimIndent()
    }
    data class CausalAnalysisResult(
        val summary: String = "",
        val causes: List<CausalFactor> = emptyList(),
        val root_causes: List<String> = emptyList(),
        val causal_chain: String = "",
        val confounders: List<String>? = null,
        val recommendations: List<String> = emptyList()
    )
    data class CausalFactor(
        val name: String = "",
        val mechanism: String = "",
        val evidence: String = "",
        val strength: String = "", // strong/moderate/weak
        val confidence: String = ""
    )


    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
      val startTime = System.currentTimeMillis()


      val transcript = task.transcript()

      try {


        task.ui.pool.submit {
          try {
            val observedEffect = executionConfig?.observed_effect
            if (observedEffect.isNullOrBlank()) {
              throw IllegalArgumentException("observed_effect must not be null or blank")
            }

            log.info("Starting CausalInference task for effect: $observedEffect")
            transcript?.write("# Causal Inference Analysis\n\n**Observed Effect:** $observedEffect\n".toByteArray())

            task.ui
            val api = defaultSmart?.getChildClient(task) ?: throw IllegalStateException("No default chatter available")
            val fastApi = defaultFast?.getChildClient(task) ?: api

            val tabs = TabbedDisplay(task)
            val overviewTask = tabs.newTask("Overview")
            overviewTask.add("## Input Files\n\n${getInputFileCode()}".renderMarkdown())
            overviewTask.add("---\n\n## Causal Inference Analysis\n\n**Observed Effect:** $observedEffect".renderMarkdown())

            val overviewStatusBuffer = overviewTask.add("**Status:** 🔄 Gathering evidence...".renderMarkdown())

            // Evidence Gathering
            val evidenceTask = tabs.newTask("Evidence Sources")
            val evidenceStatusBuffer = evidenceTask.add("## Evidence Sources\n\n🔄 Loading evidence...".renderMarkdown())

            val evidenceContext = gatherEvidence()
            transcript?.write(
              """
                        <details>
                        <summary>Evidence Context</summary>

                        ${evidenceContext.take(maxOutputLength)}${if (evidenceContext.length > maxOutputLength) "\n... (truncated)" else ""}
                        </details>
                    """.trimIndent().toByteArray()
            )

            evidenceStatusBuffer?.setLength(0)
            evidenceStatusBuffer?.append("## Evidence Sources\n\n✅ Evidence gathered successfully\n\n**Sources processed:** ${executionConfig?.evidence_sources?.size ?: 0}".renderMarkdown())
            evidenceTask.expandable(
              "Evidence Context",
              "```\n${evidenceContext.take(maxOutputLength)}\n```".renderMarkdown()
            )

            // Analysis Preparation
            val priorContext = getPriorCode(agent.executionState)
            val messageContext = messages.joinToString("\n\n")
            val potentialCauses = executionConfig?.potential_causes ?: emptyList()
            val causesText = if (potentialCauses.isNotEmpty()) {
              "**Potential Causes to Investigate:**\n" + potentialCauses.joinToString("\n") { "- $it" }
            } else {
              "**Note:** No specific potential causes provided. Will identify causes from evidence."
            }

            overviewStatusBuffer?.setLength(0)
            overviewStatusBuffer?.append("$causesText\n\n**Status:** 🔄 Analyzing causal relationships...".renderMarkdown())
            overviewTask.update()

            val prompt =
              buildAnalysisPrompt(observedEffect, potentialCauses, evidenceContext, priorContext, messageContext)
            val analysisAgent = ParsedAgent(
              resultClass = CausalAnalysisResult::class.java,
              prompt = prompt,
              model = api,
              parsingChatter = fastApi
            )

            // Perform Analysis
            val analysisTask = tabs.newTask("Causal Analysis")
            analysisTask.add("## Causal Analysis\n\n🔄 Performing causal inference...".renderMarkdown())
            val analysisResult = analysisAgent.answer(listOf(prompt)).obj

            transcript?.write(
              """
                        ## Causal Analysis Results
                        <details>
                        <summary>Raw Analysis JSON</summary>

                        ```json
                        ${analysisResult.toJson()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
            )

            analysisTask.add(
              """
                        ## Causal Analysis Results
                        
                        ✅ Analysis complete
                        
                        ### Summary
                        ${analysisResult.summary}
                        
                        ### Identified Causes
                        ${analysisResult.causes.joinToString("\n") { "* **${it.name}** (${it.strength} strength)\n  * *Mechanism:* ${it.mechanism}" }}
                        
                        ### Root Causes
                        ${analysisResult.root_causes.joinToString("\n") { "* $it" }}
                        
                        ### Causal Chain
                        ${analysisResult.causal_chain}
                        
                        ${
                if (analysisResult.confounders != null) "### Confounding Factors\n${
                  analysisResult.confounders.joinToString(
                    "\n"
                  ) { "* $it" }
                }\n" else ""
              }
                        
                        ### Recommendations
                        ${analysisResult.recommendations.joinToString("\n") { "* $it" }}
                    """.trimMargin().renderMarkdown()
            )
            analysisTask.complete()

            overviewStatusBuffer?.setLength(0)
            overviewStatusBuffer?.append("$causesText\n\n**Status:** ✅ Analysis complete".renderMarkdown())
            overviewTask.update()

            // Causal Graph
            if (executionConfig?.build_causal_graph == true) {
              generateCausalGraph(tabs, analysisResult, api, transcript)
            }

            val duration = System.currentTimeMillis() - startTime
            val summary = "Causal inference analysis completed for effect: $observedEffect"
            log.info("$summary (duration: ${duration}ms)")

            task.complete(summary)
            resultFn(formatResultMessage(task, transcript, summary))

          } catch (e: Exception) {
            // Triple Log Rule
            task.error(e)
            log.error("CausalInference task failed", e)
            transcript?.write(
              """
                        ## Error
                        <details>
                        <summary>Stack Trace</summary>

                        ```
                        ${e.stackTraceToString()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
            )

            task.complete("Analysis failed: ${e.message}")
            resultFn("ERROR: Causal inference analysis failed - ${e.message}")
          } finally {
            transcript?.close()
          }
        }
      } catch (e: Exception) {
        log.error("Failed to submit CausalInference task to pool", e)
        task.error(e)
        transcript?.close()
      }
    }

  private fun generateCausalGraph(
    tabs: TabbedDisplay,
    analysisResult: CausalAnalysisResult,
    api: ChatInterface,
    transcript: java.io.OutputStream?
  ) {
    val graphTask = tabs.newTask("Causal Graph")
    val graphBuffer = graphTask.add("## Causal Graph\n\n🔄 Generating causal graph visualization...".renderMarkdown())

    val graphPrompt = """
Based on the following causal analysis, create a Mermaid diagram showing the causal relationships.

Analysis:
${analysisResult.toJson()}

Use the following format:
- Use `graph TD` for top-down flow
- Show direct causal links with `-->` 
- Show correlations with `-.->` (dotted lines)
- Label confounders clearly
- Use descriptive node labels

Generate the Mermaid diagram now:







        """.trimIndent()

    val chatAgent = ChatAgent(prompt = graphPrompt, model = api)
    val graphResult = chatAgent.answer(listOf(graphPrompt))
    val mermaidCode = extractMermaidCode(graphResult ?: "")
    graphTask.complete()

    transcript?.write("## Causal Graph\n\n```mermaid\n$mermaidCode\n```\n".toByteArray())

    graphBuffer?.setLength(0)
    if (mermaidCode.isNotEmpty()) {
      graphBuffer?.append(
        """
                ## Causal Graph
                
                ✅ Graph generated successfully
                
                ```mermaid
                $mermaidCode
                ```
            """.trimIndent().renderMarkdown()
      )
    } else {
      graphBuffer?.append(
        """
                ## Causal Graph
                
                ⚠️ Failed to generate graph visualization
                
                The analysis did not produce a valid Mermaid diagram.
            """.trimIndent().renderMarkdown()
      )
        }
    }

    private fun buildAnalysisPrompt(
        observedEffect: String,
        potentialCauses: List<String>,
        evidenceContext: String,
        priorContext: String,
        messageContext: String
    ): String {
        val causesSection = if (potentialCauses.isNotEmpty()) {
            """
## User Input and Context:
$messageContext
---
            |## Potential Causes to Investigate:
            |${potentialCauses.joinToString("\n") { "- $it" }}
            """.trimMargin()
        } else {
            "## Note: Identify potential causes from the evidence provided."
        }

        val confoundersSection = if (executionConfig?.identify_confounders == true) {
            """
            |
            |## Confounding Factors:
            |Identify any confounding variables that might create spurious correlations.
            |Explain how these confounders affect the causal interpretation.
            """.trimMargin()
        } else {
            ""
        }

        return """
You are an expert in causal inference and root cause analysis. Your task is to identify the true causal relationships behind an observed effect.

## Observed Effect:
$observedEffect

$causesSection

## Evidence and Context:
$evidenceContext

## Previous Task Results:
$priorContext

## Analysis Instructions:
1. **Distinguish Causation from Correlation**: Identify which relationships are truly causal vs merely correlated
2. **Apply Causal Reasoning**: Use principles like:
   - Temporal precedence (cause must precede effect)
   - Mechanism (explain HOW the cause produces the effect)
   - Counterfactual reasoning (what would happen without the cause?)
   - Elimination of alternative explanations
3. **Evaluate Each Potential Cause**: For each potential cause, assess:
   - Strength of causal link
   - Supporting evidence
   - Alternative explanations
   - Confidence level
4. **Identify Root Causes**: Distinguish between:
   - Root causes (fundamental sources)
   - Intermediate causes (mediating factors)
   - Proximate causes (immediate triggers)
5. **Consider Causal Chains**: Map out sequences of causation
6. **Assess Causal Strength**: Rate each causal relationship (strong/moderate/weak)
$confoundersSection

## Output Format:
Provide a structured analysis with:
1. **Summary**: Brief overview of key findings
2. **Causal Analysis**: For each identified cause:
   - Description of the causal mechanism
   - Evidence supporting causation
   - Strength of causal link
   - Confidence level
3. **Root Cause Identification**: The fundamental cause(s)
4. **Causal Chain**: How causes lead to the observed effect
5. **Confounders** (if requested): Variables that create spurious correlations
6. **Recommendations**: Actions to address root causes

Generate the causal analysis now:
        """.trimIndent()
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
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = codeFiles[file.toPath()] ?: file.readText()
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }.let { if (it.isBlank()) "No input files specified" else it }

  private fun formatResultMessage(task: SessionTask, transcript: java.io.OutputStream?, summary: String): String {
        return try {
            val (link, _) = task.createFile("analysis_results.md")
          """
                ## Analysis Complete
                * $summary
                * Detailed results saved to: `$link`
            """.trimIndent()
        } catch (e: Exception) {
            log.error("Failed to create result file", e)
            summary
        }
    }

    private fun gatherEvidence(): String {
        val evidenceSources = executionConfig?.evidence_sources ?: emptyList()
        val relatedFiles = executionConfig?.related_files ?: emptyList()
        val allSources = (evidenceSources + relatedFiles).distinct()

        if (allSources.isEmpty()) {
            return "No specific evidence sources provided."
        }
        val maxFileSize = 2000 // Reduced from 5000
        val maxTotalSize = 10000 // Limit total evidence context
        var totalSize = 0
        return allSources.flatMap { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            root.toFile().walkTopDown()
                .filter { file ->
                    file.isFile && matcher.matches(root.relativize(file.toPath()))
                }
                .map { file ->
                    val relativePath = root.relativize(file.toPath())
                    try {
                        if (totalSize >= maxTotalSize) {
                            return@map "### $relativePath\n(Skipped - evidence limit reached)"
                        }
                        val content = file.readText()
                        val truncated = content.take(maxFileSize)
                        totalSize += truncated.length
                        "### $relativePath\n```\n$truncated${if (content.length > maxFileSize) "\n... (truncated)" else ""}\n```"
                    } catch (e: Exception) {
                        log.warn("Error reading evidence file: $relativePath", e)
                        "### $relativePath\n(Error reading file: ${e.message})"
                    }
                }
                .toList()
        }.joinToString("\n\n")
    }

    private fun extractMermaidCode(response: String): String {
        val mermaidBlockRegex = "```mermaid\\s*([\\s\\S]*?)```".toRegex()
        val match = mermaidBlockRegex.find(response)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(CausalInferenceTask::class.java)
        @JvmStatic val CausalInference = TaskType(
          name = "CausalInference",
          category = "Reasoning",
          taskClass = CausalInferenceTask::class.java,
          executionConfigClass = CausalInferenceTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Identify causal relationships and root causes",
          tooltipHtml = """
                        Performs causal inference analysis to identify true causal relationships.
                        <ul>
                          <li>Distinguishes causation from correlation</li>
                          <li>Identifies root causes vs intermediate factors</li>
                          <li>Builds causal graphs showing relationships</li>
                          <li>Identifies confounding variables</li>
                          <li>Provides evidence-based causal reasoning</li>
                          <li>Useful for debugging and root cause analysis</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/ChainOfThoughtTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
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
        var problem_statement: String = "",
        @Description("Number of reasoning steps to generate (default: auto)")
        var reasoning_depth: Int = 10,
        @Description("Whether to validate each step before proceeding")
        var validate_steps: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var related_files: List<String> = emptyList(),
        task_dependencies: MutableList<String>? = null,
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
        val transcript = task.transcript()
      // Create tabbed display for organized output
      val tabs = TabbedDisplay(task)
      // Overview tab
      val overviewTask = tabs.newTask("Overview")
      val reasoningChain = mutableListOf<ReasoningStep>()
      val problemStatement = executionConfig?.problem_statement
      if (problemStatement?.isBlank() != false) {
        task.complete("CONFIGURATION ERROR: No problem statement specified")
        resultFn("CONFIGURATION ERROR: No problem statement specified")
        return@run
      }

      val maxSteps = executionConfig.reasoning_depth.coerceIn(1, 20)
      val validateSteps = executionConfig.validate_steps

      task.ui.pool.submit {
        try {
          val startTime = System.currentTimeMillis()
          log.info("Starting ChainOfThoughtTask. Problem: ${executionConfig?.problem_statement?.take(50)}...")

        val api = defaultSmart
          val inputFileContent = getInputFileCode()

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

        overviewTask.header("Chain of Thought Reasoning")
        overviewTask.add("<b>Problem Statement:</b> $problemStatement")
        overviewTask.add("<b>Max Steps:</b> $maxSteps")
        overviewTask.add("<b>Validate Steps:</b> ${if (validateSteps) "Yes" else "No"}")

        if (inputFileContent.isNotBlank()) {
          overviewTask.expandable("Input Files", inputFileContent.renderMarkdown())
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

        val contextFiles = getContextFiles()

        if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
            val contextTask = tabs.newTask("Context")
            contextTask.header("Context")
            if (priorContext.isNotBlank()) {
              contextTask.expandable("Previous Tasks", priorContext.renderMarkdown())
            }
            if (contextFiles.isNotBlank()) {
              contextTask.expandable("Related Files", contextFiles.renderMarkdown())
            }

            // Write context to transcript
          val contextLog = buildString {
            appendLine("\n\n# Context")
            if (priorContext.isNotBlank()) appendLine("\n<details><summary>Previous Tasks</summary>\n\n$priorContext\n</details>")
            if (contextFiles.isNotBlank()) appendLine("\n<details><summary>Related Files</summary>\n\n$contextFiles\n</details>")
          }
          transcript?.write(contextLog.toByteArray())
            task.update()
        }

        // Update overview with initialization complete
        overviewTask.add("✅ Initialization complete<br/><i>Starting reasoning steps...</i>")
        task.update()

        var currentQuestion = problemStatement
        var stepNumber = 1
        val stepTimes = mutableListOf<Long>()

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


                val step = generateReasoningStep(
                    stepTask,
                    currentQuestion!!,
                    reasoningChain,
                    priorContext,
                    contextFiles,
                    stepNumber,
                    api
                )

                if (validateSteps) {
                    stepTask.add("<i>Validating step...</i>")
                    task.update()

                    val validation = validateStep(stepTask, step, reasoningChain, api)

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

                if (lastStep.next_question.isNullOrBlank() || lastStep.confidence >= 0.9) {
                    // Check if we should continue - only stop if we have high confidence AND no next question
                    // OR if next_question explicitly indicates completion
                    val shouldStop = (lastStep.next_question.isNullOrBlank() && lastStep.confidence >= 0.9) ||
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

            summaryTask.header("Final Summary", level = 2)
          summaryTask.add(summary.renderMarkdown())
            summaryTask.append("<hr/>")
            summaryTask.add("<b>Status:</b> ✅ Complete", additionalClasses = "alert alert-success")

            // Write summary to transcript
            transcript?.write("\n\n# Final Summary\n\n$summary\n\n".toByteArray())
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
          log.info("ChainOfThoughtTask completed successfully in ${totalTime}ms")
            task.update()

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
          // Triple Log: Transcript
          transcript?.write("\n\n# Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
        } finally {
            transcript?.close()
          task.complete()
        }
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

      val stepMarkdown = """
            |### Step $stepNumber
            |
            |**Reasoning**: ${finalStep.reasoning}
            |
            |**Conclusion**: ${finalStep.conclusion}
            |
            |**Confidence**: ${String.format("%.1f%%", finalStep.confidence * 100)}
            |
            |${if (finalStep.next_question != null) "**Next Question**: ${finalStep.next_question}" else "**Status**: Reasoning complete"}
        """.trimMargin()
      task.add(stepMarkdown.renderMarkdown())


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
              append("**Conclusion**: ${step.conclusion}\n")
              append("**Confidence**: ${String.format("%.1f%%", step.confidence * 100)}\n")
              if (step.next_question != null) append("**Next Question**: ${step.next_question}\n")
              append("\n---\n\n")
            }
          append("\n")
          append("---\n")
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
        @JvmStatic val ChainOfThought = TaskType(
          name = "ChainOfThought",
          category = "Reasoning",
          taskClass = ChainOfThoughtTask::class.java,
          executionConfigClass = ChainOfThoughtTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Break down complex problems into explicit reasoning steps",
          tooltipHtml = """
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/ConstraintRelaxationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConstraintRelaxationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ConstraintRelaxationTaskExecutionConfigData?
) : AbstractTask<ConstraintRelaxationTask.ConstraintRelaxationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxOutputSize = 500

    class ConstraintRelaxationTaskExecutionConfigData(
        @Description("The problem description to solve")
        val problem: String? = null,
        @Description("Map of constraints to their priority weights (0.0-1.0, where 1.0 is critical)")
        val constraints: Map<String, Double>? = null,
        @Description("Relaxation strategy: 'progressive' (gradual), 'selective' (choose subset), 'hierarchical' (by levels)")
        val relaxation_strategy: String = "progressive",
        @Description("Order for reintroducing constraints: 'by_priority', 'by_difficulty', 'by_dependency'")
        val reintroduction_order: String = "by_priority",
        @Description("Whether to actively seek creative ways to satisfy constraints")
        val find_creative_satisfactions: Boolean = true,
        @Description("Maximum number of relaxation/reintroduction iterations")
        val max_iterations: Int = 5,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Additional files for context")
        val related_files: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ConstraintRelaxation.name,
        task_description = "Solve '$problem' through progressive constraint relaxation",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (problem.isNullOrBlank()) {
                return "Problem description cannot be null or blank"
            }
            if (constraints.isNullOrEmpty()) {
                return "Constraints map cannot be null or empty"
            }
            constraints.forEach { (constraint, priority) ->
                if (constraint.isBlank()) {
                    return "Constraint name cannot be blank"
                }
                if (priority < 0.0 || priority > 1.0) {
                    return "Constraint priority must be between 0.0 and 1.0, got $priority for constraint '$constraint'"
                }
            }
            if (max_iterations < 1 || max_iterations > 10) {
                return "Max iterations must be between 1 and 10, got $max_iterations"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
ConstraintRelaxation - Solve over-constrained problems through progressive constraint relaxation
  ** Specify the problem to solve
  ** Define constraints with priority weights (0.0-1.0, where 1.0 is critical)
  ** Choose relaxation strategy:
     - 'progressive': Gradually relax constraints from lowest to highest priority
     - 'selective': Intelligently select which constraints to relax
     - 'hierarchical': Relax constraints in priority-based levels
  ** Choose reintroduction order:
     - 'by_priority': Reintroduce highest priority constraints first
     - 'by_difficulty': Reintroduce easiest constraints first
     - 'by_dependency': Reintroduce based on constraint dependencies
  ** Enable creative satisfaction finding to discover novel solutions
  ** Produces a solution that progressively satisfies constraints
  ** Shows evolution of solution as constraints are reintroduced
  ** Optionally, list input files (supports glob patterns) to be examined for context
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
        log.info("Starting ConstraintRelaxationTask for problem: '${executionConfig?.problem}'")

        val problem = executionConfig?.problem
        if (problem.isNullOrBlank()) {
            log.error("No problem description specified")
            task.safeComplete("CONFIGURATION ERROR: No problem description specified", log)
            resultFn("CONFIGURATION ERROR: No problem description specified")
            return
        }

        val constraints = executionConfig.constraints
        if (constraints.isNullOrEmpty()) {
            log.error("No constraints specified")
            task.safeComplete("CONFIGURATION ERROR: No constraints specified", log)
            resultFn("CONFIGURATION ERROR: No constraints specified")
            return
        }

        val relaxationStrategy = executionConfig.relaxation_strategy
        val reintroductionOrder = executionConfig.reintroduction_order
        val findCreativeSatisfactions = executionConfig.find_creative_satisfactions
        val maxIterations = executionConfig.max_iterations.coerceIn(1, 10)

        log.info(
            """
      |Configuration:
      |  Strategy: $relaxationStrategy
      |  Reintroduction Order: $reintroductionOrder
      |  Creative Satisfactions: $findCreativeSatisfactions
      |  Max Iterations: $maxIterations
      |  Constraints: ${constraints.size}
      """.trimMargin()
        )

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)
        val transcript = task.transcript("constraint_relaxation")
        val overviewTask = task.newTask()
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Constraint Relaxation: Progressive Problem Solving")
            appendLine()
            appendLine("**Problem:** $problem")
            appendLine()
            appendLine("**Relaxation Strategy:** $relaxationStrategy")
            appendLine()
            appendLine("**Reintroduction Order:** $reintroductionOrder")
            appendLine()
            appendLine("**Creative Satisfactions:** ${if (findCreativeSatisfactions) "Enabled" else "Disabled"}")
            appendLine()
            appendLine("**Max Iterations:** $maxIterations")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Constraints (${constraints.size})")
            appendLine()
            constraints.entries.sortedByDescending { it.value }.forEach { (constraint, priority) ->
                val priorityLabel = when {
                    priority >= 0.9 -> "🔴 Critical"
                    priority >= 0.7 -> "🟠 High"
                    priority >= 0.5 -> "🟡 Medium"
                    else -> "🟢 Low"
                }
                appendLine("- **$constraint** - $priorityLabel (${String.format("%.1f", priority)})")
            }
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing constraint relaxation process...*")
        }
        transcript?.write(overviewContent.toByteArray())
        overviewTask.add(overviewContent.renderMarkdown())
        task.update()

        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            log.debug("Found prior context: ${priorContext.length} characters")
            val contextTask = task.newTask()
            tabs["Context"] = contextTask.placeholder
            contextTask.add(
                buildString {
                    appendLine("# Context from Previous Tasks")
                    appendLine()
                    appendLine(priorContext.truncateForDisplay())
                }.renderMarkdown()
            )
            transcript?.write("\n\n# Context from Previous Tasks\n\n${priorContext.truncateForDisplay()}\n".toByteArray())
            task.update()
            contextTask.complete()
        }
        val inputFileContent = getInputFileCode()

        overviewTask.add(
            buildString {
                appendLine()
                appendLine("✅ Initialization complete")
                appendLine()
                appendLine("*Analyzing constraint structure...*")
            }.renderMarkdown()
        )
        transcript?.write("\n\n✅ Initialization complete\n\n*Analyzing constraint structure...*\n".toByteArray())
        task.update()

        val solutionBuilder = StringBuilder()
        solutionBuilder.append("# Constraint Relaxation Solution\n\n")
        solutionBuilder.append("**Problem:** $problem\n\n")
        if (inputFileContent.isNotBlank()) {
            solutionBuilder.append("## Input Files Context\n\n")
            solutionBuilder.append(inputFileContent)
            solutionBuilder.append("\n\n")
        }

        try {
            // Step 1: Analyze and order constraints
            log.info("Analyzing constraint structure")
            val analysisTask = task.newTask()
            tabs["Constraint Analysis"] = analysisTask.placeholder

            analysisTask.add(
                buildString {
                    appendLine("# Constraint Analysis")
                    appendLine()
                    appendLine("**Status:** Analyzing constraint structure and dependencies...")
                }.renderMarkdown()
            )
            task.update()

            val orderedConstraints = orderConstraints(constraints, reintroductionOrder)
            val relaxedConstraints = selectConstraintsToRelax(orderedConstraints, relaxationStrategy)
            buildString {
                appendLine()
                appendLine("## Constraint Ordering")
                appendLine()
                appendLine("Constraints will be reintroduced in the following order:")
                appendLine()
                orderedConstraints.forEachIndexed { index, (constraint, priority) ->
                    val status = if (relaxedConstraints.contains(constraint)) "🔓 Initially Relaxed" else "🔒 Active"
                    appendLine("${index + 1}. **$constraint** ($status, priority: ${String.format("%.2f", priority)})")
                }
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Status:** ✅ Analysis complete")
            }

            task.update()
            analysisTask.complete()

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Constraint analysis complete")
                    appendLine()
                    appendLine("*Solving relaxed problem...*")
                }.renderMarkdown()
            )
            task.update()

            // Step 2: Solve the fully relaxed problem
            log.info("Solving relaxed problem with ${relaxedConstraints.size} constraints relaxed")
            val relaxedSolutionTask = task.newTask()
            tabs["Relaxed Solution"] = relaxedSolutionTask.placeholder

            relaxedSolutionTask.add(
                buildString {
                    appendLine("# Initial Relaxed Solution")
                    appendLine()
                    appendLine("**Relaxed Constraints:** ${relaxedConstraints.size}")
                    appendLine()
                    relaxedConstraints.forEach { constraint ->
                        appendLine("- $constraint")
                    }
                    appendLine()
                    appendLine("**Status:** Generating solution without relaxed constraints...")
                }.renderMarkdown()
            )
            transcript?.write("\n\n# Initial Relaxed Solution\n\n**Relaxed Constraints:** ${relaxedConstraints.size}\n\n".toByteArray())
            task.update()

            val activeConstraints = constraints.filterKeys { !relaxedConstraints.contains(it) }
            val relaxedSolution = solveWithConstraints(
                problem,
                activeConstraints,
                priorContext,
                api,
                findCreativeSatisfactions
            )

            val relaxedSolutionContent =
                buildString {
                    appendLine()
                    appendLine("## Solution")
                    appendLine()
                    appendLine(relaxedSolution)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Relaxed solution generated")
                }

            transcript?.write(relaxedSolutionContent.toByteArray())
            relaxedSolutionTask.add(relaxedSolutionContent.renderMarkdown())
            task.update()
            relaxedSolutionTask.complete()

            solutionBuilder.append("## Initial Relaxed Solution\n\n")
            solutionBuilder.append("**Relaxed:** ${relaxedConstraints.joinToString(", ")}\n\n")
            solutionBuilder.append(relaxedSolution.truncateForDisplay(maxOutputSize))
            solutionBuilder.append("\n\n")

            var currentSolution = relaxedSolution
            val reintroductionSteps = mutableListOf<ReintroductionStep>()

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Relaxed solution generated")
                    appendLine()
                    appendLine("*Beginning progressive constraint reintroduction...*")
                }.renderMarkdown()
            )
            task.update()

            // Step 3: Progressively reintroduce constraints
            val constraintsToReintroduce = orderedConstraints.filter { (constraint, _) ->
                relaxedConstraints.contains(constraint)
            }

            constraintsToReintroduce.forEachIndexed { index, (constraint, priority) ->
                if (index >= maxIterations) {
                    log.info("Reached max iterations ($maxIterations), stopping reintroduction")
                    return@forEachIndexed
                }

                val iterationStartTime = System.currentTimeMillis()
                log.info("Reintroducing constraint ${index + 1}/${constraintsToReintroduce.size}: $constraint")

                val iterationTask = task.newTask()
                tabs["Iteration ${index + 1}"] = iterationTask.placeholder

                iterationTask.add(
                    buildString {
                        appendLine("# Iteration ${index + 1}: Reintroducing Constraint")
                        appendLine()
                        appendLine("**Constraint:** $constraint")
                        appendLine()
                        appendLine("**Priority:** ${String.format("%.2f", priority)}")
                        appendLine()
                        appendLine("**Status:** Adapting solution to satisfy this constraint...")
                    }.renderMarkdown()
                )
                transcript?.write("\n\n# Iteration ${index + 1}: Reintroducing Constraint\n\n**Constraint:** $constraint\n\n".toByteArray())
                task.update()

                val newActiveConstraints = activeConstraints.toMutableMap()
                newActiveConstraints[constraint] = priority

                val adaptedSolution = adaptSolutionForConstraint(
                    problem,
                    currentSolution,
                    constraint,
                    priority,
                    newActiveConstraints,
                    priorContext,
                    api,
                    findCreativeSatisfactions
                )

                val iterationTime = System.currentTimeMillis() - iterationStartTime

                val iterationContent =
                    buildString {
                        appendLine()
                        appendLine("## Adapted Solution")
                        appendLine()
                        appendLine(adaptedSolution)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete (${iterationTime / 1000.0}s)")
                    }

                transcript?.write(iterationContent.toByteArray())
                iterationTask.add(iterationContent.renderMarkdown())
                task.update()
                iterationTask.complete()

                reintroductionSteps.add(
                    ReintroductionStep(
                        constraint = constraint,
                        priority = priority,
                        solution = adaptedSolution,
                        iterationTime = iterationTime
                    )
                )

                currentSolution = adaptedSolution

                overviewTask.add(
                    buildString {
                        appendLine()
                        appendLine("✅ Iteration ${index + 1} complete: $constraint (${iterationTime / 1000.0}s)")
                    }.renderMarkdown()
                )
                task.update()
            }

            // Step 4: Generate final synthesis
            log.info("Generating final synthesis")
            val synthesisTask = task.newTask()
            tabs["Final Synthesis"] = synthesisTask.placeholder

            synthesisTask.add(
                buildString {
                    appendLine("# Final Synthesis")
                    appendLine()
                    appendLine("**Status:** Generating comprehensive analysis...")
                }.renderMarkdown()
            )
            task.update()

            val synthesis = generateSynthesis(
                problem,
                constraints,
                relaxedConstraints,
                reintroductionSteps,
                currentSolution,
                api
            )

            val synthesisContent =
                buildString {
                    appendLine()
                    appendLine(synthesis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }

            transcript?.write("\n\n# Final Synthesis\n\n${synthesis}\n".toByteArray())
            synthesisTask.add(synthesisContent.renderMarkdown())
            task.update()
            synthesisTask.complete()

            solutionBuilder.append("## Progressive Reintroduction\n\n")
            solutionBuilder.append("**Iterations:** ${reintroductionSteps.size}\n\n")
            reintroductionSteps.forEachIndexed { index, step ->
                solutionBuilder.append("${index + 1}. **${step.constraint}** (${step.iterationTime / 1000.0}s)\n")
            }
            solutionBuilder.append("\n## Final Synthesis\n\n")
            solutionBuilder.append(synthesis)

            val totalTime = System.currentTimeMillis() - startTime
            val avgIterationTime = if (reintroductionSteps.isNotEmpty()) {
                reintroductionSteps.map { it.iterationTime }.average()
            } else 0.0

            log.info(
                """
        |ConstraintRelaxationTask completed:
        |  Total Time: ${totalTime}ms
        |  Iterations: ${reintroductionSteps.size}
        |  Avg Iteration Time: ${avgIterationTime}ms
        |  Output Size: ${solutionBuilder.length} chars
        """.trimMargin()
            )

            val completionContent = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Constraint Relaxation Complete")
                appendLine()
                appendLine("**Total Time:** ${totalTime / 1000.0}s")
                appendLine()
                appendLine("**Iterations:** ${reintroductionSteps.size}")
                appendLine()
                appendLine("**Average Iteration Time:** ${avgIterationTime / 1000.0}s")
                appendLine()
                appendLine("**Constraints Satisfied:** ${constraints.size - relaxedConstraints.size + reintroductionSteps.size}/${constraints.size}")
                appendLine()
                appendLine(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
            }
            overviewTask.add(
                completionContent.renderMarkdown()
            )

            transcript?.write(completionContent.toByteArray())
            transcript?.close()
            task.update()
            overviewTask.complete()

            val finalResult = solutionBuilder.toString()
            // Write detailed output to file
            val detailedLink = task.saveFile("constraint_relaxation_detailed.md", finalResult.toByteArray())

            // Generate summary message
            val summaryMessage = buildString {
                appendLine("✅ Constraint Relaxation Complete")
                appendLine()
                appendLine("**Total Time:** ${totalTime / 1000.0}s")
                appendLine("**Iterations:** ${reintroductionSteps.size}")
                appendLine("**Constraints Satisfied:** ${constraints.size - relaxedConstraints.size + reintroductionSteps.size}/${constraints.size}")
                appendLine()
                appendLine("📄 [View Detailed Results]($detailedLink)")
            }

            task.safeComplete(
                summaryMessage,
                log
            )
            resultFn(summaryMessage)

        } catch (e: Exception) {
            log.error("Error during constraint relaxation", e)
            task.error(e)
            transcript?.let {
                it.close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
                }.renderMarkdown()
            )
            task.update()
            overviewTask.complete()

            val errorOutput = buildString {
                appendLine("# Error in Constraint Relaxation")
                appendLine()
                appendLine("**Problem:** $problem")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (solutionBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(solutionBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }


    private fun orderConstraints(
        constraints: Map<String, Double>,
        order: String
    ): List<Pair<String, Double>> {
        return when (order) {
            "by_priority" -> constraints.entries.sortedByDescending { it.value }.map { it.key to it.value }
            "by_difficulty" -> {
                // For now, treat lower priority as potentially easier to satisfy
                constraints.entries.sortedBy { it.value }.map { it.key to it.value }
            }

            "by_dependency" -> {
                // Simple heuristic: reintroduce in priority order (could be enhanced with dependency analysis)
                constraints.entries.sortedByDescending { it.value }.map { it.key to it.value }
            }

            else -> constraints.entries.sortedByDescending { it.value }.map { it.key to it.value }
        }
    }

    private fun selectConstraintsToRelax(
        orderedConstraints: List<Pair<String, Double>>,
        strategy: String
    ): Set<String> {
        return when (strategy) {
            "progressive" -> {
                // Relax bottom 50% of constraints by priority
                val relaxCount = (orderedConstraints.size * 0.5).toInt().coerceAtLeast(1)
                orderedConstraints.takeLast(relaxCount).map { it.first }.toSet()
            }

            "selective" -> {
                // Relax constraints with priority < 0.7
                orderedConstraints.filter { it.second < 0.7 }.map { it.first }.toSet()
            }

            "hierarchical" -> {
                // Relax all but the top priority tier (>= 0.9)
                orderedConstraints.filter { it.second < 0.9 }.map { it.first }.toSet()
            }

            else -> {
                // Default to progressive
                val relaxCount = (orderedConstraints.size * 0.5).toInt().coerceAtLeast(1)
                orderedConstraints.takeLast(relaxCount).map { it.first }.toSet()
            }
        }
    }

    private fun solveWithConstraints(
        problem: String,
        constraints: Map<String, Double>,
        priorContext: String,
        api: ChatInterface,
        findCreative: Boolean
    ): String {
        val prompt = buildString {
            appendLine("You are an expert problem solver specializing in constraint-based design.")
            appendLine()
            appendLine("## Problem:")
            appendLine(problem)
            appendLine()
            appendLine("## Active Constraints:")
            constraints.entries.sortedByDescending { it.value }.forEach { (constraint, priority) ->
                appendLine("- $constraint (priority: ${String.format("%.2f", priority)})")
            }
            appendLine()
            if (priorContext.isNotBlank()) {
                appendLine("## Context from Previous Tasks:")
                appendLine(priorContext.truncateForDisplay(5000))
                appendLine()
            }
            appendLine("## Instructions:")
            appendLine("Generate a solution that satisfies the active constraints listed above.")
            if (findCreative) {
                appendLine("Be creative and consider unconventional approaches that might satisfy constraints in novel ways.")
            }
            appendLine()
            appendLine("Provide:")
            appendLine("1. A clear solution description")
            appendLine("2. How each constraint is satisfied")
            appendLine("3. Any trade-offs or assumptions made")
            appendLine()
            appendLine("Generate the solution now:")
        }

        val agent = ChatAgent(
            prompt = prompt.toString(),
            model = api,
            temperature = if (findCreative) 0.8 else 0.5
        )

        return agent.answer(listOf(""))
    }

    private fun adaptSolutionForConstraint(
        problem: String,
        currentSolution: String,
        newConstraint: String,
        priority: Double,
        allActiveConstraints: Map<String, Double>,
        priorContext: String,
        api: ChatInterface,
        findCreative: Boolean
    ): String {
        val prompt = buildString {
            appendLine("You are an expert problem solver specializing in constraint-based design.")
            appendLine()
            appendLine("## Problem:")
            appendLine(problem)
            appendLine()
            appendLine("## Current Solution:")
            appendLine(currentSolution.truncateForDisplay(8000))
            appendLine()
            appendLine("## New Constraint to Satisfy:")
            appendLine("**$newConstraint** (priority: ${String.format("%.2f", priority)})")
            appendLine()
            appendLine("## All Active Constraints:")
            allActiveConstraints.entries.sortedByDescending { it.value }.forEach { (constraint, p) ->
                val marker = if (constraint == newConstraint) "🆕" else "✓"
                appendLine("$marker $constraint (priority: ${String.format("%.2f", p)})")
            }
            appendLine()
            if (priorContext.isNotBlank()) {
                appendLine("## Context from Previous Tasks:")
                appendLine(priorContext.truncateForDisplay(3000))
                appendLine()
            }
            appendLine("## Instructions:")
            appendLine("Adapt the current solution to satisfy the new constraint: **$newConstraint**")
            appendLine("You must maintain satisfaction of all previously satisfied constraints.")
            if (findCreative) {
                appendLine("Be creative! Consider:")
                appendLine("- Reframing the constraint")
                appendLine("- Finding synergies between constraints")
                appendLine("- Novel architectural approaches")
                appendLine("- Trade-offs that satisfy multiple constraints simultaneously")
            }
            appendLine()
            appendLine("Provide:")
            appendLine("1. The adapted solution")
            appendLine("2. How the new constraint is satisfied")
            appendLine("3. Confirmation that previous constraints remain satisfied")
            appendLine("4. Any creative insights or trade-offs")
            appendLine()
            appendLine("Generate the adapted solution now:")
        }

        val agent = ChatAgent(
            prompt = prompt.toString(),
            model = api,
            temperature = if (findCreative) 0.8 else 0.5
        )

        return agent.answer(listOf(""))
    }

    private fun generateSynthesis(
        problem: String,
        allConstraints: Map<String, Double>,
        initiallyRelaxed: Set<String>,
        reintroductionSteps: List<ReintroductionStep>,
        finalSolution: String,
        api: ChatInterface
    ): String {
        val prompt = buildString {
            appendLine("You are an expert problem solver providing a final synthesis of a constraint relaxation process.")
            appendLine()
            appendLine("## Problem:")
            appendLine(problem)
            appendLine()
            appendLine("## All Constraints:")
            allConstraints.entries.sortedByDescending { it.value }.forEach { (constraint, priority) ->
                appendLine("- $constraint (priority: ${String.format("%.2f", priority)})")
            }
            appendLine()
            appendLine("## Initially Relaxed Constraints:")
            initiallyRelaxed.forEach { constraint ->
                appendLine("- $constraint")
            }
            appendLine()
            appendLine("## Reintroduction Process:")
            reintroductionSteps.forEachIndexed { index, step ->
                appendLine(
                    "${index + 1}. ${step.constraint} (priority: ${
                        String.format(
                            "%.2f",
                            step.priority
                        )
                    }, time: ${step.iterationTime / 1000.0}s)"
                )
            }
            appendLine()
            appendLine("## Final Solution:")
            appendLine(finalSolution.truncateForDisplay(8000))
            appendLine()
            appendLine("## Instructions:")
            appendLine("Provide a comprehensive synthesis that includes:")
            appendLine("1. **Solution Overview**: High-level summary of the final solution")
            appendLine("2. **Constraint Satisfaction Analysis**: How each constraint is satisfied")
            appendLine("3. **Key Insights**: Important discoveries from the progressive relaxation process")
            appendLine("4. **Trade-offs**: Any compromises or trade-offs made")
            appendLine("5. **Creative Elements**: Novel or creative approaches used")
            appendLine("6. **Recommendations**: Suggestions for implementation or further refinement")
            appendLine()
            appendLine("Generate the synthesis now:")
        }

        val agent = ChatAgent(
            prompt = prompt.toString(),
            model = api,
            temperature = 0.6
        )

        return agent.answer(listOf(""))
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


    private data class ReintroductionStep(
        val constraint: String = "",
        val priority: Double = 0.0,
        val solution: String = "",
        val iterationTime: Long = 0L
    )

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ConstraintRelaxationTask::class.java)
        @JvmStatic val ConstraintRelaxation = TaskType(
          name = "ConstraintRelaxation",
          category = "Reasoning",
          taskClass = ConstraintRelaxationTask::class.java,
          executionConfigClass = ConstraintRelaxationTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Solve over-constrained problems through progressive constraint relaxation",
          tooltipHtml = """
                        Solves complex problems by temporarily relaxing constraints and progressively reintroducing them.
                        <ul>
                          <li>Identifies which constraints to initially relax based on priority</li>
                          <li>Solves simplified problem without relaxed constraints</li>
                          <li>Progressively reintroduces constraints in configurable order</li>
                          <li>Adapts solution at each step to satisfy new constraints</li>
                          <li>Finds creative ways to satisfy multiple constraints simultaneously</li>
                          <li>Supports multiple relaxation strategies (progressive, selective, hierarchical)</li>
                          <li>Configurable reintroduction order (by priority, difficulty, or dependency)</li>
                          <li>Useful for over-constrained problems, algorithm design, and architecture under constraints</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/ConstraintSatisfactionTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConstraintSatisfactionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ConstraintSatisfactionTaskExecutionConfigData?
) : AbstractTask<ConstraintSatisfactionTask.ConstraintSatisfactionTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class ConstraintSatisfactionTaskExecutionConfigData(
      @Description("The problem requiring constraint satisfaction. Be specific about the goals and constraints.")
      var problem_description: String? = null,
      @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task.")
      var input_files: List<String>? = null,
      @Description("Hard constraints that must be satisfied (cannot be violated).")
      var hard_constraints: List<String>? = emptyList(),
      @Description("Soft constraints to optimize with their relative weights (0.0-1.0).")
      var soft_constraints: Map<String, Double>? = emptyMap(),
      @Description("Search strategy: 'backtracking' (systematic), 'forward' (greedy), 'local' (hill-climbing).")
      var search_strategy: String = "backtracking",
      @Description("Maximum search iterations before returning best solution found.")
      var max_iterations: Int = 100,
      @Description("Additional files for context.")
      var related_files: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ConstraintSatisfaction.name,
        task_description = problem_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {

        override fun validate(): String? {
            // Validate problem description
            if (problem_description.isNullOrBlank()) {
                return "problem_description cannot be null or blank"
            }

            // Validate search strategy
            val validStrategies = setOf("backtracking", "forward", "local")
            if (search_strategy !in validStrategies) {
                return "search_strategy must be one of: ${validStrategies.joinToString(", ")}"
            }

            // Validate max iterations
            if (max_iterations <= 0) {
                return "max_iterations must be greater than 0"
            }

            // Validate soft constraint weights
            soft_constraints?.forEach { (constraint, weight) ->
                if (weight < 0.0 || weight > 1.0) {
                    return "soft constraint '$constraint' has invalid weight $weight (must be between 0.0 and 1.0)"
                }
            }
            // Validate input files if provided
            input_files?.forEach { pattern ->
                if (pattern.isBlank()) {
                    return "input_files patterns cannot be blank"
                }
            }

            // Call parent validation
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
        ### ConstraintSatisfaction
        Solves complex problems with multiple competing constraints using various search strategies.
        - **Use when**: You need to balance hard requirements with weighted soft preferences (e.g., architecture, scheduling).
        - **Inputs**: Problem description, hard/soft constraints, and optional file context.
        - **Strategies**: 
            - `backtracking`: Systematic search for guaranteed satisfaction.
            - `forward`: Greedy approach for speed.
            - `local`: Hill-climbing for optimization in large search spaces.
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {

      val transcript = task.transcript()
      try {
        // Validate configuration before execution
        executionConfig?.validate()?.let { error ->
          val msg = "Configuration validation failed: $error"
          log.error(msg)
          task.error(Exception(msg))
          resultFn("CONFIGURATION ERROR: $error")
          return
        }


        val startTime = System.currentTimeMillis()
        val problemDescription = executionConfig?.problem_description ?: ""
        val hardConstraints = executionConfig?.hard_constraints ?: emptyList()
        val softConstraints = executionConfig?.soft_constraints ?: emptyMap()
        val searchStrategy = executionConfig?.search_strategy ?: "backtracking"
        val maxIterations = executionConfig?.max_iterations ?: 100

        log.info("Starting Constraint Satisfaction Task for problem: ${problemDescription.take(50)}...")

        transcript?.let { stream ->
          writeTranscriptHeader(
            stream,
            problemDescription,
            hardConstraints,
            softConstraints,
            searchStrategy,
            maxIterations
          )
        }

        task.ui.pool.submit {
          try {
            val tabbedDisplay = TabbedDisplay(task)
            tabbedDisplay.newTask("Problem Overview").add(
              """
                        |## Constraint Satisfaction Problem
                        |
                        |**Problem**: ${problemDescription.truncateForDisplay()}
                        |
                        |**Hard Constraints** (${hardConstraints.size}):
                        |${hardConstraints.joinToString("\n") { "- $it" }}
                        |
                        |**Soft Constraints** (${softConstraints.size}):
                        |${softConstraints.entries.joinToString("\n") { "- ${it.key} (weight: ${it.value})" }}
                        |
                        |**Strategy**: $searchStrategy (max iterations: $maxIterations)
                        """.trimMargin().renderMarkdown()
            )

            // Step 2: Gather Context
            val contextTask = tabbedDisplay.newTask("Context")
            contextTask.add("### Gathering context from previous tasks...".renderMarkdown())

            val priorCode = getPriorCode(agent.executionState)
            val inputFileContent =
              super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)

            val prompt = buildPrompt(
              problemDescription, hardConstraints, softConstraints,
              searchStrategy, maxIterations, priorCode, inputFileContent
            )

            transcript?.write(
              """
                        |<details>
                        |<summary>Generated Prompt</summary>
                        |
                        |```markdown
                        |$prompt
                        |```
                        |</details>
                        |
                    """.trimMargin().toByteArray()
            )

            contextTask.add(
              """
                        |### Context Gathered
                        |✅ Previous task results collected
                        |✅ Prompt constructed
                        """.trimMargin().renderMarkdown()
            )

            // Step 3: Generate Solution
            val solutionGenerationTask = tabbedDisplay.newTask("Solution Generation")
            solutionGenerationTask.add("### Generating solution...".renderMarkdown())

            val api = defaultSmart ?: throw IllegalStateException("No smart model configured")
            val chatAgent = ChatAgent(prompt = prompt, model = api)
            val answer = chatAgent.answer(listOf(""))

            transcript?.write(
              """
                        |<details>
                        |<summary>Raw LLM Response</summary>
                        |
                        |```markdown
                        |$answer
                        |```
                        |</details>
                        |
                    """.trimMargin().toByteArray()
            )

            solutionGenerationTask.add("### Solution Generated ✅".renderMarkdown())

            // Step 4: Display Solution
            tabbedDisplay.newTask("Final Solution").add(
              """
                        |## Solution
                        |
                        |${answer.truncateForDisplay()}
                        """.trimMargin().renderMarkdown()
            )

            val duration = System.currentTimeMillis() - startTime
            log.info("Constraint Satisfaction Task completed in ${duration}ms")
            transcript?.write("\n\n---\n**Completed in ${duration}ms**\n".toByteArray())

            if (orchestrationConfig.autoFix) {
              finalizeTask(task, answer, resultFn)
            } else {
              val footer = acceptButtonFooter(task.ui) {
                finalizeTask(task, answer, resultFn)
              }
              task.add(footer.renderMarkdown())
            }
          } catch (e: Exception) {
            handleError(e, task, transcript, resultFn)
          } finally {
            transcript?.close()
          }
        }
      } catch (e: Exception) {
        handleError(e, task, transcript, resultFn)
        transcript?.close()
      }
    }

  private fun finalizeTask(task: SessionTask, answer: String, resultFn: (String) -> Unit) {
    try {
      val (link, _) = task.createFile("constraint_solution_transcript.md")
      val summaryMessage = """
                |Constraint satisfaction solution finalized.
                |View detailed transcript: [markdown]($link) | [html](${link.removeSuffix(".md")}.html) | [pdf](${
        link.removeSuffix(
          ".md"
        )
      }.pdf)
            """.trimMargin().renderMarkdown()
      task.complete(summaryMessage)
      resultFn(answer)
    } catch (e: Exception) {
      log.error("Error finalizing task", e)
      task.error(e)
      resultFn("ERROR: Finalization failed - ${e.message}")
    }
  }

  private fun handleError(e: Exception, task: SessionTask, transcript: FileOutputStream?, resultFn: (String) -> Unit) {
    log.error("Error in Constraint Satisfaction Task: ${e.message}", e)
    task.error(e)
    transcript?.write(
      """
            |
            |## ❌ Error
            |<details>
            |<summary>Stack Trace</summary>
            |
            |```
            |${e.stackTraceToString()}
            |```
            |</details>
        """.trimMargin().toByteArray()
    )
    resultFn("ERROR: Failed to generate constraint satisfaction solution - ${e.message}")
    }

    private fun writeTranscriptHeader(
        stream: FileOutputStream,
        problemDescription: String,
        hardConstraints: List<String>,
        softConstraints: Map<String, Double>,
        searchStrategy: String,
        maxIterations: Int
    ) {
        try {
            val header = buildString {
                appendLine("# Constraint Satisfaction Task Transcript")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("**Problem:** $problemDescription")
                appendLine("**Hard Constraints:** ${hardConstraints.size}")
                appendLine("**Soft Constraints:** ${softConstraints.size}")
                appendLine("**Search Strategy:** $searchStrategy")
                appendLine("**Max Iterations:** $maxIterations")
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


    private fun buildPrompt(
        problemDescription: String,
        hardConstraints: List<String>,
        softConstraints: Map<String, Double>,
        searchStrategy: String,
        maxIterations: Int,
        priorCode: String,
        inputFileContent: String
    ): String {
        return """
 You are an expert problem solver specializing in constraint satisfaction problems (CSP).

 ## Problem Description:
 $problemDescription
## Input Files Context:
${if (inputFileContent.isNotBlank()) inputFileContent else "No input files provided"}


 ## Hard Constraints (MUST be satisfied):
 ${hardConstraints.mapIndexed { i, c -> "${i + 1}. $c" }.joinToString("\n")}

## Soft Constraints (optimize with given weights):
${
            softConstraints.entries.mapIndexed { i, (constraint, weight) ->
                "${i + 1}. $constraint (weight: $weight)"
            }.joinToString("\n")
        }

## Search Strategy:
$searchStrategy

## Maximum Iterations:
$maxIterations

## Previous Task Results:
$priorCode

## Instructions:
1. Analyze the problem and identify the decision variables
2. Formulate the constraint satisfaction problem clearly
3. Apply the specified search strategy ($searchStrategy):
   - For 'backtracking': Use systematic search with intelligent backtracking
   - For 'forward': Use greedy forward search with constraint propagation
   - For 'local': Use local search/hill-climbing with random restarts
4. Ensure ALL hard constraints are satisfied (non-negotiable)
5. Optimize soft constraints according to their weights
6. If no perfect solution exists, find the best compromise
7. Provide reasoning for the solution and trade-offs made
8. Include a satisfaction score for each soft constraint
9. Suggest alternatives if multiple good solutions exist

## Output Format:
Provide your solution in the following structure:

### Solution Overview
[Brief description of the proposed solution]

### Decision Variables
[List the key decisions made]

### Hard Constraint Satisfaction
[Verify each hard constraint is satisfied]

### Soft Constraint Optimization
[Score each soft constraint and explain trade-offs]

### Overall Score
[Weighted sum of soft constraint satisfaction]

### Reasoning
[Explain why this solution is optimal or near-optimal]

### Alternative Solutions
[If applicable, mention other viable options]

Generate the constraint satisfaction solution now:
        """.trimIndent()
    }
    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Save Solution") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(ConstraintSatisfactionTask::class.java)
        @JvmStatic val ConstraintSatisfaction = TaskType(
          name = "ConstraintSatisfaction",
          category = "Reasoning",
          taskClass = ConstraintSatisfactionTask::class.java,
          executionConfigClass = ConstraintSatisfactionTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Solve problems with multiple competing constraints",
          tooltipHtml = """
                        Solves constraint satisfaction problems with hard and soft constraints.
                        <ul>
                          <li>Handles hard constraints that must be satisfied</li>
                          <li>Optimizes soft constraints with configurable weights</li>
                          <li>Supports multiple search strategies (backtracking, forward, local)</li>
                          <li>Provides detailed reasoning and trade-off analysis</li>
                          <li>Suggests alternative solutions when applicable</li>
                          <li>Useful for architectural decisions, resource allocation, and optimization</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/CounterfactualAnalysisTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.io.FileOutputStream

class CounterfactualAnalysisTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: CounterfactualAnalysisTaskExecutionConfigData?
) : AbstractTask<CounterfactualAnalysisTask.CounterfactualAnalysisTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxDescriptionLength = 200
    protected val codeFiles = mutableMapOf<java.nio.file.Path, String>()

    class CounterfactualAnalysisTaskExecutionConfigData(
        @Description("The actual scenario or decision to analyze")
        var actual_scenario: String? = null,
        @Description("Alternative conditions to explore (what-if scenarios)")
        var counterfactuals: List<String>? = null,
        @Description("Whether to compare outcomes across scenarios")
        var compare_outcomes: Boolean = true,
        @Description("Factors to hold constant across scenarios")
        var control_factors: List<String>? = null,
        @Description("Additional files for context (e.g., historical data, related analyses)")
        var related_files: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("Detailed description of the analysis objectives")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = CounterfactualAnalysis.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (actual_scenario.isNullOrBlank()) {
                return "actual_scenario must not be null or blank"
            }
            if (counterfactuals.isNullOrEmpty()) {
                return "counterfactuals must contain at least one scenario"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
CounterfactualAnalysis - Explore "what-if" scenarios to understand causal relationships and decision impacts
  ** Specify the actual scenario or decision that occurred
  ** Provide a list of alternative conditions to explore (counterfactuals)
  ** Optionally specify factors to hold constant across scenarios for controlled comparison
  ** Enable outcome comparison to see differences between scenarios
  ** Useful for:
     - Risk analysis and mitigation planning
     - Decision validation and retrospective analysis
     - Understanding causal relationships
     - Exploring alternative strategies
     - Impact assessment of different choices
  ** Related files can include historical data, previous analyses, or context documents
  ** Output includes detailed analysis of each scenario and comparative insights
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


        val transcript = task.transcript()










        task.ui.pool.submit {
            try {
                log.info("Starting CounterfactualAnalysis task.")

                val actualScenario = executionConfig?.actual_scenario
                val counterfactuals = executionConfig?.counterfactuals ?: emptyList()

                if (actualScenario.isNullOrBlank()) {
                    val err = "CONFIGURATION ERROR: No actual scenario specified"
                    log.error(err)
                    task.error(Exception(err))
                    resultFn(err)
                    return@submit
                }

                if (counterfactuals.isEmpty()) {
                    val err = "CONFIGURATION ERROR: No counterfactual scenarios specified"
                    log.error(err)
                    task.error(Exception(err))
                    resultFn(err)
                    return@submit
                }

                val toInput = { it: String -> messages + listOf(getInputFileCode(), it).filter { it.isNotBlank() } }
                transcript?.write("# Counterfactual Analysis Transcript\n\n".toByteArray())
                val api = defaultSmart ?: return@submit

                val tabs = TabbedDisplay(task)
                val overviewTask = tabs.newTask("Overview")

                overviewTask.add(
                    """
                    |## Counterfactual Analysis
                    |
                    |**Actual Scenario:** ${actualScenario.truncateForDisplay(maxDescriptionLength)}
                    |
                    |**Counterfactuals:** ${counterfactuals.size}
                    |
                    |**Status:** 🔄 Starting analysis...
                    """.trimMargin().renderMarkdown()
                )
                transcript?.write(
                    """
                    |## Counterfactual Analysis
                    |
                    |**Actual Scenario:** ${actualScenario.truncateForDisplay(maxDescriptionLength)}
                    |
                    |**Counterfactuals:** ${counterfactuals.size}
                    |
                    |**Status:** 🔄 Starting analysis...
                    |
                    """.trimMargin().toByteArray()
                )

                val contextFiles = getContextFiles()
                val priorCode = getPriorCode(agent.executionState)
                val actualTab = tabs.newTask("Actual Scenario")

                // Analyze actual scenario
                val actualAnalysis = analyzeScenario(
                    "Actual Scenario",
                    actualScenario,
                    contextFiles,
                    priorCode,
                    api,
                    actualTab,
                    toInput,
                    transcript
                )
                transcript?.write("\n## Actual Scenario Analysis\n\n".toByteArray())
                transcript?.write("**Scenario:** $actualScenario\n\n".toByteArray())
                transcript?.write("**Analysis:**\n\n$actualAnalysis\n\n".toByteArray())
                val counterfactualTab = tabs.newTask("Counterfactuals")
                // Analyze counterfactual scenarios
                val counterfactualAnalyses = counterfactuals.mapIndexed { index, counterfactual ->
                    transcript?.write("\n## Counterfactual Scenario ${index + 1}\n\n".toByteArray())
                    transcript?.write("**Scenario:** $counterfactual\n\n".toByteArray())
                    val analysis = analyzeScenario(
                        "Counterfactual ${index + 1}",
                        counterfactual,
                        contextFiles,
                        priorCode,
                        api,
                        counterfactualTab,
                        toInput,
                        transcript
                    )
                    transcript?.write("**Analysis:**\n\n$analysis\n\n".toByteArray())
                    analysis
                }

                // Compare outcomes if requested
                val comparisonAnalysis = if (executionConfig?.compare_outcomes == true) {
                    val comparisonTab = tabs.newTask("Comparison")
                    transcript?.write("\n## Comparative Analysis\n\n".toByteArray())
                    val comparison = compareScenarios(
                        actualScenario = actualScenario,
                        actualAnalysisTokens = actualAnalysis.split("\\s+"),
                        counterfactuals = counterfactuals,
                        counterfactualAnalyses = counterfactualAnalyses,
                        controlFactors = executionConfig.control_factors,
                        contextFiles = contextFiles,
                        priorCode = priorCode,
                        api = api,
                        comparisonTab,
                        toInput = toInput,
                        transcript = transcript
                    )
                    transcript?.write(comparison.toByteArray())
                    comparison
                } else {
                    ""
                }

                val fullReport = buildString {
                    appendLine("# Counterfactual Analysis Results")
                    appendLine()
                    appendLine("## Actual Scenario")
                    appendLine(actualScenario)
                    appendLine()
                    appendLine("### Analysis")
                    appendLine(actualAnalysis)
                    appendLine()

                    counterfactuals.forEachIndexed { index, counterfactual ->
                        appendLine("## Counterfactual Scenario ${index + 1}")
                        appendLine(counterfactual)
                        appendLine()
                        appendLine("### Analysis")
                        appendLine(counterfactualAnalyses[index])
                        appendLine()
                    }

                    if (comparisonAnalysis.isNotBlank()) {
                        appendLine("## Comparative Analysis")
                        appendLine(comparisonAnalysis)
                    }
                }
                transcript?.write("\n---\n\n**Analysis Complete**\n".toByteArray())

                val (link, file) = task.createFile("analysis_results.md")
                file?.writeText(fullReport)
                task.complete("Analysis complete. Full results written to <a href='$link' target='_blank'>$link</a>")

                val summaryMessage = """
                    ## Counterfactual Analysis Complete
                    * **Scenario:** `${actualScenario.truncateForDisplay(50)}`
                    * **Alternatives Analyzed:** ${counterfactuals.size}
                    * **Report:** [Download Analysis Results]($link)
                """.trimIndent()
                resultFn(summaryMessage)
            } catch (e: Exception) {
                task.error(e)
                log.error("Error in CounterfactualAnalysisTask", e)
                transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
            } finally {
                transcript?.close()
            }
        }
    }

    private fun analyzeScenario(
        scenarioName: String,
        scenario: String,
        contextFiles: String,
        priorCode: String,
        api: ChatInterface,
        tab: SessionTask,
        toInput: (String) -> List<String>,
        transcript: FileOutputStream?
    ): String {
        val prompt = """
 Analyze the following scenario in detail:

## Scenario: $scenarioName
$scenario

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Control Factors:
${executionConfig?.control_factors?.joinToString("\n") { "- $it" } ?: "None specified"}

## Instructions:
1. Describe the key elements and conditions of this scenario
2. Identify the main actors, decisions, and constraints
3. Analyze potential outcomes and their likelihood
4. Identify risks, opportunities, and trade-offs
5. Consider both short-term and long-term implications
6. Highlight any assumptions or uncertainties
7. Provide insights on causal relationships
        """.trimIndent()
        transcript?.write("\n<details><summary>Prompt for $scenarioName</summary>\n\n```\n$prompt\n```\n</details>\n\n".toByteArray())


        val chatAgent = ChatAgent(
            prompt = prompt,
            model = api,
        )

        var result: String? = chatAgent.answer(listOf("Provide a comprehensive analysis"))
        transcript?.write("<details><summary>Response for $scenarioName</summary>\n\n${result ?: "(No response)"}\n\n</details>\n\n".toByteArray())
        tab.add("## $scenarioName\n\n${result ?: "No analysis generated."}".renderMarkdown(true))
        tab.update()
        return result ?: ""
    }

    private fun compareScenarios(
        actualScenario: String,
        actualAnalysisTokens: List<String>,
        counterfactuals: List<String>,
        counterfactualAnalyses: List<String>,
        controlFactors: List<String>?,
        contextFiles: String,
        priorCode: String,
        api: ChatInterface,
        tab: SessionTask,
        toInput: (String) -> List<String>,
        transcript: FileOutputStream?
    ): String {
        val scenarioComparisons = counterfactuals.zip(counterfactualAnalyses)
            .mapIndexed { index, (counterfactual, analysis) ->
                """
## Counterfactual ${index + 1}
**Scenario:** $counterfactual
**Analysis:** $analysis
                """.trimIndent()
            }.joinToString("\n\n")

        val prompt = """
Compare the following scenarios and provide insights on their differences:

## Actual Scenario
**Description:** $actualScenario
**Analysis:** ${actualAnalysisTokens.joinToString(" ")}

$scenarioComparisons

## Control Factors (held constant):
${controlFactors?.joinToString("\n") { "- $it" } ?: "None specified"}

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Compare outcomes across all scenarios
2. Identify key differences and their causes
3. Assess which factors had the most impact
4. Evaluate risks and benefits of each alternative
5. Determine which scenario(s) would have been preferable and why
6. Identify lessons learned and actionable insights
7. Highlight any surprising or counterintuitive findings
8. Provide recommendations based on the analysis

        """.trimIndent()
        transcript?.write("\n<details><summary>Comparison Prompt</summary>\n\n```\n$prompt\n```\n</details>\n\n".toByteArray())


        val chatAgent = ChatAgent(
            prompt = prompt,
            model = api,
        )

        var result: String? = chatAgent.answer(listOf("Provide a comprehensive comparative analysis"))
        transcript?.write("<details><summary>Comparison Response</summary>\n\n${result ?: "(No response)"}\n\n</details>\n\n".toByteArray())
        tab.add("## Comparative Analysis\n\n${result ?: "No comparison generated."}".renderMarkdown(true))
        tab.update()
        return result ?: ""
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: emptyList()
        if (relatedFiles.isEmpty()) return "No related files provided"

        return relatedFiles.joinToString("\n\n") { pattern ->
            try {
                val file = root.resolve(pattern).toFile()
                if (file.exists() && file.isFile) {
                    "# ${file.name}\n\n```\n${file.readText()}\n```"
                } else {
                    "# $pattern\n(File not found)"
                }
            } catch (e: Exception) {
                log.warn("Error reading file: $pattern", e)
                "# $pattern\n(Error reading file: ${e.message})"
            }
        }
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
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
        file.readText()
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}", e)
        "Error reading file: ${e.message}"
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(CounterfactualAnalysisTask::class.java)
        @JvmStatic val CounterfactualAnalysis = TaskType(
            name = "CounterfactualAnalysis",
            category = "Reasoning",
            taskClass = CounterfactualAnalysisTask::class.java,
            executionConfigClass = CounterfactualAnalysisTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Explore what-if scenarios to understand causal relationships and decision impacts",
            tooltipHtml = """
                        Performs counterfactual analysis to explore alternative scenarios and outcomes.
                        <ul>
                          <li>Analyzes actual scenarios and alternative conditions</li>
                          <li>Compares outcomes across different scenarios</li>
                          <li>Identifies causal relationships and key factors</li>
                          <li>Supports controlled comparison with constant factors</li>
                          <li>Provides insights for risk analysis and decision validation</li>
                          <li>Useful for retrospective analysis and strategic planning</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/DecisionTreeTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.log2

class DecisionTreeTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DecisionTreeTaskExecutionConfigData?
) : AbstractTask<DecisionTreeTask.DecisionTreeTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class DecisionTreeTaskExecutionConfigData(
        @Description("The data file to analyze (CSV)")
        var data_file: String? = null,
        @Description("The target column name to predict")
        var target_column: String? = null,
        @Description("Maximum depth of the tree")
        var max_depth: Int = 3,
        @Description("Number of candidate rules to generate per node")
        var candidate_rules: Int = 5,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = DecisionTree.name,
        task_description = "Build a decision tree for '$target_column' from '$data_file'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (data_file.isNullOrBlank()) return "data_file is required"
            if (target_column.isNullOrBlank()) return "target_column is required"
            return null
        }
    }

    override fun promptSegment(): String {
        return """
 DecisionTree - Build an LLM-driven symbolic decision tree
  ** Specify the data file (CSV)
  ** Specify the target column to predict
  ** Configure max depth and candidate rules
  ** Uses LLM to propose semantic splitting rules
  ** Validates rules using Information Gain
  ** Generates executable code
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


      val transcript = task.transcript()
      try {
        val config = executionConfig ?: return
        val error = config.validate()
        if (error != null) {
          val msg = "Configuration Error: $error"
          task.error(Exception(msg))
          log.error(msg)
          transcript?.write("## Error\n$msg\n".toByteArray())
          task.complete()
          return
        }

        val tabs = TabbedDisplay(task)
        val executionTask = tabs.newTask("Execution")
        executionTask.header("Building Decision Tree")
        val statusBuffer = executionTask.add("Initializing...".renderMarkdown())

        task.ui.pool.submit {
          try {
            log.info("Starting DecisionTreeTask for ${config.data_file}")
            transcript?.write("## Decision Tree Construction Started\n".toByteArray())

            val dataFile = root.resolve(config.data_file!!).toFile()
            if (!dataFile.exists()) {
              val msg = "Data file not found: ${config.data_file}"
              executionTask.error(Exception(msg))
              log.error(msg)
              transcript?.write("### Error\n$msg\n".toByteArray())
              return@submit
            }

            val records = try {
              if (dataFile.extension.equals("jsonl", ignoreCase = true)) {
                loadJsonl(dataFile)
              } else {
                loadCsv(dataFile)
              }
            } catch (e: Exception) {
              executionTask.error(e)
              log.error("Error loading data", e)
              transcript?.write("### Error Loading Data\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
              return@submit
            }

            if (records.isEmpty()) {
              executionTask.add("Data file is empty".renderMarkdown())
              return@submit
            }

            val headers = records.first().keys
            if (!headers.contains(config.target_column)) {
              val msg = "Target column '${config.target_column}' not found. Available: $headers"
              executionTask.add(msg.renderMarkdown())
              return@submit
            }

            statusBuffer?.setLength(0)
            statusBuffer?.append("Loaded ${records.size} records. Analyzing...".renderMarkdown())
            executionTask.update()

            val chatAgent = ChatAgent(
              model = defaultSmart.getChildClient(task),
              temperature = 0.2,
              prompt = """
                            You are a Decision Tree Rule Generator.
                            Analyze the provided data samples and propose splitting rules to predict the target variable: '${config.target_column}'.
                            
                            Output Format:
                            Provide exactly ${config.candidate_rules} distinct rules.
                            Each rule must be in the format: `FIELD OPERATOR VALUE`
                            
                            Supported Operators:
                            - `==` (Exact match)
                            - `!=` (Not equal)
                            - `>` (Numeric greater than)
                            - `<` (Numeric less than)
                            - `contains` (String contains)
                            - `matches` (Regex match)
                            
                            Examples:
                            age > 25
                            status == active
                            description contains error
                            sku matches ^[A-Z]-123
                            
                            Do not include explanations. Just the rules, one per line.
                        """.trimIndent()
            )

            tabs["Configuration"] = """
                        ### Parameters
                        * **Target:** `${config.target_column}`
                        * **Data File:** `${config.data_file}`
                        * **Records:** ${records.size}
                        * **Max Depth:** ${config.max_depth}
                    """.trimIndent().renderMarkdown()

            val tree = buildTree(
              records,
              config.target_column!!,
              0,
              config.max_depth,
              config.candidate_rules,
              chatAgent,
              executionTask
            )
            val code = generateCode(tree)

            statusBuffer?.setLength(0)
            statusBuffer?.append("Tree construction complete.".renderMarkdown())
            executionTask.update()

            tabs["Generated Code"] = "```kotlin\n$code\n```".renderMarkdown()

            val fileUrl = task.saveFile("DecisionTree.kt", code.toByteArray())
            executionTask.add("Download: <a href='$fileUrl'>DecisionTree.kt</a>")

            transcript?.write("### Tree Construction Complete\n<details><summary>Generated Code</summary>\n\n```kotlin\n$code\n```\n</details>\n".toByteArray())
            log.info("DecisionTreeTask completed successfully.")

            resultFn(
              """
                        ## Decision Tree Generated
                        * Target: `${config.target_column}`
                        * Max Depth: `${config.max_depth}`
                        * Code saved to: `DecisionTree.kt`
                    """.trimIndent()
            )
          } catch (e: Exception) {
            executionTask.error(e)
            log.error("Error in DecisionTreeTask execution", e)
            transcript?.write("## Execution Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
          } finally {
            executionTask.complete()
            task.complete()
          }
        }
      } catch (e: Exception) {
        task.error(e)
        log.error("Error initializing DecisionTreeTask", e)
      } finally {
        transcript?.close()
      }
    }

    private fun loadCsv(file: File): List<Map<String, String>> {
        val lines = file.readLines()
        if (lines.isEmpty()) return emptyList()
        // Simple CSV parsing - assumes no commas in values for simplicity, or use a library if available
        val headers = lines.first().split(",").map { it.trim() }
        return lines.drop(1).mapNotNull { line ->
            val values = line.split(",").map { it.trim() }
            if (values.size == headers.size) {
                headers.zip(values).toMap()
            } else null
        }
    }

    private fun loadJsonl(file: File): List<Map<String, String>> {
        return file.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
            try {
                line.trim().removeSurrounding("{", "}").split(",").associate {
                    val (k, v) = it.split(":", limit = 2)
                    k.trim().removeSurrounding("\"") to v.trim().removeSurrounding("\"")
                }
            } catch (e: Exception) {
                null
            }
        }
    }


    private sealed class Node
    private data class Leaf(val prediction: String, val probability: Double, val count: Int) : Node()
    private data class Split(
        val rule: String,
        val left: Node,
        val right: Node,
        val feature: String,
        val operator: String,
        val value: String
    ) : Node()

    private fun buildTree(
        data: List<Map<String, String>>,
        target: String,
        depth: Int,
        maxDepth: Int,
        candidateRules: Int,
        agent: ChatAgent,
        task: SessionTask
    ): Node {
        val counts = data.groupingBy { it[target] ?: "Unknown" }.eachCount()
        val total = data.size
        val dominantClass = counts.maxByOrNull { it.value }?.key ?: "Unknown"
        val purity = (counts[dominantClass] ?: 0).toDouble() / total

        if (depth >= maxDepth || purity > 0.95 || total < 5) {
            return Leaf(dominantClass, purity, total)
        }

        // Sample data for LLM
        val sample = data.shuffled().take(10).joinToString("\n") { record ->
            record.entries.joinToString(", ") { "${it.key}=${it.value}" }
        }

        val prompt = """
            Target Variable: $target
            Current Data Sample (10 of $total records):
            $sample
            
            Propose $candidateRules splitting rules to separate '$target'.
        """.trimIndent()

        val response = agent.answer(listOf(prompt))
        val rules = response.lines().filter { it.isNotBlank() }.map { it.trim() }

        var bestGain = -1.0
        var bestSplit: Split? = null
        var bestLeftData: List<Map<String, String>> = emptyList()
        var bestRightData: List<Map<String, String>> = emptyList()

        val currentEntropy = entropy(data, target)

        for (ruleStr in rules) {
            val parts = parseRule(ruleStr) ?: continue
            val (feature, op, value) = parts

            val (left, right) = data.partition { record ->
                evaluate(record, feature, op, value)
            }

            if (left.isEmpty() || right.isEmpty()) continue

            val pLeft = left.size.toDouble() / total
            val pRight = right.size.toDouble() / total
            val gain = currentEntropy - (pLeft * entropy(left, target) + pRight * entropy(right, target))

            if (gain > bestGain) {
                bestGain = gain
                bestSplit = Split(ruleStr, Leaf("temp", 0.0, 0), Leaf("temp", 0.0, 0), feature, op, value)
                bestLeftData = left
                bestRightData = right
            }
        }

        if (bestSplit == null || bestGain < 0.01) {
            return Leaf(dominantClass, purity, total)
        }

        task.add("Depth $depth: Split on <b>${bestSplit.rule}</b> (Gain: ${"%.4f".format(bestGain)})")

        val leftNode = buildTree(bestLeftData, target, depth + 1, maxDepth, candidateRules, agent, task)
        val rightNode = buildTree(bestRightData, target, depth + 1, maxDepth, candidateRules, agent, task)

        return bestSplit.copy(left = leftNode, right = rightNode)
    }

    private fun parseRule(rule: String): Triple<String, String, String>? {
        // Simple regex to parse "field op value"
        // Allow spaces in value if it's the last part
        val regex = Regex("""^([a-zA-Z0-9_]+)\s+(==|!=|>|<|contains|matches)\s+(.+)$""")
        val match = regex.find(rule) ?: return null
        val (feature, op, value) = match.destructured
        return Triple(feature, op, value)
    }

    private fun evaluate(record: Map<String, String>, feature: String, op: String, value: String): Boolean {
        val recordValue = record[feature] ?: return false
        return when (op) {
            "==" -> recordValue == value
            "!=" -> recordValue != value
            ">" -> (recordValue.toDoubleOrNull() ?: 0.0) > (value.toDoubleOrNull() ?: 0.0)
            "<" -> (recordValue.toDoubleOrNull() ?: 0.0) < (value.toDoubleOrNull() ?: 0.0)
            "contains" -> recordValue.contains(value, ignoreCase = true)
            "matches" -> try {
                Regex(value).matches(recordValue)
            } catch (e: Exception) {
                false
            }

            else -> false
        }
    }

    private fun entropy(data: List<Map<String, String>>, target: String): Double {
        val counts = data.groupingBy { it[target] }.eachCount()
        val total = data.size.toDouble()
        return -counts.values.sumOf {
            val p = it / total
            p * log2(p)
        }
    }

    private fun generateCode(node: Node): String {
        val sb = StringBuilder()
        sb.append("fun predict(record: Map<String, String>): String {\n")

        fun traverse(n: Node, indent: String) {
            when (n) {
                is Leaf -> {
                    sb.append("$indent// Probability: ${"%.2f".format(n.probability)} (n=${n.count})\n")
                    sb.append("${indent}return \"${n.prediction}\"\n")
                }

                is Split -> {
                    val condition = when (n.operator) {
                        "==" -> "record[\"${n.feature}\"] == \"${n.value}\""
                        "!=" -> "record[\"${n.feature}\"] != \"${n.value}\""
                        ">" -> "(record[\"${n.feature}\"]?.toDoubleOrNull() ?: 0.0) > ${n.value}"
                        "<" -> "(record[\"${n.feature}\"]?.toDoubleOrNull() ?: 0.0) < ${n.value}"
                        "contains" -> "record[\"${n.feature}\"]?.contains(\"${n.value}\", ignoreCase = true) == true"
                        "matches" -> "Regex(\"${n.value}\").matches(record[\"${n.feature}\"] ?: \"\")"
                        else -> "false"
                    }
                    sb.append("${indent}if ($condition) {\n")
                    traverse(n.left, indent + "    ")
                    sb.append("$indent} else {\n")
                    traverse(n.right, indent + "    ")
                    sb.append("$indent}\n")
                }
            }
        }

        traverse(node, "    ")
        sb.append("}")
        return sb.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(DecisionTreeTask::class.java)
        @JvmStatic val DecisionTree = TaskType(
          name = "DecisionTree",
          category = "Reasoning",
          taskClass = DecisionTreeTask::class.java,
          executionConfigClass = DecisionTreeTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Build an LLM-driven symbolic decision tree",
          tooltipHtml = """
                        Constructs a decision tree classifier using LLM for rule proposal and data for validation.
                        <ul>
                          <li>Handles unstructured text via semantic rules</li>
                          <li>Generates interpretable code</li>
                          <li>Uses Information Gain for split selection</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/DecompositionSynthesisTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DecompositionSynthesisTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DecompositionSynthesisTaskExecutionConfigData?
) : AbstractTask<DecompositionSynthesisTask.DecompositionSynthesisTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxDescriptionLength = 1000

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DecompositionSynthesisTask::class.java)
        @JvmStatic val DecompositionSynthesis: TaskType<DecompositionSynthesisTaskExecutionConfigData, TaskTypeConfig> = TaskType(
            name = "DecompositionSynthesis",
            category = "Reasoning",
            taskClass = DecompositionSynthesisTask::class.java,
            executionConfigClass = DecompositionSynthesisTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Decompose complex problems and synthesize solutions",
            tooltipHtml = """
                        Decomposes complex problems into manageable subproblems, solves them, and synthesizes solutions.
                        <ul>
                          <li>Multiple decomposition strategies (functional, temporal, spatial, hierarchical)</li>
                          <li>Configurable decomposition depth</li>
                          <li>Dependency-aware subproblem solving</li>
                          <li>Solution synthesis with coherence validation</li>
                          <li>Confidence tracking at each level</li>
                          <li>Implements divide-and-conquer reasoning</li>
                        </ul>
                      """,
        )
    }

    class DecompositionSynthesisTaskExecutionConfigData(
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("Whether to include file context in the analysis")
        var include_file_context: Boolean = true,
        @Description("The complex problem to decompose")
        var complex_problem: String? = null,
        @Description("Decomposition strategy: 'functional', 'temporal', 'spatial', 'hierarchical'")
        var decomposition_strategy: String = "functional",
        @Description("Maximum decomposition depth")
        var max_depth: Int = 3,
        @Description("Whether to synthesize solutions from subproblems")
        var synthesize_solution: Boolean = true,
        @Description("Whether to validate synthesis coherence")
        var validate_coherence: Boolean = true,
        @Description("Additional files for context")
        var related_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = DecompositionSynthesis.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    class ProblemDecomposition(
        @Description("List of subproblems identified")
        var subproblems: List<Subproblem> = emptyList(),
        @Description("Rationale for this decomposition")
        var decomposition_rationale: String = "",
        @Description("Dependencies between subproblems")
        var dependencies: Map<String, List<String>> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (subproblems.isEmpty()) return "ProblemDecomposition must have at least one subproblem"
            if (decomposition_rationale.isBlank()) return "ProblemDecomposition must have a decomposition_rationale"
            return ValidatedObject.validateFields(this)
        }
    }

    class Subproblem(
        @Description("Unique identifier for this subproblem")
        var id: String = "",
        @Description("Description of the subproblem")
        var description: String = "",
        @Description("Estimated complexity (1-10)")
        var complexity: Int = 5,
        @Description("Whether this can be further decomposed")
        var can_decompose: Boolean = false
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "Subproblem must have an id"
            if (description.isBlank()) return "Subproblem must have a description"
            if (complexity !in 1..10) return "Subproblem complexity must be between 1 and 10, got $complexity"
            return null
        }
    }

    class SubproblemSolution(
        @Description("The subproblem ID being solved")
        var subproblem_id: String = "",
        @Description("The solution to this subproblem")
        var solution: String = "",
        @Description("Confidence in this solution (0-1)")
        var confidence: Double = 0.0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (subproblem_id.isBlank()) return "SubproblemSolution must have a subproblem_id"
            if (solution.isBlank()) return "SubproblemSolution must have a solution"
            if (confidence !in 0.0..1.0) return "SubproblemSolution confidence must be between 0 and 1, got $confidence"
            return null
        }
    }

    class SynthesizedSolution(
        @Description("The complete synthesized solution")
        var solution: String = "",
        @Description("How subproblem solutions were integrated")
        var synthesis_approach: String = "",
        @Description("Overall confidence in the solution (0-1)")
        var confidence: Double = 0.0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (solution.isBlank()) return "SynthesizedSolution must have a solution"
            if (synthesis_approach.isBlank()) return "SynthesizedSolution must have a synthesis_approach"
            if (confidence !in 0.0..1.0) return "SynthesizedSolution confidence must be between 0 and 1, got $confidence"
            return null
        }
    }

    class CoherenceValidation(
        @Description("Whether the solution is coherent")
        var is_coherent: Boolean = false,
        @Description("Issues found in the synthesis")
        var issues: List<String> = emptyList(),
        @Description("Suggestions for improvement")
        var suggestions: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
 DecompositionSynthesis - Break down complex problems into subproblems and synthesize integrated solutions
  ** Optionally, list input files (supports glob patterns) to be examined for context
  ** Problem: ${executionConfig?.complex_problem?.take(100) ?: "Not specified"}
  ** Specify the complex problem to decompose
  ** Choose decomposition strategy:
     - functional: Break down by function/capability
     - temporal: Break down by time/sequence
     - spatial: Break down by location/component
     - hierarchical: Break down by abstraction level
  ** Set maximum decomposition depth (default: 3)
  ** Enable solution synthesis to combine subproblem solutions
  ** Enable coherence validation to check solution consistency
  ** Related files can provide context for the problem
  ** Output: Comprehensive solution with decomposition analysis, subproblem solutions, and synthesis
  ** Returns: Final synthesized solution or concatenated subproblem solutions
  ** Implements divide-and-conquer reasoning approach
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        task.ui.pool.submit {
            val startTime = System.currentTimeMillis()
            log.info(
                "Starting DecompositionSynthesisTask with problem: ${
                    executionConfig?.complex_problem?.take(
                        maxDescriptionLength
                    )
                }"
            )

            val problem = executionConfig?.complex_problem
            if (problem.isNullOrBlank()) {
                log.error("No problem specified in execution config")
                task.complete("CONFIGURATION ERROR: No problem specified")
                resultFn("CONFIGURATION ERROR: No problem specified")
                return@submit
            }

            // Create tabbed display for organized output
            val tabs = TabbedDisplay(task)
            val transcriptStream = try {
                task.transcript("decomposition_transcript")
            } catch (e: Exception) {
                log.error("Failed to initialize transcript", e)
                null
            }
            val api = defaultSmart ?: run {
                log.error("No default chatter available")
                task.complete("ERROR: No API available")
                resultFn("ERROR: No API available")
                return@submit
            }


            // Overview tab
            val overviewTask = task.newTask()
            tabs["Overview"] = overviewTask.placeholder

            val overviewContent = buildString {
                appendLine("# Decomposition & Synthesis Analysis")
                appendLine()
                appendLine("**Problem:** ${problem.take(maxDescriptionLength)}${if (problem.length > maxDescriptionLength) "..." else ""}")
                appendLine()
                appendLine("**Strategy:** ${executionConfig?.decomposition_strategy}")
                appendLine("**Max Depth:** ${executionConfig?.max_depth}")
                appendLine("**Synthesize Solution:** ${executionConfig?.synthesize_solution}")
                appendLine("**Validate Coherence:** ${executionConfig?.validate_coherence}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("⏳ Starting decomposition analysis...")
            }
            overviewTask.add(overviewContent.renderMarkdown())
            transcriptStream?.let { writeToTranscript(it, overviewContent) }

            try {
                // Step 3: Build context from related files and dependencies
                log.debug("Building context from related files and dependencies")
                // Get context from related files and dependencies
                val context = buildContext(agent, root)
                // Context tab
                val contextTask = task.newTask()
                tabs["Context"] = contextTask.placeholder
                contextTask.add(buildString {
                    appendLine("# Task Context")
                    appendLine()
                    appendLine("The following context, derived from previous tasks and related files, will be used to inform the analysis.")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(context)
                }.renderMarkdown())
                transcriptStream?.let {
                    writeToTranscript(it, buildString {
                        appendLine("# Task Context")
                        appendLine("<details><summary>Raw Context Data</summary>\n")
                        appendLine(context)
                        appendLine("\n</details>")
                    })
                }

                // Update overview with context info
                overviewTask.add(buildString {
                    appendLine()
                    appendLine("✅ Context built successfully")
                    appendLine()
                }.renderMarkdown())
                transcriptStream?.let { writeToTranscript(it, "\n✅ Context built successfully\n\n") }
                // Step 4: Decompose the problem
                // Decomposition tab
                val decompositionTask = task.newTask()
                tabs["Decomposition"] = decompositionTask.placeholder
                decompositionTask.add(buildString {
                    appendLine("# Problem Decomposition")
                    appendLine()
                    appendLine("⏳ Analyzing problem structure...")
                    appendLine()
                    appendLine("**Strategy:** ${executionConfig?.decomposition_strategy}")
                    appendLine("**Max Depth:** ${executionConfig?.max_depth}")
                }.renderMarkdown())
                transcriptStream?.let {
                    writeToTranscript(it, buildString {
                        appendLine("# Problem Decomposition")
                        appendLine()
                        appendLine("**Strategy:** ${executionConfig?.decomposition_strategy}")
                        appendLine("**Max Depth:** ${executionConfig?.max_depth}")
                    })
                }
                log.info("Starting problem decomposition with strategy: ${executionConfig?.decomposition_strategy}")

                val decomposition = decomposeProblem(
                    problem = problem,
                    strategy = executionConfig?.decomposition_strategy ?: "functional",
                    maxDepth = executionConfig?.max_depth ?: 3,
                    currentDepth = 0,
                    context = context,
                    api = api
                )
                log.info("Decomposition completed: ${decomposition.subproblems.size} subproblems identified")

                decompositionTask.add(buildString {
                    appendLine()
                    appendLine("✅ Decomposition complete!")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## Results")
                    appendLine()
                    appendLine("**Rationale:** ${decomposition.decomposition_rationale}")
                    appendLine()
                    appendLine("### Subproblems Identified (${decomposition.subproblems.size})")
                    appendLine()
                    decomposition.subproblems.forEachIndexed { index, subproblem ->
                        appendLine("${index + 1}. **${subproblem.id}**: ${subproblem.description}")
                        appendLine("   - Complexity: ${subproblem.complexity}/10")
                        appendLine("   - Can Decompose Further: ${if (subproblem.can_decompose) "Yes" else "No"}")
                        appendLine()
                    }
                    appendLine("### Dependencies")
                    appendLine()
                    if (decomposition.dependencies.isEmpty()) {
                        appendLine("*No dependencies identified - subproblems can be solved independently*")
                    } else {
                        decomposition.dependencies.entries.forEach { (id, deps) ->
                            appendLine("- **$id** depends on: ${deps.joinToString(", ")}")
                        }
                    }
                    appendLine()
                }.renderMarkdown())
                transcriptStream?.let {
                    writeToTranscript(it, buildString {
                        appendLine()
                        appendLine("## Decomposition Results")
                        appendLine()
                        appendLine("**Rationale:** ${decomposition.decomposition_rationale}")
                        appendLine()
                        appendLine("### Subproblems (${decomposition.subproblems.size})")
                        decomposition.subproblems.forEachIndexed { index, subproblem ->
                            appendLine("${index + 1}. **${subproblem.id}**: ${subproblem.description}")
                            appendLine("   - Complexity: ${subproblem.complexity}/10")
                        }
                        appendLine()
                        appendLine("### Dependencies")
                        if (decomposition.dependencies.isEmpty()) {
                            appendLine("*No dependencies*")
                        } else {
                            decomposition.dependencies.entries.forEach { (id, deps) ->
                                appendLine(
                                    "- **$id** → ${
                                        deps.joinToString(
                                            ", "
                                        )
                                    }"
                                )
                            }
                        }
                    })
                }
                // Step 5: Solve all subproblems

                // Update overview
                overviewTask.add(buildString {
                    appendLine("✅ Decomposition complete: ${decomposition.subproblems.size} subproblems identified")
                    appendLine()
                }.renderMarkdown())
                transcriptStream?.let {
                    writeToTranscript(
                        it,
                        "\n✅ Decomposition complete: ${decomposition.subproblems.size} subproblems\n\n"
                    )
                }

                // Subproblem Solutions tab
                val solutionsTask = task.newTask()
                tabs["Subproblem Solutions"] = solutionsTask.placeholder
                solutionsTask.add(buildString {
                    appendLine("# Subproblem Solutions")
                    appendLine()
                    appendLine("⏳ Solving ${decomposition.subproblems.size} subproblems...")
                    appendLine()
                }.renderMarkdown())
                transcriptStream?.let {
                    writeToTranscript(it, buildString {
                        appendLine("# Subproblem Solutions")
                        appendLine()
                        appendLine("Solving ${decomposition.subproblems.size} subproblems...")
                    })
                }

                val solvedCount = AtomicInteger(0)
                log.info("Starting to solve ${decomposition.subproblems.size} subproblems")
                val solutions = solveSubproblems(
                    decomposition = decomposition,
                    context = context,
                    task = solutionsTask,
                    api = api,
                    progressCallback = { subproblemId, solution ->
                        val count = solvedCount.incrementAndGet()
                        log.debug("Solved subproblem $count/${decomposition.subproblems.size}: $subproblemId")
                        solutionsTask.add(buildString {
                            appendLine()
                            appendLine("### ${count}. ${subproblemId}")
                            appendLine()
                            appendLine("**Confidence:** ${(solution.confidence * 100).toInt()}%")
                            appendLine()
                            appendLine(solution.solution)
                            appendLine()
                            appendLine("---")
                            appendLine()
                            appendLine("**Progress:** ${count}/${decomposition.subproblems.size} subproblems solved")
                            appendLine()
                        }.renderMarkdown())
                        transcriptStream?.let {
                            writeToTranscript(it, buildString {
                                appendLine()
                                appendLine("## ${count}. ${subproblemId}")
                                appendLine()
                                appendLine("**Confidence:** ${(solution.confidence * 100).toInt()}%")
                                appendLine()
                                appendLine(solution.solution)
                            })
                        }

                        // Update overview
                        overviewTask.add(buildString {
                            appendLine("⏳ Solving subproblems: ${count}/${decomposition.subproblems.size}")
                            appendLine()
                        }.renderMarkdown())
                    }
                )


                solutionsTask.add(buildString {
                    appendLine()
                    appendLine("✅ All subproblems solved!")
                    appendLine()
                    appendLine("**Average Confidence:** ${(solutions.map { it.confidence }.average() * 100).toInt()}%")
                    appendLine()
                }.renderMarkdown())
                transcriptStream?.let {
                    writeToTranscript(it, buildString {
                        appendLine()
                        appendLine(
                            "✅ All subproblems solved! Average confidence: ${
                                (solutions.map { it.confidence }.average() * 100).toInt()
                            }%"
                        )
                    })
                }
                // Step 6: Synthesize solution (if requested)

                // Update overview
                overviewTask.add(buildString {
                    appendLine("✅ All ${solutions.size} subproblems solved")
                    appendLine()
                }.renderMarkdown())
                transcriptStream?.let { writeToTranscript(it, "\n✅ All ${solutions.size} subproblems solved\n\n") }

                val finalResult = if (executionConfig?.synthesize_solution == true) {
                    // Synthesis tab
                    val synthesisTask = task.newTask()
                    tabs["Synthesis"] = synthesisTask.placeholder
                    synthesisTask.add(buildString {
                        appendLine("# Solution Synthesis")
                        appendLine()
                        appendLine("⏳ Integrating ${solutions.size} subproblem solutions...")
                        appendLine()
                    }.renderMarkdown())
                    transcriptStream?.let {
                        writeToTranscript(it, buildString {
                            appendLine("# Solution Synthesis")
                            appendLine()
                            appendLine("Integrating ${solutions.size} subproblem solutions...")
                        })
                    }
                    log.info("Starting solution synthesis from ${solutions.size} subproblem solutions")
                    val synthesized = synthesizeSolution(
                        problem = problem,
                        decomposition = decomposition,
                        solutions = solutions,
                        context = context,
                        api = api
                    )
                    log.info("Solution synthesis completed with confidence: ${synthesized.confidence}")

                    synthesisTask.add(buildString {
                        appendLine()
                        appendLine("✅ Synthesis complete!")
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("## Synthesized Solution")
                        appendLine()
                        appendLine("**Synthesis Approach:** ${synthesized.synthesis_approach}")
                        appendLine()
                        appendLine("**Overall Confidence:** ${(synthesized.confidence * 100).toInt()}%")
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine(synthesized.solution)
                        appendLine()
                    }.renderMarkdown())
                    transcriptStream?.let {
                        writeToTranscript(it, buildString {
                            appendLine()
                            appendLine("## Synthesized Solution")
                            appendLine()
                            appendLine("**Synthesis Approach:** ${synthesized.synthesis_approach}")
                            appendLine("**Confidence:** ${(synthesized.confidence * 100).toInt()}%")
                            appendLine()
                            appendLine(synthesized.solution)
                        })
                    }

                    // Update overview
                    overviewTask.add(buildString {
                        appendLine("✅ Solution synthesized (confidence: ${(synthesized.confidence * 100).toInt()}%)")
                        appendLine()
                    }.renderMarkdown())
                    transcriptStream?.let {
                        writeToTranscript(
                            it,
                            "\n✅ Solution synthesized (confidence: ${(synthesized.confidence * 100).toInt()}%)\n\n"
                        )
                    }
                    // Step 7: Validate coherence (if requested)

                    // Validate coherence if requested
                    if (executionConfig?.validate_coherence == true) {
                        // Validation tab
                        val validationTask = task.newTask()
                        tabs["Validation"] = validationTask.placeholder
                        validationTask.add(buildString {
                            appendLine("# Coherence Validation")
                            appendLine()
                            appendLine("⏳ Validating solution coherence...")
                            appendLine()
                        }.renderMarkdown())
                        transcriptStream?.let {
                            writeToTranscript(it, buildString {
                                appendLine("# Coherence Validation")
                                appendLine()
                            })
                        }
                        log.info("Starting coherence validation")
                        val validation = validateCoherence(
                            problem = problem,
                            synthesized = synthesized,
                            solutions = solutions,
                            api = api
                        )
                        log.info("Validation completed: coherent=${validation.is_coherent}, issues=${validation.issues.size}")

                        validationTask.add(buildString {
                            appendLine()
                            appendLine("✅ Validation complete!")
                            appendLine()
                            appendLine("---")
                            appendLine()
                            appendLine("## Results")
                            appendLine()
                            appendLine("**Is Coherent:** ${if (validation.is_coherent) "✅ Yes" else "❌ No"}")
                            appendLine()
                            if (validation.issues.isNotEmpty()) {
                                appendLine("### Issues Found (${validation.issues.size})")
                                appendLine()
                                validation.issues.forEach { issue ->
                                    appendLine("- ⚠️ $issue")
                                }
                                appendLine()
                            }
                            if (validation.suggestions.isNotEmpty()) {
                                appendLine("### Suggestions for Improvement (${validation.suggestions.size})")
                                appendLine()
                                validation.suggestions.forEach { suggestion ->
                                    appendLine("- 💡 $suggestion")
                                }
                                appendLine()
                            }
                            if (validation.issues.isEmpty() && validation.suggestions.isEmpty()) {
                                appendLine("*No issues or suggestions - solution is coherent and complete*")
                                appendLine()
                            }
                        }.renderMarkdown())
                        transcriptStream?.let {
                            writeToTranscript(it, buildString {
                                appendLine()
                                appendLine("## Validation Results")
                                appendLine()
                                appendLine("**Is Coherent:** ${if (validation.is_coherent) "Yes" else "No"}")
                                if (validation.issues.isNotEmpty()) {
                                    appendLine()
                                    appendLine("### Issues (${validation.issues.size})")
                                    validation.issues.forEach { appendLine("- $it") }
                                }
                                if (validation.suggestions.isNotEmpty()) {
                                    appendLine()
                                    appendLine("### Suggestions (${validation.suggestions.size})")
                                    validation.suggestions.forEach { appendLine("- $it") }
                                }
                            })
                        }

                        // Update overview
                        overviewTask.add(buildString {
                            appendLine("✅ Validation complete: ${if (validation.is_coherent) "coherent" else "issues found"}")
                            appendLine()
                        }.renderMarkdown())
                        transcriptStream?.let { writeToTranscript(it, "\n✅ Validation complete\n\n") }
                    }

                    synthesized.solution
                } else {
                    log.info("Skipping synthesis, returning individual subproblem solutions")
                    overviewTask.add(
                        "ℹ️ Synthesis skipped - returning individual solutions\n\n".renderMarkdown()
                    )
                    // Just return the subproblem solutions
                    solutions.joinToString("\n\n") { "${it.subproblem_id}:\n${it.solution}" }
                }
                // Step 8: Finalize and return results

                // Final summary in overview
                val totalTime = System.currentTimeMillis() - startTime
                log.info("DecompositionSynthesisTask completed successfully in ${totalTime}ms")

                overviewTask.add(buildString {
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Analysis Complete!")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000} seconds")
                    appendLine("**Subproblems Identified:** ${decomposition.subproblems.size}")
                    appendLine("**Solutions Generated:** ${solutions.size}")
                    appendLine("**Average Confidence:** ${(solutions.map { it.confidence }.average() * 100).toInt()}%")
                    if (executionConfig?.synthesize_solution == true) {
                        appendLine("**Synthesis:** ✅ Complete")
                    }
                    if (executionConfig?.validate_coherence == true) {
                        appendLine("**Validation:** ✅ Complete")
                    }
                    appendLine()
                }.renderMarkdown())
                transcriptStream?.let {
                    writeToTranscript(it, buildString {
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("## Analysis Complete")
                        appendLine()
                        appendLine("**Total Time:** ${totalTime / 1000}s")
                        appendLine("**Subproblems:** ${decomposition.subproblems.size}")
                        appendLine("**Solutions:** ${solutions.size}")
                        appendLine("**Avg Confidence:** ${(solutions.map { it.confidence }.average() * 100).toInt()}%")
                    })
                }

                val summary =
                    "Decomposition & Synthesis completed: ${decomposition.subproblems.size} subproblems, ${solutions.size} solutions in ${totalTime / 1000}s"
                task.complete(summary)
                resultFn(finalResult)

            } catch (e: Exception) {
                // Triple Log Rule
                log.error("Error in decomposition synthesis", e)
                task.error(e)
                transcriptStream?.let {
                    writeToTranscript(it, buildString {
                        appendLine("\n\n## ERROR")
                        appendLine("> ${e.message}")
                        appendLine("\n<details><summary>Stack Trace</summary>\n")
                        appendLine("```")
                        appendLine(e.stackTraceToString())
                        appendLine("```")
                        appendLine("\n</details>")
                    })
                }

                // Update overview with error
                overviewTask.add(buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error")
                    appendLine()
                    appendLine("**Error Type:** ${e.javaClass.simpleName}")
                    appendLine("**Message:** ${e.message ?: "Unknown error"}")
                    appendLine()
                }.renderMarkdown())


                resultFn("ERROR: ${e.message}")
            } finally {
                transcriptStream?.flush()
                transcriptStream?.close()
                log.debug("Transcript closed")
            }
        }
    }


    private fun buildContext(agent: TaskOrchestrator, root: Path): String {
        log.debug("Building context from related files and prior code")
        val priorCode = getPriorCode(agent.executionState)
        val relatedFiles = executionConfig?.related_files?.joinToString("\n") { "- $it" } ?: ""
        val fileContext = if (executionConfig?.include_file_context == true) {
            getInputFileCode(root)
        } else {
            ""
        }


        return """
            |## Context
            |
            |### Related Files
            |$relatedFiles
            |
            |### Input Files
            |$fileContext
            |
            |### Previous Task Results
            |$priorCode
        """.trimMargin()
    }

    private fun getInputFileCode(root: Path): String = (executionConfig?.input_files ?: listOf())
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

    private fun decomposeProblem(
        problem: String,
        strategy: String,
        maxDepth: Int,
        currentDepth: Int,
        context: String,
        api: ChatInterface
    ): ProblemDecomposition {
        log.debug("Decomposing problem at depth $currentDepth/$maxDepth using $strategy strategy")
        val prompt = """
            |You are an expert systems analyst. Your task is to decompose the following complex problem using a $strategy decomposition strategy.
            |
            |**Problem**: $problem
            |
            |**Context**:
            |$context
            |
            |**Decomposition Strategy**: $strategy
            |- functional: Break down by function/capability
            |- temporal: Break down by time/sequence
            |- spatial: Break down by location/component
            |- hierarchical: Break down by abstraction level
            |
            |**Current Depth**: $currentDepth / $maxDepth
            |
            |Identify 3-7 subproblems that together solve the original problem.
            |For each subproblem:
            |- Assign a unique ID (e.g., "SP1", "SP2")
            |- Provide a clear description
            |- Estimate complexity (1-10, where 10 is most complex)
            |- Indicate if it can be further decomposed (complexity > 7 and depth < maxDepth)
            |
            |Also identify dependencies between subproblems (which must be solved first).
        """.trimMargin()

        val decompositionAgent = ParsedAgent(
            resultClass = ProblemDecomposition::class.java,
            prompt = prompt,
            model = api,
            parsingChatter = defaultFast,
        )

        val decomposition = decompositionAgent.answer(listOf(problem)).obj
        // Validate the decomposition
        decomposition.validate()?.let { error ->
            log.error("Decomposition validation failed: $error")
            throw IllegalArgumentException("Invalid decomposition: $error")
        }

        return decomposition
    }

    private fun solveSubproblems(
        decomposition: ProblemDecomposition,
        context: String,
        task: SessionTask,
        api: ChatInterface,
        progressCallback: (String, SubproblemSolution) -> Unit = { _, _ -> }
    ): List<SubproblemSolution> {
        log.debug("Starting to solve ${decomposition.subproblems.size} subproblems")
        val solutions = mutableListOf<SubproblemSolution>()
        val solvedIds = mutableSetOf<String>()

        // Solve in dependency order, handling circular dependencies
        val (sortedSubproblems, circularDeps) = topologicalSortWithCycleDetection(decomposition)

        if (circularDeps.isNotEmpty()) {
            log.warn("Circular dependencies detected and resolved: ${circularDeps.joinToString(", ")}")
            task.add(buildString {
                appendLine()
                appendLine("⚠️ **Warning**: Circular dependencies detected and automatically resolved:")
                appendLine()
                circularDeps.forEach { cycle ->
                    appendLine("- $cycle")
                }
                appendLine()
                appendLine("Dependencies have been adjusted to allow execution to proceed.")
                appendLine()
            }.let { MarkdownUtil.renderMarkdown(it, ui = task.ui) })
        }

        log.info("Solving ${sortedSubproblems.size} subproblems in dependency order")

        for (subproblem in sortedSubproblems) {
            log.debug("Solving subproblem: ${subproblem.id} - ${subproblem.description}")
            val dependencySolutions = decomposition.dependencies[subproblem.id]
                ?.mapNotNull { depId -> solutions.find { it.subproblem_id == depId } }
                ?: emptyList()

            val prompt = """
                |You are a meticulous and expert problem solver. Your task is to solve the following subproblem, considering all provided context and dependencies.
                |
                |**Subproblem ID**: ${subproblem.id}
                |**Description**: ${subproblem.description}
                |**Complexity**: ${subproblem.complexity}/10
                |
                |**Context**:
                |$context
                |
                |${
                if (dependencySolutions.isNotEmpty()) {
                    """
                    |**Dependency Solutions**:
                    |${
                        dependencySolutions.joinToString("\n\n") {
                            "- ${it.subproblem_id}: ${it.solution}"
                        }
                    }
                    """.trimMargin()
                } else ""
            }
                |
                |Provide a detailed solution to this subproblem.
                |Include your confidence level (0-1) in the solution.
            """.trimMargin()

            val solutionAgent = ParsedAgent(
                resultClass = SubproblemSolution::class.java,
                prompt = prompt,
                model = api,
                parsingChatter = defaultFast,
            )

            val solution = solutionAgent.answer(listOf(subproblem.description)).obj
            // Validate the solution
            solution.validate()?.let { error ->
                log.error("Solution validation failed for ${subproblem.id}: $error")
                throw IllegalArgumentException("Invalid solution for ${subproblem.id}: $error")
            }

            val finalSolution = solution.jsonCopy().apply {
                subproblem_id = subproblem.id
            }
            solutions.add(finalSolution)
            solvedIds.add(subproblem.id)
            // Call progress callback
            progressCallback(subproblem.id, finalSolution)
        }

        return solutions
    }

    private fun topologicalSortWithCycleDetection(decomposition: ProblemDecomposition): Pair<List<Subproblem>, List<String>> {
        log.debug("Performing topological sort with cycle detection")
        val sorted = mutableListOf<Subproblem>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val circularDependencies = mutableListOf<String>()
        val brokenEdges = mutableSetOf<Pair<String, String>>()
        // Create a mutable copy of dependencies that we can modify
        val adjustedDependencies = decomposition.dependencies.mapValues { it.value.toMutableList() }.toMutableMap()

        fun visit(id: String, path: List<String> = emptyList()) {
            if (id in visited) return

            if (id in visiting) {
                // Circular dependency detected - find the cycle
                val cycleStart = path.indexOf(id)
                val cycle = path.subList(cycleStart, path.size) + id
                val cycleDesc = cycle.joinToString(" → ")
                circularDependencies.add(cycleDesc)
                log.warn("Circular dependency detected: $cycleDesc")

                // Break the cycle by removing the edge that closes the loop
                val lastInCycle = path.last()
                adjustedDependencies[lastInCycle]?.remove(id)
                brokenEdges.add(lastInCycle to id)
                log.info("Breaking dependency: $lastInCycle → $id")
                return
            }

            visiting.add(id)
            val newPath = path + id
            adjustedDependencies[id]?.forEach { depId ->
                if (depId to id !in brokenEdges) {
                    visit(depId, newPath)
                }
            }
            visiting.remove(id)
            visited.add(id)

            decomposition.subproblems.find { it.id == id }?.let { sorted.add(it) }
        }

        decomposition.subproblems.forEach { visit(it.id) }
        log.debug("Topological sort complete: ${sorted.size} subproblems, ${circularDependencies.size} cycles detected")
        return sorted to circularDependencies
    }

    private fun synthesizeSolution(
        problem: String,
        decomposition: ProblemDecomposition,
        solutions: List<SubproblemSolution>,
        context: String,
        api: ChatInterface
    ): SynthesizedSolution {
        log.debug("Synthesizing solution from ${solutions.size} subproblem solutions")
        val prompt = """
            |You are a master synthesizer of information. Your task is to create a single, coherent solution to the original problem by integrating the provided subproblem solutions.
            |
            |**Original Problem**: $problem
            |
            |**Decomposition Strategy**: ${executionConfig?.decomposition_strategy}
            |
            |**Subproblem Solutions**:
            |${
            solutions.joinToString("\n\n") { sol ->
                """
                |### ${sol.subproblem_id}
                |${decomposition.subproblems.find { it.id == sol.subproblem_id }?.description}
                |
                |**Solution**: ${sol.solution}
                |**Confidence**: ${sol.confidence}
                """.trimMargin()
            }
        }
            |
            |**Context**:
            |$context
            |
            |Create a coherent, integrated solution that:
            |1. Addresses the original problem completely
            |2. Properly integrates all subproblem solutions
            |3. Resolves any conflicts or overlaps
            |4. Provides a clear synthesis approach
            |5. Includes an overall confidence assessment
        """.trimMargin()

        val synthesisAgent = ParsedAgent(
            resultClass = SynthesizedSolution::class.java,
            prompt = prompt,
            model = api,
            parsingChatter = defaultFast,
        )

        val synthesized: SynthesizedSolution = synthesisAgent.answer(listOf(problem)).obj
        // Validate the synthesized solution
        synthesized.validate()?.let { error ->
            log.error("Synthesis validation failed: $error")
            throw IllegalArgumentException("Invalid synthesis: $error")
        }

        return synthesized!!
    }

    private fun validateCoherence(
        problem: String,
        synthesized: SynthesizedSolution,
        solutions: List<SubproblemSolution>,
        api: ChatInterface
    ): CoherenceValidation {
        log.debug("Validating coherence of synthesized solution")
        val prompt = """
            |You are a critical reviewer and quality assurance specialist. Your task is to validate the coherence of the synthesized solution.
            |
            |**Original Problem**: $problem
            |
            |**Synthesized Solution**: ${synthesized.solution}
            |
            |**Synthesis Approach**: ${synthesized.synthesis_approach}
            |
            |**Subproblem Solutions**:
            |${solutions.joinToString("\n") { "- ${it.subproblem_id}: confidence ${it.confidence}" }}
            |
            |Check for:
            |1. Logical consistency across the solution
            |2. Completeness (all aspects of the problem addressed)
            |3. Integration quality (subproblems properly combined)
            |4. Contradictions or conflicts
            |5. Missing elements or gaps
            |
            |Provide:
            |- Whether the solution is coherent (true/false)
            |- List of any issues found
            |- Suggestions for improvement
        """.trimMargin()

        val validationAgent = ParsedAgent(
            resultClass = CoherenceValidation::class.java,
            prompt = prompt,
            model = api,
            parsingChatter = defaultFast,
        )

        val validation: CoherenceValidation = validationAgent.answer(listOf(synthesized.solution)).obj
        // Validate the validation result
        validation.validate()?.let { error ->
            log.error("Validation result validation failed: $error")
            throw IllegalArgumentException("Invalid validation result: $error")
        }

        return validation!!
    }

    override fun writeToTranscript(it: FileOutputStream, buildString: String) {
        try {
            it.write(buildString.toByteArray())
            it.write("\n\n".toByteArray())
            it.flush()
        } catch (e: Exception) {
            log.error("Failed to write to transcript", e)
        }
    }

}


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/FiniteStateMachineTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FiniteStateMachineTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FiniteStateMachineTaskExecutionConfigData?
) : AbstractTask<FiniteStateMachineTask.FiniteStateMachineTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    protected val codeFiles = mutableMapOf<java.nio.file.Path, String>()
    val maxDescriptionLength = 2000

    class FiniteStateMachineTaskExecutionConfigData(
        @Description("The concept, system, or process to model as a finite state machine")
        var concept_to_model: String? = null,
        @Description("Initial state(s) to consider")
        var initial_states: List<String>? = null,
        @Description("Known events or triggers that cause state transitions")
        var known_events: List<String>? = null,
        @Description("Whether to identify edge cases and error states")
        var identify_edge_cases: Boolean = true,
        @Description("Whether to validate state machine properties (determinism, completeness, reachability)")
        var validate_properties: Boolean = true,
        @Description("Whether to generate test scenarios for state transitions")
        var generate_test_scenarios: Boolean = true,
        @Description("Domain or context for the FSM (e.g., 'authentication system', 'order processing')")
        var domain_context: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = FiniteStateMachine.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        return """
FiniteStateMachine - Model concepts using finite state machine analysis
  ** Specify the concept, system, or process to model
  ** Optionally provide initial states and known events
  ** Identify all possible states and transitions
  ** Detect edge cases and error states
  ** Validate FSM properties (determinism, completeness, reachability)
  ** Generate test scenarios for state transitions
  ** Produces state diagram and transition table
  ** Useful for:
     - System design and validation
     - Understanding complex workflows
     - Identifying missing requirements
     - Test case generation
     - Protocol analysis
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val conceptToModel = executionConfig?.concept_to_model
      val startTime = System.currentTimeMillis()




      task.ui.pool.submit {
        val transcript = task.transcript()
        try {
          log.info("Starting FiniteStateMachineTask for concept: '$conceptToModel'")
          transcript?.write("# Finite State Machine Analysis\n\n".toByteArray())
          transcript?.write("**Started:** ${LocalDateTime.now()}\n\n".toByteArray())

          if (conceptToModel.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No concept to model specified"
            log.error(errorMsg)
            task.error(Exception(errorMsg))
            resultFn(errorMsg)
            return@submit
          }

          task.ui
          val api = defaultSmart ?: throw IllegalStateException("No default chatter available")

            val domainContext = executionConfig.domain_context ?: "general domain"
            val initialStates = executionConfig.initial_states ?: emptyList()
            val knownEvents = executionConfig.known_events ?: emptyList()

            // Create tabbed display for organized output
            val tabs = TabbedDisplay(task)
            // Initialize full report builder
            val fullReport = StringBuilder()
            fullReport.append("# Finite State Machine Analysis: $conceptToModel\n\n")
            fullReport.append("**Domain:** $domainContext\n")
            fullReport.append("**Date:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")

            // Overview tab
            val overviewTask = tabs.newTask("Overview")

          transcript?.write("## Configuration\n".toByteArray())
          transcript?.write("<details><summary>Task Parameters</summary>\n\n".toByteArray())
          transcript?.write("**Concept:** $conceptToModel\n".toByteArray())
          transcript?.write("**Domain:** $domainContext\n".toByteArray())
          transcript?.write("**Initial States:** ${initialStates.joinToString(", ")}\n".toByteArray())
          transcript?.write("**Known Events:** ${knownEvents.joinToString(", ")}\n".toByteArray())
          transcript?.write("</details>\n\n".toByteArray())

            var overviewContent = overviewTask.add(
                    """
            |## Finite State Machine Analysis
            |
            |**Concept:** $conceptToModel
            |
            |**Domain:** $domainContext
            |
            |**Initial States:** ${if (initialStates.isNotEmpty()) initialStates.joinToString(", ") else "To be identified"}
            |
            |**Known Events:** ${if (knownEvents.isNotEmpty()) knownEvents.joinToString(", ") else "To be identified"}
            |
            |**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            |
            |---
            |
            |**Status:** 🔄 Analyzing concept and identifying states...
          """.trimMargin().renderMarkdown()
            )
            task.update()

            log.debug("Gathering prior context from execution state")
            val priorContext = getPriorCode(agent.executionState)
            val inputFileContent = getInputFileCode()

            // Step 1: Identify States
            log.info("Step 1: Identifying all possible states")
            val statesTask = tabs.newTask("States")

            val statesLoading = statesTask.add(
              "## State Identification\n\n🔄 Analyzing concept to identify all possible states...".renderMarkdown()
            )
            task.update()

            val stateIdentificationPrompt = buildStateIdentificationPrompt(
                conceptToModel,
                domainContext,
                initialStates,
                priorContext,
                inputFileContent
            )

            val stateAgent = ChatAgent(
                prompt = stateIdentificationPrompt,
                model = api,
                temperature = 0.3
            )

            log.debug("Requesting state identification from LLM")
            val statesAnalysis = stateAgent.answer(listOf("Identify all possible states for this concept."))
          transcript?.write("## Step 1: State Identification\n".toByteArray())
          transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$stateIdentificationPrompt\n```\n\n### Response\n\n$statesAnalysis\n</details>\n\n".toByteArray())
            fullReport.append("## 1. State Identification\n\n$statesAnalysis\n\n")

            statesLoading?.clear()
            statesTask.add(
                    """
            |## Identified States
            |
            |✅ State analysis complete
            |
            |$statesAnalysis
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Update overview
            overviewContent?.clear()
            overviewContent = overviewTask.add(
                    """
            |## Finite State Machine Analysis
            |
            |**Concept:** $conceptToModel
            |
            |**Domain:** $domainContext
            |
            |**Status:** 🔄 Identifying transitions and events...
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Step 2: Identify Transitions
            log.info("Step 2: Identifying state transitions and events")
            val transitionsTask = tabs.newTask("Transitions")

            val transitionsLoading = transitionsTask.add(
              "## Transition Analysis\n\n🔄 Identifying events and state transitions...".renderMarkdown()
            )
            task.update()

            val transitionPrompt = buildTransitionPrompt(
                statesAnalysis,
                knownEvents
            )

            log.debug("Requesting transition analysis from LLM")
            val transitionsAnalysis = stateAgent.answer(listOf(transitionPrompt))
          transcript?.write("## Step 2: Transition Analysis\n".toByteArray())
          transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$transitionPrompt\n```\n\n### Response\n\n$transitionsAnalysis\n</details>\n\n".toByteArray())
            fullReport.append("## 2. Transition Analysis\n\n$transitionsAnalysis\n\n")

            transitionsLoading?.clear()
            transitionsTask.add(
                    """
            |## State Transitions
            |
            |✅ Transition analysis complete
            |
            |$transitionsAnalysis
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Step 3: Generate State Diagram
            log.info("Step 3: Generating state diagram")
            val diagramTask = tabs.newTask("State Diagram")

            val diagramLoading = diagramTask.add(
              "## State Diagram\n\n🔄 Generating visual representation...".renderMarkdown()
            )
            task.update()

            val diagramPrompt = """
Based on the states and transitions identified above, create a Mermaid state diagram.

Use the following format:
- Use `stateDiagram-v2` for the diagram type
- Show all states clearly
- Use `-->` for transitions with event labels
- Mark initial state with `[*]`
- Mark final/terminal states with `[*]` as destination
- Use descriptive labels for transitions
- Group related states if applicable

Generate the Mermaid diagram now:
      """.trimIndent()

            log.debug("Requesting state diagram from LLM")
            val diagramResult = stateAgent.answer(listOf(diagramPrompt))
            val mermaidCode = extractMermaidCode(diagramResult)
          transcript?.write("## Step 3: State Diagram\n".toByteArray())
          transcript?.write("<details><summary>Mermaid Source</summary>\n\n".toByteArray())
            if (mermaidCode.isNotEmpty()) {
              transcript?.write("```mermaid\n$mermaidCode\n```\n".toByteArray())
            } else {
              transcript?.write("⚠️ Failed to generate diagram\n".toByteArray())
            }
          transcript?.write("</details>\n\n".toByteArray())
            if (mermaidCode.isNotEmpty()) {
                fullReport.append("## 3. State Diagram\n\n```mermaid\n$mermaidCode\n```\n\n")
            } else {
                fullReport.append("## 3. State Diagram\n\nFailed to generate diagram.\n\n")
            }

            diagramLoading?.clear()
            if (mermaidCode.isNotEmpty()) {
                diagramTask.add(
                        """
              |## State Diagram
              |
              |✅ Diagram generated successfully
              |
              |```mermaid
              |$mermaidCode
              |```
            """.trimMargin().renderMarkdown()
                )
            } else {
                diagramTask.add(
                        """
              |## State Diagram
              |
              |⚠️ Failed to generate diagram
              |
              |Raw output:
              |```
              |$diagramResult
              |```
            """.trimMargin().renderMarkdown()
                )
            }
            task.update()

            // Step 4: Edge Cases (if enabled)
            var edgeCasesAnalysis: String
            if (executionConfig.identify_edge_cases) {
                log.info("Step 4: Identifying edge cases and error states")
                val edgeCasesTask = tabs.newTask("Edge Cases")

                val edgeCasesLoading = edgeCasesTask.add(
                  "## Edge Cases Analysis\n\n🔄 Identifying edge cases and error states...".renderMarkdown()
                )
                task.update()

                val edgeCasesPrompt = """
Analyze the finite state machine for edge cases and error conditions:

1. **Invalid Transitions:** Identify events that could occur in states where they're not valid
2. **Missing Transitions:** Find states that might be missing important transitions
3. **Error States:** Identify states that represent error conditions
4. **Recovery Paths:** Suggest how to recover from error states
5. **Boundary Conditions:** Identify unusual or extreme scenarios
6. **Race Conditions:** Identify potential concurrent event issues

Provide a structured analysis of edge cases and recommendations.
        """.trimIndent()

                log.debug("Requesting edge case analysis from LLM")
                edgeCasesAnalysis = stateAgent.answer(listOf(edgeCasesPrompt))
              transcript?.write("## Step 4: Edge Cases\n".toByteArray())
              transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$edgeCasesPrompt\n```\n\n### Response\n\n$edgeCasesAnalysis\n</details>\n\n".toByteArray())
                fullReport.append("## 4. Edge Cases Analysis\n\n$edgeCasesAnalysis\n\n")

                edgeCasesLoading?.clear()
                edgeCasesTask.add(
                        """
              |## Edge Cases and Error States
              |
              |✅ Edge case analysis complete
              |
              |$edgeCasesAnalysis
            """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 5: Validation (if enabled)
            var validationAnalysis = ""
            if (executionConfig.validate_properties) {
                log.info("Step 5: Validating FSM properties")
                val validationTask = tabs.newTask("Validation")

                val validationLoading = validationTask.add(
                  "## FSM Validation\n\n🔄 Validating state machine properties...".renderMarkdown()
                )
                task.update()

                val validationPrompt = """
Validate the following properties of this finite state machine:

1. **Determinism:** Is the FSM deterministic? (Each state + event combination leads to exactly one next state)
2. **Completeness:** Are all possible events handled in every state?
3. **Reachability:** Can all states be reached from the initial state(s)?
4. **Liveness:** Are there any deadlock states (states with no outgoing transitions)?
5. **Safety:** Are there any unsafe state transitions?
6. **Minimality:** Are there redundant states that could be merged?

For each property, provide:
- ✅ Pass or ❌ Fail
- Explanation
- Specific examples if failing
- Recommendations for fixes

Provide a structured validation report.
        """.trimIndent()

                log.debug("Requesting FSM validation from LLM")
                validationAnalysis = stateAgent.answer(listOf(validationPrompt))
              transcript?.write("## Step 5: Validation\n".toByteArray())
              transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$validationPrompt\n```\n\n### Response\n\n$validationAnalysis\n</details>\n\n".toByteArray())
                fullReport.append("## 5. Property Validation\n\n$validationAnalysis\n\n")

                validationLoading?.clear()
                validationTask.add(
                        """
              |## FSM Property Validation
              |
              |✅ Validation complete
              |
              |$validationAnalysis
            """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 6: Test Scenarios (if enabled)
            var testScenariosAnalysis: String
            if (executionConfig.generate_test_scenarios) {
                log.info("Step 6: Generating test scenarios")
                val testScenariosTask = tabs.newTask("Test Scenarios")

                val testScenariosLoading = testScenariosTask.add(
                  "## Test Scenario Generation\n\n🔄 Creating test scenarios...".renderMarkdown()
                )
                task.update()

                val testScenariosPrompt = """
Generate comprehensive test scenarios for this finite state machine:

1. **Happy Path Tests:** Normal sequences of events leading to successful outcomes
2. **Error Path Tests:** Sequences that should trigger error states
3. **Boundary Tests:** Edge cases and unusual sequences
4. **State Coverage Tests:** Scenarios that exercise all states
5. **Transition Coverage Tests:** Scenarios that exercise all transitions

For each test scenario, provide:
- **Scenario Name:** Descriptive name
- **Initial State:** Starting state
- **Event Sequence:** List of events in order
- **Expected States:** State after each event
- **Expected Outcome:** Final state and result
- **Purpose:** What this test validates

Generate at least 5-10 diverse test scenarios.
        """.trimIndent()

                log.debug("Requesting test scenario generation from LLM")
                testScenariosAnalysis = stateAgent.answer(listOf(testScenariosPrompt))
              transcript?.write("## Step 6: Test Scenarios\n".toByteArray())
              transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$testScenariosPrompt\n```\n\n### Response\n\n$testScenariosAnalysis\n</details>\n\n".toByteArray())
                fullReport.append("## 6. Test Scenarios\n\n$testScenariosAnalysis\n\n")

                testScenariosLoading?.clear()
                testScenariosTask.add(
                        """
              |## Test Scenarios
              |
              |✅ Test scenarios generated
              |
              |$testScenariosAnalysis
            """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 7: Generate Summary
            log.info("Step 7: Generating comprehensive summary")
            val summaryTask = tabs.newTask("Summary")

            val summaryLoading = summaryTask.add(
              "## Summary\n\n🔄 Generating comprehensive summary...".renderMarkdown()
            )
            task.update()

            val summaryPrompt = """
Provide a comprehensive summary of the finite state machine analysis:

1. **Overview:** Brief description of the FSM and its purpose
2. **Key States:** List the most important states (3-5)
3. **Critical Transitions:** Highlight the most important state transitions
4. **Key Findings:** Main insights from the analysis
5. **Recommendations:** Top 3-5 actionable recommendations for improvement
6. **Complexity Assessment:** Evaluate the complexity of this FSM

Keep the summary concise but informative.
      """.trimIndent()

            log.debug("Requesting summary from LLM")
            val summaryAnalysis = stateAgent.answer(listOf(summaryPrompt))
          transcript?.write("## Step 7: Summary\n".toByteArray())
          transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$summaryPrompt\n```\n\n### Response\n\n$summaryAnalysis\n</details>\n\n".toByteArray())
            fullReport.append("## 7. Summary\n\n$summaryAnalysis\n\n")

            summaryLoading?.clear()
            summaryTask.add(
                    """
            |## Analysis Summary
            |
            |✅ Summary complete
            |
            |$summaryAnalysis
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Final overview update
            overviewContent?.clear()
            val totalTime = System.currentTimeMillis() - startTime
            overviewTask.add(
                    """
            |## Finite State Machine Analysis
            |
            |**Concept:** $conceptToModel
            |
            |**Domain:** $domainContext
            |
            |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            |
            |**Duration:** ${totalTime / 1000.0}s
            |
            |---
            |
            |**Status:** ✅ Analysis complete
            |
            |### Analysis Components:
            |- ✅ State identification
            |- ✅ Transition analysis
            |- ✅ State diagram
            |${if (executionConfig.identify_edge_cases) "- ✅ Edge case analysis" else ""}
            |${if (executionConfig.validate_properties) "- ✅ Property validation" else ""}
            |${if (executionConfig.generate_test_scenarios) "- ✅ Test scenario generation" else ""}
            |- ✅ Summary and recommendations
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Build concise result for task dependencies
            val conciseResult = buildString {
                appendLine("# FSM Analysis: $conceptToModel")
                appendLine()
                if (summaryAnalysis.isNotBlank()) {
                    appendLine("## Summary")
                    appendLine(summaryAnalysis.smartTruncate(maxDescriptionLength))
                    appendLine()
                } else {
                    appendLine("## Summary")
                    appendLine("Analysis completed successfully.")
                    appendLine()
                }
                appendLine("## Key Components")
                appendLine("- States identified and analyzed")
                appendLine("- Transitions mapped")
                appendLine("- State diagram generated")
                if (executionConfig.identify_edge_cases) appendLine("- Edge cases identified")
                if (executionConfig.validate_properties) appendLine("- Properties validated")
                if (executionConfig.generate_test_scenarios) appendLine("- Test scenarios generated")
            }

            log.info("FiniteStateMachineTask completed: concept='$conceptToModel', duration=${totalTime}ms, output_size=${conciseResult.length} chars")

          transcript?.write("## Completion\n\n**Status:** ✅ Analysis complete\n".toByteArray())
          transcript?.write("**Duration:** ${totalTime / 1000.0}s\n".toByteArray())

          val (link, file) = task.createFile("fsm_analysis.md")
            file?.writeText(fullReport.toString())

            var mmdLink = ""
            if (mermaidCode.isNotEmpty()) {
                val (l, f) = task.createFile("fsm_diagram.mmd")
                f?.writeText(mermaidCode)
                mmdLink = l
            }

          task.complete(
                "FSM analysis completed for: $conceptToModel. " +
                        "Full analysis written to <a href='$link' target='_blank'>$link</a> " +
                    (if (mmdLink.isNotEmpty()) " <a href='$mmdLink' target='_blank'>Mermaid Diagram</a>" else "")
            )
            resultFn(conciseResult)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("FiniteStateMachineTask failed after ${duration}ms for concept: $conceptToModel", e)
          transcript?.write("## Error\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
            task.error(e)
            resultFn("ERROR: FSM analysis failed - ${e.message}")
        } finally {
          transcript?.close()
        }
        }
    }

    private fun buildStateIdentificationPrompt(
        concept: String,
        domain: String,
        initialStates: List<String>,
        priorContext: String,
        inputFileContent: String
    ): String {
        val fileContentSection = if (inputFileContent.isNotBlank()) {
            """
        |
        |## Reference Files:
        |$inputFileContent
      """.trimMargin()
        } else {
            ""
        }
        val initialStatesSection = if (initialStates.isNotEmpty()) {
            """
$fileContentSection
        |
        |## Known Initial States:
        |${initialStates.joinToString("\n") { "- $it" }}
      """.trimMargin()
        } else {
            ""
        }

        val contextSection = if (priorContext.isNotBlank()) {
            """
        |
        |## Context from Previous Tasks:
        |$priorContext
      """.trimMargin()
        } else {
            ""
        }

        return """
You are an expert in formal methods and finite state machine modeling. Your task is to analyze a concept and identify all possible states.

## Concept to Model:
$concept

## Domain Context:
$domain
$initialStatesSection
$contextSection

## Instructions:
Identify all possible states for this concept. For each state, provide:

1. **State Name:** Clear, descriptive name
2. **Description:** What this state represents
3. **Type:** (Initial, Normal, Error, Final/Terminal)
4. **Invariants:** Conditions that must be true in this state
5. **Entry Conditions:** What must happen to enter this state
6. **Exit Conditions:** What can cause leaving this state

Consider:
- Normal operational states
- Error or exception states
- Transient vs stable states
- Initial and final states

Provide a comprehensive list of states with detailed descriptions.
    """.trimIndent()
    }

    private fun buildTransitionPrompt(
        statesAnalysis: String,
        knownEvents: List<String>
    ): String {
        val eventsSection = if (knownEvents.isNotEmpty()) {
            """
        |
        |## Known Events:
        |${knownEvents.joinToString("\n") { "- $it" }}
      """.trimMargin()
        } else {
            ""
        }

        return """
Based on the states identified above, now identify all possible state transitions.

## States Analysis:
$statesAnalysis
$eventsSection

## Instructions:
For each possible transition, provide:

1. **Source State:** The state before the transition
2. **Event/Trigger:** What causes the transition
3. **Target State:** The state after the transition
4. **Guard Conditions:** Conditions that must be true for the transition to occur
5. **Actions:** Side effects or actions performed during the transition
6. **Priority:** If multiple transitions possible from same state

Create a comprehensive transition table covering:
- All valid transitions between states
- Self-transitions (state to itself)
- Error transitions
- Recovery transitions

Format as a clear table or structured list.
    """.trimIndent()
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
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
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp",
            "h", "hpp", "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: File) = try {
        file.getDocumentReader().use { reader ->
            when (reader) {
                is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                else -> reader.getText()
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
        try {
            file.readText()
        } catch (e2: Exception) {
            "Error reading file: ${e2.message}"
        }
    }


    private fun extractMermaidCode(response: String): String {
        // Try to extract mermaid code block
        val mermaidBlockRegex = "```mermaid\\s*([\\s\\S]*?)```".toRegex()
        val match = mermaidBlockRegex.find(response)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Try to extract stateDiagram-v2 directly
        val stateDiagramRegex = "(stateDiagram-v2[\\s\\S]*?)(?=```|$)".toRegex()
        val stateDiagramMatch = stateDiagramRegex.find(response)
        if (stateDiagramMatch != null) {
            return stateDiagramMatch.groupValues[1].trim()
        }

        return ""
    }
    private fun String.smartTruncate(maxLength: Int): String {
        if (length <= maxLength) return this
        val truncated = take(maxLength)
        val lastNewline = truncated.lastIndexOf('\n')
        return if (lastNewline > maxLength / 2) {
            truncated.substring(0, lastNewline) + "\n...(truncated)"
        } else {
            truncated + "..."
        }
    }




    companion object {
        private val log: Logger = LoggerFactory.getLogger(FiniteStateMachineTask::class.java)

        @JvmStatic val FiniteStateMachine = TaskType(
          name = "FiniteStateMachine",
          category = "Reasoning",
          taskClass = FiniteStateMachineTask::class.java,
          executionConfigClass = FiniteStateMachineTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Model concepts using finite state machine analysis",
          tooltipHtml = """
                  Analyzes concepts, systems, or processes using finite state machine modeling.
                  <ul>
                    <li>Identifies all possible states and their properties</li>
                    <li>Maps state transitions and triggering events</li>
                    <li>Generates visual state diagrams</li>
                    <li>Identifies edge cases and error states</li>
                    <li>Validates FSM properties (determinism, completeness, reachability)</li>
                    <li>Generates comprehensive test scenarios</li>
                    <li>Useful for system design, protocol analysis, and workflow validation</li>
                  </ul>
                """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/FunctorialMappingTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

class FunctorialMappingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FunctorialMappingTaskExecutionConfigData?
) : AbstractTask<FunctorialMappingTask.FunctorialMappingTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FunctorialMappingTask::class.java)
        @JvmStatic val FunctorialMapping = TaskType(
          name = "FunctorialMapping",
          category = "Reasoning",
          taskClass = FunctorialMappingTask::class.java,
          executionConfigClass = FunctorialMappingTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Solve complex problems by abstracting them into Category Theory and mapping them to domains with superior tools.",
          tooltipHtml = """
                          This task implements the logic of Category Theory. It treats domains as "Categories" (collections of objects and arrows/morphisms).
                          The goal is to construct a "Functor"—a bridge that allows you to transport a difficult problem from Domain A to Domain B, solve it there, and transport the solution back.
                          <ul>
                            <li>Formalize source and target domains as Categories</li>
                            <li>Construct a Functor F mapping objects and morphisms</li>
                            <li>Transport the problem statement via F</li>
                            <li>Solve the problem in the target category</li>
                            <li>Inverse transport the solution back to the source</li>
                          </ul>
                        """,
        )
    }

    class FunctorialMappingTaskExecutionConfigData(
        @Description("The specific problem in the Source Category")
        val problem_statement: String? = null,
        @Description("The rules of the current domain (Source Category)")
        val source_category_definition: String? = null,
        @Description("The rules of the destination domain (Target Category)")
        val target_category_definition: String? = null,
        @Description("Constraints on the mapping (e.g., 'covariant', 'contravariant')")
        val functor_properties: String? = "covariant",

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = FunctorialMapping.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (problem_statement.isNullOrBlank()) return "problem_statement must not be blank"
            if (source_category_definition.isNullOrBlank()) return "source_category_definition must not be blank"
            if (target_category_definition.isNullOrBlank()) return "target_category_definition must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
FunctorialMapping - Translate problems from one category to another
  * problem_statement: The specific problem in the Source Category.
  * source_category_definition: The rules/objects of the current domain.
  * target_category_definition: The rules/objects of the destination domain.
  * functor_properties: Constraints on the mapping (e.g., 'covariant').
  * Use this for high-level reasoning, cross-domain analogies, and mathematical problem solving.
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
        log.info("Starting FunctorialMappingTask")

        executionConfig?.validate()?.let { errorMessage ->
            log.error("Configuration validation failed: $errorMessage")
            task.error(RuntimeException("VALIDATION ERROR: $errorMessage"))
            resultFn("VALIDATION ERROR: $errorMessage")
            return
        }

        val problem = executionConfig?.problem_statement!!
        val sourceDef = executionConfig?.source_category_definition!!
        val targetDef = executionConfig?.target_category_definition!!
        val properties = executionConfig?.functor_properties ?: "covariant"

        val api = defaultSmart
        val tabs = TabbedDisplay(task)
        val transcript = task.newLogStream("Functorial Mapping Transcript")

        // Overview Tab
        val overviewTask = tabs.newTask("Overview")

        overviewTask.header("Functorial Mapping Task")
        overviewTask.add(
            """
            <b>Problem:</b> $problem<br/>
            <b>Source Category:</b> $sourceDef<br/>
            <b>Target Category:</b> $targetDef<br/>
            <b>Properties:</b> $properties
        """.trimIndent()
        )
        overviewTask.complete()

        try {
            // Step 1: Category Definition
            val step1Task = tabs.newTask("1. Categories")
            step1Task.header("Formalizing Categories...", level = 3)

            val categoryPrompt = """
                You are a Category Theory expert.
                Formalize the following domains as Categories (Objects and Morphisms).
                
                **Source Domain:**
                $sourceDef
                
                **Target Domain:**
                $targetDef
                
                Output a structured description of the Objects and Morphisms for both categories.
                Use mathematical notation where appropriate.
            """.trimIndent()

            val categories = ChatAgent(
                model = api,
                temperature = 0.3,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(categoryPrompt))
            step1Task.add(MarkdownUtil.renderMarkdown(categories, ui = step1Task.ui))
            step1Task.complete()
            transcript.write("\n## Categories\n\n$categories\n".toByteArray())

            // Step 2: Functor Construction
            val step2Task = tabs.newTask("2. Functor")
            step2Task.header("Constructing Functor...", level = 3)

            val functorPrompt = """
                You are a Category Theory expert.
                Based on the category definitions:
                
                $categories
                
                Construct a Functor F from the Source Category to the Target Category.
                Properties: $properties
                
                1. Define how F maps Objects from Source to Target.
                2. Define how F maps Morphisms from Source to Target.
                3. Explain why this mapping is a valid functor (preserves identity and composition).
            """.trimIndent()

            val functor = ChatAgent(
                model = api,
                temperature = 0.4,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(functorPrompt))
            step2Task.add(MarkdownUtil.renderMarkdown(functor, ui = step2Task.ui))
            step2Task.complete()
            transcript.write("\n## Functor\n\n$functor\n".toByteArray())

            // Step 3: Problem Transport
            val step3Task = tabs.newTask("3. Transport")
            step3Task.header("Transporting Problem...", level = 3)

            val transportPrompt = """
                You are a Category Theory expert.
                Using the Functor F defined as:
                
                $functor
                
                Transport the following problem from the Source Category to the Target Category.
                
                **Original Problem:**
                $problem
                
                Express the problem strictly in terms of the Target Category's objects and morphisms.
            """.trimIndent()

            val transportedProblem = ChatAgent(
                model = api,
                temperature = 0.3,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(transportPrompt))
            step3Task.add(MarkdownUtil.renderMarkdown(transportedProblem, ui = step3Task.ui))
            step3Task.complete()
            transcript.write("\n## Transported Problem\n\n$transportedProblem\n".toByteArray())

            // Step 4: Remote Solution
            val step4Task = tabs.newTask("4. Solution")
            step4Task.header("Solving in Target Category...", level = 3)

            val solvePrompt = """
                You are an expert in the Target Domain defined earlier.
                Solve the following problem using tools and reasoning appropriate for this domain.
                
                **Problem (in Target Category):**
                $transportedProblem
                
                Provide a detailed solution and the final result.
            """.trimIndent()

            val targetSolution = ChatAgent(
                model = api,
                temperature = 0.5,
                prompt = "You are an expert in the Target Domain."
            ).answer(listOf(solvePrompt))
            step4Task.add(MarkdownUtil.renderMarkdown(targetSolution, ui = step4Task.ui))
            step4Task.complete()
            transcript.write("\n## Target Solution\n\n$targetSolution\n".toByteArray())

            // Step 5: Inverse Transport
            val step5Task = tabs.newTask("5. Result")
            step5Task.header("Mapping Solution Back...", level = 3)

            val inversePrompt = """
                You are a Category Theory expert.
                We have solved the problem in the Target Category. Now map the solution back to the Source Category.
                
                **Functor:**
                $functor
                
                **Target Solution:**
                $targetSolution
                
                **Original Problem:**
                $problem
                
                Interpret the target solution in the context of the original problem. 
                If the functor is not strictly invertible, provide the best interpretation or adjoint mapping.
                State the final answer clearly.
            """.trimIndent()

            val finalResult = ChatAgent(
                model = api,
                temperature = 0.3,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(inversePrompt))
            step5Task.add(MarkdownUtil.renderMarkdown(finalResult, ui = step5Task.ui))
            step5Task.complete()
            transcript.write("\n## Final Result\n\n$finalResult\n".toByteArray())
            transcript.close()

            task.complete()
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error in FunctorialMappingTask", e)
            task.error(e)
            transcript.close()
            resultFn("ERROR: ${e.message}")
        }
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/GeneticOptimizationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.math.max
import kotlin.math.min

class GeneticOptimizationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GeneticOptimizationTaskExecutionConfigData?
) : AbstractTask<GeneticOptimizationTask.GeneticOptimizationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(GeneticOptimizationTask::class.java)
        @JvmStatic val GeneticOptimization = TaskType(
            name = "GeneticOptimization",
            category = "Reasoning",
            taskClass = GeneticOptimizationTask::class.java,
            executionConfigClass = GeneticOptimizationTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Iteratively evolve and perfect text through genetic algorithms",
            tooltipHtml = """
                        Uses genetic algorithms to optimize text through iterative evolution.
                        <ul>
                          <li>Generates variations using configurable mutation strategies</li>
                          <li>Evaluates variants against optimization criteria</li>
                          <li>Selects top performers for next generation</li>
                          <li>Applies crossover to combine successful traits</li>
                          <li>Tracks fitness progression across generations</li>
                          <li>Provides detailed analysis of evolution</li>
                          <li>Supports custom evaluation criteria and weights</li>
                          <li>Useful for perfecting prompts, copy, documentation, and messaging</li>
                        </ul>
                      """,
        )
        private const val TT = """```"""
        fun compressedStringBits(str: String): Int {
            val byteStream = ByteArrayOutputStream()
            val gzipStream = GZIPOutputStream(byteStream)
            gzipStream.write(str.toByteArray(Charsets.UTF_8))
            gzipStream.close()
            return byteStream.size() * 8 // bits
        }

        /**
         * Calculates the compressibility between two strings based on their compressed sizes.
         * 1 -> incompressible (high diversity)
         * 2 -> duplicate (low diversity)
         */
        fun compressibility(strA: String, strB: String): Double =
            (compressedStringBits(strA) + compressedStringBits(strA)).toDouble() / compressedStringBits(strA + strB).toDouble()
    }

    class GeneticOptimizationTaskExecutionConfigData(
        @Description("The initial text(s) to optimize (seeds for genetic algorithm) - Include the ENTIRE text(s) to be optimized. Multiple texts will be used as separate seeds in the initial population.")
        var initial_text: List<String>? = null,
        @Description("The optimization goal or criteria (e.g., 'clarity and conciseness', 'persuasiveness', 'technical accuracy')")
        var optimization_goal: String? = null,
        @Description("Evaluation criteria weights (e.g., {'clarity': 0.4, 'conciseness': 0.3, 'impact': 0.3})")
        var evaluation_weights: Map<String, Double>? = null,
        @Description("Additional context or constraints for optimization")
        var constraints: List<String>? = null,

        @Description("Number of generations to evolve (default: 5)")
        var num_generations: Int = 5,
        @Description("Population size per generation (default: 6)")
        var population_size: Int = 6,
        @Description("Number of top candidates to keep each generation (default: 2)")
        var selection_size: Int = 2,
        @Description("Mutation strategies to use (e.g., 'rephrase', 'simplify', 'elaborate', 'restructure')")
        var mutation_strategies: List<String>? = listOf("rephrase", "simplify", "elaborate"),
        @Description("Whether to enable crossover (combining traits from multiple candidates)")
        var enable_crossover: Boolean = true,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = GeneticOptimization.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (initial_text.isNullOrEmpty()) {
                return "initial_text must not be empty"
            }
            if (initial_text!!.any { it.isBlank() }) {
                return "initial_text entries must not be blank"
            }
            if (optimization_goal.isNullOrBlank()) {
                return "optimization_goal must not be blank"
            }
            if (num_generations < 1) {
                return "num_generations must be at least 1"
            }
            if (population_size < 2) {
                return "population_size must be at least 2"
            }
            if (selection_size < 1 || selection_size >= population_size) {
                return "selection_size must be between 1 and population_size-1"
            }
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class TextVariant(
        @Description("The text variant")
        val text: String = "",
        @Description("Brief explanation of what changed from parent")
        val mutation_description: String = "",
        @Description("The mutation strategy used")
        val strategy: String = ""
    )

    data class EvaluationScore(
        @Description("Overall fitness score (0-100)")
        val overall_score: Double = 0.0,
        @Description("Breakdown of scores by criteria")
        val criteria_scores: Map<String, Double> = emptyMap(),
        @Description("Strengths of this variant")
        val strengths: List<String> = emptyList(),
        @Description("Weaknesses or areas for improvement")
        val weaknesses: List<String> = emptyList(),
        @Description("Brief justification for the score")
        val justification: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (overall_score < 0.0 || overall_score > 100.0) {
                return "overall_score must be between 0 and 100"
            }
            criteria_scores.forEach { (criterion, score) ->
                if (score < 0.0 || score > 100.0) {
                    return "criteria_scores[$criterion] must be between 0 and 100"
                }
            }
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class EvaluatedVariant(
        val text: String = "",
        val score: EvaluationScore = EvaluationScore(),
        val generation: Int = 0,
        val parentIndex: Int? = null,
        val strategy: String = "",
        val diversityScore: Double = 0.0
    )

    override fun promptSegment(): String {
        return """
GeneticOptimization - Iteratively evolve and perfect text through genetic algorithms
  - Specify the FULL text(s) items to optimize
  - Define the optimization goal (e.g., clarity, persuasiveness)
  - Configure number of generations (default: 5)
  - Set population size and selection size
  - Choose mutation strategies (rephrase, simplify, elaborate, restructure)
  - Enable/disable crossover for combining traits
  - Define evaluation criteria and weights
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        task.ui.pool.submit {
            val transcript = task.transcript()
            try {
                val startTime = System.currentTimeMillis()
                log.info("Starting GeneticOptimizationTask. Goal: ${executionConfig?.optimization_goal}")

                // Validate configuration
            executionConfig?.validate()?.let { errorMessage ->
                val error = ValidatedObject.ValidationError(errorMessage, executionConfig)
                handleError(error, task, transcript, resultFn)
                return@submit
            }


            val initialText = executionConfig?.initial_text
            val optimizationGoal = executionConfig?.optimization_goal
            val numGenerations = executionConfig?.num_generations ?: 5
            val populationSize = executionConfig?.population_size ?: 6
            val selectionSize = min(executionConfig?.selection_size ?: 2, populationSize / 2)
            val mutationStrategies = executionConfig?.mutation_strategies ?: listOf("rephrase", "simplify", "elaborate")
            val enableCrossover = executionConfig?.enable_crossover ?: true
            val evaluationWeights = executionConfig?.evaluation_weights ?: mapOf(
                "clarity" to 0.35,
                "conciseness" to 0.25,
                "impact" to 0.25,
                "goal_alignment" to 0.15
            )
            val constraints = executionConfig?.constraints ?: emptyList()

            if (initialText.isNullOrEmpty() || optimizationGoal.isNullOrBlank()) {
                val error = RuntimeException("Configuration error: initial_text is empty or optimization_goal is blank")
                handleError(error, task, transcript, resultFn)
                return@submit
            }

            log.info("Configuration validated: generations=$numGenerations, population=$populationSize, selection=$selectionSize, crossover=$enableCrossover")

            val tabs = TabbedDisplay(task)
            val api = defaultSmart
            transcript?.write("# Genetic Optimization Task Transcript\n\n".toByteArray())

            // Create overview tab
            val overviewTask = tabs.newTask("Overview")
            val overviewContent = buildString {
                appendLine("# Genetic Optimization Task")
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
                appendLine("| Optimization Goal | $optimizationGoal |")
                appendLine("| Generations | $numGenerations |")
                appendLine("| Population Size | $populationSize |")
                appendLine("| Selection Size | $selectionSize |")
                appendLine("| Mutation Strategies | ${mutationStrategies.joinToString(", ")} |")
                appendLine("| Crossover | ${if (enableCrossover) "✓ Enabled" else "✗ Disabled"} |")
                appendLine()
                appendLine("## Evaluation Criteria")
                appendLine()
                evaluationWeights.forEach { (criterion, weight) ->
                    appendLine("- **$criterion**: ${String.format("%.0f%%", weight * 100)}")
                }
                if (constraints.isNotEmpty()) {
                    appendLine()
                    appendLine("## Constraints")
                    appendLine()
                    constraints.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("## Initial Text")
                initialText.forEachIndexed { i, text ->
                    appendLine("<details><summary>Seed ${i + 1}</summary>\n\n$TT\n$text\n$TT\n</details>")
                }
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Initializing population...")
            }
            overviewTask.add(overviewContent.renderMarkdown(true))
            transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

            // Gather context

            overviewTask.add(buildString {
              appendLine()
                        }.renderMarkdown(true))
            // Initialize population with the seed texts
            log.info("Evaluating ${initialText.size} initial texts")
            var currentPopulation = initialText.mapIndexed { index, text ->
                EvaluatedVariant(
                    text = text,
                    score = EvaluationScore(overall_score = 0.0),
                    generation = 0,
                    strategy = "seed",
                    diversityScore = 1.0
                )
            }

            // Evaluate all initial texts
            currentPopulation = currentPopulation.map { variant ->
                val evaluation = evaluateVariant(variant.text, optimizationGoal, evaluationWeights, constraints, api)
                variant.copy(score = evaluation, diversityScore = 1.0)
            }

            // Log and display initial evaluations
            transcript?.write("\n\n## Initial Evaluations\n\n".toByteArray(StandardCharsets.UTF_8))
            currentPopulation.forEachIndexed { index, variant ->
                log.info("Initial text ${index + 1} evaluated. Score: ${variant.score.overall_score}")

                val evalText = buildString {
                    if (currentPopulation.size > 1) {
                        appendLine("### Seed ${index + 1}")
                        appendLine()
                    }
                    appendLine("**Score:** ${String.format("%.1f", variant.score.overall_score)}/100")
                    appendLine()
                    appendLine("**Strengths:**")
                    variant.score.strengths.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("**Weaknesses:**")
                    variant.score.weaknesses.forEach { appendLine("- $it") }
                    appendLine()
                }
                transcript?.write(evalText.toByteArray(StandardCharsets.UTF_8))
            }

            // Update overview with initial scores
            val initialScores = currentPopulation.map { it.score.overall_score }
            overviewTask.add(buildString {
              appendLine()
              if (currentPopulation.size == 1) {
                appendLine("- ✓ Initial evaluation: **${String.format("%.1f", initialScores[0])}/100**")
              } else {
                appendLine("- ✓ Initial evaluations:")
                appendLine("  - Best: **${String.format("%.1f", initialScores.maxOrNull() ?: 0.0)}/100**")
                appendLine("  - Average: **${String.format("%.1f", initialScores.average())}/100**")
                appendLine("  - Worst: **${String.format("%.1f", initialScores.minOrNull() ?: 0.0)}/100**")
              }
              appendLine("- ⏳ Starting evolution...")
                        }.renderMarkdown(true))

            // Track best variant across all generations
            var bestVariant = currentPopulation[0]
            val evolutionHistory = mutableListOf<List<EvaluatedVariant>>()
            evolutionHistory.add(currentPopulation)

            // Evolution loop
            for (generation in 1..numGenerations) {
                log.info("Starting generation $generation of $numGenerations")

                val generationTask = tabs.newTask("Generation $generation")
                transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
                transcript?.write("# Generation $generation\n\n".toByteArray(StandardCharsets.UTF_8))
                generationTask.add(buildString {
                  appendLine("# Generation $generation")
                  appendLine()
                  appendLine("**Status:** In Progress")
                  appendLine()
                  appendLine("Generating $populationSize variants...")
                                }.renderMarkdown(true))

// Step 1: Generate new variants
                val newVariants = mutableListOf<EvaluatedVariant>()

                // Keep top performers from previous generation
                val survivors = currentPopulation.sortedByDescending { it.score.overall_score }.take(selectionSize)
                // Track all texts in current generation to prevent duplicates
                val existingTexts = survivors.map { it.text }.toMutableSet()


                // Generate mutations from survivors
                val mutationsNeeded = populationSize - survivors.size
                val mutationsPerSurvivor = max(1, mutationsNeeded / survivors.size)

                survivors.forEachIndexed { survivorIndex, survivor ->
                    val mutationsToGenerate = if (survivorIndex == survivors.size - 1) {
                        // Last survivor gets any remaining slots
                        mutationsNeeded - (mutationsPerSurvivor * (survivors.size - 1))
                    } else {
                        mutationsPerSurvivor
                    }

                    repeat(mutationsToGenerate) {
                        val strategy = mutationStrategies.random()
                        newVariants.add(
                            EvaluatedVariant(
                                score = EvaluationScore(overall_score = 0.0),
                                generation = generation,
                            )
                        )
                        // Try up to 3 times to generate a unique variant
                        var attempts = 0
                        var mutated: TextVariant? = null
                        while (attempts < 3 && mutated == null) {
                            val candidate = generateMutation(
                                survivor.text,
                                survivor.score,
                                strategy,
                                optimizationGoal,
                                constraints,
                                "", // Context handled by agent state
                                api
                            )
                            if (candidate != null && !existingTexts.contains(candidate.text)) {
                                mutated = candidate
                                existingTexts.add(candidate.text)
                            }
                            attempts++
                        }

                        if (mutated != null) {
                            // Calculate diversity score based on compressibility with existing population
                            val diversityScore = calculateDiversityScore(mutated.text, existingTexts.toList())

                            newVariants.add(
                                EvaluatedVariant(
                                    text = mutated.text,
                                    score = EvaluationScore(overall_score = 0.0),
                                    generation = generation,
                                    parentIndex = survivorIndex,
                                    strategy = strategy,
                                    diversityScore = diversityScore
                                )
                            )
                        }
                    }
                }

                if (enableCrossover && survivors.size >= 2 && newVariants.size < populationSize) {

                    // Try up to 3 times to generate a unique crossover
                    var attempts = 0
                    var crossoverVariant: String? = null
                    while (attempts < 3 && crossoverVariant == null) {
                        val candidate = applyCrossover(
                            survivors[0].text,
                            survivors[0].score,
                            survivors[1].text,
                            survivors[1].score,
                            optimizationGoal,
                            constraints,
                            api
                        )
                        if (candidate != null && !existingTexts.contains(candidate)) {
                            crossoverVariant = candidate
                            existingTexts.add(candidate)
                        }
                        attempts++
                    }

                    if (crossoverVariant != null) {
                        val diversityScore = calculateDiversityScore(crossoverVariant, existingTexts.toList())

                        newVariants.add(
                            EvaluatedVariant(
                                text = crossoverVariant,
                                score = EvaluationScore(overall_score = 0.0),
                                generation = generation,
                                strategy = "crossover",
                                diversityScore = diversityScore
                            )
                        )
                    }
                }

                // Combine survivors and new variants
                currentPopulation = survivors + newVariants

// Step 2: Evaluate all variants
                currentPopulation = currentPopulation.map { variant ->
                    if (variant.score.overall_score == 0.0) {
                        val evaluation = evaluateVariant(
                            variant.text,
                            optimizationGoal,
                            evaluationWeights,
                            constraints,
                            api
                        )
                        // Combine fitness score with diversity bonus (10% weight on diversity)
                        val adjustedScore = evaluation.copy(
                            overall_score = evaluation.overall_score * 0.9 + variant.diversityScore * 10.0
                        )
                        variant.copy(score = adjustedScore)
                    } else {
                        variant
                    }
                }
                // Log diversity statistics
                val avgDiversity = currentPopulation.map { it.diversityScore }.average()

                evolutionHistory.add(currentPopulation)

                // Update best variant
                val generationBest = currentPopulation.maxByOrNull { it.score.overall_score }!!
                if (generationBest.score.overall_score > bestVariant.score.overall_score) {
                    bestVariant = generationBest
                }

                // Display generation results
                val generationResults = buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## Generation $generation Results")
                    appendLine()
                    appendLine("**Status:** ✓ Complete")
                    appendLine()
                    appendLine("### Population Statistics")
                    appendLine()
                    val scores = currentPopulation.map { it.score.overall_score }
                    appendLine("- **Best Score:** ${String.format("%.1f", scores.maxOrNull() ?: 0.0)}/100")
                    appendLine("- **Average Score:** ${String.format("%.1f", scores.average())}/100")
                    appendLine("- **Worst Score:** ${String.format("%.1f", scores.minOrNull() ?: 0.0)}/100")
                    val diversityScores = currentPopulation.map { it.diversityScore }
                    appendLine("- **Average Diversity:** ${String.format("%.3f", diversityScores.average())}")
                    appendLine(
                        "- **Improvement:** ${
                            String.format(
                                "%.1f",
                                (generationBest.score.overall_score - survivors[0].score.overall_score)
                            )
                        }"
                    )
                    appendLine()
                    appendLine("### Top Variants")
                    appendLine()
                    currentPopulation.sortedByDescending { it.score.overall_score }.take(3)
                        .forEachIndexed { index, variant ->
                            appendLine(
                                "#### ${index + 1}. Score: ${
                                    String.format(
                                        "%.1f",
                                        variant.score.overall_score
                                    )
                                }/100 (${variant.strategy})"
                            )
                            appendLine("<details><summary>View Variant Text</summary>\n\n$TT")
                            appendLine(variant.text)
                            appendLine("$TT\n</details>")
                            appendLine("$TT")
                            appendLine()
                            appendLine("**Strengths:**")
                            variant.score.strengths.forEach { appendLine("- $it") }
                            appendLine()
                            if (variant.score.weaknesses.isNotEmpty()) {
                                appendLine("**Weaknesses:**")
                                variant.score.weaknesses.forEach { appendLine("- $it") }
                                appendLine()
                            }
                            appendLine("**Criteria Breakdown:**")
                            variant.score.criteria_scores.forEach { (criterion, score) ->
                                appendLine("- $criterion: ${String.format("%.1f", score)}/100")
                            }
                            appendLine()
                            appendLine("---")
                            appendLine()
                        }
                }
                generationTask.add(generationResults.renderMarkdown(true))
                transcript?.write(generationResults.toByteArray(StandardCharsets.UTF_8))

                // Update overview
                overviewTask.add(buildString {
                  appendLine()
                  appendLine(
                    "- ✓ Generation $generation: Best=${
                      String.format(
                        "%.1f",
                        generationBest.score.overall_score
                      )
                    }, Avg=${String.format("%.1f", currentPopulation.map { it.score.overall_score }.average())}"
                  )
                                }.renderMarkdown(true))
            }

            // Create evolution visualization tab
            val evolutionTask = tabs.newTask("Evolution Analysis")
            val evolutionAnalysis = buildString {
                appendLine("# Evolution Analysis")
                appendLine()
                appendLine("## Fitness Progression")
                appendLine()
                appendLine("| Generation | Best Score | Average Score | Improvement |")
                appendLine("|------------|------------|---------------|-------------|")
                evolutionHistory.forEachIndexed { index, population ->
                    val scores = population.map { it.score.overall_score }
                    val improvement = if (index > 0) {
                        scores.maxOrNull()!! - evolutionHistory[index - 1].maxOf { it.score.overall_score }
                    } else {
                        0.0
                    }
                    appendLine(
                        "| $index | ${String.format("%.1f", scores.maxOrNull() ?: 0.0)} | ${
                            String.format(
                                "%.1f",
                                scores.average()
                            )
                        } | ${String.format("%+.1f", improvement)} |"
                    )
                }
                appendLine()
                appendLine("## Strategy Effectiveness")
                appendLine()
                val strategyStats = mutableMapOf<String, MutableList<Double>>()
                evolutionHistory.flatten().forEach { variant ->
                    if (variant.strategy.isNotEmpty()) {
                        strategyStats.getOrPut(variant.strategy) { mutableListOf() }.add(variant.score.overall_score)
                    }
                }
                appendLine("| Strategy | Avg Score | Count | Success Rate |")
                appendLine("|----------|-----------|-------|--------------|")
                strategyStats.forEach { (strategy, scores) ->
                    val avgScore = scores.average()
                    val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                    val successRate =
                        scores.count { it > initialBestScore }.toDouble() / scores.size * 100
                    appendLine(
                        "| $strategy | ${
                            String.format(
                                "%.1f",
                                avgScore
                            )
                        } | ${scores.size} | ${String.format("%.0f%%", successRate)} |"
                    )
                }
                appendLine()
                appendLine("## Best Variant Evolution")
                appendLine()
                appendLine("$TT")
                appendLine(initialText)
                if (initialText.size == 1) {
                    appendLine(
                        "### Initial Text (Score: ${
                            String.format(
                                "%.1f",
                                evolutionHistory[0][0].score.overall_score
                            )
                        })"
                    )
                    appendLine("$TT")
                    appendLine(initialText[0])
                    appendLine("$TT")
                } else {
                    appendLine("### Initial Texts")
                    evolutionHistory[0].forEachIndexed { index, variant ->
                        appendLine()
                        appendLine(
                            "#### Seed ${index + 1} (Score: ${
                                String.format(
                                    "%.1f",
                                    variant.score.overall_score
                                )
                            })"
                        )
                        appendLine("$TT")
                        appendLine(variant.text)
                        appendLine("$TT")
                    }
                }
                appendLine()
                appendLine(
                    "### Final Optimized Text (Score: ${
                        String.format(
                            "%.1f",
                            bestVariant.score.overall_score
                        )
                    })"
                )
                appendLine("$TT")
                appendLine(bestVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("### Improvement Summary")
                appendLine()
                val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                appendLine(
                    "- **Score Improvement:** ${
                        String.format(
                            "%+.1f",
                            bestVariant.score.overall_score - initialBestScore
                        )
                    } points"
                )
                appendLine("- **Generation Found:** ${bestVariant.generation}")
                appendLine("- **Strategy Used:** ${bestVariant.strategy}")
                appendLine()
                appendLine("### Detailed Analysis")
                appendLine()
                appendLine("**Strengths:**")
                bestVariant.score.strengths.forEach { appendLine("- $it") }
                appendLine()
                if (bestVariant.score.weaknesses.isNotEmpty()) {
                    appendLine("**Remaining Areas for Improvement:**")
                    bestVariant.score.weaknesses.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("**Criteria Scores:**")
                bestVariant.score.criteria_scores.forEach { (criterion, score) ->
                    val initialScore =
                        evolutionHistory[0].map { it.score.criteria_scores[criterion] ?: 0.0 }.maxOrNull() ?: 0.0
                    val improvement = score - initialScore
                    appendLine(
                        "- $criterion: ${String.format("%.1f", score)}/100 (${
                            String.format(
                                "%+.1f",
                                improvement
                            )
                        })"
                    )
                }
                appendLine()
                appendLine("**Justification:**")
                appendLine(bestVariant.score.justification)
            }
            evolutionTask.add(evolutionAnalysis.renderMarkdown(true))
            transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(evolutionAnalysis.toByteArray(StandardCharsets.UTF_8))

            // Build final result
            val totalTime = System.currentTimeMillis() - startTime
            buildString {
                appendLine("# Genetic Optimization Results")
                appendLine()
                appendLine("**Optimization Goal:** $optimizationGoal")
                appendLine()
                appendLine("## Final Optimized Text")
                appendLine()
                appendLine("$TT")
                appendLine(bestVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("## Performance Metrics")
                appendLine()
                val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                appendLine("- **Initial Best Score:** ${String.format("%.1f", initialBestScore)}/100")
                appendLine("- **Final Score:** ${String.format("%.1f", bestVariant.score.overall_score)}/100")
                appendLine(
                    "- **Improvement:** ${
                        String.format(
                            "%+.1f",
                            bestVariant.score.overall_score - initialBestScore
                        )
                    } points"
                )
                appendLine("- **Generations:** $numGenerations")
                appendLine("- **Total Variants Evaluated:** ${evolutionHistory.flatten().size}")
                appendLine("- **Best Found in Generation:** ${bestVariant.generation}")
                appendLine()
                appendLine("## Key Improvements")
                appendLine()
                bestVariant.score.strengths.forEach { appendLine("- $it") }
                appendLine()
                appendLine("*See the Evolution Analysis tab for detailed progression and strategy effectiveness*")
            }

            // Final overview update
            val finalOverview = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Optimization Complete")
                appendLine()
                appendLine("| Metric | Value |")
                appendLine("|--------|-------|")
                val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                appendLine("| Initial Best Score | ${String.format("%.1f", initialBestScore)}/100 |")
                appendLine("| Final Score | ${String.format("%.1f", bestVariant.score.overall_score)}/100 |")
                appendLine(
                    "| Improvement | ${
                        String.format(
                            "%+.1f",
                            bestVariant.score.overall_score - initialBestScore
                        )
                    } |"
                )
                appendLine("| Generations | $numGenerations |")
                appendLine("| Total Variants | ${evolutionHistory.flatten().size} |")
                appendLine("| Total Time | ${totalTime / 1000}s |")
                appendLine()
                appendLine("**Status:** ✓ Complete")
            }
            overviewTask.add(finalOverview.renderMarkdown(true))
            transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))


            val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
            log.info("GeneticOptimizationTask completed successfully: total_time=${totalTime}ms, improvement=${bestVariant.score.overall_score - initialBestScore}, generations=$numGenerations")
            task.complete(
                "Optimization complete: improved by ${
                    String.format(
                        "%.1f",
                        bestVariant.score.overall_score - initialBestScore
                    )
                } points in ${totalTime / 1000}s"
            )
            val transcriptFile = "optimization_results_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            val (link, _) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
            val summaryMessage = buildString {
                appendLine("Final Optimized Text")
                appendLine("---")
                appendLine("<details><summary>Optimized Content</summary>\n\n$TT")
                appendLine(bestVariant.text)
                appendLine("$TT\n</details>")
                appendLine("---")
                appendLine("**Strengths:**")
                bestVariant.score.strengths.forEach { appendLine("- $it") }
                appendLine()
                if (bestVariant.score.weaknesses.isNotEmpty()) {
                    appendLine("**Remaining Areas for Improvement:**")
                    bestVariant.score.weaknesses.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine()
                appendLine(
                    "Detailed results: <a href='$link' target='_blank'>$link</a> " +
                            "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                            "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
                )
            }
            resultFn(summaryMessage)

            } catch (e: Exception) {
                handleError(e, task, transcript, resultFn)
            } finally {
                transcript?.close()
            }
        }
    }

    private fun handleError(e: Exception, task: SessionTask, transcript: OutputStream?, resultFn: (String) -> Unit) {
        log.error("Error in GeneticOptimizationTask: ${e.message}", e)
        task.error(e)
        transcript?.write(
            """
            ## Error
            <details><summary>Stack Trace</summary>
            ```
            ${e.stackTraceToString()}
            ```
            </details>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )
        resultFn("ERROR: ${e.message}")
    }

    /**
     * Calculate diversity score for a text variant based on its compressibility
     * with existing population members. Higher scores indicate more diversity.
     *
     * Returns a score between 0 and 1, where:
     * - 1.0 = highly diverse (incompressible with existing texts)
     * - 0.0 = duplicate or very similar (highly compressible)
     */
    private fun calculateDiversityScore(text: String, existingTexts: List<String>): Double {
        if (existingTexts.isEmpty()) return 1.0
        // Calculate average compressibility with all existing texts
        val compressibilities = existingTexts.map { existing ->
            compressibility(text, existing)
        }
        val avgCompressibility = compressibilities.average()
        // Convert compressibility to diversity score
        // compressibility of 1.0 (incompressible) -> diversity 1.0 (very diverse)
        // compressibility of 2.0 (duplicate) -> diversity 0.0 (not diverse)
        val diversityScore = max(0.0, min(1.0, 2.0 - avgCompressibility))
        log.debug(
            "Diversity score: ${
                String.format(
                    "%.3f",
                    diversityScore
                )
            } (avg compressibility: ${String.format("%.3f", avgCompressibility)})"
        )
        return diversityScore
    }


    private fun generateMutation(
        text: String,
        parentScore: EvaluationScore,
        strategy: String,
        goal: String,
        constraints: List<String>,
        context: String,
        api: ChatInterface
    ) = try {
        ParsedAgent(
            resultClass = TextVariant::class.java,
            prompt = """
 You are a text optimization expert applying genetic algorithm mutations.

 ## Optimization Goal
 $goal

 ## Mutation Strategy
 $strategy
${
                if (constraints.isNotEmpty()) {
                    "\n\nConstraints to maintain:\n${constraints.joinToString("\n") { "- $it" }}"
                } else ""
            }
${
                if (context.isNotBlank()) {
                    "\n\nAdditional context:\n${context.take(5000)}"
                } else ""
            }

 ## Instructions
 Apply the "$strategy" mutation strategy to create a variant of the text that better achieves the optimization goal.

 Mutation strategies:
- **rephrase**: Reword while maintaining meaning, focus on different phrasing
- **simplify**: Make more concise and easier to understand
- **elaborate**: Add detail, examples, or explanation
- **restructure**: Reorganize the structure or flow
- **emphasize**: Strengthen key points or calls to action
- **soften**: Make tone more gentle or diplomatic

 Generate ONE variant that applies this strategy effectively.
 
## Parent Evaluation Feedback
**Overall Score:** ${String.format("%.1f", parentScore.overall_score)}/100
**Strengths to Preserve:**
${parentScore.strengths.joinToString("\n") { "- $it" }}
**Weaknesses to Address:**
${parentScore.weaknesses.joinToString("\n") { "- $it" }}
**Criteria Scores:**
${
                parentScore.criteria_scores.entries.joinToString("\n") { (criterion, score) ->
                    "- $criterion: ${String.format("%.1f", score)}/100"
                }
            }
**Justification:** ${parentScore.justification}

Use this feedback to guide your mutation:
- Preserve the strengths identified in the parent
- Address the weaknesses while applying the mutation strategy
- Focus on improving the lowest-scoring criteria
      """.trimIndent(),
            model = api,
            temperature = 0.8,
            name = "MutationGenerator",
            parsingChatter = defaultFast,
        ).answer(
            listOf(
                """

 ## Current Text
$TT
$text
```

        """
            )
        ).obj
    } catch (e: Exception) {
        log.warn("Failed to generate mutation with strategy $strategy", e)
        null
    }

    private fun applyCrossover(
        text1: String,
        score1: EvaluationScore,
        text2: String,
        score2: EvaluationScore,
        goal: String,
        constraints: List<String>,
        api: ChatInterface
    ) = try {
        ChatAgent(
            prompt = "You are a text optimization expert.",
            model = api,
            temperature = 0.7
        ).answer(
            listOf(
                """
 You are a text optimization expert applying genetic algorithm crossover.

                
Use the evaluation feedback to guide your crossover:
- Preserve the strengths from both parents
- Avoid or fix the weaknesses identified in both parents
- Focus on combining high-scoring aspects from each parent

 ## Parent Text 1
$TT
$text1
```

## Parent 1 Evaluation
**Score:** ${String.format("%.1f", score1.overall_score)}/100
**Strengths:**
${score1.strengths.joinToString("\n") { "- $it" }}
**Weaknesses:**
${score1.weaknesses.joinToString("\n") { "- $it" }}
**Criteria Scores:**
${
                    score1.criteria_scores.entries.joinToString("\n") { (criterion, score) ->
                        "- $criterion: ${String.format("%.1f", score)}/100"
                    }
                }
                
## Parent Text 2
```
$text2
```

## Parent 2 Evaluation
**Score:** ${String.format("%.1f", score2.overall_score)}/100

**Strengths:**
${score2.strengths.joinToString("\n") { "- $it" }}

**Weaknesses:**
${score2.weaknesses.joinToString("\n") { "- $it" }}

**Criteria Scores:**
${
                    score2.criteria_scores.entries.joinToString("\n") { (criterion, score) ->
                        "- $criterion: ${String.format("%.1f", score)}/100"
                    }
                }

## Optimization Goal
$goal
${
                    if (constraints.isNotEmpty()) {
                        "\n\nConstraints to maintain:\n${constraints.joinToString("\n") { "- $it" }}"
                    } else ""
                }

## Instructions
Create a new variant by combining the best elements from both parent texts.
- Identify the strongest aspects of each parent
- Merge them into a cohesive new variant
- Ensure the result is better than either parent alone
- Maintain consistency and flow

 Generate the crossover variant now.
      """.trimIndent()
            )
        )
    } catch (e: Exception) {
        log.warn("Failed to apply crossover", e)
        null
    }

    private fun evaluateVariant(
        text: String,
        goal: String,
        weights: Map<String, Double>,
        constraints: List<String>,
        api: ChatInterface
    ) = try {
        ParsedAgent(
            resultClass = EvaluationScore::class.java,
            prompt = """
 You are an expert evaluator for text optimization using genetic algorithms.


## Optimization Goal
$goal

## Evaluation Criteria
${
                weights.entries.joinToString("\n") { (criterion, weight) ->
                    "- $criterion (${
                        String.format(
                            "%.0f%%",
                            weight * 100
                        )
                    } weight)"
                }
            }
${
                if (constraints.isNotEmpty()) {
                    "\n\nConstraints:\n${constraints.joinToString("\n") { "- $it" }}"
                } else ""
            }

## Instructions
Evaluate this text variant against the optimization goal and criteria.

For each criterion, provide a score from 0-100:
- **clarity**: How clear and understandable is the text?
- **conciseness**: How efficiently does it communicate?
- **impact**: How effective is it at achieving the goal?
- **goal_alignment**: How well does it align with the stated optimization goal?

Also provide:
1. An overall weighted score (0-100)
2. List of strengths (what works well)
3. List of weaknesses (what could be improved)
4. Brief justification for the scores

Be objective and consistent in your evaluation.
      """.trimIndent(),
            model = api,
            temperature = 0.3,
            name = "VariantEvaluator",
            parsingChatter = defaultFast,
        ).answer(
            listOf(
                """

## Text to Evaluate
$TT
$text
```
        """.trimIndent()
            )
        ).obj
    } catch (e: Exception) {
        log.warn("Failed to evaluate variant", e)
        EvaluationScore(
            overall_score = 0.0,
            criteria_scores = weights.keys.associateWith { 0.0 },
            strengths = emptyList(),
            weaknesses = listOf("Evaluation failed: ${e.message}"),
            justification = "Error during evaluation"
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/IsomorphismDiscoveryTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class IsomorphismDiscoveryTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: IsomorphismDiscoveryTaskExecutionConfigData?
) : AbstractTask<IsomorphismDiscoveryTask.IsomorphismDiscoveryTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class IsomorphismDiscoveryTaskExecutionConfigData(
        @Description("Description of the source domain (e.g., 'Number Theory')")
        var source_domain: String? = null,
        @Description("Description of the target domain (e.g., 'Geometry')")
        var target_domain: String? = null,
        @Description("Strictness of the mapping: 'loose' (homomorphism) or 'strict' (isomorphism)")
        var mapping_strictness: String = "strict",
        @Description("Whether to verify that operations are preserved across the map")
        var verify_operations: Boolean = true,
        @Description("Input files to provide context")
        var input_files: List<String>? = null,
        @Description("Additional context files")
        var related_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = IsomorphismDiscovery.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (source_domain.isNullOrBlank()) return "source_domain must not be blank"
            if (target_domain.isNullOrBlank()) return "target_domain must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class DomainStructure(
        @Description("Fundamental objects or 'atoms' in the domain")
        val objects: List<String> = emptyList(),
        @Description("Operations or 'verbs' that combine objects")
        val operations: List<String> = emptyList(),
        @Description("Key properties or axioms of the domain")
        val properties: List<String> = emptyList()
    ) : ValidatedObject

    data class MappingRule(
        @Description("Element from source domain")
        val source_element: String = "",
        @Description("Corresponding element in target domain")
        val target_element: String = "",
        @Description("Rationale for this mapping")
        val rationale: String = ""
    ) : ValidatedObject

    data class VerificationCase(
        @Description("Description of the operation in source domain (e.g. A + B = C)")
        val source_operation: String = "",
        @Description("Description of the mapped operation in target domain (e.g. f(A) * f(B) = f(C))")
        val target_operation: String = "",
        @Description("Does the mapping hold? (true/false)")
        val holds: Boolean = false,
        @Description("Explanation of the verification result")
        val explanation: String = ""
    ) : ValidatedObject

    data class IsomorphismResult(
        @Description("Structure of the source domain")
        val source_structure: DomainStructure = DomainStructure(),
        @Description("Structure of the target domain")
        val target_structure: DomainStructure = DomainStructure(),
        @Description("Proposed mapping rules between domains")
        val mappings: List<MappingRule> = emptyList(),
        @Description("Verification of structural preservation")
        val verification_cases: List<VerificationCase> = emptyList(),
        @Description("Overall assessment of the isomorphism/homomorphism")
        val conclusion: String = "",
        @Description("Confidence score (0-1)")
        val confidence: Double = 0.0
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
IsomorphismDiscovery - Search for and validate structural mappings between two distinct domains
  ** Specify source_domain and target_domain
  ** Set mapping_strictness ('loose' or 'strict')
  ** Enable verify_operations to check structural preservation
  ** The task will:
     - Identify primitives (objects and operations) in both domains
     - Generate candidate mapping rules
     - Verify if operations are preserved (f(A op B) = f(A) op' f(B))
     - Refine and assess the validity of the isomorphism
  ** Useful for theoretical physics, system architecture, cryptography, and abstract modeling
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        try {
            log.info("Starting IsomorphismDiscoveryTask. Source: ${executionConfig?.source_domain}, Target: ${executionConfig?.target_domain}")

            executionConfig?.validate()?.let { validationError ->
                log.error("IsomorphismDiscoveryTask config validation failed: $validationError")
                task.safeComplete("CONFIGURATION ERROR: $validationError", log)
                task.error(ValidatedObject.ValidationError(validationError, executionConfig))
                resultFn("CONFIGURATION ERROR: $validationError")
                return
            }

            val sourceDomain = executionConfig?.source_domain!!
            val targetDomain = executionConfig?.target_domain!!
            val strictness = executionConfig?.mapping_strictness ?: "strict"
            val verify = executionConfig?.verify_operations ?: true


            task.ui.pool.submit {
                try {
                    val startTime = System.currentTimeMillis()
                    val tabs = TabbedDisplay(task)
                    val api = defaultSmart ?: throw RuntimeException("No smart model available")
                    writeTranscriptHeader(transcript, sourceDomain, targetDomain, strictness)

            // Overview
            val overviewTask = task.newTask()
            tabs["Overview"] = overviewTask.placeholder
            overviewTask.add(buildString {
                appendLine("# Isomorphism Discovery Task")
                appendLine()
                appendLine("**Source Domain:** $sourceDomain")
                appendLine("**Target Domain:** $targetDomain")
                appendLine("**Strictness:** $strictness")
                appendLine("**Verify Operations:** $verify")
                appendLine()
                appendLine("- ⏳ Gathering context...")
            }.renderMarkdown())
            task.update()

            // Context
            val priorContext = getPriorCode(agent.executionState)
            val inputFileContent =
                super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)
            val relatedFileContent = getRelatedFilesContent()


                    writeToTranscript(
                        transcript,
                        "## Context\n<details><summary>Context Data</summary>\n\n$inputFileContent\n\n$relatedFileContent\n</details>\n\n"
                    )

                    val contextHtml = "## Context\n\n$inputFileContent\n\n$relatedFileContent".renderMarkdown()
            overviewTask.expandable("Context Data", contextHtml)
                    overviewTask.add("- ✓ Context gathered\n- ⏳ Analyzing structures and mappings...".renderMarkdown())
            task.update()

            // Analysis
            val analysisTask = task.newTask()
            tabs["Analysis"] = analysisTask.placeholder
            analysisTask.add(buildString {
                appendLine("# Structural Analysis")
                appendLine()
                appendLine("Identifying primitives and searching for mappings...")
            }.renderMarkdown())
            task.update()

            val prompt = """
                You are an expert in structural analysis, category theory, and finding isomorphisms between domains.
                
                ## Task
                Discover a structural mapping between the Source Domain and Target Domain.
                Strictness: $strictness
                
                ## Source Domain
                $sourceDomain
                
                ## Target Domain
                $targetDomain
                
                ## Context
                $priorContext
                $inputFileContent
                $relatedFileContent
                
                ## Instructions
                1. **Primitive Definition**: Identify the fundamental objects (atoms) and operations (verbs) in both domains.
                2. **Candidate Generation**: Propose a mapping function (dictionary) between objects and operations.
                3. **Structure Verification**: Verify if the structure is preserved. 
                   - If A and B are objects in Source, and * is an operation in Source:
                   - Does f(A * B) correspond to f(A) # f(B) in Target (where # is the corresponding operation)?
                   - Provide specific concrete examples or abstract derivations as verification cases.
                4. **Refinement**: Ensure the mapping is as consistent as possible given the strictness level.
                5. **Conclusion**: Assess the validity and limitations of the discovered isomorphism.
                
                Generate the full analysis result.
            """.trimIndent()

            val parser = ParsedAgent(
                resultClass = IsomorphismResult::class.java,
                prompt = prompt,
                model = api,
                temperature = 0.2,
                parsingChatter = defaultFast
            )

            var result = parser.answer(listOf(prompt)).obj

            if (result == null) {
                throw RuntimeException("Failed to generate isomorphism result")
            }

            // Display Results

                    analysisTask.add(formatAnalysisResult(result).renderMarkdown())
            task.update()

                    writeToTranscript(
                        transcript,
                        "## Analysis Result\n<details><summary>Raw Result JSON</summary>\n\n```json\n$result\n```\n</details>\n\n"
                    )

            // Synthesis
            val synthesisTask = task.newTask()
            tabs["Synthesis"] = synthesisTask.placeholder
            val synthesisText = formatSynthesis(result, sourceDomain, targetDomain)
                    synthesisTask.add(synthesisText.renderMarkdown())
            task.update()

            // Final Overview
            overviewTask.add(buildString {
                appendLine()
                appendLine("- ✓ Analysis complete")
                appendLine("- **Confidence:** ${String.format("%.1f%%", result.confidence * 100)}")
                appendLine("- **Mappings:** ${result.mappings.size}")
                appendLine("- **Verifications:** ${result.verification_cases.count { it.holds }} / ${result.verification_cases.size} passed")

            }.renderMarkdown())
            task.update()

                    writeTranscriptFooter(transcript, System.currentTimeMillis() - startTime)

            task.safeComplete("Isomorphism discovery completed with ${result.mappings.size} mappings.", log)
            resultFn(synthesisText)

                } catch (e: Exception) {
                    log.error("Error in IsomorphismDiscoveryTask execution", e)
                    writeToTranscript(
                        transcript,
                        "## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n\n"
                    )
                    task.error(e)
                    task.safeComplete("Failed with error: ${e.message}", log)
                    resultFn("ERROR: ${e.message}")
                } finally {
                    transcript?.close()
                }
            }
        } finally {
            // Transcript is closed inside the async block to ensure it captures all output
        }
    }

    private fun getRelatedFilesContent(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""
        return buildString {
            appendLine("## Related Files")
            relatedFiles.forEach { file ->
                try {
                    val filePath = root.resolve(file)
                    if (filePath.toFile().exists()) {
                        appendLine("### $file")
                        appendLine("```")
                        appendLine(filePath.toFile().readText().truncateForDisplay())
                        appendLine("```")
                    }
                } catch (e: Exception) {
                    log.warn("Error reading related file: $file", e)
                }
            }
        }
    }

    private fun formatAnalysisResult(result: IsomorphismResult): String {
        return buildString {
            appendLine("## Domain Structures")
            appendLine()
            appendLine("### Source Domain")
            appendLine("- **Objects:** ${result.source_structure.objects.joinToString(", ")}")
            appendLine("- **Operations:** ${result.source_structure.operations.joinToString(", ")}")
            appendLine()
            appendLine("### Target Domain")
            appendLine("- **Objects:** ${result.target_structure.objects.joinToString(", ")}")
            appendLine("- **Operations:** ${result.target_structure.operations.joinToString(", ")}")
            appendLine()
            appendLine("## Mappings")
            appendLine("| Source | Target | Rationale |")
            appendLine("|--------|--------|-----------|")
            result.mappings.forEach {
                appendLine("| ${it.source_element} | ${it.target_element} | ${it.rationale} |")
            }
            appendLine()
            appendLine("## Verification")
            result.verification_cases.forEach {
                val icon = if (it.holds) "✅" else "❌"
                appendLine("### $icon ${it.source_operation} → ${it.target_operation}")
                appendLine(it.explanation)
                appendLine()
            }
        }
    }

    private fun formatSynthesis(result: IsomorphismResult, source: String, target: String): String {
        return buildString {
            appendLine("# Isomorphism Discovery: $source ↔ $target")
            appendLine()
            appendLine("## Conclusion")
            appendLine(result.conclusion)
            appendLine()
            appendLine("**Confidence:** ${String.format("%.1f%%", result.confidence * 100)}")
            appendLine()
            appendLine("## Key Mappings")
            result.mappings.take(5).forEach {
                appendLine("- **${it.source_element}** ↔ **${it.target_element}**")
            }
            if (result.mappings.size > 5) appendLine("- ...and ${result.mappings.size - 5} more")
        }
    }

    private fun writeTranscriptHeader(stream: OutputStream?, source: String, target: String, strictness: String) {
        stream?.write(buildString {
            appendLine("# Isomorphism Discovery Transcript")
            appendLine("**Source:** $source")
            appendLine("**Target:** $target")
            appendLine("**Strictness:** $strictness")
            appendLine("**Date:** ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine("---")
            appendLine()
        }.toByteArray(Charsets.UTF_8))
    }

    private fun writeToTranscript(stream: OutputStream?, content: String) {
        stream?.write(content.toByteArray(Charsets.UTF_8))
    }

    private fun writeTranscriptFooter(stream: OutputStream?, duration: Long) {
        stream?.write(buildString {
            appendLine("---")
            appendLine("**Duration:** ${duration / 1000}s")
        }.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(IsomorphismDiscoveryTask::class.java)
        @JvmStatic val IsomorphismDiscovery = TaskType(
            name = "IsomorphismDiscovery",
            category = "Reasoning",
            taskClass = IsomorphismDiscoveryTask::class.java,
            executionConfigClass = IsomorphismDiscoveryTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Search for and validate structural mappings between two distinct domains",
            tooltipHtml = """
                          Identifies structural isomorphisms between domains.
                          <ul>
                            <li>Defines primitives (objects and operations) in both domains</li>
                            <li>Generates candidate mapping rules</li>
                            <li>Verifies structural preservation (homomorphism/isomorphism)</li>
                            <li>Useful for theoretical physics, system architecture, and abstract modeling</li>
                          </ul>
                        """.trimIndent(),
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/LateralThinkingTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LateralThinkingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: LateralThinkingTaskExecutionConfigData?
) : AbstractTask<LateralThinkingTask.LateralThinkingTaskExecutionConfigData, LateralThinkingTask.LateralThinkingTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(LateralThinkingTask::class.java)

        @JvmStatic val LateralThinking = TaskType(
            name = "LateralThinking",
            category = "Reasoning",
            taskClass = LateralThinkingTask::class.java,
            executionConfigClass = LateralThinkingTaskExecutionConfigData::class.java,
            taskSettingsClass = LateralThinkingTaskTypeConfig::class.java,
            description = "Break conventional thinking patterns to find innovative solutions",
            tooltipHtml = """
                        Applies lateral thinking techniques to generate unconventional solutions.
                        <ul>
                          <li>Supports multiple techniques: reversal, random stimulus, challenge assumptions, exaggeration, escape, metaphor, provocation</li>
                          <li>Generates multiple alternatives per technique</li>
                          <li>Identifies breakthrough aspects and novel perspectives</li>
                          <li>Evaluates novelty and feasibility of ideas</li>
                          <li>Synthesizes insights across techniques</li>
                          <li>Optionally performs detailed feasibility evaluation</li>
                          <li>Suggests hybrid approaches combining multiple ideas</li>
                          <li>Ideal for innovation, breaking design impasses, and creative problem-solving</li>
                        </ul>
                      """,
        )
    }

    val maxDescriptionLength = 1500

    class LateralThinkingTaskExecutionConfigData(
        @Description("The problem or challenge to approach with lateral thinking")
        var problem: String? = null,
        @Description("Lateral thinking techniques to apply: reversal, random_stimulus, challenge_assumptions, exaggeration, escape, metaphor, provocation")
        var techniques: List<String>? = listOf(
            "reversal",
            "random_stimulus",
            "challenge_assumptions",
            "exaggeration",
            "escape"
        ),
        @Description("Number of alternative solutions to generate per technique")
        var num_alternatives: Int = 5,
        @Description("Whether to evaluate the feasibility of generated ideas")
        var evaluate_feasibility: Boolean = true,
        @Description("Domain or context to constrain the thinking (optional)")
        var domain_context: String? = null,
        @Description("Additional constraints or requirements to consider")
        var constraints: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the task")
        var input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = LateralThinking.name,
        task_description = task_description
            ?: "Apply lateral thinking to: ${problem?.take(100)}${if (problem?.length ?: 0 > 100) "..." else ""}",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {

        override fun validate(): String? {
            if (problem.isNullOrBlank()) {
                return "Problem must be specified and cannot be blank"
            }
            if (num_alternatives < 1 || num_alternatives > 10) {
                return "Number of alternatives must be between 1 and 10, got: $num_alternatives"
            }
            techniques?.forEach { technique ->
                val validTechniques = listOf(
                    "reversal", "random_stimulus", "challenge_assumptions",
                    "exaggeration", "escape", "metaphor", "provocation"
                )
                if (technique !in validTechniques) {
                    return "Invalid technique '$technique'. Valid techniques are: ${validTechniques.joinToString(", ")}"
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    class LateralThinkingTaskTypeConfig(
        task_type: String? = LateralThinking.name,
        name: String? = null,
        model: ApiChatModel? = null
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name,
        model = model
    ), ValidatedObject

    data class LateralIdea(
        @Description("Title of the idea")
        val title: String = "",
        @Description("The lateral thinking technique used")
        val technique: String = "",
        @Description("Detailed description of the idea")
        val description: String = "",
        @Description("The provocation or stimulus that led to this idea")
        val provocation: String = "",
        @Description("How this breaks conventional thinking")
        val breakthrough_aspect: String = "",
        @Description("Potential benefits of this approach")
        val benefits: List<String> = emptyList(),
        @Description("Potential challenges or risks")
        val challenges: List<String> = emptyList(),
        @Description("Concrete steps to implement this idea")
        val implementation_steps: List<String> = emptyList(),
        @Description("Novelty score (0-1)")
        val novelty_score: Double = 0.0,
        @Description("Feasibility score (0-1)")
        val feasibility_score: Double = 0.0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) {
                return "LateralIdea title cannot be blank"
            }
            if (technique.isBlank()) {
                return "LateralIdea technique cannot be blank"
            }
            if (description.isBlank()) {
                return "LateralIdea description cannot be blank"
            }
            if (novelty_score < 0.0 || novelty_score > 1.0) {
                return "LateralIdea novelty_score must be between 0.0 and 1.0, got: $novelty_score"
            }
            if (feasibility_score < 0.0 || feasibility_score > 1.0) {
                return "LateralIdea feasibility_score must be between 0.0 and 1.0, got: $feasibility_score"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class TechniqueApplication(
        @Description("The technique name")
        val technique: String = "",
        @Description("Description of how the technique was applied")
        val application_description: String = "",
        @Description("The provocation or reframing used")
        val provocation: String = "",
        @Description("Ideas generated from this technique")
        val ideas: List<LateralIdea> = emptyList(),
        @Description("Key insights from applying this technique")
        val insights: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (technique.isBlank()) {
                return "TechniqueApplication technique cannot be blank"
            }
            if (application_description.isBlank()) {
                return "TechniqueApplication application_description cannot be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class FeasibilityEvaluation(
        @Description("Overall feasibility assessment")
        val overall_assessment: String = "",
        @Description("Most promising ideas ranked by feasibility")
        val top_ideas: List<String> = emptyList(),
        @Description("Ideas requiring further exploration")
        val ideas_for_exploration: List<String> = emptyList(),
        @Description("Hybrid approaches combining multiple ideas")
        val hybrid_approaches: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (overall_assessment.isBlank()) {
                return "FeasibilityEvaluation overall_assessment cannot be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class LateralThinkingResult(
        @Description("Applications of each technique")
        val technique_applications: List<TechniqueApplication> = emptyList(),
        @Description("All generated ideas across techniques")
        val all_ideas: List<LateralIdea> = emptyList(),
        @Description("Synthesized insights across all techniques")
        val synthesized_insights: List<String> = emptyList(),
        @Description("Recommended unconventional approaches")
        val recommended_approaches: List<String> = emptyList(),
        @Description("Feasibility evaluation if requested")
        val feasibility_evaluation: FeasibilityEvaluation? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (technique_applications.isEmpty()) {
                return "LateralThinkingResult must have at least one technique application"
            }
            if (all_ideas.isEmpty()) {
                return "LateralThinkingResult must have at least one idea"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
LateralThinking - Break conventional thinking patterns to find innovative solutions
  ** Specify the problem or challenge to approach creatively
  ** Select lateral thinking techniques to apply:
     - reversal: Reverse the problem or goal
     - random_stimulus: Apply unrelated concepts
     - challenge_assumptions: Question fundamental assumptions
     - exaggeration: Amplify aspects to extremes
     - escape: Temporarily ignore key constraints
     - metaphor: Use metaphorical thinking
     - provocation: Use deliberate provocations
  ** Configure number of alternatives per technique (default: 5)
  ** Optionally evaluate feasibility of generated ideas
  ** The task will:
     - Apply each selected technique systematically
     - Generate unconventional alternatives
     - Identify breakthrough aspects
     - Synthesize insights across techniques
     - Evaluate feasibility if requested
  ** Useful for innovation, breaking design impasses, and creative problem-solving
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        task.ui.pool.submit {
            val transcript = task.transcript()
            try {
                val startTime = System.currentTimeMillis()
            log.info("Starting LateralThinkingTask for problem='${executionConfig?.problem?.take(50)}...', techniques=${executionConfig?.techniques}")

            val problem = executionConfig?.problem
            if (problem.isNullOrBlank()) {
                log.error("Configuration error: problem is blank")
                task.safeComplete("CONFIGURATION ERROR: Problem must be specified", log)
                task.error(RuntimeException("Configuration error: problem is blank"))
                resultFn("CONFIGURATION ERROR: Problem must be specified")
                return@submit
            }

            val techniques = executionConfig.techniques ?: listOf(
                "reversal",
                "random_stimulus",
                "challenge_assumptions",
                "exaggeration",
                "escape"
            )
            val numAlternatives = (executionConfig.num_alternatives ?: 5).coerceIn(1, 10)
            val evaluateFeasibility = executionConfig.evaluate_feasibility ?: true
            val domainContext = executionConfig.domain_context
            val constraints = executionConfig.constraints

            log.info("Configuration: techniques=${techniques.size}, numAlternatives=$numAlternatives, evaluateFeasibility=$evaluateFeasibility")

                val tabs = TabbedDisplay(task)

            // Create overview tab
            val overviewTask = tabs.newTask("Overview")
            val overviewContent = buildString {
                appendLine("# Lateral Thinking Task")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Problem Statement")
                appendLine()
                appendLine("> $problem")
                appendLine()
                if (domainContext != null) {
                    appendLine("**Domain Context:** $domainContext")
                    appendLine()
                }
                if (!constraints.isNullOrEmpty()) {
                    appendLine("**Constraints:**")
                    constraints.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("## Configuration")
                appendLine()
                appendLine("| Parameter | Value |")
                appendLine("|-----------|-------|")
                appendLine("| Techniques | ${techniques.joinToString(", ")} |")
                appendLine("| Alternatives per Technique | $numAlternatives |")
                appendLine("| Feasibility Evaluation | ${if (evaluateFeasibility) "✓ Enabled" else "✗ Disabled"} |")
                appendLine()
                transcript?.write(this.toString().toByteArray())
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Gathering context...")
            }
            overviewTask.add(overviewContent.renderMarkdown(true))

            log.debug("Gathering prior context")
            val priorContext = getPriorCode(agent.executionState)
                val fileContext = getInputFileContent(executionConfig.input_files, agent.root)
            val combinedContext = priorContext + "\n\n" + fileContext
            log.debug("Context gathered: priorContext length=${priorContext.length}, fileContext length=${fileContext.length}")
                transcript?.write("## Context\n<details>\n<summary>Input Context</summary>\n\n$combinedContext\n</details>\n".toByteArray())

            overviewTask.add(buildString {
              appendLine()
              transcript?.write("\n- ✓ Context gathered\n- ⏳ Applying lateral thinking techniques...\n".toByteArray())
              appendLine("- ✓ Context gathered")
              appendLine("- ⏳ Applying lateral thinking techniques...")
                        }.renderMarkdown(true))

            // Step 1: Apply each technique
            log.info("Starting technique application phase")
            val techniqueApplications = mutableListOf<TechniqueApplication>()
            val allIdeas = mutableListOf<LateralIdea>()

            techniques.forEachIndexed { index, technique ->
                log.info("Applying technique ${index + 1}/${techniques.size}: $technique")

                val techniqueTask = tabs.newTask("${index + 1}. ${technique.capitalize()}")

                techniqueTask.add(buildString {
                  appendLine("# ${technique.capitalize()} Technique")
                  appendLine()
                  transcript?.write("# ${technique.capitalize()} Technique\n\n**Status:** ⏳ Generating ideas...\n\n".toByteArray())
                  appendLine("**Status:** ⏳ Generating ideas...")
                  appendLine()
                  appendLine(getTechniqueDescription(technique))
                                }.renderMarkdown(true))

                val techniquePrompt = buildTechniquePrompt(
                    technique,
                    problem,
                    numAlternatives,
                    domainContext,
                    constraints,
                    combinedContext
                )

                val techniqueParser = ParsedAgent(
                    resultClass = TechniqueApplication::class.java,
                    prompt = techniquePrompt,
                    model = defaultSmart.getChildClient(task),
                    temperature = 0.8,
                    name = "LateralThinking_${technique}",
                    parsingChatter = defaultFast,
                )

                val application = techniqueParser.answer(listOf(techniquePrompt)).obj

                if (application != null) {
                    techniqueApplications.add(application)
                    allIdeas.addAll(application.ideas)
                    log.info("Technique $technique generated ${application.ideas.size} ideas")

                    // Display technique results
                    techniqueTask.add(buildString {
                      transcript?.write("\n---\n\n## Results\n\n**Status:** ✓ Complete\n\n".toByteArray())
                      appendLine()
                      appendLine("---")
                      appendLine()
                      appendLine("## Results")
                      appendLine()
                      appendLine("**Status:** ✓ Complete")
                      appendLine()
                      appendLine("### Provocation")
                      appendLine()
                      appendLine("> ${application.provocation}")
                      appendLine()
                      appendLine("### Application")
                      appendLine()
                      appendLine(application.application_description)
                      appendLine()
                      appendLine("### Generated Ideas (${application.ideas.size})")
                      appendLine()
                      application.ideas.forEachIndexed { ideaIndex, idea ->
                        appendLine("#### ${ideaIndex + 1}. ${idea.title}")
                        appendLine()
                        appendLine(
                          "**Novelty:** ${String.format("%.1f%%", idea.novelty_score * 100)} | **Feasibility:** ${
                            String.format(
                              "%.1f%%",
                              idea.feasibility_score * 100
                            )
                          }"
                        )
                        appendLine()
                        appendLine(idea.description)
                        appendLine()
                        appendLine("**Breakthrough Aspect:** ${idea.breakthrough_aspect}")
                        appendLine()
                        if (idea.benefits.isNotEmpty()) {
                          appendLine("**Benefits:**")
                          idea.benefits.take(3).forEach { appendLine("- $it") }
                          if (idea.benefits.size > 3) appendLine("- *...and ${idea.benefits.size - 3} more*")
                          appendLine()
                        }
                        if (idea.challenges.isNotEmpty()) {
                          appendLine("**Challenges:**")
                          idea.challenges.take(3).forEach { appendLine("- $it") }
                          if (idea.challenges.size > 3) appendLine("- *...and ${idea.challenges.size - 3} more*")
                          appendLine()
                        }
                        appendLine("---")
                        appendLine()
                      }
                      transcript?.write(this.toString().toByteArray())
                      if (application.insights.isNotEmpty()) {
                        appendLine("### Key Insights")
                        appendLine()
                        application.insights.forEach { appendLine("- $it") }
                      }
                                        }.renderMarkdown(true))
                } else {
                    log.warn("Failed to generate ideas for technique: $technique")
                    transcript?.write("\n**Status:** ⚠️ Failed to generate ideas\n".toByteArray())
                    techniqueTask.add(buildString {
                      appendLine()
                      appendLine("**Status:** ⚠️ Failed to generate ideas")
                                        }.renderMarkdown(true))
                }
                techniqueTask.complete()

                overviewTask.add(buildString {
                  appendLine()
                  transcript?.write("\n- ✓ ${technique.capitalize()} complete (${application?.ideas?.size ?: 0} ideas)\n".toByteArray())
                  appendLine("- ✓ ${technique.capitalize()} complete (${application?.ideas?.size ?: 0} ideas)")
                                }.renderMarkdown(true))
            }

            log.info("All techniques applied. Total ideas generated: ${allIdeas.size}")

            overviewTask.add(buildString {
              appendLine()
              transcript?.write("\n- ✓ All techniques applied (${allIdeas.size} total ideas)\n- ⏳ Synthesizing insights...\n".toByteArray())
              appendLine("- ✓ All techniques applied (${allIdeas.size} total ideas)")
              appendLine("- ⏳ Synthesizing insights...")
                        }.renderMarkdown(true))

            // Step 2: Synthesize insights
            log.info("Starting synthesis phase")
            val synthesisTask = tabs.newTask("Synthesis")

            synthesisTask.add(buildString {
              appendLine("# Cross-Technique Synthesis")
              transcript?.write("\n# Cross-Technique Synthesis\n\n**Status:** ⏳ Analyzing patterns and insights...\n".toByteArray())
              appendLine()
              appendLine("**Status:** ⏳ Analyzing patterns and insights...")
                        }.renderMarkdown(true))

            val synthesisPrompt = """
You are an expert in creative problem-solving and innovation.

## Task
Synthesize insights across all the lateral thinking techniques applied to this problem.

## Original Problem
$problem

## Techniques Applied and Their Results
${
                techniqueApplications.joinToString("\n\n") { app ->
                    """
### ${app.technique.capitalize()}
**Provocation:** ${app.provocation}
**Ideas Generated:** ${app.ideas.size}
**Key Insights:**
${app.insights.joinToString("\n") { "- $it" }}

**Top Ideas:**
${
                        app.ideas.sortedByDescending { it.novelty_score * it.feasibility_score }.take(2)
                            .joinToString("\n") { "- ${it.title}: ${it.description.take(100)}..." }
                    }
""".trim()
                }
            }

## Instructions
1. Identify common themes and patterns across techniques
2. Highlight the most breakthrough insights
3. Identify complementary ideas that could be combined
4. Suggest 3-5 recommended unconventional approaches
5. Note any paradigm shifts or reframings that emerged

Provide a comprehensive synthesis.
            """.trimIndent()

            val synthesisAgent = ChatAgent(
                prompt = "You are an expert in creative synthesis and innovation strategy.",
                model = defaultSmart.getChildClient(task),
                temperature = 0.6
            )

            val synthesisText = synthesisAgent.answer(listOf(synthesisPrompt))

            synthesisTask.add(buildString {
              appendLine()
              transcript?.write("\n---\n\n## Synthesis Results\n\n**Status:** ✓ Complete\n\n${synthesisText}\n".toByteArray())
              appendLine("---")
              appendLine()
              appendLine("## Synthesis Results")
              appendLine()
              appendLine("**Status:** ✓ Complete")
              appendLine()
              appendLine(synthesisText)
                        }.renderMarkdown(true))
            synthesisTask.complete()

            // Extract recommended approaches from synthesis
            val recommendedApproaches = extractRecommendedApproaches(synthesisText)

            overviewTask.add(buildString {
              appendLine()
              transcript?.write("\n- ✓ Synthesis complete\n".toByteArray())
              appendLine("- ✓ Synthesis complete")
              if (evaluateFeasibility) {
                appendLine("- ⏳ Evaluating feasibility...")
              }
                        }.renderMarkdown(true))

            // Step 3: Feasibility evaluation (if requested)
            var feasibilityEvaluation: FeasibilityEvaluation? = null
            if (evaluateFeasibility) {
                log.info("Starting feasibility evaluation phase")
                val feasibilityTask = tabs.newTask("Feasibility")

                feasibilityTask.add(buildString {
                  appendLine("# Feasibility Evaluation")
                  transcript?.write("\n# Feasibility Evaluation\n\n**Status:** ⏳ Evaluating ${allIdeas.size} ideas...\n".toByteArray())
                  appendLine()
                  appendLine("**Status:** ⏳ Evaluating ${allIdeas.size} ideas...")
                                }.renderMarkdown(true))

                val feasibilityPrompt = """
You are an expert in evaluating the practical feasibility of innovative ideas.

## Original Problem
$problem

${if (domainContext != null) "## Domain Context\n$domainContext\n" else ""}
${if (!constraints.isNullOrEmpty()) "## Constraints\n${constraints.joinToString("\n") { "- $it" }}\n" else ""}

## Ideas to Evaluate
${
                    allIdeas.sortedByDescending { it.novelty_score }.take(15).joinToString("\n\n") { idea ->
                        """
### ${idea.title}
**Technique:** ${idea.technique}
**Description:** ${idea.description}
**Novelty:** ${String.format("%.1f%%", idea.novelty_score * 100)}
**Initial Feasibility:** ${String.format("%.1f%%", idea.feasibility_score * 100)}
**Benefits:** ${idea.benefits.joinToString(", ")}
**Challenges:** ${idea.challenges.joinToString(", ")}
""".trim()
                    }
                }

## Instructions
1. Provide an overall feasibility assessment
2. Rank the top 5 most promising ideas by practical feasibility
3. Identify 3-5 ideas that need further exploration or prototyping
4. Suggest 2-3 hybrid approaches that combine elements from multiple ideas
5. Consider implementation complexity, resource requirements, and risk

Provide a structured evaluation.
            """.trimIndent()

                val feasibilityParser = ParsedAgent(
                    resultClass = FeasibilityEvaluation::class.java,
                    prompt = feasibilityPrompt,
                    model = defaultSmart.getChildClient(task),
                    temperature = 0.4,
                    name = "FeasibilityEvaluation",
                    parsingChatter = defaultFast,
                )

                feasibilityEvaluation = feasibilityParser.answer(listOf(feasibilityPrompt)).obj

                if (feasibilityEvaluation != null) {
                    feasibilityTask.add(buildString {
                      transcript?.write("\n---\n\n## Evaluation Results\n\n**Status:** ✓ Complete\n\n".toByteArray())
                      appendLine()
                      appendLine("---")
                      appendLine()
                      appendLine("## Evaluation Results")
                      appendLine()
                      appendLine("**Status:** ✓ Complete")
                      appendLine()
                      appendLine("### Overall Assessment")
                      appendLine()
                      appendLine(feasibilityEvaluation.overall_assessment)
                      appendLine()
                      appendLine("### Top Ideas by Feasibility")
                      appendLine()
                      feasibilityEvaluation.top_ideas.forEachIndexed { idx, idea ->
                        appendLine("${idx + 1}. $idea")
                      }
                      appendLine()
                      appendLine("### Ideas for Further Exploration")
                      appendLine()
                      feasibilityEvaluation.ideas_for_exploration.forEach { appendLine("- $it") }
                      appendLine()
                      if (feasibilityEvaluation.hybrid_approaches.isNotEmpty()) {
                        appendLine("### Hybrid Approaches")
                        appendLine()
                        feasibilityEvaluation.hybrid_approaches.forEach { appendLine("- $it") }
                      }
                                        }.renderMarkdown(true))
                    transcript?.write(this.toString().toByteArray())
                }

                overviewTask.add(buildString {
                  transcript?.write("\n- ✓ Feasibility evaluation complete\n".toByteArray())
                  appendLine()
                  appendLine("- ✓ Feasibility evaluation complete")
                                }.renderMarkdown(true))
                feasibilityTask.complete()
            }

            // Step 4: Create final result
            val result = LateralThinkingResult(
                technique_applications = techniqueApplications,
                all_ideas = allIdeas,
                synthesized_insights = extractInsights(synthesisText),
                recommended_approaches = recommendedApproaches,
                feasibility_evaluation = feasibilityEvaluation
            )

            // Step 5: Format final output
            log.info("Formatting final results")
            val summaryTask = tabs.newTask("Summary")

            val summaryContent = formatSummary(result, problem, techniques)
            summaryTask.add(summaryContent.renderMarkdown(true))
            transcript?.write("\n${summaryContent}\n".toByteArray())
            summaryTask.complete()

            // Create concise result text
            val resultText = buildString {
                appendLine("# Lateral Thinking Results")
                appendLine()
                appendLine("**Problem:** $problem")
                appendLine()
                appendLine("## Techniques Applied")
                techniques.forEach { appendLine("- ${it.capitalize()}") }
                appendLine()
                appendLine("## Key Statistics")
                appendLine("- **Total Ideas Generated:** ${allIdeas.size}")
                appendLine(
                    "- **Average Novelty:** ${
                        String.format(
                            "%.1f%%",
                            allIdeas.map { it.novelty_score }.average() * 100
                        )
                    }"
                )
                appendLine(
                    "- **Average Feasibility:** ${
                        String.format(
                            "%.1f%%",
                            allIdeas.map { it.feasibility_score }.average() * 100
                        )
                    }"
                )
                appendLine()
                appendLine("## Top Breakthrough Ideas")
                allIdeas.sortedByDescending { it.novelty_score * it.feasibility_score }
                    .take(5)
                    .forEachIndexed { idx, idea ->
                        appendLine("${idx + 1}. **${idea.title}** (${idea.technique})")
                        appendLine("   ${idea.description.truncateForDisplay(maxDescriptionLength)}")
                        appendLine()
                    }
                appendLine("## Recommended Approaches")
                recommendedApproaches.forEach { appendLine("- $it") }
                appendLine()
                if (feasibilityEvaluation != null) {
                    appendLine("## Feasibility Assessment")
                    appendLine(feasibilityEvaluation.overall_assessment.truncateForDisplay(maxDescriptionLength))
                    appendLine()
                }
                appendLine("*See the Summary tab for complete analysis and all generated ideas*")
            }

            // Final overview update
            val totalTime = System.currentTimeMillis() - startTime
            overviewTask.add(buildString {
              appendLine()
              appendLine("---")
              appendLine()
              appendLine("## ✓ Task Complete")
              appendLine()
              appendLine("| Metric | Value |")
              appendLine("|--------|-------|")
              appendLine("| Techniques Applied | ${techniques.size} |")
              appendLine("| Total Ideas | ${allIdeas.size} |")
              appendLine(
                "| Avg Novelty | ${
                  String.format(
                    "%.1f%%",
                    allIdeas.map { it.novelty_score }.average() * 100
                  )
                } |"
              )
              appendLine(
                "| Avg Feasibility | ${
                  String.format(
                    "%.1f%%",
                    allIdeas.map { it.feasibility_score }.average() * 100
                  )
                } |"
              )
              appendLine("| Total Time | ${totalTime / 1000}s |")
              appendLine()
              appendLine("**Status:** ✓ Complete")
              transcript?.write(this.toString().toByteArray())
                        }.renderMarkdown(true))
            overviewTask.complete()

            log.info(
                "LateralThinkingTask completed: total_time=${totalTime}ms, techniques=${techniques.size}, ideas=${allIdeas.size}, avg_novelty=${
                    allIdeas.map { it.novelty_score }.average()
                }"
            )
            task.safeComplete(
                "Completed in ${totalTime / 1000}s with ${allIdeas.size} ideas across ${techniques.size} techniques.",
                log
            )
            // Create summary message with transcript link
                val transcriptLink = task.saveFile("lateral_thinking_summary.md", resultText.toByteArray())
            val summaryMessage = buildString {
                appendLine(resultText)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(
                    "📄 **Full Analysis:** [View Transcript]($transcriptLink) | [HTML](${transcriptLink.removeSuffix(".md")}.html) | [PDF](${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.pdf)"
                )
            }
            resultFn(summaryMessage)

        } catch (e: Exception) {
            log.error("Error during LateralThinkingTask execution", e)
            task.error(e)
                transcript?.write("\n## Error\n<details>\n<summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
            task.safeComplete("Failed with error: ${e.message}", log)
            resultFn("ERROR: ${e.message}")
            } finally {
                transcript?.close()
        }
        }
    }


    private fun getTechniqueDescription(technique: String): String {
        return when (technique.lowercase()) {
            "reversal" -> """
## Reversal Technique
Instead of solving the problem, reverse it. Ask "How could we make this worse?" or "What if we did the opposite?"
This helps identify hidden assumptions and can lead to breakthrough insights.
            """.trimIndent()

            "random_stimulus" -> """
## Random Stimulus Technique
Introduce a completely unrelated concept, object, or idea and force connections to the problem.
This breaks mental patterns and can spark unexpected creative solutions.
            """.trimIndent()

            "challenge_assumptions" -> """
## Challenge Assumptions Technique
Identify and question the fundamental assumptions underlying the problem.
Ask "What if this assumption wasn't true?" to open new solution spaces.
            """.trimIndent()

            "exaggeration" -> """
## Exaggeration Technique
Amplify aspects of the problem to extremes. Make it 10x, 100x, or 1000x bigger or smaller.
Extreme scenarios often reveal insights applicable to the original problem.
            """.trimIndent()

            "escape" -> """
## Escape Technique
Temporarily ignore a key constraint or requirement that seems immovable.
This mental freedom can reveal solutions that work around the constraint in unexpected ways.
            """.trimIndent()

            "metaphor" -> """
## Metaphor Technique
Describe the problem using metaphors from completely different domains.
Metaphorical thinking can reveal structural similarities and novel approaches.
            """.trimIndent()

            "provocation" -> """
## Provocation Technique
Make deliberately absurd or provocative statements about the problem.
Use "Po" (provocative operation) statements to jar thinking out of established patterns.
            """.trimIndent()

            else -> "## $technique Technique\nApplying lateral thinking technique: $technique"
        }
    }

    private fun buildTechniquePrompt(
        technique: String,
        problem: String,
        numAlternatives: Int,
        domainContext: String?,
        constraints: List<String>?,
        priorContext: String
    ): String {
        val baseContext = buildString {
            appendLine("## Problem")
            appendLine(problem)
            appendLine()
            if (domainContext != null) {
                appendLine("## Domain Context")
                appendLine(domainContext)
                appendLine()
            }
            if (!constraints.isNullOrEmpty()) {
                appendLine("## Constraints")
                constraints.forEach { appendLine("- $it") }
                appendLine()
            }
            if (priorContext.isNotBlank()) {
                appendLine("## Additional Context")
                appendLine(priorContext.take(2000))
                appendLine()
            }
        }

        return when (technique.lowercase()) {
            "reversal" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Reversal
Apply the reversal technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Reverse the problem: Instead of solving it, how would you make it worse or achieve the opposite?
2. For each reversal, identify what it reveals about hidden assumptions
3. Transform the reversal back into a positive, unconventional solution
4. Explain how each idea breaks conventional thinking
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using reversal thinking.
            """.trimIndent()

            "random_stimulus" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Random Stimulus
Apply the random stimulus technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Introduce $numAlternatives completely unrelated concepts (e.g., "ocean waves", "jazz music", "ant colonies", "origami", "thunderstorms")
2. For each random stimulus, force connections to the problem
3. Develop each connection into a concrete, unconventional solution
4. Explain how the random stimulus sparked the breakthrough
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using random stimuli.
            """.trimIndent()

            "challenge_assumptions" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Challenge Assumptions
Apply assumption-challenging to generate $numAlternatives unconventional ideas.

### Instructions
1. Identify $numAlternatives fundamental assumptions underlying the problem
2. For each assumption, ask "What if this wasn't true?"
3. Develop solutions that work in the assumption-free space
4. Explain how challenging the assumption opens new possibilities
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas by challenging assumptions.
            """.trimIndent()

            "exaggeration" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Exaggeration
Apply exaggeration to generate $numAlternatives unconventional ideas.

### Instructions
1. Create $numAlternatives extreme scenarios (10x, 100x, 1000x scale changes, or reduce to zero)
2. For each exaggeration, explore what solutions would work in that extreme
3. Scale the solution back to find insights applicable to the original problem
4. Explain how the exaggeration revealed the breakthrough
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using exaggeration.
            """.trimIndent()

            "escape" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Escape
Apply the escape technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Identify $numAlternatives key constraints that seem immovable
2. For each constraint, temporarily ignore it completely
3. Develop solutions in this constraint-free space
4. Find ways to work around or reframe the original constraint
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas by escaping constraints.
            """.trimIndent()

            "metaphor" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Metaphor
Apply metaphorical thinking to generate $numAlternatives unconventional ideas.

### Instructions
1. Create $numAlternatives metaphors from different domains (nature, music, sports, cooking, etc.)
2. For each metaphor, identify structural similarities to the problem
3. Develop solutions by applying the metaphor's logic
4. Explain how the metaphor reveals new perspectives
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using metaphorical thinking.
            """.trimIndent()

            "provocation" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Provocation
Apply provocative statements to generate $numAlternatives unconventional ideas.

### Instructions
1. Create $numAlternatives deliberately absurd or provocative "Po" statements about the problem
2. For each provocation, explore what it suggests (even if impossible)
3. Extract practical insights from the provocative thinking
4. Develop concrete solutions inspired by the provocation
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using provocations.
            """.trimIndent()

            else -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: $technique
Apply the $technique technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Apply the $technique technique systematically
2. Generate $numAlternatives unconventional ideas
3. Explain how each idea breaks conventional thinking
4. Identify benefits and challenges for each
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using $technique.
            """.trimIndent()
        }
    }

    private fun extractRecommendedApproaches(synthesisText: String): List<String> {
        // Simple extraction - look for numbered or bulleted lists in sections about recommendations
        val lines = synthesisText.lines()
        val recommendations = mutableListOf<String>()

        var inRecommendationSection = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("recommend", ignoreCase = true) ||
                trimmed.contains("approach", ignoreCase = true) ||
                trimmed.contains("suggestion", ignoreCase = true)
            ) {
                inRecommendationSection = true
                continue
            }
            if (inRecommendationSection) {
                if (trimmed.startsWith("-") || trimmed.matches(Regex("^\\d+\\..*"))) {
                    val cleaned = trimmed.removePrefix("-").removePrefix(Regex("^\\d+\\.")).trim()
                    if (cleaned.isNotEmpty() && cleaned.length > 10) {
                        recommendations.add(cleaned)
                    }
                } else if (trimmed.isEmpty() && recommendations.isNotEmpty()) {
                    break
                }
            }
        }

        return recommendations.take(5)
    }

    private fun extractInsights(synthesisText: String): List<String> {
        // Simple extraction - look for bulleted or numbered insights
        val lines = synthesisText.lines()
        val insights = mutableListOf<String>()

        var inInsightSection = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("insight", ignoreCase = true) ||
                trimmed.contains("theme", ignoreCase = true) ||
                trimmed.contains("pattern", ignoreCase = true)
            ) {
                inInsightSection = true
                continue
            }
            if (inInsightSection) {
                if (trimmed.startsWith("-") || trimmed.matches(Regex("^\\d+\\..*"))) {
                    //val cleaned = trimmed.removePrefix("-").removePrefix(Regex("^\\d+\\.")).trim()
                    val cleaned = trimmed.removePrefix("-").removePrefix(Regex("^\\d+\\.")).trim()
                    if (cleaned.isNotEmpty() && cleaned.length > 10) {
                        insights.add(cleaned)
                    }
                } else if (trimmed.isEmpty() && insights.isNotEmpty()) {
                    break
                }
            }
        }

        return insights.take(10)
    }

    private fun formatSummary(
        result: LateralThinkingResult,
        problem: String,
        techniques: List<String>
    ): String {
        return buildString {
            appendLine("# Lateral Thinking Summary")
            appendLine()
            appendLine("## Problem Statement")
            appendLine()
            appendLine("> $problem")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Executive Summary")
            appendLine()
            appendLine("Applied ${techniques.size} lateral thinking techniques to generate ${result.all_ideas.size} unconventional ideas.")
            appendLine()
            val avgNovelty = result.all_ideas.map { it.novelty_score }.average()
            val avgFeasibility = result.all_ideas.map { it.feasibility_score }.average()
            appendLine("**Average Novelty:** ${String.format("%.1f%%", avgNovelty * 100)}")
            appendLine()
            appendLine("**Average Feasibility:** ${String.format("%.1f%%", avgFeasibility * 100)}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Top Breakthrough Ideas")
            appendLine()
            result.all_ideas
                .sortedByDescending { it.novelty_score * it.feasibility_score }
                .take(10)
                .forEachIndexed { idx, idea ->
                    appendLine("### ${idx + 1}. ${idea.title}")
                    appendLine()
                    appendLine(
                        "**Technique:** ${idea.technique.capitalize()} | **Novelty:** ${
                            String.format(
                                "%.1f%%",
                                idea.novelty_score * 100
                            )
                        } | **Feasibility:** ${String.format("%.1f%%", idea.feasibility_score * 100)}"
                    )
                    appendLine()
                    appendLine("#### Description")
                    appendLine(idea.description)
                    appendLine()
                    appendLine("#### Breakthrough Aspect")
                    appendLine(idea.breakthrough_aspect)
                    appendLine()
                    if (idea.benefits.isNotEmpty()) {
                        appendLine("#### Benefits")
                        idea.benefits.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    if (idea.challenges.isNotEmpty()) {
                        appendLine("#### Challenges")
                        idea.challenges.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    if (idea.implementation_steps.isNotEmpty()) {
                        appendLine("#### Implementation Steps")
                        idea.implementation_steps.forEach { appendLine("${it.split(".").firstOrNull() ?: ""}. ${it}") }
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

            appendLine("## Synthesized Insights")
            appendLine()
            result.synthesized_insights.forEach { appendLine("- $it") }
            appendLine()

            appendLine("## Recommended Unconventional Approaches")
            appendLine()
            result.recommended_approaches.forEachIndexed { idx, approach ->
                appendLine("${idx + 1}. $approach")
            }
            appendLine()

            if (result.feasibility_evaluation != null) {
                appendLine("---")
                appendLine()
                appendLine("## Feasibility Evaluation")
                appendLine()
                appendLine("### Overall Assessment")
                appendLine()
                appendLine(result.feasibility_evaluation.overall_assessment)
                appendLine()
                appendLine("### Most Promising Ideas")
                appendLine()
                result.feasibility_evaluation.top_ideas.forEachIndexed { idx, idea ->
                    appendLine("${idx + 1}. $idea")
                }
                appendLine()
                if (result.feasibility_evaluation.ideas_for_exploration.isNotEmpty()) {
                    appendLine("### Ideas Requiring Further Exploration")
                    appendLine()
                    result.feasibility_evaluation.ideas_for_exploration.forEach { appendLine("- $it") }
                    appendLine()
                }
                if (result.feasibility_evaluation.hybrid_approaches.isNotEmpty()) {
                    appendLine("### Hybrid Approaches")
                    appendLine()
                    result.feasibility_evaluation.hybrid_approaches.forEach { appendLine("- $it") }
                    appendLine()
                }
            }

            appendLine("---")
            appendLine()
            appendLine("## Ideas by Technique")
            appendLine()
            result.technique_applications.forEach { app ->
                appendLine("### ${app.technique.capitalize()} (${app.ideas.size} ideas)")
                appendLine()
                appendLine("**Provocation:** ${app.provocation}")
                appendLine()
                app.ideas.sortedByDescending { it.novelty_score }.take(3).forEach { idea ->
                    appendLine("- **${idea.title}** (Novelty: ${String.format("%.0f%%", idea.novelty_score * 100)})")
                    appendLine("  ${idea.description.truncateForDisplay(maxDescriptionLength)}")
                }
                if (app.ideas.size > 3) {
                    appendLine("  *...and ${app.ideas.size - 3} more ideas*")
                }
                appendLine()
            }
        }
    }


    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

}

private fun String.removePrefix(prefix: Regex): String {
    return this.replace(prefix, "")
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/MathematicalReasoningTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MathematicalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: MathematicalReasoningTaskExecutionConfigData?
) :
  AbstractTask<MathematicalReasoningTask.MathematicalReasoningTaskExecutionConfigData, MathematicalReasoningTask.MathematicalReasoningTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(MathematicalReasoningTask::class.java)
        @JvmStatic val MathematicalReasoning = TaskType(
          name = "MathematicalReasoning",
          category = "Reasoning",
          taskClass = MathematicalReasoningTask::class.java,
          executionConfigClass = MathematicalReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = MathematicalReasoningTaskTypeConfig::class.java,
          description = "Solve mathematical problems through step-by-step logical reasoning with verifiable steps",
          tooltipHtml = """
                          Uses path search to solve mathematical problems through rigorous step-by-step reasoning.
                          <ul>
                              <li>Breaks down complex problems into verifiable atomic steps</li>
                              <li>Each step includes justification and verification</li>
                              <li>Explores multiple solution paths when needed</li>
                             <li>Backtracking when encountering dead ends</li>
                              <li>Provides detailed proof trail with MathJax notation</li>
                              <li>Supports algebra, calculus, number theory, and more</li>
                              <li>Validates intermediate results for correctness</li>
                              <li>Generates human-readable mathematical proofs</li>
                          </ul>
                      """,
        )
    }

  class MathematicalReasoningTaskTypeConfig(
    var promptTemplate: String = """
            MathematicalReasoning - Solve mathematical problems through step-by-step logical reasoning
              ** Specify the problem statement clearly
              ** Define the goal (prove, solve, simplify, etc.)
              ** Provide any given information or constraints
              ** Specify the mathematical domain if relevant
              ** Configure search parameters (depth, alternatives)
              ** The task will:
                 - Break down the problem into atomic steps
                 - Verify each step's mathematical validity
                 - Explore alternative solution paths
                 - Backtrack from dead ends
                 - Generate a complete proof trail
                 - Output results in MathJax/LaTeX format
              ** Useful for:
                 - Solving algebraic equations
                 - Proving mathematical theorems
                 - Simplifying complex expressions
                 - Step-by-step calculus problems
                 - Number theory proofs
                 - Geometric proofs
        """.trimIndent(),
    var initialStatePrompt: String = """
            You are a mathematical reasoning expert. Analyze the initial state of a mathematical problem.
            ## Problem Statement
            {{problem_statement}}
            ## Goal
            {{goal}}
            ## Given Information
            {{given_info}}
            ## Domain
            {{domain}}
            ## Instructions
            Create the initial reasoning step that captures the starting state of the problem.
            - Restate the problem in precise mathematical terms
            - Identify the key variables and relationships
            - Express the initial state in LaTeX notation
            - Set step_type to "initial"
            - Set step_id to "S0"
        """.trimIndent(),
    var stepGeneratorPrompt: String = """
            You are a mathematical reasoning expert. Generate the next logical step in solving a problem.
            ## Problem Statement
            {{problem_statement}}
            ## Goal
            {{goal}}
            ## Given Information
            {{given_info}}
            ## Domain
            {{domain}}
            ## Current Progress
            {{current_progress}}
            ## Instructions
            Generate the next logical step that moves us closer to the goal.
            Requirements:
            1. The step must be mathematically valid and follow from previous steps
            2. Provide clear justification (cite theorem, rule, or operation used)
            3. Include proper LaTeX notation
            4. Choose appropriate step_type: 'algebraic', 'substitution', 'simplification', 'theorem', 'inference', 'definition'
            5. Estimate confidence (0-100) based on how certain the step is
            6. Keep steps atomic - one transformation at a time
            7. Detail level: {{detail_level}}
            Focus on making progress toward: {{goal}}
        """.trimIndent(),
    var stepVerifierPrompt: String = """
            You are a mathematical verification expert. Verify if a reasoning step is valid.
            ## Domain
            {{domain}}
            ## Previous Steps
            {{previous_steps}}
            ## Step to Verify
            Statement: {{statement}}
            LaTeX: {{latex}}
            Justification: {{justification}}
            Type: {{step_type}}
            ## Instructions
            Verify this step is mathematically valid:
            1. Check if it follows logically from previous steps
            2. Verify the mathematical operations are correct
            3. Confirm the justification is appropriate
            4. Look for any errors in algebra, logic, or notation
            Be rigorous but fair - minor notation issues are acceptable if the mathematics is sound.
        """.trimIndent(),
    var goalCheckerPrompt: String = """
            You are a mathematical reasoning expert. Check if the goal has been reached.
            ## Goal
            {{goal}}
            ## Current State
            {{current_state}}
            ## Full Progress
            {{full_progress}}
            ## Instructions
            Determine if the goal has been achieved:
            1. Compare the current state to the goal
            2. Estimate progress (0-100%)
            3. If not complete, describe what remains
            4. Be precise about whether the goal is fully achieved
        """.trimIndent(),
    var alternativeGeneratorPrompt: String = """
            You are a mathematical reasoning expert. Generate alternative approaches.
            ## Problem
            {{problem_statement}}
            ## Goal
            {{goal}}
            ## Domain
            {{domain}}
            ## Current Path (last 3 steps)
            {{current_path}}
            ## Instructions
            Generate 2-3 alternative next steps that could be taken from the current state.
            Each alternative should:
            1. Be a valid mathematical operation
            2. Take a different approach than the current path
            3. Have potential to reach the goal
            Rank them by likelihood of success.
        """.trimIndent()
  ) : TaskTypeConfig()


  class MathematicalReasoningTaskExecutionConfigData(
        @Description("The mathematical problem or theorem to solve/prove")
        var problem_statement: String? = null,
        @Description("The goal or target result (e.g., 'prove equality', 'find x', 'simplify expression')")
        var goal: String? = null,
        @Description("Known facts, axioms, or given information")
        var given_information: List<String>? = null,
        @Description("Mathematical domain (e.g., 'algebra', 'calculus', 'number_theory', 'geometry', 'linear_algebra')")
        var domain: String? = "general",
        @Description("Maximum depth of reasoning steps (default: 20)")
        var max_depth: Int = 20,
        @Description("Maximum number of alternative paths to explore (default: 3)")
        var max_alternatives: Int = 3,
        @Description("Whether to show all explored paths or just the successful one")
        var show_all_paths: Boolean = false,
        @Description("Level of detail in explanations ('brief', 'standard', 'detailed')")
        var detail_level: String = "standard",

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = MathematicalReasoning.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (problem_statement.isNullOrBlank()) {
                return "problem_statement must not be blank"
            }
            if (max_depth < 1 || max_depth > 100) {
                return "max_depth must be between 1 and 100"
            }
            if (max_alternatives < 1 || max_alternatives > 10) {
                return "max_alternatives must be between 1 and 10"
            }
            if (detail_level !in listOf("brief", "standard", "detailed")) {
                return "detail_level must be 'brief', 'standard', or 'detailed'"
            }
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class ReasoningStep(
        @Description("Unique identifier for this step")
        val step_id: String = "",
        @Description("The mathematical statement or transformation at this step")
        val statement: String = "",
        @Description("LaTeX/MathJax representation of the statement")
        val latex: String = "",
        @Description("Justification for this step (theorem, axiom, or rule applied)")
        val justification: String = "",
        @Description("Type of step: 'axiom', 'definition', 'theorem', 'algebraic', 'substitution', 'simplification', 'inference'")
        val step_type: String = "",
        @Description("Confidence in this step's correctness (0-100)")
        val confidence: Int = 100,
        @Description("Whether this step has been verified")
        val verified: Boolean = false,
        @Description("Any notes or caveats about this step")
        val notes: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (statement.isBlank()) return "statement must not be blank"
            if (confidence < 0 || confidence > 100) return "confidence must be between 0 and 100"
            return null
        }
    }

    data class ReasoningPath(
        @Description("Sequence of reasoning steps")
        val steps: List<ReasoningStep> = emptyList(),
        @Description("Whether this path reached the goal")
        val reached_goal: Boolean = false,
        @Description("If not reached, reason for stopping")
        val termination_reason: String = "",
        @Description("Overall confidence in this path")
        val path_confidence: Int = 0
    )

    data class NextStepOptions(
        @Description("List of possible next steps to explore")
        val options: List<ReasoningStep> = emptyList(),
        @Description("Recommended option index (0-based)")
        val recommended_index: Int = 0,
        @Description("Reasoning for the recommendation")
        val recommendation_reason: String = ""
    )

    data class StepVerification(
        @Description("Whether the step is mathematically valid")
        val is_valid: Boolean = false,
        @Description("Explanation of the verification")
        val explanation: String = "",
        @Description("Any errors or issues found")
        val errors: List<String> = emptyList(),
        @Description("Suggestions for correction if invalid")
        val suggestions: List<String> = emptyList()
    )

    data class GoalCheck(
        @Description("Whether the current state matches the goal")
        val goal_reached: Boolean = false,
        @Description("How close we are to the goal (0-100)")
        val progress: Int = 0,
        @Description("What remains to be done")
        val remaining_work: String = "",
        @Description("Explanation of the assessment")
        val explanation: String = ""
    )

    override fun promptSegment(): String {
      return typeConfig?.promptTemplate ?: ""
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
      task.ui.pool.submit {
        try {
            val startTime = System.currentTimeMillis()
          log.info("MathematicalReasoningTask started. Problem: ${executionConfig?.problem_statement?.take(50)}...")

            // Validate configuration
            executionConfig?.validate()?.let { errorMessage ->
              log.error("MathematicalReasoningTask validation failed: $errorMessage")
                task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
              transcript?.write("## Validation Error\n$errorMessage".toByteArray(StandardCharsets.UTF_8))
                resultFn("VALIDATION ERROR: $errorMessage")
              return@submit
            }

            val problemStatement = executionConfig?.problem_statement ?: ""
            val goal = executionConfig?.goal ?: "solve"
            val givenInfo = executionConfig?.given_information ?: emptyList()
            val domain = executionConfig?.domain ?: "general"
            val maxDepth = executionConfig?.max_depth ?: 20
            val maxAlternatives = executionConfig?.max_alternatives ?: 3
            val showAllPaths = executionConfig?.show_all_paths ?: false
            val detailLevel = executionConfig?.detail_level ?: "standard"

            val tabs = TabbedDisplay(task)
            val api = defaultSmart

            // Create overview tab
            val overviewTask = tabs.newTask("Overview")
            val overviewContent = buildString {
                appendLine("# Mathematical Reasoning Task")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Problem Statement")
                appendLine()
                appendLine(problemStatement)
                appendLine()
                appendLine("## Goal")
                appendLine()
                appendLine(goal)
                appendLine()
                if (givenInfo.isNotEmpty()) {
                    appendLine("## Given Information")
                    appendLine()
                    givenInfo.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("## Configuration")
                appendLine()
                appendLine("| Parameter | Value |")
                appendLine("|-----------|-------|")
                appendLine("| Domain | $domain |")
                appendLine("| Max Depth | $maxDepth |")
                appendLine("| Max Alternatives | $maxAlternatives |")
                appendLine("| Detail Level | $detailLevel |")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Analyzing problem...")
            }
          overviewTask.add(overviewContent.renderMarkdown())
          transcript?.write(
            """
                <details>
                <summary>Task Configuration & Context</summary>
                
                $overviewContent
                </details>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
          )

            // Gather context
            val priorContext = getPriorCode(agent.executionState)

            // Initialize the search
            val exploredPaths = mutableListOf<ReasoningPath>()
            var successfulPath: ReasoningPath? = null

            // Create solution tab
            val solutionTask = tabs.newTask("Solution")

            // Path search loop
            var pathsExplored = 0
            val pathQueue = PriorityQueue<Pair<List<ReasoningStep>, Int>>(compareByDescending { it.second })

            // Initialize with starting state
            val initialStep = analyzeInitialState(problemStatement, goal, givenInfo, domain, api)
            pathQueue.add(Pair(listOf(initialStep), 100))

            while (pathQueue.isNotEmpty() && pathsExplored < maxAlternatives && successfulPath == null) {
                val (currentPath, priority) = pathQueue.poll()
                pathsExplored++


              log.info("Exploring path $pathsExplored. Steps: ${currentPath.size}, Priority: $priority")

              overviewTask.add("\n- 🔍 Exploring path $pathsExplored (${currentPath.size} steps)...".renderMarkdown())

                // Explore this path
                val result = explorePath(
                    currentPath = currentPath,
                    problemStatement = problemStatement,
                    goal = goal,
                    givenInfo = givenInfo,
                    domain = domain,
                    maxDepth = maxDepth,
                    detailLevel = detailLevel,
                    api = api,
                    solutionTask = solutionTask,
                    task = task,
                    pathNumber = pathsExplored
                )

                exploredPaths.add(result)

                if (result.reached_goal) {
                    successfulPath = result
                  log.info("MathematicalReasoningTask found solution in ${result.steps.size} steps.")
                } else if (result.steps.size < maxDepth) {
                    // Generate alternative branches from the last valid step
                    val alternatives = generateAlternatives(
                        currentPath = result.steps,
                        problemStatement = problemStatement,
                        goal = goal,
                        domain = domain,
                        api = api
                    )
                    alternatives.options.forEachIndexed { index, step ->
                        if (pathQueue.size < maxAlternatives * 2) {
                            val newPath = result.steps.dropLast(1) + step
                            pathQueue.add(Pair(newPath, step.confidence))
                        }
                    }
                }
            }

            // Create proof tab if successful
            if (successfulPath != null) {
                val proofTask = tabs.newTask("Formal Proof")
                val proofContent = generateFormalProof(successfulPath, problemStatement, goal, detailLevel)
              proofTask.add(proofContent.renderMarkdown())
                proofTask.complete()
                transcript?.write("\n\n---\n\n# Formal Proof\n\n".toByteArray(StandardCharsets.UTF_8))
                transcript?.write(proofContent.toByteArray(StandardCharsets.UTF_8))
            }

            // Show all paths if requested
            if (showAllPaths && exploredPaths.size > 1) {
                val pathsTask = tabs.newTask("All Paths")
                val pathsContent = buildString {
                    appendLine("# All Explored Paths")
                    appendLine()
                    exploredPaths.forEachIndexed { index, path ->
                        appendLine("## Path ${index + 1}")
                        appendLine()
                        appendLine("**Status:** ${if (path.reached_goal) "✅ Success" else "❌ ${path.termination_reason}"}")
                        appendLine()
                        appendLine("**Steps:** ${path.steps.size}")
                        appendLine()
                        appendLine("**Confidence:** ${path.path_confidence}%")
                        appendLine()
                        path.steps.forEachIndexed { stepIndex, step ->
                            appendLine("${stepIndex + 1}. ${step.statement}")
                            if (step.latex.isNotBlank()) {
                                appendLine("   $$${step.latex}$$")
                            }
                        }
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                }
              pathsTask.add(pathsContent.renderMarkdown())
                pathsTask.complete()
              transcript?.write(
                """
                    <details>
                    <summary>All Explored Reasoning Paths</summary>
                    $pathsContent
                    </details>
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
              )
            }

            // Final summary
            val totalTime = System.currentTimeMillis() - startTime
            val finalOverview = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                if (successfulPath != null) {
                    appendLine("## ✅ Solution Found")
                    appendLine()
                    appendLine("| Metric | Value |")
                    appendLine("|--------|-------|")
                    appendLine("| Steps | ${successfulPath.steps.size} |")
                    appendLine("| Paths Explored | $pathsExplored |")
                    appendLine("| Confidence | ${successfulPath.path_confidence}% |")
                    appendLine("| Time | ${totalTime / 1000}s |")
                } else {
                    appendLine("## ❌ No Solution Found")
                    appendLine()
                    appendLine("Explored $pathsExplored paths without finding a complete solution.")
                    appendLine()
                    appendLine(
                        "**Best attempt:** ${
                            exploredPaths.maxByOrNull { it.path_confidence }
                                ?.let { "${it.steps.size} steps, ${it.path_confidence}% confidence" } ?: "None"
                        }")
                }
            }
          overviewTask.add(finalOverview.renderMarkdown())
            overviewTask.complete()
            solutionTask.complete()
            transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))

            // Generate result
            val resultMessage = if (successfulPath != null) {
                buildString {
                    appendLine("# Solution")
                    appendLine()
                    appendLine("## Problem")
                    appendLine(problemStatement)
                    appendLine()
                    appendLine("## Answer")
                    appendLine()
                    val finalStep = successfulPath.steps.last()
                    if (finalStep.latex.isNotBlank()) {
                        appendLine("$$${finalStep.latex}$$")
                    }
                    appendLine()
                    appendLine(finalStep.statement)
                    appendLine()
                    appendLine("## Key Steps")
                    appendLine()
                    successfulPath.steps.filter { it.step_type != "initial" }.take(5).forEach { step ->
                        appendLine("- ${step.statement}")
                        if (step.latex.isNotBlank() && step.latex != step.statement) {
                            appendLine("  - $${step.latex}$")
                        }
                    }
                    if (successfulPath.steps.size > 6) {
                        appendLine("- ... (${successfulPath.steps.size - 6} more steps)")
                    }
                }
            } else {
                "Unable to find a complete solution after exploring $pathsExplored paths. See the Solution tab for partial progress."
            }

          task.complete("Mathematical reasoning complete in ${totalTime / 1000}s".renderMarkdown())
            resultFn(resultMessage)

        } catch (e: Exception) {
            task.error(e)
          log.error("MathematicalReasoningTask failed: ${e.message}", e)
          transcript?.write(
            """
                <details>
                <summary>Error Stack Trace</summary>
                
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
          )
            resultFn("ERROR: ${e.message}")
        } finally {
          transcript?.close()
        }
        }
    }

    private fun analyzeInitialState(
        problemStatement: String,
        goal: String,
        givenInfo: List<String>,
        domain: String,
        api: ChatInterface
    ): ReasoningStep {
        return try {
          val prompt = typeConfig?.initialStatePrompt
            ?.replace("{{problem_statement}}", problemStatement)
            ?.replace("{{goal}}", goal)
            ?.replace("{{given_info}}", givenInfo.joinToString("\n") { "- $it" }.ifEmpty { "None specified" })
            ?.replace("{{domain}}", domain) ?: ""
            ParsedAgent(
                resultClass = ReasoningStep::class.java,


              prompt = prompt,
                model = api,
                temperature = 0.3,
                name = "InitialStateAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze the initial state")).obj
        } catch (e: Exception) {
            log.warn("Failed to analyze initial state", e)
            ReasoningStep(
                step_id = "S0",
                statement = problemStatement,
                latex = "",
                justification = "Given problem statement",
                step_type = "initial",
                confidence = 100,
                verified = true
            )
        }
    }

    private fun explorePath(
        currentPath: List<ReasoningStep>,
        problemStatement: String,
        goal: String,
        givenInfo: List<String>,
        domain: String,
        maxDepth: Int,
        detailLevel: String,
        api: ChatInterface,
        solutionTask: SessionTask,
        task: SessionTask,
        pathNumber: Int
    ): ReasoningPath {
        val steps = currentPath.toMutableList()
        var depth = steps.size

        // Display current path progress
        solutionTask.add(buildString {
            appendLine("# Path $pathNumber Exploration")
            appendLine()
            appendLine("## Starting Point")
            appendLine()
            steps.forEach { step ->
                appendLine("**Step ${step.step_id}:** ${step.statement}")
                if (step.latex.isNotBlank()) {
                    appendLine()
                    appendLine("$$${step.latex}$$")
                }
                appendLine()
            }
            appendLine("---")
            appendLine()
        }.renderMarkdown())

        while (depth < maxDepth) {
            // Check if we've reached the goal
            val goalCheck = checkGoal(steps, goal, api)
            if (goalCheck.goal_reached) {
              solutionTask.add("\n✅ **Goal Reached!**\n\n${goalCheck.explanation}\n".renderMarkdown())
                return ReasoningPath(
                    steps = steps,
                    reached_goal = true,
                    termination_reason = "Goal achieved",
                    path_confidence = steps.map { it.confidence }.average().toInt()
                )
            }

            // Generate next step
            val nextStep = generateNextStep(steps, problemStatement, goal, givenInfo, domain, detailLevel, api)

            if (nextStep == null || nextStep.statement.isBlank()) {
              solutionTask.add("\n⚠️ **No valid next step found**\n".renderMarkdown())
                return ReasoningPath(
                    steps = steps,
                    reached_goal = false,
                    termination_reason = "No valid next step",
                    path_confidence = steps.map { it.confidence }.average().toInt()
                )
            }

            // Verify the step
            val verification = verifyStep(nextStep, steps, domain, api)
            if (!verification.is_valid) {
                solutionTask.add(buildString {
                    appendLine()
                    appendLine("⚠️ **Step verification failed:**")
                    appendLine()
                    appendLine("Attempted: ${nextStep.statement}")
                    appendLine()
                    verification.errors.forEach { appendLine("- ❌ $it") }
                    appendLine()
                }.renderMarkdown())

                // Try to recover with suggestions
                if (verification.suggestions.isNotEmpty()) {
                    log.debug("Attempting recovery with suggestions")
                    // Could implement recovery logic here
                }

                return ReasoningPath(
                    steps = steps,
                    reached_goal = false,
                    termination_reason = "Step verification failed: ${verification.errors.firstOrNull() ?: "Unknown error"}",
                    path_confidence = steps.map { it.confidence }.average().toInt()
                )
            }

            // Add verified step
            val verifiedStep = nextStep.copy(
                step_id = "S${steps.size}",
                verified = true
            )
            steps.add(verifiedStep)
            depth++

            // Display the new step
            solutionTask.add(buildString {
                appendLine()
                appendLine("### Step ${verifiedStep.step_id}: ${verifiedStep.step_type}")
                appendLine()
                appendLine(verifiedStep.statement)
                if (verifiedStep.latex.isNotBlank()) {
                    appendLine()
                    appendLine("$$${verifiedStep.latex}$$")
                }
                appendLine()
                appendLine("*Justification:* ${verifiedStep.justification}")
                if (verifiedStep.notes.isNotBlank()) {
                    appendLine()
                    appendLine("*Note:* ${verifiedStep.notes}")
                }
                appendLine()
            }.renderMarkdown())
        }

        return ReasoningPath(
            steps = steps,
            reached_goal = false,
            termination_reason = "Maximum depth reached",
            path_confidence = steps.map { it.confidence }.average().toInt()
        )
    }

    private fun generateNextStep(
        currentSteps: List<ReasoningStep>,
        problemStatement: String,
        goal: String,
        givenInfo: List<String>,
        domain: String,
        detailLevel: String,
        api: ChatInterface
    ): ReasoningStep? {
        return try {
          val progress = currentSteps.mapIndexed { i, step ->
            "Step $i: ${step.statement}\n  LaTeX: ${step.latex}\n  Justification: ${step.justification}"
          }.joinToString("\n\n")
          val prompt = typeConfig?.stepGeneratorPrompt
            ?.replace("{{problem_statement}}", problemStatement)
            ?.replace("{{goal}}", goal)
            ?.replace("{{given_info}}", givenInfo.joinToString("\n") { "- $it" }.ifEmpty { "None specified" })
            ?.replace("{{domain}}", domain)
            ?.replace("{{current_progress}}", progress)
            ?.replace("{{detail_level}}", detailLevel) ?: ""
          ParsedAgent(
            resultClass = ReasoningStep::class.java,


            prompt = prompt,
                model = api,
                temperature = 0.4,
                name = "StepGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate the next step")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate next step", e)
            null
        }
    }

    private fun verifyStep(
        step: ReasoningStep,
        previousSteps: List<ReasoningStep>,
        domain: String,
        api: ChatInterface
    ): StepVerification {
        return try {
          val prev = previousSteps.takeLast(3).mapIndexed { i, s ->
            "Step ${previousSteps.size - 3 + i}: ${s.statement}\n  LaTeX: ${s.latex}"
          }.joinToString("\n\n")
          val prompt = typeConfig?.stepVerifierPrompt
            ?.replace("{{domain}}", domain)
            ?.replace("{{previous_steps}}", prev)
            ?.replace("{{statement}}", step.statement)
            ?.replace("{{latex}}", step.latex)
            ?.replace("{{justification}}", step.justification)
            ?.replace("{{step_type}}", step.step_type) ?: ""
            ParsedAgent(
                resultClass = StepVerification::class.java,


              prompt = prompt,
                model = api,
                temperature = 0.2,
                name = "StepVerifier",
                parsingChatter = defaultFast
            ).answer(listOf("Verify this step")).obj
        } catch (e: Exception) {
            log.warn("Failed to verify step", e)
            StepVerification(
                is_valid = true,
                explanation = "Verification skipped due to error",
                errors = emptyList(),
                suggestions = emptyList()
            )
        }
    }

    private fun checkGoal(
        steps: List<ReasoningStep>,
        goal: String,
        api: ChatInterface
    ): GoalCheck {
        return try {
          val latest = steps.lastOrNull()?.let { "Latest step: ${it.statement}\nLaTeX: ${it.latex}" } ?: "No steps yet"
          val full = steps.takeLast(5).mapIndexed { i, s -> "${i + 1}. ${s.statement}" }.joinToString("\n")
          val prompt = typeConfig?.goalCheckerPrompt
            ?.replace("{{goal}}", goal)
            ?.replace("{{current_state}}", latest)
            ?.replace("{{full_progress}}", full) ?: ""
            ParsedAgent(
                resultClass = GoalCheck::class.java,


              prompt = prompt,
                model = api,
                temperature = 0.2,
                name = "GoalChecker",
                parsingChatter = defaultFast
            ).answer(listOf("Check if goal is reached")).obj
        } catch (e: Exception) {
            log.warn("Failed to check goal", e)
            GoalCheck(
                goal_reached = false,
                progress = 0,
                remaining_work = "Unable to assess",
                explanation = "Goal check failed"
            )
        }
    }

    private fun generateAlternatives(
        currentPath: List<ReasoningStep>,
        problemStatement: String,
        goal: String,
        domain: String,
        api: ChatInterface
    ): NextStepOptions {
        return try {
          val path = currentPath.takeLast(3).mapIndexed { i, s -> "Step ${currentPath.size - 3 + i}: ${s.statement}" }
            .joinToString("\n")
          val prompt = typeConfig?.alternativeGeneratorPrompt
            ?.replace("{{problem_statement}}", problemStatement)
            ?.replace("{{goal}}", goal)
            ?.replace("{{domain}}", domain)
            ?.replace("{{current_path}}", path) ?: ""
            ParsedAgent(
                resultClass = NextStepOptions::class.java,


              prompt = prompt,
                model = api,
                temperature = 0.6,
                name = "AlternativeGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate alternatives")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate alternatives", e)
            NextStepOptions(
                options = emptyList(),
                recommended_index = 0,
                recommendation_reason = "Failed to generate alternatives"
            )
        }
    }

    private fun generateFormalProof(
        path: ReasoningPath,
        problemStatement: String,
        goal: String,
        detailLevel: String
    ): String {
        return buildString {
            appendLine("# Formal Proof")
            appendLine()
            appendLine("## Problem Statement")
            appendLine()
            appendLine(problemStatement)
            appendLine()
            appendLine("## Goal")
            appendLine()
            appendLine(goal)
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Proof")
            appendLine()

            path.steps.forEachIndexed { index, step ->
                if (step.step_type == "initial") {
                    appendLine("**Given:**")
                } else {
                    appendLine("**Step $index** (${step.step_type}):")
                }
                appendLine()

                if (step.latex.isNotBlank()) {
                    appendLine("$$${step.latex}$$")
                    appendLine()
                }

                appendLine(step.statement)
                appendLine()

                if (detailLevel != "brief" && step.justification.isNotBlank()) {
                    appendLine("*Justification:* ${step.justification}")
                    appendLine()
                }

                if (detailLevel == "detailed" && step.notes.isNotBlank()) {
                    appendLine("> ${step.notes}")
                    appendLine()
                }
            }

            appendLine("---")
            appendLine()
            appendLine("## Conclusion")
            appendLine()
            val finalStep = path.steps.last()
            appendLine("$$${finalStep.latex}$$")
            appendLine()
            appendLine("**Q.E.D.** ∎")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("*Proof completed in ${path.steps.size} steps with ${path.path_confidence}% confidence.*")
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/MetaCognitiveReflectionTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
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
        @JvmStatic val MetaCognitiveReflection = TaskType(
          name = "MetaCognitiveReflection",
          category = "Reasoning",
          taskClass = MetaCognitiveReflectionTask::class.java,
          executionConfigClass = MetaCognitiveReflectionTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Reflect on and critique reasoning processes",
          tooltipHtml = """
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/NeuralNetworkLayerTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NeuralNetworkLayerTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: NeuralNetworkLayerTaskExecutionConfigData?
) : AbstractTask<NeuralNetworkLayerTask.NeuralNetworkLayerTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(NeuralNetworkLayerTask::class.java)
        @JvmStatic val NeuralNetworkLayer = TaskType(
            name = "NeuralNetworkLayer",
            category = "Writing",
            taskClass = NeuralNetworkLayerTask::class.java,
            executionConfigClass = NeuralNetworkLayerTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Design and analyze neural network layers with formal mathematical definitions and intuitive explanations",
            tooltipHtml = """
                          Comprehensive neural network layer design and analysis tool with both rigorous mathematics and intuitive explanations.
                          <ul>
                              <li>Executive summary with key insights</li>
                              <li>Intuitive explanations with real-world analogies</li>
                              <li>Visual conceptual diagrams</li>
                              <li>Formal mathematical definition of the layer function</li>
                              <li>Forward pass implementation with detailed equations</li>
                              <li>Backward pass (gradient) derivation and implementation</li>
                              <li>Higher-order derivative analysis (Hessian, etc.)</li>
                              <li>Lyapunov stability analysis for training dynamics</li>
                              <li>Lipschitz continuity and gradient flow analysis</li>
                              <li>Numerical stability considerations</li>
                              <li>Reference implementations in multiple languages</li>
                              <li>Computational complexity analysis</li>
                              <li>Memory footprint estimation</li>
                              <li>Originality and novelty assessment</li>
                              <li>Practical use cases and applications</li>
                          </ul>
                      """,
        )
    }

    class NeuralNetworkLayerTaskExecutionConfigData(
        @Description("Name of the layer type (e.g., 'Attention', 'Convolution', 'BatchNorm', 'Custom')")
        var layer_name: String? = null,
        @Description("Mathematical description of the layer's forward function")
        var forward_function_description: String? = null,
        @Description("Input tensor shape specification (e.g., '[batch, channels, height, width]')")
        var input_shape: String? = null,
        @Description("Output tensor shape specification")
        var output_shape: String? = null,
        @Description("List of learnable parameters with their shapes")
        var parameters: List<String>? = null,
        @Description("Activation function if applicable (e.g., 'relu', 'sigmoid', 'tanh', 'none')")
        var activation: String? = "none",
        @Description("Whether to include higher-order derivative analysis")
        var include_higher_order: Boolean = true,
        @Description("Whether to include Lyapunov stability analysis")
        var include_lyapunov: Boolean = true,
        @Description("Whether to include Lipschitz analysis")
        var include_lipschitz: Boolean = true,
        @Description("Target implementation languages, e.g. 'tensorflow.js', 'pseudocode'")
        var implementation_languages: List<String>? = listOf("tensorflow.js"),
        @Description("Whether to include numerical stability analysis")
        var include_numerical_stability: Boolean = true,
        @Description("Whether to generate test cases")
        var generate_tests: Boolean = true,
        @Description("Analysis depth: 'basic', 'standard', 'comprehensive'")
        var analysis_depth: String = "standard",

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = NeuralNetworkLayer.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (layer_name.isNullOrBlank()) {
                return "layer_name must not be blank"
            }
            if (forward_function_description.isNullOrBlank()) {
                return "forward_function_description must not be blank"
            }
            if (analysis_depth !in listOf("basic", "standard", "comprehensive")) {
                return "analysis_depth must be 'basic', 'standard', or 'comprehensive'"
            }
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class ExecutiveSummary(
        @Description("One-sentence description of what the layer does")
        val one_liner: String = "",
        @Description("Key mathematical insight in plain language")
        val key_insight: String = "",
        @Description("Primary strengths of this layer")
        val strengths: List<String> = emptyList(),
        @Description("Primary limitations or weaknesses")
        val limitations: List<String> = emptyList(),
        @Description("When to use this layer (decision criteria)")
        val when_to_use: String = "",
        @Description("When NOT to use this layer")
        val when_not_to_use: String = "",
        @Description("Computational cost summary (low/medium/high)")
        val computational_cost: String = "",
        @Description("Training difficulty (easy/moderate/hard)")
        val training_difficulty: String = "",
        @Description("Recommended for beginners? (yes/no/with_caution)")
        val beginner_friendly: String = ""
    )

    data class IntuitiveExplanation(
        @Description("Real-world analogy explaining what the layer does")
        val analogy: String = "",
        @Description("Step-by-step walkthrough in plain language")
        val plain_language_walkthrough: String = "",
        @Description("Visual description of information flow")
        val information_flow_description: String = "",
        @Description("What problem does this layer solve?")
        val problem_solved: String = "",
        @Description("How does it solve the problem? (mechanism)")
        val solution_mechanism: String = "",
        @Description("Common misconceptions about this layer")
        val common_misconceptions: List<String> = emptyList(),
        @Description("Intuitive explanation of why gradients work this way")
        val gradient_intuition: String = "",
        @Description("Mental model for understanding the layer")
        val mental_model: String = ""
    )

    data class ConceptualDiagram(
        @Description("ASCII art or text-based diagram of the layer")
        val ascii_diagram: String = "",
        @Description("Description of data flow through the layer")
        val data_flow_description: String = "",
        @Description("Mermaid diagram syntax for visualization")
        val mermaid_diagram: String = "",
        @Description("Description of parameter roles")
        val parameter_roles: Map<String, String> = emptyMap()
    )

    data class LayerDefinition(
        @Description("Formal mathematical notation for the forward function")
        val forward_equation: String = "",
        @Description("LaTeX representation of the forward function")
        val forward_latex: String = "",
        @Description("Domain constraints on inputs")
        val domain_constraints: List<String> = emptyList(),
        @Description("Range/codomain of the output")
        val range_description: String = "",
        @Description("Parameter initialization recommendations")
        val initialization_recommendations: List<String> = emptyList()
    )

    data class GradientDerivation(
        @Description("Gradient with respect to input (dL/dx)")
        val gradient_input: String = "",
        @Description("LaTeX for input gradient")
        val gradient_input_latex: String = "",
        @Description("Gradients with respect to each parameter")
        val parameter_gradients: Map<String, String> = emptyMap(),
        @Description("LaTeX for parameter gradients")
        val parameter_gradients_latex: Map<String, String> = emptyMap(),
        @Description("Chain rule application explanation")
        val chain_rule_explanation: String = "",
        @Description("Computational graph description")
        val computational_graph: String = ""
    )

    data class HigherOrderAnalysis(
        @Description("Hessian matrix structure")
        val hessian_structure: String = "",
        @Description("Hessian eigenvalue bounds")
        val hessian_eigenvalue_bounds: String = "",
        @Description("Second derivative expressions")
        val second_derivatives: Map<String, String> = emptyMap(),
        @Description("Curvature analysis")
        val curvature_analysis: String = "",
        @Description("Fisher information matrix if applicable")
        val fisher_information: String = "",
        @Description("Natural gradient considerations")
        val natural_gradient_notes: String = ""
    )

    data class StabilityAnalysis(
        @Description("Lyapunov function candidate")
        val lyapunov_function: String = "",
        @Description("Lyapunov stability conditions")
        val stability_conditions: List<String> = emptyList(),
        @Description("Equilibrium points analysis")
        val equilibrium_analysis: String = "",
        @Description("Basin of attraction description")
        val basin_of_attraction: String = "",
        @Description("Convergence rate bounds")
        val convergence_rate: String = "",
        @Description("Potential instability modes")
        val instability_modes: List<String> = emptyList()
    )

    data class LipschitzAnalysis(
        @Description("Lipschitz constant of the forward function")
        val forward_lipschitz: String = "",
        @Description("Lipschitz constant of the gradient")
        val gradient_lipschitz: String = "",
        @Description("Spectral norm bounds")
        val spectral_norm_bounds: String = "",
        @Description("Gradient flow analysis")
        val gradient_flow: String = "",
        @Description("Smoothness properties")
        val smoothness_properties: List<String> = emptyList()
    )

    data class NumericalStability(
        @Description("Potential overflow conditions")
        val overflow_conditions: List<String> = emptyList(),
        @Description("Potential underflow conditions")
        val underflow_conditions: List<String> = emptyList(),
        @Description("Numerical precision recommendations")
        val precision_recommendations: List<String> = emptyList(),
        @Description("Stabilization techniques")
        val stabilization_techniques: List<String> = emptyList(),
        @Description("Gradient clipping recommendations")
        val gradient_clipping: String = ""
    )

    data class Implementation(
        @Description("Language of implementation")
        val language: String = "",
        @Description("Forward pass code")
        val forward_code: String = "",
        @Description("Backward pass code")
        val backward_code: String = "",
        @Description("Parameter initialization code")
        val initialization_code: String = "",
        @Description("Dependencies/imports")
        val dependencies: List<String> = emptyList()
    )

    data class ComplexityAnalysis(
        @Description("Time complexity of forward pass")
        val forward_time_complexity: String = "",
        @Description("Time complexity of backward pass")
        val backward_time_complexity: String = "",
        @Description("Space complexity")
        val space_complexity: String = "",
        @Description("Memory bandwidth requirements")
        val memory_bandwidth: String = "",
        @Description("Parallelization potential")
        val parallelization_notes: String = ""
    )

    data class OriginalityAnalysis(
        @Description("Assessment of the layer's novelty compared to existing architectures")
        val novelty_assessment: String = "",
        @Description("List of similar or related existing layers/architectures")
        val related_architectures: List<String> = emptyList(),
        @Description("Key innovations or unique aspects of this layer")
        val key_innovations: List<String> = emptyList(),
        @Description("Comparison with baseline/standard approaches")
        val baseline_comparison: String = "",
        @Description("Potential research contributions")
        val research_contributions: List<String> = emptyList(),
        @Description("Limitations compared to existing approaches")
        val limitations: List<String> = emptyList()
    )

    data class UseCaseAnalysis(
        @Description("Primary application domains for this layer")
        val primary_domains: List<String> = emptyList(),
        @Description("Specific tasks where this layer excels")
        val optimal_tasks: List<String> = emptyList(),
        @Description("Tasks where this layer may not be suitable")
        val unsuitable_tasks: List<String> = emptyList(),
        @Description("Recommended network architectures to use this layer in")
        val recommended_architectures: List<String> = emptyList(),
        @Description("Example use case scenarios with descriptions")
        val example_scenarios: List<String> = emptyList(),
        @Description("Integration considerations when adding to existing networks")
        val integration_notes: String = "",
        @Description("Scaling considerations for different problem sizes")
        val scaling_considerations: String = "",
        @Description("Industry applications")
        val industry_applications: List<String> = emptyList()
    )

    data class PracticalGuidance(
        @Description("Hyperparameter tuning recommendations")
        val hyperparameter_tuning: List<String> = emptyList(),
        @Description("Common pitfalls and how to avoid them")
        val common_pitfalls: List<String> = emptyList(),
        @Description("Debugging tips specific to this layer")
        val debugging_tips: List<String> = emptyList(),
        @Description("Performance optimization strategies")
        val optimization_strategies: List<String> = emptyList(),
        @Description("Monitoring and diagnostics recommendations")
        val monitoring_recommendations: List<String> = emptyList(),
        @Description("Best practices for production deployment")
        val production_best_practices: List<String> = emptyList()
    )

    override fun promptSegment(): String {
        return """
 NeuralNetworkLayer - Design and analyze neural network layers with comprehensive explanations
  ** Specify the layer name and forward function description
  ** Define input/output shapes and parameters
  ** Configure analysis options (higher-order, Lyapunov, Lipschitz)
  ** Select implementation languages
  ** The task will generate:
     - Executive summary with key insights and decision criteria
     - Intuitive explanations with real-world analogies
     - Visual conceptual diagrams
     - Formal mathematical definition with LaTeX
     - Forward pass equations and implementation
     - Backward pass (gradient) derivation and implementation
     - Higher-order derivative analysis (Hessian, curvature)
     - Lyapunov stability analysis for training dynamics
     - Lipschitz continuity and gradient flow analysis
     - Numerical stability considerations
     - Reference implementations
     - Complexity analysis
     - Originality analysis comparing to existing architectures
     - Use case analysis with application domains and scenarios
     - Practical guidance for implementation and deployment
  ** Useful for:
     - Learning about neural network layers (beginners to experts)
     - Designing custom neural network layers
     - Understanding existing layer mathematics
     - Analyzing training stability
     - Optimizing layer implementations
     - Research and documentation
     - Evaluating novelty for research papers
     - Identifying practical applications
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        task.ui.pool.submit {
            val transcript = task.transcript()
            try {
            val startTime = System.currentTimeMillis()
                log.info("Starting NeuralNetworkLayerTask for layer: ${executionConfig?.layer_name}")

            executionConfig?.validate()?.let { errorMessage ->
                log.error("NeuralNetworkLayerTask configuration validation failed: $errorMessage")
                task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
                resultFn("VALIDATION ERROR: $errorMessage")
                return@submit
            }

            val layerName = executionConfig?.layer_name ?: ""
            val forwardDesc = executionConfig?.forward_function_description ?: ""
            val inputShape = executionConfig?.input_shape ?: ""
            val outputShape = executionConfig?.output_shape ?: ""
            val parameters = executionConfig?.parameters ?: emptyList()
            val activation = executionConfig?.activation ?: "none"
            val includeHigherOrder = executionConfig?.include_higher_order ?: true
            val includeLyapunov = executionConfig?.include_lyapunov ?: true
            val includeLipschitz = executionConfig?.include_lipschitz ?: true
            val languages = executionConfig?.implementation_languages ?: listOf("tensorflow.js")
            val includeNumerical = executionConfig?.include_numerical_stability ?: true
            val generateTests = executionConfig?.generate_tests ?: true
            val analysisDepth = executionConfig?.analysis_depth ?: "standard"

            val tabs = TabbedDisplay(task)
            val api = defaultSmart

            // Overview tab
            val overviewTask = task.newTask()
            tabs["Overview"] = overviewTask.placeholder
            val overviewContent = buildString {
                appendLine("# Neural Network Layer Analysis: $layerName")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Layer Specification")
                appendLine()
                appendLine("| Property | Value |")
                appendLine("|----------|-------|")
                appendLine("| Layer Name | $layerName |")
                appendLine("| Input Shape | $inputShape |")
                appendLine("| Output Shape | $outputShape |")
                appendLine("| Activation | $activation |")
                appendLine("| Analysis Depth | $analysisDepth |")
                appendLine()
                appendLine("## Forward Function Description")
                appendLine()
                appendLine(forwardDesc)
                appendLine()
                if (parameters.isNotEmpty()) {
                    appendLine("## Parameters")
                    appendLine()
                    parameters.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Generating executive summary...")
            }
            overviewTask.add(overviewContent.renderMarkdown(true))
            task.update()
                transcript?.write("## Layer Specification\n$overviewContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate executive summary first
            val summaryTask = task.newTask()
            tabs["Executive Summary"] = summaryTask.placeholder
            val summary = generateExecutiveSummary(layerName, forwardDesc, inputShape, outputShape, parameters, api)
            val summaryContent = buildString {
                appendLine("# Executive Summary")
                appendLine()
                appendLine("## ${summary.one_liner}")
                appendLine()
                appendLine("### Key Insight")
                appendLine()
                appendLine("> ${summary.key_insight}")
                appendLine()
                appendLine("### Quick Decision Guide")
                appendLine()
                appendLine("| Aspect | Assessment |")
                appendLine("|--------|------------|")
                appendLine("| Computational Cost | ${summary.computational_cost} |")
                appendLine("| Training Difficulty | ${summary.training_difficulty} |")
                appendLine("| Beginner Friendly | ${summary.beginner_friendly} |")
                appendLine()
                appendLine("### ✅ Strengths")
                appendLine()
                summary.strengths.forEach { appendLine("- $it") }
                appendLine()
                appendLine("### ⚠️ Limitations")
                appendLine()
                summary.limitations.forEach { appendLine("- $it") }
                appendLine()
                appendLine("### When to Use")
                appendLine()
                appendLine(summary.when_to_use)
                appendLine()
                appendLine("### When NOT to Use")
                appendLine()
                appendLine(summary.when_not_to_use)
            }
            summaryTask.add(summaryContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Executive summary complete".renderMarkdown(true))
            overviewTask.add("\n- ⏳ Generating intuitive explanation...".renderMarkdown(true))
                transcript?.write("\n## Executive Summary\n$summaryContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate intuitive explanation
            val intuitiveTask = task.newTask()
            tabs["Intuitive Explanation"] = intuitiveTask.placeholder
            val intuitive = generateIntuitiveExplanation(layerName, forwardDesc, summary, api)
            val intuitiveContent = buildString {
                appendLine("# Intuitive Explanation")
                appendLine()
                appendLine("## Real-World Analogy")
                appendLine()
                appendLine(intuitive.analogy)
                appendLine()
                appendLine("## What Problem Does This Solve?")
                appendLine()
                appendLine(intuitive.problem_solved)
                appendLine()
                appendLine("## How Does It Work?")
                appendLine()
                appendLine(intuitive.solution_mechanism)
                appendLine()
                appendLine("## Plain Language Walkthrough")
                appendLine()
                appendLine(intuitive.plain_language_walkthrough)
                appendLine()
                appendLine("## Information Flow")
                appendLine()
                appendLine(intuitive.information_flow_description)
                appendLine()
                appendLine("## Mental Model")
                appendLine()
                appendLine(intuitive.mental_model)
                appendLine()
                appendLine("## Understanding Gradients")
                appendLine()
                appendLine(intuitive.gradient_intuition)
                appendLine()
                if (intuitive.common_misconceptions.isNotEmpty()) {
                    appendLine("## ⚠️ Common Misconceptions")
                    appendLine()
                    intuitive.common_misconceptions.forEach { appendLine("- $it") }
                }
            }
            intuitiveTask.add(intuitiveContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Intuitive explanation complete".renderMarkdown(true))
            overviewTask.add("\n- ⏳ Creating conceptual diagram...".renderMarkdown(true))
                transcript?.write("\n## Intuitive Explanation\n$intuitiveContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate conceptual diagram
            val diagramTask = task.newTask()
            tabs["Conceptual Diagram"] = diagramTask.placeholder
            val diagram = generateConceptualDiagram(layerName, forwardDesc, inputShape, outputShape, parameters, api)
            val diagramContent = buildString {
                appendLine("# Conceptual Diagram")
                appendLine()
                appendLine("## Layer Architecture")
                appendLine()
                appendLine("```")
                appendLine(diagram.ascii_diagram)
                appendLine("```")
                appendLine()
                appendLine("## Data Flow")
                appendLine()
                appendLine(diagram.data_flow_description)
                appendLine()
                if (diagram.mermaid_diagram.isNotBlank()) {
                    appendLine("## Visual Flow Diagram")
                    appendLine()
                    appendLine("```mermaid")
                    appendLine(diagram.mermaid_diagram)
                    appendLine("```")
                    appendLine()
                }
                if (diagram.parameter_roles.isNotEmpty()) {
                    appendLine("## Parameter Roles")
                    appendLine()
                    diagram.parameter_roles.forEach { (param, role) ->
                        appendLine("### $param")
                        appendLine()
                        appendLine(role)
                        appendLine()
                    }
                }
            }
            diagramTask.add(diagramContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Conceptual diagram complete".renderMarkdown(true))
            overviewTask.add("\n- ⏳ Generating formal definition...".renderMarkdown(true))
                transcript?.write("\n## Conceptual Diagram\n$diagramContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate formal definition
            val definitionTask = task.newTask()
            tabs["Formal Definition"] = definitionTask.placeholder
            val definition =
                generateLayerDefinition(layerName, forwardDesc, inputShape, outputShape, parameters, activation, api)
            val definitionContent = buildString {
                appendLine("# Formal Definition")
                appendLine()
                appendLine("## Forward Function")
                appendLine()
                appendLine("$${definition.forward_latex}$")
                appendLine()
                appendLine("**Notation:** ${definition.forward_equation}")
                appendLine()
                appendLine("## Domain Constraints")
                appendLine()
                definition.domain_constraints.forEach { appendLine("- $it") }
                appendLine()
                appendLine("## Range")
                appendLine()
                appendLine(definition.range_description)
                appendLine()
                appendLine("## Parameter Initialization")
                appendLine()
                definition.initialization_recommendations.forEach { appendLine("- $it") }
            }
            definitionTask.add(definitionContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Formal definition complete".renderMarkdown(true))
                transcript?.write("\n## Formal Definition\n$definitionContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate gradient derivation
            overviewTask.add("\n- ⏳ Deriving gradients...".renderMarkdown(true))
            task.update()
            val gradientTask = task.newTask()
            tabs["Gradients"] = gradientTask.placeholder
            val gradients = generateGradientDerivation(layerName, definition, parameters, api)
            val gradientContent = buildString {
                appendLine("# Gradient Derivation (Backward Pass)")
                appendLine()
                appendLine("## Chain Rule Application")
                appendLine()
                appendLine(gradients.chain_rule_explanation)
                appendLine()
                appendLine("## Gradient with Respect to Input")
                appendLine()
                appendLine("$${gradients.gradient_input_latex}$")
                appendLine()
                appendLine("**Expression:** ${gradients.gradient_input}")
                appendLine()
                appendLine("## Parameter Gradients")
                appendLine()
                gradients.parameter_gradients_latex.forEach { (param, latex) ->
                    appendLine("### ∂L/∂$param")
                    appendLine()
                    appendLine("$${latex}$")
                    appendLine()
                    appendLine("**Expression:** ${gradients.parameter_gradients[param]}")
                    appendLine()
                }
                appendLine("## Computational Graph")
                appendLine()
                appendLine("```")
                appendLine(gradients.computational_graph)
                appendLine("```")
            }
            gradientTask.add(gradientContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Gradient derivation complete".renderMarkdown(true))
                transcript?.write("\n## Gradient Derivation\n$gradientContent\n".toByteArray(StandardCharsets.UTF_8))

            // Higher-order analysis
            if (includeHigherOrder && analysisDepth != "basic") {
                overviewTask.add("\n- ⏳ Analyzing higher-order derivatives...".renderMarkdown(true))
                task.update()
                val higherOrderTask = task.newTask()
                tabs["Higher-Order Analysis"] = higherOrderTask.placeholder
                val higherOrder = generateHigherOrderAnalysis(layerName, definition, gradients, analysisDepth, api)
                val higherOrderContent = buildString {
                    appendLine("# Higher-Order Derivative Analysis")
                    appendLine()
                    appendLine("## Hessian Structure")
                    appendLine()
                    appendLine(higherOrder.hessian_structure)
                    appendLine()
                    appendLine("## Eigenvalue Bounds")
                    appendLine()
                    appendLine(higherOrder.hessian_eigenvalue_bounds)
                    appendLine()
                    appendLine("## Second Derivatives")
                    appendLine()
                    higherOrder.second_derivatives.forEach { (name, expr) ->
                        appendLine("### $name")
                        appendLine()
                        appendLine("$${expr}$")
                        appendLine()
                    }
                    appendLine("## Curvature Analysis")
                    appendLine()
                    appendLine(higherOrder.curvature_analysis)
                    appendLine()
                    if (higherOrder.fisher_information.isNotBlank()) {
                        appendLine("## Fisher Information Matrix")
                        appendLine()
                        appendLine(higherOrder.fisher_information)
                        appendLine()
                    }
                    if (higherOrder.natural_gradient_notes.isNotBlank()) {
                        appendLine("## Natural Gradient Considerations")
                        appendLine()
                        appendLine(higherOrder.natural_gradient_notes)
                    }
                }
                higherOrderTask.add(higherOrderContent.renderMarkdown(true))
                task.update()
                overviewTask.add("\n- ✅ Higher-order analysis complete".renderMarkdown(true))
                transcript?.write("\n## Higher-Order Analysis\n$higherOrderContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Lyapunov stability analysis
            if (includeLyapunov && analysisDepth != "basic") {
                overviewTask.add("\n- ⏳ Performing Lyapunov stability analysis...".renderMarkdown(true))
                task.update()
                val stabilityTask = task.newTask()
                tabs["Stability Analysis"] = stabilityTask.placeholder
                val stability = generateStabilityAnalysis(layerName, definition, gradients, api)
                val stabilityContent = buildString {
                    appendLine("# Lyapunov Stability Analysis")
                    appendLine()
                    appendLine("## Lyapunov Function Candidate")
                    appendLine()
                    appendLine("$${stability.lyapunov_function}$")
                    appendLine()
                    appendLine("## Stability Conditions")
                    appendLine()
                    stability.stability_conditions.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("## Equilibrium Analysis")
                    appendLine()
                    appendLine(stability.equilibrium_analysis)
                    appendLine()
                    appendLine("## Basin of Attraction")
                    appendLine()
                    appendLine(stability.basin_of_attraction)
                    appendLine()
                    appendLine("## Convergence Rate")
                    appendLine()
                    appendLine(stability.convergence_rate)
                    appendLine()
                    if (stability.instability_modes.isNotEmpty()) {
                        appendLine("## Potential Instability Modes")
                        appendLine()
                        stability.instability_modes.forEach { appendLine("- ⚠️ $it") }
                    }
                }
                stabilityTask.add(stabilityContent.renderMarkdown(true))
                task.update()
                overviewTask.add("\n- ✅ Stability analysis complete".renderMarkdown(true))
                transcript?.write("\n## Stability Analysis\n$stabilityContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Lipschitz analysis
            if (includeLipschitz) {
                overviewTask.add("\n- ⏳ Analyzing Lipschitz properties...".renderMarkdown(true))
                task.update()
                val lipschitzTask = task.newTask()
                tabs["Lipschitz Analysis"] = lipschitzTask.placeholder
                val lipschitz = generateLipschitzAnalysis(layerName, definition, gradients, api)
                val lipschitzContent = buildString {
                    appendLine("# Lipschitz Continuity Analysis")
                    appendLine()
                    appendLine("## Forward Function Lipschitz Constant")
                    appendLine()
                    appendLine("$${lipschitz.forward_lipschitz}$")
                    appendLine()
                    appendLine("## Gradient Lipschitz Constant (Smoothness)")
                    appendLine()
                    appendLine("$${lipschitz.gradient_lipschitz}$")
                    appendLine()
                    appendLine("## Spectral Norm Bounds")
                    appendLine()
                    appendLine(lipschitz.spectral_norm_bounds)
                    appendLine()
                    appendLine("## Gradient Flow Analysis")
                    appendLine()
                    appendLine(lipschitz.gradient_flow)
                    appendLine()
                    appendLine("## Smoothness Properties")
                    appendLine()
                    lipschitz.smoothness_properties.forEach { appendLine("- $it") }
                }
                lipschitzTask.add(lipschitzContent.renderMarkdown(true))
                task.update()
                overviewTask.add("\n- ✅ Lipschitz analysis complete".renderMarkdown(true))
                transcript?.write("\n## Lipschitz Analysis\n$lipschitzContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Numerical stability
            if (includeNumerical) {
                overviewTask.add("\n- ⏳ Analyzing numerical stability...".renderMarkdown(true))
                task.update()
                val numericalTask = task.newTask()
                tabs["Numerical Stability"] = numericalTask.placeholder
                val numerical = generateNumericalStability(layerName, definition, api)
                val numericalContent = buildString {
                    appendLine("# Numerical Stability Analysis")
                    appendLine()
                    appendLine("## Overflow Conditions")
                    appendLine()
                    numerical.overflow_conditions.forEach { appendLine("- ⚠️ $it") }
                    appendLine()
                    appendLine("## Underflow Conditions")
                    appendLine()
                    numerical.underflow_conditions.forEach { appendLine("- ⚠️ $it") }
                    appendLine()
                    appendLine("## Precision Recommendations")
                    appendLine()
                    numerical.precision_recommendations.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("## Stabilization Techniques")
                    appendLine()
                    numerical.stabilization_techniques.forEach { appendLine("- ✅ $it") }
                    appendLine()
                    appendLine("## Gradient Clipping")
                    appendLine()
                    appendLine(numerical.gradient_clipping)
                }
                numericalTask.add(numericalContent.renderMarkdown(true))
                task.update()
                overviewTask.add("\n- ✅ Numerical stability analysis complete".renderMarkdown(true))
                transcript?.write("\n## Numerical Stability\n$numericalContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Generate implementations
            overviewTask.add("\n- ⏳ Generating implementations...".renderMarkdown(true))
            task.update()
            val implTask = task.newTask()
            tabs["Implementations"] = implTask.placeholder
            val implementations = languages.map { lang ->
                generateImplementation(layerName, definition, gradients, parameters, lang, api)
            }
            val implContent = buildString {
                appendLine("# Reference Implementations")
                appendLine()
                implementations.forEach { impl ->
                    appendLine("## ${impl.language.uppercase()}")
                    appendLine()
                    if (impl.dependencies.isNotEmpty()) {
                        appendLine("### Dependencies")
                        appendLine()
                        appendLine("```${impl.language}")
                        impl.dependencies.forEach { appendLine(it) }
                        appendLine("```")
                        appendLine()
                    }
                    appendLine("### Forward Pass")
                    appendLine()
                    appendLine("```${impl.language}")
                    appendLine(impl.forward_code)
                    appendLine("```")
                    appendLine()
                    appendLine("### Backward Pass")
                    appendLine()
                    appendLine("```${impl.language}")
                    appendLine(impl.backward_code)
                    appendLine("```")
                    appendLine()
                    appendLine("### Initialization")
                    appendLine()
                    appendLine("```${impl.language}")
                    appendLine(impl.initialization_code)
                    appendLine("```")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }
            implTask.add(implContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Implementations generated".renderMarkdown(true))
                transcript?.write("\n## Implementations\n$implContent\n".toByteArray(StandardCharsets.UTF_8))

            // Complexity analysis
            val complexityTask = task.newTask()
            tabs["Complexity"] = complexityTask.placeholder
            val complexity = generateComplexityAnalysis(layerName, definition, inputShape, outputShape, parameters, api)
            val complexityContent = buildString {
                appendLine("# Computational Complexity Analysis")
                appendLine()
                appendLine("## Time Complexity")
                appendLine()
                appendLine("| Pass | Complexity |")
                appendLine("|------|------------|")
                appendLine("| Forward | ${complexity.forward_time_complexity} |")
                appendLine("| Backward | ${complexity.backward_time_complexity} |")
                appendLine()
                appendLine("## Space Complexity")
                appendLine()
                appendLine(complexity.space_complexity)
                appendLine()
                appendLine("## Memory Bandwidth")
                appendLine()
                appendLine(complexity.memory_bandwidth)
                appendLine()
                appendLine("## Parallelization")
                appendLine()
                appendLine(complexity.parallelization_notes)
            }
            complexityTask.add(complexityContent.renderMarkdown(true))
            task.update()
                transcript?.write("\n## Complexity Analysis\n$complexityContent\n".toByteArray(StandardCharsets.UTF_8))

            // Originality analysis
            overviewTask.add("\n- ⏳ Analyzing originality...".renderMarkdown(true))
            task.update()
            val originalityTask = task.newTask()
            tabs["Originality"] = originalityTask.placeholder
            val originality = generateOriginalityAnalysis(layerName, definition, forwardDesc, api)
            val originalityContent = buildString {
                appendLine("# Originality Analysis")
                appendLine()
                appendLine("## Novelty Assessment")
                appendLine()
                appendLine(originality.novelty_assessment)
                appendLine()
                appendLine("## Related Architectures")
                appendLine()
                if (originality.related_architectures.isNotEmpty()) {
                    originality.related_architectures.forEach { appendLine("- $it") }
                } else {
                    appendLine("No closely related architectures identified.")
                }
                appendLine()
                appendLine("## Key Innovations")
                appendLine()
                if (originality.key_innovations.isNotEmpty()) {
                    originality.key_innovations.forEach { appendLine("- ✨ $it") }
                } else {
                    appendLine("Standard implementation without novel innovations.")
                }
                appendLine()
                appendLine("## Baseline Comparison")
                appendLine()
                appendLine(originality.baseline_comparison)
                appendLine()
                appendLine("## Potential Research Contributions")
                appendLine()
                if (originality.research_contributions.isNotEmpty()) {
                    originality.research_contributions.forEach { appendLine("- 📚 $it") }
                } else {
                    appendLine("Primarily an engineering contribution.")
                }
                appendLine()
                appendLine("## Limitations")
                appendLine()
                if (originality.limitations.isNotEmpty()) {
                    originality.limitations.forEach { appendLine("- ⚠️ $it") }
                } else {
                    appendLine("No significant limitations identified.")
                }
            }
            originalityTask.add(originalityContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Originality analysis complete".renderMarkdown(true))
                transcript?.write("\n## Originality Analysis\n$originalityContent\n".toByteArray(StandardCharsets.UTF_8))

            // Use case analysis
            overviewTask.add("\n- ⏳ Analyzing use cases...".renderMarkdown(true))
            task.update()
            val useCaseTask = task.newTask()
            tabs["Use Cases"] = useCaseTask.placeholder
            val useCases = generateUseCaseAnalysis(layerName, definition, forwardDesc, inputShape, outputShape, api)
            val useCaseContent = buildString {
                appendLine("# Use Case Analysis")
                appendLine()
                appendLine("## Primary Application Domains")
                appendLine()
                if (useCases.primary_domains.isNotEmpty()) {
                    useCases.primary_domains.forEach { appendLine("- 🎯 $it") }
                } else {
                    appendLine("General-purpose layer applicable across domains.")
                }
                appendLine()
                appendLine("## Optimal Tasks")
                appendLine()
                appendLine("Tasks where this layer excels:")
                appendLine()
                if (useCases.optimal_tasks.isNotEmpty()) {
                    useCases.optimal_tasks.forEach { appendLine("- ✅ $it") }
                } else {
                    appendLine("Suitable for general neural network tasks.")
                }
                appendLine()
                appendLine("## Unsuitable Tasks")
                appendLine()
                appendLine("Tasks where this layer may not be the best choice:")
                appendLine()
                if (useCases.unsuitable_tasks.isNotEmpty()) {
                    useCases.unsuitable_tasks.forEach { appendLine("- ❌ $it") }
                } else {
                    appendLine("No specific contraindications identified.")
                }
                appendLine()
                appendLine("## Recommended Architectures")
                appendLine()
                if (useCases.recommended_architectures.isNotEmpty()) {
                    useCases.recommended_architectures.forEach { appendLine("- 🏗️ $it") }
                } else {
                    appendLine("Can be integrated into most standard architectures.")
                }
                appendLine()
                appendLine("## Example Scenarios")
                appendLine()
                if (useCases.example_scenarios.isNotEmpty()) {
                    useCases.example_scenarios.forEachIndexed { index, scenario ->
                        appendLine("### Scenario ${index + 1}")
                        appendLine()
                        appendLine(scenario)
                        appendLine()
                    }
                } else {
                    appendLine("See primary domains for general application guidance.")
                }
                appendLine()
                appendLine("## Integration Notes")
                appendLine()
                appendLine(useCases.integration_notes.ifBlank { "Standard integration procedures apply." })
                appendLine()
                appendLine("## Scaling Considerations")
                appendLine()
                appendLine(useCases.scaling_considerations.ifBlank { "Scales linearly with input dimensions." })
                appendLine()
                appendLine("## Industry Applications")
                appendLine()
                if (useCases.industry_applications.isNotEmpty()) {
                    useCases.industry_applications.forEach { appendLine("- 🏭 $it") }
                } else {
                    appendLine("Applicable across various industries using deep learning.")
                }
            }
            useCaseTask.add(useCaseContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Use case analysis complete".renderMarkdown(true))
                transcript?.write("\n## Use Case Analysis\n$useCaseContent\n".toByteArray(StandardCharsets.UTF_8))

            // Practical guidance
            overviewTask.add("\n- ⏳ Generating practical guidance...".renderMarkdown(true))
            task.update()
            val guidanceTask = task.newTask()
            tabs["Practical Guidance"] = guidanceTask.placeholder
            val numerical = if (includeNumerical) {
                generateNumericalStability(layerName, definition, api)
            } else {
                NumericalStability(
                    overflow_conditions = emptyList(),
                    underflow_conditions = emptyList(),
                    precision_recommendations = emptyList(),
                    stabilization_techniques = emptyList(),
                    gradient_clipping = "N/A"
                )
            }
            val guidance = generatePracticalGuidance(layerName, definition, summary, numerical, api)
            val guidanceContent = buildString {
                appendLine("# Practical Guidance")
                appendLine()
                appendLine("## Hyperparameter Tuning")
                appendLine()
                if (guidance.hyperparameter_tuning.isNotEmpty()) {
                    guidance.hyperparameter_tuning.forEach { appendLine("- $it") }
                } else {
                    appendLine("Standard hyperparameter tuning approaches apply.")
                }
                appendLine()
                appendLine("## ⚠️ Common Pitfalls")
                appendLine()
                if (guidance.common_pitfalls.isNotEmpty()) {
                    guidance.common_pitfalls.forEach { appendLine("- $it") }
                } else {
                    appendLine("No specific pitfalls identified beyond standard neural network training issues.")
                }
                appendLine()
                appendLine("## 🔧 Debugging Tips")
                appendLine()
                if (guidance.debugging_tips.isNotEmpty()) {
                    guidance.debugging_tips.forEach { appendLine("- $it") }
                } else {
                    appendLine("Use standard debugging techniques: gradient checking, visualization, unit tests.")
                }
                appendLine()
                appendLine("## ⚡ Performance Optimization")
                appendLine()
                if (guidance.optimization_strategies.isNotEmpty()) {
                    guidance.optimization_strategies.forEach { appendLine("- $it") }
                } else {
                    appendLine("Standard optimization techniques apply: batching, GPU acceleration, mixed precision.")
                }
                appendLine()
                appendLine("## 📊 Monitoring & Diagnostics")
                appendLine()
                if (guidance.monitoring_recommendations.isNotEmpty()) {
                    guidance.monitoring_recommendations.forEach { appendLine("- $it") }
                } else {
                    appendLine("Monitor standard metrics: loss, gradients, activations, parameter norms.")
                }
                appendLine()
                appendLine("## 🚀 Production Best Practices")
                appendLine()
                if (guidance.production_best_practices.isNotEmpty()) {
                    guidance.production_best_practices.forEach { appendLine("- $it") }
                } else {
                    appendLine("Follow standard ML deployment practices: versioning, monitoring, A/B testing.")
                }
            }
            guidanceTask.add(guidanceContent.renderMarkdown(true))
            task.update()
            overviewTask.add("\n- ✅ Practical guidance complete".renderMarkdown(true))
                transcript?.write("\n## Practical Guidance\n$guidanceContent\n".toByteArray(StandardCharsets.UTF_8))

            // Final summary
            val totalTime = System.currentTimeMillis() - startTime
            val finalOverview = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Analysis Complete")
                appendLine()
                appendLine("| Metric | Value |")
                appendLine("|--------|-------|")
                appendLine("| Total Time | ${totalTime / 1000}s |")
                appendLine("| Sections Generated | ${tabs.size} |")
                appendLine("| Implementation Languages | ${languages.joinToString(", ")} |")
                appendLine()
                appendLine("## Configuration Summary")
                appendLine()
                appendLine("| Setting | Value |")
                appendLine("|---------|-------|")
                appendLine("| Layer Name | $layerName |")
                appendLine("| Input Shape | $inputShape |")
                appendLine("| Output Shape | $outputShape |")
                appendLine("| Activation | $activation |")
                appendLine("| Analysis Depth | $analysisDepth |")
                appendLine("| Higher-Order Analysis | $includeHigherOrder |")
                appendLine("| Lyapunov Analysis | $includeLyapunov |")
                appendLine("| Lipschitz Analysis | $includeLipschitz |")
                appendLine("| Numerical Stability | $includeNumerical |")
                appendLine("| Generate Tests | $generateTests |")
            }
            overviewTask.add(finalOverview.renderMarkdown(true))
            task.update()
                transcript?.write("\n## Final Summary\n$finalOverview\n".toByteArray(StandardCharsets.UTF_8))

            val resultMessage = buildString {
                appendLine("# $layerName Layer Analysis Complete")
                appendLine()
                appendLine("## Quick Summary")
                appendLine()
                appendLine("**${summary.one_liner}**")
                appendLine()
                appendLine("> ${summary.key_insight}")
                appendLine()
                appendLine("## Key Details")
                appendLine()
                appendLine("- **Forward Function:** $${definition.forward_latex}$")
                appendLine("- **Computational Cost:** ${summary.computational_cost}")
                appendLine("- **Training Difficulty:** ${summary.training_difficulty}")
                appendLine("- **Beginner Friendly:** ${summary.beginner_friendly}")
                appendLine()
                appendLine("## Analogy")
                appendLine()
                appendLine(intuitive.analogy.take(200) + "...")
                appendLine()
                appendLine("See tabs for complete analysis including:")
                appendLine("- Executive summary and decision guide")
                appendLine("- Intuitive explanations with analogies")
                appendLine("- Conceptual diagrams")
                appendLine("- Formal mathematics and gradients")
                appendLine("- Stability and complexity analysis")
                appendLine("- Implementations in ${languages.joinToString(", ")}")
                appendLine("- Originality assessment")
                appendLine("- Use cases and practical guidance")
            }

            task.complete("Neural network layer analysis complete in ${totalTime / 1000}s")
            resultFn(resultMessage)

            } catch (e: Exception) {
                task.error(e)
                log.error("Error during NeuralNetworkLayerTask execution for layer: ${executionConfig?.layer_name}", e)
                transcript?.write(
                    "\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                resultFn("ERROR: ${e.message}")
            } finally {
                transcript?.close()
            }
        }
    }

    private fun generateExecutiveSummary(
        layerName: String,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        api: ChatInterface
    ): ExecutiveSummary {
        return try {
            ParsedAgent(
                resultClass = ExecutiveSummary::class.java,
                prompt = """
You are an expert in neural networks. Create an executive summary for a layer.

## Layer: $layerName

## Description
$forwardDesc

## Specifications
- Input: $inputShape
- Output: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}

## Instructions
Provide a concise executive summary:
1. One-sentence description of what the layer does
2. Key mathematical insight in plain language
3. Primary strengths (3-5 bullet points)
4. Primary limitations (3-5 bullet points)
5. When to use this layer (decision criteria)
6. When NOT to use this layer
7. Computational cost assessment (low/medium/high)
8. Training difficulty (easy/moderate/hard)
9. Is it beginner-friendly? (yes/no/with_caution)

Be concise, practical, and actionable. Focus on helping users make informed decisions.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ExecutiveSummaryGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate executive summary")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate executive summary", e)
            ExecutiveSummary(
                one_liner = "$layerName: A neural network layer",
                key_insight = "Transforms input data according to learned parameters",
                strengths = listOf("Learnable parameters", "Differentiable"),
                limitations = listOf("Requires training data"),
                when_to_use = "When you need to learn transformations from data",
                when_not_to_use = "When hand-crafted features are sufficient",
                computational_cost = "medium",
                training_difficulty = "moderate",
                beginner_friendly = "with_caution"
            )
        }
    }

    private fun generateIntuitiveExplanation(
        layerName: String,
        forwardDesc: String,
        summary: ExecutiveSummary,
        api: ChatInterface
    ): IntuitiveExplanation {
        return try {
            ParsedAgent(
                resultClass = IntuitiveExplanation::class.java,
                prompt = """
You are an expert educator in neural networks. Explain a layer intuitively.

## Layer: $layerName

## Description
$forwardDesc

## Key Insight
${summary.key_insight}

## Instructions
Provide intuitive explanations:
1. Real-world analogy (compare to everyday objects/processes)
2. Plain language walkthrough (step-by-step, no math jargon)
3. Visual description of information flow
4. What problem does this layer solve?
5. How does it solve the problem? (mechanism)
6. Common misconceptions about this layer
7. Intuitive explanation of why gradients work this way
8. Mental model for understanding the layer

Use simple language. Avoid mathematical notation. Focus on building intuition.
Think of explaining to a smart high school student or non-technical stakeholder.
                """.trimIndent(),
                model = api,
                temperature = 0.4,
                name = "IntuitiveExplanationGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate intuitive explanation")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate intuitive explanation", e)
            IntuitiveExplanation(
                analogy = "Think of this layer like a filter that learns what patterns to look for in the data.",
                plain_language_walkthrough = "The layer takes input, applies learned transformations, and produces output.",
                information_flow_description = "Data flows through the layer, being transformed along the way.",
                problem_solved = "Learning useful representations from data",
                solution_mechanism = "Adjusting parameters based on feedback from training"
            )
        }
    }

    private fun generateConceptualDiagram(
        layerName: String,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        api: ChatInterface
    ): ConceptualDiagram {
        return try {
            ParsedAgent(
                resultClass = ConceptualDiagram::class.java,
                prompt = """
You are an expert in visual communication of neural networks. Create conceptual diagrams.

## Layer: $layerName

## Description
$forwardDesc

## Specifications
- Input: $inputShape
- Output: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}

## Instructions
Create visual representations:
1. ASCII art diagram showing layer structure (boxes, arrows)
2. Description of data flow through the layer
3. Mermaid diagram syntax for flowchart visualization
4. Description of each parameter's role

Make diagrams clear and informative. Use standard conventions.
For ASCII art, use boxes (┌─┐│└┘), arrows (→), and labels.
For Mermaid, use flowchart syntax with clear node labels.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ConceptualDiagramGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate conceptual diagram")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate conceptual diagram", e)
            ConceptualDiagram(
                ascii_diagram = """
                    Input → [$layerName] → Output
                """.trimIndent(),
                data_flow_description = "Data flows from input through the layer to output",
                mermaid_diagram = """
                    graph LR
                        A[Input] --> B[$layerName]
                        B --> C[Output]
                """.trimIndent()
            )
        }
    }

    private fun generatePracticalGuidance(
        layerName: String,
        definition: LayerDefinition,
        summary: ExecutiveSummary,
        numerical: NumericalStability,
        api: ChatInterface
    ): PracticalGuidance {
        return try {
            ParsedAgent(
                resultClass = PracticalGuidance::class.java,
                prompt = """
You are an expert ML engineer. Provide practical guidance for implementing a layer.

## Layer: $layerName

## Summary
${summary.one_liner}

Training Difficulty: ${summary.training_difficulty}

## Numerical Considerations
Overflow: ${numerical.overflow_conditions.joinToString("; ")}
Underflow: ${numerical.underflow_conditions.joinToString("; ")}

## Instructions
Provide practical implementation guidance:
1. Hyperparameter tuning recommendations (learning rate, initialization, etc.)
2. Common pitfalls and how to avoid them
3. Debugging tips specific to this layer
4. Performance optimization strategies
5. Monitoring and diagnostics recommendations
6. Best practices for production deployment

Be specific and actionable. Draw from real-world experience.
Focus on issues practitioners actually encounter.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "PracticalGuidanceGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate practical guidance")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate practical guidance", e)
            PracticalGuidance(
                hyperparameter_tuning = listOf("Start with standard learning rates (1e-3 to 1e-4)"),
                common_pitfalls = listOf("Forgetting to normalize inputs", "Not monitoring gradients"),
                debugging_tips = listOf("Check gradient magnitudes", "Visualize activations"),
                optimization_strategies = listOf("Use GPU acceleration", "Batch operations"),
                monitoring_recommendations = listOf("Track loss curves", "Monitor gradient norms"),
                production_best_practices = listOf("Version models", "Monitor inference latency")
            )
        }
    }

    private fun generateLayerDefinition(
        layerName: String,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        activation: String,
        api: ChatInterface
    ): LayerDefinition {
        return try {
            ParsedAgent(
                resultClass = LayerDefinition::class.java,
                prompt = """
You are an expert in neural network mathematics. Define a neural network layer formally.

## Layer: $layerName

## Description
$forwardDesc

## Specifications
- Input Shape: $inputShape
- Output Shape: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}
- Activation: $activation

## Instructions
Provide a formal mathematical definition including:
1. The forward function in precise mathematical notation
2. LaTeX representation suitable for rendering
3. Domain constraints (valid input ranges, shapes)
4. Range/codomain description
5. Parameter initialization recommendations (Xavier, He, etc.)

Be rigorous and use standard mathematical notation.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "LayerDefinitionGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate formal definition")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate layer definition", e)
            LayerDefinition(
                forward_equation = forwardDesc,
                forward_latex = "y = f(x)",
                domain_constraints = listOf("x ∈ ℝ^n"),
                range_description = "y ∈ ℝ^m",
                initialization_recommendations = listOf("Xavier initialization recommended")
            )
        }
    }

    private fun generateGradientDerivation(
        layerName: String,
        definition: LayerDefinition,
        parameters: List<String>,
        api: ChatInterface
    ): GradientDerivation {
        return try {
            ParsedAgent(
                resultClass = GradientDerivation::class.java,
                prompt = """
You are an expert in neural network backpropagation. Derive the gradients for a layer.

## Layer: $layerName

## Forward Function
${definition.forward_equation}

LaTeX: ${definition.forward_latex}

## Parameters
${parameters.joinToString("\n") { "- $it" }.ifEmpty { "None" }}

## Instructions
Derive the backward pass gradients:
1. Gradient with respect to input (∂L/∂x) for backpropagation
2. Gradient with respect to each parameter (∂L/∂θ) for optimization
3. Show the chain rule application step by step
4. Describe the computational graph

Assume upstream gradient ∂L/∂y is given. Use standard notation.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "GradientDerivationGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Derive gradients")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate gradient derivation", e)
            GradientDerivation(
                gradient_input = "∂L/∂x = ∂L/∂y · ∂y/∂x",
                gradient_input_latex = "\\frac{\\partial L}{\\partial x} = \\frac{\\partial L}{\\partial y} \\cdot \\frac{\\partial y}{\\partial x}",
                chain_rule_explanation = "Apply chain rule from output to input"
            )
        }
    }

    private fun generateHigherOrderAnalysis(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        analysisDepth: String,
        api: ChatInterface
    ): HigherOrderAnalysis {
        return try {
            ParsedAgent(
                resultClass = HigherOrderAnalysis::class.java,
                prompt = """
You are an expert in optimization theory. Analyze higher-order derivatives of a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## First Derivatives
Input gradient: ${gradients.gradient_input_latex}

## Analysis Depth: $analysisDepth

## Instructions
Analyze higher-order derivatives:
1. Hessian matrix structure (sparsity, block structure)
2. Eigenvalue bounds of the Hessian
3. Key second derivative expressions
4. Curvature analysis (convexity, saddle points)
5. Fisher information matrix if applicable
6. Natural gradient considerations

Focus on implications for optimization and training dynamics.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "HigherOrderAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze higher-order derivatives")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate higher-order analysis", e)
            HigherOrderAnalysis(
                hessian_structure = "Analysis not available",
                curvature_analysis = "See gradient derivation for first-order information"
            )
        }
    }

    private fun generateStabilityAnalysis(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        api: ChatInterface
    ): StabilityAnalysis {
        return try {
            ParsedAgent(
                resultClass = StabilityAnalysis::class.java,
                prompt = """
You are an expert in dynamical systems and neural network training dynamics. Analyze stability.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Gradients
${gradients.gradient_input_latex}

## Instructions
Perform Lyapunov stability analysis for training dynamics:
1. Propose a Lyapunov function candidate (typically loss-based)
2. Derive stability conditions
3. Analyze equilibrium points (optimal parameters)
4. Describe basin of attraction
5. Bound convergence rates
6. Identify potential instability modes (exploding/vanishing gradients)

Consider gradient descent dynamics: θ_{t+1} = θ_t - η∇L(θ_t)
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "StabilityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze stability")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate stability analysis", e)
            StabilityAnalysis(
                lyapunov_function = "V(θ) = L(θ) - L(θ*)",
                stability_conditions = listOf("Learning rate η < 2/L where L is Lipschitz constant"),
                convergence_rate = "Linear convergence for strongly convex loss"
            )
        }
    }

    private fun generateLipschitzAnalysis(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        api: ChatInterface
    ): LipschitzAnalysis {
        return try {
            ParsedAgent(
                resultClass = LipschitzAnalysis::class.java,
                prompt = """
You are an expert in functional analysis and neural networks. Analyze Lipschitz properties.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Gradients
${gradients.gradient_input_latex}

## Instructions
Analyze Lipschitz continuity:
1. Compute/bound Lipschitz constant of forward function
2. Compute/bound Lipschitz constant of gradient (smoothness)
3. Analyze spectral norm bounds
4. Describe gradient flow properties
5. List smoothness properties

These are crucial for:
- Generalization bounds
- Adversarial robustness
- Training stability
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "LipschitzAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze Lipschitz properties")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate Lipschitz analysis", e)
            LipschitzAnalysis(
                forward_lipschitz = "L_f ≤ ||W||_2 (spectral norm of weights)",
                gradient_lipschitz = "L_g bounded by second derivatives",
                gradient_flow = "Gradient magnitude bounded by Lipschitz constant"
            )
        }
    }

    private fun generateNumericalStability(
        layerName: String,
        definition: LayerDefinition,
        api: ChatInterface
    ): NumericalStability {
        return try {
            ParsedAgent(
                resultClass = NumericalStability::class.java,
                prompt = """
You are an expert in numerical computing. Analyze numerical stability of a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Instructions
Analyze numerical stability:
1. Identify overflow conditions (large values)
2. Identify underflow conditions (small values, division)
3. Recommend numerical precision (float16, float32, float64)
4. Suggest stabilization techniques (log-space, normalization)
5. Recommend gradient clipping strategies

Consider both forward and backward passes.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "NumericalStabilityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze numerical stability")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate numerical stability analysis", e)
            NumericalStability(
                overflow_conditions = listOf("Large input values may cause overflow"),
                underflow_conditions = listOf("Small gradients may underflow"),
                precision_recommendations = listOf("float32 recommended for training"),
                stabilization_techniques = listOf("Batch normalization", "Gradient clipping")
            )
        }
    }

    private fun generateImplementation(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        parameters: List<String>,
        language: String,
        api: ChatInterface
    ): Implementation {
        return try {
            ParsedAgent(
                resultClass = Implementation::class.java,
                prompt = """
You are an expert programmer. Implement a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Gradients
Input: ${gradients.gradient_input_latex}
Parameters: ${gradients.parameter_gradients_latex}

## Parameters
${parameters.joinToString("\n") { "- $it" }.ifEmpty { "None" }}

## Target Language: $language

## Instructions
Provide a complete implementation:
1. Forward pass function
2. Backward pass function (computing gradients)
3. Parameter initialization function
4. Required imports/dependencies

Use idiomatic code for the target language. Include comments explaining the mathematics.
For Python, use NumPy. For Tensorflow.js, use tfjs. For pseudocode, be clear and mathematical.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ImplementationGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate implementation")).obj.copy(language = language)
        } catch (e: Exception) {
            log.warn("Failed to generate implementation for $language", e)
            Implementation(
                language = language,
                forward_code = "# Forward pass implementation\ndef forward(x):\n    return x  # TODO",
                backward_code = "# Backward pass implementation\ndef backward(grad_output):\n    return grad_output  # TODO",
                initialization_code = "# Parameter initialization\ndef init_params():\n    pass  # TODO"
            )
        }
    }

    private fun generateComplexityAnalysis(
        layerName: String,
        definition: LayerDefinition,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        api: ChatInterface
    ): ComplexityAnalysis {
        return try {
            ParsedAgent(
                resultClass = ComplexityAnalysis::class.java,
                prompt = """
You are an expert in computational complexity. Analyze a neural network layer's complexity.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Shapes
- Input: $inputShape
- Output: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}

## Instructions
Analyze computational complexity:
1. Time complexity of forward pass (Big-O notation)
2. Time complexity of backward pass
3. Space complexity (activations, gradients)
4. Memory bandwidth requirements
5. Parallelization potential (GPU, distributed)

Express in terms of batch size (B), dimensions, etc.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ComplexityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze complexity")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate complexity analysis", e)
            ComplexityAnalysis(
                forward_time_complexity = "O(n)",
                backward_time_complexity = "O(n)",
                space_complexity = "O(n) for activations",
                memory_bandwidth = "Memory bound for large tensors",
                parallelization_notes = "Highly parallelizable on GPU"
            )
        }
    }

    private fun generateOriginalityAnalysis(
        layerName: String,
        definition: LayerDefinition,
        forwardDesc: String,
        api: ChatInterface
    ): OriginalityAnalysis {
        return try {
            ParsedAgent(
                resultClass = OriginalityAnalysis::class.java,
                prompt = """
You are an expert in deep learning research and neural network architectures. Analyze the originality of a layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Description
$forwardDesc

## Instructions
Analyze the originality and novelty of this layer:
1. Assess overall novelty compared to existing published architectures
2. List similar or related existing layers (e.g., from papers, frameworks)
3. Identify key innovations or unique aspects
4. Compare with baseline/standard approaches (what does this do differently?)
5. Identify potential research contributions (theoretical, empirical, practical)
6. Note limitations compared to existing approaches

Be specific about related work (cite paper names/years if applicable).
Consider both theoretical novelty and practical innovation.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "OriginalityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze originality")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate originality analysis", e)
            OriginalityAnalysis(
                novelty_assessment = "Unable to assess novelty automatically. Manual review recommended.",
                related_architectures = listOf("Standard neural network layers"),
                baseline_comparison = "Compare with standard implementations in PyTorch/TensorFlow"
            )
        }
    }

    private fun generateUseCaseAnalysis(
        layerName: String,
        definition: LayerDefinition,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        api: ChatInterface
    ): UseCaseAnalysis {
        return try {
            ParsedAgent(
                resultClass = UseCaseAnalysis::class.java,
                prompt = """
You are an expert in applied deep learning. Analyze use cases for a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Description
$forwardDesc

## Shapes
- Input: $inputShape
- Output: $outputShape

## Instructions
Analyze practical use cases:
1. Identify primary application domains (NLP, CV, audio, tabular, etc.)
2. List specific tasks where this layer excels
3. List tasks where this layer may not be suitable
4. Recommend network architectures to use this layer in
5. Provide concrete example scenarios with descriptions
6. Note integration considerations when adding to existing networks
7. Discuss scaling considerations for different problem sizes
8. List industry applications (healthcare, finance, autonomous vehicles, etc.)

Be practical and specific. Consider both research and production use cases.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "UseCaseAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze use cases")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate use case analysis", e)
            UseCaseAnalysis(
                primary_domains = listOf("General deep learning applications"),
                optimal_tasks = listOf("Tasks matching the layer's input/output structure"),
                integration_notes = "Standard integration with common deep learning frameworks",
                scaling_considerations = "Performance depends on input dimensions and batch size"
            )
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/ProbabilisticReasoningTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore

class ProbabilisticReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ProbabilisticReasoningTaskExecutionConfigData?
) : AbstractTask<ProbabilisticReasoningTask.ProbabilisticReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxDescriptionLength = 10000
    protected val codeFiles = mutableMapOf<Path, String>()


    class ProbabilisticReasoningTaskExecutionConfigData(
        @Description("Map of hypotheses to their prior probabilities (must sum to 1.0)")
        var hypotheses: Map<String, Double>? = null,
        @Description("List of observed evidence to update beliefs")
        var evidence: List<String>? = null,
        @Description("Whether to calculate expected values and risks")
        var calculate_expected_value: Boolean = true,
        @Description("Whether to identify key uncertainties that need resolution")
        var identify_key_uncertainties: Boolean = true,
        @Description("Whether to suggest experiments to reduce uncertainty")
        var suggest_experiments: Boolean = true,
        @Description("Risk tolerance level (low/medium/high)")
        var risk_tolerance: String = "medium",
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("Decision context or problem statement")
        var decision_context: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ProbabilisticReasoning.name,
        task_description = task_description
            ?: "Bayesian analysis of ${hypotheses?.size ?: 0} hypotheses with ${evidence?.size ?: 0} pieces of evidence",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            // Validate hypotheses
            if (hypotheses.isNullOrEmpty()) {
                return "Hypotheses map cannot be null or empty"
            }

            // Validate that all probabilities are between 0 and 1
          hypotheses!!.forEach { (hypothesis, probability) ->
                if (probability < 0.0 || probability > 1.0) {
                    return "Probability for hypothesis '$hypothesis' must be between 0.0 and 1.0, got: $probability"
                }
            }

            // Validate that probabilities sum to approximately 1.0
          val probabilitySum = hypotheses!!.values.sum()
            if (probabilitySum < 0.99 || probabilitySum > 1.01) {
                return "Prior probabilities must sum to 1.0 (current sum: $probabilitySum)"
            }

            // Validate risk tolerance
            if (risk_tolerance !in listOf("low", "medium", "high")) {
                return "Risk tolerance must be one of: low, medium, high. Got: $risk_tolerance"
            }

            // Validate evidence list if present
            evidence?.forEach { evidenceItem ->
                if (evidenceItem.isBlank()) {
                    return "Evidence items cannot be blank"
                }
            }

            // Validate decision context if present
            if (decision_context?.isBlank() == true) {
                return "Decision context cannot be blank if provided"
            }

            // Call parent validation
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
ProbabilisticReasoning - Reason under uncertainty using Bayesian analysis
  ** Specify hypotheses with prior probabilities (must sum to 1.0)
  ** Provide observed evidence to update beliefs
  ** Calculate expected values and quantify risks
  ** Identify key uncertainties that need resolution
  ** Suggest experiments to reduce uncertainty
  ** Useful for:
     - Risk assessment and management
     - Diagnostic reasoning (bug hunting)
     - A/B test analysis and decision making
     - Resource allocation under uncertainty
     - Technology adoption decisions
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
      log.info("Starting ProbabilisticReasoningTask. Hypotheses: ${executionConfig?.hypotheses?.size ?: 0}")

      // Validate configuration
        executionConfig?.validate()?.let { errorMsg ->
          log.error("ProbabilisticReasoningTask config validation failed: $errorMsg")
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }


        val hypotheses = executionConfig?.hypotheses
        if (hypotheses.isNullOrEmpty()) {
            val errorMsg = "CONFIGURATION ERROR: No hypotheses specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }


        val evidence = executionConfig.evidence ?: emptyList()
        val decisionContext = executionConfig.decision_context ?: "General probabilistic reasoning"


        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)
        val semaphore = Semaphore(0)
      val resultBuilder = StringBuilder()
      val startTime = System.currentTimeMillis()
        val transcript = task.transcript()
        transcript?.let { stream ->
            stream.write("# Probabilistic Reasoning Analysis Transcript\n\n".toByteArray())
            stream.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )
            stream.write("**Decision Context:** $decisionContext\n\n".toByteArray())
            stream.write("**Hypotheses:** ${hypotheses.size}\n\n".toByteArray())
            stream.write("**Evidence Items:** ${evidence.size}\n\n".toByteArray())
            stream.write("**Risk Tolerance:** ${executionConfig.risk_tolerance}\n\n".toByteArray())
            stream.write("---\n\n".toByteArray())
            writeInputFilesSection(stream, agent)
        }

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Probabilistic Reasoning Analysis")
            appendLine()
            appendLine("**Decision Context:** $decisionContext")
            appendLine()
            appendLine("**Hypotheses:** ${hypotheses.size}")
            appendLine()
            appendLine("**Evidence Items:** ${evidence.size}")
            appendLine()
            appendLine("**Risk Tolerance:** ${executionConfig.risk_tolerance}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing Bayesian analysis...*")
        }
      overviewTask.add(overviewContent.renderMarkdown())

        val inputFileContent = getInputFileCode(agent)
        if (inputFileContent.isNotBlank()) {
            log.debug("Found input files: ${inputFileContent.length} characters")
            val filesTask = tabs.newTask("Input Files")
            filesTask.add(
              ("# Input Files\n\n$inputFileContent").renderMarkdown()
            )
            filesTask.complete()
        }

        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            log.debug("Found prior context: ${priorContext.length} characters")
            val contextTask = tabs.newTask("Context")
            contextTask.add(
              """
        # Prior Context
        The following context was inherited from previous tasks:
        ```
        ${priorContext.truncateForDisplay()}
        ```
        """.trimIndent().renderMarkdown()
            )
            contextTask.complete()
        }
      task.ui.pool.submit {

        try {
            // Prior Probabilities tab
            val priorTask = tabs.newTask("Prior Probabilities")

            val priorContent = buildString {
                appendLine("# Prior Probabilities")
                appendLine()
                appendLine("Initial belief distribution before considering evidence:")
                appendLine()
                appendLine("| Hypothesis | Prior Probability |")
                appendLine("|------------|-------------------|")
                hypotheses.entries.sortedByDescending { it.value }.forEach { (hypothesis, prob) ->
                    appendLine("| ${hypothesis.take(50)} | ${String.format("%.1f%%", prob * 100)} |")
                }
                appendLine()
                appendLine("**Total:** ${String.format("%.3f", hypotheses.values.sum())}")
            }
          priorTask.add(priorContent.renderMarkdown())
            priorTask.complete()

            overviewTask.add(
              (buildString {
                    appendLine()
                    appendLine("✅ Prior probabilities loaded")
                    appendLine()
                    appendLine("*Analyzing evidence...*")
              }).renderMarkdown()
            )

            // Create Bayesian reasoning agent
            log.info("Creating Bayesian reasoning agent")
            val bayesianAgent = ChatAgent(
                prompt = """
You are an expert in Bayesian reasoning and probabilistic analysis. Your role is to:
1. Apply Bayes' theorem to update beliefs based on evidence
2. Calculate posterior probabilities rigorously
3. Quantify uncertainty and confidence levels
4. Identify which evidence has the strongest impact
5. Reason about conditional probabilities and independence
6. Explain probabilistic reasoning in clear terms

Use mathematical rigor while making the analysis accessible.
Consider both the strength of evidence and its reliability.
        """.trimIndent(),
                model = api,
                temperature = 0.3 // Lower temperature for more consistent probabilistic reasoning
            )

            // Bayesian Update tab
            val updateTask = tabs.newTask("Bayesian Update")

            updateTask.add(
              (buildString {
                    appendLine("# Bayesian Update")
                    appendLine()
                    appendLine("**Status:** Calculating posterior probabilities...")
                    appendLine()
              }).renderMarkdown()
            )

            val updatePrompt = buildBayesianUpdatePrompt(
                hypotheses,
                evidence,
                decisionContext,
                priorContext
            )

          log.info("Requesting Bayesian update from LLM")
            var stepStartTime = System.currentTimeMillis()
            val updateResult = bayesianAgent.answer(listOf(updatePrompt))
            var stepTime = System.currentTimeMillis() - stepStartTime
          log.info("Bayesian update completed in ${stepTime}ms")
            // Write to transcript
            transcript?.write("""
                ## Bayesian Update
                <details>
                <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                $updateResult
                </details>
            """.trimIndent().toByteArray())



            updateTask.add(
              (buildString {
                    appendLine("## Analysis Results")
                    appendLine()
                    appendLine(updateResult)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
              }).renderMarkdown()
            )
            updateTask.complete()

            overviewTask.add(
              (buildString {
                    appendLine()
                    appendLine("✅ Bayesian update complete (${stepTime / 1000.0}s)")
                    appendLine()
                    appendLine("*Generating additional analyses...*")
              }).renderMarkdown()
            )

            resultBuilder.append("# Probabilistic Reasoning Analysis\n\n")
            resultBuilder.append("**Context:** $decisionContext\n\n")
            resultBuilder.append("## Bayesian Update\n\n")
            resultBuilder.append(updateResult.take(maxDescriptionLength))
            if (updateResult.length > maxDescriptionLength) resultBuilder.append("\n... (see full analysis in UI)")
            resultBuilder.append("\n\n")

            // Expected Value Analysis (if requested)
            if (executionConfig.calculate_expected_value) {
              log.info("Calculating expected values")
                val evTask = tabs.newTask("Expected Value")

                evTask.add(
                  (buildString {
                        appendLine("# Expected Value Analysis")
                        appendLine()
                        appendLine("**Status:** Calculating expected values and risks...")
                        appendLine()
                  }).renderMarkdown()
                )

                val evPrompt = buildExpectedValuePrompt(
                    hypotheses,
                    updateResult,
                    executionConfig.risk_tolerance
                )
                stepStartTime = System.currentTimeMillis()

                val evResult = bayesianAgent.answer(listOf(evPrompt))
              stepTime = System.currentTimeMillis() - stepStartTime
                log.debug("Expected value analysis completed in ${stepTime}ms: ${evResult.length} characters")
                // Write to transcript
                transcript?.write("""
                    ## Expected Value Analysis
                    <details>
                    <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                    $evResult
                    </details>
                """.trimIndent().toByteArray())



                evTask.add(
                  (buildString {
                        appendLine("## Expected Value & Risk Analysis")
                        appendLine()
                        appendLine(evResult)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                  }).renderMarkdown()
                )
                evTask.complete()

                resultBuilder.append("## Expected Value Analysis\n\n")
                resultBuilder.append(evResult.take(maxDescriptionLength))
              if (evResult.length > maxDescriptionLength) resultBuilder.append("\n... (truncated)")
                resultBuilder.append("\n\n")

                overviewTask.add(
                  (buildString {
                        appendLine()
                        appendLine("✅ Expected value analysis complete (${stepTime / 1000.0}s)")
                  }).renderMarkdown()
                )
            }

            // Key Uncertainties (if requested)
            if (executionConfig.identify_key_uncertainties) {
              log.info("Identifying key uncertainties")
                val uncertaintyTask = tabs.newTask("Key Uncertainties")

                uncertaintyTask.add(
                  (buildString {
                        appendLine("# Key Uncertainties")
                        appendLine()
                        appendLine("**Status:** Identifying critical uncertainties...")
                        appendLine()
                  }).renderMarkdown()
                )

                val uncertaintyPrompt = buildUncertaintyPrompt(
                    hypotheses,
                    evidence,
                    updateResult
                )
                stepStartTime = System.currentTimeMillis()

                val uncertaintyResult = bayesianAgent.answer(listOf(uncertaintyPrompt))
              stepTime = System.currentTimeMillis() - stepStartTime
                log.debug("Uncertainty analysis completed in ${stepTime}ms: ${uncertaintyResult.length} characters")
                // Write to transcript
                transcript?.write("""
                    ## Key Uncertainties
                    <details>
                    <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                    $uncertaintyResult
                    </details>
                """.trimIndent().toByteArray())



                uncertaintyTask.add(
                  (buildString {
                        appendLine("## Critical Uncertainties")
                        appendLine()
                        appendLine(uncertaintyResult)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                  }).renderMarkdown()
                )
                uncertaintyTask.complete()

                resultBuilder.append("## Key Uncertainties\n\n")
                resultBuilder.append(uncertaintyResult.take(maxDescriptionLength))
              if (uncertaintyResult.length > maxDescriptionLength) resultBuilder.append("\n... (truncated)")
                resultBuilder.append("\n\n")

                overviewTask.add(
                  (buildString {
                        appendLine()
                        appendLine("✅ Key uncertainties identified (${stepTime / 1000.0}s)")
                  }).renderMarkdown()
                )
            }

            // Experiment Suggestions (if requested)
            if (executionConfig.suggest_experiments) {
              log.info("Suggesting experiments")
                val experimentTask = tabs.newTask("Suggested Experiments")

                experimentTask.add(
                  (buildString {
                        appendLine("# Suggested Experiments")
                        appendLine()
                        appendLine("**Status:** Generating experiment recommendations...")
                        appendLine()
                  }).renderMarkdown()
                )

                val experimentPrompt = buildExperimentPrompt(
                    hypotheses,
                    evidence,
                    updateResult
                )
                stepStartTime = System.currentTimeMillis()

                val experimentResult = bayesianAgent.answer(listOf(experimentPrompt))
              stepTime = System.currentTimeMillis() - stepStartTime
                log.debug("Experiment suggestions completed in ${stepTime}ms: ${experimentResult.length} characters")
                // Write to transcript
                transcript?.write("""
                    ## Suggested Experiments
                    <details>
                    <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                    $experimentResult
                    </details>
                """.trimIndent().toByteArray())



                experimentTask.add(
                  (buildString {
                        appendLine("## Recommended Experiments")
                        appendLine()
                        appendLine(experimentResult)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                  }).renderMarkdown()
                )
                experimentTask.complete()

                resultBuilder.append("## Suggested Experiments\n\n")
                resultBuilder.append(experimentResult.take(maxDescriptionLength))
              if (experimentResult.length > maxDescriptionLength) resultBuilder.append("\n... (truncated)")
                resultBuilder.append("\n\n")

                overviewTask.add(
                  (buildString {
                        appendLine()
                        appendLine("✅ Experiment suggestions generated (${stepTime / 1000.0}s)")
                  }).renderMarkdown()
                )
            }

            val totalTime = System.currentTimeMillis() - startTime
          log.info("ProbabilisticReasoningTask logic completed in ${totalTime}ms")
            // Write final summary to transcript
            transcript?.write("\n---\n\n".toByteArray())
            transcript?.write("## Analysis Complete\n\n".toByteArray())
            transcript?.write("**Total Time:** ${totalTime / 1000.0}s\n\n".toByteArray())
            transcript?.write("**Hypotheses Analyzed:** ${hypotheses.size}\n\n".toByteArray())
            transcript?.write("**Evidence Processed:** ${evidence.size}\n\n".toByteArray())
            transcript?.write(
                "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )

            // Final overview update
            overviewTask.add(
              (buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Analysis Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Hypotheses Analyzed:** ${hypotheses.size}")
                    appendLine()
                    appendLine("**Evidence Processed:** ${evidence.size}")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
              }).renderMarkdown()
            )
            overviewTask.complete()
            // Best Practice: Use acceptButtonFooter for manual review
            task.add(acceptButtonFooter(task.ui) {
                semaphore.release()
            })
            semaphore.acquire()


            val finalResult = resultBuilder.toString()
            task.complete()
            resultFn(finalResult)

        } catch (e: Exception) {
          log.error("Error in ProbabilisticReasoningTask", e)
            task.error(e)
          // Triple Log: Transcript entry with details
            transcript?.write("\n---\n\n".toByteArray())
            transcript?.write("## Error Occurred\n\n".toByteArray())
            transcript?.write("**Error:** ${e.message}\n\n".toByteArray())
          transcript?.write(
            """
                <details>
                <summary>Stack Trace</summary>
                
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray()
          )

            overviewTask.add(
              """
                ---
                ## ❌ Error Occurred
                **Error:** ${e.message}
                """.trimIndent().renderMarkdown()
            )
            overviewTask.complete()

            val errorOutput = buildString {
                appendLine("# Error in Probabilistic Reasoning")
                appendLine("**Context:** $decisionContext")
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine()
                appendLine("\n---")
                if (resultBuilder.isNotBlank()) {
                    appendLine("\n## Partial Results")
                    appendLine("The analysis failed, but the following partial results were generated before the error:")
                    appendLine(resultBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
      }
    }

    private fun getInputFileCode(agent: TaskOrchestrator): String {
        return (executionConfig?.input_files ?: listOf())
            .flatMap { pattern: String ->
                val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
                (FileSelectionUtils.filteredWalk(agent.root.toFile()) {
                    when {
                        FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                        matcher.matches(agent.root.relativize(it.toPath())) -> true
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
                val file = agent.root.toFile().resolve(relativePath)
                try {
                    val content = file.readText()
                    "# $relativePath\n\n```\n$content\n```"
                } catch (e: Throwable) {
                    log.warn("Error reading file: $relativePath", e)
                    ""
                }
            }
    }

  private fun writeInputFilesSection(stream: OutputStream, agent: TaskOrchestrator) {
        try {
            val inputFileContent = getInputFileCode(agent)
            if (inputFileContent.isNotBlank()) {
                stream.write("\n## Input Files\n<details>\n<summary>File Contents</summary>\n\n".toByteArray(StandardCharsets.UTF_8))
                stream.write(inputFileContent.toByteArray(StandardCharsets.UTF_8))
                stream.write("\n</details>\n".toByteArray(StandardCharsets.UTF_8))
                stream.write("\n\n".toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }
        } catch (e: Exception) {
            log.error("Failed to write input files section to transcript", e)
        }
    }

    private fun buildBayesianUpdatePrompt(
        hypotheses: Map<String, Double>,
        evidence: List<String>,
        context: String,
        priorContext: String
    ): String {
        return """
You are performing Bayesian analysis to update beliefs based on evidence.

## Decision Context:
$context

## Prior Probabilities:
${hypotheses.entries.joinToString("\n") { (h, p) -> "- $h: ${String.format("%.1f%%", p * 100)}" }}

## Observed Evidence:
${evidence.joinToString("\n") { "- $it" }}

${if (priorContext.isNotBlank()) "## Additional Context:\n${priorContext.truncateForDisplay()}\n" else ""}

## Analysis Instructions:
1. **Evaluate Likelihood**: For each piece of evidence, assess how likely it would be observed under each hypothesis
   - Use likelihood ratios: P(Evidence|Hypothesis) / P(Evidence|¬Hypothesis)
   - Consider the diagnostic value of each piece of evidence
   
2. **Apply Bayes' Theorem**: Calculate posterior probabilities
   - Posterior ∝ Prior × Likelihood
   - Show your reasoning for likelihood assessments
   - Normalize to ensure probabilities sum to 1.0
   
3. **Quantify Uncertainty**: 
   - Calculate confidence intervals where appropriate
   - Identify assumptions in your likelihood assessments
   - Note any evidence that is ambiguous or could support multiple hypotheses
   
4. **Explain Updates**:
   - Which evidence had the strongest impact on beliefs?
   - Which hypotheses gained/lost probability and why?
   - Are there any surprising results?

## Output Format:
Provide a structured analysis with:
1. **Likelihood Assessment**: For each evidence item, explain its diagnostic value
2. **Posterior Probabilities**: Updated belief distribution (as percentages)
3. **Probability Changes**: Show how beliefs shifted from priors
4. **Confidence Analysis**: Quantify certainty in the conclusions
5. **Key Insights**: Most important findings from the update

Generate the Bayesian analysis now:
    """.trimIndent()
    }

    private fun buildExpectedValuePrompt(
        hypotheses: Map<String, Double>,
        updateResult: String,
        riskTolerance: String
    ): String {
        return """
Based on the Bayesian analysis above, calculate expected values and assess risks.

## Updated Beliefs:
(From previous analysis)
$updateResult

## Risk Tolerance:
$riskTolerance

## Analysis Instructions:
1. **Expected Value Calculation**:
   - For each hypothesis, estimate potential outcomes (costs/benefits)
   - Calculate expected value: EV = Σ(Probability × Outcome)
   - Consider both upside potential and downside risk
   
2. **Risk Assessment**:
   - Identify worst-case scenarios and their probabilities
   - Calculate variance and standard deviation of outcomes
   - Assess tail risks (low probability, high impact events)
   
3. **Decision Recommendations**:
   - Given the risk tolerance level, what action is optimal?
   - What is the expected value of perfect information (EVPI)?
   - Are there risk mitigation strategies to consider?
   
4. **Sensitivity Analysis**:
   - How sensitive are recommendations to probability estimates?
   - Which uncertainties have the biggest impact on expected value?

## Output Format:
Provide:
1. **Expected Value Summary**: EV for each hypothesis/decision path
2. **Risk Metrics**: Variance, downside risk, worst-case scenarios
3. **Decision Recommendation**: Optimal action given risk tolerance
4. **Sensitivity Analysis**: Key factors affecting the decision
5. **Value of Information**: What additional data would be most valuable?

Generate the expected value analysis now:
    """.trimIndent()
    }

    private fun buildUncertaintyPrompt(
        hypotheses: Map<String, Double>,
        evidence: List<String>,
        updateResult: String
    ): String {
        return """
Identify the key uncertainties that most affect the analysis.

## Current Analysis:
$updateResult

## Analysis Instructions:
1. **Identify Critical Uncertainties**:
   - Which probability estimates have the widest confidence intervals?
   - Which assumptions, if wrong, would most change the conclusions?
   - Are there missing pieces of evidence that would be highly diagnostic?
   
2. **Quantify Impact**:
   - For each uncertainty, estimate how much it affects the final decision
   - Use sensitivity analysis: how much would conclusions change if this estimate was off by 20%?
   
3. **Prioritize**:
   - Rank uncertainties by their impact on the decision
   - Consider both the magnitude of uncertainty and its leverage on outcomes
   
4. **Information Value**:
   - Which uncertainties, if resolved, would provide the most value?
   - What is the expected value of resolving each uncertainty?

## Output Format:
Provide:
1. **Top Uncertainties**: Ranked list of critical unknowns
2. **Impact Assessment**: How each uncertainty affects conclusions
3. **Confidence Intervals**: Ranges for key probability estimates
4. **Information Priorities**: Which uncertainties to resolve first

Generate the uncertainty analysis now:
    """.trimIndent()
    }

    private fun buildExperimentPrompt(
        hypotheses: Map<String, Double>,
        evidence: List<String>,
        updateResult: String
    ): String {
        return """
Suggest experiments or data collection to reduce uncertainty.

## Current Analysis:
$updateResult

## Analysis Instructions:
1. **Design Experiments**:
   - What tests or observations would most discriminate between hypotheses?
   - Consider feasibility, cost, and time required
   - Focus on high-value information (maximum uncertainty reduction)
   
2. **Expected Information Gain**:
   - For each experiment, estimate how much it would reduce uncertainty
   - Calculate expected value of sample information (EVSI)
   - Consider both positive and negative results
   
3. **Sequential Testing**:
   - What should be tested first?
   - How would results guide subsequent experiments?
   - When would you have enough information to decide?
   
4. **Practical Considerations**:
   - What resources are required?
   - What are the risks of each experiment?
   - Are there ethical or practical constraints?

## Output Format:
Provide:
1. **Recommended Experiments**: Prioritized list with rationale
2. **Expected Outcomes**: What each experiment would reveal
3. **Information Value**: Expected uncertainty reduction for each
4. **Implementation Plan**: Practical steps and resource requirements
5. **Decision Criteria**: When to stop testing and make a decision

Generate the experiment recommendations now:
""".trimIndent()
    }

    companion object {
      private val log = LoggerFactory.getLogger(ProbabilisticReasoningTask::class.java)
        @JvmStatic val ProbabilisticReasoning = TaskType(
          name = "ProbabilisticReasoning",
          category = "Reasoning",
          taskClass = ProbabilisticReasoningTask::class.java,
          executionConfigClass = ProbabilisticReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Reason under uncertainty using Bayesian analysis",
          tooltipHtml = """
                        Performs probabilistic reasoning and Bayesian analysis under uncertainty.
                        <ul>
                          <li>Assigns and updates probabilities using Bayes' theorem</li>
                          <li>Calculates expected values and quantifies risks</li>
                          <li>Identifies key uncertainties and information gaps</li>
                          <li>Suggests experiments to reduce uncertainty</li>
                          <li>Provides confidence intervals and sensitivity analysis</li>
                          <li>Useful for risk assessment, diagnostic reasoning, and decision making</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/SocraticDialogueTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class SocraticDialogueTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SocraticDialogueTaskExecutionConfigData?
) : AbstractTask<SocraticDialogueTask.SocraticDialogueTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxDescriptionLength = 1500

    class SocraticDialogueTaskExecutionConfigData(
        @Description("The initial question or hypothesis to explore")
        val initial_question: String? = null,
        @Description("Optional input files (supports glob patterns) to provide context for the dialogue")
        val input_files: List<String>? = null,
        @Description("Maximum dialogue depth (number of question-answer exchanges)")
        val max_depth: Int = 5,
        @Description("Whether to challenge assumptions at each level")
        val challenge_assumptions: Boolean = true,
        @Description("Topics or domains to constrain the dialogue")
        val domain_constraints: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = SocraticDialogue.name,
        task_description = "Explore '$initial_question' through Socratic dialogue",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (initial_question.isNullOrBlank()) {
                return "initial_question cannot be null or blank"
            }
            if (max_depth < 1 || max_depth > 20) {
                return "max_depth must be between 1 and 20, got: $max_depth"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        return """
 SocraticDialogue - Explore ideas through Socratic questioning
  ** Specify the initial question or hypothesis to explore
  ** Optionally provide input files (supports glob patterns) for context
  ** Configure maximum dialogue depth (default: 5 exchanges)
  ** Enable/disable assumption challenging
  ** Optionally constrain to specific topics or domains
  ** Creates a dialogue between questioner and responder agents
  ** Explores definitions, assumptions, implications, and contradictions
  ** Produces a structured dialogue transcript with insights
  ** Specify the initial question or hypothesis to explore
  ** Configure maximum dialogue depth (default: 5 exchanges)
  ** Enable/disable assumption challenging
  ** Optionally constrain to specific topics or domains
  ** Creates a dialogue between questioner and responder agents
  ** Explores definitions, assumptions, implications, and contradictions
  ** Produces a structured dialogue transcript with insights
        """.trimIndent()
    }

    private fun getInputFileContext(): String = (executionConfig?.input_files ?: listOf())
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
                "# $relativePath\n\n```\n${file.readText()}\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val inputFileContext = getInputFileContext()
        val startTime = System.currentTimeMillis()
        log.info("Starting SocraticDialogueTask with initial question: '${executionConfig?.initial_question}'")
        // Validate configuration
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }


        val initialQuestion = executionConfig?.initial_question
        if (initialQuestion.isNullOrBlank()) {
            log.error("No initial question specified")
            task.safeComplete("CONFIGURATION ERROR: No initial question specified", log)
            resultFn("CONFIGURATION ERROR: No initial question specified")
            return
        }

        val maxDepth = executionConfig.max_depth.coerceIn(1, 20)
        val challengeAssumptions = executionConfig.challenge_assumptions
        val domainConstraints = executionConfig.domain_constraints?.joinToString(", ") ?: "any domain"
        log.info("Configuration: maxDepth=$maxDepth, challengeAssumptions=$challengeAssumptions, domainConstraints=$domainConstraints")

        val ui = task.ui
        val api = defaultSmart ?: return
        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)
        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Socratic Dialogue: Exploring the Question")
            appendLine()
            appendLine("**Initial Question:** $initialQuestion")
            appendLine()
            appendLine("**Domain Constraints:** $domainConstraints")
            appendLine()
            appendLine("**Max Depth:** $maxDepth exchanges")
            appendLine()
            appendLine("**Challenge Assumptions:** ${if (challengeAssumptions) "Yes" else "No"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing dialogue agents...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))

        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            log.debug("Found prior context from previous tasks: ${priorContext.length} characters")
        }
        val combinedContext = listOfNotNull(priorContext, inputFileContext)
            .filter { it.isNotBlank() }
            .joinToString("\n\n---\n\n")

        // Create the Socratic questioner agent
        log.info("Creating Socratic questioner agent")
        val questionerAgent = ChatAgent(
            prompt = """
You are a Socratic questioner. Your role is to ask probing questions that:
1. Challenge assumptions and definitions
2. Explore implications and consequences
3. Identify contradictions or inconsistencies
4. Seek deeper understanding through clarification
5. Guide toward examining fundamental principles

${if (challengeAssumptions) "Actively challenge assumptions at each step." else "Focus on clarification and exploration."}

Domain constraints: $domainConstraints

Ask one clear, focused question at a time. Build on previous answers to go deeper.
            """.trimIndent(),
            model = api,
            temperature = 0.7
        )

        // Create the responder agent
        log.info("Creating responder agent")
        val responderAgent = ChatAgent(
            prompt = """
You are a thoughtful responder in a Socratic dialogue. Your role is to:
1. Answer questions honestly and thoroughly
2. Acknowledge when you're uncertain
3. Provide clear definitions and reasoning
4. Consider implications of your statements
5. Be open to revising your understanding

Domain constraints: $domainConstraints

Provide substantive, well-reasoned responses that advance the dialogue.
            """.trimIndent(),
            model = api,
            temperature = 0.5
        )

        val dialogueBuilder = StringBuilder()
        val fullDialogueBuilder = StringBuilder()
        // Create transcript file
        val (transcriptLink, transcriptStream) = createTranscriptFile(task)
        val transcriptWriter = transcriptStream?.bufferedWriter()
        transcriptWriter?.apply {
            write("# Socratic Dialogue Transcript\n\n")
            write("**Initial Question:** $initialQuestion\n\n")
            write("**Domain Constraints:** $domainConstraints\n\n")
            write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            write("---\n\n")
            flush()
        }


        // Concise output for final result
        dialogueBuilder.append("# Socratic Dialogue Analysis\n\n")
        dialogueBuilder.append("**Question:** $initialQuestion\n\n")

        // Full dialogue for UI tabs only
        fullDialogueBuilder.append("# Socratic Dialogue\n\n")
        fullDialogueBuilder.append("## Initial Question\n\n")
        fullDialogueBuilder.append("$initialQuestion\n\n")

        if (combinedContext.isNotBlank()) {
            fullDialogueBuilder.append("## Context from Previous Tasks\n\n")
            fullDialogueBuilder.append("$combinedContext\n\n")
            transcriptWriter?.apply {
                write("## Context from Previous Tasks\n\n")
                write("$combinedContext\n\n")
                flush()
            }
            // Add context tab
            val contextTask = tabs.newTask("Context")
            contextTask.add(
              buildString {
                appendLine("# Context from Previous Tasks")
                appendLine()
                appendLine(combinedContext)
              }.renderMarkdown(true)
            )
        }
        // Update overview with agent initialization complete
        overviewTask.add(
          buildString {
            appendLine()
            appendLine("✅ Dialogue agents initialized")
            appendLine()
            appendLine("*Starting dialogue exchanges...*")
          }.renderMarkdown(true)
        )

        var currentQuestion = initialQuestion ?: ""
        var currentResponse = ""
        val exchangeTimes = mutableListOf<Long>()

        try {
            for (depth in 1..maxDepth) {
                val exchangeStartTime = System.currentTimeMillis()
                log.info("Starting exchange $depth of $maxDepth")

                // Create tab for this exchange
                val exchangeTask = tabs.newTask("Exchange $depth")

                exchangeTask.add(
                  buildString {
                    appendLine("# Exchange $depth of $maxDepth")
                    appendLine()
                    appendLine("**Status:** Processing...")
                    appendLine()
                    appendLine("---")
                    appendLine()
                  }.renderMarkdown(true)
                )

                // Get response to current question
                val responsePrompt = if (depth == 1) {
                    "Consider this question: $currentQuestion\n\nProvide your initial thoughts and response."
                } else {
                    "Previous question: $currentQuestion\n\nYour previous response: $currentResponse\n\nNow respond to this follow-up."
                }
                log.debug("Generating response for exchange $depth")
                exchangeTask.add(
                  buildString {
                    appendLine("## Question")
                    appendLine()
                    appendLine("> $currentQuestion")
                    appendLine()
                    appendLine("*Generating response...*")
                    appendLine()
                  }.renderMarkdown(true)
                )

                currentResponse = responderAgent.answer(listOf(responsePrompt))
                if (currentResponse.isEmpty()) currentResponse = "No response generated"
                log.debug("Response generated for exchange $depth: ${currentResponse.length} characters")

                // Store full dialogue in UI builder
                fullDialogueBuilder.append("## Exchange $depth\n\n")
                fullDialogueBuilder.append("**Question:** $currentQuestion\n\n")
                fullDialogueBuilder.append("**Response:** $currentResponse\n\n")
                transcriptWriter?.apply {
                    write("## Exchange $depth\n\n")
                    write("**Question:** $currentQuestion\n\n")
                    write("**Response:** $currentResponse\n\n")
                    flush()
                }


                // Store only key points in concise output
                if (depth == 1 || depth == maxDepth) {
                    dialogueBuilder.append("### Exchange $depth\n")
                    dialogueBuilder.append("**Q:** ${currentQuestion.truncateForDisplay(maxDescriptionLength)}\n")
                    dialogueBuilder.append("**A:** ${currentResponse.truncateForDisplay(maxDescriptionLength)}\n\n")
                }

                exchangeTask.add(
                  buildString {
                    appendLine("## Response")
                    appendLine()
                    appendLine(currentResponse)
                    appendLine()
                  }.renderMarkdown(true)
                )

                // Generate next question if not at max depth
                if (depth < maxDepth) {
                    log.debug("Generating next question for exchange ${depth + 1}")
                    exchangeTask.add(
                      buildString {
                        appendLine("*Generating next question...*")
                        appendLine()
                      }.renderMarkdown(true)
                    )

                    val nextQuestionPrompt = """
Previous question: $currentQuestion

Response received: $currentResponse

Based on this response, generate the next Socratic question that:
${if (challengeAssumptions) "- Challenges an assumption made in the response" else "- Seeks deeper clarification"}
- Explores implications or consequences
- Identifies potential contradictions
- Moves toward fundamental principles

Provide only the question, without preamble.
                    """.trimIndent()

                    currentQuestion = questionerAgent.answer(listOf(nextQuestionPrompt))
                    if (currentQuestion.isEmpty()) currentQuestion =
                        "What are the implications of this understanding?"
                    log.debug("Next question generated: $currentQuestion")
                    exchangeTask.add(
                      buildString {
                        appendLine("## Next Question")
                        appendLine()
                        appendLine("> $currentQuestion")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    transcriptWriter?.apply {
                        write("**Next Question:** $currentQuestion\n\n")
                        flush()
                    }

                }
                val exchangeTime = System.currentTimeMillis() - exchangeStartTime
                exchangeTimes.add(exchangeTime)
                // Mark exchange as complete
                exchangeTask.add(
                  buildString {
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                    appendLine()
                    appendLine("**Processing Time:** ${exchangeTime / 1000.0}s")
                  }.renderMarkdown(true)
                )
                // Update overview with progress
                overviewTask.add(
                  buildString {
                    appendLine()
                    appendLine("✅ Exchange $depth complete (${exchangeTime / 1000.0}s)")
                    if (depth < maxDepth) {
                      appendLine()
                      appendLine("*Processing exchange ${depth + 1}...*")
                    }
                  }.renderMarkdown(true)
                )
                log.info("Exchange $depth completed in ${exchangeTime}ms")
            }

            log.info("Generating synthesis of dialogue")
            val synthesisTask = tabs.newTask("Synthesis")

            synthesisTask.add(
              buildString {
                appendLine("# Synthesis")
                appendLine()
                appendLine("**Status:** Generating comprehensive synthesis...")
                appendLine()
              }.renderMarkdown(true)
            )

            val synthesisPrompt = """
Review this Socratic dialogue and provide a synthesis that:
1. Summarizes key insights discovered
2. Identifies assumptions that were challenged or confirmed
3. Notes any contradictions or tensions revealed
4. Suggests areas for further exploration
5. Draws conclusions about the original question

Dialogue:
$dialogueBuilder

Provide a structured synthesis.
            """.trimIndent()

            var synthesis = ""
            synthesis = responderAgent.answer(listOf(synthesisPrompt))
            if (synthesis.isEmpty()) synthesis = "Unable to generate synthesis"
            log.debug("Synthesis generated: ${synthesis.length} characters")

            dialogueBuilder.append("## Key Insights\n\n")
            dialogueBuilder.append(synthesis)
            transcriptWriter?.apply {
                write("## Synthesis\n\n")
                write(synthesis)
                write("\n\n")
                flush()
            }


            // Add summary statistics
            dialogueBuilder.append("\n\n---\n\n")
            dialogueBuilder.append("**Exchanges:** $maxDepth | ")
            dialogueBuilder.append("**Domain:** $domainConstraints | ")
            dialogueBuilder.append("**Time:** ${(System.currentTimeMillis() - startTime) / 1000}s\n")

            synthesisTask.add(
              buildString {
                appendLine("## Key Insights")
                appendLine()
                appendLine(synthesis)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Status:** ✅ Complete")
              }.renderMarkdown(true)
            )

            val finalResult = dialogueBuilder.toString()
            val fullDialogue = fullDialogueBuilder.toString()
            val totalTime = System.currentTimeMillis() - startTime
            val avgExchangeTime = if (exchangeTimes.isNotEmpty()) exchangeTimes.average() else 0.0
            log.info("SocraticDialogueTask completed: total_time=${totalTime}ms, exchanges=$maxDepth, avg_exchange_time=${avgExchangeTime}ms, output_size=${finalResult.length} chars (full: ${fullDialogue.length} chars)")
            transcriptWriter?.apply {
                write("---\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n\n"
                )
                write("**Total Time:** ${totalTime / 1000.0}s | **Exchanges:** $maxDepth | **Avg Exchange Time:** ${avgExchangeTime / 1000.0}s\n")
                flush()
                close()
            }


            // Update overview with completion stats
            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Dialogue Complete")
                appendLine()
                appendLine("**Total Time:** ${totalTime / 1000.0}s")
                appendLine()
                appendLine("**Exchanges Completed:** $maxDepth")
                appendLine()
                appendLine("**Average Exchange Time:** ${avgExchangeTime / 1000.0}s")
                appendLine()
                appendLine("**Total Characters Generated:** ${finalResult.length}")
                appendLine()
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )

            task.complete("Completed $maxDepth exchanges in ${totalTime / 1000}s. Concise analysis: ${finalResult.length} chars.")

            val summaryMessage = buildString {
                appendLine(finalResult)
                appendLine("\n---\n")
                appendLine(
                    "Full dialogue transcript: <a href='$transcriptLink' target='_blank'>$transcriptLink</a> <a href='${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.html' target='_blank'>html</a> <a href='${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.pdf' target='_blank'>pdf</a>"
                )
            }
            resultFn(summaryMessage)

        } catch (e: Exception) {
            log.error("Error during Socratic dialogue", e)
            task.error(e)
            transcriptWriter?.apply {
                write("\n\n---\n\n## ❌ Error Occurred\n\n")
                write("**Error:** ${e.message}\n\n")
                flush()
                close()
            }


            // Update overview with error
            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ❌ Error Occurred")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Type:** ${e.javaClass.simpleName}")
              }.renderMarkdown(true)
            )

            val errorOutput = buildString {
                appendLine("# Error in Socratic Dialogue")
                appendLine()
                appendLine("**Question:** $initialQuestion")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Exchanges Completed:** ${exchangeTimes.size} of $maxDepth")
                if (dialogueBuilder.isNotEmpty()) {
                    appendLine()
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(dialogueBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }

    private fun createTranscriptFile(task: SessionTask): Pair<String, FileOutputStream?> {
        val transcriptFile =
            this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.add(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(".md")
            }.pdf' target='_blank'>pdf</a>"
        )
        return Pair(link, markdownTranscript)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SocraticDialogueTask::class.java)
        @JvmStatic val SocraticDialogue = TaskType(
          name = "SocraticDialogue",
          category = "Reasoning",
          taskClass = SocraticDialogueTask::class.java,
          executionConfigClass = SocraticDialogueTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Explore ideas through Socratic questioning",
          tooltipHtml = """
                        Uses Socratic questioning methodology to deeply explore ideas.
                        <ul>
                          <li>Creates dialogue between questioner and responder agents</li>
                          <li>Challenges assumptions and definitions</li>
                          <li>Explores implications and consequences</li>
                          <li>Identifies contradictions and tensions</li>
                          <li>Configurable dialogue depth and constraints</li>
                          <li>Generates synthesis of insights discovered</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/StructuralInvariantAnalysisTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class StructuralInvariantAnalysisTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: StructuralInvariantAnalysisTaskExecutionConfigData?
) : AbstractTask<StructuralInvariantAnalysisTask.StructuralInvariantAnalysisTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class StructuralInvariantAnalysisTaskExecutionConfigData(
        @Description("The object to analyze (e.g., 'A Prime Number', 'A Black Hole')")
        val subject_object: String? = null,
        @Description("List of transformations to apply (e.g., 'symmetry_groups', 'limit_cases', 'context_inversion')")
        val transformation_types: List<String> = listOf("symmetry_groups", "limit_cases", "context_inversion"),
        @Description("Output format: 'fingerprint' (list of invariants) or 'signature' (hashable summary)")
        val output_format: String = "fingerprint",
        @Description("The specific files (or file patterns) to be used as input")
        val input_files: List<String>? = null,
        @Description("Additional files for context")
        val related_files: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = StructuralInvariantAnalysis.name,
        task_description = subject_object,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (subject_object.isNullOrBlank()) return "subject_object must not be null or blank"
            if (output_format !in listOf(
                    "fingerprint",
                    "signature"
                )
            ) return "output_format must be 'fingerprint' or 'signature'"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
StructuralInvariantAnalysis - Distill an object to immutable properties
  ** Specify the subject_object to analyze
  ** Define transformation_types (e.g., symmetry_groups, limit_cases)
  ** Select output_format ('fingerprint' or 'signature')
  ** Process involves:
     - Decontextualization (stripping domain terminology)
     - Stress Testing (applying transformations)
     - Invariant Extraction (identifying constants)
     - Signature Generation
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }

        val startTime = System.currentTimeMillis()
        var transcriptStream: FileOutputStream? = null

        try {
            val subject = executionConfig?.subject_object ?: ""
            val transformations = executionConfig?.transformation_types ?: emptyList()
            val format = executionConfig?.output_format ?: "fingerprint"

            transcriptStream = task.transcript()
            transcriptStream?.let { stream ->
                writeTranscriptHeader(stream, subject, transformations, format)
            }

            val api = defaultSmart ?: return
            log.info("Starting Structural Invariant Analysis: $subject")

            val tabbedDisplay = TabbedDisplay(task)

            tabbedDisplay.newTask("Overview").apply {

                header("Structural Invariant Analysis")
                add("<b>Subject:</b> $subject")
                add("<b>Transformations:</b> ${transformations.joinToString(", ")}")
                add("<b>Output Format:</b> $format")
                if (!executionConfig?.input_files.isNullOrEmpty()) {
                    add("<b>Input Files:</b> ${executionConfig?.input_files?.joinToString(", ")}")
                }
            }

            val inputFileContent =
                super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)
            val priorCode = getPriorCode(agent.executionState)

            val prompt = buildPrompt(subject, transformations, format, inputFileContent, priorCode)
            tabbedDisplay.newTask("Prompt").apply {
                expandable("Full Prompt", "<pre>${prompt}</pre>")
            }


            tabbedDisplay.newTask("Analysis").apply {
                add("Performing structural analysis... This may take a moment.", additionalClasses = "text-info")
            }

            val chatAgent = ChatAgent(
                prompt = "You are an expert in structural analysis, invariant theory, and category theory. Your goal is to strip objects of their context to find their mathematical core.",
                model = api
            )

            val response = chatAgent.answer(listOf(prompt))

            tabbedDisplay.newTask("Result").apply {
                add(MarkdownUtil.renderMarkdown(response, ui = task.ui))
                transcriptStream?.write("\n\n## Analysis Result\n\n$response".toByteArray(StandardCharsets.UTF_8))
            }

            val duration = System.currentTimeMillis() - startTime
            log.info("Structural Invariant Analysis completed in ${duration}ms")

            if (orchestrationConfig.autoFix) {
                val (link, _) = task.createFile("invariant_analysis_transcript.md")
                task.safeComplete("Analysis complete. <a href='$link' target='_blank'>View Transcript</a>", log)
                resultFn(response)
            } else {
                val acceptLink = task.ui.hrefLink("Accept Result", "btn btn-success") {
                    val (link, _) = task.createFile("invariant_analysis_transcript.md")
                    task.complete("Analysis accepted. <a href='$link' target='_blank'>View Transcript</a>")
                    resultFn(response)
                }
                task.add("<div class='p-3'>$acceptLink</div>")
                task.complete()
            }

        } catch (e: Exception) {
            log.error("Error in Structural Invariant Analysis", e)
            task.error(e)
            transcriptStream?.write("\n\nERROR: ${e.message}".toByteArray(StandardCharsets.UTF_8))
            resultFn("ERROR: ${e.message}")
        } finally {
            transcriptStream?.flush()
            transcriptStream?.close()
        }
    }

    private fun writeTranscriptHeader(
        stream: FileOutputStream,
        subject: String,
        transformations: List<String>,
        format: String
    ) {
        val header = """
            # Structural Invariant Analysis Transcript
            
            **Date:** ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}
            **Subject:** $subject
            **Transformations:** $transformations
            **Format:** $format
            
            ---
            
        """.trimIndent()
        stream.write(header.toByteArray(StandardCharsets.UTF_8))
    }

    private fun buildPrompt(
        subject: String,
        transformations: List<String>,
        format: String,
        inputFileContent: String,
        priorCode: String
    ): String {
        return """
            Analyze the following subject object to identify its structural invariants.
            
            ## Subject Object:
            $subject
            
            ## Input Context:
            ${if (inputFileContent.isNotBlank()) inputFileContent else "No input files provided."}
            
            ## Previous Context:
            $priorCode
            
            ## Transformations to Apply:
            ${transformations.joinToString("\n") { "- $it" }}
            
            ## Process:
            1. **Decontextualization**: Strip domain-specific terminology. Describe the object purely in terms of relationships, structure, and dynamics.
            2. **Stress Testing**: Apply the specified transformations. 
               - Does the property hold if parameters are scaled?
               - Does it hold under permutation?
               - Does it hold in a different context?
            3. **Invariant Extraction**: Identify properties that survive ALL transformations. These are the invariants.
            4. **Signature Generation**: Compile the invariants into the requested format ($format).
            
            ## Output Format:
            
            ### Decontextualized Description
            [Abstract description]
            
            ### Stress Test Analysis
            [Analysis of how the object behaves under transformations]
            
            ### Identified Invariants
            - [Invariant 1]: [Explanation]
            - [Invariant 2]: [Explanation]
            
            ### Structural $format
            [Final output in the requested format]
        """.trimIndent()
    }

    companion object {
      private val log = LoggerFactory.getLogger(StructuralInvariantAnalysisTask::class.java)
        @JvmStatic val StructuralInvariantAnalysis = TaskType(
          name = "StructuralInvariantAnalysis",
          category = "Reasoning",
          taskClass = StructuralInvariantAnalysisTask::class.java,
          executionConfigClass = StructuralInvariantAnalysisTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Distill an object down to its immutable properties and symmetries",
          tooltipHtml = """
                          Performs rigorous structural analysis to identify invariants.
                          <ul>
                            <li>Decontextualizes objects to remove domain bias</li>
                            <li>Applies theoretical transformations (scaling, rotation, etc.)</li>
                            <li>Extracts immutable properties (invariants)</li>
                            <li>Generates structural signatures for cross-domain comparison</li>
                          </ul>
                        """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/SystemsThinkingTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SystemsThinkingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SystemsThinkingTaskExecutionConfigData?
) : AbstractTask<SystemsThinkingTask.SystemsThinkingTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxDescriptionLength = 1500

    class SystemsThinkingTaskExecutionConfigData(
        @Description("Description of the system to analyze")
        var system_description: String? = null,
        @Description("Whether to identify feedback loops (reinforcing and balancing)")
        var identify_feedback_loops: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("Whether to map delays and accumulations in the system")
        var map_delays: Boolean = true,
        @Description("Whether to find leverage points for intervention")
        var find_leverage_points: Boolean = true,
        @Description("List of potential interventions to simulate (e.g., 'Implement caching layer', 'Add rate limiting'). Leave empty or null to skip intervention simulation.")
        var simulate_interventions: List<String>? = null,
        @Description("Time horizon for analysis (e.g., '6 months', '1 year')")
        var time_horizon: String? = "6 months",
        @Description("Whether to identify system archetypes")
        var identify_archetypes: Boolean = true,
        @Description("Whether to analyze emergent behavior")
        var analyze_emergent_behavior: Boolean = true,
        @Description("Additional files for context")
        var related_files: List<String>? = null,
        @Description("Focus areas or subsystems to prioritize in the analysis")
        var focus_areas: List<String>? = null,
        @Description("Specific questions to answer about the system")
        var analysis_questions: List<String>? = null,
        @Description("Description of the task")
        task_description: String? = null,
        @Description("List of task IDs this task depends on")
        task_dependencies: MutableList<String>? = null,
        @Description("The current state of the task")
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = SystemsThinking.name,
        task_description = task_description
            ?: "Analyze system dynamics for: ${system_description?.take(50)}${if ((system_description?.length ?: 0) > 50) "..." else ""}",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        return """
 SystemsThinking - Analyze complex systems through feedback loops and dynamics
 ** Specify the system to analyze (e.g., "CI/CD pipeline", "team workflow", "market dynamics")
  ** Identify feedback loops (reinforcing and balancing)
  ** Map delays and accumulations
  ** Find leverage points for intervention
  ** Simulate potential interventions (provide a list of specific interventions to simulate)
  ** Identify system archetypes (e.g., "Limits to Growth", "Shifting the Burden")
  ** Analyze emergent behavior and unintended consequences
  ** Optionally specify focus_areas to prioritize certain subsystems
  ** Optionally provide analysis_questions for specific insights
  ** Useful for:
     - Understanding system behavior
     - Performance optimization
     - Identifying unintended consequences
     - Organizational dynamics
     - Technical debt dynamics
     - Strategic planning and scenario analysis
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
      log.info("Starting SystemsThinkingTask for system: '${executionConfig?.system_description?.take(100)}'")
      val transcript = task.transcript()

        val systemDescription = executionConfig?.system_description
        if (systemDescription.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No system description specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val api = defaultSmart ?: return

        val ui = task.ui
        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")
        try {

            val timeHorizon = executionConfig.time_horizon ?: "6 months"
            val interventions = executionConfig.simulate_interventions ?: emptyList()
            val focusAreas = executionConfig.focus_areas ?: emptyList()
            val analysisQuestions = executionConfig.analysis_questions ?: emptyList()
            overviewTask.header("Systems Thinking Analysis", level = 1)

          val overviewMarkdown = buildString {
                    appendLine("**System:** $systemDescription")
                    appendLine()
                    appendLine("**Time Horizon:** $timeHorizon")
                    appendLine()
                    if (focusAreas.isNotEmpty()) {
                        appendLine("**Focus Areas:**")
                        focusAreas.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    if (analysisQuestions.isNotEmpty()) {
                        appendLine("**Analysis Questions:**")
                        analysisQuestions.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    appendLine("**Analysis Components:**")
                    if (executionConfig.identify_feedback_loops) appendLine("- ✅ Feedback Loops")
                    if (executionConfig.map_delays) appendLine("- ✅ Delays & Accumulations")
                    if (executionConfig.find_leverage_points) appendLine("- ✅ Leverage Points")
                    if (executionConfig.identify_archetypes) appendLine("- ✅ System Archetypes")
                    if (executionConfig.analyze_emergent_behavior) appendLine("- ✅ Emergent Behavior")
                    if (interventions.isNotEmpty()) appendLine("- ✅ Intervention Simulation (${interventions.size} scenarios)")
                    appendLine()
                    appendLine(
                        "**Started:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** 🔄 Gathering context...")
          }.renderMarkdown()
          overviewTask.add(overviewMarkdown)

          transcript?.write(
                "# Systems Thinking Analysis\n\n**System:** $systemDescription\n\n**Time Horizon:** $timeHorizon\n\n**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n---\n\n".toByteArray()
            )

            // Gather context
            log.debug("Gathering context from prior tasks and related files")
            val priorContext = getPriorCode(agent.executionState)
            val inputFileContext = getInputFileCode()
            val relatedContext = gatherRelatedFiles()

            if (priorContext.isNotBlank() || inputFileContext.isNotBlank() || relatedContext.isNotBlank()) {
              transcript?.write(
                """
                    <details>
                    <summary>Analysis Context (Prior Tasks & Files)</summary>
                    
                    ### Prior Context
                    $priorContext
                    
                    ### Input Files
                    $inputFileContext
                    
                    ### Related Files
                    $relatedContext
                    </details>
                    
                    ---
                """.trimIndent().toByteArray()
              )

              val contextTask = tabs.newTask("Context")
                contextTask.header("Context", level = 1)

                if (priorContext.isNotBlank()) {
                  contextTask.expandable("Prior Task Results", priorContext.truncateForDisplay().renderMarkdown())
                }
                if (inputFileContext.isNotBlank()) {
                  contextTask.expandable("Input Files", inputFileContext.truncateForDisplay().renderMarkdown())
                }
                if (relatedContext.isNotBlank()) {

                  contextTask.expandable("Related Files", relatedContext.truncateForDisplay().renderMarkdown())
                }
            }

          // Initialize analysis agent
            log.info("Initializing systems thinking analysis agent")
            val analysisAgent = ChatAgent(
                prompt = buildSystemsThinkingPrompt(
                    systemDescription,
                    timeHorizon,
                    priorContext,
                    relatedContext,
                    focusAreas,
                    analysisQuestions
                ),
                model = api,
                temperature = 0.6
            )

          overviewTask.add(buildString {
                    appendLine()
                    appendLine("✅ Context gathered")
                    appendLine()
                    appendLine("**Status:** 🔄 Analyzing system structure...")
          }.renderMarkdown())

            // Step 1: System Structure Analysis
            log.debug("Analyzing system structure and components")
            val structureTask = tabs.newTask("System Structure")

            structureTask.header("System Structure", level = 2)
          structureTask.add("🔄 Analyzing components and relationships...".renderMarkdown())

            val structureAnalysis = analysisAgent.answer(
                listOf(
                    """
Analyze the system structure:
1. Identify key components and variables
2. Map relationships between components
3. Identify stocks (accumulations) and flows
4. Note information flows and decision points

Provide a clear, structured analysis.
          """.trimIndent()
                )
            )

          structureTask.add(buildString {
                    appendLine("✅ Analysis complete")
                    appendLine()
                    appendLine(structureAnalysis)
          }.renderMarkdown())
          transcript?.write("## System Structure\n\n$structureAnalysis\n\n---\n\n".toByteArray())

            // Step 2: Feedback Loops
            if (executionConfig.identify_feedback_loops) {
                log.debug("Identifying feedback loops")
                val loopsTask = tabs.newTask("Feedback Loops")

                loopsTask.header("Feedback Loops", level = 2)
              loopsTask.add("🔄 Identifying reinforcing and balancing loops...".renderMarkdown())

                val loopsAnalysis = analysisAgent.answer(
                    listOf(
                        """
Identify feedback loops in the system:
1. **Reinforcing Loops (R)**: Loops that amplify change (virtuous or vicious cycles)
2. **Balancing Loops (B)**: Loops that resist change and seek equilibrium

For each loop:
- Name and describe the loop
- List the causal chain (A → B → C → A)
- Classify as reinforcing (R) or balancing (B)
- Explain the behavior it produces
- Rate its strength/impact (High/Medium/Low)

Then create a Mermaid diagram showing the loops using:
- Solid arrows (-->) for positive causality
- Dashed arrows (-.->) for negative causality
- Label loops as R1, R2, B1, B2, etc.

Provide the analysis and diagram.
          """.trimIndent()
                    )
                )

              transcript?.write("## Feedback Loops\n\n$loopsAnalysis\n\n---\n\n".toByteArray())
              loopsTask.add(buildString {
                        appendLine("✅ Analysis complete")
                        appendLine()
                        appendLine(loopsAnalysis)
              }.renderMarkdown())
            }

            // Step 3: Delays and Accumulations
            if (executionConfig.map_delays) {
                log.debug("Mapping delays and accumulations")
                val delaysTask = tabs.newTask("Delays & Accumulations")

                delaysTask.header("Delays & Accumulations", level = 2)
              delaysTask.add("🔄 Analyzing time lags and stocks...".renderMarkdown())

                val delaysAnalysis = analysisAgent.answer(
                    listOf(
                        """
Analyze delays and accumulations:
1. **Delays**: Identify time lags between cause and effect
   - Information delays
   - Physical delays
   - Decision delays
2. **Accumulations (Stocks)**: Identify what accumulates over time
   - What builds up?
   - What depletes?
   - What are the inflows and outflows?
3. **Impact**: How do these delays and accumulations affect system behavior?

Provide specific examples with estimated time scales.
          """.trimIndent()
                    )
                )

              delaysTask.add(buildString {
                        appendLine("✅ Analysis complete")
                        appendLine()
                        appendLine(delaysAnalysis)
              }.renderMarkdown())
              transcript?.write("## Delays & Accumulations\n\n$delaysAnalysis\n\n---\n\n".toByteArray())
            }

            // Step 4: System Archetypes
            if (executionConfig.identify_archetypes) {
                log.debug("Identifying system archetypes")
                val archetypesTask = tabs.newTask("System Archetypes")

                archetypesTask.header("System Archetypes", level = 2)
              archetypesTask.add("🔄 Identifying common patterns...".renderMarkdown())

                val archetypesAnalysis = analysisAgent.answer(
                    listOf(
                        """
Identify system archetypes present in this system. Common archetypes include:
- **Limits to Growth**: Growth slows as limits are approached
- **Shifting the Burden**: Short-term fixes undermine long-term solutions
- **Eroding Goals**: Performance standards decline over time
- **Escalation**: Competing parties escalate actions
- **Success to the Successful**: Winners get more resources
- **Tragedy of the Commons**: Individual actions deplete shared resources
- **Fixes that Fail**: Solutions create new problems
- **Growth and Underinvestment**: Growth is limited by underinvestment

For each archetype found:
1. Name the archetype
2. Explain how it manifests in this system
3. Describe the typical behavior pattern
4. Suggest intervention strategies

Focus on the most relevant archetypes.
          """.trimIndent()
                    )
                )

              archetypesTask.add(buildString {
                        appendLine("✅ Analysis complete")
                        appendLine()
                        appendLine(archetypesAnalysis)
              }.renderMarkdown())
              transcript?.write("## System Archetypes\n\n$archetypesAnalysis\n\n---\n\n".toByteArray())
            }

            // Step 5: Emergent Behavior
            if (executionConfig.analyze_emergent_behavior) {
                log.debug("Analyzing emergent behavior")
                val emergentTask = tabs.newTask("Emergent Behavior")

                emergentTask.header("Emergent Behavior", level = 2)
              emergentTask.add("🔄 Predicting system-level patterns...".renderMarkdown())

                val emergentAnalysis = analysisAgent.answer(
                    listOf(
                        """
Analyze emergent behavior in the system:
1. **Current Emergent Patterns**: What system-level behaviors emerge from component interactions?
2. **Unintended Consequences**: What side effects or unexpected outcomes occur?
3. **Future Predictions**: Over the $timeHorizon time horizon, what behaviors are likely to emerge?
4. **Tipping Points**: Are there thresholds where behavior changes dramatically?
5. **Resilience**: How does the system respond to disruptions?

Consider both positive and negative emergent behaviors.
          """.trimIndent()
                    )
                )

              emergentTask.add(buildString {
                        appendLine("✅ Analysis complete")
                        appendLine()
                        appendLine(emergentAnalysis)
              }.renderMarkdown())
              transcript?.write("## Emergent Behavior\n\n$emergentAnalysis\n\n---\n\n".toByteArray())
            }

            // Step 6: Leverage Points
            if (executionConfig.find_leverage_points) {
                log.debug("Finding leverage points")
                val leverageTask = tabs.newTask("Leverage Points")

                leverageTask.header("Leverage Points", level = 2)
              leverageTask.add("🔄 Identifying high-impact intervention points...".renderMarkdown())

                val leverageAnalysis = analysisAgent.answer(
                    listOf(
                        """
Identify leverage points for intervention, ranked by effectiveness (Meadows' hierarchy):
1. **Paradigms**: Mindsets and goals that shape the system
2. **Goals**: System objectives and metrics
3. **Self-Organization**: System structure and evolution
4. **Rules**: Incentives, constraints, and feedback
5. **Information Flows**: Who has access to what information
6. **Reinforcing Loops**: Amplifying feedback mechanisms
7. **Balancing Loops**: Stabilizing feedback mechanisms
8. **Delays**: Time lags in the system
9. **Stocks and Flows**: Material and information accumulations
10. **Parameters**: Constants and rates

For each leverage point:
- Describe the intervention
- Explain why it's high-leverage
- Estimate impact (High/Medium/Low)
- Note potential risks or side effects
- Suggest implementation approach

Focus on the most impactful leverage points.
          """.trimIndent()
                    )
                )

              leverageTask.add(buildString {
                        appendLine("✅ Analysis complete")
                        appendLine()
                        appendLine(leverageAnalysis)
              }.renderMarkdown())
              transcript?.write("## Leverage Points\n\n$leverageAnalysis\n\n---\n\n".toByteArray())
            }

            // Step 7: Intervention Simulation
            if (interventions.isNotEmpty()) {
                log.debug("Simulating ${interventions.size} interventions")
                val simulationTask = tabs.newTask("Intervention Simulation")
                simulationTask.header("Intervention Simulation", level = 2)

              simulationTask.add(buildString {
                        appendLine("🔄 Simulating ${interventions.size} intervention scenarios...")
                        appendLine()
                        appendLine("**Interventions:**")
                        interventions.forEach { appendLine("- $it") }
              }.renderMarkdown())

                val simulationResults = mutableListOf<String>()
                interventions.forEachIndexed { index, intervention ->
                    log.debug("Simulating intervention ${index + 1}/${interventions.size}: $intervention")

                    val simulationAnalysis = analysisAgent.answer(
                        listOf(
                            """
Simulate the intervention: "$intervention"

Analyze over the $timeHorizon time horizon:
1. **Immediate Effects** (0-1 month): What happens right away?
2. **Short-term Effects** (1-3 months): How does the system respond?
3. **Medium-term Effects** (3-6 months): What patterns emerge?
4. **Long-term Effects** (6+ months): What is the steady-state outcome?
5. **Feedback Loop Impacts**: Which loops are strengthened/weakened?
6. **Unintended Consequences**: What side effects might occur?
7. **Overall Assessment**: Rate effectiveness (High/Medium/Low) and explain

Be specific about mechanisms and timelines.
              """.trimIndent()
                        )
                    )

                    val resultMarkdown = """
### Intervention ${index + 1}: $intervention

$simulationAnalysis

---
          """.trimIndent()
                    simulationResults.add(resultMarkdown)

                    // Stream result to UI


                  simulationTask.add(resultMarkdown.renderMarkdown())

                  transcript?.write(resultMarkdown.toByteArray())
                }

              simulationTask.add(buildString {
                        appendLine("✅ Simulation complete")
              }.renderMarkdown())
            }

            // Step 8: Synthesis and Recommendations
            log.debug("Generating synthesis and recommendations")
            val synthesisTask = tabs.newTask("Synthesis")

            synthesisTask.header("Synthesis & Recommendations", level = 2)
          synthesisTask.add("🔄 Generating comprehensive synthesis...".renderMarkdown())

            val synthesisPrompt = buildString {
                appendLine("Provide a comprehensive synthesis of the systems thinking analysis:")
                appendLine()
                appendLine("1. **Key Insights**: What are the most important findings?")
                appendLine("2. **System Behavior Summary**: How does this system behave and why?")
                appendLine("3. **Critical Feedback Loops**: Which loops drive system behavior?")
                appendLine("4. **Highest-Impact Leverage Points**: Where should interventions focus?")
                if (interventions.isNotEmpty()) {
                    appendLine("5. **Recommended Interventions**: Which simulated interventions are most promising?")
                }
                appendLine("6. **Implementation Roadmap**: Suggested sequence of actions")
                appendLine("7. **Monitoring Metrics**: What should be tracked to assess progress?")
                appendLine("8. **Risks and Mitigation**: What could go wrong and how to prevent it?")
                appendLine()
                appendLine("Provide actionable, prioritized recommendations.")
            }

            val synthesis = analysisAgent.answer(listOf(synthesisPrompt.toString()))

          synthesisTask.add(buildString {
                    appendLine("✅ Analysis complete")
                    appendLine()
                    appendLine(synthesis)
          }.renderMarkdown())
          transcript?.write("## Synthesis & Recommendations\n\n$synthesis\n\n---\n\n".toByteArray())

            // Build concise final result
            val finalResult = buildString {
                appendLine("# Systems Thinking Analysis: $systemDescription")
                appendLine()
                appendLine("**Time Horizon:** $timeHorizon")
                appendLine()
                appendLine("## Key Findings")
                appendLine()
                appendLine(synthesis.truncateForDisplay(maxDescriptionLength))
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(
                    "**Analysis Components:** ${
                        listOfNotNull(
                            if (executionConfig.identify_feedback_loops) "Feedback Loops" else null,
                            if (executionConfig.map_delays) "Delays" else null,
                            if (executionConfig.find_leverage_points) "Leverage Points" else null,
                            if (executionConfig.identify_archetypes) "Archetypes" else null,
                            if (executionConfig.analyze_emergent_behavior) "Emergent Behavior" else null,
                            if (interventions.isNotEmpty()) "Intervention Simulation (${interventions.size})" else null
                        ).joinToString(", ")
                    }"
                )
            }

            val duration = System.currentTimeMillis() - startTime
            log.info(
                "SystemsThinkingTask completed: system='$systemDescription', " +
                        "duration=${duration}ms, interventions=${interventions.size}, " +
                        "output_size=${finalResult.length} chars"
            )

          overviewTask.add(buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Analysis Complete")
                    appendLine()
                    appendLine("**Total Time:** ${duration / 1000.0}s")
                    appendLine()
                    appendLine(
                        "**Components Analyzed:** ${
                            listOfNotNull(
                                if (executionConfig.identify_feedback_loops) "Feedback Loops" else null,
                                if (executionConfig.map_delays) "Delays & Accumulations" else null,
                                if (executionConfig.find_leverage_points) "Leverage Points" else null,
                                if (executionConfig.identify_archetypes) "System Archetypes" else null,
                                if (executionConfig.analyze_emergent_behavior) "Emergent Behavior" else null,
                                if (interventions.isNotEmpty()) "Intervention Simulation" else null
                            ).size
                        }"
                    )
                    appendLine()
                    if (interventions.isNotEmpty()) {
                        appendLine("**Interventions Simulated:** ${interventions.size}")
                        appendLine()
                    }
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
          }.renderMarkdown())
          transcript?.write(
                "\n\n## Analysis Complete\n\n**Total Time:** ${duration / 1000.0}s\n\n**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )


          task.complete("Systems thinking analysis completed in ${duration / 1000}s.".renderMarkdown())
            resultFn(finalResult.toString())

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
          transcript?.write(
            """
                <details>
                <summary>Error Occurred: ${e.message}</summary>
                
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray()
          )

          log.error("SystemsThinkingTask failed after ${duration}ms for system: $systemDescription", e)
            task.error(e)

          overviewTask.add(buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
            appendLine("**Error:** ${e.message?.take(200)}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
          }.renderMarkdown())

            val errorOutput = buildString {
              appendLine("## Analysis Failed")
              appendLine()
              appendLine("The systems thinking analysis encountered an error.")
                appendLine("# Error in Systems Thinking Analysis")
                appendLine()
                appendLine("**System:** $systemDescription")
                appendLine()
                appendLine("**Error:** ${e.message}")
            }
            resultFn(errorOutput.toString())
        } finally {
          transcript?.close()
        }
    }

    private fun buildSystemsThinkingPrompt(
        systemDescription: String,
        timeHorizon: String,
        priorContext: String,
        relatedContext: String,
        focusAreas: List<String>,
        analysisQuestions: List<String>
    ): String {
        return """
 You are an expert in systems thinking, system dynamics, and complex adaptive systems. Your role is to analyze systems through the lens of feedback loops, delays, accumulations, and emergent behavior.

 ## System to Analyze:
 $systemDescription

 ## Time Horizon:
 $timeHorizon

${if (focusAreas.isNotEmpty()) "## Focus Areas:\n${focusAreas.joinToString("\n") { "- $it" }}\n\n" else ""}
${if (analysisQuestions.isNotEmpty()) "## Specific Questions to Address:\n${analysisQuestions.joinToString("\n") { "- $it" }}\n\n" else ""}
 ## Context:
 ${if (priorContext.isNotBlank()) "### Prior Task Results:\n$priorContext\n\n" else ""}
 ${if (relatedContext.isNotBlank()) "### Related Files:\n$relatedContext\n\n" else ""}

 ## Systems Thinking Principles:
 1. **Feedback Loops**: Systems are governed by reinforcing and balancing feedback loops
 2. **Delays**: Time lags between cause and effect create oscillations and instability
 3. **Stocks and Flows**: Accumulations (stocks) change through inflows and outflows
 4. **Non-linearity**: Small changes can have large effects; large changes can have small effects
 5. **Emergence**: System behavior arises from interactions, not individual components
 6. **Boundaries**: System definition affects what we see and can influence
 7. **Leverage Points**: Some interventions are far more effective than others

 ## Analysis Approach:
- Think in terms of circular causality, not linear cause-and-effect
- Look for feedback loops that drive system behavior
- Identify delays that create problems
- Find leverage points where small changes have big impacts
- Consider unintended consequences and side effects
- Use system archetypes to recognize common patterns
- Think long-term about how interventions play out over time
${if (focusAreas.isNotEmpty()) "- Prioritize analysis of the specified focus areas" else ""}
${if (analysisQuestions.isNotEmpty()) "- Ensure the specific analysis questions are addressed" else ""}

Provide clear, actionable insights grounded in systems thinking principles.
    """.trimIndent()
    }

    private fun gatherRelatedFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""

        val maxFileSize = 2000
        val maxTotalSize = 8000
        var totalSize = 0

        return relatedFiles.mapNotNull { pattern ->
            val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
            root.toFile().walkTopDown()
                .filter { file ->
                    file.isFile && matcher.matches(root.relativize(file.toPath()))
                }
                .take(5) // Limit files per pattern
                .mapNotNull { file ->
                    if (totalSize >= maxTotalSize) return@mapNotNull null
                    val relativePath = root.relativize(file.toPath())
                    try {
                        val content = file.readText()
                        totalSize += content.length.coerceAtMost(maxFileSize)
                        "### $relativePath\n```\n${content.truncateForDisplay(maxFileSize)}\n```"
                    } catch (e: Exception) {
                        log.warn("Error reading file: $relativePath", e)
                        null
                    }
                }
                .joinToString("\n\n")
        }.joinToString("\n\n")
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
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
        private val log: Logger = LoggerFactory.getLogger(SystemsThinkingTask::class.java)
        @JvmStatic val SystemsThinking = TaskType(
          name = "SystemsThinking",
          category = "Reasoning",
          taskClass = SystemsThinkingTask::class.java,
          executionConfigClass = SystemsThinkingTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Analyze complex systems through feedback loops and dynamics",
          tooltipHtml = """
                        Performs systems thinking analysis to understand complex system behavior.
                        <ul>
                          <li>Identifies feedback loops (reinforcing and balancing)</li>
                          <li>Maps system archetypes (e.g., "Limits to Growth", "Shifting the Burden")</li>
                          <li>Analyzes delays and accumulations</li>
                          <li>Predicts emergent behavior and unintended consequences</li>
                          <li>Finds high-leverage intervention points</li>
                          <li>Simulates potential interventions over time</li>
                          <li>Useful for understanding system dynamics, optimization, and organizational change</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/TableCompilationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger

class TableCompilationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: TableCompilationTaskExecutionConfigData?
) : AbstractTask<TableCompilationTask.TableCompilationTaskExecutionConfigData, TableCompilationTask.TableCompilationTaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class TableCompilationTaskExecutionConfigData(
        @Description("Row headers for the table")
        var rows: List<String>? = null,
        @Description("Column headers for the table")
        var columns: List<String>? = null,
        @Description("Query template for generating cell content. Use {row} and {column} as placeholders.")
        var cell_query: String? = null,
        @Description("Overall context or description for the table generation")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = TableCompilation.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (rows.isNullOrEmpty()) {
                return "TableCompilationTaskExecutionConfigData: rows list cannot be null or empty"
            }
            if (columns.isNullOrEmpty()) {
                return "TableCompilationTaskExecutionConfigData: columns list cannot be null or empty"
            }
            if (cell_query.isNullOrBlank()) {
                return "TableCompilationTaskExecutionConfigData: cell_query cannot be null or blank"
            }
            return null
        }
    }

    class TableCompilationTaskTypeConfig(
        task_type: String? = TableCompilation.name,
        @Description("Maximum partition size for parallel processing (e.g., 2 means 2x2 partitions)")
        var partition_size: Int = 2,
    ) : TaskTypeConfig(task_type = task_type), ValidatedObject {
        override fun validate(): String? {
            if (partition_size < 1 || partition_size > 10) {
                return "TableCompilationTaskExecutionConfigData: partition_size must be between 1 and 10"
            }
            return null
        }
    }

    init {
        planTask?.validate()?.let { errorMessage ->
            throw ValidatedObject.ValidationError(errorMessage, planTask)
        }
    }

    override fun promptSegment(): String {
        return """
TableCompilation - Generate structured tables with AI-computed cell values
  ** Specify row headers in the 'rows' array
  ** Specify column headers in the 'columns' array
  ** Provide a cell_query template using {row} and {column} placeholders
  ** Cells are computed in partitions for efficiency (configurable partition_size)
  ** Supports output formats: markdown, html, csv
  ** Example use cases:
     - Comparison matrices (features vs products)
     - Data analysis tables (metrics vs categories)
     - Decision matrices (options vs criteria)
     - Cross-reference tables
        """
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()










        task.ui.pool.submit {
            try {
                executionConfig?.validate()?.let { errorMessage ->
                    val e = RuntimeException(errorMessage)
                    task.error(e)
                    log.error("Validation error in TableCompilationTask: $errorMessage")
                    transcript?.write("## Validation Error\n<details><summary>Details</summary>\n$errorMessage\n</details>".toByteArray())
                    task.complete()
                    resultFn("VALIDATION ERROR: $errorMessage")
                    return@submit
                }

                val rows = executionConfig?.rows ?: emptyList()
                val columns = executionConfig?.columns ?: emptyList()
                val cellQuery = executionConfig?.cell_query ?: ""
                val partitionSize = typeConfig?.partition_size ?: 2
                val api = defaultSmart.getChildClient(task)

                log.info("Starting TableCompilationTask: ${rows?.size}x${columns?.size}")
                transcript?.write("## Table Compilation Intent\nGenerating table with ${executionConfig?.rows?.size} rows and ${executionConfig?.columns?.size} columns.\n".toByteArray())
                renderTaskHeader(task)



                task.add(
                    """
                    Generating **${rows.size}x${columns.size}** table using partition size **$partitionSize**.
                    Query Template: `$cellQuery`
                """.trimIndent().renderMarkdown()
                )

                // Initialize the results table
                val cellResults = Array(rows.size) { Array(columns.size) { "" } }

                // Create partitions
                val rowPartitions = rows.indices.chunked(partitionSize)
                val colPartitions = columns.indices.chunked(partitionSize)

                val totalPartitions = rowPartitions.size * colPartitions.size
                var completedPartitions = 0
                val statusBuffer = task.add("Starting processing...")

                // Process each partition
                for (rowPartition in rowPartitions) {
                    for (colPartition in colPartitions) {
                        completedPartitions++
                        statusBuffer?.setLength(0)
                        statusBuffer?.append("Processing partition $completedPartitions/$totalPartitions...")
                        task.update()

                        val partitionCells = mutableListOf<Triple<Int, Int, String>>() // rowIdx, colIdx, query
                        for (rowIdx in rowPartition) {
                            for (colIdx in colPartition) {
                                val query = cellQuery
                                    .replace("{row}", rows[rowIdx])
                                    .replace("{column}", columns[colIdx])
                                partitionCells.add(Triple(rowIdx, colIdx, query))
                            }
                        }

                        // Build batch prompt for this partition
                        val batchPrompt = buildBatchPrompt(
                            partitionCells,
                            rows,
                            columns,
                            executionConfig?.task_description
                        )

                        val cellActor = ChatAgent(
                            prompt = """
        You are a precise data analyst. Generate concise cell values for a table.
        Each cell should contain a brief, relevant response based on the row and column context.
        Keep responses concise (typically 1-3 sentences or a few words/numbers as appropriate).
                            """.trimIndent(),
                            model = api,
                        )

                        try {
                            transcript?.write("<details><summary>Partition $completedPartitions Prompt</summary>\n\n```\n$batchPrompt\n```\n</details>\n".toByteArray())
                            val response = cellActor.answer(listOf(batchPrompt))
                            transcript?.write("<details><summary>Partition $completedPartitions Response</summary>\n\n```\n$response\n```\n</details>\n".toByteArray())
                            val parsedResults = parseBatchResponse(response, partitionCells.size)

                            // Store results
                            partitionCells.forEachIndexed { index, (rowIdx, colIdx, _) ->
                                cellResults[rowIdx][colIdx] = parsedResults.getOrElse(index) { "Error" }
                            }
                        } catch (e: Exception) {
                            log.error("Error processing partition $completedPartitions", e)
                            transcript?.write("### Error in Partition $completedPartitions\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
                            partitionCells.forEach { (rowIdx, colIdx, _) ->
                                cellResults[rowIdx][colIdx] = "Error: ${e.message}"
                            }
                        }
                    }
                }







                statusBuffer?.append("Processing complete.")
                task.update()

                val formattedTable = formatAsHtml(rows, columns, cellResults)
                val csvResult = formatAsCsv(rows, columns, cellResults)
                val jsonResult = formatAsJson(rows, columns, cellResults)

                val csvUrl = task.saveFile("output/table_${System.currentTimeMillis()}.csv", csvResult.toByteArray())
                val jsonUrl = task.saveFile("output/table_${System.currentTimeMillis()}.json", jsonResult.toByteArray())
                transcript?.write(
                    """
                    ## Compilation Results
                    <details>
                    <summary>HTML Table Preview</summary>
                    $formattedTable
                    </details>
                    * CSV Artifact: `$csvUrl`
                    * JSON Artifact: `$jsonUrl`
                """.trimIndent().toByteArray()
                )


                val tabs = TabbedDisplay(task)
                tabs.newTask("Table").apply { add(formattedTable); complete() }
                tabs.newTask("CSV")
                    .apply { add("<pre>$csvResult</pre>"); add("<a href='$csvUrl'>Download CSV</a>"); complete() }
                tabs.newTask("JSON")
                    .apply { add("<pre>$jsonResult</pre>"); add("<a href='$jsonUrl'>Download JSON</a>"); complete() }

                val summary =
                    "## Table Generation Complete\nGenerated ${rows.size}x${columns.size} table. Artifacts: [CSV]($csvUrl), [JSON]($jsonUrl)"
                if (orchestrationConfig.autoFix) {
                    task.complete()
                    resultFn(summary)
                } else {
                    task.add(summary.renderMarkdown() + acceptButtonFooter(task.ui) {
                        task.complete()
                        resultFn(summary)
                    })
                }
            } catch (e: Exception) {
                task.error(e)
                log.error("TableCompilationTask failed", e)
                transcript?.write("\n## Critical Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
            } finally {
                transcript?.close()
            }
        }
    }

    private fun buildBatchPrompt(
        cells: List<Triple<Int, Int, String>>,
        rows: List<String>,
        columns: List<String>,
        context: String?
    ): String {
        return buildString {
            appendLine("Generate cell values for the following table cells.")
            if (!context.isNullOrBlank()) {
                appendLine("Context: $context")
            }
            appendLine()
            appendLine("For each cell below, provide a concise value. Format your response as:")
            appendLine("CELL_1: [value]")
            appendLine("CELL_2: [value]")
            appendLine("etc.")
            appendLine()
            cells.forEachIndexed { index, (rowIdx, colIdx, query) ->
                appendLine("CELL_${index + 1}:")
                appendLine("  Row: ${rows[rowIdx]}")
                appendLine("  Column: ${columns[colIdx]}")
                appendLine("  Query: $query")
                appendLine()
            }
        }
    }

    private fun parseBatchResponse(response: String, expectedCount: Int): List<String> {
        val results = mutableListOf<String>()
        val cellPattern = """CELL_(\d+):\s*(.+?)(?=CELL_\d+:|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = cellPattern.findAll(response)

        val matchMap = matches.associate { match ->
            match.groupValues[1].toInt() to match.groupValues[2].trim()
        }

        for (i in 1..expectedCount) {
            results.add(matchMap[i] ?: "N/A")
        }

        // If parsing failed, try line-by-line fallback
        if (results.all { it == "N/A" }) {
            val lines = response.lines().filter { it.isNotBlank() }
            return lines.take(expectedCount).map { it.trim() }
        }

        return results
    }


    private fun formatAsHtml(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
        return buildString {
            appendLine("<table border=\"1\" cellpadding=\"5\" cellspacing=\"0\">")
            appendLine("  <thead>")
            appendLine("    <tr>")
            appendLine("      <th></th>")
            columns.forEach { col -> appendLine("      <th>$col</th>") }
            appendLine("    </tr>")
            appendLine("  </thead>")
            appendLine("  <tbody>")
            rows.forEachIndexed { rowIdx, rowHeader ->
                appendLine("    <tr>")
                appendLine("      <th>$rowHeader</th>")
                columns.indices.forEach { colIdx ->
                    appendLine("      <td>${cells[rowIdx][colIdx]}</td>")
                }
                appendLine("    </tr>")
            }
            appendLine("  </tbody>")
            appendLine("</table>")
        }
    }

    private fun formatAsCsv(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
        return buildString {
            // Header row
            append(",")
            appendLine(columns.joinToString(",") { "\"$it\"" })

            // Data rows
            rows.forEachIndexed { rowIdx, rowHeader ->
                append("\"$rowHeader\",")
                appendLine(columns.indices.joinToString(",") { colIdx ->
                    "\"${cells[rowIdx][colIdx].replace("\"", "\"\"")}\""
                })
            }
        }
    }


    private fun formatAsJson(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
        val tableData = rows.mapIndexed { rowIdx, rowHeader ->
            val rowMap = mutableMapOf<String, String>()
            rowMap["Row"] = rowHeader
            columns.forEachIndexed { colIdx, colHeader ->
                rowMap[colHeader] = cells[rowIdx][colIdx]
            }
            rowMap
        }
        return tableData.toJson()
    }

    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept Table") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TableCompilationTask::class.java)
        @JvmStatic val TableCompilation = TaskType(
            name = "TableCompilation",
            category = "Reasoning",
            taskClass = TableCompilationTask::class.java,
            executionConfigClass = TableCompilationTaskExecutionConfigData::class.java,
            taskSettingsClass = TableCompilationTaskTypeConfig::class.java,
            description = "Generate structured tables with AI-computed cell values",
            tooltipHtml = """
                        Generates tables by computing each cell value using AI.
                        <ul>
                          <li>Define rows and columns as headers</li>
                          <li>Provide a query template with {row} and {column} placeholders</li>
                          <li>Cells are computed in configurable partitions for efficiency</li>
                          <li>Supports markdown, HTML, and CSV output formats</li>
                          <li>Useful for comparison matrices, analysis tables, decision matrices</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/TemporalReasoningTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TemporalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: TemporalReasoningTaskExecutionConfigData?
) : AbstractTask<TemporalReasoningTask.TemporalReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxOutputLength: Int = 20000

    class TemporalReasoningTaskExecutionConfigData(
        @Description("The subject or system to analyze over time")
        var subject: String? = null,
        @Description("Time range to analyze (e.g., '2023-01-01 to 2024-01-01')")
        var time_range: String? = null,
        @Description("Granularity of analysis: daily, weekly, monthly, quarterly, yearly")
        var granularity: String = "weekly",
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("Whether to identify temporal patterns and cycles")
        var identify_patterns: Boolean = true,
        @Description("Whether to predict future states")
        var predict_future: Boolean = true,
        @Description("How far into the future to predict (e.g., '3 months', '6 weeks')")
        var prediction_horizon: String? = "3 months",
        @Description("Critical events to highlight in the timeline")
        var critical_events: List<String>? = null,
        @Description("Related files containing temporal data (logs, metrics, etc.)")
        var related_files: List<String>? = null,
        @Description("Whether to analyze rate of change and acceleration")
        var analyze_rate_of_change: Boolean = true,
        @Description("Whether to identify critical transition points")
        var identify_transitions: Boolean = true,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = TemporalReasoning.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (subject.isNullOrBlank()) {
                return "subject must not be null or blank"
            }
            if (time_range.isNullOrBlank()) {
                return "time_range must not be null or blank"
            }
            if (granularity.isBlank()) {
                return "granularity must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class TimelineEvent(
        val timestamp: String = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
        val event_type: String = "generic",
        val description: String = "",
        val significance: String = "medium",
        val related_metrics: Map<String, String>? = null
    )

    data class TemporalPattern(
        val pattern_type: String = "recurring",
        val description: String = "",
        val frequency: String = "unknown",
        val confidence: String = "medium",
        val examples: List<String> = emptyList()
    )

    data class TimelineAnalysis(
        val timeline_events: List<TimelineEvent> = emptyList(),
        val patterns: List<TemporalPattern>? = null,
        val rate_of_change_analysis: String? = null,
        val transition_points: List<String>? = null,
        val future_predictions: List<String>? = null
    )

    override fun promptSegment(): String {
        return """
TemporalReasoning - Analyze system evolution and predict future states.
- subject: The system or topic to analyze.
- time_range: Period to examine (e.g., '2023-01-01 to 2024-01-01').
- granularity: daily, weekly, monthly, quarterly, yearly.
- related_files: Logs, metrics, or history files.
- identify_patterns: (Boolean) Find cycles/trends.
- predict_future: (Boolean) Extrapolate trends.
- analyze_rate_of_change: (Boolean) Velocity analysis.
- identify_transitions: (Boolean) Find inflection points.
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {

        val subject = executionConfig?.subject
        if (subject.isNullOrBlank()) {
          log.info("Starting TemporalReasoning task for subject: ${executionConfig?.subject}")

          val errorMsg = "CONFIGURATION ERROR: No subject specified for temporal analysis"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val timeRange = executionConfig?.time_range
        if (timeRange.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No time range specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val api = defaultSmart ?: return
        val ui = task.ui
        val transcript = task.transcript()

      task.ui.pool.submit {
        val startTime = System.currentTimeMillis()
        try {
          log.info("TemporalReasoning started for subject: $subject")
            // Create tabbed display for organized output
            val tabs = TabbedDisplay(task)

            // Overview tab
            val overviewTask = tabs.newTask("Overview")
          val overviewStatus = overviewTask.add(
            """
            |## Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Status:** 🔄 Gathering temporal data...
        """.trimMargin().renderMarkdown()
            )
            transcript?.write(
                """
            |# Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Started:** ${java.time.LocalDateTime.now()}
            |
            |---
            |
            |## Gathering Temporal Data
            |
        """.trimMargin().toByteArray()
            )
            task.update()

            // Gather temporal data from files
            log.debug("Gathering temporal data from ${executionConfig?.related_files?.size ?: 0} file patterns")
            val dataTask = tabs.newTask("Temporal Data")
          val dataLoading = dataTask.add("## Temporal Data Sources\n\n🔄 Loading temporal data...".renderMarkdown())
            task.update()

            val temporalData = gatherTemporalData()
            log.debug("Temporal data gathered: ${temporalData.length} characters")
            dataLoading?.clear()
            transcript?.write(
                """
            |
            |### Data Sources Processed: ${executionConfig?.related_files?.size ?: 0}
            |
            |<details>
            |<summary>Raw Temporal Data</summary>
            |
            |${temporalData}
            |</details>
            |
            |---
            |
        """.trimMargin().toByteArray()
            )
          dataTask.add(
            """
            |## Temporal Data Sources
            |
            |✅ Data gathered successfully
            |
            |**Sources processed:** ${executionConfig?.related_files?.size ?: 0}
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
        """.trimMargin().renderMarkdown()
            )
            dataTask.expandable(
              "Temporal Data Context", """
                |```
                |${temporalData.truncateForDisplay(maxOutputLength)}
                |```
                """.trimMargin()
            )
            task.update()

            // Get prior context
            log.debug("Retrieving prior context from execution state")
            val priorContext = getPriorCode(agent.executionState)

            // Update overview
            overviewStatus?.setLength(0)
          overviewStatus?.append(
            """
            |## Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Status:** 🔄 Constructing timeline...
        """.trimMargin().renderMarkdown()
            )
            task.update()

            // Step 1: Construct timeline
            log.debug("Constructing timeline of events")
            val timelineTask = tabs.newTask("Timeline")
          val timelineLoading =
            timelineTask.add("## Timeline Construction\n\n🔄 Analyzing temporal data and constructing timeline...".renderMarkdown())
            task.update()

            val timelinePrompt = buildTimelinePrompt(subject, timeRange, temporalData, priorContext)
            val timelineAgent = ParsedAgent(
                resultClass = TimelineAnalysis::class.java,
                prompt = timelinePrompt,
                model = api,
                temperature = 0.3,
                parsingChatter = defaultFast,
            )

            val timelineAnalysis = timelineAgent.answer(listOf(timelinePrompt)).obj
            log.debug("Timeline constructed with ${timelineAnalysis.timeline_events.size} events")
            transcript?.write(
                """
            |
            |## Timeline Construction Complete
            |
            |**Events Identified:** ${timelineAnalysis.timeline_events.size}
            |
            |${formatTimeline(timelineAnalysis.timeline_events)}
        """.trimMargin().toByteArray()
            )

            timelineLoading?.clear()
          timelineTask.add(
            """
            |## Timeline of Events
            |
            |✅ Timeline constructed successfully
            |
            |**Events Identified:** ${timelineAnalysis.timeline_events.size}
            |
            |${formatTimeline(timelineAnalysis.timeline_events)}
        """.trimMargin().renderMarkdown()
            )
            task.update()

            // Step 2: Pattern identification (if enabled)
            if (executionConfig.identify_patterns && !timelineAnalysis.patterns.isNullOrEmpty()) {
                log.debug("Analyzing temporal patterns")
                val patternsTask = tabs.newTask("Patterns")
                transcript?.write(
                    """
            |
            |## Temporal Patterns Analysis
            |
            |**Patterns Found:** ${timelineAnalysis.patterns.size}
            |
            |${formatPatterns(timelineAnalysis.patterns)}
        """.trimMargin().toByteArray()
                )
              patternsTask.add(
                """
              |## Temporal Patterns
              |
              |✅ Patterns identified
              |
              |**Patterns Found:** ${timelineAnalysis.patterns.size}
              |
              |${formatPatterns(timelineAnalysis.patterns)}
              """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 3: Rate of change analysis (if enabled)
            if (executionConfig.analyze_rate_of_change && !timelineAnalysis.rate_of_change_analysis.isNullOrBlank()) {
                log.debug("Analyzing rate of change")
                val rateTask = tabs.newTask("Rate of Change")
                transcript?.write(
                    """
            |
            |## Rate of Change Analysis
            |
            |${timelineAnalysis.rate_of_change_analysis}
            |
        """.trimMargin().toByteArray()
                )
              rateTask.add(
                """
              |## Rate of Change Analysis
              |
              |✅ Analysis complete
              |
              |${timelineAnalysis.rate_of_change_analysis}
              """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 4: Transition points (if enabled)
            if (executionConfig.identify_transitions && !timelineAnalysis.transition_points.isNullOrEmpty()) {
                log.debug("Identifying critical transition points")
                val transitionsTask = tabs.newTask("Transition Points")
                transcript?.write(
                    """
            |
            |## Critical Transition Points
            |
            |${formatTransitions(timelineAnalysis.transition_points)}
        """.trimMargin().toByteArray()
                )
              transitionsTask.add(
                """
              |## Critical Transition Points
              |
              |✅ Transitions identified
              |
              |**Transitions Found:** ${timelineAnalysis.transition_points.size}
              |
              |${formatTransitions(timelineAnalysis.transition_points)}
              """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 5: Future predictions (if enabled)
            if (executionConfig.predict_future && !timelineAnalysis.future_predictions.isNullOrEmpty()) {
                log.debug("Generating future predictions")
                val predictionsTask = tabs.newTask("Future Predictions")
                transcript?.write(
                    """
            |
            |## Future State Predictions
            |
            |${formatPredictions(timelineAnalysis.future_predictions)}
        """.trimMargin().toByteArray()
                )
              predictionsTask.add(
                """
              |## Future State Predictions
              |
              |✅ Predictions generated
              |
              |**Prediction Horizon:** ${executionConfig.prediction_horizon}
              |
              |${formatPredictions(timelineAnalysis.future_predictions)}
              """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 6: Generate visualization
            log.debug("Generating timeline visualization")
            val vizTask = tabs.newTask("Visualization")
          val vizLoading = vizTask.add("## Timeline Visualization\n\n🔄 Generating Mermaid diagram...".renderMarkdown())
            task.update()

            val vizPrompt = buildVisualizationPrompt(timelineAnalysis, subject, timeRange)
            val chatAgent = ChatAgent(
                prompt = vizPrompt,
                model = api,
                temperature = 0.3
            )

            val vizResult = chatAgent.answer(listOf(vizPrompt))
            val mermaidCode = extractMermaidCode(vizResult)

            vizLoading?.clear()
            if (mermaidCode.isNotEmpty()) {
                transcript?.write(
                    """
            |
            |## Timeline Visualization
            |
            |```mermaid
            |$mermaidCode
            |```
            |
        """.trimMargin().toByteArray()
                )
              vizTask.add(
                """
              |## Timeline Visualization
              |
              |✅ Visualization generated
              |
              |```mermaid
              |$mermaidCode
              |```
              """.trimMargin().renderMarkdown()
                )
            } else {
              vizTask.add(
                """
              |## Timeline Visualization
              |
              |⚠️ Could not generate visualization
              |
              |The analysis did not produce a valid Mermaid diagram.
              """.trimMargin().renderMarkdown()
                )
            }
            task.update()

            // Generate final summary
            val summary = buildSummary(timelineAnalysis, subject, timeRange)
            transcript?.write(
                """
            |
            |---
            |
            |## Summary
            |
            |$summary
            |
        """.trimMargin().toByteArray()
            )

            // Update overview with completion
            overviewStatus?.setLength(0)
          overviewStatus?.append(
            """
            |## Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Status:** ✅ Analysis complete
            |
            |---
            |
            |### Summary
            |
            |$summary
        """.trimMargin().renderMarkdown()
            )
            task.update()

            val duration = System.currentTimeMillis() - startTime
            val completionMsg = "Temporal analysis completed for '$subject' over $timeRange"
            log.info("$completionMsg (duration: ${duration}ms, events: ${timelineAnalysis.timeline_events.size}, patterns: ${timelineAnalysis.patterns?.size ?: 0})")

            task.safeComplete(completionMsg, log)
            resultFn(summary)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime

          // Triple Log Rule
          task.error(e)
          log.error("TemporalReasoning failed for subject: $subject", e)
          transcript?.write(
            """
                    |## ❌ Error
                    |<details>
                    |<summary>Stack Trace</summary>
                    |
                    |```
                    |${e.stackTraceToString()}
                    |```
                    |</details>
                """.trimMargin().toByteArray()
          )

          task.safeComplete("Analysis failed: ${e.message}", log)
            resultFn("ERROR: Temporal reasoning analysis failed - ${e.message}")
        } finally {
          transcript?.close()
        }
        }
    }


    private fun buildTimelinePrompt(
        subject: String,
        timeRange: String,
        temporalData: String,
        priorContext: String
    ): String {
        val criticalEvents = executionConfig?.critical_events?.joinToString(", ") ?: "any significant events"
        val granularity = executionConfig?.granularity ?: "weekly"

        return """
You are an expert in temporal reasoning and timeline analysis. Your task is to analyze how a system or situation evolves over time.

## Subject to Analyze:
$subject

## Time Range:
$timeRange

## Granularity:
$granularity

## Critical Events to Highlight:
$criticalEvents

## Temporal Data:
$temporalData

## Previous Task Results:
$priorContext

## Analysis Instructions:

1. **Construct Timeline**: Create a chronological timeline of significant events
   - Identify key events and changes
   - Note timestamps and durations
   - Categorize events by type (deployment, incident, change, milestone, etc.)
   - Assess significance of each event

2. **Identify Patterns** (if enabled: ${executionConfig?.identify_patterns}):
   - Look for recurring patterns and cycles
   - Identify seasonal or periodic trends
   - Note correlations between events
   - Assess pattern confidence and frequency

3. **Analyze Rate of Change** (if enabled: ${executionConfig?.analyze_rate_of_change}):
   - Calculate velocity of change over time
   - Identify acceleration or deceleration
   - Note periods of stability vs rapid change
   - Quantify trends where possible

4. **Identify Transition Points** (if enabled: ${executionConfig?.identify_transitions}):
   - Find critical inflection points
   - Identify phase transitions
   - Note sudden changes or disruptions
   - Explain what triggered each transition

5. **Predict Future States** (if enabled: ${executionConfig?.predict_future}):
   - Extrapolate trends into the future
   - Predict likely outcomes based on historical patterns
   - Identify potential risks and opportunities
   - Provide confidence levels for predictions
   - Prediction horizon: ${executionConfig?.prediction_horizon}

## Output Format:
Provide a structured JSON response with:
- timeline_events: Array of events with timestamp, type, description, significance, and related metrics
- patterns: Array of identified patterns (if enabled)
- rate_of_change_analysis: Detailed analysis of change velocity (if enabled)
- transition_points: Array of critical transition points (if enabled)
- future_predictions: Array of predictions (if enabled)

Generate the temporal analysis now:
        """.trimIndent()
    }

    private fun buildVisualizationPrompt(
        analysis: TimelineAnalysis,
        subject: String,
        timeRange: String
    ): String {
        return """
Based on the temporal analysis for "$subject" over $timeRange, create a Mermaid timeline diagram.

Timeline Events:
${analysis.timeline_events.joinToString("\n") { "- ${it.timestamp}: ${it.description}" }}

${if (!analysis.patterns.isNullOrEmpty()) "Patterns: ${analysis.patterns.size} identified" else ""}
${if (!analysis.transition_points.isNullOrEmpty()) "Transition Points: ${analysis.transition_points.size} identified" else ""}

Use the following format:
- Use `timeline` for the diagram type
- Show events chronologically
- Use different sections for different time periods
- Highlight critical events and transition points
- Keep labels concise but descriptive

Generate the Mermaid timeline diagram now:
            """.trimIndent()
    }

    private fun gatherTemporalData(): String {
        val inputFiles = (executionConfig?.input_files ?: emptyList()) +
                (executionConfig?.related_files ?: emptyList())

        if (inputFiles.isEmpty()) {
            return "No specific temporal data files provided."
        }

        val maxFileSize = 2000

        return inputFiles.flatMap { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            root.toFile().walkTopDown()
                .filter { file ->
                    file.isFile && matcher.matches(root.relativize(file.toPath()))
                }
                .map { file ->
                    val relativePath = root.relativize(file.toPath())
                    try {
                        val content = file.readText()
                        "### $relativePath\n```\n${content.truncateForDisplay(maxFileSize)}\n```"
                    } catch (e: Exception) {
                        log.warn("Error reading temporal data file: $relativePath", e)
                        "### $relativePath\n(Error reading file: ${e.message})"
                    }
                }
                .toList()
        }.joinToString("\n\n")
    }

    private fun formatTimeline(events: List<TimelineEvent>): String {
        return buildString {
            appendLine()
            events.forEach { event ->
                appendLine("### ${event.timestamp} - ${event.event_type}")
                appendLine()
                appendLine("**Description:** ${event.description}")
                appendLine()
                appendLine("**Significance:** ${event.significance}")
                if (!event.related_metrics.isNullOrEmpty()) {
                    appendLine()
                    appendLine("**Metrics:**")
                    event.related_metrics.forEach { (key, value) ->
                        appendLine("- $key: $value")
                    }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun formatPatterns(patterns: List<TemporalPattern>): String {
        return buildString {
            appendLine()
            patterns.forEach { pattern ->
                appendLine("### ${pattern.pattern_type}")
                appendLine()
                appendLine("**Description:** ${pattern.description}")
                appendLine()
                appendLine("**Frequency:** ${pattern.frequency}")
                appendLine()
                appendLine("**Confidence:** ${pattern.confidence}")
                appendLine()
                if (pattern.examples.isNotEmpty()) {
                    appendLine("**Examples:**")
                    pattern.examples.forEach { example ->
                        appendLine("- $example")
                    }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun formatTransitions(transitions: List<String>): String {
        return buildString {
            appendLine()
            transitions.forEachIndexed { index, transition ->
                appendLine("### Transition ${index + 1}")
                appendLine()
                appendLine(transition)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun formatPredictions(predictions: List<String>): String {
        return buildString {
            appendLine()
            predictions.forEachIndexed { index, prediction ->
                appendLine("### Prediction ${index + 1}")
                appendLine()
                appendLine(prediction)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun buildSummary(analysis: TimelineAnalysis, subject: String, timeRange: String): String {
        return buildString {
            appendLine("**Subject:** $subject")
            appendLine()
            appendLine("**Time Range:** $timeRange")
            appendLine()
            appendLine("**Events Analyzed:** ${analysis.timeline_events.size}")
            appendLine()
            if (!analysis.patterns.isNullOrEmpty()) {
                appendLine("**Patterns Identified:** ${analysis.patterns.size}")
                appendLine()
            }
            if (!analysis.transition_points.isNullOrEmpty()) {
                appendLine("**Critical Transitions:** ${analysis.transition_points.size}")
                appendLine()
            }
            if (!analysis.future_predictions.isNullOrEmpty()) {
                appendLine("**Future Predictions:** ${analysis.future_predictions.size}")
                appendLine()
            }
            appendLine()
            appendLine("The temporal analysis reveals how $subject evolved over $timeRange, ")
            appendLine("identifying key events, patterns, and trends that shaped its development.")
        }
    }

    private fun extractMermaidCode(response: String): String {
        val mermaidBlockRegex = "```mermaid\\s*([\\s\\S]*?)```".toRegex()
        val match = mermaidBlockRegex.find(response)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TemporalReasoningTask::class.java)
        @JvmStatic val TemporalReasoning = TaskType(
          name = "TemporalReasoning",
          category = "Reasoning",
          taskClass = TemporalReasoningTask::class.java,
          executionConfigClass = TemporalReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Analyze how systems evolve over time and predict future states",
          tooltipHtml = """
                        Performs temporal reasoning and timeline analysis to understand system evolution.
                        <ul>
                          <li>Constructs chronological timelines of events and changes</li>
                          <li>Identifies temporal patterns, cycles, and trends</li>
                          <li>Analyzes rate of change and acceleration</li>
                          <li>Identifies critical transition points and inflection points</li>
                          <li>Predicts future states based on historical trends</li>
                          <li>Useful for technical debt analysis, performance degradation, and system evolution</li>
                        </ul>
                      """,
        )
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the reasoning tools package, synthesized from the provided task implementations.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/README.md
```markdown
# Reasoning Tools

This package contains a comprehensive suite of advanced cognitive and logical reasoning tools. These tools leverage Large Language Models (LLMs) to perform structured analysis, creative problem-solving, and rigorous logical evaluations across various domains.

## Overview

The reasoning tools are designed to handle complex tasks that require more than simple text generation. They implement established mental models and analytical frameworks—such as Bayesian inference, Socratic questioning, and Systems Thinking—to provide deep insights and verifiable conclusions.

## Available Tools

### 🧠 Logical & Analytical Reasoning
*   **[Abductive Reasoning](AbductiveReasoningTask.kt)**: Generates and evaluates the "best explanation" for a set of observations. Ideal for root cause analysis and bug investigation.
*   **[Causal Inference](CausalInferenceTask.kt)**: Distinguishes between correlation and causation, identifies root causes, and builds causal graphs to explain system behavior.
*   **[Chain of Thought](ChainOfThoughtTask.kt)**: Breaks down complex problems into explicit, verifiable reasoning steps with built-in validation and backtracking.
*   **[Decision Tree](DecisionTreeTask.kt)**: Constructs LLM-driven symbolic decision trees for classification, providing interpretable rules and executable code.
*   **[Decomposition & Synthesis](DecompositionSynthesisTask.kt)**: Implements a divide-and-conquer approach, breaking complex problems into subproblems and synthesizing a coherent final solution.
*   **[Mathematical Reasoning](MathematicalReasoningTask.kt)**: Solves mathematical problems through rigorous step-by-step logic, providing formal proofs in LaTeX/MathJax format.
*   **[Probabilistic Reasoning](ProbabilisticReasoningTask.kt)**: Performs Bayesian analysis to reason under uncertainty, updating beliefs based on evidence and quantifying risks.
*   **[Temporal Reasoning](TemporalReasoningTask.kt)**: Analyzes how systems evolve over time, identifying patterns, cycles, and predicting future states based on historical trends.

### 💡 Creative & Lateral Thinking
*   **[Analogical Reasoning](AnalogicalReasoningTask.kt)**: Solves problems by drawing structural analogies from distant domains (e.g., applying biological concepts to software architecture).
*   **[Brainstorming](BrainstormingTask.kt)**: Systematically generates diverse solution options and performs independent pros/cons/feasibility analysis for each.
*   **[Lateral Thinking](LateralThinkingTask.kt)**: Uses techniques like reversal, random stimulus, and provocation to break conventional thinking patterns and find innovative solutions.
*   **[Socratic Dialogue](SocraticDialogueTask.kt)**: Explores ideas through deep questioning, challenging assumptions, and identifying contradictions through a simulated dialogue.

### 🏗️ Structural & Systems Analysis
*   **[Abstraction Ladder](AbstractionLadderTask.kt)**: Traverses levels of abstraction (up for generalizations, down for implementations) to discover design patterns and refactoring opportunities.
*   **[Finite State Machine](FiniteStateMachineTask.kt)**: Models systems as states and transitions, identifying edge cases, error states, and generating test scenarios.
*   **[Structural Invariant Analysis](StructuralInvariantAnalysisTask.kt)**: Distills objects down to their immutable properties and symmetries by stripping away domain-specific context.
*   **[Systems Thinking](SystemsThinkingTask.kt)**: Analyzes complex systems through feedback loops, delays, and accumulations to identify high-leverage intervention points.

### 🛡️ Adversarial & Evaluative Reasoning
*   **[Adversarial Reasoning](AdversarialReasoningTask.kt)**: Performs red team analysis to identify security vulnerabilities, logic flaws, and failure modes in designs or arguments.
*   **[Counterfactual Analysis](CounterfactualAnalysisTask.kt)**: Explores "what-if" scenarios to understand the impact of different decisions and causal relationships.
*   **[Meta-Cognitive Reflection](MetaCognitiveReflectionTask.kt)**: "Thinking about thinking"—critiques the reasoning process of other tasks to identify biases, assumptions, and gaps.

### 🧪 Specialized Optimization & Mapping
*   **[Constraint Relaxation](ConstraintRelaxationTask.kt)**: Solves over-constrained problems by progressively relaxing and reintroducing constraints based on priority.
*   **[Constraint Satisfaction](ConstraintSatisfactionTask.kt)**: Balances hard requirements and weighted soft preferences to find optimal solutions in complex search spaces.
*   **[Functorial Mapping](FunctorialMappingTask.kt)**: Uses Category Theory concepts to translate problems from one domain to another where superior tools may exist.
*   **[Genetic Optimization](GeneticOptimizationTask.kt)**: Iteratively evolves and perfects text (like prompts or documentation) using mutation and crossover strategies.
*   **[Isomorphism Discovery](IsomorphismDiscoveryTask.kt)**: Searches for and validates structural mappings between two distinct domains to find underlying commonalities.
*   **[Neural Network Layer](NeuralNetworkLayerTask.kt)**: Provides a comprehensive framework for designing, deriving gradients for, and analyzing the stability of neural network layers.
*   **[Table Compilation](TableCompilationTask.kt)**: Generates structured comparison matrices and data tables by computing cell values in parallel partitions.

## Usage

These tools are typically invoked by the `TaskOrchestrator` as part of a larger plan. Each tool requires a specific configuration (e.g., `AbductiveReasoningTaskExecutionConfigData`) that defines the inputs, constraints, and goals for the reasoning process.

Most tools produce:
1.  **Interactive Tabs**: Organized views of the reasoning process (e.g., "Timeline", "Analysis", "Synthesis").
2.  **Detailed Transcripts**: Markdown reports containing the full chain of thought and evidence.
3.  **Structured Results**: Final conclusions or artifacts (like code or diagrams) that can be used by subsequent tasks.
```

### Summary of Changes
- Created a comprehensive `README.md` for the `reasoning` package.
- Categorized all 26 reasoning tasks into logical groups:
    - Logical & Analytical Reasoning
    - Creative & Lateral Thinking
    - Structural & Systems Analysis
    - Adversarial & Evaluative Reasoning
    - Specialized Optimization & Mapping
- Extracted key functionality and use cases for each tool from the source code's `TaskType` definitions and `promptSegment` methods.
- Added an overview section explaining the general philosophy of the reasoning tools (leveraging mental models and structured frameworks).
- Included a usage section describing the common outputs (tabs, transcripts, results).
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/README.md</a> Updated
