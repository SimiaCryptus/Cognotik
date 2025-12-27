package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo

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

    data class Config(
        val variables: Map<String, Any> = emptyMap(),
        val template: String = "",
        val concurrency: Int = 4
    )

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        try {
            val config = parseConfig(userMessage)
            val root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                ?: task.ui.dataStorage?.getSessionDir(user, session)?.toPath()
                ?: File(".").toPath()

            val expandedVariables = config.variables.mapValues { (key, value) ->
                expandVariable(key, value, root)
            }

            val combinations = generateCombinations(expandedVariables)

            task.echo("Running ${combinations.size} tasks with concurrency ${config.concurrency}...")

            val tabs = TabbedDisplay(task)
            val processor = FixedConcurrencyProcessor(task.ui.pool, config.concurrency)

            val futures = combinations.map { combination ->
                processor.submit {
                    val subTask = task.ui.newTask(cancelable = false, root = false)
                    val label = combination.values.joinToString(",") { it.toString() }.take(30)
                    synchronized(tabs) {
                        tabs[label] = subTask.placeholder
                    }

                    try {
                        val renderedMessage = renderTemplate(config.template, combination)
                        subTask.add("Parameters: ${JsonUtil.toJson(combination)}".renderMarkdown())

                        // Delegate to WaterfallMode for execution
                        val mode = WaterfallMode(subTask, orchestrationConfig, session, user)
                        mode.initialize()
                        mode.handleUserMessage(renderedMessage, subTask)
                    } catch (e: Throwable) {
                        subTask.error(e)
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
        try {
            return JsonUtil.fromJson(message, Config::class.java)
        } catch (e: Exception) {
            val jsonBlock = Regex("```json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL).find(message)
                ?: Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(message)

            if (jsonBlock != null) {
                return JsonUtil.fromJson(jsonBlock.groupValues.last(), Config::class.java)
            }
            throw IllegalArgumentException("Could not parse configuration. Please provide a JSON object with 'variables' and 'template'.")
        }
    }

    private fun expandVariable(key: String, value: Any, root: Path): List<Any> {
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

    private fun generateCombinations(variables: Map<String, List<Any>>): List<Map<String, Any>> {
        if (variables.isEmpty()) return listOf(emptyMap())

        val keys = variables.keys.toList()
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
        return combinations
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