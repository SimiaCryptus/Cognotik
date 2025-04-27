package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ApiModel
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
        api: API,
        contextFn: () -> List<String> = { emptyList() },
        describer: TypeDescriber
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
                        toInput(userMessage) + contextFn(),
                        describer
                    )
                },
                outputFn = {
                    try {
                        com.simiacryptus.cognotik.plan.PlanUtil.render(
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
                        userMessages.map { it.first },
                        describer
                    )
                },
            ).call().let {
                TaskBreakdownWithPrompt(
                    prompt = userMessage,
                    plan = com.simiacryptus.cognotik.plan.PlanUtil.filterPlan { it.obj } ?: emptyMap(),
                    planText = it.text
                )
            }
        else {
            newPlan(
                api,
                planSettings,
                toInput(userMessage) + contextFn(),
                describer
            ).let {
                TaskBreakdownWithPrompt(
                    prompt = userMessage,
                    plan = com.simiacryptus.cognotik.plan.PlanUtil.filterPlan { it.obj } ?: emptyMap(),
                    planText = it.text
                )
            }
        }
    }

    open fun newPlan(
        api: API,
        planSettings: PlanSettings,
        inStrings: List<String>,
        describer: TypeDescriber
    ): ParsedResponse<Map<String, TaskConfigBase>> {
        planSettings.workingDir?.apply { File(this).mkdirs() }
        val planningActor = planSettings.planningActor(describer)
        return planningActor.respond(
            messages = planningActor.chatMessages(inStrings),
            input = inStrings,
            api = api
        ).map(Map::class.java) {
            it.tasksByID ?: emptyMap<String, TaskConfigBase>()
        } as ParsedResponse<Map<String, TaskConfigBase>>
    }

    open fun inputFn(
        codeFiles: Map<Path, String>,
        files: Array<File>,
        root: Path
    ) = { str: String ->
        listOf(
            if (!codeFiles.all { it.key.toFile().isFile } || codeFiles.size > 2) "Files:\n${
                codeFiles.keys.joinToString(
                    "\n"
                ) { "* $it" }
            }  " else {
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