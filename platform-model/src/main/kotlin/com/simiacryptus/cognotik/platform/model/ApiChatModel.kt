package com.simiacryptus.cognotik.platform.model

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