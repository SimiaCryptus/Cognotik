# AI Coder Plugin User Guide

This comprehensive guide covers all available actions in the AI Coder plugin, organized by category to help you maximize
your productivity.

## Table of Contents

- [Editor Actions](#editor-actions)
- [Chat Actions](#chat-actions)
- [Git Integration](#git-integration)
- [Code Generation](#code-generation)
- [Markdown Tools](#markdown-tools)
- [Knowledge Management](#knowledge-management)
- [Development Tools](#development-tools)
- [Problem Solving](#problem-solving)
- [Multi-file Operations](#multi-file-operations)

## Editor Actions

### Smart Paste & Fast Paste

**Location**: Right-click menu > AI Coder > Paste

- **Smart Paste**: Intelligently formats clipboard content to match your code style using a powerful model
- **Fast Paste**: Similar to Smart Paste but uses a faster, simpler model for quicker results
- **Usage**: Copy code from any source, then use Smart/Fast Paste to automatically format it to match your current file

### Describe Action

**Location**: Right-click menu > AI Coder > Describe

- Generates descriptive comments for selected code
- Automatically formats comments according to the language (line or block comments)
- Helps document your code with minimal effort

### Custom Edit Action

**Location**: Right-click menu > AI Coder > Custom Edit

- Allows you to specify custom transformations for selected code
- Enter instructions like "Add error handling", "Optimize for performance", etc.
- Your recent edit instructions are saved for quick reuse

### Recent Code Edits

**Location**: Right-click menu > AI Coder > Recent Edits

- Quick access to your most recently used custom edit instructions
- Numbered for easy keyboard access (e.g., _1, _2, etc.)

### Redo Last

**Location**: Right-click menu > AI Coder > Redo Last

- Repeats the last AI Coder action you performed
- Useful for applying the same transformation to different code sections

## Chat Actions

### Code Chat

**Location**: Right-click menu > AI Coder > Chat > Code Chat

- Opens a chat interface focused on the current file
- Ask questions about the code, request explanations, or get suggestions
- The AI has full context of your code for accurate responses

### Diff Chat

**Location**: Right-click menu > AI Coder > Chat > Diff Chat

- Similar to Code Chat but specializes in suggesting code changes
- Changes are presented as diffs that can be applied directly
- Great for refactoring or implementing new features

### Multi-Code Chat

**Location**: Right-click menu > AI Coder > Chat > Multi-Code Chat

- Chat interface that includes multiple selected files for context
- Useful for discussing interactions between components
- Provides a broader view of your codebase to the AI

### Multi-Diff Chat

**Location**: Right-click menu > AI Coder > Chat > Multi-Diff Chat

- Like Multi-Code Chat but with diff application capabilities
- Allows making coordinated changes across multiple files
- Available with or without line numbers for better context

### Generic Chat

**Location**: Right-click menu > AI Coder > Chat > Generic Chat

- General-purpose chat interface without specific code context
- Useful for brainstorming, planning, or general coding questions

## Git Integration

### Chat With Commit

**Location**: Right-click on a commit > AI Coder > Chat With Commit

- Opens a chat interface focused on a specific commit
- Discuss the changes, ask for explanations, or get suggestions
- Helps understand unfamiliar code changes

### Chat With Commit Diff

**Location**: Right-click on a commit > AI Coder > Chat With Commit Diff

- Similar to Chat With Commit but focuses on the diff between the selected commit and HEAD
- Useful for understanding the impact of specific changes

### Chat With Working Copy Diff

**Location**: Right-click in Git tool window > AI Coder > Chat With Working Copy Diff

- Discusses differences between your working copy and the repository
- Helps review changes before committing

### Replicate Commit

**Location**: Right-click on a commit > AI Coder > Replicate Commit

- Analyzes a commit and applies similar changes to different files
- Useful for implementing consistent patterns across your codebase
- Provides step-by-step explanation of changes

## Code Generation

### Create File From Description

**Location**: Right-click on a folder > AI Coder > Generate > Create File From Description

- Generates a new file based on your description
- Automatically determines appropriate file type and content
- Great for quickly scaffolding new components

### Generate Related File

**Location**: Right-click on a file > AI Coder > Generate > Generate Related File

- Creates a companion file based on the selected file
- Examples: generating a test file for a class, creating a header for an implementation
- Understands the relationship between different file types

### Create Image

**Location**: Right-click on a folder > AI Coder > Generate > Create Image

- Generates images based on your code and instructions
- Useful for creating diagrams, icons, or illustrations
- Saves directly to your project

### Generate Documentation

**Location**: Right-click on a folder > AI Coder > Generate > Generate Documentation

- Creates comprehensive documentation for your code
- Options for single file output or multiple files
- Customizable output format and structure

## Markdown Tools

### Markdown List Action

**Location**: Right-click in a Markdown file > AI Coder > Markdown > Extend List

- Automatically extends bullet points or checkbox lists
- Maintains consistent style and context with existing items
- Specify how many new items to generate

### Markdown Implement

**Location**: Right-click on text in a Markdown file > AI Coder > Markdown > Implement as...

- Converts natural language descriptions into code blocks
- Supports multiple programming languages
- Great for documentation with code examples

## Knowledge Management

### Document Data Extractor

**Location**: Right-click on a document > AI Coder > Knowledge > Extract Document Data

- Extracts structured data from documents (PDFs, text files, etc.)
- Configurable extraction parameters
- Useful for analyzing and processing document content

### Save As Query Index

**Location**: Right-click on parsed files > AI Coder > Knowledge > Save As Query Index

- Creates a searchable index from parsed documents
- Enables efficient querying of document content
- Foundation for advanced knowledge management

### Create Projector From Query Index

**Location**: Right-click on index files > AI Coder > Knowledge > Create Projector

- Visualizes document relationships in an interactive 3D space
- Helps identify clusters and connections in your data
- Powerful tool for exploring large document collections

## Development Tools

### Print Tree Action

**Location**: Right-click in editor > AI Coder > Dev > Print Tree

- Prints the PSI tree structure of the current file
- Useful for understanding how the IDE parses your code
- Helpful for plugin development or debugging

### Apply Patch

**Location**: Right-click in editor > AI Coder > Dev > Apply Patch

- Applies a patch to the current file
- Supports standard diff format
- Useful for manually applying changes from other sources

### Line Filter Chat

**Location**: Right-click in editor > AI Coder > Dev > Line Filter Chat

- Chat interface that references code by line numbers
- Makes it easy to discuss specific parts of a file
- Great for code reviews or teaching

## Problem Solving

### Analyze Problem

**Location**: Right-click on a problem in the Problems view > AI Coder > Analyze Problem

- Analyzes compiler errors, warnings, or other issues
- Suggests fixes with explanations
- Helps understand and resolve complex problems

### Test Result Autofix

**Location**: Right-click on a test result > AI Coder > Autofix Test

- Analyzes failed tests and suggests fixes
- Understands test context and requirements
- Accelerates the test-driven development cycle

### Command Autofix

**Location**: Right-click on a folder > AI Coder > Command Autofix

- Runs commands and automatically fixes issues
- Useful for build errors, linting problems, etc.
- Configurable with multiple commands and retry options

### Validate Code

**Location**: Right-click on files > AI Coder > Validate Code

- Performs syntax checking and validation
- Identifies potential issues before compilation
- Suggests improvements for code quality

## Multi-file Operations

### Mass Patch

**Location**: Right-click on multiple files > AI Coder > Mass Patch

- Applies similar changes to multiple files
- Specify transformation instructions once and apply to all selected files
- Great for codebase-wide refactoring

### Documented Mass Patch

**Location**: Right-click on multiple files > AI Coder > Documented Mass Patch

- Similar to Mass Patch but specifically for aligning code with documentation
- Ensures code and documentation stay in sync
- Useful for maintaining API consistency

### Multi-Step Patch (Auto Dev)

**Location**: Right-click on files > AI Coder > Auto Dev

- Breaks down complex changes into manageable steps
- Creates a detailed plan before implementing changes
- Provides explanations for each modification

### Simple Command

**Location**: Right-click on files > AI Coder > Simple Command

- Executes a simple transformation based on your instructions
- Faster than Multi-Step Patch for straightforward changes
- Good for quick, targeted modifications

## Advanced Features

### Web Development Assistant

**Location**: Right-click on a folder > AI Coder > Web Development Assistant

- Creates complete web applications from descriptions
- Generates HTML, CSS, JavaScript, and image files
- Handles both UI and functionality implementation

### Shell Command

**Location**: Right-click on a folder > AI Coder > Shell Command

- Executes shell commands with AI assistance
- Interprets command output and suggests next steps
- Useful for complex command-line operations

### Outline & Enhanced Outline

**Location**: AI Coder menu > Outline / Enhanced Outline

- Creates structured outlines for documents or ideas
- Progressively expands concepts with multiple AI models
- Useful for planning documents, presentations, or projects

### Unified Plan

**Location**: AI Coder menu > Unified Plan

- Comprehensive planning tool with multiple cognitive modes
- Supports single tasks, plan-ahead mode, auto-plan, and graph-based planning
- Configurable with saved templates for different project types

## Tips for Effective Use

1. **Start with the right context**: Select relevant code or files before invoking actions
2. **Be specific in instructions**: Clear, detailed prompts yield better results
3. **Use the appropriate action**: Different tasks benefit from different specialized actions
4. **Save frequent commands**: Most actions remember your recent instructions for reuse
5. **Combine actions**: Use multiple actions in sequence for complex transformations
6. **Review AI suggestions**: Always verify generated code before applying changes
7. **Provide feedback**: The AI learns from your interactions and improves over time
