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

#### Reflection Aspects

**Assumptions**

- Identifies underlying assumptions (explicit and implicit)
- Evaluates validity and foundation of assumptions
- Explores consequences if assumptions are incorrect

**Biases**

- Detects cognitive biases (confirmation, anchoring, availability)
- Identifies unfair perspective favoritism
- Checks if alternative viewpoints were considered

**Alternatives**

- Explores unexplored methods or solutions
- Considers different frameworks
- Identifies unconsidered trade-offs

**Confidence**

- Assesses certainty levels of conclusions
- Evaluates supporting/undermining evidence
- Identifies areas of highest uncertainty

**Completeness**

- Checks if all relevant factors were considered
- Identifies missing elements in analysis
- Verifies edge case coverage

**Logic**

- Validates logical soundness
- Identifies logical fallacies or contradictions
- Verifies conclusions follow from premises

#### Use Cases

**Example 1: Code Review Quality Check**

```kotlin
subject_task_id = "code_review_task_456"
reflection_aspects = listOf(
  "assumptions",
  "completeness",
  "alternatives"
      +
)
+suggest_improvements = true
+identify_gaps = true
+```

**Output**:

```

Meta-Cognitive Reflection on Task: code_review_task_456

### 1. Underlying Assumptions

Identified Assumptions:

- Assumed all edge cases are covered by existing tests
  Validity: Questionable - no explicit edge case analysis performed
  Impact if incorrect: Critical bugs may reach production

- Assumed current error handling is sufficient
  Validity: Weak - no error scenarios were explicitly tested
  Impact if incorrect: Poor user experience, difficult debugging

- Assumed performance is acceptable without profiling
  Validity: Invalid - no performance testing conducted
  Impact if incorrect: Scalability issues under load

### 2. Completeness

Missing Considerations:

- Security implications not addressed
- Accessibility requirements not verified
- Database migration strategy not discussed
- Rollback plan not defined
- Monitoring and alerting not configured

Edge Cases Not Addressed:

- Concurrent modification scenarios
- Network timeout handling
- Large dataset performance
- Null/empty input handling

### 3. Alternative Approaches

Unexplored Alternatives:

1. Event-driven architecture instead of synchronous calls
  - Would improve decoupling
  - Better scalability
  - Trade-off: Increased complexity

2. Caching layer for frequently accessed data
  - Would reduce database load
  - Faster response times
  - Trade-off: Cache invalidation complexity

3. Batch processing for bulk operations
  - Would improve efficiency
  - Better resource utilization
  - Trade-off: Delayed processing

## Improvement Suggestions

1. Add explicit edge case testing
  - Create test cases for boundary conditions
  - Test with null/empty inputs
  - Verify concurrent access scenarios

2. Implement comprehensive error handling
  - Add try-catch blocks for external calls
  - Provide meaningful error messages
  - Log errors with context

3. Conduct performance profiling
  - Measure response times under load
  - Identify bottlenecks
  - Optimize critical paths

4. Address security concerns
  - Review input validation
  - Check authentication/authorization
  - Audit sensitive data handling

## Knowledge Gaps

Areas Requiring More Information:

- Expected traffic patterns and scale
- SLA requirements for response time
- Budget constraints for infrastructure
- Team expertise with proposed technologies

Questions Remaining Unanswered:

- How will this integrate with existing systems?
- What is the rollback strategy if issues arise?
- How will we monitor system health?
- What are the disaster recovery procedures?

## Confidence Assessment

High Confidence (0.8-1.0):

- Code follows established patterns
- Basic functionality is correct

Medium Confidence (0.5-0.7):

- Performance will be acceptable
- Error handling is sufficient

Low Confidence (0.0-0.4):

- All edge cases are covered
- Security is adequately addressed
- System will scale to required load

Overall Assessment: Medium confidence (0.65)
Recommendation: Address identified gaps before production deployment

```

**Example 2: Architectural Decision Review**

```kotlin
subject_task_id = "architecture_decision_789"
reflection_aspects = listOf(
    "assumptions",
    "biases",
    "alternatives",
    "logic"
     +)
     +suggest_improvements = true
     +evaluate_confidence = true
     +```

#### Best Practices

- **Use after major decisions**: Reflect on important architectural or design choices
- **Select relevant aspects**: Focus on 3-4 aspects most relevant to the task
- **Enable all features**: Always enable improvements, gaps, and confidence evaluation
- **Act on findings**: Use reflection insights to improve the original work
- **Iterate**: Reflect multiple times during complex projects

#### When to Use

✅ **Good for**:
- Quality assurance of reasoning processes
- Post-mortem analysis of decisions
- Identifying blind spots in analysis
- Improving team decision-making processes
- Learning from past mistakes

❌ **Not ideal for**:
- Initial problem exploration (use SocraticDialogue)
- Generating new solutions (use other reasoning tools first)
- Simple, straightforward tasks

---

### 9. MultiPerspectiveAnalysis

**Purpose**: Analyze problems from multiple viewpoints and synthesize unified conclusions.

#### Configuration

```kotlin
MultiPerspectiveAnalysisTaskExecutionConfigData(
    analysis_subject = "Migrating from REST to GraphQL API",
    perspectives = listOf(
        "technical",
        "business",
        "user experience",
        "security",
        "operations"
    ),
    synthesize = true,
    consensus_threshold = 0.7,
    related_files = listOf("docs/api-spec.md", "src/api/**/*.kt")
     +)
     +```

#### Perspectives Guide

**Technical Perspective**
- Implementation complexity
- Performance implications
- Scalability considerations
- Technology stack compatibility
- Maintenance burden

**Business Perspective**
- Cost implications
- Time to market
- ROI and value delivery
- Competitive advantage
- Risk assessment

**User Experience Perspective**
- Usability impact
- Feature accessibility
- Performance from user viewpoint
- Learning curve
- User satisfaction

**Security Perspective**
- Vulnerability assessment
- Compliance requirements
- Data protection
- Authentication/authorization
- Audit and monitoring

**Operations Perspective**
- Deployment complexity
- Monitoring and observability
- Incident response
- Backup and recovery
- Resource requirements

#### Use Cases

**Example 1: Technology Migration Decision**

