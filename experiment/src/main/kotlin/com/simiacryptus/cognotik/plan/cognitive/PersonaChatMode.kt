package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.instance
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

open class PersonaChatConfig(
  type: CognitiveModeType<*> = CoreTasks.Chat,
  var cognitiveStrategy: CognitiveSchemaStrategy = CognitiveSchemaStrategy.ProjectManager,
  var useExpansionSyntax: Boolean = true
) : CognitiveModeConfig(type)

open class PersonaChatMode(
  orchestrationConfig: OrchestrationConfig,
  session: Session,
  user: User,
  val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)
) : CognitiveMode<PersonaChatConfig>(
  orchestrationConfig,
  session,
  user
) {

  init {
    require(orchestrationConfig.smartModel?.instance(orchestrationConfig.user) != null) { "Default model must be specified in orchestration config" }
    require(orchestrationConfig.fastModel?.instance(orchestrationConfig.user) != null) { "Parsing model must be specified in orchestration config" }
  }

  private val messagesLock = Any()
  private val transcriptLock = Any()
  private val messages get() = messageMaps.computeIfAbsent(session) { ConcurrentLinkedQueue() }
  private val messageBuffer = ConcurrentLinkedQueue<String>()
  private var transcriptStream: FileOutputStream? = null
  private var isProcessing = false
  private val reasoningState = AtomicReference<Any?>(null)
  private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()

  private val idSubPattern = """[^|\n,/\\;}\]\[><()@]+"""
  private val expansionExpressionPattern = Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""")
  private val sequenceExpansionPattern = Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""")
  private val rangeExpansionPattern = Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""")

  override fun initialize(task: SessionTask) {
    log.debug("PersonaChatMode initialized with task types: ${enabledTasks.joinToString(", ") { it.name }}")
    transcriptStream = task.transcript()
  }

  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")
    val parserChatter = orchestrationConfig.defaultFast.getChildClient(task)
    val defaultChat = orchestrationConfig.defaultSmart.getChildClient(task)

    synchronized(messagesLock) {
      messageBuffer.add(userMessage)
      if (isProcessing) {
        return
      }
      isProcessing = true
    }

    task.echo(userMessage.renderMarkdown(true))
    writeToTranscript("## User\n\n$userMessage\n\n")
    task.ui.pool.submit {
      try {
        while (!Thread.interrupted()) {
          sleep(100)
          val msg = messageBuffer.poll() ?: continue
          val t = task.newTask()
          task.add(t.placeholder)
          execute(t, msg, parserChatter, defaultChat)
        }
      } finally {
        synchronized(messagesLock) {
          isProcessing = false
        }
      }
    }
  }

  private fun execute(
    task: SessionTask,
    userMessage: String,
    parsingChatter: ChatInterface,
    defaultChat: ChatInterface
  ) {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    try {
      val expandedUserMessage = if (config.useExpansionSyntax) expandTopics(userMessage) else userMessage

      val expansionFunctions = processMsgRecursive(
        expandedUserMessage, task, parsingChatter, defaultChat
      )
      val aggregateResponse = StringBuilder()
      runAll(task, expansionFunctions, aggregateResponse)

      synchronized(messagesLock) {
        messages.add(ModelSchema.ChatMessage(ModelSchema.Role.user, expandedUserMessage.toContentList()))
        if (aggregateResponse.isNotEmpty()) {
          messages.add(
            ModelSchema.ChatMessage(
              ModelSchema.Role.assistant, aggregateResponse.toString().toContentList()
            )
          )
        }
      }

      if (aggregateResponse.isNotEmpty()) {
        writeToTranscript("## Assistant\n\n${aggregateResponse}\n\n")
      }
    } catch (e: Exception) {
      log.error("Error executing task", e)
      writeToTranscript("## Error\n\n${e.message}\n```\n${e.stackTraceToString()}\n```\n\n")
      task.error(e)
    }
  }

  private fun processMsgRecursive(
    currentMessage: String, task: SessionTask, parsingChatter: ChatInterface, defaultChatter: ChatInterface
  ): List<(StringBuilder) -> Unit> {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    if (config.useExpansionSyntax) {
      val rangeMatch = rangeExpansionPattern.find(currentMessage)
      if (rangeMatch != null) {
        return expandRange(currentMessage, task, rangeMatch, parsingChatter, defaultChatter)
      }
      val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
      if (sequenceMatch != null) {
        return listOf { finalAggregate: StringBuilder ->
          expandSequence(
            task,
            sequenceMatch.groupValues[1].split(Regex("""\s*->\s*""")),
            currentMessage,
            sequenceMatch.value,
            defaultChatter,
            parsingChatter
          )
        }
      }
      val match = expansionExpressionPattern.find(currentMessage)
      if (match != null && match.groupValues[1].split('|', ',').size > 1) {
        return expandAlternatives(currentMessage, task, match) { msg, tsk ->
          processMsgRecursive(msg, tsk, parsingChatter, defaultChatter)
        }
      }
    }
    return listOf { aggregateResponse: StringBuilder ->
      executeTask(currentMessage, task, aggregateResponse, defaultChatter, parsingChatter)
    }
  }

  private fun executeTask(
    userMessage: String,
    task: SessionTask,
    aggregateResponse: StringBuilder,
    defaultModel: ChatInterface,
    parserChatter: ChatInterface
  ) {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    val currentState = reasoningState.updateAndGet { state ->
      if (state == null) {
        val s = config.cognitiveStrategy.initialize(
          userMessage,
          getConversationContext(),
          orchestrationConfig,
          task,
          describer
        )
        val stateTask = task.newTask()
        task.add(stateTask.placeholder)
        stateTask.complete(
          "### Initial Persona State\n" + config.cognitiveStrategy.formatState(s).renderMarkdown()
        )
        s
      } else {
        state
      }
    }!!


    val tabs = TabbedDisplay(task)

    val planTask = tabs.newTask("Plan")
    val chosenTask = if (orchestrationConfig.autoFix) {
      val result = requestToTaskWithPersona(
        defaultModel, parserChatter,
        userMessage,
        orchestrationConfig,
        currentState,
        config.cognitiveStrategy,
        getConversationContext(),
        describer
      )
      planTask.add(result.first.text.renderMarkdown())
      planTask.complete("Executing task:\n```json\n${JsonUtil.toJson(result.second)}\n```".renderMarkdown())
      result
    } else {
      Discussable(
        task = planTask,
        heading = "Plan Review",
        userMessage = { userMessage },
        initialResponse = { prompt ->
          requestToTaskWithPersona(
            defaultModel, parserChatter,
            prompt,
            orchestrationConfig,
            currentState,
            config.cognitiveStrategy,
            getConversationContext(),
            describer
          )
        },
        outputFn = { (reasoning, task) ->
          reasoning.text.renderMarkdown() + "\n```json\n${JsonUtil.toJson(task)}\n```".renderMarkdown()
        },
        reviseResponse = { history ->
          val feedbackHistory = history.map { (msg, role) ->
            "${if (role == ModelSchema.Role.user) "USER" else "ASSISTANT"}: $msg"
          }
          val lastUserMsg = history.lastOrNull { it.second == ModelSchema.Role.user }?.first ?: userMessage
          requestToTaskWithPersona(
            defaultModel, parserChatter,
            lastUserMsg,
            orchestrationConfig,
            currentState,
            config.cognitiveStrategy,
            getConversationContext() + feedbackHistory.dropLast(1),
            describer
          )
        }
      ).call()
    }
    val taskConfigJson = JsonUtil.toJson(chosenTask)
    writeToTranscript("### Plan\n\nExecuting task:\n```json\n$taskConfigJson\n```\n\n")
    synchronized(messagesLock) {
      messages.add(
        ModelSchema.ChatMessage(
          ModelSchema.Role.assistant,
          "Executing task:\n```json\n$taskConfigJson\n```".toContentList()
        )
      )
    }
    val resultSemaphore = Semaphore(0)
    val resultRef = AtomicReference<String>()

    tabs.newTask("Run").apply {
      orchestrationConfig.getImpl(chosenTask?.component2()).run(
        agent = TaskOrchestrator(
          user = user,
          session = session,
          dataStorage = ui.dataStorage!!,
          root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
            ?: ui.dataStorage.getUserDir(user, session).toPath()
            ?: File(".").toPath()),
        messages = getConversationContext().takeLast(10) + listOf("USER: $userMessage"),
        task = this,
        resultFn = { result ->
          task.newTask().apply {
            tabs["Output"] = placeholder
            complete(result.renderMarkdown())
          }
          resultRef.set(result)
          resultSemaphore.release()
        },
        orchestrationConfig = orchestrationConfig,
      )
      this.complete()
    }
    resultSemaphore.acquire()
    val resultString = resultRef.get() ?: ""
    aggregateResponse.append(resultString).append("\n\n")

    val executionRecord = AdaptivePlanningMode.ExecutionRecord(
      task = chosenTask?.component2(),
      result = resultString
    )
    val newState = config.cognitiveStrategy.update(
      currentState,
      listOf(executionRecord),
      userMessage,
      getConversationContext(),
      orchestrationConfig,
      task,
      describer
    )
    reasoningState.set(newState)

    task.newTask().apply {
      tabs["State"] = placeholder
      complete("### Updated Persona State\n" + config.cognitiveStrategy.formatState(newState).renderMarkdown())
    }

    task.complete()
  }

  private fun runAll(task: SessionTask, function1s: List<(StringBuilder) -> Unit>, target: StringBuilder) {
    val fixedConcurrencyProcessor = FixedConcurrencyProcessor(task.ui.pool, 4)
    function1s.map { function1 ->
      fixedConcurrencyProcessor.submit {
        function1(target)
      }
    }.forEach { it.get() }
  }

  private fun expandRange(
    currentMessage: String,
    task: SessionTask,
    rangeMatch: MatchResult,
    parsingChatter: ChatInterface,
    defaultChatter: ChatInterface
  ): List<(StringBuilder) -> Unit> = listOf { finalAggregate: StringBuilder ->
    val start = rangeMatch.groupValues[1].toInt()
    val end = rangeMatch.groupValues[2].toInt()
    val step = rangeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1
    expandSequence(
      task,
      generateSequence(start) { it + step }.takeWhile { if (step > 0) it <= end else it >= end }.toList()
        .map { it.toString() },
      currentMessage,
      rangeMatch.value,
      defaultChatter,
      parsingChatter
    )
  }

  private fun expandAlternatives(
    currentMessage: String,
    task: SessionTask,
    match: MatchResult,
    recursiveFn: (String, SessionTask) -> List<(StringBuilder) -> Unit>
  ): List<(StringBuilder) -> Unit> {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    val tabs = TabbedDisplay(task, closable = config.useExpansionSyntax)
    return match.groupValues[1].split('|', ',').flatMap { option ->
      recursiveFn(
        currentMessage.replaceFirst(match.value, option),
        task.newTask().apply { tabs[option] = placeholder })
    }.apply {
      tabs.update()
    }
  }

  private fun expandSequence(
    task: SessionTask,
    items: List<String>,
    currentMessage: String,
    expression: String,
    defaultChatter: ChatInterface,
    parsingChatter: ChatInterface
  ) {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    val aggregatedResponse = StringBuilder()
    val tabs = TabbedDisplay(task, closable = config.useExpansionSyntax)
    for (item in items) {
      val newMessage = currentMessage.replaceFirst(expression, item)
      val subTaskFunctions = processMsgRecursive(
        currentMessage = newMessage,
        task = task.newTask().apply { tabs[item] = placeholder },
        defaultChatter = defaultChatter,
        parsingChatter = parsingChatter
      )
      val subAggregate = StringBuilder()
      runAll(task, subTaskFunctions, subAggregate)
      aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
    }
    tabs.update()
  }

  protected open fun expandTopics(userMessage: String): String {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    if (!config.useExpansionSyntax) return userMessage
    val topicReferencePattern = Regex("""@\{([A-Z][a-zA-Z0-9_ ]+)\}|@([A-Z][a-zA-Z0-9_]*)""")
    return topicReferencePattern.replace(userMessage) { matchResult ->
      val topicType = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
      val topicList = aggregateTopics[topicType]
      val entities = synchronized(topicList ?: Any()) {
        topicList?.toList()
      }
      if (!entities.isNullOrEmpty()) {
        "@[${entities.joinToString("|")}]"
      } else {
        matchResult.value
      }
    }
  }

  private fun writeToTranscript(content: String) {
    synchronized(transcriptLock) {
      transcriptStream?.write(content.toByteArray())
      transcriptStream?.flush()
    }
  }

  private fun getConversationContext(): List<String> {
    val contextMessages = synchronized(messagesLock) {
      messages.toList()
    }
    return contextMessages.map { message ->
      "${message.role?.name?.uppercase()}: ${message.content?.joinToString("") { it.text ?: "" } ?: ""}"
    }
  }

  override fun contextData(): List<String> {
    return getConversationContext()
  }

  companion object {
    val inputCnt: Int = 1
    private val messageMaps = ConcurrentHashMap<Session, ConcurrentLinkedQueue<ModelSchema.ChatMessage>>()
    private val log = getLogger(PersonaChatMode::class.java)

    fun requestToTaskWithPersona(
      defaultModel: ChatInterface,
      fastModel: ChatInterface,
      userMessage: String,
      orchestrationConfig: OrchestrationConfig,
      state: Any,
      strategy: CognitiveSchemaStrategy,
      history: List<String>,
      describer: TaskContextYamlDescriber
    ): Pair<ParsedResponse<Tasks>, TaskExecutionConfig> {
      Tasks.initDescriber(orchestrationConfig, describer)
      val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
      val parsedActor = ParsedAgent(
        name = "TaskChooser",
        resultClass = Tasks::class.java,
        exampleInstance = Tasks(
          listOfNotNull(availableTaskTypes.firstOrNull()?.let {
            orchestrationConfig.getImpl(it).executionConfig
          }).toMutableList()
        ),
        prompt = buildString {
          append("You are an AI assistant with the following internal state/persona:\n")
          append(strategy.formatState(state))
          append("\n\n")
          append(strategy.getTaskSelectionGuidance(state))
          append("\n\n")
          append("Given the conversation history and the user's latest input, choose ONE task to execute.\n")
          append("Available task types:\n")
          append(orchestrationConfig.taskSettings.values.joinToString("\n\n") { config ->
            val taskType = TaskType.valueOf(config.task_type ?: return@joinToString "")
            val configName = config.name?.let { " ($it)" } ?: ""
            "* ${taskType.name}$configName:\n  ${
              orchestrationConfig.getImpl(taskType).promptSegment().trim().trimIndent()
                .indent("  ")
            }" + (orchestrationConfig.workingDir?.let { root ->
              "\nAvailable files:\n\n" + FileSelectionUtils.getAvailableFiles(Path(root))
                .joinToString("\n") { "      - $it" } + "\n"
            } ?: "")
          })
          append("\nChoose the most suitable task type and provide details of how it should be executed.")
        },
        model = defaultModel,
        parsingChatter = fastModel,
        temperature = orchestrationConfig.temperature,
        describer = describer,
        parserPrompt = ("Task Subtype Schema:\n" + availableTaskTypes.joinToString("\n\n") { taskType ->
          "${taskType.name}:\n  ${
            describer.describe(taskType.executionConfigClass).trim().trimIndent().indent("  ")
          }".trim()
        })
      )
      val answer = parsedActor.answer(
        history + listOf(
          "USER: $userMessage",
          "Please choose a single task to execute based on the current conversation context and your internal persona."
        )
      )
      val chosenTask = answer.obj.tasks?.firstOrNull() ?: throw IllegalStateException("No task was selected")
      return Pair(answer, chosenTask)
    }
  }
}