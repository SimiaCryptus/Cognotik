# 📚 Comic Serial Generator

Turn your ideas into an ongoing comic book series — one episode at a time.

## Overview

The Comic Serial Generator is a web application that transforms story ideas, articles, or concepts into a serialized comic book series. Each episode builds on the previous one, maintaining consistent characters, settings, and narrative arcs throughout the series.

## How It Works

The app is organized into three main sections:

### 💡 Idea
Write or paste your article, story idea, or concept. This serves as the foundation for the first episode and guides the tone of all sequels.

### ⚙️ Pipeline
The generation pipeline has three modes:

1. **Generate First Comic** — Creates the first episode from your original idea, establishing characters, setting, art style, and narrative arc.
2. **Generate Next Episode** — Continues the story by generating a sequel that builds on the previous episode while staying true to the original idea.
3. **Batch Generation** — Generates multiple episodes in sequence automatically. The pipeline will create the first comic (if needed) and then generate the requested number of sequels.

### 📖 Series
Browse all generated episodes in an expandable accordion view. Episodes can be read individually, and new episodes can be generated directly from this tab.

## File Structure

```
comic-serial/
├── app.html              # Main application UI
├── app.js                # Application logic (navigation, file I/O, pipeline control)
├── style.css             # Application styles
├── marked.min.js         # Markdown rendering library
├── idea.md               # User's story idea/concept (created at runtime)
├── comic_1.md            # First generated comic episode (created at runtime)
├── comic_2.md            # Second episode (created at runtime)
├── comic_N.md            # Nth episode (created at runtime)
├── ops/
│   ├── comic_op.md       # DocOp: transforms idea.md → comic_1.md
│   └── sequel_op.md      # DocOp: transforms comic_N.md → comic_N+1.md
└── README.md             # This file
```

## Operations

### `ops/comic_op.md`
Transforms `idea.md` into `comic_1.md`. This is the initial comic generation step that establishes the series foundation.

### `ops/sequel_op.md`
Transforms `comic_N.md` into `comic_N+1.md` using a regex-based pattern (`comic_(\d+)\.md → comic_$1+1.md`). It also references `idea.md` to maintain thematic consistency across episodes. Both operations use the `ComicBookGeneration` task type.

## Features

- **Auto-save** — The idea is automatically saved before any generation operation
- **Live monitoring** — Track generation progress via session monitor links
- **Status polling** — Badges update in real-time to reflect task status (pending, running, done, error)
- **Batch processing** — Generate an entire series with a single click
- **Expandable reader** — Browse episodes in the Series tab with collapsible cards
- **Markdown rendering** — All comic content is rendered from Markdown using `marked.js`