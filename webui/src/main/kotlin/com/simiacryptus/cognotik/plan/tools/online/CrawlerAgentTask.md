# CrawlerAgentTask

## Overview

The `CrawlerAgentTask` is a sophisticated web crawling and content analysis task that can search the web, fetch content from URLs, and analyze the retrieved information based on specified queries. It supports multiple seeding methods (Google Search or direct URLs) and various content fetching strategies (HTTP client or Selenium).

## Key Features

- **Multiple Seed Methods**: Start crawling from Google search results or direct URLs
- **Concurrent Processing**: Process multiple pages simultaneously for efficiency
- **Smart Link Following**: Automatically discover and follow relevant links
- **Content Analysis**: AI-powered analysis of fetched content based on specified queries
- **Caching**: Avoid re-fetching previously processed URLs
- **Error Handling**: Robust error handling with configurable error thresholds
- **Result Summarization**: Automatic summarization of large result sets

## Configuration

### Task Settings (`CrawlerTaskSettings`)

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `seed_method` | `SeedMethod` | Method to seed the crawler (GoogleSearch, DirectUrls) | `GoogleSearch` |
| `fetch_method` | `FetchMethod` | Method to fetch content (HttpClient, Selenium) | `HttpClient` |
| `task_type` | `String` | Task type identifier | `"CrawlerAgentTask"` |
| `enabled` | `Boolean` | Whether the task is enabled | `false` |
| `model` | `ApiChatModel` | AI model to use for analysis | `null` |

### Task Configuration (`CrawlerTaskConfigData`)

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `search_query` | `String?` | Google search query to seed the crawler | `null` |
| `direct_urls` | `String?` | Comma-separated list of URLs to analyze | `null` |
| `content_queries` | `Any?` | Questions to consider when analyzing content | `null` |
| `max_pages_per_task` | `Int?` | Maximum pages to process in one task | `30` |
| `task_description` | `String?` | Description of the task | `null` |
| `task_dependencies` | `List<String>?` | List of dependent task IDs | `null` |

### Constructor Parameters

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `follow_links` | `Boolean` | Whether to follow links found in content | `true` |
| `max_pages_per_task` | `Int` | Maximum pages to process | `50` |
| `max_final_output_size` | `Int` | Maximum size of final output in characters | `10000` |
| `concurrent_page_processing` | `Int` | Number of pages to process concurrently | `3` |
| `allow_revisit_pages` | `Boolean` | Whether to allow revisiting already processed pages | `false` |
| `create_final_summary` | `Boolean?` | Whether to create a summary of results | `true` |
| `min_content_length` | `Int` | Minimum content length to process (chars) | `100` |

## Data Structures

### LinkData
Represents a link to be processed:
- `link`: URL of the page
- `title`: Title or description of the link
- `tags`: Associated tags
- `relevance_score`: Relevance score (1-100)
- `started`: Processing started flag
- `completed`: Processing completed flag
- `depth`: Crawl depth from seed
- `error`: Error message if processing failed
- `processingTimeMs`: Time taken to process

### ParsedPage
Result of analyzing a page:
- `page_type`: Type of page (OK, Error, Irrelevant)
- `page_information`: Extracted information
- `tags`: Associated tags
- `link_data`: Links found on the page

## Processing Flow

1. **Initialization**
   - Create output directory (`.websearch`)
   - Initialize seed items using configured seed method
   - Set up page queue with seed items

2. **Crawling Loop**
   - Process pages concurrently up to `concurrent_page_processing` limit
   - For each page:
     - Fetch content using configured fetch method
     - Check content length against `min_content_length`
     - Analyze content based on `content_queries`
     - Extract and queue new links if `follow_links` is enabled
     - Save analysis results

3. **Result Compilation**
   - Combine all analysis results
   - Create final summary if results exceed `max_final_output_size`
   - Return formatted output

## Output

The task produces:
- **Markdown-formatted analysis** of each processed page
- **Saved analysis files** in `.websearch` directory with metadata
- **Final summary** combining insights from all pages
- **Tabbed display** in UI showing individual page results

## Error Handling

- **Error threshold**: Stops processing if errors exceed 50% of max pages
- **Invalid URLs**: Filters out malformed or blacklisted URLs
- **Content validation**: Skips pages with insufficient content
- **Graceful degradation**: Continues processing other pages on individual failures

## Blacklisted Domains

The following domains are automatically excluded:
- Social media: facebook.com, twitter.com, instagram.com, linkedin.com, tiktok.com, pinterest.com, reddit.com
- Video platforms: youtube.com
- E-commerce: amazon.com, ebay.com, aliexpress.com

## Usage Example

```kotlin
val task = CrawlerAgentTask(
    orchestrationConfig = config,
    planTask = CrawlerTaskConfigData(
        search_query = "artificial intelligence trends 2024",
        content_queries = "What are the key AI developments and predictions?",
        max_pages_per_task = 20
    ),
    follow_links = true,
    concurrent_page_processing = 5,
    create_final_summary = true
)
```

## Performance Considerations

- **Concurrent processing**: Adjust `concurrent_page_processing` based on available resources
- **Caching**: URL content is cached to avoid redundant fetches
- **Content chunking**: Large content is split into manageable chunks for analysis
- **Rate limiting**: Consider implementing delays between requests to avoid overwhelming servers

## File Storage

Analysis results are saved in the `.websearch` directory with the following structure:
- Main directory: Successful analyses
- `error/`: Pages that encountered errors
- `irrelevant/`: Pages marked as irrelevant
- File naming: `{url_safe}_{index}_{timestamp}.md`

## Limitations

- Maximum iterations: 1000 to prevent infinite loops
- URL safety: Special characters in URLs are sanitized for file names
- Content size: Very large pages are chunked for processing
- Link explosion: Maximum 10 links extracted per page to prevent exponential growth