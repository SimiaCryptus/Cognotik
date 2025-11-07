# Online Tools Package

## Overview

The `online` package provides a comprehensive framework for web crawling, content extraction, and intelligent data
processing. It combines AI-powered analysis with configurable processing strategies to extract structured information
from websites, making it ideal for research, data mining, competitive analysis, and automated information gathering.

## Architecture

The package is organized into four main sub-packages:

```
online/
├── seed/          # URL discovery and seed generation
├── fetch/         # Content retrieval strategies
├── processing/    # Page analysis and data extraction
└── CrawlerAgentTask.kt  # Main orchestration logic
```

### Component Flow

```
[Seed Strategy] → [URL Queue] → [Fetch Strategy] → [Processing Strategy] → [Output]
     ↓                              ↓                       ↓
  Initial URLs              Raw Content            Structured Data
```

## Core Components

### 1. CrawlerAgentTask

The main orchestrator that coordinates all crawling operations.

**Key Features:**

- Intelligent URL queue management with priority-based processing
- Concurrent page processing with configurable parallelism
- Automatic link discovery and depth-limited crawling
- Robots.txt compliance
- Domain whitelisting/blacklisting
- Real-time transcript generation
- Comprehensive error handling and recovery

**Configuration:**

```kotlin
data class CrawlerTaskTypeConfig(
  val seed_method: SeedMethod? = SeedMethod.GoogleProxy,
  val fetch_method: FetchMethod? = FetchMethod.HttpClient,
  val processing_strategy: ProcessingStrategyType? = ProcessingStrategyType.DefaultSummarizer,
  val allowed_domains: String? = null,
  val respect_robots_txt: Boolean? = true,
  val max_pages_per_task: Int? = 30,
  val max_depth: Int? = 3,
  val max_queue_size: Int? = 100,
  val concurrent_page_processing: Int? = 3,
  val follow_links: Boolean? = true,
  val allow_revisit_pages: Boolean? = false,
  val create_final_summary: Boolean? = true,
  val generate_transcript: Boolean? = true
)
```

### 2. Seed Package

Provides multiple strategies for discovering initial URLs to crawl.

**Available Methods:**

- **DirectUrls**: Use provided URLs directly
- **GoogleProxy**: Quick Google searches via proxy
- **GoogleSearch**: Full Google Custom Search API integration
- **SearchAPI.io**: Unified API for multiple search engines (Google, Bing, DuckDuckGo, Amazon, eBay, etc.)

See [seed/README.md](seed/README.md) for detailed documentation.

### 3. Fetch Package

Handles content retrieval from URLs with multiple strategies.

**Available Methods:**

- **HttpClient**: Fast, efficient HTTP-based fetching (default)
- **Selenium**: Browser-based rendering for JavaScript-heavy sites

**Supported Content Types:**

- HTML pages
- PDF documents
- Microsoft Office files (DOC, DOCX, XLS, XLSX, PPT, PPTX)
- OpenDocument formats (ODT)
- Plain text files

See [fetch/README.md](fetch/README.md) for detailed documentation.

### 4. Processing Package

Provides specialized strategies for analyzing and extracting data from web pages.

**Available Strategies:**

1. **DefaultSummarizer**: General content analysis and summarization
2. **FactChecking**: Multi-source claim verification with evidence tracking
3. **JobMatching**: Automated job search and application material generation
4. **SchemaExtraction**: Structured data extraction with custom schemas
5. **DataTableAccumulation**: Build comprehensive datasets with configurable columns

See [processing/README.md](processing/README.md) for detailed documentation.

## Quick Start

### Basic Web Search and Analysis

```kotlin
val task = CrawlerAgentTask(
  orchestrationConfig = config,
  planTask = CrawlerTaskExecutionConfigData(
    search_query = "artificial intelligence trends 2024",
    content_queries = "Extract key trends, predictions, and expert opinions"
  )
)

// Configure task type
task.typeConfig = CrawlerTaskTypeConfig(
  seed_method = SeedMethod.GoogleProxy,
  fetch_method = FetchMethod.HttpClient,
  processing_strategy = ProcessingStrategyType.DefaultSummarizer,
  max_pages_per_task = 10
)

// Run the task
task.run(agent, messages, sessionTask, resultFn, orchestrationConfig)
```

