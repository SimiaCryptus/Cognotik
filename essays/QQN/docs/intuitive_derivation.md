# QQN Algorithm: An Intuitive Derivation

## The Problem We're Solving

Imagine you're standing on a mountainside in thick fog, trying to find the lowest point. You have two advisors giving
you directions:

1. **The Conservative Guide (Gradient)**: Always points directly downhill from where you stand. Safe, but might take
   forever on gentle slopes.
2. **The Aggressive Guide (L-BFGS)**: Uses memory of recent terrain to make educated guesses about shortcuts. Fast when
   right, but can be wildly wrong.

The question is: how do we combine their advice intelligently?

## Setting Up the Mathematical Framework

Let's start with what we have at any point in our optimization:

- **Current parameters**: `x` (our current position)
- **Gradient**: `∇f(x)` (steepest ascent direction)
- **Negative gradient**: `-∇f(x)` (steepest descent direction)
- **L-BFGS direction**: `d_lbfgs` (quasi-Newton guess for best direction)

The key insight is to treat this as a **1-dimensional optimization problem**. Instead of trying to figure out the
optimal step in the full parameter space, we'll define a path and then just optimize along that path.

## Constructing the Path

We want to create a parametric path that smoothly interpolates between our two directional advisors. Let's call our path
parameter `t`, where `t ∈ [0, 1]`.

Our intuition says we want:

- At `t = 0`: Follow the conservative gradient advice
- At `t = 1`: Follow the aggressive L-BFGS advice
- For `0 < t < 1`: Some blend of both

The simplest way to achieve this is with linear interpolation:

```
direction(t) = (1-t) × (-∇f) + t × d_lbfgs
```

This gives us a **unit direction** - it tells us which way to go, but not how far.

## The Missing Piece: Scaling by Distance

Here's the crucial insight that completes the picture: **the direction needs to be scaled by how far we actually want to
move**.

If we just used `direction(t)` directly, we'd always take a step of some fixed magnitude determined by the relative
sizes of our gradient and L-BFGS vectors. But we want `t` to represent both the blend of directions AND the step size.

So our actual step becomes:

```
step(t) = t × direction(t) = t × [(1-t) × (-∇f) + t × d_lbfgs]
```

Expanding this:

```
step(t) = t(1-t) × (-∇f) + t² × d_lbfgs
```

## Gradient Scaling: Controlling Parametric Velocity

An important consideration in the QQN algorithm is the relative magnitudes of the gradient and L-BFGS directions. These vectors often have very different scales:

- The gradient magnitude depends on the function's local steepness
- The L-BFGS direction magnitude depends on the quasi-Newton approximation and previous steps

We can introduce a **gradient scaling factor** `α` to balance these magnitudes:

```
step(t) = t(1-t) × α × (-∇f) + t² × d_lbfgs
```

### Key Insight: Scaling Affects Velocity, Not Geometry

The crucial observation is that the scaling factor `α` **does not change the geometric path** traced in parameter space. To see why, consider the path as a curve parameterized by `t`:

- Without scaling: `curve₁(t) = t(1-t) × (-∇f) + t² × d_lbfgs`
- With scaling: `curve₂(t) = t(1-t) × α × (-∇f) + t² × d_lbfgs`

Both curves pass through the same points:

- Start at the origin (t=0)
- End at `d_lbfgs` (t=1)
- Have the same shape - just traversed at different speeds

What changes is the **parametric velocity** - how quickly we move along the curve as `t` increases. With a larger `α`, we move faster through the gradient-dominated portion of the path (small t) and slower through the L-BFGS-dominated portion (large t).

### Three Scaling Strategies

This leads to three natural choices for the scaling factor:

#### 1. No Scaling (α = 1)

```
step(t) = t(1-t) × (-∇f) + t² × d_lbfgs
```

- Simplest approach
- Uses the natural magnitudes of both vectors
- Works well when gradient and L-BFGS directions have comparable scales

#### 2. Dynamic Scaling (Magnitude Equalization)

```
α = ||d_lbfgs|| / ||∇f||
step(t) = t(1-t) × α × (-∇f) + t² × d_lbfgs
```

