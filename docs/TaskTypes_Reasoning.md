# Reasoning

## AbductiveReasoning

Generate and evaluate explanatory hypotheses

Performs abductive reasoning (inference to best explanation) to generate and evaluate hypotheses.
<ul>
  <li>Generates multiple explanatory hypotheses for observations</li>
  <li>Evaluates explanatory power, simplicity, testability, and prior probability</li>
  <li>Applies Occam's Razor to prefer simpler explanations</li>
  <li>Ranks hypotheses by overall quality</li>
  <li>Suggests validation tests for top hypotheses</li>
  <li>Useful for root cause analysis, bug investigation, and scientific reasoning</li>
</ul>

#### Planner Prompt Segment

```text
AbductiveReasoning - Generate and evaluate explanatory hypotheses
  ** Specify observations that need explanation
  ** Configure hypothesis generation (max_hypotheses: 5)
  ** Select evaluation criteria: explanatory_power, simplicity, testability, prior_probability
  ** Optionally provide existing hypotheses to evaluate
  ** Optionally suggest tests to validate hypotheses
  ** Useful for:
     - Root cause analysis
     - Bug investigation
     - Understanding anomalies
     - Scientific reasoning
     - Inference to best explanation
```

#### Default Execution Configuration

```json
{
  "task_type" : "AbductiveReasoning",
  "observations" : null,
  "generate_hypotheses" : true,
  "max_hypotheses" : 5,
  "input_files" : null,
  "evaluate_criteria" : [ "explanatory_power", "simplicity", "testability", "prior_probability" ],
  "suggest_tests" : true,
  "existing_hypotheses" : null,
  "domain_context" : null,
  "task_description" : "Generate and evaluate explanatory hypotheses for 0 observations",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "AbductiveReasoning"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "AbductiveReasoning",
  "name" : "AbductiveReasoning",
  "model" : null
}
```

---

## AbstractionLadder

Traverse abstraction levels to identify patterns and design insights

Analyzes concepts by moving up and down abstraction levels.
<ul>
  <li>Move up to find generalizations and patterns</li>
  <li>Move down to find specific implementations</li>
  <li>Identify design patterns at each level</li>
  <li>Discover refactoring opportunities</li>
  <li>Analyze architectural patterns</li>
  <li>Find code smells and anti-patterns</li>
  <li>Generate actionable recommendations</li>
</ul>

#### Planner Prompt Segment

```text
AbstractionLadder - Traverse abstraction levels to find patterns and design insights
  ** Specify the concrete concept or problem to analyze
  ** Choose direction: 'up' (generalize), 'down' (concretize), or 'both'
  ** Set number of levels to traverse (1-5 recommended)
  ** Enable pattern identification to discover:
     - Design patterns and anti-patterns
     - Refactoring opportunities
     - Architectural insights
     - Code smells and improvements
  ** Related files provide context for analysis
  ** Output includes:
     - Abstraction hierarchy visualization
     - Pattern analysis at each level
     - Concrete examples and generalizations
     - Refactoring recommendations
```

#### Default Execution Configuration

```json
{
  "task_type" : "AbstractionLadder",
  "concrete_concept" : null,
  "direction" : "both",
  "levels" : 3,
  "identify_patterns" : true,
  "input_files" : null,
  "related_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "AbstractionLadder"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "AbstractionLadder",
  "name" : "AbstractionLadder",
  "model" : null
}
```

---

## AdversarialReasoning

Red team analysis to identify vulnerabilities and weaknesses

Performs adversarial reasoning and red team analysis on systems, designs, or arguments.
<ul>
  <li>Identifies security vulnerabilities and attack vectors</li>
  <li>Challenges assumptions aggressively</li>
  <li>Finds edge cases and failure modes</li>
  <li>Simulates adversarial scenarios at different capability levels</li>
  <li>Stress tests logical arguments and system designs</li>
  <li>Generates detailed vulnerability reports with severity ratings</li>
  <li>Optionally provides exploit scenarios and mitigation strategies</li>
  <li>Supports multiple attack vectors: security, performance, logic, business, privacy, compliance</li>
</ul>

#### Planner Prompt Segment

```text
AdversarialReasoning - Red team analysis to identify vulnerabilities and weaknesses
  ** Specify target_system: the system, design, or argument to analyze
  ** Choose attack_vectors from: 'security', 'performance', 'logic', 'business', 'privacy', 'compliance'
  ** Set adversary_capability: 'basic', 'intermediate', 'advanced', 'nation-state'
  ** Enable generate_exploits for detailed attack scenarios (use with caution)
  ** Enable suggest_mitigations to get defensive recommendations
  ** Optionally specify related_files (glob patterns) to analyze code
  ** Optionally list challenge_assumptions to target specific beliefs
  ** Identifies vulnerabilities, edge cases, and failure modes
  ** Simulates adversarial thinking to stress test systems
  ** Produces structured vulnerability reports with severity ratings
```

#### Default Execution Configuration

```json
{
  "task_type" : "AdversarialReasoning",
  "target_system" : null,
  "attack_vectors" : [ "security", "logic" ],
  "adversary_capability" : "intermediate",
  "generate_exploits" : false,
  "suggest_mitigations" : true,
  "related_files" : null,
  "input_files" : null,
  "challenge_assumptions" : null,
  "max_vulnerabilities_per_vector" : 5,
  "task_description" : "Red team analysis of 'null' with 2 attack vectors",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "AdversarialReasoning"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "AdversarialReasoning",
  "name" : "AdversarialReasoning",
  "model" : null
}
```

---

## AnalogicalReasoning

Solve problems by finding and applying analogies from different domains

Performs creative problem-solving through analogical reasoning.
<ul>
  <li>Draws analogies from specified source domains</li>
  <li>Maps structural relationships to target problems</li>
  <li>Generates multiple perspectives and insights</li>
  <li>Validates mapping coherence and consistency</li>
  <li>Synthesizes findings across analogies</li>
  <li>Suggests concrete solutions based on analogies</li>
  <li>Useful for design thinking and novel approaches</li>
