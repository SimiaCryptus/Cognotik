# Plain Language Reasoning Prompts for General Audiences

## 1. AbstractionLadder

### Prompt 1: Social Media Addiction

"I want to understand social media addiction by starting with the concrete behavior of checking Instagram every 5 minutes. Take me up the abstraction ladder to
understand the broader patterns, then back down to see other specific examples of the same underlying issue."

**Configuration hints:**

- concrete_concept: "Checking Instagram every 5 minutes"
- direction: "both"
- levels: 4
- identify_patterns: true

### Prompt 2: Climate Action

"Start with the specific action of someone choosing to bike to work instead of driving. I want to go up the abstraction ladder to understand what this
represents at higher levels of thinking, and then come back down to see what other concrete actions fit the same pattern."

**Configuration hints:**

- concrete_concept: "Choosing to bike to work instead of driving"
- direction: "both"
- levels: 3
- identify_patterns: true

### Prompt 3: Political Polarization

"Begin with the concrete example of someone unfriending a family member on Facebook over political disagreements. Take me up to understand the broader social
patterns this represents, then show me other specific manifestations of the same phenomenon."

**Configuration hints:**

- concrete_concept: "Unfriending family members over political disagreements on social media"
- direction: "both"
- levels: 4
- identify_patterns: true

---

## 2. AnalogicalReasoning

### Prompt 1: Education Reform

"Use the way Netflix recommends shows based on viewing history as an analogy to help me think about how we could personalize education for students. Find me 3
different analogies from the entertainment industry that could inspire new approaches to teaching."

**Configuration hints:**

- source_domain: "Netflix recommendation algorithms and personalized entertainment"
- target_problem: "Creating personalized education systems that adapt to individual student needs"
- num_analogies: 3
- validate_mappings: true

### Prompt 2: Healthcare Access

"Think about how Uber solved the taxi problem - making rides available on-demand with transparent pricing. Use this and similar analogies to help me explore
solutions for making healthcare more accessible and affordable. Give me 3 different analogies from the transportation or delivery industries."

**Configuration hints:**

- source_domain: "Uber and on-demand transportation/delivery services"
- target_problem: "Making healthcare more accessible, affordable, and convenient for everyone"
- num_analogies: 3
- validate_mappings: true

### Prompt 3: Democracy and Voting

"Use how Wikipedia is created and maintained by volunteers as an analogy for thinking about citizen participation in democracy. Find me 3 analogies from
collaborative online platforms that could inspire new ways for citizens to participate in governance."

**Configuration hints:**

- source_domain: "Wikipedia and collaborative online platforms"
- target_problem: "Increasing meaningful citizen participation in democratic governance"
- num_analogies: 3
- validate_mappings: true

---

## 3. CausalInference

### Prompt 1: Rising Mental Health Issues

"Young adult depression and anxiety rates have doubled in the last decade. Help me figure out what's actually causing this. Consider these potential causes:
social media use, economic uncertainty, academic pressure, reduced in-person socialization, climate anxiety, and pandemic effects. Build a causal graph and
identify which factors are truly driving the increase versus which are just correlated."

**Configuration hints:**

- observed_effect: "Depression and anxiety rates in young adults doubled in the last decade"
-
potential_causes: ["Social media use", "Economic uncertainty and job market stress", "Academic pressure and student debt", "Reduced face-to-face socialization", "Climate change anxiety", "COVID-19 pandemic effects"]
- build_causal_graph: true
- identify_confounders: true

### Prompt 2: Declining Birth Rates

"Birth rates are falling dramatically in developed countries. What's really causing this? Consider: women's education and career opportunities, cost of
childcare and housing, changing cultural values, economic instability, environmental concerns, and access to contraception. I need to understand which are
actual causes versus which are just things that happen to correlate."

**Configuration hints:**

