# com.simiacryptus.cognotik.apps

This package contains the core application implementations for the Cognotik web interface. These applications range from
specialized code-fixing tools to general-purpose planning and testing utilities.

## Core Applications

### Patching & Error Correction

* **`PatchApp`**: An abstract base class for "Magic Code Fixer" applications. It provides a robust framework for
  iterative error correction, including:
  * Automatic error parsing from command output.
  * Context-aware code summarization with line numbers and git diffs.
  * Automated research via file searches and symbol analysis.
  * Iterative fix attempts with history tracking to avoid repeating failed patches.
* **`CmdPatchApp`**: A concrete implementation of `PatchApp` that executes shell commands (including PowerShell and
  Batch scripts). It monitors process output in real-time, handles timeouts, and triggers the AI fixing workflow if the
  command fails (non-zero exit code).

### Planning & Task Execution

* **`UnifiedPlanApp`**: A versatile application supporting multiple "Cognitive Modes" (e.g., Chat, Planning). It
  features an advanced query expansion syntax:
  * **Parallel Expansion**: `@[option1|option2]` runs prompts in parallel.
  * **Sequence Expansion**: `@{step1 -> step2}` feeds output from one step into the next.
  * **Range Expansion**: `@(1..10:2)` iterates over numeric ranges.
  * **Topic References**: `@TopicName` expands to previously identified entities.
* **`SingleTaskApp`**: Designed for executing specific, pre-configured tasks without the overhead of a full planning
  phase. It uses a `TaskOrchestrator` to run defined `TaskExecutionConfig` sequences.

### Infrastructure & Utilities

* **`SessionProxyServer`**: Acts as a routing layer for AI Coding Assistant sessions, mapping incoming socket
  connections to specific agents or chat servers.
* **`StressTestApp`**: A diagnostic tool used to verify UI performance and feature support. It generates complex Mermaid
  diagrams, nested tab structures, and high-frequency placeholder updates.

## Services

### `SymbolGraphService`

A sophisticated code analysis service that maintains an in-memory graph representation of the codebase using **Apache
TinkerPop/TinkerGraph**.

* **Vertices**: Represents Files, Symbols, Languages, Libraries, and Packages.
* **Edges**: Tracks relationships such as `DEFINED_IN`, `REFERENCES`, `WRITTEN_IN`, and `IN_PACKAGE`.
* **Features**:
  * Incremental updates based on file modification timestamps.
  * Cross-reference tracking (who calls what).
  * Advanced search capabilities using Gremlin traversals.
  * Persistence support via GraphSON format.

## Key Patterns

* **Tabbed Displays**: Most applications utilize the `TabbedDisplay` utility to organize complex AI outputs, logs, and
  research data.
* **Session Management**: Applications extend `ApplicationServer` and implement `newSession` to provide isolated
  environments for user interactions.
* **Markdown Integration**: Extensive use of `renderMarkdown` for rich UI presentation, including support for Mermaid
  diagrams and interactive "Apply Patch" links.