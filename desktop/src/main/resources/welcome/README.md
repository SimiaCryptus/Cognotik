# Cognotik Session Configuration and Launch Protocol Specification

## 1. Overview

This specification details the configuration and launch protocol for Cognotik AI sessions, covering the data structures, storage mechanisms, validation requirements, and the launch sequence flow.

## 2. Session Identification

### 2.1 Session ID Format
- Format: `U-YYYYMMDD-XXXX` where:
  - `U-` is a prefix indicating a user session
  - `YYYYMMDD` is the date in year-month-day format
  - `XXXX` is a random alphanumeric string (4 characters)
- Example: `U-20250315-a7b3`
- Generated via `generateSessionId()` function

### 2.2 Session ID Generation
- Generated when the application initializes
- Regenerated for each new session launch
- For basic chat sessions, a new session ID is generated at launch time

## 3. Configuration Data Structures

### 3.1 Task Settings Object
```javascript
{
  defaultModel: String,         // Model ID (e.g., 'GPT4o')
  parsingModel: String,         // Model ID for parsing (e.g., 'GPT4oMini')
  workingDir: String,           // Directory path (e.g., 'sessions/202503151230')
  autoFix: Boolean,             // Whether to auto-fix errors
  temperature: Number,          // Value between 0-1 (e.g., 0.3)
  maxTaskHistoryChars: Number,  // For auto-plan mode (e.g., 20000)
  maxTasksPerIteration: Number, // For auto-plan mode (e.g., 3)
  maxIterations: Number,        // For auto-plan mode (e.g., 100)
  graphFile: String,            // Path to graph file (for graph mode)
  taskSettings: {               // Enabled tasks configuration
    [taskId]: {
      enabled: Boolean,
      task_type: String         // Same as taskId
    }
  }
}
```

### 3.2 API Settings Object
```javascript
{
  apiKeys: {
    [providerId]: String        // API key for the provider
  },
  apiBase: {
    [providerId]: String        // Base URL or configuration for the provider
  },
  localTools: String[]          // Array of paths to local tools
}
```

### 3.3 Cognitive Mode Types
- `single-task`: Chat mode for individual tasks
- `auto-plan`: Autonomous mode with AI planning
- `plan-ahead`: User-guided planning mode
- `goal-oriented`: Goal-focused iterative planning
- (Deprecated: `graph`: Graph-based planning)

## 4. Storage Mechanisms

### 4.1 Client-Side Storage
- Primary storage: `localStorage`
- Keys:
  - `cognitiveMode`: Selected cognitive mode
  - `defaultModel`: Selected default model
  - `parsingModel`: Selected parsing model
  - `workingDir`: Working directory path
  - `temperature`: Temperature setting
  - `autoFix`: Auto-fix setting
  - `maxTaskHistoryChars`: Auto-plan history limit
  - `maxTasksPerIteration`: Auto-plan tasks per iteration
  - `maxIterations`: Auto-plan max iterations
  - `graphFile`: Graph file path
  - `enabledTasks`: JSON string of task settings
  - `budget`: Budget limit (for basic chat)
  - Legacy keys (for backward compatibility):
    - `basicChatModel`, `basicChatParsingModel`, etc.

### 4.2 Server-Side Storage
- Endpoint: `/taskChat/settings` for session settings
- Endpoint: `/userSettings/` for API keys and tools
- Endpoint: `/chat/settings` for basic chat settings
- Storage format: JSON serialized in POST requests

## 5. Configuration Validation

### 5.1 Required Validations
- At least one API key must be configured
- At least one task must be enabled
- Working directory must be specified
- Default model and parsing model must be selected

### 5.2 Validation Process
1. Check for API keys in `apiSettings.apiKeys`
2. Verify at least one task is enabled in `taskSettings.taskSettings`
3. Validate working directory is not empty
4. Ensure model selections are valid based on available API providers

## 6. Launch Sequence

