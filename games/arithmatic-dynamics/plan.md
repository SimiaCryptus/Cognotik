# Implementation Plan — Arithmetic Dynamics

A modular ES6 + HTML game (no build step required), touch/tablet-focused,
mouse-compatible. This plan translates `idea.md` into a concrete,
incremental engineering roadmap.

---

## 0. Guiding Constraints

- **Pure ES6 modules** loaded via `<script type="module">`. No bundler
  required to run; optional bundling later.
- **No heavy framework.** Vanilla DOM + a thin rendering layer. Keeps the
  dependency surface tiny and the code approachable.
- **Touch-first input** with Pointer Events (unifies touch + mouse + pen).
- **Deterministic core.** The expression model and transformations are
  pure, testable, and UI-agnostic.
- **Correctness by construction.** Every transformation is value-preserving;
  the engine refuses to produce an illegal move.

---

## 1. Architecture Overview

Three clean layers, communicating one direction (Model → View, View → Model
via intents):

```
┌─────────────────────────────────────────────┐
│  UI Layer (DOM, gestures, animation)          │
│   - TileRenderer, HistoryRibbon, RadialMenu   │
│   - GestureRecognizer (Pointer Events)        │
└──────────────┬────────────────────────────────┘
               │ intents (split/swap/group/combine/cancel)
┌──────────────▼────────────────────────────────┐
│  Game Layer (session, levels, progression)     │
│   - GameSession, LevelLoader, StarEvaluator    │
└──────────────┬────────────────────────────────┘
               │ operations on Expression
┌──────────────▼────────────────────────────────┐
│  Core Model (pure, testable)                   │
│   - Expression AST, Transformations, Value     │
└─────────────────────────────────────────────┘
```

### Directory Layout

```
games/arithmatic-dynamics/
  index.html
  styles/
    main.css
    tiles.css
    animations.css
  src/
    core/
      expression.js        # AST node types + construction
      value.js             # evaluate an expression to a number
      transformations.js   # split, swap, group, combine, cancel
      legality.js          # which verbs are legal on a selection
      serialize.js         # AST <-> plain string (debug/tests)
    game/
      session.js           # current expression, history, undo stack
      level.js             # level schema + loader
      stars.js             # star-goal evaluation
      levels/
        index.js           # manifest of level packs
        additive.js        # +/- levels
        multiplicative.js  # */÷ levels
    ui/
      app.js               # bootstrap, wires layers together
      stage.js             # renders the live expression as tiles
      tile.js              # single tile element + state
      history.js           # history ribbon
      radial-menu.js       # contextual verb menu
      split-chooser.js     # "how to break this apart" dialog
      gestures.js          # pointer-event gesture recognition
      animate.js           # merge/pop/fade helpers
    util/
      dom.js               # tiny DOM helpers (el, on, etc.)
      events.js            # minimal event emitter
      id.js                # stable id generation for nodes
  tests/
    expression.test.js
    transformations.test.js
    legality.test.js
    value.test.js
    stars.test.js
  assets/
    (icons, sounds later)
```

---

## 2. Core Model (Phase 1)

The heart of the game. Fully unit-tested, no DOM.

### 2.1 Expression AST

Node kinds:

- `Num` — `{ kind: 'num', id, value }`
- `BinOp` — `{ kind: 'op', id, op: '+'|'-'|'*'|'/', left, right }`
- `Group` — `{ kind: 'group', id, child }` (explicit parentheses)

Internally we may prefer a **flat list representation** for a run of
same-precedence terms (e.g. `20 + 5 - 1 - 1`) since so many verbs operate
on adjacent terms. Decision:

- Keep a canonical **AST** for value/serialization.
- Provide a **flattened "term row"** view for a group at a single
  precedence level, used by swap/combine/associativity verbs.

Each node carries a stable `id` so the UI can map tiles ↔ nodes and animate
transitions.

### 2.2 Value Evaluation (`value.js`)

- `evaluate(node) -> number` (exact integers; rationals only if `÷`
  introduced — start integer-only, division gated to exact results).
- Used by tests and by `legality.js` to _verify_ a transformation
  preserved value (belt-and-suspenders).

### 2.3 Transformations (`transformations.js`)

Each is a pure function `(expr, target, params) -> newExpr`:

- `split(expr, numId, { into })` — replace a `Num` with an equivalent
  sub-expression (e.g. `19 → 20 - 1`). `into` is validated to equal value.
- `swap(expr, aId, bId)` — swap two terms across a commutative op.
- `group(expr, [ids])` / `ungroup(expr, groupId)` — associativity.
- `combine(expr, aId, bId)` — fold two adjacent compatible terms into one
  `Num` (e.g. `25 - 2 → 23`).
- `cancel(expr, aId, bId)` — remove an inverse pair netting to identity.

**Invariant test:** for every transformation and valid input,
`evaluate(before) === evaluate(after)`. This is the single most important
test in the codebase.

### 2.4 Legality (`legality.js`)

- `legalVerbs(expr, selection) -> Verb[]` — given selected tile id(s),
  return which verbs apply. Drives the radial menu.
- Encodes commutativity/associativity rules (e.g. can't swap across `-`
  naively; must respect sign semantics).

---

## 3. Game Layer (Phase 2)

### 3.1 Session (`session.js`)

