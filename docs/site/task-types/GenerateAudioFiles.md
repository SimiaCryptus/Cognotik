# GenerateAudioFiles

**Render a spoken-word script into per-segment audio files plus a JSON manifest, with voice/silence directives and a two-phase script-generation option.**

`Side-Effect Safe` · `Writing` · `Audio Model Required` · `Async Execution`

---

## Reality Check

**Input configuration**

```json
{
  "task_type": "GenerateAudioFiles",
  "task_description": "Produce a 60-second welcome message for a new podcast episode, alternating between a host and a guest voice, with a 1-second pause between sections.",
  "output_directory": "audio/episode-01",
  "metadata_file": "audio/episode-01/metadata.json",
  "audio_format": "wav",
  "file_base_name": "segment",
  "default_voice": "Callirrhoe",
  "two_phase": true,
  "related_files": [],
  "task_dependencies": []
}
```

**Rendered output (UI)**

The task streams progress into the session transcript, then presents a tabbed result panel:

- **Overview tab** — bullet list: Output Directory, Metadata File, Segment Count, Total Duration, Default Voice, Two-Phase flag.
- **Segments tab** — one card per segment: index badge, voice or `[silence Ns]` label, duration, the segment's script text, and an inline HTML5 `<audio>` player pointing at a saved preview file, with a link to the relative file path.
- **Manifest tab** — the full `metadata.json` rendered as a syntax-highlighted JSON code block.
- **Script tab** — the reconstructed script, segments joined by `---`, each prefixed with its voice/silence annotation.

If `orchestrationConfig.autoFix` is `false`, an **"Accept Audio Files"** link/button appears; clicking it commits the segment files and manifest to disk under `output_directory`. If `autoFix` is `true`, files are written immediately without user confirmation.

---

## Documentation

### Configuration Fields

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `output_directory` | Required | `String` | Directory (relative path) where audio segment files and `metadata.json` will be written. |
| `metadata_file` | Optional | `String` | JSON metadata file to create (relative path, must end with `.json`). Defaults to `<output_directory>/metadata.json`. |
| `audio_format` | Optional | `String` | Audio file format/extension for segment files (e.g. `wav`, `mp3`). Defaults to `wav`. |
| `file_base_name` | Optional | `String` | Base name (no extension) for segment files. Defaults to `segment`; files are named `<base>_<index>.<ext>`. |
| `default_voice` | Optional | `String` | Default voice id used for segments without an explicit `[voice:Name]` directive. |
| `two_phase` | Optional | `Boolean` | Whether to use a text model to convert the user request into a speaking script before audio generation. Defaults to `true`. |
| `task_description` | Inherited | `String` | Detailed description of the audio script to generate, or the script itself. |
| `related_files` | Inherited | `List<String>` | Files providing context for script generation. |
| `task_dependencies` | Inherited | `List<String>` | IDs of tasks that must complete first. |

### Dependencies

- **`AudioProcessingAgent`** (`com.simiacryptus.cognotik.agents`) — performs the actual segment-by-segment rendering, retry/timeout handling, and voice resolution (`pickVoices`).
- **`AbstractFileTask`** — base class providing file/context wiring (`getInputFileCode`, `getPriorCode`).
- No hard dependency on other `TaskType`s, but it consumes `related_files`/`task_dependencies` outputs like other file tasks, and uses `orchestrationConfig.defaultAudio` and an optional smart text model for the two-phase script pass.

### Token Usage

**Medium** — the text-model phase (if `two_phase=true`) consumes a prompt built from `task_description` plus context/prior-code, generating a moderate-length script. The audio-model phase itself is not text-token-metered in the usual LLM sense but issues one call per segment; overall LLM token cost stays low-to-moderate unless the script/context is large.

---

## Config & Process

### Type Configuration vs. Runtime Configuration

