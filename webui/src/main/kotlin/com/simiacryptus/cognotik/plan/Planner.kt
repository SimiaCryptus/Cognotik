package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.nio.file.Path

open class Planner {

    open fun initialPlan(
        codeFiles: Map<Path, String>,
        files: Array<File>,
        root: Path,
        task: SessionTask,
        userMessage: String,
        orchestrationConfig: OrchestrationConfig,
        contextFn: () -> List<String> = { emptyList() },
        describer: TypeDescriber
    ): TaskBreakdownWithPrompt {
        val toInput = inputFn(codeFiles, files, root)
        task.echo(userMessage.renderMarkdown())
        return if (!orchestrationConfig.autoFix)
            Discussable(
                task = task,
                heading = "",
                userMessage = { userMessage },
                initialResponse = {
                    newPlan(
                        orchestrationConfig,
                        toInput(userMessage) + contextFn(),
                        describer
                    )
                },
                outputFn = {
                    try {
                        PlanUtil.render(
                            withPrompt = TaskBreakdownWithPrompt(
                                prompt = userMessage,
                                plan = it.obj,
                                planText = it.text
                            )
                        )
                    } catch (e: Throwable) {
                        log.warn("Error rendering task breakdown", e)
                        task.error(e)
                        e.message ?: e.javaClass.simpleName
                    }
                },
                reviseResponse = { userMessages: List<Pair<String, ModelSchema.Role>> ->
                    newPlan(
                        orchestrationConfig,
                        userMessages.map { it.first },
                        describer
                    )
                },
            ).call().let {
                TaskBreakdownWithPrompt(
                    prompt = userMessage,
                    plan = PlanUtil.filterPlan { it?.obj } ?: emptyMap(),
                    planText = it?.text ?: "(no plan generated)"
                )
            }
        else {
            newPlan(
                orchestrationConfig,
                toInput(userMessage) + contextFn(),
                describer
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
        orchestrationConfig: OrchestrationConfig,
        inStrings: List<String>,
        describer: TypeDescriber
    ): ParsedResponse<Map<String, TaskExecutionConfig>> {
        orchestrationConfig.absoluteWorkingDir?.apply { File(this).mkdirs() }
        val planningActor = orchestrationConfig.planningActor(describer)
        return planningActor.respond(
            messages = planningActor.chatMessages(inStrings),
            input = inStrings,
        ).map(Map::class.java) {
            it.tasksByID ?: emptyMap<String, TaskExecutionConfig>()
        } as ParsedResponse<Map<String, TaskExecutionConfig>>
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