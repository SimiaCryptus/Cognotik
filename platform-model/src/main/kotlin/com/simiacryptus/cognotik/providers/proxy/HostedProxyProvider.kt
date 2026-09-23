package com.simiacryptus.cognotik.providers.proxy

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.jsonCast
import org.slf4j.LoggerFactory

class HostedProxyProvider(
  val url: String = "https://hosted.cognotik.com/api-proxy",
  vararg name: String = arrayOf(
    "Gemini", "Anthropic", "ElevenLabs", "Groq", "Mistral", "xAI", "DeepSeek"
  )
) : ProxyProvider("hosted", url, *name) {
  override fun getAuthCookies(key: SecureString): Map<String, String?> {
    val user: User = try {
      key.decrypt?.jsonCast<User>()?.requireValid()
        ?: throw IllegalArgumentException("Upstream key must contain user information for authentication")
    } catch (e: IllegalArgumentException) {
      log.error("Invalid upstream key for getChatModels (provider='${upstreamProviderName}'): ${e.message}")
      throw e
    } catch (e: Exception) {
      log.error("Failed to decrypt/parse upstream key for getChatModels (provider='${upstreamProviderName}')", e)
      throw ProxyProviderException(
        "Failed to parse upstream key: ${e.message}",
        provider = upstreamProviderName,
        cause = e
      )
    }
    /* Cookies are local state: a lookup failure should degrade to an unauthenticated call,
       not abort provider enumeration with a stack trace for every registered provider. */
    return try {
      user.getAuthCookies()
    } catch (e: Exception) {
      log.warn("Could not resolve auth cookies for '${user}' (provider='${upstreamProviderName}'): ${e.message}")
      emptyMap()
    }
  }

  companion object {
    val log = LoggerFactory.getLogger(HostedProxyProvider::class.java)
  }
}