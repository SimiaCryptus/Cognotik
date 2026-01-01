# Social Reasoning Package Guide

## Overview

The **Social Reasoning Package** is a comprehensive suite of AI-powered tools designed to analyze complex problems through multiple social, ethical, and strategic lenses. These tools help developers, researchers, and decision-makers navigate difficult choices by simulating diverse perspectives, conducting ethical analyses, and exploring strategic interactions.

## Package Contents

The social reasoning package includes seven specialized tasks:

1. **DialecticalReasoningTask** - Resolve contradictions through thesis-antithesis-synthesis
2. **EthicalReasoningTask** - Analyze dilemmas through multiple ethical frameworks
3. **GameTheoryTask** - Model strategic interactions and find optimal strategies
4. **LLMExperimentTask** - Conduct controlled experiments on LLM behavior
5. **LLMPollSimulationTask** - Simulate surveys with diverse AI personas
6. **MultiPerspectiveAnalysisTask** - Examine topics from multiple viewpoints
7. **PersuasiveEssayTask** - Generate compelling, well-structured arguments
8. **PoliticalOptimizationTask** - Optimize messaging across political perspectives

---

## When to Use Each Tool

### Decision-Making & Analysis

| Scenario | Recommended Tool | Why |
|----------|-----------------|-----|
| Resolving conflicting requirements | **DialecticalReasoningTask** | Synthesizes opposing positions into higher-level solutions |
| Evaluating ethical implications | **EthicalReasoningTask** | Applies multiple ethical frameworks systematically |
| Architectural decisions | **MultiPerspectiveAnalysisTask** | Examines from technical, business, operational angles |
| Strategic planning | **GameTheoryTask** | Models competitive/cooperative dynamics |

### Research & Testing

| Scenario | Recommended Tool | Why |
|----------|-----------------|-----|
| Testing LLM behavior | **LLMExperimentTask** | Controlled experiments with statistical analysis |
| Survey design validation | **LLMPollSimulationTask** | Simulates diverse respondents before real deployment |
| Bias detection | **LLMExperimentTask** or **LLMPollSimulationTask** | Identifies patterns and biases in responses |

### Communication & Persuasion

| Scenario | Recommended Tool | Why |
|----------|-----------------|-----|
| Writing persuasive content | **PersuasiveEssayTask** | Generates structured arguments with evidence |
| Bipartisan messaging | **PoliticalOptimizationTask** | Finds language that builds consensus |
| Understanding polarization | **PoliticalOptimizationTask** | Identifies divisive language patterns |

---

## Tool Deep Dives

### 1. DialecticalReasoningTask

**Purpose:** Resolve contradictions by finding higher-level syntheses that transcend binary choices.

**Best For:**
- Architectural debates (monolith vs. microservices)
- Design philosophy conflicts (security vs. usability)
- Strategic disagreements
- Requirements that seem mutually exclusive

**Key Features:**
- Iterative synthesis (1-5 levels)
- Preserves strengths from both positions
- Identifies remaining tensions
- Generates actionable recommendations

**Quick Start:**
```json
{
  "thesis": "We need strong security with complex authentication",
  "antithesis": "We need simple UX with minimal friction",
  "context": "Healthcare app with sensitive patient data",
  "synthesis_levels": 3,
  "preserve_strengths": true
}
```

**Output:** Progressive synthesis at each level, culminating in integrated solution (e.g., risk-based authentication that balances both concerns).

**Pro Tips:**
- Use 3 levels for most decisions
- Provide rich context for better synthesis
- Review all synthesis levels, not just the final one
- Use for complex problems, not simple binary choices

---

### 2. EthicalReasoningTask

**Purpose:** Analyze ethical dilemmas through multiple established ethical frameworks.

**Best For:**
- AI safety and alignment decisions
- Product feature ethical implications
- Policy evaluation
- Corporate governance decisions
- Legal and compliance matters

**Key Features:**
- Five ethical frameworks (utilitarianism, deontology, virtue ethics, care ethics, rights-based)
- Stakeholder impact analysis
- Identifies agreements and conflicts between frameworks
- Synthesizes into unified recommendation

**Quick Start:**
```json
{
  "ethical_dilemma": "Deploy AI that improves efficiency 40% but may displace 15% of staff",
  "stakeholders": ["current_staff", "customers", "shareholders", "communities"],
  "ethical_frameworks": ["utilitarianism", "deontology", "virtue_ethics"],
  "context": "Company committed to employee welfare, competitive industry"
}
```

**Output:** Framework-specific analyses, synthesis showing trade-offs, final recommendation with ethical justification.

**Pro Tips:**
- Be specific about the dilemma
- Include all affected stakeholders
- Choose frameworks relevant to your context
- Use input files to provide research context
- Should complement, not replace, human judgment

---

### 3. GameTheoryTask

**Purpose:** Analyze strategic interactions to identify optimal strategies and equilibria.

**Best For:**
- Market competition analysis
- Negotiation scenarios
- Multi-party decision-making
- Cooperative vs. competitive dynamics
- Repeated interaction modeling

**Key Features:**
- Payoff matrix construction
- Nash equilibrium identification
- Dominant strategy analysis
- Pareto optimality assessment
- Repeated game modeling
- Player-specific recommendations

**Quick Start:**
```json
{
  "game_scenario": "Two companies competing on price. High prices yield $100M if both choose high, $50M if one high/one low, $20M if both low.",
  "players": ["Company A", "Company B"],
  "game_type": "non-cooperative",
  "repeated_game_analysis": true,
  "iterations": 20
}
```

**Output:** Game structure analysis, payoff matrices, equilibria, strategic recommendations for each player.

**Pro Tips:**
- Define clear scenarios with specific payoffs
- Use realistic incentive structures
- Enable repeated game analysis for multi-round scenarios
- Disable unnecessary analyses for faster results

---

### 4. LLMExperimentTask

**Purpose:** Conduct controlled experiments on LLM behavior with statistical analysis.

**Best For:**
- Testing prompt variations
- Temperature effect studies
- Consistency testing
- Bias detection research
- Prompt engineering optimization

**Key Features:**
- Prompt template with variable substitution
- Temperature variation testing
- Multiple repetitions for statistical validity
- Custom metrics (length, sentiment, clarity, etc.)
- Statistical significance testing (t-tests, effect sizes)
- Response diversity measurement

**Quick Start:**
```json
{
  "prompt_templates": ["Explain {topic} to a beginner"],
  "prompt_variables": {"topic": ["AI", "blockchain", "quantum computing"]},
  "temperature_values": [0.0, 0.5, 1.0],
  "repetitions": 5,
  "metrics": ["response_length", "clarity", "accuracy"]
}
```

**Output:** Descriptive statistics, pairwise comparisons, correlation analysis, effect sizes, AI-generated insights.

**Pro Tips:**
- Start with 3-5 repetitions for validity
- Use 2-4 temperature values
- Mix objective (length) and subjective (clarity) metrics
- Avoid too many conditions (exponential growth)

---

### 5. LLMPollSimulationTask

**Purpose:** Simulate surveys with diverse AI personas to test instruments and explore response patterns.

**Best For:**
- Survey design validation
- Testing question wording
- Exploring demographic response patterns
- Bias detection in surveys
- Pre-deployment testing

**Key Features:**
- Multiple question types (Likert, multiple choice, open-ended, ranking, etc.)
- Profile-based respondent generation
- Demographic tracking and analysis
- Cross-tabulation by demographics
- Sentiment analysis of open-ended responses
- Bias detection (central tendency, acquiescence, primacy/recency)

**Quick Start:**
```json
{
  "questions": [
    {
      "id": "satisfaction",
      "text": "How satisfied are you with our product?",
      "type": "LIKERT_SCALE",
      "min": 1,
      "max": 5
    }
  ],
  "respondent_profiles": [
    {
      "id": "power_users",
      "description": "Frequent users, tech-savvy",
      "characteristics": ["Early adopters", "Feature-focused"]
    }
  ],
  "respondents_per_profile": 50
}
```

**Output:** Descriptive statistics, cross-tabulation, sentiment analysis, bias detection, AI-generated insights.

**Pro Tips:**
- Create realistic, diverse personas
- Use 30-50 respondents per profile minimum
- Test conditional logic carefully
- Review bias detection for survey improvements

---

### 6. MultiPerspectiveAnalysisTask

**Purpose:** Examine complex subjects from multiple viewpoints and synthesize findings.

**Best For:**
- Architectural decision reviews
- Code reviews from multiple angles
- Feature evaluation
- Risk assessment
- Strategic planning

**Key Features:**
- Configurable perspectives (technical, business, ethical, UX, etc.)
- Independent perspective analyses
- Identifies agreements and conflicts
- Consensus threshold assessment
- Unified synthesis with recommendations

**Quick Start:**
```json
{
  "analysis_subject": "Migrate to microservices architecture",
  "perspectives": ["technical", "business", "operational", "security"],
  "input_files": ["src/main/**/*.kt", "docs/architecture.md"],
  "synthesize": true,
  "consensus_threshold": 0.75
}
```

**Output:** Individual perspective analyses with confidence ratings, synthesis showing agreements/conflicts, unified recommendations.

**Pro Tips:**
- Choose 3-5 relevant perspectives
- Be specific about the subject
- Provide context through input files
- Set consensus threshold based on decision criticality (0.7-0.8 for most cases)

---

### 7. PersuasiveEssayTask

**Purpose:** Generate compelling, well-structured persuasive essays with evidence and rhetorical strategies.

**Best For:**
- Business proposals
- Policy advocacy
- Opinion pieces
- Academic arguments
- Marketing content

**Key Features:**
- Structured argumentation (intro, body, counterarguments, conclusion)
- Rhetorical devices (ethos, pathos, logos)
- Evidence integration (statistics, quotes, examples)
- Counterargument handling
- Multiple revision passes
- Image generation for arguments

**Quick Start:**
```json
{
  "thesis": "Remote work should be the default for knowledge workers",
  "target_audience": "business leaders",
  "tone": "formal",
  "target_word_count": 1500,
  "num_arguments": 3,
  "include_counterarguments": true,
  "revision_passes": 2
}
```

**Output:** Complete essay with introduction, arguments, counterarguments, conclusion, plus visualizations.

**Pro Tips:**
- Make thesis specific and debatable
- Match tone to audience
- Provide research via input files
- Use 2-3 revision passes for quality
- Enable counterarguments for credibility

---

### 8. PoliticalOptimizationTask

**Purpose:** Optimize text to maximize consensus or identify divisive language across political perspectives.

**Best For:**
- Bipartisan messaging
- Understanding polarization
- Message testing
- Political research
- Inclusive communications

**Key Features:**
- Evolutionary text optimization
- Multiple political perspective evaluation
- Consensus vs. divisiveness analysis
- Mutation strategies (rephrase, soften, bridge, polarize)
- Statistical analysis of perspective agreement
- Wedge issue identification

**Quick Start:**
```json
{
  "initial_text": "We need to invest in renewable energy to address climate change while creating jobs",
  "optimization_goal": "Maximize consensus on climate policy",
  "perspectives": ["progressive", "conservative", "libertarian", "centrist"],
  "evaluation_criteria": ["clarity", "persuasiveness", "fairness"],
  "consensus_mode": "maximize",
  "num_generations": 5
}
```

**Output:** Generation-by-generation evolution, most unifying/divisive variants, consensus analysis, perspective-specific feedback.

