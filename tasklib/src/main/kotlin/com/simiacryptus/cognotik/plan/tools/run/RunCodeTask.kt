package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.oneAtATime
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

open class RunCodeTask<T : RunCodeTask.RunCodeTaskExecutionConfigData, U : RunCodeTask.RunCodeTaskTypeConfig>(
  orchestrationConfig: OrchestrationConfig,
  planTask: T?,
) : AbstractTask<T, U>(
  orchestrationConfig,
  planTask
) {
  open fun symbols(): Map<String, Any> = emptyMap()

  open class RunCodeTaskTypeConfig(
    task_type: String = RunCode.name,
    var codeRuntime: CodeRuntimes? = null,
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
    @Description("A detailed description of the task's purpose")
    task_description: String? = null,
    @Description("List of task IDs that must complete before this task starts")
    task_dependencies: List<String>? = null,
    @Description("The execution state/history of the task")
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
          * Useful for data processing, file system operations, or complex calculations.
          * Provide a clear 'goal' for the code to achieve.
          * The interpreter has access to the local workspace.
          * Results and console output will be returned to the context.
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
    val semaphore = Semaphore(0)
    val typeConfig = typeConfig ?: throw RuntimeException()
    val model = (typeConfig.model?.let { it.instance(orchestrationConfig.user) }
      ?: defaultSmart).getChildClient(task)
    val transcript = task.newUserFileStream(transcriptFile())
    log.info("Starting RunCodeTask for goal: ${executionConfig?.goal?.take(50)}...")

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
        val lang = runtime.name.lowercase().replace("runtime", "")
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
        val tabs = TabbedDisplay(task)
        tabs["Code"] = "```$lang\n${response.code}\n```".renderMarkdown()
        tabs["Result"] = "```\n${response.result.resultValue}\n```".renderMarkdown()
        tabs["Output"] = "```\n${response.result.resultOutput}\n```".renderMarkdown()


        if (orchestrationConfig.autoFix) {
          transcript?.write("## Auto-Applying Execution\n".toByteArray())
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
          transcript?.write("## User Action: Continue\n".toByteArray())
          transcript?.flush()
          val finalOutput =
            "## Execution Result\n* Code executed successfully.\n* Result: `${response.result.resultValue}`"
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
          transcript?.write("## Auto-fix Execution Completed\n".toByteArray())
          transcript?.flush()
          response.let {
            "## Execution Result\n* Code executed automatically.\n* Result: `${response.result.resultValue}`\n* Output: `${
              response.result.resultOutput?.take(
                200
              )
            }`"
          }.apply { resultFn(this) }
          semaphore.release()
        }
        return result
      }


    }


    try {
      codingAgent.start(
        codingAgent.codeRequest(
          messages.map { it to ModelSchema.Role.user } + listOf(
            (executionConfig?.goal ?: "") to ModelSchema.Role.user,
          )
        )
      )
      semaphore.acquire()
    } catch (e: Throwable) {
      // Triple Log Rule
      task.error(e)
      log.error("Error in RunCodeTask: ${e.message}", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      throw e
    } finally {
      transcript?.write("\n## Task Completed\n".toByteArray())
      transcript?.flush()
      transcript?.close()
      task.complete()
    }
  }

  open fun describer(): TypeDescriber = AbbrevWhitelistYamlDescriber(
    "com.simiacryptus"
  )

  companion object {
      private val log = LoggerFactory.getLogger(RunCodeTask::class.java)

    @JvmStatic
    val RunCode = TaskType(
      name = "RunCode",
      category = "Execution",
      taskClass = RunCodeTask::class.java,
      executionConfigClass = RunCodeTaskExecutionConfigData::class.java,
      taskSettingsClass = RunCodeTaskTypeConfig::class.java,
      description = "Execute code snippets with oversight",
      tooltipHtml = """
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