```kotlin
analysis_subject = "Migrating from monolithic database to microservices with separate databases"
perspectives = listOf(
    "technical",
    "business",
    "operations",
    "security"
     +)
     +synthesize = true
     +consensus_threshold = 0.7
     +```

**Output**:

```

Multi-Perspective Analysis: Database Migration

## Technical Perspective

Key Considerations:

- Data consistency becomes more complex (distributed transactions)
- Need to implement saga pattern or event sourcing
- Query performance may improve (smaller, focused databases)
- Schema evolution becomes more manageable per service

Risks:

- Data duplication and synchronization challenges
- Increased complexity in cross-service queries
- Need for robust event handling infrastructure

Opportunities:

- Technology diversity (choose best DB per service)
- Independent scaling of data stores
- Improved fault isolation

Recommendations:

- Start with one or two services, not all at once
- Implement comprehensive event logging
- Use database-per-service pattern with shared nothing

Confidence: 0.85

## Business Perspective

Key Considerations:

- Initial investment: $150k-$200k (development + infrastructure)
- Ongoing costs: +$30k/year (additional database licenses/hosting)
- Time to complete: 6-9 months
- Reduced time-to-market for new features (after migration)

Risks:

- Extended migration period impacts feature delivery
- Potential revenue loss during transition
- Higher operational costs

Opportunities:

- Faster feature development post-migration
- Better team autonomy and productivity
- Competitive advantage through agility

Recommendations:

- Phase migration to minimize business disruption
- Measure and communicate ROI milestones
- Allocate contingency budget (20% of estimate)

Confidence: 0.75

## Operations Perspective

Key Considerations:

- Monitoring complexity increases significantly
- Need for distributed tracing and logging
- Backup/recovery becomes more complex
- Requires additional operational expertise

Risks:

- Increased operational burden on small team
- More failure points to monitor
- Complex debugging of distributed issues

Opportunities:

- Better observability with proper tooling
- Independent service deployments
- Improved incident isolation

Recommendations:

- Invest in observability platform (Datadog, New Relic)
- Implement comprehensive health checks
- Create runbooks for common scenarios
- Train team on distributed systems operations

Confidence: 0.70

## Security Perspective

Key Considerations:

- Increased attack surface (more databases to secure)
- Need for service-to-service authentication
- Data encryption in transit and at rest
- Compliance requirements per data store

Risks:

- Potential data leakage between services
- Inconsistent security policies
- Audit trail complexity

Opportunities:

- Fine-grained access control per service
- Better data isolation and protection
- Easier compliance auditing per domain

Recommendations:

- Implement mutual TLS for service communication
- Use secrets management (Vault, AWS Secrets Manager)
- Conduct security review of each service boundary
- Implement centralized audit logging

Confidence: 0.80

## Synthesis

### Common Themes

All perspectives agree on:

- Increased complexity is inevitable
- Proper tooling and infrastructure are critical
- Phased approach is necessary
- Team training and expertise are required

### Conflicts and Tensions

Technical vs. Business:

- Technical wants thorough migration; Business wants speed
- Resolution: Prioritize high-value services first

Operations vs. Technical:

- Operations concerned about complexity; Technical excited about benefits
- Resolution: Invest in observability before migration

### Consensus Assessment

Overall consensus level: 0.78 (above threshold of 0.70)

Strong agreement on:

- Migration is beneficial long-term
- Phased approach is necessary
- Investment in tooling is critical

Areas requiring attention:

- Operational readiness (training, tooling)
- Cost management and ROI tracking
- Security implementation details

### Unified Recommendation

**Proceed with migration using phased approach:**

Phase 1 (Months 1-3): Foundation

- Set up observability platform
- Implement service mesh for security
- Train team on distributed systems
- Migrate one non-critical service as proof-of-concept

Phase 2 (Months 4-6): Core Services

- Migrate 2-3 core services
- Establish patterns and best practices
- Measure and optimize performance
- Refine operational procedures

Phase 3 (Months 7-9): Completion

- Migrate remaining services
- Decommission monolithic database
- Optimize costs and performance
- Document lessons learned

**Success Criteria:**

- Feature delivery time reduced by 40%
- Service availability > 99.9%
- Team satisfaction improved
- ROI positive within 18 months

**Next Steps:**

1. Secure budget approval ($200k + contingency)
2. Select and implement observability platform
3. Identify first service for migration
4. Create detailed migration plan for Phase 1
5. Establish success metrics and tracking

```

#### Best Practices

- **Choose diverse perspectives**: Select 3-5 perspectives that cover different concerns
- **Enable synthesis**: Always synthesize for actionable conclusions
- **Set appropriate threshold**: 0.7 is good default; lower for exploratory analysis
- **Provide context**: Include relevant documentation and code
- **Document conflicts**: Understanding tensions leads to better solutions

#### Common Perspective Combinations

**Architecture Decisions**:
```kotlin
perspectives = listOf("technical", "business", "operations", "security")
```

**Feature Development**:

```kotlin
perspectives = listOf("user experience", "technical", "business", "accessibility")
```

**Infrastructure Changes**:

```kotlin
perspectives = listOf("operations", "security", "cost", "performance")
```

**Code Refactoring**:

```kotlin
perspectives = listOf("maintainability", "performance", "testing", "team expertise")
```

---

### 10. SocraticDialogue

**Purpose**: Explore ideas through Socratic questioning to achieve deep understanding.

#### Configuration

```kotlin
SocraticDialogueTaskExecutionConfigData(
  initial_question = "What makes code 'clean' and maintainable?",
  max_depth = 5,
  challenge_assumptions = true,
  domain_constraints = listOf("software engineering", "code quality")
      +
)
+```

#### How It Works

The tool creates two AI agents :

1.* * Socratic Questioner * * : Asks probing questions that:
-Challenge assumptions and definitions
    -Explore implications and consequences
    -Identify contradictions
    -Seek deeper understanding

2.* * Thoughtful Responder * * : Provides :
-Honest, thorough answers
-Clear definitions and reasoning
    -Acknowledgment of uncertainty
-Willingness to revise understanding

#### Use Cases

**Example 1: Exploring Design Principles**

```kotlin
initial_question = "Why is loose coupling important in software design?"
max_depth = 5
challenge_assumptions = true
domain_constraints = listOf("software architecture", "design patterns")
```

