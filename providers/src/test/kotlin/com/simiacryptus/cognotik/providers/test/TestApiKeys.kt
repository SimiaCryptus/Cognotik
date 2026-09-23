package com.simiacryptus.cognotik.providers.test

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Loads optional, developer-supplied API credentials for the live provider tests.
 *
 * Resolution order:
 *  1. `-Dcognotik.test.apiKeys=<path>`
 *  2. `COGNOTIK_TEST_API_KEYS=<path>` environment variable
 *  3. `test-api-keys.json` on the test classpath (providers/src/test/resources)
 *  4. `test-api-keys.json` in the module directory or working directory
 *
 * When nothing is found, [load] returns null and the tests log-and-pass.
 * Parsing is done against a raw [JsonNode] so no extra Jackson modules are required.
 */
object TestApiKeys {

  private val log = LoggerFactory.getLogger(TestApiKeys::class.java)

  const val RESOURCE_NAME = "test-api-keys.json"
  const val SYSTEM_PROPERTY = "cognotik.test.apiKeys"
  const val ENV_VARIABLE = "COGNOTIK_TEST_API_KEYS"

  private val mapper = ObjectMapper()

  data class Defaults(
    val prompt: String = "Reply with the single word: pong",
    val maxTokens: Int = 64,
    val timeoutSeconds: Long = 120,
  )

  data class ProviderConfig(
    val name: String,
    val apiKey: String?,
    val base: String? = null,
    val enabled: Boolean = true,
    val chatModel: String? = null,
    val embeddingModel: String? = null,
  ) {
    /** A key is usable when present, non-blank and not left as an `<placeholder>`. */
    val hasUsableKey: Boolean
      get() = !apiKey.isNullOrBlank() && !apiKey.trim().startsWith("<")

    val isUsable: Boolean get() = enabled && hasUsableKey
  }

  data class Config(
    val source: String,
    val defaults: Defaults,
    val providers: Map<String, ProviderConfig>,
  ) {
    fun usableProviders(): List<ProviderConfig> =
      providers.values.filter { it.isUsable }.sortedBy { it.name }
  }

  /** @return the parsed configuration, or null when no key file is present/readable. */
  fun load(): Config? {
    val (source, json) = locate() ?: run {
      log.warn(
        "No provider credentials found - live provider tests will be skipped. " +
            "To enable them, copy 'src/test/resources/$RESOURCE_NAME.example' to " +
            "'src/test/resources/$RESOURCE_NAME' (gitignored), or set -D$SYSTEM_PROPERTY / \$$ENV_VARIABLE."
      )
      return null
    }
    return try {
      parse(source, mapper.readTree(json))
    } catch (e: Exception) {
      log.warn("Failed to parse provider credentials from $source; live provider tests will be skipped.", e)
      null
    }
  }

  private fun parse(source: String, root: JsonNode): Config {
    val defaultsNode = root.path("defaults")
    val defaults = Defaults(
      prompt = defaultsNode.path("prompt").asText("Reply with the single word: pong"),
      maxTokens = defaultsNode.path("maxTokens").asInt(64),
      timeoutSeconds = defaultsNode.path("timeoutSeconds").asLong(120),
    )
    val providers = LinkedHashMap<String, ProviderConfig>()
    root.path("providers").fields().forEach { (name, node) ->
      providers[name] = ProviderConfig(
        name = name,
        apiKey = node.path("apiKey").takeIf { it.isTextual }?.asText(),
        base = node.path("base").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() },
        enabled = node.path("enabled").asBoolean(true),
        chatModel = node.path("chatModel").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() },
        embeddingModel = node.path("embeddingModel").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() },
      )
    }
    log.info(
      "Loaded provider credentials from {} ({} configured, {} usable)",
      source, providers.size, providers.values.count { it.isUsable }
    )
    return Config(source, defaults, providers)
  }

  private fun locate(): Pair<String, String>? {
    System.getProperty(SYSTEM_PROPERTY)?.takeIf { it.isNotBlank() }?.let { path ->
      readFile(File(path))?.let { return it }
      log.warn("-D$SYSTEM_PROPERTY points at '{}', which is not readable.", path)
    }
    System.getenv(ENV_VARIABLE)?.takeIf { it.isNotBlank() }?.let { path ->
      readFile(File(path))?.let { return it }
      log.warn("\$$ENV_VARIABLE points at '{}', which is not readable.", path)
    }
    javaClass.classLoader.getResource(RESOURCE_NAME)?.let { url ->
      return "classpath:$RESOURCE_NAME ($url)" to url.readText()
    }
    listOf(
      File(RESOURCE_NAME),
      File("providers", RESOURCE_NAME),
      File("providers/src/test/resources", RESOURCE_NAME),
      File(System.getProperty("user.home"), ".cognotik/$RESOURCE_NAME"),
    ).forEach { candidate -> readFile(candidate)?.let { return it } }
    return null
  }

  private fun readFile(file: File): Pair<String, String>? =
    if (file.isFile && file.canRead()) file.absolutePath to file.readText() else null
}