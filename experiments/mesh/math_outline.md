# Mathematical Outline — Constrained Mesh Enclosure Lab

This document collects the concrete formulas, the nontrivial derivations, and
the numerical specifications required to implement the lab described in
`idea.md`. It is a _specification of problems_, not a set of solutions: each
section states the quantities involved, the exact algebraic form where it is
known, and flags the analysis still required (root isolation, degeneracy
handling, gradient availability, tolerance choices).

Notation conventions used throughout:

- `P ∈ ℝ^{V×3}` — vertex positions; row `pᵢ = P[i,:]`.
- `F` — face list; a face `f = (a, b, c)` indexes three vertices.
- `M = (P, F)` — the moving outer mesh; `K` — the static constraint mesh.
- `p⁰ᵢ` — base position of vertex `i` at the start of an iteration.
- `Δpᵢ` — proposed displacement; `pᵢ(t) = p⁰ᵢ + t·Δpᵢ`, `t ∈ [0,1]`.
- `n_f` — outward unit normal of face `f`; `Aₑ` — area of edge/face element.
- `‖·‖` is the Euclidean 2-norm unless annotated.

---

## 1. Energy Functionals

The total energy is the weighted sum

```
E(P) = λ_area·A + λ_vol·|V − V*| + λ_fit·Φ + λ_len·(−H_len)
   + λ_ang·Θ + λ_smooth·S
```

All soft terms are intended to be differentiable a.e. and evaluated under
reverse-mode autodiff (tfjs). The formulas below fix the exact scalar each
term contributes; the analysis notes flag where the gradient is singular or
discontinuous and therefore needs guarding.

### 1.1 Surface Area `A(P)`

For each triangular face `f = (a,b,c)`:

```
A_f = ½ ‖(p_b − p_a) × (p_c − p_a)‖
A(P) = Σ_f A_f
```

- **Gradient (analytic reference).** With `u = p_b − p_a`, `v = p_c − p_a`,
  `w = u × v`, `A_f = ½‖w‖`. The vertex gradients are

```
∂A_f/∂p_a = (1/(2‖w‖)) · w × (v − u)   [sign/ordering must be verified]
```

Autodiff removes the need to hand-code this, but the reference is needed for
unit tests.

- **Analysis required.** `A_f` gradient is singular as `‖w‖ → 0` (degenerate
  sliver). Specify an `ε_area` floor or reformulate as `½√(‖w‖² + ε²)` to keep
  the gradient bounded near collapse.

### 1.2 Signed Volume `V(P)` (Divergence Theorem)

For a closed, consistently-oriented mesh:

```
V(P) = (1/6) Σ_f  p_a · (p_b × p_c)
```

- The volume-matching term is `λ_vol·|V − V*|`, whose subgradient is
  `λ_vol·sign(V − V*)·∇V`. `∇V` is exact and cheap:

```
∂V/∂p_a = (1/6)(p_b × p_c),  and cyclically for p_b, p_c.
```

- **Analysis required.** The absolute value is non-smooth at `V = V*`.
  Specify either a smooth surrogate `√((V−V*)² + ε²)` or a subgradient
  convention. Also: correctness of `V` depends on **global orientation
  consistency** of `F` — this must be established once at load time and
  preserved by any re-triangulation (§8).

### 1.3 Vertex Fidelity `Φ(P)`

```
Φ = Σᵢ wᵢ ‖pᵢ − tᵢ‖²,     ∇_{pᵢ}Φ = 2 wᵢ (pᵢ − tᵢ)
```

Trivially smooth. Specify the target set `{tᵢ}` and per-vertex weights `wᵢ`
(0 for free vertices, large for pinned landmarks).

### 1.4 Edge-Length Entropy `H_len`

This is the nontrivial statistical term, mirrored on the Geometric Entropy
Lab. Let the edge set be `{e}` with lengths `ℓ_e = ‖p_{e0} − p_{e1}‖`.

1. **Kernel density over lengths.** Choose a bandwidth `h` (fixed, or
   Silverman rule from the current length sample). Define a soft histogram /
   KDE over a grid of `B` bins with centers `{c_k}`:

```
ρ_k ∝ Σ_e exp( −(ℓ_e − c_k)² / (2h²) )
```