- Equalizes the magnitudes of the two direction components
- Ensures balanced contribution from both methods
- Particularly useful when one vector dominates in magnitude
- The 1D search explores the path more uniformly

#### 3. Fixed Hyperparameter Scaling

```
α = user_specified_constant
step(t) = t(1-t) × α × (-∇f) + t² × d_lbfgs
```

- Allows manual control over the relative importance
- Can be tuned based on problem characteristics
- Useful when you have prior knowledge about the optimization landscape

### Impact on the Algorithm

The choice of scaling affects how the 1D line search explores the quadratic path:

- **Large α**: More emphasis on gradient direction, t\* tends to be smaller
- **Small α**: More emphasis on L-BFGS direction, t\* tends to be larger
- **Dynamic α**: Adaptive behavior based on current iteration

Importantly, all three strategies maintain the key properties of QQN:

- Initial descent direction (derivative at t=0 is proportional to -∇f)
- Smooth interpolation between methods
- Automatic adaptation through 1D optimization

## Why This Formula Makes Sense

Let's examine what happens at the boundaries:

**At t = 0:**

```
step(0) = 0(1-0) × (-∇f) + 0² × d_lbfgs = 0
```

We don't move at all. This makes sense - we're starting from our current position.

**At t = 1:**

```
step(1) = 1(1-1) × (-∇f) + 1² × d_lbfgs = d_lbfgs
```

We take a full step in the L-BFGS direction.

**For small t (conservative steps):**

```
step(t) ≈ t × (-∇f)
```

We move primarily in the gradient direction, with step size proportional to `t`.

**The derivative at t = 0:**

```
d/dt step(t)|_{t=0} = (1-2t) × (-∇f) + 2t × d_lbfgs|_{t=0} = -∇f
```

Our initial direction is pure gradient descent, which guarantees we start moving downhill.

## The Geometric Intuition

The path `step(t)` traces out a **quadratic curve** in parameter space:

- It starts at the origin (current position)
- Initially heads in the gradient direction
- Curves toward the L-BFGS direction
- The amount of curvature depends on how different the two directions are

This quadratic nature is why it's called "Quadratic-Quasi-Newton" - we're using a quadratic path to interpolate between
Newton-like and gradient-like steps.

## Converting to 1D Optimization

Now we have a beautiful 1D optimization problem: find the value of `t` that minimizes:

```
f(x_current + step(t))
```

where `step(t) = t(1-t) × (-∇f) + t² × d_lbfgs`

