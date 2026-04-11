---
documents: 
  - ../Philosophical_Calculator.md
  - ../../../../desktop/src/main/resources/apps/philosophical-calculator/README.md
---

# Philosophical Calculator Demo

This file records information specific to the Philosophical Calculator demo.
The `planscripts/` files contain the script for the demo and are used to generate the video.

## App Overview

The Philosophical Calculator is a multi-perspective analytical toolkit that transforms content through various philosophical, rhetorical, and creative lenses. It takes input content (notes, articles, essays) and processes it through a rich set of analytical operations to deepen understanding, strengthen arguments, and explore ideas from multiple angles.

## Demo Pipeline

The demo walkthrough follows this sequence:

1. **Input** — Supply a simple prompt (the app also supports file uploads via `notes/*`)
2. **Model Selection** — Select models (e.g., Haiku for text, Gemini Flash Image for illustrations)
3. **Draft Article** — Skip summarization (input is small) and go straight to drafting via `draft_article_op`, producing `content.md`
4. **Lenses** — Run one or more analytical lenses on the draft article (demo uses **Perspective Analysis**)
5. **Illustrate Article** — Generate AI images and weave them into the article

## Available Operations (Lenses)

The app provides 12 analytical operations, each producing a dedicated output file:

| Operation                 | Output                     | Description                                                                      |
|---------------------------|----------------------------|----------------------------------------------------------------------------------|
| **Summarize**             | `summary.md`               | Thematic/conceptual distillation of notes into a structured summary              |
| **Draft Article**         | `content.md`               | Full article or essay based on instructions, summary, and notes                  |
| **Update Article**        | `content.md`               | Synthesizes insights from all analytical outputs back into the main content      |
| **Brainstorm**            | `brainstorm.md`            | Divergent ideation — generates broad ideas, extensions, and applications         |
| **Dialectical**           | `dialectical.md`           | Thesis/antithesis/synthesis analysis of core themes                              |
| **Socratic**              | `socratic.md`              | Transforms content into a rigorous Socratic dialogue                             |
| **Perspectives**          | `perspectives.md`          | Multi-stakeholder perspective analysis                                           |
| **Persuasive**            | `persuasive.md`            | Reframes content as a compelling persuasive essay                                |
| **Game Theory**           | `gametheory.md`            | Analyzes concepts through game theory (players, strategies, payoffs, equilibria) |
| **Narrative**             | `narrative.md`             | Dramatizes the content with illustrations                                        |
| **Comic**                 | `comic.md`                 | Generates a comic book representation of the content                             |
| **Technical Explanation** | `technical_explanation.md` | Precise, in-depth technical breakdown with examples and definitions              |

Additionally, the **Illustrate Article** feature generates AI images based on the content and integrates them directly into the article with image links.

## Key Demo Points

- **Session monitoring**: Lens operations run as multi-step reasoning processes with real-time session monitoring via session links in the UI
- **Usage tracking**: Token usage and cost can be monitored in the "Usage" tab during processing
- **Parallel execution**: Multiple lenses can be run in parallel depending on need
- **Lens output structure**: Lenses produce detailed analysis followed by a Synthesis and Recommendations section
- **Update Article**: After running lenses, `update_article_op` folds insights, new ideas, and corrections from all lens outputs back into the main article
- **Illustrate Article**: Designs illustrations, generates each image via an image-capable model, then edits the document to add image links

## Demo Walkthrough Timestamps

| Timestamp | Section                        |
|-----------|--------------------------------|
| 0:00      | Introduction and input setup   |
| 0:34      | Model selection and pipeline   |
| 1:22      | Draft article complete         |
| 1:34      | Lenses: multi-perspective run  |
| 2:55      | Monitoring usage               |
| 4:54      | Reviewing lens results         |
| 6:03      | Illustrate article             |
| 8:12      | Final illustrated result       |

## Demo Example

The walkthrough uses a simple prompt to generate a fully illustrated guide to Connect Four, demonstrating the complete pipeline from input through analysis to illustrated output at a total cost of approximately $0.62.