- observed_effect: "Birth rates declining significantly in developed nations"
-
potential_causes: ["Women's increased education and career opportunities", "High cost of childcare and housing", "Shifting cultural values about parenthood", "Economic instability and uncertainty", "Environmental concerns about overpopulation", "Widespread access to contraception"]
- build_causal_graph: true
- identify_confounders: true

### Prompt 3: Political Polarization

"Political polarization in America has increased dramatically. What's actually causing people to become more extreme and less willing to compromise? Consider:
social media echo chambers, cable news, economic inequality, geographic sorting, loss of local news, and partisan gerrymandering. Help me distinguish true
causes from things that are just symptoms or correlations."

**Configuration hints:**

- observed_effect: "Dramatic increase in political polarization and unwillingness to compromise"
-
potential_causes: ["Social media echo chambers and algorithmic filtering", "Partisan cable news networks", "Growing economic inequality", "Geographic sorting (liberals and conservatives living in separate areas)", "Decline of local journalism", "Partisan gerrymandering"]
- build_causal_graph: true
- identify_confounders: true

---

## 4. ChainOfThought

### Prompt 1: Universal Basic Income

"Walk me through the reasoning step-by-step: Would implementing a Universal Basic Income of $1,000/month for all adults improve society overall? Consider
economic effects, work incentives, poverty reduction, inflation, funding mechanisms, and social impacts. Validate each step of reasoning before moving to the
next."

**Configuration hints:**

- problem_statement: "Would implementing Universal Basic Income of $1,000/month for all adults improve society overall? Consider economic effects, work
  incentives, poverty reduction, inflation, funding mechanisms, and social impacts."
- reasoning_depth: null
- validate_steps: true

### Prompt 2: Artificial Intelligence Regulation

"Think through this step-by-step: Should governments heavily regulate AI development now, or wait until we better understand the technology? Consider innovation
speed, safety risks, competitive dynamics between nations, unintended consequences of regulation, and the difficulty of regulating something we don't fully
understand yet. Validate your reasoning at each step."

**Configuration hints:**

- problem_statement: "Should governments implement heavy regulation of AI development now, or wait until we better understand the technology? Consider
  innovation vs. safety, international competition, unintended consequences, and the challenge of regulating emerging technology."
- reasoning_depth: null
- validate_steps: true

### Prompt 3: College Education Value

"Reason through this carefully: Is a traditional 4-year college degree still worth the cost for most people? Consider: student debt levels ($30k-100k+),
opportunity cost of 4 years, changing job market, alternative education paths, signaling value of degrees, and lifetime earnings differences. Validate each
reasoning step."

**Configuration hints:**

- problem_statement: "Is a traditional 4-year college degree still worth the cost for most people? Consider student debt ($30k-100k+), opportunity cost,
  changing job market, alternative education, signaling value, and lifetime earnings."
- reasoning_depth: null
- validate_steps: true

---

## 5. ConstraintSatisfaction

### Prompt 1: Career Change Decision

"Help me decide on a career change. I must: keep income above $60k/year, stay in my current city, work no more than 45 hours/week, and start within 6 months.
I'd prefer to: maximize work-life balance (weight: 0.9), do meaningful work (weight: 0.85), have growth potential (weight: 0.8), and use my existing skills (
weight: 0.7). Find the best career path that satisfies these constraints."

**Configuration hints:**

- problem_description: "Choosing a new career path"
- hard_constraints: ["Minimum income $60,000/year", "Must stay in current city", "Maximum 45 hours/week", "Can start within 6 months"]
- soft_constraints: {"Maximize work-life balance": 0.9, "Meaningful/purposeful work": 0.85, "Strong growth potential": 0.8, "Leverage existing skills": 0.7}
- search_strategy: "backtracking"

### Prompt 2: Retirement Location

"I'm choosing where to retire. Must have: affordable cost of living (under $3k/month), good healthcare access, safe neighborhood, and mild climate (no harsh
winters). I'd prefer to: be near family (weight: 0.9), have cultural activities (weight: 0.7), be in a walkable area (weight: 0.8), and have an active senior
community (weight: 0.75). Find the best location."

