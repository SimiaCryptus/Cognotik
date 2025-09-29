# Cognotik Task Library - Complete Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Architecture Overview](#architecture-overview)
3. [Core Task Types](#core-task-types)
4. [Task Configuration](#task-configuration)
5. [Task Execution Flow](#task-execution-flow)

## Introduction

The Cognotik Task Library provides a comprehensive set of AI-powered automation tasks designed to handle complex software development workflows. Built on a flexible orchestration framework, these tasks can be combined to create sophisticated automation pipelines that leverage large language models for intelligent decision-making and code generation.

### Key Capabilities
- **AI-Powered Code Generation**: Automatically generate, modify, and refactor code
- **Intelligent File Operations**: Search, analyze, and modify files with context awareness
- **Web Integration**: Crawl websites and search for information
- **Knowledge Management**: Index and search documents using vector embeddings
- **Self-Healing Execution**: Automatically fix errors in command execution
- **Dynamic Task Orchestration**: Chain tasks with dependencies and conditional execution

## Architecture Overview

### Task Hierarchy

```
AbstractTask (Base)
├── FileTask (Abstract)
│   ├── FileModificationTask
│   └── FileSearchTask
├── RunCodeTask
├── SelfHealingTask
├── KnowledgeIndexingTask
├── VectorSearchTask
└── CrawlerAgentTask
```

### Core Components

1. **Task Interface**: Defines the contract for all tasks
2. **Task Configuration**: Data classes for task-specific settings
3. **Task Orchestrator**: Manages task execution and dependencies
4. **Session Management**: Handles UI interaction and progress tracking
5. **AI Integration**: Connects to various LLM providers

## Core Task Types

### 1. File Operations

#### FileModificationTask
**Purpose**: Create new files or modify existing code with AI assistance

**Key Features**:
- AI-powered code generation and modification
- Git diff integration for context
- Automatic or manual approval workflows
- Multi-file operations support

**Use Cases**:
- Refactoring existing code
- Adding new features to existing files
- Creating new files with proper structure
- Applying consistent changes across multiple files

**Example Configuration**:
```kotlin
FileModificationTaskConfigData(
    files = listOf("src/main/kotlin/MyService.kt"),
    related_files = listOf("src/test/kotlin/MyServiceTest.kt"),
    modifications = "Add error handling and logging",
    includeGitDiff = true,
    task_description = "Improve error handling in MyService"
)
```

#### FileSearchTask
**Purpose**: Search for patterns across project files with contextual results

**Key Features**:
- Substring and regex pattern matching
- Configurable context lines
- Content extraction from non-text files
- Glob pattern file selection

**Use Cases**:
- Finding TODO comments
- Locating specific function implementations
- Searching for configuration values
- Identifying code patterns for refactoring

**Example Configuration**:
```kotlin
SearchTaskConfigData(
    search_pattern = "\\bTODO:\\s*(.+)$",
    is_regex = true,
    context_lines = 3,
    input_files = listOf("src/**/*.kt", "src/**/*.java")
)
```

### 2. Code Execution

#### RunCodeTask
**Purpose**: Execute code through an interpreter to solve problems

**Key Features**:
- Multiple runtime support (Groovy, Kotlin, Shell, etc.)
- Interactive execution with feedback
- Auto-fix capability for code issues
- Environment variable and working directory support

**Use Cases**:
- Data analysis and processing
- Algorithm implementation
- Testing code snippets
- Automated calculations

**Example Configuration**:
```kotlin
RunCodeTaskConfigData(
    goal = "Analyze CSV data and generate statistics",
    workingDir = "./data",
    task_description = "Process sales data"
)
```

#### SelfHealingTask
**Purpose**: Execute commands with automatic error recovery

**Key Features**:
- Automatic error detection and fixing
- Multiple command support
- Configurable working directories
- Command aliasing and resolution

**Use Cases**:
- Build automation
- Test execution
- Deployment scripts
- System maintenance tasks

**Example Configuration**:
```kotlin
SelfHealingTaskConfigData(
    commands = listOf(
        CommandWithWorkingDir(
            command = listOf("npm", "test"),
            workingDir = "frontend"
        ),
        CommandWithWorkingDir(
            command = listOf("gradle", "build"),
            workingDir = "backend"
        )
    ),
    task_description = "Build and test the application"
)
```

### 3. Knowledge Management

#### KnowledgeIndexingTask
**Purpose**: Index documents for semantic search capabilities

**Key Features**:
- Vector embedding generation
- Concurrent file processing
- Progress tracking
- Support for various file types

**Use Cases**:
- Building searchable documentation
- Creating knowledge bases
- Indexing code repositories
- Preparing data for RAG systems

**Example Configuration**:
```kotlin
KnowledgeIndexingTaskConfigData(
    file_paths = listOf(
        "/docs/api",
        "/docs/guides",
        "/src/main"
    ),
    task_description = "Index project documentation"
)
```

#### VectorSearchTask
**Purpose**: Perform semantic similarity searches using embeddings

**Key Features**:
- Positive and negative query support
- Multiple distance metrics
- Content filtering with regex
- Configurable result count

**Use Cases**:
- Finding similar code patterns
- Locating relevant documentation
- Duplicate detection
- Concept discovery

**Example Configuration**:
```kotlin
VectorSearchTaskConfigData(
    positive_queries = listOf("authentication", "security"),
    negative_queries = listOf("deprecated"),
    distance_type = DistanceType.Cosine,
    count = 10,
    min_length = 100
)
```

### 4. Web Integration

#### CrawlerAgentTask
**Purpose**: Crawl websites and analyze content

**Key Features**:
- Google search or direct URL seeding
- Concurrent page processing
- Smart link following
- Content analysis with AI
- Result summarization

**Use Cases**:
- Market research
- Competitive analysis
- Documentation gathering
- Technology trend analysis

**Example Configuration**:
```kotlin
CrawlerTaskConfigData(
    search_query = "latest AI developments 2024",
    content_queries = "What are the key innovations and trends?",
    max_pages_per_task = 20
)
```

## Task Configuration

### Common Configuration Elements

All tasks share common configuration properties:

| Property | Type | Description |
|----------|------|-------------|
| `task_description` | String? | Human-readable task description |
| `task_dependencies` | List<String>? | List of dependent task IDs |
| `state` | TaskState? | Current execution state |

### Task States

Tasks progress through the following states:

1. **Pending**: Task created but not started
2. **Running**: Task currently executing
3. **Completed**: Task finished successfully
4. **Failed**: Task encountered an error
5. **Cancelled**: Task was cancelled by user

### Model Configuration

Tasks that use AI models can be configured with:

```kotlin
ApiChatModel.GPT_4        // Most capable, higher cost
ApiChatModel.GPT_35_TURBO // Faster, lower cost
ApiChatModel.CLAUDE_3     // Alternative provider
// Custom models also supported
```

## Task Execution Flow

### 1. Initialization Phase
```kotlin
// Create task configuration
val config = TaskConfigData(...)

// Initialize task instance
val task = ConcreteTask(orchestrationConfig, config)
```

### 2. Dependency Resolution
```kotlin
// Orchestrator checks dependencies
orchestrator.checkDependencies(task)

// Wait for dependent tasks to complete
orchestrator.waitForDependencies(task)
```

### 3. Execution Phase
```kotlin
// Task executes main logic
task.run(
    agent = orchestrator,
    messages = contextMessages,
    task = sessionTask,
    resultFn = { result -> handleResult(result) },
    orchestrationConfig = config
)
```

### 4. Result Handling
```kotlin
// Process task output
when (task.state) {
    TaskState.Completed -> processSuccess(task.result)
    TaskState.Failed -> handleError(task.error)
    else -> // Handle other states
}
```
