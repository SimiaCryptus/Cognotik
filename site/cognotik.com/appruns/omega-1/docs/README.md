# Cognotik DocOps Developer Documentation
  
  This directory contains the developer guides for building applications on the Cognotik DocOps platform. Each document covers a distinct aspect of the system.
  
  ---
  
  ## Files
  
  ### [MODELS.md](MODELS.md)
  
  **Developer Guide: Model Management & DocProcessor Servlet**
  
  Covers how AI models are discovered, selected, and used throughout the platform. This guide is organized into three sections:
  
  1. **Model Listing** — How models are loaded from the server via the `/apiProviders/` endpoint, transformed into the global `availableModels` dictionary, and organized by provider. Includes the data structure shape, the `loadApiProviders()` function, and the authentication gate.
  
  2. **Model Selection** — How model dropdowns are populated across three UI contexts (Quick Settings, Basic Chat Modal, and Pipeline Wizard). Covers the filtering logic based on configured API keys, the `ModelManager` class, persistence to `localStorage`, and per-task model overrides in pipeline configurations.
  
  3. **DocProcessor Servlet** — How to invoke the `/docops` endpoint for document processing. Documents all required and optional parameters (including `smartModel`, `fastModel`, and `imageModel` overrides), the server-side model resolution process, response formats, error codes, security considerations, and JavaScript usage examples.
  
  Also includes a **Quick Reference** diagram tracing the complete lifecycle of a model ID string from server registration through API response, dropdown population, localStorage persistence, and back to the server as a query parameter.
  
  ---
  
  ### [PIPELINE.md](PIPELINE.md)
  
  **Developer Guide: Writing Logical Pipelines in Cognotik DocOps**
  
  A comprehensive guide to designing and authoring documentation-driven AI processing pipelines. Pipelines are defined as collections of Markdown "op files" with YAML frontmatter that declare file dependencies and transformation rules. The DocProcessor engine resolves these into a directed acyclic graph (DAG) and executes them in topological order.
  
  Key topics include:
  
  - **Core Concepts** — Op files, the execution model (discovery → target resolution → transitive expansion → topological sort → execution), and the file system as pipeline state.
  - **Anatomy of a Pipeline** — A walkthrough of the puppy-finder example app showing directory structure and data flow.
  - **Frontmatter Reference** — Detailed documentation of `transforms` (regex-based source-to-destination mappings with arithmetic backreferences), `related` (supplementary context via globs, literal paths, and URLs), `task_type`, `task_config_json`, `folder`, and `update_mode`.
  - **Designing Your Pipeline** — A step-by-step methodology: identify inputs/outputs, decompose into steps, define naming conventions, write op files, and validate the DAG.
  - **Transform Patterns and Data Flow** — Deep dive into Java regex syntax for transforms, common patterns (one-to-one, fan-out, fan-in, round progression, directory preservation), and how multiple transforms merge.
  - **Task Types** — When and how to use `FileModification`, `Brainstorming`, `MultiPerspectiveAnalysis`, `CrawlerAgent`, `SubPlan`, and `CodeReview`.
  - **Multi-Round and Iterative Pipelines** — Analysis of the health-improvement app's round-based architecture, transitive target discovery, human-in-the-loop checkpoints, and convergence into final reports.
  - **Sub-Plans and Nested Pipelines** — The `SubPlan` task type with configuration via JSON files, demonstrated through the webapp-factory app.
  - **Advanced Patterns** — Human-in-the-loop checkpoints, conditional branching via file existence, accumulating context across rounds, template-driven generation, multi-stage transitive builds, and parallel fan-out with shared context.
  - **Debugging and Troubleshooting** — Common issues (steps not executing, wrong execution order, regex mismatches, incomplete fan-in, transitive discovery failures) with diagnosis steps and fixes.
  - **Best Practices** — Fourteen actionable recommendations covering naming conventions, prompt quality, task type selection, step focus, context richness, resumability, documentation, update mode strategy, regex escaping, incremental testing, consistent relative paths, and arithmetic backreferences.
  
  ---
  
  ### [UI.md](UI.md)
  
  **Cognotik DocOps App Developer Guide**
  
  A complete guide to building the frontend single-page application for a DocOps app. Covers everything needed to create a user-facing interface that collects input, executes AI operations, and displays results.
  
  Key topics include:
  
  - **Architecture** — The overall request flow between the browser app, the File Index API, the DocOps Servlet, and the session filesystem sandbox.
  - **Project Structure** — Required files (`app.html`, `app.js`, `style.css`, `ops/` directory) and the runtime session filesystem layout.
  - **URL Conventions & Session Management** — How to parse the URL to extract `sessionId`, `basePath`, and `appId`, with standard bootstrap code.
  - **File I/O API** — Functions for reading files (`GET`), writing files (`PUT`), checking file existence (`HEAD`), and listing directory contents (`_files.json`).
  - **DocOps Operations** — How to trigger AI operations via `POST /docops`, including parameter documentation and the critical note that operations are asynchronous.
  - **Task Status & Polling** — The `docops.status.json` status file format, task status values (`RUNNING`, `COMPLETED`, `ERROR`, `FAILED`, `PENDING`), polling patterns (`waitForTask`, background polling), and flexible task lookup strategies.
  - **Building the UI** — HTML structure patterns (tabbed navigation, input sections, pipeline steps, status badges, status messages, Markdown rendering, file viewers, results tabs) with complete code examples.
  - **Pipeline Execution Patterns** — Single operation runs, sequential batch execution, parallel fan-out execution, and mixed pipelines combining sequential and fan-out steps.
  - **Live Session Monitoring** — Proxy URL format, displaying session links for running/completed/failed tasks, and integrating monitoring links into the batch execution log.
  - **Complete App Walkthrough** — A step-by-step guide to building a full DocOps app from scratch, including directory structure, HTML shell, complete JavaScript implementation, and operation definition files.
  - **API Reference** — Summary tables for file operations, DocOps execution, status file, and proxy/monitoring endpoints.
  - **Best Practices** — Ten recommendations: always poll for completion, auto-save before running, check existing state on load, provide session monitoring links, handle errors gracefully, use consistent data attributes, wrap in an IIFE, start background polling, validate user input, and design for resumability.
  - **Troubleshooting** — Common issues table with causes and solutions, debugging tips, network tab debugging guidance, and task session ID validation.