# Cognotik Glossary

## Core Concepts

* **Agent Types** - Functional augmentations to the LLM Chat interface that enable specialized behavior:
    * **ChatAgent** - Basic text input and output for general conversation (formerly SimpleActor)
    * **CodeAgent** - Code generation, compilation, and execution with syntax highlighting (formerly CodingActor)
    * **ImageAgent** - Create images from text prompts using AI image models (formerly ImageActor)
    * **ParsedAgent** - Extract structured data responses from the LLM into typed objects (formerly ParsedActor)
    * **BaseAgent** - Base class for all agent implementations with common functionality

* **Task Types** - Specific invocable procedures that may or may not utilize an LLM, designed for automated execution:
    * **FileModificationTask** - Create, modify, or delete files in the project
    * **RunShellCommandTask** - Execute shell commands safely with output capture
    * **AnalysisTask** - Analyze code and generate insights or documentation (formerly InsightTask)
    * **TaskPlanningTask** - Break down complex tasks into manageable subtasks
    * **VectorSearchTask** - Perform semantic search using AI embeddings (formerly EmbeddingSearchTask)
    * **GitHubSearchTask** - Search GitHub repositories, code, and issues
    * **SelfHealingTask** - Execute commands and automatically fix issues that arise (formerly CommandAutoFixTask)

* **Cognitive Modes** - Control flow strategies for agentic behavior and task execution:
    * **AdaptivePlanningMode** - Iterative planning with adaptive task selection and learning (formerly AutoPlanMode)
    * **WaterfallMode** - Traditional waterfall-style planning with upfront task definition (formerly PlanAheadMode)
    * **HierarchicalPlanningMode** - Hierarchical goal decomposition with dependency management (formerly GoalOrientedMode)
    * **ConversationalMode** - Conversational task execution with maintained context (formerly TaskChatMode)
    * **DependencyGraphMode** - Software graph-based planning with dependency awareness (formerly GraphOrderedPlanMode)

* **API Providers** - Service integrations for various AI and external services:
    * **OpenAI** - GPT models and other OpenAI services
    * **Anthropic** - Claude models and related services
    * **Google/Gemini** - Google's AI models and services
    * **AWS** - Amazon's AI services and models
    * **Perplexity** - Perplexity AI models for search and reasoning
    * **Mistral** - Mistral AI models for multilingual capabilities
    * **Groq** - High-speed inference models
    * **ModelsLab** - Specialized AI models and services
    * **DeepSeek** - Advanced reasoning and coding models
    * **GitHub** - GitHub API for repository and code search
    * **SearchAPI** - Web search capabilities

## System Components

* **OrchestrationConfig** - Central configuration hub containing model settings, task configurations, and execution parameters (formerly PlanSettings)
* **TaskOrchestrator** - Orchestrates task execution, manages dependencies, and coordinates parallel processing (formerly PlanCoordinator)
* **SessionTask** - Interface for UI task management with progress tracking and user interaction
* **SocketManager** - WebSocket-based communication system for real-time UI updates
* **TaskConfigBase** - Base configuration class for all task types with common properties like dependencies and state
* **AbstractTask** - Base class for all executable tasks with common lifecycle methods
* **JOpenAI** - Unified model registry and API for working with AI models across providers
* **Code Runtime Framework** - Extensible system for code execution in multiple programming languages:
    * **KotlinCodeRuntime** - Execute Kotlin code with JSR-223 scripting (formerly KotlinInterpreter)
    * **GroovyCodeRuntime** - Execute Groovy scripts with dynamic capabilities (formerly GroovyInterpreter)
    * **ProcessCodeRuntime** - Execute code via external processes (Python, Node.js, etc.) (formerly ProcessInterpreter)
    * **CodeRuntime** - Base interface for all code execution runtimes (formerly Interpreter)
    * **OutputInterceptor** - Capture stdout/stderr from code execution

## Data Structures

* **TaskState** - Enumeration of task execution states: Pending, InProgress, Completed
* **ExecutionState** - Container for plan execution state including task results and progress (formerly PlanProcessingState)
* **ThinkingStatus** - State tracking for iterative cognitive modes including confidence and knowledge accumulation
* **Goal** - Hierarchical goal structure with dependencies, subtasks, and status tracking
* **Session** - Unique session identification with global and user-specific types
* **User** - Authenticated user representation with credentials and profile information
* **ModelSchema** - Data structures for API requests and responses (chat, completion, image, audio) (formerly ApiModel)

## Utilities and Tools

* **TabbedDisplay** - UI component for creating tabbed interfaces in task outputs
* **FixedConcurrencyProcessor** - Manages concurrent task execution with configurable limits
* **TypeDescriber** - Provides type descriptions for AI model interactions
* **DynamicEnum** - Extensible enumeration system for task types and API providers
* **ChatInterface** - Interface for AI model chat interactions with context management (formerly Chatter)
* **OutputInterceptor** - Thread-local and global output stream capture for code execution
* **FileServlet** - Base class for serving files and directories with automatic MIME detection
* **ZipServlet** - Creates ZIP archives of session directories for download

## Configuration Terms

* **BYOK (Bring Your Own Key)** - Model where users provide their own API keys for AI services
* **Budget** - Resource allocation limit for AI API usage and task execution
* **Temperature** - AI model parameter controlling randomness in responses (0.0 = deterministic, 1.0 = creative)
* **maxTasksPerIteration** - Limit on parallel task execution to manage resource usage
* **maxIterations** - Maximum number of planning/execution cycles before termination
* **autoFix** - Capability to automatically attempt fixes when tasks encounter errors
* **Reasoning Effort** - Parameter for models that support variable reasoning depth (e.g., OpenAI o1 models)
* **Token Limits** - Maximum input/output token constraints per model
* **Pricing Model** - Cost calculation based on tokens, characters, or API calls per model
* **SocketManager** - Manages WebSocket connections and message routing for sessions
* **SessionTask** - Handles individual operations within chat sessions with content buffering
* **ChatSocket** - WebSocket endpoint handling individual client connections
* **ApplicationServices** - Central registry for platform services (auth, storage, usage tracking)
* **AuthenticationInterface** - Contract for user authentication implementations
* **AuthorizationInterface** - Contract for user authorization and permission checking
* **SessionIdFilter** - Protects secure endpoints by validating user authentication
* **OAuthGoogle** - Google OAuth2 authentication flow implementation
* **StorageInterface** - Contract for session data management and persistence
* **DataStorage** - File-based storage implementation with session isolation
* **MetadataStorageInterface** - Contract for session metadata management
* **HSQLMetadataStorage** - In-memory SQL-based metadata storage implementation