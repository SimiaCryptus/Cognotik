# C. Higher-Dimensional Polytopes — Detailed Reference

This document expands the **4D and higher** polytope vocabulary, along with related
non-Euclidean tilings (spherical and hyperbolic) that occasionally serve as comparison
spaces for the project's flat-space constructions.

---

## The Regular 4-Polytopes

There are exactly **six convex regular 4-polytopes** (4D analogues of Platonic solids):

| Polytope                 | Cells | Cell type    | Symmetry order |
| ------------------------ | ----- | ------------ | -------------- |
| 5-cell (simplex)         | 5     | tetrahedron  | 120            |
| 8-cell (tesseract)       | 8     | cube         | 384            |
| 16-cell (cross-polytope) | 16    | tetrahedron  | 384            |
| 24-cell                  | 24    | octahedron   | 1152           |
| 120-cell                 | 120   | dodecahedron | 14400          |
| 600-cell                 | 600   | tetrahedron  | 14400          |

### 5-cell

The **4D simplex**: the simplest 4-polytope, with 5 tetrahedral cells, 10 triangular
faces, 10 edges, and 5 vertices. Self-dual. Analogue of the tetrahedron.

### 8-cell (Tesseract / Hypercube)

The **4D analogue of the cube**: 8 cubic cells, 24 square faces, 32 edges, 16 vertices.
Often referred to as the **tesseract** in popular literature. Forms the unit cell of
$\mathbb{Z}^4$.

### 16-cell

The **4D analogue of the octahedron**: 16 tetrahedral cells, 32 faces, 24 edges, 8
vertices. Dual to the 8-cell.

### 24-cell

A **uniquely 4D polytope** with no Platonic analogue. 24 octahedral cells, 96 triangular
faces, 96 edges, 24 vertices. **Self-dual** and tiles $\mathbb{R}^4$.

### 120-cell

The **4D analogue of the dodecahedron**: **120 dodecahedral cells**, 720 pentagonal
faces, 1200 edges, 600 vertices. **Tiles the 3-sphere $S^3$**. Its symmetry group has
order $14400$ and is related to the **binary icosahedral group $2I$**.

### 600-cell

The **4D analogue of the icosahedron**: **600 tetrahedral cells**, 1200 triangular faces,
720 edges, 120 vertices. **Dual to the 120-cell**. Its vertices form the 120 **icosian
quaternions** of $2I$.

---

## Spherical Tilings

### Spherical Tiling

A **tiling of a sphere $S^n$**. Examples:

- The dodecahedron tiles $S^2$ (its 12 pentagonal faces are spherical pentagons after
  central projection).
- The 120-cell tiles $S^3$.
- Each Platonic solid tiles $S^2$ when projected.

Spherical tilings have **positive curvature**: angles sum to **more** than the flat
value. This "buys" the missing angular deficit, allowing fewer tiles to fit around a
vertex than would in flat space.

For the dodecahedron viewed as a tiling of $S^2$, exactly **3 pentagons** meet at each
vertex, and the **angular surplus** is precisely the curvature integral over each face
(Gauss–Bonnet).

---

## Hyperbolic Tilings

### Hyperbolic Tiling

A **tiling of the hyperbolic plane** $\mathbb{H}^2$ (or higher-dimensional hyperbolic
space). Supports **otherwise-forbidden configurations**:

- **{5, 4}** tiling: 4 pentagons at each vertex (impossible in flat space; possible
  hyperbolically because angles can be smaller than Euclidean).
- **{5, 5}**, **{5, 6}**, etc.: arbitrarily many pentagons at each vertex.
- **{7, 3}**, **{8, 3}**, etc.: regular tilings by polygons that cannot tile $\mathbb{R}^2$.

Hyperbolic tilings have **negative curvature**: angular deficit becomes angular
**surplus** (more than $2\pi$ would fit), which the curvature absorbs.

### Schläfli Symbol $\{p, q\}$

Indicates a tiling by regular $p$-gons with $q$ of them meeting at each vertex.

- $\{p, q\}$ is **flat (Euclidean)** iff $(p-2)(q-2) = 4$. Solutions: $\{3,6\}, \{4,4\}, \{6,3\}$.
- $\{p, q\}$ is **spherical** iff $(p-2)(q-2) < 4$. (The five Platonic solids.)
- $\{p, q\}$ is **hyperbolic** iff $(p-2)(q-2) > 4$. (Infinitely many examples.)

