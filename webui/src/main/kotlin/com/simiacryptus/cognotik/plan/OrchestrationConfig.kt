package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FileSelectionUtils.getAvailableFiles
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.Locale.getDefault
import kotlin.io.path.Path

class OrchestrationConfig(
  var sessionId: String = "default",
  var smartModel: String? = null,
  var fastModel: String? = null,
  var imageModel: String? = null,
  val shellCmd: List<String> = listOf(if (System.getProperty("os.name").lowercase(getDefault()).contains("windows")) "powershell" else "bash"),
  var temperature: Double = 0.2,
  val budget: Double = 2.0,
  val taskSettings: MutableMap<String, TaskTypeConfig> = TaskType.values().filter {
    false // Do not auto-enable any tasks
  }.associateWith { taskType ->
    taskType.newSettings()?.let {
      it.name = taskType.description
      it
    } ?: throw IllegalStateException("No default config for task type ${taskType.name}")
  }.mapKeys { it.key.name }.toMutableMap(),
  var cognitiveSettings: CognitiveModeConfig? = null,
  var autoFix: Boolean = false,
  val workingDir: String? = ".",
  var user: User = com.simiacryptus.cognotik.platform.model.defaultUser
) {

  @get:JsonIgnore
  var processor: PatchProcessor = PatchProcessors.Fuzzy

  @get:JsonIgnore
  val defaultSmart get() = (smartModel?.instance(user)
    ?: throw IllegalStateException("Default model not set")).instance(user)

  @get:JsonIgnore
  val defaultFast get() = (fastModel?.instance(user) ?: smartModel?.instance(user)
    ?: throw IllegalStateException("Parsing model not set")).instance(user)

  @get:JsonIgnore
  val defaultImage get() = (imageModel?.instance(user)
    ?: throw IllegalStateException("Image chat model not set")).instance(user)


  @get:JsonIgnore
  val absoluteWorkingDir
    get() = when {
      this.workingDir == null -> null//throw IllegalStateException("Working directory not set")
      this.workingDir.startsWith("~") -> File(
        this.workingDir.replaceFirst(
          "~",
          System.getProperty("user.home")
        )
      ).absolutePath

      else -> File(this.workingDir).absolutePath
    }

  fun planningActor(
    describer: TypeDescriber,
    task: SessionTask
  ): ParsedAgent<TaskBreakdownResult> {
    val availableTaskTypes = TaskType.getAvailableTaskTypes(this)
    return planningActor(
      taskDescriptions = availableTaskTypes.joinToString("\n") { taskType ->
        val impl = this.getImpl(taskType)
        "* ${impl.promptSegment()}"
      } + (this.workingDir?.let { root ->
        "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
      } ?: ""),
      model = defaultSmart.getChildClient(task),
      fastModel = defaultFast.getChildClient(task),
      temperature = temperature,
      describer = describer,
      availableTaskTypes = availableTaskTypes
    )
  }

  @JsonIgnore
  fun copy(
    model: String? = this.smartModel,
    fastModel: String? = this.fastModel,
    imageChatModel: String? = this.imageModel,
    shellCmd: List<String> = this.shellCmd,
    temperature: Double = this.temperature,
    budget: Double = this.budget,
    taskSettings: MutableMap<String, TaskTypeConfig> = this.taskSettings,
    cognitiveSettings: CognitiveModeConfig? = this.cognitiveSettings,
    autoFix: Boolean = this.autoFix,
    workingDir: String? = this.workingDir,
    sessionId: String = this.sessionId,
  ): OrchestrationConfig = OrchestrationConfig(
    smartModel = model,
    fastModel = fastModel,
    imageModel = imageChatModel,
    shellCmd = shellCmd,
    temperature = temperature,
    budget = budget,
    taskSettings = taskSettings,
    cognitiveSettings = cognitiveSettings,
    autoFix = autoFix,
    workingDir = workingDir,
    sessionId = sessionId,
    user = user
  )

  data class TaskBreakdownResult(
    @Description("A map where each task ID is associated with its corresponding PlanTask object. Crucial for defining task relationships and information flow.")
    val tasksByID: Map<String, TaskExecutionConfig>? = null,
  )

  companion object {
    var exampleInstance = TaskBreakdownResult(
      tasksByID = mapOf(
      ),
    )

    fun planningActor(
      taskDescriptions: String,
      model: ChatInterface,
      fastModel: ChatInterface,
      temperature: Double,
      describer: TypeDescriber,
      availableTaskTypes: List<TaskType<*, *>>
    ): ParsedAgent<TaskBreakdownResult> = ParsedAgent(
      name = "TaskBreakdown",
      resultClass = TaskBreakdownResult::class.java,
      exampleInstance = exampleInstance,
      prompt = """
              Given a user request, identify and list smaller, actionable tasks that can be directly implemented in code.
              (Do not repeat or ask for the JSON content since the platform already handles reading the software graph.)
              For each task:
              * Provide input/output file names if applicable
              * Describe any execution dependencies and the order in which tasks should be run
              * Write a brief description of the task and its role
              * Mention any important interface or integration details
              The available task types are:
              """.trimIndent() + "\n  " + taskDescriptions + """
              (Remember: the JSON file content is already loaded by the platform.)
              """.trimIndent(),
      model = model,
      parsingChatter = fastModel,
      temperature = temperature,
      describer = describer,
      parserPrompt = ("\nTask Subtype Schema:\n\n" + availableTaskTypes.joinToString("\n\n") { taskType ->
        "\n${taskType.name}:\n  ${
          describer.describe(taskType.executionConfigClass).lineSequence()
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
    )

    @JsonIgnore
    var instanceFn: ((ApiChatModel, User) -> ChatInterface)? = null

    @JsonIgnore
    fun ApiChatModel.instance(user: User) =
      instanceFn?.let { it(this, user) } ?: throw IllegalStateException("Instance function not set")
  }

  /**
   * Get all available task configurations for a given task type
   */
  fun getTaskConfigs(taskType: TaskType<*, *>): List<TaskTypeConfig> {
    return taskSettings.filter { it.value.task_type == taskType.name }.values.toList()
  }

}

fun String.instance(user: User): ApiChatModel? {
  val userSettings = fileApplicationServices().userSettingsManager.getUserSettings(user)
  val chatModel = userSettings.apis
    .filter { it.provider != null && it.key != null && it.baseUrl != null }
    .flatMap { it.provider!!.getChatModels(it.key!!, it.baseUrl!!) }
    .firstOrNull { it.modelId == this }
  val toApiChatModel = chatModel?.toApiChatModel(user)
  return toApiChatModel
}