**Pro Tips:**
- Use 3-5 relevant perspectives
- Define specific evaluation criteria
- Use "maximize" for consensus-building
- Use "minimize" to identify divisive language
- Run 5-10 generations for good optimization

---

## Common Workflows

### Workflow 1: Comprehensive Decision Analysis

**Scenario:** Major architectural decision with ethical implications

```
1. MultiPerspectiveAnalysisTask
   → Analyze from technical, business, operational perspectives

2. EthicalReasoningTask
   → Evaluate ethical implications for stakeholders

3. DialecticalReasoningTask
   → Resolve any conflicting recommendations

4. PersuasiveEssayTask
   → Document final decision with justification
```

### Workflow 2: Survey Development & Validation

**Scenario:** Designing a customer satisfaction survey

```
1. LLMPollSimulationTask (initial)
   → Test draft questions with simulated respondents

2. Review bias detection results
   → Identify problematic questions

3. LLMPollSimulationTask (revised)
   → Test improved questions

4. Deploy to real users
```

### Workflow 3: Strategic Communication

**Scenario:** Crafting a policy announcement

```
1. MultiPerspectiveAnalysisTask
   → Understand stakeholder perspectives

2. PersuasiveEssayTask
   → Draft initial message

3. PoliticalOptimizationTask
   → Optimize for consensus across perspectives

4. LLMExperimentTask
   → Test final message variations
```

### Workflow 4: Research & Experimentation

**Scenario:** Studying LLM behavior patterns

```
1. LLMExperimentTask
   → Conduct controlled experiments

2. Analyze statistical results
   → Identify significant patterns

3. LLMPollSimulationTask
   → Validate findings with simulated surveys

4. Document insights
```

---

## Best Practices Across All Tools

### Configuration

1. **Start Simple**: Begin with minimal configuration, add complexity as needed
2. **Be Specific**: Vague inputs produce vague outputs
3. **Provide Context**: Use input files and context parameters liberally
4. **Validate First**: Check configuration validation messages before running

### Interpretation

1. **Read All Tabs**: Don't focus only on final results
2. **Check Assumptions**: Verify AI interpretations match your understanding
3. **Look for Patterns**: Consistent findings across tools are more reliable
4. **Consider Limitations**: AI analysis complements, doesn't replace, human judgment

### Iteration

1. **Refine Inputs**: Use initial results to improve configuration
2. **Combine Tools**: Use multiple tools for comprehensive analysis
3. **Test Variations**: Try different parameters to explore solution space
4. **Document Findings**: Save transcripts and reports for reference

---

## Performance Considerations

### Typical Execution Times

| Tool | Configuration | Approximate Time |
|------|--------------|------------------|
| DialecticalReasoningTask | 3 synthesis levels | 2-5 minutes |
| EthicalReasoningTask | 3 frameworks | 2-4 minutes |
| GameTheoryTask | Standard analysis | 1-3 minutes |
| LLMExperimentTask | 3 temps × 3 vars × 5 reps | 3-8 minutes |
| LLMPollSimulationTask | 100 respondents × 10 questions | 2-3 minutes |
| MultiPerspectiveAnalysisTask | 4 perspectives | 2-4 minutes |
| PersuasiveEssayTask | 1500 words, 2 revisions | 2-4 minutes |
| PoliticalOptimizationTask | 5 generations, 4 perspectives | 3-6 minutes |

### Optimization Tips

1. **Reduce Iterations**: Fewer generations/repetitions = faster results
2. **Limit Perspectives**: 3-5 perspectives is usually sufficient
3. **Disable Optional Features**: Turn off unnecessary analyses
4. **Use Smaller Populations**: Reduce population sizes in experiments
5. **Parallel Execution**: Some tools support concurrent processing

---

## Troubleshooting

### Common Issues

#### "No analysis subject specified"
**Solution:** Ensure required fields are populated and not blank

#### "Validation failed"
**Solution:** Check validation error messages, adjust parameters to valid ranges

#### Results seem biased or incorrect
**Solution:**
- Provide more context via input files
- Adjust perspectives or frameworks
- Review AI limitations in documentation
- Validate with domain experts

#### Execution takes too long
**Solution:**
- Reduce number of iterations/generations
- Decrease population sizes
- Limit number of perspectives
- Simplify configuration

#### Low consensus or unexpected results
**Solution:**
- Topic may be inherently complex/divisive
- Try different perspectives or criteria
- Increase iterations for deeper analysis
- Review detailed analysis tabs for insights

---

## Integration Examples

### Example 1: Task Orchestration

```kotlin
// Define dependent tasks
val analysisTask = MultiPerspectiveAnalysisTask(...)
val ethicsTask = EthicalReasoningTask(...)
val dialecticTask = DialecticalReasoningTask(...)

// Set dependencies
ethicsTask.task_dependencies = listOf(analysisTask.id)
dialecticTask.task_dependencies = listOf(analysisTask.id, ethicsTask.id)

// Execute in order
orchestrator.execute(analysisTask)
orchestrator.execute(ethicsTask)
orchestrator.execute(dialecticTask)
```

### Example 2: Combining Results

```kotlin
// Run multiple analyses
val technicalAnalysis = MultiPerspectiveAnalysisTask(
    perspectives = listOf("technical", "performance", "security")
)

val businessAnalysis = MultiPerspectiveAnalysisTask(
    perspectives = listOf("business", "financial", "strategic")
)

// Synthesize with dialectical reasoning
val synthesis = DialecticalReasoningTask(
    thesis = technicalAnalysis.result,
    antithesis = businessAnalysis.result,
    context = "Enterprise architecture decision"
)
```

---

## Advanced Features

### File Input Support

All tools support file input with glob patterns:

```json
{
  "input_files": [
    "src/main/**/*.kt",
    "docs/**/*.md",
    "config/**/*.yml"
  ],
  "related_files": [
    "tests/**/*Test.kt",
    "README.md"
  ]
}
```

### Context Propagation

Tasks can use outputs from previous tasks:

```kotlin
val context = previousTask.result
val nextTask = SomeTask(
    additional_context = context,
    task_dependencies = listOf(previousTask.id)
)
```

### Transcript Generation

All tasks generate detailed transcripts:
- Markdown format with timestamps
- Exportable to HTML and PDF
- Accessible via web interface
- Useful for documentation and audit trails

---

## Comparison Matrix

| Feature | Dialectical | Ethical | Game Theory | LLM Experiment | Poll Simulation | Multi-Perspective | Persuasive Essay | Political Optimization |
|---------|------------|---------|-------------|----------------|-----------------|-------------------|------------------|----------------------|
| **Resolves Conflicts** | ✓✓✓ | ✓✓ | ✓ | - | - | ✓✓ | - | ✓✓ |
| **Ethical Analysis** | - | ✓✓✓ | - | - | - | ✓ | - | - |
| **Strategic Modeling** | - | - | ✓✓✓ | - | - | - | - | ✓ |
| **Experimentation** | - | - | - | ✓✓✓ | ✓✓✓ | - | - | ✓✓ |
| **Persuasion** | - | - | - | - | - | - | ✓✓✓ | ✓✓ |
| **Multiple Perspectives** | ✓ | ✓✓✓ | ✓ | - | ✓✓ | ✓✓✓ | - | ✓✓✓ |
| **Statistical Analysis** | - | - | ✓✓ | ✓✓✓ | ✓✓✓ | - | - | ✓✓ |
| **Consensus Building** | ✓✓✓ | ✓✓ | ✓ | - | - | ✓✓ | - | ✓✓✓ |

Legend: ✓✓✓ = Primary strength, ✓✓ = Strong capability, ✓ = Supported, - = Not applicable

---

## Conclusion

The Social Reasoning Package provides a comprehensive toolkit for navigating complex decisions, ethical dilemmas, and strategic challenges. By combining multiple tools and perspectives, you can:

- Make more informed decisions
- Understand diverse viewpoints
- Identify and resolve conflicts
- Test and optimize communications
- Conduct rigorous research
- Build consensus across stakeholders

**Remember:** These tools augment human judgment, they don't replace it. Always validate AI-generated insights with domain expertise and real-world testing.

---

## Quick Reference

### Decision-Making
- **Conflicting requirements?** → DialecticalReasoningTask
- **Ethical concerns?** → EthicalReasoningTask
- **Multiple viewpoints?** → MultiPerspectiveAnalysisTask
- **Strategic interactions?** → GameTheoryTask

### Research & Testing
- **Test LLM behavior?** → LLMExperimentTask
- **Validate survey?** → LLMPollSimulationTask
- **Detect bias?** → LLMExperimentTask or LLMPollSimulationTask

### Communication
- **Write persuasively?** → PersuasiveEssayTask
- **Build consensus?** → PoliticalOptimizationTask
- **Understand polarization?** → PoliticalOptimizationTask

---

**Version:** 1.0
**Last Updated:** 2024
**Package:** com.simiacryptus.cognotik.plan.tools.social


## DialecticalReasoningTask User Documentation


### Overview

The **DialecticalReasoningTask** is a sophisticated reasoning tool that resolves contradictions and conflicts by applying dialectical methodology. It takes opposing positions (thesis and antithesis) and systematically generates higher-level syntheses that transcend the opposition while preserving valuable insights from both sides.

This task is ideal for:
- Architectural debates and design decisions
- Resolving conflicting requirements
- Exploring philosophical or strategic disagreements
- Finding innovative solutions that go beyond binary choices
- Understanding complex problems from multiple perspectives

---


### Key Concepts


#### Dialectical Reasoning Process

The task follows a structured dialectical process:

1. **Thesis Analysis** - Thoroughly examines the first position
2. **Antithesis Analysis** - Thoroughly examines the opposing position
3. **Contradiction Exploration** - Identifies tensions and incompatibilities
4. **Iterative Synthesis** - Generates progressively higher-level resolutions
5. **Final Integration** - Synthesizes all insights into actionable conclusions


#### Synthesis Levels

The task can iterate through multiple synthesis levels (1-5), where each level:
- Takes the previous synthesis as a new thesis
- Identifies its limitations or opposing perspective
- Generates an even higher-level synthesis
- Progressively deepens understanding and integration

---


### Configuration Parameters


#### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| **thesis** | String | The primary position or statement to analyze (max 5000 characters) |
| **antithesis** | String | The opposing position or statement (max 5000 characters) |


#### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| **context** | String | "general domain" | Domain or context for the analysis (max 10000 characters) |
| **synthesis_levels** | Integer | 3 | Number of synthesis iterations (1-5) |
| **preserve_strengths** | Boolean | true | Whether to preserve valuable aspects from both positions |
| **input_files** | List[String] | null | File patterns (e.g., `**/*.kt`) to include as input |
| **related_files** | List[String] | null | Additional files for context and reference |

---


### Usage Examples


#### Example 1: Architectural Decision

**Scenario:** Resolving a debate between monolithic vs. microservices architecture

```
Thesis: "Monolithic architecture provides simplicity, easier deployment, 
and better performance for small to medium applications"

Antithesis: "Microservices architecture enables scalability, independent 
deployment, and technology flexibility for large systems"

Context: "Enterprise e-commerce platform with 50+ development teams"

Synthesis Levels: 3
Preserve Strengths: true
```

**Expected Output:** A synthesis suggesting a modular monolith or strategic microservices approach that combines deployment simplicity with scalability.


#### Example 2: Design Philosophy

**Scenario:** Resolving tension between security and usability

