package com.simiacryptus.cognotik.core.platform.model

import com.google.common.util.concurrent.AtomicDouble
import com.simiacryptus.cognotik.core.platform.ApplicationServices
import com.simiacryptus.cognotik.core.platform.Session
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModel
import java.util.concurrent.atomic.AtomicLong

interface UsageInterface {
  fun incrementUsage(session: Session, user: User?, model: OpenAIModel, tokens: ApiModel.Usage) = incrementUsage(
    session, when (user) {
      null -> null
      else -> {
        val userSettings = ApplicationServices.userSettingsManager.getUserSettings(user)
        userSettings.apiKeys[if (model is ChatModel) {
          model.provider
        } else {
          APIProvider.Companion.OpenAI
        }]
      }
    }, model, tokens
  )

  fun incrementUsage(session: Session, apiKey: String?, model: OpenAIModel, tokens: ApiModel.Usage)

  fun getUserUsageSummary(user: User): Map<OpenAIModel, ApiModel.Usage> = getUserUsageSummary(
    ApplicationServices.userSettingsManager.getUserSettings(user).apiKeys[APIProvider.Companion.OpenAI]!! // TODO: Support other providers
  )

  fun getUserUsageSummary(apiKey: String): Map<OpenAIModel, ApiModel.Usage>

  fun getSessionUsageSummary(session: Session): Map<OpenAIModel, ApiModel.Usage>
  fun clear()

  data class UsageKey(
    val session: Session,
    val apiKey: String?,
    val model: OpenAIModel,
  )

  class UsageValues(
    val inputTokens: AtomicLong = AtomicLong(),
    val outputTokens: AtomicLong = AtomicLong(),
    val cost: AtomicDouble = AtomicDouble(),
  ) {
    fun addAndGet(tokens: ApiModel.Usage) {
      inputTokens.addAndGet(tokens.prompt_tokens)
      outputTokens.addAndGet(tokens.completion_tokens)
      cost.addAndGet(tokens.cost ?: 0.0)
    }

    fun toUsage() = ApiModel.Usage(
      prompt_tokens = inputTokens.get(),
      completion_tokens = outputTokens.get(),
      cost = cost.get()
    )
  }

  class UsageCounters(
    val tokensPerModel: java.util.HashMap<UsageKey, UsageValues> = HashMap(),
  )
}