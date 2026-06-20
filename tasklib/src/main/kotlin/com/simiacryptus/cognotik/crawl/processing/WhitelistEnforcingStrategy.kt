package com.simiacryptus.cognotik.crawl.processing

import com.simiacryptus.cognotik.crawl.CrawlerAgentTask.PageType
import java.net.URI

/**
 * A decorating [PageProcessingStrategy] that enforces a site whitelist.
 *
 * Any page whose host does not match at least one entry in [allowedHosts] is
 * immediately rejected without being forwarded to the [delegate] strategy.
 * Extracted links that point to non-whitelisted hosts are stripped from the
 * result so that the crawler never queues off-site URLs.
 *
 * Matching rules
 * --------------
 * An entry in [allowedHosts] may be:
 *  - An exact hostname, e.g. `"docs.example.com"` – matches only that host.
 *  - A wildcard-prefixed hostname, e.g. `"*.example.com"` – matches any
 *    subdomain of `example.com` as well as `example.com` itself.
 *  - A bare apex domain without a wildcard, e.g. `"example.com"` – matches
 *    only `example.com` (not `sub.example.com`).
 *
 * @param delegate     The inner strategy that performs the real processing.
 * @param allowedHosts The set of host patterns that are permitted.
 */
class WhitelistEnforcingStrategy(
  private val delegate: PageProcessingStrategy,
  val allowedHosts: Set<String>
) : PageProcessingStrategy {

  override val name: String
    get() = "Whitelist[${allowedHosts.joinToString(",")}](${delegate.name})"

  override val description: String
    get() = "Enforces a site whitelist (${allowedHosts.joinToString(", ")}) " +
        "around: ${delegate.description}"

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  override fun processPage(
    url: String,
    content: String,
    context: PageProcessingStrategy.ProcessingContext
  ): PageProcessingStrategy.PageProcessingResult {
    if (!isAllowed(url)) {
      return blockedResult(url, "Host is not in the site whitelist: ${hostOf(url)}")
    }

    val raw = delegate.processPage(url, content, context)

    // Strip any extracted links that point outside the whitelist so the
    // crawler never enqueues off-site URLs.
    val filteredLinks = raw.extractedLinks?.filter { link ->
      link.url.isNullOrBlank() || isAllowed(link.url ?: "")
    }

    return if (filteredLinks == raw.extractedLinks) {
      raw // nothing was removed – return as-is to avoid an extra copy
    } else {
      raw.copy(extractedLinks = filteredLinks)
    }
  }

  override fun shouldContinueCrawling(
    currentResults: List<PageProcessingStrategy.PageProcessingResult>,
    context: PageProcessingStrategy.ProcessingContext
  ): PageProcessingStrategy.ContinuationDecision =
    delegate.shouldContinueCrawling(currentResults, context)

  override fun generateFinalOutput(
    results: List<PageProcessingStrategy.PageProcessingResult>,
    context: PageProcessingStrategy.ProcessingContext
  ): String = delegate.generateFinalOutput(results, context)

  override fun validateConfig(config: Any?): String? {
    if (allowedHosts.isEmpty()) {
      return "WhitelistEnforcingStrategy requires at least one entry in allowedHosts"
    }
    return delegate.validateConfig(config)
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Returns `true` when [url] resolves to a host that matches at least one
   * pattern in [allowedHosts].
   */
  fun isAllowed(url: String): Boolean {
    val host = hostOf(url) ?: return false
    return allowedHosts.any { pattern -> matches(host, pattern) }
  }

  /**
   * Extracts the lowercase host component from [url], or `null` when the
   * URL cannot be parsed or has no host.
   */
  private fun hostOf(url: String): String? = runCatching {
    URI(url).host?.lowercase()
  }.getOrNull()

  /**
   * Tests whether [host] satisfies [pattern].
   *
   * Pattern semantics:
   *  - `"*.example.com"` → matches `example.com` and any subdomain.
   *  - anything else     → exact case-insensitive match.
   */
  private fun matches(host: String, pattern: String): Boolean {
    val p = pattern.lowercase()
    return when {
      p.startsWith("*.") -> {
        val apex = p.removePrefix("*.")
        host == apex || host.endsWith(".$apex")
      }

      else -> host == p
    }
  }

  private fun blockedResult(url: String, reason: String) =
    PageProcessingStrategy.PageProcessingResult(
      url = url,
      pageType = PageType.Error,
      content = "",
      summary = reason,
      extractedLinks = emptyList(),
      metadata = mapOf("whitelistBlocked" to true, "reason" to reason),
      shouldTerminate = false,
      terminationReason = null,
      error = null
    )

  // -------------------------------------------------------------------------
  // Companion – convenience factories
  // -------------------------------------------------------------------------

  companion object {

    /**
     * Wraps [delegate] and seeds the whitelist from the hosts found in
     * [seedUrls].  This is the most common usage: "stay on the same sites
     * we started from".
     *
     * @param delegate The strategy to wrap.
     * @param seedUrls One or more starting URLs whose hosts become the
     *                 initial whitelist.
     * @param extraHosts Additional host patterns to allow beyond the seeds.
     */
    fun fromSeedUrls(
      delegate: PageProcessingStrategy,
      seedUrls: Iterable<String>,
      extraHosts: Set<String> = emptySet()
    ): WhitelistEnforcingStrategy {
      val hosts = seedUrls.mapNotNull { url ->
        runCatching { URI(url).host?.lowercase() }.getOrNull()
      }.toSet() + extraHosts

      return WhitelistEnforcingStrategy(delegate, hosts)
    }

    /**
     * Wraps [delegate] and allows any subdomain of each apex domain in
     * [apexDomains] by automatically prepending the `*.` wildcard.
     *
     * Example: `forApexDomains(delegate, setOf("example.com"))` allows
     * `example.com`, `docs.example.com`, `api.example.com`, etc.
     */
    fun forApexDomains(
      delegate: PageProcessingStrategy,
      apexDomains: Set<String>
    ): WhitelistEnforcingStrategy {
      val patterns = apexDomains.map { "*.${it.lowercase().removePrefix("*.")}" }.toSet()
      return WhitelistEnforcingStrategy(delegate, patterns)
    }
  }
}