### 1. Journalistic Writing

These tasks are designed for creating fact-based, well-structured journalistic content.

#### A. JournalismReasoningTask

This is the analytical foundation for any journalistic work. It investigates a story topic to uncover facts, perspectives, and potential angles without writing the final article.

**Purpose:**
*   Investigative reporting and research.
*   Fact-checking claims and sources.
*   Editorial planning and story vetting.
*   Understanding the different sides of a complex issue.

**Key Features:**
*   **Fact Verification:** Identifies and checks key factual claims, assessing their validity.
*   **Perspective Analysis:** Identifies key stakeholders, sources, and their different viewpoints.
*   **Bias Detection:** Analyzes the topic for potential biases, conflicts of interest, and missing voices.
*   **Contextual Analysis:** Provides historical background and broader implications to understand why the story matters.
*   **Angle Exploration:** Suggests multiple alternative angles for how the story could be covered.
*   **Gap Finding:** Pinpoints unanswered questions and missing information that require further investigation.

**When to Use:**
Use this task when you have a story idea but need to do the background research. It's perfect for building a solid, fact-based foundation before you start writing.

#### B. ArticleGenerationTask

This task is an end-to-end solution for writing a complete, publication-ready article. It automatically performs the journalism reasoning analysis as its first step.

**Purpose:**
*   Generating complete news articles, feature stories, or opinion pieces.
*   Automating content creation for news sites or blogs.
*   Rapidly drafting articles based on a topic or event.

**Key Features:**
*   **Inherits all features** from `JournalismReasoningTask`.
*   **Structure Generation:** Creates a detailed outline for the article, including a headline, subheadline, lede, and body sections.
*   **Full Article Writing:** Writes the entire article based on the investigation and outline, following journalistic standards.
*   **Revision Passes:** Can automatically perform one or more revision passes to improve clarity, flow, and quality.
*   **Social Media Snippets:** Can optionally generate short, engaging snippets for Twitter, Facebook, and LinkedIn.

**Key Configuration Options:**
*   `story_topic`: The central subject of the article.
*   `target_word_count`: The desired length of the final article.
*   `article_format`: The type of article (e.g., 'news', 'feature', 'investigative', 'opinion').
*   `writing_style`: The style to follow (e.g., 'AP style', 'narrative', 'analytical').
*   `target_publication`: The intended audience or publication, which influences tone and depth.
*   `include_quotes`, `include_data`, etc.: Booleans to control whether specific elements are included.
*   `revision_passes`: The number of editing passes to perform on the draft.

---

### 2. Narrative & Story Writing

These tasks are designed for creative writing, helping you analyze, plan, and generate complete narratives.

#### A. NarrativeReasoningTask

This task analyzes a subject or scenario through the lens of storytelling. It deconstructs the elements of a story to understand its structure, characters, and potential paths.

**Purpose:**
*   Developing a story idea or plot.
*   Analyzing character motivations and arcs.
*   Planning a novel, screenplay, or user journey.
*   Exploring "what-if" scenarios and predicting outcomes.

**Key Features:**
*   **Narrative Construction:** Builds a coherent story structure (e.g., three-act structure) from a set of elements.
*   **Plot Point Identification:** Pinpoints key moments like the inciting incident, climax, and resolution.
*   **Character Analysis:** Dives deep into character motivations, goals, and conflicts.
*   **Outcome Prediction:** Explores multiple alternative paths and potential endings for the narrative.
*   **Inconsistency Detection:** Finds logical gaps, timeline errors, or character inconsistencies in a plot.

**When to Use:**
Use this task when you have a basic idea for a story but need to flesh out the plot, characters, and structure before you begin writing scenes.

#### B. NarrativeGenerationTask

This task takes a story concept and writes the complete narrative, scene by scene. It uses the narrative reasoning analysis as its foundation.

**Purpose:**
*   Writing a complete short story or chapter.
*   Generating detailed scenarios for training or simulation.
*   Creating user journey narratives for product design.

**Key Features:**
*   **Inherits all features** from `NarrativeReasoningTask`.
*   **Detailed Outlining:** Creates a comprehensive, scene-by-scene outline for the entire story.
*   **Iterative Scene Generation:** Writes each scene one by one, feeding the context from previous scenes into the generation of the next one. This ensures high consistency in plot and character development.
*   **Full Narrative Assembly:** Compiles all the generated scenes into a single, complete story.