- **Type Configuration** (`TaskTypeConfig`, set at orchestration/task-type level): selects the text model used for the optional script-writing phase via `typeConfig?.model`, falling back to `defaultSmart` if unset.
- **Runtime Configuration** (`GenerateAudioFilesTaskExecutionConfigData`): all per-invocation fields — `output_directory`, `metadata_file`, `audio_format`, `file_base_name`, `default_voice`, `two_phase`, and the inherited `task_description`/`related_files`/`task_dependencies`.

### Lifecycle

1. **Initialization**
    - `validate()` checks: `output_directory` non-blank, `metadata_file` (if set) ends with `.json`, `audio_format` non-blank, plus generic field validation via `ValidatedObject.validateFields`.
    - Resolves `outputDir`, `audioFormat`, `baseName`, and `metadataFile` (defaulting to `<output_directory>/metadata.json`).
    - Opens a transcript stream and logs a header in the session UI.

2. **Execution** (submitted to `task.ui.pool` as an async job)
    - Resolves audio and (optionally) text models via `orchestrationConfig.defaultAudio` / `typeConfig`/`defaultSmart`.
    - Picks available voices (`pickVoices`) and resolves `default_voice`, falling back to the first available voice or `"Callirrhoe"`.
    - Builds the LLM prompt from `task_description`, plus context files and prior task results.
    - Instantiates `AudioProcessingAgent` and calls `renderSegments` to produce per-segment audio + parsed metadata (voice/silence/text).
    - If no segments are produced, reports an error and exits.
    - Converts each segment's audio to the target `audio_format` if needed (catching and logging conversion failures, falling back to original audio).
    - Builds `AudioSegmentMetadata` per segment and an aggregate `AudioFilesManifest`.
    - Writes preview copies of each segment to `previews/<relativePath>` for inline `<audio>` playback in the UI, building HTML cards per segment (or an error card if audio is missing).
    - Renders `Overview`, `Segments`, `Manifest`, and `Script` tabs via `TabbedDisplay`.

3. **Commit (accept or autoFix)**
    - `commitAction` creates the output directory, writes each segment's audio file to disk (tracking success/failure counts), writes `metadata.json`, and logs detailed diagnostics at each step.
    - Calls `task.safeComplete(...)` and `resultFn(...)` with a summary including counts and total duration.
    - If `orchestrationConfig.autoFix` is true, this runs immediately; otherwise it's deferred behind an "Accept Audio Files" UI action.

4. **Error Handling**
    - Any exception during async execution is caught, logged, reported via `task.error(e)`, appended to the transcript as a stack trace block, and returned via `resultFn("ERROR: ...")`.
    - A separate outer `try/catch` guards synchronous setup (transcript creation, header rendering) with the same error-reporting pattern.
    - The transcript stream is always closed in a `finally` block, with failures to close logged but not propagated.
    - Per-segment audio conversion failures are caught individually and don't abort the whole task — the original (unconverted) audio is used instead, and the segment is marked accordingly if `null` audio results.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
  // ... other configuration ...
  taskTypes = listOf(
    GenerateAudioFilesTask.GenerateAudioFiles,
    // ...other task types...
  ),
)

// Example execution config for this task
val execConfig = GenerateAudioFilesTask.GenerateAudioFilesTaskExecutionConfigData(
  output_directory = "audio/episode-01",
  audio_format = "wav",
  default_voice = "Callirrhoe",
  two_phase = true,
  task_description = "Produce a 60-second podcast welcome message with host and guest voices."
)
```

### Prompt Segment

The following is the static description injected to describe this task's capabilities within the orchestration prompt:

```text
GenerateAudioFiles - Render an audio script as individual per-segment audio files plus a JSON manifest
  * Splits the script using `---` separators (and/or has the model produce them in two-phase mode)
  * Renders each segment in parallel using AudioProcessingAgent (with retry / timeout)
  * Writes one audio file per segment to the output directory
  * Writes a metadata.json manifest mapping each file to its script text, voice, and duration
```