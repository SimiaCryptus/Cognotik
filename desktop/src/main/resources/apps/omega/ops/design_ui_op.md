---
transforms: ../pipeline_design\.md -> ../ui_design.md
related:
  - ../spec.md
  - ../idea.md
  - https://raw.githubusercontent.com/SimiaCryptus/Cognotik/main/src/main/resources/apps/UI.md
task_type: FileModification
---

# Design the App UI

Read the app specification and pipeline design, then produce a detailed UI design document for the generated app.

## Your Task

Design a complete single-page web application UI that:

1. **Follows DocOps conventions:**
   - Tabbed navigation (Input, Pipeline, Results sections at minimum)
   - IIFE-wrapped JavaScript with URL parsing for session/basePath
   - File I/O via fetch (GET/PUT/HEAD)
   - DocOps execution via POST /docops
   - Status polling via docops.status.json
   - Markdown rendering via marked.min.js
   - Session monitoring links via /proxy/#sessionId

2. **Provides input editors** for each user-provided file:
   - Textareas for markdown/text input
   - JSON editors with validation for structured data
   - File upload support if needed
   - Auto-save before pipeline execution

3. **Shows pipeline steps** with:
   - Visual pipeline diagram showing data flow
   - Individual run buttons per step (with data-op, data-output, data-badge, data-viewer attributes)
   - View buttons to inspect intermediate outputs
   - Status badges (pending/running/done/error)
   - Session monitoring links for running tasks
   - "Run Entire Pipeline" batch execution button
   - Batch execution log

4. **Displays results** with:
   - Tabbed results view for different output types
   - Markdown rendering for .md outputs
   - Code display with syntax highlighting for code outputs
   - File browser for directory outputs
   - Copy-to-clipboard for code/text outputs
   - Refresh buttons

5. **Uses a dark theme** with:
   - CSS custom properties for colors
   - Responsive layout
   - Consistent card-based design
   - Animated badges and status indicators

## Output Format

Write `ui_design.md` with these sections:

```
# UI Design: {App Name}

## Layout Structure
{Description of the overall page layout}

## Navigation Tabs
{List of tabs with their purposes}

## Input Section
{For each input: editor type, placeholder text, save behavior, validation}

## Pipeline Section
### Pipeline Diagram
{ASCII art of the visual pipeline diagram}

### Step Cards
{For each step: title, description, button configuration, viewer setup}

### Batch Execution
{Description of the "Run All" behavior, step sequence, error handling}

## Results Section
### Result Tabs
{For each result tab: content type, rendering method, refresh behavior}

## CSS Design Tokens
{Color palette, spacing, typography}

## JavaScript Architecture
{Key functions, event handlers, state management approach}

## Interaction Flows
{Step-by-step user journeys through the app}
```

Be specific enough that a developer (or AI) could implement the complete app from this design alone.