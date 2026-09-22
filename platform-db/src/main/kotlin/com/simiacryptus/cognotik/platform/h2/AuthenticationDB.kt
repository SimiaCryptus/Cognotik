package com.simiacryptus.cognotik.platform.h2

import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.platform.AuthenticationInterface.TokenMetadata
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.toJson
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Database-backed implementation of [AuthenticationInterface] using the Exposed DSL
 * on top of the shared [DatabaseFacet] connection management.
 *
 * Unlike [com.simiacryptus.cognotik.platform.file.AuthenticationManager] (in-memory,
 * lost on restart) this implementation persists sessions, so tokens survive process
 * restarts and are visible to every node pointing at the same database.
 *
 * Secrets: the bearer token is stored verbatim as the primary key. This is deliberate
 * (see the contract note on [AuthenticationInterface]) -- the token must be usable as a
 * lookup key. Protect the table with the usual database access controls; do NOT log
 * token values (this class never does).
 *
 * Caching: reads are served from an on-heap cache keyed by token. `last_used_at` is
 * only flushed to the database every [touchIntervalMillis] (default 60s) per token so a
 * hot token does not trigger a write on every request.
 */
open class AuthenticationDB : AuthenticationInterface {

  /**
   * Exposed table definition for access tokens / sessions.
   */
  object AccessTokensTable : Table("access_tokens") {
    val token = varchar("token", 512)
    val userId = varchar("user_id", 255)
    val userJson = text("user_json")
    val issuedAt = timestamp("issued_at")
    val expiresAt = timestamp("expires_at").nullable()
    val lastUsedAt = timestamp("last_used_at")
    override val primaryKey = PrimaryKey(token)

    init {
      index("idx_access_tokens_user_id", false, userId)
      index("idx_access_tokens_expires_at", false, expiresAt)
    }
  }

  /** Cached session row; mutable fields are updated in place on access. */
  private class Entry(
    val user: User,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    @Volatile var lastUsedAt: Instant,
    @Volatile var lastPersistedNanos: Long,
  )

  private val cache = ConcurrentHashMap<String, Entry>()

  private val cacheHits = AtomicLong(0)
  private val cacheMisses = AtomicLong(0)

  /** How often `last_used_at` is flushed to the database, per token. */
  private val touchIntervalMillis: Long =
    System.getProperty("cognotik.auth.touchIntervalMillis", "60000").toLong()

  override fun getUser(accessToken: String?): User? {
    if (accessToken.isNullOrBlank()) return null
    val entry = cache[accessToken]?.also { cacheHits.incrementAndGet() }
      ?: synchronized(cache) {
        cache[accessToken] ?: run {
          cacheMisses.incrementAndGet()
          loadFromDb(accessToken)?.also { cache[accessToken] = it }
        }
      }
      ?: return null
    val now = Instant.now()
    if (entry.expiresAt?.isBefore(now) == true) {
      cache.remove(accessToken, entry)
      deleteToken(accessToken)
      log.debug("Rejected expired access token for user: {}", entry.user)
      return null
    }
    touch(accessToken, entry, now)
    return entry.user
  }

  override fun putUser(accessToken: String, user: User): User = putUser(accessToken, user, null)

