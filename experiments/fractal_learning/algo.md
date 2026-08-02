# Efficient Commutative IFS Orbit via Binary Powers

## 1. Setting

We have $M$ affine transforms $T_1, \dots, T_M$ on $\mathbb{R}^d$,

$$
T_k(x) \;=\; A_k\, x \;+\; b_k,
\qquad A_k \in \mathbb{R}^{d\times d}, \; b_k \in \mathbb{R}^d.
$$

We **assume the $T_k$ commute** (e.g. they are all translations, all
share a common eigenbasis, or are otherwise simultaneously
diagonalizable). Under this assumption, the composition

$$
T_1^{n_1} \circ T_2^{n_2} \circ \cdots \circ T_M^{n_M}
$$

is independent of the order in which the $T_k$ are applied, and
depends only on the _exponent multiset_ $(n_1, \dots, n_M)$.

Fix a total depth $K \in \mathbb{N}$. The point set of interest is the
orbit of the origin under all length-$K$ commutative words:

$$
\mathcal{P}_K
\;=\;
\Bigl\{\, T_1^{n_1} \cdots T_M^{n_M}(0) \;:\;
    n_i \ge 0,\; \sum_{i=1}^{M} n_i = K \,\Bigr\}.
$$

Its size is

$$
|\mathcal{P}_K| \;=\; \binom{K + M - 1}{M - 1}.
$$

> **Note on naming.** In the previous draft we wrote "$K^M = N$".
> That is incorrect for the commutative case: the number of multisets
> of size $K$ over $M$ symbols is $\binom{K+M-1}{M-1}$, not $K^M$.
> $K^M$ would only count ordered words of length $M$ over an
> alphabet of size $K$. We keep $K$ = total depth and $M$ = number
> of basis transforms throughout.

---

## 2. Key idea: binary-power decomposition

For each transform $T_k$, define its iterates

$$
T_k^{n}(x) \;=\; A_k^{n}\, x \;+\; \Bigl(\sum_{j=0}^{n-1} A_k^{j}\Bigr) b_k.
$$

So $T_k^n$ is again affine, with

$$
A_k^{(n)} \;:=\; A_k^{n},
\qquad
b_k^{(n)} \;:=\; \Bigl(\sum_{j=0}^{n-1} A_k^{j}\Bigr) b_k.
$$

### 2.1 Binary powers

Write $n$ in binary: $n = \sum_{r=0}^{R} \varepsilon_r 2^r$,
with $\varepsilon_r \in \{0,1\}$ and $R = \lfloor \log_2 K \rfloor$.
Then

$$
T_k^{n} \;=\; \prod_{r : \varepsilon_r = 1} T_k^{2^r}.
$$

We precompute, for each $k = 1, \dots, M$ and each $r = 0, \dots, R$,
the affine map

$$
U_{k,r} \;:=\; T_k^{2^r},
\qquad
U_{k,r}(x) = A_{k,r}\, x + b_{k,r},
$$

via the doubling recursion

$$
\begin{aligned}
A_{k,0} &= A_k, & b_{k,0} &= b_k, \\
A_{k,r+1} &= A_{k,r}^{2}, &
b_{k,r+1} &= A_{k,r}\, b_{k,r} + b_{k,r}.
\end{aligned}
$$

(Derivation: $T_k^{2m} = T_k^{m} \circ T_k^{m}$, so if
$T_k^{m}(x) = Ax + b$, then $T_k^{2m}(x) = A(Ax+b) + b = A^2 x + (Ab+b)$.)

This takes $O(M \log K)$ matrix multiplications and is fully
differentiable in $\theta = \{(A_k, b_k)\}$.

### 2.2 Composing a single orbit point

Given an exponent vector $n = (n_1, \dots, n_M)$ with $\sum n_i = K$,
let the binary expansion of $n_k$ be $\{\varepsilon_{k,r}\}_r$. Then

$$
T_1^{n_1} \cdots T_M^{n_M}
\;=\;
\prod_{k=1}^{M} \;\prod_{r=0}^{R}
    U_{k,r}^{\varepsilon_{k,r}},
$$

a product of at most $M(R+1) = O(M \log K)$ precomputed affine maps
(commutativity lets us reorder freely). Applied to $0$:

$$
P_n \;=\; \Bigl(\prod_{k,r : \varepsilon_{k,r}=1} U_{k,r}\Bigr)(0).
$$

---

## 3. Efficient enumeration of the whole orbit

