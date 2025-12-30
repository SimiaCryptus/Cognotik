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

open class PrePlannedMode(
    override val task: SessionTask,
    override val orchestrationConfig: OrchestrationConfig,
    override val session: Session,
    override val user: User = defaultUser
) : CognitiveMode {

    private val log = LoggerFactory.getLogger(PrePlannedMode::class.java)

    override fun initialize() {
        log.debug("Initializing PrePlannedMode")
    }

    override fun contextData(): List<String> = emptyList()

    data class Config(
        val planFile: String = "plan.json",
        val variables: Map<String, String> = emptyMap()
    )

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        try {
            task.echo(userMessage.renderMarkdown())

            val root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                ?: task.ui.dataStorage?.getSessionDir(user, session)?.toPath()
                ?: File(".").toPath()

            val config = parseConfig(userMessage, root.toString())
            task.add("Loading plan from `${config.planFile}` with variables: ${config.variables}")

            val planFile = root.resolve(config.planFile).toFile()
            if (!planFile.exists()) {
                throw IllegalArgumentException("Plan file not found: ${planFile.absolutePath}")
            }

            // Load and substitute variables
            val rawJson = planFile.readText()
            val substitutedJson = renderTemplate(rawJson, config.variables)
            
            // Deserialize
            val planWrapper: WaterfallMode.TaskBreakdownWithPrompt = JsonUtil.fromJson(substitutedJson,
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

    private fun parseConfig(message: String, root: String): Config {
        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        
        val availableFiles = getAvailableFiles(Path(root))
            .filter { it.endsWith(".json") }
            .joinToString("\n") { "      - $it" }

        val agent = ParsedAgent(
            name = "PrePlannedConfigParser",
            resultClass = Config::class.java,
            exampleInstance = Config(
                planFile = "plan.json",
                variables = mapOf("topic" to "AI Safety", "language" to "Kotlin")
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

    private fun renderTemplate(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (k, v) ->
            result = result.replace("{{$k}}", v)
        }
        return result
    }

    companion object {
        val inputCnt = 1
    }
}