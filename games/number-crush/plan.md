# Number Crush — Implementation Plan

This document describes a phased, high-level plan for building **Number
Crush** as described in `idea.md`. It focuses on architecture,
milestones, and a task breakdown suitable for incremental development.

---

## 1. Guiding Principles

- **Model-first**: build a robust, testable board/state model before
  investing heavily in rendering/animation.
- **Separation of concerns**: keep the game model, input handling, rule
  evaluation, and rendering as independent modules.
- **Incremental playability**: aim for a minimal playable loop early,
  then layer polish and features.
- **Testability**: the pure-logic layers (model, rules, solvability)
  should be unit-testable without a DOM.

---

## 2. Technology Choices

- **Language**: modern JavaScript (ES6 modules).
- **Platform**: browser (HTML5), no heavy framework required initially.
- **Rendering**: HTML/CSS + `<canvas>` or SVG for the hex grid
  (canvas recommended for animation performance).
- **Build/tooling**: lightweight (e.g. Vite) or plain ES modules for a
  zero-build start.
- **Testing**: a small unit-test runner (e.g. Vitest/Jest) for logic.
- **State**: plain JS objects/classes; no external state library needed
  at this scale.

---

## 3. High-Level Architecture

```
+-----------------------------------------------------------+
|                        Game (orchestrator)                |
|  - owns the game loop, mode, score, level config          |
+-----------------------------------------------------------+
        |                |                |             |
        v                v                v             v
+--------------+  +--------------+  +-------------+  +-----------+
| Board Model  |  | Rule Engine  |  | Input /     |  | Renderer  |
| (state)      |  | (validation) |  | Selection   |  | (view)    |
+--------------+  +--------------+  +-------------+  +-----------+
        |                                                 ^
        v                                                 |
+--------------+                                   +-------------+
| Solvability  |                                   | Animation   |
| / Generator  |                                   | System      |
+--------------+                                   +-------------+
```

### 3.1 Core Modules

- **`HexGrid` / `Board`**: stores tiles by axial/offset coordinates,
  provides neighbor lookups, gravity, and refill.
- **`Tile`**: value + optional special attributes + id.
- **`RuleEngine`**: given a selection and active rule, returns valid or
  invalid (plus scoring info).
- **`Selection`**: manages the in-progress line of tiles (contiguity,
  non-repeat, length limits).
- **`Solver` / `Generator`**: creates boards and checks for at least one
  valid move; performs reshuffles.
- **`ScoreManager`**: base points, length bonus, combo multiplier.
- **`Renderer`**: draws grid, tiles, selection highlight, popups.
- **`AnimationSystem`**: tweens for clears, falls, combos.
- **`Game`**: ties everything together, owns mode logic.

---

## 4. Data Model

### 4.1 Coordinates

- Use **axial coordinates** (`q`, `r`) for hex logic, converting to
  pixel positions for rendering.
- Provide helpers: `neighbors(coord)`, `toPixel(coord)`,
  `fromPixel(x, y)`.

### 4.2 Tile

```
Tile {
  id: string,        // stable unique id (for animations)
  value: number,     // integer value
  special?: string,  // 'wildcard' | 'multiplier' | 'bomb' | 'stone'
  coord: {q, r}
}
```

### 4.3 Board

- Map of coordinate → Tile (or 2D array with offset math).
- Bounded region definition (which cells are valid).

### 4.4 Level / Rule Config

```
LevelConfig {
  rule: RuleDefinition,
  valueRange: [min, max],
  minSelection: number,
  maxSelection: number,
  mode: 'zen' | 'timed' | 'puzzle' | 'daily',
  target?: number,
  moveLimit?: number,
  timeLimit?: number,
  seed?: number
}
```

---

## 5. Milestones

### Milestone 0 — Project Scaffolding

- Repo structure, ES module setup, dev server, test runner.
- Basic HTML page with a canvas and placeholder UI.
- **Deliverable**: blank board renders; tooling runs.

### Milestone 1 — Board Model & Rendering (static)

- Implement `HexGrid`, `Tile`, coordinate conversions.
- Render a static hex-packed board with numbered tiles.
- **Deliverable**: a readable, correctly packed hex board on screen.

### Milestone 2 — Selection & Input

- Implement drag/click selection of contiguous, non-repeating tiles.
- Enforce min/max length; highlight selection path.
- Show running indicator (e.g. current sum).
- **Deliverable**: player can draw a valid line and see it highlighted.

### Milestone 3 — Rule Engine (Sum rule first)

- Implement `RuleEngine` with the **Sum equals X** rule.
- On release: validate selection, clear on success, reject on failure.
- **Deliverable**: first complete match interaction.

### Milestone 4 — Gravity, Refill & Basic Scoring