**Configuration hints:**

- problem_description: "Selecting retirement location"
- hard_constraints: ["Cost of living under $3,000/month", "Access to quality healthcare", "Low crime rate", "Mild climate without harsh winters"]
- soft_constraints: {"Proximity to family": 0.9, "Cultural activities and amenities": 0.7, "Walkable neighborhood": 0.8, "Active senior community": 0.75}
- search_strategy: "backtracking"

### Prompt 3: Family Vacation Planning

"Plan our family vacation. Must: fit $4,000 budget, accommodate 2 adults and 3 kids (ages 5-12), be reachable in one day of travel, and happen in July. We'd
prefer to: maximize kid-friendly activities (weight: 0.9), have educational value (weight: 0.7), include outdoor activities (weight: 0.8), and have some adult
relaxation time (weight: 0.75). Find the optimal destination and plan."

**Configuration hints:**

- problem_description: "Planning family vacation"
- hard_constraints: ["Total budget $4,000", "Suitable for ages 5-12", "Reachable in one day of travel", "Available in July"]
- soft_constraints: {"Kid-friendly activities": 0.9, "Educational value": 0.7, "Outdoor activities": 0.8, "Adult relaxation opportunities": 0.75}
- search_strategy: "forward"

---

## 6. CounterfactualAnalysis

### Prompt 1: Social Media Impact

"We've had widespread social media for 15 years now, and we see increased anxiety, polarization, and attention problems. Analyze what would have happened if: 1)
Social media never became popular, 2) Social media remained chronological without algorithms, 3) Social media was age-restricted to 16+, 4) Social media
companies were held liable for content. Keep constant: same technology level, same internet access, same smartphone adoption."

**Configuration hints:**

- actual_scenario: "15 years of widespread social media with algorithmic feeds, available to all ages, with platform liability protections. Observable effects:
  increased anxiety, political polarization, and attention problems."
-
counterfactuals: ["Social media never became popular", "Social media remained chronological without algorithmic curation", "Social media was age-restricted to 16+", "Social media companies held liable for harmful content"]
- compare_outcomes: true
- control_factors: ["Same technology level", "Same internet access", "Same smartphone adoption", "Same time period"]

### Prompt 2: College Debt Crisis

"We have $1.7 trillion in student loan debt affecting 45 million Americans. What would have happened if: 1) College remained affordable like in the 1970s, 2) We
had free community college for everyone, 3) Income-share agreements replaced loans, 4) Trade schools were promoted equally to universities. Keep constant: same
number of people seeking higher education, same job market, same technology changes."

**Configuration hints:**

- actual_scenario: "$1.7 trillion in student debt affecting 45 million Americans, with college costs rising 8x faster than wages since 1980."
-
counterfactuals: ["College costs remained at 1970s levels relative to income", "Free community college for all students", "Income-share agreements instead of traditional loans", "Trade schools promoted equally to universities"]
- compare_outcomes: true
- control_factors: ["Same number seeking higher education", "Same job market evolution", "Same technological changes"]

### Prompt 3: Remote Work Revolution

"COVID-19 forced a massive shift to remote work. Now many companies are mandating return to office. What would have happened if: 1) Remote work never became
widespread, 2) Companies embraced permanent remote-first policies, 3) We adopted hybrid 2-3 days in office, 4) Different industries made different choices. Keep
constant: same technology capabilities, same housing costs, same family situations."

**Configuration hints:**

- actual_scenario: "COVID-19 forced remote work experiment. Now many companies mandating return to office, creating tension and turnover."
-
counterfactuals: ["Remote work never became widespread (no pandemic)", "Companies embraced permanent remote-first policies", "Industry standard became hybrid 2-3 days in office", "Different industries made different choices based on work nature"]
- compare_outcomes: true
- control_factors: ["Same technology capabilities", "Same housing costs", "Same family situations", "Same worker preferences"]

