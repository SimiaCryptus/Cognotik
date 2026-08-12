# WriteHtml

**Generate complete, self-contained HTML pages with embedded CSS/JS — and optional AI-generated imagery — in one orchestrated multi-step pipeline.**

`Side-Effect Safe` `Destructive: Writes File` `Multi-Step LLM` `Vision Required (if generate_images)`

---

## Reality Check

**Input configuration:**

```json
{
  "task_type": "WriteHtml",
  "task_description": "Create a landing page for a SaaS analytics product with a hero section, feature grid, pricing table, and footer. Use a dark theme with teal accents.",
  "related_files": ["templates/base-layout.html"],
  "generate_images": true,
  "image_count": 2,
  "task_dependencies": []
}
```

**Rendered output (UI):**

A tabbed panel (`TabbedDisplay`) appears with the following tabs, populated in sequence as the pipeline runs:

- **Overview** — Status header (`Creating HTML File: index.html`), followed by a final Markdown summary listing the file link, image count, and timestamp.
- **HTML Structure** — Rendered `html` code block showing the semantic skeleton (classes, placeholder comments) generated in Step 1.
- **Images** *(only if `generate_images` is true)* — For each image: a status line, an inline generated image preview, a download link, and the AI's generation prompt.
- **JavaScript** — Rendered `javascript` code block from Step 2.
- **CSS** — Rendered `css` code block from Step 3.

After completion, the main task view shows a clickable link: `<a href="...">index.html</a> created`, and a transcript file (`html_generation_index.md`-style) is written to the user's session files containing every prompt/response pair from the pipeline for auditing.

---

## Documentation

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `task_description` | Optional | `String?` | Detailed description of the HTML page to create, including layout, styling, and functionality requirements. |
| `related_files` | Optional | `List<String>?` | Additional files for context (e.g., existing HTML templates, related files). |
| `generate_images` | Optional (default `false`) | `Boolean` | Whether to generate images for the HTML page via an image model. |
| `image_count` | Optional (default `0`) | `Int` | Number of images to generate; coerced into the range 0–10 during validation. |
| `task_dependencies` | Optional | `List<String>?` | IDs of tasks that must complete before this one runs (inherited from `FileTaskExecutionConfig`). |
| `main_file` (via `files`) | Required | `String` | The single output HTML file path; must end in `.html` (validated). |

**Dependencies:** No hard dependency on other `TaskType`s, but `related_files` can point at outputs of prior file-writing tasks (e.g. `WriteHtml` templates or `WriteFile` outputs), and `task_dependencies` allows explicit ordering within the orchestration graph.

**Token Usage:** `High` — the pipeline issues four to five sequential LLM calls (HTML structure, optional image spec + image generation, JavaScript, CSS, image-reference insertion), each carrying the full HTML structure and task description as context.

---

## Config & Process

### Type Configuration (per `TaskType`)
- `name`: `"WriteHtml"`
- `category`: `"Writing"`
- `taskClass` / `executionConfigClass`: `WriteHtmlTask` / `WriteHtmlTaskExecutionConfigData`
- `taskSettingsClass`: `TaskTypeConfig` (no task-specific settings beyond the base)

### Runtime Configuration (per task instance)
- `task_description`, `related_files`, `generate_images`, `image_count`, `task_dependencies`, `state` — all set per invocation via `WriteHtmlTaskExecutionConfigData`.

### Lifecycle

**Initialization**
- `executionConfig.validate()` runs first: confirms the files list isn't empty, the target file ends in `.html`, and clamps `image_count` to `[0, 10]`.
- If the file path is missing or lacks `.html`, execution aborts immediately with a `CONFIGURATION ERROR` result — no LLM calls are made.
- A `TabbedDisplay` and a transcript file stream are opened before any generation begins.

**Execution**
1. **HTML Structure** — Prompts the LLM (`ChatAgent`) to produce a semantic HTML5 skeleton with placeholder comments for CSS/JS, no styling yet.
2. **Image Generation** *(conditional)* — If `generate_images` and `image_count > 0`: prompts for `IMAGE:`/`DESCRIPTION:` pairs, parses them, then invokes `ImageProcessingAgent` per image, writing PNGs to the user session files and an `images/` directory alongside the HTML output.
3. **JavaScript** — Prompts for ES6+ interactivity code based on the generated HTML structure.
4. **CSS** — Prompts for responsive, modern styling based on the same structure.
5. **Image Reference Insertion** *(conditional)* — A follow-up prompt asks the LLM to splice `<img>` tags into the HTML structure at semantically appropriate spots.
6. **Combine** — `combineHtmlComponents` merges CSS into `<style>` (before `</head>`) and JS into `<script>` (before `</body>`), plus an HTML comment listing generated images.
7. **Write** — The combined HTML is written directly to `root.resolve(htmlFile)`; parent directories are created if missing.

**Error Handling**
- Missing/invalid HTML structure extraction (`extractCodeFromResponse` returns empty) halts the run early with an `ERROR` result and closes the transcript writer.
- Empty combined HTML (missing `</head>`/`</body>` tags) is logged and aborts with an error before any file write occurs — the write step is guarded, so partial/invalid HTML is never persisted.
- Per-image generation failures are caught individually (`try/catch` around `ImageProcessingAgent.answer`), logged, and reported in the Images tab as an error state — one failed image does not abort the rest of the pipeline.
- Failed image-reference insertion falls back gracefully to the pre-insertion HTML structure with a warning, rather than failing the whole task.
- On success, `task.safeComplete(...)` finalizes the task UI state.

---

## Integration

### Registering in an `OrchestrationConfig`

```kotlin
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask

val orchestrationConfig = OrchestrationConfig(
    // ... other settings
    taskSettings = mapOf(
        WriteHtmlTask.WriteHtml.name to TaskTypeConfig(enabled = true)
    )
)

// Example task instantiation within a plan:
val htmlTaskConfig = WriteHtmlTask.WriteHtmlTaskExecutionConfigData(
    task_description = "Landing page with hero, features grid, pricing, footer.",
    related_files = listOf("templates/base-layout.html"),
    generate_images = true,
    image_count = 2
)
```

### Prompt Segment (injected into planning LLM)

```text
WriteHtml - Create a complete HTML file with embedded CSS and JavaScript
  ** Specify the HTML file path in the files array (must end with .html)
  ** Provide a detailed description of the page requirements including:
     - Layout and structure
     - Styling requirements (colors, fonts, spacing, etc.)
     - Interactive functionality needed
     - Any specific HTML5 features to use
     - Image requirements (if generate_images is enabled)
  ** The generated HTML will be a complete, self-contained document with:
     - Proper HTML5 structure (<!DOCTYPE html>, <html>, <head>, <body>)
     - Embedded CSS within <style> tags in the <head>
     - Embedded JavaScript within <script> tags (typically before </body>)
     - Responsive design considerations
     - Modern best practices
     - Generated images (if enabled) embedded as base64 or saved as separate files
  ** Related files can include existing HTML templates or reference files
  ** Output will be presented for review before being written to disk
```

### Internal Generation Prompt (Step 1 excerpt, `htmlPrompt`)

```text
You are an expert web developer tasked with creating a complete, self-contained HTML file.

## Requirements:
{task_description}

## Context from Related Files:
{contextFiles}

## Previous Task Results:
{priorCode}

## Instructions:
1. Create a complete HTML5 document structure with proper semantic elements
2. Include appropriate meta tags (viewport, charset, etc.)
3. Add class names to elements that will need styling or JavaScript interaction
...
```