**Key Configuration Options:**
*   `subject`: The core idea, character, or scenario for the story.
*   `target_word_count`: The desired length of the final narrative.
*   `number_of_acts` & `scenes_per_act`: Control the story's structure.
*   `writing_style`: The literary style (e.g., 'literary', 'thriller', 'conversational').
*   `point_of_view`: The narrative perspective (e.g., 'first person', 'third person limited').
*   `tone`: The emotional mood of the story (e.g., 'dramatic', 'humorous', 'suspenseful').
*   `include_dialogue` & `show_internal_thoughts`: Control the level of detail in the writing.

---

### 3. Persuasive Writing

This task is a specialized tool for creating well-structured, convincing arguments.

#### PersuasiveEssayTask

This is an all-in-one task for generating a complete persuasive essay from a single thesis statement.

**Purpose:**
*   Writing opinion pieces, editorials, or blog posts.
*   Drafting academic essays or research papers.
*   Creating proposals, speeches, or marketing copy.

**Key Features:**
*   **Argument Outlining:** Creates a logical structure for the essay, including a hook, thesis, main arguments, and conclusion.
*   **Counterargument & Rebuttal:** Can identify potential counterarguments to your thesis and write strong rebuttals, making your essay more robust.
*   **Rhetorical Devices:** Intelligently uses persuasive techniques (Ethos, Pathos, Logos) to appeal to the reader's logic and emotions.
*   **Evidence Integration:** Can incorporate statistical evidence, examples, and analogies to support claims.
*   **Configurable Call to Action:** Allows you to specify the strength and type of call to action in the conclusion (from 'strong' to 'reflective').

**Key Configuration Options:**
*   `thesis`: The core statement or position you want to argue for. This is the most important input.
*   `target_audience`: Who you are trying to convince (e.g., 'academics', 'policymakers', 'general public').
*   `tone`: The desired tone of the essay (e.g., 'formal', 'passionate', 'analytical').
*   `num_arguments`: The number of main body paragraphs to develop in support of the thesis.
*   `include_counterarguments`: A boolean to turn the counterargument/rebuttal section on or off.
*   `call_to_action`: The type of concluding action you want the reader to consider.




`num_arguments`: The number of main body paragraphs to develop in support of the thesis.
`include_counterarguments`: A boolean to turn the counterargument/rebuttal section on or off.
`call_to_action`: The type of concluding action you want the reader to consider.

---

### 4. Technical Writing

These tasks are focused on creating clear, accurate, and easy-to-understand technical documentation and guides.

#### A. TechnicalExplanationTask

This task breaks down a complex technical subject into a simple, digestible explanation tailored to a specific audience.

**Purpose:**
*   Explaining a complex algorithm, scientific concept, or software architecture.
*   Creating content for documentation "concepts" pages or wikis.
*   Answering technical questions in a clear and structured way.
*   Onboarding new team members to a complex system.

**Key Features:**
*   **Audience-Level Adjustment:** Simplifies language, analogies, and examples based on the target audience's expertise (e.g., 'beginner', 'expert', 'manager').
*   **Analogy and Metaphor Generation:** Creates relatable analogies to explain abstract concepts.
*   **Structured Breakdown:** Organizes the explanation logically, often starting with a high-level overview and progressively adding detail.
*   **Code Snippet Integration:** Can generate or format code examples to illustrate the explanation.
*   **Key Terminology Definition:** Identifies and defines essential jargon or terminology for the reader.

**Key Configuration Options:**
*   `topic`: The complex subject to explain.
*   `target_audience`: The intended reader's level of expertise (e.g., 'layperson', 'software_engineer', 'data_scientist').
*   `level_of_detail`: Controls the depth of the explanation ('high-level_overview', 'detailed_walkthrough').
*   `include_code_examples`: Boolean to control the inclusion of code snippets.
*   `explanation_format`: The output format (e.g., 'markdown', 'q_and_a', 'step_by_step').

#### B. TutorialGenerationTask

This task creates a complete, step-by-step tutorial that guides a user through a specific process or project.

**Purpose:**
*   Writing "how-to" guides and tutorials for software, tools, or processes.
*   Creating educational content for workshops or online courses.
*   Generating project-based learning materials.

**Key Features:**
*   **Step-by-Step Logic:** Breaks the process down into a numbered sequence of clear, actionable steps.
*   **Prerequisite Identification:** Lists the necessary tools, software, or prior knowledge required to follow the tutorial.
*   **Command and Code Generation:** Generates the exact commands to run or code to write for each step.
*   **Expected Outcome Description:** For each step, describes what the user should see or expect as a result.
*   **Troubleshooting Tips:** Can optionally include a section with common problems and their solutions.

