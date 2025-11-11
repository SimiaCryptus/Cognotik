# LLMExperimentTask Technical Documentation

## Overview

The `LLMExperimentTask` is a sophisticated experimental framework for conducting controlled experiments on Large Language Model (LLM) behavior. It enables researchers and developers to systematically test LLM responses under varying conditions, collect metrics, and perform statistical analysis on the results.

## Architecture

### Class Hierarchy

```mermaid
classDiagram
    AbstractTask <|-- LLMExperimentTask
    TaskExecutionConfig <|-- LLMExperimentTaskExecutionConfigData
    ValidatedObject <|.. LLMExperimentTaskExecutionConfigData

    class AbstractTask {
        +OrchestrationConfig orchestrationConfig
        +TaskExecutionConfig executionConfig
        +run()
        +promptSegment()
    }

    class LLMExperimentTask {
        +LLMExperimentTaskExecutionConfigData executionConfig
        +run()
        +promptSegment()
        -generateExperimentalConditions()
        -analyzeResults()
        -generateStatisticalTables()
        -calculateMetrics()
    }

    class LLMExperimentTaskExecutionConfigData {
        +List~String~ prompt_templates
        +Map~String,List~String~~ prompt_variables
        +List~String~ metrics
        +List~Double~ temperature_values
        +Int repetitions
        +Boolean statistical_analysis
        +Double significance_level
        +validate()
    }

    class ExperimentalCondition {
        +Double temperature
        +Map~String,String~ variables
        +String prompt
    }

    class ExperimentalResult {
        +Int conditionIndex
        +Int repetition
        +Double temperature
        +Map~String,String~ variables
        +String prompt
        +String response
        +Long responseTime
        +Map~String,Double~ metrics
    }

    LLMExperimentTask --> ExperimentalCondition
    LLMExperimentTask --> ExperimentalResult
```

## Data Flow

### High-Level Experiment Flow

```mermaid
flowchart TD
    A[Start Experiment] --> B[Validate Configuration]
    B --> C{Valid?}
    C -->|No| D[Return Error]
    C -->|Yes| E[Create Transcript File]
    E --> F[Initialize Tabbed Display]
    F --> G[Generate Experimental Conditions]
    G --> H[Create Thread Pool]
    H --> I[Execute Trials Concurrently]
    I --> J[Collect Results]
    J --> K[Generate Statistical Analysis]
    K --> L[Generate Insights via LLM]
    L --> M[Create Final Report]
    M --> N[Complete Task]

    style A fill:#90EE90
    style N fill:#90EE90
    style D fill:#FFB6C1
```

### Detailed Execution Flow

```mermaid
sequenceDiagram
    participant User
    participant Task as LLMExperimentTask
    participant Config as ExecutionConfig
    participant CondGen as Condition Generator
    participant Pool as Thread Pool
    participant Agent as ChatAgent
    participant Stats as Statistical Analyzer
    participant Insights as Insights Generator

    User->>Task: run()
    Task->>Config: validate()
    Config-->>Task: validation result

    alt Invalid Config
        Task-->>User: Configuration Error
    else Valid Config
        Task->>Task: createTranscriptFile()
        Task->>Task: createTabbedDisplay()
        Task->>CondGen: generateExperimentalConditions()
        CondGen-->>Task: List<ExperimentalCondition>

        loop For each condition
            loop For each repetition
                Task->>Pool: submit trial
                Pool->>Agent: answer(prompt)
                Agent-->>Pool: response
                Pool->>Task: ExperimentalResult
            end
        end

        Task->>Stats: analyzeResults()
        Stats-->>Task: statistical analysis

        Task->>Stats: generateStatisticalTables()
        Stats-->>Task: detailed tables

        Task->>Insights: generate insights
        Insights-->>Task: LLM-generated insights

        Task->>Task: generateFinalReport()
        Task-->>User: Complete with report link
    end
```

## Core Components

### 1. Configuration Data Structure

```mermaid
graph LR
    A[LLMExperimentTaskExecutionConfigData] --> B[prompt_templates]
    A --> C[prompt_variables]
    A --> D[metrics]
    A --> E[temperature_values]
    A --> F[repetitions]
    A --> G[statistical_analysis]
    A --> H[significance_level]

    B --> B1[List of template strings]
    C --> C1[Map: variable name → values]
    D --> D1[List of metric names]
    E --> E1[List of temperature values]
    F --> F1[Integer: 1-100]
    G --> G1[Boolean flag]
    H --> H1[Double: 0.0-1.0]

    style A fill:#87CEEB
    style B fill:#FFE4B5
    style C fill:#FFE4B5
    style D fill:#FFE4B5
    style E fill:#FFE4B5
    style F fill:#FFE4B5
    style G fill:#FFE4B5
    style H fill:#FFE4B5
```

