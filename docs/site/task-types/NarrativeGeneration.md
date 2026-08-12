# NarrativeGeneration

**Generate complete, publication-ready narratives from a subject/premise via multi-pass outlining and iterative scene writing.**

`Category: Writing` · `Side-Effect Safe` · `Vision Optional (Image Generation)` · `Multi-Model Pipeline`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "NarrativeGeneration",
  "subject": "A retired lighthouse keeper discovers a message in a bottle that leads to a decades-old mystery",
  "related_files": ["notes/backstory.md"],
  "narrative_elements": {
    "conflict": "man vs. past",
    "timeline": "present day with flashbacks"
  },
  "target_word_count": 6000,
  "number_of_acts": 3,
  "scenes_per_act": 3,
  "writing_style": "literary",
  "point_of_view": "third person limited",
  "tone": "reflective",
  "detailed_descriptions": true,
  "include_dialogue": true,
  "show_internal_thoughts": true,
  "revision_passes": 1,
  "generate_scene_images": true,
  "generate_cover_image": true
}
```

**Rendered Output (UI)**

A tabbed display (`TabbedDisplay`) appears with one tab per phase/artifact:

* **Overview** — live progress log: phase checkmarks (✅ Phase 1/2/3/4 Complete), per-scene bullet list with word counts as they complete, and final statistics block (total scenes, total words, elapsed time).
* **Cover Image** — generated cover artwork rendered inline (`<img>` in an HTML card) with the image prompt shown beneath it.
* **Outline** — full rendered high-level outline (title, premise, characters, settings, act summaries), followed by the expanded detailed outline with all scenes.
* **Setting: `<id>`** / **Character: `<name>`** — one tab per generated reference image, each with prompt text and portrait/landscape image.
* **Act N Scene M** — one tab per scene, containing the rendered prose plus key moments and character-state summary.
* **Act N Scene M Image** — the illustrated scene image tab.
* **Complete Narrative** — final assembled, publication-formatted narrative text.

A markdown transcript file and a `*.narrative.json` data file (full outline + scenes + asset paths + statistics) are also written to the workspace for download/reuse.

---

## Documentation

### Configuration

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `task_description` | Optional | `String?` | Inherited base field; auto-derived from `subject` if blank. |
| `subject` | Required (effectively) | `String?` | The subject or scenario to develop into a full narrative. Task fails with a configuration error if blank. |
| `related_files` | Optional | `List<String>?` | File patterns (e.g. `**/*.kt`) used as input context loaded via `getInputFileContent`. |
| `narrative_elements` | Optional | `Map<String, Any>?` | Freeform narrative elements (characters, setting, conflict, timeline, etc.). |
| `target_word_count` | Optional | `Int` (default `5000`) | Target total word count; clamped to `100..100000`. |
| `number_of_acts` | Optional | `Int` (default `3`) | Number of acts in the structure; clamped to `1..10`. |
| `scenes_per_act` | Optional | `Int` (default `3`) | Average scenes per act; clamped to `1..20`. |
| `writing_style` | Optional | `String` (default `"literary"`) | Writing style (e.g. literary, thriller, technical). Blank values reset to `"literary"`. |
| `point_of_view` | Optional | `String` (default `"third person limited"`) | Narrative POV. |
| `tone` | Optional | `String` (default `"dramatic"`) | Overall tone. |
| `detailed_descriptions` | Optional | `Boolean` (default `true`) | Whether to include detailed scene descriptions. |
| `include_dialogue` | Optional | `Boolean` (default `true`) | Whether to include character dialogue. |
| `show_internal_thoughts` | Optional | `Boolean` (default `true`) | Whether to show internal character thoughts. |
| `revision_passes` | Optional | `Int` (default `2`) | Number of revision passes per scene; clamped to `0..5`. |
| `generate_scene_images` | Optional | `Boolean` (default `true`) | Whether to generate per-scene, setting, and character reference images. |
| `generate_cover_image` | Optional | `Boolean` (default `true`) | Whether to generate a cover image used as a visual seed for other images. |
| `task_dependencies` | Optional | `List<String>?` | Standard task-graph dependency list (inherited). |

### Dependencies

No hard dependency on other `TaskType`s is wired in code — this task is self-contained, invoking three internal agent types directly:

* `ParsedAgent` (structured outline/scene generation, using `defaultSmart` + `defaultFast` as parsing model)
* `ChatAgent` (scene revision passes)
* `ImageProcessingAgent` (cover/setting/character/scene image generation, using `orchestrationConfig.defaultImage`)

It optionally reads related files via the shared `getInputFileContent` utility (same mechanism other file-context tasks use), but does not enqueue or depend on sibling tasks in the orchestration graph.

### Token Usage

**High** — This task issues one high-level outline call, one scene-expansion call per act, one generation call per scene (`number_of_acts × scenes_per_act`), plus one `ChatAgent` revision call per scene per revision pass, and up to `2 + settings + characters + scenes` image-generation calls. For default settings (3 acts × 3 scenes, 1 revision pass) this is ~9 scene-generation calls + ~9 revision calls + outline calls + image calls — substantial cumulative token/image spend.

---

## Config & Process

### Type Configuration
Set once at task-type registration and largely fixed for the lifetime of the task type:
- `task_type = "NarrativeGeneration"`
- `taskClass = NarrativeGenerationTask::class.java`
- `executionConfigClass = NarrativeGenerationTaskExecutionConfigData::class.java`
- `taskSettingsClass = TaskTypeConfig::class.java`
- `category = "Writing"`, plus the static description/tooltip HTML shown in the task picker UI.

### Runtime Configuration
Set per-invocation via `NarrativeGenerationTaskExecutionConfigData`: `subject`, `related_files`, `narrative_elements`, structural sizing (`target_word_count`, `number_of_acts`, `scenes_per_act`), stylistic knobs (`writing_style`, `point_of_view`, `tone`, `detailed_descriptions`, `include_dialogue`, `show_internal_thoughts`), quality controls (`revision_passes`), and asset toggles (`generate_scene_images`, `generate_cover_image`). All numeric/string fields are canonicalized (clamped/defaulted, never rejected) in `validate()`.

### Lifecycle

**Initialization**
- `validate()` clamps out-of-range values (word count, acts, scenes/act, revision passes) and syncs `subject` ↔ `task_description` so either can be supplied.
- On `run()`, the config is checked for `null` and for a blank `subject`; both cases short-circuit with a `CONFIGURATION ERROR` result and no further work performed.
- Input files (`related_files`) are loaded via `getInputFileContent`; failures are logged and produce a warning in the transcript but do **not** abort the run (context is treated as best-effort).

**Execution**
1. **Phase 1 — Analysis** (placeholder `analysisResult` buffer for narrative reasoning context).
2. **Phase 2.0 — Cover image** generated first (if either image flag is set) to serve as a visual seed for all subsequent images.
3. **Phase 2.1 — High-level outline**: a `ParsedAgent` produces a `HighLevelOutline` (title, premise, characters, settings, act summaries).
4. **Phase 2.2 — Scene expansion**: each `ActSummary` is expanded into a detailed `ActOutline` with per-scene `SceneOutline`s via a second `ParsedAgent` call per act, informed by previously expanded acts for continuity.
5. **Phase 2.5 — Reference images**: if `generate_scene_images`, one image per defined setting and one per defined character are generated, each seeded by the cover image.
6. **Phase 3 — Scene generation**: for every scene (in act/scene order), a `ParsedAgent` writes a `GeneratedScene`, given a rolling context window of the last two scenes' key moments/character states/tail text. Optional revision passes rewrite `content` via `ChatAgent`. A per-scene image is generated afterward using matched setting/character reference images.
7. **Phase 4 — Assembly**: scenes are compiled into a final narrative markdown, statistics (word count, scene count, duration) are computed, and a `*.narrative.json` artifact (config + outlines + scenes + asset paths + stats) is written to the workspace.

**Error Handling**
- Outline generation, act expansion, and the overall phase-3/4 block are wrapped in `try/catch`; failures are logged with stack traces into the transcript (inside `<details>` blocks) and re-thrown as `RuntimeException` to abort generation with context.
- The outermost `catch` in `run()` calls `task.error(e)`, writes the error and any **partial results** (`resultBuilder`) accumulated so far into the final output, and returns that partial-error report via `resultFn` rather than throwing further.
- Image generation calls (cover/setting/character/scene) are individually wrapped in `try/catch` — a failure in any one image is logged and reported inline but does **not** abort the overall narrative generation (image generation degrades gracefully).
- The transcript writer is always closed in a `finally` block regardless of success/failure.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    defaultSmart = smartChatModel,
    defaultFast = fastChatModel,
    defaultImage = imageModel,
    availableTaskTypes = listOf(
        NarrativeGenerationTask.NarrativeGeneration,
        // ...other TaskTypes
    ),
    // ...other orchestration settings
)

// Example task submission:
val config = NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData(
    subject = "A retired lighthouse keeper discovers a message in a bottle...",
    target_word_count = 6000,
    number_of_acts = 3,
    scenes_per_act = 3,
    writing_style = "literary",
    generate_cover_image = true,
    generate_scene_images = true
)
```

### Prompt Segment (injected into planner LLM)

```text
NarrativeGeneration - Generate complete narratives from analysis and outlines
  ** Extends NarrativeReasoning with full story generation
  ** Specify the subject or scenario to develop
  ** Define narrative elements: characters, setting, conflict, timeline
  ** Set target word count and structural parameters (acts, scenes)
  ** Configure writing style, POV, and tone
  ** Enable detailed descriptions, dialogue, and internal thoughts
  ** Performs analysis, creates outline, then writes each scene iteratively
  ** Each scene receives context from previous scenes
  ** Produces complete, coherent narrative with consistent style
```