**Key Configuration Options:**
*   `goal`: The final outcome the user should achieve (e.g., 'deploy a web app to the cloud', 'train a simple machine learning model').
*   `target_platform`: The environment the tutorial is for (e.g., 'Windows', 'Linux', 'VS Code').
*   `include_screenshots_placeholders`: Boolean to add placeholders like `[Screenshot of the successful output]` where visuals would be needed.
*   `verbosity`: Controls how much explanatory text is included with each step ('concise', 'detailed').
*   `include_troubleshooting`: Boolean to add a common errors section.

# Brainstormed Additions to Writing Tools

## 1. Academic & Research Writing

### A. ResearchPaperTask
**Purpose:**
- Writing complete academic research papers with proper structure
- Literature review generation and synthesis
- Hypothesis development and methodology description
- Results analysis and discussion sections

**Key Features:**
- **Citation Management:** Automatically formats citations in various styles (APA, MLA, Chicago, IEEE)
- **Literature Review Synthesis:** Analyzes and synthesizes multiple sources into coherent themes
- **Methodology Section Generation:** Creates detailed research methodology descriptions
- **Abstract Generation:** Produces structured abstracts with background, methods, results, conclusions
- **Statistical Analysis Integration:** Incorporates statistical findings with proper interpretation
- **Peer Review Simulation:** Identifies potential weaknesses reviewers might flag

**Key Configuration Options:**
- `research_question`: The central question being investigated
- `citation_style`: Academic citation format to use
- `field_of_study`: Discipline-specific conventions (e.g., 'psychology', 'computer_science')
- `paper_type`: Type of research ('empirical', 'theoretical', 'meta-analysis', 'case_study')
- `include_limitations`: Boolean for limitations section
- `target_journal`: Specific publication to format for

### B. LiteratureReviewTask
**Purpose:**
- Synthesizing existing research on a topic
- Identifying research gaps and trends
- Creating comprehensive bibliographies

**Key Features:**
- **Thematic Organization:** Groups sources by themes rather than chronologically
- **Gap Analysis:** Identifies what hasn't been studied yet
- **Trend Identification:** Spots evolving perspectives over time
- **Critical Evaluation:** Assesses methodology and validity of sources
- **Synthesis Matrix Generation:** Creates comparison tables of key studies

---

## 2. Business & Professional Writing

### A. BusinessProposalTask
**Purpose:**
- Creating project proposals, RFP responses, and business plans
- Pitching ideas to stakeholders or investors
- Grant applications and funding requests

**Key Features:**
- **Executive Summary Generation:** Creates compelling high-level overviews
- **ROI Calculation Integration:** Incorporates financial projections and cost-benefit analysis
- **Risk Assessment Section:** Identifies and mitigates potential concerns
- **Stakeholder Analysis:** Tailors messaging to different decision-makers
- **Competitive Analysis:** Positions proposal against alternatives
- **Timeline and Milestone Planning:** Creates realistic project schedules

**Key Configuration Options:**
- `proposal_type`: Type of proposal ('project', 'investment', 'grant', 'partnership')
- `budget_range`: Financial scope of the proposal
- `decision_makers`: Who will evaluate the proposal
- `urgency_level`: Time sensitivity of the opportunity
- `include_appendices`: Boolean for supporting documents

### B. EmailCampaignTask
**Purpose:**
- Writing email sequences for marketing, sales, or outreach
- Creating newsletter content
- Automated follow-up sequences

**Key Features:**
- **Sequence Planning:** Designs multi-email campaigns with logical progression
- **Subject Line Optimization:** Generates A/B testable subject lines
- **Personalization Tokens:** Includes merge fields for customization
- **CTA Optimization:** Creates clear, compelling calls-to-action
- **Tone Consistency:** Maintains brand voice across sequence
- **Timing Recommendations:** Suggests optimal send intervals

### C. ReportGenerationTask
**Purpose:**
- Creating business reports, status updates, and analytical summaries
- Quarterly reviews and performance reports
- Incident reports and post-mortems

**Key Features:**
- **Data Visualization Descriptions:** Suggests charts/graphs for data points
- **Executive Dashboard Creation:** Highlights key metrics upfront
- **Trend Analysis:** Identifies patterns in data over time
- **Recommendation Engine:** Generates actionable next steps based on findings
- **Comparative Analysis:** Benchmarks against previous periods or competitors

---

## 3. Creative & Marketing Content

### A. CopywritingTask
**Purpose:**
- Writing advertising copy, landing pages, and product descriptions
- Creating taglines and brand messaging
- Social media ad copy