This is much easier than the original multi-dimensional problem! We can use any 1D line search method (golden section,
Brent's method, etc.) to find the optimal `t*`.

## The Complete Algorithm

1. **Compute directions**: Calculate `-∇f` and `d_lbfgs` at current position
2. **Define quadratic path**: `step(t) = t(1-t) × (-∇f) + t² × d_lbfgs`
3. **1D optimization**: Find `t*` that minimizes `f(x + step(t))`
4. **Update parameters**: `x_new = x_old + step(t*)`
5. **Update L-BFGS memory**: Store the step and gradient change for future iterations

## Why This Works So Well

The genius of this approach is that it:

- **Guarantees descent**: The initial direction is always downhill
- **Adapts automatically**: If L-BFGS direction is good, `t*` will be close to 1. If it's bad, `t*` will be small and
  we'll mostly follow the gradient
- **Provides smooth interpolation**: No abrupt switches between methods
- **Reduces to known methods**: Pure gradient descent when `t* ≈ 0`, pure L-BFGS when `t* ≈ 1`
- **Handles edge cases gracefully**: Even if L-BFGS gives a terrible direction, we can always fall back to gradient
  descent

The quadratic interpolation gives us the best of both worlds: the reliability of gradient descent with the speed of
quasi-Newton methods, automatically balanced based on what the function actually looks like along our search path.

## Key Benefits Over Traditional Methods

### Simplified L-BFGS Implementation

One of the most significant advantages of QQN is that it **dramatically simplifies the L-BFGS algorithm**. Traditional
L-BFGS implementations require:

- Complex line search procedures with Wolfe conditions
- Careful handling of step size initialization
- Multiple fallback strategies when the quasi-Newton direction fails
- Intricate logic for handling edge cases and numerical issues
  With QQN, we strip away this complexity. We only need L-BFGS to provide a **quasi-Newton guess** for the direction.
  The 1D optimization along our quadratic path automatically handles:
- Step size selection
- Ensuring sufficient decrease
- Fallback to gradient descent when needed
- All edge cases through the natural behavior of the interpolation
  This means the L-BFGS component can be much simpler - it just needs to maintain its memory and produce a direction
  estimate. All the robustness comes from the QQN framework itself.

### Essentially Parameter-Free

Another remarkable property of QQN is that it's **essentially parameter-free**. Unlike traditional optimization methods
that require careful tuning of:

- Initial step sizes
- Line search parameters (c1, c2 for Wolfe conditions)
- Trust region radii
- Momentum coefficients
- Learning rate schedules
  QQN has no essential hyperparameters of its own. The only parameters in the implementation belong to:

1. **Component elements**: Like the memory size for L-BFGS (typically 5-10, not sensitive)
2. **Nonessential thresholds**: Like convergence tolerances or maximum iterations
3. **1D search method**: Which typically has robust defaults
   This parameter-free nature means:

- No hyperparameter tuning needed
- Consistent behavior across different problems
- Easy to use as a drop-in replacement
- Reduced risk of misconfiguration
- More time spent on the actual problem rather than optimizer tuning
  The algorithm automatically adapts to the problem at hand through the 1D optimization, finding the right balance
  between gradient and quasi-Newton steps without any user intervention.

The only optional parameter that might be worth considering is the gradient scaling strategy, but even this has a sensible default (no scaling or dynamic scaling) that works well in practice.

## Extension: Adding Momentum with Cubic Interpolation

The quadratic interpolation we've developed provides a powerful framework, but we can extend it further by incorporating momentum. This leads to a **cubic interpolation** scheme that captures even more information about the optimization landscape.

### The Momentum Concept

In optimization, momentum helps accelerate convergence by accumulating a velocity vector based on past gradients. The key insight is that this momentum vector provides information about the **second derivative** (curvature) of our path at the starting point.

### From Quadratic to Cubic

Recall our quadratic path:

```
step(t) = t(1-t) × α × (-∇f) + t² × d_lbfgs
```

To incorporate momentum, we extend this to a cubic polynomial:

```
step(t) = t(1-t)(1-2t) × m + t(1-t) × α × (-∇f) + t² × d_lbfgs
```

where `m` is our momentum vector.

### Understanding the Cubic Terms

Let's break down what each term contributes:

1. **Momentum term**: `t(1-t)(1-2t) × m`
   - Zero at t=0 and t=1 (preserves our endpoints)
   - Maximum influence around t=0.5
   - Controls the initial curvature of the path
2. **Gradient term**: `t(1-t) × α × (-∇f)`
   - Provides the initial descent direction
   - Same role as in quadratic version
3. **L-BFGS term**: `t² × d_lbfgs`
   - Dominates as t→1
   - Unchanged from quadratic version

### The Second Derivative Connection

The crucial property is what happens to the derivatives at t=0:

**First derivative (velocity)**:

```
d/dt step(t)|_{t=0} = -∇f
```

Still pure gradient descent initially!

**Second derivative (acceleration)**:

```
d²/dt² step(t)|_{t=0} = -2m - 2α × (-∇f) + 2d_lbfgs
```

The momentum vector `m` directly influences the initial acceleration of our path. This is why we say it "defines the starting second-derivative."

### Computing the Momentum Vector

There are several ways to define the momentum vector:

#### 1. Classical Momentum

```
m_{k+1} = β × m_k + (1-β) × (-∇f_k)
```

where β ∈ [0,1] is the momentum coefficient.

#### 2. Nesterov-style Momentum

```
m_{k+1} = β × m_k + (-∇f(x_k + β × m_k))
```

Uses a "lookahead" gradient for better convergence properties.

#### 3. Adaptive Momentum

```
m_{k+1} = β(t*_k) × m_k + (1-β(t*_k)) × (-∇f_k)
```

where β depends on the optimal t\* from the previous iteration, automatically adjusting momentum based on how well our directions are working.

### The Complete Cubic QQN Algorithm

1. **Initialize**: Start with m₀ = 0 (no initial momentum)
2. **Compute directions**: Calculate `-∇f`, `d_lbfgs`, and update `m`
3. **Define cubic path**:
   ```
   step(t) = t(1-t)(1-2t) × m + t(1-t) × α × (-∇f) + t² × d_lbfgs
   ```
4. **1D optimization**: Find `t*` that minimizes `f(x + step(t))`
5. **Update parameters**: `x_new = x_old + step(t*)`
6. **Update momentum**: Using one of the schemes above
7. **Update L-BFGS memory**: As before

### Benefits of Cubic Interpolation

The cubic extension provides several advantages:

- **Richer path space**: Can represent more complex curves between gradient and L-BFGS directions
- **Momentum acceleration**: Benefits from momentum's ability to accelerate through consistent gradient directions
- **Smoother trajectories**: The additional degree of freedom allows for smoother optimization paths
- **Better handling of valleys**: Momentum helps navigate narrow valleys in the loss landscape

### When to Use Cubic vs Quadratic

**Use Quadratic QQN when**:

- Simplicity is paramount
- The problem has relatively simple geometry
- You want minimal computational overhead
- Starting from a good initialization

**Use Cubic QQN when**:

- Dealing with ill-conditioned problems
- The loss landscape has long, narrow valleys
- You can afford slightly more computation per step
- Previous experience shows momentum helps

### Implementation Considerations

The cubic version requires:

- Maintaining an additional momentum vector
- Slightly more complex path evaluation (one extra term)
- Choosing a momentum update scheme
- Potentially tuning the momentum coefficient β (though adaptive schemes can eliminate this)

Despite these additions, the cubic QQN remains remarkably simple compared to traditional optimizers with momentum, as all the complexity is still handled by the 1D line search.

### A Unified View

Both quadratic and cubic QQN can be seen as instances of a general polynomial interpolation framework:

- **Linear**: Pure gradient descent (degenerate case)
- **Quadratic**: Interpolates between gradient and quasi-Newton
- **Cubic**: Adds momentum via second-derivative control
- **Higher-order**: Possible but rarely necessary in practice

The beauty is that each extension maintains the core benefits: guaranteed descent, automatic adaptation, and essential parameter-free operation. The 1D optimization framework elegantly handles all the complexity, regardless of the polynomial degree.

## Appendix: Constrained QQN with Trust Regions and Protective Geometry

The QQN framework naturally extends to constrained optimization through the concept of **trust regions** and **protective geometry**. By modifying how we evaluate and constrain our quadratic (or cubic) path, we can incorporate various types of constraints while maintaining the simplicity of 1D optimization.

### The Core Idea: Constrained Path Evaluation

In unconstrained QQN, we optimize:

```
minimize f(x + step(t)) over t ∈ [0, 1]
```

For constrained QQN, we modify this to:

```
minimize f(x + project(step(t))) over t ∈ [0, 1]
```

where `project()` is a projection operator that ensures our step respects the constraints.

### Trust Region Framework

A trust region defines a set of "acceptable" parameters around our current point. The key insight is that we can apply trust region constraints **during** the 1D line search rather than as a separate step.

#### Basic Trust Region Projection

For a simple spherical trust region with radius δ:

```
project(step) = step × min(1, δ / ||step||)
```

This scales down the step if it would exceed the trust region boundary.

#### Adaptive Trust Regions

The trust region can adapt based on the quality of our quadratic approximation:

```
δ_{k+1} = {
  increase if t* ≈ 1 (L-BFGS direction is good)
  decrease if t* ≈ 0 (falling back to gradient)
  maintain otherwise
}
```

### Sparsity Constraints (OWL-QN Style)

One of the most powerful applications is incorporating L1 regularization for sparsity, similar to OWL-QN (Orthant-Wise Limited-memory Quasi-Newton).

#### Orthant Constraints

The key idea is to constrain our search to stay within the current orthant (sign pattern) or move coordinates to zero:

```
project_orthant(x, step, t) = {
  for each coordinate i:
    if x[i] > 0 and x[i] + step[i](t) < 0:
      project to x[i] + step[i](t) = 0
    if x[i] < 0 and x[i] + step[i](t) > 0:
      project to x[i] + step[i](t) = 0
    else:
      use x[i] + step[i](t)
}
```

#### Soft Thresholding Integration

For L1 regularization with parameter λ, we can integrate soft thresholding:

```
project_l1(step, λ) = sign(step) × max(|step| - λ, 0)
```

This naturally induces sparsity by pushing small components to exactly zero.

### Non-Degradation Constraints

A unique application of constrained QQN is ensuring that certain metrics don't degrade, even if they're not part of the main objective.

#### Protective Geometry for Auxiliary Datasets

Suppose we have:

- Primary objective: f(x) on training data
- Protected metric: g(x) on validation data

We can define a protective trust region:

```
TrustRegion = {step : g(x + step) ≥ g(x) - ε}
```

#### Efficient Implementation

Instead of evaluating g at every point, we can use a linear approximation:

```
g(x + step) ≈ g(x) + ∇g(x)ᵀ step
```

This gives us a half-space constraint:

```
∇g(x)ᵀ step ≥ -ε
```

During the 1D search, we project onto this half-space:

```
project_protect(step) = {
  if ∇g(x)ᵀ step ≥ -ε: return step
  else: return step - [(∇g(x)ᵀ step + ε) / ||∇g(x)||²] × ∇g(x)
}
```

### Composable Trust Regions

One of the most powerful aspects of this framework is that different trust regions can be **composed** naturally.

#### Intersection of Constraints

Given multiple trust regions T₁, T₂, ..., Tₙ, we can apply them sequentially:

```
project_composed(step) = projectₙ(...project₂(project₁(step)))
```

Or find their intersection more directly for certain types of constraints.

#### Example: Sparsity + Non-degradation

```
step_constrained(t) = project_protect(project_l1(step(t), λ))
```

This ensures both sparsity and protection of auxiliary metrics.

### Box Constraints and Bounded Parameters

For parameters with known bounds [l, u]:

```
project_box(x, step) = clip(x + step, l, u) - x
```

This modifies the step to ensure we stay within bounds.

### Manifold Constraints

For optimization on manifolds (e.g., orthogonal matrices, positive definite matrices):

```
project_manifold(x, step) = retract(x, tangent_project(x, step))
```

where:

- `tangent_project` projects the step onto the tangent space at x
- `retract` maps from the tangent space back to the manifold

### Implementation Strategy

The constrained QQN algorithm becomes:

1. **Compute directions**: As before (-∇f, d_lbfgs, possibly momentum)
2. **Define path**: step(t) using quadratic or cubic interpolation
3. **Constrained 1D search**:
   ```
   minimize f(x + project(step(t))) over t ∈ [0, 1]
   ```
4. **Update**: x_new = x + project(step(t\*))
5. **Update memories**: L-BFGS and momentum as usual

### Key Advantages

This constrained QQN framework offers several benefits:

1. **Unified treatment**: All constraints handled through the same projection mechanism
2. **Maintains simplicity**: Still just a 1D optimization problem
3. **Automatic adaptation**: The optimal t\* automatically adjusts based on how constraints affect the path
4. **Composability**: Different constraints combine naturally
5. **Efficiency**: Many projections can be computed analytically

### Practical Considerations

#### Projection Ordering

When composing multiple projections, the order can matter. Generally:

1. Apply hard constraints first (box bounds, manifold constraints)
2. Apply soft constraints next (sparsity, regularization)
3. Apply protective constraints last (non-degradation)

#### Computational Cost

Some projections are cheap (box constraints, L1), while others may be expensive (general convex sets, manifolds). The 1D line search naturally limits the number of projection evaluations needed.

#### Trust Region Adaptation

The trust region size can be adapted based on:

- Constraint activity (how often we hit boundaries)
- Progress quality (actual vs predicted decrease)
- Problem phase (exploration vs convergence)