The pentagon, with $\{5, q\}$, gives:

- $\{5, 3\}$: dodecahedron (spherical). Angular sum $= 324° < 360°$.
- $\{5, 4\}$: hyperbolic. Angular sum $= 432° > 360°$.
- No flat $\{5, q\}$ exists.

This is exactly why a flat tiling by regular pentagons requires either a quasi-periodic
construction (Penrose) or a multi-sheeted cover.

---

## High-Dimensional Lattices

### Hypercubic Lattice $\mathbb{Z}^n$

The **integer lattice in $\mathbb{R}^n$**: points with all integer coordinates. Voronoi
cell is the unit hypercube; symmetry group includes the **hyperoctahedral group** of
order $2^n n!$.

Importance in the project:

- $\mathbb{Z}^4$: target of 4D quasicrystal projections.
- $\mathbb{Z}^5$: cut-and-project source of Penrose tiling.
- $\mathbb{Z}^6$: cut-and-project source of AKN tiling (icosahedral quasicrystal).

---

## The Binary Icosahedral Group $2I$

### Definition

The **double cover of the icosahedral group $I$** in $SU(2)$:

$$2I = \text{Spin}(3) \text{-lift of } I \subset SO(3)$$

Order $|2I| = 2 \times 60 = 120$.

### Realization as Icosians

The 120 elements of $2I$ can be written as **unit quaternions with coefficients in
$\mathbb{Q}(\sqrt{5})$** — the so-called **icosians**.

Explicitly, they include:

- 8 elements $\{\pm 1, \pm i, \pm j, \pm k\}$.
- 16 elements $\frac{1}{2}(\pm 1 \pm i \pm j \pm k)$.
- 96 elements obtained from $\frac{1}{2}(0, \pm 1, \pm \phi^{-1}, \pm \phi)$ by even
  cyclic permutations and sign changes.

### Role in the Project

- **Vertices of the 600-cell** are the 120 icosians.
- $2I$ acts on the 120-cell and 600-cell.
- Provides the **algebraic algebra** for 3D pentagonal/icosahedral tilings.
- Encodes **spinor doubling**: the holonomy of certain loops in pentagon-related
  multi-sheeted covers lifts to $2I$ via $SU(2) \to SO(3)$.

---

## Connections Between Dimensions

### Dimensional Hierarchy

The pentagon's angular obstruction (36° deficit) propagates upward:

- **2D**: Pentagon → multi-sheeted plane (fiber: $\mathbb{Z}/10$).
- **3D**: Dodecahedron → multi-sheeted $\mathbb{R}^3$ (fiber: subgroup of $I$, possibly $2I$).
- **4D**: 120-cell tiles $S^3$ exactly (no deficit because $S^3$ supplies curvature).

Going to **higher dimension or to positive curvature** can absorb the angular deficit,
removing the need for sheets. Going to **flat lower-dimensional analogues** requires
the multi-sheeted construction.

### Spherical vs Hyperbolic vs Multi-Sheeted

Three resolutions of pentagonal frustration:

1. **Spherical**: dodecahedron on $S^2$, 120-cell on $S^3$ — positive curvature absorbs
   deficit.
2. **Hyperbolic**: $\{5, q\}$ for $q \geq 4$ — negative curvature absorbs surplus.
3. **Multi-sheeted flat**: pentagon expansion family on multi-sheeted $\mathbb{R}^2$ —
   covering space absorbs deficit as **monodromy**.

The project focuses on the third route, but the other two provide essential context and
comparison.

---

## Notational Conventions

- **$n$-cell**: a polytope with $n$ cells (in 4D, cells are the 3-dimensional faces).
- **$\{p, q\}$**: Schläfli symbol for a regular 3-polytope or 2D tiling.
- **$\{p, q, r\}$**: Schläfli symbol for a regular 4-polytope (cells $\{p, q\}$, with $r$
  meeting at each edge).

Examples:

- Dodecahedron: $\{5, 3\}$.
- 120-cell: $\{5, 3, 3\}$.
- 600-cell: $\{3, 3, 5\}$.
- Cubic honeycomb: $\{4, 3, 4\}$.