</ul>

#### Planner Prompt Segment

```text
AnalogicalReasoning - Solve problems by finding and applying analogies from different domains
  ** Specify a source domain to draw analogies from (e.g., biological systems, architecture, music)
  ** Provide the target problem you want to solve
  ** Configure the number of analogies to generate (default: 3)
  ** Optionally enable mapping validation for structural consistency
  ** The task will:
     - Identify relevant concepts in the source domain
     - Map structural relationships to the target problem
     - Generate insights and potential solutions
     - Validate the coherence of the analogical mappings
     - Synthesize findings across multiple analogies
  ** Useful for creative problem-solving, design thinking, and novel approaches
  ** Can reference related files for additional context
```

#### Default Execution Configuration

```json
{
  "task_type" : "AnalogicalReasoning",
  "source_domain" : null,
  "target_problem" : null,
  "num_analogies" : 3,
  "validate_mappings" : true,
  "related_files" : null,
  "input_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "AnalogicalReasoning"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "AnalogicalReasoning",
  "name" : "AnalogicalReasoning",
  "model" : null
}
```

---

## Brainstorming

Generate and analyze multiple solution options

Systematically generates diverse options and analyzes each independently.
<ul>
  <li>Generates multiple solution options for a given problem</li>
  <li>Analyzes each option independently (pros, cons, feasibility, impact, risks)</li>
  <li>Provides comparative summary with recommendations</li>
  <li>Supports creative and conventional approaches</li>
  <li>Configurable analysis depth and option count</li>
  <li>Identifies hybrid approaches and synergies</li>
  <li>Useful for decision making, strategic planning, and problem solving</li>
</ul>

#### Planner Prompt Segment

```text
Brainstorming - Generate and analyze multiple solution options
  ** Specify the problem or question to brainstorm solutions for
  ** Configure target number of options (default: 7)
  ** Optionally specify categories or domains to explore
  ** Define constraints or requirements
  ** Enable/disable creative/unconventional options
  ** Set analysis depth (brief/moderate/detailed)
  ** Generates diverse options, analyzes each independently
  ** Provides comparative summary with recommendations
  ** Useful for:
     - Solution exploration
     - Decision making
     - Strategic planning
     - Problem solving
```

#### Default Execution Configuration

```json
{
  "task_type" : "Brainstorming",
  "problem_statement" : null,
  "input_files" : null,
  "target_option_count" : 7,
  "categories" : null,
  "constraints" : null,
  "include_creative_options" : true,
  "analysis_depth" : "moderate",
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "Brainstorming"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "Brainstorming",
  "name" : "Brainstorming",
  "model" : null
}
```

---

## CausalInference

Identify causal relationships and root causes

Performs causal inference analysis to identify true causal relationships.
<ul>
  <li>Distinguishes causation from correlation</li>
  <li>Identifies root causes vs intermediate factors</li>
  <li>Builds causal graphs showing relationships</li>
  <li>Identifies confounding variables</li>
  <li>Provides evidence-based causal reasoning</li>
  <li>Useful for debugging and root cause analysis</li>
</ul>

#### Planner Prompt Segment

```text
CausalInference - Identify causal relationships and root causes
  ** Specify the observed effect or outcome to explain
  ** List potential causes to investigate
  ** Optionally build a causal graph showing relationships
  ** Optionally identify confounding factors
  ** Provide evidence sources (logs, metrics, code files)
  ** Optionally, list input files (supports glob patterns) to be examined
  ** Useful for:
     - Root cause analysis
     - Debugging complex issues
     - Understanding system behavior
     - Distinguishing correlation from causation
```

#### Default Execution Configuration

```json
{
  "task_type" : "CausalInference",
  "observed_effect" : null,
  "potential_causes" : null,
  "build_causal_graph" : true,
  "identify_confounders" : true,
  "evidence_sources" : null,
  "input_files" : null,
  "related_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "CausalInference"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "CausalInference",
  "name" : "CausalInference",
  "model" : null
}
```

---

## ChainOfThought

Break down complex problems into explicit reasoning steps

Performs step-by-step reasoning with validation:
<ul>
  <li>Breaks complex problems into logical steps</li>
  <li>Validates each step before proceeding</li>
  <li>Provides reasoning transparency</li>
  <li>Can backtrack if validation fails</li>
  <li>Generates comprehensive reasoning chains</li>
</ul>

#### Planner Prompt Segment

```text
ChainOfThought - Break down complex problems into explicit reasoning steps
 ** Optionally, list input files (supports glob patterns) to be examined for context
 ** Specify the problem statement that requires step-by-step reasoning
 ** Optionally set reasoning_depth to control the number of steps (default: auto)
 ** Enable validate_steps to validate each step before proceeding (default: true)
 ** Related files can provide additional context for reasoning
 ** Each step will be:
    - Generated with explicit reasoning
    - Validated for logical consistency
    - Used as context for the next step
 ** The task will backtrack if validation fails
 ** Final output includes the complete reasoning chain and conclusion
```

#### Default Execution Configuration

```json
{
  "task_type" : "ChainOfThought",
  "problem_statement" : "",
  "reasoning_depth" : 10,
  "validate_steps" : true,
  "related_files" : [ ],
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ChainOfThought",
  "task_description" : ""
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ChainOfThought",
  "name" : "ChainOfThought",
  "model" : null
}
```

---

## ConstraintRelaxation

Solve over-constrained problems through progressive constraint relaxation

