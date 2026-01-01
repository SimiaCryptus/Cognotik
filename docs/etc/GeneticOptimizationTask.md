# GeneticOptimizationTask Technical Documentation

## Overview

The `GeneticOptimizationTask` implements a genetic algorithm for iterative text optimization. It evolves text through multiple generations using mutation, crossover, and selection strategies to maximize fitness scores based on configurable evaluation criteria.

## Architecture

### Class Hierarchy

```mermaid
classDiagram
    AbstractTask <|-- GeneticOptimizationTask
    TaskExecutionConfig <|-- GeneticOptimizationTaskExecutionConfigData
    ValidatedObject <|-- GeneticOptimizationTaskExecutionConfigData
    ValidatedObject <|-- EvaluationScore

    class AbstractTask {
        +OrchestrationConfig orchestrationConfig
        +run()
        +promptSegment()
    }

    class GeneticOptimizationTask {
        +GeneticOptimizationTaskExecutionConfigData executionConfig
        +run()
        +promptSegment()
        -generateMutation()
        -applyCrossover()
        -evaluateVariant()
    }

    class GeneticOptimizationTaskExecutionConfigData {
        +String initial_text
        +String optimization_goal
        +Map evaluation_weights
        +List constraints
        +int num_generations
        +int population_size
        +int selection_size
        +List mutation_strategies
        +boolean enable_crossover
        +validate()
    }

    class TextVariant {
        +String text
        +String mutation_description
        +String strategy
    }

    class EvaluationScore {
        +double overall_score
        +Map criteria_scores
        +List strengths
        +List weaknesses
        +String justification
        +validate()
    }

    class EvaluatedVariant {
        +String text
        +EvaluationScore score
        +int generation
        +Integer parentIndex
        +String strategy
    }
```

## Data Flow

### High-Level Process Flow

```mermaid
flowchart TD
    A[Start Task] --> B[Validate Configuration]
    B --> C{Valid?}
    C -->|No| D[Return Error]
    C -->|Yes| E[Initialize UI Tabs]
    E --> F[Gather Prior Context]
    F --> G[Create Initial Population]
    G --> H[Evaluate Initial Text]
    H --> I[Evolution Loop]

    I --> J{More Generations?}
    J -->|Yes| K[Select Survivors]
    K --> L[Generate Mutations]
    L --> M{Crossover Enabled?}
    M -->|Yes| N[Apply Crossover]
    M -->|No| O[Evaluate Population]
    N --> O
    O --> P[Update Best Variant]
    P --> Q[Display Generation Results]
    Q --> I

    J -->|No| R[Create Evolution Analysis]
    R --> S[Generate Final Report]
    S --> T[Complete Task]
    T --> U[End]

    D --> U
```

### Detailed Evolution Loop

```mermaid
flowchart TD
    A[Start Generation N] --> B[Sort Previous Population by Score]
    B --> C[Select Top K Survivors]

    C --> D[Calculate Mutations Needed]
    D --> E[For Each Survivor]

    E --> F[Calculate Mutations Per Survivor]
    F --> G[For Each Mutation Slot]

    G --> H[Select Random Strategy]
    H --> I[Call generateMutation]

    I --> J[AI Agent: Generate Variant]
    J --> K[Parse TextVariant Response]
    K --> L[Create EvaluatedVariant]
    L --> M{More Mutations?}

    M -->|Yes| G
    M -->|No| N{More Survivors?}

    N -->|Yes| E
    N -->|No| O{Crossover Enabled?}

    O -->|Yes| P[Select Top 2 Survivors]
    P --> Q[Call applyCrossover]
    Q --> R[AI Agent: Combine Traits]
    R --> S[Create Crossover Variant]
    S --> T[Combine Survivors + New Variants]

    O -->|No| T

    T --> U[For Each Variant]
    U --> V{Already Evaluated?}
    V -->|No| W[Call evaluateVariant]
    W --> X[AI Agent: Score Variant]
    X --> Y[Parse EvaluationScore]
    Y --> Z[Update Variant Score]

    V -->|Yes| AA{More Variants?}
    Z --> AA

    AA -->|Yes| U
    AA -->|No| AB[Find Best in Generation]
    AB --> AC{Better than Global Best?}
    AC -->|Yes| AD[Update Global Best]
    AC -->|No| AE[Display Results]
    AD --> AE
    AE --> AF[Update UI Tabs]
    AF --> AG[End Generation N]
```

## Component Interactions

### AI Agent Interaction Pattern

