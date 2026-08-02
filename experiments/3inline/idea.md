# The No-Three-in-Line Problem: Mathematics, Algorithms, and Entropic Search

## 1. The Problem

The **no-three-in-line problem** asks: what is the maximum number of points that
can be placed on an n×n grid such that no three points are collinear — that is,
no three points lie on the same straight line? This deceptively simple constraint
eliminates not just horizontal, vertical, and diagonal alignments, but **every
possible line through the grid**, including lines of arbitrary rational slope.

### 1.1 Known Results

For small grids the answer is known exactly. A configuration achieving the
theoretical maximum of **2n** points is known for all n up to roughly 46, and
sporadically beyond. For large n the problem remains **open**:

- **Best known constructions** achieve roughly 2n points.
- **Upper bound:** a classical counting argument on lines shows you cannot place
  more than 2n points for sufficiently large n (each of the n columns can contain
  at most 2 points, immediately giving the 2n ceiling).
- **Conjectured asymptotics:** Guy and Kelly conjectured the true maximum density
  tends to roughly (π²/3)^(1/3) · n ≈ 1.874·n as n → ∞, suggesting one _cannot_
  reach 2n for all large n.

The difficulty is not merely combinatorial counting — it is the **geometry of
rational slopes** threading through a discrete lattice, and the long-range
frustration this geometry imposes on the configuration space.

### 1.2 Complexity

From a complexity-theoretic standpoint, the decision version of the problem —
"does there exist a configuration of k non-collinear points on the n×n grid?" —
lies in **NP**, but no polynomial-time algorithm is known, and the problem is not
currently known to be NP-hard. This intermediate status is itself informative: the
problem has enough structure to resist brute force, but enough irregularity to
resist clean algebraic solution.

## 2. Why a Continuous Relaxation?

The native formulation is discrete and combinatorial: choose a subset of the n²
lattice cells. Discrete search (backtracking, SAT, ILP) works for small n but
suffers from combinatorial explosion and long-range collinearity constraints that
couple distant cells.

Our approach **relaxes the lattice constraint**, letting points move continuously
in ℝ². We then design a potential landscape whose minima coincide with valid
no-three-in-line configurations. Gradient-based optimizers can then flow points
downhill, and the continuous geometry lets the optimizer "feel" near-collinear
frustration before it becomes a hard violation.

The key challenges this raises:

1. How do we make a _line_ (an infinite, continuous object) into a differentiable
   penalty?
2. How do we encode the rule "2 points on a line is fine, 3 is forbidden"?
3. How do we keep points snapping toward integer lattice positions while still
   allowing free continuous motion?

## 3. The Potential-Well Formulation

We consider a continuous optimization problem where each **line family** through
the grid (rows, columns, diagonals, and more general rational-slope lines) is
populated by a series of **line-centered potential wells**. Each well corresponds
to a candidate line; its energy contribution is controlled by a **population-fitness
response** function f(p), where p is the (soft) number of points lying on that line.

### 3.1 Population-Fitness Response

The defining feature of the well is its non-monotonic response to population:

- When a line holds **fewer than 2** points, the well is **attractive** — it pulls
  nearby points onto the line (rewarding alignment, which is needed to reach the
  2n density).
- When a line holds **exactly 2** points, the well is **neutral** — zero gradient,
  a stable plateau.
- When a line holds **3 or more** points, the well is **repulsive** — it pushes the
  excess points off the line (enforcing the constraint).

Formally, if p denotes the soft population of a line, we want a fitness f(p) such
that the _force_ (negative gradient w.r.t. point positions) changes sign at p = 2.
A convenient family:

```
f(p) = (p - 2)²           # zero-force minimum at p = 2
well_force ∝ -df/dp = -2(p - 2)
    p < 2  →  force > 0   (attractive)
    p = 2  →  force = 0   (neutral)
    p > 2  →  force < 0   (repulsive)
```

This `population-fitness-response` is **the strategy of interest** and the central
object we will experiment with. Variants to explore:

- Asymmetric responses (gentle attraction, steep repulsion past 2).
- Smooth "soft-max" populations vs. hard counts.
- Saturating wells so a single far-away point cannot over-attract.
- Temperature / annealing schedules on the well width.

### 3.2 Soft Population Count

