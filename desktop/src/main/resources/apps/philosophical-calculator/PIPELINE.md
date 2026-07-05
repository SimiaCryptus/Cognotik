# Pipeline

This document describes the available operations (ops) and their transformation pipeline for content processing.

## Overview

Each operation reads from source files and produces a specified output file. Operations are defined in the `ops/` directory and follow a consistent pattern:

- `specifies`: the output file this op generates
- `related`: input files used during the operation
- `task_type`: the category of transformation

---

## Operations

### `summarize_op`
- **Output:** `summary.md`
- **Inputs:** `notes/*.*`, `content.md`
- Extracts and distills key ideas, concepts, and insights from notes into a thematic summary.
- If `content.md` exists, compares it against notes and adds missing insights.

---

### `draft_article_op`
- **Output:** `content.md`
- **Inputs:** `instruct.md`, `summary.md`, `notes/*.*`
- Writes the full article based on instructions and summary.
- Prioritizes `instruct.md` directives if present.

---

### `update_article_op`
- **Output:** `content.md`
- **Inputs:** `instruct.md`, `summary.md`, `brainstorm.md`, `notes/*.*`, `dialectical.md`, `gametheory.md`, `narrative.md`, `perspectives.md`, `persuasive.md`, `socratic.md`, `statemachine.md`, `web_research.md`
- Updates and enriches `content.md` by synthesizing insights from all auxiliary analyses.
- Weaves new content naturally into the existing narrative.

---

### `brainstorm_op`
- **Output:** `brainstorm.md`
- **Inputs:** `content.md`
- Generates a broad, divergent set of ideas organized into thematic clusters.
- Flags the most promising or surprising ideas.

---

### `dialectical_op`
- **Output:** `dialectical.md`
- **Inputs:** `content.md`
- Analyzes core themes using thesis → antithesis → synthesis.
- Resolves contradictions into higher-level understanding.

---

### `gametheory_op`
- **Output:** `gametheory.md`
- **Inputs:** `content.md`
- Analyzes concepts through game theory: players, strategies, payoffs, equilibria.
- Uses markdown tables for payoff matrices where applicable.

---

### `perspectives_op`
- **Output:** `perspectives.md`
- **Inputs:** `content.md`
- Analyzes content from multiple stakeholder perspectives.
- Highlights differing priorities, concerns, and insights.

---

### `persuasive_op`
- **Output:** `persuasive.md`
- **Inputs:** `content.md`
- Reframes content as a compelling persuasive essay using rhetorical techniques.

---

### `socratic_op`
- **Output:** `socratic.md`
- **Inputs:** `content.md`
- Transforms content into a Socratic dialogue exploring underlying assumptions.
- Exposes contradictions and strengthens core arguments.

---

### `narrative_op`
- **Output:** `narrative.md`
- **Inputs:** `content.md`
- Dramatizes the content with inline illustrations.

---

### `technical_explanation_op`
- **Output:** `technical_explanation.md`
- **Inputs:** `content.md`
- Produces a precise, in-depth technical explanation.
- Defines key terms, breaks down mechanisms, includes code/pseudocode examples.

---

### `illustration_op`
- **Output:** `content.md` (illustrated)
- Adds inline illustrations to the document to enhance narrative and explain complex concepts.

---

### `comic_op`
- **Output:** `comic.md`
- **Inputs:** `content.md`
- Generates a comic book representation of the article.

---

### `webpage_op`
- **Output:** `page.html`
- **Inputs:** `content.md`
- Generates an HTML page representing the article.

---

## Recommended Workflow

```
notes/*.*
    │
    ▼
summarize_op ──► summary.md
    │
    ▼
draft_article_op ──► content.md
    │
    ├──► brainstorm_op    ──► brainstorm.md
    ├──► dialectical_op   ──► dialectical.md
    ├──► gametheory_op    ──► gametheory.md
    ├──► perspectives_op  ──► perspectives.md
    ├──► persuasive_op    ──► persuasive.md
    ├──► socratic_op      ──► socratic.md
    ├──► narrative_op     ──► narrative.md
    ├──► technical_explanation_op ──► technical_explanation.md
    ├──► comic_op         ──► comic.md
    └──► webpage_op       ──► page.html
         │
         ▼ (all auxiliary outputs feed back in)
    update_article_op ──► content.md (enriched)
         │
         ▼
    illustration_op ──► content.md (illustrated)
```