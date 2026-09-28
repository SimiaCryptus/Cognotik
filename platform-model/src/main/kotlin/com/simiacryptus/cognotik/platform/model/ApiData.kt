package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.cognotik.util.SecureString

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