package com.simiacryptus.cognotik.platform.model

import java.time.Duration
import java.time.Instant

/**
* Interface for managing gifted credits.
*/

interface GiftedCreditsInterface {

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

  /**
   * Creates a new gift.
   *
  * @param creator The user creating the gift; their account will be debited for the totalBudget
   * @param amountGranted The amount of credits to grant per claim
   * @param grantDuration The duration for which the granted credits are valid
   * @param totalBudget The total budget allocated for this gift
  * @param theme Optional theme id for the gift's visual appearance
   * @return The created Gift object
   */
  fun createGift(
      creator: User,
      amountGranted: Double,
      grantDuration: Duration,
      totalBudget: Double,
      theme: String? = null
  ): Gift

  /**
   * Retrieves a gift by its ID.
   *
   * @param id The unique identifier of the gift
   * @return The Gift object, or null if not found
   */
  fun getGift(id: String): Gift?

  /**
   * Allows a user to claim a gift.
   *
   * @param user The user claiming the gift
   * @param giftId The unique identifier of the gift
   * @return True if the claim was successful, false otherwise (e.g., budget exhausted, already claimed)
   */
  fun claimGift(user: User, giftId: String): Boolean

  /**
   * Lists all available gifts.
   *
   * @return A list of all Gift objects
   */
  fun listGifts(): List<Gift>
   /**
    * Lists all claims, optionally filtered by gift id and/or user id.
    *
    * @param giftId Optional gift id to filter claims by
    * @param userId Optional user id to filter claims by
    * @return A list of Claim objects matching the filters
    */
   fun listClaims(giftId: String? = null, userId: String? = null): List<Claim>
}