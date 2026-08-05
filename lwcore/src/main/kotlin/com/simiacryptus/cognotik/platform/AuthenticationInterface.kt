package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.AccessToken
import com.simiacryptus.cognotik.platform.model.TokenMetadata
import com.simiacryptus.cognotik.platform.model.User
import java.time.Duration

/**
 * Interface for managing user authentication and session management.
 *
 * Implementations MUST store only a hash of the access token at rest, MUST treat
 * tokens as opaque bearer credentials, and SHOULD support expiry and revocation
 * (see REVIEW.md §3.6).
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
   * Reverse lookup from user to token.
   *
   * @deprecated Implies tokens are recoverable (i.e. not hashed) and that a user has
   *             at most one session. Use [listTokens] for non-secret session metadata.
   */
  @Deprecated(
    "Reverse token lookup implies recoverable (unhashed) storage and a single session per " +
        "user; use listTokens(user) for non-secret metadata.",
    ReplaceWith("listTokens(user)")
  )
  fun getAccessToken(user: User): String? = listTokens(user).firstOrNull()?.label

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
   * Terminates a user session by removing the association between the access token and user.
   *
   * @param accessToken The authentication token of the session to terminate.
   * @param user The [User] object requesting logout. Must match the user associated
   *             with the access token.
   * @throws IllegalArgumentException if the provided user doesn't match the user
   *                                  associated with the access token.
   * @deprecated Throwing on mismatch is an oracle; use [logoutIfMatching].
   */
  @Deprecated(
    "Throwing on user mismatch leaks token validity; use the idempotent logoutIfMatching.",
    ReplaceWith("logoutIfMatching(accessToken, user)")
  )
  fun logout(accessToken: String, user: User) {
    logoutIfMatching(accessToken, user)
  }

  /**
   * Idempotently terminates a session.
   *
   * @return true if a session was terminated; false if the token was unknown,
   *         already expired, or belonged to a different user
   */
  @Suppress("DEPRECATION")
  fun logoutIfMatching(accessToken: String, user: User): Boolean =
    try {
      logout(accessToken, user)
      true
    } catch (e: IllegalArgumentException) {
      false
    }

  /**
   * Revokes every active session for a user (e.g. on password change or compromise).
   *
   * @return the number of sessions revoked
   * @throws UnsupportedOperationException if the implementation cannot enumerate sessions
   */
  fun revokeAll(user: User): Int =
    throw UnsupportedOperationException("revokeAll is not implemented by ${this.javaClass.name}")

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