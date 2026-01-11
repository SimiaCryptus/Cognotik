# Writing

## ArticleGeneration

Generate complete journalistic articles from investigation and analysis

Extends JournalismReasoning to generate publication-ready articles.
<ul>
  <li>Performs comprehensive journalism investigation (inherited from JournalismReasoning)</li>
  <li>Creates detailed article structure and outline</li>
  <li>Writes complete article following journalistic standards</li>
  <li>Supports multiple formats (news, feature, investigative, opinion, profile)</li>
  <li>Configurable style, tone, and target publication</li>
  <li>Includes quotes, data, expert analysis, and context as configured</li>
  <li>Optional revision passes for quality improvement</li>
  <li>Can generate headlines and social media snippets</li>
  <li>Produces publication-ready articles with proper structure and attribution</li>
  <li>Ideal for news writing, content creation, journalism training</li>
</ul>

#### Planner Prompt Segment

```text
ArticleGeneration - Generate complete journalistic articles from investigation and analysis
  ** Extends JournalismReasoning with full article writing
  ** Specify the story topic to write about
  ** Define journalism elements: who, what, when, where, why, how
  ** Set target word count and article format (news, feature, investigative, etc.)
  ** Configure writing style and target publication
  ** Enable quotes, data, expert analysis, and context
  ** Performs investigation, creates structure, then writes article
  ** Optional revision passes for quality improvement
  ** Can generate headlines and social media snippets
  ** Produces publication-ready articles with proper journalistic structure
```

#### Default Execution Configuration

```json
{
  "task_type" : "ArticleGeneration",
  "story_topic" : null,
  "input_files" : null,
  "journalism_elements" : null,
  "target_word_count" : 1000,
  "article_format" : "news",
  "writing_style" : "AP style",
  "target_publication" : "general news",
  "include_quotes" : true,
  "include_data" : true,
  "include_expert_analysis" : true,
  "include_context" : true,
  "revision_passes" : 1,
  "generate_social_snippets" : false,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ArticleGeneration",
  "task_description" : "Generate news article about 'null'",
  "verify_facts" : true,
  "identify_perspectives" : true,
  "analyze_context" : true,
  "identify_biases" : true,
  "find_gaps" : true,
  "alternative_angles" : 1,
  "assess_newsworthiness" : true
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ArticleGeneration",
  "name" : "ArticleGeneration",
  "model" : null
}
```

---

## BusinessProposal

Generate comprehensive business proposals with ROI analysis and risk assessment

Generates complete, professional business proposals for various purposes.
<ul>
  <li>Performs stakeholder analysis to understand decision-makers</li>
  <li>Creates detailed ROI analysis with financial projections</li>
  <li>Conducts risk assessment with mitigation strategies</li>
  <li>Analyzes competitive alternatives and positioning</li>
  <li>Develops timeline with milestones and dependencies</li>
  <li>Writes compelling executive summary and sections</li>
  <li>Includes optional revision passes for quality</li>
  <li>Supports multiple proposal types (project, investment, grant, partnership, RFP)</li>
  <li>Ideal for project proposals, funding requests, vendor responses, and business plans</li>
</ul>

#### Planner Prompt Segment

```text
BusinessProposal - Generate comprehensive business proposals with ROI analysis and risk assessment
  ** Specify the proposal title and objective
  ** Define proposal type (project, investment, grant, partnership, RFP response)
  ** Identify decision-makers and stakeholders
  ** Set budget range and timeline
  ** Enable ROI calculations and financial projections
  ** Include risk assessment and mitigation strategies
  ** Add competitive analysis and alternatives comparison
  ** Generate timeline with milestones
  ** Specify resource requirements
  ** Produces complete, persuasive business proposal
```

#### Default Execution Configuration

```json
{
  "task_type" : "BusinessProposal",
  "proposal_title" : null,
  "proposal_type" : "project",
  "objective" : null,
  "proposing_organization" : null,
  "decision_makers" : null,
  "budget_range" : null,
  "timeline" : null,
  "stakeholders" : null,
  "include_roi_analysis" : true,
  "include_risk_assessment" : true,
  "include_competitive_analysis" : true,
  "include_timeline_milestones" : true,
  "include_resource_requirements" : true,
  "include_appendices" : true,
  "urgency_level" : "moderate",
  "tone" : "professional",
  "target_word_count" : 3000,
  "revision_passes" : 1,
  "related_files" : null,
  "input_files" : null,
  "task_description" : "Generate business proposal: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "BusinessProposal"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "BusinessProposal",
  "name" : "BusinessProposal",
  "model" : null
}
```

---

## ComicBookGeneration

Generate comic book scripts and visuals

Creates a comic book with page/row/frame structure and optional visual generation.

#### Planner Prompt Segment

```text
ComicBookGeneration - Generate comic book scripts and visuals
  - Create a comic book script with page/row/frame structure
  - Specify subject, target pages, and art style
  - Generates character profiles and visual descriptions
  - Can generate images for each row (strip)
```

#### Default Execution Configuration

```json
{
  "task_type" : "ComicBookGeneration",
  "subject" : null,
  "target_pages" : 5,
  "art_style" : "western superhero",
  "style_details" : "",
  "generate_images" : true,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ComicBookGeneration",
  "task_description" : "Generate comic book for 'null'"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ComicBookGeneration",
  "name" : "ComicBookGeneration",
  "model" : null
}
```