```mermaid
sequenceDiagram
    participant Task as GeneticOptimizationTask
    participant Agent as ParsedAgent/ChatAgent
    participant API as ChatInterface
    participant Model as LLM

    Task->>Agent: Create with prompt & config
    Task->>Agent: answer(context)
    Agent->>API: chat(messages)
    API->>Model: Send request
    Model-->>API: Response
    API-->>Agent: Response text
    Agent->>Agent: Parse response
    Agent-->>Task: Typed object
    Task->>Task: Validate & process
```

### Mutation Generation Flow

```mermaid
sequenceDiagram
    participant Task as GeneticOptimizationTask
    participant Agent as ParsedAgent<TextVariant>
    participant API as ChatInterface

    Task->>Task: Select parent variant
    Task->>Task: Choose mutation strategy
    Task->>Agent: Create with strategy prompt

    Note over Agent: Prompt includes:<br/>- Optimization goal<br/>- Strategy type<br/>- Parent text<br/>- Parent evaluation<br/>- Constraints

    Task->>Agent: answer([parent_text])
    Agent->>API: Generate variant
    API-->>Agent: TextVariant JSON
    Agent->>Agent: Parse & validate
    Agent-->>Task: TextVariant object

    Task->>Task: Create EvaluatedVariant
    Task->>Task: Add to population
```

### Evaluation Flow

```mermaid
sequenceDiagram
    participant Task as GeneticOptimizationTask
    participant Agent as ParsedAgent<EvaluationScore>
    participant API as ChatInterface

    Task->>Agent: Create with evaluation prompt

    Note over Agent: Prompt includes:<br/>- Optimization goal<br/>- Evaluation criteria<br/>- Weights<br/>- Constraints

    Task->>Agent: answer([variant_text])
    Agent->>API: Evaluate text
    API-->>Agent: EvaluationScore JSON
    Agent->>Agent: Parse & validate

    alt Validation Success
        Agent-->>Task: EvaluationScore object
        Task->>Task: Update variant score
    else Validation Failure
        Agent-->>Task: Default score (0.0)
        Task->>Task: Log warning
    end
```

## Data Structures

### Configuration Data Flow

```mermaid
flowchart LR
    A[User Input] --> B[GeneticOptimizationTaskExecutionConfigData]
    B --> C{validate()}
    C -->|Valid| D[Task Execution]
    C -->|Invalid| E[Error Response]

    D --> F[Extract Parameters]
    F --> G[initial_text]
    F --> H[optimization_goal]
    F --> I[num_generations]
    F --> J[population_size]
    F --> K[selection_size]
    F --> L[mutation_strategies]
    F --> M[enable_crossover]
    F --> N[evaluation_weights]
    F --> O[constraints]

    G --> P[Evolution Process]
    H --> P
    I --> P
    J --> P
    K --> P
    L --> P
    M --> P
    N --> P
    O --> P
```

### Population Evolution Data Structure

```mermaid
graph TD
    A[evolutionHistory: List] --> B[Generation 0]
    A --> C[Generation 1]
    A --> D[Generation N]

    B --> E[List of EvaluatedVariant]
    C --> F[List of EvaluatedVariant]
    D --> G[List of EvaluatedVariant]

    E --> H[EvaluatedVariant 1]
    E --> I[EvaluatedVariant 2]

    H --> J[text: String]
    H --> K[score: EvaluationScore]
    H --> L[generation: Int]
    H --> M[parentIndex: Int?]
    H --> N[strategy: String]

    K --> O[overall_score: Double]
    K --> P[criteria_scores: Map]
    K --> Q[strengths: List]
    K --> R[weaknesses: List]
    K --> S[justification: String]
```

## Algorithm Details

### Selection Strategy

```mermaid
flowchart TD
    A[Current Population] --> B[Sort by overall_score DESC]
    B --> C[Take top selection_size variants]
    C --> D[Survivors]

    D --> E{For each survivor}
    E --> F[Calculate mutations needed]
    F --> G[mutations_per_survivor = max 1, mutations_needed / survivors.size]

    G --> H{Is last survivor?}
    H -->|Yes| I[Get remaining slots]
    H -->|No| J[Get mutations_per_survivor]

    I --> K[Generate mutations]
    J --> K

    K --> L[New Population = Survivors + Mutations]
```

### Fitness Calculation

