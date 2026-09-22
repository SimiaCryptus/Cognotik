package com.simiacryptus.cognotik.providers.proxy

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.platform.model.ChatClientInterface
import com.simiacryptus.cognotik.platform.model.UsageListener
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.jsonCast
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ExecutorService

/**
 * Minimal ChatClient implementation that forwards chat() requests to the
 * Cognotik proxy server.
 *
 * The server returns a job token immediately from POST /chat/{provider} and
 * then this client polls GET /chat/{provider}/result/{token} until the job
 * completes (or fails / times out).
 */
class ProxyChatClient(
    private val proxyBase: String,
    private val upstreamKey: SecureString,
    private val upstreamProviderName: String,
    private val mapper: ObjectMapper,
    override val session: Session
) : ChatClientInterface {
    private val log = LoggerFactory.getLogger(ProxyChatClient::class.java)

    val user: User = try {
        upstreamKey.decrypt?.jsonCast<User>()
            ?: throw IllegalArgumentException("Upstream key must contain user information for authentication")
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to decrypt/parse upstream key: ${e.message}", e)
    }

    init {
        if (proxyBase.isBlank()) {
            throw IllegalArgumentException("Proxy base URL cannot be blank")
        }
        if (upstreamProviderName.isBlank()) {
            throw IllegalArgumentException("Upstream provider name cannot be blank")
        }
        log.debug("ProxyChatClient initialized for upstream='$upstreamProviderName' base='$proxyBase'")
    }

    /**
     * Simple response wrapper used when parsing async POST/poll status payloads.
     * Captures fields used by the client; uses a lenient mapper.
     */
    private data class AsyncStatus(
        val token: String? = null,
        val status: String? = null,
        val error: String? = null,
        val type: String? = null
    )

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<BufferedOutputStream>,
        usageHandler: UsageListener
    ): ModelSchema.ChatResponse {
        val token = submitAsyncJob(chatRequest, model)
        return pollForResult(token, model)
    }

    /**
     * Submits the chat request and returns the async job token from the server.
     */
    private fun submitAsyncJob(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel
    ): String {
        val urlString = "${proxyBase.trimEnd('/')}/chat/$upstreamProviderName?session=${session}"
        log.debug("Submitting async chat request to proxy: $urlString (model=${model.modelId})")
        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            log.error("Invalid proxy chat URL '$urlString': ${e.message}", e)
            throw ProxyProviderException(
                "Invalid proxy chat URL: $urlString", provider = upstreamProviderName, cause = e
            )
        }

        var conn: HttpURLConnection? = null
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-API-Key", upstreamKey.toString())
            conn.setCookies(user.getAuthCookies())
            conn.connectTimeout = ProxyConfig.connectTimeoutMs


            conn.readTimeout = ProxyConfig.connectTimeoutMs

            try {
                conn.outputStream.use { os ->
                    mapper.writeValue(os, chatRequest)
                }
            } catch (e: IOException) {
                log.error(
                    "Failed to write chat request body to proxy (provider='$upstreamProviderName', url=$urlString)",
                    e
                )
                throw ProxyProviderException(
                    "Failed to send chat request body: ${e.message}",
                    provider = upstreamProviderName,
                    cause = e
                )
            }

            val code = conn.responseCode
            log.debug("Proxy async chat submission code=$code for provider='$upstreamProviderName'")

            if (code !in 200..299) {
                val err = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull() ?: ""
                log.error("Proxy chat submission failed (provider='$upstreamProviderName', code=$code, url=$urlString): $err")
                throw ProxyProviderException(
                    "Proxy chat submission failed for provider '$upstreamProviderName' ($code): $err",
                    provider = upstreamProviderName,
                    statusCode = code,
                    responseBody = err
                )
            }
            val status = try {
                conn.inputStream.use { input ->
                    mapper.readValue(input, AsyncStatus::class.java)
                }
            } catch (e: IOException) {
                log.error("Failed to parse async submission response from proxy", e)
                throw ProxyProviderException(
                    "Failed to parse async submission response: ${e.message}",
                    provider = upstreamProviderName,
                    statusCode = code,
                    cause = e
                )
            }
            val token = status.token
            if (token.isNullOrBlank()) {
                throw ProxyProviderException(
                    "Proxy returned no job token for provider '$upstreamProviderName'",
                    provider = upstreamProviderName,
                    statusCode = code
                )
            }
            log.debug("Received async job token='$token' for provider='$upstreamProviderName'")
            return token
        } catch (e: ProxyProviderException) {
            throw e
        } catch (e: SocketTimeoutException) {
            log.error("Timeout submitting async chat request (provider='$upstreamProviderName', url=$urlString): ${e.message}")
            throw ProxyProviderException(
                "Timeout submitting proxy chat request to '$upstreamProviderName'",
                provider = upstreamProviderName,
                cause = e
            )
        } catch (e: UnknownHostException) {
            log.error("Unknown host submitting async chat request (provider='$upstreamProviderName', url=$urlString): ${e.message}")
            throw ProxyProviderException(
                "Unknown host for proxy: $urlString", provider = upstreamProviderName, cause = e
            )
        } catch (e: IOException) {
            log.error("I/O error submitting async chat request (provider='$upstreamProviderName', url=$urlString)", e)
            throw ProxyProviderException(
                "I/O error during proxy chat submission: ${e.message}",
                provider = upstreamProviderName,
                cause = e
            )
        } catch (e: Exception) {
            log.error(
                "Unexpected error submitting async chat request (provider='$upstreamProviderName', url=$urlString)",
                e
            )
            throw ProxyProviderException(
                "Unexpected error during proxy chat submission: ${e.message}",
                provider = upstreamProviderName,
                cause = e
            )
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
                log.debug("Error disconnecting connection: ${e.message}")
            }
        }
    }

    /**
     * Polls the server for an async job's result with exponential backoff until
     * completion, failure, or timeout (controlled by [ProxyConfig.chatReadTimeoutMs]).
     */
    private fun pollForResult(token: String, model: ChatModel): ModelSchema.ChatResponse {
        val urlString = "${proxyBase.trimEnd('/')}/chat/$upstreamProviderName/result/$token"
        val deadline = System.currentTimeMillis() + ProxyConfig.chatReadTimeoutMs
        var delayMs = ProxyConfig.pollInitialIntervalMs
        log.debug("Polling for async chat result: token='$token' url='$urlString'")

        while (true) {
            if (System.currentTimeMillis() > deadline) {
                log.error("Polling deadline exceeded for token='$token' (provider='$upstreamProviderName')")
                throw ProxyProviderException(
                    "Timed out waiting for proxy chat result (token=$token)",
                    provider = upstreamProviderName
                )
            }

            val pollResult = try {
                pollOnce(urlString)
            } catch (e: ProxyProviderException) {
                throw e
            } catch (e: SocketTimeoutException) {
                log.warn("Timeout polling for token='$token'; will retry: ${e.message}")
                null
            } catch (e: IOException) {

                log.warn("I/O error polling for token='$token'; will retry: ${e.message}")
                null
            } catch (e: Exception) {
                log.error("Unexpected error polling for token='$token'", e)
                throw ProxyProviderException(
                    "Unexpected error polling proxy result: ${e.message}",
                    provider = upstreamProviderName,
                    cause = e
                )
            }

            when (pollResult) {
                is PollResult.Completed -> return pollResult.response
                is PollResult.Failed -> throw ProxyProviderException(
                    "Proxy chat job failed (token=$token): ${pollResult.message}",
                    provider = upstreamProviderName
                )

                is PollResult.Pending, null -> {

                    try {
                        Thread.sleep(delayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ProxyProviderException(
                            "Polling interrupted for token=$token",
                            provider = upstreamProviderName,
                            cause = ie
                        )
                    }
                    delayMs = (delayMs * 2).coerceAtMost(ProxyConfig.pollMaxIntervalMs.toLong())
                }
            }
        }
    }

    private sealed class PollResult {
        data class Completed(val response: ModelSchema.ChatResponse) : PollResult()
        data class Failed(val message: String) : PollResult()
        object Pending : PollResult()
    }

    /**
     * Performs a single poll attempt. May throw [ProxyProviderException] for
     * fatal/non-retryable errors; transient I/O errors propagate to the caller.
     */
    private fun pollOnce(urlString: String): PollResult {
        val url = URL(urlString)
        var conn: HttpURLConnection? = null
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-Key", upstreamKey.toString())
            conn.setRequestProperty("Accept", "application/json")
            conn.setCookies(user.getAuthCookies())
            conn.connectTimeout = ProxyConfig.connectTimeoutMs
            conn.readTimeout = ProxyConfig.pollReadTimeoutMs

            val code = conn.responseCode
            return when (code) {
                in 200..200 -> {

                    val response = conn.inputStream.use { input ->
                        mapper.readValue(input, ModelSchema.ChatResponse::class.java)
                    }
                    PollResult.Completed(response)
                }

                202 -> PollResult.Pending
                404 -> {
                    val err = runCatching {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull() ?: ""
                    throw ProxyProviderException(
                        "Proxy job token not found or expired: $err",
                        provider = upstreamProviderName,
                        statusCode = code,
                        responseBody = err
                    )
                }

                else -> {
                    val err = runCatching {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull() ?: ""

                    val parsed = runCatching {
                        mapper.readValue(err, AsyncStatus::class.java)
                    }.getOrNull()
                    if (parsed?.status == "failed") {
                        PollResult.Failed(parsed.error ?: "Unknown failure")
                    } else if (code in 500..599) {

                        log.warn("Server-side poll error (code=$code), will retry: $err")
                        PollResult.Pending
                    } else {
                        throw ProxyProviderException(
                            "Unexpected poll response ($code): $err",
                            provider = upstreamProviderName,
                            statusCode = code,
                            responseBody = err
                        )
                    }
                }
            }
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    override val logStreams: MutableList<BufferedOutputStream>
        get() = mutableListOf()
    override val workPool: ExecutorService
        get() = throw UnsupportedOperationException("ProxyChatClient does not support async operations")

    override fun getModels(): List<ChatModel> {
        val urlString = "${proxyBase.trimEnd('/')}/models/$upstreamProviderName"
        log.debug("Fetching models from proxy: $urlString")
        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            log.error("Invalid proxy models URL '$urlString': ${e.message}", e)
            return emptyList()
        }

        var conn: HttpURLConnection? = null
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-Key", upstreamKey.toString())
            conn.setRequestProperty("Accept", "application/json")
            conn.setCookies(user.getAuthCookies())
            conn.connectTimeout = ProxyConfig.connectTimeoutMs
            conn.readTimeout = ProxyConfig.modelsReadTimeoutMs

            val code = conn.responseCode
            log.debug("Proxy models response code=$code for provider='$upstreamProviderName'")
            if (code !in 200..299) {
                val err = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull() ?: ""
                log.warn("Proxy models request failed (provider='$upstreamProviderName', code=$code, url=$urlString): $err")
                return emptyList()
            }
            return conn.inputStream.use { input ->
                mapper.readValue(
                    input, mapper.typeFactory.constructCollectionType(List::class.java, ChatModel::class.java)
                )
            }
        } catch (e: SocketTimeoutException) {
            log.warn("Timeout fetching models from proxy (provider='$upstreamProviderName', url=$urlString): ${e.message}")
            return emptyList()
        } catch (e: UnknownHostException) {
            log.error("Unknown host fetching models from proxy (provider='$upstreamProviderName', url=$urlString): ${e.message}")
            return emptyList()
        } catch (e: IOException) {
            log.error("I/O error fetching models from proxy (provider='$upstreamProviderName', url=$urlString)", e)
            return emptyList()
        } catch (e: Exception) {
            log.error(
                "Unexpected error fetching models from proxy (provider='$upstreamProviderName', url=$urlString)", e
            )
            return emptyList()
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
                log.debug("Error disconnecting connection: ${e.message}")
            }
        }
    }

}
fun HttpURLConnection.setCookies(cookies: Map<String, String?>) {
    setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
}