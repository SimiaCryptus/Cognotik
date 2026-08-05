package com.simiacryptus.cognotik.platform.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Typed identifier / value wrappers.
 *
 * These are `@JvmInline value class`es: free at runtime, but they eliminate the
 * whole class of argument-swap bugs that raw `String` identifiers invite
 * (see REVIEW.md §4.2).
 */

@JvmInline
value class UserId(val value: String) {
  override fun toString() = value
}

@JvmInline
value class OwnerId(val value: String) {
  override fun toString() = value
}

@JvmInline
value class WorkerId(val value: String) {
  override fun toString() = value
}

@JvmInline
value class PluginId(val value: String) {
  override fun toString() = value
}

@JvmInline
value class GiftId(val value: String) {
  override fun toString() = value
}

@JvmInline
value class ClaimId(val value: String) {
  override fun toString() = value
}

/**
 * Opaque bearer token.
 *
 * [toString] is deliberately redacted so tokens cannot be leaked by naive
 * logging or string interpolation. Implementations MUST store only a hash of
 * [value] at rest.
 */
@JvmInline
value class AccessToken(val value: String) {
  override fun toString() = "AccessToken(***)"
}

/**
 * Exact monetary amount expressed in integral micro-credits (1 credit = 1_000_000 micros).
 *
 * Replaces the `Double` money fields flagged as [H] in REVIEW.md §3.7: addition,
 * subtraction and equality are exact, so budget-exhaustion checks are sound.
 */
@JvmInline
value class Credits(val micros: Long) : Comparable<Credits> {

  operator fun plus(other: Credits) = Credits(micros + other.micros)
  operator fun minus(other: Credits) = Credits(micros - other.micros)
  operator fun times(count: Int) = Credits(micros * count)

  val isZero: Boolean get() = micros == 0L
  val isPositive: Boolean get() = micros > 0L

  fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(micros, SCALE)

  /**
   * Lossy conversion retained only for interop with the deprecated `Double` APIs.
   */
  fun toDouble(): Double = micros / MICROS_PER_CREDIT.toDouble()

  override fun compareTo(other: Credits): Int = micros.compareTo(other.micros)

  override fun toString(): String = toBigDecimal().toPlainString()

  companion object {
    const val SCALE = 6
    const val MICROS_PER_CREDIT = 1_000_000L

    val ZERO = Credits(0)

    fun ofMicros(micros: Long) = Credits(micros)

    fun of(value: BigDecimal): Credits =
      Credits(value.setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).longValueExact())

    /**
     * Interop helper for the deprecated `Double`-based APIs. Prefer [of] with a [BigDecimal].
     */
    fun of(value: Double): Credits = of(BigDecimal.valueOf(value))
  }
}