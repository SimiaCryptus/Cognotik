package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.oneAtATime
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

open class RunCodeTask<T : RunCodeTask.RunCodeTaskExecutionConfigData, U:RunCodeTask.RunCodeTaskTypeConfig>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?,
) : AbstractTask<T,U>(
    orchestrationConfig,
    planTask
) {
    open fun symbols(): Map<String, Any> = emptyMap()

    open class RunCodeTaskTypeConfig(
        task_type: String = RunCode.name,
        val codeRuntime: CodeRuntimes? = null,
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name,
        model = model,
    )

    open class RunCodeTaskExecutionConfigData(
        @Description("The task or goal to be accomplished")
        var goal: String? = null,
        @Description("The relative file path of the working directory")
        var workingDir: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
        task_type: String = RunCode.name
    ) : TaskExecutionConfig(
        task_type = task_type,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        val language = typeConfig?.codeRuntime?.name ?: "code"
        return """
        RunCode - Use a $language interpreter to solve and complete the user's request.
          * Do not directly write code (yet)
          * Include detailed technical requirements for the needed solution
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val autoRunCounter = AtomicInteger(0)
        val transcript = task.transcript()
        val semaphore = Semaphore(0)
        val typeConfig = typeConfig ?: throw RuntimeException()
        val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
            ?: defaultSmart).getChildClient(task)

        val runtime = typeConfig.codeRuntime ?: CodeRuntimes.GroovyRuntime // Kotlin has issues running within IntelliJ

        val symbols = symbols()
        val codingAgent = object : CodingTask<CodeRuntime>(
            dataStorage = agent.dataStorage,
            session = agent.session,
            user = agent.user,
            ui = task.ui,
            codeRuntime = CodeRuntimes.getRuntime(
                runtimeType = runtime,
                params = mapOf(
                    "workingDir" to (
                            orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath }
                                ?: orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath }
                                ?: File(".").absolutePath
                            ),
                ) + symbols),
            symbols = symbols,
            temperature = orchestrationConfig.temperature,
            details = """
                Code a solution using ${runtime.name} to the user's request.
            """.trimIndent(),
            model = model,
            mainTask = task,
            retryable = false,
            describer = describer(),
        ) {
            override fun displayFeedback(
                task: SessionTask,
                request: CodeAgent.CodeRequest,
                response: CodeAgent.CodeResult
            ) {
                val formText = StringBuilder()
                transcript?.write(
                    """
                    ## Code Execution
                    <details><summary>Request Messages</summary>
                    
                    ```${runtime.name.lowercase().replace("runtime", "")}
                    ${request.messages}
                    ```
                    </details>
                    
                    <details><summary>Execution Result</summary>
                    
                    **Result Value:**
                    ```
                    ${response.result.resultValue}
                    ```
                    
                    **Output:**
                    ```
                    ${response.result.resultOutput}
                    ```
                    </details>
                    
                    """.trimIndent().toByteArray()
                )
                val markdown = """
                    ### Code
                    $TRIPLE_TILDE${runtime.name.lowercase().replace("runtime", "")}
                    ${request.messages}
                    $TRIPLE_TILDE
                    ### Result
                    $TRIPLE_TILDE
                    ${response.result.resultValue}
                    $TRIPLE_TILDE
                    ### Output
                    $TRIPLE_TILDE
                    ${response.result.resultOutput}
                    $TRIPLE_TILDE
                """.trimIndent()
                task.expandable("Execution Details", MarkdownUtil.renderMarkdown(markdown, ui = task.ui))


                if (orchestrationConfig.autoFix) {
                    if (autoRunCounter.incrementAndGet() <= 1) {
                        // Auto-fix: Execute immediately
                        responseAction(task, "Running...", null, formText) {
                            execute(task, response, request)
                        }
                    }
                    task.complete()
                    return
                }
                // Interactive Mode
                var formHandle: StringBuilder? = null
                val buttonsHtml = StringBuilder()
                if (super.canPlay) {
                    buttonsHtml.append(super.playButton(task, request, response, formText) { formHandle!! })
                }
                buttonsHtml.append(ui.hrefLink("Continue", "href-link play-button") {
                    transcript?.write("## User Action: Continue\n\n".toByteArray())
                    transcript?.flush()
                    val finalOutput =
                        "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n## Output\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n"
                    resultFn(finalOutput)
                    semaphore.release()
                })
                val feedbackHtml = ui.textInput(oneAtATime { feedback: String ->
                    transcript?.write("## User Feedback\n$feedback\n\n".toByteArray())
                    transcript?.flush()
                    super.responseAction(task, "Revising...", formHandle, formText) {
                        super.feedback(task, feedback, request, response)
                    }
                })
                val html = """
                    <div class="d-flex flex-row gap-2">
                        $buttonsHtml
                    </div>
                    <div class="mt-2">
                        $feedbackHtml
                    </div>
                """.trimIndent()
                formHandle = task.add(html, additionalClasses = "reply-message")
                formText.append(formHandle.toString())
                task.complete()
            }

            override fun execute(
                task: SessionTask,
                response: CodeAgent.CodeResult
            ): String {
                val result = super.execute(task, response)
                if (orchestrationConfig.autoFix) {
                    transcript?.write("## Auto-fix Execution\n\n".toByteArray())
                    transcript?.flush()
                    response.let {
                        "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n## Result\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n## Output\n$TRIPLE_TILDE\n${response.result.resultOutput}\n$TRIPLE_TILDE\n"
                    }.apply { resultFn(this) }
                    semaphore.release()
                }
                return result
            }


        }


        codingAgent.start(
            codingAgent.codeRequest(
                messages.map { it to ModelSchema.Role.user } + listOf(
                    (executionConfig?.goal ?: "") to ModelSchema.Role.user,
                )
            )
        )
        try {
            semaphore.acquire()
        } catch (e: Throwable) {
            task.error(e)
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n\n".toByteArray())
            log.error("Error in RunCodeTask", e)
        } finally {
            transcript?.write("\n## Task Completed\n".toByteArray())
            transcript?.flush()
            task.complete()
        }
    }

    open fun describer(): TypeDescriber = AbbrevWhitelistYamlDescriber(
        "com.simiacryptus"
    )

    companion object {
        private val log = LoggerFactory.getLogger(RunCodeTask::class.java)
        val RunCode = TaskType(
            "RunCode",
            "Execution & Automation",
            RunCodeTask::class.java,
            RunCodeTaskExecutionConfigData::class.java,
            RunCodeTaskTypeConfig::class.java,
            "Execute code snippets with oversight",
            """
          Executes code snippets in an interactive environment.
          <ul>
            <li>User-approved code execution</li>
            <li>Working directory configuration</li>
            <li>Output capture and formatting</li>
            <li>Error handling and reporting</li>
            <li>Interactive result review</li>
          </ul>
        """,
        )

    }
}