# Task Actions

This package contains specialized IntelliJ actions that leverage AI models to perform complex, structured tasks. Each action provides a configuration dialog to fine-tune the AI's behavior, context, and output format.

## Available Actions

### [BusinessProposalAction](BusinessProposalAction.kt)
Generates professional business proposals tailored to specific objectives.
- **Proposal Types**: Project, investment, grant, partnership, or RFP response.
- **Components**: Optional ROI analysis, risk assessment, competitive analysis, and resource requirements.
- **Context**: Incorporates existing project files and stakeholder information to ensure relevance.

### [DataIngestAction](DataIngestAction.kt)
Automates the discovery of data patterns for ingestion and parsing.
- **Functionality**: Samples input files (such as logs) to discover patterns and generate parsing logic or regex.
- **Configuration**: Adjustable sample size, coverage thresholds, and discovery iteration limits.

### [DocProcessorAction](DocProcessorAction.kt)
A sophisticated tool for maintaining documentation and code synchronization based on file metadata.
- **Frontmatter Driven**: Processes Markdown files containing `specifies`, `documents`, or `transforms` keys.
- **Flexible Execution**: Supports multiple overwrite modes including intelligent patching and update-only logic.
- **Batch Processing**: Allows users to select and execute multiple documentation tasks simultaneously.

### [FileModificationTaskAction](FileModificationTaskAction.kt)
A general-purpose action for modifying existing files or creating new ones based on natural language.
- **Input**: Natural language description of the desired modifications.
- **Context**: Can include related files, git diffs, and text extracted from non-text formats (PDF, HTML).

### [GeneratePresentationAction](GeneratePresentationAction.kt)
Creates interactive, standalone HTML presentations.
- **Output**: Modern HTML slide decks.
- **Visuals**: Integrated AI image generation for key slides to enhance visual appeal.
- **Customization**: Configurable topic, target audience, and presentation style.

### [IllustrateDocumentAction](IllustrateDocumentAction.kt)
Enhances existing Markdown or HTML documents with AI-generated imagery.
- **Process**: Analyzes document content to identify illustration opportunities and generates contextually relevant images.
- **Integration**: Optionally inserts image references directly into the source document.
- **Advanced Control**: Provides directives for image composition and integration strategies.

### [NarrativeGenerationAction](NarrativeGenerationAction.kt)
Develops long-form narratives, stories, or complex scenarios.
- **Structure**: Supports multi-act structures with configurable scenes per act.
- **Styling**: Various writing styles (literary, technical, etc.) and narrative perspectives.
- **Visuals**: Can generate cover art and scene-specific visualizations.

### [PersuasiveEssayAction](PersuasiveEssayAction.kt)
Crafts compelling arguments and persuasive essays.
- **Techniques**: Employs rhetorical devices (ethos, pathos, logos), addresses counterarguments, and incorporates evidence.
- **Customization**: Fine-tune the thesis, target audience, tone, and call-to-action strength.

### [ResearchPaperAction](ResearchPaperAction.kt)
Generates academic-grade research papers with high rigor.
- **Paper Types**: Empirical, theoretical, review, or meta-analysis.
- **Standards**: Supports APA, MLA, Chicago, and IEEE citation styles.
- **Features**: Includes literature reviews, methodology sections, and simulated peer review for quality improvement.

### [WriteHtmlAction](WriteHtmlAction.kt)
Generates complete web pages including layout and logic.
- **Scope**: Produces integrated HTML, CSS, and JavaScript.
- **Assets**: Supports generating custom AI images specifically for the page content.
- **Requirements**: Driven by descriptions of layout, styling, and functional requirements.

## Common Features

Most actions in this package share a set of core capabilities:
- **Model Selection**: Users can choose specific AI models for text and image generation tasks.
- **Temperature Control**: Adjust the balance between creative exploration and focused precision.
- **Context Awareness**: Actions can reference project files to provide grounded, relevant results.
- **Web UI Integration**: Tasks are managed via a local web server, providing a rich interface for monitoring progress and reviewing output.
- **Auto-Apply**: Options to automatically save generated content back to the workspace once the task is complete.