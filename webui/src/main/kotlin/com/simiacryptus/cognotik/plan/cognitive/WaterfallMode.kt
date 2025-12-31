package com.simiacryptus.cognotik.plan.cognitive
import com.simiacryptus.cognotik.agents.ParsedAgent

import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph
import com.simiacryptus.cognotik.plan.PlanUtil.filterPlan
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.AgentPatterns
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path

/**
 * A cognitive mode that implements the traditional plan-ahead strategy.
 */
open class WaterfallMode(
    task: SessionTask,
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<WaterfallMode.WaterfallModeConfig>(
    task,
    orchestrationConfig,
    session,
    user
) {
    class WaterfallModeConfig(
        var planFile: String? = null,
        var variables: Map<String, String> = emptyMap()
    ) : CognitiveModeConfig(type = CognitiveModeType.Waterfall)


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


            val plan = if (config.planFile != null) {
                loadPrePlanned(userMessage, coordinator.root, task)
            } else {
                val describer = TaskContextYamlDescriber(orchestrationConfig)
                Tasks.initDescriber(orchestrationConfig, describer)
                val p = initialPlan(
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
                    stream.write("\n## Generated Plan\n\n${p.planText}\n\n".toByteArray())
                    stream.flush()
                }
                // Save plan to file for PrePlanned mode
                try {
                    val planFile = coordinator.root.resolve("plan.json").toFile()
                    JsonUtil.toJson(p).let { json ->
                        planFile.writeText(json)
                        task.add("Plan saved to [${planFile.name}](${task.linkTo("plan.json")})".renderMarkdown())
                    }
                } catch (e: Exception) {
                    log.warn("Failed to save plan json", e)
                }
                p
            }
            task.header("Executing Plan")


            coordinator.executePlan(
                plan = plan.plan,
                task = task,
                userMessage = userMessage,
                orchestrationConfig = orchestrationConfig,
                // Use the budgeted and task-specific client
            )
            task.complete()
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
                heading = "Plan Generation",
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
            if (!codeFiles.all { it.key.toFile().isFile } || codeFiles.size > 2) {
                "Files:\n${codeFiles.keys.joinToString("\n") { "* $it" }}"
            } else {
                files.joinToString("\n\n") {
                    val path = root.relativize(it.toPath())
                    "\n## $path\n\n${(codeFiles[path] ?: "").let { "$TRIPLE_TILDE\n${it}\n$TRIPLE_TILDE" }}"
                }
            },
            str
        )
    }
    private fun loadPrePlanned(userMessage: String, root: Path, task: SessionTask): TaskBreakdownWithPrompt {
        val parsedConfig = parseConfig(userMessage, root.toString(), task)
        task.add("Loading plan from `${parsedConfig.planFile}` with variables: ${parsedConfig.variables}".renderMarkdown())
        val planFile = root.resolve(parsedConfig.planFile!!).toFile()
        if (!planFile.exists()) {
            throw IllegalArgumentException("Plan file not found: ${planFile.absolutePath}")
        }
        // Load and substitute variables
        val rawJson = planFile.readText()
        val genericPlan: MutableMap<String, Any> = JsonUtil.fromJson(rawJson, MutableMap::class.java)
        val processedPlan = replaceVariables(genericPlan, parsedConfig.variables)
        // Deserialize
        val planWrapper: TaskBreakdownWithPrompt = JsonUtil.fromJson(
            JsonUtil.toJson(processedPlan),
            TaskBreakdownWithPrompt::class.java
        )
        task.add("Plan loaded with ${planWrapper.plan.size} steps.")
        return planWrapper
    }
    private fun parseConfig(message: String, root: String, task: SessionTask): WaterfallModeConfig {
        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        val availableFiles = getAvailableFiles(Path(root))
            .filter { it.endsWith(".json") }
            .joinToString("\n") { "      - $it" }
        val agent = ParsedAgent(
            name = "PrePlannedConfigParser",
            resultClass = WaterfallModeConfig::class.java,
            exampleInstance = WaterfallModeConfig(
                planFile = config.planFile,
                variables = config.variables
            ),
            prompt = """
Analyze the user request to identify the plan file to use and the variables to substitute.
The user wants to execute a pre-defined plan stored in a JSON file.
1. Identify the JSON file mentioned. If not explicitly mentioned, look for '${config.planFile}' or the most relevant file in the list below.
2. Extract any other parameters or instructions as variables. The keys should match placeholders likely found in the plan (e.g., {{key}}).
Available JSON files:
$availableFiles
            """,
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = 0.1,
            describer = describer
        )
        return agent.answer(listOf(message)).obj
    }
    private fun replaceVariables(node: Any?, variables: Map<String, String>): Any? {
        return when (node) {
            is String -> {
                var result: String = node
                variables.forEach { (k, v) ->
                    result = result.replace("{{$k}}", v)
                }
                result
            }
            is Map<*, *> -> node.entries.associate { (k, v) -> k to replaceVariables(v, variables) }
            is List<*> -> node.map { replaceVariables(it, variables) }
            else -> node
        }
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
        task.add("""
            Writing transcript to:
            * [Markdown]($link)
            * [HTML](${link.removeSuffix(".md")}.html)
            * [PDF](${link.removeSuffix(".md")}.pdf)
        """.trimIndent().renderMarkdown())
        return markdownTranscript
    }

    companion object {
        val inputCnt = 1
    }
}