### Job Search Automation

```kotlin
val task = CrawlerAgentTask(
  orchestrationConfig = config,
  planTask = CrawlerTaskExecutionConfigData(
    search_query = "senior software engineer remote",
    content_queries = JobMatchingConfig(
      user_experience = "5 years Kotlin, AWS, microservices...",
      target_roles = listOf("Senior Engineer", "Tech Lead"),
      required_skills = listOf("Kotlin", "AWS", "Docker"),
      preferred_locations = listOf("Remote", "San Francisco"),
      min_match_score = 0.7,
      work_arrangement_preference = "remote",
      min_salary = 150000
    )
  )
)

task.typeConfig = CrawlerTaskTypeConfig(
  processing_strategy = ProcessingStrategyType.JobMatching,
  max_pages_per_task = 20
)
```

### Data Extraction

```kotlin
val task = CrawlerAgentTask(
  orchestrationConfig = config,
  planTask = CrawlerTaskExecutionConfigData(
    search_query = "best laptops 2024",
    content_queries = SchemaExtractionConfig(
      schema_definition = """
        {
          "model": "string",
          "price": "number",
          "rating": "number",
          "specs": {
            "processor": "string",
            "ram": "string",
            "storage": "string"
          }
        }
      """,
      aggregate_results = true,
      deduplicate = true
    )
  )
)

task.typeConfig = CrawlerTaskTypeConfig(
  processing_strategy = ProcessingStrategyType.SchemaExtraction,
  max_pages_per_task = 15
)
```

### Fact Checking

```kotlin
val task = CrawlerAgentTask(
  orchestrationConfig = config,
  planTask = CrawlerTaskExecutionConfigData(
    search_query = "company X employee count revenue",
    content_queries = FactCheckingConfig(
      claims_to_verify = listOf(
        "Company X has 10,000 employees",
        "Company X revenue is $1B annually"
      ),
      required_sources = 3,
      confidence_threshold = 0.8
    )
  )
)

task.typeConfig = CrawlerTaskTypeConfig(
  processing_strategy = ProcessingStrategyType.FactChecking,
  max_pages_per_task = 20
)
```

## Advanced Features

### Domain Whitelisting

Restrict crawling to specific domains or URL prefixes:

```kotlin
task.typeConfig = CrawlerTaskTypeConfig(
  allowed_domains = "example.com wikipedia.org https://docs.example.com/api",
  // Space-separated list of domains or URL prefixes
)
```

### Link Following

Automatically discover and follow links found in analyzed pages:

```kotlin
task.typeConfig = CrawlerTaskTypeConfig(
  follow_links = true,
  max_depth = 3,  // How many levels deep to crawl
  max_queue_size = 100  // Maximum URLs in queue
)
```

### Robots.txt Compliance

Respect website crawling rules:

```kotlin
task.typeConfig = CrawlerTaskTypeConfig(
  respect_robots_txt = true  // Honors robots.txt and crawl delays
)
```

### Concurrent Processing

Control parallelism for faster crawling:

```kotlin
task.typeConfig = CrawlerTaskTypeConfig(
  concurrent_page_processing = 5  // Process 5 pages simultaneously
)
```

## Output Structure

The crawler generates organized output in the `.websearch` directory:

```
.websearch/
├── crawler_transcript.md          # Real-time processing log
├── raw_pages/                     # Original HTML content
├── reduced_pages/                 # Simplified HTML
├── documents/                     # Downloaded documents
├── extracted_text/                # Text from documents
├── aggregated_data.json          # Extracted structured data
├── data_table.csv                # Tabular datasets
└── job_matches/                  # Job application materials
    ├── Company_Position_timestamp.md
    └── ...
```

## Transcript Generation

The crawler generates a detailed markdown transcript of all operations:

```markdown
# Crawler Agent Transcript

**Started:** 2024-01-15 10:30:00
**Search Query:** artificial intelligence trends

## Seed Links
1. [AI Trends 2024](https://example.com/ai-trends)
   - Relevance: 95.0

### Processing Page 1: [AI Trends 2024](https://example.com/ai-trends)
**Started:** 10:30:15
**Completed:** 10:30:45
**Processing Time:** 30000ms

[Content analysis...]

### Link Processing Summary
**Links Found:** 15, **Added to Queue:** 8, **Skipped:** 7

## Final Summary
[Comprehensive analysis of all processed pages...]
```

## Error Handling

The package implements comprehensive error handling:

1. **Network Errors**: Automatic retries with exponential backoff
2. **Content Errors**: Graceful handling of malformed content
3. **Processing Errors**: Individual page failures don't stop the crawl
4. **Resource Limits**: Automatic cleanup and memory management
5. **Early Termination**: Strategies can stop crawling when goals are met

## Performance Considerations

### Memory Management

- URL queue size limits prevent memory exhaustion
- Content caching with size limits
- Automatic cleanup of temporary resources
- Selenium WebDriver cleanup on task completion

### Speed Optimization

- Concurrent page processing (configurable)
- HTTP connection pooling
- Content size limits (5MB HTML, 10MB documents)
- Early termination when goals are met
- Priority-based URL queue

### Cost Control

- Page limits per task
- Depth limits for crawling
- Domain restrictions
- Robots.txt compliance
- Configurable processing strategies

## Best Practices

### 1. Choose the Right Strategy

- **Research & Analysis**: Use `DefaultSummarizer`
- **Claim Verification**: Use `FactChecking`
- **Job Hunting**: Use `JobMatching`
- **Data Mining**: Use `SchemaExtraction` or `DataTableAccumulation`

### 2. Configure Appropriately

```kotlin
// For quick research (fast, limited scope)
CrawlerTaskTypeConfig(
  max_pages_per_task = 10,
  max_depth = 2,
  concurrent_page_processing = 3
)

// For comprehensive analysis (thorough, slower)
CrawlerTaskTypeConfig(
  max_pages_per_task = 50,
  max_depth = 4,
  concurrent_page_processing = 5,
  follow_links = true
)

// For targeted extraction (focused, efficient)
CrawlerTaskTypeConfig(
  max_pages_per_task = 20,
  max_depth = 2,
  allowed_domains = "target-site.com",
  follow_links = false
)
```

### 3. Handle Results

```kotlin
// Access structured data
val extractedData = File(".websearch/aggregated_data.json")
  .readText()
  .let { ObjectMapper().readValue(it, List::class.java) }

// Read transcript
val transcript = File(".websearch/crawler_transcript.md").readText()

// Process job matches
File(".websearch/job_matches").listFiles()?.forEach { jobReport ->
  println("Found match: ${jobReport.name}")
}
```

### 4. Monitor Progress

```kotlin
// Enable transcript for real-time monitoring
task.typeConfig = CrawlerTaskTypeConfig(
  generate_transcript = true
)

// Check logs for detailed operation info
// Logs include: URL queue status, processing times, error rates
```

## Security Considerations

1. **API Keys**: Store securely in user settings, never log
2. **Domain Restrictions**: Use `allowed_domains` to prevent unauthorized crawling
3. **Robots.txt**: Respect website policies with `respect_robots_txt = true`
4. **Rate Limiting**: Built-in delays and concurrent processing limits
5. **Content Validation**: All URLs and content validated before processing

## Troubleshooting

### Common Issues

**No results returned:**

- Check search query or direct URLs
- Verify API credentials (for Google/SearchAPI methods)
- Check domain restrictions
- Review transcript for errors

**Too many errors:**

- Reduce concurrent processing
- Check network connectivity
- Verify target sites are accessible
- Review robots.txt compliance

**Memory issues:**

- Reduce `max_queue_size`
- Lower `max_pages_per_task`
- Decrease `concurrent_page_processing`

**Slow performance:**

- Increase `concurrent_page_processing`
- Use `HttpClient` instead of `Selenium`
- Reduce `max_depth`
- Set stricter domain restrictions

## Dependencies

- **Jackson**: JSON parsing and serialization
- **Java HTTP Client**: HTTP requests
- **Selenium WebDriver**: Browser automation (optional)
- **DocumentReader**: Document text extraction
- **CognoTik Platform**: AI/LLM integration