**Output**:

```
Socratic Dialogue: Exploring Loose Coupling

## Exchange 1

**Question:** Why is loose coupling important in software design?

**Response:** Loose coupling is important because it allows components to be
modified independently without affecting other parts of the system. This makes
the codebase more maintainable, testable, and flexible. When components are
loosely coupled, changes in one component don't cascade through the entire system.

## Exchange 2

**Question:** You say loose coupling allows independent modification, but doesn't
every component ultimately depend on others to function? How can we truly have
independence when the system must work as a whole?

**Response:** You're right to challenge this. True independence is impossible in
a functioning system. What we really mean by "loose coupling" is minimizing the
knowledge components have about each other's internal implementation. Components
still depend on each other, but through well-defined interfaces rather than
concrete implementations. The independence is about implementation details, not
about functionality.

## Exchange 3

**Question:** If components communicate through interfaces, haven't we just moved
the coupling from the implementation to the interface? What happens when the
interface needs to change?

**Response:** That's an excellent point. Interface coupling is indeed a form of
coupling, and you're correct that interface changes can be disruptive. However,
interfaces tend to be more stable than implementations because they represent
contracts about "what" rather than "how." When interfaces do need to change, we
can use versioning, adapters, or the facade pattern to manage the transition.
The key insight is that coupling is inevitable—we're choosing where to place it
for maximum stability.

## Exchange 4

**Question:** You mention that interfaces are more stable because they represent
"what" rather than "how," but isn't the "what" often driven by the "how" of the
implementation? Don't our implementation constraints shape what interfaces we can
realistically define?

**Response:** You've identified a fundamental tension in software design. Yes,
implementation realities do constrain interface design—we can't define interfaces
that are impossible to implement efficiently. This suggests that "loose coupling"
isn't about complete separation, but about finding the right balance. Perhaps the
goal isn't to eliminate coupling, but to make coupling explicit, intentional, and
manageable. We should couple to stable abstractions that reflect the problem
domain rather than implementation details.

## Exchange 5

**Question:** If coupling should be "explicit, intentional, and manageable," how
do we determine what constitutes a "stable abstraction" in the problem domain?
Don't problem domains themselves evolve and change?

**Response:** This is the crux of the challenge. Problem domains do evolve, which
means our abstractions must evolve too. A "stable abstraction" might be better
understood as one that captures fundamental concepts in the domain that change
slowly relative to implementation details. For example, in an e-commerce system,
concepts like "Product," "Order," and "Customer" are relatively stable, while
payment processing implementations change frequently. The art is in identifying
these core domain concepts through techniques like Domain-Driven Design. Loose
coupling, then, is about coupling to these slowly-changing domain concepts rather
than to rapidly-changing technical implementations.

## Key Insights

Through this dialogue, we discovered:

1. **Coupling is inevitable**: True independence is impossible in functioning systems

2. **Coupling is about knowledge**: Loose coupling minimizes knowledge of internal
   implementations, not elimination of all dependencies

3. **Interfaces are coupling**: Moving to interfaces doesn't eliminate coupling,
   it relocates it to a more stable location

4. **Implementation shapes interfaces**: The "what" and "how" are interconnected;
   we can't fully separate them

5. **Stability is relative**: "Stable abstractions" are those that change slowly
   relative to implementations, often rooted in domain concepts

6. **Loose coupling is a practice**: It's about making coupling explicit,
   intentional, and manageable, not about achieving perfect separation

## Assumptions Challenged

- Assumption: Loose coupling means components are independent
  Reality: Components are interdependent; loose coupling manages the nature of dependencies

- Assumption: Interfaces eliminate coupling
  Reality: Interfaces are a form of coupling, just more stable

- Assumption: Problem domains are stable
  Reality: Domains evolve; we seek relatively stable concepts within them

## Areas for Further Exploration

- How do we identify "core domain concepts" that are relatively stable?
- What metrics can we use to measure coupling in practice?
- How do we balance loose coupling with performance requirements?
- What role does testing play in managing coupling?
```

**Example 2: Debugging a Concept**

```kotlin
initial_question = "What is the difference between unit tests and integration tests?"
max_depth = 4
challenge_assumptions = true
domain_constraints = listOf("software testing", "test automation")
```

#### Best Practices

- **Start with genuine questions**: Choose questions you want to explore deeply
- **Enable assumption challenging**: This produces the most insightful dialogues
- **Limit depth appropriately**: 4-6 exchanges usually sufficient for most topics
- **Constrain domain**: Prevents dialogue from becoming too broad
- **Review the synthesis**: The key insights section is most valuable

#### When to Use

✅ **Good for**:

- Exploring complex concepts
- Challenging your own understanding
- Team learning and discussion
- Clarifying ambiguous requirements
- Philosophical or conceptual questions

❌ **Not ideal for**:

- Finding specific solutions (use other tools)
- Time-sensitive decisions
- Simple factual questions
- Problems requiring multiple perspectives (use MultiPerspectiveAnalysis)

#### Dialogue Depth Guide

- **3 exchanges**: Surface-level exploration, basic clarification
- **5 exchanges**: Standard depth, good for most topics
- **7+ exchanges**: Deep philosophical exploration, complex concepts

---

## Best Practices

### General Guidelines

#### 1. Provide Rich Context

✅ **Do**:

```kotlin
related_files = listOf(
  "src/main/kotlin/**/*.kt",
  "docs/architecture.md",
  "docs/requirements.md"
      +
)
+```

❌ **Don't**:
```kotlin
related_files = listOf("**/*")  // Too broad, includes irrelevant files
```

#### 2. Chain Tools Effectively

**Good workflow**:

1. SocraticDialogue → Explore the problem space
2. MultiPerspectiveAnalysis → Evaluate from different angles
3. DecompositionSynthesis → Break down and solve
4. MetaCognitiveReflection → Validate the solution

#### 3. Use Task Dependencies

```kotlin
task_dependencies = listOf("exploration_task", "analysis_task")
```

This allows tools to reference previous results and build on prior work.

#### 4. Configure Appropriately

**Match tool to problem complexity**:

- Simple problems: ChainOfThought, AbstractionLadder
- Complex problems: DecompositionSynthesis, ConstraintSatisfaction
- Validation: MetaCognitiveReflection, CounterfactualAnalysis

