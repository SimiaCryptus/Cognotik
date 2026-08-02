# 🧮 Philosophical Calculator

A multi-perspective analytical toolkit. It turns raw notes into a polished article, then
re-examines that article through a battery of philosophical, rhetorical and creative
"lenses", optionally folding the results back into the main text.

## Files

| File        | Purpose                                         |
|-------------|-------------------------------------------------|
| `app.html`  | Single entry point (tabs, steps, viewers)        |
| `app.js`    | ES-module controller; all shared code from `/lib/app/` |
| `style.css` | All styling; design tokens declared at `:root`   |
| `ops/`      | DocOp definitions consumed by `runDocOp()`       |

Global chrome (usage/cost reporting, session list, Git, ZIP download) is provided by the
shared menubar via `initMenu({ appName: 'Philosophical Calculator' })` — this app does
**not** duplicate any of it.

## Pipeline

```
notes/notes.md ─▶ summary.md ─▶ content.md ─▶ (lenses) ─▶ content.md ─▶ content.md
                   [1]            [2]                       [3 update]   [4 illustrate]
```

| Step | Op file                    | Output       | Badge                |
|------|----------------------------|--------------|----------------------|
| 1    | `ops/summarize_op.md`      | `summary.md` | `badge-summary`      |
| 2    | `ops/draft_article_op.md`  | `content.md` | `badge-content`      |
| 3    | `ops/update_article_op.md` | `content.md` | `badge-update`       |
| 4    | `ops/illustration_op.md`   | `content.md` | `badge-illustration` |

Inputs: `notes/notes.md` (canonical source, plus any uploaded files in `notes/`) and the
optional `instruct.md` directives file. Both are auto-saved (800 ms debounce) and are
written before any operation is launched.

## Analysis lenses

Their outputs feed back into the article via step 3 (*Update Article*).

| Lens                | Op file                    | Output            |
|---------------------|----------------------------|-------------------|
| Brainstorm          | `ops/brainstorm_op.md`     | `brainstorm.md`   |
| Dialectical         | `ops/dialectical_op.md`    | `dialectical.md`  |
| Socratic Dialogue   | `ops/socratic_op.md`       | `socratic.md`     |
| Perspectives        | `ops/perspectives_op.md`   | `perspectives.md` |
| Game Theory         | `ops/gametheory_op.md`     | `gametheory.md`   |
| Historical Debate   | `ops/debate_op.md`         | `debate.md`       |
| Unrunnable Protocol | `ops/protocol_op.md`       | `protocol.md`     |

## Output lenses

Each produces a standalone deliverable.

| Lens               | Op file                             | Output                     |
|--------------------|-------------------------------------|----------------------------|
| Persuasive Essay   | `ops/persuasive_op.md`              | `persuasive.md`            |
| Narrative          | `ops/narrative_op.md`               | `narrative.md`             |
| Comic Book         | `ops/comic_op.md`                   | `comic.md`                 |
| Technical Tutorial | `ops/technical_explanation_op.md`   | `technical_explanation.md` |
| HTML Webpage       | `ops/webpage_op.md`                 | `page.html`                |

## Models

The **Models** tab exposes per-app overrides (`smartModel`, `fastModel`, `imageModel`),
persisted in `localStorage` under the `philcalc` namespace and passed to every
`runDocOp(sessionId, op, target, models)` call. Leaving a field empty defers to the
server default.

## State & resumability

* The filesystem is the source of truth; badges are restored on load from
  `fetchDocopsStatus()` and then from file-existence checks.
* `createStatusPoller` runs at 3 s and is stopped whenever no task is `RUNNING`
  and no locally-launched operation is in flight.
* Only UI preferences are stored in `localStorage` — never content.

## Conformance

Audited against the DocOps app standard: 3-file layout ✅, modern ES module ✅,
shared menubar ✅, no duplicated Usage/Git/Sessions/Download chrome ✅, viewport ✅,
mobile-first layout at 360/768/1280 ✅.

Outstanding items: none known.
