# Pinwheel Polygons: Cyclic Composition of a Base with a Free-Edge Appendage

## Overview

The frameworks of `idea.md`, `affine.md`, and `polyhedra.md` classify when _all_ edges of
a polygon (or all faces of a polyhedron) are used as generators of an expansion family.
A natural and powerful refinement is to ask: **what happens when a polygon is built by
cyclic rotation of a simple base together with a small "fin" or appendage, leaving the
appendage's outer edge as a free (inactive) boundary?**

The canonical example — and the one that gives the construction its name — is the
**pinwheel polygon**: take a rectangle, attach a right triangle to one of its edges so
that one leg of the triangle lies along (part of) the rectangle's edge and the
hypotenuse points outward, then rotate the resulting shape by $90°$ around its center
four times. The four copies tile around a central square, and the four outward-pointing
hypotenuses look exactly like the blades of a child's pinwheel.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-60 -60 320 320" width="320" height="320">
   <title>Rectangular pinwheel P(a,b) with C4 symmetry</title>
   <!-- central square -->
   <rect x="0" y="0" width="200" height="200" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.5"/>
   <!-- four fins (right triangles), curling counterclockwise -->
   <!-- top fin: leg a along top-left of top edge, leg b upward -->
   <polygon points="0,0 80,0 0,-60" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
   <!-- right fin: rotated 90 CW about (100,100) of top fin -->
   <polygon points="200,0 200,80 260,0" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
   <!-- bottom fin -->
   <polygon points="200,200 120,200 200,260" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
   <!-- left fin -->
   <polygon points="0,200 0,120 -60,200" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
   <!-- center marker -->
   <circle cx="100" cy="100" r="3" fill="#3060a0"/>
   <!-- labels -->
   <text x="100" y="115" text-anchor="middle" font-family="serif" font-size="14" fill="#3060a0">B</text>
   <text x="40" y="-15" text-anchor="middle" font-family="serif" font-size="11" fill="#a05000">F</text>
   <text x="40" y="-30" text-anchor="middle" font-family="serif" font-size="10" fill="#444">a</text>
   <text x="-12" y="-25" text-anchor="middle" font-family="serif" font-size="10" fill="#444">b</text>
</svg>
</p>
<p align="center"><em>Figure 1. The canonical rectangular pinwheel P(a,b): a unit square base B with four right-triangle fins arranged with C₄ symmetry.</em></p>

The construction is irregular (the boundary is not that of a regular polygon, and its
edges have multiple lengths and orientations) but **symmetric** (it has cyclic
$C_n$ symmetry by construction) and **algebraically compact** (all edge vectors and
vertices lie in a single quadratic extension of $\mathbb{Q}$, often $\mathbb{Q}$
itself). These three properties — _irregular, symmetric, algebraically compact_ — are
exactly what the reconnection criteria of `affine.md` Section 2 reward, and the
pinwheel construction is a systematic recipe for producing such polygons.

---

## 1. The Pinwheel Construction

### 1.1 The Base + Fin + Rotation Recipe

The pinwheel construction has three ingredients:

1. **A base polygon** $B$ with $n$-fold rotational symmetry around its centroid
   (typically a square, equilateral triangle, regular hexagon, or regular $n$-gon).
2. **A fin** $F$: a small polygon (often a right triangle) attached to one edge of $B$
   along a _partial_ attachment segment. The fin protrudes outward, and the edge(s) of
   $F$ not coincident with $B$'s boundary become **free edges** of the composite.
