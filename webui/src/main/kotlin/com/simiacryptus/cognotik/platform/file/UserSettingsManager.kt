package com.simiacryptus.cognotik.platform.file

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.util.JsonUtil
import java.io.File

open class UserSettingsManager(val root: File) : UserSettingsInterface {

    init {
        require(root.exists() || root.mkdirs()) { "Failed to create root directory: $root" }
        log.info("Initializing UserSettingsManager with root directory: ${root}", RuntimeException())
    }

    private val userSettings = HashMap<User, UserSettings>()
    private val userConfigDirectory by lazy { root.apply { mkdirs() } }

    override fun getUserSettings(user: User): UserSettings {
        log.debug("Retrieving user settings for user: {}", user)
        return userSettings.getOrPut(user) {
            val file = File(userConfigDirectory, "$user.json")
            if (file.exists()) {
                try {
                    log.info("Loading existing user settings for user: {} from file: {}", user, file)
                    return@getOrPut JsonUtil.fromJson(file.readText(), UserSettings::class.java)
                } catch (e: Throwable) {
                    log.error("Failed to load user settings for user: {} from file: {}.", user, file, e)
                }
            }
            log.info("User settings file not found for user: {}. Creating new settings at: {}", user, file)
            return@getOrPut UserSettings()
        }
    }

    override fun updateUserSettings(user: User, settings: UserSettings) {
        log.debug("Updating user settings for user: {}", user)
        userSettings[user] = settings
        val file = File(userConfigDirectory, "$user.json")
        file.parentFile.mkdirs()
        try {
            file.writeText(JsonUtil.toJson(settings))
            log.info("Successfully updated user settings for user: {} at file: {}", user, file)
        } catch (e: Exception) {
            log.error("Failed to write user settings for user: {} to file: {}", user, file, e)
        }
    }

    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(UserSettingsManager::class.java)

        @JsonIgnore
        @JvmStatic
        var defaultUser = User(
            id = "1",
            email = "user@localhost"
        )
    }

}