```
Thesis: "Security must be the primary concern; complex authentication 
and verification processes are necessary"

Antithesis: "User experience must be prioritized; overly complex security 
measures frustrate users and reduce adoption"

Context: "Healthcare application handling sensitive patient data"

Synthesis Levels: 4
Preserve Strengths: true
```

**Expected Output:** A synthesis proposing risk-based authentication, progressive security, and context-aware verification that balances both concerns.

---


### Output Structure

The task generates a comprehensive analysis organized into tabs:


#### Overview Tab
- High-level summary of the analysis
- Progress tracking
- Completion status and timing


#### Thesis Tab
- Detailed analysis of the thesis statement
- Core claims and assumptions
- Strengths and supporting evidence
- Limitations and blind spots


#### Antithesis Tab
- Detailed analysis of the antithesis statement
- How it challenges the thesis
- Internal logic and coherence
- Scope and applicability


#### Contradictions Tab
- Direct contradictions between positions
- Underlying tensions and incompatibilities
- Areas of partial overlap
- Root causes of opposition


#### Synthesis Tabs (L1, L2, L3, etc.)
- Progressive synthesis at each level
- Integration of insights from both sides
- New understanding provided
- Remaining tensions or limitations


#### Final Integration Tab
- Summary of the entire dialectical journey
- Key insights at each level
- How the final synthesis resolves the original contradiction
- Practical implications and recommendations

---


### Best Practices


#### 1. **Define Clear Positions**
- Make thesis and antithesis statements specific and substantive
- Avoid vague or overly broad positions
- Ensure they genuinely represent opposing viewpoints


#### 2. **Provide Adequate Context**
- Include domain-specific information
- Reference relevant constraints or requirements
- Provide background that helps the AI understand nuances


#### 3. **Choose Appropriate Synthesis Levels**
- **1-2 levels:** Quick resolution of simple conflicts
- **3 levels:** Standard analysis for most decisions
- **4-5 levels:** Deep exploration of complex philosophical issues


#### 4. **Use Related Files Strategically**
- Include relevant documentation or specifications
- Reference previous decisions or analyses
- Provide examples or case studies


#### 5. **Enable Strength Preservation**
- Set `preserve_strengths: true` when both positions have merit
- This generates more balanced, integrated solutions
- Useful for organizational alignment and buy-in

---


### Interpreting Results


#### What to Look For

1. **Genuine Integration** - Does the synthesis incorporate valid points from both sides?
2. **Higher Perspective** - Does it transcend the binary choice?
3. **Practical Applicability** - Can the synthesis be implemented?
4. **Remaining Tensions** - Are unresolved issues clearly identified?
5. **Actionable Recommendations** - Are next steps clear?


#### Red Flags

- Synthesis that simply picks one side over the other
- Vague or abstract conclusions without practical grounding
- Ignored or minimized concerns from either position
- Lack of clear implementation path

---


### Performance Considerations


#### Timing
- **Thesis/Antithesis Analysis:** ~10-30 seconds each
- **Contradictions Exploration:** ~15-30 seconds
- **Each Synthesis Level:** ~20-40 seconds
- **Final Integration:** ~15-30 seconds

**Total Time:** 2-5 minutes for a 3-level analysis


#### Output Size
- Typical analysis generates 5,000-15,000 characters
- Transcript files are automatically generated in Markdown format
- Can be exported to HTML or PDF for sharing

---


### Advanced Features


#### Transcript Generation
- Automatic creation of timestamped transcript files
- Markdown format with full analysis details
- Exportable to HTML and PDF formats
- Useful for documentation and audit trails


#### File Integration
- Input files can provide concrete examples
- Related files add domain-specific context
- Supports glob patterns for flexible file selection
- Automatically handles file reading and formatting


#### Multi-Level Synthesis
- Each level builds on previous insights
- Progressively deeper understanding
- Useful for complex, multi-faceted problems
- Helps identify meta-level patterns and principles

---


### Troubleshooting


#### Issue: Synthesis Seems One-Sided
**Solution:** Ensure both thesis and antithesis are equally substantive. Weak positions may not generate balanced synthesis.


#### Issue: Results Are Too Abstract
**Solution:** Provide more specific context and concrete examples. Include related files with real-world scenarios.


#### Issue: Analysis Takes Too Long
**Solution:** Reduce synthesis levels (try 2 instead of 3). Simplify thesis/antithesis statements.


#### Issue: Contradictions Not Clearly Identified
**Solution:** Make thesis and antithesis more explicitly opposed. Avoid positions that are already partially aligned.

---


### Integration with Other Tasks

The DialecticalReasoningTask works well with:

- **FileModificationTask** - Implement synthesis recommendations
- **AnalysisTask** - Provide deeper context for analysis
- **DocumentationTask** - Document the reasoning process
- **SubPlanningTask** - Break down synthesis into actionable steps

---


### Example Workflow

```
1. Identify conflicting positions or requirements
   ↓
2. Configure thesis, antithesis, and context
   ↓
3. Run DialecticalReasoningTask (3 synthesis levels)
   ↓
4. Review synthesis at each level
   ↓
5. Examine final integration and recommendations
   ↓
6. Use FileModificationTask to implement decisions
   ↓
7. Document decisions using generated transcript
```

---


### Tips for Success

✅ **Do:**
- Invest time in clearly articulating both positions
- Provide rich context about the domain
- Review all synthesis levels, not just the final one
- Use the transcript for team communication
- Iterate if the first synthesis doesn't feel complete

❌ **Don't:**
- Use vague or poorly defined positions
- Skip providing context
- Assume the first synthesis is final
- Ignore remaining tensions identified
- Use for simple binary choices (use simpler decision tools instead)

---


### Support and Resources

For additional help:
- Review the generated transcript for detailed reasoning
- Examine the contradictions tab for deeper understanding
- Use related files to provide additional context
- Consult the final integration for practical recommendations

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\social\EthicalReasoningTask.kt


## EthicalReasoningTask Documentation


### Overview

The `EthicalReasoningTask` is a comprehensive tool designed to analyze complex ethical dilemmas through multiple established ethical frameworks. It provides structured, multi-perspective analysis to guide decision-making in situations involving competing values and stakeholder interests.


### Purpose

This task helps users:
- Deconstruct complex ethical problems systematically
- Evaluate decisions from multiple ethical perspectives
- Identify areas of agreement and conflict between frameworks
- Understand ethical trade-offs involved in different courses of action
- Make well-reasoned, ethically sound decisions


### Use Cases

- **AI Safety & Alignment**: Analyzing ethical implications of AI system design decisions
- **Product Development**: Evaluating ethical considerations in feature design and deployment
- **Policy Making**: Assessing policy options through multiple ethical lenses
- **Corporate Governance**: Guiding organizational decision-making on ethical matters
- **Legal & Compliance**: Understanding ethical dimensions of regulatory decisions


### Configuration


#### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `ethical_dilemma` | String | A clear, detailed description of the ethical problem or decision to be made |
| `stakeholders` | List[String] | Individuals, groups, or entities affected by the decision (minimum 1 required) |


#### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `ethical_frameworks` | List[String] | `["utilitarianism", "deontology", "virtue_ethics"]` | Ethical frameworks to apply. Options: `utilitarianism`, `deontology`, `virtue_ethics`, `care_ethics`, `rights_based` |
| `input_files` | List[String] | None | File paths (supports glob patterns) to provide context for analysis |
| `context` | String | None | Additional background information or constraints relevant to the dilemma |


### Ethical Frameworks

The task supports analysis through the following frameworks:


#### Utilitarianism
Focuses on maximizing overall well-being and minimizing harm. Evaluates actions based on their consequences for all stakeholders.


#### Deontology
Emphasizes duties, rights, and rules. Evaluates actions based on adherence to moral principles regardless of outcomes.


#### Virtue Ethics
Focuses on character and virtues. Evaluates what a virtuous person would do in the situation.


#### Care Ethics
Emphasizes relationships and interdependence. Evaluates actions based on maintaining relationships and responding to needs.


#### Rights-Based Ethics
Focuses on individual rights and freedoms. Evaluates actions based on respect for fundamental human rights.


### How It Works


#### Step 1: Dilemma & Stakeholder Analysis
The task begins by deconstructing the ethical dilemma:
- Identifies the core conflict and key ethical questions
- Analyzes each stakeholder's interests, rights, and potential impacts
- Establishes a shared understanding of the problem


#### Step 2: Framework Application
For each selected ethical framework:
- Explains the framework's core principles
- Applies those principles to your specific dilemma
- Derives a recommended course of action from that framework's perspective
- Justifies the recommendation


#### Step 3: Synthesis & Recommendation
The task synthesizes findings across all frameworks:
- Identifies areas of agreement and conflict
- Articulates ethical trade-offs
- Provides a final, comprehensive recommendation
- Explains why this recommendation is ethically sound


### Output

The task generates:

1. **Interactive UI Tabs** displaying:
   - Overview with final recommendation summary
   - Dilemma & Stakeholder Analysis
   - Individual framework analyses
   - Synthesis & Recommendation
   - Context (if provided)

2. **Downloadable Transcript** available in multiple formats:
   - Markdown (.md)
   - HTML (.html)
   - PDF (.pdf)

3. **Final Summary** including:
   - The ethical dilemma analyzed
   - Key recommendation
   - Links to detailed analysis


### Example Usage


#### Scenario: AI Model Deployment Decision

**Dilemma:**
"Our company has developed an AI model that improves customer service efficiency by 40% but may displace 15% of our support staff. Should we deploy it?"

**Stakeholders:**
- Current support staff
- Customers
- Company shareholders
- Affected communities
- Future employees

**Frameworks:**
- Utilitarianism
- Deontology
- Virtue Ethics

**Context:**
"The company has committed to employee welfare. The industry is competitive. Retraining programs are available."


### Tips for Best Results

1. **Be Specific**: Provide detailed descriptions of the dilemma, not vague summaries
2. **Include All Stakeholders**: Don't overlook indirect or less obvious stakeholders
3. **Provide Context**: Include relevant constraints, commitments, or background information
4. **Select Relevant Frameworks**: Choose frameworks most applicable to your situation
5. **Use Input Files**: Include relevant documents, policies, or data to inform analysis


### Validation

The task validates your configuration:
- ✓ Ethical dilemma must not be empty
- ✓ At least one stakeholder must be specified
- ✓ Framework names must be valid and non-empty
- ✓ All required fields must be populated


### Error Handling

If the analysis fails:
- Check that your ethical dilemma is clearly stated
- Verify all stakeholders are listed
- Ensure selected frameworks are valid
- Review any input files for accessibility issues
- Check system logs for detailed error messages


### Performance Considerations

- Analysis time depends on dilemma complexity and number of frameworks
- Longer dilemmas and more frameworks increase processing time
- Results are streamed to UI in real-time
- Transcripts are generated incrementally


### Limitations

- Analysis reflects AI model capabilities and training data
- Should complement, not replace, human ethical deliberation
- Complex dilemmas may require domain expertise beyond the analysis
- Framework applications are interpretations, not definitive ethical truths


### Support

For issues or questions:
1. Review the validation error messages
2. Check the generated transcript for detailed analysis
3. Verify input parameters match the required format
4. Consult the ethical frameworks documentation for clarification

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\social\GameTheoryTask.kt


## GameTheoryTask Documentation


### Overview

`GameTheoryTask` is a comprehensive game theory analysis tool that performs strategic interaction analysis using game theory principles. It systematically analyzes competitive and cooperative scenarios to identify optimal strategies, equilibria, and strategic recommendations.


### Purpose

