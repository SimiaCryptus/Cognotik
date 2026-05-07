package com.simiacryptus.cognotik.platform.model

/**
 * Interface for managing user authentication and session management.
 *
 * This interface provides the core authentication operations for managing user sessions,
 * including retrieving authenticated users, storing user sessions, and handling logouts.
 * Implementations of this interface should handle the secure storage and retrieval of
 * user authentication tokens and associated user data.
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
  fun getUser(accessToken: String?): User

  fun getAccessToken(user: User): String?

  /**
   * Stores or updates a user session with the given access token.
   *
   * This method associates a user with an access token, effectively creating
   * or updating an authenticated session. The token should be unique and
   * securely generated to prevent session hijacking.
   *
   * @param accessToken A unique authentication token for the user session.
   *                    This token will be used to retrieve the user in subsequent requests.
   * @param user The [User] object to associate with the access token.
   * @return The same [User] object that was stored, for method chaining or confirmation.
   */

  fun putUser(accessToken: String, user: User): User

  /**
   * Terminates a user session by removing the association between the access token and user.
   *
   * This method should verify that the provided user matches the one associated with
   * the token before removing the session. This prevents unauthorized session termination.
   *
   * @param accessToken The authentication token of the session to terminate.
   * @param user The [User] object requesting logout. Must match the user associated
   *             with the access token.
   * @throws IllegalArgumentException if the provided user doesn't match the user
   *                                  associated with the access token.
   */
  fun logout(accessToken: String, user: User)


  companion object {
    /**
     * The standard name for the authentication cookie used in HTTP sessions.
     *
     * This constant defines the cookie name that should be used to store and
     * retrieve the session identifier (access token) in web applications.
     * Implementations should use this constant to ensure consistency across
     * the application.
     */
    const val AUTH_COOKIE = "sessionId"
  }
}