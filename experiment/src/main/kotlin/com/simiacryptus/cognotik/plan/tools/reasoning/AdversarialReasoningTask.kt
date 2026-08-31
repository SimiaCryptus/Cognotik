package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
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
import com.simiacryptus.cognotik.platform.model.ISessionTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
  ) : ValidatedObject {
    override fun validate() = vulnerabilities.mapNotNull { it.validate() }.firstOrNull()
  }

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
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    val related_files: List<String>? = null,
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
    task: ISessionTask,
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

    val executionConfig = this.executionConfig ?: run {
      log.error("Execution configuration is missing")
      task.safeComplete("CONFIGURATION ERROR: Execution configuration is missing", log)
      resultFn("CONFIGURATION ERROR: Execution configuration is missing for adversarial analysis")
      return
    }
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

  private fun initializeTranscript(task: ISessionTask): FileOutputStream? {
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

  private fun getInputFileCode(): String = (executionConfig?.related_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          com.simiacryptus.cognotik.util.FileSelectionUtils.isIgnored(it.toPath()) -> false
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
      api: ChatInterface,
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
      parsingModel = api,
      temperature = 0.8 // Higher temperature for creative adversarial thinking
    )
  }

  private fun createMitigationAgent(api: ChatInterface): ChatAgent {
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

    @JvmStatic
    val AdversarialReasoning = TaskType(
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