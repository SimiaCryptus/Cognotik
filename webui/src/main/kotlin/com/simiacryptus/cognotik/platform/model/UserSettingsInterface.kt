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
import com.simiacryptus.cognotik.chat.model.ChatModelsDeserializer
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.file.UserSettingsManager

interface UserSettingsInterface {
    fun getUserSettings(user: User = UserSettingsManager.defaultUser): UserSettings
    fun updateUserSettings(user: User, settings: UserSettings)
}
data class ToolData(
    val name: String? = null,
    val description: String? = null,
    val command: String? = null,
)

@JsonSerialize(using = UserSettingsSerializer::class)
@JsonDeserialize(using = UserSettingsDeserializer::class)
data class UserSettings(
    val apis: MutableList<ApiData> = mutableListOf(),
    val tools: MutableList<ToolData> = mutableListOf(),
    val etc: MutableMap<String, Any> = mutableMapOf(),
) {

    @get:JsonIgnore
    @get:Deprecated("Use this.apis instead")
    val apiBase: Map<APIProvider, String>
        get() = apis.associate {
            it.provider!! to (it.baseUrl ?: "")
        }

    @get:JsonIgnore
    @get:Deprecated("Use this.tools instead")
    val localTools: List<String> = tools.mapNotNull { it.name }

}

class UserSettingsSerializer : JsonSerializer<UserSettings>() {
    override fun serialize(value: UserSettings, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeObjectField("apis", value.apis)
        gen.writeObjectField("tools", value.tools)
        gen.writeObjectField("etc", value.etc)
        gen.writeEndObject()
    }
}

class UserSettingsDeserializer : JsonDeserializer<UserSettings>() {
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
            (p.codec.treeToValue(node.get("apiKeys"), Map::class.java) as Map<String, String>).mapKeys { APIProvider.valueOf(it.key) }
        } else {
            emptyMap()
        }
        val apiBase = if (node.has("apiBase")) {
            (p.codec.treeToValue(node.get("apiBase"), Map::class.java) as Map<String, String>).mapKeys { APIProvider.valueOf(it.key) }
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
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ApiChatModel {
        return when (p.currentToken) {
            com.fasterxml.jackson.core.JsonToken.VALUE_STRING -> {
                // Handle string format - find model by name/key
                val modelName = p.readValueAs(String::class.java)
                val model = ChatModel.values().entries.find { 
                    it.key == modelName || it.value.name == modelName || it.value.modelName == modelName 
                }?.value ?: throw IllegalArgumentException("Unknown model: $modelName")
                ApiChatModel(model, null)
            }
            com.fasterxml.jackson.core.JsonToken.START_OBJECT -> {
                // Handle object format
                val node = p.readValueAsTree<ObjectNode>()
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
                    throw IllegalArgumentException("Invalid ApiChatModel object format")
                }
            }
            else -> throw IllegalArgumentException("ApiChatModel must be deserialized from either a string or an object")
        }
    }

}



data class ApiData(
    val name: String? = null,
    val key: String = "",
    val baseUrl: String = "",
    val provider: APIProvider? = null,
) {
    fun client(workPool: java.util.concurrent.ExecutorService) = provider?.getChatClient(
        key = key,
        base = baseUrl,
        workPool = workPool
    ) ?: throw IllegalStateException("Provider not set or invalid")

    fun validate(): ApiData {
        if (provider == null) throw IllegalStateException("Provider not set or invalid")
        if (key.isBlank()) throw IllegalStateException("API key not set")
        val model = ChatModel.values().values.firstOrNull { it.provider == provider }
        if (model == null) throw IllegalStateException("No chat model available for provider $provider")
        return this
    }
}

@JsonDeserialize(using = ApiChatModelDeserializer::class)
data class ApiChatModel(
    val model: ChatModel? = null,
    val provider: ApiData? = null,
)


fun toApiList(
    apiKeys: Map<APIProvider, String>,
    apiBase: Map<APIProvider, String>
): MutableList<ApiData> = apiKeys.map {
    ApiData(
        key = it.value,
        baseUrl = apiBase[it.key] ?: it.key.base ?: "",
        provider = it.key
    ).validate()
}.toMutableList()

fun toTools(localTools: List<String>): MutableList<ToolData> =
    localTools.map { ToolData(it) }.toMutableList()