Normalize to a probability vector `p_k = ρ_k / Σ_j ρ_j`.

2. **Shannon entropy.**

```
H_len = − Σ_k p_k log p_k
```

3. **Saturation bound.** `H_len ≤ log B` (grid) and conceptually
   `≤ ln(#edges)`; the degenerate optimum is `p_k` uniform. This is the
   fingerprinting regime.

- **Analysis required.**
- Choice of KDE vs. pairwise soft-gram formulation (the entropy lab uses a
  distance gram; here it is a 1-D length distribution — decide and pin down).
- Bandwidth `h` policy: fixed for a stable landscape vs. adaptive for
  resolution. Adaptive `h` makes `H_len` depend on the sample distribution
  in a second-order way — its gradient contribution must be included or
  deliberately stopped (stop-gradient).
- `log p_k` singular as `p_k → 0`; add floor `p_k ← p_k + ε` before log or
  rely on the KDE keeping mass strictly positive.
- `ℓ_e` gradient singular at zero-length edges (see §1.7 shared clause).

### 1.5 Angular Functional `Θ(P)`

Per triangle corner angle `θ` (angle at vertex `a` in face `(a,b,c)`):

```
cos θ_a = (u · v) / (‖u‖‖v‖),   u = p_b − p_a, v = p_c − p_a
```

Two candidate functionals from the menu:

```
Θ_target = Σ_corners (θ − π/3)²          # equilateral target
Θ_sliver = Σ_corners ( −log sin θ )      # anti-sliver barrier
```

- **Analysis required.**
- `θ = arccos(·)` has derivative `−1/√(1−cos²θ)` singular at `θ ∈ {0, π}`;
  the anti-sliver form `−log sin θ` diverges there by design. Specify clamp
  `sin θ ≥ ε_sin` for numerical safety.
- Decide whether to sum over all three corners per face or use a
  per-face aggregate (e.g. deviation of the angle triple).

### 1.6 Laplacian Smoothness `S(P)`

Uniform (umbrella) Laplacian:

```
L(pᵢ) = pᵢ − (1/|N(i)|) Σ_{j∈N(i)} pⱼ
S = Σᵢ ‖L(pᵢ)‖²
```

- **Analysis / options.** Consider cotangent weights for a
  geometry-aware Laplacian:

```
L_cot(pᵢ) = Σ_{j∈N(i)} (cot α_{ij} + cot β_{ij})(pⱼ − pᵢ)
```

Cotangent weights introduce their own singularities at degenerate triangles
and require the same `ε` guarding as §1.5. Specify which Laplacian is used;
the uniform version is safe and differentiable, the cotangent version is
more faithful to curvature.

### 1.7 Shared Clause — Norm/Length Gradient Guards

Every term built on `‖·‖` (area, edge length, angle) has a `1/‖·‖`
singularity as the vector collapses. Specify one global policy, e.g. replace
`‖x‖` with `√(‖x‖² + ε²)` (softening) or hard-floor with a small `ε_len`.
This policy must be consistent between the energy value and its autodiff
gradient (tfjs handles the latter automatically once the softened form is
used).

---

## 2. Discrete Curvature Quantities (Monitoring & Angular Terms)

These are diagnostic (and feed §1.5) rather than directly optimized, but their
formulas must be pinned down for the "vertices as curvature" reporting.

- **Angle deficit (Gaussian curvature proxy)** at interior vertex `i`:

```
κ_i = 2π − Σ_{f ∋ i} θ_i^f
```

- **Dihedral angle** across edge `e` shared by faces `f, g`:

```
φ_e = atan2( (n_f × n_g)·ê , n_f · n_g )
```

where `ê` is the unit edge direction. Sign convention (convex vs. concave)
must be specified relative to outward normals.

These give the early-warning signal for re-triangulation (large `|κ_i|`,
near-flat or folded `φ_e`).

---

## 3. Continuous Collision Detection (CCD)

All detection is on the **swept linear path** `p(t) = p⁰ + t·Δp`, `t∈[0,1]`.
Two primitive tests dominate: point–triangle and edge–edge. Both reduce to
finding the earliest `t ∈ [0,1]` at which a geometric predicate is satisfied.

### 3.1 Broad Phase — Swept AABBs

