# Social and Reasoning Tasks

This package contains a suite of advanced reasoning and social simulation tasks. These tools leverage LLMs to perform
complex analyses, simulate human-like interactions, and resolve multi-faceted problems through structured frameworks.

## Overview

The tasks in this package are designed for:

- **Complex Reasoning**: Dialectical, ethical, and multi-perspective analysis.
- **Strategic Analysis**: Game theory and political optimization.
- **Social Simulation**: Poll simulations and controlled LLM experiments.
- **Advanced Writing**: Generation of structured persuasive essays.

---

## Available Tasks

### 1. Dialectical Reasoning Task

Resolves contradictions by iterating through thesis, antithesis, and synthesis phases to reach higher-level
understanding.

* **Key Features**:
  * Explores contradictions and tensions between positions.
  * Generates synthesis that transcends opposition.
  * Iterates through multiple synthesis levels (1-5).
  * Preserves valuable aspects from both sides.

| Parameter            | Description                                                 |
|:---------------------|:------------------------------------------------------------|
| `thesis`             | The thesis statement or position to analyze.                |
| `antithesis`         | The antithesis statement or opposing position.              |
| `context`            | Context or domain for the dialectical analysis.             |
| `synthesis_levels`   | Number of synthesis levels to iterate through (1-5).        |
| `preserve_strengths` | Whether to preserve strengths from both sides in synthesis. |
| `input_files`        | Specific files or glob patterns for input.                  |

### 2. Ethical Reasoning Task

Analyzes complex dilemmas through multiple established ethical frameworks (e.g., Utilitarianism, Deontology, Virtue
Ethics).

* **Key Features**:
  * Evaluates dilemmas from diverse philosophical perspectives.
  * Identifies stakeholder impacts.
  * Synthesizes findings into a balanced recommendation.
  * Highlights ethical trade-offs and points of conflict.

| Parameter            | Description                                             |
|:---------------------|:--------------------------------------------------------|
| `ethical_dilemma`    | Clear description of the ethical problem.               |
| `stakeholders`       | List of individuals or groups affected.                 |
| `ethical_frameworks` | Frameworks to apply (utilitarianism, deontology, etc.). |
| `input_files`        | Optional input files for context.                       |

### 3. Game Theory Task

Performs comprehensive game theory analysis of strategic situations, supporting cooperative and non-cooperative models.

* **Key Features**:
  * Constructs payoff matrices.
  * Identifies Nash equilibria and Pareto optimal outcomes.
  * Analyzes dominant and dominated strategies.
  * Supports repeated game analysis with trigger strategies.

| Parameter       | Description                                                   |
|:----------------|:--------------------------------------------------------------|
| `game_scenario` | The strategic situation to analyze.                           |
| `players`       | List of players/agents in the game.                           |
| `game_type`     | cooperative, non-cooperative, zero-sum, repeated, sequential. |
| `iterations`    | Number of iterations for repeated game analysis.              |

### 4. LLM Experiment Task

Conducts controlled, repeatable experiments to characterize LLM behaviors, biases, and performance.

* **Key Features**:
  * Variable substitution in prompt templates.
  * Statistical analysis (t-tests, variance, significance).
  * Response diversity and consistency measurement.
  * Automated insight generation.

| Parameter            | Description                                          |
|:---------------------|:-----------------------------------------------------|
| `prompt_templates`   | Base prompt templates to test.                       |
| `prompt_variables`   | Variables to substitute in templates.                |
| `metrics`            | Metrics to track (e.g., response_length, sentiment). |
| `repetitions`        | Number of times to repeat each condition.            |
| `significance_level` | Alpha level for statistical tests (e.g., 0.05).      |

### 5. LLM Poll Simulation Task

Simulates surveys and polls using diverse AI personas to test instruments or explore demographic patterns.

* **Key Features**:
  * Supports multiple question types (Likert, Choice, Open-ended).
  * Generates realistic respondent personas with demographics.
  * Performs cross-tabulation and sentiment analysis.
  * Detects response biases (central tendency, primacy effects).

| Parameter                 | Description                                  |
|:--------------------------|:---------------------------------------------|
| `questions`               | List of survey questions.                    |
| `respondent_profiles`     | Templates defining demographics and traits.  |
| `respondents_per_profile` | Number of simulated respondents per profile. |
| `demographic_dimensions`  | Dimensions to track (age, gender, etc.).     |

### 6. Multi-Perspective Analysis Task

Examines a topic from several distinct viewpoints (e.g., technical, business, user) and synthesizes them.

* **Key Features**:
  * Independent analysis per perspective.
  * Identification of agreements and conflicts.
  * Consensus-based synthesis.

| Parameter             | Description                                 |
|:----------------------|:--------------------------------------------|
| `analysis_subject`    | The topic or problem to analyze.            |
| `perspectives`        | List of viewpoints to consider.             |
| `synthesize`          | Whether to generate a unified conclusion.   |
| `consensus_threshold` | Minimum confidence for agreement (0.0-1.0). |

### 7. Persuasive Essay Task

Generates high-quality, structured persuasive essays using rhetorical techniques and iterative refinement.

* **Key Features**:
  * Structured argument development (Ethos, Pathos, Logos).
  * Addresses counterarguments and rebuttals.
  * Iterative revision passes for quality.
  * Optional image generation for cover and arguments.

| Parameter           | Description                                    |
|:--------------------|:-----------------------------------------------|
| `thesis`            | The position to argue for.                     |
| `target_audience`   | e.g., 'general public', 'policymakers'.        |
| `tone`              | e.g., 'formal', 'analytical', 'passionate'.    |
| `target_word_count` | Target length for the essay.                   |
| `call_to_action`    | 'strong', 'moderate', 'reflective', or 'none'. |

### 8. Political Optimization Task

Evaluates and evolves text to find common ground or identify wedge issues across the political spectrum.

* **Key Features**:
  * Evaluates text from left, center, right, and other perspectives.
  * Calculates consensus fitness scores.
  * Uses evolutionary algorithms (mutation/crossover) to optimize text.
  * Identifies points of contention and unifying language.

| Parameter           | Description                                          |
|:--------------------|:-----------------------------------------------------|
| `initial_text`      | The text to optimize.                                |
| `optimization_goal` | e.g., 'maximize consensus', 'identify wedge issues'. |
| `perspectives`      | Political viewpoints to evaluate from.               |
| `num_generations`   | Number of evolutionary cycles.                       |
| `consensus_mode`    | 'maximize', 'minimize', or 'explore'.                |

---

## Implementation Details

All tasks in this package extend `AbstractTask` and utilize:

- **Tabbed Displays**: For organized, multi-step output.
- **Transcripts**: Detailed markdown logs of the reasoning process.
- **Parsed Agents**: For structured data extraction and evaluation.
- **Concurrent Execution**: Leveraging thread pools for simulations and multi-perspective runs.