To make p differentiable we replace hard membership with a kernel. For a line ℓ
with unit normal n̂ and offset b, the signed distance of point xᵢ is
dᵢ = n̂·xᵢ − b. A Gaussian kernel gives a soft membership:

```
wᵢ(ℓ) = exp(-dᵢ² / (2σ²))
p(ℓ) = Σᵢ wᵢ(ℓ)
```

σ controls the width of each well (how close a point must be to "count"). As σ → 0
the soft count approaches a hard count; annealing σ downward over the optimization
sharpens the constraint.

### 3.3 The Lattice (Grid-Snapping) Potential

To bias points toward integer lattice positions we add a periodic grid potential:

```
G(x) = Σ_dims [1 - cos(2π · coord)]
```

which has minima at every integer coordinate. Its weight is annealed up over time,
so points first explore freely and later crystallize onto the lattice.

### 3.4 Total Energy

```
E = Σ_lines  λ_line · f( p(line) )         # collinearity / population term
  + λ_grid · Σ_points G(xᵢ)                 # lattice snapping
  + λ_box  · Σ_points boundary(xᵢ)          # keep points inside [0, n-1]²
```

Points optimize their positions freely on ℝ², aligning with the grid and wells
wherever a valid solution exists. The optimizer finds a configuration that
minimizes E; a good minimum corresponds to many lattice-aligned points with no
line holding more than 2.

## 4. Which Lines Do We Track?

A grid admits infinitely many rational-slope lines, but only finitely many pass
through ≥ 2 lattice points within an n×n region. We enumerate **candidate lines**
by:

- All rows (n lines) and columns (n lines).
- All diagonals of slope ±1.
- More generally, lines through every pair of currently-active points (dynamically
  regenerated), which captures arbitrary rational slopes that actually matter for
  the present configuration.

A **dynamic line set** (rebuilt periodically from the current points) keeps the
energy tractable: we only penalize lines that are at risk of becoming triple-
occupied, rather than the combinatorially huge set of all possible lines.

## 5. Optimization

We use gradient-based optimizers provided by **TensorFlow.js**:

- **SGD / Momentum** — baseline, cheap, good with annealing.
- **Adam** — adaptive step sizes, robust to the multi-scale landscape.
- **L-BFGS** — quasi-Newton, strong for the final crystallization phase.

Suggested schedule:

1. **Explore** — wide σ, low grid weight, Adam. Points spread and find rough
   alignments.
2. **Anneal** — shrink σ, raise grid weight. Wells sharpen, lattice snapping
   strengthens.
3. **Crystallize** — small σ, high grid weight, L-BFGS polish to exact integers.

Because the landscape is non-convex and frustrated, we run **multiple random
restarts** and keep the best valid configuration. Entropic / temperature-style
noise (annealed) helps escape shallow local minima — hence "Entropic Search."

## 6. Validation

A continuous optimum must be **rounded and verified** discretely:

1. Round each point to the nearest lattice cell.
2. Remove duplicate occupancies.
3. Check every triple (or every pair-defined line) for exact collinearity using
   integer arithmetic (cross-product == 0).
4. Report the count of valid points and whether any line is triple-occupied.

The reported score is the number of placed points in a configuration with **zero**
collinear triples.

## 7. User Interface

The UI visualizes the optimization in real time:

- **Grid & points** — current continuous point positions overlaid on the n×n grid,
  with lattice cells marked.
- **Well highlighting** — each tracked line is colored by a **population/magnitude
  color scheme**:
  - Blue / cool → under-populated, attractive wells.
  - Green → neutral (population ≈ 2), satisfied.
  - Red / hot → over-populated, repulsive wells (violations).
- **Live metrics** — current energy, soft point count, number of violating lines,
  σ and grid-weight schedule values.
- **Controls** — choose optimizer (GD / Adam / L-BFGS), grid size n, σ schedule,
  restart, and play/pause/step.

## 8. Experimental Questions

1. Which population-fitness-response shape best balances reaching 2n density
   against avoiding triples?
2. Does annealing σ and the grid weight reliably crystallize valid integer
   solutions?
3. How does the dynamic line set scale with n, and where does it break down?
4. Can entropic noise push solutions past the typical local-minimum density toward
   the 2n frontier?
5. How do the continuous optima compare to known optimal discrete configurations
   for small n?
