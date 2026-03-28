package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.CoreTasks
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.OutputStream
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

class CouncilModeConfig(
  var council: List<CognitiveSchemaStrategy> = listOf(
    CognitiveSchemaStrategy.ProjectManager,
    CognitiveSchemaStrategy.AgileDeveloper,
    CognitiveSchemaStrategy.CreativeWriter,
  ),
  var maxTaskHistoryChars: Int = 20000,
  var maxTasksPerIteration: Int = 3,
  var maxIterations: Int = 10
) : CognitiveModeConfig(type = CoreTasks.Council)


open class CouncilMode(
  orchestrationConfig: OrchestrationConfig,
  session: Session,
  user: User
) : CognitiveMode<CouncilModeConfig>(
  orchestrationConfig,
  session,
  user
) {

  private val log = LoggerFactory.getLogger(CouncilMode::class.java)
  private val currentUserMessage = AtomicReference<String?>(null)
  private val executionRecords = mutableListOf<AdaptivePlanningMode.ExecutionRecord>()
  private val reasoningStates = mutableMapOf<String, Any>()
  private var isRunning = false
  private var transcriptStream: OutputStream? = null
  private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{\n<>()\[\]]+))}""")
  private val maxTaskHistoryChars: Int get() = config?.maxTaskHistoryChars ?: 20000
  private val maxTasksPerIteration: Int get() = config?.maxTasksPerIteration ?: 3
  private val maxIterations: Int get() = config?.maxIterations ?: 10
  val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)


  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    if (!isRunning) {
      isRunning = true
      startCouncilChat(task, userMessage)
    } else {
      task.echo(renderMarkdown("User: $userMessage", ui = task.ui))
      currentUserMessage.set(userMessage)
    }
  }

  override fun contextData(): List<String> = emptyList()

  private fun startCouncilChat(task: SessionTask, userMessage: String) {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    task.echo(renderMarkdown(userMessage, ui = task.ui))
    transcriptStream = task.transcript()

    val continueLoop = true
    val tabbedDisplay = TabbedDisplay(task)
    task.ui.pool.execute {
      try {
        task.complete()
        val coordinator = task.ui.dataStorage?.let {
          TaskOrchestrator(
            user = user,
            session = session,
            dataStorage = it,
            root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
              ?: task.ui.dataStorage!!.getSessionDir(user, session).toPath() ?: File(".").toPath()
          )
        }

        // Initialize all council members
        config.council.forEach { strategy ->
          val state = strategy.initialize(
            userMessage,
            contextData(),
            orchestrationConfig,
            task,
            describer
          )
          reasoningStates[strategy.name] = state
        }
        writeToTranscript("# Council Chat Session\n\n## Initial Prompt\n\n$userMessage\n\n")

        var iteration = 0
        while (iteration++ < maxIterations && continueLoop) {
          writeToTranscript("## Iteration $iteration\n\n")
          val iterationTask = tabbedDisplay.newTask("Iteration $iteration")
          val ui = iterationTask.ui
          val iterationTabbedDisplay = TabbedDisplay(iterationTask, additionalClasses = "iteration")

          // Display Inputs
          iterationTabbedDisplay.newTask("Inputs").apply {
            val inputTabs = TabbedDisplay(this)
            inputTabs.newTask("Project Info").apply {
              contextData().forEach { complete(renderMarkdown(it, tabs = false, ui = ui)) }
              complete()
            }
            formatEvalRecords().forEachIndexed { index, it ->
              inputTabs.newTask("Task ${index + 1}").apply {
                complete(renderMarkdown(it, ui = ui))
              }
            }
            // Display Council States
            config.council.forEach { strategy ->
              inputTabs.newTask("${strategy.name} State").apply {
                val state = reasoningStates[strategy.name]!!
                complete(renderMarkdown(formatState(strategy, state), ui = ui))
              }
            }
          }

          // Nominations
          val nominations = mutableListOf<Pair<String, AdaptivePlanningMode.TaskData>>()
          val nominationFutures = config.council.map { strategy ->
            ui.pool.submit<List<Pair<String, AdaptivePlanningMode.TaskData>>> {
              try {
                val state = reasoningStates[strategy.name]!!
                val tasks = getNominations(userMessage, strategy, state, iterationTask)
                val pairs =
                  tasks?.map { strategy.name to it } ?: emptyList()
                pairs
              } catch (e: Exception) {
                log.error("Error getting nominations from ${strategy.name}", e)
                emptyList()
              }
            }
          }
          nominations.addAll(nominationFutures.flatMap { it.get() })

          if (nominations.isEmpty()) {
            iterationTask.add(renderMarkdown("No tasks nominated. Finishing Council Chat.", ui = ui))
            iterationTask.complete()
            break
          }

          // Voting
          val selectedTasks = if (nominations.size > 1) {
            voteOnTasks(nominations, userMessage, iterationTask)
          } else {
            nominations.map { it.second }
          }

          if (selectedTasks.isEmpty()) {
            iterationTask.add(renderMarkdown("No tasks selected by vote. Finishing Council Chat.", ui = ui))
            iterationTask.complete()
            break
          }
          if (!orchestrationConfig.autoFix) {
            val semaphore = java.util.concurrent.Semaphore(0)
            var approved = false
            iterationTask.header("Proposed Plan", 3)
            val planHtml = StringBuilder()
            selectedTasks.forEachIndexed { index, taskData ->
              planHtml.append("${index + 1}. **${taskData.task.tasks?.firstOrNull()?.task_type ?: "Task"}**: ${taskData.task.tasks?.firstOrNull()?.task_description}\n")
            }
            iterationTask.add(renderMarkdown(planHtml.toString(), ui = ui))
            val buttons = StringBuilder()
            buttons.append(ui.hrefLink("Execute Plan", "btn btn-success mr-2") {
              approved = true
              semaphore.release()
            })
            buttons.append(" ")
            buttons.append(ui.hrefLink("Stop Council", "btn btn-danger") {
              approved = false
              semaphore.release()
            })
            iterationTask.add(buttons.toString())
            semaphore.acquire()
            if (!approved) {
              iterationTask.add(renderMarkdown("Council stopped by user.", ui = ui))
              iterationTask.complete()
              break
            }
          }


          // Execution
          val taskResults = mutableListOf<Pair<TaskExecutionConfig, Future<String>>>()
          for ((index, currentTask) in selectedTasks.withIndex()) {
            val currentTaskId = "task_${index + 1}"
            writeToTranscript("### Task $currentTaskId\n\n")
            val taskExecutionTask = task.newTask()
            val taskConfig = currentTask.task.tasks?.firstOrNull()
            val taskDescription = taskConfig?.task_description ?: "No description provided."
            taskExecutionTask.add(renderMarkdown("\n```json\n${taskConfig?.toJson()}\n```\n", ui = ui))
            writeToTranscript("**Description:** $taskDescription\n\n```json\n${JsonUtil.toJson(taskConfig)}\n```\n\n")
            taskExecutionTask.verbose(
              renderMarkdown(
                """
