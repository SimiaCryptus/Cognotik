package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.file.UserSettingsManager

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
  fun getUserSettings(user: User = UserSettingsManager.defaultUser): UserSettings

  /**
   * Updates the settings for a specific user.
   *
   * @param user The user whose settings should be updated
   * @param settings The new UserSettings object to save for the user
   */
  fun updateUserSettings(user: User, settings: UserSettings)
}

/**
 * Represents configuration data for a tool/command that can be executed.
 *
 * @property name The display name of the tool
 * @property description A human-readable description of what the tool does
 * @property command The actual command or script to execute when the tool is invoked
 */
data class ToolData(
  val name: String? = null,
  val description: String? = null,
  val command: String? = null,
)

/**
 * Container for all user-specific settings and configurations.
 * Supports both new format (apis/tools) and legacy format (apiKeys/apiBase/localTools) for backward compatibility.
 *
 * @property apis List of API configurations for various providers (OpenAI, Anthropic, etc.)
 * @property tools List of custom tools/commands available to the user
 * @property etc Additional miscellaneous settings stored as key-value pairs
 */
@JsonSerialize(using = UserSettingsSerializer::class)
@JsonDeserialize(using = UserSettingsDeserializer::class)
data class UserSettings(
  val apis: MutableList<ApiData> = mutableListOf(),
  val tools: MutableList<ToolData> = mutableListOf(),
  val etc: MutableMap<String, Any> = mutableMapOf(),
) {

  /**
   * @deprecated Use the 'apis' property instead. This provides backward compatibility
   * for legacy code expecting a Map of APIProvider to base URL.
   * @return Map of API providers to their base URLs extracted from the apis list
   */
  @get:JsonIgnore
  @get:Deprecated("Use this.apis instead")
  val apiBase: Map<APIProvider, String>
    get() = apis.associate {
      it.provider!! to (it.baseUrl ?: "")
    }

  /**
   * @deprecated Use the 'tools' property instead. This provides backward compatibility
   * for legacy code expecting a simple list of tool names.
   * @return List of tool names extracted from the tools list
   */
  @get:JsonIgnore
  @get:Deprecated("Use this.tools instead")
  val localTools: List<String> = tools.mapNotNull { it.name }

}

/**
 * Custom JSON serializer for UserSettings.
 * Serializes UserSettings to JSON format with apis, tools, and etc fields.
 */
class UserSettingsSerializer : JsonSerializer<UserSettings>() {
  /**
   * Custom JSON deserializer for UserSettings.
   * Handles both new format (apis/tools/etc) and legacy format (apiKeys/apiBase/localTools)
   * for backward compatibility with existing user configuration files.
   */
  override fun serialize(value: UserSettings, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeStartObject()
    gen.writeObjectField("apis", value.apis)
    gen.writeObjectField("tools", value.tools)
    gen.writeObjectField("etc", value.etc)
    gen.writeEndObject()
  }
}

class UserSettingsDeserializer : JsonDeserializer<UserSettings>() {
  /**
   * Custom JSON deserializer for ApiChatModel.
   * Handles deserialization from both string format (model name) and object format
   * (containing model and provider information).
   */
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UserSettings {
    val node = p.readValueAsTree<ObjectNode>()
    // Check if this is the new format (has apis/tools fields)
    if (node.has("apis") || node.has("tools")) {
      val apis = if (node.has("apis")) {
        p.codec.treeToValue(node.get("apis"), Array<ApiData>::class.java).toMutableList()
      } else {
        mutableListOf()
      }
      val tools = if (node.has("tools")) {
        p.codec.treeToValue(node.get("tools"), Array<ToolData>::class.java).toMutableList()
      } else {
        mutableListOf()
      }
      val etc = if (node.has("etc")) {
        p.codec.treeToValue(node.get("etc"), MutableMap::class.java) as MutableMap<String, Any>
      } else {
        mutableMapOf()
      }
      return UserSettings(apis, tools, etc)
    }
    // Handle legacy format (apiKeys, apiBase, localTools)
    val apiKeys = if (node.has("apiKeys")) {
      (p.codec.treeToValue(
        node.get("apiKeys"),
        Map::class.java
      ) as Map<String, String>).mapKeys { APIProvider.valueOf(it.key) }
    } else {
      emptyMap()
    }
    val apiBase = if (node.has("apiBase")) {
      (p.codec.treeToValue(
        node.get("apiBase"),
        Map::class.java
      ) as Map<String, String>).mapKeys { APIProvider.valueOf(it.key) }
    } else {
      emptyMap()
    }
    val localTools = if (node.has("localTools")) {
      p.codec.treeToValue(node.get("localTools"), Array<String>::class.java).toList()
    } else {
      emptyList()
    }
    return UserSettings(toApiList(apiKeys, apiBase), toTools(localTools))
  }
}

