# agent\CommandAutofixAction.kt

```kotlin
/**
 * Provides automated fixing of command execution issues through AI assistance.
 *
 * This action helps developers automatically diagnose and fix command execution problems
 * by analyzing exit codes and command output, then suggesting and optionally applying fixes.
 * It integrates with the project's build system and development tools to provide
 * contextual fixes for common command failures.
 *
 * Usage:
 * 1. Select a file/folder in the project view
 * 2. Invoke the action via menu or shortcut
 * 3. Configure command settings in the dialog:
 *    - Select/enter executable path
 *    - Specify command arguments
 *    - Set working directory
 *    - Choose exit code handling
 *    - Add custom instructions
 * 4. Click OK to launch the interactive fix session
 *
 * Features:
 * - Supports any executable command or script
 * - Configurable exit code handling (zero, non-zero, or any)
 * - Persistent command history
 * - Custom working directory selection
 * - Additional instruction support for AI guidance
 * - Optional automatic fix application
 * - Interactive web-based fix session interface
 *
 * Technical Details:
 * - Uses CmdPatchApp for command execution and patch generation
 * - Integrates with project's VFS for file access
 * - Maintains MRU lists for commands and arguments
 * - Runs operations asynchronously to prevent UI blocking
 * - Creates unique sessions for parallel fix operations
 *
 * Configuration Options:
 * - Executable path: Path to command executable
 * - Command arguments: Parameters passed to executable
 * - Working directory: Command execution context
 * - Exit code handling: Which exit codes trigger fixes
 * - Additional instructions: Custom AI guidance
 * - Auto-fix mode: Automatic application of suggested fixes
 *
 * Limitations:
 * - Requires valid executable path
 * - Project must be open
 * - Selected folder or project base path required
 *
 * Error Handling:
 * - Validates executable existence
 * - Checks for project context
 * - Provides error feedback via dialogs
 * - Logs errors for debugging
 *
 * @see CmdPatchApp
 * @see PatchApp
 * @see SessionProxyServer
 * @see AppServer
 */
```

# agent\DocumentedMassPatchAction.kt

```kotlin
/**
 * Applies AI-driven code updates based on documentation standards and specifications.
 *
 * This action facilitates bulk code modifications by analyzing documentation files and applying
 * consistent updates across multiple source files. It provides an interactive UI for selecting
 * documentation and source files, with options for automated or manual application of changes.
 *
 * Usage:
 * 1. Select a folder or multiple files in the project view
 * 2. Invoke the action through the context menu
 * 3. Select relevant documentation (.md) and source files
 * 4. Provide AI instructions for code transformation
 * 5. Choose whether to auto-apply changes
 * 6. Review and confirm changes in the web interface
 *
 * Features:
 * - Supports batch processing of multiple files
 * - Interactive file selection UI with checkboxes
 * - Separate selection for documentation and source files
 * - Web-based review interface
 * - Optional automatic change application
 * - Session-based processing with unique identifiers
 *
 * Configuration Options:
 * - Documentation Files: Select .md files containing standards/specifications
 * - Code Files: Select source files to be modified
 * - AI Instruction: Custom transformation instructions
 * - Auto Apply: Toggle automatic application of changes
 *
 * Technical Details:
 * - Runs in background thread (BGT) for UI responsiveness
 * - Uses SessionProxyServer for managing transformation sessions
 * - Integrates with AppServer for web-based review interface
 * - Implements file validation for appropriate file types
 * - Supports relative path handling for better portability
 *
 * Limitations:
 * - Only processes LLM-includable files (filtered by FileValidationUtils)
 * - Requires at least one selected file or folder
 * - Documentation files must be .md format
 *
 * Example Usage:
 * ```kotlin
 * val action = DocumentedMassPatchAction()
 * val settings = Settings(
 *     UserSettings(
 *         transformationMessage = "Update code documentation",
 *         documentationFiles = listOf(Path("docs/standards.md")),
 *         codeFilePaths = listOf(Path("src/main/kotlin")),
 *         autoApply = true
 *     ),
 *     project
 * )
 * *```
 * @see DocumentedMassPatchServer
 * @see SessionProxyServer
 * @see AppServer
 */
class DocumentedMassPatchAction : BaseAction() {
    // ... rest of the implementation ...
}
```

# agent\DocumentedMassPatchServer.kt

```kotlin
/**
 * Server implementation for handling documented mass code patches with AI assistance.
 * Provides a web-based interface for reviewing and updating code files based on documentation files,
 * generating and applying patches automatically or with user approval.
 *
 * Usage:
 * 1. Initialize with configuration settings, API client, and auto-apply preference
 * 2. Server starts at "/patchChat" endpoint
 * 3. Processes documentation files first, then analyzes code files
 * 4. Generates patches based on AI review
 * 5. Applies patches automatically if autoApply is true, otherwise waits for user approval
 *
 * Features:
 * - Parallel processing of multiple code files
 * - Documentation-driven code review
 * - Interactive patch application interface
 * - Automatic or manual patch approval
 * - Detailed logging of API interactions
 * - Tab-based UI for multiple file reviews
 *
 * Configuration:
 * - DocumentedMassPatchAction.Settings for project and file paths
 * - ChatClient for AI model interaction
 * - Auto-apply flag for patch handling
 *
 * Technical Details:
 * - Uses SkyeNet framework for web UI
 * - Implements ApplicationServer for web interface
 * - Maintains session-based state management
 * - Processes files asynchronously using thread pools
 * - Generates diffs in standard patch format
 * - Integrates with IntelliJ's file system
 *
 * Limitations:
 * - Requires valid project path configuration
 * - Dependent on AI model availability
 * - Processing time scales with number of files
 *
 * Example usage:
 * ```kotlin
 * val server = DocumentedMassPatchServer(
 *     config = settings,
 *     api = chatClient,
 *     autoApply = false
 * )
 * server.start()
 *```
 *
 * @property config Settings containing project and file configurations
 * @property api ChatClient for AI interactions
 * @property autoApply Whether to automatically apply suggested patches
 * @see DocumentedMassPatchAction
 * @see ApplicationServer
 * @see ChatClient
 */
```

# agent\MassPatchAction.kt

```kotlin
/**
 * MassPatchAction enables batch modification of multiple files using AI-assisted code patching.
 *
 * This action provides an interactive interface for applying consistent code changes across multiple files
 * using AI-generated patches. It's particularly useful for large-scale code modifications, refactoring,
 * or implementing consistent changes across a codebase.
 *
 * Usage:
 * 1. Select multiple files or a folder in the project view
 * 2. Invoke the action to open configuration dialog
 * 3. Select specific files to process
 * 4. Enter transformation instructions or select from recent commands
 * 5. Optionally enable auto-apply for changes
 * 6. Confirm to open browser-based interface for reviewing and applying changes
 *
 * Features:
 * - Multi-file selection and processing
 * - Interactive file selection interface
 * - Configurable AI instructions
 * - History of recent transformation commands
 * - Optional automatic patch application
 * - Browser-based review interface
 * - Diff-based change presentation
 * - Context-aware code modifications
 *
 * Configuration Options:
 * - Files to Process: Checkbox list of eligible files
 * - AI Instruction: Custom transformation instructions
 * - Recent Instructions: Dropdown of previously used commands
 * - Auto Apply: Toggle for automatic patch application
 *
 * Technical Details:
 * - Uses OpenAI API for code analysis and patch generation
 * - Implements diff-based patch format for precise changes
 * - Supports parallel processing of multiple files
 * - Integrates with IDE's file system and project structure
 * - Maintains session-based operation tracking
 * - Generates detailed operation logs
 *
 * Limitations:
 * - Only processes LLM-includable files (determined by isLLMIncludableFile)
 * - Requires active project context
 * - Browser-based interface required for review
 *
 * Example Usage:
 * ```kotlin
 * val action = MassPatchAction()
 * val settings = Settings(
 *     UserSettings(
 *         transformationMessage = "Add error handling",
 *         filesToProcess = selectedFiles,
 *         autoApply = false
 *     ),
 *     project = currentProject
 * )
 *```
 *
 * @see DocumentedMassPatchAction
 * @see MassPatchServer
 * @see AppSettingsState
 */
```

# agent\MultiStepPatchAction.kt

```kotlin
/**
 * MultiStepPatchAction provides an AI-assisted multi-step code modification system.
 *
 * This action creates an interactive development assistant that breaks down complex coding tasks 
 * into manageable steps and implements changes across multiple files. It uses AI to analyze code,
 * plan modifications, and generate appropriate patches.
 *
 * Usage:
 * 1. Select folder(s) containing code files in the project view
 * 2. Invoke the action through the context menu
 * 3. Browser opens with the Auto Dev Assistant interface
 * 4. Enter your development request/requirements
 * 5. Review and apply generated code changes
 *
 * Features:
 * - Breaks down complex tasks into discrete steps
 * - Generates detailed implementation plans
 * - Creates code patches in standard diff format
 * - Supports multi-file modifications
 * - Interactive web-based interface
 * - Session-based operation tracking
 * - Configurable AI models and parameters
 *
 * Configuration:
 * - temperature: Controls AI response randomness (default: 0.1)
 * - budget: Maximum API cost limit per session (default: 2.00)
 * - model: AI model selection (configurable via settings)
 *
 * Technical Details:
 * - Uses two-phase approach:
 *   1. Design phase: Analyzes request and creates task list
 *   2. Implementation phase: Generates specific code changes
 * - Implements retry logic for API operations
 * - Supports parallel task processing
 * - Maintains session context for continuous interaction
 * - Generates detailed API logs for debugging
 *
 * Prerequisites:
 * - Selected files/folders in project view
 * - Valid OpenAI API configuration
 * - Sufficient API credits
 *
 * Limitations:
 * - Requires active internet connection
 * - Performance depends on API response times
 * - Changes require manual review and application
 *
 * Error Handling:
 * - Validates file selection before execution
 * - Provides detailed error messages in UI
 * - Implements retry mechanism for failed operations
 *
 * Integration:
 * - Works with IDE's file system
 * - Integrates with version control diff system
 * - Supports multiple programming languages
 *
 * @see BaseAction
 * @see AutoDevApp
 * @see AutoDevAgent
 * @see AppSettingsState
 */
```

# agent\OutlineAction.kt

```kotlin
/**
 * OutlineAction provides an AI-powered outlining tool for structured content creation.
 *
 * This action launches an interactive web interface that helps users create and expand outlines
 * using AI assistance. It supports multi-step outline expansion with configurable AI models
 * and parameters for customized content generation.
 *
 * Usage:
 * 1. Invoke the action from the IDE
 * 2. Configure outline settings in the dialog:
 *    - Set expansion steps and models
 *    - Adjust temperature and token thresholds
 *    - Configure budget and other parameters
 * 3. Confirm settings to launch browser interface
 * 4. Work with the outline tool in web UI
 *
 * Features:
 * - Configurable multi-step outline expansion
 * - Custom temperature control for AI responses
 * - Adjustable token threshold for expansions
 * - Optional projector visualization
 * - Final essay generation capability
 * - Budget management for API usage
 * - Browser-based interactive interface
 *
 * Configuration Options:
 * - expansionSteps: List of models for progressive outline expansion
 * - temperature: Controls AI response randomness
 * - parsingModel: Model used for parsing content
 * - minTokensForExpansion: Minimum tokens required for expansion
 * - showProjector: Toggle visualization feature
 * - writeFinalEssay: Enable/disable final essay generation
 * - budget: Set token usage limits
 *
 * Technical Details:
 * - Runs asynchronously to prevent UI blocking
 * - Integrates with SessionProxyServer for session management
 * - Uses ApplicationServer for web interface hosting
 * - Implements browser-based interaction via AppServer
 * - Maintains session metadata for tracking
 *
 * Dependencies:
 * - OutlineApp from skyenet framework
 * - AppServer for web interface
 * - SessionProxyServer for session management
 * - UITools for progress indication
 *
 * Error Handling:
 * - Validates project context
 * - Handles configuration dialog cancellation
 * - Manages browser launch failures
 * - Provides error logging and user notifications
 *
 * Limitations:
 * - Requires active project context
 * - Depends on browser access
 * - Network connectivity required
 *
 * @see OutlineConfigDialog
 * @see OutlineApp
 * @see SessionProxyServer
 * @see AppServer
 */
```

