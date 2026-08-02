# H. Fractals and Self-Similarity — Detailed Reference

This document expands the **fractal-geometric** vocabulary referenced in the project.
Fractals provide canonical examples of non-integer dimension, anomalous diffusion, and
emergent geometry — all relevant comparisons for multi-sheeted tilings.

---

## What is a Fractal?

### Fractal

Informally: a **self-similar set with non-integer dimension**. Mandelbrot's heuristic
definition.

Formally: a set whose Hausdorff dimension exceeds its topological dimension. Many
examples are constructed via **iterated function systems** or as attractors of dynamical
systems.

Key features:

- **Self-similarity** at multiple scales.
- **Non-integer dimension** (typically).
- **Fine structure** at arbitrarily small scales.

### Self-Similarity

The property that a set $S$ contains scaled copies of itself:

$$S = \bigcup_i f_i(S)$$

for a family of contractions $f_i$. The **exact** self-similarity here can be weakened
to **statistical** or **approximate** self-similarity for random fractals or
quasiperiodic patterns.

---

## Iterated Function Systems

### Iterated Function System (IFS)

A **finite collection** $\{f_1, \ldots, f_k\}$ of **contractions** on a complete metric
space.

### Contraction Map

A map $f$ with **Lipschitz constant $< 1$**:

$$d(f(x), f(y)) \leq c \cdot d(x, y), \quad c < 1$$

Contractions shrink distances.

**Banach's fixed-point theorem** guarantees each contraction has a unique fixed point.
**Hutchinson's theorem** extends this to IFS:

There exists a **unique non-empty compact set** $K$ (the **attractor**) satisfying:

$$K = \bigcup_i f_i(K)$$

When the contractions are similarities (uniform scaling) with ratios $r_i$ satisfying the
**open set condition**, the Hausdorff dimension $d$ of the attractor is determined by:

$$\sum_i r_i^d = 1$$

For $k$ equal contractions of ratio $r$: $d = \log k / \log(1/r)$.

---

## Classical Fractal Examples

### Cantor Set

The **prototypical fractal**. Construction:

1. Start with $[0, 1]$.
2. Remove the open middle third $(1/3, 2/3)$.
3. Repeat on each remaining interval.

Limit: the Cantor set. IFS: $f_1(x) = x/3$, $f_2(x) = x/3 + 2/3$.

Dimension: $d = \log 2 / \log 3 \approx 0.631$.

Properties:

- Uncountable, but measure zero.
- Totally disconnected.
- Self-similar with two copies at scale 1/3.

### Sierpiński Gasket (Triangle)

A 2D fractal. Construction: start with a triangle, remove the middle triangle, repeat on
each of the remaining 3.

Dimension: $d = \log 3 / \log 2 \approx 1.585$.

Famous properties:

- Walk dimension: $d_w = \log 5 / \log 2 \approx 2.322$.
- Spectral dimension: $d_{\text{spec}} = 2 \log 3 / \log 5 \approx 1.365$.
- Anomalous diffusion: sub-diffusive, satisfies Alexander–Orbach exactly.

The Sierpiński gasket is the standard test case for non-integer dimension on graphs and
the canonical example where the three dimensions $(d_{\text{eff}}, d_w, d_{\text{spec}})$
all differ.

### Sierpiński Carpet

The 2D **square analogue** of the gasket. Construction: start with a square, divide into
$3 \times 3$ subsquares, remove the center, repeat.

Dimension: $d = \log 8 / \log 3 \approx 1.893$.

### Sierpiński Tetrahedron (Tetrix)

The 3D analogue of the gasket: 4 corner tetrahedra at half-scale, with the central
octahedron removed.

Dimension: **exactly 2** ($d = \log 4 / \log 2$).

Despite living in 3D and having uncountably many points, the tetrix has dimension 2 —
the same as a smooth surface. It's a "fractal surface" in 3-space.

### Menger Sponge

The 3D analogue of the Sierpiński carpet. Divide a cube into $27$ subcubes, remove the
**central cube and the 6 face-centers**, retaining 20 subcubes. Repeat.

Dimension: $d = \log 20 / \log 3 \approx 2.727$.

A "fractal volume" between 2 and 3 dimensions.

---

## Koch-Type Fractals

### Koch Snowflake / Koch Curve

