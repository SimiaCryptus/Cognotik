# Reasoning Tools User Guide

## Table of Contents

1. [Introduction](#introduction)
2. [Tool Overview](#tool-overview)
3. [Getting Started](#getting-started)
4. [Individual Tool Guides](#individual-tool-guides)
5. [Best Practices](#best-practices)
6. [Advanced Usage](#advanced-usage)
7. [Troubleshooting](#troubleshooting)

---

## Introduction

The Reasoning Tools suite provides advanced AI-powered analysis capabilities for complex problem-solving,
decision-making, and code analysis. These tools implement various cognitive reasoning strategies to help you explore
problems from multiple angles, validate assumptions, and generate high-quality solutions.

### What Are Reasoning Tools?

Reasoning tools are specialized AI agents that apply structured thinking methodologies to analyze problems, code, and
decisions. Unlike simple code generation, these tools:

- **Think systematically** through problems using proven reasoning frameworks
- **Challenge assumptions** and explore alternatives
- **Provide transparency** into the reasoning process
- **Validate conclusions** before presenting them
- **Synthesize insights** from multiple perspectives

### When to Use Reasoning Tools

Use reasoning tools when you need:

- Deep analysis of complex problems
- Multiple perspectives on architectural decisions
- Root cause analysis for bugs or issues
- Validation of design choices
- Exploration of alternative approaches
- Understanding of causal relationships
- Pattern identification and refactoring opportunities

---

## Tool Overview

### Quick Reference Table

| Tool                         | Best For                          | Complexity | Output Type             |
|------------------------------|-----------------------------------|------------|-------------------------|
| **AbstractionLadder**        | Finding patterns, generalizations | Medium     | Hierarchical analysis   |
| **AnalogicalReasoning**      | Creative problem-solving          | Medium     | Analogies + solutions   |
| **CausalInference**          | Root cause analysis               | High       | Causal relationships    |
| **ChainOfThought**           | Step-by-step reasoning            | Medium     | Reasoning chain         |
| **ConstraintSatisfaction**   | Multi-objective optimization      | High       | Optimal solution        |
| **CounterfactualAnalysis**   | What-if scenarios                 | Medium     | Scenario comparisons    |
| **DecompositionSynthesis**   | Breaking down complexity          | High       | Subproblem solutions    |
| **MetaCognitiveReflection**  | Quality assurance                 | Low        | Critique + improvements |
| **MultiPerspectiveAnalysis** | Holistic evaluation               | Medium     | Multi-view synthesis    |
| **SocraticDialogue**         | Deep exploration                  | Medium     | Dialogue transcript     |

---

## Getting Started

### Basic Configuration

All reasoning tools share common configuration patterns:

```kotlin
task_description: "Brief description of what you want to analyze"
task_dependencies: ["task_id_1", "task_id_2"]  // Optional: reference previous tasks
state: Pending  // Task state
related_files: ["src/**/*.kt", "docs/*.md"]  // Optional: context files
```

### Workflow Integration

Reasoning tools work best when integrated into a task workflow:

1. **Gather Context**: Use related_files to provide relevant code/documentation
2. **Reference Dependencies**: Link to previous task results for continuity
3. **Chain Tools**: Use multiple reasoning tools in sequence for comprehensive analysis
4. **Validate Results**: Use MetaCognitiveReflection to critique other tool outputs

---

## Individual Tool Guides

### 1. AbstractionLadder

**Purpose**: Traverse abstraction levels to identify patterns, generalizations, and specific implementations.

#### Configuration

```kotlin
AbstractionLadderTaskExecutionConfigData(
    concrete_concept = "The specific code pattern or concept to analyze",
    direction = "both",  // "up" (generalize), "down" (concretize), or "both"
    levels = 3,  // Number of abstraction levels (1-5)
    identify_patterns = true,  // Find design patterns and anti-patterns
    related_files = listOf("src/main/kotlin/**/*.kt")
)
```

#### Use Cases

**Example 1: Understanding a Design Pattern**

```kotlin
concrete_concept = "Observer pattern implementation in EventBus.kt"
direction = "up"
levels = 3
identify_patterns = true
```

**Output**:

- Level 0: Specific EventBus implementation
- Level 1: Observer pattern abstraction
- Level 2: Publish-subscribe messaging
- Level 3: Event-driven architecture
- Pattern analysis and refactoring opportunities

**Example 2: Finding Concrete Implementations**

```kotlin
concrete_concept = "Repository pattern"
direction = "down"
levels = 3
identify_patterns = true
```

**Output**:

- Level 0: Repository pattern concept
- Level -1: Database repository implementations
- Level -2: Specific DAO classes
- Level -3: SQL query implementations
- Implementation patterns and best practices

#### Best Practices

- **Start concrete**: Begin with specific code examples for upward analysis
- **Limit levels**: 3-4 levels usually provide optimal insight
- **Enable patterns**: Always enable pattern identification for actionable insights
- **Provide context**: Include related files to understand the codebase structure

#### Common Pitfalls

❌ **Too abstract**: Starting with "software architecture" is too broad
✅ **Just right**: Starting with "UserRepository class in our codebase"

❌ **Too many levels**: 7+ levels become too abstract/specific to be useful
✅ **Optimal**: 3-4 levels provide actionable insights

---

### 2. AnalogicalReasoning

**Purpose**: Solve problems by finding and applying analogies from different domains.

#### Configuration

```kotlin
AnalogicalReasoningTaskExecutionConfigData(
    source_domain = "biological immune systems",
    target_problem = "Designing a microservices health check system",
    num_analogies = 3,
    validate_mappings = true,
    related_files = listOf("docs/architecture.md")
)
```

#### Use Cases

**Example 1: Architectural Design**

```kotlin
source_domain = "urban traffic management"
target_problem = "Optimizing API request routing and load balancing"
num_analogies = 3
validate_mappings = true
```

**Output**:

- Analogy 1: Traffic lights → Rate limiting
- Analogy 2: Highway lanes → Request queues
- Analogy 3: GPS routing → Dynamic load balancing
- Structural mappings and validation
- Concrete implementation suggestions

**Example 2: Error Handling**

```kotlin
source_domain = "medical diagnosis and treatment"
target_problem = "Implementing robust error handling and recovery"
num_analogies = 3
```

**Output**:

- Analogy 1: Symptoms → Error signals
- Analogy 2: Diagnosis → Root cause analysis
- Analogy 3: Treatment → Recovery strategies
- Implementation patterns

#### Best Practices

- **Choose rich domains**: Select source domains with well-understood structures
- **Be specific**: Narrow target problems yield better analogies
- **Validate mappings**: Always enable validation for structural coherence
- **Iterate**: Try multiple source domains for different perspectives

#### Effective Source Domains

✅ **Good choices**:

- Biological systems (immune system, neural networks, ecosystems)
- Physical systems (hydraulics, electrical circuits, mechanics)
- Social systems (organizations, markets, cities)
- Natural phenomena (weather, geology, evolution)

❌ **Poor choices**:

- Abstract concepts (philosophy, mathematics)
- Overly similar domains (comparing two programming paradigms)

---

### 3. CausalInference

**Purpose**: Identify true causal relationships and root causes, distinguishing correlation from causation.

#### Configuration

```kotlin
CausalInferenceTaskExecutionConfigData(
    observed_effect = "API response times increased by 300% after deployment",
    potential_causes = listOf(
        "Database connection pool exhaustion",
        "Increased traffic volume",
        "New caching layer misconfiguration",
        "Memory leak in service"
    ),
    build_causal_graph = true,
    identify_confounders = true,
    evidence_sources = listOf("logs/**/*.log", "metrics/*.json")
)
```

#### Use Cases

**Example 1: Performance Degradation**

```kotlin
observed_effect = "Application crashes every 6 hours"
potential_causes = listOf(
    "Memory leak",
    "Database connection leak",
    "Scheduled job interference",
    "External API timeout accumulation"
)
build_causal_graph = true
identify_confounders = true
evidence_sources = listOf("logs/application.log", "metrics/heap-dump.json")
```

**Output**:

- Causal analysis of each potential cause
- Evidence evaluation (temporal precedence, mechanism, counterfactuals)
- Root cause identification
- Causal graph visualization
- Confounding factors (e.g., time of day, traffic patterns)
- Recommendations

**Example 2: Bug Investigation**

```kotlin
observed_effect = "User data corruption in production"
potential_causes = listOf(
    "Race condition in concurrent writes",
    "Database transaction isolation issue",
    "Cache invalidation bug",
    "Serialization error"
)
identify_confounders = true
evidence_sources = listOf("logs/error.log", "src/main/kotlin/data/*.kt")
```

#### Best Practices

- **Gather evidence**: Provide logs, metrics, and relevant code files
- **List hypotheses**: Include all plausible causes, even unlikely ones
- **Enable confounders**: Always identify confounding variables
- **Build graphs**: Visual causal graphs aid understanding
- **Temporal data**: Include timestamps in evidence for temporal analysis

#### Causal Reasoning Principles

The tool applies these principles:

1. **Temporal Precedence**: Cause must precede effect
2. **Mechanism**: Explain HOW the cause produces the effect
3. **Counterfactual**: What would happen without the cause?
4. **Elimination**: Rule out alternative explanations
5. **Dose-Response**: Stronger cause → stronger effect

---

### 4. ChainOfThought

**Purpose**: Break down complex problems into explicit, validated reasoning steps.

#### Configuration

```kotlin
ChainOfThoughtTaskExecutionConfigData(
    problem_statement = "Design a distributed caching strategy for our microservices",
    reasoning_depth = null,  // Auto-determine depth
    validate_steps = true,
    related_files = listOf("docs/architecture.md", "src/main/kotlin/cache/*.kt")
)
```

#### Use Cases

**Example 1: Algorithm Design**

```kotlin
problem_statement = """
Design an efficient algorithm to find the k most frequent elements
in a stream of data with memory constraints.
"""
reasoning_depth = 5
validate_steps = true
```

**Output**:

```
Step 1: Understand constraints
- Reasoning: Stream means we can't store all data...
- Conclusion: Need online algorithm with bounded memory
- Confidence: 0.95
- Next: What data structures support this?

Step 2: Evaluate data structures
- Reasoning: Min-heap can maintain top-k elements...
- Conclusion: Min-heap + HashMap for frequency tracking
- Confidence: 0.90
- Next: How to handle updates efficiently?

[... continues with validation at each step ...]

Final Summary: Complete algorithm with complexity analysis
```

**Example 2: Architectural Decision**

```kotlin
problem_statement = """
Should we migrate from monolithic architecture to microservices?
Consider: team size (5 developers), current pain points (deployment coupling),
and business requirements (faster feature delivery).
"""
validate_steps = true
```

#### Best Practices

- **Clear problem**: State the problem precisely with constraints
- **Enable validation**: Always validate steps to catch logical errors
- **Provide context**: Include relevant files and previous decisions
- **Let it auto-depth**: Don't force reasoning_depth unless needed
- **Review backtracking**: Check if validation caused backtracking (indicates complexity)

#### When to Use

✅ **Good for**:

- Complex algorithmic problems
- Multi-factor decisions
- Problems requiring proof or justification
- Learning/understanding complex concepts

❌ **Not ideal for**:

- Simple, straightforward tasks
- When you need multiple perspectives (use MultiPerspectiveAnalysis)
- Exploratory analysis (use SocraticDialogue)

---

### 5. ConstraintSatisfaction

**Purpose**: Solve problems with multiple competing constraints and objectives.

#### Configuration

```kotlin
ConstraintSatisfactionTaskExecutionConfigData(
    problem_description = "Select a database technology for our new service",
    hard_constraints = listOf(
        "Must support ACID transactions",
        "Must handle 10,000 writes/second",
        "Must have active community support",
        "Budget limit: $5,000/month"
    ),
    soft_constraints = mapOf(
        "Minimize operational complexity" to 0.9,
        "Maximize query performance" to 0.8,
        "Prefer open-source solutions" to 0.6,
        "Minimize learning curve for team" to 0.7
    ),
    search_strategy = "backtracking",
    max_iterations = 100
)
```

#### Use Cases

**Example 1: Technology Selection**

```kotlin
problem_description = "Choose a frontend framework for our web application"
hard_constraints = listOf(
    "Must support TypeScript",
    "Must have component-based architecture",
    "Must be actively maintained",
    "Team has React experience"
)
soft_constraints = mapOf(
    "Minimize bundle size" to 0.8,
    "Maximize developer productivity" to 0.9,
    "Strong ecosystem and libraries" to 0.7,
    "Good documentation" to 0.6
)
search_strategy = "backtracking"
```

**Output**:

```
Solution Overview:
Recommended: React with Next.js

Decision Variables:
- Framework: React
- Meta-framework: Next.js
- State management: Zustand
- Styling: Tailwind CSS

Hard Constraint Satisfaction:
✓ TypeScript support: Native
✓ Component-based: Yes
✓ Actively maintained: Yes (Meta)
✓ Team experience: Existing React knowledge

Soft Constraint Optimization:
- Bundle size: 8/10 (with code splitting)
- Developer productivity: 9/10 (excellent DX)
- Ecosystem: 10/10 (largest ecosystem)
- Documentation: 9/10 (comprehensive)

Overall Score: 0.87 (weighted)

Alternative Solutions:
1. Vue 3 + Nuxt (score: 0.82)
2. Svelte + SvelteKit (score: 0.79)
```

**Example 2: Resource Allocation**

```kotlin
problem_description = "Allocate cloud resources across services"
hard_constraints = listOf(
    "Total budget: $10,000/month",
    "Each service must have minimum 2 instances",
    "Database must have backup redundancy",
    "Must meet SLA of 99.9% uptime"
)
soft_constraints = mapOf(
    "Minimize cost" to 0.9,
    "Maximize performance headroom" to 0.7,
    "Optimize for peak traffic handling" to 0.8
)
search_strategy = "local"  // Good for optimization problems
```

#### Search Strategies

**Backtracking** (systematic search)

- ✅ Finds optimal solution if one exists
- ✅ Explores solution space thoroughly
- ❌ Slower for large search spaces
- **Use when**: Solution quality is critical

**Forward** (greedy search)

- ✅ Fast, finds solutions quickly
- ✅ Good for time-constrained decisions
- ❌ May miss optimal solution
- **Use when**: Speed matters, "good enough" is acceptable

**Local** (hill-climbing)

- ✅ Excellent for optimization problems
- ✅ Handles continuous variables well
- ❌ Can get stuck in local optima
- **Use when**: Optimizing numerical parameters

#### Best Practices

- **Separate hard/soft**: Hard constraints are non-negotiable; soft are preferences
- **Weight carefully**: Higher weights (0.8-1.0) for critical factors
- **Normalize weights**: Ensure weights sum to reasonable total
- **Provide context**: Include related files with current architecture
- **Iterate**: Run multiple times with different strategies

#### Common Patterns

**Architecture Decisions**:

```kotlin
hard_constraints = ["Must scale to X users", "Budget limit", "Team skills"]
soft_constraints = { "Maintainability": 0.9, "Performance": 0.8, "Cost": 0.7 }
```

**Library Selection**:

```kotlin
hard_constraints = ["License compatibility", "Language support", "Maintenance status"]
soft_constraints = { "Community size": 0.7, "Documentation": 0.8, "Performance": 0.6 }
```

---

### 6. CounterfactualAnalysis

**Purpose**: Explore "what-if" scenarios to understand causal relationships and decision impacts.

#### Configuration

```kotlin
CounterfactualAnalysisTaskExecutionConfigData(
    actual_scenario = "We chose microservices architecture for our platform",
    counterfactuals = listOf(
        "What if we had chosen a modular monolith instead?",
        "What if we had used serverless functions?",
        "What if we had kept the original monolithic architecture?"
    ),
    compare_outcomes = true,
    control_factors = listOf(
        "Team size (5 developers)",
        "Budget ($50k/month)",
        "Traffic (100k requests/day)"
    ),
    related_files = listOf("docs/architecture-decision-record.md")
)
```

#### Use Cases

**Example 1: Post-Mortem Analysis**

```kotlin
actual_scenario = """
We deployed the new feature on Friday evening.
The deployment caused a 2-hour outage affecting 30% of users.
"""
counterfactuals = listOf(
    "What if we had deployed during business hours with gradual rollout?",
    "What if we had run the deployment in staging for 24 hours first?",
    "What if we had implemented feature flags for instant rollback?"
)
compare_outcomes = true
control_factors = listOf(
    "Same code changes",
    "Same infrastructure",
    "Same team on-call"
)
```

**Output**:

```
Actual Scenario Analysis:
- Friday evening deployment reduced immediate user impact
- Limited team availability for incident response
- Rollback took 45 minutes due to database migrations
- Total cost: $50k in lost revenue, reputation damage

Counterfactual 1: Business hours + gradual rollout
- Would have detected issues affecting 5% of users
- Full team available for immediate response
- Rollback within 10 minutes
- Estimated cost: $5k in lost revenue
- Key difference: Early detection through gradual rollout

Counterfactual 2: Extended staging period
- Would have caught the bug in staging
- Zero production impact
- Estimated cost: 1 day delay in feature release
- Key difference: More thorough testing

Counterfactual 3: Feature flags
- Same initial deployment
- Instant rollback (< 1 minute)
- Estimated cost: $10k in lost revenue
- Key difference: Rapid recovery capability

Comparative Analysis:
Best outcome: Counterfactual 2 (prevention)
Most practical: Counterfactual 3 (mitigation)
Lessons learned:
- Gradual rollouts are critical for risk mitigation
- Feature flags provide safety net
- Friday deployments carry higher risk
```

**Example 2: Technology Decision Validation**

```kotlin
actual_scenario = "We chose PostgreSQL for our primary database"
counterfactuals = listOf(
    "What if we had chosen MongoDB?",
    "What if we had chosen DynamoDB?",
    "What if we had used a multi-database approach?"
)
compare_outcomes = true
control_factors = listOf(
    "Same data model complexity",
    "Same query patterns",
    "Same scale requirements"
)
```

#### Best Practices

- **Be specific**: Detailed scenarios yield better analysis
- **Control variables**: Specify what stays constant for fair comparison
- **Realistic alternatives**: Choose plausible counterfactuals
- **Include context**: Provide decision records and historical data
- **Learn forward**: Use insights to improve future decisions

#### Analysis Framework

The tool evaluates:

1. **Outcomes**: What actually happened vs. what would have happened
2. **Causal factors**: Which factors drove the differences
3. **Trade-offs**: Benefits and costs of each scenario
4. **Lessons**: Actionable insights for future decisions

---

### 7. DecompositionSynthesis

**Purpose**: Break complex problems into manageable subproblems, solve them, and synthesize solutions.

#### Configuration

```kotlin
DecompositionSynthesisTaskExecutionConfigData(
    complex_problem = "Implement a real-time collaborative document editing system",
    decomposition_strategy = "functional",  // or "temporal", "spatial", "hierarchical"
    max_depth = 3,
    synthesize_solution = true,
    validate_coherence = true,
    related_files = listOf("docs/requirements.md")
)
```

#### Decomposition Strategies

**Functional** (by capability)

```kotlin
complex_problem = "Build an e-commerce platform"
decomposition_strategy = "functional"
```

Output subproblems:

- User authentication and authorization
- Product catalog management
- Shopping cart functionality
- Payment processing
- Order fulfillment
- Inventory management

**Temporal** (by sequence)

```kotlin
complex_problem = "Implement CI/CD pipeline"
decomposition_strategy = "temporal"
```

Output subproblems:

- Code commit and version control
- Automated testing phase
- Build and artifact creation
- Deployment to staging
- Production deployment
- Monitoring and rollback

**Spatial** (by location/component)

```kotlin
complex_problem = "Design microservices architecture"
decomposition_strategy = "spatial"
```

Output subproblems:

- API Gateway layer
- Authentication service
- Business logic services
- Data persistence layer
- Message queue infrastructure
- Monitoring and logging

**Hierarchical** (by abstraction level)

```kotlin
complex_problem = "Optimize application performance"
decomposition_strategy = "hierarchical"
```

Output subproblems:

- Level 1: System architecture optimization
- Level 2: Service-level optimizations
- Level 3: Algorithm and data structure improvements
- Level 4: Code-level micro-optimizations

#### Use Cases

**Example 1: Feature Implementation**

```kotlin
complex_problem = """
Implement a recommendation engine that:
- Analyzes user behavior in real-time
- Generates personalized recommendations
- Handles 1M+ users
- Updates recommendations within 100ms
"""
decomposition_strategy = "functional"
max_depth = 3
synthesize_solution = true
validate_coherence = true
```

**Output**:

```
Problem Decomposition (Functional Strategy):

Subproblems:
1. SP1: Real-time data ingestion pipeline
   - Complexity: 7/10
   - Can decompose: Yes
   - Dependencies: None

2. SP2: User behavior analysis engine
   - Complexity: 8/10
   - Can decompose: Yes
   - Dependencies: SP1

3. SP3: Recommendation algorithm
   - Complexity: 9/10
   - Can decompose: Yes
   - Dependencies: SP2

4. SP4: Caching and serving layer
   - Complexity: 6/10
   - Can decompose: No
   - Dependencies: SP3

5. SP5: A/B testing framework
   - Complexity: 5/10
   - Can decompose: No
   - Dependencies: SP4

Subproblem Solutions:

SP1: Real-time data ingestion
Solution: Use Kafka for event streaming with Flink for processing
- Handles high throughput
- Provides exactly-once semantics
- Integrates with existing infrastructure
Confidence: 0.90

SP2: User behavior analysis
Solution: Implement session-based feature extraction with Redis
- Track user actions in real-time
- Compute behavioral features (clicks, time, patterns)
- Store in fast-access cache
Confidence: 0.85

[... continues for all subproblems ...]

Synthesized Solution:
Architecture: Event-driven recommendation pipeline

1. Data Flow:
   User actions → Kafka → Flink → Feature Store (Redis)
   → Recommendation Engine → Cache → API

2. Recommendation Engine:
   - Collaborative filtering for known users
   - Content-based for new users
   - Hybrid approach for best results

3. Performance Optimization:
   - Pre-compute recommendations for active users
   - Cache results with 5-minute TTL
   - Use approximate algorithms for speed

4. Scalability:
   - Horizontal scaling of Flink workers
   - Redis cluster for distributed caching
   - Load-balanced API servers

Overall Confidence: 0.87

Coherence Validation:
✓ Is Coherent: Yes
Issues: None
Suggestions:
- Consider adding fallback for cache misses
- Implement gradual rollout strategy
- Add monitoring for recommendation quality
```

#### Best Practices

- **Choose right strategy**: Match strategy to problem nature
- **Limit depth**: 2-3 levels usually sufficient
- **Enable synthesis**: Always synthesize for complete solution
- **Validate coherence**: Catch integration issues early
- **Document dependencies**: Clear dependency graph aids implementation

#### When to Use Each Strategy

| Strategy         | Best For               | Example                                |
|------------------|------------------------|----------------------------------------|
| **Functional**   | Feature-rich systems   | E-commerce platform, CRM system        |
| **Temporal**     | Process-oriented tasks | CI/CD pipeline, data migration         |
| **Spatial**      | Distributed systems    | Microservices, multi-tier architecture |
| **Hierarchical** | Optimization problems  | Performance tuning, refactoring        |

---

### 8. MetaCognitiveReflection

**Purpose**: Reflect on and critique reasoning processes to improve quality.

#### Configuration

```kotlin
MetaCognitiveReflectionTaskExecutionConfigData(
    subject_task_id = "task_123",  // ID of task to reflect upon
    reflection_aspects = listOf(
        "assumptions",
        "biases",
        "alternatives",
        "confidence",
        "completeness",
        "logic"
    ),
    suggest_improvements = true,
    identify_gaps = true,
    evaluate_confidence = true
)
```