**Key Features:**
- **Hook Generation:** Creates attention-grabbing opening lines
- **Benefit-Focused Writing:** Emphasizes outcomes over features
- **Urgency and Scarcity Tactics:** Incorporates psychological triggers
- **A/B Variant Generation:** Creates multiple versions for testing
- **SEO Optimization:** Includes keyword integration naturally
- **Character Limit Compliance:** Respects platform constraints (e.g., Twitter, Google Ads)

**Key Configuration Options:**
- `product_or_service`: What's being promoted
- `unique_selling_proposition`: Key differentiator
- `target_emotion`: Desired emotional response ('excitement', 'trust', 'curiosity')
- `conversion_goal`: Desired action ('purchase', 'signup', 'download')
- `brand_voice`: Personality traits ('playful', 'authoritative', 'empathetic')

### B. ContentRepurposingTask
**Purpose:**
- Converting long-form content into multiple formats
- Adapting content for different platforms
- Maximizing content ROI

**Key Features:**
- **Multi-Format Output:** Generates blog posts, social posts, infographics scripts, podcast outlines from one source
- **Platform Optimization:** Tailors content to each platform's best practices
- **Key Message Extraction:** Identifies core ideas to maintain across formats
- **Hashtag and Keyword Suggestions:** Platform-specific discovery optimization
- **Content Calendar Integration:** Suggests posting schedule across platforms

### C. ScriptwritingTask
**Purpose:**
- Writing video scripts, podcast episodes, or presentation scripts
- Creating dialogue for training videos or explainer animations
- Voiceover scripts for commercials

**Key Features:**
- **Scene Direction:** Includes visual and audio cues
- **Timing Estimation:** Calculates approximate runtime
- **Dialogue Naturalization:** Ensures spoken language sounds conversational
- **B-Roll Suggestions:** Recommends supporting visuals
- **Pacing Control:** Varies rhythm for engagement
- **Call-Out Boxes:** Marks key points for emphasis or graphics

---

## 4. Specialized Writing

### A. LegalDocumentDraftingTask
**Purpose:**
- Creating contracts, terms of service, privacy policies
- Drafting legal correspondence
- Generating compliance documentation

**Key Features:**
- **Clause Library:** Draws from standard legal language
- **Jurisdiction Awareness:** Adapts to regional legal requirements
- **Plain Language Translation:** Creates layperson-friendly summaries
- **Risk Flagging:** Identifies potentially problematic clauses
- **Version Comparison:** Tracks changes between drafts
- **Disclaimer Generation:** Creates appropriate legal disclaimers

**Note:** Should include prominent disclaimer that output requires attorney review.

### B. MedicalWritingTask
**Purpose:**
- Creating patient education materials
- Writing clinical trial documentation
- Regulatory submission documents

**Key Features:**
- **Medical Terminology Management:** Balances technical accuracy with readability
- **Regulatory Compliance:** Follows FDA, EMA, or other guidelines
- **Evidence Grading:** Cites research with appropriate strength indicators
- **Patient-Friendly Versions:** Creates parallel simplified versions
- **Adverse Event Reporting:** Structures safety information appropriately

### C. TranslationAdaptationTask
**Purpose:**
- Transcreating content for different languages and cultures
- Localizing marketing materials
- Adapting idioms and cultural references

**Key Features:**
- **Cultural Sensitivity Analysis:** Flags potentially problematic content
- **Idiom Replacement:** Finds culturally equivalent expressions
- **Formality Level Adjustment:** Adapts to cultural communication norms
- **Visual Element Suggestions:** Recommends image/color changes for cultural appropriateness
- **Local Example Integration:** Replaces examples with locally relevant ones

---

## 5. Interactive & Conversational Writing

### A. ChatbotDialogueTask
**Purpose:**
- Creating conversational flows for chatbots
- Designing voice assistant interactions
- Building FAQ response systems

**Key Features:**
- **Intent Mapping:** Identifies user goals and maps to responses
- **Fallback Strategy:** Creates graceful error handling
- **Personality Consistency:** Maintains character across interactions
- **Context Awareness:** Tracks conversation state
- **Escalation Paths:** Knows when to hand off to humans
- **Multi-Turn Planning:** Designs complex conversation trees

### B. InteractiveStoryTask
**Purpose:**
- Creating choose-your-own-adventure narratives
- Designing branching training scenarios
- Building interactive fiction or games

