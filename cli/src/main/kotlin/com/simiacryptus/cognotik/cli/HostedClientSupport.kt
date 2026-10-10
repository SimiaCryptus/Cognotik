package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.util.JsonUtil
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import kotlin.collections.get

object HostedClientSupport {
  val log = org.slf4j.LoggerFactory.getLogger(HostedClientSupport::class.java)
  data class LoginUser(
    val id: String? = null,
    val email: String? = null,
  )

  /** Union of the responses of the QR/device login endpoint (start + token polling). */
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

  private val loginHttp: HttpClient by lazy {
    HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .connectTimeout(Duration.ofSeconds(15))
      .build()
  }

  fun loginProgress(msg: String) = System.err.println(msg)
  fun postLoginForm(url: String, params: Map<String, String>): Pair<Int, LoginResponse> {
    val form = (mapOf("formAction" to "login", "loginMethod" to "qr") + params).entries.joinToString("&") {
      "${URLEncoder.encode(it.key, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
    }
    val request = HttpRequest.newBuilder(URI(url))
      .timeout(Duration.ofSeconds(30))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("Accept", "application/json")
      .header("User-Agent", "cognotik-qr-login/1.0 (java ${System.getProperty("java.version")})")
      .POST(HttpRequest.BodyPublishers.ofString(form))
      .build()
    val response = loginHttp.send(request, HttpResponse.BodyHandlers.ofString())
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

  fun tryOpenBrowser(url: String) {
    try {
      val os = System.getProperty("os.name").lowercase()
      val cmd = when {
        os.contains("mac") -> listOf("open", url)
        os.contains("win") -> listOf("cmd", "/c", "start", "\"\"", url)
        else -> listOf("xdg-open", url)
      }
      ProcessBuilder(cmd)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
    } catch (e: Exception) {
      log.debug("Could not open a browser (the URL is printed anyway)", e)
    }
  }

  /**
   * Device (QR) login, mirroring cognotik-login.mjs: start a request, send the user to the
   * verification URL and poll until the request is approved, denied or expired.
   *
   * @return the session token and user, or null when the login failed.
   */
  fun deviceLogin(baseUrl: String, openBrowser: Boolean = true): FileServer.SessionKeyFile? {
    val base = baseUrl.trimEnd('/')
    val loginEndpoint = "$base/login/"
    val clientName = "cognotik-cli@" + (try {
      InetAddress.getLocalHost().hostName
    } catch (e: Exception) {
      "localhost"
    })
    try {
      val (httpStatus, start) = postLoginForm(loginEndpoint, mapOf("qrAction" to "device", "client" to clientName))
      if (httpStatus != 200 || start.rid == null) {
        throw IllegalStateException("Failed to start login (HTTP $httpStatus): ${start.error ?: start}")
      }
      val tokenEndpoint = start.tokenEndpoint ?: loginEndpoint
      var interval = maxOf(1L, start.interval ?: 2L)
      val deadline = System.currentTimeMillis() + (start.expiresIn?.takeIf { it > 0 } ?: 180L) * 1000
      loginProgress("")
      loginProgress("To sign in, open this URL in a browser where you are already logged in:")
      loginProgress("\n    ${start.verificationUrl}\n")
      loginProgress("and check that it shows the confirmation code:  ${start.displayCode}")
      loginProgress("")
      if (openBrowser) start.verificationUrl?.let { tryOpenBrowser(it) }
      var lastStatus: String? = null
      while (System.currentTimeMillis() < deadline) {
        Thread.sleep(interval * 1000)
        val data = try {
          postLoginForm(
            tokenEndpoint,
            mapOf("qrAction" to "token", "rid" to start.rid, "poll" to (start.pollSecret ?: ""))
          ).second
        } catch (e: InterruptedException) {
          throw e
        } catch (e: Exception) {
          loginProgress("Polling error (${e.message}); retrying…")
          Thread.sleep(interval * 1000)
          continue
        }
        when (data.status) {
          "pending" -> {}
          "scanned" -> if (lastStatus != "scanned") loginProgress("Approval page opened, waiting for you to approve…")
          "slow_down" -> interval = maxOf(interval + 1, data.interval ?: (interval + 1))
          "approved" -> {
            val token = data.token ?: throw IllegalStateException("Login approved but no token was returned")
            val who = data.user?.email ?: "unknown user"
            loginProgress("✅ Logged in as $who. Token valid for ~${Math.round((data.expiresIn ?: 0L) / 86400.0)} day(s).")
            return FileServer.SessionKeyFile(
              userId = data.user?.email ?: data.user?.id ?: "",
              sessionKey = token
            )
          }

          "denied" -> throw IllegalStateException("The login request was denied.")
          "expired" -> throw IllegalStateException("The login request expired. Please run the command again.")
          else -> throw IllegalStateException("Unexpected response: $data")
        }
        lastStatus = data.status
      }
      throw IllegalStateException("Timed out waiting for approval.")
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      log.warn("Login interrupted")
      return null
    } catch (e: Exception) {
      log.warn("Device login against $base failed", e)
      loginProgress("❌ ${e.message}")
      return null
    }
  }

  fun saveSessionFile(file: File, session: FileServer.SessionKeyFile) {
    try {
      file.absoluteFile.parentFile?.mkdirs()
      file.writeText(JsonUtil.toJson(session))
      try {
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
      } catch (e: Exception) {
        log.debug("Could not restrict permissions on ${file.absolutePath}", e)
      }
      loginProgress("   Saved session to ${file.absolutePath}")
    } catch (e: Exception) {
      log.warn("Could not save session file ${file.absolutePath}", e)
    }
  }

  /** Result of querying the hosted `/userSettings/` endpoint. */
  sealed class HostedSettingsResult {
    data class Ok(val settings: UserSettings?) : HostedSettingsResult()

    /** The server rejected the session key (401/403 or a login redirect). */
    object Unauthorized : HostedSettingsResult()
    data class Failed(val reason: String) : HostedSettingsResult()
  }

  /**
   * Fetches `{baseUrl}/userSettings/` authenticated with the session cookie, asking for JSON.
   */
  fun fetchHostedUserSettings(baseUrl: String, sessionKey: String): HostedSettingsResult {
    if (sessionKey.isBlank()) return HostedSettingsResult.Unauthorized
    val url = "${baseUrl.trimEnd('/')}/userSettings/"
    return try {
      val request = HttpRequest.newBuilder(URI(url))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .header("Cookie", "${AuthenticationInterface.AUTH_COOKIE}=$sessionKey")
        .header("User-Agent", "cognotik-cli/1.0 (java ${System.getProperty("java.version")})")
        .GET()
        .build()
      val response = loginHttp.send(request, HttpResponse.BodyHandlers.ofString())
      val status = response.statusCode()
      val type = response.headers().firstValue("content-type").orElse("")
      when {
        status == 401 || status == 403 -> HostedSettingsResult.Unauthorized
        status in 300..399 -> {
          /* Redirects are not followed: a redirect here means "go log in". */
          val location = response.headers().firstValue("location").orElse("")
          if (location.contains("login", ignoreCase = true)) HostedSettingsResult.Unauthorized
          else HostedSettingsResult.Failed("unexpected redirect (HTTP $status) to $location")
        }

        status != 200 -> HostedSettingsResult.Failed("HTTP $status from $url")
        !type.contains("application/json") -> HostedSettingsResult.Failed("non-JSON response ($type) from $url")
        else -> HostedSettingsResult.Ok(JsonUtil.fromJson<UserSettings>(response.body(), Any::class.java))
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      HostedSettingsResult.Failed("interrupted")
    } catch (e: Exception) {
      log.debug("Fetching $url failed", e)
      HostedSettingsResult.Failed(e.message ?: e.javaClass.simpleName)
    }
  }

  /** Depth-first search for an `email` field that looks like an address. */
  fun findEmail(node: Any?, depth: Int = 0): String? {
    if (depth > 8 || node == null) return null
    return when (node) {
      is Map<*, *> -> {
        (node["email"] as? String)?.takeIf { it.contains('@') }
          ?: node.values.firstNotNullOfOrNull { findEmail(it, depth + 1) }
      }

      is Iterable<*> -> node.firstNotNullOfOrNull { findEmail(it, depth + 1) }
      is Array<*> -> node.firstNotNullOfOrNull { findEmail(it, depth + 1) }
      else -> null
    }
  }

  fun clearSessionFile(file: File) {
    try {
      if (file.exists() && file.delete()) {
        loginProgress("   Cleared saved session ${file.absolutePath}; you will be asked to log in again.")
      }
    } catch (e: Exception) {
      log.warn("Could not delete session file ${file.absolutePath}", e)
    }
  }



}