package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Claim
import com.simiacryptus.cognotik.platform.model.Gift
import com.simiacryptus.cognotik.platform.model.User
import java.time.Duration

/**
 * Interface for managing gifted credits.
 */

interface GiftedCreditsInterface {

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