### 6.1 Pre-Launch Preparation
1. Update all configuration values from UI elements
2. Save task selections to `taskSettings.taskSettings`
3. Generate session summary for review
4. Validate configuration (API keys, enabled tasks)

### 6.2 Launch Process
1. Display loading indicator
2. Save session settings to server via `saveSessionSettingsToServer()`
3. Determine target application path based on cognitive mode:
  - `single-task` → `/taskChat`
  - `auto-plan` → `/autoPlan`
  - `plan-ahead` → `/planAhead`
  - `goal-oriented` → `/goalOriented`
4. Redirect to target application with session ID as hash parameter:
  - Format: `${targetPath}/#${sessionId}`

### 6.3 Basic Chat Launch (Separate Flow)
1. Collect basic chat settings from modal form
2. Save settings to localStorage (both dedicated and shared keys)
3. Generate new session ID
4. Save settings to server via `/chat/settings`
5. Redirect to `/chat/#${sessionId}`

## 7. Model Selection Protocol

### 7.1 Available Models
- Models are organized by provider (OpenAI, Anthropic, etc.)
- Each model has:
  - `id`: Unique identifier (e.g., 'GPT4o')
  - `name`: Display name (e.g., 'GPT-4o')
  - `description`: Brief description

### 7.2 Model Selection Process
1. Retrieve configured API keys from server
2. Filter available models based on configured providers
3. Populate model selection dropdowns
4. Restore previously selected models if available
5. Default to first available model if previous selection unavailable

## 8. Working Directory Management

### 8.1 Directory Format
- Default format: `sessions/YYYYMMDDHHMMSS`
- User-friendly format: `~/Documents/Cognotik/session-YYYYMMDD-HHMMSS`
- Platform-specific paths for Windows, macOS, and Linux

### 8.2 Directory Generation
- Generated on demand via "New Directory" button
- Can be manually specified by user
- Can be passed via URL hash parameter

## 9. Error Handling

### 9.1 Configuration Errors
- Display error notifications for missing API keys or disabled tasks
- Navigate user to appropriate configuration step
- Prevent launch until errors are resolved

### 9.2 Server Communication Errors
- Display error notifications for failed server requests
- Allow client-side fallback when possible
- Log detailed error information to console

## 10. Implementation Requirements

### 10.1 Required Functions
- `generateSessionId()`: Generate unique session identifier
- `saveSessionSettingsToServer()`: Save settings to server
- `validateConfiguration()`: Validate launch requirements
- `populateModelSelections()`: Update model dropdowns
- `saveTaskSelection()`: Update task settings
- `updateLaunchSummaries()`: Generate session summary
- `navigateToStep()`: Handle wizard navigation

### 10.2 Event Handlers
- Model selection changes
- Working directory changes
- Temperature slider adjustments
- Task checkbox toggles
- Cognitive mode radio selection
- Launch button click
- API key changes

## 11. Integration Points

### 11.1 Server Endpoints
- `/taskChat/settings`: Session settings storage
- `/userSettings/`: API key and tool configuration
- `/chat/settings`: Basic chat settings
- `/taskChat`, `/autoPlan`, etc.: Target applications

### 11.2 UI Components
- Wizard navigation
- Settings forms
- Task toggles
- Model selection dropdowns
- Launch summary view
- Loading indicator

This specification provides a comprehensive guide for implementing and maintaining the Cognotik session configuration and launch protocol, ensuring consistent behavior across different cognitive modes and user scenarios.




# Automated Client Integration Tester for Task Type Demonstration

## 1. Overview

This specification details an automated testing framework designed to demonstrate and validate individual task types within the Cognotik AI platform. The tester will launch the `/taskChat` application with a single task type enabled, execute a predefined demonstration workflow, and generate a report of the results.

## 2. System Architecture

### 2.1 Components
- **Test Runner**: Orchestrates the test execution process
- **Session Configurator**: Sets up task-specific session configurations
- **Browser Automation Engine**: Controls browser interactions
- **Result Collector**: Captures and organizes test outputs
- **Report Generator**: Creates structured test reports

