# G. Spectral Graph Theory — Detailed Reference

This document expands the **spectral graph theory** vocabulary used in the project. The
spectrum of the graph Laplacian encodes diffusion, random-walk behavior, density of
states, and the spectral dimension — all central diagnostics of multi-sheeted tilings.

---

## The Matrices

### Adjacency Matrix $A$

The $n \times n$ matrix with $A_{ij} = 1$ if vertices $i$ and $j$ are adjacent, else 0.

For undirected graphs (the project's focus): $A$ is **symmetric**, hence has real
eigenvalues. The largest eigenvalue $\lambda_{\max}(A)$ controls the rate of exponential
growth of walk counts. The full spectrum encodes detailed structural information.

### Degree Matrix $D$

The diagonal matrix with $D_{ii} = \deg(v_i)$ = number of neighbors of vertex $v_i$.

### Graph Laplacian $\Delta = D - A$

The **discrete analogue of the Laplace operator**:

$$(\Delta f)(v) = \sum_{u \sim v} \big(f(v) - f(u)\big) = \deg(v) f(v) - \sum_{u \sim v} f(u)$$

Properties:

- Symmetric (for undirected graphs).
- Positive semi-definite: all eigenvalues $\geq 0$.
- $\Delta \mathbf{1} = 0$: constants are in the kernel.
- The **multiplicity of eigenvalue 0** equals the number of connected components.

### Sparse Matrix

A matrix with $O(n)$ non-zero entries (rather than $O(n^2)$). The adjacency matrix of a
graph with bounded degree is automatically sparse. **Sparse linear algebra** algorithms
(Lanczos, Arnoldi, conjugate gradient) exploit sparsity for efficiency.

### Sparse Linear Algebra

Algorithms designed for sparse matrices. Standard for large graphs:

- **Matrix-vector products** are $O(n)$ instead of $O(n^2)$.
- **Iterative eigensolvers** (Lanczos, Arnoldi) avoid forming the full eigendecomposition.
- **Shift-invert** transformations access specific spectral ranges.

---

## Spectrum and Eigenvalues

### Eigenvalue

A scalar $\lambda$ such that $Mv = \lambda v$ for some non-zero **eigenvector** $v$.

### Spectrum

The **set (or multiset) of eigenvalues** of a matrix or operator. For the graph Laplacian:

$$0 = \lambda_0 \leq \lambda_1 \leq \lambda_2 \leq \cdots \leq \lambda_{n-1}$$

### Spectral Gap

The **smallest non-zero Laplacian eigenvalue**, $\lambda_1$.

- $\lambda_1 = 0$: graph is disconnected.
- Large $\lambda_1$: graph is well-connected, "expander-like."
- $\lambda_1 \to 0$ as $n \to \infty$: typical for graphs that "look low-dimensional."

The spectral gap controls **mixing time** of random walks and the **isoperimetric**
properties of the graph.

### Cheeger Constant

The **graph isoperimetric ratio**:

$$h(G) = \min_S \frac{|\partial S|}{\min(|S|, |V \setminus S|)}$$

where $\partial S$ is the edge boundary. **Cheeger's inequality** relates it to the
spectral gap:

$$\frac{h(G)^2}{2 d_{\max}} \leq \lambda_1 \leq 2 h(G)$$

A small Cheeger constant indicates a **bottleneck**; the corresponding small $\lambda_1$
reflects slow mixing.

---

## Density of States

### Density of States (DOS) $\rho(\lambda)$

The **density of Laplacian eigenvalues** per unit eigenvalue interval per unit volume:

$$\rho(\lambda) = \frac{1}{n} \sum_{i=0}^{n-1} \delta(\lambda - \lambda_i)$$

For large graphs, viewed as a smooth function. The low-energy behavior controls
diffusion:

$$\rho(\lambda) \sim \lambda^{d_{\text{spec}}/2 - 1} \quad \text{as } \lambda \to 0^+$$

The **van Hove singularities** at band edges (peaks in DOS) are diagnostic of structural
features.

### Return Probability $P_0(t)$

The **probability that a continuous-time random walk** (with Laplacian generator)
**returns to its starting vertex** at time $t$:

$$P_0(t) = \langle v_0 | e^{-t\Delta} | v_0 \rangle = \int_0^\infty \rho(\lambda) e^{-\lambda t} d\lambda$$

Asymptotic scaling:

$$P_0(t) \sim t^{-d_{\text{spec}}/2}$$

defines the **spectral dimension**.

---

## Computational Methods

### Lanczos Algorithm

An **iterative method for extreme eigenvalues** of sparse symmetric matrices. Builds a
Krylov subspace and tridiagonalizes within it. Extracts the largest and smallest
eigenvalues efficiently.

Key features:

- Memory: $O(n)$ vectors stored.
- Per-iteration cost: one sparse matrix-vector product.
- Convergence: rapid for **isolated extreme eigenvalues**, slower for clustered.

### Shift-Invert

A **spectral transformation** giving access to eigenvalues near a target $\sigma$:

$$M \to (M - \sigma I)^{-1}$$

The eigenvalues closest to $\sigma$ become the largest in absolute value, hence amenable
to Lanczos/Arnoldi. Requires a sparse linear solver for $(M - \sigma I)x = b$.

### Kernel Polynomial Method (KPM)

A **matrix-free spectral approximation** technique. Expands the density of states in
**Chebyshev polynomials**:

$$\rho(\lambda) \approx \sum_{k=0}^{K} \mu_k T_k(\lambda)$$

The **Chebyshev moments** $\mu_k = \text{Tr}\, T_k(\Delta)$ are estimated via random-
vector trace estimators:

$$\mu_k \approx \frac{1}{R} \sum_{r=1}^R v_r^T T_k(\Delta) v_r$$

Cost: $O(n K)$ for $K$ Chebyshev polynomials and $R$ stochastic vectors. **Matrix-free**:
only needs to apply $\Delta$ to a vector.

Particularly suited to large-scale DOS estimation on graphs like the pentagonal
multi-sheeted adjacency graph.

### Chebyshev Polynomial

Orthogonal polynomial family with three-term recurrence:

$$T_0(x) = 1, \quad T_1(x) = x, \quad T_{k+1}(x) = 2x T_k(x) - T_{k-1}(x)$$

Defined on $[-1, 1]$. Used in KPM because:

- Stable three-term recurrence (no exponential growth of round-off).
- Optimal approximation properties for the relevant function spaces.
- Direct matrix-vector implementation via the recurrence.

---

## Random Walks

### Random Walk

A **stochastic process stepping uniformly to neighbors** on a graph. In discrete time:

$$\text{Pr}[X_{t+1} = u \mid X_t = v] = \frac{1}{\deg(v)} \text{ for } u \sim v$$

In continuous time: a Poisson clock of rate $\deg(v)$ at vertex $v$ triggers a jump to a
uniformly random neighbor. Generator is the graph Laplacian (suitably normalized).

### Mixing Time

The **time for a random walk to approach its stationary distribution**. Typically:

$$t_{\text{mix}} \sim \frac{\log n}{\lambda_1}$$

so spectral gap controls mixing.

For expander graphs, $t_{\text{mix}}$ is $O(\log n)$ — extremely fast. For low-dimensional
lattices, $t_{\text{mix}}$ is polynomial in $n$.

---

## Graph-Theoretic Properties

### Diameter

The **maximum graph distance** between any two vertices:

$$\text{diam}(G) = \max_{u, v} d(u, v)$$

For $\mathbb{Z}^n$ restricted to a ball: $\text{diam} \sim n^{1/d}$. The diameter scales
inversely with the effective dimension.

### Girth

The **length of the shortest cycle**. A graph with no cycles (tree) has $\text{girth} = \infty$.
**Large girth** is important for proving expansion / Ramanujan properties.

### Expander Graph

A graph with **spectral gap bounded away from 0** as graph size grows:

$$\lambda_1 \geq c > 0 \quad \text{(uniformly in } n\text{)}$$

Equivalently, a small Cheeger constant. Expanders have:

- Rapid random-walk mixing ($O(\log n)$).
- No small "bottlenecks."
- Good error-correcting code performance.

### Ramanujan Graph

A graph **saturating the Alon–Boppana bound** on spectral gap:

$$|\lambda_i(A)| \leq 2\sqrt{d-1} \quad \forall \lambda_i \neq \pm d$$

(for $d$-regular Ramanujan graphs). These are the "optimal expanders."

---

## Why Spectral Theory Drives the Diagnostics

For multi-sheeted tilings, the **spectrum of the graph Laplacian** encodes essentially
all the diffusion and dimensional properties:

1. **Effective dimension** $d_{\text{eff}}$: from BFS ball volumes (combinatorial, not
   spectral).

2. **Walk dimension** $d_w$: from MSD scaling (statistical sampling of random walk).

3. **Spectral dimension** $d_{\text{spec}}$: from low-eigenvalue DOS scaling, or
   equivalently return probability decay.

4. **Spectral gap** $\lambda_1$: signals connectivity and mixing.

5. **Density of states**: full spectrum gives a "fingerprint" of the graph; comparing
   $\rho(\lambda)$ for multi-sheeted pentagons vs. flat-pentagon attempts vs. Penrose
   tilings reveals structural differences.

The **Kernel Polynomial Method** is the project's workhorse for estimating
$\rho(\lambda)$ on large graphs, since it scales linearly and is matrix-free.

Spectral graph theory thus provides both the **theoretical framework** (Alexander–Orbach,
spectral gap, etc.) and the **practical computational tools** (Lanczos, KPM) for
diagnosing the geometric properties of multi-sheeted tilings.
