# Reasoning Tools

This package contains a comprehensive suite of advanced cognitive and logical reasoning tools. These tools leverage Large Language Models (LLMs) to perform structured analysis, creative problem-solving, and rigorous logical evaluations across various domains.

## Overview

The reasoning tools are designed to handle complex tasks that require more than simple text generation. They implement established mental models and analytical frameworks—such as Bayesian inference, Socratic questioning, and Systems Thinking—to provide deep insights and verifiable conclusions.

## Available Tools

### 🧠 Logical & Analytical Reasoning
*   **[Abductive Reasoning](AbductiveReasoningTask.kt)**: Generates and evaluates the "best explanation" for a set of observations. Ideal for root cause analysis and bug investigation.
*   **[Causal Inference](CausalInferenceTask.kt)**: Distinguishes between correlation and causation, identifies root causes, and builds causal graphs to explain system behavior.
*   **[Chain of Thought](ChainOfThoughtTask.kt)**: Breaks down complex problems into explicit, verifiable reasoning steps with built-in validation and backtracking.
*   **[Decision Tree](DecisionTreeTask.kt)**: Constructs LLM-driven symbolic decision trees for classification, providing interpretable rules and executable code.
*   **[Decomposition & Synthesis](DecompositionSynthesisTask.kt)**: Implements a divide-and-conquer approach, breaking complex problems into subproblems and synthesizing a coherent final solution.
*   **[Mathematical Reasoning](MathematicalReasoningTask.kt)**: Solves mathematical problems through rigorous step-by-step logic, providing formal proofs in LaTeX/MathJax format.
*   **[Probabilistic Reasoning](ProbabilisticReasoningTask.kt)**: Performs Bayesian analysis to reason under uncertainty, updating beliefs based on evidence and quantifying risks.
*   **[Temporal Reasoning](TemporalReasoningTask.kt)**: Analyzes how systems evolve over time, identifying patterns, cycles, and predicting future states based on historical trends.

### 💡 Creative & Lateral Thinking
*   **[Analogical Reasoning](AnalogicalReasoningTask.kt)**: Solves problems by drawing structural analogies from distant domains (e.g., applying biological concepts to software architecture).
*   **[Brainstorming](BrainstormingTask.kt)**: Systematically generates diverse solution options and performs independent pros/cons/feasibility analysis for each.
*   **[Lateral Thinking](LateralThinkingTask.kt)**: Uses techniques like reversal, random stimulus, and provocation to break conventional thinking patterns and find innovative solutions.
*   **[Socratic Dialogue](SocraticDialogueTask.kt)**: Explores ideas through deep questioning, challenging assumptions, and identifying contradictions through a simulated dialogue.

### 🏗️ Structural & Systems Analysis
*   **[Abstraction Ladder](AbstractionLadderTask.kt)**: Traverses levels of abstraction (up for generalizations, down for implementations) to discover design patterns and refactoring opportunities.
*   **[Finite State Machine](FiniteStateMachineTask.kt)**: Models systems as states and transitions, identifying edge cases, error states, and generating test scenarios.
*   **[Structural Invariant Analysis](StructuralInvariantAnalysisTask.kt)**: Distills objects down to their immutable properties and symmetries by stripping away domain-specific context.
*   **[Systems Thinking](SystemsThinkingTask.kt)**: Analyzes complex systems through feedback loops, delays, and accumulations to identify high-leverage intervention points.

### 🛡️ Adversarial & Evaluative Reasoning
*   **[Adversarial Reasoning](AdversarialReasoningTask.kt)**: Performs red team analysis to identify security vulnerabilities, logic flaws, and failure modes in designs or arguments.
*   **[Counterfactual Analysis](CounterfactualAnalysisTask.kt)**: Explores "what-if" scenarios to understand the impact of different decisions and causal relationships.
*   **[Meta-Cognitive Reflection](MetaCognitiveReflectionTask.kt)**: "Thinking about thinking"—critiques the reasoning process of other tasks to identify biases, assumptions, and gaps.

### 🧪 Specialized Optimization & Mapping
*   **[Constraint Relaxation](ConstraintRelaxationTask.kt)**: Solves over-constrained problems by progressively relaxing and reintroducing constraints based on priority.
*   **[Constraint Satisfaction](ConstraintSatisfactionTask.kt)**: Balances hard requirements and weighted soft preferences to find optimal solutions in complex search spaces.
*   **[Functorial Mapping](FunctorialMappingTask.kt)**: Uses Category Theory concepts to translate problems from one domain to another where superior tools may exist.
*   **[Genetic Optimization](GeneticOptimizationTask.kt)**: Iteratively evolves and perfects text (like prompts or documentation) using mutation and crossover strategies.
*   **[Isomorphism Discovery](IsomorphismDiscoveryTask.kt)**: Searches for and validates structural mappings between two distinct domains to find underlying commonalities.
*   **[Neural Network Layer](NeuralNetworkLayerTask.kt)**: Provides a comprehensive framework for designing, deriving gradients for, and analyzing the stability of neural network layers.
*   **[Table Compilation](TableCompilationTask.kt)**: Generates structured comparison matrices and data tables by computing cell values in parallel partitions.

## Usage

These tools are typically invoked by the `TaskOrchestrator` as part of a larger plan. Each tool requires a specific configuration (e.g., `AbductiveReasoningTaskExecutionConfigData`) that defines the inputs, constraints, and goals for the reasoning process.

Most tools produce:
1.  **Interactive Tabs**: Organized views of the reasoning process (e.g., "Timeline", "Analysis", "Synthesis").
2.  **Detailed Transcripts**: Markdown reports containing the full chain of thought and evidence.
3.  **Structured Results**: Final conclusions or artifacts (like code or diagrams) that can be used by subsequent tasks.