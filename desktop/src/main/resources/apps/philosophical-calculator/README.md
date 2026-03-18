# Philosophical Calculator

A multi-perspective analytical toolkit that transforms content through various philosophical, rhetorical, and creative
lenses.

## Overview

The Philosophical Calculator takes input content (notes, articles, essays) and processes it through a rich set of
analytical operations to deepen understanding, strengthen arguments, and explore ideas from multiple angles.

## Operations

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

## Workflow

```
notes.* ──► summarize_op ──► summary.md
                                  │
                                  ▼
              instruct.md ──► draft_article_op ──► content.md
                                                      │
                    ┌─────────────────────────────────┤
                    ▼                                  ▼
             brainstorm_op                     dialectical_op
             socratic_op                       perspectives_op
             persuasive_op                     gametheory_op
             narrative_op                      comic_op
             technical_explanation_op
                    │                                  │
                    └─────────────┬────────────────────┘
                                  ▼
                          update_article_op ──► content.md (enhanced)
```

### Typical Usage

1. **Start with notes** — Upload raw notes, transcripts, or ideas to `notes/*`; supports text, docx, pdx, etc
2. **Summarize** — Run `summarize_op` to distill key themes into `summary.md`
3. **Draft** — Run `draft_article_op` to produce a polished first draft in `content.md`
4. **Analyze** — Run any combination of analytical operations (dialectical, socratic, game theory, etc.) to explore the
   content from different angles
5. **Update** — Run `update_article_op` to weave analytical insights back into the main article

## Input Files

| File          | Purpose                                                               |
|---------------|-----------------------------------------------------------------------|
| `notes/*`    | Raw notes, transcripts, or source material (canonical/authoritative). Supports file upload via drag-and-drop or file picker.  |
| `instruct.md` | Structural and thematic directives for article generation             |
| `content.md`  | The main article — both an output and an input for further refinement |

## Design Principles

- **Notes are canonical** — The raw notes are always the authoritative source of truth
- **Iterative refinement** — Content improves through repeated analysis and synthesis cycles
- **Non-destructive integration** — Updates weave new insights naturally rather than appending
- **Voice preservation** — The original tone and core message are maintained through transformations