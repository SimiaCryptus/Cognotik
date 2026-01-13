package com.simiacryptus.cognotik.plan.cognitive

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient

@JsonDeserialize(using = CognitiveSchemaStrategyDeserializer::class)
@JsonSerialize(using = CognitiveSchemaStrategySerializer::class)
abstract class CognitiveSchemaStrategy(
    name: String,
    val description: String
) : DynamicEnum<CognitiveSchemaStrategy>(name) {
    abstract fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any

    abstract fun update(
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any

    abstract fun formatState(state: Any): String
    abstract fun getTaskSelectionGuidance(state: Any): String

    companion object {
        val ProjectManager = ProjectManagerStrategy()
        val ScientificMethod = ScientificMethodStrategy()
        val AgileDeveloper = AgileDeveloperStrategy()
        val CriticalAuditor = CriticalAuditorStrategy()
        val CreativeWriter = CreativeWriterStrategy()

        init {
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, ProjectManager)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, ScientificMethod)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, AgileDeveloper)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, CriticalAuditor)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, CreativeWriter)
        }

        fun values() = DynamicEnum.values(CognitiveSchemaStrategy::class.java)
        fun valueOf(name: String) = DynamicEnum.valueOf(CognitiveSchemaStrategy::class.java, name)
    }

}

class CognitiveSchemaStrategySerializer : JsonSerializer<CognitiveSchemaStrategy>() {
    override fun serialize(value: CognitiveSchemaStrategy, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.name)
    }
}

class CognitiveSchemaStrategyDeserializer : JsonDeserializer<CognitiveSchemaStrategy>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CognitiveSchemaStrategy {
        return CognitiveSchemaStrategy.valueOf(p.text)
    }
}


open class ProjectManagerStrategy(
    name: String = "Project Manager",
    description: String = "Standard goal-oriented planning.",
    val initPrompt: String? = null,
    val updatePrompt: String? = null
) : CognitiveSchemaStrategy(name, description) {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        return ParsedAgent(
            name = "ThinkingStatusInitializer",
            resultClass = AdaptivePlanningMode.ReasoningState::class.java,
            exampleInstance = AdaptivePlanningMode.ReasoningState(
                initialPrompt = "Example prompt",
                goals = AdaptivePlanningMode.Goals(
                    shortTerm = mutableListOf(AdaptivePlanningMode.Goal("Understand the user's request")),
                    longTerm = mutableListOf(AdaptivePlanningMode.Goal("Complete the user's task"))
                ),
                knowledge = AdaptivePlanningMode.Knowledge(
                    facts = mutableListOf("Initial Context: User's request received"),
                    openQuestions = mutableListOf("What is the first task?")
                ),
                executionContext = AdaptivePlanningMode.ExecutionContext(
                    nextSteps = mutableListOf("Analyze the initial prompt", "Identify key objectives"),
                )
            ),
            prompt = initPrompt ?: """
        Initialize a comprehensive thinking status for an AI assistant based on the user's prompt.
        Goals:
        1. Short-term goals: Define immediate objectives that can be accomplished in 1-2 iterations
        2. Long-term goals: Outline the overall project objectives and desired end state
        Knowledge Base:
        1. Facts: Extract concrete information and requirements from the prompt
        2. Hypotheses: Form initial assumptions that need validation
        3. Open Questions: List critical uncertainties and information gaps
        Execution Context:
        1. Next Steps: Plan initial 2-3 concrete actions
        2. Potential Challenges: Identify possible obstacles and constraints
        3. Available Resources: List tools and capabilities at disposal
        Analysis Guidelines:
        * Break down complex requirements into manageable components
        * Consider both technical and non-technical aspects
        * Identify dependencies and prerequisites
        * Maintain alignment between short-term actions and long-term goals
        * Ensure scalability and maintainability of the approach
      """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj.apply {
            initialPrompt = userMessage
        }
    }

    override fun update(
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as AdaptivePlanningMode.ReasoningState
        return ParsedAgent(
            name = "UpdateQuestionsActor",
            resultClass = AdaptivePlanningMode.ReasoningState::class.java,
            exampleInstance = AdaptivePlanningMode.ReasoningState(
                initialPrompt = "Create a Python script to analyze log files and generate a summary report",
                confidence = 0.8,
                iteration = 1,
                goals = AdaptivePlanningMode.Goals(
                    shortTerm = mutableListOf(
                        AdaptivePlanningMode.Goal(
                            "Understand log file format requirements",
                            isRigid = true,
                            priority = 1
                        ),
                        AdaptivePlanningMode.Goal("Define report structure", priority = 2),
                        AdaptivePlanningMode.Goal("Plan implementation approach", priority = 3)
                    ),
                    longTerm = mutableListOf(
                        AdaptivePlanningMode.Goal("Deliver working Python script", isRigid = true, priority = 1),
                        AdaptivePlanningMode.Goal("Ensure robust error handling", priority = 2),
                        AdaptivePlanningMode.Goal("Provide documentation", priority = 3)
                    )
                ),
                knowledge = AdaptivePlanningMode.Knowledge(
                    facts = mutableListOf(
                        "Project requires Python programming",
                        "Output format needs to be a summary report",
                        "Input consists of log files"
                    ),
                    hypotheses = mutableListOf(
                        "Log files might be in different formats",
                        "Performance optimization may be needed for large files"
                    ),
                    openQuestions = mutableListOf(
                        "What is the specific log file format?",
                        "Are there any performance requirements?",
                        "What specific metrics should be included in the report?"
                    )
                ),
                executionContext = AdaptivePlanningMode.ExecutionContext(
                    completedTasks = mutableListOf(
                        "Initial requirements analysis",
                        "Project scope definition"
                    ),
                    currentTask = AdaptivePlanningMode.CurrentTask(
                        taskId = "TASK_003",
                        description = "Design log parsing algorithm"
                    ),
                    nextSteps = mutableListOf(
                        "Implement log file reader",
                        "Create report generator",
                        "Add error handling",
                        "Invoke reflect task if needed"
                    )
                )
            ),
            prompt = updatePrompt ?: """
      Given the current thinking status, the last completed task, its result, and any repeating error signals,
      update the open questions and next steps to guide the planning process.
      Reflect on what went well and what could be improved.
      Reassess the goals (paying attention to priorities and rigidity) and adjust the confidence level.
      If error patterns are recurring or progress slows, trigger a reflection loop by adding a 'reflect' task.
    """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current thinking status: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.flatMap { record ->
                        val t: TaskExecutionConfig? = record.task
                        listOf(
                            "Completed task: ${t?.task_description}",
                            "Task result: ${record.result}",
                            record.reflections?.let { "Reflection: Positive: ${it.positiveNotes}, Improvements: ${it.improvementSuggestions}" }
                                ?: "")
                    } +
                    (userMessage?.let { listOf("User message: $it") } ?: listOf()),
        ).obj.apply {
            knowledge?.facts?.apply {
                this.addAll(completedTasks.mapIndexed { index, record ->
                    "Task ${(executionContext?.completedTasks?.size ?: 0) + index + 1} Result: ${record.result}"
                })
            }
        }
    }

    override fun formatState(state: Any): String {
        return "```json\n${JsonUtil.toJson(state)}\n```"
    }

    override fun getTaskSelectionGuidance(state: Any): String {
        return "Please choose the next single task to execute based on the current status.\nIf there are no tasks to execute, return {}."
    }
}

