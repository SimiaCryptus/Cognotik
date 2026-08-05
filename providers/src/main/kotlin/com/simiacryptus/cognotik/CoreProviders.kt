package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.CognotikPlugin
import com.simiacryptus.cognotik.providers.AnthropicProvider
import com.simiacryptus.cognotik.providers.BedrockProvider
import com.simiacryptus.cognotik.providers.DeepSeekProvider
import com.simiacryptus.cognotik.providers.ElevenLabsProvider
import com.simiacryptus.cognotik.providers.GeminiProvider
import com.simiacryptus.cognotik.providers.GroqProvider
import com.simiacryptus.cognotik.providers.MistralProvider
import com.simiacryptus.cognotik.providers.ModelsLabProvider
import com.simiacryptus.cognotik.providers.OllamaProvider
import com.simiacryptus.cognotik.providers.OpenAIProvider
import com.simiacryptus.cognotik.providers.PerplexityProvider
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
     DynamicEnum.register(APIProvider::class.java, ElevenLabs)
  }
}