**Key Features:**
- **Decision Point Mapping:** Creates meaningful choice nodes
- **Consequence Tracking:** Ensures choices have logical outcomes
- **Path Convergence:** Manages multiple storylines efficiently
- **State Variable Management:** Tracks inventory, relationships, stats
- **Dead End Prevention:** Ensures all paths lead somewhere meaningful
- **Replay Value Optimization:** Creates distinct experiences for different choices

---

## 6. Editing & Enhancement Tools

### A. StyleConsistencyTask
**Purpose:**
- Ensuring consistent voice and style across documents
- Aligning content with brand guidelines
- Harmonizing multi-author content

**Key Features:**
- **Voice Analysis:** Identifies stylistic patterns in reference text
- **Inconsistency Detection:** Flags deviations from established style
- **Terminology Standardization:** Ensures consistent term usage
- **Tone Calibration:** Adjusts emotional register to match target
- **Readability Scoring:** Measures and adjusts complexity level

### B. ContentExpansionTask
**Purpose:**
- Expanding bullet points into full paragraphs
- Adding depth and detail to sparse content
- Meeting word count requirements meaningfully

**Key Features:**
- **Context Inference:** Intelligently expands based on surrounding content
- **Example Generation:** Adds relevant examples and illustrations
- **Transition Creation:** Smoothly connects expanded sections
- **Redundancy Avoidance:** Adds new information rather than repeating
- **Depth Control:** Allows specification of how much to expand

### C. SimplificationTask
**Purpose:**
- Reducing complexity for broader audiences
- Creating plain language versions of technical content
- Improving accessibility and readability

**Key Features:**
- **Jargon Replacement:** Substitutes technical terms with common language
- **Sentence Restructuring:** Breaks complex sentences into simpler ones
- **Active Voice Conversion:** Changes passive constructions to active
- **Readability Targeting:** Aims for specific grade level (e.g., 8th grade)
- **Concept Preservation:** Maintains accuracy while simplifying

---

## 7. Analysis & Feedback Tools

### A. ContentAuditTask
**Purpose:**
- Analyzing existing content for quality and effectiveness
- Identifying improvement opportunities
- Competitive content analysis

**Key Features:**
- **SEO Analysis:** Evaluates keyword usage and optimization
- **Engagement Prediction:** Estimates likely reader response
- **Gap Identification:** Finds missing information or perspectives
- **Structural Analysis:** Assesses organization and flow
- **Fact-Checking Flags:** Identifies claims needing verification
- **Improvement Prioritization:** Ranks suggested changes by impact

### B. WritingCoachTask
**Purpose:**
- Providing developmental feedback on drafts
- Teaching writing principles through examples
- Personalized writing improvement plans

**Key Features:**
- **Strength Identification:** Highlights what's working well
- **Constructive Critique:** Explains why something isn't working
- **Alternative Suggestions:** Offers multiple ways to improve passages
- **Pattern Recognition:** Identifies recurring issues across writing
- **Progress Tracking:** Shows improvement over multiple submissions
- **Exercise Generation:** Creates targeted practice activities

---

## 8. Specialized Content Types

### A. RecipeWritingTask
**Purpose:**
- Creating clear, tested-style recipe instructions
- Writing food blog posts with recipes
- Generating meal plans

**Key Features:**
- **Ingredient List Formatting:** Standardizes measurements and order
- **Step Clarity:** Ensures each instruction is actionable
- **Timing Information:** Includes prep, cook, and total times
- **Substitution Suggestions:** Offers alternatives for dietary needs
- **Scaling Calculations:** Adjusts quantities for different serving sizes
- **Headnote Generation:** Creates engaging recipe introductions

### B. ProductDescriptionTask
**Purpose:**
- Writing e-commerce product descriptions
- Creating catalog copy
- Generating comparison content

**Key Features:**
- **Feature-Benefit Translation:** Converts specs into customer value
- **SEO Optimization:** Naturally incorporates search terms
- **Sensory Language:** Uses descriptive, evocative words
- **Social Proof Integration:** Incorporates review highlights
- **Specification Formatting:** Presents technical details clearly
- **Cross-Sell Suggestions:** Recommends complementary products

### C. GrantWritingTask
**Purpose:**
- Writing grant applications and funding proposals
- Creating nonprofit program descriptions
- Developing impact statements

**Key Features:**
- **Needs Statement Development:** Articulates the problem compellingly
- **Logic Model Creation:** Connects activities to outcomes
- **Budget Narrative:** Explains and justifies costs
- **Evaluation Plan:** Describes how success will be measured
- **Sustainability Planning:** Addresses long-term viability
- **Funder Alignment:** Tailors to specific foundation priorities
