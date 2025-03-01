package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.skyenet.apps.general.CommandPatchApp
import com.simiacryptus.skyenet.apps.general.PatchApp
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.io.File

abstract class AbstractAnalysisTask<T : AbstractFileTask.FileTaskConfigBase>(
  planSettings: PlanSettings,
  planTask: T?
) : AbstractFileTask<T>(planSettings, planTask) {

  protected fun validateConfig() {
    requireNotNull(taskConfig) { "Task configuration is required" }
    require(!((taskConfig.related_files ?: emptyList()) + (taskConfig.files ?: emptyList())).isEmpty()) {
      "At least one input or output file must be specified"
    }
  }

  abstract val actorName: String
  abstract val actorPrompt: String

  protected val analysisActor by lazy {
    SimpleActor(
      name = actorName,
      prompt = actorPrompt,
      model = planSettings.getTaskSettings(TaskType.valueOf(planTask?.task_type!!)).model
        ?: planSettings.defaultModel,
      temperature = planSettings.temperature,
    )
  }

  override fun run(
    agent: PlanCoordinator,
    messages: List<String>,
    task: SessionTask,
    api: ChatClient,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings
  ) {
    validateConfig()

    val analysisResult = analysisActor.answer(
      messages + listOf(
        getInputFileCode(),
        "${getAnalysisInstruction()}:\n${getInputFileCode()}",
      ).filter { it.isNotBlank() }, api = api
    )
    resultFn(analysisResult)
    applyChanges(agent, task, analysisResult, api)
  }

  abstract fun getAnalysisInstruction(): String

  private fun applyChanges(agent: PlanCoordinator, task: SessionTask, analysisResult: String, api: API) {
    try {
      val outputResult = CommandPatchApp(
        root = agent.root.toFile(),
        session = agent.session,
        settings = PatchApp.Settings(
          commands = listOf(
            PatchApp.CommandSettings(
              executable = File("dummy"),
              workingDirectory = agent.root.toFile(),
              additionalInstructions = ""
            )
          ),
          autoFix = agent.planSettings.autoFix,
          exitCodeOption = "nonzero",
          includeLineNumbers = false,
        ),
        api = api as ChatClient,
        model = agent.planSettings.getTaskSettings(TaskType.valueOf(taskConfig?.task_type!!)).model
          ?: agent.planSettings.defaultModel,
        files = agent.files,
        command = analysisResult,
        parsingModel = agent.planSettings.parsingModel,
      ).run(
        ui = agent.ui,
        task = task
      )
      if (outputResult.exitCode == 0) {
        task.add("${actorName} completed and suggestions have been applied successfully.")
      } else {
        task.add("${actorName} completed, but failed to apply suggestions. Exit code: ${outputResult.exitCode}")
      }
    } catch (e: Exception) {
      log.error("Error applying changes", e)
      task.add("Error applying changes: ${e.message}")
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(AbstractAnalysisTask::class.java)
  }
}