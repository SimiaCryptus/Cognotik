# Optimizer Parameter Schema and Instantiation Guide

This comprehensive guide covers the parameter schemas, configuration options, and instantiation patterns for all optimizers in the QQN research framework.

## Table of Contents

1. [Overview](#overview)
2. [Common Patterns](#common-patterns)
3. [Gradient Descent (GD)](#gradient-descent-gd)
4. [Adam Optimizer](#adam-optimizer)
5. [L-BFGS Optimizer](#l-bfgs-optimizer)
6. [QQN Optimizer](#qqn-optimizer)
7. [Trust Region Optimizer](#trust-region-optimizer)
8. [Configuration Best Practices](#configuration-best-practices)
9. [Examples and Use Cases](#examples-and-use-cases)

## Overview

All optimizers in the framework follow a consistent pattern:

1. **Configuration struct** (`*Config`) - Defines all parameters
2. **State struct** (`*State`) - Maintains internal optimization state
3. **Optimizer struct** (`*Optimizer`) - Main optimization engine
4. **Preset configurations** - Pre-defined parameter sets for common use cases

### Common Configuration Principles

- **Sensible defaults**: All configurations provide reasonable default values
- **Preset variants**: `strict()`, `lax()`, `debug()` presets for different scenarios
- **Comprehensive validation**: Parameters are validated at creation time
- **Serialization support**: Configurations can be saved/loaded via serde

## Common Patterns

### Basic Instantiation Pattern

```rust
use qqn::optimizers::*;

// 1. Create configuration
let config = OptimizerConfig::default();

// 2. Customize if needed
let config = OptimizerConfig {
    learning_rate: 0.001,
    ..Default::default()
};

// 3. Create optimizer
let optimizer = Optimizer::new(config);
```

### Using Presets

```rust
// Conservative settings for stability
let optimizer = Optimizer::new(OptimizerConfig::strict());

// Aggressive settings for speed
let optimizer = Optimizer::new(OptimizerConfig::lax());

// Debug settings with verbose logging
let optimizer = Optimizer::new(OptimizerConfig::debug());
```

## Gradient Descent (GD)

### Parameter Schema

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GDConfig {
    /// Optimizer identifier
    pub name: String,

    /// Learning rate (step size)
    /// Range: 1e-6 to 1.0, Default: 0.01
    /// Higher values: faster convergence, risk of divergence
    /// Lower values: stable but slow convergence
    pub learning_rate: f64,

    /// Momentum coefficient
    /// Range: 0.0 to 0.99, Default: 0.0
    /// 0.0: no momentum, 0.9: high momentum
    /// Accelerates convergence, smooths oscillations
    pub momentum: f64,

    /// Weight decay (L2 regularization)
    /// Range: 0.0 to 0.1, Default: 0.0
    /// Prevents overfitting by penalizing large parameters
    pub weight_decay: f64,

    /// Enable Nesterov momentum
    /// Default: false
    /// Uses "look-ahead" gradients for better convergence
    pub nesterov: bool,

    /// Maximum gradient norm for clipping
    /// Range: 0.0 (disabled) to 100.0, Default: 10.0
    /// Prevents gradient explosion
    pub max_grad_norm: f64,

    /// Enable adaptive learning rate
    /// Default: true
    /// Automatically reduces LR for large gradients
    pub adaptive_lr: bool,

    /// Minimum learning rate for adaptive scaling
    /// Range: 1e-10 to 1e-4, Default: 1e-7
    pub min_learning_rate: f64,

    /// Enable verbose logging
    /// Default: false
    pub verbose: bool,
}
```

### Instantiation Examples

```rust
use qqn::optimizers::gd::{GDConfig, GDOptimizer};

// Basic usage with defaults
let optimizer = GDOptimizer::new(GDConfig::default());

// Custom configuration
let config = GDConfig {
    name: "CustomGD".to_string(),
    learning_rate: 0.001,
    momentum: 0.9,
    nesterov: true,
    max_grad_norm: 5.0,
    adaptive_lr: true,
    weight_decay: 1e-4,
    verbose: false,
    ..Default::default()
};
let optimizer = GDOptimizer::new(config);

// Using presets
let strict_optimizer = GDOptimizer::new(GDConfig::strict());
let lax_optimizer = GDOptimizer::new(GDConfig::lax());
let rosenbrock_optimizer = GDOptimizer::new(GDConfig::rosenbrock());
let debug_optimizer = GDOptimizer::new(GDConfig::debug());
```

### Preset Configurations

#### `GDConfig::strict()`

- **Use case**: Ill-conditioned problems, production stability
- **Learning rate**: 0.001 (very conservative)
- **Momentum**: 0.0 (no momentum to avoid overshooting)
- **Gradient clipping**: 1.0 (aggressive clipping)
- **Adaptive LR**: Enabled for additional safety

#### `GDConfig::lax()`

- **Use case**: Well-conditioned problems, fast experimentation
- **Learning rate**: 0.1 (aggressive)
- **Momentum**: 0.9 with Nesterov acceleration
- **Gradient clipping**: 100.0 (relaxed)
- **Adaptive LR**: Disabled for consistent behavior

#### `GDConfig::rosenbrock()`

- **Use case**: Non-convex optimization, narrow valleys
- **Learning rate**: 0.001 (handles steep gradients)
- **Momentum**: 0.9 with Nesterov (navigates valleys)
- **Gradient clipping**: 10.0 (moderate stability)
- **Adaptive LR**: Enabled for varying gradient magnitudes

## Adam Optimizer

### Parameter Schema

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AdamConfig {
    /// Learning rate (step size)
    /// Range: 1e-5 to 1e-1, Default: 0.001
    /// Controls magnitude of parameter updates
    pub learning_rate: f64,

    /// Learning rate schedule strategy
    /// Options: "constant", "exponential", "cosine", "adaptive"
    /// Default: "constant"
    pub lr_schedule: String,

    /// Decay rate for exponential schedule
    /// Range: 0.9 to 0.999, Default: 0.999
    pub lr_decay: f64,

    /// Minimum learning rate floor
    /// Range: 1e-15 to 1e-8, Default: 1e-12
    pub min_learning_rate: f64,

    /// Gradient clipping threshold
    /// Range: 0.5 to 5.0 or None, Default: None
    /// Prevents exploding gradients
    pub gradient_clip: Option<f64>,

    /// First moment decay rate (momentum)
    /// Range: 0.0 to 0.999, Default: 0.9
    /// Higher values: more momentum, smoother updates
    pub beta1: f64,

    /// Second moment decay rate (adaptive LR)
    /// Range: 0.9 to 0.9999, Default: 0.999
    /// Higher values: more stable adaptive rates
    pub beta2: f64,

    /// Numerical stability constant
    /// Range: 1e-12 to 1e-8, Default: 1e-8
    /// Prevents division by zero
    pub epsilon: f64,

    /// Weight decay coefficient
    /// Range: 0.0 to 0.01, Default: 0.0
    /// L2 regularization strength
    pub weight_decay: f64,

    /// Enable AMSGrad variant
    /// Default: false
    /// Better theoretical convergence properties
    pub amsgrad: bool,

    /// Maximum line search iterations
    /// Range: 5 to 50, Default: 20
    pub max_line_search_iter: usize,

    /// Enable verbose logging
    /// Default: false
    pub verbose: bool,
}
```

### Instantiation Examples

```rust
use qqn::optimizers::adam::{AdamConfig, AdamOptimizer};

// Basic usage
let optimizer = AdamOptimizer::autoname(AdamConfig::default());

// Custom configuration
let config = AdamConfig {
    learning_rate: 0.001,
    lr_schedule: "adaptive".to_string(),
    beta1: 0.9,
    beta2: 0.999,
    epsilon: 1e-8,
    weight_decay: 1e-4,
    gradient_clip: Some(1.0),
    amsgrad: false,
    verbose: false,
    ..Default::default()
};
let optimizer = AdamOptimizer::new("CustomAdam".to_string(), config);

// Using presets
let strict_optimizer = AdamOptimizer::autoname(AdamConfig::strict());
let lax_optimizer = AdamOptimizer::autoname(AdamConfig::lax());
let dl_optimizer = AdamOptimizer::autoname(AdamConfig::deep_learning());
```

### Preset Configurations

#### `AdamConfig::strict()`

- **Use case**: High-precision optimization, scientific computing
- **Learning rate**: 0.0001 (10x smaller than default)
- **Schedule**: "adaptive" with automatic reduction
- **Gradient clipping**: 0.5 (tight control)
- **AMSGrad**: Enabled for convergence guarantees
- **Epsilon**: 1e-12 (higher precision)

#### `AdamConfig::lax()`

- **Use case**: Rapid prototyping, approximate solutions
- **Learning rate**: 0.01 (10x larger than default)
- **Schedule**: "exponential" decay
- **Gradient clipping**: None (allows large steps)
- **Beta2**: 0.99 (faster adaptation)
- **Epsilon**: 1e-6 (lower precision for speed)

#### `AdamConfig::deep_learning()`

- **Use case**: Neural network training
- **Learning rate**: 0.001 (proven effective for NNs)
- **Schedule**: "cosine" annealing
- **Gradient clipping**: 1.0 (prevents exploding gradients)
- **Weight decay**: 0.01 (moderate regularization)

## L-BFGS Optimizer

### Parameter Schema

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LBFGSConfig {
    /// Number of previous iterations for Hessian approximation
    /// Range: 1 to 50, Typical: 5-20, Default: 10
    /// Larger values: better approximation, more memory
    pub history_size: usize,

    /// Line search configuration
    /// Controls step size selection using strong Wolfe conditions
    pub line_search: LineSearchConfig,

    /// Numerical stability constant
    /// Range: 1e-16 to 1e-6, Default: 1e-8
    /// Used in curvature condition checks
    pub epsilon: f64,

    /// Maximum correction pairs in two-loop recursion
    /// Range: 1 to history_size, Default: 10
    /// Limits computational cost
    pub max_correction_pairs: usize,

    /// Maximum allowed step size per iteration
    /// Range: 0.1 to 100+, Default: 2.0
    /// Prevents numerical instability
    pub max_step_size: f64,

    /// Minimum step size before convergence failure
    /// Range: 1e-20 to 1e-10, Default: 1e-16
    pub min_step_size: f64,

    /// Maximum parameter change per iteration (L∞ norm)
    /// Range: 0.01 to 1000+, Default: 1.0
    /// Set to 0.0 to disable
    pub max_param_change: f64,

    /// Gradient clipping threshold
    /// Range: 0.0 (disabled) to 1e6+, Default: 1e3
    pub gradient_clip: f64,

    /// Enable recovery mechanism for stagnation
    /// Default: true
    pub enable_recovery: bool,

    /// Iterations without improvement before recovery
    /// Range: 1 to 20, Default: 5
    pub recovery_patience: usize,

    /// Enable verbose logging
    /// Default: false
    pub verbose: bool,

    /// Optimizer name
    /// Default: "L-BFGS"
    pub name: String,
}
```

### Line Search Configuration

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LineSearchConfig {
    /// Line search method
    /// Options: StrongWolfe, Backtracking, Bisection, etc.
    pub method: LineSearchMethod,

    /// Armijo condition parameter (sufficient decrease)
    /// Range: 1e-6 to 1e-2, Default: 1e-4
    pub c1: f64,

    /// Curvature condition parameter
    /// Range: 0.1 to 0.9, Default: 0.9 (for L-BFGS)
    pub c2: f64,

    /// Maximum line search iterations
    /// Range: 5 to 100, Default: 20
    pub max_iterations: usize,

    /// Initial step size
    /// Range: 0.1 to 10.0, Default: 1.0
    pub initial_step: f64,

    /// Minimum step size
    /// Range: 1e-16 to 1e-8, Default: 1e-8
    pub min_step: f64,

    /// Maximum step size
    /// Range: 10.0 to 1000.0, Default: 100.0
    pub max_step: f64,

    /// Enable verbose logging
    /// Default: false
    pub verbose: bool,
}
```

### Instantiation Examples

```rust
use qqn::optimizers::lbfgs::{LBFGSConfig, LBFGSOptimizer};
use qqn::line_search::{LineSearchConfig, LineSearchMethod};

// Basic usage
let optimizer = LBFGSOptimizer::new(LBFGSConfig::default());

// Custom configuration
let line_search_config = LineSearchConfig {
    method: LineSearchMethod::StrongWolfe,
    c1: 1e-4,
    c2: 0.9,
    max_iterations: 20,
    initial_step: 1.0,
    ..Default::default()
};

let config = LBFGSConfig {
    name: "CustomLBFGS".to_string(),
    history_size: 15,
    line_search: line_search_config,
    epsilon: 1e-8,
    max_step_size: 5.0,
    gradient_clip: 1e2,
    enable_recovery: true,
    recovery_patience: 3,
    verbose: false,
    ..Default::default()
};
let optimizer = LBFGSOptimizer::new(config);

// Using presets
let strict_optimizer = LBFGSOptimizer::new(LBFGSConfig::strict());
let lax_optimizer = LBFGSOptimizer::new(LBFGSConfig::lax());
let qqn_optimizer = LBFGSOptimizer::new(LBFGSConfig::for_qqn());
```

### Preset Configurations

#### `LBFGSConfig::strict()`

- **Use case**: Ill-conditioned problems, high precision
- **History size**: 5 (reduces memory effects)
- **Max step size**: 0.5 (conservative)
- **Max param change**: 0.1 (gradual progress)
- **Epsilon**: 1e-10 (high precision)
- **Recovery patience**: 10 (patient recovery)

#### `LBFGSConfig::lax()`

- **Use case**: Well-conditioned problems, fast convergence
- **History size**: 20 (better approximation)
- **Max step size**: 50.0 (large steps allowed)
- **Max param change**: 100.0 (big jumps allowed)
- **Curvature condition**: 0.1 (relaxed)
- **Recovery patience**: 2 (quick recovery)

#### `LBFGSConfig::for_qqn()`

- **Use case**: Component within QQN algorithm
- **History size**: 10 (balanced)
- **Gradient clipping**: 0.0 (disabled - QQN handles)
- **Recovery**: Disabled (QQN manages adaptation)
- **Curvature condition**: 0.5 (balanced)

## QQN Optimizer

### Parameter Schema

```rust
#[derive(Debug, Clone)]
pub struct QQNConfig {
    /// Optimizer instance name
    /// Default: "QQN"
    pub name: String,

    /// L-BFGS history length
    /// Range: 3 to 50, Default: 10
    /// Controls memory usage and approximation quality
    pub lbfgs_history: usize,

    /// Minimum iterations before enabling L-BFGS
    /// Range: 0 to 10, Default: 1
    /// Uses steepest descent initially
    pub min_lbfgs_iterations: usize,

    /// Line search configuration
    /// Default: Bisection method
    pub line_search: LineSearchConfig,

    /// Numerical stability constant
    /// Range: 1e-10 to 1e-4, Default: 1e-6
    pub epsilon: f64,

    /// Enable verbose logging
    /// Default: false
    pub verbose: bool,

    /// Minimum step size for persistence
    /// Range: 1e-4 to 1.0, Default: 1e-1
    /// Step sizes below this aren't persisted for next iteration
    pub min_step_persist: f64,

    /// Absolute minimum step size
    /// Range: 1e-15 to 1e-8, Default: 1e-10
    pub min_step_size: f64,

    /// Scaling factor for gradient descent direction
    /// Range: 0.1 to 10.0, Default: 1.0
    /// Allows line search to explore larger steps
    pub gradient_scale_factor: f64,
}
```

### Instantiation Examples

```rust
use qqn::optimizers::qqn::{QQNConfig, QQNOptimizer};
use qqn::line_search::{LineSearchConfig, LineSearchMethod};

// Basic usage
let optimizer = QQNOptimizer::new(QQNConfig::default());

// Custom configuration
let line_search_config = LineSearchConfig {
    method: LineSearchMethod::Bisection,
    max_iterations: 30,
    c1: 1e-4,
    c2: 0.1,
    ..Default::default()
};

let config = QQNConfig {
    name: "CustomQQN".to_string(),
    lbfgs_history: 15,
    min_lbfgs_iterations: 2,
    line_search: line_search_config,
    epsilon: 1e-8,
    verbose: false,
    min_step_persist: 1e-2,
    min_step_size: 1e-12,
    gradient_scale_factor: 2.0,
};
let optimizer = QQNOptimizer::new(config);

// Using presets
let strict_optimizer = QQNOptimizer::new(QQNConfig::strict());
let lax_optimizer = QQNOptimizer::new(QQNConfig::lax());
let verbose_optimizer = QQNOptimizer::new(QQNConfig::verbose());
```

### Preset Configurations

#### `QQNConfig::strict()`

- **Use case**: Robust convergence, ill-conditioned problems
- **L-BFGS history**: 20 (better approximation)
- **Min L-BFGS iterations**: 5 (more steepest descent)
- **Line search**: 50 max iterations
- **Epsilon**: 1e-8 (tighter stability)
- **Gradient scale factor**: 1.0 (conservative)

#### `QQNConfig::lax()`

- **Use case**: Fast convergence, well-conditioned problems
- **L-BFGS history**: 5 (computational efficiency)
- **Min L-BFGS iterations**: 1 (quick L-BFGS activation)
- **Line search**: 20 max iterations
- **Epsilon**: 1e-4 (looser stability)
- **Gradient scale factor**: 1.0 (aggressive)

#### `QQNConfig::verbose()`

- **Use case**: Debugging, analysis, education
- **Verbose**: true (detailed logging)
- **Otherwise**: Default parameters

## Trust Region Optimizer

### Parameter Schema

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrustRegionConfig {
    /// Initial trust region radius
    /// Range: 0.1 to 10.0, Default: 1.0
    pub initial_radius: f64,

    /// Maximum trust region radius
    /// Range: 1.0 to 1000.0, Default: 100.0
    pub max_radius: f64,

    /// Minimum radius before convergence
    /// Range: 1e-10 to 1e-4, Default: 1e-8
    pub min_radius: f64,

    /// Threshold for accepting a step
    /// Range: 0.0 to 0.5, Default: 0.1
    /// Ratio of actual to predicted reduction
    pub eta_1: f64,

    /// Threshold for expanding trust region
    /// Range: 0.5 to 1.0, Default: 0.75
    pub eta_2: f64,

    /// Factor for shrinking trust region
    /// Range: 0.1 to 0.5, Default: 0.25
    pub gamma_1: f64,

    /// Factor for expanding trust region
    /// Range: 1.5 to 4.0, Default: 2.0
    pub gamma_2: f64,

    /// Maximum subproblem iterations
    /// Range: 10 to 100, Default: 50
    pub max_subproblem_iterations: usize,

    /// Subproblem tolerance
    /// Range: 1e-10 to 1e-4, Default: 1e-6
    pub subproblem_tolerance: f64,

    /// Use Cauchy point fallback
    /// Default: true
    pub use_cauchy_fallback: bool,

    /// Enable verbose logging
    /// Default: false
    pub verbose: bool,

    /// Optimizer name
    /// Default: "TrustRegion"
    pub name: String,
}
```

### Instantiation Examples

```rust
use qqn::optimizers::trust_region::{TrustRegionConfig, TrustRegionOptimizer};

// Basic usage
let optimizer = TrustRegionOptimizer::new(TrustRegionConfig::default());

// Custom configuration
let config = TrustRegionConfig {
    name: "CustomTR".to_string(),
    initial_radius: 0.5,
    max_radius: 50.0,
    min_radius: 1e-10,
    eta_1: 0.15,
    eta_2: 0.8,
    gamma_1: 0.3,
    gamma_2: 2.5,
    max_subproblem_iterations: 75,
    subproblem_tolerance: 1e-8,
    use_cauchy_fallback: true,
    verbose: false,
};
let optimizer = TrustRegionOptimizer::new(config);

// Using presets
let conservative_optimizer = TrustRegionOptimizer::new(TrustRegionConfig::conservative());
let aggressive_optimizer = TrustRegionOptimizer::new(TrustRegionConfig::aggressive());
```

### Preset Configurations

#### `TrustRegionConfig::conservative()`

- **Use case**: Stable convergence, sensitive problems
- **Initial radius**: 0.5 (small initial region)
- **Max radius**: 10.0 (limited expansion)
- **Accept threshold**: 0.2 (stricter acceptance)
- **Expand threshold**: 0.8 (conservative expansion)
- **Shrink factor**: 0.2 (aggressive shrinking)

#### `TrustRegionConfig::aggressive()`

- **Use case**: Fast convergence, well-behaved problems
- **Initial radius**: 2.0 (large initial region)
- **Max radius**: 1000.0 (unlimited expansion)
- **Accept threshold**: 0.05 (lenient acceptance)
- **Expand threshold**: 0.5 (quick expansion)
- **Expand factor**: 3.0 (aggressive expansion)
