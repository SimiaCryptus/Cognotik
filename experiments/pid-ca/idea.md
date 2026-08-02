# PID-Regulated Cellular Automaton

## Technical Project Plan & Specification

---

## 1. Overview

This document specifies the design of a **PID-Regulated Cellular Automaton (PID-CA)**, a discrete-timestep,
discrete-state cellular automaton in which each cell's next state is determined not by a static lookup rule, but by a
**local PID (Proportional-Integral-Derivative) controller** attempting to regulate the cell's active-neighbor count
toward a target value.

The system is to be implemented as a **browser-based application** using plain **HTML** and **modular ES6 JavaScript**,
with no external frameworks required. This document covers only the conceptual model, architecture, and module
specification — it contains no implementation code and no work estimates.

The purpose of the system is to allow interactive definition, simulation, and visual exploration of PID-CA rule spaces,
including tuning of gain parameters, target density, state-expression functions, and grid/boundary conditions.

---

## 2. Background & Motivation

Classical cellular automata (e.g., Conway's Life, Brian's Brain) define a cell's next state via a fixed, memoryless rule
table applied to the current neighborhood. Such systems are brittle: behavior is dictated entirely by the rule, and
small parameter changes tend to produce qualitatively different, hard-to-predict universes.

The PID-CA reframes the update rule as a **local control system**: each cell continuously measures the discrepancy
between its actual active-neighbor count and a desired target, and computes a control output using proportional,
integral, and derivative terms. This control output is then mapped onto a small discrete state space (2 or 3 states) to
produce the cell's next expressed state.

This reframing introduces properties absent from classical CA:

- **Local memory** (via the integral term), giving cells a persistent internal "frustration" or accumulated-error state.
- **Local anticipation** (via the derivative term), giving cells sensitivity to the _rate of change_ of their
  neighborhood.
- **Tunable dynamical regimes** (via gain parameters), allowing the same rule family to express qualitatively different
  behaviors — oscillatory, critically stable, or slow-creeping — without changing the rule structure itself.
- **An implicit global energy interpretation**, where the sum of squared neighbor-count errors across the grid behaves
  as a quantity the system tends to minimize.

This project specifies a tool for constructing, running, and observing such systems.

---

## 3. Conceptual Model

### 3.1 Grid and Neighborhood

The automaton operates on a two-dimensional grid of cells. Each cell has a neighborhood (e.g., Moore or von Neumann,
configurable) used to compute the **active-neighbor count** at each timestep.

- Grid dimensions are configurable.
- Boundary condition is configurable (wrapped/toroidal, fixed/dead-border, or reflective).
- Neighborhood shape and radius are configurable, decoupled from the core PID mechanism.

### 3.2 Error Signal

For a given cell `c` at discrete time `t`:

- `N_t(c)` — the count of active neighbors of `c` at time `t` (a neighbor is "active" according to a configurable
  definition relative to the state space, e.g., "state ≥ 1").
- `T` — the target active-neighbor count, which may be a global constant, a per-cell constant, or a spatially/temporally
  varying field.
- `e_t(c) = T - N_t(c)` — the instantaneous error for cell `c` at time `t`.

### 3.3 PID Terms

Each cell maintains its own scalar controller state, computed independently of other cells' controller states (locality
is preserved: a cell only reads neighbor _states_, not neighbor _controller internals_).

- **Proportional term:** `P_t = Kp * e_t`
- **Integral term:** `I_t = I_(t-1) + Ki * e_t`, optionally bounded (clamped) to prevent unbounded windup.
- **Derivative term:** `D_t = Kd * (e_t - e_(t-1))`
- **Control output:** `u_t = P_t + I_t + D_t`

Gains `Kp`, `Ki`, `Kd` are global configuration parameters shared by all cells (spatial variation of gains is a possible
extension but not required for the base system).

Each cell's persistent controller state consists of, at minimum: the previous error `e_(t-1)` and the accumulated
integral `I_(t-1)`. This constitutes the cell's "memory" and is carried forward between timesteps alongside its
expressed state.

### 3.4 State Expression Function

The control output `u_t` is discretized into the cell's next expressed state via a configurable **state-expression
function** `f`. Three canonical forms are specified:

1. **Binary threshold mapping** (2-state: 0/1)
   - State becomes `1` if `u_t` exceeds a threshold `θ`, otherwise `0`.

2. **Ternary quantization mapping** (3-state: 0/1/2)
   - State is chosen from three bands of `u_t`, separated by two thresholds `a < b`.

