package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
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

      transcriptStream = task.newUserFileStream(transcriptFile())
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
        if (!executionConfig?.related_files.isNullOrEmpty()) {
          add("<b>Input Files:</b> ${executionConfig?.related_files?.joinToString(", ")}")
        }
      }

      val inputFileContent =
        super.getInputFileContent(executionConfig?.related_files, root, treatDocumentsAsText = true)
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

    @JvmStatic
    val StructuralInvariantAnalysis = TaskType(
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