Solves complex problems by temporarily relaxing constraints and progressively reintroducing them.
<ul>
  <li>Identifies which constraints to initially relax based on priority</li>
  <li>Solves simplified problem without relaxed constraints</li>
  <li>Progressively reintroduces constraints in configurable order</li>
  <li>Adapts solution at each step to satisfy new constraints</li>
  <li>Finds creative ways to satisfy multiple constraints simultaneously</li>
  <li>Supports multiple relaxation strategies (progressive, selective, hierarchical)</li>
  <li>Configurable reintroduction order (by priority, difficulty, or dependency)</li>
  <li>Useful for over-constrained problems, algorithm design, and architecture under constraints</li>
</ul>

#### Planner Prompt Segment

```text
ConstraintRelaxation - Solve over-constrained problems through progressive constraint relaxation
  ** Specify the problem to solve
  ** Define constraints with priority weights (0.0-1.0, where 1.0 is critical)
  ** Choose relaxation strategy:
     - 'progressive': Gradually relax constraints from lowest to highest priority
     - 'selective': Intelligently select which constraints to relax
     - 'hierarchical': Relax constraints in priority-based levels
  ** Choose reintroduction order:
     - 'by_priority': Reintroduce highest priority constraints first
     - 'by_difficulty': Reintroduce easiest constraints first
     - 'by_dependency': Reintroduce based on constraint dependencies
  ** Enable creative satisfaction finding to discover novel solutions
  ** Produces a solution that progressively satisfies constraints
  ** Shows evolution of solution as constraints are reintroduced
  ** Optionally, list input files (supports glob patterns) to be examined for context
```

#### Default Execution Configuration

```json
{
  "task_type" : "ConstraintRelaxation",
  "problem" : null,
  "constraints" : null,
  "relaxation_strategy" : "progressive",
  "reintroduction_order" : "by_priority",
  "find_creative_satisfactions" : true,
  "max_iterations" : 5,
  "input_files" : null,
  "related_files" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ConstraintRelaxation",
  "task_description" : "Solve 'null' through progressive constraint relaxation"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ConstraintRelaxation",
  "name" : "ConstraintRelaxation",
  "model" : null
}
```

---

## ConstraintSatisfaction

Solve problems with multiple competing constraints

Solves constraint satisfaction problems with hard and soft constraints.
<ul>
  <li>Handles hard constraints that must be satisfied</li>
  <li>Optimizes soft constraints with configurable weights</li>
  <li>Supports multiple search strategies (backtracking, forward, local)</li>
  <li>Provides detailed reasoning and trade-off analysis</li>
  <li>Suggests alternative solutions when applicable</li>
  <li>Useful for architectural decisions, resource allocation, and optimization</li>
</ul>

#### Planner Prompt Segment

```text
ConstraintSatisfaction - Solve problems with multiple competing constraints
 ** problem_description: The problem requiring constraint satisfaction
 ** input_files: List of files or glob patterns to use as input
 ** hard_constraints: List of constraints that must be satisfied
 ** soft_constraints: Map of constraints to optimize with weights (0.0-1.0)
 ** search_strategy: 'backtracking', 'forward', or 'local'
 ** max_iterations: Maximum search iterations
```

#### Default Execution Configuration

```json
{
  "task_type" : "ConstraintSatisfaction",
  "problem_description" : null,
  "input_files" : null,
  "hard_constraints" : null,
  "soft_constraints" : null,
  "search_strategy" : "backtracking",
  "max_iterations" : 100,
  "related_files" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ConstraintSatisfaction",
  "task_description" : null
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ConstraintSatisfaction",
  "name" : "ConstraintSatisfaction",
  "model" : null
}
```

---

## CounterfactualAnalysis

Explore what-if scenarios to understand causal relationships and decision impacts

Performs counterfactual analysis to explore alternative scenarios and outcomes.
<ul>
  <li>Analyzes actual scenarios and alternative conditions</li>
  <li>Compares outcomes across different scenarios</li>
  <li>Identifies causal relationships and key factors</li>
  <li>Supports controlled comparison with constant factors</li>
  <li>Provides insights for risk analysis and decision validation</li>
  <li>Useful for retrospective analysis and strategic planning</li>
</ul>

#### Planner Prompt Segment

```text
CounterfactualAnalysis - Explore "what-if" scenarios to understand causal relationships and decision impacts
  ** Specify the actual scenario or decision that occurred
  ** Provide a list of alternative conditions to explore (counterfactuals)
  ** Optionally specify factors to hold constant across scenarios for controlled comparison
  ** Enable outcome comparison to see differences between scenarios
  ** Useful for:
     - Risk analysis and mitigation planning
     - Decision validation and retrospective analysis
     - Understanding causal relationships
     - Exploring alternative strategies
     - Impact assessment of different choices
  ** Related files can include historical data, previous analyses, or context documents
  ** Output includes detailed analysis of each scenario and comparative insights
```

#### Default Execution Configuration

```json
{
  "task_type" : "CounterfactualAnalysis",
  "actual_scenario" : null,
  "counterfactuals" : null,
  "compare_outcomes" : true,
  "control_factors" : null,
  "related_files" : null,
  "input_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "CounterfactualAnalysis"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "CounterfactualAnalysis",
  "name" : "CounterfactualAnalysis",
  "model" : null
}
```

---

## DecisionTree

Build an LLM-driven symbolic decision tree

Constructs a decision tree classifier using LLM for rule proposal and data for validation.
<ul>
  <li>Handles unstructured text via semantic rules</li>
  <li>Generates interpretable code</li>
  <li>Uses Information Gain for split selection</li>
</ul>

#### Planner Prompt Segment

```text
DecisionTree - Build an LLM-driven symbolic decision tree
 ** Specify the data file (CSV)
 ** Specify the target column to predict
 ** Configure max depth and candidate rules
 ** Uses LLM to propose semantic splitting rules
 ** Validates rules using Information Gain
 ** Generates executable code
```

#### Default Execution Configuration