---

## EmailCampaign

Generate complete email sequences for marketing, sales, or outreach

Generates complete, ready-to-use email campaigns with strategic planning.
<ul>
  <li>Develops comprehensive campaign strategy and messaging</li>
  <li>Creates detailed outline for each email in the sequence</li>
  <li>Generates A/B test variants for subject lines</li>
  <li>Writes complete email bodies with CTAs</li>
  <li>Includes personalization tokens and preview text</li>
  <li>Supports multiple campaign types (welcome, nurture, sales, etc.)</li>
  <li>Configurable brand voice, tone, and length</li>
  <li>Optional revision passes for quality improvement</li>
  <li>Provides implementation notes and best practices</li>
  <li>Ideal for marketing automation, sales outreach, and customer engagement</li>
</ul>

#### Planner Prompt Segment

```text
EmailCampaign - Generate multi-email marketing or outreach sequences.
- campaign_goal: Primary objective.
- subject_matter: Product or topic.
- target_audience: Who is receiving the emails.
- campaign_type: welcome_series, nurture, sales, etc.
- num_emails: Length of sequence (1-10).
- brand_voice: professional, friendly, etc.
- primary_cta: Main action desired.
- related_files: Brand guidelines or context.
```

#### Default Execution Configuration

```json
{
  "task_type" : "EmailCampaign",
  "campaign_goal" : null,
  "subject_matter" : null,
  "target_audience" : "general audience",
  "campaign_type" : "nurture",
  "num_emails" : 3,
  "send_intervals" : null,
  "brand_voice" : "professional",
  "primary_cta" : "learn_more",
  "generate_subject_variants" : true,
  "subject_variants_count" : 3,
  "include_personalization" : true,
  "include_preview_text" : true,
  "use_emoji" : false,
  "max_subject_length" : 60,
  "body_length" : "medium",
  "include_ps" : true,
  "revision_passes" : 1,
  "input_files" : null,
  "related_files" : null,
  "task_description" : "Generate email campaign for: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "EmailCampaign"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "EmailCampaign",
  "name" : "EmailCampaign",
  "model" : null
}
```

---

## GenerateImage

Generate images using AI image generation models

Creates images from text descriptions using AI models like DALL-E.
<ul>
  <li>Generates high-quality images from detailed prompts</li>
  <li>Context-aware generation using related files</li>
  <li>Integration with previous task results</li>
</ul>

#### Planner Prompt Segment

```text
GenerateImage - Create images using AI image generation models
```

#### Default Execution Configuration

```json
{
  "task_type" : "GenerateImage",
  "files" : null,
  "related_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GenerateImage",
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GenerateImage",
  "name" : "GenerateImage",
  "model" : null
}
```

---

## GeneratePresentation

Create complete Reveal.js presentations with narration support

Creates professional Reveal.js presentations with speaker notes.
<ul>
  <li>Generates complete, self-contained HTML presentations</li>
  <li>Includes Reveal.js framework integration</li>
  <li>Adds speaker notes for each slide</li>
  <li>Supports custom styling and themes</li>
  <li>Optional AI-generated images for key slides</li>
  <li>Interactive approval or auto-apply mode</li>
  <li>Includes navigation and progress indicators</li>
  <li>Optional audio narration support</li>
</ul>

#### Planner Prompt Segment

```text
GeneratePresentation - Create a Reveal.js presentation with custom styling
 ** Specify the HTML presentation file path in the files array (must end with .html)
 ** Provide a detailed description including:
    - Presentation topic and title
    - Key points and sections to cover
    - Target audience and tone (professional, casual, technical, etc.)
    - Number of slides desired
    - Any specific visual style preferences
 ** The generated presentation will include:
    - Complete HTML structure using Reveal.js framework
    - Multiple slides with proper structure and speaker notes
    - Custom CSS file (presentation.css) for styling
    - Autoplay controls and voice selection UI
    - Proper accessibility features
    - Optional AI-generated images for key slides
 ** Related files can include reference materials or existing presentations
 ** Output will be presented for review before being written to disk
```

#### Default Execution Configuration

```json
{
  "task_type" : "GeneratePresentation",
  "files" : null,
  "related_files" : null,
  "task_description" : null,
  "generate_images" : false,
  "image_width" : 1024,
  "image_height" : 1024,
  "max_images" : 5,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GeneratePresentation",
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GeneratePresentation",
  "name" : "GeneratePresentation",
  "model" : null
}
```

---

## GenerateQRImage

Generate artistic QR codes with AI styling

Creates stylized QR codes using AI image processing while maintaining scannability.
<ul>
  <li>Generates QR codes with high error correction (30% redundancy)</li>
  <li>Applies artistic styles using AI image generation</li>
  <li>Verifies the resulting QR code remains readable</li>
  <li>Retries with more conservative styling if verification fails</li>
</ul>

#### Planner Prompt Segment

```text
GenerateQRImage - Generate artistic QR codes using AI image processing
  ** files: The output image file to be created (relative path, must end with .png, .jpg, or .jpeg)
  ** qr_content: The data/text content to encode in the QR code
  ** style_directive: Artistic style directive for the Image Agent (e.g., 'watercolor painting')
  ** qr_size: Size of the QR code in pixels (default: 500)
  ** max_retries: Maximum number of retry attempts if QR verification fails (default: 3)
  ** related_files: Additional files for context (e.g., reference images)
```

