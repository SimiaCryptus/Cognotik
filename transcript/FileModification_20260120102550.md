# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/RobotsTxtParser.kt

```
package com.simiacryptus.cognotik.crawl

import com.simiacryptus.cognotik.util.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Parser and cache for robots.txt files
 */
class RobotsTxtParser {
    private val cache = ConcurrentHashMap<String, RobotsTxt>()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    data class RobotsTxt(
        val disallowedPaths: List<String> = emptyList(),
        val allowedPaths: List<String> = emptyList(),
        val crawlDelay: Long? = null,
        val sitemaps: List<String> = emptyList()
    )

    /**
     * Check if a URL is allowed by robots.txt
     */
    fun isAllowed(url: String, userAgent: String = "*"): Boolean {
        try {
            val uri = URI.create(url)
            val baseUrl =
                "${uri.scheme}://${uri.host}${if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
            val robotsTxt = getRobotsTxt(baseUrl)

            val path = uri.path + (if (uri.query != null) "?${uri.query}" else "")

            // Check allowed paths first (they take precedence)
            if (robotsTxt.allowedPaths.any { matchesPattern(path, it) }) {
                log.debug("URL allowed by robots.txt allow rule: $url")
                return true
            }

            // Check disallowed paths
            if (robotsTxt.disallowedPaths.any { matchesPattern(path, it) }) {
                log.debug("URL disallowed by robots.txt: $url")
                return false
            }

            log.debug("URL allowed by robots.txt (no matching rules): $url")
            return true
        } catch (e: Exception) {
            log.warn("Error checking robots.txt for $url, allowing by default", e)
            return true
        }
    }

    /**
     * Get crawl delay in milliseconds for a domain
     */
    fun getCrawlDelay(url: String): Long? {
        try {
            val uri = URI.create(url)
            val baseUrl =
                "${uri.scheme}://${uri.host}${if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
            return getRobotsTxt(baseUrl).crawlDelay
        } catch (e: Exception) {
            log.warn("Error getting crawl delay for $url", e)
            return null
        }
    }

    /**
     * Fetch and parse robots.txt for a domain
     */
    private fun getRobotsTxt(baseUrl: String): RobotsTxt {
        return cache.getOrPut(baseUrl) {
            try {
                val robotsUrl = "$baseUrl/robots.txt"
                log.debug("Fetching robots.txt from: $robotsUrl")

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (compatible; CognotikBot/1.0)")
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 200) {
                    parseRobotsTxt(response.body())
                } else {
                    log.debug("No robots.txt found at $robotsUrl (status: ${response.statusCode()})")
                    RobotsTxt() // Empty rules = allow all
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch robots.txt from $baseUrl, allowing all", e)
                RobotsTxt() // On error, allow all
            }
        }
    }

    /**
     * Parse robots.txt content
     */
    private fun parseRobotsTxt(content: String): RobotsTxt {
        val disallowedPaths = mutableListOf<String>()
        val allowedPaths = mutableListOf<String>()
        val sitemaps = mutableListOf<String>()
        var crawlDelay: Long? = null
        var isRelevantUserAgent = false

        content.lines().forEach { line ->
            val trimmed = line.trim()

            // Skip comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@forEach
            }

            val parts = trimmed.split(":", limit = 2)
            if (parts.size != 2) return@forEach

            val directive = parts[0].trim().lowercase()
            val value = parts[1].trim()

            when (directive) {
                "user-agent" -> {
                    // Match * or specific bot names
                    isRelevantUserAgent = value == "*" || value.lowercase().contains("cognotik")
                }

                "disallow" -> {
                    if (isRelevantUserAgent && value.isNotEmpty()) {
                        disallowedPaths.add(value)
                    }
                }

                "allow" -> {
                    if (isRelevantUserAgent && value.isNotEmpty()) {
                        allowedPaths.add(value)
                    }
                }

                "crawl-delay" -> {
                    if (isRelevantUserAgent) {
                        crawlDelay = value.toDoubleOrNull()?.let { (it * 1000).toLong() }
                    }
                }

                "sitemap" -> {
                    sitemaps.add(value)
                }
            }
        }

        log.debug("Parsed robots.txt: ${disallowedPaths.size} disallow rules, ${allowedPaths.size} allow rules, crawl-delay: $crawlDelay")
        return RobotsTxt(disallowedPaths, allowedPaths, crawlDelay, sitemaps)
    }

    /**
     * Check if a path matches a robots.txt pattern
     */
    private fun matchesPattern(path: String, pattern: String): Boolean {
        // Convert robots.txt pattern to regex
        // * matches any sequence of characters
        // $ at end means end of URL
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", "\\?")
            .let { if (it.endsWith("$")) it else "$it.*" }

        return try {
            Regex("^$regexPattern").matches(path)
        } catch (e: Exception) {
            log.info("Invalid robots.txt pattern: $pattern", e)
            false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RobotsTxtParser::class.java)
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new `README.md` file for the `com.simiacryptus.cognotik.crawl` package, based on the `RobotsTxtParser.kt` implementation.

### webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/README.md
```markdown
# Crawl Utilities

This package provides utilities for web crawling and robot protocol compliance.

## RobotsTxtParser

The `RobotsTxtParser` class is responsible for fetching, parsing, and caching `robots.txt` files. It ensures that the crawler respects the directives set by website administrators.

### Key Features

*   **Caching**: Uses a `ConcurrentHashMap` to cache parsed `RobotsTxt` objects per domain, minimizing network requests.
*   **Directive Support**: Handles `User-agent`, `Allow`, `Disallow`, `Crawl-delay`, and `Sitemap` directives.
*   **Pattern Matching**: Supports standard `robots.txt` pattern matching, including wildcards (`*`) and end-of-string anchors (`$`).
*   **Bot Identification**: Identifies as `CognotikBot/1.0` and prioritizes rules specific to this agent or the global wildcard (`*`).
*   **Resilience**: Defaults to allowing access if a `robots.txt` file is missing or unreachable.

### Public API

#### `isAllowed(url: String, userAgent: String = "*"): Boolean`
Determines if a specific URL is permitted to be crawled.
- **Precedence**: Explicit `Allow` rules take precedence over `Disallow` rules.
- **Default**: Returns `true` if no matching rules are found or if an error occurs during fetching.

#### `getCrawlDelay(url: String): Long?`
Retrieves the recommended crawl delay for a domain in milliseconds.
- **Returns**: The delay value if specified in `robots.txt`, otherwise `null`.

### Implementation Details

- **HTTP Client**: Utilizes Java's `HttpClient` with a 10-second connection timeout and follows standard redirects.
- **Regex Engine**: Path patterns from `robots.txt` are converted into regular expressions for efficient matching.
- **Data Model**: The `RobotsTxt` data class stores disallowed/allowed paths, crawl delays, and sitemap locations.

### Example Usage

```kotlin
val parser = RobotsTxtParser()
val url = "https://example.com/data/index.html"

if (parser.isAllowed(url)) {
    val delay = parser.getCrawlDelay(url) ?: 0L
    Thread.sleep(delay)
    // Proceed with crawling...
}
```
```

### Summary of Changes
- Created a new `README.md` in `webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/`.
- Documented the `RobotsTxtParser` class, including its caching mechanism, supported directives, and public API.
- Included implementation details regarding the HTTP client and pattern matching logic.
- Added a usage example in Kotlin.
- Followed the transformation rules specified in `index_docs.md`.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/README.md</a> Updated
