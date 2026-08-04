package com.simiacryptus.cognotik.platform.model

import java.time.Duration

/**
 * Represents a gift of credits that can be claimed by users.
 *
 * @property id Unique identifier for the gift
 * @property claimants Number of users who have claimed this gift
 * @property amountGranted The amount of credits granted per claim
 * @property grantDuration The duration for which the granted credits are valid
 * @property totalBudget The total budget allocated for this gift
 * @property spentBudget The amount of the budget that has already been claimed
 * @property createdBy The id of the user who created this gift (may be null for legacy gifts)
 * @property theme The id of the visual theme assigned to this gift (may be null for legacy gifts)
 */
data class Gift(
  val id: String,
  val claimants: Int,
  val amountGranted: Double,
  val grantDuration: Duration,
  val totalBudget: Double,
  val spentBudget: Double,
  val createdBy: String? = null,
  val theme: String? = null
)