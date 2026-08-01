# Story Pipeline

This document describes the operation pipeline that drives the interactive story
generator. Each operation is defined by a markdown file in `ops/` with YAML
frontmatter describing how it fits into the pipeline, followed by the prompt
body that is sent to the model.

## Frontmatter Fields

Operations are declared using the following frontmatter keys:

- **`specifies`** — Declares that this operation produces/owns a specific,
  fixed output file (e.g. `../story/0.md`). Used for one-off, non-branching
  outputs like the initial story node or the stylesheet.
- **`transforms`** — Declares a mapping (or list of mappings) of the form
  `PATTERN -> REPLACEMENT`, where `PATTERN` is a regex matched against an
  existing file path and `REPLACEMENT` (which may reference capture groups
  like `$1` and template vars like `{{CHOICE}}`) determines the output file
  path. Used for operations that branch off of an existing story node.
- **`template_vars`** — A map of variables (e.g. `CHOICE`, `CHOICE_LABEL`)
  that are substituted into `transforms` patterns and are available for
  interpolation within the prompt body itself.
- **`related`** — A list of additional files that should be supplied as
  context to the model when running this operation, without being the
  direct subject of `specifies`/`transforms`.
- **`task_type`** — Identifies the kind of generation task when it isn't a
  plain text/markdown completion. Recognized values include `GenerateAudio`
  and `GenerateImage`. When omitted, the task is a standard text-generation
  task.

## Operations

### `ops/initial_node.md`
- **Type:** text generation, `specifies: ../story/0.md`
- **Related:** `../story_idea.md`, `../story/world_facts.md`
- Bootstraps the story: introduces the protagonist, setting, and initial
  conflict, and offers three labeled choices (A/B/C). Also produces
  `../story/world_facts.md`, `../story/image_style.md`, and
  `../story/audio_style.md` as companion outputs establishing persistent
  world facts and the visual/audio style guides for the story.

### `ops/initial_image.md`
- **Type:** `GenerateImage`, `transforms: ../story/0.md -> ../story/0.png`
- **Related:** `../story_idea.md`, `../story/image_style.md`
- Generates the cover/opening image for the story's first node.

### `ops/initial_audio.md`
- **Type:** `GenerateAudio`, `transforms: ../story/0.md -> ../story/0.wav`
- **Related:** `../story_idea.md`, `../story/audio_style.md`
- Generates the opening ambient/narration audio for the first node.

### `ops/choice.md`
- **Type:** text generation
- **Template vars:** `CHOICE` (e.g. `a`), `CHOICE_LABEL` (e.g. `A`)
- **Transforms:** `../story/([^./]+)\.md -> ../story/$1{{CHOICE}}.md`
- **Related:** `../story/world_facts.md`
- Core branching operation. Given a parent story node and a chosen option,
  produces the next story node at a deeper path (e.g. `start.md` ->
  `starta.md` -> `startab.md`). Consults `world_facts.md` for consistency
  and updates it with any new persistent facts.
- Implements **End State Guidance**: based on the depth of the resulting
  node and the tone of the choice (reckless, wise, game-breaking, fatal,
  etc.), the node may become a **Triumphant**, **Doom**, or **Bittersweet**
  ending instead of continuing with new choices. Ending nodes omit choice
  options and close with an epitaph/closing line.
- Produces two file outputs: the new story node and the updated
  `world_facts.md`.

### `ops/choice_image.md`
- **Type:** `GenerateImage`
- **Template vars:** `CHOICE`
- **Transforms (list):**
  - `../story/([^./]+){{CHOICE}}\.md -> ../story/$1{{CHOICE}}.png`
  - `../story/([^./]+)\.png -> ../story/$1{{CHOICE}}.png`
- **Related:** `../story/image_style.md`
- Generates an illustration for a newly created choice node, deriving style
  either from the parent node's markdown or from the parent node's own
  image (chained continuity), guided by `image_style.md`.

### `ops/choice_audio.md`
- **Type:** `GenerateAudio`
- **Template vars:** `CHOICE`
- **Transforms:** `../story/([^./]+){{CHOICE}}\.md -> ../story/$1{{CHOICE}}.wav`
- **Related:** `../story/audio_style.md`
- Generates ambient/narration audio for a newly created choice node,
  guided by `audio_style.md`.

### `ops/update_stylesheet.md`
- **Type:** text/code generation, `specifies: ../style.css`
- **Related:** `../story/0.md`, `../story_idea.md`,
  `../stylesheet_instructions.md`
- Updates the application's `style.css` to implement visual changes
  requested by the user, while adhering to the project's established
  design language (color palette, border radii, typography, responsive
  breakpoints, immersive-mode overrides, accessibility, and animation
  conventions) — always deferring to explicit user intent, including
  unconventional or experimental requests.

## Pipeline Flow Summary

1. **Initialization** — `initial_node.md` creates `story/0.md` plus the
   world/style bibles (`world_facts.md`, `image_style.md`,
   `audio_style.md`).
2. **Media for root node** — `initial_image.md` and `initial_audio.md`
   generate `story/0.png` and `story/0.wav`.
3. **Branching** — For each choice (A/B/C) presented on a node, `choice.md`
   is invoked with `CHOICE`/`CHOICE_LABEL` template vars to produce the
   next node (text), which may be a continuation or an ending.
4. **Media for branch node** — `choice_image.md` and `choice_audio.md` are
   invoked with the same `CHOICE` var to produce matching image/audio
   assets for the newly created node.
5. **Styling** — `update_stylesheet.md` can be run at any time,
   independent of story progression, to evolve `style.css`.

This flow repeats recursively: each generated node's filename encodes its
path from the root (e.g. `0.md` -> `0a.md` -> `0ab.md` -> `0abc.md`),
with depth used by `choice.md` to weight the probability and type of
story endings.