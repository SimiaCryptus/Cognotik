---
title: Hallucipedia
summary: The front page of an encyclopedia that writes each article the moment you ask to read it.
tags: meta, front-page
updated: 2026-01-01
---

**Hallucipedia** is a reference work with no back issues. Nothing here was written in advance:
every article is composed at the instant a reader follows a link to it, then filed permanently.
Red links are not errors — they are the parts of the world that have not been observed yet.

## How to read it

Type any path into the bar above (it is relative to `articles/`) or simply follow a link. If the
page does not exist, it is created and written for you. Use **Illustrate** on any page to have
diagrams and plates added to it, and **Regenerate** to have it rewritten from scratch.

## Departments

- [Physical sciences](science/index.md)
- [Mathematics](mathematics/index.md)
- [History](history/index.md)
- [Geography and places](places/index.md)
- [People](people/index.md)
- [Technology](technology/index.md)
- [Arts and culture](arts/index.md)
- [Natural history](nature/index.md)

## Featured, more or less at random

- [The cartography of imaginary coastlines](places/imaginary_coastlines.md)
- [Resonant materials](science/resonant_materials.md)
- [A short history of forgotten instruments](arts/forgotten_instruments.md)
- [Recursive libraries](technology/recursive_libraries.md)

## About this edition

The editorial conventions of this encyclopedia — its tone, its world, and what counts as true —
are defined in `control/notes.md`. Article generation is specified by `spec/main.op.md`;
illustration by `spec/illustrate.op.md`. The link graph of everything written so far is kept in
`links.json`.

```mermaid
flowchart LR
  R["Reader follows a link"] --> E{"Article exists?"}
  E -- "yes" --> V["Render Markdown + MathJax + Mermaid"]
  E -- "no" --> C["Create empty file"] --> G["Run main.op.md"] --> V
  V --> L["Update links.json"]
```

*The generation loop of Hallucipedia.*
- [The Ardent Compact](history/ardent_compact.md)
- [Mira Solvent](people/mira_solvent.md), first archivist of the [Unwritten Catalogue](technology/unwritten_catalogue.md)
- [Tidal glass](nature/tidal_glass.md) and the [Sea of Glass](places/sea_of_glass.md)
- [The arithmetic of unfinished proofs](mathematics/unfinished_proofs.md)
## The frontier
None of these have been written yet. Following one is how it gets written.
| Department | Unobserved subject |
| --- | --- |
| Physical sciences | [quantum foam](science/quantum_foam.md), [slow light districts](science/slow_light_districts.md) |
| Mathematics | [the Solvent conjecture](mathematics/solvent_conjecture.md), [countable coastlines](mathematics/countable_coastlines.md) |
| History | [the Long Thaw](history/the_long_thaw.md), [the Paper Wars](history/paper_wars.md) |
| Places | [Ambergate](places/ambergate.md), [the Hollow Meridian](places/hollow_meridian.md) |
| People | [Josias Rell](people/josias_rell.md), [the Nine Compilers](people/nine_compilers.md) |
| Technology | [lantern engines](technology/lantern_engines.md), [index rot](technology/index_rot.md) |
| Arts | [the glass fugue](arts/glass_fugue.md), [marginal portraiture](arts/marginal_portraiture.md) |
| Nature | [the ledger moth](nature/ledger_moth.md), [reversing tides](nature/reversing_tides.md) |