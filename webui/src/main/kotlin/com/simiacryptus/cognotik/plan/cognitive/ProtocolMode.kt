package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

open class ProtocolMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser,
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)
) : CognitiveMode<ProtocolModeConfig>(
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(ProtocolMode::class.java)
    private var isRunning = false
    private val history = mutableListOf<String>()

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        if (!isRunning) {
            isRunning = true
            startProtocolSession(task, userMessage)
        } else {
            task.echo("User: $userMessage".renderMarkdown(true))
            history.add("User Message: $userMessage")
        }
    }

    private fun startProtocolSession(task : SessionTask, userMessage: String) {
        task.echo(userMessage.renderMarkdown(true))
        val transcript = task.transcript()
        fun writeToTranscript(content: String) {
            transcript?.write(content.toByteArray())
            transcript?.flush()
        }

        task.ui.pool.execute {
            try {
                task.complete()
                val coordinator = task.ui.dataStorage?.let {
                    TaskOrchestrator(
                      user = user,
                      session = session,
                      dataStorage = it,
                      root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                          ?: task.ui.dataStorage!!.getSessionDir(user, session).toPath() ?: File(".").toPath()
                    )
                } ?: throw IllegalStateException("Coordinator could not be initialized")

                val protocol = if (config.protocolFile != null) {
                    loadPrePlanned(userMessage, coordinator.root, task)
                } else {
                    val definer = { msgs: List<String> -> defineProtocol(task, msgs) }
                    val p = if (orchestrationConfig.autoFix) {
                        definer(listOf(userMessage))
                    } else {
                        Discussable(
                            task = task,
                            heading = "Protocol Definition",
                            userMessage = { userMessage },
                            initialResponse = { definer(listOf(it)) },
                            outputFn = { "```json\n${JsonUtil.toJson(it)}\n```".renderMarkdown() },
                            reviseResponse = { history ->
                                definer(history.map { "${it.second}: ${it.first}" })
                            }
                        ).call()
                    }
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
                }!!
                writeToTranscript("# Protocol Definition\n\n```json\n${JsonUtil.toJson(protocol)}\n```\n\n")

                val protocolDisplay = TabbedDisplay(task)
              protocolDisplay["Protocol"] = "```json\n${JsonUtil.toJson(protocol)}\n```".renderMarkdown()

                var currentStateName: String? = protocol.initialState
                var iteration = 0
                val maxIterations = config.maxIterations

                while (currentStateName != null && iteration++ < maxIterations) {
                    val currentState = protocol.states.find { it.name == currentStateName }
                        ?: throw IllegalStateException("State $currentStateName not found")

                    writeToTranscript("## State: ${currentState.name}\n\n")
                    val stateTask = task.newTask()
                    protocolDisplay["${iteration}. ${currentState.name}"] = stateTask.placeholder
                    stateTask.header("State: ${currentState.name}", level = 3)
                    stateTask.add("**Instructions:** ${currentState.instructions}".renderMarkdown())

                    var statePassed = false
                    var retryCount = 0
                    val maxRetries = config.maxRetries
                    var nextState: String? = null

                    while (!statePassed && retryCount++ < maxRetries) {
                        if (retryCount > 1) {
                            stateTask.header("Retry $retryCount", level = 4)
                            writeToTranscript("### Retry $retryCount\n\n")
                        }

                        // 1. Execute Action
                        val taskConfig = if (orchestrationConfig.autoFix) {
                            selectTask(task, currentState, userMessage, history)
                        } else {
                            Discussable(
                                task = stateTask,
                                heading = "Task Selection",
                                userMessage = { "Select task for state: ${currentState.name}" },
                                initialResponse = { selectTask(task, currentState, userMessage, history) },
                                outputFn = { "Selected Task: **${it.task_description}**".renderMarkdown() },
                                reviseResponse = { h ->
                                    selectTask(
                                        task, currentState,
                                        userMessage,
                                        history + h.map { "${it.second}: ${it.first}" })
                                }
                            ).call()
                        }

                        val result = StringBuilder()
                        val taskImpl = orchestrationConfig.getImpl(taskConfig)
                        val executionTask = stateTask.newTask()
                        stateTask.add(executionTask.placeholder)
                        executionTask.add("Executing: ${taskConfig?.task_description}".renderMarkdown())

                        taskImpl.run(
                            agent = coordinator,
                            messages = listOf(userMessage),
                            task = executionTask,
                            resultFn = { result.append(it) },
                            orchestrationConfig = orchestrationConfig
                        )
                        val actionResult = result.toString()

                        writeToTranscript("### Action\n\n**Task:** ${taskConfig!!.task_description}\n\n**Result:**\n$actionResult\n\n")

                        // 2. Validate
                        val validation = if (orchestrationConfig.autoFix) {
                            validateState(task, currentState, taskConfig!!, actionResult)
                        } else {
                            Discussable(
                                task = stateTask,
                                heading = "Validation",
                                userMessage = { "Validate result for state: ${currentState.name}" },
                                initialResponse = { validateState(task, currentState, taskConfig!!, actionResult) },
                                outputFn = { "Passed: ${it.passed}\nFeedback: ${it.feedback}".renderMarkdown() },
                                reviseResponse = { h ->
                                    validateState(
                                        task, currentState,
                                        taskConfig!!, actionResult, h.map { "${it.second}: ${it.first}" })
                                }
                            ).call()
                        }!!
                        writeToTranscript("### Validation\n\n**Passed:** ${validation.passed}\n\n**Feedback:** ${validation.feedback}\n\n")

                        val statusClass = if (validation.passed) "text-success" else "text-danger"
                        stateTask.add(
                            "<b>Validation:</b> ${if (validation.passed) "PASSED" else "FAILED"}",
                            additionalClasses = statusClass
                        )
                        stateTask.add(validation.feedback.renderMarkdown())

                        if (validation.passed) {
                            statePassed = true
                            nextState = validation.nextState
                            history.add("State ${currentState.name} completed successfully. Action: ${taskConfig!!.task_description}. Result: $actionResult")
                        } else {
                            history.add("State ${currentState.name} failed. Feedback: ${validation.feedback}")
                        }
                    }

                    if (statePassed) {
                        currentStateName = nextState
                    } else {
                        stateTask.add(
                            "State ${currentState.name} failed after $maxRetries retries.",
                            additionalClasses = "text-danger"
                        )
                        writeToTranscript("State ${currentState.name} failed after max retries.\n")
                        break
                    }
                }

                task.add("Protocol session completed.")
                task.complete()
            } catch (e: Throwable) {
                log.error("Error in ProtocolMode", e)
                task.error(e)
            } finally {
                isRunning = false
                transcript?.close()
            }
        }
    }

    private fun defineProtocol(task : SessionTask, messages: List<String>): ProtocolDefinition {
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
        ).answer(messages).obj
    }

    private fun selectTask(
        task : SessionTask,
        state: ProtocolState,
        userMessage: String,
        history: List<String>
    ): TaskExecutionConfig {
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

        return tasks.tasks?.firstOrNull() ?: throw IllegalStateException("No task generated for state ${state.name}")
    }

    private fun validateState(
      task : SessionTask,
      state: ProtocolState,
      taskConfig: TaskExecutionConfig,
      result: String,
      messages: List<String> = emptyList()
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
        ).answer(messages).obj
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