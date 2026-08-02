# M. Algorithmic and Computational — Detailed Reference

This document expands the **computational machinery** used to actually evaluate
reconnection, build adjacency graphs, and estimate dimensional/spectral exponents.
Exact symbolic arithmetic is essential — floating-point arithmetic is **fatal** for
pentagonal lattices, where small errors cascade into spurious topological tearing.

---

## Why Exact Arithmetic?

### The Problem with Floating Point

Consider two pentagonal lattice points

$p = a_1 + b_1 \phi, \quad q = a_2 + b_2 \phi.$

Testing $p = q$ requires comparing $a_1 = a_2$ and $b_1 = b_2$ as integers (or
rationals). In floating point, $\phi$ is stored as the truncated value
$1.6180339887\ldots$, and tiny errors in repeated multiplication can make
$p - q$ appear non-zero even when it is mathematically zero.

### Consequences for Topology

A spurious non-equality means an adjacency that **should exist** is missed: two
tiles that should be identified are treated as distinct. The result:

- **Spurious tree-like growth** in the adjacency graph.
- **Wrong dimensional estimates**.
- **Lost vortex holonomy** (sheet identifications break).
- **Topological tearing**: the multi-sheeted cover unravels into an infinite tree.

This is a **catastrophic failure mode** — and it is invisible to non-careful
code.

### The Solution: $\mathbb{Z}[\phi]$ Arithmetic

Represent each lattice element as a pair of integers $(a, b)$ with the
understanding $a + b\phi$. Arithmetic:

- Addition: $(a_1, b_1) + (a_2, b_2) = (a_1 + a_2, b_1 + b_2)$.
- Multiplication: $(a_1, b_1)(a_2, b_2) = (a_1 a_2 + b_1 b_2, a_1 b_2 + a_2 b_1 +
  b_1 b_2)$, using $\phi^2 = \phi + 1$.
- Equality: integer equality of pairs.

All exact. No floating point.

---

## Symbolic Computation

### Maxima

The project's reference implementation uses **Maxima**, a free open-source
computer algebra system descended from Macsyma. Files of the form
`experiment.mac` contain Maxima code.

**Maxima strengths**:

- Exact arithmetic over arbitrary algebraic number fields.
- Symbolic simplification of expressions in $\mathbb{Z}[\phi]$.
- Pattern matching for sheet-transition rule application.
- Reproducibility (no hidden numerical thresholds).

### General Symbolic Computation

Any CAS supporting exact algebraic arithmetic works:

- **SageMath** (recommended for production-scale computations).
- **Mathematica**.
- **SymPy** (Python; slower but accessible).
- **PARI/GP** (algebraic number theory specialist).

The key requirement: representation of $\mathbb{Z}[\phi]$ (or relevant ring) with
exact equality testing.

---

## The Adjacency Oracle Pattern

### Definition

An **adjacency oracle** is a function

$\text{neighbors}(\text{tile}) \to \text{list of (neighbor, edge)}$

that returns the adjacent tiles of a given tile, computed **on demand** using
exact arithmetic.

### Why an Oracle?

The expansion family is **infinite**. Materializing the entire family upfront is
impossible. The oracle lets us:

- Explore the family lazily via BFS or DFS from a seed.
- Stop at any time once enough tiles are generated.
- Memoize results to avoid recomputation.

### Implementation

For each tile, the oracle:

1. Applies each generator to obtain neighbor candidates.
2. Canonicalizes each neighbor (e.g., by reducing the affine representation).
3. Hashes the canonical form to find or create a unique tile ID.
4. Returns the list of (neighbor ID, edge label) pairs.

The canonicalization step is where exact arithmetic is critical.

---

## Spatial Hashing and Memoization

### Spatial Hashing

Tiles are hashed by their **canonical centroid** (in $\mathbb{Z}[\phi]$ coordinates).
The hash table maps centroid → tile ID. Lookup is $O(1)$ amortized.

For multi-sheeted tilings, the hash key includes the **sheet index**:

$\text{key} = (\text{centroid}_a, \text{centroid}_b, \text{sheet})$

so tiles on different sheets are distinct even if their projections coincide.

### Memoization

Function-level caching of expensive operations:

- Tile centroid computation.
- Generator application.
- Sheet-transition lookup.
- Distance computation (for distance-web analyses).

Memoization is essential for performance: typical expansion families have many
near-duplicate computations, and a small cache catches most of them.

