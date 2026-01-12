# Online & Search

## CrawlerAgent

Search Google, fetch top results, and analyze content

         Searches Google for specified queries and analyzes the top results.
         <ul>
           <li>Performs Google searches</li>
           <li>Fetches top search results</li>
           <li>Analyzes content for specific goals</li>
           <li>Generates detailed analysis reports</li>
</ul>

#### Planner Prompt Segment

```text
CrawlerAgent - Search Google, fetch top results, and analyze content
** Specify the search query
** Or provide direct URLs to analyze
** Specify a detailed query/analysis prompt to guide content processing
** Choose a processing strategy: DefaultSummarizer, FactChecking, or JobMatching
** Results will be saved to .websearch directory for future reference
** Links found in analysis can be automatically followed for deeper research

```

#### Default Execution Configuration

```json
{
  "task_type" : "CrawlerAgent",
  "search_query" : null,
  "direct_urls" : null,
  "content_queries" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "CrawlerAgent"
}
```

#### Default Type Configuration

```json
{
  "seed_method" : "GoogleProxy",
  "fetch_method" : "HttpClient",
  "processing_strategy" : "DefaultSummarizer",
  "allowed_domains" : null,
  "respect_robots_txt" : true,
  "max_pages_per_task" : null,
  "max_depth" : null,
  "max_queue_size" : null,
  "concurrent_page_processing" : null,
  "max_final_output_size" : null,
  "min_content_length" : null,
  "follow_links" : null,
  "allow_revisit_pages" : null,
  "create_final_summary" : null,
  "generate_transcript" : true,
  "task_type" : "CrawlerAgent",
  "model" : null,
  "name" : "CrawlerAgent"
}
```

---

## GitHubSearch

Search GitHub repositories, code, issues and users

Performs comprehensive searches across GitHub's content.
<ul>
  <li>Searches repositories, code, and issues</li>
  <li>Supports advanced search queries</li>
  <li>Filters results by various criteria</li>
  <li>Formats results with relevant details</li>
  <li>Handles API rate limiting</li>
</ul>

#### Planner Prompt Segment

```text
GitHubSearch - Search GitHub for code, commits, issues, repositories, topics, or users
   * Specify the search query
   * Specify the type of search (code, commits, issues, repositories, topics, users)
   * Specify the number of results to return (max 100)
   * Optionally specify sort order (e.g. stars, forks, updated)
   * Optionally specify sort direction (asc or desc)
```

#### Default Execution Configuration

```json
{
  "task_type" : "GitHubSearch",
  "search_query" : "",
  "search_type" : "repositories",
  "per_page" : 30,
  "sort" : null,
  "order" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "GitHubSearch"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GitHubSearch",
  "name" : "GitHubSearch",
  "model" : null
}
```

---

## MCPTool

Execute tools from Model Context Protocol servers

Executes tools from MCP (Model Context Protocol) servers.
<ul>
  <li>Connect to MCP servers via various transports</li>
  <li>Execute tools with custom arguments</li>
  <li>Configurable timeouts and retry logic</li>
  <li>Support for multiple MCP server integrations</li>
  <li>Structured result handling</li>
  <li>Automatic tool discovery and validation</li>
  <li>Exponential backoff retry strategy</li>
</ul>

#### Planner Prompt Segment

```text
MCPTool - Execute tools from Model Context Protocol (MCP) servers
 ** Specify the MCP server name and tool to execute
 ** Provide tool arguments as a JSON object
 ** Configure timeout and retry behavior
 ** Supports integration with external MCP-compatible services
```

#### Default Execution Configuration

```json
{
  "task_type" : "MCPTool",
  "server_name" : null,
  "tool_name" : null,
  "tool_arguments" : null,
  "timeout_seconds" : 30,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "MCPTool"
}
```

#### Default Type Configuration

```json
{
  "default_server" : null,
  "default_timeout" : 30,
  "auto_retry" : false,
  "generate_transcript" : true,
  "max_retries" : 3,
  "retry_delay_ms" : 1000,
  "exponential_backoff" : true,
  "task_type" : "MCPTool",
  "name" : "MCPTool",
  "model" : null
}
```

---

