package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Claim
import com.simiacryptus.cognotik.platform.model.ClaimResult
import com.simiacryptus.cognotik.platform.model.Credits
import com.simiacryptus.cognotik.platform.model.Gift
import com.simiacryptus.cognotik.platform.model.GiftId
import com.simiacryptus.cognotik.platform.model.GiftStats
import com.simiacryptus.cognotik.platform.model.Page
import com.simiacryptus.cognotik.platform.model.PageResult
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.paginate
import java.time.Duration
import java.time.Instant

/**
 * Interface for managing gifted credits.
 *
 * Transaction contract: [createGift] debits the creator's account and creates the gift
 * atomically; [claim] debits the gift's shared budget and credits the claiming user
 * atomically. Both accept an optional idempotency key so that a retried request cannot
 * double-charge (REVIEW.md §3.7).
 *
 * Validation contract: implementations MUST reject non-positive `amountGranted`,
 * `totalBudget < amountGranted`, non-positive `grantDuration`, and insufficient creator
 * balance with [IllegalArgumentException] (or [IllegalStateException] for balance).
 */

interface GiftedCreditsInterface {

  /**
   * Creates a new gift using exact credit amounts.
   *
   * @param idempotencyKey Optional caller-supplied key; repeating a call with the same key
   *                       MUST return the previously created gift rather than charging again
   * @param expiresAt Optional point after which the gift can no longer be claimed
   * @param maxClaimsPerUser Optional per-user claim limit (defaults to one)
   */
  @Suppress("DEPRECATION")
  fun createGift(
    creator: User,
    amountGranted: Credits,
    grantDuration: Duration,
    totalBudget: Credits,
    theme: String? = null,
    idempotencyKey: String? = null,
    expiresAt: Instant? = null,
    maxClaimsPerUser: Int? = null,
  ): Gift =
    createGift(creator, Credits.of(amountGranted.toDouble()), grantDuration, Credits.of(totalBudget.toDouble()), theme)

  /**
   * Retrieves a gift by its ID.
   *
   * @param id The unique identifier of the gift
   * @return The Gift object, or null if not found
   */
  fun getGift(id: String): Gift?

  /** Typed-id overload of [getGift]. */
  fun getGift(id: GiftId): Gift? = getGift(id.value)

  /** Current derived counters for a gift, or null if it does not exist. */
  fun getGiftStats(id: GiftId): GiftStats? = getGift(id)?.stats()

  /**
   * Allows a user to claim a gift, reporting a precise outcome.
   *
   * @param idempotencyKey Optional caller-supplied key; repeating a call with the same key
   *                       MUST return the original outcome rather than granting again
   */
  @Suppress("DEPRECATION")
  fun claim(user: User, giftId: String, idempotencyKey: String? = null): ClaimResult {
    val gift = getGift(giftId) ?: return ClaimResult.GiftNotFound
    if (gift.revoked) return ClaimResult.GiftRevoked
    if (gift.isExpired()) return ClaimResult.GiftExpired
    if (!gift.canGrantAnother()) return ClaimResult.BudgetExhausted
    return if (claim(user, giftId) is ClaimResult.Granted) {
      ClaimResult.Granted(
        amount = gift.amountGrantedCredits,
        expiresAt = Instant.now().plus(gift.grantDuration),
      )
    } else {
      ClaimResult.Failed("claim rejected by ${this.javaClass.simpleName}")
    }
  }

  /** Typed-id overload of [claim]. */
  fun claim(user: User, giftId: GiftId, idempotencyKey: String? = null): ClaimResult =
    claim(user, giftId.value, idempotencyKey)

  /**
   * Lists all available gifts.
   *
   * @return A list of all Gift objects
   */
  fun listGifts(): List<Gift>

  /**
   * Lists gifts created by a specific user.
   *
   * Default implementation filters [listGifts]; backends should push the filter down.
   */
  fun listGifts(createdBy: String?): List<Gift> =
    if (createdBy == null) listGifts() else listGifts().filter { it.createdBy == createdBy }

  /** Paged variant of [listGifts]; default pages in memory. */
  fun listGifts(createdBy: String?, page: Page): PageResult<Gift> = listGifts(createdBy).paginate(page)

  /**
   * Revokes a gift so that it can no longer be claimed. Existing claims are unaffected.
   *
   * @return true if the gift existed and was revoked
   */
  fun revokeGift(giftId: GiftId): Boolean =
    throw UnsupportedOperationException("revokeGift is not implemented by ${this.javaClass.name}")

  /**
   * Marks a gift as expired as of now, refunding unspent budget to the creator.
   *
   * @return true if the gift existed and was expired
   */
  fun expireGift(giftId: GiftId): Boolean =
    throw UnsupportedOperationException("expireGift is not implemented by ${this.javaClass.name}")

  /**
   * Permanently deletes a gift and its claim history.
   *
   * @return true if the gift existed and was deleted
   */
  fun deleteGift(giftId: GiftId): Boolean =
    throw UnsupportedOperationException("deleteGift is not implemented by ${this.javaClass.name}")

  /**
   * Lists all claims, optionally filtered by gift id and/or user id.
   *
   * @param giftId Optional gift id to filter claims by
   * @param userId Optional user id to filter claims by
   * @return A list of Claim objects matching the filters
   */
  fun listClaims(giftId: String? = null, userId: String? = null): List<Claim>

  /** Paged variant of [listClaims]; default pages in memory. */
  fun listClaims(giftId: String?, userId: String?, page: Page): PageResult<Claim> =
    listClaims(giftId, userId).paginate(page)
}