```json
{
  "task_type" : "DecisionTree",
  "data_file" : null,
  "target_column" : null,
  "max_depth" : 3,
  "candidate_rules" : 5,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "DecisionTree",
  "task_description" : "Build a decision tree for 'null' from 'null'"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "DecisionTree",
  "name" : "DecisionTree",
  "model" : null
}
```

---

## DecompositionSynthesis

Decompose complex problems and synthesize solutions

Decomposes complex problems into manageable subproblems, solves them, and synthesizes solutions.
<ul>
  <li>Multiple decomposition strategies (functional, temporal, spatial, hierarchical)</li>
  <li>Configurable decomposition depth</li>
  <li>Dependency-aware subproblem solving</li>
  <li>Solution synthesis with coherence validation</li>
  <li>Confidence tracking at each level</li>
  <li>Implements divide-and-conquer reasoning</li>
</ul>

#### Planner Prompt Segment

```text
DecompositionSynthesis - Break down complex problems into subproblems and synthesize integrated solutions
 ** Optionally, list input files (supports glob patterns) to be examined for context
 ** Problem: Not specified
 ** Specify the complex problem to decompose
 ** Choose decomposition strategy:
    - functional: Break down by function/capability
    - temporal: Break down by time/sequence
    - spatial: Break down by location/component
    - hierarchical: Break down by abstraction level
 ** Set maximum decomposition depth (default: 3)
 ** Enable solution synthesis to combine subproblem solutions
 ** Enable coherence validation to check solution consistency
 ** Related files can provide context for the problem
 ** Output: Comprehensive solution with decomposition analysis, subproblem solutions, and synthesis
 ** Returns: Final synthesized solution or concatenated subproblem solutions
 ** Implements divide-and-conquer reasoning approach
```

#### Default Execution Configuration

```json
{
  "task_type" : "DecompositionSynthesis",
  "input_files" : null,
  "include_file_context" : true,
  "complex_problem" : null,
  "decomposition_strategy" : "functional",
  "max_depth" : 3,
  "synthesize_solution" : true,
  "validate_coherence" : true,
  "related_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "DecompositionSynthesis"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "DecompositionSynthesis",
  "name" : "DecompositionSynthesis",
  "model" : null
}
```

---

## DialecticalReasoning

Resolve contradictions through thesis-antithesis-synthesis

Applies dialectical reasoning to resolve contradictions and find higher-level synthesis.
<ul>
  <li>Analyzes thesis and antithesis positions thoroughly</li>
  <li>Explores contradictions and tensions between positions</li>
  <li>Generates synthesis that transcends opposition</li>
  <li>Iterates through multiple synthesis levels for deeper understanding</li>
  <li>Preserves valuable aspects from both sides</li>
  <li>Provides final integration with practical implications</li>
  <li>Useful for architectural debates, requirement conflicts, and design philosophy</li>
</ul>

#### Planner Prompt Segment

```text
DialecticalReasoning - Resolve contradictions through thesis-antithesis-synthesis
  ** Specify thesis and antithesis statements representing opposing positions
  ** Provide context to ground the dialectical analysis
  ** Configure synthesis_levels (1-5) to iterate toward higher understanding
  ** Set preserve_strengths=true to maintain valuable aspects of both sides
  ** Related files can provide additional context
  ** Explores contradictions and tensions between positions
  ** Generates synthesis that transcends opposition
  ** Iterates to progressively higher levels of understanding
  ** Produces structured dialectical analysis with final synthesis
```

#### Default Execution Configuration

```json
{
  "task_type" : "DialecticalReasoning",
  "thesis" : null,
  "antithesis" : null,
  "context" : null,
  "synthesis_levels" : 3,
  "preserve_strengths" : true,
  "input_files" : null,
  "related_files" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "DialecticalReasoning",
  "task_description" : "Dialectical analysis: 'null' vs 'null'"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "DialecticalReasoning",
  "name" : "DialecticalReasoning",
  "model" : null
}
```

---

## EthicalReasoning

Analyze a dilemma through multiple ethical frameworks to guide decision-making.

Provides a structured analysis of a complex ethical problem or decision.
<ul>
  <li>Evaluates a dilemma from the perspectives of several established ethical frameworks (e.g., Utilitarianism, Deontology, Virtue Ethics).</li>
  <li>For each framework, it assesses the situation, applies the framework's core principles, and determines a recommended course of action.</li>
  <li>Synthesizes these findings to provide a comprehensive recommendation, highlighting points of convergence, divergence, and the ethical trade-offs involved.</li>
  <li>Useful for AI safety, product development, policy making, and corporate governance.</li>
  <li>Generates a downloadable transcript in markdown, HTML, and PDF formats.</li>
</ul>

#### Planner Prompt Segment

```text
EthicalReasoning - Analyze a dilemma through multiple ethical frameworks
 ** Optionally specify input files (supports glob patterns) to provide context
 ** Files will be read and included in the analysis
 ** Specify the ethical dilemma and stakeholders
 ** Specify the ethical dilemma and stakeholders
 ** Choose from frameworks: utilitarianism, deontology, virtue_ethics, care_ethics, rights_based
 ** Provides analysis from each framework's perspective
 ** Synthesizes findings into a balanced recommendation
 ** Highlights ethical trade-offs and points of conflict
 ** Useful for:
    - AI safety and alignment
    - Product and policy ethics
    - Corporate governance
```

#### Default Execution Configuration

```json
{
  "task_type" : "EthicalReasoning",
  "ethical_dilemma" : null,
  "input_files" : null,
  "stakeholders" : null,
  "ethical_frameworks" : [ "utilitarianism", "deontology", "virtue_ethics" ],
  "context" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "EthicalReasoning",
  "task_description" : "Analyze ethical dilemma: null"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "EthicalReasoning",
  "name" : "EthicalReasoning",
  "model" : null
}
```

---

## FiniteStateMachine

Model concepts using finite state machine analysis

