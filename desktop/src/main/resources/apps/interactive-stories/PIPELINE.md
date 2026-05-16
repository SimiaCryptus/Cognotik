# Pipeline

This document describes the operational pipeline for the interactive story application. Each stage defines how story content, images, audio, and styles are generated and connected.

---

## Stage 1 — Story Initialization

**Op:** `ops/initial_node.md`
**Output:** `story/0.md`, `story/world_facts.md`, `story/image_style.md`, `story/audio_style.md`
**Inputs:** `story_idea.md`

Creates the opening story node, establishing the main character, setting, and central conflict. Presents the player with three initial choices (A, B, C). Also initializes the world facts registry and defines the visual and audio style guides used by all subsequent generation stages.

---

## Stage 2 — Initial Media Generation

These two operations run after `story/0.md` is created.

### 2a — Initial Image

**Op:** `ops/initial_image.md`
**Transform:** `story/0.md` → `story/0.png`
**Task type:** `GenerateImage`
**Inputs:** `story_idea.md`, `story/image_style.md`

Generates the illustration for the opening story node.

### 2b — Initial Audio

**Op:** `ops/initial_audio.md`
**Transform:** `story/0.md` → `story/0.wav`
**Task type:** `GenerateAudio`
**Inputs:** `story_idea.md`, `story/audio_style.md`

Generates the ambient audio for the opening story node.

---

## Stage 3 — Choice Node Generation

**Op:** `ops/choice.md`
**Transform:** `story/([^./]+)\.md` → `story/$1{CHOICE}.md`
**Template vars:** `CHOICE` (e.g. `a`, `b`, `c`), `CHOICE_LABEL` (e.g. `A`, `B`, `C`)
**Inputs:** `story/world_facts.md`

Generates the next story node for a given player choice. The output filename is the parent node's name suffixed with the choice letter (e.g. `story/0.md` + choice `a` → `story/0a.md`).

Each generated node:
- Advances the narrative based on the chosen path.
- Consults `world_facts.md` for consistency.
- Updates `world_facts.md` with any new persistent facts (characters, locations, factions, world rules).
- Either presents three new choices (A, B, C) **or** concludes the story with a typed ending (Triumphant, Doom, or Bittersweet), based on node depth and narrative tone.

### Ending probability by depth

| Node depth | Base end probability |
|------------|----------------------|
| 0–2        | Continuation         |
| 3–4        | ~15%                 |
| 5–6        | ~35%                 |
| 7–8        | ~60%                 |
| 9+         | ~85%                 |

Modifiers: aggressive/reckless choice +25%; wise/diplomatic/heroic choice +10% (skewed toward triumph). Game-breaking status forces a triumphant ending; catastrophic loss forces a doom ending.

---

## Stage 4 — Choice Media Generation

These two operations run after each choice node `.md` file is created.

### 4a — Choice Image

**Op:** `ops/choice_image.md`
**Transforms:**
- `story/([^./]+){CHOICE}\.md` → `story/$1{CHOICE}.png`
- `story/([^./]+)\.png` → `story/$1{CHOICE}.png`
**Task type:** `GenerateImage`
**Inputs:** `story/image_style.md`

Generates the illustration for the new choice node, inheriting visual style from the style guide.

### 4b — Choice Audio

**Op:** `ops/choice_audio.md`
**Transform:** `story/([^./]+){CHOICE}\.md` → `story/$1{CHOICE}.wav`
**Task type:** `GenerateAudio`
**Inputs:** `story/audio_style.md`

Generates the ambient audio for the new choice node.

---

## Stage 5 — Stylesheet Updates

**Op:** `ops/update_stylesheet.md`
**Output:** `style.css`
**Inputs:** `story/0.md`, `story_idea.md`, `stylesheet_instructions.md`

Updates the application stylesheet to match the story's visual identity. Follows the established design language (indigo/purple palette, system fonts, responsive breakpoints, immersive-mode overrides) while deferring fully to explicit user intent for creative or unconventional styling.

---

## File Dependency Graph

```
story_idea.md
    │
    ▼
ops/initial_node.md
    ├──▶ story/0.md ──────────────────────────────────────────────┐
    ├──▶ story/world_facts.md                                      │
    ├──▶ story/image_style.md ──▶ ops/initial_image.md ──▶ story/0.png
    └──▶ story/audio_style.md ──▶ ops/initial_audio.md ──▶ story/0.wav
                                                                   │
    story/0.md + choice (a|b|c)                                    │
         │                                                         │
         ▼                                                         │
    ops/choice.md                                                  │
         ├──▶ story/0{a|b|c}.md ◀─────────────────────────────────┘
         │        ├──▶ ops/choice_image.md ──▶ story/0{a|b|c}.png
         │        └──▶ ops/choice_audio.md ──▶ story/0{a|b|c}.wav
         └──▶ story/world_facts.md (updated)

    (repeats recursively for each subsequent choice node)

story_idea.md + story/0.md
    │
    ▼
ops/update_stylesheet.md ──▶ style.css
```

---

## Naming Conventions

| Pattern                | Description                                            |
|------------------------|--------------------------------------------------------|
| `story/0.md`           | Root/opening node                                      |
| `story/0a.md`          | Depth-1 node reached via choice A from node `0`        |
| `story/0ab.md`         | Depth-2 node reached via choice B from node `0a`       |
| `story/0abc.md`        | Depth-3 node, and so on                                |
| `story/*.png`          | Illustration for the corresponding `.md` node          |
| `story/*.wav`          | Audio for the corresponding `.md` node                 |
| `story/world_facts.md` | Persistent universe facts registry (updated each node) |
| `story/image_style.md` | Visual style guide for image generation                |
| `story/audio_style.md` | Sonic style guide for audio generation                 |