```mermaid
flowchart TD
    A[Variant Text] --> B[AI Evaluation Agent]
    B --> C[Score Each Criterion 0-100]

    C --> D[clarity_score]
    C --> E[conciseness_score]
    C --> F[impact_score]
    C --> G[goal_alignment_score]

    D --> H[Apply Weight: w1]
    E --> I[Apply Weight: w2]
    F --> J[Apply Weight: w3]
    G --> K[Apply Weight: w4]

    H --> L[weighted_sum]
    I --> L
    J --> L
    K --> L

    L --> M[overall_score = weighted_sum]

    M --> N[EvaluationScore Object]
    C --> O[criteria_scores Map]
    O --> N

    B --> P[Identify Strengths]
    B --> Q[Identify Weaknesses]
    B --> R[Generate Justification]

    P --> N
    Q --> N
    R --> N
```

### Crossover Mechanism

```mermaid
flowchart TD
    A[Parent 1 Text + Score] --> C[AI Crossover Agent]
    B[Parent 2 Text + Score] --> C

    C --> D[Analyze Parent 1 Strengths]
    C --> E[Analyze Parent 2 Strengths]

    D --> F[Extract Best Elements]
    E --> F

    F --> G[Combine Elements]
    G --> H[Ensure Coherence]
    H --> I[Generate Offspring Text]

    I --> J[New Variant]
    J --> K[Add to Population]
```

## UI Component Structure

### Tab Organization

```mermaid
graph TD
    A[TabbedDisplay] --> B[Overview Tab]
    A --> C[Generation 1 Tab]
    A --> D[Generation 2 Tab]
    A --> E[Generation N Tab]
    A --> F[Evolution Analysis Tab]

    B --> G[Configuration Summary]
    B --> H[Initial Evaluation]
    B --> I[Progress Updates]
    B --> J[Final Metrics]

    C --> K[Generation Status]
    C --> L[Population Statistics]
    C --> M[Top 3 Variants]
    C --> N[Detailed Scores]

    F --> O[Fitness Progression Table]
    F --> P[Strategy Effectiveness]
    F --> Q[Best Variant Evolution]
    F --> R[Improvement Summary]
```

### Real-time Update Flow

```mermaid
sequenceDiagram
    participant Task as GeneticOptimizationTask
    participant Tabs as TabbedDisplay
    participant Overview as Overview Tab
    participant Gen as Generation Tab
    participant Session as SessionTask

    Task->>Tabs: Create TabbedDisplay
    Task->>Overview: Create overview task
    Task->>Overview: Add initial content
    Session->>Session: task.update()

    loop For each generation
        Task->>Gen: Create generation task
        Task->>Gen: Add "In Progress" status
        Session->>Session: task.update()

        Task->>Task: Generate & evaluate variants

        Task->>Gen: Add results markdown
        Session->>Session: task.update()

        Task->>Overview: Append generation summary
        Session->>Session: task.update()
    end

    Task->>Tabs: Create evolution analysis tab
    Task->>Tabs: Add final analysis
    Session->>Session: task.update()

    Task->>Overview: Add completion status
    Session->>Session: task.complete()
```

## Error Handling

### Validation and Error Flow

```mermaid
flowchart TD
    A[Task Start] --> B{Config Valid?}
    B -->|No| C[Log Error]
    C --> D[task.complete with error]
    D --> E[task.error with ValidationError]
    E --> F[Close Transcript]
    F --> G[Return Error Message]

    B -->|Yes| H[Execute Task]

    H --> I{AI Agent Call}
    I -->|Success| J[Process Response]
    I -->|Exception| K[Log Warning]
    K --> L[Return Default/Null]
    L --> M{Critical?}
    M -->|Yes| N[Fail Task]
    M -->|No| O[Continue with Fallback]

    J --> P{More Work?}
    P -->|Yes| H
    P -->|No| Q[Complete Successfully]

    N --> R[task.error]
    R --> S[Close Transcript]
    S --> T[Return Error]

    Q --> U[Generate Report]
    U --> V[task.complete]
    V --> W[Return Success]
```

### Agent Failure Handling

```mermaid
flowchart TD
    A[Call AI Agent] --> B{Try}
    B --> C[Agent.answer]
    C --> D[Parse Response]
    D --> E{Valid?}

    E -->|Yes| F[Return Object]
    E -->|No| G[Validation Error]

    B --> H{Catch Exception}
    H --> I[Log Warning]
    I --> J{Agent Type}

    J -->|Mutation| K[Return null]
    J -->|Crossover| L[Return null]
    J -->|Evaluation| M[Return Default Score 0.0]

    K --> N[Skip Variant]
    L --> O[Skip Crossover]
    M --> P[Continue with Low Score]

    G --> I
```

