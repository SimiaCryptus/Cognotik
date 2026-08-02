# QQN (Quasi-Quadratic-Newton) Algorithm Technical Documentation

## Overview

The QQN (Quasi-Quadratic-Newton) algorithm is a novel optimization method that combines the robustness of steepest descent with the efficiency of L-BFGS through a unique quadratic interpolation scheme. This implementation provides a sophisticated approach to unconstrained optimization that adaptively blends gradient descent and quasi-Newton directions.

## Algorithm Description

### Core Concept

QQN operates by constructing a quadratic path between two search directions:

1. **Steepest descent direction**: `-∇f(x)` (negative gradient)
2. **L-BFGS direction**: `-H∇f(x)` (quasi-Newton direction with approximate inverse Hessian H)

The algorithm searches along a parametric curve defined by:

```
d(t) = t(1-t)(-∇f) + t²(-H∇f)
```

where `t ∈ [0, 1]` is the interpolation parameter.

### Key Properties

- **t = 0**: Pure steepest descent direction
- **t = 1**: Pure L-BFGS direction
- **0 < t < 1**: Smooth blend between the two directions

This formulation ensures:

- The direction is always a descent direction (for small enough steps)
- Smooth transition between conservative (gradient) and aggressive (quasi-Newton) steps
- Adaptive behavior based on problem characteristics

## Implementation Architecture

### Main Components

#### 1. **QQNOptimizer**

The main optimizer class that orchestrates the optimization process.

```rust
pub struct QQNOptimizer {
    _config: QQNConfig,
    state: QQNState,
    line_search: Box<dyn LineSearch>,
}
```

#### 2. **QQNConfig**

Configuration parameters controlling optimizer behavior:

- `lbfgs_history`: Number of gradient/parameter pairs to store (default: 10)
- `min_lbfgs_iterations`: Iterations before enabling L-BFGS (default: 1)
- `line_search`: Line search configuration
- `epsilon`: Numerical stability constant (default: 1e-6)
- `verbose`: Enable detailed logging
- `min_step_persist`: Minimum step size to persist for next iteration (default: 1e-1)
- `min_step_size`: Minimum allowed step size (default: 1e-10)

#### 3. **QQNState**

Internal state tracking:

- `iteration`: Current iteration count
- `lbfgs_state`: L-BFGS history and parameters
- `previous_step_size`: Cached step size for warm-starting line search

#### 4. **QuadraticPath**

Represents the quadratic interpolation path with caching:

```rust
pub struct QuadraticPath {
    start_point: Vec<Tensor>,
    negative_gradient: Vec<Tensor>,
    lbfgs_direction: Vec<Tensor>,
    lbfgs_state: Arc<Mutex<LBFGSState>>,
    function: Arc<dyn DifferentiableFunction + Send + Sync>,
}
```

## Algorithm Flow

### Step-by-Step Process

1. **Initialization Phase**
   - Compute gradients at current position
   - Validate inputs for NaN/Inf values
   - Check iteration count against `min_lbfgs_iterations`

2. **Direction Selection**
   - If `iteration < min_lbfgs_iterations`: Use steepest descent
   - Otherwise: Compute L-BFGS direction using stored history

3. **Quadratic Path Construction**
   - Create `QuadraticPath` object with:
     - Current position as start point
     - Negative gradient as first direction
     - L-BFGS direction as second direction

4. **Line Search Along Quadratic Path**
   - Convert to 1D optimization problem
   - Find optimal `t*` using configured line search method
   - Warm-start with previous step size if available

5. **Parameter Update**
   - Compute new position: `x_new = x_old + d(t*)`
   - Verify function decrease (fatal error if increase)
   - Update L-BFGS history with new gradient information

6. **State Management**
   - Increment iteration counter
   - Cache successful step size for next iteration
   - Update convergence metrics

### Fallback Mechanisms

The algorithm includes multiple robustness features:

1. **Steepest Descent Fallback**
   - Triggered when L-BFGS direction is invalid
   - Used for initial iterations
   - Applied when line search fails

2. **Step Size Adaptation**
   - Conservative steps for large gradients
   - Adaptive initial step based on problem scale

## Mathematical Details

### Quadratic Path Formula

The direction at parameter t is:

```
d(t) = t(1-t)(-∇f) + t²(d_lbfgs)
```

The derivative with respect to t:

```
d'(t) = (1-2t)(-∇f) + 2t(d_lbfgs)
```

### Properties

1. **Boundary Conditions**:
   - d(0) = 0 (start at current point)
   - d'(0) = -∇f (initial direction is steepest descent)
   - d(1) = d_lbfgs (end at L-BFGS direction)

2. **Curvature**:
   - The path curves from steepest descent toward L-BFGS
   - Provides smooth interpolation between conservative and aggressive steps

## Performance Optimizations

### 1. **L-BFGS State Updates**

- Updates are performed opportunistically when both position and gradient are available
- Skipped for very small steps to maintain numerical stability

### 2. **Warm-Starting**

- Previous successful step sizes are used to initialize line search
- Significantly reduces line search iterations

## Configuration Profiles

### Default Configuration

Balanced settings for general use:

```rust
QQNConfig::default()
```

### Strict Configuration

Conservative settings for difficult problems:

```rust
QQNConfig::strict()
```

- Larger L-BFGS history (20)
- More steepest descent iterations (5)
- Tighter tolerances

### Lax Configuration

Aggressive settings for well-conditioned problems:

```rust
QQNConfig::lax()
```

- Smaller L-BFGS history (5)
- Immediate L-BFGS usage
- Looser tolerances

## Error Handling

### Fatal Errors

- Function value increase after step (violates descent property)
- NaN/Inf in gradients or parameters
- Empty parameter vectors

### Recoverable Errors

- Line search failure → fallback to steepest descent
- Invalid L-BFGS direction → use gradient descent
- Non-finite values in L-BFGS computation → reset history

## Usage Example

```rust
use candle_lbfgs::{QQNOptimizer, QQNConfig};

// Create optimizer with custom configuration
let _config = QQNConfig {
    lbfgs_history: 15,
    min_lbfgs_iterations: 3,
    verbose: true,
    ..QQNConfig::default()
};
let mut optimizer = QQNOptimizer::new(_config);

// Optimize
let mut params = initial_params();
let function = Arc::new(MyFunction::new());

for _ in 0..max_iterations {
    let result = optimizer.step(&mut params, function.clone())?;

    if result.convergence_info.converged {
        break;
    }
}
```

## Advantages

1. **Adaptive Behavior**: Automatically balances between conservative and aggressive steps
2. **Robustness**: Multiple fallback mechanisms ensure progress
3. **Efficiency**: L-BFGS acceleration when appropriate
4. **Smooth Transitions**: Quadratic interpolation avoids abrupt direction changes

## Limitations

1. **Memory Requirements**: Stores L-BFGS history (O(m×n) where m is history size, n is parameter dimension)
2. **Computational Overhead**: Quadratic path evaluation adds complexity
3. **Parameter Tuning**: Performance sensitive to configuration settings

## Theoretical Guarantees

Under standard assumptions (smooth, bounded gradients):

- **Global Convergence**: Guaranteed due to steepest descent fallback
- **Superlinear Convergence**: Near optimum when L-BFGS direction dominates
- **Descent Property**: Every step decreases function value (enforced)

## References

The QQN algorithm combines ideas from:

- L-BFGS (Limited-memory Broyden-Fletcher-Goldfarb-Shanno)
- Trust region methods (quadratic models)
- Adaptive step size selection
- Gradient descent with momentum
