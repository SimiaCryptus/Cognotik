# Extendable Strategies in the Application

## Overview

This application employs several sophisticated strategy patterns that allow for extensibility and customization. These strategies enable developers to add new capabilities without modifying core code, following the Open/Closed Principle.

## The Critical Role of Strategies in AI-Assisted Development

### Why Strategies Matter for Generative AI

In any large software project, you'll frequently encounter what's known as the Strategy Pattern—essentially, an interface with multiple implementations that can be plugged in various ways. This architectural pattern becomes particularly crucial when working with generative AI for several key reasons:

#### 1. Managing Growing Libraries at Scale

Managing a growing library of implementations—like the 26+ different reasoning patterns in this application—can become increasingly expensive and cause scaling problems in your project. Traditional manual approaches to maintaining consistency across dozens of similar implementations quickly become:

* **Time-consuming**: Manually updating each implementation with new patterns or utilities
* **Error-prone**: Inconsistencies creep in across similar files
* **Expensive**: Developer time spent on repetitive tasks rather than innovation
* **Difficult to document**: Keeping documentation synchronized across many files

This is where AI assistance becomes invaluable. The strategy pattern provides the perfect structure for AI-powered tools to:

* **Generate documentation at scale**: Establish standards from exemplar implementations, then apply them consistently across all strategy implementations
* **Suggest new implementations**: Analyze existing patterns to propose complementary strategies that fill gaps in functionality
* **Create implementations from specifications**: Use existing strategies as templates to generate new, fully-functional implementations
* **Refactor consistently**: Apply improvements across entire families of related implementations simultaneously

#### 2. Enabling AI-Driven Evolution

The strategy pattern creates a framework where AI can actively participate in codebase evolution:

```kotlin
// AI can analyze this pattern across 26+ implementations
abstract class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
  name: String,
  val executionConfigClass: Class<out T>,
  val taskSettingsClass: Class<out U>
)
```

With this structure, AI tools can:

1. **Learn from examples**: Analyze 2-3 high-quality implementations to understand patterns
2. **Generate standards**: Create documentation guidelines based on exemplar code
3. **Propose additions**: Suggest new strategy implementations that complement existing ones
4. **Implement automatically**: Generate complete, compilable implementations from specifications
5. **Maintain consistency**: Apply refactorings and improvements across all implementations

#### 3. Reducing Cognitive Load

When you have 26 different reasoning task implementations, keeping them all consistent and up-to-date manually is cognitively overwhelming. The strategy pattern combined with AI assistance allows developers to:

* Focus on high-level design decisions rather than repetitive implementation details
* Maintain consistency without manually reviewing every file
* Quickly expand capabilities by describing desired behavior rather than coding from scratch
* Ensure quality through pattern-based generation rather than error-prone manual coding

#### 4. Accelerating Innovation

The combination of strategy patterns and AI assistance creates a virtuous cycle:

1. **Start small**: Begin with a few core implementations
2. **Document patterns**: Use AI to extract and formalize patterns from examples
3. **Generate ideas**: AI suggests complementary strategies based on existing patterns
4. **Rapid implementation**: AI generates new implementations following established patterns
5. **Iterate and improve**: Use AI to refactor and enhance all implementations simultaneously

This is how the application grew from a handful of task types to 26+ implementations—not through months of manual coding, but through an iterative process of AI-assisted generation and refinement.

### Practical Benefits in This Codebase

The strategy patterns in this application are specifically designed to work well with AI assistance:

* **Task Types**: 26+ reasoning patterns that can be documented, analyzed, and extended using AI
* **API Providers**: Multiple AI service integrations that follow consistent patterns
* **Processing Strategies**: Different approaches to web crawling and analysis that can be generated from templates
* **Cognitive Modes**: Planning strategies that can be mixed and matched, with AI helping to create new combinations

Each of these strategy families benefits from AI-powered:
* Documentation generation
* Implementation suggestion
* Code generation from specifications
* Consistent refactoring across all implementations

---

## 1. API Provider Strategy

### Purpose

Manages different AI service providers (OpenAI, Anthropic, Google, etc.) with a unified interface.

### Implementation

Located in `APIProvider.kt`, this uses a dynamic enum pattern:

```kotlin
abstract class APIProvider(name: String, val base: String) : DynamicEnum<APIProvider>(name)
```

### Key Features