#### Default Execution Configuration

```json
{
  "task_type" : "GenerateQRImage",
  "files" : null,
  "related_files" : null,
  "qr_content" : null,
  "style_directive" : null,
  "qr_size" : 500,
  "max_retries" : 3,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GenerateQRImage",
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GenerateQRImage",
  "name" : "GenerateQRImage",
  "model" : null
}
```

---

## GenerateSpriteSheet

Generate a sprite sheet and associated JSON metadata

Creates game assets by generating a sprite sheet image and extracting coordinate data.
<ul>
  <li>Generates visual sprite sheet using AI image models</li>
  <li>Analyzes the generated image to find sprite bounding boxes</li>
  <li>Exports standard JSON metadata for game engine integration</li>
</ul>

#### Planner Prompt Segment

```text
GenerateSpriteSheet - Create a sprite sheet image and corresponding JSON metadata
  * Generates an image containing multiple sprites based on a description
  * Automatically identifies sprite locations (x, y, width, height)
  * Outputs both a .png image and a .json metadata file
```

#### Default Execution Configuration

```json
{
  "task_type" : "GenerateSpriteSheet",
  "files" : null,
  "metadata_file" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GenerateSpriteSheet",
  "related_files" : null,
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GenerateSpriteSheet",
  "name" : "GenerateSpriteSheet",
  "model" : null
}
```

---

## IllustrateDocument

Analyze a document and generate images to enhance its content

Intelligently analyzes document content and generates contextually appropriate images.
<ul>
<li>Analyzes document structure to identify optimal image locations</li>
<li>Generates images that enhance understanding of complex concepts</li>
<li>Saves images with descriptive names in the document's folder</li>
<li>Automatically inserts image references at appropriate locations</li>
<li>Supports both Markdown and HTML formats</li>
<li>Creates diagrams, illustrations, and visual aids</li>
<li>Provides meaningful captions and alt text</li>
<li>Configurable image count and format</li>
</ul>

#### Planner Prompt Segment

```text
IllustrateDocument - Analyze a document and generate images to enhance its content
  - Specify a markdown or HTML file to illustrate
  - Configure maximum number of images (default: 5)
  - Choose image format (png/jpg)
  - Analyzes document structure and content
  - Generates contextually appropriate images
  - Saves images with descriptive names in the same folder
  - Optionally inserts image references at appropriate locations
```

#### Default Execution Configuration

```json
{
  "task_type" : "IllustrateDocument",
  "files" : null,
  "maxImages" : 5,
  "imageFormat" : "png",
  "autoInsert" : true,
  "imageInstructions" : null,
  "composerDirective" : null,
  "integratorDirective" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "IllustrateDocument",
  "related_files" : null,
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "IllustrateDocument",
  "name" : "IllustrateDocument",
  "model" : null
}
```

---

## InteractiveStory

Create choose-your-own-adventure narratives with branching paths

Generates complete interactive stories with meaningful choices and multiple endings.
<ul>
  <li>Creates detailed story structure with decision tree</li>
  <li>Writes opening segment that hooks the reader</li>
  <li>Develops branching narrative segments for each decision point</li>
  <li>Generates multiple distinct endings based on player choices</li>
  <li>Tracks state variables (health, reputation, inventory, etc.)</li>
  <li>Ensures all paths lead to meaningful endings (no dead ends)</li>
  <li>Optimizes for replay value with significantly different experiences</li>
  <li>Tracks consequences across choices for coherent storytelling</li>
  <li>Produces complete playable interactive story map</li>
  <li>Ideal for interactive fiction, training scenarios, educational content, and games</li>
</ul>

#### Planner Prompt Segment

```text
InteractiveStory - Create choose-your-own-adventure narratives with branching paths
 ** Optionally, list input files (supports glob patterns) to be examined for context
 ** Specify the premise or starting scenario
 ** Define genre, tone, and target audience
 ** Set number of decision points and choices per decision
 ** Enable state variable tracking (health, reputation, inventory, etc.)
 ** Prevent dead ends to ensure all paths lead somewhere meaningful
 ** Create multiple distinct endings based on player choices
 ** Optimize for replay value with different experiences
 ** Track consequences across choices for coherent storytelling
 ** Produces complete interactive narrative with decision tree
```

#### Default Execution Configuration

```json
{
  "task_type" : "InteractiveStory",
  "premise" : null,
  "genre" : "fantasy",
  "target_audience" : "young_adult",
  "tone" : "serious",
  "num_decision_points" : 5,
  "choices_per_decision" : 3,
  "track_state_variables" : true,
  "state_variables" : null,
  "prevent_dead_ends" : true,
  "num_endings" : 3,
  "optimize_replay_value" : true,
  "segment_word_count" : 300,
  "writing_style" : "descriptive",
  "point_of_view" : "second_person",
  "input_files" : null,
  "task_description" : "Generate interactive story: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "InteractiveStory"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "InteractiveStory",
  "name" : "InteractiveStory",
  "model" : null
}
```

---

## IterativeGraphGeneration

