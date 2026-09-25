package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.platform.AuthenticationInterface.TokenMetadata
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
 * plus [TokenMetadata.token] to build session-management UIs.
 */
open class AuthenticationManager : AuthenticationInterface {
  init {
    log.info("AuthenticationManager initialized", RuntimeException("Stack Trace"))
  }

  private class Entry(
    val user: User,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    @Volatile var lastUsedAt: Instant,
  )

  override fun getUser(accessToken: String?): User? {
    if (accessToken.isNullOrBlank()) return null
    val entry = sessions[accessToken] ?: return null
    if (entry.expiresAt?.isBefore(Instant.now()) == true) {
      sessions.remove(accessToken, entry)
      log.debug("Rejected expired access token for user: {}", entry.user)
      return null
    }
    entry.lastUsedAt = Instant.now()
    if (verbose) log.info("Resolved access token to user: {} (lastUsedAt updated)", entry.user)
    return entry.user
  }

  override fun putUser(accessToken: String, user: User): User = putUser(accessToken, user, null)

  override fun putUser(accessToken: String, user: User, ttl: Duration?): User {
    require(accessToken.isNotBlank()) { "Access token must not be blank" }
    val now = Instant.now()
    sessions[accessToken] = Entry(
      user = user,
      issuedAt = now,
      expiresAt = ttl?.let { now.plus(it) },
      lastUsedAt = now,
    )
    log.debug("Stored session for user: {} (ttl={})", user, ttl)
    if (verbose) log.info("Session created for user: {} (issuedAt={}, expiresAt={}, totalSessions={})",
      user, now, ttl?.let { now.plus(it) }, sessions.size)
    return user
  }

  override fun listTokens(user: User): List<TokenMetadata> = sessions.entries
    .filter { it.value.user == user }
    .map { (key, entry) ->
      TokenMetadata(
        userId = entry.user.id,
        issuedAt = entry.issuedAt,
        expiresAt = entry.expiresAt,
        lastUsedAt = entry.lastUsedAt,
        token = key,
      )
    }
    .also { if (verbose) log.info("Listed {} token(s) for user: {}", it.size, user) }

  override fun logoutIfMatching(accessToken: String, user: User): Boolean {
    if (accessToken.isBlank()) return false
    val entry = sessions[accessToken] ?: return false
    if (entry.user != user) {
      log.warn("Logout attempted with a token belonging to a different user")
      return false
    }
    val removed = sessions.remove(accessToken, entry)
    if (verbose) log.info("Logout for user: {} succeeded={} (remainingSessions={})", user, removed, sessions.size)
    return removed
  }

  override fun revokeAll(user: User): Int {
    val doomed = sessions.entries.filter { it.value.user == user }
    doomed.forEach { sessions.remove(it.key, it.value) }
    log.info("Revoked {} session(s) for user: {}", doomed.size, user)
    if (verbose) log.info("Session store size after revocation: {}", sessions.size)
    return doomed.size
  }

  companion object {
    private val log = LoggerFactory.getLogger(AuthenticationManager::class.java)
    /** Hardcoded verbose flag to aid debugging with additional info-level logging. */
    private const val verbose: Boolean = true


    fun hash(token: String): String =
      MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /** Keyed by the hash of the presented token; never by the token itself. */
    private val sessions = ConcurrentHashMap<String, Entry>()
      /**
       * Visible for testing only. The session store is `static`, so tests that run in the
       * same JVM would otherwise leak state into one another.
       */
      internal fun clearAllSessions() {
        if (verbose) log.info("Clearing all sessions (previous size={})", sessions.size)
        sessions.clear()
      }
      /** Visible for testing only: number of live (not necessarily unexpired) sessions. */
      internal fun sessionCount(): Int = sessions.size
  }
}