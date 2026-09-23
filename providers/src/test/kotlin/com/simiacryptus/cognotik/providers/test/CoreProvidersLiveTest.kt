package com.simiacryptus.cognotik.providers.test

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.ChatMessageModality
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.LLMModel
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageListener
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.slf4j.LoggerFactory

/**
 * Live smoke tests for the providers registered by [CoreProviders].
 *
 * These require credentials supplied through [TestApiKeys]; with no key file the
 * factories emit a single passing "skipped" test so CI stays green.
 */
class CoreProvidersLiveTest {

  companion object {
    private val log = LoggerFactory.getLogger(CoreProvidersLiveTest::class.java)

    private val config: TestApiKeys.Config? by lazy { TestApiKeys.load() }

    @BeforeAll
    @JvmStatic
    fun registerProviders() {
      CoreProviders.init()
    }

    private fun noConfigTest(what: String) = listOf(
      DynamicTest.dynamicTest("$what - skipped (no credentials configured)") {
        log.info(
          "Skipping {}: no '{}' found. See src/test/resources/{}.example.",
          what, TestApiKeys.RESOURCE_NAME, TestApiKeys.RESOURCE_NAME
        )
      }
    )
  }

  @TestFactory
  fun `provider lists chat models`(): List<DynamicTest> = perProvider("chat model listing") { cfg, provider, key, base ->
    val models = provider.getChatModels(key, base)
    assertNotNull(models, "getChatModels returned null for ${cfg.name}")
    log.info("{} exposes {} chat model(s): {}", cfg.name, models.size, models.take(10).map { it.modelId })
    assertTrue(models.isNotEmpty(), "${cfg.name} returned no chat models")
    assertTrue(models.all { !it.modelId.isNullOrBlank() }, "${cfg.name} returned a model with a blank modelId")
  }

  @TestFactory
  fun `provider completes a chat request`(): List<DynamicTest> = perProvider("chat completion") { cfg, provider, key, base ->
    val defaults = config!!.defaults
    val model = selectChatModel(cfg, provider, key, base)
    assumeTrue(model != null) { "${cfg.name}: no suitable text chat model found" }
    model!!
    log.info("{}: issuing chat request against '{}'", cfg.name, model.modelId)

    val session = Session.newUserID()
    val client = provider.getChatClient(key = key, session = session)
    val request = ModelSchema.ChatRequest(
      messages = listOf(
        ModelSchema.ChatMessage(
          role = ModelSchema.Role.user,
          content = listOf(ModelSchema.ContentPart.text(defaults.prompt))
        )
      ),
      model = model.modelId,
      temperature = 0.0,
      // Reasoning models frequently need far more headroom than the smoke-test budget.
      max_tokens = if (model.supportsReasoning) null else defaults.maxTokens,
    )

    @Suppress("DEPRECATION")
    val response = client.chat(request, model, usageHandler = recordingListener(session))

    assertNull(response.error?.message, "${cfg.name} returned an API error: ${response.error}")
    assertTrue(response.choices.isNotEmpty(), "${cfg.name} returned no choices")
    val content = response.choices.first().message?.content
    log.info("{} responded: {}", cfg.name, content?.take(200))
    assertFalse(content.isNullOrBlank(), "${cfg.name} returned an empty completion")
  }

  @TestFactory
  fun `provider computes embeddings`(): List<DynamicTest> = perProvider("embeddings") { cfg, provider, key, base ->
    val models = try {
      provider.getEmbeddingModels(key, base).ifEmpty { provider.getEmbeddingModels() }
    } catch (e: UnsupportedOperationException) {
      emptyList()
    }
    assumeTrue(models.isNotEmpty()) { "${cfg.name}: no embedding models advertised" }
    val model = cfg.embeddingModel
      ?.let { requested -> models.firstOrNull { it.modelId == requested } }
      ?: models.first()
    log.info("{}: embedding with '{}'", cfg.name, model.modelId)

    val embedding = model.instance(key = key, base = base).embed("The quick brown fox jumps over the lazy dog.")
    assertTrue(embedding.isNotEmpty(), "${cfg.name} returned an empty embedding vector")
    assertTrue(embedding.any { it != 0.0 }, "${cfg.name} returned an all-zero embedding vector")
  }

  /**
   * Builds one dynamic test per usable provider entry, or a single passing
   * placeholder when no credentials are available.
   */
  private fun perProvider(
    what: String,
    body: (TestApiKeys.ProviderConfig, APIProvider, SecureString, String) -> Unit
  ): List<DynamicTest> {
    val cfg = config ?: return noConfigTest(what)
    val usable = cfg.usableProviders()
    if (usable.isEmpty()) {
      log.warn("'{}' contains no enabled provider with a real key; skipping {}.", cfg.source, what)
      return noConfigTest(what)
    }
    return usable.map { providerConfig ->
      DynamicTest.dynamicTest("$what - ${providerConfig.name}") {
        val provider = runCatching { APIProvider.valueOf(providerConfig.name) }.getOrNull()
        assumeTrue(provider != null) {
          "Unknown provider '${providerConfig.name}' in ${cfg.source}; known: ${APIProvider.values().map { it.name }}"
        }
        provider!!
        val base = providerConfig.base ?: provider.base
        body(providerConfig, provider, providerConfig.apiKey!!.trim().encrypt, base)
      }
    }
  }

  private fun selectChatModel(
    cfg: TestApiKeys.ProviderConfig,
    provider: APIProvider,
    key: SecureString,
    base: String
  ): ChatModel? {
    val models = runCatching { provider.getChatModels(key, base) }.getOrElse {
      log.warn("{}: could not list chat models", cfg.name, it)
      emptyList()
    }
    cfg.chatModel?.let { requested ->
      val match = models.firstOrNull { it.modelId == requested || it.name == requested }
      if (match != null) return match
      log.warn("{}: configured chatModel '{}' not found; falling back to auto-selection", cfg.name, requested)
    }
    return models.firstOrNull {
      !it.deprecated &&
          it.inputModalities.contains(ChatMessageModality.TEXT) &&
          it.outputModalities.contains(ChatMessageModality.TEXT)
    } ?: models.firstOrNull()
  }

  /**
   * Local implementation instead of [UsageListener.fn]: the factory's
   * `override val sessionId get() = sessionId` is self-referential.
   */
  private fun recordingListener(session: Session) = object : UsageListener {
    override val sessionId: Session = session
    override fun onUsage(model: LLMModel, usage: ModelSchema.Usage, data: ModelSchema.UsageData?) {
      log.info(
        "Usage for {}: total={} cost={} counts={}",
        model.modelId, usage.total_tokens, usage.cost, usage.counts
      )
    }
  }
}