3. **A rotation group** $C_n$ acting on the centroid of $B$. Place a copy of $F$ on
   each of the $n$ rotation images of the chosen edge of $B$, all related by the same
   rotation. The result is the **pinwheel polygon** $P = B \cup \bigcup_{k=0}^{n-1}
   R^k(F)$, where $R$ is rotation by $2\pi/n$.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 560 180" width="560" height="180">
   <title>Pinwheel construction recipe: base + fin + rotation</title>
   <!-- Step 1: Base -->
   <g transform="translate(40,40)">
     <rect x="0" y="0" width="100" height="100" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.5"/>
     <text x="50" y="55" text-anchor="middle" font-family="serif" font-size="14" fill="#3060a0">B</text>
     <text x="50" y="160" text-anchor="middle" font-family="serif" font-size="12" fill="#222">1. Base</text>
   </g>
   <!-- arrow -->
   <text x="170" y="95" text-anchor="middle" font-family="serif" font-size="20" fill="#666">→</text>
   <!-- Step 2: Base + one fin -->
   <g transform="translate(200,40)">
     <rect x="0" y="0" width="100" height="100" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.5"/>
     <polygon points="0,0 40,0 0,-30" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <text x="50" y="55" text-anchor="middle" font-family="serif" font-size="14" fill="#3060a0">B</text>
     <text x="15" y="-8" text-anchor="middle" font-family="serif" font-size="11" fill="#a05000">F</text>
     <text x="50" y="160" text-anchor="middle" font-family="serif" font-size="12" fill="#222">2. Attach fin</text>
   </g>
   <!-- arrow -->
   <text x="340" y="95" text-anchor="middle" font-family="serif" font-size="20" fill="#666">→</text>
   <!-- Step 3: rotate -->
   <g transform="translate(370,40)">
     <rect x="0" y="0" width="100" height="100" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.5"/>
     <polygon points="0,0 40,0 0,-30" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="100,0 100,40 130,0" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="100,100 60,100 100,130" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="0,100 0,60 -30,100" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <!-- rotation arrow -->
     <path d="M 50 50 m -25 0 a 25 25 0 1 0 25 -25" fill="none" stroke="#3060a0" stroke-width="1.2"/>
     <polygon points="75,25 70,18 68,28" fill="#3060a0"/>
     <text x="50" y="160" text-anchor="middle" font-family="serif" font-size="12" fill="#222">3. Rotate by 2π/n</text>
   </g>
</svg>
</p>
<p align="center"><em>Figure 2. The three-step pinwheel construction recipe (illustrated for n=4).</em></p>

The resulting polygon has the **dihedral or cyclic symmetry of $B$ reduced to the
cyclic subgroup $C_n$**: the fins break the reflection symmetry of $B$ but preserve
its rotational symmetry, giving the characteristic chiral "pinwheel" appearance.

### 1.2 The Canonical Example: Rectangular Pinwheel

Take $B$ to be the unit square $[0, 1]^2$. Attach to its top edge a right triangle $F$
with legs $a$ (horizontal, along part of the top edge) and $b$ (vertical, perpendicular
to the top edge), and hypotenuse of length $\sqrt{a^2 + b^2}$ as the free outer edge.
Specifically, let $F$ have vertices at $(0, 1)$, $(a, 1)$, $(0, 1 + b)$, so one leg
lies along the top-left portion of the square's edge, and the other leg lies along
the extension of the left edge. The hypotenuse runs from $(a, 1)$ to $(0, 1 + b)$.

Now rotate the composite $B \cup F$ by $90°, 180°, 270°$ around the center $(1/2, 1/2)$
of the square, taking the _union_ of all four rotations. Each fin protrudes from a
different side of the square, and the four fins all curl in the same rotational sense.

The resulting pinwheel polygon $P_{a,b}$ has:

- **8 boundary edges**: 4 partial square edges (each of length $1 - a$) and 4 fin
  hypotenuses (each of length $\sqrt{a^2 + b^2}$). Wait — let's count more carefully.
  Each square edge is split by the fin into a length-$a$ portion (covered by the fin's
  leg) and a length-$(1-a)$ portion exposed as boundary. Each fin contributes one
  perpendicular leg of length $b$ exposed as boundary and one hypotenuse exposed as
  boundary. So there are $4 + 4 + 4 = 12$ boundary edges in three classes:
  - 4 "square remnants" of length $1 - a$,
  - 4 "fin perpendiculars" of length $b$,
  - 4 "fin hypotenuses" of length $\sqrt{a^2 + b^2}$.
- **4-fold rotational symmetry** ($C_4$), no reflection symmetry (chiral).
- **All vertices in $\mathbb{Q}^2$** if $a, b \in \mathbb{Q}$. The hypotenuse
  _length_ may be irrational, but the hypotenuse _vector_ $(- a, b)$ lies in
  $\mathbb{Q}^2$, which is what matters for reconnection (Criterion 2 of `affine.md`
  is about vectors, not lengths).

