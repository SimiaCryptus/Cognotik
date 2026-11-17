package com.simiacryptus.cognotik.providers.test

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.mcp.LocalMCPToolProvider
import com.simiacryptus.cognotik.platform.ServiceKey
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.mcp.*
import com.simiacryptus.cognotik.platform.service.UsageInterface
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import com.simiacryptus.cognotik.util.jsonCast
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * Live smoke tests for the providers registered by [CoreProviders].
 *
 * These require credentials supplied through [TestApiKeys]; with no key file the
 * factories emit a single passing "skipped" test so CI stays green.
 */
class McpProvidersLiveTest {

  companion object {
    private val log = LoggerFactory.getLogger(McpProvidersLiveTest::class.java)

    private val config: TestApiKeys.Config? by lazy { TestApiKeys.load() }

    @BeforeAll
    @JvmStatic
    fun registerProviders() {
      CoreProviders.init()
      log.info("Registered API providers: {}", APIProvider.values().map { it.name }.sorted())
    }

    @BeforeAll
    @JvmStatic
    fun setupTestingPlatform() {
      ServiceKey.USAGE_DB.defaultFactory = {
        object : UsageInterface {
          override fun getUserUsageSummary(
            user: User,
            from: LocalDate,
            to: LocalDate
          ): Map<String, ModelSchema.Usage> {
            return emptyMap()
          }

          override fun getSessionUsageSummary(
            user: User,
            session: Session
          ): Map<String, ModelSchema.Usage> {
            return emptyMap()
          }

          override fun incrementUsage(
            session: Session,
            user: User,
            model: AIModel,
            usage: ModelSchema.Usage,
            data: ModelSchema.UsageData?
          ) {
            // no-op
          }

          override fun clear() {
            // no-op
          }

          override fun setParentSession(
            user: User,
            child: Session,
            parent: Session
          ) {
            // no-op
          }

          override fun getParentSession(
            user: User,
            child: Session
          ): Session? {
            return null
          }

          override fun getAvailableBudget(user: User): Double {
            return 0.0
          }

          override fun creditUser(
            user: User,
            amount: Double,
            comment: String?,
            metadata: Map<String, String>?
          ): Double {
            return 0.0
          }

          override fun getUserDailyUsage(
            user: User,
            from: LocalDate,
            to: LocalDate
          ): List<UsageInterface.DailyUsage> {
            return emptyList()
          }

          override fun getUserCredits(
            user: User,
            from: LocalDate,
            to: LocalDate
          ): List<UsageInterface.CreditEntry> {
            return emptyList()
          }

          override fun getUserBalance(user: User): Double {
            return 0.0
          }

          override fun getSessionUsageRows(
            session: Session,
            user: User
          ): List<UsageInterface.UsageRow> {
            return emptyList()
          }
        }
      }
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
  fun `supports MCP where advertised`(): List<DynamicTest> =
    perProvider("chat model listing") { cfg, provider, key, base ->
      val models = provider.getChatModels(key, base)
      val model = models.filter { it.supportsInteractiveTools }.firstOrNull() ?: return@perProvider
      log.info("{}: issuing chat request against '{}'", cfg.name, model.modelId)

      val toolInvocations = AtomicInteger(0)
      val session = Session.newUserID()
      val client = model.instance(
        key = key,
        session = session,
        user = User.NULL,
      )
      val request = ModelSchema.ChatRequest(
        messages = listOf(
          ModelSchema.ChatMessage(
            role = ModelSchema.Role.user,
            content = listOf(ModelSchema.ContentPart.text("Run the tool"))
          )
        ),
        model = model.modelId,
        temperature = 0.0,
        max_tokens = if (model.supportsReasoning) null else 1024,
        toolProviders = listOf(
          LocalMCPToolProvider(
            listOf(
              object : MCPTool {
                override val definition: MCPToolDefinition
                  get() {
                    return MCPToolDefinition(
                      name = "test_tool",
                      description = "A test tool for smoke testing",
                      inputSchema = (mapOf<String, Any>(
                        "type" to "object",
                        "properties" to mapOf(
                          "message" to mapOf(
                            "type" to "string",
                            "description" to "An optional message to echo back"
                          )
                        ),
                        "required" to emptyList<String>()
                      )).jsonCast<JsonNode>(),
                      outputSchema = (mapOf<String, Any>(
                        "type" to "object",
                        "properties" to mapOf(
                          "result" to mapOf(
                            "type" to "string",
                            "description" to "The result of the tool execution"
                          )
                        )
                      )).jsonCast<JsonNode>(),
                    )
                  }

                override fun call(
                  arguments: JsonNode,
                  context: MCPCallContext
                ): MCPToolResult {
                  toolInvocations.incrementAndGet()
                  log.info(
                    "{}: test_tool invoked with arguments: {}",
                    cfg.name,
                    arguments
                  )
                  return MCPToolResult(
                    content = listOf(MCPContent.Text("Tool executed successfully")),
                    isError = false
                  )
                }
              }
            ))
        )
      )

      @Suppress("DEPRECATION")
      val response = client.chat(request)
      assertNull(response.error?.message, "${cfg.name} returned an API error: ${response.error}")
      assertTrue(response.choices.isNotEmpty(), "${cfg.name} returned no choices")
      val content = response.choices.first().message?.content
      log.info("{} responded: {}", cfg.name, content?.take(200))
      log.info("{}: test_tool was invoked {} time(s)", cfg.name, toolInvocations.get())
      assertFalse(content.isNullOrBlank(), "${cfg.name} returned an empty completion")
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
    log.info(
      "{}: generating {} test(s) from '{}' for provider(s) {}",
      what, usable.size, cfg.source, usable.map { it.name }
    )
    return usable.map { providerConfig ->
      DynamicTest.dynamicTest("$what - ${providerConfig.name}") {
        /* A misspelled / unregistered provider name is a configuration error, not a
           reason to silently skip: fail loudly and list what *is* registered. */
        val provider = resolveProvider(providerConfig.name) ?: fail(
          "Unknown provider '${providerConfig.name}' declared in ${cfg.source}. " +
              "Registered providers: ${APIProvider.values().map { it.name }.sorted()}"
        )
        val base = providerConfig.base ?: provider.base
        log.info("{}: running '{}' against provider '{}' (base={})", providerConfig.name, what, provider.name, base)
        body(providerConfig, provider, providerConfig.apiKey!!.trim().encrypt, base)
      }
    }
  }

  /**
   * Resolves a configured provider name against the [DynamicEnum][APIProvider]
   * registry, tolerating case differences so that `hostedproxy`, `HostedProxy`
   * and `HOSTEDPROXY` all refer to the same provider.
   */
  private fun resolveProvider(name: String): APIProvider? {
    val trimmed = name.trim()
    runCatching { APIProvider.valueOf(trimmed) }.getOrNull()?.let { return it }
    val ciMatch = APIProvider.values().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    if (ciMatch != null) {
      log.warn(
        "Provider '{}' matched '{}' case-insensitively; consider fixing the configuration.",
        trimmed,
        ciMatch.name
      )
    } else {
      log.error(
        "Provider '{}' is not registered. Registered providers: {}",
        trimmed, APIProvider.values().map { it.name }.sorted()
      )
    }
    return ciMatch
  }
}