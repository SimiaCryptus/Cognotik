package com.simiacryptus.skyenet.webui.chat

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.general.renderMarkdown
import com.simiacryptus.skyenet.apps.parse.ParsingModel
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.util.FixedConcurrencyProcessor
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
import com.simiacryptus.skyenet.webui.session.getChildClient
import java.util.concurrent.ConcurrentHashMap

open class ChatSocketManager(
  session: Session,
  val model: ChatModel,
  val parsingModel: ChatModel,
  val userInterfacePrompt: String,
  open val initialAssistantPrompt: String = "",
  open val systemPrompt: String,
  val api: ChatClient,
  val temperature: Double = 0.3,
  applicationClass: Class<out ApplicationServer>,
  val storage: StorageInterface?,
) : SocketManagerBase(session, storage, owner = null, applicationClass = applicationClass) {
  // Store all identified topics across conversations
  protected val aggregateTopics = ConcurrentHashMap<String, MutableSet<String>>()
  
  init {
    if (userInterfacePrompt.isNotBlank()) {
      send("""aaa,<div class="initial-prompt">${MarkdownUtil.renderMarkdown(userInterfacePrompt)}</div>""")
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
  
  @Synchronized
  override fun onRun(userMessage: String, socket: ChatSocket) {
    // Apply topic-based autoexpansion to user message
    val expandedUserMessage = applyTopicAutoexpansion(userMessage)
    
    val task = newTask()
    val api = api.getChildClient(task)
    task.echo(renderResponse(expandedUserMessage, task))
    messages += ApiModel.ChatMessage(ApiModel.Role.user, expandedUserMessage.toContentList())
    try {
      val ui = ApplicationInterface(this)
      Retryable(ui, task) { it: StringBuilder ->
        val task = ui.newTask(false)
        pool.submit {
          task.add("")
          val responseString = respond(api, task, expandedUserMessage)
          messages.dropLastWhile { it.role == ApiModel.Role.assistant } // Clear previous assistant messages to avoid duplication if retrying
          messages += ApiModel.ChatMessage(ApiModel.Role.assistant, responseString.toContentList())
          task.complete()
        }
        task.placeholder
      }
    } catch (e: Exception) {
      log.info("Error in chat", e)
      task.error(ApplicationInterface(this), e)
    }
  }
  
  
  protected open fun respond(api: ChatClient, task: SessionTask, userMessage: String) = buildString {
    val fixedConcurrencyProcessor = FixedConcurrencyProcessor(this@ChatSocketManager.pool, 4)
    processMsgRecursive(api, userMessage, task, messages.toList()).map { function1 ->
      fixedConcurrencyProcessor.submit {
        function1(this)
      }
    }.forEach { it.get() }
  }.let { response ->
    try {
      val answer = ParsedActor(
        resultClass = Topics::class.java,
        prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
        model = model,
        temperature = temperature,
        name = "Topics",
        parsingModel = parsingModel,
      ).answer(
        listOf(
          response
        ), api = api
      )
      response + answer.obj.topics.let { topics ->
        if (topics?.isNotEmpty() == true) {
          // Add identified topics to the aggregate list
          topics.forEach { (topicType, entities) ->
            aggregateTopics.computeIfAbsent(topicType) { ConcurrentHashMap.newKeySet() }.addAll(entities)
          }
          val joinToString = topics.entries.joinToString("\n") { "* `{${it.key}}` - ${it.value.joinToString(", ") { "`$it`" }}" }
          task.complete(joinToString.renderMarkdown())
          "\n\n" + joinToString
        } else {
          ""
        }
      }
    } catch (e : Exception) {
      task.error(null, e)
      log.error("Error in topic extraction", e)
      response
    }
  }
  
  private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:[|,][^|,}{]+)+)}""")
  
  data class Topics(
    val topics: Map<String, List<String>>? = emptyMap()
  )
  
  /**
   * Applies topic-based autoexpansion to the user message
   * Looks for @topic syntax and replaces with expansion options
   */
  protected open fun applyTopicAutoexpansion(userMessage: String): String {
    val topicReferencePattern = Regex("""\{([^}|,]+)}""")
    return topicReferencePattern.replace(userMessage) { matchResult ->
      val topicType = matchResult.groupValues[1]
      val entities = aggregateTopics[topicType]
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
    baseMessages: List<ApiModel.ChatMessage>, // Pass the history without the current user turn
  ): List<(StringBuilder) -> Unit> {
    val match = expansionExpressionPattern.find(currentMessage)
    return if (match == null) listOf { aggregateResponse: StringBuilder ->
      task.add("")
      val finalMessages = baseMessages.dropLast(1) + ApiModel.ChatMessage(ApiModel.Role.user, currentMessage.toContentList())
      val response = respond(api, finalMessages)
      // Pass the substituted message content
      task.complete(renderResponse(response, task))
      aggregateResponse.append(response).append("\n\n") // Append response for aggregation
    } else {
      val tabs = TabbedDisplay(task, closable = false)
      match.groupValues[1].split('|',',').flatMap { option ->
        processMsgRecursive(
          api = api,
          currentMessage = currentMessage.replaceFirst(match.value, option),
          task = newTask(false, false).apply { tabs[option] = placeholder },
          baseMessages = baseMessages,
        )
      }
    }
  }
  
  protected open fun respond(
    api: ChatClient,
    chatMessages: List<ApiModel.ChatMessage>
  ): String {
    return (api.chat(
      ApiModel.ChatRequest(
        messages = chatMessages,
        temperature = temperature,
        model = model.modelName,
      ), model
    ).choices.first().message?.content.orEmpty())
  }
  
  open fun renderResponse(response: String, task: SessionTask) =
    """<div>${MarkdownUtil.renderMarkdown(response)}</div>"""
  
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ChatSocketManager::class.java)
  }
}