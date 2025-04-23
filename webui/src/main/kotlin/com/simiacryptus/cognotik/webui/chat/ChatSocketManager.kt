package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.Retryable
import com.simiacryptus.cognotik.TabbedDisplay
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.core.actors.ParsedActor
import com.simiacryptus.cognotik.core.platform.Session
import com.simiacryptus.cognotik.core.platform.model.StorageInterface
import com.simiacryptus.cognotik.core.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManagerBase
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

open class ChatSocketManager(
  session: Session,
  var model: ChatModel,
  var parsingModel: ChatModel,
  val userInterfacePrompt: String = """
    ## Special Expansion Syntaxes

    You can use the following syntaxes in your messages to automatically expand your queries:

    * **Parallel Expansion:**
      Use `{option1|option2|option3}` to run the same prompt with each option in parallel.
      Example:
      `Tell me a joke about {cats|dogs|birds}`
      This will generate a joke about cats, a joke about dogs, and a joke about birds.

    * **Sequence Expansion:**
      Use `<step1;step2;step3>` to run a sequence of prompts, where the output of each feeds into the next.
      Example:
      `Summarize this text, then <translate to French;translate to German>`
      This will summarize, then translate the summary to French, then to German.

    * **Range Expansion:**
      Use `[[start..end:step]]` to iterate over a range of numbers.
      Example:
      `Count from [[1..5]]`
      This will run the prompt for 1, 2, 3, 4, and 5.

    * **Topic Reference Expansion:**
      Use `{topicType}` to refer to previously identified topics.
      Example:
      `Tell me about {Person}`
      If "Person" topics have been identified, this will expand to include all of them.

    ---
    You can combine these syntaxes for more complex expansions.
  """.trimIndent(),
  open val initialAssistantPrompt: String = "",
  open val systemPrompt: String,
  var api: ChatClient,
  var temperature: Double = 0.3,
  applicationClass: Class<out ChatServer>,
  val storage: StorageInterface?,
  open val fastTopicParsing: Boolean = true,
  val retriable: Boolean = true,
) : SocketManagerBase(session, storage, owner = null, applicationClass = applicationClass) {
  
  private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
  private val messagesLock = Any()
  
  init {
    if (userInterfacePrompt.isNotBlank()) {
      val task = newTask(false, true)
      task.complete(userInterfacePrompt.renderMarkdown)
    }
  }
  
  protected val messages by lazy {
    val list = listOf(
      ApiModel.ChatMessage(ApiModel.Role.system, systemPrompt.toContentList()),
    ).toMutableList()
    if (initialAssistantPrompt.isNotBlank()) list +=
      ApiModel.ChatMessage(ApiModel.Role.assistant, initialAssistantPrompt.toContentList())
    list
  }
  val ui = ApplicationInterface(this)
  
  override fun onRun(userMessage: String, socket: ChatSocket) {
    // Apply topic-based autoexpansion to user message
    val expandedUserMessage = applyTopicAutoexpansion(userMessage)
    
    val task = newTask()
    val api = api.getChildClient(task)
    task.echo(renderResponse(expandedUserMessage, task))
    
    synchronized(messagesLock) {
      messages += ApiModel.ChatMessage(ApiModel.Role.user, expandedUserMessage.toContentList())
    }
    
    try {
      if(!retriable) {
        task.add("")
        val responseString = respond(api, task, expandedUserMessage)
        
        synchronized(messagesLock) {
          // Remove last assistant message if it exists (for retries)
          if (messages.lastOrNull()?.role == ApiModel.Role.assistant) {
            messages.removeAt(messages.size - 1)
          }
          messages += ApiModel.ChatMessage(ApiModel.Role.assistant, responseString.toContentList())
        }
        
        task.complete()
      } else {
        Retryable(ui, task) { it: StringBuilder ->
          val task = ui.newTask(false)
          pool.submit {
            try {
              task.add("")
              val responseString = respond(api, task, expandedUserMessage)
              
              synchronized(messagesLock) {
                // Remove last assistant message if it exists (for retries)
                if (messages.lastOrNull()?.role == ApiModel.Role.assistant) {
                  messages.removeAt(messages.size - 1)
                }
                messages += ApiModel.ChatMessage(ApiModel.Role.assistant, responseString.toContentList())
              }
              
              task.complete()
            } catch (e: Throwable) {
              log.warn("Exception occurred while processing chat message", e)
            }
          }
          task.placeholder
        }
        
      }
    } catch (e: Exception) {
      log.info("Error in chat", e)
      task.error(ApplicationInterface(this), e)
    }
  }
  
  // Pattern for parallel expansion: {option1|option2}
  private val expansionExpressionPattern = Regex("""\{([^|\n,/\\;}{]+(?:\|[^|\n,/\\;}{]+)+)}""")
  
  // Pattern for ordered sequence expansion: <item1;item2;item3>
  private val sequenceExpansionPattern = Regex("""<([^;><\n,/\\]+(?:;[^;><\n,/\\]+)+)>""")
  
  // Pattern for range expansion: [[start..end:step]]
  private val rangeExpansionPattern = Regex("""\[\[(\d+)(?:\.{2,3}| to )(\d+)(?:(?::| by )(\d+))?]]""")
  
  
  protected open fun respond(api: ChatClient, task: SessionTask, userMessage: String) = buildString {
    val currentChatMessages = chatMessages()
    val function1List = processMsgRecursive(api, userMessage, task, currentChatMessages)
    runAll(function1List, this)
  }.let { response ->
    try {
      val answer = extractTopics(api, response)
      val topicsText = try {
        answer.topics.let { topics ->
          if (topics?.isNotEmpty() == true) {
            // Add identified topics to the aggregate list
            topics.forEach { (topicType, entities) ->
              val topicList = aggregateTopics.computeIfAbsent(topicType) { mutableListOf() }
              synchronized(topicList) {
                topicList.addAll(entities)
              }
            }
            val joinToString = topics.entries.joinToString("\n") { "* `{${it.key}}` - ${it.value.joinToString(", ") { "`$it`" }}" }
            task.complete(joinToString.renderMarkdown(), additionalClasses = "topics")
            "\n\n" + joinToString
          } else {
            ""
          }
        }
      } catch (e: Exception) {
        log.error("Error in topic extraction", e)
        ""
      }
      response + topicsText
    } catch (e: Exception) {
      task.error(null, e)
      log.error("Error in topic extraction", e)
      response
    }
  }
  
  
  /**
   * Executes a list of functions, each appending to the target StringBuilder, potentially in parallel.
   */
  private fun runAll(function1s: List<(StringBuilder) -> Unit>, target: StringBuilder) {
    val fixedConcurrencyProcessor = FixedConcurrencyProcessor(this@ChatSocketManager.pool, 4)
    function1s.map { function1 ->
      fixedConcurrencyProcessor.submit {
        function1(target)
      }
    }.forEach { it.get() }
  }
  
  private fun extractTopics(api: ChatClient, response: String): Topics {
    val topicsParsedActor = ParsedActor(
      resultClass = Topics::class.java,
      prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
      model = model,
      temperature = temperature,
      name = "Topics",
      parsingModel = parsingModel,
    )
    return if (fastTopicParsing) {
      topicsParsedActor.getParser(api).apply(response)
    } else {
      topicsParsedActor.answer(listOf(response), api).obj
    }
  }
  
  
  protected open fun chatMessages() = messages.let {
    synchronized(messagesLock) {
      listOf(ApiModel.ChatMessage(ApiModel.Role.system, systemPrompt.toContentList())) + it.toImmutableList().drop(1)
    }
  }.toList()
  
  data class Topics(
    val topics: Map<String, List<String>>? = emptyMap()
  )
  
  /**
   * Applies topic-based autoexpansion to the user message
   * Looks for @topic syntax and replaces with expansion options
   */
  protected open fun applyTopicAutoexpansion(userMessage: String): String {
    val topicReferencePattern = Regex("""\{([^}|]+)}""")
    return topicReferencePattern.replace(userMessage) { matchResult ->
      val topicType = matchResult.groupValues[1]
      val entities = aggregateTopics[topicType]?.toList() // Create a copy to avoid concurrent modification
      if (entities != null && entities.isNotEmpty()) {
        // Create an expansion expression with all entities of this type
        "{${entities.joinToString("|")}}"
      } else {
        // Keep the original reference if no entities found
        matchResult.value
      }
    }
  }
  
  private fun processMsgRecursive(
    api: ChatClient,
    currentMessage: String,
    task: SessionTask,
    baseMessages: List<ApiModel.ChatMessage>,
  ): List<(StringBuilder) -> Unit> {
    // 1. Handle range expansion [start..end:step]
    val rangeMatch = rangeExpansionPattern.find(currentMessage)
    if (rangeMatch != null) {
      return expandRange(api, currentMessage, task, baseMessages, rangeMatch)
    }
    val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
    if (sequenceMatch != null) {
      return expandSequences(api, currentMessage, task, baseMessages, sequenceMatch)
    }
    // 2. Handle parallel expansion {a|b|c}
    val match = expansionExpressionPattern.find(currentMessage)
    if (match != null) {
      return expandAlternatives(api, currentMessage, task, baseMessages, match, this::processMsgRecursive)
    }
    
    return listOf { aggregateResponse: StringBuilder ->
      task.add("")
      // Base case: no more expansions to process
      val finalMessages = baseMessages + ApiModel.ChatMessage(ApiModel.Role.user, currentMessage.toContentList())
      val responseRef = AtomicReference<String>()
      try {
        val chatResponse = api.chat(
          ApiModel.ChatRequest(
            messages = finalMessages,
            temperature = temperature,
            model = model.modelName,
          ), model
        )
        responseRef.set(chatResponse.choices.first().message?.content.orEmpty())
      } catch (e: Exception) {
        log.error("Error in API call", e)
        responseRef.set("Error: ${e.message}")
      }
      
      val response = responseRef.get() ?: "No response received"
      task.complete(renderResponse(response, task))
      aggregateResponse.append(response).append("\n\n") // Append response for aggregation
    }
  }
  
  /**
   * Expands range expressions in the format [start..end:step]
   * Creates a sequence of numbers from start to end with the given step (default 1)
   */
  private fun expandRange(
    api: ChatClient,
    currentMessage: String,
    task: SessionTask,
    baseMessages: List<ApiModel.ChatMessage>,
    rangeMatch: MatchResult
  ): List<(StringBuilder) -> Unit> = listOf { finalAggregate: StringBuilder ->
    val start = rangeMatch.groupValues[1].toInt()
    val end = rangeMatch.groupValues[2].toInt()
    val step = rangeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1
    expandSequence(
      task,
      baseMessages,
      generateSequence(start) { it + step }
        .takeWhile { if (step > 0) it <= end else it >= end }
        .toList()
        .map { it.toString() },
      currentMessage,
      rangeMatch.value,
      api
    )
  }
  
  
  /**
   * Expands alternative expressions in the format {option1|option2|option3}
   * Each option is processed in parallel
   */
  private fun expandAlternatives(
    api: ChatClient,
    currentMessage: String,
    task: SessionTask,
    baseMessages: List<ApiModel.ChatMessage>,
    match: MatchResult,
    recursiveFn: (ChatClient, String, SessionTask, List<ApiModel.ChatMessage>) -> List<(StringBuilder) -> Unit>
  ): List<(StringBuilder) -> Unit> {
    val tabs = TabbedDisplay(task, closable = false)
    return match.groupValues[1].split('|', ',').flatMap { option ->
      recursiveFn(
        api,
        currentMessage.replaceFirst(match.value, option),
        ui.newTask(false).apply { tabs[option] = placeholder },
        baseMessages,
      )
    }.apply {
      tabs.update()
    }
  }
  
  /**
   * Expands sequence expressions in the format <item1;item2;item3>
   * Each item is processed sequentially, with the output of each step feeding into the next.
   * Returns a single function that encapsulates the entire sequential process.
   */
  private fun expandSequences(
    api: ChatClient,
    currentMessage: String,
    task: SessionTask,
    baseMessages: List<ApiModel.ChatMessage>,
    sequenceMatch: MatchResult
  ) = listOf { finalAggregate: StringBuilder ->
    expandSequence(
      task,
      baseMessages,
      sequenceMatch.groupValues[1].split(';'),
      currentMessage,
      sequenceMatch.value,
      api
    )
  }
  
  private fun expandSequence(
    task: SessionTask,
    baseMessages: List<ApiModel.ChatMessage>,
    items: List<String>,
    currentMessage: String,
    expression: String,
    api: ChatClient
  ) {
    val aggregatedResponse = StringBuilder()
    val tabs = TabbedDisplay(task, closable = false)
    val messages = baseMessages.dropLast(1).toMutableList()
    for (item in items) {
      val itemTask = ui.newTask(false).apply { tabs[item] = placeholder }
      // Determine the sub-tasks for the current item
      val replaceFirst = currentMessage.replaceFirst(expression, item)
      val subTaskFunctions = processMsgRecursive(
        api = api,
        currentMessage = replaceFirst,
        task = itemTask,
        // Pass the accumulated messages from previous steps
        baseMessages = messages
      )
      // Execute the sub-tasks (potentially in parallel if they contain alternatives)
      // and collect their results into subAggregate
      val subAggregate = StringBuilder()
      runAll(subTaskFunctions, subAggregate) // Use runAll to handle potential parallelism within the item
      // Format and append the result of this step
      aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
      // Add the response of this step to the cumulative history for the next step
      messages.add(ApiModel.ChatMessage(ApiModel.Role.user, replaceFirst.toContentList()))
      messages.add(ApiModel.ChatMessage(ApiModel.Role.assistant, subAggregate.toString().toContentList()))
    }
    tabs.update()
  }
  
  open fun renderResponse(response: String, task: SessionTask) =
    """<div>${response.renderMarkdown()}</div>"""
  
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ChatSocketManager::class.java)
  }
}

