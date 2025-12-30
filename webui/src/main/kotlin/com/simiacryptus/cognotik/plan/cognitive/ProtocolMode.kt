package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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

                val protocol = defineProtocol(userMessage)
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
                            history.add("State ${currentState.name} completed successfully. Action: ${actionConfig.task_description}. Result: $actionResult")
                        } else {
                            history.add("State ${currentState.name} failed. Feedback: ${validation.feedback}")
                        }
                    }

                    if (statePassed) {
                        currentStateName = currentState.nextState
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
            If the user asks for TDD, use a Red -> Green -> Refactor cycle.
            If the user asks for documentation, use Read -> Draft -> Verify.
            Otherwise, design a logical flow of states.
            Each state must have clear instructions and validation criteria.
            The validation criteria will be used by a referee agent to check if the state exit condition is met.
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
            Provide feedback explaining why it passed or failed.
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
        @Description("Criteria for the referee to validate transition")
        val validationCriteria: String,
        @Description("The name of the next state on success, or null if terminal")
        val nextState: String?
    )

    data class ValidationResult(
        @Description("Whether the criteria were met")
        val passed: Boolean,
        @Description("Feedback or reason for failure/success")
        val feedback: String
    )

    companion object {
        val inputCnt = 1
    }
}
class ProtocolModeConfig(
    var maxIterations: Int = 20,
    var maxRetries: Int = 3
) : CognitiveModeConfig()