Thus the rectangular pinwheel $P_{a,b}$ with $a, b \in \mathbb{Q}$ is **algebraically
compact over $\mathbb{Q}$**: $\mathbb{F} = \mathbb{Q}$, despite having edges of three
different lengths and four different orientations.

### 1.3 Why "Pinwheel"?

The name reflects the visual appearance: the four fins curl outward in the same
rotational sense, like the blades of a child's pinwheel toy. The construction is
inherently **chiral** — its mirror image (where each fin curls the opposite way) is
not congruent to the original by orientation-preserving isometries alone. This
chirality is one of the construction's key features: it allows the pinwheel to
distinguish "left" from "right" in expansions, producing graphs with intrinsic
handedness.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 420 200" width="420" height="200">
   <title>Chirality: left-handed and right-handed pinwheels</title>
   <!-- Left-handed (CCW fins) -->
   <g transform="translate(40,30)">
     <rect x="0" y="0" width="100" height="100" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.5"/>
     <polygon points="0,0 40,0 0,-30" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="100,0 100,40 130,0" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="100,100 60,100 100,130" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="0,100 0,60 -30,100" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <text x="50" y="160" text-anchor="middle" font-family="serif" font-size="12" fill="#222">Left-handed</text>
   </g>
   <!-- Mirror -->
   <line x1="210" y1="20" x2="210" y2="160" stroke="#999" stroke-dasharray="4,3" stroke-width="1"/>
   <text x="210" y="15" text-anchor="middle" font-family="serif" font-size="10" fill="#999">mirror</text>
   <!-- Right-handed (CW fins) -->
   <g transform="translate(280,30)">
     <rect x="0" y="0" width="100" height="100" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.5"/>
     <polygon points="60,0 100,0 100,-30" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="100,60 100,100 130,100" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="0,100 40,100 0,130" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <polygon points="0,0 0,40 -30,0" fill="#ffd9a8" stroke="#a05000" stroke-width="1.5"/>
     <text x="50" y="160" text-anchor="middle" font-family="serif" font-size="12" fill="#222">Right-handed</text>
   </g>
</svg>
</p>
<p align="center"><em>Figure 3. The two chiralities of a C₄ pinwheel. Reflection swaps them; no direct rotation does.</em></p>

---

## 2. Reconnection Properties of Pinwheel Polygons

### 2.1 Orientation Group

Because the pinwheel polygon has $C_n$ symmetry by construction, the reflections (or
half-turns) across its edges generate a subgroup of the dihedral group $D_{2n}$ or a
closely related group. Crucially:

- The **boundary edges of the same orbital class** (e.g., the four hypotenuses) are
  all related by the rotation $R$ of order $n$.
- The reflection across one boundary edge, conjugated by $R^k$, gives the reflection
  across the $k$-th rotated copy of that edge.

Therefore the orientation group $\Gamma$ generated by edge reflections is contained
in the **dihedral group $D_{2n}$** generated by reflections across $n$ lines through
the polygon's center, together with reflections across lines _parallel_ to those
through the edge midpoints. In all standard pinwheel cases ($n = 4$ with $a, b
\in \mathbb{Q}$, or $n = 3$ with appropriate ratios, or $n = 6$, etc.), $\Gamma$ is
a **finite group** — specifically a subgroup of $D_{2n}$ extended by translations.

**Criterion 1 of `affine.md` is satisfied by construction**: pinwheels have finite
orientation groups because they are built from a finite cyclic symmetry.

### 2.2 Algebraic Compactness

The vertices of $P$ all lie in $\mathbb{F}^2$ where $\mathbb{F} = \mathbb{Q}(\cos(2\pi/n),
\sin(2\pi/n), a, b)$. For the special values of $n$ where $\cos(2\pi/n), \sin(2\pi/n)
\in \mathbb{Q}$ — namely $n = 1, 2, 4$ — and rational $a, b$, we have $\mathbb{F} =
\mathbb{Q}$ exactly. For $n = 3, 6$, $\mathbb{F} = \mathbb{Q}(\sqrt{3})$. For $n = 5$,
$\mathbb{F} = \mathbb{Q}(\sqrt{5})$ (the golden ratio field). For $n = 8$,
$\mathbb{F} = \mathbb{Q}(\sqrt{2})$.

