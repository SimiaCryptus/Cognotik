# Appendix

## A. Detailed Proofs

### A.1 Proof of Sufficient Decrease Constant

**Lemma A.1** (Sufficient Decrease): Under the assumptions of Theorem 2, there exists a constant $c > 0$ independent of $k$ such that:
$$f(\mathbf{x}_k) - f(\mathbf{x}_{k+1}) \geq c\|\nabla f(\mathbf{x}_k)\|^2$$

**Proof**: Consider the quadratic path $\mathbf{d}(t) = t(1-t)(-\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$.

For small $t$, Taylor expansion gives:
$$\mathbf{d}(t) = -t\nabla f + O(t^2)$$

Using the descent lemma:
$$f(\mathbf{x}_k + \mathbf{d}(t)) \leq f(\mathbf{x}_k) + \nabla f(\mathbf{x}_k)^T \mathbf{d}(t) + \frac{L}{2}\|\mathbf{d}(t)\|^2$$

Substituting the path:
$$f(\mathbf{x}_k + \mathbf{d}(t)) \leq f(\mathbf{x}_k) - t\|\nabla f(\mathbf{x}_k)\|^2 + \frac{Lt^2}{2}\|\nabla f(\mathbf{x}_k)\|^2 + O(t^3)$$

For $t = \min\{1, 1/L\}$, we get:
$$f(\mathbf{x}_k + \mathbf{d}(t)) \leq f(\mathbf{x}_k) - \frac{t}{2}\|\nabla f(\mathbf{x}_k)\|^2$$

Since the univariate optimization finds $t^*$ at least as good as this choice:
$$f(\mathbf{x}_k) - f(\mathbf{x}_{k+1}) \geq \frac{\min\{1, 1/L\}}{2}\|\nabla f(\mathbf{x}_k)\|^2$$

Taking $c = \frac{\min\{1, 1/L\}}{2}$ completes the proof. $\square$

### A.2 Dennis-Moré Condition Analysis

**Lemma A.2** (Dennis-Moré Implies Unit Steps): If the L-BFGS approximation satisfies the Dennis-Moré condition, then $t^* \to 1$ as $k \to \infty$.

**Proof**: The Dennis-Moré condition states:
$$\lim_{k \to \infty} \frac{\|(\mathbf{H}_k - (\nabla^2 f(\mathbf{x}^*))^{-1})(\mathbf{x}_{k+1} - \mathbf{x}_k)\|}{\|\mathbf{x}_{k+1} - \mathbf{x}_k\|} = 0$$

Near the optimum, the L-BFGS direction becomes:
$$\mathbf{d}_{\text{L-BFGS}} = -\mathbf{H}_k \nabla f(\mathbf{x}_k) \approx -(\nabla^2 f(\mathbf{x}^*))^{-1} \nabla f(\mathbf{x}_k)$$

The optimal step in Newton's method would be:
$$\mathbf{s}_{\text{Newton}} = -(\nabla^2 f(\mathbf{x}_k))^{-1} \nabla f(\mathbf{x}_k) \approx -(\nabla^2 f(\mathbf{x}^*))^{-1} \nabla f(\mathbf{x}_k)$$

At $t = 1$, the QQN path gives:
$$\mathbf{d}(1) = \mathbf{d}_{\text{L-BFGS}} \approx \mathbf{s}_{\text{Newton}}$$

By the optimality of Newton's method near the minimum and continuity of the objective function, the univariate optimization will find $t^* \to 1$. $\square$

## B. Implementation Details

### B.1 L-BFGS Direction Computation

The L-BFGS direction is computed using the two-loop recursion:

```
function compute_lbfgs_direction(gradient, memory):
    q = gradient
    alphas = []
    // First loop (backward)
    for i = memory.size-1 down to 0:
        rho_i = 1 / (memory.y[i]^T * memory.s[i])
        alpha_i = rho_i * memory.s[i]^T * q
        q = q - alpha_i * memory.y[i]
        alphas.append(alpha_i)
    // Apply initial Hessian approximation
    if memory.size > 0:
        gamma = (memory.s[-1]^T * memory.y[-1]) / (memory.y[-1]^T * memory.y[-1])
        r = gamma * q
    else:
        r = q
    // Second loop (forward)
    for i = 0 to memory.size-1:
        rho_i = 1 / (memory.y[i]^T * memory.s[i])
        beta = rho_i * memory.y[i]^T * r
        r = r + (alphas[memory.size-1-i] - beta) * memory.s[i]
    return -r
```

### B.2 Univariate Optimization Methods

#### Golden Section Search

```
function golden_section_search(f, a, b, tol):
    phi = (1 + sqrt(5)) / 2
    resphi = 2 - phi
    x1 = a + resphi * (b - a)
    x2 = b - resphi * (b - a)
    f1 = f(x1)
    f2 = f(x2)
    while abs(b - a) > tol:
        if f1 > f2:
            a = x1
            x1 = x2
            f1 = f2
            x2 = b - resphi * (b - a)
            f2 = f(x2)
        else:
            b = x2
            x2 = x1
            f2 = f1
            x1 = a + resphi * (b - a)
            f1 = f(x1)
    return (a + b) / 2
```

#### Brent's Method

Combines golden section search with parabolic interpolation for faster convergence when the function is smooth.

### B.3 Memory Update

After each iteration, update the L-BFGS memory:

```
function update_memory(memory, s_k, y_k):
    if memory.size == memory.max_size:
        // Remove oldest pair
        memory.s.pop_front()
        memory.y.pop_front()
    // Add new pair
    memory.s.push_back(s_k)
    memory.y.push_back(y_k)
    memory.size = min(memory.size + 1, memory.max_size)
    // Check curvature condition
    if s_k^T * y_k <= 0:
        // Skip update or apply damping
        memory.s.pop_back()
        memory.y.pop_back()
        memory.size -= 1
```

## C. Convergence Rate Analysis

### C.1 Linear Convergence Rate

For strongly convex functions with condition number $\kappa$, QQN achieves at least linear convergence with rate:
$$\|\mathbf{x}_{k+1} - \mathbf{x}^*\| \leq \left(1 - \frac{1}{\kappa}\right)\|\mathbf{x}_k - \mathbf{x}^*\|$$

This follows from the fact that QQN reduces to gradient descent in the worst case, and gradient descent achieves this rate on strongly convex functions.

### C.2 Superlinear Convergence Rate

Near the optimum, when L-BFGS provides good approximations, QQN achieves superlinear convergence:
$$\|\mathbf{x}_{k+1} - \mathbf{x}^*\| = o(\|\mathbf{x}_k - \mathbf{x}^*\|)$$

The exact rate depends on how quickly the L-BFGS approximation converges to the true inverse Hessian.

## D. Numerical Examples

### D.1 Quadratic Function

Consider $f(\mathbf{x}) = \frac{1}{2}\mathbf{x}^T \mathbf{A} \mathbf{x}$ where $\mathbf{A}$ is positive definite.

The gradient is $\nabla f(\mathbf{x}) = \mathbf{A}\mathbf{x}$ and the optimal step is $\mathbf{x}^* = \mathbf{0}$.
For this function, the L-BFGS direction (after sufficient iterations) becomes:
$$\mathbf{d}_{\text{L-BFGS}} = -\mathbf{A}^{-1}\mathbf{A}\mathbf{x} = -\mathbf{x}$$

The QQN path becomes:
$$\mathbf{d}(t) = t(1-t)(-\mathbf{A}\mathbf{x}) + t^2(-\mathbf{x}) = -t\mathbf{x}[(1-t)\mathbf{A} + t\mathbf{I}]$$

For a quadratic function with exact L-BFGS approximation, the univariate optimization yields $t^* = 1$, giving the exact Newton step and convergence in one iteration.

### D.2 Rosenbrock Function

The Rosenbrock function $f(x,y) = 100(y-x^2)^2 + (1-x)^2$ is a classic test case for optimization algorithms.

The gradient is:
$$\nabla f = \begin{pmatrix} -400x(y-x^2) - 2(1-x) \\ 200(y-x^2) \end{pmatrix}$$

Near the optimum $(1,1)$, the Hessian is:
$$\nabla^2 f = \begin{pmatrix} 802 & -400 \\ -400 & 200 \end{pmatrix}$$

This is ill-conditioned with condition number $\kappa \approx 2416$, making it challenging for first-order methods but suitable for demonstrating QQN's robustness.

## E. Extensions and Variations

### E.1 Constrained QQN

For box constraints $\mathbf{l} \leq \mathbf{x} \leq \mathbf{u}$, we can modify the univariate optimization:
$$t^* = \arg\min_{t \geq 0} f(\text{proj}(\mathbf{x} + \mathbf{d}(t)))$$

where $\text{proj}$ is the projection onto the feasible region.

### E.2 Stochastic QQN

For stochastic optimization, we can use:

1. **Mini-batch gradients**: Compute $\nabla f$ on subsets of data
2. **Variance reduction**: Use techniques like SVRG or SAGA
3. **Adaptive sampling**: Increase batch size as optimization progresses

### E.3 Preconditioning

The gradient can be preconditioned:
$$\mathbf{d}(t) = t(1-t)(-\mathbf{P}^{-1}\nabla f) + t^2 \mathbf{d}_{\text{L-BFGS}}$$

where $\mathbf{P}$ is a preconditioning matrix (e.g., diagonal scaling).

## F. Computational Complexity Analysis

### F.1 Per-Iteration Cost

The computational cost per iteration consists of:

1. **Gradient computation**: $O(n)$ to $O(n^2)$ depending on the function
2. **L-BFGS direction**: $O(mn)$ where $m$ is memory size
3. **Path evaluation**: $O(n)$ per function evaluation
4. **Univariate optimization**: $O(k)$ function evaluations, typically $k = 3-10$

Total: $O(mn + kn)$ operations plus $k$ function evaluations, where the function evaluation cost typically dominates.

### F.2 Memory Requirements

### F.3 Comparison with Other Methods

| Method           | Per-iteration ops | Memory   | Function evals | Robustness |
| ---------------- | ----------------- | -------- | -------------- | ---------- |
| Gradient Descent | $O(n)$            | $O(n)$   | 1-5            | High       |
| L-BFGS           | $O(mn)$           | $O(mn)$  | 3-20           | Medium     |
| QQN              | $O(mn)$           | $O(mn)$  | 3-10           | High       |
| Newton           | $O(n^3)$          | $O(n^2)$ | 1              | Low        |
| Trust Region     | $O(n^3)$          | $O(n^2)$ | 1-10           | High       |

QQN matches L-BFGS complexity while providing gradient descent robustness and often requiring fewer function evaluations due to better step selection.
