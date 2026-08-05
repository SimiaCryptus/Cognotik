package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.TokenMetadata
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [AuthenticationInterface].
 *
 * Per the platform contract (REVIEW.md §3.6) only a SHA-256 *hash* of the bearer
 * token is retained, so a token value can never be recovered from this store.
 * That is why there is no reverse lookup (`user -> token`); use [listTokens]
 * plus [TokenMetadata.tokenId] to build session-management UIs.
 */
open class AuthenticationManager : AuthenticationInterface {

  private class Entry(
    val user: User,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    @Volatile var lastUsedAt: Instant,
  )

  /** Keyed by the hash of the presented token; never by the token itself. */
  private val sessions = ConcurrentHashMap<String, Entry>()

  override fun getUser(accessToken: String?): User? {
    if (accessToken.isNullOrBlank()) return null
    val key = hash(accessToken)
    val entry = sessions[key] ?: return null
    if (entry.expiresAt?.isBefore(Instant.now()) == true) {
      sessions.remove(key, entry)
      log.debug("Rejected expired access token for user: {}", entry.user)
      return null
    }
    entry.lastUsedAt = Instant.now()
    return entry.user
  }

  override fun putUser(accessToken: String, user: User): User = putUser(accessToken, user, null)

  override fun putUser(accessToken: String, user: User, ttl: Duration?): User {
    require(accessToken.isNotBlank()) { "Access token must not be blank" }
    val now = Instant.now()
    sessions[hash(accessToken)] = Entry(
      user = user,
      issuedAt = now,
      expiresAt = ttl?.let { now.plus(it) },
      lastUsedAt = now,
    )
    log.debug("Stored session for user: {} (ttl={})", user, ttl)
    return user
  }

  override fun listTokens(user: User): List<TokenMetadata> = sessions.entries
    .filter { it.value.user == user }
    .map { (key, entry) ->
      TokenMetadata(
        tokenId = key.take(12),
        userId = user.id,
        issuedAt = entry.issuedAt,
        expiresAt = entry.expiresAt,
        lastUsedAt = entry.lastUsedAt,
      )
    }

  override fun logoutIfMatching(accessToken: String, user: User): Boolean {
    if (accessToken.isBlank()) return false
    val key = hash(accessToken)
    val entry = sessions[key] ?: return false
    if (entry.user != user) {
      log.warn("Logout attempted with a token belonging to a different user")
      return false
    }
    return sessions.remove(key, entry)
  }

  override fun revokeAll(user: User): Int {
    val doomed = sessions.entries.filter { it.value.user == user }
    doomed.forEach { sessions.remove(it.key, it.value) }
    log.info("Revoked {} session(s) for user: {}", doomed.size, user)
    return doomed.size
  }

  @Deprecated(
    "Tokens are stored hashed and cannot be recovered; use listTokens(user).",
    ReplaceWith("listTokens(user)")
  )
  fun getAccessToken(user: User): String? {
    log.warn("getAccessToken is no longer supported; tokens are stored hashed. User: {}", user)
    return null
  }

  @Deprecated("Use logoutIfMatching, which is idempotent.", ReplaceWith("logoutIfMatching(accessToken, user)"))
  fun logout(accessToken: String, user: User) {
    require(logoutIfMatching(accessToken, user)) { "Invalid user" }
  }

  private fun hash(token: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(token.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }

  companion object {
    private val log = LoggerFactory.getLogger(AuthenticationManager::class.java)
  }
}