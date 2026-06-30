package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.toContentList
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.OutputStream

/**
 * A ChatSocketManager that features:
 * 1. History auto-summarization when conversation gets too long
 * 2. Query elevation from fast model to smart model for complex queries
 */
open class SmartChatSocketManager(
    session: Session,
    useExpansionSyntax: Boolean = true,
    smartModel: ChatInterface,
    fastModel: ChatInterface,
    userInterfacePrompt: String = "",
    override val systemPrompt: String,
    temperature: Double = 0.3,
    applicationClass: Class<out ChatServer>,
    storage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
    override val fastTopicParsing: Boolean = true,
    retriable: Boolean = true,
    budget: Double,
    /**
   * Maximum number of tokens in conversation history before summarization is triggered
   */
  private val maxHistoryTokens: Int = 4000,
    /**
   * Target number of tokens after summarization
   */
  private val targetSummaryTokens: Int = 1000,
    /**
   * Number of recent messages to preserve without summarization
   */
  private val preserveRecentMessages: Int = 4,
    owner: User,
  ) : ChatSocketManager(
  session = session,
  useExpansionSyntax = useExpansionSyntax,
  smartModel = smartModel,
  fastModel = fastModel,
  userInterfacePrompt = userInterfacePrompt,
  systemPrompt = systemPrompt,
  temperature = temperature,
  applicationClass = applicationClass,
  storage = storage,
  fastTopicParsing = fastTopicParsing,
  retriable = retriable,
  budget = budget,
  owner = owner,
) {

  private var conversationSummary: String? = null
  private val summaryLock = Any()

  data class QueryElevationDecision(
    @Description("Indicates whether the query should be elevated to the smart model, or left on the fast model")
    val shouldElevate: Boolean = false,
    @Description("Reason for the elevation decision")
    val reason: String = ""
  )

  override fun respond(
    task: SessionTask,
    userMessage: String,
    currentChatMessages: List<ModelSchema.ChatMessage>,
    transcriptStream: OutputStream?
  ): String {
    val fast = fastModel.getChildClient(task)
    val smart = smartModel.getChildClient(task)
    // Check if we need to summarize history
    val messagesForChat = maybeCompactHistory(currentChatMessages, task)

    // First, use fast model to determine if we should elevate to smart model
    val shouldElevate = checkQueryElevation(
      userMessage,
      messagesForChat,
      fast
    )

    val modelToUse = if (shouldElevate) {
      log.info("Elevating query to smart model: $userMessage")
      task.add("<div class='elevation-notice'>🧠 <em>Using advanced model for this query</em></div>")
      smart
    } else {
      fast
    }

    return buildString {
      val finalMessages = messagesForChat + ModelSchema.ChatMessage(
        ModelSchema.Role.user,
        userMessage.toContentList()
      )

      task.add("")
      try {
        val chatResponse = modelToUse.chat(
          ChatRequest(
            model = modelToUse.model.modelId,
            messages = finalMessages,
            temperature = temperature,
            audio = modelToUse.audio,
          )
        )
        val choices = chatResponse.choices
        var responseText = choices.firstOrNull()?.message?.content.orEmpty()

        choices.forEach { choice ->
          choice.message?.image_data?.let {
            val imageMimeType = choice.message?.image_mime_type ?: "image/png"
            val (link, file) = task.createFile(
              java.util.UUID.randomUUID().toString() + when (imageMimeType) {
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

        append(responseText)
        task.complete(renderResponse(responseText, task))

        // Write to transcript
        transcriptStream?.write("## Assistant\n$responseText\n\n".transcriptFilter().toByteArray())
        transcriptStream?.flush()

      } catch (e: Exception) {
        log.error("Error in API call", e)
        val errorMsg = "Error: ${e.message}"
        append(errorMsg)
        task.error(e)
      }
    }.let { response ->
      // Extract topics as in parent class
      try {
        val answer = extractTopicsInternal(response, smart)
        val topicsText = try {
          answer.topics?.let { topics ->
            if (topics.isNotEmpty()) {
              val joinToString = topics.entries.joinToString("\n") {
                "* `{${it.key}}` - ${it.value.joinToString(", ") { "`$it`" }}"
              }
              task.complete(joinToString.renderMarkdown(), additionalClasses = "topics")
              "\n\n$joinToString"
            } else ""
          } ?: ""
        } catch (e: Exception) {
          task.error(e)
          log.error("Error in topic extraction", e)
          ""
        }
        response + topicsText
      } catch (e: Exception) {
        log.error("Error in topic extraction", e)
        response
      }
    }
  }

  private fun extractTopicsInternal(response: String, model: ChatInterface): Topics {
    val topicsParsedActor = ParsedAgent(
      resultClass = Topics::class.java,
      prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
      model = model,
      temperature = temperature,
      name = "Topics",
      parsingModel = fastModel,
    )
    return if (fastTopicParsing) {
      topicsParsedActor.getParser().apply(response)
    } else {
      topicsParsedActor.answer(listOf(response)).obj
    }
  }

  /**
   * Checks if the query should be elevated to the smart model
   */
  private fun checkQueryElevation(
    userMessage: String,
    currentMessages: List<ModelSchema.ChatMessage>,
    model: ChatInterface
  ) = try {
    val decision = ParsedAgent(
      resultClass = QueryElevationDecision::class.java,
      /* Note: this prompt is ignored due to getParser().apply */
      prompt = """
                Analyze the following user query and conversation context to determine if it requires advanced reasoning capabilities.
                
                Criteria for elevation to advanced model:
                - Complex multi-step reasoning or analysis
                - Code generation or debugging
                - Mathematical or scientific calculations
                - Creative writing requiring nuanced understanding
                - Questions requiring synthesis of multiple concepts
                - Ambiguous queries requiring careful interpretation
                
                Simple queries that DON'T need elevation:
                - Factual lookups
                - Simple clarifications
                - Greetings or casual conversation
                - Direct, straightforward questions
                
                User Query: $userMessage
                
                Recent conversation context (last few messages):
                ${
        currentMessages.takeLast(4)
          .joinToString("\n") { "${it.role}: ${it.content?.firstOrNull()?.text?.take(200) ?: ""}" }
      }
            """.trimIndent(),
      model = model,
      temperature = 0.1,
      name = "QueryElevation",
      parsingModel = model,
    ).getParser().apply(userMessage)
    log.debug("Elevation decision: shouldElevate=${decision.shouldElevate}, reason=${decision.reason}")
    decision.shouldElevate
  } catch (e: Exception) {
    log.warn("Error checking query elevation, defaulting to fast model", e)
    false
  }

  /**
   * Compacts conversation history by summarizing older messages if needed
   */
  private fun maybeCompactHistory(
    messages: List<ModelSchema.ChatMessage>,
    task: SessionTask
  ): List<ModelSchema.ChatMessage> {
    val estimatedTokens = estimateTokenCount(messages)

    if (estimatedTokens <= maxHistoryTokens) {
      return messages
    }

    log.info("Compacting history: estimated $estimatedTokens tokens exceeds max $maxHistoryTokens")

    synchronized(summaryLock) {
      // Separate system message from conversation
      val systemMessage = messages.firstOrNull { it.role == ModelSchema.Role.system }
      val conversationMessages = messages.filter { it.role != ModelSchema.Role.system }

      // Preserve recent messages
      val recentMessages = conversationMessages.takeLast(preserveRecentMessages)
      val messagesToSummarize = conversationMessages.dropLast(preserveRecentMessages)

      if (messagesToSummarize.isEmpty()) {
        return messages
      }

      // Generate summary of older messages
      val newSummary = generateSummary(messagesToSummarize, task)
      conversationSummary = if (conversationSummary != null) {
        // Combine with existing summary
        "$conversationSummary\n\nAdditional context:\n$newSummary"
      } else {
        newSummary
      }

      // Build new message list with summary
      val summaryMessage = ModelSchema.ChatMessage(
        ModelSchema.Role.system,
        """
                    ${systemMessage?.content?.firstOrNull()?.text ?: systemPrompt}
                    
                    Previous conversation summary:
                    $conversationSummary
                """.trimIndent().toContentList()
      )

      task.add("<div class='summary-notice'>📝 <em>Conversation history summarized</em></div>")

      return listOf(summaryMessage) + recentMessages
    }
  }

  /**
   * Generates a summary of the given messages
   */
  private fun generateSummary(
    messages: List<ModelSchema.ChatMessage>,
    task: SessionTask
  ): String {
    val conversationText = messages.joinToString("\n\n") { msg ->
      "${msg.role}: ${msg.content?.firstOrNull()?.text ?: ""}"
    }

    val summaryPrompt = """
            Summarize the following conversation, preserving:
            - Key topics discussed
            - Important decisions or conclusions
            - Any specific facts, numbers, or code mentioned
            - User preferences or requirements stated
            
            Keep the summary concise but comprehensive (target: ~$targetSummaryTokens tokens).
            
            Conversation:
            $conversationText
        """.trimIndent()

    return try {
      val summaryMessages = listOf(
        ModelSchema.ChatMessage(
          ModelSchema.Role.system,
          "You are a helpful assistant that creates concise conversation summaries.".toContentList()
        ),
        ModelSchema.ChatMessage(ModelSchema.Role.user, summaryPrompt.toContentList())
      )

      val childClient = fastModel.getChildClient(task)
      val response = childClient.chat(
        ChatRequest(
          model = childClient.model.modelId,
          messages = summaryMessages,
          temperature = temperature,
          audio = childClient.audio,
        )
      )
      response.choices.firstOrNull()?.message?.content ?: "Unable to generate summary"
    } catch (e: Exception) {
      log.error("Error generating summary", e)
      "Previous conversation covered: ${messages.size} messages"
    }
  }

  /**
   * Estimates token count for messages (rough approximation: ~4 chars per token)
   */
  private fun estimateTokenCount(messages: List<ModelSchema.ChatMessage>): Int {
    return messages.sumOf { msg ->
      (msg.content?.sumOf { it.text?.length ?: 0 } ?: 0) / 4
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(SmartChatSocketManager::class.java)
  }
}