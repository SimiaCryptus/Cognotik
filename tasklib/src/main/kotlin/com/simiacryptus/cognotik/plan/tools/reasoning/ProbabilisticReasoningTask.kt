package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
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


    val executionConfig = this.executionConfig ?: run {
      val errorMsg = "CONFIGURATION ERROR: Execution config is null"
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
    val transcript = task.newUserFileStream(transcriptFile())
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
        transcript?.write(
          """
                ## Bayesian Update
                <details>
                <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                $updateResult
                </details>
            """.trimIndent().toByteArray()
        )



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
          transcript?.write(
            """
                    ## Expected Value Analysis
                    <details>
                    <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                    $evResult
                    </details>
                """.trimIndent().toByteArray()
          )



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
          transcript?.write(
            """
                    ## Key Uncertainties
                    <details>
                    <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                    $uncertaintyResult
                    </details>
                """.trimIndent().toByteArray()
          )



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
          transcript?.write(
            """
                    ## Suggested Experiments
                    <details>
                    <summary>Analysis Details (${stepTime / 1000.0}s)</summary>
                    $experimentResult
                    </details>
                """.trimIndent().toByteArray()
          )



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
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())

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

    @JvmStatic
    val ProbabilisticReasoning = TaskType(
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