3. **Ternary semantic mapping** (3-state, dominant-term interpretation)
   - State reflects which PID term dominates the control output for that cell at that timestep (e.g., 0 = inactive, 1 =
     stabilizing/integral-dominant, 2 = active/proportional-dominant), giving the visualization a qualitative
     "regulatory mode" read-out in addition to raw activity.

4. **Probabilistic mapping** (optional extension)
   - State is chosen stochastically with activation probability `σ(u_t)` (logistic sigmoid), for
     stochastic/annealing-like variants.

The state-expression function is a pluggable component of the architecture (see §7.4) so that alternative mappings can
be substituted without altering the controller or grid mechanics.

---

## 4. State Model

The specification requires support for exactly two cardinalities of state space:

- **2-state model:** states `{0, 1}` — "inactive" / "active".
- **3-state model:** states `{0, 1, 2}` — supports either a magnitude-quantized reading of `u_t`, or a semantic
  dominant-term reading (§3.4, item 3).

Both models share the identical controller mechanism (§3.2–3.3); only the discretization function differs. The system
must allow switching between 2-state and 3-state modes at configuration time without altering the grid, neighborhood, or
controller logic.

"Active," for the purpose of computing `N_t(c)`, is a configurable predicate over state values (e.g., "state > 0" for
both 2- and 3-state models by default).

---

## 5. Parameters & Configuration

The following parameters constitute the full configuration surface of a PID-CA run:

| Parameter                 | Description                                                                                                          |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Grid width / height       | Dimensions of the cell grid                                                                                          |
| Boundary condition        | Toroidal, fixed, or reflective                                                                                       |
| Neighborhood type         | Moore / von Neumann, and radius                                                                                      |
| Target `T`                | Global scalar, or spatial field, or time-varying function                                                            |
| `Kp`, `Ki`, `Kd`          | PID gains                                                                                                            |
| Integral clamp            | Optional min/max bound on `I_t` accumulation                                                                         |
| State cardinality         | 2-state or 3-state                                                                                                   |
| State-expression function | Threshold, quantization, semantic, or probabilistic                                                                  |
| Threshold(s)              | `θ`, or `a`/`b` band edges, depending on expression function                                                         |
| Active predicate          | Definition of "active" state used for neighbor counting                                                              |
| Initial condition         | Random density, seeded pattern, or manual/painted grid                                                               |
| Update order              | Synchronous (all cells computed from a fixed snapshot) — required; asynchronous update is an explicit non-goal (§13) |
| Timestep rate             | Simulation speed (steps per second) for animation                                                                    |

Configuration must be exposed through the UI (§9) and representable as a serializable plain-object/JSON structure so
that presets can be saved, loaded, and shared.

---

## 6. System Architecture

The application is a static, client-side, browser-based system: a single `index.html` entry point loading modular ES6
JavaScript via `<script type="module">`. No build step, bundler, or server-side component is required by the
specification, though the modular structure should not preclude later bundling.

### 6.1 High-Level Module Map

- **`main.js`** — Application entry point; wires together configuration, model, controller, renderer, and UI; owns the
  simulation loop scheduler.
- **`config.js`** — Defines the configuration schema, default values, validation, and serialization (to/from plain
  objects / JSON) described in §5.
- **`grid.js`** — Owns the grid data structure: cell states, dimensions, boundary condition, neighborhood enumeration,
  and active-neighbor counting.
- **`controller.js`** — Implements the per-cell PID controller: error computation, term accumulation, integral clamping,
  and control-output computation. Operates over the grid's per-cell controller-state buffers.
- **`stateExpression.js`** — Implements the pluggable state-expression functions (threshold, quantization, semantic,
  probabilistic) that map control output to next discrete state.
- **`simulation.js`** — Orchestrates a single discrete timestep: synchronous read of current grid + controller state,
  computation of next grid + controller state, and atomic swap of buffers. Also manages initial-condition generation.
- **`renderer.js`** — Draws the grid to an HTML canvas (or equivalent DOM/graphics surface), mapping discrete states
  (and optionally continuous `u_t` values, for diagnostic overlays) to colors.
- **`ui.js`** — Builds and manages interactive controls (parameter sliders/inputs, play/pause/step, preset load/save,
  grid painting for initial conditions) and binds them to `config.js` and `simulation.js`.
- **`presets.js`** (optional) — A library of named default configurations demonstrating known behavioral regimes (§11).

### 6.2 Data Flow

