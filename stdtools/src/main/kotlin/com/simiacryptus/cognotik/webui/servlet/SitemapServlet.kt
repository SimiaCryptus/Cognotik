package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.webui.application.AppEntry
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/**
 * Servlet that generates an XML sitemap (sitemap.xml) for the application.
 *
 * The sitemap includes:
 *  - The root welcome page
 *  - The application directory listing
 *  - Each registered AppEntry's path
 *
 * Optionally, requesting `?format=txt` returns a plain-text list of URLs
 * (one per line), useful for quick inspection or simple crawlers.
  *
  * This servlet also handles robots.txt requests. The robots.txt disallows all
  * paths by default, then explicitly allows only the URLs defined in the sitemap,
  * plus sitemap.xml, robots.txt, and the gateway page itself.
 */
class SitemapServlet : HttpServlet() {

    companion object {
        private val log = LoggerFactory.getLogger(SitemapServlet::class.java)

        private fun w3cDateNow(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }

        private fun escapeXml(s: String): String =
            s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
         /** Extracts the path component from an absolute URL, or returns the value as-is if already a path. */
         private fun toPath(url: String, baseUrl: String): String? {
             return when {
                 url.startsWith(baseUrl) -> url.removePrefix(baseUrl).ifEmpty { "/" }
                 url.startsWith("http://") || url.startsWith("https://") -> null // external URL – skip
                 url.startsWith("/") -> url
                 else -> "/$url"
             }
         }
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val baseUrl = computeBaseUrl(req)
             val servletPath = req.servletPath ?: req.pathInfo ?: ""

             when {
                 servletPath.endsWith("robots.txt") -> serveRobotsTxt(req, resp, baseUrl)
                 else -> serveSitemap(req, resp, baseUrl)
             }
        } catch (e: Exception) {
            log.error("Error generating sitemap: ${e.message}", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "text/plain; charset=UTF-8"
            resp.writer.use { it.write("Error generating sitemap: ${e.message ?: "unknown"}") }
        }
    }
     private fun serveSitemap(req: HttpServletRequest, resp: HttpServletResponse, baseUrl: String) {
         val format = req.getParameter("format")?.lowercase()
         val urls = buildUrlList(baseUrl)
         if (format == "txt") {
             resp.contentType = "text/plain; charset=UTF-8"
             resp.characterEncoding = "UTF-8"
             resp.writer.use { out ->
                 urls.forEach { out.println(it.loc) }
             }
             log.debug("Served sitemap.txt with ${urls.size} URLs")
             return
         }
         resp.contentType = "application/xml; charset=UTF-8"
         resp.characterEncoding = "UTF-8"
         resp.setHeader("Cache-Control", "public, max-age=3600")
         val now = w3cDateNow()
         resp.writer.use { out ->
             out.println("""<?xml version="1.0" encoding="UTF-8"?>""")
             out.println("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
             urls.forEach { entry ->
                 out.println("  <url>")
                 out.println("    <loc>${escapeXml(entry.loc)}</loc>")
                 out.println("    <lastmod>$now</lastmod>")
                 out.println("    <changefreq>${entry.changefreq}</changefreq>")
                 out.println("    <priority>${entry.priority}</priority>")
                 out.println("  </url>")
             }
             out.println("</urlset>")
         }
         log.debug("Served sitemap.xml with ${urls.size} URLs")
     }
     private fun serveRobotsTxt(req: HttpServletRequest, resp: HttpServletResponse, baseUrl: String) {
         val urls = buildUrlList(baseUrl)
         // Collect the path components of all sitemap URLs that belong to this host.
         // External URLs (different host) are excluded from Allow rules.
         val allowedPaths = urls
             .mapNotNull { toPath(it.loc, baseUrl) }
             .distinct()
             .sorted()
         resp.contentType = "text/plain; charset=UTF-8"
         resp.characterEncoding = "UTF-8"
         resp.setHeader("Cache-Control", "public, max-age=3600")
         resp.writer.use { out ->
             out.println("User-agent: *")
             // Disallow everything by default …
             out.println("Disallow: /")
             out.println()
             // … then explicitly allow the sitemap URLs …
             allowedPaths.forEach { path ->
                 out.println("Allow: $path")
             }
             // … plus the meta-files and gateway page themselves.
             out.println("Allow: /sitemap.xml")
             out.println("Allow: /robots.txt")
             out.println("Allow: /gateway")
             out.println()
             out.println("Sitemap: $baseUrl/sitemap.xml")
         }
         log.debug("Served robots.txt with ${allowedPaths.size} allowed paths")
     }


    private data class UrlEntry(
        val loc: String,
        val changefreq: String = "weekly",
        val priority: String = "0.5"
    )

    private fun buildUrlList(baseUrl: String): List<UrlEntry> {
        val entries = mutableListOf<UrlEntry>()
        // Root / welcome page
        entries += UrlEntry(loc = "$baseUrl/", changefreq = "daily", priority = "1.0")
        // App directory listing
        entries += UrlEntry(loc = "$baseUrl/appDirectory", changefreq = "daily", priority = "0.8")
         // Gateway page
         entries += UrlEntry(loc = "$baseUrl/gateway", changefreq = "daily", priority = "0.9")

        // Each registered AppEntry
        try {
            AppEntry.values().forEach { entry ->
                val path = entry.path.takeIf { it.isNotBlank() } ?: return@forEach
                val normalized = if (path.startsWith("/")) path else "/$path"
                entries += UrlEntry(
                    loc = "$baseUrl$normalized",
                    changefreq = "weekly",
                    priority = "0.7"
                )
                // Video landing page for this app
                entries += UrlEntry(
                    loc = "$baseUrl/video/${entry.name.lowercase()}/",
                    changefreq = "weekly",
                    priority = "0.6"
                )
                // Include example sessions if provided
                entry.exampleSessions?.forEach { (_, url) ->
                    if (url.isNotBlank()) {
                        val absolute = when {
                            url.startsWith("http://") || url.startsWith("https://") -> url
                            url.startsWith("/") -> "$baseUrl$url"
                            else -> "$baseUrl/$url"
                        }
                        entries += UrlEntry(
                            loc = absolute,
                            changefreq = "monthly",
                            priority = "0.4"
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            log.warn("Failed to enumerate AppEntry values for sitemap: ${e.message}", e)
        }

        // Deduplicate by loc, preserving order
        return entries.distinctBy { it.loc }
    }

    private fun computeBaseUrl(req: HttpServletRequest): String {
        // Honor X-Forwarded-* headers if present (already handled by Jetty's ForwardedRequestCustomizer
        // for req.scheme/serverName, but we still build the URL manually for clarity).
        val scheme = req.scheme ?: "http"
        val host = req.serverName ?: "localhost"
        val port = req.serverPort
        val includePort = !((scheme == "http" && port == 80) || (scheme == "https" && port == 443) || port <= 0)
        val portPart = if (includePort) ":$port" else ""
        return "$scheme://$host$portPart"
    }
}