# Bioelectrical Membrane Extension (Simplified)

## Domain Specification for the PID-Regulated Cellular Automaton

---

## 1. Purpose & Scope

This document specifies a **simplified bioelectrical membrane model** to be run inside the existing PID-CA
simulation scaffolding (see `idea.md`). Each cell in the grid is reinterpreted as a **patch of excitable
membrane** carrying a scalar membrane potential and a small voltage-gated channel population.

The goal is _not_ biophysical fidelity. The goal is to fit a recognisable bioelectrical behaviour class
(thresholded gating, spiking, refractoriness, propagating depolarisation waves) into the **existing**
grid + per-cell-state + synchronous-update architecture, with the smallest possible number of new concepts.

Scope is deliberately narrow:

- A cell has a membrane potential `V`.
- A cell has one **gate** which may be open or closed.
- The gate is **voltage-operated**: it opens when `V` crosses a threshold.
- While open, the gate **releases ions**, which drops the membrane potential toward a fixed release potential.
- Gate behaviour is shaped by **configurable timing mechanics**: a reset (refractory) period, and an optional
  minimum open time.

---

## 2. Simplifying Assumptions (Explicit)

The following are **intentionally not modelled** in this iteration:

- **No osmotic gradients.** There is no water, no volume, no pressure.
- **No honest ion transport.** There are no ion species, no concentrations, no conservation of charge or mass.
  "Releasing ions" is a purely phenomenological statement: it means _the potential moves toward a configured
  release potential at a configured rate._
- **No Nernst / GHK equilibrium computation.** Reversal potentials are configuration constants, not derived
  from concentrations.
- **No channel population statistics.** Each cell has one abstract gate with a binary open/closed state, not a
  stochastic ensemble of channels with open probabilities.
- **No ATP / pump energetics.** The restoring drift toward rest is a fixed leak/pump term with no cost model.
- **No multi-compartment geometry.** One potential per cell, no intracellular/extracellular split.

These are catalogued in §10 as candidate future extensions; the present model is a scaffold that should not
make them harder to add later.

---

## 3. Conceptual Model

### 3.1 Cell Substrate

Each cell `c` on the grid (dimensions, neighbourhood, and boundary conditions inherited unchanged from
`grid.js`, §3.1 of `idea.md`) carries:

| Field       | Type    | Meaning                                                                |
| ----------- | ------- | ---------------------------------------------------------------------- |
| `V`         | float   | Membrane potential (arbitrary units; defaults chosen in mV-like range) |
| `gate`      | enum    | `CLOSED` \| `OPEN` \| `REFRACTORY`                                     |
| `openTicks` | integer | Timesteps elapsed since the gate opened                                |
| `restTicks` | integer | Timesteps remaining in the refractory/reset period                     |

`V` is the **continuous** substrate; `gate` is the **discrete** substrate. The displayed CA state (§7) is
derived from these, exactly as the PID-CA's displayed state is derived from `u_t`.

### 3.2 Gate as a State Machine

The gate is a three-phase machine driven by voltage and timers:

CLOSED --[ V crosses V_threshold ]--> OPEN
OPEN --[ openTicks >= minOpen AND V past V_close ]--> REFRACTORY
REFRACTORY --[ restTicks == 0 ]--> CLOSED

- **`CLOSED`** — gate is shut and _armed_. It will fire when the threshold comparator trips.
- **`OPEN`** — gate is conducting; the release current is active.
- **`REFRACTORY`** — gate is shut and _disarmed_. The threshold comparator is ignored for `resetPeriod` ticks.
  This is what prevents immediate re-opening and what makes wave propagation directional rather than
  ping-ponging between neighbours.

### 3.3 Trigger Polarity

The comparator polarity is configurable, because "the gate operates on voltage" is ambiguous about direction:

- `polarity: "depolarizing"` (default) — gate opens when `V >= V_threshold`, i.e. the cell has been pushed
  _up_ past threshold by neighbour coupling or external stimulus.
- `polarity: "hyperpolarizing"` — gate opens when `V <= V_threshold`.

In either case, **opening drops the potential**: the release potential `V_release` is configured below
`V_rest`, so an open gate pulls `V` downward. With the default depolarizing polarity this produces the
familiar spike-then-collapse shape.

### 3.4 Voltage Dynamics

Per timestep, `V` is advanced by three additive contributions plus optional stimulus:

dV = leak + coupling + release + stimulus

leak = k_leak * (V_rest - V)
coupling = k_coupling * Σ_{n ∈ N(c)} (V_n - V)
release = (gate == OPEN) ? k_release * (V_release - V) : 0
stimulus = externally injected current for painted / clamped cells (default 0)

V' = clamp(V + dV, V_min, V_max)

