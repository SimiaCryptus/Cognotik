# ReportGenerator Documentation

## Overview

The `ReportGenerator` is a comprehensive reporting system for optimization benchmark results. It generates multiple output formats including HTML/Markdown reports, CSV data exports, LaTeX tables, and detailed analysis documents. The system is designed to provide thorough analysis and visualization of optimization algorithm performance across different problem families.

## Table of Contents

1. [Core Structure](#core-structure)
2. [Key Features](#key-features)
3. [Data Structures](#data-structures)
4. [Public API](#public-api)
5. [Report Generation Methods](#report-generation-methods)
6. [Export Functionality](#export-functionality)
7. [LaTeX Generation](#latex-generation)
8. [Detailed Analysis](#detailed-analysis)
9. [Usage Examples](#usage-examples)
10. [Configuration](#configuration)

## Core Structure

### Main Components

```rust
pub struct ReportGenerator {
    output_dir: String,                    // Directory for output files
    _config: BenchmarkConfig,               // Benchmark configuration
    statistical_analysis: StatisticalAnalysis, // Statistical analysis engine
}
```

### Supporting Data Structures

```rust
#[derive(Debug, Clone)]
struct FamilyPerformanceData {
    average_ranking: f64,      // Average rank across problems
    best_rank_average: f64,    // Average of best ranks achieved
    best_variant: String,      // Name of best performing variant
    worst_variant: String,     // Name of worst performing variant
}
```

## Key Features

### 1. Multi-Format Output

- **HTML/Markdown Reports**: Interactive reports with tables and plots
- **CSV Exports**: Raw data and summary statistics
- **LaTeX Tables**: Publication-ready academic tables
- **Detailed Analysis**: Individual optimizer-problem reports

### 2. Comprehensive Analysis

- Performance ranking and comparison
- Statistical significance testing
- Family-based analysis (optimizer families vs problem families)
- Convergence behavior analysis
- Resource utilization metrics

### 3. Visualization Support

- Convergence plots (linear and logarithmic scales)
- Performance comparison matrices
- Family vs family comparison tables

## Data Structures

### Problem Family Classification

The system automatically classifies optimization problems into families:

```rust
pub fn get_family(problem_name: &str) -> String {
    match problem_name.split([' ', '_']).next().unwrap_or(problem_name) {
        // Convex/Unimodal functions
        "Sphere" | "Matyas" => "Convex Unimodal".to_string(),

        // Non-convex but unimodal
        "Rosenbrock" | "Beale" | "GoldsteinPrice" | "Levi" => "Non-Convex Unimodal".to_string(),

        // Highly multimodal
        "Rastrigin" | "Ackley" | "Michalewicz" | "StyblinskiTang" => "Highly Multimodal".to_string(),

        // Machine Learning problems
        name if name.contains("Regression") => "ML Regression".to_string(),
        name if name.contains("Neural") => "ML Neural Networks".to_string(),
        name if name.contains("SVM") | name.contains("Logistic") => "ML Classification".to_string(),

        // Default fallback
        x => x.to_string(),
    }
}
```

## Public API

### Constructor

```rust
impl ReportGenerator {
    pub fn new(output_dir: String, _config: BenchmarkConfig) -> Self
```

Creates a new `ReportGenerator` instance.

**Parameters:**

- `output_dir`: Directory path where all output files will be generated
- `_config`: Benchmark configuration containing run parameters

**Returns:** New `ReportGenerator` instance

### Main Report Generation

```rust
pub async fn generate_main_report(
    &self,
    all_results: &[(&ProblemSpec, BenchmarkResults)],
    use_optimizer_families: bool,
) -> anyhow::Result<()>
```

Generates the complete benchmark report with all components.

**Parameters:**

- `all_results`: Array of tuples containing problem specifications and their results
- `use_optimizer_families`: Whether to group optimizers by family for analysis

**Generated Files:**

- `benchmark_report.md`: Main HTML/Markdown report
- `detailed_results.csv`: Raw experimental data
- `summary_statistics.csv`: Aggregated statistics
- `problems/`: Directory with problem-specific CSV files
- `latex/`: Directory with LaTeX tables and comprehensive document
- `detailed_*.md`: Individual optimizer-problem analysis reports

## Report Generation Methods

### 1. Winner Summary Table

```rust
fn generate_winner_summary_table(
    &self,
    all_results: &[(&ProblemSpec, BenchmarkResults)],
) -> String
```

Creates a quick summary table showing the best performing optimizer for each problem.

**Features:**

- Problem family classification
- Success rate calculation
- Mean final value comparison
- Median best value analysis
- Runner-up identification
- QQN optimizer highlighting

### 2. Family vs Family Comparison

```rust
fn generate_family_vs_family_comparison_table(
    &self,
    all_results: &[(&ProblemSpec, BenchmarkResults)],
) -> anyhow::Result<String>
```

Generates a comprehensive matrix comparing optimizer families against problem families.

**Metrics Included:**

- Average ranking across problems
- Best rank average (average of best ranks achieved)
- Best performing variant identification
- Worst performing variant identification

### 3. Problem-Specific Analysis

```rust
fn generate_problem_section(
    &self,
    problem: &ProblemSpec,
    results: &BenchmarkResults,
) -> anyhow::Result<String>
```

Creates detailed analysis for individual problems.

**Components:**

- Performance ranking table
- Success/failure rate analysis
- Statistical measures (mean, std dev, best, worst)
- Resource utilization metrics
- Convergence plot references
- Suspicious result detection

## Export Functionality

### CSV Generation

The system generates multiple CSV files for different analysis needs:

#### 1. Detailed Results CSV

```csv
Problem,ProblemFamily,Dimension,Optimizer,Run,FinalValue,FinalGradientNorm,Iterations,FunctionEvals,GradientEvals,Time,Converged,ConvergenceReason
```

#### 2. Summary Statistics CSV

```csv
Problem,ProblemFamily,Dimension,Optimizer,MeanFinalValue,MeanFinalValueSuccess,MeanFinalValueFail,StdFinalValue,BestValue,WorstValue,MeanIterations,MeanFunctionEvals,MeanFunctionEvalsSuccess,MeanFunctionEvalsFail,MeanGradientEvals,MeanGradientEvalsSuccess,MeanGradientEvalsFail,MeanTime,SuccessRate,NumRuns
```

#### 3. Problem-Specific CSVs

Individual CSV files for each problem stored in `problems/` directory.

### CSV Export Implementation

```rust
fn generate_csv_exports(
    &self,
    all_results: &[(&ProblemSpec, BenchmarkResults)],
) -> anyhow::Result<()>
```

**Features:**

- Separate statistics for successful and failed runs
- Comprehensive error handling
- Automatic directory creation
- Data validation and filtering

## LaTeX Generation

The system generates publication-ready LaTeX tables and documents:

### 1. Main Performance Table

```rust
fn generate_main_performance_latex_table(
    &self,
    all_results: &[(&ProblemSpec, BenchmarkResults)],
    latex_dir: &Path,
) -> anyhow::Result<()>
```

Creates a comprehensive performance comparison table using `longtable` for multi-page support.

**Features:**

- Scientific notation formatting with `siunitx`
- Multi-row problem grouping
- Bold highlighting for best performers
- Automatic page breaks

### 2. Comparison Matrices

```rust
fn generate_comparison_matrix_latex_table(
    &self,
    all_results: &[(&ProblemSpec, BenchmarkResults)],
    latex_dir: &Path,
) -> anyhow::Result<()>
```

Generates win-loss-tie matrices between optimizer families.

**Statistical Analysis:**

- Welch's t-test for significance testing
- Color coding for dominance patterns
- Comprehensive legend and explanations

### 3. Comprehensive Document

```rust
fn generate_comprehensive_latex_document(
    &self,
    all_results: &[(&ProblemSpec, BenchmarkResults)],
    latex_dir: &Path,
) -> anyhow::Result<()>
```

Creates a complete LaTeX document with:

- Title page and abstract
- Methodology section
- All performance tables
- Individual problem results
- Conclusions and data availability

## Detailed Analysis

### Individual Optimizer-Problem Reports

```rust
async fn generate_optimizer_problem_report(
    &self,
    problem: &dyn OptimizationProblem,
    optimizer_name: &str,
    runs: &[&SingleResult],
) -> anyhow::Result<()>
```

Generates detailed analysis for each optimizer-problem combination.

#### Report Components:

1. **Executive Summary**
   - Problem and optimizer identification
   - Success rate and basic statistics
   - Quick performance metrics

2. **Run-by-Run Analysis**
   - Tabular view of all individual runs
   - Color coding for success/failure
   - Detailed convergence information

3. **Convergence Analysis**
   - Success/failure pattern analysis
   - Timing and iteration statistics
   - Failure reason categorization

4. **Parameter Evolution Analysis**
   - Best run detailed examination
   - Parameter trajectory visualization
   - Convergence behavior insights

5. **Performance Analysis**
   - Computational efficiency metrics
   - Resource utilization statistics
   - Comparative performance indicators

6. **Failure Analysis**
   - Failure pattern identification
   - Early failure detection
   - Numerical stability assessment

## Usage Examples

### Basic Usage

```rust
use crate::experiment_runner::report_generator::ReportGenerator;
use crate::benchmarks::evaluation::BenchmarkConfig;

// Create configuration
let _config = BenchmarkConfig {
    num_runs: 30,
    max_iterations: 1000,
    time_limit: Some(Duration::from_secs(300)),
    // ... other _config parameters
};

// Create report generator
let report_generator = ReportGenerator::new(
    "output/benchmark_results".to_string(),
    _config
);

// Generate comprehensive report
report_generator.generate_main_report(&all_results, false).await?;
```

### Family-Based Analysis

```rust
// Generate report with optimizer family grouping
report_generator.generate_main_report(&all_results, true).await?;
```

### Custom Output Directory

```rust
let custom_generator = ReportGenerator::new(
    "/path/to/custom/output".to_string(),
    _config
);
```

## Configuration

### Benchmark Configuration Impact

The `BenchmarkConfig` affects report generation in several ways:

```rust
pub struct BenchmarkConfig {
    pub num_runs: usize,                    // Affects statistical reliability
    pub max_iterations: usize,              // Convergence criteria
    pub maximum_function_calls: usize,      // Resource limits
    pub time_limit: Option<Duration>,       // Time constraints
    pub min_improvement_percent: f64,       // Success criteria
    // ... other parameters
}
```

### Output Directory Structure

```
output_dir/
├── benchmark_report.md              # Main report
├── detailed_results.csv             # Raw data
├── summary_statistics.csv           # Aggregated stats
├── detailed_[problem]_[optimizer].md # Individual analyses
├── convergence_*.png                # Convergence plots
├── problems/                        # Problem-specific data
│   ├── Sphere_results.csv
│   ├── Rosenbrock_results.csv
│   └── ...
└── latex/                          # LaTeX outputs
    ├── main_performance_table.tex
    ├── comparison_matrix.tex
    ├── family_comparison_matrix.tex
    ├── comprehensive_benchmark_report.tex
    └── ...
```

## Error Handling

The system includes comprehensive error handling:

```rust
// File I/O errors
fs::write(&path, content).with_context(|| {
    format!("Failed to write report to: {}", path.display())
})?;

// Data validation
if final_values.is_empty() {
    continue; // Skip optimizers with no valid results
}

// Statistical computation errors
if let Ok((_, p_value)) = self.statistical_analysis.welch_t_test_public(&data1, &data2) {
    // Process statistical results
} else {
    // Handle statistical computation failure
}
```

## Performance Considerations

### Memory Management

- Streaming CSV generation for large datasets
- Efficient data structure usage
- Minimal memory footprint for report generation

### Computational Efficiency

- Lazy evaluation where possible
- Efficient sorting and ranking algorithms
- Optimized statistical computations

### File I/O Optimization

- Batch file operations
- Efficient string building
- Minimal disk access patterns

## Extensibility

### Adding New Report Types

```rust
impl ReportGenerator {
    // Add custom report generation method
    pub fn generate_custom_analysis(&self, data: &CustomData) -> anyhow::Result<String> {
        // Implementation
    }
}
```

### Custom Problem Families

Extend the `get_family` function to support new problem classifications:

```rust
pub fn get_family(problem_name: &str) -> String {
    match problem_name.split([' ', '_']).next().unwrap_or(problem_name) {
        // Existing classifications...

        // New custom family
        "CustomProblem" => "Custom Family".to_string(),

        // Default fallback
        x => x.to_string(),
    }
}
```

### Statistical Analysis Extensions

The system integrates with `StatisticalAnalysis` for extensible statistical computations:

```rust
// Access statistical analysis engine
let stats = &self.statistical_analysis;
let (t_stat, p_value) = stats.welch_t_test_public(&sample1, &sample2)?;
```

## Best Practices

### 1. Data Validation

Always validate input data before processing:

```rust
let final_values: Vec<f64> = runs
    .iter()
    .map(|r| r.final_value)
    .filter(|&v| v.is_finite())  // Filter out NaN and infinite values
    .collect();

if final_values.is_empty() {
    continue; // Skip invalid data
}
```

### 2. Error Context

Provide meaningful error context:

```rust
fs::write(&csv_path, csv_content).with_context(|| {
    format!("Failed to write CSV to: {}", csv_path.display())
})?;
```

### 3. Resource Management

Create output directories as needed:

```rust
fs::create_dir_all(&self.output_dir)
    .with_context(|| format!("Failed to create output directory: {}", self.output_dir))?;
```

### 4. Consistent Formatting

Use consistent number formatting throughout reports:

```rust
format!("{:.2e}", value)  // Scientific notation
format!("{:.1}%", rate)   // Percentage
format!("{:.3}", time)    // Time in seconds
```
