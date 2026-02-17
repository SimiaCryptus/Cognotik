# com.simiacryptus.cognotik.ui.patch

This package provides a modular system for parsing AI-generated code responses, extracting diffs and new file blocks,
and instrumenting them with interactive apply/revert controls in a web UI context.

## Architecture

The package follows a clean separation of concerns:

```
ResponseParser ──► ResponseSegment (sealed class)
│
▼
DiffInstrumentor ──► DiffApplyController ──► ApplyState (state machine)
│                    │
▼                    ▼
DiffUIRenderer         FileSystem (abstraction)
```

## Core Components

### ResponseParser

Parses raw AI response text into a list of `ResponseSegment` instances. Handles:

- Markdown header detection to resolve filenames (both `#`-style and `File:` decorated headers)
- Code block extraction via `PatchProcessor.extractCodeBlocks()`
- Auto-closing of unclosed code blocks
- Diff vs. new-file classification based on language tag and content heuristics
- Filename resolution with fallback to a default file

### ResponseSegment

A sealed class hierarchy representing parsed response parts:

| Variant        | Description                                      |
|----------------|--------------------------------------------------|
| `Markdown`     | Plain markdown text between code blocks          |
| `NewFileBlock` | A code block representing a new file to create   |
| `DiffBlock`    | A diff/patch block to apply to an existing file  |

### DiffInstrumentor

The main orchestrator that takes a parsed response and produces instrumented HTML/markdown with interactive buttons. Responsibilities:

- Delegates parsing to `ResponseParser`
- Resolves filenames against a project root using configurable resolver functions
- Renders new file blocks with save buttons
- Renders diff blocks with apply/revert buttons and validation warnings
- Supports auto-apply mode via the `shouldAutoApply` predicate
- Records patch metadata via the renderer

### DiffApplyController

A thread-safe state machine managing the lifecycle of a single diff application. Uses `AtomicReference` with CAS loops for lock-free concurrency.

**State transitions:**

```
Pending ──apply()──► Applied ──revert()──► Reverted
│                    │                      │
│                    └──(already applied)    └──apply()──► Applied
│
└──apply() fail──► Failed
```

### ApplyState

Sealed class representing the four possible states:

- **Pending** – Initial state, no action taken
- **Applied** – Diff successfully applied; holds original and new code with timestamp
- **Reverted** – Previously applied diff has been reverted; holds restored code
- **Failed** – An error occurred during apply; holds the exception

### FileSystem

An abstraction over file I/O with two implementations:

| Implementation     | Purpose                                    |
|--------------------|--------------------------------------------|
| `RealFileSystem`   | Production implementation using `java.nio` |
| `InMemoryFileSystem`| Testing implementation using `ConcurrentHashMap` |

### DiffUIRenderer

Interface for rendering UI controls. Decouples the instrumentation logic from any specific web framework.

Methods:
- `renderSaveButton()` – Button to save a new file
- `renderApplyDiffButton()` – Button pair for apply/revert of a diff
- `renderAutoApplied()` – Indicator for auto-applied patches with optional revert
- `renderWarning()` – Warning message for invalid patches
- `recordPatch()` – Persists patch metadata for debugging/auditing

### SocketManagerUIRenderer

Production implementation of `DiffUIRenderer` that integrates with `SocketManager` for real-time web UI updates. Features:

- Creates async tasks for button interactions
- Dynamically swaps button HTML between apply/revert states using `StringBuilder.set()`
- Records patch data as JSON files accessible via session-scoped URLs

## Supporting Types

| Type                   | Description                                          |
|------------------------|------------------------------------------------------|
| `AppliedPatch`         | Data class capturing a completed patch application   |
| `CreatedFile`          | Data class for a newly created file                  |
| `InstrumentationResult`| Aggregate result with rendered markdown and metadata |
| `InstrumentationError` | Error details for failed instrumentations            |

## Utility Functions

### `normalizeFilename`

Iteratively cleans a filename string by removing:
- Common prefixes (`File:`, `Code:`, `Path:`, `Modified:`, etc.)
- Markdown formatting (`**`, `*`, backticks, quotes)
- Leading numbered list markers (`1. `)
- Bare language identifiers mistaken for filenames (e.g., `kotlin`, `java`)

Uses a generic `repeat` helper that applies a transform up to `maxIterations` times or until a fixed point is reached.
