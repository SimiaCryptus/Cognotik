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
import com.simiacryptus.cognotik.models.APIProvider
import org.slf4j.event.Level
import java.io.BufferedOutputStream

interface UserSettingsInterface {

    data class ApiData(
        val key: String? = null,
        val baseUrl: String? = null,
        val provider: APIProvider? = null,
    ) {
        fun client(workPool: java.util.concurrent.ExecutorService) = provider?.getChatClient(
            key = key ?: "",
            base = baseUrl ?: "",
            workPool = workPool
        ) ?: throw IllegalStateException("Provider not set or invalid")

        fun models(
            logLevel: Level = Level.INFO,
            logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        ) = provider?.getChatModels()?.map {
            it.instance(
                key = key ?: "",
                base = baseUrl ?: provider.base ?: "",
                logLevel = logLevel,
                logStreams = logStreams
            )
        } ?: emptyList()
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
        @Deprecated("Use UserSettings constructor with MutableList parameters instead")
        constructor(
            apiKeys: Map<APIProvider, String>,
            apiBase: Map<APIProvider, String> = emptyMap(),
            localTools: List<String> = emptyList()
        ) : this(apiKeys.map {
            ApiData(
                key = it.value,
                baseUrl = apiBase[it.key] ?: it.key.base ?: "",
                provider = it.key
            )
        }.toMutableList(), localTools.map {
            ToolData(
                name = it
            )
        }.toMutableList())

        @get:JsonIgnore
        @get:Deprecated("Use this.apis instead")
        val apiKeys: Map<APIProvider, String>
            get() = apis.associate {
                it.provider!! to (it.key ?: "")
            }

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
                    mutableListOf<ApiData>()
                }
                val tools = if (node.has("tools")) {
                    p.codec.treeToValue(node.get("tools"), Array<ToolData>::class.java).toMutableList()
                } else {
                    mutableListOf<ToolData>()
                }
                val etc = if (node.has("etc")) {
                    p.codec.treeToValue(node.get("etc"), MutableMap::class.java) as MutableMap<String, Any>
                } else {
                    mutableMapOf<String, Any>()
                }
                return UserSettings(apis, tools, etc)
            }
            // Handle legacy format (apiKeys, apiBase, localTools)
            val apiKeys = if (node.has("apiKeys")) {
                p.codec.treeToValue(node.get("apiKeys"), Map::class.java) as Map<APIProvider, String>
            } else {
                emptyMap<APIProvider, String>()
            }
            val apiBase = if (node.has("apiBase")) {
                p.codec.treeToValue(node.get("apiBase"), Map::class.java) as Map<APIProvider, String>
            } else {
                emptyMap<APIProvider, String>()
            }
            val localTools = if (node.has("localTools")) {
                p.codec.treeToValue(node.get("localTools"), Array<String>::class.java).toList()
            } else {
                emptyList<String>()
            }
            return UserSettings(apiKeys, apiBase, localTools)
        }
    }

    fun getUserSettings(user: User): UserSettings

    fun updateUserSettings(user: User, settings: UserSettings)
}