* **Dynamic Registration**: Providers can be registered at runtime
* **Unified Interface**: All providers implement common methods:
  - `getChatClient()` - Returns chat interface
  - `getChatModels()` - Lists available models
  - `getEmbeddingModels()` - Lists embedding models
  - `getImageModels()` - Lists image generation models
  - `authorize()` - Handles authentication

### Built-in Providers

* **OpenAI**: GPT models, DALL-E, Whisper
* **Anthropic**: Claude models
* **Google Gemini**: Gemini models with vision
* **AWS**: Bedrock models
* **Groq**: Fast inference models
* **Ollama**: Local model hosting
* **Mistral**: Mistral AI models
* **DeepSeek**: DeepSeek models

### Extension Example

```kotlin
val CustomProvider: APIProvider = object : APIProvider("Custom", "https://api.custom.com") {
  override fun getChatModels(key: String, baseUrl: String) = listOf(...)
  override fun getChatClient(...) = CustomChatClient(...)
}
```

### AI-Assisted Development

This strategy pattern is ideal for AI-assisted expansion:

1. **Documentation**: AI can analyze existing providers to generate comprehensive API documentation
2. **New Providers**: AI can generate new provider implementations by following the pattern of existing ones
3. **Consistency**: AI can ensure all providers implement the interface correctly and follow naming conventions
4. **Testing**: AI can generate test cases by analyzing how existing providers are tested

---

## 2. Cognitive Mode Strategy

### Purpose

Defines different reasoning and planning approaches for AI task execution.

### Implementation

Located in `CognitiveMode.kt`:

```kotlin
interface CognitiveModeStrategy {
  val inputCnt: Int
  fun getCognitiveMode(...): CognitiveMode
}
```

### Available Modes

#### Chat Mode

* **Purpose**: Simple conversational interaction
* **Use Case**: Direct Q&A, simple tasks
* **Characteristics**: No planning overhead, immediate responses

#### Adaptive Mode

* **Purpose**: Dynamic planning with iterative refinement
* **Use Case**: Complex tasks requiring flexibility
* **Characteristics**: Adjusts plan based on execution results

#### Waterfall Mode

* **Purpose**: Sequential execution with dependencies
* **Use Case**: Tasks with clear sequential steps
* **Characteristics**: Linear progression, each step builds on previous

#### Hierarchical Mode

* **Purpose**: Nested sub-planning for complex problems
* **Use Case**: Large projects requiring decomposition
* **Characteristics**: Tree-like task structure, recursive planning

### Extension Example

```kotlin
enum class CognitiveModeStrategies : CognitiveModeStrategy {
  Custom {
    override val inputCnt: Int get() = 3
    override fun getCognitiveMode(...): CognitiveMode {
      return CustomPlanningMode(...)
    }
  }
}
```

### AI-Assisted Development

Cognitive modes benefit significantly from AI assistance:

1. **Pattern Analysis**: AI can analyze existing modes to suggest new planning strategies
2. **Hybrid Modes**: AI can propose combinations of existing modes for specific use cases
3. **Documentation**: AI can generate detailed explanations of when to use each mode
4. **Optimization**: AI can analyze execution patterns to suggest mode improvements

---

## 3. Task Type Strategy

### Purpose

Extensible task execution system supporting various specialized operations.

### Implementation

Located in `TaskType.kt`:

```kotlin
class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
  name: String,
  val executionConfigClass: Class<out T>,
  val taskSettingsClass: Class<out U>
)
```

### Task Categories

#### File Operations

* **FileModification**: Edit files with AI assistance
* **FileSearch**: Search codebase with semantic understanding
* **Analysis**: Analyze code structure and patterns

#### Knowledge Management

* **KnowledgeIndexing**: Build vector databases
* **VectorSearch**: Semantic search across documents

#### Reasoning Tasks

* **ChainOfThought**: Step-by-step reasoning
* **MetaCognitiveReflection**: Self-analysis of reasoning
* **CausalInference**: Identify cause-effect relationships
* **AbstractionLadder**: Move between concrete and abstract
* **CounterfactualAnalysis**: "What if" scenarios
* **AnalogicalReasoning**: Find and apply analogies
* **SocraticDialogue**: Question-driven exploration

#### Writing Tasks

* **ArticleGeneration**: Create articles
* **PersuasiveEssay**: Write persuasive content
* **BusinessProposal**: Generate proposals
* **TechnicalExplanation**: Explain technical concepts
* **TutorialGeneration**: Create tutorials

#### Advanced Reasoning

* **SystemsThinking**: Analyze complex systems
* **GameTheory**: Strategic decision analysis
* **ProbabilisticReasoning**: Handle uncertainty
* **TemporalReasoning**: Time-based reasoning
* **EthicalReasoning**: Ethical analysis

