---
task_type: FileModification
folder: ../generated_app/
related:
  - ../requirements.md
  - ../idea.md
  - ../docs/UI.md
  - ../generated_app/ops/*.md
  - ../generated_app/index.html
  - ../generated_app/README.md
  - ../ui_notes.md
---

Update the existing UI for the generated DocOps application based on user feedback and change requests.

## Context

The related file `UI.md` is the authoritative developer guide for building DocOps app UIs. Follow its conventions exactly.

The `ui_notes.md` file contains user feedback, bug reports, feature requests, and design changes for the UI.

The `ops/*.md` files show the current pipeline — read them to understand what steps exist, what files they produce, and what the user needs to interact with.

The existing `index.html` is the current UI implementation.

## What to Do

* Read the update notes in `ui_notes.md` which contain user feedback, bug reports, feature requests, and design changes for the UI
* Review the original idea in `idea.md` and requirements in `requirements.md` for context on the project's goals
* Review the existing `index.html` to understand the current UI implementation
* Review the current pipeline ops in `ops/*.md` in case new steps were added or existing steps changed
* Apply the requested changes from `ui_notes.md` to the existing UI
* Preserve existing functionality that is not mentioned in the notes
* Ensure all changes maintain consistency with the existing code style and architecture
* If pipeline steps were added or removed, update the Pipeline Tab accordingly:
  - Add/remove cards for new/removed pipeline steps
  - Update the "Run All" sequence
  - Update status polling for new tasks
* If new input or output files were introduced, update the Input/Results tabs accordingly
* Update the README.md if the changes are significant enough to warrant documentation updates

## Architecture Constraints (must be preserved)

* **Vanilla HTML, CSS, and JavaScript** — no frameworks, no build tools, everything inline in one file
* **IIFE-wrapped** JavaScript to avoid global scope pollution
* **Session-based**: Extract `sessionId` and `basePath` from `window.location.pathname`
* **File I/O via REST**: `GET` to read, `PUT` to write, `HEAD` to check existence
* **DocOps execution**: `POST /docops` with `{path: "..."}` body
* **Status polling**: Poll `docops.status.json` for task progress

## REST API Reference

| Endpoint | Method | Purpose |
|---|---|---|
| `{basePath}/{file}` | `GET` | Read file content |
| `{basePath}/{file}` | `PUT` | Write file content |
| `{basePath}/{file}` | `HEAD` | Check file existence |
| `{basePath}/{dir}/_files.json` | `GET` | List directory contents |
| `/docops` | `POST` | Execute op (body: `{"path": "{basePath}/ops/{name}.md"}`) |
| `{basePath}/docops.status.json` | `GET` | Poll task status |

Produce a complete, working HTML file with all CSS and JavaScript inline. It must work correctly when served at a DocOps session URL.