---

## 7. DecompositionSynthesis

### Prompt 1: Solving Homelessness

"Break down the complex problem of solving homelessness in a major city. Consider all the interconnected issues: mental health, addiction, affordable housing
shortage, job access, healthcare, criminal records, family breakdown, and systemic poverty. Decompose this into manageable subproblems, solve each one, then
synthesize a comprehensive solution. Use functional decomposition."

**Configuration hints:**

- complex_problem: "Solving homelessness in a major city, addressing mental health, addiction, affordable housing, employment, healthcare access, criminal
  records, family breakdown, and systemic poverty"
- decomposition_strategy: "functional"
- max_depth: 3
- synthesize_solution: true
- validate_coherence: true

### Prompt 2: Reducing Carbon Emissions

"Break down the massive challenge of reducing global carbon emissions by 50% in 10 years. This involves transportation, energy production, agriculture,
manufacturing, buildings, and changing consumer behavior across billions of people. Decompose this into solvable pieces, address each one, then synthesize a
coherent global strategy. Use hierarchical decomposition."

**Configuration hints:**

- complex_problem: "Reducing global carbon emissions by 50% within 10 years, addressing transportation, energy production, agriculture, manufacturing,
  buildings, and consumer behavior worldwide"
- decomposition_strategy: "hierarchical"
- max_depth: 4
- synthesize_solution: true
- validate_coherence: true

### Prompt 3: Reforming Education System

"Break down the challenge of reforming the K-12 education system to prepare students for the 21st century. This involves curriculum design, teacher training,
technology integration, assessment methods, equity issues, funding, parental involvement, and adapting to AI. Decompose into manageable parts, solve each, then
synthesize a complete reform plan. Use temporal decomposition."

**Configuration hints:**

- complex_problem: "Reforming K-12 education for the 21st century: curriculum, teacher training, technology, assessment, equity, funding, parental involvement,
  and AI adaptation"
- decomposition_strategy: "temporal"
- max_depth: 3
- synthesize_solution: true
- validate_coherence: true

---

## 8. MetaCognitiveReflection

### Prompt 1: Critique Climate Change Skepticism

"Reflect on the reasoning behind climate change skepticism. Examine the assumptions, identify cognitive biases, explore what alternative evidence might exist,
assess the confidence levels, check for logical fallacies, and identify gaps in the reasoning. Suggest improvements to the thinking process."

**Configuration hints:**

- subject_task_id: "climate_skepticism_reasoning"
- reflection_aspects: ["assumptions", "biases", "alternatives", "confidence", "logic", "completeness"]
- suggest_improvements: true
- identify_gaps: true
- evaluate_confidence: true

### Prompt 2: Critique Meritocracy Belief

"Reflect on the belief that 'America is a meritocracy where anyone can succeed through hard work.' Examine underlying assumptions, identify biases in this
thinking, explore alternative perspectives, assess how confident we should be in this claim, check the logic, and identify what's missing from this analysis."

**Configuration hints:**

- subject_task_id: "meritocracy_belief_analysis"
- reflection_aspects: ["assumptions", "biases", "alternatives", "confidence", "logic", "completeness"]
- suggest_improvements: true
- identify_gaps: true
- evaluate_confidence: true

### Prompt 3: Critique Free Speech Absolutism

"Reflect on the position that 'free speech should be absolute with no restrictions.' Examine the assumptions behind this view, identify cognitive biases,
explore alternative frameworks, assess confidence levels, check for logical consistency, and identify gaps in the reasoning."

**Configuration hints:**

- subject_task_id: "free_speech_absolutism_position"
- reflection_aspects: ["assumptions", "biases", "alternatives", "confidence", "logic", "completeness"]
- suggest_improvements: true
- identify_gaps: true
- evaluate_confidence: true

---

## 9. MultiPerspectiveAnalysis

