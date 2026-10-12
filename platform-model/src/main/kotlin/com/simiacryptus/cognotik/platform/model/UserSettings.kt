package com.simiacryptus.cognotik.platform.model

/**
 * Container for all user-specific settings and configurations.
 * Supports both new format (apis/tools) and legacy format (apiKeys/apiBase/localTools) for backward compatibility.
 *
 * @property apis List of API configurations for various providers (OpenAI, Anthropic, etc.)
 * @property tools List of custom tools/commands available to the user
 * @property toolPaths Map of tool providers to their executable paths
 */
data class UserSettings(
  val user: User,
  val apis: MutableList<ApiData> = mutableListOf(),
  val collectSessionData: Boolean = false,
  val passwordHash: String? = null,
  val internalToken: String? = null,
  val smartModel: String? = null,
  val fastModel: String? = null,
)