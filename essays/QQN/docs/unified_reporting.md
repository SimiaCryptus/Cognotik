# Unified Report System

This document describes the unified reporting system for the QQN Optimizer benchmark suite. The system provides a consistent interface for generating various types of performance reports across multiple output formats.

## Overview

The unified reporting system is built around the `Report` trait, which provides a standardized interface for all report types. This allows for:

- **Consistent API**: All reports implement the same interface
- **Multiple formats**: HTML, LaTeX, Markdown, and CSV output
- **Unified testing**: Common test patterns for all report implementations
- **Easy extension**: Simple to add new report types
- **Batch processing**: Generate multiple reports at once

## Core Components

### Report Trait

The `Report` trait defines the interface that all reports must implement:

```rust
pub trait Report {
    fn name(&self) -> &'static str;
    fn description(&self) -> &'static str;
    fn generate_content(&self, data: &[(&ProblemSpec, BenchmarkResults)], _config: &ReportConfig) -> Result<String>;
    fn export_to_file(&self, data: &[(&ProblemSpec, BenchmarkResults)], _config: &ReportConfig, output_path: &Path) -> Result<()>;
    fn validate_data(&self, data: &[(&ProblemSpec, BenchmarkResults)]) -> Result<()>;
    fn get_metadata(&self, data: &[(&ProblemSpec, BenchmarkResults)]) -> ReportMetadata;
    fn supported_formats(&self) -> Vec<ReportFormat>;
}
```

### Report Configuration

Reports are configured using the `ReportConfig` struct:

```rust
pub struct ReportConfig {
    pub format: ReportFormat,              // Output format (HTML, LaTeX, etc.)
    pub include_detailed_stats: bool,      // Include detailed statistics
    pub include_plots: bool,               // Include visualizations
    pub style_options: HashMap<String, String>, // Custom styling
}
```

### Report Collection

The `ReportCollection` struct allows batch processing of multiple reports:

```rust
let reports = ReportCollection::new()
    .add_report(SummaryStatisticsReport::new())
    .add_report(PerformanceTableReport::new());

let metadata = reports.generate_all(&data, &_config, &output_dir)?;
```

## Available Report Types

### Summary Statistics Report

Provides aggregate performance metrics grouped by problem family and optimizer.

### Family vs Family Report

Shows a comparison matrix of how different optimizer families perform across different problem families.

- **Name**: `family_vs_family`
- **Formats**: HTML, LaTeX, Markdown, CSV
- **Use case**: Cross-family performance comparison

### Performance Table Report

Shows detailed performance metrics for each optimizer-problem combination.

## Usage Examples

### Basic Report Generation

```rust
use qqn_optimizer::experiment_runner::{Report, ReportConfig, ReportFormat};
use qqn_optimizer::experiment_runner::reports::unified_summary_statistics::SummaryStatisticsReport;

let report = SummaryStatisticsReport::new();
let _config = ReportConfig {
    format: ReportFormat::Html,
    ..Default::default()
};

let content = report.generate_content(&benchmark_data, &_config)?;
```

### Batch Report Generation

```rust
use qqn_optimizer::experiment_runner::{ReportCollection, ReportConfig, ReportFormat};

let reports = ReportCollection::new()
    .add_report(SummaryStatisticsReport::new())
    .add_report(PerformanceTableReport::new())
    .add_report(FamilyVsFamilyReport::new());

let _config = ReportConfig {
    format: ReportFormat::Html,
    include_detailed_stats: true,
    include_plots: false,
    ..Default::default()
};

let metadata_list = reports.generate_all(&data, &_config, &output_dir)?;
```

### Multiple Format Generation

```rust
let report = SummaryStatisticsReport::new();
let formats = [ReportFormat::Html, ReportFormat::Markdown, ReportFormat::Csv];

for format in formats {
    let _config = ReportConfig { format, ..Default::default() };
    let content = report.generate_content(&data, &_config)?;
    // Save content to appropriate file...
}
```

## Testing Infrastructure

The unified reporting system includes comprehensive testing infrastructure through the `UnifiedReportTestSuite`:

### Test Categories

1. **Basic Functionality**: Tests report name, description, supported formats
2. **Content Generation**: Validates content generation for all formats
3. **Data Validation**: Tests input data validation and error handling
4. **Metadata Generation**: Validates report metadata
5. **File Export**: Tests file export functionality
6. **Format Consistency**: Ensures different formats produce different but valid content

### Running Tests

```rust
use qqn_optimizer::experiment_runner::UnifiedReportTestSuite;

// Test a specific report
let report = SummaryStatisticsReport::new();
UnifiedReportTestSuite::test_report(&report)?;

// Test individual aspects
UnifiedReportTestSuite::test_basic_functionality(&report)?;
UnifiedReportTestSuite::test_content_generation(&report)?;
// ... etc
```

### Creating Test Data

The test suite provides utilities for creating test data:

```rust
let test_data = UnifiedReportTestSuite::create_test_data();
let data_refs: Vec<_> = test_data.iter().map(|(p, r)| (p, r.clone())).collect();
```

## Adding New Report Types

To add a new report type:

1. **Create the Report Struct**:

```rust
pub struct MyCustomReport;

impl MyCustomReport {
    pub fn new() -> Self {
        Self
    }
}
```

2. **Implement the Report Trait**:

```rust
impl Report for MyCustomReport {
    fn name(&self) -> &'static str {
        "my_custom_report"
    }

    fn description(&self) -> &'static str {
        "Description of what this report provides"
    }

    fn generate_content(&self, data: &[(&ProblemSpec, BenchmarkResults)], _config: &ReportConfig) -> Result<String> {
        // Implementation for different formats
        match _config.format {
            ReportFormat::Html => self.generate_html(data, _config),
            ReportFormat::Latex => self.generate_latex(data, _config),
            // ... etc
        }
    }
}
```

3. **Add Tests**:

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use crate::experiment_runner::UnifiedReportTestSuite;

    #[test]
    fn test_my_custom_report() {
        let report = MyCustomReport::new();
        UnifiedReportTestSuite::test_report(&report).unwrap();
    }
}
```

4. **Add to Collection** (optional):

```rust
let reports = ReportCollection::new()
    .add_report(MyCustomReport::new())
    .add_report(SummaryStatisticsReport::new());
```

## Output Formats

### HTML

- Complete standalone HTML documents
- Embedded CSS for styling
- Tables and basic formatting
- Suitable for web display

### LaTeX

- Complete LaTeX documents with packages
- Professional table formatting
- Scientific notation support
- Ready for academic publication

### Markdown

- GitHub-flavored markdown
- Table support
- Suitable for documentation
- Easy to convert to other formats

### CSV

- Comma-separated values
- Easy data import/export
- Suitable for further analysis
- Compatible with spreadsheet software

## Performance Considerations

- Reports validate input data before processing
- Large datasets are processed efficiently
- File export uses streaming where appropriate
- Memory usage is optimized for batch processing

## Future Extensions

The unified reporting system is designed to be extensible:

- **New Formats**: Easy to add JSON, XML, or other formats
- **Enhanced Styling**: More sophisticated styling options
- **Interactive Reports**: Support for interactive HTML reports
- **Template System**: Customizable report templates
- **Caching**: Results caching for large datasets
