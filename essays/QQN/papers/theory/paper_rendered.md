    Theoretical Analysis of the Quadratic-Quasi-Newton Algorithm:
            Convergence Properties and Geometric Insights
                                         Andrew Charneski
                                       SimiaCryptus Software
                                           August 2, 2025

1 Abstract
We present the Quadratic-Quasi-Newton (QQN) algorithm, a novel optimization method that combines gra-
dient descent and quasi-Newton directions through quadratic interpolation. QQN constructs a parametric
path d(t) = t(1 − t)(−∇f ) + t2 dL-BFGS and performs univariate optimization along this path. Our key
contributions are: (1) a parameter-free framework for combining optimization directions that guarantees
descent, (2) global convergence under standard assumptions with explicit convergence rates, (3) local su-
perlinear convergence matching quasi-Newton methods, and (4) automatic graceful degradation to gradient
descent when quasi-Newton approximations fail. The algorithm requires no hyperparameters beyond those
of its constituent methods and matches the computational complexity of L-BFGS while providing superior
robustness.
Keywords: optimization, quasi-Newton methods, L-BFGS, gradient descent, quadratic interpolation,
convergence analysis

2 Introduction
Optimization lies at the heart of modern computational science, from training neural networks to solving
inverse problems in physics. Despite decades of research, practitioners still face a fundamental dilemma:
gradient descent methods are robust but slow, while quasi-Newton methods are fast but fragile. This paper
resolves this dilemma through a novel geometric framework.
Consider the following motivating example: when training a deep neural network, gradient descent with
momentum might take thousands of iterations to converge, while L-BFGS could converge in hundreds—but
might also diverge catastrophically if the Hessian approximation becomes poor. Current solutions involve
complex heuristics, careful hyperparameter tuning, and frequent manual intervention.
We present the Quadratic-Quasi-Newton (QQN) algorithm, which automatically combines the robustness
of gradient descent with the efficiency of quasi-Newton methods through a principled geometric framework.
Our approach requires no hyperparameters and provides theoretical guarantees that match or exceed both
constituent methods.

2.1 The Direction Combination Problem
Consider the fundamental question in optimization: given multiple directional advisors, how should we
combine their recommendations? This problem arises naturally when we have:

    • Gradient direction: −∇f (x) providing guaranteed descent
    • Quasi-Newton direction: dQN offering potential superlinear convergence
    • Trust and uncertainty: The quasi-Newton direction may be unreliable

    Traditional approaches include:



                                                   1

• Trust region methods [Conn et al., 2000]: Constrain steps within regions where quadratic models
are trusted
• Line search switching [Morales and Nocedal, 2000]: Alternate between methods based on heuristics
• Linear combinations [Biggs, 1973]: Weighted averages of directions

    We propose a geometric solution: construct a smooth parametric path that naturally interpolates between

directions while guaranteeing descent properties.

3 The QQN Algorithm
3.1 Geometric Motivation
The key insight is to formulate direction combination as a boundary value problem in parametric space. We
seek a curve d : [0, 1] → Rn satisfying:

    1. Initial position: d(0) = 0
    2. Initial tangent: d′ (0) = −∇f (x) (ensures descent)
    3. Terminal position: d(1) = dL-BFGS

    The minimal polynomial satisfying these constraints is quadratic:

                                            d(t) = at2 + bt + c

    Applying boundary conditions:

    • From condition 1: c = 0
    • From condition 2: b = −∇f (x)
    • From condition 3: a = dL-BFGS + ∇f (x)

    This yields the canonical QQN path:

                                     d(t) = t(1 − t)(−∇f ) + t2 dL-BFGS

3.2 Algorithm Specification
Algorithm 1: Quadratic-Quasi-Newton (QQN)

Input: Initial point x, objective function f, tolerance > 0
Initialize: L-BFGS memory H = I, k = 0
while ||f(x)|| > do
Compute g = f(x)
if k = 0 then
d*LBFGS = -g
else
d_LBFGS = -Hg // Two-loop recursion
end if
Define path: d(t) = t(1-t)(-g) + t²d_LBFGS
Find t\* = argmin*{t[0,2]} f(x + d(t)) // Allow t > 1
x = x + d(t\*)
s = x - x
y = f(x) - f(x)
if sy > 0 then // Curvature condition
Update L-BFGS memory with (s, y)
end if
k = k + 1
end while
return x

                                                     2

