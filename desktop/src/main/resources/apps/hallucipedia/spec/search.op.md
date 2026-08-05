---
specifies: ../articles/search/*.md
related:
  - ../control/notes.md
  - main.op.md
---

# Hallucipedia — Simulated Search

The target file is a **search results page**, not an article. Its path encodes the reader's
query: `articles/search/tidal_glass_harvest.md` is the results page for the query
*“tidal glass harvest”* (underscores are spaces).

Read `control/notes.md` first: it defines the world being searched and overrides anything below
that conflicts with it. The accompanying node lists the pages that already exist and the paths
already promised — those are canon and must be reused verbatim.

## Output contract

Replace the entire contents of the target file with:

1. **YAML frontmatter** — exactly these keys:
   ```yaml
   ---
   title: 'Search: tidal glass harvest'
   summary: Results returned by the catalogue for the query “tidal glass harvest”.
   tags: search, tidal-glass, harvest
   updated: YYYY-MM-DD
   ---
   ```
2. **A one-sentence lead** stating how many entries the catalogue returned and how well it
   thinks they match. Written in the encyclopedia's own voice — a card catalogue reporting on
   itself, never a web search engine, never a chat reply.

## Sections

- `## Results` — 8–15 entries, each shaped like this:

  ```md
  ### [Tidal glass](../nature/tidal_glass.md)
  `nature/tidal_glass.md` · *Natural history*

  Two or three sentences in the encyclopedic voice, written as though lifted from the opening
  of that article.
  ```

  Ordering: pages the node lists as **already written** come first, and their snippet must
  agree with the summary quoted for them. Everything after them is a page that does not exist
  yet; **at least two thirds of all results must be unwritten pages.**
- `## Near matches` — 3–6 one-line bullets, each a link with a dash-gloss explaining why the
  catalogue thinks it is adjacent to the query.
- `## Related searches` — 4–8 links to *sibling search pages* in this same folder, written as
  bare slugs: `[glass kilns](glass_kilns.md)`. Slugs are lowercase `snake_case`.

## Rules

- Paths are relative to `articles/search/`, so an article link is `../nature/tidal_glass.md`.
  Never write the `articles/` prefix. Never link the same target twice.
- Reuse existing and already-promised paths instead of minting near-synonyms.
- Snippets advertise; they are not binding canon. A later article may expand on a snippet
  freely, but nothing here may contradict a page that already exists.
- No `## See also`, no `## Open questions`, no `## References` — this is an index, not an
  article. No images, no external links, no meta commentary about searching or generation.
- If the query matches nothing plausible in this world, say so in the lead in one dry sentence
  and still return the closest oblique entries; never return an empty page.