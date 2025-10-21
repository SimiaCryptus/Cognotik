# AbductiveReasoningTask.kt

## Abductive Reasoning

### Overview

The Abductive Reasoning task is designed to perform "inference to the best explanation." Given a set of observations, it generates and evaluates multiple
potential hypotheses to determine the most plausible cause. This process mimics scientific reasoning and diagnostic investigation.

- **Primary Use Cases:**
  - Root cause analysis for system failures or errors.
  - Investigating and diagnosing complex software bugs.
  - Understanding anomalous data or unexpected system behavior.
  - Formulating and testing scientific hypotheses.
  - Any scenario requiring inference from effects back to potential causes.
- **Expected Outcomes:**
  - A ranked list of explanatory hypotheses, each scored on multiple criteria.
  - A detailed comparative analysis of the competing hypotheses.
  - A clear identification of the "best explanation" based on the evidence.
  - Actionable suggestions for tests to validate or falsify the top hypotheses.

### When to Use

This task excels in situations where you have a set of symptoms (observations) but the underlying cause is not obvious. It is ideal for open-ended
investigations where multiple explanations are possible.

- **Specific Scenarios:**
  - A web application is experiencing intermittent timeouts, and you have logs showing various non-critical errors.
  - A data pipeline produces incorrect output, and you have a series of observations about the data at different stages.
  - A user reports a bug with a vague description, and you have a set of related system events.
