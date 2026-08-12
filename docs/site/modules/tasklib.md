# Tasklib

*Turn a single high-level goal into a coordinated, dependency-aware set of AI-executed development tasks.*

## Overview

Tasklib is the AI task-planning and execution engine at the heart of Cognotik. Give it a goal — "add
authentication to this service," "refactor this module," "generate a report from these files" — and it breaks the
work down into discrete, typed subtasks, tracks dependencies between them, and drives each one to completion using
the right tool for the job: code generation, file editing, web search, document parsing, or shell execution.

It's designed for real codebases: it reads PDFs, Word docs, and spreadsheets, applies diffs safely with validation
and preview, drives headless browsers when it needs live web content, and reports progress through Cognotik's
interactive web UI in real time.

## Key Features

- **AI-Powered Task Planning** — decomposes complex requests into subtasks with explicit dependency graphs, so
  work executes in the correct order.
- **Multiple Cognitive Modes** — switch planning strategies (e.g. plan-ahead vs. auto-planning) to match the
  task's complexity and risk profile.
- **Rich Task Types** — file modification, code generation, web search, and more, each implemented as a
  specialized, composable unit of work.
- **Safe Diff Application** — changes are generated, previewed, and validated before being applied, reducing the
  risk of broken builds from AI-generated edits.
- **Document & Office File Support** — built-in handling for PDFs (including JBIG2/JPEG2000 images), Word/Excel
  files via Apache POI, and QR/barcode generation and decoding via ZXing.
- **Headless Browser Automation** — Selenium-based web capture (including saving pages to S3) for tasks that need
  live web data.
- **Plan Visualization** — generates visual dependency graphs so you can see how a plan will execute before it
  runs.
- **Real-Time Progress Reporting** — integrates with Cognotik's WebSocket-based UI so users watch tasks execute
  live, not just see a final result.

## Example

A minimal planning request might look like this from the API surface tasklib exposes to the webui layer:

```kotlin
val plan = PlanCoordinator.build {
    goal("Add rate limiting middleware to the API server")
    mode(CognitiveMode.PlanAheadMode)
}

plan.execute { task ->
    println("Running: ${task.name} (depends on: ${task.dependencies})")
}
```

Each task in the resulting plan is one of tasklib's specialized types (e.g. a `FileModificationTask` or
`CodeGenerationTask`), executed in dependency order, with diffs previewed and validated before being written to
disk.

## Integration

Tasklib sits on top of several other Cognotik modules and brings in a curated set of JVM libraries:

- **Cognotik core, lwcore, text, docops** — shared utilities, low-level primitives, and document processing
  building blocks.
- **Cognotik webui** — provides the interactive UI and real-time session/chat layer that surfaces task progress.
- **Selenium + WebDriverManager** — for headless browser-driven tasks.
- **Apache PDFBox / POI** — for parsing PDFs, Word, and Excel documents.
- **ZXing** — for barcode/QR code generation and decoding.
- **Jetty** — embedded server support for local task/session hosting.
- **Jackson + Flexmark** — JSON handling and Markdown rendering for task output.

Because it's JVM-native, tasklib runs anywhere Cognotik runs — no separate Python environment or external
orchestration service required.