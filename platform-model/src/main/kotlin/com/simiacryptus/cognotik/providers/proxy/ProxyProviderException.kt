package com.simiacryptus.cognotik.providers.proxy

/**
  * Exception thrown when a proxy operation fails. Carries contextual information
  * such as the upstream provider, HTTP status code, and response body when available.
  */
class ProxyProviderException(
     message: String,
     val provider: String? = null,
     val statusCode: Int? = null,
     val responseBody: String? = null,
     cause: Throwable? = null
) : RuntimeException(message, cause)