Extract structured knowledge from unstructured data by iteratively building an entity-relationship graph.

Constructs a knowledge graph by iteratively analyzing context and adding nodes/edges.
<ul>
  <li>Processes large contexts by chunking and iterative refinement</li>
  <li>Supports custom schemas for nodes and edges</li>
  <li>Visualizes progress using Mermaid diagrams</li>
  <li>Allows merging nodes to resolve entities</li>
  <li>Exports the final graph as GraphSON JSON</li>
  <li>Ideal for mapping complex domains, research analysis, and knowledge extraction</li>
</ul>

#### Planner Prompt Segment

```text
IterativeGraphGeneration - Build knowledge graphs incrementally
  * goal_prompt: The goal or question the graph should answer/represent.
  * context_data: Input text to analyze.
  * input_files: Input files to analyze.
  * node_types/edge_types: Allowed labels for nodes and edges.
  * Use this to extract entities and relationships for complex knowledge management and visualization.
```

#### Default Execution Configuration

```json
{
  "task_type" : "IterativeGraphGeneration",
  "goal_prompt" : null,
  "context_data" : null,
  "input_files" : null,
  "initial_graph_file" : null,
  "max_iterations" : 20,
  "max_nodes" : 50,
  "max_edges" : 100,
  "node_types" : [ "Concept", "Entity" ],
  "edge_types" : [ "RELATES_TO" ],
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "IterativeGraphGeneration",
  "task_description" : "Generate knowledge graph for 'unknown'"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "IterativeGraphGeneration",
  "name" : "IterativeGraphGeneration",
  "model" : null
}
```

---

## IterativeImageGeneration

Recursively generates and upscales images for high detail.

Generates a base image, identifies regions of interest, and recursively upscales and refines them using generative AI.
Useful for:
<ul>
    <li>Large format posters</li>
    <li>Detailed maps or "Where's Waldo" style scenes</li>
    <li>Images requiring text or small details legible at high zoom</li>
</ul>

#### Planner Prompt Segment

```text
IterativeImageGeneration - Generates ultra-high-resolution images via recursive upscaling
* Use for: Creating posters, maps, or detailed scenes where standard generation lacks resolution.
* Mechanism: Generates a base image, identifies regions, upscales them, and uses AI to refine details recursively.
```

#### Default Execution Configuration

```json
{
  "task_type" : "IterativeImageGeneration",
  "output_file" : "",
  "prompts" : null,
  "input_file" : null,
  "upscale_factor" : 2.0,
  "min_region_size" : 128,
  "max_aspect_ratio" : 3.0,
  "extension" : "png",
  "grid_schedule" : null,
  "tile_overlap" : 0.15,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "IterativeImageGeneration",
  "task_description" : null,
  "files" : null,
  "related_files" : null,
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "IterativeImageGeneration",
  "name" : "IterativeImageGeneration",
  "model" : null
}
```

---

## JournalismReasoning

Investigate stories through journalistic principles and methods

Analyzes stories using professional journalism standards and practices.
<ul>
  <li>Verifies facts and checks claims against evidence</li>
  <li>Identifies multiple perspectives and source credibility</li>
  <li>Analyzes context, background, and broader implications</li>
  <li>Detects potential biases and conflicts of interest</li>
  <li>Finds information gaps and unanswered questions</li>
  <li>Explores alternative story angles and approaches</li>
  <li>Assesses newsworthiness and public interest</li>
  <li>Useful for investigative reporting, fact-checking, editorial planning</li>
  <li>Generates structured journalistic analysis with verified facts</li>
</ul>

#### Planner Prompt Segment

```text
JournalismReasoning - Investigate stories through journalistic principles and methods
  ** Specify the story topic or event to investigate
  ** Define journalism elements: who, what, when, where, why, how
  ** Enable fact verification and source checking
  ** Identify multiple perspectives and stakeholder voices
  ** Analyze context, background, and broader implications
  ** Detect potential biases and conflicts of interest
  ** Find information gaps and unanswered questions
  ** Explore alternative story angles
  ** Assess newsworthiness and public interest
  ** Produces structured journalistic analysis with verified facts
```

#### Default Execution Configuration

```json
{
  "task_type" : "JournalismReasoning",
  "story_topic" : null,
  "input_files" : null,
  "journalism_elements" : null,
  "verify_facts" : true,
  "identify_perspectives" : true,
  "analyze_context" : true,
  "identify_biases" : true,
  "find_gaps" : true,
  "alternative_angles" : 3,
  "assess_newsworthiness" : true,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "JournalismReasoning",
  "task_description" : "Investigate 'null' through journalistic analysis"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "JournalismReasoning",
  "name" : "JournalismReasoning",
  "model" : null
}
```

---

## NarrativeGeneration

Generate complete narratives from analysis and outlines

Extends NarrativeReasoning to generate complete, publication-ready narratives.
<ul>
  <li>Performs comprehensive narrative analysis (inherited from NarrativeReasoning)</li>
  <li>Creates detailed scene-by-scene outline based on analysis</li>
  <li>Generates each scene iteratively with full context</li>
  <li>Maintains consistency by feeding previous scenes into each generation</li>
  <li>Supports configurable structure (acts, scenes, word count)</li>
  <li>Customizable writing style, POV, tone, and narrative elements</li>
  <li>Optional revision passes for quality improvement</li>
  <li>Produces complete, coherent narrative with consistent style and voice</li>
  <li>Ideal for story generation, scenario planning, user journey narratives</li>
