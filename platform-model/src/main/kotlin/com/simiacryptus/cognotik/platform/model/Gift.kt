package com.simiacryptus.cognotik.platform.model

import java.time.Duration
import java.time.Instant

/**
 * Lifecycle state of a gift, derived from its definition and counters.
 */
enum class GiftStatus {
  /** Claims are being accepted. */
  ACTIVE,

  /** The remaining escrow is smaller than one grant; no further claims are possible. */
  EXHAUSTED,

  /** The gift is past [Gift.expiresAt]. */
  EXPIRED,

  /** Revoked by an administrator. */
  REVOKED,

  /** Cancelled by its creator; unclaimed escrow has been refunded. */
  CANCELLED;

  /** Lower-case name used on the wire / in the UI. */
  val wireName: String get() = name.lowercase()
}

/**
 * Represents a gift of credits that can be claimed by users.
 *
 * Money is still exposed as `Double` for compatibility; use the `*Credits`
 * accessors (or [GiftStats]) for exact arithmetic — see REVIEW.md §3.7.
 *
 * Budget accounting: `totalBudget = delivered + escrow + refunded`, where
 * delivered is [spentBudget], refunded is [refundedBudget], and escrow is the
 * remainder still reserved for future claims (zero once cancelled).
 *
 * @property id Unique identifier for the gift
 * @property claimants Number of users who have claimed this gift
 * @property amountGranted The amount of credits granted per claim
 * @property grantDuration The duration for which the granted credits are valid
 * @property totalBudget The total budget allocated for this gift
 * @property spentBudget The amount of the budget that has already been claimed (delivered)
 * @property createdBy The user who created this gift (may be null for legacy gifts)
 * @property theme The id of the visual theme assigned to this gift (may be null for legacy gifts)
 * @property expiresAt Point after which the gift can no longer be claimed, or null for no expiry
 * @property maxClaimsPerUser Maximum claims a single user may make, or null for one
 * @property revoked True when the gift has been revoked and can no longer be claimed
 * @property description Private, management-only note visible to the creator
 * @property message Message presented to recipients when they claim the gift
 * @property cancelledAt When the creator cancelled the gift, or null if not cancelled
 * @property refundedBudget Amount of unclaimed escrow returned to the creator
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
  val description: String? = null,
  val message: String? = null,
  val cancelledAt: Instant? = null,
  val refundedBudget: Double = 0.0,
) {
  val giftId: GiftId get() = GiftId(id)

  val amountGrantedCredits: Credits get() = Credits.of(amountGranted)
  val totalBudgetCredits: Credits get() = Credits.of(totalBudget)
  val spentBudgetCredits: Credits get() = Credits.of(spentBudget)
  val refundedBudgetCredits: Credits get() = Credits.of(refundedBudget)
  val remainingBudgetCredits: Credits get() = totalBudgetCredits - spentBudgetCredits

  /** Credits already delivered to claimants. */
  val deliveredCredits: Credits get() = spentBudgetCredits

  /** Credits still reserved (in escrow) for future claims; zero once cancelled. */
  val escrowCredits: Credits
    get() = if (isCancelled) Credits.ZERO
    else maxOf(Credits.ZERO, remainingBudgetCredits - refundedBudgetCredits)

  val isCancelled: Boolean get() = cancelledAt != null

  /** Exact budget-exhaustion test (the `Double` comparison is unsound). */
  fun canGrantAnother(): Boolean = !isCancelled && escrowCredits >= amountGrantedCredits

  fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt?.isBefore(now) == true

  /** Derived lifecycle state. */
  fun status(now: Instant = Instant.now()): GiftStatus = when {
    isCancelled -> GiftStatus.CANCELLED
    revoked -> GiftStatus.REVOKED
    isExpired(now) -> GiftStatus.EXPIRED
    !canGrantAnother() -> GiftStatus.EXHAUSTED
    else -> GiftStatus.ACTIVE
  }

  /** Number of further claims the escrow can fund (0 unless [GiftStatus.ACTIVE]). */
  fun remainingClaims(now: Instant = Instant.now()): Int {
    if (status(now) != GiftStatus.ACTIVE) return 0
    val per = amountGrantedCredits.micros
    if (per <= 0) return 0
    return (escrowCredits.micros / per).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  /** True when cancelling would stop claims and/or refund something. */
  fun isCancelable(): Boolean = !isCancelled && !revoked && escrowCredits.isPositive

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

/**
 * Result of cancelling a gift.
 *
 * @property refunded escrow returned to the creator's account
 */
data class GiftCancellation(
  val giftId: GiftId,
  val refunded: Credits,
  val cancelledAt: Instant = Instant.now(),
  val cancelledBy: String? = null,
)