package com.simiacryptus.cognotik.platform.file

import com.google.gson.GsonBuilder
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.UserSettings
import com.simiacryptus.cognotik.platform.UserSettingsInterface
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.toJson
import org.slf4j.LoggerFactory.getLogger
import java.io.File

open class UserSettingsManager(val root: File) : UserSettingsInterface {

  init {
    require(root.exists() || root.mkdirs()) { "Failed to create root directory: $root" }
    log.info("Initializing UserSettingsManager with root directory: ${root}")
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
          val text = file.readText()
          val fromJson = fromJson<UserSettings>(text, UserSettings::class.java)
          return@getOrPut fromJson
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
    val file = File(userConfigDirectory, "$user.json")
    if (file.exists()) {
      log.warn("Updating existing user settings for user: {} at file: {}", user, file)
      val prevJson = fromJson<UserSettings>(file.readText(), UserSettings::class.java)
      val mergedJson = settings.copy(
        passwordHash = settings.passwordHash?.ifBlank { null } ?: prevJson.passwordHash
      )
      try {
        userSettings[user] = mergedJson
        file.writeText(mergedJson.toJson())
        log.info("Successfully updated user settings for user: {} at file: {}", user, file)
      } catch (e: Exception) {
        log.error("Failed to write user settings for user: {} to file: {}", user, file, e)
      }
    } else {
      log.info("Creating new user settings file for user: {} at file: {}", user, file)
      file.parentFile.mkdirs()
      try {
        userSettings[user] = settings
        file.writeText(settings.toJson())
        log.info("Successfully created user settings for user: {} at file: {}", user, file)
      } catch (e: Exception) {
        log.error("Failed to write user settings for user: {} to file: {}", user, file, e)
      }
    }
  }

  companion object {
    private val log = getLogger(UserSettingsManager::class.java)

    fun merge_gson(prevJson: String, newJson: String): String {
      val prev: com.google.gson.JsonObject =
        GsonBuilder().create().fromJson(prevJson, com.google.gson.JsonObject::class.java)
      val new: com.google.gson.JsonObject =
        GsonBuilder().create().fromJson(newJson, com.google.gson.JsonObject::class.java)
      val gson = GsonBuilder().setPrettyPrinting().create()
      val merged = com.google.gson.JsonObject()
      val keys = HashSet<String>()
      prev.entrySet().forEach { keys.add(it.key) }
      new.entrySet().forEach { keys.add(it.key) }
      keys.forEach { key ->
        val prevValue = prev.get(key)
        val newValue = new.get(key)
        if (prevValue != null && newValue != null) {
          if (prevValue.isJsonObject && newValue.isJsonObject) {
            merged.add(
              key,
              merge_gson(gson.toJson(prevValue), gson.toJson(newValue)).let {
                gson.fromJson(
                  it,
                  com.google.gson.JsonObject::class.java
                )
              })
          } else {
            merged.add(key, newValue)
          }
        } else if (newValue != null) {
          merged.add(key, newValue)
        } else if (prevValue != null) {
          merged.add(key, prevValue)
        }
      }
      return gson.toJson(merged)
    }

  }

}