- Holds current `Expression`, an **undo stack**, and a **redo stack**.
- `apply(transformName, args)` pushes previous state, computes new state.
- `undo()` / `redo()` — first-class, unlimited.
- Emits events: `changed`, `solved`.
- `isSolved()` — true when expression is a single `Num`.
- Tracks **move count** and **verbs used** (for star goals).

### 3.2 Levels (`level.js`, `levels/*`)

Level schema:

```js
{
    id: 'add-04-plus-19',
        start
:
    '4 + 19',           // parsed to AST
        allowedVerbs
:
    ['split', 'swap', 'group', 'ungroup', 'combine', 'cancel'],
        allowedOps
:
    ['+', '-'],
        stars
:
    [
        {id: 'solve', test: s => s.isSolved()},
        {id: 'few-moves', test: s => s.moveCount <= 5},
        {
            id: 'split-combine-only',
            test: s => s.verbsUsed.subsetOf(['split', 'combine'])
        }
    ],
        hint
:
    'Try rounding 19 up to 20.'
}
```

- `additive.js` — hand-authored + procedurally-varied `+`/`-` puzzles.
- `multiplicative.js` — gated `×`/`÷` puzzles.

### 3.3 Stars (`stars.js`)

- `evaluate(session, level) -> { earned: string[] }`.
- Pure, testable against replayed sessions.

---

## 4. UI Layer (Phase 3)

### 4.1 Rendering (`stage.js`, `tile.js`)

- Render the current expression as a horizontal row of **tiles**
  (number / operator / parenthesis), keyed by node `id`.
- Reconcile on `session.changed`: reuse existing tile DOM by id so
  animations can play (FLIP technique for position transitions).
- Visual language:
  - "Round" numbers (5, 10, 20…) get a subtle glow class.
  - `+`/`-` share one color pairing; `×`/`÷` another.

### 4.2 Gestures (`gestures.js`)

Built on Pointer Events, gesture → intent mapping:

- **Tap** a number → open Split Chooser.
- **Drag onto adjacent tile** → Combine (if legal).
- **Drag past a neighbor** → Swap.
- **Lasso / multi-touch bracket** a run → Group; reverse → Ungroup.
- Long-press → Radial Menu of all legal verbs (discoverability fallback).

Design the recognizer as a small state machine emitting semantic events
(`gesture:tap`, `gesture:dragCombine`, etc.) so the app layer stays clean.

### 4.3 Contextual Menus

- `radial-menu.js` — large icon buttons for legal verbs on a selection.
- `split-chooser.js` — friendly presets:
  - "nearest ten ± n" (e.g. 19 → 20 − 1)
  - "make a five"
  - custom `a op b` picker (still validated for value equality).

### 4.4 History Ribbon (`history.js`)

- Renders each committed step as a compact row (the pre-transform
  expression + the verb applied).
- Tapping a row previews that state; optional "rewind to here."

### 4.5 Animation (`animate.js`)

- Merge animation for Combine.
- "Pop and vanish" for Cancel.
- FLIP-based slide for Swap/Group.
- Respect `prefers-reduced-motion`.

---

## 5. Feedback & Motivation (Phase 4)

- **Solved state:** celebratory (calm) animation; reveal earned stars.
- **Alternate paths:** after solving, optionally show a shorter/other
  solution the engine can find via a small search (BFS over legal verbs,
  depth-limited) — framed as "here's another way!"
- **No fail state:** undo/reset always available; no timers by default.

---

## 6. Persistence (Phase 5)

- `localStorage` for: unlocked packs, stars earned, last level.
- Simple versioned schema with migration guard.
- Fully optional; game works stateless.

---

## 7. Testing Strategy

- **Core is unit-tested** with a lightweight runner (Node's built-in
  `node:test` or a tiny custom harness — no build step).
- Priority tests:
  1. Value-preservation invariant for every transformation.
  2. Legality correctness (no illegal verbs surfaced).
  3. Solve detection + star evaluation on scripted sessions.
  4. Serialize round-trip (`parse(serialize(ast)) === ast`).
- UI smoke tests deferred / manual on a real tablet.

---

## 8. Milestones (incremental, always-playable)

1. **M1 — Core engine.** AST, evaluate, all transformations + tests.
   (No UI; validated via tests + a debug console page.)
2. **M2 — Minimal playable stage.** Render one level, tap-to-split via a
   basic chooser, combine via button. Undo. Solve detection.
3. **M3 — Gestures.** Drag-combine, drag-swap, group/ungroup, radial menu.
4. **M4 — History ribbon + polish animations.**
5. **M5 — Levels & progression.** Additive pack, stars, unlocks.
6. **M6 — `×`/`÷` unlock.** Extend model, transformations, levels.
7. **M7 — Alternate-path finder + persistence.**
8. **M8 — Accessibility & tablet QA.** Reduced motion, large targets,
   color-blind-safe pairings.

---

## 9. Open Questions / Decisions to Revisit

- Flat term-row vs. strict binary AST — start binary, add flat view helper
  as needed.
- Division semantics — integer-only with exact-division gating first;
  revisit fractions much later (likely out of scope for v1).
- How aggressive the "alternate path" search should be (perf on tablet).
- Whether Group/Ungroup needs an explicit `Group` node or can be inferred
  from AST structure (leaning: explicit node for clear UI mapping).
