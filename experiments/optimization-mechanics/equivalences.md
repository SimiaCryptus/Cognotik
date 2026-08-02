# QQN Equivalences: Reproducing Classical Optimizers

## Overview

QQN is best understood as a **combiner** of four orthogonal components —
**gradient**, **oracle**, **search**, and **region** (see
[`algorithm.md`](algorithm.md)). A powerful consequence of this design is that
many classical optimization methods arise as _special cases_ of QQN under
particular configurations of these axes. This document catalogs those
equivalences, from the obvious to the subtle.

The central object is the quadratic path

```
d(t) = t(1 - t)(-∇f) + t²(-H∇f),   t ∈ [0, 1]
```

with endpoints `d(0) = 0`, tangent `d'(0) = -∇f`, and `d(1) = -H∇f`. By
choosing the oracle (which defines `-H∇f`), the search (which selects `t` and
the step), and the region (which projects each candidate), QQN collapses onto
a wide range of well-known algorithms.

---

## Summary Table

| Classical Method               | Oracle               | Search                            | Region              | Notes                                  |
| ------------------------------ | -------------------- | --------------------------------- | ------------------- | -------------------------------------- |
| Gradient Descent (fixed)       | any                  | `fixed` step, `t→0` regime        | `None`              | tangent is `-∇f`                       |
| Steepest Descent (line search) | any                  | line search restricted near `t=0` | `None`              | exact line min along `-∇f`             |
| L-BFGS                         | `lbfgs`              | line search reaching `t=1`        | `None`              | `d(1) = -H∇f`                          |
| Newton's Method                | exact-Hessian oracle | accept `t=1`                      | `None`              | local, well-conditioned; unimplemented |
| Momentum / Heavy-Ball          | `momentum`           | `fixed` (`t=1`)                   | `None`              | `d(1) = -(βv+(1-β)∇f)`                 |
| Barzilai-Borwein               | `secant`             | accept `t=1`                      | `None`              | scalar BB step                         |
| Anderson Acceleration          | `anderson`           | accept `t=1`                      | `None`              | least-squares residual mix             |
| Trust Region                   | `lbfgs`              | `t`-search w/ ρ-acceptance        | `TrustRegion`       | adaptive radius                        |
| OWL-QN                         | `lbfgs`              | line search                       | `OrthantRegion(l1)` | sparsity via orthant                   |
| Projected Gradient             | any                  | line search near `t=0`            | `BoxRegion`         | bound constraints                      |
| Conjugate Gradient             | no-op / CG oracle    | bisecting line search             | `None`              | see caveats below                      |

---

## 1. Trivial / Obvious Equivalences

These follow directly from the geometry of the path and the choice of search.

### 1.1 Gradient Descent

Because `d'(0) = -∇f`, the path's initial tangent is _exactly_ steepest
descent. Any configuration that keeps the selected `t` small (or uses a
`fixed` search with a small step) traverses only the gradient-dominated portion
of the path, where `d(t) ≈ -t·∇f`. With

```python
QQN(fun, line_search="fixed", line_search_options={"step": η})
```

and an oracle whose contribution is suppressed (or simply with `t` constrained
near 0), each step is `x ← x - η·∇f` — classical gradient descent with learning
rate `η`.

### 1.2 Steepest Descent (with line search)

Restricting the search to the neighborhood of `t = 0` and performing an exact
line minimization along `-∇f` reproduces classical steepest descent. The Armijo
/ backtracking searches already do this whenever the oracle direction is not
profitable: the line search retreats toward `t = 0`, where the path _is_ the
negative gradient.

### 1.3 L-BFGS

With the default oracle (`oracle="lbfgs"`) and a line search permitted to reach
`t = 1`, the accepted endpoint is `d(1) = -H∇f` — the L-BFGS quasi-Newton
direction computed by the two-loop recursion. When the line search selects
`t = 1`, QQN takes a pure L-BFGS step:

```python
QQN(fun, oracle="lbfgs", line_search="strong_wolfe")
```

With `region=None`, the L-BFGS history update and direction are byte-for-byte
equivalent to a standalone L-BFGS implementation at the `t = 1` endpoint
(numerically equivalent up to floating-point reordering). Note that QQN's
_curved_ path means the overall trajectory generally differs from standalone
L-BFGS — the path is slightly less optimized per step but more robust. This is
the baseline the rest of QQN extends.

### 1.4 Newton's Method