Analyzes concepts, systems, or processes using finite state machine modeling.
<ul>
  <li>Identifies all possible states and their properties</li>
  <li>Maps state transitions and triggering events</li>
  <li>Generates visual state diagrams</li>
  <li>Identifies edge cases and error states</li>
  <li>Validates FSM properties (determinism, completeness, reachability)</li>
  <li>Generates comprehensive test scenarios</li>
  <li>Useful for system design, protocol analysis, and workflow validation</li>
</ul>

#### Planner Prompt Segment

```text
FiniteStateMachine - Model concepts using finite state machine analysis
  ** Specify the concept, system, or process to model
  ** Optionally provide initial states and known events
  ** Identify all possible states and transitions
  ** Detect edge cases and error states
  ** Validate FSM properties (determinism, completeness, reachability)
  ** Generate test scenarios for state transitions
  ** Produces state diagram and transition table
  ** Useful for:
     - System design and validation
     - Understanding complex workflows
     - Identifying missing requirements
     - Test case generation
     - Protocol analysis
```

#### Default Execution Configuration

```json
{
  "task_type" : "FiniteStateMachine",
  "concept_to_model" : null,
  "initial_states" : null,
  "known_events" : null,
  "identify_edge_cases" : true,
  "validate_properties" : true,
  "generate_test_scenarios" : true,
  "domain_context" : null,
  "input_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "FiniteStateMachine"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FiniteStateMachine",
  "name" : "FiniteStateMachine",
  "model" : null
}
```

---

## FunctorialMapping

Solve complex problems by abstracting them into Category Theory and mapping them to domains with superior tools.

This task implements the logic of Category Theory. It treats domains as "Categories" (collections of objects and arrows/morphisms).
The goal is to construct a "Functor"—a bridge that allows you to transport a difficult problem from Domain A to Domain B, solve it there, and transport the solution back.
<ul>
  <li>Formalize source and target domains as Categories</li>
  <li>Construct a Functor F mapping objects and morphisms</li>
  <li>Transport the problem statement via F</li>
  <li>Solve the problem in the target category</li>
  <li>Inverse transport the solution back to the source</li>
</ul>

#### Planner Prompt Segment

```text
FunctorialMapping - Translate problems from one category to another
  * problem_statement: The specific problem in the Source Category.
  * source_category_definition: The rules/objects of the current domain.
  * target_category_definition: The rules/objects of the destination domain.
  * functor_properties: Constraints on the mapping (e.g., 'covariant').
  * Use this for high-level reasoning, cross-domain analogies, and mathematical problem solving.
```

#### Default Execution Configuration

```json
{
  "task_type" : "FunctorialMapping",
  "problem_statement" : null,
  "source_category_definition" : null,
  "target_category_definition" : null,
  "functor_properties" : "covariant",
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "FunctorialMapping"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FunctorialMapping",
  "name" : "FunctorialMapping",
  "model" : null
}
```

---

## GameTheory

Analyze strategic interactions using game theory

Performs comprehensive game theory analysis of strategic situations.
<ul>
  <li>Analyzes game structure and player strategies</li>
  <li>Constructs payoff matrices for strategy combinations</li>
  <li>Identifies Nash equilibria (pure and mixed strategies)</li>
  <li>Analyzes dominant and dominated strategies</li>
  <li>Finds Pareto optimal outcomes</li>
  <li>Supports repeated game analysis with trigger strategies</li>
  <li>Provides strategic recommendations for each player</li>
  <li>Handles cooperative, non-cooperative, zero-sum, and sequential games</li>
  <li>Useful for competitive analysis, negotiation, and strategic planning</li>
</ul>

#### Planner Prompt Segment

```text
GameTheory - Analyze strategic interactions using game theory
  ** Specify the strategic situation or game scenario
  ** Define players and their available strategies
  ** Choose game type: cooperative, non-cooperative, zero-sum, repeated, sequential
  ** Optionally build payoff matrices
  ** Identify Nash equilibria and dominant strategies
  ** Find Pareto optimal outcomes
  ** Provide strategic recommendations for each player
  ** Analyze repeated games with multiple iterations
  ** Useful for:
     - Strategic decision making
     - Competitive analysis
     - Negotiation planning
     - Market strategy
     - Conflict resolution
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameTheory",
  "game_scenario" : null,
  "players" : null,
  "player_strategies" : null,
  "game_type" : "non-cooperative",
  "build_payoff_matrix" : true,
  "find_nash_equilibria" : true,
  "analyze_dominant_strategies" : true,
  "find_pareto_optimal" : true,
  "provide_recommendations" : true,
  "repeated_game_analysis" : false,
  "iterations" : 10,
  "additional_context" : null,
  "input_files" : null,
  "task_description" : "Analyze game theory scenario: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameTheory"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameTheory",
  "name" : "GameTheory",
  "model" : null
}
```

---

## GeneticOptimization

Iteratively evolve and perfect text through genetic algorithms

Uses genetic algorithms to optimize text through iterative evolution.
<ul>
  <li>Generates variations using configurable mutation strategies</li>
  <li>Evaluates variants against optimization criteria</li>
  <li>Selects top performers for next generation</li>
  <li>Applies crossover to combine successful traits</li>
  <li>Tracks fitness progression across generations</li>
  <li>Provides detailed analysis of evolution</li>
  <li>Supports custom evaluation criteria and weights</li>
  <li>Useful for perfecting prompts, copy, documentation, and messaging</li>
</ul>

#### Planner Prompt Segment

```text
GeneticOptimization - Iteratively evolve and perfect text through genetic algorithms
  - Specify the FULL text(s) items to optimize
  - Define the optimization goal (e.g., clarity, persuasiveness)
  - Configure number of generations (default: 5)
  - Set population size and selection size
  - Choose mutation strategies (rephrase, simplify, elaborate, restructure)
  - Enable/disable crossover for combining traits
  - Define evaluation criteria and weights
```