- **Comparison with Alternatives:**
  - **Deductive Reasoning:** Starts with a known rule and applies it to a case (e.g., "All users in group X have this permission; John is in group X; therefore,
    John has this permission"). Use this when rules are well-defined.
  - **Inductive Reasoning:** Generalizes from specific examples to form a rule (e.g., "I've seen 100 white swans; therefore, all swans are white"). Use this for
    pattern recognition.
  - **Abductive Reasoning:** Finds the most likely explanation for an observation (e.g., "The ground is wet; it most likely rained"). Use this for diagnosis and
    explanation-finding.

### Configuration

The task's behavior is configured through the `AbductiveReasoningTaskExecutionConfigData` class.

#### Required Parameters

| Parameter      | Type           | Description                                                       | Example                                                                    |
|:---------------|:---------------|:------------------------------------------------------------------|:---------------------------------------------------------------------------|
| `observations` | `List<String>` | A list of observed facts or symptoms that require an explanation. | `["User login fails with 500 error", "Database CPU usage spikes to 100%"]` |

#### Optional Parameters

| Parameter             | Type           | Default                                                                   | Description                                                                                                                               | Example                                                                                             |
|:----------------------|:---------------|:--------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------|
| `generate_hypotheses` | `Boolean`      | `true`                                                                    | If `true`, the task will generate new hypotheses. If `false`, it will only evaluate the `existing_hypotheses`.                            | `false`                                                                                             |
| `max_hypotheses`      | `Int`          | `5`                                                                       | The maximum number of distinct hypotheses to generate and evaluate.                                                                       | `3`                                                                                                 |
| `evaluate_criteria`   | `List<String>` | `["explanatory_power", "simplicity", "testability", "prior_probability"]` | The criteria used to score each hypothesis.                                                                                               | `["explanatory_power", "simplicity"]`                                                               |
| `suggest_tests`       | `Boolean`      | `true`                                                                    | If `true`, the task will generate a set of suggested tests to validate the top hypotheses.                                                | `false`                                                                                             |
| `existing_hypotheses` | `List<String>` | `null`                                                                    | A list of pre-defined hypotheses to evaluate. Used when `generate_hypotheses` is `false`.                                                 | `["The database connection pool is exhausted", "A recent code change introduced an infinite loop"]` |
| `domain_context`      | `String`       | `null`                                                                    | Provides background context or constraints about the domain (e.g., "This is a Java Spring Boot application using a PostgreSQL database"). | `"The system is a real-time bidding platform with high throughput."`                                |

### How It Works

#### Process Flow

The task follows a structured, multi-step process to ensure a thorough analysis:

1. **Observation Logging:** The initial set of observations is documented.
2. **Context Gathering:** The task gathers relevant context from the output of previous tasks in the plan.
3. **Hypothesis Generation/Evaluation:**
  - If `generate_hypotheses` is `true`, it prompts an LLM to generate a set of diverse, explanatory hypotheses based on the observations and context.
  - If `false`, it uses the provided `existing_hypotheses`.
  - Each hypothesis is evaluated and scored against the specified `evaluate_criteria`.
4. **Comparative Analysis:** The LLM performs a comparative analysis of all hypotheses, discussing their trade-offs, strengths, and weaknesses. It identifies
   which hypothesis best explains the full set of observations.
5. **Test Suggestion (Optional):** If `suggest_tests` is `true`, the task generates concrete, actionable tests (confirmatory, falsification, and discriminating)
   for the top-ranked hypotheses.
6. **Best Explanation Summary:** The single best hypothesis is identified and presented in detail, explaining why it is the most plausible explanation.
7. **Final Report:** A comprehensive summary of the entire process is generated, including key findings and the final conclusion.

#### Internal Mechanics

The task leverages a `ParsedAgent` to interact with the LLM, ensuring that the model's output conforms to the structured `Hypothesis` and `HypothesesResponse`
data classes. This allows for reliable data extraction and scoring. For more open-ended steps like comparative analysis and test generation, a standard
`ChatAgent` is used. The prompts are carefully engineered to guide the LLM through the principles of abductive reasoning, such as Occam's Razor (simplicity) and
testability.

#### Output Structure

The results are presented in a multi-tabbed user interface for clarity and easy navigation:

- **Overview:** A high-level summary of the task's configuration, progress, and final results.
- **Observations:** A list of the initial observations that the task is trying to explain.
- **Context:** Relevant information gathered from previous tasks.
- **Hypotheses:** A detailed breakdown of each generated hypothesis, including its description, explanation, and scores for each evaluation criterion.
- **Analysis:** The full text of the comparative analysis, highlighting the trade-offs between different explanations.
- **Validation Tests:** (If enabled) A list of suggested experiments to prove or disprove the top hypotheses.
- **Best Explanation:** A focused summary of the most likely hypothesis and the reasoning behind its selection.

# AbstractionLadderTask.kt

## AbstractionLadderTask

### Overview

The Abstraction Ladder task provides a structured method for analyzing a concept, problem, or piece of code by traversing different levels of abstraction. It
helps uncover underlying principles, identify design patterns, and discover concrete implementation details by systematically moving from general to specific,
and vice-versa.

- **Primary Use Cases:**
  - Understanding a complex system by breaking it down into more general components or more specific examples.
  - Identifying reusable design patterns or architectural principles from a concrete piece of code.
  - Brainstorming potential implementations or variations of a high-level concept.
  - Discovering refactoring opportunities and code smells by examining code at different granularities.
  - Improving system design by ensuring a clear and logical hierarchy of abstractions.
- **Expected Outcomes:** A detailed report that visualizes the abstraction hierarchy, provides analysis at each level (including patterns and examples), and
  offers actionable recommendations for refactoring or implementation.

### When to Use

This task is particularly effective when you need to gain a deeper, multi-faceted understanding of a subject.

- **Specific Scenarios:**
  - **Code Refactoring:** When you have a complex class or function and want to identify opportunities to apply design patterns or simplify its structure.
  - **System Design:** When starting with a high-level requirement and needing to explore concrete implementation strategies.
  - **Learning and Exploration:** When encountering a new algorithm or programming paradigm and wanting to understand its core principles and practical
    applications.
- **Problem Types:**
  - Analyzing existing codebases for architectural improvements.
  - Generating documentation that explains a concept from first principles to specific examples.
  - Bridging the gap between abstract requirements and concrete technical solutions.
- **Comparison with Alternative Reasoning Types:**
  - Unlike **Chain of Thought**, which focuses on a linear, step-by-step process to solve a problem, the Abstraction Ladder explores a topic's structure
    vertically.
  - It is more structured than a general **Analysis Task**, which might provide a flat summary. The ladder forces a hierarchical exploration, often yielding
    deeper insights into relationships between components.

### Configuration

#### Required Parameters

| Parameter          | Type   | Description                                       | Example                                |
|--------------------|--------|---------------------------------------------------|----------------------------------------|
| `concrete_concept` | String | The initial concept, problem, or code to analyze. | `"A singleton pattern implementation"` |

#### Optional Parameters

| Parameter           | Type         | Default | Description                                                                                                   | Example                                    |
|---------------------|--------------|---------|---------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| `direction`         | String       | "both"  | The direction to traverse the ladder: 'up' (generalize), 'down' (concretize), or 'both'.                      | `"up"`                                     |
| `levels`            | Int          | 3       | The number of abstraction levels to traverse in each direction. A range of 1-5 is recommended.                | `2`                                        |
| `identify_patterns` | Boolean      | true    | If true, the analysis will explicitly identify design patterns, anti-patterns, and refactoring opportunities. | `false`                                    |
| `related_files`     | List<String> | null    | A list of file paths to provide additional context for the analysis (e.g., related code or documentation).    | `["src/main/MySingleton.kt"]`              |
| `task_description`  | String       | null    | A custom description for the task instance.                                                                   | `"Analyze the user authentication module"` |
| `task_dependencies` | List<String> | null    | A list of task IDs that must be completed before this task can run.                                           | `["task_123"]`                             |

### How It Works

#### Process Flow

1. **Initialization:** The task starts with the user-provided `concrete_concept`. It sets up a multi-tabbed UI to organize the results for "Overview", "Upward
   Analysis", "Downward Analysis", and "Pattern Analysis".
2. **Context Gathering:** It reads the content of any `related_files` and incorporates results from previous tasks to build a comprehensive context.
3. **Upward Analysis (Generalization):** If `direction` is 'up' or 'both', the task prompts an AI agent to move up the abstraction ladder from the starting
   concept for the specified number of `levels`. For each level, it identifies a more general concept, explains what is being abstracted, and notes relevant
   design or architectural patterns.
4. **Downward Analysis (Concretization):** If `direction` is 'down' or 'both', the task prompts the AI to move down the ladder. For each level, it identifies
   more specific implementations, explains what is being specialized, provides concrete examples (including code snippets), and notes implementation patterns or
   anti-patterns.
5. **Pattern Synthesis:** If `identify_patterns` is enabled, a final prompt is sent to the AI, asking it to synthesize the findings from both the upward and
   downward analyses. This step generates a consolidated summary of all identified patterns, anti-patterns, architectural insights, and actionable refactoring
   recommendations.
6. **Completion:** The final, structured Markdown report is assembled from the results of each stage and presented to the user across the different UI tabs.

#### Internal Mechanics

The core of the task relies on a series of structured prompts sent to a `ChatAgent` configured with the persona of an "expert software architect." Each prompt
is carefully crafted to guide the AI through a single stage of the process (generalization, concretization, or synthesis). By breaking the problem down and
iterating through levels, the task elicits a more detailed and structured analysis than a single, monolithic prompt could achieve.

#### Output Structure

The final output is a comprehensive Markdown report organized into the following sections, each displayed in its own tab in the user interface:

- **Upward Abstraction (Generalizations):** A hierarchical list moving from the concrete concept to more abstract principles. Each level includes an explanation
  of the generalization, examples of other concepts at that level, and identified patterns.
- **Downward Concretization (Specific Implementations):** A hierarchical list moving from the starting concept to more specific examples. Each level includes an
  explanation of the specialization, concrete code snippets, and relevant implementation patterns or anti-patterns.
- **Pattern Analysis & Recommendations:** A synthesized summary that includes:
  - A list of all identified design patterns.
  - High-level architectural insights.
  - Specific, actionable refactoring opportunities.
  - A list of anti-patterns and code smells to address.

# AdversarialReasoningTask.kt

## AdversarialReasoning

### Overview

Adversarial Reasoning, also known as Red Team Analysis, is a task designed to proactively identify vulnerabilities, weaknesses, and failure modes in a system,
design, or argument. It simulates an attacker's mindset to stress-test the target from various perspectives.

- **Primary Use Cases:**
  - Performing security audits on software systems or infrastructure designs.
  - Stress-testing the logic and robustness of a plan or argument.
  - Identifying potential edge cases and failure modes before implementation.
  - Proactively discovering business, privacy, or compliance risks.
  - Simulating attack scenarios to understand potential impact.

- **Expected Outcomes:**
  - A structured list of identified vulnerabilities, each with a severity rating (Critical, High, Medium, Low).
  - A comprehensive executive summary detailing the overall risk level and key concerns.
  - (Optional) Detailed exploit scenarios for identified vulnerabilities.
  - (Optional) Actionable mitigation strategies to address the findings.
  - A list of discovered edge cases and potential failure modes.

### When to Use

This task is ideal for situations requiring a critical and skeptical evaluation of a target. It moves beyond simple analysis to actively probe for weaknesses.

- **Use this task when you need to:**
  - Validate the security of a new feature or application.
  - Prepare for a security audit or penetration test.
  - Ensure a system design is robust against unexpected conditions.
  - Critically evaluate a business plan or strategic proposal for hidden flaws.
  - Understand the potential attack surface of a system.

- **Comparison with other reasoning types:**
  - Unlike a standard `AnalysisTask` which aims to understand and describe a system, `AdversarialReasoning` actively tries to break it.
  - It is more focused and structured than a general `BrainstormingTask`, as it operates within specific attack vectors and follows a defined red team
    methodology.

### Configuration

The task is configured through the `AdversarialReasoningTaskExecutionConfigData` object.

#### Required Parameters

| Parameter       | Type   | Description                                     | Example                                                  |
|-----------------|--------|-------------------------------------------------|----------------------------------------------------------|
| `target_system` | String | The system, design, or argument to be analyzed. | `"The user authentication flow for our new mobile app."` |

#### Optional Parameters

| Parameter                        | Type           | Default                 | Description                                                                                      | Example                                                                   |
|----------------------------------|----------------|-------------------------|--------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `attack_vectors`                 | List\<String\> | `["security", "logic"]` | The specific angles of attack to explore.                                                        | `["security", "privacy", "performance"]`                                  |
| `adversary_capability`           | String         | `"intermediate"`        | The skill level of the simulated adversary.                                                      | `"advanced"`                                                              |
| `generate_exploits`              | Boolean        | `false`                 | If true, the task will generate detailed, technical exploit scenarios. Use with caution.         | `true`                                                                    |
| `suggest_mitigations`            | Boolean        | `true`                  | If true, the task will generate recommended strategies to fix the identified vulnerabilities.    | `false`                                                                   |
| `related_files`                  | List\<String\> | `null`                  | Glob patterns for related files or code to include as context for the analysis.                  | `["src/main/kotlin/com/auth/**/*.kt"]`                                    |
| `challenge_assumptions`          | List\<String\> | `null`                  | A list of specific assumptions about the system that the analysis should aggressively challenge. | `["The database is always available.", "User input is never malicious."]` |
| `max_vulnerabilities_per_vector` | Int            | `5`                     | The maximum number of vulnerabilities to identify for each specified attack vector. Range: 1-20. | `10`                                                                      |

#### Advanced Options

- **`attack_vectors`**: You can choose from a predefined list: `'security'`, `'performance'`, `'logic'`, `'business'`, `'privacy'`, `'compliance'`. Combining
  multiple vectors provides a more comprehensive analysis.
- **`adversary_capability`**: This setting significantly influences the depth and creativity of the analysis.
  - `basic`: Simulates an attacker with common tools and basic skills.
  - `intermediate`: A technically solid attacker who can chain common exploits.
  - `advanced`: A skilled security researcher with deep knowledge and creative strategies.
  - `nation-state`: An adversary with virtually unlimited resources, capable of developing zero-day exploits.
- **`generate_exploits`**: Enabling this provides concrete, step-by-step instructions on how to exploit a vulnerability. This is useful for penetration testing
  and validation but may generate sensitive or dangerous information.

### How It Works

#### Process Flow

1. **Initialization**: The task starts by validating its configuration and setting up a tabbed display in the user interface. An "Overview" tab is created to
   track progress.
2. **Context Gathering**: It gathers context from previous tasks and reads the content of any files specified in `related_files`.
3. **Vector Analysis Loop**: The task iterates through each `attack_vector` provided in the configuration.
  - For each vector, a new UI tab is created.
  - A specialized **Adversarial Agent** is instantiated with a persona tailored to the specific vector and adversary capability level. Its AI model is set to a
    higher temperature (0.8) to encourage creative and unconventional thinking.
  - A detailed prompt is constructed, instructing the agent to analyze the `target_system` from the vector's perspective.
  - The agent performs the analysis.
  - The raw results are parsed to extract structured `VulnerabilityReport` objects, as well as lists of edge cases and failure modes.
4. **Mitigation Generation**: If `suggest_mitigations` is enabled and vulnerabilities were found, a separate **Mitigation Agent** is created. This agent has a
   more defensive persona and is tasked with proposing practical solutions for the identified issues.
5. **Summary Generation**: An executive summary is created, providing a high-level overview of the findings, a risk assessment, and top-level recommendations.
6. **Finalization**: The UI is updated with all generated reports, and a concise markdown summary is passed as the final output to the orchestrator.

#### Internal Mechanics

The task's effectiveness comes from its use of multiple, specialized AI agents. Instead of a single, monolithic analysis, it breaks the problem down:

- **Adversarial Agent**: This agent is prompted to be aggressive, creative, and skeptical. Its persona changes based on the `attack_vector` and
  `adversary_capability`, allowing it to adopt different mindsets (e.g., a security hacker vs. a lawyer looking for compliance loopholes).
- **Mitigation Agent**: This agent has a "blue team" or defensive persona. It is prompted to be a security architect providing practical, actionable advice to
  fix the problems found by the adversarial agent.

This separation of concerns allows each agent to excel at its specific task, leading to a more robust and well-rounded analysis.

#### Output Structure

The task produces a rich, multi-tabbed output in the UI, typically including:

- **Overview**: A real-time log of the task's progress and a final summary of results.
- **Vector Tabs**: A dedicated tab for each `attack_vector` (e.g., "Vector: Security"), containing the detailed analysis and findings for that area.
- **Mitigations**: If enabled, a tab with recommended short-term and long-term fixes.
- **Executive Summary**: A high-level report for stakeholders, including a risk matrix and top concerns.

The final output returned to the plan orchestrator is a concise markdown string summarizing the key findings, total vulnerabilities, and overall risk level.
Identified issues are structured internally as `VulnerabilityReport` objects.

# AnalogicalReasoningTask.kt

## Analogical Reasoning

### Overview

The Analogical Reasoning task is a powerful tool for creative problem-solving. It works by identifying and applying structural similarities from a
well-understood "source domain" to a complex "target problem." This process helps generate novel insights, overcome mental blocks, and develop innovative
solutions that might not be apparent through conventional, linear thinking.

- **Primary Use Cases:**
  - Generating creative solutions for complex or ill-defined problems.
  - Simplifying and explaining complex topics by relating them to more familiar concepts.
  - Developing new product features or business strategies by drawing inspiration from other industries.
  - Overcoming creative blocks in design, writing, or strategic planning.
  - Exploring a problem from multiple, unconventional perspectives.

- **Expected Outcomes:**
  - A list of detailed analogies, each mapping concepts from the source domain to the target problem.
  - A synthesis of key insights derived from across all generated analogies.
  - A concrete, recommended approach or solution for the target problem.
  - An optional validation report assessing the logical coherence of the analogies.

### When to Use

This task is ideal when you need to break away from traditional problem-solving methods and introduce a creative, non-linear approach.

- **Specific Scenarios:**
  - When you are "stuck" on a problem and need a fresh perspective.
  - During brainstorming or ideation sessions to broaden the range of possible solutions.
  - When designing a new system and looking for proven architectural patterns from other fields (e.g., applying principles of biological evolution to software
    design).
  - For strategic planning, to anticipate market shifts by drawing parallels with historical events or trends in other sectors.

- **Comparison with Alternative Reasoning Types:**
  - **vs. Chain of Thought:** Chain of Thought follows a linear, step-by-step logical progression. Analogical Reasoning is non-linear, making creative leaps
    between different conceptual domains.
  - **vs. Decomposition-Synthesis:** This method breaks a problem into smaller parts and solves them individually. Analogical Reasoning reframes the entire
    problem by looking at it through a different lens.

### Configuration

#### Required Parameters

| Parameter        | Type   | Description                                   | Example                                    |
|------------------|--------|-----------------------------------------------|--------------------------------------------|
| `source_domain`  | String | The domain from which to draw analogies.      | "Biological systems" or "Urban planning"   |
| `target_problem` | String | The specific problem you are trying to solve. | "How to improve user retention in our app" |

#### Optional Parameters

| Parameter           | Type         | Default | Description                                                                                               | Example                                        |
|---------------------|--------------|---------|-----------------------------------------------------------------------------------------------------------|------------------------------------------------|
| `num_analogies`     | Int          | `3`     | The number of distinct analogies to generate and explore.                                                 | `5`                                            |
| `validate_mappings` | Boolean      | `true`  | If enabled, a second AI agent will review the analogies for structural consistency and logical coherence. | `false`                                        |
| `related_files`     | List<String> | `null`  | A list of file paths to provide additional context for the reasoning process.                             | `["./docs/user_feedback.md", "./src/main.py"]` |
| `task_description`  | String       | `null`  | A custom description for this specific task instance.                                                     | "Analogies for improving app onboarding"       |
| `task_dependencies` | List<String> | `null`  | A list of other task IDs that must be completed before this one can start.                                | `["task_123", "task_456"]`                     |

### How It Works

#### Process Flow

1. **Configuration & Context:** The task starts by validating the required `source_domain` and `target_problem`. It then gathers context from previous steps in
   the plan and any files specified in `related_files`.
2. **Analogy Generation:** An AI agent, prompted to be an expert in creative problem-solving, generates the requested number of analogies. For each analogy, it
   identifies a source concept, explains how it applies to the target problem, details the conceptual mappings, and extracts key insights and potential
   solutions.
3. **Mapping Validation (Optional):** If `validate_mappings` is enabled, a separate, more critical AI agent reviews the generated analogies. It assesses the
   structural parallelism, logical consistency, and overall coherence of the mappings, providing a validation assessment.
4. **Synthesis & Recommendation:** The initial generation step also produces a high-level synthesis of insights gathered from all analogies and formulates a
   final recommended approach to the target problem.
5. **Output Formatting:** The task concludes by organizing all generated content—analogies, validation notes, synthesis, and recommendations—into a structured,
   human-readable markdown report.

#### Internal Mechanics

The task leverages a Large Language Model's ability to recognize and articulate abstract patterns across different domains. The core of the process is a
detailed prompt that instructs the model to focus on deep structural similarities rather than superficial resemblances. The optional validation step introduces
a "second opinion" from another AI agent, adding a layer of logical rigor to the creative output.

#### Output Structure

The final output is a comprehensive report (`AnalogicalReasoningResult`) containing:

- **A List of Analogies:** Each `Analogy` object includes:
  - `title`: A descriptive title for the analogy.
  - `source_description`: An explanation of the concept from the source domain.
  - `application`: How the concept applies to the target problem.
  - `mappings`: A detailed list of one-to-one mappings between source and target concepts.
  - `insights`: Key takeaways and new perspectives gained from the analogy.
  - `suggested_solutions`: Actionable ideas and solutions inspired by the analogy.
  - `confidence`: An AI-generated score (0-1) indicating the perceived strength of the analogy.
- **Synthesized Insights:** A list of the most important insights that emerge when considering all analogies together.
- **Recommended Approach:** A final, consolidated recommendation for solving the target problem.
- **Validation Notes:** (If enabled) A textual assessment of the logical soundness of the generated analogies.

# BrainstormingTask.kt

## Brainstorming

### Overview

The Brainstorming task is a powerful tool for systematically generating and analyzing a diverse set of potential solutions or ideas for a given problem. It
breaks down the complex process of creative problem-solving into distinct, manageable steps: generating options, analyzing each one independently, and then
synthesizing the results into a comparative summary with actionable recommendations.

- **Primary use cases:**
  - Exploring a wide range of solutions for a complex problem.
  - Making informed decisions by comparing multiple well-analyzed options.
  - Strategic planning and feature ideation.
  - Creative problem-solving and "out-of-the-box" thinking.
  - Identifying potential hybrid solutions by combining the best aspects of different ideas.
- **Expected outcomes:** A structured report containing a list of generated ideas, a detailed pro/con analysis for each, and a final summary that compares the
  options and provides recommendations for the best path forward.

### When to Use

This task is ideal for situations where a problem is open-ended and does not have a single, obvious solution. It encourages divergent thinking to explore the
solution space broadly before converging on the most promising options.

- **Specific scenarios where this reasoning type excels:**
  - Initial stages of project planning where multiple architectural approaches could be taken.
  - Marketing strategy sessions to generate different campaign ideas.
  - Product development meetings to brainstorm new features.
  - Troubleshooting complex issues where the root cause is unknown and multiple hypotheses need to be explored.
- **Problem types best suited for this approach:**
  - "How can we improve...?"
  - "What are the different ways to achieve...?"
  - "Generate a list of potential solutions for..."
- **Comparison with alternative reasoning types:** Unlike simpler tasks that might provide a single answer or code implementation, the Brainstorming task is
  designed for exploration. It is more comprehensive than a simple chat interaction, as it enforces a structured process of generation, analysis, and synthesis,
  leading to higher-quality, more reliable outputs.

### Configuration

#### Required Parameters

| Parameter           | Type   | Description                                 | Example                                             |
|---------------------|--------|---------------------------------------------|-----------------------------------------------------|
| `problem_statement` | String | The core problem or question to brainstorm. | "How can we reduce our application's startup time?" |

#### Optional Parameters

| Parameter                  | Type         | Default      | Description                                                                           | Example                               |
|----------------------------|--------------|--------------|---------------------------------------------------------------------------------------|---------------------------------------|
| `target_option_count`      | Int          | `7`          | The desired number of distinct options to generate.                                   | `10`                                  |
| `categories`               | List<String> | `null`       | Specific domains or categories to guide the brainstorming process.                    | `["Frontend", "Backend", "Database"]` |
| `constraints`              | List<String> | `null`       | Requirements or limitations that the solutions must adhere to.                        | `["Must not increase server costs"]`  |
| `include_creative_options` | Boolean      | `true`       | If true, encourages the generation of unconventional or novel ideas.                  | `false`                               |
| `analysis_depth`           | String       | `"moderate"` | Controls the level of detail in the analysis phase (`brief`, `moderate`, `detailed`). | `"detailed"`                          |

### How It Works

The Brainstorming task employs a multi-agent, three-stage process to ensure both creativity and analytical rigor.

#### Process Flow

1. **Option Generation:** The task first constructs a detailed prompt based on the `problem_statement`, `target_option_count`, `categories`, and `constraints`.
   It uses a `ParsedAgent` to call the LLM, which generates a structured list of diverse solution options. This step focuses on divergent thinking to cover a
   wide range of possibilities.
2. **Independent Analysis:** For each option generated in the previous step, the task initiates a separate, independent analysis. A new prompt is created for
   each option, asking a `ParsedAgent` to evaluate its pros, cons, feasibility, potential impact, risks, and requirements. This parallel, isolated analysis
   prevents bias and ensures each idea is evaluated on its own merits.
3. **Comparative Summary & Synthesis:** Once all options have been analyzed, the task gathers all the generated data (options and their corresponding analyses).
   It creates a final prompt for a `ChatAgent`, asking it to act as a strategic advisor. This agent synthesizes all the information to produce a comparative
   analysis, identify top recommendations, suggest potential hybrid approaches, and outline the next steps. This final step focuses on convergent thinking to
   produce an actionable conclusion.

#### Internal Mechanics

The task leverages multiple specialized agents to break down the complex cognitive workload.

- A `ParsedAgent` is used for generation and analysis to ensure the output is in a structured, predictable JSON format, which can be programmatically processed.
- A standard `ChatAgent` is used for the final summary, as this step benefits from the fluency and narrative capabilities of a less constrained model.
- The temperature setting for the LLM is adjusted based on the `include_creative_options` flag—a higher temperature encourages more novel ideas, while a lower
  one promotes more practical, grounded suggestions.

#### Output Structure

The results are presented in a user-friendly tabbed interface within the session:

- **Overview:** A summary of the task configuration and real-time progress updates.
- **Generated Options:** A list of the initial ideas generated by the first agent.
- **Option X Analysis:** A dedicated tab for each option, containing its detailed analysis (pros, cons, feasibility, etc.).
- **Summary & Recommendations:** The final synthesized report with comparative analysis and actionable advice.

The final, concise output passed to subsequent tasks is a markdown-formatted summary of the key findings and recommendations.

# CausalInferenceTask.kt

## CausalInference

### Overview

The `CausalInference` task is designed to identify the true causal relationships and root causes behind an observed effect. It goes beyond simple correlation by
applying causal reasoning principles to evidence provided from various sources, such as log files, metrics, or source code.

- **Primary Use Cases:**
  - Performing root cause analysis for system failures or bugs.
  - Debugging complex and intermittent issues.
  - Understanding emergent system behavior.
  - Distinguishing correlation from causation in data.
  - Analyzing performance regressions.
- **Expected Outcomes:**
  - A structured report detailing the causal analysis, including identified root causes, causal chains, and confidence levels.
  - An optional causal graph (Mermaid diagram) visualizing the relationships between causes and effects.
  - Recommendations for actions to address the identified root causes.

### When to Use

This task is ideal for situations where a simple analysis is insufficient to explain *why* an event occurred. Use it when you need to move from observing a
symptom to understanding the fundamental driver of a problem.

- **Specific Scenarios:**
  - A critical production service is experiencing repeated crashes, and the immediate logs don't point to a single, obvious cause.
  - After a new deployment, a key performance metric has degraded, but it's unclear which change is responsible.
  - Analyzing security incidents to understand the attack vector and the sequence of events that led to a breach.
- **Comparison with Alternative Reasoning Types:**
  - **AnalysisTask:** While a general `AnalysisTask` can summarize the content of files, `CausalInference` uses that content as evidence to construct a logical
    argument about cause and effect.
  - **ChainOfThoughtTask:** `CausalInference` is a more specialized and structured form of reasoning. It follows a specific methodology for evaluating evidence
    against causal principles, whereas `ChainOfThought` is a more general-purpose, step-by-step reasoning process.

### Configuration

#### Required Parameters

| Parameter         | Type   | Description                                | Example                                 |
|-------------------|--------|--------------------------------------------|-----------------------------------------|
| `observed_effect` | String | The observed effect or outcome to explain. | "Application latency increased by 50%." |

#### Optional Parameters

| Parameter              | Type           | Default | Description                                                                                               | Example                                                       |
|------------------------|----------------|---------|-----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `potential_causes`     | List\<String\> | `null`  | A list of potential causes to investigate.                                                                | `["Database query performance", "Network issue"]`             |
| `build_causal_graph`   | Boolean        | `true`  | If true, the task will generate a Mermaid diagram of the causal relationships.                            | `false`                                                       |
| `identify_confounders` | Boolean        | `true`  | If true, the task will attempt to identify confounding variables that could create spurious correlations. | `false`                                                       |
| `evidence_sources`     | List\<String\> | `null`  | A list of file paths or glob patterns pointing to evidence.                                               | `["logs/**/*.log", "src/main/java/com/example/Service.java"]` |
| `related_files`        | List\<String\> | `null`  | Additional files to provide context for the analysis.                                                     | `["config/production.yml"]`                                   |

### How It Works

#### Process Flow

1. **Initialization:** The task begins by validating that an `observed_effect` has been specified.
2. **Evidence Gathering:** It searches for and reads the content of files matching the patterns in `evidence_sources` and `related_files`. To manage context
   size, it truncates large files and limits the total amount of evidence collected.
3. **Prompt Construction:** A detailed prompt is constructed for the language model. This prompt includes the observed effect, the list of potential causes, the
   gathered evidence, and specific instructions to perform a rigorous causal analysis (distinguishing causation from correlation, identifying root causes,
   etc.).
4. **Causal Analysis:** The prompt is sent to the AI, which performs the analysis and returns a structured, evidence-based report in Markdown format.
5. **Graph Generation (Optional):** If `build_causal_graph` is enabled, a second request is sent to the AI, asking it to summarize its analysis as a Mermaid.js
   graph diagram.
6. **Output Display:** The results are presented in a tabbed interface, with separate tabs for the overall summary, the evidence used, the detailed causal
   analysis, and the causal graph.

#### Internal Mechanics

The core of the task is its sophisticated prompt engineering. It guides the AI to act as an expert in root cause analysis. The prompt explicitly instructs the
model to apply causal reasoning principles such as:

- **Temporal Precedence:** The cause must happen before the effect.
- **Causal Mechanism:** Explaining *how* a cause leads to an effect.
- **Counterfactual Reasoning:** Considering what would have happened if the cause had not occurred.
- **Elimination of Alternatives:** Ruling out other possible explanations.

This structured approach ensures the output is a well-reasoned analysis rather than a simple summary of the evidence.

#### Output Structure

The final output is a comprehensive report, typically including:

- **Summary:** A high-level overview of the key findings.
- **Causal Analysis:** A detailed breakdown for each identified cause, including the supporting evidence, strength of the causal link, and a confidence score.
- **Root Cause Identification:** A clear statement of the most fundamental cause(s).
- **Causal Chain:** A description of the sequence of events from the root cause to the observed effect.
- **Confounders:** A list of any identified confounding variables.
- **Recommendations:** Suggested actions to mitigate or resolve the root cause.
- **Causal Graph:** A visual Mermaid diagram illustrating the causal relationships.

# ChainOfThoughtTask.kt

## Chain of Thought Reasoning

### Overview

The Chain of Thought (CoT) task breaks down a complex problem into a sequence of explicit, interconnected reasoning steps. It mimics the human process of
thinking through a problem one step at a time, ensuring a logical and transparent path to a conclusion. Each step is generated, optionally validated for logical
consistency, and then used as the foundation for the next step.

- **Primary Use Cases:**
  - Solving complex logical puzzles or multi-step mathematical problems.
  - Debugging intricate code flows or system interactions.
  - Planning detailed, sequential projects where each step depends on the previous one.
  - Analyzing complex scenarios that require a clear, traceable line of reasoning.
  - Developing detailed arguments or explanations where the process is as important as the final answer.
- **Expected Outcomes:** A comprehensive, step-by-step breakdown of the reasoning process, culminating in a final summary and a well-supported conclusion to the
  initial problem.

### When to Use

This task is ideal when a problem is too complex for a single-shot answer and requires a methodical, verifiable approach.

- **Use this task for:**
  - Problems requiring sequential logic.
  - Situations where transparency and auditability of the reasoning process are crucial.
  - Tasks where self-correction is beneficial; the validation mechanism allows the model to identify and recover from logical errors.
- **Comparison with other reasoning types:**
  - Unlike **Brainstorming**, which is divergent and generates many independent ideas, Chain of Thought is linear and convergent, focusing on a single logical
    path.
  - Compared to **Decomposition-Synthesis**, which breaks a problem down and then rebuilds a solution, CoT focuses on a forward-moving, sequential narrative of
    thought.

### Configuration

#### Required Parameters

| Parameter           | Type   | Description                                           | Example                             |
|---------------------|--------|-------------------------------------------------------|-------------------------------------|
| `problem_statement` | String | The complex problem requiring step-by-step reasoning. | "Explain how to build a birdhouse." |

#### Optional Parameters

| Parameter         | Type         | Default       | Description                                                                                                 | Example                                        |
|-------------------|--------------|---------------|-------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| `reasoning_depth` | Int          | `10`          | The maximum number of reasoning steps to generate. The process may conclude earlier if a solution is found. | `5`                                            |
| `validate_steps`  | Boolean      | `true`        | If true, each reasoning step is validated for logical consistency before proceeding to the next.            | `false`                                        |
| `related_files`   | List<String> | `emptyList()` | A list of file paths to provide additional context for the reasoning process.                               | `["docs/materials.txt", "plans/blueprint.md"]` |

### How It Works

#### Process Flow

1. **Initialization:** The task begins with the user-provided `problem_statement`.
2. **Iterative Reasoning Loop:** The task enters a loop that continues until the `reasoning_depth` is reached or the problem is solved.
  - **Generate Step:** An AI agent generates a single `ReasoningStep`, which includes the reasoning process, a conclusion for that step, a confidence score, and
    a `next_question` to guide the subsequent step.
  - **Validate Step (Optional):** If `validate_steps` is enabled, a separate validation agent assesses the generated step for logical fallacies and consistency
    with previous steps.
  - **Self-Correction:** If validation fails, the task attempts to regenerate the step, providing the validation feedback to the AI to guide the correction.
  - **Advance:** The `next_question` from the current step becomes the input for the next iteration of the loop.
3. **Termination:** The loop concludes when:
  - The model generates a step with high confidence and no `next_question`.
  - The `reasoning_depth` limit is reached.
4. **Summarization:** A final AI agent synthesizes the entire chain of conclusions into a comprehensive summary and final answer.

#### Internal Mechanics

The task orchestrates multiple AI agents. A primary `ParsedAgent` is responsible for generating structured `ReasoningStep` objects. An optional secondary
`ParsedAgent` generates `StepValidation` objects to critique the output of the first. This agent-based system ensures that the output of one logical step
correctly informs the input of the next, creating a robust and coherent "chain" of thought.

#### Output Structure

The final output is a detailed Markdown document containing:

- **Reasoning Steps:** A numbered list of all steps taken. Each step includes:
  - **Reasoning:** The detailed thought process for that step.
  - **Conclusion:** The specific outcome or finding of the step.
  - **Confidence:** The AI's confidence level in its conclusion for that step.
  - **Next Question:** The question that prompted the following step.
- **Final Summary:** A comprehensive summary that synthesizes the conclusions from all steps into a final, coherent answer to the original `problem_statement`.

# ConstraintRelaxationTask.kt

## ConstraintRelaxation

### Overview

The Constraint Relaxation task is a sophisticated reasoning tool designed to solve complex, over-constrained problems. It works by strategically relaxing less
critical constraints to find an initial, viable solution, and then progressively reintroducing them to refine the solution. This method helps navigate problems
that might otherwise seem impossible due to conflicting requirements.

- **Primary Use Cases:**
  - Solving complex design or architectural problems with numerous, competing constraints.
  - Developing algorithms or systems where requirements are in tension.
  - Resource allocation and scheduling under tight limitations.
  - Finding creative, "good enough" solutions when a perfect solution is unattainable.
  - Understanding the relative importance and impact of different constraints on a final outcome.

- **Expected Outcomes:**
  - A final solution that satisfies the most critical constraints and attempts to accommodate as many lower-priority ones as possible.
  - A detailed, step-by-step report showing how the solution evolved as each constraint was reintroduced.
  - A final synthesis analyzing the trade-offs made, creative approaches discovered, and overall constraint satisfaction.

### When to Use

This task is ideal for situations where a direct approach to problem-solving fails due to the sheer number or conflicting nature of the requirements.

- **Specific Scenarios:**
  - When you have a long list of "must-have" features that are difficult to implement simultaneously.
  - When initial attempts to solve a problem result in no viable solution.
  - When you need to prioritize requirements and understand the consequences of dropping less important ones.

- **Comparison with Alternative Reasoning Types:**
  - Unlike a `ChainOfThoughtTask` which attempts to address all constraints at once, Constraint Relaxation simplifies the problem first, making intractable
    problems approachable. It builds towards a complete solution incrementally, which is more robust for highly constrained scenarios.

### Configuration

#### Required Parameters

| Parameter     | Type                | Description                                                              | Example                                                                   |
|---------------|---------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `problem`     | String              | A clear and detailed description of the problem to be solved.            | `"Design a mobile app for a local delivery service."`                     |
| `constraints` | Map<String, Double> | A map of constraint descriptions to their priority weights (0.0 to 1.0). | `{"Must work offline": 1.0, "UI must be blue": 0.5, "Fast loading": 0.8}` |

#### Optional Parameters

| Parameter                     | Type         | Default         | Description                                                                              | Example                                      |
|-------------------------------|--------------|-----------------|------------------------------------------------------------------------------------------|----------------------------------------------|
| `relaxation_strategy`         | String       | `"progressive"` | The strategy for initially relaxing constraints. See Advanced Options.                   | `"selective"`                                |
| `reintroduction_order`        | String       | `"by_priority"` | The order in which to reintroduce relaxed constraints. See Advanced Options.             | `"by_difficulty"`                            |
| `find_creative_satisfactions` | Boolean      | `true`          | If true, the AI will actively seek novel and unconventional ways to satisfy constraints. | `false`                                      |
| `max_iterations`              | Int          | `5`             | The maximum number of relaxation/reintroduction cycles to perform.                       | `10`                                         |
| `related_files`               | List<String> | `null`          | A list of file paths to provide additional context for the problem.                      | `["/path/to/specs.md", "/path/to/api.json"]` |

#### Advanced Options

- **`relaxation_strategy`**: Determines which constraints are initially ignored.
  - `progressive`: Relaxes the bottom 50% of constraints, ordered by priority.
  - `selective`: Relaxes all constraints with a priority weight less than 0.7.
  - `hierarchical`: Relaxes all constraints except for the highest priority tier (those with a weight >= 0.9).

- **`reintroduction_order`**: Controls the sequence for re-adding relaxed constraints.
  - `by_priority`: Reintroduces constraints starting with the highest priority first.
  - `by_difficulty`: Reintroduces constraints starting with the lowest priority first (as a proxy for "easiest").
  - `by_dependency`: (Currently defaults to `by_priority`) Intended for future enhancement where constraint dependencies are analyzed.

### How It Works

#### Process Flow

1. **Analysis**: The task begins by analyzing the provided constraints and ordering them based on the chosen `reintroduction_order`.
2. **Initial Relaxation**: Using the selected `relaxation_strategy`, it identifies a subset of constraints to temporarily ignore.
3. **Solve Relaxed Problem**: The AI generates an initial solution for the problem considering only the active (non-relaxed) constraints.
4. **Iterative Reintroduction**: The task enters a loop, reintroducing one relaxed constraint at a time, up to `max_iterations`.
5. **Adapt Solution**: In each iteration, the AI is given the current solution and the new constraint, and is instructed to adapt the solution to satisfy the
   new requirement while maintaining satisfaction for all previously active constraints.
6. **Final Synthesis**: After the loop completes, the AI generates a comprehensive final report that summarizes the process, analyzes the final solution's
   adherence to all constraints, and discusses any trade-offs or creative insights discovered.

#### Internal Mechanics

The core of this task is an iterative refinement loop. It leverages an AI agent to first tackle a simplified version of the problem. The output of one step
becomes the input for the next, with an added layer of complexity (a new constraint). This progressive approach allows the AI to navigate a complex solution
space without being overwhelmed, building a robust solution piece by piece.

#### Output Structure

The final output is a detailed Markdown document that includes:

- **Initial Relaxed Solution**: The first solution generated with the easiest set of constraints.
- **Progressive Reintroduction Summary**: A log of each iteration, noting which constraint was reintroduced.
- **Final Synthesis**: A comprehensive analysis containing:
  - An overview of the final solution.
  - A breakdown of how each original constraint was satisfied (or not).
  - Key insights and discoveries made during the process.
  - A discussion of trade-offs and compromises.
  - Highlights of any creative or novel approaches used.

# ConstraintSatisfactionTask.kt

## ConstraintSatisfaction

### Overview

The Constraint Satisfaction task is designed to solve complex problems that involve multiple, often competing, requirements. It systematically finds optimal or
near-optimal solutions by balancing mandatory (hard) constraints with desirable (soft) constraints, each of which can be weighted by importance.

- **Primary use cases:**
  - Making architectural decisions that balance performance, maintainability, and cost.
  - Optimizing resource allocation with competing priorities.
  - Finding the best configuration for a system with multiple objectives.
  - Performing detailed trade-off analysis for design choices.
  - Solving scheduling or planning problems with complex rules.
- **Expected outcomes:** A detailed report that proposes a solution, verifies that all hard constraints are met, scores how well soft constraints are satisfied,
  and provides clear reasoning for the trade-offs made.

### When to Use

This task is ideal for situations where a decision must adhere to strict rules while also optimizing for several weighted goals.

- **Specific scenarios where this reasoning type excels:**
  - You have a clear set of non-negotiable requirements (e.g., "The application must support 10,000 concurrent users").
  - You also have a set of desirable goals with varying levels of importance (e.g., "Minimize latency" is more important than "Minimize infrastructure cost").
  - The problem has too many variables and trade-offs to easily resolve manually.
- **Problem types best suited for this approach:**
  - System Design & Architecture
  - Project Planning & Scheduling
  - Resource Management
  - Configuration Optimization
- **Comparison with alternative reasoning types:**
  - **Brainstorming:** Less structured. Use Brainstorming for open-ended idea generation, not for finding a specific solution that meets strict criteria.
  - **Game Theory:** Use Game Theory for strategic decision-making in competitive or adversarial scenarios, not for single-agent optimization problems.
  - **Genetic Optimization:** A more complex, evolutionary approach. Use Constraint Satisfaction for problems that can be modeled with discrete variables and
    constraints, and Genetic Optimization for continuous or highly complex search spaces where an evolutionary algorithm is more suitable.

### Configuration

#### Required Parameters

| Parameter             | Type   | Description                                                              | Example                                                    |
|-----------------------|--------|--------------------------------------------------------------------------|------------------------------------------------------------|
| `problem_description` | String | A clear and detailed description of the problem that needs to be solved. | "Design a database schema for a social media application." |

#### Optional Parameters

| Parameter          | Type                | Default        | Description                                                                                                                          | Example                                                                         |
|--------------------|---------------------|----------------|--------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `hard_constraints` | List<String>        | `[]`           | A list of non-negotiable requirements that the final solution *must* satisfy.                                                        | `["User data must be encrypted at rest.", "The system must comply with GDPR."]` |
| `soft_constraints` | Map<String, Double> | `{}`           | A map of desirable goals to their relative importance (weights from 0.0 to 1.0).                                                     | `{"Minimize query latency": 0.9, "Reduce monthly cost": 0.6}`                   |
| `search_strategy`  | String              | `backtracking` | The conceptual search strategy for the AI to use. Options: `backtracking` (systematic), `forward` (greedy), `local` (hill-climbing). | `forward`                                                                       |
| `max_iterations`   | Int                 | `100`          | The maximum number of conceptual search iterations to perform, controlling the trade-off between search depth and time.              | `200`                                                                           |
| `related_files`    | List<String>        | `[]`           | A list of file paths to provide additional context for the problem.                                                                  | `["src/main/kotlin/com/example/User.kt"]`                                       |

### How It Works

#### Process Flow

1. **Initialization:** The task starts by receiving the problem description, hard constraints, soft constraints, and search strategy.
2. **Context Gathering:** It collects relevant information from the workspace and previous tasks to build a comprehensive context.
3. **Prompt Construction:** A detailed prompt is formulated, instructing the AI to act as a constraint satisfaction expert. The prompt includes the problem, all
   constraints, the chosen search strategy, and the gathered context. It also specifies the required output format.
4. **Solution Generation:** The AI analyzes the problem, identifies decision variables, and applies the specified search strategy to find a solution that
   satisfies all hard constraints while maximizing the weighted score of the soft constraints.
5. **Output Formatting:** The AI generates a structured report detailing the solution, its reasoning, constraint satisfaction analysis, and potential
   alternatives.
6. **Review and Completion:** The final report is presented to the user for review and approval.

#### Internal Mechanics

The task leverages a powerful language model to perform the complex reasoning required for constraint satisfaction. By providing a highly structured prompt, the
task guides the model to:

- Deconstruct the problem into key variables and constraints.
- Conceptually simulate a search algorithm (like backtracking or local search) to explore the solution space.
- Evaluate potential solutions against the defined hard and soft constraints.
- Articulate the trade-offs and justify the final proposed solution based on the weighted importance of the soft constraints.

#### Output Structure

The final output is a comprehensive markdown report with the following sections:

- **Solution Overview:** A brief summary of the proposed solution.
- **Decision Variables:** A list of the key decisions made to arrive at the solution.
- **Hard Constraint Satisfaction:** A checklist verifying that each hard constraint has been met.
- **Soft Constraint Optimization:** A breakdown of how well each soft constraint was satisfied, including a satisfaction score and an explanation of any
  trade-offs.
- **Overall Score:** A weighted sum representing the overall quality of the solution based on soft constraint satisfaction.
- **Reasoning:** A detailed explanation of why the proposed solution is optimal or the best possible compromise.
- **Alternative Solutions:** A discussion of other viable options and why they were not chosen, if applicable.

# CounterfactualAnalysisTask.kt

## Counterfactual Analysis

### Overview

Counterfactual Analysis is a reasoning task designed to explore "what-if" scenarios. It systematically analyzes an actual, real-world event or decision against
a set of hypothetical alternatives (counterfactuals) to understand causal relationships and the potential impacts of different choices.

- **Primary Use Cases:**
  - **Retrospective Analysis:** Understanding what might have happened if a past decision had been made differently.
  - **Strategic Planning:** Evaluating potential strategies by comparing their likely outcomes under various conditions.
  - **Risk Assessment:** Exploring how changing certain variables could mitigate risks or lead to different failures.
  - **Causal Inference:** Strengthening understanding of cause-and-effect by examining what would happen in the absence of a supposed cause.
  - **Decision Validation:** Assessing the robustness of a decision by comparing it to viable alternatives.
- **Expected Outcomes:** A detailed report that provides an in-depth analysis of the actual scenario, a similar analysis for each counterfactual, and a final
  comparative analysis that highlights key differences, causal factors, and actionable insights.

### When to Use

This task is ideal when you need to move beyond simple prediction to understand the *why* behind an outcome. It is particularly effective for project
post-mortems, strategic planning, policy impact assessments, and historical event analysis.

- **Problem Types:** Best suited for problems where you need to dissect causality, evaluate the significance of specific factors, and make more informed future
  decisions by learning from hypothetical alternatives.
- **Comparison with Alternatives:**
  - vs. **Chain of Thought:** While Chain of Thought follows a single, linear reasoning path, Counterfactual Analysis explores multiple, parallel reasoning
    paths branching from a common starting point.
  - vs. **Brainstorming:** Brainstorming is generative and divergent, aiming for a wide range of ideas. Counterfactual Analysis is more analytical and focused,
    examining the specific consequences of predefined alternative scenarios.

### Configuration

#### Required Parameters

| Parameter         | Type           | Description                                           | Example                                                                      |
|-------------------|----------------|-------------------------------------------------------|------------------------------------------------------------------------------|
| `actual_scenario` | String         | The real-world scenario or decision that occurred.    | "We launched the marketing campaign on a Monday."                            |
| `counterfactuals` | List of String | A list of alternative "what-if" scenarios to explore. | `["What if we had launched on a Friday?", "What if we doubled the budget?"]` |

#### Optional Parameters

| Parameter          | Type           | Default | Description                                                                   | Example                                                                      |
|--------------------|----------------|---------|-------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| `compare_outcomes` | Boolean        | `true`  | If true, generates a final section comparing all scenarios.                   | `false`                                                                      |
| `control_factors`  | List of String | `null`  | Factors to be held constant across all scenarios for a controlled comparison. | `["The target audience remains the same.", "The ad creative is unchanged."]` |
| `related_files`    | List of String | `null`  | Paths to files providing additional context (e.g., historical data, reports). | `["reports/q1_performance.txt", "data/user_demographics.csv"]`               |

### How It Works

#### Process Flow

1. **Initialization:** The task begins with a defined `actual_scenario` and a list of `counterfactuals`.
2. **Context Gathering:** It reads the content of any specified `related_files` and incorporates results from previous tasks to provide rich context for the
   analysis.
3. **Individual Analysis:** The task first sends the actual scenario to an LLM for a detailed breakdown of its elements, outcomes, risks, and causal links. It
   then repeats this process independently for each counterfactual scenario.
4. **Comparative Analysis:** If `compare_outcomes` is enabled, the task synthesizes the individual analyses. It prompts the LLM to compare the outcomes,
   identify key differentiating factors, and extract lessons learned across all scenarios.
5. **Report Generation:** All individual analyses and the final comparison are compiled into a single, structured Markdown report.

#### Internal Mechanics

The task uses a `ChatAgent` to interact with an LLM. It relies on two primary, carefully crafted prompts:

- **`analyzeScenario`:** This prompt focuses on a single case (either actual or counterfactual). It instructs the AI to identify key elements, potential
  outcomes, risks, opportunities, and causal relationships within that specific scenario.
- **`compareScenarios`:** This prompt provides the AI with all the individual analyses. It asks for a higher-level synthesis, focusing on the differences
  between scenarios, the impact of key variables, and overall recommendations.

#### Output Structure

The final output is a comprehensive Markdown document with the following sections:

- **Counterfactual Analysis Results:** The main title of the report.
- **Actual Scenario:** A description of the baseline case, followed by its detailed analysis.
- **Counterfactual Scenario [N]:** A separate, numbered section for each alternative scenario, containing its description and analysis.
- **Comparative Analysis:** (If enabled) A concluding section that contrasts all scenarios, discusses the impact of key variables, and provides overall insights
  and recommendations.

# DecompositionSynthesisTask.kt

## Decomposition & Synthesis

### Overview

The Decomposition & Synthesis task implements a "divide and conquer" reasoning strategy. It breaks down a large, complex problem into smaller, more manageable
subproblems, solves each one individually, and then synthesizes the individual solutions into a coherent, comprehensive final answer.

- **Primary Use Cases:**
  - Solving complex design or engineering problems (e.g., "Design a scalable microservices architecture for an e-commerce site").
  - Planning multi-step projects with interdependent tasks.
  - Performing detailed root cause analysis by breaking down a failure into its constituent parts.
  - Answering broad, multifaceted questions that require exploring several sub-topics.
  - Generating complex documents or reports by tackling each section as a subproblem.

- **Expected Outcomes:**
  - A structured breakdown of the original problem, including subproblems and their dependencies.
  - Individual, focused solutions for each identified subproblem.
  - A final, integrated solution that combines the sub-solutions.
  - A validation report assessing the coherence and completeness of the final solution.

### When to Use

This task is ideal for problems that are too large or complex to be solved effectively in a single step. It excels when a problem can be logically segmented.

- **Specific Scenarios:**
  - Use when a direct approach fails to yield a satisfactory or complete answer.
  - When the problem has distinct functional components, sequential stages, or a hierarchical structure.
  - When you need to ensure all facets of a complex issue are addressed systematically.

- **Comparison with Alternative Reasoning Types:**
  - **Chain of Thought:** Solves problems linearly, step-by-step. Decomposition is better for problems with parallel or non-linear sub-components.
  - **Brainstorming:** Generates a wide array of ideas without structure. Decomposition imposes a logical structure to guide the solution process.
  - **Hierarchical Planning:** Focuses on creating a plan of tasks. Decomposition & Synthesis goes a step further by executing the sub-tasks (solving
    subproblems) and integrating the results.

### Configuration

#### Required Parameters

| Parameter         | Type   | Description                                                                   | Example                                                           |
|-------------------|--------|-------------------------------------------------------------------------------|-------------------------------------------------------------------|
| `complex_problem` | String | The main complex problem or question that needs to be broken down and solved. | `"Create a comprehensive marketing plan for a new SaaS product."` |

#### Optional Parameters

| Parameter                | Type    | Default      | Description                                                                                                                           | Example                            |
|--------------------------|---------|--------------|---------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|
| `decomposition_strategy` | String  | `functional` | The method used to break down the problem. Options: `functional`, `temporal`, `spatial`, `hierarchical`.                              | `temporal`                         |
| `max_depth`              | Integer | `3`          | The maximum number of recursive levels for decomposition. Prevents excessively granular breakdowns.                                   | `2`                                |
| `synthesize_solution`    | Boolean | `true`       | If true, the task will attempt to combine all subproblem solutions into a single, integrated final solution.                          | `false`                            |
| `validate_coherence`     | Boolean | `true`       | If true, an additional step is run to check the final synthesized solution for logical consistency, completeness, and contradictions. | `false`                            |
| `related_files`          | List    | `null`       | A list of file paths to provide additional context for solving the problem and its subproblems.                                       | `["/path/to/market_research.pdf"]` |

#### Advanced Options

- **Decomposition Strategies:**
  - `functional`: Breaks the problem down by its functions or capabilities. Best for system design or process analysis. (e.g., Decomposing an app into user
    authentication, data processing, and UI).
  - `temporal`: Breaks the problem down by time or sequence of events. Ideal for planning, scheduling, or historical analysis. (e.g., Decomposing a product
    launch into pre-launch, launch day, and post-launch phases).
  - `spatial`: Breaks the problem down by physical location or components. Useful for engineering or logistical problems. (e.g., Decomposing a network design by
    datacenter, office, and remote locations).
  - `hierarchical`: Breaks the problem down by levels of abstraction, from high-level concepts to low-level details. Good for organizational planning or
    creating detailed documentation.

### How It Works

#### Process Flow

The task follows a systematic, multi-step process:

1. **Context Building:** Gathers information from any provided `related_files` and the results of previous tasks to create a contextual background.
2. **Decomposition:** The AI analyzes the `complex_problem` using the specified `decomposition_strategy` and `max_depth`. It produces a list of subproblems and
   identifies dependencies between them (i.e., which subproblems must be solved before others).
3. **Subproblem Solving:** The task performs a topological sort on the subproblems to determine the correct execution order, automatically detecting and
   resolving any circular dependencies. It then proceeds to solve each subproblem one by one, feeding the solutions of prerequisite tasks as context to
   dependent tasks.
4. **Synthesis (Optional):** If `synthesize_solution` is enabled, the AI takes all the individual subproblem solutions and integrates them into a single,
   coherent final document. It explains the approach used for the synthesis.
5. **Validation (Optional):** If `validate_coherence` is enabled, the AI reviews the synthesized solution for logical consistency, completeness, and internal
   contradictions. It reports whether the solution is coherent and provides a list of issues and suggestions for improvement.
6. **Final Output:** The task returns the synthesized solution. If synthesis is disabled, it returns a concatenation of all the individual subproblem solutions.

#### Internal Mechanics

The core of this task is the iterative application of AI agents.

- A `ParsedAgent` is first used to analyze the problem and generate a structured `ProblemDecomposition` object.
- For each subproblem, another `ParsedAgent` is invoked to generate a `SubproblemSolution`.
- Finally, separate agents are used for the synthesis and validation steps, again parsing the output into structured `SynthesizedSolution` and
  `CoherenceValidation` objects. This structured approach ensures reliability and allows the system to manage the complex flow of information between steps.

#### Output Structure

The results are presented in a tabbed interface for clarity:

- **Overview:** A summary of the task configuration and a real-time log of the progress through the different stages.
- **Decomposition:** Shows the rationale for the breakdown, the list of identified subproblems, their estimated complexity, and a map of their dependencies.
- **Subproblem Solutions:** Displays the detailed solution for each subproblem as it is completed.
- **Synthesis:** (If enabled) Contains the final, integrated solution and an explanation of how it was constructed.
- **Validation:** (If enabled) Shows the results of the coherence check, including any issues found and suggestions for improvement.

# DialecticalReasoningTask.kt

## DialecticalReasoningTask

### Overview

The Dialectical Reasoning Task resolves contradictions between two opposing viewpoints (a thesis and an antithesis) by generating a higher-level synthesis. It
systematically analyzes each position, identifies the core tensions between them, and then iteratively builds a more comprehensive understanding that
incorporates the valid insights from both sides.

- **Primary Use Cases:**
  - Resolving architectural debates or conflicting technical approaches.
  - Reconciling contradictory stakeholder requirements.
  - Exploring complex design philosophies or strategic decisions.
  - Analyzing and synthesizing opposing arguments in a debate or policy analysis.
  - Finding innovative solutions that transcend simple compromises.
- **Expected Outcomes:** A structured report detailing the analysis of the thesis, antithesis, their contradictions, and a final, integrated synthesis that
  offers a more nuanced and complete perspective.

### When to Use

This task is ideal for situations where you have two well-defined, opposing ideas that need to be reconciled rather than simply choosing one over the other.

- **Specific Scenarios:** Use this task when a problem has inherent tensions and a simple "right" answer is unlikely. It's effective for moving beyond a
  stalemate by creating a new frame of reference.
- **Problem Types:** Best suited for complex, abstract, or strategic problems where understanding the relationship between opposing forces is key to finding a
  robust solution.
- **Comparison with Alternatives:**
  - Unlike **Chain of Thought**, which follows a single, linear path of reasoning, Dialectical Reasoning explores a conflict between two paths to create a
    third.
  - Unlike **Brainstorming**, which is a divergent process to generate many ideas, this is a convergent process aimed at integrating two specific, conflicting
    ideas into a single, more refined one.

### Configuration

The task's behavior is configured through the `DialecticalReasoningTaskExecutionConfigData` class.

#### Required Parameters

| Parameter    | Type   | Description                                    | Example                                        |
|--------------|--------|------------------------------------------------|------------------------------------------------|
| `thesis`     | String | The first statement, position, or argument.    | "All services should be microservices."        |
| `antithesis` | String | The opposing statement, position, or argument. | "A monolithic architecture is more efficient." |

#### Optional Parameters

| Parameter            | Type         | Default          | Description                                                                                               | Example                                                            |
|----------------------|--------------|------------------|-----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `context`            | String       | "general domain" | Provides background or domain information to ground the analysis.                                         | "We are designing a new e-commerce platform for a small business." |
| `synthesis_levels`   | Int          | 3                | The number of iterative synthesis rounds to perform. Must be between 1 and 5.                             | `2`                                                                |
| `preserve_strengths` | Boolean      | true             | If true, the synthesis process is instructed to explicitly retain the valuable aspects of both positions. | `false`                                                            |
| `related_files`      | List<String> | null             | A list of file glob patterns. The content of matching files will be included as additional context.       | `["src/main/docs/architecture.md", "requirements/*.txt"]`          |
| `task_dependencies`  | List<String> | null             | A list of task IDs that must be completed before this task can run.                                       | `["task_123", "task_456"]`                                         |

#### Advanced Options

- **`synthesis_levels`**: This is a powerful parameter that controls the depth of the analysis.
  - `1`: A single synthesis is generated, providing a direct resolution.
  - `3-5`: The task treats each new synthesis as a new thesis, considers its potential limitations, and generates an even higher-level synthesis. This allows
    for a progressively deeper and more abstract understanding of the core problem.
- **`preserve_strengths`**: Setting this to `false` encourages the AI to move beyond both original positions entirely, potentially leading to a more radical or
  unexpected synthesis, but with the risk of losing valuable insights from the original arguments.

### How It Works

#### Process Flow

The task follows a structured, multi-step process modeled on the classical dialectical method.

1. **Context Gathering**: The task collects all provided context, including the `context` parameter, the content of `related_files`, and the results from any
   dependent tasks.
2. **Thesis Analysis**: An AI agent performs a thorough analysis of the `thesis`, identifying its core claims, assumptions, strengths, and potential
   limitations.
3. **Antithesis Analysis**: A second AI agent analyzes the `antithesis` in the same manner, paying special attention to how it directly challenges or
   contradicts the thesis.
4. **Contradiction Exploration**: A third agent examines both analyses to identify the direct contradictions, underlying tensions, areas of partial agreement,
   and the root cause of the opposition.
5. **Iterative Synthesis**: This is the core reasoning loop, which runs for the number of `synthesis_levels` specified:
  - **Level 1**: An AI agent uses the thesis, antithesis, and contradiction analyses to generate a new, higher-level synthesis that attempts to resolve the
    conflict.
  - **Level 2+**: The synthesis from the previous level is treated as a new thesis. The AI considers its implicit limitations or a potential new antithesis to
    generate an even more refined synthesis.
6. **Final Integration**: A final AI agent reviews the entire journey—from the initial opposition to the final synthesis—and produces a comprehensive summary.
   This summary explains how the final synthesis resolves the original conflict and provides practical implications or actionable recommendations.

#### Internal Mechanics

The task orchestrates a series of `ChatAgent` instances, each with a highly specialized prompt tailored to a specific step in the dialectical process. By
breaking the problem down into these distinct stages (analysis, contradiction, synthesis), the task can achieve a more rigorous and insightful result than a
single, monolithic prompt. The iterative nature of the synthesis step allows the model to build upon its own reasoning, leading to progressively deeper
understanding.

#### Output Structure

The final output is a comprehensive markdown document that includes:

- A summary of the initial context.
- The detailed analysis of the **Thesis**.
- The detailed analysis of the **Antithesis**.
- A report on the **Contradictions & Tensions** between them.
- The full text of the synthesis generated at **each level**.
- A **Final Integration** section that summarizes the entire process and provides a concluding perspective with actionable insights.

# FiniteStateMachineTask.kt

## FiniteStateMachine

### Overview

The Finite State Machine (FSM) task models a concept, system, or process by analyzing its states and the transitions between them. It provides a formal and
structured way to understand and validate complex, event-driven behaviors.

- **Primary use cases:**
  - Designing and validating complex systems (e.g., user authentication, order processing).
  - Understanding and documenting intricate workflows or protocols.
  - Automatically generating comprehensive test cases for state-based behavior.
  - Identifying missing requirements, edge cases, and potential error states.
  - Analyzing network protocols or communication systems.
- **Expected outcomes:**
  - A detailed list of all identified states and their properties.
  - A comprehensive transition table mapping events to state changes.
  - A visual Mermaid state diagram.
  - An analysis of edge cases, error states, and recovery paths.
  - A validation report on FSM properties like determinism and completeness.
  - A set of generated test scenarios covering various paths and conditions.

### When to Use

This task is ideal for problems that can be broken down into a finite number of distinct states and the events that trigger transitions between them.

- **Specific scenarios where this reasoning type excels:**
  - Modeling the lifecycle of an object or entity in a system (e.g., a support ticket from "Open" to "Closed").
  - Defining the behavior of a user interface component in response to user actions.
  - Validating the logic of a business process before implementation.
- **Problem types best suited for this approach:**
  - Event-driven systems.
  - Protocol design and analysis.
  - Workflow and process modeling.
  - Systems where behavior is highly dependent on the current state.
- **Comparison with alternative reasoning types:**
  - Compared to **Chain of Thought**, which follows a single line of reasoning, FSM explores the entire state space of a system.
  - It is more structured and formal than **Brainstorming**, providing a concrete model rather than a collection of ideas.

### Configuration

#### Required Parameters

| Parameter          | Type   | Description                                         | Example                            |
|--------------------|--------|-----------------------------------------------------|------------------------------------|
| `concept_to_model` | String | The concept, system, or process to model as an FSM. | "A user authentication login flow" |

#### Optional Parameters

| Parameter                 | Type         | Default | Description                                                                        | Example                            |
|---------------------------|--------------|---------|------------------------------------------------------------------------------------|------------------------------------|
| `initial_states`          | List<String> | `[]`    | A list of known starting states for the FSM.                                       | `["Logged Out"]`                   |
| `known_events`            | List<String> | `[]`    | A list of known events or triggers that cause state transitions.                   | `["submit_credentials", "logout"]` |
| `identify_edge_cases`     | Boolean      | `true`  | If enabled, the task will analyze the FSM for edge cases and error states.         | `false`                            |
| `validate_properties`     | Boolean      | `true`  | If enabled, validates properties like determinism, completeness, and reachability. | `false`                            |
| `generate_test_scenarios` | Boolean      | `true`  | If enabled, generates test scenarios for state and transition coverage.            | `true`                             |
| `domain_context`          | String       | `null`  | The specific domain or context for the FSM to provide better focus.                | "E-commerce website"               |

### How It Works

#### Process Flow

The task executes a structured, multi-step process to build and analyze the finite state machine.

1. **State Identification:** The agent first analyzes the core concept to identify all possible states, including normal, error, initial, and terminal states.
2. **Transition Identification:** Based on the identified states, the agent determines the events that trigger transitions between them, creating a
   comprehensive transition table.
3. **Diagram Generation:** The agent generates a Mermaid state diagram to provide a clear visual representation of the FSM.
4. **Edge Case Analysis (Optional):** If enabled, the agent probes the FSM for invalid transitions, missing logic, error conditions, and recovery paths.
5. **Property Validation (Optional):** If enabled, the agent formally validates key FSM properties such as determinism (is the next state always predictable?),
   completeness (are all events handled?), and reachability (are all states accessible?).
6. **Test Scenario Generation (Optional):** If enabled, the agent creates a suite of test scenarios, including "happy path," error cases, and boundary
   conditions, to ensure full state and transition coverage.
7. **Summary:** Finally, the agent compiles all findings into a comprehensive summary with key insights and actionable recommendations.

#### Internal Mechanics

The task orchestrates a series of calls to a chat-based LLM. Each step in the process flow is guided by a detailed, specialized prompt that instructs the LLM to
perform a specific part of the analysis (e.g., "Identify all possible states," "Create a Mermaid state diagram"). The output from one step is used as context
for the next, creating a chain of analysis that builds the complete FSM model.

#### Output Structure

The results are presented in a user-friendly tabbed interface, with each tab dedicated to a specific part of the analysis:

- **Overview:** A summary of the task configuration and final status.
- **States:** A detailed list of all identified states and their descriptions.
- **Transitions:** The complete state transition table.
- **State Diagram:** The visual Mermaid diagram of the FSM.
- **Edge Cases:** (If enabled) Analysis of potential issues and error conditions.
- **Validation:** (If enabled) The FSM property validation report.
- **Test Scenarios:** (If enabled) The generated list of test cases.
- **Summary:** A high-level summary of the analysis, including key findings and recommendations.

# GameTheoryTask.kt

## GameTheory Task

### Overview

The GameTheory Task provides a comprehensive analysis of strategic interactions by applying principles of game theory. It systematically breaks down a
competitive or cooperative scenario, identifies optimal strategies, and predicts potential outcomes.

- **Primary use cases:**
  - Strategic decision-making in business and economics.
  - Competitive analysis and market strategy formulation.
  - Planning and preparation for negotiations.
  - Modeling and resolving conflicts.
  - Understanding complex multi-agent systems.
- **Expected outcomes:** A detailed, multi-faceted report including the game's structure, payoff matrix, equilibrium analysis (Nash, Dominant Strategies, Pareto
  Optimality), and actionable strategic recommendations for each player involved.

