# 🎨 Comic Book Generator

## Overview

The Comic Book Generator is one of the most entertaining apps available in Cognotik. It generates a complete series of comic books based on a simple text prompt, producing character reference images, multi-frame comic pages, and a polished HTML presentation — all powered by AI.

## Demo Walkthrough

### Setting Up the Prompt

The demo begins by entering a creative prompt for the comic:

> **"Rude parakeets that will not stop"**

This whimsical premise drives the entire comic generation pipeline.

### Configuring AI Models

Before generating, the appropriate AI models are selected:

- **Text Model:** Gemini Flash 3 Preview — handles script generation, dialogue, and narrative structure
- **Image Model:** Gemini Flash 1 Image Preview — generates character art and comic panel illustrations

Model settings are saved before proceeding.

### Understanding the Serial Format

The app is called **Comic Serial** because it supports an iterative workflow:

1. **Generate a first comic book** — a complete issue with multiple frames and pages
2. **Generate sequels** — continue the story with additional issues, as many as desired
3. **Render to HTML** — compile the comic into a polished, browsable HTML format

## Generation Pipeline

### Step 1: Script Generation

The system first sketches out a script for the comic, establishing the narrative arc, scenes, and dialogue.

### Step 2: Character Reference Images

Before rendering any comic pages, the generator creates **character reference images**. These reference images ensure visual consistency — the same characters maintain their appearance across all panels and pages throughout the comic.

### Step 3: Page Generation

With the script and character references in place, the generator produces the actual comic pages frame by frame. Each page contains illustrated panels with dialogue and narration. The demo's comic even concludes with a fourth-wall-breaking joke.

### Step 4: HTML Rendering

Once all pages are generated, the final step renders the comic book into a structured **HTML format**. This process:

- Reuses the same generated images from the comic panels
- Wraps them in an attractive HTML layout
- Places textual dialogue alongside the illustrations
- Completes quickly after the main generation is done

## Viewing the Result

The rendered HTML comic can be opened in a new browser tab for preview. The final presentation includes:

- **Character reference images** displayed at the top for context
- **Comic pages** with panels arranged in a visually appealing layout
- **Dialogue text** presented alongside each panel for readability
- A polished, shareable format suitable for browsing

## Key Takeaways

- The Comic Book Generator transforms a simple text prompt into a full multi-page comic book
- AI models handle both the narrative scripting and visual illustration
- Character reference images maintain visual consistency across the entire comic
- The serial format supports ongoing story continuation through sequels
- Final HTML rendering produces an attractive, self-contained presentation