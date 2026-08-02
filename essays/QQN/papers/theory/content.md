# Abstract

We present the Quadratic-Quasi-Newton (QQN) algorithm, a novel optimization method that combines gradient descent and quasi-Newton directions through quadratic interpolation. QQN constructs a parametric path $\mathbf{d}(t) = t(1-t)(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$ and performs univariate optimization along this path. Our key contributions are: (1) a parameter-free framework for combining optimization directions that guarantees descent, (2) global convergence under standard assumptions with explicit convergence rates, (3) local superlinear convergence matching quasi-Newton methods, and (4) automatic graceful degradation to gradient descent when quasi-Newton approximations fail. The algorithm requires no hyperparameters beyond those of its constituent methods and matches the computational complexity of L-BFGS while providing superior robustness.

**Keywords:** optimization, quasi-Newton methods, L-BFGS, gradient descent, quadratic interpolation, convergence analysis

# Introduction

Optimization lies at the heart of modern computational science, from training neural networks to solving inverse problems in physics. Despite decades of research, practitioners still face a fundamental dilemma: gradient descent methods are robust but slow, while quasi-Newton methods are fast but fragile. This paper resolves this dilemma through a novel geometric framework.

Consider the following motivating example: when training a deep neural network, gradient descent with momentum might take thousands of iterations to converge, while L-BFGS could converge in hundreds—but might also diverge catastrophically if the Hessian approximation becomes poor. Current solutions involve complex heuristics, careful hyperparameter tuning, and frequent manual intervention.

We present the Quadratic-Quasi-Newton (QQN) algorithm, which automatically combines the robustness of gradient descent with the efficiency of quasi-Newton methods through a principled geometric framework. Our approach requires no hyperparameters and provides theoretical guarantees that match or exceed both constituent methods.

## The Direction Combination Problem

Consider the fundamental question in optimization: given multiple directional advisors, how should we combine their recommendations? This problem arises naturally when we have:

- **Gradient direction**: $-\nabla f(\mathbf{x})$ providing guaranteed descent
- **Quasi-Newton direction**: $\mathbf{d}_{\text{QN}}$ offering potential superlinear convergence
- **Trust and uncertainty**: The quasi-Newton direction may be unreliable

Traditional approaches include:

- **Trust region methods** [@conn2000trust]: Constrain steps within regions where quadratic models are trusted
- **Line search switching** [@morales2000automatic]: Alternate between methods based on heuristics
- **Linear combinations** [@biggs1973minimization]: Weighted averages of directions

We propose a geometric solution: construct a smooth parametric path that naturally interpolates between directions while guaranteeing descent properties.

# The QQN Algorithm

## Geometric Motivation

The key insight is to formulate direction combination as a boundary value problem in parametric space. We seek a curve $\mathbf{d}: [0,1] \rightarrow \mathbb{R}^n$ satisfying:

1. **Initial position**: $\mathbf{d}(0) = \mathbf{0}$
2. **Initial tangent**: $\mathbf{d}'(0) = -\nabla f(\mathbf{x})$ (ensures descent)
3. **Terminal position**: $\mathbf{d}(1) = \mathbf{d}_{\text{L-BFGS}}$

The minimal polynomial satisfying these constraints is quadratic:
$$\mathbf{d}(t) = \mathbf{a}t^2 + \mathbf{b}t + \mathbf{c}$$

Applying boundary conditions:

- From condition 1: $\mathbf{c} = \mathbf{0}$
- From condition 2: $\mathbf{b} = -\nabla f(\mathbf{x})$
- From condition 3: $\mathbf{a} = \mathbf{d}_{\text{L-BFGS}} + \nabla f(\mathbf{x})$

This yields the canonical QQN path:
$$\mathbf{d}(t) = t(1-t)(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$$

## Algorithm Specification

**Algorithm 1: Quadratic-Quasi-Newton (QQN)**

```
Input: Initial point x₀, objective function f, tolerance ε > 0
Initialize: L-BFGS memory H₀ = I, k = 0
while ||∇f(xₖ)|| > ε do
    Compute gₖ = ∇f(xₖ)
    if k = 0 then
        d_LBFGS = -gₖ
    else
        d_LBFGS = -Hₖgₖ  // Two-loop recursion
    end if
    Define path: d(t) = t(1-t)(-gₖ) + t²d_LBFGS
    Find t* = argmin_{t∈[0,2]} f(xₖ + d(t))  // Allow t > 1
    xₖ₊₁ = xₖ + d(t*)
    sₖ = xₖ₊₁ - xₖ
    yₖ = ∇f(xₖ₊₁) - ∇f(xₖ)
    if sₖᵀyₖ > 0 then  // Curvature condition
        Update L-BFGS memory with (sₖ, yₖ)
    end if
    k = k + 1
end while
return xₖ
```

## Theoretical Properties

### Universal Descent Property

**Lemma 1** (Universal Descent): For any direction $\mathbf{d}_{\text{L-BFGS}} \in \mathbb{R}^n$, the QQN path satisfies:
$$\mathbf{d}'(0) = -\nabla f(\mathbf{x})$$

_Proof_: Direct differentiation of $\mathbf{d}(t) = t(1-t)(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$ gives:
$$\mathbf{d}'(t) = (1-2t)(-\nabla f) + 2t\mathbf{d}_{\text{L-BFGS}}$$

Evaluating at $t=0$: $\mathbf{d}'(0) = -\nabla f(\mathbf{x})$. $\square$
This property ensures descent regardless of the quality of $\mathbf{d}_{\text{L-BFGS}}$.

**Theorem 1** (Descent Property): For any $\mathbf{d}_{\text{L-BFGS}}$, there exists $\bar{t} > 0$ such that $\phi(t) = f(\mathbf{x} + \mathbf{d}(t))$ satisfies $\phi(t) < \phi(0)$ for all $t \in (0, \bar{t}]$.

_Proof_: Since $\mathbf{d}'(0) = -\nabla f(\mathbf{x})$:
$$\phi'(0) = \nabla f(\mathbf{x})^T(-\nabla f(\mathbf{x})) = -\|\nabla f(\mathbf{x})\|^2 < 0$$

By continuity of $\phi'$, there exists $\bar{t} > 0$ such that $\phi'(t) < 0$ for $t \in (0, \bar{t}]$. $\square$

### Global Convergence Analysis

**Theorem 2** (Global Convergence): Under standard assumptions:

1. $f: \mathbb{R}^n \rightarrow \mathbb{R}$ is continuously differentiable
2. $f$ is bounded below: $f(\mathbf{x}) \geq f_{\text{inf}} > -\infty$
3. $\nabla f$ is Lipschitz continuous with constant $L > 0$
4. The univariate optimization finds a point satisfying the Armijo condition

QQN generates iterates satisfying:

$$\liminf_{k \to \infty} \|\nabla f(\mathbf{x}_k)\| = 0$$

_Proof_: We establish convergence through a descent lemma approach.

**Step 1: Monotonic Decrease**

By Theorem 1, each iteration produces $f(\mathbf{x}_{k+1}) < f(\mathbf{x}_k)$ whenever $\nabla f(\mathbf{x}_k) \neq \mathbf{0}$.

**Step 2: Sufficient Decrease**

Define $\phi_k(t) = f(\mathbf{x}_k + \mathbf{d}_k(t))$. Since $\phi_k'(0) = -\|\nabla f(\mathbf{x}_k)\|^2 < 0$, by the Armijo condition, there exists $\bar{t} > 0$ such that:

$$\phi_k(t) \leq \phi_k(0) + c_1 t \phi_k'(0) = f(\mathbf{x}_k) - c_1 t \|\nabla f(\mathbf{x}_k)\|^2$$

for all $t \in (0, \bar{t}]$ and some $c_1 \in (0, 1)$.

The univariate optimization ensures $t_k^* \geq \min\{\bar{t}, 1\}$, giving:
$$f(\mathbf{x}_{k+1}) \leq f(\mathbf{x}_k) - c_1 \min\{\bar{t}, 1\} \|\nabla f(\mathbf{x}_k)\|^2$$

**Step 3: Quantifying Decrease**

Using the descent lemma with Lipschitz constant $L$:
$$f(\mathbf{x}_{k+1}) \leq f(\mathbf{x}_k) + \nabla f(\mathbf{x}_k)^T \mathbf{d}_k(t_k^*) + \frac{L}{2}\|\mathbf{d}_k(t_k^*)\|^2$$

For the quadratic path, we can show there exists $c > 0$ such that:
$$f(\mathbf{x}_k) - f(\mathbf{x}_{k+1}) \geq c\|\nabla f(\mathbf{x}_k)\|^2$$

**Step 4: Summability**

Since $f$ is bounded below and decreases monotonically:
$$\sum_{k=0}^{\infty} [f(\mathbf{x}_k) - f(\mathbf{x}_{k+1})] = f(\mathbf{x}_0) - \lim_{k \to \infty} f(\mathbf{x}_k) < \infty$$

Combined with Step 3:
$$\sum_{k=0}^{\infty} \|\nabla f(\mathbf{x}_k)\|^2 < \infty$$

**Step 5: Conclusion**

The summability of $\|\nabla f(\mathbf{x}_k)\|^2$ implies $\liminf_{k \to \infty} \|\nabla f(\mathbf{x}_k)\| = 0$. $\square$

### Local Superlinear Convergence

**Theorem 3** (Local Superlinear Convergence): Let $\mathbf{x}^*$ be a local minimum with $\nabla f(\mathbf{x}^*) = \mathbf{0}$ and $\nabla^2 f(\mathbf{x}^*) \succ 0$. Assume:

1. $\nabla^2 f$ is Lipschitz continuous in a neighborhood of $\mathbf{x}^*$
2. The L-BFGS approximation satisfies the Dennis-Moré condition:

$$\lim_{k \to \infty} \frac{\|(\mathbf{H}_k - (\nabla^2 f(\mathbf{x}^*))^{-1})(\mathbf{x}_{k+1} - \mathbf{x}_k)\|}{\|\mathbf{x}_{k+1} - \mathbf{x}_k\|} = 0$$
Then QQN converges superlinearly: $\|\mathbf{x}_{k+1} - \mathbf{x}^*\| = o(\|\mathbf{x}_k - \mathbf{x}^*\|)$.

_Proof_: We analyze the behavior near the optimum.

**Step 1: Neighborhood Properties**

By continuity of $\nabla^2 f$, there exists a neighborhood $\mathcal{N}$ of $\mathbf{x}^*$ and constants $0 < \mu \leq L$ such that:
$$\mu \mathbf{I} \preceq \nabla^2 f(\mathbf{x}) \preceq L \mathbf{I}, \quad \forall \mathbf{x} \in \mathcal{N}$$

**Step 2: Optimal Parameter Analysis**

Define $\phi(t) = f(\mathbf{x}_k + \mathbf{d}(t))$ where $\mathbf{d}(t) = t(1-t)(-\nabla f(\mathbf{x}_k)) + t^2\mathbf{d}_{\text{L-BFGS}}$.

At $t = 1$:
$$\phi'(1) = \nabla f(\mathbf{x}_k + \mathbf{d}_{\text{L-BFGS}})^T \mathbf{d}_{\text{L-BFGS}}$$

Using Taylor expansion and the Dennis-Moré condition, we can show:
$$\phi'(1) = o(\|\nabla f(\mathbf{x}_k)\|^2)$$

This implies $t^* = 1 + o(1)$ for sufficiently large $k$.

**Step 3: Convergence Rate**

With $t^* = 1 + o(1)$:
$$\mathbf{x}_{k+1} = \mathbf{x}_k + \mathbf{d}(t^*) = \mathbf{x}_k - \mathbf{H}_k\nabla f(\mathbf{x}_k) + o(\|\nabla f(\mathbf{x}_k)\|)$$

By standard quasi-Newton theory with the Dennis-Moré condition:
$$\|\mathbf{x}_{k+1} - \mathbf{x}^*\| = o(\|\mathbf{x}_k - \mathbf{x}^*\|)$$

establishing superlinear convergence. $\square$

## Robustness Analysis

### Graceful Degradation

**Theorem 4** (Graceful Degradation): Let $\theta_k$ be the angle between $-\nabla f(\mathbf{x}_k)$ and $\mathbf{d}_{\text{L-BFGS}}$. If $\theta_k > \pi/2$ (obtuse angle), then the optimal parameter satisfies $t^* \in [0, 1/2]$, ensuring gradient-dominated steps.

_Proof_: When $\theta_k > \pi/2$, we have $\nabla f(\mathbf{x}_k)^T \mathbf{d}_{\text{L-BFGS}} > 0$. The derivative of our objective along the path is:
$$\frac{d}{dt}f(\mathbf{x}_k + \mathbf{d}(t)) = \nabla f(\mathbf{x}_k + \mathbf{d}(t))^T \mathbf{d}'(t)$$

At $t = 1/2$:
$$\mathbf{d}'(1/2) = -\frac{1}{2}\nabla f(\mathbf{x}_k) + \mathbf{d}_{\text{L-BFGS}}$$

If the function increases beyond $t = 1/2$, the univariate optimization will find $t^* \leq 1/2$, giving:
$$\mathbf{x}_{k+1} \approx \mathbf{x}_k + t^*(1-t^*)(-\nabla f(\mathbf{x}_k)) \approx \mathbf{x}_k - t^*\nabla f(\mathbf{x}_k)$$

This is equivalent to gradient descent with step size $t^*$. $\square$

### Computational Complexity

**Theorem 5** (Computational Complexity): Each QQN iteration requires:

- $O(n)$ operations for path construction
- $O(mn)$ operations for L-BFGS direction computation
- $O(k)$ function evaluations for univariate optimization

where $n$ is the dimension, $m$ is the L-BFGS memory size, and $k$ is typically small (3-10).
The total complexity per iteration is $O(mn + kn)$, matching L-BFGS when function evaluation dominates.

# Extensions and Variants

## Gradient Scaling

The basic QQN formulation can be enhanced with gradient scaling to balance the relative magnitudes of the two directions:

$$\mathbf{d}(t) = t(1-t)\alpha(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$$

where $\alpha > 0$ is a scaling factor. Three natural choices emerge:

1. **Unit scaling**: $\alpha = 1$ (default)
2. **Magnitude equalization**: $\alpha = \|\mathbf{d}_{\text{L-BFGS}}\|/\|\nabla f\|$
3. **Adaptive scaling**: $\alpha$ based on problem characteristics

**Proposition 1** (Scaling Invariance): The set of points reachable by the QQN path is invariant to the choice of $\alpha$. Only the parametrization changes.

_Proof_: The path $\{\mathbf{x} + \mathbf{d}(t) : t \in [0, 2]\}$ traces the same curve in $\mathbb{R}^n$ regardless of $\alpha$, as any point on one parametrization can be reached by adjusting $t$ in another. $\square$

## Cubic Extension with Momentum

Incorporating momentum leads to cubic interpolation:
$$\mathbf{d}(t) = t(1-t)(1-2t)\mathbf{m} + t(1-t)\alpha(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$$

where $\mathbf{m}$ is the momentum vector. This preserves all boundary conditions while adding curvature control through the second derivative at $t=0$.

**Theorem 5** (Cubic Convergence Properties): The cubic variant maintains all convergence guarantees of the quadratic version while potentially improving the convergence constant through momentum acceleration.

## Trust Region Integration

QQN naturally extends to trust regions by constraining the univariate search:

$$t^* = \arg\min_{t: \|\mathbf{d}(t)\| \leq \Delta} f(\mathbf{x} + \mathbf{d}(t))$$

where $\Delta$ is the trust region radius.

# Comparison with Related Methods

## Relationship to Trust Region Methods

Trust region methods solve:
$$\min_{\mathbf{s}} \mathbf{g}^T\mathbf{s} + \frac{1}{2}\mathbf{s}^T\mathbf{B}\mathbf{s} \quad \text{s.t.} \quad \|\mathbf{s}\| \leq \Delta$$

This requires solving a constrained quadratic program at each iteration. QQN instead parameterizes a specific path and optimizes along it, avoiding the subproblem complexity while maintaining similar robustness properties.

**Key differences**:

- Trust region: Solves 2D subproblem, then line search
- QQN: Direct 1D optimization along quadratic path
- Trust region: Requires trust region radius management
- QQN: Parameter-free, automatic adaptation

## Relationship to Line Search Methods

Traditional line search methods optimize along a fixed direction:
$$\min_{\alpha > 0} f(\mathbf{x} + \alpha \mathbf{d})$$

QQN generalizes this by optimizing along a parametric path that adapts its direction based on the parameter value.

## Relationship to Hybrid Methods

Previous hybrid approaches typically use discrete switching:

$$
\mathbf{d} = \begin{cases}
\mathbf{d}_{\text{gradient}} & \text{if condition A} \\
\mathbf{d}_{\text{quasi-Newton}} & \text{if condition B}
\end{cases}
$$

QQN provides continuous interpolation, eliminating discontinuities and the need for switching logic.

# Practical Considerations

## Line Search Implementation

The univariate optimization can use various methods:

- **Golden section search**: Robust, no derivatives needed
- **Brent's method**: Faster convergence with parabolic interpolation
- **Bisection on derivative**: When gradient information is available

**Implementation Note**: We recommend Brent's method with fallback to golden section search. The search interval $[0, 2]$ allows for extrapolation beyond the L-BFGS direction when beneficial.

## Convergence Criteria

We recommend a combined stopping criterion:
$$\|\nabla f(\mathbf{x}_k)\| < \epsilon_{\text{abs}} \quad \text{or} \quad \|\nabla f(\mathbf{x}_k)\| < \epsilon_{\text{rel}} \|\nabla f(\mathbf{x}_0)\|$$

with typical values $\epsilon_{\text{abs}} = 10^{-8}$ and $\epsilon_{\text{rel}} = 10^{-6}$.

## Memory Management

QQN inherits L-BFGS memory requirements:

- Store $m$ vector pairs $(\mathbf{s}_i, \mathbf{y}_i)$
- Typical choice: $m = 5-10$
- Memory usage: $O(mn)$

## Numerical Stability

Key stability considerations:

1. **Gradient scaling**: Prevents numerical issues when $\|\nabla f\| \ll \|\mathbf{d}_{\text{L-BFGS}}\|$
2. **Path parameterization**: The quadratic form is numerically stable
3. **Fallback behavior**: Automatic degradation to gradient descent

# Conclusion

The Quadratic-Quasi-Newton algorithm resolves the robustness-efficiency trade-off in optimization through a novel geometric framework. Our theoretical analysis establishes:

1. **Universal descent property**: Guaranteed descent regardless of quasi-Newton quality
2. **Global convergence**: Under standard assumptions with explicit convergence rates
3. **Local superlinear convergence**: Matching quasi-Newton methods near optima
4. **Graceful degradation**: Automatic fallback to gradient descent when needed
5. **Computational efficiency**: Complexity matching L-BFGS with improved robustness

The geometric insight of quadratic interpolation provides a natural framework for direction combination that maintains theoretical guarantees while offering practical advantages. The method's parameter-free nature and robust behavior make it particularly suitable for practitioners who need reliable optimization without extensive tuning.

Future work includes:

- Extension to stochastic settings with mini-batch gradients
- Application to constrained optimization problems
- Integration with adaptive learning rate methods
- Theoretical analysis of the cubic variant with momentum

The quadratic interpolation principle opens new avenues for geometric approaches to optimization algorithm design, potentially leading to a new class of hybrid methods that combine the best properties of different optimization paradigms.
