# Web Crawler Agent Documentation

## Overview

The `CrawlerAgentTask` is a sophisticated web crawling and content analysis system that can search the web, fetch content from URLs, analyze pages using AI, and automatically follow relevant links. It's designed to gather and synthesize information from multiple web sources based on specific queries or goals.

## Key Features

- **Multiple Seeding Methods**: Start crawling from Google search results or direct URLs
- **Intelligent Content Fetching**: Support for HTML, PDF, DOCX, and other document formats
- **AI-Powered Analysis**: Uses language models to extract relevant information from pages
- **Automatic Link Following**: Discovers and follows relevant links based on analysis
- **Robots.txt Compliance**: Respects website crawling rules and rate limits
- **Priority Queue**: Processes pages based on relevance scores and depth
- **Concurrent Processing**: Handles multiple pages simultaneously for efficiency
- **Content Caching**: Avoids re-fetching the same URLs
- **Comprehensive Logging**: Detailed tracking of crawling progress and errors

## Architecture

### Core Components

#### 1. CrawlerAgentTask
The main orchestrator that manages the crawling workflow:
- Initializes the page queue with seed URLs
- Manages concurrent page processing
- Coordinates content fetching and analysis
- Generates final summaries

#### 2. Seed Methods
Strategies for initializing the crawler:

**GoogleSearch**: Searches Google and extracts top results
```kotlin
enum class SeedMethod {
    GoogleSearch,
    DirectUrls
}
```

**DirectUrls**: Uses explicitly provided URLs

#### 3. Fetch Strategies
Methods for retrieving web content:

**HttpClient**: Standard HTTP client with SSL support
- Handles HTML, text, and document formats
- Extracts text from PDFs, DOCX, etc.
- Simplifies HTML for better analysis

**Selenium**: Browser automation for JavaScript-heavy sites
- Renders dynamic content
- Captures screenshots
- Handles complex interactions

#### 4. Content Processing Pipeline

```
URL → Fetch → Simplify → Analyze → Extract Links → Queue New URLs
```

1. **Fetch**: Retrieve content using selected strategy
2. **Simplify**: Clean HTML, extract text from documents
3. **Analyze**: Use AI to extract relevant information
4. **Extract Links**: Find and score new URLs to follow
5. **Queue**: Add promising links to priority queue

## Configuration

### Task Type Configuration

```kotlin
class CrawlerTaskTypeConfig(
    val seed_method: SeedMethod? = SeedMethod.GoogleSearch,
    val fetch_method: FetchMethod? = FetchMethod.HttpClient,
    val allowed_domains: String? = null,
    val respect_robots_txt: Boolean? = true,
    val max_pages_per_task: Int? = 30,
    val max_depth: Int? = 3,
    val max_queue_size: Int? = 100,
    val concurrent_page_processing: Int? = 3,
    val max_final_output_size: Int? = 15000,
    val min_content_length: Int? = 500,
    val follow_links: Boolean? = true,
    val allow_revisit_pages: Boolean? = false,
    val create_final_summary: Boolean? = true
)
```

### Execution Configuration

```kotlin
class CrawlerTaskExecutionConfigData(
    val search_query: String? = null,
    val direct_urls: List<String>? = null,
    val content_queries: Any? = null,
    val allowed_domains: String? = null
)
```

## Usage Examples

### Example 1: Google Search with Analysis

```kotlin
val config = CrawlerTaskExecutionConfigData(
    search_query = "artificial intelligence recent developments",
    content_queries = """
        Extract:
        - Key technological breakthroughs
        - Companies involved
        - Potential applications
        - Publication dates
    """,
    allowed_domains = "arxiv.org nature.com sciencedaily.com"
)
```

### Example 2: Direct URL Analysis

```kotlin
val config = CrawlerTaskExecutionConfigData(
    direct_urls = listOf(
        "https://example.com/article1",
        "https://example.com/article2"
    ),
    content_queries = "Summarize the main arguments and supporting evidence"
)
```

### Example 3: Deep Crawl with Link Following

```kotlin
val typeConfig = CrawlerTaskTypeConfig(
    seed_method = SeedMethod.DirectUrls,
    max_depth = 5,
    max_pages_per_task = 100,
    follow_links = true,
    concurrent_page_processing = 5
)
```

## Data Structures

### LinkData
Represents a URL to be crawled:

```kotlin
data class LinkData(
    val link: String?,
    val title: String?,
    val tags: List<String>?,
    val relevance_score: Double = 100.0,
    var depth: Int = 0,
    var started: Boolean = false,
    var completed: Boolean = false,
    var error: String? = null
)
```

**Priority Calculation**: `relevance_score / (depth + 1.0)`
- Higher relevance = higher priority
- Lower depth = higher priority

### ParsedPage
Result of AI analysis:

```kotlin
data class ParsedPage(
    val page_type: PageType,  // OK, Error, Irrelevant
    val page_information: Any?,
    val tags: List<String>?,
    val link_data: List<LinkData>?
)
```

