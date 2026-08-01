package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.text.ui.DiffInstrumentor
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.concurrent.Semaphore

class IterativeFileModificationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: IterativeFileModificationTaskExecutionConfigData?
) : AbstractFileTask<IterativeFileModificationTask.IterativeFileModificationTaskExecutionConfigData>(
  orchestrationConfig,
  planTask
) {

  class IterativeFileModificationTaskExecutionConfigData(
    related_files: List<String>? = null,
    @Description("Maximum number of change items to generate in the planning phase")
    val max_changes: Int = 10,
    @Description("Whether to require user approval between each change iteration")
    val approve_each_change: Boolean = false,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : FileTaskExecutionConfig(
    task_type = IterativeFileModification.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    related_files = related_files,
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (listOf<String>(main_file).isEmpty() && related_files.isNullOrEmpty()) {
        return "At least one file must be specified in either 'files' or 'related_files'"
      }
      if (max_changes < 1 || max_changes > 50) {
        return "max_changes must be between 1 and 50"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  class IterativeFileModificationTypeConfig(
    @Description("Model to use for the planning phase")
    var planningModel: ApiChatModel? = null,
    @Description("Model to use for the implementation phase")
    var implementationModel: ApiChatModel? = null,
    @Description("Custom system prompt for the planning agent")
    var planningPrompt: String? = null,
    @Description("Custom system prompt for the implementation agent")
    var implementationPrompt: String? = null,
  ) : TaskTypeConfig()

  override val typeConfig: IterativeFileModificationTypeConfig?
    get() = taskType.let { task_type ->
      orchestrationConfig.taskSettings.values.firstOrNull { it.task_type == task_type } as? IterativeFileModificationTypeConfig
    }

  override fun promptSegment() = """
IterativeFileModification - Multi-phase file modification with planning and iterative implementation
  * First, a planning agent analyzes the goal and generates a list of discrete changes
  * Then, each change is implemented one at a time by an implementation agent
  * Useful for complex refactoring or multi-step modifications
  * Supports optional user approval between each change iteration
  * Specify the modification_goal describing the overall objective
  * Set max_changes to limit the number of planned changes (default: 10)
  * Set approve_each_change to true for manual review between iterations
""".trimIndent()

  data class PlannedChange(
    @Description("Sequential index of this change")
    val index: Int = 0,
    @Description("Brief title describing the change")
    val title: String = "",
    @Description("Detailed description of what needs to be done")
    val description: String = "",
    @Description("List of file paths that will be modified")
    val targetFiles: List<String> = emptyList(),
    @Description("Explanation of why this change is needed")
    val rationale: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "Change title cannot be blank"
      if (description.isBlank()) return "Change description cannot be blank"
      return null
    }
  }

  data class ModificationPlan(
    @Description("List of planned changes in order of execution")
    val changes: List<PlannedChange> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (changes.isEmpty()) return "At least one change must be specified"
      return changes.mapNotNull { it.validate() }.firstOrNull()
    }
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val semaphore = Semaphore(0)
    val completionNotes = mutableListOf<String>()
    val transcript = task.newUserFileStream(transcriptFile())
    val tabs = TabbedDisplay(task)

    try {
      transcript?.write("# Iterative File Modification Task Transcript\n\n".toByteArray())
      transcript?.flush()

      // Phase 1: Planning
      val planningTab = tabs.newTask("Planning Phase")
      planningTab.add("Analyzing modification goal and generating change plan...".renderMarkdown())

      val plannedChanges = executePlanningPhase(agent, messages, planningTab, transcript)

      if (plannedChanges.isEmpty()) {
        planningTab.complete("No changes were identified by the planning agent.".renderMarkdown())
        resultFn("No changes identified for the given modification goal.")
        return
      }

      planningTab.complete(formatPlanSummary(plannedChanges).renderMarkdown())
      transcript?.write("\n## Planning Complete\nIdentified ${plannedChanges.size} changes to implement.\n".toByteArray())
      transcript?.flush()

      // Phase 2: Iterative Implementation
      val implementationTab = tabs.newTask("Implementation Phase")
      implementationTab.add("Beginning iterative implementation of ${plannedChanges.size} changes...".renderMarkdown())

      val implementedChanges = mutableListOf<String>()

      for ((index, change) in plannedChanges.withIndex()) {
        val changeTab = tabs.newTask("Change ${index + 1}: ${change.title}")

        transcript?.write("\n### Implementing Change ${index + 1}: ${change.title}\n".toByteArray())

        val changeResult = executeImplementationPhase(
          agent = agent,
          change = change,
          previousChanges = implementedChanges,
          task = changeTab,
          transcript = transcript,
          completionNotes = completionNotes
        )

        implementedChanges.add("Change ${index + 1} (${change.title}): $changeResult")

        // Handle approval between changes if configured
        if ((executionConfig?.approve_each_change
            ?: false) && !orchestrationConfig.autoFix && index < plannedChanges.size - 1
        ) {
          val approvalSemaphore = Semaphore(0)
          changeTab.add(acceptButtonFooter(changeTab.ui) {
            approvalSemaphore.release()
          })
          approvalSemaphore.acquire()
        } else {
          changeTab.complete()
        }
      }

      implementationTab.complete("All ${plannedChanges.size} changes have been processed.".renderMarkdown())

      // Final Summary
      val summary = buildFinalSummary(plannedChanges, completionNotes)
      transcript?.write("\n## Final Summary\n$summary\n".toByteArray())
      transcript?.flush()

      if (!orchestrationConfig.autoFix) {
        task.add(acceptButtonFooter(task.ui) {
          task.complete()
          semaphore.release()
        })
        semaphore.acquire()
      } else {
        semaphore.release()
      }

      resultFn(summary)

    } catch (e: Throwable) {
      task.error(e)
      log.error("Error in IterativeFileModificationTask", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      throw e
    } finally {
      transcript?.close()
    }
  }

  private fun executePlanningPhase(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    transcript: FileOutputStream?
  ): List<PlannedChange> {
    val typeConfig = typeConfig
    val planningModel = typeConfig?.planningModel?.let { it.instance(orchestrationConfig.user) }
      ?: defaultSmart
    val defaultChatter = planningModel.getChildClient(task)
    val parsingChatter = defaultFast.getChildClient(task)

    val fileContext = getInputFileCode()
    val dependencyContext = getPriorCode(agent.executionState)

    transcript?.write(
      """
## Planning Phase Input
<details>
<summary>File Context</summary>

$fileContext
</details>

<details>
<summary>Dependency Context</summary>

$dependencyContext
</details>

        """.toByteArray()
    )

    val planningPrompt = typeConfig?.planningPrompt ?: getDefaultPlanningPrompt()

    val planningAgent = ParsedAgent(
      resultClass = ModificationPlan::class.java,
      prompt = planningPrompt,
      model = defaultChatter,
      parsingModel = parsingChatter
    )

    val planningInput = buildString {
      appendLine("## Modification Goal")
      appendLine(executionConfig?.task_description)
      appendLine()
      appendLine("## Current File Contents")
      appendLine(fileContext)
      if (dependencyContext.isNotBlank()) {
        appendLine()
        appendLine("## Context from Dependencies")
        appendLine(dependencyContext)
      }
      appendLine()
      appendLine("## Instructions")
      appendLine("Generate a list of up to ${executionConfig?.max_changes ?: 10} discrete, ordered changes.")
      appendLine("Each change should be atomic and independently implementable.")
      appendLine("Return the changes as a structured list with index, title, description, targetFiles, and rationale for each.")
    }

    val planResult = planningAgent.answer(messages + listOf(planningInput))

    transcript?.write(
      """
## Planning Agent Response
<details>
<summary>Raw Output</summary>

${planResult.text}
</details>

        """.toByteArray()
    )

    task.add(planResult.text.renderMarkdown())

    // Use parsed result, with fallback to text parsing if needed
    val parsedChanges = planResult.obj.changes
    return if (parsedChanges.isNotEmpty()) {
      // Re-index the changes to ensure sequential numbering
      parsedChanges.mapIndexed { idx, change ->
        change.copy(
          index = idx + 1,
          targetFiles = change.targetFiles.ifEmpty { executionConfig?.let { listOf(it.main_file) } ?: emptyList() }
        )
      }.take(executionConfig?.max_changes ?: 10)
    } else {
      // Fallback to text parsing if structured parsing failed
      parsePlannedChanges(planResult.text)
    }
  }

  private fun executeImplementationPhase(
    agent: TaskOrchestrator,
    change: PlannedChange,
    previousChanges: List<String>,
    task: SessionTask,
    transcript: FileOutputStream?,
    completionNotes: MutableList<String>
  ): String {
    val typeConfig = typeConfig
    val implementationModel = typeConfig?.implementationModel?.let { it.instance(orchestrationConfig.user) }
      ?: defaultSmart
    val chatInterface = implementationModel.getChildClient(task)

    // Re-read files to get current state (may have been modified by previous iterations)
    val currentFileContents = change.targetFiles.mapNotNull { targetFilePath ->
      val file = root.resolve(targetFilePath).toFile()
      val relativePath = file.relativeTo(root.toFile()).path
      if (file.exists()) {
        "# $relativePath\n\n${TRIPLE_TILDE}\n${file.readText()}\n${TRIPLE_TILDE}"
      } else null
    }.joinToString("\n\n")

    val implementationAgent = ChatAgent(
      name = "IterativeModificationImplementer",
      prompt = (typeConfig?.implementationPrompt ?: getDefaultImplementationPrompt())
          + "\n\n" + orchestrationConfig.processor.patchFormatPrompt,
      model = chatInterface,
      temperature = orchestrationConfig.temperature,
    )

    val implementationInput = buildString {
      appendLine("## Change to Implement")
      appendLine("**Title:** ${change.title}")
      appendLine("**Description:** ${change.description}")
      appendLine("**Target Files:** ${change.targetFiles.joinToString(", ")}")
      appendLine("**Rationale:** ${change.rationale}")
      appendLine()
      appendLine("## Current File Contents")
      appendLine(currentFileContents)
      if (previousChanges.isNotEmpty()) {
        appendLine()
        appendLine("## Previously Implemented Changes")
        previousChanges.forEach { appendLine("- $it") }
      }
    }

    transcript?.write(
      """
#### Implementation Input for Change ${change.index}
<details>
<summary>Input</summary>

$implementationInput
</details>

        """.toByteArray()
    )

    val implementationResponse = implementationAgent.answer(listOf(implementationInput))

    transcript?.write(
      """
#### Implementation Response for Change ${change.index}
<details>
<summary>Raw Output</summary>

$implementationResponse
</details>

        """.toByteArray()
    )

    // Render with diff application links
    val autoFix = orchestrationConfig.autoFix
    val markdown = renderMarkdown(implementationResponse, ui = task.ui) {
      DiffInstrumentor(
        orchestrationConfig.processor,
        SessionRenderer(task),
      ).instrument(
        root = agent.root,
        response = it,
        handle = { newCodeMap: Map<Path, String> ->
          newCodeMap.forEach { (path, _) ->
            val note =
              "Change ${change.index} - <a href='fileIndex/${agent.session}/$path'>$path</a> Updated"
            completionNotes += note
            try {
              transcript?.write("- $note\n".toByteArray())
            } catch (e: Exception) {
              log.warn("Failed to write to transcript for change ${change.index}, path: $path", e)
            }
          }
        },
        shouldAutoApply = { it: Path -> autoFix },
        defaultFile = change.targetFiles.firstOrNull(),
        resolver = ::resolveToRelativePath,
        prefilterFilename = ::prefilterFilename
      )
    }

    if (autoFix) {
      task.complete(markdown)
    } else {
      task.add(markdown)
    }
    transcript?.flush()

    return "Completed"
  }

  private fun parsePlannedChanges(response: String): List<PlannedChange> {
    val changes = mutableListOf<PlannedChange>()
    val changePattern = Regex(
      """(?:^|\n)(?:#{1,3}\s*)?(?:Change\s*)?(\d+)[.:\s]+(.+?)(?:\n|$)""",
      RegexOption.MULTILINE
    )

    val matches = changePattern.findAll(response)
    var currentIndex = 0

    for (match in matches) {
      currentIndex++
      val title = match.groupValues[2].trim()

      // Extract description - text until next change header or end
      val startPos = match.range.last + 1
      val nextMatch = changePattern.find(response, startPos)
      val endPos = nextMatch?.range?.first ?: response.length
      val description = response.substring(startPos, endPos).trim()

      // Try to extract target files from description
      val filePattern = Regex("""(?:file[s]?|target[s]?):\s*([^\n]+)""", RegexOption.IGNORE_CASE)
      val fileMatch = filePattern.find(description)

      changes.add(
        PlannedChange(
          index = currentIndex,
          title = title,
          description = description,
          targetFiles = fileMatch?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: executionConfig?.let {
            listOf(
              it.main_file
            )
          } ?: listOf(),
          rationale = "Part of: ${executionConfig?.task_description}"
        )
      )

      if (currentIndex >= (executionConfig?.max_changes ?: 10)) break
    }

    // Fallback: if no structured changes found, create a single change
    if (changes.isEmpty() && response.isNotBlank()) {
      changes.add(
        PlannedChange(
          index = 1,
          title = "Implement Modification",
          description = response,
          targetFiles = executionConfig?.let { listOf(it.main_file) } ?: listOf(),
          rationale = executionConfig?.task_description ?: ""
        )
      )
    }

    return changes
  }

  private fun formatPlanSummary(changes: List<PlannedChange>): String = buildString {
    appendLine("# Modification Plan")
    appendLine()
    appendLine("The following ${changes.size} changes have been identified:")
    appendLine()
    changes.forEach { change ->
      appendLine("## ${change.index}. ${change.title}")
      appendLine("**Target Files:** ${change.targetFiles.joinToString(", ")}")
      appendLine()
      appendLine(change.description.take(500))
      if (change.description.length > 500) appendLine("...")
      appendLine()
    }
  }

  private fun buildFinalSummary(changes: List<PlannedChange>, completionNotes: List<String>): String = buildString {
    appendLine("### Iterative Modification Complete")
    appendLine()
    appendLine("**Goal:** ${executionConfig?.task_description}")
    appendLine()
    appendLine("**Changes Planned:** ${changes.size}")
    appendLine()
    if (completionNotes.isNotEmpty()) {
      appendLine("**Files Modified:**")
      completionNotes.forEach { appendLine("* $it") }
    } else {
      appendLine("No files were modified.")
    }
  }

  private fun getDefaultPlanningPrompt(): String = """
You are a code modification planning agent. Your task is to analyze a modification goal and break it down into discrete, ordered changes.

For each change, provide:
1. A clear, concise title
2. A detailed description of what needs to be done
3. The specific files that will be affected
4. The rationale for this change

Return your response as a structured ModificationPlan with a list of PlannedChange objects.
Each change should be atomic and independently implementable.
Consider dependencies between changes and order them appropriately.


Each PlannedChange should have:
- index: Sequential number starting from 1
- title: Brief descriptive title
- description: Detailed explanation of the change
- targetFiles: List of file paths to modify
- rationale: Why this change is needed
    """.trimIndent()

   private fun getDefaultImplementationPrompt(): String = """
You are a code implementation agent. Your task is to implement a specific change as part of a larger modification plan.

Guidelines:
- Implement ONLY the specific change described - do not make additional modifications
- Ensure the change integrates smoothly with existing code
- Follow the project's coding standards and patterns
- Provide clear, well-documented code
- Consider edge cases and error handling

After the code changes, provide a brief summary of what was implemented.

    """.trimIndent()

  companion object {
    private val log = LoggerFactory.getLogger(IterativeFileModificationTask::class.java)

    @JvmStatic
    val IterativeFileModification = TaskType(
        name = "IterativeFileModification",
        category = "File",
        taskClass = IterativeFileModificationTask::class.java,
        executionConfigClass = IterativeFileModificationTaskExecutionConfigData::class.java,
        taskSettingsClass = IterativeFileModificationTypeConfig::class.java,
        description = "Multi-phase file modification with planning and iterative implementation",
        tooltipHtml = """
                        Performs complex file modifications through a two-phase approach:
                        <ul>
                            <li><b>Planning Phase:</b> An AI agent analyzes the modification goal and generates a list of discrete, ordered changes</li>
                            <li><b>Implementation Phase:</b> Each change is implemented iteratively by a separate AI agent</li>
                            <li>Supports optional user approval between each change iteration</li>
                            <li>Maintains context of previously implemented changes for coherent modifications</li>
                            <li>Ideal for complex refactoring, multi-step modifications, or large-scale code changes</li>
                            <li>Provides detailed transcripts and progress tracking for each phase</li>
                            <li>Configurable models for planning and implementation phases</li>
                        </ul>
                    """,
    )
  }
}