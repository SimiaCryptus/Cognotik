# GenerateAudio

**AI-driven audio synthesis task — narration, voice-over, and sound-effect generation with reference-audio context.**

`Side-Effect Safe` (opt-in save via accept button unless `autoFix`) · `Destructive` (overwrites `main_file` on disk when saved) · `Audio Model Required`

---

## Reality Check

**Input configuration**

```json
{
  "task_type": "GenerateAudio",
  "main_file": "assets/narration/intro.mp3",
  "task_description": "Warm, calm female narrator reading the intro script at a slow, deliberate pace. Subtle room reverb, no music.",
  "related_files": [
    "assets/reference/voice_sample.wav",
    "scripts/intro_script.txt"
  ],
  "task_dependencies": ["draft-script"],
  "state": "Pending"
}
```

**Rendered output (UI)**

A tabbed display with two tabs:

- **Prompt** — a markdown code block showing the fully assembled generation prompt (task description + injected context from related files + prior task results).
- **Preview** — a header "Generating Audio: assets/narration/intro.mp3", followed by any agent response text (if the model returned commentary), then a "Generated Audio Preview" header. If `autoFix` is disabled, an accept-button footer appears ("Audio generated. Click below to save to workspace."); clicking it (or automatically under `autoFix`) writes the file and renders a success line: `Successfully generated and saved audio to <a href="...">assets/narration/intro.mp3</a>.` On failure, the tab renders a full stack trace via `previewTask.error(e)`.

A parallel transcript file logs the prompt, agent response text, and result/error in markdown.

---

## Documentation

### Configuration

| Field Name | Type | Description |
|---|---|---|
| `main_file` (Required) | `String` | Single output audio file path. Must end in `.mp3`, `.wav`, `.ogg`, `.flac`, `.m4a`, or `.aac`; validation fails otherwise. |
| `task_description` (Optional) | `String` | Detailed description of desired audio content, tone, voice, style, mood, pacing, and requirements. Forms the core of the generation prompt. |
| `related_files` (Optional) | `List<String>` | Additional context files — reference audio clips (used as `AudioAndText` inputs to the agent), scripts, or style guides (rendered as text context). |
| `task_dependencies` (Optional) | `List<String>` | IDs of prerequisite tasks whose results feed into `getPriorCode`. |
| `state` (Optional) | `TaskState` | Task lifecycle state, defaults to `Pending`. |

**Dependencies:** No hard dependency on another `TaskType` at the class level, but `task_dependencies` allows referencing other tasks (e.g. a script-writing task) whose outputs are appended to the prompt via `getPriorCode`. Uses `AudioProcessingAgent` (from `com.simiacryptus.cognotik.agents`) internally, driven by `orchestrationConfig.defaultAudio` (audio model) and `orchestrationConfig.defaultSmart` (text model).

**Token Usage:** `Medium` — prompt includes task description, concatenated related-file context, and prior task text; output is binary audio (not tokenized), but the text model used for response commentary adds moderate token cost.

---

## Config & Process

### Type Configuration

- `TaskType.name = "GenerateAudio"`, `category = "Writing"`.
- `executionConfigClass = GenerateAudioTaskExecutionConfigData::class.java`.
- `taskSettingsClass = TaskTypeConfig::class.java` (no task-specific settings beyond the base type config).

### Runtime Configuration

- `orchestrationConfig.defaultAudio` — audio-capable model client used to instantiate `AudioProcessingAgent`.
- `orchestrationConfig.defaultSmart` — text model client used for the agent's `textModel`.
- `orchestrationConfig.autoFix` — when `true`, generated audio is saved immediately; when `false`, the UI requires an explicit accept-button click.

### Lifecycle

1. **Initialization** — `GenerateAudioTaskExecutionConfigData.validate()` ensures `main_file` is non-empty and has a supported audio extension; otherwise `ValidatedObject.validateFields` surfaces other field errors.
2. **Execution** — A transcript stream is opened; a `TabbedDisplay` with `Preview` and `Prompt` tabs is created. Work is submitted to `task.ui.pool`: reference audio files matching audio extensions are base64-encoded into `AudioAndText`/`AudioSegment` inputs; text-context files and prior task outputs are concatenated into the prompt; the prompt is rendered to the Prompt tab and transcript; `AudioProcessingAgent.respond` is invoked with the prompt plus reference audio. If no audio is returned, an exception is thrown. On success, either `autoFix` triggers immediate save, or an accept button defers the save action until user confirmation; saving writes the file via `AudioSegment.writeAudio`, adds a workspace link, and completes the preview task.
3. **Error Handling** — Any exception during generation or scheduling is logged (`log.error`), rendered in the Preview tab via `previewTask.error(e)`, appended to the transcript as a stack trace, and reported through `resultFn("ERROR: ${e.message}")`. The transcript stream is always closed in a `finally` block; scheduling failures are caught separately and also close the transcript and report the error.

---

## Integration

### Registering in `OrchestrationConfig`

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other config ...
    defaultAudio = audioModelClient,   // model capable of audio generation
    defaultSmart = textModelClient,    // text model for agent commentary
    autoFix = false,                   // require explicit user acceptance before saving
    taskTypes = listOf(
        AudioGenerationTask.GenerateAudio,
        // ...other registered task types
    )
)
```

### Prompt Segment (injected into planning LLM)

```
GenerateAudio - Create high-quality audio using AI generation models.
  * Specify a single output file path (mp3, wav, ogg, flac, m4a, or aac).
  * Provide a detailed task_description covering content, tone, voice, and style.
  * Use related_files to provide audio context, scripts, or style references.
  * Useful for narration, voice-overs, sound effects, and audio assets.
```