### Registration Pattern

```kotlin
registerConstructor(CustomTask) { settings, task ->
  CustomTaskImplementation(settings, task)
}
```

### Extension Example

```kotlin
class CustomTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: CustomTaskConfig?
) : AbstractTask<CustomTaskConfig, CustomTaskSettings>(
  orchestrationConfig, planTask
) {
  override fun promptSegment(): String = "Custom task prompt"
  override fun run(...): TaskResult<*> {
    // Implementation
  }
}
```

### AI-Assisted Development at Scale

The Task Type strategy is the prime example of how AI assistance transforms development:

#### Documentation Generation

1. **Establish Standards**: Select 2-3 exemplar task implementations
2. **Generate Guidelines**: AI analyzes examples to create documentation standards
3. **Apply at Scale**: AI generates documentation for all 26+ task types following the standards
4. **Maintain Consistency**: Documentation stays synchronized as implementations evolve

#### Idea Generation

```kotlin
// AI can analyze existing tasks and suggest:
// - Complementary reasoning patterns
// - Missing cognitive approaches
// - Hybrid task types combining existing strategies
// - Domain-specific task variations
```

#### Implementation from Specification

```kotlin
// Process:
// 1. AI suggests "Ethical Reasoning" task
// 2. Developer selects 2-3 similar tasks as templates
// 3. AI generates complete implementation following patterns
// 4. Result: Fully functional, compilable code integrated with existing system
```

#### Mass Refactoring

```kotlin
// AI can:
// 1. Analyze utility functions across all task implementations
// 2. Identify opportunities to use shared utilities
// 3. Generate patches for each implementation
// 4. Apply changes consistently across all 26+ files
```

### Real-World Growth Pattern

This application demonstrates the power of AI-assisted strategy development:

* **Started with**: ~6 core task types
* **Grew to**: 26+ implementations
* **Timeline**: Weeks, not months
* **Method**: Iterative AI-assisted generation and refinement
* **Quality**: Consistent patterns, comprehensive documentation, minimal bugs

---

## 4. Patch Processor Strategy

### Purpose

Different algorithms for applying code changes with varying levels of fuzzy matching.

### Implementation

Located in `PatchProcessor.kt` and `PatchProcessors.kt`:

```kotlin
interface PatchProcessor {
  val label: String
  val patchFormatPrompt: String
  fun generatePatch(oldCode: String, newCode: String): String
  fun applyPatch(source: String, patch: String): String
}
```

### Available Processors

#### FullReplacement

* **Strategy**: Complete file replacement
* **Use Case**: Major rewrites
* **Reliability**: 100% (no fuzzy matching)

#### Strict

* **Strategy**: Exact matching only
* **Use Case**: Critical files requiring precision
* **Reliability**: High, but may fail on minor differences

#### Fuzzy (Default)

* **Strategy**: Balanced fuzzy matching
* **Use Case**: General purpose editing
* **Reliability**: Good balance of flexibility and accuracy

#### Lenient

* **Strategy**: Maximum fuzzy matching
* **Use Case**: Files with significant formatting differences
* **Reliability**: Most flexible, may introduce errors

#### Thermodynamic

* **Strategy**: DNA-like binding energy approach
* **Use Case**: Experimental, biologically-inspired matching
* **Reliability**: Novel approach, still being refined

### Extension Example

```kotlin
enum class PatchProcessors : PatchProcessor {
  Custom {
    override val label = "Custom"
    override val matcher = CustomPatchMatcher()
    override fun extractCodeBlocks(response: String) = matcher.extractCodeBlocks(response)
  }
}
```

### AI-Assisted Development

Patch processors are crucial for AI-generated code modifications:

1. **Mass Patching**: Apply AI-generated changes across multiple files simultaneously
2. **Fuzzy Matching**: Handle variations in AI-generated patches
3. **Quality Control**: Review patches before applying to ensure correctness
4. **Iterative Refinement**: AI can adjust patch strategies based on success rates

---

## 5. Code Runtime Strategy

### Purpose

Execute code in different languages with unified interface.

### Implementation

Located in `CodeRuntime.kt`:

```kotlin
interface CodeRuntime : EnabledStrategy {
  fun getLanguage(): String
  fun getSymbols(): Map<String, Any>
  fun run(code: String): Any?
  fun validate(code: String): Throwable?
  fun wrapCode(code: String): String
}
```

