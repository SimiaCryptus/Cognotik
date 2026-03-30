---
folder: ../generated_app/
related:
  - ../requirements.md
  - ../idea.md
  - ../docs/UI.md
  - ../docs/MODELS.md
  - ../generated_app/ops/*.md
  - ../generated_app/README.md
---

Generate a complete HTML/JS/CSS project that serves as the UI for the generated DocOps application.

## IMPORTANT

* DO NOT edit the ops files
* ONLY create new files in the current directory (which is implicitly `generated_app/`)
* At the end of the task, generate a brief `README.md` (in the implicit `generated_app/` directory) that explains the purpose of the generated app, summarizes the UI features, and provides instructions for how to use it.

## Context

The related file `UI.md` is the authoritative developer guide for building DocOps app UIs. Follow its conventions exactly.

The `ops/*.md` files show you the actual pipeline that was generated — read them to understand what steps exist, what files they produce, and what the user needs to interact with.

## Architecture Requirements

* **Vanilla HTML, CSS, and JavaScript** — no frameworks, no build tools, everything inline in one file
* **IIFE-wrapped** JavaScript to avoid global scope pollution
* **Session-based**: Extract `sessionId` and `basePath` from `window.location.pathname`
* **File I/O via REST**: `GET` to read, `PUT` to write, `HEAD` to check existence
* **DocOps execution**: `POST /docops` with `{path: "..."}` body
* **Status polling**: Poll `docops.status.json` for task progress

## Required UI Sections

### Input Tab
* Editable textarea(s) for each user input file specified in the requirements
* Save buttons that `PUT` content to the session filesystem
* Placeholder text with instructions

### Pipeline Tab
* A card for each pipeline step with:
  - Title and description
  - "Run" button with `data-op` attribute pointing to the op file path
  - Status badge (pending → running → done / error)
  - "View Output" button to inspect results
  - Session monitoring link (`/proxy/#sessionId`) shown while running
* "Run All" button that executes steps sequentially, updating badges as each completes
* Batch execution log area

### Results Tab
* Rendered markdown viewers for each final output file
* Refresh buttons
* Tabbed sub-navigation if there are multiple outputs

## Implementation Checklist

* [ ] Parse URL: `const parts = window.location.pathname.split('/'); const sessionId = parts[2]; const basePath = parts.slice(0, 4).join('/');`
* [ ] `loadFile(path)` — GET with text response
* [ ] `saveFile(path, content)` — PUT with text body
* [ ] `runOp(opPath)` — POST to `/docops` with JSON body
* [ ] `pollStatus(callback)` — GET `docops.status.json`, parse JSON, invoke callback with status map
* [ ] `waitForTask(taskId, onComplete)` — poll until task reaches terminal state
* [ ] Use `marked.min.js` from CDN for markdown rendering
* [ ] Dark theme with CSS custom properties
* [ ] Responsive layout with CSS Grid or Flexbox
* [ ] Loading spinners during AI operations
* [ ] Graceful error handling with user-visible messages
* [ ] Auto-save input before running pipeline steps
* [ ] Check for existing files on page load to restore state

## REST API Reference

| Endpoint | Method | Purpose |
|---|---|---|
| `{basePath}/{file}` | `GET` | Read file content |
| `{basePath}/{file}` | `PUT` | Write file content |
| `{basePath}/{file}` | `HEAD` | Check file existence |
| `{basePath}/{dir}/_files.json` | `GET` | List directory contents |
| `/docops` | `POST` | Execute op (body: `{"path": "{basePath}/ops/{name}.md"}`) |
| `{basePath}/docops.status.json` | `GET` | Poll task status |
| `/apiProviders/?format=json` | `GET` | Fetch available AI models and providers |
## ⚠️ CRITICAL: Model Selection Is Required for DocOps
**Every call to `/docops` MUST include `smartModel` and `fastModel` query parameters.** These are not optional — the DocProcessor servlet requires them to know which AI models to use for processing.
The generated UI **must**:
1. **Fetch available models** on page load from `/apiProviders/?format=json` (see `MODELS.md` for the exact data flow)
2. **Provide model selection dropdowns** in the UI (at minimum: Smart Model and Fast Model)
3. **Persist selections** to `localStorage` under keys `smartModel`, `fastModel`, and optionally `imageModel`
4. **Include model parameters** in every `/docops` POST request as query parameters: `/docops?smartModel=X&fastModel=Y`
5. **Validate before execution** — if no models are selected, show an error message and prevent the pipeline from running
6. **Block the "Run" and "Run All" buttons** until models have been selected
Example of correct DocOps invocation with models:
```js
const smartModel = localStorage.getItem('smartModel');
const fastModel = localStorage.getItem('fastModel');
if (!smartModel || !fastModel) {
     showError('Please select AI models before running the pipeline.');
     return;
}
const response = await fetch(`/docops?smartModel=${encodeURIComponent(smartModel)}&fastModel=${encodeURIComponent(fastModel)}`, {
     method: 'POST',
     headers: { 'Content-Type': 'application/json' },
     body: JSON.stringify({ path: `${basePath}/ops/${opName}.md` })
});
```
Refer to `MODELS.md` for the complete model listing, selection, and parameter passing guide.

Produce a complete, working HTML file with all CSS and JavaScript inline. It must work correctly when served at a DocOps session URL.