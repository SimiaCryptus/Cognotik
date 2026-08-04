package com.simiacryptus.cognotik.platform.model

import java.time.Instant

/**
 * Represents a single claim of a gift by a user.
 *
 * @property giftId Unique identifier of the claimed gift
 * @property userId The id of the user who claimed the gift
 * @property claimedAt The time the claim was created (may be null if not tracked)
 */
data class Claim(
  val giftId: String,
  val userId: String,
  val claimedAt: Instant? = null
)