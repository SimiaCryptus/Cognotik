package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.apps.code.CodingAgent
import com.simiacryptus.cognotik.interpreter.ProcessInterpreter
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ApiModel
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
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
    @Description("Timeout in minutes for command execution (default: 15)")
    val timeoutMinutes: Long? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : TaskConfigBase(
    task_type = TaskType.RunShellCommandTask.name,
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
          planSettings.absoluteWorkingDir
        ).absolutePath),
        "language" to (planSettings.language ?: "bash"),
        "command" to (planSettings.shellCmd),
        "timeoutMinutes" to (planTask?.timeoutMinutes ?: 15L),
      ),
      model = planTask?.task_type?.let { planSettings.getTaskSettings(TaskType.valueOf(it)).model }
        ?: planSettings.defaultModel,
      temperature = planSettings.temperature,
      fallbackModel = planTask?.task_type?.let { planSettings.getTaskSettings(TaskType.valueOf(it)).model }
        ?: planSettings.defaultModel
    )
  }

  override fun promptSegment() = """
    RunShellCommandTask - Execute ${planSettings.language ?: "bash"} shell commands and provide the output
      ** Specify the command to be executed, or describe the task to be performed
      ** Optionally specify a working directory for the command execution
      ** Optionally specify a timeout in minutes (default: 15)
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
    val autoRunCounter = AtomicInteger(0)
    val semaphore = Semaphore(0)
    val codingAgent = object : CodingAgent<ProcessInterpreter>(
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
      retryable = false,
    ) {
      override fun execute(
        task: SessionTask,
        response: CodingActor.CodeResult
      ): String {
        val result = super.execute(task, response) // Runs the interpreter, updates response.result
        if (planSettings.autoFix) {
          val resultString =
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
        request: CodingActor.CodeRequest,
        response: CodingActor.CodeResult
      ) {
        if (planSettings.autoFix && autoRunCounter.incrementAndGet() <= 1) {
          super.responseAction(task, "Running...", null, StringBuilder()) {
            this.execute(task, response, request) // Calls the overridden execute
          }
        } else if (!planSettings.autoFix) {
          // Manual feedback UI
          val formText = StringBuilder()
          var formHandle: StringBuilder? = null
          formHandle = task.add(
            "<div>\n${
              if (!super.canPlay) "" else super.playButton(
                task,
                request,
                response,
                formText
              ) { formHandle!! }
            }\n${acceptButton(response, task)}\n</div>\n${ // Pass task to acceptButton if needed for consistency, or ensure response is sufficient
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
        response: CodingActor.CodeResult,
        @Suppress("UNUSED_PARAMETER") task: SessionTask // Added task param for potential future use or consistency
      ): String {
        return ui.hrefLink("Accept", "href-link play-button") {
          response.let {
            "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n" +
                "## Result\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n" +
                "## Output\n$TRIPLE_TILDE\n${response.result.resultOutput}\n$TRIPLE_TILDE\n"
          }.apply { resultFn(this) }
          semaphore.release()
        }
      }
    }
    codingAgent.start(
      codingAgent.codeRequest(
        messages.map { it to ApiModel.Role.user } +
            listOfNotNull(
              this.taskConfig?.command?.takeIf { it.isNotBlank() }?.let { it to ApiModel.Role.user }
            )
      )
    )
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