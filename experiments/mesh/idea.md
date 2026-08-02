# Constrained Mesh Enclosure Lab

> **Optimize a closed surface mesh around a constraint volume.**
> A deformable triangulated mesh is driven by gradient descent to extremize
> geometric fitness functionals (area, volume, entropy of edge lengths, angular
> regularity) while a hard non-penetration constraint guarantees the mesh never
> intrudes into an inner constraint mesh — and never intersects itself.

## The Problem in One Paragraph

Given a fixed inner "constraint" mesh (a keep-out volume), find the closed,
manifold, self-intersection-free outer mesh that encloses it while minimizing a
chosen energy functional. This is the mesh analogue of a shrink-wrap: the outer
surface flows downhill on its energy landscape, but every step is filtered
through a continuous collision detection (CCD) pipeline that clips motion at
the exact moment any vertex, edge, or face would violate the constraint
geometry or the mesh's own manifold integrity. The result is a family of
extremal enclosures — minimal-area wraps, maximal-entropy tessellations,
curvature-balanced shells — all provably intrusion-free.

## Relationship to the Sibling Labs

This lab shares its DNA with the other experiments in this collection:

- Like the **Geometric Entropy Lab**, fitness can include the Shannon entropy
  of a distribution derived from the geometry (here, the edge-length
  distribution rather than pairwise point distances). The same
  degenerate-optimum / optimizer-fingerprinting phenomena apply: many distinct
  meshes achieve the same entropy, and the optimizer's dynamics select among
  them.
- Like the **No-Three-in-Line Lab**, hard combinatorial/geometric constraints
  are handled inside a continuous relaxation. Where that lab uses divergent
  angle penalties, this lab uses _exact_ constraint enforcement via projection
  and step truncation — a hard wall rather than a soft barrier.

The new ingredient here is **topology**: the decision variables are not a
loose point cloud but the vertices of a mesh whose connectivity must remain a
valid, non-self-intersecting 2-manifold throughout the optimization.

---

## 1. Formulation

### 1.1 Decision Variables

The optimization state is the vertex position matrix `P ∈ ℝ^{V×3}` of a
triangulated mesh `M = (P, F)` with fixed (or rarely-updated) face list `F`.
The connectivity `F` is treated as static wherever possible; see §6 on
re-triangulation.

### 1.2 Energy Functional

The solver minimizes a weighted global energy

```
E(P) = λ_area   · A(P)                 # total surface area
   + λ_vol    · |V(P) − V*|          # volume matching (signed divergence-theorem volume)
   + λ_fit    · Σᵢ ‖pᵢ − tᵢ‖²        # vertex-positional fidelity to targets
   + λ_len    · (−H_len)             # edge-length entropy (Shannon-gram)
   + λ_ang    · Σ_faces g(θ)          # angular functional (triangle quality)
   + λ_smooth · Σᵢ ‖L(pᵢ)‖²          # Laplacian smoothness regularizer
```

subject to the **hard constraints**

```
C1:  no vertex of M penetrates the constraint mesh K        (keep-out)
C2:  no vertex of M penetrates a non-adjacent face of M     (self-collision)
C3:  no edge of M crosses a non-adjacent edge of M or K     (edge-edge)
```

Constraints C1–C3 are not penalty terms. They are enforced exactly, per
iteration, by the collision pipeline described in §3–§5. This keeps the
feasible set clean and lets the soft energies be tuned freely without
balancing against a barrier stiffness.

### 1.3 Fitness Menu

| Functional             | Definition                                     | Typical use                      |
| ---------------------- | ---------------------------------------------- | -------------------------------- |
| **Surface area**       | `Σ_faces ½‖(b−a)×(c−a)‖`                       | Shrink-wrap / minimal enclosure  |
| **Volume**             | `⅙ Σ_faces a·(b×c)` (divergence theorem)       | Inflate / match a target volume  |
| **Vertex fidelity**    | `Σᵢ wᵢ‖pᵢ − tᵢ‖²`                              | Pin landmarks, preserve shape    |
| **Length entropy**     | `−Σ p_e log p_e` over a KDE of edge lengths    | Uniform vs. diverse tessellation |
| **Angular functional** | e.g. `Σ (θ − 60°)²` or `−log sin θ` per corner | Triangle quality / anti-sliver   |
| **Laplacian smooth**   | `Σᵢ ‖pᵢ − mean(neighbors)‖²`                   | Fair, low-curvature surfaces     |

As in the entropy lab, the length-entropy term saturates at `ln(#edges)` when
all edge lengths contribute equal kernel mass — a highly degenerate optimum
whose realized geometry fingerprints the optimizer that found it.

---

## 2. Trust Radius Dynamics

Each vertex `i` carries a per-iteration **trust radius** `rᵢ`, and the
proposed displacement is clamped so that `‖Δpᵢ‖ ≤ rᵢ`. This serves three
purposes:

