package com.simiacryptus.skyenet.webui.chat

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
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
    val renderRequest = renderResponse(userMessage, task)
    task.echo(renderRequest)
    messages += ApiModel.ChatMessage(ApiModel.Role.user, userMessage.toContentList())
    val messagesCopy = messages.toList()
    try {
      val ui = ApplicationInterface(this)
      val process = { it: StringBuilder ->
        val response = respond(api, messagesCopy)
        messages.dropLastWhile { it.role == ApiModel.Role.assistant }
        messages += ApiModel.ChatMessage(ApiModel.Role.assistant, response.toContentList())
        val renderResponse = renderResponse(response, task)
        onResponse(renderResponse, renderRequest)
        renderResponse
      }
      Retryable(ui, task, process)
    } catch (e: Exception) {
      log.info("Error in chat", e)
      task.error(ApplicationInterface(this), e)
    }
  }

  protected open fun respond(
    api: ChatClient,
    messagesCopy: List<ApiModel.ChatMessage>
  ): String {
    return (api.chat(
      ApiModel.ChatRequest(
        messages = messagesCopy,
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