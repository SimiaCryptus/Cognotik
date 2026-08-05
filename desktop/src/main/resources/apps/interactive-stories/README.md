# Interactive Stories

Craft branching, choose-your-own-adventure narratives with AI-generated prose,
illustrations and narration.

## Files

```
interactive-stories/
├── app.html   # single entry point
├── app.js     # single ES-module entry point
├── style.css  # all styling (design-token based)
├── ops/       # DocOp definitions
└── README.md
```

## Pipeline

| Step | Badge              | Input                        | Op                          | Output                  |
|------|--------------------|------------------------------|-----------------------------|-------------------------|
| 1    | `badge-idea`       | user text                    | — (direct write)            | `story_idea.md`         |
| 2    | `badge-tree`       | `story_idea.md`              | `ops/initial_node.md`       | `story/0.md`            |
| 3    | `badge-node`       | parent node + chosen letter  | `ops/choice.md`             | `story/<path><a\|b\|c>.md` |
| 3a   | —                  | `story/0.md`                 | `ops/initial_image.md`      | `story/0.png`           |
| 3b   | —                  | `story/<path>.md`            | `ops/choice_image.md`       | `story/<path>.png`      |
| 3c   | —                  | `story/0.md`                 | `ops/initial_audio.md`      | `story/0.wav`           |
| 3d   | —                  | `story/<path>.md`            | `ops/choice_audio.md`       | `story/<path>.wav`      |
| 4    | `badge-stylesheet` | `stylesheet_instructions.md` | `ops/update_stylesheet.md`  | `style.css`             |

### Node naming

Story nodes live in `story/` and are named by their choice path:

* `0`   — the root node
* `0a`  — after choosing **A** at the root
* `0ab` — after choosing **B** at `0a`

Illustrations and narration for a node share its id with a `.png` / `.wav`
extension. A node with no parsable `A/B/C` list is treated as an **end state**
and renders the "The story has reached its end" panel instead of choice buttons.

### Template variables

`ops/choice.md`, `ops/choice_image.md` and `ops/choice_audio.md` receive:

* `CHOICE` — the lowercase branch letter (`a`, `b`, `c`)
* `CHOICE_LABEL` — the uppercase branch letter (narrative op only)

## Op files

| File                        | Purpose                                                        |
|-----------------------------|----------------------------------------------------------------|
| `ops/initial_node.md`       | Turn `story_idea.md` into the opening scene plus three choices. |
| `ops/choice.md`             | Continue the story along the `CHOICE` branch of the parent node.|
| `ops/initial_image.md`      | Illustrate the root node.                                       |
| `ops/choice_image.md`       | Illustrate the `CHOICE` branch node.                            |
| `ops/initial_audio.md`      | Narrate the root node to `story/0.wav`.                         |
| `ops/choice_audio.md`       | Narrate the `CHOICE` branch node.                               |
| `ops/update_stylesheet.md`  | Rewrite `style.css` from `stylesheet_instructions.md`.          |

## Local preferences (`localStorage`)

All keys are namespaced `interactiveStories.*`:

* `autoRead`, `autoImage`, `autoAudio`, `highlightReadalong` — toolbar toggles
* `voice` — selected browser TTS voice URI
* model selections via `saveModelSelections()` / `loadModelSelections()`

No story content is ever stored in `localStorage`; the filesystem is the source
of truth and badges are restored from `docops.status.json` on load.

## Read-aloud

Two independent mechanisms:

1. **Browser TTS** (`speechSynthesis`) with sentence-level read-along
   highlighting. Falls back to a timer-driven highlight when the browser or
   voice does not emit `boundary` events.
2. **Generated narration** (`story/<id>.wav`) which takes precedence over TTS
   when present and auto-read is enabled.

Both respect browser autoplay policy: playback is deferred until the first user
gesture, and the Read Aloud button shows "Click to Read" while deferred.

## Keyboard

| Key      | Action                                     |
|----------|--------------------------------------------|
| `F`      | Toggle immersive mode (when not in a field) |
| `Escape` | Exit immersive mode / dismiss confirmation  |

## Conformance

| Axis           | Status | Note |
|----------------|:------:|------|
| 3-file layout  |   ✅   |      |
| Modern JS      |   ✅   |      |
| Menubar        |   ✅   | `initMenu({ appName: 'Interactive Stories' })` |
| No dup. chrome |   ✅   | Usage/Git/Sessions/Download owned by the menubar |
| Viewport       |   ✅   |      |
| Mobile         |   ✅   | Verified at 360 / 768 / 1280 px |

### Outstanding

* The end-state panel is still injected with `innerHTML` from a static template
  literal. It contains no model- or user-supplied data, but should move to a
  `<template>` element when `app.html` is next touched.