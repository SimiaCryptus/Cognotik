# Cognotik

Cognotik is a comprehensive AI-powered development framework that combines intelligent planning, code generation, and
interactive web interfaces to streamline software development workflows.

## Overview

Cognotik provides a suite of tools and utilities for AI-assisted software development, including:

- **AI-powered Task Planning**: Break down complex tasks into manageable subtasks with proper dependency management
- **Interactive Web UI**: Real-time communication and rich interactive components for AI-assisted development
- **Code Generation and Modification**: Intelligent code creation and modification with contextual understanding
- **Diff Management**: Sophisticated tools for generating, displaying, and applying code changes
- **Knowledge Management**: Index and search project knowledge for contextual assistance

## Core Modules

### Cognotik Utility Package

The `com.simiacryptus.cognotik.util` package provides essential utilities that support various functionalities:

- **TabbedDisplay**: Create and manage tabbed interfaces in the UI
- **Retryable**: Provide retry functionality for operations that might fail
- **Discussable**: Implement interactive discussion interfaces for content feedback
- **MarkdownUtil**: Render Markdown content to HTML with advanced features
- **HtmlSimplifier**: Clean and simplify HTML content
- **Selenium2S3**: Save web content to S3 storage
- **TensorflowProjector**: Generate visualizations of embeddings
- **OpenAPI**: Structured representation of OpenAPI specifications
- **EncryptFiles**: Utilities for encrypting files

### Cognotik Plan Package

The `com.simiacryptus.cognotik.plan` package provides a framework for AI-assisted task planning and execution:

- **Task Types**: Specialized tasks for file modifications, code generation, web searches, and more
- **Cognitive Modes**: Different planning strategies (AutoPlanMode, PlanAheadMode, etc.)
- **PlanCoordinator**: Orchestrates task execution with dependency management
- **Plan Visualization**: Generate visual representations of task dependencies

### Cognotik Web UI Module

The `com.simiacryptus.cognotik.webui` module offers a framework for building interactive web applications:

- **Chat System**: Real-time communication between clients and server
- **Session Management**: Handle user sessions, state persistence, and task management
- **Application Framework**: Structure for building web applications
- **Servlets**: Various servlets for handling HTTP requests
- **Interactive UI Components**: Links, text inputs, tasks, and file handling

### Diff Utilities Package

The `com.simiacryptus.diff` package provides tools for working with differences between text files:

- **DiffUtil**: Generate and format differences between text files
- **ApxPatchUtil**: Apply patches with approximate matching
- **DiffMatchPatch**: Character-level diff operations
- **Interactive UI Components**: Apply diffs through user interfaces
- **Validation and Preview**: Ensure applied diffs result in valid code