In each of these cases $\mathbb{F}$ is a **single quadratic extension of $\mathbb{Q}$**,
which is precisely the condition for Criterion 2 of `affine.md` to hold.

**Criterion 2 of `affine.md` is satisfied by construction for $n \in \{3, 4, 5, 6, 8\}$
and rational fin parameters.** Pinwheels with rational fins and these symmetry orders
are **algebraically compact** in the strongest sense.

### 2.3 Pinwheels Are Level-0 or Level-1 Reconnective

Combining the above:

- **$n = 4$, $a, b \in \mathbb{Q}$**: $\Gamma$ finite, $\mathbb{F} = \mathbb{Q}$.
  The full expansion family (all edges active) is reconnective, and the adjacency graph
  lives on a periodic refinement of $\mathbb{Z}^2$. **Level 0** (full reconnection).
- **$n = 3, 6$**: $\Gamma$ finite (subgroup of $D_{12}$), $\mathbb{F} = \mathbb{Q}(\sqrt{3})$.
  **Level 0** on the triangular/hexagonal lattice.
- **$n = 5$**: $\Gamma$ finite (subgroup of $D_{10}$), $\mathbb{F} = \mathbb{Q}(\sqrt{5})$.
  The angular deficit forces a multi-sheeted cover (the Penrose-like sheet structure).
  **Level 1** (multi-sheeted full reconnection).

Pinwheels with $n \in \{7, 9, 11, \dots\}$ produce $\mathbb{F}$ involving cyclotomic
fields of degree $> 2$, which generally violates Criterion 2. Such pinwheels are
**edge-restricted reconnective** (Level 2) — one drops the hypotenuse edges and works
only with the rational base+leg sector.

---

## 3. The Tiling Picture

### 3.1 Pinwheels as Periodic Tiles

The rectangular pinwheel $P_{a,b}$ with $a, b \in \mathbb{Q}$ tiles the plane
_periodically_. The reason is that the four fins of one pinwheel can interlock with
the fins of four neighboring pinwheels to fill a $2 \times 2$ super-square of side
$\sqrt{(1 + b)^2 + a^2}$ (or similar, depending on fit), and this super-square then
tiles $\mathbb{R}^2$ periodically.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-80 -80 420 420" width="420" height="420">
   <title>Periodic tiling of pinwheels: four interlocking copies</title>
   <defs>
     <g id="pw">
       <rect x="0" y="0" width="100" height="100" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.2"/>
       <polygon points="0,0 40,0 0,-30" fill="#ffd9a8" stroke="#a05000" stroke-width="1.2"/>
       <polygon points="100,0 100,40 130,0" fill="#ffd9a8" stroke="#a05000" stroke-width="1.2"/>
       <polygon points="100,100 60,100 100,130" fill="#ffd9a8" stroke="#a05000" stroke-width="1.2"/>
       <polygon points="0,100 0,60 -30,100" fill="#ffd9a8" stroke="#a05000" stroke-width="1.2"/>
     </g>
   </defs>
   <use href="#pw" x="0"   y="0"/>
   <use href="#pw" x="130" y="0"/>
   <use href="#pw" x="0"   y="130"/>
   <use href="#pw" x="130" y="130"/>
   <!-- super-cell outline -->
   <rect x="-30" y="-30" width="260" height="260" fill="none" stroke="#c03030" stroke-width="1.5" stroke-dasharray="6,4"/>
   <text x="100" y="-40" text-anchor="middle" font-family="serif" font-size="12" fill="#c03030">super-cell</text>
</svg>
</p>
<p align="center"><em>Figure 4. Four rectangular pinwheels interlock to fill a periodic super-cell (dashed red).</em></p>

This is a key distinction from the Conway–Radin pinwheel **tile** (a single right
triangle whose tiling is aperiodic): our pinwheel **polygon** is a _composite_ shape
that tiles periodically and reconnects cleanly. The two are different mathematical
objects with the same evocative name.

### 3.2 The Adjacency Graph of a Pinwheel Tiling

When pinwheels tile periodically, the adjacency graph (one vertex per pinwheel, edges
between pinwheels sharing a boundary segment) is a periodic lattice graph in
$\mathbb{R}^2$. The valence depends on which pairs of edge classes match:

