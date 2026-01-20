# Generate Actions

This package contains a suite of AI-powered actions designed to generate new assets, documentation, and code structures within the IntelliJ environment. These tools leverage large language models and image generation capabilities to streamline development workflows.

## Actions

### [CreateFileFromDescriptionAction](CreateFileFromDescriptionAction.kt)
Allows users to create a new file by providing a natural language description. The AI interprets the requirements to determine both an appropriate filename and the initial content of the file.
- **Usage**: Right-click in the project view and select "Create File From Description".
- **Features**: Automatic path resolution relative to the project root and collision detection for existing files.

### [CreateImageAction](CreateImageAction.kt)
A technical drawing assistant that generates images based on the context of selected code files.
- **Usage**: Select one or more files/folders and trigger "Create Image".
- **Features**: Uses the code content as context for the image generation prompt, allowing for the creation of diagrams or illustrations relevant to the implementation.

### [GenerateDocumentationAction](GenerateDocumentationAction.kt)
Compiles user-facing or technical documentation from a selection of source files.
- **Usage**: Select files or a directory to process into documentation.
- **Features**: 
    - Supports producing a single compiled markdown file or individual documentation files for each source.
    - Maintains a history of recent documentation instructions.
    - Parallel processing of files for efficiency.

### [GenerateRelatedFileAction](GenerateRelatedFileAction.kt)
Creates a new "analogue" file based on an existing source file and a specific directive (e.g., "Create a README for this class" or "Create a unit test").
- **Usage**: Right-click a single file and select "Create Analogue File".
- **Features**: Provides the source file's content to the AI to ensure the generated file is contextually accurate.

### [OCRAction](OCRAction.kt)
Converts documents and images into Markdown text using vision-capable AI models.
- **Usage**: Select image files or PDFs and trigger "OCR Processing".
- **Features**: 
    - Handles paginated documents (like PDFs).
    - Renders document pages to images before processing with an OCR-specialized AI prompt.
    - Outputs results as `.md` files in the same directory as the source.

## Configuration
These actions primarily utilize the "Smart Chat Client" and "Image Model" settings configured in the Cognotik plugin settings. Ensure your API keys and model selections are correctly configured to use these features.