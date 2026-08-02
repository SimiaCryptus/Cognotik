# Magnet Lattice Toy — Specification & Plan

## 1. Concept

A browser-based design and simulation app for a 3D-printed magnet toy.

Equal-strength cylindrical magnets are seated in sockets on a fixed grid.
Each magnet is an **upright magnetic disk** that can freely **rotate about
its own central (vertical) axis** but cannot translate. The magnet's
in-plane dipole moment therefore points somewhere in the 2D plane and is
described by a single angle θ.

The user first **places cores** (draw mode), then switches to a **dynamical
simulation** where the coupled dipoles evolve, seeking their minimum-energy
configuration and exhibiting oscillations/resonances about it.

### Scope constraints

- **2D only** (dipoles live in the plane of the grid).
- Standard **snap-to-grid** placement with configurable pitch.
- Arrangements are **copy/paste-able as JSON**.

---

## 2. Physical Model

### 2.1 Degrees of freedom

- Each magnet `i` has one DOF: its orientation angle `θ_i` (in-plane).
- Fixed position `r_i = (x_i, y_i)` on the grid (does not change).
- All magnets share the same dipole magnitude `m` (equal strength).
- Dipole vector: `μ_i = m (cos θ_i, sin θ_i)`.

### 2.2 Dipole–dipole interaction

For two coplanar dipoles at separation vector `d = r_j - r_i`, `r = |d|`,
unit vector `n = d / r`, the interaction energy is

    U_ij = (μ0 / 4π r^3) [ μ_i·μ_j - 3 (μ_i·n)(μ_j·n) ]

Total potential energy:

    U(θ) = Σ_{i<j} U_ij

Constants (`μ0`, geometric factors) are folded into a single tunable
coupling constant `k` so `U_ij = (k / r^3) [ μ_i·μ_j - 3 (μ_i·n)(μ_j·n) ]`.

### 2.3 Kinetic energy

Each disk has moment of inertia `I` about its axis:

    T = Σ_i (1/2) I θ_i_dot^2

### 2.4 Lagrangian

    L(θ, θ_dot) = T - U

Optional viscous damping term `-γ θ_i_dot` (used for relaxation / settling,
disabled for conservative resonance study).

---

## 3. Numerical Requirements

> "Use a least-action solver on the Lagrangian — precision matters."

### 3.1 Time integration (variational integrator)

We must **not** naively forward-Euler. Use a **variational / symplectic
integrator** derived from a discrete Lagrangian, so energy is conserved to
machine precision over long runs.

Plan:

- Discrete Lagrangian `L_d(θ_k, θ_{k+1}, h)` using a midpoint or trapezoidal
  quadrature.
- Discrete Euler–Lagrange (DEL) equations define the update:
  D2 L_d(θ_{k-1}, θ_k) + D1 L_d(θ_k, θ_{k+1}) = 0
- Solve the implicit step for `θ_{k+1}` with Newton iteration (analytic
  Jacobian of the torque field; see §3.3).
- Equivalent leapfrog/Störmer–Verlet fallback available for validation.

### 3.2 Torques (generalized forces)

Torque on magnet `i`: `τ_i = -∂U/∂θ_i`.
Derive analytically from the dipole energy (avoid finite differences in the
hot loop). Must be verified against a finite-difference check in tests.

### 3.3 Jacobian / Hessian

The Hessian `H_ij = ∂²U / ∂θ_i ∂θ_j` is needed for:

- Newton solve inside the implicit integrator.
- Linear stability / resonance analysis (§4).
  Provide analytic expressions and a finite-difference validator.

### 3.4 Energy diagnostics

Track and display total energy `E = T + U` over time; deviation must stay
within a tight tolerance for the symplectic scheme.

---

## 4. Stability / Resonance Analysis

Deliverable: a **coupling matrix** view.

### 4.1 Equilibrium finding

Minimize `U(θ)` to find an equilibrium `θ*`:

- Gradient descent / L-BFGS style minimizer, or
- Damped dynamics run to rest.

### 4.2 Linearization

At equilibrium, `M θ_ddot = -H(θ*) δθ`, with `M = I·Identity`.
Solve generalized eigenproblem `H v = ω² M v`:

- Eigenvalues → **normal-mode frequencies** `ω_n` (imaginary ⇒ unstable).
- Eigenvectors → **mode shapes**.

### 4.3 Coupling matrix visualization

- Render `H` (or the normalized coupling `C_ij = H_ij / sqrt(H_ii H_jj)`)
  as a heatmap.