- **`leak`** is the lumped leak-plus-pump homeostat: in the absence of everything else, `V` relaxes
  exponentially back to `V_rest`. This replaces honest ion transport (§2).
- **`coupling`** is a discrete diffusion / gap-junction term over the configured neighbourhood. It is the
  _only_ inter-cell interaction in the model, and it is what makes waves propagate.
- **`release`** is the gate's effect: a first-order pull toward `V_release` while open. Strength `k_release`
  should normally dominate `k_leak` by an order of magnitude, otherwise the gate cannot win against the
  homeostat and no spike forms.
- `V_min` / `V_max` clamp the potential to a sane range and prevent numeric runaway from aggressive gains.

All coefficients are per-timestep fractions in `[0, 1]`, i.e. this is forward-Euler integration with `dt`
folded into the constants. No adaptive stepping.

### 3.5 Timing Mechanics

Two configurable timers shape gate behaviour:

- **`resetPeriod`** (integer ≥ 0) — the refractory length. After the gate closes it enters `REFRACTORY` for
  this many ticks, during which the voltage comparator is ignored. `resetPeriod = 0` disables refractoriness
  (gate may re-arm on the very next tick). This is the primary knob for spike rate and for wave directionality.

- **`minOpenTicks`** (integer ≥ 0) — the minimum number of ticks the gate must stay open once triggered,
  regardless of voltage. `minOpenTicks = 0` disables it.

**On the necessity of `minOpenTicks`:** the original note flagged this as "possibly not needed", and that is
correct _if_ the close condition uses hysteresis (§3.6) and `k_release` is large enough to move `V` decisively
past `V_close` within one tick. It becomes necessary when:

1. `k_release` is small, so a single tick's release does not clear the close condition and the gate would
   chatter open/closed at the comparator boundary; or
2. coupling is strong enough that neighbours hold `V` above `V_close` — the gate would then latch open
   indefinitely without a timed exit; or
3. you want the _pulse width_ of the release event to be a directly tunable parameter (useful for controlling
   wave speed and refractory overlap).

It is therefore retained as a first-class, defaulted-to-`1`, disable-able parameter rather than removed.

### 3.6 Close Condition & Hysteresis

The gate closes when **both** hold:

- `openTicks >= minOpenTicks`, and
- the voltage has retreated past a separate close threshold `V_close`, on the opposite side of the trigger:
  for depolarizing polarity, `V <= V_close`, with `V_close < V_threshold`.

The gap `V_threshold - V_close` is the **hysteresis band**. It is what prevents the comparator from chattering
on a cell sitting exactly at threshold. Configuration validation must enforce the ordering
`V_release < V_close < V_threshold` (mirrored for hyperpolarizing polarity).

Optionally, `closeMode: "timed"` bypasses the voltage condition entirely and closes the gate purely on
`openTicks >= minOpenTicks` — a simpler, fully deterministic pulse generator. Both modes must be selectable.

---

## 4. Update Rule (One Timestep)

Synchronous update semantics are inherited unchanged from §8 of `idea.md`: every cell's next state is computed
from a single frozen snapshot of the previous timestep. Within a cell, the ordering is fixed:

for each cell c, reading only snapshot values:

    1. SENSE
       V      = snapshot.V[c]
       gate   = snapshot.gate[c]
       Vn_sum = Σ over neighbours of (snapshot.V[n] - V)

    2. INTEGRATE VOLTAGE
       dV = k_leak * (V_rest - V)
          + k_coupling * Vn_sum
          + (gate == OPEN ? k_release * (V_release - V) : 0)
          + stimulus[c]
       V_next = clamp(V + dV, V_min, V_max)

    3. ADVANCE GATE  (evaluated against V_next)
       switch gate:
         CLOSED:
           if comparator_trips(V_next):
             gate_next = OPEN;  openTicks_next = 0
           else
             gate_next = CLOSED
         OPEN:
           openTicks_next = openTicks + 1
           if close_condition(openTicks_next, V_next):
             gate_next = REFRACTORY;  restTicks_next = resetPeriod
           else
             gate_next = OPEN
         REFRACTORY:
           restTicks_next = restTicks - 1
           gate_next = (restTicks_next <= 0) ? CLOSED : REFRACTORY

    4. EXPRESS
       displayState[c] = expressBioelectrical(V_next, gate_next)   // §7

commit all *_next buffers atomically

Two ordering decisions are load-bearing and must be honoured by any implementation:

- **Voltage integrates before the gate advances**, so a cell that crosses threshold this tick begins releasing
  on the _next_ tick, not the same one. This gives the model a one-tick conduction delay per cell, which is
  what sets wave speed.
- **Neighbour voltages are read from the snapshot only.** No cell may observe another cell's gate state or its
  updated voltage. Locality and determinism are preserved exactly as in the PID-CA core.

---

## 5. Parameters

