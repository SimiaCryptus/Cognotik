# Number Crush — Game Design Document

## 1. Overview

**Number Crush** is a match-and-clear puzzle game inspired by the
"candy-crush" genre, but built around **numbers and mathematical
relationships** rather than color matching. Tiles are arranged in a
**hexagonally packed grid**. Players select short lines of adjacent tiles
(typically 2–4 tiles) and, if the selection satisfies the active rule,
the tiles are cleared, points are awarded, and tiles above fall down to
fill the gaps.

The core hook is that matching is driven by **arithmetic logic**, giving
the game an educational flavor while remaining fast and satisfying.

---

## 2. Goals & Design Pillars

- **Simple to learn, deep to master** — one core action (draw a line of
  tiles) with rule variety providing depth.
- **Mathematical engagement** — every clear should reinforce a numeric
  relationship (sums, factors, multiples, etc.).
- **Satisfying feedback** — clearing tiles, cascades, and combos should
  feel responsive and rewarding.
- **Accessible presentation** — clean, readable tiles and a calm,
  uncluttered board.

---

## 3. Board & Tiles

### 3.1 Board Layout

- Tiles are arranged in a **hexagonal packing** (offset rows), so each
  interior tile has up to **six neighbors**.
- The board is a bounded region (e.g. a rounded hexagon or rectangle of
  hex cells). A typical starting size is a grid of roughly 7–9 columns
  and 8–10 rows.
- **Gravity** pulls tiles downward. When tiles clear, the tiles above
  slide down to fill vacancies, and new tiles spawn from the top.

### 3.2 Tiles

- Each tile displays a **single integer value** (initial range e.g. 1–9,
  expandable in harder modes).
- Tiles may optionally carry **special attributes** (see Special Tiles).
- Tile values are generated to ensure the board always has at least one
  valid move available (see Solvability).

---

## 4. Core Gameplay

### 4.1 Selecting Tiles

- The player draws a **connected line** of tiles by clicking/tapping or
  dragging across adjacent tiles.
- A valid selection is:
  - **Contiguous** — each tile is a hex-neighbor of the previous one.
  - **Non-repeating** — a tile can only appear once in the selection.
  - Between **2 and 4 tiles** long (configurable per rule/level).
- The current selection is visually highlighted, and a **running
  indicator** (e.g. the current sum) is shown to help the player.

### 4.2 Resolving a Selection

- When the player completes/releases a selection:
  - If it **satisfies the active rule**, the tiles are cleared, points
    awarded, and gravity + refill occurs.
  - If it **fails**, the selection is rejected with gentle feedback
    (shake/flash) and no penalty (or a small penalty in timed modes).

### 4.3 Cascades & Combos

- After tiles fall and refill, if new valid groups form and are cleared
  in quick succession, a **combo multiplier** increases the score.
- Chained clears create a satisfying cascade effect.

---

## 5. Match Rules

Rules define what makes a selection valid. A level uses **one primary
rule** (rules may rotate or combine in advanced modes).

### 5.1 Sum Rules

- **Sum equals target X** — selected values must add up to a specific
  target (e.g. 10).
- **Sum is a multiple of X** — total is divisible by X.

### 5.2 Factor / Divisibility Rules

- **Shared common factor** — all selected tiles share a common factor
  greater than 1.
- **Special factor** — all tiles are divisible by a designated "special"
  number for the level.

### 5.3 Sequence Rules (optional / advanced)

- **Consecutive run** — values form a consecutive ascending/descending
  sequence (e.g. 4-5-6).
- **Same value** — all selected tiles share the same value.

Each rule should have a short, plain-language description shown to the
player at the start of a level.

---

## 6. Special Tiles (Optional Enhancements)

- **Wildcard** — can represent any value to complete a match.
- **Multiplier** — clearing it multiplies the points of that clear.
- **Bomb** — when cleared, also removes surrounding neighbors.
- **Locked/Stone** — cannot be selected until cleared indirectly by an
  adjacent match.

These are optional and should be introduced gradually to avoid
overwhelming new players.

---

## 7. Scoring

- **Base points** per cleared tile.
- **Length bonus** — longer valid selections score disproportionately
  more (encourages ambitious matches).
- **Combo multiplier** — consecutive cascade clears increase multiplier.
- **Special tile bonuses** — as defined above.
- Optional **target score** or **move/time limits** per level.

---

## 8. Game Modes

- **Endless / Zen** — no timer, play until no moves remain.
- **Timed** — score as much as possible before time runs out.
- **Puzzle / Level** — reach a target score within a move limit.
- **Daily Challenge** — a fixed seed board shared by all players.

---

## 9. Progression & Difficulty

- Difficulty scales via:
  - **Larger value range** (harder to reach sums / factors).
  - **Tighter targets** or more restrictive rules.
  - **Smaller move/time budgets**.
  - Introduction of **obstacle tiles** (stone/locked).
- Levels should ramp gradually and telegraph new mechanics.

---

## 10. Solvability & Board Generation

- After every board change, the game **verifies at least one valid move
  exists** for the active rule.
- If no move exists (deadlock), the board should **reshuffle** or inject
  helpful tiles.
- Initial boards are generated to avoid immediate accidental clears
  unless intended.

---

## 11. Feedback & Presentation

- **Visual**: highlighted selection path, animated clears, falling tiles,
  combo popups, score flyups.
- **Audio**: distinct sounds for select, valid clear, invalid attempt,
  combo escalation.
- **Accessibility**: high-contrast tiles, colorblind-safe design (rely on
  numbers, not just color), and adjustable animation speed.

---

## 12. Technical Notes (High Level)

- **Platform**: browser-based (HTML), using modular ES6 for structure.
- **Rendering**: a lightweight 2D presentation of the hex grid.
- **Architecture** (conceptual): clear separation between
  - **board/state model** (tiles, positions, rules),
  - **input/selection handling**,
  - **rule evaluation**,
  - **rendering/animation**.
- Designed to be extendable with new rules and special tiles.

---

## 13. Future Ideas

- Multiplayer / head-to-head score races.
- User-created rule sets and puzzle sharing.
- Achievements and long-term progression.
- Themed tile skins (numbers styled as fruit, gems, etc.).