### When to Use

This task is ideal for situations where the outcome of a decision is dependent on the choices made by other rational actors.

- **Specific scenarios where this reasoning type excels:**
  - Analyzing pricing strategies in an oligopoly market.
  - Modeling arms races or political standoffs.
  - Determining bidding strategies in an auction.
  - Evaluating choices in social dilemmas like the Prisoner's Dilemma.
- **Problem types best suited for this approach:**
  - Problems with clearly defined players, actions, and outcomes.
  - Situations requiring the prediction of others' behavior to inform one's own strategy.
  - Scenarios where finding a stable "equilibrium" state is crucial.
- **Comparison with alternative reasoning types:**
  - Unlike **Brainstorming**, which generates a wide range of ideas, Game Theory provides a structured, analytical framework to find optimal strategies within
    defined constraints.
  - Compared to **Systems Thinking**, which focuses on the interconnectedness and feedback loops of a whole system, Game Theory hones in on the strategic
    choices and payoffs of individual, rational agents.

### Configuration

#### Required Parameters

| Parameter       | Type           | Description                                  | Example                                                                       |
|:----------------|:---------------|:---------------------------------------------|:------------------------------------------------------------------------------|
| `game_scenario` | String         | The strategic situation or game to analyze.  | "Two coffee shops on the same street deciding whether to lower their prices." |
| `players`       | List\<String\> | A list of the players or agents in the game. | `["Shop A", "Shop B"]`                                                        |