If the oracle returns the _exact_ Newton direction `-∇²f⁻¹∇f` (a custom
`Oracle` whose `direction` solves the Hessian system), then `d(1)` is the
Newton step. Accepting `t = 1` (e.g. via a Wolfe line search that admits the
full step near a well-conditioned minimum) reproduces Newton's method, with
QQN's gradient tangent providing globalization away from the basin of
convergence.

> **Caveat**: This holds only near a well-conditioned minimum. For non-convex
> `f` the Newton system need not be positive-definite, so `t = 1` may be an
> ascent direction; QQN's steepest-descent tangent at `t → 0` still provides
> globalization, but exact Newton recovery is local. This oracle is currently
> **unimplemented** (it is not practical at scale) and is listed for
> conceptual completeness.

---

## 2. Region-Induced Equivalences

Regions are pure projections applied inside the line search. They reshape the
_feasible_ path `d_R(t) = project_R(x, x + d(t)) - x`, and several classical
constrained / structured methods emerge from specific region choices.

### 2.1 Trust Region Methods

The **Trust-Region Sphere** region radially clips the step to
`‖x_new − x‖ ≤ Δ` and adapts `Δ` via the ratio `ρ = ared / pred`, where the
predicted reduction `pred(t) = -⟨∇f, d(t)⟩` comes from QQN's along-path
quadratic model. With

```python
QQN(fun, oracle="lbfgs", region=TrustRegion(radius=Δ₀, adaptive = True))
```

QQN reproduces a quasi-Newton trust-region method: the quadratic model is the
along-path model, the step is the projected path step, and the radius grows /
shrinks exactly as in classical trust-region updates
(`ρ < 0.25` shrinks, `ρ > 0.75` at the boundary grows). The line search's
sufficient-decrease test plays the role of the trust-region acceptance test.

### 2.2 OWL-QN (Orthant-Wise Limited-memory Quasi-Newton)

OWL-QN extends L-BFGS to L1-regularized objectives by restricting each step to
the orthant defined by the current point's signs. QQN reproduces it by pairing
the L-BFGS oracle with the **Orthant** region:

```python
QQN(fun, oracle="lbfgs", region=OrthantRegion(l1=λ))
```

The orthant projection zeros any coordinate whose sign would flip, and (when
`l1 > 0`) chooses the orthant for zero coordinates using the pseudo-gradient
`∇f + λ·sign(x)` — the OWL-QN convention. The L-BFGS curvature memory supplies
`d(1)`, the orthant region enforces the sign constraints along the projected
path, and the line search's sufficient-decrease check tolerates the
discontinuities the orthant projection introduces.

### 2.3 Projected (Bound-Constrained) Gradient Descent

Combining a gradient-dominated search with the **Box** region yields projected
gradient descent onto a box `[lo, hi]`:

```python
QQN(fun, region=BoxRegion(lo=lo, hi=hi), line_search="backtracking")
```

Each candidate `x + d(t)` is clipped elementwise into the feasible box before
evaluation, and the line search navigates the projected path. With the L-BFGS
oracle this generalizes to **projected quasi-Newton**.

---

## 3. Oracle-Induced Equivalences

The oracle defines the `t = 1` endpoint. Swapping it reproduces a family of
first-order and curvature methods.

### 3.1 Momentum / Heavy-Ball

The **Momentum** oracle returns `-(β·v + (1-β)·∇f)` at `t = 1`. With a `fixed`
search that accepts `t = 1`:

```python
QQN(fun, oracle="momentum", line_search="fixed",
    line_search_options={"step": 1.0})
```

each step uses the heavy-ball velocity direction, while QQN retains the raw
gradient at `t = 0` for globalization.

### 3.2 Barzilai-Borwein

The **Secant** oracle infers a scalar BB1 inverse-curvature step
`α = ⟨s, s⟩ / ⟨s, y⟩` from the previous iteration's realized secant and returns
`-α·∇f` at `t = 1`. Accepting `t = 1` reproduces the Barzilai-Borwein method:

```python
QQN(fun, oracle="secant", line_search="fixed")
```

This is matrix-free (`O(n)` / scalar state) and reuses information the
quadratic path already measured.

### 3.2b Anderson Acceleration

The **Anderson** oracle solves a small constrained least-squares problem over
a window of recent gradient residuals and returns the extrapolated mixing
direction at `t = 1`. Accepting `t = 1` reproduces Anderson/Pulay
acceleration — the variational ideal that L-BFGS approximates:

```python
QQN(fun, oracle="anderson", line_search="strong_wolfe")
```

