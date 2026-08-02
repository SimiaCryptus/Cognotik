# Multi-Polygon Tilings: Pinwheels as Graphs of Connected Shapes

## Overview

The pinwheel construction of `pinwheels.md` produces a single composite tile (base + fins)
that tiles the plane by isometry. But when we look closely at the _traversal_ through a
pinwheel tiling — say, walking from the centroid of one pinwheel to a neighbor — we notice
something striking: the walk **is not direct**. To get from centroid $A$ of pinwheel $P_A$
to centroid $B$ of pinwheel $P_B$, the path passes through _two distinct geometric regions
in sequence_:

1. From centroid $A$ outward through the **fin region** of $P_A$ (the appendage on the
   side facing $P_B$),
2. Across the shared boundary into the **fin region** of $P_B$,
3. Then inward from $P_B$'s fin to centroid $B$.

In other words, the adjacency relation $A \sim B$ is mediated by a **pair of fins**, and
the walk has a natural **four-stage structure**: _centroid-base → fin → fin → centroid-base_.

This observation is the seed of a generalization. If we view the pinwheel not as a single
polygon but as a **graph of two tile types** (base + fin) glued in a specific schedule, we
can ask: what happens if we replace the schedule with an arbitrary one? What if the
sequence of tile types along a walk is _base-A, base-B, base-A, base-B, ..._ (alternating)?
Or _base-A, base-B, base-C_ (cyclic three-coloring)? Or, most generally, a walk on an
arbitrary **type graph** where each step selects the next tile type from a finite set?

This document develops this generalization. The pinwheel becomes a special case of a much
broader family of **multi-polygon tilings**, in which the plane is decomposed into multiple
interlocking tile types whose adjacencies are governed by a **type graph** (or equivalently,
a **schedule automaton**). The framework subsumes pinwheels, periodic multi-color tilings
(e.g., the truncated square tiling, the rhombitrihexagonal tiling), substitution tilings
(Penrose, Ammann–Beenker), and a host of new constructions in between.

---

## 1. The Pinwheel as a Two-Type Walk

### 1.1 Anatomy of a Pinwheel Traversal

Let $P$ be a rectangular pinwheel as in `pinwheels.md` Section 1.2: a unit square base $B$
with four right-triangle fins $F_1, F_2, F_3, F_4$ attached at the four edges by $C_4$
rotation. Each fin has a hypotenuse as its free (outward) edge, and the hypotenuses of
adjacent pinwheels in the tiling match up to form the **interlocking boundary**.

Consider two adjacent pinwheels $P_A$ and $P_B$ sharing a common hypotenuse-pair: fin
$F_A^{(k)}$ of $P_A$ is glued along its hypotenuse to fin $F_B^{(\ell)}$ of $P_B$. The
**dual adjacency walk** from $A$ (centroid of $P_A$) to $B$ (centroid of $P_B$) passes
through:

- **Stage 1**: $A$ (centroid of base $B_A$).
- **Stage 2**: Cross the boundary $B_A \to F_A^{(k)}$ (a partial edge of the base square).
- **Stage 3**: Cross the boundary $F_A^{(k)} \to F_B^{(\ell)}$ (the shared hypotenuse).
- **Stage 4**: Cross the boundary $F_B^{(\ell)} \to B_B$.
- **Stage 5**: $B$ (centroid of base $B_B$).

The walk visits **two tile types in an alternating pattern**: $\textbf{base, fin, fin, base}$
— or, reading just the type sequence, $(B, F, F, B)$. The fins serve as **bridge tiles**
between centroid-bearing base tiles.

### 1.2 The Type Graph of a Pinwheel

We can summarize the adjacency structure of pinwheel tilings as a **type graph** $\mathcal{T}$:

- **Vertices** of $\mathcal{T}$: tile types $\{B, F\}$ (base, fin).
- **Edges** of $\mathcal{T}$: allowed type-to-type adjacencies. In a pinwheel:
  - $B \sim F$ (a base is adjacent to its own fins),
  - $F \sim F$ (two fins from adjacent pinwheels share a hypotenuse),
  - $B \sim B$ does **not** occur (no two bases share a boundary directly).