### 2.2 Technology Stack
- **Primary Language**: JavaScript/TypeScript
- **Browser Automation**: Playwright or Puppeteer
- **Test Framework**: Jest or Mocha
- **Reporting**: Custom HTML/PDF generator

## 3. Test Configuration

### 3.1 Task Type Definition Object
```typescript
interface TaskTypeTest {
  taskId: string;                // Unique identifier for the task type
  displayName: string;           // Human-readable name
  description: string;           // Brief description of the task
  requiredApiProviders: string[]; // API providers needed (e.g., ['openai'])
  testPrompts: string[];         // Array of prompts to demonstrate the task
  expectedOutputPatterns: RegExp[]; // Patterns to validate in responses
  setupInstructions?: string;    // Special setup instructions
  timeoutSeconds: number;        // Maximum test duration
  successCriteria: string[];     // List of criteria that define success
}
```

### 3.2 Session Configuration
```typescript
interface TestSessionConfig {
  sessionId: string;             // Generated session ID
  cognitiveMode: 'single-task';  // Always single-task for this tester
  defaultModel: string;          // Model to use for testing
  parsingModel: string;          // Parsing model to use
  workingDir: string;            // Test-specific working directory
  temperature: number;           // Temperature setting (typically 0.2 for tests)
  autoFix: boolean;              // Whether to enable auto-fix
  taskSettings: {                // Only the tested task is enabled
    [taskId: string]: {
      enabled: boolean;
      task_type: string;
    }
  }
}
```

## 4. Test Execution Flow

### 4.1 Initialization Phase
1. Load test configuration for specified task type
2. Generate unique session ID using `generateSessionId()`
3. Create test-specific working directory
4. Verify required API providers are configured
5. Configure session with only the target task type enabled

### 4.2 Launch Phase
1. Save session configuration to server via `/taskChat/settings`
2. Launch browser instance with `/taskChat/#${sessionId}` URL
3. Wait for application to fully load
4. Verify task type is correctly displayed in UI

### 4.3 Execution Phase
1. For each test prompt in the task configuration:
   a. Input the prompt into the chat interface
   b. Wait for response completion
   c. Capture the complete response
   d. Verify response against expected output patterns
   e. Capture screenshots at key interaction points
2. Execute any task-specific validation steps
3. Test interaction with task output (e.g., downloading files, viewing visualizations)

### 4.4 Result Collection Phase
1. Capture final application state
2. Collect all generated outputs from working directory
3. Measure response times and performance metrics
4. Evaluate success criteria fulfillment
5. Generate test summary

## 5. Task-Specific Test Configurations

### 5.1 Example: Code Generation Task
```javascript
{
  taskId: "code_generation",
  displayName: "Code Generation",
  description: "Generates code based on user requirements",
  requiredApiProviders: ["openai"],
  testPrompts: [
    "Create a simple Python function that calculates the Fibonacci sequence",
    "Write a React component that displays a countdown timer"
  ],
  expectedOutputPatterns: [
    /def\s+fibonacci/i,
    /function\s+(\w+Component|Countdown)/i
  ],
  timeoutSeconds: 120,
  successCriteria: [
    "Response contains syntactically valid code",
    "Code executes without errors",
    "Code performs the requested function"
  ]
}
```

### 5.2 Example: Data Analysis Task
```javascript
{
  taskId: "data_analysis",
  displayName: "Data Analysis",
  description: "Analyzes data and provides insights",
  requiredApiProviders: ["openai"],
  testPrompts: [
    "Analyze this sample sales data: [CSV data]",
    "What trends can you identify in this time series data: [JSON data]"
  ],
  expectedOutputPatterns: [
    /trend|pattern|correlation|analysis/i,
    /increase|decrease|growth|decline/i
  ],
  setupInstructions: "Place sample data files in the test/fixtures directory",
  timeoutSeconds: 180,
  successCriteria: [
    "Response identifies at least one meaningful insight",
    "Response includes numerical analysis",
    "Response references specific data points"
  ]
}
```

