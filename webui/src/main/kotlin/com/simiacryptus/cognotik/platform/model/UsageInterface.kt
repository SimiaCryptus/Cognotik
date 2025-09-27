package com.simiacryptus.cognotik.platform.model

import com.google.common.util.concurrent.AtomicDouble
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.platform.Session
import java.util.concurrent.atomic.AtomicLong

/**
 * Interface for managing and tracking AI model usage across users and sessions.
 *
 * This interface provides methods to track, increment, and retrieve usage statistics
 * for AI models, including token counts and associated costs. Implementations of this
 * interface handle the persistence and retrieval of usage data.
 */

interface UsageInterface {
    /**
     * Retrieves a summary of AI model usage for a specific user.
     *
     * @param user The user whose usage summary is to be retrieved
     * @return A map where keys are model names and values are [ApiModel.Usage] objects
     *         containing aggregated token counts and costs for each model the user has used
     */

    fun getUserUsageSummary(user: User): Map<String, ApiModel.Usage>

    /**
     * Retrieves a summary of AI model usage for a specific session.
     *
     * @param session The session whose usage summary is to be retrieved
     * @return A map where keys are model names and values are [ApiModel.Usage] objects
     *         containing aggregated token counts and costs for each model used in the session
     */
    fun getSessionUsageSummary(session: Session): Map<String, ApiModel.Usage>

    /**
     * Records and increments usage statistics for a specific AI model invocation.
     *
     * This method should be called after each AI model API call to track resource consumption.
     *
     * @param session The session in which the usage occurred
     * @param user The user who initiated the AI model call
     * @param model The AI model that was used
     * @param tokens The usage details including prompt tokens, completion tokens, and cost
     */
    fun incrementUsage(session: Session, user: User, model: AIModel, tokens: ApiModel.Usage)

    /**
     * Clears all stored usage data.
     *
     * WARNING: This operation is destructive and will remove all historical usage data.
     * Use with caution, typically only for testing or system reset scenarios.
     */
    fun clear()

    /**
     * Represents a unique key for identifying usage records.
     *
     * This data class combines session, user, and model information to create
     * a composite key for usage tracking.
     *
     * @property session The session associated with the usage
     * @property user The user who initiated the usage (nullable for anonymous sessions)
     * @property model The AI model that was used
     */

    data class UsageKey(
        val session: Session,
        val user: User?,
        val model: AIModel,
    )

    /**
     * Thread-safe container for accumulating usage values.
     *
     * This class uses atomic operations to safely accumulate token counts and costs
     * across multiple threads. It's designed to handle concurrent updates without
     * requiring external synchronization.
     *
     * @property inputTokens Atomic counter for input/prompt tokens
     * @property outputTokens Atomic counter for output/completion tokens
     * @property cost Atomic accumulator for monetary cost
     */

    class UsageValues(
        val inputTokens: AtomicLong = AtomicLong(),
        val outputTokens: AtomicLong = AtomicLong(),
        val cost: AtomicDouble = AtomicDouble(),
    ) {
        /**
         * Atomically adds the given usage tokens and cost to the current values.
         *
         * This method is thread-safe and can be called concurrently without external
         * synchronization. It updates all three metrics (input tokens, output tokens,
         * and cost) atomically.
         *
         * @param tokens The usage object containing tokens and cost to add
         */
        fun addAndGet(tokens: ApiModel.Usage) {
            inputTokens.addAndGet(tokens.prompt_tokens)
            outputTokens.addAndGet(tokens.completion_tokens)
            cost.addAndGet(tokens.cost ?: 0.0)
        }
    }

}