### 2. Experimental Condition Generation

```mermaid
flowchart TD
    A[Input: Templates, Variables, Temperatures] --> B[Generate Variable Combinations]
    B --> C{Variables Empty?}
    C -->|Yes| D[Single Empty Map]
    C -->|No| E[Cartesian Product of Variable Values]

    E --> F[For Each Temperature]
    D --> F

    F --> G[For Each Template]
    G --> H[For Each Variable Combination]
    H --> I[Substitute Variables in Template]
    I --> J{Duplicate Check}
    J -->|Unique| K[Add to Conditions List]
    J -->|Duplicate| L[Skip]
    K --> M[Next Combination]
    L --> M
    M --> N{More Combinations?}
    N -->|Yes| H
    N -->|No| O[Return Conditions List]

    style A fill:#90EE90
    style O fill:#90EE90
```

**Algorithm Details:**

The condition generation uses a recursive Cartesian product algorithm:

```kotlin
fun generateVariableCombinations(variables: Map<String, List<String>>): List<Map<String, String>> {
    if (variables.isEmpty()) return listOf(emptyMap())

    val keys = variables.keys.toList()
    val values = keys.map { variables[it]!! }

    fun combine(index: Int, current: Map<String, String>): List<Map<String, String>> {
        if (index == keys.size) return listOf(current)

        val results = mutableListOf<Map<String, String>>()
        values[index].forEach { value ->
            results.addAll(combine(index + 1, current + (keys[index] to value)))
        }
        return results
    }

    return combine(0, emptyMap())
}
```

### 3. Trial Execution Pipeline

```mermaid
flowchart TD
    A[Experimental Condition] --> B[Create ChatAgent with Temperature]
    B --> C[Submit to Thread Pool]
    C --> D[Execute Trial]
    D --> E[Measure Start Time]
    E --> F[Call agent.answer]
    F --> G[Receive Response]
    G --> H[Measure End Time]
    H --> I[Calculate Response Time]
    I --> J[Calculate Metrics via LLM]
    J --> K[Create ExperimentalResult]
    K --> L{Success?}
    L -->|Yes| M[Add to Results]
    L -->|No| N[Increment Failed Trials]
    M --> O[Update Progress]
    N --> O
    O --> P[Write to Transcript]

    style A fill:#87CEEB
    style K fill:#FFE4B5
    style M fill:#90EE90
    style N fill:#FFB6C1
```

### 4. Metrics Calculation Flow

```mermaid
sequenceDiagram
    participant Task
    participant ParsedAgent
    participant LLM
    participant Result

    Task->>ParsedAgent: calculateMetrics(metrics, response)
    ParsedAgent->>ParsedAgent: Create evaluation prompt
    ParsedAgent->>LLM: Evaluate response on metrics
    LLM-->>ParsedAgent: MetricRatings object
    ParsedAgent->>ParsedAgent: Extract ratings map
    ParsedAgent->>ParsedAgent: Match metric names (fuzzy)
    ParsedAgent-->>Task: Map<String, Double>
    Task->>Result: Store in ExperimentalResult
```

**Metric Rating Structure:**

```mermaid
classDiagram
    class MetricRating {
        +Double score
        +String reasoning
    }

    class MetricRatings {
        +Map~String,MetricRating~ ratings
    }

    class ExperimentalResult {
        +Map~String,Double~ metrics
    }

    MetricRatings --> MetricRating
    ExperimentalResult --> MetricRatings : extracts scores
```

## Statistical Analysis

### 5. Analysis Pipeline

```mermaid
flowchart TD
    A[All Experimental Results] --> B[Group by Temperature]
    A --> C[Group by Variables]

    B --> D[Calculate Descriptive Stats]
    D --> D1[Mean, SD, Min, Max, Median, CV]

    C --> E[Calculate Variable Effects]
    E --> E1[Mean, SD, 95% CI per value]

    B --> F{Multiple Temperatures?}
    F -->|Yes| G[Pairwise T-Tests]
    F -->|No| H[Skip Comparisons]

    G --> G1[Calculate t-statistic]
    G1 --> G2[Calculate p-value]
    G2 --> G3[Calculate Cohen's d]
    G3 --> G4[Determine Significance]

    C --> I{Multiple Variable Values?}
    I -->|Yes| J[Variable Pairwise Tests]
    I -->|No| K[Skip]

    A --> L[Calculate Correlation Matrix]
    L --> L1[Pearson Correlation for all metric pairs]

    D1 --> M[Generate Statistical Tables]
    E1 --> M
    G4 --> M
    J --> M
    L1 --> M

    M --> N[Format as Markdown Tables]
    N --> O[Return Analysis Report]

    style A fill:#87CEEB
    style O fill:#90EE90
```

