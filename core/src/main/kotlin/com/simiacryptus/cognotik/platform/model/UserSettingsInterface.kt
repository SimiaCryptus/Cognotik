package com.simiacryptus.cognotik.platform.model

import com.simiacryptus.jopenai.models.APIProvider

interface UserSettingsInterface {
  data class UserSettings(
    val apiKeys: Map<APIProvider, String> = APIProvider.Companion.values().associateWith { "" },
    val apiBase: Map<APIProvider, String> = APIProvider.Companion.values().associateWith { it.base ?: "" },
  )

  fun getUserSettings(user: User): UserSettings

  fun updateUserSettings(user: User, settings: UserSettings)
}