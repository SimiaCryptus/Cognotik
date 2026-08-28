package com.simiacryptus.cognotik.platform.model

import java.time.Duration
import java.time.Instant

/**
 * Represents a gift of credits that can be claimed by users.
 *
 * Money is still exposed as `Double` for compatibility; use the `*Credits`
 * accessors (or [GiftStats]) for exact arithmetic — see REVIEW.md §3.7.
 *
 * @property id Unique identifier for the gift
 * @property claimants Number of users who have claimed this gift
 * @property amountGranted The amount of credits granted per claim
 * @property grantDuration The duration for which the granted credits are valid
 * @property totalBudget The total budget allocated for this gift
 * @property spentBudget The amount of the budget that has already been claimed
 * @property createdBy The id of the user who created this gift (may be null for legacy gifts)
 * @property theme The id of the visual theme assigned to this gift (may be null for legacy gifts)
 * @property expiresAt Point after which the gift can no longer be claimed, or null for no expiry
 * @property maxClaimsPerUser Maximum claims a single user may make, or null for one
 * @property revoked True when the gift has been revoked and can no longer be claimed
 */
data class Gift(
  val id: String,
  val claimants: Int,
  val amountGranted: Double,
  val grantDuration: Duration,
  val totalBudget: Double,
  val spentBudget: Double,
  val createdBy: User? = null,
  val theme: String? = null,
  val expiresAt: Instant? = null,
  val maxClaimsPerUser: Int? = null,
  val revoked: Boolean = false,
) {
  val giftId: GiftId get() = GiftId(id)

  val amountGrantedCredits: Credits get() = Credits.of(amountGranted)
  val totalBudgetCredits: Credits get() = Credits.of(totalBudget)
  val spentBudgetCredits: Credits get() = Credits.of(spentBudget)
  val remainingBudgetCredits: Credits get() = totalBudgetCredits - spentBudgetCredits

  /** Exact budget-exhaustion test (the `Double` comparison is unsound). */
  fun canGrantAnother(): Boolean = remainingBudgetCredits >= amountGrantedCredits

  fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt?.isBefore(now) == true

  /** Mutable/derived counters, split out of the immutable definition. */
  fun stats(): GiftStats = GiftStats(
    giftId = giftId,
    claimants = claimants,
    spentBudget = spentBudgetCredits,
    totalBudget = totalBudgetCredits,
  )
}

/**
 * Contended, derived counters for a gift, separated from its immutable definition.
 */
data class GiftStats(
  val giftId: GiftId,
  val claimants: Int,
  val spentBudget: Credits,
  val totalBudget: Credits,
) {
  val remainingBudget: Credits get() = totalBudget - spentBudget
}