| Parameter          | Type  | Default        | Description                                               |
| ------------------ | ----- | -------------- | --------------------------------------------------------- |
| `V_rest`           | float | `-70`          | Resting potential; leak target                            |
| `V_threshold`      | float | `-55`          | Gate trigger threshold                                    |
| `V_close`          | float | `-60`          | Gate close threshold (hysteresis lower edge)              |
| `V_release`        | float | `-90`          | Potential the open gate drives toward ("ions released")   |
| `V_min` / `V_max`  | float | `-100`/`+40`   | Hard clamp on membrane potential                          |
| `k_leak`           | float | `0.05`         | Per-tick relaxation rate toward `V_rest`                  |
| `k_coupling`       | float | `0.08`         | Per-tick gap-junction diffusion coefficient per neighbour |
| `k_release`        | float | `0.60`         | Per-tick release rate toward `V_release` while open       |
| `polarity`         | enum  | `depolarizing` | Comparator direction (§3.3)                               |
| `closeMode`        | enum  | `hysteresis`   | `hysteresis` \| `timed` (§3.6)                            |
| `minOpenTicks`     | int   | `1`            | Minimum gate open duration; `0` disables                  |
| `resetPeriod`      | int   | `6`            | Refractory length in ticks; `0` disables                  |
| `initialCondition` | enum  | `uniform-rest` | `uniform-rest`, `noisy-rest`, `seeded-spike`, `painted`   |
| `noiseAmplitude`   | float | `0`            | Optional per-tick Gaussian jitter added to `dV`           |
| `stimulus`         | field | `0`            | Per-cell injected current; UI painting writes here        |

Grid dimensions, neighbourhood type/radius, boundary condition, and tick rate are inherited from the base
configuration surface (§5 of `idea.md`) and are **not** redefined here.

**Validation rules:**

- `V_release < V_close < V_threshold` for depolarizing polarity (reverse the ordering for hyperpolarizing).
- `V_min <= V_release` and `V_threshold <= V_max`.
- All `k_*` in `[0, 1]`; `k_coupling * maxNeighbourCount <= 1` to keep forward-Euler diffusion stable.
- `minOpenTicks >= 0`, `resetPeriod >= 0`, both integers.

---

## 6. Mapping onto the PID-CA Architecture

The extension is additive. No existing module changes responsibility.

### 6.1 Reused unchanged

- **`grid.js`** — dimensions, boundary conditions, neighbour enumeration. The membrane model adds two new
  per-cell buffers (`V`, plus a packed gate/timer record) alongside the existing state buffer; buffer
  allocation and double-buffering follow the existing pattern.
- **`simulation.js`** — snapshot/compute/commit cycle, play/pause/step/reset, tick scheduling.
- **`renderer.js`** — canvas drawing; gains one additional colour map (§7).
- **`config.js`** — schema is extended with the §5 block and its validation rules; serialization is unchanged.

### 6.2 New module: `membrane.js`

**Responsibility:** implement §3.4–§3.6 and step 2–3 of §4 as pure functions of
`(V, gate, openTicks, restTicks, neighbourSum, stimulus, params)` → `(V', gate', openTicks', restTicks')`.

It has no knowledge of grid topology (it receives a pre-computed neighbour sum), no knowledge of rendering,
and no knowledge of discretisation — mirroring exactly the contract `controller.js` holds in the base system.
This is what makes the two domains swappable.

### 6.3 Relationship to `controller.js`

Two integration modes are specified, selectable by configuration:

1. **`mode: "membrane-only"`** (default for this document) — the PID controller is bypassed. `membrane.js`
   alone determines cell evolution. This is the minimal, honest version of the model.

2. **`mode: "pid-homeostat"`** (extension) — the PID controller is repurposed as the cell's _homeostatic
   regulator_, replacing the fixed `k_leak` term. The controlled variable becomes the membrane potential
   itself rather than the active-neighbour count:

   e_t = V_target - V_t
   u_t = Kp*e_t + I_t + Kd*(e_t - e_{t-1})
   leak := u_t // replaces k_leak * (V_rest - V)

This is a genuinely interesting configuration: the integral term becomes an **adaptation / accommodation**
mechanism (a cell chronically held off-target slowly re-tunes its own resting drive), and the derivative
term becomes **rate sensitivity** — a cell that fires only on _fast_ depolarisation, ignoring slow drift,
which is a real property of biological accommodation. It is specified here but is not required for the
base implementation.

### 6.4 `stateExpression.js`

A new strategy, `bioelectrical`, is registered. It maps `(V, gate)` to a displayed discrete state per §7,
conforming to the existing pluggable-strategy interface and requiring no change to the module's contract.

---

## 7. State Expression & Rendering

**Discrete display state (3-state model):**

