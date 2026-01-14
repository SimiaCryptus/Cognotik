# Web Crawling and Processing Package

## Overview

This package provides a flexible and powerful framework for web crawling, content extraction, and intelligent data
processing. It combines AI-powered analysis with configurable processing strategies to extract structured information
from websites.

## Core Components

### Page Processing Strategies

The package implements a strategy pattern for processing web pages, allowing different extraction and analysis
approaches:

#### 1. **DefaultSummarizerStrategy**

Basic content analysis and summarization strategy.

**Use Cases:**

- General web content analysis
- Research and information gathering
- Content summarization

**Features:**

- AI-powered content transformation
- Automatic link extraction
- Markdown-formatted output
- Chunked processing for large pages

**Configuration:**

```json
{
  "task_description": "Analyze content and provide insights",
  "content_queries": "Optional specific queries"
}
```

#### 2. **FactCheckingStrategy**

Verifies claims against web content with evidence tracking.

**Use Cases:**

- Claim verification
- Research validation
- Source credibility assessment

**Features:**

- Multi-source evidence collection
- Confidence scoring
- Supporting/contradicting evidence tracking
- Automatic termination when sufficient evidence is gathered

**Configuration:**

```json
{
  "claims_to_verify": ["Claim 1", "Claim 2"],
  "confidence_threshold": 0.8,
  "required_sources": 3,
  "contradiction_threshold": 2
}
```

**Example:**

```kotlin
val config = FactCheckingConfig(
  claims_to_verify = listOf(
    "Company X has 10,000 employees",
    "Product Y was released in 2023"
  ),
  confidence_threshold = 0.8,
  required_sources = 3
)
```

#### 3. **JobMatchingStrategy**

Analyzes job postings and matches them against candidate profiles.

**Use Cases:**

- Job search automation
- Resume matching
- Application material generation

**Features:**

- Automatic job posting detection
- Multi-dimensional matching (skills, location, salary, work arrangement)
- Cover letter generation
- Detailed application reports
- Location and work arrangement compatibility scoring
- Salary range analysis

**Configuration:**

```json
{
  "user_experience": "Your resume/experience summary",
  "target_roles": ["Software Engineer", "Senior Developer"],
  "required_skills": ["Python", "AWS", "Docker"],
  "preferred_locations": ["San Francisco", "Remote"],
  "acceptable_locations": ["California", "New York"],
  "excluded_locations": ["International"],
  "min_match_score": 0.6,
  "target_matches": 10,
  "work_arrangement_preference": "remote",
  "max_days_in_office": 2,
  "min_salary": 120000,
  "target_salary": 150000,
  "max_salary": 180000,
  "salary_currency": "USD",
  "willing_to_relocate": false
}
```

**Output:**

- Individual job reports in `job_matches/` directory
- Cover letters tailored to each position
- Compatibility scores (skills, location, salary, work arrangement)
- Application strategy notes

#### 4. **SchemaExtractionStrategy**

Extracts structured data according to user-defined schemas.

**Use Cases:**

- Data mining
- Structured data extraction
- API-like data collection from websites

**Features:**

- Custom JSON schema definition
- Automatic data validation
- Deduplication
- Aggregated JSON output
- Confidence-based filtering

**Configuration:**

```json
{
  "schema_definition": "{\"name\": \"string\", \"price\": \"number\"}",
  "extraction_instructions": "Extract product information",
  "aggregate_results": true,
  "min_confidence": 0.7,
  "max_items_per_page": 50,
  "validate_schema": true,
  "deduplicate": true,
  "deduplication_keys": "name,id"
}
```

**Example Schema:**

```json
{
  "product_name": "string",
  "price": "number",
  "rating": "number",
  "availability": "boolean",
  "specifications": {
    "weight": "string",
    "dimensions": "string"
  }
}
```

**Output:**

- `aggregated_data.json` - All extracted data
- `aggregated_data_pretty.json` - Pretty-printed version
- `extraction_metadata.json` - Extraction statistics

#### 5. **DataTableAccumulationStrategy**

Builds comprehensive datasets from web pages with configurable columns.

**Use Cases:**

- Competitive analysis
- Market research
- Price comparison
- Feature matrices

**Features:**

- Configurable column definitions
- Type validation
- Data normalization
- Multiple export formats (CSV, JSON, Markdown)
- Automatic HTML table detection
- Row deduplication

**Configuration:**

```json
{
  "column_names": "Product,Price,Rating,Availability",
  "column_descriptions": {
    "Product": "Product name or title",
    "Price": "Price in USD",
    "Rating": "Customer rating out of 5",
    "Availability": "In stock status"
  },
  "column_types": {
    "Product": "string",
    "Price": "number",
    "Rating": "number",
    "Availability": "boolean"
  },
  "extraction_instructions": "Extract product comparison data",
  "auto_detect_tables": true,
  "min_rows": 1,
  "max_rows_per_page": 100,
  "deduplicate": true,
  "key_columns": "Product",
  "validate_types": true,
  "normalize_data": true,
  "export_format": "csv",
  "include_source_urls": true
}
```

**Output:**

- `data_table.csv` / `.json` / `.md` - Exported table
- Column statistics
- Data quality metrics

## Architecture

### ProcessingContext

Shared context for all strategies:

