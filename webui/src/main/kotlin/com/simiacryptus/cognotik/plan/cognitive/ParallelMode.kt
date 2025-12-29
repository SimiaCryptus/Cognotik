package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.cognitive.ConversationalMode.Companion.requestToTask
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

open class ParallelMode(
    override val task: SessionTask,
    override val orchestrationConfig: OrchestrationConfig,
    override val session: Session,
    override val user: User = defaultUser
) : CognitiveMode {

    private val log = LoggerFactory.getLogger(ParallelMode::class.java)

    override fun initialize() {
        log.debug("Initializing ParallelMode")
    }

    override fun contextData(): List<String> = emptyList()
    enum class CombinationMode {
        CrossJoin,
        Zip
    }

    data class Config(
        val variables: Map<String, Any> = emptyMap(),
        val template: String = "",
        val concurrency: Int = 4,
        val mode: CombinationMode = CombinationMode.CrossJoin
    )

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        try {
            task.echo(userMessage.renderMarkdown)

            val config = parseConfig(userMessage)
            val root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                ?: task.ui.dataStorage?.getSessionDir(user, session)?.toPath()
                ?: File(".").toPath()

            val expandedVariables = config.variables.mapValues { (_, value) -> expandVariable(value, root) }
            val combinations = generateCombinations(expandedVariables, config.mode)

            task.add("Running ${combinations.size} tasks with concurrency ${config.concurrency}...")

            val tabs = TabbedDisplay(task)
            val processor = FixedConcurrencyProcessor(task.ui.pool, config.concurrency)

            val futures = combinations.map { combination ->
                processor.submit {
                    val task = task.ui.newTask(cancelable = false, root = false)
                    val label = combination.values.joinToString(",") { it.toString() }.take(30)
                    synchronized(tabs) {
                        tabs[label] = task.placeholder
                    }

                    try {
                        val renderedMessage = renderTemplate(config.template, combination)
                        task.add("Parameters: \n```json\n${JsonUtil.toJson(combination)}\n```".renderMarkdown())
                        task.add("Rendered Message: \n```text\n${renderedMessage}\n```".renderMarkdown())
                        val (_, chosenTask) = requestToTask(
                            defaultModel = orchestrationConfig.defaultSmart.getChildClient(task),
                            fastModel = orchestrationConfig.defaultFast.getChildClient(task),
                            userMessage = renderedMessage,
                            orchestrationConfig = orchestrationConfig,
                            singleStage = true
                        )
                        task.add("Config: \n```json\n${JsonUtil.toJson(chosenTask)}\n```".renderMarkdown())
                        val coordinator = TaskOrchestrator(
                            user = user,
                            session = session,
                            dataStorage = task.ui.dataStorage!!,
                            root = root
                        )
                        val impl = TaskType.getImpl(orchestrationConfig, chosenTask)
                        impl.run(
                            agent = coordinator,
                            messages = listOf(userMessage),
                            task = task,
                            resultFn = { result ->
                                task.complete(result.renderMarkdown())
                            },
                            orchestrationConfig = orchestrationConfig
                        )
                    } catch (e: Throwable) {
                        task.error(e)
                        log.error("Error in parallel task $label", e)
                    }
                }
            }

            futures.forEach {
                try {
                    it.get()
                } catch (e: Exception) {
                    log.warn("Task failed", e)
                }
            }
            task.complete("All parallel tasks completed.")

        } catch (e: Throwable) {
            task.error(e)
            log.error("Error in ParallelMode", e)
        }
    }

    private fun parseConfig(message: String): Config {
        val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
        val taskDescriptions = availableTaskTypes.joinToString("\n") { taskType ->
            val impl = TaskType.getImpl(orchestrationConfig, taskType)
            "* ${taskType.name}: ${impl.promptSegment()}"
        }

        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        val agent = ParsedAgent(
            name = "ParallelConfigParser",
            resultClass = Config::class.java,
            exampleInstance = Config(
                variables = mapOf("file" to listOf("src/main.kt", "src/utils.kt")),
                template = """{"task_type": "CodingTask", "prompt": "Review the code in {{file}}"}""",
                concurrency = 2,
                mode = CombinationMode.CrossJoin
            ),
            prompt = """
Analyze the user request to identify parallel execution parameters.
Extract variables that represent lists of items to process (e.g., files, inputs).
Construct a template string that uses these variables (e.g., "{{variableName}}") to formulate a request describing the task to be performed.

Available task types that the downstream agent can perform:
$taskDescriptions

If the user mentions specific files or globs, include them in the variables map.
If the user specifies concurrency, set it; otherwise default to 4.
If the user implies pairing items (e.g. "zip", "pair", "corresponding"), set mode to Zip. Default is CrossJoin.
            """ + (orchestrationConfig.workingDir?.let {root ->
                "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
            } ?: ""),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = 0.1,
            describer = describer
        )
        return agent.answer(listOf(message)).obj
    }

    private fun expandVariable(value: Any, root: Path): List<Any> {
        return when (value) {
            is String -> {
                if (value.contains("*") || value.contains("?") || (value.contains("[") && value.contains("]"))) {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:$value")
                    val files = mutableListOf<String>()
                    if (Files.exists(root)) {
                        Files.walk(root).use { stream ->
                            stream.filter { !it.isDirectory() }
                                .forEach { path ->
                                    val relative = try {
                                        root.relativize(path)
                                    } catch (e: IllegalArgumentException) {
                                        path
                                    }
                                    if (matcher.matches(relative)) {
                                        files.add(relative.toString())
                                    }
                                }
                        }
                    }
                    files.sorted()
                } else {
                    listOf(value)
                }
            }

            is List<*> -> value.filterNotNull()
            else -> listOf(value)
        }
    }

    private fun generateCombinations(variables: Map<String, List<Any>>, mode: CombinationMode): List<Map<String, Any>> {
        if (variables.isEmpty()) return listOf(emptyMap())

        val keys = variables.keys.toList()

        return when (mode) {
            CombinationMode.CrossJoin -> {
                var combinations = variables[keys[0]]!!.map { mapOf(keys[0] to it) }
                for (i in 1 until keys.size) {
                    val key = keys[i]
                    val values = variables[key]!!
                    combinations = combinations.flatMap { map ->
                        values.map { value ->
                            map + (key to value)
                        }
                    }
                }
                combinations
            }

            CombinationMode.Zip -> {
                val size = variables.values.minOf { it.size }
                (0 until size).map { i ->
                    keys.associateWith { key -> variables[key]!![i] }
                }
            }
        }
    }

    private fun renderTemplate(template: String, variables: Map<String, Any>): String {
        var result = template
        variables.forEach { (k, v) ->
            result = result.replace("{{$k}}", v.toString())
        }
        return result
    }

    companion object : CognitiveModeStrategy {
        override val inputCnt = 1
        override fun getCognitiveMode(
            task: SessionTask,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User
        ) = ParallelMode(task, orchestrationConfig, session, user)
    }
}