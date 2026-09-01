package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.AtomicDouble
import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import java.time.LocalDate
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

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
   * @return A map where keys are model names and values are [com.simiacryptus.cognotik.models.ModelSchema.Usage] objects
   *         containing aggregated token counts and costs for each model the user has used
   */

  fun getUserUsageSummary(user: User, from: LocalDate, to: LocalDate): Map<String, ModelSchema.Usage>

  /**
   * Retrieves a summary of AI model usage for a specific session.
   *
   * Session-scoped queries are not bounded by date.
   *
   * @param session The session whose usage summary is to be retrieved
   * @return A map where keys are model names and values are [com.simiacryptus.cognotik.models.ModelSchema.Usage] objects
   *         containing aggregated token counts and costs for each model used in the session
   */
  fun getSessionUsageSummary(session: Session): Map<String, ModelSchema.Usage>

  /**
   * Bulk variant of [getSessionUsageSummary] that fetches usage summaries for
   * multiple sessions in a single backend call.
   *
   * NOTE: Unlike [getSessionUsageSummary], this method intentionally returns
   * usage attributable directly to each session ID provided, without recursing
   * into descendant sessions. This is the form needed by the sessions listing
   * page and avoids quadratic behaviour when many sessions are displayed.
   *
   * The default implementation falls back to invoking [getSessionUsageSummary]
   * per session (which does include descendants), so existing implementations
   * remain source-compatible. DB-backed implementations should override this
   * for both efficiency and the documented non-recursive semantics.
   *
   * @param sessionIds The set of session IDs to summarize
   * @return A map from session ID to its per-model usage summary
   */
  fun getSessionUsageSummaryBulk(sessionIds: Collection<Session>): Map<Session, Map<String, ModelSchema.Usage>> {
    return sessionIds.associateWith { getSessionUsageSummary(it) }
  }

  /**
   * Aggregated single-row summary across all models for a session, suitable for
   * compact display in listing UIs.
   */
  data class SessionUsageTotals(
    val totalTokens: Long,
    val totalCost: Double,
    val modelCount: Int,
  )

  /**
   * Records and increments usage statistics for a specific AI model invocation.
   *
   * This method should be called after each AI model API call to track resource consumption.
   *
   * @param session The session in which the usage occurred
   * @param user The user who initiated the AI model call
   * @param model The AI model that was used
   * @param usage The usage details including prompt tokens, completion tokens, and cost
   */
  fun incrementUsage(
    session: Session,
    user: User,
    model: AIModel,
    usage: ModelSchema.Usage,
    data: ModelSchema.UsageData? = null
  )

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
  fun getUserBalance(user: User): Double

  /**
   * Retrieves the individual usage rows for a session (and its descendant sessions).
   *
   * Each entry corresponds to a single recorded AI model invocation, including
   * token counts, cost, timestamp, and (where retained) the input and output text.
   *
   * @param session The session whose usage rows are to be retrieved
   * @return A list of [UsageRow] entries ordered by ascending datetime
   */
  fun getSessionUsageRows(session: Session): List<UsageRow>

  /**
   * Represents a single usage row recorded for a session.
   */
  data class UsageRow(
    val id: Long,
    val sessionId: String?,
    val userId: String?,
    val model: String?,
    val datetime: Instant?,
    val tokenCounts: Map<TokenTypes, Long>,
    val cost: Double,
    val inputText: String?,
    val outputText: String?
  )


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
   * @property tokenCounts Atomic counters for each token type
   * @property cost Atomic accumulator for monetary cost
   */

  class UsageValues(
    val tokenCounts: ConcurrentHashMap<TokenTypes, AtomicLong> = ConcurrentHashMap(),
    val cost: AtomicDouble = AtomicDouble(),
  ) {
    /**
     * Atomically adds the given usage tokens and cost to the current values.
     *
     * This method is thread-safe and can be called concurrently without external
     * synchronization. It updates token counts (per type) and cost atomically.
     *
     * @param tokens The usage object containing tokens and cost to add
     */
    fun addAndGet(tokens: ModelSchema.Usage, cost: Double? = null) {
      tokens.counts.forEach { (type, count) ->
        tokenCounts.computeIfAbsent(type) { AtomicLong() }.addAndGet(count)
      }
      if(tokens.counts.isEmpty() && tokens.total_tokens > 0) {
        tokenCounts.computeIfAbsent(TokenTypes.Prompt) { AtomicLong() }.addAndGet(tokens.total_tokens)
      }
      this.cost.addAndGet(cost ?: 0.0)
    }
  }

}