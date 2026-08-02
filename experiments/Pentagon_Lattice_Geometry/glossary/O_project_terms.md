# O. Project-Specific Terminology — Detailed Reference

This document collects **terms unique to (or distinctively shaped by) the
multi-sheeted n-gon tiling project**. These are not standard mathematical
terminology — they were coined or repurposed within the project to name specific
phenomena observed in the pentagonal multi-sheeted framework.

---

## The Degeneracy Engine

### Definition

The **degeneracy engine** is the mechanism by which the algebraic structure of
$\mathbb{Z}[\phi]$ — specifically, **norm-multiplicativity** combined with **unique
factorization** — forces many distance coincidences in pentagonal lattices.

### How It Works

1. Each lattice point $p = a + b\phi$ has squared distance to origin given by
   $|p|^2 = a^2 + ab\phi - b^2\phi + b^2\phi^2 = \ldots$ in $\mathbb{Z}[\phi]$.
2. The integer-valued **norm** $N(p) = a^2 + ab - b^2$ is multiplicative: $N(pq) =
   N(p) N(q)$.
3. Since $\mathbb{Z}[\phi]$ is a UFD, integers $n$ that are norms of $\mathbb{Z}[\phi]$
   elements are exactly the integers whose prime factorization has compatible behavior
   under the splitting of primes in $\mathbb{Z}[\phi]$.
4. A given norm value $n$ may be achieved by **many distinct factorizations**,
   each corresponding to a different lattice point at the corresponding distance.

The result: **distance rings have very high multiplicity**, producing the
pinwheel distance-direction patterns characteristic of pentagonal lattices.

### Significance

The degeneracy engine is **why pentagonal lattices are extremal for Erdős-type
distance problems**. It is the algebraic source of the pentagonal pinwheel
phenomenon, and it is what makes the pentagonal multi-sheeted framework a natural
candidate for testing classical conjectures in combinatorial geometry.

---

## The Pinwheel Phenomenon

### Definition

The **pinwheel phenomenon** is the **density of (distance, direction) pairs** in
pentagonal lattices: many lattice points lie at the same distance from a reference
point, but in different directions, with the directions becoming dense in $S^1$.

### Mechanism

The orientation group $\Gamma = \mathbb{Z}_{10}$ generates 10 distinct rotation
angles per generator step. Combined with the degeneracy engine (many factorizations
per norm), each distance ring contains points at many distinct angles.

Iterating the expansion process, the set of (distance, direction) pairs becomes
**dense** in $\mathbb{R}_{\geq 0} \times S^1$, in a quasi-uniform way — but with
structured local patterns that distinguish pentagonal from generic Delone sets.

### Visualization

A typical visualization: plot all lattice points within distance $R$ of the origin
and color by distance. The resulting plot looks like a **pinwheel** — concentric
rings of points spinning around the origin, with each ring carrying many points at
distinct angles. The visual analogy to a pinwheel toy gives the phenomenon its
name.

### Comparison

| Lattice              | Distance rings               | Direction density              |
| -------------------- | ---------------------------- | ------------------------------ |
| $\mathbb{Z}^2$       | Sparse (sums of two squares) | $\pi/2$-discrete               |
| $\mathbb{Z}[i]$      | Same as $\mathbb{Z}^2$       | $\pi/2$-discrete               |
| Hexagonal lattice    | Moderate (Eisenstein norms)  | $\pi/3$-discrete               |
| $\mathbb{Z}[\phi]^2$ | **Dense (golden norms)**     | **$\pi/5$-discrete and dense** |

The pentagonal lattice qualitatively differs from cubic and hexagonal lattices in
having genuinely dense direction sets per distance ring.

---

## The signed3 Rule

### Definition

The **signed3 rule** is the project's default rule for assigning sheet-transition
labels to edges around a vortex. Edges receive labels from the set

$\{-1, 0, +1\}$

cyclically, with exactly **two-thirds of edges carrying a non-zero shift**.

### Construction

For a vortex vertex of degree $k$ (with $k$ incident edges) in a multi-sheeted
pentagonal tiling:

