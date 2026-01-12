# Social

## LLMExperiment

Conduct controlled experiments on LLM behavior

              Conducts rigorous experiments to characterize LLM behaviors and biases.
              <ul>
                <li>Experimentally-controlled prompts with variable substitution</li>
                <li>Multiple temperature settings for comparison</li>
                <li>Configurable repetitions for statistical validity</li>
                <li>Custom metrics tracking (length, sentiment, patterns)</li>
                <li>Statistical analysis including t-tests and variance</li>
                <li>Response diversity and consistency measurement</li>
<li>Automated insight generation from results</li>
                <li>Comprehensive experiment reports with visualizations</li>
                <li>Concurrent execution for faster experiment completion</li>
              </ul>
              <p><strong>Use cases:</strong> Bias studies, cognitive studies, logical performance analysis, consistency testing</p>

#### Planner Prompt Segment

```text
LLMExperiment - Conduct controlled experiments on LLM behavior
 ** Specify one or more prompt templates with variables for substitution
 ** Define experimental conditions (temperature(s), prompt variations)
 ** Configure number of repetitions for statistical validity
 ** Rate custom attributes in responses
 ** Analyze statistical significance of results
```

#### Default Execution Configuration

```json
{
  "task_type" : "LLMExperiment",
  "prompt_templates" : null,
  "prompt_variables" : null,
  "metrics" : [ "response_length", "response_time" ],
  "temperature_values" : [ 0.1, 0.7 ],
  "repetitions" : 3,
  "statistical_analysis" : true,
  "significance_level" : 0.05,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "LLMExperiment",
  "task_description" : "Conduct LLM experiment with 3 repetitions"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "LLMExperiment",
  "name" : "LLMExperiment",
  "model" : null
}
```

---

## LLMPollSimulation

Simulate polls and surveys with AI personas

Simulates polls and surveys using LLMs to model diverse respondent personas.
<ul>
  <li>Define survey questions with multiple types (choice, Likert, open-ended)</li>
  <li>Create respondent profiles with demographics and characteristics</li>
  <li>Generate realistic survey responses from simulated personas</li>
  <li>Analyze results with descriptive statistics and frequency distributions</li>
  <li>Cross-tabulation analysis by demographic dimensions</li>
  <li>Sentiment analysis for open-ended responses</li>
  <li>Bias detection (central tendency, primacy/recency effects)</li>
  <li>Automated insights and recommendations</li>
  <li>Comprehensive reports with visualizations</li>
</ul>
<p><strong>Use cases:</strong> Survey instrument testing, response pattern exploration, demographic analysis, bias detection</p>

#### Planner Prompt Segment

```text
LLMPollSimulation - Simulate polls and surveys with diverse AI personas
  ** Define survey questions with various types (multiple choice, Likert, open-ended)
  ** Create respondent profiles with demographics and characteristics
  ** Generate realistic survey responses from simulated personas
  ** Analyze results with cross-tabulations and statistical summaries
  ** Detect response patterns, biases, and sentiment
  ** Test survey instruments before real-world deployment
```

#### Default Execution Configuration

```json
{
  "task_type" : "LLMPollSimulation",
  "questions" : null,
  "respondent_profiles" : null,
  "respondents_per_profile" : 10,
  "include_demographics" : true,
  "demographic_dimensions" : [ "age", "gender", "location", "education" ],
  "cross_tabulation" : true,
  "sentiment_analysis" : true,
  "bias_detection" : true,
  "temperature" : 0.7,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "LLMPollSimulation",
  "task_description" : "Simulate poll with 0 profiles"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "LLMPollSimulation",
  "name" : "LLMPollSimulation",
  "model" : null
}
```

---

## MultiPerspectiveAnalysis

Analyze problems from multiple viewpoints with synthesis

Analyzes topics from multiple perspectives and synthesizes findings.
<ul>
  <li>Examines subject from specified viewpoints</li>
  <li>Generates detailed analysis for each perspective</li>
  <li>Identifies agreements and conflicts</li>
  <li>Synthesizes perspectives into unified conclusion</li>
  <li>Configurable consensus threshold</li>
  <li>Useful for architectural decisions and code reviews</li>
  <li>Supports context from related files</li>
