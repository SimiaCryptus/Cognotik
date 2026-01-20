# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/AbstractTask.kt

```
package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.docs.isDocumentFile
import com.simiacryptus.cognotik.plan.ExecutionState
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.exists

abstract class AbstractTask<T : TaskExecutionConfig, U : TaskTypeConfig>(
    val orchestrationConfig: OrchestrationConfig,
    val executionConfig: T?
) {

    var state: TaskState? = TaskState.Pending

    protected open val root: Path
        get() = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
            ?: throw IllegalStateException("Working directory not set")

    open val taskType: String = executionConfig?.task_type ?: this::class.simpleName ?: "UnknownTask"

    open val typeConfig: U?
        get() = taskType.let { task_type -> orchestrationConfig.taskSettings.values.firstOrNull { it.task_type == task_type } as? U }

    open val defaultSmart: ChatInterface
        get() = typeConfig?.model?.let { orchestrationConfig.instance(it) } ?: orchestrationConfig.defaultSmart

    open val defaultFast: ChatInterface
        get() = orchestrationConfig.defaultFast

    enum class TaskState {
        Pending,
        InProgress,
        Completed,
    }

    open fun getPriorCode(executionState: ExecutionState?) =
        executionConfig?.task_dependencies?.joinToString("\n\n\n") { dependency ->
            "# $dependency\n\n${executionState?.taskResult[dependency] ?: ""}"
        } ?: ""
    protected open fun renderTaskHeader(task: SessionTask, title: String? = null) {
        task.header(title ?: taskType)
        executionConfig?.task_description?.let {
            task.add("**Description:** $it".renderMarkdown())
        }
    }


    protected open fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val footerTask = ui.newTask(false)
        lateinit var textHandle: StringBuilder
        @Suppress("AssignedValueIsNeverRead")
        textHandle = footerTask.complete("""<div style="margin-top: 20px; border-top: 1px solid #ccc; padding-top: 10px;">""" + ui.hrefLink("Accept Result", classname = "href-link cmd-button") {
            try {
                textHandle.set("""<div class="cmd-button">Accepted</div>""")
                footerTask.complete()
            } catch (e: Throwable) {
                log.warn("Error", e)
            }
            fn()
        } + "</div>")!!
        return footerTask.placeholder
    }

    abstract fun promptSegment(): String

    abstract fun run(
        agent: TaskOrchestrator,
        messages: List<String> = listOf(),
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig,
    )

    fun getInputFileContent(
        files: List<String>?,
        root: Path,
        treatDocumentsAsText: Boolean = true,
    ): String = (files ?: listOf())
        .flatMap { pattern: String ->
            if (root.resolve(pattern).exists()) {
                return@flatMap listOf(root.resolve(pattern).toFile())
            }
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile(), treatDocumentsAsText = treatDocumentsAsText) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    it.isDirectory -> true
                    !matcher.matches(root.relativize(it.toPath())) -> false
                    else -> true
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                if (treatDocumentsAsText && file.isDocumentFile()) {
                    file.getDocumentReader().getText()
                } else {
                    "# $relativePath\n\n```\n${file.readText()}\n```"
                }
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    fun SessionTask.transcript(name: String = this@AbstractTask.taskType): FileOutputStream? {
        val transcriptFile: String = "transcript/${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(linkTo(transcriptFile), resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        add("[Transcript](${link.removeSuffix(".md")}.html)".renderMarkdown())
        return markdownTranscript
    }

    open fun initializeTranscript(task: SessionTask, name: String = this@AbstractTask.taskType): Pair<String, FileOutputStream?> {
        val transcriptFile = "transcript/${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.add(
            MarkdownUtil.renderMarkdown(
                "Writing transcript to <a href='$link' target='_blank'>transcript.md</a> " +
                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a>",
                ui = task.ui
            )
        )
        return Pair(link, markdownTranscript)
    }

    fun createTabbedDisplay(task: SessionTask) = TabbedDisplay(task)
    open fun writeToTranscript(stream: FileOutputStream, string: String) {
        stream.write(string.toByteArray())
        stream.flush()
    }

    companion object {
        val log = LoggerFactory.getLogger(AbstractTask::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/TaskExecutionConfig.kt

```
package com.simiacryptus.cognotik.plan.tools

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.simiacryptus.cognotik.describe.Description

