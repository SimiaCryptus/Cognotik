# LLMPollSimulationTask Technical Design

## Overview

The `LLMPollSimulationTask` is a sophisticated framework for simulating polls and surveys using LLMs to model diverse respondent personas. It enables researchers to test survey instruments, explore response patterns across demographics, and analyze results before conducting real-world surveys.

## Architecture

### Class Hierarchy

```mermaid
classDiagram
    AbstractTask <|-- LLMPollSimulationTask
    TaskExecutionConfig <|-- LLMPollSimulationTaskExecutionConfigData
    ValidatedObject <|.. LLMPollSimulationTaskExecutionConfigData

    class AbstractTask {
        +OrchestrationConfig orchestrationConfig
        +TaskExecutionConfig executionConfig
        +run()
        +promptSegment()
    }

    class LLMPollSimulationTask {
        +LLMPollSimulationTaskExecutionConfigData executionConfig
        +run()
        +promptSegment()
        -generateRespondents()
        -conductSurvey()
        -analyzeResults()
        -generateCrossTabs()
        -detectBiases()
    }

    class LLMPollSimulationTaskExecutionConfigData {
        +List~SurveyQuestion~ questions
        +List~RespondentProfile~ respondent_profiles
        +Int respondents_per_profile
        +Boolean include_demographics
        +List~String~ demographic_dimensions
        +Boolean cross_tabulation
        +Boolean sentiment_analysis
        +Boolean bias_detection
        +Double temperature
        +validate()
    }

    class SurveyQuestion {
        +String id
        +String text
        +QuestionType type
        +List~String~ options
        +Boolean required
        +String conditional_on
        +Map~String,Any~ validation
    }

    class RespondentProfile {
        +String id
        +String description
        +Map~String,String~ demographics
        +List~String~ characteristics
        +String background_context
    }

    class SimulatedRespondent {
        +String id
        +RespondentProfile profile
        +Map~String,String~ demographics
        +String persona_prompt
    }

    class SurveyResponse {
        +String respondent_id
        +Map~String,Any~ answers
        +Map~String,String~ demographics
        +Long response_time
        +String reasoning
    }

    LLMPollSimulationTask --> SurveyQuestion
    LLMPollSimulationTask --> RespondentProfile
    LLMPollSimulationTask --> SimulatedRespondent
    LLMPollSimulationTask --> SurveyResponse
```

## Data Flow

### High-Level Poll Simulation Flow

```mermaid
flowchart TD
    A[Start Poll Simulation] --> B[Validate Configuration]
    B --> C{Valid?}
    C -->|No| D[Return Error]
    C -->|Yes| E[Create Transcript File]
    E --> F[Initialize Tabbed Display]
    F --> G[Generate Respondent Personas]
    G --> H[Create Survey Instrument]
    H --> I[Create Thread Pool]
    I --> J[Conduct Survey Concurrently]
    J --> K[Collect Responses]
    K --> L[Validate Response Quality]
    L --> M[Generate Descriptive Statistics]
    M --> N[Create Cross-Tabulations]
    N --> O[Perform Sentiment Analysis]
    O --> P[Detect Response Biases]
    P --> Q[Generate Insights via LLM]
    Q --> R[Create Final Report]
    R --> S[Complete Task]

    style A fill:#90EE90
    style S fill:#90EE90
    style D fill:#FFB6C1
```

### Detailed Execution Flow

```mermaid
sequenceDiagram
    participant User
    participant Task as LLMPollSimulationTask
    participant Config as ExecutionConfig
    participant PersonaGen as Persona Generator
    participant Pool as Thread Pool
    participant Agent as ChatAgent
    participant Analyzer as Response Analyzer
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

        Task->>PersonaGen: generateRespondents()
        PersonaGen->>PersonaGen: For each profile
        PersonaGen->>PersonaGen: Generate N personas
        PersonaGen-->>Task: List<SimulatedRespondent>

        loop For each respondent
            Task->>Pool: submit survey
            Pool->>Agent: conductSurvey(respondent, questions)
            Agent->>Agent: Build persona context
            Agent->>Agent: Present questions
            Agent->>Agent: Collect answers
            Agent->>Agent: Extract reasoning
            Agent-->>Pool: SurveyResponse
            Pool->>Task: Store response
        end

        Task->>Analyzer: validateResponses()
        Analyzer-->>Task: quality metrics

        Task->>Stats: generateDescriptiveStats()
        Stats-->>Task: frequency distributions

        Task->>Stats: generateCrossTabs()
        Stats-->>Task: cross-tabulation tables

        Task->>Analyzer: performSentimentAnalysis()
        Analyzer-->>Task: sentiment scores

        Task->>Analyzer: detectBiases()
        Analyzer-->>Task: bias report

        Task->>Insights: generate insights
        Insights-->>Task: LLM-generated insights

        Task->>Task: generateFinalReport()
        Task-->>User: Complete with report link
    end
```

