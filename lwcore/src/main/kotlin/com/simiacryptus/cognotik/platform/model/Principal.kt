package com.simiacryptus.cognotik.platform.model

/**
 * Explicit encoding of "who is acting", replacing the overloaded `User?` parameter
 * (see REVIEW.md §4.1) where `null`, [User.NULL] and `defaultUser` all meant
 * subtly different things.
 */
sealed interface Principal {

  /** The authenticated user, or null for [Anonymous]/[System]. */
  val user: User? get() = null

  /** No authenticated identity (public / unauthenticated access). */
  object Anonymous : Principal

  /** The platform itself acting outside any user context (background jobs, migrations). */
  object System : Principal

  /** A concrete authenticated user. */
  data class Authenticated(override val user: User) : Principal

  companion object {
    /**
     * Bridge from the legacy `User?` convention.
     *
     * @param user the user, or null for anonymous access
     */
    fun of(user: User?): Principal = if (user == null) Anonymous else Authenticated(user)
  }
}