Equivalently, in the language of walks: starting at type $B$, the allowed type-transitions
are $B \to F$ and $F \to F$ and $F \to B$, but never $B \to B$ directly. The walk pattern
on type space is therefore constrained, and the **type-walk** from $B$ to $B$ must pass
through at least one $F$-segment (in fact at least two in a pinwheel: the fin of $P_A$ and
the fin of $P_B$).

### 1.3 Generalization: Replace $\mathcal{T}$ with Anything

Once we recognize the pinwheel as a tiling with a specific two-vertex type graph and a
specific transition rule, the generalization is immediate: **replace $\mathcal{T}$ with an
arbitrary finite directed graph** whose vertices are tile types and whose edges are allowed
adjacencies. The tiling is then constructed by:

1. Choosing a starting tile type $t_0 \in V(\mathcal{T})$.
2. At each step, choosing the next tile type by following an outgoing edge of $\mathcal{T}$.
3. Realizing the chosen type as a geometric tile, placed adjacently to the previous one
   along a compatible boundary.

The pinwheel is the case $V(\mathcal{T}) = \{B, F\}$ with the specific edge rules above.
Other choices of $\mathcal{T}$ give other constructions, many of which are well-known in
the tiling literature, and some of which are new.

---

## 2. The Multi-Polygon Tiling Framework

### 2.1 Formal Definition

A **multi-polygon tiling system** is a tuple $(\mathcal{T}, \{P_t\}_{t \in V(\mathcal{T})},
\{M_{tt'}\}_{(t,t') \in E(\mathcal{T})})$, where:

