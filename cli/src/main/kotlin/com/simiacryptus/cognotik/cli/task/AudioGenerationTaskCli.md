# AudioGenerationTaskCli

Command line wrapper for `AudioGenerationTask` (task type `GenerateAudio`). It runs the task
through `TaskHarness`, generating an audio file from a text description.

By default it uses the hosted backend (device login, proxied API calls). Pass `--local` to use
locally configured API keys instead.

## Launching

```bash
cli/bin/cognotik audio --prompt "Calm 20 second narration introducing Cognotik" --output out/intro.mp3
```

Or directly:

```bash
java -cp cli-all.jar com.simiacryptus.cognotik.cli.task.AudioGenerationTaskCli --help
```

## Options

| Option                  | Description                                                                                       |
|-------------------------|---------------------------------------------------------------------------------------------------|
| `--prompt <text>`       | **Required.** Description of the audio: content, tone, voice, style, mood, pacing.                |
| `--output <file>`       | **Required.** Output file (`.mp3`, `.wav`, `.ogg`, `.flac`, `.m4a`, `.aac`) inside the workspace. |
| `--related <files>`     | Comma-separated context files (reference audio clips, scripts, style guides).                     |
| `--workspace <dir>`     | Workspace directory (default: `.`). Relative paths are resolved against it.                       |
| `--timeout-minutes <n>` | Harness timeout (default: 30).                                                                    |
| `--smart-model <name>`  | Smart model (default: `GeminiFlash_38`).                                                          |
| `--fast-model <name>`   | Fast model (default: `GeminiFlash_38`).                                                           |
| `--audio-model <name>`  | Audio model (default: the fast model). Use an audio-capable model for real generation.            |
| `--hosted`              | Use the hosted backend via device login (default).                                                |
| `--local`               | Use locally configured API keys; skip hosted login.                                               |
| `--help`                | Show usage.                                                                                       |

Options accept either `--key value` or `--key=value`.

## Notes

- `--output` and `--related` paths must resolve inside the workspace; absolute paths are
  converted to workspace-relative paths.
- Reference audio files in `--related` are sent to the audio agent as context; other related
  files are included as text context.
- Exit codes: `0` success, `1` task failure (or no file written), `2` usage/configuration/login error.