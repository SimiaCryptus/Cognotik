package com.simiacryptus.cognotik.plan
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.ObjectNode

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.plan.PlanUtil.isWindows
import com.simiacryptus.cognotik.plan.TaskType.Companion.getAvailableTaskTypes
import com.simiacryptus.cognotik.plan.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.CommandAutoFixTask
import com.simiacryptus.cognotik.plan.tools.CommandAutoFixTask.CommandAutoFixTaskConfigData
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.cognotik.plan.tools.plan.PlanningTask.PlanningTaskConfigData
import com.simiacryptus.cognotik.plan.tools.plan.PlanningTask.TaskBreakdownResult
import com.simiacryptus.cognotik.util.Selenium2S3.Companion.chromeDriver
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ChatModel
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
class TaskSettingsMapDeserializer : JsonDeserializer<MutableMap<String, TaskSettingsBase>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MutableMap<String, TaskSettingsBase> {
        val codec = p.codec as ObjectMapper
        val node: JsonNode = codec.readTree(p)
        val result = mutableMapOf<String, TaskSettingsBase>()
        if (node.isObject) {
            node.fields().forEach { (key, valueNode) ->
                if (valueNode.isObject) {
                    // Add/overwrite the task_type field in the value node
                    // This ensures the PlanTaskTypeIdResolver in TaskSettingsBase can find the type ID
                    (valueNode as ObjectNode).put("task_type", key)
                    try {
                        val taskSettingsEntry = codec.treeToValue(valueNode, TaskSettingsBase::class.java)
                        if (taskSettingsEntry != null) {
                            result[key] = taskSettingsEntry
                        } else {
                            // Log or handle error: Deserialization returned null
                            ctxt.reportInputMismatch(TaskSettingsBase::class.java, "Failed to deserialize TaskSettingsBase for key '$key', got null")
                        }
                    } catch (e: Exception) {
                        // Log or handle error: Deserialization threw an exception
                        ctxt.reportInputMismatch(TaskSettingsBase::class.java, "Failed to deserialize TaskSettingsBase for key '$key': ${e.message}")
                    }
                } else {
                    // Log or handle error: Value is not an object
                    ctxt.reportInputMismatch(Map::class.java, "Value for key '$key' in taskSettings is not a JSON object, but ${valueNode.nodeType}")
                }
            }
        } else {
            // Log or handle error: taskSettings is not a JSON object
            ctxt.reportInputMismatch(Map::class.java, "taskSettings is not a JSON object, but ${node.nodeType}")
        }
        return result
    }
}


open class PlanSettings(
    var defaultModel: ChatModel,
    var parsingModel: ChatModel,
    val shellCmd: List<String> = listOf(if (isWindows) "powershell" else "bash"),
    var temperature: Double = 0.2,
    val budget: Double = 2.0,
    @JsonDeserialize(using = TaskSettingsMapDeserializer::class)
    val taskSettings: MutableMap<String, TaskSettingsBase> = TaskType.values().associateWith { taskType ->
        TaskSettingsBase(
            taskType.name, when (taskType) {
                TaskType.FileModificationTask, TaskType.InsightTask -> true
                else -> false
            }
        )
    }.mapKeys { it.key.name }.toMutableMap(),
    var autoFix: Boolean = false,
    val env: Map<String, String>? = mapOf(),
    val workingDir: String? = ".",
    val language: String? = if (isWindows) "powershell" else "bash",
    var maxTaskHistoryChars: Int = 10000,
    var maxTasksPerIteration: Int = 3,
    var maxIterations: Int = 10,

) {

    val absoluteWorkingDir get() = when {
        this.workingDir == null -> null//throw IllegalStateException("Working directory not set")
        this.workingDir.startsWith("~") -> File(this.workingDir.replaceFirst("~", System.getProperty("user.home"))).absolutePath
        else -> File(this.workingDir).absolutePath
    }

    fun getTaskSettings(taskType: TaskType<*, *>): TaskSettingsBase =
        taskSettings[taskType.name] ?: TaskSettingsBase(taskType.name)

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
        env = env,
        workingDir = workingDir,
        language = language,
        maxTaskHistoryChars = this.maxTaskHistoryChars,
        maxTasksPerIteration = this.maxTasksPerIteration,
        maxIterations = this.maxIterations,
    )

    fun planningActor(describer: TypeDescriber): ParsedActor<TaskBreakdownResult> {
        val planTaskSettings = this.getTaskSettings(TaskType.TaskPlanningTask)


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

    /*
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
      */
    fun driver(): RemoteWebDriver {
        return chromeDriver()
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
                    related_files = listOf("input2.txt"),
                    files = listOf("output2.txt"),
                ), "3" to PlanningTaskConfigData(
                    task_description = "Task 3",
                    task_dependencies = listOf("2"),
                )
            ),
        )
    }
}