# agent\OutlineConfigDialog.kt

```kotlin
/**
 * Configuration dialog for the AI-powered document outline generation tool.
 * 
 * This dialog allows users to configure multiple aspects of the outline generation process,
 * including model selection, temperature settings, and output preferences. It provides
 * a multi-step approach to outline generation with configurable AI models.
 *
 * Usage:
 * 1. Launch from the Outline action
 * 2. Configure expansion steps by adding/removing/editing models
 * 3. Adjust global settings like temperature and token thresholds
 * 4. Set output preferences and budget
 * 5. Click OK to apply settings
 *
 * Features:
 * - Multi-step outline generation configuration
 * - Dynamic model selection based on available API keys
 * - Customizable temperature and token thresholds
 * - Budget control for API usage
 * - Optional projector visualization
 * - Final essay generation option
 *
 * Configuration Options:
 * - Expansion Steps: Ordered list of AI models for progressive outline refinement
 * - Parsing Model: Specific model for structure analysis
 * - Temperature: Creativity level (0-100)
 * - Min Tokens: Threshold for section expansion
 * - Projector Display: Toggle relationship visualization
 * - Final Essay: Toggle final content generation
 * - Budget: Maximum cost allocation
 *
 * Technical Details:
 * - Implements DialogWrapper for standard IDE dialog behavior
 * - Uses DSL builder pattern for UI construction
 * - Validates configuration before acceptance
 * - Maintains state through OutlineSettings data class
 * - Filters available models based on API key availability
 *
 * Limitations:
 * - Requires at least one expansion step
 * - Models limited to those with valid API keys
 * - Budget must be between 0.1 and 10.0 dollars
 *
 * @see OutlineAction
 * @see ModelSelectionDialog
 * @see OutlineSettings
 * @see ExpansionStep
 */
```

# agent\ShellCommandAction.kt

```kotlin
/**
 * Provides an interactive shell command execution interface with AI assistance.
 *
 * This action creates a browser-based interface for executing shell commands in a selected directory
 * with AI-powered assistance. It supports both PowerShell (Windows) and Bash (Unix) environments,
 * providing intelligent command interpretation and execution feedback.
 *
 * Usage:
 * 1. Select a folder in the project view
 * 2. Invoke the Shell Command action
 * 3. Wait for browser interface to open
 * 4. Enter commands in the chat interface
 * 5. View execution results and AI suggestions
 *
 * Features:
 * - Interactive browser-based command execution
 * - AI-assisted command interpretation
 * - Platform-aware shell selection (PowerShell/Bash)
 * - Real-time execution feedback
 * - Session persistence
 * - Error handling and graceful recovery
 *
 * Configuration:
 * - Shell command: Configurable through AppSettingsState
 * - Temperature: Controls AI response randomness
 * - Model: Uses smart model from AppSettingsState
 *
 * Technical Details:
 * - Implements ApplicationServer for web interface
 * - Uses ProcessInterpreter for command execution
 * - Integrates with CodingAgent for AI assistance
 * - Maintains session state for continuous interaction
 * - Runs commands asynchronously to prevent UI blocking
 *
 * Limitations:
 * - Requires folder selection for execution context
 * - Single input mode only
 * - Browser-dependent interface
 * - Network connectivity required for AI features
 *
 * Error Handling:
 * - Validates folder selection
 * - Catches and displays execution exceptions
 * - Provides user feedback for initialization failures
 * - Supports command cancellation
 *
 * Example usage:
 * ```kotlin
 * val action = ShellCommandAction()
 * action.handle(event) // Initializes shell environment and opens browser interface
 *```
 *
 * @see BaseAction
 * @see SessionProxyServer
 * @see CodingAgent
 * @see ProcessInterpreter
 * @see AppSettingsState
 */
```

# agent\SimpleCommandAction.kt

```kotlin
/**
 * SimpleCommandAction provides an AI-powered code assistance interface for executing commands and making code modifications.
 *
 * This action creates an interactive chat-based interface that allows users to describe desired code changes
 * and automatically generates corresponding patches. It supports both single-file and multi-file modifications
 * while maintaining context awareness of the project structure.
 *
 * Usage:
 * 1. Select file(s) or folder in project view
 * 2. Invoke the action through IDE menu or shortcut
 * 3. Browser opens with chat interface
 * 4. Enter natural language commands to modify code
 * 5. Review and apply generated patches
 *
 * Features:
 * - Interactive chat-based interface
 * - Intelligent file selection and context gathering
 * - Automatic patch generation in diff format
 * - Multi-file modification support
 * - Search capability across project files
 * - Built-in retry mechanism for reliability
 * - Progress tracking for long operations
 *
 * Technical Details:
 * - Maximum file size limit: 512KB for performance
 * - Uses OpenAI API for code analysis and generation
 * - Implements session-based communication
 * - Supports markdown rendering
 * - Handles file paths with wildcard expansion
 * - Includes diff visualization and application
 *
 * Configuration:
 * - Working directory: Project root or selected folder
 * - Model selection: Configurable through AppSettingsState
 * - Session management: Automatic with unique IDs
 * - Browser integration: Automatic launch with delay
 *
 * Limitations:
 * - File size restricted to 512KB
 * - Requires valid project context
 * - Network dependency for AI operations
 * - Browser required for interface
 *
 * Error Handling:
 * - Validates file operations and paths
 * - Provides user feedback for failures
 * - Implements retry logic for API calls
 * - Graceful handling of cancellation
 *
 * Example Usage:
 * ```kotlin
 * // Basic action invocation
 * val action = SimpleCommandAction()
 * action.handle(event)
 *
 * // Custom settings
 * val settings = Settings(
 *     workingDirectory = File("/path/to/project")
 * )
 *```
 *
 * @see BaseAction
 * @see SessionProxyServer
 * @see AppSettingsState
 * @see FileValidationUtils
 */
```

# agent\WebDevelopmentAssistantAction.kt

```kotlin
/**
 * An AI-powered web development assistant that helps create and manage web application projects.
 *
 * This action provides an interactive development environment for web applications, offering automated
 * generation of HTML, CSS, JavaScript, and image files through AI assistance. It creates a complete
 * project structure based on user requirements and manages the development workflow.
 *
 * Usage:
 * 1. Select a directory in the project explorer
 * 2. Right-click and select "Web Development Assistant"
 * 3. Browser opens with the assistant interface
 * 4. Describe your web application requirements
 * 5. Review and refine generated code through the interactive UI
 *
 * Features:
 * - Complete web application scaffolding
 * - Automated generation of HTML, CSS, and JavaScript files
 * - AI-powered image generation for web assets
 * - Interactive code review and refinement
 * - Real-time file updates and previews
 * - Multi-file project management
 * - Integrated development workflow
 *
 * Technical Details:
 * - Uses OpenAI API for code and image generation
 * - Implements actor-based architecture for different file types
 * - Supports parallel file processing
 * - Provides real-time code review and suggestions
 * - Maintains session-based project context
 *
 * Configuration:
 * - temperature: Controls AI response creativity (default: 0.1)
 * - model: Specifies the main AI model (default: GPT4)
 * - parsingModel: Model for parsing responses (default: GPT4-mini)
 * - tools: List of additional development tools
 * - budget: Token budget for API calls (default: 2.00)
 *
 * Supported File Types:
 * - HTML (.html)
 * - CSS (.css)
 * - JavaScript (.js)
 * - Images (.png, .jpg)
 * - Other web assets
 *
 * Limitations:
 * - Requires directory selection
 * - Depends on OpenAI API availability
 * - Limited to web development projects
 *
 * Example Usage:
 * ```kotlin
 * val action = WebDevelopmentAssistantAction()
 * action.handle(event) // Opens web UI for development
 *```
 *
 * @see BaseAction
 * @see WebDevApp
 * @see WebDevAgent
 * @see ProjectSpec
 */
```

# chat\CodeChatAction.kt

```kotlin
/**
 * Provides an interactive AI-powered code chat interface for discussing and analyzing code.
 *
 * CodeChatAction creates a web-based chat interface that allows developers to discuss code with an AI assistant.
 * It captures the current code selection or file content and initializes a chat session with context-aware
 * understanding of the programming language and file type.
 *
 * Usage:
 * 1. Select code in editor (optional - entire file will be used if no selection)
 * 2. Invoke action through menu or shortcut
 * 3. Browser window opens with chat interface
 * 4. Interact with AI assistant about the code
 *
 * Features:
 * - Language-aware code analysis
 * - Persistent chat sessions
 * - Web-based interface
 * - Support for full file or selection-based context
 * - Integration with IDE's language detection
 * - Session management and history
 *
 * Technical Details:
 * - Uses WebSocket for real-time communication
 * - Integrates with OpenAI's chat models
 * - Runs chat interface in system browser
 * - Background thread handling for UI responsiveness
 * - Session-based architecture for managing multiple chats
 *
 * Configuration:
 * - Model: Configurable through AppSettingsState.smartModel
 * - Storage: Uses ApplicationServices.dataStorageFactory
 * - Session: Automatically generated unique session IDs
 *
 * Dependencies:
 * - AppServer for web interface hosting
 * - CodeChatSocketManager for WebSocket handling
 * - AppSettingsState for configuration
 * - OpenAI API integration
 *
 * Limitations:
 * - Requires active internet connection
 * - Depends on OpenAI API availability
 * - One chat session per invocation
 *
 * @see CodeChatSocketManager
 * @see AppSettingsState
 * @see SessionProxyServer
 * @see AppServer
 */
class CodeChatAction : BaseAction() {
    // ... rest of the implementation ...
}
```

# chat\DiffChatAction.kt

```kotlin
/**
 * Interactive code modification action that enables AI-assisted code changes through a diff-based chat interface.
 *
 * This action opens a chat interface where users can discuss code modifications with an AI assistant. The AI provides
 * suggestions in a standardized diff format that can be directly applied to the code. It supports both full file and
 * selection-based modifications.
 *
 * Usage:
 * 1. Select code in editor (optional - will use full file if no selection)
 * 2. Invoke action through menu or shortcut
 * 3. Chat interface opens in browser
 * 4. Discuss changes with AI
 * 5. Click "Apply" links to implement suggested changes
 *
 * Features:
 * - Interactive chat interface in browser window
 * - Contextual code understanding
 * - Standardized diff format output
 * - One-click patch application
 * - Supports all text-based file types
 * - Maintains code context during chat
 * - Session persistence
 *
 * Technical Details:
 * - Uses WebSocket for real-time communication
 * - Implements custom diff rendering with clickable apply links
 * - Integrates with IntelliJ's document model for safe writes
 * - Maintains session state across interactions
 * - Supports both full file and selection-based contexts
 * 
 * Configuration:
 * - Uses AppSettingsState.smartModel for AI model selection
 * - Customizable system prompt for diff formatting
 * - Session-based chat history storage
 *
 * Prerequisites:
 * - Active editor with valid document
 * - Project context
 * - Network access for AI API calls
 *
 * Limitations:
 * - Requires browser for chat interface
 * - Changes are sequential and must be applied individually
 * - Subject to AI model token limits
 *
 * Error Handling:
 * - Validates editor and document availability
 * - Provides error feedback through UI dialogs
 * - Logs errors for debugging
 *
 * Example Diff Format:
 * ```diff
 * ### filename.ext
 *  // Context lines
 *  function example() {
 * -   return oldValue;
 * +   return newValue;
 *  }
 *```
 *
 * @see CodeChatSocketManager
 * @see SessionProxyServer
 * @see AppServer
 * @see BaseAction
 */
```

