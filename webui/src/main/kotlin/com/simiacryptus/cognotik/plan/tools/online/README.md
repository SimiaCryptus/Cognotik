# Online & Search Tools

This package contains a suite of tools designed for interacting with online resources, performing web searches, crawling content, and integrating with external services via the Model Context Protocol (MCP).

## Tools

### Crawler Agent (`CrawlerAgent`)
A sophisticated web crawler and analyzer that can perform Google searches or process direct URLs to extract and summarize information based on specific queries.

**Key Features:**
* **Search & Seed:** Supports Google search (via proxy) and direct URL seeding.
* **Processing Strategies:** Includes specialized strategies like `DefaultSummarizer`, `FactChecking`, and `JobMatching`.
* **Link Following:** Automatically discovers and follows links to perform deeper research.
* **Compliance:** Respects `robots.txt` rules and supports configurable crawl delays.
* **Robustness:** Features concurrent page processing, memory-safe queue management, and domain blacklisting.
* **Reporting:** Generates detailed session transcripts and comprehensive final summaries.

**Configuration:**
* **Execution:** Requires a `search_query` or a list of `direct_urls`, along with `content_queries` to guide the analysis.
* **Type Settings:** Configurable fetch methods (HttpClient, Selenium), depth limits, max pages, and allowed domain whitelists.

### GitHub Search (`GitHubSearch`)
Provides comprehensive search capabilities across GitHub's vast repository of code, issues, and users.

**Key Features:**
* **Multi-type Search:** Search across repositories, code, commits, issues, topics, and users.
* **Advanced Filtering:** Supports sorting by stars, forks, or update status with configurable result counts.
* **Rich Formatting:** Results are rendered with relevant metadata (e.g., star counts, commit dates, avatars).
* **Integration:** Requires a GitHub API token for authenticated requests.

**Configuration:**
* **Execution:** Requires a `search_query` and `search_type`. Supports `per_page` (up to 100), `sort`, and `order` parameters.

### MCP Tool (`MCPTool`)
An integration tool that allows the agent to execute functionality provided by Model Context Protocol (MCP) servers.

**Key Features:**
* **Extensibility:** Connects to any MCP-compatible server to access external tools and data.
* **Reliability:** Implements automatic retries with exponential backoff for transient network or connection errors.
* **Discovery:** Automatically discovers available tools on the connected server and validates arguments.
* **Control:** Configurable execution timeouts and detailed transcript logging.

**Configuration:**
* **Execution:** Requires `server_name`, `tool_name`, and a JSON object of `tool_arguments`.
* **Type Settings:** Defines default servers, retry logic parameters (`max_retries`, `retry_delay_ms`), and timeout defaults.

## Implementation Details

These tools are built on the `AbstractTask` framework, ensuring consistent integration with the Cognotik orchestration engine. They leverage:
* **Tabbed Displays:** For organized UI feedback during long-running operations (like crawling).
* **Transcript Streams:** For persistent logging of external interactions.
* **Validated Objects:** To ensure configuration integrity before execution.