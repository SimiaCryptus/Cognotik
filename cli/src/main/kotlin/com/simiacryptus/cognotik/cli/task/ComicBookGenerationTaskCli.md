# ComicBookGenerationTaskCli

Command line wrapper for `ComicBookGenerationTask`. It generates a comic book script (pages / rows / frames) and, unless
disabled, character sheets and one image per row.

## Running

```bash
cognotik comic --description "A robot learns to paint" --pages 3 --art-style manga
```

Options may be given as `--key value` or `--key=value`.

| Option                                           | Description                                                        |
|--------------------------------------------------|--------------------------------------------------------------------|
| `--description <text>`                           | Subject or scenario (required unless `--description-file` is used) |
| `--description-file <path>`                      | Read the subject from a file                                       |
| `--pages <n>`                                    | Target page count, 1-100 (default 5)                               |
| `--art-style <style>`                            | Art style (default `western superhero`)                            |
| `--style-details <text>`                         | Extra visual guidelines                                            |
| `--no-images`                                    | Script only; skip image generation                                 |
| `--characters-file <path>`                       | JSON array of character references                                 |
| `--plot-file <path>`                             | JSON object of plot continuity details                             |
| `--timeout-minutes <n>`                          | Harness timeout (default 60)                                       |
| `--smart-model`, `--fast-model`, `--image-model` | Model overrides                                                    |
| `--hosted` / `--local`                           | Hosted backend via device login (default) or local API keys        |
| `--help`                                         | Show usage                                                         |

## Character references file

```json
[
  {
    "character_name": "Robo",
    "reference_image_path": "robo.png",
    "visual_description": "Small rusty robot with one blue eye",
    "personality_notes": "Curious and shy",
    "role": "protagonist"
  }
]
```

Relative `reference_image_path` values are resolved against the directory of the JSON file.

## Plot continuity file

```json
{
  "narrative_structure": "three-act structure",
  "key_plot_points": ["Robo finds a brush", "First painting fails", "Robo paints a sunrise"],
  "tone": "lighthearted",
  "themes": ["creativity"]
}
```

## Output

Artifacts (script JSON, character images, page/row images, transcript) are written to the
harness workspace under the current working directory.