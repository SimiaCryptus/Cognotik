# Novelty & Utility Analysis — Constrained Mesh Enclosure Lab

This document assesses the **novelty** and **utility** of the lab described in
`idea.md` / `math_outline.md`, relative to established work in mesh
optimization, physical simulation, and geometry processing. It follows the
same evaluative frame used across the sibling labs: separate the genuinely
new contributions from the recombination of known techniques, and state where
the construction is useful versus where it is primarily a research/teaching
artifact.

---

## 1. Summary Judgement

- **Novelty: moderate, mostly in _combination_.** Each individual component
  (CCD, trust-region descent, entropy fitness, mesh energies) is
  well-established. The novel move is fusing an **exact CCD hard-constraint
  wall** with a **fitness landscape whose optima are deliberately degenerate**
  (edge-length entropy), then using the _optimizer identity_ as the selection
  mechanism among tied enclosures. This "optimizer fingerprinting under hard
  geometric constraints" framing is the distinctive contribution.
- **Utility: high as a controlled testbed, modest as a production tool.** The
  lab is a clean instrument for studying constrained descent dynamics; it is
  not competitive with dedicated shrink-wrap / remeshing pipelines for
  end-user geometry tasks.

---

## 2. Prior Art Landscape

| Ingredient                     | Established in                                 | This lab's relation                              |
| ------------------------------ | ---------------------------------------------- | ------------------------------------------------ |
| Continuous collision detection | Cloth/rigid-body sim (Bridson, Provot, Brochu) | Reused; standard cubic-coplanarity narrow phase  |
| Shrink-wrap / offset surfaces  | Geometry processing, mesh fitting              | Reused as one _regime_, not the whole objective  |
| Trust-region descent           | Numerical optimization (Nocedal & Wright)      | Reused; per-vertex adaptation is a light twist   |
| Cotangent Laplacian / fairing  | Desbrun, Meyer, Pinkall–Polthier               | Reused as a regularizer                          |
| Discrete curvature (deficit)   | Discrete differential geometry                 | Used for monitoring, not new                     |
| Entropy-of-geometry fitness    | Sibling **Geometric Entropy Lab**              | Ported from pairwise-distance to edge-length KDE |
| Optimizer fingerprinting       | Sibling labs (entropy, no-three-in-line)       | Extended into a topological/constrained setting  |

The honest reading: **none of the machinery is new**. The novelty is at the
system level.

## 3. What Is Genuinely Novel

1. **Hard-wall CCD as a constraint on an _optimizer_, not a _simulator_.**
   CCD is normally a component of a time-stepping physics integrator. Here it
   filters the _line-search step_ of a quasi-Newton optimizer, with the
   explicit reframing that `t` parameterizes the step, not a clock. Coupling
   the TOI truncation to **optimizer state resets** (stale L-BFGS/QQN
   curvature across a projection discontinuity) is the specific, defensible
   new detail — it is the same lesson as teleport-resets in the
   no-three-in-line lab, transported into a collision setting.

2. **Degenerate-optimum selection under topology.** The edge-length entropy
   term saturates at `ln(#edges)`; a large family of meshes tie. Adding a
   _fixed connectivity manifold_ plus _exact non-penetration_ means the tie is
   broken by the interaction of optimizer dynamics with the feasible-set
   geometry (its corners = simultaneous multi-contact). Studying _which_
   enclosure each optimizer selects is a question that, to our knowledge, is
   not posed elsewhere in this exact form.

3. **Curvature-as-decision-variable coupling.** Because `F` is static, vertex
   positions alone carry the discrete curvature, so the entropy term doubles
   as a _curvature-distribution_ controller and the angular term regularizes
   the discrete second fundamental form directly. This dual reading of a
   single energy term is a modestly novel framing.

## 4. What Is Not Novel (and Should Not Be Claimed)

- The energy functionals (§1 of the outline) are textbook.
- The CCD predicates (cubic coplanarity, segment–segment distance,
  Möller triangle–triangle audit) are standard and should cite the source
  literature rather than present as original.
- Projection-with-offset and delta-scaling resolutions are conventional
  contact-response heuristics.
- The trust-region ratio test is classical; the only wrinkle is per-vertex
  granularity and its coupling to broad-phase cell size.

## 5. Utility Assessment

### 5.1 As a research instrument (high utility)

- **Clean separation of soft vs. hard constraints.** Because C1–C3 are exact
  rather than penalty terms, energy weights can be tuned without balancing a
  barrier stiffness. This makes it a good bench for isolating optimizer
  behavior from constraint-penalty artifacts.
- **Reproducible fingerprinting.** Identical initial mesh + identical energy +
  different optimizer → visibly different converged enclosures at equal
  entropy. That is a crisp, demonstrable phenomenon.
- **Stress test for CCD/optimizer coupling.** The stiff multi-contact corners
  of the feasible set are exactly where naive descent + naive collision
  response fail; the lab surfaces those failure modes deliberately.

### 5.2 As a practical tool (modest utility)

- Dedicated shrink-wrap and remeshing tools (e.g. instant-meshes-style
  pipelines, offset-surface generators) will produce cleaner results faster
  for real geometry.
- The hand-rolled JS CCD will not scale to high-poly meshes; the design
  explicitly leans on small trust radii and per-iteration hashing to stay
  tractable.
- Re-triangulation is deliberately conservative and rate-limited, so deep
  concavities / thin spikes on `K` may stall — acknowledged as an open
  question in `idea.md §8`.

### 5.3 Educational utility (high)

- The lab is an excellent vehicle for teaching: (a) the difference between
  penalty and projection constraint handling, (b) why stateful optimizers
  need resets across discontinuities, (c) how CCD prevents tunneling, and
  (d) degenerate-optimum / fingerprinting intuition with a tangible 3D
  artifact.

## 6. Risks to the Novelty/Utility Claims

- **Tolerance coupling could dominate results.** If the converged geometry is
  largely determined by the `ε`/`δ` ordering (`ε_resid < δ_safe < ε_shell ≪
r_min`) rather than by optimizer identity, the "fingerprint" is an artifact
  of tolerance choices, not a property of the optimizer. The cross-coupling
  analysis in outline §9 is therefore load-bearing for the central claim.
- **Energy injection via resolution.** The claim that symmetric,
  single-line-search truncation "cannot inject energy" (outline §4.3) is
  asserted, not proven. If false, apparent optimizer differences may be
  resolution-induced oscillation instead.
- **Degeneracy handling in CCD** (grazing `n·Δp = 0`, parallel edges,
  already-inside-shell starts) can silently bias which contacts fire first,
  again confounding the fingerprint signal.

## 7. Recommendation

Position the lab as a **controlled study of constrained-descent dynamics and
optimizer fingerprinting on a topological decision variable**, not as a
geometry-processing product. To make the novelty defensible, prioritize:

1. Pin the tolerance table and _demonstrate_ that fingerprints persist across
   a range of `ε`/`δ` settings (rules out tolerance-artifact objection).
2. Provide the energy-non-injection argument for the resolution step
   (empirical bound at minimum).
3. Cite the CCD / Laplacian / trust-region literature explicitly so the
   genuinely new system-level contribution stands out from reused parts.
