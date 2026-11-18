package com.simiacryptus.cognotik.providers.test

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.mcp.LocalMCPToolProvider
import com.simiacryptus.cognotik.mcp.McpClientToolProvider
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
     perProvider("MCP local tool provider") { cfg, provider, key, base ->
      val models = provider.getChatModels(key, base)
       val model = models.firstOrNull { it.supportsInteractiveTools }
       assumeTrue(model != null, "${cfg.name}: no chat model advertises supportsInteractiveTools")
       model!!
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
             content = listOf(ModelSchema.ContentPart.text("Run the test_tool tool and report its result."))
          )
        ),
        model = model.modelId,
        temperature = 0.0,
        max_tokens = if (model.supportsReasoning) null else 1024,
        toolProviders = listOf(
          LocalMCPToolProvider(
            listOf(

               createTestTool("test_tool", "A test tool for smoke testing") { arguments, _ ->
                 toolInvocations.incrementAndGet()
                 log.info("{}: test_tool invoked with arguments: {}", cfg.name, arguments)
                 MCPToolResult(
                   content = listOf(MCPContent.Text("Tool executed successfully")),
                   isError = false
                 )
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
       assertTrue(toolInvocations.get() > 0, "${cfg.name}: expected test_tool to be invoked at least once")
      assertFalse(content.isNullOrBlank(), "${cfg.name} returned an empty completion")
    }
   @TestFactory
   fun `supports MCP loopback transport`(): List<DynamicTest> =
     perProvider("MCP loopback transport") { cfg, provider, key, base ->
       val models = provider.getChatModels(key, base)
       val model = models.firstOrNull { it.supportsInteractiveTools }
       assumeTrue(model != null, "${cfg.name}: no chat model advertises supportsInteractiveTools")
       model!!
       log.info("{}: issuing loopback MCP chat request against '{}'", cfg.name, model.modelId)
       val toolInvocations = AtomicInteger(0)
       val session = Session.newUserID()
       val client = model.instance(
         key = key,
         session = session,
         user = User.NULL,
       )
       val loopbackProvider = McpClientToolProvider.loopback(
         name = "loopback-test",
         tools = listOf(
           createTestTool("loopback_echo", "Echoes a message over the MCP loopback transport") { arguments, _ ->
             toolInvocations.incrementAndGet()
             log.info("{}: loopback_echo invoked with arguments: {}", cfg.name, arguments)
             val msg = arguments.path("message").asText("default")
             MCPToolResult(
               content = listOf(MCPContent.Text("LOOPBACK_OK: $msg")),
               isError = false
             )
           }
         ),
         session = session,
         user = User.NULL,
       )
       try {
         val request = ModelSchema.ChatRequest(
           messages = listOf(
             ModelSchema.ChatMessage(
               role = ModelSchema.Role.user,
               content = listOf(
                 ModelSchema.ContentPart.text("Call the loopback_echo tool with message='ping' and report what it returned.")
               )
             )
           ),
           model = model.modelId,
           temperature = 0.0,
           max_tokens = if (model.supportsReasoning) null else 1024,
           toolProviders = listOf(loopbackProvider)
         )
         @Suppress("DEPRECATION")
         val response = client.chat(request)
         assertNull(response.error?.message, "${cfg.name} returned an API error: ${response.error}")
         assertTrue(response.choices.isNotEmpty(), "${cfg.name} returned no choices")
         val content = response.choices.first().message?.content
         log.info("{} loopback responded: {}", cfg.name, content?.take(200))
         log.info("{}: loopback_echo was invoked {} time(s)", cfg.name, toolInvocations.get())
         assertNotNull(
           loopbackProvider.protocolVersion,
           "${cfg.name}: expected loopback MCP client to complete initialize handshake"
         )
         assertTrue(toolInvocations.get() > 0, "${cfg.name}: expected loopback_echo to be invoked at least once")
         assertFalse(content.isNullOrBlank(), "${cfg.name} returned an empty completion")
       } finally {
         loopbackProvider.close()
       }
     }
   @TestFactory
   fun `supports error-returning MCP tools and multi-turn roundtrips`(): List<DynamicTest> =
     perProvider("MCP error tool and multi-turn roundtrip") { cfg, provider, key, base ->
       val models = provider.getChatModels(key, base)
       val model = models.firstOrNull { it.supportsInteractiveTools }
       assumeTrue(model != null, "${cfg.name}: no chat model advertises supportsInteractiveTools")
       model!!
       log.info("{}: issuing multi-turn MCP error-tool test against '{}'", cfg.name, model.modelId)
       val seedInvocations = AtomicInteger(0)
       val verifyInvocations = AtomicInteger(0)
       val session = Session.newUserID()
       val client = model.instance(
         key = key,
         session = session,
         user = User.NULL,
       )
       val loopbackProvider = McpClientToolProvider.loopback(
         name = "loopback-multi-turn",
         tools = listOf(
           createTestTool("get_seed_value", "Returns a seed token needed for verification") { arguments, _ ->
             seedInvocations.incrementAndGet()
             log.info("{}: get_seed_value invoked with arguments: {}", cfg.name, arguments)
             MCPToolResult(
               content = listOf(MCPContent.Text("SEED-4242")),
               isError = false
             )
           },
           createTestTool("verify_seed_value", "Verifies a seed token and reports status or error") { arguments, _ ->
             verifyInvocations.incrementAndGet()
             log.info("{}: verify_seed_value invoked with arguments: {}", cfg.name, arguments)
             MCPToolResult(
               content = listOf(MCPContent.Text("ERROR_CODE_503: verification rejected for SEED-4242")),
               isError = true
             )
           }
         ),
         session = session,
         user = User.NULL,
       )
       try {
         val turn1Prompt = "Call the get_seed_value tool and tell me the exact value it returned."
         val turn1Request = ModelSchema.ChatRequest(
           messages = listOf(
             ModelSchema.ChatMessage(
               role = ModelSchema.Role.user,
               content = listOf(ModelSchema.ContentPart.text(turn1Prompt))
             )
           ),
           model = model.modelId,
           temperature = 0.0,
           max_tokens = if (model.supportsReasoning) null else 1024,
           toolProviders = listOf(loopbackProvider)
         )
         @Suppress("DEPRECATION")
         val response1 = client.chat(turn1Request)
         assertNull(response1.error?.message, "${cfg.name} turn 1 returned an API error: ${response1.error}")
         assertTrue(response1.choices.isNotEmpty(), "${cfg.name} turn 1 returned no choices")
         val reply1 = response1.choices.first().message?.content
         log.info("{} turn 1 responded: {}", cfg.name, reply1?.take(200))
         assertTrue(seedInvocations.get() > 0, "${cfg.name}: expected get_seed_value to be invoked on turn 1")
         assertFalse(reply1.isNullOrBlank(), "${cfg.name} turn 1 returned an empty completion")
         val turn2Prompt =
           "Now call the verify_seed_value tool with that value and report the exact error or status it returned."
         val turn2Request = ModelSchema.ChatRequest(
           messages = listOf(
             ModelSchema.ChatMessage(
               role = ModelSchema.Role.user,
               content = listOf(ModelSchema.ContentPart.text(turn1Prompt))
             ),
             ModelSchema.ChatMessage(
               role = ModelSchema.Role.assistant,
               content = listOf(ModelSchema.ContentPart.text(reply1!!))
             ),
             ModelSchema.ChatMessage(
               role = ModelSchema.Role.user,
               content = listOf(ModelSchema.ContentPart.text(turn2Prompt))
             )
           ),
           model = model.modelId,
           temperature = 0.0,
           max_tokens = if (model.supportsReasoning) null else 1024,
           toolProviders = listOf(loopbackProvider)
         )
         @Suppress("DEPRECATION")
         val response2 = client.chat(turn2Request)
         assertNull(response2.error?.message, "${cfg.name} turn 2 returned an API error: ${response2.error}")
         assertTrue(response2.choices.isNotEmpty(), "${cfg.name} turn 2 returned no choices")
         val reply2 = response2.choices.first().message?.content
         log.info("{} turn 2 responded: {}", cfg.name, reply2?.take(200))
         assertTrue(verifyInvocations.get() > 0, "${cfg.name}: expected verify_seed_value to be invoked on turn 2")
         assertFalse(reply2.isNullOrBlank(), "${cfg.name} turn 2 returned an empty completion")
         assertTrue(
           reply2!!.contains("503") ||
             reply2.contains("rejected", ignoreCase = true) ||
             reply2.contains("error", ignoreCase = true) ||
             reply2.contains("fail", ignoreCase = true),
           "${cfg.name}: expected turn 2 reply to reflect the isError=true tool result, got: $reply2"
         )
       } finally {
         loopbackProvider.close()
       }
     }
   private fun createTestTool(
     name: String,
     description: String,
     onCall: (JsonNode, MCPCallContext) -> MCPToolResult,
   ): MCPTool = object : MCPTool {
     override val definition: MCPToolDefinition = MCPToolDefinition(
       name = name,
       description = description,
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
     override fun call(
       arguments: JsonNode,
       context: MCPCallContext
     ): MCPToolResult = onCall(arguments, context)
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