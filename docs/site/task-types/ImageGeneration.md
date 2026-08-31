# GenerateImage

**Create high-quality images from text descriptions using AI image generation models (e.g. DALL·E-class models).**

`Side-Effect Safe` (writes a single new image file) · `Vision Required` · `Interactive Approval Available`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "GenerateImage",
  "task_description": "A minimalist flat-style hero illustration of a rocket launching over a city skyline at dusk, purple and orange gradient sky, clean vector lines, no text.",
  "related_files": [
    "assets/style-reference.png"
  ],
  "main_file": "assets/hero-rocket.png",
  "task_dependencies": [],
  "state": "Pending"
}
```

**Rendered Output (UI)**

A tabbed panel appears with two tabs: **Preview** and **Prompt**.

* **Prompt tab:** shows the raw `task_description` prompt (with any prepended context from related files or prior
  task results) in a fenced code block, followed by the *optimized prompt* actually returned/used by the underlying
  `ImageProcessingAgent`.
* **Preview tab:** shows a header (`Generating Image: assets/hero-rocket.png`), then once generation completes, an
  inline rendered `<img>` preview of the generated bitmap.
  * If `autoFix` is disabled, an **Accept** button footer appears below the preview — clicking it writes the image to
    disk and reports a summary line with a link to the saved file (`Successfully generated and saved image to
    <a>assets/hero-rocket.png</a>.`).
  * If `autoFix` is enabled, the save happens automatically and the same summary line is shown immediately.
* A transcript file (`transcriptFile()`) is written in parallel containing `## Prompt`, `## Optimized Prompt`, and
  `## Result` (or `## Error`) sections in Markdown.

---

## Documentation

### Configuration

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `main_file` | Required | `String` | Single output image path. Must end in `.png`, `.jpg`, or `.jpeg`; validated explicitly by the task. |
| `task_description` | Optional | `String` | Detailed description of the image to generate — subject, style, composition, colors, mood, and specific requirements. Used to build the generation prompt. |
| `related_files` | Optional | `List<String>` | Additional context files. Files matching `.png/.jpg/.jpeg` are treated as reference images and passed to the image agent as `ImageAndText` inputs; non-image related files are included as text context via `getInputFileCode()`. |
| `task_dependencies` | Optional | `List<String>` | IDs of tasks that must complete first (standard task dependency wiring, inherited from `FileTaskExecutionConfig`). |
| `state` | Optional | `TaskState` | Task lifecycle state, defaults to `Pending`. |

### Dependencies

* Extends `AbstractFileTask`, so it participates in the standard file-task lifecycle (`getInputFileCode()`,
  `getPriorCode()`, ignoring `.png/.jpg/.jpeg` in the general file-diff pipeline via `isIgnored`/`formatFileForLLM`
  overrides).
* Delegates actual image synthesis to `ImageProcessingAgent` from `com.simiacryptus.cognotik.agents`, configured via
  `orchestrationConfig.defaultImage`.
* No hard dependency on other `TaskType`s, but commonly follows a planning/design task whose textual output feeds
  `related_files` or is picked up via `getPriorCode(agent.executionState)`.

### Token Usage

**Medium** — the LLM-facing prompt is typically a single description string plus any related-file context/prior
task text, and the primary cost is the image-generation call itself rather than large text generation.

---

## Config & Process

### Type Configuration

* `orchestrationConfig.defaultImage` — the model/client used to construct the `ImageProcessingAgent` (resolved per
  task via `getChildClient(task)`).
* `orchestrationConfig.autoFix` — controls whether the generated image is saved automatically or requires explicit
  user approval via an accept button.

### Runtime Configuration

* `GenerateImageTaskExecutionConfigData.main_file` — output path (validated for image extension).
* `task_description` — the generation prompt seed.
* `related_files` — reference images and/or textual context.

### Lifecycle

1. **Initialization:** `validate()` ensures `main_file` is present and has a valid image extension
   (`png|jpg|jpeg`, case-insensitive); otherwise the task configuration is rejected before execution.
2. **Execution:**
   * A transcript stream is opened and a two-tab UI (`Preview`, `Prompt`) is created.
   * Work is submitted to `task.pool` asynchronously.
   * Reference images are loaded from `related_files` via `ImageIO.read`; missing files are silently skipped.
   * The final prompt is assembled from `task_description` + context from related non-image files
     (`getInputFileCode()`) + prior task output (`getPriorCode()`), and logged to both the Prompt tab and transcript.
   * `ImageProcessingAgent.answer(...)` is called with the prompt and reference images; the returned image and the
     agent's own "optimized prompt" text are both displayed.
   * Depending on `autoFix`, the image is either saved immediately or gated behind an **Accept** button
     (`acceptButtonFooter`) that triggers the same save logic on click.
   * On save, the image is written with `ImageIO.write` using a format derived from the file extension, and a
     clickable link plus success summary is emitted via `resultFn`.
3. **Error Handling:** All generation/save logic is wrapped in try/catch. Failures are logged (error level), shown
   in the Preview tab via `previewTask.error(e)`, appended to the transcript under `## Error` with a full stack
   trace, and reported back through `resultFn("ERROR: ${e.message}")`. Scheduling failures (before the async block
   even starts) are caught separately at the top level and reported the same way. The transcript stream is always
   closed in a `finally` block.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings ...
    availableTaskTypes = listOf(
        ImageGenerationTask.GenerateImage,
        // ... other task types ...
    ),
    defaultImage = myImageModelClientConfig,
    autoFix = false, // require explicit user approval before saving generated images
)
```

### Prompt Segment (injected into planning LLM)

```
GenerateImage - Create high-quality images using AI generation models.
  * Specify a single output file path (png, jpg, or jpeg).
  * Provide a detailed task_description covering style, composition, and mood.
  * Use related_files to provide visual context or style references.
  * Useful for UI mockups, illustrations, and visual assets.
```

### Image-Generation Agent Prompt (internal)

```
Transform the user request into an image
```