### Prompt 1: Legalizing Marijuana

"Analyze marijuana legalization from multiple perspectives: public health, criminal justice, personal freedom, economic impact, social equity, and youth
protection. Synthesize these viewpoints into a unified recommendation. Use a consensus threshold of 0.7."

**Configuration hints:**

- analysis_subject: "Legalizing marijuana nationwide"
-
perspectives: ["public health and addiction", "criminal justice and incarceration", "personal freedom and liberty", "economic impact and tax revenue", "social equity and racial justice", "youth protection and access"]
- synthesize: true
- consensus_threshold: 0.7

### Prompt 2: Immigration Policy

"Analyze immigration policy from these perspectives: economic impact on jobs and wages, humanitarian obligations, national security, cultural integration,
fiscal costs and benefits, and labor market needs. Synthesize into a coherent policy recommendation. Use consensus threshold of 0.65."

**Configuration hints:**

- analysis_subject: "Comprehensive immigration reform policy"
-
perspectives: ["economic impact on jobs and wages", "humanitarian obligations and asylum", "national security concerns", "cultural integration and social cohesion", "fiscal costs and tax contributions", "labor market needs and shortages"]
- synthesize: true
- consensus_threshold: 0.65

### Prompt 3: Universal Healthcare

"Analyze universal healthcare from these angles: healthcare outcomes and quality, economic costs and efficiency, personal choice and freedom, business
competitiveness, innovation in medicine, and equity of access. Synthesize these perspectives into a unified conclusion. Use consensus threshold of 0.7."

**Configuration hints:**

- analysis_subject: "Implementing universal healthcare system"
-
perspectives: ["healthcare outcomes and quality of care", "economic costs and efficiency", "personal choice and freedom", "business competitiveness and labor mobility", "medical innovation and research", "equity and access to care"]
- synthesize: true
- consensus_threshold: 0.7

---

## 10. SocraticDialogue

### Prompt 1: Nature of Happiness

"Explore through Socratic questioning: What is happiness, and can we choose to be happy? Start with the question 'Is happiness something we find or something we
create?' Challenge assumptions about happiness being dependent on external circumstances versus internal mindset. Go 6 exchanges deep."

**Configuration hints:**

- initial_question: "Is happiness something we find or something we create?"
- max_depth: 6
- challenge_assumptions: true
- domain_constraints: ["psychology", "philosophy", "well-being"]

### Prompt 2: Justice and Fairness

"Use Socratic dialogue to explore: What makes something 'fair' or 'just'? Start with 'Should everyone get equal outcomes, or equal opportunities?' Challenge
assumptions about equality, merit, need, and desert. Go 7 exchanges deep."

**Configuration hints:**

- initial_question: "Should everyone get equal outcomes, or equal opportunities?"
- max_depth: 7
- challenge_assumptions: true
- domain_constraints: ["ethics", "political philosophy", "social justice"]

### Prompt 3: Free Will

"Explore through Socratic questioning: Do we have free will, or are our choices determined by factors beyond our control? Start with 'If our brains are physical
systems following natural laws, how can we have free will?' Challenge assumptions about consciousness, choice, and responsibility. Go 6 exchanges deep."

**Configuration hints:**

- initial_question: "If our brains are physical systems following natural laws, how can we have free will?"
- max_depth: 6
- challenge_assumptions: true
- domain_constraints: ["philosophy", "neuroscience", "ethics"]

---

## 11. Brainstorming

### Prompt 1: Reducing Food Waste

"I need creative solutions for reducing food waste in my household. We throw away about 30% of our groceries. Brainstorm 7-10 diverse options ranging from
practical to innovative. Include some unconventional ideas. Analyze each option's pros, cons, feasibility, and impact."
**Configuration hints:**

