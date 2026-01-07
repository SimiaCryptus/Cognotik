# Cognotik Framework Documentation

Welcome to the developer documentation for the Cognotik Framework. Cognotik is a sophisticated AI-powered application framework designed for complex task planning, code execution, and orchestration.

This repository contains detailed guides ranging from high-level architectural strategies to specific implementation details for UI servlets and code interpreters.

## 📚 Documentation Index

### 1. Core Architecture & Platform
Understanding the foundation of the system, including session management, storage, and the design philosophy behind the framework's extensibility.

*   **[Platform Architecture](./platform.md)**
    *   Overview of the layered architecture (Service, Interface, Implementation).
    *   Session management (Global vs. User sessions).
    *   Data storage, authentication, and cloud integration.
*   **[Strategic Extensibility](./strategies.md)**
    *   **Must Read:** Explains the "Multiplicative Scaling" philosophy.
    *   Deep dive into the Strategy Patterns used for Models, Providers, and Tasks.
    *   Historical context on interoperability and AI-assisted development.
*   **[User Settings Subsystem](./user_settings.md)**
    *   Managing API credentials and local tool configurations.
    *   Persistence, security masking, and JSON migration logic.
*   **[Generative Use Cases](./use_case_categories.md)**
    *   A taxonomy of generative AI patterns (0→1 Creation, 1→1 Transformation, Synthesis, etc.).

### 2. AI Logic & Cognitive Models
How the AI "thinks," assumes personas, and structures its reasoning.

*   **[Actor Types](./actor_types.md)**
    *   Guide to the base AI workers: `SimpleActor` (Text), `ParsedActor` (Structured Data), `CodingActor` (Code Gen), and `ImageActor`.
*   **[Cognitive Modes](./cognitive_modes.md)**
    *   User guide to the planning engines: Conversational, Waterfall, Adaptive Planning, and Hierarchical Planning.
*   **[Cognitive Schemas](./cognitive_schema.md)**
    *   Defining AI "Mindsets" or personas (e.g., Project Manager, Scientific Method, Agile Developer, Critical Auditor).

### 3. Task Planning System
The core engine where AI logic meets actionable execution.

*   **[Task Planning Overview](./taskplanning.md)**
    *   **Start Here:** Introduction to the Task Planning Framework.
    *   Concepts of Tasks, Orchestration, and how they interact with Cognitive Modes.
*   **[Task Library & Reference](./task_type_docs.md)**
    *   Detailed documentation of available tools: `RunCodeTask`, `FileModificationTask`, `CrawlerAgentTask`, `VectorSearchTask`, and more.
*   **[Launch API & Configuration](./task_planning_launch_api.md)**
    *   How to configure and launch sessions via the Web UI, IntelliJ Plugin, or HTTP API.
    *   Details on `OrchestrationConfig` and model selection.

### 4. Code Execution & Manipulation
The subsystems responsible for running code and applying AI-generated changes to source files.

*   **[Interpreter Subsystem](./interpreter.md)**
    *   Guide to the `Interpreter` interface.
    *   Implementations for Kotlin, Groovy, and Shell execution.
    *   Security and output interception.
*   **[Patch Processors](./patch_processors.md)**
    *   Deep dive into the fuzzy matching algorithms used to apply AI code edits.
    *   Explanation of different processors (CStyle, Python, Markdown, Strict vs. Lenient).

### 5. Web Interface & Server
Documentation for the web-based components of the framework.

*   **[Servlets Guide](./servlets.md)**
    *   Overview of all HTTP endpoints (Auth, Session Management, File Serving, Proxying).
*   **[Server-Driven UI](user_interface.md)**
    *   Guide to the `SessionTask` UI system.
    *   How to build interactive, streaming UIs using Kotlin (Tabs, Buttons, Markdown rendering).
*   **[Application API](./application_api.md)**
    *   Detailed reference for HTTP endpoints (Session management, File system, Usage).
*   **[WebSocket Protocol](./websocket_protocol.md)**
    *   Technical guide to the real-time communication protocol.
    *   Message replay, synchronization, and data transport.

### 6. Developer Standards & Best Practices
Guidelines for extending the framework and maintaining quality.

*   **[Task Implementation Standards](task_type_best_practices.md)**
    *   **Critical for Contributors:** Rules for creating new `TaskType` entities.
    *   Safety protocols (Human-in-the-loop), UI feedback requirements, and configuration standards.
*   **[Product Page Guidelines](./task_product_page.md)**
    *   Design and content standards for creating user-facing "Product Pages" for specific tasks.

---

## 🚀 Recommended Reading Path

1.  **New Users:** Start with **[Task Planning Overview](./taskplanning.md)** and **[Cognitive Modes](./cognitive_modes.md)** to understand what the system does.
2.  **Architects:** Read **[Strategic Extensibility](./strategies.md)** and **[Platform Architecture](./platform.md)** to understand the design philosophy.
3.  **Task Developers:** Read **[Actor Types](./actor_types.md)**, **[Task Library](./task_type_docs.md)**, and **[Task Implementation Standards](task_type_best_practices.md)**.
4.  **UI/Web Developers:** Focus on **[Servlets](./servlets.md)** and **[Server-Driven UI](user_interface.md)**.