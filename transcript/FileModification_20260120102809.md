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

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/ExecutionState.kt

```
package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.util.concurrent.Future

data class ExecutionState(
    val subTasks: Map<String, TaskExecutionConfig>,
    val tasksByDescription: MutableMap<String?, TaskExecutionConfig> = subTasks.entries.toTypedArray()
        .associate { (it.value.task_description ?: it.key) to it.value }.toMutableMap(),
    val taskIdProcessingQueue: MutableList<String> = PlanUtil.executionOrder(subTasks)
        .toMutableList(),
    val taskResult: MutableMap<String, String> = mutableMapOf(),
    val completedTasks: MutableList<String> = mutableListOf(),
    val taskFutures: MutableMap<String, Future<*>> = mutableMapOf(),
    val uitaskMap: MutableMap<String, SessionTask> = mutableMapOf()
)
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/OrchestrationConfig.kt

```
package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.PlanUtil.isWindows
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask.AutoFixTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import kotlin.io.path.Path


class OrchestrationConfig(
    var sessionId: String = "default",
    var defaultSmartModel: ApiChatModel? = null,
    var defaultFastModel: ApiChatModel? = null,
    var defaultImageModel: ApiChatModel? = null,
    val shellCmd: List<String> = listOf(if (isWindows) "powershell" else "bash"),
    var temperature: Double = 0.2,
    val budget: Double = 2.0,
    val taskSettings: MutableMap<String, TaskTypeConfig> = TaskType.values().filter {
        false // Do not auto-enable any tasks
    }.associateWith { taskType ->
        taskType.newSettings()?.let {
            it.name = taskType.description
            it
        } ?: throw IllegalStateException("No default config for task type ${taskType.name}")
    }.mapKeys { it.key.name }.toMutableMap(),
    var cognitiveSettings: CognitiveModeConfig? = null,
    var autoFix: Boolean = false,
    val workingDir: String? = ".",
) {
    val cognitiveMode: CognitiveModeType<*>? get() = cognitiveSettings?.type

    @get:JsonIgnore
    var processor: PatchProcessor = PatchProcessors.Fuzzy

    @get:JsonIgnore
    val defaultSmart get() = instance(defaultSmartModel ?: throw IllegalStateException("Default model not set"))

    @get:JsonIgnore
    val defaultFast
        get() = instance(defaultFastModel ?: defaultSmartModel ?: throw IllegalStateException("Parsing model not set"))

    @get:JsonIgnore
    val defaultImage
        get() = instance(defaultImageModel ?: throw IllegalStateException("Image chat model not set"))


    @JsonIgnore
    fun instance(model: ApiChatModel) =
        instanceFn?.let { it(model) } ?: throw IllegalStateException("Instance function not set")

    @get:JsonIgnore
    val absoluteWorkingDir
        get() = when {
            this.workingDir == null -> null//throw IllegalStateException("Working directory not set")
            this.workingDir.startsWith("~") -> File(
                this.workingDir.replaceFirst(
                    "~",
                    System.getProperty("user.home")
                )
            ).absolutePath

            else -> File(this.workingDir).absolutePath
        }

    fun planningActor(
        describer: TypeDescriber,
        task: SessionTask
    ): ParsedAgent<TaskBreakdownResult> {
        val availableTaskTypes = TaskType.getAvailableTaskTypes(this)
        return planningActor(
            taskDescriptions = availableTaskTypes.joinToString("\n") { taskType ->
                val impl = this.getImpl(taskType)
                "* ${impl.promptSegment()}"
            } + (this.workingDir?.let { root ->
                "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
            } ?: ""),
            model = defaultSmart.getChildClient(task),
            parsingModel = defaultFast.getChildClient(task),
            temperature = temperature,
            describer = describer,
            availableTaskTypes = availableTaskTypes
        )
    }

    @JsonIgnore
    fun copy(
        model: ApiChatModel? = this.defaultSmartModel,
        parsingModel: ApiChatModel? = this.defaultFastModel,
        imageChatModel: ApiChatModel? = this.defaultImageModel,
        shellCmd: List<String> = this.shellCmd,
        temperature: Double = this.temperature,
        budget: Double = this.budget,
        taskSettings: MutableMap<String, TaskTypeConfig> = this.taskSettings,
        cognitiveSettings: CognitiveModeConfig? = this.cognitiveSettings,
        autoFix: Boolean = this.autoFix,
        workingDir: String? = this.workingDir,
        sessionId: String = this.sessionId,
    ): OrchestrationConfig = OrchestrationConfig(
        defaultSmartModel = model,
        defaultFastModel = parsingModel,
        defaultImageModel = imageChatModel,
        shellCmd = shellCmd,
        temperature = temperature,
        budget = budget,
        taskSettings = taskSettings,
        cognitiveSettings = cognitiveSettings,
        autoFix = autoFix,
        workingDir = workingDir,
        sessionId = sessionId
    )


    data class TaskBreakdownResult(
        @Description("A map where each task ID is associated with its corresponding PlanTask object. Crucial for defining task relationships and information flow.")
        val tasksByID: Map<String, TaskExecutionConfig>? = null,
    )

    companion object {
        var exampleInstance = TaskBreakdownResult(
            tasksByID = mapOf(
                "1" to AutoFixTaskExecutionConfigData(
                    task_description = "Task 1", task_dependencies = listOf(), commands = mutableListOf(
                        AutoFixTask.CommandWithWorkingDir(
                            command = mutableListOf("echo", "Hello, World!"), workingDir = "."
                        )
                    )
                ), "2" to FileModificationTaskExecutionConfigData(
                    task_description = "Task 2",
                    task_dependencies = listOf("1"),
                    related_files = listOf("input2.txt"),
                    files = listOf("output2.txt"),
                )
            ),
        )

        fun planningActor(
            taskDescriptions: String,
            model: ChatInterface,
            parsingModel: ChatInterface,
            temperature: Double,
            describer: TypeDescriber,
            availableTaskTypes: List<TaskType<*, *>>
        ): ParsedAgent<TaskBreakdownResult> = ParsedAgent(
            name = "TaskBreakdown",
            resultClass = TaskBreakdownResult::class.java,
            exampleInstance = exampleInstance,
            prompt = """
              Given a user request, identify and list smaller, actionable tasks that can be directly implemented in code.
              (Do not repeat or ask for the JSON content since the platform already handles reading the software graph.)
              For each task:
              * Provide input/output file names if applicable
              * Describe any execution dependencies and the order in which tasks should be run
              * Write a brief description of the task and its role
              * Mention any important interface or integration details
              The available task types are:
              """.trimIndent() + "\n  " + taskDescriptions + """
              (Remember: the JSON file content is already loaded by the platform.)
              """.trimIndent(),
            model = model,
            parsingChatter = parsingModel,
            temperature = temperature,
            describer = describer,
            parserPrompt = ("\nTask Subtype Schema:\n\n" + availableTaskTypes.joinToString("\n\n") { taskType ->
                "\n${taskType.name}:\n  ${
                    describer.describe(taskType.executionConfigClass).lineSequence()
                        .map {
                            when {
                                it.isBlank() -> {
                                    when {
                                        it.length < "  ".length -> "  "
                                        else -> it
                                    }
                                }

                                else -> "  " + it
                            }
                        }
                        .joinToString("\n")
                }\n".trim()
            } + "\n")
        )

        @JsonIgnore
        var instanceFn: ((ApiChatModel) -> ChatInterface)? = null
    }

    /**
     * Get all available task configurations for a given task type
     */
    fun getTaskConfigs(taskType: TaskType<*, *>): List<TaskTypeConfig> {
        return taskSettings.filter { it.value.task_type == taskType.name }.values.toList()
    }

    /**
     * Get a specific task configuration by task type and name
     */
    fun getTaskConfig(taskType: TaskType<*, *>, configName: String?): TaskTypeConfig? {
        val configs = getTaskConfigs(taskType)
        return if (configName != null) {
            configs.firstOrNull { it.name == configName }
        } else {
            configs.firstOrNull()
        }
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/PlanUtil.kt

```
package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.plan.tools.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlanUtil {

  fun diagram(
    taskMap: Map<String, TaskExecutionConfig>
  ) = "## Sub-Plan Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${
    buildMermaidGraph(
      taskMap
    )
  }\n${TRIPLE_TILDE}".renderMarkdown(true)


    fun executionOrder(tasks: Map<String, TaskExecutionConfig>): List<String> {
        val taskIds: MutableList<String> = mutableListOf()
        val taskMap = tasks.toMutableMap()
        while (taskMap.isNotEmpty()) {
            val nextTasks =
                taskMap.filter { (_, task) ->
                    task.task_dependencies?.filter { entry ->
                        entry in tasks.keys
                    }?.all { taskIds.contains(it) } ?: true
                }
            if (nextTasks.isEmpty()) {
                throw RuntimeException("Circular dependency detected in task breakdown")
            }
            taskIds.addAll(nextTasks.keys)
            nextTasks.keys.forEach { taskMap.remove(it) }
        }
        return taskIds
    }

    val isWindows = System.getProperty("os.name").lowercase(Locale.getDefault()).contains("windows")
    private fun sanitizeForMermaid(input: String) = input
        .replace(" ", "_")
        .replace("\"", "\\\"")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .let { "`$it`" }

    private val mermaidGraphCache = ConcurrentHashMap<String, String>()
    private val mermaidExceptionCache = ConcurrentHashMap<String, Exception>()

    fun buildMermaidGraph(subTasks: Map<String, TaskExecutionConfig>, includeStyles: Boolean = true): String {

        val cacheKey = JsonUtil.toJson(subTasks)

        mermaidGraphCache[cacheKey]?.let { return it }
        mermaidExceptionCache[cacheKey]?.let { throw it }
        try {
            val graphBuilder = StringBuilder("graph TD;\n")
            subTasks.forEach { (taskId, task) ->
                val sanitizedTaskId = sanitizeForMermaid(taskId)
                val taskType = task.task_type ?: "Unknown"
                if(includeStyles) {
                    graphBuilder.append(
                        "    " + sanitizedTaskId + "[" + sanitizeForMermaid(
                            task.task_description ?: ""
                        ) + "]" + when (task.state) {
                            TaskState.Completed -> ":::completed"
                            TaskState.InProgress -> ":::inProgress"
                            else -> ":::$taskType"
                        } + ";\n")
                }
                task.task_dependencies?.forEach { dependency ->
                    val sanitizedDependency = sanitizeForMermaid(dependency)
                    graphBuilder.append("    $sanitizedDependency --> ${sanitizedTaskId};\n")
                }
            }
            if(includeStyles) {
                graphBuilder.append("    classDef default fill:#f9f9f9,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef NewFile fill:lightblue,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef EditFile fill:lightgreen,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef Documentation fill:lightyellow,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef Inquiry fill:orange,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef TaskPlanning fill:lightgrey,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef completed fill:#90EE90,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef inProgress fill:#FFA500,stroke:#333,stroke-width:2px;\n")
            }
            val graph = graphBuilder.toString()
            mermaidGraphCache[cacheKey] = graph
            return graph
        } catch (e: Exception) {
            mermaidExceptionCache[cacheKey] = e
            throw e
        }
    }

    fun filterPlan(
        retries: Int = 3,
        fn: () -> Map<String, TaskExecutionConfig>?
    ): Map<String, TaskExecutionConfig>? {
        val tasksByID = fn() ?: emptyMap()
        tasksByID.forEach {
            it.value.task_dependencies = it.value.task_dependencies?.filter { it in tasksByID.keys }?.toMutableList()
            it.value.state = TaskState.Pending
        }
        try {
            executionOrder(tasksByID)
        } catch (e: RuntimeException) {
            if (retries <= 0) {
                log.warn("Error filtering plan: " + JsonUtil.toJson(fn() ?: emptyMap<String, TaskExecutionConfig>()), e)
                throw e
            } else {
                log.info("Circular dependency detected in task breakdown")
                return filterPlan(retries - 1, fn)
            }
        }
        return if (tasksByID.size == (fn() ?: emptyMap()).size) {
            fn() ?: emptyMap()
        } else filterPlan {
            tasksByID
        }
    }

    fun getAllDependencies(
      subPlanTask: TaskExecutionConfig,
      subTasks: Map<String, TaskExecutionConfig>,
      visited: MutableSet<String>
    ): List<String> {
        val dependencies = subPlanTask.task_dependencies?.toMutableList() ?: mutableListOf()
        subPlanTask.task_dependencies?.forEach { dep ->
            if (dep in visited) return@forEach
            val subTask = subTasks[dep]
            if (subTask != null) {
                visited.add(dep)
                dependencies.addAll(
                    getAllDependencies(
                        subTask,
                        subTasks,
                        visited
                    )
                )
            }
        }
        return dependencies
    }

    val log = LoggerFactory.getLogger(PlanUtil::class.java)

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/TaskContextYamlDescriber.kt

```
package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.plan.tools.TaskType

class TaskContextYamlDescriber(
    val orchestrationConfig: OrchestrationConfig
) : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "aicoder.actions"
) {
    override val includeMethods: Boolean get() = false

    override fun getEnumValues(clazz: Class<*>): List<String> {
        return if (clazz == TaskType::class.java) {
            orchestrationConfig.taskSettings.keys.toList()
        } else {
            super.getEnumValues(clazz)
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/TaskOrchestrator.kt

```
package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph
import com.simiacryptus.cognotik.plan.PlanUtil.filterPlan
import com.simiacryptus.cognotik.plan.PlanUtil.getAllDependencies
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class TaskOrchestrator(
    val user: User = defaultUser,
    val session: Session,
    val dataStorage: StorageInterface,
    val root: Path,
    val transcriptStream: OutputStream? = null,
    val timeoutMinutes: Long = 15
) {
    val pool: ExecutorService by lazy { ApplicationServices.threadPoolManager.getPool(session, user) }

    val files: Array<File> by lazy {
        FileSelectionUtils.expandFileList(root.toFile())
    }

    val codeFiles: Map<Path, String>
        get() = files
            .filter { it.exists() && it.isFile }
            .filter { !it.name.startsWith(".") }
            .associate { file ->
                root.relativize(file.toPath()) to try {
                    file.inputStream().bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    log.warn("Error reading file", e)
                    ""
                }
            }

    var executionState: ExecutionState? = null

    fun executePlan(
        plan: Map<String, TaskExecutionConfig>,
        task: SessionTask,
        userMessage: String,
        orchestrationConfig: OrchestrationConfig,
    ): ExecutionState {
        val tabs = TabbedDisplay(task)
        val planProcessingState = newState(plan)
        this.executionState = planProcessingState
        try {
            val diagramTask = tabs.newTask("Plan")
            executePlan(
                diagramBuffer = diagramTask.add(
                    "## Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${buildMermaidGraph(planProcessingState.subTasks)}\n$TRIPLE_TILDE".renderMarkdown(
                        true
                    ),
                    additionalClasses = "flow-chart"
                ),
                subTasks = planProcessingState.subTasks,
                task = diagramTask,
                executionState = planProcessingState,
                taskIdProcessingQueue = planProcessingState.taskIdProcessingQueue,
                pool = pool,
                userMessage = userMessage,
                plan = plan,
                tabs = tabs,
                orchestrationConfig = orchestrationConfig
            )
        } catch (e: Throwable) {
            log.warn("Error during incremental code generation process", e)
            task.error(e)
        }
        return planProcessingState
    }

    private fun newState(plan: Map<String, TaskExecutionConfig>) =
        ExecutionState(
            subTasks = (filterPlan { plan }?.entries?.toTypedArray<Map.Entry<String, TaskExecutionConfig>>()
                ?.associate { it.key to it.value } ?: mapOf()).toMutableMap()
        )

    fun executePlan(
        diagramBuffer: StringBuilder?,
        subTasks: Map<String, TaskExecutionConfig>,
        task: SessionTask,
        executionState: ExecutionState,
        taskIdProcessingQueue: MutableList<String>,
        pool: ExecutorService,
        userMessage: String,
        plan: Map<String, TaskExecutionConfig>,
        tabs: TabbedDisplay,
        orchestrationConfig: OrchestrationConfig,
    ) {
        val taskTabs = object : TabbedDisplay(
            tabs.newTask("Session"),
            additionalClasses = "task-tabs"
        ) {
            override fun renderTabButtons(): String {
                diagramBuffer?.set(
                    "\n## Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${buildMermaidGraph(subTasks)}\n$TRIPLE_TILDE".renderMarkdown(
                        true
                    )
                )
                task.complete()
                return buildString {
                    append("<div class='tabs'>\n")
                    super.tabs.withIndex().forEach { (idx, t) ->
                        val (taskId, _) = t
                        val subTask = executionState.tasksByDescription[taskId]
                        if (null == subTask) {
                            log.warn("Task tab not found: $taskId")
                        }
                        val isChecked = if (taskId in taskIdProcessingQueue) "checked" else ""
                        val style = when (subTask?.state) {
                            AbstractTask.TaskState.Completed -> " style='text-decoration: line-through;'"
                            null -> " style='opacity: 20%;'"
                            AbstractTask.TaskState.Pending -> " style='opacity: 30%;'"
                            else -> ""
                        }
                        append("<label class='tab-button' data-for-tab='${idx}'$style><input type='checkbox' $isChecked disabled />$taskId</label>\n")
                    }
                    append("</div>")
                }
            }
        }
        taskIdProcessingQueue.forEach { taskId ->
            val subtask: TaskExecutionConfig? = executionState.subTasks[taskId]
            val description = subtask?.task_description
            val newTask = taskTabs.newTask(description ?: taskId)
            executionState.uitaskMap[taskId] = newTask
            log.debug("Creating task tab: $taskId ${System.identityHashCode(subtask)} $description")
        }
        Thread.sleep(100)
        while (taskIdProcessingQueue.isNotEmpty()) {
            val taskId = taskIdProcessingQueue.removeAt(0)
            val subTask = executionState.subTasks[taskId] ?: throw RuntimeException("Task not found: $taskId")
            subTask.state = AbstractTask.TaskState.Pending
            log.debug("Awaiting dependencies: ${subTask.task_dependencies?.joinToString(", ") ?: ""}")
            subTask.task_dependencies
                ?.associate { it to executionState.taskFutures[it] }
                ?.forEach { (id, future) ->
                    try {
                        future?.get() ?: log.warn("Dependency not found: $id")
                    } catch (e: Throwable) {
                        log.warn("Error", e)
                    }
                }
            executionState.taskFutures[taskId] = pool.submit {
                subTask.state = AbstractTask.TaskState.InProgress
                taskTabs.update()
                log.debug("Running task: ${System.identityHashCode(subTask)} ${subTask.task_description}")
                transcriptStream?.let { stream ->
                    synchronized(stream) {
                        stream.write("\n## Task `$taskId` Started\n\n${subTask.task_description}\n\n".toByteArray())
                        stream.flush()
                    }
                }
                val task = executionState.uitaskMap[taskId] ?: taskTabs.newTask(taskId)
                task.add(
                    ("\n## Task `" + taskId + "`" + (subTask.task_description ?: "") + "\n" +
                        TRIPLE_TILDE + "json" + JsonUtil.toJson(data = subTask) + "\n" + TRIPLE_TILDE + "\n").renderMarkdown(
                        true
                    )
                )
                try {
                    val dependencies = subTask.task_dependencies?.toMutableSet() ?: mutableSetOf()
                    dependencies += getAllDependencies(
                        subPlanTask = subTask,
                        subTasks = executionState.subTasks,
                        visited = mutableSetOf()
                    )
                    task.add(("\n### Dependencies:" + dependencies.joinToString("\n") { "* $it" }).renderMarkdown(true))
                    val impl = orchestrationConfig.getImpl(subTask)
                    val messages = listOf(
                        userMessage,
                        JsonUtil.toJson(plan),
                        impl.getPriorCode(executionState)
                    )
                    val onComplete = Semaphore(0)
                    impl.run(
                        agent = this,
                        messages = messages,
                        task = task,
                        resultFn = {
                            try {
                                executionState.taskResult[taskId] = it
                                transcriptStream?.let { stream ->
                                    synchronized(stream) {
                                        stream.write("\n### Task `$taskId` Result\n\n$it\n\n".toByteArray())
                                        stream.flush()
                                    }
                                }
                            } catch (e: Throwable) {
                                log.warn("Error during result handling", e)
                            } finally {
                                onComplete.release()
                            }
                        },
                        orchestrationConfig = orchestrationConfig
                    )
                    if(!onComplete.tryAcquire(timeoutMinutes, TimeUnit.MINUTES)) {
                        throw RuntimeException("Task $taskId timed out")
                    }
                } catch (e: Throwable) {
                    log.warn("Error during task execution", e)
                    task.error(e)
                    transcriptStream?.let { stream ->
                        synchronized(stream) {
                            stream.write("\n### Task `$taskId` Error\n\n```\n${e.message}\n${e.stackTraceToString()}\n```\n\n".toByteArray())
                            stream.flush()
                        }
                    }
                } finally {
                    executionState.completedTasks.add(element = taskId)
                    subTask.state = AbstractTask.TaskState.Completed
                    log.debug("Completed task: $taskId ${System.identityHashCode(subTask)}")
                    taskTabs.update()
                    transcriptStream?.let { stream ->
                        synchronized(stream) {
                            stream.write("\n### Task `$taskId` Completed\n\n".toByteArray())
                            stream.flush()
                        }
                    }
                }
            }
        }
        await(executionState.taskFutures)
    }

    fun await(futures: MutableMap<String, Future<*>>) {
        val start = System.currentTimeMillis()
        fun cont(): Boolean {
            val elapsed = System.currentTimeMillis() - start
            val done = futures.values.count { it.isDone }
            return done < futures.size && elapsed < TimeUnit.MINUTES.toMillis(20)
        }
        while (cont()) Thread.sleep(1000)
    }

    fun copy(
        user: User = this.user,
        session: Session = this.session,
        dataStorage: StorageInterface = this.dataStorage,
        root: Path = this.root,
        transcriptStream: OutputStream? = this.transcriptStream
    ) = TaskOrchestrator(
        user = user,
        session = session,
        dataStorage = dataStorage,
        root = root,
        transcriptStream = transcriptStream
    )

    companion object {
        private val log = LoggerFactory.getLogger(TaskOrchestrator::class.java)
    }
}

const val TRIPLE_TILDE = "```"
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik.plan` package, based on the provided source code and specifications.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/README.md
```markdown
# Plan Orchestration and Execution

This package provides the core logic for breaking down complex user requests into actionable tasks and orchestrating their execution. It manages task dependencies, execution state, and provides visual feedback through Mermaid diagrams and tabbed UI components.

## Key Components

### [TaskOrchestrator](TaskOrchestrator.kt)
The central engine responsible for executing a plan. It:
- Manages a thread pool for parallel task execution.
- Handles task dependencies, ensuring prerequisites are completed before a task starts.
- Updates the user interface with real-time progress using a tabbed display.
- Captures task results and handles errors/timeouts.
- Streams execution transcripts for logging and auditing.

### [OrchestrationConfig](OrchestrationConfig.kt)
Defines the environment and parameters for planning and execution:
- **Model Configuration**: Specifies which AI models to use for planning, parsing, and image processing.
- **Task Settings**: Manages configurations for different task types (e.g., file modification, shell commands).
- **Planning Actor**: Contains the logic and prompts used to transform a user request into a structured `TaskBreakdownResult`.
- **Environment**: Sets the working directory, shell commands (bash/powershell), and execution budget.

### [ExecutionState](ExecutionState.kt)
A data class that tracks the runtime status of a plan:
- **Sub-tasks**: The map of task configurations.
- **Queue**: The ordered list of tasks to be processed.
- **Results**: A map of outputs from completed tasks.
- **Futures**: Tracks active background processes.
- **UI Mapping**: Links internal tasks to their corresponding UI components.

### [PlanUtil](PlanUtil.kt)
A utility object providing helper functions for plan management:
- **Dependency Resolution**: Performs topological sorts to determine valid execution orders and detects circular dependencies.
- **Visualization**: Generates Mermaid.js graph definitions to visualize task relationships and execution status (Pending, In Progress, Completed).
- **Filtering**: Sanitizes and validates plans to ensure consistency.

### [TaskContextYamlDescriber](TaskContextYamlDescriber.kt)
A specialized YAML describer used to generate schemas for the AI planning actor. it ensures the LLM understands the available task types and their configuration requirements based on the current `OrchestrationConfig`.

## Workflow

1.  **Planning**: The `OrchestrationConfig` uses a `planningActor` (LLM) to analyze a user request and generate a `TaskBreakdownResult`.
2.  **Initialization**: `TaskOrchestrator` creates an `ExecutionState` and determines the execution order based on task dependencies.
3.  **Visualization**: A Mermaid diagram is rendered in the UI to show the task graph.
4.  **Execution**:
    - Tasks are submitted to a thread pool.
    - The orchestrator waits for a task's dependencies to complete before starting it.
    - Each task is assigned a dedicated UI tab for output and status updates.
5.  **Completion**: Results are collected, and the final state is returned.

## Features

- **Parallel Execution**: Tasks with no mutual dependencies run concurrently.
- **Resilient Orchestration**: Includes timeout handling and error reporting per task.
- **Dynamic UI**: Automatically generates a tabbed interface where each task's progress can be monitored independently.
- **Visual Feedback**: Real-time updates to the Mermaid dependency graph reflect the current state of the execution pipeline.
```

### Summary of Changes
- Created a new `README.md` in the `com.simiacryptus.cognotik.plan` package directory.
- Documented the primary responsibilities of `TaskOrchestrator`, `OrchestrationConfig`, `ExecutionState`, and `PlanUtil`.
- Described the high-level workflow from user request to task completion.
- Highlighted key features such as parallel execution and Mermaid-based visualization.
- Followed the transform pattern specified in `index_docs.md` to provide documentation for the Kotlin source files in this directory.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/README.md</a> Updated