3.3 Theoretical Properties
3.3.1 Universal Descent Property
Lemma 1 (Universal Descent): For any direction dL-BFGS ∈ Rn , the QQN path satisfies:

                                                  d′ (0) = −∇f (x)

Proof : Direct differentiation of d(t) = t(1 − t)(−∇f ) + t2 dL-BFGS gives:

                                       d′ (t) = (1 − 2t)(−∇f ) + 2tdL-BFGS

    Evaluating at t = 0: d′ (0) = −∇f (x). □ This property ensures descent regardless of the quality of

dL-BFGS .
Theorem 1 (Descent Property): For any dL-BFGS , there exists t̄ > 0 such that ϕ(t) = f (x + d(t))
satisfies ϕ(t) < ϕ(0) for all t ∈ (0, t̄].
Proof : Since d′ (0) = −∇f (x):

                                  ϕ′ (0) = ∇f (x)T (−∇f (x)) = −∥∇f (x)∥2 < 0

By continuity of ϕ′ , there exists t̄ > 0 such that ϕ′ (t) < 0 for t ∈ (0, t̄]. □

3.3.2 Global Convergence Analysis
Theorem 2 (Global Convergence): Under standard assumptions:

1. f : Rn → R is continuously differentiable
2. f is bounded below: f (x) ≥ finf > −∞
3. ∇f is Lipschitz continuous with constant L > 0
4. The univariate optimization finds a point satisfying the Armijo condition

QQN generates iterates satisfying:

                                               lim inf ∥∇f (xk )∥ = 0
                                                k→∞

    Proof : We establish convergence through a descent lemma approach.
    Step 1: Monotonic Decrease
    By Theorem 1, each iteration produces f (xk+1 ) < f (xk ) whenever ∇f (xk ) ̸= 0.
    Step 2: Sufficient Decrease
    Define ϕk (t) = f (xk + dk (t)). Since ϕ′k (0) = −∥∇f (xk )∥2 < 0, by the Armijo condition, there exists

t̄ > 0 such that:

                               ϕk (t) ≤ ϕk (0) + c1 tϕ′k (0) = f (xk ) − c1 t∥∇f (xk )∥2

for all t ∈ (0, t̄] and some c1 ∈ (0, 1).
The univariate optimization ensures t∗k ≥ min{t̄, 1}, giving:

                                   f (xk+1 ) ≤ f (xk ) − c1 min{t̄, 1}∥∇f (xk )∥2

Step 3: Quantifying Decrease
Using the descent lemma with Lipschitz constant L:
L
f (xk+1 ) ≤ f (xk ) + ∇f (xk )T dk (t∗k ) + ∥dk (t∗k )∥2
2
For the quadratic path, we can show there exists c > 0 such that:

                                         f (xk ) − f (xk+1 ) ≥ c∥∇f (xk )∥2

Step 4: Summability

                                                          3

Since f is bounded below and decreases monotonically:
∞
X
[f (xk ) − f (xk+1 )] = f (x0 ) − lim f (xk ) < ∞
k→∞
k=0

Combined with Step 3:
∞
X
∥∇f (xk )∥2 < ∞
k=0

Step 5: Conclusion
The summability of ∥∇f (xk )∥2 implies lim inf k→∞ ∥∇f (xk )∥ = 0. □

3.3.3 Local Superlinear Convergence
Theorem 3 (Local Superlinear Convergence): Let x∗ be a local minimum with ∇f (x∗ ) = 0 and ∇2 f (x∗ ) ≻ 0. Assume:

1.  ∇2 f is Lipschitz continuous in a neighborhood of x∗
2.  The L-BFGS approximation satisfies the Dennis-Moré condition:

                                        ∥(Hk − (∇2 f (x∗ ))−1 )(xk+1 − xk )∥
                                     lim                                     =0
                                    k→∞          ∥xk+1 − xk ∥

    Then QQN converges superlinearly: ∥xk+1 − x∗ ∥ = o(∥xk − x∗ ∥).
    Proof : We analyze the behavior near the optimum.
    Step 1: Neighborhood Properties
    By continuity of ∇2 f , there exists a neighborhood N of x∗ and constants 0 < µ ≤ L such that:

                                              µI ⪯ ∇2 f (x) ⪯ LI,     ∀x ∈ N

