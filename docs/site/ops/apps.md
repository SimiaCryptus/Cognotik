---
transforms:
  - ../../../desktop/src/main/resources/apps/(.*)/README\.md -> ../apps/$1.md
---

# Task: DocOps App Showcase Page

You are transforming an individual **DocOps App's `README.md`** (already written in a friendly, non-technical tone
per `docs/apps/app_readme.md`) into a polished, public **showcase page** for the Cognotik website's app gallery. The
audience is a curious visitor browsing available apps, deciding which one to try first.

## Goal

Produce a page that:

1. **Hooks the reader** with an H1 title (the app's display name) and a one-line tagline capturing the app's core
   value proposition (adapt/tighten the README's intro rather than copying it verbatim).
2. **Explains the experience** — a short "What it does" section describing the input the user provides and the
   output they receive, written for someone who has never used an AI pipeline tool before.
3. **Shows it in action** — a "How to Use It" walkthrough as a numbered list (3–5 steps max), derived from any
   usage instructions in the README.
4. **Highlights the payoff** — a bulleted "Why You'll Love It" section translating technical capabilities into
   user benefits (e.g. "consistent characters across every comic panel" rather than "maintains character embedding
   state across pipeline stages").
5. **Visual proof (if available)** — if the app directory contains `icon.png` and/or `background.png`, reference
   them as a small header image and page background respectively; if a `PIPELINE.md` exists, summarize its stages
   as a simple "Behind the Scenes" list (kept brief — this is a showcase, not technical documentation).

## Style Guide

* **Tone:** Friendly, light marketing voice — enthusiastic but honest, matching the tone already established in the
  app's own README. Avoid technical jargon (no "pipeline stages", "actors", "orchestrator" unless simplified, e.g.
  "AI steps working together behind the scenes").
* **Format:** Valid Markdown:
    * H1 app title with icon emoji if one is known from `apps.json` (see `docs/apps/app_json.md`).
    * `##` sections: What It Does, How to Use It, Why You'll Love It, and (optionally) Behind the Scenes.
    * Numbered lists for step-by-step usage; bullet lists for benefits.
    * Reference images with standard Markdown image syntax (`![App Icon](icon.png)`), only when the asset exists.

## Source-to-Output Mapping

| Source Signal                                             | Output Section          |
|-------------------------------------------------------------|--------------------------|
| README title/intro paragraph                                | H1 Title + Tagline       |
| README "how to use" / usage instructions                    | How to Use It            |
| README benefits / value statements                          | Why You'll Love It       |
| `icon.png` / `background.png` (if present)                  | Visual header/background |
| `PIPELINE.md` stage descriptions (if present)                | Behind the Scenes        |

## Constraints

* Do not introduce features the README does not describe — this page advertises what the app *actually* does.
* Keep the page short and skimmable (aim for under one minute of reading); this is a gallery card expanded into a
  page, not a full manual.
* If no visual assets exist, omit the Visual Proof section entirely rather than describing a placeholder image.