---
specifies: ../control/notes.md
related:
  - ../control/seed.md
  - main.op.md
---

# Hallucipedia — World Setup

The target file is `control/notes.md`, the constitution of this encyclopedia. Every later
article and illustration defers to it. It is being written (or rewritten) from a short prompt
supplied by the reader, which is quoted in the node that invoked this operation and stored in
`control/seed.md`.

Your job is to expand one or two sentences of wish into a world bible a writer can obey
without asking questions.

## Output contract

Replace the entire contents of the target file with a Markdown document, no frontmatter,
beginning `# Site definition`, containing exactly these sections in this order:

- `## Premise` — 3–6 sentences. What world this encyclopedia is a reference work *from*, what
  is different about it, and what year/era its present is. Concrete, not atmospheric.
- `## Timeline anchors` — a table of 4–8 fixed dates and what happened, phrased as settled
  history. These may never be contradicted later.
- `## Voice` — 4–6 bullets on tense, person, hedging, attribution and humour.
- `## Departments` — a table of 6–9 rows: folder path in backticks (`history/`, `nature/`, …,
  lowercase, `snake_case`, trailing slash) and one line on what belongs in it. Choose the
  folders that suit this world; the front page will use these and only these.
- `## Fixed names and terms` — a table of 8–15 proper nouns, institutions, units, materials or
  offices, each with a one-line definition and, where relevant, the article path it will live
  at. These are the seed vocabulary every later article must reuse.
- `## Canon rules` — a numbered list of 4–6 rules, including that existing articles are true
  and that names, dates, units and spellings stay stable.
- `## Structure preferences` — where hub pages live (`<folder>/index.md`), mandatory tail
  sections, typical length.
- `## Visual identity` — medium, palette, lighting and subject conventions for illustrations;
  never text inside images.
- `## Naming` — path conventions and capitalisation.
- `## Taboos` — 2–4 things the encyclopedia never discusses, or discusses only obliquely.

## Rules

- 500–900 words. Dense and imperative; this file is read as instructions, not prose.
- Invent freely where the prompt is silent, and state the inventions as long established.
  Vagueness is the only real error: prefer one specific date to three hedged ones.
- Do not mention the reader, the prompt, generation, models or files other than the paths and
  folders named above.
- Do not include Markdown links to articles; folder and file paths go in backticks.
- Output the document only — no preamble, no notes on choices made.