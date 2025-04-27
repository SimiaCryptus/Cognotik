package com.simiacryptus.cognotik.platform.test

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.jopenai.models.APIProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

abstract class UserSettingsTest(private val userSettings: UserSettingsInterface) {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(UserSettingsTest::class.java)
    }

    @Test
    fun `updateUserSettings should store custom settings for user`() {
        log.info("Starting test: updateUserSettings should store custom settings for user")
        val id = UUID.randomUUID().toString()
        val testUser = User(
            email = "$id@example.com",
            name = "Test User",
            id = id
        )
        log.debug("Created test user with id: {}", id)

        val newSettings = UserSettingsInterface.UserSettings(apiKeys = mapOf(APIProvider.OpenAI to "12345"))
        log.debug("Updating user settings with new API key")
        userSettings.updateUserSettings(testUser, newSettings)

        val settings = userSettings.getUserSettings(testUser)
        log.debug("Retrieved user settings after update")

        Assertions.assertEquals("12345", settings.apiKeys[APIProvider.OpenAI])
        log.info("Test completed: updateUserSettings successfully stored custom settings for user")
    }

    @Test
    fun `getUserSettings should return updated settings after updateUserSettings is called`() {
        log.info("Starting test: getUserSettings should return updated settings after updateUserSettings is called")
        val id = UUID.randomUUID().toString()
        val testUser = User(
            email = "$id@example.com",
            name = "Test User",
            id = id
        )
        log.debug("Created test user with id: {}", id)

        val initialSettings = userSettings.getUserSettings(testUser)
        log.debug("Retrieved initial user settings")
        Assertions.assertEquals("", initialSettings.apiKeys[APIProvider.OpenAI])

        val updatedSettings = UserSettingsInterface.UserSettings(apiKeys = mapOf(APIProvider.OpenAI to "67890"))
        log.debug("Updating user settings with new API key")
        userSettings.updateUserSettings(testUser, updatedSettings)

        val settingsAfterUpdate = userSettings.getUserSettings(testUser)
        log.debug("Retrieved user settings after update")

        Assertions.assertEquals("67890", settingsAfterUpdate.apiKeys[APIProvider.OpenAI])
        log.info("Test completed: getUserSettings successfully returned updated settings after updateUserSettings was called")
    }
}