- problem_statement: "Reducing household food waste - currently throwing away ~30% of groceries"
- target_option_count: 8
- categories: ["storage solutions", "meal planning", "technology", "behavior change", "community solutions"]
- constraints: ["Must be implementable by average household", "Should not require major lifestyle changes"]
- include_creative_options: true
- analysis_depth: "moderate"

### Prompt 2: Improving Local Community

"Our neighborhood feels disconnected and people don't know each other. Brainstorm ways to build community and increase neighborly interaction. Generate 10
options from simple to ambitious. Include both traditional and creative approaches. Analyze feasibility and potential impact of each."
**Configuration hints:**

- problem_statement: "Building stronger community connections in a disconnected suburban neighborhood"
- target_option_count: 10
- categories: ["events and gatherings", "shared spaces", "digital platforms", "regular activities", "infrastructure"]
- constraints: ["Must work in suburban setting", "Should appeal to diverse age groups", "Limited budget available"]
- include_creative_options: true
- analysis_depth: "moderate"

### Prompt 3: Career Transition Strategy

"I'm a 35-year-old accountant who wants to transition into a more creative field but need to maintain income. Brainstorm 6-8 realistic transition strategies.
Focus on practical, proven approaches but include one or two innovative options. Analyze risks and requirements for each path."
**Configuration hints:**

- problem_statement: "Career transition from accounting to creative field while maintaining income stability"
- target_option_count: 7
- categories: ["gradual transition", "education/training", "freelance/side hustle", "industry pivot", "entrepreneurship"]
- constraints: ["Must maintain current income level", "Prefer transition within 2-3 years", "Limited time for retraining"]
- include_creative_options: false
- analysis_depth: "detailed"

---

## 12. GameTheory

### Prompt 1: Salary Negotiation

"I'm negotiating salary for a new job. The company has a budget but wants to pay less. I want maximum salary but don't want to lose the offer. Both sides have
incomplete information about the other's limits. Analyze this as a game theory problem. What's my optimal strategy? Should I reveal my current salary? Make the
first offer? What are the Nash equilibria?"
**Configuration hints:**

- game_scenario: "Job salary negotiation between candidate and employer, both trying to maximize their outcome while reaching agreement"
- players: ["Job Candidate", "Hiring Manager/Company"]
- game_type: "non-cooperative"
- build_payoff_matrix: true
- find_nash_equilibria: true
- analyze_dominant_strategies: true
- provide_recommendations: true
- additional_context: "Incomplete information game - neither party knows the other's true reservation price. Candidate risks losing offer if demands too high.
  Company risks losing candidate if offers too low."

### Prompt 2: Climate Change Cooperation

"Countries face a climate change dilemma: everyone benefits if all reduce emissions, but each country benefits most by not reducing while others do (free-rider
problem). Analyze this as a game theory problem. What are the Nash equilibria? How can cooperation be sustained? What role do repeated interactions and
reputation play?"
**Configuration hints:**

- game_scenario: "International climate cooperation - countries must decide whether to reduce emissions (costly) or continue polluting (beneficial short-term)"
- players: ["Developed Nations", "Developing Nations", "Major Polluters"]
- game_type: "non-cooperative"
- build_payoff_matrix: true
- find_nash_equilibria: true
- find_pareto_optimal: true
- repeated_game_analysis: true
- iterations: 20
- provide_recommendations: true
- additional_context: "Classic tragedy of the commons. Individual incentive to defect but collective benefit from cooperation. Repeated game with reputation
  effects."

### Prompt 3: Social Media Platform Competition

"Two social media platforms (like Twitter and Threads) compete for users. Users prefer platforms where their friends are (network effects). Each platform
decides whether to allow easy data portability or lock users in. Analyze the strategic dynamics. What are the equilibria? Should platforms cooperate on
interoperability?"
**Configuration hints:**

- game_scenario: "Social media platform competition with network effects - platforms decide on data portability and interoperability strategies"
- players: ["Incumbent Platform", "New Challenger Platform"]
- player_strategies: {
  "Incumbent Platform": ["Allow data portability", "Lock-in users", "Selective interoperability"],
  "New Challenger Platform": ["Full interoperability", "Closed ecosystem", "Gradual opening"]
  }
