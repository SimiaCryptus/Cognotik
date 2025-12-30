package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph
import com.simiacryptus.cognotik.plan.PlanUtil.filterPlan
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.AgentPatterns
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*

/**
 * A cognitive mode that implements the traditional plan-ahead strategy.
 */
open class WaterfallMode(
    task: SessionTask,
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<CognitiveModeConfig>(
    task,
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(WaterfallMode::class.java)
    private var transcriptStream: FileOutputStream? = null

    override fun initialize() {
        log.debug("Initializing PlanAheadMode")
        transcriptStream = transcript(task)
    }

    override fun contextData(): List<String> = emptyList()

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        log.debug("Handling user message: $userMessage")
        transcriptStream?.let { stream ->
            stream.write("\n## User Message\n\n$userMessage\n\n".toByteArray())
            stream.flush()
        }
        execute(userMessage, task)
    }

    private fun execute(userMessage: String, task: SessionTask) {
        try {
            val coordinator = TaskOrchestrator(
                user = user,
                session = session,
                dataStorage = this.task.ui.dataStorage!!,
                root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                    ?: this.task.ui.dataStorage?.getSessionDir(
                        user,
                        session
                    )?.toPath() ?: File(".").toPath()
            )


            val describer = TaskContextYamlDescriber(orchestrationConfig)
            Tasks.initDescriber(orchestrationConfig, describer)
            val plan = initialPlan(
                codeFiles = coordinator.codeFiles,
                files = coordinator.files,
                root = coordinator.root,
                task = task,
                userMessage = userMessage,
                orchestrationConfig = orchestrationConfig,
                contextFn = { contextData() },
                describer = describer
            )
            transcriptStream?.let { stream ->
                stream.write("\n## Generated Plan\n\n${plan.planText}\n\n".toByteArray())
                stream.flush()
            }
            // Save plan to file for PrePlanned mode
            try {
                val planFile = coordinator.root.resolve("plan.json").toFile()
                JsonUtil.toJson(plan).let { json ->
                    planFile.writeText(json)
                    task.add("Plan saved to [${planFile.name}](${task.linkTo("plan.json")})")
                }
            } catch (e: Exception) {
                log.warn("Failed to save plan json", e)
            }


            coordinator.executePlan(
                plan = plan.plan,
                task = task,
                userMessage = userMessage,
                orchestrationConfig = orchestrationConfig,
                // Use the budgeted and task-specific client
            )
        } catch (e: Throwable) {
            task.error(e) // Report error on the current task
            log.error("Error in execute", e)
            transcriptStream?.let { stream ->
                stream.write("\n## Error\n\n```\n${e.message}\n${e.stackTraceToString()}\n```\n\n".toByteArray())
                stream.flush()
            }
        } finally {
            transcriptStream?.close()
        }
    }

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
                        describer,
                        task
                    )
                },
                outputFn = {
                    try {
                        render(
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
                        describer,
                        task
                    )
                },
            ).call().let {
                TaskBreakdownWithPrompt(
                    prompt = userMessage,
                    plan = filterPlan { it?.obj } ?: emptyMap(),
                    planText = it?.text ?: "(no plan generated)"
                )
            }
        else {
            newPlan(
                orchestrationConfig,
                toInput(userMessage) + contextFn(),
                describer,
                task
            ).let {
                TaskBreakdownWithPrompt(
                    prompt = userMessage,
                    plan = filterPlan { it.obj } ?: emptyMap(),
                    planText = it.text
                )
            }
        }
    }

    data class TaskBreakdownWithPrompt(
        val prompt: String,
        val plan: Map<String, TaskExecutionConfig>,
        val planText: String
    )

    fun render(
        withPrompt: TaskBreakdownWithPrompt
    ) = AgentPatterns.displayMapInTabs(
        mapOf(
            "Text" to withPrompt.planText.renderMarkdown(),
            "JSON" to "${TRIPLE_TILDE}json\n${JsonUtil.toJson(withPrompt)}\n${TRIPLE_TILDE}".renderMarkdown(),
            "Diagram" to (("```mermaid\n" + buildMermaidGraph(
                (filterPlan {
                    withPrompt.plan
                } ?: emptyMap()).toMutableMap()
            ) + "\n```\n").renderMarkdown())
        )
    )

    open fun newPlan(
        orchestrationConfig: OrchestrationConfig,
        inStrings: List<String>,
        describer: TypeDescriber,
        task: SessionTask
    ): ParsedResponse<Map<String, TaskExecutionConfig>> {
        orchestrationConfig.absoluteWorkingDir?.apply { File(this).mkdirs() }
        val planningActor = orchestrationConfig.planningActor(describer, task)
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
                    "\n## $path\n\n${(codeFiles[path] ?: "").let { "$TRIPLE_TILDE\n${it}\n$TRIPLE_TILDE" }}"
                }
            },
            str
        )
    }

    /**
     * Creates a transcript file for logging the session's interactions.
     * The transcript is written in Markdown format and includes links to HTML and PDF versions.
     *
     * @param task The session task used to create the file
     * @return FileOutputStream for writing to the transcript, or null if creation failed
     */
    private fun transcript(task: SessionTask): FileOutputStream? {
        val transcriptFile = this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>"
        )
        return markdownTranscript
    }

    companion object {
        val inputCnt = 1
    }
}