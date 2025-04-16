package com.simiacryptus.skyenet.webui.chat

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.general.renderMarkdown
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
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.ConcurrentHashMap

open class ChatSocketManager(
  session: Session,
  var model: ChatModel,
  var parsingModel: ChatModel,
  val userInterfacePrompt: String,
  open val initialAssistantPrompt: String = "",
  open val systemPrompt: String,
  val api: ChatClient,
  var temperature: Double = 0.3,
  applicationClass: Class<out ApplicationServer>,
  val storage: StorageInterface?,
  open val fastTopicParsing : Boolean = true,
) : SocketManagerBase(session, storage, owner = null, applicationClass = applicationClass) {
  
  private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
  
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
  val ui = ApplicationInterface(this)
  
  @Synchronized
  override fun onRun(userMessage: String, socket: ChatSocket) {
    // Apply topic-based autoexpansion to user message
    val expandedUserMessage = applyTopicAutoexpansion(userMessage)
    
    val task = newTask()
    val api = api.getChildClient(task)
    task.echo(renderResponse(expandedUserMessage, task))
    messages += ApiModel.ChatMessage(ApiModel.Role.user, expandedUserMessage.toContentList())
    try {
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
  
  // Pattern for parallel expansion: {option1|option2}
  private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:[|,][^|\n,}{]+)+)}""")
  
  // Pattern for ordered sequence expansion: <item1;item2;item3>
  private val sequenceExpansionPattern = Regex("""<([^;><\n]+(?:;[^;><\n]+)+)>""")
  
  protected open fun respond(api: ChatClient, task: SessionTask, userMessage: String) = buildString {
    val fixedConcurrencyProcessor = FixedConcurrencyProcessor(this@ChatSocketManager.pool, 4)
    processMsgRecursive(api, userMessage, task, chatMessages()).map { function1 ->
      fixedConcurrencyProcessor.submit {
        function1(this)
      }
    }.forEach { it.get() }
  }.let { response ->
    try {
      val topicsParsedActor = ParsedActor(
        resultClass = Topics::class.java,
        prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
        model = model,
        temperature = temperature,
        name = "Topics",
        parsingModel = parsingModel,
      )
      val answer = if(fastTopicParsing) {
        topicsParsedActor.getParser(api).apply(response)
      } else {
        topicsParsedActor.answer(listOf(response), api).obj
      }
      response + try {
        answer.topics.let { topics ->
          if (topics?.isNotEmpty() == true) {
            // Add identified topics to the aggregate list
            topics.forEach { (topicType, entities) ->
              aggregateTopics.computeIfAbsent(topicType) { mutableListOf() }.addAll(entities)
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
    } catch (e : Exception) {
      task.error(null, e)
      log.error("Error in topic extraction", e)
      response
    }
  }
  
  protected open fun chatMessages() = messages.let {
    listOf(ApiModel.ChatMessage(ApiModel.Role.system, systemPrompt.toContentList())) + it.toImmutableList().drop(1)
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
    baseMessages: List<ApiModel.ChatMessage>,
  ): List<(StringBuilder) -> Unit> {
    // 1. Handle sequence expansion <a;b;c> with higher precedence
    val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
    if (sequenceMatch != null) {
      val items = sequenceMatch.groupValues[1].split(';')
      // For ordered expansion, we want to process each item in sequence, passing the response of the previous as input to the next
      // We'll accumulate responses in a StringBuilder and pass them along
      return listOf { aggregateResponse: StringBuilder ->
        var input = currentMessage
        var prevResponse: String? = null
        val sequenceTab = TabbedDisplay(task, closable = false)
        for ((i, item) in items.withIndex()) {
          val replaced = input.replaceFirst(sequenceMatch.value, item)
          val subTask = ui.newTask(false).apply { sequenceTab[item] = placeholder }
          val subResponses = processMsgRecursive(api, replaced, subTask, baseMessages)
          val subAggregate = StringBuilder()
          subResponses.forEach { it(subAggregate) }
          prevResponse = subAggregate.toString()
          // For the next iteration, append the previous response so that subsequent items
          // receive the cumulative output as part of the prompt.
          input = replaced + "\n" + (prevResponse ?: "")
          aggregateResponse.append("[$item]\n").append(prevResponse).append("\n")
        }
        sequenceTab.update()
      }
    } else {
      // 2. Handle parallel expansion {a|b|c}
      val match = expansionExpressionPattern.find(currentMessage)
      if (match != null) {
        val tabs = TabbedDisplay(task, closable = false)
        return match.groupValues[1].split('|', ',').flatMap { option ->
          processMsgRecursive(
            api = api,
            currentMessage = currentMessage.replaceFirst(match.value, option),
            task = ui.newTask(false).apply { tabs[option] = placeholder },
            baseMessages = baseMessages,
          )
        }.apply {
          tabs.update()
        }
      } else {
        return listOf { aggregateResponse: StringBuilder ->
          task.add("")
          val finalMessages = baseMessages.dropLast(1) + ApiModel.ChatMessage(ApiModel.Role.user, currentMessage.toContentList())
          val chatResponse = api.chat(
            ApiModel.ChatRequest(
              messages = finalMessages,
              temperature = temperature,
              model = model.modelName,
            ), model
          )
          val response = (chatResponse.choices.first().message?.content.orEmpty())
          task.complete(renderResponse(response, task))
          aggregateResponse.append(response).append("\n\n") // Append response for aggregation
        }
      }
    }
  }
  
  open fun renderResponse(response: String, task: SessionTask) =
    """<div>${MarkdownUtil.renderMarkdown(response)}</div>"""
  
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ChatSocketManager::class.java)
  }
}