class ApiChatModelDeserializer : JsonDeserializer<ApiChatModel>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ApiChatModel? {
    return when (p.currentToken) {
      com.fasterxml.jackson.core.JsonToken.VALUE_STRING -> {
        try {
          val modelName = p.readValueAs(String::class.java)
          // Handle string format - find model by name/key
          val model = ChatModel.values().entries.find {
            it.key == modelName || it.value.name == modelName || it.value.modelName == modelName
          }?.value ?: throw IllegalArgumentException("Unknown model: $modelName")
          ApiChatModel(model, null)
        } catch (e: Exception) {
          throw IllegalArgumentException("Error deserializing ApiChatModel: ${e.message}", e)
        }
      }

      com.fasterxml.jackson.core.JsonToken.START_OBJECT -> {
        // Handle object format
        val node = p.readValueAsTree<ObjectNode>()
        try {
          if (node.has("model") && node.has("provider")) {
            val model = p.codec.treeToValue(node.get("model"), ChatModel::class.java)
            val provider = p.codec.treeToValue(node.get("provider"), ApiData::class.java)
            ApiChatModel(model, provider)
          } else if (node.has("modelName")) {
            val modelName = node.get("modelName").asText()
            val model = ChatModel.values().values.firstOrNull { it.modelName == modelName }
              ?: throw IllegalArgumentException("Unknown model: $modelName")
            ApiChatModel(model, null)
          } else {
            //throw IllegalArgumentException("Invalid ApiChatModel object format")
            null
          }
        } catch (e: Exception) {
          throw IllegalArgumentException("Error deserializing ApiChatModel: ${e.message}", e)
        }
      }

      else -> null // throw IllegalArgumentException("ApiChatModel must be deserialized from either a string or an object")
    }
  }
}


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
  val key: String? = null,
  val baseUrl: String = "",
  val provider: APIProvider? = null,
) {
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
    // Only validate chat models for providers that support chat functionality
    val supportsChatModels = provider.getChatModels(key, baseUrl).isNotEmpty()
    if (supportsChatModels) {
      val model = ChatModel.values().values.firstOrNull { it.provider == provider }
      if (model == null) {
        throw IllegalStateException("No chat model available for provider $provider")
      }
    }
    return this
  }
}

/**
 * Represents a chat model with its associated API provider configuration.
 *
 * @property model The chat model to use
 * @property provider Optional API configuration to use with this model (overrides default)
 */
@JsonDeserialize(using = ApiChatModelDeserializer::class)
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
    key = it.value, baseUrl = apiBase[it.key] ?: it.key.base, provider = it.key
  ).validate()
}.toMutableList()

/**
 * Converts a simple list of tool names to ToolData objects.
 * Used for backward compatibility when migrating from old settings format.
 *
 * @param localTools List of tool names
 * @return MutableList of ToolData objects with the given names
 */
fun toTools(localTools: List<String>): MutableList<ToolData> = localTools.map { ToolData(it) }.toMutableList()