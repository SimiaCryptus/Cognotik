package com.simiacryptus.cognotik.platform.model

import com.google.common.util.concurrent.AtomicDouble
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.AIModel
import com.simiacryptus.jopenai.models.chat.LLMModel
import java.util.concurrent.atomic.AtomicLong

interface UsageInterface {

    fun getUserUsageSummary(user: User): Map<String, ApiModel.Usage>
    fun getSessionUsageSummary(session: Session): Map<String, ApiModel.Usage>
    fun incrementUsage(session: Session, user: User, model: LLMModel, tokens: ApiModel.Usage)
    fun clear()

    data class UsageKey(
        val session: Session,
        val user: User?,
        val model: AIModel,
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
    }
}