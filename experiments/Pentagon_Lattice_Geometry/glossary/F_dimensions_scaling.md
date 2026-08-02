# F. Dimensions and Scaling — Detailed Reference

This document expands the **dimension-theoretic** vocabulary used in the project, with
emphasis on the multiple **non-equivalent notions of dimension** that arise in
combinatorial / fractal / disordered geometry, and the **scaling regimes** (UV/IR) and
**diffusion exponents** that characterize them.

---

## The Many Faces of Dimension

A naive intuition equates "dimension" with the number of coordinates. But on fractals
and complex graphs, several inequivalent definitions emerge:

| Dimension                  | What it measures                 | Typical formula                             |
| -------------------------- | -------------------------------- | ------------------------------------------- |
| Topological                | Local Euclidean structure        | Manifold dimension                          |
| Hausdorff                  | Measure-theoretic scaling        | Covering by small balls                     |
| Fractal / box-counting     | Geometric self-similarity        | $\log N / \log s$                           |
| Effective $d_{\text{eff}}$ | Combinatorial growth             | $V(r) \sim r^{d_{\text{eff}}}$              |
| Walk $d_w$                 | Random walk speed                | $\langle \Delta x^2 \rangle \sim t^{2/d_w}$ |
| Spectral $d_{\text{spec}}$ | Heat kernel / return probability | $P_0(t) \sim t^{-d_{\text{spec}}/2}$        |

For smooth manifolds, all of these agree. For fractals and complex tilings, they
typically differ — and the relationships among them encode deep structural information.

---

## Hausdorff and Fractal Dimensions

### Hausdorff Dimension

A **measure-theoretic dimension** defined by examining the asymptotic behavior of
covers by sets of small diameter:

$$\dim_H(S) = \inf \{s \geq 0 : H^s(S) = 0\}$$

where $H^s$ is the $s$-dimensional Hausdorff measure. This is the most refined notion of
dimension for general sets — agrees with the topological dimension on smooth manifolds
but can be non-integer.

### Fractal Dimension

The general term for **non-integer dimensions of self-similar sets**. For a set composed
of $N$ scaled copies of itself, each at scale $1/s$:

$$d_f = \frac{\log N}{\log s}$$

