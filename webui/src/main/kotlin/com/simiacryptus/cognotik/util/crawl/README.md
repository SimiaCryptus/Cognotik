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