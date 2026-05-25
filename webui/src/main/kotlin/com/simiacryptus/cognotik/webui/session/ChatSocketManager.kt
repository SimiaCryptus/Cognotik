package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory.getLogger
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference


open class ChatSocketManager(
  session: Session,
  var useExpansionSyntax: Boolean = true,
  var smartModel: ChatInterface,
  var fastModel: ChatInterface,
  val userInterfacePrompt: String = (if (!useExpansionSyntax) "" else """
    <div class="expandable-guide">
      <div class="expandable-header">
        <strong>Query Expansion Syntax Guide</strong>
        <span class="expand-icon">▼</span>
      </div>
      <div class="expandable-content">
        <p>You can use the following syntaxes in your messages to automatically expand your queries:</p>

        <h4 class="expandable-section-title">Parallel Expansion</h4>
        <p class="expandable-description">Use <code>@[option1|option2|option3]</code> to run the same prompt with each option in parallel.</p>
        <p class="expandable-example"><em>Example:</em> <code>Tell me a joke about @[cats|dogs|birds]</code></p>

        <h4 class="expandable-section-title">Sequence Expansion</h4>
        <p class="expandable-description">Use <code>@{step1 -> step2 -> step3}</code> to run a sequence of prompts, where the output of each feeds into the next.</p>
        <p class="expandable-example"><em>Example:</em> <code>Summarize this text, then @{translate to French -> translate to German}</code></p>

        <h4 class="expandable-section-title">Range Expansion</h4>
        <p class="expandable-description">Use <code>@(start..end:step)</code> to iterate over a range of numbers.</p>
        <p class="expandable-example"><em>Example:</em> <code>Project an alternate history where Rome never fell. Tell what happened in @(1000..1500:100)</code></p>

        <h4 class="expandable-section-title">Topic Reference Expansion</h4>
        <p class="expandable-description">Use <code>@topicType</code> to refer to previously identified topics.</p>
        <p class="expandable-example"><em>Example:</em> <code>Tell me about @Person</code></p>

        <p class="expandable-footer">You can combine these syntaxes for more complex expansions.</p>
      </div>
    </div>
    """.trimIndent()),
  open val systemPrompt: String,
  var temperature: Double = 0.3,
  applicationClass: Class<out ChatServer>,
  val storage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
  open val fastTopicParsing: Boolean = true,
  val retriable: Boolean = true,
  val budget: Double,
  owner: User,
) : SocketManager(session, storage, owner = owner, applicationClass = applicationClass) {

  private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
  private val messagesLock = Any()

  init {
    if (userInterfacePrompt.isNotBlank()) {
      newTask().complete(userInterfacePrompt)
    }
  }

  open val sysMessage: ModelSchema.ChatMessage
    get() = ModelSchema.ChatMessage(ModelSchema.Role.system, systemPrompt.toContentList())

  protected val chatMessages = mutableListOf<ModelSchema.ChatMessage>()

  fun SessionTask.transcript(name: String = this.javaClass.simpleName): FileOutputStream? {
    val relativePath = "transcript/${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis())}.md"
    val (link, file) = Pair(linkTo(relativePath), resolveUserFile(relativePath))
    val markdownTranscript = file?.outputStream()
    complete(
      "Writing $name to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
        link.removeSuffix(
          ".md"
        )
      }.pdf' target='_blank'>pdf</a>",
      additionalClasses = "verbose"
    )
    return markdownTranscript
  }

  val markdownTranscript by lazy { newTask().transcript() }

  override fun onRun(userMessage: String, socket: ChatSocket) {

    val expandedUserMessage = expandTopics(userMessage)
    markdownTranscript?.write("## User\n$expandedUserMessage\n\n".toByteArray())
    val task = newTask()
    task.echo(expandedUserMessage.renderMarkdown(true))

    synchronized(messagesLock) {
      chatMessages += ModelSchema.ChatMessage(ModelSchema.Role.user, expandedUserMessage.toContentList())
    }

    try {
      if (!retriable) {
        task.add("")
        val responseString = respond(task, expandedUserMessage, chatMessages(), markdownTranscript)
        synchronized(messagesLock) {
          if (chatMessages.lastOrNull()?.role == ModelSchema.Role.assistant) {
            chatMessages.removeAt(chatMessages.size - 1)
          }
          chatMessages += ModelSchema.ChatMessage(ModelSchema.Role.assistant, responseString.toContentList())
        }
        task.complete()
      } else {
        Retryable(task, process = { task: SessionTask ->
          chatMessages.takeLastWhile { it.role == ModelSchema.Role.assistant }
            .forEach { chatMessages.remove(it) }
          val currentChatMessages = chatMessages()
          innerRun(task, expandedUserMessage, currentChatMessages, markdownTranscript)
        }.async(task.ui, pool))
      }
    } catch (e: Exception) {
      log.info("Error in chat", e)
      task.error(e)
    }
  }

  private fun innerRun(
    task: SessionTask,
    expandedUserMessage: String,
    currentChatMessages: List<ModelSchema.ChatMessage>,
    transcriptStream: OutputStream?
  ) {
    try {
      task.add("")
      val responseString = respond(task, expandedUserMessage, currentChatMessages, transcriptStream)
      synchronized(messagesLock) {
        if (chatMessages.lastOrNull()?.role == ModelSchema.Role.assistant) {
          chatMessages.removeAt(chatMessages.size - 1)
        }
        chatMessages += ModelSchema.ChatMessage(
          ModelSchema.Role.assistant,
          responseString.toContentList()
        )
      }
      task.complete()
    } catch (e: Throwable) {
      log.warn("Exception occurred while processing chat message", e)
    }
  }

  private val idSubPattern =
    """[^|\n,/\\;}\]\[><()@]+""" // Matches any valid identifier character except for special characters used in the expansion syntax
  private val expansionExpressionPattern =
    Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""") // Matches @[option1|option2|option3]

  private val sequenceExpansionPattern =
    Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""") // Matches @{item1 -> item2 -> item3}

  private val rangeExpansionPattern =
    Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""") // Matches @(start..end:step) or @(start to end by step)

  protected open fun respond(
    task: SessionTask,
    userMessage: String,
    currentChatMessages: List<ModelSchema.ChatMessage>,
    transcriptStream: OutputStream? = null
  ): String {
    val model = smartModel.getChildClient(task)
    return buildString {
      runAll(
        processMsgRecursive(
          userMessage,
          task,
          currentChatMessages,
          transcriptStream,
          model
        ), this
      )
    }.let { response ->
      // Write assistant response to transcript
      transcriptStream?.write("## Assistant\n$response\n\n".transcriptFilter().toByteArray())
      transcriptStream?.flush()
      response
    }
  }

  /**
   * Executes a list of functions, each appending to the target StringBuilder, potentially in parallel.
   */
  private fun runAll(function1s: List<(StringBuilder) -> Unit>, target: StringBuilder) {
    val fixedConcurrencyProcessor = FixedConcurrencyProcessor(pool, 4)
    function1s.map { function1 ->
      fixedConcurrencyProcessor.submit {
        function1(target)
      }
    }.forEach { it.get() }
  }

  protected open fun chatMessages(): List<ModelSchema.ChatMessage> = synchronized(messagesLock) {
    if (chatMessages.isEmpty() || chatMessages.first().role != ModelSchema.Role.system) {
      listOf(sysMessage) + chatMessages
    } else {
      chatMessages
    }
  }

  data class Topics(
    val topics: Map<String, List<String>>? = emptyMap()
  )

  protected open fun expandTopics(userMessage: String): String {
    // Matches both @TopicType and @{Topic Type With Spaces}
    val topicReferencePattern =
      Regex("""@\{([A-Z][a-zA-Z0-9_ ]+)\}|@([A-Z][a-zA-Z0-9_]*)""")
    return topicReferencePattern.replace(userMessage) { matchResult -> // Read access needs synchronization
      // Group 1 is for delimited format @{Topic Type}, Group 2 is for simple format @TopicType
      val topicType = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
      val topicList = aggregateTopics[topicType]
      val entities = synchronized(topicList ?: Any()) { // Synchronize on the list if it exists, or a dummy object
        topicList?.toList() // Create copy while holding lock
      }
      if (!entities.isNullOrEmpty()) { // Check if the copied list is not null or empty
        "@[${entities.joinToString("|")}]" // Use the copied list
      } else {
        matchResult.value
      }
    }
  }

  private fun processMsgRecursive(
    currentMessage: String,
    task: SessionTask,
    baseMessages: List<ModelSchema.ChatMessage>,
    transcriptStream: OutputStream? = null,
    model: ChatInterface
  ): List<(StringBuilder) -> Unit> {

    if (useExpansionSyntax) {
      val rangeMatch = rangeExpansionPattern.find(currentMessage)
      if (rangeMatch != null) {
        return expandRange(currentMessage, task, baseMessages, rangeMatch, transcriptStream)
      }

      val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
      if (sequenceMatch != null) {
        return listOf { finalAggregate: StringBuilder ->
          expandSequence(
            task,
            baseMessages,
            sequenceMatch.groupValues[1].split(Regex("""\s*->\s*""")),
            currentMessage,
            sequenceMatch.value,
            transcriptStream
          )
        }
      }

      val match = expansionExpressionPattern.find(currentMessage)
      if (match != null && match.groupValues[1].split('|', ',').size > 1) {
        return expandAlternatives(
          currentMessage,
          task,
          baseMessages,
          match,
          transcriptStream
        ) { msg, tsk, msgs ->
          processMsgRecursive(msg, tsk, msgs, transcriptStream, this@ChatSocketManager.smartModel)
        }
      }
    }

    return listOf { aggregateResponse: StringBuilder ->
      task.add("")

      val finalMessages =
        baseMessages + ModelSchema.ChatMessage(ModelSchema.Role.user, currentMessage.toContentList())
      val responseRef = AtomicReference<String>()
      try {
        val chatResponse = model.chat(
          ChatRequest(
            model = model.modelType.modelId,
            messages = finalMessages,
            temperature = model.temperature,
            audio = model.audio,
          )
        )
        val choices = chatResponse.choices
        var responseText = choices.firstOrNull()?.message?.content.orEmpty()
        choices.forEach { choice ->
          choice.message?.image_data?.let {
            val imageMimeType = choice.message?.image_mime_type ?: "image/png"
            val (link, file) = task.createFile(
              UUID.randomUUID().toString() + when (imageMimeType) {
                "image/png" -> ".png"
                "image/jpeg", "image/jpg" -> ".jpg"
                "image/gif" -> ".gif"
                else -> ".img"
              }
            )
            file?.writeBytes(it)
            val imageLink =
              """<a href='$link' target='_blank'><img src="$link" alt="Image" style="max-width: 300px;" ></a>"""
            responseText += "\n\n" + imageLink
          }
        }
        responseRef.set(responseText)
      } catch (e: Exception) {
        log.error("Error in API call", e)
        responseRef.set("Error: ${e.message}")
      }

      val response = responseRef.get() ?: "No response received"
      task.complete(renderResponse(response, task))
      aggregateResponse.append(response).append("\n\n")
      // Write intermediate responses to transcript if in expansion mode
      if (useExpansionSyntax && transcriptStream != null) {
        transcriptStream.write("### Expansion Result\n$response\n\n".transcriptFilter().toByteArray())
        transcriptStream.flush()
      }

    }
  }

  /**
   * Expands range expressions in the format [start...end:step]
   * Creates a sequence of numbers from start to end with the given step (default 1)
   */
  private fun expandRange(
    currentMessage: String,
    task: SessionTask,
    baseMessages: List<ModelSchema.ChatMessage>,
    rangeMatch: MatchResult,
    transcriptStream: OutputStream? = null
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
      transcriptStream
    )
  }

  /**
   * Expands alternative expressions in the format {option1|option2|option3}
   * Each option is processed in parallel
   */
  private fun expandAlternatives(
    currentMessage: String,
    task: SessionTask,
    baseMessages: List<ModelSchema.ChatMessage>,
    match: MatchResult,
    transcriptStream: OutputStream? = null,
    recursiveFn: (String, SessionTask, List<ModelSchema.ChatMessage>) -> List<(StringBuilder) -> Unit>
  ): List<(StringBuilder) -> Unit> {
    val tabs = TabbedDisplay(task, closable = useExpansionSyntax)
    return match.groupValues[1].split('|', ',').flatMap { option ->
      recursiveFn(
        currentMessage.replaceFirst(match.value, option),
        this.newTask(cancelable = false, root = false).apply { tabs[option] = placeholder },
        baseMessages.filter { it.content?.any { it.text?.contains(match.value) == true } != true }
      )
    }.apply {
      tabs.update()
    }
  }

  private fun expandSequence(
    task: SessionTask,
    baseMessages: List<ModelSchema.ChatMessage>,
    items: List<String>,
    currentMessage: String,
    expression: String,
    transcriptStream: OutputStream? = null
  ) {
    val aggregatedResponse = StringBuilder()
    val tabs = TabbedDisplay(task, closable = useExpansionSyntax)
    val messages = baseMessages.dropLast(1).toMutableList()
    for (item in items) {
      val newMessage = currentMessage.replaceFirst(expression, item)
      val subTaskFunctions = processMsgRecursive(
        currentMessage = newMessage,
        task = this.newTask(cancelable = false, root = false).apply { tabs[item] = placeholder },
        baseMessages = messages.filter { it.content?.any { it.text?.contains(expression) == true } != true },
        transcriptStream = transcriptStream,
        model = this@ChatSocketManager.smartModel
      )
      val subAggregate = StringBuilder()
      runAll(subTaskFunctions, subAggregate)
      aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
      messages.add(ModelSchema.ChatMessage(ModelSchema.Role.user, newMessage.toContentList()))
      messages.add(ModelSchema.ChatMessage(ModelSchema.Role.assistant, subAggregate.toString().toContentList()))
    }
    tabs.update()
  }

  open fun renderResponse(response: String, task: SessionTask) = """<div>${response.renderMarkdown(true)}</div>"""

  companion object {
    private val log = getLogger(ChatSocketManager::class.java)
  }
}

fun String.transcriptFilter() = this.let {
  Regex("""(href=|src=['"])?fileIndex/[A-Za-z0-9\-_]+/""").replace(it) { matchResult ->
    matchResult.groupValues[1]
  }
}
