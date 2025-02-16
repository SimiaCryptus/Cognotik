package com.simiacryptus.skyenet.apps.plan.tools

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.skyenet.apps.code.CodingAgent
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.interpreter.ProcessInterpreter
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import kotlin.reflect.KClass

class RunShellCommandTask(
  planSettings: PlanSettings,
  planTask: RunShellCommandTaskConfigData?
) : AbstractTask<RunShellCommandTask.RunShellCommandTaskConfigData>(planSettings, planTask) {

  class RunShellCommandTaskConfigData(
    @Description("The shell command to be executed")
    val command: String? = null,
    @Description("The relative file path of the working directory")
    val workingDir: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : TaskConfigBase(
    task_type = TaskType.RunShellCommand.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  val shellCommandActor by lazy {
    CodingActor(
      name = "RunShellCommand",
      interpreterClass = ProcessInterpreter::class,
      details = """
        Execute the following shell command(s) and provide the output. Ensure to handle any errors or exceptions gracefully.
        Note: This task is for running simple and safe commands. Avoid executing commands that can cause harm to the system or compromise security.
        """.trimIndent(),
      symbols = mapOf<String, Any>(
        "env" to (planSettings.env ?: emptyMap()),
        "workingDir" to (planTask?.workingDir?.let { File(it).absolutePath } ?: File(
          planSettings.workingDir
        ).absolutePath),
        "language" to (planSettings.language ?: "bash"),
        "command" to (planSettings.shellCmd),
      ),
      model = planSettings.getTaskSettings(TaskType.valueOf(planTask?.task_type!!)).model
        ?: planSettings.defaultModel,
      temperature = planSettings.temperature,
    )
  }

  override fun promptSegment() = """
    RunShellCommand - Execute shell commands and provide the output
      ** Specify the command to be executed, or describe the task to be performed
      ** List input files/tasks to be examined when writing the command
      ** Optionally specify a working directory for the command execution
    """.trimIndent()

  override fun run(
    agent: PlanCoordinator,
    messages: List<String>,
    task: SessionTask,
    api: ChatClient,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings
  ) {
    val semaphore = Semaphore(0)
    object : CodingAgent<ProcessInterpreter>(
      api = api,
      dataStorage = agent.dataStorage,
      session = agent.session,
      user = agent.user,
      ui = agent.ui,
      interpreter = shellCommandActor.interpreterClass as KClass<ProcessInterpreter>,
      symbols = shellCommandActor.symbols,
      temperature = shellCommandActor.temperature,
      details = shellCommandActor.details,
      model = shellCommandActor.model,
      mainTask = task,
    ) {
      override fun displayFeedback(
        task: SessionTask,
        request: CodingActor.CodeRequest,
        response: CodingActor.CodeResult
      ) {
        val formText = StringBuilder()
        var formHandle: StringBuilder? = null
        formHandle = task.add(
          "<div style=\"display: flex;flex-direction: column;\">\n${
            if (!super.canPlay) "" else super.playButton(
              task,
              request,
              response,
              formText
            ) { formHandle!! }
          }\n${acceptButton(response)}\n</div>\n${super.reviseMsg(task, request, response, formText) { formHandle!! }}", additionalClasses = "reply-message"
        )
        formText.append(formHandle.toString())
        formHandle.toString()
        task.complete()
      }

      fun acceptButton(
        response: CodingActor.CodeResult
      ): String {
        return ui.hrefLink("Accept", "href-link play-button") {
          response.let {
            "## Shell Command Output\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n\n$TRIPLE_TILDE\n${response.renderedResponse}\n$TRIPLE_TILDE\n"
          }.apply { resultFn(this) }
          semaphore.release()
        }
      }
    }.apply<CodingAgent<ProcessInterpreter>> {
      start(
        codeRequest(
          messages.map { it to ApiModel.Role.user }
        )
      )
    }
    try {
      semaphore.acquire()
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(RunShellCommandTask::class.java)
  }
}