</ul>

#### Planner Prompt Segment

```text
NarrativeGeneration - Generate complete narratives from analysis and outlines
  ** Extends NarrativeReasoning with full story generation
  ** Specify the subject or scenario to develop
  ** Define narrative elements: characters, setting, conflict, timeline
  ** Set target word count and structural parameters (acts, scenes)
  ** Configure writing style, POV, and tone
  ** Enable detailed descriptions, dialogue, and internal thoughts
  ** Performs analysis, creates outline, then writes each scene iteratively
  ** Each scene receives context from previous scenes
  ** Produces complete, coherent narrative with consistent style
```

#### Default Execution Configuration

```json
{
  "task_type" : "NarrativeGeneration",
  "subject" : null,
  "input_files" : null,
  "narrative_elements" : null,
  "target_word_count" : 5000,
  "number_of_acts" : 3,
  "scenes_per_act" : 3,
  "writing_style" : "literary",
  "point_of_view" : "third person limited",
  "tone" : "dramatic",
  "detailed_descriptions" : true,
  "include_dialogue" : true,
  "show_internal_thoughts" : true,
  "revision_passes" : 2,
  "generate_scene_images" : true,
  "generate_cover_image" : true,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "NarrativeGeneration",
  "task_description" : "Generate full narrative for 'null'"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "NarrativeGeneration",
  "name" : "NarrativeGeneration",
  "model" : null
}
```

---

## NeuralNetworkLayer

Design and analyze neural network layers with formal mathematical definitions and intuitive explanations

Comprehensive neural network layer design and analysis tool with both rigorous mathematics and intuitive explanations.
<ul>
    <li>Executive summary with key insights</li>
    <li>Intuitive explanations with real-world analogies</li>
    <li>Visual conceptual diagrams</li>
    <li>Formal mathematical definition of the layer function</li>
    <li>Forward pass implementation with detailed equations</li>
    <li>Backward pass (gradient) derivation and implementation</li>
    <li>Higher-order derivative analysis (Hessian, etc.)</li>
    <li>Lyapunov stability analysis for training dynamics</li>
    <li>Lipschitz continuity and gradient flow analysis</li>
    <li>Numerical stability considerations</li>
    <li>Reference implementations in multiple languages</li>
    <li>Computational complexity analysis</li>
    <li>Memory footprint estimation</li>
    <li>Originality and novelty assessment</li>
    <li>Practical use cases and applications</li>
</ul>

#### Planner Prompt Segment

```text
NeuralNetworkLayer - Design and analyze neural network layers with comprehensive explanations
 ** Specify the layer name and forward function description
 ** Define input/output shapes and parameters
 ** Configure analysis options (higher-order, Lyapunov, Lipschitz)
 ** Select implementation languages
 ** The task will generate:
    - Executive summary with key insights and decision criteria
    - Intuitive explanations with real-world analogies
    - Visual conceptual diagrams
    - Formal mathematical definition with LaTeX
    - Forward pass equations and implementation
    - Backward pass (gradient) derivation and implementation
    - Higher-order derivative analysis (Hessian, curvature)
    - Lyapunov stability analysis for training dynamics
    - Lipschitz continuity and gradient flow analysis
    - Numerical stability considerations
    - Reference implementations
    - Complexity analysis
    - Originality analysis comparing to existing architectures
    - Use case analysis with application domains and scenarios
    - Practical guidance for implementation and deployment
 ** Useful for:
    - Learning about neural network layers (beginners to experts)
    - Designing custom neural network layers
    - Understanding existing layer mathematics
    - Analyzing training stability
    - Optimizing layer implementations
    - Research and documentation
    - Evaluating novelty for research papers
    - Identifying practical applications
```

#### Default Execution Configuration

```json
{
  "task_type" : "NeuralNetworkLayer",
  "layer_name" : null,
  "forward_function_description" : null,
  "input_shape" : null,
  "output_shape" : null,
  "parameters" : null,
  "activation" : "none",
  "include_higher_order" : true,
  "include_lyapunov" : true,
  "include_lipschitz" : true,
  "implementation_languages" : [ "tensorflow.js" ],
  "include_numerical_stability" : true,
  "generate_tests" : true,
  "analysis_depth" : "standard",
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "NeuralNetworkLayer"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "NeuralNetworkLayer",
  "name" : "NeuralNetworkLayer",
  "model" : null
}
```

---

## PersuasiveEssay

Generate compelling persuasive essays with structured arguments

Generates complete, well-structured persuasive essays using rhetorical techniques.
<ul>
  <li>Creates detailed outline with thesis, arguments, and counterarguments</li>
  <li>Writes compelling introduction with hook and background</li>
  <li>Develops main arguments with evidence and rhetorical devices</li>
  <li>Addresses counterarguments with strong rebuttals</li>
  <li>Crafts powerful conclusion with call to action</li>
  <li>Supports multiple tones and target audiences</li>
  <li>Includes optional revision passes for quality</li>
  <li>Uses ethos, pathos, and logos for persuasive impact</li>
  <li>Ideal for opinion pieces, proposals, advocacy, and academic arguments</li>