1. Walk around the vortex cyclically, visiting edges in counterclockwise order.
2. Assign labels in the repeating pattern $(+1, -1, 0, +1, -1, 0, \ldots)$.
3. Verify the labels sum to the deficit $\delta$ modulo the fiber group order.

For pentagonal lattices with $k = 3$ pentagons per vortex, the pattern $(+1, -1,
0)$ gives a total shift of $0$ — i.e., trivial holonomy in $\mathbb{Z}_3$ — but
when lifted to $\mathbb{Z}_{10}$ with appropriate scaling, the holonomy is
non-trivial.

### The 2/3 Edge Fraction

A key invariant: under signed3, exactly **2/3 of the edges** carry non-zero shift,
independent of vortex degree. This **edge fraction** is one of the most robust
observables of the rule.

### Why "signed3"?

The "signed" refers to the use of both $+1$ and $-1$ (rather than just $\{0, 1\}$),
and the "3" refers to the 3-symbol alphabet $\{-1, 0, +1\}$. The rule contrasts
with simpler alternatives:

- **unsigned1**: all non-vortex edges get $+1$ shift. Trivial; produces simple
  counting holonomy but no signed-loop structure.
- **signed2**: edges get $\{-1, +1\}$. Vortex edge fraction is $1$; gives more
  holonomy but is harder to make consistent.
- **signed3**: the default; gives the right balance between non-triviality and
  consistency, with the canonical $2/3$ edge fraction.

### Alternative Rules

The framework supports other rules (parameterized by the fiber group and the
desired vortex symmetry), but signed3 is the canonical baseline and the rule used
in most project computations.

---

## Spinor-Like Holonomy

### Definition

**Spinor-like holonomy** is the discrete combinatorial analogue of the
$SU(2) \to SO(3)$ double-cover phenomenon: a single loop around a vortex returns
the **"wrong" sheet** (analogue of $-1$), but **two loops restore identity**.

### How It Arises

In the pentagonal multi-sheeted framework with fiber group $\mathbb{Z}_{10}$,
consider the $\mathbb{Z}_2$ quotient: each sheet has a **parity**. Under signed3,
a loop around a vortex shifts the sheet by $\pm 5 \mod 10$, flipping parity. Two
loops shift by $\pm 10 \equiv 0$, restoring parity.

Thus, in the parity sector:

- **One loop**: parity flip ($\equiv -1$).
- **Two loops**: identity ($\equiv +1$).

This is exactly the spinor structure of $SU(2) \to SO(3)$, realized **discretely**
by a combinatorial sheet-transition rule.

### Significance

Spinor-like holonomy is one of the most striking signatures of the multi-sheeted
pentagon framework. It demonstrates that:

1. Spinor structure is **not exclusively continuous** — it can emerge from
   discrete combinatorial topology.
2. The pentagonal angular deficit, far from being a defect, is the **source** of
   this rich structure.
3. Fermionic / spinorial behavior has a natural combinatorial home in
   multi-sheeted tilings, with implications for lattice formulations of fermions
   and for discrete quantum gravity.

### Discrete vs. Continuous

| Property             | Continuous spinor | Discrete spinor-like           |
| -------------------- | ----------------- | ------------------------------ |
| Domain               | $SU(2)$-rep       | $\mathbb{Z}_{10}$ sheet bundle |
| Single loop          | $-1$ rotation     | Sheet parity flip              |
| Double loop          | $+1$ (identity)   | Sheet parity restored          |
| Source               | Group topology    | Vortex holonomy                |
| Physical realization | Electrons, quarks | CA gliders on sheets           |

---

## Vortex Edge Fraction

### Definition

The **vortex edge fraction** $\rho_V$ is the fraction of edges in the tiling that
carry **non-zero sheet shift** under a given vortex rule:

$\rho_V = \frac{|\{e \in E : \tau(e) \neq 0\}|}{|E|}.$

### The 2/3 Theorem

Under the signed3 rule on pentagonal multi-sheeted tilings, the edge fraction
satisfies