# chat\GenericChatAction.kt

```kotlin
/**
 * Generic Chat Action provides a base implementation for AI-powered chat interactions within the IDE.
 *
 * This action launches a web-based chat interface that allows users to have free-form conversations
 * with an AI model. It serves as a general-purpose chat interface that can be used for various
 * development tasks and queries.
 *
 * Usage:
 * 1. Invoke the action from the IDE
 * 2. A new browser window opens with the chat interface
 * 3. Enter messages to interact with the AI assistant
 * 4. Chat session persists until browser window is closed
 *
 * Features:
 * - Web-based chat interface
 * - Persistent session management
 * - Configurable AI model settings
 * - Asynchronous message handling
 * - Integration with IDE project context
 *
 * Technical Details:
 * - Uses AppServer for web interface hosting
 * - Implements ChatSocketManager for WebSocket communication
 * - Integrates with OpenAI chat models
 * - Runs chat initialization in background thread
 * - Maintains session state through ApplicationServer
 *
 * Configuration:
 * - Model: Configured through AppSettingsState.smartModel
 * - System Prompt: Customizable via systemPrompt property
 * - User Interface Prompt: Configurable via userInterfacePrompt property
 *
 * Limitations:
 * - Requires active project context
 * - Depends on browser for interface rendering
 * - Network connectivity required for AI model access
 *
 * Error Handling:
 * - Gracefully handles browser launch failures
 * - Logs errors through SLF4J
 * - Provides user feedback for initialization errors
 *
 * @see BaseAction
 * @see ChatSocketManager
 * @see AppServer
 * @see AppSettingsState
 */
class GenericChatAction : BaseAction() {
    // ... rest of the implementation ...
}
```

# chat\LargeOutputChatAction.kt

```kotlin
/**
 * Enhanced Chat Interface for Large-Scale Code Discussions
 *
 * Provides an advanced chat interface optimized for handling large, structured responses 
 * in code-related discussions. This action creates a dedicated browser-based chat session
 * with enhanced formatting and organization capabilities.
 *
 * Usage:
 * 1. Invoke the action through IDE menu or shortcut
 * 2. A new browser window opens with the chat interface
 * 3. Enter coding questions or requests
 * 4. Receive structured, detailed responses broken down into clear sections
 *
 * Features:
 * - Enhanced response formatting using ellipsis notation
 * - Persistent chat sessions with unique identifiers
 * - Structured output organization for complex explanations
 * - Integration with IDE's project context
 * - Asynchronous processing for better performance
 *
 * Technical Details:
 * - Uses LargeOutputActor for handling extensive responses
 * - Implements background thread processing (BGT)
 * - Maintains session state through SessionProxyServer
 * - Configurable model parameters:
 *   * Temperature: 0.3 (balanced between creativity and precision)
 *   * Max iterations: 3
 *
 * Configuration:
 * - Model: Configured through AppSettingsState
 * - System Prompt: Defines AI assistant behavior
 * - User Interface Prompt: Sets chat interface expectations
 *
 * Dependencies:
 * - AppServer for web interface hosting
 * - EnhancedChatSocketManager for session management
 * - ApplicationServices for data storage
 * - UITools for IDE integration
 *
 * Limitations:
 * - Requires active project context
 * - Browser-based interface required
 * - Network connectivity needed for API calls
 *
 * Error Handling:
 * - Graceful handling of browser launch failures
 * - Project context validation
 * - Async operation error management
 *
 * @see EnhancedChatSocketManager
 * @see AppServer
 * @see BaseAction
 * @see LargeOutputActor
 */
class LargeOutputChatAction : BaseAction() {
    // ... rest of the implementation ...
}
```

# chat\MultiCodeChatAction.kt

```kotlin
/**
 * Enables interactive AI-assisted code discussions and modifications across multiple files.
 * 
 * This action provides a chat interface for discussing and modifying multiple code files simultaneously.
 * It creates a web-based chat session where users can interact with an AI assistant to analyze, discuss,
 * and modify code across multiple files in the project.
 *
 * Usage:
 * 1. Select multiple files or folders in the project view
 * 2. Right-click and select "Multi-Code Chat"
 * 3. A browser window opens with the chat interface
 * 4. Enter questions or requests about the selected code
 * 5. Review and apply suggested code modifications
 *
 * Features:
 * - Supports simultaneous discussion of multiple files
 * - Real-time code modification suggestions
 * - Interactive patch application
 * - Token count monitoring for selected files
 * - Persistent chat sessions
 * - Automatic file diff generation and application
 *
 * Technical Details:
 * - Uses GPT-4 tokenizer for content management
 * - Implements retry logic for API operations
 * - Maintains session state for ongoing conversations
 * - Generates clickable file diff links for code modifications
 * - Supports both file and folder-level selection
 *
 * Configuration:
 * - Budget control for API usage (default: 2.00)
 * - Model selection via AppSettingsState
 * - Customizable session naming
 *
 * Limitations:
 * - Requires valid file selection in project view
 * - Performance may vary with large numbers of files
 * - Token limits based on selected AI model
 *
 * Implementation Notes:
 * - Creates a web socket connection for real-time communication
 * - Maintains file paths relative to project root
 * - Logs API interactions for debugging
 * - Handles both single files and directory structures
 *
 * Error Handling:
 * - Validates file selection before activation
 * - Provides error feedback through UI
 * - Implements retry mechanism for failed API calls
 * 
 * Example Usage:
 * ```kotlin
 * // Programmatic usage
 * val action = MultiCodeChatAction()
 * action.handle(event)
 *```
 *
 * @see BaseAction
 * @see AppServer
 * @see SessionProxyServer
 * @see MultiStepPatchAction
 */
```

# chat\MultiDiffChatAction.kt

```kotlin
/**
 * Provides an interactive chat interface for reviewing and modifying multiple code files simultaneously.
 *
 * This action opens a browser-based chat interface that allows users to discuss and modify multiple
 * code files in a single conversation. It supports generating and applying code patches across files
 * using diff format, making it ideal for coordinated code changes and refactoring tasks.
 *
 * Usage:
 * 1. Select one or more files/folders in the project view
 * 2. Invoke the action through the context menu or shortcut
 * 3. Browser opens with chat interface
 * 4. Enter questions or requests about the code
 * 5. Review and apply suggested changes through diff patches
 *
 * Features:
 * - Multi-file code context awareness
 * - Interactive chat with AI assistance
 * - Generates and applies patches in standard diff format
 * - Supports both single files and directory selections
 * - Automatic token counting for selected files
 * - Clickable diff links for easy code updates
 * - Session persistence and logging
 *
 * Technical Details:
 * - Uses AppServer for browser communication
 * - Implements SessionProxyServer for chat management
 * - Supports binary file detection and filtering
 * - Maintains file context through relative paths
 * - Uses GPT4Tokenizer for content size estimation
 * - Implements ApplicationServer for web interface
 *
 * Configuration:
 * - Model: Configurable through AppSettingsState
 * - Budget: Customizable per session (default 2.00)
 * - Session naming: Automatic with timestamp
 *
 * Limitations:
 * - Cannot process binary files
 * - Requires valid file selection
 * - Performance depends on total code size
 * - Browser access required for interface
 *
 * Error Handling:
 * - Validates file existence before processing
 * - Logs errors with stack traces
 * - Shows user-friendly error dialogs
 * - Implements comprehensive error recovery
 *
 * Example Usage:
 * ```kotlin
 * val action = MultiDiffChatAction()
 * action.handle(event) // Opens chat interface for selected files
 *```
 *
 * @see BaseAction
 * @see SessionProxyServer
 * @see AppServer
 * @see ApplicationServer
 */
```

# dev\ApplyPatchAction.kt

```kotlin
/**
 * Applies patch content to selected files in the IDE.
 * 
 * This action allows developers to apply text-based patches to files directly within the IDE.
 * It provides a simple interface for inputting patch content and handles the patch application
 * process with appropriate error handling and user feedback.
 *
 * Usage:
 * 1. Select a single file in the project view or editor
 * 2. Invoke "Apply Patch" action
 * 3. Enter the patch content in the displayed dialog
 * 4. System applies the patch and shows results/errors
 *
 * Features:
 * - Interactive patch content input dialog
 * - Validation of patch content
 * - Immediate feedback on patch application
 * - Supports any text-based file
 * - Undo/redo support through write commands
 *
 * Technical Details:
 * - Uses IterativePatchUtil for patch application
 * - Runs in background thread for UI responsiveness
 * - Executes as a write command for proper undo/redo support
 * - Validates patch content before application
 * - Provides visual feedback for success/failure cases
 *
 * Limitations:
 * - Only supports single file selection
 * - Requires non-empty patch content
 * - Patch must be in valid format
 *
 * Error Handling:
 * - Validates project and file selection
 * - Checks for empty patch content
 * - Handles patch application failures
 * - Provides user feedback for all error cases
 *
 * Prerequisites:
 * - Active project
 * - Single file selected
 * - Valid patch content
 *
 * @see IterativePatchUtil
 * @see WriteCommandAction
 * @see BaseAction
 */
```

# dev\LineFilterChatAction.kt

```kotlin
/**
 * Interactive chat interface for analyzing and discussing code with line-by-line reference capabilities.
 *
 * This action opens a chat interface that allows developers to discuss code while maintaining the ability
 * to reference specific lines by their numbers. It provides a structured way to analyze code segments
 * with AI assistance while preserving context and enabling precise line references.
 *
 * Usage:
 * 1. Select code in the editor (or entire file will be used)
 * 2. Invoke the action through the IDE menu or shortcut
 * 3. A browser window opens with the chat interface
 * 4. Reference specific lines using line numbers in the chat
 *
 * Features:
 * - Line-numbered code display for easy reference
 * - Markdown formatting support in responses
 * - Language-aware code formatting
 * - Supports both selection and full file analysis
 * - Real-time chat interface in browser
 * - Line injection syntax for referencing code (e.g., "001" injects line 1)
 *
 * Technical Details:
 * - Uses WebSocket-based chat implementation
 * - Integrates with OpenAI chat models
 * - Implements custom response rendering for line references
 * - Runs chat server on local port
 * - Session-based chat management
 * - Background thread handling for browser launch
 *
 * Configuration:
 * - Requires valid AppSettingsState configuration
 * - Uses smartModel setting for AI model selection
 * - Accessible only when devActions is enabled
 *
 * Prerequisites:
 * - Active editor with valid file
 * - Supported programming language
 * - Valid AI model configuration
 *
 * Limitations:
 * - Only available when devActions is enabled
 * - Requires browser access
 * - Depends on external AI service availability
 *
 * Example Usage:
 * ```kotlin
 * // Reference specific lines in chat
 * 001 // References first line of code
 * ## Some markdown heading
 * 025 // References line 25 of code
 *```
 *
 * @see BaseAction
 * @see SessionProxyServer
 * @see ChatSocketManager
 */
```

