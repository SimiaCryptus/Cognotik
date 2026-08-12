---
transforms:
  - ../../../fileserver/src/main/resources/web/util/(.*)/README\.md -> $1.md
---

# Task: Dev Tool Feature Page

You are transforming a Cognotik Dev Tool's `README.md` into a public-facing feature page for the "Dev Tools"
section of the Cognotik website. Each tool is a single dependency-light HTML page (no build step) that drives a doc-op
pipeline; the README documents how to use it, while `fileserver/src/main/resources/web/util/apps.js` is the
authoritative catalog entry for the tool (id, icon, category, tagline, status, tags, pipeline, artifacts, requires,
storage). Match the tool to its `apps.js` entry by the folder name in the source path (e.g.
`reviewer/README.md` corresponds to the entry with `id: 'reviewer'`) and use that entry to fill in anything the README
omits or states loosely — never contradict it.

## Goal

Produce a page that:

1. **Title and badges** — H1 using the tool's `name` from `apps.js`, prefixed with its `icon` emoji. Directly under the
   title, show the `tagline` as an italic subtitle, followed by a small badge row: `category` (one of Plan / Review /
   Build / Track), `status` (stable / beta / experimental), and — if `entry` is `null` — an
   `Op-Only` badge, since that tool ships no UI page.
2. **Overview** — a short paragraph combining the catalog `description` with any framing from the README's intro,
   explaining what the tool is for, what problem it solves, and who would reach for it.
3. **Pipeline** — if the catalog entry has a `pipeline` array, render it as a numbered list, in order, of the
   documents/stages the tool moves through from first input to final artifact.
4. **Getting Started** — a short numbered walkthrough (3-6 steps) derived from the README's usage instructions: opening
   the page (or, for op-only tools, running the entry op), what to type or point it at first, and what to expect back.
5. **Artifacts** — if the catalog entry has `artifacts`, render a two-column table (`Path`, `What it holds`)
   from each `{path, note}` pair, describing every file the tool owns on disk.
6. **Requirements** — if the catalog entry has `requires`, list the runtime dependencies (server endpoints, client
   libraries) as a bullet list, phrased as "what needs to be available to run this tool" rather than as raw import
   paths.
7. **Local State** — if the catalog entry has `storage`, briefly note what the tool persists in
   `localStorage` (e.g. "your task list and target files stay in the browser between sessions") — do not just list the
   raw key names as the whole sentence.

## Style Guide
---

* **Tone:** Practical and specific — this documents a tool that exists and is used today, not a pitch for a hypothetical
  feature. Prefer the README's own voice where it is already clear; tighten rather than replace.
* **Format:** Valid Markdown:
    * H1 title formatted as `<icon> <name>`, with an italic tagline line directly under it.
    * `##` sections, included only when the corresponding data exists: Overview, Pipeline, Getting Started, Artifacts,
      Requirements, Local State.
    * Numbered lists for Pipeline and Getting Started; a Markdown table for Artifacts; bullet lists for Requirements.
    * Wrap file paths, doc-op filenames, and storage keys in backticks.

## Source-to-Output Mapping

| Source Signal                                  | Output Section       |
|------------------------------------------------|----------------------|
| README title / intro paragraph                 | Overview             |
| README usage instructions                      | Getting Started      |
| `apps.js` entry: `name`, `icon`, `tagline`     | Title + subtitle     |
| `apps.js` entry: `category`, `status`, `entry` | Badges               |
| `apps.js` entry: `pipeline`                    | Pipeline section     |
| `apps.js` entry: `artifacts`                   | Artifacts table      |
| `apps.js` entry: `requires`                    | Requirements section |
| `apps.js` entry: `storage`                     | Local State section  |

## Constraints

* If no matching `apps.js` entry can be found for a tool, name the tool from the README alone and omit every
  catalog-derived section (badges, Pipeline, Artifacts, Requirements, Local State) rather than inventing metadata.
* Never invent a pipeline stage, artifact path, requirement, or storage key that is not present in `apps.js` or the
  README.
* Do not include internal implementation details (function/variable names, JS file internals, DOM structure)
  beyond what the README already documents for end users.
* Keep the page short enough to read in under two minutes — this is a tool card expanded into a page, not a manual.