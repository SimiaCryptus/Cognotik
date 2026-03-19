---
task_type: FileModification
folder: ../generated_app/
related:
  - ../idea.md
  - ../UI.md
  - ../analysis.md
  - ../generated_app/ops/*.*
  - ../pipeline_design.md
---

Generate a complete, self-contained HTML file that serves as the UI for the DocOps application.

The UI must follow the established DocOps app conventions:

## Architecture Requirements

* **Vanilla HTML, CSS, and JavaScript** — no frameworks, no build tools
* **Session-based**: Extract session ID from the URL path
* **File I/O via REST**: Use `GET` to read files, `PUT` to write files from the session workspace
* **Doc op execution**: Use `POST /docops` to trigger pipeline steps
* **Status polling**: Poll `docops.status.json` to track task progress and update UI badges

## UI Structure

Based on the pipeline design, create appropriate panels:

* **Input panel(s)**: Editable textarea(s) for user input files, with Save buttons
* **Action button(s)**: Buttons to trigger each pipeline stage (or groups of stages)
* **Output panel(s)**: Read-only rendered markdown panels for generated outputs
* **Status indicators**: Badges or icons showing step status (pending, running, complete, failed)
* **Navigation**: If the app has multiple rounds or phases, provide clear navigation

## Implementation Details

* Use `marked.js` (from CDN) for markdown rendering
* Compute `basePath` from `window.location.pathname` to handle session-scoped file paths
* Implement `loadFile(path)`, `saveFile(path, content)`, `runDocOp(opPath)`, and `pollStatus()` utility functions
* Style with clean, modern CSS (inline in the HTML file)
* Use CSS Grid or Flexbox for layout
* Include responsive design for reasonable screen sizes
* Add loading spinners or progress indicators during AI operations
* Handle errors gracefully with user-visible messages

## REST API Reference

| Endpoint | Method | Purpose |
|---|---|---|
| `{basePath}/{file}` | `GET` | Read file |
| `{basePath}/{file}` | `PUT` | Write file |
| `{basePath}/{dir}/_files.json` | `GET` | List directory |
| `/docops` | `POST` | Execute doc op (body: `{"path": "{basePath}/ops/{op_file}.md"}`) |
| `{basePath}/docops.status.json` | `GET` | Poll status |

Produce a complete, working HTML file with all CSS and JavaScript inline.