# dev\PrintTreeAction.kt

```kotlin
/**
 * PrintTreeAction provides PSI tree structure visualization for code analysis and debugging.
 *
 * This action generates and logs a detailed tree representation of the PSI (Program Structure Interface)
 * structure for the currently selected code element. It's primarily designed for developers to inspect
 * and understand the internal code structure representation used by IntelliJ IDEA.
 *
 * Usage:
 * 1. Enable "devActions" in the plugin settings
 * 2. Open the file you want to analyze
 * 3. Place cursor in code or select code segment
 * 4. Right-click and select "Print Tree" from the context menu
 * 5. View the tree structure in the IDE's log
 *
 * Features:
 * - Visualizes complete PSI tree hierarchy
 * - Supports all file types with PSI representation
 * - Runs asynchronously to prevent UI freezing
 * - Provides progress indication during analysis
 *
 * Technical Details:
 * - Executes on background thread using ApplicationManager
 * - Uses PsiUtil for PSI tree traversal and formatting
 * - Implements progress tracking via ProgressIndicator
 * - Logs output using SLF4J
 *
 * Prerequisites:
 * - Requires "devActions" setting to be enabled
 * - Needs valid PSI context (active editor with valid file)
 *
 * Error Handling:
 * - Validates PSI entity availability before processing
 * - Logs warnings for missing PSI context
 * - Reports execution errors through UI notification
 *
 * Limitations:
 * - Only available when devActions setting is enabled
 * - Requires valid PSI structure in current context
 * - Output is limited to log viewing
 *
 * Example output in log:
 *```
 * PsiFile
 *   PsiClass
 *     PsiMethod
 *       PsiParameter
 *       PsiCodeBlock
 *```
 *
 * @property log SLF4J Logger instance for output and error reporting
 * @see BaseAction Base action implementation
 * @see PsiUtil Utility class for PSI operations
 * @see AppSettingsState Plugin settings management
 */
```

# editor\CustomEditAction.kt

```kotlin
/**
 * Provides AI-powered custom code editing capabilities with natural language instructions.
 *
 * This action allows users to modify selected code by providing natural language instructions
 * for the desired changes. It leverages AI to interpret the instructions and apply appropriate
 * code transformations while preserving the code's language-specific syntax and style.
 *
 * Usage:
 * 1. Select code in the editor
 * 2. Invoke the action (via shortcut or menu)
 * 3. Enter edit instruction in the dialog
 * 4. AI processes the instruction and updates the code
 *
 * Features:
 * - Natural language instruction processing
 * - Multi-language support
 * - Code context preservation
 * - Command history tracking
 * - Configurable AI model parameters
 * - Progress indication during processing
 *
 * Configuration:
 * - Temperature: Controls AI creativity (via AppSettingsState)
 * - Model: AI model selection for processing
 * - Human Language: Output language preference
 *
 * Technical Details:
 * - Uses ChatProxy for AI interaction
 * - Implements VirtualAPI interface for code editing
 * - Maintains edit history in settings
 * - Runs in background thread (BGT)
 * - Provides progress feedback
 *
 * Error Handling:
 * - Validates selection requirements
 * - Handles API failures with user feedback
 * - Preserves original code on error
 * - Logs errors for debugging
 *
 * Example Usage:
 * ```kotlin
 * // Original code
 * println("Hello")
 * 
 * // Instruction: "Add parameter validation"
 * // Result:
 * if (message != null) {
 *     println("Hello")
 * }
 *```
 *
 * Limitations:
 * - Requires active selection
 * - Depends on AI service availability
 * - Processing time varies with request complexity
 *
 * @see SelectionAction
 * @see AppSettingsState
 * @see ChatProxy
 */
```

# editor\DescribeAction.kt

```kotlin
/**
 * Generates natural language descriptions of selected code blocks with appropriate code comments.
 *
 * This action analyzes selected code and generates human-readable descriptions using AI, 
 * automatically formatting them as comments appropriate for the programming language.
 * It helps improve code readability and documentation by providing clear explanations
 * of what the code does.
 *
 * Usage:
 * 1. Select a block of code in the editor
 * 2. Invoke the Describe action (via shortcut or menu)
 * 3. The action will replace the selection with commented description followed by original code
 *
 * Features:
 * - Automatic language detection based on file extension
 * - Smart comment style selection (line vs block comments based on description length)
 * - Preserves code indentation
 * - Supports multiple programming languages
 * - Configurable output language via AppSettingsState.humanLanguage
 * 
 * Technical Details:
 * - Uses ChatProxy for AI-powered code description generation
 * - Processes code asynchronously in background thread
 * - Handles both single-line and multi-line descriptions
 * - Wraps text at 120 characters for readability
 * - Preserves original code formatting and indentation
 *
 * Configuration:
 * - Temperature: Controls AI response randomness via AppSettingsState
 * - Model: Uses smart model from AppSettingsState
 * - Human Language: Configurable via AppSettingsState.humanLanguage
 *
 * Limitations:
 * - Requires valid code selection
 * - Language detection depends on file extension
 * - May fail if AI service is unavailable
 *
 * Example output:
 *```
 * // This function calculates the factorial of a number recursively
 * fun factorial(n: Int): Int {
 *     return if (n <= 1) 1 else n * factorial(n - 1)
 * }
 *```
 *
 * @see SelectionAction
 * @see ComputerLanguage
 * @see AppSettingsState
 */
class DescribeAction : SelectionAction<String>() {
    // ... existing implementation
}
```

# editor\PasteAction.kt

```kotlin
/**
 * Base class for intelligent clipboard paste actions that automatically convert content to appropriate code format.
 * 
 * This action analyzes clipboard content and converts it to match the target file's programming language,
 * supporting both plain text and HTML clipboard content with automatic language detection.
 *
 * Usage:
 * 1. Copy content from any source (webpage, document, code file)
 * 2. Select target location in code editor
 * 3. Invoke paste action via shortcut or menu
 * 4. Content is automatically converted to match target file's language
 *
 * Features:
 * - Supports both text and HTML clipboard content
 * - Automatic source language detection
 * - HTML content cleaning and optimization
 * - Configurable AI model selection
 * - Progress indication during conversion
 * 
 * Technical Details:
 * - Uses ChatGPT API for content conversion
 * - HTML processing via JSoup library
 * - Implements clipboard access safety checks
 * - Supports multiple clipboard data flavors
 * - HTML content size limited to 100KB
 *
 * Configuration:
 * - Model selection via AppSettingsState
 * - Temperature setting for conversion flexibility
 * 
 * Limitations:
 * - Requires valid clipboard content
 * - May have reduced accuracy with complex HTML
 * - Performance depends on AI model response time
 *
 * @see SmartPasteAction
 * @see FastPasteAction
 */
abstract class PasteActionBase...

/**
 * Smart paste implementation using more capable but slower AI model.
 * 
 * This action provides high-quality code conversion with better understanding
 * of complex code structures and language features.
 *
 * Usage:
 * - Use for complex code conversions
 * - When accuracy is more important than speed
 *
 * Features:
 * - Advanced language understanding
 * - Better handling of complex code patterns
 * - More accurate type conversion
 *
 * @see PasteActionBase
 */
class SmartPasteAction...

/**
 * Fast paste implementation using simpler but quicker AI model.
 *
 * This action provides rapid code conversion for simple content,
 * optimized for speed over complex language understanding.
 *
 * Usage:
 * - Use for simple code conversions
 * - When speed is more important than perfect accuracy
 *
 * Features:
 * - Faster response times
 * - Lower token usage
 * - Progress tracking
 *
 * Technical Details:
 * - Uses smaller/faster AI model
 * - Implements progress indication
 * - Optimized for common conversion patterns
 *
 * @see PasteActionBase
 */
class FastPasteAction...
```

# editor\RecentCodeEditsAction.kt

```kotlin
/**
 * Provides quick access to recently used custom edit commands through a dynamic action group.
 *
 * This action creates a submenu of the most recently used custom edit commands, allowing users
 * to quickly reuse previous editing operations. Each command appears as a numbered menu item,
 * with the first 9 items having keyboard shortcuts (_1 through _9).
 *
 * Usage:
 * 1. Select code in the editor
 * 2. Access the context menu or action menu
 * 3. Choose from the list of recent custom edits
 * 4. The selected edit operation will be applied to the current selection
 *
 * Features:
 * - Maintains history of up to 10 most recent custom edit commands
 * - Keyboard shortcuts for first 9 items (_1 through _9)
 * - Dynamic menu updates based on command history
 * - Context-aware visibility (only shown for valid code selections)
 *
 * Technical Details:
 * - Extends ActionGroup to create dynamic submenu
 * - Uses background thread for action updates
 * - Integrates with AppSettingsState for command history storage
 * - Creates CustomEditAction instances dynamically for each history item
 *
 * Limitations:
 * - Not available for plain text files
 * - Requires active text selection
 * - Limited to 10 most recent commands
 * - Only visible when valid code is selected
 *
 * @see CustomEditAction
 * @see AppSettingsState
 * @see ComputerLanguage
 */
class RecentCodeEditsAction : ActionGroup() {
    // ... rest of the implementation ...
}
```

# editor\RedoLast.kt

```kotlin
/**
 * RedoLast provides functionality to repeat the most recent AI Coder action in the editor.
 *
 * This action enables users to quickly repeat their last AI-assisted operation without having
 * to reconfigure or reselect options. It maintains a history of operations per document and
 * allows for efficient workflow by rerunning previous actions.
 *
 * Usage:
 * 1. Open a file in the editor
 * 2. Ensure a previous AI Coder action has been performed
 * 3. Invoke the RedoLast action through:
 *    - Keyboard shortcut
 *    - Editor context menu
 *    - Action search
 *
 * Features:
 * - Document-specific action history
 * - Background thread execution
 * - Automatic state management
 * - Quick access to previous operations
 *
 * Technical Details:
 * - Runs on background thread (BGT) to prevent UI freezing
 * - Uses UITools.retry map to store document-specific actions
 * - Requires active editor and document context
 * - Maintains action state per editor document
 *
 * Limitations:
 * - Only stores the most recent action per document
 * - Requires a previous action to be available
 * - Must be executed with an active editor context
 *
 * Error Handling:
 * - Automatically disables when no previous action exists
 * - Validates editor context before execution
 * - Safely handles missing document scenarios
 *
 * Dependencies:
 * - BaseAction
 * - UITools.retry
 * - IntelliJ Platform SDK
 *
 * @see BaseAction
 * @see com.simiacryptus.aicoder.util.UITools
 * @see com.intellij.openapi.editor.Document
 */
```

# find\FindResultsModificationAction.kt

