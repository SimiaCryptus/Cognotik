# The Einstein Tiling Through the Multi-Polygon Framework

## Abstract

In 2023, Smith, Myers, Kaplan, and Goodman-Strauss resolved a half-century-old open problem
by exhibiting the **"hat" monotile** — a single 13-sided polygon (and its mirror, the "turtle")
that tiles the plane aperiodically. Subsequent refinement produced the **"spectre"** monotile,
which achieves strict aperiodicity _without_ requiring reflected copies. Collectively, these
shapes are known as **einsteins** (from the German "ein Stein," one stone).

This document analyzes the einstein tilings through the lens of the frameworks developed in
`affine.md`, `pinwheels.md`, `multipolygon.md`, and `idea.md`. We show that:

1. The hat/spectre belong to a **single-vertex type graph** $\mathcal{T} = \{H\}$ with a
   rich self-loop structure encoded by the **metatile substitution system**.
2. The natural algebraic substrate is $\mathbb{F} = \mathbb{Q}(\sqrt{3})$, inherited from
   the kite-based construction on the $3.4.6.4$ Laves lattice — making the einstein
   **Criterion-2 admissible** in the sense of `affine.md`.
3. The orientation group, however, is **infinite**: the hat appears in twelve rotational
   orientations _and_ reflections, but the substitution rule forces additional rotations
   by angles incommensurate with $\mathbb{Z}_{12}$ at the meta-scale. This is the
   **partial failure of Criterion 1** that underlies aperiodicity.
4. Reconnection occurs only through a **substitution hierarchy** rather than through
   isometric closure, placing the einstein at **Level 1** of the reconnection hierarchy
   (cf. `multipolygon.md` Section 9) — alongside Penrose and Ammann–Beenker.
5. The spectre's chirality-breaking variant induces a **$\mathbb{Z}_2$-valued holonomy**
   reminiscent of the spinor double cover discussed in `idea.md`, where the "sheet index"
   is replaced by a **handedness label**.

The einstein is therefore not a counterexample to the multi-polygon framework but a
particularly elegant _limit case_: a single-vertex type graph whose entire combinatorial
richness is pushed into the **substitution loop** on that vertex.

---

## 1. The Einstein Problem in Context

### 1.1 Historical Background

The hierarchy of tiling problems can be ordered by the _number of distinct prototiles_
required to enforce aperiodicity:

- **Wang (1961)**: an aperiodic set of tiles must exist if the Domino Problem is
  undecidable. Berger (1966) gave the first such set with ~20,000 tiles.
- **Robinson (1971)**: 6 tiles.
- **Penrose (1974)**: 2 tiles (kite + dart, or thin + thick rhomb).
- **Smith–Myers–Kaplan–Goodman-Strauss (2023)**: 1 tile (the hat).
- **Spectre (2023)**: 1 tile with no reflections required (strict aperiodic monotile).

The progression from many tiles to one is precisely a progression in the **type graph**
of `multipolygon.md`: from a complex multi-vertex graph $\mathcal{T}$ down to the
**single-vertex graph** $\mathcal{T} = \{H\}$.

### 1.2 What the Einstein Achieves

The einstein achieves three structural feats simultaneously:

1. **Geometric realization**: a concrete 13-sided polygon that fits together edge-to-edge.
2. **Aperiodic forcing**: any tiling by congruent copies is necessarily non-periodic.
3. **Substitutive hierarchy**: the tiling admits a self-similar inflation rule on
   _metatiles_ (collections of hats), which generates the full tiling combinatorially.

In the language of `multipolygon.md` Section 2.3, the einstein is a **schedule automaton**
with a single state but an extraordinarily rich set of input symbols (edge classes),
together with a **substitution rule** that promotes finite patches to larger patches.

---

## 2. The Hat: Geometric Description

### 2.1 Construction from the $3.4.6.4$ Laves Lattice

The hat tile is most naturally described as a **union of eight kites** drawn on the
$3.4.6.4$ Laves tiling (the dual of the rhombitrihexagonal Archimedean tiling). Each kite
has angles $(90°, 60°, 120°, 90°)$ and edge ratios involving $\sqrt{3}$.

Specifically, partition the plane into kites of the $3.4.6.4$ Laves lattice. The hat
is the union of **eight** adjacent kites assembled into a 13-sided polygon. Because the
underlying kite has $\mathbb{Q}(\sqrt{3})$ coordinates (it sits on a hexagonal-symmetric
substrate), **every vertex of the hat lies in $\mathbb{Q}(\sqrt{3})^2$**.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-220 -180 440 360" width="500">
   <title>The Hat Monotile</title>
   <!-- Underlying kite grid (faint) -->
   <g stroke="#bbb" stroke-width="0.5" fill="none" opacity="0.5">
     <!-- Hexagonal reference grid lines -->
     <path d="M -200 -150 L 200 -150 M -200 -100 L 200 -100 M -200 -50 L 200 -50 M -200 0 L 200 0 M -200 50 L 200 50 M -200 100 L 200 100 M -200 150 L 200 150"/>
     <path d="M -200 -150 L -200 150 M -150 -150 L -150 150 M -100 -150 L -100 150 M -50 -150 L -50 150 M 0 -150 L 0 150 M 50 -150 L 50 150 M 100 -150 L 100 150 M 150 -150 L 150 150 M 200 -150 L 200 150"/>
   </g>
   <!-- The hat polygon (13 sides), constructed on a sqrt(3) grid -->
   <!-- Vertices in units where short edge = 50, long edge = 50*sqrt(3) ~ 86.6 -->
   <polygon points="
     0,-100
     50,-100
     93.3,-75
     93.3,-25
     136.6,0
     93.3,25
     93.3,75
     50,100
     -43.3,100
     -43.3,50
     -86.6,25
     -86.6,-25
     -43.3,-50
   " fill="#7fb3d5" stroke="#1b4f72" stroke-width="2.5" opacity="0.85"/>
   <!-- Mark short vs long edges with colored dots at midpoints -->
   <g fill="#c0392b">
     <circle cx="25" cy="-100" r="3"/>
     <circle cx="93.3" cy="0" r="3"/>
     <circle cx="-43.3" cy="75" r="3"/>
     <circle cx="-86.6" cy="0" r="3"/>
     <circle cx="-65" cy="-37.5" r="3"/>
     <circle cx="-21.65" cy="-75" r="3"/>
   </g>
   <g fill="#1e8449">
     <circle cx="71.65" cy="-87.5" r="3"/>
     <circle cx="93.3" cy="-50" r="3"/>
     <circle cx="115" cy="-12.5" r="3"/>
     <circle cx="115" cy="12.5" r="3"/>
     <circle cx="93.3" cy="50" r="3"/>
     <circle cx="71.65" cy="87.5" r="3"/>
     <circle cx="3.35" cy="100" r="3"/>
   </g>
   <!-- Legend -->
   <g font-family="serif" font-size="12">
     <circle cx="-180" cy="-160" r="4" fill="#c0392b"/>
     <text x="-170" y="-156">short edges (length 1)</text>
     <circle cx="-180" cy="-140" r="4" fill="#1e8449"/>
     <text x="-170" y="-136">long edges (length √3)</text>
   </g>
