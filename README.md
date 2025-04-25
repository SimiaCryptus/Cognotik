# Cognotik: Comprehensive AI-Powered Development Platform

[![Build](https://github.com/SimiaCryptus/Cognotik/workflows/Build/badge.svg)](https://github.com/SimiaCryptus/Cognotik/actions)
[![Version](https://img.shields.io/jetbrains/plugin/v/20724-ai-coding-assistant.svg)](https://plugins.jetbrains.com/plugin/20724-ai-coding-assistant)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/20724-ai-coding-assistant.svg)](https://plugins.jetbrains.com/plugin/20724-ai-coding-assistant)

![logo.svg](logo.svg)

## Overview

Cognotik is a comprehensive AI-powered development platform that combines intelligent planning, code generation, and
interactive interfaces to streamline software development workflows. The platform consists of multiple integrated
components that work together to provide a complete solution for AI-assisted development.

## Core Components

### 1. Cognotik Core (core)

The foundation of the platform, providing essential services and utilities for AI interactions.

**Key Features:**

- Actor system for AI model interactions (SimpleActor, CodingActor, ImageActor)
- Session management and data persistence
- Authentication and authorization
- Code patch generation and application utilities
- Extensible interpreter framework for code execution

### 2. Web UI Framework (webui)

A framework for building interactive web applications with real-time communication.

**Key Features:**

- Chat system with real-time communication
- Session management and state persistence
- Interactive UI components (links, inputs, tasks, file handling)
- Application framework for building web interfaces

### 3. Planning Framework (plan)

AI-assisted task planning and execution framework.

**Key Features:**

- Task types for file modifications, code generation, and web searches
- Multiple cognitive modes (AutoPlanMode, PlanAheadMode)
- Plan coordination with dependency management
- Plan visualization tools

### 4. Desktop Application (desktop)

A standalone desktop application that hosts the Cognotik platform.

**Key Features:**

- System tray integration for easy access
- Background daemon process
- Socket-based communication for remote control
- Cross-platform support (Windows, macOS, Linux)

### 5. Web Application (webapp)

A React-based chat application interface with real-time messaging.

**Key Features:**

- WebSocket connectivity for real-time updates
- Multiple themes with dynamic switching
- Markdown support with syntax highlighting
- Tab system with state persistence

### 6. IntelliJ Plugin (intellij)

An IntelliJ-based plugin that integrates Cognotik capabilities into the IDE.

**Key Features:**

- Smart code operations (paste, transformations)
- Contextual AI chat with code understanding
- Intelligent workflows for multi-step changes
- Test result autofix and problem analysis

### 7. JOpenAI (jo-penai)

A unified, type-safe model registry and API for working with AI models.

**Key Features:**

- Type-safe referencing of models by name or provider
- Centralized pricing and quota logic
- Support for multiple providers (OpenAI, Anthropic, Google, AWS, etc.)
- Unified API for text, chat, embedding, image, and audio models

## Architecture

The Cognotik platform follows a modular architecture:

1. **Core Services Layer**: Provided by the Core module, handling AI interactions, session management, and data
   persistence.

2. **Application Layer**: Consists of the Web UI Framework and Planning Framework, providing the building blocks for
   AI-powered applications.

3. **Client Applications**: Desktop Application, Web Application, and IntelliJ Plugin, offering different interfaces to
   access the platform.

4. **Model Integration Layer**: JOpenAI provides a unified interface to various AI models and providers.

Communication between components:

- RESTful APIs and WebSockets for client-server communication
- Socket-based communication for desktop application components
- File-based storage for session data and generated content

## Getting Started

### Prerequisites

- Java 17 or higher
- Node.js 16+ (for web application)
- Gradle 7.6+ (for building)
- API keys for supported AI providers (OpenAI, Anthropic, etc.)

### Installation Options

#### Desktop Application

1. Download the appropriate package for your platform:
    - Windows: MSI installer
    - macOS: DMG package
    - Linux: DEB package

2. Run the installer and follow the instructions.

3. Launch the application from your desktop or start menu.

#### Web Application

1. Clone the repository
2. Install dependencies:
   ```bash
   cd webapp
   npm install
   ```
3. Start the development server:
   ```bash
   npm start
   ```
4. Open your browser and navigate to `http://localhost:3000`

#### IntelliJ Plugin

1. Install from the JetBrains Marketplace:
    - Open IntelliJ IDEA
    - Go to Settings/Preferences > Plugins
    - Search for "AI Coding Assistant"
    - Click Install and restart the IDE

### Building from Source

1. Clone the repository
2. Build using Gradle:
   ```bash
   ./gradlew build
   ```
3. For specific components:
   ```bash
   # Desktop application
   ./gradlew :desktop:build

   # IntelliJ plugin
   ./gradlew :intellij:buildPlugin

   # Web application
   cd webapp
   npm run build
   ```

## Configuration

The platform can be configured through various mechanisms:

1. **Environment Variables**: Configure API endpoints, credentials, and feature flags.
2. **Configuration Files**: JSON or YAML files for detailed configuration.
3. **UI Settings**: Each application provides UI-based configuration options.

Key configuration options:

- API provider selection and API keys
- Model preferences and parameters
- Storage locations and persistence options
- UI themes and preferences

## Development

### Project Structure

```
cognotik/
├── core/               # Core services and utilities
├── webui/              # Web UI framework
├── plan/               # Planning framework
├── desktop/            # Desktop application
├── webapp/             # Web application (React)
├── intellij/           # IntelliJ plugin
├── jo-penai/           # JOpenAI model registry
└── gradle/             # Gradle configuration
```

### Extension Points

The platform provides several extension points for customization:

1. **Actors**: Create custom actors for specialized AI interactions
2. **Interpreters**: Add support for new programming languages
3. **Storage Providers**: Implement custom storage solutions
4. **Authentication Providers**: Integrate with identity providers
5. **UI Components**: Create custom UI components for the web interface

## Use Cases

### 1. AI-Assisted Development

Use the IntelliJ plugin or web interface to:

- Generate code from natural language descriptions
- Refactor existing code with AI assistance
- Get explanations and documentation for complex code
- Fix bugs and implement features with AI guidance

### 2. Automated Task Planning

Use the planning framework to:

- Break down complex development tasks into manageable steps
- Generate implementation plans with proper dependencies
- Execute tasks with AI assistance
- Visualize and track progress

### 3. Knowledge Management

Use the platform's knowledge tools to:

- Extract structured data from documentation
- Create searchable knowledge indexes
- Generate documentation from code
- Visualize document relationships

## Support and Resources

- **Documentation**: Comprehensive documentation for each component
- **Examples**: Sample projects and use cases
- **Community**: Forums and discussion groups for users and developers
- **Issue Tracking**: GitHub issues for bug reports and feature requests

## License

This project is licensed under the Apache 2.0 License - see the LICENSE file for details.

## Acknowledgments

- OpenAI, Anthropic, and other AI providers for their powerful models
- JetBrains for the IntelliJ platform
- The open-source community for various libraries and tools used in the project