#### 5. Iterate and Refine

Don't expect perfect results on first try:

1. Run tool with initial configuration
2. Review results
3. Adjust parameters based on output
4. Run again with refined configuration

### Common Patterns

#### Pattern 1: Decision Making

```kotlin
// Step 1: Explore options
MultiPerspectiveAnalysis(
  analysis_subject = "Choose between React and Vue",
  perspectives = listOf("technical", "business", "team")
      +
)

// Step 2: Evaluate constraints
ConstraintSatisfaction(
  problem_description = "Select framework",
  hard_constraints = listOf("TypeScript support", "Active maintenance"),
  soft_constraints = mapOf("Learning curve" to 0.8, "Ecosystem" to 0.7)
      +
)

// Step 3: Validate decision
MetaCognitiveReflection(
  subject_task_id = "constraint_satisfaction_task",
  reflection_aspects = listOf("assumptions", "alternatives", "confidence")
      +
)
+```

#### Pattern 2: Problem Solving

```kotlin
// Step 1: Understand the problem
SocraticDialogue(
  initial_question = "What is the root cause of the performance issue?",
  max_depth = 5
      +
)

// Step 2: Identify causes
CausalInference(
  observed_effect = "API response time increased 300%",
  potential_causes = listOf("Database", "Network", "Code changes")
      +
)

// Step 3: Explore solutions
DecompositionSynthesis(
  complex_problem = "Optimize API performance",
  decomposition_strategy = "functional"
      +
)
+```

#### Pattern 3: Code Analysis

```kotlin
// Step 1: Find patterns
AbstractionLadder(
  concrete_concept = "Current authentication implementation",
  direction = "up",
  identify_patterns = true
      +
)

// Step 2: Explore alternatives
AnalogicalReasoning(
  source_domain = "physical security systems",
  target_problem = "Improve authentication security"
      +
)

// Step 3: Validate approach
CounterfactualAnalysis(
  actual_scenario = "Current authentication approach",
  counterfactuals = listOf("OAuth 2.0", "JWT", "Session-based")
      +
)
+```

---

## Advanced Usage

### Custom Workflows

Create custom task orchestration workflows that combine multiple reasoning tools :

```kotlin
val workflow = TaskOrchestrator(config).apply {
  // Phase 1: Exploration
  addTask(
    SocraticDialogueTask(
      config, SocraticDialogueTaskExecutionConfigData(
        initial_question = "What are the key challenges in our system?",
        max_depth = 5
      )
    )
  )

  // Phase 2: Analysis
  addTask(
    MultiPerspectiveAnalysisTask(
      config, MultiPerspectiveAnalysisTaskExecutionConfigData(
        analysis_subject = "System architecture challenges",
        perspectives = listOf("technical", "business", "operations"),
        task_dependencies = listOf("socratic_dialogue_task")
      )
    )
  )

  // Phase 3: Solution Design
  addTask(
    DecompositionSynthesisTask(
      config, DecompositionSynthesisTaskExecutionConfigData(
        complex_problem = "Address identified challenges",
        decomposition_strategy = "functional",
        task_dependencies = listOf("multi_perspective_task")
      )
    )
  )

  // Phase 4: Validation
  addTask(
    MetaCognitiveReflectionTask(
      config, MetaCognitiveReflectionTaskExecutionConfigData(
        subject_task_id = "decomposition_synthesis_task",
        reflection_aspects = listOf("assumptions", "completeness", "alternatives")
      )
    )
  )
  +
}

workflow.execute()
```

### Combining with Other Tools

Reasoning tools work well with other Cognotik tools:

```kotlin
// Use reasoning tools to guide code generation
val analysis = MultiPerspectiveAnalysisTask(...)
val codeGen = CodeGenerationTask(
  requirements = analysis.result,
  task_dependencies = listOf(analysis.taskId)
      +
)

// Use reasoning tools for code review
val review = CodeReviewTask(...)
val reflection = MetaCognitiveReflectionTask(
  subject_task_id = review.taskId
      +
)
+```

### Performance Optimization

**Reduce token usage**:
```kotlin
// Limit context files
related_files = listOf("src/main/kotlin/specific/*.kt")  // Not "**/*.kt"

// Reduce reasoning depth
max_depth = 3  // Instead of 7

// Disable optional features when not needed
synthesize = false
validate_steps = false
```

**Parallel execution**:

```kotlin
// Run independent analyses in parallel
val perspectives = listOf("technical", "business", "security")
val tasks = perspectives.map { perspective ->
  async {
    SinglePerspectiveAnalysis(
      analysis_subject = subject,
      perspective = perspective
    ).execute()
  }
  +
}
+
val results = tasks.awaitAll()
+```

---

## Troubleshooting

### Common Issues

#### Issue: "Configuration Error: No [parameter] specified"

**Cause**: Required parameter is missing or null

**Solution**:
```kotlin
// ❌ Wrong
analysis_subject = null

// ✅ Correct
analysis_subject = "Specific problem to analyze"
```

#### Issue: Tool produces generic or unhelpful output

**Cause**: Insufficient context or too broad scope

**Solution**:

```kotlin
// ❌ Too broad
analysis_subject = "Improve the system"

// ✅ Specific
analysis_subject = "Reduce API response time for user profile endpoint"
related_files = listOf("src/api/UserProfileController.kt", "docs/performance-requirements.md")
```

#### Issue: Tool times out or runs too long

**Cause**: Too much context or too deep reasoning

**Solution**:

```kotlin
// Reduce scope
max_depth = 3  // Instead of 7
related_files = listOf("src/specific/*.kt")  // Not "**/*"

// Disable expensive features
validate_steps = false
build_causal_graph = false
```

#### Issue: Results don't reference previous tasks

**Cause**: Missing task dependencies

**Solution**:

```kotlin
task_dependencies = listOf("previous_task_id")
```

#### Issue: Synthesis produces conflicting recommendations

**Cause**: Perspectives have fundamental disagreements

**Solution**: This is actually valuable! The conflicts reveal important trade-offs.
Review the synthesis section for how to resolve tensions.

### Debugging Tips

