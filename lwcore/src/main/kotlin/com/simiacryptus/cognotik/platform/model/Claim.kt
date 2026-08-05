package com.simiacryptus.cognotik.platform.model

import java.time.Instant

/**
 * Represents a single claim of a gift by a user.
 *
 * The granted amount and expiry are snapshotted on the claim so that history
 * remains auditable even if the gift definition is later edited (REVIEW.md §3.7).
 *
 * @property giftId Unique identifier of the claimed gift
 * @property userId The id of the user who claimed the gift
 * @property claimedAt The time the claim was created (may be null if not tracked)
 * @property id Unique identifier of this claim, or null for legacy claims
 * @property grantedAmount The amount actually granted by this claim, or null for legacy claims
 * @property expiresAt When the granted credits expire, or null if unknown/unbounded
 */
data class Claim(
  val giftId: String,
  val userId: String,
  val claimedAt: Instant? = null,
  val id: String? = null,
  val grantedAmount: Credits? = null,
  val expiresAt: Instant? = null,
) {
  val claimId: ClaimId? get() = id?.let { ClaimId(it) }
}