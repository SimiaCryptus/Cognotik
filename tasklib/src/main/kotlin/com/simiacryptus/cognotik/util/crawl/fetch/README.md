# Online Fetch Package

This package provides flexible strategies for fetching web content, supporting both HTTP-based and browser-based
approaches to web scraping and content retrieval.

## Overview

The fetch package implements a strategy pattern for web content retrieval, allowing seamless switching between different
fetching methods based on requirements and availability. It handles various content types including HTML, documents (
PDF, DOCX, etc.), and plain text.

## Components

### FetchStrategy Interface

The core interface that defines the contract for fetching web content:

```kotlin
interface FetchStrategy : EnabledStrategy {
  fun fetch(
    url: String,
    webSearchDir: File,
    index: Int,
    pool: ExecutorService,
    orchestrationConfig: OrchestrationConfig
  ): String
}
```

### Fetch Methods

#### 1. HttpClient (Default)

A lightweight, efficient HTTP-based fetching strategy using Java's built-in HttpClient.

**Features:**

- Fast and resource-efficient
- Handles multiple content types:
    - HTML pages (with simplification)
    - PDF documents
    - Microsoft Office documents (DOC, DOCX, XLS, XLSX, PPT, PPTX)
    - OpenDocument formats (ODT)
    - Plain text files
- SSL/TLS support with flexible certificate validation
- Automatic text extraction from documents
- Content size limits (5MB for HTML, 10MB for documents)
- Proper error handling and fallbacks

**Usage:**

```kotlin
val strategy = HttpClientFetch().createStrategy(task)
val content = strategy.fetch(url, webSearchDir, index, pool, config)
```

#### 2. Selenium (Optional)

A browser-based fetching strategy using Selenium WebDriver for JavaScript-heavy sites.

**Features:**

- Full browser rendering
- JavaScript execution support
- Automatic fallback to HttpClient on failure
- Can be enabled/disabled via `FetchConfig.isSeleniumEnabled`

**Usage:**

```kotlin
FetchConfig.isSeleniumEnabled = true
val strategy = Selenium().createStrategy(task)
val content = strategy.fetch(url, webSearchDir, index, pool, config)
```

### FetchMethod Enum

Factory enum for creating fetch strategies:

```kotlin
enum class FetchMethod : FetchMethodFactory {
  Selenium,
  HttpClient
}
```

## Content Processing

### HTML Processing

1. **Raw Content Storage**: Original HTML is saved to `raw_pages/`
2. **Simplification**: HTML is cleaned and simplified using `HtmlSimplifier`:

- Removes CSS, scripts, and interactive elements
- Preserves semantic structure
- Removes event handlers and media elements

3. **Reduced Content Storage**: Simplified HTML is saved to `reduced_pages/`

### Document Processing

1. **Binary Download**: Documents are downloaded as byte arrays
2. **Storage**: Original documents saved to `documents/` directory
3. **Text Extraction**: Text content extracted using `DocumentReader`
4. **Extracted Text Storage**: Plain text saved to `extracted_text/`

Supported formats:

- PDF (`.pdf`)
- Microsoft Word (`.doc`, `.docx`)
- Microsoft Excel (`.xls`, `.xlsx`)
- Microsoft PowerPoint (`.ppt`, `.pptx`)
- OpenDocument Text (`.odt`)
- Rich Text Format (`.rtf`)

## Configuration

### FetchConfig

Global configuration for fetch behavior:

```kotlin
object FetchConfig {
  var isSeleniumEnabled: Boolean = false
}
```

### Content Limits

- **HTML Content**: 5MB maximum (truncated if larger)
- **Document Files**: 10MB maximum (skipped if larger)
- **HTTP Timeout**: 60 seconds
- **Connection Timeout**: 30 seconds

## Error Handling

The package implements robust error handling:

1. **HTTP Errors**: Non-2xx status codes throw descriptive exceptions
2. **Selenium Fallback**: Automatic fallback to HttpClient if Selenium fails
3. **Document Extraction**: Graceful handling of extraction failures
4. **Content Type Validation**: Skips unsupported content types with warnings
5. **Size Limits**: Enforces reasonable content size limits

## Directory Structure

```
webSearchDir/
├── raw_pages/          # Original HTML content
├── reduced_pages/      # Simplified HTML content
├── documents/          # Original document files
├── text_pages/         # Plain text content
└── extracted_text/     # Extracted text from documents
```

## Logging

Comprehensive logging at multiple levels:

- **INFO**: Major operations (fetching, processing)
- **DEBUG**: Detailed operation steps
- **WARN**: Fallbacks and skipped content
- **ERROR**: Failures and exceptions

## Best Practices

1. **Use HttpClient by default** - It's faster and more reliable for most content
2. **Enable Selenium only when needed** - For JavaScript-heavy sites
3. **Monitor content sizes** - Large documents may be skipped
4. **Handle exceptions** - Network issues and timeouts can occur
5. **Check content types** - Not all content types are supported

## Example Usage

```kotlin
// Create a crawler task
val task = CrawlerAgentTask(...)

// Use HttpClient (recommended)
val httpStrategy = HttpClientFetch().createStrategy(task)
val content = httpStrategy.fetch(
  url = "https://example.com",
  webSearchDir = File("./output"),
  index = 0,
  pool = executorService,
  orchestrationConfig = config
)

// Or use Selenium for JavaScript sites
FetchConfig.isSeleniumEnabled = true
val seleniumStrategy = Selenium().createStrategy(task)
val jsContent = seleniumStrategy.fetch(
  url = "https://js-heavy-site.com",
  webSearchDir = File("./output"),
  index = 1,
  pool = executorService,
  orchestrationConfig = config
)
```

## Dependencies

- Java 11+ HttpClient
- Selenium WebDriver (optional)
- DocumentReader for text extraction
- HtmlSimplifier for HTML processing