1. **Check logs**: Review task execution logs for errors
2. **Validate config**: Ensure all required parameters are set
3. **Start simple**: Begin with minimal configuration, add complexity gradually
4. **Review context**: Verify related_files contain relevant information
5. **Check dependencies**: Ensure task_dependencies reference valid task IDs

### Getting Help

If you encounter issues:

1. Check this documentation for similar use cases
2. Review the tool's `promptSegment()` for configuration guidance
3. Examine example configurations in the tool's companion object
4. Enable verbose logging to see detailed execution flow
5. Consult the Cognotik community or support channels

---

## Appendix

### Tool Selection Matrix

| Your Goal                   | Recommended Tool         | Alternative              |
|-----------------------------|--------------------------|--------------------------|
| Understand a concept deeply | SocraticDialogue         | ChainOfThought           |
| Find root cause of issue    | CausalInference          | CounterfactualAnalysis   |
| Make a decision             | ConstraintSatisfaction   | MultiPerspectiveAnalysis |
| Solve complex problem       | DecompositionSynthesis   | ChainOfThought           |
| Validate a solution         | MetaCognitiveReflection  | CounterfactualAnalysis   |
| Find patterns in code       | AbstractionLadder        | AnalogicalReasoning      |
| Explore alternatives        | CounterfactualAnalysis   | AnalogicalReasoning      |
| Get multiple viewpoints     | MultiPerspectiveAnalysis | SocraticDialogue         |
| Creative problem-solving    | AnalogicalReasoning      | SocraticDialogue         |
| Step-by-step reasoning      | ChainOfThought           | DecompositionSynthesis   |

### Configuration Quick Reference

#### Common Parameters

```kotlin
// All tasks support these
task_description: String?  // Brief description
task_dependencies: List<String>?  // IDs of prerequisite tasks
state: TaskState?  // Pending, Running, Completed, Failed
related_files: List<String>?  // Glob patterns for context files
```

#### Tool-Specific Parameters

**AbstractionLadder**:

- `concrete_concept`: String (required)
- `direction`: "up" | "down" | "both"
- `levels`: Int (1-5)
- `identify_patterns`: Boolean

**AnalogicalReasoning**:

- `source_domain`: String (required)
- `target_problem`: String (required)
- `num_analogies`: Int (1-5)
- `validate_mappings`: Boolean

**CausalInference**:

- `observed_effect`: String (required)
- `potential_causes`: List<String> (required)
- `build_causal_graph`: Boolean
- `identify_confounders`: Boolean
- `evidence_sources`: List<String>

**ChainOfThought**:

- `problem_statement`: String (required)
- `reasoning_depth`: Int? (null = auto)
- `validate_steps`: Boolean

**ConstraintSatisfaction**:

- `problem_description`: String (required)
- `hard_constraints`: List<String> (required)
- `soft_constraints`: Map<String, Double> (required)
- `search_strategy`: "backtracking" | "forward" | "local"
- `max_iterations`: Int

**CounterfactualAnalysis**:

- `actual_scenario`: String (required)
- `counterfactuals`: List<String> (required)
- `compare_outcomes`: Boolean
- `control_factors`: List<String>

**DecompositionSynthesis**:

- `complex_problem`: String (required)
- `decomposition_strategy`: "functional" | "temporal" | "spatial" | "hierarchical"
- `max_depth`: Int (1-5)
- `synthesize_solution`: Boolean
- `validate_coherence`: Boolean

**MetaCognitiveReflection**:

- `subject_task_id`: String (required)
- `reflection_aspects`: List<String> (required)
- `suggest_improvements`: Boolean
- `identify_gaps`: Boolean
- `evaluate_confidence`: Boolean

**MultiPerspectiveAnalysis**:

- `analysis_subject`: String (required)
- `perspectives`: List<String> (required)
- `synthesize`: Boolean
- `consensus_threshold`: Double (0.0-1.0)

**SocraticDialogue**:

- `initial_question`: String (required)
- `max_depth`: Int (1-20)
- `challenge_assumptions`: Boolean
- `domain_constraints`: List<String>

### Glossary

**Abstraction Ladder**: A conceptual tool for moving between concrete and abstract levels of thinking

**Analogy**: A comparison between two things based on structural similarity

**Causal Inference**: The process of determining cause-and-effect relationships

**Chain of Thought**: Explicit step-by-step reasoning process

**Cognitive Bias**: Systematic pattern of deviation from rational judgment

**Confounding Variable**: A factor that influences both cause and effect, creating spurious correlation

**Constraint Satisfaction**: Finding solutions that meet all requirements and optimize preferences

**Counterfactual**: A hypothetical scenario describing what would have happened under different conditions

**Decomposition**: Breaking a complex problem into simpler subproblems

**Meta-cognition**: Thinking about thinking; awareness and understanding of one's own thought processes

**Perspective**: A particular viewpoint or framework for analyzing a problem

**Socratic Method**: Teaching/learning through asking and answering questions

**Synthesis**: Combining separate elements into a coherent whole

---

## Conclusion

The Reasoning Tools suite provides powerful capabilities for systematic problem-solving and analysis. By understanding each tool's strengths and appropriate use
cases, you can:

- Make better-informed decisions
- Solve complex problems more effectively
- Validate and improve your reasoning
- Explore problems from multiple angles
- Generate high-quality, well-reasoned solutions

Start with simple configurations, iterate based on results, and combine tools for comprehensive analysis. The key to success is matching the right tool to your
specific needs and providing rich context for analysis.

Happy reasoning! 🧠✨









# Plain Language Reasoning Prompts for General Audiences

## 1. AbstractionLadder

### Prompt 1: Social Media Addiction
"I want to understand social media addiction by starting with the concrete behavior of checking Instagram every 5 minutes. Take me up the abstraction ladder to understand the broader patterns, then back down to see other specific examples of the same underlying issue."

**Configuration hints:**
- concrete_concept: "Checking Instagram every 5 minutes"
- direction: "both"
- levels: 4
- identify_patterns: true

### Prompt 2: Climate Action
"Start with the specific action of someone choosing to bike to work instead of driving. I want to go up the abstraction ladder to understand what this represents at higher levels of thinking, and then come back down to see what other concrete actions fit the same pattern."

**Configuration hints:**
- concrete_concept: "Choosing to bike to work instead of driving"
- direction: "both"
- levels: 3
- identify_patterns: true