This is the **similarity dimension**, agreeing with Hausdorff dimension under reasonable
conditions (Moran's theorem).

### Box-Counting Dimension

Defined via counts of $\epsilon$-boxes needed to cover a set:

$$\dim_{\text{box}}(S) = \lim_{\epsilon \to 0} \frac{\log N(\epsilon)}{\log(1/\epsilon)}$$

where $N(\epsilon)$ is the minimum number of boxes of side $\epsilon$. Easier to compute
than Hausdorff but equal for "nice" sets.

---

## Combinatorial Dimensions on Graphs

### Effective Dimension $d_{\text{eff}}$

Also called **connectivity dimension**, this is the exponent governing the **growth of
breadth-first-search (BFS) balls**:

$$V(r) = |\{v : d(v, v_0) \leq r\}| \sim r^{d_{\text{eff}}}$$

as $r \to \infty$ on an infinite graph. For $\mathbb{Z}^n$: $d_{\text{eff}} = n$. For
random graphs near percolation: $d_{\text{eff}}$ can be non-integer.

This is the project's primary geometric dimension — what the graph "looks like" from far
away.

### Connectivity Dimension

Synonym for effective dimension. The term emphasizes that it depends only on the
**combinatorial structure** of the graph, not on any Euclidean embedding.

### Power-Law Growth

The scaling $V(r) \sim r^d$ that **defines** the effective dimension. The exponent $d$ is
extracted via a **log-log fit** of $V(r)$ vs $r$:

$$\log V(r) \approx d \log r + \text{const}$$

---

## Walk and Spectral Dimensions

### Walk Dimension $d_w$

The exponent in **mean-squared displacement** of a random walker on the graph:

$$\langle \Delta x^2 \rangle \sim t^{2/d_w}$$

For ordinary Brownian motion on $\mathbb{R}^n$ or a regular lattice: $d_w = 2$. On
fractals and disordered graphs, $d_w$ can deviate.

### Sub-Diffusion

Diffusion **slower than Brownian**, i.e., $d_w > 2$. Walker is "trapped" by geometric
bottlenecks. Common on fractals (e.g., Sierpiński gasket has $d_w \approx 2.32$).

### Super-Diffusion

Diffusion **faster than Brownian**, $d_w < 2$. Walker propagates ballistically due to
long-range correlations. Rare on graphs with bounded degree but possible in special
settings.

### Mean Squared Displacement (MSD)

The quantity

$$\langle \Delta x^2 \rangle_t = \mathbb{E}[(X_t - X_0)^2]$$

measured along a random walk. The slope of its log-log fit is $2/d_w$.

### Anomalous Diffusion

**Diffusion with $d_w \neq 2$**. The general phenomenon that random walks on fractals
and complex networks deviate from the classical Brownian scaling.

### Spectral Dimension $d_{\text{spec}}$

The exponent in the **return probability** of a random walk:

$$P_0(t) = \text{Prob}[X_t = X_0] \sim t^{-d_{\text{spec}}/2}$$

For Brownian motion on $\mathbb{R}^n$: $d_{\text{spec}} = n$. For fractals,
$d_{\text{spec}}$ can be non-integer and is often **smaller than $d_{\text{eff}}$**.

The spectral dimension also governs the **low-energy density of states** of the graph
Laplacian:

$$\rho(\lambda) \sim \lambda^{d_{\text{spec}}/2 - 1} \quad \text{as } \lambda \to 0$$

---

## The Alexander–Orbach Relation

### Alexander–Orbach Relation

A fundamental identity tying the three combinatorial dimensions together:

$$d_{\text{spec}} = \frac{2 d_{\text{eff}}}{d_w}$$

This relation holds for random walks on a wide class of homogeneous fractals and graphs.
Consequences:

- If $d_w = 2$ (ordinary diffusion): $d_{\text{spec}} = d_{\text{eff}}$.
- If $d_w > 2$ (sub-diffusion): $d_{\text{spec}} < d_{\text{eff}}$.
- $d_w < 2$ (super-diffusion): $d_{\text{spec}} > d_{\text{eff}}$.

The Alexander–Orbach relation is one of the project's key diagnostic tools: knowing any
two dimensions determines the third, providing consistency checks for numerical
estimates.

---

## Scaling Regimes

### UV (Ultraviolet)

The **short-distance / short-time regime**:

- Small spatial separation.
- Short walk times.
- Large momentum / high frequency in Fourier picture.
- Probes the **microscopic structure** of the graph (individual tiles, local
  connectivity).

### IR (Infrared)

The **long-distance / long-time regime**:

- Large spatial separation.
- Long walk times.
- Small momentum / low frequency.
- Probes **macroscopic** / **emergent** structure.

The UV and IR can have **different effective dimensions** — a phenomenon called
**dimensional flow**.

### Dimensional Flow

**Scale-dependent dimension**: the effective dimension depends on the length/time scale
of observation. The UV value differs from the IR value.

Examples:

- Causal Dynamical Triangulation: $d_{\text{spec}} \to 2$ in UV, $d_{\text{spec}} \to 4$
  in IR.
- Asymptotic safety: similar UV $\to$ IR running.
- Pentagonal tilings (in the project): possibly $d_{\text{spec}}$ flow between $\sim 1.5$
  and $\sim 2$.

### Dimensional Reduction

A specific form of dimensional flow where the **effective dimension decreases at long
scales** or in the UV. Observed in:

- CDT quantum gravity: 4D macroscopic, ~2D microscopic.
- Asymptotic safety: 2D UV fixed point.
- Holographic renormalization: bulk dimension reduces to boundary dimension at IR.

The conjecture is that **graphs with non-trivial topology** (sheet structure) can
naturally exhibit dimensional reduction, providing a discrete model of quantum-gravity
behavior.

---

## Universality Classes

### Universality Class

A **set of systems sharing scaling exponents** — i.e., having the same $d_{\text{eff}}$,
$d_w$, $d_{\text{spec}}$ and the same critical scaling laws. Universality means
microscopic details don't matter for large-scale behavior; only symmetry, dimension, and
range of interaction matter.

The **renormalization group** (see Section J) formalizes this: a universality class is a
fixed point of the RG flow.

---

## Power-Law Fitting in Practice

To estimate any of these exponents from data $(r, V(r))$ or $(t, \langle \Delta x^2 \rangle)$:

1. **Take logs**: convert $V \sim r^d$ to $\log V \sim d \log r + c$.
2. **Linear regression** in log-log coordinates.
3. **Estimate exponent** from slope.

Pitfalls:

- **Boundary effects**: small $r$ probes UV, may not represent asymptotic IR.
- **Finite-size effects**: large $r$ saturates if the graph is finite.
- **Crossover regimes**: if the system exhibits dimensional flow, a single power law
  doesn't fit the entire range.

The **dimensional flow** itself can be visualized as a plot of "local slope" of log-log
data, showing how the apparent exponent runs with scale.

---

## Connection to the Project

The pentagonal multi-sheeted tilings raise fundamental questions:

1. **What is $d_{\text{eff}}$?** Likely 2 (the BFS ball grows polynomially with exponent
   2, as expected for any planar-like graph), but with possible logarithmic corrections.

2. **What is $d_w$?** The sheet structure may slow random walks — vortices trap walkers
   that wind around them multiple times. If $d_w > 2$, the system **sub-diffuses**.

3. **What is $d_{\text{spec}}$?** By Alexander–Orbach, $d_{\text{spec}} = 2 d_{\text{eff}} / d_w$.
   Sub-diffusion would give $d_{\text{spec}} < 2$, mimicking **dimensional reduction**.

4. **Is there dimensional flow?** The sheet topology may make UV and IR dimensions
   differ — a discrete combinatorial echo of quantum gravity's dimensional flow.

These questions are central to the project's broader physics motivations and connect
multi-sheeted tilings to **emergent geometry** in quantum gravity and condensed-matter
physics.
