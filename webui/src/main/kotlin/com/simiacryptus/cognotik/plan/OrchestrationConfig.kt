package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.PlanUtil.isWindows
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeStrategies
import com.simiacryptus.cognotik.plan.tools.SelfHealingTask
import com.simiacryptus.cognotik.plan.tools.SelfHealingTask.SelfHealingTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File


class OrchestrationConfig(
    @JsonSerialize(using = ApiChatModelSerializer::class)
    @JsonDeserialize(using = ApiChatModelDeserializer::class)
    var defaultModel: ApiChatModel? = null,
    @JsonSerialize(using = ApiChatModelSerializer::class)
    @JsonDeserialize(using = ApiChatModelDeserializer::class)
    var parsingModel: ApiChatModel? = null,
    var cognitiveMode: CognitiveModeStrategies? = null,
    val shellCmd: List<String> = listOf(if (isWindows) "powershell" else "bash"),
    var temperature: Double = 0.2,
    val budget: Double = 2.0,
    val taskSettings: MutableMap<String, TaskTypeConfig> = TaskType.values().filter {
        false
    }.associateWith { taskType ->
        TaskTypeConfig(
            taskType.name, taskType.description
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
        get() = instance(parsingModel ?: defaultModel ?: throw IllegalStateException("Parsing model not set"))

    @JsonIgnore
    fun instance(model: ApiChatModel) = instanceFn?.let { it(model) }
        ?: throw IllegalStateException("Instance function not set")

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

    fun getTaskSettings(taskType: TaskType<*, *>): TaskTypeConfig =
        taskSettings[taskType.name] ?: TaskTypeConfig(taskType.name)

    fun planningActor(
        describer: TypeDescriber,
        task: SessionTask
    ): ParsedAgent<TaskBreakdownResult> {
        val availableTaskTypes = TaskType.Companion.getAvailableTaskTypes(this)
        return planningActor(
            taskDescriptions = availableTaskTypes.joinToString("\n") { taskType ->
                val impl = TaskType.Companion.getImpl(this, taskType)
                "* ${impl.promptSegment()}"
            },
            model = defaultChatter.getChildClient(task),
            parsingModel = parsingChatter.getChildClient(task),
            temperature = temperature,
            describer = describer,
            availableTaskTypes = availableTaskTypes
        )
    }

    @JsonIgnore
    fun copy(
        model: ApiChatModel? = this.defaultModel,
        parsingModel: ApiChatModel? = this.parsingModel,
        command: List<String> = this.shellCmd,
        temperature: Double = this.temperature,
        budget: Double = this.budget,
        taskSettings: MutableMap<String, TaskTypeConfig> = this.taskSettings,
        autoFix: Boolean = this.autoFix,
        env: Map<String, String>? = this.env,
        workingDir: String? = this.workingDir,
        language: String? = this.language,
        cognitiveMode: CognitiveModeStrategies? = this.cognitiveMode,
        maxTaskHistoryChars: Int = this.maxTaskHistoryChars,
        maxTasksPerIteration: Int = this.maxTasksPerIteration,
        maxIterations: Int = this.maxIterations,
    ): OrchestrationConfig = OrchestrationConfig(
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
        cognitiveMode = cognitiveMode,
    )



    data class TaskBreakdownResult(
        @Description("A map where each task ID is associated with its corresponding PlanTask object. Crucial for defining task relationships and information flow.")
        val tasksByID: Map<String, TaskExecutionConfig>? = null,
    )

    companion object {
        var exampleInstance = TaskBreakdownResult(
            tasksByID = mapOf(
                "1" to SelfHealingTaskExecutionConfigData(
                    task_description = "Task 1", task_dependencies = listOf(), commands = listOf(
                        SelfHealingTask.CommandWithWorkingDir(
                            command = listOf("echo", "Hello, World!"), workingDir = "."
                        )
                    )
                ), "2" to FileModificationTaskExecutionConfigData(
                    task_description = "Task 2",
                    task_dependencies = listOf("1"),
                    related_files = listOf("input2.txt"),
                    files = listOf("output2.txt"),
                )
            ),
        )

        fun planningActor(
            taskDescriptions: String,
            model: ChatInterface,
            parsingModel: ChatInterface,
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
            parsingModel = parsingModel,
            temperature = temperature,
            describer = describer,
            parserPrompt = ("\nTask Subtype Schema:\n\n" + availableTaskTypes.joinToString("\n\n") { taskType ->
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
        )

        @JsonIgnore var instanceFn: ((ApiChatModel) -> ChatInterface)? = null
    }

    /**
     * Get all available task configurations for a given task type
     */
    fun getTaskConfigs(taskType: TaskType<*, *>): List<TaskTypeConfig> {
        return taskSettings.filter { it.value.task_type == taskType.name }.values.toList()
    }

    /**
     * Get a specific task configuration by task type and name
     */
    fun getTaskConfig(taskType: TaskType<*, *>, configName: String?): TaskTypeConfig? {
        val configs = getTaskConfigs(taskType)
        return if (configName != null) {
            configs.firstOrNull { it.name == configName }
        } else {
            configs.firstOrNull()
        }
    }

    fun addTaskConfig(taskType: TaskType<*, *>, newConfig: TaskTypeConfig) {
        val configs = getTaskConfigs(taskType)
        if (configs.any { it.name == newConfig.name }) {
            throw IllegalArgumentException("A configuration with the name '${newConfig.name}' already exists for task type '${taskType.name}'")
        }
        taskSettings[newConfig.task_type!!] = newConfig
    }

    fun removeTaskConfig(taskType: TaskType<*, *>, selectedConfig: String) {
        val configs = getTaskConfigs(taskType)
        val configToRemove = configs.firstOrNull { it.name == selectedConfig }
        if (configToRemove != null) {
            taskSettings.remove(configToRemove.task_type)
        }
    }
}

/**
 * Custom serializer for ApiChatModel that only serializes the model name
 */
class ApiChatModelSerializer : JsonSerializer<ApiChatModel>() {
    override fun serialize(value: ApiChatModel?, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value == null) {
            gen.writeNull()
        } else {
            gen.writeString(value.model?.modelName ?: value.model?.name)
        }
    }
}

/**
 * Custom deserializer for ApiChatModel that resolves the model from its name
 */
class ApiChatModelDeserializer : JsonDeserializer<ApiChatModel>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ApiChatModel? {
        val modelName = p.readValueAs(String::class.java) ?: return null
        val userSettings = com.simiacryptus.cognotik.platform.ApplicationServices
            .fileApplicationServices()
            .userSettingsManager
            .getUserSettings()
        val model = userSettings.apis.flatMap {
            it.provider?.getChatModels(it.key ?: "", it.baseUrl) ?: listOf()
        }.firstOrNull {
            it.modelName == modelName || it.name == modelName
        } ?: return null
        val apiData = userSettings.apis.firstOrNull {
            it.provider == model.provider
        }
        return ApiChatModel(model, apiData)
    }
}