  override fun putUser(accessToken: String, user: User, ttl: Duration?): User {
    require(accessToken.isNotBlank()) { "Access token must not be blank" }
    require(accessToken.length <= 512) { "Access token exceeds the maximum supported length (512)" }
    val now = Instant.now()
    val expiry = ttl?.let { now.plus(it) }
    val json = user.toJson()
    val id = userId(user)
    try {
      transaction(facet.database) {
        val updated = AccessTokensTable.update({ AccessTokensTable.token eq accessToken }) {
          it[AccessTokensTable.userId] = id
          it[AccessTokensTable.userJson] = json
          it[AccessTokensTable.issuedAt] = now
          it[AccessTokensTable.expiresAt] = expiry
          it[AccessTokensTable.lastUsedAt] = now
        }
        if (updated == 0) {
          try {
            AccessTokensTable.insert {
              it[AccessTokensTable.token] = accessToken
              it[AccessTokensTable.userId] = id
              it[AccessTokensTable.userJson] = json
              it[AccessTokensTable.issuedAt] = now
              it[AccessTokensTable.expiresAt] = expiry
              it[AccessTokensTable.lastUsedAt] = now
            }
          } catch (e: java.sql.SQLException) {
            // Race: another writer inserted the same token between our UPDATE and INSERT.
            log.debug("Insert race detected for access_tokens; retrying update: {}", e.message)
            val retried = AccessTokensTable.update({ AccessTokensTable.token eq accessToken }) {
              it[AccessTokensTable.userId] = id
              it[AccessTokensTable.userJson] = json
              it[AccessTokensTable.issuedAt] = now
              it[AccessTokensTable.expiresAt] = expiry
              it[AccessTokensTable.lastUsedAt] = now
            }
            if (retried == 0) {
              log.error("Failed to upsert access token after insert race for user: {}", user, e)
              throw e
            }
          }
        }
      }
    } catch (e: Exception) {
      cache.remove(accessToken)
      log.error("Failed to persist session for user: {}: {}", user, e.message, e)
      throw e
    }
    cache[accessToken] = Entry(
      user = user,
      issuedAt = now,
      expiresAt = expiry,
      lastUsedAt = now,
      lastPersistedNanos = System.nanoTime(),
    )
    log.debug("Stored session for user: {} (ttl={})", user, ttl)
    return user
  }

  override fun listTokens(user: User): List<TokenMetadata> {
    val id = userId(user)
    return try {
      transaction(facet.database) {
        AccessTokensTable
          .selectAll()
          .where { AccessTokensTable.userId eq id }
          .mapNotNull { row ->
            val expiresAt = row[AccessTokensTable.expiresAt]
            if (expiresAt?.isBefore(Instant.now()) == true) null
            else TokenMetadata(
              token = row[AccessTokensTable.token],
              userId = row[AccessTokensTable.userId],
              issuedAt = row[AccessTokensTable.issuedAt],
              expiresAt = expiresAt,
              lastUsedAt = row[AccessTokensTable.lastUsedAt],
            )
          }
      }
    } catch (e: Exception) {
      log.error("Failed to list tokens for user: {}: {}", user, e.message, e)
      emptyList()
    }
  }

  override fun logoutIfMatching(accessToken: String, user: User): Boolean {
    if (accessToken.isBlank()) return false
    val id = userId(user)
    return try {
      val deleted = transaction(facet.database) {
        AccessTokensTable.deleteWhere {
          (AccessTokensTable.token eq accessToken) and (AccessTokensTable.userId eq id)
        }
      }
      if (deleted > 0) {
        cache.remove(accessToken)
        true
      } else {
        // Either unknown, or it belongs to somebody else -- do not leak which.
        if (cache[accessToken]?.user?.let { userId(it) != id } == true) {
          log.warn("Logout attempted with a token belonging to a different user")
        }
        false
      }
    } catch (e: Exception) {
      log.error("Failed to terminate session for user: {}: {}", user, e.message, e)
      false
    }
  }

  override fun revokeAll(user: User): Int {
    val id = userId(user)
    return try {
      val deleted = transaction(facet.database) {
        AccessTokensTable.deleteWhere { AccessTokensTable.userId eq id }
      }
      cache.entries.removeIf { userId(it.value.user) == id }
      log.info("Revoked {} session(s) for user: {}", deleted, user)
      deleted
    } catch (e: Exception) {
      log.error("Failed to revoke sessions for user: {}: {}", user, e.message, e)
      throw e
    }
  }

  /**
   * Bulk-delete every session whose `expires_at` is in the past. Safe to call
   * periodically from a scheduler; expiry is also enforced lazily on read.
   *
   * @return the number of rows removed
   */
  fun purgeExpired(): Int {
    val now = Instant.now()
    return try {
      val deleted = transaction(facet.database) {
        AccessTokensTable.deleteWhere {
          AccessTokensTable.expiresAt.isNotNull() and (AccessTokensTable.expiresAt less now)
        }
      }
      if (deleted > 0) {
        cache.entries.removeIf { it.value.expiresAt?.isBefore(now) == true }
        log.info("Purged {} expired session(s)", deleted)
      }
      deleted
    } catch (e: Exception) {
      log.error("Failed to purge expired sessions: {}", e.message, e)
      0
    }
  }