The classic fractal curve. Construction:

1. Start with a line segment.
2. Replace the middle third with two sides of an equilateral triangle.
3. Repeat on each new segment.

Dimension: $d = \log 4 / \log 3 \approx 1.262$.

A 1D curve (topologically) embedded in 2D with non-integer dimension. Infinite length
but bounds finite area in the snowflake variant.

### Koch Tetrahedron / Koch Snowflake Surface

3D analogue: a tetrahedral surface where each triangular face has a smaller tetrahedron
attached at its center, recursively.

Dimension: $d = \log 6 / \log 3 \approx 1.631$. (Different sources give different
variants.)

---

## Dimension Concepts (Repeated for Emphasis)

### Box-Counting Dimension

For a set $S \subset \mathbb{R}^n$, let $N(\epsilon)$ be the minimum number of boxes of
side $\epsilon$ needed to cover $S$. Then:

$$d_{\text{box}} = \lim_{\epsilon \to 0^+} \frac{\log N(\epsilon)}{\log(1/\epsilon)}$$

Properties:

- Easy to compute numerically.
- Agrees with Hausdorff dimension for "nice" sets.
- Can disagree for highly irregular sets.

For self-similar IFS attractors satisfying the open set condition, all three
dimensions — box, Hausdorff, similarity — coincide.

---

## Why Fractals Matter for the Project

Multi-sheeted tilings are **not strictly self-similar** in the IFS sense — they don't
arise from iterated contractions of a fixed seed. But they share key features with
fractals:

1. **Possible non-integer effective/spectral dimensions** due to sheet structure.

2. **Anomalous diffusion** if vortex traps slow random walks: $d_w > 2$, as on
   Sierpiński gaskets.

3. **Multiple scales of structure**: from the tile scale (UV) through cluster scales to
   the asymptotic combinatorial structure (IR).

4. **Comparison templates**: numerical estimates of $(d_{\text{eff}}, d_w, d_{\text{spec}})$
   for the pentagonal cover can be compared against the **exact** values known for
   classical fractals like the Sierpiński gasket. If the pentagonal cover behaves
   "Sierpiński-like" in any regime, that's a striking finding.

5. **Quasi-self-similarity in Penrose / AKN tilings**: while not strictly self-similar,
   these quasiperiodic tilings exhibit **inflation/deflation symmetries** (multiplication
   by $\phi$) that are statistically self-similar — analogous to fractal scaling but
   with discrete scaling factors.

---

## A Hierarchy of Geometric Complexity

To organize the project's geometric landscape:

| Type                   | Example           | Dimensions                                       |
| ---------------------- | ----------------- | ------------------------------------------------ |
| Lattice                | $\mathbb{Z}^n$    | $d_{\text{eff}} = d_w/2 = d_{\text{spec}}/1 = n$ |
| Smooth manifold        | $\mathbb{R}^n$    | Same as lattice                                  |
| Fractal (self-similar) | Sierpiński gasket | All three differ, all non-integer                |
| Quasicrystal           | Penrose tiling    | All three = 2 (statistically)                    |
| Multi-sheeted tiling   | Pentagon cover    | **Subject of investigation**                     |
| Random graph           | Erdős–Rényi       | All three = "effectively infinite"               |

The project asks: **where does the multi-sheeted pentagon cover sit in this hierarchy?**
The answer determines whether it represents a fundamentally new class of emergent
geometry or fits within existing frameworks.

---

## Computational Considerations

Estimating fractal dimensions in practice involves:

1. **Box-counting** for static dimension: tile space with boxes of decreasing size,
   count occupied boxes, fit power law.

2. **BFS-ball growth** for $d_{\text{eff}}$ on graphs.

3. **MSD simulation** for $d_w$: run many random walks, average squared displacements.

4. **KPM / Lanczos** for $d_{\text{spec}}$: extract low-eigenvalue DOS scaling.

5. **Crossover detection**: plot local slopes vs. scale to identify regimes where
   different dimensions dominate.

For the multi-sheeted tilings, all four approaches are deployed in `experiment.mac` and
related computational tools, providing cross-checks via the Alexander–Orbach relation:

$$d_{\text{spec}} = \frac{2 d_{\text{eff}}}{d_w}$$

Consistency among independent estimates increases confidence in the reported exponents.