```kotlin
/**
 * Enables batch modification of code based on find results using AI assistance.
 * 
 * This action allows users to modify multiple occurrences of found text across files
 * by leveraging AI to suggest and apply consistent changes. It provides an interactive
 * interface through a web browser for reviewing and applying modifications.
 * 
 * Usage:
 * 1. Perform a find operation in the IDE
 * 2. Select the find results
 * 3. Invoke "Modify Find Results" action
 * 4. Enter replacement text and configuration in dialog
 * 5. Review and approve changes in browser interface
 * 
 * Features:
 * - AI-assisted code modifications across multiple files
 * - Interactive web UI for reviewing changes
 * - Context-aware code analysis
 * - Batch application of changes
 * - Auto-apply option for automated modifications
 * - Preserves code structure and formatting
 * 
 * Configuration Options:
 * - Replacement text: The desired modification pattern
 * - Auto-apply: Toggle automatic application of changes
 * 
 * Technical Details:
 * - Integrates with IntelliJ's Usage View system
 * - Uses OpenAI API for intelligent code modifications
 * - Implements web-based diff review interface
 * - Handles PSI-based code analysis for context awareness
 * - Supports parallel processing of multiple files
 * 
 * Prerequisites:
 * - Active find results in Usage View
 * - Valid project context
 * - Selected files must be writable
 * 
 * Limitations:
 * - Requires active find results
 * - Changes are limited to found text contexts
 * - Network connectivity required for AI assistance
 * 
 * Error Handling:
 * - Validates find results availability
 * - Checks for project context
 * - Handles browser launch failures
 * - Provides error feedback through UI
 * 
 * Example Usage:
 * ```kotlin
 * val action = FindResultsModificationAction()
 * action.handle(event)
 *```
 * 
 * @see UsageView
 * @see PatchApp
 * @see FindResultsModificationDialog
 * @see ApplicationServer
 */
```

# find\FindResultsModificationDialog.kt

```kotlin
/**
 * Dialog for configuring AI-based modifications to search results in code.
 *
 * Provides an interface for users to enter natural language instructions for modifying code matches
 * found through IDE's find functionality. The dialog collects user preferences for how the 
 * modifications should be applied and validated.
 *
 * Usage:
 * 1. Dialog appears after initiating find results modification
 * 2. Enter modification instructions in text area
 * 3. Optionally enable auto-apply for changes
 * 4. Click "Modify Code" to proceed or Cancel to abort
 *
 * Features:
 * - Multi-line text input for detailed modification instructions
 * - Auto-apply toggle for batch processing
 * - Input validation with meaningful error messages
 * - Resizable instruction input area
 * - Focus management for immediate typing
 * 
 * Configuration Options:
 * - replacementText: Instructions for how code should be modified
 * - autoApply: Whether to apply changes automatically without confirmation
 *
 * Technical Details:
 * - Extends DialogWrapper for standard IDE dialog behavior
 * - Uses DSL builder pattern for UI construction
 * - Implements validation logic for instruction quality
 * - Returns null if dialog is cancelled
 *
 * Validation Rules:
 * - Instructions cannot be blank
 * - Minimum instruction length of 10 characters required
 * - Returns ValidationInfo for invalid inputs
 *
 * Example usage:
 * ```kotlin
 * val dialog = FindResultsModificationDialog(project, matchCount)
 * val config = dialog.showAndGetConfig()
 * if (config != null) {
 *     // Process modifications with config.replacementText
 *     // Handle auto-apply based on config.autoApply
 * }
 *```
 *
 * @property project The IDE project context
 * @property matchCount Number of matches found (for informational purposes)
 * @see FindResultsModificationAction
 */
```

# generate\CreateFileFromDescriptionAction.kt

```kotlin
/**
 * Creates new files based on natural language descriptions using AI assistance.
 *
 * This action allows users to generate new files by providing natural language descriptions,
 * automatically determining appropriate file paths and content. It integrates with OpenAI's
 * API to interpret requirements and generate appropriate file content.
 *
 * Usage:
 * 1. Select a file/folder in the project view to establish context
 * 2. Invoke the action through the context menu
 * 3. Enter a natural language description of the desired file
 * 4. Review and confirm the generated file
 *
 * Features:
 * - Natural language file generation
 * - Intelligent path determination based on context
 * - Automatic file naming and conflict resolution
 * - Project structure-aware path handling
 * - Support for any file type or language
 *
 * Configuration:
 * - Directive: Natural language description of the file to create
 * - Temperature: Controls AI response creativity (via AppSettingsState)
 * - Model: AI model selection (via AppSettingsState)
 *
 * Technical Details:
 * - Uses OpenAI chat API for content generation
 * - Implements background thread processing (BGT)
 * - Handles relative path resolution for proper file placement
 * - Implements automatic file name conflict resolution
 * - Supports project module awareness
 *
 * Error Handling:
 * - Validates project root and selected file existence
 * - Handles API failures with user feedback
 * - Ensures non-empty file content
 * - Prevents overwriting existing files
 *
 * Example Usage:
 * ```kotlin
 * val settings = Settings(
 *     directive = "Create a new REST controller for user management",
 *     project = currentProject
 * )
 * val action = CreateFileFromDescriptionAction()
 * action.processSelection(state, settings, progressIndicator)
 *```
 *
 * Limitations:
 * - Requires valid project context
 * - Depends on OpenAI API availability
 * - Generated content quality depends on prompt clarity
 *
 * @see FileContextAction
 * @see AppSettingsState
 * @see UITools
 */
class CreateFileFromDescriptionAction : aicoder.actions.FileContextAction<CreateFileFromDescriptionAction.Settings>(false, true) {
    // ... rest of the implementation
}
```

# generate\CreateImageAction.kt

```kotlin
/**
 * Creates AI-generated images based on code context and user instructions.
 * 
 * This action analyzes selected code files and generates technical illustrations using AI image generation.
 * It provides a dialog interface for customizing the image generation process and handles the creation
 * and storage of the resulting images.
 *
 * Usage:
 * 1. Select file(s) or folder in project view
 * 2. Invoke "Create Image" action
 * 3. Enter desired output filename and special instructions
 * 4. Click OK to generate the image
 *
 * Features:
 * - AI-powered technical illustration generation
 * - Support for multiple file analysis
 * - Custom filename and instruction inputs
 * - Automatic file organization and storage
 * - Progress tracking during generation
 *
 * Technical Details:
 * - Uses OpenAI's image generation API via ImageActor
 * - Supports PNG output format
 * - Processes code files recursively
 * - Implements background task handling
 * - Provides real-time progress updates
 *
 * Configuration:
 * - Output filename: Customizable with default timestamp-based naming
 * - Special instructions: Optional text input for specific image requirements
 * - Image model: Configurable through AppSettingsState
 *
 * Prerequisites:
 * - Valid project context
 * - Selected file(s) or folder
 * - Configured OpenAI API access
 *
 * Error Handling:
 * - Validates file paths and permissions
 * - Handles IO exceptions during file operations
 * - Provides user feedback for errors
 * - Implements proper resource cleanup
 *
 * Limitations:
 * - Requires valid file selection
 * - Dependent on OpenAI API availability
 * - Limited to supported image formats
 *
 * @see ImageActor
 * @see AppSettingsState
 * @see IdeaOpenAIClient
 */
class CreateImageAction : BaseAction() {
    // ... rest of the implementation ...
}
```

# generate\GenerateDocumentationAction.kt

```kotlin
/**
 * Generates AI-assisted documentation for project files in markdown format.
 *
 * This action analyzes source code files and generates comprehensive documentation using AI assistance.
 * It supports both single-file and multi-file documentation generation with customizable output formats
 * and locations. The action is designed to help developers maintain up-to-date documentation for their
 * codebase with minimal manual effort.
 *
 * Usage:
 * 1. Select a directory in the project view
 * 2. Right-click and select "Generate Documentation"
 * 3. Configure documentation options in the dialog:
 *    - Select files to process
 *    - Choose output format (single/multiple files)
 *    - Specify AI instructions
 *    - Set output location
 * 4. Click OK to generate documentation
 *
 * Features:
 * - Batch processing of multiple files
 * - Configurable output format (single combined file or individual files)
 * - Custom AI instructions for documentation style
 * - Recent instructions history
 * - Progress tracking during generation
 * - Automatic file opening after generation
 * - Git-aware path handling
 *
 * Configuration Options:
 * - Single Output File: Combines all documentation into one file
 * - Files to Process: Selectable list of project files
 * - AI Instruction: Custom prompt for documentation generation
 * - Output Filename: Name of the generated documentation file
 * - Output Directory: Location for generated documentation
 *
 * Technical Details:
 * - Uses OpenAI API for content generation
 * - Implements parallel processing with ExecutorService
 * - Includes retry logic for API failures (max 3 attempts)
 * - Supports relative path handling for Git repositories
 * - Implements timeout handling (2 minutes per file)
 * - Maintains MRU (Most Recently Used) instruction history
 *
 * Limitations:
 * - Only processes regular files (not directories)
 * - Requires valid file access permissions
 * - Subject to API rate limits and timeouts
 * - Memory constraints for large projects
 *
 * Error Handling:
 * - Validates user input before processing
 * - Implements exponential backoff for retries
 * - Logs errors with appropriate context
 * - Gracefully handles dialog cancellation
 *
 * Example Usage:
 * ```kotlin
 * val settings = UserSettings(
 *     transformationMessage = "Create user documentation",
 *     outputFilename = "docs/api.md",
 *     singleOutputFile = true,
 *     outputDirectory = "docs/"
 * )
 * val config = Settings(settings, project)
 *```
 *
 * @see TestResultAutofixAction
 * @see FileContextAction
 * @see AppSettingsState
 */
```

# generate\GenerateRelatedFileAction.kt

```kotlin
/**
 * Generates related files based on existing source files using AI assistance.
 * 
 * This action analyzes an existing source file and creates a related file (like tests, implementations, 
 * or complementary code files) based on user-provided directives. It uses AI to intelligently generate
 * appropriate content while maintaining project structure and naming conventions.
 *
 * Usage:
 * 1. Select a single source file in the project view or editor
 * 2. Invoke the action via menu or shortcut
 * 3. Enter directive for the type of related file to generate
 * 4. Review and confirm generation settings
 * 5. Generated file opens automatically in editor
 *
 * Features:
 * - AI-powered code generation based on source file analysis
 * - Smart file path handling with automatic conflict resolution
 * - Configurable generation directives
 * - Automatic file opening after generation
 * - Project structure awareness
 * 
 * Configuration Options:
 * - Directive: Text describing the type of file to generate (e.g. "Create test cases")
 * - Uses global temperature setting from AppSettingsState
 * - Uses smart model selection from app settings
 *
 * Technical Details:
 * - Runs in background thread (BGT)
 * - Uses OpenAI chat API for content generation
 * - Maintains relative paths from project root
 * - Implements automatic file numbering for conflict resolution
 * - Handles UTF-8 encoding for file operations
 *
 * Limitations:
 * - Only works with single file selection
 * - Requires valid project context
 * - Depends on AI service availability
 * - Generated files must not exist (auto-numbered if conflict)
 *
 * Error Handling:
 * - Validates file selection and project context
 * - Logs errors during generation process
 * - Creates parent directories as needed
 * - Retries file opening on IDE readiness
 *
 * Example Usage:
 * ```kotlin
 * // Generate test file for source file
 * val settings = Settings(
 *     UserSettings(directive = "Create test cases"),
 *     project
 * )
 * action.processSelection(selectionState, settings, progressIndicator)
 *```
 *
 * @see FileContextAction
 * @see AppSettingsState
 * @see UITools
 */
```

# git\ChatWithCommitAction.kt