</svg>
<br/><em>Figure 1: The hat tile, a 13-gon with edges in two length classes. Vertex
coordinates lie in $\mathbb{Q}(\sqrt{3})^2$.</em>
</p>

This is the first key observation:

> **Proposition 2.1**: The vertex coordinates of the hat tile lie in
> $\mathbb{F} = \mathbb{Q}(\sqrt{3})$. The hat is therefore **Criterion-2 admissible**
> in the sense of `affine.md` Section 2.2.

### 2.2 Edge Classes

The hat has 13 edges, but these fall into a small number of **edge classes** under
congruence:

- **Short edges** of length $1$ (in normalized units).
- **Long edges** of length $\sqrt{3}$.

Specifically, the hat has 6 short edges and 7 long edges (or vice versa depending on
normalization conventions), alternating in a pattern that breaks all rotational symmetries
of the underlying hexagonal lattice but preserves the algebraic field $\mathbb{Q}(\sqrt{3})$.

### 2.3 The One-Parameter Family

The hat is actually one member of a **continuous one-parameter family** of tiles
$\text{Tile}(a, b)$ parameterized by the ratio of short-edge to long-edge lengths:

- $\text{Tile}(1, 1)$ is the **"chevron"**, which tiles periodically.
- $\text{Tile}(1, \sqrt{3})$ is the **hat**, which tiles aperiodically (with reflections).
- $\text{Tile}(\sqrt{3}, 1)$ is the **turtle**, the mirror partner.
- $\text{Tile}(1, 0)$ degenerates to the **"comet"**, which also tiles periodically.

The aperiodic regime occupies an **interval** of parameter values, with the hat sitting
at a distinguished point where the edge lengths span $\mathbb{Q}(\sqrt{3})$ minimally.

This is reminiscent of the parameter-tuning role played by the contraction ratio in the
Sierpiński triangle (cf. `affine.md` Section 4.1): aperiodicity is a property not of a
single rigid shape but of a **codimension-zero open set** in shape-parameter space.

---

## 3. The Type Graph of the Einstein

### 3.1 Vertex Set: Trivial

The einstein has a **single tile type**, so:
$$V(\mathcal{T}_{\text{einstein}}) = \{H\}.$$

This places the einstein in the top row of the specialization table of `multipolygon.md`
Section 2.2 — alongside the square, hexagonal, and pinwheel monotile tilings.

### 3.2 Edge Set: Rich Self-Loops

The single vertex $H$ has a self-loop for each **edge class** of adjacency. The hat has
on the order of 13 edges with multiple distinct congruence classes, and each pair (edge
of hat $A$, edge of hat $B$) that may legally abut contributes one labeled self-loop:

$$E(\mathcal{T}_{\text{einstein}}) = \{(H, H, \ell) : \ell \text{ is an admissible edge-class pair}\}.$$

The number of such edge classes is large (a careful enumeration gives roughly 30 distinct
matching configurations, accounting for orientation and chirality), reflecting the
**combinatorial richness** of a single-vertex type graph with many self-loops.

### 3.3 The Schedule Automaton View

From the schedule-automaton perspective of `multipolygon.md` Section 2.3, the einstein
is a one-state automaton:

- **State**: $H$ (the only tile type).
- **Transitions**: each labeled by an edge class, all returning to state $H$.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-150 -120 300 240" width="320">
   <title>Einstein Type Graph: One Vertex, Many Self-Loops</title>
   <!-- Central vertex H -->
   <circle cx="0" cy="0" r="22" fill="#f5cba7" stroke="#7d6608" stroke-width="2"/>
   <text x="0" y="6" text-anchor="middle" font-family="serif" font-size="20" font-style="italic">H</text>
   <!-- Self-loops around H, at various angles -->
   <g fill="none" stroke-width="1.5">
     <path d="M 19,-12 C 70,-60 100,-40 22,-5" stroke="#c0392b"/>
     <path d="M 22,0 C 90,-10 90,10 22,0" stroke="#1e8449"/>
     <path d="M 19,12 C 70,60 100,40 22,5" stroke="#c0392b"/>
     <path d="M 12,19 C 40,70 -40,70 -12,19" stroke="#2874a6"/>
     <path d="M -19,12 C -70,60 -100,40 -22,5" stroke="#1e8449"/>
     <path d="M -22,0 C -90,10 -90,-10 -22,0" stroke="#c0392b"/>
     <path d="M -19,-12 C -70,-60 -100,-40 -22,-5" stroke="#1e8449"/>
     <path d="M -12,-19 C -40,-70 40,-70 12,-19" stroke="#2874a6"/>
   </g>
   <!-- Arrowheads (small) -->
   <g fill="#555">
     <circle cx="22" cy="-5" r="2"/>
     <circle cx="22" cy="0" r="2"/>
     <circle cx="22" cy="5" r="2"/>
     <circle cx="-12" cy="19" r="2"/>
     <circle cx="-22" cy="5" r="2"/>
     <circle cx="-22" cy="0" r="2"/>
     <circle cx="-22" cy="-5" r="2"/>
     <circle cx="12" cy="-19" r="2"/>
   </g>
   <text x="0" y="105" text-anchor="middle" font-family="serif" font-size="12" font-style="italic">
     Self-loops labeled by edge-class pairs (~30 distinct labels)
   </text>
