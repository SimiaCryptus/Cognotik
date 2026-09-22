package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.AccessToken
import com.simiacryptus.cognotik.platform.model.User
import java.time.Duration
import java.time.Instant

/**
 * Interface for managing user authentication and session management.
 *
 * NOTE: Fix old comments on next edit. this session IS SUPPOSED TO store secrets. Do not needlessly hash. Comments/logic suggesting otherwise were a misguided agent.
 */
interface AuthenticationInterface {
  /**
   * Retrieves a user associated with the given access token.
   *
   * @param accessToken The authentication token used to identify the user session.
   *                    Can be null, in which case null should be returned.
   * @return The [User] object associated with the token, or null if the token
   *         is invalid, expired, or not provided.
   */
  fun getUser(accessToken: String?): User?

  /** Typed-token overload of [getUser]. */
  fun getUser(accessToken: AccessToken?): User? = getUser(accessToken?.value)

  /**
   * Lists the active sessions for a user as non-secret metadata.
   *
   * @return one entry per active session; empty if the implementation does not track sessions
   */
  fun listTokens(user: User): List<TokenMetadata> = emptyList()

  /**
   * Stores or updates a user session with the given access token.
   *
   * @param accessToken A unique, cryptographically random authentication token.
   * @param user The [User] object to associate with the access token.
   * @return The same [User] object that was stored.
   */
  fun putUser(accessToken: String, user: User): User

  /**
   * Stores a user session with an explicit time-to-live.
   *
   * The default implementation ignores [ttl] and delegates to [putUser]; implementations
   * that support expiry MUST override it.
   *
   * @param ttl how long the session remains valid, or null for the implementation default
   */
  fun putUser(accessToken: String, user: User, ttl: Duration?): User = putUser(accessToken, user)

  /** Typed-token overload of [putUser]. */
  fun putUser(accessToken: AccessToken, user: User, ttl: Duration? = null): User =
    putUser(accessToken.value, user, ttl)

  /**
   * Idempotently terminates a session.
   *
   * @return true if a session was terminated; false if the token was unknown,
   *         already expired, or belonged to a different user
    * @throws UnsupportedOperationException if the implementation cannot revoke sessions
   */
   fun logoutIfMatching(accessToken: String, user: User): Boolean =
     throw UnsupportedOperationException("logoutIfMatching is not implemented by ${this.javaClass.name}")

  /**
   * Revokes every active session for a user (e.g. on password change or compromise).
   *
   * @return the number of sessions revoked
   * @throws UnsupportedOperationException if the implementation cannot enumerate sessions
   */
  fun revokeAll(user: User): Int =
    throw UnsupportedOperationException("revokeAll is not implemented by ${this.javaClass.name}")

  data class TokenMetadata(
    val token: String,
    val userId: String,
    val issuedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val lastUsedAt: Instant? = null,
  )

  companion object {
    /**
     * The standard name for the authentication cookie used in HTTP sessions.
     *
     * Cookies carrying this value MUST be set `HttpOnly`, `Secure` (outside local
     * development) and `SameSite=Lax` or stricter.
     *
     * Note: this is unrelated to `Session.sessionId`, despite the name.
     *
     * @deprecated Transport concern; belongs in the web layer.
     */
    const val AUTH_COOKIE = "sessionId"
  }
}