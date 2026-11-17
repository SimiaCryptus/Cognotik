package com.simiacryptus.cognotik.platform.model.mcp

import org.slf4j.LoggerFactory

/**
 * Collects the tools of several [MCPToolProvider]s for a single loop run.
 *
 * Provider tool names may contain characters that LLM providers reject (limit is
 * ~64 chars of `[A-Za-z0-9_-]`). Names are sanitised; on collision they are
 * namespaced as `<provider>__<tool>`; the exposed name maps back to the provider
 * and original name on dispatch.
 */
class ToolRegistry(
  providers: List<MCPToolProvider>,
  allowed: ((MCPToolDefinition) -> Boolean)? = null,
) {
  /** @property definition the provider's original definition (original name) */
  data class Entry(val exposedName: String, val provider: MCPToolProvider, val definition: MCPToolDefinition)

  private val entries = LinkedHashMap<String, Entry>()

  init {
    val raw = providers.flatMap { provider ->
      val tools = try {
        provider.listTools()
      } catch (e: Exception) {
        log.warn("Skipping MCP tool provider '${provider.name}': listTools failed", e)
        emptyList()
      }
      tools.filter { allowed?.invoke(it) ?: true }.map { provider to it }
    }
    val baseCounts = raw.groupingBy { sanitize(it.second.name) }.eachCount()
    raw.forEach { (provider, def) ->
      val base = sanitize(def.name)
      val stem = if ((baseCounts[base] ?: 0) > 1) sanitize("${provider.name}__${def.name}") else base
      var name = stem
      var i = 2
      while (entries.containsKey(name)) name = sanitize(stem.take(MAX_NAME - 4) + "_" + i++)
      entries[name] = Entry(name, provider, def)
    }
  }

  /** Definitions as exposed to the model (sanitised names). */
  val definitions: List<MCPToolDefinition> get() = entries.values.map { it.definition.copy(name = it.exposedName) }

  fun resolve(exposedName: String): Entry? = entries[exposedName]

  fun isEmpty() = entries.isEmpty()

  companion object {
    private val log = LoggerFactory.getLogger(ToolRegistry::class.java)
    const val MAX_NAME = 64
    private val INVALID = Regex("[^A-Za-z0-9_-]")

    fun sanitize(name: String): String = name.replace(INVALID, "_").take(MAX_NAME).ifEmpty { "tool" }
  }
}