### Lazy Graph Generation

The adjacency graph is built **lazily**: vertices and edges are created as the BFS
encounters them. This pattern (originating in cellular automata research) lets us
handle effectively infinite graphs with bounded memory.

---

## Sparse Linear Algebra

### Sparse Adjacency Matrix $A$

For an adjacency graph with $n$ vertices and average degree $d$, the matrix $A$ has
$O(nd)$ non-zero entries — sparse for typical $d \ll n$. Stored in CSR (compressed
sparse row) or COO (coordinate) format.

### Laplacian $\Delta = D - A$

The diagonal of $\Delta$ is the degree sequence; off-diagonals match $-A$.
Sparsity is preserved.

### Lanczos Algorithm

Iterative method for **extreme eigenvalues** of large sparse symmetric matrices.
Uses only matrix-vector products, so works with implicit / on-demand matrices.

Used to extract:

- The spectral gap $\lambda_1$ (smallest non-zero eigenvalue).
- The largest eigenvalues (top of the band).
- A few interior eigenvalues via shift-invert.

### Shift-Invert

Transform $M \mapsto (M - \sigma I)^{-1}$ to convert eigenvalues near $\sigma$
into extreme eigenvalues. Requires solving a sparse linear system at each Lanczos
step; uses sparse LU or iterative solvers (CG, MINRES).

Useful for studying the **density of states near $\lambda = 0$** (relevant for
spectral dimension).

---

## The Kernel Polynomial Method (KPM)

### Motivation

Computing all eigenvalues of a sparse matrix is $O(n^3)$ — infeasible for $n >
10^5$. We don't need all eigenvalues; we need the **density of states**
$\rho(\lambda)$.

### The Method

KPM approximates $\rho(\lambda)$ by its **Chebyshev expansion**:

$\rho(\lambda) \approx \sum_{k=0}^{N} \mu_k T_k(\lambda)$

where $T_k$ are Chebyshev polynomials and the moments $\mu_k = \frac{1}{n}
\text{Tr}(T_k(\hat{H}))$ are computed by **stochastic trace estimation**:

$\mu_k \approx \frac{1}{R} \sum_{r=1}^{R} v_r^T T_k(\hat{H}) v_r$

with random vectors $v_r$.

### Why It Works

Each Chebyshev moment requires only **matrix-vector products** with $\hat{H}$.
Total cost: $O(N \cdot R \cdot \text{nnz}(H))$, linear in matrix size. Scales to
$n \sim 10^7$ or more.

### Jackson Kernel

To suppress Gibbs oscillations at the spectrum edges, KPM uses a **Jackson kernel**
smoothing of the Chebyshev expansion. Produces a smooth, positive-definite
approximation to $\rho(\lambda)$.

### Output

A smooth estimate of $\rho(\lambda)$ on a fine grid. From this:

- Spectral gap from the lowest eigenvalue support.
- Spectral dimension $d_{\text{spec}}$ from $\rho(\lambda) \sim
  \lambda^{d_{\text{spec}}/2 - 1}$ as $\lambda \to 0$.

---

## Random Walk Simulation

### Walk Dimension Estimation

Simulate many random walks on the adjacency graph, starting from a seed. Record
mean-squared displacement (MSD):

$\langle \Delta x^2 \rangle(t) = \mathbb{E}[\|X_t - X_0\|^2]$

Fit $\langle \Delta x^2 \rangle \sim t^{2/d_w}$ to extract $d_w$.

### Implementation Notes

- Use the **adjacency oracle** to step lazily through the graph.
- Position $X_t$ uses **embedded coordinates** (in $\mathbb{Z}[\phi]^2$) for exact
  distance computation.
- Average over many walks (typically $10^3$–$10^5$).
- Restrict to **dimensional flow window**: discard short-time (mesh-dependent) and
  long-time (boundary-dominated) regimes.

### Return Probability

For spectral dimension via return probability:

$P_0(t) = \Pr[X_t = X_0]$

Fit $P_0(t) \sim t^{-d_{\text{spec}}/2}$. Note: simpler in theory but requires
long walks to get good statistics on return events.

---

## Power-Law Fitting

### The Procedure

Given data $\{(r_i, V_i)\}$ believed to follow $V \sim r^d$:

1. Log-transform: $\log V_i = d \log r_i + c$.
2. Linear regression on log-transformed data.
3. Extract slope $d$ and intercept $c$.

### Pitfalls