1. **Model validity** — the local quadratic approximation of `E` used by the
   quasi-Newton optimizers is only trustworthy in a small neighborhood;
   clamping prevents wild steps in the stiff, highly non-linear regions near
   contact.
2. **CCD tractability** — bounded displacements bound the swept volumes that
   the broad-phase collision query must consider, keeping detection cheap.
3. **Adaptive resolution** — `rᵢ` shrinks after a rejected or truncated step
   (contact-rich regions crawl carefully) and grows after a sequence of clean
   accepted steps (free regions stride).

A standard ratio test governs the update: if actual energy reduction closely
matches the model's prediction, `rᵢ ← min(2rᵢ, r_max)`; if the step failed or
was collision-truncated, `rᵢ ← rᵢ/2`.

---

## 3. Intersection Detection

All detection is **continuous**: the test object is the swept path from the
iteration base position `p⁰` to the proposed target `p⁰ + Δp`, not just the
endpoints. This prevents _tunneling_ — fast elements passing entirely through
a thin constraint feature between discrete steps.

### 3.1 Broad Phase

A spatial hash (or BVH over `K` and over `M`'s faces) returns, for each moving
vertex and edge, the set of constraint primitives whose expanded AABBs overlap
the swept AABB of the element. Trust radii give a hard upper bound on sweep
extent, so the hash cell size can be chosen once per iteration.

### 3.2 Point-to-Plane (Narrow Phase)

For each candidate (vertex, face) pair, compute the signed distance
`d(t) = n·(p(t) − q)` along the linearized path `p(t) = p⁰ + t·Δp`,
`t ∈ [0, 1]`, where `n` is the face normal and `q` a point on its plane. A
crossing of the half-space (`d(t) = 0` with `d` decreasing into the solid)
followed by a barycentric containment test flags a collision and yields the
parametric **time of impact** `t* = d(0) / (d(0) − d(1))`.

> _"Time of impact" is a numerical metaphor: there is no simulation clock,
> only the projection geometry obeyed during the line-search phase of the
> optimization. `t` parameterizes the step, not time._

### 3.3 Edge-to-Edge (Narrow Phase)

For each candidate edge pair, track the minimum distance between the two
segments as both endpoints move along their linear paths. The squared-distance
function is polynomial in `t`; a root of `dist(t) = ε` within `[0, 1]`
(with distance decreasing) flags an imminent crossing and its TOI. The
epsilon shell provides a safety margin against floating-point grazing.

---

## 4. Collision Resolution

### 4.1 Point–Plane: Projection with Safety Offset

On a detected point–face collision against the **static constraint mesh K**,
the vertex is projected back onto the constraint surface along the face
normal, then offset outward by a small `δ_safe`:

```
p ← p − (n·(p − q) − δ_safe) · n
```

This zeroes the velocity component perpendicular to the constraint while
preserving tangential motion — the vertex may slide along the keep-out
surface, allowing the optimizer to continue making progress _around_ the
obstacle rather than stalling against it.

### 4.2 Edge–Edge: Delta Scaling

For an imminent edge–edge crossing, the displacement deltas of all four
involved vertices are uniformly scaled back to a safe fraction of the TOI:

```
Δp ← (η · t*) · Δp,     η ≈ 0.9
```

This preserves the _direction_ of the optimization step while halting progress
exactly at (just short of) the collision boundary. On subsequent iterations
the energy gradient typically rotates the step direction tangentially, and the
edges slide past one another rather than locking.

### 4.3 Self-Collisions: Both Sides Move

Self-contact is more delicate because the "constraint plane" is itself moving:

- **Point–face self-collision** — the projection rule _pushes the point_
  (rather than deforming the face) to avoid ambiguity about which side owns
  the correction. The face's own vertices are handled symmetrically when they
  appear as moving points against other faces, so no element is privileged
  globally.
- **Edge–edge self-collision** — the delta-scaling rule of §4.2 applies
  directly; both edges' endpoint displacements are truncated at the shared
  TOI. Because the truncation is symmetric, momentum-free, and applied within
  a single line search, it cannot inject energy or oscillate.

### 4.4 Resolution Ordering

Within one iteration, collisions are resolved in TOI order (earliest first),
re-running narrow-phase checks against updated targets after each resolution.
A small fixed number of resolution rounds (typically 3–4) suffices; if
conflicts persist, the whole step is rejected and trust radii shrink.

---

## 5. Optimization Loop

```
repeat:
1. g  ← ∇E(P)                        # autodiff gradient of soft energies
2. Δ  ← optimizer step (Adam / QQN / L-BFGS), clipped by global norm
3. Δᵢ ← clamp(Δᵢ, rᵢ)                # per-vertex trust radius
4. broad phase: swept AABBs vs. K and vs. self
5. narrow phase: point-plane + edge-edge CCD → TOIs
6. resolve: projection (4.1/4.3) and delta scaling (4.2), earliest-TOI first
7. accept P ← P + Δ; update trust radii by ratio test
8. (periodic) validity audit: exact self-intersection & watertightness check
```

Stateful optimizers (L-BFGS, QQN) have their history **reset** for any vertex
whose step was truncated or projected — the same lesson as the
no-three-in-line solver's teleports: stale curvature estimates across a
discontinuous correction produce garbage search directions.

---

## 6. Triangulation: Formation, Stability, Re-triangulation

The strong preference is to **fix the initial triangulation and keep it
static**. A stable connectivity:

- keeps the energy landscape continuous between iterations,
- preserves optimizer state validity,
- and makes the collision epsilon shells meaningful across steps.

When re-triangulation is unavoidable (degenerate slivers, extreme anisotropy,
valence pathologies), it must be done **conservatively**:

1. Perform edits (edge flips, splits, collapses) only on elements whose local
   neighborhood is strictly outside all constraint epsilon shells — a
   connectivity edit must never be the operation that creates a boundary
   violation.
2. Re-run a full local CCD audit on the edited patch before accepting.
3. Reset optimizer state and trust radii for all touched vertices.
4. Rate-limit edits (e.g. at most one edit pass per N iterations) so the
   landscape remains quasi-static.

### Vertices as Curvature

Because the connectivity is fixed, the vertex positions alone encode the
discrete curvature of the surface (angle deficit at each vertex, dihedral
angles across each edge). This is important for manifold behavior:

- curvature concentrates where vertices cluster, so the **length-entropy**
  term doubles as a curvature-distribution control;
- the angular functionals directly regularize the discrete second fundamental
  form, suppressing creases and fold-overs before they become collisions;
- monitoring per-vertex angle deficit gives a cheap early-warning signal for
  regions that will need re-triangulation.

---

## 7. Fitness Metrics (Reported Live)

| Metric              | Meaning                                                       |
| ------------------- | ------------------------------------------------------------- |
| **Surface area**    | Total area `A(P)` of the enclosure                            |
| **Volume**          | Enclosed signed volume `V(P)` and gap to target `V*`          |
| **Vertex fidelity** | RMS distance of pinned vertices to their targets              |
| **Length entropy**  | Shannon entropy of the edge-length KDE (vs. `ln #edges`)      |
| **Angular energy**  | Aggregate triangle-quality / anti-sliver functional           |
| **Min clearance**   | Smallest signed distance from `M` to `K` (should be ≥ δ_safe) |
| **Contacts**        | Count of active point-plane and edge-edge constraints         |
| **Step / rejects**  | Iterations taken; steps rejected or collision-truncated       |

---

## 8. Expected Behaviors & Open Questions

- **Shrink-wrap regime** (`λ_area` dominant): the mesh should converge to a
  tight offset surface of `K` at clearance `δ_safe`, with contact patches
  where curvature permits — a discrete analogue of the convex-hull /
  minimal-enclosure surface.
- **Entropy regime** (`λ_len` dominant): the degenerate `ln(#edges)` optimum
  means many tessellations tie; different optimizers should land on visibly
  different meshes at identical entropy — the fingerprinting effect from the
  entropy lab, now with topology in the loop.
- **Sliding contact dynamics**: does tangential projection (§4.1) produce
  smooth "creeping" flow along the constraint, or stick-slip cycling? Trust
  radius adaptation is expected to damp the latter.
- **Re-triangulation necessity**: for which constraint geometries can a fixed
  triangulation survive to convergence, and where does it inevitably
  degenerate (deep concavities, thin spikes on `K`)?
- **Constraint-manifold degeneracy**: the feasible set carved out by C1–C3 is
  itself a complicated manifold-with-boundary; how the optimizer path
  interacts with its corners (simultaneous multi-contact) is the stiff,
  interesting case.

---

## 9. Planned File Structure

```
mesh/
├── index.html               # Single-file app: viewport, controls, loop
├── js/
│   ├── mesh-energy.js        # Soft energy functionals (tfjs autodiff)
│   ├── ccd.js                # Broad + narrow phase continuous collision
│   ├── resolve.js            # Projection & delta-scaling resolution
│   ├── trust.js              # Per-vertex trust radius bookkeeping
│   ├── retriangulate.js      # Conservative connectivity edits
│   ├── optimizer-adam.js     # Shared with sibling labs
│   ├── optimizer-qqn.js
│   └── optimizer-lbfgs.js
└── idea.md                   # This document
```

## Dependencies (planned, CDN)

| Library                | Purpose                                 |
| ---------------------- | --------------------------------------- |
| `@tensorflow/tfjs` 4.x | Autodiff gradients of the soft energies |
| (none for CCD)         | Collision pipeline is hand-rolled JS    |

## License

Part of the experiments collection. See the repository root for license
details.