- Show eigenvalue spectrum and let user select/animate a normal mode.
- Flag unstable / soft modes.

---

## 5. Application Modes

### 5.1 Draw / Edit mode

- Click on grid cells to add/remove a magnet.
- Optional per-magnet initial angle set by drag.
- Configurable grid pitch, extent, and snap behavior.
- Live count and validity indicators.

### 5.2 Simulation mode

- Play / pause / step / reset.
- Sliders: coupling `k`, inertia `I`, damping `γ`, timestep `h`.
- Real-time animation of rotating dipoles (arrows/disks).
- Energy & angular-momentum readouts.

### 5.3 Analysis mode

- "Relax to equilibrium" button.
- Coupling-matrix heatmap + eigen-spectrum.
- Normal-mode picker with animated preview.

---

## 6. Data Format (copy/paste JSON)

```json
{
    "version": 1,
    "grid": {
        "pitch": 20,
        "snap": true
    },
    "params": {
        "k": 1.0,
        "I": 1.0,
        "gamma": 0.0,
        "m": 1.0
    },
    "magnets": [
        {
            "id": 0,
            "cell": [0, 0],
            "theta": 0.0
        },
        {
            "id": 1,
            "cell": [1, 0],
            "theta": 1.5708
        }
    ]
}
```

- Positions stored as integer grid cells (`cell`), pixel/world coords
  derived from `pitch`.
- `theta` in radians.
- Import validates schema; export is pretty-printed.

---

## 7. Architecture (modular ES6, no build step)

Pure HTML + ES modules, runs from a static file server.

```
experiments/magnet-lattice/
  index.html
  src/
    main.js            # bootstrap, mode switching, UI wiring
    model/
      lattice.js       # magnet list, grid <-> world mapping
      physics.js       # energy, torque, Hessian (analytic)
      integrator.js    # variational/symplectic stepper + Newton
      analysis.js      # equilibrium, eigenproblem, coupling matrix
    io/
      serialize.js     # JSON import/export + schema validation
    ui/
      canvas.js        # 2D rendering (grid, dipoles, arrows)
      controls.js      # panels, sliders, buttons
      heatmap.js       # coupling matrix + spectrum render
    math/
      linalg.js        # small dense linear algebra (solve, eig)
  test/
    physics.test.js    # torque vs finite-diff, energy conservation
    integrator.test.js # symplectic energy drift bounds
    analysis.test.js   # known 2-magnet equilibrium & modes
```

### Module responsibilities

- **lattice**: source of truth for magnet set; grid math; add/remove.
- **physics**: stateless functions of `(state, params)` returning
  `U`, gradient `∇U` (= −τ), Hessian `H`.
- **integrator**: advances state; owns Newton solver using physics Jacobian.
- **analysis**: minimizer + generalized eigen-solver → modes & matrix.
- **linalg**: LU solve, symmetric eigen (Jacobi rotation) — small N.
- **ui/***: no physics; render + emit events only.
- **io**: versioned schema, validation, error reporting.

---

## 8. Correctness Strategy (precision is non-negotiable)

1. **Analytic derivatives validated** against central finite differences
   to `~1e-6` relative error (unit tests).
2. **Energy conservation**: symplectic integrator drift bounded and
   non-secular over 10^5 steps for a benchmark 2–3 magnet system.
3. **Analytic 2-magnet check**: known ground state (head-to-tail alignment)
   and its oscillation frequency compared to closed-form.
4. **Eigen-solver** verified on hand-computed small symmetric matrices.
5. Regression fixtures stored as JSON arrangements.

---

## 9. Milestones

1. **M0 – Skeleton**: HTML shell, module layout, empty modules, test runner.
2. **M1 – Lattice + IO**: grid, place/remove, JSON import/export round-trip.
3. **M2 – Physics core**: `U`, `∇U`, `H` + finite-diff tests.
4. **M3 – Integrator**: symplectic stepper + Newton; energy-drift tests.
5. **M4 – Simulation UI**: canvas animation, play/pause/step, params.
6. **M5 – Analysis**: equilibrium finder, eigen-solver, coupling heatmap,
   normal-mode animation.
7. **M6 – Polish**: validation UX, presets, docs, performance pass.

---

## 10. Open Questions

- Boundary/self-field effects of finite-size disks vs pure point dipoles?
- Should we support unequal magnet strengths later (schema already allows
  per-magnet extension)?
- Constrain θ to discrete detents to mimic physical printed sockets?
- Performance ceiling: target max magnet count for interactive Hessian eig?