</ul>

#### Planner Prompt Segment

```text
MultiPerspectiveAnalysis - Analyze problems from multiple viewpoints with synthesis
 ** Specify the subject to analyze in analysis_subject
 ** Provide a list of perspectives to consider (e.g., technical, business, ethical, user experience)
 ** Optionally, list input files (supports glob patterns) to provide context for the analysis
 ** Set synthesize=true to generate a unified conclusion from all perspectives
 ** Configure consensus_threshold (0.0-1.0) to determine minimum agreement level
 ** Additional context files can be specified via input_files
 ** Each perspective will be analyzed independently, then synthesized
 ** Useful for:
    - Architectural decision making
    - Code review from multiple angles
    - Strategic planning
    - Risk assessment
    - Feature evaluation
```

#### Default Execution Configuration

```json
{
  "task_type" : "MultiPerspectiveAnalysis",
  "analysis_subject" : null,
  "perspectives" : null,
  "input_files" : null,
  "synthesize" : true,
  "consensus_threshold" : 0.7,
  "related_files" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "MultiPerspectiveAnalysis",
  "task_description" : "Analyze 'null' from perspectives: null"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "MultiPerspectiveAnalysis",
  "name" : "MultiPerspectiveAnalysis",
  "model" : null
}
```

---

## PoliticalOptimization

Optimize text using multi-perspective political consensus analysis

Evaluates and optimizes text from multiple political perspectives using consensus-based fitness.
<ul>
  <li>Evaluates text from configurable political perspectives (left, center, right, libertarian, etc.)</li>
  <li>Measures agreement/disagreement across perspectives</li>
  <li>Calculates consensus fitness (positive = unifying, negative = divisive)</li>
  <li>Identifies wedge issues and points of contention</li>
  <li>Generates variants that maximize consensus or highlight divisions</li>
  <li>Provides detailed perspective-by-perspective analysis</li>
  <li>Tracks evolution of consensus across generations</li>
  <li>Useful for crafting bipartisan messaging, identifying divisive topics, or understanding political framing</li>
</ul>

#### Planner Prompt Segment

```text
PoliticalOptimization - Optimize text using multi-perspective political consensus analysis
  ** Specify the initial text to analyze/optimize
  ** Define political perspectives to evaluate from (progressive, conservative, libertarian, centrist, etc.)
  ** Set optimization goal (maximize consensus, minimize divisiveness, or explore both)
  ** Configure evaluation criteria (clarity, persuasiveness, factual accuracy, emotional appeal, etc.)
  ** Choose consensus mode:
     - maximize: Find text that unifies across perspectives
     - minimize: Identify wedge issues and divisive framing
     - explore: Generate both unifying and divisive variants
  ** The task will:
     - Evaluate text from each political perspective independently
     - Calculate consensus score (positive = unifying, negative = divisive)
     - Identify common ground and points of contention
     - Generate variants optimized for consensus or division
     - Track evolution of agreement/disagreement
     - Provide perspective-by-perspective analysis
  ** Useful for:
     - Crafting bipartisan messaging
     - Understanding political framing effects
     - Identifying divisive topics and language
     - Testing message reception across political spectrum
     - Finding common ground in contentious debates
```

#### Default Execution Configuration

```json
{
  "task_type" : "PoliticalOptimization",
  "initial_text" : null,
  "optimization_goal" : null,
  "perspectives" : [ "progressive", "conservative", "libertarian", "centrist" ],
  "evaluation_criteria" : [ "clarity", "persuasiveness", "factual_accuracy", "emotional_appeal" ],
  "consensus_mode" : "explore",
  "num_generations" : 5,
  "population_size" : 8,
  "selection_size" : 3,
  "mutation_strategies" : [ "rephrase", "emphasize", "soften", "reframe" ],
  "enable_crossover" : true,
  "consensus_weight" : 0.6,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "PoliticalOptimization"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "PoliticalOptimization",
  "name" : "PoliticalOptimization",
  "model" : null
}
```

---

