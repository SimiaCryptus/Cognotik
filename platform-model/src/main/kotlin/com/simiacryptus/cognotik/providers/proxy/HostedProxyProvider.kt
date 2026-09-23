package com.simiacryptus.cognotik.providers.proxy

import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory

class HostedProxyProvider(
  val url: String = "https://hosted.cognotik.com/api-proxy",
  vararg name: String = arrayOf(
    "Gemini", "Anthropic", "ElevenLabs", "Groq", "Mistral", "xAI", "DeepSeek"
  )
) : ProxyProvider(PROVIDER_NAME, url, *name) {
  override fun getAuthCookies(key: SecureString): Map<String, String?> = mapOf(
    AuthenticationInterface.AUTH_COOKIE to key.decrypt,
  )

  companion object {
    /**
     * Registration name of this provider. Must match the key used in
     * user/test configuration files (e.g. `test-api-keys.json`) and the
     * `CoreProviders.HostedProxy` field name.
     */
    const val PROVIDER_NAME = "HostedProxy"
    val log = LoggerFactory.getLogger(HostedProxyProvider::class.java)
  }
}