### Prompt 3: Political Polarization
"Begin with the concrete example of someone unfriending a family member on Facebook over political disagreements. Take me up to understand the broader social patterns this represents, then show me other specific manifestations of the same phenomenon."

**Configuration hints:**
- concrete_concept: "Unfriending family members over political disagreements on social media"
- direction: "both"
- levels: 4
- identify_patterns: true

---

## 2. AnalogicalReasoning

### Prompt 1: Education Reform
"Use the way Netflix recommends shows based on viewing history as an analogy to help me think about how we could personalize education for students. Find me 3 different analogies from the entertainment industry that could inspire new approaches to teaching."

**Configuration hints:**
- source_domain: "Netflix recommendation algorithms and personalized entertainment"
- target_problem: "Creating personalized education systems that adapt to individual student needs"
- num_analogies: 3
- validate_mappings: true

### Prompt 2: Healthcare Access
"Think about how Uber solved the taxi problem - making rides available on-demand with transparent pricing. Use this and similar analogies to help me explore solutions for making healthcare more accessible and affordable. Give me 3 different analogies from the transportation or delivery industries."

**Configuration hints:**
- source_domain: "Uber and on-demand transportation/delivery services"
- target_problem: "Making healthcare more accessible, affordable, and convenient for everyone"
- num_analogies: 3
- validate_mappings: true

### Prompt 3: Democracy and Voting
"Use how Wikipedia is created and maintained by volunteers as an analogy for thinking about citizen participation in democracy. Find me 3 analogies from collaborative online platforms that could inspire new ways for citizens to participate in governance."

**Configuration hints:**
- source_domain: "Wikipedia and collaborative online platforms"
- target_problem: "Increasing meaningful citizen participation in democratic governance"
- num_analogies: 3
- validate_mappings: true

---

## 3. CausalInference

### Prompt 1: Rising Mental Health Issues
"Young adult depression and anxiety rates have doubled in the last decade. Help me figure out what's actually causing this. Consider these potential causes: social media use, economic uncertainty, academic pressure, reduced in-person socialization, climate anxiety, and pandemic effects. Build a causal graph and identify which factors are truly driving the increase versus which are just correlated."

**Configuration hints:**
- observed_effect: "Depression and anxiety rates in young adults doubled in the last decade"
- potential_causes: ["Social media use", "Economic uncertainty and job market stress", "Academic pressure and student debt", "Reduced face-to-face socialization", "Climate change anxiety", "COVID-19 pandemic effects"]
- build_causal_graph: true
- identify_confounders: true

### Prompt 2: Declining Birth Rates
"Birth rates are falling dramatically in developed countries. What's really causing this? Consider: women's education and career opportunities, cost of childcare and housing, changing cultural values, economic instability, environmental concerns, and access to contraception. I need to understand which are actual causes versus which are just things that happen to correlate."

**Configuration hints:**
- observed_effect: "Birth rates declining significantly in developed nations"
- potential_causes: ["Women's increased education and career opportunities", "High cost of childcare and housing", "Shifting cultural values about parenthood", "Economic instability and uncertainty", "Environmental concerns about overpopulation", "Widespread access to contraception"]
- build_causal_graph: true
- identify_confounders: true

### Prompt 3: Political Polarization
"Political polarization in America has increased dramatically. What's actually causing people to become more extreme and less willing to compromise? Consider: social media echo chambers, cable news, economic inequality, geographic sorting, loss of local news, and partisan gerrymandering. Help me distinguish true causes from things that are just symptoms or correlations."

**Configuration hints:**
- observed_effect: "Dramatic increase in political polarization and unwillingness to compromise"
- potential_causes: ["Social media echo chambers and algorithmic filtering", "Partisan cable news networks", "Growing economic inequality", "Geographic sorting (liberals and conservatives living in separate areas)", "Decline of local journalism", "Partisan gerrymandering"]
- build_causal_graph: true
- identify_confounders: true

---

## 4. ChainOfThought

### Prompt 1: Universal Basic Income
"Walk me through the reasoning step-by-step: Would implementing a Universal Basic Income of $1,000/month for all adults improve society overall? Consider economic effects, work incentives, poverty reduction, inflation, funding mechanisms, and social impacts. Validate each step of reasoning before moving to the next."

**Configuration hints:**
- problem_statement: "Would implementing Universal Basic Income of $1,000/month for all adults improve society overall? Consider economic effects, work incentives, poverty reduction, inflation, funding mechanisms, and social impacts."
- reasoning_depth: null
- validate_steps: true

### Prompt 2: Artificial Intelligence Regulation
"Think through this step-by-step: Should governments heavily regulate AI development now, or wait until we better understand the technology? Consider innovation speed, safety risks, competitive dynamics between nations, unintended consequences of regulation, and the difficulty of regulating something we don't fully understand yet. Validate your reasoning at each step."

**Configuration hints:**
- problem_statement: "Should governments implement heavy regulation of AI development now, or wait until we better understand the technology? Consider innovation vs. safety, international competition, unintended consequences, and the challenge of regulating emerging technology."
- reasoning_depth: null
- validate_steps: true

### Prompt 3: College Education Value
"Reason through this carefully: Is a traditional 4-year college degree still worth the cost for most people? Consider: student debt levels ($30k-100k+), opportunity cost of 4 years, changing job market, alternative education paths, signaling value of degrees, and lifetime earnings differences. Validate each reasoning step."

**Configuration hints:**
- problem_statement: "Is a traditional 4-year college degree still worth the cost for most people? Consider student debt ($30k-100k+), opportunity cost, changing job market, alternative education, signaling value, and lifetime earnings."
- reasoning_depth: null
- validate_steps: true

---

## 5. ConstraintSatisfaction

### Prompt 1: Career Change Decision
"Help me decide on a career change. I must: keep income above $60k/year, stay in my current city, work no more than 45 hours/week, and start within 6 months. I'd prefer to: maximize work-life balance (weight: 0.9), do meaningful work (weight: 0.85), have growth potential (weight: 0.8), and use my existing skills (weight: 0.7). Find the best career path that satisfies these constraints."