$\rho_V = \frac{2}{3}$

exactly (in the infinite limit, and approximately for finite samples). This is a
direct combinatorial consequence of the labeling pattern $(+1, -1, 0)$ used
cyclically.

### Significance

The $2/3$ fraction is a **canonical invariant** of the signed3 rule. It is:

- Independent of the size of the tiling sample.
- Independent of the specific topology of the multi-sheeted cover (as long as
  it's consistent).
- A diagnostic for correct rule implementation.

If a computation reports a different vortex edge fraction under signed3, the
implementation has a bug or the rule has been misapplied.

---

## Vortex Rule

### Definition

A **vortex rule** is a rule for assigning sheet-transition labels $\tau(e) \in G$
to edges incident to a vortex vertex, such that the total holonomy around the
vortex matches the angular deficit (modulo the fiber group).

### Examples

- **signed3** (default): labels in $\{-1, 0, +1\}$, $2/3$ edge fraction.
- **unsigned1**: labels in $\{0, 1\}$, simpler but less expressive.
- **signed2**: labels in $\{-1, +1\}$, full edge coverage.
- **signed-k**: labels in $\{-k, \ldots, +k\}$, $k$-step generalizations.
- **custom**: any consistent assignment satisfying total-holonomy constraint.

### Constraints

Every vortex rule must satisfy:

1. **Local consistency**: sum of shifts around any vortex equals the deficit (mod
   fiber group).
2. **Global consistency**: edges shared between vortices receive consistent labels
   from both sides.
3. **Background closure**: non-vortex loops have trivial total shift.

Most rules satisfy (1) by construction; (2) and (3) require careful global
consistency checks (see section M, computational tools).

---

## The Multi-Sheeted Pivot

### Definition

The **multi-sheeted pivot** is the conceptual move at the heart of the project:
instead of viewing the pentagonal angular deficit as a **failure to tile**
(the historical perspective: Penrose's two-tile resolution), view it as a
**trigger for promoting the tiling to a multi-sheeted cover**.

### Two Resolutions Compared

The angular deficit of $36°$ for the regular pentagon has two known resolutions:

1. **Penrose's resolution**: introduce a **second tile shape** (kite + dart, or
   thick + thin rhomb), with matching rules forcing aperiodicity. Tiling lives
   on a **single sheet**.

2. **Multi-sheeted resolution** (this project): keep the **single pentagon tile
   shape**, but promote to a **multi-sheeted cover** with vortex holonomy.
   Angular deficit absorbed into sheet topology.

Both are valid; both produce mathematically rich structures. The multi-sheeted
pivot reveals connections to gauge theory, spinor topology, and quantum gravity
that the Penrose resolution does not.

### Why It Matters

The multi-sheeted pivot reframes the entire pentagonal tiling problem in
**topological language**:

- Sheets ↔ bundles.
- Sheet transitions ↔ discrete connections.
- Vortex holonomy ↔ Wilson loops.
- Spinor-like behavior ↔ universal covers.

This linguistic shift opens the door to the physics analogues catalogued in
section J — and it is the project's distinctive intellectual contribution.

---

## Topological Tearing

### Definition

**Topological tearing** is the failure mode in which **floating-point arithmetic
errors** cause spuriously distinct identifications of tiles that should be
identical, breaking the multi-sheeted topology and producing an artificially
tree-like adjacency graph.

### Symptoms

- Exponential growth of the BFS volume (instead of polynomial).
- Missing cycles in the adjacency graph.
- Failed vortex consistency checks.
- Wrong dimensional estimates.
- Lost spinor-like holonomy.

### Cause

Comparing pentagonal lattice points $a_1 + b_1 \phi$ and $a_2 + b_2 \phi$ requires
exact equality of $(a_1, b_1) = (a_2, b_2)$. Floating point cannot reliably do
this — small errors in repeated multiplication produce false negatives.

### Cure

**Exact symbolic arithmetic** over $\mathbb{Z}[\phi]$ (or whichever ring is
relevant). See section M for implementation details. There is no half-measure
fix; topological tearing requires fundamentally exact arithmetic.

### The Name

"Topological tearing" evokes the image of a fabric (the multi-sheeted cover) being
torn into pieces by the slightest numerical inaccuracy. The phrase captures both
the failure mode (tearing) and its source (topological identifications).

---

## Sheet Stack

### Definition

The **sheet stack** is the foliated structure of the multi-sheeted cover:
a collection of copies of $\mathbb{R}^n$ (the **sheets**) glued together along
edges by sheet-transition functions.

### Picture

Imagine $|G|$ parallel copies of $\mathbb{R}^n$ stacked vertically. Each copy is
a **sheet**, indexed by an element of the fiber group $G$. A point in
$\mathcal{M}$ is specified by a base point in $\mathbb{R}^n$ and a sheet index
in $G$. Edges of the tiling glue sheets together: crossing an edge with shift
$\tau(e) = g$ moves you up the stack by $g$.

### Why "Stack"?

The visual metaphor of stacking emphasizes that the sheets are **parallel copies**
of the base, not deformations. The sheet stack is the **discrete analogue** of a
foliation by space-like slices in physics, or a covering space in topology.

---

## Dimensional Flow

### Definition

**Dimensional flow** is the scale-dependence of effective and spectral dimensions:
the UV (short-scale) and IR (long-scale) values differ, often markedly.

### In the Pentagon

Empirically, pentagonal multi-sheeted tilings show:

- **UV regime** (small BFS radii): $d_{\text{eff}}$, $d_{\text{spec}}$ approach
  $\sim 2$ — the topological tearing-free limit reveals near-2D scaling.
- **IR regime** (large BFS radii): $d_{\text{eff}}$, $d_{\text{spec}}$ approach
  $2$ exactly — Euclidean scaling far from vortices.

Near vortices, transient deviations occur. The flow itself — the **shape of the
interpolation** — is the project's prediction.

### Connection to Asymptotic Safety

The Asymptotic Safety scenario in quantum gravity predicts UV spectral dimension
$\to 2$, with IR spectral dimension $\to 4$. The multi-sheeted pentagon's UV → IR
flow is **structurally analogous**, though in lower ambient dimension. The
convergence of these patterns is one of the project's most suggestive empirical
findings.

---

## Reconnection Hierarchy

Already covered in detail in section L; here we just note that the project
consistently uses the four-level vocabulary:

- **Level 0**: full reconnection on a single sheet.
- **Level 1**: multi-sheeted full reconnection (the pentagonal case).
- **Level 2**: edge-restricted reconnection (pinwheel polygons).
- **Level 3**: non-reconnective (infinite tree).

This hierarchy is **the** organizing principle of the project's classification
work.

---

## Summary: The Project's Conceptual Signature

A handful of terms uniquely identify the project's intellectual fingerprint:

1. **Multi-sheeted pivot**: angular deficit → sheet topology.
2. **Reconnection hierarchy**: four levels of expansion-family behavior.
3. **signed3 rule**: canonical sheet-transition assignment with $2/3$ edge
   fraction.
4. **Spinor-like holonomy**: discrete $SU(2) \to SO(3)$ analogue.
5. **Degeneracy engine**: norm-multiplicativity drives distance coincidences.
6. **Pinwheel phenomenon**: dense (distance, direction) pairs.
7. **Topological tearing**: floating-point failure mode.
8. **Dimensional flow**: UV vs. IR dimensional regimes.

Together, these terms describe a coherent mathematical landscape: a **discrete
topological framework for aperiodic order** that doubles as a **toy model for
discrete quantum gravity** and a **testing ground for extremal combinatorial
geometry**. Each term names a specific phenomenon, and each phenomenon has both
a precise mathematical definition and a vivid geometric picture.

The project's distinctive intellectual move is the **multi-sheeted pivot itself**:
treating the pentagonal angular deficit not as an obstruction but as the **engine**
of a rich new combinatorial topology. Everything else in the framework follows from
that move.