For a moving vertex, the swept AABB is

```
box = AABB( p⁰ ) ∪ AABB( p⁰ + Δp ),   inflated by (δ_safe + ε_shell).
```

For an edge or face, take the union over its moving endpoints. Because
`‖Δpᵢ‖ ≤ rᵢ` (trust radius), the inflation and hence the spatial-hash cell
size can be bounded a priori per iteration:

```
cell ≈ f( max_i rᵢ , δ_safe , ε_shell ),   f a small constant multiple.
```

- **Specification needed.** Choice of spatial hash vs. BVH; whether `K`'s BVH
  is prebuilt once (K static) while `M`'s structure is rebuilt or refit each
  iteration.

### 3.2 Point–Plane / Point–Triangle Narrow Phase

Let a moving vertex `p(t)` be tested against a triangle with (for the static
case `K`) fixed vertices `q₀,q₁,q₂`, plane point `q = q₀`, and normal `n`.

1. **Signed distance along the path:**

```
d(t) = n · ( p(t) − q ) = d(0) + t·( n·Δp )
```

This is **affine in `t`** when the triangle is static (constraint `K`).

2. **Half-space crossing:** a collision candidate exists iff `d(0) > 0` and
   `d(1) ≤ 0` (entering the solid, `d` decreasing). The **time of impact**:

```
t* = d(0) / ( d(0) − d(1) )
```

3. **Barycentric containment at `t*`.** Project `p(t*)` into the triangle
   plane and solve for barycentric coords `(u,v,w)`:

```
p(t*) − q₀ = u(q₁−q₀) + v(q₂−q₀),  w = 1−u−v
collision confirmed iff  u,v,w ∈ [−ε_bary, 1+ε_bary].
```

- **Self-collision case (moving triangle).** When the triangle's own vertices
  move, `n`, `q`, and the barycentric basis all depend on `t`. Then `d(t)` is
  **cubic in `t`** (the classic coplanarity condition):

```
(x_{p} − x_{0}) · [ (x_{1} − x_{0}) × (x_{2} − x_{0}) ] = 0   at time t
```

where each `x_k(t)` is affine, so the triple product is a **degree-3
polynomial** in `t`. Root isolation on `[0,1]` is required, followed by the
barycentric containment test at each root.

- **Analysis required.**
- Cubic root finding on `[0,1]`: specify method (closed-form Cardano vs.
  numeric bracket+bisection with the affine special-case fast path).
- Coplanarity vs. actual overlap: a coplanar root is necessary but not
  sufficient — must confirm containment.
- Degeneracies: `n·Δp = 0` (grazing / parallel motion), `d(0)=d(1)`
  (no crossing), starting already inside the `ε` shell.

### 3.3 Edge–Edge Narrow Phase

Two segments with moving endpoints:

```
A(t) = A⁰ + tΔA,  B(t) = B⁰ + tΔB   (edge 1 endpoints)
C(t) = C⁰ + tΔC,  D(t) = D⁰ + tΔD   (edge 2 endpoints)
```

1. **Minimum-distance parameterization.** For fixed `t`, the closed segment–
   segment squared distance is a function of two clamp parameters `(s, r)`:

```
P₁(s) = A(t) + s(B(t)−A(t)),  s∈[0,1]
P₂(r) = C(t) + r(D(t)−C(t)),  r∈[0,1]
dist²(t) = min_{s,r} ‖P₁(s) − P₂(r)‖²
```

The unconstrained interior minimizer solves the standard 2×2 linear system
(Lumelsky / Ericson); clamping handles endpoint cases.

2. **Coplanarity / crossing polynomial.** The signed volume

```
ψ(t) = ( B(t)−A(t) ) · [ ( C(t)−A(t) ) × ( D(t)−A(t) ) ]
```

is again a **degree-3 polynomial** in `t`; `ψ(t*) = 0` is the coplanarity
condition (necessary for a crossing). Confirm an actual crossing by
checking that the closest-point parameters `s,r ∈ [0,1]` at `t*`.

3. **Epsilon-shell TOI.** Rather than exact contact, flag the earliest
   `t ∈ [0,1]` with `dist²(t) = ε_shell²` and `d/dt dist²(t) < 0`
   (distance decreasing). This is the safety-margin root that §4.2 truncates
   against.

