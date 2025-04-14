package com.simiacryptus.skyenet.webui.chat

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.util.FixedConcurrencyProcessor
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
import com.simiacryptus.skyenet.webui.session.getChildClient

open class ChatSocketManager(
  session: Session,
  val model: ChatModel,
  val userInterfacePrompt: String,
  open val initialAssistantPrompt: String = "",
  open val systemPrompt: String,
  val api: ChatClient,
  val temperature: Double = 0.3,
  applicationClass: Class<out ApplicationServer>,
  val storage: StorageInterface?,
) : SocketManagerBase(session, storage, owner = null, applicationClass = applicationClass) {
  
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
    val task = newTask()
    val api = api.getChildClient(task)
    task.echo(renderResponse(userMessage, task))
    messages += ApiModel.ChatMessage(ApiModel.Role.user, userMessage.toContentList())
    try {
      val ui = ApplicationInterface(this)
      Retryable(ui, task) { it: StringBuilder ->
        val task = ui.newTask(false)
        pool.submit {
          task.add("")
          val function1s = processMsgRecursive(api, userMessage, task, messages.toList())
          val aggregateResponse = StringBuilder()
          val fixedConcurrencyProcessor = FixedConcurrencyProcessor(this.pool, 4)
          function1s.forEach { function1 ->
            fixedConcurrencyProcessor.submit {
              function1(aggregateResponse)
            }
          }
          messages.dropLastWhile { it.role == ApiModel.Role.assistant } // Clear previous assistant messages to avoid duplication if retrying
          messages += ApiModel.ChatMessage(ApiModel.Role.assistant, aggregateResponse.toString().toContentList())
          task.complete()
        }
        task.placeholder
      }
    } catch (e: Exception) {
      log.info("Error in chat", e)
      task.error(ApplicationInterface(this), e)
    }
  }
  
  private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{]+)+)}""")
  
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
      onResponse(response, currentMessage) // Pass the substituted message content
      task.complete(renderResponse(response, task))
      aggregateResponse.append(response).append("\n\n") // Append response for aggregation
    } else {
      val tabs = TabbedDisplay(task)
      match.groupValues[1].split('|').flatMap { option ->
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
  
  open fun onResponse(response: String, responseContents: String) {}
  
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ChatSocketManager::class.java)
  }
}