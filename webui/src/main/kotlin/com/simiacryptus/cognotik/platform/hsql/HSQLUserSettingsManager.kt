package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.toJson
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap

/**
 * HSQL-backed implementation of [UserSettingsInterface].
 *
 * Settings are stored as a single JSON blob per user in the `user_settings`
 * table. The user_settings schema lives alongside the metadata facet so it
 * shares the same physical database/connection as [HSQLMetadataStorage].
 */
open class HSQLUserSettingsManager(private val root: File? = null) : UserSettingsInterface {

    init {
        HSQLUtils.ensureRoot(root)
        log.info("Initializing HSQLUserSettingsManager with root directory: {}", root)
        ensureSchema(connection)
    }

    private val connection: Connection get() = HSQLMetadataStorage.getConn(root)

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
        val prev = loadFromDb(user)
        val merged = if (prev != null) {
            settings.copy(
                passwordHash = settings.passwordHash?.ifBlank { null } ?: prev.passwordHash
            )
        } else {
            settings
        }
        try {
            writeToDb(user, merged)
            cache[user] = merged
            log.info("Successfully updated user settings for user: {}", user)
        } catch (e: Exception) {
            log.error("Failed to write user settings for user: {}", user, e)
        }
    }

    private fun loadFromDb(user: User): UserSettings? {
        val statement = connection.prepareStatement(
            "SELECT settings_json FROM user_settings WHERE user_key = ?"
        )
        statement.setString(1, userKey(user))
        val rs = statement.executeQuery()
        return if (rs.next()) {
            try {
                val json = rs.getString("settings_json")
                fromJson<UserSettings>(json, UserSettings::class.java)
            } catch (e: Throwable) {
                log.error("Failed to deserialize user settings for user: {}", user, e)
                null
            }
        } else {
            null
        }
    }

    private fun writeToDb(user: User, settings: UserSettings) {
        val statement = connection.prepareStatement(
            """
            MERGE INTO user_settings USING (VALUES(?, ?, ?)) AS vals(user_key, settings_json, timestamp)
            ON user_settings.user_key = vals.user_key
            WHEN MATCHED THEN UPDATE SET user_settings.settings_json = vals.settings_json, user_settings.timestamp = vals.timestamp
            WHEN NOT MATCHED THEN INSERT VALUES vals.user_key, vals.settings_json, vals.timestamp
            """
        )
        statement.setString(1, userKey(user))
        statement.setString(2, settings.toJson())
        statement.setTimestamp(3, Timestamp(System.currentTimeMillis()))
        statement.executeUpdate()
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

        @Volatile
        private var schemaInitialized: Boolean = false

        private fun ensureSchema(connection: Connection) {
            if (schemaInitialized) return
            synchronized(HSQLUserSettingsManager::class.java) {
                if (schemaInitialized) return
                log.debug("Creating user_settings schema if not exists")
                connection.createStatement().executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS user_settings (
                        user_key VARCHAR(255) PRIMARY KEY,
                        settings_json LONGVARCHAR,
                        timestamp TIMESTAMP
                    )
                    """
                )
                schemaInitialized = true
            }
        }
    }
}