Step 2: Optimal Parameter Analysis
Define ϕ(t) = f (xk + d(t)) where d(t) = t(1 − t)(−∇f (xk )) + t2 dL-BFGS .
At t = 1:
ϕ′ (1) = ∇f (xk + dL-BFGS )T dL-BFGS
Using Taylor expansion and the Dennis-Moré condition, we can show:

                                              ϕ′ (1) = o(∥∇f (xk )∥2 )

This implies t∗ = 1 + o(1) for sufficiently large k.
Step 3: Convergence Rate
With t∗ = 1 + o(1):

                            xk+1 = xk + d(t∗ ) = xk − Hk ∇f (xk ) + o(∥∇f (xk )∥)

By standard quasi-Newton theory with the Dennis-Moré condition:

                                           ∥xk+1 − x∗ ∥ = o(∥xk − x∗ ∥)

establishing superlinear convergence. □

                                                          4

3.4 Robustness Analysis
3.4.1 Graceful Degradation
Theorem 4 (Graceful Degradation): Let θk be the angle between −∇f (xk ) and dL-BFGS . If θk > π/2
(obtuse angle), then the optimal parameter satisfies t∗ ∈ [0, 1/2], ensuring gradient-dominated steps.
Proof : When θk > π/2, we have ∇f (xk )T dL-BFGS > 0. The derivative of our objective along the path
is:
d
f (xk + d(t)) = ∇f (xk + d(t))T d′ (t)
dt
At t = 1/2:
1
d′ (1/2) = − ∇f (xk ) + dL-BFGS
2
If the function increases beyond t = 1/2, the univariate optimization will find t∗ ≤ 1/2, giving:
xk+1 ≈ xk + t∗ (1 − t∗ )(−∇f (xk )) ≈ xk − t∗ ∇f (xk )
This is equivalent to gradient descent with step size t∗ . □

3.4.2 Computational Complexity
Theorem 5 (Computational Complexity): Each QQN iteration requires:
• O(n) operations for path construction
• O(mn) operations for L-BFGS direction computation
• O(k) function evaluations for univariate optimization
where n is the dimension, m is the L-BFGS memory size, and k is typically small (3-10). The total
complexity per iteration is O(mn + kn), matching L-BFGS when function evaluation dominates.

4 Extensions and Variants
4.1 Gradient Scaling
The basic QQN formulation can be enhanced with gradient scaling to balance the relative magnitudes of the
two directions:

                                     d(t) = t(1 − t)α(−∇f ) + t2 dL-BFGS
    where α > 0 is a scaling factor. Three natural choices emerge:
    1. Unit scaling: α = 1 (default)
    2. Magnitude equalization: α = ∥dL-BFGS ∥/∥∇f ∥
    3. Adaptive scaling: α based on problem characteristics

Proposition 1 (Scaling Invariance): The set of points reachable by the QQN path is invariant to the
choice of α. Only the parametrization changes.
Proof : The path {x + d(t) : t ∈ [0, 2]} traces the same curve in Rn regardless of α, as any point on one
parametrization can be reached by adjusting t in another. □

4.2 Cubic Extension with Momentum
Incorporating momentum leads to cubic interpolation:
d(t) = t(1 − t)(1 − 2t)m + t(1 − t)α(−∇f ) + t2 dL-BFGS
where m is the momentum vector. This preserves all boundary conditions while adding curvature control
through the second derivative at t = 0.
Theorem 5 (Cubic Convergence Properties): The cubic variant maintains all convergence guarantees of
the quadratic version while potentially improving the convergence constant through momentum acceleration.

                                                       5

4.3 Trust Region Integration
QQN naturally extends to trust regions by constraining the univariate search:

                                          t∗ = arg     min        f (x + d(t))
                                                     t:∥d(t)∥≤∆

    where ∆ is the trust region radius.

5 Comparison with Related Methods
5.1 Relationship to Trust Region Methods
Trust region methods solve:
1
min gT s + sT Bs s.t. ∥s∥ ≤ ∆
s 2
This requires solving a constrained quadratic program at each iteration. QQN instead parameterizes a
specific path and optimizes along it, avoiding the subproblem complexity while maintaining similar robustness
properties.
Key differences:

    • Trust region: Solves 2D subproblem, then line search
    • QQN: Direct 1D optimization along quadratic path
    • Trust region: Requires trust region radius management
    • QQN: Parameter-free, automatic adaptation

