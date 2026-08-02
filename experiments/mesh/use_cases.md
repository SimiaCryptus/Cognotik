# Use Cases — Constrained Mesh Enclosure Lab

This document collects concrete applications for the constrained mesh
enclosure lab. Each entry names the _energy configuration_ that drives it,
the _mechanism_ by which the geometry responds, and the _practical payoff_.
These are motivating scenarios, not benchmarks — they explain **why** a
particular combination of the fitness terms in `math_outline.md §1` is
interesting.

---

## 1. Sticker / Decal Regions via Vertex-Fidelity Penalty

- **Configuration.** Dominant `λ_fit` on a chosen landmark set `{tᵢ}`
  (outline §1.3), moderate `λ_area` for shrink-wrap, light `λ_smooth`.
- **Mechanism.** Pinning vertices to targets locally suppresses motion, so
  the surrounding surface must absorb curvature elsewhere — curvature is
  _diverted_ away from the pinned patch, leaving it comparatively flat.
- **Payoff.** Shrink-wrap a model while reserving flat, low-distortion
  panels for decals, stickers, or printed labels. The keep-out constraint
  guarantees the wrap still clears the inner mesh `K` by `δ_safe`.

## 2. Edge-Length Distribution Collapse → Uniform Tessellation

- **Configuration.** `λ_len` driving the edge-length entropy term toward its
  degenerate optimum (outline §1.4), with `λ_area`/`λ_ang` as tie-breakers.
- **Mechanism.** As the length KDE collapses toward a single mode, edges
  (and hence faces) become near-congruent. The entropy term saturates at
  `ln(#edges)` when all lengths contribute equal kernel mass.
- **Payoff.**
- _Aesthetics._ Visually regular, evenly-sized facets.
- _Manufacturing._ Similar edges/faces simplify tooling, panelization,
  and repeated-part fabrication.
- _Engineering._ Symmetric, homogeneous structures can yield predictable
  mechanical behavior (e.g. controlled resonance modes, uniform stress
  distribution).

## 3. Dihedral L1 Loss → Faceting / Planar Grouping

- **Configuration.** An L1 penalty on dihedral angles `φ_e` across edges
  (outline §2), optionally targeting `φ_e = 0`.
- **Mechanism.** L1 sparsity drives most dihedral angles to exactly zero,
  merging adjacent triangles into coplanar groups — effectively producing
  _nontriangular (planar) faces_ out of a triangulated mesh while keeping a
  few sharp creases.
- **Payoff.** Low-facet-count "developable-ish" surfaces suited to flat
  panel manufacturing (sheet metal, folded card, architectural cladding).
- **Open question.** Dihedral _inversion_ (fold-over) must be constrained —
  define a valid dihedral region so the L1 pull does not flip faces through
  one another (couples to the self-intersection guarantees C2/C3).

## 4. Volume Matching → Inflate / Deflate to Spec

- **Configuration.** Dominant `λ_vol · |V − V*|` (outline §1.2) with light
  smoothing.
- **Mechanism.** The signed divergence-theorem volume is pushed toward a
  target `V*`; the mesh inflates or deflates while CCD keeps it clear of
  `K` and itself.
- **Payoff.** Generate enclosures of a prescribed internal capacity around a
  fixed keep-out volume — packaging, containment shells, or fit-to-budget
  casings.

## 5. Minimal-Area Shrink-Wrap → Tight Offset Surface

- **Configuration.** Dominant `λ_area` (outline §1.1), `δ_safe` clearance.
- **Mechanism.** Area minimization pulls the surface inward until arrested
  by the keep-out constraint at clearance `δ_safe`, forming contact patches
  where curvature permits.
- **Payoff.** A discrete analogue of a convex-hull / minimal-enclosure
  surface — protective skins, conformal covers, collision proxies.

## 6. Anti-Sliver / Triangle-Quality Regularization

- **Configuration.** `λ_ang` with the anti-sliver barrier `−log sin θ` or
  the equilateral target `(θ − π/3)²` (outline §1.5).
- **Mechanism.** The angular functional penalizes degenerate corners,
  keeping triangles well-shaped as the surface deforms.
- **Payoff.** A conditioning term for any of the above regimes: healthier
  elements improve both the numerical robustness of the energies (fewer
  `1/‖·‖` singularities, outline §1.7) and downstream simulation/meshing.

## 7. Optimizer Fingerprinting (Research Instrument)

- **Configuration.** Any degenerate-optimum regime (esp. §2 above) run with
  Adam vs. L-BFGS vs. QQN.
- **Mechanism.** When many meshes tie on the energy, the optimizer's
  dynamics — interacting with the corners of the feasible set (simultaneous
  multi-contact) — select _which_ enclosure is realized.
- **Payoff.** A reproducible study of constrained-descent behavior: identical
  mesh + identical energy + different optimizer → visibly distinct
  converged geometries at equal fitness (see `novelty_utility.md §3`).