- **Hypotenuse-to-hypotenuse matches** between adjacent pinwheels are the most common,
  giving 4 hypotenuse-neighbors per pinwheel.
- **Leg-to-leg matches** along the perpendicular fin edges give additional neighbors.
- **Square-remnant-to-square-remnant matches** along the leftover square edges give
  the remaining neighbors.

The total valence is typically 8 or 12, depending on the values of $a$ and $b$. This
is the **highly reconnected** regime: small chromatic number, high symmetry, and
integer effective dimension $d_{\text{eff}} = 2$ exactly.

### 3.3 Engineering Aperiodicity from Pinwheels

Although the basic pinwheel polygon $P_{a,b}$ tiles periodically, one can introduce
aperiodicity by **varying the fin parameters** in a controlled way:

- **Hierarchical pinwheels**: Choose fin parameters $(a, b)$ so that the super-square
  formed by four interlocking pinwheels is itself similar to a single pinwheel at a
  different scale. This produces a self-similar inflation rule analogous to the
  Conway–Radin substitution.
- **Multi-scale pinwheels**: Mix pinwheels with different $(a, b)$ in a single tiling,
  with a substitution rule that selects which fin to attach based on local context.

These hierarchical and multi-scale variants are the bridge between the _periodic_
pinwheel polygon (the central object of this note) and the _aperiodic_ Conway–Radin
tiling. Both arise from the same construction principle (cyclic composition with fins),
but with different parameter choices.

---

## 4. Pinwheels of Higher Symmetry

### 4.1 The $C_3$ Pinwheel (Triangular Base)

Take $B$ to be an equilateral triangle of side $s$ and attach a smaller fin triangle to
each of its three edges, with $C_3$ rotational symmetry around the centroid. If the
fins are right triangles with legs $a$ along the base edge and $b$ perpendicular to it,
the resulting hexagonal pinwheel has:

For $a, b \in \mathbb{Q}$ (with $s = 1$), $\mathbb{F} = \mathbb{Q}(\sqrt{3})$ and
$\Gamma$ is a subgroup of $D_6 \ltimes \mathbb{F}^2$. The pinwheel reconnects on the
triangular lattice.

<p align="center">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-80 -80 360 320" width="360" height="320">
   <title>C3 (triangular), C6 (hexagonal), and C5 (pentagonal) pinwheels</title>
   <!-- C3 pinwheel -->
   <g transform="translate(0,40)">
     <!-- equilateral triangle, side 80, centered around (50,50) -->
     <polygon points="10,75 90,75 50,5" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.3"/>
     <!-- three fins (small right triangles), CCW -->
     <polygon points="10,75 40,75 5,90" fill="#ffd9a8" stroke="#a05000" stroke-width="1.2"/>
     <polygon points="90,75 70,40 105,55" fill="#ffd9a8" stroke="#a05000" stroke-width="1.2"/>
     <polygon points="50,5 30,40 15,15" fill="#ffd9a8" stroke="#a05000" stroke-width="1.2"/>
     <text x="50" y="115" text-anchor="middle" font-family="serif" font-size="12" fill="#222">C₃</text>
   </g>
   <!-- C6 pinwheel -->
   <g transform="translate(120,40)">
     <!-- regular hexagon, radius 40, center (50,50) -->
     <polygon points="90,50 70,84.6 30,84.6 10,50 30,15.4 70,15.4" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.3"/>
     <!-- six small fins -->
     <polygon points="90,50 80,67 105,62" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="70,84.6 50,84.6 75,100" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="30,84.6 20,67 5,90" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="10,50 20,33 -5,38" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="30,15.4 50,15.4 25,0" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="70,15.4 80,33 95,10" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <text x="50" y="115" text-anchor="middle" font-family="serif" font-size="12" fill="#222">C₆</text>
   </g>
   <!-- C5 pinwheel -->
   <g transform="translate(240,40)">
     <!-- regular pentagon, radius 40, center (50,50), vertex at top -->
     <polygon points="50,10 88,37.6 73.5,82.4 26.5,82.4 12,37.6" fill="#e8f0ff" stroke="#3060a0" stroke-width="1.3"/>
     <!-- five fins -->
     <polygon points="50,10 70,22 78,5" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="88,37.6 81,60 105,55" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="73.5,82.4 50,82.4 70,100" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="26.5,82.4 30,60 5,75" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <polygon points="12,37.6 30,30 15,10" fill="#ffd9a8" stroke="#a05000" stroke-width="1.1"/>
     <text x="50" y="115" text-anchor="middle" font-family="serif" font-size="12" fill="#222">C₅ (multi-sheeted)</text>
   </g>
