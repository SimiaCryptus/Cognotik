package com.simiacryptus.cognotik.chat.model

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
class ChatModel(
  val name: String = "",
  modelId: String = name,
  maxTotalTokens: Int = -1,
  maxOutTokens: Int = maxTotalTokens,
  provider: APIProvider? = null,
  val inputTokenPricePerK: Double = 0.0,
  val outputTokenPricePerK: Double = inputTokenPricePerK,
  val supportsTemperature: Boolean = true,
  val supportsReasoning: Boolean = false,
  val deprecated: Boolean = false,
  val inputModalities: Set<ChatMessageModality>,
  val outputModalities: Set<ChatMessageModality>,
) : LLMModel(
  modelId = modelId,
  maxTotalTokens = maxTotalTokens,
  maxOutTokens = maxOutTokens,
  provider = provider,
) {

  override fun toString() = modelId

  fun pricing(usage: Usage): Double {
    val promptCost = usage.prompt_tokens * inputTokenPricePerK
    val completionCost = usage.completion_tokens * outputTokenPricePerK
    val estimatedUnaccountedCost =
      (usage.total_tokens - (usage.prompt_tokens + usage.completion_tokens)) * ((inputTokenPricePerK + outputTokenPricePerK) / 2)
    return (promptCost + completionCost + estimatedUnaccountedCost) / 1000.0
  }

  fun instance(
    key: SecureString,
    base: String = provider?.base!!,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    workPool: ExecutorService = Executors.newFixedThreadPool(4),
    temperature: Double = 0.1,
    scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(
      Executors.newScheduledThreadPool(
        1
      )
    ),
    session: Session,
    user: User,
  ): ChatInterface = ChatInterface(
    logStreams = logStreams,
    key = key,
    base = base,
    logLevel = logLevel,
    temperature = temperature,
    provider = provider!!,
    modelType = this,
    workPool = workPool,
    scheduledPool = scheduledPool,
    session = session,
    onUsage = { model, usage -> ON_USAGE(model, usage, user, session) },
  )

  companion object {
    var ON_USAGE : (LLMModel, Usage, User, Session) -> Unit = { model, usage, user, session ->
      log.info("Model: ${model.modelId}, Prompt Tokens: ${usage.prompt_tokens}, Completion Tokens: ${usage.completion_tokens}, Total Tokens: ${usage.total_tokens}, User: ${user.id}")
    }
    val NULL: ChatModel = ChatModel(
      name = "NULL",
      modelId = "NULL",
      outputTokenPricePerK = 0.0,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
    val log = getLogger(ChatModel::class.java)
  }
}

