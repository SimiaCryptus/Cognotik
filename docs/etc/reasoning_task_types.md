# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\AbductiveReasoningTask.kt

## Abductive Reasoning

#### **Overview**

* **One-Line Description:** Generate and evaluate explanatory hypotheses for a set of observations.
* **Detailed Description:** This task performs abductive reasoning, also known as "inference to the best explanation."
  Given a list of observations, it generates multiple potential hypotheses that could explain them. Each hypothesis is
  then rigorously evaluated against criteria such as explanatory power, simplicity (Occam's Razor), testability, and
  prior probability. The task ranks the hypotheses, identifies the most plausible explanation, and can even suggest
  concrete validation tests.
* **Key Use Cases:**
    * **Root Cause Analysis:** Determine the underlying cause of a system failure or unexpected behavior.
    * **Bug Investigation:** Formulate and evaluate potential reasons for a software bug based on reported symptoms.
    * **Understanding Anomalies:** Explain unusual patterns or outliers in data.
    * **Scientific Reasoning:** Develop and assess competing theories based on experimental evidence.
    * **Strategic Planning:** Evaluate potential explanations for market shifts or competitor actions.

#### **Configuration Parameters**

| Parameter             | Type           | Description                                                                                                                            | Default Value                                                             |
|-----------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `observations`        | `List<String>` | A list of observations, facts, or symptoms that require an explanation.                                                                | **Required**                                                              |
| `generate_hypotheses` | `Boolean`      | If `true`, the task will generate new hypotheses. If `false`, it will only evaluate `existing_hypotheses`.                             | `true`                                                                    |
| `max_hypotheses`      | `Int`          | The maximum number of distinct hypotheses to generate and evaluate.                                                                    | `5`                                                                       |
| `evaluate_criteria`   | `List<String>` | The criteria used to score each hypothesis. Options include `explanatory_power`, `simplicity`, `testability`, and `prior_probability`. | `["explanatory_power", "simplicity", "testability", "prior_probability"]` |
| `suggest_tests`       | `Boolean`      | If `true`, the task will generate a set of suggested experiments or tests to validate the top hypotheses.                              | `true`                                                                    |
| `existing_hypotheses` | `List<String>` | An optional list of pre-defined hypotheses to evaluate. Used when `generate_hypotheses` is `false`.                                    | `null`                                                                    |
| `domain_context`      | `String`       | Optional background information or constraints about the domain (e.g., "This is a microservice architecture").                         | `null`                                                                    |

#### **Process Flow**

1. **Initialization:** The task begins by validating its configuration, ensuring that a list of `observations` has been
   provided.
2. **Hypothesis Generation/Evaluation:**
    * If `generate_hypotheses` is `true`, the task uses an LLM to generate up to `max_hypotheses` distinct explanations
      for the provided observations, taking the domain context into account.
    * If `generate_hypotheses` is `false`, the task evaluates the user-provided `existing_hypotheses` against the
      observations.
3. **Scoring:** Each hypothesis is scored from 0.0 to 1.0 based on the specified `evaluate_criteria`. An `overall_score`
   is calculated to rank them.
4. **Comparative Analysis:** The task performs a comparative analysis across all hypotheses. It identifies the strongest
   explanation, discusses the trade-offs between different options, and highlights any observations that remain poorly
   explained.
5. **Validation Test Generation (Optional):** If `suggest_tests` is `true`, the task generates concrete, actionable
   tests for the top-ranked hypotheses. This includes confirmatory tests (to prove), falsification tests (to disprove),
   and discriminating tests (to distinguish between similar hypotheses).
6. **Summary:** The task identifies the single best explanation and compiles a final summary of the entire analysis.

#### **Output Structure**

* **Final Result:** The task's output is a concise Markdown summary of the analysis. It includes the number of
  hypotheses generated, identifies the best explanation and its score, and presents the key findings from the
  comparative analysis. This summary is designed to be passed as context to subsequent tasks in a plan.

* **UI Breakdown:** The user interface provides a detailed, tabbed view of the entire process:
    * **Overview:** A real-time log of the task's progress, showing timings for each major step and the final outcome.
    * **Observations:** A list of the initial observations that the task was asked to explain.
    * **Context:** (If applicable) Displays relevant context inherited from previous tasks in the execution plan.
    * **Hypotheses:** A detailed breakdown of each generated hypothesis, including its description, a full explanation,
      its scores for each criterion, and any identified supporting or contradicting evidence.
    * **Analysis:** The complete text of the comparative analysis, offering a deep dive into the relative strengths and
      weaknesses of each hypothesis.
    * **Validation Tests:** (If enabled) A list of suggested experiments, including implementation steps and expected
      results, to validate the top hypotheses.
    * **Best Explanation:** A focused view of the single highest-scoring hypothesis, summarizing why it is the most
      likely explanation and outlining recommended next steps.

#### **Example Usage**

* **Scenario:** A software team is investigating an intermittent bug. Users report that their profile data is
  occasionally corrupted, but only on Tuesdays and only for users who signed up in the last month. The application logs
  show no errors.

* **Configuration:**
  ```kotlin
  AbductiveReasoningTask.AbductiveReasoningTaskExecutionConfigData(
      observations = listOf(
          "Data corruption occurs in user profiles.",
          "The issue is intermittent and happens only on Tuesdays.",
          "It only affects users who signed up in the last month.",
          "Application logs show no corresponding errors."
      ),
      max_hypotheses = 3,
      domain_context = "A multi-tenant SaaS application with a weekly data processing batch job.",
      suggest_tests = true
  )
  ```

* **Expected Output Snippet:**
  ```markdown
  # Abductive Reasoning Summary

  **Observations Analyzed:** 4
  **Hypotheses Generated:** 3
  **Best Explanation:** A weekly batch job, scheduled for Tuesdays, has a logic error that incorrectly processes new user records, causing data corruption.
  **Best Score:** 0.92

  ## Key Findings

  The leading hypothesis provides the most parsimonious explanation for all observations, particularly the "Tuesday" constraint, which strongly points to a scheduled task. While a database race condition was considered, it fails to explain the weekly recurrence. The recommended first step is to audit the code for the weekly user data processing job, specifically looking for date-based logic and edge cases related to new user accounts created within the last 30 days.
  ```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\AbstractionLadderTask.kt

## Abstraction Ladder

#### Overview

**One-Line Description:** Traverse abstraction levels to identify patterns and design insights.

**Detailed Description:** The Abstraction Ladder task provides a structured method for analyzing a concept, problem, or
code pattern by moving both up and down a hierarchy of abstraction. By moving "up" the ladder, the task identifies
generalizations, architectural principles, and high-level design patterns. By moving "down," it explores specific
implementations, concrete use cases, and potential pitfalls. This bidirectional analysis helps uncover deep design
insights, refactoring opportunities, and a clearer understanding of how a specific component fits into a larger system.

**Key Use Cases:**

* **Architectural Analysis:** Understand how a concrete class or module fits into broader architectural patterns.
* **Refactoring:** Identify opportunities to generalize code, apply design patterns, or improve implementations.
* **Design Exploration:** Explore alternative, more specific implementations for an abstract concept.
* **Code Review:** Discover code smells, anti-patterns, and potential improvements by examining a component at multiple
  levels of abstraction.
* **Learning & Onboarding:** Help developers understand a complex piece of code by breaking it down into its abstract
  principles and concrete examples.

#### Configuration Parameters

The task's behavior is controlled by the following parameters:

| Parameter           | Type            | Description                                                                                                                                             | Default Value |
|---------------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|
| `concrete_concept`  | `String?`       | The concrete concept, problem, or code pattern to analyze.                                                                                              | **Required**  |
| `direction`         | `String`        | Direction to traverse: 'up' for abstraction (generalizations), 'down' for concretization (specific implementations), 'both' for bidirectional analysis. | `"both"`      |
| `levels`            | `Int`           | Number of abstraction levels to traverse in each direction (1-5 recommended).                                                                           | `3`           |
| `identify_patterns` | `Boolean`       | Whether to identify design patterns, anti-patterns, and refactoring opportunities at each level.                                                        | `true`        |
| `related_files`     | `List<String>?` | Additional files for context (e.g., existing code, related implementations).                                                                            | `null`        |
| `task_description`  | `String?`       | A user-defined description of the task's purpose.                                                                                                       | `null`        |
| `task_dependencies` | `List<String>?` | A list of task IDs that must be completed before this task can start.                                                                                   | `null`        |
| `state`             | `TaskState?`    | The initial state of the task.                                                                                                                          | `Pending`     |

#### Process Flow

1. **Initialization:** The task begins by validating the configuration, ensuring a `concrete_concept` is provided and
   the `direction` is valid ('up', 'down', or 'both'). It sets up a tabbed display in the UI for organized output.
2. **Context Gathering:** It reads the content of any files specified in `related_files` and incorporates results from
   previous tasks to build a comprehensive context for the analysis.
3. **Upward Analysis (Optional):** If the `direction` is 'up' or 'both', the task performs a multi-level analysis moving
   from the concrete concept to more general abstractions. For each level, it identifies the generalized concept,
   explains the abstraction, and notes relevant design patterns or refactoring opportunities.
4. **Downward Analysis (Optional):** If the `direction` is 'down' or 'both', the task performs a multi-level analysis
   moving from the initial concept to more specific, concrete implementations. For each level, it identifies
   specializations, provides code examples, and notes implementation patterns or anti-patterns to avoid.
5. **Pattern Synthesis (Optional):** If `identify_patterns` is enabled, the task synthesizes the findings from both the
   upward and downward analyses into a final summary. This report includes identified design patterns, architectural
   insights, refactoring recommendations, and anti-patterns.
6. **Finalization:** The task concludes by updating the "Overview" tab with a summary of the analysis and passing the
   complete, structured Markdown report as its final result.

#### Output Structure

**Final Result:**
The final output is a single, comprehensive Markdown string that concatenates the results of the upward analysis,
downward analysis, and the pattern summary. This structured text is designed to be consumed by subsequent tasks or saved
as a design document.

**UI Breakdown:**
The task's progress and detailed results are presented in a series of tabs in the user interface:

* **Overview:** Displays the initial configuration and a final summary upon completion, indicating which analyses were
  performed.
* **Upward Analysis:** Contains the detailed, level-by-level report of generalizations, moving from the concrete concept
  to abstract principles.
* **Downward Analysis:** Contains the detailed, level-by-level report of concretizations, showing specific
  implementations and examples derived from the initial concept.
* **Pattern Analysis:** If enabled, this tab presents a synthesized report summarizing all identified design patterns,
  anti-patterns, architectural insights, and actionable refactoring recommendations.

#### Example Usage

**Scenario:**
A developer wants to analyze a `FileLogger` class to understand its place in the system's architecture and explore
potential improvements. They want to see both the abstract concepts it implements and alternative, more specialized
logging mechanisms.

**Configuration:**

```json
{
  "concrete_concept": "A FileLogger class that writes log messages to a local file, with methods for info, warn, and error levels. It handles file rotation based on size.",
  "direction": "both",
  "levels": 2,
  "identify_patterns": true,
  "related_files": ["src/main/java/com/mycorp/logging/FileLogger.java"]
}
```

**Expected Output Snippet:**

```markdown

### Upward Abstraction (Generalizations)


#### Level 0 (Concrete): FileLogger
- Description: Writes log messages to a local file with size-based rotation.
- Characteristics: File I/O, synchronous writes, specific log levels.


#### Level 1: Local Appender
- Generalization: The concept of a destination for log messages is abstracted from a specific file to any local destination.
- Examples: ConsoleAppender, DatabaseAppender (local DB).
- Patterns: Strategy Pattern (for different appending strategies).
- Refactoring: Extract a common `Appender` interface.


### Downward Concretization (Specific Implementations)


#### Level 0 (Starting): FileLogger
- Description: Writes log messages to a local file with size-based rotation.
- Characteristics: File I/O, synchronous writes, specific log levels.


#### Level -1: Asynchronous RollingFileLogger
- Specialization: Introduces asynchronous writing to improve application performance.
- Examples: A logger that uses a background thread and a queue to write log entries in batches.
- Code: `// Example using a BlockingQueue and a dedicated writer thread...`
- Patterns: Producer-Consumer Pattern.
- Anti-patterns: Avoid losing log messages on application crash if the queue is not persisted.
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\AdversarialReasoningTask.kt

## Adversarial Reasoning

#### **Overview**

* **One-Line Description:** Red team analysis to identify vulnerabilities and weaknesses.
* **Detailed Description:** This task performs adversarial reasoning and red team analysis on systems, designs, or
  arguments. It simulates an attacker's mindset to proactively discover security vulnerabilities, logical flaws, edge
  cases, and potential failure modes. By challenging assumptions and exploring various attack vectors at different
  capability levels, it stress-tests systems and arguments to uncover hidden weaknesses before they can be exploited.
  The task generates structured vulnerability reports, complete with severity ratings, and can optionally suggest
  mitigation strategies.
* **Key Use Cases:**
    * **Security Audits:** Identifying security vulnerabilities and potential attack vectors in software or
      infrastructure.
    * **System Design Review:** Stress-testing a system design to find logical flaws, edge cases, and failure modes.
    * **Argument Deconstruction:** Analyzing a business plan, proposal, or argument to find weaknesses and
      counter-arguments.
    * **Threat Modeling:** Simulating adversarial scenarios to understand potential risks.
    * **Compliance & Privacy Analysis:** Evaluating a system for potential privacy violations or non-compliance with
      regulations.

#### **Configuration Parameters**

| Parameter                        | Type            | Description                                                                                         | Default Value                      |
|----------------------------------|-----------------|-----------------------------------------------------------------------------------------------------|------------------------------------|
| `target_system`                  | `String?`       | The target system, design, or argument to analyze for weaknesses.                                   | **Required**                       |
| `attack_vectors`                 | `List<String>?` | Attack vectors to explore: 'security', 'performance', 'logic', 'business', 'privacy', 'compliance'. | `["security", "logic"]`            |
| `adversary_capability`           | `String`        | Adversary capability level: 'basic', 'intermediate', 'advanced', 'nation-state'.                    | `"intermediate"`                   |
| `generate_exploits`              | `Boolean`       | Whether to generate detailed exploit scenarios.                                                     | `false`                            |
| `suggest_mitigations`            | `Boolean`       | Whether to suggest mitigation strategies.                                                           | `true`                             |
| `related_files`                  | `List<String>?` | Related files or code to analyze (glob patterns).                                                   | `null`                             |
| `challenge_assumptions`          | `List<String>?` | Specific assumptions to challenge.                                                                  | `null`                             |
| `max_vulnerabilities_per_vector` | `Int`           | Maximum number of vulnerabilities to identify per vector.                                           | `5`                                |
| `task_description`               | `String?`       | A custom description for the task.                                                                  | Auto-generated from configuration. |
| `task_dependencies`              | `List<String>?` | A list of task IDs that must be completed before this task can start.                               | `null`                             |

#### **Process Flow**

1. **Initialization & Context Gathering:** The task begins by setting up the UI and gathering all relevant context. This
   includes results from previous tasks and the content of any files specified in the `related_files` parameter.
2. **Iterative Vector Analysis:** The task iterates through each specified `attack_vector`. For each vector:
    * A specialized "adversarial agent" is created with a persona matching the configured `adversary_capability` (e.g.,
      a basic hacker vs. a nation-state actor).
    * A detailed analysis prompt is constructed, instructing the agent to focus on the current vector, challenge
      specific assumptions, and identify vulnerabilities.
    * The agent performs the analysis, and the results are parsed to extract structured vulnerability data, edge cases,
      and failure modes.
3. **Mitigation Generation (Optional):** If `suggest_mitigations` is enabled and vulnerabilities were found, a
   separate "security architect" agent is tasked with analyzing the findings and proposing practical, actionable
   mitigation strategies.
4. **Executive Summary Generation:** A final, high-level summary is created. It consolidates all findings, provides an
   overall risk assessment, highlights the most critical concerns, and offers strategic recommendations.
5. **Completion:** The task concludes, providing a detailed, tabbed report in the UI and a concise summary of the key
   findings for use in subsequent tasks.

#### **Output Structure**

* **Final Result:** The final output is a concise Markdown-formatted string summarizing the entire analysis. It
  includes:
    * The total number of vulnerabilities found, broken down by severity.
    * A list of the top 3-5 most critical vulnerabilities with brief descriptions.
    * Key statistics, such as the number of edge cases and failure modes identified.
    * The overall risk level assessment.

* **UI Breakdown:** The task provides a rich, multi-tabbed interface for detailed exploration of the results:
    * **Overview:** Displays the initial configuration, real-time progress updates, and a final summary of the analysis
      upon completion.
    * **Context:** Shows the contextual data (from prior tasks or files) that was used as the basis for the analysis.
    * **Vector: \[Vector Name\]:** A dedicated tab is created for each analyzed attack vector (e.g., "Vector:
      Security"). This tab contains the raw, detailed output from the adversarial agent for that specific vector.
    * **Mitigations:** If enabled, this tab contains the detailed defensive recommendations and strategies proposed by
      the security architect agent.
    * **Executive Summary:** Presents the final, polished summary report, including risk assessment tables, top
      concerns, and strategic recommendations.

#### **Example Usage**

* **Scenario:** A development team has just finished building a new user authentication service that uses JWTs. They
  want to perform a security and logic audit before deploying it to production.

* **Configuration:**
  ```json
  {
    "target_system": "A new JWT-based user authentication service. It handles user registration, login, token issuance, and token validation. It uses a symmetric HS256 signing key stored in an environment variable.",
    "attack_vectors": ["security", "logic", "privacy"],
    "adversary_capability": "advanced",
    "generate_exploits": false,
    "suggest_mitigations": true,
    "challenge_assumptions": [
      "The JWT signing key will always remain secret.",
      "Timestamps in the JWT payload cannot be manipulated."
    ]
  }
  ```

* **Expected Output Snippet:**
  ```markdown
  # Adversarial Analysis: A new JWT-based user authentication service...

  **Adversary Capability:** advanced

  ## Key Findings

  - **Total Vulnerabilities:** 8
  - **Critical/High Severity:** 3
  - **Attack Vectors:** security, logic, privacy

  ## Top Vulnerabilities

  ### CRITICAL: Insecure Token Validation
  The system does not properly validate the 'alg' (algorithm) header in received JWTs. An attacker could forge a token by changing the algorithm to 'none' and submitting it, bypassing signature verification entirely to gain unauthorized access.

  ### HIGH: Lack of Token Revocation Mechanism
  There is no system in place to revoke issued JWTs. If a user's token is compromised or they are logged out, the token remains valid until its natural expiration, allowing an attacker to reuse it.
  ```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\AnalogicalReasoningTask.kt

## Analogical Reasoning

#### **Overview**

* **One-Line Description:** Solve problems by finding and applying analogies from different domains.
* **Detailed Description:** This task performs creative problem-solving by drawing analogies from a specified source
  domain (e.g., biology, architecture) and applying them to a target problem. It maps structural relationships,
  generates multiple perspectives, and synthesizes findings to suggest novel solutions. The process can optionally
  include a validation step to ensure the coherence and consistency of the analogical mappings.
* **Key Use Cases:**
    * Breaking through creative blocks in design and engineering.
    * Developing novel strategies or business models.
    * Simplifying complex problems by reframing them in a more familiar context.
    * Enhancing design thinking and innovation workshops.

#### **Configuration Parameters**

| Parameter           | Type           | Description                                                                                                                                | Default Value |
|:--------------------|:---------------|:-------------------------------------------------------------------------------------------------------------------------------------------|:--------------|
| `source_domain`     | `String`       | The source domain to draw analogies from (e.g., 'biological systems', 'urban planning', 'musical composition').                            | **Required**  |
| `target_problem`    | `String`       | The target problem to solve using analogies.                                                                                               | **Required**  |
| `num_analogies`     | `Int`          | The number of distinct analogies to generate and explore.                                                                                  | `3`           |
| `validate_mappings` | `Boolean`      | If true, the task will perform an additional step to validate the structural consistency and logical coherence of the generated analogies. | `true`        |
| `related_files`     | `List<String>` | A list of file paths to provide additional context for the reasoning process.                                                              | `null`        |
| `task_description`  | `String`       | A user-defined description of the task's purpose.                                                                                          | `null`        |
| `task_dependencies` | `List<String>` | A list of other task IDs that must be completed before this one can run.                                                                   | `null`        |

#### **Process Flow**

1. **Context Gathering:** The task begins by collecting context from the overall execution plan and loading the content
   of any files specified in the `related_files` parameter.
2. **Analogy Generation:** It prompts an AI agent to generate the specified number of analogies. For each analogy, the
   agent identifies a relevant concept from the source domain, maps its structure to the target problem, and derives
   potential insights and solutions.
3. **Mapping Validation (Optional):** If `validate_mappings` is enabled, a separate AI agent reviews the generated
   analogies. It assesses them against criteria like structural parallelism, mapping consistency, and the absence of
   logical fallacies, providing a validation assessment.
4. **Synthesis and Reporting:** The task synthesizes the insights gathered from all analogies into a cohesive summary.
   It formulates a final recommended approach and formats the detailed results for display in the user interface.

#### **Output Structure**

* **Final Result:** The task outputs a comprehensive Markdown-formatted string that is passed to subsequent tasks. This
  report includes a summary of the source domain and target problem, a list of the generated analogies with their
  applications and insights, a synthesis of key findings, and the final recommended approach.
* **UI Breakdown:** The user interface provides a detailed, tabbed view of the process:
    * **Overview:** Shows the initial configuration, live progress updates, and a final summary table with key metrics
      like the number of analogies generated, average confidence score, and total execution time.
    * **Analogy Generation:** Displays the AI's process of generating analogies, followed by a formatted list of the
      generated analogies, including their titles, confidence scores, and key insights.
    * **Validation:** (Only appears if `validate_mappings` is true) Details the validation criteria and presents the
      AI's assessment of the analogies' structural and logical coherence.
    * **Synthesis & Recommendations:** Contains the final, detailed report. This includes a cross-analogy synthesis, the
      final recommended approach, and a complete breakdown of each analogy with its conceptual mappings, structural
      similarities, limitations, and suggested solutions.

#### **Example Usage**

* **Scenario:** A software development team is designing a new system for managing complex, interdependent data
  workflows. They want to ensure the system is robust, adaptable, and easy to understand. They decide to use an analogy
  from biology to inspire their design.
* **Configuration:**
  ```json
  {
    "source_domain": "Biological ecosystems and cellular processes",
    "target_problem": "Design a robust and adaptable system for managing complex data workflows.",
    "num_analogies": 2,
    "validate_mappings": true
  }
  ```
* **Expected Output Snippet:**
  The final result might include an analogy like this:

  **Analogy: DNA Transcription and Protein Synthesis**

    * **Source Concept:** In a cell, DNA holds the master blueprint. RNA polymerase transcribes a specific gene (a
      workflow template) into messenger RNA (a workflow instance). Ribosomes then read the mRNA to assemble proteins (
      the final data product).
    * **Application:** Our system can use a "master template" (like DNA) to define workflow structures. When a new
      workflow is needed, the system "transcribes" it into an active instance (mRNA) with specific parameters. "Worker
      modules" (like ribosomes) then execute the steps to produce the final output.
    * **Key Insight:** This analogy suggests a clear separation between the workflow definition (template) and its
      execution (instance), preventing corruption of the master logic and allowing for many parallel, independent
      executions.
    * **Recommended Approach:** Implement a workflow engine where workflows are defined as immutable templates. Create
      a "Workflow Manager" service that instantiates these templates with runtime parameters and dispatches them to a
      pool of stateless "Execution Agents".

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\BrainstormingTask.kt

## Brainstorming

#### Overview

**One-Line Description:** Generate and analyze multiple solution options for a given problem.

**Detailed Description:** The Brainstorming task systematically generates a diverse set of potential solutions or ideas
for a specified problem. It then analyzes each option independently, evaluating its pros, cons, feasibility, potential
impact, and associated risks. Finally, it synthesizes these individual analyses into a comparative summary with
actionable recommendations. This structured approach helps move from a broad problem statement to a well-vetted set of
potential paths forward, supporting both creative and conventional approaches.

**Key Use Cases:**

* **Solution Exploration:** Exploring a wide range of solutions for a complex problem.
* **Decision Making:** Making informed decisions by comparing multiple well-analyzed options.
* **Strategic Planning:** Ideating new features, strategies, or project directions.
* **Problem Solving:** Breaking down a complex issue into manageable, actionable solution paths.
* **Identifying Synergies:** Discovering hybrid approaches by combining the best aspects of different ideas.

---

#### Configuration Parameters

The task's behavior is controlled by the following parameters in its `ExecutionConfigData` class.

| Parameter                  | Type           | Description                                                                                      | Default Value |
|----------------------------|----------------|--------------------------------------------------------------------------------------------------|---------------|
| `problem_statement`        | `String`       | The problem or question to brainstorm solutions for.                                             | **Required**  |
| `target_option_count`      | `Int`          | The target number of distinct options to generate.                                               | `7`           |
| `categories`               | `List<String>` | Optional list of categories or domains to guide the brainstorming process.                       | `null`        |
| `constraints`              | `List<String>` | Optional list of constraints or requirements that solutions must adhere to.                      | `null`        |
| `include_creative_options` | `Boolean`      | If true, the task will be encouraged to generate unconventional or "out-of-the-box" ideas.       | `true`        |
| `analysis_depth`           | `String`       | The level of detail for the analysis of each option. Accepts `brief`, `moderate`, or `detailed`. | `moderate`    |

---

#### Process Flow

The task executes in a clear, three-step sequence to ensure a thorough and organized outcome:

1. **Option Generation:** The task begins by generating a set of distinct solution options based on the user-provided
   `problem_statement`, `categories`, and `constraints`. It uses a specialized agent to ensure the output is structured,
   diverse, and adheres to the target count.
2. **Independent Analysis:** Each generated option is then analyzed individually. For every option, the task evaluates
   its pros, cons, feasibility, potential impact, risks, and requirements. This isolated analysis prevents bias and
   ensures each idea is judged on its own merits.
3. **Synthesis and Summary:** After all options have been analyzed, the task generates a final comparative summary. This
   report synthesizes the findings, highlights the most promising options, discusses potential hybrid approaches, and
   provides actionable recommendations for next steps.

---

#### Output Structure

The task produces a detailed output in the UI and a concise summary for subsequent tasks.

**Final Result:**
The final output passed to other tasks is a single, concise Markdown-formatted string. It includes the original problem
statement, a list of the generated option titles and their descriptions, and the key findings from the comparative
summary. This output is intentionally truncated to a manageable length to serve as an effective input for other AI
tasks.

**UI Breakdown:**
The user interface provides a detailed, multi-tab view of the entire process, allowing for in-depth review:

* **Overview:** Displays the initial configuration, tracks the real-time progress of the task (generation, analysis,
  completion), and shows final summary statistics like total time taken.
* **Context:** If the task is part of a larger plan, this tab shows relevant information and code gathered from previous
  tasks that informed the brainstorming.
* **Generated Options:** Lists all the brainstormed options with their titles and full descriptions as soon as they are
  generated.
* **Option [N] Analysis:** A separate tab is created for each option, containing its detailed analysis, including pros,
  cons, feasibility, impact, risks, and requirements.
* **Summary & Recommendations:** Presents the full, unabridged comparative analysis, top recommendations, and suggested
  next steps.

---

#### Example Usage

**Scenario:**
A software development team needs to decide how to improve the performance of their application's main dashboard, which
has become slow. They want to explore several technical approaches before committing to one.

**Configuration:**

```json
{
  "problem_statement": "The main user dashboard is experiencing significant performance degradation, with load times exceeding 5 seconds. Brainstorm technical solutions to reduce the dashboard load time to under 2 seconds.",
  "target_option_count": 5,
  "categories": ["Frontend Optimization", "Backend Caching", "Database Tuning", "Architectural Changes"],
  "constraints": [
    "The solution must not require a complete rewrite of the frontend framework.",
    "Any new infrastructure must be compatible with our existing AWS environment."
  ],
  "analysis_depth": "moderate"
}
```

**Expected Output Snippet:**

```markdown

## Brainstorming Results: The main user dashboard is experiencing significant performance degradation...


### Options Generated: 5


#### 1. Implement Frontend Pagination and Virtual Scrolling
Redesign the dashboard to load only the visible data, fetching more as the user scrolls...


#### 2. Introduce a Redis Caching Layer for Dashboard Widgets
Cache the results of expensive backend queries in an in-memory Redis store...
...


### Key Findings

The analysis recommends a two-pronged approach. The highest impact, lowest risk solution is to **Introduce a Redis Caching Layer (Option 2)**, which can be implemented quickly on the backend. For a long-term solution, **Refactor Data-Heavy Components to be Asynchronous (Option 4)** is recommended as a follow-up project to further improve user experience.
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\CausalInferenceTask.kt

## Causal Inference

#### **Overview**

* **One-Line Description:** Identify causal relationships and root causes.
* **Detailed Description:** This task performs a causal inference analysis to uncover the true causal relationships
  behind an observed phenomenon. It goes beyond simple correlation by applying causal reasoning principles to evidence
  from various sources. The task can identify root causes, intermediate factors, and potential confounding variables
  that might otherwise lead to incorrect conclusions. It is designed to provide a structured, evidence-based explanation
  for complex events, making it invaluable for debugging, root cause analysis, and understanding system dynamics.
* **Key Use Cases:**
    * Root cause analysis of production incidents.
    * Debugging complex software issues where the cause is not obvious.
    * Understanding emergent system behavior.
    * Distinguishing correlation from causation in data analysis.

#### **Configuration Parameters**

This table details the configurable parameters for the Causal Inference task.

| Parameter              | Type            | Description                                                                                                    | Default Value |
|------------------------|-----------------|----------------------------------------------------------------------------------------------------------------|---------------|
| `observed_effect`      | `String?`       | The observed effect or outcome to explain.                                                                     | **Required**  |
| `potential_causes`     | `List<String>?` | A list of potential causes to investigate.                                                                     | `null`        |
| `build_causal_graph`   | `Boolean`       | If true, the task will attempt to generate a Mermaid diagram visualizing the causal relationships.             | `true`        |
| `identify_confounders` | `Boolean`       | If true, the task will explicitly try to identify confounding factors that could create spurious correlations. | `true`        |
| `evidence_sources`     | `List<String>?` | A list of file patterns (glob) or paths to use as evidence for the analysis.                                   | `null`        |
| `related_files`        | `List<String>?` | A list of additional files to provide context for the analysis.                                                | `null`        |
| `task_description`     | `String?`       | A user-defined description of the task.                                                                        | `null`        |
| `task_dependencies`    | `List<String>?` | A list of task IDs that must be completed before this task can run.                                            | `null`        |
| `state`                | `TaskState?`    | The initial state of the task.                                                                                 | `Pending`     |

#### **Process Flow**

1. **Initialization:** The task starts and validates that an `observed_effect` has been provided.
2. **Evidence Gathering:** It searches for and reads the content of files matching the patterns in `evidence_sources`
   and `related_files`. This content is compiled into a single evidence context.
3. **Prompt Construction:** A detailed prompt is constructed for the LLM, including the observed effect, the list of
   potential causes, the gathered evidence, and instructions for performing a rigorous causal analysis.
4. **Causal Analysis:** The prompt is sent to the LLM, which analyzes the information and returns a structured textual
   breakdown of the causal relationships.
5. **Graph Generation (Optional):** If `build_causal_graph` is enabled, a follow-up prompt is sent to the LLM, asking it
   to create a Mermaid diagram based on its analysis.
6. **Result Presentation:** The complete analysis, including the evidence, textual breakdown, and optional graph, is
   presented in a multi-tabbed view in the UI.

#### **Output Structure**

* **Final Result:** The primary output is a string containing the detailed causal analysis generated by the language
  model. This text includes a summary, a breakdown of each identified cause, root cause identification, and
  recommendations. This string is passed to subsequent tasks.
* **UI Breakdown:** The task provides a rich, interactive output in the UI, organized into the following tabs:
    * **Overview:** Displays the initial configuration (observed effect, potential causes) and the final status of the
      analysis.
    * **Evidence Sources:** Shows the content of all files that were read and used as evidence, allowing for easy
      verification.
    * **Causal Analysis:** Contains the full, formatted textual analysis from the LLM.
    * **Causal Graph:** If enabled, this tab displays the rendered Mermaid diagram, providing a visual map of the causal
      relationships.

#### **Example Usage**

* **Scenario:** A DevOps engineer is investigating a sudden 50% increase in API response times for a critical
  microservice. The cause is unknown, but they suspect recent code changes, a database performance issue, or a surge in
  traffic.
* **Configuration:**
  ```json
  {
    "observed_effect": "API response time for the 'user-service' has increased by 50% in the last 3 hours.",
    "potential_causes": [
      "Recent deployment of commit #a1b2c3d",
      "Increased load on the primary PostgreSQL database",
      "Unusual traffic patterns from a new client integration"
    ],
    "evidence_sources": [
      "logs/user-service-*.log",
      "metrics/database_cpu_*.csv",
      "src/main/kotlin/com/example/UserService.kt"
    ],
    "build_causal_graph": true,
    "identify_confounders": true
  }
  ```
* **Expected Output Snippet:**
  > **Summary:** The root cause of the increased API latency is a new, inefficient database query introduced in commit
  #a1b2c3d. While the traffic surge was a contributing factor (intermediate cause), it only exacerbated the performance
  issue in the new code; it was not the root cause.
  >
  > **Root Cause Identification:** The fundamental cause is the introduction of a non-indexed query in `UserService.kt`
  during the latest deployment.
  >
  > **Causal Chain:** `Commit #a1b2c3d` -> `Inefficient DB Query` -> `High DB CPU Usage` -> `Increased API Latency`.

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\ChainOfThoughtTask.kt

## ChainOfThought

#### Overview

* **One-Line Description:** Break down complex problems into explicit reasoning steps.
* **Detailed Description:** This task performs a step-by-step reasoning process with built-in validation. It is designed
  to break down complex problems into a series of logical, sequential steps. A key feature is its ability to validate
  each step for logical consistency before moving to the next, providing a high degree of transparency in the reasoning
  process. If a step fails validation, the task can backtrack and attempt to correct its reasoning. The final output is
  a comprehensive chain of thought that documents the entire analytical journey from the initial problem to the final
  conclusion.
* **Key Use Cases:**
    * Solving multi-step mathematical or logical puzzles.
    * Debugging complex code by tracing the logic step-by-step.
    * Planning a sequence of actions where each step depends on the previous one.
    * Analyzing complex scenarios by breaking them into manageable parts.
    * Providing transparent, auditable explanations for a decision.

#### Configuration Parameters

| Parameter           | Type           | Description                                                                                     | Default Value                   |
|:--------------------|:---------------|:------------------------------------------------------------------------------------------------|:--------------------------------|
| `problem_statement` | `String`       | The complex problem requiring step-by-step reasoning.                                           | `""` (Effectively **Required**) |
| `reasoning_depth`   | `Int`          | Number of reasoning steps to generate. The process may stop earlier if a conclusion is reached. | `10`                            |
| `validate_steps`    | `Boolean`      | Whether to validate each step for logical consistency before proceeding.                        | `true`                          |
| `related_files`     | `List<String>` | A list of file paths to provide additional context for the reasoning process.                   | `emptyList()`                   |
| `task_dependencies` | `List<String>` | A list of task IDs that must be completed before this task can start.                           | `null`                          |

#### Process Flow

1. **Initialization:** The task begins by validating the `problem_statement` and gathering context from any completed
   dependency tasks and the contents of the files specified in `related_files`.
2. **Iterative Reasoning Loop:** The task enters a loop that continues until the maximum `reasoning_depth` is reached or
   a logical conclusion is found.
3. **Step Generation:** In each iteration, an AI agent generates a single `ReasoningStep`. This includes the thought
   process, a clear conclusion for that step, a confidence score (from 0.0 to 1.0), and a question to guide the next
   step.
4. **Step Validation (Optional):** If `validate_steps` is enabled, a separate AI agent evaluates the generated step for
   logical consistency against the conclusions of previous steps. If validation fails, the task attempts to regenerate
   the step using the validation feedback as guidance.
5. **Continuation or Termination:** The task checks if it should stop. It terminates if the AI indicates the reasoning
   is complete (e.g., no next question is posed and confidence is high) or if the maximum number of steps is reached.
   Otherwise, it uses the `next_question` from the current step to start the next iteration.
6. **Final Summary:** Once the loop concludes, the task directs an AI agent to generate a comprehensive summary that
   synthesizes the entire reasoning chain into a final, coherent answer.

#### Output Structure

* **Final Result:** The final output passed to subsequent tasks is a detailed Markdown document. It contains a complete,
  step-by-step breakdown of the entire reasoning process, including the reasoning, conclusion, confidence, and next
  question for each step. This is followed by the comprehensive final summary that provides the ultimate answer to the
  problem.
* **UI Breakdown:** In the user interface, the task's execution is broken down into several tabs for clarity:
    * **Overview:** Displays the initial configuration, real-time progress updates (e.g., "Step 3 complete"), and final
      statistics upon completion (total time, steps completed, average step time, final confidence).
    * **Context:** If applicable, this tab shows the context gathered from previous tasks and the full content of any
      `related_files`.
    * **Step X:** A dedicated tab is created for each reasoning step, showing the question being addressed, the
      generated reasoning, the conclusion, the confidence score, and the validation status.
    * **Summary:** Contains the final, comprehensive summary of the entire reasoning process and the ultimate
      conclusion.

#### Example Usage

* **Scenario:** We need to solve a classic logic puzzle: "You have a 5-gallon jug and a 3-gallon jug, and an unlimited
  supply of water. How can you measure out exactly 4 gallons of water?"
* **Configuration:**
    * `problem_statement`: "You have a 5-gallon jug and a 3-gallon jug, and an unlimited supply of water. How can you
      measure out exactly 4 gallons of water?"
    * `reasoning_depth`: `8`
    * `validate_steps`: `true`
* **Expected Output Snippet:**
  ```markdown
  ## Final Summary

  The problem of measuring exactly 4 gallons of water using only 3-gallon and 5-gallon jugs is solved through a precise sequence of filling, pouring, and emptying actions. The key insight is to use the difference in volumes between the jugs to isolate specific quantities of water.

  The successful procedure is as follows:
  1. Fill the 5-gallon jug completely.
  2. Pour water from the 5-gallon jug into the 3-gallon jug until it is full. This leaves exactly 2 gallons in the 5-gallon jug.
  3. Empty the 3-gallon jug.
  4. Pour the 2 gallons from the 5-gallon jug into the now-empty 3-gallon jug.
  5. Refill the 5-gallon jug completely.
  6. Carefully pour water from the 5-gallon jug to top off the 3-gallon jug, which already contains 2 gallons and thus only needs 1 more gallon.

  After this final pour, exactly 4 gallons of water will remain in the 5-gallon jug, successfully solving the puzzle.
  ```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\ConstraintRelaxationTask.kt

## ConstraintRelaxation

### Overview

**One-Line Description:** Solve over-constrained problems through progressive constraint relaxation.

**Detailed Description:** This task solves complex problems by temporarily relaxing less critical constraints and then
progressively reintroducing them. It begins by identifying which constraints to initially relax based on their assigned
priority. It then solves a simplified version of the problem without these constraints to establish a baseline solution.
Following this, it systematically reintroduces the relaxed constraints one by one, adapting the solution at each step to
satisfy the newly added requirement while maintaining satisfaction for all previous ones. This iterative process allows
for the discovery of creative solutions and trade-offs, making it ideal for problems where a direct solution is
difficult due to conflicting requirements.

**Key Use Cases:**

* **System Architecture:** Designing complex systems (e.g., software, hardware) with conflicting goals like high
  performance, low cost, and small form factor.
* **Algorithm Design:** Developing algorithms that must balance competing factors such as speed, memory usage, and
  accuracy.
* **Product Design:** Creating a product specification that meets numerous, often contradictory, user requirements and
  business goals.
* **Logistics and Planning:** Finding workable solutions for scheduling or routing problems that are heavily
  constrained.

### Configuration Parameters

This table details the configurable parameters for the Constraint Relaxation task.

| Parameter                     | Type                  | Description                                                                                  | Default Value |
|-------------------------------|-----------------------|----------------------------------------------------------------------------------------------|---------------|
| `problem`                     | `String`              | The problem description to solve.                                                            | **Required**  |
| `constraints`                 | `Map<String, Double>` | A map of constraint descriptions to their priority weights (0.0-1.0, where 1.0 is critical). | **Required**  |
| `relaxation_strategy`         | `String`              | The strategy for relaxing constraints: 'progressive', 'selective', or 'hierarchical'.        | `progressive` |
| `reintroduction_order`        | `String`              | The order for reintroducing constraints: 'by_priority', 'by_difficulty', or 'by_dependency'. | `by_priority` |
| `find_creative_satisfactions` | `Boolean`             | If true, the AI will actively seek creative and unconventional ways to satisfy constraints.  | `true`        |
| `max_iterations`              | `Int`                 | The maximum number of relaxation and reintroduction iterations to perform.                   | `5`           |
| `related_files`               | `List<String>`        | A list of additional file paths to provide as context for the task.                          | `null`        |
| `task_dependencies`           | `List<String>`        | A list of task IDs that must be completed before this task can start.                        | `null`        |

### Process Flow

The task executes through a structured, multi-step process to find a viable solution:

1. **Constraint Analysis:** The task first analyzes the provided constraints. It orders them according to the
   `reintroduction_order` and then selects a subset to temporarily relax based on the chosen `relaxation_strategy`.
2. **Initial Relaxed Solution:** It generates an initial solution to the problem, considering only the active (
   non-relaxed) constraints. This provides a feasible starting point.
3. **Progressive Reintroduction:** The task enters a loop, reintroducing the relaxed constraints one at a time. In each
   iteration, it adapts the current solution to satisfy the new constraint while ensuring all previously satisfied
   constraints remain met.
4. **Adaptation and Refinement:** During each reintroduction step, the AI refines the solution, potentially discovering
   novel ways to balance competing requirements. This process continues until all constraints are reintroduced or the
   `max_iterations` limit is reached.
5. **Final Synthesis:** Once the iterative process is complete, the task generates a comprehensive final report. This
   report summarizes the final solution, analyzes how each constraint was satisfied, and discusses key insights,
   trade-offs, and creative elements discovered during the process.

### Output Structure

The output is delivered in two forms: a concise string for subsequent tasks and a detailed breakdown in the user
interface.

**Final Result:**
The final output is a Markdown-formatted string that includes:

* The initial problem statement.
* A summary of the initial solution when constraints were relaxed.
* A list of the reintroduction steps.
* The full text of the final synthesis, which provides a detailed overview of the final solution, constraint
  satisfaction analysis, key insights, and recommendations.

**UI Breakdown:**
The user interface provides a tabbed view for a detailed exploration of the task's execution:

* **Overview:** Displays the initial configuration, a list of all constraints with their priorities, and a real-time log
  of the task's progress.
* **Constraint Analysis:** Shows the ordered list of constraints and clearly marks which ones were initially relaxed.
* **Relaxed Solution:** Contains the first solution generated by the AI, which only addresses the high-priority,
  non-relaxed constraints.
* **Iteration `[X]`:** A separate tab is created for each reintroduction step, showing the adapted solution after
  incorporating a new constraint.
* **Final Synthesis:** Presents the final, comprehensive report detailing the solution, trade-offs, and overall
  analysis.

### Example Usage

**Scenario:**
A startup is designing a new consumer drone. The goal is to create a product that is affordable, lightweight, has a long
flight time, and includes a high-quality 4K camera. These requirements are conflicting and create an over-constrained
design problem.

**Configuration:**

```json
{
  "problem": "Design a consumer drone with the optimal balance of features and cost.",
  "constraints": {
    "Retail price under $400": 1.0,
    "Flight time of at least 30 minutes": 0.9,
    "Weight under 250g to avoid FAA registration": 0.8,
    "Includes a 4K camera": 0.7,
    "Has a range of at least 5km": 0.6,
    "Uses premium carbon fiber materials": 0.4
  },
  "relaxation_strategy": "progressive",
  "reintroduction_order": "by_priority",
  "find_creative_satisfactions": true
}
```

**Expected Output Snippet:**
The final synthesis might contain a section like this:

> **Key Insights:** The progressive relaxation process revealed that the constraint "Uses premium carbon fiber
> materials" was the most significant driver of cost. By relaxing this constraint initially, we were able to find a
> baseline design using a high-quality polymer composite that met the weight target. When the "4K camera" constraint was
> reintroduced, the solution adapted by slightly reducing battery size to offset the camera's weight, resulting in a
> flight time of 28 minutes. This trade-off was deemed acceptable to keep the price under the critical $400 threshold
> while still offering a key feature. The final recommendation is to proceed with the polymer composite frame and market
> the flight time as "up to 30 minutes."

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\ConstraintSatisfactionTask.kt

## Constraint Satisfaction

#### **Overview**

* **One-Line Description:** Solve problems with multiple competing constraints.
* **Detailed Description:** This task solves complex problems by balancing multiple, often competing, requirements. It
  systematically evaluates solutions against a set of 'hard' constraints (which must be met) and 'soft' constraints (
  which are desirable but can be traded off). By assigning weights to soft constraints and using various search
  strategies, the task can find an optimal or near-optimal solution that best satisfies all requirements. It provides
  detailed reasoning for its choices, making it ideal for complex decision-making processes.
* **Key Use Cases:**
    * Architectural decisions balancing performance, maintainability, and cost.
    * Resource allocation with competing priorities.
    * Configuration optimization with multiple objectives.
    * Design trade-off analysis.

#### **Configuration Parameters**

This table details every configurable parameter for the Constraint Satisfaction task.

| Parameter             | Type                   | Description                                                                                | Default Value       |
|-----------------------|------------------------|--------------------------------------------------------------------------------------------|---------------------|
| `problem_description` | `String?`              | The problem requiring constraint satisfaction.                                             | **Required**        |
| `hard_constraints`    | `List<String>?`        | Hard constraints that must be satisfied (cannot be violated).                              | `null` (empty list) |
| `soft_constraints`    | `Map<String, Double>?` | Soft constraints to optimize with their relative weights (0.0-1.0).                        | `null` (empty map)  |
| `search_strategy`     | `String`               | Search strategy: 'backtracking' (systematic), 'forward' (greedy), 'local' (hill-climbing). | `"backtracking"`    |
| `max_iterations`      | `Int`                  | Maximum search iterations before returning best solution found.                            | `100`               |
| `related_files`       | `List<String>?`        | Additional files for context.                                                              | `null`              |
| `task_dependencies`   | `List<String>?`        | A list of task IDs that must be completed before this task can start.                      | `null`              |
| `state`               | `TaskState?`           | The initial state of the task.                                                             | `TaskState.Pending` |

#### **Process Flow**

1. **Problem Overview:** The task begins by displaying a summary of the problem description, hard and soft constraints,
   and the chosen search strategy in the UI.
2. **Context Gathering:** It collects the results and code generated from any preceding tasks to provide relevant
   context for the problem.
3. **Solution Generation:** The task constructs a detailed prompt for the AI, instructing it to act as a constraint
   satisfaction expert. It then sends this prompt to the language model to generate a solution that adheres to the hard
   constraints while optimizing for the weighted soft constraints.
4. **Display Solution:** The complete, structured solution from the AI is presented in the UI, including the proposed
   solution, analysis of constraint satisfaction, and reasoning.
5. **Acceptance:** The task waits for the user to accept the solution. Once accepted, the generated text is passed on as
   the result for subsequent tasks.

#### **Output Structure**

* **Final Result:** The final output is a detailed string in Markdown format containing the AI's full analysis. This
  text follows a predefined structure, including sections for the solution overview, decision variables, constraint
  satisfaction analysis, overall score, reasoning, and alternative solutions.
* **UI Breakdown:** The user interface provides a step-by-step view of the process in the following tabs:
    * **Problem Overview:** Displays the initial problem statement and all configured constraints and parameters.
    * **Context:** Shows the status of gathering information from previous tasks.
    * **Solution Generation:** Indicates that the AI is currently processing the request.
    * **Final Solution:** Presents the complete, formatted solution generated by the AI for review and acceptance.

#### **Example Usage**

* **Scenario:** A development team needs to choose a backend framework for a new e-commerce platform. They have several
  competing requirements.
* **Configuration:**
  ```kotlin
  problem_description = "Select the best backend framework for a new e-commerce platform."
  hard_constraints = listOf(
      "Must be based on the JVM ecosystem.",
      "Must have strong support for RESTful APIs."
  )
  soft_constraints = mapOf(
      "High performance and low latency" to 1.0,
      "Rapid development and developer productivity" to 0.8,
      "Large community and availability of third-party libraries" to 0.7,
      "Low operational cost" to 0.5
  )
  search_strategy = "backtracking"
  ```
* **Expected Output Snippet:**
  ```markdown
  ### Solution Overview
  The recommended backend framework is Spring Boot with Kotlin. It fully satisfies all hard constraints and provides the best-balanced score across the desired soft constraints, excelling in community support and developer productivity.

  ### Hard Constraint Satisfaction
  - **JVM Ecosystem:** Verified. Spring Boot is a leading Java framework.
  - **RESTful API Support:** Verified. Spring Web MVC provides excellent support for creating REST APIs.

  ### Soft Constraint Optimization
  - **High performance (weight: 1.0):** Score: 0.8/1.0. While fast, frameworks like Vert.x might offer lower latency.
  - **Rapid development (weight: 0.8):** Score: 1.0/1.0. Spring Boot's convention-over-configuration approach is a major strength.
  ...
  ```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\CounterfactualAnalysisTask.kt

## Counterfactual Analysis

#### Overview

**One-Line Description:**
Explore "what-if" scenarios to understand causal relationships and decision impacts.

**Detailed Description:**
This task performs a counterfactual analysis to explore alternative scenarios and their potential outcomes. It
systematically analyzes an actual, historical event or decision and compares it against one or more hypothetical "
what-if" conditions (counterfactuals). The process helps identify key causal factors, supports controlled comparisons by
holding certain variables constant, and provides valuable insights for risk analysis, decision validation, and strategic
planning. It is particularly useful for retrospective analysis to understand why certain outcomes occurred and how
different choices might have led to different results.

**Key Use Cases:**

* **Risk Analysis:** Evaluating the potential impact of different risk mitigation strategies.
* **Decision Validation:** Assessing whether a past decision was optimal by comparing it to alternatives.
* **Retrospective Analysis:** Understanding the root causes of a project's success or failure.
* **Strategic Planning:** Exploring the potential outcomes of different strategic choices before committing resources.
* **Impact Assessment:** Determining the influence of a specific variable or event by imagining a scenario where it was
  absent or different.

---

#### Configuration Parameters

The task's behavior is controlled by the following parameters:

| Parameter          | Type           | Description                                                                                                        | Default Value |
|--------------------|----------------|--------------------------------------------------------------------------------------------------------------------|---------------|
| `actual_scenario`  | `String`       | The actual scenario or decision that occurred and is to be analyzed.                                               | **Required**  |
| `counterfactuals`  | `List<String>` | A list of alternative "what-if" scenarios to explore and compare against the actual one.                           | **Required**  |
| `compare_outcomes` | `Boolean`      | If true, the task will generate a final section comparing the outcomes of the actual and counterfactual scenarios. | `true`        |
| `control_factors`  | `List<String>` | A list of factors or variables to hold constant across all scenarios to ensure a controlled comparison.            | `null`        |
| `related_files`    | `List<String>` | Paths to additional files (e.g., historical data, related analyses) to provide context for the analysis.           | `null`        |
| `task_description` | `String`       | A detailed description of the specific analysis objectives for this task instance.                                 | `null`        |

---

#### Process Flow

The task executes in the following sequence:

1. **Validation:** The task first checks if the `actual_scenario` and `counterfactuals` list have been provided. If
   either is missing, it terminates with a configuration error.
2. **Context Gathering:** It reads the content of any files specified in `related_files` and incorporates results from
   previous dependent tasks to build a comprehensive context.
3. **Actual Scenario Analysis:** It performs a detailed analysis of the `actual_scenario`, identifying its key elements,
   actors, decisions, constraints, and potential outcomes.
4. **Counterfactual Scenarios Analysis:** It iterates through each hypothetical scenario in the `counterfactuals` list,
   performing the same detailed analysis for each one independently.
5. **Comparative Analysis:** If `compare_outcomes` is enabled, the task initiates a final step to compare the analysis
   of the actual scenario against all counterfactual scenarios. This step identifies key differences, assesses the
   impact of variable factors, and synthesizes lessons learned.
6. **Report Compilation:** All individual analyses and the final comparison are compiled into a single, structured
   Markdown report.

---

#### Output Structure

**Final Result:**
The final output is a single, comprehensive Markdown string that is passed to subsequent tasks. This report is
structured with clear headings for the actual scenario, each counterfactual scenario, and a concluding comparative
analysis (if enabled).

**UI Breakdown:**
In the user interface, the task's progress and results are displayed as follows:

* **Overview Tab:** An initial tab is created to show a summary of the configuration, including the actual scenario and
  the number of counterfactuals being analyzed, along with a status indicator.
* **Main Result View:** Upon completion, the main view is populated with the full, rendered Markdown report. This report
  includes:
    * A section detailing the analysis of the **Actual Scenario**.
    * A separate section for each **Counterfactual Scenario**, detailing its analysis.
    * A final **Comparative Analysis** section that synthesizes findings, compares outcomes, and provides strategic
      recommendations.

---

#### Example Usage

**Scenario:**
A software company decided to build a new feature using Technology Stack A. The project was completed over budget and
behind schedule. The project manager wants to analyze whether choosing Technology Stack B, which was the other finalist,
would have resulted in a better outcome.

**Configuration:**

```json
{
  "actual_scenario": "We developed the 'Analytics Dashboard' feature using React for the frontend and a Python/Flask monolith for the backend (Tech Stack A). The project took 6 months and cost $150,000.",
  "counterfactuals": [
    "What if we had developed the 'Analytics Dashboard' using a serverless architecture with AWS Lambda and a Vue.js frontend (Tech Stack B)?"
  ],
  "compare_outcomes": true,
  "control_factors": [
    "Team size: 4 developers",
    "Core feature requirements remain the same"
  ],
  "related_files": [
    "docs/project_postmortem_stack_A.txt"
  ]
}
```

**Expected Output Snippet:**

```markdown

## Counterfactual Analysis Results


### Actual Scenario
We developed the 'Analytics Dashboard' feature using React for the frontend and a Python/Flask monolith for the backend (Tech Stack A). The project took 6 months and cost $150,000.


#### Analysis
The monolithic architecture led to deployment complexities and tight coupling between components, increasing development time. While React is powerful, the team's limited experience with it contributed to schedule slips...


### Counterfactual Scenario 1
What if we had developed the 'Analytics Dashboard' using a serverless architecture with AWS Lambda and a Vue.js frontend (Tech Stack B)?


#### Analysis
A serverless approach would have eliminated server management overhead. The team's prior experience with Vue.js would likely have accelerated frontend development. The estimated timeline would be closer to 4 months with a potential cost reduction due to pay-per-use pricing...


### Comparative Analysis
The analysis suggests that Technology Stack B would have been the preferable choice. The primary drivers for this conclusion are the reduction in operational overhead from serverless architecture and better alignment with the team's existing skills (Vue.js). The estimated savings of 2 months and potentially lower costs highlight the significant impact of the initial technology choice...
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\DecompositionSynthesisTask.kt

## DecompositionSynthesis

#### Overview

**One-Line Description:** Decomposes complex problems into manageable subproblems, solves them, and synthesizes
integrated solutions.

**Detailed Description:** This task implements a "divide and conquer" reasoning strategy. It systematically breaks down
a large, complex problem into smaller, more manageable subproblems. It can use various decomposition strategies, such as
breaking the problem down by function, time, or hierarchy. After decomposing, it solves each subproblem individually,
respecting any identified dependencies between them. Finally, it synthesizes these individual solutions into a single,
coherent, and comprehensive solution to the original problem, optionally validating the final result for logical
consistency.

**Key Use Cases:**

* **System Design:** Breaking down the architecture of a new software system into components like API, database,
  frontend, and authentication.
* **Complex Bug Fixing:** Decomposing a bug report into potential causes (e.g., data corruption, race condition, UI
  error) to investigate each one systematically.
* **Project Planning:** Breaking a large project goal into a series of smaller, actionable tasks with clear
  dependencies.
* **Research and Analysis:** Structuring a complex research question into sub-questions, analyzing each, and
  synthesizing the findings into a final report.

---

#### Configuration Parameters

| Parameter                | Type           | Description                                                                    | Default Value |
|--------------------------|----------------|--------------------------------------------------------------------------------|---------------|
| `complex_problem`        | `String`       | The complex problem to decompose.                                              | **Required**  |
| `decomposition_strategy` | `String`       | The strategy to use: 'functional', 'temporal', 'spatial', 'hierarchical'.      | `functional`  |
| `max_depth`              | `Int`          | The maximum depth for recursive decomposition.                                 | `3`           |
| `synthesize_solution`    | `Boolean`      | If true, the task will integrate subproblem solutions into a single output.    | `true`        |
| `validate_coherence`     | `Boolean`      | If true, the synthesized solution is checked for consistency and completeness. | `true`        |
| `related_files`          | `List<String>` | A list of file paths to provide additional context for the problem.            | `null`        |

---

#### Process Flow

1. **Context Building:** The task first gathers all available context from previous tasks and any specified
   `related_files` to create a comprehensive understanding of the problem space.
2. **Problem Decomposition:** Using the selected `decomposition_strategy`, the AI agent breaks the `complex_problem`
   into a set of distinct subproblems. It also identifies any dependencies between these subproblems (e.g., Subproblem B
   depends on the solution of Subproblem A).
3. **Subproblem Solving:** The task performs a topological sort on the subproblems to create an efficient execution
   order that respects all dependencies. It then proceeds to solve each subproblem one by one, feeding the solutions of
   completed dependencies as context to the next ones.
4. **Solution Synthesis (Optional):** If `synthesize_solution` is enabled, the task takes all the individual subproblem
   solutions and integrates them into a single, unified solution that addresses the original problem.
5. **Coherence Validation (Optional):** If `validate_coherence` is enabled, a final review is conducted on the
   synthesized solution. The AI checks for logical consistency, completeness, and any internal contradictions, providing
   a list of issues and suggestions if any are found.
6. **Result Finalization:** The task outputs the final synthesized solution. If synthesis is disabled, it returns a
   concatenation of all the individual subproblem solutions.

---

#### Output Structure

**Final Result:**
The final result is a single string containing the complete, synthesized solution to the original problem. If solution
synthesis is disabled, this string will be a concatenation of all the individual subproblem solutions, each clearly
marked with its subproblem ID.

**UI Breakdown:**
The task provides a detailed, multi-tab view in the user interface to track its progress and results:

* **Overview:** A summary tab showing the initial configuration, live progress updates (e.g., "Solving subproblem 3/5"),
  and a final report including total time, number of subproblems, and average solution confidence.
* **Context:** Displays the contextual information gathered from related files and previous tasks that was used to
  inform the analysis.
* **Decomposition:** Shows the results of the decomposition step, including the rationale, a list of all identified
  subproblems, their estimated complexity, and a map of their dependencies.
* **Subproblem Solutions:** Contains the detailed solution for each subproblem, along with the AI's confidence score for
  that specific solution.
* **Synthesis:** (If enabled) Presents the final integrated solution, explains the approach used to combine the
  sub-solutions, and gives an overall confidence score.
* **Validation:** (If enabled) Displays the results of the coherence check, stating whether the solution is coherent and
  listing any identified issues or suggestions for improvement.

---

#### Example Usage

**Scenario:**
A software development team needs to design a scalable, real-time notification system for their social media
application. The system must handle various notification types and deliver them through multiple channels.

**Configuration:**

```json
{
  "complex_problem": "Design a scalable, real-time notification system for a social media app. It must handle user mentions, new comments, and friend requests. The system should be efficient and deliver notifications via web sockets and push notifications.",
  "decomposition_strategy": "functional",
  "max_depth": 2,
  "synthesize_solution": true,
  "validate_coherence": true
}
```

**Expected Output Snippet:**

```markdown
**Synthesized Solution for Real-Time Notification System**

**1. System Architecture Overview**
The proposed notification system is designed as a microservice-based architecture to ensure scalability and maintainability. It consists of three core services: the Notification Ingestion Service, the Fan-out & Delivery Service, and the User Preference Service.

**2. Core Components**
*   **Notification Ingestion Service (Subproblem SP1):** This service provides a REST API endpoint (`/api/v1/notifications`) to receive events from other microservices (e.g., comments, mentions). It validates incoming events, formats them into a standard notification payload, and publishes them to a Kafka topic named `notification-events`.

*   **Fan-out & Delivery Service (Subproblem SP2 & SP3):** This is the core processing engine. It consumes events from the `notification-events` topic. For each event, it determines the recipient list and fans out the notification. It then dispatches the notification to the appropriate delivery channels:
    *   **Web Sockets (SP3.1):** Pushes the notification to active user sessions via a dedicated WebSocket gateway.
    *   **Push Notifications (SP3.2):** Integrates with Firebase Cloud Messaging (FCM) and Apple Push Notification Service (APNS) to send mobile push notifications.

*   **User Preference Service (Subproblem SP4):** This service manages user settings for notifications, allowing users to enable or disable specific types of alerts. The Delivery Service queries this service before sending a notification to respect user preferences.

**3. Data Model**
The primary data store will be a NoSQL database (e.g., MongoDB) to store notification history for each user, with fields for `notificationId`, `userId`, `type`, `content`, `isRead`, and `timestamp`.
...
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\DialecticalReasoningTask.kt

## Dialectical Reasoning

#### Overview

**One-Line Description:** Resolve contradictions through thesis-antithesis-synthesis.

**Detailed Description:** This task applies the principles of dialectical reasoning to explore and resolve conflicts
between two opposing viewpoints (a thesis and an antithesis). It systematically analyzes each position, identifies the
core contradictions and tensions, and then generates a series of higher-level "syntheses" that aim to transcend the
original opposition. By iterating through multiple levels of synthesis, the task facilitates a deeper, more nuanced
understanding of the problem, often leading to innovative solutions that incorporate the valid insights from both sides.
It is particularly effective for tackling complex problems where there is no single, obvious right answer.

**Key Use Cases:**

* **Architectural Debates:** Resolving conflicts between different software architecture patterns (e.g., Monolith vs.
  Microservices).
* **Requirement Conflicts:** Finding a resolution for contradictory stakeholder requirements.
* **Strategic Planning:** Evaluating two opposing business strategies to form a more robust, integrated plan.
* **Design Philosophy:** Reconciling conflicting design principles or user experience goals.
* **Policy Analysis:** Examining the pros and cons of opposing policies to create a more comprehensive third option.

---

#### Configuration Parameters

| Parameter            | Type           | Description                                                 | Default Value |
|----------------------|----------------|-------------------------------------------------------------|---------------|
| `thesis`             | `String`       | The thesis statement or position to analyze.                | **Required**  |
| `antithesis`         | `String`       | The antithesis statement or opposing position.              | **Required**  |
| `context`            | `String`       | Context or domain for the dialectical analysis.             | `null`        |
| `synthesis_levels`   | `Int`          | Number of synthesis levels to iterate through (1-5).        | `3`           |
| `preserve_strengths` | `Boolean`      | Whether to preserve strengths from both sides in synthesis. | `true`        |
| `related_files`      | `List<String>` | Additional files to provide context for the analysis.       | `null`        |

---

#### Process Flow

The task executes in a structured, multi-step process to ensure a thorough dialectical analysis:

1. **Thesis Analysis:** The agent performs an in-depth analysis of the `thesis` statement, identifying its core claims,
   assumptions, strengths, and potential limitations.
2. **Antithesis Analysis:** The agent conducts a similar analysis of the `antithesis`, focusing on how it directly
   challenges or contradicts the thesis.
3. **Contradiction Exploration:** The agent identifies the primary points of conflict, underlying tensions, areas of
   partial agreement, and the root causes of the opposition between the thesis and antithesis.
4. **Iterative Synthesis:** This is the core of the reasoning process. The task iterates for the configured number of
   `synthesis_levels`:
    * **Level 1:** It generates an initial synthesis that attempts to resolve the primary conflict by creating a new,
      higher-level perspective that incorporates valid points from both the thesis and antithesis.
    * **Subsequent Levels:** For each new level, the synthesis from the previous level is treated as a new thesis. The
      agent then implicitly considers its limitations or a new antithesis to generate an even more refined and
      comprehensive synthesis.
5. **Final Integration:** After all synthesis levels are complete, the agent generates a final summary. This report
   outlines the entire "dialectical journey" from the initial conflict to the final resolution, highlighting key
   insights, practical implications, and actionable recommendations based on the final synthesis.

---

#### Output Structure

**Final Result:**
The final output passed to subsequent tasks is a concise Markdown-formatted string. It includes the analysis context,
the results of the first and final synthesis levels, and the complete final integration report. This provides a summary
of the most critical outcomes of the dialectical process.

**UI Breakdown:**
In the user interface, the task provides a detailed, tabbed view of the entire process, allowing for a comprehensive
review:

* **Overview:** Displays the initial configuration, a real-time log of the task's progress, and final summary statistics
  like total time and output size.
* **Context:** Shows the content of any provided `related_files` and context from prior tasks.
* **Thesis:** Contains the full, detailed analysis of the thesis statement.
* **Antithesis:** Contains the full, detailed analysis of the antithesis statement.
* **Contradictions:** Presents the complete exploration of tensions, conflicts, and overlaps between the two positions.
* **Synthesis L[n]:** A dedicated tab is created for each synthesis level (e.g., "Synthesis L1", "Synthesis L2"),
  showing the full text of the synthesis generated at that stage.
* **Final Integration:** Displays the final, comprehensive report summarizing the entire process and its conclusions.

---

#### Example Usage

**Scenario:**
A startup is deciding on the architecture for its new e-commerce platform. The development team is small, but the
company anticipates rapid growth. There's a debate between starting with a simple monolith for speed or a scalable
microservices architecture for long-term growth.

**Configuration:**

* **`thesis`**:
  `"Our new e-commerce platform should be built using a monolithic architecture for simplicity and rapid initial development."`
* **`antithesis`**:
  `"Our new e-commerce platform must be built with a microservices architecture to ensure scalability, team autonomy, and long-term maintainability."`
* **`context`**:
  `"We are a startup with a 5-person development team but expect rapid user growth within two years. The platform needs to handle high traffic during seasonal peaks and allow for easy addition of new features like a recommendation engine and a loyalty program."`
* **`synthesis_levels`**: `2`

**Expected Output Snippet:**

```markdown

#### Synthesis Level 2

The optimal approach is a "Modular Monolith" with a well-defined evolutionary path toward microservices. This synthesis transcends the initial dichotomy by prioritizing immediate development speed while embedding long-term scalability.

**Integration:**
- **Initial Phase:** Develop the platform as a single, deployable unit but with strict logical boundaries between modules (e.g., Product Catalog, Orders, Payments). These modules communicate via internal APIs, not direct code calls. This preserves the simplicity and speed of the monolith (Thesis).
- **Evolutionary Path:** As the team and traffic grow, these well-encapsulated modules can be extracted and deployed as independent microservices with minimal refactoring. This addresses the long-term scalability and maintainability concerns (Antithesis).

This strategy provides the best of both worlds: rapid market entry without accumulating prohibitive technical debt, ensuring the architecture can evolve with the business.
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\FiniteStateMachineTask.kt

## FiniteStateMachine

#### **Overview**

**One-Line Description:**
Model concepts using finite state machine analysis.

**Detailed Description:**
This task analyzes a concept, system, or process by modeling it as a finite state machine (FSM). It systematically
identifies all possible states, the events that trigger transitions between them, and the properties of the overall
system. The task generates a visual state diagram, identifies potential edge cases and error conditions, validates
formal properties like determinism and completeness, and can even generate test scenarios. This structured approach is
invaluable for system design, protocol analysis, and validating complex workflows.

**Key Use Cases:**

* **System Design and Validation:** Formalize and verify the behavior of software or hardware systems before
  implementation.
* **Understanding Complex Workflows:** Deconstruct and visualize intricate business processes or user flows.
* **Identifying Missing Requirements:** Uncover gaps in logic, unhandled states, or missing transitions.
* **Test Case Generation:** Automatically create a comprehensive suite of tests to ensure full state and transition
  coverage.
* **Protocol Analysis:** Model and validate communication protocols to ensure they are robust and error-free.

---

#### **Configuration Parameters**

| Parameter                 | Type           | Description                                                                              | Default Value |
|---------------------------|----------------|------------------------------------------------------------------------------------------|---------------|
| `concept_to_model`        | `String`       | The concept, system, or process to model as a finite state machine.                      | **Required**  |
| `initial_states`          | `List<String>` | A list of initial state(s) to consider at the start of the analysis.                     | `null`        |
| `known_events`            | `List<String>` | A list of known events or triggers that are expected to cause state transitions.         | `null`        |
| `identify_edge_cases`     | `Boolean`      | If true, the task will actively identify edge cases and potential error states.          | `true`        |
| `validate_properties`     | `Boolean`      | If true, the task will validate formal FSM properties like determinism and completeness. | `true`        |
| `generate_test_scenarios` | `Boolean`      | If true, the task will generate a set of test scenarios for the state transitions.       | `true`        |
| `domain_context`          | `String`       | The specific domain or context for the FSM (e.g., 'authentication system').              | `null`        |

---

#### **Process Flow**

1. **State Identification:** The task begins by analyzing the provided concept to identify all possible states. For each
   state, it determines its name, description, type (e.g., Initial, Normal, Error), and entry/exit conditions.
2. **Transition Identification:** Based on the identified states, the task determines the events and triggers that cause
   transitions between them. It creates a comprehensive transition table, including guard conditions and actions for
   each transition.
3. **State Diagram Generation:** A visual state diagram is generated in Mermaid format, providing a clear graphical
   representation of the states and their relationships.
4. **Edge Case Analysis (Optional):** If `identify_edge_cases` is enabled, the task analyzes the FSM for invalid
   transitions, missing logic, error states, and potential race conditions.
5. **Property Validation (Optional):** If `validate_properties` is enabled, the task formally validates key FSM
   properties, including determinism, completeness, reachability, and liveness.
6. **Test Scenario Generation (Optional):** If `generate_test_scenarios` is enabled, the task creates a diverse set of
   test scenarios covering happy paths, error conditions, and boundary cases to ensure full state and transition
   coverage.
7. **Summary Generation:** The task concludes by producing a comprehensive summary of the analysis, highlighting key
   findings, critical transitions, and actionable recommendations.

---

#### **Output Structure**

**Final Result:**
The final output passed to subsequent tasks is a concise markdown-formatted string. It includes a summary of the FSM
analysis, its key findings, and a checklist of the components that were generated (e.g., states, transitions, diagram,
test scenarios). This provides essential context without overwhelming the next step in a plan.

**UI Breakdown:**
In the user interface, the detailed analysis is presented in a series of tabs for easy navigation:

* **Overview:** A summary card showing the concept being modeled, its domain, status, and a final report of the analysis
  components.
* **States:** A detailed list of all identified states, including their names, descriptions, types, and invariants.
* **Transitions:** A comprehensive table or list detailing all state transitions, including source/target states,
  triggers, and guard conditions.
* **State Diagram:** A rendered Mermaid diagram visually representing the finite state machine.
* **Edge Cases:** (If enabled) A structured analysis of potential edge cases, error conditions, and recovery paths.
* **Validation:** (If enabled) A report on the validation of FSM properties, indicating whether each property passed or
  failed and why.
* **Test Scenarios:** (If enabled) A list of generated test scenarios, each with a name, event sequence, and expected
  outcome.
* **Summary:** A high-level summary of the entire analysis, including key insights and actionable recommendations.

---

#### **Example Usage**

**Scenario:**
A developer needs to design and validate the user authentication flow for a new web application. They want to ensure all
states (e.g., logged out, pending, logged in, locked) and transitions (e.g., login success, login failure, password
reset) are correctly handled.

**Configuration:**

```json
{
  "concept_to_model": "User login process for a web application",
  "initial_states": ["Logged Out"],
  "known_events": [
    "Enter Credentials",
    "Submit",
    "Successful Login",
    "Failed Login",
    "Request Password Reset"
  ],
  "domain_context": "Web application security",
  "identify_edge_cases": true,
  "validate_properties": true,
  "generate_test_scenarios": true
}
```

**Expected Output Snippet:**

```markdown

## FSM Analysis: User login process for a web application


### Summary
The analysis modeled the user login process, identifying key states such as Logged Out, Awaiting Credentials, Authenticating, Logged In, and Account Locked. The critical transition is from Authenticating to Logged In, guarded by valid credential verification. Key findings include a potential race condition if a user attempts multiple logins simultaneously and a recommendation to add a 'Password Reset Pending' state for improved security.


### Key Components
- States identified and analyzed
- Transitions mapped
- State diagram generated
- Edge cases identified
- Properties validated
- Test scenarios generated
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\GameTheoryTask.kt

## Game Theory

### Overview

**One-Line Description:** Analyze strategic interactions using game theory.

**Detailed Description:** This task performs a comprehensive game theory analysis of strategic situations. It models
interactions between rational decision-makers (players) to predict outcomes and identify optimal strategies. The process
involves analyzing the game's structure, constructing payoff matrices, identifying equilibria and dominant strategies,
and finding efficient outcomes. It supports various game types, including cooperative, non-cooperative, zero-sum, and
repeated games, making it a powerful tool for competitive analysis, negotiation, and strategic planning.

**Key Use Cases:**

* Strategic decision making
* Competitive analysis
* Negotiation planning
* Market strategy
* Conflict resolution

### Configuration Parameters

This table details the configurable parameters for the Game Theory task.

| Parameter                     | Type                        | Description                                                                 | Default Value       |
|-------------------------------|-----------------------------|-----------------------------------------------------------------------------|---------------------|
| `game_scenario`               | `String`                    | The strategic situation or game to analyze.                                 | **Required**        |
| `players`                     | `List<String>`              | List of players/agents in the game.                                         | **Required**        |
| `player_strategies`           | `Map<String, List<String>>` | Available strategies for each player (optional, can be inferred).           | `null`              |
| `game_type`                   | `String`                    | Type of game: cooperative, non-cooperative, zero-sum, repeated, sequential. | `"non-cooperative"` |
| `build_payoff_matrix`         | `Boolean`                   | Whether to construct a payoff matrix.                                       | `true`              |
| `find_nash_equilibria`        | `Boolean`                   | Whether to identify Nash equilibria.                                        | `true`              |
| `analyze_dominant_strategies` | `Boolean`                   | Whether to analyze dominant strategies.                                     | `true`              |
| `find_pareto_optimal`         | `Boolean`                   | Whether to identify Pareto optimal outcomes.                                | `true`              |
| `provide_recommendations`     | `Boolean`                   | Whether to provide strategic recommendations for each player.               | `true`              |
| `repeated_game_analysis`      | `Boolean`                   | Whether to analyze the game as a repeated game.                             | `false`             |
| `iterations`                  | `Int`                       | Number of iterations for repeated game analysis.                            | `10`                |
| `additional_context`          | `String`                    | Additional context or constraints.                                          | `null`              |

### Process Flow

The task executes the following steps to perform its analysis:

1. **Initialization:** The task starts by validating that a game scenario and players have been specified. It sets up
   the UI with an "Overview" tab to track progress.
2. **Context Gathering:** It compiles context from previous tasks and any `additional_context` provided in the
   configuration to inform the analysis.
3. **Game Structure Analysis:** The task first analyzes the fundamental structure of the game, identifying the game
   type, strategy spaces for each player, and the nature of the payoffs.
4. **Payoff Matrix Construction (Optional):** If `build_payoff_matrix` is true, it constructs a payoff matrix, detailing
   the outcomes for each player for every possible combination of strategies.
5. **Nash Equilibria Identification (Optional):** If `find_nash_equilibria` is true, it analyzes the payoff matrix to
   find all Nash equilibria, where no player can benefit by unilaterally changing their strategy.
6. **Dominant Strategy Analysis (Optional):** If `analyze_dominant_strategies` is true, it identifies any strategies
   that are always optimal for a player, regardless of what other players do.
7. **Pareto Optimality Analysis (Optional):** If `find_pareto_optimal` is true, it identifies outcomes where no player
   can be made better off without making another player worse off, highlighting the efficiency of different outcomes.
8. **Repeated Game Analysis (Optional):** If `repeated_game_analysis` is true, it analyzes the scenario as a repeated
   interaction, considering factors like reputation, trigger strategies, and the folk theorem.
9. **Strategic Recommendations (Optional):** If `provide_recommendations` is true, it synthesizes all prior analysis to
   provide actionable strategic advice for each player.
10. **Structured Summary:** Finally, the task generates a structured summary of the entire analysis, extracting key
    findings like game type, equilibria, and recommendations.

### Output Structure

#### Final Result

The final output is a concise Markdown-formatted string summarizing the entire analysis. It includes the game scenario,
players, game type, and truncated sections for the game structure, Nash equilibria, dominant strategies, and key
recommendations. This summary is designed to be passed as context to subsequent tasks.

#### UI Breakdown

The task provides a detailed, multi-tab view in the user interface for a comprehensive understanding of the analysis:

* **Overview:** Displays the initial configuration, current status, and a final completion summary.
* **Context:** Shows any context provided from previous tasks or the configuration.
* **Game Structure:** Contains the detailed analysis of the game's fundamental components.
* **Payoff Matrix:** Presents the constructed payoff matrix.
* **Nash Equilibria:** Details the identified Nash equilibria and why they are stable.
* **Dominant Strategies:** Explains any dominant or dominated strategies found.
* **Pareto Optimality:** Lists the Pareto optimal outcomes and discusses their efficiency.
* **Repeated Game:** (If enabled) Provides the analysis of the game in a repeated context.
* **Recommendations:** Offers detailed, actionable advice for each player.
* **Summary:** Shows the final structured summary of key findings.

### Example Usage

#### Scenario

Two competing coffee shops, "BrewBeans" and "CafeDrift," are deciding whether to set a "High Price" or a "Low Price" for
their lattes. A low price attracts more customers but yields lower margins. If both set a low price, they split the
market at a lower profit. If both set a high price, they maintain high margins. If one sets a low price and the other
high, the low-price shop captures most of the market. This is a classic "Prisoner's Dilemma" scenario.

#### Configuration

```json
{
  "game_scenario": "Two coffee shops, BrewBeans and CafeDrift, must simultaneously decide on a pricing strategy: High Price or Low Price. If both choose High, they each make $500 profit. If both choose Low, they each make $300 profit. If one chooses Low and the other High, the Low-price shop makes $700 and the High-price shop makes $100.",
  "players": ["BrewBeans", "CafeDrift"],
  "game_type": "non-cooperative",
  "find_nash_equilibria": true,
  "analyze_dominant_strategies": true,
  "provide_recommendations": true
}
```

#### Expected Output Snippet

```markdown

## Game Theory Analysis: Two coffee shops...


### Dominant Strategies
For both BrewBeans and CafeDrift, setting a "Low Price" is a strictly dominant strategy. Regardless of what the competitor does, each shop earns a higher payoff by choosing "Low Price".


### Nash Equilibria
The single Nash Equilibrium is (Low Price, Low Price). In this state, neither shop can improve its outcome by unilaterally changing its price. While (High Price, High Price) is a better outcome for both, it is not a stable equilibrium.


### Key Recommendations
- **BrewBeans**: Play the dominant strategy: "Low Price". Be aware that this will likely lead to a lower-profit equilibrium for both shops.
- **CafeDrift**: Play the dominant strategy: "Low Price".
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\GeneticOptimizationTask.kt

## GeneticOptimization

### Overview

**One-Line Description:** Iteratively evolve and perfect text through genetic algorithms.

**Detailed Description:** This task uses genetic algorithms to optimize a piece of text through iterative evolution. It
generates variations using configurable mutation strategies, evaluates these variants against defined optimization
criteria, and selects the top performers to create the next generation. It can also apply crossover to combine
successful traits from different variants. The task tracks the fitness progression across all generations and provides a
detailed analysis of the evolution, including which strategies were most effective. It supports custom evaluation
criteria and weights, making it a powerful tool for refining prompts, marketing copy, technical documentation, or any
form of messaging.

**Key Use Cases:**

* Perfecting prompts and instructions for LLMs.
* Refining marketing copy for better engagement.
* Optimizing technical documentation for clarity and accuracy.
* Improving the overall clarity and impact of any message.

### Configuration Parameters

| Parameter             | Type                  | Description                                                                                | Default Value                                 |
|-----------------------|-----------------------|--------------------------------------------------------------------------------------------|-----------------------------------------------|
| `initial_text`        | `String`              | The initial text to optimize (seed for genetic algorithm).                                 | **Required**                                  |
| `optimization_goal`   | `String`              | The optimization goal or criteria (e.g., 'clarity and conciseness', 'persuasiveness').     | **Required**                                  |
| `num_generations`     | `Int`                 | Number of generations to evolve.                                                           | `5`                                           |
| `population_size`     | `Int`                 | Population size per generation.                                                            | `6`                                           |
| `selection_size`      | `Int`                 | Number of top candidates to keep each generation.                                          | `2`                                           |
| `mutation_strategies` | `List<String>`        | Mutation strategies to use (e.g., 'rephrase', 'simplify', 'elaborate', 'restructure').     | `["rephrase", "simplify", "elaborate"]`       |
| `enable_crossover`    | `Boolean`             | Whether to enable crossover (combining traits from multiple candidates).                   | `true`                                        |
| `evaluation_weights`  | `Map<String, Double>` | Evaluation criteria weights (e.g., `{'clarity': 0.4, 'conciseness': 0.3, 'impact': 0.3}`). | `{"clarity": 0.35, "conciseness": 0.25, ...}` |
| `constraints`         | `List<String>`        | Additional context or constraints for optimization.                                        | `null`                                        |
| `task_description`    | `String`              | A description of the task's purpose.                                                       | `null`                                        |
| `task_dependencies`   | `List<String>`        | A list of task IDs that must be completed before this one can start.                       | `null`                                        |

### Process Flow

1. **Initialization:** The task starts by validating the configuration, ensuring that `initial_text` and
   `optimization_goal` are provided.
2. **Initial Evaluation:** The provided `initial_text` is evaluated against the defined criteria to establish a baseline
   fitness score.
3. **Evolutionary Loop:** The task iterates through the specified number of generations. In each generation:
   a. **Selection:** The best-performing variants (survivors) from the previous generation are selected to continue.
   b. **Mutation:** New variants are created by applying random mutation strategies (e.g., rephrase, simplify,
   elaborate) to the survivors.
   c. **Crossover (Optional):** If enabled, new variants are created by combining the traits of the top two survivors.
   d. **Evaluation:** All new and surviving variants in the current generation's population are evaluated and assigned a
   fitness score.
4. **Tracking:** The best variant found across all generations is continuously tracked and updated.
5. **Analysis & Reporting:** After the final generation, the task compiles a comprehensive analysis, including the
   fitness progression over time, the effectiveness of different mutation strategies, and a detailed breakdown of the
   best variant found.

### Output Structure

**Final Result:**
The final output is a Markdown-formatted summary containing the final optimized text, key performance metrics (initial
score, final score, and total improvement), and a list of the key improvements identified in the best variant. This
concise summary is passed to subsequent tasks.

**UI Breakdown:**
The task provides a detailed, multi-tab view in the UI for in-depth analysis:

* **Overview:** Displays the task configuration, evaluation criteria, initial text, and a live-updating log of the
  generation-by-generation progress and final summary metrics.
* **Generation [N]:** A separate tab is created for each generation, showing detailed results, population statistics (
  best, average, worst scores), and a breakdown of the top-performing variants from that generation, including their
  text, scores, strengths, and weaknesses.
* **Evolution Analysis:** A final summary tab that visualizes the entire evolutionary process. It includes a table of
  fitness progression across all generations, an analysis of the effectiveness of each mutation strategy, and a
  side-by-side comparison of the initial and final optimized text with a detailed breakdown of the improvements.

### Example Usage

**Scenario:**
A marketing team wants to optimize the subject line for an email campaign promoting a new productivity app. The goal is
to make it more engaging and persuasive to increase open rates.

**Configuration:**

* **`initial_text`**: `"New App to Help You Manage Your Tasks"`
* **`optimization_goal`**: `"Maximize persuasiveness and create a sense of urgency for a tech-savvy audience."`
* **`num_generations`**: `5`
* **`population_size`**: `10`
* **`evaluation_weights`**: `{"persuasiveness": 0.5, "clarity": 0.3, "urgency": 0.2}`

**Expected Output Snippet:**

```markdown

## Genetic Optimization Results

**Optimization Goal:** Maximize persuasiveness and create a sense of urgency for a tech-savvy audience.


### Final Optimized Text

```

Stop Juggling Tasks. Your New Productivity Command Center is Here.

```


### Performance Metrics

- **Initial Score:** 45.2/100
- **Final Score:** 88.7/100
- **Improvement:** +43.5 points
- **Generations:** 5
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\LateralThinkingTask.kt

## Lateral Thinking

#### **Overview**

**One-Line Description:**
Break conventional thinking patterns to find innovative solutions.

**Detailed Description:**
The Lateral Thinking task applies a structured set of creative techniques to generate unconventional solutions for a
given problem. It systematically moves through methods like reversing the problem, introducing random stimuli,
challenging core assumptions, and exaggerating constraints to break free from traditional thought patterns. The task
generates numerous alternatives, evaluates their novelty and feasibility, synthesizes insights across all techniques,
and provides a ranked list of breakthrough ideas. It is an ideal tool for innovation, overcoming design impasses, and
fostering creative problem-solving.

**Key Use Cases:**

* **Innovation:** Generating novel product features or business models.
* **Problem-Solving:** Finding creative solutions when standard approaches have failed.
* **Design:** Breaking through creative blocks in user experience, product, or system design.
* **Strategy:** Developing unconventional strategies to gain a competitive advantage.

---

#### **Configuration Parameters**

| Parameter              | Type            | Description                                                                                                                                        | Default Value                                                                        |
|------------------------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `problem`              | `String?`       | The problem or challenge to approach with lateral thinking.                                                                                        | **Required**                                                                         |
| `techniques`           | `List<String>?` | Lateral thinking techniques to apply: `reversal`, `random_stimulus`, `challenge_assumptions`, `exaggeration`, `escape`, `metaphor`, `provocation`. | `["reversal", "random_stimulus", "challenge_assumptions", "exaggeration", "escape"]` |
| `num_alternatives`     | `Int`           | Number of alternative solutions to generate per technique.                                                                                         | `5`                                                                                  |
| `evaluate_feasibility` | `Boolean`       | Whether to perform a detailed evaluation of the feasibility of generated ideas.                                                                    | `true`                                                                               |
| `domain_context`       | `String?`       | The specific domain or context to constrain the thinking (e.g., "mobile app development", "aerospace engineering").                                | `null`                                                                               |
| `constraints`          | `List<String>?` | A list of additional constraints or requirements to consider during idea generation.                                                               | `null`                                                                               |
| `task_description`     | `String?`       | A custom description for the task instance.                                                                                                        | Automatically generated from the problem statement.                                  |
| `task_dependencies`    | `List<String>?` | A list of other task IDs that must be completed before this one can run.                                                                           | `null`                                                                               |

---

#### **Process Flow**

1. **Initialization:** The task starts by setting up an "Overview" tab that displays the problem statement,
   configuration, and real-time progress.
2. **Technique Application:** The task iterates through each selected lateral thinking technique. For each technique,
   it:
    * Creates a dedicated UI tab.
    * Prompts an AI agent with instructions specific to that technique (e.g., "Reverse the problem" or "Introduce a
      random stimulus").
    * Generates a set of unconventional ideas, each with a title, description, novelty score, and feasibility score.
    * Displays the results in the technique's dedicated tab.
3. **Cross-Technique Synthesis:** After all techniques have been applied, a new "Synthesis" tab is created. An AI agent
   analyzes the ideas and insights from all techniques to identify common themes, patterns, and potential hybrid
   solutions, providing a high-level summary of the creative session.
4. **Feasibility Evaluation (Optional):** If `evaluate_feasibility` is enabled, a "Feasibility" tab is added. A
   specialized AI agent assesses the most promising ideas against practical constraints like implementation complexity,
   resource requirements, and risk, providing a ranked list of the most viable options.
5. **Final Summary:** The task compiles all generated ideas, synthesized insights, and feasibility evaluations into a
   comprehensive report in a final "Summary" tab. A concise version of this report is prepared as the final text output.

---

#### **Output Structure**

**Final Result:**
The final output is a concise Markdown-formatted string summarizing the entire process. It includes:

* The original problem statement.
* A list of the techniques that were applied.
* Key statistics, such as the total number of ideas generated and the average novelty/feasibility scores.
* A list of the top 3-5 "breakthrough" ideas, with brief descriptions.
* A summary of the recommended unconventional approaches derived from the synthesis step.
* A brief overview of the feasibility assessment, if it was performed.

**UI Breakdown:**
The task provides a rich, tabbed interface for detailed analysis:

* **Overview:** Displays the initial configuration and a live log of the task's progress.
* **[Technique Name] (e.g., "1. Reversal"):** A separate tab is created for each technique. It contains the specific
  provocation used, a description of how the technique was applied, and a detailed breakdown of every idea generated.
* **Synthesis:** Shows the full text of the cross-technique analysis, highlighting common themes, patterns, and
  breakthrough insights.
* **Feasibility:** (If enabled) Presents the detailed feasibility report, including an overall assessment, a ranked list
  of top ideas, and suggestions for hybrid approaches.
* **Summary:** A comprehensive, well-formatted report containing the executive summary, a detailed list of the top 10
  breakthrough ideas, synthesized insights, and the full feasibility evaluation.

---

#### **Example Usage**

**Scenario:**
A team is tasked with designing a more engaging way for users to learn a new language on a mobile app, but they are
stuck on traditional flashcard and quiz-based ideas. They decide to use the Lateral Thinking task to generate fresh
concepts.

**Configuration:**

```json
{
  "problem": "Design a radically more engaging and effective mobile app experience for learning a new language, moving beyond simple flashcards and quizzes.",
  "techniques": [
    "reversal",

    "random_stimulus",
    "metaphor"
  ],
  "num_alternatives": 3,
  "evaluate_feasibility": true,
  "domain_context": "Mobile application for casual learners (iOS and Android)"
}
```

**Expected Output Snippet:**

```markdown

## Lateral Thinking Results

**Problem:** Design a radically more engaging and effective mobile app experience for learning a new language, moving beyond simple flashcards and quizzes.


### Techniques Applied
- Reversal
- Random_stimulus
- Metaphor


### Key Statistics
- **Total Ideas Generated:** 9
- **Average Novelty:** 78.5%
- **Average Feasibility:** 62.0%


### Top Breakthrough Ideas
1. **The Anti-Goal App** (reversal)
   Instead of learning a language, users try to *forget* it by identifying and flagging words they already know. This gamifies vocabulary assessment in a novel way.
2. **Language DJ** (random_stimulus)
   Inspired by "jazz music," the app generates personalized language lessons as audio "mixes" that match the user's mood, activity, or musical taste.
3. **Vocabulary Garden** (metaphor)
   Users plant "seed words" and "water" them with practice. Words grow into plants and eventually form a vibrant garden, visualizing their vocabulary growth.


### Recommended Approaches
- Gamify progress through tangible, visual metaphors rather than points and badges.
- Integrate learning into passive activities like listening to music or podcasts.
- Reframe "testing" as a creative or discovery-oriented activity.
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\MetaCognitiveReflectionTask.kt

## MetaCognitiveReflection

### Overview

**One-Line Description:** Reflects on and critiques the reasoning processes of other tasks.

**Detailed Description:** This task performs a "thinking about thinking" analysis on the output of a previously executed
task. It critically examines the reasoning, solution, or analysis provided by another task to identify underlying
assumptions, potential cognitive biases, and logical inconsistencies. By evaluating aspects like confidence,
completeness, and alternative approaches, it helps improve the overall quality and robustness of a plan's execution. It
can suggest improvements, identify knowledge gaps, and provide a structured critique to enhance decision-making.

**Key Use Cases:**

* **Quality Assurance:** Critiquing the output of a code generation or modification task to catch logical flaws or
  unhandled edge cases.
* **Strategy Validation:** Evaluating the reasoning behind a planning task (e.g., `HierarchicalPlanningMode`) to ensure
  the proposed strategy is sound.
* **Debugging Failed Tasks:** Analyzing the output of a failed task to understand why its reasoning was flawed.
* **Improving AI Reliability:** Systematically checking for common AI pitfalls like making unstated assumptions or
  exhibiting confirmation bias.

### Configuration Parameters

| Parameter              | Type           | Description                                                                                                         | Default Value                                             |
|------------------------|----------------|---------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| `subject_task_id`      | `String`       | The ID of the task whose reasoning process should be reflected upon.                                                | **Required**                                              |
| `reflection_aspects`   | `List<String>` | Aspects to evaluate. Valid options: 'assumptions', 'biases', 'alternatives', 'confidence', 'completeness', 'logic'. | `["assumptions", "biases", "alternatives", "confidence"]` |
| `suggest_improvements` | `Boolean`      | Whether to suggest improvements to the reasoning process.                                                           | `true`                                                    |
| `identify_gaps`        | `Boolean`      | Whether to identify knowledge gaps and uncertainties.                                                               | `true`                                                    |
| `evaluate_confidence`  | `Boolean`      | Whether to evaluate the confidence level of the subject task's conclusions.                                         | `true`                                                    |
| `task_description`     | `String`       | A user-defined description of the task's purpose.                                                                   | `null`                                                    |
| `task_dependencies`    | `List<String>` | A list of task IDs that must be completed before this task can run.                                                 | `[]`                                                      |

### Process Flow

1. **Target Identification:** The task begins by identifying the `subject_task_id` provided in the configuration. It
   retrieves the final result and any relevant prior context associated with this target task from the overall execution
   state.
2. **Prompt Construction:** It constructs a detailed, structured prompt for the AI. This prompt includes the full result
   of the subject task, the prior context it operated on, and specific instructions based on the configured
   `reflection_aspects`.
3. **AI-Powered Analysis:** A specialized `ChatAgent`, acting as a meta-cognitive analyst, processes the prompt. It
   performs a critical evaluation based on the requested aspects (e.g., checking for assumptions, evaluating logical
   consistency).
4. **Structured Output Generation:** The AI generates a detailed, structured report in Markdown format, with separate
   sections for each analyzed aspect, improvement suggestions, knowledge gaps, and a confidence assessment.
5. **Summary Extraction:** The task parses the detailed AI report to extract the most critical findings and generates a
   concise summary.
6. **UI Presentation:** The results are displayed in a comprehensive, tabbed format in the user interface for easy
   review.

### Output Structure

**Final Result:** The final output passed to subsequent tasks is a concise summary string. This summary typically
contains the key insights and most critical points identified during the reflection, often presented as a bulleted list.

**UI Breakdown:**

* **Overview:** Displays the configuration parameters used for the reflection, including the subject task ID and the
  aspects being analyzed.
* **Context:** (If applicable) Shows the prior context and data that the original subject task had access to, providing
  a basis for the critique.
* **Reflection Analysis:** Contains the full, detailed critique generated by the AI, with sections for each reflection
  aspect, suggestions, and identified gaps.
* **Summary:** Presents the final, condensed summary of the reflection's key findings.

### Example Usage

**Scenario:** A `FileModificationTask` (ID: `task_abc123`) was used to refactor a complex function. We want to ensure
the reasoning behind the refactor is sound and doesn't introduce new bugs before proceeding.

**Configuration:**

```json
{
  "subject_task_id": "task_abc123",
  "reflection_aspects": ["assumptions", "logic", "completeness"],
  "suggest_improvements": true,
  "identify_gaps": false,
  "evaluate_confidence": true
}
```

**Expected Output Snippet:**

```markdown
**Key Insights:**
- The refactor assumes the input array will never be empty, which could lead to a null pointer exception.
- The logic for the primary loop is sound and more efficient than the original implementation.
- Completeness check: The refactor does not account for negative integer values in the input, which was an implicit requirement.
- Confidence in the core logic is high, but low for edge case handling.
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\MultiPerspectiveAnalysisTask.kt

## Multi-Perspective Analysis

### Overview

**One-Line Description:** Analyze problems from multiple viewpoints with synthesis.

**Detailed Description:** This task provides a structured framework for analyzing a topic or problem from several
distinct perspectives. It examines the subject from each specified viewpoint, generating a detailed analysis that
identifies key considerations, risks, and opportunities. The task can then synthesize these individual analyses to
identify common themes, highlight conflicts, and produce a balanced, unified conclusion. This methodology is crucial for
making well-rounded decisions by ensuring that a problem is considered from all relevant angles.

**Key Use Cases:**

* **Architectural Decision Making:** Evaluating a new technology from technical, financial, and operational
  perspectives.
* **Code Review:** Assessing code changes from the angles of performance, maintainability, security, and user impact.
* **Strategic Planning:** Analyzing a business strategy by considering market, competitor, and internal capability
  viewpoints.
* **Risk Assessment:** Identifying potential risks by looking at a project through financial, legal, and reputational
  lenses.
* **Feature Evaluation:** Deciding on a new product feature by analyzing it from user experience, business value, and
  technical feasibility perspectives.

### Configuration Parameters

The task's behavior is controlled by the following parameters:

| Parameter             | Type           | Description                                                                              | Default Value |
|-----------------------|----------------|------------------------------------------------------------------------------------------|---------------|
| `analysis_subject`    | `String`       | The topic or problem to analyze from multiple viewpoints.                                | **Required**  |
| `perspectives`        | `List<String>` | A list of perspectives to consider (e.g., "technical", "business", "ethical", "user").   | **Required**  |
| `synthesize`          | `Boolean`      | If true, the task will synthesize the individual perspectives into a unified conclusion. | `true`        |
| `consensus_threshold` | `Double`       | The minimum confidence threshold (0.0-1.0) for perspective agreement during synthesis.   | `0.7`         |
| `related_files`       | `List<String>` | A list of file paths or glob patterns to provide additional context for the analysis.    | `null`        |
| `task_dependencies`   | `List<String>` | A list of task IDs that must be completed before this task can run.                      | `null`        |
| `state`               | `TaskState`    | The initial state of the task.                                                           | `Pending`     |

### Process Flow

1. **Initialization:** The task begins by validating that an `analysis_subject` and a list of `perspectives` have been
   provided. It then sets up an "Overview" tab in the UI to display the initial configuration.
2. **Context Gathering:** It reads the content from any files specified in `related_files` and gathers results from any
   preceding tasks to build a comprehensive context.
3. **Independent Perspective Analysis:** The task iterates through each string in the `perspectives` list. For each one:
    * A dedicated UI tab is created for that perspective.
    * A prompt is constructed, instructing an AI agent to analyze the `analysis_subject` from that specific viewpoint,
      using the gathered context.
    * The agent's analysis is captured and displayed in the corresponding tab.
4. **Synthesis (Conditional):** If the `synthesize` parameter is `true`, the task proceeds to this step after all
   individual analyses are complete.
    * A "Synthesis" tab is created in the UI.
    * A new prompt is generated that includes the original subject and all the individual perspective analyses.
    * An AI agent is tasked with synthesizing these viewpoints, identifying common themes, highlighting conflicts, and
      forming a balanced, unified recommendation.
    * The resulting synthesis is displayed in its tab.
5. **Final Output Generation:** The task compiles all the individual analyses and the final synthesis (if generated)
   into a single, comprehensive Markdown report.

### Output Structure

**Final Result:**
The final output passed to subsequent tasks is a single Markdown string. This string contains the complete analysis,
with each perspective's findings presented under a distinct heading (e.g., `## Technical Perspective`). If synthesis was
enabled, a final `## Synthesis` section is appended, containing the integrated conclusion.

**UI Breakdown:**
The user interface provides a detailed, tabbed view of the process:

* **Overview:** Displays the initial analysis subject and the list of perspectives being considered.
* **[Perspective Name] (e.g., "Technical"):** A separate tab is generated for each perspective, containing the detailed
  analysis from that specific viewpoint.
* **Synthesis:** If enabled, this tab contains the unified conclusion, recommendations, and a summary of agreements and
  conflicts between the different perspectives.

### Example Usage

**Scenario:**
A software team is considering migrating their application's primary database from a traditional SQL database to a NoSQL
alternative to improve scalability. They need to evaluate this significant architectural change from multiple angles
before making a decision.

**Configuration:**

```json
{
  "analysis_subject": "Evaluate the proposal to migrate our primary user database from PostgreSQL to MongoDB to improve performance and scalability for our rapidly growing user base.",
  "perspectives": [
    "Technical Feasibility",
    "Business Impact",
    "Operational Readiness"
  ],
  "synthesize": true,
  "consensus_threshold": 0.7,
  "related_files": [
    "docs/current_architecture.md",
    "reports/performance_bottlenecks.csv"
  ]
}
```

**Expected Output Snippet:**

```markdown

## Multi-Perspective Analysis: Evaluate the proposal to migrate...


### Technical Feasibility Perspective
Migrating to MongoDB offers significant advantages in horizontal scaling and flexible schema management, which directly addresses our current performance bottlenecks. However, the migration process will be complex, requiring a data transformation strategy to handle the shift from a relational to a document-based model. Key risks include potential data loss during migration and the learning curve for the development team...


### Business Impact Perspective
...


### Operational Readiness Perspective
...


### Synthesis
The analysis reveals a strong consensus across all perspectives that a database migration is necessary to support future growth. The primary tension exists between the long-term technical benefits and the short-term operational costs and risks. The unified recommendation is to proceed with a phased migration, starting with a non-critical service as a pilot project. This approach will allow the team to build expertise and validate the migration strategy while minimizing risk to the core business.
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\NarrativeGenerationTask.kt

## NarrativeGeneration

#### **Overview**

* **One-Line Description:** Generate complete narratives from analysis and outlines.
* **Detailed Description:** This task extends the foundational `NarrativeReasoning` task to produce complete,
  publication-ready narratives. It begins by performing a comprehensive analysis of a given subject or scenario. Based
  on this analysis, it constructs a detailed, scene-by-scene outline. The task then iteratively generates the content
  for each scene, feeding the context from previously written scenes into the generation of the next one to ensure
  consistency in plot, character, and tone. It is highly configurable, allowing control over structure, style, point of
  view, and specific narrative elements.
* **Key Use Cases:**
    * **Creative Writing:** Generating complete short stories, novellas, or chapters from a simple premise.
    * **Scenario Planning:** Developing detailed narratives for business or strategic scenarios to explore potential
      outcomes.
    * **Content Creation:** Authoring scripts, marketing copy, or user journey narratives.
    * **Game Development:** Creating quest lines, character backstories, and in-game lore.

#### **Configuration Parameters**

| Parameter                | Type               | Description                                                                                  | Default Value            |
|--------------------------|--------------------|----------------------------------------------------------------------------------------------|--------------------------|
| `subject`                | `String`           | The subject or scenario to develop into a full narrative.                                    | **Required**             |
| `narrative_elements`     | `Map<String, Any>` | Narrative elements to consider (characters, setting, conflict, timeline, etc.).              | `null`                   |
| `target_word_count`      | `Int`              | Target word count for the complete narrative.                                                | `5000`                   |
| `number_of_acts`         | `Int`              | Number of acts in the narrative structure (typically 3 or 5).                                | `3`                      |
| `scenes_per_act`         | `Int`              | Average number of scenes per act.                                                            | `3`                      |
| `writing_style`          | `String`           | The writing style (e.g., 'literary', 'thriller', 'technical', 'conversational').             | `"literary"`             |
| `point_of_view`          | `String`           | The point of view (e.g., 'first person', 'third person limited', 'third person omniscient'). | `"third person limited"` |
| `tone`                   | `String`           | The tone (e.g., 'dramatic', 'humorous', 'suspenseful', 'reflective').                        | `"dramatic"`             |
| `detailed_descriptions`  | `Boolean`          | Whether to include detailed scene descriptions.                                              | `true`                   |
| `include_dialogue`       | `Boolean`          | Whether to include character dialogue.                                                       | `true`                   |
| `show_internal_thoughts` | `Boolean`          | Whether to show internal character thoughts.                                                 | `true`                   |
| `revision_passes`        | `Int`              | The number of revision passes to perform on each generated scene for quality improvement.    | `1`                      |
| `task_dependencies`      | `List<String>`     | A list of task IDs that must be completed before this task can start.                        | `null`                   |

#### **Process Flow**

The task executes in a structured, multi-phase process:

1. **Phase 1: Narrative Analysis:** The task first runs the base `NarrativeReasoning` analysis on the subject and
   provided elements. This initial step identifies key plot points, character motivations, and potential inconsistencies
   to form a solid foundation for the story.
2. **Phase 2: Outline Generation:** Using the results of the analysis, the task generates a detailed, scene-by-scene
   outline for the entire narrative. This outline includes a title, premise, and a breakdown of acts and scenes, with
   each scene specifying its purpose, setting, characters, key events, and emotional arc.
3. **Phase 3: Scene Generation:** The task proceeds to write each scene iteratively. For each scene, it constructs a
   context that includes the overall premise and details from the last one or two previously generated scenes. This
   ensures a coherent flow and consistent character development. Optional revision passes are performed on each scene to
   refine the prose and enhance its impact.
4. **Phase 4: Final Assembly:** Once all scenes are generated, the task compiles them into a single, complete narrative
   document, structured by acts and scenes. It concludes by calculating final statistics like total word count and
   execution time.

#### **Output Structure**

* **Final Result:** The final output passed to subsequent tasks is a concise Markdown summary. It includes the
  narrative's title, total word count, scene count, and total generation time, along with a high-level view of the
  story's outline. It explicitly notes that the full text is available in the UI.

* **UI Breakdown:** The user interface provides a comprehensive, tabbed view of the entire generation process:
    * **Overview:** A central hub showing the initial configuration, a real-time progress log of the generation phases,
      and final statistics (total words, scenes, time).
    * **Outline:** Displays the complete, detailed narrative outline generated in Phase 2.
    * **Scene `[Number]`:** A dedicated tab is created for each generated scene. This tab contains the full text of the
      scene, its word count, a summary of key moments, and the emotional/physical state of characters at the scene's
      conclusion.
    * **Complete Narrative:** A final tab that presents the fully assembled narrative, combining all generated scenes
      into a single, readable document.

#### **Example Usage**

* **Scenario:** A writer wants to create a short, suspenseful sci-fi story about a lone astronaut who discovers a
  mysterious, humming artifact buried beneath the Martian soil.

* **Configuration:**
  ```json
  {
    "subject": "A lone astronaut on Mars discovers a mysterious, humming artifact.",
    "narrative_elements": {
      "character": "Dr. Aris Thorne, a cautious but curious geologist",
      "setting": "A remote, desolate crater on Mars, near a deep-drilling site"
    },
    "target_word_count": 2500,
    "number_of_acts": 3,
    "scenes_per_act": 2,
    "writing_style": "thriller",
    "tone": "suspenseful"
  }
  ```

* **Expected Output Snippet:**
  ```markdown
  # Narrative Generation Summary: The Crimson Humming

  A complete narrative of **2580 words** across **6 scenes** was generated in **124.5s**.
  > The full text is available in the UI for detailed review.

  ## The Crimson Humming

  **Premise:** A lone astronaut on Mars, Dr. Aris Thorne, discovers a mysterious, humming artifact that challenges his scientific understanding and threatens his sanity.

  ---

  ### Act 1: The Discovery
  **Purpose:** Introduce Aris, his isolation, and the initial discovery of the strange object.
  #### Scene 1: The Anomaly
  - **Setting:** Dr. Thorne's rover, analyzing seismic data.
  - **Purpose:** Establish the routine and the first hint of something unusual.
  #### Scene 2: First Contact
  - **Setting:** The excavation site in the crater.
  - **Purpose:** Aris unearths the artifact and experiences its strange properties for the first time.
  ```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\NarrativeReasoningTask.kt

## Narrative Reasoning

#### **Overview**

* **One-Line Description:** Understand scenarios through narrative structures and storytelling.
* **Detailed Description:** This task analyzes complex situations by framing them as stories. It constructs coherent
  narratives from given elements, identifies key plot points and character arcs, analyzes motivations, predicts
  potential outcomes, and finds inconsistencies. This storytelling approach helps to understand the dynamics of a
  scenario, such as a user's journey, a project's evolution, or the impact of a strategic change.
* **Key Use Cases:**
    * Analyzing user journeys to understand customer experience.
    * Mapping the potential evolution of a system or product.
    * Planning and communicating change management initiatives.
    * Strategic foresight and scenario planning.
    * Analyzing competitive landscapes by framing them as strategic narratives.
    * Deconstructing project failures or successes to learn from them.

#### **Configuration Parameters**

| Parameter                | Type                | Description                                                                     | Default Value       |
|--------------------------|---------------------|---------------------------------------------------------------------------------|---------------------|
| `subject`                | `String?`           | The subject or scenario to analyze through narrative reasoning.                 | **Required**        |
| `narrative_elements`     | `Map<String, Any>?` | Narrative elements to consider (characters, setting, conflict, timeline, etc.). | `null`              |
| `construct_narrative`    | `Boolean`           | Whether to construct a coherent narrative from the elements.                    | `true`              |
| `identify_plot_points`   | `Boolean`           | Whether to identify key plot points and story arcs.                             | `true`              |
| `predict_outcomes`       | `Boolean`           | Whether to predict narrative outcomes and resolutions.                          | `true`              |
| `alternative_narratives` | `Int`               | Number of alternative narrative paths to explore.                               | `3`                 |
| `analyze_motivations`    | `Boolean`           | Whether to analyze character motivations and stakeholder perspectives.          | `true`              |
| `find_inconsistencies`   | `Boolean`           | Whether to identify narrative inconsistencies or gaps.                          | `true`              |
| `task_dependencies`      | `List<String>?`     | A list of task IDs that must be completed before this task can start.           | `null`              |
| `state`                  | `TaskState?`        | The initial state of the task.                                                  | `TaskState.Pending` |

#### **Process Flow**

1. **Initialization:** The task starts by setting up the user interface and displaying an "Overview" tab with the
   subject and configuration parameters.
2. **Narrative Construction:** If enabled, it constructs a primary, coherent narrative based on the provided `subject`
   and `narrative_elements`. This story is structured into acts, including a summary, themes, and tone.
3. **Plot Point Identification:** If enabled, the task analyzes the narrative to identify critical plot points, such as
   the inciting incident, rising action, climax, and resolution.
4. **Character Analysis:** If enabled, it performs a deep dive into the specified characters (or stakeholders),
   analyzing their roles, motivations, goals, conflicts, and character arcs.
5. **Outcome Prediction:** If enabled, the task generates a set of potential future outcomes based on the narrative,
   including the most likely, best-case, and worst-case scenarios.
6. **Inconsistency Check:** If enabled, it scrutinizes the narrative for logical contradictions, timeline gaps, or
   behaviors that conflict with character motivations, suggesting resolutions for any issues found.
7. **Synthesis:** Finally, the task generates a comprehensive synthesis of all preceding analyses, summarizing the core
   narrative, key insights, critical decision points, and recommended actions.

#### **Output Structure**

* **Final Result:** The final output is a concise Markdown-formatted string that summarizes the entire analysis. It
  includes the main narrative's title and summary, highlights of key plot points and character motivations, a list of
  predicted outcomes, a summary of any inconsistencies, and the final synthesis. This condensed report is designed to be
  passed to subsequent tasks.
* **UI Breakdown:** The task provides a detailed, multi-tab view in the user interface for in-depth exploration:
    * **Overview:** Displays the initial configuration, progress updates, and a final summary of the analysis duration.
    * **Context:** (If applicable) Shows context inherited from previous tasks.
    * **Main Narrative:** Presents the fully constructed story, broken down by acts, including themes and tone.
    * **Plot Points:** Details each identified plot point with its description, significance, timing, and affected
      characters.
    * **Characters:** Provides a detailed analysis for each character, covering their role, motivations, goals,
      conflicts, and arc.
    * **Predicted Outcomes:** Lists each potential outcome with its probability, key contributing factors, consequences,
      and resolution path.
    * **Inconsistencies:** Details any identified narrative gaps or contradictions, including their severity and
      suggested resolutions. If none are found, it confirms the narrative's coherence.
    * **Synthesis:** Contains the full text of the final synthesis, offering actionable insights and an overall
      assessment.

#### **Example Usage**

* **Scenario:** A product manager wants to analyze the potential impact of introducing a new, disruptive feature ("
  Project Phoenix") into their existing software. They want to understand how different user personas will react and
  what the likely long-term outcomes are.
* **Configuration:**
  ```json
  {
    "subject": "Introduction of 'Project Phoenix' feature",
    "narrative_elements": {
      "characters": ["Power User Paula", "New User Nick", "Lead Developer Dave"],
      "setting": "A mature B2B SaaS platform with a loyal user base",
      "conflict": "The new feature simplifies workflows for new users but requires existing power users to change their habits."
    },
    "alternative_narratives": 3,
    "analyze_motivations": true,
    "predict_outcomes": true
  }
  ```
* **Expected Output Snippet:**
  ```markdown
  # Narrative Reasoning Analysis: Introduction of 'Project Phoenix' feature

  ## Main Narrative: The Phoenix Rises
  The introduction of 'Project Phoenix' creates a schism in the user base. While New User Nick champions the intuitive design, Power User Paula struggles with the new workflows, leading to initial friction. Lead Developer Dave must balance supporting legacy users with pushing the new paradigm.

  ## Predicted Outcomes
  - **Successful Adoption (High):** After an initial dip, power users adapt and overall productivity increases.
  - **User Base Fork (Medium):** A significant portion of power users remain on a legacy version, creating a support burden.
  - **Feature Rollback (Low):** Negative feedback from high-value power users forces the company to retract the feature.

  ## Synthesis
  The core challenge is managing the transition for the existing power user base. The analysis suggests a phased rollout with dedicated training for power users is critical to mitigate risks and achieve the 'Successful Adoption' scenario. Key insights point to the need for better communication from Lead Developer Dave to bridge the gap between user expectations and technical implementation.
  ```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\ProbabilisticReasoningTask.kt

## ProbabilisticReasoning

### Overview

**One-Line Description:** Reason under uncertainty using Bayesian analysis.

**Detailed Description:** This task performs probabilistic reasoning and Bayesian analysis to make decisions under
uncertainty. It systematically updates beliefs by incorporating new evidence according to Bayes' theorem. The task can
calculate expected values, quantify risks, identify key information gaps, and suggest targeted experiments to reduce
uncertainty. It provides a structured framework for risk assessment, diagnostic reasoning, and data-driven
decision-making.

**Key Use Cases:**

* **Risk Assessment and Management:** Evaluating the likelihood and impact of potential risks.
* **Diagnostic Reasoning:** Pinpointing the most likely cause of a problem, such as a software bug or system failure.
* **A/B Test Analysis:** Determining which version of a product is statistically superior.
* **Resource Allocation:** Deciding how to allocate limited resources for maximum expected return.
* **Technology Adoption:** Assessing whether to invest in a new, unproven technology.

### Configuration Parameters

| Parameter                    | Type                  | Description                                                                                                            | Default Value                                          |
|:-----------------------------|:----------------------|:-----------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------|
| `hypotheses`                 | `Map<String, Double>` | A map of hypotheses to their prior probabilities. The probabilities must sum to 1.0.                                   | `null` (**Required**)                                  |
| `evidence`                   | `List<String>`        | A list of observed evidence or data points used to update beliefs.                                                     | `null`                                                 |
| `calculate_expected_value`   | `Boolean`             | If true, the task will calculate expected values and assess risks for the hypotheses.                                  | `true`                                                 |
| `identify_key_uncertainties` | `Boolean`             | If true, the task will identify the assumptions and estimates that have the most significant impact on the conclusion. | `true`                                                 |
| `suggest_experiments`        | `Boolean`             | If true, the task will suggest experiments or data collection methods to reduce key uncertainties.                     | `true`                                                 |
| `risk_tolerance`             | `String`              | The risk tolerance level to use for decision recommendations. Accepts "low", "medium", or "high".                      | `"medium"`                                             |
| `decision_context`           | `String`              | A description of the problem or decision being made to provide context for the analysis.                               | `null` (**Required**)                                  |
| `task_description`           | `String`              | A user-defined description of the task instance.                                                                       | Auto-generated based on hypothesis and evidence count. |
| `task_dependencies`          | `List<String>`        | A list of other task IDs that must be completed before this one can start.                                             | `null`                                                 |
| `state`                      | `TaskState`           | The initial execution state of the task.                                                                               | `Pending`                                              |

### Process Flow

1. **Initialization and Validation:** The task begins by validating the configuration. It checks that hypotheses have
   been provided and that their prior probabilities sum to approximately 1.0.
2. **Prior Probability Analysis:** It displays the initial belief distribution based on the provided prior probabilities
   before any evidence is considered.
3. **Bayesian Update:** The core of the task. It uses a specialized AI agent to evaluate each piece of evidence against
   each hypothesis. It calculates the likelihood of observing the evidence given each hypothesis and applies Bayes'
   theorem to compute the updated (posterior) probabilities.
4. **Expected Value Analysis (Optional):** If `calculate_expected_value` is enabled, the task estimates potential
   outcomes (costs and benefits) for each hypothesis and calculates the overall expected value. It also assesses
   downside risks and worst-case scenarios.
5. **Key Uncertainty Identification (Optional):** If `identify_key_uncertainties` is enabled, the task analyzes the
   model to find which assumptions or probability estimates have the largest impact on the final conclusions.
6. **Experiment Suggestion (Optional):** If `suggest_experiments` is enabled, the task recommends specific tests, A/B
   experiments, or data collection strategies that would most effectively reduce the key uncertainties identified in the
   previous step.
7. **Summary Generation:** The results from all enabled steps are compiled into a comprehensive final report.

### Output Structure

**Final Result:**
The final output is a concise Markdown-formatted string that summarizes the entire analysis. It includes the key
findings from the Bayesian update, expected value calculation, uncertainty analysis, and experiment suggestions. This
summary is designed to be passed as context to subsequent tasks.

**UI Breakdown:**
In the user interface, the task's output is broken down into several tabs for detailed inspection:

* **Overview:** A summary of the task's configuration, progress, and final results, including total execution time.
* **Context:** Displays any contextual information inherited from previous tasks.
* **Prior Probabilities:** A table showing the initial hypotheses and their assigned prior probabilities.
* **Bayesian Update:** The detailed analysis of how evidence impacted beliefs, showing the reasoning for likelihood
  assessments and the final posterior probabilities.
* **Expected Value:** (If enabled) A report on the expected value calculations, risk metrics, and decision
  recommendations based on the specified risk tolerance.
* **Key Uncertainties:** (If enabled) A ranked list of the most critical uncertainties affecting the analysis and an
  assessment of their impact.
* **Suggested Experiments:** (If enabled) A prioritized list of recommended experiments, including their rationale and
  expected information gain.

### Example Usage

**Scenario:**
A software company is deciding whether to refactor a legacy monolithic application into a microservices architecture.
The project is risky, but the potential payoff is high. They want to use probabilistic reasoning to guide their
decision.

**Configuration:**

```kotlin
val config = ProbabilisticReasoningTask.ProbabilisticReasoningTaskExecutionConfigData(
    decision_context = "Decide whether to refactor our legacy monolith to microservices.",
    hypotheses = mapOf(
        "Refactor leads to >20% improvement in developer velocity and system scalability" to 0.4,
        "Refactor has marginal impact (0-20% improvement)" to 0.5,
        "Refactor negatively impacts performance and increases operational complexity" to 0.1
    ),
    evidence = listOf(
        "A small-scale pilot refactor of one module showed a 15% performance gain.",
        "Two senior engineers have left the company in the last quarter, citing frustration with the monolith.",
        "A competitor who undertook a similar refactor reported a 30% increase in deployment frequency after 18 months."
    ),
    risk_tolerance = "medium"
)
```

**Expected Output Snippet:**

```markdown

## Probabilistic Reasoning Analysis

**Context:** Decide whether to refactor our legacy monolith to microservices.


### Bayesian Update

The evidence provided moderately increases the probability of a successful refactor. The competitor's success and engineer feedback were the most impactful pieces of evidence.

**Posterior Probabilities:**
- Refactor leads to >20% improvement: 55% (up from 40%)
- Refactor has marginal impact: 38% (down from 50%)
- Refactor has negative impact: 7% (down from 10%)


### Expected Value Analysis

Given the updated probabilities, the expected value of the refactor is positive, though with significant variance. The optimal decision under a 'medium' risk tolerance is to proceed with a phased refactor, starting with non-critical services.


### Key Uncertainties

The primary uncertainty is the actual cost and timeline of the full refactor, as the pilot was limited in scope. The impact on operational complexity is also a significant unknown.


### Suggested Experiments

Recommend a second, larger pilot on a more complex service to better estimate costs and performance implications before committing to a full-scale project.
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\SocraticDialogueTask.kt

## Socratic Dialogue

### Overview

**One-Line Description:** Explore ideas through Socratic questioning.

**Detailed Description:** This task uses the Socratic questioning methodology to deeply explore a concept, hypothesis,
or question. It simulates a dialogue between two AI agents: a 'Questioner' that probes for deeper meaning and a '
Responder' that provides thoughtful answers. The process is designed to challenge assumptions, explore implications,
identify contradictions, and ultimately lead to a more robust understanding of the topic. It is a powerful tool for
critical thinking and conceptual analysis.

**Key Use Cases:**

* Exploring the fundamental principles of a complex topic.
* Testing the validity and robustness of a hypothesis or argument.
* Uncovering hidden assumptions in a plan or strategy.
* Clarifying definitions and understanding the implications of a concept.
* Generating a structured, critical analysis of an idea for reports or research.

### Configuration Parameters

| Parameter               | Type            | Description                                                                                      | Default Value       |
|-------------------------|-----------------|--------------------------------------------------------------------------------------------------|---------------------|
| `initial_question`      | `String?`       | The initial question or hypothesis to explore.                                                   | **Required**        |
| `max_depth`             | `Int`           | Maximum dialogue depth (number of question-answer exchanges).                                    | `5`                 |
| `challenge_assumptions` | `Boolean`       | Whether to explicitly instruct the questioner agent to challenge assumptions at each level.      | `true`              |
| `domain_constraints`    | `List<String>?` | A list of topics or domains to constrain the dialogue, helping to keep the conversation focused. | `null` (any domain) |
| `task_dependencies`     | `List<String>?` | A list of task IDs that must be completed before this task can run.                              | `null`              |
| `state`                 | `TaskState?`    | The initial state of the task.                                                                   | `Pending`           |

### Process Flow

1. **Initialization:** The task starts by validating the configuration and initializing two distinct AI agents: a
   Socratic Questioner and a thoughtful Responder, each with specific instructions based on the task parameters.
2. **Dialogue Loop:** The task enters a loop that runs for the configured `max_depth`. In each iteration (or "
   exchange"):
   a. The Responder agent answers the current question.
   b. The Questioner agent analyzes the response and formulates a new, probing follow-up question designed to challenge
   assumptions, explore implications, or seek clarification.
3. **Synthesis Generation:** After all dialogue exchanges are complete, the entire conversation is provided to an agent
   to generate a final synthesis. This summary highlights key insights, challenged assumptions, identified
   contradictions, and potential areas for further exploration.
4. **Output Compilation:** The task compiles the final result, which includes a concise version of the dialogue and the
   full synthesis.

### Output Structure

**Final Result:** The final output passed to subsequent tasks is a Markdown-formatted string containing a concise
analysis. It includes the initial question, the first and last question-answer exchanges to provide context, and the
complete final synthesis. This summary is designed to be a self-contained, high-level overview of the dialogue's
findings.

**UI Breakdown:** In the user interface, the task provides a detailed, tabbed breakdown of the entire process for easy
navigation and review:

* **Overview:** Displays the initial configuration, real-time progress updates, and final summary statistics (e.g.,
  total time, exchanges completed).
* **Context:** (If applicable) Shows any contextual information passed from previous tasks that was used to inform the
  dialogue.
* **Exchange \[N]:** A separate tab is created for each question-answer exchange, showing the question asked, the full
  response generated, and the next question formulated.
* **Synthesis:** Contains the complete, detailed synthesis of the entire dialogue, including key insights and
  conclusions.

### Example Usage

**Scenario:** A product team wants to critically evaluate their new feature idea: "An AI-powered personal finance
advisor for young adults." They use the Socratic Dialogue task to uncover potential ethical issues and user assumptions
before development begins.

**Configuration:**

```json
{
  "initial_question": "Is an AI-powered personal finance advisor inherently beneficial for young adults?",
  "max_depth": 5,
  "challenge_assumptions": true,
  "domain_constraints": ["ethics", "finance", "user psychology", "AI safety"]
}
```

**Expected Output Snippet:**

```markdown

## Socratic Dialogue Analysis

**Question:** Is an AI-powered personal finance advisor inherently beneficial for young adults?


#### Exchange 1
**Q:** What does 'beneficial' mean in the context of personal finance for a young adult?
**A:** 'Beneficial' means helping them achieve financial goals like saving for a down payment, paying off debt, and investing, while also improving their financial literacy.

...


#### Exchange 5
**Q:** If the AI's advice, while mathematically optimal, leads to extreme frugality that negatively impacts a user's mental health, can it still be considered 'beneficial'?
**A:** No, it cannot. This reveals a critical tension. The definition of 'beneficial' must be expanded to include not just financial outcomes but also the user's overall well-being. The system must have safeguards to avoid promoting harmful financial habits.


### Key Insights

The dialogue revealed that the term 'beneficial' is more complex than initially assumed. A key assumption that 'optimal financial advice is always good' was challenged. The primary insight is that a successful AI advisor must balance quantitative financial optimization with qualitative user well-being, potentially by incorporating user-defined lifestyle constraints and mental health check-ins...
```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\SystemsThinkingTask.kt

## SystemsThinking

#### Overview

* **One-Line Description:** Analyze complex systems through feedback loops and dynamics.
* **Detailed Description:** This task performs a systems thinking analysis to understand complex system behavior. It
  deconstructs a system into its core components, identifies the feedback loops (both reinforcing and balancing) that
  govern its dynamics, and maps out delays and accumulations that can lead to unexpected outcomes. By identifying common
  system archetypes (e.g., "Limits to Growth," "Shifting the Burden"), it predicts emergent behavior and finds
  high-leverage points where small interventions can produce significant, lasting change. The task can also simulate
  potential interventions to forecast their impact over time, making it a powerful tool for strategic planning,
  optimization, and organizational change.
* **Key Use Cases:**
    * Understanding the root causes of persistent problems in a complex system.
    * Optimizing performance in areas like CI/CD pipelines, team workflows, or market dynamics.
    * Identifying and mitigating unintended consequences of proposed changes.
    * Analyzing organizational dynamics and communication flows.
    * Managing technical debt by understanding its feedback loops and long-term impact.

---

#### Configuration Parameters

The following table details the configuration parameters for the SystemsThinking task.

| Parameter                   | Type            | Description                                                         | Default Value |
|-----------------------------|-----------------|---------------------------------------------------------------------|---------------|
| `system_description`        | `String?`       | A detailed description of the system to be analyzed.                | **Required**  |
| `identify_feedback_loops`   | `Boolean`       | If true, the task will identify reinforcing and balancing loops.    | `true`        |
| `map_delays`                | `Boolean`       | If true, the task will map delays and accumulations in the system.  | `true`        |
| `find_leverage_points`      | `Boolean`       | If true, the task will find leverage points for intervention.       | `true`        |
| `simulate_interventions`    | `List<String>?` | A list of potential interventions to simulate and analyze.          | `null`        |
| `time_horizon`              | `String?`       | The time horizon for the analysis (e.g., "6 months", "1 year").     | `"6 months"`  |
| `identify_archetypes`       | `Boolean`       | If true, the task will identify common system archetypes.           | `true`        |
| `analyze_emergent_behavior` | `Boolean`       | If true, the task will analyze potential emergent behavior.         | `true`        |
| `related_files`             | `List<String>?` | A list of file paths or glob patterns for additional context.       | `null`        |
| `task_dependencies`         | `List<String>?` | A list of task IDs that must be completed before this task can run. | `null`        |

---

#### Process Flow

The task executes the following steps to perform its analysis:

1. **Context Gathering:** The task begins by gathering all available context. This includes results from any
   prerequisite tasks and the content of files specified in the `related_files` parameter.
2. **System Structure Analysis:** It performs an initial analysis to identify the system's key components, variables,
   relationships, accumulations (stocks), and information flows.
3. **Feedback Loop Identification:** If enabled, it identifies and classifies reinforcing (virtuous/vicious cycles) and
   balancing (stabilizing) feedback loops. It describes the causal chain for each loop and generates a Mermaid diagram
   to visualize the system's structure.
4. **Delay & Accumulation Mapping:** If enabled, it analyzes time lags between cause and effect (e.g., information,
   physical, or decision delays) and identifies what accumulates over time (e.g., technical debt, customer trust).
5. **System Archetype Identification:** If enabled, it identifies common, recurring patterns of behavior (archetypes)
   like "Limits to Growth" or "Shifting the Burden" to explain the system's dynamics.
6. **Emergent Behavior Analysis:** If enabled, it analyzes system-level behaviors that arise from component
   interactions, predicts potential unintended consequences, and identifies possible tipping points.
7. **Leverage Point Identification:** If enabled, it identifies the most effective places to intervene in the system,
   ranked according to Donella Meadows' hierarchy of leverage points.
8. **Intervention Simulation:** If a list of `simulate_interventions` is provided, the task runs a thought experiment
   for each one, analyzing its immediate, short-term, and long-term effects, its impact on feedback loops, and potential
   side effects.
9. **Synthesis & Recommendations:** Finally, the task synthesizes all findings into a comprehensive report that
   summarizes key insights, critical feedback loops, and provides an actionable, prioritized roadmap for intervention.

---

#### Output Structure

* **Final Result:** The final output passed to subsequent tasks is a concise Markdown-formatted string. It includes the
  system description, the analysis time horizon, and a summary of the key findings and recommendations, truncated to a
  maximum length. This provides a high-level overview of the analysis results.

* **UI Breakdown:** In the user interface, the task provides a detailed, multi-tabbed view of the entire analysis
  process:
    * **Overview:** Displays the initial configuration, live status updates, and a final summary of the analysis run,
      including total time and components analyzed.
    * **Context:** Shows the contextual data gathered from prior tasks and related files.
    * **System Structure:** Presents the detailed breakdown of the system's components and relationships.
    * **Feedback Loops:** Contains the analysis of reinforcing and balancing loops, including the generated Mermaid
      diagram.
    * **Delays & Accumulations:** Details the identified time lags and their impact on system behavior.
    * **System Archetypes:** Lists and explains the common system patterns found within the system.
    * **Emergent Behavior:** Describes the predicted system-level behaviors and potential unintended consequences.
    * **Leverage Points:** Presents the identified high-impact intervention points, ranked by effectiveness.
    * **Intervention Simulation:** If configured, this tab shows the detailed simulation results for each proposed
      intervention scenario.
    * **Synthesis:** Contains the final, comprehensive synthesis, key insights, and actionable recommendations.

---

#### Example Usage

* **Scenario:** A software development team is struggling with a CI/CD pipeline that has become increasingly slow and
  unreliable. They want to understand the underlying dynamics and find effective ways to improve it without introducing
  new problems.

* **Configuration:**
  ```json
  {
    "system_description": "Our team's CI/CD pipeline for the main web application. It involves stages for code checkout, dependency installation, unit testing, integration testing, container building, and deployment to a staging environment. Developers complain about long wait times for feedback, and flaky tests frequently cause the entire pipeline to fail, blocking deployments.",
    "time_horizon": "3 months",
    "find_leverage_points": true,
    "identify_archetypes": true,
    "simulate_interventions": [
      "Add more parallel runners to the testing stage.",
      "Implement a 'quarantine' system for flaky tests.",
      "Invest in optimizing the dependency installation process."
    ]
  }
  ```

* **Expected Output Snippet:**
  ```markdown
  # Systems Thinking Analysis: Our team's CI/CD pipeline...

  **Time Horizon:** 3 months

  ## Key Findings

  The analysis reveals a classic "Shifting the Burden" archetype where developers, frustrated by long test runs (the fundamental problem), resort to temporarily disabling or ignoring flaky tests (the symptomatic solution). This erodes test quality, leading to more bugs in staging, which in turn requires more hotfixes and disrupts planned work, reinforcing the pressure to take shortcuts.

  The highest-impact leverage point is improving the feedback loop for test failures. The most promising intervention is quarantining flaky tests, as it immediately unblocks the pipeline while creating a clear backlog for addressing test instability, thus tackling the root cause without stopping development. Adding more runners provides only temporary relief and will be undermined by the eroding test quality.
  ... (see full analysis in task output)

  ---

  **Analysis Components:** Feedback Loops, Delays, Leverage Points, Archetypes, Emergent Behavior, Intervention Simulation (3)
  ```

# webui\src\main\kotlin\com\simiacryptus\cognotik\plan\tools\reasoning\TemporalReasoningTask.kt

## TemporalReasoning

### Overview

**One-Line Description:** Analyze how systems evolve over time and predict future states.

**Detailed Description:** This task performs a comprehensive temporal analysis to understand the evolution of a system,
project, or situation. It constructs a chronological timeline of significant events by processing provided data sources
like logs, metrics, or commit histories. The task can identify recurring patterns, analyze the rate of change, pinpoint
critical transition points, and extrapolate trends to predict future states. It is designed to uncover insights from
time-series data, helping to diagnose issues like performance degradation, understand architectural drift, or track
feature adoption.

**Key Use Cases:**

* Analyzing the accumulation of technical debt over a project's lifecycle.
* Tracking system evolution and architectural drift.
* Investigating performance degradation over time by correlating events.
* Creating a timeline of bug introductions and resolutions.
* Understanding feature adoption and usage patterns after release.

### Configuration Parameters

The following table details the configuration parameters for the Temporal Reasoning task.

| Parameter                | Type            | Description                                                                           | Default Value |
|--------------------------|-----------------|---------------------------------------------------------------------------------------|---------------|
| `subject`                | `String?`       | The subject or system to analyze over time.                                           | **Required**  |
| `time_range`             | `String?`       | The time range to analyze (e.g., '2023-01-01 to 2024-01-01').                         | **Required**  |
| `granularity`            | `String`        | The granularity of analysis: daily, weekly, monthly, quarterly, yearly.               | `"weekly"`    |
| `identify_patterns`      | `Boolean`       | Whether to identify temporal patterns and cycles.                                     | `true`        |
| `predict_future`         | `Boolean`       | Whether to predict future states based on trends.                                     | `true`        |
| `prediction_horizon`     | `String?`       | How far into the future to predict (e.g., '3 months', '6 weeks').                     | `"3 months"`  |
| `critical_events`        | `List<String>?` | A list of critical event types to specifically highlight in the timeline.             | `null`        |
| `related_files`          | `List<String>?` | A list of file paths or glob patterns containing temporal data (logs, metrics, etc.). | `null`        |
| `analyze_rate_of_change` | `Boolean`       | Whether to analyze the rate of change and acceleration.                               | `true`        |
| `identify_transitions`   | `Boolean`       | Whether to identify critical transition points in the timeline.                       | `true`        |
| `task_description`       | `String?`       | A custom description for this specific task instance.                                 | `null`        |
| `task_dependencies`      | `List<String>?` | A list of task IDs that must be completed before this task can run.                   | `null`        |

### Process Flow

1. **Data Gathering:** The task begins by searching for and reading the content of files matching the glob patterns
   specified in `related_files`. This content serves as the raw temporal data for the analysis.
2. **Timeline Construction:** The core of the task involves sending the subject, time range, and gathered data to an AI
   agent. The agent analyzes this information to construct a detailed, chronological timeline of significant events.
3. **Pattern Identification:** If `identify_patterns` is enabled, the task analyzes the constructed timeline to identify
   recurring patterns, cycles, and periodic trends.
4. **Rate of Change Analysis:** If `analyze_rate_of_change` is enabled, the task examines the timeline to determine the
   velocity of change, identifying periods of stability versus rapid evolution.
5. **Transition Point Identification:** If `identify_transitions` is enabled, the task pinpoints critical inflection
   points or phase transitions where the system's behavior or state changed significantly.
6. **Future Prediction:** If `predict_future` is enabled, the task extrapolates from historical trends and patterns to
   generate predictions about the system's future state within the specified `prediction_horizon`.
7. **Visualization Generation:** A Mermaid timeline diagram is generated to provide a clear visual representation of the
   key events and periods identified during the analysis.
8. **Summary Generation:** The task compiles all findings into a final, comprehensive summary.

### Output Structure

**Final Result:**
The final output passed to subsequent tasks is a concise Markdown-formatted summary of the analysis. It includes the
subject, time range, and key statistics such as the number of events analyzed, patterns identified, and critical
transitions found. This summary provides a high-level overview of how the subject evolved during the specified period.

**UI Breakdown:**
The task's detailed results are presented in a multi-tabbed view in the user interface:

* **Overview:** Displays the initial configuration and the final summary of the analysis upon completion.
* **Temporal Data:** Shows the raw, aggregated content gathered from the `related_files`.
* **Timeline:** Presents the detailed, chronological list of all events identified by the AI, including their timestamp,
  type, description, and significance.
* **Patterns:** (Conditional) If enabled, this tab details the recurring patterns, their frequency, and confidence
  levels.
* **Rate of Change:** (Conditional) If enabled, this tab contains the textual analysis of the system's rate of change
  over time.
* **Transition Points:** (Conditional) If enabled, this tab lists and describes the critical transition points that were
  identified.
* **Future Predictions:** (Conditional) If enabled, this tab outlines the predictions for the system's future state.
* **Visualization:** Renders the generated Mermaid timeline diagram for a visual overview of the analysis.

### Example Usage

**Scenario:**
A project manager wants to understand how the complexity of their main application's codebase has evolved over the last
year. They suspect that a major library upgrade six months ago led to a spike in bugs and want to visualize the timeline
of events.

**Configuration:**

```json
{
  "subject": "Codebase complexity and bug introduction for 'WebApp-Main'",
  "time_range": "2023-05-01 to 2024-05-01",
  "granularity": "monthly",
  "related_files": [
    "project/logs/git_log.txt",
    "project/metrics/complexity_scores.csv"
  ],
  "critical_events": [
    "Major library upgrade",
    "v2.0 Release",
    "v2.1 Release"
  ],
  "predict_future": true,
  "prediction_horizon": "6 months"
}
```

**Expected Output Snippet:**

```markdown
**Subject:** Codebase complexity and bug introduction for 'WebApp-Main'
**Time Range:** 2023-05-01 to 2024-05-01
**Events Analyzed:** 42
**Patterns Identified:** 3
**Critical Transitions:** 1

The temporal analysis reveals how WebApp-Main evolved over the last year, identifying key events, patterns, and trends that shaped its development. A significant transition point was identified around the 'Major library upgrade', correlating with a subsequent increase in bug reports.
```