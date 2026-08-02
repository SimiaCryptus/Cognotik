# Appendix A: Problem Family vs Optimizer Family Comparison Matrix

```{=latex}
\input{results/latex/family_vs_family_matrix.tex}
```

# Appendix B: Theoretical Foundations and Proofs

## B.1 Algorithm Derivation

### B.1.1 The Direction Combination Problem

Consider the fundamental problem of combining multiple optimization directions. Given:

- Gradient direction: $-\nabla f(\mathbf{x})$ providing guaranteed descent
- Quasi-Newton direction: $\mathbf{d}_{\text{QN}}$ offering potential superlinear convergence

We seek a principled method to combine these directions that:

1. Guarantees descent from any starting point
2. Smoothly interpolates between the directions
3. Requires no additional hyperparameters
4. Maintains computational efficiency

### B.1.2 Geometric Formulation

We formulate direction combination as a boundary value problem in parametric space. Consider a parametric curve $\mathbf{d}: [0,1] \rightarrow \mathbb{R}^n$ satisfying:

1. **Initial position**: $\mathbf{d}(0) = \mathbf{0}$
2. **Initial tangent**: $\mathbf{d}'(0) = -\nabla f(\mathbf{x})$ (ensures descent)
3. **Terminal position**: $\mathbf{d}(1) = \mathbf{d}_{\text{L-BFGS}}$

The minimal polynomial satisfying these constraints is quadratic:
$$\mathbf{d}(t) = \mathbf{a}t^2 + \mathbf{b}t + \mathbf{c}$$

Applying boundary conditions:

- From condition 1: $\mathbf{c} = \mathbf{0}$
- From condition 2: $\mathbf{b} = -\nabla f(\mathbf{x})$
- From condition 3: $\mathbf{a} + \mathbf{b} = \mathbf{d}_{\text{L-BFGS}}$

Therefore: $\mathbf{a} = \mathbf{d}_{\text{L-BFGS}} + \nabla f(\mathbf{x})$

