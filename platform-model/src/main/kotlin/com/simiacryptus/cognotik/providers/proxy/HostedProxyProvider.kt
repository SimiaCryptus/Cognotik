package com.simiacryptus.cognotik.providers.proxy

import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory

class HostedProxyProvider(
  val url: String = "https://hosted.cognotik.com/api-proxy",
  vararg name: String = arrayOf(
    "Gemini", "Anthropic", "ElevenLabs", "Groq", "Mistral", "xAI", "DeepSeek"
  )
) : ProxyProvider("Cognotik", url, *name) {
  override fun getAuthCookies(key: SecureString): Map<String, String?> = mapOf(
    AuthenticationInterface.AUTH_COOKIE to key.decrypt,
  )

  companion object {
    val log = LoggerFactory.getLogger(HostedProxyProvider::class.java)
  }
}