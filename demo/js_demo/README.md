# Cognotik AI Task Type Tester

An automated testing framework designed to demonstrate and validate individual task types within the Cognotik AI platform. This tool launches the `/taskChat` application with a single task type enabled, executes a predefined demonstration workflow, and generates a comprehensive report of the results.

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [Test Execution Flow](#test-execution-flow)
- [Adding New Task Types](#adding-new-task-types)
- [Report Formats](#report-formats)
- [Error Handling](#error-handling)
- [Security Considerations](#security-considerations)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgments](#acknowledgments)

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [Test Execution Flow](#test-execution-flow)
- [Adding New Task Types](#adding-new-task-types)
- [Report Formats](#report-formats)
- [Error Handling](#error-handling)
- [Security Considerations](#security-considerations)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgments](#acknowledgments)

## Features

- **Cross-browser Testing**: Support for Chromium, Firefox, and WebKit
- **Performance Metrics**: Capture response times and other performance indicators
- **Screenshot Capture**: Automatic screenshots at key interaction points
- **Artifact Collection**: Gather and organize test artifacts for analysis

## Installation

```bash
# Clone the repository
git clone https://github.com/your-org/cognotik-task-tester.git
cd cognotik-task-tester

# Install dependencies
npm install

# Build the project
npm run build
```

### Prerequisites

- Node.js 14.x or higher
- npm 6.x or higher
- Access to the Cognotik AI platform
- API keys for required providers (OpenAI, Anthropic, etc.)

### Prerequisites

- Node.js 14.x or higher
- npm 6.x or higher
- Access to the Cognotik AI platform
- API keys for required providers (OpenAI, Anthropic, etc.)

## Configuration

### API Configuration

Create a `.env` file in the project root with your API keys:

```
OPENAI_API_KEY=your_openai_api_key
ANTHROPIC_API_KEY=your_anthropic_api_key
# Add other required API keys
```

Alternatively, you can create a JSON configuration file:

```json
{
   "apiKeys": {
      "openai": "your_openai_api_key",
      "anthropic": "your_anthropic_api_key"
   },
   "baseUrl": "http://localhost:3000",
   "defaultTimeout": 120000
}
```

Alternatively, you can create a JSON configuration file:

```json
{
   "apiKeys": {
      "openai": "your_openai_api_key",
      "anthropic": "your_anthropic_api_key"
   },
   "baseUrl": "http://localhost:3000",
   "defaultTimeout": 120000
}
```

### Task Type Configuration

Task types are defined in `src/taskTypes/index.ts`. Each task type has a configuration object that defines:

- Setup instructions (if applicable)
- Display name and description

Example task type configuration:

```json
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

### Browser Configuration

You can configure the browser used for testing:

```typescript
// In your test script
const tester = new CognotikTaskTester({
   browserType: 'chromium', // 'chromium', 'firefox', or 'webkit'
   headless: false, // Set to true for CI environments
   viewport: {width: 1280, height: 720}
});
```

### Browser Configuration

You can configure browser settings in your test configuration:

```typescript
const browserConfig = {
   browserType: 'chromium', // 'chromium', 'firefox', or 'webkit'
   headless: true,          // Run in headless mode
   slowMo: 50,              // Slow down operations by 50ms (useful for debugging)
   viewport: {width: 1280, height: 720},
   recordVideo: true,
   videoDir: './test-videos'
};
```

## Usage

### Command Line Interface

```bash
# Run a test for a specific task type
npm run test-task -- --task-id code_generation

# Run tests for all available task types
npm run test-task -- --all

# Specify output directory for reports
npm run test-task -- --task-id data_analysis --report-dir ./reports

# Run in headless mode (no visible browser)
npm run test-task -- --task-id image_generation --headless

# Enable verbose logging
npm run test-task -- --task-id text_summarization --verbose
# Set maximum test duration
npm run test-task -- --task-id code_generation --timeout 300000
# Specify browser type
npm run test-task -- --task-id image_generation --browser firefox
# Generate specific report formats
npm run test-task -- --task-id data_analysis --formats html,json,pdf
# Set custom timeout
npm run test-task -- --task-id code_generation --timeout 300000
# Specify browser type
npm run test-task -- --task-id image_generation --browser firefox
# Generate specific report formats
npm run test-task -- --task-id data_analysis --formats html,json,pdf
```

### Programmatic Usage

```typescript
import { CognotikTaskTester } from 'cognotik-task-tester';

async function runTests() {
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
   // Save summary report
   await tester.saveSummaryReport(summary, './test-reports/summary.html');
   // Get available task types
   const taskTypes = tester.getAvailableTaskTypes();
   console.log(`Available task types: ${taskTypes.map(t => t.displayName).join(', ')}`);
   // Custom test configuration
   const customReport = await tester.runTaskTest('code_generation', {
      timeout: 180000,
      retries: 2,
      browser: 'firefox'
   });
   // Save summary report
   await tester.saveSummaryReport(summary, './test-reports/summary.html');
   // Get available task types
   const taskTypes = tester.getAvailableTaskTypes();
   console.log(`Available task types: ${taskTypes.map(t => t.displayName).join(', ')}`);
   // Advanced configuration
   const customTester = new CognotikTaskTester({
      baseUrl: 'https://custom-cognotik-instance.com',
      apiConfigPath: './config/api-keys.json',
      reportDir: './custom-reports',
      headless: false,
      verbose: true
   });
}

runTests().catch(console.error);
```

### Using with Jest or Mocha

```typescript
// In your test file
import {CognotikTaskTester} from 'cognotik-task-tester';

describe('Task Type Tests', () => {
   let tester: CognotikTaskTester;
   beforeAll(() => {
      tester = new CognotikTaskTester({
         headless: true,
         apiConfigPath: './config/api-keys.json'
      });
   });
   test('Code Generation task should pass all criteria', async () => {
      const report = await tester.runTaskTest('code_generation');
      expect(report.overallResult).toBe('PASS');
      expect(report.successCriteriaFailed).toHaveLength(0);
   });
   test('Data Analysis task should generate insights', async () => {
      const report = await tester.runTaskTest('data_analysis');
      expect(report.promptResults.some(r =>
          /insight|trend|pattern/i.test(r.response)
      )).toBe(true);
   });
});
```

### Docker Usage

```bash
# Build the Docker image
docker build -t cognotik-task-tester .
# Run tests in Docker
docker run -v $(pwd)/reports:/app/reports \
  -e OPENAI_API_KEY=your_key \
  cognotik-task-tester --task-id code_generation
```

## Test Execution Flow

1. **Initialization Phase**
   - Load test configuration for specified task type
   - Generate unique session ID
   - Create test-specific working directory
   - Verify required API providers are configured
   - Configure session with only the target task type enabled

2. **Launch Phase**
   - Save session configuration to server
   - Launch browser instance with the application URL
   - Wait for application to fully load
   - Verify task type is correctly displayed in UI

3. **Execution Phase**
   - Input each test prompt into the chat interface
   - Wait for response completion
   - Capture the complete response
   - Verify response against expected output patterns
   - Capture screenshots at key interaction points

4. **Result Collection Phase**
   - Capture final application state
   - Collect all generated outputs
   - Measure response times and performance metrics
   - Evaluate success criteria fulfillment
   - Generate test summary
5. **Reporting Phase**
   - Generate detailed test reports in requested formats
   - Save screenshots and artifacts
   - Create summary reports for multiple tests
   - Output console summary for immediate feedback
6. **Cleanup Phase**
   - Close browser sessions
   - Remove temporary files
   - Release system resources
5. **Reporting Phase**
   - Generate detailed test reports in requested formats
   - Save screenshots and other artifacts
   - Create summary reports for multiple tests
   - Archive test results for future reference
6. **Cleanup Phase**
   - Close browser sessions
   - Remove temporary files
   - Release system resources

## Adding New Task Types

1. Create a new task type configuration in `src/taskTypes/index.ts`:

```typescript
export const myNewTaskType: TaskTypeTest = {
  taskId: "my_new_task",
  displayName: "My New Task",
  description: "Description of what this task does",
  requiredApiProviders: ["openai"],
  testPrompts: [
    "First test prompt",
    "Second test prompt"
  ],
  expectedOutputPatterns: [
    /expected pattern 1/i,
    /expected pattern 2/i
  ],
  timeoutSeconds: 120,
  successCriteria: [
    "First success criterion",
    "Second success criterion"
  ]
};
```

2. Register the task type in the task types collection:

```typescript
export const taskTypes: Record<string, TaskTypeTest> = {
  code_generation: codeGenerationTask,
  data_analysis: dataAnalysisTask,
  my_new_task: myNewTaskType  // Add your new task here
};
```

3. If needed, implement custom validation logic in `src/core/ResultCollector.ts`.
4. Add test fixtures if required:

```typescript
// In src/fixtures/myNewTask.ts
export const testFixtures = {
   sampleData: `Your sample data here`,
   expectedResults: [
      "Expected result 1",
      "Expected result 2"
   ]
};
```

5. Create a custom test runner if needed:

```typescript
// In src/examples/myNewTaskTest.ts
import {CognotikTaskTester} from '../index';
import {testFixtures} from '../fixtures/myNewTask';

export async function runMyNewTaskTest() {
   const tester = new CognotikTaskTester();
   // Custom test logic here
   return await tester.runTaskTest('my_new_task');
}
```

4. Create test prompts that effectively demonstrate the task's capabilities:

```typescript
// For complex prompts, you can load from external files
import fs from 'fs';
import path from 'path';

const complexPrompt = fs.readFileSync(
    path.join(__dirname, '../prompts/complex_task_prompt.txt'),
    'utf8'
);
export const complexTaskType: TaskTypeTest = {
   // ... other configuration
   testPrompts: [
      complexPrompt,
      "Simple secondary prompt"
   ]
};
```

5. Add custom validation logic if needed:

```typescript
// In your test runner or custom validator
function validateComplexTaskOutput(output: string): ValidationResult {
   // Custom validation logic
   const containsRequiredElements =
       output.includes('required element 1') &&
       output.includes('required element 2');
   const hasCorrectStructure = /structure pattern/.test(output);
   return {
      isValid: containsRequiredElements && hasCorrectStructure,
      matchedPatterns: [],
      unmatchedPatterns: [],
      details: 'Custom validation for complex task'
   };
}
```

## Report Formats

### HTML Report

Interactive report with expandable sections and embedded screenshots. Useful for detailed analysis and sharing with stakeholders.
Features:

- Collapsible sections for prompts and responses
- Embedded screenshots
- Syntax highlighting for code responses
- Success criteria summary
- Performance metrics visualization
  Features:
- Collapsible sections for prompts and responses
- Embedded screenshots
- Syntax highlighting for code responses
- Success criteria summary
- Performance metrics visualization

### JSON Report

Machine-readable format for CI/CD integration and programmatic processing of test results.
Structure:

```json
{
   "taskId": "code_generation",
   "displayName": "Code Generation",
   "timestamp": "2023-05-15T14:22:33.456Z",
   "duration": 12345,
   "sessionId": "test-1234567890",
   "overallResult": "PASS",
   "promptResults": [
      {
         "prompt": "Create a function that...",
         "response": "Here's a function that...",
         "responseTime": 3456,
         "matchedPatterns": [
            "pattern1",
            "pattern2"
         ],
         "result": "PASS"
      }
   ],
   "successCriteriaMet": [
      "criteria1",
      "criteria2"
   ],
   "successCriteriaFailed": []
}
```

Structure:

```json
{
   "taskId": "code_generation",
   "displayName": "Code Generation",
   "timestamp": "2023-02-15T12:34:56.789Z",
   "duration": 12345,
   "sessionId": "test-session-id",
   "overallResult": "PASS",
   "promptResults": [
      {
         "prompt": "Create a function that...",
         "response": "def example_function()...",
         "responseTime": 3456,
         "matchedPatterns": [
            "pattern1",
            "pattern2"
         ],
         "result": "PASS"
      }
   ],
   "successCriteriaMet": [
      "criteria1",
      "criteria2"
   ],
   "successCriteriaFailed": []
}
```

### PDF Report

Formal documentation for stakeholder review with a professional layout.
Includes:

- Cover page with test summary
- Table of contents
- Detailed test results
- Screenshots and artifacts
- Success criteria evaluation
- Appendix with raw data
  Contents:
- Executive summary
- Test configuration details
- Prompt and response details
- Success criteria evaluation
- Screenshots and visual evidence
- Performance metrics

### Console Summary

Quick overview displayed in the terminal after test execution.
Example output:

```
=====================================
Code Generation Test Report
=====================================
Session ID: test-1234567890
Timestamp: 2023-05-15T14:22:33.456Z
Duration: 12345ms
Overall Result: PASS
Prompt Results:
  1. PASS (3456ms)
     Prompt: Create a function that...
     Matched Patterns: 2
Success Criteria:
  Met: 2
    ✓ Response contains syntactically valid code
    ✓ Code performs the requested function
  Failed: 0
=====================================
```

Example output:

```
=====================================
Code Generation Test Report (code_generation)
=====================================
Session ID: test-1234567890
Timestamp: 2023-02-15T12:34:56.789Z
Duration: 12345ms
Overall Result: PASS
Prompt Results:
  1. PASS (3456ms)
     Prompt: Create a function that...
     Matched Patterns: 2
Success Criteria:
  Met: 3
    ✓ Response contains syntactically valid code
    ✓ Code executes without errors
    ✓ Code performs the requested function
  Failed: 0
=====================================
```

## Error Handling

The tester implements robust error handling strategies:

- **Error categorization** to distinguish between different types of failures:
   - Configuration errors
   - Network errors
   - Timeout errors
   - Validation errors
   - Browser automation errors
   - API errors

Error recovery strategies:

```typescript
// Example error handling in the test runner
try {
   // Test execution code
} catch (error) {
   // Categorize the error
   const errorCategory = categorizeError(error);

   // Apply recovery strategy based on category
   switch (errorCategory) {
      case 'NETWORK_ERROR':
         // Retry with exponential backoff
         await retryWithBackoff(executeTest, 3);
         break;
      case 'VALIDATION_ERROR':
         // Continue with warning
         logger.warn(`Validation error: ${error.message}`);
         break;
      case 'CONFIGURATION_ERROR':
         // Abort test
         logger.error(`Configuration error: ${error.message}`);
         throw error;
      default:
         // Default handling
         logger.error(`Unexpected error: ${error.message}`);
         await captureErrorState();
         throw error;
   }
}
```

## Security Considerations

- No sensitive data is stored in version control
- Secure handling of credentials in CI/CD pipelines
- Isolation of test environments
- Regular security audits of dependencies

Security best practices:

```typescript
// Example of masking sensitive information in logs
function maskSensitiveData(text: string): string {
   return text
       .replace(/api[_-]?key[=:]\s*["']?\w+["']?/gi, 'api_key=***REDACTED***')
       .replace(/password[=:]\s*["']?\w+["']?/gi, 'password=***REDACTED***')
       .replace(/token[=:]\s*["']?\w+["']?/gi, 'token=***REDACTED***');
}

// Log with sensitive data masked
logger.info(maskSensitiveData(responseData));
```

## Development

### Project Structure

```
cognotik-task-tester/
├── src/
│   ├── core/               # Core components
│   │   ├── TestRunner.ts
│   │   ├── SessionConfigurator.ts
│   │   ├── BrowserAutomation.ts
│   │   ├── ResultCollector.ts
│   │   ├── ReportGenerator.ts
│   │   └── ErrorHandler.ts
│   ├── types/              # TypeScript interfaces
│   │   └── index.ts
│   ├── taskTypes/          # Task type definitions
│   │   └── index.ts
│   ├── utils/              # Utility functions
│   │   └── index.ts
│   ├── examples/           # Example implementations
│   │   └── codeGenerationTest.ts
│   ├── cli.ts              # Command line interface
│   └── index.ts            # Main entry point
├── tests/                  # Test cases
│   └── TestRunner.test.ts
├── .github/                # GitHub workflows
│   └── workflows/
│       └── test.yml
├── package.json
├── tsconfig.json
├── jest.config.js
└── README.md
```

### Architecture

The framework follows a modular architecture with clear separation of concerns:

- **TestRunner**: Orchestrates the entire test process
- **SessionConfigurator**: Manages test session configuration
- **BrowserAutomation**: Handles browser interaction using Playwright
- **ResultCollector**: Collects and processes test results
- **ReportGenerator**: Creates reports in various formats
- **ErrorHandler**: Manages error detection and recovery
  This design allows for easy extension and customization of individual components.

### Architecture

The Cognotik AI Task Type Tester follows a modular architecture with clear separation of concerns:

1. **Core Components**:
   - `TestRunner`: Orchestrates the test execution flow
   - `SessionConfigurator`: Manages test session configuration
   - `BrowserAutomation`: Handles browser interaction via Playwright
   - `ResultCollector`: Gathers and processes test results
   - `ReportGenerator`: Creates reports in various formats
   - `ErrorHandler`: Manages error detection and recovery
2. **Task Type Definitions**:
   - Declarative configurations for each task type
   - Defines test prompts, expected patterns, and success criteria
3. **Utilities**:
   - Helper functions for common operations
   - Logging, file handling, session management
4. **CLI Interface**:
   - Command-line interface for running tests
   - Parameter parsing and validation
5. **Programmatic API**:
   - Clean interface for integration with other systems
   - Extensible design for custom implementations

### Running Tests

```bash
# Run unit tests
npm test

# Run linter
npm run lint

# Run type checking
npm run type-check
# Run integration tests
npm run test:integration
# Run with coverage report
npm run test:coverage
# Run integration tests
npm run test:integration
# Run with coverage report
npm run test:coverage
```

### Development Workflow

1. **Setup Development Environment**:
   ```bash
   git clone https://github.com/your-org/cognotik-task-tester.git
   cd cognotik-task-tester
   npm install
   ```
2. **Create Feature Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Implement Changes**:
   - Write code following the project's style guidelines
   - Add tests for new functionality
   - Update documentation as needed
4. **Run Tests Locally**:
   ```bash
   npm test
   npm run lint
   ```
5. **Submit Pull Request**:
   - Push changes to your fork
   - Create a pull request with a clear description
   - Address review feedback

### Code Style Guidelines

- Use TypeScript for type safety
- Follow ESLint configuration
- Write comprehensive JSDoc comments
- Use async/await for asynchronous code
- Write unit tests for all new functionality
- Keep functions small and focused
- Use meaningful variable and function names