## 6. Reporting

### 6.1 Test Report Structure
```typescript
interface TestReport {
  taskId: string;
  displayName: string;
  timestamp: string;
  duration: number;
  sessionId: string;
  workingDir: string;
  overallResult: 'PASS' | 'FAIL' | 'ERROR';
  promptResults: Array<{
    prompt: string;
    response: string;
    responseTime: number;
    matchedPatterns: string[];
    screenshots: string[];
    result: 'PASS' | 'FAIL';
  }>;
  successCriteriaMet: string[];
  successCriteriaFailed: string[];
  artifacts: string[];
  logs: string[];
  errorDetails?: string;
}
```

### 6.2 Report Formats
- **HTML Report**: Interactive report with expandable sections and embedded screenshots
- **JSON Report**: Machine-readable format for CI/CD integration
- **PDF Report**: Formal documentation for stakeholder review
- **Console Summary**: Quick overview for command-line execution

## 7. Integration Points

### 7.1 CI/CD Integration
- GitHub Actions workflow configuration
- Jenkins pipeline integration
- Automated execution on PR/merge events

### 7.2 Monitoring Integration
- Export test results to monitoring dashboard
- Track performance metrics over time
- Alert on test failures

## 8. Implementation Requirements

### 8.1 Required Functions
- `runTaskTest(taskId: string): Promise<TestReport>`
- `configureTestSession(taskType: TaskTypeTest): TestSessionConfig`
- `launchTaskChat(sessionConfig: TestSessionConfig): Promise<BrowserSession>`
- `executePromptSequence(browser: BrowserSession, prompts: string[]): Promise<PromptResult[]>`
- `validateTaskOutput(output: string, patterns: RegExp[]): ValidationResult`
- `generateTestReport(results: TestResults): Report`

### 8.2 Command Line Interface
```
Usage: cognotik-task-tester [options]

Options:
  --task-id <id>           Task type ID to test
  --all                    Test all available task types
  --report-dir <path>      Directory for test reports
  --headless               Run tests in headless browser mode
  --api-config <path>      Path to API configuration file
  --timeout <seconds>      Override default timeout
  --verbose                Enable verbose logging
```

## 9. Error Handling

### 9.1 Test Failure Categories
- **Configuration Error**: Missing API keys, invalid task type
- **Launch Error**: Failed to start application, session initialization failure
- **Execution Error**: Browser automation failure, timeout
- **Validation Error**: Response doesn't match expected patterns
- **System Error**: Unexpected exceptions, resource limitations

### 9.2 Error Recovery Strategies
- Automatic retry for transient failures (max 3 attempts)
- Graceful degradation for non-critical failures
- Detailed error logging for debugging
- Screenshot capture at point of failure

## 10. Security Considerations

### 10.1 API Key Management
- Use environment variables for API keys
- Support for key rotation
- Mask API keys in logs and reports

### 10.2 Data Handling
- Sanitize test data in reports
- Option to exclude sensitive response content
- Automatic cleanup of test artifacts

## 11. Extensibility

### 11.1 Adding New Task Types
1. Create new task type configuration file
2. Implement any task-specific validation functions
3. Add task-specific test fixtures if needed
4. Register task in the test runner configuration

### 11.2 Custom Validators
- Support for task-specific validation plugins
- Extensible validation framework
- Custom success criteria evaluators

## 12. Example Usage

```javascript
// Example: Running a test for the code_generation task
const tester = new CognotikTaskTester();

// Run a single task test
const report = await tester.runTaskTest('code_generation');
console.log(`Test result: ${report.overallResult}`);

// Generate and save reports
await tester.generateReports(report, {
  formats: ['html', 'json'],
  outputDir: './test-reports'
});

// Run all available task tests
const allReports = await tester.runAllTaskTests();
const summary = tester.generateSummaryReport(allReports);
```
