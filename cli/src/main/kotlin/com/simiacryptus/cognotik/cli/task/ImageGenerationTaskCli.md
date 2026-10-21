# ImageGenerationTaskCli

Command line wrapper for `ImageGenerationTask` (task type `GenerateImage`).
Launch it via `cli/bin/cognotik imagegen ...` (alias: `image`).

## Usage

    cognotik imagegen --output <file> (--prompt <text> | --prompt-file <file>) [options]

| Option                                           | Description                                                                               |
|--------------------------------------------------|-------------------------------------------------------------------------------------------|
| `--output <file>`                                | Output image (.png, .jpg, .jpeg). Relative paths resolve against `--workspace`.           |
| `--prompt <text>`                                | Image description: subject, style, composition, colors, mood.                             |
| `--prompt-file <file>`                           | Read the description from a file.                                                         |
| `--reference <a,b,...>`                          | Comma-separated reference images used as visual context.                                  |
| `--context <a,b,...>`                            | Comma-separated extra (non-image) context files.                                          |
| `--workspace <dir>`                              | Base directory for relative paths (default `.`).                                          |
| `--timeout-minutes <n>`                          | Harness timeout (default 30).                                                             |
| `--smart-model`, `--fast-model`, `--image-model` | Model overrides (defaults: GeminiFlash_38, GeminiFlash_38, GeminiFlash_31_Image_Preview). |
| `--hosted` / `--local`                           | Hosted backend via device login (default) or local API keys.                              |
| `--help`                                         | Show help.                                                                                |

## Exit codes

* `0` - image written
* `1` - task failed or no output file produced
* `2` - invalid arguments or login/bootstrap failure

## Example

    cognotik imagegen --output assets/logo.png \
      --prompt "Minimalist flat logo of a fox made of circuit traces, teal on dark navy"