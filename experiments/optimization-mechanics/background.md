# Background and Motivation

This document provides the conceptual and historical background for the QQN
(Quadratic Quasi-Newton) algorithm. It situates QQN among the classical
optimization methods it generalizes and explains the central tension — between
_robustness_ and _speed_ — that motivates its design. For the full algorithm,
see [`algorithm.md`](algorithm.md); for the precise sense in which QQN reduces
to classical methods, see [`equivalences.md`](equivalences.md).

---

## The Central Tension: Robustness vs. Speed

Unconstrained smooth optimization is dominated by a long-standing trade-off
between two families of methods:

- **First-order methods** (gradient descent, momentum) use only `∇f(x)`. They
  are cheap, memory-light, and _robust_: the negative gradient is always a
  descent direction. But they converge slowly, especially on ill-conditioned
  problems where the loss landscape forms long, narrow valleys.

- **Second-order / quasi-Newton methods** (Newton, BFGS, L-BFGS) use curvature
  information `H ≈ ∇²f⁻¹` to take much larger, better-aimed steps. They converge
  _fast_ (superlinearly near a minimum) but are _fragile_: the quasi-Newton
  direction `-H∇f` is only guaranteed to be a descent direction when `H` is
  positive-definite, which can fail on non-convex objectives or when the
  curvature history is stale or degenerate.

The classical reconciliation is to **pick one direction and then run a line
search** along it. If the quasi-Newton direction is good, the line search
accepts a full step; if it is bad, the search backtracks. But this still commits
to a _single_ direction per iteration. When the oracle direction is poor, a
backtracking search along it can waste evaluations without ever exploring the
reliable gradient direction.

---

## The QQN Idea: Blend, Don't Choose

QQN's core insight is to refuse the binary choice. Instead of selecting either
the gradient or the quasi-Newton direction, it constructs a **continuous
quadratic path** that smoothly connects them:

```
d(t) = t(1 - t)(-∇f) + t²(-H∇f),   t ∈ [0, 1]
```

This single curve has three decisive properties (derived in
[`algorithm.md`](algorithm.md)):

- `d(0) = 0` — the path starts at the current iterate `x`.
- `d'(0) = -∇f` — the path _begins_ tangent to steepest descent, so it is
  guaranteed to decrease `f` for small `t` whenever `∇f ≠ 0`.
- `d(1) = -H∇f` — the path _ends_ exactly at the quasi-Newton (oracle) step.

The line search then walks `t ∈ [0, 1]` directly. Near `t = 0` the path _is_
gradient descent (robustness); near `t = 1` it _is_ the quasi-Newton step
(speed). The search discovers the right blend automatically, with no manual
tuning, and inherits global convergence from the gradient tangent while
retaining superlinear behavior when the oracle direction dominates.

> **The reframing**: QQN turns "which direction?" into "where on the curve?".
> The one-dimensional search over `t` replaces the discrete choice between two
> competing directions with a continuous, globally-anchored interpolation.

---

## Historical Lineage

QQN draws on several established threads in numerical optimization:

### Quasi-Newton Methods (BFGS / L-BFGS)

The Broyden–Fletcher–Goldfarb–Shanno (BFGS) method and its limited-memory
variant (L-BFGS, Nocedal & Wright, Algorithm 7.4) approximate the inverse
Hessian from a rolling history of gradient differences `(s, y)`. L-BFGS is the
default **oracle** in QQN — it supplies the `t = 1` endpoint via the two-loop
recursion. Crucially, QQN does _not_ require the L-BFGS direction to be a
descent direction on its own, because the gradient anchor at `t = 0` provides
globalization.

### Line Search Theory (Armijo / Wolfe)

Sufficient-decrease (Armijo) and curvature (Wolfe) conditions are the classical
guarantees that a line search makes genuine progress. In QQN the line search is
promoted from an implementation detail to a **first-class component**: it walks
the path, enforces descent, and — when strong Wolfe conditions are used — keeps
the L-BFGS curvature updates well-conditioned. QQN's descent and convergence
guarantees are explicitly _inherited from_ the line search's
sufficient-decrease test.

### Trust-Region Methods

Trust-region methods build a local quadratic model and restrict the step to a
region of trusted accuracy, adapting the radius via the ratio
`ρ = ared / pred`. QQN absorbs this idea as a **projective region**: the
Trust-Region Sphere clips the step and adapts its radius using QQN's along-path
predicted reduction (see [`regions.md`](regions.md)). The line search's
acceptance test plays the role of the trust-region acceptance test.

### Cubic Hermite Interpolation

Because every probe along `d(t)` yields _both_ a fitness value and a directional
derivative, QQN can fit a piecewise **cubic Hermite spline** to the path,
reusing gradient information that a naive line search discards. This is the
basis of the information-reusing **spline search**
(see [`spline_search.md`](spline_search.md)).

---

## Four Orthogonal Axes

A second organizing principle behind QQN is **separation of concerns**. A
classical optimizer entangles three decisions — what direction to move, how far
to step, and how to respect constraints. QQN factors these into four
conceptually orthogonal, independently swappable axes:

1. **Gradient** — the steepest-descent signal `-∇f`, anchoring the path tangent.
2. **Oracle** — the curvature-aware `t = 1` endpoint `-H∇f` (L-BFGS, momentum,
   secant, Anderson, Shampoo, …); see [`oracles.md`](oracles.md).
3. **Search** — the strategy that walks `t ∈ [0, 1]` and enforces sufficient
   decrease (backtracking/Armijo, strong Wolfe, Hager-Zhang, fixed), optionally
   refined by the spline; see [`algorithm.md`](algorithm.md).
4. **Region** — an optional projection `project_R(x, x + d(t))` that remaps each
   candidate onto a feasible/preferred set; see [`regions.md`](regions.md).

This factoring is what makes the equivalences in
[`equivalences.md`](equivalences.md) possible: many classical methods are simply
QQN with one or two axes fixed to a canonical choice. Gradient descent is the
`t → 0` regime; L-BFGS is the `t = 1` corner with the default oracle; trust
regions, OWL-QN, and projected gradient descent arise from region choices;
momentum, Barzilai-Borwein, and Anderson acceleration arise from oracle choices.

---

## Why JAX

The entire QQN implementation (`qqn-jax`) is written as pure, functional JAX so
that every component composes with `jit`, `vmap`, `pmap`, and `grad`. This means:

- **No host-side control flow** — all branching uses `jnp.where` / `lax.select`
  and loops use `lax.while_loop` / `lax.scan`, so the optimizer is fully
  traceable.
- **Batched optimization** — `vmap` over starting points runs many
  optimizations in parallel.
- **Differentiable optimization** — `grad` can differentiate end-to-end through
  `solver.run`, enabling meta-learning and bi-level setups.

Pure-functional design is not merely an implementation convenience; it is what
lets the four axes remain genuinely modular, threading all state through a
single immutable `QQNState`.

---

## Reading Guide

- [`algorithm.md`](algorithm.md) — the comprehensive algorithm reference: path
  geometry, the four axes, the solver loop, and theoretical guarantees.
- [`oracles.md`](oracles.md) — the oracle abstraction and concrete oracles.
- [`regions.md`](regions.md) — projective regions for feasibility/preference.
- [`spline_search.md`](spline_search.md) — the cubic Hermite spline line search.
- [`equivalences.md`](equivalences.md) — how QQN reproduces classical methods.
- [`notation.md`](notation.md) — symbol reference and disambiguation.
