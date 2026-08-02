# Affine IFS Point-Set Fitting

## Overview

We want to approximate a target point cloud in $\mathbb{R}^d$ by the
_orbit_ of the origin under all length-$N$ words in a small alphabet
of affine transforms. The transforms are then optimized (e.g. via
gradient descent) so that the generated orbit matches the target as
closely as possible under a nearest-neighbor metric.

This is closely related to the _inverse problem for iterated function
systems (IFS)_, but restricted to a fixed depth $N$ and with an
explicit (commutative-style) enumeration of all transform compositions.

---

## 1. Setup

### 1.1 Transforms

Let $K \in \mathbb{N}$ be the number of affine transforms.
Each transform $T_k : \mathbb{R}^d \to \mathbb{R}^d$ has the form

$$
T_k(x) \;=\; A_k\, x \;+\; b_k,
\qquad A_k \in \mathbb{R}^{d \times d},\; b_k \in \mathbb{R}^d.
$$

Collect all parameters into

$$
\theta \;=\; \{(A_k, b_k)\}_{k=1}^{K} \;\in\; \mathbb{R}^{K(d^2 + d)}.
$$

### 1.2 Generated point set

Fix a depth $N \in \mathbb{N}$ ("number of transforms applied").
For a multi-index (word) $w = (k_1, k_2, \dots, k_N) \in \{1,\dots,K\}^N$
define

$$
P_w(\theta) \;=\; \bigl(T_{k_N} \circ T_{k_{N-1}} \circ \cdots \circ T_{k_1}\bigr)(0).
$$

The full generated point set is

$$
\mathcal{P}_N(\theta) \;=\; \bigl\{\, P_w(\theta) \;:\; w \in \mathcal{W} \,\bigr\},
$$

where $\mathcal{W}$ is the indexing set of words.

#### Two flavors of enumeration

1. **Ordered words** (non-commutative):
   $\mathcal{W} = \{1,\dots,K\}^N$, $|\mathcal{W}| = K^N$.

2. **Commutative / multiset words**: only the _count_ of each transform
   matters, not the order. Then
   $$
   \mathcal{W} \;=\; \Bigl\{ (n_1,\dots,n_K) \in \mathbb{Z}_{\ge 0}^K \;:\; \sum_k n_k = N \Bigr\},
   \qquad |\mathcal{W}| = \binom{N + K - 1}{K - 1}.
   $$
   This is exact when the $T_k$ commute (e.g. pure translations, or
   commuting diagonal maps); otherwise it is an approximation that
   collapses orbits by transform-multiset.

The idea.md note says "every possible combination of transforms
(commutative)", which corresponds to flavor (2).

### 1.3 Target point set

Let

$$
\mathcal{Q} \;=\; \{q_1, \dots, q_M\} \subset \mathbb{R}^d
$$

be a given target point cloud.

---

## 2. Loss function

We measure the discrepancy between $\mathcal{P}_N(\theta)$ and
$\mathcal{Q}$ via a (squared) nearest-neighbor distance.

Define the one-sided Chamfer terms:

$$
D_{P \to Q}(\theta)
\;=\; \frac{1}{|\mathcal{P}_N|}
\sum_{p \in \mathcal{P}_N(\theta)}
\min_{q \in \mathcal{Q}} \; \|p - q\|_2^2,
$$

$$
D_{Q \to P}(\theta)
\;=\; \frac{1}{|\mathcal{Q}|}
\sum_{q \in \mathcal{Q}}
\min_{p \in \mathcal{P}_N(\theta)} \; \|p - q\|_2^2.
$$

The symmetric Chamfer loss is

$$
\mathcal{L}(\theta)
\;=\; \alpha\, D_{P \to Q}(\theta) \;+\; \beta\, D_{Q \to P}(\theta),
\qquad \alpha, \beta \ge 0.
$$

Common choice: $\alpha = \beta = 1$.
Using only $D_{P \to Q}$ ("forward" only) tends to let the model
collapse onto a subset of $\mathcal{Q}$; using only $D_{Q \to P}$
("backward" only) tends to leave stray generated points. The symmetric
form is preferred.

