package com.simiacryptus.cognotik.config

import com.intellij.openapi.progress.ProcessCanceledException
import com.simiacryptus.cognotik.util.JsonUtil
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Device (QR) login against the Cognotik hosted backend.
 * Mirrors the CLI FileServer login, but only returns the token; it does not touch any services.
 */
object HostedLogin {
    private val log = LoggerFactory.getLogger(HostedLogin::class.java)

    const val DEFAULT_URL = "https://hosted.cognotik.com"

    val baseUrl: String
        get() = System.getProperty("cognotik.url")?.takeIf { it.isNotBlank() }
            ?: System.getenv("COGNOTIK_URL")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_URL

    data class LoginUser(
        val id: String? = null,
        val email: String? = null,
    )

    data class LoginResponse(
        val rid: String? = null,
        val pollSecret: String? = null,
        val verificationUrl: String? = null,
        val displayCode: String? = null,
        val tokenEndpoint: String? = null,
        val cookieName: String? = null,
        val status: String? = null,
        val token: String? = null,
        val error: String? = null,
        val interval: Long? = null,
        val expiresIn: Long? = null,
        val user: LoginUser? = null,
    )

    data class LoginResult(
        val token: String,
        val userId: String,
    )

    /** Callbacks used to report progress to the UI and to support cancellation. */
    interface Listener {
        /** Called once the login request is started; the user must open [verificationUrl] and confirm [displayCode]. */
        fun onVerification(verificationUrl: String, displayCode: String?)
        fun onStatus(message: String) {}
        /** Should throw (e.g. [ProcessCanceledException]) when the user cancelled. */
        fun checkCanceled() {}
    }

    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

     /** Waits for [future] in small slices, checking for cancellation in between. */
     private fun <T> awaitCancellable(future: CompletableFuture<T>, listener: Listener): T {
         while (true) {
             try {
                 return future.get(200, TimeUnit.MILLISECONDS)
             } catch (e: TimeoutException) {
                 try {
                     listener.checkCanceled()
                 } catch (t: Throwable) {
                     future.cancel(true)
                     throw t
                 }
             } catch (e: ExecutionException) {
                 throw e.cause ?: e
             }
         }
     }

     private fun postLoginForm(url: String, params: Map<String, String>, listener: Listener): Pair<Int, LoginResponse> {
        val form = (mapOf("formAction" to "login", "loginMethod" to "qr") + params).entries.joinToString("&") {
            "${URLEncoder.encode(it.key, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
        }
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .header("User-Agent", "cognotik-intellij-login/1.0 (java ${System.getProperty("java.version")})")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
         val response = awaitCancellable(http.sendAsync(request, HttpResponse.BodyHandlers.ofString()), listener)
        val type = response.headers().firstValue("content-type").orElse("")
        if (!type.contains("application/json")) {
            val location = response.headers().firstValue("location").orElse(null)
            throw IllegalStateException(
                "Unexpected response from server (HTTP ${response.statusCode()}" +
                        (if (location != null) ", redirect to $location" else "") +
                        "). Is the QR login method enabled on this server?"
            )
        }
        return response.statusCode() to JsonUtil.fromJson<LoginResponse>(response.body(), LoginResponse::class.java)
    }

    /** Sleeps in small slices so cancellation is responsive. */
    private fun sleepCancellable(millis: Long, listener: Listener) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            listener.checkCanceled()
            Thread.sleep(minOf(200L, maxOf(1L, end - System.currentTimeMillis())))
        }
        listener.checkCanceled()
    }

    /**
     * Runs the device login. Blocking; must not be called on the EDT.
     * @throws IllegalStateException when the login fails, is denied or expires.
     */
    fun login(listener: Listener, base: String = baseUrl): LoginResult {
        val root = base.trimEnd('/')
        val loginEndpoint = "$root/login/"
        val clientName = "cognotik-intellij@" + try {
            java.net.InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "localhost"
        }
         val (httpStatus, start) =
             postLoginForm(loginEndpoint, mapOf("qrAction" to "device", "client" to clientName), listener)
        if (httpStatus != 200 || start.rid == null || start.verificationUrl == null) {
            throw IllegalStateException("Failed to start login (HTTP $httpStatus): ${start.error ?: start}")
        }
        val tokenEndpoint = start.tokenEndpoint ?: loginEndpoint
        var interval = maxOf(1L, start.interval ?: 2L)
        val deadline = System.currentTimeMillis() + (start.expiresIn?.takeIf { it > 0 } ?: 180L) * 1000
        listener.onVerification(start.verificationUrl, start.displayCode)
        var lastStatus: String? = null
        while (System.currentTimeMillis() < deadline) {
            sleepCancellable(interval * 1000, listener)
            val data = try {
                postLoginForm(
                    tokenEndpoint,
                     mapOf("qrAction" to "token", "rid" to start.rid, "poll" to (start.pollSecret ?: "")),
                     listener
                ).second
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                log.debug("Polling error; retrying", e)
                listener.onStatus("Polling error (${e.message}); retrying…")
                sleepCancellable(interval * 1000, listener)
                continue
            }
            when (data.status) {
                "pending" -> {}
                "scanned" -> if (lastStatus != "scanned") listener.onStatus("Approval page opened, waiting for you to approve…")
                "slow_down" -> interval = maxOf(interval + 1, data.interval ?: (interval + 1))
                "approved" -> {
                    val token = data.token ?: throw IllegalStateException("Login approved but no token was returned")
                    val who = data.user?.email ?: data.user?.id ?: ""
                    log.info("Hosted login approved for {}", who.ifBlank { "unknown user" })
                    return LoginResult(token = token, userId = who)
                }

                "denied" -> throw IllegalStateException("The login request was denied.")
                "expired" -> throw IllegalStateException("The login request expired. Please try again.")
                else -> throw IllegalStateException("Unexpected response: ${data.error ?: data.status}")
            }
            lastStatus = data.status
        }
        throw IllegalStateException("Timed out waiting for approval.")
    }
}