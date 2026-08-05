package com.simiacryptus.cognotik.platform.model

import java.time.Instant

/**
 * Outcome of a gift claim attempt.
 *
 * Replaces the opaque `Boolean` return of `claimGift` (REVIEW.md §3.7) so callers
 * can explain *why* a claim failed.
 */
sealed interface ClaimResult {

  /** The claim succeeded and credits were granted. */
  data class Granted(
    val amount: Credits,
    val expiresAt: Instant? = null,
    val claimId: ClaimId? = null,
  ) : ClaimResult

  /** This user has already claimed this gift (or exceeded `maxClaimsPerUser`). */
  object AlreadyClaimed : ClaimResult

  /** The gift's remaining budget is insufficient for another grant. */
  object BudgetExhausted : ClaimResult

  /** The gift is past its expiry. */
  object GiftExpired : ClaimResult

  /** No gift exists with the requested id. */
  object GiftNotFound : ClaimResult

  /** The gift was revoked by its creator or an administrator. */
  object GiftRevoked : ClaimResult

  /**
   * Failure that the implementation could not classify.
   * Present so that legacy `Boolean`-returning implementations can be bridged.
   */
  data class Failed(val reason: String? = null) : ClaimResult
}