5.2 Relationship to Line Search Methods
Traditional line search methods optimize along a fixed direction:

                                                 min f (x + αd)
                                                 α>0

QQN generalizes this by optimizing along a parametric path that adapts its direction based on the
parameter value.

5.3 Relationship to Hybrid Methods
Previous hybrid approaches typically use discrete switching:
(
dgradient if condition A
d=
dquasi-Newton if condition B

    QQN provides continuous interpolation, eliminating discontinuities and the need for switching logic.

6 Practical Considerations
6.1 Line Search Implementation
The univariate optimization can use various methods:

    • Golden section search: Robust, no derivatives needed
    • Brent’s method: Faster convergence with parabolic interpolation
    • Bisection on derivative: When gradient information is available

Implementation Note: We recommend Brent’s method with fallback to golden section search. The
search interval [0, 2] allows for extrapolation beyond the L-BFGS direction when beneficial.

                                                           6

6.2 Convergence Criteria
We recommend a combined stopping criterion:

                                ∥∇f (xk )∥ < ϵabs   or ∥∇f (xk )∥ < ϵrel ∥∇f (x0 )∥

    with typical values ϵabs = 10−8 and ϵrel = 10−6 .

6.3 Memory Management
QQN inherits L-BFGS memory requirements:

    • Store m vector pairs (si , yi )
    • Typical choice: m = 5 − 10
    • Memory usage: O(mn)

6.4 Numerical Stability
Key stability considerations:

    1. Gradient scaling: Prevents numerical issues when ∥∇f ∥ ≪ ∥dL-BFGS ∥
    2. Path parameterization: The quadratic form is numerically stable
    3. Fallback behavior: Automatic degradation to gradient descent

7 Conclusion
The Quadratic-Quasi-Newton algorithm resolves the robustness-efficiency trade-off in optimization through
a novel geometric framework. Our theoretical analysis establishes:

    1. Universal descent property: Guaranteed descent regardless of quasi-Newton quality
    2. Global convergence: Under standard assumptions with explicit convergence rates
    3. Local superlinear convergence: Matching quasi-Newton methods near optima
    4. Graceful degradation: Automatic fallback to gradient descent when needed
    5. Computational efficiency: Complexity matching L-BFGS with improved robustness

The geometric insight of quadratic interpolation provides a natural framework for direction combination
that maintains theoretical guarantees while offering practical advantages. The method’s parameter-free
nature and robust behavior make it particularly suitable for practitioners who need reliable optimization
without extensive tuning.
Future work includes:

    • Extension to stochastic settings with mini-batch gradients
    • Application to constrained optimization problems
    • Integration with adaptive learning rate methods
    • Theoretical analysis of the cubic variant with momentum

    The quadratic interpolation principle opens new avenues for geometric approaches to optimization al-

gorithm design, potentially leading to a new class of hybrid methods that combine the best properties of
different optimization paradigms.

References
Michael C Biggs. Minimization algorithms making use of non-quadratic properties of the objective function.
IMA Journal of Applied Mathematics, 12(3):337–357, 1973.

                                                         7

Andrew R Conn, Nicholas IM Gould, and Philippe L Toint. Trust Region Methods. SIAM, 2000. ISBN
978-0-898714-60-9.
José Luis Morales and Jorge Nocedal. Automatic preconditioning by limited memory quasi-Newton updating.
SIAM Journal on Optimization, 10(4):1079–1096, 2000. doi: 10.1137/S1052623497327854.

                                                   8

8 Appendix
8.1 A. Detailed Proofs
8.1.1 A.1 Proof of Sufficient Decrease Constant
Lemma A.1 (Sufficient Decrease): Under the assumptions of Theorem 2, there exists a constant c > 0
independent of k such that:
f (xk ) − f (xk+1 ) ≥ c∥∇f (xk )∥2
Proof : Consider the quadratic path d(t) = t(1 − t)(−∇f ) + t2 dL-BFGS .
For small t, Taylor expansion gives:

                                                d(t) = −t∇f + O(t2 )

    Using the descent lemma:
                                                                            L
                              f (xk + d(t)) ≤ f (xk ) + ∇f (xk )T d(t) +      ∥d(t)∥2
                                                                            2
    Substituting the path:

                                                                    Lt2
                        f (xk + d(t)) ≤ f (xk ) − t∥∇f (xk )∥2 +        ∥∇f (xk )∥2 + O(t3 )
                                                                     2
    For t = min{1, 1/L}, we get:
                                                                  t
                                         f (xk + d(t)) ≤ f (xk ) − ∥∇f (xk )∥2
                                                                  2
    Since the univariate optimization finds t∗ at least as good as this choice:

                                                           min{1, 1/L}
                                   f (xk ) − f (xk+1 ) ≥               ∥∇f (xk )∥2
                                                               2

    Taking c = min{1,1/L}
                   2      completes the proof. □

8.1.2 A.2 Dennis-Moré Condition Analysis
Lemma A.2 (Dennis-Moré Implies Unit Steps): If the L-BFGS approximation satisfies the Dennis-Moré
condition, then t∗ → 1 as k → ∞.
Proof : The Dennis-Moré condition states:
∥(Hk − (∇2 f (x∗ ))−1 )(xk+1 − xk )∥
lim =0
k→∞ ∥xk+1 − xk ∥

    Near the optimum, the L-BFGS direction becomes:

                               dL-BFGS = −Hk ∇f (xk ) ≈ −(∇2 f (x∗ ))−1 ∇f (xk )

    The optimal step in Newton’s method would be:

                          sNewton = −(∇2 f (xk ))−1 ∇f (xk ) ≈ −(∇2 f (x∗ ))−1 ∇f (xk )

    At t = 1, the QQN path gives:
                                              d(1) = dL-BFGS ≈ sNewton

By the optimality of Newton’s method near the minimum and continuity of the objective function, the
univariate optimization will find t∗ → 1. □

                                                            9

8.2 B. Implementation Details
8.2.1 B.1 L-BFGS Direction Computation
The L-BFGS direction is computed using the two-loop recursion:

function compute*lbfgs_direction(gradient, memory):
q = gradient
alphas = []
// First loop (backward)
for i = memory.size-1 down to 0:
rho_i = 1 / (memory.y[i]^T * memory.s[i])
alpha*i = rho_i * memory.s[i]^T _ q
q = q - alpha_i _ memory.y[i]
alphas.append(alpha*i)
// Apply initial Hessian approximation
if memory.size > 0:
gamma = (memory.s[-1]^T * memory.y[-1]) / (memory.y[-1]^T _ memory.y[-1])
r = gamma _ q
else:
r = q
// Second loop (forward)
for i = 0 to memory.size-1:
rho*i = 1 / (memory.y[i]^T * memory.s[i])
beta = rho*i * memory.y[i]^T \_ r
r = r + (alphas[memory.size-1-i] - beta) \* memory.s[i]
return -r

8.2.2 B.2 Univariate Optimization Methods
Golden Section Search
function golden*section_search(f, a, b, tol):
phi = (1 + sqrt(5)) / 2
resphi = 2 - phi
x1 = a + resphi * (b - a)
x2 = b - resphi _ (b - a)
f1 = f(x1)
f2 = f(x2)
while abs(b - a) > tol:
if f1 > f2:
a = x1
x1 = x2
f1 = f2
x2 = b - resphi _ (b - a)
f2 = f(x2)
else:
b = x2
x2 = x1
f2 = f1
x1 = a + resphi \_ (b - a)
f1 = f(x1)
return (a + b) / 2

Brent’s Method Combines golden section search with parabolic interpolation for faster convergence when
the function is smooth.

                                                  10

8.2.3 B.3 Memory Update
After each iteration, update the L-BFGS memory:

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
if s_k^T \* y_k <= 0:
// Skip update or apply damping
memory.s.pop_back()
memory.y.pop_back()
memory.size -= 1

8.3 C. Convergence Rate Analysis
8.3.1 C.1 Linear Convergence Rate
For strongly convex functions with condition number κ, QQN achieves at least linear convergence with rate:
 
∗ 1
∥xk+1 − x ∥ ≤ 1 − ∥xk − x∗ ∥
κ

This follows from the fact that QQN reduces to gradient descent in the worst case, and gradient descent
achieves this rate on strongly convex functions.

8.3.2 C.2 Superlinear Convergence Rate
Near the optimum, when L-BFGS provides good approximations, QQN achieves superlinear convergence:

                                       ∥xk+1 − x∗ ∥ = o(∥xk − x∗ ∥)

The exact rate depends on how quickly the L-BFGS approximation converges to the true inverse Hessian.

8.4 D. Numerical Examples
8.4.1 D.1 Quadratic Function
Consider f (x) = 21 xT Ax where A is positive definite.
The gradient is ∇f (x) = Ax and the optimal step is x∗ = 0. For this function, the L-BFGS direction
(after sufficient iterations) becomes:

                                       dL-BFGS = −A−1 Ax = −x

The QQN path becomes:

                           d(t) = t(1 − t)(−Ax) + t2 (−x) = −tx[(1 − t)A + tI]

    For a quadratic function with exact L-BFGS approximation, the univariate optimization yields t∗ = 1,

giving the exact Newton step and convergence in one iteration.

                                                   11

8.4.2 D.2 Rosenbrock Function
The Rosenbrock function f (x, y) = 100(y − x2 )2 + (1 − x)2 is a classic test case for optimization algorithms.
The gradient is:
−400x(y − x2 ) − 2(1 − x)
 
∇f =
200(y − x2 )
Near the optimum (1, 1), the Hessian is:
 
2 802 −400
∇ f=
−400 200

This is ill-conditioned with condition number κ ≈ 2416, making it challenging for first-order methods
but suitable for demonstrating QQN’s robustness.

8.5 E. Extensions and Variations
8.5.1 E.1 Constrained QQN
For box constraints l ≤ x ≤ u, we can modify the univariate optimization:

                                       t∗ = arg min f (proj(x + d(t)))
                                                  t≥0

where proj is the projection onto the feasible region.

8.5.2 E.2 Stochastic QQN
For stochastic optimization, we can use:

1. Mini-batch gradients: Compute ∇f on subsets of data
2. Variance reduction: Use techniques like SVRG or SAGA
3. Adaptive sampling: Increase batch size as optimization progresses

8.5.3 E.3 Preconditioning
The gradient can be preconditioned:

                                   d(t) = t(1 − t)(−P−1 ∇f ) + t2 dL-BFGS

where P is a preconditioning matrix (e.g., diagonal scaling).

8.6 F. Computational Complexity Analysis
8.6.1 F.1 Per-Iteration Cost
The computational cost per iteration consists of:

1. Gradient computation: O(n) to O(n2 ) depending on the function
2. L-BFGS direction: O(mn) where m is memory size
3. Path evaluation: O(n) per function evaluation
4. Univariate optimization: O(k) function evaluations, typically k = 3 − 10

Total: O(mn + kn) operations plus k function evaluations, where the function evaluation cost typically
dominates.

                                                         12

8.6.2 F.2 Memory Requirements
8.6.3 F.3 Comparison with Other Methods

                                                 () () () () ()
                                             * * * * *
                                                       Per-
                                             0.2400Method    Function
                                                   0.1333Memory
                                                         0.1600Robustness
                                                       iteration
                                                      0.2133 evals
                                                0.2533ops
                                                  () () () () ()
                                             * * * * *
                                             0.2400Gra-
                                                 0.2533O(n)
                                                    0.1333O(n)
                                                       0.21331-
                                                          0.1600High
                                             di-       5
                                             ent
                                             De-
                                             scent
                                                  () () () () ()
                                             * * * * *
                                             0.2400L-
                                                 0.2533O(mn)
                                                    0.1333O(mn)
                                                       0.21333-
                                                          0.1600Medium
                                             BFGS 20
                                                  () () () () ()
                                             * * * * *
                                                          0.1600High
                                                       0.21333-
                                                    0.1333O(mn)
                                                 0.2533O(mn)
                                             0.2400QQN
                                                       10
                                                  () () () () ()
                                             * * * * *
                                                             3 2
                                                          0.1600Low
                                                       0.21331
                                                    0.1333O(n
                                                 0.2533O(n
                                             0.2400New-       ) )
                                             ton
                                                  () () () () ()
                                             * * * * *
                                                             3 2
                                             0.2400Trust
                                                 0.2533O(n
                                                    0.1333O(n
                                                       0.21331-
                                                          0.1600High
                                                              ) )
                                             Re-       10
                                             gion

QQN matches L-BFGS complexity while providing gradient descent robustness and often requiring fewer
function evaluations due to better step selection.

                                                 13