---

## 3. Optimization problem

$$
\theta^\star \;=\; \arg\min_{\theta} \; \mathcal{L}(\theta).
$$

### 3.1 Optional constraints / regularizers

To keep the system well-posed and the orbit bounded, we may add:

- **Contractivity:** $\|A_k\|_2 \le 1 - \varepsilon$ for all $k$
  (or a soft penalty $\sum_k \max(0, \|A_k\|_2 - (1-\varepsilon))^2$).
- **Parameter regularization:** $\lambda_A \sum_k \|A_k\|_F^2 + \lambda_b \sum_k \|b_k\|_2^2$.
- **Diversity:** penalize pairs $(T_i, T_j)$ that are too similar,
  e.g. $-\mu \sum_{i<j} \|(A_i,b_i) - (A_j,b_j)\|^2$ clipped.

---

## 4. Algorithm

```
Inputs:
    target points  Q      (M x d)
    num transforms K
    depth          N
    learning rate  eta
    iterations     T

Initialize:
    For k = 1..K:
        A_k <- small random matrix (e.g. 0.5 * I + noise)
        b_k <- small random vector

Enumerate words W:
    if commutative:
        W = all (n_1,...,n_K) with sum n_i = N         # multisets
    else:
        W = all (k_1,...,k_N) in {1..K}^N               # ordered

For t = 1..T:
    # Forward pass: build P_N(theta)
    P <- []
    for w in W:
        x <- 0
        for k_i in w:                                   # ordered case
            x <- A_{k_i} x + b_{k_i}
        P.append(x)

    # Loss
    For each p in P:    d_p <- min_q ||p - q||^2
    For each q in Q:    d_q <- min_p ||p - q||^2
    L <- mean(d_p) + mean(d_q)  (+ regularizers)

    # Backward pass
    Compute grad_theta L      (autodiff)
    theta <- theta - eta * grad_theta L

Return theta, P
```

### 4.1 Efficient commutative enumeration

In the commutative case, the orbit can be built incrementally:

Let $\mathcal{P}_N^{(c)} = \{\, T_1^{n_1} \cdots T_K^{n_K}(0) :
\sum n_i = N \,\}$ (interpreting the product in some fixed order,
which is exact iff the $T_k$ commute). Then

$$
\mathcal{P}_n^{(c)}
\;=\; \bigcup_{k=1}^{K} T_k\bigl(\mathcal{P}_{n-1}^{(c)}\bigr),
$$

with duplicates removed by multiset index. Implementation: index each
point by $(n_1,\dots,n_K)$, compute it from any parent
$(n_1,\dots,n_k-1,\dots,n_K)$ via $T_k$, and cache.

### 4.2 Nearest-neighbor computation

- For small $|\mathcal{P}_N|, |\mathcal{Q}|$: full pairwise distance
  matrix, $O(|\mathcal{P}_N|\,|\mathcal{Q}|\,d)$, fully differentiable.
- For larger sets: KD-tree / approximate NN for the _index lookup_,
  then recompute the chosen distances exactly so gradients still flow.

---

## 5. Notes and caveats

- **Commutativity assumption.** Generic affine maps do not commute, so
  "every possible combination (commutative)" is only literally correct
  for commuting families (e.g. all translations, or all maps sharing a
  common eigenbasis). For general $T_k$, flavor (2) should be
  understood as "index orbits by transform-multiset and pick a
  canonical application order" — an explicit modeling choice.
- **Size blow-up.** $|\mathcal{P}_N| = K^N$ (ordered) or
  $\binom{N+K-1}{K-1}$ (commutative). Both grow fast; keep $K, N$ small
  or subsample words.
- **Non-convexity.** The loss is highly non-convex; use multiple random
  restarts, annealing of $N$ (start small, grow), or curriculum on $\mathcal{Q}$.
- **Relation to IFS.** As $N \to \infty$ with contractive $T_k$, the
  orbit of $0$ densifies in the attractor of the IFS $\{T_k\}$. So this
  method is effectively fitting an IFS attractor to $\mathcal{Q}$,
  truncated at depth $N$.
