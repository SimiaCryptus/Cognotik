# IllustrateDocument

**Analyze a Markdown/HTML document and generate contextually-relevant images, then patch them into the source.**

`Side-Effect Safe` (patch-gated by `autoFix`) · `Destructive` (writes image files + can rewrite document) · `Vision Required` · `Category: Writing`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "IllustrateDocument",
  "main_file": "docs/architecture.md",
  "max_images": 4,
  "image_format": "png",
  "auto_insert": true,
  "image_instructions": "Use a flat, minimal vector style with a blue/gray palette",
  "composer_directive": "Focus on technical diagrams, not photorealistic scenes",
  "integrator_directive": "Insert directly beneath the relevant heading"
}
```

**Rendered Output (UI)**

A tabbed session view (`TabbedDisplay`) with four tabs:

1. **Overview** — bullet summary: format detected (Markdown/HTML), max images, image format, any composer/integrator directives.
2. **Analysis** — a numbered list of planned images (`### 1. image_name`, location, caption), followed by an approval button (`🚀 Proceed with Generation`) if `autoFix` is disabled.
3. **Generation** — per-image sections with an inline `<img>` preview (max-width 400px, rounded, shadowed), a success line `✅ Saved as \`filename.png\`` or a red-text failure line if generation fails.
4. **Integration** — a rendered `diff` code block showing the proposed insertion patches, either auto-applied ("Auto-applied image insertion patches") or gated behind an "Accept" footer button.

The transcript file additionally contains collapsible `<details>` blocks for the raw document, the JSON `DocumentAnalysis`, and any stack traces on failure.

---

## Documentation

### Configuration Fields

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `main_file` | Required (inherited) | `String` | Must be a single `.md` or `.html` file; exactly one file may be specified. |
| `max_images` | Optional | `Int` (default `5`) | Coerced to range `1..20`. Maximum number of images to generate. |
| `image_format` | Optional | `String` (default `"png"`) | Must be `png`, `jpg`, or `jpeg`; normalized to lowercase. |
| `auto_insert` | Optional | `Boolean` (default `true`) | Whether image references should be inserted into the document automatically. |
| `image_instructions` | Optional | `String?` | Additional free-text instructions appended to every image generation prompt (style, constraints). |
| `composer_directive` | Optional | `String?` | Directive steering the image-generation model (e.g. "Generate a background wallpaper"). |
| `integrator_directive` | Optional | `String?` | Directive steering how/where the integrator model places images (e.g. "Insert as page background"). |

### Dependencies

- **`ParsedAgent`** (from `com.simiacryptus.cognotik.agents`) — used to produce a structured `DocumentAnalysis` (list of `ImageSuggestion`) from the document content.
- **`ImageProcessingAgent`** — generates the actual raster image per suggestion using the orchestration's `defaultImage` model.
- **`ChatAgent`** — generates the diff patch text for inserting image references.
- **`DiffInstrumentor`** / `PatchProcessors.Fuzzy` — parses and applies the generated diff patches to the document file, same infrastructure used by other file-patching tasks (e.g. code-editing tasks in this package).
- No explicit dependency on other `TaskType`s in the orchestration graph, but it assumes `main_file` already exists on disk (typically produced/edited by prior tasks).

### Token Usage Estimate

**Medium** — the analysis prompt embeds up to 10,000 characters of document content plus a structured-output parse; the integration prompt embeds the *full* document content again. Image generation itself is a vision-model call, not token-metered in the same way, but the two text prompts alone push this above "Low".

---

## Config & Process

### Type Configuration (fixed at `TaskType` registration)

```kotlin
val IllustrateDocument = TaskType(
  name = "IllustrateDocument",
  category = "Writing",
  taskClass = IllustrateDocumentTask::class.java,
  executionConfigClass = IllustrateDocumentTaskExecutionConfigData::class.java,
  taskSettingsClass = TaskTypeConfig::class.java,
  description = "...",
  tooltipHtml = "..."
)
```

### Runtime Configuration (per invocation)

`IllustrateDocumentTaskExecutionConfigData` — `main_file`, `max_images`, `image_format`, `auto_insert`, `image_instructions`, `composer_directive`, `integrator_directive`, plus inherited `task_description`, `task_dependencies`, `state`.

### Lifecycle

**Initialization**
- `validate()` enforces exactly one `main_file` with `.md`/`.html` extension, clamps `max_images` to `1..20`, and normalizes `image_format`.
- On `run()`, resolves `documentFile` from config; aborts with a logged/UI error if unset or the file does not exist on disk.
- Derives a themed output directory (`dataDir`) from the primary file name for storing preview copies of generated images.

**Execution**
1. Reads the document's raw text and detects Markdown vs. HTML.
2. Builds an analysis prompt (`buildAnalysisPrompt`) embedding up to 10k chars of content, the `composer_directive` (if any), and instructions to produce up to `max_images` `ImageSuggestion`s.
3. Calls `ParsedAgent` to get a validated `DocumentAnalysis`; logs and transcribes the raw JSON.
4. Unless `orchestrationConfig.autoFix` is true, blocks on a `Semaphore` waiting for a UI "Proceed with Generation" click.
5. For each suggestion, builds an enhanced prompt (base `image_prompt` + `composer_directive` + `image_instructions`), calls `ImageProcessingAgent`, writes the raw file next to the document and a preview copy into `dataDir`. Failures per-image are caught individually and reported inline without aborting the whole task.
6. Builds a patch-insertion prompt (`generateImageInsertionPatches`) containing the full document, the list of generated image files/captions/locations, and the `integrator_directive`. Sends it via `ChatAgent`.
7. Applies the resulting diff via `DiffInstrumentor`; if `autoFix` is enabled the patch auto-applies, otherwise an "Accept" button gates application and releases a semaphore.
8. Writes a final summary (counts, elapsed time, generated image list) to both the transcript and the task result callback.

**Error Handling**
- File-not-found and missing-config errors short-circuit early with a triple-logged (`SessionTask`, SLF4J, transcript) error message and no partial work.
- Per-image generation errors are caught and reported individually (red-text UI line + stack trace in transcript `<details>`), allowing remaining images to still be generated.
- Patch generation/integration (`integrateImagesWithRetry`) retries once on exception before giving up; failures release the waiting semaphore and log via `subTask.error(e)`.
- The outer `run()` block wraps everything in a top-level `try/catch/finally`, guaranteeing the transcript stream is always closed and a failure summary is emitted via `resultFn`.

---

## Integration

### Registering in an `OrchestrationConfig`

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings
    availableTaskTypes = listOf(
        IllustrateDocumentTask.IllustrateDocument,
        // ...other TaskTypes
    ),
    defaultImage = myImageModelClient,
    autoFix = false, // require human approval before generation/patching
)
```

### Prompt Segment (injected into planning LLM)

```text
IllustrateDocument - Analyze a document and generate images to enhance its content
  ** Specify a single markdown or HTML file to illustrate
  ** Configure max_images (default: 5, range 1-20)
  ** Choose image_format (png/jpg)
  ** Optionally provide composer_directive to control image generation style
  ** Optionally provide integrator_directive to control image placement
  ** Analyzes document structure and content to identify optimal image locations
  ** Generates contextually appropriate images with descriptive names
  ** Saves images in the same folder as the document
  ** Optionally inserts image references at appropriate locations via diff patches
  ** Use this when the user wants to add visual enhancements to an existing document
```