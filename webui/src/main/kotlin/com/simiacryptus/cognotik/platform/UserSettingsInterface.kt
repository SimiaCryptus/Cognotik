package com.simiacryptus.cognotik.platform

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
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


/**
 * Container for all user-specific settings and configurations.
 * Supports both new format (apis/tools) and legacy format (apiKeys/apiBase/localTools) for backward compatibility.
 *
 * @property apis List of API configurations for various providers (OpenAI, Anthropic, etc.)
 * @property tools List of custom tools/commands available to the user
 * @property toolPaths Map of tool providers to their executable paths
 */
data class UserSettings(
  val apis: MutableList<ApiData> = mutableListOf(),
  val collectSessionData: Boolean = false,
  val passwordHash: String? = null,
  val smartModel: String? = null,
  val fastModel: String? = null,
)

/**
 * Configuration data for an API provider.
 * Contains all necessary information to connect to and authenticate with an API service.
 *
 * @property name Optional display name for this API configuration
 * @property key API key or authentication token for the provider
 * @property baseUrl Base URL for the API endpoint (can override provider's default)
 * @property provider The API provider type (OpenAI, Anthropic, Google, etc.)
 */
data class ApiData(
  val name: String? = null,
  val key: SecureString? = null,
  val baseUrl: String? = null,
  val provider: APIProvider? = null,
) {
  @get:JsonIgnore
  val apiBase
    get() = when {
      !baseUrl.isNullOrBlank() -> baseUrl
      else -> provider?.base?.ifBlank { null }
    } ?: throw RuntimeException("Cannot get api base for $name")

  /**
   * Validates this API configuration.
   * Checks that provider is set, API key is not blank, and for chat-capable providers,
   * ensures at least one chat model is available.
   *
   * @return This ApiData instance if validation passes
   * @throws IllegalStateException if validation fails
   */
  fun validate(): ApiData {
    if (provider == null) throw IllegalStateException("Provider not set or invalid")
    if (key == null) throw IllegalStateException("API key not set")
    return this
  }
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

/**
 * Represents a chat model with its associated API provider configuration.
 *
 * @property model The chat model to use
 * @property provider Optional API configuration to use with this model (overrides default)
 */
data class ApiChatModel(
  val model: ChatModel? = null,
  val provider: ApiData? = null,
)

/**
 * Converts legacy API configuration format to the new ApiData list format.
 * Used for backward compatibility when migrating from old settings format.
 *
 * @param apiKeys Map of API providers to their authentication keys
 * @param apiBase Map of API providers to their base URLs
 * @return MutableList of ApiData objects representing the converted configuration
 */
fun toApiList(
  apiKeys: Map<APIProvider, String>, apiBase: Map<APIProvider, String>
): MutableList<ApiData> = apiKeys.map {
  ApiData(
    key = SecureString(it.value), baseUrl = apiBase[it.key] ?: it.key.base, provider = it.key
  ).validate()
}.toMutableList()