| State | Name       | Condition            | Suggested colour |
| ----- | ---------- | -------------------- | ---------------- |
| `0`   | Polarized  | `gate == CLOSED`     | dark / blue      |
| `1`   | Firing     | `gate == OPEN`       | bright / white   |
| `2`   | Refractory | `gate == REFRACTORY` | dim / red        |

This is the direct analogue of Brian's Brain, and is the recommended default view: propagating wavefronts read
clearly as bright leading edges trailed by red refractory wake.

**Diagnostic overlay (continuous):** the existing heatmap overlay is reused to render `V` directly, mapped
linearly from `V_min` (cold) through `V_rest` (neutral) to `V_max` (hot). This makes sub-threshold summation —
the slow build-up of depolarisation from neighbour coupling that has not yet tripped any gate — visible, which
the discrete view cannot show. Overlay and discrete view must be independently toggleable.

**UI additions:** all §5 parameters as live-editable controls; a "paint stimulus" tool that writes into the
`stimulus` field (click-drag to inject current, so the user can trigger waves manually); and a "clamp cell"
toggle that pins a cell's `V` to a fixed value, for building pacemakers and boundaries.

---

## 8. Expected Behaviour Classes

These should be reachable via presets and serve as validation targets:

- **Quiescence** — weak coupling, everything relaxes to `V_rest` and stays there. Sanity baseline.
- **Single propagating wavefront** — a seeded spike with `resetPeriod > 0` produces an annular wave that
  expands and self-extinguishes at the boundary (or annihilates on collision with another wave). This is the
  core validation case: refractoriness is what prevents back-propagation.
- **Spiral waves / reentry** — a wavefront broken by a refractory or clamped region curls into a rotating
  spiral. Requires `resetPeriod` tuned relative to wave speed; this is the classic excitable-media signature
  and its appearance is strong evidence the timing mechanics are correct.
- **Pacemaker-driven trains** — a clamped depolarized cell emits periodic waves at a rate set by
  `resetPeriod + minOpenTicks` plus recovery time.
- **Wave-speed control** — increasing `k_coupling` increases propagation speed; increasing `minOpenTicks`
  widens the wavefront. Both should be directly observable and monotonic.
- **Conduction block** — a band of cells with elevated `V_threshold` (or a clamped region) blocks or refracts
  incoming waves, permitting simple "circuitry".
- **Chatter (failure mode)** — with `closeMode: hysteresis`, zero hysteresis band, and small `k_release`, gates
  oscillate at the comparator boundary. This is the pathology `minOpenTicks` exists to suppress (§3.5); the
  preset library should include it as a deliberate counter-example.

---

## 9. Validation & Sanity Checks

- **Isolated cell, no stimulus:** `V` converges monotonically to `V_rest`; gate never leaves `CLOSED`.
- **Isolated cell, supra-threshold stimulus pulse:** exactly one open/refractory cycle, of duration
  `≥ minOpenTicks` open and exactly `resetPeriod` refractory, then return to `CLOSED`.
- **Stimulus during refractory period:** produces no firing, regardless of magnitude.
- **Two adjacent cells, one seeded:** the second fires with a delay of at least one tick; no third firing of
  the first cell while `resetPeriod > 0` and coupling is below the re-excitation limit.
- **Determinism:** identical configuration + identical initial condition + `noiseAmplitude = 0` produces a
  bit-identical run.
- **Stability:** with `k_coupling * maxNeighbourCount <= 1`, `V` never exits `[V_min, V_max]` other than by
  the explicit clamp, and no oscillation is introduced by the diffusion term alone.

---

## 10. Non-Goals & Deferred Extensions

Restating §2 as an explicit boundary, with the natural next steps flagged:

| Deferred                      | Why deferred                                 | What it would need                              |
| ----------------------------- | -------------------------------------------- | ----------------------------------------------- |
| Osmotic gradients / volume    | Requires water flux and pressure; large      | Per-cell volume state, osmolarity, flux term    |
| Honest ion transport          | Requires species, concentrations, balance    | Per-species concentration buffers, conservation |
| Nernst / GHK potentials       | Depends on concentrations existing first     | Derived reversal potentials from §above         |
| Multiple gate types per cell  | Doubles state and interaction surface        | Vector of gates, summed conductances            |
| Stochastic channel gating     | Breaks determinism guarantee (§9)            | Per-gate open probability, seeded RNG           |
| Active pumps with energy cost | No metabolic model exists                    | ATP field, pump rate as function of supply      |
| Ligand / mechanical gating    | Out of scope: "the gate operates on voltage" | Additional gating inputs to the comparator      |

None of these are precluded by the architecture: `membrane.js` is a pure per-cell transition function, so
richer per-cell state can be added behind the same interface, and `grid.js` already owns arbitrary per-cell
buffer allocation.