Executing task: `$currentTaskId` - $taskDescription
Full TaskData JSON:
```json 
${JsonUtil.toJson(taskConfig)}
```
""".trimIndent(), ui = ui
              )
            )
            iterationTabbedDisplay["Task Execution $currentTaskId"] = taskExecutionTask.placeholder

            val future = ui.pool.submit<String> {
              try {
                if (coordinator != null) {
                  runTask(
                    coordinator = coordinator,
                    currentTask = taskConfig!!,
                    userMessage = userMessage,
                    task = taskExecutionTask
                  )
                } else {
                  log.error("Coordinator is null, cannot run task")
                  ""
                }
              } catch (e: Exception) {
                taskExecutionTask.error(e)
                log.error("Error executing task", e)
                "Error executing task: ${e.message}"
              }
            }
            taskResults.add(Pair(taskConfig!!, future))
          }

          val completedTasks = taskResults.map { (task, future) ->
            val result = future.get()
            writeToTranscript("**Result:**\n\n$result\n\n")
            AdaptivePlanningMode.ExecutionRecord(
              time = Date(),
              iteration = iteration,
              task = task,
              result = result
            )
          }
          executionRecords.addAll(completedTasks)

          // Update States
          config.council.forEach { strategy ->
            val oldState = reasoningStates[strategy.name]!!
            val newState = updateState(
              strategy,
              oldState,
              completedTasks,
              currentUserMessage.get(),
              contextData(),
              orchestrationConfig,
              iterationTask,
              describer
            )
            reasoningStates[strategy.name] = newState
          }
          currentUserMessage.set(null)
          iterationTask.complete()
        }
        task.complete("Council Chat completed.")
      } catch (e: Throwable) {
        task.error(e)
        log.error("Error in startCouncilChat", e)
      } finally {
        isRunning = false
        transcriptStream?.flush()
        transcriptStream?.close()
        transcriptStream = null
        task.complete()
      }
    }
  }

  private fun updateState(
    strategy: CognitiveSchemaStrategy,
    state: Any,
    completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
    userMessage: String?,
    contextData: List<String>,
    orchestrationConfig: OrchestrationConfig,
    task: SessionTask,
    describer: TaskContextYamlDescriber
  ): Any {
    @Suppress("UNCHECKED_CAST")
    return strategy.update(state, completedTasks, userMessage, contextData, orchestrationConfig, task, describer)
  }

  private fun formatState(strategy: CognitiveSchemaStrategy, state: Any): String {
    @Suppress("UNCHECKED_CAST")
    return strategy.formatState(state)
  }

  private fun getNominations(
    userMessage: String,
    strategy: CognitiveSchemaStrategy,
    state: Any,
    task: SessionTask
  ): List<AdaptivePlanningMode.TaskData>? {
    @Suppress("UNCHECKED_CAST")
    val typedState = state
    Tasks.initDescriber(orchestrationConfig, describer)
    val parsedActor = ParsedAgent(
      name = "TaskChooser",
      resultClass = Tasks::class.java,
      exampleInstance = Tasks(mutableListOf()),
      prompt = buildString {
        append("As ${strategy.name} (${strategy.description}), given the following input, choose up to ")
        append(maxTasksPerIteration)
        append(" tasks to execute.\n")
        append("Available task types:\n")
        append(
          TaskType.getAvailableTaskTypes(orchestrationConfig)
            .flatMap { taskType ->
              val configs = orchestrationConfig.getTaskConfigs(taskType)
              configs.map { config ->
                val configName = config.name?.let { " - Configuration: '$it'" } ?: ""
                "* ${taskType.name}$configName:\n  ${
                  orchestrationConfig.getImpl(taskType).promptSegment().trim()
                    .trimIndent()
                    .indent("  ")
                }"
              }
            }
            .joinToString("\n\n"))
      },
      model = orchestrationConfig.defaultSmart.getChildClient(task),
      parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
      temperature = orchestrationConfig.temperature,
      describer = describer
    )
    val answer = parsedActor.answer(
      listOf(userMessage) + contextData() + listOf(
        """
        Current thinking status: ${strategy.formatState(typedState)}
        ${strategy.getTaskSelectionGuidance(typedState)}
        """.trimIndent()
      ) + formatEvalRecords(),
    )

    val executor = task.ui.pool ?: return null
    val processor = FixedConcurrencyProcessor(executor, 4)
    val expandedTasks = processTaskExpansionRecursive(answer.text, task, parsedActor, processor)

    val tasks = expandedTasks.map { taskData ->
      taskData.task.tasks?.map { taskConfigBase ->
        AdaptivePlanningMode.TaskData(
          Tasks(mutableListOf(taskConfigBase)),
          taskData.actorResponse
        )
      } ?: emptyList()
    }.flatten()

    return tasks.take(maxTasksPerIteration)
  }

  private fun voteOnTasks(
    nominations: List<Pair<String, AdaptivePlanningMode.TaskData>>,
    userMessage: String,
    task: SessionTask
  ): List<AdaptivePlanningMode.TaskData> {
    val votes = mutableMapOf<Int, Int>()
    val nominationDescriptions = nominations.mapIndexed { index, (nominator, taskData) ->
      val taskDesc = taskData.task.tasks?.joinToString("\n") { it.task_description ?: "No description" }
      "${index + 1}. [$nominator] $taskDesc"
    }.joinToString("\n\n")

    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    config.council.forEach { strategy ->
      val state = reasoningStates[strategy.name]!!
      val voter = ParsedAgent(
        name = "Voter",
        resultClass = Voting::class.java,
        exampleInstance = Voting(listOf(1, 3), "Tasks 1 and 3 align with goals."),
        prompt = "Vote for the best tasks.",
        model = orchestrationConfig.defaultSmart.getChildClient(task),
        parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
        temperature = orchestrationConfig.temperature
      )
      val vote = voter.answer(
        listOf(
          "User Message: $userMessage",
          "My Role: ${strategy.name} - ${strategy.description}",
          "My State: ${formatState(strategy, state)}",
          "Nominations:\n$nominationDescriptions",
          "Please vote for the best tasks by index (1-based)."
        )
      ).obj
      vote.votes.forEach { index ->
        if (index in 1..nominations.size) {
          votes[index] = votes.getOrDefault(index, 0) + 1
        }
      }
    }

    return votes.entries.sortedByDescending { it.value }
      .take(maxTasksPerIteration)
      .map { nominations[it.key - 1].second }
  }

  private fun runTask(
    coordinator: TaskOrchestrator,
    currentTask: TaskExecutionConfig,
    userMessage: String,
    task: SessionTask
  ): String {
    val taskImpl = orchestrationConfig.getImpl(currentTask)
    val result = StringBuilder()
    taskImpl.run(
      agent = coordinator,
      messages = listOf(userMessage) + formatEvalRecords(),
      task = task,
      resultFn = { result.append(it) },
      orchestrationConfig = orchestrationConfig,
    )
    return result.toString()
  }

  private fun processTaskExpansionRecursive(
    currentText: String,
    task: SessionTask,
    parsedActor: ParsedAgent<Tasks>,
    processor: FixedConcurrencyProcessor
  ): List<AdaptivePlanningMode.TaskData> {
    val match = expansionExpressionPattern.find(currentText)
    if (match == null) {
      return try {
        val chosenTasks = parsedActor.getParser().apply(currentText)
        listOf(AdaptivePlanningMode.TaskData(chosenTasks, currentText))
      } catch (e: Exception) {
        log.error("Error parsing task text: $currentText", e)
        emptyList()
      }
    } else {
      val expression = match.groupValues[1]
      val options = expression.split('|')
      val tabs = TabbedDisplay(task)
      val futures = options.map { option ->
        processor.submit {
          val subTask = tabs.newTask(option)
          val nextText = currentText.replaceFirst(match.value, option)
          processTaskExpansionRecursive(nextText, subTask, parsedActor, processor)
        }
      }
      return futures.flatMap { it.get() }
    }
  }

  private fun formatEvalRecords(): List<String> {
    var currentLength = 0
    val formattedRecords = mutableListOf<String>()
    for (record in executionRecords.reversed()) {
      val formattedRecord = """
        # Task ${executionRecords.indexOf(record) + 1}
        ## Task:
        ```json
        ${JsonUtil.toJson(record.task!!)}
        ```
        ## Result:
        ${record.result}
      """
      if (currentLength + formattedRecord.length > maxTaskHistoryChars) {
        formattedRecords.add("... (earlier records truncated)")
        break
      }
      formattedRecords.add(0, formattedRecord)
      currentLength += formattedRecord.length
    }
    return formattedRecords
  }


  private fun writeToTranscript(content: String) {
    transcriptStream?.write(content.toByteArray())
  }

  data class Voting(
    @Description("The indices of the tasks to execute (1-based).")
    val votes: List<Int> = emptyList(),
    @Description("Reasoning for the votes.")
    val reasoning: String = ""
  )

  companion object {
    val inputCnt = 1
  }
}