1. `config.js` produces a validated configuration object.
2. `grid.js` initializes the state buffer and controller-state buffers according to the configuration's
   initial-condition rule.
3. On each scheduled tick, `simulation.js`:
   a. Iterates all cells, using `grid.js` to compute `N_t(c)` from the _current_ (frozen) state buffer. b. Uses
   `controller.js` to compute `e_t`, `P_t`, `I_t`, `D_t`, and `u_t` per cell, reading/writing per-cell controller-state
   buffers. c. Uses `stateExpression.js` to map each `u_t` to the cell's next state. d. Commits the new state buffer and
   controller-state buffers as the current ones (synchronous/simultaneous update — see §8).
4. `renderer.js` reads the current state buffer (and optionally the controller-state buffers, for diagnostics) and
   redraws the canvas.
5. `ui.js` reflects current configuration and simulation status, and dispatches configuration changes back into
   `config.js`/`simulation.js`.

This is a unidirectional data flow: configuration and prior state flow forward into the next state; no module reaches
"backward" into another module's internals.

---

## 7. Module Specifications

### 7.1 `config.js`

**Responsibility:** Single source of truth for all tunable parameters (§5).

- Defines default values for every parameter.
- Provides validation (e.g., grid dimensions positive integers, gains numeric, threshold ordering `a < b`).
- Provides serialization to/from a plain JSON-compatible object, enabling presets and shareable configuration strings.
- Exposes a change-notification mechanism so dependent modules (simulation, renderer, UI) can react to configuration
  edits without polling.

### 7.2 `grid.js`

**Responsibility:** Represent and query the spatial structure of the automaton.

- Maintains the current discrete-state buffer, sized to configured grid dimensions.
- Maintains the per-cell controller-state buffers (`e_(t-1)`, `I_(t-1)`) required by `controller.js`.
- Implements neighbor enumeration according to configured neighborhood type/radius and boundary condition.
- Implements the "active" predicate over state values and exposes a function to compute `N_t(c)` for any cell.
- Provides initial-condition population routines: uniform random density, seeded/patterned placement, and manual editing
  (single-cell set, for UI painting).

### 7.3 `controller.js`

**Responsibility:** Implement the per-cell PID mechanism described in §3.2–3.3, independent of grid topology and
independent of the state-expression function.

- Pure function (s) of: previous error, previous integral accumulator, current error, and gains — returning updated
  error, integral, derivative, and combined control output.
- Applies optional integral clamping per configuration.
- Has no knowledge of rendering, UI, or discretization — strictly a numeric control-signal computation, keeping the
  controller reusable independent of how its output is expressed.

### 7.4 `stateExpression.js`

**Responsibility:** Map a scalar control output `u_t` (and optionally the individual `P_t`, `I_t`, `D_t` components, for
the semantic mapping) to a next discrete state.

- Exposes each mapping variant from §3.4 as an independently selectable strategy.
- Strategy selection and associated thresholds are read from `config.js`.
- Must be swappable without any change to `controller.js` or `grid.js`, preserving the separation between "control
  computation" and "state discretization."

### 7.5 `simulation.js`

**Responsibility:** Advance the automaton by exactly one discrete timestep per invocation, and manage the timing loop
for continuous playback.

- Guarantees **synchronous update semantics**: every cell's next state and controller state is computed from a single,
  unmodified snapshot of the prior timestep (no read-after-write hazards within a step).
- Manages double-buffering (or equivalent) of state and controller-state buffers.
- Exposes step/play/pause/reset controls to `ui.js`.
- Delegates initial-condition generation to `grid.js` upon reset.

### 7.6 `renderer.js`

**Responsibility:** Visualize the current grid state.

- Renders the discrete state buffer to a canvas, with a distinct color per state value.
- Optionally supports a diagnostic overlay mode showing continuous `u_t` (or `I_t`) magnitude as a heatmap, to visualize
  the "frustration field" described conceptually in the background notes.
- Redraw is triggered once per simulation tick; rendering must not mutate simulation state.

### 7.7 `ui.js`

**Responsibility:** Present and bind all interactive controls.

- Parameter controls for every entry in §5 (grid size, boundary, neighborhood, target, gains, clamp, state cardinality,
  expression function + thresholds, initial condition, update rate).
- Playback controls: play, pause, single-step, reset.
- Grid painting: allow manual toggling of individual cell states as an initial condition or live intervention.
- Preset load/save, backed by `config.js` serialization and `presets.js`.

### 7.8 `presets.js` (optional)

