package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.jopenai.models.APIProvider

interface UserSettingsInterface {

    data class ApiData(
        val key: String? = null,
        val baseUrl: String? = null,
        val provider: APIProvider? = null,
    )

    data class ToolData(
        val name: String? = null,
        val description: String? = null,
        val command: String? = null,
    )

    class UserSettings(
        val apis: MutableList<ApiData> = mutableListOf(),
        val tools: MutableList<ToolData> = mutableListOf(),
        val etc: MutableMap<String, Any> = mutableMapOf(),
    ) {
        @Deprecated("Use UserSettings constructor with MutableList parameters instead")
        constructor(
            apiKeys: Map<APIProvider, String> = emptyMap(),
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

    fun getUserSettings(user: User): UserSettings

    fun updateUserSettings(user: User, settings: UserSettings)
}