</ul>

#### Planner Prompt Segment

```text
PersuasiveEssay - Generate compelling persuasive essays with structured arguments
 ** Specify the thesis statement or position to argue
 ** Optionally provide input files (supports glob patterns) to incorporate as research
 ** Define target audience and tone
 ** Set target word count and number of main arguments
 ** Enable counterarguments and rebuttals for balanced perspective
 ** Use rhetorical devices (ethos, pathos, logos) for persuasive impact
 ** Include statistical evidence and citations
 ** Incorporate analogies and examples for clarity
 ** Configure call to action strength
 ** Performs outline creation, argument development, and iterative writing
 ** Produces complete, well-structured persuasive essay
 ** Detailed output saved to files with links in summary
```

#### Default Execution Configuration

```json
{
  "task_type" : "PersuasiveEssay",
  "input_files" : null,
  "thesis" : null,
  "target_audience" : "general public",
  "tone" : "formal",
  "target_word_count" : 1500,
  "num_arguments" : 3,
  "include_counterarguments" : true,
  "use_rhetorical_devices" : true,
  "include_evidence" : true,
  "use_analogies" : true,
  "call_to_action" : "strong",
  "revision_passes" : 1,
  "related_files" : null,
  "task_description" : "Generate persuasive essay for thesis: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "PersuasiveEssay"
}
```

#### Default Type Configuration

```json
{
  "generate_images" : true,
  "generate_cover_image" : true,
  "task_type" : "PersuasiveEssay",
  "model" : null,
  "name" : "PersuasiveEssay"
}
```

---

## ReportGeneration

Generate comprehensive business reports with data analysis and recommendations

Generates complete, professional business reports with structured analysis.
<ul>
  <li>Analyzes metrics and data points with trend analysis</li>
  <li>Creates structured report outline with multiple sections</li>
  <li>Generates executive summary/dashboard for quick insights</li>
  <li>Writes detailed sections with data-driven content</li>
  <li>Provides actionable recommendations based on findings</li>
  <li>Includes risk assessment and mitigation strategies</li>
  <li>Suggests data visualizations (charts, graphs, tables)</li>
  <li>Supports multiple report types (status updates, quarterly reviews, incident reports)</li>
  <li>Tailors content to target audience (executives, team members, stakeholders)</li>
  <li>Optional revision passes for quality improvement</li>
  <li>Ideal for business reporting, performance analysis, project summaries</li>
</ul>

#### Planner Prompt Segment

```text
ReportGeneration - Generate comprehensive business reports with data analysis and recommendations
  ** Specify the report topic and type (status update, quarterly review, incident report, etc.)
  ** Define target audience and time period
  ** Provide key metrics, KPIs, and data points to analyze
  ** Enable trend analysis, visualizations, and comparative analysis
  ** Include executive summary/dashboard for quick insights
  ** Generate actionable recommendations based on findings
  ** Assess risks and challenges
  ** Produces complete, professional report with clear structure
```

#### Default Execution Configuration

```json
{
  "task_type" : "ReportGeneration",
  "report_topic" : null,
  "report_type" : "status_update",
  "target_audience" : "executives",
  "time_period" : null,
  "key_metrics" : null,
  "data_points" : null,
  "include_trend_analysis" : true,
  "include_visualizations" : true,
  "include_executive_summary" : true,
  "include_recommendations" : true,
  "include_comparative_analysis" : true,
  "include_risk_assessment" : true,
  "tone" : "professional",
  "target_word_count" : 2000,
  "revision_passes" : 1,
  "related_files" : null,
  "input_files" : null,
  "task_description" : "Generate report on: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ReportGeneration"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ReportGeneration",
  "name" : "ReportGeneration",
  "model" : null
}
```

---

## ResearchPaperGeneration

Generate comprehensive academic research papers with citations

Generates complete, publication-ready academic research papers.
<ul>
  <li>Analyzes research sources and identifies gaps</li>
  <li>Creates structured academic outline</li>
  <li>Generates multi-section papers with proper citations</li>
  <li>Supports multiple paper types (empirical, theoretical, review, meta-analysis)</li>
  <li>Configurable academic levels (undergraduate to postdoc)</li>
  <li>Multiple citation styles (APA, MLA, Chicago, IEEE)</li>
  <li>Automatic bibliography generation</li>
  <li>Optional peer review simulation</li>
  <li>Revision passes for quality improvement</li>
  <li>Ideal for academic research, literature reviews, thesis chapters</li>
</ul>

#### Planner Prompt Segment

```text
ResearchPaperGeneration - Generate comprehensive academic research papers with citations
  ** research_topic: The main research question or topic
  ** paper_type: 'empirical', 'theoretical', 'review', or 'meta-analysis'
  ** academic_level: 'undergraduate', 'masters', 'phd', or 'postdoc'
  ** target_word_count: Target word count for the complete paper
  ** citation_style: 'apa', 'mla', 'chicago', or 'ieee'
  ** include_literature_review: Whether to include a literature review section
  ** include_methodology: Whether to include methodology section
  ** include_statistical_analysis: Whether to include statistical analysis descriptions
  ** include_peer_review: Whether to include peer review simulation
  ** number_of_sections: Number of main sections
  ** revision_passes: Number of revision passes
  ** research_files: Research source files or data to incorporate
  ** input_files: Specific files or patterns to use as input
```