- game_type: "non-cooperative"
- build_payoff_matrix: true
- find_nash_equilibria: true
- analyze_dominant_strategies: true
- provide_recommendations: true
- additional_context: "Strong network effects create winner-take-all dynamics. First-mover advantage vs. late-mover learning. Regulatory pressure for
  interoperability."

---

## 13. FiniteStateMachine

### Prompt 1: Online Dating Journey

"Model the journey of someone using a dating app as a finite state machine. Start from creating a profile through various stages of matching, messaging, dating,
and potential outcomes. Include error states like ghosting, catfishing, or burnout. Identify all possible states and transitions. Generate test scenarios for
different user journeys."
**Configuration hints:**

- concept_to_model: "User journey through online dating app from profile creation to relationship outcome"
- domain_context: "Online dating and relationship formation"
- initial_states: ["No Profile"]
- known_events: ["Create profile", "Get match", "Send message", "Receive reply", "Schedule date", "Go on date", "Get ghosted", "Delete app"]
- identify_edge_cases: true
- validate_properties: true
- generate_test_scenarios: true

### Prompt 2: Job Application Process

"Model the job application process as a finite state machine. From seeing a job posting through applying, interviewing, negotiating, and final outcomes. Include
rejection states, ghosting, offer rescinding, and candidate withdrawal. Identify all states and transitions. Validate that the FSM is complete and handles all
edge cases."
**Configuration hints:**

- concept_to_model: "Job application and hiring process from candidate perspective"
- domain_context: "Employment and recruitment"
- initial_states: ["Job Seeker"]
-
known_events: ["See job posting", "Submit application", "Get screening call", "Complete interview", "Receive offer", "Get rejected", "Withdraw application", "Negotiate offer"]
- identify_edge_cases: true
- validate_properties: true
- generate_test_scenarios: true

### Prompt 3: Subscription Service Lifecycle

"Model a customer's lifecycle with a subscription service (like Netflix or Spotify) as a finite state machine. Include trial periods, active subscription,
payment failures, pausing, cancellation, win-back attempts, and reactivation. Identify all states, transitions, and edge cases. Generate test scenarios for
different customer journeys."
**Configuration hints:**

- concept_to_model: "Customer lifecycle with subscription service from trial to cancellation and potential reactivation"
- domain_context: "Subscription business model and customer retention"
- initial_states: ["Prospect"]
-
known_events: ["Start trial", "Convert to paid", "Payment succeeds", "Payment fails", "Pause subscription", "Cancel subscription", "Reactivate", "Receive win-back offer"]
- identify_edge_cases: true
- validate_properties: true
- generate_test_scenarios: true

---

## Additional Usage Notes for New Tools

### Brainstorming Tool

- Best for: Generating diverse solution options when stuck or need fresh perspectives
- Works well with: Problems that have multiple possible approaches
- Tip: Set `include_creative_options: true` for innovation, `false` for proven approaches
- Output: Structured list of options with independent analysis of each

### GameTheory Tool

- Best for: Strategic situations with multiple parties and conflicting interests
- Works well with: Negotiations, competition, cooperation dilemmas
- Tip: Use `repeated_game_analysis: true` for ongoing relationships
- Output: Payoff matrices, Nash equilibria, strategic recommendations

### FiniteStateMachine Tool

- Best for: Understanding processes, workflows, and state-dependent systems
- Works well with: User journeys, business processes, protocols
- Tip: Enable all validation options to catch edge cases and missing transitions
- Output: State diagram, transition table, test scenarios, validation report
  These tools complement the existing reasoning tools by adding:
- **Brainstorming**: Divergent thinking and option generation
- **GameTheory**: Strategic interaction analysis
- **FiniteStateMachine**: Process modeling and validation

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
---