```kotlin
/**
 * Enables interactive chat discussions about Git commit changes using AI assistance.
 *
 * This action allows developers to analyze and discuss Git commit changes through an AI-powered chat interface.
 * It extracts the differences between selected files in a commit and presents them in a readable format,
 * supporting both text and binary files.
 *
 * Usage:
 * 1. Select one or more files in the project view
 * 2. Right-click and select "Chat with Commit"
 * 3. A browser window opens with the chat interface showing the commit changes
 * 4. Interact with the AI to discuss the changes
 *
 * Features:
 * - Supports both single and multiple file selection
 * - Handles binary and text files differently
 * - Processes directory selections recursively
 * - Generates readable diff format for text files
 * - Creates specialized summaries for binary files
 * - Integrates with browser-based chat interface
 *
 * Technical Details:
 * - Uses CodeChatSocketManager for chat session management
 * - Implements asynchronous processing via Thread
 * - Integrates with IntelliJ VCS framework
 * - Uses IterativePatchUtil for diff generation
 * - Supports session persistence and management
 * 
 * Configuration:
 * - Uses AppSettingsState for model configuration
 * - Configurable through standard IntelliJ settings
 *
 * Limitations:
 * - Only works with non-Git VCS (enabled when VCS is not Git)
 * - Requires active project context
 * - Binary files only show basic metadata
 *
 * Dependencies:
 * - IdeaChatClient for AI communication
 * - AppServer for web interface
 * - SessionProxyServer for session management
 * - IntelliJ VCS API
 *
 * @see CodeChatSocketManager
 * @see SessionProxyServer
 * @see IdeaChatClient
 * @see AppSettingsState
 */
class ChatWithCommitAction : AnAction() {
    // ... rest of the implementation ...
}
```

# git\ChatWithCommitDiffAction.kt

```kotlin
/**
 * Provides an interactive chat interface for discussing Git commit differences.
 *
 * This action allows developers to analyze and discuss changes between commits through
 * a chat interface powered by AI. It extracts the diff information from selected commits
 * and presents it in a user-friendly chat environment for detailed discussion and analysis.
 *
 * Usage:
 * 1. Select a commit in the Version Control window
 * 2. Right-click and select "Chat with Commit Diff"
 * 3. A browser window will open with the chat interface
 * 4. Discuss the changes with the AI assistant
 *
 * Features:
 * - Interactive chat interface for commit analysis
 * - Supports all file types tracked by VCS
 * - Real-time diff generation and display
 * - Persistent chat sessions
 * - Integration with IDE's VCS system
 *
 * Technical Details:
 * - Uses CodeChatSocketManager for chat communication
 * - Implements async processing for UI responsiveness
 * - Generates unified diff format for changes
 * - Integrates with IntelliJ's VCS framework
 * - Uses AppServer for web interface hosting
 *
 * Prerequisites:
 * - Active VCS (Version Control System) in the project
 * - Selected commit for comparison
 * - Valid project context
 *
 * Limitations:
 * - Requires active internet connection for AI chat
 * - Only works with Git repositories
 * - Limited to comparing with current HEAD
 *
 * Error Handling:
 * - Validates VCS availability
 * - Handles missing commit information
 * - Provides user feedback for errors
 * - Graceful fallback for browser launch issues
 *
 * @see BaseAction
 * @see CodeChatSocketManager
 * @see AppServer
 * @see SessionProxyServer
 */
class ChatWithCommitDiffAction : BaseAction(
    name = "Chat with Commit Diff",
    description = "Opens a chat interface to discuss commit differences"
) {
    // ... rest of the implementation ...
}
```

# git\ChatWithWorkingCopyDiffAction.kt

```kotlin
/**
 * Enables interactive chat discussions about working copy changes in version control.
 * 
 * This action allows developers to review and discuss uncommitted changes in their working copy
 * through an AI-powered chat interface. It generates a diff of the current changes and opens
 * a chat session focused on those changes.
 *
 * Usage:
 * 1. Select a file or files in the project view
 * 2. Invoke the action through the VCS menu or context menu
 * 3. A browser window opens with a chat interface showing the working copy changes
 * 4. Discuss the changes with the AI assistant
 *
 * Features:
 * - Real-time diff generation of working copy changes
 * - Interactive chat interface for code review discussions
 * - Supports all file types tracked by VCS
 * - Displays file path and change type information
 * - Line-by-line diff visualization
 *
 * Technical Details:
 * - Uses ChangeListManager to track working copy changes
 * - Creates simplified diff format showing additions and deletions
 * - Launches chat session in browser using AppServer
 * - Integrates with CodeChatSocketManager for chat functionality
 * - Uses configured AI model from AppSettingsState
 *
 * Limitations:
 * - Requires active VCS integration
 * - Only shows changes for tracked files
 * - Limited to text-based file comparisons
 *
 * Example diff output:
 *```
 * File: src/main/kotlin/Example.kt
 * Type: MODIFICATION
 * - old line
 * + new line
 *```
 *
 * Dependencies:
 * - VCS integration
 * - AppServer
 * - CodeChatSocketManager
 * - IdeaChatClient
 * - Browser for chat interface
 *
 * @see CodeChatSocketManager
 * @see AppServer
 * @see ChangeListManager
 */
class ChatWithWorkingCopyDiffAction : AnAction() {
    // ... rest of the implementation
}
```

# git\ReplicateCommitAction.kt

```kotlin
/**
 * Replicates and adapts Git commit changes to new requirements using AI assistance.
 *
 * This action analyzes selected Git commit changes and helps developers replicate similar changes
 * in other parts of the codebase while adapting them to new requirements. It provides an interactive
 * chat interface to guide the modification process.
 *
 * Usage:
 * 1. Select files/changes in version control view
 * 2. Right-click and select "Replicate Commit"
 * 3. Enter modification requirements in the chat interface
 * 4. Review and apply suggested changes
 *
 * Features:
 * - Interactive chat-based modification workflow
 * - Supports both single and multiple file changes
 * - Handles file additions, deletions, and modifications
 * - Generates contextual diffs with proper formatting
 * - Provides file validation and safety checks
 * - Supports wildcard file selection patterns
 *
 * Technical Details:
 * - Uses AI models for code analysis and patch generation
 * - Implements retry logic for robust operation
 * - Processes changes in background thread
 * - Validates file sizes (max 512KB per file)
 * - Generates unified diff format with context
 *
 * Configuration:
 * - Working directory: Project or selected folder root
 * - Session management: Unique session per operation
 * - AI model: Configurable through AppSettingsState
 *
 * Limitations:
 * - Binary files are identified but not processed
 * - Maximum file size limit of 512KB
 * - Requires valid Git commit changes
 * - Hidden folders (starting with '.') are excluded
 *
 * Error Handling:
 * - Validates project and file selection
 * - Checks for valid working directory
 * - Handles API failures with retry mechanism
 * - Provides user feedback for errors
 *
 * Dependencies:
 * - Git VCS integration
 * - OpenAI API client
 * - Web browser for UI
 * - File system access
 *
 * @see BaseAction
 * @see SessionProxyServer
 * @see AppSettingsState
 * @see IterativePatchUtil
 */
```

# knowledge\CreateProjectorFromQueryIndexAction.kt

```kotlin
/**
 * Creates an interactive TensorFlow Projector visualization from query index data files.
 *
 * This action processes selected .index.data files to generate an interactive visualization
 * that allows exploration and analysis of document embeddings in a web browser. It's useful
 * for analyzing semantic relationships between documents in your codebase.
 *
 * Usage:
 * 1. Select one or more .index.data files or directories containing them in the project view
 * 2. Right-click and select "Create Projector From Query Index"
 * 3. Wait for processing to complete
 * 4. Browser will automatically open with the visualization
 *
 * Features:
 * - Supports single file or batch processing of multiple .index.data files
 * - Automatically processes nested directories
 * - Creates interactive web-based visualization
 * - Integrates with browser for immediate viewing
 * - Maintains session persistence for later access
 *
 * Technical Details:
 * - Runs processing in background thread to maintain UI responsiveness
 * - Uses TensorflowProjector for visualization generation
 * - Integrates with ApplicationServer for web interface
 * - Creates unique session ID for each visualization
 * - Implements progress indication during processing
 *
 * Prerequisites:
 * - Valid .index.data files containing document records
 * - Active project with web server capabilities
 * - Available browser for visualization
 *
 * Error Handling:
 * - Validates file selection before processing
 * - Shows error dialog for invalid selections
 * - Provides error feedback for processing failures
 * - Logs detailed error information for debugging
 *
 * Configuration:
 * - sessionId: Unique identifier for the visualization session
 * - applicationName: Name displayed in the projector interface
 *
 * Limitations:
 * - Only processes .index.data files
 * - Requires valid document records in the index files
 * - Browser access needed for visualization
 *
 * Example usage:
 * ```kotlin
 * val action = CreateProjectorFromQueryIndexAction()
 * action.handle(event) // Processes selected files and opens visualization
 *```
 *
 * @see TensorflowProjector
 * @see ApplicationServer
 * @see DocumentRecord
 */
```

# knowledge\DocumentDataExtractorAction.kt

```kotlin
/**
 * Extracts structured data from documents using AI-powered analysis.
 *
 * This action processes various document types (PDF, text, HTML, markdown) to extract meaningful 
 * structured data using AI models. It's particularly useful for bulk document analysis and 
 * data extraction tasks.
 *
 * Usage:
 * 1. Select file(s) or folder in project view
 * 2. Right-click and select "Extract Document Data"
 * 3. Configure extraction settings in dialog:
 *    - Fast mode option
 *    - Parsing model type
 * 4. View results in web interface
 *
 * Features:
 * - Supports multiple document formats (PDF, TXT, HTML, MD)
 * - Batch processing of multiple files
 * - Configurable parsing models
 * - Interactive web interface for results
 * - Recursive directory processing
 * - Session-based results storage
 *
 * Supported File Types:
 * - PDF documents
 * - Text files (.txt)
 * - HTML/HTM files
 * - Markdown files
 * - Other text-based formats
 *
 * Excluded File Types:
 * - Images (jpg, png, etc.)
 * - Videos (mp4, avi, etc.)
 * - Audio files (mp3, wav, etc.)
 * - Archives (zip, rar, etc.)
 * - Executables
 * - Previously parsed files (.parsed.json)
 *
 * Technical Details:
 * - Uses SkyeNet framework for document parsing
 * - Implements async processing with progress tracking
 * - Stores results in session-based storage
 * - Launches results in system default browser
 * - Background thread support for UI responsiveness
 *
 * Configuration Options:
 * - Fast Mode: Enables quick processing mode
 * - Model Type: Selection of parsing model implementation
 * - Custom settings via DocumentParserApp.Settings
 *
 * Error Handling:
 * - Validates file selection before processing
 * - Shows error dialogs for initialization failures
 * - Logs errors for debugging
 *
 * Dependencies:
 * - SkyeNet document parsing framework
 * - OpenAI API integration
 * - Web browser for results display
 *
 * Limitations:
 * - Requires valid file types
 * - Processing speed depends on AI model response time
 * - Memory usage scales with document size
 *
 * @see DocumentParserApp
 * @see DocumentDataExtractorConfigDialog
 * @see ParsingModel
 */
class DocumentDataExtractorAction : BaseAction(
    name = "Extract Document Data", 
    description = "Extracts structured data from documents using AI"
) {
    // ... rest of the implementation ...
}
```

# knowledge\DocumentDataExtractorConfigDialog.kt

