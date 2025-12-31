package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path

open class ProtocolMode(
    task: SessionTask,
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser,
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)
) : CognitiveMode<ProtocolModeConfig>(
    task,
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(ProtocolMode::class.java)
    private var isRunning = false
    private var transcriptStream: FileOutputStream? = null
    private val history = mutableListOf<String>()

    override fun initialize() {
        log.debug("Initializing ProtocolMode")
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        if (!isRunning) {
            isRunning = true
            startProtocolSession(userMessage)
        } else {
            task.echo("User: $userMessage".renderMarkdown)
            history.add("User Message: $userMessage")
        }
    }

    private fun startProtocolSession(userMessage: String) {
        task.echo(userMessage.renderMarkdown)
        transcriptStream = transcript(task)
        
        this.task.ui.pool.execute {
            try {
                task.complete()
                val coordinator = this.task.ui.dataStorage?.let {
                    TaskOrchestrator(
                        user = user,
                        session = session,
                        dataStorage = it,
                        root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                            ?: this.task.ui.dataStorage!!.getSessionDir(user, session).toPath() ?: File(".").toPath()
                    )
                } ?: throw IllegalStateException("Coordinator could not be initialized")

                val protocol = if (config.protocolFile != null) {
                    loadPrePlanned(userMessage, coordinator.root, task)
                } else {
                    val p = defineProtocol(userMessage)
                    try {
                        val protocolFile = coordinator.root.resolve("protocol.json").toFile()
                        JsonUtil.toJson(p).let { json ->
                            protocolFile.writeText(json)
                            task.add("Protocol saved to [${protocolFile.name}](${task.linkTo("protocol.json")})")
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to save protocol json", e)
                    }
                    p
                }
                writeToTranscript("# Protocol Definition\n\n```json\n${JsonUtil.toJson(protocol)}\n```\n\n")
                
                val protocolDisplay = TabbedDisplay(task)
                protocolDisplay["Protocol"] = task.ui.newTask(false).apply {
                    complete(renderMarkdown("```json\n${JsonUtil.toJson(protocol)}\n```"))
                }.placeholder

                var currentStateName: String? = protocol.initialState
                var iteration = 0
                val maxIterations = config.maxIterations

                while (currentStateName != null && iteration++ < maxIterations) {
                    val currentState = protocol.states.find { it.name == currentStateName }
                        ?: throw IllegalStateException("State $currentStateName not found")
                    
                    writeToTranscript("## State: ${currentState.name}\n\n")
                    val stateTask = task.ui.newTask(false)
                    protocolDisplay["${iteration}. ${currentState.name}"] = stateTask.placeholder
                    stateTask.add(renderMarkdown("### State: ${currentState.name}\n\n**Instructions:** ${currentState.instructions}"))

                    var statePassed = false
                    var retryCount = 0
                    val maxRetries = config.maxRetries
                    var nextState: String? = null

                    while (!statePassed && retryCount++ < maxRetries) {
                        if (retryCount > 1) {
                            stateTask.add(renderMarkdown("#### Retry $retryCount"))
                            writeToTranscript("### Retry $retryCount\n\n")
                        }

                        // 1. Execute Action
                        val actionTask = executeStateAction(currentState, userMessage, coordinator, stateTask)
                        val actionResult = actionTask.second
                        val actionConfig = actionTask.first
                        
                        writeToTranscript("### Action\n\n**Task:** ${actionConfig.task_description}\n\n**Result:**\n$actionResult\n\n")

                        // 2. Validate
                        val validation = validateState(currentState, actionConfig, actionResult, stateTask)
                        writeToTranscript("### Validation\n\n**Passed:** ${validation.passed}\n\n**Feedback:** ${validation.feedback}\n\n")
                        
                        stateTask.add(renderMarkdown("**Validation:** ${if (validation.passed) "PASSED" else "FAILED"}\n\n${validation.feedback}"))

                        if (validation.passed) {
                            statePassed = true
                            nextState = validation.nextState
                            history.add("State ${currentState.name} completed successfully. Action: ${actionConfig.task_description}. Result: $actionResult")
                        } else {
                            history.add("State ${currentState.name} failed. Feedback: ${validation.feedback}")
                        }
                    }

                    if (statePassed) {
                        currentStateName = nextState
                    } else {
                        stateTask.add(renderMarkdown("State ${currentState.name} failed after $maxRetries retries."))
                        writeToTranscript("State ${currentState.name} failed after max retries.\n")
                        break
                    }
                }
                
                task.complete("Protocol session completed.")
            } catch (e: Throwable) {
                log.error("Error in ProtocolMode", e)
                task.error(e)
            } finally {
                isRunning = false
                transcriptStream?.close()
            }
        }
    }

    private fun defineProtocol(userMessage: String): ProtocolDefinition {
        val prompt = """
            Define a strict protocol (state machine) to achieve the user's request.
            The protocol can have branching, loops, or be linear.
            Each state must have clear instructions and validation criteria.
            The validation criteria will be used by a referee agent to determine success and the next state.
            Explicitly mention in validation criteria which state to transition to under what conditions.
        """.trimIndent()

        return ParsedAgent(
            name = "ProtocolDefiner",
            resultClass = ProtocolDefinition::class.java,
            prompt = prompt,
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage)).obj
    }

    private fun executeStateAction(
        state: ProtocolState,
        userMessage: String,
        coordinator: TaskOrchestrator,
        uiTask: SessionTask
    ): Pair<TaskExecutionConfig, String> {
        Tasks.initDescriber(orchestrationConfig, describer)
        val prompt = """
            You are executing the state '${state.name}' of a protocol.
            Instructions: ${state.instructions}
            
            Choose the appropriate task to perform this step.
            Available task types:
            ${TaskType.getAvailableTaskTypes(orchestrationConfig).joinToString(", ") { it.name }}
        """.trimIndent()

        val tasks = ParsedAgent(
            name = "StateExecutor",
            resultClass = Tasks::class.java,
            prompt = prompt,
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer,
            parserPrompt = ("Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(orchestrationConfig)
                .joinToString("\n\n") { taskType ->
                    "${taskType.name}:\n  ${
                        describer.describe(taskType.executionConfigClass).trim().trimIndent().indent("  ")
                    }".trim()
                })
        ).answer(listOf(userMessage) + history).obj

        val taskConfig = tasks.tasks?.firstOrNull() ?: throw IllegalStateException("No task generated for state ${state.name}")
        
        val result = StringBuilder()
        val taskImpl = TaskType.getImpl(orchestrationConfig, taskConfig)
        
        val executionTask = uiTask.ui.newTask(false)
        uiTask.add(executionTask.placeholder)
        
        executionTask.add(renderMarkdown("Executing: ${taskConfig.task_description}"))
        
        taskImpl.run(
            agent = coordinator,
            messages = listOf(userMessage),
            task = executionTask,
            resultFn = { result.append(it) },
            orchestrationConfig = orchestrationConfig
        )
        
        return taskConfig to result.toString()
    }

    private fun validateState(
        state: ProtocolState,
        taskConfig: TaskExecutionConfig,
        result: String,
        uiTask: SessionTask
    ): ValidationResult {
        val prompt = """
            You are the Referee.
            Current State: ${state.name}
            Validation Criteria: ${state.validationCriteria}
            
            Task Executed: ${taskConfig.task_description}
            Task Result:
            $result
            
            Did the task result satisfy the validation criteria?
            If yes, determine the next state based on the criteria.
            If the protocol is finished, the next state should be null.
            Provide feedback explaining your decision.
        """.trimIndent()

        return ParsedAgent(
            name = "Referee",
            resultClass = ValidationResult::class.java,
            prompt = prompt,
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(emptyList()).obj
    }

    override fun contextData(): List<String> = history
    private fun loadPrePlanned(userMessage: String, root: Path, task: SessionTask): ProtocolDefinition {
        val parsedConfig = parseConfig(userMessage, root.toString(), task)
        task.add("Loading protocol from `${parsedConfig.protocolFile}` with variables: ${parsedConfig.variables}")
        val protocolFile = root.resolve(parsedConfig.protocolFile!!).toFile()
        if (!protocolFile.exists()) {
            throw IllegalArgumentException("Protocol file not found: ${protocolFile.absolutePath}")
        }
        val rawJson = protocolFile.readText()
        val genericProtocol: MutableMap<String, Any> = JsonUtil.fromJson(rawJson, MutableMap::class.java)
        val processedProtocol = replaceVariables(genericProtocol, parsedConfig.variables)
        return JsonUtil.fromJson(JsonUtil.toJson(processedProtocol), ProtocolDefinition::class.java)
    }

    private fun parseConfig(message: String, root: String, task: SessionTask): ProtocolModeConfig {
        val availableFiles = getAvailableFiles(Path(root))
            .filter { it.endsWith(".json") }
            .joinToString("\n") { "      - $it" }
        val agent = ParsedAgent(
            name = "ProtocolConfigParser",
            resultClass = ProtocolModeConfig::class.java,
            exampleInstance = ProtocolModeConfig(
                protocolFile = config.protocolFile,
                variables = config.variables
            ),
            prompt = """
                Analyze the user request to identify the protocol file to use and the variables to substitute.
                The user wants to execute a pre-defined protocol stored in a JSON file.
                1. Identify the JSON file mentioned. If not explicitly mentioned, look for '${config.protocolFile}' or the most relevant file in the list below.
                2. Extract any other parameters or instructions as variables. The keys should match placeholders likely found in the protocol (e.g., {{key}}).
                Available JSON files:
                $availableFiles
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = 0.1,
            describer = describer
        )
        return agent.answer(listOf(message)).obj
    }

    private fun replaceVariables(node: Any?, variables: Map<String, String>): Any? {
        return when (node) {
            is String -> {
                var result: String = node
                variables.forEach { (k, v) ->
                    result = result.replace("{{$k}}", v)
                }
                result
            }

            is Map<*, *> -> node.entries.associate { (k, v) -> k to replaceVariables(v, variables) }
            is List<*> -> node.map { replaceVariables(it, variables) }
            else -> node
        }
    }


    private fun transcript(task: SessionTask): FileOutputStream? {
        val transcriptFile = "protocol_mode_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a>"
        )
        return markdownTranscript
    }

    private fun writeToTranscript(content: String) {
        transcriptStream?.write(content.toByteArray())
        transcriptStream?.flush()
    }

    data class ProtocolDefinition(
        @Description("The list of states in the protocol")
        val states: List<ProtocolState>,
        @Description("The name of the initial state")
        val initialState: String
    )

    data class ProtocolState(
        @Description("Unique name of the state")
        val name: String,
        @Description("Instructions for the agent in this state")
        val instructions: String,
        @Description("Criteria for the referee to validate success and decide the next state")
        val validationCriteria: String
    )

    data class ValidationResult(
        @Description("Whether the criteria were met")
        val passed: Boolean,
        @Description("Feedback or reason for failure/success")
        val feedback: String,
        @Description("The name of the next state to transition to, or null if terminal")
        val nextState: String?
    )

    companion object {
        val inputCnt = 1
    }
}
class ProtocolModeConfig(
    var maxIterations: Int = 20,
    var maxRetries: Int = 3,
    var protocolFile: String? = null,
    var variables: Map<String, String> = emptyMap()
) : CognitiveModeConfig()