#### Default Execution Configuration

```json
{
  "task_type" : "ResearchPaperGeneration",
  "research_topic" : null,
  "paper_type" : "empirical",
  "academic_level" : "masters",
  "target_word_count" : 8000,
  "citation_style" : "apa",
  "include_literature_review" : true,
  "include_methodology" : true,
  "include_statistical_analysis" : true,
  "include_peer_review" : true,
  "number_of_sections" : 6,
  "revision_passes" : 1,
  "research_files" : null,
  "input_files" : null,
  "task_description" : "Generate research paper on: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ResearchPaperGeneration"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ResearchPaperGeneration",
  "name" : "ResearchPaperGeneration",
  "model" : null
}
```

---

## Scriptwriting

Generate complete scripts for videos, podcasts, and presentations

Generates production-ready scripts with dialogue, timing, and production notes.
<ul>
  <li>Creates detailed script outline with sections and timing</li>
  <li>Writes natural, conversational dialogue for spoken delivery</li>
  <li>Includes visual directions and scene descriptions</li>
  <li>Suggests B-roll and supporting visuals</li>
  <li>Marks key points for emphasis or graphics</li>
  <li>Provides timing markers and duration estimates</li>
  <li>Includes production notes and speaker guidance</li>
  <li>Supports multiple script types (video, podcast, presentation, commercial)</li>
  <li>Configurable tone, pacing, and audience targeting</li>
  <li>Optional revision passes for quality improvement</li>
  <li>Ideal for video production, podcasts, presentations, training videos</li>
</ul>

#### Planner Prompt Segment

```text
Scriptwriting - Generate complete scripts for videos, podcasts, and presentations
 ** Optionally, list input files (supports glob patterns) to be examined when generating the script
 ** Specify the topic and script type (video, podcast, presentation, etc.)
 ** Set target duration and audience
 ** Configure tone and pacing
 ** Specify the topic and script type (video, podcast, presentation, etc.)
 ** Set target duration and audience
 ** Configure tone and pacing
 ** Include visual directions, timing markers, and B-roll suggestions
 ** Mark key points for emphasis or graphics
 ** Add speaker notes and production notes
 ** Performs outline creation, segment writing, and timing calculation
 ** Produces complete, production-ready script with all necessary elements
```

#### Default Execution Configuration

```json
{
  "task_type" : "Scriptwriting",
  "topic" : null,
  "script_type" : "video",
  "target_duration_minutes" : 5,
  "target_audience" : "general public",
  "tone" : "professional",
  "include_directions" : true,
  "include_timing" : true,
  "suggest_b_roll" : true,
  "include_notes" : true,
  "mark_key_points" : true,
  "pacing" : "moderate",
  "include_hook" : true,
  "include_cta" : true,
  "input_files" : null,
  "revision_passes" : 1,
  "related_files" : null,
  "task_description" : "Generate script for: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "Scriptwriting"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "Scriptwriting",
  "name" : "Scriptwriting",
  "model" : null
}
```

---

## SoftwareDesignDocument

Generate comprehensive software design documentation

Creates complete software design documentation with Mermaid diagrams.
<ul>
  <li>Use case diagrams and actor documentation</li>
  <li>Functional and non-functional requirements</li>
  <li>Architecture diagrams (C4, component, deployment)</li>
  <li>Data model and ERD diagrams</li>
  <li>Sequence and activity flow diagrams</li>
  <li>Test plan and test case documentation</li>
  <li>Phase planning with Gantt charts</li>
  <li>Project data JSON with tasks, epics, sprints, releases</li>
  <li>All diagrams use Mermaid syntax</li>
</ul>

#### Planner Prompt Segment

```text
SoftwareDesignDocument - Generate comprehensive software design documentation
  ** Specify the project name and system description
  ** Generate use case diagrams and actor documentation
  ** Create functional and non-functional requirements
  ** Produce architectural diagrams (C4, component, deployment)
  ** Design data models with ERD diagrams
  ** Create sequence and activity diagrams for key flows
  ** Generate test plans and test case documentation
  ** Plan development phases with milestones
  ** Output project data JSON with tasks, epics, sprints, releases
  ** All diagrams use Mermaid syntax for easy rendering
  ** Useful for:
     - Project kickoff documentation
     - Technical specification creation
     - Sprint and release planning
     - Stakeholder communication
     - Development team onboarding
```

#### Default Execution Configuration

```json
{
  "task_type" : "SoftwareDesignDocument",
  "project_name" : null,
  "system_description" : null,
  "target_audience" : null,
  "stakeholders" : null,
  "generate_use_cases" : true,
  "generate_requirements" : true,
  "generate_architecture" : true,
  "generate_data_model" : true,
  "generate_flow_diagrams" : true,
  "generate_test_plan" : true,
  "generate_phase_plan" : true,
  "generate_project_data" : true,
  "sprint_count" : 6,
  "sprint_duration_weeks" : 2,
  "technology_stack" : null,
  "constraints" : null,
  "input_files" : null,
  "task_description" : "Generate software design document for: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "SoftwareDesignDocument"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "SoftwareDesignDocument",
  "name" : "SoftwareDesignDocument",
  "model" : null
}
```