### Supported Runtimes

* **Kotlin**: JVM-based execution
* **JavaScript**: V8/Nashorn engine
* **Python**: Jython or external process
* **Shell**: System command execution

### Extension Example

```kotlin
class CustomRuntime : CodeRuntime {
  override fun getLanguage() = "custom"
  override fun getSymbols() = mapOf("api" to customApi)
  override fun run(code: String): Any? {
    // Execute in custom interpreter
  }
  override fun validate(code: String): Throwable? {
    // Syntax validation
  }
}
```

---

## 6. Document Reader Strategy

### Purpose

Read various document formats with unified interface.

### Implementation

Located in `DocumentReader.kt`:

```kotlin
interface DocumentReader : AutoCloseable {
  fun getText(): String
}

interface PaginatedDocumentReader : DocumentReader {
  fun getPageCount(): Int
  fun getText(startPage: Int, endPage: Int): String
}
```

### Supported Formats

* **PDF**: PDFReader
* **Word**: DocxReader, DocReader
* **Excel**: XlsxReader, XlsReader
* **PowerPoint**: PptxReader, PptReader
* **HTML**: HTMLReader
* **Email**: EmlReader
* **Text**: TextReader (fallback)

### Extension Pattern

```kotlin
fun File.getReader(): DocumentReader = when {
  this.name.endsWith(".custom") -> CustomReader(this)
  else -> TextReader(this)
}
```

---

## 7. Type Describer Strategy

### Purpose

Generate human-readable descriptions of types for AI prompts.

### Implementation

Located in `TypeDescriber.kt`:

```kotlin
abstract class TypeDescriber {
  abstract val markupLanguage: String
  abstract fun describe(rawType: Class<in Nothing>, ...): String
  abstract fun registerSubType(parentClass: Class<T>, subClass: Class<U>)
}
```

### Use Cases

* Generate API documentation for AI
* Create type-aware prompts
* Support polymorphic type handling

### Extension Example

```kotlin
class CustomDescriber : TypeDescriber() {
  override val markupLanguage = "custom-markup"
  override fun describe(rawType: Class<in Nothing>, ...): String {
    // Generate custom format description
  }
}
```

---

## 8. Fetch Strategy

### Purpose

Provides different methods for retrieving web content, from simple HTTP requests to full browser automation.

### Implementation

Located in `FetchMethod.kt`:

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

interface FetchMethodFactory {
  fun createStrategy(task: CrawlerAgentTask): FetchStrategy
}
```

### Available Methods

#### Selenium

* **Purpose**: Full browser automation with JavaScript execution
* **Use Case**: Dynamic websites requiring JavaScript rendering
* **Characteristics**:
  - Handles SPAs (Single Page Applications)
  - Executes JavaScript
  - Supports complex interactions
  - Higher resource usage
* **Configuration**: Requires `FetchConfig.isSeleniumEnabled = true`

#### HttpClient

* **Purpose**: Lightweight HTTP requests
* **Use Case**: Static content, APIs, simple web pages
* **Characteristics**:
  - Fast and efficient
  - Low resource usage
  - No JavaScript execution
  - Direct HTTP/HTTPS support

### Extension Example

```kotlin
enum class FetchMethod : FetchMethodFactory {
  CustomFetch {
    override fun createStrategy(task: CrawlerAgentTask) = object : FetchStrategy {
      override fun isEnabled() = true
      override fun fetch(
        url: String,
        webSearchDir: File,
        index: Int,
        pool: ExecutorService,
        orchestrationConfig: OrchestrationConfig
      ): String {
        // Custom fetching logic
        // Could use Playwright, Puppeteer, or custom HTTP client
        return fetchedContent
      }
    }
  }
}
```

### Integration Points

* Works with `CrawlerAgentTask` for web scraping
* Supports concurrent fetching via `ExecutorService`
* Configurable via `OrchestrationConfig`
* Results stored in `webSearchDir` for caching

### Best Practices

1. **Choose Appropriately**: Use HttpClient for static content, Selenium for dynamic
2. **Handle Timeouts**: Implement proper timeout handling
3. **Respect Robots.txt**: Check and honor robots.txt directives
4. **Rate Limiting**: Implement delays between requests
5. **Error Recovery**: Handle network failures gracefully

---

## 9. Seed Strategy

### Purpose

Generates initial URLs for web crawling from various search sources and methods.

### Implementation

Located in `SeedMethod.kt`:

```kotlin
data class SeedItem(
  val link: String,
  val title: String,
  val tags: List<String>? = null,
  @Description("1-100") val relevance_score: Double = 100.0,
  val additionalData: Map<String, Any> = emptyMap()
)

