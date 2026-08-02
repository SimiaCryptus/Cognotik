# Optimization Mechanics Visualizer

An interactive, browser-based visualization comparing gradient-based
optimizers (GD, ADAM, L-BFGS, QQN) on a configurable 2D loss surface
with tunable noise and confounding-gradient lattices.

---

## 1. Goals

- Visualize 2D optimization on a **noisy lattice** to compare
  **GD**, **ADAM**, **L-BFGS**, and **QQN** side by side or overlaid.
- Show live **directional statistics** (current gradient, history,
  time-integrated gradient, and time-integrated squared gradient) via
  **statistics bars** aligned to the left and bottom edges of the grid.
- Allow the grid to **pan infinitely**, enabling an unbounded
  optimization path that follows the active optimizer.
- Provide a rich **step controller** that surfaces internal optimizer
  mechanics (e.g., QQN's oracle point, quadratic path, and line search).
- Offer flexible **loss visualization** (color schemes, contours,
  or hidden surface).
- Render **gradient vectors** at occupied points and fade **point
  histories** using transparency.

---

## 2. Non-Goals (for v1)

- No 3D rendering (strictly 2D top-down heatmap/contour).
- No training of real neural networks — objective functions are
  synthetic and analytic.
- No server-side compute; everything runs client-side.

---

## 3. Objective Function Model

The loss surface `L(x, y)` is a **sum of composable layers**. Each
layer is toggleable and independently configurable.

### 3.1 Base Field (choose one)

1. **Linear flow** — a global static gradient vector:
   `L_linear = a·x + b·y`
   Parameters: `a`, `b`.

2. **Quadratic bowl** — squared terms centered at `(cx, cy)`:
   `L_bowl = kx·(x - cx)² + ky·(y - cy)²`
   Parameters: `kx`, `ky`, `cx`, `cy`.

### 3.2 Noise Overlays (additive, toggleable)

1. **High-frequency noise** — smooth periodic / value noise:
   `L_noise = amp · noise(fx·x, fy·y)`
   Parameters: `amp`, `fx`, `fy`, `seed`.
   Implementation: deterministic hash-based value noise with
   bilinear interpolation (so gradients are continuous).

2. **Confounding lattice** — a grid of regions (≈ 50% of area) with a
   different, confounding gradient:
   - Lattice cell size `L`, fill fraction `f` (default 0.5).
   - Inside "active" cells, add `L_lattice = cgx·x + cgy·y`
     (a confounding local gradient).
   - Use a **smoothstep** blend at cell boundaries so the composite
     function remains differentiable (avoids gradient discontinuities
     that would break L-BFGS/QQN).

### 3.3 Gradient Computation

- Since all layers are analytic, provide **closed-form gradients**
  per layer for accuracy and speed.
- Optionally support **numerical (finite-difference)** gradients as a
  fallback / verification path.
- The objective must expose:
  - `value(x, y) -> number`
  - `grad(x, y) -> [gx, gy]`

---

## 4. Optimizers

A common **Optimizer interface** so all can be driven by the same loop:

```
interface Optimizer {
  name: string
  reset(x0, y0)
  // returns a StepResult describing what happened this step
  step(objective) -> StepResult
  getState() -> object   // for stats/inspection
  params: { ...tunables }
}
```

### 4.1 StepResult (for visualization)

```
StepResult {
  from: [x, y]
  to:   [x, y]
  grad: [gx, gy]
  // optional mechanics for rich rendering:
  probes?: [{ x, y, t, loss }]   // line-search evaluations
  oracle?: [x, y]                // QQN oracle / L-BFGS full step
  path?:   [[x,y], ...]          // quadratic/curved path samples
  chosenT?: number
}
```

### 4.2 Implemented optimizers

- **GD** — `x -= lr · grad`. StepResult shows `from`, `to`, `grad`.
- **ADAM** — standard biased/unbiased moment estimates. StepResult
  includes moment vectors for stats bars.
- **L-BFGS** — two-loop recursion (port of the existing `tf`-based
  logic to a pure 2D numeric implementation for infinite panning).
  StepResult shows the full quasi-Newton step as `oracle`.
- **QQN** — quadratic path
  `step(t) = t(1-t)·d_sd + t²·d_lbfgs`, with golden-section line
  search over `t ∈ [0,1]`. StepResult includes:
  - `oracle` = full L-BFGS point,
  - `path` = sampled quadratic path,
  - `probes` = line-search evaluations,
  - `chosenT`.

> Note: Existing `optimizer-*.js` files use TensorFlow.js. For this
> visualizer we will implement lightweight **pure-JS 2D versions**
> (no `tf` dependency) to support arbitrary panning and per-step
> mechanics. The tf versions may be kept for reference.

---

## 5. Rendering & Layout (Canvas)

### 5.1 Overall layout

```
+--------------------------------------------------+
| Toolbar (controls, dropdowns, buttons)           |
+----+---------------------------------------+-----+
| S  |                                       |     |
| t  |                                       |     |
| a  |         Main optimization grid        |     |
| t  |         (heatmap / contours)          |     |
|    |                                       |     |
| (left stats bar)                           |     |
+----+---------------------------------------+-----+
|  Bottom stats bar (x-direction stats)      |     |
+--------------------------------------------------+
```

- **Left stats bar**: vertical bar aligned to grid's left edge —
  encodes y-direction statistics.
- **Bottom stats bar**: horizontal bar aligned to grid's bottom edge —
  encodes x-direction statistics.
- Both bars share the grid's coordinate mapping so they read as
  "projections" of the directional statistics.

### 5.2 Canvas strategy

Use **layered canvases** (stacked, absolutely positioned) to avoid
redrawing expensive layers each frame:

1. `#loss-layer` — heatmap / contour of `L`. Redraw only on
   objective-param change or pan/zoom.
2. `#overlay-layer` — grid lines, lattice cell outlines.
3. `#path-layer` — optimizer path history (with fading), gradient
   vectors, QQN mechanics (oracle, path, probes). Redraw per step.
4. `#stats-layer` — left & bottom statistics bars.
5. `#ui-layer` — cursor readouts, hover tooltip, current point.

### 5.3 Loss visualization modes

- **Heatmap** — color map (viridis / grayscale / signed diverging).
- **Contours** — iso-lines via marching squares.
- **Hidden** — no surface; only path + gradients (to emphasize
  optimizer behavior on an "unknown" landscape).

Color scheme selectable; support **log / linear** value scaling and
auto-normalization over the visible viewport.

### 5.4 Path history & gradient vectors

- Store the full path (or a capped ring buffer) of visited points.
- Render older points with lower alpha (fade).
- At each occupied point, draw the **gradient vector** (scaled arrow).
- Highlight the current point distinctly.

### 5.5 Infinite panning

- Maintain a **world-space camera** `{ cx, cy, scale }`.
- The grid follows the current point (auto-follow toggle) so the path
  can extend indefinitely.
- World↔screen transforms drive all layers; loss layer re-rasterizes
  the visible viewport on pan/zoom.

---

## 6. Statistics Bars

Four tracked quantities, projected onto x and y axes:

1. **Current gradient** — instantaneous `grad`.
2. **History** — recent gradient samples (sparkline).
3. **Time-integrated gradient** — running sum/mean `Σ grad`
   (first-moment-like).
4. **Time-integrated squared gradient** — running `Σ grad²`
   (second-moment-like; mirrors ADAM's `v`).

- **Bottom bar** shows the **x-components**; **left bar** shows the
  **y-components**.
- Each quantity is a distinct sub-track with a legend and color.
- Values normalized to bar extents; show sign via direction/color.

---

## 7. Step Controller / Playback

- **Step** — advance the active optimizer one iteration; render
  mechanics.
- **Play / Pause** — auto-step via `requestAnimationFrame`, with a
  speed slider (steps per second).
- **Reset** — return to `(x0, y0)`, clear history & optimizer state.
- **Micro-step (QQN)** — optional sub-stepping to reveal:
  1. show oracle point,
  2. draw quadratic path,
  3. animate line-search probes,
  4. commit chosen step.

---

## 8. Controls (UI)

- **Optimizer**: dropdown (GD / ADAM / L-BFGS / QQN) or "compare all".
- **Learning rate** and optimizer-specific params (β1, β2, ε, history
  size `m`).
- **Objective**: base field selector + parameters.
- **Noise**: amp/frequency/seed; lattice size/fill/confounding grad.
- **Visualization**: mode (heatmap/contour/hidden), color scheme,
  scaling, show/hide gradients, path fade length.
- **Camera**: auto-follow toggle, zoom, reset view.
- **Start point**: draggable / numeric entry.

---

## 9. File / Module Structure

```
experiments/optimization-mechanics/
  idea.md
  index.html
  css/
    style.css
  js/
    main.js               // bootstrap, wiring, animation loop
    app-state.js          // central config/state store
    objective.js          // composable loss + analytic gradients
    noise.js              // value noise + lattice helpers
    camera.js             // world<->screen transforms, panning/zoom
    renderer/
      loss-renderer.js    // heatmap/contour rasterization
      overlay-renderer.js // grid + lattice outlines
      path-renderer.js    // path history, gradient arrows, QQN
      stats-renderer.js   // left/bottom statistics bars
    optimizers/
      optimizer.js        // common interface + factory
      gd.js
      adam.js
      lbfgs.js            // pure-JS 2D two-loop recursion
      qqn.js              // quadratic path + golden-section search
    stats.js              // running gradient statistics accumulators
    ui/
      controls.js         // build & bind DOM controls
  // existing tf-based references (kept for parity):
  js/optimizer-adam.js
  js/optimizer-lbfgs.js
  js/optimizer-qqn.js
```

All modules are **native ES6 modules** (`<script type="module">`),
no bundler required. Canvas-only rendering (no external chart libs).

---

## 10. Data Flow

```
UI controls ──> app-state (config) ──┐
                                     │
animation loop ──> optimizer.step(objective) ──> StepResult
                                     │
                                     ├─> stats accumulators update
                                     └─> renderers draw (path, stats)
camera changes ──> loss-renderer re-rasterize + all layers redraw
```

- **app-state** is the single source of truth; controls mutate it and
  emit change events. Renderers subscribe and mark layers dirty.
- A simple **dirty-flag** system decides which canvases redraw per
  frame (loss layer is expensive; others are cheap).

---

## 11. HTML Skeleton (planned)

```html
<div id="toolbar"><!-- controls --></div>
<div id="stage">
  <canvas id="loss-layer"></canvas>
  <canvas id="overlay-layer"></canvas>
  <canvas id="path-layer"></canvas>
  <canvas id="stats-layer"></canvas>
  <canvas id="ui-layer"></canvas>
</div>
<script type="module" src="js/main.js"></script>
```

- All canvases share identical CSS size and are stacked with
  `position: absolute`. Device-pixel-ratio scaling handled in
  `camera`/renderers for crisp rendering.

---

## 12. Implementation Phases

1. **Scaffold** — HTML/CSS layout, layered canvases, camera transform,
   DPR handling.
2. **Objective + noise** — analytic loss & gradients; verify with FD.
3. **Loss renderer** — heatmap first, then contours, hidden mode.
4. **Optimizers** — GD → ADAM → L-BFGS → QQN (pure JS).
5. **Path renderer** — history fade + gradient arrows.
6. **Stats** — accumulators + left/bottom bars.
7. **Step controller & playback** — step/play/pause/reset, QQN
   micro-steps.
8. **UI controls** — full parameter panel wired to app-state.
9. **Infinite pan / auto-follow** — viewport re-rasterization.
10. **Polish** — compare-all overlay, color schemes, tooltips.

---

## 13. Open Questions / Future Ideas

- Compare-all mode: run all optimizers from the same start
  simultaneously with color-coded paths?
- Record & export path data (CSV) or the animation (GIF/WebM)?
- Add momentum-GD and RMSProp for a fuller comparison.
- Add a "difficulty" preset selector (bowl / saddle / rugged lattice).
- Numerical-gradient toggle to demonstrate FD noise effects on
  quasi-Newton methods.