- Implement gravity (tiles fall to fill vacancies).
- Spawn new tiles from top.
- Basic scoring (base + length bonus).
- **Deliverable**: full core loop — select, clear, fall, refill, score.

### Milestone 5 — Cascades & Combos

- Detect auto-forming clears? (design decision: manual-only vs auto)
- Implement combo multiplier for rapid consecutive player clears.
- **Deliverable**: satisfying chained clears with combo feedback.

### Milestone 6 — Solvability & Generation

- Board generator that avoids accidental clears at start.
- Deadlock detection + reshuffle when no valid move exists.
- **Deliverable**: boards are always solvable / never soft-lock.

### Milestone 7 — Additional Rules

- Add **Sum multiple of X**, **Common factor**, **Special factor**,
  **Consecutive run**, **Same value**.
- Rule descriptions surfaced in UI at level start.
- **Deliverable**: rule variety selectable per level.

### Milestone 8 — Game Modes

- Zen (default), Timed, Puzzle (target + move limit), Daily (seeded).
- Mode-specific UI (timer, moves left, target).
- **Deliverable**: playable modes with win/lose conditions.

### Milestone 9 — Special Tiles

- Wildcard, Multiplier, Bomb, Locked/Stone.
- Introduce gradually via level config.
- **Deliverable**: special tiles functioning and scored correctly.

### Milestone 10 — Feedback & Polish

- Animations (clears, falls, popups), audio hooks, score flyups.
- Accessibility: high-contrast, colorblind-safe, animation speed setting.
- **Deliverable**: responsive, polished feel.

### Milestone 11 — Progression & Balancing

- Difficulty ramp (value range, targets, budgets, obstacles).
- Level sequencing and telegraphing new mechanics.
- **Deliverable**: a tuned progression curve.

---

## 6. Suggested Directory Structure

```
games/number-crush/
  index.html
  src/
    main.js                # entry / bootstrap
    game/
      Game.js
      LevelConfig.js
      ScoreManager.js
    model/
      HexGrid.js
      Board.js
      Tile.js
      coords.js
    rules/
      RuleEngine.js
      rules/
        sumEquals.js
        sumMultiple.js
        commonFactor.js
        specialFactor.js
        consecutive.js
        sameValue.js
    gen/
      Generator.js
      Solver.js
    input/
      Selection.js
      InputController.js
    render/
      Renderer.js
      AnimationSystem.js
    ui/
      Hud.js
      Overlays.js
    audio/
      AudioManager.js
  test/
    hexGrid.test.js
    ruleEngine.test.js
    solver.test.js
  assets/
    (sounds, icons)
```

---

## 7. Key Algorithms

### 7.1 Neighbor & Contiguity

- Axial neighbor offsets: precomputed 6-direction table.
- Selection validity check: each new tile must be a neighbor of the last
  and not already in the selection.

### 7.2 Gravity & Refill

- Determine "fall columns" respecting hex offset geometry.
- Move surviving tiles down, then spawn new tiles at the top of each
  column until full.

### 7.3 Solvability Check

- Enumerate candidate selections up to `maxSelection` length via bounded
  DFS from each tile.
- Return early on first selection that satisfies the active rule.
- Prune aggressively (short lengths first) to keep it fast.

### 7.4 Combo Timing

- Track a combo timer; consecutive clears within a window increase the
  multiplier; reset on timeout or invalid attempt (mode-dependent).

---

## 8. Testing Strategy

- **Unit tests** (pure logic, no DOM):
  - Coordinate conversions and neighbor lookups.
  - Each rule's validation logic (positive + negative cases).
  - Gravity/refill correctness.
  - Solver deadlock detection and reshuffle.
- **Integration tests**:
  - Full clear → fall → refill cycle.
  - Combo multiplier accumulation.
- **Manual/playtest**:
  - Feel of selection, animation timing, difficulty ramp.

---

## 9. Risks & Mitigations

- **Hex geometry complexity** → isolate all hex math in `coords.js`,
  cover with tests early.
- **Solver performance** → bound selection length, prune, cache neighbor
  tables; only run on board changes.
- **Animation vs logic coupling** → keep model changes instantaneous;
  animations are a visual replay of already-resolved state.
- **Scope creep (special tiles/modes)** → gate behind milestones; ship
  the core loop first.

---

## 10. Definition of "MVP"

The MVP is reached at **Milestone 6**:

- Hex board renders correctly.
- Player can select and clear tiles under the Sum rule.
- Gravity, refill, and scoring work.
- Boards are always solvable (no soft-locks).
- Zen mode is fully playable.

Everything beyond MVP (extra rules, modes, special tiles, polish) is
additive and independently shippable.