interface SeedStrategy : EnabledStrategy {
  fun getSeedItems(
    taskConfig: CrawlerTaskExecutionConfigData?,
    orchestrationConfig: OrchestrationConfig
  ): List<SeedItem>?
}

interface SeedMethodFactory {
  fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy
}
```

### Available Methods

#### Google-Based Methods

##### GoogleProxy

* **Purpose**: Google search via proxy service
* **Use Case**: Low rate limits, no authentication needed
* **Characteristics**: Indirect access, may have latency

##### GoogleSearch

* **Purpose**: Direct Google search integration
* **Use Case**: Standard web search
* **Characteristics**: Direct API access, subject to rate limits

#### SearchIO Integration

The application integrates with SearchIO API for multiple search engines:

##### SearchIO_Google_Search

* **Purpose**: General web search
* **Data Source**: `organic_results`
* **Use Case**: Broad web content discovery

##### SearchIO_Google_Maps

* **Purpose**: Location-based search
* **Data Source**: `local_results`
* **Use Case**: Finding businesses, locations, geographic data

##### SearchIO_Google_Scholar

* **Purpose**: Academic paper search
* **Data Source**: `organic_results`
* **Use Case**: Research, academic content, citations

##### SearchIO_Google_Patents

* **Purpose**: Patent database search
* **Data Source**: `organic_results`
* **Use Case**: Patent research, prior art searches

##### SearchIO_Google_News

* **Purpose**: News article search
* **Data Source**: `organic_results`
* **Use Case**: Current events, news aggregation

##### SearchIO_Google_Jobs

* **Purpose**: Job listing search
* **Data Source**: `jobs`
* **Use Case**: Employment opportunities, job market analysis

##### SearchIO_Amazon

* **Purpose**: Product search on Amazon
* **Data Source**: `organic_results`
* **Use Case**: E-commerce, product research

##### SearchIO_Bing

* **Purpose**: Bing search engine
* **Data Source**: `organic_results`
* **Use Case**: Alternative to Google, different result sets

##### SearchIO_DuckDuckGo

* **Purpose**: Privacy-focused search
* **Data Source**: `organic_results`
* **Use Case**: Privacy-conscious searching

##### SearchIO_EBay

* **Purpose**: eBay product search
* **Data Source**: `organic_results`
* **Use Case**: Auction items, price comparison

#### DirectUrls

* **Purpose**: Manual URL specification
* **Use Case**: Known URLs, specific targets
* **Characteristics**: No search required, direct targeting

### Extension Example

```kotlin
enum class SeedMethod : SeedMethodFactory {
  CustomSearch {
    override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy {
      return object : SeedStrategy {
        override fun isEnabled() = true

        override fun getSeedItems(
          taskConfig: CrawlerTaskExecutionConfigData?,
          orchestrationConfig: OrchestrationConfig
        ): List<SeedItem>? {
          // Custom search implementation
          val query = taskConfig?.searchQuery ?: return null
          val results = customSearchAPI.search(query)

          return results.map { result ->
            SeedItem(
              link = result.url,
              title = result.title,
              tags = result.categories,
              relevance_score = result.score,
              additionalData = mapOf(
                "source" to "custom",
                "timestamp" to System.currentTimeMillis()
              )
            )
          }
        }
      }
    }
  }
}
```

### SearchIO Integration Pattern

```kotlin
class SearchAPISearch(
  private val engine: String,
  private val resultsKey: String
) : SeedMethodFactory {
  override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy {
    return object : SeedStrategy {
      override fun getSeedItems(...): List<SeedItem>? {
        val apiKey = getSearchIOApiKey()
        val response = searchIOClient.search(
          engine = engine,
          query = taskConfig?.searchQuery,
          apiKey = apiKey
        )
        return extractResults(response, resultsKey)
      }
    }
  }
}
```

### Best Practices

1. **API Key Management**: Securely store and rotate API keys
2. **Rate Limiting**: Respect API rate limits
3. **Result Filtering**: Filter results by relevance score
4. **Error Handling**: Handle API failures gracefully
5. **Caching**: Cache results to reduce API calls
6. **Diversity**: Use multiple seed sources for comprehensive coverage

---

## 10. Page Processing Strategy

### Purpose

Defines how crawled web pages are analyzed, summarized, and integrated into the overall task execution.

### Implementation

Located in `PageProcessingStrategy.kt`:

```kotlin
interface PageProcessingStrategy {
  val description: String

