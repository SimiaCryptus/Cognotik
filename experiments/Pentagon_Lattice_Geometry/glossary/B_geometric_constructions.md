# B. Geometric Constructions — Detailed Reference

This document expands the geometric vocabulary used throughout the multi-sheeted n-gon
tiling project. It covers polygons, polyhedra, tilings, adjacency structures, and the
specific constructions (pinwheels, Penrose, Kelvin, etc.) that recur in the project.

---

## Polytopes — The Basic Objects

### Polygon

A **two-dimensional polytope**: a closed planar figure bounded by straight line segments.
Polygons are the basic tiles of 2D tilings.

### Polyhedron

A **three-dimensional polytope**: a solid bounded by flat polygonal faces meeting at
straight edges. Polyhedra are the basic 3D tiles.

### Polytope

The **$n$-dimensional generalization** of polygons and polyhedra: a region of
$\mathbb{R}^n$ bounded by flat $(n{-}1)$-dimensional facets.

### Vertex

A **corner point** of a polygon or polyhedron — a 0-dimensional face.

---

## Polygon Specifics

### Pentagon

A **five-sided polygon**. The regular pentagon has:

- Interior angle: $108°$
- Angular deficit at a vertex: $360° - 3 \times 108° = 36°$
- Symmetry group: dihedral $D_5$ of order 10
- Field: $\mathbb{Q}(\sqrt{5})$

The pentagon's failure to tile the plane (deficit $\neq 0$) is precisely what forces a
**multi-sheeted cover** in the reconnection framework.

### Interior Angle

The angle inside a polygon at one of its vertices. For a regular $n$-gon:

$$\theta_n = \frac{(n-2) \pi}{n}$$

### Angular Deficit

The shortfall

$$\delta = 2\pi - \sum_k \theta_k$$

between a full rotation and the sum of interior angles meeting at a vertex of a candidate
tiling. Pentagon: $\delta = 36°$ when 3 pentagons meet. A nonzero deficit prevents flat
periodic tiling; it can be absorbed by curving the surface, adding extra pentagons (5+),
or **transitioning between sheets** in a multi-sheeted cover.

---

## Polyhedron Specifics

### Platonic Solids

The five **convex regular polyhedra**:

| Solid        | Faces        | Field                  |
| ------------ | ------------ | ---------------------- |
| Tetrahedron  | 4 triangles  | $\mathbb{Q}(\sqrt{2})$ |
| Cube         | 6 squares    | $\mathbb{Q}$           |
| Octahedron   | 8 triangles  | $\mathbb{Q}$           |
| Dodecahedron | 12 pentagons | $\mathbb{Q}(\sqrt{5})$ |
| Icosahedron  | 20 triangles | $\mathbb{Q}(\sqrt{5})$ |

### Tetrahedron

4 triangular faces, 4 vertices, 6 edges. Self-dual. Smallest Platonic solid.

### Octahedron

8 triangular faces, 6 vertices, 12 edges. Dual to the cube.

### Dodecahedron

**12 pentagonal faces**, 20 vertices, 30 edges. Field: $\mathbb{Q}(\sqrt{5})$. Dual to
the icosahedron. The dodecahedron is the **3D analogue** of the pentagon, inheriting all
of its pentagonal-tiling obstructions.

### Icosahedron