## Core Components

### 1. Configuration Data Structure

```mermaid
graph TD
    A[LLMPollSimulationTaskExecutionConfigData] --> B[questions]
    A --> C[respondent_profiles]
    A --> D[respondents_per_profile]
    A --> E[include_demographics]
    A --> F[demographic_dimensions]
    A --> G[cross_tabulation]
    A --> H[sentiment_analysis]
    A --> I[bias_detection]
    A --> J[temperature]

    B --> B1[List of SurveyQuestion objects]
    B1 --> B2[id, text, type, options]
    B1 --> B3[required, conditional_on]
    B1 --> B4[validation rules]

    C --> C1[List of RespondentProfile objects]
    C1 --> C2[id, description]
    C1 --> C3[demographics map]
    C1 --> C4[characteristics list]
    C1 --> C5[background_context]

    D --> D1[Integer: 1-1000]
    E --> E1[Boolean flag]
    F --> F1[List: age, gender, location, etc.]
    G --> G1[Boolean flag]
    H --> H1[Boolean flag]
    I --> I1[Boolean flag]
    J --> J1[Double: 0.0-1.0]

    style A fill:#87CEEB
    style B fill:#FFE4B5
    style C fill:#FFE4B5
```

### 2. Question Types

```mermaid
classDiagram
    class QuestionType {
        <<enumeration>>
        MULTIPLE_CHOICE
        SINGLE_CHOICE
        LIKERT_SCALE
        RATING
        OPEN_ENDED
        YES_NO
        RANKING
        MATRIX
        DEMOGRAPHIC
    }

    class SurveyQuestion {
        +String id
        +String text
        +QuestionType type
        +List~String~ options
        +Boolean required
        +String conditional_on
        +Map~String,Any~ validation
        +validate()
    }

    class ValidationRules {
        +Int min_length
        +Int max_length
        +String pattern
        +List~String~ allowed_values
        +Int min_selections
        +Int max_selections
    }

    SurveyQuestion --> QuestionType
    SurveyQuestion --> ValidationRules
```

### 3. Respondent Profile Structure

```mermaid
graph TD
    A[RespondentProfile] --> B[Core Identity]
    A --> C[Demographics]
    A --> D[Characteristics]
    A --> E[Background Context]

    B --> B1[Unique ID]
    B --> B2[Description/Label]

    C --> C1[Age/Age Range]
    C --> C2[Gender]
    C --> C3[Location]
    C --> C4[Education]
    C --> C5[Income Level]
    C --> C6[Occupation]
    C --> C7[Custom Demographics]

    D --> D1[Personality Traits]
    D --> D2[Values/Beliefs]
    D --> D3[Interests]
    D --> D4[Behaviors]
    D --> D5[Attitudes]

    E --> E1[Life Experiences]
    E --> E2[Cultural Context]
    E --> E3[Social Environment]

    style A fill:#87CEEB
    style B fill:#FFE4B5
    style C fill:#90EE90
    style D fill:#DDA0DD
    style E fill:#F0E68C
```

### 4. Persona Generation Pipeline

```mermaid
flowchart TD
    A[RespondentProfile Template] --> B[Generate N Personas]
    B --> C[For Each Persona]
    C --> D[Assign Unique ID]
    D --> E{Demographics Specified?}
    E -->|Yes| F[Use Exact Demographics]
    E -->|No| G[Generate Realistic Demographics]

    G --> G1[Sample from Distributions]
    G1 --> G2[Ensure Consistency]
    G2 --> G3[Apply Correlations]

    F --> H[Build Persona Prompt]
    G3 --> H

    H --> I[Include Background Context]
    I --> J[Add Characteristics]
    J --> K[Specify Response Style]
    K --> L[Create SimulatedRespondent]
    L --> M{More Personas?}
    M -->|Yes| C
    M -->|No| N[Return All Respondents]

    style A fill:#87CEEB
    style N fill:#90EE90
```

