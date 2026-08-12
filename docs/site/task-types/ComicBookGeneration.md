# ComicBookGeneration

**Generates a structured comic book script (page/row/frame) from a subject prompt, optionally rendering character reference sheets and per-row comic strip visuals via an image model.**

`Side-Effect Safe` · `Writing` · `Vision Required` (when `generate_images: true`) · `User-Approval Gate`

---

## Reality Check

**Input configuration:**

```json
{
  "task_type": "ComicBookGeneration",
  "task_description": "A rookie detective in a rain-soaked cyberpunk city discovers her partner is an android",
  "target_pages": 3,
  "art_style": "noir",
  "style_details": "high-contrast black and white with neon accent colors, heavy shadow work",
  "generate_images": true,
  "character_references": [
    {
      "character_name": "Detective Mara Chen",
      "reference_image_path": "uploads/mara_ref.png",
      "visual_description": "Late 30s, short dark hair, trench coat, cybernetic left eye",
      "personality_notes": "Cynical, sharp-tongued, secretly compassionate",
      "role": "protagonist",
      "relationships": "Partner of Kade-7"
    }
  ],
  "plot_continuity": {
    "narrative_structure": "three-act structure",
    "key_plot_points": [
      "Mara discovers evidence of android tampering",
      "Confrontation with Kade-7's manufacturer",
      "Mara decides to protect Kade-7's secret"
    ],
    "setting_details": "Neo-Shanghai, 2087, perpetual rain",
    "themes": ["identity", "trust"],
    "tone": "dark and gritty",
    "prior_story_context": "",
    "continuity_constraints": ["Kade-7's true nature is not revealed before page 2"],
    "ending_notes": "Ambiguous, bittersweet",
    "additional_notes": ""
  }
}
```

**Rendered output (UI):**

* An **"Overview"** tab showing a status line that progresses `Generating script...` → `✅ Script Generated` → (after approval) `✅ Script Generated<br/>Generating pages...` → `✅ Images Generated`.
* A **"Script"** tab containing the full rendered Markdown script: title, premise, character list (name/description/visual traits), and a page-by-page breakdown of rows/panels/dialog/captions.
* An **approval gate** (footer button "Accept") shown on the Overview tab before any image generation begins, unless `orchestrationConfig.autoFix` is set.
* A **"Characters"** tab with one `<h2>` block per character, each containing an embedded reference image (`<img>`) and a description caption.
* One tab **per page** (`"Page 1"`, `"Page 2"`, ...) containing, per row: an embedded strip image (`<img>` sized to 800px max-width, bordered) and the row's dialog/caption text rendered as Markdown.
* A transcript file (Markdown) capturing the full script, all generated images (as embedded links), and any errors with collapsible `<details>` stack traces.
* A saved JSON artifact (`*.comic.json`) containing `config`, `script`, `characterImages` (name → path map), and `rowImages` (page_row → path map).
* Final result summary text listing title, page count, character reference count, continuity status, and artifact file counts.

---

## Documentation

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `task_description` | Required | `String?` | The subject or scenario to develop into a comic book. |
| `target_pages` | Optional (default `5`) | `Int` | Target number of pages; coerced to range `1..100`. |
| `art_style` | Optional (default `"western superhero"`) | `String` | Art style (e.g. `manga`, `western superhero`, `noir`, `cartoon`). Falls back to default if blank. |
| `style_details` | Optional (default `""`) | `String` | Additional style/visual guideline details. |
| `generate_images` | Optional (default `true`) | `Boolean` | Whether to generate images for each row. |
| `character_references` | Optional (default `[]`) | `List<CharacterReference>` | Pre-defined character reference images/descriptions for visual consistency. |
| `character_references[].character_name` | Required | `String` | Name of the character this reference applies to. |
| `character_references[].reference_image_path` | Optional | `String?` | Path/URL to a user-supplied reference image. |
| `character_references[].visual_description` | Optional | `String?` | Detailed visual description (appearance, clothing, palette). |
| `character_references[].personality_notes` | Optional | `String?` | Personality/behavioral notes. |
| `character_references[].role` | Optional | `String?` | Character's story role (protagonist, antagonist, etc.). |
| `character_references[].relationships` | Optional | `String?` | Relationships with other characters. |
| `plot_continuity` | Optional | `PlotContinuityDetails?` | Narrative continuity guidance object. |
| `plot_continuity.narrative_structure` | Optional | `String` | Overall story arc/structure. |
| `plot_continuity.key_plot_points` | Optional | `List<String>` | Ordered required plot points. |
| `plot_continuity.setting_details` | Optional | `String` | Time period/location/world-building notes. |
| `plot_continuity.themes` | Optional | `List<String>` | Themes/motifs. |
| `plot_continuity.tone` | Optional | `String` | Tone/mood guidance. |
| `plot_continuity.prior_story_context` | Optional | `String` | Backstory this comic continues from. |
| `plot_continuity.continuity_constraints` | Optional | `List<String>` | Specific ordering/appearance constraints. |
| `plot_continuity.ending_notes` | Optional | `String` | Desired ending/resolution notes. |
| `plot_continuity.additional_notes` | Optional | `String` | Free-form extra guidance for writer/artist. |
| `task_dependencies` | Optional | `List<String>?` | Standard task dependency graph field (inherited from `TaskExecutionConfig`). |

### Dependencies

No hard dependency on other `Task` classes is wired in code — this task is self-contained, using:

- **`ParsedAgent<ComicScript>`** against `defaultSmart` (with `defaultFast` as the parsing model) to generate the structured script.
- **`ImageProcessingAgent`** against `orchestrationConfig.defaultImage` for both character reference-sheet images and per-row strip visuals (only when `generate_images: true`).

### Token Usage Estimate

**High** — the script-generation prompt embeds character references and full plot continuity details, and the parsed response can span many pages/rows/frames with dialog. Image generation stages add further per-character and per-row multimodal calls (image + text) proportional to `target_pages`.

---

## Config & Process

### Type Configuration

Static, defined once per `TaskType` registration:

- `name = "ComicBookGeneration"`, `category = "Writing"`
- `executionConfigClass = ComicBookGenerationTaskExecutionConfigData::class.java`
- `taskSettingsClass = TaskTypeConfig::class.java` (no task-specific settings beyond the base)

### Runtime Configuration

Supplied per invocation via `ComicBookGenerationTaskExecutionConfigData`: `task_description`, `target_pages`, `art_style`, `style_details`, `generate_images`, `character_references`, `plot_continuity`, plus inherited `task_dependencies`/`state`.

### Lifecycle

1. **Initialization**
   - `validate()` coerces `target_pages` into `1..100` and defaults `art_style` to `"western superhero"` if blank.
   - A transcript file stream and an output directory (derived from the task's `.md` output filename, or `"comic"`) are created.
   - Aborts early with a `CONFIGURATION ERROR` if no usable `subject` is found in `messages` or `task_description`.

2. **Execution**
   - Builds character-reference and plot-continuity prompt fragments and combines them into a single script-generation prompt.
   - Calls `ParsedAgent<ComicScript>` (smart model for generation, fast model for parsing) to produce a `ComicScript` (title, premise, character profiles, pages/rows/frames/dialog).
   - Renders the script to Markdown and displays it in a "Script" tab; updates the "Overview" status to `✅ Script Generated`.
   - If `generate_images` is `false`, returns immediately with a text-only summary and skips to persistence.
   - If `generate_images` is `true` **and** `orchestrationConfig.autoFix` is `false`, blocks on a `Semaphore` gated by an **"Accept"** button — the user must approve before visuals are generated. In `autoFix` mode this gate is bypassed automatically.
   - Preloads any user-supplied `reference_image_path` images per character.
   - Generates a character reference sheet per `ComicScript.characters` entry via `ImageProcessingAgent`, chaining the previous character's image as a style reference and merging in matched `character_references` metadata; saves each as `char_<name>.png` and displays it in a "Characters" tab.
   - Generates one strip image per row via `ImageProcessingAgent`, supplying: any matching character reference images (user-provided and/or generated), the previous row's image as a style/continuity reference, and the row's `visual_description`/frame/dialog text. Saves each as `page_<n>_row_<n>.png` in a per-page tab.
   - Persists a full JSON artifact combining `config`, `script`, `characterImages`, and `rowImages`.
   - Calls `task.safeComplete(...)` and returns a final Markdown summary via `resultFn`.

3. **Error Handling**
   - Each character-image and row-image generation call is individually wrapped in try/catch: failures are logged, reported via `task.error(e)`, written to the transcript as a collapsible stack trace, and rendered as `**Failed to generate image**` inline — generation continues for remaining characters/rows rather than aborting the whole task.
   - A top-level try/catch/finally wraps the entire `run` body: any unhandled exception is reported via `task.error(e)`, logged, written to the transcript, and returned as an `"Error: ..."` string; the transcript stream is always closed in `finally`.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other config ...
    availableTaskTypes = listOf(
        ComicBookGenerationTask.ComicBookGeneration,
        // ...other TaskType entries...
    ),
    defaultImage = imageModel, // required for generate_images = true
)

val executionConfig = ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData(
    task_description = "A rookie detective in a rain-soaked cyberpunk city discovers her partner is an android",
    target_pages = 3,
    art_style = "noir",
    generate_images = true,
)
```

### Prompt Segment (injected into planning LLM)

```
ComicBookGeneration - Generate comic book scripts and visuals
  ** Use this tool to create professional comic book scripts with a structured page/row/frame layout.
  ** Inputs: Requires a 'task_description' (subject), 'target_pages', and 'art_style'.
  ** Optional Inputs:
    - 'character_references': Provide reference images and detailed descriptions for characters to ensure visual consistency.
    - 'plot_continuity': Specify narrative structure, key plot points, themes, tone, prior story context, and continuity constraints.
  ** Capabilities: Generates character profiles, detailed visual descriptions, and can optionally generate AI visuals for each row (strip).
  ** Output: Returns a summary of the generated script and links to saved image artifacts.
```

### Script-Generation Prompt (constructed at runtime)

```
You are a professional comic book writer. Create a detailed script for a comic book.
**Subject:** <subject>
**Target Pages:** <target_pages>
**Style:** <art_style>
Style Details: <style_details>
[## Pre-defined Character References ...]
[## Plot Continuity & Narrative Guidelines ...]

Structure the output with:
- Title and Premise
- Character Profiles (Name, Description, Visual Traits)
- Pages (numbered)
- Rows per page (usually 3-4 rows per page)
- Frames per row (usually 1-3 frames per row)

For each frame, provide:
- Visual description
- Dialog (Character: Text)
- Captions (if any)

For each row, provide a 'visual_description' that summarizes the row for an artist to draw as a strip. ...
```