# PID-Regulated Cellular Automaton

Reference implementation of the specification in `idea.md`: a discrete-state, discrete-time cellular automaton whose
update rule is a **per-cell PID controller** regulating the cell's active-neighbour count `N_t(c)` toward a target `T`.

## Running

ES modules require an HTTP origin (they will not load from `file://`):

```
python3 -m http.server 8080
# then open http://localhost:8080/index.html
```

No build step, bundler, or backend is required.

## Module map

| File                    | Role                                                                                                          |
| ----------------------- | ------------------------------------------------------------------------------------------------------------- |
| `js/config.js`          | Parameter schema, defaults, validation, JSON/hash serialisation, change notification                          |
| `js/grid.js`            | State + controller-state buffers, neighbour enumeration, boundary conditions, initial conditions, seeded PRNG |
| `js/controller.js`      | Pure PID computation: `(e_(t-1), I_(t-1), e_t, gains) -> (P, I, D, u)` with anti-windup clamping              |
| `js/stateExpression.js` | Pluggable `u_t -> state` strategies: threshold, quantisation, semantic, probabilistic                         |
| `js/simulation.js`      | One synchronous timestep per call, double buffering, playback scheduling, statistics                          |
| `js/renderer.js`        | Canvas drawing of states plus optional `u_t` / `I_t` / `e_t` heat overlay                                     |
| `js/ui.js`              | Schema-driven control panel, playback buttons, grid painting, presets, export/import                          |
| `js/presets.js`         | Named configurations for the behaviour classes in §10 of the spec                                             |
| `js/main.js`            | Wiring + frame scheduler                                                                                      |

## Update semantics

Each step is a pure function of `(states_t, prevError_t, integral_t, config)`. All cells read a frozen snapshot; results
are written to shadow buffers and swapped atomically (`Grid.commit()`), so runs are deterministic for a given seed and
configuration — except in probabilistic expression mode, whose randomness is itself seeded by `config.seed`.

## Controls

- **Space** play/pause, **S** single step, **R** reset, **C** clear.
- Click/drag the grid to paint cells; **shift**-drag or right-drag erases.
- Every parameter is live-editable; grid-size changes reallocate and reset.
- _Copy share link_ encodes the non-default parameters into the URL hash.
- _Export/Import JSON_ round-trips the full configuration for presets.

## Extending

- New expression regime: add an entry to `STRATEGIES` in `stateExpression.js`
  and to the `expression` enum options in `config.js`.
- New topology: extend `neighborOffsets` / `countActiveNeighbors` in `grid.js`.
- Spatial gain fields: replace the scalar reads of `cfg.kp/ki/kd` inside
  `simulation.step()` with per-cell buffers; `controller.js` needs no change.