## Processing Flow

### 1. Initialization
```
┌─────────────────┐
│  Seed Method    │
│  (Google/URLs)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Priority Queue │
│  (LinkData)     │
└─────────────────┘
```

### 2. Crawling Loop
```
┌──────────────────────────────────────────┐
│  While (pages < max && errors < limit)   │
│  ┌────────────────────────────────────┐  │
│  │  Get Next Page from Queue          │  │
│  │  (Highest Priority)                │  │
│  └──────────┬─────────────────────────┘  │
│             │                             │
│             ▼                             │
│  ┌────────────────────────────────────┐  │
│  │  Fetch Content                     │  │
│  │  (HTTP/Selenium)                   │  │
│  └──────────┬─────────────────────────┘  │
│             │                             │
│             ▼                             │
│  ┌────────────────────────────────────┐  │
│  │  Simplify/Extract Text             │  │
│  └──────────┬─────────────────────────┘  │
│             │                             │
│             ▼                             │
│  ┌────────────────────────────────────┐  │
│  │  AI Analysis                       │  │
│  │  (Extract Information)             │  │
│  └──────────┬─────────────────────────┘  │
│             │                             │
│             ▼                             │
│  ┌────────────────────────────────────┐  │
│  │  Extract & Score Links             │  │
│  └──────────┬─────────────────────────┘  │
│             │                             │
│             ▼                             │
│  ┌────────────────────────────────────┐  │
│  │  Add to Queue (if relevant)        │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

### 3. Finalization
```
┌─────────────────┐
│  All Results    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Create Summary │
│  (if enabled)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Final Output   │
└─────────────────┘
```

## Content Fetching Strategies

### HttpClient Strategy

**Advantages**:
- Fast and lightweight
- Good for static content
- Handles documents (PDF, DOCX)
- Low resource usage

**Limitations**:
- Cannot execute JavaScript
- May miss dynamic content
- Limited interaction capabilities

**Supported Formats**:
- HTML pages
- Plain text
- PDF documents
- Microsoft Office (DOC, DOCX, XLS, XLSX, PPT, PPTX)
- OpenDocument formats (ODT, ODS, ODP)
- RTF files

### Selenium Strategy

**Advantages**:
- Executes JavaScript
- Renders dynamic content
- Can interact with pages
- Captures visual state

**Limitations**:
- Slower than HTTP
- Higher resource usage
- Requires browser driver
- More complex setup

**Use Cases**:
- Single-page applications
- JavaScript-heavy sites
- Sites requiring interaction
- Visual verification needed

## Robots.txt Compliance

The crawler respects robots.txt rules when enabled:

### Features
- **Automatic Fetching**: Downloads and caches robots.txt per domain
- **Rule Parsing**: Supports Disallow, Allow, Crawl-delay, Sitemap
- **User-Agent Matching**: Respects rules for "*" and "CognotikBot"
- **Pattern Matching**: Handles wildcards and path patterns
- **Crawl Delays**: Automatically applies specified delays

### Example robots.txt
```
User-agent: *
Disallow: /admin/
Disallow: /private/
Allow: /public/
Crawl-delay: 1

Sitemap: https://example.com/sitemap.xml
```

## Content Analysis

### AI-Powered Extraction

The crawler uses language models to:
1. **Classify Pages**: Determine if content is relevant, error, or irrelevant
2. **Extract Information**: Pull out specific data based on queries
3. **Score Links**: Evaluate relevance of discovered URLs
4. **Summarize Content**: Create concise summaries of findings

### Analysis Prompt Structure

```kotlin
val prompt = """
Below are analyses of different parts of a web page related to this goal: $analysisGoal

Create a unified summary that combines the key insights from all parts.
Use markdown formatting for your response.
Identify the most important links that should be followed up on.
"""
```

### Content Chunking

For large pages (>50KB):
1. Split into manageable chunks
2. Analyze each chunk separately
3. Combine results into unified summary
4. Preserve context across chunks

## Output and Storage

### Directory Structure
```
.websearch/
├── raw_pages/          # Original HTML
├── reduced_pages/      # Simplified HTML
├── documents/          # Downloaded files
├── extracted_text/     # Text from documents
├── text_pages/         # Plain text content
├── error/              # Failed analyses
└── irrelevant/         # Filtered content
```

### Analysis Files
Each analyzed page is saved as:
```
{url_safe}_{index}_{timestamp}.md
```

With metadata header:
```markdown
<!-- {
  "url": "https://example.com/page",
  "timestamp": "2024-01-15T10:30:00",
  "index": 1,
  "query": "search query",
  "content_query": "analysis goal"
} -->

## Page Title

