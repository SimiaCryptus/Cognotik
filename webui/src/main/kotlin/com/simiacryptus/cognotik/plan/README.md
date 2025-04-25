# Cognotik Plan Package Documentation

## Overview

The Cognotik Plan package provides a comprehensive framework for AI-assisted task planning and execution. It enables
breaking down complex tasks into manageable subtasks, executing them in the correct order with proper dependency
management, and providing rich interactive feedback to users.

The package supports various task types, from file modifications and code generation to web searches and knowledge
indexing, all orchestrated through different cognitive planning modes.

## Core Components

### Task Types

The package includes numerous specialized task types for different operations:

- **FileModificationTask**: Create or modify source files with AI assistance
- **InquiryTask**: Analyze code and provide detailed explanations
- **FileSearchTask**: Search project files using patterns with contextual results
- **WebSearchTask**: Search the web, fetch results, and analyze content
- **RunShellCommandTask**: Execute shell commands safely
- **CommandAutoFixTask**: Run commands and automatically fix issues
- **PlanningTask**: Break down complex tasks with dependency management
- **ForeachTask**: Execute subtasks for each item in a list
- **SoftwareGraphGenerationTask**: Generate graph representations of codebases
- **KnowledgeIndexingTask**: Index content for semantic search
- **SeleniumSessionTask**: Automate browser interactions
- **CommandSessionTask**: Manage interactive command-line sessions
- **GitHubSearchTask**: Search GitHub repositories, code, and issues
- **DataTableCompilationTask**: Compile structured data from multiple files

### Cognitive Modes

The package offers different planning strategies through cognitive modes:

- **AutoPlanMode**: Implements iterative thinking with dynamic task selection
- **PlanAheadMode**: Traditional plan-ahead strategy with upfront planning
- **SingleTaskMode**: Executes a single task based on user input
- **GraphOrderedPlanMode**: Uses software graph structure to order task execution

### Key Classes

- **PlanCoordinator**: Orchestrates task execution with dependency management
- **AbstractTask**: Base class for all task implementations
- **PlanSettings**: Configuration for the planning system
- **TaskType**: Enum-like class defining available task types
- **PlanUtil**: Utility functions for plan visualization and management

## Usage Examples

### Basic Plan Execution

```kotlin
// Create plan settings
val planSettings = PlanSettings(
    defaultModel = ChatModel.GPT4,
    parsingModel = ChatModel.GPT4,
    autoFix = true
)

// Enable specific task types
planSettings.taskSettings["FileModificationTask"]?.enabled = true
planSettings.taskSettings["InquiryTask"]?.enabled = true

// Create coordinator
val coordinator = PlanCoordinator(
    user = currentUser,
    session = currentSession,
    dataStorage = dataStorage,
    ui = applicationInterface,
    planSettings = planSettings,
    root = projectRoot
)

// Execute a plan
val plan = mapOf(
    "task1" to FileModificationTaskConfigData(
        task_description = "Create a new file",
        files = listOf("newfile.txt")
    )
)
coordinator.executePlan(
    plan = plan,
    task = uiTask,
    userMessage = "Create a new file with hello world content",
    api = apiClient,
    api2 = openAIClient
)
```

### Using Cognitive Modes

```kotlin
// Create an AutoPlanMode instance
val cognitiveMode = AutoPlanMode(
    ui = applicationInterface,
    api = apiClient,
    planSettings = planSettings,
    session = currentSession,
    user = currentUser,
    api2 = openAIClient,
    describer = typeDescriber
)

// Initialize and handle user message
cognitiveMode.initialize()
cognitiveMode.handleUserMessage("Create a REST API with Spring Boot", uiTask)
```

## Task Configuration

Each task type has a specific configuration class that extends `TaskConfigBase`. For example:

```kotlin
val fileModTask = FileModificationTaskConfigData(
    task_description = "Update the README file",
    files = listOf("README.md"),
    related_files = listOf("src/main/java/com/example/App.java"),
    modifications = "Add installation instructions"
)
```

## Visualization

The package includes built-in visualization for task dependencies using Mermaid diagrams:

```kotlin
// Generate a Mermaid diagram for a plan
val diagram = PlanUtil.buildMermaidGraph(plan)
uiTask.add(MarkdownUtil.renderMarkdown(
    "## Task Dependency Graph\n```mermaid\n$diagram\n```",
    ui = applicationInterface
))
```

## Advanced Features

### Software Graph Analysis

The package can analyze software structure and generate task plans based on the codebase graph:

```kotlin
// Generate a software graph
val graphTask = SoftwareGraphGenerationTask(
    planSettings = planSettings,
    planTask = SoftwareGraphGenerationTaskConfigData(
        output_file = "software_graph.json",
        node_types = listOf("CodeFile", "CodePackage")
    )
)

// Use the graph for planning
GraphOrderedPlanMode.graphFile = "software_graph.json"
val graphMode = GraphOrderedPlanMode(
    ui = applicationInterface,
    api = apiClient,
    planSettings = planSettings,
    session = currentSession,
    user = currentUser,
    api2 = openAIClient,
    graphFile = "software_graph.json",
    describer = typeDescriber
)
```

### Web Interaction

The package supports web search and browser automation:

```kotlin
// Perform a web search
val searchTask = CrawlerAgentTask(
    planSettings = planSettings,
    planTask = SearchAndAnalyzeTaskConfigData(
        search_query = "Kotlin coroutines tutorial",
        content_queries = "How to implement async operations",
        follow_links = true
    )
)

// Automate browser interaction
val seleniumTask = SeleniumSessionTask(
    planSettings = planSettings,
    planTask = SeleniumSessionTaskConfigData(
        url = "https://example.com",
        commands = listOf(
            "document.querySelector('#login').click();",
            "return document.title;"
        )
    )
)
```

## Extension Points

The package is designed for extensibility:

1. Create custom task types by extending `AbstractTask`
2. Register new task types with `TaskType.registerConstructor`
3. Implement custom cognitive modes by implementing the `CognitiveMode` interface
4. Extend `PlanSettings` to add custom configuration options

## Best Practices

1. **Task Dependencies**: Ensure proper task dependencies to maintain correct execution order
2. **Task Descriptions**: Provide clear task descriptions for better UI feedback
3. **Error Handling**: Implement proper error handling in custom tasks
4. **Resource Management**: Close resources (like Selenium sessions) properly
5. **Model Selection**: Choose appropriate models for different tasks based on complexity