- **Analysis required.**
- `dist²(t)` is piecewise-polynomial (the active clamp region of `(s,r)`
  changes with `t`); specify whether we (a) root-find on the cubic
  coplanarity `ψ` and validate, or (b) sample/bracket `dist²(t)` directly.
- Adjacency exclusion: edges sharing a vertex are exempt (C2/C3 say
  _non-adjacent_); define the adjacency mask precisely (shared vertex,
  shared face, or 1-ring).
- Parallel-edge degeneracy (system matrix singular) → fall back to
  endpoint-to-segment distances.

### 3.4 Global TOI Aggregation

For a given moving vertex or edge, the effective TOI is the minimum over all
confirmed narrow-phase events:

```
t*_element = min over confirmed (point-tri, edge-edge) events, in [0,1].
```

Resolution (§4) proceeds in **global earliest-TOI order**.

---

## 4. Collision Resolution

### 4.1 Point–Plane Projection with Safety Offset (vs. static K)

On a confirmed point–face collision, project the vertex back to the constraint
surface plus outward offset:

```
p ← p − ( n·(p − q) − δ_safe ) · n
```

- Removes only the normal component of penetration; tangential displacement
  survives (sliding contact).
- **Analysis required.** After projection the vertex sits on the `δ_safe`
  offset surface, but the _offset surface_ near an edge/corner of `K` is not
  the plane of a single face — specify corner handling (project to nearest
  feature: face/edge/vertex of the offset surface) to avoid oscillation
  between adjacent faces' planes.

### 4.2 Edge–Edge Delta Scaling

Uniformly retract the step of all four involved vertices to a safe fraction of
the TOI:

```
Δp_k ← ( η · t* ) · Δp_k,   k ∈ {edge1, edge2 endpoints},  η ≈ 0.9
```

- Preserves step direction, halts just short of contact.
- **Analysis required.** When a vertex participates in multiple edge–edge
  events in one round, it receives multiple scale factors — specify the
  combination rule (take the **minimum** `η·t*` across all its events).

### 4.3 Self-Collision Symmetry