@JsonTypeIdResolver(TaskExecutionConfig.PlanTaskTypeIdResolver::class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "task_type")
open class TaskExecutionConfig(
    @Description("An enumeration indicating the type of task to be executed. Must be a single value from the TaskType enum.")
    open val task_type: String? = null,
    @Description("A brief user-facing description of the task")
    open var task_description: String? = null,
    @Description("A list of IDs of tasks that must be completed before this task can be executed. This defines upstream dependencies ensuring proper task order and information flow.")
    var task_dependencies: MutableList<String>? = null,
    @Description("Ignore.")
    var state: AbstractTask.TaskState? = null
) {

    class PlanTaskTypeIdResolver : TypeIdResolverBase() {
        override fun idFromValue(value: Any) = when (value) {
            is TaskExecutionConfig -> if (value.task_type != null) {
                value.task_type
            } else {
                throw IllegalArgumentException("Unknown task type")
            }

            else -> {
                throw IllegalArgumentException("Unexpected value type: ${value.javaClass}")
            }
        }

        override fun idFromValueAndType(value: Any, suggestedType: Class<*>) = idFromValue(value)

        override fun typeFromId(context: DatabindContext, id: String): JavaType {
            val taskType = TaskType.valueOf(id.replace(" ", ""))
            val subType = context.constructType(taskType.executionConfigClass)
            return subType
        }

        override fun getMechanism(): JsonTypeInfo.Id {
            return JsonTypeInfo.Id.CUSTOM
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/TaskType.kt

```
package com.simiacryptus.cognotik.plan.tools

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.code.LanguageServerTask
import com.simiacryptus.cognotik.plan.tools.file.*
import com.simiacryptus.cognotik.plan.tools.games.GameEconomyTask
import com.simiacryptus.cognotik.plan.tools.games.GameLevelDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.reasoning.*
import com.simiacryptus.cognotik.plan.tools.run.*
import com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask
import com.simiacryptus.cognotik.plan.tools.session.JdbcSessionTask
import com.simiacryptus.cognotik.plan.tools.social.*
import com.simiacryptus.cognotik.plan.tools.writing.*
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer

@JsonDeserialize(using = TaskTypeDeserializer::class)
@JsonSerialize(using = TaskTypeSerializer::class)
class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
    name: String,
    val category: String,
    val taskClass: Class<out AbstractTask<out T,out U>>,
    val executionConfigClass: Class<out T>,
    val taskSettingsClass: Class<out U>,
    val description: String? = null,
    val tooltipHtml: String? = null,
) : DynamicEnum<TaskType<*, *>>(name) {
    companion object {

        private val taskConstructors by lazy {
            val taskConstructors: MutableMap<TaskType<*, *>, (OrchestrationConfig, TaskExecutionConfig?) -> AbstractTask<out TaskExecutionConfig, TaskTypeConfig>> =
                mutableMapOf()

            fun <T : TaskExecutionConfig, U : TaskTypeConfig> registerConstructor(
                taskType: TaskType<T, U>
            ) {
                try {
                    val constructor = taskType.getConstructor()
                    taskConstructors[taskType] = { settings: OrchestrationConfig, task: TaskExecutionConfig? ->
                        @Suppress("UNCHECKED_CAST")
                        constructor(settings, task as T?) as AbstractTask<TaskExecutionConfig, TaskTypeConfig>
                    }
                    register(taskType)
                } catch (e: NoSuchMethodException) {
                    throw RuntimeException("Failed to register task type: ${taskType.name}. Ensure that the task class has a constructor with parameters (OrchestrationConfig, ${taskType.executionConfigClass.name})", e)
                }

            }

            registerConstructor(AbductiveReasoningTask.AbductiveReasoning)
            registerConstructor(AbstractionLadderTask.AbstractionLadder)
            registerConstructor(AdversarialReasoningTask.AdversarialReasoning)
            registerConstructor(AnalogicalReasoningTask.AnalogicalReasoning)
            registerConstructor(ArticleGenerationTask.ArticleGeneration)
            registerConstructor(AutoFixTask.AutoFix)
            registerConstructor(BrainstormingTask.Brainstorming)
            registerConstructor(BusinessProposalTask.BusinessProposal)
            registerConstructor(CausalInferenceTask.CausalInference)
            registerConstructor(ChainOfThoughtTask.ChainOfThought)
            registerConstructor(ComicBookGenerationTask.ComicBookGeneration)
            registerConstructor(CommandSessionTask.CommandSession)
            registerConstructor(ConstraintRelaxationTask.ConstraintRelaxation)
            registerConstructor(ConstraintSatisfactionTask.ConstraintSatisfaction)
            registerConstructor(CounterfactualAnalysisTask.CounterfactualAnalysis)
            registerConstructor(CrawlerAgentTask.CrawlerAgent)
            registerConstructor(DataIngestTask.DataIngest)
            registerConstructor(DataTableCompilationTask.DataTableCompilation)
            registerConstructor(DecisionTreeTask.DecisionTree)
            registerConstructor(DecompositionSynthesisTask.DecompositionSynthesis)
            registerConstructor(DialecticalReasoningTask.DialecticalReasoning)
            registerConstructor(DiscussionTask.Discussion)
            registerConstructor(EmailCampaignTask.EmailCampaign)
            registerConstructor(EthicalReasoningTask.EthicalReasoning)
            registerConstructor(FileAppendTask.FileAppend)
            registerConstructor(FileModificationTask.FileModification)
            registerConstructor(FileSearchTask.FileSearch)
            registerConstructor(FiniteStateMachineTask.FiniteStateMachine)
            registerConstructor(FunctorialMappingTask.FunctorialMapping)
            registerConstructor(GameEconomyTask.GameEconomy)
            registerConstructor(GameLevelDesignTask.GameLevelDesign)
            registerConstructor(GameMechanicsDesignTask.GameMechanicsDesign)
            registerConstructor(GameNarrativeDesignTask.GameNarrativeDesign)
            registerConstructor(GameTheoryTask.GameTheory)
            registerConstructor(ImageGenerationTask.GenerateImage)
            registerConstructor(GeneratePresentationTask.GeneratePresentation)
            registerConstructor(GenerateQRImageTask.GenerateQRImage)
            registerConstructor(GenerateSpriteSheetTask.GenerateSpriteSheet)
            registerConstructor(GeneticOptimizationTask.GeneticOptimization)
            registerConstructor(GitHubSearchTask.GitHubSearch)
            registerConstructor(IllustrateDocumentTask.IllustrateDocument)
            registerConstructor(ImageDecompositionTask.ImageDecomposition)
            registerConstructor(TiledImageGenerationTask.TiledImageGeneration)
            registerConstructor(ImageTableTask.ImageTable)
            registerConstructor(ImageVariationTask.ImageVariation)
            registerConstructor(InteractiveStoryTask.InteractiveStory)
            registerConstructor(IsomorphismDiscoveryTask.IsomorphismDiscovery)
            registerConstructor(IterativeGraphGenerationTask.IterativeGraphGeneration)
            registerConstructor(JdbcSessionTask.JdbcSession)
            registerConstructor(JournalismReasoningTask.JournalismReasoning)
            registerConstructor(LanguageServerTask.LanguageServer)
            registerConstructor(LateralThinkingTask.LateralThinking)
            registerConstructor(LLMExperimentTask.LLMExperiment)
            registerConstructor(LLMPollSimulationTask.LLMPollSimulation)
            registerConstructor(MathematicalReasoningTask.MathematicalReasoning)
            registerConstructor(MCPToolTask.MCPTool)
            registerConstructor(MetaCognitiveReflectionTask.MetaCognitiveReflection)
            registerConstructor(MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis)
            registerConstructor(NarrativeGenerationTask.NarrativeGeneration)
            registerConstructor(NeuralNetworkLayerTask.NeuralNetworkLayer)
            registerConstructor(OCRTask.OCR)
            registerConstructor(PdfFormTask.PdfForm)
            registerConstructor(PersuasiveEssayTask.PersuasiveEssay)
            registerConstructor(PoliticalOptimizationTask.PoliticalOptimization)
            registerConstructor(ProbabilisticReasoningTask.ProbabilisticReasoning)
            registerConstructor(ReadDocumentsTask.ReadDocuments)
            registerConstructor(ReportGenerationTask.ReportGeneration)
            registerConstructor(ResearchPaperGenerationTask.ResearchPaperGeneration)
            registerConstructor(RunCodeTask.RunCode)
            registerConstructor(RunToolTask.RunTool)
            registerConstructor(ScriptwritingTask.Scriptwriting)
            registerConstructor(SegmentedImageGenerationTask.SegmentedImageGeneration)
            registerConstructor(SingleFixTask.SingleFix)
            registerConstructor(SocraticDialogueTask.SocraticDialogue)
            registerConstructor(SoftwareDesignDocumentTask.SoftwareDesignDocument)
            registerConstructor(StructuralInvariantAnalysisTask.StructuralInvariantAnalysis)
            registerConstructor(SubPlanTask.SubPlan)
            registerConstructor(SymbolsDbCodeTask.SymbolsDbCode)
            registerConstructor(SystemsThinkingTask.SystemsThinking)
            registerConstructor(TableCompilationTask.TableCompilation)
            registerConstructor(TechnicalExplanationTask.TechnicalExplanation)
            registerConstructor(TemporalReasoningTask.TemporalReasoning)
            registerConstructor(TutorialGenerationTask.TutorialGeneration)
            registerConstructor(WriteHtmlTask.WriteHtml)

            taskConstructors.toMap()
        }

        fun values(): List<TaskType<*, *>> {
            @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
            return values(TaskType::class.java)
        }

        fun OrchestrationConfig.getImpl(
            planTask: TaskExecutionConfig?
        ) = getImpl(
            taskType = planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"),
            cfg = planTask)


        fun <T : TaskExecutionConfig, U : TaskTypeConfig> OrchestrationConfig.getImpl(
            taskType: TaskType<T,U>, cfg: TaskExecutionConfig? = null
        ): AbstractTask<out T, U> {
            val constructor = taskConstructors[taskType]
            if (constructor == null) {
                throw RuntimeException("Unknown task type: ${taskType.name}")
            }
            val executionConfig: TaskExecutionConfig = cfg ?: try {
                taskType.executionConfigClass.getDeclaredConstructor().newInstance() as TaskExecutionConfig
            } catch (e: NoSuchMethodException) {
                throw RuntimeException("Task execution config class ${taskType.executionConfigClass.name} does not have a no-arg constructor. Please provide a planTask instance.", e)
            }
            return constructor(this, executionConfig) as AbstractTask<out T, U>
        }

        fun getAvailableTaskTypes(orchestrationConfig: OrchestrationConfig): List<TaskType<*, *>> {
            @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
            return orchestrationConfig.taskSettings.mapNotNull { x ->
                valueOf(
                    x.value.task_type ?: return@mapNotNull null
                )
            }
        }

        fun valueOf(name: String): TaskType<*, *> {
            @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
            return valueOf(TaskType::class.java, name)
        }

        private fun register(taskType: TaskType<*, *>) = register(TaskType::class.java, taskType)
    }

    fun getConstructor(): (OrchestrationConfig, @UnsafeVariance T?) -> AbstractTask<out T, out U> =
        taskClass.let { cls ->
            val method =
                cls.getDeclaredConstructor(OrchestrationConfig::class.java, executionConfigClass)
            method.isAccessible = true
            { settings: OrchestrationConfig, task: T? ->
                method.newInstance(settings, task) as AbstractTask<T, U>
            }
        }

}

class TaskTypeSerializer : DynamicEnumSerializer<TaskType<*, *>>(TaskType::class.java)

class TaskTypeDeserializer : DynamicEnumDeserializer<TaskType<*, *>>(TaskType::class.java)
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/TaskTypeConfig.kt

```
package com.simiacryptus.cognotik.plan.tools

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.simiacryptus.cognotik.platform.model.ApiChatModel

@JsonTypeIdResolver(TaskTypeConfig.PlanTaskTypeIdResolver::class)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    property = "task_type",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = true
)
open class TaskTypeConfig(
    var task_type: String? = null,
    name: String? = task_type,
    var model: ApiChatModel? = null
) {
    var name: String? = name
        get() = field ?: task_type

    class PlanTaskTypeIdResolver : TypeIdResolverBase() {
        override fun idFromValue(value: Any): String? {
            return when (value) {
                is TaskTypeConfig -> value.task_type ?: return null
                else -> throw IllegalArgumentException("Unexpected value type: ${value.javaClass}")
            }
        }

        override fun idFromValueAndType(value: Any, suggestedType: Class<*>): String? {
            return idFromValue(value)
        }

        override fun typeFromId(context: DatabindContext, id: String): JavaType {
            val taskType = TaskType.valueOf(id.replace(" ", ""))
            val subType = context.constructType(taskType.taskSettingsClass)
            return subType
        }

        override fun getMechanism(): JsonTypeInfo.Id {
            return JsonTypeInfo.Id.CUSTOM
        }
    }
}

fun TaskType<*, *>.newSettings(): TaskTypeConfig? =
    taskSettingsClass.declaredConstructors.firstOrNull { it.parameters.isEmpty() }?.let {
        it.isAccessible = true
        val defaultConfig = it.newInstance() as TaskTypeConfig
        defaultConfig.task_type = name
        defaultConfig.name = null
        defaultConfig.model = null
        defaultConfig
    }

```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/TaskUtils.kt

```
package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.OutputStream

/**
 * Truncates text for display with an ellipsis indicator.
 * Follows Cognotik IO Best Practices for concise UI output.
 */
fun String.truncateForDisplay(maxLength: Int = 10000): String {
    return if (this.length > maxLength) {
        "${this.take(maxLength)}\n\n> _... (truncated for display, ${this.length - maxLength} characters omitted)_"
    } else {
        this
    }
}

/**
 * Safely completes a task with error handling and Markdown rendering.
 * Ensures the UI spinner is removed even if rendering fails.
 * Follows Cognotik IO Best Practices for UI output.
 */
fun SessionTask.safeComplete(message: String, log: Logger) {
    try {
        // Use renderMarkdown extension as per Cognotik IO Best Practices
        this.complete(message.renderMarkdown())
    } catch (e: Exception) {
        log.error("Error completing task with message: $message", e)
        // Fallback to raw message if markdown rendering fails, ensuring task.complete() is called to clear UI state
        try {
            this.complete(message)
        } catch (e2: Exception) {
            log.error("Critical failure in SessionTask.complete", e2)
        }
    }
}

/**
 * Implements the "Triple Log Rule" from Cognotik IO Best Practices.
 * Logs to UI, SLF4J, and the Task Transcript.
 */
fun SessionTask.tripleLog(
    e: Throwable,
    log: Logger,
    transcript: OutputStream? = null,
    contextMessage: String = "An error occurred"
) {
    // 1. UI: Visual feedback for the user
    this.error(e)

    // 2. SLF4J: System operational layer (Single line preferred)
    log.error("$contextMessage: ${e.message}")

    // 3. Transcript: Audit trail with stack trace in <details> tag
    if (transcript != null) {
        try {
            val errorEntry = """
                ## Error: $contextMessage
                
                **Message:** `${e.message}`
                
                <details>
                <summary>Stack Trace</summary>
                
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent()
            transcript.write(errorEntry.toByteArray())
        } catch (transcriptEx: Exception) {
            log.warn("Failed to write to transcript: ${transcriptEx.message}")
        }
    }
}


fun ChatModel.toApiChatModel(): ApiChatModel {
    val apis = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis
    return ApiChatModel(
        model = this, provider = ApiData(
            key = apis.find { it.provider == this.provider }?.key
                ?: throw IllegalArgumentException("No API Key for ${this.provider?.name}"),
            baseUrl = apis.find { it.provider == this.provider }?.baseUrl ?: this.provider?.base ?: "",
            provider = this.provider,
        ).validate()
    )
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik.plan.tools` package, reflecting the architecture and functionality described in the provided source files.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/README.md
```markdown
# Task Orchestration Tools

The `com.simiacryptus.cognotik.plan.tools` package provides the core framework for defining, configuring, and executing modular tasks within the Cognotik orchestration system. It enables a polymorphic task execution environment where different AI-driven capabilities can be composed into complex workflows.

## Core Components

### [AbstractTask](./AbstractTask.kt)
The base class for all task implementations. It manages:
- **Execution State**: Tracks task progress (Pending, InProgress, Completed).
- **Resource Access**: Provides methods for reading input files with glob pattern support and document-to-text conversion.
- **UI Integration**: Handles rendering task headers, creating tabbed displays, and managing interactive UI elements like "Accept" buttons.
- **Transcripts**: Automatically generates Markdown and HTML transcripts of task execution for auditing and debugging.
- **Model Selection**: Provides access to "Smart" and "Fast" LLM interfaces based on task-specific or global configurations.

### [TaskType](./TaskType.kt)
A `DynamicEnum` registry that maps task identifiers to their implementation classes and configuration schemas. It categorizes tasks into several domains:
- **Reasoning**: Abductive, Adversarial, Causal, Socratic, etc.
- **Writing**: Article Generation, Email Campaigns, Scriptwriting, Technical Explanations.
- **File Operations**: Search, Modification, Append, Data Ingest.
- **Coding**: Run Code, AutoFix, Language Server integration.
- **Games**: Narrative Design, Mechanics, Economy balancing.
- **Online**: Web Crawling, GitHub Search, MCP Tool integration.

### [TaskExecutionConfig](./TaskExecutionConfig.kt)
Defines the instance-specific configuration for a task, including:
- `task_type`: The specific implementation to use.
- `task_description`: A user-facing description of the goal.
- `task_dependencies`: A list of upstream task IDs that must complete before this task starts, enabling data flow between tasks.

### [TaskTypeConfig](./TaskTypeConfig.kt)
Provides global settings for a specific task type, such as the default `ApiChatModel` to be used by all instances of that task type.

### [TaskUtils](./TaskUtils.kt)
Contains utility extensions for robust task execution:
- **Triple Log Rule**: A standardized logging pattern that records errors to the UI (for the user), SLF4J (for system operations), and the Task Transcript (for detailed auditing with stack traces).
- **Safe Completion**: Ensures the UI state is correctly updated and spinners are removed even if Markdown rendering fails.
- **Display Truncation**: Prevents UI clutter by truncating large text outputs with clear omission indicators.

## Implementation Patterns

To implement a new task, extend `AbstractTask` and register it in the `TaskType` companion object. Tasks should:
1. Define a specific `TaskExecutionConfig` if they require custom parameters.
2. Implement `promptSegment()` to define their contribution to the LLM context.
3. Implement `run()` to execute the core logic, utilizing the provided `SessionTask` for UI feedback and `TaskOrchestrator` for agent interaction.

## Usage Example

Tasks are typically instantiated via the `OrchestrationConfig`:

```kotlin
val taskConfig = TaskExecutionConfig(task_type = "Brainstorming", task_description = "Generate app ideas")
val taskImpl = orchestrationConfig.getImpl(taskConfig)
taskImpl.run(agent, messages, sessionTask, { result -> /* handle result */ }, orchestrationConfig)
```

## Logging and Debugging

The system emphasizes traceability through transcripts. Every task execution can generate a transcript file located in the `transcript/` directory, providing a step-by-step record of the AI's reasoning and the task's output.
```

### Summary of Changes
- **Created/Updated README.md**: Documented the core architecture of the task system.
- **Component Breakdown**: Explained the roles of `AbstractTask`, `TaskType`, and the configuration classes.
- **Feature Highlights**: Noted the "Triple Log Rule" and dependency management system found in the source.
- **Standardization**: Followed the project's documentation patterns, linking to the relevant source files.
- **Clarity**: Provided a high-level overview of how the polymorphic task system is structured and how to extend it.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/README.md</a> Updated
