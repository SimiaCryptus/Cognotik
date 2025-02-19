package com.simiacryptus.skyenet.apps.plan

import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.apps.plan.PlanUtil.isWindows
import com.simiacryptus.skyenet.apps.plan.TaskType.Companion.getAvailableTaskTypes
import com.simiacryptus.skyenet.apps.plan.TaskType.Companion.getImpl
import com.simiacryptus.skyenet.apps.plan.tools.CommandAutoFixTask
import com.simiacryptus.skyenet.apps.plan.tools.CommandAutoFixTask.CommandAutoFixTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.plan.PlanningTask.PlanningTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.plan.PlanningTask.TaskBreakdownResult
import com.simiacryptus.skyenet.core.actors.ParsedActor


open class PlanSettings(
  var defaultModel: ChatModel,
  var parsingModel: ChatModel,
  val shellCmd: List<String> = listOf(if (isWindows) "powershell" else "bash"),
  var temperature: Double = 0.2,
  val budget: Double = 2.0,
  val taskSettings: MutableMap<String, TaskSettingsBase> = TaskType.values().associateWith { taskType ->
    TaskSettingsBase(
      taskType.name, when (taskType) {
        TaskType.FileModification, TaskType.Inquiry -> true
        else -> false
      }
    )
  }.mapKeys { it.key.name }.toMutableMap(),
  var autoFix: Boolean = false,
  var allowBlocking: Boolean = true,
  val env: Map<String, String>? = mapOf(),
  val workingDir: String? = ".",
  val language: String? = if (isWindows) "powershell" else "bash",
  var githubToken: String? = null,
  var googleApiKey: String? = null,
  var googleSearchEngineId: String? = null,
) {

  fun getTaskSettings(taskType: TaskType<*, *>): TaskSettingsBase = taskSettings[taskType.name] ?: TaskSettingsBase(taskType.name)

  fun setTaskSettings(taskType: TaskType<*, *>, settings: TaskSettingsBase) {
    taskSettings[taskType.name] = settings
  }

  fun copy(
    model: ChatModel = this.defaultModel,
    parsingModel: ChatModel = this.parsingModel,
    command: List<String> = this.shellCmd,
    temperature: Double = this.temperature,
    budget: Double = this.budget,
    taskSettings: MutableMap<String, TaskSettingsBase> = this.taskSettings,
    autoFix: Boolean = this.autoFix,
    allowBlocking: Boolean = this.allowBlocking,
    env: Map<String, String>? = this.env,
    workingDir: String? = this.workingDir,
    language: String? = this.language,
  ) = PlanSettings(
    defaultModel = model,
    parsingModel = parsingModel,
    shellCmd = command,
    temperature = temperature,
    budget = budget,
    taskSettings = taskSettings,
    autoFix = autoFix,
    allowBlocking = allowBlocking,
    env = env,
    workingDir = workingDir,
    language = language,
    githubToken = this.githubToken,
    googleApiKey = this.googleApiKey,
    googleSearchEngineId = this.googleSearchEngineId,
  )

  fun planningActor(): ParsedActor<TaskBreakdownResult> {
    val planTaskSettings = this.getTaskSettings(TaskType.TaskPlanning)
    // Note: the platform automatically reads and provides the necessary JSON software graph.
    // The prompt below should focus purely on breaking down the user instruction without re-framing the JSON data.
    val prompt = """
                      Given a user request, identify and list smaller, actionable tasks that can be directly implemented in code.
                      (Do not repeat or ask for the JSON content since the platform already handles reading the software graph.)
                      For each task:
                      * Provide input/output file names if applicable
                      * Describe any execution dependencies and the order in which tasks should be run
                      * Write a brief description of the task and its role
                      * Mention any important interface or integration details
                      The available task types are:
                      """.trimIndent() + "\n  " + getAvailableTaskTypes(this).joinToString("\n") { taskType ->
      "* ${getImpl(this, taskType).promptSegment()}"
    } + """
                      (Remember: the JSON file content is already loaded by the platform.)
                      """.trimIndent() + (if (planTaskSettings.enabled) "Do not start your plan with a plan to plan!\n" else "")
    val describer = describer()
    val parserPrompt =
      ("\nTask Subtype Schema:\n\n" + getAvailableTaskTypes(this).joinToString("\n\n") { taskType ->
          "\n${taskType.name}:\n  ${
              describer.describe(taskType.taskDataClass).lineSequence()
                  .map {
                      when {
                          it.isBlank() -> {
                              when {
                                  it.length < "  ".length -> "  "
                                  else -> it
                              }
                          }

                          else -> "  " + it
                      }
                  }
                  .joinToString("\n")
          }\n".trim()
      } + "\n")
    return ParsedActor(
      name = "TaskBreakdown",
      resultClass = TaskBreakdownResult::class.java,
      exampleInstance = exampleInstance,
      prompt = prompt,
      model = planTaskSettings.model ?: this.defaultModel,
      parsingModel = this.parsingModel,
      temperature = this.temperature,
      describer = describer,
      parserPrompt = parserPrompt
    )
  }

  open fun describer() = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "aicoder.actions"
  ) {
    override val includeMethods: Boolean get() = false

    override fun getEnumValues(clazz: Class<*>): List<String> {
      return if (clazz == TaskType::class.java) {
        taskSettings.filter { it.value.enabled }.map { it.key }
      } else {
        super.getEnumValues(clazz)
      }
    }
  }

  companion object {
    var exampleInstance = TaskBreakdownResult(
      tasksByID = mapOf(
        "1" to CommandAutoFixTaskConfigData(
          task_description = "Task 1", task_dependencies = listOf(), commands = listOf(
            CommandAutoFixTask.CommandWithWorkingDir(
              command = listOf("echo", "Hello, World!"), workingDir = "."
            )
          )
        ), "2" to FileModificationTaskConfigData(
          task_description = "Task 2",
          task_dependencies = listOf("1"),
          input_files = listOf("input2.txt"),
          output_files = listOf("output2.txt"),
        ), "3" to PlanningTaskConfigData(
          task_description = "Task 3",
          task_dependencies = listOf("2"),
        )
      ),
    )
  }
}