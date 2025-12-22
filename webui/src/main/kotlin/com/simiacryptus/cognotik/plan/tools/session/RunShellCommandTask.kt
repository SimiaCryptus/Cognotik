package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.apps.code.CodingTask
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.interpreter.ProcessCodeRuntime
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class RunShellCommandTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: RunShellCommandTaskExecutionConfigData?
) : AbstractTask<RunShellCommandTask.RunShellCommandTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class RunShellCommandTaskExecutionConfigData(
        @Description("The shell command to be executed")
        val command: String? = null,
        @Description("The relative file path of the working directory")
        val workingDir: String? = null,
        @Description("Timeout in minutes for command execution (default: 15)")
        val timeoutMinutes: Long? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = RunShellCommand.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            // Validate command is not null or blank
            if (command.isNullOrBlank()) {
                return "Command cannot be null or blank"
            }

            // Validate timeout is positive if specified
            if (timeoutMinutes != null && timeoutMinutes <= 0) {
                return "Timeout must be a positive number of minutes"
            }

            // Call parent validation
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment() = """
    RunShellCommand - Execute ${orchestrationConfig.language ?: "bash"} shell commands and provide the output
      ** Specify the command to be executed, or describe the task to be performed
      ** Optionally specify a working directory for the command execution
      ** Optionally specify a timeout in minutes (default: 15)
    """.trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val autoRunCounter = AtomicInteger(0)
        val semaphore = Semaphore(0)
        val markdownTranscript = task.transcript()
        val typeConfig = typeConfig ?: throw RuntimeException()
        val chatter = (typeConfig.model?.let { this.orchestrationConfig.instance(it) }
            ?: this.defaultChatter).getChildClient(task)
        val planTask = this.executionConfig
        val shellCommandActor = CodeAgent(
            name = "RunShellCommand",
            codeRuntimeClass = ProcessCodeRuntime::class,
            details = """
        Execute the following shell command(s) and provide the output. Ensure to handle any errors or exceptions gracefully.
        Note: This task is for running simple and safe commands. Avoid executing commands that can cause harm to the system or compromise security.
        """.trimIndent(),
            symbols = mapOf<String, Any>(
                "env" to (this.orchestrationConfig.env ?: emptyMap<String, String>()),
                "workingDir" to ((planTask?.workingDir?.let { File(it).absolutePath }
                    ?: File(this.orchestrationConfig.absoluteWorkingDir ?: ".").absolutePath)
                    ?.let { a -> this.orchestrationConfig.absoluteWorkingDir?.let { b -> File(b).resolve(a) } }
                    ?: this.orchestrationConfig.absoluteWorkingDir ?: "."),
                "language" to (this.orchestrationConfig.language ?: "bash"),
                "command" to (this.orchestrationConfig.shellCmd),
                "timeoutMinutes" to (planTask?.timeoutMinutes ?: 15L),
            ),
            model = chatter,
            temperature = this.orchestrationConfig.temperature,
            fallbackModel = chatter
        )
        val codingAgent = object : CodingTask<ProcessCodeRuntime>(
            dataStorage = agent.dataStorage,
            session = agent.session,
            user = agent.user,
            ui = task.ui,
            interpreter = shellCommandActor.codeRuntimeClass as KClass<ProcessCodeRuntime>,
            symbols = shellCommandActor.symbols,
            temperature = shellCommandActor.temperature,
            details = shellCommandActor.details,
            model = shellCommandActor.model,
            mainTask = task,
            retryable = false,
        ) {
            override fun execute(
                task: SessionTask,
                response: CodeAgent.CodeResult
            ): String {
                val result = super.execute(task, response) // Runs the interpreter, updates response.result
                if (orchestrationConfig.autoFix) {
                    val resultString =
                        "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n" +
                                "## Result\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n" + // STDOUT
                                "## Output\n$TRIPLE_TILDE\n${response.result.resultOutput}\n$TRIPLE_TILDE\n" // STDERR
                    markdownTranscript?.write(resultString.toByteArray())
                    markdownTranscript?.flush()
                    resultFn(resultString)
                    "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n" +
                            "## Result\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n" + // STDOUT
                            "## Output\n$TRIPLE_TILDE\n${response.result.resultOutput}\n$TRIPLE_TILDE\n" // STDERR
                    resultFn(resultString)
                    semaphore.release()
                }
                return result
            }

            override fun displayFeedback(
                task: SessionTask,
                request: CodeAgent.CodeRequest,
                response: CodeAgent.CodeResult
            ) {
                if (orchestrationConfig.autoFix && autoRunCounter.incrementAndGet() <= 1) {
                    super.responseAction(task, "Running...", null, StringBuilder()) {
                        this.execute(task, response, request) // Calls the overridden execute
                    }
                } else if (!orchestrationConfig.autoFix) {
                    // Manual feedback UI
                    val formText = StringBuilder()
                    var formHandle: StringBuilder? = null
                    @Suppress("AssignedValueIsNeverRead")
                    formHandle = task.add(
                        "<div>\n${
                            if (!super.canPlay) "" else super.playButton(
                                task,
                                request,
                                response,
                                formText
                            ) { formHandle!! }
                        }\n${
                            acceptButton(
                                response,
                                task
                            )
                        }\n</div>\n${ // Pass task to acceptButton if needed for consistency, or ensure response is sufficient
                            super.ui.textInput { feedback ->
                                super.responseAction(
                                    task,
                                    "Revising...", formHandle!!, formText
                                ) {
                                    super.feedback(
                                        task, feedback, request, response
                                    )
                                }
                            }
                        }", additionalClasses = "reply-message"
                    )
                    // Omitted potentially problematic lines:
                    // formText.append(formHandle.toString())
                    // formHandle.toString()
                }
                task.complete()
            }

            fun acceptButton(
                response: CodeAgent.CodeResult,
                task: SessionTask // Added task param for potential future use or consistency
            ): String {
                return ui.hrefLink("Accept", "href-link play-button") {
                    response.let {
                        "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n" +
                                "## Result\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n" +
                                "## Output\n$TRIPLE_TILDE\n${response.result.resultOutput}\n$TRIPLE_TILDE\n"
                    }.apply {
                        markdownTranscript?.write(this.toByteArray())
                        markdownTranscript?.flush()
                        resultFn(this)
                    }
                    semaphore.release()
                }
            }
        }
        codingAgent.start(
            codingAgent.codeRequest(
                messages.map { it to ModelSchema.Role.user } +
                        listOfNotNull(
                            this.executionConfig?.command?.takeIf { it.isNotBlank() }
                                ?.let { it to ModelSchema.Role.user }
                        )
            )
        )
        try {
            semaphore.acquire()
        } catch (e: Throwable) {
            log.warn("Error", e)
        } finally {
            markdownTranscript?.close()
        }
    }


    companion object {
        private val log = LoggerFactory.getLogger(RunShellCommandTask::class.java)
        val RunShellCommand = TaskType(
            "RunShellCommand",
            "Execution & Automation",
            RunShellCommandTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Execute shell commands safely",
            """
          Executes shell commands in a controlled environment.
          <ul>
            <li>Safe command execution handling</li>
            <li>Working directory configuration</li>
            <li>Output capture and formatting</li>
            <li>Error handling and reporting</li>
            <li>Interactive result review</li>
          </ul>
        """
        )

    }
}