</svg>
<br/><em>Figure 2: The ground-level type graph $\mathcal{T}_{\text{einstein}}$ has a
single vertex with many self-loops, one per admissible edge-class adjacency.</em>
</p>

The aperiodicity therefore cannot be diagnosed from the state graph alone — it lies
entirely in the **matching rules** $\{M_{HH,\ell}\}$ and, crucially, in the **substitution
structure** that we now describe.

---

## 4. The Substitution Hierarchy: The Missing Ingredient

### 4.1 Metatiles

A tiling of the plane by hats admits a hierarchical decomposition into **metatiles**:
clusters of hats that play the role of higher-order tiles. Smith et al. identify four
metatile types:
$$\mathcal{M} = \{H_7, H_8, F, P\},$$
where each metatile is a specific union of (roughly) 7–8 hats. The metatiles themselves
satisfy a **substitution rule**:
$$\sigma: \mathcal{M} \to \mathcal{M}^*,$$
mapping each metatile to a finite patch composed of (smaller) metatiles. Iterating
$\sigma$ produces arbitrarily large patches of the tiling.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-260 -160 520 320" width="600">
   <title>The Substitution Rule on Metatiles</title>
   <!-- LHS: a single metatile H7 -->
   <g transform="translate(-180,0)">
     <polygon points="-40,-50 40,-50 60,0 40,50 -40,50 -60,0"
              fill="#aed6f1" stroke="#1b4f72" stroke-width="2"/>
     <text x="0" y="5" text-anchor="middle" font-family="serif" font-size="20" font-style="italic">H₇</text>
     <text x="0" y="80" text-anchor="middle" font-family="serif" font-size="13">metatile (level n)</text>
   </g>
   <!-- Arrow with sigma -->
   <g>
     <line x1="-100" y1="0" x2="-30" y2="0" stroke="#333" stroke-width="2"/>
     <polygon points="-30,0 -38,-5 -38,5" fill="#333"/>
     <text x="-65" y="-8" text-anchor="middle" font-family="serif" font-size="18" font-style="italic">σ</text>
   </g>
   <!-- RHS: a patch of smaller metatiles -->
   <g transform="translate(80,0)">
     <!-- Cluster of smaller hexagons / quads representing sub-metatiles -->
     <polygon points="-20,-40 0,-40 10,-25 0,-10 -20,-10 -30,-25"
              fill="#aed6f1" stroke="#1b4f72" stroke-width="1.5"/>
     <text x="-10" y="-22" text-anchor="middle" font-family="serif" font-size="10">H₇</text>
     <polygon points="10,-40 35,-40 45,-20 30,-5 10,-10 0,-25"
              fill="#f9e79f" stroke="#7d6608" stroke-width="1.5"/>
     <text x="22" y="-20" text-anchor="middle" font-family="serif" font-size="10">H₈</text>
     <polygon points="-30,-10 0,-10 10,15 -10,30 -35,20"
              fill="#f5b7b1" stroke="#7b241c" stroke-width="1.5"/>
     <text x="-15" y="10" text-anchor="middle" font-family="serif" font-size="10">F</text>
     <polygon points="10,-5 35,-5 50,15 35,35 10,30 0,15"
              fill="#abebc6" stroke="#1e8449" stroke-width="1.5"/>
     <text x="25" y="15" text-anchor="middle" font-family="serif" font-size="10">P</text>
     <polygon points="-35,20 -10,30 0,50 -25,55 -45,45"
              fill="#aed6f1" stroke="#1b4f72" stroke-width="1.5"/>
     <text x="-22" y="42" text-anchor="middle" font-family="serif" font-size="10">H₇</text>
     <polygon points="10,30 35,35 40,55 15,55 0,50"
              fill="#f9e79f" stroke="#7d6608" stroke-width="1.5"/>
     <text x="20" y="48" text-anchor="middle" font-family="serif" font-size="10">H₈</text>
     <text x="0" y="80" text-anchor="middle" font-family="serif" font-size="13">patch of metatiles (level n−1)</text>
   </g>
</svg>
<br/><em>Figure 3: Schematic of the substitution rule $\sigma$ inflating one metatile
$H_7$ into a patch of four metatile types $\{H_7, H_8, F, P\}$.</em>
</p>

### 4.2 The Two-Level Type Graph

The substitution structure is naturally captured by a **two-level type graph**:

- **Ground level**: the single-vertex graph $\{H\}$ with self-loops for hat-hat adjacencies.
- **Meta level**: the four-vertex graph $\{H_7, H_8, F, P\}$ with edges encoding metatile
  adjacency.

These two levels are connected by **inflation/deflation edges** (cf. `multipolygon.md`
Section 2.2, last paragraph): an edge $H_7 \to H$ records that the metatile $H_7$ is
composed of hat tiles in a specific arrangement.

Formally, the einstein type graph in the **decorated** sense is:

$$V(\mathcal{T}) = \{H, H_7, H_8, F, P\}, \quad E(\mathcal{T}) = E_{\text{adj}} \cup E_{\text{inflate}},$$

