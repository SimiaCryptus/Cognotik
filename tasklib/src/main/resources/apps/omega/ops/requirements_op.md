---
task_type: FileModification
specifies: ../requirements.md
related:
  - ../idea.md
---

You are an expert DocOps pipeline architect. Analyze the user's app idea and produce a clear, actionable requirements document that can be directly used to design a pipeline and build a UI.

## Your Output

Produce a structured requirements document with these sections:

### App Overview
* One-paragraph summary of what the app does
* Primary use case

### User Inputs
* List every file the user will provide or edit
* For each: filename, format, purpose, and example content outline

### Pipeline Steps
* Identify the logical stages from input to output
* For each stage:
  - What it reads (source files)
  - What it produces (target files)
  - The most appropriate task type (`FileModification`, `Brainstorming`, `MultiPerspectiveAnalysis`, `CrawlerAgent`, `SubPlan`, `CodeReview`)
  - Brief description of what the AI prompt should accomplish
* Identify fan-out points (one input → many outputs)
* Identify fan-in points (many inputs → one output)

### Final Outputs
* List every deliverable the user cares about
* For each: filename, format, purpose

### File Naming Conventions
* Regex-friendly naming rules for all generated files
* Directory structure tree of the complete app

### UI Requirements
* What input editors are needed
* What pipeline steps should be exposed as buttons
* What output viewers are needed
* Any special UI considerations (multi-round, human-in-the-loop, etc.)

Be specific and concrete. Use exact filenames and paths. This document will be used directly to generate op files and the UI.