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

/**
 * HSQL-backed implementation of [UserSettingsInterface] using Exposed DSL.
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
            cache[user] = merged
            log.debug("Successfully updated user settings for user: {}", user)
        } catch (e: Exception) {
            log.error("Failed to write user settings for user: {}: {}", user, e.message, e)
            throw e
        }
    }

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

    private fun writeToDb(user: User, settings: UserSettings) {
        val key = userKey(user)
        val json = settings.toJson()
        val now = Instant.now()
        transaction(facet.database) {
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