#### Optional Parameters

| Parameter                     | Type                         | Default           | Description                                                                                                       | Example                                         |
|:------------------------------|:-----------------------------|:------------------|:------------------------------------------------------------------------------------------------------------------|:------------------------------------------------|
| `player_strategies`           | Map\<String, List\<String\>> | `null`            | Pre-defined strategies for each player. If omitted, they will be inferred.                                        | `{"Shop A": ["Lower Price", "Maintain Price"]}` |
| `game_type`                   | String                       | "non-cooperative" | The type of game: cooperative, non-cooperative, zero-sum, repeated, sequential.                                   | "zero-sum"                                      |
| `build_payoff_matrix`         | Boolean                      | `true`            | Whether to construct a payoff matrix showing outcomes for all strategy combinations.                              | `false`                                         |
| `find_nash_equilibria`        | Boolean                      | `true`            | Whether to identify Nash equilibria, where no player benefits by changing strategy alone.                         | `false`                                         |
| `analyze_dominant_strategies` | Boolean                      | `true`            | Whether to analyze for dominant strategies that are optimal regardless of others' actions.                        | `false`                                         |
| `find_pareto_optimal`         | Boolean                      | `true`            | Whether to identify Pareto optimal outcomes where no one can be better off without making someone else worse off. | `false`                                         |
| `provide_recommendations`     | Boolean                      | `true`            | Whether to provide strategic recommendations for each player.                                                     | `false`                                         |
| `additional_context`          | String                       | `null`            | Any extra context, constraints, or information relevant to the scenario.                                          | "Shop A has a larger marketing budget."         |