```kotlin
/**
 * Configuration dialog for document data extraction settings.
 * 
 * This dialog allows users to configure various parameters for the document parsing process,
 * including DPI settings, page limits, output formats, and processing options. It provides
 * a user-friendly interface to customize the DocumentParserApp behavior.
 *
 * Usage:
 * 1. Instantiate with project, settings, and model type
 * 2. Show dialog using showAndGet()
 * 3. Access updated settings and model type after dialog closes
 * 
 * Features:
 * - Model type selection from available parsing models
 * - DPI configuration for image processing
 * - Page limit and batch size settings
 * - Output format customization
 * - Multiple output options (images, text, JSON)
 * - Processing mode options (fast mode, line numbers)
 * 
 * Configuration Options:
 * - DPI: Resolution for image processing (positive float value)
 * - Max Pages: Maximum number of pages to process (positive integer)
 * - Output Format: Format specification for parsed output
 * - Pages Per Batch: Number of pages to process in each batch (positive integer)
 * - Show Images: Toggle image display during processing
 * - Save Image Files: Enable/disable saving of extracted images
 * - Save Text Files: Enable/disable saving of extracted text
 * - Save Final JSON: Enable/disable saving final JSON output
 * - Fast Mode: Toggle faster processing mode
 * - Add Line Numbers: Toggle line number addition in output
 *
 * Technical Details:
 * - Extends DialogWrapper for standard IntelliJ dialog behavior
 * - Implements real-time validation for numeric fields
 * - Uses IntelliJ UI DSL for form construction
 * - Maintains default values for critical parameters
 * - Supports validation before settings application
 *
 * Validation Rules:
 * - DPI must be a positive float value
 * - Max Pages must be a positive integer
 * - Pages Per Batch must be a positive integer
 *
 * Default Values:
 * - DPI: 300.0
 * - Max Pages: 100
 * - Pages Per Batch: 10
 *
 * @see DocumentParserApp.Settings
 * @see ParsingModelType
 * @see DialogWrapper
 */
```

# knowledge\SaveAsQueryIndexAction.kt

```kotlin
/**
 * Converts parsed JSON documents into a binary vector index for efficient querying.
 *
 * This action processes .parsed.json files and creates vector embeddings using OpenAI's API,
 * storing them in a binary format optimized for fast similarity searches. It supports both
 * single file and batch directory processing.
 *
 * Usage:
 * 1. Select one or more .parsed.json files or directories containing them
 * 2. Right-click and select "Save As Query Index"
 * 3. The action will process files in parallel and show progress
 * 4. Results are saved in binary format in the same location
 *
 * Features:
 * - Parallel processing with configurable thread count
 * - Progress tracking with cancellation support
 * - Handles both single files and directories
 * - Automatic file filtering for .parsed.json
 * - Background task execution
 *
 * Configuration:
 * - threadCount: Number of concurrent processing threads (default: 8)
 * - batchSize: Number of items processed per batch (default: 100)
 *
 * Technical Details:
 * - Uses OpenAI API for vector embedding generation
 * - Implements background task processing via ProgressManager
 * - Utilizes thread pool for parallel processing
 * - Supports progress monitoring and cancellation
 *
 * Limitations:
 * - Only processes .parsed.json files
 * - Requires valid OpenAI API configuration
 * - Memory usage scales with thread count and batch size
 *
 * Error Handling:
 * - Validates file selection and type
 * - Handles user cancellation gracefully
 * - Reports errors via UI dialogs
 * - Ensures thread pool cleanup
 *
 * @see DocumentRecord
 * @see IdeaOpenAIClient
 * @see BaseAction
 */
class SaveAsQueryIndexAction : BaseAction() {
    // ... rest of the implementation ...
}
```

# markdown\MarkdownImplementActionGroup.kt

```kotlin
/**
 * Action group that provides code implementation options for markdown text selections.
 * Converts natural language descriptions in markdown files into code snippets in various programming languages.
 *
 * Purpose and Overview:
 * - Enables converting text descriptions into actual code implementations
 * - Supports multiple target programming languages
 * - Integrates with markdown editing workflow
 *
 * Usage:
 * 1. Open a markdown file
 * 2. Select text that describes desired code functionality
 * 3. Right-click to access context menu
 * 4. Choose target programming language from the implementation options
 * 5. Generated code is inserted as a markdown code block
 *
 * Features:
 * - Supports 29+ programming languages including:
 *   SQL, Java, C++, Python, JavaScript and more
 * - Automatically formats output as markdown code blocks
 * - Preserves original text while adding implementation
 * - Uses AI to interpret requirements and generate code
 *
 * Technical Details:
 * - Implements ActionGroup for dynamic sub-action generation
 * - Uses ChatProxy for AI-powered code generation
 * - Runs in background thread via ActionUpdateThread.BGT
 * - Integrates with IDE selection and language detection
 *
 * Prerequisites:
 * - Must be used in markdown files
 * - Requires text selection
 * - Needs configured OpenAI API access
 *
 * Limitations:
 * - Only works with markdown file type
 * - Requires valid text selection
 * - Quality depends on clarity of description
 *
 * Error Handling:
 * - Validates file type and selection
 * - Shows error dialog for conversion failures
 * - Logs errors for debugging
 *
 * @see SelectionAction
 * @see ComputerLanguage
 * @see ChatProxy
 */
class MarkdownImplementActionGroup : ActionGroup() {
    // ... existing implementation ...
}

/**
 * Individual action for implementing code in a specific target language.
 * Handles the conversion of selected text to code in the specified language.
 *
 * Features:
 * - Converts natural language to code
 * - Formats output as markdown code block
 * - Preserves original context
 *
 * Technical Details:
 * - Uses ChatProxy for AI code generation
 * - Runs in background thread
 * - Supports configuration via AppSettingsState
 *
 * @property language Target programming language for code generation
 */
open class MarkdownImplementAction(private val language: String) : SelectionAction<String>(true) {
    // ... existing implementation ...
}
```

# markdown\MarkdownListAction.kt

```kotlin
/**
 * Extends markdown lists by intelligently generating additional items using AI assistance.
 * This action analyzes existing list items and generates contextually relevant new items
 * while preserving the original list format and style.
 *
 * Usage:
 * 1. Place cursor within a markdown list (bullet points, checkboxes, or numbered)
 * 2. Invoke action via shortcut or menu
 * 3. Enter desired number of new items to generate
 * 4. AI generates and appends new items maintaining list style
 *
 * Features:
 * - Supports multiple list formats:
 *   - Bullet lists (-, *)
 *   - Checkbox lists (- [ ])
 *   - Numbered lists (1.)
 * - Preserves list indentation and formatting
 * - Context-aware item generation based on existing content
 * - Configurable number of items to generate
 * - Temperature-based generation control
 *
 * Technical Details:
 * - Uses ChatProxy for AI interaction
 * - Implements ListAPI interface for structured response handling
 * - Maintains document write safety through UITools
 * - Supports background thread execution
 * - Includes progress indication during generation
 *
 * Configuration:
 * - itemCount: Number of new items to generate
 * - temperature: Controls AI response randomness (from AppSettingsState)
 *
 * Prerequisites:
 * - Active markdown file
 * - Cursor positioned within a valid markdown list
 * - Project context available
 *
 * Error Handling:
 * - Validates list context before execution
 * - Provides user feedback for failures
 * - Implements error recovery through redoable tasks
 *
 * Example list formats supported:
 * ```markdown
 * - Simple bullet item
 * * Alternate bullet item
 * - [ ] Checkbox item
 * 1. Numbered item
 *```
 *
 * Limitations:
 * - Requires valid markdown list structure
 * - Performance depends on AI response time
 * - Must have write access to document
 *
 * @see BaseAction
 * @see ListAPI
 * @see UITools
 */
```

# plan\AutoPlanChatAction.kt

```kotlin
/**
 * Provides an AI-powered interactive planning and task execution interface for project files.
 *
 * AutoPlanChatAction creates a chat-based interface that helps users plan and execute tasks
 * related to their project files. It combines file analysis, AI assistance, and command execution
 * capabilities to provide an interactive development experience.
 *
 * Usage:
 * 1. Select a file or folder in the project view
 * 2. Invoke the action to open the plan configuration dialog
 * 3. Configure settings including AI models and environment
 * 4. Interact with the chat interface in browser to plan and execute tasks
 *
 * Features:
 * - Interactive chat-based planning interface
 * - Automatic file context analysis
 * - Support for both PowerShell and Bash commands
 * - Configurable AI models for different tasks
 * - Project-aware file processing
 * - Integration with GitHub and Google APIs
 *
 * Configuration Options:
 * - Default Model: Primary AI model for planning
 * - Parsing Model: Secondary AI model for code analysis
 * - Shell Command: Command interpreter (PowerShell/Bash)
 * - Temperature: AI response creativity (0.0-1.0)
 * - Working Directory: Project root location
 * - API Keys: GitHub token and Google API credentials
 *
 * Technical Details:
 * - Maximum file size limit: 512KB per file
 * - Runs in background thread (BGT)
 * - Creates unique session for each chat instance
 * - Supports both single and multi-file context
 * - Implements async initialization and browser launch
 *
 * Limitations:
 * - File size restricted to 512KB
 * - Requires valid project root directory
 * - Browser-based interface required
 * - Dependent on external AI API availability
 *
 * Error Handling:
 * - Validates project root existence
 * - Handles file processing exceptions
 * - Provides user feedback for initialization failures
 * - Manages browser launch failures gracefully
 *
 * Integration Points:
 * - AppServer for web interface
 * - SessionProxyServer for chat management
 * - DataStorage for session persistence
 * - ApplicationServer for UI configuration
 *
 * @see PlanConfigDialog
 * @see AutoPlanChatApp
 * @see AppServer
 * @see SessionProxyServer
 */
```

# plan\PlanAheadAction.kt

```kotlin
/**
 * PlanAheadAction provides an AI-assisted task planning and development interface.
 *
 * This action launches an interactive planning session that helps developers break down
 * and execute complex development tasks. It integrates with the project's file system
 * and provides a web-based interface for task planning and execution.
 *
 * Usage:
 * 1. Select a project folder or file in the IDE
 * 2. Invoke the PlanAhead action
 * 3. Configure planning settings in the dialog:
 *    - AI models for planning and parsing
 *    - Shell command preferences
 *    - Temperature settings
 *    - Working directory
 *    - API tokens and credentials
 * 4. Interact with the web interface for task planning
 *
 * Features:
 * - Interactive web-based planning interface
 * - Configurable AI models for different aspects of planning
 * - Cross-platform shell command support (PowerShell/Bash)
 * - Integration with GitHub and Google APIs
 * - Session-based task management
 * - Project-specific working directory context
 *
 * Configuration:
 * - defaultModel: Primary AI model for planning
 * - parsingModel: Secondary AI model for parsing
 * - shellCmd: Platform-specific shell command
 * - temperature: AI response creativity setting
 * - workingDir: Project working directory
 * - githubToken: GitHub API authentication
 * - googleApiKey: Google API authentication
 * - googleSearchEngineId: Google Custom Search configuration
 *
 * Technical Details:
 * - Runs on background thread (BGT) for UI responsiveness
 * - Creates unique session ID for each planning instance
 * - Manages session storage paths for file system access
 * - Launches local web server for UI interaction
 * - Implements platform-specific shell command selection
 * - Handles browser launch with retry logic
 *
 * Limitations:
 * - Requires valid project context
 * - Depends on external web browser
 * - Needs appropriate API credentials configured
 *
 * @see PlanConfigDialog
 * @see PlanSettings
 * @see AppServer
 * @see SessionProxyServer
 */
class PlanAheadAction : BaseAction() {
    // ... rest of the implementation ...
}
```

# plan\PlanChatAction.kt