**20 triangular faces**, 12 vertices, 30 edges. Field: $\mathbb{Q}(\sqrt{5})$. Dual to
the dodecahedron. Source of 3D quasicrystals (Shechtman's AlMn alloy, AKN tilings).

### Rhombic Dodecahedron

A polyhedron with **12 rhombic faces**. Voronoi cell of the FCC lattice. Tiles space
periodically.

### Truncated Octahedron

The **only Archimedean solid that tiles space periodically by itself**. Result of
truncating each vertex of an octahedron. Its honeycomb is the **Kelvin structure**.

### Triacontahedron

A **30-faced polyhedron** dual to the icosidodecahedron. Its rhombic faces are golden
rhombi. Serves as the **acceptance window** for 3D icosahedral quasicrystals in the
cut-and-project method.

### Dihedral Angle

The **angle between two faces** of a polyhedron meeting at a shared edge. For a regular
dodecahedron: $\approx 116.57°$; for an icosahedron: $\approx 138.19°$. Dihedral angles
determine whether copies of a polyhedron can wrap consistently around an edge.

### Duality

A correspondence between polyhedra **exchanging vertices and faces**:

- Cube ↔ octahedron
- Dodecahedron ↔ icosahedron
- Tetrahedron ↔ tetrahedron (self-dual)

Duality preserves combinatorial structure while swapping dimensions.

---

## Tilings and Honeycombs

### Tiling

A **covering of a space by tiles** with no gaps and no overlaps (except on lower-
dimensional boundaries). In $\mathbb{R}^2$, tiles are typically polygons; in
$\mathbb{R}^3$, polyhedra.

### Honeycomb

A **space-filling tiling of $\mathbb{R}^3$**. Examples:

- Cubic honeycomb (cubes).
- Kelvin honeycomb (truncated octahedra).
- Tetrahedral-octahedral honeycomb (mix of tetrahedra and octahedra).

### Monohedral Tiling

A tiling using **only one tile shape** (up to congruence). The pinwheel tiling is
monohedral (one right-triangle shape with leg ratio 1:2).

### Bitruncated Cubic Honeycomb / Kelvin Structure

The space-filling tiling by **truncated octahedra**. Each tile has 14 faces (6 squares,
8 hexagons). Conjectured by Lord Kelvin (1887) to minimize surface area among equal-
volume partitions — later disproved by the Weaire–Phelan structure.

### Tetrahedral–Octahedral Honeycomb

Space-filling tiling combining **tetrahedra and octahedra in ratio 2:1**. Built on the
FCC lattice. Both tile types appear.

---

## Aperiodic Tilings

### Penrose Tiling

The famous **aperiodic tiling of the plane** with **5-fold symmetry**, using two prototiles
(kites and darts, or thick and thin rhombi). Cut-and-project image of $\mathbb{Z}^5$.
Both a tiling and a quasicrystal.

### Conway–Radin Pinwheel Tiling

An aperiodic tiling of $\mathbb{R}^2$ by **right triangles with leg ratio 1:2**. Famous
for exhibiting **infinitely many tile orientations** — the triangle copies rotate by all
multiples of $\arctan(1/2)$, which is irrational.

### Quasicrystal

An **aperiodic structure** with long-range order and forbidden (crystallographically
impossible) symmetries — most famously 5-fold. Discovered by Shechtman (1984) in
metallic alloys.

### Ammann-Kramer-Neri (AKN) Tiling

The **3D analogue of Penrose tiling**: an aperiodic tiling of $\mathbb{R}^3$ with
**icosahedral symmetry**, built from two prototiles (acute and obtuse golden
rhombohedra).

---

## Cut-and-Project Construction

### Cut-and-Project Method

A general construction of quasicrystals:

1. Take a higher-dimensional lattice $L \subset \mathbb{R}^N$.
2. Decompose $\mathbb{R}^N = E_\parallel \oplus E_\perp$ (physical + perpendicular).
3. Select lattice points whose projection to $E_\perp$ lies in a bounded window $W$.
4. Project these selected points to $E_\parallel$.

The result is a Delone set with quasiperiodic order.

### Acceptance Window

The **bounded region $W \subset E_\perp$** filtering ambient lattice points. Window shape
determines tiling combinatorics. For Penrose: pentagonal window in 5D ambient lattice.
For AKN: triacontahedral window in 6D.

### Cyclotomic Density Trap (revisited)

Without an acceptance window, projections of irrational orbits **densify** the plane.
The window provides bounded selection, restoring discreteness.

---

## Adjacency Structures

### Adjacency Graph $\mathcal{G}$

A graph encoding tile adjacency:

- **Vertices**: tiles of the tiling.
- **Edges**: between tiles sharing an **active edge/face** generator.

This graph carries the combinatorial geometry used in spectral, diffusion, and dimension
analyses.

### Centroid

The **geometric center** of a polygon or polyhedron. Often used to label tiles in
algorithms.

### Edge Midpoint

The **midpoint of an edge**. Center of **edge half-turns** in 3D pinwheel constructions.

---

## Generators and Symmetries

### Generator

A group element (typically a reflection, half-turn, or rotation) used to **build up the
expansion family** by repeated application to a seed tile.

### Active Edge / Active Face

An edge (2D) or face (3D) **carrying a generator operation**. Crossing an active edge maps
one tile to its neighbor.

### Inactive Edge / Inactive Face

An edge or face **carrying no generator** (mere decoration) or a weak gauge generator
(sheet transition without spatial translation).

### Active Set $E_A$ (preview, see Section L)

The **subset of edges/faces used as generators** in a pinwheel construction.

### Edge-Restricted Reconnective

A polygon that **fails full reconnection** but reconnects when its generator set is
**restricted to a subset of edges**. The pentagon is an example: not all five edges can
act as generators simultaneously without forcing tree growth.

### Reflection

An **isometry fixing a hyperplane** (a line in 2D, plane in 3D) and reversing orientation.
Determinant $-1$.

### Face Reflection

A **reflection across a face plane** of a polyhedron. Improper isometry. Used as a 3D
generator.

### Half-Turn

A **rotation by $\pi$** (180°). In 2D about a point; in 3D about an axis.

### Edge Half-Turn

A **rotation by $\pi$ about an edge axis** of a polyhedron. Proper isometry of
determinant $+1$. The 3D analogue of a 2D half-turn used in pinwheel constructions.

### Orientation

An element of the **rotation group** attached to a tile, recording its current angular
state. Two congruent tiles can occupy different orientations in the tiling.

### Orientation Group $\Gamma$

The **subgroup of $O(n)$** generated by rotational parts of all generators. **Finite
orientation group** is one of the reconnection criteria (Criterion 1): infinite
orientation group forces the expansion family to be a tree.

---

## Affine and Linear Structures

### Affine Map

A map $x \mapsto Ax + b$ combining a **linear transformation** $A$ and a **translation**
$b$. The basic building block for tile-to-tile transformations.

### Affine Group $\text{Aff}(\mathbb{R}^n)$

The **group of all affine maps** of $\mathbb{R}^n$, with composition. Decomposes as a
semidirect product $\mathbb{R}^n \rtimes GL(n)$.

### Lattice (Bravais)

A **discrete subgroup of $\mathbb{R}^n$** generated by $n$ linearly independent
translations. Equivalently, $\mathbb{Z}$-spans of a basis.

### Voronoi Cell

The **region of space closest to a given lattice point**. Voronoi cells of a lattice tile
$\mathbb{R}^n$ monohedrally. The Voronoi cell of FCC is the rhombic dodecahedron.

---

## Coverings and Sheets

### Branched Cover

A covering with **ramification points** where multiple sheets meet. The pentagon's
**multi-sheeted cover** branches at vertices: each vertex is a branch point where the
angular deficit accumulates.

### Sheet

A **copy of $\mathbb{R}^n$** in a multi-sheeted covering space, **indexed by an element
of the fiber group**. Tiles live on specific sheets.

### Sheet Index

The **label $s \in G$** identifying which sheet a tile lives on. Different sheets carry
the same tile shape but with different rotational states.

### Sheet Transition

A map between sheets associated with **crossing an edge/face**. Element of the **fiber
group**. Encodes how the angular deficit is absorbed.

### Fiber

The **preimage of a point** under the projection $\pi: \mathcal{M} \to \mathbb{R}^n$ in
a covering space. For a $k$-sheeted cover, the fiber over each non-branch point has $k$
elements.

### Foliation

A decomposition of a manifold into parallel leaves. Sheets in a multi-sheeted cover
**foliate the total space** when interpreted as a stack of $\mathbb{R}^2$ copies.

### Vortex

A **topological defect at a vertex** around which **sheet transitions accumulate**.
Encircling a vortex once yields a nontrivial holonomy (sheet shift); encircling it the
right number of times returns to the original sheet.

---

## Properties of Tilings

### Reconnection

The property that an expansion family **closes into a globally consistent adjacency
graph** rather than diverging as an infinite tree. The central goal of the framework.

### Local Finiteness

Every **compact region contains finitely many tiles**. A bare-minimum requirement for a
valid tiling. Failed when the cyclotomic density trap occurs.

---

## Summary of Key Relationships

- **Pentagon → Multi-sheeted plane**: deficit $36°$ forces 10-sheeted cover (rotations
  by multiples of $36°$).
- **Dodecahedron → 3D analogue**: dihedral structure forces multi-sheeted $\mathbb{R}^3$.
- **Penrose / AKN tilings**: cut-and-project alternatives sidestepping the
  multi-sheeted construction.
- **Kelvin honeycomb**: archetype of crystallographic periodic tiling for comparison.

These objects together form the geometric vocabulary in which the reconnection problem is
posed and the multi-sheeted resolution is constructed.