**Persona Prompt Template:**

```kotlin
fun buildPersonaPrompt(respondent: SimulatedRespondent): String {
    return """
        You are participating in a survey. Please respond authentically based on your profile:

        Demographics:
        ${respondent.demographics.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}

        Background:
        ${respondent.profile.background_context}

        Characteristics:
        ${respondent.profile.characteristics.joinToString("\n") { "- $it" }}

        Instructions:
        - Answer each question honestly from your perspective
        - Consider your background and values when responding
        - Provide brief reasoning for your answers when appropriate
        - If a question doesn't apply to you, indicate that clearly
        - Maintain consistency across your responses
    """.trimIndent()
}
```

### 5. Survey Conduction Flow

```mermaid
flowchart TD
    A[SimulatedRespondent] --> B[Create ChatAgent with Persona]
    B --> C[Initialize Response Object]
    C --> D[For Each Question]
    D --> E{Conditional Question?}
    E -->|Yes| F{Condition Met?}
    F -->|No| G[Skip Question]
    F -->|Yes| H[Present Question]
    E -->|No| H

    H --> I[Format Question with Options]
    I --> J[Submit to Agent]
    J --> K[Receive Response]
    K --> L[Parse Response]
    L --> M{Valid Response?}
    M -->|No| N[Request Clarification]
    N --> J
    M -->|Yes| O[Store Answer]

    O --> P[Extract Reasoning if Available]
    P --> Q{More Questions?}
    Q -->|Yes| D
    Q -->|No| R[Calculate Response Time]
    R --> S[Create SurveyResponse]
    S --> T[Return Response]

    style A fill:#87CEEB
    style T fill:#90EE90
    style N fill:#FFE4B5
```

### 6. Response Parsing and Validation

```mermaid
flowchart TD
    A[Raw LLM Response] --> B{Question Type}

    B -->|MULTIPLE_CHOICE| C[Extract Selected Options]
    C --> C1[Validate Against Options List]
    C1 --> C2{Valid?}
    C2 -->|Yes| C3[Store as List]
    C2 -->|No| C4[Request Re-answer]

    B -->|SINGLE_CHOICE| D[Extract Single Option]
    D --> D1[Validate Against Options]
    D1 --> D2{Valid?}
    D2 -->|Yes| D3[Store as String]
    D2 -->|No| D4[Request Re-answer]

    B -->|LIKERT_SCALE| E[Extract Numeric Value]
    E --> E1[Validate Range]
    E1 --> E2{Valid?}
    E2 -->|Yes| E3[Store as Integer]
    E2 -->|No| E4[Request Re-answer]

    B -->|RATING| F[Extract Rating]
    F --> F1[Validate Range]
    F1 --> F2{Valid?}
    F2 -->|Yes| F3[Store as Double]
    F2 -->|No| F4[Request Re-answer]

    B -->|OPEN_ENDED| G[Extract Text]
    G --> G1[Validate Length]
    G1 --> G2{Valid?}
    G2 -->|Yes| G3[Store as String]
    G2 -->|No| G4[Request Re-answer]

    B -->|YES_NO| H[Extract Boolean]
    H --> H1[Normalize Response]
    H1 --> H2[Store as Boolean]

    B -->|RANKING| I[Extract Ordered List]
    I --> I1[Validate All Items Present]
    I1 --> I2{Valid?}
    I2 -->|Yes| I3[Store as List]
    I2 -->|No| I4[Request Re-answer]

    C3 --> J[Success]
    D3 --> J
    E3 --> J
    F3 --> J
    G3 --> J
    H2 --> J
    I3 --> J

    C4 --> K[Retry]
    D4 --> K
    E4 --> K
    F4 --> K
    G4 --> K
    I4 --> K

    style A fill:#87CEEB
    style J fill:#90EE90
    style K fill:#FFB6C1
```
