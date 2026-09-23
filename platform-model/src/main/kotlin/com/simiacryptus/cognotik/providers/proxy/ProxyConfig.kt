package com.simiacryptus.cognotik.providers.proxy

/**
 * Centralized configuration for proxy providers, all values overridable via
 * Java system properties.
 *
 * Properties:
 *  - cognotik.proxy.base                  Default proxy base URL.
 *  - cognotik.proxy.connectTimeoutMs      HTTP connect timeout in ms (default 30000).
 *  - cognotik.proxy.chatReadTimeoutMs     Overall deadline for async chat completion in ms (default 300000).
 *  - cognotik.proxy.modelsReadTimeoutMs   HTTP read timeout for model listing in ms (default 120000).
 *  - cognotik.proxy.pollReadTimeoutMs     HTTP read timeout for an individual poll request in ms (default 30000).
 *  - cognotik.proxy.pollInitialIntervalMs Initial polling interval in ms (default 500).
 *  - cognotik.proxy.pollMaxIntervalMs     Maximum polling interval in ms (default 5000).
 */
object ProxyConfig {
    const val PROP_PROXY_BASE = "cognotik.proxy.base"
    const val PROP_CONNECT_TIMEOUT_MS = "cognotik.proxy.connectTimeoutMs"
    const val PROP_CHAT_READ_TIMEOUT_MS = "cognotik.proxy.chatReadTimeoutMs"
    const val PROP_MODELS_READ_TIMEOUT_MS = "cognotik.proxy.modelsReadTimeoutMs"
    const val PROP_POLL_READ_TIMEOUT_MS = "cognotik.proxy.pollReadTimeoutMs"
    const val PROP_POLL_INITIAL_INTERVAL_MS = "cognotik.proxy.pollInitialIntervalMs"
    const val PROP_POLL_MAX_INTERVAL_MS = "cognotik.proxy.pollMaxIntervalMs"

    const val DEFAULT_PROXY_BASE = "http://localhost:12891"
    const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000
    const val DEFAULT_CHAT_READ_TIMEOUT_MS = 300_000
    const val DEFAULT_MODELS_READ_TIMEOUT_MS = 120_000
    const val DEFAULT_POLL_READ_TIMEOUT_MS = 30_000
    const val DEFAULT_POLL_INITIAL_INTERVAL_MS = 500
    const val DEFAULT_POLL_MAX_INTERVAL_MS = 5_000

    val defaultProxyBase: String
        get() = System.getProperty(PROP_PROXY_BASE, DEFAULT_PROXY_BASE) + "/api-proxy"

    val connectTimeoutMs: Int
        get() = System.getProperty(PROP_CONNECT_TIMEOUT_MS)?.toIntOrNull()
            ?: DEFAULT_CONNECT_TIMEOUT_MS

    val chatReadTimeoutMs: Int
        get() = System.getProperty(PROP_CHAT_READ_TIMEOUT_MS)?.toIntOrNull()
            ?: DEFAULT_CHAT_READ_TIMEOUT_MS

    val modelsReadTimeoutMs: Int
        get() = System.getProperty(PROP_MODELS_READ_TIMEOUT_MS)?.toIntOrNull()
            ?: DEFAULT_MODELS_READ_TIMEOUT_MS
    val pollReadTimeoutMs: Int
        get() = System.getProperty(PROP_POLL_READ_TIMEOUT_MS)?.toIntOrNull()
            ?: DEFAULT_POLL_READ_TIMEOUT_MS
    val pollInitialIntervalMs: Long
        get() = System.getProperty(PROP_POLL_INITIAL_INTERVAL_MS)?.toLongOrNull()
            ?: DEFAULT_POLL_INITIAL_INTERVAL_MS.toLong()
    val pollMaxIntervalMs: Int
        get() = System.getProperty(PROP_POLL_MAX_INTERVAL_MS)?.toIntOrNull()
            ?: DEFAULT_POLL_MAX_INTERVAL_MS
}