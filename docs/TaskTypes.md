# Task Types

This document lists all available task types in the system, categorized and alphabetized.

<!-- TOC -->
* [Execution & Automation](#execution--automation)
  * [AutoFix](#autofix)
  * [RunCode](#runcode)
  * [RunTool](#runtool)
  * [SubPlan](#subplan)
  * [SymbolsDbCodeTask](#symbolsdbcodetask)
* [File](#file)
  * [DataIngest](#dataingest)
  * [Discussion](#discussion)
  * [FileAppend](#fileappend)
  * [FileModification](#filemodification)
  * [FileSearch](#filesearch)
  * [ImageTable](#imagetable)
  * [LanguageServer](#languageserver)
  * [PdfForm](#pdfform)
  * [ReadDocuments](#readdocuments)
* [File Operations](#file-operations)
  * [OCRTask](#ocrtask)
* [Games](#games)
  * [GameEconomy](#gameeconomy)
  * [GameLevelDesign](#gameleveldesign)
  * [GameMechanicsDesign](#gamemechanicsdesign)
  * [GameNarrativeDesign](#gamenarrativedesign)
* [Online & Search](#online--search)
  * [CrawlerAgent](#crawleragent)
  * [GitHubSearch](#githubsearch)
  * [MCPTool](#mcptool)
* [Reasoning](#reasoning)
  * [AbductiveReasoning](#abductivereasoning)
  * [AbstractionLadder](#abstractionladder)
  * [AdversarialReasoning](#adversarialreasoning)
  * [AnalogicalReasoning](#analogicalreasoning)
  * [Brainstorming](#brainstorming)
  * [CausalInference](#causalinference)
  * [ChainOfThought](#chainofthought)
  * [ConstraintRelaxation](#constraintrelaxation)
  * [ConstraintSatisfaction](#constraintsatisfaction)
  * [CounterfactualAnalysis](#counterfactualanalysis)
  * [DecisionTree](#decisiontree)
  * [DecompositionSynthesis](#decompositionsynthesis)
  * [DialecticalReasoning](#dialecticalreasoning)
  * [EthicalReasoning](#ethicalreasoning)
  * [FiniteStateMachine](#finitestatemachine)
  * [FunctorialMapping](#functorialmapping)
  * [GameTheory](#gametheory)
  * [GeneticOptimization](#geneticoptimization)
  * [IsomorphismDiscovery](#isomorphismdiscovery)
  * [LateralThinking](#lateralthinking)
  * [MathematicalReasoning](#mathematicalreasoning)
  * [MetaCognitiveReflection](#metacognitivereflection)
  * [ProbabilisticReasoning](#probabilisticreasoning)
  * [SocraticDialogue](#socraticdialogue)
  * [StructuralInvariantAnalysis](#structuralinvariantanalysis)
  * [SystemsThinking](#systemsthinking)
  * [TableCompilation](#tablecompilation)
  * [TemporalReasoning](#temporalreasoning)
* [Session](#session)
  * [CommandSession](#commandsession)
  * [JdbcSession](#jdbcsession)
* [Social](#social)
  * [LLMExperiment](#llmexperiment)
  * [LLMPollSimulation](#llmpollsimulation)
  * [MultiPerspectiveAnalysis](#multiperspectiveanalysis)
  * [PoliticalOptimization](#politicaloptimization)
* [Writing](#writing)
  * [ArticleGeneration](#articlegeneration)
  * [BusinessProposal](#businessproposal)
  * [ComicBookGeneration](#comicbookgeneration)
  * [EmailCampaign](#emailcampaign)
  * [GenerateImage](#generateimage)
  * [GeneratePresentation](#generatepresentation)
  * [GenerateQRImage](#generateqrimage)
  * [GenerateSpriteSheet](#generatespritesheet)
  * [IllustrateDocument](#illustratedocument)
  * [InteractiveStory](#interactivestory)
  * [IterativeGraphGeneration](#iterativegraphgeneration)
  * [JournalismReasoning](#journalismreasoning)
  * [NarrativeGeneration](#narrativegeneration)
  * [NeuralNetworkLayer](#neuralnetworklayer)
  * [PersuasiveEssay](#persuasiveessay)
  * [ReportGeneration](#reportgeneration)
  * [ResearchPaperGeneration](#researchpapergeneration)
  * [Scriptwriting](#scriptwriting)
  * [SoftwareDesignDocument](#softwaredesigndocument)
  * [TechnicalExplanation](#technicalexplanation)
  * [TutorialGeneration](#tutorialgeneration)
  * [WriteHtml](#writehtml)
<!-- TOC -->

## Execution & Automation

### AutoFix

Run a command and automatically fix any issues that arise

Executes a command and automatically fixes any issues that arise.
<ul>
  <li>Specify commands and working directories</li>
  <li>Supports multiple commands and directories</li>
  <li>Interactive approval mode</li>
  <li>Output diff formatting</li>
</ul>

#### Default Execution Configuration

```json
{
  "task_type" : "AutoFix",
  "commands" : [ ],
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "AutoFix"
}
```

#### Default Type Configuration

```json
{
  "name" : "AutoFix",
  "model" : null,
  "promptTemplate" : "SelfHealing - Run a command and automatically fix any issues that arise\n* Specify the commands to be executed along with their working directories\n* Each command's working directory should be specified relative to the root directory\n* Provide the commands and their arguments in the 'commands' field\n* Each command should be a list of strings\n* Available commands:\n{executables}",
  "task_type" : "AutoFix"
}
```

---

### RunCode

Execute code snippets with oversight

Executes code snippets in an interactive environment.
<ul>
  <li>User-approved code execution</li>
  <li>Working directory configuration</li>
  <li>Output capture and formatting</li>
  <li>Error handling and reporting</li>
  <li>Interactive result review</li>
</ul>

#### Planner Prompt Segment

```text
RunCode - Use a code interpreter to solve and complete the user's request.
  * Do not directly write code (yet)
  * Include detailed technical requirements for the needed solution
```

#### Default Execution Configuration

```json
{
  "task_type" : "RunCode",
  "goal" : null,
  "workingDir" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "RunCode"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "RunCode",
  "codeRuntime" : null,
  "model" : null,
  "name" : "RunCode"
}
```

---

### RunTool

Execute external tools

Executes configured external tools.

#### Planner Prompt Segment

```text
RunTool - Execute a tool with custom arguments
  * Available tools: 

```

#### Default Execution Configuration

```json
{
  "task_type" : "RunTool",
  "tool" : null,
  "args" : null,
  "workingDir" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "RunTool",
  "executable" : null
}
```

#### Default Type Configuration

```json
{
  "task_type" : "RunTool",
  "model" : null,
  "name" : "RunTool"
}
```

---

### SubPlan

Create and execute sub-plans using recursive planning

Enables recursive planning and execution with configurable cognitive modes.
<ul>
  <li>Create sub-plans with different cognitive strategies</li>
  <li>Support for multiple recursion levels</li>
  <li>Context propagation to sub-plans</li>
  <li>Configurable recursion depth limits</li>
  <li>Automatic result aggregation and summarization</li>
  <li>Flexible cognitive mode selection per sub-plan</li>
  <li>Useful for complex multi-stage problems</li>
</ul>

#### Planner Prompt Segment

```text
SubPlanningTask - Create and execute sub-plans using recursive planning with configurable cognitive modes.
** Specify a planning goal or objective
** Optionally provide context information
** Can override the cognitive mode for the sub-plan
** Supports multiple levels of recursion up to configured depth
** Results are aggregated and optionally summarized
```

#### Default Execution Configuration

```json
{
  "task_type" : "SubPlan",
  "planning_goal" : null,
  "context" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "SubPlan"
}
```

#### Default Type Configuration

```json
{
  "cognitiveSettings" : null,
  "taskSettings" : { },
  "purpose" : "",
  "summaryPrompt" : "Create a comprehensive summary of the sub-planning results below.\n\nOriginal Goal: {goal}\n\nThe summary should:\n- Highlight key findings and accomplishments\n- Identify any issues or blockers encountered\n- Provide actionable next steps if applicable\n- Be concise but complete\n\nUse markdown formatting with headers and bullet points.",
  "model" : null,
  "name" : "SubPlan",
  "task_type" : "SubPlan",
  "cognitiveMode" : null
}
```

---

### SymbolsDbCodeTask

Execute code snippets with predefined symbols

Executes code snippets in an interactive environment with access to a symbol graph.
<ul>
  <li>Access to `symbols_db` (SymbolGraphService)</li>
  <li>Query code symbols and relationships</li>
  <li>User-approved code execution</li>
  <li>Interactive result review</li>
</ul>

#### Planner Prompt Segment

```text
RunCode - Use a code interpreter to solve and complete the user's request.
  * Do not directly write code (yet)
  * Include detailed technical requirements for the needed solution
You have access to a `symbols_db` object (SymbolGraphService) to assist with code execution.
```

#### Default Execution Configuration

```json
{
  "task_type" : "SymbolsDbCodeTask",
  "goal" : null,
  "workingDir" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "SymbolsDbCodeTask"
}
```

#### Default Type Configuration

```json
{
  "codeRuntime" : "GroovyRuntime",
  "symbolFile" : "symbol_graph.json",
  "task_type" : "SymbolsDbCodeTask",
  "model" : null,
  "name" : "SymbolsDbCodeTask"
}
```

---

## File

### DataIngest

Iteratively parse unstructured logs into structured data

Automates the creation of regex parsers for log files.
<ul>
  <li>Samples data to discover patterns using LLM</li>
  <li>Iteratively targets residual (unparsed) data</li>
  <li>Streams large files to produce JSONL output</li>
  <li>Generates an index linking data back to source lines</li>
</ul>

#### Planner Prompt Segment

```text
DataIngest - Iteratively parse unstructured logs/text into structured data
  ** Specify input_files patterns (glob) to process
  ** Iteratively discovers Regex patterns using LLM for residual data
  ** Generates structured artifacts: data.jsonl, data.csv, patterns.json, and index.csv
  ** Efficiently handles large files via streaming extraction
```

#### Default Execution Configuration

```json
{
  "task_type" : "DataIngest",
  "input_files" : null,
  "sample_size" : 1000,
  "max_iterations" : 10,
  "coverage_threshold" : 0.95,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "DataIngest"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "DataIngest",
  "name" : "DataIngest",
  "model" : null
}
```

---

### Discussion

Directly answer questions or provide insights using the LLM, optionally referencing files, with optional user feedback and iteration.

Provides direct answers and insights using the LLM, optionally referencing project files.
<ul>
  <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
  <li>Reading files is optional; the task can operate with or without file input</li>
  <li>User feedback and iterative refinement are supported but not required</li>
  <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
  <li>Can answer detailed questions about code, design, or project context</li>
  <li>Supports both one-shot and interactive discussion modes</li>
  <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
</ul>

#### Planner Prompt Segment

```text

  Discussion - Directly answer questions or provide insights using the LLM. Reading files is optional and can be included if relevant to the inquiry.
    * Specify the questions and the goal of the inquiry.
    * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
    * User response/feedback and iteration are supported.
  
```

#### Default Execution Configuration

```json
{
  "task_type" : "Discussion",
  "inquiry_questions" : null,
  "inquiry_goal" : null,
  "input_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "Discussion"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "Discussion",
  "name" : "Discussion",
  "model" : null
}
```

---

### FileAppend

Append content to the end of existing files

Allows for precise additions to the end of files without modifying existing content.
<ul>
  <li>Ideal for logs, exports, and list updates</li>
  <li>Supports AI-generated content based on context</li>
  <li>Provides reviewable previews before applying changes</li>
  <li>Integrates with project structure and standards</li>
</ul>

#### Planner Prompt Segment

```text
FileAppend - Append content to the end of an existing file
  * Specify the relative file path and the content or goal of the addition.
  * The target file is NOT provided to the AI; only related context files are.
  * Useful for adding log entries, updating lists, or adding new exports/imports at the end of a file
```

#### Default Execution Configuration

```json
{
  "task_type" : "FileAppend",
  "file" : null,
  "related_files" : null,
  "append_content" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "FileAppend"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FileAppend",
  "name" : "FileAppend",
  "model" : null
}
```

---

### FileModification

Create new files or modify existing code with AI-powered assistance

Creates or modifies source files with AI assistance while maintaining code quality.
<ul>
  <li>Shows proposed changes in diff format for easy review</li>
  <li>Supports both automated application and manual approval modes</li>
  <li>Maintains project coding standards and style consistency</li>
  <li>Handles complex multi-file operations and refactoring</li>
  <li>Provides clear documentation of all changes with rationale</li>
  <li>Implements proper error handling and edge cases</li>
  <li>Updates imports and dependencies automatically</li>
  <li>Preserves existing code formatting and structure</li>
</ul>

#### Planner Prompt Segment

```text
FileModification - Modify existing files or create new files
  * For each file, specify the relative file path and the goal of the modification or creation
  * List input files/tasks to be examined when designing the modifications or new files
```

#### Default Execution Configuration

```json
{
  "task_type" : "FileModification",
  "files" : null,
  "related_files" : null,
  "extractContent" : false,
  "modifications" : null,
  "includeGitDiff" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "FileModification"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FileModification",
  "name" : "FileModification",
  "model" : null
}
```

---

### FileSearch

Search project files using patterns with contextual results

Performs pattern-based searches across project files with context.
<ul>
  <li>Supports both substring and regex search patterns</li>
  <li>Shows configurable context lines around matches</li>
  <li>Groups results by file with line numbers</li>
  <li>Filters for text-based files automatically</li>
  <li>Provides organized, readable output format</li>
</ul>

#### Planner Prompt Segment

```text
FileSearch - Search for patterns in files and provide results with context
* Specify the search pattern (substring or regex)
* Specify whether the pattern is a regex or a substring
* Specify the number of context lines to include
* List files (incl glob patterns) to be searched
```

#### Default Execution Configuration

```json
{
  "task_type" : "FileSearch",
  "search_pattern" : "",
  "is_regex" : false,
  "context_lines" : 2,
  "input_files" : null,
  "extractContent" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "FileSearch"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FileSearch",
  "name" : "FileSearch",
  "model" : null
}
```

---

### ImageTable

Generate a table/grid of AI-generated images

Creates a grid of images by generating each cell using AI image generation.
<ul>
  <li>Define rows and columns as labels for the grid</li>
  <li>Provide a prompt template with {row} and {column} placeholders</li>
  <li>Optionally specify a base style for consistent aesthetics</li>
  <li>Generates individual images and an HTML table view</li>
  <li>Useful for style comparisons, product variations, character sheets</li>
</ul>

#### Default Execution Configuration

```json
{
  "task_type" : "ImageTable",
  "rows" : null,
  "columns" : null,
  "image_prompt_template" : null,
  "base_style" : null,
  "output_directory" : "generated_images",
  "image_format" : "png",
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ImageTable"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ImageTable",
  "parallel_generation" : 2,
  "image_width" : 512,
  "image_height" : 512,
  "model" : null,
  "name" : "ImageTable"
}
```

---

### LanguageServer

Interact with Language Servers (LSP)

Provides code intelligence capabilities via the Language Server Protocol.
<ul>
    <li><b>Definition:</b> Locate where a symbol is defined.</li>
    <li><b>References:</b> Find all usages of a symbol.</li>
    <li><b>Diagnostics:</b> Check files for syntax errors and warnings.</li>
    <li><b>Hover:</b> Get documentation or type information at a specific position.</li>
</ul>
Requires language servers (e.g., pylsp, typescript-language-server) to be installed in the environment.

#### Planner Prompt Segment

```text
LanguageServer - Query code intelligence (LSP)
  * Use to find definitions, references, or check for syntax errors (diagnostics).
  * Supported extensions: py, js, ts, kt, java, c, cpp, go, rs, sh, tex, yaml, dockerfile
  * Actions: 'diagnostics' (file-wide), 'definition' (specific pos), 'references' (specific pos), 'hover' (specific pos).
```

#### Default Execution Configuration

```json
{
  "task_type" : "LanguageServer",
  "action" : null,
  "file" : null,
  "line" : null,
  "character" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "LanguageServer"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "LanguageServer",
  "model" : null,
  "name" : "LanguageServer"
}
```

---

### PdfForm

Fills out a specific PDF form template with provided data.

Fills fields in a pre-configured PDF template.
<ul>
  <li><b>Requires:</b> A template PDF file defined in the global Type Config.</li>
  <li><b>Output:</b> A new PDF file with the fields populated.</li>
  <li>Automatically lists available fields from the template to the Planner.</li>
</ul>

#### Planner Prompt Segment

```text
PdfForm - Configuration Error: No template_file specified in TypeConfig.
```

#### Default Execution Configuration

```json
{
  "task_type" : "PdfForm",
  "output_file" : null,
  "fields" : null,
  "flatten" : true,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "PdfForm"
}
```

#### Default Type Configuration

```json
{
  "template_file" : null,
  "task_type" : "PdfForm",
  "name" : "PdfForm",
  "model" : null
}
```

---

### ReadDocuments

Deeply analyze project files and provide comprehensive technical insights or answers to specific questions.

Analyzes project files and provides detailed technical insights using the LLM.
<ul>
  <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
  <li>Reading files is optional; the task can operate with or without file input</li>
  <li>User feedback and iterative refinement are supported but not required</li>
  <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
  <li>Can answer detailed questions about code, design, or project context</li>
  <li>Supports both one-shot and interactive discussion modes</li>
  <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
</ul>

#### Planner Prompt Segment

```text

  ReadDocuments - Directly answer questions or provide insights using the LLM.
    * inquiry_questions: Specific questions to address.
    * inquiry_goal: The goal of the inquiry.
    * input_files: File patterns (e.g. **/*.kt) to use as input.
  
```

#### Default Execution Configuration

```json
{
  "task_type" : "ReadDocuments",
  "inquiry_questions" : null,
  "inquiry_goal" : null,
  "input_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "ReadDocuments"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ReadDocuments",
  "name" : "ReadDocuments",
  "model" : null
}
```

---

## File Operations

### OCRTask

Convert documents (PDF, Images) to Markdown text

Uses Vision models to extract text and formatting from documents.
<ul>
<li>Supports PDF and Image files</li>
<li>Converts to Markdown format</li>
<li>Preserves layout and structure where possible</li>
<li>Optionally extracts figures and metadata</li>
</ul>

#### Planner Prompt Segment

```text
OCR - Convert documents (PDF, Images) to Markdown text.
* Extracts text from images and PDFs using Vision models.
* Preserves formatting as Markdown.
* Optionally extracts figures as images and metadata/form fields.
* Saves output to a .md file with the same name.
```

#### Default Execution Configuration

```json
{
  "task_type" : "OCRTask",
  "files" : null,
  "dpi" : 150.0,
  "extract_figures" : false,
  "extract_metadata" : false,
  "extract_text" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "OCRTask",
  "related_files" : null,
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "OCRTask",
  "name" : "OCRTask",
  "model" : null
}
```

---

## Games

### GameEconomy

Design complete game economic systems with progression and monetization

Designs comprehensive game economy systems with balanced progression.
<ul>
  <li>Creates multi-resource economic systems with generation and consumption</li>
  <li>Designs progression curves with experience and level systems</li>
  <li>Builds skill trees and talent systems</li>
  <li>Creates loot tables with balanced drop rates</li>
  <li>Designs monetization strategies without pay-to-win</li>
  <li>Implements engagement hooks (daily rewards, seasonal content, battle passes)</li>
  <li>Forecasts economy health and player progression</li>
  <li>Provides balance recommendations and adjustment strategies</li>
  <li>Useful for game design, economy balancing, and monetization planning</li>
</ul>

#### Planner Prompt Segment

```text
GameEconomy - Design complete game economic systems with progression and monetization
  ** Specify the game title and type (RPG, strategy, idle, multiplayer)
  ** Define progression style (linear, branching, open)
  ** Configure number of resources (2-10) and progression tiers (5-100)
  ** Optionally include skill trees, crafting, and trading systems
  ** Choose monetization model (free-to-play, premium, subscription)
  ** Optionally include daily rewards, seasonal content, and battle passes
  ** Generate economy forecasts for 3-12 months
  ** Optionally generate detailed balance reports
  ** Useful for:
     - Game design and balancing
     - Economy system design
     - Monetization strategy
     - Player progression planning
     - Engagement system design
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameEconomy",
  "game_title" : null,
  "game_type" : "RPG",
  "progression_style" : "linear",
  "num_resources" : 3,
  "num_progression_tiers" : 50,
  "include_skill_tree" : true,
  "include_crafting" : false,
  "include_trading" : false,
  "monetization_model" : "free-to-play",
  "include_daily_rewards" : true,
  "include_seasonal_content" : true,
  "include_battle_pass" : true,
  "forecast_months" : 6,
  "generate_balance_report" : true,
  "additional_context" : null,
  "input_files" : null,
  "task_description" : "Design game economy for: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameEconomy"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameEconomy",
  "name" : "GameEconomy",
  "model" : null
}
```

---

### GameLevelDesign

Generate complete game level designs with layout, pacing, and encounters

Generates production-ready game level designs with comprehensive documentation.
<ul>
  <li>Creates detailed level layout with zones and connections</li>
  <li>Designs encounters with appropriate difficulty progression</li>
  <li>Analyzes and visualizes pacing curves</li>
  <li>Places collectibles and secret areas strategically</li>
  <li>Designs player guidance systems (implicit and explicit)</li>
  <li>Generates difficulty variants for accessibility</li>
  <li>Includes ASCII/text-based level visualization</li>
  <li>Supports multiple game types (platformer, shooter, puzzle, RPG)</li>
  <li>Configurable pacing styles (steady, escalating, varied)</li>
  <li>Optional boss encounters, puzzles, and secrets</li>
  <li>Ideal for game development, level design documentation, and prototyping</li>
</ul>

#### Planner Prompt Segment

```text
GameLevelDesign - Generate complete game level designs with layout, pacing, and encounters
 ** Optionally, list input files (supports glob patterns) to be examined for context
 ** Specify level name and game type (platformer, shooter, puzzle, rpg, etc.)
 ** Set target duration and difficulty tier
 ** Configure player count (single or multiplayer)
 ** Choose level theme and visual style
 ** Include boss encounters, puzzles, secrets, and collectibles
 ** Define pacing style (steady, escalating, varied)
 ** Generate difficulty variants for accessibility
 ** Produces complete level design with ASCII visualization
 ** Includes encounter progression, pacing analysis, and player guidance
 ** Ideal for game development, level design documentation, and prototyping
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameLevelDesign",
  "level_name" : null,
  "game_type" : "platformer",
  "level_duration_minutes" : 10,
  "difficulty_tier" : "medium",
  "player_count" : 1,
  "level_theme" : "dungeon",
  "include_boss_encounter" : false,
  "include_puzzles" : true,
  "include_secrets" : true,
  "include_collectibles" : true,
  "pacing_style" : "escalating",
  "generate_difficulty_variants" : false,
  "include_visual_layout" : true,
  "input_files" : null,
  "task_description" : "Generate game level design: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameLevelDesign"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameLevelDesign",
  "name" : "GameLevelDesign",
  "model" : null
}
```

---

### GameMechanicsDesign

Generate comprehensive game mechanics with balance analysis

Designs complete game mechanics systems with detailed analysis.
<ul>
  <li>Generates core gameplay mechanics from high-level concepts</li>
  <li>Analyzes mechanic interactions and synergies</li>
  <li>Designs progression and economy systems</li>
  <li>Evaluates balance, fairness, and difficulty curves</li>
  <li>Predicts player behavior through simulated playtesting</li>
  <li>Provides tuning parameters and recommendations</li>
  <li>Useful for game design prototyping, balancing, and competitive design</li>
</ul>

#### Planner Prompt Segment

```text
GameMechanicsDesign - Generate comprehensive game mechanics with balance analysis
  ** Specify the game concept (e.g., "Tower defense with resource management")
  ** Define target audience (casual, hardcore, family, competitive)
  ** Set core gameplay loop duration
  ** Configure number of mechanics to design (3-8)
  ** Choose balance focus (skill, luck, strategy, mixed)
  ** The task will:
     - Generate core gameplay mechanics
     - Analyze mechanic interactions
     - Design progression systems
     - Create economy systems
     - Evaluate balance and fairness
     - Simulate playtesting scenarios
     - Provide tuning parameters
  ** Useful for:
     - Game design prototyping
     - Balancing existing games
     - Competitive game design
     - Educational game mechanics
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameMechanicsDesign",
  "game_concept" : null,
  "target_audience" : "casual",
  "core_loop_duration" : "15 minutes",
  "num_mechanics" : 5,
  "include_progression_system" : true,
  "include_economy_system" : true,
  "include_difficulty_scaling" : true,
  "balance_focus" : "mixed",
  "playtesting_scenarios" : 3,
  "generate_tuning_guide" : true,
  "input_files" : null,
  "task_description" : "Design game mechanics for: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameMechanicsDesign"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameMechanicsDesign",
  "name" : "GameMechanicsDesign",
  "model" : null
}
```

---

### GameNarrativeDesign

Create interactive game narratives with branching storylines

Creates complete game narrative designs with interactive elements and player agency.
<ul>
  <li>Extends NarrativeGeneration with game-specific features</li>
  <li>Three-act structure adapted for interactive media</li>
  <li>Multiple branching points with meaningful choices</li>
  <li>Character arcs that respond to player decisions</li>
  <li>Branching dialogue trees with emotional beats</li>
  <li>Multiple endings based on player choices</li>
  <li>Optional side quests and expanded content</li>
  <li>Player agency analysis and replayability factors</li>
  <li>Complete design documentation for implementation</li>
  <li>Ideal for RPGs, adventure games, visual novels, interactive fiction</li>
</ul>

#### Planner Prompt Segment

```text
GameNarrativeDesign - Create interactive game narratives with branching storylines
  ** Extends NarrativeGeneration with game-specific features
  ** Specify game title, genre, and narrative style
  ** Define player agency level and role
  ** Design core game mechanics and systems
  ** Configure branching points and multiple endings
  ** Include dialogue trees with emotional beats
  ** Character arcs that respond to player choices
  ** Side quests and optional content
  ** Produces complete game narrative design document
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameNarrativeDesign",
  "game_title" : null,
  "genre" : "RPG",
  "narrative_style" : "branching",
  "player_agency_level" : "high",
  "num_main_characters" : 4,
  "num_branching_points" : 8,
  "num_endings" : 4,
  "include_dialogue_trees" : true,
  "include_character_arcs" : true,
  "include_side_quests" : true,
  "include_game_mechanics" : true,
  "tone" : "heroic",
  "player_role" : "protagonist",
  "estimated_playtime_hours" : 20,
  "setting" : null,
  "themes" : null,
  "generate_character_portraits" : false,
  "generate_scene_art" : false,
  "input_files" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameNarrativeDesign",
  "task_description" : "Design game narrative for 'null'",
  "subject" : null,
  "narrative_elements" : {
    "genre" : "RPG",
    "narrative_style" : "branching",
    "player_agency_level" : "high",
    "num_main_characters" : 4,
    "tone" : "heroic",
    "player_role" : "protagonist"
  },
  "target_word_count" : 80000,
  "number_of_acts" : 3,
  "scenes_per_act" : 3,
  "writing_style" : "epic fantasy",
  "point_of_view" : "second person",
  "detailed_descriptions" : true,
  "include_dialogue" : true,
  "show_internal_thoughts" : true,
  "revision_passes" : 1,
  "generate_scene_images" : false,
  "generate_cover_image" : true
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameNarrativeDesign",
  "name" : "GameNarrativeDesign",
  "model" : null
}
```

---

## Online & Search

### CrawlerAgent

Search Google, fetch top results, and analyze content

Searches Google for specified queries and analyzes the top results.
<ul>
 <li>Performs Google searches</li>
 <li>Fetches top search results</li>
 <li>Analyzes content for specific goals</li>
 <li>Generates detailed analysis reports</li>
</ul>

#### Planner Prompt Segment

```text
CrawlerAgent - Search Google, fetch top results, and analyze content
** Specify the search query
** Or provide direct URLs to analyze
** Specify a detailed query/analysis prompt to guide content processing
** Choose a processing strategy: DefaultSummarizer, FactChecking, or JobMatching
** Results will be saved to .websearch directory for future reference
** Links found in analysis can be automatically followed for deeper research

```

#### Default Execution Configuration

```json
{
  "task_type" : "CrawlerAgent",
  "search_query" : null,
  "direct_urls" : null,
  "content_queries" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "CrawlerAgent"
}
```

#### Default Type Configuration

```json
{
  "seed_method" : "GoogleProxy",
  "fetch_method" : "HttpClient",
  "processing_strategy" : "DefaultSummarizer",
  "allowed_domains" : null,
  "respect_robots_txt" : true,
  "max_pages_per_task" : null,
  "max_depth" : null,
  "max_queue_size" : null,
  "concurrent_page_processing" : null,
  "max_final_output_size" : null,
  "min_content_length" : null,
  "follow_links" : null,
  "allow_revisit_pages" : null,
  "create_final_summary" : null,
  "generate_transcript" : true,
  "task_type" : "CrawlerAgent",
  "model" : null,
  "name" : "CrawlerAgent"
}
```

---

### GitHubSearch

Search GitHub repositories, code, issues and users

Performs comprehensive searches across GitHub's content.
<ul>
  <li>Searches repositories, code, and issues</li>
  <li>Supports advanced search queries</li>
  <li>Filters results by various criteria</li>
  <li>Formats results with relevant details</li>
  <li>Handles API rate limiting</li>
</ul>

#### Planner Prompt Segment

```text
GitHubSearch - Search GitHub for code, commits, issues, repositories, topics, or users
   * Specify the search query
   * Specify the type of search (code, commits, issues, repositories, topics, users)
   * Specify the number of results to return (max 100)
   * Optionally specify sort order (e.g. stars, forks, updated)
   * Optionally specify sort direction (asc or desc)
```

#### Default Execution Configuration

```json
{
  "task_type" : "GitHubSearch",
  "search_query" : "",
  "search_type" : "repositories",
  "per_page" : 30,
  "sort" : null,
  "order" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "GitHubSearch"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GitHubSearch",
  "name" : "GitHubSearch",
  "model" : null
}
```

---

### MCPTool

Execute tools from Model Context Protocol servers

Executes tools from MCP (Model Context Protocol) servers.
<ul>
  <li>Connect to MCP servers via various transports</li>
  <li>Execute tools with custom arguments</li>
  <li>Configurable timeouts and retry logic</li>
  <li>Support for multiple MCP server integrations</li>
  <li>Structured result handling</li>
  <li>Automatic tool discovery and validation</li>
  <li>Exponential backoff retry strategy</li>
</ul>

#### Planner Prompt Segment

```text
MCPTool - Execute tools from Model Context Protocol (MCP) servers
 ** Specify the MCP server name and tool to execute
 ** Provide tool arguments as a JSON object
 ** Configure timeout and retry behavior
 ** Supports integration with external MCP-compatible services
```

#### Default Execution Configuration

```json
{
  "task_type" : "MCPTool",
  "server_name" : null,
  "tool_name" : null,
  "tool_arguments" : null,
  "timeout_seconds" : 30,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "MCPTool"
}
```

#### Default Type Configuration

```json
{
  "default_server" : null,
  "default_timeout" : 30,
  "auto_retry" : false,
  "generate_transcript" : true,
  "max_retries" : 3,
  "retry_delay_ms" : 1000,
  "exponential_backoff" : true,
  "task_type" : "MCPTool",
  "name" : "MCPTool",
  "model" : null
}
```

---

## Reasoning

### AbductiveReasoning

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

### AbstractionLadder

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

### AdversarialReasoning

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

### AnalogicalReasoning

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

### Brainstorming

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

### CausalInference

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

### ChainOfThought

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

### ConstraintRelaxation

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

### ConstraintSatisfaction

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

### CounterfactualAnalysis

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

### DecisionTree

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

### DecompositionSynthesis

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

### DialecticalReasoning

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

### EthicalReasoning

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

### FiniteStateMachine

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

### FunctorialMapping

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

### GameTheory

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

### GeneticOptimization

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

### IsomorphismDiscovery

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

### LateralThinking

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

### MathematicalReasoning

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

### MetaCognitiveReflection

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

### ProbabilisticReasoning

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

### SocraticDialogue

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

### StructuralInvariantAnalysis

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

### SystemsThinking

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

### TableCompilation

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

### TemporalReasoning

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

## Session

### CommandSession

Execute commands in a stateful, interactive session

Creates and manages a persistent command-line session (e.g., bash, python).
This allows for stateful interactions where commands can build on the results of previous ones.
<ul>
    <li><b>Start any interactive process:</b> Specify the command to run</li>
    <li><b>Send inputs:</b> Provide a list of commands to be executed sequentially in the session.</li>
    <li><b>Stateful Sessions:</b> Reuse sessions by providing a `sessionId`. The environment (variables, current directory) persists between tasks using the same ID.</li>
    <li><b>Manage Session Lifecycle:</b> Sessions can be explicitly closed or will be cleaned up automatically.</li>
    <li><b>TTY Support:</b> Set `tty` to true to allocate a pseudo-terminal (requires pty4j), enabling UI applications and TTY-dependent tools.</li>
</ul>

#### Planner Prompt Segment

```text
CommandSession - Create and manage a stateful interactive terminal session
** Specify the command to start an interactive session, or sessionId to reuse an existing one
** Provide inputs to send to the session
** Session persists between commands for stateful interactions

System Information:
- OS: Linux 6.14.0-37-generic (amd64)
- Working Directory: /home/andrew/code/Cognotik
- Available Tools: 

Active Sessions:

```

#### Default Execution Configuration

```json
{
  "task_type" : "CommandSession",
  "command" : [ "bash", "-i" ],
  "inputs" : [ ],
  "sessionId" : null,
  "timeout" : 30000,
  "idle_timeout" : 2000,
  "tty" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "CommandSession"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "CommandSession",
  "name" : "CommandSession",
  "model" : null
}
```

---

### JdbcSession

Execute SQL queries via JDBC

Executes SQL statements against a database using JDBC.
<ul>
    <li><b>Connection:</b> Requires `url`. Optional `user`, `password`, and `driver`.</li>
    <li><b>Stateful:</b> Use `sessionId` to keep connections open across multiple tasks (useful for transactions or temp tables).</li>
    <li><b>Output:</b> Returns results as Markdown tables.</li>
</ul>

#### Planner Prompt Segment

```text
JdbcSession - Execute SQL queries via JDBC in a stateful session.
** Specify the `url`, `user`, and `password` to start a new session.
** Use `sessionId` to reuse an existing connection for transactions or subsequent queries.
** Provide a list of `sql` statements to execute.

Active Sessions:
None
```

#### Default Execution Configuration

```json
{
  "task_type" : "JdbcSession",
  "url" : null,
  "user" : null,
  "password" : null,
  "driver" : null,
  "sql" : [ ],
  "sessionId" : null,
  "closeSession" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "JdbcSession"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "JdbcSession",
  "name" : "JdbcSession",
  "model" : null
}
```

---

## Social

### LLMExperiment

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

### LLMPollSimulation

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

### MultiPerspectiveAnalysis

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

### PoliticalOptimization

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

## Writing

### ArticleGeneration

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

### BusinessProposal

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

### ComicBookGeneration

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

### EmailCampaign

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

### GenerateImage

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

### GeneratePresentation

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

### GenerateQRImage

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

### GenerateSpriteSheet

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

### IllustrateDocument

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

### InteractiveStory

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

### IterativeGraphGeneration

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

### JournalismReasoning

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

### NarrativeGeneration

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

### NeuralNetworkLayer

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

### PersuasiveEssay

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

### ReportGeneration

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

### ResearchPaperGeneration

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

### Scriptwriting

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

### SoftwareDesignDocument

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

### TechnicalExplanation

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

### TutorialGeneration

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

### WriteHtml

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