#### Advanced Options

| Parameter                | Type    | Default | Description                                                                  | Example |
|:-------------------------|:--------|:--------|:-----------------------------------------------------------------------------|:--------|
| `repeated_game_analysis` | Boolean | `false` | If true, analyzes the scenario as a game played over multiple rounds.        | `true`  |
| `iterations`             | Int     | `10`    | The number of iterations to consider if `repeated_game_analysis` is enabled. | `5`     |

### How It Works

#### Process Flow

The task executes a sequential chain of analytical steps, where each step builds upon the output of the previous ones.

1. **Initialization**: The task validates the required inputs (`game_scenario`, `players`) and sets up a tabbed user interface for organized output.
2. **Context Gathering**: It compiles context from previous tasks and the `additional_context` parameter.
3. **Game Structure Analysis**: An AI agent analyzes the scenario to define the game's fundamental properties: type, players, strategy spaces, and payoff
   characteristics.
4. **Payoff Matrix Construction**: If enabled, the agent constructs a payoff matrix (typically a table) that quantifies or describes the outcome for each player
   for every possible combination of strategies.
5. **Equilibrium Analysis**: The task runs a series of analyses if enabled:
  - **Nash Equilibria**: Identifies stable strategy profiles.
  - **Dominant Strategies**: Finds strategies that are always optimal for a player.
  - **Pareto Optimality**: Determines the most efficient outcomes for the group.
6. **Repeated Game Analysis**: If enabled, it analyzes how strategies might change over multiple iterations, considering factors like reputation and
   punishment (e.g., trigger strategies).
7. **Strategic Recommendations**: The agent synthesizes all prior analysis to generate concrete, actionable advice for each player.
8. **Structured Summary**: A `ParsedAgent` reviews the entire analysis to extract key findings into a structured `GameAnalysis` object.
9. **Final Report**: The task compiles all generated sections into a final markdown report, which is returned as the task's output.

#### Internal Mechanics

The core of the task is a `ChatAgent` that is prompted sequentially. It maintains a continuous conversation, allowing each analytical step to leverage the full
context of the preceding steps. This chained-prompting approach ensures a deep and coherent analysis. For the final summary, it switches to a `ParsedAgent` to
ensure the output is structured and reliable, fitting neatly into the `GameAnalysis` data class.

#### Output Structure

The results are presented in two ways:

1. **Interactive UI**: A multi-tabbed display where each tab corresponds to a specific step of the analysis (e.g., "Game Structure", "Payoff Matrix", "
   Recommendations", "Summary"). This allows for a detailed, step-by-step review.
2. **Final Output String**: A consolidated markdown document that summarizes the most critical findings from the analysis, including the game type, players, key
   equilibria, and recommendations. This serves as the final, portable result of the task.

# GeneticOptimizationTask.kt

## Genetic Optimization

### Overview

The Genetic Optimization task uses a genetic algorithm to iteratively evolve and refine a piece of text to better meet a specified optimization goal. It
simulates natural selection by generating variations (mutations and crossovers), evaluating their fitness against defined criteria, and promoting the
best-performing variants over multiple generations.

- **Primary Use Cases:**
  - Perfecting LLM prompts for better and more consistent outputs.
  - Refining marketing copy to improve clarity, persuasiveness, and impact.
  - Optimizing technical documentation for accuracy and readability.
  - Improving the overall quality and effectiveness of any piece of text.
  - Brainstorming creative variations of a core idea.
- **Expected Outcomes:** A highly optimized version of the initial text that scores significantly better against the defined criteria, along with a detailed
  analysis of the evolutionary process, strategy effectiveness, and fitness progression.

### When to Use

This task is ideal when you have a good starting point for a piece of text but believe it can be significantly improved through structured, iterative
refinement. It excels in scenarios with complex optimization goals that involve multiple, sometimes competing, criteria (e.g., being both concise and
comprehensive).

- **Problem Types Best Suited:**
  - Creative text refinement.
  - Multi-objective optimization of text.
  - Exploring a wide solution space of potential improvements.
- **Comparison with Alternatives:**
  - Compared to a simple "improve this text" prompt, Genetic Optimization is more systematic and robust. It explores a wider range of possibilities by
    maintaining a diverse population of candidates, making it less likely to get stuck in a local optimum and more likely to produce novel and effective
    results.

### Configuration

#### Required Parameters

| Parameter           | Type   | Description                                                                                           | Example                                     |
|---------------------|--------|-------------------------------------------------------------------------------------------------------|---------------------------------------------|
| `initial_text`      | String | The initial text to be optimized. This serves as the seed for the first generation of the algorithm.  | "Our new software helps teams collaborate." |
| `optimization_goal` | String | The primary goal or criteria for optimization. This defines the "fitness function" for the evolution. | "Make the text more persuasive and urgent." |

#### Optional Parameters

| Parameter             | Type                 | Default                                 | Description                                                                                                             | Example                                                              |
|-----------------------|----------------------|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| `num_generations`     | Int                  | `5`                                     | The number of evolutionary cycles to run.                                                                               | `10`                                                                 |
| `population_size`     | Int                  | `6`                                     | The number of text variants to create and evaluate in each generation.                                                  | `10`                                                                 |
| `selection_size`      | Int                  | `2`                                     | The number of top-performing candidates from one generation that are kept to "breed" the next generation.               | `3`                                                                  |
| `mutation_strategies` | List\<String>        | `["rephrase", "simplify", "elaborate"]` | The list of mutation techniques to apply. Common strategies include 'rephrase', 'simplify', 'elaborate', 'restructure'. | `["rephrase", "emphasize", "soften"]`                                |
| `enable_crossover`    | Boolean              | `true`                                  | If true, the task will combine the best traits from the top two candidates to create a new "offspring" variant.         | `false`                                                              |
| `evaluation_weights`  | Map\<String, Double> | `{"clarity": 0.35, ...}`                | A map defining the criteria and their weights for scoring each variant. The sum of weights should ideally be 1.0.       | `{"persuasiveness": 0.6, "clarity": 0.4}`                            |
| `constraints`         | List\<String>        | `null`                                  | A list of additional constraints or rules that all text variants must adhere to.                                        | `["Must be under 280 characters.", "Avoid using technical jargon."]` |

### How It Works

#### Process Flow

1. **Initialization:** The process begins with the `initial_text`, which forms the entire population of Generation 0. This text is evaluated against the
   `optimization_goal` to establish a baseline fitness score.
2. **Evolution Loop (per generation):**
   a.  **Selection:** The top-performing variants from the previous generation (the "survivors") are selected to pass on their traits. The number of survivors
   is determined by `selection_size`.
   b.  **Mutation:** New variants are created by applying random `mutation_strategies` (e.g., 'rephrase', 'simplify') to the selected survivors.
   c.  **Crossover (Optional):** If `enable_crossover` is true, the best traits from the top two survivors are combined to create a new "offspring" variant,
   adding more diversity to the population.
   d.  **Evaluation:** All new variants, along with the survivors from the previous generation, are evaluated and scored against the `optimization_goal` and
   `evaluation_weights`.
3. **Termination:** The evolution loop continues for the specified `num_generations`.
4. **Result:** The single best-performing variant found across all generations is presented as the final, optimized output.

#### Internal Mechanics

The task relies on two core LLM-driven agents that work in tandem:

- **Mutation Agent:** This agent is responsible for creating new text variants. It takes a parent text and a specific strategy (e.g., "simplify") and generates
  a new, mutated version that adheres to that strategy.
- **Evaluation Agent:** This agent acts as an objective judge. It takes a text variant and scores it against the defined criteria and weights, providing a
  detailed fitness report that includes an overall score, strengths, and weaknesses. This feedback is crucial for guiding the selection process in the next
  generation.

#### Output Structure

The task's output is a comprehensive report delivered across multiple UI tabs, allowing for a deep dive into the optimization process:

- **Overview:** A summary of the initial configuration and a high-level log of the process, showing the best score from each generation.
- **Generation [N]:** A dedicated tab for each generation, providing a detailed breakdown of the population, including the top variants, their scores,
  strengths, weaknesses, and the strategies that created them.
- **Evolution Analysis:** A final summary tab that visualizes the entire process. It includes:
  - A table showing fitness progression (best and average scores) over time.
  - An analysis of the effectiveness of different mutation and crossover strategies.
  - A direct comparison of the initial text and the final optimized text, with a detailed breakdown of the improvements.
- The primary result is the final, best-performing text variant, which is presented clearly in the final report.

# LateralThinkingTask.kt

## Lateral Thinking

### Overview

The Lateral Thinking task is a structured process designed to break conventional thinking patterns and generate innovative, unconventional solutions to a given
problem. It systematically applies a variety of creative techniques to reframe the problem, challenge assumptions, and explore novel perspectives that would be
missed by traditional, linear analysis.

- **Primary use cases:**
  - Overcoming creative blocks or design impasses.
  - Generating novel ideas for product development or innovation.
  - Developing unconventional business or marketing strategies.
  - Solving complex, ill-defined problems where standard solutions fail.
  - Challenging and validating core assumptions in a project or plan.
- **Expected outcomes:**
  - A diverse set of creative and unconventional ideas.
  - A detailed analysis of each idea, including its novelty, feasibility, benefits, and challenges.
  - Synthesized insights that reveal common themes and patterns across different creative approaches.
  - A list of recommended, actionable, and unconventional strategies.
  - An optional, detailed feasibility analysis of the most promising ideas.

### When to Use

This task is ideal when you need to move beyond incremental improvements and discover breakthrough solutions.

- **Specific scenarios where this reasoning type excels:**
  - When a team is stuck and keeps generating the same types of ideas.
  - During the initial stages of a project to explore the entire solution space.
  - When a disruptive change in the market requires a fundamental rethinking of strategy.
- **Problem types best suited for this approach:**
  - Problems that are not well-defined and have no clear "right" answer.
  - Challenges that require a paradigm shift rather than optimization.
  - Strategic questions like "What business should we be in?" or "How can we create a new market?"
- **Comparison with alternative reasoning types:**
  - **vs. Chain of Thought:** Chain of Thought follows a logical, step-by-step path. Lateral Thinking deliberately breaks logical sequences to jump to new
    patterns. Use CoT for deduction, Lateral Thinking for invention.
  - **vs. Brainstorming:** Standard brainstorming is often unstructured. Lateral Thinking provides a structured framework with specific techniques (like
    Reversal or Random Stimulus) to force the mind out of its usual ruts.
  - **vs. Decomposition/Synthesis:** Decomposition breaks a problem into smaller, manageable parts. Lateral Thinking reframes the entire problem from multiple,
    often illogical, angles.

### Configuration

#### Required Parameters

| Parameter | Type   | Description                                      | Example                                    |
|:----------|:-------|:-------------------------------------------------|:-------------------------------------------|
| `problem` | String | The problem or challenge to approach creatively. | "How can we reduce customer churn by 50%?" |

#### Optional Parameters

| Parameter              | Type           | Default                                                                              | Description                                                                                                       | Example                                                                    |
|:-----------------------|:---------------|:-------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------|
| `techniques`           | List\<String\> | `["reversal", "random_stimulus", "challenge_assumptions", "exaggeration", "escape"]` | The specific lateral thinking techniques to apply. See Advanced Options for a full list.                          | `["reversal", "metaphor"]`                                                 |
| `num_alternatives`     | Int            | `5`                                                                                  | The number of alternative solutions to generate for each selected technique.                                      | `3`                                                                        |
| `evaluate_feasibility` | Boolean        | `true`                                                                               | If true, the task will perform a final, detailed evaluation of the generated ideas' practical feasibility.        | `false`                                                                    |
| `domain_context`       | String         | `null`                                                                               | Provides specific domain knowledge or context to constrain the thinking process, making the ideas more relevant.  | "We are a B2B SaaS company in the logistics industry."                     |
| `constraints`          | List\<String\> | `null`                                                                               | A list of additional constraints or requirements to consider, which can also be challenged by certain techniques. | `["The solution must not require new hardware.", "Budget is under $50k."]` |

#### Advanced Options

The `techniques` parameter allows you to control the creative process. Each technique forces a different kind of mental leap:

- **`reversal`**: Reverses the problem statement (e.g., "How can we *increase* churn?") to uncover hidden assumptions and then transforms the insights back into
  positive solutions.
- **`random_stimulus`**: Introduces a completely unrelated word or concept (e.g., "jazz music") and forces connections back to the problem to spark novel ideas.
- **`challenge_assumptions`**: Explicitly lists the core assumptions about the problem (e.g., "We assume customers leave due to price") and then generates
  solutions based on those assumptions being false.
- **`exaggeration`**: Takes a parameter of the problem to an extreme (e.g., "What if we had 10 million customers?" or "What if we had only 10?") to reveal
  solutions at different scales.
- **`escape`**: Temporarily ignores a major constraint (e.g., "What if the budget was unlimited?") to ideate freely, then adapts those ideas back to the real
  world.
- **`metaphor`**: Applies the logic and structure of a metaphor from a different domain (e.g., "How is our customer support like a hospital emergency room?") to
  find new approaches.
- **`provocation`**: Uses a deliberately absurd or provocative statement (e.g., "What if customers paid *us* to report bugs?") to shock the thinking process
  onto a new track.

### How It Works

#### Process Flow

The task executes a multi-step, multi-agent workflow to ensure both creativity and analytical rigor.

1. **Initialization**: The task starts by creating an "Overview" tab that summarizes the problem statement and the selected configuration.
2. **Technique Application**: The task iterates through each selected `technique`. For each one:
   a. A specialized prompt is constructed to guide an AI agent in applying that specific technique.
   b. The agent generates a set of `LateralIdea` objects, each containing a title, description, breakthrough aspect, benefits, challenges, and initial scores
   for novelty and feasibility.
   c. The results are displayed in a new, dedicated tab for that technique.
3. **Cross-Technique Synthesis**: After all techniques have been applied, a new agent analyzes the complete set of generated ideas. It identifies common themes,
   highlights the most powerful insights, and formulates a list of recommended unconventional approaches.
4. **Feasibility Evaluation (Optional)**: If `evaluate_feasibility` is true, a final agent performs a practical assessment of the most promising ideas. It ranks
   them, suggests hybrid approaches, and identifies which ideas require further exploration or prototyping.
5. **Final Summary**: The task concludes by generating a comprehensive summary report that consolidates the top ideas, synthesized insights, and the feasibility
   assessment into a single, actionable document.

#### Internal Mechanics

The task leverages a "multi-agent" system. It uses `ParsedAgent` instances, which are specialized to return structured data (like a list of ideas), for the
technique application and feasibility steps. For the more analytical synthesis step, it uses a standard `ChatAgent` to produce a narrative report. This
combination ensures that the creative, idea-generation phases are captured in a structured way, while the synthesis phase allows for more nuanced, high-level
reasoning.

#### Output Structure

The final output is a `LateralThinkingResult` object containing all generated data, which is presented to the user in a multi-tabbed interface:

- **Overview Tab**: Tracks the overall progress and final metrics (total ideas, time taken, etc.).
- **Technique Tabs**: One tab for each technique applied, showing the specific ideas and insights generated.
- **Synthesis Tab**: Contains the cross-technique analysis and recommended approaches.
- **Feasibility Tab**: (If enabled) Shows the detailed feasibility report.
- **Summary Tab**: A final, clean report that presents the most important findings, including the top-ranked breakthrough ideas, synthesized insights, and
  executive summary.

# MetaCognitiveReflectionTask.kt

## MetaCognitiveReflection

### Overview

The `MetaCognitiveReflectionTask` performs a critical analysis of the reasoning process and output of a previously executed task. It's a form of "thinking about
thinking," designed to improve the quality, robustness, and reliability of AI-generated solutions by identifying weaknesses, biases, and unexamined assumptions.

- **Primary use cases:**
  - Critically reviewing the solution or analysis from another task.
  - Identifying underlying assumptions, potential cognitive biases, and logical fallacies.
  - Evaluating the confidence level and completeness of a conclusion.
  - Surfacing knowledge gaps and suggesting areas for further investigation.
  - Recommending specific, actionable improvements to a reasoning process.
- **Expected outcomes:** A structured, detailed report that critiques a specified task's output, highlighting strengths, weaknesses, and providing suggestions
  for enhancement.

### When to Use

This task is ideal for situations where the quality and justification of a result are as important as the result itself.

- **Specific scenarios where this reasoning type excels:**
  - As a quality assurance step in a multi-task plan before finalizing a complex solution.
  - When debugging a plan where a previous task produced a flawed or unexpected result.
  - To increase the robustness of a solution by challenging its underlying assumptions.
  - When you need to understand the certainty and potential blind spots of an AI's conclusion.
- **Problem types best suited for this approach:**
  - Complex analysis, strategic planning, or decision-making tasks where hidden biases can lead to poor outcomes.
  - Any task where the output will be used for critical applications and requires a high degree of trust.
- **Comparison with alternative reasoning types:** Unlike tasks that directly solve a problem (e.g., `RunCodeTask`, `FileModificationTask`), this is a
  meta-task. It doesn't produce a new solution but rather evaluates an existing one. It complements other reasoning tasks by adding a layer of critical
  self-assessment and refinement to the overall process.

### Configuration

#### Required Parameters

| Parameter         | Type   | Description                                                                                    | Example                    |
|:------------------|:-------|:-----------------------------------------------------------------------------------------------|:---------------------------|
| `subject_task_id` | String | The unique identifier of the task whose reasoning process and result should be reflected upon. | `"code_generation_task_1"` |

#### Optional Parameters

| Parameter              | Type         | Default                                                   | Description                                                                                                                                                   | Example                                    |
|:-----------------------|:-------------|:----------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------|:-------------------------------------------|
| `reflection_aspects`   | List<String> | `["assumptions", "biases", "alternatives", "confidence"]` | A list of specific areas to focus the critique on. Available aspects include: `assumptions`, `biases`, `alternatives`, `confidence`, `completeness`, `logic`. | `["logic", "completeness", "assumptions"]` |
| `suggest_improvements` | Boolean      | `true`                                                    | If true, the analysis will include actionable recommendations to improve the reasoning process.                                                               | `false`                                    |
| `identify_gaps`        | Boolean      | `true`                                                    | If true, the analysis will identify knowledge gaps, missing information, or areas of uncertainty.                                                             | `false`                                    |
| `evaluate_confidence`  | Boolean      | `true`                                                    | If true, the analysis will include an assessment of the confidence level of the subject task's conclusions.                                                   | `false`                                    |

#### Advanced Options

The combination of different `reflection_aspects` allows for a highly tailored critique. For example, focusing solely on `['logic', 'assumptions']` can be used
for a rigorous logical audit, while `['alternatives', 'biases']` is better for creative problem-solving and avoiding groupthink.

### How It Works

#### Process Flow

1. **Identify Subject:** The task starts by reading the `subject_task_id` from its configuration.
2. **Retrieve Context:** It fetches the final result and reasoning log of the specified subject task from the orchestrator's execution state.
3. **Construct Prompt:** A detailed prompt is dynamically constructed, instructing the LLM to act as a meta-cognitive analyst. This prompt includes the subject
   task's full output.
4. **Incorporate Aspects:** The prompt is customized with specific instructions based on the configured `reflection_aspects`, `suggest_improvements`,
   `identify_gaps`, and `evaluate_confidence` settings.
5. **Execute Analysis:** The prompt is sent to a `ChatAgent`, which queries the LLM to perform the critical reflection.
6. **Receive and Format:** The LLM returns a structured analysis in markdown format.
7. **Present Results:** The task displays the formatted analysis in the UI, often creating a summary of key insights for quick review.

#### Internal Mechanics

The core of this task is leveraging an LLM's ability to reason about reasoning itself. By providing the complete context of a previous operation and a clear set
of critical evaluation criteria, the task prompts the model to step outside the role of a "solver" and into the role of a "critic." This allows it to identify
logical fallacies, unstated assumptions, and cognitive biases that may have influenced the original output.

#### Output Structure

The final output is a comprehensive markdown document. It is typically organized into clear sections with headers corresponding to each requested reflection
aspect (e.g., "Underlying Assumptions," "Cognitive Biases"). If enabled, these are followed by sections for "Improvement Suggestions," "Knowledge Gaps," and a "
Confidence Assessment." The report uses formatting like bullet points and bold text to be easily scannable and actionable.

# MultiPerspectiveAnalysisTask.kt

## MultiPerspectiveAnalysis

### Overview

The `MultiPerspectiveAnalysis` task provides a structured framework for examining a problem or topic from several distinct viewpoints. It generates a detailed
analysis for each perspective and then, if configured, synthesizes these individual analyses into a single, coherent conclusion.

- **Primary Use Cases:**
  - Making complex architectural or design decisions.
  - Conducting thorough code reviews from multiple angles (e.g., security, performance, maintainability).
  - Developing strategic plans by considering business, technical, and user impacts.
  - Performing comprehensive risk assessments.
  - Evaluating new features from different stakeholder viewpoints.

- **Expected Outcomes:** A well-structured report that includes a detailed breakdown of the subject from each requested perspective, followed by a synthesized
  summary that highlights agreements, conflicts, and provides a balanced, unified recommendation.

### When to Use

This task is ideal for situations that require a holistic and balanced understanding of a complex issue, especially when different stakeholders have competing
interests or priorities.

- **Specific Scenarios:**
  - When a decision has significant downstream consequences across different domains (e.g., choosing a new database technology).
  - To ensure all facets of a problem are considered before committing to a solution.
  - To facilitate consensus-building by explicitly laying out the pros and cons from various viewpoints.
  - When you need to uncover hidden risks or opportunities that a single-track analysis might miss.

- **Comparison with Alternative Reasoning Types:**
  - Compared to a simple `ChainOfThoughtTask`, which follows a linear reasoning path, `MultiPerspectiveAnalysis` is better for "fan-out/fan-in" problems where
    multiple independent lines of inquiry must be explored and then integrated. It is more structured and less prone to a single line of reasoning dominating
    the outcome.

### Configuration

#### Required Parameters

| Parameter          | Type           | Description                                                      | Example                                                             |
|--------------------|----------------|------------------------------------------------------------------|---------------------------------------------------------------------|
| `analysis_subject` | String         | The central topic, question, or problem to be analyzed.          | `"Should we migrate our backend from a monolith to microservices?"` |
| `perspectives`     | List of String | A list of distinct viewpoints from which to analyze the subject. | `["technical", "business", "ethical", "user experience"]`           |

#### Optional Parameters

| Parameter             | Type           | Default | Description                                                                                                  | Example                                      |
|-----------------------|----------------|---------|--------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| `synthesize`          | Boolean        | `true`  | If true, the task will generate a final section that synthesizes all perspectives into a unified conclusion. | `false`                                      |
| `consensus_threshold` | Double         | `0.7`   | The minimum confidence threshold (0.0-1.0) for the synthesis agent to consider when assessing agreement.     | `0.8`                                        |
| `related_files`       | List of String | `null`  | A list of file paths or glob patterns to provide additional context for the analysis.                        | `["src/main/kotlin/**/*.kt", "docs/api.md"]` |
| `task_dependencies`   | List of String | `null`  | A list of other task IDs that must be completed before this one can start.                                   | `["task_123"]`                               |

### How It Works

#### Process Flow

1. **Initialization**: The task starts with the `analysis_subject` and the list of `perspectives`.
2. **Context Gathering**: It reads the content of any files specified in `related_files` to build a contextual understanding.
3. **Parallel Analysis**: For each perspective in the list, the task initiates a separate analysis. An AI agent, prompted to be an expert in that specific
   domain (e.g., a "technical expert," a "business analyst"), examines the subject.
4. **Individual Reporting**: Each agent produces a detailed report containing its findings, risks, opportunities, and recommendations from its assigned
   viewpoint. The UI displays each of these reports in a separate tab.
5. **Synthesis (Optional)**: If `synthesize` is enabled, a final "synthesis" agent is invoked. This agent receives all the individual perspective reports as
   input.
6. **Unified Conclusion**: The synthesis agent's goal is to identify common themes, highlight conflicts, and formulate a balanced, overarching recommendation
   based on the combined insights.
7. **Final Output**: The task concludes by assembling all the individual reports and the final synthesis into a single, comprehensive Markdown document.

#### Internal Mechanics

The core principle of this task is "divide and conquer." By assigning specialized agents to each perspective, it avoids the biases or blind spots that a single,
generalist agent might have. This structured parallelism ensures that each viewpoint is given a full and independent evaluation. The final synthesis step acts
as an integrator, transforming the specialized inputs into a holistic and actionable strategy.

#### Output Structure

The final output is a structured Markdown document with the following format:

```markdown

## Multi-Perspective Analysis: [Your Analysis Subject]

### [Perspective 1] Perspective

[Detailed analysis, risks, and recommendations from the first perspective...]

### [Perspective 2] Perspective

[Detailed analysis, risks, and recommendations from the second perspective...]

...

### Synthesis

[An integrated summary, highlighting agreements, conflicts, and providing a unified recommendation and next steps. This section only appears if
`synthesize` is true.]
```

# NarrativeGenerationTask.kt

## NarrativeGeneration

### Overview

The `NarrativeGeneration` task is a powerful tool for creating complete, structured narratives from a high-level concept. It extends the analytical capabilities
of `NarrativeReasoning` by adding a multi-phase generative process that takes a subject from initial analysis to a fully written story, complete with acts,
scenes, and consistent styling.

- **Primary Use Cases:**
  - Creative writing and automated story generation.
  - Developing detailed scenarios for simulations or strategic planning.
  - Creating compelling user journey narratives for product design and UX.
  - Generating narrative content for games, marketing materials, or interactive experiences.
  - Prototyping plot structures and character arcs quickly.
- **Expected Outcomes:** A fully-formed narrative in markdown format, structured into acts and scenes. The output also includes a detailed breakdown of the
  generation process, including the initial analysis, the generated outline, and statistics like word count and generation time.

### When to Use

This task is ideal when you need to generate a complete, long-form piece of creative or descriptive writing.

- **Use this task when:**
  - You have a story idea or scenario and want to see it fully fleshed out.
  - You need to create a coherent narrative that follows a specific structure (e.g., a three-act structure).
  - You want to control the writing style, point of view, and tone of the generated text.
  - The goal is content creation, not just analysis.
- **Comparison with `NarrativeReasoningTask`:**
  - `NarrativeReasoningTask` is primarily **analytical**. It takes existing information and identifies plot points, character motivations, and potential
    outcomes.
  - `NarrativeGenerationTask` is **generative**. It performs the initial analysis and then uses that foundation to *write* the story from scratch. Choose
    `NarrativeGeneration` when the end goal is a finished piece of writing.

### Configuration

#### Required Parameters

| Parameter | Type   | Description                                            | Example                                                     |
|:----------|:-------|:-------------------------------------------------------|:------------------------------------------------------------|
| `subject` | String | The core subject, theme, or scenario for the narrative | "A detective investigating a missing artist in 1920s Paris" |

#### Optional Parameters

| Parameter                | Type    | Default                | Description                                                                  | Example                                                              |
|:-------------------------|:--------|:-----------------------|:-----------------------------------------------------------------------------|:---------------------------------------------------------------------|
| `narrative_elements`     | Map     | `null`                 | Key-value pairs defining characters, setting, conflict, etc.                 | `{"protagonist": "Detective Dubois", "setting": "Montmartre, 1925"}` |
| `target_word_count`      | Int     | 5000                   | The desired total word count for the final narrative.                        | `10000`                                                              |
| `number_of_acts`         | Int     | 3                      | The number of acts to structure the story into (e.g., 3 or 5).               | `5`                                                                  |
| `scenes_per_act`         | Int     | 3                      | The average number of scenes to generate for each act.                       | `5`                                                                  |
| `writing_style`          | String  | "literary"             | The desired writing style (e.g., 'thriller', 'technical', 'humorous').       | `"thriller"`                                                         |
| `point_of_view`          | String  | "third person limited" | The narrative perspective (e.g., 'first person', 'third person omniscient'). | `"first person"`                                                     |
| `tone`                   | String  | "dramatic"             | The emotional tone of the narrative (e.g., 'suspenseful', 'reflective').     | `"suspenseful"`                                                      |
| `detailed_descriptions`  | Boolean | `true`                 | If `true`, the AI will include vivid, sensory descriptions.                  | `false`                                                              |
| `include_dialogue`       | Boolean | `true`                 | If `true`, the narrative will include character dialogue.                    | `false`                                                              |
| `show_internal_thoughts` | Boolean | `true`                 | If `true`, the AI will include the internal monologue of characters.         | `false`                                                              |
| `revision_passes`        | Int     | 1                      | The number of editing/revision passes to perform on each generated scene.    | `2`                                                                  |

### How It Works

The task employs a sophisticated, multi-phase pipeline to ensure a coherent and high-quality final product. It breaks down the complex process of creative
writing into a series of manageable steps, each handled by a specialized AI agent.

#### Process Flow

1. **Phase 1: Narrative Analysis:** The task begins by running the base `NarrativeReasoningTask`. This analyzes the `subject` and `narrative_elements` to
   identify key plot points, character motivations, and potential story arcs. This forms the logical foundation for the story.
2. **Phase 2: Outline Generation:** A story architect AI agent takes the analysis from Phase 1 and creates a detailed, scene-by-scene outline. This outline maps
   out the entire story structure, including the purpose, key events, and emotional arc for each scene within each act.
3. **Phase 3: Iterative Scene Writing:** A creative writer AI agent writes each scene from the outline one by one. To maintain continuity, the agent is given
   the context of the previously written scenes (e.g., the ending of the last scene, the emotional state of characters). This ensures a smooth flow and logical
   progression.
4. **Phase 4: Revision and Assembly:** After a scene is written, an editor AI agent can perform one or more revision passes to improve prose, pacing, and
   emotional impact. Once all scenes are generated and revised, they are compiled into a single, complete narrative document.

#### Internal Mechanics

The task uses a chain of `ParsedAgent` and `ChatAgent` instances.

- An **analyst agent** performs the initial reasoning.
- An **architect agent** is prompted to structure the story and output a `NarrativeOutline` object.
- A **writer agent** is prompted for each scene, receiving the outline details and recent context, and is expected to output a `GeneratedScene` object.
- An optional **editor agent** refines the output of the writer agent.

This multi-agent, phased approach transforms a complex creative endeavor into a structured, repeatable process, significantly improving the coherence and
quality of the final narrative compared to a single, monolithic prompt.

#### Output Structure

The user is presented with a tabbed interface that documents the entire process:

- **Overview Tab:** Shows the initial configuration, real-time progress through the phases, and final statistics (total word count, generation time, etc.).
- **Outline Tab:** Displays the complete, detailed outline generated in Phase 2.
- **Scene [X] Tabs:** Each scene gets its own tab, showing the generated text, word count, key moments, and character states at the end of that scene.
- **Complete Narrative Tab:** Presents the final, assembled story in a clean, readable markdown format.

The final result returned by the task is the complete narrative markdown file.

# NarrativeReasoningTask.kt

## NarrativeReasoningTask

### Overview

The `NarrativeReasoningTask` analyzes complex subjects or scenarios by framing them as stories. It uses narrative structures and storytelling principles to
uncover underlying dynamics, motivations, and potential outcomes that might be missed by purely logical analysis.

- **Primary Use Cases:**
  - Analyzing user journeys to understand customer experiences and pain points.
  - Mapping the evolution of a system or project to identify critical moments.
  - Planning for change management by understanding stakeholder perspectives and potential story arcs.
  - Conducting strategic foresight by exploring alternative future scenarios as different narratives.
  - Performing root cause analysis on complex failures by constructing a coherent timeline of events.

- **Expected Outcomes:** A comprehensive, multi-part report that breaks down the subject into narrative components. This includes a constructed central story,
  key plot points, character/stakeholder analysis, predicted outcomes, identified inconsistencies, and a final synthesis of insights.

### When to Use

This task is ideal when you need to understand the "why" and "how" behind a situation, especially those involving human factors, complex interactions over time,
and ambiguity.

- **Specific Scenarios:**
  - When you have a collection of events, facts, and actors but need to weave them into a coherent explanation.
  - To explore potential futures for a product, company, or project in a qualitative, story-driven way.
  - When analyzing qualitative data like interview transcripts, user feedback, or historical documents.

- **Problem Types:**
  - Problems where context, sequence of events, and motivations are critical.
  - Strategic planning and scenario analysis.
  - Qualitative analysis that requires deep interpretation rather than just data aggregation.

- **Comparison with Alternative Reasoning Types:**
  - **vs. Causal Inference:** Narrative Reasoning focuses on the story and motivations, which is good for understanding human-driven systems. Causal Inference
    is better for identifying statistically significant cause-and-effect relationships in more quantifiable systems.
  - **vs. Systems Thinking:** Systems Thinking maps the components and feedback loops of a system. Narrative Reasoning tells the story of how that system
    behaves over time, focusing on the temporal and motivational aspects. They can be highly complementary.

### Configuration

#### Required Parameters

| Parameter | Type   | Description                                           | Example                              |
|:----------|:-------|:------------------------------------------------------|:-------------------------------------|
| `subject` | String | The central topic, event, or scenario to be analyzed. | `"The launch of our new mobile app"` |

#### Optional Parameters

| Parameter                | Type             | Default | Description                                                                                          | Example                                                                                  |
|:-------------------------|:-----------------|:--------|:-----------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------|
| `narrative_elements`     | Map<String, Any> | `null`  | Key components of the story, such as characters, setting, conflict, etc.                             | `{"characters": ["dev team", "users"], "conflict": "balancing features with stability"}` |
| `construct_narrative`    | Boolean          | `true`  | If true, the task will construct a coherent narrative from the elements.                             | `true`                                                                                   |
| `identify_plot_points`   | Boolean          | `true`  | If true, the task will identify key moments like the inciting incident, climax, and resolution.      | `true`                                                                                   |
| `predict_outcomes`       | Boolean          | `true`  | If true, the task will generate several potential outcomes or resolutions for the narrative.         | `true`                                                                                   |
| `alternative_narratives` | Int              | `3`     | The number of different outcomes or alternative story paths to explore.                              | `5`                                                                                      |
| `analyze_motivations`    | Boolean          | `true`  | If true, the task will analyze the motivations, goals, and conflicts of the characters/stakeholders. | `true`                                                                                   |
| `find_inconsistencies`   | Boolean          | `true`  | If true, the task will scan the narrative for logical gaps, contradictions, or timeline issues.      | `false`                                                                                  |

### How It Works

#### Process Flow

The `NarrativeReasoningTask` executes a series of steps, each building upon the last. The user can enable or disable most steps via the configuration.

1. **Initialization:** The task sets up an overview tab displaying the subject and configuration.
2. **Narrative Construction:** If enabled, it uses a specialized AI agent to act as a storyteller, weaving the provided `narrative_elements` into a structured
   story with a title, summary, acts, and themes.
3. **Plot Point Identification:** If enabled, another agent analyzes the narrative to identify and describe critical plot points (e.g., inciting incident,
   climax, resolution).
4. **Character Analysis:** If enabled, an agent with expertise in psychology analyzes each character or stakeholder, detailing their motivations, goals,
   conflicts, and character arc.
5. **Outcome Prediction:** If enabled, a foresight agent predicts a configured number of possible outcomes (e.g., best-case, worst-case, most likely), outlining
   the factors and consequences for each.
6. **Inconsistency Check:** If enabled, a consistency-checking agent scrutinizes the narrative for logical contradictions, timeline gaps, or unmotivated
   character actions, suggesting potential resolutions.
7. **Synthesis:** Finally, a synthesis agent reviews all the generated analysis to produce a high-level summary, highlighting key insights, critical decision
   points, and recommended actions.

#### Internal Mechanics

The task orchestrates multiple calls to the language model, using the `ParsedAgent` for most steps. Each agent is given a specific role (storyteller, plot
analyst, psychologist) and a prompt tailored to its sub-task. It is instructed to return a structured object (e.g., `ParsedNarrative`, `PlotPoints`,
`CharacterAnalyses`), which ensures the output is consistent, machine-readable, and easily formatted for the user interface. This modular, step-by-step approach
breaks down the complex cognitive process of narrative analysis into manageable and verifiable stages.

#### Output Structure

The final output is presented in a multi-tabbed user interface, with each tab dedicated to a specific part of the analysis:

- **Overview:** Shows the initial configuration and a live progress log.
- **Main Narrative:** The full story constructed by the AI.
- **Plot Points:** A list of key narrative moments and their significance.
- **Characters:** Detailed profiles for each character or stakeholder.
- **Predicted Outcomes:** A breakdown of potential future scenarios.
- **Inconsistencies:** A report on any identified gaps or contradictions.
- **Synthesis:** A high-level summary with actionable insights.

The task also returns a single, consolidated Markdown document containing all of these sections, suitable for saving or sharing.

# ProbabilisticReasoningTask.kt

## Probabilistic Reasoning

### Overview

The Probabilistic Reasoning task performs a formal Bayesian analysis to reason under uncertainty. It updates initial beliefs (prior probabilities) about a set
of hypotheses based on observed evidence, resulting in updated beliefs (posterior probabilities). This task is designed to bring mathematical rigor to
decision-making in complex, uncertain environments.

- **Primary Use Cases:**
  - **Risk Assessment:** Quantifying the likelihood and impact of various risks.
  - **Diagnostic Reasoning:** Systematically identifying the root cause of a problem (e.g., bug hunting, medical diagnosis).
  - **A/B Test Analysis:** Determining the statistical significance of experimental results.
  - **Strategic Decision-Making:** Evaluating options like technology adoption or market entry where outcomes are uncertain.
  - **Resource Allocation:** Deciding how to allocate limited resources for the best expected return.

- **Expected Outcomes:**
  - A detailed report showing how the probability of each hypothesis changes in light of new evidence.
  - Calculation of expected values and risk profiles for different scenarios.
  - Identification of the most critical uncertainties that impact the decision.
  - Actionable suggestions for experiments or data collection to reduce key uncertainties.

### When to Use

This task is ideal for situations where you have:

- **Multiple Competing Hypotheses:** You can formulate several mutually exclusive explanations for a situation.
- **Quantifiable Beliefs:** You can assign an initial probability (even if it's a rough estimate) to each hypothesis.
- **Observable Evidence:** You have or can gather data/observations that help differentiate between the hypotheses.

Compared to other reasoning types, Probabilistic Reasoning is more structured and quantitative than `Brainstorming` or `Chain of Thought`. It excels at
systematically reducing uncertainty and is preferable when you need to justify a decision with a formal, data-driven analysis rather than a purely qualitative
one.

### Configuration

#### Required Parameters

| Parameter          | Type                | Description                                                                                                                   | Example                                                           |
|--------------------|---------------------|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| `hypotheses`       | Map<String, Double> | A map where keys are the hypotheses (as strings) and values are their prior probabilities. The probabilities must sum to 1.0. | `{"Hypothesis A": 0.6, "Hypothesis B": 0.3, "Hypothesis C": 0.1}` |
| `decision_context` | String              | A clear statement of the problem or decision that the analysis is intended to support.                                        | `"Determine the most likely cause of the recent server outage."`  |

#### Optional Parameters

| Parameter                    | Type         | Default                                                                                         | Description                                                                                                                     | Example |
|------------------------------|--------------|-------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|---------|
| `evidence`                   | List<String> | A list of observed facts or pieces of evidence to be used for updating the prior probabilities. | `["Log files show a spike in memory usage.", "A recent software patch was deployed."]`                                          |
| `calculate_expected_value`   | Boolean      | `true`                                                                                          | If true, the task will perform an expected value and risk analysis based on the posterior probabilities.                        | `false` |
| `identify_key_uncertainties` | Boolean      | `true`                                                                                          | If true, the task will identify the assumptions and probability estimates that have the largest impact on the final conclusion. | `false` |
| `suggest_experiments`        | Boolean      | `true`                                                                                          | If true, the task will recommend specific tests or data collection efforts to reduce the most critical uncertainties.           | `false` |
| `risk_tolerance`             | String       | `"medium"`                                                                                      | Sets the risk tolerance level for decision recommendations. Can be "low", "medium", or "high".                                  | `"low"` |

### How It Works

#### Process Flow

The task executes a structured, multi-step analysis by sequentially prompting a specialized AI agent.

1. **Input Validation:** The task first validates the configuration, ensuring that a set of hypotheses is provided and that their prior probabilities sum to
   approximately 1.0.
2. **Prior Probability Analysis:** It begins by laying out the initial state of belief, presenting the provided hypotheses and their assigned prior
   probabilities.
3. **Bayesian Update:** The core of the task. It instructs an AI agent, acting as a Bayesian expert, to evaluate each piece of evidence. The agent assesses the
   likelihood of observing the evidence under each hypothesis and uses this to calculate the updated (posterior) probabilities via Bayes' theorem.
4. **Expected Value & Risk Analysis (Optional):** If enabled, the task uses the newly calculated posterior probabilities to analyze potential outcomes. It
   prompts the agent to calculate expected values, identify worst-case scenarios, and make a decision recommendation based on the specified risk tolerance.
5. **Uncertainty Identification (Optional):** If enabled, the agent is asked to perform a sensitivity analysis to identify which assumptions or probability
   estimates are most critical to the final conclusion.
6. **Experiment Suggestion (Optional):** If enabled, the agent uses the uncertainty analysis to propose concrete, high-value experiments or data-gathering
   actions that would most effectively reduce uncertainty and clarify the decision.
7. **Report Generation:** All analyses are compiled into a comprehensive final report, which is also rendered in a tabbed user interface for easy navigation.

#### Internal Mechanics

The task orchestrates a series of targeted calls to a `ChatAgent` that is primed with a persona of an expert in Bayesian reasoning and probabilistic analysis.
It does not perform the raw mathematical calculations itself but instead structures the problem in a way that allows the LLM to apply probabilistic reasoning
rigorously. Each step builds on the output of the previous one, creating a coherent and logical chain of analysis from prior beliefs to actionable
recommendations.

#### Output Structure

The final output is a detailed markdown document that includes the following sections, depending on the configuration:

- **Likelihood Assessment:** An explanation of how each piece of evidence impacts the beliefs.
- **Posterior Probabilities:** A table showing the updated probabilities for each hypothesis.
- **Expected Value Summary:** The calculated expected value for key decisions or scenarios.
- **Risk Metrics:** An analysis of downside risk and worst-case scenarios.
- **Top Uncertainties:** A ranked list of the most critical unknowns affecting the analysis.
- **Recommended Experiments:** A prioritized list of actions to gather more information.

# ReasoningTaskUtils.kt

## Reasoning Task Utilities

### Overview

The `ReasoningTaskUtils.kt` file provides a collection of essential helper functions and extensions used across various reasoning tasks. These utilities are
designed to streamline common operations, enhance robustness by handling potential errors gracefully, and reduce code duplication.

- **Primary use cases**:
  * Validating the availability of a default chat API before use.
  * Truncating long strings for cleaner display in logs and user interfaces.
  * Safely completing tasks without crashing due to unexpected exceptions.
- **Expected outcomes**: More reliable, maintainable, and cleaner execution of reasoning tasks.

### When to Use

These utility functions are not a standalone task but are intended for internal use within other reasoning tasks. They should be used whenever a task needs to:

- Interact with the default chat API.
- Handle or display large blocks of text.
- Mark a `SessionTask` as complete, especially in complex scenarios where exceptions might occur.

### How It Works

#### `validateAndGetApi`

This function acts as a crucial precondition check to ensure a valid chat API is configured and available before a task attempts to use it. It centralizes error
handling for this common requirement.

**Process Flow:**

1. It attempts to retrieve the `defaultChatter` from the provided `OrchestrationConfig`.
2. If the API is not available (`null`), it logs an error and immediately completes the associated `SessionTask` with an error message.
3. If the API is available, it returns the `ChatInterface` instance for the calling task to use.

```kotlin
fun validateAndGetApi(
  orchestrationConfig: OrchestrationConfig,
  task: SessionTask,
  log: Logger,
  resultFn: (String) -> Unit
): ChatInterface?
```

#### `String.truncateForDisplay`

This is a `String` extension function that shortens text to a specified maximum length, adding an ellipsis to indicate that the content has been cut.

**Internal Mechanics:**

- It checks if the string's length is greater than the `maxLength` parameter (which defaults to 10,000).
- If the string is longer, it returns a new string containing the initial `maxLength` characters followed by a note explaining that the text was truncated.
- If the string is within the length limit, it is returned unmodified.

```kotlin
fun String.truncateForDisplay(maxLength: Int = 10000): String
```

#### `SessionTask.safeComplete`

This `SessionTask` extension function provides a robust way to complete a task by wrapping the `complete()` method in error-handling logic.

**Internal Mechanics:**

- It calls the `complete()` method on the `SessionTask` instance within a `try-catch` block.
- If any exception occurs during the completion process, it is caught, and a warning is logged. This prevents the exception from propagating and potentially
  crashing the execution thread.

```kotlin
fun SessionTask.safeComplete(message: String, log: Logger)
```

# SocraticDialogueTask.kt

## Socratic Dialogue

### Overview

The Socratic Dialogue task uses a structured, conversational questioning method to deeply explore a topic, idea, or hypothesis. It simulates a dialogue between
a probing "questioner" agent and a thoughtful "responder" agent to uncover underlying assumptions, explore implications, and refine understanding.

- **Primary Use Cases:**
  - Deeply analyzing a complex problem or question.
  - Uncovering hidden assumptions and biases in an argument.
  - Exploring the logical consequences and implications of a belief or proposal.
  - Clarifying definitions and understanding fundamental principles.
  - Stress-testing an idea by examining it from multiple angles.
- **Expected Outcomes:**
  - A detailed transcript of the dialogue between the questioner and responder.
  - A final synthesis that summarizes key insights, challenged assumptions, and areas for further exploration.

### When to Use

This task is ideal for situations that require critical thinking and deep exploration rather than a direct, simple answer.

- **Specific Scenarios:**
  - Use it when you have a foundational question (e.g., "Is our current marketing strategy effective?") and want to explore it thoroughly before making
    decisions.
  - Employ it to challenge your own thinking or the premises of a project.
  - Helpful for refining a vague idea into a more concrete and well-understood concept.
- **Problem Types:**
  - Ill-defined or ambiguous problems.
  - Philosophical, ethical, or strategic questions.
  - Problems where the underlying assumptions are as important as the final answer.
- **Comparison with Alternative Reasoning Types:**
  - **Chain of Thought:** More linear and focused on reaching a specific conclusion. Socratic Dialogue is more exploratory and may not lead to a single answer.
  - **Brainstorming:** Aims for a wide breadth of ideas. Socratic Dialogue aims for depth and critical examination of a single idea.
  - **Multi-Perspective Analysis:** Gathers different viewpoints. Socratic Dialogue actively challenges and deconstructs a single line of reasoning through
    questioning.

### Configuration

#### Required Parameters

| Parameter          | Type   | Description                                     | Example                                                                      |
|--------------------|--------|-------------------------------------------------|------------------------------------------------------------------------------|
| `initial_question` | String | The starting question or hypothesis to explore. | "Is decentralization always the best approach for organizational structure?" |

#### Optional Parameters

| Parameter               | Type         | Default | Description                                                               | Example                                 |
|-------------------------|--------------|---------|---------------------------------------------------------------------------|-----------------------------------------|
| `max_depth`             | Integer      | `5`     | The maximum number of question-and-answer exchanges in the dialogue.      | `10`                                    |
| `challenge_assumptions` | Boolean      | `true`  | If true, the questioner agent will actively try to challenge assumptions. | `false`                                 |
| `domain_constraints`    | List<String> | `null`  | A list of topics or domains to keep the dialogue focused on.              | `["business management", "technology"]` |
| `task_dependencies`     | List<String> | `null`  | A list of task IDs that must be completed before this task can run.       | `["task_123", "task_456"]`              |

### How It Works

#### Process Flow

1. **Initialization:** The task sets up two distinct AI agents: a "Socratic Questioner" and a "Thoughtful Responder," each with specific instructions based on
   the task configuration.
2. **Dialogue Loop:** The task enters a loop that runs for the configured `max_depth`.
  - **Response:** The Responder agent answers the current question. For the first exchange, this is the `initial_question`.
  - **Question:** The Questioner agent analyzes the Responder's answer and formulates a new, probing follow-up question designed to challenge, clarify, or
    explore implications.
  - **Iteration:** The new question becomes the input for the next cycle of the loop.
3. **Synthesis:** After the dialogue loop is complete, the entire conversation is fed to an agent to generate a final synthesis. This summary highlights key
   insights, identified assumptions, and potential contradictions.
4. **Output:** The task presents the results in a structured format, including an overview, the full dialogue transcript broken down by exchange, and the final
   synthesis.

#### Internal Mechanics

The core of this task is the dynamic between the two specialized agents:

- **The Questioner:** Is prompted to ask questions that challenge definitions, explore consequences, and identify inconsistencies. Its goal is not to answer,
  but to guide the dialogue towards deeper understanding. If `challenge_assumptions` is true, its instructions are more adversarial.
- **The Responder:** Is prompted to answer honestly, provide clear reasoning, and be open to revising its understanding based on the questioning. Its role is to
  provide the substance that the Questioner can then examine.

This structured interaction ensures a focused and critical exploration of the topic.

#### Output Structure

The output is organized into a tabbed display for clarity:

- **Overview:** A summary of the task configuration and real-time progress, concluding with performance statistics (e.g., total time, average exchange time).
- **Context:** If the task has dependencies, this tab shows the relevant information passed from previous tasks.
- **Exchange [N]:** Each question-answer pair gets its own tab, showing the question asked, the response given, and the next question generated.
- **Synthesis:** A dedicated tab containing the final summary, which identifies key insights, challenged assumptions, and conclusions drawn from the dialogue.

The final result returned by the task is a concise markdown document containing the initial question, highlights from the dialogue, and the full synthesis.

# SystemsThinkingTask.kt

## Systems Thinking Task

### Overview

The Systems Thinking Task analyzes complex systems by examining their underlying structures, feedback loops, and dynamic behaviors over time. Instead of linear
cause-and-effect, it focuses on circular causality to uncover non-obvious patterns and high-impact intervention points.

- **Primary Use Cases:**
  - Understanding the behavior of complex technical systems (e.g., CI/CD pipelines, microservice architectures).
  - Optimizing team workflows and organizational processes.
  - Identifying and mitigating unintended consequences of changes.
  - Analyzing the dynamics of technical debt or market evolution.
  - Finding the most effective places to intervene in a system for lasting improvement.
- **Expected Outcomes:** A comprehensive, multi-part report that includes a structural analysis of the system, identification of key feedback loops (visualized
  with a diagram), a list of system archetypes at play, an analysis of emergent behaviors, and a prioritized list of leverage points for intervention.

### When to Use

This task is ideal for problems that are persistent, complex, and where previous solutions have led to unexpected side effects.

- **Specific Scenarios:**
  - A team's productivity is declining despite efforts to improve it.
  - A software system is becoming increasingly brittle and hard to maintain.
  - A new policy has created unforeseen negative consequences elsewhere in the organization.
  - You need to plan a large-scale change and want to anticipate its full range of effects.
- **Problem Types:** Use this for diagnostic challenges where the "why" is more important than the "what." It excels at untangling interconnected issues rather
  than solving isolated, well-defined problems.
- **Comparison with Alternatives:** Unlike tasks that perform direct code modification or linear planning, Systems Thinking provides a holistic understanding
  *before* action is taken. It's a strategic analysis tool used to inform subsequent, more tactical tasks.

### Configuration

#### Required Parameters

| Parameter            | Type   | Description                                       | Example                                                |
|----------------------|--------|---------------------------------------------------|--------------------------------------------------------|
| `system_description` | String | A clear description of the system to be analyzed. | "Our team's software development and release process." |

#### Optional Parameters

| Parameter                   | Type         | Default      | Description                                                                 | Example                                                               |
|-----------------------------|--------------|--------------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------|
| `identify_feedback_loops`   | Boolean      | `true`       | If true, identifies and analyzes reinforcing and balancing feedback loops.  | `true`                                                                |
| `map_delays`                | Boolean      | `true`       | If true, maps out time delays and accumulations (stocks) in the system.     | `true`                                                                |
| `find_leverage_points`      | Boolean      | `true`       | If true, identifies high-impact points for intervention.                    | `true`                                                                |
| `identify_archetypes`       | Boolean      | `true`       | If true, identifies common system archetypes (e.g., "Shifting the Burden"). | `true`                                                                |
| `analyze_emergent_behavior` | Boolean      | `true`       | If true, analyzes system-level behaviors that emerge from interactions.     | `true`                                                                |
| `time_horizon`              | String       | `"6 months"` | The time frame over which to analyze the system's behavior.                 | `"1 year"`                                                            |
| `simulate_interventions`    | List<String> | `null`       | A list of potential interventions to simulate and analyze.                  | `["Introduce weekly code freezes", "Hire two more senior engineers"]` |
| `related_files`             | List<String> | `null`       | A list of file paths or glob patterns to provide additional context.        | `["docs/process/*.md", "Jenkinsfile"]`                                |

### How It Works

#### Process Flow

1. **Context Gathering:** The task begins by collecting context from previous tasks and reading the contents of any specified `related_files`.
2. **Agent Initialization:** It initializes a specialized AI agent with a detailed prompt that primes it to think in terms of systems thinking principles (
   feedback loops, delays, non-linearity, etc.).
3. **Sequential Analysis:** The agent performs a series of structured analyses, each focusing on a different aspect of the system. This includes:
  - Mapping the system's structure, components, and relationships.
  - Identifying reinforcing and balancing feedback loops, and generating a Mermaid diagram to visualize them.
  - Analyzing delays and accumulations.
  - Identifying common system archetypes.
  - Predicting emergent behaviors and unintended consequences.
  - Ranking potential leverage points according to Donella Meadows' hierarchy.
4. **Intervention Simulation (Optional):** If interventions are provided, the agent simulates the likely short, medium, and long-term effects of each one.
5. **Synthesis:** The agent generates a final synthesis that summarizes the key insights, critical dynamics, and provides a prioritized, actionable set of
   recommendations.
6. **Output Generation:** The entire analysis is rendered as a detailed, tabbed Markdown report.

#### Internal Mechanics

The core of the task is an AI agent that is guided through a structured, multi-step reasoning process. Rather than asking a single, broad question, the task
breaks the complex analysis into a sequence of focused sub-problems. Each step builds on the previous ones, allowing the agent to develop a deep, contextual
understanding of the system's dynamics. This mimics the methodical approach of a human systems thinking expert.

#### Output Structure

The final output is a comprehensive report presented in a tabbed interface, with each tab dedicated to a specific part of the analysis:

- **Overview:** A summary of the analysis parameters and final status.
- **Context:** The contextual information gathered from files and prior tasks.
- **System Structure:** A breakdown of the system's components, stocks, and flows.
- **Feedback Loops:** A detailed description of the identified loops and a Mermaid diagram.
- **Delays & Accumulations:** Analysis of time lags and their impact.
- **System Archetypes:** Identification and explanation of patterns like "Limits to Growth."
- **Emergent Behavior:** Predictions of system-level behavior and potential unintended consequences.
- **Leverage Points:** A ranked list of the most effective places to intervene.
- **Intervention Simulation:** (If configured) A scenario analysis for each proposed intervention.
- **Synthesis:** A final summary with key insights and actionable recommendations.

# TemporalReasoningTask.kt

## TemporalReasoning

### Overview

The `TemporalReasoningTask` analyzes how systems, codebases, or situations evolve over time. It constructs chronological timelines, identifies recurring
patterns and trends, analyzes the rate of change, and predicts future states based on historical data.

- **Primary use cases:**
  - Analyzing technical debt accumulation.
  - Tracking system evolution and architecture drift.
  - Investigating performance degradation over time.
  - Analyzing bug introduction timelines from commit histories or logs.
  - Understanding feature adoption and usage patterns from metrics files.
- **Expected outcomes:** A comprehensive report including a chronological timeline, identified patterns, rate of change analysis, critical transition points,
  future predictions, and a visual Mermaid diagram summarizing the timeline.

### When to Use

Use Temporal Reasoning when you need to understand the history of a system to make informed decisions about its future. It excels in scenarios that require
identifying trends, understanding cause-and-effect relationships over a period, or forecasting based on past events.

This task is ideal for:

- Conducting post-mortems on incidents by analyzing logs and metrics leading up to an event.
- Long-term strategic planning by understanding how a product or feature has evolved.
- Identifying hidden or slowly developing problems, like gradual performance degradation.

Compared to static analysis tasks, which provide a snapshot at a single point in time, Temporal Reasoning focuses on the dynamics and evolution between multiple
points.

### Configuration

#### Required Parameters

| Parameter    | Type   | Description                                 | Example                         |
|--------------|--------|---------------------------------------------|---------------------------------|
| `subject`    | String | The subject or system to analyze over time. | `"User authentication service"` |
| `time_range` | String | The time period to analyze.                 | `"2023-01-01 to 2024-01-01"`    |

#### Optional Parameters

| Parameter                | Type         | Default      | Description                                                                                    | Example                              |
|--------------------------|--------------|--------------|------------------------------------------------------------------------------------------------|--------------------------------------|
| `granularity`            | String       | `"weekly"`   | The time interval for analysis. Can be `daily`, `weekly`, `monthly`, `quarterly`, or `yearly`. | `"monthly"`                          |
| `identify_patterns`      | Boolean      | `true`       | If true, the task will attempt to identify recurring patterns and cycles.                      | `false`                              |
| `predict_future`         | Boolean      | `true`       | If true, the task will predict future states based on historical trends.                       | `false`                              |
| `prediction_horizon`     | String       | `"3 months"` | The timeframe for future predictions.                                                          | `"6 weeks"`                          |
| `critical_events`        | List<String> | `null`       | A list of specific, known events to highlight in the timeline.                                 | `["v2.0 release", "Major outage"]`   |
| `related_files`          | List<String> | `null`       | Glob patterns for files containing temporal data (e.g., logs, metrics, CSVs).                  | `["logs/**/*.log", "metrics/*.csv"]` |
| `analyze_rate_of_change` | Boolean      | `true`       | If true, the task will analyze the velocity and acceleration of change.                        | `false`                              |
| `identify_transitions`   | Boolean      | `true`       | If true, the task will identify critical inflection points or phase shifts.                    | `false`                              |

### How It Works

#### Process Flow

1. **Data Gathering:** The task begins by collecting temporal data from files specified in the `related_files` parameter, using glob patterns to find relevant
   sources like logs or metrics.
2. **Timeline Construction:** An AI agent analyzes the gathered data to construct a chronological timeline of significant events, categorizing them and
   assessing their importance.
3. **Detailed Analysis (Optional):** Based on the configuration, the agent performs deeper analysis:
  - **Pattern Identification:** Looks for recurring cycles and trends.
  - **Rate of Change Analysis:** Calculates the velocity and acceleration of changes.
  - **Transition Point Identification:** Finds critical inflection points where system behavior shifted.
  - **Future Prediction:** Extrapolates from historical data to forecast future states.
4. **Visualization:** A separate AI call generates a Mermaid.js timeline diagram to provide a clear visual representation of the events.
5. **Reporting:** The results are organized into a tabbed display, and a final summary is generated.

#### Internal Mechanics

The task leverages a `ParsedAgent` to instruct an LLM to perform the analysis and return a structured JSON object (`TimelineAnalysis`). This structured data
contains the timeline events, patterns, predictions, and other analytical components. A subsequent call to a `ChatAgent` uses this structured data to generate a
concise Mermaid diagram for visualization.

#### Output Structure

The output is presented in a multi-tabbed user interface for easy navigation:

- **Overview:** A high-level summary of the analysis, including the subject, time range, and key findings.
- **Temporal Data:** A list of the data sources that were processed.
- **Timeline:** A detailed, chronological list of all identified events with descriptions and significance.
- **Patterns:** (If enabled) A breakdown of identified temporal patterns, their frequency, and confidence levels.
- **Rate of Change:** (If enabled) A textual analysis of the system's rate of change over time.
- **Transition Points:** (If enabled) A list and description of critical transitions.
- **Future Predictions:** (If enabled) A set of predictions about the system's future state.
- **Visualization:** A rendered Mermaid timeline diagram for a quick visual overview.