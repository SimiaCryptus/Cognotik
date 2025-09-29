package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.PlanUtil.isWindows
import com.simiacryptus.cognotik.plan.TaskType.Companion.getAvailableTaskTypes
import com.simiacryptus.cognotik.plan.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.SelfHealingTask
import com.simiacryptus.cognotik.plan.tools.SelfHealingTask.SelfHealingTaskConfigData
import com.simiacryptus.cognotik.plan.tools.file.AnalysisTask
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModificationTaskType
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
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
                            ctxt.reportInputMismatch(
                                TaskSettingsBase::class.java,
                                "Failed to deserialize TaskSettingsBase for key '$key', got null"
                            )
                        }
                    } catch (e: Exception) {
                        // Log or handle error: Deserialization threw an exception
                        ctxt.reportInputMismatch(
                            TaskSettingsBase::class.java,
                            "Failed to deserialize TaskSettingsBase for key '$key': ${e.message}"
                        )
                    }
                } else {
                    // Log or handle error: Value is not an object
                    ctxt.reportInputMismatch(
                        Map::class.java,
                        "Value for key '$key' in taskSettings is not a JSON object, but ${valueNode.nodeType}"
                    )
                }
            }
        } else {
            // Log or handle error: taskSettings is not a JSON object
            ctxt.reportInputMismatch(Map::class.java, "taskSettings is not a JSON object, but ${node.nodeType}")
        }
        return result
    }
}


open class OrchestrationConfig(
    var defaultModel: ApiChatModel? = null,
    var parsingModel: ApiChatModel? = null,
    val shellCmd: List<String> = listOf(if (isWindows) "powershell" else "bash"),
    var temperature: Double = 0.2,
    val budget: Double = 2.0,
    @JsonDeserialize(using = TaskSettingsMapDeserializer::class)
    val taskSettings: MutableMap<String, TaskSettingsBase> = TaskType.values().associateWith { taskType ->
        TaskSettingsBase(
            taskType.name, when (taskType) {
                FileModificationTaskType, AnalysisTask.AnalysisTaskType -> true
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

    @get:JsonIgnore
    val defaultChatter get() = instance(defaultModel ?: throw IllegalStateException("Default model not set"))

    @get:JsonIgnore
    val parsingChatter
        get() = instance(
            parsingModel ?: defaultModel ?: throw IllegalStateException("Parsing model not set")
        )

    @JsonIgnore
    open fun instance(model: ApiChatModel): ChatInterface {
        throw NotImplementedError("Must be implemented in subclass")
    }

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

    fun getTaskSettings(taskType: TaskType<*, *>): TaskSettingsBase =
        taskSettings[taskType.name] ?: TaskSettingsBase(taskType.name)

    fun setTaskSettings(taskType: TaskType<*, *>, settings: TaskSettingsBase) {
        taskSettings[taskType.name] = settings
    }

    @JsonIgnore
    fun copy(
        model: ApiChatModel? = this.defaultModel,
        parsingModel: ApiChatModel? = this.parsingModel,
        command: List<String> = this.shellCmd,
        temperature: Double = this.temperature,
        budget: Double = this.budget,
        taskSettings: MutableMap<String, TaskSettingsBase> = this.taskSettings,
        autoFix: Boolean = this.autoFix,
        env: Map<String, String>? = this.env,
        workingDir: String? = this.workingDir,
        language: String? = this.language,
        instanceFn: (ApiChatModel) -> ChatInterface = this::instance,
    ): OrchestrationConfig = OrchestrationConfigCopy(
        model,
        parsingModel,
        command,
        temperature,
        budget,
        taskSettings,
        autoFix,
        env,
        workingDir,
        language,
        instanceFn,
        maxTaskHistoryChars,
        maxTasksPerIteration,
        maxIterations,
    )

    fun planningActor(describer: TypeDescriber): ParsedAgent<TaskBreakdownResult> {
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
                      """.trimIndent()
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
        return ParsedAgent(
            name = "TaskBreakdown",
            resultClass = TaskBreakdownResult::class.java,
            exampleInstance = exampleInstance,
            prompt = prompt,
            model = defaultModel?.let { instance(it) } ?: throw IllegalStateException("No model configured"),
            parsingModel = this.parsingChatter,
            temperature = this.temperature,
            describer = describer,
            parserPrompt = parserPrompt
        )
    }

    companion object {
        var exampleInstance = TaskBreakdownResult(
            tasksByID = mapOf(
                "1" to SelfHealingTaskConfigData(
                    task_description = "Task 1", task_dependencies = listOf(), commands = listOf(
                        SelfHealingTask.CommandWithWorkingDir(
                            command = listOf("echo", "Hello, World!"), workingDir = "."
                        )
                    )
                ), "2" to FileModificationTaskConfigData(
                    task_description = "Task 2",
                    task_dependencies = listOf("1"),
                    related_files = listOf("input2.txt"),
                    files = listOf("output2.txt"),
                )
            ),
        )
    }
}

private class OrchestrationConfigCopy(
    model: ApiChatModel?,
    parsingModel: ApiChatModel?,
    command: List<String>,
    temperature: Double,
    budget: Double,
    taskSettings: MutableMap<String, TaskSettingsBase>,
    autoFix: Boolean,
    env: Map<String, String>?,
    workingDir: String?,
    language: String?,
    @JsonIgnore val instanceFn: (ApiChatModel) -> ChatInterface,
    maxTaskHistoryChars: Int,
    maxTasksPerIteration: Int,
    maxIterations: Int,
) : OrchestrationConfig(
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
    maxTaskHistoryChars = maxTaskHistoryChars,
    maxTasksPerIteration = maxTasksPerIteration,
    maxIterations = maxIterations,
) {
    override fun instance(model: ApiChatModel): ChatInterface = instanceFn(model)
}


data class TaskBreakdownResult(
    @Description("A map where each task ID is associated with its corresponding PlanTask object. Crucial for defining task relationships and information flow.")
    val tasksByID: Map<String, TaskConfigBase>? = null,
)
