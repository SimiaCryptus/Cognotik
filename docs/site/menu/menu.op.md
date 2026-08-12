---
transforms: ../([^/\.]+)/([^/]+).md -> $1.json
related:
  - menu_schema.ts
---

# Task: Build the Apps Gallery Menu

You are producing **`menu.json`**, the data file that drives the Cognotik website's App Gallery (card grid and
navigation menu). Rather than transforming a single document, this task **summarizes every app showcase page**
in this directory into one compact list. The output must conform exactly to the `AppMenu` / `AppMenuEntry` shape defined in `menu_schema.ts`.

## Goal

For every app that has a generated showcase page (`*.md` in this directory, excluding this file), produce one
`AppMenuEntry` containing just enough information to render a gallery card and a nav link — without requiring the
reader to open the full page first.

## What to Read

* **Every showcase `*.md` page** in this directory — pull the title, tagline, and any icon/background image
  references already resolved there.

## Field-by-Field Mapping

| `menu.json` field   | Derived from                                                                  |
|---------------------|-------------------------------------------------------------------------------|
| `slug`              | name / id                                                                     |
| `name`              | Showcase name / header                                                        |
| `shortDescription`  | Showcase page tagline (trim further if needed to fit a card)                  |
| `file`              | Filename of the showcase page in this directory, e.g. `"comic-book.md"`       |

## Style Guide

* Output must be **valid JSON** matching `AppMenu` from `menu_schema.ts` — no comments, no trailing commas.
* `shortDescription` should be a single sentence, gallery-card length (roughly under 120 characters).
* Keep entries **flat and minimal** — omit any optional field you don't have real data for rather than guessing
  or inventing a value.
* Order `apps` alphabetically by `name` unless a category grouping is defined, in which case group by `category`
  first (alphabetically), then by `name` within each group.

## Constraints

* Do not include an entry for an app that has no corresponding showcase `*.md` page.
* Validate the result mentally against `isValidAppMenu` in `menu_schema.ts` before finalizing — every entry needs
  at least `slug`, `name`, `shortDescription`, and `file`.