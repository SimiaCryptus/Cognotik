package com.simiacryptus.skyenet.apps.plan

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.skyenet.Discussable
import com.simiacryptus.skyenet.apps.general.renderMarkdown
import com.simiacryptus.skyenet.core.actors.ParsedResponse
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

open class Planner {

  open fun initialPlan(
    codeFiles: Map<Path, String>,
    files: Array<File>,
    root: Path,
    task: SessionTask,
    userMessage: String,
    ui: ApplicationInterface,
    planSettings: PlanSettings,
    api: API
  ): TaskBreakdownWithPrompt {
    val api = (api as ChatClient).getChildClient(task)
    val toInput = inputFn(codeFiles, files, root)
    task.echo(userMessage.renderMarkdown)
    return if (!planSettings.autoFix)
      Discussable(
        task = task,
        heading = "",
        userMessage = { userMessage },
        initialResponse = {
          newPlan(
            api,
            planSettings,
            toInput(userMessage)
          )
        },
        outputFn = {
          try {
            PlanUtil.render(
              withPrompt = TaskBreakdownWithPrompt(
                prompt = userMessage,
                plan = it.obj,
                planText = it.text
              ),
              ui = ui
            )
          } catch (e: Throwable) {
            log.warn("Error rendering task breakdown", e)
            task.error(ui, e)
            e.message ?: e.javaClass.simpleName
          }
        },
        ui = ui,
        reviseResponse = { userMessages: List<Pair<String, ApiModel.Role>> ->
          newPlan(
            api,
            planSettings,
            userMessages.map { it.first })
        },
      ).call().let {
        TaskBreakdownWithPrompt(
          prompt = userMessage,
          plan = PlanUtil.filterPlan { it.obj } ?: emptyMap(),
          planText = it.text
        )
      }
    else {
      newPlan(
        api,
        planSettings,
        toInput(userMessage)
      ).let {
        TaskBreakdownWithPrompt(
          prompt = userMessage,
          plan = PlanUtil.filterPlan { it.obj } ?: emptyMap(),
          planText = it.text
        )
      }
    }
  }

  open fun newPlan(
    api: API,
    planSettings: PlanSettings,
    inStrings: List<String>
  ): ParsedResponse<Map<String, TaskConfigBase>> {
    val planningActor = planSettings.planningActor()
    return planningActor.respond(
      messages = planningActor.chatMessages(inStrings),
      input = inStrings,
      api = api
    ).map(Map::class.java) { it.tasksByID ?: emptyMap<String, TaskConfigBase>() } as ParsedResponse<Map<String, TaskConfigBase>>
  }


  open fun inputFn(
    codeFiles: Map<Path, String>,
    files: Array<File>,
    root: Path
  ) = { str: String ->
    listOf(
      if (!codeFiles.all { it.key.toFile().isFile } || codeFiles.size > 2) "Files:\n${codeFiles.keys.joinToString("\n") { "* $it" }}  " else {
        files.joinToString("\n\n") {
          val path = root.relativize(it.toPath())
          "## $path\n\n${(codeFiles[path] ?: "").let { "$TRIPLE_TILDE\n${it}\n$TRIPLE_TILDE" }}"
        }
      },
      str
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(Planner::class.java)
  }
}