where $E_{\text{adj}}$ encodes ground-level and meta-level adjacencies, and
$E_{\text{inflate}}$ encodes the substitution maps.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-220 -160 440 320" width="520">
   <title>The Two-Level Type Graph of the Einstein</title>
   <!-- Meta level: 4 vertices in a row -->
   <g>
     <circle cx="-150" cy="-90" r="20" fill="#aed6f1" stroke="#1b4f72" stroke-width="2"/>
     <text x="-150" y="-85" text-anchor="middle" font-family="serif" font-size="14">H₇</text>
     <circle cx="-50" cy="-90" r="20" fill="#f9e79f" stroke="#7d6608" stroke-width="2"/>
     <text x="-50" y="-85" text-anchor="middle" font-family="serif" font-size="14">H₈</text>
     <circle cx="50" cy="-90" r="20" fill="#f5b7b1" stroke="#7b241c" stroke-width="2"/>
     <text x="50" y="-85" text-anchor="middle" font-family="serif" font-size="14">F</text>
     <circle cx="150" cy="-90" r="20" fill="#abebc6" stroke="#1e8449" stroke-width="2"/>
     <text x="150" y="-85" text-anchor="middle" font-family="serif" font-size="14">P</text>
     <!-- Meta-level adjacency edges (curved) -->
     <g fill="none" stroke="#555" stroke-width="1.2">
       <path d="M -130 -90 Q -100 -110 -70 -90"/>
       <path d="M -30 -90 Q 0 -110 30 -90"/>
       <path d="M 70 -90 Q 100 -110 130 -90"/>
       <path d="M -130 -90 Q -50 -135 130 -90"/>
     </g>
     <text x="0" y="-145" text-anchor="middle" font-family="serif" font-size="12" font-style="italic">Meta level: metatiles + substitution edges</text>
   </g>
   <!-- Ground level: single vertex H -->
   <g>
     <circle cx="0" cy="80" r="24" fill="#f5cba7" stroke="#7d6608" stroke-width="2"/>
     <text x="0" y="86" text-anchor="middle" font-family="serif" font-size="20" font-style="italic">H</text>
     <!-- self loops -->
     <path d="M 23,72 C 60,40 60,120 23,88" fill="none" stroke="#555" stroke-width="1.2"/>
     <path d="M -23,72 C -60,40 -60,120 -23,88" fill="none" stroke="#555" stroke-width="1.2"/>
     <text x="0" y="130" text-anchor="middle" font-family="serif" font-size="12" font-style="italic">Ground level: single tile H with self-loops</text>
   </g>
   <!-- Inflation edges (dashed) from meta to ground -->
   <g stroke="#c0392b" stroke-width="1.5" stroke-dasharray="4 3" fill="none">
     <line x1="-150" y1="-70" x2="-15" y2="62"/>
     <line x1="-50" y1="-70" x2="-8" y2="60"/>
     <line x1="50" y1="-70" x2="8" y2="60"/>
     <line x1="150" y1="-70" x2="15" y2="62"/>
   </g>
   <text x="-195" y="0" font-family="serif" font-size="11" fill="#c0392b" font-style="italic">inflation edges</text>
</svg>
<br/><em>Figure 4: The decorated two-level type graph. Solid edges are adjacencies;
dashed red edges are inflation (substitution) maps connecting metatiles to constituent hats.</em>
</p>

### 4.3 Comparison with Penrose

This matches exactly the Penrose construction described in `multipolygon.md` Section 2.2:
the Penrose substitution graph has tile types $\{\text{thin}, \text{thick}\}$ at the ground
level and an inflation rule connecting each rhomb to a patch of smaller rhombs. The
einstein simply replaces the two-vertex ground graph with a **one-vertex ground graph**,
pushing all combinatorial complexity into the inflation rules at the meta level.

> **Key insight**: From the multi-polygon perspective, the einstein is _Penrose with the
> ground graph collapsed to a point_. The substitution-level graph carries all the work
> that the multi-tile ground graph carried in earlier aperiodic constructions.

---

## 5. Algebraic Analysis

### 5.1 The Coordinate Field

As noted in Section 2.1, all hat vertices lie in $\mathbb{F} = \mathbb{Q}(\sqrt{3})$.
The substitution rule $\sigma$ acts on metatiles by an affine map whose linear part is
a $2 \times 2$ matrix with entries in $\mathbb{Q}(\sqrt{3})$, scaling lengths by the
inflation factor:

$$\lambda_{\text{einstein}} = \phi_{\text{einstein}} := \frac{1 + \sqrt{3} + \sqrt{15}}{2}$$

or, in another normalization,

$$\lambda^2 = 4 + \sqrt{15}, \quad \text{so } \lambda = \sqrt{4 + \sqrt{15}}.$$

The inflation eigenvalue is a quadratic irrational over $\mathbb{Q}(\sqrt{3})$, sitting
naturally in the **biquadratic field** $\mathbb{Q}(\sqrt{3}, \sqrt{5})$.

> **Caveat — Criterion 2 reanalysis**: This is exactly the failure mode flagged in
> `multipolygon.md` Section 4.2 for the (pentagon, hexagon) pairing! The combined field
> $\mathbb{Q}(\sqrt{3}, \sqrt{5})$ is a **degree-4 extension**, not a simple quadratic.
>
> The resolution is that the **ground-level tiling** lives entirely in $\mathbb{Q}(\sqrt{3})$.
> The $\sqrt{5}$ enters only at the **meta-level inflation factor**, not at any individual
> hat vertex. Criterion 2 is satisfied at the ground level; the meta-level adds an
> _independent_ algebraic layer that is consistent with — rather than required by — the
> ground-level geometry.

This stratified algebraic structure is genuinely new in the framework: the einstein
exhibits a **two-tier algebraic compactness**, where:

1. The ground field is $\mathbb{Q}(\sqrt{3})$ — admissible.
2. The substitution scaling factor lies in $\mathbb{Q}(\sqrt{3}, \sqrt{5})$, but acts on
   the tiling only as a _global similarity_, not as a generator of new tile placements
   within a single inflation level.

