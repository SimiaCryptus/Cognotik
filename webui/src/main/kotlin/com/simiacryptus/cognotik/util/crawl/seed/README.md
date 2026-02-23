# Online Seed Package

## Overview

The `seed` package provides various strategies for discovering and collecting initial URLs (seed items) for web crawling
operations. It supports multiple search engines and methods for generating starting points for web crawlers.

## Components

### Core Interfaces

#### `SeedStrategy`

The main interface for implementing seed collection strategies. Each strategy must implement:

- `isEnabled()`: Determines if the strategy is available for use

#### `SeedMethodFactory`

Factory interface for creating `SeedStrategy` instances with proper user context and task configuration.

#### `SeedItem`

Data class representing a discovered URL with metadata:

- `link`: The URL to crawl
- `title`: Human-readable title
- `tags`: Optional categorization tags
- `additionalData`: Extra metadata from the source

### Seed Methods

#### `DirectUrls`

Directly uses a list of provided URLs without any search or discovery.

**Use Case**: When you have specific URLs to crawl
**Configuration**: Requires `direct_urls` list in task config
**Enabled**: Always available

#### `GoogleProxy`

Uses a proxy endpoint to perform Google searches without requiring API credentials.

**Use Case**: Quick Google searches without API setup
**Configuration**:

- Requires `search_query` in task config
- Uses environment variable `GOOGLE_SEARCH_PROXY_ENDPOINT` (defaults to AWS endpoint)
  **Enabled**: Always available
  **Limitations**: Returns up to 20 results

#### `GoogleSearch`

Direct integration with Google Custom Search API.

**Use Case**: Production Google searches with full API access
**Configuration**:

- Requires `search_query` in task config
- Requires Google API key and Search Engine ID in user settings
  **Enabled**: Only when user has configured Google API credentials
  **Limitations**: Subject to Google API quotas and rate limits

#### `SearchAPISearch`

Base class for SearchAPI.io integrations, supporting multiple search engines:

##### Available Engines:

- **SearchIO_Google_Search**: Standard Google web search
- **SearchIO_Google_Maps**: Location-based business search
- **SearchIO_Google_Scholar**: Academic paper search
- **SearchIO_Google_Patents**: Patent database search
- **SearchIO_Google_News**: News article search
- **SearchIO_Google_Jobs**: Job listing search
- **SearchIO_Amazon**: Amazon product search
- **SearchIO_Bing**: Bing web search
- **SearchIO_EBay**: eBay product search

**Use Case**: Unified API for multiple search engines
**Configuration**:

- Requires `search_query` in task config
- Requires SearchAPI.io API key in user settings
  **Enabled**: Only when user has configured SearchAPI.io credentials
  **Limitations**: Returns up to 20 results per query

## Usage Example

```kotlin
// Create a seed strategy
val seedMethod = SeedMethod.GoogleProxy
val strategy = seedMethod.createStrategy(crawlerTask, user)

// Check if strategy is available
if (strategy.isEnabled()) {
    // Get seed items
    val seedItems = strategy.getSeedItems(taskConfig, orchestrationConfig)

    // Process results
    seedItems?.forEach { item ->
        println("Found: ${item.title} at ${item.link}")
        println("Relevance: ${item.relevance_score}")
    }
}
```

## Configuration

### Task Configuration

```kotlin
data class CrawlerTaskExecutionConfigData(
    val search_query: String? = null,      // For search-based methods
    val direct_urls: List<String> = emptyList(), // For DirectUrls method
    // ... other configuration fields
    val crawl_depth: Int = 1,
    val max_pages: Int = 100
)
```

### User Settings

Required API credentials in user settings:

- **Google Search**: API key and Search Engine ID
- **SearchAPI.io**: API key

## Error Handling

All seed methods implement robust error handling:

- Missing configuration throws `IllegalArgumentException`
- API failures throw `RuntimeException` with descriptive messages
- Empty results return empty lists (not errors)

## Logging

Comprehensive logging at multiple levels:

- **INFO**: Method start/completion, result counts
- **DEBUG**: Configuration details, parsing steps
- **WARN**: Invalid data, missing results
- **ERROR**: API failures, configuration issues

## Best Practices

1. **Choose the Right Method**:

- Use `DirectUrls` for known URLs
- Use `GoogleProxy` for quick testing
- Use `GoogleSearch` for production with API access
- Use `SearchAPISearch` variants for specialized searches

2. **Handle Rate Limits**:

- Implement delays between requests
- Monitor API quotas
- Use appropriate result limits

3. **Validate Results**:

- Check `isEnabled()` before using a strategy
- Handle empty result sets gracefully
- Validate URLs before crawling

4. **Security**:

- Store API keys securely in user settings
- Never log API keys
- Use HTTPS endpoints only

## Extension

To add a new seed method:

1. Implement `SeedMethodFactory`:

```kotlin
class CustomSearch : SeedMethodFactory {
    override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy {
        return object : SeedStrategy {
            override fun getSeedItems(...): List<SeedItem> {
                // Implementation
            }

            override fun isEnabled(): Boolean {
                // Check availability
            }
        }
    }
}
```

2. Add to `SeedMethod` enum:

```kotlin
enum class SeedMethod : SeedMethodFactory {
    CustomSearch {
        override fun createStrategy(...) = CustomSearch().createStrategy(...)
    }
}
```

## Dependencies

- Jackson for JSON parsing
- Java HTTP Client for API requests
- CognoTik platform services for user settings


- `getSeedItems()`: Returns a list of `SeedItem` objects (or null) based on task configuration
- `relevance_score`: Relevance rating (1-100, defaults to 100.0)
- **SearchIO_DuckDuckGo**: DuckDuckGo search
- Invalid URLs (non-HTTP/HTTPS or malformed) are filtered out