### 6. Statistical Tables Generation

```mermaid
graph TD
    A[generateStatisticalTables] --> B[Table 1: Descriptive Statistics by Temperature]
    A --> C[Table 2: Pairwise Temperature Comparisons]
    A --> D[Table 3: Variable Effects Analysis]
    A --> E[Table 4: Correlation Matrix]
    A --> F[Table 5: Effect Sizes Summary]

    B --> B1[For each temperature]
    B1 --> B2[For each metric]
    B2 --> B3[Calculate: Mean, SD, Min, Max, Median, CV]

    C --> C1[For each temperature pair]
    C1 --> C2[For each metric]
    C2 --> C3[Calculate: t-stat, p-value, Cohen's d]

    D --> D1[For each variable]
    D1 --> D2[For each value]
    D2 --> D3[Calculate: Mean, SD, 95% CI]
    D1 --> D4[Pairwise comparisons]

    E --> E1[For each metric pair]
    E1 --> E2[Calculate Pearson correlation]

    F --> F1[For each comparison]
    F1 --> F2[Calculate Cohen's d]
    F2 --> F3[Interpret effect size]

    style A fill:#87CEEB
```

### 7. T-Test Calculation

```mermaid
flowchart TD
    A[Two Samples] --> B[Calculate Means]
    B --> C[Calculate Variances]
    C --> D[Calculate Pooled Standard Error]
    D --> E[Calculate t-statistic]
    E --> F[t = mean_diff / pooled_SE]
    F --> G[Calculate Degrees of Freedom]
    G --> H[df = n1 + n2 - 2]
    H --> I[Approximate p-value]
    I --> J{df > 30?}
    J -->|Yes| K[Use Normal Approximation]
    J -->|No| L[Use Conservative Adjustment]
    K --> M[Calculate using erf function]
    L --> M
    M --> N[Return p-value]

    style A fill:#87CEEB
    style N fill:#90EE90
```

**Mathematical Formulas:**

1. **T-Statistic:**
   ```
   t = (μ₁ - μ₂) / √(σ₁²/n₁ + σ₂²/n₂)
   ```

2. **Cohen's d:**
   ```
   d = (μ₁ - μ₂) / σ_pooled
   where σ_pooled = √((σ₁² + σ₂²) / 2)
   ```

3. **Coefficient of Variation:**
   ```
   CV = σ / μ
   ```

4. **Pearson Correlation:**
   ```
   r = Σ((x - μₓ)(y - μᵧ)) / √(Σ(x - μₓ)² × Σ(y - μᵧ)²)
   ```

### 8. Response Diversity Calculation

```mermaid
flowchart TD
    A[List of Results] --> B[For each result A]
    B --> C[For each other result B]
    C --> D[Compress A alone]
    C --> E[Compress B alone]
    C --> F[Compress A+B concatenated]
    D --> G[Calculate: size_A + size_B / size_AB]
    E --> G
    F --> G
    G --> H[Average across all pairs]
    H --> I{Compressibility Score}
    I -->|< 1.1| J[High Diversity]
    I -->|1.1-1.5| K[Moderate Diversity]
    I -->|> 1.5| L[Low Diversity]

    style A fill:#87CEEB
    style J fill:#90EE90
    style K fill:#FFE4B5
    style L fill:#FFB6C1
```

**Diversity Interpretation:**
- **Compressibility ≈ 1.0**: Responses are highly unique (incompressible together)
- **Compressibility ≈ 2.0**: Responses are nearly identical (highly compressible)

## Output Generation

### 9. Tabbed Display Structure

```mermaid
graph TD
    A[TabbedDisplay] --> B[Overview Tab]
    A --> C[Progress Tab]
    A --> D[Statistical Tables Tab]
    A --> E[Analysis Tab]
    A --> F[Insights Tab]

    B --> B1[Experiment Design Summary]
    B --> B2[Progress Updates]
    B --> B3[Completion Status]

    C --> C1[Condition-by-Condition Progress]
    C --> C2[Trial Completion Stats]
    C --> C3[Condition Summaries]

    D --> D1[Table 1: Descriptive Stats]
    D --> D2[Table 2: Pairwise Comparisons]
    D --> D3[Table 3: Variable Effects]
    D --> D4[Table 4: Correlation Matrix]
    D --> D5[Table 5: Effect Sizes]

    E --> E1[Summary Statistics]
    E --> E2[Temperature Effects]
    E --> E3[Variable Effects]
    E --> E4[Response Diversity]

    F --> F1[LLM-Generated Insights]
    F --> F2[Key Patterns]
    F --> F3[Recommendations]

    style A fill:#87CEEB
```

