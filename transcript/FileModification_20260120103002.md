# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/AutoFixTask.kt

```
package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.CmdPatchApp
import com.simiacryptus.cognotik.apps.PatchApp
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.io.path.exists

class AutoFixTask(
    orchestrationConfig: OrchestrationConfig, planTask: AutoFixTaskExecutionConfigData?
) : AbstractTask<AutoFixTask.AutoFixTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

    companion object {
        private val log = LoggerFactory.getLogger(AutoFixTask::class.java)
        @JvmStatic val AutoFix = TaskType(
            name = "AutoFix",
            category = "Execution",
            taskClass = AutoFixTask::class.java,
            executionConfigClass = AutoFixTaskExecutionConfigData::class.java,
            taskSettingsClass = AutoFixTaskTypeConfig::class.java,
            description = "Run a command and automatically fix any issues that arise",
            tooltipHtml = """
                    Executes a command and automatically fixes any issues that arise.
                    <ul>
                      <li>Specify commands and working directories</li>
                      <li>Supports multiple commands and directories</li>
                      <li>Interactive approval mode</li>
                      <li>Output diff formatting</li>
                    </ul>
                  """,
        )
    }

    class AutoFixTaskTypeConfig(
        name: String? = AutoFix.name,
        model: ApiChatModel? = null,
        var promptTemplate: String = """
  SelfHealing - Run a command and automatically fix any issues that arise
  * Specify the commands to be executed along with their working directories
  * Each command's working directory should be specified relative to the root directory
  * Provide the commands and their arguments in the 'commands' field
  * Each command should be a list of strings
  * Available commands:
  {executables}
        """.trimIndent()
    ) : TaskTypeConfig(AutoFix.name, name, model)

    class AutoFixTaskExecutionConfigData(
        @Description("The commands to be executed with their respective working directories") var commands: MutableList<CommandWithWorkingDir>? = ArrayList(),
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = AutoFix.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (commands.isNullOrEmpty()) {
                return "commands must not be null or empty"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class CommandWithWorkingDir(
        @Description("The command to be executed") var command: MutableList<String> = ArrayList(),
        @Description("The relative path of the working directory") var workingDir: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (command.isEmpty()) {
                return "command must not be empty"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        val executables = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
            .tools.flatMap { it.component1()?.getExecutables() ?: emptyList() }.distinct().sorted()
            .joinToString("\n") { "    * $it" }
        return typeConfig.promptTemplate.replace("{executables}", executables).trim()
    }

    override val typeConfig: AutoFixTaskTypeConfig
        get() = super.typeConfig as AutoFixTaskTypeConfig

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val semaphore = Semaphore(0)
        Retryable(task = task) {
            val subTask = task.newTask()

            fun execute() {
                subTask.ui.pool.submit {
                    val transcript = createTranscript(subTask)
                    subTask.add(transcript.second.renderMarkdown())
                    val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
                        ?: defaultSmart).getChildClient(subTask)
                    val markdownTranscript = transcript.first
                    try {
                        markdownTranscript?.write("## Self-Healing Task Execution\n\n".toByteArray())
                        markdownTranscript?.write("## Commands\n".toByteArray())
                        CmdPatchApp(
                            root = agent.root,
                            settings = PatchApp.Settings(
                                commands = this.executionConfig?.commands?.map { commandWithDir ->
                                    val alias = commandWithDir.command.firstOrNull()
                                    val toolExecutable = if (alias != null) {
                                        val tools =
                                            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
                                        tools.find { it.provider?.getExecutables()?.contains(alias) == true }?.let { toolData ->
                                            if (toolData.path != null) {
                                                toolData.provider!!.resolve(toolData.path).firstOrNull()?.let { File(it) }
                                            } else {
                                                toolData.resolve(alias)?.let { File(it) }
                                            }
                                        }
                                    } else null
                                    PatchApp.CommandSettings(
                                        executable = toolExecutable ?: when {
                                            alias.isNullOrBlank() -> null
                                            root.resolve(alias).exists() -> root.resolve(alias).toFile().absoluteFile
                                            File(alias).exists() -> File(alias).absoluteFile
                                            else -> null
                                        } ?: throw IllegalArgumentException("Command not found: $alias"),
                                        arguments = commandWithDir.command.drop(1).joinToString(" "),
                                        workingDirectory = (commandWithDir.workingDir?.let { agent.root.toFile().resolve(it) }
                                            ?: agent.root.toFile()).apply { mkdirs() },
                                        additionalInstructions = ""
                                    )
                                } ?: emptyList(),
                                autoFix = orchestrationConfig.autoFix,
                                includeLineNumbers = false,
                            ),
                            files = agent.files,
                            model = model,
                            parsingModel = defaultFast,
                            processor = orchestrationConfig.processor,
                        ).run(
                            task = subTask, model = model
                        ).apply {
                            markdownTranscript?.write("\n### Execution Result\n* **Exit Code:** ${this.exitCode}\n".toByteArray())
                            when {
                                this.exitCode == 0 -> {
                                    if (orchestrationConfig.autoFix) {
                                        resultFn("### Success\nAll commands executed successfully with exit code 0.")
                                        semaphore.release()
                                        subTask.complete()
                                    } else {
                                        subTask.add(
                                            subTask.ui.hrefLink("Accept & Continue", "btn btn-primary") {
                                                resultFn("### Success\nUser accepted command execution results.")
                                                semaphore.release()
                                                subTask.complete()
                                            }.renderMarkdown()
                                        )
                                    }
                                }

                                else -> {
                                    log.warn("Command failed with exit code ${this.exitCode}")
                                    subTask.add(
                                        subTask.ui.hrefLink("Ignore Error", "href-link cmd-button") {
                                            resultFn("### Warning\nCommands failed with exit code ${this.exitCode}, but error was ignored by user.")
                                            semaphore.release()
                                            subTask.complete()
                                        }.renderMarkdown()
                                    )
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        // Triple Log Rule: UI, SLF4J, and Transcript
                        subTask.error(e)
                        log.error("Critical error during AutoFixTask execution", e)
                        markdownTranscript?.write("\n### Execution Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())

                        if (orchestrationConfig.autoFix) {
                            semaphore.release()
                            subTask.complete()
                        }
                    } finally {
                        markdownTranscript?.close()
                    }
                }
            }
            if (orchestrationConfig.autoFix) {
                execute()
            } else {
                subTask.add(subTask.ui.hrefLink("▶ Run AutoFix", "btn btn-primary") {
                execute()
                }.renderMarkdown())
            }
            subTask.placeholder
        }
        try {
            semaphore.acquire()
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
        task.complete()
    }

    private fun createTranscript(task: SessionTask): Pair<FileOutputStream?, String> {
        val transcriptFile =
            this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        val html =
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(".md")
            }.pdf' target='_blank'>pdf</a>"
        return Pair(markdownTranscript, html)
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/CodingTask.kt

```
package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.agents.CodeAgent.CodeRequest
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale.getDefault

