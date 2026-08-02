# E. Topology and Bundle Theory — Detailed Reference

This document expands the topological and bundle-theoretic vocabulary used in the
multi-sheeted n-gon tiling project. Topology provides the language for sheets, covers,
holonomies, and gauge structures — the geometric infrastructure underlying reconnection.

---

## Spaces and Manifolds

### Manifold

A **locally Euclidean topological space** — every point has a neighborhood homeomorphic
to $\mathbb{R}^n$ for some fixed $n$ (the manifold's dimension). Examples:

- $\mathbb{R}^n$, $S^n$.
- Surfaces (2-manifolds): sphere, torus, hyperbolic plane.
- 3-manifolds: $\mathbb{R}^3$, $S^3$, Poincaré dodecahedral space.

### CW Complex

A topological space built **inductively from cells**: 0-cells (points), 1-cells (edges),
2-cells (disks), etc., attached via specified maps. Every tiling defines a CW complex
structure on its ambient space.

### Quotient Space

The space obtained by **identifying equivalent points** under an equivalence relation.
Examples:

- $\mathbb{R}^n / \mathbb{Z}^n$ = torus $T^n$.
- $S^2 / \{\pm 1\}$ = projective plane $\mathbb{RP}^2$.
- Pentagon expansion family quotiented by translations = a quasiperiodic structure.

### Punctured Plane

The space $\mathbb{R}^2 \setminus \Sigma$ for a **vortex set $\Sigma$** (a discrete set of
points). Base space of the pentagon's multi-sheeted bundle: the punctures are the
vertices around which sheet transitions accumulate.

The fundamental group of the punctured plane is the **free group** on $|\Sigma|$
generators (one loop around each puncture).

### Simply Connected

Having **trivial fundamental group**: every loop is contractible. The plane $\mathbb{R}^2$
is simply connected; the punctured plane is **not**.

Multi-sheeted covers exist precisely because the underlying base is **not** simply
connected.

---

## Fundamental Group and Loops

### Fundamental Group $\pi_1$

The **group of homotopy classes of loops** based at a fixed point. Operation: loop
concatenation.

For our spaces:

- $\pi_1(\mathbb{R}^n) = 1$ (trivial).
- $\pi_1(S^1) = \mathbb{Z}$ (winding number).
- $\pi_1(T^2) = \mathbb{Z}^2$ (two independent loops).
- $\pi_1(\mathbb{R}^2 \setminus \{p\}) = \mathbb{Z}$ (encircling the puncture).
- $\pi_1(\mathbb{R}^2 \setminus \{p_1, \ldots, p_k\}) = F_k$ (free group of rank $k$).
- $\pi_1(SO(3)) = \mathbb{Z}/2$ (spinor doubling).

### Loops and Their Homotopy Classes

Two loops are **homotopic** if one can be continuously deformed into the other. The
homotopy class of a loop captures its topological "winding behavior" — useful for
detecting topological defects (vortices).

### Topological Defect

A **singularity that cannot be removed by local deformation**. Vortices are 2D
topological defects: shrinking a loop around a vortex never makes it contractible while
keeping it inside the punctured plane.

---

## Covering Spaces

### Covering Space

A continuous map $\pi: \mathcal{M} \to X$ that is a **local homeomorphism**, with $X$
covered by open sets $U$ where $\pi^{-1}(U)$ is a disjoint union of copies of $U$ each
mapped homeomorphically to $U$. Each copy is a **sheet**.

Properties:

- Number of sheets is constant on connected components of $X$ (assuming $X$ is locally
  connected).
- Covering spaces correspond bijectively to subgroups of $\pi_1(X)$.
- The **universal cover** is the unique simply connected covering space.

### Multi-Sheeted Cover

A covering space with **multiple sheets** — i.e., $|\pi^{-1}(x)| > 1$ for some (hence
all) regular $x \in X$. Allows tile-projection overlaps: two distinct tiles in
$\mathcal{M}$ can project to overlapping regions of $X$.

### Double Cover

A **2-fold covering space**. The canonical examples:

- $S^n \to \mathbb{RP}^n$.
- $SU(2) \to SO(3)$, encoding spinor doubling.
- $\text{Spin}(n) \to SO(n)$.

### Branched Cover

A covering with **ramification points** where the local homeomorphism property fails.
Above a branch point, sheets "collapse" — typically $\pi$ is locally $z \mapsto z^k$ for
some $k > 1$.

In our setting: the multi-sheeted pentagon cover is branched at **vertex points** of the
tiling. Encircling a vertex once shifts the sheet by the deficit angle.

### Branch Point / Ramification Point

A point of the base space above which the covering fails to be a local homeomorphism.
The fiber above a branch point has fewer elements than over generic points.

### Riemann Surface

A **complex 1-manifold**, i.e., a 2-manifold equipped with complex analytic structure.
Classical setting for branched covers: $w = z^{1/n}$ defines an $n$-sheeted Riemann
surface over $\mathbb{C} \setminus \{0\}$.

The pentagon multi-sheeted cover is the **discrete combinatorial analogue** of a Riemann
surface with branch points of order 10.

---

## Cut-and-Project Topology

### Physical Space $E_\parallel$

The **subspace of cut-and-project ambient lattice** containing the actual quasicrystal.
For Penrose tiling, $\dim E_\parallel = 2$ inside $\mathbb{R}^5$.

### Perpendicular Space $E_\perp$

The **orthogonal complement** of $E_\parallel$ in the ambient lattice. Hosts the
acceptance window. For Penrose, $\dim E_\perp = 3$.

### Acceptance Window $W$

The **bounded region in $E_\perp$** filtering which lattice points project to the
physical tiling.

Window shape determines the resulting tiling combinatorics:

- Pentagonal window: Penrose tiling.
- Triacontahedral window: AKN tiling.
- Octagonal window: Ammann–Beenker tiling.

---

## Connections and Holonomy

### Connection

A rule for **parallel transport** in a fiber bundle. In smooth differential geometry: a
connection 1-form valued in the Lie algebra of the structure group. In discrete settings:

### Discrete Connection / Gauge Connection

An **assignment of group elements to edges** of a CW complex. In a lattice gauge theory,
each edge $e$ carries a group element $g_e \in G$. Composition along a path gives
parallel transport. The framework's **sheet transitions** $\tau(e)$ are exactly this kind
of discrete connection.

### Parallel Transport

**Transport of fiber elements along paths via a connection**. In a discrete setting:
walking along a path multiplies the group elements on the edges.

### Holonomy

The **group element accumulated by parallel transport around a closed loop**. For a
trivial connection, holonomy is identity for all loops. Nontrivial holonomy detects
**curvature** (smooth case) or **monodromy** (discrete case).

### Monodromy

The **holonomy of a closed loop** in a covering or bundle. For the pentagon cover, the
monodromy of a small loop around a vortex equals the deficit angle ($36°$) in the
fiber group $\mathbb{Z}/10$.

### Transition Function

The **map between trivializations** of a fiber bundle, valued in the structure group. In
the discrete pinwheel setting, transition functions are **sheet-change maps assigned to
each edge** — the same thing as the discrete connection's edge labels $\tau(e)$.

---

## Principal Bundles

### Principal $G$-Bundle (continued from Section D)

A **fiber bundle with $G$-action** — fibers are copies of $G$, transitions and bundle
operations preserve $G$-structure.

Smooth example: the orthonormal frame bundle of a Riemannian manifold ($G = O(n)$).

### Discrete Principal $G$-Bundle

The **combinatorial analogue**: a CW complex whose 0-cells carry sheet labels in $G$,
with edges assigned $G$-elements (the discrete connection) that determine how sheet
labels transform when crossing.

This is the precise structure of the **multi-sheeted pinwheel cover**.

---

## Local Properties of Tilings

### Local Finiteness

The **finiteness condition**: every compact region contains finitely many tiles. A
fundamental requirement for any reasonable tiling.

Failed when irrational rotation orbits densify the plane (cyclotomic density trap).
Restored by either:

- Restricting to rational rotations (orientation group finite).
- Using cut-and-project (acceptance window bounded).

---

## Euler Characteristic

### Euler Characteristic $\chi$

The **alternating sum of cell counts** in a CW complex:

$$\chi = c_0 - c_1 + c_2 - c_3 + \cdots$$

For a closed surface: $\chi = v - e + f$.

Key values:

- Sphere $S^2$: $\chi = 2$.
- Torus $T^2$: $\chi = 0$.
- Genus-$g$ surface: $\chi = 2 - 2g$.

**Gauss–Bonnet theorem** relates $\chi$ to total Gaussian curvature, linking topology to
geometry. Pentagonal frustration's $36°$ deficit per vertex shows up via Gauss–Bonnet
when interpreting the multi-sheeted cover as a polyhedral surface.

---

## Glossary of Closely Related Terms

| Term               | Smooth setting                   | Discrete setting            |
| ------------------ | -------------------------------- | --------------------------- |
| Manifold           | Smooth manifold                  | CW complex                  |
| Connection         | Connection 1-form                | Edge labels in $G$          |
| Parallel transport | ODE solution                     | Path-product of edge labels |
| Curvature          | $d\omega + \omega \wedge \omega$ | Holonomy of small loop      |
| Holonomy           | Path-ordered exponential         | Product around loop         |
| Bundle             | Smooth fiber bundle              | Discrete principal bundle   |
| Cover              | Topological cover                | Lattice gauge configuration |

The pentagon multi-sheeted cover is the **discrete combinatorial analogue** of a smooth
principal $\mathbb{Z}/10$-bundle over the punctured plane, with **flat connection** away
from vortices and **concentrated curvature** ($36°$ per vortex) at the vertex set.

---

## Why Topology Matters for Reconnection

Topology enters the framework in several essential ways:

1. **Existence of multi-sheeted covers**: requires the base to be non-simply-connected
   (have punctures or vortices).

2. **Holonomy classifies sheet transitions**: the holonomy representation
   $\pi_1(\mathbb{R}^2 \setminus \Sigma) \to G$ determines the cover up to isomorphism.

3. **Local finiteness and discrete structure**: topological compactness conditions
   ensure the tiling makes geometric sense.

4. **Gauss–Bonnet bookkeeping**: total deficit = $2\pi \chi$, providing a topological
   accounting of all the angular shortfalls in the construction.

5. **Gauge-theoretic interpretation**: sheet transitions $\tau(e)$ are gauge fields;
   reconnection is the statement that this gauge field has a consistent global
   structure.

Topology and group theory together provide the structural backbone of the entire
reconnection framework.