**Configuration hints:**
- problem_description: "Choosing a new career path"
- hard_constraints: ["Minimum income $60,000/year", "Must stay in current city", "Maximum 45 hours/week", "Can start within 6 months"]
- soft_constraints: {"Maximize work-life balance": 0.9, "Meaningful/purposeful work": 0.85, "Strong growth potential": 0.8, "Leverage existing skills": 0.7}
- search_strategy: "backtracking"

### Prompt 2: Retirement Location
"I'm choosing where to retire. Must have: affordable cost of living (under $3k/month), good healthcare access, safe neighborhood, and mild climate (no harsh winters). I'd prefer to: be near family (weight: 0.9), have cultural activities (weight: 0.7), be in a walkable area (weight: 0.8), and have an active senior community (weight: 0.75). Find the best location."

**Configuration hints:**
- problem_description: "Selecting retirement location"
- hard_constraints: ["Cost of living under $3,000/month", "Access to quality healthcare", "Low crime rate", "Mild climate without harsh winters"]
- soft_constraints: {"Proximity to family": 0.9, "Cultural activities and amenities": 0.7, "Walkable neighborhood": 0.8, "Active senior community": 0.75}
- search_strategy: "backtracking"

### Prompt 3: Family Vacation Planning
"Plan our family vacation. Must: fit $4,000 budget, accommodate 2 adults and 3 kids (ages 5-12), be reachable in one day of travel, and happen in July. We'd prefer to: maximize kid-friendly activities (weight: 0.9), have educational value (weight: 0.7), include outdoor activities (weight: 0.8), and have some adult relaxation time (weight: 0.75). Find the optimal destination and plan."

**Configuration hints:**
- problem_description: "Planning family vacation"
- hard_constraints: ["Total budget $4,000", "Suitable for ages 5-12", "Reachable in one day of travel", "Available in July"]
- soft_constraints: {"Kid-friendly activities": 0.9, "Educational value": 0.7, "Outdoor activities": 0.8, "Adult relaxation opportunities": 0.75}
- search_strategy: "forward"

---

## 6. CounterfactualAnalysis

### Prompt 1: Social Media Impact
"We've had widespread social media for 15 years now, and we see increased anxiety, polarization, and attention problems. Analyze what would have happened if: 1) Social media never became popular, 2) Social media remained chronological without algorithms, 3) Social media was age-restricted to 16+, 4) Social media companies were held liable for content. Keep constant: same technology level, same internet access, same smartphone adoption."

**Configuration hints:**
- actual_scenario: "15 years of widespread social media with algorithmic feeds, available to all ages, with platform liability protections. Observable effects: increased anxiety, political polarization, and attention problems."
- counterfactuals: ["Social media never became popular", "Social media remained chronological without algorithmic curation", "Social media was age-restricted to 16+", "Social media companies held liable for harmful content"]
- compare_outcomes: true
- control_factors: ["Same technology level", "Same internet access", "Same smartphone adoption", "Same time period"]

### Prompt 2: College Debt Crisis
"We have $1.7 trillion in student loan debt affecting 45 million Americans. What would have happened if: 1) College remained affordable like in the 1970s, 2) We had free community college for everyone, 3) Income-share agreements replaced loans, 4) Trade schools were promoted equally to universities. Keep constant: same number of people seeking higher education, same job market, same technology changes."

**Configuration hints:**
- actual_scenario: "$1.7 trillion in student debt affecting 45 million Americans, with college costs rising 8x faster than wages since 1980."
- counterfactuals: ["College costs remained at 1970s levels relative to income", "Free community college for all students", "Income-share agreements instead of traditional loans", "Trade schools promoted equally to universities"]
- compare_outcomes: true
- control_factors: ["Same number seeking higher education", "Same job market evolution", "Same technological changes"]

### Prompt 3: Remote Work Revolution
"COVID-19 forced a massive shift to remote work. Now many companies are mandating return to office. What would have happened if: 1) Remote work never became widespread, 2) Companies embraced permanent remote-first policies, 3) We adopted hybrid 2-3 days in office, 4) Different industries made different choices. Keep constant: same technology capabilities, same housing costs, same family situations."

**Configuration hints:**
- actual_scenario: "COVID-19 forced remote work experiment. Now many companies mandating return to office, creating tension and turnover."
- counterfactuals: ["Remote work never became widespread (no pandemic)", "Companies embraced permanent remote-first policies", "Industry standard became hybrid 2-3 days in office", "Different industries made different choices based on work nature"]
- compare_outcomes: true
- control_factors: ["Same technology capabilities", "Same housing costs", "Same family situations", "Same worker preferences"]

---

## 7. DecompositionSynthesis

### Prompt 1: Solving Homelessness
"Break down the complex problem of solving homelessness in a major city. Consider all the interconnected issues: mental health, addiction, affordable housing shortage, job access, healthcare, criminal records, family breakdown, and systemic poverty. Decompose this into manageable subproblems, solve each one, then synthesize a comprehensive solution. Use functional decomposition."

**Configuration hints:**
- complex_problem: "Solving homelessness in a major city, addressing mental health, addiction, affordable housing, employment, healthcare access, criminal records, family breakdown, and systemic poverty"
- decomposition_strategy: "functional"
- max_depth: 3
- synthesize_solution: true
- validate_coherence: true

### Prompt 2: Reducing Carbon Emissions
"Break down the massive challenge of reducing global carbon emissions by 50% in 10 years. This involves transportation, energy production, agriculture, manufacturing, buildings, and changing consumer behavior across billions of people. Decompose this into solvable pieces, address each one, then synthesize a coherent global strategy. Use hierarchical decomposition."

**Configuration hints:**
- complex_problem: "Reducing global carbon emissions by 50% within 10 years, addressing transportation, energy production, agriculture, manufacturing, buildings, and consumer behavior worldwide"
- decomposition_strategy: "hierarchical"
- max_depth: 4
- synthesize_solution: true
- validate_coherence: true

### Prompt 3: Reforming Education System
"Break down the challenge of reforming the K-12 education system to prepare students for the 21st century. This involves curriculum design, teacher training, technology integration, assessment methods, equity issues, funding, parental involvement, and adapting to AI. Decompose into manageable parts, solve each, then synthesize a complete reform plan. Use temporal decomposition."