### 10. Transcript File Generation

```mermaid
sequenceDiagram
    participant Task
    participant File as Transcript File
    participant Writer as BufferedWriter

    Task->>File: Create transcript file
    Task->>Writer: Open buffered writer

    Writer->>File: Write experiment design
    Writer->>File: Write start timestamp

    loop For each condition
        Writer->>File: Write condition header
        Writer->>File: Write variables and prompt

        loop For each repetition
            Writer->>File: Write repetition number
            Writer->>File: Write response time
            Writer->>File: Write response preview
        end

        Writer->>File: Write condition summary
    end

    Writer->>File: Write statistical tables
    Writer->>File: Write analysis
    Writer->>File: Write insights
    Writer->>File: Write completion timestamp
    Writer->>File: Write summary statistics

    Writer->>File: Close file
    Task->>Task: Generate HTML/PDF versions
```

## Concurrency Model

### 11. Thread Pool Execution

```mermaid
flowchart TD
    A[Main Thread] --> B[Create Thread Pool]
    B --> C[For Each Condition]
    C --> D[Submit N Repetitions to Pool]
    D --> E[Thread Pool]

    E --> F1[Worker Thread 1]
    E --> F2[Worker Thread 2]
    E --> F3[Worker Thread 3]
    E --> F4[Worker Thread N]

    F1 --> G1[Execute Trial]
    F2 --> G2[Execute Trial]
    F3 --> G3[Execute Trial]
    F4 --> G4[Execute Trial]

    G1 --> H[Collect Results Thread-Safe]
    G2 --> H
    G3 --> H
    G4 --> H

    H --> I[ConcurrentHashMap]
    I --> J[Wait for All Futures]
    J --> K{All Complete?}
    K -->|Yes| L[Process Next Condition]
    K -->|No| J
    L --> M{More Conditions?}
    M -->|Yes| C
    M -->|No| N[Analyze All Results]

    style A fill:#87CEEB
    style E fill:#FFE4B5
    style I fill:#90EE90
```

**Thread Safety Mechanisms:**

1. **ConcurrentHashMap**: Thread-safe storage for results
2. **AtomicInteger**: Thread-safe counters for completed/failed trials
3. **Synchronized blocks**: For transcript writing
4. **Future.get()**: Ensures all trials complete before proceeding

## Error Handling

### 12. Error Flow

```mermaid
flowchart TD
    A[Operation] --> B{Error Occurs?}
    B -->|No| C[Continue]
    B -->|Yes| D{Error Type}

    D -->|Configuration Error| E[Validate Config]
    E --> F[Return Error Message]
    F --> G[Complete Task with Error]

    D -->|Trial Execution Error| H[Catch Exception]
    H --> I[Increment Failed Trials]
    I --> J[Log Error]
    J --> K[Write to Transcript]
    K --> L[Continue with Other Trials]

    D -->|Fatal Error| M[Catch in Main Try-Catch]
    M --> N[Write Error to Transcript]
    N --> O[Update Overview Tab]
    O --> P[Generate Partial Results]
    P --> Q[Complete Task with Error]

    style B fill:#FFE4B5
    style F fill:#FFB6C1
    style G fill:#FFB6C1
    style Q fill:#FFB6C1
```

## Data Structures

### 13. Key Data Structures

```mermaid
erDiagram
    LLMExperimentTaskExecutionConfigData ||--o{ ExperimentalCondition : generates
    ExperimentalCondition ||--o{ ExperimentalResult : produces
    ExperimentalResult ||--|| MetricRatings : contains

    LLMExperimentTaskExecutionConfigData {
        List_String prompt_templates
        Map_String_List_String prompt_variables
        List_String metrics
        List_Double temperature_values
        Int repetitions
        Boolean statistical_analysis
        Double significance_level
    }

    ExperimentalCondition {
        Double temperature
        Map_String_String variables
        String prompt
    }

    ExperimentalResult {
        Int conditionIndex
        Int repetition
        Double temperature
        Map_String_String variables
        String prompt
        String response
        Long responseTime
        Map_String_Double metrics
    }

    MetricRatings {
        Map_String_MetricRating ratings
    }

    MetricRating {
        Double score
        String reasoning
    }
```

## Performance Characteristics

### 14. Performance Metrics

