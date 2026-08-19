---
specifies: ../articles/root.md
related:
  - ../control/notes.md
  - ../control/seed.md
  - main.op.md
---

# Hallucipedia — Front Page

The target file is `articles/root.md`, the front page of the encyclopedia and the origin of its
entire link graph. It is written immediately after `control/notes.md` has been fixed.

Read `control/notes.md` first and treat it as binding: use the department folders it names, its
anchor dates, its fixed vocabulary and its voice. Invent no department it does not list.

## Output contract

Replace the entire contents of the target file with:

1. **YAML frontmatter** — `title`, `summary`, `tags`, `updated` (the same four keys every
   article carries). The title is the name of the encyclopedia's world, not "Root".
2. **A 2–4 sentence lead** describing what this encyclopedia covers, in its own voice, as
   though it had been in print for decades.
3. `## Departments` — one bullet per department folder in `control/notes.md`, linking
   `<folder>/index.md` with a short dash-gloss.
4. `## Featured` — 4–6 links to specific, enticing subjects, each with one line of gloss.
5. `## Anchor points` — 4–8 links to the fixed names, events and institutions listed in
   `control/notes.md`, at their canonical paths.
6. `## The frontier` — a table with columns *Department* and *Unobserved subjects*, one row per
   department, two to three linked subjects per row.
7. A short closing note (2–3 sentences) explaining that unwritten entries are red and that
   following one is what writes it.

## Rules

- **30–45 internal links**, all relative to `articles/` (root.md sits at the root, so a link is
  `history/the_long_thaw.md`). Lowercase `snake_case`, ending in `.md`, never the `articles/`
  prefix, never the same target twice.
- Every link except the department overviews should point at a page that does not exist yet.
  The front page's job is to commission the first few dozen articles.
- Prefer the specific over the generic: a named treaty, a named person, a dated incident.
- No images, no external links, no `## See also`, no meta commentary about the software.
- Output the document only.