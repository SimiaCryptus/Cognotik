package com.simiacryptus.cognotik.providers.proxy

import com.simiacryptus.cognotik.platform.CognotikConfig.controllerEndpoint
import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory

class HostedProxyProvider(
  val url: String = controllerEndpoint + "/api-proxy",
  vararg upstreamProviderNames: String = arrayOf(
    "Gemini", "Anthropic", "ElevenLabs", "Groq", "Mistral", "xAI", "DeepSeek"
  )
) : ProxyProvider("Cognotik", url, *upstreamProviderNames) {
  override val base: String get() = controllerEndpoint + "/api-proxy"

  override fun getAuthCookies(key: SecureString): Map<String, String?> = mapOf(
    AuthenticationInterface.AUTH_COOKIE to key.decrypt,
  )

  companion object {
    val log = LoggerFactory.getLogger(HostedProxyProvider::class.java)
  }
}