- **Point–face self-collision:** move the point (projection of §4.1 with the
  _moving_ face's plane evaluated at TOI), never the face, to avoid ownership
  ambiguity. The face's vertices are corrected symmetrically when they later
  act as moving points.
- **Edge–edge self-collision:** §4.2 applied to both edges' four endpoints at
  the shared TOI — symmetric, momentum-free.
- **Analysis required.** Prove (or empirically bound) that symmetric,
  single-line-search truncation cannot inject energy — i.e. resolution is a
  contraction toward the feasible set, not a source of oscillation.

### 4.4 Iterated Resolution Rounds

```
for round in 1..R (R ≈ 3–4):
  recompute narrow-phase against updated targets
  resolve earliest-TOI event
if unresolved conflicts remain: reject whole step, shrink trust radii
```

- **Analysis required.** Termination / non-cycling guarantee for the round
  loop, and the precise "unresolved" predicate (residual penetration depth
  `> ε_resid`, or `> 0` new events after `R` rounds).

---

## 5. Trust Radius Dynamics

Per-vertex radius `rᵢ` clamps the displacement:

```
Δpᵢ ← Δpᵢ · min( 1, rᵢ / ‖Δpᵢ‖ )
```

**Ratio test.** With model decrease `m = E(P) − q(P+Δ)` (quadratic model `q`)
and actual decrease `a = E(P) − E(P+Δ)`, the ratio `ρ = a/m` drives:

```
ρ high  (& step accepted, no truncation):  rᵢ ← min( 2 rᵢ , r_max )
step truncated / projected / rejected:     rᵢ ← rᵢ / 2  (≥ r_min)
```

- **Analysis / specification.**
- Definition of the local quadratic model `q` — is it the optimizer's
  implicit model (L-BFGS/QQN) or a diagonal proxy? This determines `m`.
- Whether `ρ` is computed globally (scalar energy) but applied per-vertex,
  and how a global `ρ` maps to per-vertex updates (e.g. only shrink the
  vertices that were truncated).
- Thresholds for "high"/"low" `ρ` (typical `0.75` / `0.25`).
- `r_min`, `r_max`, and coupling of `r_max` to broad-phase cell size (§3.1).

---

## 6. Optimizer Step Models

The proposed raw step `Δ_raw = optimizer(g)` is then globally norm-clipped,
per-vertex trust-clamped, and CCD-filtered. The three optimizers:

- **Adam.** First/second moment estimates `m_t, v_t`; step
  `Δ = −α · m̂_t / (√v̂_t + ε)`. Stateless w.r.t. geometry; safe across
  corrections but must have `(m,v)` **reset** for any vertex that was
  truncated/projected (stale moments across a discontinuity).
- **L-BFGS.** Two-loop recursion over `(s_k, y_k)` history pairs. History
  **must be reset** for corrected vertices — `y_k` spanning a projection is a
  corrupted curvature sample.
- **QQN.** Quadratic-path / quasi-Newton hybrid (shared with sibling labs);
  same reset discipline.

- **Analysis required.**
- Global gradient-norm clip threshold and its interaction with the
  per-vertex trust clamp (order of operations is: optimizer → global clip →
  trust clamp → CCD).
- Exact state-reset granularity: per-vertex slices of `(m,v)` / history, and
  how L-BFGS/QQN histories (which mix vertices) are partially invalidated.

---

## 7. Validity Audit (Periodic)

Exact (non-continuous) checks run every `N` iterations:

- **Watertightness / manifoldness:** every edge shared by exactly 2 faces;
  consistent orientation; Euler characteristic `V − E + F = 2 − 2g` for the
  expected genus `g`.
- **Self-intersection:** exact triangle–triangle intersection test over all
  non-adjacent face pairs (broad-phased). This is the ground-truth check that
  the incremental CCD has not drifted.
- **Min clearance:** `min over vertices of signed distance to K ≥ δ_safe`.

- **Specification needed.** Triangle–triangle intersection predicate
  (e.g. Möller) and the exact-arithmetic vs. `ε`-tolerant policy for the
  audit (audit should be stricter than the running CCD `ε_shell`).

---

## 8. Re-Triangulation Invariants

When connectivity edits (flip / split / collapse) are permitted:

- **Locality constraint:** the edited 1-ring must lie strictly outside all
  constraint `ε` shells (edit ≠ boundary-creating operation).
- **Orientation preservation:** every edit must preserve global orientation
  consistency (needed for `V(P)` in §1.2).
- **Post-edit CCD audit** on the patch before acceptance; **state reset** for
  touched vertices (§5, §6).

- **Analysis required.** Formal conditions under which an edge flip is
  _guaranteed_ not to create a self-intersection given the local geometry;
  quality predicates (min-angle improvement, valence balancing) that trigger
  each edit type; rate-limiting policy.

---

## 9. Global Constants / Tolerances to Pin Down

A single table of every `ε`/`δ` referenced above, to be fixed in one place:

| Symbol      | Meaning                                         | Coupled to           |
| ----------- | ----------------------------------------------- | -------------------- |
| `δ_safe`    | outward offset kept from `K`                    | §4.1, min clearance  |
| `ε_shell`   | edge–edge safety-margin distance                | §3.3, §4.2           |
| `ε_bary`    | barycentric containment slack                   | §3.2                 |
| `ε_len`     | length/area/norm gradient floor                 | §1.1, §1.4, §1.7     |
| `ε_sin`     | anti-sliver angle clamp                         | §1.5                 |
| `ε_resid`   | residual penetration tolerance after resolution | §4.4                 |
| `η`         | edge–edge retraction fraction (≈0.9)            | §4.2                 |
| `r_min/max` | trust radius bounds                             | §5, broad-phase cell |
| `h`         | edge-length KDE bandwidth                       | §1.4                 |
| `B`         | KDE bin count                                   | §1.4                 |
| `R`         | resolution rounds per iteration                 | §4.4                 |
| `N`         | audit / re-triangulation period                 | §7, §8               |

**Cross-coupling analysis required:** several tolerances interact
(`δ_safe < ε_shell`? `r_max` vs. cell size vs. `δ_safe`), and a consistency
ordering (e.g. `ε_resid < δ_safe < ε_shell ≪ r_min`) must be established so
that the running CCD and the exact audit agree.