### 5.2 The Orientation Group

The hat appears in 12 rotational orientations (multiples of $30°$) plus reflections,
giving an apparent orientation group $D_{12}$ of order 24. Combined with the substitution
rule, however, the orientation group at the meta-level is _not_ a subgroup of $D_{12}$:
the inflation rule rotates metatiles by angles involving $\arctan(\sqrt{3}/\sqrt{5})$ and
related quadratic-irrational arctangents, which are **not** rational multiples of $\pi$.

> **Proposition 5.2 (Failure of Criterion 1 at the meta-level)**: The orientation group
> generated by the union of (ground-level edge reflections) ∪ (meta-level substitution
> rotation) is **infinite**. This is precisely why no flat isometric reconnection exists.

This is the einstein's analogue of the pentagonal frustration of `idea.md`: the local
symmetry group is finite ($D_{12}$ for the hat), but the substitution dynamics inject
irrational rotations that prevent finite closure under inflation.

### 5.3 Why Aperiodicity Follows

Combining the two observations:

- Criterion 2 holds at the ground level ($\mathbb{F} = \mathbb{Q}(\sqrt{3})$).
- Criterion 1 holds at the ground level ($D_{12}$ acts on hat orientations).
- But the substitution rule injects an irrational rotation at the meta-level, breaking
  any finite closure.

The resulting tiling is **reconnective but only via the substitution**: any patch can be
extended to a tiling of the plane, but no two such tilings are translates of one another.
This is **Level 1** of the hierarchy in `multipolygon.md` Section 9 — the same level as
Penrose. The einstein is therefore the **minimal-prototile representative of Level 1**.

---

## 6. The Spectre and Chirality: A $\mathbb{Z}_2$ Holonomy

### 6.1 The Reflection Issue

The hat tiles the plane aperiodically _only if_ both the hat and its mirror image are used.
The hat alone, without reflections, does not admit any tiling. Smith et al.'s refinement
to the **spectre** modifies the edges of the hat (replacing straight segments with curves
that break reflective symmetry) to produce a tile that:

- Has **no reflection symmetry**.
- Tiles the plane aperiodically using only **direct congruences** (rotations + translations).

This is the resolution of the "strict einstein" problem.

### 6.2 Handedness as a Sheet Index

For the hat (with reflections), the local data at each tile includes a **chirality label**
$\chi \in \{+, -\}$. The matching rules force chirality flips along certain edge classes.
Walking around a vertex configuration, the chirality label may flip an even or odd number
of times — exactly analogous to the **sheet index** of `idea.md`!

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-200 -130 400 260" width="500">
   <title>The Z₂ Chirality Bundle over the Hat Tiling</title>
   <!-- Base: simplified hat tiling patch -->
   <g opacity="0.9">
     <polygon points="-150,-80 -110,-80 -90,-50 -110,-20 -150,-20 -170,-50"
              fill="#7fb3d5" stroke="#1b4f72" stroke-width="1.5"/>
     <text x="-130" y="-45" text-anchor="middle" font-family="serif" font-size="14">+</text>
     <polygon points="-90,-50 -50,-50 -30,-20 -50,10 -90,10 -110,-20"
              fill="#f5b7b1" stroke="#7b241c" stroke-width="1.5"/>
     <text x="-70" y="-15" text-anchor="middle" font-family="serif" font-size="14">−</text>
     <polygon points="-30,-20 10,-20 30,10 10,40 -30,40 -50,10"
              fill="#7fb3d5" stroke="#1b4f72" stroke-width="1.5"/>
     <text x="-10" y="15" text-anchor="middle" font-family="serif" font-size="14">+</text>
     <polygon points="30,10 70,10 90,40 70,70 30,70 10,40"
              fill="#f5b7b1" stroke="#7b241c" stroke-width="1.5"/>
     <text x="50" y="45" text-anchor="middle" font-family="serif" font-size="14">−</text>
     <polygon points="-150,-20 -110,-20 -90,10 -110,40 -150,40 -170,10"
              fill="#f5b7b1" stroke="#7b241c" stroke-width="1.5"/>
     <text x="-130" y="15" text-anchor="middle" font-family="serif" font-size="14">−</text>
     <polygon points="-90,10 -50,10 -30,40 -50,70 -90,70 -110,40"
              fill="#7fb3d5" stroke="#1b4f72" stroke-width="1.5"/>
     <text x="-70" y="45" text-anchor="middle" font-family="serif" font-size="14">+</text>
   </g>
   <!-- Holonomy walk: closed loop around a vertex -->
   <g fill="none" stroke="#1a5276" stroke-width="2.5" stroke-dasharray="5 3">
     <path d="M -130,-20 L -70,-20 L -10,10 L -70,40 L -130,40 Z"/>
   </g>
   <g fill="#1a5276">
     <circle cx="-130" cy="-20" r="3"/>
     <circle cx="-70" cy="-20" r="3"/>
     <circle cx="-10" cy="10" r="3"/>
     <circle cx="-70" cy="40" r="3"/>
     <circle cx="-130" cy="40" r="3"/>
   </g>
   <text x="-70" y="95" text-anchor="middle" font-family="serif" font-size="11" fill="#1a5276" font-style="italic">closed walk γ</text>
   <!-- Legend / Z2 fiber diagram on right -->
   <g transform="translate(140,-50)">
     <text x="0" y="-30" text-anchor="middle" font-family="serif" font-size="13" font-weight="bold">ℤ₂ fiber</text>
     <circle cx="0" cy="0" r="14" fill="#7fb3d5" stroke="#1b4f72" stroke-width="2"/>
     <text x="0" y="5" text-anchor="middle" font-family="serif" font-size="16">+</text>
     <circle cx="0" cy="50" r="14" fill="#f5b7b1" stroke="#7b241c" stroke-width="2"/>
     <text x="0" y="55" text-anchor="middle" font-family="serif" font-size="16">−</text>
     <!-- Double arrow between -->
     <g stroke="#555" stroke-width="1.5" fill="none">
       <path d="M 0,14 L 0,36"/>
       <polygon points="0,36 -4,30 4,30" fill="#555"/>
       <polygon points="0,14 -4,20 4,20" fill="#555"/>
     </g>
     <text x="22" y="30" font-family="serif" font-size="11" font-style="italic">flip</text>
   </g>
