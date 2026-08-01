package com.simiacryptus.cognotik.docops.resolve

import com.simiacryptus.cognotik.util.HtmlSimplifier
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration

/**
 * On-disk cache of fetched URLs. Entries are `<sha256[0..16]>_<sanitized-name>` plus a `.meta`
 * sidecar holding url / fetched-at / content-type / etag / last-modified.
 */
class UrlCache(
  private val dir: File,
  private val ttl: Duration = Duration.ofHours(1),
  private val fetcher: HttpFetcher = JdkHttpFetcher(),
  private val clock: Clock = Clock.systemUTC(),
) {

  fun get(url: String): File? {
    return try {
      dir.mkdirs()
      val base = cacheName(url)
      val content = File(dir, base)
      val metaFile = File(dir, "$base.meta")
      val meta = readMeta(metaFile)
      val fetchedAt = meta["fetched"]?.toLongOrNull() ?: 0L
      val now = clock.millis()

      if (content.exists() && meta.isNotEmpty() && now - fetchedAt < ttl.toMillis()) {
        log.debug("Using cached URL content for: $url")
        return content
      }

      val response = try {
        fetcher.fetch(HttpFetchRequest(url, etag = meta["etag"], lastModified = meta["last-modified"]))
      } catch (e: Exception) {
        log.warn("Failed to fetch URL: $url", e)
        return if (content.exists()) content.also { log.warn("Serving stale cache entry for $url") } else null
      }

      when {
        response.isNotModified && content.exists() -> {
          writeMeta(metaFile, url, now, meta["content-type"] ?: "", meta["etag"], meta["last-modified"])
          log.debug("URL not modified, refreshing cache timestamp: $url")
          content
        }

        response.isSuccess -> {
          content.writeText(simplify(url, response))
          writeMeta(metaFile, url, now, response.contentType, response.etag, response.lastModified)
          log.info("Cached URL content for $url at ${content.absolutePath}")
          content
        }

        content.exists() -> {
          log.warn("Failed to fetch URL $url: HTTP ${response.statusCode}; serving stale cache entry")
          content
        }

        else -> {
          log.warn("Failed to fetch URL $url: HTTP ${response.statusCode}")
          null
        }
      }
    } catch (e: Exception) {
      log.error("Failed to fetch and cache URL: $url", e)
      null
    }
  }

  private fun simplify(url: String, response: HttpFetchResponse): String {
    val isHtml = response.contentType.startsWith("text/html") || response.contentType.isEmpty()
    if (!isHtml) return response.body
    return try {
      HtmlSimplifier.scrubHtml(
        str = response.body,
        baseUrl = url,
        includeCssData = false,
        simplifyStructure = true,
        keepObjectIds = false,
        preserveWhitespace = false,
        keepScriptElements = false,
        keepInteractiveElements = false,
        keepMediaElements = false,
        keepEventHandlers = false,
      )
    } catch (e: Exception) {
      log.warn("HTML simplification failed for URL: $url, using raw content", e)
      response.body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }
  }

  private fun cacheName(url: String): String {
    val hash = MessageDigest.getInstance("SHA-256")
      .digest(url.toByteArray())
      .joinToString("") { "%02x".format(it) }
      .take(16)
    val safeName = url.substringAfterLast("/")
      .substringBefore("?")
      .replace(Regex("[^a-zA-Z0-9._-]"), "_")
      .take(50)
      .ifEmpty { "index" }
    return "${hash}_$safeName"
  }

  private fun readMeta(metaFile: File): Map<String, String> {
    if (!metaFile.exists()) return emptyMap()
    return try {
      metaFile.readLines().mapNotNull { line ->
        val idx = line.indexOf('=')
        if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
      }.toMap()
    } catch (e: Exception) {
      log.warn("Failed to read cache metadata: ${metaFile.absolutePath}", e)
      emptyMap()
    }
  }

  private fun writeMeta(
    metaFile: File,
    url: String,
    fetchedAt: Long,
    contentType: String,
    etag: String?,
    lastModified: String?,
  ) {
    metaFile.writeText(
      buildString {
        appendLine("url=$url")
        appendLine("fetched=$fetchedAt")
        appendLine("content-type=$contentType")
        etag?.let { appendLine("etag=$it") }
        lastModified?.let { appendLine("last-modified=$it") }
      }
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(UrlCache::class.java)
  }
}