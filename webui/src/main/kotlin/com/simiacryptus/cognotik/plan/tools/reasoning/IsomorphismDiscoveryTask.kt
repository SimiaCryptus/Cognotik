package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
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
    val transcript = task.newUserFileStream(transcriptFile())
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
          transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
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

    @JvmStatic
    val IsomorphismDiscovery = TaskType(
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