  fun processPage(
    url: String,
    content: String,
    context: ProcessingContext
  ): PageProcessingResult

  fun shouldContinueCrawling(
    currentResults: List<PageProcessingResult>,
    context: ProcessingContext
  ): ContinuationDecision

  fun generateFinalOutput(
    results: List<PageProcessingResult>,
    context: ProcessingContext
  ): String

  fun validateConfig(config: Any?): String?
}
```

### Core Data Structures

#### ProcessingContext

```kotlin
data class ProcessingContext(
  val executionConfig: CrawlerTaskExecutionConfigData,
  val typeConfig: CrawlerTaskTypeConfig,
  val orchestrationConfig: OrchestrationConfig,
  val messages: List<String> = emptyList(),
  val task: SessionTask,
  val webSearchDir: File = File("websearch"),
  val processedCount: AtomicInteger = AtomicInteger(0),
  val maxPages: Int = Int.MAX_VALUE,
  val transcriptStream: FileOutputStream? = null
)
```

#### PageProcessingResult

```kotlin
data class PageProcessingResult(
  val url: String = "",
  val pageType: PageType = PageType.Error,
  val content: String = "",
  val extractedLinks: List<LinkData>? = null,
  val metadata: Map<String, Any> = emptyMap(),
  val shouldTerminate: Boolean = false,
  val terminationReason: String? = null,
  val error: Throwable? = null
)
```

#### ContinuationDecision

```kotlin
data class ContinuationDecision(
  val shouldContinue: Boolean = true,
  val reason: String = "No specific reason"
)
```

### Available Strategies

#### DefaultSummarizer

* **Purpose**: General-purpose page summarization
* **Use Case**: Content aggregation, research
* **Process**:
  1. Extract main content from HTML
  2. Generate AI-powered summary
  3. Extract relevant links
  4. Score relevance to query
* **Output**: Structured summaries with metadata

#### FactChecking

* **Purpose**: Verify claims and statements
* **Use Case**: Fact verification, claim validation
* **Process**:
  1. Extract factual claims
  2. Cross-reference with multiple sources
  3. Assign confidence scores
  4. Track source credibility
* **Output**: Fact-check report with evidence

#### JobMatching

* **Purpose**: Match job listings to criteria
* **Use Case**: Job search, candidate matching
* **Process**:
  1. Extract job details (title, requirements, salary)
  2. Score against candidate profile
  3. Identify matching skills
  4. Flag missing qualifications
* **Output**: Ranked job matches with fit analysis

#### SchemaExtraction

* **Purpose**: Extract structured data from pages
* **Use Case**: Data mining, structured information extraction
* **Process**:
  1. Identify schema.org markup
  2. Extract JSON-LD data
  3. Parse microdata
  4. Normalize to common schema
* **Output**: Structured data objects

#### DataTableAccumulation

* **Purpose**: Aggregate tabular data across pages
* **Use Case**: Price comparison, data collection
* **Process**:
  1. Identify HTML tables
  2. Extract and normalize data
  3. Merge with existing dataset
  4. Handle schema variations
* **Output**: Consolidated data table

### Extension Example

```kotlin
enum class ProcessingStrategyType {
  CustomAnalysis {
    override fun createStrategy(): PageProcessingStrategy {
      return object : PageProcessingStrategy {
        override val description = "Custom page analysis"

        override fun processPage(
          url: String,
          content: String,
          context: ProcessingContext
        ): PageProcessingResult {
          // Custom processing logic
          val analysis = analyzeContent(content)
          val links = extractRelevantLinks(content, context)

          return PageProcessingResult(
            url = url,
            pageType = determinePageType(analysis),
            content = formatAnalysis(analysis),
            extractedLinks = links,
            metadata = mapOf(
              "sentiment" to analysis.sentiment,
              "topics" to analysis.topics,
              "entities" to analysis.entities
            ),
            shouldTerminate = analysis.isComplete
          )
        }

        override fun shouldContinueCrawling(
          currentResults: List<PageProcessingResult>,
          context: ProcessingContext
        ): ContinuationDecision {
          val coverage = calculateCoverage(currentResults)
          val quality = assessQuality(currentResults)

          return ContinuationDecision(
            shouldContinue = coverage < 0.8 && quality > 0.5,
            reason = "Coverage: $coverage, Quality: $quality"
          )
        }

        override fun generateFinalOutput(
          results: List<PageProcessingResult>,
          context: ProcessingContext
        ): String {
          return buildReport(results, context)
        }

        override fun validateConfig(config: Any?): String? {
          // Validation logic
          return null // or error message
        }
      }
    }
  }
}
```

### Advanced Processing Patterns

#### Multi-Stage Processing

```kotlin
class MultiStageProcessor : PageProcessingStrategy {
  private val stages = listOf(
    ContentExtractionStage(),
    EntityRecognitionStage(),
    SentimentAnalysisStage(),
    SummarizationStage()
  )