```mermaid
graph LR
    A[Performance Factors] --> B[Number of Conditions]
    A --> C[Repetitions per Condition]
    A --> D[Thread Pool Size]
    A --> E[LLM Response Time]
    A --> F[Metric Calculation Time]

    B --> G[Total Trials = Conditions × Repetitions]
    C --> G

    D --> H[Parallelization Factor]
    E --> H

    G --> I[Total Execution Time]
    H --> I
    F --> I

    I --> J[Throughput = Trials / Time]

    style A fill:#87CEEB
    style I fill:#FFE4B5
    style J fill:#90EE90
```

**Time Complexity:**
- Condition Generation: O(T × P × V) where T=templates, P=prompt variables (Cartesian product), V=variable values
- Trial Execution: O(C × R × M) where C=conditions, R=repetitions, M=LLM response time
- Statistical Analysis: O(C² × M) for pairwise comparisons
- Overall: O(C × R × M) dominated by trial execution

**Space Complexity:**
- Results Storage: O(C × R × (P + M)) where P=prompt size, M=metrics count
- Transcript File: O(C × R × R_size) where R_size=average response size

## Usage Examples

### 15. Example Configuration Flow

```mermaid
flowchart TD
    A[User Defines Experiment] --> B[Set Prompt Templates]
    B --> C[Define Variables]
    C --> D[Select Temperature Values]
    D --> E[Choose Metrics]
    E --> F[Set Repetitions]
    F --> G[Configure Statistical Analysis]

    G --> H[Create Config Object]
    H --> I[Validate Configuration]
    I --> J{Valid?}
    J -->|No| K[Show Validation Errors]
    J -->|Yes| L[Execute Experiment]

    L --> M[Generate Conditions]
    M --> N[Run Trials]
    N --> O[Analyze Results]
    O --> P[Generate Report]

    style A fill:#87CEEB
    style P fill:#90EE90
    style K fill:#FFB6C1
```

**Example Configuration:**

```kotlin
val config = LLMExperimentTaskExecutionConfigData(
    prompt_templates = listOf(
        "Explain {concept} in simple terms",
        "What is {concept}? Provide a detailed explanation"
    ),
    prompt_variables = mapOf(
        "concept" to listOf("quantum computing", "machine learning", "blockchain")
    ),
    temperature_values = listOf(0.1, 0.5, 0.9),
    repetitions = 5,
    metrics = listOf("clarity", "technical_accuracy", "completeness"),
    statistical_analysis = true,
    significance_level = 0.05
)
```

This generates:
- 2 templates × 3 concepts × 3 temperatures = 18 conditions
- 18 conditions × 5 repetitions = 90 total trials

## Integration Points

### 16. System Integration

```mermaid
graph TD
    A[LLMExperimentTask] --> B[TaskOrchestrator]
    A --> C[ChatInterface API]
    A --> D[SessionTask UI]
    A --> E[File System]

    B --> B1[Task Scheduling]
    B --> B2[Dependency Management]

    C --> C1[ChatAgent]
    C --> C2[ParsedAgent]
    C --> C3[Model API Calls]

    D --> D1[TabbedDisplay]
    D --> D2[Progress Updates]
    D --> D3[Task Completion]

    E --> E1[Transcript Files]
    E --> E2[HTML/PDF Generation]

    style A fill:#87CEEB
```

## Best Practices

### 17. Recommended Workflow

```mermaid
flowchart TD
    A[Start] --> B[Define Research Question]
    B --> C[Design Prompt Templates]
    C --> D[Identify Variables]
    D --> E[Select Temperature Range]
    E --> F[Choose Appropriate Metrics]
    F --> G[Determine Sample Size]
    G --> H{Pilot Test}
    H -->|Issues Found| I[Refine Design]
    I --> C
    H -->|Looks Good| J[Run Full Experiment]
    J --> K[Review Statistical Tables]
    K --> L[Analyze Insights]
    L --> M[Draw Conclusions]
    M --> N{Need Follow-up?}
    N -->|Yes| O[Design Follow-up Experiment]
    O --> C
    N -->|No| P[Document Findings]

    style A fill:#90EE90
    style P fill:#90EE90
```

## Limitations and Considerations

### 18. System Constraints

```mermaid
mindmap
    root((Limitations))
        Configuration
            Max 100 repetitions
            Temperature 0.0-2.0
            Prompt template required
        Performance
            LLM API rate limits
            Memory for large experiments
            Disk space for transcripts
        Statistical
            Assumes normal distribution
            Simplified p-value calculation
            Limited to t-tests
        Metrics
            LLM-based evaluation bias
            Subjective metric interpretation
            Consistency across trials
```