```kotlin
data class ProcessingContext(
  val executionConfig: CrawlerTaskExecutionConfigData,
  val typeConfig: CrawlerTaskTypeConfig,
  val orchestrationConfig: OrchestrationConfig,
  val messages: List<String>,
  val task: SessionTask,
  val webSearchDir: File,
  val processedCount: AtomicInteger,
  val maxPages: Int,
  val transcriptStream: FileOutputStream?
)
```

### PageProcessingResult

Standard result format:

```kotlin
data class PageProcessingResult(
  val url: String,
  val pageType: PageType,
  val content: String,
  val extractedLinks: List<LinkData>?,
  val metadata: Map<String, Any>,
  val shouldTerminate: Boolean,
  val terminationReason: String?,
  val error: Throwable?
)
```

## Strategy Selection

Strategies are selected via the `ProcessingStrategyType` enum:

```kotlin
enum class ProcessingStrategyType {
  DefaultSummarizer,
  FactChecking,
  JobMatching,
  SchemaExtraction,
  DataTableAccumulation
}
```

## Common Features

### Early Termination

All strategies support early termination based on:

- Target achievement (e.g., finding N job matches)
- Confidence thresholds
- Evidence sufficiency
- Custom strategy-specific conditions

### Link Extraction

Intelligent link prioritization based on:

- Relevance scoring
- Content analysis
- Strategy-specific criteria

### Progress Tracking

- Real-time transcript updates
- Processing statistics
- Error handling and reporting

### Output Formats

- Markdown reports
- JSON data exports
- CSV tables
- Structured metadata

## Usage Examples

### Basic Crawling with Summarization

```kotlin
val strategy = DefaultSummarizerStrategy()
val context = ProcessingContext(
  executionConfig = config,
  typeConfig = typeConfig,
  orchestrationConfig = orchestrationConfig,
  task = task,
  webSearchDir = File("output")
)

val result = strategy.processPage(url, content, context)
```

### Job Search Automation

```kotlin
val strategy = JobMatchingStrategy()
val config = JobMatchingConfig(
  user_experience = resumeText,
  target_roles = listOf("Senior Engineer", "Tech Lead"),
  required_skills = listOf("Kotlin", "AWS", "Kubernetes"),
  preferred_locations = listOf("Remote", "San Francisco"),
  min_match_score = 0.7,
  target_matches = 5,
  work_arrangement_preference = "remote",
  min_salary = 150000
)

// Strategy will automatically:
// 1. Detect job postings
// 2. Analyze compatibility
// 3. Generate cover letters
// 4. Save detailed reports
// 5. Terminate after finding 5 good matches
```

### Data Extraction

```kotlin
val strategy = SchemaExtractionStrategy()
val config = SchemaExtractionConfig(
  schema_definition = """
    {
      "title": "string",
      "price": "number",
      "features": ["string"]
    }
  """,
  aggregate_results = true,
  deduplicate = true
)

// Extracts structured data matching schema
// Outputs aggregated JSON file
```

### Fact Verification

```kotlin
val strategy = FactCheckingStrategy()
val config = FactCheckingConfig(
  claims_to_verify = listOf(
    "The company was founded in 2010",
    "The product has 1M+ users"
  ),
  required_sources = 3,
  confidence_threshold = 0.8
)

// Collects evidence from multiple sources
// Terminates when sufficient evidence found
```

## Error Handling

All strategies implement robust error handling:

```kotlin
try {
  val result = strategy.processPage(url, content, context)
  if (result.error != null) {
    // Handle processing error
  }
} catch (e: Exception) {
  // Handle critical failure
}
```

## Configuration Validation

Each strategy validates its configuration:

```kotlin
val error = strategy.validateConfig(config)
if (error != null) {
  throw IllegalArgumentException(error)
}
```

## Best Practices

1. **Choose the Right Strategy**

- Use `DefaultSummarizer` for general content analysis
- Use `JobMatching` for recruitment automation
- Use `SchemaExtraction` for structured data mining
- Use `FactChecking` for claim verification
- Use `DataTableAccumulation` for comparative datasets

2. **Configure Appropriately**

- Set realistic confidence thresholds
- Define clear extraction criteria
- Use deduplication for large datasets
- Set page limits to control costs

3. **Monitor Progress**

- Check transcript streams for real-time updates
- Review metadata for extraction statistics
- Handle early termination gracefully

4. **Handle Errors**

- Implement retry logic for transient failures
- Log errors for debugging
- Validate configurations before execution

5. **Optimize Performance**

- Use appropriate page limits
- Enable deduplication when needed
- Set confidence thresholds to filter noise
- Leverage early termination

## Output Structure

```
output/
├── transcript.md              # Real-time processing log
├── final_report.md           # Final summary
├── aggregated_data.json      # Extracted data (SchemaExtraction)
├── data_table.csv            # Tabular data (DataTableAccumulation)
└── job_matches/              # Job reports (JobMatching)
    ├── Company_Position_timestamp.md
    └── ...
```

## Dependencies

- AI/LLM integration via `ChatInterface`
- JSON parsing via Jackson
- Markdown generation
- Concurrent processing support

## Thread Safety

All strategies use thread-safe data structures:

- `ConcurrentHashMap` for shared state
- `AtomicInteger` for counters
- Synchronized file I/O