**Responsibility:** Ship a small library of named configurations illustrating the behavioral regimes catalogued in §11
(e.g., "under-damped ripples," "critically-damped blobs," "frustration vortices"), for discoverability and demonstration
purposes.

---

## 8. Simulation Loop & Update Semantics

- The automaton **must** use synchronous (simultaneous) global updates: all cells compute their next state from the same
  fixed snapshot of the previous timestep's state and controller buffers. Asynchronous/random-order updates are
  explicitly out of scope (§13), to keep the control-theoretic interpretation (§3, §11) well-defined and reproducible.
- Each timestep is a pure function of (previous state buffer, previous controller-state buffers, configuration) → (next
  state buffer, next controller-state buffers). This makes runs deterministic given a fixed initial condition and
  configuration (with the exception of the optional probabilistic state-expression mode, which introduces controlled
  randomness).
- The simulation loop is decoupled from rendering: the tick rate (steps per second) is configurable independently of the
  browser's display refresh rate, and a "single step" control must be available for frame-by-frame inspection regardless
  of playback state.

---

## 9. Rendering & UI Design

- The application is a single HTML page containing a canvas (or equivalent) for grid visualization and a control panel
  for configuration (§7.7).
- Visual encoding:
  - 2-state mode: two colors (inactive/active).
  - 3-state mode: three colors, chosen so the semantic mapping (dominant PID term) and the quantization mapping are
    both legible.
  - Optional diagnostic overlay: a heatmap layer (toggleable) representing continuous control-output or
    integral-accumulation magnitude per cell, to make the "frustration field" and long-range temporal coupling
    described in the background notes directly observable.
- All configuration parameters from §5 must be live-editable during a paused simulation, and
  grid/neighborhood/target/gain changes must be reflected on the next step without requiring a full page reload.
- The UI must support saving the current configuration (and, optionally, the current grid state) as a preset, and
  loading a previously saved preset.

---

## 10. Emergent Behavior Classes (Reference Catalog)

The following behavior classes are known/expected outcomes of this model, drawn from the conceptual analysis that
motivated this specification, and should guide validation of the implemented system (i.e., these behaviors should be
_observable_ given appropriate parameter presets, per §7.8):

- **Under-damped regime** (high `Kp`, low `Kd`): rippling wavefronts, oscillatory density rings, "breathing" clusters.
- **Critically-damped regime** (balanced `Kp`/`Kd`): stable blobs, smooth morphogenesis, self-maintaining textures.
- **Over-damped regime** (high `Kd`): slow creep, frozen-in patterns, metastable glassy states.
- **Integral-driven frustration fields**: slow-moving "stress gradients" arising from accumulated error, visible via the
  diagnostic overlay (§9).
- **Derivative-driven anticipation**: shock-front stabilization, edge-tracking, pattern smoothing in response to rapidly
  changing neighborhoods.
- **Self-healing**: locally damaged regions of the grid are drawn back toward the target density by the controller.
- **Programmable morphogenesis** (extension): spatially or temporally varying target `T` produces pattern-following or
  shape-guided growth.

---

## 11. Extensibility Considerations

The architecture (§6–§7) is designed to accommodate the following future extensions without structural rework:

- Spatially or temporally varying target field `T(c, t)` (already anticipated in §5's configuration surface).
- Spatially varying gains (`Kp`, `Ki`, `Kd` as fields rather than scalars).
- Additional state-expression strategies beyond the four specified in §3.4, added as new modules conforming to the
  `stateExpression.js` interface.
- Alternative neighborhood topologies (hexagonal grids, arbitrary graphs) by extending `grid.js`'s neighbor-enumeration
  responsibility.
- Multi-agent/robotics reinterpretation, where each "cell" corresponds to a physical or simulated agent rather than a
  grid cell — feasible because `controller.js` has no dependency on grid geometry.

---

## 12. Non-Goals

- **No fixed-rule-table CA compatibility mode** — this system specifies a controller-based rule mechanism, not a general
  CA rule interpreter (e.g., it is not required to reproduce Conway's Life via lookup tables).
- **No asynchronous/random-order cell updates** (§8) — only synchronous global updates are in scope.
- **No server-side or persistent-storage component** — presets and configuration are handled client-side (in-browser
  export/import), with no backend service specified.
- **No performance/work estimation** — this document is a design and specification artifact only; scheduling,
  optimization targets, and implementation effort are explicitly excluded.
- **No build tooling requirement** — the modular ES6 structure is specified to run directly via native browser module
  loading; bundling/transpilation is not a requirement of this spec.