data class ScientificState(
    @Description("The core question or problem being investigated.")
    val researchQuestion: String? = null,
    @Description("List of hypotheses with confidence levels and evidence requirements.")
    val currentHypotheses: MutableList<Hypothesis>? = null,
    @Description("Facts that have been verified through evidence.")
    val establishedFacts: MutableList<String>? = null,
    @Description("Theories that have been proven false.")
    val refutedTheories: MutableList<String>? = null,
    @Description("Log of experiments or investigations performed.")
    val experimentLog: MutableList<String>? = null
)

data class Hypothesis(
    val statement: String = "",
    val confidence: Double = 0.0,
    val evidenceNeeded: String = ""
)

class ScientificMethodStrategy : CognitiveSchemaStrategy("Scientific Researcher", "Hypothesis-driven investigation.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        return ParsedAgent(
            name = "ScientificInitializer",
            resultClass = ScientificState::class.java,
            exampleInstance = ScientificState(
                researchQuestion = "Why is the system crashing?",
                currentHypotheses = mutableListOf(
                    Hypothesis("Memory leak in loop", 0.4, "Heap dump analysis"),
                    Hypothesis("Database timeout", 0.3, "Log timestamp correlation")
                ),
                establishedFacts = mutableListOf("Crashes occur every 24 hours"),
                refutedTheories = mutableListOf("Disk space full"),
                experimentLog = mutableListOf("Checked disk space")
            ),
            prompt = """
                Formulate a scientific research plan based on the user request.
                1. Define the core research question.
                2. Propose initial hypotheses with confidence levels.
                3. Identify what evidence is needed to prove/disprove them.
                4. List any known facts provided in the prompt.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }

    override fun update(
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as ScientificState
        return ParsedAgent(
            name = "ScientificUpdater",
            resultClass = ScientificState::class.java,
            exampleInstance = state,
            prompt = """
                Analyze the results of the recent tasks.
                1. Did the results confirm or refute any hypothesis?
                2. Move proven hypotheses to established facts.
                3. Move disproven hypotheses to refuted theories.
                4. Update confidence levels based on new evidence.
                5. Add new hypotheses if new questions arise.
                6. Update the experiment log.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
        return "Select tasks specifically designed to falsify or validate the top hypothesis. Prioritize information gathering over content generation."
    }
}

data class AgileState(
    val userStory: String? = null,
    val acceptanceCriteria: MutableList<String>? = null,
    val currentPhase: String? = null, // "TEST_FAILING", "IMPLEMENTING", "REFACTORING"
    val knownBugs: MutableList<String>? = null,
    val todoList: MutableList<String>? = null
)

