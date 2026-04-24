package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.chat.model.ChatInterface
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
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

open class ConversationalModeConfig(
  var useExpansionSyntax: Boolean = true
) : CognitiveModeConfig(type = CoreTasks.Chat)


/**
 * A cognitive mode that executes tasks based on user input while maintaining conversation history.
 */
open class ConversationalMode(
  orchestrationConfig: OrchestrationConfig,
  session: Session,
  user: User
) : CognitiveMode<ConversationalModeConfig>(
  orchestrationConfig,
  session,
  user
) {

  init {
    require(orchestrationConfig.smartModel?.instance(orchestrationConfig.user) != null) { "Default model must be specified in orchestration config" }
    require(orchestrationConfig.fastModel?.instance(orchestrationConfig.user) != null) { "Parsing model must be specified in orchestration config" }
  }

  private val messagesLock = Any()
  private val messages get() = messageMaps.computeIfAbsent(session) { ConcurrentLinkedQueue() }
  private val messageBuffer = ConcurrentLinkedQueue<String>()
  private var transcriptStream: FileOutputStream? = null
  private var isProcessing = false
  private val systemPrompt =
    "Given the following input, choose ONE task to execute and describe it in detail. Be sure to include all necessary parameters for execution, and all data required to complete the task."
  private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
  private val idSubPattern =
    """[^|\n,/\\;}\]\[><()@]+""" // Matches any valid identifier character except for special characters used in the expansion syntax
  private val expansionExpressionPattern =
    Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""") // Matches @[option1|option2|option3]
  private val sequenceExpansionPattern =
    Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""") // Matches @{item1 -> item2 -> item3}
  private val rangeExpansionPattern =
    Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""") // Matches @(start..end:step) or @(start to end by step)

  override fun initialize(task: SessionTask) {
    log.debug(
      "ConversationalMode initialized with task types: ${enabledTasks.joinToString(", ") { it.name }}",
      RuntimeException()
    )
    transcriptStream = task.transcript()
    log.debug(
      "Task configurations: ${
        orchestrationConfig.taskSettings.values.joinToString(", ") {
          "${it.task_type}${it.name?.let { name -> ":$name" } ?: ""}"
        }
      }")
  }

  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")
    val parserChatter = orchestrationConfig.defaultFast.getChildClient(task)
    val defaultChat = this@ConversationalMode.orchestrationConfig.defaultSmart.getChildClient(task)

    synchronized(messagesLock) {
      messageBuffer.add(userMessage)
      if (isProcessing) {
        log.debug("Already processing a task, adding message to buffer: ${userMessage}")
        return
      }
      isProcessing = true
    }

    task.echo(userMessage.renderMarkdown(true))
    writeToTranscript("## User\n\n$userMessage\n\n")
    task.ui.pool.submit {
      try {
        while (!Thread.interrupted()) {
          sleep(100) // Brief pause to allow batching of messages
          val userMessage = messageBuffer.poll() ?: continue
          val task = task.newTask()
          execute(task, userMessage, parserChatter, defaultChat)
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
    try {
      val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
      val expandedUserMessage = expandTopics(userMessage)
      val expansionFunctions = processMsgRecursive(
        expandedUserMessage, task, parsingChatter, defaultChat
      )
      val aggregateResponse = StringBuilder()
      runAll(task, expansionFunctions, aggregateResponse)
      // Now add the original user message and aggregate response to history
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

      // Extract topics from the aggregated response
      if (config.useExpansionSyntax && aggregateResponse.isNotEmpty()) {
        try {
          writeToTranscript("## Assistant\n\n${aggregateResponse}\n\n")
          val model = defaultChat
          val topics = extractTopics(aggregateResponse.toString(), model, parsingChatter)
          topics.topics?.forEach { (topicType, entities) ->
            val topicList = aggregateTopics.computeIfAbsent(topicType) { mutableListOf() }
            synchronized(topicList) {
              topicList.addAll(entities)
            }
          }
          if (topics.topics?.isNotEmpty() == true) {
            val topicsText = topics.topics.entries.joinToString("\n") {
              "* `{${it.key}}` - ${it.value.joinToString(", ") { "`$it`" }}"
            }
            task.complete(topicsText.renderMarkdown(), additionalClasses = "topics")
            writeToTranscript("### Topics\n\n$topicsText\n\n")
          }
        } catch (e: Exception) {
          log.error("Error in topic extraction", e)
        }
      }
      task.complete()
    } catch (e: Exception) {
      log.error("Error executing task", e)
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
        return expandRange(
          currentMessage, task, rangeMatch, parsingChatter, defaultChatter
        )
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
        return expandAlternatives(
          currentMessage, task, match
        ) { msg, tsk ->
          processMsgRecursive(
            msg, tsk, parsingChatter, defaultChatter = defaultChatter
          )
        }
      }
    }
    return listOf { aggregateResponse: StringBuilder ->
      executeTask(
        currentMessage, task, aggregateResponse, defaultChatter, parsingChatter
      )
    }
  }

  private fun executeTask(
    userMessage: String,
    task: SessionTask,
    aggregateResponse: StringBuilder,
    defaultModel: ChatInterface,
    parserChatter: ChatInterface
  ) {
    val tabs = TabbedDisplay(task)
    val planTask = tabs.newTask("Plan")

    val chosenTask: Pair<ParsedResponse<Tasks>, TaskExecutionConfig>? = if (orchestrationConfig.autoFix) {
      val result = requestToTask(
        defaultModel, parserChatter,
        userMessage,
        this@ConversationalMode.orchestrationConfig,
        this@ConversationalMode.systemPrompt,
        this.getConversationContext()
      )
      planTask.add(result.first.text.renderMarkdown())
      planTask.complete("Executing task:\n```json\n${JsonUtil.toJson(result.second)}\n```".renderMarkdown())
      result
    } else {
      Discussable(
        task = planTask,
        heading = "Plan Task",
        userMessage = { userMessage },
        initialResponse = {
          requestToTask(
            defaultModel, parserChatter,
            userMessage,
            this@ConversationalMode.orchestrationConfig,
            this@ConversationalMode.systemPrompt,
            this.getConversationContext()
          )
        },
        outputFn = { (reasoning, taskConfig) ->
          reasoning.text.renderMarkdown() +
              "\n\nProposed Task:\n```json\n${JsonUtil.toJson(taskConfig)}\n```".renderMarkdown()
        },
        reviseResponse = { history ->
          val discussionContext = history.map { (msg, role) ->
            "${role.name.uppercase()}: $msg"
          }
          requestToTask(
            defaultModel, parserChatter,
            userMessage,
            this@ConversationalMode.orchestrationConfig,
            this@ConversationalMode.systemPrompt,
            this.getConversationContext() + discussionContext
          )
        }
      ).call()
    }

    // Add task configuration to conversation history
    val taskConfigJson = JsonUtil.toJson(chosenTask)
    synchronized(messagesLock) {
      messages.add(
        ModelSchema.ChatMessage(
          ModelSchema.Role.assistant,
          "Executing task:\n```json\n$taskConfigJson\n```".toContentList()
        )
      )
    }

    val resultRef = AtomicReference<String>()
    tabs.newTask("Run").apply {
      orchestrationConfig.getImpl(chosenTask?.component2()).run(
        agent = TaskOrchestrator(
          user = user,
          session = session,
          dataStorage = ui.dataStorage!!,
          root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
            ?: ui.dataStorage.getSessionDir(user, session).toPath()
            ?: File(".").toPath()),
        messages = getConversationContext().takeLast(10) + listOf("USER: $userMessage"),
        task = this,
        resultFn = { result ->
          tabs.newTask("Output").complete(result.renderMarkdown())
          // Don't add to messages here - it will be added in execute() after all expansions complete
          resultRef.set(result)
          aggregateResponse.append(resultRef.get() ?: "").append("\n\n")
          task.complete()
        },
        orchestrationConfig = orchestrationConfig,
      )
      this.complete()
    }
  }

  /**
   * Executes a list of functions, each appending to the target StringBuilder, potentially in parallel.
   */
  private fun runAll(task: SessionTask, function1s: List<(StringBuilder) -> Unit>, target: StringBuilder) {
    val fixedConcurrencyProcessor = FixedConcurrencyProcessor(task.ui.pool, 4)
    function1s.map { function1 ->
      fixedConcurrencyProcessor.submit {
        function1(target)
      }
    }.forEach { it.get() }
  }

  /**
   * Expands range expressions in the format [start...end:step]
   * Creates a sequence of numbers from start to end with the given step (default 1)
   */
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

  /**
   * Expands alternative expressions in the format {option1|option2|option3}
   * Each option is processed in parallel
   */
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
        tabs.newTask(option)
      )
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
    val aggregatedResponse = StringBuilder()
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    val tabs = TabbedDisplay(task, closable = config.useExpansionSyntax)
    for (item in items) {
      val newMessage = currentMessage.replaceFirst(expression, item)
      val subTaskFunctions = processMsgRecursive(
        currentMessage = newMessage,
        task = tabs.newTask(item),
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
    // Matches both @TopicType and @{Topic Type With Spaces}
    val topicReferencePattern = Regex("""@\{([A-Z][a-zA-Z0-9_ ]+)\}|@([A-Z][a-zA-Z0-9_]*)""")
    return topicReferencePattern.replace(userMessage) { matchResult ->
      // Group 1 is for delimited format @{Topic Type}, Group 2 is for simple format @TopicType
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

  private fun extractTopics(response: String, model: ChatInterface, chatInterface: ChatInterface): Topics {
    val topicsParsedActor = ParsedAgent(
      resultClass = Topics::class.java,
      prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
      model = model,
      temperature = orchestrationConfig.temperature,
      name = "Topics",
      parsingChatter = chatInterface,
    )
    return topicsParsedActor.getParser().apply(response)
  }

  data class Topics(
    val topics: Map<String, List<String>>? = emptyMap()
  )


  /**
   * Writes content to the transcript file if available
   */
  private fun writeToTranscript(content: String) {
    transcriptStream?.write(content.toByteArray())
    transcriptStream?.flush()
  }

  /**
   * Gets the current conversation context as a list of messages
   */
  private fun getConversationContext(): List<String> {
    val contextMessages = synchronized(messagesLock) {
      messages.toList()
    }

    return contextMessages.map { message ->
      "${message.role?.name?.uppercase()}: ${message.content?.joinToString("") { it.text ?: "" } ?: ""}"
    }
  }

  /**
   * Provides context data for the conversation
   */
  override fun contextData(): List<String> {
    return getConversationContext()
  }

  companion object {

    val inputCnt = 1

    private val messageMaps = ConcurrentHashMap<Session, ConcurrentLinkedQueue<ModelSchema.ChatMessage>>()
    private val log = LoggerFactory.getLogger(ConversationalMode::class.java)
    fun requestToTask(
      defaultModel: ChatInterface,
      fastModel: ChatInterface,
      userMessage: String,
      orchestrationConfig: OrchestrationConfig,
      prompt: String = "",
      history: List<String> = emptyList(),
      singleStage: Boolean = false,
      taskTypes: List<TaskType<*, *>> = TaskType.getAvailableTaskTypes(orchestrationConfig),
    ): Pair<ParsedResponse<Tasks>, TaskExecutionConfig> {
      log.debug("requestToTask called with userMessage: ${userMessage.take(200)}, history size: ${history.size}, singleStage: $singleStage")
      log.debug("Available task types: ${taskTypes.joinToString(", ") { it.name }}")
      val describer = TaskContextYamlDescriber(orchestrationConfig)
      Tasks.initDescriber(orchestrationConfig, describer)
      log.debug("Initialized describer and Tasks schema")
      val parserPromptA = buildString {
        append(prompt)
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
        if (orchestrationConfig.taskSettings.values.any { it.name != null }) {
          append("\nNote: Some task types have multiple configurations available. You can specify which configuration to use by setting the task_config_name field.")
        }
      }
      val parserPromptB = "Task Subtype Schema:\n" + taskTypes.joinToString("\n\n") { taskType ->
        "${taskType.name}:\n  ${
          describer.describe(taskType.executionConfigClass).trim().trimIndent().indent("  ")
        }".trim()
      }
      val parsedActor = ParsedAgent(
        name = "TaskChooser",
        resultClass = Tasks::class.java,
        exampleInstance = Tasks(
          listOfNotNull(taskTypes.firstOrNull()?.let {
            orchestrationConfig.getImpl(it).executionConfig
          }).toMutableList()
        ),
        prompt = parserPromptA.also { log.debug("Constructed prompt for ParsedAgent (length: ${it.length}):\n${it.take(500)}...") },
        model = defaultModel,
        parsingChatter = fastModel,
        temperature = orchestrationConfig.temperature,
        describer = describer,
        parserPrompt = parserPromptB,
        singleStage = singleStage
      )
      log.debug("ParsedAgent created, sending request with ${history.size} history messages")
      // Use the expanded userMessage with full conversation context for task selection
      val answer = try {
        parsedActor.answer(
          history + listOf(
            "USER: $userMessage",
            "Please choose a single task to execute based on the current conversation context above."
          )
        ).also {
          log.info("ParsedAgent returned response with ${it.obj.tasks?.size ?: 0} task(s)")
          log.debug("ParsedAgent raw response text (first 500 chars): ${it.text.take(500)}")
        }
      } catch (e: Exception) {
        log.error("ParsedAgent failed to produce a valid response for userMessage: ${userMessage.take(200)}", e)
        throw e
      }
      val chosenTask = answer.obj.tasks?.firstOrNull() ?: run {
        log.error("No task was selected from ParsedAgent response. Raw text: ${answer.text.take(500)}")
        throw IllegalStateException("No task was selected")
      }
      log.info(
        "Selected task type: ${chosenTask.task_type}, config name: ${chosenTask.task_description ?: "(default)"}, " +
            "for userMessage: ${userMessage.take(100)}"
      )
      log.debug("Chosen task details: ${JsonUtil.toJson(chosenTask)}")
      return Pair(answer, chosenTask)
    }
  }

}