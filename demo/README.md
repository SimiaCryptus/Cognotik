# AI Coder Demo Test Suite

## Overview

This project contains an automated test suite for demonstrating and validating the AI Coder plugin's functionality in
IntelliJ IDEA. It includes comprehensive UI tests for various features including code generation, documentation,
refactoring, and AI-assisted coding operations.

## Features Tested

### Agent Features

- **Command Autofix** - Automated code issue detection and fixing
- **Documented Mass Patch** - Bulk code modifications based on documentation
- **Outline Tool** - AI-powered content structure generation
- **Shell Command** - Integrated terminal command execution
- **Simple Command** - Basic AI coding operations

### Chat Features

- **Code Chat** - Interactive code discussion and modification
- **Diff Chat** - Code difference analysis and patching
- **Generic Chat** - General purpose AI coding assistant
- **Multi-Code Chat** - Multiple file analysis and modification
- **Multi-Diff Chat** - Multiple file difference handling

### Editor Features

- **Custom Edit** - Specialized code modifications
- **Describe Code** - Automated code documentation
- **Fast Paste** - Intelligent code pasting
- **Smart Paste** - Context-aware code conversion

### Generation Features

- **Create File from Description** - Natural language file generation
- **Create Image** - Code visualization generation
- **Generate Documentation** - Automated documentation creation
- **Generate Related File** - Context-based file generation

## Technical Details

### Prerequisites

- IntelliJ IDEA with AI Coder plugin installed
- Java Development Kit (JDK) 11 or higher
- Chrome WebDriver for Selenium tests
- Network access for AI API calls

### Key Components

- **DemoTestBase** - Base test class with common functionality
- **ScreenRec** - Screen recording capabilities
- **UDPClient** - Network communication handling
- **SplashScreenConfig** - UI configuration for demos

### Dependencies

```groovy
dependencies {
    implementation 'org.seleniumhq.selenium:selenium-java'
    implementation 'io.github.bonigarcia:webdrivermanager'
    implementation 'com.intellij.remoterobot:remote-robot'
    implementation 'org.monte:monte-screen-recorder'
    implementation 'com.simiacryptus:jopenai'
}
```

## Setup and Usage

### Configuration

1. Set up the test environment:
   ```bash
   export OPENAI_API_KEY=your_api_key
   export CHROME_DRIVER_PATH=/usr/bin/chromedriver
   ```

2. Prepare test projects:
   ```bash
   mkdir -p demo_projects/TestProject
   mkdir -p demo_projects/DataGnome
   ```

### Running Tests

Execute individual test classes:

```bash
./gradlew test --tests "com.simiacryptus.aicoder.demotest.action.agent.CommandAutofixActionTest"
```

Run all tests:

```bash
./gradlew test
```

### Recording Demos

Tests include screen recording capabilities:

1. Recordings are saved to `test-recordings/`
2. Each test includes a custom splash screen
3. Voice narration is included (requires OpenAI API access)

## Project Structure

```
src/main/kotlin/com/simiacryptus/aicoder/demotest/
├── action/
│   ├── agent/    # Agent-related test cases
│   ├── chat/     # Chat feature test cases
│   ├── editor/   # Editor feature test cases
│   └── generate/ # Generation feature test cases
├── flow/         # Workflow test cases
├── DemoTestBase.kt
├── ScreenRec.kt
├── SplashScreenConfig.kt
└── UDPClient.kt
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Implement changes with appropriate tests
4. Submit a pull request

## Best Practices

- Add comprehensive documentation for new tests
- Include voice narration for demo clarity
- Maintain consistent styling and naming conventions
- Handle errors gracefully with appropriate logging

## Troubleshooting

- Check Chrome WebDriver compatibility
- Verify network access for AI API calls
- Ensure proper screen recording permissions
- Monitor system resources during test execution