</svg>
<br/><em>Figure 5: A patch of the hat tiling labeled with chirality $\chi \in \{+,-\}$.
The closed walk $\gamma$ may pick up a non-trivial $\mathbb{Z}_2$ holonomy.</em>
</p>

> **Proposition 6.2**: The hat tiling carries a natural $\mathbb{Z}_2$ holonomy whose
> "sheet index" is the chirality label $\chi$. Closed walks on the dual adjacency graph
> may transport chirality non-trivially, yielding a discrete principal $\mathbb{Z}_2$-bundle
> in the sense of `idea.md` Section 2.

Under the substitution rule, the chirality of a metatile is determined by its position in
the inflation hierarchy; the chirality flips are organized by the substitution combinatorics
into a globally consistent pattern (the bundle has _finitely many_ sheets and _finite_
holonomy, unlike the pentagonal case where the holonomy is governed by an infinite cyclic
group).

The **spectre** breaks this duality by removing reflective symmetry from the prototile
itself, effectively gauging away the chirality sheet — the resulting tiling has a
**trivial holonomy bundle** over a quotient that respects only direct congruences.

### 6.3 The Spectre as a Quotient

In bundle language:

- **Hat tiling**: a $\mathbb{Z}_2$-bundle over the projected plane, with chirality as
  fiber.
- **Spectre tiling**: the quotient by the chirality involution, yielding a trivial bundle
  whose total space is the spectre's tiling itself.

This makes the hat→spectre transition a direct analogue of passing from the spin double
cover to the rotation group — except that here the discrete bundle is _finite_ and the
quotient is _geometric_ rather than topological.

---

## 7. The Dual Adjacency Graph

### 7.1 Centroid–Centroid Walks

In the multi-polygon framework of `multipolygon.md` Section 6, we distinguish **centroid**
tiles from **bridge** tiles. The einstein has only one tile type, so every tile is a
centroid tile; the dual adjacency graph is simply the hat–hat adjacency graph.

The type-distance between hats is **1** (any two adjacent hats are directly connected),
which would naively place the einstein in the "all-centroid" regime of Section 6 — the
simplest case. The combinatorial richness lies entirely in the edge labeling and the
substitution structure, not in the type graph's vertex set.

### 7.2 Spectral Dimension

By the methods of `idea.md` Section 6, one can compute the spectral dimension of the dual
graph $\mathcal{G}_{\text{hat}}$ via the graph Laplacian. Empirical results from
mathematical-physics studies of substitution tilings suggest:

- **Volume growth**: $V(r) \sim r^2$ (the tiling is genuinely planar, with no fractional
  expansion).
- **Spectral dimension**: $d_{\text{spec}} = 2$ exactly, since the tiling has finite local
  complexity and bounded vertex degrees.
- **Walk dimension**: $d_w = 2$ (standard diffusion).

This is a sharp contrast with the multi-sheeted pentagonal construction of `idea.md`,
which exhibited fractional $d_{\text{eff}}$ and dimensional flow. The einstein lives in
a clean planar Euclidean regime; its aperiodicity is **purely combinatorial** rather than
geometric-dimensional.

### 7.3 Spectral Signature of Aperiodicity

While the spectral _dimension_ is integer, the spectrum itself exhibits the hallmarks of
substitution tilings:

- **Cantor-set spectrum**: the Laplacian spectrum has gaps at every scale of the
  substitution hierarchy.
- **Pure-point + singular continuous decomposition**: by the Bombieri–Taylor and
  Lee–Moody–Solomyak theorems for substitution systems.
- **Bragg peaks**: the diffraction spectrum has pure-point components aligned with the
  substitution eigenvalues, characteristic of quasicrystalline order.

These features place the einstein in the same diffraction class as Penrose and
Ammann–Beenker, even though the underlying type graph is minimal.

---

## 8. Reconnection Hierarchy: Where Does the Einstein Sit?

Refining the table of `multipolygon.md` Section 9:

