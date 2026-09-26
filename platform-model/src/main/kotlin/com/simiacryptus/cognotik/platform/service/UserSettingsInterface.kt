package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SecureString

/**
 * Interface for managing user-specific settings and configurations.
 * Provides methods to retrieve and update settings for individual users.
 */
interface UserSettingsInterface {
  /**
   * Retrieves the settings for a specific user.
   *
   * @param user The user whose settings should be retrieved. Defaults to UserSettingsManager.defaultUser
   * @return UserSettings object containing the user's configuration
   */
  fun getUserSettings(user: User): UserSettings

  /**
   * Updates the settings for a specific user.
   *
   * @param user The user whose settings should be updated
   * @param settings The new UserSettings object to save for the user
   */
  fun updateUserSettings(user: User, settings: UserSettings)
}


fun ChatModel.asApiChatModel(
  key: String
): ApiChatModel = ApiChatModel(
  provider = this.provider.let { provider ->
    ApiData(
      name = provider?.name,
      key = SecureString(key),
      baseUrl = provider?.base!!,
      provider = provider
    )
  },
  model = this,
)