Because the path retains `-∇f` at `t = 0`, an ill-conditioned residual solve
is automatically globalized by the gradient anchor.

### 3.3 Shampoo / Preconditioned Methods

The **Shampoo** oracle applies a structure-aware inverse-root preconditioner,
so `d(1)` is the Shampoo direction. With a line search reaching `t = 1`, QQN
reproduces Shampoo, while inheriting QQN's gradient-anchored globalization.

---

## 4. Search-Induced Equivalences (Subtle Cases)

These equivalences depend on the line-search strategy walking the path in a
particular way and require care.

### 4.1 Conjugate Gradient Descent

Conjugate Gradient (CG) builds search directions that are conjugate with
respect to the (implicit) Hessian, combining the current gradient with the
previous direction via a `β` coefficient (Fletcher-Reeves, Polak-Ribière,
etc.). QQN approaches CG along two complementary routes:

1. **CG-as-oracle**: Supply a custom `Oracle` whose `direction` returns the CG
   direction `-∇f + β·p_prev` (storing `p_prev` in oracle state and updating
   `β` from the gradient history). Then `d(1)` is the CG direction, and
   accepting `t = 1` with an exact line search reproduces nonlinear CG.

2. **No-op oracle + bisecting line search**: With an oracle that returns the
   pure gradient (so `d(1) = -∇f` and the path degenerates to scaled steepest
   descent), a **bisecting** (exact) line search performs an exact line
   minimization along `-∇f`. On a quadratic, exact line searches along
   successive negative gradients are _not_ conjugate by themselves — true CG
   requires the conjugate `β` correction — so this route reproduces **steepest
   descent with exact line search**, the degenerate `β = 0` case of CG, not
   full CG.

**Caveat**: The faithful CG reproduction is route (1), where conjugacy is
encoded _in the oracle_. Route (2) only recovers CG's degenerate special case.
A bisecting line search is the natural "exact line minimization" subroutine
that both CG and steepest descent share; QQN supplies the conjugacy via the
oracle, not the search.

---

## 5. Why These Equivalences Hold

The equivalences are not coincidental — they follow from three structural
facts:

1. **The tangent anchor (`d'(0) = -∇f`)** means every configuration contains
   gradient descent as the `t → 0` limit. This is why _any_ oracle still yields
   a globally convergent method.
2. **The endpoint (`d(1) = -H∇f`)** means whatever direction the oracle
   proposes is reachable exactly at `t = 1`. Pure oracle methods (L-BFGS,
   Newton, momentum, BB, Shampoo) are the `t = 1` corner.
3. **Projection inside the search** means constrained / structured methods
   (trust region, OWL-QN, projected gradient) arise by remapping the path
   without altering the gradient/oracle/search machinery.

Equivalently: QQN factors a classical optimizer into _(direction source,
step selection, feasibility projection)_ and lets each be chosen
independently. Classical methods are the points in this configuration space
where one or two axes are fixed to a canonical choice.

---

## 6. Equivalence Caveats

- **Exactness vs. regime.** Gradient descent and steepest descent are
  reproduced in the `t → 0` _regime_; with a non-trivial oracle the line search
  may still select a larger `t` if it yields more decrease. To force pure
  gradient behavior, suppress the oracle (e.g. a no-op oracle) or use a `fixed`
  search.
- **Line-search fidelity.** Newton, CG, and exact-line-search methods assume an
  _exact_ (or strong-Wolfe) line search. The default `armijo` / `backtracking`
  searches are inexact; for faithful reproduction of methods that depend on
  exact line minimization, use `strong_wolfe` or a bisecting search — while
  noting `strong_wolfe` can over-restrict the path step (see
  [`algorithm.md`](algorithm.md)).
- **Reparameterization invariance.** Rescaling the gradient or oracle direction
  does not change the geometric path, only the `t`-clock. Equivalences are
  stated up to this reparameterization.
- **Floating-point reordering.** "Byte-for-byte" claims (e.g. default L-BFGS)
  hold up to floating-point operation reordering.

---

## See Also

- [`algorithm.md`](algorithm.md) — the full QQN algorithm and the four axes.
- [`oracles.md`](oracles.md) — oracle abstraction (L-BFGS, momentum, secant,
  Shampoo, combinators).
- [`regions.md`](regions.md) — projective regions (box, orthant, trust region,
  combinators).
- [`spline_search.md`](spline_search.md) — the information-reusing spline
  refinement.