  override fun processPage(...): PageProcessingResult {
    var result = initialResult
    for (stage in stages) {
      result = stage.process(result, context)
      if (result.shouldTerminate) break
    }
    return result
  }
}
```

#### Adaptive Processing

```kotlin
class AdaptiveProcessor : PageProcessingStrategy {
  override fun processPage(...): PageProcessingResult {
    val pageType = detectPageType(content)
    val processor = selectProcessor(pageType)
    return processor.process(url, content, context)
  }

  private fun selectProcessor(type: PageType) = when (type) {
    PageType.Article -> ArticleProcessor()
    PageType.Product -> ProductProcessor()
    PageType.Forum -> ForumProcessor()
    else -> DefaultProcessor()
  }
}
```

#### Incremental Aggregation

```kotlin
class IncrementalAggregator : PageProcessingStrategy {
  private val accumulator = DataAccumulator()

  override fun processPage(...): PageProcessingResult {
    val extracted = extractData(content)
    accumulator.add(extracted)

    return PageProcessingResult(
      content = accumulator.getSummary(),
      shouldTerminate = accumulator.isComplete(),
      metadata = mapOf("total_items" to accumulator.size())
    )
  }
}
```

### AI-Assisted Development

Page processing strategies benefit from AI assistance in several ways:

1. **Strategy Generation**: AI can analyze existing processors and suggest new ones for specific domains
2. **Pattern Recognition**: AI can identify common processing patterns and extract them into reusable components
3. **Quality Improvement**: AI can analyze processing results and suggest improvements to extraction logic
4. **Documentation**: AI can generate comprehensive documentation for each processing strategy

### Best Practices

#### 1. Efficient Processing

* Stream large content instead of loading entirely
* Use parallel processing for independent pages
* Cache intermediate results

#### 2. Robust Error Handling

```kotlin
override fun processPage(...): PageProcessingResult {
  return try {
    doProcessing(url, content, context)
  } catch (e: Exception) {
    PageProcessingResult(
      url = url,
      pageType = PageType.Error,
      error = e,
      shouldTerminate = !isRecoverable(e)
    )
  }
}
```

#### 3. Quality Control

* Validate extracted data
* Score result quality
* Filter low-quality results
* Track processing metrics

#### 4. Continuation Logic

```kotlin
override fun shouldContinueCrawling(...): ContinuationDecision {
  val reasons = mutableListOf<String>()

  // Check page limit
  if (context.processedCount.get() >= context.maxPages) {
    return ContinuationDecision(false, "Max pages reached")
  }

  // Check quality threshold
  val avgQuality = currentResults.map { it.quality }.average()
  if (avgQuality < 0.3) {
    return ContinuationDecision(false, "Quality too low")
  }

  // Check goal completion
  if (isGoalMet(currentResults, context)) {
    return ContinuationDecision(false, "Goal achieved")
  }

  return ContinuationDecision(true, "Continue crawling")
}
```

#### 5. Output Generation

```kotlin
override fun generateFinalOutput(...): String {
  return buildString {
    appendLine("# Processing Report")
    appendLine()
    appendLine("## Summary")
    appendLine("Processed ${results.size} pages")
    appendLine()
    appendLine("## Results")
    results.forEach { result ->
      appendLine("### ${result.url}")
      appendLine(result.content)
      appendLine()
    }
    appendLine("## Metadata")
    appendLine(formatMetadata(results))
  }
}
```

### Integration with Other Strategies

#### With Fetch Strategy

```kotlin
val content = fetchStrategy.fetch(url, webSearchDir, index, pool, config)
val result = processingStrategy.processPage(url, content, context)
```

#### With Seed Strategy

```kotlin
val seeds = seedStrategy.getSeedItems(taskConfig, orchestrationConfig)
val results = seeds.map { seed ->
  val content = fetch(seed.link)
  processingStrategy.processPage(seed.link, content, context)
}
```

#### With Task Types

```kotlin
class CrawlerTask : AbstractTask<...> {
  override fun run(...): TaskResult<*> {
    val strategy = typeConfig.processingStrategy.createStrategy()
    val results = crawlAndProcess(strategy)
    val output = strategy.generateFinalOutput(results, context)
    return TaskResult(output)
  }
}
```

---

## Configuration Examples

### Complete Crawler Configuration

```kotlin
val crawlerConfig = CrawlerTaskExecutionConfigData(
  searchQuery = "AI research papers",
  seedMethod = SeedMethod.SearchIO_Google_Scholar,
  fetchMethod = FetchMethod.HttpClient,
  processingStrategy = ProcessingStrategyType.SchemaExtraction,
  maxPages = 50,
  relevanceThreshold = 0.7
)
```

### Multi-Source Seed Configuration

```kotlin
val multiSourceSeeds = listOf(
  SeedMethod.SearchIO_Google_Search,
  SeedMethod.SearchIO_Bing,
  SeedMethod.SearchIO_DuckDuckGo
).flatMap { method ->
  method.createStrategy(task, user)
    .getSeedItems(taskConfig, orchestrationConfig)
    ?: emptyList()
}.distinctBy { it.link }
```

### Adaptive Processing Configuration

```kotlin
val adaptiveProcessor = object : PageProcessingStrategy {
  override fun processPage(...): PageProcessingResult {
    val strategy = when {
      url.contains("scholar.google") -> SchemaExtractionStrategy()
      url.contains("news") -> DefaultSummarizerStrategy()
      url.contains("jobs") -> JobMatchingStrategy()
      else -> DefaultSummarizerStrategy()
    }
    return strategy.processPage(url, content, context)
  }
}
```

---

## Design Principles

### 1. Open/Closed Principle

All strategies are open for extension but closed for modification. New implementations can be added without changing existing code.

### 2. Strategy Pattern

Each strategy family defines an interface and multiple implementations, allowing runtime selection.

### 3. Dynamic Registration

Many strategies use dynamic registration, allowing plugins to add new implementations at runtime.

### 4. Type Safety

Generic types ensure compile-time safety while maintaining flexibility.

### 5. Separation of Concerns

Each strategy focuses on a single responsibility, making the system modular and maintainable.

### 6. AI-First Design

Strategies are designed to work seamlessly with AI-assisted development tools, enabling:
* Automated documentation generation
* Pattern-based code generation
* Consistent mass refactoring
* Intelligent suggestion systems

---

## Best Practices for Extension

### 1. Follow Existing Patterns

Study existing implementations before creating new ones. Use AI tools to analyze patterns across multiple implementations.

### 2. Register Properly

Use the registration mechanisms provided (e.g., `register()` methods).

### 3. Handle Errors Gracefully

Implement proper error handling and validation.

### 4. Document Thoroughly

Provide clear documentation for new strategies. Consider using AI to generate initial documentation based on exemplar implementations.

### 5. Test Comprehensively

Include unit tests for new implementations.

### 6. Consider Performance

Be mindful of performance implications, especially for frequently-used strategies.

### 7. Leverage AI Assistance

When extending strategies:
* Use AI to analyze existing implementations and extract patterns
* Generate documentation standards from exemplar code
* Create new implementations from specifications using AI
* Apply refactorings consistently across all implementations
* Validate that new implementations follow established patterns

### 8. Maintain Consistency

When you have many implementations (like 26+ task types):
* Use AI to ensure consistent naming conventions
* Apply utility functions uniformly across all implementations
* Keep documentation synchronized
* Maintain similar code structure and patterns

---

## Conclusion

The application's extensible strategy system provides a robust framework for adding new capabilities while maintaining code quality and consistency. 
The combination of well-designed strategy patterns and AI-assisted development tools creates a powerful environment where:

* **Documentation scales**: Generate and maintain documentation for dozens of implementations automatically
* **Innovation accelerates**: Quickly prototype and implement new strategies based on existing patterns
* **Quality remains high**: AI ensures consistency and catches deviations from established patterns
* **Maintenance simplifies**: Apply improvements across entire strategy families simultaneously

By understanding and extending these strategies—and leveraging AI assistance throughout the process—developers can create powerful, customized web crawling and analysis workflows tailored to specific use cases, without the traditional overhead of managing large collections of similar implementations.

The growth from a handful of implementations to 26+ task types demonstrates the practical power of this approach: what would traditionally take months of careful manual coding can be accomplished in weeks through intelligent use of AI-assisted development within a well-designed strategy framework.