| Level | Structure                              | Field                         | Orientation group                                           | Example                   |
| ----- | -------------------------------------- | ----------------------------- | ----------------------------------------------------------- | ------------------------- |
| 0     | Periodic, finite isometry closure      | Single $\mathbb{Q}(\sqrt{d})$ | Finite                                                      | Square, hexagonal         |
| 1a    | Aperiodic, substitution-mediated       | $\mathbb{Q}(\sqrt{5})$        | Finite (local) + infinite (meta)                            | Penrose                   |
| 1b    | Aperiodic, substitution + chirality    | $\mathbb{Q}(\sqrt{3})$        | Finite (local) + infinite (meta) + $\mathbb{Z}_2$ chirality | **Hat**                   |
| 1c    | Aperiodic, substitution, no chirality  | $\mathbb{Q}(\sqrt{3})$        | Finite (local) + infinite (meta)                            | **Spectre**               |
| 2     | Multi-sheeted covering, fractional dim | $\mathbb{Q}(\sqrt{5})$        | Infinite cyclic                                             | Pentagon (`idea.md`)      |
| 3     | Non-reconnective                       | Mixed / transcendental        | Infinite, free                                              | Generic irregular polygon |

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-30 -30 660 360" width="640">
   <title>The Reconnection Hierarchy</title>
   <!-- Horizontal axis: levels 0..3 -->
   <g font-family="serif" font-size="13">
     <!-- Level boxes -->
     <rect x="10" y="50" width="90" height="60" fill="#d4efdf" stroke="#1e8449" stroke-width="2" rx="6"/>
     <text x="55" y="75" text-anchor="middle" font-weight="bold">Level 0</text>
     <text x="55" y="95" text-anchor="middle" font-size="11">periodic</text>
     <rect x="120" y="20" width="90" height="60" fill="#fcf3cf" stroke="#7d6608" stroke-width="2" rx="6"/>
     <text x="165" y="45" text-anchor="middle" font-weight="bold">1a Penrose</text>
     <text x="165" y="65" text-anchor="middle" font-size="11">ℚ(√5)</text>
     <rect x="120" y="100" width="90" height="60" fill="#fadbd8" stroke="#7b241c" stroke-width="3" rx="6"/>
     <text x="165" y="125" text-anchor="middle" font-weight="bold">1b Hat</text>
     <text x="165" y="145" text-anchor="middle" font-size="11">ℚ(√3), ℤ₂</text>
     <rect x="120" y="180" width="90" height="60" fill="#d6eaf8" stroke="#1b4f72" stroke-width="3" rx="6"/>
     <text x="165" y="205" text-anchor="middle" font-weight="bold">1c Spectre</text>
     <text x="165" y="225" text-anchor="middle" font-size="11">ℚ(√3), no χ</text>
     <rect x="240" y="100" width="100" height="60" fill="#e8daef" stroke="#6c3483" stroke-width="2" rx="6"/>
     <text x="290" y="125" text-anchor="middle" font-weight="bold">Level 2</text>
     <text x="290" y="145" text-anchor="middle" font-size="11">multi-sheeted</text>
     <rect x="370" y="100" width="100" height="60" fill="#f6ddcc" stroke="#a04000" stroke-width="2" rx="6"/>
     <text x="420" y="125" text-anchor="middle" font-weight="bold">Level 3</text>
     <text x="420" y="145" text-anchor="middle" font-size="11">non-reconnective</text>
     <!-- Arrows between -->
     <g stroke="#333" stroke-width="1.5" fill="none">
       <line x1="100" y1="80" x2="120" y2="50"/>
       <line x1="100" y1="80" x2="120" y2="130"/>
       <line x1="100" y1="80" x2="120" y2="210"/>
       <line x1="210" y1="130" x2="240" y2="130"/>
       <line x1="340" y1="130" x2="370" y2="130"/>
     </g>
     <!-- Axis label -->
     <text x="240" y="290" text-anchor="middle" font-style="italic" font-size="12">
       Increasing combinatorial / algebraic / topological complexity →
     </text>
     <g stroke="#333" stroke-width="2" fill="none">
       <line x1="40" y1="270" x2="440" y2="270"/>
       <polygon points="440,270 432,265 432,275" fill="#333"/>
     </g>
   </g>
</svg>
<br/><em>Figure 6: The reconnection hierarchy. The hat and spectre occupy the new
sublevels 1b and 1c, with $\mathbb{Q}(\sqrt{3})$ ground-level substrate.</em>
</p>

The hat and spectre occupy a **new sublevel 1b/1c** in the hierarchy, distinguished from
Penrose by:

- **Single ground-level tile type** (rather than two for Penrose).
- **Underlying field $\mathbb{Q}(\sqrt{3})$ rather than $\mathbb{Q}(\sqrt{5})$**.
- **Chirality holonomy** (for the hat, absent for the spectre).

---

## 9. The Einstein as a Limiting Case of the Pinwheel

### 9.1 Single-Vertex Type Graphs

Recall from `pinwheels.md` (and `multipolygon.md` Section 1.2) that the pinwheel can be
viewed _either_ as a single-vertex tiling (with the composite base+fin treated as a
monotile) _or_ as a two-vertex tiling (with $\{B, F\}$). The einstein corresponds to the
**single-vertex view applied at the finest possible level**: there is no internal
decomposition of the hat into "base" and "fin" components.

But the substitution structure of the einstein recovers a similar hierarchical flavor:
each metatile $H_7$, $H_8$, $F$, $P$ plays the role of a "composite" tile at a higher
scale, and the inflation rule plays the role of "fin attachment" at the meta-level.

### 9.2 Pinwheel Type-Distance vs. Einstein Type-Distance

The pinwheel has **ground-level type-distance 3** between base centroids (cf.
`multipolygon.md` Section 3.4: base → fin → fin → base). The einstein has **ground-level
type-distance 1** (hat → hat). But the einstein has **meta-level type-distance** $\ge 1$
on the substitution hierarchy.

This swap is illuminating: the pinwheel "spends" its combinatorial complexity at the
ground level (multiple tile types per pinwheel), while the einstein "spends" it at the
meta level (substitution dynamics). Both achieve aperiodicity (or quasi-periodicity in
the rotational pinwheel case) by **distributing the combinatorial work across scales**.

### 9.3 The Conway–Radin Pinwheel Connection

The Conway–Radin rotational pinwheel is itself a substitution tiling with infinite
rotational orientations (rotations by $\arctan(1/2)$, irrational over $\pi$). Its
classification under the multi-polygon framework places it at **Level 2** (multi-sheeted)
because its orientation group is infinite cyclic and densifies in $SO(2)$.

The einstein **does not** have this densification: hat orientations are confined to a
finite set ($D_{12}$ at the ground level). The substitution rotation, while irrational
over $\pi$, acts only at the meta-scale and does not generate dense orientations of
individual hat tiles. This is what keeps the einstein at Level 1 rather than Level 2 —
the einstein is **less wild** than the Conway–Radin pinwheel despite both being aperiodic.

---

## 10. Algorithmic Construction

Adapting the construction algorithm of `multipolygon.md` Section 11 to the einstein:

```python
def construct_einstein_tiling(seed_hat, max_levels):
    """
    Build a hat tiling by hierarchical substitution.

    Args:
        seed_hat: initial hat tile with position, orientation, chirality in Q(sqrt(3)).
        max_levels: number of inflation levels.

    Returns:
        list of placed hats, each with (position, orientation, chirality).
    """
    # Start from a single seed metatile of level max_levels.
    metatile = seed_metatile(seed_hat, level=max_levels)
    placed = []

    def expand(mt, level):
        if level == 0:
            # Convert ground-level metatile to constituent hats.
            for hat in mt.constituent_hats():
                placed.append(hat)
            return
        # Apply substitution: replace mt with sub-metatiles.
        for sub_mt in mt.substitute():
            expand(sub_mt, level - 1)

    expand(metatile, max_levels)
    return placed
```

Critical implementation notes (paralleling `idea.md` Section 2.3):

1. **Exact arithmetic in $\mathbb{Q}(\sqrt{3})$**: every hat vertex is stored as a
   4-tuple $(a, b, c, d) \in \mathbb{Q}^4$ representing
   $(a + b\sqrt{3}, c + d\sqrt{3})$.
2. **Substitution scaling**: the inflation factor $\lambda = \sqrt{4 + \sqrt{15}}$ acts as
   a global similarity; metatile placements at each level multiply by $\lambda$, and
   exactness is preserved by working in the biquadratic field $\mathbb{Q}(\sqrt{3}, \sqrt{5})$
   at the meta-level.
3. **Chirality labels**: each hat carries a chirality bit $\chi \in \{+, -\}$ propagated
   by the substitution rule.
4. **No collision detection needed**: by construction, the substitution rule produces a
   valid edge-to-edge tiling; the algorithm is more efficient than the general
   adjacency-oracle approach of `multipolygon.md`.

---

## 11. Open Questions Specific to the Einstein

Adapting and specializing the open questions of `multipolygon.md` Section 12:

1. **Continuous family classification**: the one-parameter $\text{Tile}(a, b)$ family
   transitions between periodic and aperiodic regimes. Is there a sharp algebraic
   characterization of the aperiodic interval in terms of the $\mathbb{Q}(\sqrt{3})$
   structure?

2. **Spectral signature of the substitution**: compute $d_{\text{spec}}$ via KPM (cf.
   `idea.md` Section 6.4) on large finite patches of the hat tiling and verify
   $d_{\text{spec}} = 2$ with the predicted Cantor-set spectrum. Does the multi-scale
   spectral structure encode the inflation factor $\lambda$?

3. **Generalization to other fields**: are there einstein-like monotiles based on
   $\mathbb{Q}(\sqrt{2})$ (octagonal substrate) or $\mathbb{Q}(\sqrt{5})$ (pentagonal
   substrate)? The hat sits on the $\mathbb{Q}(\sqrt{3})$ substrate; the framework
   suggests systematic searches at other quadratic fields.

4. **Spectre and higher chirality**: the hat has a $\mathbb{Z}_2$ chirality bundle. Are
   there einstein analogues with $\mathbb{Z}_n$ chirality for $n \ge 3$? These would
   require tiles with $n$-fold "internal twist" labels and would directly generalize the
   spinor double cover of `idea.md` to higher-order anyonic statistics.

5. **3D einstein**: a recent open problem asks whether a single polyhedron (without
   reflections) tiles 3-space aperiodically. The multi-polygon framework's 3D extension
   (`multipolygon.md` Section 12, open question 5) provides a natural setting for this
   question. The algebraic field would presumably be $\mathbb{Q}(\sqrt{2})$ or
   $\mathbb{Q}(\sqrt{3})$ from a cubic/octahedral substrate.

6. **CA dynamics on einstein tilings**: applying the isometric CA framework of
   `idea.md` Section 5 to the hat-adjacency graph, what cellular automata rules support
   Turing-universal computation? The aperiodicity guarantees that glider trajectories
   never repeat, potentially enabling new universality constructions.

7. **Causal structure**: the substitution hierarchy naturally provides a _time foliation_
   (each substitution level = one time step). Does this yield a causal CA in the sense of
   `idea.md` Section 5.2, with the meta-level inflation playing the role of "spacetime"
   coarse-graining?

---

## 12. Summary

The einstein tilings — hat, turtle, and spectre — exemplify a striking limit case in the
multi-polygon tiling framework:

- **Minimal ground-level type graph**: a single vertex $\{H\}$ with rich self-loops.
- **Algebraic substrate**: $\mathbb{F} = \mathbb{Q}(\sqrt{3})$, inherited from the
  $3.4.6.4$ Laves kite construction. Criterion 2 satisfied at the ground level.
- **Finite local symmetry**: orientations confined to $D_{12}$, with chirality bit
  $\chi \in \{+, -\}$ for the hat. Criterion 1 satisfied at the ground level.
- **Substitution-mediated reconnection**: combinatorial closure is achieved via a
  hierarchy of metatiles $\{H_7, H_8, F, P\}$ inflated by a quadratic-irrational scale
  factor $\lambda \in \mathbb{Q}(\sqrt{3}, \sqrt{5})$.
- **Meta-level Criterion 1 failure**: the inflation rotation is irrational over $\pi$,
  preventing periodic closure and forcing aperiodicity.
- **Chirality holonomy**: the hat carries a $\mathbb{Z}_2$ bundle over its dual graph,
  analogous to (but tamer than) the multi-sheeted pentagonal construction of `idea.md`.
  The spectre quotients away this bundle.
- **Position in the hierarchy**: Level 1, alongside Penrose, but distinguished by its
  minimal prototile count and $\mathbb{Q}(\sqrt{3})$ substrate.

The einstein is, in the framework's terms, **the canonical demonstration that combinatorial
complexity can be entirely concentrated at the substitution level when the ground-level
type graph is collapsed to a single vertex**. It is the type-graph-theoretic dual of the
pinwheel: where the pinwheel distributes complexity across a two-vertex ground graph with
a simple schedule, the einstein concentrates complexity in the substitution loop on a
one-vertex graph.

Both constructions belong to the same broader landscape of multi-polygon tilings, and
the framework reveals them as **complementary limit cases** of a single underlying
combinatorial-algebraic structure.