This yields the canonical QQN path:
$$\mathbf{d}(t) = t(1-t)(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$$

## B.2 Convergence Analysis

### B.2.1 Universal Descent Property

**Lemma B.1** (Universal Descent): For any direction $\mathbf{d}_{\text{L-BFGS}} \in \mathbb{R}^n$, the QQN path satisfies:
$$\mathbf{d}'(0) = -\nabla f(\mathbf{x})$$

_Proof_: Direct differentiation of $\mathbf{d}(t) = t(1-t)(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$ gives:
$$\mathbf{d}'(t) = (1-2t)(-\nabla f) + 2t\mathbf{d}_{\text{L-BFGS}}$$

Evaluating at $t=0$: $\mathbf{d}'(0) = -\nabla f(\mathbf{x})$. $\square$
**Theorem B.1** (Descent Property): For any $\mathbf{d}_{\text{L-BFGS}}$, there exists $\bar{t} > 0$ such that $\phi(t) = f(\mathbf{x} + \mathbf{d}(t))$ satisfies $\phi(t) < \phi(0)$ for all $t \in (0, \bar{t}]$.

_Proof_: Since $\mathbf{d}'(0) = -\nabla f(\mathbf{x})$:
$$\phi'(0) = \nabla f(\mathbf{x})^T(-\nabla f(\mathbf{x})) = -\|\nabla f(\mathbf{x})\|^2 < 0$$

By continuity of $\phi'$ (assuming $f$ is continuously differentiable), there exists $\bar{t} > 0$ such that $\phi'(t) < 0$ for all $t \in (0, \bar{t}]$. By the fundamental theorem of calculus:
$$\phi(t) - \phi(0) = \int_0^t \phi'(s) ds < 0$$

for all $t \in (0, \bar{t}]$. $\square$

### B.2.2 Global Convergence Analysis

**Theorem B.2** (Global Convergence): Under standard assumptions:

1. $f: \mathbb{R}^n \rightarrow \mathbb{R}$ is continuously differentiable
2. $f$ is bounded below: $f(\mathbf{x}) \geq f_{\text{inf}} > -\infty$
3. $\nabla f$ is Lipschitz continuous with constant $L > 0$
4. The univariate optimization finds a point satisfying the Armijo condition

QQN generates iterates satisfying:
$$\liminf_{k \to \infty} \|\nabla f(\mathbf{x}_k)\| = 0$$

_Proof_: We establish convergence through a descent lemma approach.

**Step 1: Monotonic Decrease**

By Theorem B.1, each iteration produces $f(\mathbf{x}_{k+1}) < f(\mathbf{x}_k)$ whenever $\nabla f(\mathbf{x}_k) \neq \mathbf{0}$.

**Step 2: Sufficient Decrease**

Define $\phi_k(t) = f(\mathbf{x}_k + \mathbf{d}_k(t))$. Since $\phi_k'(0) = -\|\nabla f(\mathbf{x}_k)\|^2 < 0$, by the Armijo condition, there exists $c_1 \in (0, 1)$ and $\bar{t} > 0$ such that:
$$\phi_k(t) \leq \phi_k(0) + c_1 t \phi_k'(0) = f(\mathbf{x}_k) - c_1 t \|\nabla f(\mathbf{x}_k)\|^2$$

for all $t \in (0, \bar{t}]$.

**Step 3: Quantifying Decrease**

Using the descent lemma with Lipschitz constant $L$:
$$f(\mathbf{x}_{k+1}) \leq f(\mathbf{x}_k) + \nabla f(\mathbf{x}_k)^T \mathbf{d}_k(t_k^*) + \frac{L}{2}\|\mathbf{d}_k(t_k^*)\|^2$$

For the quadratic path with $t_k^* \in (0, \bar{t}]$:
$$\|\mathbf{d}_k(t)\|^2 = \|t(1-t)(-\nabla f(\mathbf{x}_k)) + t^2\mathbf{d}_{\text{L-BFGS}}\|^2$$

$$\leq 2t^2(1-t)^2\|\nabla f(\mathbf{x}_k)\|^2 + 2t^4\|\mathbf{d}_{\text{L-BFGS}}\|^2$$

For small $t$, the gradient term dominates, giving:
$$f(\mathbf{x}_k) - f(\mathbf{x}_{k+1}) \geq c\|\nabla f(\mathbf{x}_k)\|^2$$

for some $c > 0$ independent of $k$.

**Step 4: Summability**

Since $f$ is bounded below and decreases monotonically:
$$\sum_{k=0}^{\infty} [f(\mathbf{x}_k) - f(\mathbf{x}_{k+1})] = f(\mathbf{x}_0) - \lim_{k \to \infty} f(\mathbf{x}_k) < \infty$$

Combined with Step 3:
$$\sum_{k=0}^{\infty} \|\nabla f(\mathbf{x}_k)\|^2 < \infty$$

**Step 5: Conclusion**
The summability of $\|\nabla f(\mathbf{x}_k)\|^2$ implies $\liminf_{k \to \infty} \|\nabla f(\mathbf{x}_k)\| = 0$. $\square$

### B.2.3 Local Superlinear Convergence

**Theorem B.3** (Local Superlinear Convergence): Let $\mathbf{x}^*$ be a local minimum with $\nabla f(\mathbf{x}^*) = \mathbf{0}$ and $\nabla^2 f(\mathbf{x}^*) = H^* \succ 0$. Assume:

1. $\nabla^2 f$ is Lipschitz continuous in a neighborhood of $\mathbf{x}^*$
2. The L-BFGS approximation satisfies the Dennis-Moré condition:

$$\lim_{k \to \infty} \frac{\|(\mathbf{H}_k - (H^*)^{-1})(\mathbf{x}_{k+1} - \mathbf{x}_k)\|}{\|\mathbf{x}_{k+1} - \mathbf{x}_k\|} = 0$$

Then QQN converges superlinearly: $\|\mathbf{x}_{k+1} - \mathbf{x}^*\| = o(\|\mathbf{x}_k - \mathbf{x}^*\|)$.

_Proof_: We analyze the behavior near the optimum.

**Step 1: Neighborhood Properties**

By continuity of $\nabla^2 f$, there exists a neighborhood $\mathcal{N}$ of $\mathbf{x}^*$ and constants $0 < \mu \leq L$ such that:
$$\mu \mathbf{I} \preceq \nabla^2 f(\mathbf{x}) \preceq L \mathbf{I}, \quad \forall \mathbf{x} \in \mathcal{N}$$

**Step 2: Optimal Parameter Analysis**

Define $\phi(t) = f(\mathbf{x}_k + \mathbf{d}(t))$ where $\mathbf{d}(t) = t(1-t)(-\nabla f(\mathbf{x}_k)) + t^2\mathbf{d}_{\text{L-BFGS}}$.
The first derivative is:
$$\phi'(t) = \nabla f(\mathbf{x}_k + \mathbf{d}(t))^T[(1-2t)(-\nabla f(\mathbf{x}_k)) + 2t\mathbf{d}_{\text{L-BFGS}}]$$

The second derivative is:
$$\phi''(t) = [(1-2t)(-\nabla f(\mathbf{x}_k)) + 2t\mathbf{d}_{\text{L-BFGS}}]^T \nabla^2 f(\mathbf{x}_k + \mathbf{d}(t))[(1-2t)(-\nabla f(\mathbf{x}_k)) + 2t\mathbf{d}_{\text{L-BFGS}}]$$

$$+ \nabla f(\mathbf{x}_k + \mathbf{d}(t))^T[-2(-\nabla f(\mathbf{x}_k)) + 2\mathbf{d}_{\text{L-BFGS}}]$$

At $t = 1$:
$$\phi'(1) = \nabla f(\mathbf{x}_k + \mathbf{d}_{\text{L-BFGS}})^T \mathbf{d}_{\text{L-BFGS}}$$

Using Taylor expansion:
$$\nabla f(\mathbf{x}_k + \mathbf{d}_{\text{L-BFGS}}) = \nabla f(\mathbf{x}_k) + \nabla^2 f(\mathbf{x}_k)\mathbf{d}_{\text{L-BFGS}} + O(\|\mathbf{d}_{\text{L-BFGS}}\|^2)$$

Since $\mathbf{d}_{\text{L-BFGS}} = -\mathbf{H}_k\nabla f(\mathbf{x}_k)$:
$$\nabla f(\mathbf{x}_k + \mathbf{d}_{\text{L-BFGS}}) = [\mathbf{I} - \nabla^2 f(\mathbf{x}_k)\mathbf{H}_k]\nabla f(\mathbf{x}_k) + O(\|\nabla f(\mathbf{x}_k)\|^2)$$

By the Dennis-Moré condition, as $k \to \infty$:
$$\|\mathbf{I} - \nabla^2 f(\mathbf{x}_k)\mathbf{H}_k\| \to 0$$

Therefore:
$$\phi'(1) = o(\|\nabla f(\mathbf{x}_k)\|^2)$$

**Step 3: Optimal Parameter Convergence**

Since $\phi'(0) = -\|\nabla f(\mathbf{x}_k)\|^2 < 0$ and $\phi'(1) = o(\|\nabla f(\mathbf{x}_k)\|^2)$, by the intermediate value theorem and the fact that $\phi$ is strongly convex near $t = 1$ (due to positive definite Hessian), the minimizer satisfies:
$$t_k^* = 1 + o(1)$$

**Step 4: Convergence Rate**

With $t_k^* = 1 + o(1)$:
$$\mathbf{x}_{k+1} = \mathbf{x}_k + \mathbf{d}(t_k^*) = \mathbf{x}_k + (1 + o(1))\mathbf{d}_{\text{L-BFGS}} + o(\|\mathbf{d}_{\text{L-BFGS}}\|)$$

$$= \mathbf{x}_k - \mathbf{H}_k\nabla f(\mathbf{x}_k) + o(\|\nabla f(\mathbf{x}_k)\|)$$

By standard quasi-Newton theory with the Dennis-Moré condition:

$$\|\mathbf{x}_{k+1} - \mathbf{x}^*\| = o(\|\mathbf{x}_k - \mathbf{x}^*\|)$$

establishing superlinear convergence. $\square$

## B.3 Robustness Analysis

### B.3.1 Graceful Degradation

**Theorem B.4** (Graceful Degradation): Let $\theta_k$ be the angle between $-\nabla f(\mathbf{x}_k)$ and $\mathbf{d}_{\text{L-BFGS}}$. If $\theta_k > \pi/2$ (obtuse angle), then the optimal parameter satisfies $t^* \in [0, 1/2]$, ensuring gradient-dominated steps.

_Proof_: When $\theta_k > \pi/2$, we have $\nabla f(\mathbf{x}_k)^T \mathbf{d}_{\text{L-BFGS}} > 0$.

The derivative of our objective along the path is:
$$\frac{d}{dt}f(\mathbf{x}_k + \mathbf{d}(t)) = \nabla f(\mathbf{x}_k + \mathbf{d}(t))^T \mathbf{d}'(t)$$

At $t = 1/2$:
$$\mathbf{d}'(1/2) = -\frac{1}{2}\nabla f(\mathbf{x}_k) + \mathbf{d}_{\text{L-BFGS}}$$

For small steps from $\mathbf{x}_k$:
$$\nabla f(\mathbf{x}_k + \mathbf{d}(1/2)) \approx \nabla f(\mathbf{x}_k)$$

Therefore:
$$\left.\frac{d}{dt}f(\mathbf{x}_k + \mathbf{d}(t))\right|_{t=1/2} \approx \nabla f(\mathbf{x}_k)^T[-\frac{1}{2}\nabla f(\mathbf{x}_k) + \mathbf{d}_{\text{L-BFGS}}]$$

$$= -\frac{1}{2}\|\nabla f(\mathbf{x}_k)\|^2 + \nabla f(\mathbf{x}_k)^T\mathbf{d}_{\text{L-BFGS}} > 0$$

when $\nabla f(\mathbf{x}_k)^T\mathbf{d}_{\text{L-BFGS}} > \frac{1}{2}\|\nabla f(\mathbf{x}_k)\|^2$.

This implies the function increases beyond $t = 1/2$, so the univariate optimization will find $t^* \leq 1/2$, giving:

$$\mathbf{x}_{k+1} \approx \mathbf{x}_k + t^*(1-t^*)(-\nabla f(\mathbf{x}_k))$$

Since $t^* \leq 1/2$, we have $t^*(1-t^*) \geq t^*(1/2)$, ensuring a gradient-dominated step. $\square$

### B.3.2 Stability Under Numerical Errors

**Theorem B.5** (Numerical Stability): Let $\tilde{\mathbf{d}}_{\text{L-BFGS}} = \mathbf{d}_{\text{L-BFGS}} + \boldsymbol{\epsilon}$ where $\boldsymbol{\epsilon}$ represents numerical errors with $\|\boldsymbol{\epsilon}\| \leq \delta$. The perturbed QQN path:
$$\tilde{\mathbf{d}}(t) = t(1-t)(-\nabla f) + t^2 \tilde{\mathbf{d}}_{\text{L-BFGS}}$$

satisfies:
$$\|\tilde{\mathbf{d}}(t) - \mathbf{d}(t)\| \leq t^2\delta$$

_Proof_: Direct computation:

$$\|\tilde{\mathbf{d}}(t) - \mathbf{d}(t)\| = \|t^2(\tilde{\mathbf{d}}_{\text{L-BFGS}} - \mathbf{d}_{\text{L-BFGS}})\| = t^2\|\boldsymbol{\epsilon}\| \leq t^2\delta$$

For small $t$ (near the initial descent phase), the error is $O(t^2\delta)$, providing quadratic error suppression. $\square$

## B.4 Computational Complexity

**Theorem B.6** (Computational Complexity): Each QQN iteration requires:

- $O(n)$ operations for path construction
- $O(mn)$ operations for L-BFGS direction computation
- $O(k)$ function evaluations for univariate optimization

where $n$ is the dimension, $m$ is the L-BFGS memory size, and $k$ is typically small (3-10).

_Proof_:

1. **Path construction**: Computing $\mathbf{d}(t) = t(1-t)(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$ requires $O(n)$ operations for vector arithmetic.
2. **L-BFGS direction**: The two-loop recursion requires $O(mn)$ operations to compute $\mathbf{H}_k\nabla f(\mathbf{x}_k)$.
3. **Line search**: Each function evaluation along the path requires $O(n)$ operations to compute $\mathbf{x}_k + \mathbf{d}(t)$, plus the cost of evaluating $f$.

Total complexity per iteration: $O(mn + kn) + k \cdot \text{cost}(f)$. $\square$

## B.5 Extensions and Variants

### B.5.1 Gradient Scaling

The basic QQN formulation can be enhanced with gradient scaling:
$$\mathbf{d}(t) = t(1-t)\alpha(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$$

where $\alpha > 0$ is a scaling factor.

**Proposition B.1** (Scaling Invariance): The set of points reachable by the QQN path is invariant to the choice of $\alpha$. Only the parametrization changes.

_Proof_: Consider the mapping $s = \beta(t)$ where $\beta$ is chosen such that:
$$t(1-t)\alpha(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}} = s(1-s)(-\nabla f) + s^2 \mathbf{d}_{\text{L-BFGS}}$$

This gives a bijection between parametrizations, showing that any point reachable with one $\alpha$ is reachable with another. $\square$

### B.5.2 Cubic Extension with Momentum

Incorporating momentum leads to cubic interpolation:
$$\mathbf{d}(t) = t(1-t)(1-2t)\mathbf{m} + t(1-t)\alpha(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$$

where $\mathbf{m}$ is the momentum vector.

This satisfies:

- $\mathbf{d}(0) = \mathbf{0}$
- $\mathbf{d}'(0) = \alpha(-\nabla f) + \mathbf{m}$
- $\mathbf{d}(1) = \mathbf{d}_{\text{L-BFGS}}$
- $\mathbf{d}''(0) = -6\mathbf{m} + 2\alpha(-\nabla f) + 2\mathbf{d}_{\text{L-BFGS}}$

**Theorem B.7** (Cubic Convergence Properties): The cubic variant maintains all convergence guarantees of the quadratic version while potentially improving the convergence constant through momentum acceleration.

### B.5.3 Trust Region Integration

QQN naturally extends to trust regions by constraining the univariate search:
$$t^* = \arg\min_{t: \|\mathbf{d}(t)\| \leq \Delta} f(\mathbf{x} + \mathbf{d}(t))$$

where $\Delta$ is the trust region radius.

**Proposition B.2** (Trust Region Feasibility): For any $\Delta > 0$, there exists $t_{\max} > 0$ such that $\|\mathbf{d}(t)\| \leq \Delta$ for all $t \in [0, t_{\max}]$.

_Proof_: Since $\mathbf{d}(0) = \mathbf{0}$ and $\mathbf{d}$ is continuous, by the intermediate value theorem, the set $\{t : \|\mathbf{d}(t)\| \leq \Delta\}$ contains an interval $[0, t_{\max}]$ for some $t_{\max} > 0$. $\square$

## B.6 Comparison with Related Methods

### B.6.1 Relationship to Trust Region Methods

Trust region methods solve:
$$\min_{\mathbf{s}} \mathbf{g}^T\mathbf{s} + \frac{1}{2}\mathbf{s}^T\mathbf{B}\mathbf{s} \quad \text{s.t.} \quad \|\mathbf{s}\| \leq \Delta$$

QQN can be viewed as solving a related but different problem:
$$\min_{t \geq 0} f(\mathbf{x} + \mathbf{d}(t))$$

where $\mathbf{d}(t)$ is the quadratic path.
**Key differences**:

- Trust region: Solves 2D subproblem, then line search
- QQN: Direct 1D optimization along quadratic path
- Trust region: Requires trust region radius management
- QQN: Parameter-free, automatic adaptation

### B.6.2 Relationship to Line Search Methods

Traditional line search methods optimize:
$$\min_{\alpha > 0} f(\mathbf{x} + \alpha \mathbf{d})$$

QQN generalizes this by optimizing along a parametric path:
$$\min_{t \geq 0} f(\mathbf{x} + \mathbf{d}(t))$$

The key insight is that the direction itself changes with the parameter, providing additional flexibility.

### B.6.3 Relationship to Hybrid Methods

Previous hybrid approaches typically use discrete switching:

$$
\mathbf{d} = \begin{cases}
\mathbf{d}_{\text{gradient}} & \text{if condition A} \\
\mathbf{d}_{\text{quasi-Newton}} & \text{if condition B}
\end{cases}
$$

QQN provides continuous interpolation, eliminating discontinuities and the need for switching logic.