```kotlin
/**
 * Provides an interactive planning and command execution interface through a chat-based UI.
 * 
 * This action opens a specialized chat interface that allows users to plan and execute commands
 * in their development environment. It supports both Windows (PowerShell) and Unix (Bash) 
 * environments, providing a unified interface for system operations through natural language.
 * 
 * Usage:
 * 1. Select a folder or file in the project view
 * 2. Invoke the action through the context menu or shortcut
 * 3. Configure settings in the PlanConfigDialog:
 *    - AI models for planning and parsing
 *    - Shell command settings
 *    - Working directory
 *    - Environment variables
 *    - API tokens (GitHub, Google)
 * 4. Interact with the chat interface to plan and execute commands
 * 
 * Features:
 * - Intelligent command planning using AI models
 * - Support for both PowerShell and Bash environments
 * - Configurable AI models for different tasks
 * - Integration with GitHub and Google services
 * - Context-aware command execution
 * - Real-time command output
 * 
 * Technical Details:
 * - Uses separate AI models for planning and parsing
 * - Implements asynchronous command execution
 * - Maintains session-based chat history
 * - Integrates with IDE's command system
 * - Supports custom working directory selection
 * 
 * Prerequisites:
 * - Valid project context
 * - Selected file or folder
 * - Configured API credentials in settings
 * 
 * Configuration Options:
 * - Default AI model for main interactions
 * - Parsing model for command interpretation
 * - Shell command configuration
 * - Temperature setting for AI responses
 * - Working directory path
 * - Environment variables
 * - API tokens for external services
 * 
 * Error Handling:
 * - Validates project context and selection
 * - Provides error feedback through UI
 * - Handles browser opening failures
 * - Manages chat session initialization errors
 * 
 * Limitations:
 * - Requires valid project selection
 * - Depends on external AI service availability
 * - Shell command execution restricted to configured environments
 * 
 * @see PlanConfigDialog
 * @see PlanSettings
 * @see PlanChatApp
 * @see AppServer
 */
```

# plan\PlanConfigDialog.kt

```kotlin
/**
 * Configuration dialog for AI-powered task planning and execution settings.
 *
 * Provides a comprehensive interface for configuring task execution parameters, model selection,
 * and task-specific settings. Supports saving and loading configurations for reuse.
 *
 * Usage:
 * 1. Launch dialog through task planning actions
 * 2. Configure global settings (temperature, auto-fix, blocking)
 * 3. Enable/disable specific tasks and select AI models
 * 4. Save configuration for future use or apply directly
 *
 * Features:
 * - Global Settings Management
 *   - Temperature control for AI response creativity
 *   - Auto-fix toggle for automatic application of changes
 *   - Blocking mode configuration for UI interaction
 *
 * - Task Configuration
 *   - Individual enable/disable toggles for each task type
 *   - AI model selection per task
 *   - Special handling for command auto-fix tasks
 *   - Visual indicators for enabled/disabled state
 *
 * - Configuration Persistence
 *   - Save/load named configurations
 *   - Validation of configuration names
 *   - Overwrite protection for existing configs
 *
 * Technical Details:
 * - UI Components:
 *   - Split panel layout with task list and configuration panels
 *   - Card layout for task-specific settings
 *   - Custom list cell renderer for task visualization
 *   - Dynamic model selection based on available API keys
 *
 * - State Management:
 *   - Maintains PlanSettings object for task configurations
 *   - Handles serialization/deserialization of saved configs
 *   - Validates model selection and configuration names
 *
 * Limitations:
 * - Requires valid API keys for model selection
 * - Configuration names limited to alphanumeric, underscore, and hyphen
 * - Command auto-fix task requires valid executable paths
 *
 * @property project The current IntelliJ project context
 * @property settings The plan settings object to configure
 * @property singleTaskMode Flag to enable single task selection mode
 * 
 * @see PlanSettings
 * @see TaskType
 * @see AppSettingsState
 */
```

# plan\PrePlanAction.kt

```kotlin
/**
 * PrePlanAction enables pre-configured task planning and execution through a JSON-based interface.
 *
 * This action allows users to initialize the PlanAheadApp with predefined task breakdowns and configurations,
 * supporting template-based task definitions with variable substitution. It integrates with the project's
 * build system and environment settings to execute tasks in the appropriate context.
 *
 * Usage:
 * 1. Invoke action from IDE
 * 2. Enter TaskBreakdownWithPrompt JSON in the dialog
 * 3. Fill in any template variables if present ({{variable}} syntax)
 * 4. Configure planning settings in the subsequent dialog
 * 5. View and interact with the plan in browser
 *
 * Features:
 * - JSON-based task definition input
 * - Template variable substitution
 * - Configurable planning settings
 *   - Model selection
 *   - Shell command configuration
 *   - Temperature settings
 *   - Environment variables
 * - Integration with GitHub and Google APIs
 * - Browser-based plan visualization
 *
 * Configuration:
 * - Default Model: Configurable via AppSettingsState
 * - Parsing Model: Configurable via AppSettingsState
 * - Shell Commands: Automatically selected based on OS
 * - Working Directory: Based on selected project context
 * - API Keys: Configurable for GitHub and Google services
 *
 * Technical Details:
 * - Runs on Background Thread (BGT)
 * - Uses SessionProxyServer for chat management
 * - Integrates with AppServer for web interface
 * - Supports both Windows (PowerShell) and Unix (Bash) environments
 * - Implements template variable resolution through dialog UI
 *
 * Limitations:
 * - Requires valid JSON input matching TaskBreakdownWithPrompt schema
 * - Browser access needed for plan visualization
 * - Depends on external API configurations
 *
 * Example JSON input:
 * ```json
 * {
 *   "prompt": "{{taskDescription}}",
 *   "breakdown": {
 *     "steps": [
 *       {"description": "{{step1}}"}
 *     ]
 *   }
 * }
 *```
 *
 * @see PlanAheadApp
 * @see TaskBreakdownWithPrompt
 * @see PlanSettings
 * @see SessionProxyServer
 */
```

# plan\SingleTaskAction.kt

```kotlin
/**
 * SingleTaskAction provides a user interface for executing single AI-assisted tasks with contextual awareness.
 *
 * This action creates an interactive environment for users to perform individual AI-powered tasks
 * with full context of their selected files and project structure. It integrates with the SkyeNet
 * framework to provide a web-based interface for task execution.
 *
 * Usage:
 * 1. Select file(s) or folder in the project view (optional)
 * 2. Invoke the action through IDE menu or shortcut
 * 3. Configure task settings in the dialog:
 *    - AI models for task execution and parsing
 *    - Shell command preferences
 *    - Temperature for AI responses
 *    - Working directory
 *    - API credentials (GitHub, Google)
 * 4. Confirm settings to launch browser interface
 *
 * Features:
 * - Contextual awareness of selected files and project structure
 * - Configurable AI models and parameters
 * - Cross-platform shell command support (PowerShell/Bash)
 * - Web-based task execution interface
 * - File size validation and token counting
 * - Session management and persistence
 *
 * Configuration:
 * - defaultModel: Primary AI model for task execution
 * - parsingModel: Secondary AI model for parsing
 * - shellCmd: Platform-specific shell command
 * - temperature: AI response randomness (0.0-1.0)
 * - workingDir: Task execution directory
 * - API credentials: GitHub token, Google API keys
 *
 * Technical Details:
 * - Runs in background thread (BGT)
 * - Implements async task initialization
 * - Uses session-based data storage
 * - Integrates with AppServer for web interface
 * - Handles file path relativization
 * - Implements file size limits (512KB per file)
 * - Includes token counting for context management
 *
 * Limitations:
 * - Maximum file size: 512KB per file
 * - Requires valid file system access
 * - Browser access needed for task interface
 *
 * Example context data format:
 *```
 * * path/to/file.txt - 1234 bytes, 567 tokens
 *```
 *
 * @see PlanConfigDialog
 * @see SingleTaskApp
 * @see AppServer
 * @see SessionProxyServer
 */
```

# problems\AnalyzeProblemAction.kt

```kotlin
/**
 * Analyzes IDE-detected code problems and suggests fixes using AI assistance.
 * 
 * This action helps developers resolve code issues by providing AI-powered analysis and 
 * suggested fixes for problems identified in the IDE's Problems view. It examines the problem
 * context, related files, and project structure to generate targeted solutions.
 *
 * Usage:
 * 1. Select a problem in the IDE's Problems view
 * 2. Right-click and select "Analyze Problem"
 * 3. A browser window opens with the analysis interface
 * 4. Review suggested fixes and apply them directly from the interface
 *
 * Features:
 * - Contextual code analysis with surrounding lines
 * - Project structure awareness
 * - Multiple fix suggestions per problem
 * - Interactive fix application
 * - Supports all file types recognized by the IDE
 * - Git integration for file management
 * - Markdown-based response formatting
 * - Diff-based code patch suggestions
 *
 * Technical Details:
 * - Uses OpenAI API for analysis via IdeaChatClient
 * - Implements retry logic for API operations
 * - Runs analysis in background thread
 * - Maintains session state for analysis results
 * - Integrates with IDE's PSI system for code analysis
 * - Uses ApplicationServer for UI presentation
 *
 * Problem Analysis Process:
 * 1. Captures problem context including:
 *    - File path and type
 *    - Problem description
 *    - Line and column numbers
 *    - Surrounding code context
 *    - Project structure
 * 2. Analyzes problem using AI to identify:
 *    - Files requiring fixes
 *    - Related files for debugging
 * 3. Generates fix suggestions in diff format
 * 4. Provides interactive UI for reviewing and applying fixes
 *
 * Dependencies:
 * - IntelliJ Platform SDK
 * - OpenAI API client
 * - Skyenet framework for AI interaction
 * - Git integration
 *
 * Limitations:
 * - Requires active internet connection
 * - Analysis quality depends on problem context clarity
 * - May require manual review of suggested fixes
 *
 * @see TestResultAutofixAction
 * @see IdeaChatClient
 * @see ApplicationServer
 */
```

# test\TestResultAutofixAction.kt

```kotlin
/**
 * Automatically analyzes test failures and suggests code fixes using AI assistance.
 * 
 * This action processes failed test results, analyzes the error messages and stack traces,
 * and provides AI-generated suggestions for fixing the failing tests. It examines the project
 * structure and relevant files to provide contextual fixes.
 * 
 * Usage:
 * 1. Run tests in the IDE
 * 2. When a test fails, select the failed test in the test runner
 * 3. Click the "Analyze Test Result" action
 * 4. Review and apply suggested fixes in the opened browser window
 * 
 * Features:
 * - Automatic test failure analysis
 * - AI-powered fix suggestions
 * - Interactive diff-based code updates
 * - Project-wide context awareness
 * - Multi-file fix support
 * - Git integration for file management
 * 
 * Technical Details:
 * - Uses OpenAI API for analysis and fix generation
 * - Implements retry logic for API operations
 * - Supports both single and multiple error analysis
 * - Generates patches in standard diff format
 * - Integrates with IDE's test framework
 * 
 * Configuration:
 * - Uses AppSettingsState for model configuration
 * - Supports custom session management
 * - Configurable through standard IDE action system
 * 
 * Limitations:
 * - Requires valid test results with error messages
 * - File size limit of 0.5MB for analysis
 * - Depends on Git project structure
 * 
 * Implementation Notes:
 * - Processes files recursively excluding .git and ignored files
 * - Creates interactive web UI for fix review
 * - Maintains session state for ongoing fixes
 * - Supports markdown rendering for documentation
 * 
 * Error Handling:
 * - Validates test proxy availability
 * - Checks for valid virtual files
 * - Implements progress indicators for long operations
 * - Provides error feedback through UI
 * 
 * @see BaseAction
 * @see SMTestProxy
 * @see ApplicationServer
 * @see IdeaChatClient
 */
```