This task enables users to:
- Analyze strategic situations involving multiple players with competing or aligned interests
- Identify Nash equilibria and dominant strategies
- Construct and analyze payoff matrices
- Find Pareto optimal outcomes
- Model repeated game dynamics
- Generate strategic recommendations for each player


### Key Features


#### Core Analysis Capabilities

| Feature | Description |
|---------|-------------|
| **Game Structure Analysis** | Identifies game type, player strategies, and payoff characteristics |
| **Payoff Matrix Construction** | Builds detailed matrices showing outcomes for all strategy combinations |
| **Nash Equilibrium Identification** | Finds pure and mixed strategy equilibria |
| **Dominant Strategy Analysis** | Identifies strictly/weakly dominant and dominated strategies |
| **Pareto Optimality** | Determines efficient outcomes and compares to equilibria |
| **Repeated Game Analysis** | Models multi-round games with trigger strategies and reputation effects |
| **Strategic Recommendations** | Provides player-specific guidance based on complete analysis |


#### Supported Game Types

- **Cooperative Games**: Players can form binding agreements
- **Non-Cooperative Games**: Players act independently
- **Zero-Sum Games**: One player's gain is another's loss
- **Repeated Games**: Multi-iteration scenarios with memory effects
- **Sequential Games**: Games with ordered moves and information revelation


### Configuration


#### Required Parameters

```kotlin
game_scenario: String          // The strategic situation to analyze
players: List<String>          // List of player/agent names
```


#### Optional Parameters

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `player_strategies` | Map<String, List<String>> | null | Pre-defined strategies per player |
| `game_type` | String | "non-cooperative" | Type of game to analyze |
| `build_payoff_matrix` | Boolean | true | Generate payoff matrix |
| `find_nash_equilibria` | Boolean | true | Identify Nash equilibria |
| `analyze_dominant_strategies` | Boolean | true | Analyze dominant strategies |
| `find_pareto_optimal` | Boolean | true | Find Pareto optimal outcomes |
| `provide_recommendations` | Boolean | true | Generate strategic recommendations |
| `repeated_game_analysis` | Boolean | false | Analyze as repeated game |
| `iterations` | Int | 10 | Number of rounds for repeated games |
| `additional_context` | String | null | Extra constraints or information |
| `input_files` | List<String> | null | File patterns for context |


### Usage Examples


#### Example 1: Prisoner's Dilemma Analysis

```kotlin
GameTheoryTaskExecutionConfigData(
    game_scenario = "Two suspects arrested for a crime. Each can cooperate with the other or defect by testifying against them. If both cooperate, each gets 1 year. If both defect, each gets 3 years. If one defects and one cooperates, the defector goes free and the cooperator gets 5 years.",
    players = listOf("Suspect A", "Suspect B"),
    player_strategies = mapOf(
        "Suspect A" to listOf("Cooperate", "Defect"),
        "Suspect B" to listOf("Cooperate", "Defect")
    ),
    game_type = "non-cooperative",
    build_payoff_matrix = true,
    find_nash_equilibria = true,
    analyze_dominant_strategies = true
)
```


#### Example 2: Market Competition Analysis

```kotlin
GameTheoryTaskExecutionConfigData(
    game_scenario = "Two companies competing in a market. Each can set prices high or low. High prices yield $100M profit if both choose high, $50M if one chooses high and one low, $20M if both choose low.",
    players = listOf("Company A", "Company B"),
    game_type = "non-cooperative",
    repeated_game_analysis = true,
    iterations = 20,
    provide_recommendations = true
)
```


#### Example 3: Negotiation Scenario

```kotlin
GameTheoryTaskExecutionConfigData(
    game_scenario = "Two parties negotiating a contract. Party A values the deal at $1M, Party B at $800K. They can accept, reject, or make counter-offers.",
    players = listOf("Party A", "Party B"),
    game_type = "sequential",
    additional_context = "Negotiations must conclude within 3 rounds",
    find_pareto_optimal = true
)
```


### Output Structure

The task generates organized output across multiple tabs:


#### 1. **Overview Tab**
- Scenario summary
- Players and game type
- Analysis status and completion time


#### 2. **Game Structure Tab**
- Game classification (type, information structure)
- Strategy space definition
- Payoff characteristics
- Key game features


#### 3. **Payoff Matrix Tab**
- Strategy combinations and outcomes
- Numerical or qualitative payoffs
- Outcome explanations


#### 4. **Nash Equilibria Tab**
- All identified equilibria
- Pure vs. mixed strategy classification
- Stability assessment
- Equilibrium selection discussion


#### 5. **Dominant Strategies Tab**
- Strictly dominant strategies
- Weakly dominant strategies
- Dominated strategies
- Iterative elimination results


#### 6. **Pareto Optimality Tab**
- Pareto optimal outcomes
- Comparison to Nash equilibria
- Efficiency gaps
- Cooperation opportunities


#### 7. **Repeated Game Tab** (if enabled)
- Folk theorem implications
- Trigger strategy analysis
- Reputation effects
- Discount factor considerations


#### 8. **Recommendations Tab**
- Player-specific optimal strategies
- Contingent strategy guidance
- Risk assessments
- Implementation guidance


#### 9. **Summary Tab**
- Structured analysis summary
- Key findings consolidated
- Strategic insights


### Validation Rules

The task validates configuration data:

- `game_scenario` must not be blank
- `players` list must not be empty
- All player names must be non-blank
- `game_type` must be specified
- `iterations` must be ≥ 1
- Repeated game analysis requires ≥ 2 iterations


### Output Formats


#### Transcript File
- Markdown format with complete analysis
- Includes timestamps and duration
- Exportable to HTML and PDF
- Accessible via web interface


#### Final Result
- Consolidated summary with key sections
- Truncated to 10,000 characters per field
- Includes analysis duration
- Ready for downstream processing


### Advanced Features


#### Context Integration
- Incorporates prior task outputs
- Supports additional context injection
- Maintains execution state across tasks


#### File Input Support
- Accepts glob patterns for file inclusion
- Automatically filters ignored files
- Supports multiple document formats
- Extracts text from various file types


#### Repeated Game Modeling
- Configurable iteration counts
- Trigger strategy analysis
- Reputation and discount factor effects
- Finite vs. infinite horizon implications


### Performance Considerations

- Analysis time scales with game complexity
- Large strategy spaces may require simplification
- Repeated game analysis increases computation time
- Transcript generation adds I/O overhead


### Error Handling

The task provides detailed error messages for:
- Missing or invalid configuration
- API communication failures
- File access issues
- Analysis generation errors

Errors are logged with context and duration information for debugging.


### Best Practices

1. **Define Clear Scenarios**: Provide specific, unambiguous game descriptions
2. **Specify All Players**: Ensure all relevant actors are included
3. **Use Realistic Payoffs**: Base payoff structures on actual incentives
4. **Enable Relevant Analysis**: Disable unnecessary analyses for faster results
5. **Provide Context**: Include additional constraints or information when relevant
6. **Review Recommendations**: Validate strategic recommendations against domain knowledge


### Integration with Task Orchestration

This task integrates with the broader task orchestration system:
- Accepts prior context from dependent tasks
- Outputs structured results for downstream processing
- Supports task chaining and complex workflows
- Maintains execution state and transcript records

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\social\LLMExperimentTask.kt


## LLMExperimentTask User Documentation


### Overview

The **LLMExperimentTask** is a comprehensive tool for conducting controlled experiments on Large Language Model (LLM) behavior. It enables researchers and developers to systematically test how LLMs respond to different prompts, temperature settings, and variables while collecting detailed metrics and statistical analysis.


### Key Features

- **Prompt Template Testing**: Define base prompts with variable placeholders for systematic substitution
- **Temperature Variation**: Test multiple temperature values to observe how randomness affects responses
- **Repetition-Based Statistics**: Run multiple trials per condition for statistical validity
- **Custom Metrics**: Track response characteristics like length, sentiment, and keyword presence
- **Statistical Analysis**: Automatic t-tests, correlation analysis, and effect size calculations
- **Response Diversity Measurement**: Quantify consistency vs. variability across trials
- **Concurrent Execution**: Run multiple trials in parallel for faster completion
- **Comprehensive Reporting**: Generate detailed markdown reports with tables and insights


### Configuration Parameters


#### Required Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `prompt_templates` | List<String> | Base prompt templates with `{variable}` placeholders | `["What is {topic}?", "Explain {topic} in detail"]` |


#### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `prompt_variables` | Map<String, List<String>> | `{}` | Variables to substitute in templates | `{"topic": ["AI", "ML", "NLP"]}` |
| `temperature_values` | List<Double> | `[0.1, 0.7]` | Temperature settings to test (0.0-2.0) | `[0.0, 0.5, 1.0, 1.5]` |
| `repetitions` | Int | `3` | Number of trials per condition (1-100) | `5` |
| `metrics` | List<String> | `["response_length", "response_time"]` | Metrics to track | `["response_length", "sentiment", "clarity"]` |
| `statistical_analysis` | Boolean | `true` | Enable statistical significance testing | `true` |
| `significance_level` | Double | `0.05` | Alpha level for statistical tests (0.01-0.10) | `0.05` |


### Usage Examples


#### Example 1: Basic Temperature Comparison

Test how temperature affects response consistency:

```json
{
  "prompt_templates": [
    "Write a creative story about a robot discovering emotions."
  ],
  "temperature_values": [0.0, 0.5, 1.0, 1.5],
  "repetitions": 5,
  "metrics": ["response_length", "response_time"]
}
```

**What this does:**
- Runs the same prompt 5 times at each temperature (20 total trials)
- Compares response lengths and generation times
- Analyzes statistical differences between temperatures


#### Example 2: Prompt Variation Study

Compare different prompt phrasings:

```json
{
  "prompt_templates": [
    "What is machine learning?",
    "Explain machine learning",
    "Define machine learning in simple terms"
  ],
  "temperature_values": [0.7],
  "repetitions": 3,
  "metrics": ["response_length", "response_time"]
}
```

**What this does:**
- Tests 3 different prompt phrasings
- Runs each 3 times (9 total trials)
- Identifies which phrasing produces most consistent results


#### Example 3: Variable Substitution with Statistics

Study how topic affects response quality:

```json
{
  "prompt_templates": [
    "Explain {topic} to a beginner",
    "What are the key concepts of {topic}?"
  ],
  "prompt_variables": {
    "topic": ["quantum computing", "blockchain", "neural networks"]
  },
  "temperature_values": [0.3, 0.7],
  "repetitions": 4,
  "metrics": ["response_length", "response_time"],
  "statistical_analysis": true,
  "significance_level": 0.05
}
```

