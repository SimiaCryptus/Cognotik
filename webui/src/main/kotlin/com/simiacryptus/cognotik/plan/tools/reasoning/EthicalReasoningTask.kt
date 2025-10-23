package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EthicalReasoningTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: EthicalReasoningTaskExecutionConfigData?
) : AbstractTask<EthicalReasoningTask.EthicalReasoningTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class EthicalReasoningTaskExecutionConfigData(
    @Description("A clear description of the ethical problem or decision to be made.")
    val ethical_dilemma: String? = null,
    @Description("A list of individuals, groups, or entities affected by the decision.")
    val stakeholders: List<String>? = null,
    @Description("The ethical frameworks to apply. Options: utilitarianism, deontology, virtue_ethics, care_ethics, rights_based.")
    val ethical_frameworks: List<String>? = listOf("utilitarianism", "deontology", "virtue_ethics"),
    @Description("Optional background information or constraints relevant to the dilemma.")
    val context: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = EthicalReasoning.name,
    task_description = "Analyze ethical dilemma: ${ethical_dilemma?.take(100)}",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (ethical_dilemma.isNullOrBlank()) {
        return "ethical_dilemma must not be null or blank"
      }
      if (stakeholders.isNullOrEmpty()) {
        return "stakeholders must not be null or empty"
      }
      ethical_frameworks?.forEach { framework ->
        if (framework.isBlank()){
          return "Invalid ethical_frameworks entry: must not be blank"
        }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
EthicalReasoning - Analyze a dilemma through multiple ethical frameworks
  ** Specify the ethical dilemma and stakeholders
  ** Choose from frameworks: utilitarianism, deontology, virtue_ethics, care_ethics, rights_based
  ** Provides analysis from each framework's perspective
  ** Synthesizes findings into a balanced recommendation
  ** Highlights ethical trade-offs and points of conflict
  ** Useful for:
     - AI safety and alignment
     - Product and policy ethics
     - Corporate governance
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
    log.info("Starting EthicalReasoning task for dilemma: ${executionConfig?.ethical_dilemma?.truncateForDisplay(200)}")

    val dilemma = executionConfig?.ethical_dilemma
    if (dilemma.isNullOrBlank()) {
      // Validate configuration
      executionConfig?.validate()?.let { validationError ->
        val errorMsg = "VALIDATION ERROR: $validationError"
        log.error(errorMsg)
        task.safeComplete(errorMsg, log)
        resultFn(errorMsg)
        return
      }
      val dilemma = executionConfig?.ethical_dilemma
      if (dilemma.isNullOrBlank()) {
        val errorMsg = "CONFIGURATION ERROR: No ethical dilemma specified"
        log.error(errorMsg)
        task.safeComplete(errorMsg, log)
        resultFn(errorMsg)
        return
      }

      val stakeholders = executionConfig.stakeholders
      if (stakeholders.isNullOrEmpty()) {
        val errorMsg = "CONFIGURATION ERROR: No stakeholders specified"
        log.error(errorMsg)
        task.safeComplete(errorMsg, log)
        resultFn(errorMsg)
        return
      }

      val frameworks = executionConfig.ethical_frameworks ?: listOf("utilitarianism", "deontology", "virtue_ethics")
      val context = executionConfig.context ?: ""

      val ui = task.ui
      val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return
      val tabs = TabbedDisplay(task)
      val overviewTask = task.ui.newTask(false)
      tabs["Overview"] = overviewTask.placeholder

      try {
        var overviewTaskStatus = overviewTask.add(
          MarkdownUtil.renderMarkdown(
            """
            |## Ethical Reasoning Analysis
            |
            |**Dilemma:** ${dilemma.truncateForDisplay()}
            |
            |**Stakeholders:** ${stakeholders.joinToString(", ")}
            |
            |**Frameworks:** ${frameworks.joinToString(", ")}
            |
            |**Status:** 🔄 Initializing analysis...
        """.trimMargin(), ui = ui
          )
        )
        task.update()

        val priorContext = getPriorCode(agent.executionState)
        val fullContext = buildString {
          if (priorContext.isNotBlank()) {
            append("## Context from Previous Tasks\n\n")
            append(priorContext)
            append("\n\n")
          }
          if (context.isNotBlank()) {
            append("## Additional Context\n\n")
            append(context)
            append("\n\n")
          }
        }

        if (fullContext.isNotBlank()) {
          val contextTask = task.ui.newTask(false)
          tabs["Context"] = contextTask.placeholder
          contextTask.add(MarkdownUtil.renderMarkdown(fullContext, ui = ui))
          task.update()
        }

        // Step 1: Dilemma & Stakeholder Analysis
        log.debug("Analyzing dilemma and stakeholders")
        val analysisTask = task.ui.newTask(false)
        tabs["Dilemma Analysis"] = analysisTask.placeholder
        val analysisLoading = analysisTask.add(
          MarkdownUtil.renderMarkdown("## Dilemma & Stakeholder Analysis\n\n🔄 Analyzing...", ui = ui)
        )
        task.update()

        val analysisPrompt = """
You are an expert in ethical analysis. Your first task is to deconstruct the provided ethical dilemma and analyze the stakeholders.

**Ethical Dilemma:**
$dilemma

**Stakeholders:**
${stakeholders.joinToString(", ")}

$fullContext

**Instructions:**
1.  **Deconstruct the Dilemma:** Clearly articulate the core conflict, the decision to be made, and the key ethical questions at play.
2.  **Analyze Stakeholders:** For each stakeholder, identify their interests, rights, and how they might be positively or negatively impacted by different outcomes.

Provide a detailed analysis.
      """.trimIndent()

        val chatAgent = ChatAgent(
          prompt = "",
          model = api
        )
        val dilemmaAnalysis = chatAgent.answer(listOf(analysisPrompt))
        log.info("Dilemma analysis completed. Length: ${dilemmaAnalysis.length} characters")

        analysisLoading?.clear()
        analysisTask.add(MarkdownUtil.renderMarkdown("## Dilemma & Stakeholder Analysis\n\n$dilemmaAnalysis", ui = ui))
        task.update()

        overviewTaskStatus?.clear()
        overviewTaskStatus = overviewTask.add(
          MarkdownUtil.renderMarkdown(
            """
            |## Ethical Reasoning Analysis
            |
            |**Dilemma:** ${dilemma.truncateForDisplay()}
            |
            |**Status:** 🔄 Applying ethical frameworks...
        """.trimMargin(), ui = ui
          )
        )
        task.update()

        // Step 2: Framework Application
        val frameworkAnalyses = mutableMapOf<String, String>()
        for (framework in frameworks) {
          val capitalizedFramework = framework.replaceFirstChar { it.titlecase() }
          log.debug("Applying framework: $framework")
          val frameworkTask = task.ui.newTask(false)
          tabs["Framework: $capitalizedFramework"] = frameworkTask.placeholder
          val frameworkLoading = frameworkTask.add(
            MarkdownUtil.renderMarkdown("## $capitalizedFramework Analysis\n\n🔄 Applying framework...", ui = ui)
          )
          task.update()

          val frameworkPrompt = """
You are an expert specializing in the **$capitalizedFramework** ethical framework.
Analyze the following dilemma from this specific perspective.

**Ethical Dilemma:**
$dilemma

**Stakeholders:**
${stakeholders.joinToString(", ")}

**Dilemma & Stakeholder Analysis:**
$dilemmaAnalysis

$fullContext

**Instructions:**
1.  **Explain Core Principles:** Briefly explain the core principles of $capitalizedFramework ethics.
2.  **Apply to Dilemma:** Apply these principles to the dilemma, considering the impact on all stakeholders.
3.  **Derive Recommendation:** Based on your analysis, what is the recommended course of action from a purely $capitalizedFramework perspective? Justify your recommendation.

Provide a clear and structured analysis.
        """.trimIndent()

          val frameworkAnalysis = chatAgent.answer(listOf<String>(frameworkPrompt))
          frameworkAnalyses[framework] = frameworkAnalysis
          log.info("$framework analysis completed. Length: ${frameworkAnalysis.length} characters")

          frameworkLoading?.clear()
          frameworkTask.add(MarkdownUtil.renderMarkdown("## $capitalizedFramework Analysis\n\n$frameworkAnalysis", ui = ui))
          task.update()
        }

        // Step 3: Synthesis and Recommendation
        log.debug("Synthesizing framework analyses")
        val synthesisTask = task.ui.newTask(false)
        tabs["Synthesis"] = synthesisTask.placeholder
        val synthesisLoading = synthesisTask.add(
          MarkdownUtil.renderMarkdown("## Synthesis & Recommendation\n\n🔄 Synthesizing results...", ui = ui)
        )
        task.update()

        val synthesisPrompt = """
You are a master ethicist. Your task is to synthesize the analyses from multiple ethical frameworks to provide a final, balanced recommendation.

**Ethical Dilemma:**
$dilemma

**Analyses from Different Frameworks:**
${frameworkAnalyses.entries.joinToString("\n\n") { "### ${it.key.replaceFirstChar { char -> char.titlecase() }} Analysis\n${it.value}" }}

**Instructions:**
1.  **Compare and Contrast:** Identify areas of agreement and conflict between the recommendations from each framework.
2.  **Discuss Trade-offs:** Clearly articulate the ethical trade-offs involved in choosing one course of action over another.
3.  **Formulate Final Recommendation:** Provide a final, comprehensive recommendation. It should be practical and acknowledge the complexities and conflicting values. Justify why this recommendation is the most ethically sound approach, considering all perspectives.

Provide a detailed synthesis and a clear final recommendation.
      """.trimIndent()

        val synthesis = chatAgent.answer(listOf<String>(synthesisPrompt))
        log.info("Synthesis completed. Length: ${synthesis.length} characters")

        synthesisLoading?.clear()
        synthesisTask.add(MarkdownUtil.renderMarkdown("## Synthesis & Recommendation\n\n$synthesis", ui = ui))
        task.update()

        // Final result and overview update
        val finalRecommendationSummary = chatAgent.answer(
          listOf<String>(
            """
Based on the following synthesis, provide a very concise summary (2-3 sentences) of the final recommendation and the key trade-off.

**Synthesis:**
$synthesis
        """.trimIndent()
          )
        )

        overviewTaskStatus?.clear()
        overviewTask.add(
          MarkdownUtil.renderMarkdown(
            """
            |## Ethical Reasoning Analysis
            |
            |**Dilemma:** ${dilemma.truncateForDisplay()}
            |
            |**Status:** ✅ Analysis complete
            |
            |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            |
            |---
            |
            |### Final Recommendation Summary
            |$finalRecommendationSummary
        """.trimMargin(), ui = ui
          )
        )
        task.update()

        val finalResult = buildString {
          appendLine("# Ethical Reasoning Summary")
          appendLine()
          appendLine("**Dilemma:** ${dilemma.truncateForDisplay()}")
          appendLine()
          appendLine("**Recommendation:** $finalRecommendationSummary")
          appendLine()
          appendLine("---")
          appendLine("Detailed analysis is available in the UI tabs.")
        }

        val duration = System.currentTimeMillis() - startTime
        val summary = "Ethical reasoning analysis completed for dilemma: ${dilemma.truncateForDisplay(200)}"
        log.info("$summary (duration: ${duration}ms)")

        task.safeComplete(summary, log)
        resultFn(finalResult)

      } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        log.error("EthicalReasoning task failed after ${duration}ms for dilemma: ${dilemma.truncateForDisplay(200)}", e)
        overviewTask.add(
          MarkdownUtil.renderMarkdown(
            """
            |## Ethical Reasoning Analysis
            |
            |**Status:** ❌ Analysis Failed
            |
            |**Error:** ${e.message}
            """.trimMargin(), ui = ui
          )
        )
        task.update()
        task.error(e)
        task.safeComplete("Analysis failed: ${e.message}", log)
        resultFn("ERROR: Ethical reasoning analysis failed - ${e.message}")
      }
    }
  }
  companion object {
    private val log: Logger = LoggerFactory.getLogger(EthicalReasoningTask::class.java)
    val EthicalReasoning = TaskType(
      "EthicalReasoning",
      EthicalReasoningTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Analyze a dilemma through multiple ethical frameworks to guide decision-making.",
      """
              Provides a structured analysis of a complex ethical problem or decision.
              <ul>
                <li>Evaluates a dilemma from the perspectives of several established ethical frameworks (e.g., Utilitarianism, Deontology, Virtue Ethics).</li>
                <li>For each framework, it assesses the situation, applies the framework's core principles, and determines a recommended course of action.</li>
                <li>Synthesizes these findings to provide a comprehensive recommendation, highlighting points of convergence, divergence, and the ethical trade-offs involved.</li>
                <li>Useful for AI safety, product development, policy making, and corporate governance.</li>
              </ul>
            """
    )
  }
}