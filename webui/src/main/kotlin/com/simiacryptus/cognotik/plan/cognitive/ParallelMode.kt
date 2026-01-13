package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.cognitive.ConversationalMode.Companion.requestToTask
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

class ParallelModeConfig(
    var defaultConcurrency: Int = 4,
    var defaultMode: CombinationMode = CombinationMode.CrossJoin
) : CognitiveModeConfig(type = CognitiveModeType.Parallel) {
    enum class CombinationMode {
        CrossJoin,
        Zip
    }
}

open class ParallelMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<ParallelModeConfig>(
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(ParallelMode::class.java)


    override fun contextData(): List<String> = emptyList()

    data class ParallelPlan(
        val variables: Map<String, Any> = emptyMap(),
        val template: String = "",
        val concurrency: Int = 4,
        val mode: ParallelModeConfig.CombinationMode = ParallelModeConfig.CombinationMode.CrossJoin
    )

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        val transcript = task.transcript()
        try {
            task.echo(userMessage.renderMarkdown)

            transcript?.write("User Message: $userMessage\n".toByteArray())

            val root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                ?: task.ui.dataStorage?.getSessionDir(user, session)?.toPath()
                ?: File(".").toPath()
            val parser = createParserAgent(task)
            val plan = if (orchestrationConfig.autoFix) {
                parser.answer(listOf(userMessage)).obj
            } else {
                Discussable(
                    task = task,
                    heading = "Parallel Execution Plan",
                    userMessage = { userMessage },
                    initialResponse = { parser.answer(listOf(it)).obj },
                    outputFn = { plan ->
                        val expandedVariables = plan.variables.mapValues { (_, value) -> expandVariable(value, root) }
                        val combinations = generateCombinations(expandedVariables, plan.mode)
                        buildString {
                            append("<div>")
                            append("<b>Template:</b> <pre>${plan.template}</pre>")
                            append("<b>Concurrency:</b> ${plan.concurrency}<br/>")
                            append("<b>Mode:</b> ${plan.mode}<br/>")
                            append("<b>Tasks to run:</b> ${combinations.size}<br/>")
                            append("<details><summary>Variables</summary><pre>${JsonUtil.toJson(plan.variables)}</pre></details>")
                            append("<details><summary>Combinations (First 10)</summary><ul>")
                            combinations.take(10).forEach { append("<li>${JsonUtil.toJson(it)}</li>") }
                            append("</ul></details>")
                            append("</div>")
                        }
                    },
                    reviseResponse = { history ->
                        parser.answer(history.map { it.first }).obj
                    }
                ).call()!!
            }
            transcript?.write("Plan: ${JsonUtil.toJson(plan)}\n".toByteArray())


            val expandedVariables = plan.variables.mapValues { (_, value) -> expandVariable(value, root) }
            val combinations = generateCombinations(expandedVariables, plan.mode)

            task.header("Running ${combinations.size} tasks (Concurrency: ${plan.concurrency})", level = 3)

            val tabs = TabbedDisplay(task)
            val processor = FixedConcurrencyProcessor(task.ui.pool, plan.concurrency)

            val futures = combinations.map { combination ->
                val label = combination.values.joinToString(",") { it.toString() }
                val task = tabs.newTask(label)
                processor.submit {
                    try {
                        val renderedMessage = renderTemplate(plan.template, combination)
                        task.expandable("Parameters", "```json\n${JsonUtil.toJson(combination)}\n```".renderMarkdown())
                        task.expandable("Rendered Message", "```text\n${renderedMessage}\n```".renderMarkdown())
                        val (_, chosenTask) = requestToTask(
                            defaultModel = orchestrationConfig.defaultSmart.getChildClient(task),
                            fastModel = orchestrationConfig.defaultFast.getChildClient(task),
                            userMessage = renderedMessage,
                            orchestrationConfig = orchestrationConfig,
                            singleStage = true
                        )
                        task.expandable("Config", "```json\n${JsonUtil.toJson(chosenTask)}\n```".renderMarkdown())
                        val coordinator = TaskOrchestrator(
                            user = user,
                            session = session,
                            dataStorage = task.ui.dataStorage!!,
                            root = root
                        )
                        val impl = orchestrationConfig.getImpl(chosenTask)
                        var resultString = ""
                        impl.run(
                            agent = coordinator,
                            messages = listOf(userMessage),
                            task = task,
                            resultFn = { result ->
                                resultString = result
                                task.complete(result.renderMarkdown())
                            },
                            orchestrationConfig = orchestrationConfig
                        )
                        Result.success(resultString)
                    } catch (e: Throwable) {
                        task.error(e)
                        log.error("Error in parallel task $label", e)
                        Result.failure(e)
                    }
                }
            }

            val results = futures.map {
                try {
                    it.get() as Result<String>
                } catch (e: Exception) {
                    log.warn("Task failed", e)
                    Result.failure(e)
                }
            }
            val succeeded = results.count { it.isSuccess }
            val failed = results.count { it.isFailure }
            task.complete("All parallel tasks completed. $succeeded Succeeded, $failed Failed.")

        } catch (e: Throwable) {
            task.error(e)
            log.error("Error in ParallelMode", e)
        } finally {
            transcript?.close()
        }
    }

    private fun createParserAgent(task: SessionTask): ParsedAgent<ParallelPlan> {
        val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
        val taskDescriptions = availableTaskTypes.joinToString("\n") { taskType ->
            val impl = orchestrationConfig.getImpl(taskType)
            "* ${taskType.name}: ${impl.promptSegment()}"
        }

        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        return ParsedAgent(
            name = "ParallelConfigParser",
            resultClass = ParallelPlan::class.java,
            exampleInstance = ParallelPlan(
                variables = mapOf("file" to listOf("src/main.kt", "src/utils.kt")),
                template = """{"task_type": "CodingTask", "prompt": "Review the code in {{file}}"}""",
                concurrency = config.defaultConcurrency,
                mode = config.defaultMode
            ),
            prompt = """
Analyze the user request to identify parallel execution parameters.
Extract variables that represent lists of items to process (e.g., files, inputs).
Construct a template string that uses these variables (e.g., "{{variableName}}") to formulate a request describing the task to be performed.

Available task types that the downstream agent can perform:
$taskDescriptions

If the user mentions specific files or globs, include them in the variables map.
If the user specifies concurrency, set it; otherwise default to ${config.defaultConcurrency}.
If the user implies pairing items (e.g. "zip", "pair", "corresponding"), set mode to Zip. Default is ${config.defaultMode}.
            """ + (orchestrationConfig.workingDir?.let { root ->
                "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
            } ?: ""),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = 0.1,
            describer = describer
        )
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

    private fun generateCombinations(
        variables: Map<String, List<Any>>,
        mode: ParallelModeConfig.CombinationMode
    ): List<Map<String, Any>> {
        if (variables.isEmpty()) return listOf(emptyMap())

        val keys = variables.keys.toList()

        return when (mode) {
            ParallelModeConfig.CombinationMode.CrossJoin -> {
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

            ParallelModeConfig.CombinationMode.Zip -> {
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

    companion object {
        val inputCnt = 1
    }
}