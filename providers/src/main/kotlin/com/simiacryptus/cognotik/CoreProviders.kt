package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.platform.CognotikPlugin
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.providers.*
import com.simiacryptus.cognotik.util.DynamicEnum
import org.slf4j.LoggerFactory

object CoreProviders : CognotikPlugin {

  val log = LoggerFactory.getLogger(CoreProviders::class.java)

  @JvmStatic
  val Gemini: APIProvider = GeminiProvider()

  @JvmStatic
  val Ollama: APIProvider = OllamaProvider()

  @JvmStatic
  val OpenAI: APIProvider = OpenAIProvider()

  @JvmStatic
  val Anthropic: APIProvider = AnthropicProvider()

  @JvmStatic
  val AWS: APIProvider = BedrockProvider()

  @JvmStatic
  val Groq: APIProvider = GroqProvider()

  @JvmStatic
  val Perplexity: APIProvider = PerplexityProvider()

  @JvmStatic
  val ModelsLab: APIProvider = ModelsLabProvider()

  @JvmStatic
  val Mistral: APIProvider = MistralProvider()

  @JvmStatic
  val DeepSeek: APIProvider = DeepSeekProvider()

  @JvmStatic
  val XAI: APIProvider = XAIProvider()

  /*ZAI*/
  @JvmStatic
  val ZAI: APIProvider = com.simiacryptus.cognotik.providers.ZAIProvider()

  @JvmStatic
  val Qwen: APIProvider = QwenProvider()

  @JvmStatic
  val ElevenLabs: APIProvider = ElevenLabsProvider()


  override fun init() {
    log.info("Registering API providers")
    DynamicEnum.register(APIProvider::class.java, Gemini)
    DynamicEnum.register(APIProvider::class.java, OpenAI)
    DynamicEnum.register(APIProvider::class.java, Anthropic)
    DynamicEnum.register(APIProvider::class.java, AWS)
    DynamicEnum.register(APIProvider::class.java, Groq)
    DynamicEnum.register(APIProvider::class.java, Perplexity)
    DynamicEnum.register(APIProvider::class.java, ModelsLab)
    DynamicEnum.register(APIProvider::class.java, Mistral)
    DynamicEnum.register(APIProvider::class.java, DeepSeek)
    DynamicEnum.register(APIProvider::class.java, Ollama)
    DynamicEnum.register(APIProvider::class.java, XAI)
    DynamicEnum.register(APIProvider::class.java, ZAI)
    DynamicEnum.register(APIProvider::class.java, Qwen)
    DynamicEnum.register(APIProvider::class.java, ElevenLabs)
  }
}