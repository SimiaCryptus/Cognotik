package com.simiacryptus.cognotik.platform.model

import com.google.common.util.concurrent.AtomicDouble
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import java.time.LocalDate
import java.time.Instant
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
     * Retrieves a summary of AI model usage for a specific user within a required date range.
     *
     * User-scoped queries are required to be date-bounded for performance and privacy
     * reasons. The range is inclusive of [from] and exclusive of [to].
     *
     * @param user The user whose usage summary is to be retrieved
     * @param from Inclusive start date (UTC day)
     * @param to   Exclusive end date (UTC day)
     * @return A map where keys are model names and values are [ModelSchema.Usage] objects
     *         containing aggregated token counts and costs for each model the user has used
     */

    fun getUserUsageSummary(user: User, from: LocalDate, to: LocalDate): Map<String, ModelSchema.Usage>

    /**
     * Retrieves a summary of AI model usage for a specific session.
     *
     * Session-scoped queries are not bounded by date.
     *
     * @param session The session whose usage summary is to be retrieved
     * @return A map where keys are model names and values are [ModelSchema.Usage] objects
     *         containing aggregated token counts and costs for each model used in the session
     */
    fun getSessionUsageSummary(session: Session): Map<String, ModelSchema.Usage>

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
    fun incrementUsage(session: Session, user: User, model: AIModel, tokens: ModelSchema.Usage)

    /**
     * Clears all stored usage data.
     *
     * WARNING: This operation is destructive and will remove all historical usage data.
     * Use with caution, typically only for testing or system reset scenarios.
     */
    fun clear()
    fun setParentSession(child: Session, parent: Session)
    fun getParentSession(child: Session): Session?

    /**
     * Returns the available budget (in cost units, e.g. USD) for a user.
     *
     * Available budget = sum(credits) - sum(cost-to-date).
     *
     * Implementations must provide real-time speed and accuracy. Typically backed
     * by a cached running total updated atomically on each [incrementUsage] and
     * [creditUser] call.
     *
     * @param user The user whose available budget is to be retrieved
     * @return The available budget as a Double; may be negative if the user is overdrawn
     */
    fun getAvailableBudget(user: User): Double

    /**
     * Adds credits to a user's budget with an optional comment and metadata.
     *
     * This is recorded as a ledger entry and atomically applied to the cached
     * available budget for the user.
     *
     * @param user The user receiving the credit
     * @param amount The amount to credit (positive to add, negative to debit/adjust)
     * @param comment Free-form comment describing the reason for the credit
     * @param metadata Optional structured metadata stored alongside the entry
     * @return The new available budget after applying the credit
     */
    fun creditUser(
        user: User,
        amount: Double,
        comment: String? = null,
        metadata: Map<String, String>? = null
    ): Double

    /**
     * Retrieves daily usage time-series for a specific user within a required date range.
     *
     * @param user The user whose daily usage is to be retrieved
     * @param from Inclusive start date (UTC day)
     * @param to   Exclusive end date (UTC day)
     * @return A list of [DailyUsage] entries, one per (day, model) with non-zero usage,
     *         ordered by ascending day
     */
    fun getUserDailyUsage(user: User, from: LocalDate, to: LocalDate): List<DailyUsage>

    /**
     * Represents a single day's usage for a (user, model) pair.
     */
    data class DailyUsage(
        val day: LocalDate,
        val model: String,
        val usage: ModelSchema.Usage
    )

    /**
     * Retrieves the credit ledger entries for a specific user within a required date range.
     *
     * @param user The user whose credit history is to be retrieved
     * @param from Inclusive start date (UTC day)
     * @param to   Exclusive end date (UTC day)
     * @return A list of [CreditEntry] records ordered by ascending datetime
     */
    fun getUserCredits(user: User, from: LocalDate, to: LocalDate): List<CreditEntry>

    /**
     * Represents a single credit ledger entry for a user.
     */
    data class CreditEntry(
        val datetime: Instant,
        val amount: Double,
        val comment: String?,
        val metadata: Map<String, String>?
    )


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
        fun addAndGet(tokens: ModelSchema.Usage) {
            inputTokens.addAndGet(tokens.prompt_tokens)
            outputTokens.addAndGet(tokens.completion_tokens)
            cost.addAndGet(tokens.cost ?: 0.0)
        }
    }

}