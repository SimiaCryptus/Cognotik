# Technical Summary: Non-QQN Optimizer Strategies

This document provides a comprehensive technical analysis of the non-QQN optimization algorithms implemented in our framework, covering L-BFGS, Gradient Descent, and Adam optimizers with their various configurations and strategic variants.

## 1. L-BFGS (Limited-memory Broyden-Fletcher-Goldfarb-Shanno)

### Core Algorithm

L-BFGS is a quasi-Newton method that approximates the inverse Hessian matrix using a limited history of gradient and parameter changes. The algorithm maintains:

- **Parameter differences**: `s_k = x_{k+1} - x_k`
- **Gradient differences**: `y_k = g_{k+1} - g_k`
- **Curvature information**: `ρ_k = 1/(s_k^T y_k)`

The search direction is computed using the two-loop recursion algorithm, which efficiently applies the inverse Hessian approximation without storing the full matrix.

### Configuration Strategies

#### Default Configuration

```rust
LBFGSConfig {
    history_size: 10,
    line_search: LineSearchConfig {
        c1: 1e-4,  // Armijo condition
        c2: 0.9,   // Curvature condition
        initial_step: 1.0,
        max_step: 2.0,
    },
    max_step_size: 2.0,
    min_step_size: 1e-16,
    max_param_change: 1.0,
    gradient_clip: 1e3,
    enable_recovery: true,
    recovery_patience: 5,
}
```

#### Aggressive Configuration

- **Reduced history size**: 5 (faster computation, less memory)
- **Larger step limits**: `max_step_size: 10.0`, `max_param_change: 10.0`
- **Disabled gradient clipping**: Allows full gradient magnitude
- **Strategy**: Prioritizes speed over stability, suitable for well-conditioned problems

#### Hybrid Configuration

- **Balanced history**: 12 corrections (good approximation quality)
- **Moderate constraints**: `max_step_size: 5.0`, `max_param_change: 2.0`
- **Controlled gradient clipping**: `gradient_clip: 50.0`
- **Strategy**: Balances convergence speed with numerical stability

### Key Technical Features

1. **Powell's Damping**: Handles negative curvature by modifying `y_k` when `s_k^T y_k < threshold`
2. **Gamma Scaling**: Initial Hessian approximation `γ = (s_k^T y_k)/(y_k^T y_k)`
3. **Recovery Mechanism**: Resets history when no improvement is detected
4. **Numerical Safeguards**: Finite value checks and gradient magnitude limits

## 2. Gradient Descent Variants

### Core Algorithm

Implements various gradient descent strategies with momentum, adaptive learning rates, and regularization:

```
v_t = β * v_{t-1} + (1-β) * g_t  (momentum)
x_{t+1} = x_t - α * v_t           (parameter update)
```

### Configuration Strategies

#### Default Configuration

```rust
GDConfig {
    learning_rate: 0.01,
    momentum: 0.0,
    adaptive_lr: true,
    max_grad_norm: 10.0,
    min_learning_rate: 1e-7,
}
```

#### Conservative Configuration

- **Low learning rate**: 0.001 for stability
- **High momentum**: 0.95 for smooth convergence
- **Nesterov acceleration**: Improved momentum variant
- **Fixed learning rate**: Predictable behavior
- **Strategy**: Prioritizes stability and convergence guarantees

#### Momentum Configuration

- **Standard momentum**: 0.9 coefficient
- **Adaptive learning rate**: Scales based on gradient magnitude
- **Strategy**: Accelerates convergence while maintaining stability

### Technical Features

1. **Adaptive Learning Rate**:

   ```rust
   let adaptive_factor = if grad_norm > threshold {
       1.0 / (1.0 + (grad_norm / threshold).ln())
   } else { 1.0 };
   ```

2. **Gradient Clipping**: Prevents gradient explosion
3. **Weight Decay**: L2 regularization `grad += weight_decay * param`
4. **Nesterov Momentum**: Look-ahead gradient computation

## 3. Adam (Adaptive Moment Estimation)

### Core Algorithm

Adam combines momentum with adaptive per-parameter learning rates using first and second moment estimates:

```
m_t = β₁ * m_{t-1} + (1-β₁) * g_t        (first moment)
v_t = β₂ * v_{t-1} + (1-β₂) * g_t²       (second moment)
m̂_t = m_t / (1 - β₁^t)                   (bias correction)
v̂_t = v_t / (1 - β₂^t)                   (bias correction)
x_{t+1} = x_t - α * m̂_t / (√v̂_t + ε)    (parameter update)
```

### Configuration Strategies

#### Fast Configurations

Multiple variants optimized for different scenarios:

1. **Adam-Fast**: `learning_rate: 0.1`, constant schedule
2. **Adam-Fast-Conservative**: Adds gradient clipping (`1.0`)
3. **Adam-Fast-Aggressive**: Higher LR (`0.125`), reduced β₁ (`0.85`)
4. **Adam-Fast-Adaptive**: Adaptive LR schedule with gradient clipping
5. **Adam-Fast-Momentum**: Enhanced momentum (`β₁: 0.95`)
6. **Adam-Fast-Regularized**: Includes weight decay (`0.01`)

### Learning Rate Schedules

#### Exponential Decay

```rust
current_lr *= lr_decay;
current_lr = current_lr.max(min_learning_rate);
```

#### Cosine Annealing

```rust
let cosine_decay = 0.5 * (1.0 + (π * t / T).cos());
current_lr = min_lr + (max_lr - min_lr) * cosine_decay;
```

#### Adaptive Schedule

```rust
if relative_improvement < threshold {
    bad_step_count += 1;
    if bad_step_count >= patience {
        current_lr *= 0.5;  // Aggressive reduction
    }
}
```

### Technical Features

1. **AMSGrad Variant**: Maintains maximum of past squared gradients
2. **Bias Correction**: Compensates for initialization bias
3. **Gradient Clipping**: Optional norm-based clipping
4. **Weight Decay**: Decoupled L2 regularization

## 4. Strategic Optimizer Selection

### Problem-Specific Recommendations

#### Well-Conditioned Quadratic Problems

- **L-BFGS-Aggressive**: Fast convergence with large steps
- **Adam-Fast**: High learning rate with momentum

#### Ill-Conditioned Problems

- **L-BFGS-Hybrid**: Balanced approach with safeguards
- **GD-Conservative**: Stable, predictable convergence

#### Non-Convex Landscapes (e.g., Rosenbrock)

- **GD-Conservative**: Nesterov momentum for valley navigation
- **Adam-Fast-Adaptive**: Automatic learning rate adjustment

#### High-Dimensional Problems

- **Adam variants**: Efficient per-parameter adaptation
- **L-BFGS**: Limited memory requirements

### Performance Characteristics

#### Convergence Speed Ranking

1. **L-BFGS-Aggressive**: Fastest for smooth problems
2. **Adam-Fast variants**: Consistent across problem types
3. **GD-Conservative**: Steady but slower convergence

#### Numerical Stability Ranking

1. **GD-Conservative**: Most stable with safeguards
2. **L-BFGS-Hybrid**: Good balance of speed and stability
3. **Adam-Fast-Conservative**: Stable with gradient clipping

#### Memory Efficiency Ranking

1. **GD variants**: Minimal memory overhead
2. **Adam**: Moderate memory for moment estimates
3. **L-BFGS**: Higher memory for history storage

## 5. Implementation Highlights

### Numerical Safeguards

- **Finite value checking**: Prevents NaN/Inf propagation
- **Gradient magnitude limits**: Prevents numerical overflow
- **Step size bounds**: Ensures reasonable parameter updates
- **Recovery mechanisms**: Automatic state reset on failure

### Performance Optimizations

- **Vectorized operations**: Efficient tensor computations
- **Memory pre-allocation**: Reduces allocation overhead
- **Early termination**: Convergence detection prevents unnecessary iterations
- **Adaptive tolerances**: Problem-aware convergence criteria

### Monitoring and Diagnostics

- **Comprehensive metadata**: Gradient norms, step sizes, convergence metrics
- **Timing information**: Performance profiling capabilities
- **Verbose logging**: Detailed optimization progress tracking
- **State serialization**: Checkpoint and resume functionality

This comprehensive suite of optimizers provides robust solutions across a wide range of optimization problems, from simple quadratic functions to complex non-convex landscapes, with careful attention to both performance and numerical stability.