## Performance Considerations

### Computational Complexity

```mermaid
graph TD
    A[Total Complexity] --> B[O G * P * E]

    B --> C[G = num_generations]
    B --> D[P = population_size]
    B --> E[E = evaluation_cost]

    E --> F[AI API Call]
    F --> G[Network Latency]
    F --> H[Model Inference]
    F --> I[Response Parsing]

    A --> J[Optimization Strategies]
    J --> K[Parallel Evaluation Possible]
    J --> L[Caching Survivors]
    J --> M[Early Stopping on Convergence]
```

### Memory Usage Pattern

```mermaid
flowchart LR
    A[Memory Usage] --> B[evolutionHistory]
    A --> C[currentPopulation]
    A --> D[UI Components]
    A --> E[Transcript Buffer]

    B --> F[G * P * V]
    C --> G[P * V]
    D --> H[G * T]
    E --> I[Streaming]

    F --> J[G=generations<br/>P=population<br/>V=variant_size]
    G --> J
    H --> K[T=tab_content_size]
```

## Extension Points

### Adding Custom Mutation Strategies

```mermaid
flowchart TD
    A[Define New Strategy] --> B[Add to mutation_strategies list]
    B --> C[generateMutation receives strategy]
    C --> D[AI Agent Prompt includes strategy]
    D --> E[Agent applies strategy-specific logic]
    E --> F[Returns TextVariant]

    G[Strategy Examples] --> H[rephrase]
    G --> I[simplify]
    G --> J[elaborate]
    G --> K[restructure]
    G --> L[emphasize]
    G --> M[soften]
    G --> N[Custom Strategy]
```

### Custom Evaluation Criteria

```mermaid
flowchart TD
    A[Define Criteria] --> B[Add to evaluation_weights]
    B --> C[evaluateVariant receives weights]
    C --> D[AI Agent Prompt includes criteria]
    D --> E[Agent scores each criterion]
    E --> F[Returns criteria_scores Map]
    F --> G[Calculate weighted overall_score]

    H[Default Criteria] --> I[clarity: 0.35]
    H --> J[conciseness: 0.25]
    H --> K[impact: 0.25]
    H --> L[goal_alignment: 0.15]

    M[Custom Criteria] --> N[User-defined weights]
    N --> O[Sum must equal 1.0]
```

## Integration Points

### Task Orchestration Integration

```mermaid
sequenceDiagram
    participant Orch as TaskOrchestrator
    participant Task as GeneticOptimizationTask
    participant Agent as AI Agents
    participant UI as SessionTask

    Orch->>Task: run(agent, messages, task, resultFn, config)
    Task->>Task: Validate configuration
    Task->>UI: Create tabs
    Task->>Orch: getPriorCode(executionState)
    Orch-->>Task: Prior context

    loop Evolution
        Task->>Agent: Generate mutations
        Agent-->>Task: Variants
        Task->>Agent: Evaluate variants
        Agent-->>Task: Scores
        Task->>UI: Update displays
    end

    Task->>UI: Generate final report
    Task->>Orch: resultFn(summary)
    Task->>UI: task.complete()
```

### File System Integration

```mermaid
flowchart TD
    A[Task Execution] --> B[Create Transcript File]
    B --> C[transcript_YYYYMMDDHHMMSS.md]

    A --> D[Write Progress]
    D --> E[FileOutputStream]
    E --> C

    A --> F[Generate Links]
    F --> G[task.linkTo]
    G --> H[Markdown Link]
    G --> I[HTML Link]
    G --> J[PDF Link]

    A --> K[Complete Task]
    K --> L[Close Transcript]
    L --> M[File Available]
```

## Summary

The `GeneticOptimizationTask` implements a sophisticated genetic algorithm for text optimization with the following key characteristics:

1. **Modular Design**: Separation of concerns between mutation, crossover, evaluation, and selection
2. **AI-Driven Evolution**: Uses LLM agents for intelligent variant generation and evaluation
3. **Real-time Feedback**: Progressive UI updates showing evolution progress
4. **Configurable**: Extensive configuration options for generations, population, strategies, and criteria
5. **Robust Error Handling**: Graceful degradation when AI agents fail
6. **Comprehensive Reporting**: Detailed analysis of evolution history and strategy effectiveness

The system leverages AI agents as the core intelligence for both generating creative variations and objectively evaluating fitness, making it a hybrid symbolic-neural approach to text optimization.