**Configuration hints:**
- complex_problem: "Reforming K-12 education for the 21st century: curriculum, teacher training, technology, assessment, equity, funding, parental involvement, and AI adaptation"
- decomposition_strategy: "temporal"
- max_depth: 3
- synthesize_solution: true
- validate_coherence: true

---

## 8. MetaCognitiveReflection

### Prompt 1: Critique Climate Change Skepticism
"Reflect on the reasoning behind climate change skepticism. Examine the assumptions, identify cognitive biases, explore what alternative evidence might exist, assess the confidence levels, check for logical fallacies, and identify gaps in the reasoning. Suggest improvements to the thinking process."

**Configuration hints:**
- subject_task_id: "climate_skepticism_reasoning"
- reflection_aspects: ["assumptions", "biases", "alternatives", "confidence", "logic", "completeness"]
- suggest_improvements: true
- identify_gaps: true
- evaluate_confidence: true

### Prompt 2: Critique Meritocracy Belief
"Reflect on the belief that 'America is a meritocracy where anyone can succeed through hard work.' Examine underlying assumptions, identify biases in this thinking, explore alternative perspectives, assess how confident we should be in this claim, check the logic, and identify what's missing from this analysis."

**Configuration hints:**
- subject_task_id: "meritocracy_belief_analysis"
- reflection_aspects: ["assumptions", "biases", "alternatives", "confidence", "logic", "completeness"]
- suggest_improvements: true
- identify_gaps: true
- evaluate_confidence: true

### Prompt 3: Critique Free Speech Absolutism
"Reflect on the position that 'free speech should be absolute with no restrictions.' Examine the assumptions behind this view, identify cognitive biases, explore alternative frameworks, assess confidence levels, check for logical consistency, and identify gaps in the reasoning."

**Configuration hints:**
- subject_task_id: "free_speech_absolutism_position"
- reflection_aspects: ["assumptions", "biases", "alternatives", "confidence", "logic", "completeness"]
- suggest_improvements: true
- identify_gaps: true
- evaluate_confidence: true

---

## 9. MultiPerspectiveAnalysis

### Prompt 1: Legalizing Marijuana
"Analyze marijuana legalization from multiple perspectives: public health, criminal justice, personal freedom, economic impact, social equity, and youth protection. Synthesize these viewpoints into a unified recommendation. Use a consensus threshold of 0.7."

**Configuration hints:**
- analysis_subject: "Legalizing marijuana nationwide"
- perspectives: ["public health and addiction", "criminal justice and incarceration", "personal freedom and liberty", "economic impact and tax revenue", "social equity and racial justice", "youth protection and access"]
- synthesize: true
- consensus_threshold: 0.7

### Prompt 2: Immigration Policy
"Analyze immigration policy from these perspectives: economic impact on jobs and wages, humanitarian obligations, national security, cultural integration, fiscal costs and benefits, and labor market needs. Synthesize into a coherent policy recommendation. Use consensus threshold of 0.65."

**Configuration hints:**
- analysis_subject: "Comprehensive immigration reform policy"
- perspectives: ["economic impact on jobs and wages", "humanitarian obligations and asylum", "national security concerns", "cultural integration and social cohesion", "fiscal costs and tax contributions", "labor market needs and shortages"]
- synthesize: true
- consensus_threshold: 0.65

### Prompt 3: Universal Healthcare
"Analyze universal healthcare from these angles: healthcare outcomes and quality, economic costs and efficiency, personal choice and freedom, business competitiveness, innovation in medicine, and equity of access. Synthesize these perspectives into a unified conclusion. Use consensus threshold of 0.7."

**Configuration hints:**
- analysis_subject: "Implementing universal healthcare system"
- perspectives: ["healthcare outcomes and quality of care", "economic costs and efficiency", "personal choice and freedom", "business competitiveness and labor mobility", "medical innovation and research", "equity and access to care"]
- synthesize: true
- consensus_threshold: 0.7

---

## 10. SocraticDialogue

### Prompt 1: Nature of Happiness
"Explore through Socratic questioning: What is happiness, and can we choose to be happy? Start with the question 'Is happiness something we find or something we create?' Challenge assumptions about happiness being dependent on external circumstances versus internal mindset. Go 6 exchanges deep."

**Configuration hints:**
- initial_question: "Is happiness something we find or something we create?"
- max_depth: 6
- challenge_assumptions: true
- domain_constraints: ["psychology", "philosophy", "well-being"]

### Prompt 2: Justice and Fairness
"Use Socratic dialogue to explore: What makes something 'fair' or 'just'? Start with 'Should everyone get equal outcomes, or equal opportunities?' Challenge assumptions about equality, merit, need, and desert. Go 7 exchanges deep."

**Configuration hints:**
- initial_question: "Should everyone get equal outcomes, or equal opportunities?"
- max_depth: 7
- challenge_assumptions: true
- domain_constraints: ["ethics", "political philosophy", "social justice"]

### Prompt 3: Free Will
"Explore through Socratic questioning: Do we have free will, or are our choices determined by factors beyond our control? Start with 'If our brains are physical systems following natural laws, how can we have free will?' Challenge assumptions about consciousness, choice, and responsibility. Go 6 exchanges deep."

**Configuration hints:**
- initial_question: "If our brains are physical systems following natural laws, how can we have free will?"
- max_depth: 6
- challenge_assumptions: true
- domain_constraints: ["philosophy", "neuroscience", "ethics"]

---

## Usage Notes

These prompts are designed to:

1. **Be accessible** - No technical jargon or specialized knowledge required
2. **Be controversial** - Touch on real debates people care about
3. **Be standalone** - Require only general knowledge, no fictional documents
4. **Be interesting** - Explore questions that matter to people's lives
5. **Be parseable** - Written in natural language that a planner agent can convert to configuration

Each prompt can be spoken naturally to an AI assistant, which would then parse it into the appropriate tool configuration. The prompts cover topics like:

- Social issues (polarization, mental health, education)
- Economic questions (UBI, student debt, healthcare)
- Personal decisions (career, retirement, family)
- Philosophical questions (happiness, justice, free will)
- Policy debates (immigration, climate, legalization)

These are designed to produce genuinely interesting, thought-provoking analyses that demonstrate the power of structured reasoning tools for everyday questions people actually care about.