#### Default Execution Configuration

```json
{
  "task_type" : "GeneticOptimization",
  "initial_text" : null,
  "optimization_goal" : null,
  "evaluation_weights" : null,
  "constraints" : null,
  "num_generations" : 5,
  "population_size" : 6,
  "selection_size" : 2,
  "mutation_strategies" : [ "rephrase", "simplify", "elaborate" ],
  "enable_crossover" : true,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GeneticOptimization"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GeneticOptimization",
  "name" : "GeneticOptimization",
  "model" : null
}
```

---

## IsomorphismDiscovery

Search for and validate structural mappings between two distinct domains

Identifies structural isomorphisms between domains.
<ul>
  <li>Defines primitives (objects and operations) in both domains</li>
  <li>Generates candidate mapping rules</li>
  <li>Verifies structural preservation (homomorphism/isomorphism)</li>
  <li>Useful for theoretical physics, system architecture, and abstract modeling</li>
</ul>

#### Planner Prompt Segment

```text
IsomorphismDiscovery - Search for and validate structural mappings between two distinct domains
  ** Specify source_domain and target_domain
  ** Set mapping_strictness ('loose' or 'strict')
  ** Enable verify_operations to check structural preservation
  ** The task will:
     - Identify primitives (objects and operations) in both domains
     - Generate candidate mapping rules
     - Verify if operations are preserved (f(A op B) = f(A) op' f(B))
     - Refine and assess the validity of the isomorphism
  ** Useful for theoretical physics, system architecture, cryptography, and abstract modeling
```

#### Default Execution Configuration

```json
{
  "task_type" : "IsomorphismDiscovery",
  "source_domain" : null,
  "target_domain" : null,
  "mapping_strictness" : "strict",
  "verify_operations" : true,
  "input_files" : null,
  "related_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "IsomorphismDiscovery"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "IsomorphismDiscovery",
  "name" : "IsomorphismDiscovery",
  "model" : null
}
```

---

## LateralThinking

Break conventional thinking patterns to find innovative solutions

Applies lateral thinking techniques to generate unconventional solutions.
<ul>
  <li>Supports multiple techniques: reversal, random stimulus, challenge assumptions, exaggeration, escape, metaphor, provocation</li>
  <li>Generates multiple alternatives per technique</li>
  <li>Identifies breakthrough aspects and novel perspectives</li>
  <li>Evaluates novelty and feasibility of ideas</li>
  <li>Synthesizes insights across techniques</li>
  <li>Optionally performs detailed feasibility evaluation</li>
  <li>Suggests hybrid approaches combining multiple ideas</li>
  <li>Ideal for innovation, breaking design impasses, and creative problem-solving</li>
</ul>

#### Planner Prompt Segment

```text
LateralThinking - Break conventional thinking patterns to find innovative solutions
  ** Specify the problem or challenge to approach creatively
  ** Select lateral thinking techniques to apply:
     - reversal: Reverse the problem or goal
     - random_stimulus: Apply unrelated concepts
     - challenge_assumptions: Question fundamental assumptions
     - exaggeration: Amplify aspects to extremes
     - escape: Temporarily ignore key constraints
     - metaphor: Use metaphorical thinking
     - provocation: Use deliberate provocations
  ** Configure number of alternatives per technique (default: 5)
  ** Optionally evaluate feasibility of generated ideas
  ** The task will:
     - Apply each selected technique systematically
     - Generate unconventional alternatives
     - Identify breakthrough aspects
     - Synthesize insights across techniques
     - Evaluate feasibility if requested
  ** Useful for innovation, breaking design impasses, and creative problem-solving
```

#### Default Execution Configuration

```json
{
  "task_type" : "LateralThinking",
  "problem" : null,
  "techniques" : [ "reversal", "random_stimulus", "challenge_assumptions", "exaggeration", "escape" ],
  "num_alternatives" : 5,
  "evaluate_feasibility" : true,
  "domain_context" : null,
  "constraints" : null,
  "input_files" : null,
  "task_description" : "Apply lateral thinking to: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "LateralThinking"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "LateralThinking",
  "name" : "LateralThinking",
  "model" : null
}
```

---

## MathematicalReasoning

Solve mathematical problems through step-by-step logical reasoning with verifiable steps

Uses path search to solve mathematical problems through rigorous step-by-step reasoning.
<ul>
    <li>Breaks down complex problems into verifiable atomic steps</li>
    <li>Each step includes justification and verification</li>
    <li>Explores multiple solution paths when needed</li>
    <li>Backtracks when encountering dead ends</li>
    <li>Provides detailed proof trail with MathJax notation</li>
    <li>Supports algebra, calculus, number theory, and more</li>
    <li>Validates intermediate results for correctness</li>
    <li>Generates human-readable mathematical proofs</li>
</ul>

#### Planner Prompt Segment

```text
MathematicalReasoning - Solve mathematical problems through step-by-step logical reasoning
  ** Specify the problem statement clearly
  ** Define the goal (prove, solve, simplify, etc.)
  ** Provide any given information or constraints
  ** Specify the mathematical domain if relevant
  ** Configure search parameters (depth, alternatives)
  ** The task will:
     - Break down the problem into atomic steps
     - Verify each step's mathematical validity
     - Explore alternative solution paths
     - Backtrack from dead ends
     - Generate a complete proof trail
     - Output results in MathJax/LaTeX format
  ** Useful for:
     - Solving algebraic equations
     - Proving mathematical theorems
     - Simplifying complex expressions
     - Step-by-step calculus problems
     - Number theory proofs
     - Geometric proofs
```

#### Default Execution Configuration