**What this does:**
- Creates 6 unique prompts (2 templates × 3 topics)
- Tests each at 2 temperatures with 4 repetitions (48 total trials)
- Performs t-tests comparing topics and temperatures
- Calculates effect sizes (Cohen's d)


### Understanding the Output


#### Overview Tab

Displays:
- Experimental design summary
- Number of conditions and total trials
- Progress indicator
- Completion time and success rate


#### Progress Tab

Shows:
- Real-time updates for each condition
- Temperature and variable values
- Sample prompts
- Trial completion status


#### Statistical Tables Tab

Contains:

1. **Descriptive Statistics by Temperature**
   - Mean, standard deviation, min/max values
   - Coefficient of variation (CV)
   - Median values

2. **Pairwise Temperature Comparisons**
   - t-statistics and p-values
   - Cohen's d effect sizes
   - Significance indicators (✓/✗)

3. **Variable Effects Analysis**
   - Breakdown by each variable value
   - 95% confidence intervals
   - Pairwise comparisons

4. **Correlation Matrix**
   - Pearson correlations between all metrics
   - Identifies relationships between measurements

5. **Effect Sizes Summary**
   - Interpretation: Negligible, Small, Medium, Large


#### Analysis Tab

Provides:
- Summary statistics grouped by temperature
- Variable effect analysis
- Response diversity assessment
- Statistical significance findings


#### Insights Tab

Contains:
- LLM-generated interpretation of results
- Key patterns and trends
- Implications for LLM behavior
- Recommendations for further investigation


### Interpreting Results


#### Response Diversity Metric

```
Compressibility Score:
- < 1.1  → High Diversity (responses are very different)
- 1.1-1.5 → Moderate Diversity (some variation)
- > 1.5  → Low Diversity (responses are similar)
```

**Interpretation:**
- **High diversity at low temperature**: Unexpected; may indicate prompt ambiguity
- **Low diversity at high temperature**: Unexpected; may indicate strong response patterns
- **Increasing diversity with temperature**: Expected behavior


#### Statistical Significance

- **p-value < 0.05**: Statistically significant difference (reject null hypothesis)
- **p-value ≥ 0.05**: No significant difference
- **Cohen's d > 0.8**: Large practical effect
- **Cohen's d 0.5-0.8**: Medium effect
- **Cohen's d < 0.2**: Negligible effect


#### Temperature Effects

| Temperature | Typical Behavior |
|-------------|-----------------|
| 0.0 | Deterministic; identical responses |
| 0.3-0.5 | Focused; consistent with variation |
| 0.7-1.0 | Balanced; good creativity/consistency |
| 1.5+ | Creative; high variability |


### Best Practices


#### Experimental Design

1. **Start Simple**: Begin with 1-2 templates and 2-3 temperatures
2. **Adequate Repetitions**: Use at least 3-5 repetitions for statistical validity
3. **Clear Variables**: Use distinct, non-overlapping variable values
4. **Meaningful Metrics**: Select metrics relevant to your research question


#### Configuration Tips

```json
// ✓ Good: Clear, focused experiment
{
  "prompt_templates": ["Explain {concept}"],
  "prompt_variables": {"concept": ["AI", "ML", "DL"]},
  "temperature_values": [0.0, 0.7, 1.0],
  "repetitions": 5
}

// ✗ Avoid: Too many conditions
{
  "prompt_templates": ["Template 1", "Template 2", ..., "Template 20"],
  "prompt_variables": {"var1": [...], "var2": [...], "var3": [...]},
  "temperature_values": [0.0, 0.1, 0.2, ..., 2.0],
  "repetitions": 10  // 20 × 3 × 21 × 10 = 12,600 trials!
}
```


#### Metric Selection

**Built-in Metrics:**
- `response_length`: Character count of response
- `response_time`: Generation time in milliseconds

**Custom Metrics** (LLM-evaluated):
- `clarity`: How clear and understandable is the response?
- `accuracy`: How factually correct is the response?
- `creativity`: How original or creative is the response?
- `completeness`: Does it address all aspects of the question?
- `sentiment`: What is the emotional tone?


### Common Use Cases


#### 1. Bias Detection Study

```json
{
  "prompt_templates": [
    "Is {group} good at {skill}?"
  ],
  "prompt_variables": {
    "group": ["men", "women", "engineers", "artists"],
    "skill": ["math", "writing", "coding", "design"]
  },
  "temperature_values": [0.7],
  "repetitions": 5,
  "metrics": ["sentiment", "bias_indicators"]
}
```


#### 2. Consistency Testing

```json
{
  "prompt_templates": [
    "What is 2+2?"
  ],
  "temperature_values": [0.0, 0.5, 1.0],
  "repetitions": 10,
  "metrics": ["response_length"]
}
```


#### 3. Prompt Engineering Optimization

```json
{
  "prompt_templates": [
    "Solve: {problem}",
    "Step by step, solve: {problem}",
    "Think carefully about: {problem}"
  ],
  "prompt_variables": {
    "problem": ["2x + 3 = 7", "What is 15% of 200?"]
  },
  "temperature_values": [0.1],
  "repetitions": 3,
  "metrics": ["accuracy", "response_length"]
}
```


### Troubleshooting


#### Issue: "Repetitions must be between 1 and 100"

**Solution:** Adjust `repetitions` parameter to valid range


#### Issue: "Temperature values must be between 0.0 and 2.0"

**Solution:** Ensure all temperature values are within valid range


#### Issue: Experiment takes too long

**Solutions:**
- Reduce number of conditions
- Decrease repetitions (minimum 2-3 for statistics)
- Reduce number of metrics
- Use fewer temperature values


#### Issue: Statistical tables show "N/A"

**Possible causes:**
- Insufficient data points (need ≥2 per group)
- Metrics not calculated for all responses
- Variable combinations with no results


### Output Files

The experiment generates:

1. **Markdown Report** (`.md`): Full experiment transcript with all results
2. **HTML Report** (`.html`): Formatted version for web viewing
3. **PDF Report** (`.pdf`): Printable version

All files are accessible via links in the final output.


### Advanced Features


#### Statistical Methods Used

- **t-tests**: Compare means between groups
- **Pearson Correlation**: Measure relationships between metrics
- **Cohen's d**: Standardized effect size
- **Confidence Intervals**: 95% CI for means
- **Coefficient of Variation**: Relative variability measure


#### Concurrent Execution

The task automatically:
- Runs multiple trials in parallel
- Maintains thread-safe result collection
- Provides real-time progress updates
- Handles errors gracefully with partial results


### Support and Resources

For questions or issues:
1. Review the "Analysis" tab for detailed statistical breakdowns
2. Check the "Insights" tab for LLM-generated interpretation
3. Examine sample responses in condition summaries
4. Review statistical tables for specific comparisons

---

**Last Updated:** 2024
**Version:** 1.0

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\social\LLMPollSimulationTask.kt


## LLMPollSimulationTask Documentation


### Overview

The **LLMPollSimulationTask** is a sophisticated survey simulation tool that uses AI language models to generate realistic poll responses from diverse simulated respondent personas. It enables researchers and analysts to test survey instruments, explore response patterns, and detect potential biases before deploying surveys to real audiences.


### Key Features


#### Survey Design
- **Multiple Question Types**: Support for multiple choice, single choice, Likert scales, ratings, yes/no, ranking, matrix, and open-ended questions
- **Conditional Logic**: Skip questions based on previous responses
- **Validation Rules**: Define min/max values for numeric scales
- **Question Metadata**: Track question IDs, requirements, and dependencies


#### Respondent Simulation
- **Profile-Based Generation**: Create respondent profiles with demographics and characteristics
- **Realistic Demographics**: Auto-generate or specify age, gender, location, education
- **Persona Prompts**: Build detailed personas that guide LLM responses
- **Scalable Simulation**: Generate multiple respondents per profile


#### Analysis & Reporting
- **Descriptive Statistics**: Mean, median, standard deviation, frequency distributions
- **Cross-Tabulation**: Analyze responses by demographic dimensions
- **Sentiment Analysis**: Evaluate emotional tone of open-ended responses
- **Bias Detection**: Identify central tendency bias, primacy/recency effects, response patterns
- **Comprehensive Reports**: Multi-tab interface with detailed findings and insights


### Configuration


#### Basic Setup

```kotlin
val config = LLMPollSimulationTask.LLMPollSimulationTaskExecutionConfigData(
    questions = listOf(
        SurveyQuestion(
            id = "q1",
            text = "How satisfied are you with our service?",
            type = QuestionType.LIKERT_SCALE,
            min = 1,
            max = 5,
            required = true
        )
    ),
    respondent_profiles = listOf(
        RespondentProfile(
            id = "profile_1",
            description = "Tech-savvy millennials",
            demographics = mapOf(
                "age" to "25-34",
                "gender" to "Mixed",
                "location" to "Urban"
            ),
            characteristics = listOf("Early adopters", "Digital natives", "Value convenience")
        )
    ),
    respondents_per_profile = 50,
    temperature = 0.7
)
```


#### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `questions` | List<SurveyQuestion> | Required | Survey questions to administer |
| `respondent_profiles` | List<RespondentProfile> | Required | Respondent profile templates |
| `respondents_per_profile` | Int | 10 | Number of simulated respondents per profile |
| `include_demographics` | Boolean | true | Include demographic data in responses |
| `demographic_dimensions` | List<String> | [age, gender, location, education] | Dimensions to track |
| `cross_tabulation` | Boolean | true | Generate cross-tabulation analysis |
| `sentiment_analysis` | Boolean | true | Analyze sentiment of open-ended responses |
| `bias_detection` | Boolean | true | Detect response biases and patterns |
| `temperature` | Double | 0.7 | LLM temperature (0.0-1.0) for response variability |


### Question Types


#### SINGLE_CHOICE
Select one option from a list.
```kotlin
SurveyQuestion(
    id = "q_brand",
    text = "Which brand do you prefer?",
    type = QuestionType.SINGLE_CHOICE,
    options = listOf("Brand A", "Brand B", "Brand C")
)
```


#### MULTIPLE_CHOICE
Select multiple options from a list.
```kotlin
SurveyQuestion(
    id = "q_features",
    text = "Which features are important to you?",
    type = QuestionType.MULTIPLE_CHOICE,
    options = listOf("Price", "Quality", "Support", "Design")
)
```


#### LIKERT_SCALE
Rate agreement on a scale (typically 1-5).
```kotlin
SurveyQuestion(
    id = "q_satisfaction",
    text = "I am satisfied with this product",
    type = QuestionType.LIKERT_SCALE,
    min = 1,
    max = 5
)
```


#### RATING
Numeric rating scale.
```kotlin
SurveyQuestion(
    id = "q_rating",
    text = "Rate your overall experience (1-10)",
    type = QuestionType.RATING,
    min = 1,
    max = 10
)
```


#### YES_NO
Binary response.
```kotlin
SurveyQuestion(
    id = "q_purchase",
    text = "Would you purchase this product?",
    type = QuestionType.YES_NO
)
```


#### OPEN_ENDED
Free-text response.
```kotlin
SurveyQuestion(
    id = "q_feedback",
    text = "Please share any additional feedback",
    type = QuestionType.OPEN_ENDED
)
```


#### RANKING
Rank options in order of preference.
```kotlin
SurveyQuestion(
    id = "q_priorities",
    text = "Rank these factors by importance",
    type = QuestionType.RANKING,
    options = listOf("Cost", "Quality", "Speed", "Support")
)
```


### Respondent Profiles

Define realistic respondent personas to guide survey responses:

```kotlin
RespondentProfile(
    id = "profile_executive",
    description = "C-level executives",
    demographics = mapOf(
        "age" to "45-54",
        "gender" to "Male",
        "location" to "Urban",
        "education" to "Master's"
    ),
    characteristics = listOf(
        "Decision makers",
        "Budget conscious",
        "Results-oriented",
        "Time-constrained"
    ),
    background_context = "Senior management with 15+ years experience in enterprise software"
)
```


### Output & Reports


#### Multi-Tab Interface

The task generates a comprehensive report with multiple tabs:

1. **Overview**: Survey design summary and progress
2. **Progress**: Real-time response collection status
3. **Statistics**: Descriptive statistics and frequency distributions
4. **Cross-Tabulation**: Analysis by demographic dimensions
5. **Sentiment**: Sentiment analysis of open-ended responses
6. **Bias Detection**: Identified biases and response patterns
7. **Insights**: AI-generated findings and recommendations


#### Report Contents


##### Descriptive Statistics
- Response counts and percentages
- Mean, median, standard deviation for numeric scales
- Frequency distributions for categorical responses
- Sample text responses for open-ended questions


##### Cross-Tabulation Analysis
- Response patterns by demographic groups
- Comparison tables showing differences across dimensions
- Percentage breakdowns within each demographic segment


##### Sentiment Analysis
- Positive/negative/neutral sentiment scores
- Overall sentiment classification
- Sentiment distribution across respondent groups


##### Bias Detection
- **Central Tendency Bias**: Clustering around midpoint values
- **Acquiescence Bias**: Low variance indicating agreement patterns
- **Primacy/Recency Effects**: First/last option selection bias
- **Demographic Bias**: Significant differences across groups
- **Response Quality**: Identification of rushed or suspicious responses


##### Insights & Recommendations
- Key findings from the survey
- Demographic patterns and differences
- Response consistency assessment
- Potential survey improvements
- Implications for real-world polling


### Usage Examples


#### Example 1: Product Satisfaction Survey

```kotlin
val productSurvey = LLMPollSimulationTask.LLMPollSimulationTaskExecutionConfigData(
    questions = listOf(
        SurveyQuestion(
            id = "satisfaction",
            text = "How satisfied are you with our product?",
            type = QuestionType.LIKERT_SCALE,
            min = 1,
            max = 5
        ),
        SurveyQuestion(
            id = "recommend",
            text = "Would you recommend us to others?",
            type = QuestionType.YES_NO
        ),
        SurveyQuestion(
            id = "improvements",
            text = "What improvements would you suggest?",
            type = QuestionType.OPEN_ENDED
        )
    ),
    respondent_profiles = listOf(
        RespondentProfile(
            id = "power_users",
            description = "Power users and enthusiasts",
            characteristics = listOf("Frequent users", "Feature-focused", "Tech-savvy")
        ),
        RespondentProfile(
            id = "casual_users",
            description = "Casual users",
            characteristics = listOf("Occasional use", "Value simplicity", "Less technical")
        )
    ),
    respondents_per_profile = 100
)
```


#### Example 2: Political Poll Simulation

```kotlin
val politicalPoll = LLMPollSimulationTask.LLMPollSimulationTaskExecutionConfigData(
    questions = listOf(
        SurveyQuestion(
            id = "approval",
            text = "Do you approve of the current administration?",
            type = QuestionType.YES_NO
        ),
        SurveyQuestion(
            id = "priority",
            text = "What is your top policy priority?",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Economy", "Healthcare", "Education", "Environment")
        )
    ),
    respondent_profiles = listOf(
        RespondentProfile(
            id = "conservative",
            description = "Conservative voters",
            characteristics = listOf("Traditional values", "Lower taxes", "Strong defense")
        ),
        RespondentProfile(
            id = "progressive",
            description = "Progressive voters",
            characteristics = listOf("Social justice", "Environmental focus", "Expanded services")
        ),
        RespondentProfile(
            id = "moderate",
            description = "Moderate/Independent voters",
            characteristics = listOf("Pragmatic", "Balanced approach", "Issue-focused")
        )
    ),
    respondents_per_profile = 200,
    cross_tabulation = true,
    bias_detection = true
)
```


### Interpretation Guide


#### Understanding Statistics

- **Mean**: Average response value (useful for Likert/rating scales)
- **Median**: Middle value when responses are sorted (robust to outliers)
- **Std Dev**: Measure of response variability (higher = more diverse opinions)
- **Frequency**: Count of responses for each option


#### Identifying Biases

| Bias Type | Indicator | Implication |
|-----------|-----------|-------------|
| Central Tendency | Mean near midpoint, low std dev | Respondents avoiding extremes |
| Acquiescence | Consistently high agreement | "Yes-saying" tendency |
| Primacy Effect | First option selected more | Question order matters |
| Recency Effect | Last option selected more | Question order matters |
| Demographic Bias | Large differences between groups | Potential sampling issues |


#### Quality Indicators

- **Response Rate**: Percentage of completed surveys (target: >90%)
- **Response Time**: Average time to complete (too fast = rushed, too slow = confused)
- **Variance**: Diversity of responses (low = potential bias)
- **Consistency**: Agreement across similar questions


### Best Practices


#### Survey Design
1. **Keep questions clear and unambiguous** - Avoid leading language
2. **Use consistent scales** - Maintain same rating ranges across questions
3. **Order questions logically** - General to specific, easy to difficult
4. **Test conditional logic** - Verify skip patterns work correctly
5. **Balance response options** - Avoid biased option lists


#### Respondent Profiles
1. **Create realistic personas** - Base on actual demographic data
2. **Include diverse profiles** - Represent target population segments
3. **Specify characteristics** - Provide context for persona behavior
4. **Use sufficient respondents** - At least 30-50 per profile for stability
5. **Vary demographics** - Test across age, gender, education, location


#### Analysis
1. **Review all tabs** - Don't focus only on statistics
2. **Check for biases** - Investigate detected patterns
3. **Compare demographics** - Look for meaningful differences
4. **Read insights** - AI-generated recommendations provide context
5. **Validate findings** - Cross-reference multiple analysis types


### Troubleshooting


#### Issue: Low Response Quality
**Solution**: Increase temperature (0.8-0.9) for more varied responses, or review persona prompts for clarity


#### Issue: Biased Responses
**Solution**: Check respondent profile characteristics, ensure diverse demographic representation, review question wording


#### Issue: Inconsistent Results
**Solution**: Increase respondents_per_profile for more stable statistics, verify question types match analysis needs


#### Issue: Missing Demographic Analysis
**Solution**: Ensure include_demographics=true, verify demographic_dimensions are populated in profiles


### Advanced Features


#### Conditional Questions
Skip questions based on previous responses:
```kotlin
SurveyQuestion(
    id = "q_purchase_reason",
    text = "Why did you purchase?",
    type = QuestionType.OPEN_ENDED,
    conditional_on = "q_purchase"  // Only shown if q_purchase answered
)
```


#### Custom Temperature Settings
Adjust LLM creativity:
- **0.0-0.3**: Consistent, predictable responses
- **0.4-0.6**: Balanced responses with some variation
- **0.7-0.9**: Diverse, creative responses
- **1.0**: Maximum variability


#### Demographic Tracking
Automatically track and analyze by specified dimensions:
```kotlin
demographic_dimensions = listOf("age", "gender", "location", "education", "income")
```


### Output Files

The task generates:
- **Markdown Report** (.md): Full survey report with all analyses
- **HTML Version** (.html): Formatted web-viewable report
- **PDF Version** (.pdf): Printable report format

All files are accessible via links in the task completion message.


### Performance Considerations

- **Response Time**: ~1-2 seconds per respondent (depends on question count and LLM)
- **Memory**: Scales with respondent count (typically <100MB for 1000 respondents)
- **Parallelization**: Responses collected in parallel for efficiency
- **Typical Duration**: 100 respondents × 10 questions ≈ 2-3 minutes


### See Also

- [LLMExperimentTask](LLMExperimentTask.md) - Run controlled LLM experiments
- [GameTheoryTask](./GameTheoryTask.md) - Simulate strategic interactions
- [MultiPerspectiveAnalysisTask](./MultiPerspectiveAnalysisTask.md) - Analyze from multiple viewpoints

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\social\MultiPerspectiveAnalysisTask.kt


## MultiPerspectiveAnalysisTask Documentation


### Overview

The **MultiPerspectiveAnalysisTask** is a sophisticated analysis tool that examines complex subjects from multiple viewpoints and synthesizes the findings into a unified conclusion. This task is particularly valuable for architectural decisions, code reviews, strategic planning, and risk assessments where diverse perspectives lead to better outcomes.


### Purpose

This task enables you to:
- Analyze topics from multiple specified perspectives (e.g., technical, business, ethical, user experience)
- Generate detailed, perspective-specific insights
- Identify agreements and conflicts between viewpoints
- Synthesize diverse analyses into coherent recommendations
- Apply consensus thresholds to measure perspective alignment


### Configuration Parameters


#### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `analysis_subject` | String | The topic or problem to analyze from multiple viewpoints. Be specific and clear about what you want analyzed. |
| `perspectives` | List[String] | List of perspectives to consider (e.g., `["technical", "business", "ethical", "user"]`). Each perspective will be analyzed independently. |


#### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `input_files` | List[String] | None | Specific files or file patterns (glob syntax, e.g., `**/*.kt`) to provide context for the analysis. Supports wildcard patterns. |
| `related_files` | List[String] | None | Additional files for context that inform the analysis without being the primary focus. |
| `synthesize` | Boolean | `true` | Whether to synthesize perspectives into a unified conclusion. Set to `false` to receive only individual perspective analyses. |
| `consensus_threshold` | Double | `0.7` | Minimum confidence threshold for perspective agreement (range: 0.0-1.0). Used to assess how well perspectives align. |
| `task_dependencies` | List[String] | None | IDs of other tasks that must complete before this task runs. |


### Usage Examples


#### Example 1: Architectural Decision Review

Analyze a proposed microservices architecture from multiple angles:

```json
{
  "analysis_subject": "Migrate monolithic application to microservices architecture",
  "perspectives": ["technical", "business", "operational", "security"],
  "input_files": ["src/main/**/*.kt", "docs/architecture.md"],
  "synthesize": true,
  "consensus_threshold": 0.75
}
```

**Expected Output:** Individual analyses from each perspective, followed by a synthesis identifying technical feasibility, business impact, operational challenges, and security implications.


#### Example 2: Code Review from Multiple Angles

Review a critical code module from different viewpoints:

```json
{
  "analysis_subject": "Review PaymentProcessor.kt for production readiness",
  "perspectives": ["code_quality", "performance", "maintainability", "security"],
  "input_files": ["src/main/kotlin/payment/PaymentProcessor.kt"],
  "related_files": ["src/test/kotlin/payment/**/*.kt"],
  "synthesize": true,
  "consensus_threshold": 0.8
}
```

**Expected Output:** Detailed feedback from each perspective with a unified recommendation on production readiness.


#### Example 3: Feature Evaluation

Assess a proposed feature from stakeholder perspectives:

```json
{
  "analysis_subject": "Implement real-time notifications feature",
  "perspectives": ["user_experience", "technical_feasibility", "business_value", "resource_cost"],
  "synthesize": true,
  "consensus_threshold": 0.7
}
```


### Output Structure

The task generates output organized as follows:


#### Individual Perspective Analyses
Each perspective receives its own section containing:
- Key considerations and insights specific to that viewpoint
- Identified risks and opportunities
- Specific recommendations
- Confidence rating (0.0-1.0)
- Anticipated conflicts or synergies with other perspectives


#### Synthesis (if enabled)
A comprehensive summary including:
- Common themes and agreements across perspectives
- Conflicts or tensions between viewpoints
- Overall consensus level assessment
- Balanced, unified recommendations
- Perspectives requiring special attention
- Suggested next steps and action items


### Best Practices


#### 1. **Choose Relevant Perspectives**
Select perspectives that genuinely matter for your decision:
- For technical decisions: technical, performance, maintainability, security
- For business decisions: business, user, financial, operational
- For strategic decisions: strategic, competitive, risk, resource


#### 2. **Provide Context**
Include relevant files to give the analysis proper context:
```json
{
  "input_files": ["src/main/**/*.kt"],
  "related_files": ["docs/**/*.md", "config/**/*.yml"]
}
```


#### 3. **Set Appropriate Consensus Threshold**
- **0.5-0.6**: Accept diverse viewpoints; useful for exploratory analysis
- **0.7-0.8**: Moderate agreement required; good for most decisions
- **0.9+**: High consensus needed; use for critical decisions


#### 4. **Be Specific with Subjects**
❌ Poor: `"Review the code"`
✅ Good: `"Review PaymentProcessor.kt for thread safety and performance under high load"`


#### 5. **Use Glob Patterns for File Selection**
```json
{
  "input_files": [
    "src/main/kotlin/**/*.kt",
    "src/test/kotlin/**/*Test.kt",
    "docs/**/*.md"
  ]
}
```


### Common Use Cases


#### Architectural Decision Making
Evaluate proposed system designs from technical, operational, security, and business perspectives to ensure comprehensive coverage.


#### Code Review
Analyze critical code from quality, performance, maintainability, and security angles for thorough assessment.


#### Strategic Planning
Examine strategic initiatives from competitive, resource, risk, and business value perspectives.


#### Risk Assessment
Analyze potential risks from technical, operational, financial, and reputational perspectives.


#### Feature Evaluation
Assess new features from user experience, technical feasibility, business value, and resource cost perspectives.


### Validation Rules

The task validates configuration before execution:

| Validation | Rule |
|-----------|------|
| `analysis_subject` | Cannot be null or blank |
| `perspectives` | List cannot be null or empty |
| `consensus_threshold` | Must be between 0.0 and 1.0 |
| `input_files` | Cannot contain blank entries |

If validation fails, the task will report the specific error and not proceed.


### Performance Considerations

- **Analysis Time**: Scales with number of perspectives (each perspective requires an API call)
- **Context Size**: Larger input files increase processing time; consider file size limits
- **Synthesis Complexity**: Synthesis adds one additional API call but provides significant value


### Troubleshooting


#### Issue: "No analysis subject specified"
**Solution:** Ensure `analysis_subject` is provided and not empty.


#### Issue: "No perspectives specified"
**Solution:** Provide at least one perspective in the `perspectives` list.


#### Issue: "No default chatter available"
**Solution:** Ensure your API configuration is properly set up in the orchestration config.


#### Issue: Files not found with glob patterns
**Solution:** Verify glob pattern syntax and that files exist at the specified paths. Use `**/*.ext` for recursive matching.


### Integration with Task Orchestration

This task can be part of a larger workflow:

```json
{
  "task_dependencies": ["code_review_task_id"],
  "analysis_subject": "Evaluate code review findings",
  "perspectives": ["technical", "business", "risk"]
}
```

The task will wait for dependent tasks to complete before starting analysis.


### Output Files

The task generates:
- **Transcript File**: `multi_perspective_analysis_[timestamp].txt` containing the complete analysis transcript
- **Console Output**: Structured markdown with all perspective analyses and synthesis


### Tips for Better Results

1. **Provide specific context** through input files
2. **Use clear, descriptive perspective names** that are self-explanatory
3. **Adjust consensus threshold** based on decision criticality
4. **Review synthesis carefully** for conflicts that need resolution
5. **Use results to inform decision-making**, not replace human judgment

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\social\PersuasiveEssayTask.kt


## PersuasiveEssayTask User Documentation


### Overview

The **PersuasiveEssayTask** is a comprehensive tool for generating compelling, well-structured persuasive essays. It uses advanced AI techniques to create arguments, counterarguments, and rhetorical strategies tailored to your specific audience and purpose.


### Quick Start


#### Basic Usage

1. **Specify Your Thesis**: Provide the main argument or position you want to defend
2. **Choose Your Audience**: Select who you're trying to persuade (e.g., "general public", "academics", "policymakers")
3. **Set the Tone**: Define how formal or conversational your essay should be
4. **Run the Task**: The system will generate a complete essay with all sections


#### Minimal Configuration Example

```json
{
  "thesis": "Remote work should be the default option for knowledge workers",
  "target_audience": "business leaders",
  "tone": "formal"
}
```


### Configuration Options


#### Required Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `thesis` | String | The main argument or position to defend | "Climate change requires immediate government action" |


#### Optional Parameters


##### Content Structure

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `target_audience` | String | "general public" | Who you're persuading (e.g., "academics", "policymakers", "business leaders") |
| `tone` | String | "formal" | Writing style: "formal", "conversational", "passionate", or "analytical" |
| `target_word_count` | Integer | 1500 | Approximate total length of the essay |
| `num_arguments` | Integer | 3 | Number of main arguments to develop (1-10) |


##### Persuasive Techniques

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `include_counterarguments` | Boolean | true | Include opposing viewpoints and rebuttals |
| `use_rhetorical_devices` | Boolean | true | Use ethos, pathos, and logos techniques |
| `include_evidence` | Boolean | true | Add statistics, quotes, and expert testimony |
| `use_analogies` | Boolean | true | Include analogies and concrete examples |
| `call_to_action` | String | "strong" | Type of conclusion: "strong", "moderate", "reflective", or "none" |


##### Refinement

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `revision_passes` | Integer | 1 | Number of editing passes (0-5) for quality improvement |


##### Input Files

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `input_files` | List | null | Files to use as research (supports glob patterns like `**/*.md`) |
| `related_files` | List | null | Additional reference files to incorporate |


##### Visual Generation

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `generate_images` | Boolean | true | Generate visualizations for arguments |
| `generate_cover_image` | Boolean | true | Create a cover image for the essay |


### Configuration Examples


#### Example 1: Academic Persuasive Essay

```json
{
  "thesis": "Artificial intelligence should be regulated by international bodies",
  "target_audience": "academics",
  "tone": "analytical",
  "target_word_count": 2500,
  "num_arguments": 4,
  "include_counterarguments": true,
  "use_rhetorical_devices": true,
  "include_evidence": true,
  "revision_passes": 2,
  "call_to_action": "reflective"
}
```


#### Example 2: Business Proposal

```json
{
  "thesis": "Our company should adopt a four-day work week",
  "target_audience": "business leaders",
  "tone": "formal",
  "target_word_count": 1200,
  "num_arguments": 3,
  "include_counterarguments": true,
  "use_rhetorical_devices": true,
  "include_evidence": true,
  "call_to_action": "strong",
  "input_files": ["research/productivity_studies.md", "data/employee_surveys.md"]
}
```


#### Example 3: Passionate Opinion Piece

```json
{
  "thesis": "Social media companies must prioritize user mental health over engagement metrics",
  "target_audience": "general public",
  "tone": "passionate",
  "target_word_count": 1800,
  "num_arguments": 3,
  "include_counterarguments": true,
  "use_analogies": true,
  "call_to_action": "strong",
  "generate_images": true
}
```


### Output Structure

The task generates a complete essay with the following sections:


#### 1. **Introduction**
- Compelling hook to grab attention
- Background context
- Clear thesis statement
- Sets tone for the entire essay


#### 2. **Body Arguments**
- Multiple main arguments (configurable count)
- Supporting points for each argument
- Evidence (statistics, quotes, examples)
- Rhetorical devices (ethos, pathos, logos)
- Smooth transitions between ideas


#### 3. **Counterarguments & Rebuttals** (if enabled)
- Acknowledges opposing viewpoints fairly
- Demonstrates understanding of other perspectives
- Provides strong logical rebuttals
- Strengthens original thesis


#### 4. **Conclusion**
- Restates thesis in fresh language
- Synthesizes main arguments
- Emphasizes significance and implications
- Includes call to action (strength varies by configuration)


### Output Files

The task creates several deliverables:

| File | Format | Description |
|------|--------|-------------|
| `persuasive_essay.md` | Markdown | Complete essay with formatting |
| `persuasive_essay.html` | HTML | Web-ready version |
| `persuasive_essay.pdf` | PDF | Print-ready version |
| `transcript.md` | Markdown | Generation process log |
| `00_cover_image.png` | PNG | Cover image (if enabled) |
| `01_outline_visualization.png` | PNG | Argument structure diagram |
| `argument_N_image.png` | PNG | Visualization for each argument |
| `counterargument_image.png` | PNG | Counterargument visualization |


### Rhetorical Devices Explained

The task uses three classical persuasive techniques:


#### **Ethos** (Credibility)
- Establishes authority and trustworthiness
- Uses expert credentials and citations
- Demonstrates knowledge and experience
- *Example: "As a leading researcher in climate science..."*


#### **Pathos** (Emotion)
- Appeals to audience emotions and values
- Uses stories, examples, and vivid language
- Connects to audience concerns
- *Example: "Imagine a future where..."*


#### **Logos** (Logic)
- Uses facts, statistics, and logical reasoning
- Presents evidence and data
- Builds logical arguments
- *Example: "Studies show that 87% of..."*


### Tips for Best Results


#### 1. **Craft a Strong Thesis**
- Make it specific and debatable
- Avoid overly broad statements
- ✅ Good: "Remote work increases productivity for knowledge workers"
- ❌ Weak: "Remote work is good"


#### 2. **Match Tone to Audience**
- **Formal**: Academic, policy, business contexts
- **Conversational**: General audience, opinion pieces
- **Passionate**: Advocacy, opinion, social issues
- **Analytical**: Research, technical, academic


#### 3. **Provide Research Context**
- Use `input_files` to incorporate existing research
- Include relevant data and studies
- Supports glob patterns: `research/**/*.md`


#### 4. **Optimize Word Count**
- Set realistic targets based on complexity
- 1000-1500 words: Simple arguments
- 1500-2500 words: Complex topics with counterarguments
- 2500+ words: Comprehensive academic essays


#### 5. **Use Revision Passes**
- 1 pass: Quick polish
- 2 passes: Good quality
- 3+ passes: Highly refined (slower)


#### 6. **Enable Counterarguments**
- Strengthens credibility
- Shows balanced perspective
- More persuasive to critical audiences


### Validation Rules

The system validates your configuration:

| Rule | Error Message |
|------|---------------|
| Thesis is required | "thesis must not be null or blank" |
| Word count must be positive | "target_word_count must be positive" |
| Arguments between 1-10 | "num_arguments must be between 1 and 10" |
| Revision passes 0-5 | "revision_passes must be between 0 and 5" |
| Valid call to action | "call_to_action must be one of: strong, moderate, reflective, none" |


### Troubleshooting


#### Issue: Essay is too short
**Solution**: Increase `target_word_count` or add more arguments with `num_arguments`


#### Issue: Arguments lack evidence
**Solution**: Enable `include_evidence: true` and provide `input_files` with research


#### Issue: Tone doesn't match audience
**Solution**: Adjust `tone` parameter and ensure `target_audience` is specific


#### Issue: Generation is slow
**Solution**: Reduce `revision_passes` or decrease `target_word_count`


#### Issue: Counterarguments are weak
**Solution**: Ensure `include_counterarguments: true` and provide sufficient research context


### Advanced Usage


#### Incorporating Research Files

```json
{
  "thesis": "Universal basic income reduces poverty effectively",
  "input_files": [
    "research/ubi_studies.md",
    "data/pilot_programs/**/*.md",
    "reports/economic_impact.md"
  ],
  "related_files": [
    "background/poverty_statistics.md",
    "context/current_policies.md"
  ]
}
```


#### Multi-Pass Revision for Publication

```json
{
  "thesis": "Your thesis here",
  "revision_passes": 3,
  "tone": "formal",
  "target_audience": "academics",
  "include_evidence": true,
  "use_rhetorical_devices": true
}
```


#### Balanced Persuasion

```json
{
  "thesis": "Your thesis here",
  "include_counterarguments": true,
  "call_to_action": "moderate",
  "tone": "analytical",
  "use_rhetorical_devices": true
}
```


### Performance Metrics

Typical generation times:

| Configuration | Time | Word Count |
|---------------|------|-----------|
| Basic (3 args, no revisions) | 30-45s | 1200-1500 |
| Standard (3 args, 1 revision) | 45-60s | 1500-1800 |
| Comprehensive (4 args, 2 revisions) | 60-90s | 2000-2500 |
| With images | +30-60s | Same |


### Best Practices

1. **Start Simple**: Begin with basic configuration, then add features
2. **Provide Context**: Use input files for better evidence integration
3. **Review Output**: Check generated essay for accuracy and tone
4. **Iterate**: Use revision passes for important essays
5. **Customize**: Adjust parameters based on your specific needs
6. **Validate**: Ensure thesis is clear and debatable before running


### Support & Feedback

For issues or suggestions:
- Check the transcript file for generation details
- Review validation error messages
- Adjust configuration based on feedback
- Consult the examples above for similar use cases

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\social\PoliticalOptimizationTask.kt


## PoliticalOptimizationTask User Documentation


### Overview

The **PoliticalOptimizationTask** is an advanced text optimization tool that evaluates and refines content from multiple political perspectives simultaneously. It uses evolutionary algorithms to generate text variants that either maximize consensus across perspectives or identify divisive language patterns.


### Use Cases


#### 1. **Crafting Bipartisan Messaging**
- Create political communications that appeal across the political spectrum
- Find common ground language that resonates with diverse audiences
- Identify and eliminate unnecessarily divisive framing


#### 2. **Understanding Political Polarization**
- Discover which topics and language create the most division
- Identify "wedge issues" that naturally polarize audiences
- Analyze how different perspectives interpret the same message


#### 3. **Message Testing & Optimization**
- Test how your message performs with different political viewpoints
- Refine language to improve reception across perspectives
- Track which communication strategies work best for each audience


#### 4. **Political Research & Analysis**
- Study how framing affects political perception
- Identify perspective-specific language preferences
- Analyze consensus vs. divisiveness in political discourse


### Configuration Guide


#### Required Parameters


##### **Initial Text**
The text you want to analyze and optimize.

**Example:**
```
We need to invest in renewable energy to address climate change 
while creating jobs in clean technology sectors.
```


##### **Optimization Goal**
A clear statement of what you're trying to achieve.

**Examples:**
- "Maximize consensus on climate policy across political perspectives"
- "Identify divisive language in healthcare messaging"
- "Create bipartisan infrastructure proposal language"


##### **Perspectives**
List of political viewpoints to evaluate from. The task evaluates your text from each perspective independently.

**Default perspectives:**
- `progressive` - Left-leaning, emphasizes social programs and environmental protection
- `conservative` - Right-leaning, emphasizes fiscal responsibility and traditional values
- `libertarian` - Emphasizes individual liberty and limited government
- `centrist` - Seeks middle ground and pragmatic solutions

**Custom perspectives:**
You can add any perspective you want to evaluate:
- `socialist`, `anarchist`, `monarchist`
- `nationalist`, `globalist`, `isolationist`
- `religious`, `secular`, `theocratic`
- Industry-specific: `corporate`, `labor`, `environmental`


#### Evaluation Criteria

Dimensions on which each perspective evaluates your text.

**Default criteria:**
- `clarity` - How clear and understandable is the message?
- `persuasiveness` - How convincing is it from this perspective?
- `factual_accuracy` - How factually sound does it appear?
- `emotional_appeal` - How emotionally resonant is it?

**Custom criteria examples:**
- `fairness`, `practicality`, `innovation`, `tradition`
- `cost-effectiveness`, `environmental_impact`, `social_justice`
- `individual_freedom`, `collective_benefit`, `economic_growth`


#### Consensus Mode

Determines the optimization direction.


##### **Maximize** (Default for consensus-building)
- Finds text that achieves highest agreement across perspectives
- Minimizes divisiveness
- Best for: bipartisan messaging, inclusive communications


##### **Minimize** (For divisiveness analysis)
- Identifies text that creates maximum disagreement
- Finds "wedge issues" and polarizing language
- Best for: understanding polarization, political research


##### **Explore** (Balanced analysis)
- Generates both unifying and divisive variants
- Provides comprehensive perspective analysis
- Best for: understanding full spectrum of reactions


#### Advanced Parameters


##### **Number of Generations** (default: 5)
How many evolutionary cycles to run. More generations = better optimization but longer runtime.

- 3-5: Quick analysis
- 5-10: Balanced optimization
- 10+: Deep optimization (slower)


##### **Population Size** (default: 8)
Number of text variants to generate per generation.

- Larger = more diversity but slower
- Smaller = faster but less exploration


##### **Selection Size** (default: 3)
How many top variants to keep for breeding next generation.

- Higher = more conservative (keep best)
- Lower = more experimental (allow weaker variants)


##### **Mutation Strategies**
How variants are modified. Available strategies:

- `rephrase` - Change wording while maintaining meaning
- `emphasize` - Strengthen certain points
- `soften` - Make tone more moderate
- `reframe` - Change perspective or framing
- `polarize` - Make more appealing to specific perspectives
- `bridge` - Add connecting language between perspectives


##### **Enable Crossover** (default: true)
Whether to combine successful variants from different parents.


##### **Consensus Weight** (default: 0.6)
Balance between consensus and quality in fitness calculation.

- 0.0 = Only care about quality
- 0.5 = Equal weight
- 1.0 = Only care about consensus


### Understanding the Results


#### Consensus Score

**Range:** -100 to +100

- **Positive scores** (0 to +100): Text creates agreement across perspectives
  - +80 to +100: Strong consensus, unifying language
  - +40 to +80: Moderate consensus, generally acceptable
  - 0 to +40: Weak consensus, some disagreement

- **Negative scores** (-100 to 0): Text creates disagreement
  - -40 to 0: Weak divisiveness, minor disagreements
  - -80 to -40: Strong divisiveness, significant disagreement
  - -100 to -80: Extreme divisiveness, polarizing language

- **Zero:** Perfectly balanced disagreement


#### Score Variance

Measures how much perspectives disagree with each other.

- **Low variance** (0-15): Perspectives largely agree
- **Medium variance** (15-30): Some disagreement
- **High variance** (30+): Significant disagreement


#### Wedge Issue Indicator

Text is flagged as a "wedge issue" when variance exceeds 25, meaning it naturally polarizes audiences.


#### Common Ground

Strengths or aspects that multiple perspectives appreciate. These are the unifying elements of your text.


#### Points of Contention

Aspects that create disagreement. These are where perspectives diverge.


### Output Tabs


#### **Overview**
- Configuration summary
- Initial evaluation results
- Generation-by-generation progress
- Final metrics


#### **Generation N** (one per generation)
- Population statistics for that generation
- Best unifying and divisive variants
- Perspective-by-perspective breakdown
- Evolution of consensus


#### **Consensus Analysis**
- Most unifying text variant found
- Common ground across perspectives
- Detailed scores from each perspective
- Strengths and weaknesses by perspective


#### **Divisiveness Analysis**
- Most divisive text variant found
- Points of contention
- Polarization analysis
- Which perspectives favor/oppose the text


#### **Evolution**
- Consensus progression across generations
- Strategy effectiveness comparison
- Perspective-specific trends
- How different mutation strategies performed


### Practical Examples


#### Example 1: Bipartisan Healthcare Message

**Configuration:**
- Initial text: Your current healthcare proposal
- Goal: "Create healthcare messaging that appeals across political spectrum"
- Perspectives: progressive, conservative, libertarian, centrist
- Criteria: clarity, fairness, cost-effectiveness, practicality
- Mode: maximize
- Generations: 5

**Expected output:** Text that emphasizes shared values (access, quality, affordability) while avoiding partisan language.


#### Example 2: Identifying Polarizing Language

**Configuration:**
- Initial text: Political speech or policy proposal
- Goal: "Identify which language creates the most division"
- Perspectives: progressive, conservative, libertarian, centrist
- Criteria: persuasiveness, emotional_appeal, fairness
- Mode: minimize
- Generations: 3

**Expected output:** Variants showing which phrases/framings are most divisive and why.


#### Example 3: Understanding Perspective Differences

**Configuration:**
- Initial text: Any political statement
- Goal: "Understand how different perspectives interpret this message"
- Perspectives: progressive, conservative, libertarian, centrist, socialist
- Criteria: clarity, factual_accuracy, alignment_with_values
- Mode: explore
- Generations: 5

**Expected output:** Comprehensive analysis of how each perspective views the text differently.


### Tips for Best Results


#### 1. **Start with Clear Text**
- Use well-written, grammatically correct initial text
- Ambiguous text produces ambiguous results
- Specific claims are easier to optimize than vague statements


#### 2. **Choose Relevant Perspectives**
- Include perspectives that actually matter for your audience
- Too many perspectives = slower, less focused results
- 3-5 perspectives is usually optimal


#### 3. **Define Specific Criteria**
- Generic criteria produce generic results
- Use criteria relevant to your goal
- Mix objective (clarity, accuracy) and subjective (persuasiveness, appeal) criteria


#### 4. **Adjust Consensus Weight**
- For consensus-building: use 0.6-0.8
- For divisiveness analysis: use 0.3-0.5
- For balanced analysis: use 0.5


#### 5. **Iterate**
- Run multiple times with different configurations
- Use results to refine your approach
- Combine insights from different runs


#### 6. **Validate Results**
- Test optimized text with real audiences
- Consensus scores are predictions, not guarantees
- Real-world reception may differ from AI evaluation


### Limitations & Considerations


#### AI Perspective Simulation
- Perspectives are AI-generated approximations
- May not perfectly represent real people's views
- Use as analytical tool, not definitive truth


#### Language Bias
- AI models have inherent biases
- Some perspectives may be better represented than others
- Results reflect training data limitations


#### Complexity
- Very long texts may be harder to optimize
- Highly technical content may need domain-specific criteria
- Nuanced topics may require custom perspectives


#### Time & Cost
- More generations = longer runtime
- Larger populations = more API calls
- Balance thoroughness with practical constraints


### Troubleshooting


#### Low Consensus Scores
- Topic may be inherently divisive
- Try different mutation strategies
- Adjust perspectives to find common ground
- Increase generations for deeper optimization


#### Unexpected Results
- Verify your perspectives are well-defined
- Check that criteria are relevant
- Review the detailed perspective analysis
- Try with different initial text


#### Slow Performance
- Reduce number of generations
- Decrease population size
- Use fewer perspectives
- Simplify evaluation criteria


### Advanced Usage


#### Custom Perspective Definition
When creating custom perspectives, be specific:
- ❌ "business" (too vague)
- ✅ "small_business_owner" (specific viewpoint)


#### Combining Multiple Runs
1. Run with "maximize" mode to find consensus language
2. Run with "minimize" mode to identify divisive elements
3. Run with "explore" mode for comprehensive analysis
4. Compare results to understand the full spectrum


#### Iterative Refinement
1. Start with broad perspectives
2. Analyze results
3. Add specialized perspectives based on findings
4. Run again with refined configuration

---

**Need help?** Review the detailed analysis tabs for perspective-specific feedback and evolution metrics to understand how the optimization progressed.