1. $\mathcal{T}$ is a finite directed graph (the **type graph**),
2. For each vertex $t \in V(\mathcal{T})$, $P_t$ is a polygon (the **tile of type $t$**),
3. For each edge $(t, t') \in E(\mathcal{T})$, $M_{tt'}$ is a **matching rule** specifying
   which edges of $P_t$ may abut which edges of $P_{t'}$ and how.

A **valid tiling** $\mathcal{T}\text{-tiling}$ is an assignment of tile copies to regions
of $\mathbb{R}^2$ such that:

- Every point of $\mathbb{R}^2$ is covered by exactly one tile (up to boundary).
- Every adjacency between two tiles is of a type $(t, t') \in E(\mathcal{T})$, and the
  matching rule $M_{tt'}$ is satisfied at the shared boundary.

The **type-walk** along any path in the dual adjacency graph reads out as a walk on
$\mathcal{T}$: each step in the dual graph corresponds to one edge of $\mathcal{T}$.

### 2.2 Specializations

Different choices of $\mathcal{T}$ recover or generalize many familiar tiling families:

| $\mathcal{T}$                     | Tile types                      | Example tiling                            |
| --------------------------------- | ------------------------------- | ----------------------------------------- |
| Single vertex with self-loop      | $\{P\}$                         | Square, hexagonal, pinwheel (as monotile) |
| Two vertices, bipartite           | $\{A, B\}$                      | Checkerboard, truncated square tiling     |
| Two vertices, pinwheel rules      | $\{B, F\}$                      | Pinwheel polygon (as 2-tile system)       |
| Three vertices, cyclic            | $\{A, B, C\}$                   | Trihexagonal, snub square                 |
| $k$-cycle on $k$ vertices         | $\{A_1, \dots, A_k\}$           | $k$-coloring tilings                      |
| Complete graph $K_n$              | $\{P_1, \dots, P_n\}$           | Arbitrary $n$-coloring tilings            |
| Penrose substitution graph        | $\{\text{thin}, \text{thick}\}$ | Penrose rhombus tiling                    |
| Ammann–Beenker substitution graph | $\{\text{rhomb}, \text{sq.}\}$  | Ammann–Beenker tiling                     |

Note that **substitution tilings** fit naturally into this framework when the type graph
is decorated with **scale-change edges** (an edge $A \to B$ means "after inflation, a tile
of type $A$ becomes a region containing tiles of type $B$").

### 2.3 The Schedule Automaton

An equivalent formulation views the type graph as the state-transition diagram of a
**schedule automaton** $\mathcal{A}$:

- **States** of $\mathcal{A}$: tile types $t \in V(\mathcal{T})$.
- **Transitions** of $\mathcal{A}$: allowed type-to-type transitions $(t \to t') \in E(\mathcal{T})$.
- **Input symbol** at each transition: the _direction_ (or edge-class) along which the
  transition occurs (e.g., "north", "across hypotenuse", "fin-side", etc.).

A valid tiling is then a _labeling_ of $\mathbb{R}^2$ by states of $\mathcal{A}$ such that
adjacent labels are consistent with the transitions of $\mathcal{A}$. This is the
**shift-of-finite-type (SFT)** view of multi-polygon tilings, and it connects the
framework directly to symbolic dynamics and Wang tile systems.

---

## 3. The Pinwheel Schedule Made Explicit

Returning to the pinwheel: the type graph $\mathcal{T}_{\text{pinwheel}}$ has the
following structure.

### 3.1 Vertices

- $B$: the **base** tile (e.g., the unit square in the rectangular pinwheel).
- $F$: the **fin** tile (e.g., the right triangle).

### 3.2 Edges

Each edge of $\mathcal{T}$ is labeled by the _boundary class_ along which the transition
occurs. For the rectangular pinwheel:

- $(B, F, \text{leg-1})$: a base shares its short fin-leg edge with a fin.
- $(F, F, \text{hypotenuse})$: a fin shares its hypotenuse with another fin (from the
  neighboring pinwheel).
- $(F, B, \text{leg-1})$: symmetric to the first.
- $(B, B, *)$: **absent**. Bases never share a boundary directly in a pinwheel tiling.
- $(B, F, \text{leg-2})$ — the second leg, also present.

### 3.3 The Walk Sequence

The walk in the dual graph (one vertex per tile, edges between adjacent tiles) from base
$B_A$ to base $B_B$ in two adjacent pinwheels reads:

$B_A \to F_A \to F_B \to B_B$

on the type graph. The minimal type-walk between two bases is therefore of length 3 (three
edge-traversals), passing through two fin tiles. This is the **type-distance 3 property**
of the pinwheel: bases are not directly adjacent, but separated by two fin-stages.

### 3.4 Why Length 3?

The length-3 structure of the pinwheel walk is a direct consequence of the **fin
architecture**: each pinwheel has fins on its outside, and to get from one pinwheel's
interior to another's, the walk must:

1. Exit the base of $P_A$ into a fin of $P_A$ (1 transition).
2. Cross the hypotenuse between fins of $P_A$ and $P_B$ (1 transition).
3. Enter the base of $P_B$ from its fin (1 transition).

Total: 3 transitions, 2 fin-stages, 4 stages overall.

Generalizations of the pinwheel to different fin architectures change this length:

- **Direct adjacency** (no fins, $\mathcal{T}$ is a single self-looping vertex):
  length 1.
- **Pinwheel** (one fin layer per side): length 3.
- **Double-fin** (two concentric fin layers per side): length 5.
- **$k$-fin** ($k$ concentric layers): length $2k + 1$.

The pinwheel is the **simplest non-trivial case** in a tower of constructions indexed by
the type-walk length between centroid-bearing tiles.

---

## 4. Alternating Polygons: The Two-Type Case

The simplest generalization of the pinwheel is to take $\mathcal{T}$ with two vertices and
require the type-walk to **strictly alternate**: $A, B, A, B, \dots$. This means:

- $V(\mathcal{T}) = \{A, B\}$,
- $E(\mathcal{T}) = \{(A, B), (B, A)\}$, no self-loops,
- Every adjacency is between an $A$-tile and a $B$-tile.

The dual adjacency graph is therefore **bipartite**.

### 4.1 Examples of Alternating Two-Type Tilings

- **Checkerboard**: $P_A = P_B = $ unit square, both colored differently. Bipartite, periodic,
  trivially reconnective with $\mathbb{F} = \mathbb{Q}$.
- **Truncated square tiling**: $P_A = $ octagon, $P_B = $ small square. Each octagon is
  adjacent only to small squares (and vice versa). Bipartite, periodic, $\mathbb{F} =
  \mathbb{Q}(\sqrt{2})$.
- **Trihexagonal tiling**: $P_A = $ triangle, $P_B = $ hexagon. Bipartite (each triangle
  is adjacent only to hexagons and vice versa). Periodic, $\mathbb{F} = \mathbb{Q}(\sqrt{3})$.
- **Snub square tiling** (partial): can be partitioned into alternating triangle/square
  strata.

Many of these are **Archimedean tilings** and have been classified completely in the
crystallographic literature. The multi-polygon framework reveals them as instances of a
common abstract pattern.

### 4.2 Reconnection Properties of Alternating Tilings

The reconnection criteria of `affine.md` extend naturally:

- **Criterion 1** (finite orientation group): both $P_A$ and $P_B$ must individually have
  finite orientation groups, _and_ the composite group generated by edge-reflections of
  both tiles must be finite. For Archimedean tilings, this is automatic.
- **Criterion 2** (single quadratic field): the coordinate field $\mathbb{F}$ must remain
  a simple quadratic extension. This restricts the pair $(P_A, P_B)$ — e.g., the pair
  (square, regular octagon) gives $\mathbb{F} = \mathbb{Q}(\sqrt{2})$ (one quadratic),
  but a pair (square, regular pentagon) would give $\mathbb{F} = \mathbb{Q}(\sqrt{5})$
  _combined with_ the square's $\mathbb{Q}$, still a single quadratic field — admissible.
  A pair (regular pentagon, regular hexagon) would give $\mathbb{F} = \mathbb{Q}(\sqrt{3},
  \sqrt{5})$, a degree-4 extension — **inadmissible** by Criterion 2.

The framework therefore predicts which multi-polygon pairings are reconnective and which
are not, purely from the algebraic content of each tile.

### 4.3 Schedule Variations

Even with two tile types, several schedule variants are possible:

- **Strict alternation** ($A \to B \to A \to B$): bipartite tilings as above.
- **Pinwheel schedule** ($B \to F \to F \to B$): two consecutive fins between bases.
- **Sticky alternation** ($A \to A \to B \to B \to A$): pairs of same-type tiles before
  switching. Realized by, e.g., domino-like double tiles.
- **Weighted alternation**: stochastic transitions $A \to A$ with probability $p$ and
  $A \to B$ with probability $1 - p$. Realized by random tilings with type-correlations.

Each schedule is a different walk pattern on $\mathcal{T}$, and the resulting tiling has
different statistical and spectral properties.

---

## 5. Three-Type and Higher: Cyclic and Star Schedules

### 5.1 Three-Type Cyclic Schedule

With three tile types $\{A, B, C\}$ and a cyclic schedule $A \to B \to C \to A \to \dots$,
the type graph is the directed 3-cycle. Realizations include:

- **Trihexagonal hybrid**: triangles, hexagons, and small fillers in a cyclic adjacency.
- **Rhombitrihexagonal tiling**: triangles, squares, hexagons in a 3-coloring.
- **Color-rotated pinwheel**: three pinwheels per supercell, related by $C_3$ symmetry.

The reconnection criterion now requires the coordinate field of _all three_ tiles to lie
in a single quadratic extension. For Archimedean tilings using $\{$triangle, square,
hexagon$\}$, the union is $\mathbb{Q}(\sqrt{3})$, which is a single quadratic — admissible.

### 5.2 Star Schedule (Hub-and-Spoke)

A **star schedule** has a single "hub" type $H$ adjacent to multiple "spoke" types
$\{S_1, S_2, \dots, S_k\}$, with no direct spoke-to-spoke adjacencies:

- $V(\mathcal{T}) = \{H, S_1, \dots, S_k\}$,
- $E(\mathcal{T}) = \{(H, S_i), (S_i, H) : 1 \le i \le k\}$.

This is reminiscent of the pinwheel architecture (base + multiple fins, each fin different)
but generalized to allow $k$ distinct fin types per base. The walk pattern between two
hubs is always _hub-spoke-hub_, a length-2 traversal — but the spoke can be of any of
$k$ types.

Star schedules naturally model **decorated pinwheels** where each fin carries a different
decoration or sub-tile.

### 5.3 Tree Schedule

A schedule whose type graph $\mathcal{T}$ is a tree (no cycles) forces every type-walk
between two vertices to be unique. Such schedules realize **hierarchical tilings** where
each tile type has a fixed role in a hierarchy: a parent type, a sibling type, child
types, etc.

This connects naturally to substitution tilings and L-system-generated tilings.

### 5.4 Complete-Graph Schedule

The most permissive schedule is the complete graph $K_n$ on $n$ tile types: any tile type
can be adjacent to any other. This is the **maximum-entropy** multi-polygon system, and
it models random tilings with $n$ tile types and unrestricted local adjacencies.

---

## 6. Centroid-Mediated vs. Boundary-Mediated Walks

A key conceptual point: in the pinwheel, the **centroid** of the base plays a privileged
role as the "vertex" of the dual adjacency graph, and the fins are bridge regions between
centroids. But in a general multi-polygon system, we can ask: _which tiles are centroid-
bearing and which are bridges?_

This is a **role assignment** on the type graph:

- **Centroid types**: tiles whose centroids appear as vertices of the dual graph.
- **Bridge types**: tiles whose presence is only as a "passage" between centroids.

In the pinwheel, $B$ is a centroid type and $F$ is a bridge type. In a checkerboard, both
$A$ and $B$ are centroid types (the dual graph has vertices for _every_ tile).

The role assignment changes the effective adjacency graph dramatically:

- **All-centroid**: dual graph = adjacency graph of all tiles. Dense, high valence.
- **Centroid + bridges**: dual graph contracts bridge regions, lowering valence and
  potentially merging multiple bridges into a single edge.
- **Bridge-mediated**: every walk between centroids must pass through at least one bridge
  stage, lengthening type-walks.

This is the multi-polygon analogue of edge contraction in graph theory, and it provides
a tunable parameter for the effective dimension and spectral properties of the dual graph.

### 6.1 Example: Pinwheel as Centroid-Mediated

In the pinwheel, contracting all $F$-tiles to "boundary regions" between $B$-tiles gives
the dual adjacency graph of the base squares — a periodic 2D lattice with valence 4 (or
higher, depending on how interlocking fins are counted). The fin tiles are _implicit_ in
this view; they appear only as the "thickness" of the bonds between bases.

Equivalently, one can keep the fins as explicit vertices of the dual graph, giving a
higher-valence lattice with mixed vertex types. Both views are valid and yield the same
underlying tiling, but they emphasize different aspects.

---

## 7. The Adjacency Oracle for Multi-Polygon Systems

The adjacency oracle of `idea.md` Section 6 generalizes immediately to multi-polygon
systems. Given a current tile type $t$ at position $\mathbf{x}$ with orientation
$\theta$, the oracle returns the set of neighbor tile types and their placements:

```
AdjacencyOracle(t, x, theta):
    for each outgoing edge (t, t') in T:
        for each matching edge-class M_{tt'}:
            yield (t', x', theta')   # placement of neighbor
```

The oracle thus enumerates not just _positions_ but also _types_. This makes the expansion
family a walk on a **typed labeled graph**, with the type graph $\mathcal{T}$ providing the
discrete combinatorial backbone and the matching rules providing the geometric realization.

### 7.1 Type-Walk Statistics

For a random walk on the dual adjacency graph of a multi-polygon tiling, the **type-walk
statistics** — the frequencies of visiting each tile type — are encoded by the stationary
distribution of the random walk on $\mathcal{T}$. For the pinwheel with type graph
$B \to F \to F \to B \to \dots$, the stationary frequencies are:

- $\pi_B = \frac{\text{area}(B)}{\text{area}(B) + 4 \cdot \text{area}(F)}$,
- $\pi_F = \frac{4 \cdot \text{area}(F)}{\text{area}(B) + 4 \cdot \text{area}(F)}$,

where the factor of 4 accounts for the four fins per base in a $C_4$ pinwheel. More
generally, $\pi_t$ is proportional to the total area covered by tiles of type $t$.

### 7.2 Hitting Times and Type-Distance

The **type-distance** between two types $t, t'$ on the type graph $\mathcal{T}$ — the
minimum number of transitions to go from $t$ to $t'$ — is a fundamental invariant. It
measures the "depth" of the walk pattern:

- Pinwheel: type-distance $d(B, B) = 3$ (must pass through 2 fins).
- Checkerboard: type-distance $d(A, A) = 2$ (must pass through 1 of the other type).
- $k$-fin pinwheel: type-distance $d(B, B) = 2k + 1$.

Hitting times on the dual adjacency graph scale as the type-distance multiplied by the
geometric walk length, giving a clean separation of _combinatorial_ and _geometric_
contributions to spectral quantities.

---

## 8. Algebraic Compactness for Multi-Polygon Systems

The framework of `affine.md` extends:

- **Criterion 1'** (finite orientation group on $\mathcal{T}$): the orientation group
  generated by the union of edge-reflections across _all_ tile types must be finite.
- **Criterion 2'** (single quadratic field): the union of coordinate fields of all tile
  types must lie in a single quadratic extension $\mathbb{F}$ of $\mathbb{Q}$.

These criteria interact subtly with the type graph $\mathcal{T}$:

- If $\mathcal{T}$ has multiple connected components, each component can be analyzed
  independently. Tiles in disconnected components never interact, and their algebraic
  compactness is independent.
- If $\mathcal{T}$ is connected but contains both "rational" and "irrational" tile types
  (e.g., a square base and a pentagonal fin), the combined field is determined by the
  _union_ of their fields. For a square + pentagon system, $\mathbb{F} = \mathbb{Q}(\sqrt{5})$,
  a single quadratic — admissible.
- If $\mathcal{T}$ contains tile types from incompatible quadratic extensions (e.g., a
  pentagonal base and a hexagonal fin), $\mathbb{F} = \mathbb{Q}(\sqrt{3}, \sqrt{5})$, a
  degree-4 extension — inadmissible by Criterion 2'.

The framework thus gives a clear **algebraic compatibility test** for whether a proposed
multi-polygon system can reconnect.

---

## 9. The Reconnection Hierarchy for Multi-Polygon Systems

Combining the type graph $\mathcal{T}$ with the algebraic criteria yields a refined
hierarchy (compare `pinwheels.md` Section 6):

| Level | $\mathcal{T}$ structure                        | Algebraic compatibility                                          | Example                                                        |
| ----- | ---------------------------------------------- | ---------------------------------------------------------------- | -------------------------------------------------------------- |
| 0     | Connected, finite                              | All tiles in $\mathbb{Q}$ or single $\mathbb{Q}(\sqrt{d})$       | Square checkerboard, trihexagonal tiling                       |
| 1     | Connected, finite                              | Single quadratic, but angular deficit forces multi-sheeted cover | Penrose tiling (thin + thick rhombs in $\mathbb{Q}(\sqrt{5})$) |
| 2     | Connected, finite                              | Compatible quadratics on a subset; restrict to that subset       | Mixed Archimedean tilings with one "free" tile type dropped    |
| 3     | Connected, infinite or incompatible quadratics | —                                                                | Random multi-tile system with arbitrary fins                   |

The pinwheel ($\mathcal{T} = \{B, F\}$ with the pinwheel rules) is a Level 0 or Level 1
instance depending on the symmetry order, as in `pinwheels.md` Section 6. The
multi-polygon framework simply embeds this in a broader landscape.

---

## 10. Examples Beyond the Pinwheel

### 10.1 The Truncated Hexagonal Pinwheel

Replace the square base of the rectangular pinwheel with a regular hexagon and the right
triangular fins with isoceles triangles. The type graph remains $\{B, F\}$ with the same
edges, but now $B = $ hexagon and $F = $ isoceles triangle. The pinwheel has $C_6$
symmetry, $\mathbb{F} = \mathbb{Q}(\sqrt{3})$, and reconnects on a refinement of the
triangular lattice. The type-walk between bases still has length 3.

### 10.2 The Alternating Square-Octagon Tiling

Type graph: $\{S, O\}$, bipartite (each $S$ adjacent only to $OO$ adjacent
only to $S$'s except along its long edges, where $O$-to-$O$ adjacencies are also allowed).

Tile shapes: $S = $ small square, $O = $ regular octagon. The Archimedean truncated
square tiling. $\mathbb{F} = \mathbb{Q}(\sqrt{2})$. Level 0 reconnective.

### 10.3 The Three-Layer Pinwheel ($k = 2$)

Type graph: $\{B, F_1, F_2\}$ with linear adjacency $B \leftrightarrow F_1 \leftrightarrow
F_2 \leftrightarrow F_2 \leftrightarrow F_1 \leftrightarrow B$. The walk from base to base
passes through _two_ fin layers per side, giving type-distance 5.

Geometrically: a base polygon surrounded by a first fin ring, then a second fin ring, with
the second fins of adjacent pinwheels sharing hypotenuses. The interior architecture is
more complex but the algebraic analysis proceeds identically.

### 10.4 The Pinwheel-Penrose Hybrid

Type graph: $\{B, F, T_1, T_2\}$ where $B, F$ are pinwheel tiles and $T_1, T_2$ are
Penrose rhombs (thin and thick). The fins of the pinwheel are glued to Penrose rhombs at
matching edges, creating a hierarchical tiling where pinwheel pockets are connected by
Penrose corridors.

This requires $\mathbb{F} = \mathbb{Q}(\sqrt{5})$ throughout (the pinwheel fins must be
chosen with $\sqrt{5}$-compatible parameters), placing the system at Level 1.

### 10.5 Random Multi-Tile System

Type graph: $K_n$ (complete graph), with $n$ tile types each a random irregular polygon.
Generically, the algebraic compatibility fails (incompatible irrationalities), placing
this at Level 3 — non-reconnective. The tiling is a random tree.

---

## 11. Algorithmic Construction of Multi-Polygon Tilings

Constructing a multi-polygon tiling programmatically:

```python
def construct_multi_polygon_tiling(type_graph, tile_shapes, matching_rules, seed_type, seed_position, max_depth):
    """
    Construct a multi-polygon tiling by depth-first expansion from a seed tile.

    Args:
        type_graph: dict mapping tile type -> list of (neighbor_type, edge_class) pairs.
        tile_shapes: dict mapping tile type -> polygon (list of vertices).
        matching_rules: dict mapping (type, neighbor_type, edge_class) -> placement transform.
        seed_type: type of the seed tile.
        seed_position: (x, y, theta) of the seed tile.
        max_depth: how many expansion steps to perform.

    Returns:
        A list of placed tiles, each tagged with its type and position.
    """
    placed = [(seed_type, seed_position)]
    frontier = [(seed_type, seed_position, 0)]
    while frontier:
        t, pos, depth = frontier.pop()
        if depth >= max_depth:
            continue
        for (t_prime, edge_class) in type_graph[t]:
            transform = matching_rules[(t, t_prime, edge_class)]
            new_pos = apply_transform(pos, transform)
            if not collides_with_placed(t_prime, new_pos, placed):
                placed.append((t_prime, new_pos))
                frontier.append((t_prime, new_pos, depth + 1))
    return placed
```

Key subtleties:

1. **Type graph traversal**: the order of expansion (DFS, BFS, priority queue) affects
   which valid tilings emerge from non-deterministic type graphs.
2. **Matching rule consistency**: the placement transforms must be invertible and
   compatible (i.e., applying $M_{tt'}$ then $M_{t't}$ should return to the original tile).
3. **Collision detection**: in multi-type systems, the geometric collision check is more
   expensive than in mono-type systems because tile shapes vary.
4. **Exact arithmetic**: as always, work in $\mathbb{F}$ throughout to preserve algebraic
   compactness.

---

## 12. Open Questions

1. **Type graph classification**: For each finite type graph $\mathcal{T}$ and each tile-
   shape assignment, when does a valid tiling exist? This is the multi-polygon analogue of
   the Wang tile undecidability question and is expected to have deep connections to
   symbolic dynamics.

2. **Optimal type graphs for expansion**: Among all type graphs $\mathcal{T}$ with $n$
   vertices, which yield multi-polygon tilings with the largest spectral gap on their
   dual adjacency graph? Is there an analogue of the Ramanujan property for typed
   tilings?

3. **Substitution as type-graph dynamics**: Penrose, Ammann–Beenker, and other
   substitution tilings can be encoded as type graphs with inflation rules. Can every
   primitive substitution tiling be realized as a multi-polygon system with a finite
   type graph?

4. **Stochastic schedules**: When the type graph is decorated with transition
   _probabilities_ rather than allowed/forbidden edges, what is the typical behavior of
   random multi-polygon tilings? Connections to random tilings, dimers, and statistical
   mechanics.

5. **3D multi-polyhedron systems**: The framework extends to 3D by replacing polygons
   with polyhedra and edges with faces. What 3D type graphs admit space-filling
   realizations? Does this subsume the Schmitt–Conway–Danzer biprism?

6. **Type graphs and crystallographic groups**: For each wallpaper group, what is the
   minimal type graph realizing a tiling with that group as its symmetry? Is there a
   natural "categorification" of wallpaper groups via type graphs?

7. **Centroid/bridge role assignments**: When does a multi-polygon tiling admit multiple
   valid centroid/bridge role assignments, and how do these correspond to different dual
   graph structures?

---

## 13. Summary

The pinwheel construction of `pinwheels.md` is, structurally, a **two-type tiling system**
in which a base tile $B$ and a fin tile $F$ are glued according to a specific schedule:
$B \to F \to F \to B$. The walk from one base to another passes through two fin stages,
giving a type-distance of 3 between centroid-bearing bases.

Generalizing this observation, we define a **multi-polygon tiling system** as a finite
directed type graph $\mathcal{T}$ together with a polygon $P_t$ for each vertex $t$ and a
matching rule $M_{tt'}$ for each edge $(t, t') \in E(\mathcal{T})$. The pinwheel is one
instance; checkerboards, Archimedean tilings, Penrose tilings, and many other constructions
are others.

The framework is:

- **General**: any finite type graph and any set of polygon shapes can be combined.
- **Algebraically structured**: the reconnection criteria of `affine.md` (finite
  orientation group, single quadratic coordinate field) extend straightforwardly to the
  multi-polygon setting, predicting which type graphs yield reconnective tilings and
  which do not.
- **Combinatorially rich**: the type graph $\mathcal{T}$ provides a symbolic-dynamics
  backbone for the tiling, separating combinatorial structure (walks on $\mathcal{T}$)
  from geometric realization (placements in $\mathbb{R}^2$).
- **Hierarchical**: multi-polygon systems include substitution tilings (as type graphs
  with inflation), hierarchical pinwheels, and mixed periodic/aperiodic constructions.

The most fundamental insight is the **role of bridge tiles**: in the pinwheel, fins are
bridges between centroid-bearing bases, and the type-distance of 3 measures the depth of
indirection in the walk. By varying the type graph, one can construct tilings with
arbitrary type-distance structures, controllable spectral properties, and tunable
algebraic complexity — all from a small finite-state symbolic skeleton.

Multi-polygon tilings thus form the **categorical generalization** of pinwheels: instead
of one polygon with a fixed schedule, we have a graph of polygons with an arbitrary
schedule, and the pinwheel is the canonical example showing that the schedule itself —
not just the geometry — carries essential information about the resulting tiling's
structure.