```json
{
  "task_type" : "MathematicalReasoning",
  "problem_statement" : null,
  "goal" : null,
  "given_information" : null,
  "domain" : "general",
  "max_depth" : 20,
  "max_alternatives" : 3,
  "show_all_paths" : false,
  "detail_level" : "standard",
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "MathematicalReasoning"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "MathematicalReasoning",
  "name" : "MathematicalReasoning",
  "model" : null
}
```

---

## MetaCognitiveReflection

Reflect on and critique reasoning processes

Performs meta-cognitive reflection on task reasoning and solutions.
<ul>
  <li>Analyzes assumptions and identifies biases</li>
  <li>Evaluates alternative approaches</li>
  <li>Assesses confidence and certainty levels</li>
  <li>Identifies knowledge gaps and uncertainties</li>
  <li>Suggests improvements to reasoning quality</li>
  <li>Checks logical consistency and completeness</li>
</ul>

#### Planner Prompt Segment

```text
MetaCognitiveReflection - Reflect on and critique reasoning processes
  ** Specify the subject_task_id to identify which task's reasoning to reflect upon
  ** Choose reflection_aspects from:
     - 'assumptions': Identify underlying assumptions
     - 'biases': Detect potential cognitive biases
     - 'alternatives': Consider alternative approaches
     - 'confidence': Evaluate certainty levels
     - 'completeness': Check for missing considerations
     - 'logic': Verify logical consistency
  ** Optionally, list input files (supports glob patterns) to provide context
  ** Optionally, specify reflection_questions to guide the analysis
  ** Enable include_file_context to incorporate file content in reflection
  ** Enable suggest_improvements to get actionable recommendations
  ** Enable identify_gaps to surface knowledge uncertainties
  ** Enable evaluate_confidence to assess conclusion reliability
  ** This task implements "thinking about thinking" for quality improvement
```

#### Default Execution Configuration

```json
{
  "task_type" : "MetaCognitiveReflection",
  "subject_task_id" : null,
  "input_files" : null,
  "reflection_questions" : null,
  "include_file_context" : true,
  "reflection_aspects" : [ "assumptions", "biases", "alternatives", "confidence" ],
  "suggest_improvements" : true,
  "identify_gaps" : true,
  "evaluate_confidence" : true,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "MetaCognitiveReflection"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "MetaCognitiveReflection",
  "name" : "MetaCognitiveReflection",
  "model" : null
}
```

---

## ProbabilisticReasoning

Reason under uncertainty using Bayesian analysis

Performs probabilistic reasoning and Bayesian analysis under uncertainty.
<ul>
  <li>Assigns and updates probabilities using Bayes' theorem</li>
  <li>Calculates expected values and quantifies risks</li>
  <li>Identifies key uncertainties and information gaps</li>
  <li>Suggests experiments to reduce uncertainty</li>
  <li>Provides confidence intervals and sensitivity analysis</li>
  <li>Useful for risk assessment, diagnostic reasoning, and decision making</li>
</ul>

#### Planner Prompt Segment

```text
ProbabilisticReasoning - Reason under uncertainty using Bayesian analysis
  ** Specify hypotheses with prior probabilities (must sum to 1.0)
  ** Provide observed evidence to update beliefs
  ** Calculate expected values and quantify risks
  ** Identify key uncertainties that need resolution
  ** Suggest experiments to reduce uncertainty
  ** Useful for:
     - Risk assessment and management
     - Diagnostic reasoning (bug hunting)
     - A/B test analysis and decision making
     - Resource allocation under uncertainty
     - Technology adoption decisions
```

#### Default Execution Configuration

```json
{
  "task_type" : "ProbabilisticReasoning",
  "hypotheses" : null,
  "evidence" : null,
  "calculate_expected_value" : true,
  "identify_key_uncertainties" : true,
  "suggest_experiments" : true,
  "risk_tolerance" : "medium",
  "input_files" : null,
  "decision_context" : null,
  "task_description" : "Bayesian analysis of 0 hypotheses with 0 pieces of evidence",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ProbabilisticReasoning"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ProbabilisticReasoning",
  "name" : "ProbabilisticReasoning",
  "model" : null
}
```

---

## SocraticDialogue

Explore ideas through Socratic questioning

Uses Socratic questioning methodology to deeply explore ideas.
<ul>
  <li>Creates dialogue between questioner and responder agents</li>
  <li>Challenges assumptions and definitions</li>
  <li>Explores implications and consequences</li>
  <li>Identifies contradictions and tensions</li>
  <li>Configurable dialogue depth and constraints</li>
  <li>Generates synthesis of insights discovered</li>
</ul>

#### Planner Prompt Segment

```text
SocraticDialogue - Explore ideas through Socratic questioning
 ** Specify the initial question or hypothesis to explore
 ** Optionally provide input files (supports glob patterns) for context
 ** Configure maximum dialogue depth (default: 5 exchanges)
 ** Enable/disable assumption challenging
 ** Optionally constrain to specific topics or domains
 ** Creates a dialogue between questioner and responder agents
 ** Explores definitions, assumptions, implications, and contradictions
 ** Produces a structured dialogue transcript with insights
 ** Specify the initial question or hypothesis to explore
 ** Configure maximum dialogue depth (default: 5 exchanges)
 ** Enable/disable assumption challenging
 ** Optionally constrain to specific topics or domains
 ** Creates a dialogue between questioner and responder agents
 ** Explores definitions, assumptions, implications, and contradictions
 ** Produces a structured dialogue transcript with insights
```

#### Default Execution Configuration

```json
{
  "task_type" : "SocraticDialogue",
  "initial_question" : null,
  "input_files" : null,
  "max_depth" : 5,
  "challenge_assumptions" : true,
  "domain_constraints" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "SocraticDialogue",
  "task_description" : "Explore 'null' through Socratic dialogue"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "SocraticDialogue",
  "name" : "SocraticDialogue",
  "model" : null
}
```

---

## StructuralInvariantAnalysis

Distill an object down to its immutable properties and symmetries

