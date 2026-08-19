---
transforms:
  - (../[^\./]+)/([^\./]+.md) -> $1/README.md
  - (../[^\./]+)/(README.md) -> ../README.md 
---

# Task: Directory Index Page (README.md)

You are producing a **README.md** index page for a directory of already-generated Cognotik documentation pages. This op
runs in two contexts, driven by which transform rule matched:

1. **Category index** — every other `*.md` page inside one category directory (e.g. `../models/*.md`,
   `../modules/*.md`, `../patchers/*.md`, `../task-types/*.md`, `../cognitive-mode/*.md`, `../apps/*.md`,
   `../devtools/*.md`, `../ways-to-run/*.md`) is folded into that directory's own `README.md`.
2. **Site index** — every category directory's `README.md` (produced by step 1) is folded into the top-level
   `docs/site/README.md`, giving visitors a single entry point into the whole site. The audience is a visitor who has
   landed in a folder (or at the site root) and needs to quickly see what's inside and pick where to go next — this page
   is a table of contents, not new content.

## Goal

For the directory being indexed, produce a page that:

1. **Names the section** — an H1 naming the directory/category (e.g. "Supported Models", "Modules", "Patchers",
   "Cognotik Documentation" for the root case).
2. **One-line orientation** — a single sentence under the H1 explaining what kind of pages live in this directory and
   why a visitor would want to browse them.
3. **Lists every sibling page** — one entry per source page, each as a Markdown link to that page's filename, with a
   short (one sentence or less) description pulled from that page's own title/tagline/subtitle — never re-summarized
   from scratch or invented.
4. **Groups entries only if the pages themselves suggest a natural grouping** (e.g. category badges already present on
   Dev Tool or Task-Type pages); otherwise list entries alphabetically by display name.

## Style Guide

* **Tone:** Plain and navigational — this page's only job is to get the reader to the right sub-page quickly.
* **Format:** Valid Markdown:
    * Single `#` H1 title + one-line intro sentence.
    * A flat or lightly-grouped bullet list, one bullet per linked page:
      `- [Page Title](./filename.md) — one-line description.`
    * If grouping by category, use `##` subheadings for each group, each still followed by a flat bullet list.
* **Links:** Always relative to the directory containing the generated `README.md` (e.g. `./anthropic.md`, not an
  absolute path), and must point at the actual filename of the source page being indexed.

## Source-to-Output Mapping

| Source Signal (from each sibling `*.md` page)        | Output Section / Element        |
|------------------------------------------------------|---------------------------------|
| Page's H1 title                                      | Link text                       |
| Page's italic tagline / one-line subtitle            | Bullet description              |
| Page's filename                                      | Link target                     |
| Category directory name                              | H1 title (pluralized/humanized) |
| (Root case) Each category directory's `README.md` H1 | Bullet entry for that category  |

## Constraints

* Never invent a description for a linked page beyond what its own title/tagline already states — if a page has no

---

clear one-line summary, use its H1 title alone as the link text with no added description.

* Do not duplicate the full content of any linked page here — this is strictly an index/table of contents.
* Do not link to a page that doesn't exist among the matched source files, and don't omit one that does.
* Keep the page short enough to scan in a few seconds even as the number of entries grows — resist the urge to add
  editorial commentary per entry beyond the one-line description.