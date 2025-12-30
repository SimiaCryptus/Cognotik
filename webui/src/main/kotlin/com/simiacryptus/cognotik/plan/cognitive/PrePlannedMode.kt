package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import kotlin.io.path.Path

class PrePlannedModeConfig(
    var planFile: String = "plan.json",
    var variables: Map<String, String> = emptyMap()
) : CognitiveModeConfig()

open class PrePlannedMode(
    task: SessionTask,
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<PrePlannedModeConfig>(
    task,
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(PrePlannedMode::class.java)

    override fun initialize() {
        log.debug("Initializing PrePlannedMode")
    }

    override fun contextData(): List<String> = emptyList()


    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        try {
            task.echo(userMessage.renderMarkdown())

            val root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                ?: task.ui.dataStorage?.getSessionDir(user, session)?.toPath()
                ?: File(".").toPath()

            val parsedConfig = parseConfig(userMessage, root.toString())
            task.add("Loading plan from `${parsedConfig.planFile}` with variables: ${parsedConfig.variables}")

            val planFile = root.resolve(parsedConfig.planFile).toFile()
            if (!planFile.exists()) {
                throw IllegalArgumentException("Plan file not found: ${planFile.absolutePath}")
            }

            // Load and substitute variables
            val rawJson = planFile.readText()
            val genericPlan: MutableMap<String, Any> = JsonUtil.fromJson(rawJson, MutableMap::class.java)
            val processedPlan = replaceVariables(genericPlan, parsedConfig.variables)
            
            // Deserialize
            val planWrapper: WaterfallMode.TaskBreakdownWithPrompt = JsonUtil.fromJson(
                JsonUtil.toJson(processedPlan),
                WaterfallMode.TaskBreakdownWithPrompt::class.java)
            
            task.add("Plan loaded with ${planWrapper.plan.size} steps.")

            val coordinator = TaskOrchestrator(
                user = user,
                session = session,
                dataStorage = task.ui.dataStorage!!,
                root = root
            )

            coordinator.executePlan(
                plan = planWrapper.plan,
                task = task,
                userMessage = userMessage,
                orchestrationConfig = orchestrationConfig
            )

        } catch (e: Throwable) {
            task.error(e)
            log.error("Error in PrePlannedMode", e)
        }
    }

    private fun parseConfig(message: String, root: String): PrePlannedModeConfig {
        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        
        val availableFiles = getAvailableFiles(Path(root))
            .filter { it.endsWith(".json") }
            .joinToString("\n") { "      - $it" }

        val agent = ParsedAgent(
            name = "PrePlannedConfigParser",
            resultClass = PrePlannedModeConfig::class.java,
            exampleInstance = PrePlannedModeConfig(
                planFile = config.planFile,
                variables = config.variables
            ),
            prompt = """
Analyze the user request to identify the plan file to use and the variables to substitute.
The user wants to execute a pre-defined plan stored in a JSON file.

1. Identify the JSON file mentioned. If not explicitly mentioned, look for 'plan.json' or the most relevant file in the list below.
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

    companion object {
        val inputCnt = 1
    }
}