Performs rigorous structural analysis to identify invariants.
<ul>
  <li>Decontextualizes objects to remove domain bias</li>
  <li>Applies theoretical transformations (scaling, rotation, etc.)</li>
  <li>Extracts immutable properties (invariants)</li>
  <li>Generates structural signatures for cross-domain comparison</li>
</ul>

#### Planner Prompt Segment

```text
StructuralInvariantAnalysis - Distill an object to immutable properties
  ** Specify the subject_object to analyze
  ** Define transformation_types (e.g., symmetry_groups, limit_cases)
  ** Select output_format ('fingerprint' or 'signature')
  ** Process involves:
     - Decontextualization (stripping domain terminology)
     - Stress Testing (applying transformations)
     - Invariant Extraction (identifying constants)
     - Signature Generation
```

#### Default Execution Configuration

```json
{
  "task_type" : "StructuralInvariantAnalysis",
  "subject_object" : null,
  "transformation_types" : [ "symmetry_groups", "limit_cases", "context_inversion" ],
  "output_format" : "fingerprint",
  "input_files" : null,
  "related_files" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "StructuralInvariantAnalysis",
  "task_description" : null
}
```

#### Default Type Configuration

```json
{
  "task_type" : "StructuralInvariantAnalysis",
  "name" : "StructuralInvariantAnalysis",
  "model" : null
}
```

---

## SystemsThinking

Analyze complex systems through feedback loops and dynamics

Performs systems thinking analysis to understand complex system behavior.
<ul>
  <li>Identifies feedback loops (reinforcing and balancing)</li>
  <li>Maps system archetypes (e.g., "Limits to Growth", "Shifting the Burden")</li>
  <li>Analyzes delays and accumulations</li>
  <li>Predicts emergent behavior and unintended consequences</li>
  <li>Finds high-leverage intervention points</li>
  <li>Simulates potential interventions over time</li>
  <li>Useful for understanding system dynamics, optimization, and organizational change</li>
</ul>

#### Planner Prompt Segment

```text
SystemsThinking - Analyze complex systems through feedback loops and dynamics
** Specify the system to analyze (e.g., "CI/CD pipeline", "team workflow", "market dynamics")
 ** Identify feedback loops (reinforcing and balancing)
 ** Map delays and accumulations
 ** Find leverage points for intervention
 ** Simulate potential interventions (provide a list of specific interventions to simulate)
 ** Identify system archetypes (e.g., "Limits to Growth", "Shifting the Burden")
 ** Analyze emergent behavior and unintended consequences
 ** Optionally specify focus_areas to prioritize certain subsystems
 ** Optionally provide analysis_questions for specific insights
 ** Useful for:
    - Understanding system behavior
    - Performance optimization
    - Identifying unintended consequences
    - Organizational dynamics
    - Technical debt dynamics
    - Strategic planning and scenario analysis
```

#### Default Execution Configuration

```json
{
  "task_type" : "SystemsThinking",
  "system_description" : null,
  "identify_feedback_loops" : true,
  "input_files" : null,
  "map_delays" : true,
  "find_leverage_points" : true,
  "simulate_interventions" : null,
  "time_horizon" : "6 months",
  "identify_archetypes" : true,
  "analyze_emergent_behavior" : true,
  "related_files" : null,
  "focus_areas" : null,
  "analysis_questions" : null,
  "task_description" : "Analyze system dynamics for: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "SystemsThinking"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "SystemsThinking",
  "name" : "SystemsThinking",
  "model" : null
}
```

---

## TableCompilation

Generate structured tables with AI-computed cell values

Generates tables by computing each cell value using AI.
<ul>
  <li>Define rows and columns as headers</li>
  <li>Provide a query template with {row} and {column} placeholders</li>
  <li>Cells are computed in configurable partitions for efficiency</li>
  <li>Supports markdown, HTML, and CSV output formats</li>
  <li>Useful for comparison matrices, analysis tables, decision matrices</li>
</ul>

#### Default Execution Configuration

```json
{
  "task_type" : "TableCompilation",
  "rows" : null,
  "columns" : null,
  "cell_query" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "TableCompilation"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "TableCompilation",
  "partition_size" : 2,
  "model" : null,
  "name" : "TableCompilation"
}
```

---

## TemporalReasoning

Analyze how systems evolve over time and predict future states

Performs temporal reasoning and timeline analysis to understand system evolution.
<ul>
  <li>Constructs chronological timelines of events and changes</li>
  <li>Identifies temporal patterns, cycles, and trends</li>
  <li>Analyzes rate of change and acceleration</li>
  <li>Identifies critical transition points and inflection points</li>
  <li>Predicts future states based on historical trends</li>
  <li>Useful for technical debt analysis, performance degradation, and system evolution</li>
</ul>

#### Planner Prompt Segment

```text
TemporalReasoning - Analyze system evolution and predict future states.
- subject: The system or topic to analyze.
- time_range: Period to examine (e.g., '2023-01-01 to 2024-01-01').
- granularity: daily, weekly, monthly, quarterly, yearly.
- related_files: Logs, metrics, or history files.
- identify_patterns: (Boolean) Find cycles/trends.
- predict_future: (Boolean) Extrapolate trends.
- analyze_rate_of_change: (Boolean) Velocity analysis.
- identify_transitions: (Boolean) Find inflection points.
```

#### Default Execution Configuration

```json
{
  "task_type" : "TemporalReasoning",
  "subject" : null,
  "time_range" : null,
  "granularity" : "weekly",
  "input_files" : null,
  "identify_patterns" : true,
  "predict_future" : true,
  "prediction_horizon" : "3 months",
  "critical_events" : null,
  "related_files" : null,
  "analyze_rate_of_change" : true,
  "identify_transitions" : true,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "TemporalReasoning"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "TemporalReasoning",
  "name" : "TemporalReasoning",
  "model" : null
}
```

---

