# Cognotik: Comprehensive AI-Powered Development Platform

[![Build](https://github.com/SimiaCryptus/Cognotik/workflows/Build/badge.svg)](https://github.com/SimiaCryptus/Cognotik/actions)
[![Version](https://img.shields.io/jetbrains/plugin/v/27289-cognotik.svg)](https://plugins.jetbrains.com/plugin/27289-cognotik)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/27289-cognotik.svg)](https://plugins.jetbrains.com/plugin/27289-cognotik)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![logo.svg](logo.svg)

## Overview

**AI that shows its work.**

Cognotik is a comprehensive, **JVM-native** AI-powered development platform that combines intelligent planning, code
generation, document pipelines, and interactive interfaces to streamline software development workflows. Unlike
black-box AI tools, every step of every pipeline is visible, auditable, and stored as readable files — making AI
outputs reproducible, trustworthy, and version-controllable.

Built natively on the JVM in Kotlin, Cognotik runs in your existing Java/Kotlin ecosystem without Python bridges,
conda environments, or cross-language friction. The platform consists of multiple integrated components that work
together to provide a complete solution for AI-assisted development — from IDE integration to standalone web
applications to a full document-operations pipeline.

## 🚀 Quick Start

```bash
# Clone the repository
git clone https://github.com/SimiaCryptus/Cognotik.git
cd Cognotik
# Build the project
./gradlew build
# Run the desktop application
./gradlew :desktop:run
```

For detailed installation instructions, see the [Installation Guide](#installation-options).

## Why Cognotik?

| Feature             | Cognotik                                                                                         | Typical AI Tools                                    |
|:--------------------|:-------------------------------------------------------------------------------------------------|:----------------------------------------------------|
| **Architecture**    | ✅ **JVM-native**: Built in Kotlin, runs natively in Java/Kotlin ecosystems                       | 🔶 Python-first with JVM support as an afterthought |
| **Transparency**    | ✅ **File-based state**: Every input, output, and intermediate step is a readable, auditable file | ❌ Opaque conversation history with no audit trail   |
| **Model Control**   | ✅ **Bring Your Own Key (BYOK)**: Use any compatible LLM, including local or fine-tuned models    | ❌ Locked into a single provider's models            |
| **Data Privacy**    | ✅ **Your infrastructure**: Code and data go directly to the provider you choose, or stay local   | ❌ Data passes through intermediary servers          |
| **Source Code**     | ✅ **Open Source (Apache 2.0)**: Full transparency — inspect, modify, contribute                  | ❌ Closed source black box                           |
| **Reproducibility** | ✅ **Pipeline artifacts in Git**: Re-run, diff, and trace any output to its source                | ❌ Non-deterministic, non-reproducible outputs       |
| **Core Focus**      | ✅ **Agentic pipelines**: Multi-step workflows with explicit user control and oversight           | 🔶 Primarily autocomplete or single-turn chat       |

## Open Source & Bring Your Own Key

Cognotik is 100% open source software, released under the Apache 2.0 license. The platform follows a "Bring Your Own
Key" (BYOK) model, meaning you provide your own API keys for AI services (OpenAI, Anthropic, etc.). This gives you
complete control over:

- Which AI providers and models you use
- Your data privacy and security
- Your usage costs and billing
- Service configuration and customization

## API Keys and Usage

Cognotik uses a "Bring Your Own Key" (BYOK) model for all AI service integrations:

- You must provide your own API keys for services like OpenAI, Anthropic, Google AI, etc.
- All API usage costs are billed directly to your accounts with these providers
- No data is shared with any third parties without your explicit configuration
- The software includes usage tracking tools to help you monitor your API consumption

### Supported AI Providers

- **OpenAI** (GPT-4, GPT-3.5, DALL-E)
- **Anthropic** (Claude 3, Claude 2)
- **Google** (Gemini, PaLM)
- **AWS Bedrock** (Various models)
- **Azure OpenAI** (GPT models)
- **Groq** (Fast inference)
- **Mistral AI** (Mistral models)
- **DeepSeek** (Coding models)
- **Perplexity** (Search-optimized models)
- **Local Models** (Ollama, LM Studio)

## Core Components

### 1. DocOps App Suite (apps)

A collection of AI-powered web applications built on a shared document-operations ("doc ops") platform. Each app
transforms user input through one or more AI pipeline stages to produce rich, structured outputs — all from the browser
with no local build tools required.

**Included Applications:**

| App                                   | Description                                                                                                              |
|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| 🧮 **Philosophical Calculator**       | Multi-perspective analytical toolkit — dialectical analysis, Socratic dialogue, game theory, persuasive essays, and more |
| 📚 **Comic Serial Generator**         | Transforms story ideas into an ongoing comic book series with consistent characters and arcs                             |
| 🧙 **System Wizard**                  | Describe a goal in plain language → generates, runs, and auto-fixes a shell script                                       |
| 🏗️ **Webapp Builder**                | Turns natural language descriptions into complete, runnable web apps                                                     |
| 🔮 **Omega — App Factory**            | Meta-application that designs and produces other DocOps applications from plain language                                 |

See the [DocOps App Suite README](src/main/resources/apps/README.md) for architecture details and the
[Guided Tour](docs/demo/TOUR.md) for a walkthrough of each application.

### 2. Cognotik Core (core)

The foundation of the platform, providing essential services and utilities for AI interactions.

**Key Features:**

- Actor system for AI model interactions (SimpleActor, CodingActor, ImageActor)
- Session management and data persistence
- Authentication and authorization
- Code patch generation and application utilities
- Extensible interpreter framework for code execution
- Token counting and cost estimation
- Rate limiting and retry logic

### 3. Web UI Framework (webui)

A framework for building interactive web applications with real-time communication.

**Key Features:**

- Chat system with real-time communication
- Session management and state persistence
- Interactive UI components (links, inputs, tasks, file handling)
- Application framework for building web interfaces
- Local storage management
- File upload and download capabilities
- Session history and management

### 4. Planning Framework (plan)

AI-assisted task planning and execution framework.

**Key Features:**

- Task types for file modifications, code generation, and web searches
- Plan coordination with dependency management
- Plan visualization tools
- Multiple cognitive modes (AutoPlanMode, PlanAheadMode, GoalOrientedMode, TaskChatMode, GraphOrderedPlanMode)
- Self-healing task execution

### 5. Desktop Application (desktop)

A standalone desktop application that hosts the Cognotik platform.

**Key Features:**

- System tray integration for easy access
- Background daemon process
- Socket-based communication for remote control
- Cross-platform support (Windows, macOS, Linux)
- Auto-update functionality

### 6. Web Application (webapp)

A React-based chat application interface with real-time messaging.

**Key Features:**

- WebSocket connectivity for real-time updates
- Multiple themes with dynamic switching
- Markdown support with syntax highlighting
- Tab system with state persistence
- Event-driven architecture for real-time updates

### 7. IntelliJ Plugin (intellij)

An IntelliJ-based plugin that integrates Cognotik capabilities into the IDE.

**Key Features:**

- Smart code operations (paste, transformations)
- Contextual AI chat with code understanding
- Intelligent workflows for multi-step changes
- Test result autofix and problem analysis
- Code review and documentation generation
- Refactoring suggestions

## Architecture

The Cognotik platform follows a modular architecture:

1. **Core Services Layer**: Provided by the Core module, handling AI interactions, session management, and file-based
   persistence.

2. **Application Layer**: Consists of the Web UI Framework, Planning Framework, and DocOps App Suite, providing the
   building blocks for AI-powered applications and ready-to-use tools.

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
    - **Windows**: [Download MSI installer](https://github.com/SimiaCryptus/Cognotik/releases/latest)
    - **macOS**: [Download DMG package](https://github.com/SimiaCryptus/Cognotik/releases/latest)
    - **Linux**: [Download DEB/RPM package](https://github.com/SimiaCryptus/Cognotik/releases/latest)

2. Run the installer and follow the instructions.

3. Launch the application from your desktop or start menu.
4. Configure your API keys in Settings → API Configuration.

#### IntelliJ Plugin

1. Install from the JetBrains Marketplace:
    - Open IntelliJ IDEA
    - Go to Settings/Preferences > Plugins
    - Search for "Cognotik"
    - Click Install and restart the IDE
2. Configure API keys:
    - Go to Settings/Preferences > Tools > Cognotik
    - Enter your API keys for the providers you want to use

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

#### Web Application

1. Clone the repository
2. Install dependencies:

   ```bash
   cd webapp
   npm install
   ```

3. Configure environment variables:
   ```bash
   cp .env.example .env
   # Edit .env with your API keys
   ```
4. Start the development server:
   ```bash
   npm start
   ```
5. Open your browser and navigate to `http://localhost:3000`

### Project Structure

```
cognotik/
├── core/               # Core services and utilities
├── webui/              # Web UI framework
├── desktop/            # Desktop application
├── webapp/             # Web application (React)
├── intellij/           # IntelliJ plugin
├── gradle/             # Gradle configuration
└── docs/               # Documentation
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

### 4. Code Analysis and Review

Leverage AI for:

- Security vulnerability detection
- Performance optimization suggestions
- Code quality metrics
- Architecture analysis
- Dependency management

### 5. Document Operations (DocOps)

Use the DocOps App Suite to:

- Analyze content through multiple philosophical and rhetorical lenses
- Generate complete web applications from natural language descriptions
- Create serialized comic book series with consistent characters and arcs
- Automate system tasks with self-healing shell scripts
- Design and generate custom AI pipeline applications with Omega
- Build multi-round diagnostic and analytical workflows

## Support and Resources

- **📚 Documentation**: [Full documentation](https://github.com/SimiaCryptus/Cognotik/wiki)
- **🐛 Issue Tracking**: [GitHub Issues](https://github.com/SimiaCryptus/Cognotik/issues)

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details on:

- Code of Conduct
- Development setup
- Submitting pull requests
- Reporting issues
- Feature requests

## License

This project is licensed under the Apache 2.0 License - see the LICENSE file for details. As open source software, you
are free to use, modify, and distribute the code according to the terms of the license.

## Acknowledgments

- OpenAI, Anthropic, and other AI providers for their powerful models
- JetBrains for the IntelliJ platform
- The open-source community for various libraries and tools used in the project
- All contributors who have helped improve Cognotik

## Roadmap

See our [public roadmap](https://github.com/SimiaCryptus/Cognotik/projects) for upcoming features and improvements.

---

<p align="center">
   Made with ❤️ by the Cognotik Team<br/>
   <em>AI that shows its work.</em>
</p>