- **Crossover regimes**: $V(r)$ may have UV (small-$r$) and IR (large-$r$)
  regimes with different slopes. Fit only the **scaling window**.
- **Logarithmic corrections**: $V \sim r^d (\log r)^c$ is hard to distinguish
  from $V \sim r^{d'}$ over a limited range.
- **Finite-size effects**: small graphs deviate from asymptotic scaling.

Best practice: report slopes from **multiple windows** and verify stability.

---

## Vortex Detection and Rule Application

### Vortex Detection

For each vertex of the tiling, sum the angular contributions of incident tiles. If
the sum differs from $2\pi$ (after accounting for sheet topology), the vertex is
a **vortex** — and a sheet transition rule must be applied.

In the pentagonal case, vortices are precisely the vertices where **3 pentagons
meet** (deficit $36°$) — typically the bulk vertices of the tiling.

### The signed3 Rule

For each vortex with $k$ incident edges, label the edges cyclically with shifts
drawn from $\{-1, 0, +1\}$ such that:

1. The sum around the vortex equals the deficit (in fiber group units).
2. The labels distribute as evenly as possible (giving the $2/3$ edge fraction).

Implementation: a small state machine that walks around each vortex and assigns
labels.

### Consistency Checking

After all vortex rules are applied, the sheet structure must be **globally
consistent**: holonomy around every non-vortex loop must be trivial. This is
checked by verifying that the sheet labels on shared edges agree (modulo the fiber
group).

---

## Reproducibility and Verification

### Test Cases

Reference computations include:

- **Square tiling**: trivial Level 0; should give $d_{\text{eff}} = d_{\text{spec}}
  = 2$, $d_w = 2$.
- **Hexagonal tiling**: another Level 0 sanity check.
- **Sierpiński gasket**: known $d_{\text{eff}} = \log 3 / \log 2$, $d_{\text{spec}} =
  2 \log 3 / \log 5$. Validates the dimensional measurement pipeline.
- **Pentagon multi-sheeted tiling**: the actual target.

### Cross-Validation

Multiple independent estimates of each dimension should agree:

- $d_{\text{eff}}$ from BFS volume scaling.
- $d_{\text{spec}}$ from KPM density-of-states scaling.
- $d_{\text{spec}}$ from random-walk return probability.
- $d_w$ from random-walk MSD.
- Check the Alexander–Orbach relation $d_{\text{spec}} = 2 d_{\text{eff}} / d_w$.

If these disagree by more than a few percent, something is wrong (bug, finite-size
effects, fitting window mis-chosen).

---

## Performance Notes

### Typical Scales

- **Symbolic exploration**: $10^3$–$10^4$ tiles, full algebraic arithmetic.
- **Sparse adjacency analysis**: $10^4$–$10^6$ tiles, integer arithmetic with
  careful hashing.
- **KPM spectral analysis**: $10^5$–$10^7$ tiles, all matrix operations
  matrix-vector products.
- **Random walk dimensional estimation**: $10^4$–$10^6$ tiles, $10^3$–$10^5$
  independent walks.

### Bottlenecks

- **Adjacency oracle**: usually the rate-limiting step; aggressive memoization is
  critical.
- **Sheet-rule consistency checking**: $O(\text{vortex count})$ per pass; usually
  fast.
- **KPM moments**: parallelizable trivially (multiple random vectors).
- **Sparse linear algebra**: well-handled by scipy.sparse or equivalent.

---

## Summary: The Computational Pipeline

1. **Define the seed tile** $P_0$ with vertices in $\mathbb{Z}[\phi]$.
2. **Define generators** (edge reflections, half-turns, or face reflections).
3. **Define vortex rule** (e.g., signed3).
4. **Build adjacency oracle** with exact arithmetic and spatial hashing.
5. **BFS expansion** from seed; record tiles, edges, sheet labels.
6. **Build sparse adjacency** $A$ and Laplacian $\Delta = D - A$.
7. **Spectral analysis**: KPM for density of states, Lanczos for spectral gap.
8. **Geometric analysis**: BFS volume for $d_{\text{eff}}$, random walks for $d_w$.
9. **Cross-validate** via Alexander–Orbach.
10. **Report** dimensional flow (UV vs. IR exponents) and vortex holonomy
    statistics.

The whole pipeline is built on **exact symbolic arithmetic**. Without this
foundation, the pentagonal lattice's distinctive topology dissolves into numerical
noise.