open class CodingTask<T : CodeRuntime>(
    val dataStorage: StorageInterface,
    val session: Session,
    val user: User?,
    val ui: SocketManager,
    val codeRuntime: T,
    val symbols: Map<String, Any>,
    val temperature: Double = 0.1,
    val details: String? = null,
    val model: ChatInterface,
    private val mainTask: SessionTask,
    val retryable: Boolean = true,
    val autoFix: Boolean = false,
    val describer: TypeDescriber = AbbrevWhitelistYamlDescriber(
        "com.simiacryptus"
    ),
) {


    open val canPlay by lazy {
        ApplicationServices.authorizationManager.isAuthorized(
            this::class.java, user, AuthorizationInterface.OperationType.Execute
        )
    }

    fun start(
        codeRequest: CodeRequest,
        task: SessionTask = mainTask,
    ) {
        val subTask = task.newTask()
        task.complete(subTask.placeholder)
        if (retryable) {
            Retryable(ui.newTask(true), process = { innerTask: SessionTask ->
                try {
                    val statusSB = innerTask.add("Running...")
                    displayCode(innerTask, codeRequest)
                    statusSB?.clear()
                } catch (e: Throwable) {
                  log.error("Error in CodingTask start (retryable)", e)
                    innerTask.error(e)
                  val transcript = innerTask.transcript()
                  try {
                    transcript?.write(
                      """
                            # Error
                            <details><summary>Stack Trace</summary>
                            ```text
                            ${e.stackTraceToString()}
                            ```
                            </details>
                        """.trimIndent().toByteArray()
                    )
                  } finally {
                    transcript?.close()
                  }
                } finally {
                    innerTask.complete()
                }
                Unit
            }.async(task.ui))
        } else {
            ui.pool.submit {
                try {
                    val statusSB = subTask.add("Running...")
                    displayCode(subTask, codeRequest)
                    statusSB?.clear()
                    subTask.update()
                } catch (e: Throwable) {
                  log.error("Error in CodingTask start", e)
                    subTask.error(e)
                  val transcript = subTask.transcript()
                  try {
                    transcript?.write(
                      """
                            # Error
                            <details><summary>Stack Trace</summary>
                            ```text
                            ${e.stackTraceToString()}
                            ```
                            </details>
                        """.trimIndent().toByteArray()
                    )
                  } finally {
                    transcript?.close()
                  }
                } finally {
                    subTask.complete()
                }
            }
        }
    }

    fun SessionTask.transcript(name: String = this.javaClass.simpleName): FileOutputStream? {
        val relativePath = "transcript/${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis())}.md"
        val (link, file) = Pair(linkTo(relativePath), resolveUserFile(relativePath))
        val markdownTranscript = file?.outputStream()
      add(
            "Writing $name to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>".renderMarkdown(),
            additionalClasses = "verbose"
        )
        return markdownTranscript
    }
    open fun codeRequest(messages: List<Pair<String, ModelSchema.Role>>) = CodeRequest(messages)

    fun displayCode(
        task: SessionTask,
        codeRequest: CodeRequest,
    ) {
        try {
            val lastUserMessage = codeRequest.messages.last { it.second == ModelSchema.Role.user }.first.trim()
            val codeAgent = CodeAgent(
                codeRuntime,
                symbols = symbols(task),
                temperature = temperature,
                details = details,
                model = model,
                fallbackModel = model,
                describer = describer,
            )
            val codeResponse: CodeAgent.CodeResult = if (lastUserMessage.startsWith("```")) {
                codeAgent.CodeResultImpl(
                    messages = codeAgent.chatMessages(codeRequest),
                    input = codeRequest,
                    givenCode = lastUserMessage.removePrefix("```").removeSuffix("```")
                )
            } else {
                codeAgent.answer(codeRequest)
            }
            displayCodeAndFeedback(task, codeRequest, codeResponse, codeAgent.language)
        } catch (e: Throwable) {
          log.error("Error in displayCode", e)
          task.error(e)
          val transcript = task.transcript()
          try {
            transcript?.write(
              """
                    # Error in displayCode
                    <details><summary>Stack Trace</summary>
                    
                    ```text
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
            )
          } finally {
            transcript?.close()
          }
        }
    }

    open fun symbols(task: SessionTask) = symbols + mapOf(
          "task" to task,
      )

    protected fun displayCodeAndFeedback(
        task: SessionTask,
        codeRequest: CodeRequest,
        response: CodeAgent.CodeResult,
        language: String,
    ) {
        try {
            val string = response.renderedResponse
                ?: "\n```${language.lowercase(getDefault())}\n${response.code.trim()}\n```\n"
            task.expanded("Code", string.renderMarkdown(true))
          val transcript = task.transcript()
          try {
            transcript?.write("# Generated Code\n$string\n".toByteArray())
          } finally {
            transcript?.close()
          }
            if (autoFix && canPlay) {
                execute(task, response, codeRequest)
            } else {
                displayFeedback(
                    task, codeRequest(
                    messages = codeRequest.messages + listOf(
                        response.code to ModelSchema.Role.assistant,
                    ).filter { it.first.isNotBlank() }), response
                )
            }
        } catch (e: Throwable) {
          log.error("Error in displayCodeAndFeedback", e)
            task.error(e)
          val transcript = task.transcript()
          try {
            transcript?.write(
              """
                    # Error in displayCodeAndFeedback
                    <details><summary>Stack Trace</summary>
                    
                    ```text
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
            )
          } finally {
            transcript?.close()
          }
        }
    }

    open fun displayFeedback(
        task: SessionTask, request: CodeRequest, response: CodeAgent.CodeResult
    ) {
        val formHandle = task.add("", additionalClasses = "reply-message")
        val formText = StringBuilder()
        formText.append("<div>\n")
        if (canPlay) {
            formText.append(playButton(task, request, response, formText) { formHandle!! })
        }
        formText.append("\n</div>\n")
        formText.append(ui.textInput { feedback ->
            responseAction(task, "Revising...", formHandle, formText) {
                feedback(task, feedback, request, response)
            }
        })
        formHandle?.append(formText)
        task.update()
        task.complete()
    }

    protected fun playButton(
        task: SessionTask,
        request: CodeRequest,
        response: CodeAgent.CodeResult,
        formText: StringBuilder,
        formHandle: () -> StringBuilder
    ) = if (!canPlay) "" else ui.hrefLink("▶ Run", "href-link play-button") {
        responseAction(task, "Running...", formHandle(), formText) {
            execute(task, response, request)
        }
    }.replace("<a class", """<a style="font-size: large;" class""")

    protected open fun responseAction(
        task: SessionTask, message: String, formHandle: StringBuilder?, formText: StringBuilder, fn: () -> Unit = {}
    ) {
        formHandle?.clear()
        task.update()
        val header = task.header(message, 2)
        try {
            fn()
        } finally {
            header?.clear()
            var revertButton: StringBuilder? = null
            val link = ui.hrefLink("↩", "href-link regen-button") {
                revertButton?.clear()
                formHandle?.append(formText)
                task.update()
            }
            revertButton = task.add(link)
            task.complete()
        }
    }

    protected open fun feedback(
        task: SessionTask, feedback: String, request: CodeRequest, response: CodeAgent.CodeResult
    ) {
        try {
            task.echo(feedback.renderMarkdown(true))
            start(
                codeRequest = codeRequest(
                    messages = request.messages + listOf(
                        response.code to ModelSchema.Role.assistant,
                        feedback to ModelSchema.Role.user,
                    ).filter { it.first.isNotBlank() }.map { it.first to it.second }), task = task
            )
        } catch (e: Throwable) {
          log.error("Error in feedback", e)
            task.error(e)
        }
    }

    protected fun execute(
        task: SessionTask,
        response: CodeAgent.CodeResult,
        request: CodeRequest,
    ) {
        try {
            val result = execute(task, response)
            displayFeedback(
                task, codeRequest(
                    messages = request.messages + listOf(
                        "Running...\n\n$result" to ModelSchema.Role.assistant,
                    ).filter { it.first.isNotBlank() }), response
            )
        } catch (e: Throwable) {
          log.error("Execution failed", e)
          task.error(e)
          val transcript = task.transcript()
          try {
            transcript?.write(
              """
                    # Execution Error
                    <details><summary>Stack Trace</summary>
                    ```text
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
            )
          } finally {
            transcript?.close()
          }

          val message = when {
                e is ValidatedObject.ValidationError -> e.message ?: "".renderMarkdown(true)
            e is FailedToImplementException -> "**Failed to Implement** \n\n${e.message}\n\n".renderMarkdown(true)
            else -> "**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n".renderMarkdown(true)
            }
            displayCode(
                task, CodeRequest(
                    messages = request.messages + listOf(
                        response.code to ModelSchema.Role.assistant,
                        message to ModelSchema.Role.system,
                    ).filter { it.first.isNotBlank() })
            )
        }
    }

    protected open fun execute(
        task: SessionTask, response: CodeAgent.CodeResult
    ): String {
        val transcript = task.transcript()

      try {
        val resultValue = response.result.resultValue
        val resultOutput = response.result.resultOutput
        transcript?.write(
          """
                # Execution Result
                <details><summary>Output & Value</summary>
                
                ## Output
                ```text
                $resultOutput
                ```
                ## Value
                ```text
                $resultValue
                ```
                </details>
                """.trimIndent().toByteArray()
        )
        val tabs = TabbedDisplay(task)
        tabs["Result"] = "```text\n$resultValue\n```".renderMarkdown()
        tabs["Output"] = "```text\n$resultOutput\n```".renderMarkdown()
        return when {
          resultValue.isBlank() || resultValue.trim().lowercase() == "null" -> "# Output\n```text\n$resultOutput\n```"
          else -> "# Result\n```\n$resultValue\n```\n\n# Output\n```text\n$resultOutput\n```"
        }
      } finally {
        transcript?.close()
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(CodeAgent::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/LanguageServerTask.kt

```
package com.simiacryptus.cognotik.plan.tools.code

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.Semaphore

class LanguageServerTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: LanguageServerTaskExecutionConfigData?
) : AbstractTask<LanguageServerTask.LanguageServerTaskExecutionConfigData, LanguageServerTask.LanguageServerTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class LanguageServerTaskTypeConfig(
        task_type: String = LanguageServer.name,
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name,
        model = model,
    )

    class LanguageServerTaskExecutionConfigData(
        @Description("The LSP operation to perform. Supported: 'diagnostics', 'definition', 'references', 'hover'")
        val action: String? = null,
        @Description("The relative path of the file to analyze")
        val file: String? = null,
        @Description("The line number (0-indexed) for position-based requests (definition, hover, references)")
        val line: Int? = null,
        @Description("The character offset (0-indexed) for position-based requests")
        val character: Int? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = LanguageServer.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        val supportedLangs = serverCommands.keys.joinToString(", ")
        return """
            LanguageServer - Query code intelligence (LSP)
              * Use to find definitions, references, or check for syntax errors (diagnostics).
              * Supported extensions: $supportedLangs
              * Actions: 'diagnostics' (file-wide), 'definition' (specific pos), 'references' (specific pos), 'hover' (specific pos).
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        val mapper = ObjectMapper()

        try {
            val filePath = executionConfig?.file ?: throw IllegalArgumentException("File path is required")
            val action = executionConfig.action ?: throw IllegalArgumentException("Action is required")
            val file = root.resolve(filePath).toFile()

            if (!file.exists()) throw IllegalArgumentException("File does not exist: $filePath")

            val extension = file.extension
            val command = serverCommands[extension]
                ?: throw IllegalArgumentException("No Language Server support defined for extension: .$extension")


            val executeLsp = {
                task.header("LSP Execution: $action", level = 3)
                val statusBuffer = task.add("Starting LSP for .$extension...")
                transcript?.write("# LSP Session\nCommand: ${command.joinToString(" ")}\nTarget: $filePath\nAction: $action\n\n".toByteArray())
                val process = try {
                    ProcessBuilder(command)
                        .directory(root.toFile())
                        .start()
                } catch (e: IOException) {
                    throw RuntimeException("Failed to start Language Server '${command.joinToString(" ")}'. Ensure it is installed and on your PATH.", e)
                }
                val lsp = LspClient(process.inputStream, process.outputStream, mapper, transcript)
                try {
                    // 1. Initialize
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Initializing Server...")
                    task.update()
                    lsp.sendRequest("initialize", mapper.createObjectNode().apply {
                        put("processId", ProcessHandle.current().pid())
                        put("rootUri", root.toUri().toString())
                        putObject("capabilities").putObject("textDocument")
                    })
                    // 2. Open Document
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Opening Document...")
                    task.update()
                    val fileUri = file.toURI().toString()
                    lsp.sendNotification("textDocument/didOpen", mapper.createObjectNode().apply {
                        putObject("textDocument").apply {
                            put("uri", fileUri)
                            put("languageId", extension)
                            put("version", 1)
                            put("text", file.readText())
                        }
                    })
                    // 3. Perform Action
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Executing $action...")
                    task.update()
                    val result = when (action.lowercase()) {
                        "diagnostics" -> {
                            // Diagnostics are usually pushed as notifications after opening.
                            // We wait a brief moment for them.
                            Thread.sleep(2000)
                            // In a real persistent client, we'd listen.
                            // For a one-shot task, we might miss them if not immediate,
                            // but many LSPs send them right after didOpen.
                            "Diagnostics are pushed asynchronously. Check transcript for 'textDocument/publishDiagnostics'."
                        }

                        "definition" -> {
                            validatePosition()
                            val params = positionParams(mapper, fileUri)
                            val response = lsp.sendRequest("textDocument/definition", params)
                            formatLocationResponse(response, "Definition")
                        }

                        "references" -> {
                            validatePosition()
                            val params = positionParams(mapper, fileUri).apply {
                                putObject("context").put("includeDeclaration", true)
                            }
                            val response = lsp.sendRequest("textDocument/references", params)
                            formatLocationResponse(response, "References")
                        }

                        "hover" -> {
                            validatePosition()
                            val params = positionParams(mapper, fileUri)
                            val response = lsp.sendRequest("textDocument/hover", params)
                            response?.get("contents")?.toString() ?: "No hover info"
                        }

                        else -> throw IllegalArgumentException("Unknown action: $action")
                    }
                    // 4. Shutdown
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("Shutting down...")
                    task.update()
                    lsp.sendRequest("shutdown", null)
                    lsp.sendNotification("exit", null)
                    val finalOutput = "LSP Action '$action' completed.\nResult:\n$result"
                    transcript?.write("\n## Final Result\n$finalOutput\n".toByteArray())
                    statusBuffer?.setLength(0)
                    statusBuffer?.append("<b>LSP Action Completed</b>")
                    task.update()
                    finalOutput
                } catch (e: Exception) {
                    log.error("LSP Error", e)
                    transcript?.write("\n## Error\n${e.message}\n".toByteArray())
                    task.error(e)
                    throw e
                } finally {
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }
            if (orchestrationConfig.autoFix) {
                resultFn(executeLsp())
            } else {
                val semaphore = Semaphore(0)
                task.add("Ready to run LSP action '$action' on '$filePath'.")
                task.add(task.ui.hrefLink("Run LSP Action", "btn btn-primary") {
                    try {
                        resultFn(executeLsp())
                    } catch (e: Exception) {
                        task.error(e)
                        resultFn("Error: ${e.message}")
                    } finally {
                        semaphore.release()
                    }
                })
                semaphore.acquire()
            }

        } catch (e: Exception) {
            log.warn("Task Failed", e)
            task.error(e)
            resultFn("Error: ${e.message}")
        } finally {
            transcript?.close()
        }
    }

    private fun validatePosition() {
        if (executionConfig?.line == null || executionConfig.character == null) {
            throw IllegalArgumentException("Line and Character are required for this action")
        }
    }

    private fun positionParams(mapper: ObjectMapper, uri: String): ObjectNode {
        return mapper.createObjectNode().apply {
            putObject("textDocument").put("uri", uri)
            putObject("position").apply {
                put("line", executionConfig!!.line!!)
                put("character", executionConfig.character!!)
            }
        }
    }

    private fun formatLocationResponse(json: JsonNode?, title: String): String {
        if (json == null || json.isNull) return "No $title found."

        val locations = if (json.isArray) json else listOf(json)
        return locations.joinToString("\n") { loc ->
            val uri = loc.get("uri").asText()
            val range = loc.get("range")
            val start = range.get("start")
            val line = start.get("line").asInt()
            val char = start.get("character").asInt()
            // Convert URI back to relative path if possible
            val path = if (uri.startsWith(root.toUri().toString())) {
                uri.removePrefix(root.toUri().toString())
            } else uri
            "- $path:$line:$char"
        }
    }

    /**
     * Minimal JSON-RPC Client for one-shot tasks
     */
    private class LspClient(
        val input: InputStream,
        val output: OutputStream,
        val mapper: ObjectMapper,
        val transcript: OutputStream?
    ) {
        private var idCounter = 0

        fun sendRequest(method: String, params: JsonNode?): JsonNode? {
            val id = idCounter++
            val message = mapper.createObjectNode().apply {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                if (params != null) set<JsonNode>("params", params)
            }
            write(message)

            // Read until we get the response with matching ID
            while (true) {
                val response = read() ?: throw RuntimeException("Stream ended before response")
                if (response.has("id") && response.get("id").asInt() == id) {
                    if (response.has("error")) {
                        throw RuntimeException("LSP Error: ${response.get("error")}")
                    }
                    return response.get("result")
                }
                // Log notifications while waiting
                if (!response.has("id")) {
                    transcript?.write("Received Notification: ${response.get("method")}\n".toByteArray())
                }
            }
        }

        fun sendNotification(method: String, params: JsonNode?) {
            val message = mapper.createObjectNode().apply {
                put("jsonrpc", "2.0")
                put("method", method)
                if (params != null) set<JsonNode>("params", params)
            }
            write(message)
        }

        private fun write(json: JsonNode) {
            val str = mapper.writeValueAsString(json)
            val content = "Content-Length: ${str.toByteArray().size}\r\n\r\n$str"
            output.write(content.toByteArray())
            output.flush()
            transcript?.write("--> $str\n".toByteArray())
        }

        private fun read(): JsonNode? {
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine() ?: return null
                if (line.isEmpty()) break
                val parts = line.split(": ", limit = 2)
                if (parts.size == 2) headers[parts[0]] = parts[1]
            }

            val length = headers["Content-Length"]?.toIntOrNull()
                ?: throw RuntimeException("Missing Content-Length header")

            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val c = input.read(bytes, read, length - read)
                if (c == -1) throw RuntimeException("Unexpected EOF")
                read += c
            }

            val str = String(bytes)
            transcript?.write("<-- $str\n".toByteArray())
            return mapper.readTree(str)
        }

        private fun readLine(): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (bytes.size() > 0) bytes.toString() else null
                if (b == '\n'.code) {
                    val str = bytes.toString()
                    return if (str.endsWith('\r')) str.dropLast(1) else str
                }
                bytes.write(b)
            }
        }
    }

    val serverCommands: Map<String, List<String>>
        get() {
            return mapOf(
                "py" to listOf("pylsp"),
                "js" to listOf("typescript-language-server", "--stdio"),
                "ts" to listOf("typescript-language-server", "--stdio"),
                "kt" to listOf("kotlin-language-server"),
                "java" to listOf("jdtls"),
                "c" to listOf("clangd"),
                "cpp" to listOf("clangd"),
                "go" to listOf("gopls"),
                "rs" to listOf("rust-analyzer"),
                "sh" to listOf("bash-language-server", "start"),
                "tex" to listOf("texlab"),
                "yaml" to listOf("yaml-language-server", "--stdio"),
                "dockerfile" to listOf("docker-langserver", "--stdio")
            )
        }

    companion object {
        private val log = LoggerFactory.getLogger(LanguageServerTask::class.java)
        @JvmStatic val LanguageServer = TaskType(
          name = "LanguageServer",
          category = "File",
          taskClass = LanguageServerTask::class.java,
          executionConfigClass = LanguageServerTaskExecutionConfigData::class.java,
          taskSettingsClass = LanguageServerTaskTypeConfig::class.java,
          description = "Interact with Language Servers (LSP)",
          tooltipHtml = """
                          Provides code intelligence capabilities via the Language Server Protocol.
                          <ul>
                              <li><b>Definition:</b> Locate where a symbol is defined.</li>
                              <li><b>References:</b> Find all usages of a symbol.</li>
                              <li><b>Diagnostics:</b> Check files for syntax errors and warnings.</li>
                              <li><b>Hover:</b> Get documentation or type information at a specific position.</li>
                          </ul>
                          Requires language servers (e.g., pylsp, typescript-language-server) to be installed in the environment.
                      """.trimIndent(),
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/RunCodeTask.kt

```
package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.oneAtATime
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

open class RunCodeTask<T : RunCodeTask.RunCodeTaskExecutionConfigData, U:RunCodeTask.RunCodeTaskTypeConfig>(
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
        val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
            ?: defaultSmart).getChildClient(task)
      val transcript = task.transcript()
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
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n\n".toByteArray())
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
        @JvmStatic val RunCode = TaskType(
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/RunToolTask.kt

```
package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.util.concurrent.Semaphore

class RunToolTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: RunToolTaskExecutionConfigData?,
) : AbstractTask<RunToolTask.RunToolTaskExecutionConfigData, RunToolTask.RunToolTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class RunToolTaskTypeConfig(
        task_type: String = RunTool.name,
        model: ApiChatModel? = null,
        name: String? = RunTool.name,
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name,
        model = model,
    )

    class RunToolTaskExecutionConfigData(
        @Description("The tool to run")
        var tool: String? = null,
        @Description("The arguments to pass to the tool")
        var args: List<String>? = null,
        @Description("The relative file path of the working directory")
        var workingDir: String? = null,
        @Description("A description of the task's purpose")
        task_description: String? = null,
        @Description("List of task IDs this task depends on")
        task_dependencies: List<String>? = null,
        @Description("The current state of the task")
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = RunTool.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        val executable: File?
            get() {
                val tools = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
                return tools.find { it.provider?.getExecutables()?.contains(tool) == true }?.let { toolData ->
                    if (toolData.path != null) {
                        toolData.provider!!.resolve(toolData.path).forEach { resolved ->
                            return File(resolved)
                        }
                    }
                    val resolved: String? = toolData.resolve(tool)
                    if (resolved != null) {
                        File(resolved)
                    } else {
                        null
                    }
                }
            }
    }

    override fun promptSegment(): String {
        val executables: List<String>? =
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
                .tools.flatMap { it.component1()?.getExecutables() ?: emptyList() }.distinct().sorted()

        return """
            RunTool - Execute external CLI tools with custom arguments.
            * **Use when:** You need to run compilers, linters, search tools, or custom scripts.
            * **Available tools:** ${executables?.joinToString(", ") ?: "None"}
            * **Inputs:** Specify the `tool` name and a list of `args`.
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        task.ui.pool.submit {
            try {
                log.info("Starting RunToolTask for tool: ${executionConfig?.tool}")
                val tabs = TabbedDisplay(task)

                val context = getPriorCode(agent.executionState)
                if (context.isNotBlank()) {
                    tabs["Context"] = "```\n$context\n```".renderMarkdown()
                    transcript?.write("# Context\n<details><summary>Prior Code</summary>\n\n```\n$context\n```\n</details>\n".toByteArray())
                }

                val tool = executionConfig?.tool ?: throw IllegalArgumentException("Tool not specified")
                val args = executionConfig?.args ?: emptyList()
                val workingDir = executionConfig?.workingDir?.let { File(it) }
                    ?: File(orchestrationConfig.absoluteWorkingDir ?: ".")
                val executable = executionConfig?.executable?.absolutePath
                    ?: throw IllegalArgumentException("Executable for tool '$tool' not found")
                val command = listOf(executable) + args
                val commandStr = command.joinToString(" ")
                tabs["Command"] = "```bash\n$commandStr\n```".renderMarkdown()

                transcript?.write("## Command\n```bash\n$commandStr\n```\n\n".toByteArray())

                fun execute(outputTask: SessionTask): String {
                    val status = outputTask.add("Executing process...".renderMarkdown())
                    val process = ProcessBuilder(command)
                        .directory(workingDir)
                        .redirectErrorStream(true)
                        .start()

                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    status?.setLength(0)
                    status?.append("**Execution Complete** (Exit Code: $exitCode)".renderMarkdown())
                    outputTask.update()
                    outputTask.add("#### Output\n```\n$output\n```".renderMarkdown())

                    transcript?.write("## Output\n<details><summary>Process Output</summary>\n\n```\n$output\n```\n</details>\n\n".toByteArray())

                    return if (exitCode == 0) {
                        "### Tool execution successful\n**Tool:** `$tool`\n\n#### Output\n$output"
                    } else {
                        "### Tool execution failed (Exit Code: $exitCode)\n**Tool:** `$tool`\n\n#### Output\n$output"
                    }
                }

                if (orchestrationConfig.autoFix) {
                    val outputTask = tabs.newTask("Output")
                    resultFn(execute(outputTask))
                    outputTask.complete()
                    task.complete()
                } else {
                    val semaphore = Semaphore(0)
                    var result = "Skipped"

                    task.add("### Approval Required\nReview the command in the **Command** tab before running.".renderMarkdown())

                    task.add(task.ui.hrefLink("▶ Run Tool", "btn btn-primary") {
                        try {
                            val outputTask = tabs.newTask("Output")
                            result = execute(outputTask)
                            outputTask.complete()

                            task.add(acceptButtonFooter(task.ui) {
                                semaphore.release()
                            })
                        } catch (e: Exception) {
                            task.error(e)
                            log.error("Error in RunTool hrefLink", e)
                            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                        }
                    })

                    semaphore.acquire()
                    resultFn(result)
                    task.complete()
                }
            } catch (e: Exception) {
                task.error(e)
                log.error("Error running tool", e)
                transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                throw e
            } finally {
                transcript?.close()
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RunToolTask::class.java)
        @JvmStatic val RunTool = TaskType(
            name = "RunTool",
            category = "Execution",
            taskClass = RunToolTask::class.java,
            executionConfigClass = RunToolTaskExecutionConfigData::class.java,
            taskSettingsClass = RunToolTaskTypeConfig::class.java,
            description = "Execute external tools",
            tooltipHtml = """
                <p>Executes configured external tools and scripts.</p>
                <ul>
                    <li>Supports custom arguments and working directories.</li>
                    <li>Captures stdout and stderr.</li>
                    <li>Requires manual approval for side effects unless auto-fix is enabled.</li>
                </ul>
            """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/SingleFixTask.kt

```
package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.PatchApp
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore

class SingleFixTask(
  orchestrationConfig: OrchestrationConfig, planTask: SingleFixTaskExecutionConfigData?
) : AbstractTask<SingleFixTask.SingleFixTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
  companion object {
    private val log = LoggerFactory.getLogger(SingleFixTask::class.java)

    @JvmStatic
    val SingleFix = TaskType(
      name = "SingleFix",
      category = "Execution",
      taskClass = SingleFixTask::class.java,
      executionConfigClass = SingleFixTaskExecutionConfigData::class.java,
      taskSettingsClass = SingleFixTaskTypeConfig::class.java,
      description = "Analyze a log file and fix errors found in it without running commands",
      tooltipHtml = """
    Analyzes a provided log file for errors and attempts to fix them in the codebase.
    <ul>
    <li>Does not execute commands</li>
    <li>Requires a log file path</li>
    <li>Performs one pass of fixes</li>
    </ul>
    """,
    )
  }

  class SingleFixTaskTypeConfig(
    name: String? = SingleFix.name,
    model: ApiChatModel? = null,
  ) : TaskTypeConfig(SingleFix.name, name, model)

  class SingleFixTaskExecutionConfigData(
    @Description("The path to the log file containing errors") var logFile: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : ValidatedObject, TaskExecutionConfig(
    task_type = SingleFix.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ) {
    override fun validate(): String? {
      if (logFile.isNullOrBlank()) {
        return "logFile must not be empty"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return "Analyze the log file '${executionConfig?.logFile}' and fix any errors found."
  }

  override val typeConfig: SingleFixTaskTypeConfig
    get() = super.typeConfig as SingleFixTaskTypeConfig

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val semaphore = Semaphore(0)
    Retryable(task = task) {
      val subTask = task.newTask()

      fun execute() {
        subTask.ui.pool.submit {
          val transcript = createTranscript(subTask)
          subTask.add(transcript.second.renderMarkdown())
          val model =
            (typeConfig.model?.let { orchestrationConfig.instance(it) } ?: defaultSmart).getChildClient(subTask)
          val markdownTranscript = transcript.first
          try {
            markdownTranscript?.write("## Single Fix Task Execution\n\n".toByteArray())

            val logFilePath = executionConfig?.logFile ?: throw IllegalArgumentException("Log file not specified")
            val logFile = agent.root.toFile().resolve(logFilePath)
            if (!logFile.exists()) throw IllegalArgumentException("Log file not found: $logFile")

            val workingDir = orchestrationConfig.workingDir?.let { agent.root.toFile().resolve(it) } ?: agent.root.toFile()

            markdownTranscript?.write("Analyzing log file: ${logFile.absolutePath}\n".toByteArray())

            object : PatchApp(
              root = agent.root.toFile(),
              settings = PatchApp.Settings(
                // Dummy command to ensure workingDirectory property works correctly in PatchApp
                commands = listOf(
                  PatchApp.CommandSettings(
                    executable = File("dummy"), workingDirectory = workingDir
                  )
                ),
                autoFix = orchestrationConfig.autoFix,
                includeLineNumbers = false,
              ),
              model = model,
              parsingModel = defaultFast,
              processor = orchestrationConfig.processor,
            ) {
              override fun codeFiles(): Set<Path> {
                return FileSelectionUtils.filteredWalk(root).filter { it.length() < 1024 * 1024 / 2 }
                  .map { root.toPath().relativize(it.toPath()) }.toSet()
              }

              override fun projectSummary(): String {
                val codeFiles = codeFiles()
                return codeFiles.asSequence().filter { root.toPath().resolve(it).toFile().exists() }.distinct().sorted()
                  .joinToString("\n") { path ->
                    "* $path - ${root.toPath().resolve(path).toFile().length()} bytes"
                  }
              }

              override fun output(
                task: SessionTask, settings: Settings, tabs: TabbedDisplay
              ): OutputResult {
                // Return exit code 1 to trigger the fix logic in PatchApp.run
                return OutputResult(1, logFile.readText())
              }

              override fun searchFiles(searchStrings: List<String>): Set<Path> {
                return searchStrings.flatMap { searchString ->
                  FileSelectionUtils.filteredWalk(workingDir)
                    .filter { it.readText().contains(searchString, ignoreCase = true) }.map { it.toPath() }.toList()
                }.toSet()
              }
            }.run(
              task = subTask, model = model
            ).apply {
              resultFn("### Success\nLog analysis and fix generation completed.")
              semaphore.release()
              subTask.complete()
            }
          } catch (e: Throwable) {
            subTask.error(e)
            log.error("Critical error during SingleFixTask execution", e)
            markdownTranscript?.write("\n### Execution Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())

            if (orchestrationConfig.autoFix) {
              semaphore.release()
              subTask.complete()
            }
          } finally {
            markdownTranscript?.close()
          }
        }
      }

      if (orchestrationConfig.autoFix) {
        execute()
      } else {
        subTask.add(subTask.ui.hrefLink("▶ Run SingleFix", "btn btn-primary") {
          execute()
        }.renderMarkdown())
      }
      subTask.placeholder
    }
    try {
      semaphore.acquire()
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
    task.complete()
  }

  private fun createTranscript(task: SessionTask): Pair<FileOutputStream?, String> {
    val transcriptFile =
      this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
    val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
    val markdownTranscript = file?.outputStream()
    val html = "Writing transcript to <a href='$link' target='_blank'>$link</a>"
    return Pair(markdownTranscript, html)
  }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/SubPlanTask.kt

```
package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient

class SubPlanTask(
    orchestrationConfig: OrchestrationConfig, planTask: SubPlanTaskExecutionConfigData?
) : AbstractTask<SubPlanTask.SubPlanTaskExecutionConfigData, SubPlanTask.SubPlanTaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class SubPlanTaskTypeConfig(
      @Description("Cognitive strategy to use for sub-planning (overrides default)") var cognitiveSettings: CognitiveModeConfig? = null,
      @Description("Task-specific configurations available within sub-plans") var taskSettings: MutableMap<String, TaskTypeConfig> = mutableMapOf(),
      @Description("Supplemental description of the purpose of this configuration") var purpose: String = "",
      @Description("Prompt template for summarizing sub-plan results") var summaryPrompt: String = """
               Create a comprehensive summary of the sub-planning results below.
               
               Original Goal: {goal}
               
               The summary should:
               - Highlight key findings and accomplishments
               - Identify any issues or blockers encountered
               - Provide actionable next steps if applicable
               - Be concise but complete
               
               Use markdown formatting with headers and bullet points.
           """.trimIndent(),
      model: ApiChatModel? = null,
      name: String? = SubPlan.name,
    ) : TaskTypeConfig(task_type = SubPlan.name, name = name, model = model), ValidatedObject {
        val cognitiveMode: CognitiveModeType<*>? get() = cognitiveSettings?.type
        override fun validate(): String? {
            // Validate that taskSettings don't contain invalid configurations
            taskSettings.forEach { (key, config) ->
                if (config is ValidatedObject) {
                    config.validate()?.let { return "Invalid task setting '$key': $it" }
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    class SubPlanTaskExecutionConfigData(
        @Description("The goal or objective for the sub-planning task") var planning_goal: String? = null,
        @Description("Context information to provide to the sub-planner") var context: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = SubPlan.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            // Validate that either planning_goal or task_description is provided
            if (planning_goal.isNullOrBlank() && task_description.isNullOrBlank()) {
                return "Either planning_goal or task_description must be specified for SubPlanningTask"
            }

            // Validate context items if present
            context?.forEachIndexed { index, ctx ->
                if (ctx.isBlank()) {
                    return "Context item at index $index is blank"
                }
            }

            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        val typeConfig = typeConfig
        return buildString {
            appendLine("SubPlanningTask - Create and execute sub-plans using recursive planning with configurable cognitive modes.")
            typeConfig?.purpose?.takeIf { it.isNotEmpty() }?.let {
                appendLine("** Purpose: $it")
            }
            typeConfig?.taskSettings?.values?.joinToString(", ") { it.task_type ?: "?" }?.let {
                appendLine("** This SubPlanningTask can run the following tasks types: $it")
            }
            appendLine("** Specify a planning goal or objective")
            appendLine("** Optionally provide context information")
            appendLine("** Can override the cognitive mode for the sub-plan")
            appendLine("** Supports multiple levels of recursion up to configured depth")
            append("** Results are aggregated and optionally summarized")
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()

        try {
          log.info("Starting SubPlanningTask. Goal: ${executionConfig?.planning_goal ?: "N/A"}")
          transcript?.write("## Sub-Planning Task Initialization\n".toByteArray())
            val typeConfig = this.typeConfig ?: throw RuntimeException()
            // Get the cognitive mode for sub-planning
            val cognitiveMode =
                (typeConfig.cognitiveMode?.newSettings() ?: orchestrationConfig.cognitiveSettings
                ?: CognitiveModeType.Adaptive.newSettings())

            val subConfig = orchestrationConfig.copy(
                taskSettings = typeConfig.taskSettings,
                cognitiveSettings = typeConfig.cognitiveSettings ?: orchestrationConfig.cognitiveSettings,
            )
            log.info("Using cognitive mode: ${cognitiveMode.type?.name} for sub-planning")

            // Create tabs for displaying sub-plan execution
            val tabs = TabbedDisplay(task)

            // Create planning context
            val planningTask = task.newTask()
            tabs["Planning"] = planningTask.placeholder

            // Get the planning goal
            var planningGoal =
                executionConfig?.planning_goal ?: executionConfig?.task_description
                ?: throw IllegalArgumentException("No planning goal specified for SubPlanningTask")

            // Append purpose if available
            if (typeConfig.purpose.isNotEmpty()) planningGoal += "\n\nPurpose: ${typeConfig.purpose}"

            // Build context for the sub-planner
          val contextMessages = buildContextMessages(agent, messages)

            // Append context to the planning goal
            if (contextMessages.isNotEmpty()) {
                planningGoal = planningGoal + "\n\nContext:\n" + contextMessages.joinToString("\n")
            }

            log.debug("Planning goal: $planningGoal")

            // Initialize the cognitive mode
            val cognitiveInstance = cognitiveMode.type!!.getImpl(
                orchestrationConfig = subConfig, session = agent.session, user = agent.user
            ).apply { initialize(task) }

            // Display planning information
            val planningInfo = buildString {
                appendLine("# Sub-Planning Task")
                appendLine()
                appendLine("**Goal:** $planningGoal")
                appendLine()
                appendLine("**Cognitive Mode:** ${cognitiveMode.type?.name}")
                appendLine()
                if (typeConfig.purpose.isNotEmpty()) {
                    appendLine("**Purpose:** ${typeConfig.purpose}")
                    appendLine()
                }
                if (!executionConfig?.context.isNullOrEmpty()) {
                    appendLine("**Context:**")
                    executionConfig.context?.forEach { ctx ->
                        appendLine("- $ctx")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
            }
            transcript?.write(planningInfo.toByteArray())
            planningTask.add(planningInfo.renderMarkdown(true))
            planningTask.complete()

            fun runExecution(): String {
                // Execute the sub-plan using the cognitive mode
                val executionTask = task.newTask()
                tabs["Execution"] = executionTask.placeholder

                log.debug("Executing sub-plan with ${contextMessages.size} context messages")

                // Handle the user message through the cognitive mode
                transcript?.write("\n\n## Execution\n\n".toByteArray())
                transcript?.write("**Planning Goal:**\n\n".toByteArray())
                transcript?.write(planningGoal.toByteArray())
                transcript?.write("\n\n".toByteArray())

                cognitiveInstance.handleUserMessage(planningGoal, executionTask)

                // Collect results from the cognitive mode's context
                val results = cognitiveInstance.contextData()

                log.info("Sub-plan execution completed with ${results.size} results")

                // Create summary if configured
                val summaryTask = task.newTask()
                tabs["Summary"] = summaryTask.placeholder

                val summary = createSummary(results, planningGoal, summaryTask, orchestrationConfig)
                transcript?.write("\n\n## Summary\n\n".toByteArray())
                transcript?.write(summary.toByteArray())
                transcript?.write("\n\n".toByteArray())
                summaryTask.add(summary.renderMarkdown(true))
                summaryTask.complete()
                tabs.update()
                return summary
            }

            if (orchestrationConfig.autoFix) {
              task.ui.pool.submit {
                try {
                  val summary = runExecution()
                  resultFn(summary)
                  task.complete()
                } catch (e: Exception) {
                  handleError(e, task, transcript, resultFn)
                }
              }
            } else {
                val semaphore = java.util.concurrent.Semaphore(0)
              task.add(task.ui.hrefLink("▶ Run Sub-Plan", "btn btn-primary".renderMarkdown(true)) {
                    task.ui.pool.submit {
                        try {
                            val summary = runExecution()
                            val footer = acceptButtonFooter(task.ui) {
                                resultFn(summary)
                                semaphore.release()
                              task.complete()
                            }
                          task.add(footer.renderMarkdown(true))
                        } catch (e: Exception) {
                          handleError(e, task, transcript, resultFn)
                          semaphore.release()
                        }
                    }
                })
                semaphore.acquire()
            }
        } catch (e: Exception) {
          handleError(e, task, transcript, resultFn)
        }
    }

  private fun handleError(
    e: Exception,
    task: SessionTask,
    transcript: java.io.OutputStream?,
    resultFn: (String) -> Unit
  ) {
    task.error(e)
    log.error("Error in SubPlanningTask: ${e.message}", e)
    val errorLog =
      "\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n"
    transcript?.write(errorLog.toByteArray())
    resultFn("Error in sub-planning: ${e.message}")
    }

  private fun buildContextMessages(agent: TaskOrchestrator, messages: List<String>): List<String> {
        val contextMessages = mutableListOf<String>()

        // Add explicit context from execution config
        executionConfig?.context?.let { contextMessages.addAll(it) }

        // Add prior task results
    val priorCode = getPriorCode(agent.executionState)
        if (priorCode.isNotBlank()) {
            contextMessages.add("## Prior Task Results\n\n$priorCode")
        }

        // Add incoming messages
        contextMessages.addAll(messages)

        log.debug("Built ${contextMessages.size} context messages for sub-planning")
        return contextMessages
    }

    private fun createSummary(
        results: List<String>, goal: String, task: SessionTask, orchestrationConfig: OrchestrationConfig
    ): String {
        log.info("Creating summary of ${results.size} sub-plan results")
        if (results.isEmpty()) {
            return buildString {
                appendLine("# Sub-Planning Execution Completed")
                appendLine()
                appendLine("**Goal:** $goal")
                appendLine()
                appendLine("The sub-plan executed successfully. No specific data context was returned.")
                appendLine()
                appendLine("Check the Execution tab for detailed logs.")
            }
        }


        val combinedResults = results.joinToString("\n\n---\n\n")

        if (combinedResults.length < 5000) {
            log.debug("Results are short enough, returning without summarization")
            return buildString {
                appendLine("# Sub-Planning Results")
                appendLine()
                appendLine("**Goal:** $goal")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(combinedResults)
            }
        }

        // Use an agent to create a summary
        val typeConfig = typeConfig ?: throw RuntimeException()
        val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
            ?: defaultSmart).getChildClient(task)

        val summaryAgent = ChatAgent(
               
               
               
            prompt = typeConfig.summaryPrompt.replace("{goal}", goal), model = model
        )

        val summary = summaryAgent.answer(listOf(combinedResults))

        log.debug("Generated summary of length: ${summary.length}")

        return buildString {
            appendLine("# Sub-Planning Summary")
            appendLine()
            appendLine("**Goal:** $goal")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine(summary)
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("<details>")
            appendLine("<summary>Full Results (${results.size} items)</summary>")
            appendLine()
            appendLine(combinedResults)
            appendLine()
            appendLine("</details>")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SubPlanTask::class.java)

        @JvmStatic val SubPlan = TaskType(
          name = "SubPlan",
          category = "Execution",
          taskClass = SubPlanTask::class.java,
          executionConfigClass = SubPlanTaskExecutionConfigData::class.java,
          taskSettingsClass = SubPlanTaskTypeConfig::class.java,
          description = "Create and execute sub-plans using recursive planning",
          tooltipHtml = """
                       Enables recursive planning and execution with configurable cognitive modes.
                       <ul>
                         <li>Create sub-plans with different cognitive strategies</li>
                         <li>Support for multiple recursion levels</li>
                         <li>Context propagation to sub-plans</li>
                         <li>Configurable recursion depth limits</li>
                         <li>Automatic result aggregation and summarization</li>
                         <li>Flexible cognitive mode selection per sub-plan</li>
                         <li>Useful for complex multi-stage problems</li>
                       </ul>
                     """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/SymbolsDbCodeTask.kt

```
package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.run.SymbolsDbCodeTask.SymbolsDbCodeTaskExecutionConfigData
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.LoggerFactory

class SymbolsDbCodeTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SymbolsDbCodeTaskExecutionConfigData?,
) : RunCodeTask<SymbolsDbCodeTaskExecutionConfigData, SymbolsDbCodeTask.SymbolsDbCodeTaskTypeConfig>(
    orchestrationConfig,
    planTask,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
        @JvmStatic val SymbolsDbCode = TaskType(
            name = "SymbolsDbCodeTask",
            category = "Execution",
            taskClass = SymbolsDbCodeTask::class.java,
            executionConfigClass = SymbolsDbCodeTaskExecutionConfigData::class.java,
            taskSettingsClass = SymbolsDbCodeTaskTypeConfig::class.java,
            description = "Execute code snippets with predefined symbols",
            tooltipHtml = """
                Executes code snippets in an interactive environment with access to a symbol graph.
                <ul>
                  <li>Access to <code>symbols_db</code> (SymbolGraphService)</li>
                  <li>Query code symbols and relationships</li>
                  <li>User-approved code execution</li>
                  <li>Interactive result review</li>
                </ul>
            """.trimIndent(),
        )
    }

  override fun promptSegment(): String {
    val basePrompt = super.promptSegment()
    val customPrompt = typeConfig?.promptTemplate?.replace("{file}", typeConfig?.symbolFile ?: "unknown")
      ?: "You have access to a `symbols_db` object (SymbolGraphService) loaded from the project symbol graph."
    return """
            $basePrompt
            
            ### Symbols Database Access
            * $customPrompt
            * Use `symbols_db.findSymbol("name")` to locate code elements.
            * Use `symbols_db.getDependencies("name")` to analyze relationships.
        """.trimIndent()
  }

    override fun symbols(): Map<String, Any> = typeConfig?.let { typeConfig ->
      val file = root.toFile().resolve(typeConfig.symbolFile)
      log.info("Loading symbols database from ${file.absolutePath}")
        mapOf(
            "symbols_db" to SymbolGraphService().apply {
              if (file.exists()) load(file)
            }
        )
    } ?: emptyMap()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.transcript()
    try {
      log.info("Starting SymbolsDbCodeTask - Goal: ${executionConfig?.goal}")
      task.add("### Initializing Symbols Database".renderMarkdown())
      transcript?.write("## Symbols Database Initialization\n".toByteArray())
      val configDetails = """
                <details>
                <summary>Configuration Details</summary>
                * **Symbol File:** `${typeConfig?.symbolFile}`
                * **Runtime:** `${typeConfig?.codeRuntime}`
                * **Working Dir:** `${executionConfig?.workingDir}`
                </details>
            """.trimIndent()
      transcript?.write(configDetails.toByteArray())
      super.run(agent, messages, task, resultFn, orchestrationConfig)
    } catch (e: Exception) {
      // Triple Log Rule
      task.error(e)
      log.error("Error executing SymbolsDbCodeTask", e)
      transcript?.write("\n## Execution Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
      throw e
    } finally {
      transcript?.close()
    }
  }


  override fun describer() = AbbrevWhitelistYamlDescriber("com.simiacryptus")

    open class SymbolsDbCodeTaskTypeConfig(
      @Description("The code runtime to use for execution (e.g., Groovy, Kotlin).")
        codeRuntime: CodeRuntimes? = CodeRuntimes.GroovyRuntime,
      @Description("The relative path to the symbol graph JSON file.")
      var symbolFile: String = "symbol_graph.json",
      @Description("The prompt template used to describe the symbols database to the LLM.")
      var promptTemplate: String = "You have access to a `symbols_db` object (SymbolGraphService) loaded from '{file}'."
    ) : RunCodeTaskTypeConfig(
        task_type = SymbolsDbCode.name,
        codeRuntime = codeRuntime,
        model = null,
        name = SymbolsDbCode.name,
    )

    open class SymbolsDbCodeTaskExecutionConfigData(
      @Description("The high-level goal or objective for the code execution.")
        goal: String? = null,
      @Description("The working directory where the code will be executed.")
        workingDir: String? = null,
      @Description("A detailed description of the specific task to be performed.")
        task_description: String? = null,
      @Description("A list of task IDs that must be completed before this task starts.")
        task_dependencies: List<String>? = null,
      @Description("The current execution state of the task.")
        state: TaskState? = null,
    ) : RunCodeTaskExecutionConfigData(
        goal = goal,
        workingDir = workingDir,
        task_description = task_description,
        task_dependencies = task_dependencies,
        state = state,
        task_type = SymbolsDbCode.name
    )
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated README for the execution and automation tools package.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/README.md
```markdown
# Execution and Automation Tasks

This package provides a suite of specialized task implementations for the Cognotik orchestration framework. These tasks focus on code execution, external tool integration, automated error correction, and recursive planning.

## Overview

The tasks in this package allow the agent to interact with the environment by running commands, executing scripts, querying language servers, and managing complex sub-goals through recursive planning. Most tasks support both interactive (user-approved) and automated (auto-fix) modes.

## Task Types

### [AutoFixTask](AutoFixTask.kt)
**Task Type:** `AutoFix`
Executes shell commands and automatically attempts to fix any errors that occur during execution. It uses a self-healing loop to analyze command output and apply patches to the codebase until the command succeeds or a limit is reached.

### [LanguageServerTask](LanguageServerTask.kt)
**Task Type:** `LanguageServer`
Provides code intelligence capabilities by interacting with Language Servers (LSP). It supports operations such as:
- **Diagnostics:** Identifying syntax errors and warnings.
- **Definition:** Finding the declaration of a symbol.
- **References:** Locating all usages of a symbol.
- **Hover:** Retrieving documentation and type information.

### [RunCodeTask](RunCodeTask.kt)
**Task Type:** `RunCode`
Executes code snippets (typically Groovy or Kotlin) within the application's environment. This is useful for data processing, complex calculations, or file system operations that are better expressed as scripts than shell commands.

### [SymbolsDbCodeTask](SymbolsDbCodeTask.kt)
**Task Type:** `SymbolsDbCodeTask`
An extension of `RunCodeTask` that provides the executing script with access to a `symbols_db` object. This object allows the script to query a pre-computed symbol graph of the project, enabling sophisticated code analysis and relationship mapping.

### [RunToolTask](RunToolTask.kt)
**Task Type:** `RunTool`
A wrapper for executing external CLI tools (compilers, linters, etc.) with specific arguments. It handles output capture and provides a structured way to integrate existing development tools into the agent's workflow.

### [SingleFixTask](SingleFixTask.kt)
**Task Type:** `SingleFix`
Analyzes a provided log file (e.g., a build log or test failure) and attempts to fix the identified errors in the codebase in a single pass, without re-running the commands that generated the log.

### [SubPlanTask](SubPlanTask.kt)
**Task Type:** `SubPlan`
Enables recursive planning by allowing a task to spawn its own sub-orchestrator. This is used to break down complex objectives into smaller, manageable steps, potentially using different cognitive strategies for the sub-tasks.

## Base Classes and Utilities

- **[CodingTask](CodingTask.kt):** An abstract base class that provides the infrastructure for interactive code execution, including UI feedback loops, transcript generation, and error handling.
- **Transcript Generation:** Most tasks in this package automatically generate detailed Markdown transcripts of their execution, including inputs, outputs, and any generated patches.

## Configuration

Tasks are configured via `TaskExecutionConfig` data classes which specify parameters like goals, commands, working directories, and dependencies. Global behavior, such as whether to require manual approval for side effects, is controlled via the `OrchestrationConfig`.
```

### Summary of Changes
- Created/Updated `README.md` in `com.simiacryptus.cognotik.plan.tools.run`.
- Documented all major task types: `AutoFix`, `LanguageServer`, `RunCode`, `SymbolsDbCode`, `RunTool`, `SingleFix`, and `SubPlan`.
- Included descriptions based on the `TaskType` metadata found in the source files.
- Highlighted the role of `CodingTask` as a foundational component for interactive execution.
- Explained the general execution patterns (interactive vs. auto-fix) and transcript generation features.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/run/README.md</a> Updated
