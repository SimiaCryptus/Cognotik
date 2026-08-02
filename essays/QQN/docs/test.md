# QQN Optimizer Testing and Benchmarking System

## Technical Documentation

### Table of Contents

1. [System Overview](#system-overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Benchmark Framework](#benchmark-framework)
5. [Statistical Analysis](#statistical-analysis)
6. [Reporting System](#reporting-system)
7. [Usage Guide](#usage-guide)
8. [Extension Guide](#extension-guide)
9. [Performance Considerations](#performance-considerations)
10. [Troubleshooting](#troubleshooting)

---

## System Overview

The QQN Optimizer Testing and Benchmarking System is a comprehensive Rust-based framework designed for rigorous evaluation and comparison of optimization algorithms. The system provides standardized benchmarking capabilities, statistical analysis, and automated report generation for academic and research purposes.

### Key Features

- **Standardized Benchmarking**: Consistent evaluation across multiple optimization problems
- **Statistical Rigor**: Welch's t-tests, Cohen's d effect sizes, and significance testing
- **Comprehensive Reporting**: HTML reports with plots, CSV exports, and academic-quality documentation
- **Extensible Architecture**: Easy addition of new optimizers and benchmark problems
- **Robust Error Handling**: Graceful handling of numerical errors and convergence failures
- **Performance Monitoring**: Detailed tracking of function evaluations, execution time, and memory usage

### Design Principles

1. **Reproducibility**: Deterministic results with proper random seed management
2. **Fairness**: Equal treatment of all optimizers with consistent termination criteria
3. **Transparency**: Complete logging of all evaluation metrics and convergence behavior
4. **Academic Standards**: Publication-ready statistical analysis and reporting

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Experiment Runner                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Benchmark     │  │   Statistical   │  │   Report     │ │
│  │   Framework     │  │   Analysis      │  │  Generator   │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Core Components                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Optimizer     │  │  Optimization   │  │   Problem    │ │
│  │    Trait        │  │   Problems      │  │   Wrapper    │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Utility Layer                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Math Utils    │  │   Plotting      │  │   File I/O   │ │
│  │                 │  │   Manager       │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Module Structure

```
src/
├── benchmarks/
│   ├── evaluation.rs          # Core benchmarking framework
│   ├── functions.rs           # Optimization problem trait
│   └── analytic_functions.rs  # Standard test functions
├── core/
│   ├── optimizer.rs           # Optimizer trait and types
│   └── lbfgs.rs              # L-BFGS implementation
├── utils/
│   └── math.rs               # Mathematical utilities
└── lib.rs                    # Public API

tests/
├── experiment_runner/
│   ├── mod.rs                # Main experiment orchestration
│   ├── report_generator.rs   # HTML/CSV report generation
│   ├── plotting_manager.rs   # Visualization generation
│   ├── statistical_analysis.rs # Statistical testing
│   └── standard_optimizers.rs # Optimizer configurations
└── benchmark_analytic_experiments.rs # Integration tests
```

---

## Core Components

### 1. Optimizer Trait

The `Optimizer` trait defines the interface that all optimization algorithms must implement:

```rust
pub trait Optimizer: Send + Sync + Debug + 'static {
    fn clone_box(&self) -> Box<dyn Optimizer>;
    fn step(
        &mut self,
        params: &mut [Tensor],
        function: Arc<dyn DifferentiableFunction + Send + Sync>,
    ) -> CandleResult<StepResult>;
    fn reset(&mut self);
    fn name(&self) -> &str;
    fn iteration(&self) -> usize;
    fn stagnation_multiplier(&self) -> f64;
    fn stagnation_count(&self) -> usize;
    // ... additional methods
}
```

#### Key Methods

- **`step()`**: Performs a single optimization iteration
- **`reset()`**: Resets optimizer state for new runs
- **`stagnation_multiplier()`**: Provides relaxed convergence criteria
- **`stagnation_count()`**: Threshold for applying relaxed criteria

#### StepResult Structure

```rust
pub struct StepResult {
    pub step_size: f64,
    pub convergence_info: ConvergenceInfo,
    pub metadata: OptimizationMetadata,
}
```

### 2. Optimization Problems

The `OptimizationProblem` trait defines benchmark problems:

```rust
pub trait OptimizationProblem: Send + Sync + Debug {
    fn name(&self) -> &str;
    fn dimension(&self) -> usize;
    fn evaluate_f64(&self, x: &[f64]) -> Result<f64, Box<dyn std::error::Error>>;
    fn gradient_f64(&self, x: &[f64]) -> Result<Vec<f64>, Box<dyn std::error::Error>>;
    fn initial_point(&self) -> Vec<f64>;
    fn optimal_value(&self) -> Option<f64>;
    fn clone_problem(&self) -> Box<dyn OptimizationProblem>;
}
```

#### Standard Test Functions

The system includes implementations of classical optimization benchmarks:

- **Convex Functions**: Sphere, Matyas
- **Non-Convex Unimodal**: Rosenbrock, Beale, Goldstein-Price, Levi
- **Highly Multimodal**: Rastrigin, Ackley, Michalewicz, Styblinski-Tang

### 3. Problem Wrapper

The `ProblemWrapper` bridges optimization problems with the differentiable function interface:

```rust
pub struct ProblemWrapper {
    problem: Box<dyn OptimizationProblem>,
    function_evaluations: Arc<AtomicUsize>,
    gradient_evaluations: Arc<AtomicUsize>,
}
```

This wrapper:

- Tracks function and gradient evaluation counts
- Converts between tensor and f64 vector formats
- Provides thread-safe evaluation counting

---

## Benchmark Framework

### BenchmarkRunner

The `BenchmarkRunner` orchestrates the execution of optimization benchmarks:

```rust
pub struct BenchmarkRunner {
    _config: BenchmarkConfig,
}
```

#### Configuration

```rust
pub struct BenchmarkConfig {
    pub max_iterations: usize,           // Maximum optimization iterations
    pub maximum_function_calls: usize,   // Function evaluation limit
    pub min_improvement_percent: f64,    // Minimum improvement threshold
    pub time_limit: DurationWrapper,     // Wall-clock time limit
    pub num_runs: usize,                 // Independent runs per configuration
}
```

#### Execution Flow

1. **Initialization**: Reset optimizer and randomize starting point
2. **Optimization Loop**:
   - Evaluate function and gradient
   - Check convergence criteria
   - Perform optimizer step
   - Update trace and counters
3. **Termination**: Record final results and convergence reason

#### Convergence Criteria

The system employs multiple convergence criteria:

- **Gradient Tolerance**: `||∇f(x)|| < ε_grad`
- **Function Tolerance**: Percentage improvement threshold
- **Maximum Iterations**: Hard limit on optimization steps
- **Maximum Evaluations**: Limit on function/gradient calls
- **Time Limit**: Wall-clock time constraint
- **Numerical Errors**: Detection of non-finite values

#### Error Handling

Robust error handling includes:

- **Numerical Error Tracking**: Count and limit non-finite values
- **Graceful Degradation**: Continue with valid results when possible
- **Detailed Logging**: Record error conditions and recovery attempts

### Optimization Trace

The system maintains detailed traces of optimization progress:

```rust
pub struct OptimizationTrace {
    pub iterations: Vec<IterationData>,
    pub total_function_evaluations: usize,
    pub total_gradient_evaluations: usize,
}

pub struct IterationData {
    pub iteration: usize,
    pub function_value: f64,
    pub gradient_norm: f64,
    pub step_size: f64,
    pub parameters: Vec<f64>,
    pub timestamp: DurationWrapper,
    pub total_function_evaluations: usize,
    pub total_gradient_evaluations: usize,
}
```

### Results Structure

```rust
pub struct SingleResult {
    pub problem_name: String,
    pub optimizer_name: String,
    pub run_id: usize,
    pub final_value: f64,
    pub final_gradient_norm: f64,
    pub iterations: usize,
    pub function_evaluations: usize,
    pub gradient_evaluations: usize,
    pub convergence_achieved: bool,
    pub execution_time: Duration,
    pub trace: OptimizationTrace,
    pub convergence_reason: ConvergenceReason,
    pub performance_metrics: PerformanceMetrics,
    pub error_message: Option<String>,
}
```

---

## Statistical Analysis

### Statistical Testing Framework

The `StatisticalAnalysis` component provides rigorous statistical comparisons:

#### Welch's t-test Implementation

```rust
fn welch_t_test(&self, sample_a: &[f64], sample_b: &[f64]) -> anyhow::Result<(f64, f64)> {
    // Calculate means
    let mean_a = sample_a.iter().sum::<f64>() / sample_a.len() as f64;
    let mean_b = sample_b.iter().sum::<f64>() / sample_b.len() as f64;

    // Calculate variances
    let var_a = sample_a.iter().map(|x| (x - mean_a).powi(2)).sum::<f64>()
        / (sample_a.len() - 1) as f64;
    let var_b = sample_b.iter().map(|x| (x - mean_b).powi(2)).sum::<f64>()
        / (sample_b.len() - 1) as f64;

    // Calculate standard error and t-statistic
    let se = (var_a / sample_a.len() as f64 + var_b / sample_b.len() as f64).sqrt();
    let t_stat = (mean_a - mean_b) / se;

    // Calculate degrees of freedom (Welch-Satterthwaite equation)
    let df = /* ... complex calculation ... */;

    // Compute p-value
    let p_value = self.t_distribution_p_value(t_stat.abs(), df);
    Ok((t_stat, p_value))
}
```

#### Effect Size Calculation

Cohen's d effect size:

```rust
fn cohens_d(&self, sample_a: &[f64], sample_b: &[f64]) -> f64 {
    let mean_a = sample_a.iter().sum::<f64>() / sample_a.len() as f64;
    let mean_b = sample_b.iter().sum::<f64>() / sample_b.len() as f64;

    let var_a = sample_a.iter().map(|x| (x - mean_a).powi(2)).sum::<f64>()
        / (sample_a.len() - 1) as f64;
    let var_b = sample_b.iter().map(|x| (x - mean_b).powi(2)).sum::<f64>()
        / (sample_b.len() - 1) as f64;

    let pooled_sd = ((var_a + var_b) / 2.0).sqrt();
    (mean_a - mean_b) / pooled_sd
}
```

#### Comparison Methodology

1. **Grouping**: Results grouped by problem family for increased statistical power
2. **Metrics**: Comparison on final objective values and computational cost
3. **Significance Level**: α = 0.05 for statistical significance
4. **Multiple Comparisons**: Pairwise comparisons between QQN and non-QQN optimizers

### Performance Analysis

#### Success Rate Calculation

```rust
pub fn success_rates(&self) -> HashMap<String, f64> {
    let mut rates = HashMap::new();
    for optimizer_name in self.get_optimizer_names() {
        let results = self.get_results_for_optimizer(&optimizer_name);
        let successful = results.iter().filter(|r| r.convergence_achieved).count();
        let total = results.len();
        rates.insert(optimizer_name, successful as f64 / total as f64);
    }
    rates
}
```

#### Performance Metrics

```rust
pub struct PerformanceMetrics {
    pub iterations_per_second: f64,
    pub function_evaluations_per_second: f64,
    pub gradient_evaluations_per_second: f64,
    pub convergence_rate: f64,
}
```

---

## Reporting System

### HTML Report Generation

The `ReportGenerator` creates comprehensive HTML reports with:

#### Executive Summary

- Total problems and runs
- Overall success rates by optimizer
- Family-specific performance breakdowns

#### Problem-Specific Analysis

- Performance tables with statistical highlighting
- Convergence plots (linear and logarithmic scales)
- Detailed metrics comparison

#### Statistical Analysis Section

- Pairwise comparison matrices
- Significance testing results
- Effect size interpretations

#### Report Structure

```html
<!DOCTYPE html>
<html>
  <head>
    <title>QQN Optimizer Benchmark Results</title>
    <style>
      /* Academic styling */
    </style>
  </head>
  <body>
    <div class="header"><!-- Title and metadata --></div>
    <div class="section"><!-- Executive Summary --></div>
    <div class="section"><!-- Problem Analysis --></div>
    <div class="section"><!-- Statistical Analysis --></div>
    <div class="section"><!-- Conclusions --></div>
    <footer><!-- Generation info --></footer>
  </body>
</html>
```

### CSV Export System

Multiple CSV formats for different analysis needs:

#### Detailed Results (`detailed_results.csv`)

```csv
Problem,ProblemFamily,Dimension,Optimizer,Run,FinalValue,FinalGradientNorm,
Iterations,FunctionEvals,GradientEvals,Time,Converged,ConvergenceReason
```

#### Summary Statistics (`summary_statistics.csv`)

```csv
Problem,ProblemFamily,Dimension,Optimizer,MeanFinalValue,MeanFinalValueSuccess,
MeanFinalValueFail,StdFinalValue,BestValue,WorstValue,MeanIterations,
MeanFunctionEvals,MeanTime,SuccessRate,NumRuns
```

#### Statistical Analysis (`statistical_analysis_raw_data.csv`)

```csv
Problem,QQN_Optimizer,NonQQN_Optimizer,Metric,Winner,Test_Statistic,
P_Value,Significant,Effect_Size
```

### Plotting System

The `PlottingManager` generates visualizations:

#### Convergence Plots

- Linear and logarithmic scales
- Multiple optimizers per plot
- Median convergence curves with confidence intervals

#### Performance Comparisons

- Bar charts of success rates
- Box plots of performance distributions
- Scatter plots of efficiency metrics

#### Implementation Example

```rust
pub async fn generate_convergence_plot(
    &self,
    problem_name: &str,
    results: &BenchmarkResults,
) -> anyhow::Result<()> {
    // Extract convergence data
    let mut optimizer_traces = HashMap::new();
    for result in &results.results {
        optimizer_traces
            .entry(result.optimizer_name.clone())
            .or_insert_with(Vec::new)
            .push(&result.trace);
    }

    // Generate plot data
    let plot_data = self.prepare_convergence_data(&optimizer_traces)?;

    // Create and save plots
    self.create_convergence_plot(&plot_data, problem_name, false).await?;
    self.create_convergence_plot(&plot_data, problem_name, true).await?; // Log scale

    Ok(())
}
```

---

## Usage Guide

### Basic Usage

```rust
use qqn_optimizer::benchmarks::evaluation::{BenchmarkConfig, BenchmarkRunner};
use std::time::Duration;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Configure benchmark parameters
    let _config = BenchmarkConfig {
        max_iterations: 10000,
        maximum_function_calls: 50000,
        min_improvement_percent: 0.01,
        time_limit: Duration::from_secs(600).into(),
        num_runs: 10,
    };

    // Create runner and execute benchmarks
    let runner = BenchmarkRunner::new(_config);
    let results = runner.run_benchmarks(problems, optimizers).await?;

    // Save results
    results.save_to_file(&Path::new("results.json"))?;

    Ok(())
}
```

### Comprehensive Experiment

```rust
use experiment_runner::ExperimentRunner;

let experiment = ExperimentRunner::new(
    "results/experiment_001".to_string(),
    _config
);

experiment.run_comparative_benchmarks(
    problems,
    optimizers
).await?;
```

### Custom Optimizer Integration

```rust
#[derive(Debug)]
struct MyOptimizer {
    // Optimizer state
}

impl Optimizer for MyOptimizer {
    fn step(&mut self, params: &mut [Tensor], function: Arc<dyn DifferentiableFunction>)
        -> CandleResult<StepResult> {
        // Implementation
    }

    fn name(&self) -> &str { "MyOptimizer" }
    // ... other required methods
}
```

### Custom Problem Definition

```rust
#[derive(Debug, Clone)]
struct MyProblem {
    dimension: usize,
}

impl OptimizationProblem for MyProblem {
    fn evaluate_f64(&self, x: &[f64]) -> Result<f64, Box<dyn std::error::Error>> {
        // Objective function implementation
    }

    fn gradient_f64(&self, x: &[f64]) -> Result<Vec<f64>, Box<dyn std::error::Error>> {
        // Gradient computation
    }

    // ... other required methods
}
```

---

## Extension Guide

### Adding New Optimizers

1. **Implement the Optimizer Trait**:

   ```rust
   impl Optimizer for NewOptimizer {
       fn step(&mut self, params: &mut [Tensor], function: Arc<dyn DifferentiableFunction>)
           -> CandleResult<StepResult> {
           // Core optimization logic
       }

       fn reset(&mut self) {
           // Reset internal state
       }

       // ... implement all required methods
   }
   ```

2. **Add to Standard Optimizers**:
   ```rust
   pub fn standard_optimizers() -> Vec<(String, Arc<dyn Optimizer>)> {
       vec![
           // ... existing optimizers
           ("NewOptimizer".to_string(), Arc::new(NewOptimizer::new())),
       ]
   }
   ```

### Adding New Benchmark Problems

1. **Implement OptimizationProblem**:

   ```rust
   #[derive(Debug, Clone)]
   pub struct NewFunction {
       dimension: usize,
   }

   impl OptimizationProblem for NewFunction {
       fn name(&self) -> &str { "NewFunction" }
       fn dimension(&self) -> usize { self.dimension }

       fn evaluate_f64(&self, x: &[f64]) -> Result<f64, Box<dyn std::error::Error>> {
           // Function evaluation
       }

       fn gradient_f64(&self, x: &[f64]) -> Result<Vec<f64>, Box<dyn std::error::Error>> {
           // Gradient computation
       }

       fn initial_point(&self) -> Vec<f64> {
           // Starting point
       }

       fn optimal_value(&self) -> Option<f64> {
           // Known optimum (if available)
       }

       fn clone_problem(&self) -> Box<dyn OptimizationProblem> {
           Box::new(self.clone())
       }
   }
   ```

2. **Update Problem Family Classification**:
   ```rust
   pub fn get_family(problem_name: &str) -> String {
       match problem_name.split([' ', '_']).next().unwrap_or(problem_name) {
           // ... existing cases
           "NewFunction" => "Custom Family".to_string(),
           // ...
       }
   }
   ```

### Extending Statistical Analysis

1. **Add New Statistical Tests**:

   ```rust
   impl StatisticalAnalysis {
       pub fn mann_whitney_u_test(&self, sample_a: &[f64], sample_b: &[f64])
           -> anyhow::Result<(f64, f64)> {
           // Non-parametric test implementation
       }
   }
   ```

2. **Custom Performance Metrics**:
   ```rust
   pub struct ExtendedPerformanceMetrics {
       pub base_metrics: PerformanceMetrics,
       pub custom_metric: f64,
   }
   ```

### Custom Report Sections

```rust
impl ReportGenerator {
    fn generate_custom_analysis(&self, results: &[BenchmarkResults]) -> String {
        // Custom analysis implementation
    }
}
```

---

## Performance Considerations

### Memory Management

1. **Trace Data**: Large optimization traces can consume significant memory

   ```rust
   // Consider limiting trace size for long runs
   if trace.iterations.len() > MAX_TRACE_SIZE {
       trace.iterations.truncate(MAX_TRACE_SIZE);
   }
   ```

2. **Result Storage**: Use streaming for large result sets
   ```rust
   // Write results incrementally rather than storing all in memory
   ```

### Computational Efficiency

1. **Parallel Execution**: Runs are independent and can be parallelized

   ```rust
   // Future enhancement: parallel run execution
   let futures: Vec<_> = (0..num_runs)
       .map(|run_id| run_single_benchmark(problem, optimizer, run_id))
       .collect();
   ```

2. **Function Evaluation Caching**: For expensive functions
   ```rust
   struct CachedProblem {
       inner: Box<dyn OptimizationProblem>,
       cache: HashMap<Vec<OrderedFloat<f64>>, f64>,
   }
   ```

### Scalability Considerations

1. **Large Problem Sets**: Consider batch processing
2. **Long Runs**: Implement checkpointing for recovery
3. **Resource Limits**: Monitor memory and CPU usage

---

## Troubleshooting

### Common Issues

#### 1. Numerical Instability

**Symptoms**: Non-finite function values, gradient explosion
**Solutions**:

- Adjust initial point randomization range
- Implement gradient clipping
- Use more robust line search parameters

```rust
// Example: Gradient clipping
let gradient_norm = gradient.iter().map(|g| g * g).sum::<f64>().sqrt();
if gradient_norm > MAX_GRADIENT_NORM {
    for g in gradient.iter_mut() {
        *g *= MAX_GRADIENT_NORM / gradient_norm;
    }
}
```

#### 2. Convergence Issues

**Symptoms**: No convergence achieved, excessive iterations
**Solutions**:

- Relax convergence criteria
- Adjust stagnation parameters
- Check problem scaling

```rust
// Example: Adaptive tolerance
let adaptive_tolerance = base_tolerance * (1.0 + problem_scale_factor);
```

#### 3. Memory Issues

**Symptoms**: Out of memory errors, slow performance
**Solutions**:

- Limit trace storage
- Use streaming I/O
- Reduce number of runs

#### 4. Statistical Analysis Failures

**Symptoms**: Invalid t-test results, NaN values
**Solutions**:

- Check sample sizes (minimum 2 per group)
- Verify data validity (no infinite values)
- Use non-parametric alternatives for skewed data

### Debugging Tools

#### 1. Verbose Logging

```rust
use log::{debug, info, warn, error};

// Enable detailed logging
env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("debug")).init();
```

#### 2. Trace Analysis

```rust
// Examine optimization traces for debugging
for iteration_data in &result.trace.iterations {
    if !iteration_data.function_value.is_finite() {
        warn!("Non-finite function value at iteration {}", iteration_data.iteration);
    }
}
```

#### 3. Parameter Validation

```rust
fn validate_parameters(params: &[f64]) -> Result<(), String> {
    for (i, ¶m) in params.iter().enumerate() {
        if !param.is_finite() {
            return Err(format!("Parameter {} is not finite: {}", i, param));
        }
    }
    Ok(())
}
```

### Performance Profiling

#### 1. Timing Analysis

```rust
use std::time::Instant;

let start = Instant::now();
// ... operation
let duration = start.elapsed();
println!("Operation took: {:?}", duration);
```

#### 2. Memory Profiling

```rust
// Use tools like valgrind or heaptrack for detailed memory analysis
// Or implement custom memory tracking
```

#### 3. Function Call Counting

The system automatically tracks function and gradient evaluations through the `ProblemWrapper`.

---

## Best Practices

### 1. Experimental Design

- **Multiple Runs**: Always use multiple independent runs (≥10)
- **Random Seeds**: Ensure proper randomization for reproducibility
- **Fair Comparison**: Use identical termination criteria for all optimizers
- **Problem Diversity**: Include problems from different families

### 2. Statistical Analysis

- **Appropriate Tests**: Use Welch's t-test for unequal variances
- **Effect Sizes**: Report Cohen's d alongside p-values
- **Multiple Comparisons**: Consider Bonferroni correction for multiple tests
- **Sample Size**: Ensure adequate power for statistical tests

### 3. Reporting

- **Transparency**: Report all metrics, including failures
- **Reproducibility**: Include all configuration parameters
- **Visualization**: Use appropriate scales (linear/log) for different metrics
- **Academic Standards**: Follow publication guidelines for statistical reporting

### 4. Code Quality

- **Error Handling**: Implement comprehensive error handling
- **Documentation**: Maintain clear documentation and comments
- **Testing**: Include unit tests for critical components
- **Version Control**: Track all changes and configurations
