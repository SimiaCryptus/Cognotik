package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.toJson
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap

/**
 * HSQL-backed implementation of [UserSettingsInterface].
 *
 * Settings are stored as a single JSON blob per user in the `user_settings`
 * table, managed by its own [HSQLFacet] (separate logical DB from metadata).
 */
open class HSQLUserSettingsManager(private val root: File? = null) : UserSettingsInterface {

    init {
        require(root?.exists() != false || root.mkdirs()) { "Failed to create root directory: $root" }
        log.info("Initializing HSQLUserSettingsManager with root directory: {}", root)
    }

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
        return facet.withConnection(root) { conn ->
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
        facet.withConnection(root) { conn ->
            conn.prepareStatement(
                """
                    MERGE INTO user_settings USING (VALUES(?, ?, ?)) AS vals(user_key, settings_json, timestamp)
                    ON user_settings.user_key = vals.user_key
                    WHEN MATCHED THEN UPDATE SET user_settings.settings_json = vals.settings_json, user_settings.timestamp = vals.timestamp
                    WHEN NOT MATCHED THEN INSERT VALUES vals.user_key, vals.settings_json, vals.timestamp
                    """
            ).use { stmt ->
                stmt.setString(1, userKey(user))
                stmt.setString(2, settings.toJson())
                stmt.setTimestamp(3, Timestamp(System.currentTimeMillis()))
                stmt.executeUpdate()
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
        private val log = LoggerFactory.getLogger(HSQLUserSettingsManager::class.java)

        internal val facet = HSQLFacet(
            name = "user_settings",
            schemaSql = listOf(
                """
                    CREATE TABLE IF NOT EXISTS user_settings (
                        user_key VARCHAR(255) PRIMARY KEY,
                        settings_json LONGVARCHAR,
                        timestamp TIMESTAMP
                    )
                    """
            )
        )
    }
}