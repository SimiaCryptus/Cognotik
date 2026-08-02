# Magnet Lattice Toy

Browser-based design & simulation of a 3D-printed rotating-magnet toy.
See `idea.md` for the full specification.

## Running the app

No build step. Serve the folder statically and open `index.html`:

```sh
# from the repository root
cd experiments/magnet-lattice
python3 -m http.server 8000
# then open http://localhost:8000/
```

(ES modules require a server; opening `index.html` via `file://` will not work.)

## Usage

1. **Draw** mode — click grid cells to add/remove magnets.
   Shift-drag on a magnet to set its initial angle.
2. **Simulate** mode — Play/Step/Reset the coupled-dipole dynamics.
   Tune coupling `k`, inertia `I`, damping `γ`, and timestep `h`.
   Choose the Störmer–Verlet or variational (Newton) integrator.
   Energy / angular-momentum diagnostics update live.
3. **Analyze** mode — relax to equilibrium, compute the coupling-matrix
   heatmap and normal-mode spectrum, and animate a selected mode.

Use **Export**/**Import** to copy/paste arrangements as JSON.

## Physics

Each magnet is a fixed-position disk with one rotational DOF `θ`.
Pairwise dipole energy:

```
U_ij = (k / r³) [ μ_i·μ_j − 3 (μ_i·n)(μ_j·n) ]
```

folded into the compact trig form (see `src/model/physics.js`):

```
U_ij = (k m² / r³) [ cos(θ_i − θ_j) − 3 cos(θ_i − φ) cos(θ_j − φ) ]
```

with `φ` the angle of the separation vector. Analytic gradient and Hessian
are provided and validated against finite differences.

## Tests

```sh
node test/run.js
```

Covers:

- analytic gradient / Hessian vs finite differences (`~1e-5`),
- energy-conservation drift bounds for both integrators,
- damping dissipation,
- LU solve & Jacobi eigen-solver on known matrices,
- 2-magnet equilibrium & normal modes.

## Module map

| Module                | Responsibility                                        |
| --------------------- | ----------------------------------------------------- |
| `model/lattice.js`    | magnet set, grid ↔ world mapping                      |
| `model/physics.js`    | energy, gradient/torque, Hessian (analytic + FD)      |
| `model/integrator.js` | Verlet + variational (Newton) steppers                |
| `model/analysis.js`   | relaxation, generalized eigenproblem, coupling matrix |
| `io/serialize.js`     | versioned JSON import/export + validation             |
| `ui/canvas.js`        | grid / dipole rendering                               |
| `ui/heatmap.js`       | coupling matrix + spectrum rendering                  |
| `math/linalg.js`      | LU solve, symmetric Jacobi eigen                      |