</svg>
</p>
<p align="center"><em>Figure 5. Higher-symmetry pinwheels: triangular (C₃), hexagonal (C₆), and pentagonal (C₅). The C₅ case requires a multi-sheeted cover due to its angular deficit.</em></p>

### 4.2 The $C_6$ Pinwheel (Hexagonal Base)

Take $B$ to be a regular hexagon and attach six fins with $C_6$ symmetry. The resulting
12- or 18-sided polygon reconnects on the triangular lattice with valence $6$ per
pinwheel (one hypotenuse-pair per fin pair across the dual edge).

### 4.3 The $C_5$ Pinwheel (Pentagonal Base, Multi-Sheeted)

Take $B$ to be a regular pentagon and attach five fins with $C_5$ symmetry. The
angular deficit at vertices forces a multi-sheeted cover with fiber group
$\mathbb{Z}_{\geq 1}$ generated by rotations by $2\pi/5$ — analogous to the regular
pentagon expansion of `idea.md`. The pinwheel inherits the Penrose-like sheet
structure of its base.

### 4.4 Asymmetric "Almost-Pinwheel" Variants

One can also relax the cyclic symmetry by attaching fins of _different_ dimensions to
different edges of $B$, while keeping the rotation group abstract (i.e., the fins are
only "approximately" cyclically related). These **asymmetric pinwheels** lose
Criterion 1' on the full edge set, but often retain it on a chosen active subset —
placing them at Level 2 of the hierarchy in Section 6 below.

---

## 5. Algebraic Compactness as a Design Principle

The pinwheel construction is best understood not as a single recipe but as a
**design principle**: _to construct a polygon that is irregular yet reconnective,
build it by cyclic composition of an algebraically simple base with an algebraically
simple fin._

The three governing constraints are:

1. **Symmetry**: The polygon has $C_n$ rotational symmetry by construction. This
   guarantees Criterion 1 (finite orientation group, modulo the angular deficit
   handled by the sheet rule).
2. **Rationality of vectors**: All fin parameters are chosen in a single quadratic
   extension $\mathbb{F}$ of $\mathbb{Q}$. This guarantees Criterion 2.
3. **Geometric closure**: The fins are chosen so that the boundary of $P$ closes up
   without gaps and so that copies of $P$ tile $\mathbb{R}^2$ (periodically or with
   controlled aperiodicity).

