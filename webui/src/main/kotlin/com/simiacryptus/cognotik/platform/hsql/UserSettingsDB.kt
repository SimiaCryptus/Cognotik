package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.toJson
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap

/**
 * HSQL-backed implementation of [UserSettingsInterface].
 *
 * Settings are stored as a single JSON blob per user in the `user_settings`
 * table, managed by its own [DatabaseFacet] (separate logical DB from metadata).
 */
open class UserSettingsDB : UserSettingsInterface {


    private val cache = ConcurrentHashMap<User, UserSettings>()

    override fun getUserSettings(user: User): UserSettings {
        log.debug("Retrieving user settings for user: {}", user)
        cache[user]?.let { return it }
        return synchronized(cache) {
            cache[user]?.let { return@synchronized it }
            val loaded = loadFromDb(user) ?: UserSettings()
            cache[user] = loaded
            loaded
        }
    }

    override fun updateUserSettings(user: User, settings: UserSettings) {
        log.debug("Updating user settings for user: {}", user)
        try {
            val prev = loadFromDb(user)
            val merged = if (prev != null) {
                settings.copy(
                    passwordHash = settings.passwordHash?.ifBlank { null } ?: prev.passwordHash
                )
            } else {
                settings
            }
            writeToDb(user, merged)
            cache[user] = merged
            log.info("Successfully updated user settings for user: {}", user)
        } catch (e: Exception) {
            log.error("Failed to write user settings for user: {}", user, e)
            throw e
        }
    }

    private fun loadFromDb(user: User): UserSettings? {
        return facet.withConnection() { conn ->
            conn.prepareStatement(
                "SELECT settings_json FROM user_settings WHERE user_key = ?"
            ).use { stmt ->
                stmt.setString(1, userKey(user))
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        try {
                            val json = rs.getString("settings_json")
                            fromJson<UserSettings>(json, UserSettings::class.java)
                        } catch (e: Throwable) {
                            log.error("Failed to deserialize user settings for user: {}", user, e)
                            null
                        }
                    } else null
                }
            }
        }
    }

    private fun writeToDb(user: User, settings: UserSettings) {
        facet.withConnection() { conn ->
             upsertUserSettings(conn, userKey(user), settings.toJson(), Timestamp(System.currentTimeMillis()))
        }
    }
     /**
      * Portable upsert that works on both HSQL and PostgreSQL.
      * Performs an UPDATE first; if no rows are affected, performs an INSERT.
      * This avoids vendor-specific MERGE/ON CONFLICT syntax differences.
      */
     private fun upsertUserSettings(
         conn: java.sql.Connection,
         key: String,
         json: String,
         ts: Timestamp
     ) {
         val updated = conn.prepareStatement(
             "UPDATE user_settings SET settings_json = ?, timestamp = ? WHERE user_key = ?"
         ).use { stmt ->
             stmt.setString(1, json)
             stmt.setTimestamp(2, ts)
             stmt.setString(3, key)
             stmt.executeUpdate()
         }
         if (updated == 0) {
             try {
                 conn.prepareStatement(
                     "INSERT INTO user_settings (user_key, settings_json, timestamp) VALUES (?, ?, ?)"
                 ).use { stmt ->
                     stmt.setString(1, key)
                     stmt.setString(2, json)
                     stmt.setTimestamp(3, ts)
                     stmt.executeUpdate()
                 }
             } catch (e: java.sql.SQLException) {
                 // Race condition: another writer inserted between our UPDATE and INSERT.
                 // Retry the UPDATE once.
                 conn.prepareStatement(
                     "UPDATE user_settings SET settings_json = ?, timestamp = ? WHERE user_key = ?"
                 ).use { stmt ->
                     stmt.setString(1, json)
                     stmt.setTimestamp(2, ts)
                     stmt.setString(3, key)
                     stmt.executeUpdate()
                 }
             }
         }
     }

    private fun userKey(user: User): String {
        val email = try {
            user.email
        } catch (_: Throwable) {
            null
        }
        return email?.takeIf { it.isNotBlank() } ?: user.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserSettingsDB::class.java)

        internal val facet = DatabaseFacet(
            name = "user_settings",
            schema = {
                listOf(
                    """
                    CREATE TABLE IF NOT EXISTS user_settings (
                        user_key VARCHAR(255) PRIMARY KEY,
                        settings_json LONGVARCHAR,
                        timestamp TIMESTAMP
                    )
                    """
                )
            }        )
    }
}