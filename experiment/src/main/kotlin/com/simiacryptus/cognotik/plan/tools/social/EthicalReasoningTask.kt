package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.ISessionTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class EthicalReasoningTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: EthicalReasoningTaskExecutionConfigData?
) : AbstractTask<EthicalReasoningTask.EthicalReasoningTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  companion object {
      private val log: Logger = LoggerFactory.getLogger(EthicalReasoningTask::class.java)

    @JvmStatic
    val EthicalReasoning = TaskType(
      name = "EthicalReasoning",
      category = "Reasoning",
      taskClass = EthicalReasoningTask::class.java,
      executionConfigClass = EthicalReasoningTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Analyze a dilemma through multiple ethical frameworks to guide decision-making.",
      tooltipHtml = """
                        Provides a structured analysis of a complex ethical problem or decision.
                        <ul>
                          <li>Evaluates a dilemma from the perspectives of several established ethical frameworks (e.g., Utilitarianism, Deontology, Virtue Ethics).</li>
                          <li>For each framework, it assesses the situation, applies the framework's core principles, and determines a recommended course of action.</li>
                          <li>Synthesizes these findings to provide a comprehensive recommendation, highlighting points of convergence, divergence, and the ethical trade-offs involved.</li>
                          <li>Useful for AI safety, product development, policy making, and corporate governance.</li>
                          <li>Generates a downloadable transcript in markdown, HTML, and PDF formats.</li>
                        </ul>
                      """,
    )
  }

  class EthicalReasoningTaskExecutionConfigData(
    @Description("A clear description of the ethical problem or decision to be made.")
    var ethical_dilemma: String? = null,
    @Description("Optional input files (supports glob patterns) to provide context for the ethical analysis")
    var related_files: List<String>? = null,
    @Description("A list of individuals, groups, or entities affected by the decision.")
    var stakeholders: List<String>? = null,
    @Description("The ethical frameworks to apply. Options: utilitarianism, deontology, virtue_ethics, care_ethics, rights_based.")
    var ethical_frameworks: List<String>? = listOf("utilitarianism", "deontology", "virtue_ethics"),
    @Description("Optional background information or constraints relevant to the dilemma.")
    var context: String? = null,
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
        if (framework.isBlank()) {
          return "Invalid ethical_frameworks entry: must not be blank"
        }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
 EthicalReasoning - Analyze a dilemma through multiple ethical frameworks
  ** Optionally specify input files (supports glob patterns) to provide context
  ** Files will be read and included in the analysis
  ** Specify the ethical dilemma and stakeholders
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
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    log.info("EthicalReasoning task started. Dilemma: ${executionConfig?.ethical_dilemma?.truncateForDisplay(100)}")
    // Validate configuration first
    executionConfig?.validate()?.let { validationError ->
      val errorMsg = "VALIDATION ERROR: $validationError"
      log.warn("EthicalReasoning validation failed: $validationError")
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }


    val dilemma = executionConfig?.ethical_dilemma
    if (dilemma.isNullOrBlank()) {
      val errorMsg = "ERROR: No ethical dilemma specified"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }
    val stakeholders = executionConfig?.stakeholders
    if (stakeholders.isNullOrEmpty()) {
      val errorMsg = "ERROR: No stakeholders specified"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }
    val frameworks = executionConfig?.ethical_frameworks ?: listOf("utilitarianism", "deontology", "virtue_ethics")
    val context = executionConfig?.context ?: ""

    val api = defaultSmart ?: return
    val transcript = task.newUserFileStream(transcriptFile())
    val tabs = TabbedDisplay(task)

    task.pool.submit {
      val startTime = System.currentTimeMillis()
      val overviewTask = tabs.newTask("Overview")
      try {
        transcript?.write("# Ethical Reasoning Analysis\n\n".toByteArray())
        transcript?.write(
          "**Started:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n\n".toByteArray()
        )
        transcript?.write("**Dilemma:** $dilemma\n\n".toByteArray())
        transcript?.write("**Stakeholders:** ${stakeholders.joinToString(", ")}\n\n".toByteArray())
        transcript?.write("**Frameworks:** ${frameworks.joinToString(", ")}\n\n".toByteArray())
        transcript?.write("---\n\n".toByteArray())

        var overviewTaskStatus = overviewTask.add(
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
        """.trimMargin().renderMarkdown()
        )

        val priorContext = getPriorCode(agent.executionState)
        val fileContext = executionConfig?.related_files?.let {
          getInputFileContent(it, root)
        } ?: ""

        val fullContext = buildString {
          if (fileContext.isNotBlank()) {
            append("## File Context\n\n")
            append(fileContext)
            append("\n\n")
          }
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
          val contextTask = tabs.newTask("Context")
          contextTask.add("## Analysis Context\n\n$fullContext".renderMarkdown())
          contextTask.complete()
        }

        // Step 1: Dilemma & Stakeholder Analysis
        val analysisTask = tabs.newTask("Dilemma Analysis")
        val analysisLoading = analysisTask.add(
          "## Dilemma & Stakeholder Analysis\n\n🔄 Analyzing...".renderMarkdown()
        )

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
        transcript?.write("## Dilemma & Stakeholder Analysis\n\n".toByteArray())
        transcript?.write("${dilemmaAnalysis}\n\n".toByteArray())
        transcript?.write("---\n\n".toByteArray())


        analysisLoading?.clear()
        analysisTask.add("## Dilemma & Stakeholder Analysis\n\n$dilemmaAnalysis".renderMarkdown())
        analysisTask.complete()
        task.update()

        overviewTaskStatus?.clear()
        overviewTaskStatus = overviewTask.add(
          """
            |## Ethical Reasoning Analysis
            |
            |**Dilemma:** ${dilemma.truncateForDisplay()}
            |
            |**Status:** 🔄 Applying ethical frameworks...
        """.trimMargin().renderMarkdown()
        )

        // Step 2: Framework Application
        val frameworkAnalyses = mutableMapOf<String, String>()
        for (framework in frameworks) {
          val capitalizedFramework = framework.replaceFirstChar { it.titlecase() }
          val frameworkTask = tabs.newTask("Framework: $capitalizedFramework")
          val frameworkLoading = frameworkTask.add(
            "## $capitalizedFramework Analysis\n\n🔄 Applying framework...".renderMarkdown()
          )

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
          transcript?.write("## $capitalizedFramework Analysis\n\n".toByteArray())
          transcript?.write("${frameworkAnalysis}\n\n".toByteArray())
          transcript?.write("---\n\n".toByteArray())


          frameworkLoading?.clear()
          frameworkTask.add("## $capitalizedFramework Analysis\n\n$frameworkAnalysis".renderMarkdown())
          frameworkTask.complete()
          task.update()
        }

        // Step 3: Synthesis and Recommendation
        val synthesisTask = tabs.newTask("Synthesis")
        val synthesisLoading = synthesisTask.add(
          "## Synthesis & Recommendation\n\n🔄 Synthesizing results...".renderMarkdown()
        )

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
        transcript?.write("## Synthesis & Recommendation\n\n".toByteArray())
        transcript?.write("${synthesis}\n\n".toByteArray())
        transcript?.write("---\n\n".toByteArray())


        synthesisLoading?.clear()
        synthesisTask.add("## Synthesis & Recommendation\n\n$synthesis".renderMarkdown())
        synthesisTask.complete()
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
        transcript?.write("## Final Recommendation Summary\n\n".toByteArray())
        transcript?.write("${finalRecommendationSummary}\n\n".toByteArray())
        transcript?.write("---\n\n".toByteArray())
        transcript?.write(
          "**Completed:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n".toByteArray()
        )


        overviewTaskStatus?.clear()
        overviewTask.add(
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
        """.trimMargin().renderMarkdown()
        )
        overviewTask.complete()

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
        val reportFile = "Ethical_Analysis_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.md"
        val reportUrl = task.saveFile(reportFile, transcript.toString().toByteArray())
        task.add("Analysis report generated: <a href='$reportUrl'>Download Markdown</a>")


        val duration = System.currentTimeMillis() - startTime

        log.info("EthicalReasoning task completed in ${duration}ms")
        task.safeComplete("Analysis complete.", log)
        resultFn("$finalResult\n\n---\n\nFull report saved to: `$reportFile`")

      } catch (e: Exception) {
        log.error("EthicalReasoning task failed", e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        overviewTask.add(
          """
            |## Ethical Reasoning Analysis
            |
            |**Status:** ❌ Analysis Failed
            |
            |**Error:** ${e.message}
            """.trimMargin().renderMarkdown()
        )
        task.error(e)
        task.safeComplete("Analysis failed: ${e.message}", log)
        resultFn("ERROR: Ethical reasoning analysis failed - ${e.message}")
      } finally {
        transcript?.close()
      }
    }

  }

}