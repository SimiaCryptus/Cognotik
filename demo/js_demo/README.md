# Cognotik AI Task Type Tester

An automated testing framework designed to demonstrate and validate individual task types within the Cognotik AI platform. This tool launches the `/taskChat` application with a single task type enabled, executes a predefined demonstration workflow, and generates a comprehensive report of the results.

## Features

- **Isolated Task Testing**: Test each task type in isolation to validate its functionality
- **Automated Browser Interaction**: Uses Playwright to automate user interactions
- **Comprehensive Reporting**: Generates detailed reports in multiple formats (HTML, JSON, PDF)
- **Configurable Test Scenarios**: Define custom test prompts and validation criteria
- **CI/CD Integration**: Ready for integration with GitHub Actions, Jenkins, etc.
- **Extensible Architecture**: Easily add new task types and validation strategies

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

## Configuration

### API Configuration

Create a `.env` file in the project root with your API keys:

```
OPENAI_API_KEY=your_openai_api_key
ANTHROPIC_API_KEY=your_anthropic_api_key
# Add other required API keys
```

### Task Type Configuration

Task types are defined in `src/taskTypes/index.ts`. Each task type has a configuration object that defines:

- Test prompts to execute
- Expected output patterns
- Success criteria
- Timeout settings
- Required API providers

Example task type configuration:

```typescript
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
}

runTests().catch(console.error);
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

## Report Formats

### HTML Report

Interactive report with expandable sections and embedded screenshots. Useful for detailed analysis and sharing with stakeholders.

### JSON Report

Machine-readable format for CI/CD integration and programmatic processing of test results.

### PDF Report

Formal documentation for stakeholder review with a professional layout.

### Console Summary

Quick overview displayed in the terminal after test execution.

## Error Handling

The tester implements robust error handling strategies:

- **Automatic retry** for transient failures (max 3 attempts)
- **Graceful degradation** for non-critical failures
- **Detailed error logging** for debugging
- **Screenshot capture** at point of failure

## Security Considerations

- API keys are managed through environment variables
- Sensitive data is masked in logs and reports
- Test artifacts are automatically cleaned up

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

### Running Tests

```bash
# Run unit tests
npm test

# Run linter
npm run lint

# Run type checking
npm run type-check
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Cognotik AI platform team
- Playwright documentation and community
- TypeScript team and community