Analysis content...
```

## Error Handling

### Retry Logic
- Tracks retry count per URL
- Implements exponential backoff
- Maximum retry attempts configurable

### Error Types
1. **Network Errors**: Connection failures, timeouts
2. **HTTP Errors**: 4xx, 5xx status codes
3. **Parse Errors**: Invalid HTML, malformed documents
4. **Analysis Errors**: AI model failures
5. **Resource Errors**: Memory limits, disk space

### Error Recovery
```kotlin
try {
    // Fetch and process
} catch (e: Exception) {
    log.error("Error processing URL: $url", e)
    errorCount.incrementAndGet()
    page.error = e.message
    // Continue with next page
}
```

## Performance Optimization

### Concurrent Processing
- Configurable worker threads
- Completion service for task management
- Active task tracking

### Caching
- URL content cache (in-memory)
- Robots.txt cache (per domain)
- Prevents redundant fetches

### Queue Management
- Priority-based processing
- Maximum queue size limits
- Duplicate URL detection

### Resource Limits
- Maximum pages per task
- Maximum crawl depth
- Content size limits
- Queue size limits

## Best Practices

### 1. Define Clear Goals
```kotlin
content_queries = """
Extract specific information:
- Data point 1
- Data point 2
- Evaluation criteria
- Filtering priorities
"""
```

### 2. Restrict Domains
```kotlin
allowed_domains = "example.com trusted-source.org"
```

### 3. Set Reasonable Limits
```kotlin
max_pages_per_task = 50  // Don't crawl too much
max_depth = 3            // Prevent infinite loops
concurrent_page_processing = 3  // Balance speed/resources
```

### 4. Respect Websites
```kotlin
respect_robots_txt = true  // Always enable
// Crawler automatically applies delays
```

### 5. Monitor Progress
- Check logs for errors
- Review intermediate results
- Adjust configuration as needed

## Troubleshooting

### Common Issues

**1. No Results Found**
- Check search query specificity
- Verify allowed_domains aren't too restrictive
- Ensure URLs are accessible

**2. Too Many Errors**
- Reduce concurrent_page_processing
- Check network connectivity
- Verify robots.txt compliance

**3. Irrelevant Content**
- Refine content_queries
- Adjust relevance scoring
- Restrict domains more carefully

**4. Memory Issues**
- Reduce max_queue_size
- Lower max_pages_per_task
- Decrease concurrent_page_processing

**5. Slow Performance**
- Increase concurrent_page_processing
- Use HttpClient instead of Selenium
- Reduce max_depth

## API Reference

### Main Methods

#### `run()`
Executes the crawling task
```kotlin
fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
)
```

#### `addToQueue()`
Adds a new URL to the processing queue
```kotlin
fun addToQueue(
    newLink: LinkData,
    maxDepth: Int,
    maxQueueSize: Int
): Boolean
```

#### `getNextPage()`
Retrieves the highest priority page from queue
```kotlin
fun getNextPage(): LinkData?
```

#### `fetchAndProcessUrl()`
Fetches and processes content from a URL
```kotlin
private fun fetchAndProcessUrl(
    url: String,
    webSearchDir: File,
    index: Int,
    pool: ExecutorService,
    fetchStrategy: FetchStrategy
): String
```

#### `transformContent()`
Analyzes content using AI
```kotlin
private fun transformContent(
    content: String,
    analysisGoal: String,
    orchestrationConfig: OrchestrationConfig,
    task: SessionTask
): ParsedResponse<ParsedPage>
```

## Advanced Features

### Custom Fetch Strategies

Implement `FetchStrategy` interface:
```kotlin
interface FetchStrategy {
    fun fetch(
        url: String,
        webSearchDir: File,
        index: Int,
        pool: ExecutorService,
        orchestrationConfig: OrchestrationConfig
    ): String
}
```

### Custom Seed Methods

Implement `SeedStrategy` interface:
```kotlin
interface SeedStrategy {
    fun getSeedItems(
        executionConfig: CrawlerTaskExecutionConfigData?,
        orchestrationConfig: OrchestrationConfig
    ): List<LinkData>?
}
```

### Link Extraction

Automatic extraction from:
- Markdown links: `[text](url)`
- HTML anchor tags: `<a href="url">`
- Structured data from AI analysis

### Content Filtering

Multiple filtering stages:
1. **Domain whitelist/blacklist**
2. **Robots.txt compliance**
3. **Duplicate detection**
4. **Relevance scoring**
5. **Content length requirements**

## Security Considerations

### SSL/TLS
- Accepts all certificates (configurable)
- Supports HTTPS connections
- Handles certificate errors gracefully

### Rate Limiting
- Respects robots.txt crawl delays
- Configurable concurrent requests
- Automatic backoff on errors

### Content Validation
- URL format validation
- Content type checking
- Size limit enforcement
- Malicious content detection

## Future Enhancements

Potential improvements:
- [ ] Distributed crawling support
- [ ] Advanced JavaScript rendering
- [ ] Image and video analysis
- [ ] Multi-language support
- [ ] Custom extraction rules
- [ ] Real-time monitoring dashboard
- [ ] Export to various formats
- [ ] Integration with knowledge graphs
