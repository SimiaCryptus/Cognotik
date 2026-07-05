package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.webui.application.AppEntry
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Serves SEO-optimised video landing pages for each app that has a videoUrl.
 *
 * URL pattern: /video/{appId}
 *
 * Each page includes:
 *  - Open Graph / Twitter Card meta tags
 *  - JSON-LD VideoObject structured data (for Google rich results)
 *  - An embedded player (iframe for YouTube/Vimeo, <video> for direct files)
 *  - A prominent CTA linking back to the app gateway page
 *  - The app description and tags for keyword density
 */
class VideoLandingServlet : HttpServlet() {

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        // Path is expected to be  /{appId}  (the servlet is mapped to /video/*)
        val rawPath = request.servletPath ?: "/"
        val appId = rawPath.removePrefix("/").trimEnd('/')

        if (appId.isBlank()) {
            renderIndex(request, response)
            return
        }

        val entry = AppEntry.values().find { it.appId == appId || it.id == appId }
        if (entry == null || entry.videoUrl.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No video landing page found for: $appId")
            return
        }

        renderVideoPage(request, response, entry)
    }

    // -------------------------------------------------------------------------
    // Index page – lists all apps that have videos
    // -------------------------------------------------------------------------

    private fun renderIndex(request: HttpServletRequest, response: HttpServletResponse) {
        val entries = AppEntry.values().filter { !it.videoUrl.isNullOrBlank() }
        response.contentType = "text/html; charset=UTF-8"
        response.status = HttpServletResponse.SC_OK

        val baseUrl = getBaseUrl(request)
        val listHtml = buildString {
            entries.forEach { e ->
                val id = e.appId ?: e.id
                val name = e.displayName.ifBlank { id }
                append("""<li><a href="${escapeHtml("$baseUrl/video/$id")}">${escapeHtml(name)}</a></li>""")
                append("\n")
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>App Videos – Cognotik</title>
              <meta name="description" content="Watch demo videos for all Cognotik AI-powered apps.">
              <link rel="canonical" href="${escapeHtml("$baseUrl/video/")}">
              ${commonStyles()}
            </head>
            <body>
              <div class="page">
                <h1>App Demo Videos</h1>
                <p>Explore demo videos for our AI-powered apps.</p>
                <ul class="video-index">
                  $listHtml
                </ul>
              </div>
              ${themeScript()}
            </body>
            </html>
        """.trimIndent()

        response.writer.use { it.write(html) }
    }

    // -------------------------------------------------------------------------
    // Individual video landing page
    // -------------------------------------------------------------------------

    private fun renderVideoPage(
        request: HttpServletRequest,
        response: HttpServletResponse,
        entry: AppEntry
    ) {
        val appId = entry.appId ?: entry.id
        val displayName = entry.displayName.ifBlank { appId }
        val description = entry.description.ifBlank { "Watch the $displayName demo video." }
        val videoUrl = entry.videoUrl!!
        val icon = entry.icon
        val tags = entry.tags
        val category = entry.category
        val baseUrl = getBaseUrl(request)
        val canonicalUrl = "$baseUrl/video/$appId"
        val gatewayUrl = "/$appId/"
        val thumbnailUrl = if (entry.hasBackground)
            "$baseUrl/appDirectory/$appId/background.png"
        else
            "$baseUrl/logo.png"

        // Determine embed type
        val isYouTube = videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")
        val isVimeo = videoUrl.contains("vimeo.com")
        val isEmbed = isYouTube || isVimeo

        // Build canonical YouTube embed URL for structured data
        val embedUrl = when {
            isYouTube -> toYouTubeEmbedUrl(videoUrl)
            isVimeo -> toVimeoEmbedUrl(videoUrl)
            else -> videoUrl
        }

        // JSON-LD VideoObject
        val jsonLd = buildJsonLd(
            name = displayName,
            description = description,
            thumbnailUrl = thumbnailUrl,
            contentUrl = if (!isEmbed) videoUrl else embedUrl,
            embedUrl = if (isEmbed) embedUrl else null,
            canonicalUrl = canonicalUrl
        )

        // Player HTML
        val playerHtml = if (isEmbed) {
            """
            <div class="video-wrapper">
              <iframe
                src="${escapeHtml(embedUrl)}"
                title="${escapeHtml("$displayName demo video")}"
                frameborder="0"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen
                loading="lazy">
              </iframe>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="video-wrapper">
              <video controls preload="metadata" poster="${escapeHtml(thumbnailUrl)}">
                <source src="${escapeHtml(videoUrl)}">
                Your browser does not support the video tag.
              </video>
            </div>
            """.trimIndent()
        }

        val tagsHtml = if (tags.isNotEmpty()) buildString {
            append("""<div class="tags">""")
            tags.forEach { append("""<span class="tag">${escapeHtml(it)}</span>""") }
            append("</div>")
        } else ""

        val categoryHtml = if (!category.isNullOrBlank())
            """<span class="category">${escapeHtml(category)}</span>"""
        else ""

        val ogType = if (isEmbed) "video.other" else "website"

        response.contentType = "text/html; charset=UTF-8"
        response.status = HttpServletResponse.SC_OK

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${escapeHtml("$displayName – Demo Video")}</title>
              <meta name="description" content="${escapeHtml(description)}">
              <link rel="canonical" href="${escapeHtml(canonicalUrl)}">

              <!-- Open Graph -->
              <meta property="og:type"        content="$ogType">
              <meta property="og:title"       content="${escapeHtml("$displayName – Demo Video")}">
              <meta property="og:description" content="${escapeHtml(description)}">
              <meta property="og:url"         content="${escapeHtml(canonicalUrl)}">
              <meta property="og:image"       content="${escapeHtml(thumbnailUrl)}">
              <meta property="og:video"       content="${escapeHtml(embedUrl)}">
              <meta property="og:site_name"   content="Cognotik">

              <!-- Twitter Card -->
              <meta name="twitter:card"        content="player">
              <meta name="twitter:title"       content="${escapeHtml("$displayName – Demo Video")}">
              <meta name="twitter:description" content="${escapeHtml(description)}">
              <meta name="twitter:image"       content="${escapeHtml(thumbnailUrl)}">
              <meta name="twitter:player"      content="${escapeHtml(embedUrl)}">
              <meta name="twitter:player:width"  content="1280">
              <meta name="twitter:player:height" content="720">

              <!-- JSON-LD Structured Data -->
              <script type="application/ld+json">
              $jsonLd
              </script>

              <script src="/modules/theme.js"></script>
              ${commonStyles()}
              <style>
                .video-wrapper {
                  position: relative;
                  width: 100%;
                  aspect-ratio: 16 / 9;
                  background: #000;
                  border-radius: 8px;
                  overflow: hidden;
                  margin: 1.5em 0;
                  box-shadow: 0 4px 24px rgba(0,0,0,0.25);
                }
                .video-wrapper iframe,
                .video-wrapper video {
                  position: absolute;
                  top: 0; left: 0;
                  width: 100%; height: 100%;
                  border: 0;
                }
                .cta-block {
                  margin: 2em 0;
                  padding: 1.25em 1.5em;
                  background: var(--panel-bg);
                  border: 1px solid var(--panel-border);
                  border-radius: 6px;
                  display: flex;
                  align-items: center;
                  gap: 1em;
                  flex-wrap: wrap;
                }
                .cta-icon { font-size: 2.5rem; line-height: 1; flex-shrink: 0; }
                .cta-text { flex: 1; min-width: 200px; }
                .cta-text h2 { margin: 0 0 0.25em 0; font-size: 1.2em; }
                .cta-text p  { margin: 0; color: var(--muted); font-size: 0.95em; }
                .cta-btn {
                  display: inline-block;
                  padding: 0.65em 1.4em;
                  background: var(--btn-primary-bg);
                  color: var(--link-fg);
                  border-radius: 5px;
                  text-decoration: none;
                  font-size: 1em;
                  font-weight: 600;
                  white-space: nowrap;
                  transition: filter 0.15s;
                }
                .cta-btn:hover { filter: brightness(1.12); }
                .breadcrumb {
                  font-size: 0.85em;
                  color: var(--muted);
                  margin-bottom: 1em;
                }
                .breadcrumb a { color: var(--btn-primary-bg); text-decoration: none; }
                .breadcrumb a:hover { text-decoration: underline; }
                .app-meta-row {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 0.5em;
                  align-items: center;
                  margin-bottom: 0.75em;
                }
                .share-row {
                  margin: 1.5em 0 0.5em 0;
                  display: flex;
                  gap: 0.75em;
                  flex-wrap: wrap;
                  align-items: center;
                }
                .share-label { font-size: 0.9em; color: var(--muted); }
                .share-btn {
                  display: inline-flex;
                  align-items: center;
                  gap: 0.3em;
                  padding: 0.35em 0.85em;
                  border-radius: 4px;
                  font-size: 0.85em;
                  text-decoration: none;
                  border: 1px solid var(--panel-border);
                  background: var(--panel-bg);
                  color: var(--fg);
                  cursor: pointer;
                  transition: filter 0.15s;
                }
                .share-btn:hover { filter: brightness(1.1); }
              </style>
            </head>
            <body>
              <div class="page">
                <!-- Breadcrumb -->
                <nav class="breadcrumb" aria-label="Breadcrumb">
                  <a href="/">Home</a> &rsaquo;
                  <a href="${escapeHtml(gatewayUrl)}">${escapeHtml(displayName)}</a> &rsaquo;
                  Demo Video
                </nav>

                <!-- App meta -->
                <div class="app-meta-row">
                  <span class="app-icon-emoji" aria-hidden="true">${escapeHtml(icon)}</span>
                  <h1 style="margin:0;">${escapeHtml("$displayName – Demo Video")}</h1>
                </div>
                <div class="app-meta-row">
                  $categoryHtml
                  $tagsHtml
                </div>

                <p class="description">${escapeHtml(description)}</p>

                <!-- Video player -->
                $playerHtml

                <!-- Share row -->
                <div class="share-row">
                  <span class="share-label">Share:</span>
                  <a class="share-btn"
                     href="https://twitter.com/intent/tweet?url=${urlEncode(canonicalUrl)}&text=${urlEncode("Watch the $displayName demo video")}"
                     target="_blank" rel="noopener noreferrer" aria-label="Share on Twitter/X">
                    𝕏 Twitter
                  </a>
                  <a class="share-btn"
                     href="https://www.linkedin.com/sharing/share-offsite/?url=${urlEncode(canonicalUrl)}"
                     target="_blank" rel="noopener noreferrer" aria-label="Share on LinkedIn">
                    in LinkedIn
                  </a>
                  <button class="share-btn" id="copy-link-btn" aria-label="Copy link to clipboard">
                    🔗 Copy Link
                  </button>
                </div>

                <!-- CTA -->
                <div class="cta-block">
                  <div class="cta-icon" aria-hidden="true">${escapeHtml(icon)}</div>
                  <div class="cta-text">
                    <h2>Try ${escapeHtml(displayName)}</h2>
                    <p>Start a new session and experience it yourself – no setup required.</p>
                  </div>
                  <a class="cta-btn" href="${escapeHtml(gatewayUrl)}">
                    Get Started →
                  </a>
                </div>
              </div>

              <script>
                (function() {
                  if (window.ThemeManager) window.ThemeManager.init();
                  var copyBtn = document.getElementById('copy-link-btn');
                  if (copyBtn) {
                    copyBtn.addEventListener('click', function() {
                      var url = "${jsStringEscape(canonicalUrl)}";
                      if (navigator.clipboard) {
                        navigator.clipboard.writeText(url).then(function() {
                          copyBtn.textContent = '✅ Copied!';
                          setTimeout(function() { copyBtn.textContent = '🔗 Copy Link'; }, 2000);
                        });
                      } else {
                        var ta = document.createElement('textarea');
                        ta.value = url;
                        document.body.appendChild(ta);
                        ta.select();
                        document.execCommand('copy');
                        document.body.removeChild(ta);
                        copyBtn.textContent = '✅ Copied!';
                        setTimeout(function() { copyBtn.textContent = '🔗 Copy Link'; }, 2000);
                      }
                    });
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()

        response.writer.use { it.write(html) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun getBaseUrl(request: HttpServletRequest): String {
        val scheme = request.getHeader("X-Forwarded-Proto") ?: request.scheme
        val host = request.getHeader("X-Forwarded-Host") ?: request.serverName
        val port = request.serverPort
        val defaultPort = if (scheme == "https") 443 else 80
        return if (port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
    }

    private fun toYouTubeEmbedUrl(url: String): String {
        // Handle youtu.be/ID and youtube.com/watch?v=ID and youtube.com/embed/ID
        val embedBase = "https://www.youtube.com/embed/"
        return when {
            url.contains("youtu.be/") -> {
                val id = url.substringAfter("youtu.be/").substringBefore("?").substringBefore("#")
                "$embedBase$id"
            }
            url.contains("youtube.com/embed/") -> url
            url.contains("v=") -> {
                val id = url.substringAfter("v=").substringBefore("&").substringBefore("#")
                "$embedBase$id"
            }
            else -> url
        }
    }

    private fun toVimeoEmbedUrl(url: String): String {
        if (url.contains("player.vimeo.com")) return url
        val id = url.substringAfterLast("/").substringBefore("?").substringBefore("#")
        return "https://player.vimeo.com/video/$id"
    }

    private fun buildJsonLd(
        name: String,
        description: String,
        thumbnailUrl: String,
        contentUrl: String,
        embedUrl: String?,
        canonicalUrl: String
    ): String {
        val embedPart = if (embedUrl != null) """
              "embedUrl": "${jsonEscape(embedUrl)}",""" else ""
        return """
            {
              "@context": "https://schema.org",
              "@type": "VideoObject",
              "name": "${jsonEscape(name)} Demo",
              "description": "${jsonEscape(description)}",
              "thumbnailUrl": "${jsonEscape(thumbnailUrl)}",
              "contentUrl": "${jsonEscape(contentUrl)}",
              $embedPart
              "url": "${jsonEscape(canonicalUrl)}",
              "publisher": {
                "@type": "Organization",
                "name": "Cognotik",
                "url": "https://cognotik.com"
              }
            }
        """.trimIndent()
    }

    private fun commonStyles(): String = """
        <style>
          :root {
            --bg: #ffffff; --fg: #222222; --heading-fg: #333333; --muted: #555555;
            --panel-bg: #f9f9f9; --panel-border: #dddddd; --code-bg: #f4f4f4;
            --link-fg: #ffffff; --btn-primary-bg: #337ab7;
            --btn-border: #cccccc;
          }
          [data-theme="dark"] {
            --bg: #1e1e1e; --fg: #e6e6e6; --heading-fg: #f0f0f0; --muted: #b0b0b0;
            --panel-bg: #2a2a2a; --panel-border: #444444; --code-bg: #2a2a2a;
            --link-fg: #ffffff; --btn-primary-bg: #4a90c2;
            --btn-border: #555555;
          }
          @media (prefers-color-scheme: dark) {
            :root:not([data-theme="light"]) {
              --bg: #1e1e1e; --fg: #e6e6e6; --heading-fg: #f0f0f0; --muted: #b0b0b0;
              --panel-bg: #2a2a2a; --panel-border: #444444; --code-bg: #2a2a2a;
              --link-fg: #ffffff; --btn-primary-bg: #4a90c2;
              --btn-border: #555555;
            }
          }
          html, body { background: var(--bg); color: var(--fg); margin: 0; padding: 0; }
          body { font-family: sans-serif; line-height: 1.6; }
          .page { max-width: 900px; margin: 2em auto; padding: 0 1em; }
          h1, h2, h3 { color: var(--heading-fg); }
          .description { color: var(--muted); font-size: 1.05em; margin: 0.5em 0 1em 0; }
          .category {
            display: inline-block; padding: 0.15em 0.6em; font-size: 0.8em;
            background: var(--panel-bg); color: var(--muted);
            border: 1px solid var(--panel-border); border-radius: 12px;
          }
          .tags { display: flex; flex-wrap: wrap; gap: 0.35em; }
          .tag {
            display: inline-block; padding: 0.1em 0.5em; font-size: 0.75em;
            background: var(--code-bg); color: var(--muted);
            border: 1px solid var(--panel-border); border-radius: 4px;
          }
          .app-icon-emoji { font-size: 2rem; line-height: 1; }
          .video-index { list-style: none; padding: 0; }
          .video-index li { margin: 0.5em 0; }
          .video-index a { color: var(--btn-primary-bg); text-decoration: none; font-size: 1.05em; }
          .video-index a:hover { text-decoration: underline; }
        </style>
    """.trimIndent()

    private fun themeScript(): String = """
        <script src="/modules/theme.js"></script>
        <script>if (window.ThemeManager) window.ThemeManager.init();</script>
    """.trimIndent()

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun jsStringEscape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\"); '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n");  '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t");  '<'  -> sb.append("\\u003C")
                '>'  -> sb.append("\\u003E"); '&' -> sb.append("\\u0026")
                else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}