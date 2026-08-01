package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.toJson
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * HSQL-backed implementation of [UserSettingsInterface] using Exposed DSL.
  *
  * Maintains an on-heap cache of [UserSettings] keyed by [User] to avoid
  * round-tripping to the database on every read. Cache entries have a
  * configurable TTL (default 5 minutes) after which they are reloaded from
  * the database on next access. Writes update the cache atomically with the
  * underlying database row so subsequent reads observe the new value.
 */
open class UserSettingsDB : UserSettingsInterface {

    /**
     * Exposed table definition for user settings.
     */
    object UserSettingsTable : Table("user_settings") {
        val userKey = varchar("user_key", 255)
        val settingsJson = text("settings_json")
        val timestamp = timestamp("timestamp")
        override val primaryKey = PrimaryKey(userKey)
    }

     /**
      * Cache entry pairing a value with the wall-clock time it was loaded so
      * we can expire it after [cacheTtlMillis].
      */
     private data class CacheEntry(val value: UserSettings, val loadedAtNanos: Long)

     private val cache = ConcurrentHashMap<User, CacheEntry>()

     /** Cache statistics, exposed for diagnostics/testing. */
     private val cacheHits = AtomicLong(0)
     private val cacheMisses = AtomicLong(0)

     /** TTL for cache entries in milliseconds. Override via system property. */
     private val cacheTtlMillis: Long =
         System.getProperty("cognotik.userSettings.cacheTtlMillis", "300000").toLong()

     private fun isFresh(entry: CacheEntry): Boolean {
         if (cacheTtlMillis <= 0) return true
         val ageMs = (System.nanoTime() - entry.loadedAtNanos) / 1_000_000L
         return ageMs < cacheTtlMillis
     }

    override fun  getUserSettings(user: User): UserSettings {
        log.debug("Retrieving user settings for user: {}", user)
         cache[user]?.let { entry ->
             if (isFresh(entry)) {
                 cacheHits.incrementAndGet()
                 log.debug("Cache hit for user: {}", user)
                 return entry.value
             } else {
                 log.debug("Cache entry expired for user: {}; reloading", user)
                 cache.remove(user, entry)
             }
         }
        return synchronized(cache) {
             cache[user]?.let { entry ->
                 if (isFresh(entry)) {
                     cacheHits.incrementAndGet()
                     return@synchronized entry.value
                 }
             }
             cacheMisses.incrementAndGet()
            val loaded = loadFromDb(user) ?: UserSettings()
             cache[user] = CacheEntry(loaded, System.nanoTime())
            loaded
        }
    }

    override fun updateUserSettings(user: User, settings: UserSettings) {
        log.debug("Updating user settings for user: {}", user)
        try {
            // Merge and write inside a single transaction so concurrent writers
            // cannot interleave between the read and the write.
            val merged = transaction(facet.database) {
                val prev = UserSettingsTable
                    .selectAll()
                    .where { UserSettingsTable.userKey eq userKey(user) }
                    .limit(1)
                    .firstOrNull()
                    ?.let { row ->
                        try {
                            fromJson<UserSettings>(row[UserSettingsTable.settingsJson], UserSettings::class.java)
                        } catch (e: Throwable) {
                            log.error(
                                "Failed to deserialize existing user settings for user: {}; existing settings will be discarded",
                                user, e
                            )
                            null
                        }
                    }
                val computed = if (prev != null) {
                    settings.copy(
                        passwordHash = settings.passwordHash?.ifBlank { null } ?: prev.passwordHash
                    )
                } else {
                    settings
                }
                val key = userKey(user)
                val json = computed.toJson()
                val now = Instant.now()
                val updated = UserSettingsTable.update({ UserSettingsTable.userKey eq key }) {
                    it[settingsJson] = json
                    it[timestamp] = now
                }
                if (updated == 0) {
                    try {
                        UserSettingsTable.insert {
                            it[userKey] = key
                            it[settingsJson] = json
                            it[timestamp] = now
                        }
                    } catch (e: java.sql.SQLException) {
                        // Race: another writer inserted between our UPDATE and INSERT. Retry UPDATE.
                        log.debug(
                            "Insert race detected for user_settings (user={}); retrying update: {}",
                            user, e.message
                        )
                        val retried = UserSettingsTable.update({ UserSettingsTable.userKey eq key }) {
                            it[settingsJson] = json
                            it[timestamp] = now
                        }
                        if (retried == 0) {
                            log.error(
                                "Failed to upsert user settings after insert race for user: {}",
                                user, e
                            )
                            throw e
                        }
                    }
                }
                computed
            }
             cache[user] = CacheEntry(merged, System.nanoTime())
            log.debug("Successfully updated user settings for user: {}", user)
        } catch (e: Exception) {
             // On failure, invalidate cache so we don't serve a stale value
             // that disagrees with the database.
             cache.remove(user)
            log.error("Failed to write user settings for user: {}: {}", user, e.message, e)
            throw e
        }
    }
     /**
      * Invalidate the cached entry for [user], forcing the next read to
      * reload from the database. Useful when external processes may have
      * modified the row out-of-band.
      */
     fun invalidate(user: User) {
         cache.remove(user)
         log.debug("Invalidated cache entry for user: {}", user)
     }
     /** Clear all cached entries. */
     fun invalidateAll() {
         val size = cache.size
         cache.clear()
         log.debug("Invalidated all {} cached user settings entries", size)
     }
     /** Returns a snapshot of cache statistics: (hits, misses, size). */
     fun cacheStats(): Triple<Long, Long, Int> =
         Triple(cacheHits.get(), cacheMisses.get(), cache.size)

    private fun loadFromDb(user: User): UserSettings? {
        val key = userKey(user)
        return try {
            transaction(facet.database) {
                UserSettingsTable
                    .selectAll()
                    .where { UserSettingsTable.userKey eq key }
                    .limit(1)
                    .firstOrNull()
                    ?.let { row ->
                        try {
                            fromJson<UserSettings>(row[UserSettingsTable.settingsJson], UserSettings::class.java)
                        } catch (e: Throwable) {
                            log.error(
                                "Failed to deserialize user settings for user: {}; returning defaults",
                                user, e
                            )
                            null
                        }
                    }
            }
        } catch (e: Exception) {
            log.error("Failed to load user settings for user: {}: {}", user, e.message, e)
            null
        }
    }


    private fun userKey(user: User): String {
        val email = try {
            user.email
        } catch (e: Throwable) {
            log.debug("Could not read user.email for {}: {}", user, e.message, e)
            null
        }
        return email?.takeIf { it.isNotBlank() } ?: user.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserSettingsDB::class.java)

        internal val facet = DatabaseFacet(
            name = "user_settings",
            tables = listOf(UserSettingsTable),
        )
    }
}