Naively iterating over all multisets and composing each in
$O(M\log K)$ costs

$$
O\!\Bigl(\binom{K+M-1}{M-1} \cdot M \log K\Bigr).
$$

We can do better by sharing work across multisets via dynamic
programming.

### 3.1 DP over multisets (recommended)

Order the transforms $1, 2, \dots, M$. For each prefix $k$ and each
partial budget $s \le K$, store the set of partial affine maps

$$
\mathcal{S}_{k, s}
\;=\;
\Bigl\{\, T_1^{n_1} \cdots T_k^{n_k} \;:\;
    n_i \ge 0,\; \sum_{i \le k} n_i = s \,\Bigr\}.
$$

Recursion:

$$
\mathcal{S}_{k, s}
\;=\;
\bigcup_{n_k = 0}^{s}
\Bigl\{\, U \circ T_k^{n_k} \;:\; U \in \mathcal{S}_{k-1,\, s - n_k} \,\Bigr\},
$$

with base case $\mathcal{S}_{0,0} = \{\mathrm{Id}\}$,
$\mathcal{S}_{0,s>0} = \emptyset$. The final orbit is

$$
\mathcal{P}_K \;=\; \{\, U(0) : U \in \mathcal{S}_{M, K} \,\}.
$$

Each entry of $\mathcal{S}_{k,s}$ is indexed by $(n_1,\dots,n_k)$ with
$\sum n_i = s$, so there are no duplicates by construction. The total
number of partial affine maps stored is

$$
\sum_{k=1}^{M} \sum_{s=0}^{K} \binom{s + k - 1}{k - 1}
\;=\; O\!\Bigl(M \cdot \binom{K+M-1}{M-1}\Bigr).
$$

Each map is built from a parent via one composition with some
$T_k^{n_k}$ (itself read off the binary-power table $U_{k,r}$), so
the total compute is dominated by the orbit size.

### 3.2 Lazy / point-only variant

If we don't actually need the affine map, only its image of $0$, we
can store points instead of maps. Define

$$
p_{(n_1,\dots,n_k)} \;=\; (T_1^{n_1} \cdots T_k^{n_k})(0),
$$

with recursion

$$
p_{(n_1,\dots,n_k)}
\;=\;
T_k^{n_k}\bigl(p_{(n_1,\dots,n_{k-1})}\bigr)
\;=\;
A_{k}^{n_k}\, p_{(n_1,\dots,n_{k-1})} + b_k^{(n_k)},
$$

where $A_k^{n_k}$ and $b_k^{(n_k)}$ are read from / assembled from the
binary-power table. This trades the cost of storing $d\times d$
matrices for storing $d$-vectors and is preferable when $d$ is large.

### 3.3 Doubling-over-budget variant

