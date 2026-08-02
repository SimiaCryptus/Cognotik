# Stereo-Tac-Toe

**Magic Eye Tic Tac Toe** — an HTML + modular ES6 browser game where the
entire game interface is hidden inside an autostereogram ("Magic Eye").

---

## 1. Concept

Render a Single Image Random Dot Stereogram (SIRDS) whose hidden depth map
_is_ the game board. When you relax your eyes and let the stereogram fuse,
the tic-tac-toe grid, the cursor, and the X's and O's float out of the noise
at different depths.

The game logic is deliberately trivial. **The interface is the point** —
playing a familiar game through a perception-bending display.

---

## 2. Goals & Non-Goals

### Goals

- Real-time autostereogram rendering on an HTML `<canvas>`.
- A playable 3×3 tic-tac-toe game driven entirely by the depth map.
- A visible depth cursor that tracks the mouse.
- A diagnostics toggle to view the raw depth map (grayscale).
- Pure client-side, no build step, modular ES6 (`<script type="module">`).

### Non-Goals (v1)

- Networked / multiplayer play.
- AI opponent (v1 is hot-seat two-player; AI is a stretch goal).
- Mobile / touch tuning (desktop mouse first).
- Animated / textured stereogram backgrounds (static noise ribbon in v1).

---

## 3. Architecture

```
games/stereo-tac-toe/
├── index.html            # Canvas, controls, module entry
├── styles.css            # Layout & control styling
├── idea.md               # This document
└── src/
    ├── main.js           # Bootstrap: wire modules, game loop
    ├── config.js         # Tunable constants (sizes, depths, colors)
    ├── stereogram.js     # Depth map -> SIRDS pixel renderer
    ├── depthMap.js       # Composites game state into a depth buffer
    ├── game.js           # Tic-tac-toe rules & state machine
    ├── input.js          # Mouse -> grid-cell mapping
    └── diagnostics.js    # Raw depth-map overlay renderer
```

### Data flow

```
input.js ──► game.js ──► depthMap.js ──► stereogram.js ──► <canvas>
                                │
                                └────────► diagnostics.js (optional overlay)
```

Each frame:

1. `input` updates the hovered cell / cursor position.
2. `game` exposes current board + cursor + winner state.
3. `depthMap` rasterizes that state into a `Float32Array` depth buffer
   (0 = far / background, 1 = near / foreground).
4. `stereogram` converts the depth buffer into RGBA pixels via SIRDS.
5. If diagnostics is on, `diagnostics` draws the grayscale depth map instead
   of (or beside) the stereogram.

---

## 4. Module Specs

### config.js

Exports a frozen `CONFIG` object:

- `WIDTH`, `HEIGHT` — canvas dimensions (e.g. 640 × 480).
- `EYE_SEPARATION_PX` — perceived eye distance in pixels (~ 280).
- `PATTERN_WIDTH` — base repeat width of the noise ribbon (~ 96).
- `DEPTH_LEVELS` — named depth values:
  - `BACKGROUND = 0.0`
  - `GRID = 0.35`
  - `MARK = 0.65`
  - `CURSOR = 0.85`
- `GRID_MARGIN`, `CELL_GAP` — layout metrics for the 3×3 board.

### depthMap.js

- `createDepthMap(width, height) -> Float32Array`
- `renderDepthMap(buffer, gameState)`:
  - Clears to `BACKGROUND`.
  - Draws grid lines at `GRID` depth.
  - Draws each placed `X` / `O` at `MARK` depth (X as two diagonals,
    O as a ring — rendered as shapes in the depth buffer).
  - Draws cursor marker at `CURSOR` depth over the hovered cell.
- Uses simple software rasterization helpers (`fillRect`, `drawLine`,
  `drawRing`) that write depth values, not colors.

### stereogram.js

- `renderStereogram(ctx, depthBuffer, width, height)`:
  - Classic SIRDS constraint-propagation algorithm (Thimbleby/Inglis):
    - For each row, compute per-pixel separation from depth.
    - Link left/right pixel constraints, then assign random colors to
      unconstrained anchors.
  - v1 uses a **static noise ribbon**: a precomputed random color pattern
    of width `PATTERN_WIDTH`, sampled to fill unconstrained pixels so the
    background stays stable frame-to-frame (only depth changes ripple).
- Writes into an `ImageData` and blits to the canvas.

### game.js

- State: `board[9]` (`null` | `'X'` | `'O'`), `turn`, `winner`, `cursor`.
- `move(cellIndex)` — place mark if empty & no winner; toggle turn;
  evaluate win/draw.
- `checkWinner()` — 8 winning lines.
- `reset()` — clear board, X starts.
- Emits no DOM; pure state consumed by `depthMap` and `main`.

### input.js

- Tracks mouse position over the canvas.
- `pixelToCell(x, y) -> index | null` using grid layout from `config`.
- Exposes `cursorPixel` for depth-cursor rendering.
- Click within a valid cell calls `game.move(...)`.

### diagnostics.js

- `drawDepth(ctx, depthBuffer, width, height)` — maps depth `0..1` to
  grayscale `0..255` for direct inspection.
- Toggled via checkbox / hotkey `D`.

### main.js

- Instantiates modules, owns the `requestAnimationFrame` loop.
- Reads diagnostics toggle; picks stereogram vs. depth renderer.
- Wires reset button and status text (turn / winner).

---

## 5. UI / Controls

- **Canvas** — the stereogram display.
- **Diagnostics toggle** — checkbox + hotkey `D` to view raw depth map.
- **Reset button** — new game.
- **Status line** — "X to move" / "O wins" / "Draw".
- **Instructions** — short blurb on how to view a Magic Eye image.

---

## 6. Rendering Notes

- Depth buffer is authoritative; stereogram and diagnostics are two views
  of the same data.
- Keep background noise deterministic per-column so only foreground depth
  shifts are visible when the cursor/marks move.
- Redraw only when game state or cursor cell changes (dirty flag) to keep
  the fused image steady and reduce flicker.

---

## 7. Milestones

1. **M1 — Skeleton**: HTML + module wiring, blank canvas, config.
2. **M2 — Depth map**: grid + marks + cursor rendered to buffer;
   diagnostics view proves it out.
3. **M3 — Stereogram**: SIRDS renderer over the depth map.
4. **M4 — Gameplay**: input mapping, moves, win/draw, reset.
5. **M5 — Polish**: stable noise, dirty-flag redraw, instructions, styling.

---

## 8. Stretch Goals

- Simple AI opponent (minimax).
- Animated depth transitions when marks are placed.
- Adjustable eye-separation / pattern-width sliders.
- Textured pattern (image tile) instead of pure noise.
- Win-line highlight rendered as a raised depth stripe.
