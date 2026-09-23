package com.simiacryptus.cognotik.providers.test

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.util.DynamicEnum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class CoreProvidersRegistrationTest {

  companion object {
    /** All providers registered by [CoreProviders.init]. */
    val expected: Map<String, APIProvider> by lazy {
      listOf(
        CoreProviders.Gemini,
        CoreProviders.OpenAI,
        CoreProviders.Anthropic,
        CoreProviders.AWS,
        CoreProviders.Groq,
        CoreProviders.Perplexity,
        CoreProviders.ModelsLab,
        CoreProviders.Mistral,
        CoreProviders.DeepSeek,
        CoreProviders.Ollama,
        CoreProviders.XAI,
        CoreProviders.ZAI,
        CoreProviders.Qwen,
        CoreProviders.ElevenLabs,
        CoreProviders.HostedProxy,
      ).associateBy { it.name }
    }

    @BeforeAll
    @JvmStatic
    fun registerProviders() {
      CoreProviders.init()
    }
  }

  @Test
  fun `init registers every core provider`() {
    val registered = APIProvider.values().map { it.name }.toSet()
    val missing = expected.keys - registered
    assertTrue(missing.isEmpty()) { "Providers missing from the registry: $missing" }
  }

  @Test
  fun `provider names are unique`() {
    val names = APIProvider.values().map { it.name }
    assertEquals(names.size, names.toSet().size) { "Duplicate provider registrations: $names" }
  }

  @Test
  fun `init is idempotent`() {
    val before = APIProvider.values().size
    CoreProviders.init()
    CoreProviders.init()
    assertEquals(before, APIProvider.values().size, "Re-running init() must not duplicate registrations")
  }

  @TestFactory
  fun `valueOf resolves the singleton instance`(): List<DynamicTest> = expected.map { (name, provider) ->
    DynamicTest.dynamicTest(name) {
      assertSame(provider, APIProvider.valueOf(name), "valueOf('$name') must return the CoreProviders singleton")
    }
  }

  @TestFactory
  fun `providers expose a usable identity`(): List<DynamicTest> = expected.map { (name, provider) ->
    DynamicTest.dynamicTest(name) {
      assertTrue(provider.name.isNotBlank(), "Provider name must not be blank")
      assertEquals(name, provider.toString(), "DynamicEnum.toString() should be the provider name")
      // Equality/hashing is name-based; a provider must equal itself and not equal another.
      assertEquals(provider, APIProvider.valueOf(name))
      assertEquals(provider.hashCode(), APIProvider.valueOf(name).hashCode())
    }
  }

  @Test
  fun `the NULL provider is a sentinel and is not registered`() {
    assertFalse(APIProvider.values().any { it === APIProvider.NULL }, "NULL must not be registered")
    assertEquals("NULL", APIProvider.NULL.name)
    assertEquals("", APIProvider.NULL.base)
  }

  @Test
  fun `unknown provider names fail fast`() {
    assertThrows(IllegalArgumentException::class.java) {
      APIProvider.valueOf("definitely-not-a-provider-${System.nanoTime()}")
    }
  }

  @Test
  fun `unregister removes only the named provider`() {
    val name = "test-only-provider-${System.nanoTime()}"
    val probe = object : APIProvider(name, "http://localhost") {
      override fun getChatClient(
        key: com.simiacryptus.cognotik.util.SecureString,
        workPool: java.util.concurrent.ExecutorService,
        logLevel: org.slf4j.event.Level,
        logStreams: MutableList<java.io.BufferedOutputStream>,
        scheduledPool: com.google.common.util.concurrent.ListeningScheduledExecutorService,
        session: com.simiacryptus.cognotik.platform.model.Session
      ) = throw UnsupportedOperationException("test probe")
    }
    DynamicEnum.register(APIProvider::class.java, probe)
    try {
      assertSame(probe, APIProvider.valueOf(name))
    } finally {
      assertTrue(DynamicEnum.unregister(APIProvider::class.java, name))
    }
    assertTrue(expected.keys.all { APIProvider.values().map { p -> p.name }.contains(it) })
    assertThrows(IllegalArgumentException::class.java) { APIProvider.valueOf(name) }
  }
}