  /** Drop the cached copy of [accessToken], forcing the next read to hit the database. */
  fun invalidate(accessToken: String) {
    cache.remove(accessToken)
  }

  /** Drop every cached session (does not touch the database). */
  fun invalidateAll() {
    val size = cache.size
    cache.clear()
    log.debug("Invalidated all {} cached session entries", size)
  }

  /** Returns a snapshot of cache statistics: (hits, misses, size). */
  fun cacheStats(): Triple<Long, Long, Int> =
    Triple(cacheHits.get(), cacheMisses.get(), cache.size)

  /** Number of persisted sessions (including ones that have expired but not been purged). */
  fun sessionCount(): Int = try {
    transaction(facet.database) { AccessTokensTable.selectAll().count().toInt() }
  } catch (e: Exception) {
    log.error("Failed to count sessions: {}", e.message, e)
    0
  }

  /**
   * Visible for testing: removes every session from the database and the cache.
   */
  internal fun clearAllSessions() {
    try {
      transaction(facet.database) { AccessTokensTable.deleteAll() }
    } catch (e: Exception) {
      log.warn("Failed to clear sessions: {}", e.message, e)
    } finally {
      cache.clear()
    }
  }

  private fun touch(accessToken: String, entry: Entry, now: Instant) {
    entry.lastUsedAt = now
    if (touchIntervalMillis <= 0) return
    val sinceMs = (System.nanoTime() - entry.lastPersistedNanos) / 1_000_000L
    if (sinceMs < touchIntervalMillis) return
    entry.lastPersistedNanos = System.nanoTime()
    try {
      transaction(facet.database) {
        AccessTokensTable.update({ AccessTokensTable.token eq accessToken }) {
          it[AccessTokensTable.lastUsedAt] = now
        }
      }
    } catch (e: Exception) {
      // Non-fatal: last_used_at is diagnostic metadata only.
      log.debug("Failed to update last_used_at for a session: {}", e.message, e)
    }
  }

  private fun loadFromDb(accessToken: String): Entry? = try {
    transaction(facet.database) {
      AccessTokensTable
        .selectAll()
        .where { AccessTokensTable.token eq accessToken }
        .limit(1)
        .firstOrNull()
        ?.let { row ->
          val user = try {
            fromJson<User>(row[AccessTokensTable.userJson], User::class.java)
          } catch (e: Throwable) {
            log.error("Failed to deserialize the user of a stored session; discarding it", e)
            null
          }
          user?.let {
            Entry(
              user = it,
              issuedAt = row[AccessTokensTable.issuedAt],
              expiresAt = row[AccessTokensTable.expiresAt],
              lastUsedAt = row[AccessTokensTable.lastUsedAt],
              lastPersistedNanos = System.nanoTime(),
            )
          }
        }
    }
  } catch (e: Exception) {
    log.error("Failed to load session: {}", e.message, e)
    null
  }

  private fun deleteToken(accessToken: String) {
    try {
      transaction(facet.database) {
        AccessTokensTable.deleteWhere { AccessTokensTable.token eq accessToken }
      }
    } catch (e: Exception) {
      log.debug("Failed to delete expired session: {}", e.message, e)
    }
  }

  private fun userId(user: User): String {
    val id = try {
      user.id
    } catch (e: Throwable) {
      log.debug("Could not read user.id for {}: {}", user, e.message, e)
      null
    }
    if (!id.isNullOrBlank()) return id
    val email = try {
      user.email
    } catch (e: Throwable) {
      log.debug("Could not read user.email for {}: {}", user, e.message, e)
      null
    }
    return email?.takeIf { it.isNotBlank() } ?: user.toString()
  }

  companion object {
    private val log = LoggerFactory.getLogger(AuthenticationDB::class.java)

    internal val facet by lazy {
      DatabaseFacet(
        name = "authentication",
        tables = listOf(AccessTokensTable),
      )
    }
  }
}