---

## TechnicalExplanation

Break down complex technical subjects into clear, digestible explanations

Generates clear, audience-appropriate explanations of complex technical topics.
<ul>
  <li>Creates structured outline with key concepts and terminology</li>
  <li>Adjusts language and depth for target audience (layperson to expert)</li>
  <li>Generates relatable analogies and metaphors</li>
  <li>Includes code examples with detailed explanations</li>
  <li>Defines essential terminology in context</li>
  <li>Provides visual descriptions and diagrams</li>
  <li>Includes practical examples and use cases</li>
  <li>Compares with related concepts for clarity</li>
  <li>Supports multiple formats (markdown, Q&A, step-by-step, tutorial)</li>
  <li>Optional revision passes for clarity improvement</li>
  <li>Ideal for documentation, onboarding, education, and knowledge sharing</li>
</ul>

#### Planner Prompt Segment

```text
TechnicalExplanation - Break down complex technical subjects into clear, digestible explanations
  ** Specify the technical topic to explain
  ** Define target audience expertise level
  ** Set level of detail (overview to comprehensive)
  ** Configure explanation format (markdown, Q&A, step-by-step, etc.)
  ** Enable analogies and metaphors for clarity
  ** Include code examples with explanations
  ** Define key terminology
  ** Provide visual descriptions
  ** Include practical examples and use cases
  ** Compare with related concepts
  ** Performs outline creation, content generation, and iterative refinement
  ** Produces clear, audience-appropriate technical explanations
```

#### Default Execution Configuration

```json
{
  "task_type" : "TechnicalExplanation",
  "topic" : null,
  "target_audience" : "intermediate",
  "level_of_detail" : "moderate_detail",
  "include_code_examples" : true,
  "explanation_format" : "markdown",
  "use_analogies" : true,
  "include_visual_descriptions" : true,
  "define_terminology" : true,
  "include_examples" : true,
  "include_comparisons" : true,
  "input_files" : null,
  "code_language" : null,
  "revision_passes" : 1,
  "related_files" : null,
  "task_description" : "Generate technical explanation for: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "TechnicalExplanation"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "TechnicalExplanation",
  "name" : "TechnicalExplanation",
  "model" : null
}
```

---

## TutorialGeneration

Create complete, step-by-step tutorials for processes and projects

Generates comprehensive tutorials with clear, actionable steps.
<ul>
  <li>Creates detailed outline with prerequisites and learning objectives</li>
  <li>Breaks process into logical, numbered steps</li>
  <li>Generates exact commands and code examples</li>
  <li>Includes expected outcomes and validation steps</li>
  <li>Adds screenshot placeholders for visual guidance</li>
  <li>Provides troubleshooting section for common issues</li>
  <li>Suggests next steps for continued learning</li>
  <li>Configurable verbosity and skill level</li>
  <li>Platform-specific instructions and requirements</li>
  <li>Ideal for how-to guides, educational content, and project-based learning</li>
</ul>

#### Planner Prompt Segment

```text
TutorialGeneration - Create complete, step-by-step tutorials for processes and projects
  ** Specify the goal or final outcome to achieve
  ** Define target platform and environment
  ** Set skill level and estimated duration
  ** Enable screenshot placeholders for visual guidance
  ** Configure verbosity level (concise, detailed, verbose)
  ** Include code examples and commands
  ** Add validation steps to verify success
  ** Include troubleshooting section for common errors
  ** Add learning objectives and next steps
  ** Produces publication-ready tutorial with clear, actionable steps
```

#### Default Execution Configuration

```json
{
  "task_type" : "TutorialGeneration",
  "goal" : null,
  "target_platform" : "cross-platform",
  "include_screenshots_placeholders" : true,
  "verbosity" : "detailed",
  "include_troubleshooting" : true,
  "skill_level" : "beginner",
  "estimated_duration" : 30,
  "include_code_examples" : true,
  "include_validation_steps" : true,
  "include_learning_objectives" : true,
  "include_next_steps" : true,
  "target_step_count" : 7,
  "related_files" : null,
  "input_files" : null,
  "task_description" : "Generate tutorial for: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "TutorialGeneration"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "TutorialGeneration",
  "name" : "TutorialGeneration",
  "model" : null
}
```

---

## WriteHtml

Create complete HTML files with embedded CSS and JavaScript

Creates standalone HTML files with embedded CSS and JavaScript.
<ul>
  <li>Generates complete, self-contained HTML documents</li>
  <li>Embeds CSS styles within &lt;style&gt; tags</li>
  <li>Embeds JavaScript within &lt;script&gt; tags</li>
  <li>Supports modern HTML5 features</li>
  <li>Can generate images using AI image models</li>
  <li>Automatically creates image directory and references</li>
  <li>Interactive approval or auto-apply mode</li>
  <li>Proper HTML structure and formatting</li>
</ul>

#### Default Execution Configuration

```json
{
  "task_type" : "WriteHtml",
  "files" : null,
  "related_files" : null,
  "task_description" : null,
  "generate_images" : false,
  "image_count" : 0,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "WriteHtml",
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "WriteHtml",
  "name" : "WriteHtml",
  "model" : null
}
```

---