Another option is to mimic Section 2.1 at the orbit level: build
$\mathcal{P}_K$ from $\mathcal{P}_{K/2}$ by combining all pairs
$(n', n'')$ with $n' + n'' = K$ (component-wise) and dividing out
multiplicity. This is elegant but requires deduplication and is
usually no faster than 3.1 in practice.

---

## 4. Differentiability

Every step above is a composition of:

- matrix multiplications $A_{k,r+1} = A_{k,r}^2$,
- matrix–vector products $A x$,
- vector additions $A b + b$,
- a final application $U(0) = b_{\text{total}}$.

All are smooth in $\theta = \{(A_k, b_k)\}_{k=1}^{M}$, so the whole
orbit $\mathcal{P}_K(\theta)$ is a differentiable function of
$\theta$. Plug it into the Chamfer loss of `idea.md`:

$$
\mathcal{L}(\theta)
= \alpha\, D_{P \to Q}(\theta) + \beta\, D_{Q \to P}(\theta)
$$

and backprop. Memory cost is the dominant concern:
materializing all $\binom{K+M-1}{M-1}$ points plus their autograd
tape can be large. Mitigations:

- **Checkpoint** the DP layers $\mathcal{S}_{k,\cdot}$ and recompute
  on backward.
- **Mini-batch words.** Each step, sample a subset
  $\mathcal{W}_t \subset \mathcal{W}$ and evaluate the loss on
  $\{P_n : n \in \mathcal{W}_t\}$.
- **Anneal $K$.** Start with small $K$ (cheap, smooth landscape),
  grow as training proceeds.

---

## 5. Complexity summary

Let $S = \binom{K+M-1}{M-1}$ be the orbit size.

| Stage | Time | Space |
| -------------------------------- | ----------------------- | ----------------------- | ---------------------- | ---------- |
| Binary powers $U_{k,r}$ | $O(M \log K \cdot d^3)$ | $O(M \log K \cdot d^2)$ |
| DP over multisets (maps, §3.1) | $O(M\, S \cdot d^3)$ | $O(M\, S \cdot d^2)$ |
| DP over multisets (points, §3.2) | $O(M\, S \cdot d^2)$ | $O(M\, S \cdot d)$ |
| Chamfer loss vs $                | \mathcal{Q}             | =Q$ | $O(S \cdot Q \cdot d)$ | $O(S + Q)$ |

Compared with the naive "compose each word from scratch" approach
($O(S \cdot M \log K \cdot d^3)$), the DP saves a factor of
$\log K$ and, more importantly, reuses partial compositions across
sibling multisets.

---

## 6. Pseudocode

```
Inputs:
    A[1..M]   : list of d x d matrices
    b[1..M]   : list of d vectors
    K         : total depth
    M         : number of transforms

# ---- 1. Binary powers of each T_k ----
R = floor(log2(K))
for k in 1..M:
    Akr[k,0] = A[k]
    bkr[k,0] = b[k]
    for r in 1..R:
        Akr[k,r] = Akr[k,r-1] @ Akr[k,r-1]
        bkr[k,r] = Akr[k,r-1] @ bkr[k,r-1] + bkr[k,r-1]

# Helper: affine map for T_k^n using binary expansion of n.
def power_map(k, n):
    A_acc = I_d ; b_acc = 0_d
    r = 0
    while n > 0:
        if n & 1:
            # compose U_{k,r} after current:  (A_kr x + b_kr) then A_acc
            # i.e. new map x -> A_acc (A_kr x + b_kr) + b_acc
            b_acc = A_acc @ bkr[k,r] + b_acc
            A_acc = A_acc @ Akr[k,r]
        n >>= 1 ; r += 1
    return A_acc, b_acc

# ---- 2. DP over multisets (point-only variant, §3.2) ----
# S[k][s] : dict from tuple (n_1,...,n_k) with sum=s  ->  point in R^d
S = empty table
S[0][0] = { (): 0_vector }

for k in 1..M:
    for s in 0..K:
        S[k][s] = {}
        for n_k in 0..s:
            A_kn, b_kn = power_map(k, n_k)
            for (idx, p) in S[k-1][s - n_k].items():
                new_idx = idx + (n_k,)
                S[k][s][new_idx] = A_kn @ p + b_kn

# ---- 3. Final orbit ----
P_K = list of S[M][K].values()   # length = C(K+M-1, M-1)
return P_K
```

Notes:

- `power_map` can itself be memoized; in the DP loop $n_k$ ranges
  over $0..s$, so caching `power_map(k, n)` for each $(k,n)$ used is
  cheap.
- For autograd: keep all tensors (`Akr`, `bkr`, points in `S`) as
  leaves of the computation graph rooted at $\theta$; do not detach.
- For the map-valued variant (§3.1), replace points by
  `(A_acc, b_acc)` pairs and propagate via
  $(A', b') \circ (A, b) = (A' A,\; A' b + b')$.

---

## 7. Sanity checks

- **$M = 1$:** only one transform. Then $S = 1$ (single multiset
  $(K)$), and the algorithm reduces to computing $T_1^K(0)$ via
  binary exponentiation — the standard $O(\log K)$ method.
- **All $T_k$ pure translations** ($A_k = I$): then
  $T_1^{n_1}\cdots T_M^{n_M}(0) = \sum_k n_k b_k$, and the orbit is
  the set of lattice points $\sum n_k b_k$ on the simplex
  $\sum n_k = K$. Commutativity is exact; the DP collapses to summing
  precomputed $n_k b_k$.
- **Simultaneously diagonalizable $A_k = V D_k V^{-1}$:** transform
  to the eigenbasis once; in that basis each $A_k$ is diagonal and
  everything reduces to per-coordinate scalar IFS.

---

## 8. When commutativity fails

For generic affine $T_k$, the DP in §3.1 still runs but its output
depends on the chosen application order ($T_1$ innermost, then
$T_2$, ...). It then represents a _canonicalized_ orbit indexed by
multisets, not the true non-commutative orbit. This is the modeling
choice flagged in `idea.md §5`. To recover the full non-commutative
orbit one must enumerate ordered words ($K^M \to M^K$ in our
notation: $M^K$ ordered length-$K$ words), losing the DP savings.