class AgileDeveloperStrategy : CognitiveSchemaStrategy("Agile Developer", "Iterative Test-Driven Development.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        return ParsedAgent(
            name = "AgileInitializer",
            resultClass = AgileState::class.java,
            exampleInstance = AgileState(
                userStory = "As a user, I want to login so that I can access my data",
                acceptanceCriteria = mutableListOf("Valid credentials logs in", "Invalid credentials shows error"),
                currentPhase = "TEST_FAILING",
                knownBugs = mutableListOf(),
                todoList = mutableListOf("Create login test", "Implement login function")
            ),
            prompt = """
                Break the request into a User Story and Acceptance Criteria.
                Initialize the process in the 'TEST_FAILING' phase (TDD).
                Create a TODO list of small, incremental steps.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }

    override fun update(
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as AgileState
        return ParsedAgent(
            name = "AgileUpdater",
            resultClass = AgileState::class.java,
            exampleInstance = state,
            prompt = """
                Update the Agile state based on task results.
                - If in TEST_FAILING and tests passed, move to REFACTORING.
                - If in TEST_FAILING and tests failed (as expected), move to IMPLEMENTING.
                - If in IMPLEMENTING and tests pass, move to REFACTORING.
                - If in REFACTORING and code is clean, pick next TODO and move to TEST_FAILING.
                - Update known bugs and TODO list.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
        val s = state as AgileState
        return when (s.currentPhase) {
            "TEST_FAILING" -> "Create a test file or run existing tests to confirm failure."
            "IMPLEMENTING" -> "Write code to satisfy the failing test."
            "REFACTORING" -> "Optimize the code without changing behavior."
            else -> "Check acceptance criteria and pick the next item."
        }
    }
}

data class AuditState(
    val targetScope: String? = null,
    val riskAssessment: MutableList<Risk>? = null,
    val complianceChecklist: MutableMap<String, Boolean>? = null,
    val vulnerabilitiesFound: MutableList<String>? = null,
    val finalVerdict: String? = null
)

data class Risk(
    val description: String = "",
    val severity: String = "LOW",
    val status: String = "OPEN"
)

class CriticalAuditorStrategy : CognitiveSchemaStrategy("Critical Auditor", "Security and logic validation.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        return ParsedAgent(
            name = "AuditInitializer",
            resultClass = AuditState::class.java,
            exampleInstance = AuditState(
                targetScope = "Login Module",
                riskAssessment = mutableListOf(Risk("SQL Injection", "HIGH", "OPEN")),
                complianceChecklist = mutableMapOf("GDPR" to false),
                vulnerabilitiesFound = mutableListOf(),
                finalVerdict = "PENDING"
            ),
            prompt = """
                Identify potential risks, attack vectors, and compliance requirements in the user request.
                Define the scope of the audit.
                Initialize the risk assessment.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }

    override fun update(
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as AuditState
        return ParsedAgent(
            name = "AuditUpdater",
            resultClass = AuditState::class.java,
            exampleInstance = state,
            prompt = """
                Review the output. Be extremely critical.
                - If any error or weakness is found, log it in vulnerabilities.
                - Update risk status (MITIGATED, CONFIRMED, OPEN).
                - Update compliance checklist.
                - If serious issues found, escalate severity.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
        return "Choose tasks that stress-test the system. Try to break the implementation. Do not fix issues, only report them."
    }
}

data class NarrativeState(
    val theme: String? = null,
    val targetAudience: String? = null,
    val outline: MutableList<Chapter>? = null,
    val currentSection: String? = null,
    val toneCheck: String? = null // e.g., "Too formal", "Just right"
)

data class Chapter(
    val title: String = "",
    val summary: String = "",
    val status: String = "DRAFT"
)

class CreativeWriterStrategy : CognitiveSchemaStrategy("Creative Writer", "Narrative and content generation.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        return ParsedAgent(
            name = "WriterInitializer",
            resultClass = NarrativeState::class.java,
            exampleInstance = NarrativeState(
                theme = "Cyberpunk Noir",
                targetAudience = "Young Adults",
                outline = mutableListOf(Chapter("The Setup", "Hero meets villain", "TODO")),
                currentSection = "The Setup",
                toneCheck = "Pending"
            ),
            prompt = """
                Develop a narrative structure based on the user request.
                Define the theme and target audience.
                Create a high-level outline of chapters or sections.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }

    override fun update(
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as NarrativeState
        return ParsedAgent(
            name = "WriterUpdater",
            resultClass = NarrativeState::class.java,
            exampleInstance = state,
            prompt = """
                Review the generated content.
                - Check if the tone matches the theme.
                - Update the status of chapters (DRAFT, REVIEWED, DONE).
                - Move to the next section if the current one is satisfactory.
                - Adjust the outline if the story evolves differently.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
        return "Focus on generating content. If the tone is off, select a task to rewrite or edit. Do not execute code unless it is to generate text."
    }
}