Pinwheels are essentially the **simplest non-trivial polygons** that satisfy all
three constraints. A regular polygon satisfies all three trivially (it's regular!);
a generic irregular polygon satisfies none of them. The pinwheel sits in between:
_as irregular as possible while still being symmetric and algebraically compact._

This is what makes pinwheels valuable as building blocks for expansion families.
They span the space of _interesting_ (non-regular, chiral, multi-length-class)
polygons that are nevertheless tractable under the reconnection framework.

---

## 6. The Reconnection Hierarchy for Pinwheels

Combining the pinwheel construction with the classification of `affine.md` Section 2:

| Level | Pinwheel type                                              | Reconnection                                    | Example                                              |
| ----- | ---------------------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------- |
| 0     | $C_n$ pinwheel, $n \in \{3, 4, 6\}$, rational fins         | Full periodic reconnection                      | Rectangular pinwheel $P_{a,b}$, $a,b \in \mathbb{Q}$ |
| 1     | $C_n$ pinwheel, $n \in \{5, 8, 12\}$, single quadratic fin | Multi-sheeted full reconnection                 | Pentagonal pinwheel                                  |
| 2     | $C_n$ pinwheel, $n \in \{7, 9, 11\}$ or mixed quadratic    | Edge-restricted reconnection (drop hypotenuses) | Heptagonal pinwheel                                  |
| 3     | Asymmetric "pinwheel" with no compatible $\mathbb{F}$      | Non-reconnective                                | Random fin parameters                                |

Notice that **most** pinwheels of practical interest fall in Levels 0 or 1 — the
construction is naturally aligned with the reconnection framework. This is the
sense in which pinwheels are "designed for reconnection".

---

## 7. Examples Catalogue

### 7.1 The Rectangular Pinwheel $P_{a,b}$ ($n = 4$)

- **Base**: unit square.
- **Fin**: right triangle with legs $a$ (horizontal), $b$ (vertical), hypotenuse $\sqrt{a^2+b^2}$.
- **Symmetry**: $C_4$ chiral.
- **$\mathbb{F}$**: $\mathbb{Q}$ if $a, b \in \mathbb{Q}$.
- **Tiling**: periodic, super-cell of area $(1 + ab) \cdot \text{something}$ depending on fit.
- **Adjacency valence**: 8 or 12.
- **$d_{\text{eff}}$**: $2$ exactly.

Special cases:

- $a = b = 1/2$: symmetric pinwheel, super-cell is a $\sqrt{2} \times \sqrt{2}$ square.
- $a = 1/2, b = 1$: tall fins (the "windmill blade" pinwheel).
- $a \to 0$: fins degenerate to spikes, pinwheel approaches the unit square.

### 7.2 The Triangular Pinwheel ($n = 3$)

- **Base**: equilateral triangle of side $1$.
- **Fin**: right triangle with legs in $\mathbb{Q}$.
- **Symmetry**: $C_3$ chiral.
- **$\mathbb{F}$**: $\mathbb{Q}(\sqrt{3})$.
- **Tiling**: periodic on the triangular lattice.

### 7.3 The Hexagonal Pinwheel ($n = 6$)

- **Base**: regular hexagon of side $1$.
- **Fin**: right triangle with legs in $\mathbb{Q}$.
- **Symmetry**: $C_6$ chiral.
- **$\mathbb{F}$**: $\mathbb{Q}(\sqrt{3})$.
- **Tiling**: periodic, kagome-like adjacency graph.

### 7.4 The Pentagonal Pinwheel ($n = 5$, Multi-Sheeted)

- **Base**: regular pentagon of side $1$.
- **Fin**: triangle with edges in $\mathbb{Q}(\sqrt{5})$.
- **Symmetry**: $C_5$ chiral.
- **$\mathbb{F}$**: $\mathbb{Q}(\sqrt{5})$.
- **Tiling**: multi-sheeted cover of the plane, fiber group $\mathbb{Z}$ generated
  by rotation by $2\pi/5$.
- **$d_{\text{eff}}$**: in $(2, 3)$, analogous to the regular pentagon expansion of
  `idea.md`.

### 7.5 The Square-Hypotenuse Pinwheel ($n = 4$, $a = b$)

The special case $a = b$ gives fins that are **isoceles right triangles** — the
hypotenuse makes a $45°$ angle with the square's edges. The full orientation group
is a subgroup of $D_8$ (octagonal dihedral). This pinwheel has the highest symmetry
in the $n = 4$ family while remaining chiral, and its hypotenuse vectors lie in
$\mathbb{Q}^2$ (since $(-a, a) \in \mathbb{Q}^2$). It is the **canonical $n = 4$
pinwheel** and is the simplest example showing all the features of the construction.

---

## 8. Algorithmic Construction

Constructing a pinwheel polygon programmatically is straightforward given the base,
fin, and rotation order:

```python
def construct_pinwheel(base_polygon, fin_polygon, fin_attachment_edge_index, n):
    """
    Construct a C_n pinwheel from a base polygon and a fin polygon.

    Args:
        base_polygon: list of vertices of the base, in CCW order.
        fin_polygon: list of vertices of the fin, with the first edge being the
                     attachment edge (lies along part of one base edge).
        fin_attachment_edge_index: index of the base edge to attach the fin to.
        n: rotational symmetry order (must divide the symmetry of base_polygon).

    Returns:
        A list of vertices forming the boundary of the pinwheel polygon.
    """
    center = centroid(base_polygon)
    rotation_angle = 2 * pi / n
    composite = polygon_union(base_polygon, place_fin(fin_polygon, base_polygon, fin_attachment_edge_index))
    for k in range(1, n):
        rotated_fin = rotate(place_fin(fin_polygon, base_polygon, fin_attachment_edge_index),
                             center, k * rotation_angle)
        composite = polygon_union(composite, rotated_fin)
    return boundary_vertices(composite)
```

The key subtleties are:

1. **Fin placement**: The fin's attachment edge must lie _along_ (not coincide with)
   a portion of the base edge, so the fin "sticks out" from a sub-segment of the
   base edge.
2. **Polygon union**: Care is needed to avoid duplicating shared edges in the boundary.
3. **Exact arithmetic**: For algebraic compactness, use exact rational or quadratic
   arithmetic throughout — never floating-point — to preserve the property that all
   vertices lie in $\mathbb{F}^2$.

Once the pinwheel polygon is constructed, its expansion family is generated by the
standard adjacency-oracle machinery of `idea.md` Section 6.

---

## 9. Open Questions

1. **Classification of pinwheel tilings**: For each $n \in \{3, 4, 5, 6, 8, 12\}$ and
   each fin shape, when does the resulting pinwheel polygon tile $\mathbb{R}^2$
   periodically? What is the fundamental domain?

2. **Hierarchical pinwheels**: For which fin parameters $(a, b)$ does the
   $2 \times 2$ super-square of interlocking pinwheels (in the $n = 4$ case) admit a
   further pinwheel substitution rule, generating self-similar tilings? Is the
   Conway–Radin tiling a degenerate limit of this family?

3. **Optimal pinwheels for expander construction**: Among all $C_n$ pinwheels with
   fins in $\mathbb{Q}$, which produce adjacency graphs with the largest spectral
   gap per unit valence? Is there a "Ramanujan pinwheel"?

4. **Higher-dimensional pinwheels**: A 3D pinwheel polyhedron is a base polyhedron
   (e.g., cube) with a small "fin polyhedron" attached to one face and the
   composite rotated by a finite rotation subgroup of $SO(3)$ (e.g., the cyclic group
   $C_4$ or the tetrahedral group $T$). What 3D tilings arise from this construction?
   Do they include the Schmitt–Conway–Danzer "biprism" aperiodic monotile?

5. **Pinwheels and crystallography**: The 17 wallpaper groups classify all periodic
   2D tilings. Which wallpaper groups arise as the symmetry groups of pinwheel
   tilings, and which require the breaking of pinwheel chirality?

---

## 10. Summary

The **pinwheel polygon** is a polygon built by cyclic composition: take a symmetric
base polygon $B$ with $C_n$ rotational symmetry, attach a small fin $F$ to one of its
edges, and replicate $F$ on each of the $n$ rotated edges. The resulting polygon is
**irregular** (multiple edge lengths and orientations), **symmetric** ($C_n$
rotational symmetry, chiral), and **algebraically compact** (all vertices in a single
quadratic extension $\mathbb{F}$ of $\mathbb{Q}$).

These three properties make pinwheels ideal building blocks for expansion families:

- **Symmetry** guarantees that the orientation group $\Gamma$ is finite (Criterion 1).
- **Algebraic compactness** guarantees that all edge vectors lie in $\mathbb{F}^2$
  for a single quadratic $\mathbb{F}$ (Criterion 2).
- **Irregularity** ensures the construction goes beyond the trivial case of regular
  polygons, producing chiral, multi-class edge structures with rich adjacency graphs.

Pinwheels span the space of _interesting_ polygons that are nonetheless tractable
under the reconnection framework. By tuning the fin parameters $(a, b)$ and the
rotation order $n$, one can produce expansion families with prescribed effective
dimension, valence, and spectral properties — all from purely geometric ingredients.

The pinwheel construction sits at Levels 0 and 1 of the reconnection hierarchy
(`affine.md` Section 2 and `pinwheels.md` Section 6 above) for the small symmetry
orders $n \in \{3, 4, 5, 6\}$, making it the **canonical source of non-regular
reconnective polygons** in the framework. Together with the regular polygons and
polyhedra, pinwheels form the basic vocabulary from which more elaborate
expansion-family constructions can be assembled.
